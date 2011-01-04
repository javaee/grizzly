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

import java.util.concurrent.Executor;
import org.glassfish.grizzly.attributes.AttributeBuilder;
import org.glassfish.grizzly.attributes.DefaultAttributeBuilder;
import org.glassfish.grizzly.memory.HeapMemoryManager;
import org.glassfish.grizzly.memory.MemoryManager;
import org.glassfish.grizzly.nio.DefaultSelectionKeyHandler;
import org.glassfish.grizzly.nio.DefaultSelectorHandler;
import org.glassfish.grizzly.nio.NIOTransport;
import org.glassfish.grizzly.nio.SelectionKeyHandler;
import org.glassfish.grizzly.nio.SelectorHandler;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import org.glassfish.grizzly.nio.transport.UDPNIOTransport;
import org.glassfish.grizzly.strategies.SameThreadIOStrategy;
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
public class NIOTransportBuilder<T extends NIOTransport> {


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
            new DefaultAttributeBuilder();


    /**
     * The default {@link SelectorHandler} used by all created builder instances.
     */
    private static final SelectorHandler DEFAULT_SELECTOR_HANDLER =
            new DefaultSelectorHandler();

    /**
     * The default {@link SelectionKeyHandler} used by all created builder instances.
     */
    private static final SelectionKeyHandler DEFAULT_SELECTION_KEY_HANDLER =
            new DefaultSelectionKeyHandler();


    /**
     * The {@link IOStrategy} used by this builder instance.
     */
    private IOStrategy strategy;

    /**
     * The {@link NIOTransport} implementation.
     */
    private T transport;

    /**
     * Configuration for the {@link NIOTransport}'s selector thread pool.
     */
    private ThreadPoolConfig selectorConfig;

    /**
     * Configuration for the {@link NIOTransport}'s worker thread pool (dependent on the
     * {@link IOStrategy} being used.
     */
    private ThreadPoolConfig workerConfig;


    // ------------------------------------------------------------ Constructors


    /**
     * <p>
     * Constructs a new <code>NIOTransport</code> using the given
     * <code>transportClass</code> and {@link IOStrategy}.
     * </p>
     *
     * <p>
     * The builder's worker thread pool configuration will be based on the return
     * value of {@link IOStrategy#createDefaultWorkerPoolConfig(org.glassfish.grizzly.nio.NIOTransport)}.
     * If worker thread configuration is non-null, the initial selector thread pool
     * configuration will be cloned from it, otherwise a default configuration
     * will be chosen.
     * </p>
     *
     * @param transportClass the class of the {@link NIOTransport}
     *  implementation to be used.
     * @param strategy the {@link IOStrategy}.
     */
    private NIOTransportBuilder(final Class<? extends NIOTransport> transportClass,
                                final IOStrategy strategy)
    throws IllegalAccessException, InstantiationException {

        transport = (T) transportClass.newInstance();
        workerConfig = strategy.createDefaultWorkerPoolConfig(transport);
        selectorConfig = configSelectorPool((workerConfig != null)
                              ? workerConfig.clone()
                              : ThreadPoolConfig.defaultConfig().clone());
        this.strategy = strategy;
    }


    // ---------------------------------------------------------- Public Methods


    /**
     * <p>
     * Constructs a new <code>NIOTransport</code> using the given
     * <code>transportClass</code> and {@link IOStrategy}.
     * </p>
     *
     * <p>
     * The builder's worker thread pool configuration will be based on the return
     * value of {@link IOStrategy#createDefaultWorkerPoolConfig(org.glassfish.grizzly.nio.NIOTransport)}.
     * If worker thread configuration is non-null, the initial selector thread pool
     * configuration will be cloned from it, otherwise a default configuration
     * will be chosen.
     * </p>
     *
     * @param transportClass the class of the {@link NIOTransport}
     *  implementation to be used.
     * @param strategy the {@link IOStrategy}.
     *
     * @throws IllegalStateException if <code>transportClass</code> cannot be
     *  constructed.
     */
    public static NIOTransportBuilder newInstance(final Class<? extends NIOTransport> transportClass,
                                                  final IOStrategy strategy) {
        try {
            return new NIOTransportBuilder(transportClass, strategy);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }


    /**
     * <p>
     * This method calls {@link #newInstance(Class, IOStrategy)}
     * passing {@link TCPNIOTransport}.class as the <code>transportClass<code>
     * and {@link SameThreadIOStrategy} as the {@link IOStrategy}.
     * </p>
     *
     * @return an <code>NIOTransportBuilder</code> that will build {@link TCPNIOTransport}
     *  instances.
     */
    public static NIOTransportBuilder<TCPNIOTransport> defaultTCPTransportBuilder() {
        return newInstance(TCPNIOTransport.class, new SameThreadIOStrategy());
    }

    /**
     * <p>
     * This method calls {@link #newInstance(Class, IOStrategy)}
     * passing {@link TCPNIOTransport}.class as the <code>transportClass<code>
     * and {@link SameThreadIOStrategy} as the {@link IOStrategy}.
     * </p>
     *
     * @return an <code>NIOTransportBuilder</code> that will build {@link UDPNIOTransport}
     *  instances.
     */
    public static NIOTransportBuilder<UDPNIOTransport> defaultUDPTransportBuilder() {
        return newInstance(UDPNIOTransport.class, new SameThreadIOStrategy());
    }

    /**
     * @return the {@link ThreadPoolConfig} that will be used to construct the
     *  {@link java.util.concurrent.ExecutorService} for <code>IOStrategies</code>
     *  that require worker threads.  Depending on the {@link IOStrategy} being
     *  used, this may return <code>null</code>.
     */
    public ThreadPoolConfig getWorkerThreadPoolConfig() {
        return workerConfig;
    }

    /**
     * @return the {@link ThreadPoolConfig} that will be used to construct the
     *  {@link java.util.concurrent.ExecutorService} which will run the {@link NIOTransport}'s
     *  {@link org.glassfish.grizzly.nio.SelectorRunner}s.
     */
    public ThreadPoolConfig getSelectorThreadPoolConfig() {
        return selectorConfig;
    }

    /**
     * @return the {@link IOStrategy} that will be used by the created {@link NIOTransport}
     */
    public IOStrategy getIOStrategy() {
        return strategy;
    }

    /**
     * <p>
     * Changes the {@link IOStrategy} that will be used.  Invoking this method
     * may change the return value of {@link #getWorkerThreadPoolConfig()}
     *
     * @param strategy the {@link IOStrategy} to use.
     */
    public void setIOStrategy(IOStrategy strategy) {
        this.strategy = strategy;
        this.workerConfig = strategy.createDefaultWorkerPoolConfig((NIOTransport) transport);
    }


    /**
     * @return an {@link NIOTransport} based on the builder's configuration.
     */
    public T build() {
        transport.setMemoryManager(DEFAULT_MEMORY_MANAGER);
        transport.setAttributeBuilder(DEFAULT_ATTRIBUTE_BUILDER);
        transport.setSelectorHandler(DEFAULT_SELECTOR_HANDLER);
        transport.setSelectionKeyHandler(DEFAULT_SELECTION_KEY_HANDLER);
        transport.setWorkerThreadPoolConfig(workerConfig);
        transport.setSelectorRunnerThreadPoolConfig(selectorConfig);
        transport.setSelectorRunnersCount(selectorConfig.getMaxPoolSize());
        transport.setIOStrategy(strategy);
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
        return config.setCorePoolSize(runnerCount).setMaxPoolSize(runnerCount);
    }


    // --------------------------------------------------------- Private Methods


    /**
     * @return the default number of {@link org.glassfish.grizzly.nio.SelectorRunner}s
     *  that should be used.
     */
    private int getRunnerCount() {
        return Math.max(1, Runtime.getRuntime().availableProcessors() / 2 * 3);
    }

}
