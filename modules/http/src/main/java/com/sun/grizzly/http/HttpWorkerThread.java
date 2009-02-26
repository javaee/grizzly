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
 * Header Notice in each file and include the License file s
 * at glassfish/bootstrap/legal/CDDLv1.0.txt.  
 * If applicable, add the following below the CDDL Header, 
 * with the fields enclosed by brackets [] replaced by
 * you own identifying information: 
 * "Portions Copyrighted [year] [name of copyright owner]"
 * 
 * Copyright 2006 Sun Microsystems, Inc. All rights reserved.
 */

package com.sun.grizzly.http;

import com.sun.grizzly.Pipeline;
import com.sun.grizzly.util.ByteBufferInputStream;
import com.sun.grizzly.util.WorkerThreadImpl;
import java.util.concurrent.Callable;

import com.sun.grizzly.util.StreamAlgorithm;

/**
 * Specialized WorkerThread.
 *
 * @author Jeanfrancois Arcand
 */
public class HttpWorkerThread extends WorkerThreadImpl{
    
    private ProcessorTask processorTask;
    
    
    private StreamAlgorithm streamAlgorithm;   
    
    
    private ByteBufferInputStream inputStream = new ByteBufferInputStream();    
    
    
    /** 
     * Create a Thread that will synchronizes/block on 
     * <code>Pipeline</code> instance.
     * @param threadGroup <code>ThreadGroup</code>
     * @param runnable <code>Runnable</code>
     */
    public HttpWorkerThread(ThreadGroup threadGroup, Runnable runnable){
        super(threadGroup, runnable);                    
    }    
    
    
    /** 
     * Create a Thread that will synchronizes/block on 
     * <code>Pipeline</code> instance.
     * @param pipeline <code>Pipeline</code>
     * @param name <code>String</code>
     */
    public HttpWorkerThread(Pipeline<Callable> pipeline, String name){
        super(pipeline, name);                    
    }

    
    public StreamAlgorithm getStreamAlgorithm() {
        return streamAlgorithm;
    }

    
    public void setStreamAlgorithm(StreamAlgorithm streamAlgorithm) {
        this.streamAlgorithm = streamAlgorithm;
    }

    
    public ByteBufferInputStream getInputStream() {
        return inputStream;
    }

    
    public void setInputStream(ByteBufferInputStream inputStream) {
        this.inputStream = inputStream;
    }

    
    public ProcessorTask getProcessorTask() {
        return processorTask;
    }

    
    public void setProcessorTask(ProcessorTask processorTask) {
        this.processorTask = processorTask;
    }
    
}
