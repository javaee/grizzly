/*
 * 
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2007-2010 Sun Microsystems, Inc. All rights reserved.
 * 
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License. You can obtain
 * a copy of the License at https://glassfish.dev.java.net/public/CDDL+GPL.html
 * or glassfish/bootstrap/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 * 
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/bootstrap/legal/LICENSE.txt.
 * Sun designates this particular file as subject to the "Classpath" exception
 * as provided by Sun in the GPL Version 2 section of the License file that
 * accompanied this code.  If applicable, add the following below the License
 * Header, with the fields enclosed by brackets [] replaced by your own
 * identifying information: "Portions Copyrighted [year]
 * [name of copyright owner]"
 * 
 * Contributor(s):
 * 
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
 *
 */

package com.sun.grizzly.nio.transport;

import java.util.concurrent.Future;
import com.sun.grizzly.IOEvent;
import com.sun.grizzly.nio.AbstractNIOTransport;
import com.sun.grizzly.Connection;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.util.Collection;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;
import java.util.logging.Logger;
import com.sun.grizzly.Buffer;
import com.sun.grizzly.CompletionHandler;
import com.sun.grizzly.CompletionHandlerAdapter;
import com.sun.grizzly.Context;
import com.sun.grizzly.Grizzly;
import com.sun.grizzly.PostProcessor;
import com.sun.grizzly.Processor;
import com.sun.grizzly.ProcessorExecutor;
import com.sun.grizzly.ProcessorResult;
import com.sun.grizzly.ProcessorResult.Status;
import com.sun.grizzly.ProcessorRunnable;
import com.sun.grizzly.ProcessorSelector;
import com.sun.grizzly.ReadResult;
import com.sun.grizzly.SocketBinder;
import com.sun.grizzly.SocketConnectorHandler;
import com.sun.grizzly.StandaloneProcessor;
import com.sun.grizzly.StandaloneProcessorSelector;
import com.sun.grizzly.WriteResult;
import com.sun.grizzly.asyncqueue.AsyncQueueEnabledTransport;
import com.sun.grizzly.asyncqueue.AsyncQueueIO;
import com.sun.grizzly.asyncqueue.AsyncQueueReader;
import com.sun.grizzly.asyncqueue.AsyncQueueWriter;
import com.sun.grizzly.filterchain.DefaultFilterChain;
import com.sun.grizzly.filterchain.Filter;
import com.sun.grizzly.filterchain.FilterChain;
import com.sun.grizzly.filterchain.FilterChainEnabledTransport;
import com.sun.grizzly.filterchain.FilterChainFactory;
import com.sun.grizzly.filterchain.PatternFilterChainFactory;
import com.sun.grizzly.filterchain.SingletonFilterChainFactory;
import com.sun.grizzly.nio.DefaultSelectionKeyHandler;
import com.sun.grizzly.nio.DefaultSelectorHandler;
import com.sun.grizzly.nio.NIOConnection;
import com.sun.grizzly.nio.RegisterChannelResult;
import com.sun.grizzly.nio.RoundRobinConnectionDistributor;
import com.sun.grizzly.nio.SelectorRunner;
import com.sun.grizzly.nio.tmpselectors.TemporarySelectorIO;
import com.sun.grizzly.nio.tmpselectors.TemporarySelectorPool;
import com.sun.grizzly.nio.tmpselectors.TemporarySelectorsEnabledTransport;
import com.sun.grizzly.strategies.WorkerThreadStrategy;
import com.sun.grizzly.streams.StreamReader;
import com.sun.grizzly.streams.StreamWriter;
import com.sun.grizzly.threadpool.AbstractThreadPool;
import com.sun.grizzly.threadpool.GrizzlyExecutorService;
import com.sun.grizzly.threadpool.ThreadPoolConfig;

/**
 * UDP NIO transport implementation
 * 
 * @author Alexey Stashok
 */
public final class UDPNIOTransport extends AbstractNIOTransport
        implements SocketBinder, SocketConnectorHandler,
        AsyncQueueEnabledTransport, FilterChainEnabledTransport,
        TemporarySelectorsEnabledTransport  {
    
    private Logger logger = Grizzly.logger(UDPNIOTransport.class);

    private static final String DEFAULT_TRANSPORT_NAME = "UDPNIOTransport";

    /**
     * The server socket time out
     */
    protected int serverSocketSoTimeout = 0;
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
     * FilterChainFactory implementation
     */
    protected FilterChainFactory filterChainFactory;
    /**
     * Transport AsyncQueueIO
     */
    protected AsyncQueueIO asyncQueueIO;
    /**
     * Server socket backlog.
     */
    protected TemporarySelectorIO temporarySelectorIO;
    private final Filter transportFilter;
    protected final RegisterChannelCompletionHandler registerChannelCompletionHandler;
    private final EnableInterestPostProcessor enablingInterestPostProcessor;

    public UDPNIOTransport() {
        this(DEFAULT_TRANSPORT_NAME);
    }

    public UDPNIOTransport(String name) {
        super(name);

        readBufferSize = -1;
        writeBufferSize = -1;

        registerChannelCompletionHandler = new RegisterChannelCompletionHandler();
        enablingInterestPostProcessor = new EnableInterestPostProcessor();

        asyncQueueIO = new AsyncQueueIO(new UDPNIOAsyncQueueReader(this),
                new UDPNIOAsyncQueueWriter(this));

        temporarySelectorIO = new TemporarySelectorIO(
                new UDPNIOTemporarySelectorReader(this),
                new UDPNIOTemporarySelectorWriter(this));

        filterChainFactory = new SingletonFilterChainFactory(
                new DefaultFilterChain());

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
        state.getStateLocker().writeLock().lock();

        try {
            DatagramChannel serverSocketChannel = DatagramChannel.open();
            final UDPNIOServerConnection serverConnection =
                    new UDPNIOServerConnection(this, serverSocketChannel);
            serverConnections.add(serverConnection);

            DatagramSocket socket = serverSocketChannel.socket();
            socket.setReuseAddress(reuseAddress);
            socket.setSoTimeout(serverSocketSoTimeout);
            socket.bind(socketAddress);

            serverSocketChannel.configureBlocking(false);

            if (!isStopped()) {
                serverConnection.register();
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
            if (connection != null &&
                    serverConnections.remove((UDPNIOServerConnection) connection)) {
                connection.close();
            }
        } finally {
            state.getStateLocker().writeLock().unlock();
        }
    }

    @Override
    public void unbindAll() throws IOException {
        state.getStateLocker().writeLock().lock();

        try {
            stopServerConnections();
        } finally {
            state.getStateLocker().writeLock().unlock();
        }
    }

    /**
     * Creates non-connected UDP {@link Connection}.
     *
     * @return non-connected UDP {@link Connection}.
     * @throws java.io.IOException
     */
    public Future<Connection> connect() throws IOException {
        return connect(null, null, null);
    }

    @Override
    public Future<Connection> connect(String host, int port)
            throws IOException {
        return connect(new InetSocketAddress(host, port));
    }

    @Override
    public Future<Connection> connect(SocketAddress remoteAddress)
            throws IOException {
        return connect(remoteAddress, (SocketAddress) null);
    }

    @Override
    public Future<Connection> connect(SocketAddress remoteAddress,
            SocketAddress localAddress) throws IOException {
        return connect(remoteAddress, localAddress, null);
    }

    @Override
    public Future<Connection> connect(SocketAddress remoteAddress,
            CompletionHandler<Connection> completionHandler)
            throws IOException {
        return connect(remoteAddress, null, completionHandler);

    }

    @Override
    public Future<Connection> connect(SocketAddress remoteAddress,
            SocketAddress localAddress,
            CompletionHandler<Connection> completionHandler)
            throws IOException {
        UDPNIOConnectorHandler connectorHandler = new UDPNIOConnectorHandler(this);
        return connectorHandler.connect(remoteAddress, localAddress,
                completionHandler);
    }

    @Override
    protected void closeConnection(Connection connection) throws IOException {
        SelectableChannel nioChannel = ((NIOConnection) connection).getChannel();

        // channel could be either SocketChannel or ServerSocketChannel
        if (nioChannel instanceof DatagramChannel) {
            DatagramSocket socket = ((DatagramChannel) nioChannel).socket();
            socket.close();
        }

        if (nioChannel != null) {
            try {
                nioChannel.close();
            } catch (IOException e) {
                logger.log(Level.FINE,
                        "TCPNIOTransport.closeChannel exception", e);
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
    public void start() throws IOException {
        state.getStateLocker().writeLock().lock();
        try {
            State currentState = state.getState();
            if (currentState != State.STOP) {
                logger.log(Level.WARNING,
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
                processor = getFilterChainFactory().create();
            }

            if (selectorRunnersCount <= 0) {
                selectorRunnersCount = Runtime.getRuntime().availableProcessors();
            }

            if (nioChannelDistributor == null) {
                nioChannelDistributor = new RoundRobinConnectionDistributor(this);
            }

            if (strategy == null) {
                strategy = new WorkerThreadStrategy(threadPool);
            }

            if (threadPool == null) {
                threadPool = GrizzlyExecutorService.createInstance(
                        ThreadPoolConfig.DEFAULT.clone().
                        setCorePoolSize(selectorRunnersCount * 2).
                        setMaxPoolSize(selectorRunnersCount * 2));
            }

            /* By default TemporarySelector pool size should be equal
            to the number of processing threads */
            int selectorPoolSize =
                    TemporarySelectorPool.DEFAULT_SELECTORS_COUNT;
            if (threadPool instanceof AbstractThreadPool) {
                selectorPoolSize =((AbstractThreadPool) threadPool).
                        getConfig().getMaxPoolSize();
            }
            temporarySelectorIO.setSelectorPool(
                    new TemporarySelectorPool(selectorPoolSize));

            startSelectorRunners();
            
            registerServerConnections();
        } finally {
            state.getStateLocker().writeLock().unlock();
        }
    }

    private void registerServerConnections() {
        for (UDPNIOServerConnection serverConnection : serverConnections) {
            try {
                serverConnection.register();
            } catch (Exception e) {
                logger.log(Level.WARNING,
                        "Exception occurred when starting server connection: " +
                        serverConnection, e);
            }
        }
    }

    @Override
    public void stop() throws IOException {
        state.getStateLocker().writeLock().lock();

        try {
            state.setState(State.STOP);
            stopSelectorRunners();

            if (threadPool != null) {
                threadPool.shutdown();
                threadPool = null;
            }

            stopServerConnections();
        } finally {
            state.getStateLocker().writeLock().unlock();
        }
    }

    private void stopServerConnections() {
        for (Connection serverConnection : serverConnections) {
            try {
                serverConnection.close();
            } catch (Exception e) {
                logger.log(Level.FINE,
                        "Exception occurred when closing server connection: " +
                        serverConnection, e);
            }
        }

        serverConnections.clear();
    }

    @Override
    public void pause() throws IOException {
        state.getStateLocker().writeLock().lock();

        try {
            if (state.getState() != State.START) {
                logger.log(Level.WARNING,
                        "Transport is not in START state!");
            }
            state.setState(State.PAUSE);
        } finally {
            state.getStateLocker().writeLock().unlock();
        }
    }

    @Override
    public void resume() throws IOException {
        state.getStateLocker().writeLock().lock();

        try {
            if (state.getState() != State.PAUSE) {
                logger.log(Level.WARNING,
                        "Transport is not in PAUSE state!");
            }
            state.setState(State.START);
        } finally {
            state.getStateLocker().writeLock().unlock();
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
                processor = getFilterChainFactory().create();
                processorSelector = null;
            }
        }
    }
    
    @Override
    public FilterChainFactory getFilterChainFactory() {
        return filterChainFactory;
    }

    @Override
    public void setFilterChainFactory(FilterChainFactory factory) {
        filterChainFactory = factory;
    }

    @Override
    public FilterChain getFilterChain() {
        final FilterChainFactory factory = getFilterChainFactory();
        if (factory instanceof PatternFilterChainFactory) {
            return ((PatternFilterChainFactory) factory).getFilterChainPattern();
        }

        throw new IllegalStateException(
                "Transport FilterChainFactory doesn't " +
                "support creating of FilterChain by a patterns. " +
                "It means you have to add/remove Filters using " +
                "FilterChainFactory API: " + factory.getClass().getName());
    }

    @Override
    public Filter getTransportFilter() {
        return transportFilter;
    }

    protected NIOConnection obtainNIOConnection(DatagramChannel channel) {
        final UDPNIOConnection connection = new UDPNIOConnection(this, channel);
        connection.configureBlocking(isBlocking);
        return connection;
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
    public void setTemporarySelectorIO(TemporarySelectorIO temporarySelectorIO) {
        this.temporarySelectorIO = temporarySelectorIO;
    }

    public int getConnectionTimeout() {
        return connectionTimeout;
    }

    public void setConnectionTimeout(int connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    public boolean isReuseAddress() {
        return reuseAddress;
    }

    public void setReuseAddress(boolean reuseAddress) {
        this.reuseAddress = reuseAddress;
    }

    @Override
    public IOEventReg fireIOEvent(final IOEvent ioEvent,
            final Connection connection) throws IOException {

        try {
            // First of all try operations, which could run in standalone mode
            if (ioEvent == IOEvent.READ) {
                return processReadIoEvent(ioEvent, (UDPNIOConnection) connection);
            } else if (ioEvent == IOEvent.WRITE) {
                return processWriteIoEvent(ioEvent, (UDPNIOConnection) connection);
            } else {
                final Processor conProcessor = getConnectionProcessor(
                        connection, ioEvent);

                if (conProcessor != null) {
                    if (executeProcessor(ioEvent, connection, conProcessor, null, null)) {
                        return IOEventReg.REGISTER;
                    }

                    return IOEventReg.LEAVE;
                } else {
                    return IOEventReg.DEREGISTER;
                }
            }
        } catch (IOException e) {
            logger.log(Level.FINE, "IOException occurred on fireIOEvent()." +
                    "connection=" + connection + " event=" + ioEvent);
            throw e;
        } catch (Exception e) {
            String text = new StringBuilder(256).
                    append("Unexpected exception occurred fireIOEvent().").
                    append("connection=").append(connection).
                    append(" event=").append(ioEvent).toString();

            logger.log(Level.WARNING, text, e);
            throw new IOException(e.getClass() + ": " + text);
        }

    }

    protected boolean executeProcessor(final IOEvent ioEvent,
            final Connection connection,
            final Processor processor, final ProcessorExecutor executor,
            final PostProcessor postProcessor)
            throws IOException {

        final ProcessorRunnable processorRunnable = ProcessorRunnable.create(
                ioEvent, connection, processor, postProcessor);

        return processorRunnable.execute();
    }

    private IOEventReg processReadIoEvent(final IOEvent ioEvent,
            final UDPNIOConnection connection) throws IOException {

        final UDPNIOAsyncQueueReader asyncQueueReader =
                (UDPNIOAsyncQueueReader) getAsyncQueueIO().getReader();

        if (asyncQueueReader == null || !asyncQueueReader.isReady(connection)) {
            return executeDefaultProcessor(ioEvent, connection);
        } else {
            executeProcessor(ioEvent, connection, asyncQueueReader, null, null);
            return IOEventReg.LEAVE;
        }
    }

    private IOEventReg processWriteIoEvent(final IOEvent ioEvent,
            final UDPNIOConnection connection)
            throws IOException {
        final AsyncQueueWriter asyncQueueWriter = getAsyncQueueIO().getWriter();

        if (asyncQueueWriter == null || !asyncQueueWriter.isReady(connection)) {
            return executeDefaultProcessor(ioEvent, connection);
        } else {
            executeProcessor(ioEvent, connection, asyncQueueWriter, null, null);
            return IOEventReg.LEAVE;
        }
    }


    private IOEventReg executeDefaultProcessor(final IOEvent ioEvent,
            final UDPNIOConnection connection) throws IOException {

        final Processor conProcessor = getConnectionProcessor(connection, ioEvent);
        if (conProcessor != null) {
            if (executeProcessor(ioEvent, connection, conProcessor, null, null)) {
                return IOEventReg.REGISTER;
            }

            return IOEventReg.LEAVE;
        }

        return IOEventReg.DEREGISTER;
    }

    Processor getConnectionProcessor(final Connection connection,
            final IOEvent ioEvent) {
        Processor conProcessor = connection.getProcessor();
        final ProcessorSelector conProcessorSelector =
                connection.getProcessorSelector();

        if ((conProcessor == null || !conProcessor.isInterested(ioEvent)) &&
                conProcessorSelector != null) {
            conProcessor = conProcessorSelector.select(ioEvent, connection);
        }

        return conProcessor;
    }

    private int readConnected(final UDPNIOConnection connection, Buffer buffer,
            final ReadResult currentResult) throws IOException {
        final SocketAddress peerAddress = connection.getPeerAddress();

        final int read;
        
        if (buffer.isComposite()) {
            final ByteBuffer[] byteBuffers = buffer.toByteBufferArray();
            read = (int) ((DatagramChannel) connection.getChannel()).read(byteBuffers);

            if (read > 0) {
                resetByteBuffers(byteBuffers, read);
                buffer.position(buffer.position() + read);
            }
        } else {
            read = (int) ((DatagramChannel) connection.getChannel()).read(
                    buffer.toByteBuffer());
        }

        if (currentResult != null && read > 0) {
            currentResult.setMessage(buffer);
            currentResult.setReadSize(currentResult.getReadSize() + read);
            currentResult.setSrcAddress(peerAddress);
        }

        return read;
    }
    
    private int readNonConnected(final UDPNIOConnection connection, Buffer buffer,
            final ReadResult currentResult) throws IOException {
        final SocketAddress peerAddress;

        final int read;

        if (!buffer.isComposite()) {
            final ByteBuffer underlyingBB = buffer.toByteBuffer();
            final int initialBufferPos = underlyingBB.position();
            peerAddress = ((DatagramChannel) connection.getChannel()).receive(
                    underlyingBB);
            read = underlyingBB.position() - initialBufferPos;
        } else {
            throw new IllegalStateException("Can not read from "
                    + "non-connection UDP connection into CompositeBuffer");
        }

        if (currentResult != null && read > 0) {
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
            final ReadResult currentResult) throws IOException {

        int read = 0;

        final boolean isAllocate = (buffer == null && currentResult != null);

        if (isAllocate) {
            buffer = memoryManager.allocate(connection.getReadBufferSize());
        }

        try {
            if (connection.isConnected()) {
                read = readConnected(connection, buffer, currentResult);
            } else {
                read = readNonConnected(connection, buffer, currentResult);
            }
        } finally {
            if (isAllocate && read <= 0) {
                buffer.dispose();
                buffer = null;
            }
        }

        return read;
    }

    public int write(final UDPNIOConnection connection,
            final SocketAddress dstAddress, final Buffer buffer)
            throws IOException {
        return write(connection, dstAddress, buffer, null);
    }

    public int write(final UDPNIOConnection connection, final SocketAddress dstAddress,
            final Buffer buffer, final WriteResult currentResult)
            throws IOException {

        final int written;

        if (dstAddress != null) {
            written = ((DatagramChannel) connection.getChannel()).send(
                    buffer.toByteBuffer(), dstAddress);
        } else {

            if (buffer.isComposite()) {
                final ByteBuffer[] byteBuffers = buffer.toByteBufferArray();
                written = (int) ((DatagramChannel) connection.getChannel()).write(byteBuffers);

                if (written > 0) {
                    resetByteBuffers(byteBuffers, written);
                    buffer.position(buffer.position() + written);
                }
            } else {
                written = (int) ((DatagramChannel) connection.getChannel()).write(
                        buffer.toByteBuffer());
            }
        }

        if (currentResult != null) {
            currentResult.setMessage(buffer);
            currentResult.setWrittenSize(currentResult.getWrittenSize()
                    + written);
            currentResult.setDstAddress(
                    connection.getPeerAddress());
        }

        return written;
    }

    @Override
    public StreamReader getStreamReader(Connection connection) {
        return new UDPNIOStreamReader((UDPNIOConnection) connection);
    }

    @Override
    public StreamWriter getStreamWriter(Connection connection) {
        return new UDPNIOStreamWriter((UDPNIOConnection) connection);
    }

    private void resetByteBuffers(final ByteBuffer[] byteBuffers, int processed) {
        int index = 0;
        while(processed > 0) {
            final ByteBuffer byteBuffer = byteBuffers[index++];
            byteBuffer.position(0);
            processed -= byteBuffer.remaining();
        }
    }

    public class EnableInterestPostProcessor
            implements PostProcessor {

        @Override
        public void process(final ProcessorResult result,
                final Context context) throws IOException {
            if (result == null || result.getStatus() == Status.OK) {
                final IOEvent ioEvent = context.getIoEvent();
                ((NIOConnection) context.getConnection()).enableIOEvent(ioEvent);
            }
        }
    }

    protected class RegisterChannelCompletionHandler
            extends CompletionHandlerAdapter<RegisterChannelResult> {

        @Override
        public void completed(final RegisterChannelResult result) {
            try {
                final SelectionKey selectionKey = result.getSelectionKey();

                final UDPNIOConnection connection =
                        (UDPNIOConnection) getSelectionKeyHandler().
                        getConnectionForKey(selectionKey);

                if (connection != null) {
                    final SelectorRunner selectorRunner = result.getSelectorRunner();
                    connection.setSelectionKey(selectionKey);
                    connection.setSelectorRunner(selectorRunner);
                }
            } catch (Exception e) {
                logger.log(Level.FINE, "Exception happened, when " +
                        "trying to register the channel", e);
            }
        }
    }
}
