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
import com.sun.grizzly.http.SelectorThread;
import java.util.ArrayList;
import java.util.StringTokenizer;
import java.util.logging.Level;

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

    private final static String ASYNC_FILTER = 
            "com.sun.grizzly.arp.asyncFilters";

    
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
    private static String[] sharedAsyncFilters = null;;
    
    
    /**
     * The <code>AsyncFilter</code> to execute asynchronous operations on 
     * a <code>ProcessorTask</code>.
     */
    private ArrayList<AsyncFilter> asyncFilters = 
            new ArrayList<AsyncFilter>();
    
    
    /**
     * Do we need to invoke filters?
     */
    private boolean invokeFilter = true;

    
    /**
     * Loads filters implementation.
     */
    static {
        loadFilters();
    }
    
    
    /**
     * The <code>AsyncHandler</code> associated with this object.
     */
    private AsyncHandler asyncHandler;
    
    // --------------------------------------------------------------------- //
    
    public DefaultAsyncExecutor(){
        init();
    }
    
    
    private void init(){
        if (sharedAsyncFilters != null){
            for (String filterName: sharedAsyncFilters){
                asyncFilters.add(loadInstance(filterName));
            }
        }
    }
    
    // ------------------------------------------------Asynchrounous Execution --/
    
    /**
     * Pre-execute a <code>ProcessorTask</code> by parsing the request 
     * line.
     */
    public boolean preExecute() throws Exception{
        processorTask = asyncProcessorTask.getProcessorTask(); 
        if ( processorTask == null ){
            throw new IllegalStateException("Null ProcessorTask");
        }
        processorTask.preProcess();
        processorTask.parseRequest();
        return true;
    }
    
    
    /**
     * Interrupt the <code>ProcessorTask</code> if <code>AsyncFilter</code>
     * has been defined.
     * @return true if the execution can continue, false if delayed.
     */
    public boolean interrupt() throws Exception{
        if ( asyncFilters == null || asyncFilters.size() == 0 ) {
            execute();
            return false;
        } else {
            asyncHandler.addToInterruptedQueue(asyncProcessorTask); 
            return invokeFilters();
        }
    }
    
    
    /**
     * Interrupt the <code>ProcessorTask</code> if <code>AsyncFilter</code>
     * has been defined.
     * @return true if the execution can continue, false if delayed.
     */
    public boolean execute() throws Exception{
        processorTask.invokeAdapter();
        return true;
    }

    
    /**
     * Invoke the <code>AsyncFilter</code>
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
     * Post-execute the <code>ProcessorTask</code> by preparing the response,
     * flushing the response and then close or keep-alive the connection.
     */
    public boolean postExecute() throws Exception{
        processorTask.postResponse();
        processorTask.postProcess();        
        processorTask.terminateProcess();
        
        // De-reference so under stress we don't have a simili leak.
        processorTask = null;
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
    
    
    /**
     * Instanciate a class based on a property.
     */
    private static AsyncFilter loadInstance(String property){        
        Class className = null;                               
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

    
    /**
     * Get the <code>AsyncHandler</code> who drive the asynchronous process.
     */
    public AsyncHandler getAsyncHandler() {
        return asyncHandler;
    }
    
    
    /**
     * Set the <code>AsyncHandler</code> who drive the asynchronous process.
     */
    public void setAsyncHandler(AsyncHandler asyncHandler) {
        this.asyncHandler = asyncHandler;
    }
}
