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
 * A interface used to define the execution of a <code>AsyncTask</code>
 * By default, <code>AsyncTask</code> will invoke an implementation
 * of this interface in this order:
 * 
 * (1) preExecute()
 * (2) interrupt()
 * (3) postExecute()
 *
 * Implementation of this interface must decide when a task must be interrupted.
 *
 * @author Jeanfrancois Arcand
 */
public interface AsyncExecutor {
    
    /**
     * Pre-execute some operations in the <code>AsycnProcesssorTask</code>
     * associated. 
     * @return true if the processing can continue.
     */
    public boolean preExecute() throws Exception;
    
    
    /**
     * Execute some operations on the <code>AsycnProcesssorTask</code> and then
     * interrupt it.
     * @return true if the processing can continue, false if it needs to be 
     *              interrupted.
     */    
    public boolean interrupt() throws Exception;
    
    
   /**
     * Execute the main operation on
     * @return true if the processing can continue, false if it needs to be 
     *              interrupted.
     */    
    public boolean execute() throws Exception;    
    
    /**
     * Post-execute some operations in the <code>AsycnProcesssorTask</code>
     * associated.
     * @return true if the processing can continue.
     */    
    public boolean postExecute() throws Exception;
    
    
    /**
     * Set the <code>AsycnProcesssorTask</code>.
     */
    public void setAsyncTask(AsyncTask task);

    
    /**
     * Get the <code>AsycnProcesssorTask</code>.
     */    
    public AsyncTask getAsyncTask();
    
    
    /**
     * Add a <code>AsyncFilter</code>
     */
    public void addAsyncFilter(AsyncFilter asyncFilter);
    
    
    /**
     * Remove an <code>AsyncFilter</code>
     */
    public boolean removeAsyncFilter(AsyncFilter asyncFilter);
    
        
    /**
     * Get the <code>AsyncHandler</code> who drive the asynchronous process.
     */
    public AsyncHandler getAsyncHandler();
    
    
    /**
     * Set the <code>AsyncHandler</code> who drive the asynchronous process.
     */
    public void setAsyncHandler(AsyncHandler asyncHandler);
}
