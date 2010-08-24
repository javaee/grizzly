/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2007-2010 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.enterprise.web.connector.grizzly.comet;

import com.sun.enterprise.web.connector.grizzly.AsyncHandler;
import com.sun.enterprise.web.connector.grizzly.AsyncTask;
import com.sun.enterprise.web.connector.grizzly.Pipeline;
import com.sun.enterprise.web.connector.grizzly.AsyncExecutor;
import com.sun.enterprise.web.connector.grizzly.ConcurrentQueue;
import com.sun.enterprise.web.connector.grizzly.LinkedListPipeline;
import com.sun.enterprise.web.connector.grizzly.SelectorThread;
import com.sun.enterprise.web.connector.grizzly.async.AsyncProcessorTask;
import com.sun.enterprise.web.connector.grizzly.ProcessorTask;
import com.sun.enterprise.web.connector.grizzly.SelectorFactory;
import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.util.Iterator;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Main class allowing Comet support on top of Grizzly Asynchronous
 * Request Processing mechanism. This class is the entry point to any
 * component interested to execute Comet request style. Components can be
 * Servlets, JSP, JSF or pure Java class. A component interested to support
 * Comet request must do:
 * <pre><code>
 * (1) First, register the topic on which Comet support will be applied:
 *     CometEngine cometEngine = CometEngine.getEngine()
 *     CometContext cometContext = cometEngine.register(topic)
 * (2) Second, add an instance of {@link CometHandler} to the
 *     {@link CometContext} returned by the register method:
 *     {@link CometContext#addCometHandler}. Executing this operation
 *     will tells Grizzly to suspend the response.
 * (3) Finally, you can {@link CometContext#notify} other {@link CometHandler}
 *     to share information between {@ CometHandler}. When notified,
 *     {@link CometHandler} can decides to push back the data, resume the
 *     response, or simply ignore the content of the notification.
 * </code></pre>
 * You can also select the stage where the suspension of the response happens when
 * registering the {@link CometContext}'s topic (see {@link #register}), which can be
 * before, during or after invoking a {@link Servlet}
 *
 * @author Jeanfrancois Arcand
 */
public final class CometEngine {
    private final static String NOTIFICATION_HANDLER =
        "com.sun.grizzly.comet.notificationHandlerClassName";

    /**
     * Comet thread pool config in the format
     *"<on|off>[-<max-pool-size>[-<min-pool-size>]]"
     */
    private final static String COMET_THREAD_POOL_CONFIG =
        "com.sun.grizzly.comet.thread-pool-config";

    // Disable suspended connection time out.
    public final static int DISABLE_SUSPEND_TIMEOUT = -1;

    // Disable client detection close.
    public final static int DISABLE_CLIENT_DISCONNECTION_DETECTION = 0;


    /**
     * The token used to support BEFORE_REQUEST_PROCESSING polling.
     */
    public final static int BEFORE_REQUEST_PROCESSING = 0;


    /**
     * The token used to support AFTER_SERVLET_PROCESSING polling.
     */
    public final static int AFTER_SERVLET_PROCESSING = 1;


    /**
     * The token used to support BEFORE_RESPONSE_PROCESSING polling.
     */
    public final static int AFTER_RESPONSE_PROCESSING = 2;


    /**
     * Main logger
     */
    private final static Logger logger = SelectorThread.logger();


    /**
     * The {@link Pipeline} used to execute {@link CometTask}
     */
    protected volatile Pipeline pipeline;

    /**
     * Sync object for pipeline update
     */
    private final Object pipelineUpdateSync = new Object();


    /**
     * The single instance of this class.
     */
    private static CometEngine cometEngine;


    /**
     * The current active {@link CometContext} keyed by context path.
     */
    protected ConcurrentHashMap<String,CometContext> activeContexts;


    /**
     * Cache of {@link CometTask} instance
     */
    protected Queue<CometTask> cometTasks;


    /**
     * Cache of {@link CometContext} instance.
     */
    protected Queue<CometContext> cometContexts;


    /**
     * The {@link CometSelector} used to poll requests.
     */
    protected CometSelector cometSelector;


    /**
     * The default class to use when deciding which NotificationHandler
     * to use. The default is DefaultNotificationHandler.
     */
    protected static String notificationHandlerClassName =
        DefaultNotificationHandler.class.getName();


    /**
     * Temporary repository that associate a Thread ID with a Key.
     * NOTE: A ThreadLocal might be more efficient.
     */
    protected ConcurrentHashMap<Long,SelectionKey> threadsId;


    /**
     * Store modified CometContext.
     */
    protected ConcurrentHashMap<Long,CometContext> updatedCometContexts;


    /**
     * The list of registered {@link AsyncProcessorTask}. This object
     * are mainly keeping the state of the Comet request.
     */
    private Queue<AsyncProcessorTask> asyncTasks;


    // Simple lock.
    private ReentrantLock lock = new ReentrantLock();

    private static final ThreadPoolConfig threadPoolConfig = new ThreadPoolConfig();
    
    // --------------------------------------------------------------------- //

    static {
        final String notificationHandlerClass =
                System.getProperty(NOTIFICATION_HANDLER);
        if (notificationHandlerClass != null) {
            setNotificationHandlerClassName(notificationHandlerClass);
        }

        // expected config string format is "<on|off>[-<max-pool-size>[-<min-pool-size>]]"
        final String threadPoolConfigString = System.getProperty(COMET_THREAD_POOL_CONFIG);
        if (threadPoolConfigString != null) {
            threadPoolConfig.configure(threadPoolConfigString);
        }
    }

    /**
     * Create a singleton and initialize all lists required. Also create and
     * start the {@link CometSelector}
     */
    protected CometEngine() {
        activeContexts = new ConcurrentHashMap<String,CometContext>();
        cometTasks = new ConcurrentQueue<CometTask>("CometEngine.cometTasks");
        cometContexts = new ConcurrentQueue<CometContext>("CometEngine.cometContexts");

        cometSelector = new CometSelector(this);
        try{
            cometSelector.start();
        } catch(InterruptedException ex){
            logger.log(Level.SEVERE,"Unable to start CometSelector",ex);
        }

        threadsId = new ConcurrentHashMap<Long,SelectionKey>();
        updatedCometContexts = new ConcurrentHashMap<Long,CometContext>();

        asyncTasks = new ConcurrentQueue<AsyncProcessorTask>("CometEngine.asyncTasks");

        if (threadPoolConfig.isEnabled) {
            final LinkedListPipeline threadPool =
                    new LinkedListPipeline(threadPoolConfig.maxSize,
                    threadPoolConfig.minSize,
                    "Comet-thread-pool", 0);
            
            threadPool.initPipeline();
            threadPool.startPipeline();

            setPipeline(threadPool);
        }
    }


    /**
     * Return a singleton of this Class.
     * @return CometEngine the singleton.
     */
    public synchronized static CometEngine getEngine(){
        if (cometEngine == null) {
            cometEngine = new CometEngine();
        }
        return cometEngine;
    }


    /**
     * Unregister the {@link CometHandler} to the list of the
     * {@link CometContext}. Invoking this method will invoke all
     * {@link CometHandler#onTerminate(com.sun.enterprise.web.connector.grizzly.comet.CometEvent)} before
     * removing the associated {@link CometContext}. Invoking that method
     * will also resume the underlying connection associated with the
     * {@link CometHandler}, similar to what
     * {@link CometContext#resumeCometHandler(com.sun.enterprise.web.connector.grizzly.comet.CometHandler)}
     * do.
     */
    public synchronized CometContext unregister(String topic){
        CometContext cometContext = activeContexts.get(topic);
        try{
            cometContext.notify(cometContext,CometEvent.TERMINATE);
        } catch (IOException ex){
            logger.log(Level.WARNING,"unregister",ex);
        }
        finalizeContext(cometContext);

        return activeContexts.remove(topic);
    }


    /**
     * Register a context path with this {@link CometEngine}. The
     * {@link CometContext} returned will be of type
     * AFTER_SERVLET_PROCESSING, which means the request target (most probably
     * a Servlet) will be executed first and then polled.
     * @param topic the context path used to create the
     *        {@link CometContext}
     * @return CometContext a configured {@link CometContext}.
     */
    public CometContext register(String topic){
        return register(topic,AFTER_SERVLET_PROCESSING);
    }


    /**
     * Register a context path with this {@link CometEngine}. The
     * {@link CometContext} returned will be of type
     * <code>type</code>.
     * @param topic the context path used to create the
     *        {@link CometContext}
     * @param type when the request will be suspended, e.g. {@link BEFORE_REQUEST_PROCESSING},
     * {@link AFTER_SERVLET_PROCESSING} or {@link AFTER_RESPONSE_PROCESSING}
     * @return CometContext a configured {@link CometContext}.
     */
    public synchronized CometContext register(String topic, int type){
        return register(topic, type,CometContext.class);
    }


    /**
     * Instanciate a new {@link CometContext}.
     * @param topic the topic the new {@link CometContext} will represent.
     * @param type when the request will be suspended, e.g. {@link BEFORE_REQUEST_PROCESSING},
     * {@link AFTER_SERVLET_PROCESSING} or {@link AFTER_RESPONSE_PROCESSING}
     * @param contextclass The {@link CometContext} class to instanticate.
     * @return a new {@link CometContext} if not already created, or the
     * existing one.
     */
    public synchronized CometContext register(String topic, int type,
            Class<? extends CometContext> contextclass ) {
        CometContext cometContext = activeContexts.get(topic);
        if (cometContext == null){
            cometContext = cometContexts.poll();
            if (cometContext == null){
                try{
                    cometContext = contextclass.getConstructor(String.class, int.class).newInstance(topic, type);
                } catch (Throwable t) {
                    logger.log(Level.SEVERE,"Invalid CometContext class : ",t);
                    cometContext = new CometContext(topic, type);
                }
                cometContext.setCometSelector(cometSelector);
                NotificationHandler notificationHandler
                    = loadNotificationHandlerInstance
                    (notificationHandlerClassName);
                cometContext.setNotificationHandler(notificationHandler);
                if (notificationHandler != null && (notificationHandler
                            instanceof DefaultNotificationHandler)){
                    ((DefaultNotificationHandler)notificationHandler)
                        .setPipeline(pipeline);
                }
            }
            activeContexts.put(topic,cometContext);
        }
        return cometContext;
    }


    /**
     * Handle an interrupted(or polled) request by matching the current context
     * path with the registered one.
     * If required, the bring the target component (Servlet) to the proper
     * execution stage and then {@link CometContext#notify} the {@link CometHandler}
     * @param apt the current apt representing the request.
     * @return boolean true if the request can be polled.
     */
    protected boolean handle(AsyncProcessorTask apt) throws IOException{

        if (pipeline == null){
            pipeline = apt.getPipeline();
        }

        String topic = apt.getProcessorTask().getRequestURI();
        CometContext cometContext = null;
        if (topic != null){
            cometContext = activeContexts.get(topic);
            try{
                lock.lock();
                if (cometContext != null){
                    NotificationHandler notificationHandler =
                        cometContext.getNotificationHandler();
                    if (notificationHandler instanceof DefaultNotificationHandler){
                        ((DefaultNotificationHandler)notificationHandler)
                            .setPipeline(pipeline);
                    }
                }
            } finally {
                lock.unlock();
            }
        }

        /*
         * If the cometContext is null, it means the context has never
         * been registered. The registration might happens during the
         * Servlet.service() execution so we need to keep a reference
         * to the current thread so we can later retrieve the associated
         * SelectionKey. The SelectionKey is required in order to park the
         * request.
         */
        boolean activateContinuation = true;
        SelectionKey key = apt.getProcessorTask().getSelectionKey();
        threadsId.put(Thread.currentThread().getId(),key);

        int continuationType = (cometContext == null)?
            AFTER_SERVLET_PROCESSING:cometContext.continuationType;

        /*
         * Execute the Servlet.service method. CometEngine.register() or
         * CometContext.addCometHandler() might be invoked during the
         * execution.
         */
        executeServlet(continuationType,apt);

        /*
         * Will return a CometContext instance if and only if the
         * Servlet.service() have invoked CometContext.addCometHandler().
         * If the returned CometContext is null, it means we need to
         * execute a synchronous request.
         */
        cometContext = updatedCometContexts.remove(Thread.currentThread().getId());

        if (cometContext == null){
            activateContinuation = false;
        }

        boolean parkRequest = true;
        if (activateContinuation) {
            // Prevent the Servlet to suspend/resume the request in a single
            // transaction
            CometContext.addInProgressSelectionKey(key);
            // Disable keep-alive
            key.attach(null);
            boolean isBlocking = cometContext.isBlockingNotification();
            // We must initialize in blocking mode in case the connection is resumed
            // during the invocation of that method. If non blocking, there is a possible
            // thread race.
            cometContext.setBlockingNotification(true);
            cometContext.initialize(key);
            cometContext.setBlockingNotification(isBlocking);

            /**
             * The CometHandler has been resumed during the onIntialize method
             * call if getCometHandler return null.
             */
            if (cometContext.getCometHandler(key) != null){
                asyncTasks.offer(apt);
                CometTask cometTask = getCometTask(cometContext, key, null);
                cometTask.setSelectorThread(apt.getSelectorThread());
                cometTask.setExpirationDelay(cometContext.getExpirationDelay());
                cometContext.addActiveCometTask(cometTask);
                if (cometContext.getExpirationDelay() != DISABLE_CLIENT_DISCONNECTION_DETECTION){
                    cometSelector.registerKey(key,cometTask);
                }
            } else{
                parkRequest = false;
            }
            // Now we can allow full control
            CometContext.removeInProgressSelectionKey(key);
        } else {
            parkRequest = false;
        }
        return parkRequest;
    }


    /**
     * Tell the CometEngine to activate Grizzly ARP on that CometContext.
     * This method is called when CometContext.addCometHandler() is
     * invoked.
     * @param threadId the Thread.getId().
     * @param cometContext An instance of CometContext.
     * @return key The SelectionKey associated with the current request.
     */
    protected SelectionKey activateContinuation(Long threadId,
            CometContext cometContext, boolean continueExecution){
        if (!continueExecution){
            updatedCometContexts.put(threadId,cometContext);
        }
        return threadsId.remove(threadId);
    }


    /**
     * Return a clean and configured {@link CometTask}
     * @param cometContext the CometContext to clean
     * @param key The current {@link SelectionKey}
     * @return a new CometContext
     */
     CometTask getCometTask(CometContext cometContext,SelectionKey key,
            Pipeline ctxPipeline){

        if (ctxPipeline == null){
            ctxPipeline = pipeline;
        }

        CometTask cometTask = cometTasks.poll();
        if (cometTask == null){
            cometTask = new CometTask();
        }
        cometTask.setCometContext(cometContext);
        cometTask.setSelectionKey(key);
        cometTask.setCometSelector(cometSelector);
        cometTask.setPipeline(ctxPipeline);
        return cometTask;
    }


    /**
     * Cleanup the {@link CometContext}
     * @param cometContext the CometContext to clean
     */
    private void finalizeContext(CometContext cometContext) {
        Iterator<String> iterator = activeContexts.keySet().iterator();
        String topic;
        while(iterator.hasNext()){
            topic = iterator.next();
            if ( activeContexts.get(topic).equals(cometContext) ){
                activeContexts.remove(topic);
                break;
            }
        }

        for (AsyncProcessorTask apt: asyncTasks){
            flushResponse(apt);
        }
        cometContext.recycle();
        cometContexts.offer(cometContext);
    }


    /**
     * Return the {@link CometContext} associated with the topic.
     * @param topic the topic used to creates the {@link CometContext}
     */
    public CometContext getCometContext(String topic){
        return activeContexts.get(topic);
    }


    /**
     * The {@link CometSelector} is expiring idle {@link SelectionKey},
     * hence we need to resume the current request.
     * @param key the expired SelectionKey
     */
    protected void interrupt(final SelectionKey key) {
        final CometTask cometTask = (CometTask)key.attachment();
        key.attach(null);

        interrupt(cometTask);
    }

    protected void interrupt(final CometTask cometTask) {
        if (cometTask == null){
            if (logger.isLoggable(Level.FINE)){
                logger.fine("CometTask was null");
            }
            return;
        }

        final SelectionKey akey = cometTask.getSelectionKey();
        try{
            if (akey == null) return;

            final Iterator<AsyncProcessorTask> iterator = asyncTasks.iterator();

            AsyncHandler ah = null;
            while (iterator.hasNext()){
                final AsyncProcessorTask apt = iterator.next();
                ah = apt.getAsyncExecutor().getAsyncHandler();
                if (apt.getProcessorTask().getSelectionKey() == akey){
                    iterator.remove();
                    if (akey != null){
                        akey.attach(null);
                    }
                    /**
                     * The connection was parked and resumed before
                     * the CometEngine.handle() terminated.
                     */
                    if (apt.getStage() != AsyncTask.POST_EXECUTE){
                        break;
                    }

                    flushResponse(apt);
                    break;
                }
            }
        } finally {
            returnTask(cometTask);
        }
    }

    /**
     * Return a {@link Task} to the pool.
     */
    protected void returnTask(CometTask cometTask){
        cometTask.recycle();
        cometTasks.offer(cometTask);
    }


    /**
     * Resume the long polling request by unblocking the current
     * {@link SelectionKey}
     */
    protected synchronized void resume(SelectionKey key) {
        Iterator<AsyncProcessorTask> iterator = asyncTasks.iterator();

        AsyncProcessorTask apt = null;
        AsyncExecutor asyncE = null;
        ProcessorTask pt = null;
        while (iterator.hasNext()){
            apt = iterator.next();
            asyncE = apt.getAsyncExecutor();
            if (asyncE == null){
                return;
            }
            pt = apt.getProcessorTask();
            if (pt != null && pt.getSelectionKey() == key){
                iterator.remove();

                /**
                 * The connection was parked and resumed before
                 * the CometEngine.handle() terminated.
                 */
                if (apt.getStage() != AsyncTask.POST_EXECUTE){
                    break;
                }
                flushResponse(apt);
                break;
            }
        }
    }


    /**
     * Complete the asynchronous request.
     */
    private void flushResponse(AsyncProcessorTask apt){
        apt.setStage(AsyncTask.POST_EXECUTE);
        try{
            apt.doTask();
        } catch (IllegalStateException ex){
            if (logger.isLoggable(Level.FINEST)){
                logger.log(Level.FINEST,"flushResponse failed",ex);
            }
        } catch (IOException ex) {
            logger.log(Level.SEVERE,"flushResponse failed",ex);
        }
    }


    /**
     * Bring the cometContext path target (most probably a Servlet) to the processing
     * stage we need for Comet request processing.
     * @param cometContext The CometContext associated with the Servlet
     * @param apt the AsyncProcessorTask
     */
    private void executeServlet(int continuationType,
            AsyncProcessorTask apt){

        try{
            switch (continuationType){
                case BEFORE_REQUEST_PROCESSING:
                    apt.setStage(AsyncTask.PRE_EXECUTE);
                    break;
                case AFTER_SERVLET_PROCESSING:
                    apt.getProcessorTask().invokeAdapter();
                    return;
                case AFTER_RESPONSE_PROCESSING:
                    apt.setStage(AsyncTask.POST_EXECUTE);
                    // Last step, execute directly from here.
                    apt.doTask();
                    break;
                default:
                    throw new IllegalStateException("Invalid state");
            }

            /**
             * We have finished the processing, most probably because we
             * entered the {@link FileCache} or because we of
             * the {@link #AFTER_RESPONSE_PROCESSING} configuration.
             */
            if (apt.getStage() == AsyncTask.POST_EXECUTE){
                return;
            }

            apt.doTask();
        } catch (IOException ex){
            logger.log(Level.SEVERE,"executeServlet",ex);
        }

    }


    /**
     * Return the default {@link NotificationHandler} class name.
     * @return the default {@link NotificationHandler} class name.
     */
    public static String getNotificationHandlerClassName() {
        return notificationHandlerClassName;
    }


    /**
     * Set the default {@link NotificationHandler} class name.
     * @param the default {@link NotificationHandler} class name.
     */
    public static void setNotificationHandlerClassName(String aNotificationHandlerClassName) {
        notificationHandlerClassName = aNotificationHandlerClassName;
    }

    public Pipeline getPipeline() {
        return pipeline;
    }

    public void setPipeline(final Pipeline pipeline) {
        synchronized(pipelineUpdateSync) {
            int oldSize = 0;
            if (this.pipeline != null) {
                oldSize = this.pipeline.getMaxThreads();
                this.pipeline.stopPipeline();
            }

            final int delta = pipeline.getMaxThreads() - oldSize;

            try {
                SelectorFactory.changeSelectorsBy(delta);
            } catch (Exception e) {
                logger.log(Level.WARNING, "Error resizing the selector pool", e);
            }

            this.pipeline = pipeline;
        }
    }

    /**
     * Util to load classes using reflection.
     */
    protected final static NotificationHandler loadNotificationHandlerInstance(String className){
        Class clazz = null;
        try{
            clazz = Class.forName(className,true,
                    Thread.currentThread().getContextClassLoader());
            return (NotificationHandler)clazz.newInstance();
        } catch (Throwable t) {
            logger.log(Level.WARNING,"Invalid NotificationHandler: ",t);
        }
        return new DefaultNotificationHandler();
    }


    /**
     * Return the current logger.
     */
    public final static Logger logger(){
        return logger;
    }

    private static class ThreadPoolConfig {
        private boolean isEnabled = false;
        private int minSize = 1;
        private int maxSize = 5;

        /**
         * Parse config string in the format
         *"<on|off>[-<max-pool-size>[-<min-pool-size>]]"
         *
         * @param threadPoolConfigString <on|off>[-<max-pool-size>[-<min-pool-size>]]
         */
        private void configure(String threadPoolConfigString) {
            String[] poolConfig = threadPoolConfigString.split("-");
            if (poolConfig.length > 0) {
                final String useThreadPoolConfig = poolConfig[0];
                isEnabled = useThreadPoolConfig.equalsIgnoreCase("on")  ||
                        useThreadPoolConfig.equalsIgnoreCase("yes") ||
                        useThreadPoolConfig.equalsIgnoreCase("enabled") ||
                        useThreadPoolConfig.equalsIgnoreCase("true");
            }

            if (poolConfig.length > 1) {
                final String threadPoolMaxConfig = poolConfig[1];
                try {
                    maxSize = Integer.parseInt(threadPoolMaxConfig);
                } catch (Exception ignored) {
                }
            }

            if (poolConfig.length > 2) {
                final String threadPoolMinConfig = poolConfig[2];
                try {
                    minSize = Integer.parseInt(threadPoolMinConfig);
                } catch (Exception ignored) {
                }
            }
        }
    }
}
