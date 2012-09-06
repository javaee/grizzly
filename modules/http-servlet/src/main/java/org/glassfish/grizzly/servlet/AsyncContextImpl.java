/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 1997-2012 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://glassfish.dev.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */

package org.glassfish.grizzly.servlet;

import java.io.IOException;
import java.util.LinkedList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.AsyncContext;
import javax.servlet.AsyncEvent;
import javax.servlet.AsyncListener;
import javax.servlet.DispatcherType;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import org.glassfish.grizzly.http.server.util.Globals;

class AsyncContextImpl implements AsyncContext {

    /* 
     * Event notification types for async mode
     */
    static enum AsyncEventType { COMPLETE, TIMEOUT, ERROR, START_ASYNC }

    private static final Logger log =
        Logger.getLogger(AsyncContextImpl.class.getName());

    // Default timeout for async operations
    private static final long DEFAULT_ASYNC_TIMEOUT_MILLIS = -1; // No timeout by default

    // Thread pool for async dispatches
    static final ExecutorService pool =
        Executors.newCachedThreadPool(new AsyncPoolThreadFactory());

    // The original (unwrapped) request
    private HttpServletRequestImpl origRequest;

    // The possibly wrapped request passed to ServletRequest.startAsync
    private ServletRequest servletRequest;

    // The possibly wrapped response passed to ServletRequest.startAsync    
    private ServletResponse servletResponse;

    private boolean isOriginalRequestAndResponse = false;

    private boolean isStartAsyncWithZeroArg = false;

    // defaults to false
    private AtomicBoolean isDispatchInProgress = new AtomicBoolean(); 

    private ThreadLocal<Boolean> isDispatchInScope = new ThreadLocal<Boolean>() {
        @Override
        protected Boolean initialValue() {
            return Boolean.FALSE;
        }
    };

    private AtomicBoolean isOkToConfigure = new AtomicBoolean(true); 

    private long asyncTimeoutMillis = DEFAULT_ASYNC_TIMEOUT_MILLIS;

    private final LinkedList<AsyncListenerContext> asyncListenerContexts =
        new LinkedList<AsyncListenerContext>();

    // The number of times this AsyncContext has been reinitialized via a call
    // to ServletRequest#startAsync
    private AtomicInteger startAsyncCounter = new AtomicInteger(0);

    private ThreadLocal<Boolean> isStartAsyncInScope = new ThreadLocal<Boolean>() {
        @Override  
        protected Boolean initialValue() {  
            return Boolean.FALSE;  
        }  
    };  

    /**
     * Constructor
     *
     * @param origRequest the original (unwrapped) request
     * @param servletRequest the possibly wrapped request passed to
     * ServletRequest.startAsync
     * @param servletResponse the possibly wrapped response passed to
     * ServletRequest.startAsync
     * @param isStartAsyncWithZeroArg true if the zero-arg version of
     * startAsync was called, false otherwise
     */
    AsyncContextImpl(HttpServletRequestImpl origRequest,
                            ServletRequest servletRequest,
                            ServletResponse servletResponse,
                            boolean isStartAsyncWithZeroArg) {
        this.origRequest = origRequest;
        init(servletRequest, servletResponse, isStartAsyncWithZeroArg);
    }

    @Override
    public ServletRequest getRequest() {
        return servletRequest;
    }

    HttpServletRequestImpl getOriginalRequest() {
        return origRequest;
    }

    @Override
    public ServletResponse getResponse() {
        return servletResponse;
    }

    @Override
    public boolean hasOriginalRequestAndResponse() {
        return isOriginalRequestAndResponse;
    }

    @Override
    public void dispatch() {
        ApplicationDispatcher dispatcher = 
            (ApplicationDispatcher)getZeroArgDispatcher(
                origRequest, servletRequest, isStartAsyncWithZeroArg);

        isDispatchInScope.set(true);
        if (dispatcher != null) {
            if (isDispatchInProgress.compareAndSet(false, true)) {
                pool.execute(new Handler(this, dispatcher, origRequest));
            } else {
                throw new IllegalStateException("Asynchronous dispatch already "
                        + "in progress, must call ServletRequest.startAsync first");
            }
        } else {
            // Should never happen, because any unmapped paths will be 
            // mapped to the DefaultServlet
            log.warning("Unable to determine target of zero-arg dispatcher");
        }
    } 

    @Override
    public void dispatch(String path) {
        if (path == null) {
            throw new IllegalArgumentException("Null path");
        }
        ApplicationDispatcher dispatcher = (ApplicationDispatcher)
            servletRequest.getRequestDispatcher(path);
        isDispatchInScope.set(true);
        if (dispatcher != null) {
            if (isDispatchInProgress.compareAndSet(false, true)) {
                pool.execute(new Handler(this, dispatcher, origRequest));
            } else {
                throw new IllegalStateException("Asynchronous dispatch already "
                        + "in progress, must call ServletRequest.startAsync first");
            }
        } else {
            // Should never happen, because any unmapped paths will be 
            // mapped to the DefaultServlet
            log.log(Level.WARNING, "Unable to acquire RequestDispatcher for {0}", path);
        }
    }

    @Override
    public void dispatch(ServletContext context, String path) {
        if (path == null || context == null) {
            throw new IllegalArgumentException("Null context or path");
        }
        ApplicationDispatcher dispatcher = (ApplicationDispatcher)
            context.getRequestDispatcher(path);
        isDispatchInScope.set(true);
        if (dispatcher != null) {
            if (isDispatchInProgress.compareAndSet(false, true)) {
                pool.execute(new Handler(this, dispatcher, origRequest));
            } else {
                throw new IllegalStateException("Asynchronous dispatch already "
                        + "in progress, must call ServletRequest.startAsync first");
            }
        } else {
            // Should never happen, because any unmapped paths will be 
            // mapped to the DefaultServlet
            log.log(Level.WARNING, "Unable to acquire RequestDispatcher for {0}in servlet context {1}",
                    new Object[]{path, context.getContextPath()});
        }
    }

    boolean isDispatchInScope() {
        return isDispatchInScope.get();
    }

    boolean getAndResetDispatchInScope() {
        final boolean flag = isDispatchInScope.get();
        isDispatchInScope.set(Boolean.FALSE);
        return flag;
    }

    @Override
    public void complete() {
        origRequest.asyncComplete();
    }

    @Override
    public void start(Runnable run) {
        pool.execute(run);
    }

    @Override
    public void addListener(AsyncListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("Null listener");
        }

        if (!isOkToConfigure.get()) {
            throw new IllegalStateException("Must not call AsyncContext.addListener "
                    + "after the container-initiated dispatch during which "
                    + "ServletRequest.startAsync was called has returned to the container");
        }

        synchronized(asyncListenerContexts) {
            asyncListenerContexts.add(new AsyncListenerContext(listener));
        }
    }

    @Override
    public void addListener(AsyncListener listener,
                            ServletRequest servletRequest,
                            ServletResponse servletResponse) {
        if (listener == null || servletRequest == null ||
                servletResponse == null) {
            throw new IllegalArgumentException(
                "Null listener, request, or response");
        }

        if (!isOkToConfigure.get()) {
            throw new IllegalStateException("Must not call AsyncContext.addListener "
                    + "after the container-initiated dispatch during which "
                    + "ServletRequest.startAsync was called has returned to the container");
        }

        synchronized(asyncListenerContexts) {
            asyncListenerContexts.add(new AsyncListenerContext(
                listener, servletRequest, servletResponse));
        }
    }

    @Override
    public <T extends AsyncListener> T createListener(Class<T> clazz)
            throws ServletException {
        T listener = null;
        final WebappContext ctx = origRequest.getContextImpl();
        if (ctx != null) {
            try {
                listener = ctx.createListener(clazz);
            } catch (Throwable t) {
                throw new ServletException(t);
            }
        }
        return listener;
    }

    @Override
    public void setTimeout(long timeout) {
        if (!isOkToConfigure.get()) {
            throw new IllegalStateException("Must not call AsyncContext.setTimeout"
                    + " after the container-initiated dispatch during which "
                    + "ServletRequest.startAsync was called has returned to the container");
        }
        asyncTimeoutMillis = timeout;
//        origRequest.setAsyncTimeout(timeout);
    }

    @Override
    public long getTimeout() {
        return asyncTimeoutMillis;
    }

    /*
     * Reinitializes this AsyncContext with the given request and response.
     *
     * @param servletRequest the ServletRequest with which to initialize
     * the AsyncContext
     * @param servletResponse the ServletResponse with which to initialize
     * the AsyncContext
     * @param isStartAsyncWithZeroArg true if the zero-arg version of
     * startAsync was called, false otherwise
     */
    void reinitialize(ServletRequest servletRequest,
                      ServletResponse servletResponse,
                      boolean isStartAsyncWithZeroArg) {

        init(servletRequest, servletResponse, isStartAsyncWithZeroArg);
        isDispatchInProgress.set(false);
        setOkToConfigure(true);
        startAsyncCounter.incrementAndGet();
        notifyAsyncListeners(AsyncEventType.START_ASYNC, null);
    }

    /**
     * @return value true if calls to AsyncContext#addListener and
     * AsyncContext#setTimeout will be accepted, and false if these
     * calls will result in an IllegalStateException
     */
    boolean isOkToConfigure() {
        return isOkToConfigure.get();
    }

    /**
     * @param value true if calls to AsyncContext#addListener and
     * AsyncContext#setTimeout will be accepted, and false if these
     * calls will result in an IllegalStateException
     */
    void setOkToConfigure(boolean value) {
        isOkToConfigure.set(value);
    }

    private void init(ServletRequest servletRequest,
            ServletResponse servletResponse, boolean isStartAsyncWithZeroArg) {

        this.servletRequest = servletRequest;
        this.servletResponse = servletResponse;
        // If original or container-wrapped request and response,
        // AsyncContext#hasOriginalRequestAndResponse must return true;
        // false otherwise (i.e., if application-wrapped)
        this.isOriginalRequestAndResponse =
                ((servletRequest instanceof HttpServletRequestImpl) &&
                (servletResponse instanceof HttpServletResponseImpl)) ||
                ((servletRequest instanceof DispatchedHttpServletRequest) &&
                (servletResponse instanceof DispatchedHttpServletResponse));
                
//                ((servletRequest instanceof RequestFacade ||
//                servletRequest instanceof ApplicationHttpRequest) &&
//                (servletResponse instanceof ResponseFacade ||
//                servletResponse instanceof ApplicationHttpResponse));

        this.isStartAsyncWithZeroArg = isStartAsyncWithZeroArg;
    }

    /**
     * Determines the dispatcher of a zero-argument async dispatch for the
     * given request.
     *
     * @return the dispatcher of the zero-argument async dispatch
     */
    private RequestDispatcher getZeroArgDispatcher(
            HttpServletRequestImpl origRequest, ServletRequest servletRequest,
            boolean isStartAsyncWithZeroArg) {

        String dispatchTarget = null;
        boolean isNamed = false;
        if ((!isStartAsyncWithZeroArg) &&
                servletRequest instanceof HttpServletRequest) {

            HttpServletRequest req = (HttpServletRequest)servletRequest;
            dispatchTarget = getCombinedPath(req);
        } else {
            DispatchTargetsInfo dtInfo = (DispatchTargetsInfo)origRequest.getAttribute(
                    ApplicationDispatcher.LAST_DISPATCH_REQUEST_PATH_ATTR);
            if (dtInfo != null) {
                dispatchTarget = dtInfo.getLastDispatchTarget();
                isNamed = dtInfo.isLastNamedDispatchTarget();
            }
            if (dispatchTarget == null) {
                dispatchTarget = getCombinedPath(origRequest);
            }
        }

        RequestDispatcher dispatcher = null;
        if (dispatchTarget != null) {
            dispatcher = ((isNamed) ?
                    servletRequest.getServletContext().getNamedDispatcher(dispatchTarget) :
                    servletRequest.getRequestDispatcher(dispatchTarget));
        }

        return dispatcher;
    }

    private String getCombinedPath(HttpServletRequest req) {
        String servletPath = req.getServletPath();
        if (servletPath == null) {
            return null;
        }
        String pathInfo = req.getPathInfo();
        if (pathInfo == null) {
            return servletPath;
        }
        return servletPath + pathInfo;
    }

    static class Handler implements Runnable {

        private final AsyncContextImpl asyncContext;
        private final ApplicationDispatcher dispatcher;
        private final HttpServletRequestImpl origRequest;

        Handler(AsyncContextImpl asyncContext,
                ApplicationDispatcher dispatcher,
                HttpServletRequestImpl origRequest) {
            this.asyncContext = asyncContext;
            this.dispatcher = dispatcher;
            this.origRequest = origRequest;
        }
       
        @Override
        public void run() {
            asyncContext.isStartAsyncInScope.set(Boolean.TRUE);
            origRequest.setAttribute(Globals.DISPATCHER_TYPE_ATTR,
                                     DispatcherType.ASYNC);
            origRequest.setAsyncStarted(false);
            int startAsyncCurrent = asyncContext.startAsyncCounter.get();
            try {
                dispatcher.dispatch(asyncContext.getRequest(),
                    asyncContext.getResponse(), DispatcherType.ASYNC);
                /* 
                 * Close the response after the dispatch target has
                 * completed execution, unless the dispatch target has called
                 * ServletRequest#startAsync, in which case the AsyncContext's
                 * startAsyncCounter will be greater than it was before the
                 * dispatch
                 */
                if (asyncContext.startAsyncCounter.compareAndSet(
                        startAsyncCurrent, startAsyncCurrent)) {
                    asyncContext.complete();
                } else {
                    // Reset async timeout
                    origRequest.setAsyncTimeout(asyncContext.getTimeout());
                }
            } catch (Throwable t) {
                asyncContext.notifyAsyncListeners(AsyncEventType.ERROR, t);
                asyncContext.getOriginalRequest().errorDispatchAndComplete(t);
            } finally {
                asyncContext.isStartAsyncInScope.set(Boolean.FALSE);
            }
        }
    }

    boolean isStartAsyncInScope() {
        return isStartAsyncInScope.get().booleanValue();
    }

    /*
     * Notifies all AsyncListeners of the given async event type
     */
    void notifyAsyncListeners(AsyncEventType asyncEventType, Throwable t) {
        LinkedList<AsyncListenerContext> clone;
        synchronized(asyncListenerContexts) {
            if (asyncListenerContexts.isEmpty()) {
                return;
            }
            clone =
                new LinkedList<AsyncListenerContext>(asyncListenerContexts);
            if (asyncEventType.equals(AsyncEventType.START_ASYNC)) {
                asyncListenerContexts.clear();
            }
        }
        for (AsyncListenerContext asyncListenerContext : clone) {
            AsyncListener asyncListener =
                asyncListenerContext.getAsyncListener();
            AsyncEvent asyncEvent = new AsyncEvent(
                this, asyncListenerContext.getRequest(),
                asyncListenerContext.getResponse(), t);
            try {
                switch (asyncEventType) {
                case COMPLETE:
                    asyncListener.onComplete(asyncEvent);
                    break;
                case TIMEOUT:
                    asyncListener.onTimeout(asyncEvent);
                    break;
                case ERROR:
                    asyncListener.onError(asyncEvent);
                    break;
                case START_ASYNC:
                    asyncListener.onStartAsync(asyncEvent);
                    break;
                default: // not possible
                    break;
                }
            } catch (IOException ioe) {
                log.log(Level.WARNING, "Error invoking AsyncListener",
                        ioe);
            }
        }
    }

    void clear() {
        synchronized(asyncListenerContexts) {
            asyncListenerContexts.clear();
        }
    }

    /**
     * Class holding all the information required for invoking an
     * AsyncListener (including the AsyncListener itself).
     */
    private static class AsyncListenerContext {

        private AsyncListener listener;
        private ServletRequest request;
        private ServletResponse response;

        public AsyncListenerContext(AsyncListener listener) {
            this(listener, null, null);
        }

        public AsyncListenerContext(AsyncListener listener,
                                    ServletRequest request,
                                    ServletResponse response) {
            this.listener = listener;
            this.request = request;
            this.response = response;
        }

        public AsyncListener getAsyncListener() {
            return listener;
        }

        public ServletRequest getRequest() {
            return request;
        }

        public ServletResponse getResponse() {
            return response;
        }
    }


    private static final class AsyncPoolThreadFactory implements ThreadFactory {

        private final ThreadFactory defaultFactory = Executors.defaultThreadFactory();
        private final AtomicInteger counter = new AtomicInteger(0);


        // ------------------------------------------ Methods from ThreadFactory


        @Override
        public Thread newThread(Runnable r) {

            final Thread t = defaultFactory.newThread(r);
            t.setName("grizzly-web-async-thread-" + counter.incrementAndGet());
            return t;

        }

    } // END AsyncPoolThreadFactory

}
