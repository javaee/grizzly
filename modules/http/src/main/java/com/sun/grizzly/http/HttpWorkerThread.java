/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2007-2010 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.grizzly.http;

import com.sun.grizzly.util.InputReader;
import com.sun.grizzly.util.WorkerThreadImpl;

import com.sun.grizzly.util.StreamAlgorithm;
import com.sun.grizzly.util.ThreadAttachment;
import com.sun.grizzly.util.Interceptor;
import com.sun.grizzly.standalone.StaticHandler;

import java.util.concurrent.ExecutorService;

/**
 * Specialized WorkerThread.
 *
 * @author Jeanfrancois Arcand
 */
public class HttpWorkerThread extends WorkerThreadImpl {
    
    private ProcessorTask processorTask;
    
    
    private StreamAlgorithm streamAlgorithm;   
    
    
    private InputReader inputStream = new InputReader();    
    
    
    /** 
     * Create a Thread that will synchronizes/block on 
     * {@link ExecutorService} instance.
     * @param threadGroup <code>ThreadGroup</code>
     * @param runnable <code>Runnable</code>
     */
    public HttpWorkerThread(ThreadGroup threadGroup, Runnable runnable){
        super(threadGroup, runnable);                    
    }    
    
    
    /** 
     * Create a Thread that will synchronizes/block on 
     * {@link ExecutorService} instance.
     * @param threadPool {@link ExecutorService}
     * @param name <code>String</code>
     */
    public HttpWorkerThread(ExecutorService threadPool, String name){
        super(threadPool, name);
    }

    /**
     * Create a Thread
     * @param threadPool {@link ExecutorService} instance
     * @param name thread name
     * @param runnable task to execute
     * @param initialByteBufferSize initial size of thread associated
     * {@link ByteBuffer}
     */
    public HttpWorkerThread(ExecutorService threadPool, String name, Runnable runnable,
            int initialByteBufferSize) {
        super(threadPool, name, runnable, initialByteBufferSize);
    }

    
    public StreamAlgorithm getStreamAlgorithm() {
        return streamAlgorithm;
    }

    
    public void setStreamAlgorithm(StreamAlgorithm streamAlgorithm) {
        this.streamAlgorithm = streamAlgorithm;
    }

    
    public InputReader getInputStream() {
        return inputStream;
    }

    
    public void setInputStream(InputReader inputStream) {
        this.inputStream = inputStream;
    }

    
    public ProcessorTask getProcessorTask() {
        return processorTask;
    }

    
    public void setProcessorTask(ProcessorTask processorTask) {
        this.processorTask = processorTask;
    }   
    
    
    @Override
    public ThreadAttachment getAttachment() {
       if (threadAttachment == null) {
            threadAttachment = new KeepAliveThreadAttachment();
            threadAttachment.associate();
        }
        return threadAttachment;
    }

    @Override
    public void reset() {
        if( streamAlgorithm != null ) {
            Interceptor handler = streamAlgorithm.getHandler();
            if( handler instanceof StaticHandler ) {
                ((StaticHandler) handler).reset();
            }
        }
        super.reset();
    }
}
