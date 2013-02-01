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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.util.Collection;
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
import org.glassfish.grizzly.monitoring.jmx.JmxObject;
import org.glassfish.grizzly.nio.*;
import org.glassfish.grizzly.nio.tmpselectors.TemporarySelectorIO;
import org.glassfish.grizzly.nio.tmpselectors.TemporarySelectorPool;
import org.glassfish.grizzly.nio.tmpselectors.TemporarySelectorsEnabledTransport;
import org.glassfish.grizzly.strategies.SameThreadIOStrategy;
import org.glassfish.grizzly.strategies.WorkerThreadIOStrategy;
import org.glassfish.grizzly.threadpool.AbstractThreadPool;
import org.glassfish.grizzly.threadpool.GrizzlyExecutorService;
import org.glassfish.grizzly.utils.Futures;

/**
 * UDP NIO transport implementation
 * 
 * @author Alexey Stashok
 */
public final class UDPNIOTransport extends NIOTransport implements
        SocketBinder, SocketConnectorHandler, AsyncQueueEnabledTransport,
        FilterChainEnabledTransport, TemporarySelectorsEnabledTransport {

    static final Logger LOGGER = Grizzly.logger(UDPNIOTransport.class);
    private static final String DEFAULT_TRANSPORT_NAME = "UDPNIOTransport";
    /**
     * The server socket time out
     */
    protected final int serverSocketSoTimeout = 0;
    /**
     * The socket reuseAddress
     */
    protected boolean reuseAddress = true;
    /**
     * Default channel connection timeout
     */
    protected int connectionTimeout =
            UDPNIOConnectorHandler.DEFAULT_CONNECTION_TIMEOUT;
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
    protected final TemporarySelectorIO temporarySelectorIO;
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
        super(name);

        readBufferSize = -1;
        writeBufferSize = -1;

        registerChannelCompletionHandler = new RegisterChannelCompletionHandler();

        asyncQueueIO = AsyncQueueIO.Factory.createImmutable(
                new UDPNIOAsyncQueueReader(this),
                new UDPNIOAsyncQueueWriter(this));

        temporarySelectorIO = new TemporarySelectorIO(
                new UDPNIOTemporarySelectorReader(this),
                new UDPNIOTemporarySelectorWriter(this));

        transportFilter = new UDPNIOTransportFilter(this);
        serverConnections = new ConcurrentLinkedQueue<UDPNIOServerConnection>();
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
    public void unbind(final Connection connection) throws IOException {
        final Lock lock = state.getStateLocker().writeLock();
        lock.lock();
        try {
            if (connection != null
                    && serverConnections.remove(connection)) {
                final FutureImpl<Connection> future =
                        Futures.<Connection>createSafeFuture();
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
    public void unbindAll() throws IOException {
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

    /**
     * Start UDPNIOTransport.
     * 
     * The transport will be started only if its current state is {@link State#STOP},
     * otherwise the call will be ignored without exception thrown and the transport
     * state will remain the same as it was before the method call.
     */
    @Override
    public void start() throws IOException {
        final Lock lock = state.getStateLocker().writeLock();
        lock.lock();
        try {
            State currentState = state.getState();
            if (currentState != State.STOP) {
                LOGGER.log(Level.WARNING,
                        LogMessages.WARNING_GRIZZLY_TRANSPORT_NOT_STOP_STATE_EXCEPTION());
                return;
            }

            state.setState(State.STARTING);

            super.start();

            if (selectorHandler == null) {
                selectorHandler = new DefaultSelectorHandler();
            }

            if (selectionKeyHandler == null) {
                selectionKeyHandler = new DefaultSelectionKeyHandler();
            }

            if (processor == null && processorSelector == null) {
                processor = new StandaloneProcessor();
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
                strategy =  WorkerThreadIOStrategy.getInstance();
            }

            temporarySelectorIO.setSelectorPool(
                    new TemporarySelectorPool(selectorProvider, selectorPoolSize));

            startSelectorRunners();

            registerServerConnections();

            state.setState(State.START);

            notifyProbesStart(this);
        } finally {
            lock.unlock();
        }
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

    /**
     * Stop UDPNIOTransport.
     * 
     * If the current transport state is {@link State#STOP} - the call will be
     * ignored and no exception thrown.
     */
    @Override
    public void stop() throws IOException {
        final Lock lock = state.getStateLocker().writeLock();
        lock.lock();
        try {
            final State stateNow = state.getState();
            
            if (stateNow == State.STOP) {
                return;
            }
            
            if (stateNow == State.PAUSE) {
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

    /**
     * Pause UDPNIOTransport, so I/O events coming on its {@link UDPNIOConnection}s
     * will not be processed. Use {@link #resume()} in order to resume UDPNIOTransport processing.
     * 
     * The transport will be paused only if its current state is {@link State#START},
     * otherwise the call will be ignored without exception thrown and the transport
     * state will remain the same as it was before the method call.
     */
    @Override
    public void pause() throws IOException {
        final Lock lock = state.getStateLocker().writeLock();
        lock.lock();
        try {
            if (state.getState() != State.START) {
                LOGGER.log(Level.WARNING,
                        LogMessages.WARNING_GRIZZLY_TRANSPORT_NOT_START_STATE_EXCEPTION());
                return;
            }
            state.setState(State.PAUSE);
            notifyProbesPause(this);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Resume UDPNIOTransport, which has been paused before using {@link #pause()}.
     * 
     * The transport will be resumed only if its current state is {@link State#PAUSE},
     * otherwise the call will be ignored without exception thrown and the transport
     * state will remain the same as it was before the method call.
     */
    @Override
    public void resume() throws IOException {
        final Lock lock = state.getStateLocker().writeLock();
        lock.lock();
        try {
            if (state.getState() != State.PAUSE) {
                LOGGER.log(Level.WARNING,
                        LogMessages.WARNING_GRIZZLY_TRANSPORT_NOT_PAUSE_STATE_EXCEPTION());
                return;
            }
            state.setState(State.START);
            notifyProbesResume(this);
        } finally {
            lock.unlock();
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

    public int getConnectionTimeout() {
        return connectionTimeout;
    }

    public void setConnectionTimeout(int connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
        notifyProbesConfigChanged(this);
    }

    public boolean isReuseAddress() {
        return reuseAddress;
    }

    public void setReuseAddress(boolean reuseAddress) {
        this.reuseAddress = reuseAddress;
        notifyProbesConfigChanged(this);
    }

    @Override
    public void fireIOEvent(final IOEvent ioEvent,
            final Connection connection,
            final IOEventProcessingHandler processingHandler) {

        final Processor conProcessor = connection.obtainProcessor(ioEvent);

        ProcessorExecutor.execute(Context.create(connection,
                    conProcessor, ioEvent, processingHandler));
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

        final int oldPos = buffer.position();

        if (!buffer.isComposite()) {
            final ByteBuffer underlyingBB = buffer.toByteBuffer();
            final int initialBufferPos = underlyingBB.position();
            peerAddress = ((DatagramChannel) connection.getChannel()).receive(
                    underlyingBB);
            read = underlyingBB.position() - initialBufferPos;
        } else {
            throw new IllegalStateException("Cannot read from "
                    + "non-connection UDP connection into CompositeBuffer");
        }

        final boolean hasRead = (read > 0);

        if (hasRead) {
            buffer.position(oldPos + read);
        }
        
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
            if (connection.isConnected()) {
                read = readConnected(connection, buffer, currentResult);
            } else {
                read = readNonConnected(connection, buffer, currentResult);
            }

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
        connection.configureStandalone(isStandalone);
        connection.setProcessor(processor);
        connection.setProcessorSelector(processorSelector);
        connection.setReadTimeout(readTimeout, TimeUnit.MILLISECONDS);
        connection.setWriteTimeout(writeTimeout, TimeUnit.MILLISECONDS);
        if (connectionMonitoringConfig.hasProbes()) {
            connection.setMonitoringProbes(connectionMonitoringConfig.getProbes());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected JmxObject createJmxManagementObject() {
        return new org.glassfish.grizzly.nio.transport.jmx.UDPNIOTransport(this);
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
}
