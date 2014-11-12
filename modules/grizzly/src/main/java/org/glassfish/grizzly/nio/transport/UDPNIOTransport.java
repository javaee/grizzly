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

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.util.Collection;
import java.util.HashSet;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.grizzly.*;
import org.glassfish.grizzly.asyncqueue.*;
import org.glassfish.grizzly.filterchain.Filter;
import org.glassfish.grizzly.filterchain.FilterChainEnabledTransport;
import org.glassfish.grizzly.impl.FutureImpl;
import org.glassfish.grizzly.localization.LogMessages;
import org.glassfish.grizzly.memory.ByteBufferArray;
import org.glassfish.grizzly.monitoring.MonitoringUtils;
import org.glassfish.grizzly.nio.*;
import org.glassfish.grizzly.nio.tmpselectors.TemporarySelectorIO;
import org.glassfish.grizzly.utils.Futures;

/**
 * UDP NIO transport implementation
 * 
 * @author Alexey Stashok
 */
public final class UDPNIOTransport extends NIOTransport
        implements FilterChainEnabledTransport {
    /**
     * Default {@link ChannelConfigurator} used to configure client and server side
     * channels.
     */
    public static final ChannelConfigurator DEFAULT_CHANNEL_CONFIGURATOR =
            new DefaultChannelConfigurator();

    static final Logger LOGGER = Grizzly.logger(UDPNIOTransport.class);
    private static final String DEFAULT_TRANSPORT_NAME = "UDPNIOTransport";
    /**
     * The Server connections.
     */
    protected final Collection<UDPNIOServerConnection> serverConnections;
    /**
     * Transport AsyncQueueIO
     */
    protected final AsyncQueueIO<SocketAddress> asyncQueueIO;
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

    private final UDPNIOBindingHandler bindingHandler =
            new UDPNIOBindingHandler(this);


    public UDPNIOTransport() {
        this(DEFAULT_TRANSPORT_NAME);
    }

    public UDPNIOTransport(String name) {
        super(name != null ? name : DEFAULT_TRANSPORT_NAME);

        readBufferSize = -1;
        writeBufferSize = -1;

        registerChannelCompletionHandler = new RegisterChannelCompletionHandler();

        asyncQueueIO = AsyncQueueIO.Factory.createImmutable(
                new UDPNIOAsyncQueueReader(this),
                new UDPNIOAsyncQueueWriter(this));

        transportFilter = new UDPNIOTransportFilter(this);
        serverConnections = new ConcurrentLinkedQueue<UDPNIOServerConnection>();
    }

    @Override
    protected TemporarySelectorIO createTemporarySelectorIO() {
        return new TemporarySelectorIO(new UDPNIOTemporarySelectorReader(this),
                                       new UDPNIOTemporarySelectorWriter(this));
    }

    @Override
    protected void listen() {
        for (UDPNIOServerConnection serverConnection : serverConnections) {
            try {
                serverConnection.register();
            } catch (Exception e) {
                LOGGER.log(Level.WARNING,
                           LogMessages.WARNING_GRIZZLY_TRANSPORT_START_SERVER_CONNECTION_EXCEPTION(
                                   serverConnection),
                           e);
            }
        }
    }

    @Override
    public synchronized boolean addShutdownListener(GracefulShutdownListener shutdownListener) {
        final State state = getState().getState();
        if (state != State.STOPPING || state != State.STOPPED) {
            if (shutdownListeners == null) {
                shutdownListeners = new HashSet<GracefulShutdownListener>();
            }
            return shutdownListeners.add(shutdownListener);
        }
        return false;
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
        return bind(socketAddress, 4096);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public UDPNIOServerConnection bind(SocketAddress socketAddress, int backlog)
            throws IOException {
        return bindingHandler.bind(socketAddress, backlog);
    }

    @Override
    public Connection bindToInherited() throws IOException {
        return bindingHandler.bindToInherited();
    }

    
    /**
     * {@inheritDoc}
     */
    @Override
    public UDPNIOServerConnection bind(final String host,
            final PortRange portRange, final int backlog) throws IOException {

        return (UDPNIOServerConnection) bindingHandler.bind(host, portRange, backlog);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void unbind(final Connection connection) {
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
    protected void closeConnection(final Connection connection)
            throws IOException {
        final SelectableChannel nioChannel =
                ((NIOConnection) connection).getChannel();

        if (nioChannel != null) {
            try {
                nioChannel.close();
            } catch (IOException e) {
                LOGGER.log(Level.FINE,
                        "UDPNIOTransport.closeChannel exception", e);
            }
        }

        if (asyncQueueIO != null) {
            AsyncQueueReader reader = asyncQueueIO.getReader();
            if (reader != null) {
                reader.onClose(connection);
            }

            AsyncQueueWriter writer = asyncQueueIO.getWriter();
            if (writer != null) {
                writer.onClose(connection);
            }

        }
    }

    @Override
    public synchronized void configureStandalone(boolean isStandalone) {
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

    @Override
    public Filter getTransportFilter() {
        return transportFilter;
    }

    @Override
    public AsyncQueueIO getAsyncQueueIO() {
        return asyncQueueIO;
    }

    @Override
    public TemporarySelectorIO getTemporarySelectorIO() {
        return temporarySelectorIO;
    }

    @Override
    public void fireIOEvent(final IOEvent ioEvent,
            final Connection connection,
            final IOEventLifeCycleListener listener) {

        final Processor conProcessor = connection.obtainProcessor(ioEvent);

        ProcessorExecutor.execute(Context.create(connection,
                    conProcessor, ioEvent, listener));
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

        if (isAllocate) {
            buffer = memoryManager.allocateAtLeast(connection.getReadBufferSize());
        }

        try {
            read = connection.isConnected()
                    ? readConnected(connection, buffer, currentResult)
                    : readNonConnected(connection, buffer, currentResult);

            connection.onRead(buffer, read);
        } catch (Exception e) {
            read = -1;
        } finally {
            if (isAllocate) {
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


    /**
     * {@inheritDoc}
     */
    @Override
    protected Object createJmxManagementObject() {
        return MonitoringUtils.loadJmxObject(
                "org.glassfish.grizzly.nio.transport.jmx.UDPNIOTransport", this,
                UDPNIOTransport.class);
    }

    protected class RegisterChannelCompletionHandler
            extends EmptyCompletionHandler<RegisterChannelResult> {

        @Override
        public void completed(final RegisterChannelResult result) {
            final SelectionKey selectionKey = result.getSelectionKey();

            final UDPNIOConnection connection =
                    (UDPNIOConnection) getSelectionKeyHandler().
                    getConnectionForKey(selectionKey);

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
        public Processor getProcessor() {
            return UDPNIOTransport.this.getProcessor();
        }

        @Override
        public ProcessorSelector getProcessorSelector() {
            return UDPNIOTransport.this.getProcessorSelector();
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
