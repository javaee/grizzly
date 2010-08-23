/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2010 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.grizzly.util;

import com.sun.grizzly.tcp.PendingIOhandler;
import java.util.concurrent.Callable;
import com.sun.grizzly.util.ByteBufferFactory.ByteBufferType;
import com.sun.grizzly.util.ThreadAttachment.Mode;
import java.util.concurrent.ExecutorService;

/**
 * Simple worker thread used for processing HTTP requests. All threads are
 * synchronized using a {@link ExecutorService} object
 *
 * @author Jean-Francois Arcand
 */
public class WorkerThreadImpl extends WorkerThread {
    
    public static final int DEFAULT_BYTE_BUFFER_SIZE = 8192;
    
    public static final ByteBufferType DEFAULT_BYTEBUFFER_TYPE = ByteBufferType.HEAP_VIEW;;  

    /**
     * The {@link ExecutorService} on which this thread synchronize.

    protected ExecutorService threadPool;


    **
     * The <code>ThreadGroup</code> used.
     *
    protected final static ThreadGroup threadGroup = new ThreadGroup("Grizzly");
    */

    /**
     * The state/attributes on this WorkerThread.
     */
    protected ThreadAttachment threadAttachment;
    
    
    /**
     * The ByteBufferType used when creating the ByteBuffer attached to this object.
     */
    private ByteBufferType byteBufferType = ByteBufferType.HEAP_VIEW;
    
    
    /**
     * The size of the ByteBuffer attached to this object.
     */
    private int initialByteBufferSize;


    /**
     * Used by selectionkey attachments to enqueue io events that will be executed in
     * selectorhandler.postselect by worker threads instead of the selector thread.
     */
    private PendingIOhandler pendingIOhandler;

    private Object context;
    
    /**
     * Create a Thread that will synchronizes/block on
     * {@link ExecutorService} instance.
     * @param threadGroup <code>ThreadGroup</code>
     * @param runnable <code>Runnable</code>
     */
    public WorkerThreadImpl(ThreadGroup threadGroup, Runnable runnable){
        this(threadGroup, runnable, DEFAULT_BYTE_BUFFER_SIZE);
    }

    public WorkerThreadImpl(Runnable runnable){
        this(null, "workerthread", runnable, 0);
    }

    public WorkerThreadImpl(String name, Runnable runnable){
        this(null, name, runnable, 0);
    }
    /**
     * Create a Thread that will synchronizes/block on
     * {@link ExecutorService} instance.
     * @param threadGroup <code>ThreadGroup</code>
     * @param runnable <code>Runnable</code>
     * @param initialByteBufferSize initial {@link ByteBuffer} size
     */
    public WorkerThreadImpl(ThreadGroup threadGroup, Runnable runnable, 
            int initialByteBufferSize){
        super(threadGroup, runnable);
        this.initialByteBufferSize = initialByteBufferSize;
    }
    
    /**
     * Create a Thread that will synchronizes/block on
     * {@link ExecutorService} instance.
     * @param threadPool {@link ExecutorService}
     * @param name <code>String</code>
     */
    public WorkerThreadImpl(ExecutorService threadPool, String name){
        this(threadPool, name, DEFAULT_BYTE_BUFFER_SIZE);
    }
    
    /**
     * Create a Thread that will synchronizes/block on
     * {@link ExecutorService} instance.
     * @param threadPool {@link ExecutorService}
     * @param name <code>String</code>
     * @param initialByteBufferSize initial {@link ByteBuffer} size
     */
    public WorkerThreadImpl(ExecutorService threadPool, String name,
            int initialByteBufferSize){
        super(name);
        //this.threadPool = threadPool;
        this.initialByteBufferSize = initialByteBufferSize;
    }
    
    /**
     * Create a Thread that will synchronizes/block on
     * {@link ExecutorService} instance.
     * @param threadPool {@link ExecutorService}
     * @param name <code>String</code>
     * @param initialByteBufferSize initial {@link ByteBuffer} size
     */
    public WorkerThreadImpl(ExecutorService threadPool, String name,
            Runnable runnable, int initialByteBufferSize){
        super(runnable, name);
        //this.threadPool = threadPool;
        this.initialByteBufferSize = initialByteBufferSize;
    }

    
    /**
     * Allocate a {@link ByteBuffer} if the current instance is null;
     */
    protected void createByteBuffer(boolean force){
        if (force || byteBuffer == null){
            byteBuffer = ByteBufferFactory.allocate(byteBufferType,
                    initialByteBufferSize);
        }
    }
    
    
    public ThreadAttachment updateAttachment(int mode) {
        ThreadAttachment currentAttachment = getAttachment();
        currentAttachment.reset();

        if ((mode & Mode.BYTE_BUFFER) != 0) {
            currentAttachment.setByteBuffer(byteBuffer);
        }

        if ((mode & Mode.SSL_ENGINE) != 0) {
            currentAttachment.setSSLEngine(sslEngine);
        }

        if ((mode & Mode.INPUT_BB) != 0) {
            currentAttachment.setInputBB(inputBB);
        }

        if ((mode & Mode.OUTPUT_BB) != 0) {
            currentAttachment.setOutputBB(outputBB);
        }

        currentAttachment.setMode(mode);
        
        return currentAttachment;
    }
    
    public ThreadAttachment getAttachment() {
        if (threadAttachment == null) {
            threadAttachment = new ThreadAttachment();
            threadAttachment.associate();
        }

        return threadAttachment;
    }

    public ThreadAttachment detach() {
        ThreadAttachment currentAttachment = getAttachment();
        int mode = currentAttachment.getMode();
        updateAttachment(mode);
        
        // Re-create a new ByteBuffer
        if ((mode & Mode.BYTE_BUFFER) != 0) {
            byteBuffer = null;
        }
        
        if ((mode & Mode.SSL_ENGINE) != 0) {
            sslEngine = null;
        }
        
        if ((mode & Mode.INPUT_BB) != 0) {
            inputBB = null;
        }
        
        if ((mode & Mode.OUTPUT_BB) != 0) {
            outputBB = null;
        }
        
        // Switch to the new ThreadAttachment.
        this.threadAttachment = null;
        
        currentAttachment.deassociate();
        return currentAttachment;
    }


    public void attach(ThreadAttachment threadAttachment) {
        threadAttachment.associate();
        int mode = threadAttachment.getMode();
        
        if ((mode & Mode.BYTE_BUFFER) != 0) {
            byteBuffer = threadAttachment.getByteBuffer();
        }
        
        if ((mode & Mode.SSL_ENGINE) != 0) {
            sslEngine = threadAttachment.getSSLEngine();
        }
        
        if ((mode & Mode.INPUT_BB) != 0) {
            inputBB = threadAttachment.getInputBB();
        }
        
        if ((mode & Mode.OUTPUT_BB) != 0) {
            outputBB = threadAttachment.getOutputBB();
        }
        
        this.threadAttachment = threadAttachment;   
    }

    
    /**
     * The <code>ByteBufferType</code> used to create the {@link ByteBuffer}
     * associated with this object.
     * @return The <code>ByteBufferType</code> used to create the {@link ByteBuffer}
     * associated with this object.
     */
    public ByteBufferType getByteBufferType() {
        return byteBufferType;
    }

    
    /**
     * Set the <code>ByteBufferType</code> to use when creating the
     * {@link ByteBuffer} associated with this object.
     * @param byteBufferType The ByteBuffer type.
     */
    public void setByteBufferType(ByteBufferType byteBufferType) {
        this.byteBufferType = byteBufferType;
    }

    public int getInitialByteBufferSize() {
        return initialByteBufferSize;
    }

    public void setInitialByteBufferSize(int initialByteBufferSize) {
        this.initialByteBufferSize = initialByteBufferSize;
    }

    /**
     * Processes the given task.
     *
     * @param t the task to process
     */
    protected void processTask(Callable t) throws Exception {
        if (t != null){
            t.call();
        }
    }


    /**
     * Used by selectionkey attachments to enqueue io events that will be executed in
     * selectorhandler.postselect by worker threads instead of the selector thread.
     */
    public PendingIOhandler getPendingIOhandler() {
        return pendingIOhandler;
    }

    /**
     * Used by selectionkey attachments to enqueue io events that will be executed in
     * selectorhandler.postselect by worker threads instead of the selector thread.
     */
    public void setPendingIOhandler(PendingIOhandler pendingIOhandler) {
        this.pendingIOhandler = pendingIOhandler;
    }

    public Object getContext() {
        return context;
    }

    public void setContext(Object context) {
        this.context = context;
    }
    
    @Override
    public void reset() {
        if (threadAttachment != null) {
            /** 
             * ThreadAttachment was created during prev. processing and wasn't
             * detached. It could happen due to some error - we need to release
             * the ThreadAttachment association with the current thread
             */
            threadAttachment.deassociate();
            threadAttachment = null;
        }                
        super.reset();
    }
}

