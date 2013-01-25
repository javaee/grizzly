/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011-2013 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.nio;

import java.nio.channels.spi.SelectorProvider;
import org.glassfish.grizzly.IOStrategy;
import org.glassfish.grizzly.Processor;
import org.glassfish.grizzly.Transport;
import org.glassfish.grizzly.attributes.AttributeBuilder;
import org.glassfish.grizzly.filterchain.FilterChain;
import org.glassfish.grizzly.filterchain.FilterChainBuilder;
import org.glassfish.grizzly.filterchain.TransportFilter;
import org.glassfish.grizzly.memory.MemoryManager;
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
public abstract class NIOTransportBuilder<T extends NIOTransportBuilder> {

    /**
     * The {@link NIOTransport} implementation.
     */
    protected NIOTransport transport;


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
     * @param strategy the {@link IOStrategy}.
     */
    protected NIOTransportBuilder(final Class<? extends NIOTransport> transportClass,
                                  final IOStrategy strategy)
    throws IllegalAccessException, InstantiationException {

        transport = transportClass.newInstance();
        final ThreadPoolConfig workerConfig = strategy.createDefaultWorkerPoolConfig(transport);
        final ThreadPoolConfig selectorConfig = configSelectorPool((workerConfig != null)
                                                   ? workerConfig.copy()
                                                   : ThreadPoolConfig.newConfig());
        transport.setSelectorHandler(SelectorHandler.DEFAULT_SELECTOR_HANDLER);
        transport.setMemoryManager(MemoryManager.DEFAULT_MEMORY_MANAGER);
        transport.setAttributeBuilder(AttributeBuilder.DEFAULT_ATTRIBUTE_BUILDER);
        transport.setIOStrategy(strategy);
//        transport.setReadBufferSize(Transport.DEFAULT_READ_BUFFER_SIZE);
        transport.setWorkerThreadPoolConfig(workerConfig);
        transport.setKernelThreadPoolConfig(selectorConfig);
        transport.setSelectorRunnersCount(selectorConfig.getMaxPoolSize());
        // this block is for compatibility
        final FilterChain chain = FilterChainBuilder.stateless().add(new TransportFilter()).build();
        transport.setFilterChain(chain);

    }

    // ---------------------------------------------------------- Public Methods


    /**
     * @return the {@link ThreadPoolConfig} that will be used to construct the
     *  {@link java.util.concurrent.ExecutorService} for <code>IOStrategies</code>
     *  that require worker threads.  Depending on the {@link IOStrategy} being
     *  used, this may return <code>null</code>.
     */
    public ThreadPoolConfig getWorkerThreadPoolConfig() {
        return transport.getWorkerThreadPoolConfig();
    }

    /**
     * @return the {@link ThreadPoolConfig} that will be used to construct the
     *  {@link java.util.concurrent.ExecutorService} which will run the {@link NIOTransport}'s
     *  {@link org.glassfish.grizzly.nio.SelectorRunner}s.
     */
    public ThreadPoolConfig getSelectorThreadPoolConfig() {
        return transport.getKernelThreadPoolConfig();
    }

    /**
     * @return the {@link IOStrategy} that will be used by the created {@link NIOTransport}.
     */
    public IOStrategy getIOStrategy() {
        return transport.getIOStrategy();
    }

    /**
     * <p>
     * Changes the {@link IOStrategy} that will be used.  Invoking this method
     * may change the return value of {@link #getWorkerThreadPoolConfig()}
     *
     * @param strategy the {@link IOStrategy} to use.
     *
     * @return this <code>NIOTransportBuilder</code>
     */
    public T setIOStrategy(final IOStrategy strategy) {
        transport.setIOStrategy(strategy);
        return getThis();
    }

    /**
     * @return the {@link MemoryManager} that will be used by the created {@link NIOTransport}.
     *  If not explicitly set, then {@link MemoryManager#DEFAULT_MEMORY_MANAGER} will be used.
     */
    public MemoryManager getMemoryManager() {
        return transport.getMemoryManager();
    }

    /**
     * Set the {@link MemoryManager} to be used by the created {@link NIOTransport}.
     *
     * @param memoryManager the {@link MemoryManager}.
     *
     * @return this <code>NIOTransportBuilder</code>
     */
    public T setMemoryManager(final MemoryManager memoryManager) {
        transport.setMemoryManager(memoryManager);
        return getThis();
    }

    /**
     * @return the {@link SelectorHandler} that will be used by the created {@link NIOTransport}.
     *  If not explicitly set, then {@link SelectorHandler#DEFAULT_SELECTOR_HANDLER} will be used.
     */
    public SelectorHandler getSelectorHandler() {
        return transport.getSelectorHandler();
    }

    /**
     * Set the {@link SelectorHandler} to be used by the created {@link NIOTransport}.
     *
     * @param selectorHandler the {@link SelectorHandler}.
     *
     * @return this <code>NIOTransportBuilder</code>
     */
    public T setSelectorHandler(final SelectorHandler selectorHandler) {
        transport.setSelectorHandler(selectorHandler);
        return getThis();
    }

    /**
     * @return the {@link AttributeBuilder} that will be used by the created {@link NIOTransport}.
     *  If not explicitly set, then {@link AttributeBuilder#DEFAULT_ATTRIBUTE_BUILDER} will be used.
     */
    public AttributeBuilder getAttributeBuilder() {
        return transport.getAttributeBuilder();
    }

    /**
     * Set the {@link AttributeBuilder} to be used by the created {@link NIOTransport}.
     *
     * @param attributeBuilder the {@link AttributeBuilder}.
     *
     * @return this <code>NIOTransportBuilder</code>
     */
    public T setAttributeBuilder(AttributeBuilder attributeBuilder) {
        transport.setAttributeBuilder(attributeBuilder);
        return getThis();
    }

    /**
     * @return the {@link NIOChannelDistributor} that will be used by the created {@link NIOTransport}.
     *  If not explicitly set, then {@link AttributeBuilder#DEFAULT_ATTRIBUTE_BUILDER} will be used.
     */
    public NIOChannelDistributor getNIOChannelDistributor() {
        return transport.getNIOChannelDistributor();
    }

    /**
     * Set the {@link NIOChannelDistributor} to be used by the created {@link NIOTransport}.
     *
     * @param nioChannelDistributor the {@link NIOChannelDistributor}.
     *
     * @return this <code>NIOTransportBuilder</code>
     */
    public T setNIOChannelDistributor(NIOChannelDistributor nioChannelDistributor) {
        transport.setNIOChannelDistributor(nioChannelDistributor);
        return getThis();
    }

    /**
     * @return the {@link SelectorProvider} that will be used by the created {@link NIOTransport}.
     *  If not explicitly set, then {@link SelectorProvider#provider()} will be used.
     */
    public SelectorProvider getSelectorProvider() {
        return transport.getSelectorProvider();
    }

    /**
     * Set the {@link SelectorProvider} to be used by the created {@link NIOTransport}.
     *
     * @param selectorProvider the {@link SelectorProvider}.
     *
     * @return this <code>NIOTransportBuilder</code>
     */
    public T setSelectorProvider(SelectorProvider selectorProvider) {
        transport.setSelectorProvider(selectorProvider);
        return getThis();
    }

    
    /**
     * @see Transport#getName()
     */
    public String getName() {
        return transport.getName();
    }

    /**
     * @see Transport#setName(String)
     *
     * @return this <code>NIOTransportBuilder</code>
     */
    public T setName(String name) {
        transport.setName(name);
        return getThis();
    }

    /**
     * @see Transport#getFilterChain()
     */
    public FilterChain getFilterChain() {
        return transport.getFilterChain();
    }

    /**
     * @see Transport#setFilterChain(FilterChain)
     *
     * @return this <code>NIOTransportBuilder</code>
     */
    public T setFilterChain(FilterChain filterChain) {
        transport.setFilterChain(filterChain);
        return getThis();
    }

    /**
     * @see Transport#getReadBufferSize() ()
     */
    public int getReadBufferSize() {
        return transport.getReadBufferSize();
    }

    /**
     * @see Transport#setReadBufferSize(int)
     *
     * @return this <code>NIOTransportBuilder</code>
     */
    public T setReadBufferSize(int readBufferSize) {
        transport.setReadBufferSize(readBufferSize);
        return getThis();
    }

    /**
     * @see Transport#getWriteBufferSize()
     */
    public int getWriteBufferSize() {
        return transport.getWriteBufferSize();
    }

    /**
     * @see Transport#setWriteBufferSize(int)
     *
     * @return this <code>NIOTransportBuilder</code>
     */
    public T setWriteBufferSize(int writeBufferSize) {
        transport.setWriteBufferSize(writeBufferSize);
        return getThis();
    }


    /**
     * @see NIOTransport#isOptimizedForMultiplexing()
     */
    public boolean isOptimizedForMultiplexing() {
        return transport.isOptimizedForMultiplexing();
    }

    /**
     * @see NIOTransport#setOptimizedForMultiplexing(boolean)
     *
     * @return this <code>NIOTransportBuilder</code>
     */
    public T setOptimizedForMultiplexing(
            final boolean isOptimizedForMultiplexing) {
        transport.setOptimizedForMultiplexing(isOptimizedForMultiplexing);
        return getThis();
    }
    

    /**
     * @see AsyncQueueWriter#getMaxPendingBytesPerConnection()
     * 
     * Note: the value is per connection, not transport total.
     */
    public int getMaxAsyncWriteQueueSizeInBytes() {
        return transport.getAsyncQueueWriter()
                .getMaxPendingBytesPerConnection();
    }
    
    /**
     * @see AsyncQueueWriter#setMaxPendingBytesPerConnection(int)
     * 
     * Note: the value is per connection, not transport total.
     *
     * @return this <code>NIOTransportBuilder</code>
     */
    public T setMaxAsyncWriteQueueSizeInBytes(
            final int size) {
        transport.getAsyncQueueWriter().setMaxPendingBytesPerConnection(size);
        return getThis();
    }
    
    /**
     * @return an {@link NIOTransport} based on the builder's configuration.
     */
    public NIOTransport build() {
        return transport;
    }


    // ------------------------------------------------------- Protected Methods


    /**
     * <p>
     * Configure the {@link org.glassfish.grizzly.nio.SelectorRunner} pool's
     * default core and max pool size.
     * </p>
     * @param config
     */
    protected ThreadPoolConfig configSelectorPool(final ThreadPoolConfig config) {
        final int runnerCount = getRunnerCount();
        config.setPoolName("Grizzly-kernel");
        return config.setCorePoolSize(runnerCount).setMaxPoolSize(runnerCount);
    }

    /**
     * See: <a href="http://www.angelikalanger.com/GenericsFAQ/FAQSections/ProgrammingIdioms.html#FAQ205">http://www.angelikalanger.com/GenericsFAQ/FAQSections/ProgrammingIdioms.html#FAQ205</a>
     */
    protected abstract T getThis();


    // --------------------------------------------------------- Private Methods


    /**
     * @return the default number of {@link org.glassfish.grizzly.nio.SelectorRunner}s
     *  that should be used.
     */
    private int getRunnerCount() {
        return Runtime.getRuntime().availableProcessors();
    }

}
