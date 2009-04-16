/*
 * 
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2007-2008 Sun Microsystems, Inc. All rights reserved.
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
package org.glassfish.grizzly.nio.transport;

import org.glassfish.grizzly.strategies.WorkerThreadStrategy;
import org.glassfish.grizzly.asyncqueue.AsyncQueueIO;
import org.glassfish.grizzly.nio.RegisterChannelResult;
import org.glassfish.grizzly.nio.RoundRobinConnectionDistributor;
import org.glassfish.grizzly.nio.DefaultSelectorHandler;
import org.glassfish.grizzly.nio.DefaultSelectionKeyHandler;
import org.glassfish.grizzly.nio.AbstractNIOTransport;
import org.glassfish.grizzly.asyncqueue.AsyncQueueEnabledTransport;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.IOEvent;
import org.glassfish.grizzly.Context;
import org.glassfish.grizzly.Processor;
import org.glassfish.grizzly.nio.NIOConnection;
import org.glassfish.grizzly.SocketAcceptor;
import org.glassfish.grizzly.asyncqueue.AsyncQueueReader;
import org.glassfish.grizzly.asyncqueue.AsyncQueueWriter;
import org.glassfish.grizzly.filterchain.DefaultFilterChain;
import org.glassfish.grizzly.filterchain.Filter;
import org.glassfish.grizzly.filterchain.FilterChain;
import org.glassfish.grizzly.filterchain.FilterChainEnabledTransport;
import org.glassfish.grizzly.filterchain.FilterChainFactory;
import org.glassfish.grizzly.filterchain.PatternFilterChainFactory;
import org.glassfish.grizzly.filterchain.SingletonFilterChainFactory;
import org.glassfish.grizzly.threadpool.DefaultThreadPool;
import org.glassfish.grizzly.nio.tmpselectors.TemporarySelectorPool;
import org.glassfish.grizzly.nio.tmpselectors.TemporarySelectorsEnabledTransport;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.WritableByteChannel;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.CompletionHandlerAdapter;
import org.glassfish.grizzly.PostProcessor;
import org.glassfish.grizzly.ProcessorExecutor;
import org.glassfish.grizzly.ProcessorResult;
import org.glassfish.grizzly.ProcessorResult.Status;
import org.glassfish.grizzly.ProcessorRunnable;
import org.glassfish.grizzly.ProcessorSelector;
import org.glassfish.grizzly.ReadResult;
import org.glassfish.grizzly.SocketConnectorHandler;
import org.glassfish.grizzly.WriteResult;
import org.glassfish.grizzly.nio.SelectorRunner;
import org.glassfish.grizzly.nio.tmpselectors.TemporarySelectorIO;
import org.glassfish.grizzly.threadpool.ExtendedThreadPool;

/**
 * TCP Transport NIO implementation
 * 
 * @author Alexey Stashok
 * @author Jean-Francois Arcand
 */
public class TCPNIOTransport extends AbstractNIOTransport implements
        SocketAcceptor, SocketConnectorHandler, AsyncQueueEnabledTransport,
        FilterChainEnabledTransport,
        TemporarySelectorsEnabledTransport {

    private Logger logger = Grizzly.logger;
    
    private static final int DEFAULT_READ_BUFFER_SIZE = 65536;
    private static final int DEFAULT_WRITE_BUFFER_SIZE = 4096;
    
    private static final String DEFAULT_TRANSPORT_NAME = "TCPNIOTransport";
    /**
     * Default SelectorRunners count
     */
    private static final int DEFAULT_SELECTOR_RUNNERS_COUNT = 2;
    /**
     * The Server connection.
     */
    protected TCPNIOServerConnection serverConnection;
    /**
     * FilterChainFactory implementation
     */
    protected FilterChainFactory filterChainFactory;
    /**
     * Transport AsyncQueueIO
     */
    protected AsyncQueueIO asyncQueueIO;
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
    /**
     * Server socket backlog.
     */
    protected TemporarySelectorIO temporarySelectorIO;
    private Filter defaultTransportFilter;
    protected RegisterChannelCompletionHandler registerChannelCompletionHandler;
    private EnableInterestPostProcessor enablingInterestPostProcessor;
    
    public TCPNIOTransport() {
        this(DEFAULT_TRANSPORT_NAME);
        readBufferSize = DEFAULT_READ_BUFFER_SIZE;
        writeBufferSize = DEFAULT_WRITE_BUFFER_SIZE;
        
        registerChannelCompletionHandler = new RegisterChannelCompletionHandler();
        enablingInterestPostProcessor = new EnableInterestPostProcessor();

        asyncQueueIO = new AsyncQueueIO(new TCPNIOAsyncQueueReader(this),
                new TCPNIOAsyncQueueWriter(this));

        temporarySelectorIO = new TemporarySelectorIO(
                new TCPNIOTemporarySelectorReader(this),
                new TCPNIOTemporarySelectorWriter(this));

        PatternFilterChainFactory patternFactory =
                new SingletonFilterChainFactory();
        FilterChain filterChain = new DefaultFilterChain(patternFactory);
        patternFactory.setFilterChainPattern(filterChain);

        filterChainFactory = patternFactory;


        defaultTransportFilter = new TCPNIOTransportFilter(this);
    }

    protected TCPNIOTransport(String name) {
        super(name);
    }

    @Override
    public void start() throws IOException {
        state.getStateLocker().writeLock().lock();
        try {
            State currentState = state.getState(false);
            if (currentState != State.STOP) {
                Grizzly.logger.log(Level.WARNING,
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
                selectorRunnersCount = DEFAULT_SELECTOR_RUNNERS_COUNT;
            }

            if (nioChannelDistributor == null) {
                nioChannelDistributor = new RoundRobinConnectionDistributor(this);
            }

            if (strategy == null) {
                strategy = new WorkerThreadStrategy(this);
            }
            
            if (internalThreadPool == null) {
                internalThreadPool = new DefaultThreadPool(
                        selectorRunnersCount * 2,
                        selectorRunnersCount * 4, 1, 5, TimeUnit.SECONDS);
            }

            if (workerThreadPool == null) {
                workerThreadPool = new DefaultThreadPool();
            }

            /* By default TemporarySelector pool size should be equal
            to the number of processing threads */
            int selectorPoolSize =
                    TemporarySelectorPool.DEFAULT_SELECTORS_COUNT;
            if (workerThreadPool instanceof ExtendedThreadPool) {
                selectorPoolSize =((ExtendedThreadPool) workerThreadPool).
                        getMaximumPoolSize();
            }
            temporarySelectorIO.setSelectorPool(
                    new TemporarySelectorPool(selectorPoolSize));

            startSelectorRunners();

            if (serverConnection != null) {
                serverConnection.listen();
            }
        } finally {
            state.getStateLocker().writeLock().unlock();
        }
    }

    @Override
    public void stop() throws IOException {
        state.getStateLocker().writeLock().lock();

        try {
            state.setState(State.STOP);
            stopSelectorRunners();

            if (internalThreadPool != null) {
                internalThreadPool.shutdown();
                internalThreadPool = null;
            }

            if (serverConnection != null) {
                serverConnection.close();
            }
        } finally {
            state.getStateLocker().writeLock().unlock();
        }
    }

    @Override
    public void pause() throws IOException {
        state.getStateLocker().writeLock().lock();

        try {
            if (state.getState(false) != State.START) {
                Grizzly.logger.log(Level.WARNING,
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
                Grizzly.logger.log(Level.WARNING,
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
    public void bind(int port) throws IOException {
        bind(new InetSocketAddress(port));
    }

    /**
     * {@inheritDoc}
     */
    public void bind(String host, int port) throws IOException {
        bind(host, port, 50);
    }

    /**
     * {@inheritDoc}
     */
    public void bind(String host, int port, int backlog) throws IOException {
        bind(new InetSocketAddress(host, port), backlog);
    }

    /**
     * {@inheritDoc}
     */
    public void bind(SocketAddress socketAddress) throws IOException {
        bind(socketAddress, 4096);
    }

    /**
     * {@inheritDoc}
     */
    public void bind(SocketAddress socketAddress, int backlog) throws IOException {
        state.getStateLocker().writeLock().lock();

        try {
            if (state.getState(false) != State.STOP) {
                Grizzly.logger.log(Level.WARNING,
                        "Transport is not in STOPPED state!");
            }

            ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
            serverConnection = new TCPNIOServerConnection(this,
                    serverSocketChannel);

            ServerSocket serverSocket = serverSocketChannel.socket();
            serverSocket.setReuseAddress(reuseAddress);
            serverSocket.setSoTimeout(serverSocketSoTimeout);

            serverSocket.bind(socketAddress, backlog);

            serverSocketChannel.configureBlocking(false);
        } finally {
            state.getStateLocker().writeLock().unlock();
        }
    }

    public Future<Connection> accept() throws IOException {
        return serverConnection.accept();
    }

    public Future<Connection> connect(String host, int port)
            throws IOException {
        return connect(new InetSocketAddress(host, port));
    }

    public Future<Connection> connect(SocketAddress remoteAddress)
            throws IOException {
        return connect(remoteAddress, null);
    }

    public Future<Connection> connect(SocketAddress remoteAddress,
            SocketAddress localAddress) throws IOException {

        TCPNIOConnectorHandler connectorHandler = new TCPNIOConnectorHandler(this);
        return connectorHandler.connect(remoteAddress, localAddress);
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
                Grizzly.logger.log(Level.FINE,
                        "TCPNIOTransport.closeChannel exception", e);
            }

            try {
                if (!socket.isOutputShutdown()) {
                    socket.shutdownOutput();
                }
            } catch (IOException e) {
                Grizzly.logger.log(Level.FINE,
                        "TCPNIOTransport.closeChannel exception", e);
            }

            try {
                socket.close();
            } catch (IOException e) {
                Grizzly.logger.log(Level.FINE,
                        "TCPNIOTransport.closeChannel exception", e);
            }
        }

        if (nioChannel != null) {
            try {
                nioChannel.close();
            } catch (IOException e) {
                Grizzly.logger.log(Level.FINE,
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

    protected NIOConnection obtainNIOConnection(SelectableChannel channel) {
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

        socket.setTcpNoDelay(tcpNoDelay);
        socket.setReuseAddress(reuseAddress);
    }

    public AsyncQueueIO getAsyncQueueIO() {
        return asyncQueueIO;
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

    public FilterChainFactory getFilterChainFactory() {
        return filterChainFactory;
    }

    public void setFilterChainFactory(FilterChainFactory factory) {
        filterChainFactory = factory;
    }

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

    public void setFilterChain(FilterChain filterChain) {
        FilterChainFactory factory = getFilterChainFactory();
        if (factory instanceof PatternFilterChainFactory) {
            ((PatternFilterChainFactory) factory).setFilterChainPattern(filterChain);
            return;
        }

        throw new IllegalStateException(
                "Transport FilterChainFactory doesn't " +
                "support creating of FilterChain by a patterns. " +
                "It means you have to add/remove Filters using " +
                "FilterChainFactory API: " + factory.getClass().getName());
    }

    public Filter getDefaultTransportFilter() {
        return defaultTransportFilter;
    }

    public TemporarySelectorIO getTemporarySelectorIO() {
        return temporarySelectorIO;
    }

    public void setTemporarySelectorIO(TemporarySelectorIO temporarySelectorIO) {
        this.temporarySelectorIO = temporarySelectorIO;
    }

    public void fireIOEvent(IOEvent ioEvent, Connection connection,
            Object strategyContext) throws IOException {

        try {
            // First of all try operations, which could run in standalone mode
            if (ioEvent == IOEvent.READ) {
                processReadIoEvent(ioEvent, (TCPNIOConnection) connection,
                        strategyContext);
            } else if (ioEvent == IOEvent.WRITE) {
                processWriteIoEvent(ioEvent, (TCPNIOConnection) connection,
                        strategyContext);
            } else {
                if (ioEvent == IOEvent.SERVER_ACCEPT &&
                        serverConnection.tryAccept()) {
                    return;
                }

                Processor conProcessor = getConnectionProcessor(connection, ioEvent);

                if (conProcessor != null) {
                    executeProcessor(ioEvent, connection, conProcessor,
                            null, null, strategyContext);
                } else {
                    disableInterest((NIOConnection) connection,ioEvent);
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
            disableInterest(connection, ioEvent);
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
            disableInterest(connection, ioEvent);
            executeProcessor(ioEvent, connection, asyncQueueWriter,
                    null, null, strategyContext);
        }
    }


    private void executeDefaultProcessor(IOEvent ioEvent,
            TCPNIOConnection connection, Object strategyContext)
            throws IOException {
        
        disableInterest(connection, ioEvent);
        Processor conProcessor = getConnectionProcessor(connection, ioEvent);
        if (conProcessor != null) {
            executeProcessor(ioEvent, connection, conProcessor, null,
                    enablingInterestPostProcessor, strategyContext);
        }
    }

    Processor getConnectionProcessor(Connection connection, IOEvent ioEvent) {
        Processor conProcessor = connection.getProcessor();
        ProcessorSelector conProcessorSelector =
                connection.getProcessorSelector();

        if ((conProcessor == null || !conProcessor.isInterested(ioEvent)) &&
                conProcessorSelector != null) {
            conProcessor = conProcessorSelector.select(ioEvent, connection);
        }

        return conProcessor;
    }

    void enableInterest(NIOConnection connection,
            IOEvent ioEvent) throws IOException {
        SelectionKey key = connection.getSelectionKey();

        selectorHandler.registerKey(
                connection.getSelectorRunner(), key,
                selectionKeyHandler.ioEvent2SelectionKeyInterest(ioEvent));
    }

    void disableInterest(NIOConnection connection,
            IOEvent ioEvent) throws IOException {
        SelectionKey key = connection.getSelectionKey();
        
        getSelectorHandler().unregisterKey(
                connection.getSelectorRunner(), key,
                getSelectionKeyHandler().ioEvent2SelectionKeyInterest(ioEvent));
    }

    public int read(Connection connection, Buffer buffer) throws IOException {
        return read(connection, buffer, null);
    }

    public int read(Connection connection, Buffer buffer,
            ReadResult currentResult) throws IOException {

        int read = 0;

        boolean isAllocated = false;
        if (buffer == null && currentResult != null) {

            buffer = memoryManager.allocate(
                    connection.getReadBufferSize());
            isAllocated = true;
        }

        if (buffer.hasRemaining()) {
            TCPNIOConnection tcpConnection = (TCPNIOConnection) connection;
            read = ((ReadableByteChannel) tcpConnection.getChannel()).read(
                    (ByteBuffer) buffer.underlying());
        }

        if (isAllocated) {
            if (read > 0) {
                buffer.trim();
                buffer.position(buffer.limit());
            } else {
                buffer.dispose();
                buffer = null;
            }
        }

        if (currentResult != null && read >= 0) {
            currentResult.setMessage(buffer);
            currentResult.setReadSize(currentResult.getReadSize() + read);
            currentResult.setSrcAddress(connection.getPeerAddress());
        }

        return read;
    }

    public int write(Connection connection, Buffer buffer) throws IOException {
        return write(connection, buffer, null);
    }

    public int write(Connection connection, Buffer buffer,
            WriteResult currentResult) throws IOException {

        TCPNIOConnection tcpConnection = (TCPNIOConnection) connection;
        int written = ((WritableByteChannel) tcpConnection.getChannel()).write(
                (ByteBuffer) buffer.underlying());
        if (currentResult != null) {
            currentResult.setMessage(buffer);
            currentResult.setWrittenSize(currentResult.getWrittenSize() +
                    written);
            currentResult.setDstAddress(
                    connection.getPeerAddress());
        }

        return written;
    }

    public class EnableInterestPostProcessor
            implements PostProcessor {

        public void process(ProcessorResult result,
                Context context) throws IOException {
            if (result == null || result.getStatus() == Status.OK) {
                IOEvent ioEvent = context.getIoEvent();
                enableInterest(
                        (NIOConnection) context.getConnection(), ioEvent);
            }
        }
    }
    
    protected class RegisterChannelCompletionHandler
            extends CompletionHandlerAdapter<RegisterChannelResult> {

        @Override
        public void completed(Connection c, RegisterChannelResult result) {
            try {
                SelectionKey selectionKey = result.getSelectionKey();

                TCPNIOConnection connection =
                        (TCPNIOConnection) getSelectionKeyHandler().
                        getConnectionForKey(selectionKey);

                if (connection != null) {
                    SelectorRunner selectorRunner = result.getSelectorRunner();
                    connection.setSelectionKey(selectionKey);
                    connection.setSelectorRunner(selectorRunner);
                }
            } catch (Exception e) {
                Grizzly.logger.log(Level.FINE, "Exception happened, when " +
                        "trying to register the channel", e);
            }
        }
    }
}
