/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2014 Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.nio.channels.spi.SelectorProvider;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.glassfish.grizzly.AbstractTransport;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.GracefulShutdownListener;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.GrizzlyFuture;
import org.glassfish.grizzly.SocketBinder;
import org.glassfish.grizzly.SocketConnectorHandler;
import org.glassfish.grizzly.StandaloneProcessor;
import org.glassfish.grizzly.Transport;
import org.glassfish.grizzly.TransportProbe;
import org.glassfish.grizzly.asyncqueue.AsyncQueueEnabledTransport;
import org.glassfish.grizzly.impl.FutureImpl;
import org.glassfish.grizzly.localization.LogMessages;
import org.glassfish.grizzly.nio.tmpselectors.TemporarySelectorIO;
import org.glassfish.grizzly.nio.tmpselectors.TemporarySelectorPool;
import org.glassfish.grizzly.nio.tmpselectors.TemporarySelectorsEnabledTransport;
import org.glassfish.grizzly.strategies.SameThreadIOStrategy;
import org.glassfish.grizzly.strategies.WorkerThreadIOStrategy;
import org.glassfish.grizzly.threadpool.AbstractThreadPool;
import org.glassfish.grizzly.threadpool.GrizzlyExecutorService;
import org.glassfish.grizzly.threadpool.ThreadPoolConfig;
import org.glassfish.grizzly.utils.Futures;

/**
 *
 * @author oleksiys
 */
public abstract class NIOTransport extends AbstractTransport
        implements SocketBinder, SocketConnectorHandler,
        TemporarySelectorsEnabledTransport, AsyncQueueEnabledTransport {

    public static final int DEFAULT_SERVER_SOCKET_SO_TIMEOUT = 0;

    public static final boolean DEFAULT_REUSE_ADDRESS = true;
    public static final int DEFAULT_CLIENT_SOCKET_SO_TIMEOUT = 0;
    public static final int DEFAULT_CONNECTION_TIMEOUT =
            SocketConnectorHandler.DEFAULT_CONNECTION_TIMEOUT;
    public static final int DEFAULT_SELECTOR_RUNNER_COUNT = -1;
    public static final boolean DEFAULT_OPTIMIZED_FOR_MULTIPLEXING = false;

    private static final Logger LOGGER = Grizzly.logger(NIOTransport.class);

    protected SelectorHandler selectorHandler;
    protected SelectionKeyHandler selectionKeyHandler;
    /**
     * The server socket time out
     */
    int serverSocketSoTimeout = DEFAULT_SERVER_SOCKET_SO_TIMEOUT;
    /**
     * The socket reuseAddress
     */
    boolean reuseAddress = DEFAULT_REUSE_ADDRESS;
    /**
     * The socket time out
     */
    int clientSocketSoTimeout = DEFAULT_CLIENT_SOCKET_SO_TIMEOUT;
    /**
     * Default channel connection timeout
     */
    int connectionTimeout = DEFAULT_CONNECTION_TIMEOUT;
    
    protected ChannelConfigurator channelConfigurator;

    private int selectorRunnersCount = DEFAULT_SELECTOR_RUNNER_COUNT;

    private boolean optimizedForMultiplexing = DEFAULT_OPTIMIZED_FOR_MULTIPLEXING;

    protected SelectorRunner[] selectorRunners;
    
    protected NIOChannelDistributor nioChannelDistributor;

    protected SelectorProvider selectorProvider = SelectorProvider.provider();

    protected final TemporarySelectorIO temporarySelectorIO;

    protected Set<GracefulShutdownListener> shutdownListeners;

    /**
     * Future to control graceful shutdown status
     */
    protected FutureImpl<Transport> shutdownFuture;

    /**
     * ExecutorService hosting shutdown listener threads.
     */
    protected ExecutorService shutdownService;

    public NIOTransport(final String name) {
        super(name);
        temporarySelectorIO = createTemporarySelectorIO();
    }

    @Override
    public abstract void unbindAll();

    @Override
    public boolean addShutdownListener(final GracefulShutdownListener shutdownListener) {
        final Lock lock = state.getStateLocker().writeLock();
        lock.lock();
        try {
            final State stateNow = state.getState();
            if (stateNow != State.STOPPING || stateNow != State.STOPPED) {
                if (shutdownListeners == null) {
                    shutdownListeners = new HashSet<GracefulShutdownListener>();
                }
                return shutdownListeners.add(shutdownListener);
            }
            return false;
        } finally {
            lock.unlock();
        }
    }

    public TemporarySelectorIO getTemporarySelectorIO() {
        return temporarySelectorIO;
    }

    public SelectionKeyHandler getSelectionKeyHandler() {
        return selectionKeyHandler;
    }

    public void setSelectionKeyHandler(final SelectionKeyHandler selectionKeyHandler) {
        this.selectionKeyHandler = selectionKeyHandler;
        notifyProbesConfigChanged(this);
    }

    public SelectorHandler getSelectorHandler() {
        return selectorHandler;
    }

    public void setSelectorHandler(final SelectorHandler selectorHandler) {
        this.selectorHandler = selectorHandler;
        notifyProbesConfigChanged(this);
    }

    /**
     * @return the configurator responsible for initial {@link SelectableChannel}
     * configuration
     */
    public ChannelConfigurator getChannelConfigurator() {
        return channelConfigurator;
    }

    /**
     * Sets the configurator responsible for initial {@link SelectableChannel}
     * configuration.
     * 
     * @param channelConfigurator {@link ChannelConfigurator}
     */
    public void setChannelConfigurator(
            final ChannelConfigurator channelConfigurator) {
        this.channelConfigurator = channelConfigurator;
        notifyProbesConfigChanged(this);
    }

    /**
     * @return the number of {@link SelectorRunner}s used for handling
     * NIO events
     */
    public int getSelectorRunnersCount() {
        if (selectorRunnersCount <= 0) {
            selectorRunnersCount = getDefaultSelectorRunnersCount();
        }
        
        return selectorRunnersCount;
    }

    /**
     * Sets the number of {@link SelectorRunner}s used for handling
     * NIO events.
     * @param selectorRunnersCount
     */
    public void setSelectorRunnersCount(final int selectorRunnersCount) {
        if (selectorRunnersCount > 0) {
            this.selectorRunnersCount = selectorRunnersCount;
            if (kernelPoolConfig != null &&
                    kernelPoolConfig.getMaxPoolSize() < selectorRunnersCount) {
                kernelPoolConfig.setCorePoolSize(selectorRunnersCount)
                                .setMaxPoolSize(selectorRunnersCount);
            }
            notifyProbesConfigChanged(this);
        }
    }

    /**
     * Get the {@link SelectorProvider} to be used by this transport.
     * 
     * @return the {@link SelectorProvider} to be used by this transport.
     */    
    public SelectorProvider getSelectorProvider() {
        return selectorProvider;
    }

    /**
     * Set the {@link SelectorProvider} to be used by this transport.
     *
     * @param selectorProvider the {@link SelectorProvider}.
     */
    public void setSelectorProvider(final SelectorProvider selectorProvider) {
        this.selectorProvider = selectorProvider != null
                ? selectorProvider
                : SelectorProvider.provider();
    }

    /**
     * Returns <tt>true</tt>, if <tt>NIOTransport</tt> is configured to use
     * {@link org.glassfish.grizzly.asyncqueue.AsyncQueueWriter}, optimized to be used in connection multiplexing
     * mode, or <tt>false</tt> otherwise.
     *
     * @return <tt>true</tt>, if <tt>NIOTransport</tt> is configured to use
     * {@link org.glassfish.grizzly.asyncqueue.AsyncQueueWriter}, optimized to be used in connection multiplexing
     * mode, or <tt>false</tt> otherwise.
     */
    @SuppressWarnings("UnusedDeclaration")
    public boolean isOptimizedForMultiplexing() {
        return optimizedForMultiplexing;
    }

    /**
     * Configures <tt>NIOTransport</tt> to be optimized for specific for the
     * connection multiplexing usecase, when different threads will try to
     * write data simultaneously.
     */
    public void setOptimizedForMultiplexing(final boolean optimizedForMultiplexing) {
        this.optimizedForMultiplexing = optimizedForMultiplexing;
        getAsyncQueueIO().getWriter().setAllowDirectWrite(!optimizedForMultiplexing);
    }

    protected synchronized void startSelectorRunners() throws IOException {
        selectorRunners = new SelectorRunner[selectorRunnersCount];
        
        for (int i = 0; i < selectorRunnersCount; i++) {
            final SelectorRunner runner = SelectorRunner.create(this);
            runner.start();
            selectorRunners[i] = runner;
        }
    }
    
    protected synchronized void stopSelectorRunners() {
        if (selectorRunners == null) {
            return;
        }

        for (int i = 0; i < selectorRunners.length; i++) {
            SelectorRunner runner = selectorRunners[i];
            if (runner != null) {
                runner.stop();
                selectorRunners[i] = null;
            }
        }

        selectorRunners = null;
    }

    public NIOChannelDistributor getNIOChannelDistributor() {
        return nioChannelDistributor;
    }

    public void setNIOChannelDistributor(final NIOChannelDistributor nioChannelDistributor) {
        this.nioChannelDistributor = nioChannelDistributor;
        notifyProbesConfigChanged(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void notifyTransportError(final Throwable error) {
        notifyProbesError(this, error);
    }

    protected SelectorRunner[] getSelectorRunners() {
        return selectorRunners;
    }

    /**
     * Notify registered {@link TransportProbe}s about the error.
     *
     * @param transport the <tt>Transport</tt> event occurred on.
     */
    protected static void notifyProbesError(final NIOTransport transport,
            final Throwable error) {
        final TransportProbe[] probes =
                transport.transportMonitoringConfig.getProbesUnsafe();
        if (probes != null) {
            for (TransportProbe probe : probes) {
                probe.onErrorEvent(transport, error);
            }
        }
    }

    /**
     * Notify registered {@link TransportProbe}s about the start event.
     *
     * @param transport the <tt>Transport</tt> event occurred on.
     */
    protected static void notifyProbesStart(final NIOTransport transport) {
        final TransportProbe[] probes =
                transport.transportMonitoringConfig.getProbesUnsafe();
        if (probes != null) {
            for (TransportProbe probe : probes) {
                probe.onStartEvent(transport);
            }
        }
    }
    
    /**
     * Notify registered {@link TransportProbe}s about the stop event.
     *
     * @param transport the <tt>Transport</tt> event occurred on.
     */
    protected static void notifyProbesStop(final NIOTransport transport) {
        final TransportProbe[] probes =
                transport.transportMonitoringConfig.getProbesUnsafe();
        if (probes != null) {
            for (TransportProbe probe : probes) {
                probe.onStopEvent(transport);
            }
        }
    }

    /**
     * Notify registered {@link TransportProbe}s about the pause event.
     *
     * @param transport the <tt>Transport</tt> event occurred on.
     */
    protected static void notifyProbesPause(final NIOTransport transport) {
        final TransportProbe[] probes =
                transport.transportMonitoringConfig.getProbesUnsafe();
        if (probes != null) {
            for (TransportProbe probe : probes) {
                probe.onPauseEvent(transport);
            }
        }
    }

    /**
     * Notify registered {@link TransportProbe}s about the resume event.
     *
     * @param transport the <tt>Transport</tt> event occurred on.
     */
    protected static void notifyProbesResume(final NIOTransport transport) {
        final TransportProbe[] probes =
                transport.transportMonitoringConfig.getProbesUnsafe();
        if (probes != null) {
            for (TransportProbe probe : probes) {
                probe.onResumeEvent(transport);
            }
        }
    }

    /**
     * Start TCPNIOTransport.
     * <p/>
     * The transport will be started only if its current state is {@link State#STOPPED},
     * otherwise the call will be ignored without exception thrown and the transport
     * state will remain the same as it was before the method call.
     */
    @Override
    public void start() throws IOException {
        final Lock lock = state.getStateLocker().writeLock();
        lock.lock();
        try {
            State currentState = state.getState();
            if (currentState != State.STOPPED) {
                LOGGER.log(Level.WARNING,
                           LogMessages.WARNING_GRIZZLY_TRANSPORT_NOT_STOP_STATE_EXCEPTION());
                return;
            }

            state.setState(State.STARTING);
            notifyProbesBeforeStart(this);

            if (selectorProvider == null) {
                selectorProvider = SelectorProvider.provider();
            }

            if (selectorHandler == null) {
                selectorHandler = new DefaultSelectorHandler();
            }

            if (selectionKeyHandler == null) {
                selectionKeyHandler = new DefaultSelectionKeyHandler();
            }

            if (processor == null && processorSelector == null) {
                processor = new StandaloneProcessor();
            }

            final int selectorRunnersCnt = getSelectorRunnersCount();

            if (nioChannelDistributor == null) {
                nioChannelDistributor =
                        new RoundRobinConnectionDistributor(this);
            }

            if (kernelPool == null) {
                if (kernelPoolConfig == null) {
                    kernelPoolConfig = ThreadPoolConfig.defaultConfig()
                            .setCorePoolSize(selectorRunnersCnt)
                            .setMaxPoolSize(selectorRunnersCnt)
                            .setPoolName("grizzly-nio-kernel");
                } else if (kernelPoolConfig.getMaxPoolSize() < selectorRunnersCnt) {
                    LOGGER.log(Level.INFO, "Adjusting kernel thread pool to max "
                            + "size {0} to handle configured number of SelectorRunners",
                            selectorRunnersCnt);
                    kernelPoolConfig.setCorePoolSize(selectorRunnersCnt)
                            .setMaxPoolSize(selectorRunnersCnt);
                }

                kernelPoolConfig.setMemoryManager(memoryManager);
                setKernelPool0(
                        GrizzlyExecutorService.createInstance(
                                kernelPoolConfig));
            }

            if (workerThreadPool == null) {
                if (workerPoolConfig != null) {
                    if (getThreadPoolMonitoringConfig().hasProbes()) {
                        workerPoolConfig.getInitialMonitoringConfig().addProbes(
                                getThreadPoolMonitoringConfig().getProbes());
                    }
                    workerPoolConfig.setMemoryManager(memoryManager);
                    setWorkerThreadPool0(GrizzlyExecutorService.createInstance(
                            workerPoolConfig));
                }
            }

            /* By default TemporarySelector pool size should be equal
            to the number of processing threads */
            int selectorPoolSize =
                    TemporarySelectorPool.DEFAULT_SELECTORS_COUNT;
            if (workerThreadPool instanceof AbstractThreadPool) {
                if (strategy instanceof SameThreadIOStrategy) {
                    selectorPoolSize = selectorRunnersCnt;
                } else {
                    selectorPoolSize = Math.min(
                            ((AbstractThreadPool) workerThreadPool).getConfig()
                                    .getMaxPoolSize(),
                            selectorPoolSize);
                }
            }

            if (strategy == null) {
                strategy = WorkerThreadIOStrategy.getInstance();
            }

            temporarySelectorIO.setSelectorPool(
                    new TemporarySelectorPool(selectorProvider,
                                              selectorPoolSize));

            startSelectorRunners();

            listen();

            state.setState(State.STARTED);

            notifyProbesStart(this);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public GrizzlyFuture<Transport> shutdown() {
        return shutdown(-1, TimeUnit.MILLISECONDS);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public GrizzlyFuture<Transport> shutdown(final long gracePeriod,
                                             final TimeUnit timeUnit) {
        final Lock lock = state.getStateLocker().writeLock();
        lock.lock();
        try {
            final State stateNow = state.getState();
            if (stateNow == State.STOPPING) {
                // graceful shutdown in progress
                return shutdownFuture;
            } else if (stateNow == State.STOPPED) {
                return Futures.<Transport>createReadyFuture(this);
            } else if (stateNow == State.PAUSED) {
                resume();
            }

            state.setState(State.STOPPING);

            unbindAll();

            final GrizzlyFuture<Transport> resultFuture;
            
            if (shutdownListeners != null && !shutdownListeners.isEmpty()) {
                shutdownFuture = Futures.createSafeFuture();
                shutdownService = createShutdownExecutorService();
                shutdownService.execute(
                        new GracefulShutdownRunner(this,
                                                   shutdownListeners,
                                                   shutdownService,
                                                   gracePeriod,
                                                   timeUnit));
                shutdownListeners = null;
                resultFuture = shutdownFuture;
            } else {
                finalizeShutdown();
                resultFuture = Futures.<Transport>createReadyFuture(this);
            }
            
            return resultFuture;
        } finally {
            lock.unlock();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void shutdownNow() throws IOException {
        final Lock lock = state.getStateLocker().writeLock();
        lock.lock();
        try {
            final State stateNow = state.getState();

            if (stateNow == State.STOPPED) {
                return;
            }

            if (stateNow == State.PAUSED) {
                // if Transport is paused - first we need to resume it
                // so selectorrunners can perform the close phase
                resume();
            }
            
            state.setState(State.STOPPING);
            unbindAll();
            finalizeShutdown();
        } finally {
            lock.unlock();
        }
    }

    @Override
    protected abstract void closeConnection(Connection connection)
            throws IOException;

    protected abstract TemporarySelectorIO createTemporarySelectorIO();

    protected abstract void listen();

    protected int getDefaultSelectorRunnersCount() {
        return Runtime.getRuntime().availableProcessors();
    }

    protected void finalizeShutdown() {
        if (shutdownService != null && !shutdownService.isShutdown()) {
            final boolean isInterrupted = Thread.currentThread().isInterrupted();
            shutdownService.shutdownNow();
            shutdownService = null;
            
            if (!isInterrupted) {
                // if we're in shutdown thread and prev status was "not-interrupted" -
                // clear the interrupted flag, which might have been set
                // when we shutdownNow() the shutdownService.
                Thread.interrupted();
            }
        }

        notifyProbesBeforeStop(this);
        stopSelectorRunners();

        if (workerThreadPool != null && managedWorkerPool) {
            workerThreadPool.shutdown();
            workerThreadPool = null;
        }

        if (kernelPool != null) {
            kernelPool.shutdownNow();
            kernelPool = null;
        }
        state.setState(State.STOPPED);
        notifyProbesStop(this);
        
        if (shutdownFuture != null) {
            shutdownFuture.result(this);
            shutdownFuture = null;
        }
    }

    /**
     * Pause UDPNIOTransport, so I/O events coming on its {@link org.glassfish.grizzly.nio.transport.UDPNIOConnection}s
     * will not be processed. Use {@link #resume()} in order to resume UDPNIOTransport processing.
     *
     * The transport will be paused only if its current state is {@link org.glassfish.grizzly.Transport.State#STARTED},
     * otherwise the call will be ignored without exception thrown and the transport
     * state will remain the same as it was before the method call.
     */
    @Override
    public void pause() {
        final Lock lock = state.getStateLocker().writeLock();
        lock.lock();
        try {
            if (state.getState() != State.STARTED) {
                LOGGER.log(Level.WARNING,
                        LogMessages.WARNING_GRIZZLY_TRANSPORT_NOT_START_STATE_EXCEPTION());
                return;
            }
            state.setState(State.PAUSING);
            notifyProbesBeforePause(this);
            state.setState(State.PAUSED);
            notifyProbesPause(this);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Resume UDPNIOTransport, which has been paused before using {@link #pause()}.
     *
     * The transport will be resumed only if its current state is {@link org.glassfish.grizzly.Transport.State#PAUSED},
     * otherwise the call will be ignored without exception thrown and the transport
     * state will remain the same as it was before the method call.
     */
    @Override
    public void resume() {
        final Lock lock = state.getStateLocker().writeLock();
        lock.lock();
        try {
            if (state.getState() != State.PAUSED) {
                LOGGER.log(Level.WARNING,
                        LogMessages.WARNING_GRIZZLY_TRANSPORT_NOT_PAUSE_STATE_EXCEPTION());
                return;
            }
            state.setState(State.STARTING);
            notifyProbesBeforeResume(this);
            state.setState(State.STARTED);
            notifyProbesResume(this);
        } finally {
            lock.unlock();
        }
    }

    protected void configureNIOConnection(NIOConnection connection) {
        connection.configureBlocking(isBlocking);
        connection.configureStandalone(isStandalone);
        connection.setProcessor(processor);
        connection.setProcessorSelector(processorSelector);
        connection.setReadTimeout(readTimeout, TimeUnit.MILLISECONDS);
        connection.setWriteTimeout(writeTimeout, TimeUnit.MILLISECONDS);
        if (connectionMonitoringConfig.hasProbes()) {
            connection.setMonitoringProbes(connectionMonitoringConfig.getProbes());
        }
    }

    public boolean isReuseAddress() {
        return reuseAddress;
    }

    public void setReuseAddress(final boolean reuseAddress) {
        this.reuseAddress = reuseAddress;
        notifyProbesConfigChanged(this);
    }

    public int getClientSocketSoTimeout() {
        return clientSocketSoTimeout;
    }

    @SuppressWarnings({"UnusedDeclaration"})
    public void setClientSocketSoTimeout(final int socketTimeout) {
        if (socketTimeout < 0) {
            throw new IllegalArgumentException("socketTimeout can't be negative value");
        }
        
        this.clientSocketSoTimeout = socketTimeout;
        notifyProbesConfigChanged(this);
    }

    public int getConnectionTimeout() {
        return connectionTimeout;
    }

    @SuppressWarnings({"UnusedDeclaration"})
    public void setConnectionTimeout(final int connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
        notifyProbesConfigChanged(this);
    }

    public int getServerSocketSoTimeout() {
        return serverSocketSoTimeout;
    }

    @SuppressWarnings({"UnusedDeclaration"})
    public void setServerSocketSoTimeout(final int serverSocketSoTimeout) {
        if (serverSocketSoTimeout < 0) {
            throw new IllegalArgumentException("socketTimeout can't be negative value");
        }

        this.serverSocketSoTimeout = serverSocketSoTimeout;
        notifyProbesConfigChanged(this);
    }

    protected ExecutorService createShutdownExecutorService() {
        final String baseThreadIdentifier =
                this.getName()
                        + '['
                        + Integer.toHexString(this.hashCode())
                        + "]-Shutdown-Thread";
        final ThreadFactory factory =
                new ThreadFactory() {
                    private int counter;

                    @Override
                    public Thread newThread(Runnable r) {
                        Thread t =
                                new Thread(r, baseThreadIdentifier
                                                 + "(" + counter++ + ')');
                        t.setDaemon(true);
                        return t;
                    }
                };

        return Executors.newFixedThreadPool(2, factory);
    }
}
