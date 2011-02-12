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

package org.glassfish.grizzly.aio.transport;

import java.util.concurrent.ExecutorService;
import org.glassfish.grizzly.CompletionHandler;
import org.glassfish.grizzly.ProcessorSelector;
import org.glassfish.grizzly.asyncqueue.AsyncQueueIO;
import org.glassfish.grizzly.aio.AIOTransport;
import org.glassfish.grizzly.asyncqueue.AsyncQueueEnabledTransport;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.IOEvent;
import org.glassfish.grizzly.Processor;
import org.glassfish.grizzly.aio.AIOConnection;
import org.glassfish.grizzly.asyncqueue.AsyncQueueReader;
import org.glassfish.grizzly.asyncqueue.AsyncQueueWriter;
import org.glassfish.grizzly.filterchain.FilterChainEnabledTransport;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Collection;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.grizzly.GrizzlyFuture;
import org.glassfish.grizzly.PostProcessor;
import org.glassfish.grizzly.ProcessorExecutor;
import org.glassfish.grizzly.Reader;
import org.glassfish.grizzly.SocketBinder;
import org.glassfish.grizzly.StandaloneProcessor;
import org.glassfish.grizzly.StandaloneProcessorSelector;
import org.glassfish.grizzly.Writer;
import org.glassfish.grizzly.filterchain.Filter;
import org.glassfish.grizzly.monitoring.jmx.JmxObject;
import org.glassfish.grizzly.strategies.WorkerThreadIOStrategy;
import org.glassfish.grizzly.threadpool.GrizzlyExecutorService;
import java.net.StandardSocketOption;
import java.nio.channels.AsynchronousChannel;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.util.concurrent.TimeUnit;
import org.glassfish.grizzly.SocketConnectorHandler;
import org.glassfish.grizzly.aio.BlockingIO;

/**
 * TCP Transport AIO implementation
 * 
 * @author Alexey Stashok
 * @author Jean-Francois Arcand
 */
public final class TCPAIOTransport extends AIOTransport implements
        SocketBinder, SocketConnectorHandler, AsyncQueueEnabledTransport,
        FilterChainEnabledTransport {

    private static final Logger LOGGER = Grizzly.logger(TCPAIOTransport.class);

    private static final int DEFAULT_READ_BUFFER_SIZE = 8192;
    private static final int DEFAULT_WRITE_BUFFER_SIZE = 8192;
    
    private static final String DEFAULT_TRANSPORT_NAME = "TCPAIOTransport";
    /**
     * The Server connections.
     */
    final Collection<TCPAIOServerConnection> serverConnections;
    /**
     * Transport AsyncQueueIO
     */
    final AsyncQueueIO asyncQueueIO;
    
    final BlockingIO blockingIO;
    
    /**
     * The server socket time out
     */
    int serverSocketSoTimeout = 0;
    /**
     * The socket tcpDelay.
     * 
     * Default value for tcpNoDelay is disabled (set to true).
     */
    boolean tcpNoDelay = true;
    /**
     * The socket reuseAddress
     */
    boolean reuseAddress = true;
    /**
     * The socket linger.
     */
    int linger = -1;
    /**
     * The socket keepAlive mode.
     */
    boolean isKeepAlive = false;
    /**
     * The socket time out
     */
    int clientSocketSoTimeout = -1;
    /**
     * The default server connection backlog size
     */
    int serverConnectionBackLog = 4096;
    /**
     * Default channel connection timeout
     */
    int connectionTimeout =
            TCPAIOConnectorHandler.DEFAULT_CONNECTION_TIMEOUT;

    private int maxReadAttempts = 3;
    
    private Filter defaultTransportFilter;

    /**
     * Default {@link TCPAIOConnectorHandler}
     */
    private final TCPAIOConnectorHandler connectorHandler =
            new TransportConnectorHandler();
    
    private volatile AsynchronousChannelGroup asynchronousChannelGroup;
    
    public TCPAIOTransport() {
        this(DEFAULT_TRANSPORT_NAME);
    }

    TCPAIOTransport(final String name) {
        super(name);
        
        readBufferSize = DEFAULT_READ_BUFFER_SIZE;
        writeBufferSize = DEFAULT_WRITE_BUFFER_SIZE;

        asyncQueueIO = new AsyncQueueIO(new TCPAIOAsyncQueueReader(this),
                new TCPAIOAsyncQueueWriter(this));
        blockingIO = new BlockingIO(new TCPAIOBlockingReader(this),
                new TCPAIOBlockingWriter(this));

        attributeBuilder = Grizzly.DEFAULT_ATTRIBUTE_BUILDER;
        defaultTransportFilter = new TCPAIOTransportFilter(this);
        serverConnections = new ConcurrentLinkedQueue<>();
    }

    @Override
    public void start() throws IOException {
        state.getStateLocker().writeLock().lock();
        try {
            State currentState = state.getState();
            if (currentState != State.STOP) {
                LOGGER.log(Level.WARNING,
                        "Transport is not in STOP or BOUND state!");
            }

            state.setState(State.STARTING);

            if (processor == null && processorSelector == null) {
                processor = new StandaloneProcessor();
            }

//            if (selectorRunnersCount <= 0) {
//                selectorRunnersCount = Math.max(1, Runtime.getRuntime().availableProcessors() / 2 * 3);
//            }

            checkKernelPool();
            
            if (workerThreadPool == null) {
                if (workerPoolConfig != null) {
                    workerPoolConfig.getInitialMonitoringConfig().addProbes(
                        getThreadPoolMonitoringConfig().getProbes());
                    setWorkerThreadPool0(GrizzlyExecutorService.createInstance(workerPoolConfig));
                }
            }

            if (strategy == null) {
                strategy = WorkerThreadIOStrategy.getInstance();
            }
            
            listenServerConnections();

            state.setState(State.START);

            notifyProbesStart(this);
        } finally {
            state.getStateLocker().writeLock().unlock();
        }
    }

    private void listenServerConnections() {
        for (TCPAIOServerConnection serverConnection : serverConnections) {
            try {
                listenServerConnection(serverConnection);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING,
                        "Exception occurred when starting server connection: " +
                        serverConnection, e);
            }
        }
    }

    private void listenServerConnection(TCPAIOServerConnection serverConnection)
            throws IOException {
        serverConnection.listen();
    }

    @Override
    public void stop() throws IOException {
        state.getStateLocker().writeLock().lock();

        try {
            unbindAll();
            state.setState(State.STOP);

//            stopSelectorRunners();
            final AsynchronousChannelGroup asyncChannelGroupLocal =
                    asynchronousChannelGroup;
            asynchronousChannelGroup = null;
            
            if (asyncChannelGroupLocal != null) {
                asyncChannelGroupLocal.shutdown();
            }
            
            if (workerThreadPool != null && managedWorkerPool) {
                workerThreadPool.shutdown();
                workerThreadPool = null;
            }

            notifyProbesStop(this);
        } finally {
            state.getStateLocker().writeLock().unlock();
        }
    }

    @Override
    public void pause() throws IOException {
        state.getStateLocker().writeLock().lock();

        try {
            if (state.getState() != State.START) {
                LOGGER.log(Level.WARNING,
                        "Transport is not in START state!");
            }
            state.setState(State.PAUSE);
            notifyProbesPause(this);
        } finally {
            state.getStateLocker().writeLock().unlock();
        }
    }

    @Override
    public void resume() throws IOException {
        state.getStateLocker().writeLock().lock();

        try {
            if (state.getState() != State.PAUSE) {
                LOGGER.log(Level.WARNING,
                        "Transport is not in PAUSE state!");
            }
            state.setState(State.START);
            notifyProbesResume(this);
        } finally {
            state.getStateLocker().writeLock().unlock();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TCPAIOServerConnection bind(final int port) throws IOException {
        return bind(new InetSocketAddress(port));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TCPAIOServerConnection bind(final String host, final int port)
            throws IOException {
        return bind(host, port, serverConnectionBackLog);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TCPAIOServerConnection bind(final String host, final int port,
            final int backlog) throws IOException {
        return bind(new InetSocketAddress(host, port), backlog);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TCPAIOServerConnection bind(final SocketAddress socketAddress)
            throws IOException {
        return bind(socketAddress, serverConnectionBackLog);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TCPAIOServerConnection bind(final SocketAddress socketAddress,
            final int backlog)
            throws IOException {
        state.getStateLocker().writeLock().lock();

        try {
            AsynchronousServerSocketChannel serverSocketChannel =
                    AsynchronousServerSocketChannel.open(getAsynchronousChannelGroup());
            
            final TCPAIOServerConnection serverConnection =
                    obtainServerAIOConnection(serverSocketChannel);

            serverConnections.add(serverConnection);
            
//            ServerSocket serverSocket = serverSocketChannel.socket();
            serverSocketChannel.setOption(StandardSocketOption.SO_REUSEADDR,
                    reuseAddress);
//            serverSocket.setSoTimeout(serverSocketSoTimeout);

            serverSocketChannel.bind(socketAddress, backlog);

            serverConnection.resetProperties();

            if (!isStopped()) {
                listenServerConnection(serverConnection);
            }

            return serverConnection;
        } finally {
            state.getStateLocker().writeLock().unlock();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void unbind(Connection connection) throws IOException {
        state.getStateLocker().writeLock().lock();

        try {
            if (connection != null
                    && serverConnections.remove(connection)) {
                final GrizzlyFuture future = connection.close();
                try {
                    future.get(1000, TimeUnit.MILLISECONDS);
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Error unbinding connection: " + connection, e);
                } finally {
                    future.markForRecycle(true);
                }
            }
        } finally {
            state.getStateLocker().writeLock().unlock();
        }
    }

    @Override
    public void unbindAll() throws IOException {
        state.getStateLocker().writeLock().lock();

        try {
            for (Connection serverConnection : serverConnections) {
                try {
                    unbind(serverConnection);
                } catch (Exception e) {
                    LOGGER.log(Level.FINE,
                            "Exception occurred when closing server connection: "
                            + serverConnection, e);
                }
            }

            serverConnections.clear();
        } finally {
            state.getStateLocker().writeLock().unlock();
        }
    }

    /**
     * Creates, initializes and connects socket to the specific remote host
     * and port and returns {@link Connection}, representing socket.
     *
     * @param host remote host to connect to.
     * @param port remote port to connect to.
     * @return {@link GrizzlyFuture} of connect operation, which could be used to get
     * resulting {@link Connection}.
     *
     * @throws java.io.IOException
     */
    @Override
    public GrizzlyFuture<Connection> connect(final String host, final int port)
            throws IOException {
        return connectorHandler.connect(host, port);
    }

    /**
     * Creates, initializes and connects socket to the specific
     * {@link SocketAddress} and returns {@link Connection}, representing socket.
     *
     * @param remoteAddress remote address to connect to.
     * @return {@link GrizzlyFuture} of connect operation, which could be used to get
     * resulting {@link Connection}.
     *
     * @throws java.io.IOException
     */
    @Override
    public GrizzlyFuture<Connection> connect(final SocketAddress remoteAddress)
            throws IOException {
        return connectorHandler.connect(remoteAddress);
    }

    /**
     * Creates, initializes and connects socket to the specific
     * {@link SocketAddress} and returns {@link Connection}, representing socket.
     *
     * @param remoteAddress remote address to connect to.
     * @param completionHandler {@link CompletionHandler}.
     * @return {@link GrizzlyFuture} of connect operation, which could be used to get
     * resulting {@link Connection}.
     *
     * @throws java.io.IOException
     */
    @Override
    public GrizzlyFuture<Connection> connect(final SocketAddress remoteAddress,
            final CompletionHandler<Connection> completionHandler)
            throws IOException {
        return connectorHandler.connect(remoteAddress, completionHandler);
    }

    /**
     * Creates, initializes socket, binds it to the specific local and remote
     * {@link SocketAddress} and returns {@link Connection}, representing socket.
     *
     * @param remoteAddress remote address to connect to.
     * @param localAddress local address to bind socket to.
     * @return {@link GrizzlyFuture} of connect operation, which could be used to get
     * resulting {@link Connection}.
     *
     * @throws java.io.IOException
     */
    @Override
    public GrizzlyFuture<Connection> connect(final SocketAddress remoteAddress,
            final SocketAddress localAddress) throws IOException {
        return connectorHandler.connect(remoteAddress, localAddress);
    }

    /**
     * Creates, initializes socket, binds it to the specific local and remote
     * {@link SocketAddress} and returns {@link Connection}, representing socket.
     *
     * @param remoteAddress remote address to connect to.
     * @param localAddress local address to bind socket to.
     * @param completionHandler {@link CompletionHandler}.
     * @return {@link GrizzlyFuture} of connect operation, which could be used to get
     * resulting {@link Connection}.
     *
     * @throws java.io.IOException
     */
    @Override
    public GrizzlyFuture<Connection> connect(final SocketAddress remoteAddress,
            final SocketAddress localAddress,
            final CompletionHandler<Connection> completionHandler)
            throws IOException {
        return connectorHandler.connect(remoteAddress, localAddress,
                completionHandler);
    }

    @Override
    protected void closeConnection(final Connection connection) throws IOException {
        final AsynchronousChannel aioChannel = ((AIOConnection) connection).getChannel();

        if (aioChannel != null) {
            try {
                aioChannel.close();
            } catch (IOException e) {
                LOGGER.log(Level.FINE,
                        "TCPAIOTransport.closeChannel exception", e);
            }
        }

        if (asyncQueueIO != null) {
            final AsyncQueueReader reader = asyncQueueIO.getReader();
            if (reader != null) {
                reader.onClose(connection);
            }

            final AsyncQueueWriter writer = asyncQueueIO.getWriter();
            if (writer != null) {
                writer.onClose(connection);
            }

        }
    }

    void checkKernelPool() {
        if (kernelPool == null) {
            setKernelPool0(GrizzlyExecutorService.createInstance(kernelPoolConfig));
        }
    }
    
    AsynchronousChannelGroup getAsynchronousChannelGroup() {
        if (asynchronousChannelGroup == null) {
            checkKernelPool();
            try {
                asynchronousChannelGroup =
                        AsynchronousChannelGroup.withThreadPool(kernelPool);
            } catch (IOException e) {
                throw new IllegalStateException(
                        "Can not initialize AsynchronousChannelGroup", e);
            }
        }
        
        return asynchronousChannelGroup;
    }
    
    TCPAIOConnection obtainAIOConnection(final AsynchronousSocketChannel channel) {
        final TCPAIOConnection connection = new TCPAIOConnection(this, channel);
        configureAIOConnection(connection);
        
        return connection;
    }

    TCPAIOServerConnection obtainServerAIOConnection(
            final AsynchronousServerSocketChannel channel) {
        final TCPAIOServerConnection connection =
                new TCPAIOServerConnection(this, channel);
        configureAIOConnection(connection);

        return connection;
    }

    void configureAIOConnection(final TCPAIOConnection connection) {
        connection.configureBlocking(isBlocking);
        connection.configureStandalone(isStandalone);
        connection.setProcessor(processor);
        connection.setProcessorSelector(processorSelector);
        connection.setMonitoringProbes(connectionMonitoringConfig.getProbes());
    }
    
    /**
     * Configuring <code>SocketChannel</code> according the transport settings
     * @param channel <code>SocketChannel</code> to configure
     * @throws java.io.IOException
     */
    void configureChannel(final AsynchronousSocketChannel channel)
            throws IOException {

        try {
            if (linger >= 0) {
                channel.setOption(StandardSocketOption.SO_LINGER, linger);
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Can not set linger to " + linger, e);
        }

        try {
            channel.setOption(StandardSocketOption.SO_KEEPALIVE, isKeepAlive);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Can not set keepAlive to " + isKeepAlive, e);
        }
        
        try {
            channel.setOption(StandardSocketOption.TCP_NODELAY, tcpNoDelay);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Can not set TcpNoDelay to " + tcpNoDelay, e);
        }
        
        channel.setOption(StandardSocketOption.SO_REUSEADDR, reuseAddress);
    }

    @Override
    public AsyncQueueIO getAsyncQueueIO() {
        return asyncQueueIO;
    }

    public BlockingIO getBlockingIO() {
        return blockingIO;
    }
    
    @Override
    public synchronized void configureStandalone(final boolean isStandalone) {
        if (this.isStandalone != isStandalone) {
            this.isStandalone = isStandalone;
            if (isStandalone) {
                processor = StandaloneProcessor.INSTANCE;
                processorSelector = StandaloneProcessorSelector.INSTANCE;
            } else {
                processor = null;
                processorSelector = null;
            }
        }
    }

    public int getLinger() {
        return linger;
    }

    public void setLinger(final int linger) {
        this.linger = linger;
        notifyProbesConfigChanged(this);
    }

    /**
     * Get the default server connection backlog size.
     * @return the default server connection backlog size.
     */
    public int getServerConnectionBackLog() {
        return serverConnectionBackLog;
    }

    /**
     * Set the default server connection backlog size.
     * @serverConnectionBackLog the default server connection backlog size.
     */
    public void setServerConnectionBackLog(final int serverConnectionBackLog) {
        this.serverConnectionBackLog = serverConnectionBackLog;
    }

    public boolean isKeepAlive() {
        return isKeepAlive;
    }

    @SuppressWarnings({"UnusedDeclaration"})
    public void setKeepAlive(final boolean isKeepAlive) {
        this.isKeepAlive = isKeepAlive;
        notifyProbesConfigChanged(this);
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

    public boolean isTcpNoDelay() {
        return tcpNoDelay;
    }

    public void setTcpNoDelay(final boolean tcpNoDelay) {
        this.tcpNoDelay = tcpNoDelay;
        notifyProbesConfigChanged(this);
    }

    public int getServerSocketSoTimeout() {
        return serverSocketSoTimeout;
    }

    @SuppressWarnings({"UnusedDeclaration"})
    public void setServerSocketSoTimeout(final int serverSocketSoTimeout) {
        this.serverSocketSoTimeout = serverSocketSoTimeout;
        notifyProbesConfigChanged(this);
    }

    @Override
    public Filter getTransportFilter() {
        return defaultTransportFilter;
    }

    @Override
    public IOEventReg fireIOEvent(final IOEvent ioEvent,
            final Connection connection, final PostProcessor postProcessor)
            throws IOException {

        try {            
            final Processor conProcessor = connection.obtainProcessor(ioEvent);

                if (ProcessorExecutor.execute(connection, ioEvent,
                        conProcessor, postProcessor)) {
                    return IOEventReg.REGISTER;
                } else {
                    return IOEventReg.DEREGISTER;
                }
        } catch (IOException e) {
            LOGGER.log(Level.FINE, "IOException occurred on fireIOEvent(). "
                    + "Connection={0} event={1}", new Object[] {connection, ioEvent});
            throw e;
        } catch (Exception e) {
            String text = new StringBuilder(256).append("Unexpected exception occurred fireIOEvent().").
                    append("connection=").append(connection).
                    append(" event=").append(ioEvent).toString();

            LOGGER.log(Level.WARNING, text, e);
            throw new IOException(e.getClass() + ": " + text);
        }
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public Reader getReader(final Connection connection) {
        return getReader(connection.isBlocking());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Reader getReader(final boolean isBlocking) {
        if (isBlocking) {
            return blockingIO.getReader();
        } else {
            return getAsyncQueueIO().getReader();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Writer getWriter(final Connection connection) {
        return getWriter(connection.isBlocking());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Writer getWriter(final boolean isBlocking) {
        if (isBlocking) {
            return blockingIO.getWriter();
        } else {
            return getAsyncQueueIO().getWriter();
        }
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    protected JmxObject createJmxManagementObject() {
//        return new org.glassfish.grizzly.aio.transport.jmx.TCPAIOTransport(this);
        return null;
    }
    
    /**
     * Transport default {@link TCPAIOConnectorHandler}.
     */
     class TransportConnectorHandler extends TCPAIOConnectorHandler {
        public TransportConnectorHandler() {
            super(TCPAIOTransport.this);
        }

        @Override
        public Processor getProcessor() {
            return TCPAIOTransport.this.getProcessor();
        }

        @Override
        public ProcessorSelector getProcessorSelector() {
            return TCPAIOTransport.this.getProcessorSelector();
        }
    }
}
