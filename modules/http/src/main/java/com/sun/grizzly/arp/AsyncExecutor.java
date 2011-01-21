/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2007-2011 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.grizzly.arp;

import com.sun.grizzly.http.ProcessorTask;



/**
 * A interface used to define the execution of a {@link AsyncTask}
 * By default, {@link AsyncTask} will invoke an implementation
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
     * Pre-execute some http request operation like decoding the headers. 
     * @return true if the processing can continue.
     */
    public boolean preExecute() throws Exception;
    
    
    /**
     * Interrupt the current request processing. To resume, just invoke
     * the {@link #execute}
     * @return true if the processing can continue, false if it needs to be 
     *              interrupted.
     */    
    public boolean interrupt() throws Exception;
    
    
    /**
     * Execute the defined {@link Adapter} or {@link GrizzlyAdapter}
     * @return true if the processing can continue, false if it needs to be 
     *              interrupted.
     */    
    public boolean execute() throws Exception;    
    
    
    /**
     * Commit the http response.
     * @return true if the processing can continue.
     */    
    public boolean postExecute() throws Exception;
    
    /**
     * finish the processing on this connection until new data come.
     * @return true if the processing can continue.
     */
    public boolean finishExecute() throws Exception;
    
    /**
     * Set the {@link AsyncProcesssorTask}.
     */
    public void setAsyncTask(AsyncTask task);

    
    /**
     * Get the {@link AsyncProcesssorTask}.
     */    
    public AsyncTask getAsyncTask();
    
    
    /**
     * Add a {@link AsyncFilter}
     */
    public void addAsyncFilter(AsyncFilter asyncFilter);
    
    
    /**
     * Remove an {@link AsyncFilter}
     */
    public boolean removeAsyncFilter(AsyncFilter asyncFilter);
    
        
    /**
     * Get the {@link AsyncHandler} who drive the asynchronous process.
     */
    public AsyncHandler getAsyncHandler();
    
    
    /**
     * Set the {@link AsyncHandler} who drive the asynchronous process.
     */
    public void setAsyncHandler(AsyncHandler asyncHandler);
    
    
    /**
     * Set the {@link ProcessorTask} used to execute the request processing.
     * @param task a {@link ProcessorTask} 
     */    
    public void setProcessorTask(ProcessorTask task);
    
    
    /**
     * The {@link ProcessorTask} used to execute the request processing.
     * @return {@link ProcessorTask} used to execute the request processing.
     */        
    public ProcessorTask getProcessorTask();

    /**
     * Reset
     */
    public void reset();

}
