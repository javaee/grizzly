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
package com.sun.grizzly.http;


/**
 * When asynchronous request processing is enabled, the 
 * <code>Task</code> must delegate the execution of the request 
 * processing to an implementation of this interface.
 * 
 * @author Jeanfrancois Arcand
 */
public interface AsyncHandler {
    
    /**
     * Handle a <code>Task</code> execution. 
     */
    public void handle(Task task);
    
    
    /**
     * Add the <code>Task</code> to the interrupted queue. 
     */
    public void addToInterruptedQueue(AsyncTask task);
    
    
    /**
     * Remove the <code>Task</code> from the interrupted queue.
     */
    public void removeFromInterruptedQueue(AsyncTask task);  
    
    
    /**
     * Set the <code>AsyncExecutor</code> used by this object.
     */
    public void setAsyncExecutorClassName(String asyncExecutorClassName);
    
    
    /**
     * Get the code>AsyncExecutor</code> used by this object.
     */
    public String getAsyncExecutorClassName();
    
    
    /**
     * Add a <code>AsyncFilter</code>
     */
    public void addAsyncFilter(AsyncFilter asyncFilter);
    
    
    /**
     * Remove an <code>AsyncFilter</code>
     */
    public boolean removeAsyncFilter(AsyncFilter asyncFilter);
    
    
    /**
     * Return a <code>Task</code> 
     */
    public void returnTask(AsyncTask task);
}
