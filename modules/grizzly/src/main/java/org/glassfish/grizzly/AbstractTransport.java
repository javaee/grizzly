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

package org.glassfish.grizzly;

import org.glassfish.grizzly.attributes.AttributeBuilder;
import org.glassfish.grizzly.memory.MemoryManager;
import org.glassfish.grizzly.util.ExceptionHandler;
import org.glassfish.grizzly.util.ObjectPool;
import org.glassfish.grizzly.util.StateHolder;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import org.glassfish.grizzly.util.LinkedTransferQueue;

/**
 *
 * @author oleksiys
 */
public abstract class AbstractTransport implements Transport {
    protected String name;
    protected boolean isBlocking;
    protected StateHolder<State> state;
    protected ObjectPool<Context> defaultContextPool;
    protected Processor processor;
    protected ProcessorSelector processorSelector;
    protected ProcessorExecutorSelector processorExecutorSelector;
    protected MemoryManager memoryManager;
    protected ExecutorService workerThreadPool;
    protected ExecutorService internalThreadPool;
    protected AttributeBuilder attributeBuilder;

    protected int readBufferSize;
    protected int writeBufferSize;

    protected volatile LinkedTransferQueue<ExceptionHandler> exceptionHandlers;
    
    public AbstractTransport(String name) {
        this.name = name;
        state = new StateHolder<State>(false, State.STOP);
    }
    
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isBlocking() {
        return isBlocking;
    }

    public void configureBlocking(boolean isBlocking) {
        this.isBlocking = isBlocking;
    }

    public StateHolder<State> getState() {
        return state;
    }

    public int getReadBufferSize() {
        return readBufferSize;
    }

    public void setReadBufferSize(int readBufferSize) {
        this.readBufferSize = readBufferSize;
    }

    public int getWriteBufferSize() {
        return writeBufferSize;
    }

    public void setWriteBufferSize(int writeBufferSize) {
        this.writeBufferSize = writeBufferSize;
    }

    /**
     * {@inheritDoc}
     */
    public boolean isStopped() {
        State currentState = state.getState();
        return currentState == State.STOP || currentState == State.STOPPING;
    }

    public ObjectPool<Context> getDefaultContextPool() {
        return defaultContextPool;
    }

    public void setDefaultContextPool(ObjectPool<Context> defaultContextPool) {
        this.defaultContextPool = defaultContextPool;
    }

    public Processor getProcessor() {
        return processor;
    }

    public void setProcessor(Processor processor) {
        this.processor = processor;
    }

    public ProcessorSelector getProcessorSelector() {
        return processorSelector;
    }

    public void setProcessorSelector(ProcessorSelector selector) {
        processorSelector = selector;
    }

    public ProcessorExecutorSelector getProcessorExecutorSelector() {
        return processorExecutorSelector;
    }

    public void setProcessorExecutorSelector(
            ProcessorExecutorSelector processorExecutorSelector) {
        this.processorExecutorSelector = processorExecutorSelector;
    }


    public MemoryManager getMemoryManager() {
        return memoryManager;
    }

    public void setMemoryManager(MemoryManager memoryManager) {
        this.memoryManager = memoryManager;
    }

    public ExecutorService getWorkerThreadPool() {
        return workerThreadPool;
    }

    public void setWorkerThreadPool(ExecutorService workerThreadPool) {
        this.workerThreadPool = workerThreadPool;
    }

    public ExecutorService getInternalThreadPool() {
        return internalThreadPool;
    }

    public void setInternalThreadPool(ExecutorService internalThreadPool) {
        this.internalThreadPool = internalThreadPool;
    }

    public AttributeBuilder getAttributeBuilder() {
        return attributeBuilder;
    }

    public void setAttributeBuilder(AttributeBuilder attributeBuilder) {
        this.attributeBuilder = attributeBuilder;
    }
    
    public void addExceptionHandler(ExceptionHandler handler) {
        if (exceptionHandlers == null) {
            synchronized(state) {
                if (exceptionHandlers == null) {
                    exceptionHandlers = new LinkedTransferQueue<ExceptionHandler>();
                }
            }
        }
        
        exceptionHandlers.add(handler);
    }
    
    public void removeExceptionHandler(ExceptionHandler handler) {
        if (exceptionHandlers == null) return;
        
        exceptionHandlers.remove(handler);
    }

    public void notifyException(Severity severity, Throwable throwable) {
        if (exceptionHandlers == null || exceptionHandlers.isEmpty()) return;
        for(ExceptionHandler exceptionHandler : exceptionHandlers) {
            exceptionHandler.notifyException(severity, throwable);
        }
    }    
    
    /**
     * Close the connection, managed by Transport
     * 
     * @param connection
     * @throws java.io.IOException
     */
    protected abstract void closeConnection(Connection connection) throws IOException;

    /**
     * Starts the transport
     * 
     * @throws java.io.IOException
     */
    public abstract void start() throws IOException;
    
    /**
     * Stops the transport and closes all the connections
     * 
     * @throws java.io.IOException
     */
    public abstract void stop() throws IOException;
    
    /**
     * Pauses the transport
     * 
     * @throws java.io.IOException
     */
    public abstract void pause() throws IOException;
    
    /**
     * Resumes the transport after a pause
     * 
     * @throws java.io.IOException
     */
    public abstract void resume() throws IOException;
}
