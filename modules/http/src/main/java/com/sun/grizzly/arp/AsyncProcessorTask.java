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
import com.sun.grizzly.http.AsyncTask;
import com.sun.grizzly.http.ProcessorTask;
import com.sun.grizzly.http.SelectorThread;
import com.sun.grizzly.http.TaskBase;
import com.sun.grizzly.http.TaskEvent;
import java.util.logging.Level;


/**
 * A <code>Task</code> that wraps the execution of an asynchronous execution
 * of a <code>ProcessorTask</code>. Internaly, this class invoke the associated
 * <code>AsyncExecutor</code> method to execute the <code>ProcessorTask</code>
 * lifecycle operations.
 *
 * @author Jeanfrancois Arcand
 */
public class AsyncProcessorTask extends TaskBase implements AsyncTask {
 
    /**
     * The <code>AsyncExecutor</code> which drive the execution of the 
     * <code>ProcesssorTask</code>
     */
    private AsyncExecutor asyncExecutor;

    
    /**
     * The <code>ProcessorTask</code>
     */
    private ProcessorTask processorTask;
    
    
    /**
     * The current execution stage.
     */
    private int stage = AsyncTask.PRE_EXECUTE;
    
    
    /**
     * Execute the <code>AsyncExecutor</code> based on the <code>stage</code>
     * of the <code>ProcessorTask</code> execution.
     */
    public void doTask() throws java.io.IOException {
        boolean contineExecution = true;
        while ( contineExecution ) {
            try{
                switch(stage){
                    case AsyncTask.PRE_EXECUTE:
                       stage = AsyncTask.INTERRUPTED;                       
                       contineExecution = asyncExecutor.preExecute();
                       break;                
                    case AsyncTask.INTERRUPTED:
                       stage = AsyncTask.POST_EXECUTE;                        
                       contineExecution = asyncExecutor.interrupt();
                       break;  
                    case AsyncTask.EXECUTE:    
                       contineExecution = asyncExecutor.execute();
                       stage = AsyncTask.POST_EXECUTE;
                       break;                           
                    case AsyncTask.POST_EXECUTE:    
                       contineExecution = asyncExecutor.postExecute();
                       stage = AsyncTask.COMPLETED;
                       break;                
                }
            } catch (Throwable t){
                SelectorThread.logger().log(Level.SEVERE,t.getMessage(),t);
                if ( stage <= AsyncTask.INTERRUPTED) {
                    // We must close the connection.
                    stage = AsyncTask.POST_EXECUTE;
                } else {
                    stage = AsyncTask.COMPLETED;
                    throw new RuntimeException(t);
                }
            } finally {
                // If the execution is completed, return this task to the pool.
                if ( stage == AsyncTask.COMPLETED){
                    stage = AsyncTask.PRE_EXECUTE;
                    asyncExecutor.getAsyncHandler().returnTask(this);
                }
            }
        } 
    }

    
    /**
     * Not used.
     */
    public void taskEvent(TaskEvent event) {
    }
    
    /**
     * Return the <code>stage</code> of the current execution.
     */
    public int getStage(){
        return stage;
    }
    
    
    /**
     * Reset the object.
     */
    public void recycle(){
        stage = AsyncTask.PRE_EXECUTE;
        processorTask = null;
    }

    
    /**
     * Set the <code>AsyncExecutor</code> used by this <code>Task</code>
     * to delegate the execution of a <code>ProcessorTask</code>.
     */
    public void setAsyncExecutor(AsyncExecutor asyncExecutor){
        this.asyncExecutor = asyncExecutor;
    }
    
    
    /**
     * Get the <code>AsyncExecutor</code>.
     */
    public AsyncExecutor getAsyncExecutor(){
        return asyncExecutor;
    }
    
    
    /**
     * Set the <code>ProcessorTask</code> that needs to be executed
     * asynchronously.
     */
    public void setProcessorTask(ProcessorTask processorTask){
        this.processorTask = processorTask;
        if ( pipeline == null && processorTask != null) {
            setPipeline(processorTask.getPipeline());
        }        
    }
    
    
    /**
     * Return the <code>ProcessorTask</code>.
     */
    public ProcessorTask getProcessorTask(){
        return processorTask;
    }

    
    /**
     * 
     */
    public void setStage(int stage){
        this.stage = stage;
    }
}
