/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2012 Oracle and/or its affiliates. All rights reserved.
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

import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.channels.Channel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Collection;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.CompletionHandler;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Context;
import org.glassfish.grizzly.EmptyCompletionHandler;
import org.glassfish.grizzly.Event;
import org.glassfish.grizzly.EventProcessingHandler;
import org.glassfish.grizzly.FileTransfer;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.GrizzlyFuture;
import org.glassfish.grizzly.PortRange;
import org.glassfish.grizzly.Processor;
import org.glassfish.grizzly.SocketBinder;
import org.glassfish.grizzly.SocketConnectorHandler;
import org.glassfish.grizzly.WritableMessage;
import org.glassfish.grizzly.WriteResult;
import org.glassfish.grizzly.Writer;
import org.glassfish.grizzly.filterchain.Filter;
import org.glassfish.grizzly.filterchain.FilterChain;
import org.glassfish.grizzly.filterchain.FilterChainEnabledTransport;
import org.glassfish.grizzly.localization.LogMessages;
import org.glassfish.grizzly.memory.CompositeBuffer;
import org.glassfish.grizzly.monitoring.jmx.JmxObject;
import org.glassfish.grizzly.nio.AbstractNIOAsyncQueueWriter;
import org.glassfish.grizzly.nio.DefaultSelectorHandler;
import org.glassfish.grizzly.nio.NIOConnection;
import org.glassfish.grizzly.nio.NIOTransport;
import org.glassfish.grizzly.nio.RegisterChannelResult;
import org.glassfish.grizzly.nio.RoundRobinConnectionDistributor;
import org.glassfish.grizzly.nio.SelectorRunner;
import org.glassfish.grizzly.nio.tmpselectors.TemporarySelectorIO;
import org.glassfish.grizzly.nio.tmpselectors.TemporarySelectorPool;
import org.glassfish.grizzly.strategies.SameThreadIOStrategy;
import org.glassfish.grizzly.strategies.WorkerThreadIOStrategy;
import org.glassfish.grizzly.threadpool.AbstractThreadPool;
import org.glassfish.grizzly.threadpool.GrizzlyExecutorService;
import org.glassfish.grizzly.utils.Exceptions;

/**
 * TCP NIO Transport implementation
 * 
 * @author Alexey Stashok
 * @author Jean-Francois Arcand
 */
public class TCPNIOTransport extends NIOTransport
        implements SocketBinder, SocketConnectorHandler,
        FilterChainEnabledTransport {

    static final Logger LOGGER = Grizzly.logger(TCPNIOTransport.class);

    private static final int DEFAULT_READ_BUFFER_SIZE = -1;
    private static final int DEFAULT_WRITE_BUFFER_SIZE = -1;
    
    private static final String DEFAULT_TRANSPORT_NAME = "TCPNIOTransport";
    /**
     * The Server connections.
     */
    protected final Collection<TCPNIOServerConnection> serverConnections;
    /**
     * Transport async write queue
     */
    final TCPNIOAsyncQueueWriter asyncQueueWriter;
    /**
     * Transport TemporarySelectorIO, used for blocking I/O simulation
     */
    final TemporarySelectorIO temporarySelectorIO;
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

        asyncQueueWriter = new TCPNIOAsyncQueueWriter(this);

        temporarySelectorIO = new TemporarySelectorIO(
                new TCPNIOTemporarySelectorReader(this),
                new TCPNIOTemporarySelectorWriter(this));

        attributeBuilder = Grizzly.DEFAULT_ATTRIBUTE_BUILDER;
        defaultTransportFilter = new TCPNIOTransportFilter(this);
        serverConnections = new ConcurrentLinkedQueue<TCPNIOServerConnection>();
    }

    @Override
    public void start() throws IOException {
        final Lock lock = state.getStateLocker().writeLock();
        lock.lock();
        try {
            State currentState = state.getState();
            if (currentState != State.STOP) {
                LOGGER.log(Level.WARNING,
                        LogMessages.WARNING_GRIZZLY_TRANSPORT_NOT_STOP_OR_BOUND_STATE_EXCEPTION());
            }

            state.setState(State.STARTING);

            super.start();
            
            if (selectorHandler == null) {
                selectorHandler = new DefaultSelectorHandler();
            }

            if (filterChain == null) {
                throw new IllegalStateException("No processor available.");
            }

            final int selectorRunnersCount = getSelectorRunnersCount();

            if (nioChannelDistributor == null) {
                nioChannelDistributor = new RoundRobinConnectionDistributor(this);
            }

            if (kernelPool == null) {
                kernelPoolConfig.setMemoryManager(memoryManager);
                setKernelPool0(GrizzlyExecutorService.createInstance(kernelPoolConfig));
            }

            if (workerThreadPool == null) {
                if (workerPoolConfig != null) {
                    if (getThreadPoolMonitoringConfig().hasProbes()) {
                        workerPoolConfig.getInitialMonitoringConfig().addProbes(
                            getThreadPoolMonitoringConfig().getProbes());
                    }
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
                    new TemporarySelectorPool(selectorProvider, selectorPoolSize));

            startSelectorRunners();

            listenServerConnections();

            state.setState(State.START);

            notifyProbesStart(this);
        } finally {
            lock.unlock();
        }
    }

    @Override
    protected int getDefaultSelectorRunnersCount() {
        // Consider ACCEPTOR will occupy one selector thread, and depending
        // on usecase it might be idle for most of the time -
        // so allocate one more extra thread to process channel events
        return Runtime.getRuntime().availableProcessors() + 1;
    }
    
    private void listenServerConnections() {
        for (TCPNIOServerConnection serverConnection : serverConnections) {
            try {
                listenServerConnection(serverConnection);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING,
                        LogMessages.WARNING_GRIZZLY_TRANSPORT_START_SERVER_CONNECTION_EXCEPTION(serverConnection),
                        e);
            }
        }
    }

    private void listenServerConnection(TCPNIOServerConnection serverConnection)
            throws IOException {
        serverConnection.listen();
    }

    @Override
    public void stop() throws IOException {
        final Lock lock = state.getStateLocker().writeLock();
        lock.lock();
        try {
            if (state.getState() == State.PAUSE) {
                // if Transport is paused - first we need to resume it
                // so selectorrunners can perform the close phase
                resume();
            }
            
            unbindAll();
            state.setState(State.STOP);

            stopSelectorRunners();

            if (workerThreadPool != null && managedWorkerPool) {
                workerThreadPool.shutdown();
                workerThreadPool = null;
            }

            if (kernelPool != null) {
                kernelPool.shutdownNow();
                kernelPool = null;
            }

            notifyProbesStop(this);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void pause() throws IOException {
        final Lock lock = state.getStateLocker().writeLock();
        lock.lock();
        try {
            if (state.getState() != State.START) {
                LOGGER.log(Level.WARNING,
                        LogMessages.WARNING_GRIZZLY_TRANSPORT_NOT_START_STATE_EXCEPTION());
            }
            state.setState(State.PAUSE);
            notifyProbesPause(this);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void resume() throws IOException {
        final Lock lock = state.getStateLocker().writeLock();
        lock.lock();
        try {
            if (state.getState() != State.PAUSE) {
                LOGGER.log(Level.WARNING,
                        LogMessages.WARNING_GRIZZLY_TRANSPORT_NOT_PAUSE_STATE_EXCEPTION());
            }
            state.setState(State.START);
            notifyProbesResume(this);
        } finally {
            lock.unlock();
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
        
        TCPNIOServerConnection serverConnection = null;
        final ServerSocketChannel serverSocketChannel =
                selectorProvider.openServerSocketChannel();
        
        final Lock lock = state.getStateLocker().writeLock();
        lock.lock();
        try {
            final ServerSocket serverSocket = serverSocketChannel.socket();
            
            try {
                serverSocket.setReuseAddress(reuseAddress);
            } catch (IOException e) {
                LOGGER.log(Level.WARNING,
                        LogMessages.WARNING_GRIZZLY_SOCKET_REUSEADDRESS_EXCEPTION(reuseAddress), e);
            }

            try {
                serverSocket.setSoTimeout(serverSocketSoTimeout);
            } catch (IOException e) {
                LOGGER.log(Level.WARNING,
                        LogMessages.WARNING_GRIZZLY_SOCKET_TIMEOUT_EXCEPTION(serverSocketSoTimeout), e);
            }
            

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

                serverConnection.closeSilently();
            } else {
                try {
                    serverSocketChannel.close();
                } catch (IOException ignored) {
                }
            }
            
            throw Exceptions.makeIOException(e);
        } finally {
            lock.unlock();
        }
    }

    
    /**
     * {@inheritDoc}
     */
    @Override
    public TCPNIOServerConnection bindToInherited() throws IOException {
        TCPNIOServerConnection serverConnection = null;
        final Channel inheritedChannel = System.inheritedChannel();
        
        if (inheritedChannel == null) {
            throw new IOException("Inherited channel is not set");
        }
        if (!(inheritedChannel instanceof ServerSocketChannel)) {
            throw new IOException("Inherited channel is not java.nio.channels.ServerSocketChannel, but " + inheritedChannel.getClass().getName());
        }
        
        final ServerSocketChannel serverSocketChannel = (ServerSocketChannel) inheritedChannel;
        
        final Lock lock = state.getStateLocker().writeLock();
        lock.lock();
        try {
            
            final ServerSocket serverSocket = serverSocketChannel.socket();
            
            try {
                serverSocket.setReuseAddress(reuseAddress);
            } catch (IOException e) {
                LOGGER.log(Level.WARNING,
                        LogMessages.WARNING_GRIZZLY_SOCKET_REUSEADDRESS_EXCEPTION(reuseAddress), e);
            }

            try {
                serverSocket.setSoTimeout(serverSocketSoTimeout);
            } catch (IOException e) {
                LOGGER.log(Level.WARNING,
                        LogMessages.WARNING_GRIZZLY_SOCKET_TIMEOUT_EXCEPTION(serverSocketSoTimeout), e);
            }
            
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

                serverConnection.closeSilently();
            } else {
                try {
                    serverSocketChannel.close();
                } catch (IOException ignored) {
                }
            }
            
            throw Exceptions.makeIOException(e);
        } finally {
            lock.unlock();
        }
    }

    
    /**
     * {@inheritDoc}
     */
    @Override
    public TCPNIOServerConnection bind(final String host,
            final PortRange portRange, final int backlog) throws IOException {

        IOException ioException;

        final int lower = portRange.getLower();
        final int range = portRange.getUpper() - lower + 1;
        
        int offset = RANDOM.nextInt(range);
        final int start = offset;

        do {
            final int port = lower + offset;

            try {
                return bind(host, port, backlog);
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
    public void unbind(final Connection connection) throws IOException {
        final Lock lock = state.getStateLocker().writeLock();
        lock.lock();
        try {
            //noinspection SuspiciousMethodCalls
            if (connection != null
                    && serverConnections.remove(connection)) {
                final GrizzlyFuture future = connection.close();
                try {
                    future.get(1000, TimeUnit.MILLISECONDS);
                    future.recycle(false);
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING,
                            LogMessages.WARNING_GRIZZLY_TRANSPORT_UNBINDING_CONNECTION_EXCEPTION(connection),
                            e);
                }
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void unbindAll() throws IOException {
        final Lock lock = state.getStateLocker().writeLock();
        lock.lock();
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
            lock.unlock();
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
     */
    @Override
    public GrizzlyFuture<Connection> connect(final String host, final int port) {
        return connectorHandler.connect(host, port);
    }

    /**
     * Creates, initializes and connects socket to the specific
     * {@link SocketAddress} and returns {@link Connection}, representing socket.
     *
     * @param remoteAddress remote address to connect to.
     * @return {@link GrizzlyFuture} of connect operation, which could be used to get
     * resulting {@link Connection}.
     */
    @Override
    public GrizzlyFuture<Connection> connect(final SocketAddress remoteAddress) {
        return connectorHandler.connect(remoteAddress);
    }

    /**
     * Creates, initializes and connects socket to the specific
     * {@link SocketAddress} and returns {@link Connection}, representing socket.
     *
     * @param remoteAddress remote address to connect to.
     * @param completionHandler {@link CompletionHandler}.
     */
    @Override
    public void connect(final SocketAddress remoteAddress,
            final CompletionHandler<Connection> completionHandler) {
        connectorHandler.connect(remoteAddress, completionHandler);
    }

    /**
     * Creates, initializes socket, binds it to the specific local and remote
     * {@link SocketAddress} and returns {@link Connection}, representing socket.
     *
     * @param remoteAddress remote address to connect to.
     * @param localAddress local address to bind socket to.
     * @return {@link GrizzlyFuture} of connect operation, which could be used to get
     * resulting {@link Connection}.
     */
    @Override
    public GrizzlyFuture<Connection> connect(final SocketAddress remoteAddress,
            final SocketAddress localAddress) {
        return connectorHandler.connect(remoteAddress, localAddress);
    }

    /**
     * Creates, initializes socket, binds it to the specific local and remote
     * {@link SocketAddress} and returns {@link Connection}, representing socket.
     *
     * @param remoteAddress remote address to connect to.
     * @param localAddress local address to bind socket to.
     * @param completionHandler {@link CompletionHandler}.
     */
    @Override
    public void connect(final SocketAddress remoteAddress,
            final SocketAddress localAddress,
            final CompletionHandler<Connection> completionHandler) {
        connectorHandler.connect(remoteAddress, localAddress,
                completionHandler);
    }

    @Override
    protected void closeConnection(final NIOConnection connection)
            throws IOException {
        
        final SelectableChannel nioChannel = connection.getChannel();

        if (nioChannel != null) {
            try {
                nioChannel.close();
            } catch (IOException e) {
                LOGGER.log(Level.FINE,
                        "TCPNIOTransport.closeChannel exception", e);
            }
        }

        asyncQueueWriter.onClose(connection);
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
        connection.setFilterChain(filterChain);
        if (connectionMonitoringConfig.hasProbes()) {
            connection.setMonitoringProbes(connectionMonitoringConfig.getProbes());
        }
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
            LOGGER.log(Level.WARNING,
                    LogMessages.WARNING_GRIZZLY_SOCKET_LINGER_EXCEPTION(linger), e);
        }

        try {
            socket.setKeepAlive(isKeepAlive);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING,
                    LogMessages.WARNING_GRIZZLY_SOCKET_KEEPALIVE_EXCEPTION(isKeepAlive), e);
        }
        
        try {
            socket.setTcpNoDelay(tcpNoDelay);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING,
                    LogMessages.WARNING_GRIZZLY_SOCKET_TCPNODELAY_EXCEPTION(tcpNoDelay), e);
        }
        
        try {
            socket.setReuseAddress(reuseAddress);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING,
                    LogMessages.WARNING_GRIZZLY_SOCKET_REUSEADDRESS_EXCEPTION(reuseAddress), e);
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

    /**
     * {@inheritDoc}
     */
    @Override
    protected Writer<SocketAddress> getWriter(final Connection connection) {
        return super.getWriter(connection);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Writer<SocketAddress> getWriter(final boolean isBlocking) {
        return super.getWriter(isBlocking);
    }
    
    @Override
    protected final AbstractNIOAsyncQueueWriter getAsyncQueueWriter() {
        return asyncQueueWriter;
    }

    @Override
    protected TemporarySelectorIO getTemporarySelectorIO() {
        return temporarySelectorIO;
    }

    @Override
    protected boolean processOpAccept(NIOConnection connection)
            throws IOException {
        ((TCPNIOServerConnection) connection).onAccept();
        return true;
    }

    @Override
    protected boolean processOpConnect(NIOConnection connection)
            throws IOException {
        ((TCPNIOConnection) connection).onConnect();
        return true;
    }
//    
//    /**
//     * {@inheritDoc}
//     */
//    @Override
//    public Reader<SocketAddress> getReader(final Connection connection) {
//        return getReader(connection.isBlocking());
//    }
//
//    /**
//     * {@inheritDoc}
//     */
//    @Override
//    public Reader<SocketAddress> getReader(final boolean isBlocking) {
//        if (isBlocking) {
//            return getTemporarySelectorIO().getReader();
//        } else {
//            return getAsyncQueueIO().getReader();
//        }
//    }

    public Buffer read(final Connection connection, Buffer buffer)
            throws IOException {

        final TCPNIOConnection tcpConnection = (TCPNIOConnection) connection;
        int read;

        final boolean isAllocate = (buffer == null);
        if (isAllocate) {
            try {
                buffer = TCPNIOUtils.allocateAndReadBuffer(tcpConnection);
                read = buffer.position();
                tcpConnection.onRead(buffer, read);
            } catch (Exception e) {
                if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.log(Level.FINE, "TCPNIOConnection (" + connection + ") (allocated) read exception", e);
                }
                
                read = -1;
            }

            if (read == 0) {
                buffer = null;
            } else if (read < 0) {
                // Mark connection as closed remotely.
                tcpConnection.close(null, false);
                throw new EOFException();
            }
        } else {
            if (buffer.hasRemaining()) {
                try {
                    read = TCPNIOUtils.readBuffer(tcpConnection, buffer);
                } catch (Exception e) {
                    if (LOGGER.isLoggable(Level.FINE)) {
                        LOGGER.log(Level.FINE, "TCPNIOConnection (" + connection + ") (existing) read exception", e);
                    }
                    read = -1;
                }
                
                tcpConnection.onRead(buffer, read);
                
                if (read < 0) {
                    // Mark connection as closed remotely.
                    tcpConnection.close(null, false);
                    throw new EOFException();
                }
            }
        }

        return buffer;
    }

    public int write(final TCPNIOConnection connection, final WritableMessage message)
            throws IOException {
        return write(connection, message, null);
    }

    @SuppressWarnings("unchecked")
    public int write(final TCPNIOConnection connection, final WritableMessage message,
            final WriteResult currentResult) throws IOException {

        final int written;
        if (message.remaining() == 0) {
            written = 0;
        } else if (message instanceof Buffer) {
            final Buffer buffer = (Buffer) message;
            final TCPNIOConnection tcpConnection = (TCPNIOConnection) connection;

            try {
                if (buffer.isComposite()) {
                    written = TCPNIOUtils.writeCompositeBuffer(tcpConnection,
                            (CompositeBuffer) buffer);
                } else {
                    written = TCPNIOUtils.writeSimpleBuffer(tcpConnection,
                            buffer);
                }

                final boolean hasWritten = (written >= 0);

                tcpConnection.onWrite(buffer, written);

                if (hasWritten) {
                    if (currentResult != null) {
                        currentResult.setMessage(message);
                        currentResult.setWrittenSize(currentResult.getWrittenSize()
                                + written);
                        currentResult.setDstAddressHolder(
                                connection.peerSocketAddressHolder);
                    }
                }
            } catch (IOException e) {
                // Mark connection as closed remotely.
                tcpConnection.close(null, false);
                throw e;
            }
        } else if (message instanceof FileTransfer) {
            written = (int) ((FileTransfer) message).writeTo((SocketChannel)
                                  ((TCPNIOConnection) connection).getChannel());
        } else {
            throw new IllegalStateException("Unhandled message type");
        }

        return written;
    }
    
    private static void failProcessingHandler(final Event event,
            final Connection connection,
            final EventProcessingHandler processingHandler,
            final IOException e) {
        if (processingHandler != null) {
            try {
                processingHandler.onError(Context.create(connection, null,
                        event, processingHandler), e);
            } catch (IOException ignored) {
            }
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
                    (TCPNIOConnection) getConnectionForKey(selectionKey);

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
        public FilterChain getFilterChain() {
            return TCPNIOTransport.this.getFilterChain();
        }
    }
}
