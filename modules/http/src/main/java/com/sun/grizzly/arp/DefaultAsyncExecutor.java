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
package com.sun.grizzly.arp;

import com.sun.grizzly.http.ProcessorTask;
import com.sun.grizzly.util.ClassLoaderUtil;
import java.util.ArrayList;
import java.util.StringTokenizer;

/**
 * Default implementation of the {@link AsyncExecutor}. This class will
 * execute a {@link ProcessorTask} asynchronously, by interrupting the 
 * process based on the logic defined in its associated {@link AsyncFilter}
 * If no {@link AsyncFilter} are defined, the {@link ProcessorTask}
 * will not be interrupted and executed synchronously.
 *
 * @author Jeanfrancois Arcand
 */
public class DefaultAsyncExecutor implements AsyncExecutor{
    
    private final static String ASYNC_FILTER = 
            "com.sun.grizzly.asyncFilters";
    
    /**
     * The <code>AsyncFilter</code> to execute asynchronous operations on 
     * a <code>ProcessorTask</code>.
     */
    private static String[] sharedAsyncFilters = null;
    
    
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
    private ArrayList<AsyncFilter> asyncFilters = 
            new ArrayList<AsyncFilter>();
    
    
    /**
     * The {@link AsyncHandler} associated with this object.
     */
    private AsyncHandler asyncHandler;

    
    // --------------------------------------------------------------------- //
    
    public DefaultAsyncExecutor(){
        init();
    }
    
    
    private void init(){
        loadFilters();
        if (sharedAsyncFilters != null){
            for (String filterName: sharedAsyncFilters){
                asyncFilters.add((AsyncFilter)ClassLoaderUtil.load(filterName));
            }
        }
    }
    
    // ------------------------------------------------Asynchrounous Execution --/    
    /**
     * Pre-execute a {@link ProcessorTask} by parsing the request 
     * line.
     */
    public boolean preExecute() throws Exception{
        processorTask.preProcess();
        
        // True when there is an error or when the (@link FileCache} is enabled
        if (processorTask.parseRequest()){
            finishResponse();
            return false;
        }
        return true;
    }
    
    
    /**
     * Interrupt the {@link ProcessorTask} if {@link AsyncFilter}
     * has been defined.
     * @return true if the execution can continue, false if delayed.
     */
    public boolean interrupt() throws Exception{
        if ( asyncFilters == null || asyncFilters.size() == 0 ) {
            execute();
            return false;
        } else {
            return invokeFilters();
        }
    }
    
    
    /**
     * Execute the associated {@link Adapter} or {@link GrizzlyAdapter}
     * @return true if the execution can continue, false if delayed.
     */
    public boolean execute() throws Exception{
        processorTask.invokeAdapter();
        return true;
    }

    
    /**
     * Invoke the {@link AsyncFilter}
     */
    private boolean invokeFilters(){
        boolean continueExec = true;
        for (AsyncFilter asf: asyncFilters){
            continueExec = asf.doFilter(this);
            if ( !continueExec ){
                break;
            }
        }
        return continueExec;
    }
    
    
    /**
     * Finish the {@link Response} and recyle {@link ProcessorTask}.
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
        if (processorTask == null) return false;
        processorTask.postResponse();
        finishResponse();
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
    protected static void loadFilters(){      
        if ( System.getProperty(ASYNC_FILTER) != null){
            StringTokenizer st = new StringTokenizer(
                    System.getProperty(ASYNC_FILTER),",");
            
            sharedAsyncFilters = new String[st.countTokens()];    
            int i = 0;
            while (st.hasMoreTokens()){
                sharedAsyncFilters[i++] = st.nextToken();                
            } 
        }   
    }  
}
