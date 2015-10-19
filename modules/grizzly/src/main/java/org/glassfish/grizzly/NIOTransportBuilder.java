/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011-2015 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly;

import java.nio.channels.spi.SelectorProvider;
import java.util.concurrent.TimeUnit;

import org.glassfish.grizzly.asyncqueue.AsyncQueueWriter;
import org.glassfish.grizzly.attributes.AttributeBuilder;
import org.glassfish.grizzly.memory.MemoryManager;
import org.glassfish.grizzly.nio.NIOChannelDistributor;
import org.glassfish.grizzly.nio.NIOTransport;
import org.glassfish.grizzly.nio.SelectionKeyHandler;
import org.glassfish.grizzly.nio.SelectorHandler;
import org.glassfish.grizzly.strategies.WorkerThreadIOStrategy;
import org.glassfish.grizzly.threadpool.ThreadPoolConfig;

/**
 * This builder is responsible for creating {@link NIOTransport} implementations
 * as well as providing basic configuration for <code>IOStrategies</code> and
 * thread pools.
 *
 * @see NIOTransport
 * @see IOStrategy
 * @see ThreadPoolConfig
 *
 * @since 2.0
 */
@SuppressWarnings("UnusedDeclaration")
public abstract class NIOTransportBuilder<T extends NIOTransportBuilder> {

    protected final Class<? extends NIOTransport> transportClass;
    protected ThreadPoolConfig workerConfig;
    protected ThreadPoolConfig kernelConfig;
    protected SelectorProvider selectorProvider;
    protected SelectorHandler selectorHandler =
            SelectorHandler.DEFAULT_SELECTOR_HANDLER;
    protected SelectionKeyHandler selectionKeyHandler =
            SelectionKeyHandler.DEFAULT_SELECTION_KEY_HANDLER;
    protected MemoryManager memoryManager =
            MemoryManager.DEFAULT_MEMORY_MANAGER;
    protected AttributeBuilder attributeBuilder =
            AttributeBuilder.DEFAULT_ATTRIBUTE_BUILDER;
    protected IOStrategy ioStrategy = WorkerThreadIOStrategy.getInstance();
    protected int selectorRunnerCount = NIOTransport.DEFAULT_SELECTOR_RUNNER_COUNT;
    protected NIOChannelDistributor nioChannelDistributor;
    protected String name;
    protected Processor processor;
    protected ProcessorSelector processorSelector;
    protected int readBufferSize = Transport.DEFAULT_READ_BUFFER_SIZE;
    protected int writeBufferSize = Transport.DEFAULT_WRITE_BUFFER_SIZE;
    protected int clientSocketSoTimeout = NIOTransport.DEFAULT_CLIENT_SOCKET_SO_TIMEOUT;
    protected int connectionTimeout = NIOTransport.DEFAULT_CONNECTION_TIMEOUT;
    protected boolean reuseAddress = NIOTransport.DEFAULT_REUSE_ADDRESS;
    protected int maxPendingBytesPerConnection = AsyncQueueWriter.AUTO_SIZE;
    protected boolean optimizedForMultiplexing = NIOTransport.DEFAULT_OPTIMIZED_FOR_MULTIPLEXING;

    protected long readTimeout = TimeUnit.MILLISECONDS.convert(Transport.DEFAULT_READ_TIMEOUT, TimeUnit.SECONDS);
    protected long writeTimeout = TimeUnit.MILLISECONDS.convert(Transport.DEFAULT_WRITE_TIMEOUT, TimeUnit.SECONDS);

    // ------------------------------------------------------------ Constructors


    /**
     * <p>
     * Constructs a new <code>NIOTransport</code> using the given
     * <code>transportClass</code> and {@link IOStrategy}.
     * </p>
     *
     * <p>
     * The builder's worker thread pool configuration will be based on the return
     * value of {@link IOStrategy#createDefaultWorkerPoolConfig(Transport)}.
     * If worker thread configuration is non-null, the initial selector thread pool
     * configuration will be cloned from it, otherwise a default configuration
     * will be chosen.
     * </p>
     *
     * @param transportClass the class of the {@link NIOTransport}
     *  implementation to be used.
     */
    protected NIOTransportBuilder(final Class<? extends NIOTransport> transportClass) {

        this.transportClass = transportClass;

    }

    // ---------------------------------------------------------- Public Methods

    /**
     * @return the number of {@link Selector}s to be created to serve Transport
     * connections. <tt>-1</tt> is the default value, which lets the Transport
     * to pick the value, usually it's equal to the number of CPU cores
     * {@link Runtime#availableProcessors()}
     */
    public int getSelectorRunnersCount() {
        return selectorRunnerCount;
    }

    /**
     * Sets the number of {@link Selector}s to be created to serve Transport
     * connections. <tt>-1</tt> is the default value, which lets the Transport
     * to pick the value, usually it's equal to the number of CPU cores
     * {@link Runtime#availableProcessors()}.
     * 
     * @param selectorRunnersCount 
     * @return the builder
     */
    public T setSelectorRunnersCount(final int selectorRunnersCount) {
        this.selectorRunnerCount = selectorRunnersCount;
        return getThis();
    }

    /**
     * @return the {@link ThreadPoolConfig} that will be used to construct the
     *  {@link java.util.concurrent.ExecutorService} for <code>IOStrategies</code>
     *  that require worker threads.  This method will return <code>null</code>
     *  if a {@link ThreadPoolConfig} had not been previously set.
     */
    public ThreadPoolConfig getWorkerThreadPoolConfig() {
        return workerConfig;
    }

    /**
     * Sets the {@link ThreadPoolConfig} that will be used to construct the
     *  {@link java.util.concurrent.ExecutorService} for <code>IOStrategies</code>
     *  that require worker threads
     */
    public T setWorkerThreadPoolConfig(final ThreadPoolConfig workerConfig) {
        this.workerConfig = workerConfig;
        return getThis();
    }
    
    /**
     * @return the {@link ThreadPoolConfig} that will be used to construct the
     *  {@link java.util.concurrent.ExecutorService} which will run the {@link NIOTransport}'s
     *  {@link org.glassfish.grizzly.nio.SelectorRunner}s.
     */
    public ThreadPoolConfig getSelectorThreadPoolConfig() {
        return kernelConfig;
    }

    /**
     * Sets the {@link ThreadPoolConfig} that will be used to construct the
     *  {@link java.util.concurrent.ExecutorService} which will run the {@link NIOTransport}'s
     *  {@link org.glassfish.grizzly.nio.SelectorRunner}s.
     */
    public T setSelectorThreadPoolConfig(final ThreadPoolConfig kernelConfig) {
        this.kernelConfig = kernelConfig;
        return getThis();
    }

    /**
     * @return the {@link IOStrategy} that will be used by the created {@link NIOTransport}.
     */
    public IOStrategy getIOStrategy() {
        return ioStrategy;
    }

    /**
     * <p>
     * Changes the {@link IOStrategy} that will be used.  Invoking this method
     * may change the return value of {@link #getWorkerThreadPoolConfig()}
     *
     * @param ioStrategy the {@link IOStrategy} to use.
     *
     * @return this <code>NIOTransportBuilder</code>
     */
    public T setIOStrategy(final IOStrategy ioStrategy) {
        this.ioStrategy = ioStrategy;
        return getThis();
    }

    /**
     * @return the {@link MemoryManager} that will be used by the created {@link NIOTransport}.
     *  If not explicitly set, then {@link MemoryManager#DEFAULT_MEMORY_MANAGER} will be used.
     */
    public MemoryManager getMemoryManager() {
        return memoryManager;
    }

    /**
     * Set the {@link MemoryManager} to be used by the created {@link NIOTransport}.
     *
     * @param memoryManager the {@link MemoryManager}.
     *
     * @return this <code>NIOTransportBuilder</code>
     */
    public T setMemoryManager(final MemoryManager memoryManager) {
        this.memoryManager = memoryManager;
        return getThis();
    }

    /**
     * @return the {@link SelectorHandler} that will be used by the created {@link NIOTransport}.
     *  If not explicitly set, then {@link SelectorHandler#DEFAULT_SELECTOR_HANDLER} will be used.
     */
    public SelectorHandler getSelectorHandler() {
        return selectorHandler;
    }

    /**
     * Set the {@link SelectorHandler} to be used by the created {@link NIOTransport}.
     *
     * @param selectorHandler the {@link SelectorHandler}.
     *
     * @return this <code>NIOTransportBuilder</code>
     */
    public T setSelectorHandler(final SelectorHandler selectorHandler) {
        this.selectorHandler = selectorHandler;
        return getThis();
    }

    /**
     * @return the {@link SelectionKeyHandler} that will be used by the created {@link NIOTransport}.
     *  If not explicitly set, then {@link SelectionKeyHandler#DEFAULT_SELECTION_KEY_HANDLER} will be used.
     */
    public SelectionKeyHandler getSelectionKeyHandler() {
        return selectionKeyHandler;
    }

    /**
     * Set the {@link SelectionKeyHandler} to be used by the created {@link NIOTransport}.
     *
     * @param selectionKeyHandler the {@link SelectionKeyHandler}.
     *
     * @return this <code>NIOTransportBuilder</code>
     */
    public T setSelectionKeyHandler(final SelectionKeyHandler selectionKeyHandler) {
        this.selectionKeyHandler = selectionKeyHandler;
        return getThis();
    }

    /**
     * @return the {@link AttributeBuilder} that will be used by the created {@link NIOTransport}.
     *  If not explicitly set, then {@link AttributeBuilder#DEFAULT_ATTRIBUTE_BUILDER} will be used.
     */
    public AttributeBuilder getAttributeBuilder() {
        return attributeBuilder;
    }

    /**
     * Set the {@link AttributeBuilder} to be used by the created {@link NIOTransport}.
     *
     * @param attributeBuilder the {@link AttributeBuilder}.
     *
     * @return this <code>NIOTransportBuilder</code>
     */
    public T setAttributeBuilder(AttributeBuilder attributeBuilder) {
        this.attributeBuilder = attributeBuilder;
        return getThis();
    }

    /**
     * @return the {@link NIOChannelDistributor} that will be used by the created {@link NIOTransport}.
     *  If not explicitly set, then {@link AttributeBuilder#DEFAULT_ATTRIBUTE_BUILDER} will be used.
     */
    public NIOChannelDistributor getNIOChannelDistributor() {
        return nioChannelDistributor;
    }

    /**
     * Set the {@link NIOChannelDistributor} to be used by the created {@link NIOTransport}.
     *
     * @param nioChannelDistributor the {@link NIOChannelDistributor}.
     *
     * @return this <code>NIOTransportBuilder</code>
     */
    public T setNIOChannelDistributor(NIOChannelDistributor nioChannelDistributor) {
        this.nioChannelDistributor = nioChannelDistributor;
        return getThis();
    }

    /**
     * @return the {@link SelectorProvider} that will be used by the created {@link NIOTransport}.
     *  If not explicitly set, then {@link SelectorProvider#provider()} will be used.
     */
    public SelectorProvider getSelectorProvider() {
        return selectorProvider;
    }

    /**
     * Set the {@link SelectorProvider} to be used by the created {@link NIOTransport}.
     *
     * @param selectorProvider the {@link SelectorProvider}.
     *
     * @return this <code>NIOTransportBuilder</code>
     */
    public T setSelectorProvider(SelectorProvider selectorProvider) {
        this.selectorProvider = selectorProvider;
        return getThis();
    }

    
    /**
     * @see Transport#getName()
     */
    public String getName() {
        return name;
    }

    /**
     * @see Transport#setName(String)
     *
     * @return this <code>NIOTransportBuilder</code>
     */
    public T setName(String name) {
        this.name = name;
        return getThis();
    }

    /**
     * @see Transport#getProcessor()
     */
    public Processor getProcessor() {
        return processor;
    }

    /**
     * @see Transport#setProcessor(Processor)
     *
     * @return this <code>NIOTransportBuilder</code>
     */
    public T setProcessor(Processor processor) {
        this.processor = processor;
        return getThis();
    }

    /**
     * @see Transport#getProcessorSelector() ()
     */
    public ProcessorSelector getProcessorSelector() {
        return processorSelector;
    }

    /**
     * @see Transport#setProcessorSelector(ProcessorSelector)
     *
     * @return this <code>NIOTransportBuilder</code>
     */
    public T setProcessorSelector(ProcessorSelector processorSelector) {
        this.processorSelector = processorSelector;
        return getThis();
    }

    /**
     * @see Transport#getReadBufferSize() ()
     */
    public int getReadBufferSize() {
        return readBufferSize;
    }

    /**
     * @see Transport#setReadBufferSize(int)
     *
     * @return this <code>NIOTransportBuilder</code>
     */
    public T setReadBufferSize(int readBufferSize) {
        this.readBufferSize = readBufferSize;
        return getThis();
    }

    /**
     * @see Transport#getWriteBufferSize()
     */
    public int getWriteBufferSize() {
        return writeBufferSize;
    }

    /**
     * @see Transport#setWriteBufferSize(int)
     *
     * @return this <code>NIOTransportBuilder</code>
     */
    public T setWriteBufferSize(int writeBufferSize) {
        this.writeBufferSize = writeBufferSize;
        return getThis();
    }

    /**
     * @see NIOTransport#getClientSocketSoTimeout()
     */
    public int getClientSocketSoTimeout() {
        return clientSocketSoTimeout;
    }

    /**
     * @return this <code>NIOTransportBuilder</code>
     * @see NIOTransport#setClientSocketSoTimeout(int)
     */
    public T setClientSocketSoTimeout(int clientSocketSoTimeout) {
        this.clientSocketSoTimeout = clientSocketSoTimeout;
        return getThis();
    }

    /**
     * @see NIOTransport#getConnectionTimeout()
     */
    public int getConnectionTimeout() {
        return connectionTimeout;
    }

    /**
     * @return this <code>NIOTransportBuilder</code>
     * @see NIOTransport#setConnectionTimeout(int)
     */
    public T setConnectionTimeout(int connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
        return getThis();
    }

    /**
     * @see Transport#getReadTimeout(java.util.concurrent.TimeUnit)
     */
    public long getReadTimeout(final TimeUnit timeUnit) {
        if (readTimeout <= 0) {
            return -1;
        } else {
            return timeUnit.convert(readTimeout, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * @see Transport#setReadTimeout(long, java.util.concurrent.TimeUnit)
     */
    public T setReadTimeout(final long timeout, final TimeUnit timeUnit) {
        if (timeout <= 0) {
            readTimeout = -1;
        } else {
            readTimeout = TimeUnit.MILLISECONDS.convert(timeout, timeUnit);
        }
        return getThis();
    }

    /**
     * @see Transport#getWriteTimeout(java.util.concurrent.TimeUnit)
     */
    public long getWriteTimeout(final TimeUnit timeUnit) {
        if (writeTimeout <= 0) {
            return -1;
        } else {
            return timeUnit.convert(writeTimeout, TimeUnit.MILLISECONDS);
        }        
    }

    /**
     * @see Transport#setWriteTimeout(long, java.util.concurrent.TimeUnit)
     */
    public T setWriteTimeout(final long timeout, final TimeUnit timeUnit) {
        if (timeout <= 0) {
            writeTimeout = -1;
        } else {
            writeTimeout = TimeUnit.MILLISECONDS.convert(timeout, timeUnit);
        }
        return getThis();
    }


    /**
     * @see org.glassfish.grizzly.nio.transport.TCPNIOTransport#isReuseAddress()
     */
    public boolean isReuseAddress() {
        return reuseAddress;
    }

    /**
     * @return this <code>TCPNIOTransportBuilder</code>
     * @see org.glassfish.grizzly.nio.transport.TCPNIOTransport#setReuseAddress(boolean)
     */
    public T setReuseAddress(boolean reuseAddress) {
        this.reuseAddress = reuseAddress;
        return getThis();
    }

    /**
     * @see org.glassfish.grizzly.asyncqueue.AsyncQueueWriter#getMaxPendingBytesPerConnection()
     * <p/>
     * Note: the value is per connection, not transport total.
     */
    public int getMaxAsyncWriteQueueSizeInBytes() {
        return maxPendingBytesPerConnection;
    }

    /**
     * @return this <code>TCPNIOTransportBuilder</code>
     * @see org.glassfish.grizzly.asyncqueue.AsyncQueueWriter#setMaxPendingBytesPerConnection(int)
     * <p/>
     * Note: the value is per connection, not transport total.
     */
    public T setMaxAsyncWriteQueueSizeInBytes(final int maxAsyncWriteQueueSizeInBytes) {
        this.maxPendingBytesPerConnection = maxAsyncWriteQueueSizeInBytes;
        return getThis();
    }

    /**
     * @see org.glassfish.grizzly.nio.NIOTransport#isOptimizedForMultiplexing()
     */
    public boolean isOptimizedForMultiplexing() {
        return optimizedForMultiplexing;
    }

    /**
     * @see org.glassfish.grizzly.nio.NIOTransport#setOptimizedForMultiplexing(boolean)
     *
     * @return this <code>TCPNIOTransportBuilder</code>
     */
    public T setOptimizedForMultiplexing(final boolean optimizedForMultiplexing) {
        this.optimizedForMultiplexing = optimizedForMultiplexing;
        return getThis();
    }

    /**
     * @return an {@link NIOTransport} based on the builder's configuration.
     */
    public NIOTransport build() {
        NIOTransport transport = create(name);
        transport.setIOStrategy(ioStrategy);
        if (workerConfig != null) {
            transport.setWorkerThreadPoolConfig(workerConfig.copy());
        }
        if (kernelConfig != null) {
            transport.setKernelThreadPoolConfig(kernelConfig.copy());
        }
        transport.setSelectorProvider(selectorProvider);
        transport.setSelectorHandler(selectorHandler);
        transport.setSelectionKeyHandler(selectionKeyHandler);
        transport.setMemoryManager(memoryManager);
        transport.setAttributeBuilder(attributeBuilder);
        transport.setSelectorRunnersCount(selectorRunnerCount);
        transport.setNIOChannelDistributor(nioChannelDistributor);
        transport.setProcessor(processor);
        transport.setProcessorSelector(processorSelector);
        transport.setClientSocketSoTimeout(clientSocketSoTimeout);
        transport.setConnectionTimeout(connectionTimeout);
        transport.setReadTimeout(readTimeout, TimeUnit.MILLISECONDS);
        transport.setWriteTimeout(writeTimeout, TimeUnit.MILLISECONDS);
        transport.setReadBufferSize(readBufferSize);
        transport.setWriteBufferSize(writeBufferSize);
        transport.setReuseAddress(reuseAddress);
        transport.setOptimizedForMultiplexing(isOptimizedForMultiplexing());
        transport.getAsyncQueueIO()
                .getWriter()
                .setMaxPendingBytesPerConnection(
                        maxPendingBytesPerConnection);
        return transport;
    }


    // ------------------------------------------------------- Protected Methods

    /**
     * See: <a href="http://www.angelikalanger.com/GenericsFAQ/FAQSections/ProgrammingIdioms.html#FAQ205">http://www.angelikalanger.com/GenericsFAQ/FAQSections/ProgrammingIdioms.html#FAQ205</a>
     */
    protected abstract T getThis();

    protected abstract NIOTransport create(String name);
}
