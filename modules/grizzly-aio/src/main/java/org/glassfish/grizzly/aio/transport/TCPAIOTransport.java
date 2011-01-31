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
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.GrizzlyFuture;
import org.glassfish.grizzly.PostProcessor;
import org.glassfish.grizzly.ProcessorExecutor;
import org.glassfish.grizzly.Reader;
import org.glassfish.grizzly.SocketBinder;
import org.glassfish.grizzly.StandaloneProcessor;
import org.glassfish.grizzly.StandaloneProcessorSelector;
import org.glassfish.grizzly.WriteResult;
import org.glassfish.grizzly.Writer;
import org.glassfish.grizzly.filterchain.Filter;
import org.glassfish.grizzly.monitoring.jmx.JmxObject;
import org.glassfish.grizzly.strategies.WorkerThreadIOStrategy;
import org.glassfish.grizzly.threadpool.GrizzlyExecutorService;
import org.glassfish.grizzly.threadpool.WorkerThread;
import java.io.EOFException;
import java.lang.ref.SoftReference;
import java.net.StandardSocketOption;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousChannel;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.glassfish.grizzly.SocketConnectorHandler;
import org.glassfish.grizzly.ThreadCache;
import org.glassfish.grizzly.memory.BufferArray;
import org.glassfish.grizzly.memory.ByteBufferArray;

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
    AsyncQueueIO asyncQueueIO;
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
    
    public TCPAIOTransport() {
        this(DEFAULT_TRANSPORT_NAME);
    }

    TCPAIOTransport(final String name) {
        super(name);
        
        readBufferSize = DEFAULT_READ_BUFFER_SIZE;
        writeBufferSize = DEFAULT_WRITE_BUFFER_SIZE;

//        selectorRegistrationHandler = new RegisterChannelCompletionHandler();

        asyncQueueIO = new AsyncQueueIO(new TCPAIOAsyncQueueReader(this),
                new TCPAIOAsyncQueueWriter(this));

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

            if (selectorPool == null) {
                setSelectorPool0(GrizzlyExecutorService.createInstance(selectorConfig));
            }

            if (threadPool == null) {
                if (workerConfig != null) {
                    workerConfig.getInitialMonitoringConfig().addProbes(
                        getThreadPoolMonitoringConfig().getProbes());
                    setThreadPool0(GrizzlyExecutorService.createInstance(workerConfig));
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

            if (threadPool != null && managedWorkerPool) {
                threadPool.shutdown();
                threadPool = null;
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
                    AsynchronousServerSocketChannel.open();
            
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
            return getTemporarySelectorIO().getReader();
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
            return getTemporarySelectorIO().getWriter();
        } else {
            return getAsyncQueueIO().getWriter();
        }
    }

    public void read(final Connection connection, Buffer buffer,
            final Object attachment) throws IOException {

        final TCPAIOConnection tcpConnection = (TCPAIOConnection) connection;
        int read;

        final boolean isAllocate = (buffer == null);
        if (isAllocate) {
            buffer = memoryManager.allocateAtLeast(connection.getReadBufferSize());

            try {
                readSimple(tcpConnection, buffer);

                tcpConnection.onRead(buffer, read);
            } catch (Exception e) {
                if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.log(Level.FINE, "TCPAIOConnection (" + connection + ") (allocated) read exception", e);
                }
                read = -1;
            }

            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.log(Level.FINE, "TCPAIOConnection ({0}) (allocated) read {1} bytes",
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
                
                final AsynchronousSocketChannel socketChannel =
                        (AsynchronousSocketChannel) tcpConnection.getChannel();
                
                if (buffer.isComposite()) {
                    readComposite(tcpConnection, buffer);
                } else {
                    readSimple(tcpConnection, buffer);
                }

                if (read > 0) {
                    buffer.position(oldPos + read);
                }


                tcpConnection.onRead(buffer, read);
                
                if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.log(Level.FINE, "TCPAIOConnection ({0}) (nonallocated) read {1} bytes", new Object[] {connection, read});
                }
                
                if (read < 0) {
                    throw new EOFException();
                }
            }
        }

        return buffer;
    }

    private <A> void readSimple(final TCPAIOConnection tcpConnection,
            final Buffer buffer, final A attachment,
            final java.nio.channels.CompletionHandler<Integer, A> completionHandler)
            throws IOException {

        final AsynchronousSocketChannel socketChannel =
                (AsynchronousSocketChannel) tcpConnection.getChannel();

        socketChannel.read(buffer.toByteBuffer(), attachment, completionHandler);
    }
    
    private <A> void readComposite(final TCPAIOConnection tcpConnection,
            final ByteBuffer[] byteBuffers, final int offset, final int size,
            final A attachment,
            final java.nio.channels.CompletionHandler<Long, A> completionHandler)
            throws IOException {
//        final ByteBufferArray array = buffer.toByteBufferArray();
//
//        final ByteBuffer[] byteBuffers = array.getArray();
//        final int size = array.size();

        final AsynchronousSocketChannel socketChannel =
                (AsynchronousSocketChannel) tcpConnection.getChannel();
        
        socketChannel.read(byteBuffers, 0, size, Long.MAX_VALUE,
                TimeUnit.MILLISECONDS, attachment, completionHandler);

//        array.restore();
//        array.recycle();
    }
    
    public int write(Connection connection, Buffer buffer) throws IOException {
        return write(connection, buffer, null);
    }

    public int write(Connection connection, Buffer buffer,
            WriteResult currentResult) throws IOException {

        final TCPAIOConnection tcpConnection = (TCPAIOConnection) connection;
        final int oldPos = buffer.position();
        
        int written;
        if (buffer.isComposite()) {
            final BufferArray array = buffer.toBufferArray();

            written = writeGathered(tcpConnection, array);

            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.log(Level.FINE, "TCPAIOConnection ({0}) (composite) write {1} bytes",
                        new Object[]{connection, written});
            }

            array.restore();
            array.recycle();
        } else {
            written = writeSimple(tcpConnection, buffer);
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.log(Level.FINE, "TCPAIOConnection ({0}) (plain) write {1} bytes",
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

        final TCPAIOConnection tcpConnection = (TCPAIOConnection) connection;
        final int written = writeGathered(tcpConnection, bufferArray);

        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.log(Level.FINE, "TCPAIOConnection ({0}) (composite) write {1} bytes",
                    new Object[]{connection, written});
        }

        return written;
    }

    int write0(final Connection connection, final Buffer buffer)
    throws IOException {

        final TCPAIOConnection tcpConnection = (TCPAIOConnection) connection;
        final int written = writeSimple(tcpConnection, buffer);
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.log(Level.FINE, "TCPAIOConnection ({0}) (plain) write {1} bytes",
                    new Object[]{connection, written});
        }

        return written;
    }
    
    private static int writeSimple(final TCPAIOConnection tcpConnection,
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

    private static int writeGathered(final TCPAIOConnection tcpConnection,
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
        DirectByteBufferRecord record = ThreadCache.takeFromCache(CACHE_IDX);
        final ByteBuffer byteBuffer;
        if (record != null) {
            if ((byteBuffer = record.switchToStrong()) != null) {
                if (byteBuffer.remaining() >= size) {
                    return record;
                }
            }
        } else {
            record = new DirectByteBufferRecord();
        }

        record.reset(ByteBuffer.allocateDirect(size));
        return record;
    }

    private static void releaseDirectByteBuffer(
            final DirectByteBufferRecord directByteBufferRecord) {
        directByteBufferRecord.switchToSoft();
        ThreadCache.putToCache(CACHE_IDX, directByteBufferRecord);
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
                softRef = new SoftReference<>(strongRef);
            }

            strongRef = null;
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
