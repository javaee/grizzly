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
 * A <code>Task</code> that wraps the execution of an asynchronous execution
 * of a <code>ProcessorTask</code>. 
 *
 * @author Jean-Francois Arcand
 */
public interface AsyncTask extends Task{
    
    public final static int PRE_EXECUTE = 0;
    public final static int INTERRUPTED = 1;
    public final static int POST_EXECUTE = 2;
    public final static int COMPLETED = 3;
    public final static int EXECUTE = 4;    
    
    /**
     * Get the <code>AsyncExecutor</code>.
     */
    public AsyncExecutor getAsyncExecutor();

    
    /**
     * Return the <code>ProcessorTask</code>.
     */
    public ProcessorTask getProcessorTask();

    
    /**
     * Return the <code>stage</code> of the current execution.
     */
    public int getStage();

    
    /**
     * Set the <code>AsyncExecutor</code> used by this <code>Task</code>
     * to delegate the execution of a <code>ProcessorTask</code>.
     */
    public void setAsyncExecutor(AsyncExecutor asyncExecutor);

    
    /**
     * Set the <code>ProcessorTask</code> that needs to be executed
     * asynchronously.
     */
    public void setProcessorTask(ProcessorTask processorTask);

}
