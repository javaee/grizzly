/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2007-2013 Oracle and/or its affiliates. All rights reserved.
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
package org.glassfish.grizzly.comet;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.glassfish.grizzly.Closeable;
import org.glassfish.grizzly.CompletionHandler;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.CloseType;
import org.glassfish.grizzly.GenericCloseListener;
import org.glassfish.grizzly.ReadHandler;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.http.server.Response;
import org.glassfish.grizzly.http.server.TimeoutHandler;
import org.glassfish.grizzly.http.io.NIOInputStream;
import org.glassfish.grizzly.http.util.Header;
import org.glassfish.grizzly.utils.DataStructures;

/**
 * The main object used by {@link CometHandler} and Servlet to push information amongst suspended request/response. The
 * {@link CometContext} is always available for {@link CometHandler} and can be used to {@link #notify}, or share
 * information with other {@link CometHandler}. This is the equivalent of server push as the CometContext will invoke
 * all registered CometHandler ({@link #addCometHandler}) sequentially. <p/> <p>A CometContext can be considered as a
 * topic where CometHandler register for information. A CometContext can be shared amongst Servlet of the same
 * application, or globally across all deployed web applications. Normally, a CometContext is created using a topic's
 * name like:
 * <pre><code>
 * <p/>
 * CometEngine ce = CometEngine.getEngine();
 * CometContext cc = ce.registerContext("MyTopic");
 * <p/>
 * and then inside a Servlet.service() method, you just need to call:
 * <p/>
 * cc.addCometListener(myNewCometListener());
 * cc.notify("I'm pushing data to all registered CometHandler");
 * </code>
 * </pre>
 * <p/> <p> As soon as {@link #addCometHandler} is invoked, Grizzly will automatically <strong>suspend</strong> the
 * request/response (will not commit the response). A response can be <strong>resumed</strong> by invoking {@link
 * #resumeCometHandler}, which will automatically commit the response and remove the associated CometHandler from the
 * CometContext. <p/> <p>A CometContext uses a {@link NotificationHandler} to invoke, using the calling thread or a
 * Grizzly thread pool, all CometHandler than have been added using the {@link #addCometHandler}. A {@link
 * NotificationHandler} can be used to filter or transform the content that will eventually be pushed back to all
 * connected clients. You can also use a {@link NotificationHandler} to throttle push like invoking only a subset of the
 * CometHandler, etc. <p/> <p>Idle suspended connection can be timed out by configuring the {@link
 * #setExpirationDelay(long)}. The value needs to be in milliseconds. If there is no I/O operations and no invocation of
 * {@link #notify} during the expiration delay, Grizzly will resume all suspended connection. An application will have a
 * chance to send back data using the connection as Grizzly will invoke the {@link CometHandler#onInterrupt} before
 * resuming the connection. Note that setting the expiration delay to -1 disable the above mechanism, e.g. idle
 * connection will never get resumed by Grizzly. <p/> <p>Attributes can be added/removed the same way HttpServletSession
 * is doing. It is not recommended to use attributes if this {@link CometContext} is not shared amongst multiple context
 * path (uses HttpServletSession instead). </p>
 */
public class CometContext<E> {
    /**
     * Generic error message
     */
    protected final static String INVALID_COMET_HANDLER = "CometHandler cannot be null. "
        + "This CometHandler was probably resumed and an invalid reference was made to it.";
    protected final static String ALREADY_REMOVED = "CometHandler already been removed or invalid.";
    private final static String COMET_NOT_ENABLED = "Make sure you have enabled Comet or make sure the thread"
        + " invoking that method is the same as the Servlet.service() thread.";
    protected final static Logger LOGGER = Logger.getLogger(CometContext.class.getName());
    private final Map<Object,Object> attributes;
      
    protected final static ThreadLocal<Request> REQUEST_LOCAL = new ThreadLocal<Request>();
    
    /**
     * The context path associated with this instance.
     */
    protected String topic;
    /**
     * The default delay expiration before a {@link CometContext}'s {@link CometHandler} are interrupted.
     */
    private long expirationDelay;
    /**
     * The default NotificationHandler.
     */
    protected NotificationHandler notificationHandler;
    /**
     * The list of registered {@link CometHandler}
     */
    private final List<CometHandler> handlers;
    protected final CometEvent<CometContext> eventInterrupt;
    protected final CometEvent<CometContext> eventTerminate;
    private final CometEvent<CometContext> eventInitialize;

    /**
     * true, if we want to enable mechanism, which detects closed connections,
     * or false otherwise. The mentioned mechanism should be disabled if we
     * expect client to use HTTP pipelining.
     */
    private boolean isDetectClosedConnections = true;
    
    /**
     * Create a new instance
     *
     * @param contextTopic the context path
     */
    public CometContext(CometEngine engine, String contextTopic) {
        topic = contextTopic;
        attributes = DataStructures.<Object, Object>getConcurrentMap();
        handlers = new CopyOnWriteArrayList<CometHandler>();
        eventInterrupt = new CometEvent<CometContext>(CometEvent.Type.INTERRUPT, this);
        eventInitialize = new CometEvent<CometContext>(CometEvent.Type.INITIALIZE, this);
        eventTerminate = new CometEvent<CometContext>(CometEvent.Type.TERMINATE, this, this);
        initDefaultValues();
    }

    /**
     * init of default values. used by constructor and the cache recycle mechanism
     */
    private void initDefaultValues() {
        expirationDelay = -1;
    }

    /**
     * Get the context path associated with this instance.
     *
     * @return topic the context path associated with this instance
     *
     * @deprecated - use getTopic.
     */
    public String getContextPath() {
        return getTopic();
    }

    /**
     * Get the topic representing this instance with this instance. This is the value to uses when invoking {@link
     * CometEngine#getCometContext}
     *
     * @return topic the topic associated with this instance
     */
    public String getTopic() {
        return topic;
    }

    /**
     * Add an attribute.
     *
     * @param key the key
     * @param value the value
     */
    public void addAttribute(Object key, Object value) {
        attributes.put(key, value);
    }

    /**
     * Retrieve an attribute.
     *
     * @param key the key
     *
     * @return Object the value.
     */
    public Object getAttribute(Object key) {
        return attributes.get(key);
    }

    /**
     * Remove an attribute.
     *
     * @param key the key
     *
     * @return Object the value
     */
    public Object removeAttribute(Object key) {
        return attributes.remove(key);
    }

    /**
     * Add a {@link CometHandler}. The underlying HttpServletResponse will not get committed until {@link
     * CometContext#resumeCometHandler(CometHandler)} is invoked, unless the {@link
     * CometContext#setExpirationDelay(long)} expires. If set to alreadySuspended is set to true, no  I/O operations are
     * allowed inside the {@link CometHandler} as the underlying HttpServletResponse has not been suspended. Adding such
     * {@link CometHandler} is useful only when no I/O operations on the HttpServletResponse are required. Examples
     * include calling a remote EJB when a push operations happens, storing data inside a database, etc.
     *
     * @param handler a new {@link CometHandler}
     *
     * @return The hash code of the handler.
     */
    public int addCometHandler(CometHandler<E> handler) {
        if (handler == null) {
            throw new IllegalStateException(INVALID_COMET_HANDLER);
        }
        if (!CometEngine.getEngine().isCometEnabled()) {
            throw new IllegalStateException(COMET_NOT_ENABLED);
        }
        final Request request = REQUEST_LOCAL.get();
        final Response response = request.getResponse();
        final Connection c = request.getContext().getConnection();
        
        handler.setResponse(response);
        handler.setCometContext(this);
        try {
            initialize(handler);
            final CometCompletionHandler ccHandler = new CometCompletionHandler(handler);
            c.addCloseListener(ccHandler);
            response.suspend(getExpirationDelay(), TimeUnit.MILLISECONDS, ccHandler, new CometTimeoutHandler(handler));
            if (isDetectClosedConnections) {
                // If Detect connection close mode is on - disable keep-alive for this connection
                response.addHeader(Header.Connection, "close");
                
                // Initialize asynchronous reading to be notified when connection
                // is getting closed by peer
                response.getRequest().getInputBuffer().initiateAsyncronousDataReceiving();
            }

            handlers.add(handler);
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
        
        return handler.hashCode();
    }

    /**
     * Recycle this object.
     */
    public void recycle() {
        try {
            notificationHandler.notify(new CometEvent<E>(CometEvent.Type.TERMINATE, this, null), handlers.iterator());
        } catch (IOException ignored) {
        }
        handlers.clear();
        attributes.clear();
        topic = null;
        notificationHandler = null;
        initDefaultValues();
    }

    /**
     * Invoke a {@link CometHandler} using the {@link CometEvent}
     *
     * @param event - {@link CometEvent}
     * @param cometHandler - {@link CometHandler}
     */
    protected void invokeCometHandler(CometEvent event, CometHandler cometHandler) throws IOException {
        if (cometHandler == null) {
            throw new IllegalStateException(INVALID_COMET_HANDLER);
        }
        event.setCometContext(this);
        cometHandler.onEvent(event);
    }

    /**
     * Remove a {@link CometHandler}. If the continuation (connection) associated with this {@link CometHandler} no
     * longer have {@link CometHandler} associated to it, it will be resumed by Grizzly by calling {@link
     * CometContext#resumeCometHandler(CometHandler)}
     *
     * @return <tt>true</tt> if the operation succeeded.
     */
    public boolean removeCometHandler(CometHandler handler) {
        return removeCometHandler(handler, true);
    }

    /**
     * Remove a {@link CometHandler}. If the continuation (connection) associated with this {@link CometHandler} no
     * longer have {@link CometHandler} associated to it, it will be resumed.
     *
     * @param handler The CometHandler to remove.
     * @param resume True is the connection can be resumed if no CometHandler are associated with the underlying
     * SelectionKey.
     *
     * @return <tt>true</tt> if the operation succeeded.
     */
    public boolean removeCometHandler(CometHandler handler, boolean resume) {
        boolean removed = handlers.remove(handler);
        if (removed && resume) {
            handler.getResponse().resume();
        }
        return removed;
    }

    /**
     * Resume the Comet request and remove it from the active {@link CometHandler} list. Once resumed, a CometHandler
     * must never manipulate the HttpServletRequest or HttpServletResponse as those object will be recycled and may be
     * re-used to serve another request.
     * <p/>
     * If you cache them for later reuse by another thread there is a possibility to introduce corrupted responses next
     * time a request is made.
     *
     * @param handler The CometHandler to resume.
     *
     * @return <tt>true</tt> if the operation succeeded.
     */
    public boolean resumeCometHandler(CometHandler handler) throws IOException {
        return interrupt(handler, false);
    }

    /**
     * Interrupt a {@link CometHandler} by invoking {@link CometHandler#onInterrupt}
     *
     * @param handler The {@link CometHandler} encapsulating the suspended connection.
     * @param finishExecution Finish the current execution.
     */
    public boolean interrupt(CometHandler handler, boolean finishExecution) throws IOException {
        final CometContext cometContext = handler.getCometContext();
        final boolean removed = cometContext.removeCometHandler(handler, finishExecution);
        if (removed && !finishExecution) {
            interrupt0(handler, finishExecution);
        }
        return removed;
    }

    /**
     * Interrupt logic in its own method, so it can be executed either async or sync.<br> cometHandler.onInterrupt is
     * performed async due to its functionality is unknown, hence not safe to run in the performance critical selector
     * thread.
     *
     * @param handler The {@link CometHandler} encapsulating the suspended connection.
     * @param finishExecution Finish the current execution.
     */
    protected void interrupt0(CometHandler handler, boolean finishExecution) throws IOException {
        if (finishExecution) {
            try {
                handler.onInterrupt(eventInterrupt);
            } catch (IOException ignored) {
            }
        }
        handler.getResponse().resume();
    }

    /**
     * Return true if this {@link CometHandler} is still active, e.g. there is still a suspended connection associated
     * with it.
     *
     * @return true
     */
    public boolean isActive(CometHandler handler) {
        return handlers.contains(handler);
    }

    /**
     * Notify all {@link CometHandler}. All {@link CometHandler#onEvent} will be invoked with a {@link CometEvent} of
     * type NOTIFY.
     *
     * @param attachment An object shared amongst {@link CometHandler}.
     */
    public void notify(E attachment) throws IOException {
        notify(attachment, CometEvent.Type.NOTIFY);
    }

    /**
     * Notify a single {@link CometHandler#onEvent(CometEvent)}.
     *
     * @param attachment An object shared amongst {@link CometHandler}.
     * @param cometHandler {@link CometHandler} to notify.
     */
    public void notify(E attachment, CometHandler cometHandler) throws IOException {
        notify(attachment, CometEvent.Type.NOTIFY, cometHandler);
    }

    /**
     * Notify a single {@link CometHandler}. The {@link CometEvent#getType()} will determine which {@link CometHandler}
     * method will be invoked:
     * <pre><code>
     * CometEvent.INTERRUPT -> {@link CometHandler#onInterrupt(CometEvent)}
     * CometEvent.Type.NOTIFY -> {@link CometHandler#onEvent(CometEvent)}
     * CometEvent.INITIALIZE -> {@link CometHandler#onInitialize(CometEvent)}
     * CometEvent.TERMINATE -> {@link CometHandler#onTerminate(CometEvent)}
     * CometEvent.READ -> {@link CometHandler#onEvent(CometEvent)}
     * </code></pre>
     *
     * @param attachment An object shared amongst {@link CometHandler}.
     * @param eventType The type of notification.
     * @param cometHandler {@link CometHandler} to notify.
     */
    public void notify(E attachment, CometEvent.Type eventType, CometHandler cometHandler)
        throws IOException {
        if (cometHandler == null) {
            throw new IllegalStateException(INVALID_COMET_HANDLER);
        }
        CometEvent<E> event = new CometEvent<E>(eventType, this, attachment);
        notificationHandler.notify(event, cometHandler);
    }

    /**
     * Notify all {@link CometHandler}. The {@link CometEvent#getType()} will determine which {@link CometHandler}
     * method will be invoked:
     * <pre><code>
     * CometEvent.Type.INTERRUPT -> {@link CometHandler#onInterrupt}
     * CometEvent.Type.NOTIFY -> {@link CometHandler#onEvent}
     * CometEvent.Type.INITIALIZE -> {@link CometHandler#onInitialize}
     * CometEvent.Type.TERMINATE -> {@link CometHandler#onTerminate}
     * CometEvent.Type.READ -> {@link CometHandler#onEvent}
     * </code></pre>
     *
     * @param attachment An object shared amongst {@link CometHandler}.
     * @param eventType The type of notification.
     */
    public void notify(E attachment, CometEvent.Type eventType) throws IOException {
        notificationHandler.notify(new CometEvent<E>(eventType, this, attachment), handlers.iterator());
    }

    /**
     * Initialize the newly added {@link CometHandler}.
     */
    protected void initialize(CometHandler handler) throws IOException {
        handler.onInitialize(eventInitialize);
    }

    @Override
    public String toString() {
        return topic;
    }

    /**
     * Return the <code>long</code> delay, in millisecond, before a request is resumed.
     *
     * @return long the <code>long</code> delay, in millisecond, before a request is resumed.
     */
    public long getExpirationDelay() {
        return expirationDelay;
    }

    /**
     * Set the <code>long</code> delay before a request is resumed.
     *
     * @param expirationDelay the <code>long</code> delay before a request is resumed. Value is in milliseconds.
     */
    public void setExpirationDelay(long expirationDelay) {
        this.expirationDelay = expirationDelay;
    }

    /**
     * Return the current list of active {@link CometHandler}
     *
     * @return the current list of active {@link CometHandler}
     */
    public List<CometHandler> getCometHandlers() {
        return handlers;
    }

    /**
     * Set the current {@link NotificationHandler}
     */
    public void setNotificationHandler(NotificationHandler notificationHandler) {
        this.notificationHandler = notificationHandler;
    }

    /**
     * Return the associated {@link NotificationHandler}
     */
    public NotificationHandler getNotificationHandler() {
        return notificationHandler;
    }

    /**
     * Enable/disable the mechanism, which detects closed connections and notifies
     * user's handlers via
     * {@link CometHandler#onInterrupt(org.glassfish.grizzly.comet.CometEvent)} method.
     * If this feature is on - HTTP pipelining can not be used.
     * 
     * @param isDetectClosedConnections
     */
    public void setDetectClosedConnections(final boolean isDetectClosedConnections) {
        this.isDetectClosedConnections = isDetectClosedConnections;
    }
    
    /**
     * Returns <tt>true</tt> if connection terminate detection is on.
     * If this feature is on - HTTP pipelining can not be used.
     * The feature is enabled by default.
     */
    public boolean isDetectClosedConnections() {
        return isDetectClosedConnections;
    }
    
    private static void notifyOnAsyncRead(CometHandler handler) {
        try {
            handler.onEvent(new CometEvent(CometEvent.Type.READ));
        } catch (IOException e) {
            LOGGER.log(Level.FINE, e.getMessage());
        }
    }

    private class CometCompletionHandler implements CompletionHandler<Response>,
            GenericCloseListener {
        private final CometHandler handler;

        public CometCompletionHandler(CometHandler handler) {
            this.handler = handler;
        }

        @Override
        public void cancelled() {
        }

        @Override
        public void failed(Throwable throwable) {
            try {
                handler.onInterrupt(eventInterrupt);
            } catch (IOException e) {
                LOGGER.log(Level.FINE, "CometCompletionHandler.failed", e.getMessage());
            }
        }

        @Override
        public void completed(Response result) {
            try {
                handler.onInterrupt(eventInterrupt);
            } catch (IOException e) {
                LOGGER.log(Level.FINE, "CometCompletionHandler.completed", e.getMessage());
            }
        }

        @Override
        public void updated(Response result) {
        }

        @Override
        public void onClosed(final Closeable closeable, final CloseType type)
                throws IOException {
            removeCometHandler(handler);
            closeable.removeCloseListener(this);
        }
    }

    private class CometTimeoutHandler implements TimeoutHandler {
        private final CometHandler handler;

        public CometTimeoutHandler(CometHandler handler) {
            this.handler = handler;
        }

        @Override
        public boolean onTimeout(Response response) {
            try {
                handler.onInterrupt(eventInterrupt);
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, e.getMessage());
                throw new RuntimeException(e.getMessage(), e);
            }
            return true;
        }


    }

    private static class CometInputHandler implements ReadHandler {
        final NIOInputStream nioInputStream;
        private final CometHandler handler;

        public CometInputHandler(NIOInputStream nioInputStream,
            CometHandler handler) {
            this.nioInputStream = nioInputStream;
            this.handler = handler;
        }

        @Override
        public void onDataAvailable() {
            notifyOnAsyncRead(handler);
            nioInputStream.notifyAvailable(this);
        }

        @Override
        public void onError(Throwable t) {
        }

        @Override
        public void onAllDataRead() {
            if (nioInputStream.isReady()) {
                notifyOnAsyncRead(handler);
            }
        }
    }
}
