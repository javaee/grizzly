/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011 Oracle and/or its affiliates. All rights reserved.
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

import org.glassfish.grizzly.attributes.AttributeBuilder;
import org.glassfish.grizzly.memory.HeapMemoryManager;
import org.glassfish.grizzly.memory.MemoryManager;
import org.glassfish.grizzly.aio.AIOTransport;
import org.glassfish.grizzly.threadpool.ThreadPoolConfig;

/**
 * This builder is responsible for creating {@link AIOTransport} implementations
 * as well as providing basic configuration for <code>IOStrategies</code> and
 * thread pools.
 *
 * @see AIOTransport
 * @see IOStrategy
 * @see ThreadPoolConfig
 *
 * @since 2.0
 */
public abstract class AIOTransportBuilder<T extends AIOTransportBuilder> {


    /**
     * <p>
     * The default {@link MemoryManager} implementation used by all created builder
     * instances.
     * </p>
     *
     * <p>
     * This may be updated with an alternate {@link MemoryManager} implementation
     * if so desired.
     * </p>
     */
    public static MemoryManager DEFAULT_MEMORY_MANAGER =
            new HeapMemoryManager();

    /**
     * <p>
     * The default {@link AttributeBuilder} implementation used by all created builder
     * instances.
     * </p>
     *
     * <p>
     * This may be updated with an alternate {@link AttributeBuilder} implementation
     * if so desired.
     * </p>
     */
    public static AttributeBuilder DEFAULT_ATTRIBUTE_BUILDER =
            AttributeBuilder.DEFAULT_ATTRIBUTE_BUILDER;

    /**
     * The {@link AIOTransport} implementation.
     */
    protected AIOTransport transport;


    // ------------------------------------------------------------ Constructors


    /**
     * <p>
     * Constructs a new <code>AIOTransport</code> using the given
     * <code>transportClass</code> and {@link IOStrategy}.
     * </p>
     *
     * <p>
     * The builder's worker thread pool configuration will be based on the return
     * value of {@link IOStrategy#createDefaultWorkerPoolConfig(org.glassfish.grizzly.aio.AIOTransport)}.
     * If worker thread configuration is non-null, the initial kernel thread pool
     * configuration will be cloned from it, otherwise a default configuration
     * will be chosen.
     * </p>
     *
     * @param transportClass the class of the {@link AIOTransport}
     *  implementation to be used.
     * @param strategy the {@link IOStrategy}.
     */
    protected AIOTransportBuilder(final Class<? extends AIOTransport> transportClass,
                                  final IOStrategy strategy)
    throws IllegalAccessException, InstantiationException {

        transport = transportClass.newInstance();
        final ThreadPoolConfig workerConfig = strategy.createDefaultWorkerPoolConfig(transport);
        final ThreadPoolConfig kernelConfig = configKernelPool((workerConfig != null)
                                                   ? workerConfig.clone()
                                                   : ThreadPoolConfig.defaultConfig().clone());
        transport.setMemoryManager(DEFAULT_MEMORY_MANAGER);
        transport.setAttributeBuilder(DEFAULT_ATTRIBUTE_BUILDER);
        transport.setIOStrategy(strategy);
        transport.setWorkerThreadPoolConfig(workerConfig);
        transport.setKernelThreadPoolConfig(kernelConfig);

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
     *  {@link java.util.concurrent.ExecutorService} which will run the {@link AIOTransport}'s
     *  {@link org.glassfish.grizzly.nio.SelectorRunner}s.
     */
    public ThreadPoolConfig getKernelThreadPoolConfig() {
        return transport.getKernelThreadPoolConfig();
    }

    /**
     * @return the {@link IOStrategy} that will be used by the created {@link AIOTransport}.
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
     * @return this <code>AIOTransportBuilder</code>
     */
    public T setIOStrategy(final IOStrategy strategy) {
        transport.setIOStrategy(strategy);
        transport.setWorkerThreadPoolConfig(strategy.createDefaultWorkerPoolConfig(transport));
        return getThis();
    }

    /**
     * @return the {@link MemoryManager} that will be used by the created {@link AIOTransport}.
     *  If not explicitly set, then {@link #DEFAULT_MEMORY_MANAGER} will be used.
     */
    public MemoryManager getMemoryManager() {
        return transport.getMemoryManager();
    }

    /**
     * Set the {@link MemoryManager} to be used by the created {@link AIOTransport}.
     *
     * @param memoryManager the {@link MemoryManager}.
     *
     * @return this <code>AIOTransportBuilder</code>
     */
    public T setMemoryManager(final MemoryManager memoryManager) {
        transport.setMemoryManager(memoryManager);
        return getThis();
    }

    /**
     * @return the {@link AttributeBuilder} that will be used by the created {@link AIOTransport}.
     *  If not explicitly set, then {@link #DEFAULT_ATTRIBUTE_BUILDER} will be used.
     */
    public AttributeBuilder getAttributeBuilder() {
        return transport.getAttributeBuilder();
    }

    /**
     * Set the {@link AttributeBuilder} to be used by the created {@link AIOTransport}.
     *
     * @param attributeBuilder the {@link AttributeBuilder}.
     *
     * @return this <code>AIOTransportBuilder</code>
     */
    public T setAttributeBuilder(AttributeBuilder attributeBuilder) {
        transport.setAttributeBuilder(attributeBuilder);
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
     * @return this <code>AIOTransportBuilder</code>
     */
    public T setName(String name) {
        transport.setName(name);
        return getThis();
    }

    /**
     * @see Transport#getProcessor()
     */
    public Processor getProcessor() {
        return transport.getProcessor();
    }

    /**
     * @see Transport#setProcessor(Processor)
     *
     * @return this <code>AIOTransportBuilder</code>
     */
    public T setProcessor(Processor processor) {
        transport.setProcessor(processor);
        return getThis();
    }

    /**
     * @see Transport#getProcessorSelector() ()
     */
    public ProcessorSelector getProcessorSelector() {
        return transport.getProcessorSelector();
    }

    /**
     * @see Transport#setProcessorSelector(ProcessorSelector)
     *
     * @return this <code>AIOTransportBuilder</code>
     */
    public T setProcessorSelector(ProcessorSelector processorSelector) {
        transport.setProcessorSelector(processorSelector);
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
     * @return this <code>AIOTransportBuilder</code>
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
     * @return this <code>AIOTransportBuilder</code>
     */
    public T setWriteBufferSize(int writeBufferSize) {
        transport.setWriteBufferSize(writeBufferSize);
        return getThis();
    }

    /**
     * @return an {@link AIOTransport} based on the builder's configuration.
     */
    public AIOTransport build() {
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
    protected ThreadPoolConfig configKernelPool(final ThreadPoolConfig config) {
        final int runnerCount = getRunnerCount();
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
        return Math.max(1, Runtime.getRuntime().availableProcessors());
    }

}
