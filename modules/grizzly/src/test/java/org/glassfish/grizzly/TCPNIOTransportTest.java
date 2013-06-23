/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2013 Oracle and/or its affiliates. All rights reserved.
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

import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.FilterChainBuilder;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.filterchain.TransportFilter;
import org.glassfish.grizzly.impl.FutureImpl;
import org.glassfish.grizzly.impl.SafeFutureImpl;
import org.glassfish.grizzly.memory.ByteBufferWrapper;
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
import org.glassfish.grizzly.streams.StreamReader;
import org.glassfish.grizzly.streams.StreamWriter;
import org.glassfish.grizzly.threadpool.ThreadPoolConfig;
import org.glassfish.grizzly.utils.ClientCheckFilter;
import org.glassfish.grizzly.utils.DataStructures;
import org.glassfish.grizzly.utils.EchoFilter;
import org.glassfish.grizzly.utils.Futures;
import org.glassfish.grizzly.utils.ParallelWriteFilter;
import org.glassfish.grizzly.utils.RandomDelayOnWriteFilter;
import org.glassfish.grizzly.utils.StringFilter;

import static junit.framework.Assert.assertTrue;


/**
 * Unit test for {@link TCPNIOTransport}
 *
 * @author Alexey Stashok
 */
public class TCPNIOTransportTest extends GrizzlyTestCase {

    public static final int PORT = 7777;

    private static final Logger logger = Grizzly.logger(TCPNIOTransportTest.class);

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

    public void testStartStopStart() throws Exception {
        TCPNIOTransport transport = TCPNIOTransportBuilder.newInstance().build();

        try {
            transport.bind(PORT);
            transport.start();
            Future<Connection> future = transport.connect("localhost", PORT);
            Connection connection = future.get(10, TimeUnit.SECONDS);
            assertTrue(connection != null);
            connection.closeSilently();
            
            transport.stop();
            assertTrue(transport.isStopped());
            
            transport.bind(PORT);
            transport.start();
            assertTrue(!transport.isStopped());
            
            future = transport.connect("localhost", PORT);
            connection = future.get(10, TimeUnit.SECONDS);
            assertTrue(connection != null);
            connection.closeSilently();
        } finally {
            transport.stop();
        }
    }
    
    public void testReadWriteTimeout() throws Exception {
        TCPNIOTransport transport = TCPNIOTransportBuilder.newInstance().build();
        assertEquals(30, transport.getWriteTimeout(TimeUnit.SECONDS));
        assertEquals(30, transport.getReadTimeout(TimeUnit.SECONDS));
        transport.setReadTimeout(45, TimeUnit.MINUTES);
        assertEquals(TimeUnit.MILLISECONDS.convert(45, TimeUnit.MINUTES), transport.getReadTimeout(TimeUnit.MILLISECONDS));
        assertEquals(30, transport.getWriteTimeout(TimeUnit.SECONDS));
        transport.setReadTimeout(-5, TimeUnit.SECONDS);
        assertEquals(-1, transport.getReadTimeout(TimeUnit.MILLISECONDS));
        transport.setReadTimeout(0, TimeUnit.SECONDS);
        assertEquals(-1, transport.getReadTimeout(TimeUnit.MILLISECONDS));
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
        StreamWriter writer = null;

        TCPNIOTransport transport = TCPNIOTransportBuilder.newInstance().build();

        try {
            transport.bind(PORT);
            transport.start();

            final FutureImpl<Connection> connectFuture =
                    Futures.<Connection>createSafeFuture();
            transport.connect(
                    new InetSocketAddress("localhost", PORT),
                    Futures.<Connection>toCompletionHandler(
                    connectFuture, new EmptyCompletionHandler<Connection>()  {

                        @Override
                        public void completed(final Connection connection) {
                            connection.configureStandalone(true);
                        }
                    }));
            
            connection = connectFuture.get(10, TimeUnit.SECONDS);
            assertTrue(connection != null);

            connection.configureBlocking(true);
            writer = StandaloneProcessor.INSTANCE.getStreamWriter(connection);
            byte[] sendingBytes = "Hello".getBytes();
            writer.writeByteArray(sendingBytes);
            Future<Integer> writeFuture = writer.flush();
            Integer bytesWritten = writeFuture.get(10, TimeUnit.SECONDS);
            assertTrue(writeFuture.isDone());
            assertEquals(sendingBytes.length, (int) bytesWritten);
        } finally {
            if (writer != null) {
                writer.close();
            }

            if (connection != null) {
                connection.closeSilently();
            }

            transport.stop();
        }
    }

    public void testClose() throws Exception {
        final BlockingQueue<Connection> acceptedQueue = DataStructures.<Connection>getLTQInstance();
        
        Connection connectedConnection = null;
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
            
            transport.setProcessor(filterChainBuilder.build());

            transport.bind(PORT);
            transport.start();
            
            Future<Connection> connectFuture = transport.connect(
                    new InetSocketAddress("localhost", PORT));
                        
            connectedConnection = connectFuture.get(10, TimeUnit.SECONDS);
            acceptedConnection = acceptedQueue.poll(10, TimeUnit.SECONDS);
            
            final FutureImpl<Boolean> connectedCloseFuture = new SafeFutureImpl<Boolean>();
            final FutureImpl<Boolean> acceptedCloseFuture = new SafeFutureImpl<Boolean>();
            
            connectedConnection.addCloseListener(new GenericCloseListener() {

                @Override
                public void onClosed(Closeable closeable, CloseType type) throws IOException {
                    connectedCloseFuture.result(type == CloseType.LOCALLY);
                }
            });
            
            acceptedConnection.addCloseListener(new GenericCloseListener() {

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
        StreamReader reader;
        StreamWriter writer;

        FilterChainBuilder filterChainBuilder = FilterChainBuilder.stateless();
        filterChainBuilder.add(new TransportFilter());
        filterChainBuilder.add(new EchoFilter());

        TCPNIOTransport transport = TCPNIOTransportBuilder.newInstance().build();
        transport.setProcessor(filterChainBuilder.build());

        try {
            transport.bind(PORT);
            transport.start();

            final FutureImpl<Connection> connectFuture =
                    Futures.<Connection>createSafeFuture();
            transport.connect(
                    new InetSocketAddress("localhost", PORT),
                    Futures.<Connection>toCompletionHandler(
                    connectFuture, new EmptyCompletionHandler<Connection>()  {

                        @Override
                        public void completed(final Connection connection) {
                            connection.configureStandalone(true);
                        }
                    }));
            
            connection = connectFuture.get(10, TimeUnit.SECONDS);
            assertTrue(connection != null);

            connection.configureBlocking(true);

            byte[] originalMessage = "Hello".getBytes();
            writer = StandaloneProcessor.INSTANCE.getStreamWriter(connection);
            writer.writeByteArray(originalMessage);
            Future<Integer> writeFuture = writer.flush();

            assertTrue("Write timeout", writeFuture.isDone());
            assertEquals(originalMessage.length, (int) writeFuture.get());


            reader = StandaloneProcessor.INSTANCE.getStreamReader(connection);
            Future readFuture = reader.notifyAvailable(originalMessage.length);
            assertTrue("Read timeout", readFuture.get(10, TimeUnit.SECONDS) != null);

            byte[] echoMessage = new byte[originalMessage.length];
            reader.readByteArray(echoMessage);
            assertTrue(Arrays.equals(echoMessage, originalMessage));
        } finally {
            if (connection != null) {
                connection.closeSilently();
            }

            transport.stop();
        }
    }

    public void testSeveralPacketsEcho() throws Exception {
        Connection connection = null;
        StreamReader reader;
        StreamWriter writer;

        FilterChainBuilder filterChainBuilder = FilterChainBuilder.stateless();
        filterChainBuilder.add(new TransportFilter());
        filterChainBuilder.add(new EchoFilter());
        TCPNIOTransport transport = TCPNIOTransportBuilder.newInstance().build();
        transport.setProcessor(filterChainBuilder.build());

        try {
            transport.bind(PORT);
            transport.start();
            transport.configureBlocking(true);

            final FutureImpl<Connection> connectFuture =
                    Futures.<Connection>createSafeFuture();
            transport.connect(
                    new InetSocketAddress("localhost", PORT),
                    Futures.<Connection>toCompletionHandler(
                    connectFuture, new EmptyCompletionHandler<Connection>()  {

                        @Override
                        public void completed(final Connection connection) {
                            connection.configureStandalone(true);
                        }
                    }));
            
            connection = connectFuture.get(10, TimeUnit.SECONDS);
            assertTrue(connection != null);

            reader = StandaloneProcessor.INSTANCE.getStreamReader(connection);
            writer = StandaloneProcessor.INSTANCE.getStreamWriter(connection);

            for (int i = 0; i < 100; i++) {
                byte[] originalMessage = ("Hello world #" + i).getBytes();
                writer.writeByteArray(originalMessage);
                Future<Integer> writeFuture = writer.flush();

                assertTrue("Write timeout", writeFuture.isDone());
                assertEquals(originalMessage.length, (int) writeFuture.get());

                Future readFuture = reader.notifyAvailable(originalMessage.length);
                assertTrue("Read timeout", readFuture.get(10, TimeUnit.SECONDS) != null);

                byte[] echoMessage = new byte[originalMessage.length];
                reader.readByteArray(echoMessage);
                assertTrue(Arrays.equals(echoMessage, originalMessage));
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
        StreamReader reader;
        StreamWriter writer;

        FilterChainBuilder filterChainBuilder = FilterChainBuilder.stateless();
        filterChainBuilder.add(new TransportFilter());
        filterChainBuilder.add(new EchoFilter());
        
        TCPNIOTransport transport = TCPNIOTransportBuilder.newInstance().build();
        transport.setProcessor(filterChainBuilder.build());

        try {
            transport.bind(PORT);
            transport.start();

            final FutureImpl<Connection> connectFuture =
                    Futures.<Connection>createSafeFuture();
            transport.connect(
                    new InetSocketAddress("localhost", PORT),
                    Futures.<Connection>toCompletionHandler(
                    connectFuture, new EmptyCompletionHandler<Connection>()  {

                        @Override
                        public void completed(final Connection connection) {
                            connection.configureStandalone(true);
                        }
                    }));
            
            connection = connectFuture.get(10, TimeUnit.SECONDS);
            assertTrue(connection != null);

            byte[] originalMessage = "Hello".getBytes();
            writer = StandaloneProcessor.INSTANCE.getStreamWriter(connection);
            writer.writeByteArray(originalMessage);
            Future<Integer> writeFuture = writer.flush();

            Integer writtenBytes = writeFuture.get(10, TimeUnit.SECONDS);
            assertEquals(originalMessage.length, (int) writtenBytes);


            reader = StandaloneProcessor.INSTANCE.getStreamReader(connection);
            Future readFuture = reader.notifyAvailable(originalMessage.length);
            assertTrue("Read timeout", readFuture.get(10, TimeUnit.SECONDS) != null);

            byte[] echoMessage = new byte[originalMessage.length];
            reader.readByteArray(echoMessage);
            assertTrue(Arrays.equals(echoMessage, originalMessage));
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
        StreamReader reader;
        StreamWriter writer;
        
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

        TCPNIOTransport transport = TCPNIOTransportBuilder.newInstance().build();
        transport.setProcessor(filterChainBuilder.build());
        transport.getAsyncQueueIO().getWriter().setMaxPendingBytesPerConnection(-1);

        try {
            transport.bind(PORT);
            transport.start();

            final FutureImpl<Connection> connectFuture =
                    Futures.<Connection>createSafeFuture();
            transport.connect(
                    new InetSocketAddress("localhost", PORT),
                    Futures.<Connection>toCompletionHandler(
                    connectFuture, new EmptyCompletionHandler<Connection>()  {

                        @Override
                        public void completed(final Connection connection) {
                            connection.configureStandalone(true);
                        }
                    }));
            
            connection = connectFuture.get(10, TimeUnit.SECONDS);
            assertTrue(connection != null);

            reader = StandaloneProcessor.INSTANCE.getStreamReader(connection);
            writer = StandaloneProcessor.INSTANCE.getStreamWriter(connection);

            final CountDownLatch sendLatch = new CountDownLatch(packetsNumber);

            for (int i = 0; i < packetsNumber; i++) {
                final byte[] message = new byte[packetSize];
                Arrays.fill(message, (byte) i);

                writer.writeByteArray(message);
                writer.flush(new EmptyCompletionHandler<Integer>() {

                    @Override
                    public void completed(Integer result) {
                        assertEquals(message.length, (int) result);
                        sendLatch.countDown();
                    }

                    @Override
                    public void failed(Throwable throwable) {
                        logger.log(Level.WARNING, "failure", throwable);
                    }
                });
            }

            for (int i = 0; i < packetsNumber; i++) {
                byte[] pattern = new byte[packetSize];
                Arrays.fill(pattern, (byte) i);

                byte[] message = new byte[packetSize];
                Future future = reader.notifyAvailable(packetSize);
                try {
                    future.get(10, TimeUnit.SECONDS);
                } catch (TimeoutException e) {
                    assertTrue("Timeout. Server processed " +
                            serverBytesCounter.get() + " bytes", false);
                }
                assertTrue(future.isDone());
                reader.readByteArray(message);
                assertTrue(Arrays.equals(pattern, message));
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
    
    public void testFeeder() throws Exception {
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
                final Buffer buffer = (Buffer) ctx.getMessage();
                logger.log(Level.INFO, "Feeder. Check size filter: {0}", buffer);
                if (buffer.remaining() >= size) {
                    latch.countDown();
                    return ctx.getInvokeAction();
                }

                return ctx.getStopAction(buffer);
            }

        }

        int fullMessageSize = 2048;

        Connection connection = null;
        StreamReader reader;
        StreamWriter writer;

        CheckSizeFilter checkSizeFilter = new CheckSizeFilter(fullMessageSize);

        FilterChainBuilder filterChainBuilder = FilterChainBuilder.stateless();
        filterChainBuilder.add(new TransportFilter());
        filterChainBuilder.add(checkSizeFilter);
        filterChainBuilder.add(new EchoFilter());

        TCPNIOTransport transport = TCPNIOTransportBuilder.newInstance().build();
        transport.setProcessor(filterChainBuilder.build());

        try {
            transport.bind(PORT);
            transport.start();

            final FutureImpl<Connection> connectFuture =
                    Futures.<Connection>createSafeFuture();
            transport.connect(
                    new InetSocketAddress("localhost", PORT),
                    Futures.<Connection>toCompletionHandler(
                    connectFuture, new EmptyCompletionHandler<Connection>()  {

                        @Override
                        public void completed(final Connection connection) {
                            connection.configureStandalone(true);
                        }
                    }));
            connection = connectFuture.get(10, TimeUnit.SECONDS);
            assertTrue(connection != null);

            byte[] firstChunk = new byte[fullMessageSize / 5];
            Arrays.fill(firstChunk, (byte) 1);
            writer = StandaloneProcessor.INSTANCE.getStreamWriter(connection);
            writer.writeByteArray(firstChunk);
            Future<Integer> writeFuture = writer.flush();
            assertTrue("First chunk write timeout", writeFuture.get(10, TimeUnit.SECONDS) > 0);

            Thread.sleep(1000);
            
            byte[] secondChunk = new byte[fullMessageSize - firstChunk.length];
            Arrays.fill(secondChunk, (byte) 2);
            writer.writeByteArray(secondChunk);
            writeFuture = writer.flush();
            assertTrue("Second chunk write timeout", writeFuture.get(10, TimeUnit.SECONDS) > 0);

            reader = StandaloneProcessor.INSTANCE.getStreamReader(connection);
            Future readFuture = reader.notifyAvailable(fullMessageSize);
            try {
                assertTrue("Read timeout. CheckSizeFilter latch: " +
                        checkSizeFilter.latch,
                        readFuture.get(10, TimeUnit.SECONDS) != null);
            } catch (TimeoutException e) {
                assertTrue("Read timeout. CheckSizeFilter latch: " +
                        checkSizeFilter.latch, false);
            }

            byte[] pattern = new byte[fullMessageSize];
            Arrays.fill(pattern, 0, firstChunk.length, (byte) 1);
            Arrays.fill(pattern, firstChunk.length, pattern.length, (byte) 2);
            byte[] echoMessage = new byte[fullMessageSize];
            reader.readByteArray(echoMessage);
            assertTrue(Arrays.equals(pattern, echoMessage));
        } finally {
            if (connection != null) {
                connection.closeSilently();
            }

            transport.stop();
        }
    }

    public void testSelectorSwitch() throws Exception {
        Connection connection = null;
        StreamReader reader;
        StreamWriter writer;

        TCPNIOTransport transport = TCPNIOTransportBuilder.newInstance().build();

        final CustomChannelDistributor distributor = new CustomChannelDistributor(transport);
        transport.setNIOChannelDistributor(distributor);

        FilterChainBuilder filterChainBuilder = FilterChainBuilder.stateless();
        filterChainBuilder.add(new TransportFilter());
        filterChainBuilder.add(new BaseFilter() {

            @Override
            public NextAction handleAccept(final FilterChainContext ctx) throws IOException {
                final NIOConnection connection = (NIOConnection) ctx.getConnection();

                connection.attachToSelectorRunner(distributor.getSelectorRunner());
                connection.enableIOEvent(IOEvent.READ);
                
                return ctx.getInvokeAction();
            }
        });
        filterChainBuilder.add(new EchoFilter());

        transport.setProcessor(filterChainBuilder.build());

        transport.setSelectorRunnersCount(4);
        
        try {
            transport.bind(PORT);
            transport.start();

            final FutureImpl<Connection> connectFuture =
                    Futures.<Connection>createSafeFuture();
            transport.connect(
                    new InetSocketAddress("localhost", PORT),
                    Futures.<Connection>toCompletionHandler(
                    connectFuture, new EmptyCompletionHandler<Connection>()  {

                        @Override
                        public void completed(final Connection connection) {
                            connection.configureStandalone(true);
                        }
                    }));
            connection = connectFuture.get(10, TimeUnit.SECONDS);
            assertTrue(connection != null);

            connection.configureBlocking(true);

            byte[] originalMessage = "Hello".getBytes();
            writer = StandaloneProcessor.INSTANCE.getStreamWriter(connection);
            writer.writeByteArray(originalMessage);
            Future<Integer> writeFuture = writer.flush();

            assertTrue("Write timeout", writeFuture.isDone());
            assertEquals(originalMessage.length, (int) writeFuture.get());


            reader = StandaloneProcessor.INSTANCE.getStreamReader(connection);
            Future readFuture = reader.notifyAvailable(originalMessage.length);
            assertTrue("Read timeout", readFuture.get(10, TimeUnit.SECONDS) != null);

            byte[] echoMessage = new byte[originalMessage.length];
            reader.readByteArray(echoMessage);
            assertTrue(Arrays.equals(echoMessage, originalMessage));
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

        transport.setProcessor(serverFilterChainBuilder.build());
        
        SocketConnectorHandler connectorHandler = TCPNIOConnectorHandler
                .builder(transport)
                .processor(clientFilterChainBuilder.build())
                .build();

        try {
            transport.bind(PORT);
            transport.start();

            int numberOfCancelledConnections = 0;
            final int connectionsNum = 100;
            
            for (int i = 0; i < connectionsNum; i++) {
                final Future<Connection> connectFuture = connectorHandler.connect(
                        new InetSocketAddress("localhost", PORT));
                if (connectFuture.cancel(false)) {
                    numberOfCancelledConnections++;
                } else {
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
        ThreadPoolConfig config = ThreadPoolConfig.defaultConfig();
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
        ThreadPoolConfig config = ThreadPoolConfig.defaultConfig();
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


    @SuppressWarnings("unchecked")
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
        transport.setProcessor(filterChainBuilder.build());
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
                            .processor(clientFilterChainBuilder.build())
                            .build();

            Future<Connection> future = connectorHandler.connect("localhost", PORT);
            connection = future.get(10, TimeUnit.SECONDS);
            assertTrue(connection != null);

            try {
                connection.write("start");
            } catch (Exception e) {
                logger.log(Level.WARNING, "Error occurred when sending start command");
                throw e;
            }

            Boolean isDone = clientFuture.get(10, TimeUnit.SECONDS);
            assertEquals(Boolean.TRUE, isDone);
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
