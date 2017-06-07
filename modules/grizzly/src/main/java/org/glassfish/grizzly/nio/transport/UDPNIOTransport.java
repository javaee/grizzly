/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2017 Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.util.Collection;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.Closeable;
import org.glassfish.grizzly.CompletionHandler;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.EmptyCompletionHandler;
import org.glassfish.grizzly.FileTransfer;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.GrizzlyFuture;
import org.glassfish.grizzly.PortRange;
import org.glassfish.grizzly.ReadResult;
import org.glassfish.grizzly.WritableMessage;
import org.glassfish.grizzly.WriteResult;
import org.glassfish.grizzly.Writer;
import org.glassfish.grizzly.filterchain.Filter;
import org.glassfish.grizzly.filterchain.FilterChain;
import org.glassfish.grizzly.impl.FutureImpl;
import org.glassfish.grizzly.localization.LogMessages;
import org.glassfish.grizzly.memory.ByteBufferArray;
import org.glassfish.grizzly.monitoring.MonitoringUtils;
import org.glassfish.grizzly.nio.AbstractNIOAsyncQueueWriter;
import org.glassfish.grizzly.nio.ChannelConfigurator;
import org.glassfish.grizzly.nio.DirectByteBufferRecord;
import org.glassfish.grizzly.nio.NIOConnection;
import org.glassfish.grizzly.nio.NIOTransport;
import org.glassfish.grizzly.nio.RegisterChannelResult;
import org.glassfish.grizzly.nio.SelectorRunner;
import org.glassfish.grizzly.nio.tmpselectors.TemporarySelectorIO;
import org.glassfish.grizzly.utils.Exceptions;
import org.glassfish.grizzly.utils.Futures;

/**
 * UDP NIO transport implementation
 * 
 *
 */
public final class UDPNIOTransport extends NIOTransport {
    /**
     * Default {@link ChannelConfigurator} used to configure client and server side
     * channels.
     */
    public static final ChannelConfigurator DEFAULT_CHANNEL_CONFIGURATOR =
            new DefaultChannelConfigurator();

    private static final Logger LOGGER = Grizzly.logger(UDPNIOTransport.class);
    private static final String DEFAULT_TRANSPORT_NAME = "UDPNIOTransport";

    /**
     * The Server connections.
     */
    protected final Collection<UDPNIOServerConnection> serverConnections;
    /**
     * Transport async write queue
     */
    final UDPNIOAsyncQueueWriter asyncQueueWriter;
    /**
     * Server socket backlog.
     */
    private final Filter transportFilter;
    protected final RegisterChannelCompletionHandler registerChannelCompletionHandler;
    /**
     * Default {@link TCPNIOConnectorHandler}
     */
    private final UDPNIOConnectorHandler connectorHandler =
            new TransportConnectorHandler();

    public UDPNIOTransport() {
        this(DEFAULT_TRANSPORT_NAME);
    }

    public UDPNIOTransport(String name) {
        super(name != null ? name : DEFAULT_TRANSPORT_NAME);

        readBufferSize = -1;
        writeBufferSize = -1;

        registerChannelCompletionHandler = new RegisterChannelCompletionHandler();

        asyncQueueWriter = new UDPNIOAsyncQueueWriter(this);

        transportFilter = new UDPNIOTransportFilter(this);
        serverConnections = new ConcurrentLinkedQueue<UDPNIOServerConnection>();
    }

    @Override
    public TemporarySelectorIO createTemporarySelectorIO() {
        return new TemporarySelectorIO(new UDPNIOTemporarySelectorReader(this),
                                       new UDPNIOTemporarySelectorWriter(this));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public UDPNIOServerConnection bind(int port) throws IOException {
        return bind(new InetSocketAddress(port));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public UDPNIOServerConnection bind(String host, int port)
            throws IOException {
        return bind(host, port, 50);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public UDPNIOServerConnection bind(String host, int port, int backlog)
            throws IOException {
        return bind(new InetSocketAddress(host, port), backlog);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public UDPNIOServerConnection bind(SocketAddress socketAddress)
            throws IOException {
        return bind(socketAddress, -1);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public UDPNIOServerConnection bind(SocketAddress socketAddress, int backlog)
            throws IOException {
        final DatagramChannel serverDatagramChannel =
                selectorProvider.openDatagramChannel();
        UDPNIOServerConnection serverConnection = null;

        final Lock lock = state.getStateLocker().writeLock();
        lock.lock();
        try {
            getChannelConfigurator().preConfigure(this, serverDatagramChannel);

            final DatagramSocket socket = serverDatagramChannel.socket();
            socket.bind(socketAddress);
            
            getChannelConfigurator().postConfigure(this, serverDatagramChannel);

            serverConnection = obtainServerNIOConnection(serverDatagramChannel);
            serverConnections.add(serverConnection);

            if (!isStopped()) {
                serverConnection.register();
            }

            return serverConnection;
        } catch (Exception e) {
            if (serverConnection != null) {
                serverConnections.remove(serverConnection);

                serverConnection.closeSilently();
            } else {
                try {
                    serverDatagramChannel.close();
                } catch (IOException ignored) {
                }
            }

            throw Exceptions.makeIOException(e);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Connection bindToInherited() throws IOException {
        final Channel inheritedChannel = System.inheritedChannel();

        if (inheritedChannel == null) {
            throw new IOException("Inherited channel is not set");
        }
        if (!(inheritedChannel instanceof DatagramChannel)) {
            throw new IOException("Inherited channel is not java.nio.channels.DatagramChannel, but " + inheritedChannel.getClass().getName());
        }

        final DatagramChannel serverDatagramChannel = (DatagramChannel) inheritedChannel;
        UDPNIOServerConnection serverConnection = null;

        final Lock lock = state.getStateLocker().writeLock();
        lock.lock();
        try {
            getChannelConfigurator().preConfigure(this, serverDatagramChannel);
            getChannelConfigurator().postConfigure(this, serverDatagramChannel);

            serverConnection = obtainServerNIOConnection(serverDatagramChannel);
            serverConnections.add(serverConnection);

            if (!isStopped()) {
                serverConnection.register();
            }

            return serverConnection;
        } catch (Exception e) {
            if (serverConnection != null) {
                serverConnections.remove(serverConnection);

                serverConnection.closeSilently();
            } else {
                try {
                    serverDatagramChannel.close();
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
    public UDPNIOServerConnection bind(final String host,
            final PortRange portRange) throws IOException {
        return bind(host, portRange, -1);
    }
    
    
    /**
     * {@inheritDoc}
     */
    @Override
    public UDPNIOServerConnection bind(final String host,
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
                final FutureImpl<Closeable> future =
                        Futures.createSafeFuture();
                ((UDPNIOServerConnection) connection).unbind(
                        Futures.toCompletionHandler(future));
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
                    if (LOGGER.isLoggable(Level.FINE)) {
                        LOGGER.log(Level.FINE,
                                "Exception occurred when closing server connection: "
                                + serverConnection, e);
                    }
                }
            }

            serverConnections.clear();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Creates non-connected UDP {@link Connection}.
     *
     * @return non-connected UDP {@link Connection}.
     * @throws java.io.IOException
     */
    public GrizzlyFuture<Connection> connect() throws IOException {
        return connectorHandler.connect();
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
    public GrizzlyFuture<Connection> connect(String host, int port)
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
    public GrizzlyFuture<Connection> connect(SocketAddress remoteAddress) {
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
    public void connect(SocketAddress remoteAddress,
            CompletionHandler<Connection> completionHandler) {
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
    public GrizzlyFuture<Connection> connect(SocketAddress remoteAddress,
            SocketAddress localAddress) {
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
    public void connect(SocketAddress remoteAddress,
            SocketAddress localAddress,
            CompletionHandler<Connection> completionHandler) {
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
                        "UDPNIOTransport.closeChannel exception", e);
            }
        }

        asyncQueueWriter.onClose(connection);
    }

    @Override
    public void listen() {
        registerServerConnections();
    }

    private void registerServerConnections() {
        for (UDPNIOServerConnection serverConnection : serverConnections) {
            try {
                serverConnection.register();
            } catch (Exception e) {
                LOGGER.log(Level.WARNING,
                        LogMessages.WARNING_GRIZZLY_TRANSPORT_START_SERVER_CONNECTION_EXCEPTION(serverConnection),
                        e);
            }
        }
    }

    @Override
    public Filter getTransportFilter() {
        return transportFilter;
    }

    @Override
    protected final AbstractNIOAsyncQueueWriter getAsyncQueueWriter() {
        return asyncQueueWriter;
    }

    @Override
    public TemporarySelectorIO getTemporarySelectorIO() {
        return temporarySelectorIO;
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

    private int readConnected(final UDPNIOConnection connection, Buffer buffer,
            final ReadResult<Buffer, SocketAddress> currentResult) throws IOException {
        final int read;

        final int oldPos = buffer.position();

        if (buffer.isComposite()) {
            final ByteBufferArray array = buffer.toByteBufferArray();
            final ByteBuffer[] byteBuffers = array.getArray();
            final int size = array.size();

            read = (int) ((DatagramChannel) connection.getChannel()).read(byteBuffers, 0, size);

            array.restore();
            array.recycle();
        } else {
            read = ((DatagramChannel) connection.getChannel()).read(
                    buffer.toByteBuffer());
        }

        final boolean hasRead = (read > 0);

        if (hasRead) {
            buffer.position(oldPos + read);
        }
        
        if (hasRead && currentResult != null) {
            currentResult.setMessage(buffer);
            currentResult.setReadSize(currentResult.getReadSize() + read);
            currentResult.setSrcAddressHolder(connection.peerSocketAddressHolder);
        }

        return read;
    }

    private int readNonConnected(final UDPNIOConnection connection, Buffer buffer,
            final ReadResult<Buffer, SocketAddress> currentResult)
            throws IOException {
        final SocketAddress peerAddress;

        final int read;

        final DirectByteBufferRecord ioRecord =
                        DirectByteBufferRecord.get();
        try {
            final ByteBuffer directByteBuffer =
                    ioRecord.allocate(buffer.limit());
            final int initialBufferPos = directByteBuffer.position();
            peerAddress = ((DatagramChannel) connection.getChannel()).receive(
                    directByteBuffer);
            read = directByteBuffer.position() - initialBufferPos;
            if (read > 0) {
                directByteBuffer.flip();
                buffer.put(directByteBuffer);
            }
        } finally {
            ioRecord.release();
        }

        final boolean hasRead = (read > 0);

        if (hasRead && currentResult != null) {
            currentResult.setMessage(buffer);
            currentResult.setReadSize(currentResult.getReadSize() + read);
            currentResult.setSrcAddress(peerAddress);
        }

        return read;
    }

    public int read(final UDPNIOConnection connection, final Buffer buffer)
            throws IOException {
        return read(connection, buffer, null);
    }

    public int read(final UDPNIOConnection connection, Buffer buffer,
            final ReadResult<Buffer, SocketAddress> currentResult)
            throws IOException {

        int read = 0;

        final boolean isAllocate = (buffer == null && currentResult != null);

        try {
            if (isAllocate) {
                buffer = memoryManager.allocateAtLeast(connection.getReadBufferSize());
            }

            read = connection.isConnected()
                    ? readConnected(connection, buffer, currentResult)
                    : readNonConnected(connection, buffer, currentResult);

            connection.onRead(buffer, read);
        } catch (Exception e) {
            read = -1;
        } finally {
            if (isAllocate && buffer != null) {
                if (read <= 0) {
                    buffer.dispose();
                } else {
                    buffer.allowBufferDispose(true);
                }
            }
        }

        return read;
    }

    public long write(final UDPNIOConnection connection,
            final SocketAddress dstAddress, final WritableMessage message)
            throws IOException {
        return write(connection, dstAddress, message, null);
    }

    public long write(final UDPNIOConnection connection, final SocketAddress dstAddress,
            final WritableMessage message, final WriteResult<WritableMessage, SocketAddress> currentResult)
            throws IOException {

        final long written;
        if (message instanceof Buffer) {
            final Buffer buffer = (Buffer) message;
            final int oldPos = buffer.position();

            if (dstAddress != null) {
                written = ((DatagramChannel) connection.getChannel()).send(
                        buffer.toByteBuffer(), dstAddress);
            } else {

                if (buffer.isComposite()) {
                    final ByteBufferArray array = buffer.toByteBufferArray();
                    final ByteBuffer[] byteBuffers = array.getArray();
                    final int size = array.size();

                    written = ((DatagramChannel) connection.getChannel()).write(byteBuffers, 0, size);

                    array.restore();
                    array.recycle();
                } else {
                    written = ((DatagramChannel) connection.getChannel()).write(
                            buffer.toByteBuffer());
                }
            }

            if (written > 0) {
                buffer.position(oldPos + (int) written);
            }

            connection.onWrite(buffer, (int) written);
        } else if (message instanceof FileTransfer) {
            written = ((FileTransfer) message).writeTo((DatagramChannel) connection.getChannel());
        } else {
            throw new IllegalStateException("Unhandled message type");
        }

        if (currentResult != null) {
            currentResult.setMessage(message);
            currentResult.setWrittenSize(currentResult.getWrittenSize()
                    + written);
            currentResult.setDstAddressHolder(connection.peerSocketAddressHolder);
        }

        return written;
    }

    @Override
    public ChannelConfigurator getChannelConfigurator() {
        final ChannelConfigurator cc = channelConfigurator;
        return cc != null ? cc : DEFAULT_CHANNEL_CONFIGURATOR;
    }
    
    UDPNIOConnection obtainNIOConnection(DatagramChannel channel) {
        UDPNIOConnection connection = new UDPNIOConnection(this, channel);
        configureNIOConnection(connection);

        return connection;
    }

    UDPNIOServerConnection obtainServerNIOConnection(DatagramChannel channel) {
        UDPNIOServerConnection connection = new UDPNIOServerConnection(this, channel);
        configureNIOConnection(connection);

        return connection;
    }

    protected void configureNIOConnection(UDPNIOConnection connection) {
        connection.configureBlocking(isBlocking);
        connection.setFilterChain(filterChain);
        connection.setBlockingReadTimeout(readTimeout, TimeUnit.MILLISECONDS);
        connection.setBlockingWriteTimeout(writeTimeout, TimeUnit.MILLISECONDS);
        if (connectionMonitoringConfig.hasProbes()) {
            connection.setMonitoringProbes(connectionMonitoringConfig.getProbes());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Object createJmxManagementObject() {
        return MonitoringUtils.loadJmxObject(
                "org.glassfish.grizzly.nio.transport.jmx.UDPNIOTransport", this,
                UDPNIOTransport.class);
    }

    @Override
    protected boolean processOpAccept(NIOConnection connection) throws IOException {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    protected boolean processOpConnect(NIOConnection connection) throws IOException {
        throw new UnsupportedOperationException("Not supported");
    }

    protected class RegisterChannelCompletionHandler
            extends EmptyCompletionHandler<RegisterChannelResult> {

        @Override
        public void completed(final RegisterChannelResult result) {
            final SelectionKey selectionKey = result.getSelectionKey();

            final UDPNIOConnection connection =
                    (UDPNIOConnection) getConnectionForKey(selectionKey);

            if (connection != null) {
                final SelectorRunner selectorRunner = result.getSelectorRunner();
                connection.setSelectionKey(selectionKey);
                connection.setSelectorRunner(selectorRunner);
            }
        }
    }

    /**
     * Transport default {@link UDPNIOConnectorHandler}.
     */
    protected class TransportConnectorHandler extends UDPNIOConnectorHandler {

        public TransportConnectorHandler() {
            super(UDPNIOTransport.this);
        }

        @Override
        public FilterChain getFilterChain() {
            return UDPNIOTransport.this.getFilterChain();
        }
    }
    
    private static class DefaultChannelConfigurator implements ChannelConfigurator {
        @Override
        public void preConfigure(NIOTransport transport,
                SelectableChannel channel) throws IOException {
            
            final UDPNIOTransport udpNioTransport = (UDPNIOTransport) transport;
            final DatagramChannel datagramChannel = (DatagramChannel) channel;
            final DatagramSocket datagramSocket = datagramChannel.socket();
            
            datagramChannel.configureBlocking(false);

            try {
                datagramSocket.setReuseAddress(udpNioTransport.isReuseAddress());
            } catch (IOException e) {
                LOGGER.log(Level.WARNING,
                        LogMessages.WARNING_GRIZZLY_SOCKET_REUSEADDRESS_EXCEPTION(udpNioTransport.isReuseAddress()), e);
            }
        }

        @Override
        public void postConfigure(final NIOTransport transport,
                final SelectableChannel channel) throws IOException {

            final UDPNIOTransport udpNioTransport = (UDPNIOTransport) transport;
            final DatagramChannel datagramChannel = (DatagramChannel) channel;
            final DatagramSocket datagramSocket = datagramChannel.socket();

            final boolean isConnected = datagramChannel.isConnected();
            
            final int soTimeout = isConnected
                    ? udpNioTransport.getClientSocketSoTimeout()
                    : udpNioTransport.getServerSocketSoTimeout();
            
            try {
                datagramSocket.setSoTimeout(soTimeout);
            } catch (IOException e) {
                LOGGER.log(Level.WARNING,
                        LogMessages.WARNING_GRIZZLY_SOCKET_TIMEOUT_EXCEPTION(soTimeout), e);
            }
        }
    }
}
