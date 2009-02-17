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

import com.sun.grizzly.comet.concurrent.DefaultConcurrentCometHandler;
import com.sun.grizzly.http.SelectorThread;
import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The main object used by {@link CometHandler} and {@link Servlet} to push information
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
 * and then inside a {@link Servlet.service} method, you just need to call:
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
 * Idle suspended connection can be timed out by configuring the {@link #setExpirationDelay(int)}.
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
 * Attributes can be added/removed the same way {@link HttpServletSession} 
 * is doing. It is not recommended to use attributes if this 
 * {@link CometContext} is not shared amongs multiple
 * context path (uses {@link HttpServletSession} instead).
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
     * The {@link CometSelector} used to register {@link SelectionKey}
     * for upcoming bytes.
     */
    protected CometSelector cometSelector;
    
    
    /**
     * The {@link CometContext} continuationType. See {@link CometEngine}
     */
    protected int continuationType = CometEngine.AFTER_SERVLET_PROCESSING;
    
    
    /**
     * The default delay expiration before a {@link CometContext}'s
     * {@link CometHandler} are interrupted.
     */
    private long expirationDelay = 30 * 1000;
    
    
    /**
     * <tt>true</tt> if the caller of {@link #notify} should block when 
     * notifying other CometHandler.
     */
    protected boolean blockingNotification = false;

    
    /**
     * The default NotificationHandler.
     */
    protected NotificationHandler notificationHandler;
    

     /**
      * timestamp for last performed resetSuspendidletimeout.
      * used to limit the frequency of actual performed resets.
      */
    private volatile long lastIdleReset;

    /**
     * Current associated list of {@link CometTask}
     */
    protected final ConcurrentHashMap<CometTask,Object> activeTasks;

    /**
     * The list of registered {@link CometHandler}
     */
    protected final ConcurrentHashMap<CometHandler,SelectionKey> handlers;

    private final CometEvent eventInterrupt;

    private final CometEvent eventInitialize;

    private static final IllegalStateException ISE = new IllegalStateException(INVALID_COMET_HANDLER);    

    private static final IllegalStateException cometNotEnabled =
            new IllegalStateException("Make sure you have enabled Comet " +
                "or make sure the Thread invoking that method is the same " +
                "as the Servlet.service() Thread.");

    // ---------------------------------------------------------------------- //
    
    
    /**
     * Create a new instance
     * @param topic the context path 
     * @param type when the Comet processing will happen (see {@link CometEngine}).
     */
    public CometContext(String topic, int continuationType) {
        this.topic = topic;
        this.continuationType = continuationType;
        this.attributes       = new ConcurrentHashMap();        
        this.handlers         = new ConcurrentHashMap<CometHandler,SelectionKey>(16,0.75f,64);        
        this.activeTasks      = new ConcurrentHashMap<CometTask,Object>(16,0.75f,64); 
        this.eventInterrupt   = new CometEvent<E>(CometEvent.INTERRUPT,this);
        this.eventInitialize  = new CometEvent<E>(CometEvent.INITIALIZE,this);
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
     * Add a {@link CometHandler}. The underlying {@link HttpServletResponse} will 
     * not get commited until {@link CometContext#resumeCometHandler(CometHandler)}
     * is invoked, unless the {@link CometContext#setExoirationDelay(int)} expires.
     * If set to alreadySuspended is set to true, no  I/O operations are allowed 
     * inside the {@link CometHandler} as the underlying {@link HttpServletResponse}
     * has not been suspended. Adding such {@link CometHandler} is usefull only when
     * no I/O operations on the {@link HttpServletResponse} are required. Examples
     * include calling a remote EJB when a push operations happens, storing
     * data inside a database, etc. 
     * 
     * @param handler a new {@link CometHandler} 
     * @param alreadySuspended Add the Comethandler but don't suspend
     *        the {@link HttpServletResponse}. If set to true, no 
     *        I/O operations are allowed inside the {@link CometHandler} as the
     *        underlying {@link HttpServletResponse} has not been suspended, unless
     *        the {@link CometHandler} is shared amongs more than one {@link CometContext}
     *       
     * @return The {@link CometHandler#hashCode} value.
     */
    public int addCometHandler(CometHandler handler, boolean alreadySuspended){
        if (handler == null){
            throw ISE;
        }
        
        if (!CometEngine.getEngine().isCometEnabled()){
            throw cometNotEnabled;
        }
        // is it ok that we only manage one adcomethandler call ?
        CometTask cometTask = new CometTask();
        cometTask.setCometContext(this);
        cometTask.setCometHandler(handler);
        cometTask.setRecycle(alreadySuspended);
        CometEngine.updatedContexts.set(cometTask);
        
        return handler.hashCode();
    }
    
    
    /**
     * Add a {@link CometHandler} which will starts the process of suspending
     * the underlying response. The underlying {@link HttpServletResponse} will 
     * not get commited until {@link CometContext#resumeCometHandler(CometHandler)}
     * is invoked, unless the {@link CometContext#setExoirationDelay(int)} expires.
     * @param handler a new {@link CometHandler}
     */
    public int addCometHandler(CometHandler handler){
        return addCometHandler(handler,false);
    }
    
    
    /**
     * Retrieve a {@link CometHandler} using its based on its {@link CometHandler#hashCode};
     */
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
        handlers.clear();
        attributes.clear();
        activeTasks.clear();
        topic = null;
    }


    /**
     * adds a {@link CometHandler} to the active set
     * @param handler {@link CometHandler} 
     * @param cometKey {@link SelectionKey}
     */
    protected void addActiveHandler(CometHandler handler, SelectionKey cometKey){
        handlers.put(handler, cometKey);
    }
        
    /**
     * Invoke a {@link CometHandler} using the {@link CometEvent}
     * @param event - {@link CometEvent}
     * @param cometHandler - {@link CometHandler}
     * @throws java.io.IOException
     */
    protected void invokeCometHandler(CometEvent event, CometHandler cometHandler) throws IOException{
        if (cometHandler == null){
            throw ISE;
        }
        event.setCometContext(this);
        if (cometHandler instanceof DefaultConcurrentCometHandler){
            ((DefaultConcurrentCometHandler)cometHandler).EnQueueEvent(event);
        }else{
            cometHandler.onEvent(event);
        }
    }    
    
    
    /**
     * Remove a {@link CometHandler}. If the continuation (connection)
     * associated with this {@link CometHandler} no longer have 
     * {@link CometHandler} associated to it, it will be resumed by Grizzly 
     * by calling {@link CometContext#resumeCometHandler()}
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
        SelectionKey key = handlers.remove(handler);
        if (key != null){
            if (resume){
                CometEngine.getEngine().flushPostExecute(
                        ((CometTask)key.attachment()).getAsyncProcessorTask());
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
    public boolean removeCometHandler(int hashCode){
        Iterator<CometHandler> iterator = handlers.keySet().iterator();
        CometHandler handler = null;
        while (iterator.hasNext()){
            handler = iterator.next();
            if (handler.hashCode() == hashCode){
                SelectionKey key = handlers.get(handler);
                if (key == null){
                    logger.warning(ALREADY_REMOVED);
                    return false;
                }
                iterator.remove();
                return true;
    }
        }
        return false;
    }

    /**
     * Resume the Comet request and remove it from the active {@link CometHandler} list. Once resumed,
     * a CometHandler must never manipulate the {@link HttpServletRequest} or {@link HttpServletResponse} as
     * those object will be recycled and may be re-used to serve another request. 
     * 
     * If you cache them for later reuse by another thread there is a
     * possibility to introduce corrupted responses next time a request is made.
     * @param handler The CometHandler to resume.
     * @return <tt>true</tt> if the operation succeeded.
     */
    public boolean resumeCometHandler(CometHandler handler){
        return resumeCometHandler(handler,true);
    }
    
    
    /**
     * Resume the Comet request.
     * 
     * @param handler The CometHandler associated with the current continuation.
     * @param remove true if the CometHandler needs to be removed.
     * @return <tt>true</tt> if the operation succeeded.
     */
    protected boolean resumeCometHandler(CometHandler handler, boolean remove){
        return cometSelector.cancelKey(handlers.get(handler), false, remove, false);
    }

    /**
     * Interrupt a {@link CometHandler} by invoking {@link CometHandler#onInterrupt}
     */
    protected boolean interrupt(CometTask task,boolean removeCometHandler, 
            boolean notifyInterrupt) {
        
        boolean status = true;
        try{
            if (removeCometHandler){
                status = (handlers.remove(task.getCometHandler()) != null);
                if (status && notifyInterrupt){
                    task.getCometHandler().onInterrupt(eventInterrupt);
                }else{
                    logger.finer(ALREADY_REMOVED);
                }
            }
        } catch (Throwable ex){
            status = false;
            logger.log(Level.FINE,"Unable to interrupt",ex);            
        }finally{
            activeTasks.remove(task);
            return status;
        }
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
     * Notify all {@link CometHandler}. All
     * {@link CometHandler.onEvent()} will be invoked with a {@link CometEvent}
     * of type NOTIFY. 
     * @param attachment An object shared amongst {@link CometHandler}. 
     */
    public void notify(final E attachment) throws IOException{
        notify(attachment, CometEvent.NOTIFY);
    }
    
    
    /**
     * Notify a single {@link CometHandler}. The {@link CometEvent.getType()} 
     * will determine which {@link CometHandler}  method will be invoked:
     * <pre><code>
     * CometEvent.INTERRUPT -> {@link CometHandler#onInterrupt}
     * CometEvent.NOTIFY -> {@link CometHandler#onEvent}
     * CometEvent.INITIALIZE -> {@link CometHandler#onInitialize}
     * CometEvent.TERMINATE -> {@link CometHandler#onTerminate}
     * CometEvent.READ -> {@link CometHandler#onEvent}
     * </code></pre>
     * @param attachment An object shared amongst {@link CometHandler}. 
     * @param type The type of notification. 
     * @param cometHandlerID Notify a single CometHandler.
     * @deprecated - use notify(attachment,eventType,CometHandler;
     */
    public void notify(final E attachment,final int eventType,final int cometHandlerID)
        throws IOException{   
        notify(attachment,eventType,getCometHandler(cometHandlerID));
    }
    
    /**
     * Notify a single {@link CometHandler#onEvent(CometEvent}. 
     * @param attachment An object shared amongst {@link CometHandler}. 
     * @param {@link CometHandler} to notify.
      */
    public void notify(final E attachment,final CometHandler cometHandler) 
        throws IOException{
        notify(attachment,CometEvent.NOTIFY,cometHandler);
    }  
    
    /**
     * Notify a single {@link CometHandler}. The {@link CometEvent.getType()} 
     * will determine which {@link CometHandler}  method will be invoked:
     * <pre><code>
     * CometEvent.INTERRUPT -> {@link CometHandler#onInterrupt}
     * CometEvent.NOTIFY -> {@link CometHandler#onEvent}
     * CometEvent.INITIALIZE -> {@link CometHandler#onInitialize}
     * CometEvent.TERMINATE -> {@link CometHandler#onTerminate}
     * CometEvent.READ -> {@link CometHandler#onEvent}
     * </code></pre>
     * @param attachment An object shared amongst {@link CometHandler}. 
     * @param type The type of notification. 
     * @param {@link CometHandler} to notify.
      */
    public void notify(final E attachment,final int eventType,final CometHandler cometHandler)
        throws IOException{
        if (cometHandler == null){
            throw ISE;
        }
        CometEvent event = new CometEvent<E>(eventType,this);
        event.attach(attachment);        
        notificationHandler.setBlockingNotification(blockingNotification);        
        notificationHandler.notify(event,cometHandler);
        if (event.getType() == CometEvent.TERMINATE 
            || event.getType() == CometEvent.INTERRUPT) {
            resumeCometHandler(cometHandler);
        } else {
            resetSuspendIdleTimeout();
        }
    }    

      
    /**
     * Notify all {@link CometHandler}. The {@link CometEvent.getType()} 
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
     * @param type The type of notification. 
     */   
    public void notify(final E attachment,final int eventType)
        throws IOException{
        CometEvent event = new CometEvent<E>(eventType,this);
        event.attach(attachment);
        Iterator<CometHandler> iterator = handlers.keySet().iterator();
        notificationHandler.setBlockingNotification(blockingNotification);
        notificationHandler.notify(event,iterator);
        if (event.getType() == CometEvent.TERMINATE 
            || event.getType() == CometEvent.INTERRUPT) {
            while(iterator.hasNext()){
                resumeCometHandler(iterator.next());
            }
        } else {
            resetSuspendIdleTimeout();
        } 
    }


    /**
     * Initialize the newly added {@link CometHandler}.
     *
     * @param attachment An object shared amongst {@link CometHandler}.
     * @param type The type of notification.
     * @param key The SelectionKey representing the CometHandler.
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
            // not threadsafe, but that will only lead to a few extra idle checks.
            // it will still be a major win.
            if (timestamp - lastIdleReset >= 1000){
                lastIdleReset = timestamp; 
                for (CometTask cometTask:activeTasks.keySet()){
                    cometTask.setExpireTime(timestamp);
                }
            }
        }
    }    
    
    
    /**
     * Register for asynchronous read event (CometEvent#READ}. As soon as Grizzly detects
     * there is some bytes available for read, your {@link Comethandler#onEvent(CometEvent)} will be invoked.
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
     * {@link Comethandler#onEvent(CometEvent)} will be invoked.
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
        SelectionKey cometKey = null;
        if (handler != null) {
            cometKey = handlers.get(handler);
        }
        if (handler == null || cometKey == null) {
            throw ISE;
        }
        
        CometTask cometTask = (CometTask)cometKey.attachment();
        if (cometTask != null){
            cometKey.interestOps(cometKey.interestOps() | interest);            
            if (interest == SelectionKey.OP_READ){
                cometTask.setAsyncReadSupported(true);
            }
            cometTask.setComethandlerisAsyncregistered(true);
            return true;
        }
        throw ISE;        
    }

    
    /**
     * Set the {@link CometSelector} associated with this instance.
     * @param CometSelector the {@link CometSelector} associated with 
     *         this instance.
     */   
    protected void setCometSelector(CometSelector cometSelector) {
        this.cometSelector = cometSelector;
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
     * Add a {@link CometTask} to the active list.
     * @param cometTask
     */
    protected void addActiveCometTask(CometTask cometTask){
        activeTasks.put(cometTask,Boolean.TRUE);
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
     * Return the current set of active {@link CometTask}
     * @return
     */
    protected Set<CometTask> getActiveTasks() {
        return activeTasks.keySet();
    }

}
    
