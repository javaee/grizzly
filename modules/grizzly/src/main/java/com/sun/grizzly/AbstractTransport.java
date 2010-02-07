/*
 * 
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2007-2010 Sun Microsystems, Inc. All rights reserved.
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
    protected volatile boolean isBlocking;

    protected volatile boolean isStandalone;

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
     * Transport thread pool
     */
    protected ExecutorService threadPool;

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
    @Override
    public String getName() {
        return name;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setName(String name) {
        this.name = name;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isBlocking() {
        return isBlocking;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void configureBlocking(boolean isBlocking) {
        this.isBlocking = isBlocking;
    }

    @Override
    public boolean isStandalone() {
        return isStandalone;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public StateHolder<State> getState() {
        return state;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getReadBufferSize() {
        return readBufferSize;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setReadBufferSize(int readBufferSize) {
        this.readBufferSize = readBufferSize;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getWriteBufferSize() {
        return writeBufferSize;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setWriteBufferSize(int writeBufferSize) {
        this.writeBufferSize = writeBufferSize;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isStopped() {
        State currentState = state.getState();
        return currentState == State.STOP || currentState == State.STOPPING;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Processor getProcessor() {
        return processor;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setProcessor(Processor processor) {
        this.processor = processor;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ProcessorSelector getProcessorSelector() {
        return processorSelector;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setProcessorSelector(ProcessorSelector selector) {
        processorSelector = selector;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Strategy getStrategy() {
        return strategy;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setStrategy(Strategy strategy) {
        this.strategy = strategy;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MemoryManager getMemoryManager() {
        return memoryManager;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setMemoryManager(MemoryManager memoryManager) {
        this.memoryManager = memoryManager;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ExecutorService getThreadPool() {
        return threadPool;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setThreadPool(ExecutorService threadPool) {
        this.threadPool = threadPool;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AttributeBuilder getAttributeBuilder() {
        return attributeBuilder;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setAttributeBuilder(AttributeBuilder attributeBuilder) {
        this.attributeBuilder = attributeBuilder;
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public void addExceptionHandler(ExceptionHandler handler) {
        exceptionHandlers.add(handler);
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public void removeExceptionHandler(ExceptionHandler handler) {
        exceptionHandlers.remove(handler);
    }

    /**
     * {@inheritDoc}
     */
    @Override
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
    @Override
    public abstract void start() throws IOException;
    
    /**
     * Stops the transport and closes all the connections
     * 
     * @throws java.io.IOException
     */
    @Override
    public abstract void stop() throws IOException;
    
    /**
     * Pauses the transport
     * 
     * @throws java.io.IOException
     */
    @Override
    public abstract void pause() throws IOException;
    
    /**
     * Resumes the transport after a pause
     * 
     * @throws java.io.IOException
     */
    @Override
    public abstract void resume() throws IOException;
}
