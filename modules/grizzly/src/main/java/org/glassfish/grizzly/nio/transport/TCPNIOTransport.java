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

package org.glassfish.grizzly.nio.transport;

import java.io.EOFException;
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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.grizzly.*;
import org.glassfish.grizzly.asyncqueue.*;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.CompletionHandler;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.EmptyCompletionHandler;
import org.glassfish.grizzly.FileTransfer;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.GrizzlyFuture;
import org.glassfish.grizzly.PortRange;
import org.glassfish.grizzly.WriteResult;
import org.glassfish.grizzly.Writer;
import org.glassfish.grizzly.filterchain.Filter;
import org.glassfish.grizzly.filterchain.FilterChainEnabledTransport;
import org.glassfish.grizzly.localization.LogMessages;
import org.glassfish.grizzly.memory.CompositeBuffer;
import org.glassfish.grizzly.monitoring.MonitoringUtils;
import org.glassfish.grizzly.nio.*;
import org.glassfish.grizzly.nio.tmpselectors.TemporarySelectorIO;
import org.glassfish.grizzly.nio.tmpselectors.TemporarySelectorsEnabledTransport;

/**
 * TCP Transport NIO implementation
 * 
 * @author Alexey Stashok
 * @author Jean-Francois Arcand
 */
public final class TCPNIOTransport extends NIOTransport implements
        AsyncQueueEnabledTransport, FilterChainEnabledTransport,
        TemporarySelectorsEnabledTransport {

    static final Logger LOGGER = Grizzly.logger(TCPNIOTransport.class);
    
    /**
     * Default {@link ChannelConfigurator} used to configure client and server side
     * channels.
     */
    public static final ChannelConfigurator DEFAULT_CHANNEL_CONFIGURATOR =
            new DefaultChannelConfigurator();

    public static final int MAX_RECEIVE_BUFFER_SIZE =
            Integer.getInteger(TCPNIOTransport.class.getName() +
                    ".max-receive-buffer-size", Integer.MAX_VALUE);

    public static final int MAX_SEND_BUFFER_SIZE =
            Integer.getInteger(TCPNIOTransport.class.getName() +
                    ".max-send-buffer-size", Integer.MAX_VALUE);
    
    public static final boolean DEFAULT_TCP_NO_DELAY = true;
    public static final boolean DEFAULT_KEEP_ALIVE = true;
    public static final int DEFAULT_LINGER = -1;
    public static final int DEFAULT_SERVER_CONNECTION_BACKLOG = 4096;

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
     * The socket linger.
     */
    int linger = DEFAULT_LINGER;
    /**
     * The default server connection backlog size
     */
    int serverConnectionBackLog = DEFAULT_SERVER_CONNECTION_BACKLOG;
    /**
     * The socket tcpDelay.
     *
     * Default value for tcpNoDelay is disabled (set to true).
     */
    boolean tcpNoDelay = DEFAULT_TCP_NO_DELAY;
    /**
     * The socket keepAlive mode.
     */
    boolean isKeepAlive = DEFAULT_KEEP_ALIVE;

    private final Filter defaultTransportFilter;
    final RegisterChannelCompletionHandler selectorRegistrationHandler;

    /**
     * Default {@link TCPNIOConnectorHandler}
     */
    private final TCPNIOConnectorHandler connectorHandler =
            new TransportConnectorHandler();

    private final TCPNIOBindingHandler bindingHandler =
            new TCPNIOBindingHandler(this);

    public TCPNIOTransport() {
        this(DEFAULT_TRANSPORT_NAME);
    }

    TCPNIOTransport(final String name) {
        super(name != null ? name : DEFAULT_TRANSPORT_NAME);
        
        readBufferSize = DEFAULT_READ_BUFFER_SIZE;
        writeBufferSize = DEFAULT_WRITE_BUFFER_SIZE;

        selectorRegistrationHandler = new RegisterChannelCompletionHandler();

        asyncQueueIO = AsyncQueueIO.Factory.createImmutable(
                new TCPNIOAsyncQueueReader(this), new TCPNIOAsyncQueueWriter(this));

        attributeBuilder = Grizzly.DEFAULT_ATTRIBUTE_BUILDER;
        defaultTransportFilter = new TCPNIOTransportFilter(this);
        serverConnections = new ConcurrentLinkedQueue<TCPNIOServerConnection>();
    }

    @Override
    protected TemporarySelectorIO createTemporarySelectorIO() {
        return new TemporarySelectorIO(new TCPNIOTemporarySelectorReader(this),
                                       new TCPNIOTemporarySelectorWriter(this));
    }

    @Override
    protected void listen() {
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

    @Override
    protected int getDefaultSelectorRunnersCount() {
        // Consider ACCEPTOR will occupy one selector thread, and depending
        // on usecase it might be idle for most of the time -
        // so allocate one more extra thread to process channel events
        return Runtime.getRuntime().availableProcessors() + 1;
    }
    
    void listenServerConnection(TCPNIOServerConnection serverConnection)
            throws IOException {
        serverConnection.listen();
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
        
        return bindingHandler.bind(socketAddress, backlog);

    }

    
    /**
     * {@inheritDoc}
     */
    @Override
    public TCPNIOServerConnection bindToInherited() throws IOException {
        return bindingHandler.bindToInherited();
    }

    
    /**
     * {@inheritDoc}
     */
    @Override
    public TCPNIOServerConnection bind(final String host,
            final PortRange portRange, final int backlog) throws IOException {

        return (TCPNIOServerConnection) bindingHandler.bind(host, portRange, backlog);

    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public void unbind(Connection connection) {
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
    public void unbindAll() {
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

    @Override
    public ChannelConfigurator getChannelConfigurator() {
        final ChannelConfigurator cc = channelConfigurator;
        return cc != null ? cc : DEFAULT_CHANNEL_CONFIGURATOR;
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

    public boolean isKeepAlive() {
            return isKeepAlive;
        }

    @SuppressWarnings({"UnusedDeclaration"})
    public void setKeepAlive(final boolean isKeepAlive) {
        this.isKeepAlive = isKeepAlive;
        notifyProbesConfigChanged(this);
    }

    public boolean isTcpNoDelay() {
        return tcpNoDelay;
    }

    public void setTcpNoDelay(final boolean tcpNoDelay) {
        this.tcpNoDelay = tcpNoDelay;
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

    @Override
    public Filter getTransportFilter() {
        return defaultTransportFilter;
    }

    @Override
    public TemporarySelectorIO getTemporarySelectorIO() {
        return temporarySelectorIO;
    }

    @Override
    public void fireIOEvent(final IOEvent ioEvent,
            final Connection connection,
            final IOEventLifeCycleListener listener) {

        if (ioEvent == IOEvent.SERVER_ACCEPT) {
            try {
                ((TCPNIOServerConnection) connection).onAccept();
            } catch (IOException e) {
                failProcessingHandler(ioEvent, connection,
                        listener, e);
            }

            return;
        } else if (ioEvent == IOEvent.CLIENT_CONNECTED) {
            try {
                ((TCPNIOConnection) connection).onConnect();
            } catch (IOException e) {
                failProcessingHandler(ioEvent, connection,
                        listener, e);
            }

            return;
        }

        ProcessorExecutor.execute(
                Context.create(
                        connection,
                        connection.obtainProcessor(ioEvent),
                        ioEvent,
                        listener));
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
                final IOException e = new EOFException();
                // Mark connection as closed remotely.
                tcpConnection.terminate0(null,
                        new CloseReason(CloseType.REMOTELY, e));
                throw e;
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
                    final IOException e = new EOFException();
                    // Mark connection as closed remotely.
                    tcpConnection.terminate0(null,
                            new CloseReason(CloseType.REMOTELY, e));
                    throw e;
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

            try {
                written = buffer.isComposite() ?
                        TCPNIOUtils.writeCompositeBuffer(connection,
                            (CompositeBuffer) buffer) :
                        TCPNIOUtils.writeSimpleBuffer(connection, buffer);

                final boolean hasWritten = (written >= 0);

                connection.onWrite(buffer, written);

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
                connection.terminate0(null,
                        new CloseReason(CloseType.REMOTELY, e));
                throw e;
            }
        } else if (message instanceof FileTransfer) {
            written = (int) ((FileTransfer) message).writeTo((SocketChannel)
                                  connection.getChannel());
        } else {
            throw new IllegalStateException("Unhandled message type");
        }

        return written;
    }

    private static void failProcessingHandler(final IOEvent ioEvent,
            final Connection connection,
            final IOEventLifeCycleListener processingHandler,
            final IOException e) {
        if (processingHandler != null) {
            try {
                processingHandler.onError(Context.create(connection, null,
                        ioEvent, processingHandler), e);
            } catch (IOException ignored) {
            }
        }
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    protected Object createJmxManagementObject() {
        return MonitoringUtils.loadJmxObject(
                "org.glassfish.grizzly.nio.transport.jmx.TCPNIOTransport", this,
                TCPNIOTransport.class);
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
    
    private static class DefaultChannelConfigurator implements ChannelConfigurator {
        @Override
        public void preConfigure(NIOTransport transport,
                SelectableChannel channel) throws IOException {
            final TCPNIOTransport tcpNioTransport = (TCPNIOTransport) transport;
            if (channel instanceof SocketChannel) {
                final SocketChannel sc = (SocketChannel) channel;
                final Socket socket = sc.socket();

                sc.configureBlocking(false);
                
                final boolean reuseAddress = tcpNioTransport.isReuseAddress();
                try {
                    socket.setReuseAddress(reuseAddress);
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING,
                            LogMessages.WARNING_GRIZZLY_SOCKET_REUSEADDRESS_EXCEPTION(reuseAddress), e);
                }
            } else { // ServerSocketChannel
                final ServerSocketChannel serverSocketChannel
                        = (ServerSocketChannel) channel;
                final ServerSocket serverSocket = serverSocketChannel.socket();

                serverSocketChannel.configureBlocking(false);

                try {
                    serverSocket.setReuseAddress(tcpNioTransport.isReuseAddress());
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING,
                            LogMessages.WARNING_GRIZZLY_SOCKET_REUSEADDRESS_EXCEPTION(tcpNioTransport.isReuseAddress()), e);
                }
            }
        }

        @Override
        public void postConfigure(final NIOTransport transport,
                final SelectableChannel channel) throws IOException {
            
            final TCPNIOTransport tcpNioTransport = (TCPNIOTransport) transport;
            if (channel instanceof SocketChannel) {
                final SocketChannel sc = (SocketChannel) channel;
                final Socket socket = sc.socket();

                final int linger = tcpNioTransport.getLinger();
                try {
                    if (linger >= 0) {
                        socket.setSoLinger(true, linger);
                    }
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING,
                            LogMessages.WARNING_GRIZZLY_SOCKET_LINGER_EXCEPTION(linger), e);
                }

                final boolean keepAlive = tcpNioTransport.isKeepAlive();
                try {
                    socket.setKeepAlive(keepAlive);
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING,
                            LogMessages.WARNING_GRIZZLY_SOCKET_KEEPALIVE_EXCEPTION(keepAlive), e);
                }

                final boolean tcpNoDelay = tcpNioTransport.isTcpNoDelay();
                try {
                    socket.setTcpNoDelay(tcpNoDelay);
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING,
                            LogMessages.WARNING_GRIZZLY_SOCKET_TCPNODELAY_EXCEPTION(tcpNoDelay), e);
                }

                final int clientSocketSoTimeout = tcpNioTransport.getClientSocketSoTimeout();
                try {
                    socket.setSoTimeout(clientSocketSoTimeout);
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING, LogMessages.WARNING_GRIZZLY_SOCKET_TIMEOUT_EXCEPTION(tcpNioTransport.getClientSocketSoTimeout()), e);
                }
            } else { //ServerSocketChannel
                final ServerSocketChannel serverSocketChannel =
                        (ServerSocketChannel) channel;
                final ServerSocket serverSocket = serverSocketChannel.socket();

                try {
                    serverSocket.setSoTimeout(tcpNioTransport.getServerSocketSoTimeout());
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING,
                            LogMessages.WARNING_GRIZZLY_SOCKET_TIMEOUT_EXCEPTION(tcpNioTransport.getServerSocketSoTimeout()), e);
                }
            }
        }
    }
}
