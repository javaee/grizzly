/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2011 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.nio.transport;

import org.glassfish.grizzly.CompletionHandler;
import org.glassfish.grizzly.PortRange;
import org.glassfish.grizzly.ProcessorSelector;
import org.glassfish.grizzly.asyncqueue.AsyncQueueIO;
import org.glassfish.grizzly.nio.RegisterChannelResult;
import org.glassfish.grizzly.nio.RoundRobinConnectionDistributor;
import org.glassfish.grizzly.nio.DefaultSelectorHandler;
import org.glassfish.grizzly.nio.DefaultSelectionKeyHandler;
import org.glassfish.grizzly.nio.NIOTransport;
import org.glassfish.grizzly.asyncqueue.AsyncQueueEnabledTransport;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.IOEvent;
import org.glassfish.grizzly.Processor;
import org.glassfish.grizzly.nio.NIOConnection;
import org.glassfish.grizzly.asyncqueue.AsyncQueueReader;
import org.glassfish.grizzly.asyncqueue.AsyncQueueWriter;
import org.glassfish.grizzly.filterchain.FilterChainEnabledTransport;
import org.glassfish.grizzly.nio.tmpselectors.TemporarySelectorPool;
import org.glassfish.grizzly.nio.tmpselectors.TemporarySelectorsEnabledTransport;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Collection;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.EmptyCompletionHandler;
import org.glassfish.grizzly.GrizzlyFuture;
import org.glassfish.grizzly.IOEventProcessingHandler;
import org.glassfish.grizzly.ProcessorExecutor;
import org.glassfish.grizzly.Reader;
import org.glassfish.grizzly.SocketBinder;
import org.glassfish.grizzly.StandaloneProcessor;
import org.glassfish.grizzly.StandaloneProcessorSelector;
import org.glassfish.grizzly.WriteResult;
import org.glassfish.grizzly.Writer;
import org.glassfish.grizzly.filterchain.Filter;
import org.glassfish.grizzly.monitoring.jmx.JmxObject;
import org.glassfish.grizzly.nio.SelectorRunner;
import org.glassfish.grizzly.nio.tmpselectors.TemporarySelectorIO;
import org.glassfish.grizzly.strategies.SameThreadIOStrategy;
import org.glassfish.grizzly.strategies.WorkerThreadIOStrategy;
import org.glassfish.grizzly.threadpool.AbstractThreadPool;
import org.glassfish.grizzly.threadpool.GrizzlyExecutorService;
import org.glassfish.grizzly.threadpool.WorkerThread;
import java.io.EOFException;
import java.lang.ref.SoftReference;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;
import org.glassfish.grizzly.SocketConnectorHandler;
import org.glassfish.grizzly.ThreadCache;
import org.glassfish.grizzly.memory.BufferArray;
import org.glassfish.grizzly.memory.ByteBufferArray;
import org.glassfish.grizzly.utils.Exceptions;

/**
 * TCP Transport NIO implementation
 * 
 * @author Alexey Stashok
 * @author Jean-Francois Arcand
 */
public final class TCPNIOTransport extends NIOTransport implements
        SocketBinder, SocketConnectorHandler, AsyncQueueEnabledTransport,
        FilterChainEnabledTransport, TemporarySelectorsEnabledTransport {

    private static final Logger LOGGER = Grizzly.logger(TCPNIOTransport.class);

    private static final int DEFAULT_READ_BUFFER_SIZE = 8192;
    private static final int DEFAULT_WRITE_BUFFER_SIZE = 8192;
    
    private static final String DEFAULT_TRANSPORT_NAME = "TCPNIOTransport";
    /**
     * The Server connections.
     */
    final Collection<TCPNIOServerConnection> serverConnections;
    /**
     * Transport AsyncQueueIO
     */
    final AsyncQueueIO<SocketAddress> asyncQueueIO;
    /**
     * Transport TemporarySelectorIO, used for blocking I/O simulation
     */
    TemporarySelectorIO temporarySelectorIO;
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
            TCPNIOConnectorHandler.DEFAULT_CONNECTION_TIMEOUT;

    private final int maxReadAttempts = 3;
    
    private final Filter defaultTransportFilter;
    final RegisterChannelCompletionHandler selectorRegistrationHandler;

    /**
     * Default {@link TCPNIOConnectorHandler}
     */
    private final TCPNIOConnectorHandler connectorHandler =
            new TransportConnectorHandler();
    
    public TCPNIOTransport() {
        this(DEFAULT_TRANSPORT_NAME);
    }

    TCPNIOTransport(final String name) {
        super(name);
        
        readBufferSize = DEFAULT_READ_BUFFER_SIZE;
        writeBufferSize = DEFAULT_WRITE_BUFFER_SIZE;

        selectorRegistrationHandler = new RegisterChannelCompletionHandler();

        asyncQueueIO = new AsyncQueueIO<SocketAddress>(
                new TCPNIOAsyncQueueReader(this),
                new TCPNIOAsyncQueueWriter(this));

        temporarySelectorIO = new TemporarySelectorIO(
                new TCPNIOTemporarySelectorReader(this),
                new TCPNIOTemporarySelectorWriter(this));

        attributeBuilder = Grizzly.DEFAULT_ATTRIBUTE_BUILDER;
        defaultTransportFilter = new TCPNIOTransportFilter(this);
        serverConnections = new ConcurrentLinkedQueue<TCPNIOServerConnection>();
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

            if (selectorHandler == null) {
                selectorHandler = new DefaultSelectorHandler();
            }

            if (selectionKeyHandler == null) {
                selectionKeyHandler = new DefaultSelectionKeyHandler();
            }

            if (processor == null && processorSelector == null) {
                processor = new StandaloneProcessor();
            }

            if (selectorRunnersCount <= 0) {
                selectorRunnersCount = Runtime.getRuntime().availableProcessors();
            }

            if (nioChannelDistributor == null) {
                nioChannelDistributor = new RoundRobinConnectionDistributor(this);
            }

            if (kernelPool == null) {
                kernelPoolConfig.setMemoryManager(memoryManager);
                setKernelPool0(GrizzlyExecutorService.createInstance(kernelPoolConfig));
            }

            if (workerThreadPool == null) {
                if (workerPoolConfig != null) {
                    workerPoolConfig.getInitialMonitoringConfig().addProbes(
                        getThreadPoolMonitoringConfig().getProbes());
                    workerPoolConfig.setMemoryManager(memoryManager);
                    setWorkerThreadPool0(GrizzlyExecutorService.createInstance(workerPoolConfig));
                }
            }

            /* By default TemporarySelector pool size should be equal
            to the number of processing threads */
            int selectorPoolSize =
                    TemporarySelectorPool.DEFAULT_SELECTORS_COUNT;
            if (workerThreadPool instanceof AbstractThreadPool) {
                if (strategy instanceof SameThreadIOStrategy) {
                    selectorPoolSize = selectorRunnersCount;
                } else {
                    selectorPoolSize = Math.min(
                           ((AbstractThreadPool) workerThreadPool).getConfig().getMaxPoolSize(),
                           selectorPoolSize);
                }
            }

            if (strategy == null) {
                strategy = WorkerThreadIOStrategy.getInstance();
            }

            temporarySelectorIO.setSelectorPool(
                    new TemporarySelectorPool(selectorPoolSize));

            startSelectorRunners();

            listenServerConnections();

            state.setState(State.START);

            notifyProbesStart(this);
        } finally {
            state.getStateLocker().writeLock().unlock();
        }
    }

    private void listenServerConnections() {
        for (TCPNIOServerConnection serverConnection : serverConnections) {
            try {
                listenServerConnection(serverConnection);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING,
                        "Exception occurred when starting server connection: " +
                        serverConnection, e);
            }
        }
    }

    private void listenServerConnection(TCPNIOServerConnection serverConnection)
            throws IOException {
        serverConnection.listen();
    }

    @Override
    public void stop() throws IOException {
        state.getStateLocker().writeLock().lock();

        try {
            unbindAll();
            state.setState(State.STOP);

            stopSelectorRunners();

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
    public TCPNIOServerConnection bind(final int port) throws IOException {
        return bind(new InetSocketAddress(port));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TCPNIOServerConnection bind(final String host, final int port)
            throws IOException {
        return bind(host, port, serverConnectionBackLog);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TCPNIOServerConnection bind(final String host, final int port,
            final int backlog) throws IOException {
        return bind(new InetSocketAddress(host, port), backlog);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TCPNIOServerConnection bind(final SocketAddress socketAddress)
            throws IOException {
        return bind(socketAddress, serverConnectionBackLog);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TCPNIOServerConnection bind(final SocketAddress socketAddress,
            final int backlog)
            throws IOException {
        state.getStateLocker().writeLock().lock();

        TCPNIOServerConnection serverConnection = null;
        final ServerSocketChannel serverSocketChannel =
                ServerSocketChannel.open();
        try {
            final ServerSocket serverSocket = serverSocketChannel.socket();
            serverSocket.setReuseAddress(reuseAddress);
            serverSocket.setSoTimeout(serverSocketSoTimeout);

            serverSocket.bind(socketAddress, backlog);

            serverSocketChannel.configureBlocking(false);

            serverConnection = obtainServerNIOConnection(serverSocketChannel);
            serverConnections.add(serverConnection);
            serverConnection.resetProperties();

            if (!isStopped()) {
                listenServerConnection(serverConnection);
            }

            return serverConnection;
        } catch (Exception e) {
            if (serverConnection != null) {
                serverConnections.remove(serverConnection);

                try {
                    serverConnection.close();
                } catch (IOException ignored) {
                }
            } else {
                try {
                    serverSocketChannel.close();
                } catch (IOException ignored) {
                }
            }
            
            throw Exceptions.makeIOException(e);
        } finally {
            state.getStateLocker().writeLock().unlock();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TCPNIOServerConnection bind(final String host,
            final PortRange portRange, final int backlog) throws IOException {

        IOException ioException = null;

        final int lower = portRange.getLower();
        final int range = portRange.getUpper() - lower + 1;
        
        int offset = RANDOM.nextInt(range);
        final int start = offset;

        do {
            final int port = lower + offset;

            try {
                final TCPNIOServerConnection serverConnection =
                        bind(host, port, backlog);
                return serverConnection;
            } catch (IOException e) {
                ioException = e;
            }

            offset = (offset + 1) % range;
        } while (offset != start);

        throw ioException;
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
        final SelectableChannel nioChannel = ((NIOConnection) connection).getChannel();

        if (nioChannel != null) {
            try {
                nioChannel.close();
            } catch (IOException e) {
                LOGGER.log(Level.FINE,
                        "TCPNIOTransport.closeChannel exception", e);
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

    TCPNIOConnection obtainNIOConnection(final SocketChannel channel) {
        final TCPNIOConnection connection = new TCPNIOConnection(this, channel);
        configureNIOConnection(connection);
        
        return connection;
    }

    TCPNIOServerConnection obtainServerNIOConnection(final ServerSocketChannel channel) {
        final TCPNIOServerConnection connection = new TCPNIOServerConnection(this, channel);
        configureNIOConnection(connection);

        return connection;
    }

    void configureNIOConnection(final TCPNIOConnection connection) {
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
    void configureChannel(final SocketChannel channel) throws IOException {
        final Socket socket = channel.socket();

        channel.configureBlocking(false);

        try {
            if (linger >= 0) {
                socket.setSoLinger(true, linger);
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Can not set linger to " + linger, e);
        }

        try {
            socket.setKeepAlive(isKeepAlive);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Can not set keepAlive to " + isKeepAlive, e);
        }
        
        try {
            socket.setTcpNoDelay(tcpNoDelay);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Can not set TcpNoDelay to " + tcpNoDelay, e);
        }
        socket.setReuseAddress(reuseAddress);
    }

    @Override
    public AsyncQueueIO<SocketAddress> getAsyncQueueIO() {
        return asyncQueueIO;
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
     * @param serverConnectionBackLog the default server connection backlog size.
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
    public TemporarySelectorIO getTemporarySelectorIO() {
        return temporarySelectorIO;
    }

    @Override
    public void setTemporarySelectorIO(final TemporarySelectorIO temporarySelectorIO) {
        this.temporarySelectorIO = temporarySelectorIO;
        notifyProbesConfigChanged(this);
    }

    @Override
    public IOEventReg fireIOEvent(final IOEvent ioEvent,
            final Connection connection,
            final IOEventProcessingHandler processingHandler)
            throws IOException {

        try {
            if (ioEvent == IOEvent.SERVER_ACCEPT) {
                ((TCPNIOServerConnection) connection).onAccept();
                return IOEventReg.REGISTER;
            } else if (ioEvent == IOEvent.CLIENT_CONNECTED) {
                ((TCPNIOConnection) connection).onConnect();
                return IOEventReg.REGISTER;
            }
            
            final Processor conProcessor = connection.obtainProcessor(ioEvent);

                if (ProcessorExecutor.execute(connection, ioEvent,
                        conProcessor, processingHandler)) {
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
    public Reader<SocketAddress> getReader(final Connection connection) {
        return getReader(connection.isBlocking());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Reader<SocketAddress> getReader(final boolean isBlocking) {
        if (isBlocking) {
            return getTemporarySelectorIO().getReader();
        } else {
            return getAsyncQueueIO().getReader();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Writer<SocketAddress> getWriter(final Connection connection) {
        return getWriter(connection.isBlocking());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Writer<SocketAddress> getWriter(final boolean isBlocking) {
        if (isBlocking) {
            return getTemporarySelectorIO().getWriter();
        } else {
            return getAsyncQueueIO().getWriter();
        }
    }

    public Buffer read(final Connection connection, Buffer buffer)
            throws IOException {

        final Thread currentThread = Thread.currentThread();
        final boolean isSelectorThread = (currentThread instanceof WorkerThread) &&
                ((WorkerThread) currentThread).isSelectorThread();
        
        final TCPNIOConnection tcpConnection = (TCPNIOConnection) connection;
        int read;

        final boolean isAllocate = (buffer == null);
        if (isAllocate) {
            buffer = memoryManager.allocateAtLeast(connection.getReadBufferSize());

            try {
                read = readSimple(tcpConnection, buffer, isSelectorThread);

                tcpConnection.onRead(buffer, read);
            } catch (Exception e) {
                if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.log(Level.FINE, "TCPNIOConnection (" + connection + ") (allocated) read exception", e);
                }
                read = -1;
            }

            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.log(Level.FINE, "TCPNIOConnection ({0}) (allocated) read {1} bytes",
                        new Object[]{connection, read});
            }
            
            if (read > 0) {
                buffer.position(read);
            } else {
                buffer.dispose();
                buffer = null;

                if (read < 0) {
                    throw new EOFException();
                }                
            }
        } else {
            if (buffer.hasRemaining()) {
                final int oldPos = buffer.position();
                
                final SocketChannel socketChannel =
                        (SocketChannel) tcpConnection.getChannel();
                
                if (buffer.isComposite()) {
                    final ByteBufferArray array = buffer.toByteBufferArray();
                    final ByteBuffer[] byteBuffers = array.getArray();
                    final int size = array.size();

                    if (!isSelectorThread) {
                        read = doReadInLoop(socketChannel, byteBuffers, 0, size);
                    } else {
                        read = (int) socketChannel.read(byteBuffers, 0, size);
                    }

                    array.restore();
                    array.recycle();
                } else {
                    read = readSimple(tcpConnection, buffer, isSelectorThread);
                }

                if (read > 0) {
                    buffer.position(oldPos + read);
                }


                tcpConnection.onRead(buffer, read);
                
                if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.log(Level.FINE, "TCPNIOConnection ({0}) (nonallocated) read {1} bytes", new Object[] {connection, read});
                }
                
                if (read < 0) {
                    throw new EOFException();
                }
            }
        }

        return buffer;
    }

    private int readSimple(final TCPNIOConnection tcpConnection,
            final Buffer buffer, final boolean isSelectorThread) throws IOException {

        final SocketChannel socketChannel = (SocketChannel) tcpConnection.getChannel();

        final int read;
        if (!buffer.isDirect()) {
            final DirectByteBufferRecord record = obtainDirectByteBuffer(
                    tcpConnection.getReadBufferSize());
            final ByteBuffer directByteBuffer = record.strongRef;
            final int length = Math.min(buffer.remaining(), directByteBuffer.remaining());

            try {
                // make sure we won't read more than buffer allows
                directByteBuffer.limit(directByteBuffer.position() + length);

                if (!isSelectorThread) {
                    read = doReadInLoop(socketChannel, directByteBuffer);
                } else {
                    read = socketChannel.read(directByteBuffer);
                }

                if (read > 0) {
                    directByteBuffer.flip();
                    buffer.put(directByteBuffer);
                }
            } finally {
                directByteBuffer.clear();
                releaseDirectByteBuffer(record);
            }

        } else {
            if (!isSelectorThread) {
                read = doReadInLoop(socketChannel, buffer.toByteBuffer());
            } else {
                read = socketChannel.read(buffer.toByteBuffer());
            }
        }

        return read;
    }
    
    private int doReadInLoop(final SocketChannel socketChannel,
            final ByteBuffer byteBuffer) throws IOException {
        int read = 0;
        int readAttempt = 0;
        int readNow;
        while ((readNow = socketChannel.read(byteBuffer)) >= 0) {
            read += readNow;
            if (!byteBuffer.hasRemaining()
                    || ++readAttempt >= maxReadAttempts) {
                return read;
            }
        }

        if (read == 0) {
            // Assign last readNow (may be -1)
            read = readNow;
        }

        return read;
    }
    
    private int doReadInLoop(final SocketChannel socketChannel,
            final ByteBuffer[] byteBuffers, final int offset, final int length) throws IOException {
        
        int read = 0;
        int readAttempt = 0;
        int readNow;
        final ByteBuffer lastByteBuffer = byteBuffers[length - 1];
        
        while ((readNow = (int) socketChannel.read(byteBuffers, offset, length)) >= 0) {
            read += readNow;
            if (!lastByteBuffer.hasRemaining()
                    || ++readAttempt >= maxReadAttempts) {
                return read;
            }
        }

        if (read == 0) {
            // Assign last readNow (may be -1)
            read = readNow;
        }
        
        return read;
    }

    public int write(Connection connection, Buffer buffer) throws IOException {
        return write(connection, buffer, null);
    }
    
    @SuppressWarnings("unchecked")
    public int write(Connection connection, Buffer buffer,
            WriteResult currentResult) throws IOException {

        final TCPNIOConnection tcpConnection = (TCPNIOConnection) connection;
        final int oldPos = buffer.position();
        
        int written;
        if (buffer.isComposite()) {
            final BufferArray array = buffer.toBufferArray();

            written = writeGathered(tcpConnection, array);

            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.log(Level.FINE, "TCPNIOConnection ({0}) (composite) write {1} bytes",
                        new Object[]{connection, written});
            }

            array.restore();
            array.recycle();
        } else {
            written = writeSimple(tcpConnection, buffer);
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.log(Level.FINE, "TCPNIOConnection ({0}) (plain) write {1} bytes",
                        new Object[]{connection, written});
            }
        }

        final boolean hasWritten = (written >= 0);
        if (hasWritten) {
            buffer.position(oldPos + written);
        }

        tcpConnection.onWrite(buffer, written);

        if (hasWritten) {
            if (currentResult != null) {
                currentResult.setMessage(buffer);
                currentResult.setWrittenSize(currentResult.getWrittenSize()
                        + written);
                currentResult.setDstAddress(
                        connection.getPeerAddress());
            }
        } else {
            throw new IOException("Error writing to peer");
        }

        return written;
    }

    int write0(final Connection connection, final BufferArray bufferArray)
    throws IOException {

        final TCPNIOConnection tcpConnection = (TCPNIOConnection) connection;
        final int written = writeGathered(tcpConnection, bufferArray);

        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.log(Level.FINE, "TCPNIOConnection ({0}) (composite) write {1} bytes",
                    new Object[]{connection, written});
        }

        return written;
    }

    int write0(final Connection connection, final Buffer buffer)
    throws IOException {

        final TCPNIOConnection tcpConnection = (TCPNIOConnection) connection;
        final int written = writeSimple(tcpConnection, buffer);
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.log(Level.FINE, "TCPNIOConnection ({0}) (plain) write {1} bytes",
                    new Object[]{connection, written});
        }

        return written;
    }
    
    private static int writeSimple(final TCPNIOConnection tcpConnection,
            final Buffer buffer) throws IOException {
        final SocketChannel socketChannel = (SocketChannel) tcpConnection.getChannel();

        if (!buffer.hasRemaining()) return 0;

        if (!buffer.isDirect()) {
            final int length = buffer.remaining();
            final DirectByteBufferRecord record = obtainDirectByteBuffer(length);
            final ByteBuffer directByteBuffer = record.strongRef;

            try {

                if (length < directByteBuffer.remaining()) {
                    directByteBuffer.limit(directByteBuffer.position() + length);
                }

                buffer.get(directByteBuffer);

                final int written = flushDirectByteBuffer(socketChannel, directByteBuffer);
                final int remaining = directByteBuffer.remaining();
                if (remaining > 0) {
                    buffer.position(buffer.position() - remaining);
                }

                return written;
            } finally {
                directByteBuffer.clear();
                releaseDirectByteBuffer(record);
            }

        } else {
            return socketChannel.write(buffer.toByteBuffer());
        }

    }
    
    private static int writeGathered(final TCPNIOConnection tcpConnection,
            final BufferArray bufferArray)
            throws IOException {

        final Buffer[] buffers = bufferArray.getArray();
        final int length = bufferArray.size();
        
        final SocketChannel socketChannel = (SocketChannel) tcpConnection.getChannel();

        int written = 0;
        DirectByteBufferRecord record = null;
        ByteBuffer directByteBuffer = null;

        int next;

        for (int i = findNextAvailBuffer(buffers, -1, length); i < length; i = next) {

            final Buffer buffer = buffers[i];
            next = findNextAvailBuffer(buffers, i, length);

            final boolean isFlush = next == length || buffers[next].isDirect();

            // If Buffer is not direct - copy it to the direct buffer and write
            if (!buffer.isDirect()) {
                if (record == null) {
                    record = obtainDirectByteBuffer(tcpConnection.getWriteBufferSize());
                    directByteBuffer = record.strongRef;
                }

                final int currentBufferRemaining = buffer.remaining();

                final boolean isAdaptByteBuffer =
                        currentBufferRemaining < directByteBuffer.remaining();


                if (isAdaptByteBuffer) {
                    directByteBuffer.limit(directByteBuffer.position() + currentBufferRemaining);
                }

                buffer.get(directByteBuffer);

                if (isAdaptByteBuffer) {
                    directByteBuffer.limit(directByteBuffer.capacity());
                }

                if (!directByteBuffer.hasRemaining() || isFlush) {
                    written += flushDirectByteBuffer(socketChannel, directByteBuffer);
                    int remaining = directByteBuffer.remaining();
                    if (remaining > 0) {
                        while (remaining > 0) {
                            final Buffer revertBuffer = buffers[i];
                            final int shift = Math.min(remaining,
                                    revertBuffer.position() - bufferArray.getInitialPosition(i));
                            revertBuffer.position(revertBuffer.position() - shift);
                            i--;
                            remaining -= shift;
                        }
                        
                        break;
                    }
                    
                    directByteBuffer.clear();

                    if (buffer.hasRemaining()) {
                        // continue the same buffer
                        next = i;
                    }
                }
            } else { // if it's direct buffer
                final ByteBuffer byteBuffer = buffer.toByteBuffer();
                written += socketChannel.write(byteBuffer);
                if (byteBuffer.hasRemaining()) {
                    break;
                }

            }
        }

        if (record != null) {
            directByteBuffer.clear();
            releaseDirectByteBuffer(record);
        }

        return written;
    }

    private static int findNextAvailBuffer(final Buffer[] buffers, final int start, final int end) {
        for (int i = start + 1; i < end; i++) {
            if (buffers[i].hasRemaining()) {
                return i;
            }
        }

        return end;
    }


    private static int flushDirectByteBuffer(final SocketChannel channel,
                                             final ByteBuffer directByteBuffer)
    throws IOException {
        
        directByteBuffer.flip();
        
        return channel.write(directByteBuffer);
    }

    private static final ThreadCache.CachedTypeIndex<DirectByteBufferRecord> CACHE_IDX =
            ThreadCache.obtainIndex("direct-buffer-cache", DirectByteBufferRecord.class, 1);
    
    private static DirectByteBufferRecord obtainDirectByteBuffer(final int size) {
        DirectByteBufferRecord record = ThreadCache.getFromCache(CACHE_IDX);
        final ByteBuffer byteBuffer;
        if (record != null) {
            if ((byteBuffer = record.switchToStrong()) != null) {
                if (byteBuffer.remaining() >= size) {
                    return record;
                }
            }
        } else {
            record = new DirectByteBufferRecord();
            ThreadCache.putToCache(CACHE_IDX, record);
        }

        record.reset(ByteBuffer.allocateDirect(size));
        return record;
    }

    private static void releaseDirectByteBuffer(
            final DirectByteBufferRecord directByteBufferRecord) {
        directByteBufferRecord.switchToSoft();
    }

    static final class DirectByteBufferRecord {
        private ByteBuffer strongRef;
        private SoftReference<ByteBuffer> softRef;

        void reset(ByteBuffer byteBuffer) {
            strongRef = byteBuffer;
            softRef = null;
        }

        ByteBuffer switchToStrong() {
            if (strongRef == null && softRef != null) {
                strongRef = softRef.get();
            }

            return strongRef;
        }

        void switchToSoft() {
            if (strongRef != null && softRef == null) {
                softRef = new SoftReference<ByteBuffer>(strongRef);
            }

            strongRef = null;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected JmxObject createJmxManagementObject() {
        return new org.glassfish.grizzly.nio.transport.jmx.TCPNIOTransport(this);
    }

    class RegisterChannelCompletionHandler
            extends EmptyCompletionHandler<RegisterChannelResult> {

        @Override
        public void completed(final RegisterChannelResult result) {
            final SelectionKey selectionKey = result.getSelectionKey();

            final TCPNIOConnection connection =
                    (TCPNIOConnection) getSelectionKeyHandler().
                    getConnectionForKey(selectionKey);

            if (connection != null) {
                final SelectorRunner selectorRunner = result.getSelectorRunner();
                connection.setSelectionKey(selectionKey);
                connection.setSelectorRunner(selectorRunner);
            }
        }
    }

    /**
     * Transport default {@link TCPNIOConnectorHandler}.
     */
     class TransportConnectorHandler extends TCPNIOConnectorHandler {
        public TransportConnectorHandler() {
            super(TCPNIOTransport.this);
        }

        @Override
        public Processor getProcessor() {
            return TCPNIOTransport.this.getProcessor();
        }

        @Override
        public ProcessorSelector getProcessorSelector() {
            return TCPNIOTransport.this.getProcessorSelector();
        }
    }
}
