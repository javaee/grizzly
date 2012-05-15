/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2007-2012 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.grizzly.comet;

import com.sun.grizzly.comet.concurrent.DefaultConcurrentCometHandler;
import com.sun.grizzly.http.SelectorThread;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * The main object used by {@link CometHandler} and Servlet to push information
 * amongs suspended request/response. The {@link CometContext} is always available for {@link CometHandler}
 * and can be used to {@link #notify}, or share information with other 
 * {@link CometHandler}. This is the equivalent of server push as the CometContext
 * will invoke all registered CometHandler ({@link #addCometHandler}) sequentially
 * or using a thread pool when the {@link #setBlockingNotification} is set to
 * <tt>false</tt>
 * <p>
 * A CometContext can be considered as a topic where CometHandler register for 
 * information. A CometContext can be shared amongs Servlet of the same application,
 * or globally accros all deployed web applications. Normally, a CometContext
 * is created using a topic's name like:</p><p><pre><code>
 * 
 * CometEngine ce = CometEngine.getEngine();
 * CometContext cc = ce.registerContext("MyTopic");
 * cc.setBlockingNotification(false); // Use multiple thread to execute the server push
 * 
 * and then inside a Servlet.service() method, you just need to call:
 * 
 * cc.addCometListener(myNewCometListener());
 * cc.notify("I'm pushing data to all registered CometHandler");
 * </core></pre></p>
 * <p>
 * As soom as {@link #addCometHandler} is invoked, Grizzly will automatically
 * <strong>suspend</strong> the request/response (will not commit the response).
 * A response can be <strong>resumed</strong> by invoking {@link #resumeCometHandler},
 * which will automatically commit the response and remove the associated
 * CometHandler from the CometContext. 
 * 
 * A CometContext uses a {@link NotificationHandler} to invoke, using the calling
 * thread or a Grizzly thread pool, all CometHandler than have been added using the
 * {@link #addCometHandler}. A {@link NotificationHandler} can be used to filter
 * or transform the content that will eventually be pushed back to all connected
 * clients. You can also use a {@link NotificationHandler} to throttle push like
 * invoking only a subset of the CometHandler, etc.
 * 
 * Idle suspended connection can be timed out by configuring the {@link #setExpirationDelay(long)}.
 * The value needs to be in milliseconds. If there is no I/O operations and no
 * invokation of {@link #notify} during the expiration delay, Grizzly
 * will resume all suspended connection. An application will have a chance to 
 * send back data using the connection as Grizzly will invoke the {@link CometHandler#onInterrupt}
 * before resuming the connection. Note that setting the expiration delay to -1
 * disable the above mechanism, e.g. idle connection will never get resumed
 * by Grizzly.
 * 
 * </p>
 * <p>
 * Attributes can be added/removed the same way HttpServletSession
 * is doing. It is not recommended to use attributes if this 
 * {@link CometContext} is not shared amongst multiple
 * context path (uses HttpServletSession instead).
 * </p> 
 * @author Jeanfrancois Arcand
 * @author Gustav Trede
 */
public class CometContext<E> {
    
    /**
     * Generic error message
     */
    protected final static String INVALID_COMET_HANDLER = "CometHandler cannot be null. " 
            + "This CometHandler was probably resumed and an invalid " 
            +  "reference was made to it.";
    
    protected final static String ALREADY_REMOVED = 
            "CometHandler already been removed or invalid.";

    private final static String COMET_NOT_ENABLED =
            "Make sure you have enabled Comet "
            + "or make sure the Thread invoking that method is the same "
            + "as the Servlet.service() Thread.";
    
    /**
     * Main logger
     */
    protected final static Logger logger = SelectorThread.logger();  
 
     
    /**
     * Attributes placeholder.
     */
    private final ConcurrentHashMap attributes;
    
    
    /**
     * The context path associated with this instance.
     */
    protected String topic;
    
    
    /**
     * The {@link CometContext} continuationType. See {@link CometEngine}
     */
    protected int continuationType = CometEngine.AFTER_SERVLET_PROCESSING;
    
    
    /**
     * The default delay expiration before a {@link CometContext}'s
     * {@link CometHandler} are interrupted.
     */
    private long expirationDelay ;
    
    
    /**
     * <tt>true</tt> if the caller of {@link #notify} should block when 
     * notifying other CometHandler.
     */
    protected boolean blockingNotification;

    
    /**
     * The default NotificationHandler.
     */
    protected NotificationHandler notificationHandler;
    

     /**
      * time stamp for next idle check
      * used to limit the frequency of actual performed resets.
      */
    private volatile long nextIdleClear;

    /**
     * The list of registered {@link CometHandler}
     */
    protected final ConcurrentHashMap<CometHandler,CometTask> handlers;

    protected final CometEvent eventInterrupt;

    protected final CometEvent eventTerminate;

    private final CometEvent eventInitialize;

    /**
     * true, if we want to enable mechanism, which detects closed connections,
     * or false otherwise. The mentioned mechanism should be disabled if we
     * expect client to use HTTP pipelining.
     */
    private boolean isDetectClosedConnections = true;
    
    /**
     * Create a new instance
     * @param contextTopic the context path
     * @param type when the Comet processing will happen (see {@link CometEngine}).
     */
    public CometContext(String contextTopic, int type) {
        topic = contextTopic;
        continuationType = type;
        attributes = new ConcurrentHashMap();
        handlers = new ConcurrentHashMap<CometHandler,CometTask>(16,0.75f,64);
        eventInterrupt = new CometEvent<CometContext>(CometEvent.INTERRUPT,this);
        eventInitialize = new CometEvent<CometContext>(CometEvent.INITIALIZE,this);
        eventTerminate = new CometEvent<CometContext>(CometEvent.TERMINATE,this,this);
        initDefaultValues();
    }
    
    /**
     * init of default values.
     * used by constructor and the cache recycle mechanism
     */
    private void initDefaultValues() {
        blockingNotification = false;
        expirationDelay = 30*1000;
        nextIdleClear = 0;
    }

    /**
     * Get the context path associated with this instance.
     * @return topic the context path associated with this instance
     * @deprecated - use getTopic.
     */
    public String getContextPath(){
        return getTopic();
    }
    
    /**
     * Get the topic representing this instance with this instance. This is
     * the value to uses when invoking {@link CometEngine#getCometContext}
     * @return topic the topic associated with this instance
     */
    public String getTopic(){
        return topic;
    }
    
    
    /**
     * Add an attibute. 
     * @param key the key
     * @param value the value
     */
    public void addAttribute(Object key, Object value){
        attributes.put(key,value);
    }

    
    /**
     * Retrieve an attribute.
     * @param key the key
     * @return Object the value.
     */
    public Object getAttribute(Object key){
        return attributes.get(key);
    }    
    
    
    /**
     * Remove an attribute.
     * @param key the key
     * @return Object the value
     */
    public Object removeAttribute(Object key){
        return attributes.remove(key);
    }  
    
  
    /**
     * Add a {@link CometHandler}. The underlying HttpServletResponse will
     * not get commited until {@link CometContext#resumeCometHandler(CometHandler)}
     * is invoked, unless the {@link CometContext#setExpirationDelay(long)} expires.
     * If set to alreadySuspended is set to true, no  I/O operations are allowed 
     * inside the {@link CometHandler} as the underlying HttpServletResponse
     * has not been suspended. Adding such {@link CometHandler} is usefull only when
     * no I/O operations on the HttpServletResponse are required. Examples
     * include calling a remote EJB when a push operations happens, storing
     * data inside a database, etc. 
     * 
     * @param handler a new {@link CometHandler} 
     * @param alreadySuspended Add the Comethandler but don't suspend
     *        the HttpServletResponse. If set to true, no
     *        I/O operations are allowed inside the {@link CometHandler} as the
     *        underlying HttpServletResponse has not been suspended, unless
     *        the {@link CometHandler} is shared amongs more than one {@link CometContext}
     *       
     * @return The {@link CometHandler#hashCode} value.
     */
    public int addCometHandler(CometHandler handler, boolean alreadySuspended){
        if (handler == null){
            throw new IllegalStateException(INVALID_COMET_HANDLER);
        }
        
        if (!CometEngine.getEngine().isCometEnabled()){
            throw new IllegalStateException(COMET_NOT_ENABLED);
        }
        // is it ok that we only manage one addcomethandler call per thread ?
        // else we can use a list of handlers to add inside tlocal
        CometTask cometTask = new CometTask(this,handler);
        cometTask.upcoming_op_isread = alreadySuspended;
        CometEngine.updatedContexts.set(cometTask);
        return handler.hashCode();
    }
    
    
    /**
     * Add a {@link CometHandler} which will starts the process of suspending
     * the underlying response. The underlying HttpServletResponse will
     * not get committed until {@link CometContext#resumeCometHandler(CometHandler)}
     * is invoked, unless the {@link CometContext#setExpirationDelay(long)} expires.
     * @param handler a new {@link CometHandler}
     */
    public int addCometHandler(CometHandler handler){
        return addCometHandler(handler,false);
    }
    
    
    /**
     * Retrieve a {@link CometHandler} using its based on its {@link CometHandler#hashCode};
     */
    @Deprecated
    public CometHandler getCometHandler(int hashCode){
        for (CometHandler handler:handlers.keySet()){
            if (handler.hashCode() == hashCode )
               return handler;
        }
        return null;
    }   

    /**
     * Recycle this object.
     */
    public void recycle(){
        try{
            notify(this,CometEvent.TERMINATE);
        } catch (IOException ex) {

        }
        handlers.clear();
        attributes.clear();
        topic = null;
        notificationHandler = null;
        initDefaultValues();
        // add check for datastructure size, if cometcontext had large
        // datastructes its probably not optimal to waste RAM with caching it        
        CometEngine.cometEngine.cometContextCache.offer(this);
    }


    /**
     * adds a {@link CometTask} to the active set
     * @param cometTask {@link CometTask}
     */
    protected void addActiveHandler(CometTask cometTask){
        handlers.put(cometTask.cometHandler, cometTask);
    }
        
    /**
     * Invoke a {@link CometHandler} using the {@link CometEvent}
     * @param event - {@link CometEvent}
     * @param cometHandler - {@link CometHandler}
     * @throws IOException
     */
    protected void invokeCometHandler(CometEvent event, CometHandler cometHandler) throws IOException{
        if (cometHandler == null){
            throw new IllegalStateException(INVALID_COMET_HANDLER);
        }
        event.setCometContext(this);
        if (cometHandler instanceof DefaultConcurrentCometHandler){
            ((DefaultConcurrentCometHandler)cometHandler).enqueueEvent(event);
        }else{
            synchronized(cometHandler){
                cometHandler.onEvent(event);
            }
        }
    }    
    
    
    /**
     * Remove a {@link CometHandler}. If the continuation (connection)
     * associated with this {@link CometHandler} no longer have 
     * {@link CometHandler} associated to it, it will be resumed by Grizzly 
     * by calling {@link CometContext#resumeCometHandler(CometHandler)}
     * @return <tt>true</tt> if the operation succeeded.
     */
    public boolean removeCometHandler(CometHandler handler){
        return removeCometHandler(handler,true);
    }
     
    
    /**
     * Remove a {@link CometHandler}. If the continuation (connection)
     * associated with this {@link CometHandler} no longer have 
     * {@link CometHandler} associated to it, it will be resumed.
     * @param handler The CometHandler to remove.
     * @param resume True is the connection can be resumed if no CometHandler
     *                    are associated with the underlying SelectionKey.
     * @return <tt>true</tt> if the operation succeeded.
     */
    public boolean removeCometHandler(CometHandler handler,boolean resume){
        CometTask task = handlers.remove(handler);
        if (task != null){
            if (resume){
                CometEngine.getEngine().flushPostExecute(task,false);
            }
            return true;
        }
        return false;
    }
    
    
    /**
     * Remove a {@link CometHandler} based on its hashcode. Return <tt>true</tt>
     * if the operation was sucessfull.
     * O(n) performance.
     * @param hashCode The hashcode of the CometHandler to remove.
     * @return <tt>true</tt> if the operation succeeded.
     */
    @Deprecated
    public boolean removeCometHandler(int hashCode){
        CometHandler handler_ = null;
        for (CometHandler handler:handlers.keySet()){
            if (handler.hashCode() == hashCode){
                handler_ = handler;
                break;
            }
        }
        return handler_ != null && handlers.remove(handler_) != null;
    }

    /**
     * Resume the Comet request and remove it from the active {@link CometHandler} list. Once resumed,
     * a CometHandler must never manipulate the HttpServletRequest or HttpServletResponse as
     * those object will be recycled and may be re-used to serve another request. 
     * 
     * If you cache them for later reuse by another thread there is a
     * possibility to introduce corrupted responses next time a request is made.
     * @param handler The CometHandler to resume.
     * @return <tt>true</tt> if the operation succeeded.
     */
    public boolean resumeCometHandler(CometHandler handler){
        return CometEngine.getEngine().interrupt(handlers.get(handler),false);
    }


    /**
     * Return true if this {@link CometHandler} is still active, e.g. there is 
     * still a suspended connection associated with it.
     * 
     * @return true 
     */
    public boolean isActive(CometHandler handler){
        return handlers.containsKey(handler);
    }
    
    /**
     * Return the internal list of active {@link CometHandler}
     * @return Return the internal list of active {@link CometHandler}
     */
    protected ConcurrentHashMap<CometHandler,CometTask> handlers(){
        return handlers;
    }

    /**
     * Notify all {@link CometHandler}. All
     * {@link CometHandler#onEvent} will be invoked with a {@link CometEvent}
     * of type NOTIFY.
     * @param attachment An object shared amongst {@link CometHandler}. 
     */
    public void notify(final Object attachment) throws IOException{
        notify(attachment, CometEvent.NOTIFY);
    }
    
    
    /**
     * Notify a single {@link CometHandler}. The {@link CometEvent#getType()}
     * will determine which {@link CometHandler}  method will be invoked:
     * <pre><code>
     * CometEvent.INTERRUPT -> {@link CometHandler#onInterrupt}
     * CometEvent.NOTIFY -> {@link CometHandler#onEvent}
     * CometEvent.INITIALIZE -> {@link CometHandler#onInitialize}
     * CometEvent.TERMINATE -> {@link CometHandler#onTerminate}
     * CometEvent.READ -> {@link CometHandler#onEvent}
     * </code></pre>
     * @param attachment An object shared amongst {@link CometHandler}. 
     * @param eventType The type of notification.
     * @param cometHandlerID Notify a single CometHandler.
     * @deprecated - use notify(attachment,eventType,CometHandler;
     */
    public void notify(final Object attachment,final int eventType,final int cometHandlerID)
        throws IOException{   
        notify(attachment,eventType, getCometHandler(cometHandlerID));
    }
    
    /**
     * Notify a single {@link CometHandler#onEvent(CometEvent}. 
     * @param attachment An object shared amongst {@link CometHandler}. 
     * @param {@link CometHandler} to notify.
      */
    public void notify(final Object attachment,final CometHandler cometHandler)
        throws IOException{
        notify(attachment,CometEvent.NOTIFY,cometHandler);
    }  
    
    /**
     * Notify a single {@link CometHandler}. The {@link CometEvent#getType()}
     * will determine which {@link CometHandler}  method will be invoked:
     * <pre><code>
     * CometEvent.INTERRUPT -> {@link CometHandler#onInterrupt}
     * CometEvent.NOTIFY -> {@link CometHandler#onEvent}
     * CometEvent.INITIALIZE -> {@link CometHandler#onInitialize}
     * CometEvent.TERMINATE -> {@link CometHandler#onTerminate}
     * CometEvent.READ -> {@link CometHandler#onEvent}
     * </code></pre>
     * @param attachment An object shared amongst {@link CometHandler}. 
     * @param eventType The type of notification.
     * @param {@link CometHandler} to notify.
      */
    public void notify(final Object attachment,final int eventType,final CometHandler cometHandler)
        throws IOException{
        if (cometHandler == null){
            throw new IllegalStateException(INVALID_COMET_HANDLER);
        }
        CometEvent event = new CometEvent(eventType,this,attachment);
        notificationHandler.setBlockingNotification(blockingNotification);        
        notificationHandler.notify(event,cometHandler);
        if (event.getType() != CometEvent.TERMINATE
            && event.getType() != CometEvent.INTERRUPT) {
            resetSuspendIdleTimeout();
        }
    }    

      
    /**
     * Notify all {@link CometHandler}. The {@link CometEvent#getType()}
     * will determine which {@link CometHandler}
     * method will be invoked:
     * <pre><code>
     * CometEvent.INTERRUPT -> {@link CometHandler#onInterrupt}
     * CometEvent.NOTIFY -> {@link CometHandler#onEvent}
     * CometEvent.INITIALIZE -> {@link CometHandler#onInitialize}
     * CometEvent.TERMINATE -> {@link CometHandler#onTerminate}
     * CometEvent.READ -> {@link CometHandler#onEvent}
     * </code></pre>
     * @param attachment An object shared amongst {@link CometHandler}. 
     * @param eventType The type of notification.
     */   
    public void notify(Object attachment,int eventType) throws IOException {
        CometEvent event = new CometEvent(eventType,this,attachment);
        Iterator<CometHandler> iterator = handlers.keySet().iterator();
        notificationHandler.setBlockingNotification(blockingNotification);
        notificationHandler.notify(event,iterator);
        if (event.getType() != CometEvent.TERMINATE
            && event.getType() != CometEvent.INTERRUPT) {
            resetSuspendIdleTimeout();
        }
    }


    /**
     * Initialize the newly added {@link CometHandler}.
     */
     protected void initialize(CometHandler handler) throws IOException {
        handler.onInitialize(eventInitialize); 
    }


     
    /**
     * Reset the current timestamp on a suspended connection.
     */
    protected void resetSuspendIdleTimeout(){
        if (expirationDelay != -1){            
            long timestamp = System.currentTimeMillis();
            if (timestamp > nextIdleClear){
                boolean update=false;
                synchronized(handlers){
                    if (timestamp > nextIdleClear){
                        nextIdleClear = timestamp+1000;
                        update = true;
                    }
                }
                if (update){
                    for (CometTask cometTask:handlers.values()){
                        cometTask.setTimeout(timestamp);
                    }
                }
            }
        }
    }
    
    
    /**
     * Register for asynchronous read event (CometEvent#READ}. As soon as Grizzly detects
     * there is some bytes available for read, your {@link CometHandler#onEvent(CometEvent)} will be invoked.
     * {@link CometEvent#attachment()} will return an instance of {@link CometReader}
     * that can be used to asynchronously read the available bytes.
     * 
     * If your client supports http pipelining,
     * invoking this method might result in a state where your CometHandler
     * is invoked with a {@link CometReader} that will read the next http request. In that
     * case, it is strongly recommended to not use that method unless your
     * CometHandler can handle the http request.
     * 
     * @param handler The CometHandler that will be invoked.
     */
    public boolean registerAsyncRead(CometHandler handler){
        return doAsyncRegister(handler, SelectionKey.OP_READ);
    }

            
    /**
     * Register for asynchronous write event (CometEvent#WRITE} .As soon as Grizzly detects
     * there is some OS buffer available for write operations, your 
     * {@link CometHandler#onEvent(CometEvent)} will be invoked.
     * {@link CometEvent#attachment()} will return an instance of {@link CometWriter}
     * that can be used to asynchronously write the available bytes.
     * @param handler The CometHandler that will be invoked.
     */
    public boolean registerAsyncWrite(CometHandler handler){
        return doAsyncRegister(handler, SelectionKey.OP_WRITE);
    }

    /**
     * Register for asynchronous read or write I/O operations.
     * @param handler the CometHandler that will be invoked
     * @param interest The read or write interest.
     * @return true if the operation worked.
     */
    private boolean doAsyncRegister(CometHandler handler, int interest){
        if (handler != null) {
            CometTask task = handlers.get(handler);
            if (task != null) {
                SelectionKey mainKey = task.getSelectionKey();
                if (mainKey != null){
                    mainKey.interestOps(mainKey.interestOps() | interest);
                    task.setComethandlerIsAsyncRegistered(true);
                    return true;
                }
            }
        }
        throw new IllegalStateException(INVALID_COMET_HANDLER);
    }

    
    /**
     * Helper.
     */
    @Override
    public String toString(){
        return topic;
    }

    
    /**
     * Return the {@link long} delay, in millisecond, before a request is resumed.
     * @return long the {@link long} delay, in millisecond, before a request is resumed.
     */
    public long getExpirationDelay() {
        return expirationDelay;
    }

    
    /**
     * Set the {@link long} delay before a request is resumed.
     * @param expirationDelay the {@link long} delay before a request is resumed. 
     *        Value is in milliseconds.
     */    
    public void setExpirationDelay(long expirationDelay) {
        this.expirationDelay = expirationDelay;
    }   
    
    
    /**
     * Return the current list of active {@link CometHandler}
     * @return the current list of active {@link CometHandler}
     */
    public Set<CometHandler> getCometHandlers(){
        return handlers.keySet();
    }
   
    
    /**
     * Return <tt>true</tt> if the invoker of {@link #notify} should block when
     * notifying Comet Handlers.
     */
    public boolean isBlockingNotification() {
        return blockingNotification;
    }

    
    /**
     * Set to <tt>true</tt> if the invoker of {@link #notify} should block when
     * notifying Comet Handlers.
     */
    public void setBlockingNotification(boolean blockingNotification) {
        this.blockingNotification = blockingNotification;
    }

    
    /**
     * Set the current {@link NotificationHandler}
     * @param notificationHandler
     */
    public void setNotificationHandler(NotificationHandler notificationHandler){
        this.notificationHandler = notificationHandler;
    }
    

    /**
     * Return the associated {@link NotificationHandler}
     * @return
     */
    public NotificationHandler getNotificationHandler(){
        return notificationHandler;
    }

    /**
     * Enable/disable the mechanism, which detects closed connections and notifies
     * user's handlers via
     * {@link CometHandler#onInterrupt(com.sun.grizzly.comet.CometEvent)} method.
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
}
