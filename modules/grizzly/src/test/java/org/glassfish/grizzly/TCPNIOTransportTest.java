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

package org.glassfish.grizzly;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.util.Arrays;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.grizzly.CloseType;
import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.FilterChainBuilder;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.filterchain.TransportFilter;
import org.glassfish.grizzly.impl.FutureImpl;
import org.glassfish.grizzly.impl.SafeFutureImpl;
import org.glassfish.grizzly.memory.Buffers;
import org.glassfish.grizzly.memory.ByteBufferWrapper;
import org.glassfish.grizzly.memory.MemoryManager;
import org.glassfish.grizzly.nio.AbstractNIOConnectionDistributor;
import org.glassfish.grizzly.nio.NIOConnection;
import org.glassfish.grizzly.nio.NIOTransport;
import org.glassfish.grizzly.nio.RegisterChannelResult;
import org.glassfish.grizzly.nio.SelectorRunner;
import org.glassfish.grizzly.nio.transport.TCPNIOConnectorHandler;
import org.glassfish.grizzly.nio.transport.TCPNIOServerConnection;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import org.glassfish.grizzly.nio.transport.TCPNIOTransportBuilder;
import org.glassfish.grizzly.strategies.SameThreadIOStrategy;
import org.glassfish.grizzly.strategies.WorkerThreadIOStrategy;
import org.glassfish.grizzly.threadpool.ThreadPoolConfig;
import org.glassfish.grizzly.utils.BufferInQueueFilter;
import org.glassfish.grizzly.utils.ClientCheckFilter;
import org.glassfish.grizzly.utils.DataStructures;
import org.glassfish.grizzly.utils.EchoFilter;
import org.glassfish.grizzly.utils.InQueueFilter;
import org.glassfish.grizzly.utils.ParallelWriteFilter;
import org.glassfish.grizzly.utils.RandomDelayOnWriteFilter;
import org.glassfish.grizzly.utils.StringFilter;


/**
 * Unit test for {@link TCPNIOTransport}
 *
 * @author Alexey Stashok
 */
@SuppressWarnings("unchecked")
public class TCPNIOTransportTest extends GrizzlyTestCase {

    public static final int PORT = 7777;

    private static final Logger LOGGER = Grizzly.logger(TCPNIOTransportTest.class);

    @Override
    protected void setUp() throws Exception {
        ByteBufferWrapper.DEBUG_MODE = true;
    }


    public void testStartStop() throws IOException {
        TCPNIOTransport transport = TCPNIOTransportBuilder.newInstance().build();

        try {
            transport.bind(PORT);
            transport.start();
        } finally {
            transport.stop();
        }
    }

    public void testConnectorHandlerConnect() throws Exception {
        Connection connection = null;
        TCPNIOTransport transport = TCPNIOTransportBuilder.newInstance().build();

        try {
            transport.bind(PORT);
            transport.start();

            Future<Connection> future = transport.connect("localhost", PORT);
            connection = future.get(10, TimeUnit.SECONDS);
            assertTrue(connection != null);
        } finally {
            if (connection != null) {
                connection.closeSilently();
            }

            transport.stop();
        }
    }

    public void testBindUnbind() throws Exception {
        Connection connection = null;
        TCPNIOTransport transport = TCPNIOTransportBuilder.newInstance().build();

        try {
            transport.bind(PORT);
            transport.start();

            Future<Connection> future = transport.connect("localhost", PORT);
            connection = future.get(10, TimeUnit.SECONDS);
            assertTrue(connection != null);
            connection.closeSilently();

            transport.unbindAll();

            future = transport.connect("localhost", PORT);
            try {
                future.get(10, TimeUnit.SECONDS);
                assertTrue("Server connection should be closed!", false);
            } catch (ExecutionException e) {
                assertTrue(e.getCause() instanceof IOException);
            }

            transport.bind(PORT);

            future = transport.connect("localhost", PORT);
            connection = future.get(10, TimeUnit.SECONDS);
            assertTrue(connection != null);
        } finally {
            if (connection != null) {
                connection.closeSilently();
            }

            transport.stop();
        }
    }

    public void testMultiBind() throws Exception {
        Connection connection = null;
        TCPNIOTransport transport = TCPNIOTransportBuilder.newInstance().build();

        try {
            final TCPNIOServerConnection serverConnection1 = transport.bind(PORT);
            final TCPNIOServerConnection serverConnection2 = transport.bind(PORT + 1);

            transport.start();

            Future<Connection> future = transport.connect("localhost", PORT);
            connection = future.get(10, TimeUnit.SECONDS);
            assertTrue(connection != null);
            connection.closeSilently();

            future = transport.connect("localhost", PORT + 1);
            connection = future.get(10, TimeUnit.SECONDS);
            assertTrue(connection != null);
            connection.closeSilently();

            transport.unbind(serverConnection1);

            future = transport.connect("localhost", PORT);
            try {
                connection = future.get(10, TimeUnit.SECONDS);
                assertTrue("Server connection should be closed!", false);
            } catch (ExecutionException e) {
                assertTrue(e.getCause() instanceof IOException);
            }

            transport.unbind(serverConnection2);
            future = transport.connect("localhost", PORT + 1);
            try {
                connection = future.get(10, TimeUnit.SECONDS);
                assertTrue("Server connection should be closed!", false);
            } catch (ExecutionException e) {
                assertTrue(e.getCause() instanceof IOException);
            }

        } finally {
            if (connection != null) {
                connection.closeSilently();
            }

            transport.stop();
        }
    }

    public void testPortRangeBind() throws Exception {
        final int portsTest = 10;
        final int startPort = PORT + 1234;
        final PortRange portRange = new PortRange(startPort, startPort + portsTest - 1);

        Connection connection;
        TCPNIOTransport transport = TCPNIOTransportBuilder.newInstance()
                .setReuseAddress(false)
                .build();

        try {
            for (int i = 0; i < portsTest; i++) {
                        transport.bind("localhost", portRange, 4096);
            }

            try {
                transport.bind("localhost", portRange, 4096);
                fail("All ports in range had to be occupied");
            } catch (IOException e) {
                // must be thrown
            }

            transport.start();

            for (int i = 0; i < portsTest; i++) {
                Future<Connection> future = transport.connect("localhost", startPort + i);
                connection = future.get(10, TimeUnit.SECONDS);
                assertTrue(connection != null);
                connection.closeSilently();
            }
        } finally {
            transport.stop();
        }
    }
    
    public void testConnectorHandlerConnectAndWrite() throws Exception {
        Connection connection = null;
        TCPNIOTransport transport = TCPNIOTransportBuilder.newInstance().build();
        
        transport.setFilterChain(FilterChainBuilder.stateless()
                .add(new TransportFilter())
                .build());

        try {
            transport.bind(PORT);
            transport.start();

            final Future<Connection> connectFuture = transport.connect(
                    new InetSocketAddress("localhost", PORT));
            
            connection = connectFuture.get(10, TimeUnit.SECONDS);
            assertTrue(connection != null);

            connection.configureBlocking(true);
            final Buffer sendingBuffer = Buffers.wrap(
                    transport.getMemoryManager(), "Hello");
            final int bufferSize = sendingBuffer.remaining();

            Future<WriteResult> writeFuture = connection.write(sendingBuffer);
            
            WriteResult writeResult = writeFuture.get(10, TimeUnit.SECONDS);
            assertTrue(writeFuture.isDone());
            assertEquals(bufferSize, writeResult.getWrittenSize());
        } finally {
            if (connection != null) {
                connection.closeSilently();
            }

            transport.stop();
        }
    }

    public void testClose() throws Exception {
        final BlockingQueue<Connection> acceptedQueue = DataStructures.getLTQInstance();
        
        Connection<?> connectedConnection = null;
        Connection acceptedConnection = null;

        TCPNIOTransport transport = TCPNIOTransportBuilder.newInstance().build();

        try {
            FilterChainBuilder filterChainBuilder = FilterChainBuilder.stateless();
            filterChainBuilder.add(new TransportFilter());
            filterChainBuilder.add(new BaseFilter() {

                @Override
                public NextAction handleAccept(final FilterChainContext ctx)
                        throws IOException {
                    acceptedQueue.offer(ctx.getConnection());
                    return ctx.getInvokeAction();
                }
            });
            
            transport.setFilterChain(filterChainBuilder.build());

            transport.bind(PORT);
            transport.start();
            
            Future<Connection> connectFuture = transport.connect(
                    new InetSocketAddress("localhost", PORT));
                        
            connectedConnection = connectFuture.get(10, TimeUnit.SECONDS);
            acceptedConnection = acceptedQueue.poll(10, TimeUnit.SECONDS);
            
            final FutureImpl<Boolean> connectedCloseFuture = new SafeFutureImpl<Boolean>();
            final FutureImpl<Boolean> acceptedCloseFuture = new SafeFutureImpl<Boolean>();
            
            connectedConnection.addCloseListener(new CloseListener<Connection>() {

                @Override
                public void onClosed(Connection connection, CloseType type) throws IOException {
                    connectedCloseFuture.result(type == CloseType.LOCALLY);
                }
            });
            
            acceptedConnection.addCloseListener(new CloseListener() {

                @Override
                public void onClosed(Closeable closeable, CloseType type) throws IOException {
                    acceptedCloseFuture.result(type == CloseType.REMOTELY);
                }
            });

            connectedConnection.closeSilently();

            assertTrue(connectedCloseFuture.get(10, TimeUnit.SECONDS));
            assertTrue(acceptedCloseFuture.get(10, TimeUnit.SECONDS));
        } finally {
            if (acceptedConnection != null) {
                acceptedConnection.closeSilently();
            }

            if (connectedConnection != null) {
                connectedConnection.closeSilently();
            }

            transport.stop();
        }        
    }
    
    public void testSimpleEcho() throws Exception {
        Connection connection = null;

        FilterChainBuilder serverChainBuilder = FilterChainBuilder.stateless();
        serverChainBuilder.add(new TransportFilter());
        serverChainBuilder.add(new StringFilter());
        serverChainBuilder.add(new EchoFilter());

        TCPNIOTransport transport = TCPNIOTransportBuilder.newInstance().build();
        transport.setFilterChain(serverChainBuilder.build());

        try {
            transport.bind(PORT);
            transport.start();

            final InQueueFilter<String> inQueueFilter = new InQueueFilter<String>();
            final FilterChainBuilder clientChainBuilder = FilterChainBuilder.stateless()
                    .add(new TransportFilter())
                    .add(new StringFilter())
                    .add(inQueueFilter);

            final TCPNIOConnectorHandler connectorHandler = 
                    TCPNIOConnectorHandler.builder(transport)
                    .filterChain(clientChainBuilder.build())
                    .build();
            
            final Future<Connection> connectFuture = connectorHandler.connect(
                    new InetSocketAddress("localhost", PORT));
            
            connection = connectFuture.get(10, TimeUnit.SECONDS);
            assertTrue(connection != null);

            connection.configureBlocking(true);
            
            final String testString = "Hello";
            Future<WriteResult> writeFuture = connection.write(testString);
            assertTrue("Write timeout", writeFuture.isDone());

            final String inString = inQueueFilter.poll(10, TimeUnit.SECONDS);

            assertEquals(inString, testString);
        } finally {
            if (connection != null) {
                connection.closeSilently();
            }

            transport.stop();
        }
    }

    public void testSeveralPacketsEcho() throws Exception {
        Connection connection = null;

        FilterChainBuilder filterChainBuilder = FilterChainBuilder.stateless()
                .add(new TransportFilter())
                .add(new StringFilter())
                .add(new EchoFilter());
        
        TCPNIOTransport transport = TCPNIOTransportBuilder.newInstance().build();
        transport.setFilterChain(filterChainBuilder.build());

        try {
            transport.bind(PORT);
            transport.start();
            transport.configureBlocking(true);

            final InQueueFilter<String> inQueueFilter = new InQueueFilter<String>();
            final FilterChainBuilder clientChainBuilder = FilterChainBuilder.stateless()
                    .add(new TransportFilter())
                    .add(new StringFilter())
                    .add(inQueueFilter);

            final TCPNIOConnectorHandler connectorHandler = 
                    TCPNIOConnectorHandler.builder(transport)
                    .filterChain(clientChainBuilder.build())
                    .build();
            
            final Future<Connection> connectFuture = connectorHandler.connect(
                    new InetSocketAddress("localhost", PORT));
            
            connection = connectFuture.get(10, TimeUnit.SECONDS);
            assertTrue(connection != null);

            for (int i = 0; i < 100; i++) {
                String originalMessage = "Hello world #" + i;
                Future<WriteResult> writeFuture = connection.write(originalMessage);
                
                assertTrue("Write timeout", writeFuture.isDone());

                String echoMessage = inQueueFilter.poll(10, TimeUnit.SECONDS);
                assertEquals(echoMessage, originalMessage);
            }
        } finally {
            if (connection != null) {
                connection.closeSilently();
            }

            transport.stop();
        }
    }

    public void testAsyncReadWriteEcho() throws Exception {
        Connection connection = null;

        FilterChainBuilder filterChainBuilder = FilterChainBuilder.stateless()
                .add(new TransportFilter())
                .add(new StringFilter())
                .add(new EchoFilter());
        
        TCPNIOTransport transport = TCPNIOTransportBuilder.newInstance().build();
        transport.setFilterChain(filterChainBuilder.build());

        try {
            transport.bind(PORT);
            transport.start();

            final InQueueFilter<String> inQueueFilter = new InQueueFilter<String>();
            final FilterChainBuilder clientChainBuilder = FilterChainBuilder.stateless()
                    .add(new TransportFilter())
                    .add(new StringFilter())
                    .add(inQueueFilter);

            final TCPNIOConnectorHandler connectorHandler = 
                    TCPNIOConnectorHandler.builder(transport)
                    .filterChain(clientChainBuilder.build())
                    .build();
            
            final Future<Connection> connectFuture = connectorHandler.connect(
                    new InetSocketAddress("localhost", PORT));
            
            connection = connectFuture.get(10, TimeUnit.SECONDS);
            assertTrue(connection != null);

            final String testString = "Hello";
            Future<WriteResult> writeFuture = connection.write(testString);
            assertTrue("Write timeout", writeFuture.isDone());

            final String inString = inQueueFilter.poll(10, TimeUnit.SECONDS);

            assertEquals(inString, testString);
        } finally {
            if (connection != null) {
                connection.closeSilently();
            }

            transport.stop();
        }
    }

    public void testSeveralPacketsAsyncReadWriteEcho() throws Exception {
        int packetsNumber = 20;
        final int packetSize = 17644;
        final AtomicInteger serverBytesCounter = new AtomicInteger();

        Connection connection = null;
        
        FilterChainBuilder filterChainBuilder = FilterChainBuilder.stateless();
        filterChainBuilder.add(new TransportFilter());
        filterChainBuilder.add(new EchoFilter() {

            @Override
            public NextAction handleRead(FilterChainContext ctx)
                    throws IOException {
                
                serverBytesCounter.addAndGet(((Buffer) ctx.getMessage()).remaining());
                return super.handleRead(ctx);
            }
        });

        TCPNIOTransport transport = TCPNIOTransportBuilder.newInstance()
                .setMaxAsyncWriteQueueSizeInBytes(-1)
                .build();
        transport.setFilterChain(filterChainBuilder.build());

        try {
            transport.bind(PORT);
            transport.start();

            final BufferInQueueFilter inQueueFilter = new BufferInQueueFilter(packetSize);
            final FilterChainBuilder clientChainBuilder = FilterChainBuilder.stateless()
                    .add(new TransportFilter())
                    .add(inQueueFilter);

            final TCPNIOConnectorHandler connectorHandler = 
                    TCPNIOConnectorHandler.builder(transport)
                    .filterChain(clientChainBuilder.build())
                    .build();
            
            final Future<Connection> connectFuture = connectorHandler.connect(
                    new InetSocketAddress("localhost", PORT));
            
            connection = connectFuture.get(10, TimeUnit.SECONDS);
            assertTrue(connection != null);

            final CountDownLatch sendLatch = new CountDownLatch(packetsNumber);

            for (int i = 0; i < packetsNumber; i++) {
                final byte[] message = new byte[packetSize];
                Arrays.fill(message, (byte) i);

                connection.write(Buffers.wrap(transport.getMemoryManager(), message),
                        new EmptyCompletionHandler<WriteResult>() {

                    @Override
                    public void completed(WriteResult result) {
                        assertEquals(message.length, result.getWrittenSize());
                        sendLatch.countDown();
                    }

                    @Override
                    public void failed(Throwable throwable) {
                        LOGGER.log(Level.WARNING, "failure", throwable);
                    }
                });
            }

            for (int i = 0; i < packetsNumber; i++) {
                byte[] pattern = new byte[packetSize];
                Arrays.fill(pattern, (byte) i);

                Buffer message = inQueueFilter.poll(10, TimeUnit.SECONDS);
                
                assertTrue(Buffers.wrap(transport.getMemoryManager(), pattern).equals(message));
            }

            sendLatch.await(10, TimeUnit.SECONDS);
            assertEquals(0, sendLatch.getCount());
        } finally {
            if (connection != null) {
                connection.closeSilently();
            }

            transport.stop();
        }
    }
    
    public void testChunkedMessageWithSleep() throws Exception {
        class CheckSizeFilter extends BaseFilter {
            private int size;
            private CountDownLatch latch;

            public CheckSizeFilter(int size) {
                latch = new CountDownLatch(1);
                this.size = size;
            }

            @Override
            public NextAction handleRead(FilterChainContext ctx)
                    throws IOException {
                final Buffer buffer = ctx.getMessage();
                LOGGER.log(Level.INFO, "Feeder. Check size filter: {0}", buffer);
                if (buffer.remaining() >= size) {
                    latch.countDown();
                    return ctx.getInvokeAction();
                }

                return ctx.getStopAction(buffer);
            }

        }

        int fullMessageSize = 2048;

        Connection connection = null;

        CheckSizeFilter checkSizeFilter = new CheckSizeFilter(fullMessageSize);

        FilterChainBuilder filterChainBuilder = FilterChainBuilder.stateless();
        filterChainBuilder.add(new TransportFilter());
        filterChainBuilder.add(checkSizeFilter);
        filterChainBuilder.add(new EchoFilter());

        TCPNIOTransport transport = TCPNIOTransportBuilder.newInstance().build();
        transport.setFilterChain(filterChainBuilder.build());

        try {
            transport.bind(PORT);
            transport.start();

            final BufferInQueueFilter inQueueFilter = new BufferInQueueFilter(fullMessageSize);
            final FilterChainBuilder clientChainBuilder = FilterChainBuilder.stateless()
                    .add(new TransportFilter())
                    .add(inQueueFilter);

            final TCPNIOConnectorHandler connectorHandler = 
                    TCPNIOConnectorHandler.builder(transport)
                    .filterChain(clientChainBuilder.build())
                    .build();
            
            final Future<Connection> connectFuture = connectorHandler.connect(
                    new InetSocketAddress("localhost", PORT));
            
            connection = connectFuture.get(10, TimeUnit.SECONDS);
            assertTrue(connection != null);

            final MemoryManager mm = transport.getMemoryManager();
            
            byte[] firstChunk = new byte[fullMessageSize / 5];
            Arrays.fill(firstChunk, (byte) 1);
            Future<WriteResult> writeFuture = connection.write(Buffers.wrap(mm, firstChunk));
            assertTrue("First chunk write timeout", writeFuture.get(10, TimeUnit.SECONDS) != null);

            Thread.sleep(1000);
            
            byte[] secondChunk = new byte[fullMessageSize - firstChunk.length];
            Arrays.fill(secondChunk, (byte) 2);
            writeFuture = connection.write(Buffers.wrap(mm, secondChunk));
            assertTrue("Second chunk write timeout", writeFuture.get(10, TimeUnit.SECONDS) != null);

            Buffer echoMessage = null;
            try {
                echoMessage = inQueueFilter.poll(10, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                assertTrue("Read timeout. CheckSizeFilter latch: " +
                        checkSizeFilter.latch, false);
            }

            byte[] pattern = new byte[fullMessageSize];
            Arrays.fill(pattern, 0, firstChunk.length, (byte) 1);
            Arrays.fill(pattern, firstChunk.length, pattern.length, (byte) 2);
            assertTrue(Buffers.wrap(mm, pattern).equals(echoMessage));
        } finally {
            if (connection != null) {
                connection.closeSilently();
            }

            transport.stop();
        }
    }

    public void testSelectorSwitch() throws Exception {
        Connection connection = null;

        TCPNIOTransport transport = TCPNIOTransportBuilder.newInstance().build();

        final CustomChannelDistributor distributor = new CustomChannelDistributor(transport);
        transport.setNIOChannelDistributor(distributor);

        FilterChainBuilder filterChainBuilder = FilterChainBuilder.stateless()
                .add(new TransportFilter())
                .add(new StringFilter())
                .add(new BaseFilter() {

            @Override
            public NextAction handleAccept(final FilterChainContext ctx) throws IOException {
                final NIOConnection connection = (NIOConnection) ctx.getConnection();

                connection.attachToSelectorRunner(distributor.getSelectorRunner());
                connection.registerKeyInterest(SelectionKey.OP_READ);
                
                return ctx.getInvokeAction();
            }
        })
                .add(new EchoFilter());

        transport.setFilterChain(filterChainBuilder.build());

        transport.setSelectorRunnersCount(4);
        
        try {
            transport.bind(PORT);
            transport.start();

            final InQueueFilter<String> inQueueFilter = new InQueueFilter<String>();
            final FilterChainBuilder clientChainBuilder = FilterChainBuilder.stateless()
                    .add(new TransportFilter())
                    .add(new StringFilter())
                    .add(inQueueFilter);

            final TCPNIOConnectorHandler connectorHandler = 
                    TCPNIOConnectorHandler.builder(transport)
                    .filterChain(clientChainBuilder.build())
                    .build();
            
            final Future<Connection> connectFuture = connectorHandler.connect(
                    new InetSocketAddress("localhost", PORT));

            connection = connectFuture.get(10, TimeUnit.SECONDS);
            assertTrue(connection != null);

            connection.configureBlocking(true);

            final String testString = "Hello";
            Future<WriteResult> writeFuture = connection.write(testString);
            assertTrue("Write timeout", writeFuture.isDone());

            final String inString = inQueueFilter.poll(10, TimeUnit.SECONDS);

            assertEquals(inString, testString);
        } finally {
            if (connection != null) {
                connection.closeSilently();
            }

            transport.stop();
        }
    }
    
    public void testConnectFutureCancel() throws Exception {
        TCPNIOTransport transport = TCPNIOTransportBuilder.newInstance().build();

        final AtomicInteger connectCounter = new AtomicInteger();
        final AtomicInteger closeCounter = new AtomicInteger();
        
        FilterChainBuilder serverFilterChainBuilder = FilterChainBuilder.stateless()
            .add(new TransportFilter());

        FilterChainBuilder clientFilterChainBuilder = FilterChainBuilder.stateless()
            .add(new TransportFilter())
            .add(new BaseFilter() {
            @Override
            public NextAction handleConnect(FilterChainContext ctx) throws IOException {
                connectCounter.incrementAndGet();
                return ctx.getInvokeAction();
            }

            @Override
            public NextAction handleClose(FilterChainContext ctx) throws IOException {
                closeCounter.incrementAndGet();
                return ctx.getInvokeAction();
            }
        });

        transport.setFilterChain(serverFilterChainBuilder.build());
        
        SocketConnectorHandler connectorHandler = TCPNIOConnectorHandler
                .builder(transport)
                .filterChain(clientFilterChainBuilder.build())
                .build();

        try {
            transport.bind(PORT);
            transport.start();

            final int connectionsNum = 100;
            
            for (int i = 0; i < connectionsNum; i++) {
                final Future<Connection> connectFuture = connectorHandler.connect(
                        new InetSocketAddress("localhost", PORT));
                if (!connectFuture.cancel(false)) {
                    assertTrue("Future is not done", connectFuture.isDone());
                    final Connection c = connectFuture.get();
                    assertNotNull("Connection is null?", c);
                    assertTrue("Connection is not connected", c.isOpen());
                    c.closeSilently();
                }
            }
            
            Thread.sleep(50);
            
            assertEquals("Number of connected and closed connections doesn't match", connectCounter.get(), closeCounter.get());
        } finally {
            transport.stop();
        }
    }
    
    public void testWorkerThreadPoolConfiguration() throws Exception {
        TCPNIOTransport t = TCPNIOTransportBuilder.newInstance().build();
        ThreadPoolConfig config = ThreadPoolConfig.newConfig();
        config.setCorePoolSize(1);
        config.setMaxPoolSize(1);
        config.setPoolName("custom");
        t.setWorkerThreadPoolConfig(config);
        t.setIOStrategy(WorkerThreadIOStrategy.getInstance());
        ThreadPoolConfig underTest = t.getWorkerThreadPoolConfig();
        assertEquals(1, underTest.getCorePoolSize());
        assertEquals(1, underTest.getMaxPoolSize());
        assertEquals("custom", underTest.getPoolName());
    }

    public void testWorkerThreadPoolConfiguration2() throws Exception {
        TCPNIOTransport t = TCPNIOTransportBuilder.newInstance().build();
        ThreadPoolConfig config = ThreadPoolConfig.newConfig();
        config.setCorePoolSize(1);
        config.setMaxPoolSize(1);
        config.setPoolName("custom");
        t.setWorkerThreadPoolConfig(config);
        t.setIOStrategy(SameThreadIOStrategy.getInstance());
        assertNull(t.getWorkerThreadPoolConfig());
        assertNull(t.getWorkerThreadPool());
    }
    
    public void testParallelWritesBlockingMode() throws Exception {
        doTestParallelWrites(100, 100000, true);
    }


    // --------------------------------------------------------- Private Methods


    protected void doTestParallelWrites(int packetsNumber,
                                        int size,
                                        boolean blocking) throws Exception {
        Connection connection = null;

        final ExecutorService executorService = Executors.newCachedThreadPool();

        FilterChainBuilder filterChainBuilder = FilterChainBuilder.stateless();
        filterChainBuilder.add(new TransportFilter());
        filterChainBuilder.add(new RandomDelayOnWriteFilter());
        filterChainBuilder.add(new StringFilter());
        filterChainBuilder.add(new ParallelWriteFilter(executorService, packetsNumber, size));

        TCPNIOTransport transport =
                TCPNIOTransportBuilder.newInstance().build();
        transport.setFilterChain(filterChainBuilder.build());
        transport.configureBlocking(blocking);

        try {
            transport.bind(PORT);
            transport.start();

            final FutureImpl<Boolean> clientFuture = SafeFutureImpl.create();
            FilterChainBuilder clientFilterChainBuilder = FilterChainBuilder.stateless();
            clientFilterChainBuilder.add(new TransportFilter());
            clientFilterChainBuilder.add(new StringFilter());

            final ClientCheckFilter clientTestFilter = new ClientCheckFilter(
                    clientFuture, packetsNumber, size);

            clientFilterChainBuilder.add(clientTestFilter);

            SocketConnectorHandler connectorHandler =
                    TCPNIOConnectorHandler.builder(transport)
                            .filterChain(clientFilterChainBuilder.build())
                            .build();

            Future<Connection> future = connectorHandler.connect("localhost", PORT);
            connection = future.get(10, TimeUnit.SECONDS);
            assertTrue(connection != null);

            try {
                connection.write("start");
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error occurred when sending start command");
                throw e;
            }

            Boolean isDone = clientFuture.get(1000, TimeUnit.SECONDS);
            assertEquals(Boolean.TRUE, isDone);
        } catch (Exception e) {
            e.printStackTrace();
            fail();
        } finally {
            try {
                executorService.shutdownNow();
            } catch (Exception e) {
            }

            if (connection != null) {
                try {
                    connection.close();
                } catch (Exception e) {
                }
            }

            try {
                transport.stop();
            } catch (Exception e) {
            }

        }
    }
    
    // ---------------------------------------------------------- Nested Classes
    public static class CustomChannelDistributor extends AbstractNIOConnectionDistributor {

        private final AtomicInteger counter;

        public CustomChannelDistributor(final NIOTransport transport) {
            super(transport);
            counter = new AtomicInteger();
        }

        @Override
        public void registerChannel(final SelectableChannel channel,
                final int interestOps, final Object attachment) throws IOException {
            final SelectorRunner runner = getSelectorRunner();

            transport.getSelectorHandler().registerChannel(runner,
                    channel, interestOps, attachment);
        }

        @Override
        public void registerChannelAsync(
                final SelectableChannel channel, final int interestOps,
                final Object attachment,
                final CompletionHandler<RegisterChannelResult> completionHandler) {
            final SelectorRunner runner = getSelectorRunner();

            transport.getSelectorHandler().registerChannelAsync(
                    runner, channel, interestOps, attachment, completionHandler);
        }

        @Override
        public void registerServiceChannelAsync(
                final SelectableChannel channel, final int interestOps,
                final Object attachment,
                final CompletionHandler<RegisterChannelResult> completionHandler) {
            final SelectorRunner runner = getSelectorRunner();
            
            transport.getSelectorHandler().registerChannelAsync(
                    runner, channel, interestOps, attachment, completionHandler);
        }

        
        private SelectorRunner getSelectorRunner() {
            final SelectorRunner[] runners = getTransportSelectorRunners();
            final int index = counter.getAndIncrement() % runners.length;

            return runners[index];
        }
    }
}
