/*
 * 
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2007-2008 Sun Microsystems, Inc. All rights reserved.
 * 
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License. You can obtain
 * a copy of the License at https://glassfish.dev.java.net/public/CDDL+GPL.html
 * or glassfish/bootstrap/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 * 
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/bootstrap/legal/LICENSE.txt.
 * Sun designates this particular file as subject to the "Classpath" exception
 * as provided by Sun in the GPL Version 2 section of the License file that
 * accompanied this code.  If applicable, add the following below the License
 * Header, with the fields enclosed by brackets [] replaced by your own
 * identifying information: "Portions Copyrighted [year]
 * [name of copyright owner]"
 * 
 * Contributor(s):
 * 
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
 *
 */

package com.sun.grizzly.comet;

import com.sun.grizzly.arp.AsyncTask;
import com.sun.grizzly.http.SelectorThread;
import com.sun.grizzly.arp.AsyncProcessorTask;
import com.sun.grizzly.http.ProcessorTask;
import com.sun.grizzly.util.ExtendedThreadPool;
import com.sun.grizzly.util.LinkedTransferQueue;
import com.sun.grizzly.util.SelectorFactory;
import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
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
 * @author Gustav Trede
 */
public class CometEngine {

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
    protected final static Logger logger = SelectorThread.logger();

    /**
     * 
     */
    private final static IllegalStateException ISE = new IllegalStateException("Invalid state");


    /**
     * The {@link ExecutorService} used to execute {@link CometTask}
     */
    protected ExtendedThreadPool threadPool;


    /**
     * The single instance of this class.
     */
    protected final static CometEngine cometEngine = new CometEngine();


    /**
     * The current active {@link CometContext} keyed by context path.
     */
    protected final ConcurrentHashMap<String,CometContext> activeContexts;


    /**
     *  cached CometContexts
     */
    protected final LinkedTransferQueue<CometContext> cometContextCache;
    
    
    /**
      * Is Grizzly ARP enabled? By default we set it to false.
     */
    private static volatile boolean isCometSupported;


    /**
     * Store updatedCometContextCometContext.
     */
    protected final static ThreadLocal<CometTask> updatedContexts = new ThreadLocal<CometTask>();
            
    /**
     * Creat a singleton and initialize all lists required.
     */
    protected CometEngine() {       
        cometContextCache = new LinkedTransferQueue<CometContext>();
        activeContexts    = new ConcurrentHashMap<String,CometContext>(16,0.75f,64);

        /*ExecutorService tpe = new ThreadPoolExecutor(
                64,
                64,
                30L,
                TimeUnit.SECONDS,
                //new LinkedTransferQueue(),
               new LinkedBlockingQueue<Runnable>(),
                new ThreadFactory() {
                    private final AtomicInteger counter = new AtomicInteger();
                    public Thread newThread(Runnable r) {
                        //return new Thread(r, "CometWorker-"+counter.incrementAndGet());
                        return new WorkerThreadImpl(null, "CometWorker-"+counter.incrementAndGet(), r, 0);
                    }
                }); */
        
        //ExecutorService tpe = threadPool = new DefaultExecutorService(4, 8, 30,
              //  TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>(), "CometWorker-");
        ExtendedThreadPool tpe = new com.sun.grizzly.util.FixedThreadPool(8,"CometWorker");
        setThreadPool(tpe);
    }

    /**
     * Return true is Comet is enabled, e.g. {@link SelectorThread.setAsyncExectution}
     * has been set to <tt>true</tt>
     * @return
     */
    protected boolean isCometEnabled(){
        return isCometSupported;
    }
    

    /**
     * Return a singleton of this Class.
     * @return CometEngine the singleton.
     */
    public static CometEngine getEngine(){
        return cometEngine;
    }

    /**
     * sets the default threadpool that DefaultNotificationHandler use.
     * shuttdownnow is called on the existing threadpool.
     * does notupdate existing notificationhandlers.
     */
    public void setThreadPool(ExtendedThreadPool threadPool) {
        if (threadPool != null){
            int oldsize = 0;
            if (this.threadPool != null){
                oldsize = this.threadPool.getMaximumPoolSize();
                this.threadPool.shutdownNow();
            }
            this.threadPool = threadPool;
            int delta = threadPool.getMaximumPoolSize() - oldsize;
            try {
                System.err.println("comet stuff delta:"+delta);
                SelectorFactory.changeSelectorsBy(delta);
            } catch (IOException ex) {
                logger.log(Level.WARNING, "comet failed to resize Selector cache", ex);
            }
        }
    }

    /**
     * returns the threadpool comet is using
     * @return ExtendedThreadPool
     */
    public ExtendedThreadPool getThreadPool() {
        return threadPool;
    }


    /**
     * Unregister the {@link CometHandler} to the list of the
     * {@link CometContext}. Invoking this method will invoke all
     * {@link CometHandler#onTerminate(com.sun.grizzly.comet.CometEvent)} before
     * removing the associated {@link CometContext}. Invoking that method
     * will also resume the underlying connection associated with the 
     * {@link CometHandler}, similar to what 
     * {@link CometContext#resumeCometHandler(com.sun.grizzly.comet.CometHandler)}
     * do.
     */
    public CometContext unregister(String topic){
        CometContext cometContext = activeContexts.remove(topic);
        if (cometContext != null){
            cometContext.recycle();            
        }
        return cometContext;
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
    public CometContext register(String topic, int type){
        return register(topic, type, DefaultNotificationHandler.class);
    }

    /**
     * Instanciate a new {@link CometContext}.
     * @param topic the topic the new {@link CometContext} will represent.
     * @param type when the request will be suspended, e.g. {@link BEFORE_REQUEST_PROCESSING},
     * {@link AFTER_SERVLET_PROCESSING} or {@link AFTER_RESPONSE_PROCESSING}     
     * @return a new {@link CometContext} if not already created, or the
     * existing one.
     */
    public CometContext register(String topic, int type,
            Class<? extends NotificationHandler> notificationClass  ) {
        // Double checked locking used used to prevent the otherwise static/global 
        // locking, cause example code does heavy usage of register calls
        // for existing topics from http get calls etc.
        CometContext cometContext = activeContexts.get(topic);
        if (cometContext == null){
            synchronized(activeContexts){
                cometContext = activeContexts.get(topic);
                if (cometContext == null){
                    cometContext = cometContextCache.poll();
                    if (cometContext != null){
                        cometContext.topic = topic;
                    }else{
                        cometContext = new CometContext(topic, type);
                    }
                    NotificationHandler notificationHandler = null;
                    try{
                        notificationHandler = notificationClass.newInstance();
                    } catch (Throwable t) {
                        logger.log(Level.SEVERE,"Invalid NotificationHandler class : "
                                + notificationClass.getName() + " Using default.",t);
                        notificationHandler = new DefaultNotificationHandler();
                    }
                    cometContext.setNotificationHandler(notificationHandler);
                    if (notificationHandler != null && (notificationHandler
                                instanceof DefaultNotificationHandler)){
                        ((DefaultNotificationHandler)notificationHandler)
                            .setThreadPool(threadPool);
                    }
                    activeContexts.put(topic,cometContext);
                }
                
            }
        }
        cometContext.continuationType = type;
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

        // That means Grizzly ARP is invoking us via AsyncFilter.
        if (!isCometSupported){
            //volatile read is in general cheaper then a write.
            isCometSupported = true;
        }
        
        String topic = apt.getAsyncExecutor().getProcessorTask().getRequestURI();
        CometContext cometContext = topic==null?null:activeContexts.get(topic);

        /* If the cometContext is null, it means the context has never
         * been registered. The registration might happens during the
         * Servlet.service() execution so we need to keep a reference
         * to the current thread so we can later retrieve the associated
         * SelectionKey. The SelectionKey is required in order to park the request. 
         */        
        int continuationType = (cometContext == null)?
            AFTER_SERVLET_PROCESSING:cometContext.continuationType;
        
        /* Execute the Servlet.service method. CometEngine.register() or
         * CometContext.addCometHandler() might be invoked during the execution. 
         */
        executeServlet(continuationType,apt);
        /* Will return a CometContext instance if and only if the
         * Servlet.service() have invoked CometContext.addCometHandler().
         * If the returned CometContext is null, it means we need to 
         * execute a synchronous request. 
         */
        CometTask cometTask = updatedContexts.get();
        if (cometTask != null) {
            //need to impl tlocal that gets and sets null in one efficent operation
            updatedContexts.set(null);
            cometContext = cometTask.getCometContext();
            if (cometTask.upcoming_op_isread){  //alreadySuspended
                cometTask.upcoming_op_isread = false;
                //need to set dumykey in cometTask ?
                cometContext.addActiveHandler(cometTask);
                return false;
            }            
            cometTask.setAsyncProcessorTask(apt);
            if (cometContext.getExpirationDelay() != -1){
                cometTask.setTimeout(System.currentTimeMillis());
            }
            SelectionKey mainKey = apt.getAsyncExecutor().getProcessorTask().getSelectionKey(); 
            if (mainKey.isValid()){
                try{
                    mainKey.interestOps(SelectionKey.OP_READ);
                    mainKey.attach(cometTask);
                    cometContext.initialize(cometTask.getCometHandler());
                }catch(Exception e){
                    mainKey.attach(Long.MIN_VALUE);
                    return false;
                }
                cometContext.addActiveHandler(cometTask);
                return true;
            }
        }
        return false;
    }


    /**
     * Return the {@link CometContext} associated with the topic.
     * @param topic the topic used to creates the {@link CometContext}
     */
    public CometContext getCometContext(String topic){
        return activeContexts.get(topic);
    }

    /**
     * Ensures {@link ProcessorTask} is recycled and that {@link Selectionkey} is canceled when needed.
     *
     * @param task
     * @param aptflush
     * @param cancelkey
     */
    protected void flushPostExecute(final CometTask task, boolean aptflush,boolean cancelkey) {
        AsyncProcessorTask apt = task.getAsyncProcessorTask();        
        ProcessorTask p = task.getAsyncProcessorTask().getAsyncExecutor().getProcessorTask();
        p.setReRegisterSelectionKey(false);
        p.setAptCancelKey(cancelkey);
        if (!aptflush){
            p.terminateProcess();
        }else{
            if (apt.getStage() == AsyncTask.POST_EXECUTE){
                try{
                    //All comet IO operations sync on handler except close
                    synchronized(task.cometHandler){
                        apt.doTask();
                    }
                } catch (IllegalStateException ex){
                    if (logger.isLoggable(Level.FINEST)){
                        logger.log(Level.FINEST,"Resuming Response failed at aptflush",ex);
                    }
                } catch (Throwable ex) {
                    logger.log(Level.SEVERE,"Resuming  failed at aptflush",ex);
                }
            }else{
                logger.warning("APTflush called at wrong stage");
            }
        }
    }

    
    /**
     * Bring the cometContext path target (most probably a Servlet) to the processing
     * stage we need for Comet request processing.
     * @param cometContext The CometContext associated with the Servlet
     * @param apt the AsyncProcessorTask
     */
    private void executeServlet(int continuationType, AsyncProcessorTask apt){
        try{        
            switch (continuationType){
                case BEFORE_REQUEST_PROCESSING:
                    apt.setStage(AsyncTask.PRE_EXECUTE);
                    break;
                case AFTER_SERVLET_PROCESSING:
                    apt.getAsyncExecutor().getProcessorTask().invokeAdapter();
                    return;
                case AFTER_RESPONSE_PROCESSING:
                    apt.setStage(AsyncTask.POST_EXECUTE);
                    // Last step, execute directly from here.
                    apt.doTask();
                    break;
                default:
                    throw ISE;
            }

            /**
             * We have finished the processing, most probably because we
             * entered the {@link FileCache} or because we of
             * the {@link #AFTER_RESPONSE_PROCESSING} configuration.
             */
            if (apt.getStage() != AsyncTask.POST_EXECUTE){
                apt.doTask();
            }                   
        } catch (IOException ex){
            logger.log(Level.SEVERE,"executeServlet",ex);
        }
    }

    /**
     * Return the current logger.
     */
    public final static Logger logger(){
        return logger;
    }
}
