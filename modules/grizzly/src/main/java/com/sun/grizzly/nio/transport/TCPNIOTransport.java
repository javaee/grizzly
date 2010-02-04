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

import com.sun.grizzly.CompletionHandler;
import com.sun.grizzly.asyncqueue.AsyncQueueIO;
import com.sun.grizzly.nio.RegisterChannelResult;
import com.sun.grizzly.nio.RoundRobinConnectionDistributor;
import com.sun.grizzly.nio.DefaultSelectorHandler;
import com.sun.grizzly.nio.DefaultSelectionKeyHandler;
import com.sun.grizzly.nio.AbstractNIOTransport;
import com.sun.grizzly.asyncqueue.AsyncQueueEnabledTransport;
import com.sun.grizzly.Connection;
import com.sun.grizzly.Grizzly;
import com.sun.grizzly.IOEvent;
import com.sun.grizzly.Context;
import com.sun.grizzly.Processor;
import com.sun.grizzly.nio.NIOConnection;
import com.sun.grizzly.asyncqueue.AsyncQueueReader;
import com.sun.grizzly.asyncqueue.AsyncQueueWriter;
import com.sun.grizzly.filterchain.DefaultFilterChain;
import com.sun.grizzly.filterchain.Filter;
import com.sun.grizzly.filterchain.FilterChain;
import com.sun.grizzly.filterchain.FilterChainEnabledTransport;
import com.sun.grizzly.filterchain.FilterChainFactory;
import com.sun.grizzly.filterchain.PatternFilterChainFactory;
import com.sun.grizzly.filterchain.SingletonFilterChainFactory;
import com.sun.grizzly.streams.StreamReader;
import com.sun.grizzly.streams.StreamWriter;
import com.sun.grizzly.nio.tmpselectors.TemporarySelectorPool;
import com.sun.grizzly.nio.tmpselectors.TemporarySelectorsEnabledTransport;
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
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;
import com.sun.grizzly.Buffer;
import com.sun.grizzly.CompletionHandlerAdapter;
import com.sun.grizzly.PostProcessor;
import com.sun.grizzly.ProcessorExecutor;
import com.sun.grizzly.ProcessorResult;
import com.sun.grizzly.ProcessorResult.Status;
import com.sun.grizzly.ProcessorRunnable;
import com.sun.grizzly.ProcessorSelector;
import com.sun.grizzly.SocketBinder;
import com.sun.grizzly.SocketConnectorHandler;
import com.sun.grizzly.StandaloneProcessor;
import com.sun.grizzly.StandaloneProcessorSelector;
import com.sun.grizzly.WriteResult;
import com.sun.grizzly.nio.SelectorRunner;
import com.sun.grizzly.nio.tmpselectors.TemporarySelectorIO;
import com.sun.grizzly.strategies.SameThreadStrategy;
import com.sun.grizzly.threadpool.AbstractThreadPool;
import com.sun.grizzly.threadpool.GrizzlyExecutorService;
import com.sun.grizzly.threadpool.ThreadPoolConfig;
import java.io.EOFException;
import java.nio.ByteBuffer;

/**
 * TCP Transport NIO implementation
 * 
 * @author Alexey Stashok
 * @author Jean-Francois Arcand
 */
public final class TCPNIOTransport extends AbstractNIOTransport implements
        SocketBinder, SocketConnectorHandler,
        AsyncQueueEnabledTransport, FilterChainEnabledTransport,
        TemporarySelectorsEnabledTransport {

    private static Logger logger = Grizzly.logger(TCPNIOTransport.class);

    private static final int DEFAULT_READ_BUFFER_SIZE = 8192;
    private static final int DEFAULT_WRITE_BUFFER_SIZE = 4096;
    
    private static final String DEFAULT_TRANSPORT_NAME = "TCPNIOTransport";
    /**
     * The Server connections.
     */
    protected final Collection<TCPNIOServerConnection> serverConnections;
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
    /**
     * The server socket time out
     */
    protected int serverSocketSoTimeout = 0;
    /**
     * The socket tcpDelay.
     * 
     * Default value for tcpNoDelay is disabled (set to true).
     */
    protected boolean tcpNoDelay = true;
    /**
     * The socket reuseAddress
     */
    protected boolean reuseAddress = true;
    /**
     * The socket linger.
     */
    protected int linger = -1;
    /**
     * The socket time out
     */
    protected int clientSocketSoTimeout = -1;
    /**
     * Default channel connection timeout
     */
    protected int connectionTimeout =
            TCPNIOConnectorHandler.DEFAULT_CONNECTION_TIMEOUT;
    private Filter defaultTransportFilter;
    protected final RegisterChannelCompletionHandler registerChannelCompletionHandler;
    private final EnableInterestPostProcessor enablingInterestPostProcessor;
    
    public TCPNIOTransport() {
        this(DEFAULT_TRANSPORT_NAME);
    }

    protected TCPNIOTransport(String name) {
        super(name);
        
        readBufferSize = DEFAULT_READ_BUFFER_SIZE;
        writeBufferSize = DEFAULT_WRITE_BUFFER_SIZE;

        registerChannelCompletionHandler = new RegisterChannelCompletionHandler();
        enablingInterestPostProcessor = new EnableInterestPostProcessor();

        asyncQueueIO = new AsyncQueueIO(new TCPNIOAsyncQueueReader(this),
                new TCPNIOAsyncQueueWriter(this));

        temporarySelectorIO = new TemporarySelectorIO(
                new TCPNIOTemporarySelectorReader(this),
                new TCPNIOTemporarySelectorWriter(this));

        filterChainFactory = new SingletonFilterChainFactory(
                new DefaultFilterChain());

        attributeBuilder = Grizzly.DEFAULT_ATTRIBUTE_BUILDER;
        defaultTransportFilter = new TCPNIOTransportFilter(this);
        serverConnections = new ConcurrentLinkedQueue<TCPNIOServerConnection>();
    }

    @Override
    public void start() throws IOException {
        state.getStateLocker().writeLock().lock();
        try {
            State currentState = state.getState(false);
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
                strategy = new SameThreadStrategy();
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

            listenServerConnections();
        } finally {
            state.getStateLocker().writeLock().unlock();
        }
    }

    private void listenServerConnections() {
        for (TCPNIOServerConnection serverConnection : serverConnections) {
            try {
                serverConnection.listen();
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
            if (state.getState(false) != State.START) {
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
            if (state.getState(false) != State.PAUSE) {
                logger.log(Level.WARNING,
                        "Transport is not in PAUSE state!");
            }
            state.setState(State.START);
        } finally {
            state.getStateLocker().writeLock().unlock();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TCPNIOServerConnection bind(int port) throws IOException {
        return bind(new InetSocketAddress(port));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TCPNIOServerConnection bind(String host, int port)
            throws IOException {
        return bind(host, port, 50);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TCPNIOServerConnection bind(String host, int port, int backlog)
            throws IOException {
        return bind(new InetSocketAddress(host, port), backlog);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TCPNIOServerConnection bind(SocketAddress socketAddress)
            throws IOException {
        return bind(socketAddress, 4096);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TCPNIOServerConnection bind(SocketAddress socketAddress, int backlog)
            throws IOException {
        state.getStateLocker().writeLock().lock();

        try {
            ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
            final TCPNIOServerConnection serverConnection =
                    new TCPNIOServerConnection(this, serverSocketChannel);

            serverConnections.add(serverConnection);
            
            ServerSocket serverSocket = serverSocketChannel.socket();
            serverSocket.setReuseAddress(reuseAddress);
            serverSocket.setSoTimeout(serverSocketSoTimeout);

            serverSocket.bind(socketAddress, backlog);

            serverSocketChannel.configureBlocking(false);

            if (!isStopped()) {
                serverConnection.listen();
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
                    serverConnections.remove((TCPNIOServerConnection) connection)) {
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
        TCPNIOConnectorHandler connectorHandler = new TCPNIOConnectorHandler(this);
        return connectorHandler.connect(remoteAddress, localAddress,
                completionHandler);
    }


    @Override
    protected void closeConnection(Connection connection) throws IOException {
        SelectableChannel nioChannel = ((NIOConnection) connection).getChannel();

        // channel could be either SocketChannel or ServerSocketChannel
        if (nioChannel instanceof SocketChannel) {
            Socket socket = ((SocketChannel) nioChannel).socket();

            try {
                if (!socket.isInputShutdown()) {
                    socket.shutdownInput();
                }
            } catch (IOException e) {
                logger.log(Level.FINE,
                        "TCPNIOTransport.closeChannel exception", e);
            }

            try {
                if (!socket.isOutputShutdown()) {
                    socket.shutdownOutput();
                }
            } catch (IOException e) {
                logger.log(Level.FINE,
                        "TCPNIOTransport.closeChannel exception", e);
            }

            try {
                socket.close();
            } catch (IOException e) {
                logger.log(Level.FINE,
                        "TCPNIOTransport.closeChannel exception", e);
            }
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

    protected NIOConnection obtainNIOConnection(SocketChannel channel) {
        TCPNIOConnection connection = new TCPNIOConnection(this, channel);
        connection.configureBlocking(isBlocking);
        return connection;
    }

    /**
     * Configuring <code>SocketChannel</code> according the transport settings
     * @param channel <code>SocketChannel</code> to configure
     * @throws java.io.IOException
     */
    protected void configureChannel(SocketChannel channel) throws IOException {
        Socket socket = channel.socket();

        channel.configureBlocking(false);

        if (linger >= 0) {
            socket.setSoLinger(true, linger);
        }

        try {
            socket.setTcpNoDelay(tcpNoDelay);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Can not set TcpNoDelay to " + tcpNoDelay,
                    e);
        }
        socket.setReuseAddress(reuseAddress);
    }

    @Override
    public AsyncQueueIO getAsyncQueueIO() {
        return asyncQueueIO;
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

    public int getLinger() {
        return linger;
    }

    public void setLinger(int linger) {
        this.linger = linger;
    }

    public boolean isReuseAddress() {
        return reuseAddress;
    }

    public void setReuseAddress(boolean reuseAddress) {
        this.reuseAddress = reuseAddress;
    }

    public int getClientSocketSoTimeout() {
        return clientSocketSoTimeout;
    }

    public void setClientSocketSoTimeout(int socketTimeout) {
        this.clientSocketSoTimeout = socketTimeout;
    }

    public int getConnectionTimeout() {
        return connectionTimeout;
    }

    public void setConnectionTimeout(int connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    public boolean isTcpNoDelay() {
        return tcpNoDelay;
    }

    public void setTcpNoDelay(boolean tcpNoDelay) {
        this.tcpNoDelay = tcpNoDelay;
    }

    public int getServerSocketSoTimeout() {
        return serverSocketSoTimeout;
    }

    public void setServerSocketSoTimeout(int serverSocketSoTimeout) {
        this.serverSocketSoTimeout = serverSocketSoTimeout;
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
        FilterChainFactory factory = getFilterChainFactory();
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
        return defaultTransportFilter;
    }

    @Override
    public TemporarySelectorIO getTemporarySelectorIO() {
        return temporarySelectorIO;
    }

    @Override
    public void setTemporarySelectorIO(TemporarySelectorIO temporarySelectorIO) {
        this.temporarySelectorIO = temporarySelectorIO;
    }

    @Override
    public void fireIOEvent(final IOEvent ioEvent, final Connection connection,
            final Object strategyContext) throws IOException {

        try {
            // First of all try operations, which could run in standalone mode
            if (ioEvent == IOEvent.READ) {
                processReadIoEvent(ioEvent, (TCPNIOConnection) connection,
                        strategyContext);
            } else if (ioEvent == IOEvent.WRITE) {
                processWriteIoEvent(ioEvent, (TCPNIOConnection) connection,
                        strategyContext);
            } else {
                final Processor conProcessor =
                        getConnectionProcessor(connection, ioEvent);

                if (conProcessor != null) {
                    executeProcessor(ioEvent, connection, conProcessor,
                            null, null, strategyContext);
                } else {
                    ((NIOConnection) connection).disableIOEvent(ioEvent);
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

    protected void executeProcessor(IOEvent ioEvent, Connection connection,
            Processor processor, ProcessorExecutor executor,
            PostProcessor postProcessor, Object strategyContext)
            throws IOException {

        if (logger.isLoggable(Level.FINEST)) {
            logger.log(Level.FINEST, "executeProcessor connection (" + 
                    connection + "). IOEvent=" + ioEvent +
                    " processor=" + processor + " executor=" + executor +
                    " postProcessor=" + postProcessor +
                    " strategy=" + strategyContext);
        }

        ProcessorRunnable processorRunnable = new ProcessorRunnable(ioEvent,
                connection, processor, postProcessor);

        strategy.executeProcessor(strategyContext, processorRunnable);
    }

    private void processReadIoEvent(IOEvent ioEvent,
            TCPNIOConnection connection, Object strategyContext)
            throws IOException {

        TCPNIOAsyncQueueReader asyncQueueReader =
                (TCPNIOAsyncQueueReader) getAsyncQueueIO().getReader();

        if (asyncQueueReader == null || !asyncQueueReader.isReady(connection)) {
            executeDefaultProcessor(ioEvent, connection, strategyContext);
        } else {
            connection.disableIOEvent(ioEvent);
            executeProcessor(ioEvent, connection, asyncQueueReader,
                    null, null, strategyContext);
        }
    }

    private void processWriteIoEvent(IOEvent ioEvent,
            TCPNIOConnection connection, Object strategyContext)
            throws IOException {
        AsyncQueueWriter asyncQueueWriter = getAsyncQueueIO().getWriter();
        
        if (asyncQueueWriter == null || !asyncQueueWriter.isReady(connection)) {
            executeDefaultProcessor(ioEvent, connection, strategyContext);
        } else {
            connection.disableIOEvent(ioEvent);
            executeProcessor(ioEvent, connection, asyncQueueWriter,
                    null, null, strategyContext);
        }
    }


    private void executeDefaultProcessor(IOEvent ioEvent,
            TCPNIOConnection connection, Object strategyContext)
            throws IOException {
        
        connection.disableIOEvent(ioEvent);
        Processor conProcessor = getConnectionProcessor(connection, ioEvent);
        if (conProcessor != null) {
            executeProcessor(ioEvent, connection, conProcessor, null,
                    enablingInterestPostProcessor, strategyContext);
        }
    }

    final Processor getConnectionProcessor(Connection connection, IOEvent ioEvent) {
        final Processor conProcessor = connection.getProcessor();
        final ProcessorSelector conProcessorSelector =
                connection.getProcessorSelector();

        if ((conProcessor == null || !conProcessor.isInterested(ioEvent)) &&
                conProcessorSelector != null) {
            return conProcessorSelector.select(ioEvent, connection);
        }

        return conProcessor;
    }

    public Buffer read(final Connection connection, Buffer buffer)
            throws IOException {

        final TCPNIOConnection tcpConnection = (TCPNIOConnection) connection;

        int read = 0;

        final boolean isAllocate = (buffer == null);
        if (isAllocate) {
            buffer = memoryManager.allocate(connection.getReadBufferSize());
            final ByteBuffer byteBuffer = buffer.toByteBuffer();
            
            try {
                read = ((SocketChannel) tcpConnection.getChannel()).read(byteBuffer);
            } catch (Exception e) {
                if (logger.isLoggable(Level.FINE)) {
                    logger.log(Level.FINE, "TCPNIOConnection (" + connection + ") (allocated) read exception", e);
                }
                read = -1;
            }

            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, "TCPNIOConnection (" + connection + ") (allocated) read " + read + " bytes");
            }
            
            if (read <= 0) {
                buffer.dispose();
                buffer = null;
                
                if (read < 0) {
                    throw new EOFException();
                }
            }
        } else {
            if (buffer.hasRemaining()) {
                if (buffer.isComposite()) {
                    final ByteBuffer[] byteBuffers = buffer.toByteBufferArray();
                    read = (int) ((SocketChannel) tcpConnection.getChannel()).read(byteBuffers);

                    if (read > 0) {
                        resetByteBuffers(byteBuffers, read);
                        buffer.position(buffer.position() + read);
                    }
                } else {
                    read = (int) ((SocketChannel) tcpConnection.getChannel()).read(
                            buffer.toByteBuffer());
                }

                if (logger.isLoggable(Level.FINE)) {
                    logger.log(Level.FINE, "TCPNIOConnection (" + connection + ") (nonallocated) read " + read + " bytes");
                }
                
                if (read < 0) {
                    throw new EOFException();
                }
            }
        }

        return buffer;
    }

    public int write(Connection connection, Buffer buffer) throws IOException {
        return write(connection, buffer, null);
    }

    public int write(Connection connection, Buffer buffer,
            WriteResult currentResult) throws IOException {

        TCPNIOConnection tcpConnection = (TCPNIOConnection) connection;
        int written;
        if (buffer.isComposite()) {
            final ByteBuffer[] byteBuffers = buffer.toByteBufferArray();
            written = (int) ((SocketChannel) tcpConnection.getChannel()).write(byteBuffers);

            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, "TCPNIOConnection (" + connection + ") (composite) write " + written + " bytes");
            }

            if (written > 0) {
                resetByteBuffers(byteBuffers, written);
                buffer.position(buffer.position() + written);
            }
        } else {
            final ByteBuffer byteBuffer = buffer.toByteBuffer();

            written = (int) ((SocketChannel) tcpConnection.getChannel()).write(byteBuffer);
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, "TCPNIOConnection (" + connection + ") (plain) write " + written + " bytes");
            }
        }

        if (written >= 0) {
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

    @Override
    public StreamReader getStreamReader(Connection connection) {
        return new TCPNIOStreamReader((TCPNIOConnection) connection);
    }

    @Override
    public StreamWriter getStreamWriter(Connection connection) {
        return new TCPNIOStreamWriter((TCPNIOConnection) connection);
    }

    private void resetByteBuffers(final ByteBuffer[] byteBuffers, int processed) {
        int index = 0;
        while(processed > 0) {
            final ByteBuffer byteBuffer = byteBuffers[index++];
            byteBuffer.position(0);
            processed -= byteBuffer.remaining();
        }
    }

    public class EnableInterestPostProcessor implements PostProcessor {

        @Override
        public void process(ProcessorResult result,
                Context context) throws IOException {
            if (result == null || result.getStatus() == Status.OK) {
                IOEvent ioEvent = context.getIoEvent();
                ((NIOConnection) context.getConnection()).enableIOEvent(ioEvent);
            }
        }
    }
    
    protected class RegisterChannelCompletionHandler
            extends CompletionHandlerAdapter<RegisterChannelResult> {

        @Override
        public void completed(RegisterChannelResult result) {
            try {
                SelectionKey selectionKey = result.getSelectionKey();

                TCPNIOConnection connection =
                        (TCPNIOConnection) getSelectionKeyHandler().
                        getConnectionForKey(selectionKey);

                if (connection != null) {
                    SelectorRunner selectorRunner = result.getSelectorRunner();
                    connection.setSelectionKey(selectionKey);
                    connection.setSelectorRunner(selectorRunner);
                    connection.resetAddresses();
                }
            } catch (Exception e) {
                logger.log(Level.FINE, "Exception happened, when " +
                        "trying to register the channel", e);
            }
        }
    }
}
