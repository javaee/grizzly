/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 1997-2014 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.enterprise.web.connector.grizzly.async;

import com.sun.enterprise.web.connector.grizzly.AsyncExecutor;
import com.sun.enterprise.web.connector.grizzly.AsyncFilter;
import com.sun.enterprise.web.connector.grizzly.AsyncHandler;
import com.sun.enterprise.web.connector.grizzly.AsyncTask;
import com.sun.enterprise.web.connector.grizzly.ProcessorTask;
import com.sun.enterprise.web.connector.grizzly.SelectorThread;
import java.util.ArrayList;
import java.util.StringTokenizer;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Default implementation of the <code>AsyncExecutor</code>. This class will
 * execute a <code>ProcessorTask</code> asynchronously, by interrupting the 
 * process based on the logic defined in its associated <code>AsyncFilter</code>
 * If no <code>AsyncFilter</code> are defined, the <code>ProcessorTask</code>
 * will not be interrupted and executed synchronously.
 *
 * @author Jeanfrancois Arcand
 */
public class DefaultAsyncExecutor implements AsyncExecutor{
    private static final Logger LOGGER =
            Logger.getLogger(DefaultAsyncExecutor.class.getName());
    private static final Level LOG_LEVEL = Level.FINEST;

    private final static String ASYNC_FILTER = 
            "com.sun.enterprise.web.connector.grizzly.asyncFilters";

    /**
     * The <code>AsyncFilter</code> to execute asynchronous operations on 
     * a <code>ProcessorTask</code>.
     */
    private final static ArrayList<AsyncFilter> sharedAsyncFilters =
            loadFilters();

    
    /**
     * The <code>AsyncTask</code> used to wrap the 
     * <code>ProcessorTask</code>
     */
    private AsyncTask asyncProcessorTask;
       
    
    /**
     * The associated <code>ProcessorTask</code>
     */
    private ProcessorTask processorTask;
    
    
    /**
     * The <code>AsyncFilter</code> to execute asynchronous operations on 
     * a <code>ProcessorTask</code>.
     */
    private final ArrayList<AsyncFilter> asyncFilters =
            new ArrayList<AsyncFilter>(sharedAsyncFilters);
    
    
    /**
     * The <code>AsyncHandler</code> associated with this object.
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
     * Pre-execute a <code>ProcessorTask</code> by parsing the request 
     * line.
     */
    public boolean preExecute() throws Exception {
        if (LOGGER.isLoggable(LOG_LEVEL)) {
            LOGGER.log(LOG_LEVEL, "DefaultAsyncExecutor.preExecute apt={0}", new Object[]{asyncProcessorTask});
        }
        
        if (!parseHeaderPhase.getAndSet(true)) {
            if (LOGGER.isLoggable(LOG_LEVEL)) {
                LOGGER.log(LOG_LEVEL, "DefaultAsyncExecutor.preExecute_1");
            }

            processorTask.preProcess();

            // True when there is an error or when the (@link FileCache} is enabled
            if (processorTask.parseRequest()) {
                if (LOGGER.isLoggable(LOG_LEVEL)) {
                    LOGGER.log(LOG_LEVEL, "DefaultAsyncExecutor.preExecute_2");
                }
                finishResponse();
                return false;
            }
            if (LOGGER.isLoggable(LOG_LEVEL)) {
                LOGGER.log(LOG_LEVEL, "DefaultAsyncExecutor.preExecute parsed OK. request-url={0}",
                        processorTask.getRequestURI());
            }
            
            return true;
        }
        return false;
    }
    
    
    /**
     * Interrupt the <code>ProcessorTask</code> if <code>AsyncFilter</code>
     * has been defined.
     * @return true if the execution can continue, false if delayed.
     */
    public boolean interrupt() throws Exception {
        if (LOGGER.isLoggable(LOG_LEVEL)) {
            LOGGER.log(LOG_LEVEL, "DefaultAsyncExecutor.interrupt apt={0}", new Object[]{asyncProcessorTask});
        }
        if (asyncFilters.isEmpty()) {
            if (LOGGER.isLoggable(LOG_LEVEL)) {
                LOGGER.log(LOG_LEVEL, "DefaultAsyncExecutor.interrupt_1");
            }
            return execute();
        } else {
            if (LOGGER.isLoggable(LOG_LEVEL)) {
                LOGGER.log(LOG_LEVEL, "DefaultAsyncExecutor.interrupt_2");
            }
            final AsyncFilter.Result result = invokeFilters();
            if (result == AsyncFilter.Result.NEXT) {
                if (LOGGER.isLoggable(LOG_LEVEL)) {
                    LOGGER.log(LOG_LEVEL, "DefaultAsyncExecutor.interrupt_3");
                }
                return execute();
            }

            return result == AsyncFilter.Result.FINISH;
        }
    }
    
    
    /**
     * Interrupt the <code>ProcessorTask</code> if <code>AsyncFilter</code>
     * has been defined.
     * @return true if the execution can continue, false if delayed.
     */
    public boolean execute() throws Exception{
        if (LOGGER.isLoggable(LOG_LEVEL)) {
            LOGGER.log(LOG_LEVEL, "DefaultAsyncExecutor.execute apt={0}", new Object[]{asyncProcessorTask});
        }
        if (!executeAdapterPhase.getAndSet(true)){
            if (LOGGER.isLoggable(LOG_LEVEL)) {
                LOGGER.log(LOG_LEVEL, "DefaultAsyncExecutor.execute_1");
            }
            processorTask.invokeAdapter();
            return true;
        }
        return false;
    }

    
    /**
     * Invoke the <code>AsyncFilter</code>
     */
    private AsyncFilter.Result invokeFilters() {
        if (LOGGER.isLoggable(LOG_LEVEL)) {
            LOGGER.log(LOG_LEVEL, "DefaultAsyncExecutor.invokeFilters apt={0}",
                    new Object[]{asyncProcessorTask});
        }
        for (AsyncFilter asyncFilter : asyncFilters) {
            if (LOGGER.isLoggable(LOG_LEVEL)) {
                LOGGER.log(LOG_LEVEL, "DefaultAsyncExecutor.invokeFilters.doFilter apt={0}, filter={1}",
                        new Object[]{asyncProcessorTask, asyncFilter});
            }
            final AsyncFilter.Result result = asyncFilter.doFilter(this);
            if (LOGGER.isLoggable(LOG_LEVEL)) {
                LOGGER.log(LOG_LEVEL, "DefaultAsyncExecutor.invokeFilters.doFilter apt={0}, filter={1}, result={2}",
                        new Object[]{asyncProcessorTask, asyncFilter, result});
            }
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
        if (LOGGER.isLoggable(LOG_LEVEL)) {
            LOGGER.log(LOG_LEVEL, "DefaultAsyncExecutor.finishResponse apt={0}",
                    new Object[]{asyncProcessorTask});
        }
        processorTask.postProcess();        
        processorTask.terminateProcess();
        // De-reference so under stress we don't have a simili leak.
        processorTask = null;    
    }
    
    
    /**
     * Resume the connection by commit the {@link Response} object.
     */
    public boolean postExecute() throws Exception {
        if (LOGGER.isLoggable(LOG_LEVEL)) {
            LOGGER.log(LOG_LEVEL, "DefaultAsyncExecutor.postExecute apt={0}",
                    new Object[]{asyncProcessorTask});
        }
        if (!commitResponsePhase.getAndSet(true)) {
            if (LOGGER.isLoggable(LOG_LEVEL)) {
                LOGGER.log(LOG_LEVEL, "DefaultAsyncExecutor.postExecute_1");
            }
            if (processorTask == null) return false;
            if (LOGGER.isLoggable(LOG_LEVEL)) {
                LOGGER.log(LOG_LEVEL, "DefaultAsyncExecutor.postExecute_2");
            }
            processorTask.postResponse();
            return true;
        }
        return false;
    }

    /**
     * Resume the connection by commit the {@link Response} object.
     */
    public boolean finishExecute() throws Exception {
        if (LOGGER.isLoggable(LOG_LEVEL)) {
            LOGGER.log(LOG_LEVEL, "DefaultAsyncExecutor.finishExecute apt={0}",
                    new Object[]{asyncProcessorTask});
        }
        if (!finishResponsePhase.getAndSet(true)){
            if (LOGGER.isLoggable(LOG_LEVEL)) {
                LOGGER.log(LOG_LEVEL, "DefaultAsyncExecutor.finishExecute_1");
            }
            if (processorTask == null) return false;
            if (LOGGER.isLoggable(LOG_LEVEL)) {
                LOGGER.log(LOG_LEVEL, "DefaultAsyncExecutor.finishExecute_2");
            }
            finishResponse();
            return false;
        }
        return false;
    }
      
    /**
     * Set the <code>AsyncTask</code>.
     */
    public void setAsyncTask(AsyncTask asyncProcessorTask){
        this.asyncProcessorTask = asyncProcessorTask;
    }
    
    
    /**
     * Return <code>AsyncTask</code>.
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
                AsyncFilter filter = (AsyncFilter) loadInstance(st.nextToken());
                if (filter != null) {
                    al.add(filter);
                }
            } 
        }
        return al;
    }
    
    /**
     * Instantiate a class based on a property.
     */
    private static AsyncFilter loadInstance(String property){        
        Class className;                               
        try{                              
            className = Class.forName(property);
            return (AsyncFilter)className.newInstance();
        } catch (ClassNotFoundException ex){
            SelectorThread.logger().log(Level.WARNING,ex.getMessage(),ex);
        } catch (InstantiationException ex){
            SelectorThread.logger().log(Level.WARNING,ex.getMessage(),ex);            
        } catch (IllegalAccessException ex){
            SelectorThread.logger().log(Level.WARNING,ex.getMessage(),ex);            
        }
        return null;
    }   

    /**
     * Reset
     */
    public void reset() {
        if (LOGGER.isLoggable(LOG_LEVEL)) {
            LOGGER.log(LOG_LEVEL, "DefaultAsyncExecutor.reset apt={0}",
                    new Object[]{asyncProcessorTask});
        }        
        parseHeaderPhase.set(false);
        executeAdapterPhase.set(false);
        commitResponsePhase.set(false);
        finishResponsePhase.set(false);
    }
}
