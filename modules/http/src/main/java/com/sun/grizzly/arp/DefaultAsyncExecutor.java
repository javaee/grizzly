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
import com.sun.grizzly.tcp.Adapter;
import com.sun.grizzly.tcp.Response;
import com.sun.grizzly.tcp.http11.GrizzlyAdapter;
import com.sun.grizzly.util.ClassLoaderUtil;
import java.util.ArrayList;
import java.util.StringTokenizer;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Default implementation of the {@link AsyncExecutor}. This class will
 * execute a {@link ProcessorTask} asynchronously, by interrupting the 
 * process based on the logic defined in its associated {@link AsyncFilter}
 * If no {@link AsyncFilter} are defined, the {@link ProcessorTask}
 * will not be interrupted and executed synchronously.
 *
 * @author Jeanfrancois Arcand
 */
public class DefaultAsyncExecutor implements AsyncExecutor {
    
    private final static String ASYNC_FILTER = 
            "com.sun.grizzly.asyncFilters";
    
    /**
     * The <code>AsyncFilter</code> to execute asynchronous operations on 
     * a <code>ProcessorTask</code>.
     */
    private final static ArrayList<AsyncFilter> sharedAsyncFilters = loadFilters();
    
    
    /**
     * The {@link AsyncTask} used to wrap the 
     * {@link ProcessorTask}
     */
    private AsyncTask asyncProcessorTask;
       
    
    /**
     * The associated {@link ProcessorTask}
     */
    private ProcessorTask processorTask;
    
    
    /**
     * The {@link AsyncFilter} to execute asynchronous operations on 
     * a {@link ProcessorTask}.
     */
    private final ArrayList<AsyncFilter> asyncFilters = new ArrayList<AsyncFilter>(sharedAsyncFilters);
    
    
    /**
     * The {@link AsyncHandler} associated with this object.
     */
    private AsyncHandler asyncHandler;

    /**
     * Only one execution of every steps are allowed.
     */
    private final AtomicBoolean parseHeaderPhase = new AtomicBoolean(false);
    private final AtomicBoolean executeAdapterPhase = new AtomicBoolean(false);
    private final AtomicBoolean commitResponsePhase = new AtomicBoolean(false);
    private final AtomicBoolean finishResponsePhase = new AtomicBoolean(false);

    
    // --------------------------------------------------------------------- //
    
    public DefaultAsyncExecutor(){
    }
    
    
    
    // ------------------------------------------------Asynchrounous Execution --/    
    /**
     * Pre-execute a {@link ProcessorTask} by parsing the request 
     * line.
     */
    public boolean preExecute() throws Exception{
        if (!parseHeaderPhase.getAndSet(true)){
            processorTask.preProcess();

            // True when there is an error or when the (@link FileCache} is enabled
            if (processorTask.parseRequest()){
                finishResponse();
                return false;
            }
            return true;
        }
        return false;
    }
    
    
    /**
     * Interrupt the {@link ProcessorTask} if {@link AsyncFilter}
     * has been defined.
     * @return true if the execution can continue, false if delayed.
     */
    public boolean interrupt() throws Exception{
        if (asyncFilters.isEmpty()) {
            return execute();
        } else {
            final AsyncFilter.Result result = invokeFilters();
            if (result == AsyncFilter.Result.NEXT) {
                return execute();
            }

            return result == AsyncFilter.Result.FINISH;
        }
    }
    
    
    /**
     * Execute the associated {@link Adapter} or {@link GrizzlyAdapter}
     * @return true if the execution can continue, false if delayed.
     */
    public boolean execute() throws Exception{
        if (!executeAdapterPhase.getAndSet(true)){
            processorTask.invokeAdapter();
            return true;
        }
        return false;
    }

    
    /**
     * Invoke the {@link AsyncFilter}s
     */
    private AsyncFilter.Result invokeFilters(){
        for (AsyncFilter asyncFilter : asyncFilters) {
            final AsyncFilter.Result result = asyncFilter.doFilter(this);
            if (result != AsyncFilter.Result.NEXT) {
                return result;
            }
        }

        return AsyncFilter.Result.NEXT;
    }
    
    
    /**
     * Finish the {@link Response} and recycle {@link ProcessorTask}.
     */
    private void finishResponse() throws Exception{       
        processorTask.postProcess();        
        processorTask.terminateProcess();
        // De-reference so under stress we don't have a simili leak.
        processorTask = null;    
    }    
    
    
    /**
     * Resume the connection by commit the {@link Response} object.
     */
    public boolean postExecute() throws Exception{
        if (!commitResponsePhase.getAndSet(true)){
            if (processorTask == null) return false;
            processorTask.postResponse();
            return true;
        }
        return false;
    }

    /**
     * Resume the connection by commit the {@link Response} object.
     */
    public boolean finishExecute() throws Exception{
        if (!finishResponsePhase.getAndSet(true)){
            if (processorTask == null) return false;
            finishResponse();
            return false;
        }
        return false;
    }
      
    /**
     * Set the {@link AsyncTask}.
     */
    public void setAsyncTask(AsyncTask asyncProcessorTask){
        this.asyncProcessorTask = asyncProcessorTask;
    }
    
    
    /**
     * Return {@link AsyncTask}.
     */
    public AsyncTask getAsyncTask(){
        return asyncProcessorTask;
    }
    
    
    // --------------------------------------------------------- Util --------//  
 
    /**
     * Add an {@link AsyncFilter}
     */
    public void addAsyncFilter(AsyncFilter asyncFilter) {
        asyncFilters.add(asyncFilter);
    }

    
    /**
     * Remove an {@link AsyncFilter}
     */
    public boolean removeAsyncFilter(AsyncFilter asyncFilter) {
        return asyncFilters.remove(asyncFilter);
    }

    
    /**
     * Get the {@link AsyncHandler} who drive the asynchronous process.
     */
    public AsyncHandler getAsyncHandler() {
        return asyncHandler;
    }
    
    
    /**
     * Set the {@link AsyncHandler} who drive the asynchronous process.
     */
    public void setAsyncHandler(AsyncHandler asyncHandler) {
        this.asyncHandler = asyncHandler;
    }

    
    /**
     * Set the {@link ProcessorTask} used to execute the request processing.
     * @param task a {@link ProcessorTask} 
     */
    public void setProcessorTask(ProcessorTask task) {
        processorTask = task;
    }
    
    
    /**
     * The {@link ProcessorTask} used to execute the request processing.
     * @return {@link ProcessorTask} used to execute the request processing.
     */
    public ProcessorTask getProcessorTask(){
        return processorTask;
    }
    
    
    /**
     * Load the list of <code>AsynchFilter</code>.
     */
    protected static ArrayList<AsyncFilter> loadFilters(){
        ArrayList<AsyncFilter> al = new ArrayList<AsyncFilter>();
        if ( System.getProperty(ASYNC_FILTER) != null){
            StringTokenizer st = new StringTokenizer(
                    System.getProperty(ASYNC_FILTER),",");
            while (st.hasMoreTokens()){
                AsyncFilter filter = (AsyncFilter)ClassLoaderUtil.load(st.nextToken());
                if (filter != null) {
                    al.add(filter);
                }
            } 
        }
        return al;
    }

    /**
     * Reset
     */
    public void reset(){
        parseHeaderPhase.set(false);
        executeAdapterPhase.set(false);
        commitResponsePhase.set(false);
        finishResponsePhase.set(false);
    }
}
