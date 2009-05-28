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

package com.sun.grizzly;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import com.sun.grizzly.attributes.AttributeBuilder;
import com.sun.grizzly.memory.MemoryManager;
import com.sun.grizzly.utils.ExceptionHandler;
import com.sun.grizzly.utils.StateHolder;
import com.sun.grizzly.utils.LinkedTransferQueue;

/**
 * Abstract {@link Transport}.
 * Implements common transport functionality.
 *
 * @author Alexey Stashok
 */
public abstract class AbstractTransport implements Transport {
    /**
     * Transport name
     */
    protected String name;

    /**
     * Transport mode
     */
    protected boolean isBlocking;

    /**
     * Transport state controller
     */
    protected StateHolder<State> state;

    /**
     * Transport default Processor
     */
    protected Processor processor;

    /**
     * Transport default ProcessorSelector
     */
    protected ProcessorSelector processorSelector;

    /**
     * Transport Strategy
     */
    protected Strategy strategy;

    /**
     * Transport MemoryManager
     */
    protected MemoryManager memoryManager;

    /**
     * Transport worker thread pool
     */
    protected ExecutorService workerThreadPool;

    /**
     * Transport internal thread pool
     */
    protected ExecutorService internalThreadPool;

    /**
     * Transport AttributeBuilder, which will be used to create Attributes
     */
    protected AttributeBuilder attributeBuilder;

    /**
     * Transport default buffer size for read operations
     */
    protected int readBufferSize;

    /**
     * Transport default buffer size for write operations
     */
    protected int writeBufferSize;

    /**
     * Transport ExceptionHandler list
     */
    protected LinkedTransferQueue<ExceptionHandler> exceptionHandlers;
    
    public AbstractTransport(String name) {
        this.name = name;
        state = new StateHolder<State>(false, State.STOP);
        exceptionHandlers = new LinkedTransferQueue<ExceptionHandler>();
    }
    
    /**
     * {@inheritDoc}
     */
    public String getName() {
        return name;
    }

    /**
     * {@inheritDoc}
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * {@inheritDoc}
     */
    public boolean isBlocking() {
        return isBlocking;
    }

    /**
     * {@inheritDoc}
     */
    public void configureBlocking(boolean isBlocking) {
        this.isBlocking = isBlocking;
    }

    /**
     * {@inheritDoc}
     */
    public StateHolder<State> getState() {
        return state;
    }

    /**
     * {@inheritDoc}
     */
    public int getReadBufferSize() {
        return readBufferSize;
    }

    /**
     * {@inheritDoc}
     */
    public void setReadBufferSize(int readBufferSize) {
        this.readBufferSize = readBufferSize;
    }

    /**
     * {@inheritDoc}
     */
    public int getWriteBufferSize() {
        return writeBufferSize;
    }

    /**
     * {@inheritDoc}
     */
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

    /**
     * {@inheritDoc}
     */
    public Processor getProcessor() {
        return processor;
    }

    /**
     * {@inheritDoc}
     */
    public void setProcessor(Processor processor) {
        this.processor = processor;
    }

    /**
     * {@inheritDoc}
     */
    public ProcessorSelector getProcessorSelector() {
        return processorSelector;
    }

    /**
     * {@inheritDoc}
     */
    public void setProcessorSelector(ProcessorSelector selector) {
        processorSelector = selector;
    }

    /**
     * {@inheritDoc}
     */
    public Strategy getStrategy() {
        return strategy;
    }

    /**
     * {@inheritDoc}
     */
    public void setStrategy(Strategy strategy) {
        this.strategy = strategy;
    }

    /**
     * {@inheritDoc}
     */
    public MemoryManager getMemoryManager() {
        return memoryManager;
    }

    /**
     * {@inheritDoc}
     */
    public void setMemoryManager(MemoryManager memoryManager) {
        this.memoryManager = memoryManager;
    }

    /**
     * {@inheritDoc}
     */
    public ExecutorService getWorkerThreadPool() {
        return workerThreadPool;
    }

    /**
     * {@inheritDoc}
     */
    public void setWorkerThreadPool(ExecutorService workerThreadPool) {
        this.workerThreadPool = workerThreadPool;
    }

    /**
     * {@inheritDoc}
     */
    public ExecutorService getInternalThreadPool() {
        return internalThreadPool;
    }

    /**
     * {@inheritDoc}
     */
    public void setInternalThreadPool(ExecutorService internalThreadPool) {
        this.internalThreadPool = internalThreadPool;
    }

    /**
     * {@inheritDoc}
     */
    public AttributeBuilder getAttributeBuilder() {
        return attributeBuilder;
    }

    /**
     * {@inheritDoc}
     */
    public void setAttributeBuilder(AttributeBuilder attributeBuilder) {
        this.attributeBuilder = attributeBuilder;
    }
    
    /**
     * {@inheritDoc}
     */
    public void addExceptionHandler(ExceptionHandler handler) {
        exceptionHandlers.add(handler);
    }
    
    /**
     * {@inheritDoc}
     */
    public void removeExceptionHandler(ExceptionHandler handler) {
        exceptionHandlers.remove(handler);
    }

    /**
     * {@inheritDoc}
     */
    public void notifyException(Severity severity, Throwable throwable) {
        if (exceptionHandlers == null || exceptionHandlers.isEmpty()) return;
        for(ExceptionHandler exceptionHandler : exceptionHandlers) {
            exceptionHandler.notifyException(severity, throwable);
        }
    }

    /**
     * {@inheritDoc}
     */
    public void fireIOEvent(IOEvent ioEvent, Connection connection) throws IOException {
        fireIOEvent(ioEvent, connection, null);
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
