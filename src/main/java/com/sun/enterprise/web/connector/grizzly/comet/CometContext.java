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

import com.sun.enterprise.web.connector.grizzly.ConcurrentQueue;
import com.sun.enterprise.web.connector.grizzly.SelectorThread;
import com.sun.enterprise.web.connector.grizzly.comet.concurrent.DefaultConcurrentCometHandler;

import java.io.IOException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Queue;
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
 * is created using a topic's name like:<</p><p><pre><code>
 * 
 * CometEngine ce = CometEngine.getEngine();
 * CometContext cc = ce.registerContext("MyTopic");
 * cc.setBlockingNotification(false); // Use multiple thread to execute the server push
 * 
 * and then inside a Servlet.service method, you just need to call:
 * 
 * cc.addCometListener(myNewCometListener());
 * cc.notify("I'm pushing data to all registered CometHandler");
 * </core></pre></p>
 * <p>
 * As soom as {@link #addCometHandler} is invoked, Grizzly will automatically
 * <strong>suspend</strong> the request/response (will not commit the response).
 * A response can be <strong>resumed</strong> by invoking {@link resumeCometHandler},
 * which will automatically commit the response and remove the associated
 * CometHandler from the CometContext. 
 * 
 * A CometContext uses a {@link NotificationHandler} to invoke, using the calling
 * thread or a Grizzly thread pool, all CometHandler than have been added using the
 * {@link #addCometHandler}. A {@link NotificationHandler} can be used to filter
 * or transform the content that will eventually be pushed back to all connected
 * clients. You can also use a {@link NotificationHandler} to throttle push like
 * invoking only a subset of the CometHandler, etc.
 * </p>
 * <p>
 * Attributes can be added/removed the same way {@link HttpServletSession} 
 * is doing. It is not recommended to use attributes if this 
 * {@link CometContext} is not shared amongs multiple
 * context path (uses {@link HttpServletSession} instead).
 * </p>
 * @author Jeanfrancois Arcand
 */
public class CometContext<E> {
    
    /**
     * Generic error message
     */
    protected final static String INVALID_COMET_HANDLER = "CometHandler cannot be null. " 
            + "This CometHandler was probably resumed and an invalid " 
            +  "reference was made to it.";
    
    /**
     * Main logger
     */
    private final static Logger logger = SelectorThread.logger();  
 
     
    /**
     * Attributes placeholder.
     */
    private ConcurrentHashMap attributes;
    
    
    /**
     * The context path associated with this instance.
     */
    private String topic;
    
    
    /**
     * Is the {@link CometContext} instance been cancelled.
     */
    protected boolean cancelled = false;
    
    
    /**
     * The list of registered {@link CometHandler}
     */
    protected ConcurrentHashMap<CometHandler,SelectionKey> handlers;
    
    
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
     * SelectionKey that are in the process of being parked.
     */
    private static Queue<SelectionKey> inProgressSelectionKey = null;
     
    
    /**
     * Current associated list of {@link CometTask}
     */
    protected Queue<CometTask> activeTasks =
            new ConcurrentQueue<CometTask>("CometContext.activeTasks");
    
    // ---------------------------------------------------------------------- //
    
    
    /**
     * Create a new instance
     * @param topic the context path 
     * @param type when the Comet processing will happen (see {@link CometEngine}).
     */
    public CometContext(String topic, int continuationType) {
        this.topic = topic;
        this.continuationType = continuationType;
        attributes = new ConcurrentHashMap();
        handlers = new ConcurrentHashMap<CometHandler,SelectionKey>();
        inProgressSelectionKey = new ConcurrentQueue<SelectionKey>("CometContext.inProgressSelectionKey");
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
     * Retrive an attribute.
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
     * Add a {@link CometHandler}. Client of this method might
     * make sure the {@link CometHandler} is removed when the 
     * {@link CometHandler.onInterrupt} is invoked.
     * @param handler a new {@link CometHandler}
     * @param completeExecution Add the Comethandler but don't block waiting
     *        for event.
     * @return The {@link CometHandler#hashCode} value.
     */
    public synchronized int addCometHandler(CometHandler handler, boolean completeExecution){
        Long threadId = Thread.currentThread().getId();
        SelectionKey key = CometEngine.getEngine().
                activateContinuation(threadId,this,completeExecution);

        if (key == null){
            throw new 
               IllegalStateException("Make sure you have enabled Comet or" +
               " make sure the Thread invoking that method " +
               "is the same a the request Thread.");
        }
        
        if (handler == null){
            throw new 
               IllegalStateException(INVALID_COMET_HANDLER);
        }

        if (!completeExecution){
            handlers.putIfAbsent(handler,key);
        } else {
            handlers.putIfAbsent(handler,new SelectionKey() {
                public void cancel() {
                }
                public SelectableChannel channel() {
                    throw new IllegalStateException();
                }
                public int interestOps() {
                    throw new IllegalStateException();
                }
                public SelectionKey interestOps(int ops) {
                    throw new IllegalStateException();
                }
                public boolean isValid() {
                    return true;
                }
                public int readyOps() {
                    throw new IllegalStateException();
                }
                public Selector selector() {
                    throw new IllegalStateException();
                }
            });
        }
        return handler.hashCode();
    }
    
    
    /**
     * Add a {@link CometHandler}. Client on this method might
     * make sure the {@link CometHandler} is removed when the 
     * {@link CometHandler.onInterrupt} is invoked.
     * @param handler a new {@link CometHandler}
     */
    public int addCometHandler(CometHandler handler){
        return addCometHandler(handler,false);
    }
    
    
    /**
     * Retrive a {@link CometHandler} using its hashKey;
     */
    public CometHandler getCometHandler(int hashCode){
        for (CometHandler handler:handlers.keySet()){
            if (handler.hashCode() == hashCode )
               return handler;
        }
        return null;
    }   
    
    
    /**
     * Retrive a {@link CometHandler} using its SelectionKey. The 
     * {@link SelectionKey} is not exposed to the Comet API, hence this
     * method must be protected.
     */
    protected CometHandler getCometHandler(SelectionKey key){
        for (Entry<CometHandler,SelectionKey> entry:handlers.entrySet()){
            if (entry.getValue() == key )
               return entry.getKey();
        }
        return null;
    }
    
    
    /**
     * Notify all {@link CometHandler}. The attachment can be null.
     * The {@link type} will determine which code>CometHandler} 
     * method will be invoked:
     * <pre><code>
     * CometEvent.INTERRUPT -> {@link CometHandler#onInterrupt}
     * CometEvent.NOTIFY -> {@link CometHandler#onEvent}
     * CometEvent.INITIALIZE -> {@link CometHandler#onInitialize}
     * CometEvent.TERMINATE -> {@link CometHandler#onTerminate}
     * CometEvent.READ -> {@link CometHandler#onEvent}
     * CometEvent.WRITE -> @link CometHandler#onEvent}
     * }</pre>
     * @param attachment An object shared amongst {@link CometHandler}. 
     * @param type The type of notification. 
     * @param key The SelectionKey associated with the CometHandler.
     */
    protected void invokeCometHandler(CometEvent event, int eventType, SelectionKey key) 
            throws IOException{
        CometHandler cometHandler = getCometHandler(key);
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
     * {@link CometHandler} associated to it, it will be resumed.
     */
    public void removeCometHandler(CometHandler handler){
        removeCometHandler(handler,true);
    }

    
    /**
     * Remove a {@link CometHandler}. If the continuation (connection)
     * associated with this {@link CometHandler} no longer have 
     * {@link CometHandler} associated to it, it will be resumed.
     * @return <tt>true</tt> if the operation succeeded.
     */
    public boolean removeCometHandler0(CometHandler handler){
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
        if (key == null) return false;
              
        if (resume && !handlers.containsValue(key)){
            CometEngine.getEngine().resume(key);
        }
        return true;
    }
    
    
    /**
     * Remove a {@link CometHandler} based on its hashcode. Return <tt>true</tt>
     * if the operation was sucessfull.
     * @param hashCode The hashcode of the CometHandler to remove.
     * @return <tt>true</tt> if the operation succeeded.
     */
    public synchronized boolean removeCometHandler(int hashCode){
        Iterator<CometHandler> iterator = handlers.keySet().iterator();
        CometHandler cometHandler = null;
        while (iterator.hasNext()){
            cometHandler = iterator.next();
            if (cometHandler.hashCode() == hashCode){
                SelectionKey key = handlers.get(cometHandler);
                if (key == null){
                    logger.warning("CometHandler already been removed or invalid.");
                    return false;
                }   

                if (inProgressSelectionKey.contains(key)){
                    logger.warning("Cannot resume an in progress connection.");
                    return false;
                }                  
                iterator.remove();
                return true;
            }
        }
        return false;
    }

    
    /**
     * Resume the Comet request and remove it from the active CometHandler list. Once resumed,
     * a CometHandler should never manipulate the {@link HttpServletRequest} or {@link HttpServletResponse} as
     * those are recycled. If you cache them for later reuse by another thread there is a
     * possibility to introduce corrupted responses next time a request is made.
     * @param handler The CometHandler to resume.
     */
    public void resumeCometHandler(CometHandler handler){
        resumeCometHandler0(handler);
    }

    
    /**
     * Resume the Comet request and remove it from the active CometHandler list. Once resumed,
     * a CometHandler should never manipulate the HttpServletRequest or HttpServletResponse as
     * those are recycled. If you cache them for later reuse by another thread there is a
     * possibility to introduce corrupted responses next time a request is made.
     * @param handler The CometHandler to resume.
     * @return <tt>true</tt> if the operation succeeded.
     */
    public boolean resumeCometHandler0(CometHandler handler){
        return resumeCometHandler(handler,true);
    }
    
    
    /**
     * Resume the Comet request.
     * @param handler The CometHandler associated with the current continuation.
     * @param remove true if the CometHandler needs to be removed.
     * @return <tt>true</tt> if the operation succeeded.
     */
    protected boolean resumeCometHandler(CometHandler handler, boolean remove){
        SelectionKey key = handlers.get(handler);
        if (key == null){
            logger.warning("CometHandler already been resumed or invalid.");
            return false;
        }   
        
        if (inProgressSelectionKey.contains(key)){
            logger.warning("Cannot resume an in progress connection.");
            return false;            
        }        
                 
        if (remove){
            boolean ok = removeCometHandler(handler,false);
            if (!ok) return false;
        }     
        // Retrieve the CometSelector key.
        SelectionKey cometKey = cometSelector.cometKeyFor(key.channel());
        if (cometKey != null){
            CometTask task = (CometTask)cometKey.attachment();
            if (task != null){
                activeTasks.remove(task);
            }
            cometKey.attach(null);
            cometKey.cancel();
        }     
        CometEngine.getEngine().resume(key);
        return true;
    }
    
    
    /**
     * Return true if this CometHandler is still active, e.g. there is 
     * still a continuation associated with it.
     * @return true if active, false if not.
     */
    public synchronized boolean isActive(CometHandler cometHandler){
        if (cometHandler == null){
            return false;
        }
        SelectionKey key = handlers.get(cometHandler);
        return (key != null && !inProgressSelectionKey.contains(key));
    }
    
    
    /**
     * Notify all {@link CometHandler}. The attachment can be null. All
     * {@link CometHandler.onEvent()} will be invoked.
     * @param attachment An object shared amongst {@link CometHandler}. 
     */
    public void notify(final E attachment) throws IOException{
        notify(attachment, CometEvent.NOTIFY);
    }
    
    
    /**
     * Notify a single {@link CometHandler}. The attachment can be null.
     * The {@link type} will determine which code>CometHandler} 
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
     * @param cometHandlerID Notify a single CometHandler.
     */
    public void notify(final E attachment,final int eventType,final int cometHandlerID) 
            throws IOException{   
        CometHandler cometHandler = getCometHandler(cometHandlerID);
  
        if (cometHandler == null){
            throw new IllegalStateException(INVALID_COMET_HANDLER);
        }
        CometEvent event = new CometEvent<E>();
        event.setType(eventType);
        event.attach(attachment);
        event.setCometContext(CometContext.this);
        
        notificationHandler.setBlockingNotification(blockingNotification);        
        notificationHandler.notify(event,cometHandler);
        if (event.getType() == CometEvent.TERMINATE 
            || event.getType() == CometEvent.INTERRUPT) {
            resumeCometHandler0(cometHandler);
        } else {
            if (expirationDelay < Long.MAX_VALUE)
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
     protected void initialize(SelectionKey key) throws IOException {
        CometEvent event = new CometEvent<E>();
        event.setType(CometEvent.INITIALIZE);
        event.setCometContext(this);
        
        for (Entry<CometHandler,SelectionKey> entry:handlers.entrySet()){
            SelectionKey ak = entry.getValue();
            if(ak != null && ak.equals(key)){
                entry.getKey().onInitialize(event);
                break;
            }
        }
    }
     
      
    /**
     * Notify all {@link CometHandler}. The attachment can be null.
     * The {@link type} will determine which code>CometHandler} 
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
    public void notify(final E attachment,final int eventType) throws IOException{
        // XXX Use a pool of CometEvent instance.
        CometEvent event = new CometEvent<E>();
        event.setType(eventType);
        event.attach(attachment);
        event.setCometContext(CometContext.this);

        Iterator<CometHandler> iterator = handlers.keySet().iterator();
        notificationHandler.setBlockingNotification(blockingNotification);
        notificationHandler.notify(event,iterator);
        if (event.getType() == CometEvent.TERMINATE 
            || event.getType() == CometEvent.INTERRUPT) {
            while(iterator.hasNext()){
                resumeCometHandler0(iterator.next());
            }
        } else {
            if (expirationDelay < Long.MAX_VALUE){
                resetSuspendIdleTimeout();
            }
        } 
    }

    
    /**
     * Reset the current timestamp on a suspended connection.
     */
    protected synchronized void resetSuspendIdleTimeout(){
        CometTask cometTask;       
        Iterator<CometTask> it = activeTasks.iterator();
        while(it.hasNext()){
            cometTask = it.next();           
            cometTask.setExpireTime(System.currentTimeMillis());
        }  
    }    
    
    
    /**
     * Register for asynchronous read. If your client supports http pipelining,
     * invoking this method might result in a state where your CometHandler
     * is invoked with a {@link CometRead} that will read the next http request. In that
     * case, it is strongly recommended to not use that method unless your
     * CometHandler can handle the http request.
     * @oaram handler The CometHandler that will be invoked.
     */
    public boolean registerAsyncRead(CometHandler handler){
        SelectionKey key = null;
        if (handler != null) {
            key = handlers.get(handler);
        }
        if (handler == null || key == null) { 
            throw new 
               IllegalStateException(INVALID_COMET_HANDLER);            
        }
        // Retrieve the CometSelector key.
        SelectionKey cometKey = cometSelector.cometKeyFor(key.channel());
        if (cometKey != null){
            cometKey.interestOps(cometKey.interestOps() | SelectionKey.OP_READ); 
            if (cometKey.attachment() != null){
                ((CometTask)cometKey.attachment()).setAsyncReadSupported(true);
            }
            return true;
        } else {
            return false;
        }  
    }
    
    
    /**
     * Register for asynchronous write.
     */
    public boolean registerAsyncWrite(CometHandler handler){
        SelectionKey key = null;
        if (handler != null) {
            key = handlers.get(handler);
        }
        if (handler == null || key == null) { 
            throw new 
               IllegalStateException(INVALID_COMET_HANDLER);            
        }
        // Retrieve the CometSelector key.
        SelectionKey cometKey = cometSelector.cometKeyFor(key.channel());
        if (cometKey != null){
            cometKey.interestOps(cometKey.interestOps() | SelectionKey.OP_WRITE); 
            return true;
        } else {
            return false;
        }           
    }
    
    
    /**
     * Recycle this object.
     */
    protected void recycle(){
        handlers.clear();
        attributes.clear();
        cancelled = false;
        activeTasks.clear();
    }    

    
    /**
     * Is this instance beeing cancelled by the {@link CometSelector}
     * @return boolean cancelled or not.
     */
    protected boolean isCancelled() {
        return cancelled;
    }

    
    /**
     * Cancel this object or "uncancel".
     * @param cancelled true or false.
     */
    protected void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
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
     * Return the {@link long} delay before a request is resumed.
     * @return long the {@link long} delay before a request is resumed.
     */
    public long getExpirationDelay() {
        return expirationDelay;
    }

    
    /**
     * Set the {@link long} delay before a request is resumed.
     * @param long the {@link long} delay before a request is resumed.
     */    
    public void setExpirationDelay(long expirationDelay) {
        this.expirationDelay = expirationDelay;
    }   

    
    /**
     * Interrupt a {@link CometHandler} by invoking {@link CometHandler#onInterrupt}
     */
    protected void interrupt(CometTask task){
        CometEvent event = new CometEvent<E>();
        event.setType(CometEvent.INTERRUPT);
        event.attach(null);
        event.setCometContext(this);
        
        for (Entry<CometHandler,SelectionKey> entry:handlers.entrySet()){
            if (entry.getValue().equals(task.getSelectionKey())){
                try{
                    handlers.remove(entry.getKey());
                    entry.getKey().onInterrupt(event);
                } catch (IOException ex){
                    logger.log(Level.WARNING,"Exception: ",ex);
                }
                break;
            }
        }  
        activeTasks.remove(task);
    }
    
    
    /**
     * Return the current list of active {@link CometHandler}
     * return the current list of active {@link CometHandler}
     */
    public Set<CometHandler> getCometHandlers(){
        return handlers.keySet();
    }
    
    
    
    /**
     * Add a {@link CometTask} to the active list.
     * @param cometTask
     */
    protected void addActiveCometTask(CometTask cometTask) {
        activeTasks.offer(cometTask);
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
     * Add a {@link SelectionKey} to the list of current operations.
     * @param key
     */
    protected static void addInProgressSelectionKey(SelectionKey key){
        inProgressSelectionKey.add(key);
    }

    
    /**
     * Remove a {@link SelectionKey} to the list of current operations.
     * @param key
     * @return
     */
    protected static boolean removeInProgressSelectionKey(SelectionKey key){
        return inProgressSelectionKey.remove(key);
    }
}
    
