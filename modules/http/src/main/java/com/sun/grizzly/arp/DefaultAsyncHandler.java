/*
 * The contents of this file are subject to the terms 
 * of the Common Development and Distribution License 
 * (the License).  You may not use this file except in
 * compliance with the License.
 * 
 * You can obtain a copy of the license at 
 * https://glassfish.dev.java.net/public/CDDLv1.0.html or
 * glassfish/bootstrap/legal/CDDLv1.0.txt.
 * See the License for the specific language governing 
 * permissions and limitations under the License.
 * 
 * When distributing Covered Code, include this CDDL 
 * Header Notice in each file and include the License file 
 * at glassfish/bootstrap/legal/CDDLv1.0.txt.  
 * If applicable, add the following below the CDDL Header, 
 * with the fields enclosed by brackets [] replaced by
 * you own identifying information: 
 * "Portions Copyrighted [year] [name of copyright owner]"
 * 
 * Copyright 2006 Sun Microsystems, Inc. All rights reserved.
 */
package com.sun.grizzly.arp;

import com.sun.grizzly.http.AsyncExecutor;
import com.sun.grizzly.http.AsyncFilter;
import com.sun.grizzly.http.AsyncHandler;
import com.sun.grizzly.http.AsyncTask;
import com.sun.grizzly.http.ProcessorTask;
import com.sun.grizzly.http.Task;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Default implementation of <code>AsyncHandler</code>. This class handle 
 * the aysnchronous execution of a <code>ProcessorTask</code>. The request
 * processing is executed by doing:
 *
 * (1) Wrap the <code>ProcessorTask</code> using an instance of 
 *     <code>AsyncTask</code>
 * (2) Execute the <code>AsyncTask</code> using the wrapped
 *     <code>ProcessorTask</code> <code>Pipeline</code>
 * (3) If the <code>AsyncTask</code> has been interrupted but ready
 *     to be removed from the interrupted queue, remove it and execute the
 *     remaining operations.
 *
 * @author Jeanfrancois Arcand
 */
public class DefaultAsyncHandler implements AsyncHandler{
    
    /**
     * Cache instance of <code>AsyncTask</code>
     */
    private ConcurrentLinkedQueue<AsyncTask>
            asyncProcessors = new ConcurrentLinkedQueue<AsyncTask>();
    
    
    /**
     * A queue used to cache interrupted <code>AsyncTask</code>.
     */
    private ConcurrentLinkedQueue<AsyncTask>
            interrruptedQueue = new ConcurrentLinkedQueue<AsyncTask>();  
               
    
    /**
     * The <code>AsyncFilter</code> to execute asynchronous operations on 
     * a <code>ProcessorTask</code>.
     */
    private ArrayList<AsyncFilter> asyncFilters = 
            new ArrayList<AsyncFilter>();   
    
    
    /**
     * The <code>AsyncExecutor</code> class name to use.
     */ 
    private String asyncExecutorClassName 
        = "com.sun.grizzly.arp.DefaultAsyncExecutor";
    // ------------------------------------------------- Constructor --------//
    
    
    public DefaultAsyncHandler() {
    }
    
    
    /**
     * Create an instance of <code>AsyncTask</code>
     */
    private AsyncTask newAsyncProcessorTask(){
        AsyncTask asyncTask = new AsyncProcessorTask();
        asyncTask.setAsyncExecutor(newAsyncExecutor(asyncTask));  
        return asyncTask;
    }
    
    
    /**
     * Create an instance of <code>DefaultAsyncExecutor</code>
     */    
    private AsyncExecutor newAsyncExecutor(AsyncTask asyncTask){
        
        Class className = null; 
        AsyncExecutor asyncExecutor = null;
        try{                              
            className = Class.forName(asyncExecutorClassName);
            asyncExecutor = (AsyncExecutor)className.newInstance();
        } catch (ClassNotFoundException ex){
            throw new RuntimeException(ex);
        } catch (InstantiationException ex){
            throw new RuntimeException(ex);
        } catch (IllegalAccessException ex){
            throw new RuntimeException(ex);
        }
        
        if ( asyncExecutor != null ){
            asyncExecutor.setAsyncTask(asyncTask);
            asyncExecutor.setAsyncHandler(this);
            
            for (AsyncFilter l : asyncFilters){
                asyncExecutor.addAsyncFilter(l);
            }
        }
        return asyncExecutor;
    }
    
    
    /**
     * Return an instance of <code>AsyncTask</code>, which is 
     * configured and ready to be executed.
     */
    private AsyncTask getAsyncProcessorTask(){
        AsyncTask asyncTask = asyncProcessors.poll();
        if ( asyncTask == null) {
            asyncTask = newAsyncProcessorTask();
        } else {
            asyncTask.recycle();
        }
        return asyncTask;
    }
    
    
    // ---------------------------------------------------- Interface -------//
    
    
    /**
     * Handle an instance of a <code>Task</code>. This method is invoked
     * first by a <code>ProcessorTask</code>, which delegate its execution to 
     * this handler. Second, this method is invoked once a 
     * <code>ProcessorTask</code> needs to be removed from the interrupted queue.
     */
    public void handle(Task task){
        
        AsyncTask apt = null;
        if ( task.getType() == Task.PROCESSOR_TASK) {
            apt = getAsyncProcessorTask();
            apt.setProcessorTask((ProcessorTask)task);
            apt.setSelectorThread(task.getSelectorThread());
        }
        
        boolean wasInterrupted = interrruptedQueue.remove(task);
        if ( !wasInterrupted && apt == null) {
            String errorMsg = "";
            if ( task.getSelectionKey() != null ) {
                errorMsg = "Connection " + task.getSelectionKey().channel()
                            + " wasn't interrupted";
            } 
            throw new IllegalStateException(errorMsg);
        } else if ( apt == null ){
            apt = (AsyncTask)task;
        }
        apt.execute();
    }
    

    /**
     * Return th <code>Task</code> to the pool
     */
    public void returnTask(AsyncTask asyncTask){
        asyncProcessors.offer(asyncTask);
    }
    
    /**
     * Add a <code>Task</code> to the interrupted queue.
     */
    public void addToInterruptedQueue(AsyncTask task){
        interrruptedQueue.offer(task);
    }
    
    
    /**
     * Remove the <code>Task</code> from the interrupted queue.
     */
    public void removeFromInterruptedQueue(AsyncTask task){
        interrruptedQueue.remove(task);
    }
    
    
    /**
     * Set the <code>AsyncExecutor</code> used by this object.
     */
    public void setAsyncExecutorClassName(String asyncExecutorClassName){
        this.asyncExecutorClassName = asyncExecutorClassName;
    }
    
    
    /**
     * Get the code>AsyncExecutor</code> used by this object.
     */
    public String getAsyncExecutorClassName(){
        return asyncExecutorClassName;
    }
    
    
    /**
     * Add an <code>AsyncFilter</code>
     */
    public void addAsyncFilter(AsyncFilter asyncFilter) {
        asyncFilters.add(asyncFilter);
    }

    
    /**
     * Remove an <code>AsyncFilter</code>
     */
    public boolean removeAsyncFilter(AsyncFilter asyncFilter) {
        return asyncFilters.remove(asyncFilter);
    }
}
