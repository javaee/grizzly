/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2013-2015 Oracle and/or its affiliates. All rights reserved.
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

import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.FilterChainBuilder;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.filterchain.TransportFilter;
import org.glassfish.grizzly.impl.FutureImpl;
import org.glassfish.grizzly.nio.NIOTransport;
import org.glassfish.grizzly.nio.transport.TCPNIOTransportBuilder;
import org.glassfish.grizzly.nio.transport.UDPNIOTransportBuilder;
import org.glassfish.grizzly.strategies.SameThreadIOStrategy;
import org.glassfish.grizzly.strategies.WorkerThreadIOStrategy;
import org.glassfish.grizzly.streams.StreamReader;
import org.glassfish.grizzly.streams.StreamWriter;
import org.glassfish.grizzly.threadpool.ThreadPoolConfig;
import org.glassfish.grizzly.utils.EchoFilter;
import org.glassfish.grizzly.utils.Futures;
import org.glassfish.grizzly.utils.Holder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.Assert.*;

@RunWith(Parameterized.class)
public class NIOTransportTest {

    private static final Logger LOGGER = Grizzly.logger(NIOTransportTest.class);
    private static final int PORT = 7777;

    @Parameterized.Parameters
    public static Collection<Object[]> getTransport() {
        return Arrays.asList(new Object[][]{
            {new Holder<NIOTransport>() {
                    @Override
                    public NIOTransport get() {
                        return TCPNIOTransportBuilder.newInstance().build();
                    }
                }},
            {new Holder<NIOTransport>() {
                    @Override
                    public NIOTransport get() {
                        return UDPNIOTransportBuilder.newInstance().build();
                    }
                }}});
    }

    private final NIOTransport transport;

    public NIOTransportTest(final Holder<NIOTransport> transportHolder) {
        this.transport = transportHolder.get();
    }


    // ------------------------------------------------------------ Test Methods

    @Test
    public void testStartStop() throws IOException {
        LOGGER.log(Level.INFO, "Running: testStartStop ({0})", transport.getName());
        
        try {
            transport.bind(PORT);
            transport.start();
        } finally {
            transport.shutdownNow();
        }
    }

    @Test
    public void testStartStopStart() throws Exception {
        LOGGER.log(Level.INFO, "Running: testStartStopStart ({0})", transport.getName());

        try {
            transport.bind(PORT);
            transport.start();
            Future<Connection> future = transport.connect("localhost", PORT);
            Connection connection = future.get(10, TimeUnit.SECONDS);
            assertTrue(connection != null);
            connection.closeSilently();

            transport.shutdownNow();
            assertTrue(transport.isStopped());

            transport.bind(PORT);
            transport.start();
            assertTrue(!transport.isStopped());

            future = transport.connect("localhost", PORT);
            connection = future.get(10, TimeUnit.SECONDS);
            assertTrue(connection != null);
            connection.closeSilently();
        } finally {
            transport.shutdownNow();
        }
    }

    @Test
    public void testReadWriteTimeout() throws Exception {
        LOGGER.log(Level.INFO, "Running: testReadWriteTimeout ({0})", transport.getName());

        assertEquals(30, transport.getWriteTimeout(TimeUnit.SECONDS));
        assertEquals(30, transport.getReadTimeout(TimeUnit.SECONDS));
        transport.setReadTimeout(45, TimeUnit.MINUTES);
        assertEquals(TimeUnit.MILLISECONDS.convert(45, TimeUnit.MINUTES),
                     transport.getReadTimeout(TimeUnit.MILLISECONDS));
        assertEquals(30, transport.getWriteTimeout(TimeUnit.SECONDS));
        transport.setReadTimeout(-5, TimeUnit.SECONDS);
        assertEquals(-1, transport.getReadTimeout(TimeUnit.MILLISECONDS));
        transport.setReadTimeout(0, TimeUnit.SECONDS);
        assertEquals(-1, transport.getReadTimeout(TimeUnit.MILLISECONDS));
    }

    @Test
    public void testConnectorHandlerConnect() throws Exception {
        LOGGER.log(Level.INFO, "Running: testConnectorHandlerConnect ({0})", transport.getName());

        Connection connection = null;

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

            transport.shutdownNow();
        }
    }

    @Test
    public void testPortRangeBind() throws Exception {
        LOGGER.log(Level.INFO, "Running: testPortRangeBind ({0})", transport.getName());

        final int portsTest = 10;
        final int startPort = PORT + 1234;
        final PortRange portRange =
                new PortRange(startPort, startPort + portsTest - 1);

        Connection connection;
        transport.setReuseAddress(false);

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
                Future<Connection> future =
                        transport.connect("localhost", startPort + i);
                connection = future.get(10, TimeUnit.SECONDS);
                assertTrue(connection != null);
                connection.closeSilently();
            }
        } finally {
            transport.shutdownNow();
        }
    }

    @Test
    public void testConnectorHandlerConnectAndWrite() throws Exception {
        LOGGER.log(Level.INFO, "Running: testConnectorHandlerConnectAndWrite ({0})", transport.getName());

        Connection connection = null;
        StreamWriter writer = null;

        try {
            transport.bind(PORT);
            transport.start();

            final FutureImpl<Connection> connectFuture =
                    Futures.createSafeFuture();
            transport.connect(
                    new InetSocketAddress("localhost", PORT),
                    Futures.toCompletionHandler(
                            connectFuture,
                            new EmptyCompletionHandler<Connection>() {

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

            transport.shutdownNow();
        }
    }

    @Test
    public void testSimpleEcho() throws Exception {
        LOGGER.log(Level.INFO, "Running: testSimpleEcho ({0})", transport.getName());

        Connection connection = null;
        StreamReader reader;
        StreamWriter writer;

        FilterChainBuilder filterChainBuilder = FilterChainBuilder.stateless();
        filterChainBuilder.add(new TransportFilter());
        filterChainBuilder.add(new EchoFilter());

        transport.setProcessor(filterChainBuilder.build());

        try {
            transport.bind(PORT);
            transport.start();

            final FutureImpl<Connection> connectFuture =
                    Futures.createSafeFuture();
            transport.connect(
                    new InetSocketAddress("localhost", PORT),
                    Futures.toCompletionHandler(
                            connectFuture,
                            new EmptyCompletionHandler<Connection>() {

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
            assertTrue("Read timeout",
                       readFuture.get(10, TimeUnit.SECONDS) != null);

            byte[] echoMessage = new byte[originalMessage.length];
            reader.readByteArray(echoMessage);
            assertTrue(Arrays.equals(echoMessage, originalMessage));
        } finally {
            if (connection != null) {
                connection.closeSilently();
            }

            transport.shutdownNow();
        }
    }

    @Test
    public void testSeveralPacketsEcho() throws Exception {
        LOGGER.log(Level.INFO, "Running: testSeveralPacketsEcho ({0})", transport.getName());

        Connection connection = null;
        StreamReader reader;
        StreamWriter writer;

        FilterChainBuilder filterChainBuilder = FilterChainBuilder.stateless();
        filterChainBuilder.add(new TransportFilter());
        filterChainBuilder.add(new EchoFilter());
        transport.setProcessor(filterChainBuilder.build());

        try {
            transport.bind(PORT);
            transport.start();
            transport.configureBlocking(true);

            final FutureImpl<Connection> connectFuture =
                    Futures.createSafeFuture();
            transport.connect(
                    new InetSocketAddress("localhost", PORT),
                    Futures.toCompletionHandler(
                            connectFuture,
                            new EmptyCompletionHandler<Connection>() {

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

                Future readFuture =
                        reader.notifyAvailable(originalMessage.length);
                assertTrue("Read timeout",
                           readFuture.get(10, TimeUnit.SECONDS) != null);

                byte[] echoMessage = new byte[originalMessage.length];
                reader.readByteArray(echoMessage);
                assertTrue(Arrays.equals(echoMessage, originalMessage));
            }
        } finally {
            if (connection != null) {
                connection.closeSilently();
            }

            transport.shutdownNow();
        }
    }

    @Test
    public void testAsyncReadWriteEcho() throws Exception {
        LOGGER.log(Level.INFO, "Running: testAsyncReadWriteEcho ({0})", transport.getName());

        Connection connection = null;
        StreamReader reader;
        StreamWriter writer;

        FilterChainBuilder filterChainBuilder = FilterChainBuilder.stateless();
        filterChainBuilder.add(new TransportFilter());
        filterChainBuilder.add(new EchoFilter());

        transport.setProcessor(filterChainBuilder.build());

        try {
            transport.bind(PORT);
            transport.start();

            final FutureImpl<Connection> connectFuture =
                    Futures.createSafeFuture();
            transport.connect(
                    new InetSocketAddress("localhost", PORT),
                    Futures.toCompletionHandler(
                            connectFuture,
                            new EmptyCompletionHandler<Connection>() {

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
            assertTrue("Read timeout",
                       readFuture.get(10, TimeUnit.SECONDS) != null);

            byte[] echoMessage = new byte[originalMessage.length];
            reader.readByteArray(echoMessage);
            assertTrue(Arrays.equals(echoMessage, originalMessage));
        } finally {
            if (connection != null) {
                connection.closeSilently();
            }

            transport.shutdownNow();
        }
    }

    @Test
    public void testSeveralPacketsAsyncReadWriteEcho() throws Exception {
        LOGGER.log(Level.INFO, "Running: testSeveralPacketsAsyncReadWriteEcho ({0})", transport.getName());

        int packetsNumber = 100;
        final int packetSize = 32;

        Connection connection = null;
        StreamReader reader;
        StreamWriter writer;

        FilterChainBuilder filterChainBuilder = FilterChainBuilder.stateless();
        filterChainBuilder.add(new TransportFilter());
        filterChainBuilder.add(new EchoFilter());

        transport.setProcessor(filterChainBuilder.build());

        try {
            transport.setReadBufferSize(2048);
            transport.setWriteBufferSize(2048);

            transport.bind(PORT);

            transport.start();

            final FutureImpl<Connection> connectFuture =
                    Futures.createSafeFuture();
            transport.connect(
                    new InetSocketAddress("localhost", PORT),
                    Futures.toCompletionHandler(
                    connectFuture, new EmptyCompletionHandler<Connection>() {
                @Override
                public void completed(final Connection connection) {
                    connection.configureStandalone(true);
                }
            }));
            connection = connectFuture.get(10, TimeUnit.SECONDS);
            assertTrue(connection != null);

            reader = StandaloneProcessor.INSTANCE.getStreamReader(connection);
            writer = StandaloneProcessor.INSTANCE.getStreamWriter(connection);

            for (int i = 0; i < packetsNumber; i++) {
                final byte[] message = new byte[packetSize];
                Arrays.fill(message, (byte) i);

                writer.writeByteArray(message);
                writer.flush();

                final byte[] rcvMessage = new byte[packetSize];
                Future future = reader.notifyAvailable(packetSize);
                future.get(10, TimeUnit.SECONDS);
                assertTrue(future.isDone());
                reader.readByteArray(rcvMessage);

                assertTrue("Message is corrupted!",
                        Arrays.equals(rcvMessage, message));

            }
        } finally {
            if (connection != null) {
                connection.closeSilently();
            }

            transport.shutdownNow();
        }
    }

    @Test
    public void testFeeder() throws Exception {
        LOGGER.log(Level.INFO, "Running: testFeeder ({0})", transport.getName());

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
                LOGGER.log(Level.INFO, "Feeder. Check size filter: {0}",
                           buffer);
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

        transport.setProcessor(filterChainBuilder.build());

        try {
            transport.bind(PORT);
            transport.start();

            final FutureImpl<Connection> connectFuture =
                    Futures.createSafeFuture();
            transport.connect(
                    new InetSocketAddress("localhost", PORT),
                    Futures.toCompletionHandler(
                            connectFuture,
                            new EmptyCompletionHandler<Connection>() {

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
            assertTrue("First chunk write timeout",
                       writeFuture.get(10, TimeUnit.SECONDS) > 0);

            Thread.sleep(1000);

            byte[] secondChunk = new byte[fullMessageSize - firstChunk.length];
            Arrays.fill(secondChunk, (byte) 2);
            writer.writeByteArray(secondChunk);
            writeFuture = writer.flush();
            assertTrue("Second chunk write timeout",
                       writeFuture.get(10, TimeUnit.SECONDS) > 0);

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

            transport.shutdownNow();
        }
    }

    @Test
    public void testWorkerThreadPoolConfiguration() throws Exception {
        LOGGER.log(Level.INFO, "Running: testWorkerThreadPoolConfiguration ({0})", transport.getName());

        ThreadPoolConfig config = ThreadPoolConfig.defaultConfig();
        config.setCorePoolSize(1);
        config.setMaxPoolSize(1);
        config.setPoolName("custom");
        transport.setWorkerThreadPoolConfig(config);
        transport.setIOStrategy(WorkerThreadIOStrategy.getInstance());
        ThreadPoolConfig underTest = transport.getWorkerThreadPoolConfig();
        assertEquals(1, underTest.getCorePoolSize());
        assertEquals(1, underTest.getMaxPoolSize());
        assertEquals("custom", underTest.getPoolName());
    }

    @Test
    public void testWorkerThreadPoolConfiguration2() throws Exception {
        LOGGER.log(Level.INFO, "Running: testWorkerThreadPoolConfiguration2 ({0})", transport.getName());

        ThreadPoolConfig config = ThreadPoolConfig.defaultConfig();
        config.setCorePoolSize(1);
        config.setMaxPoolSize(1);
        config.setPoolName("custom");
        transport.setWorkerThreadPoolConfig(config);
        transport.setIOStrategy(SameThreadIOStrategy.getInstance());
        assertNull(transport.getWorkerThreadPoolConfig());
        assertNull(transport.getWorkerThreadPool());
    }

    @Test
    public void testGracefulShutdown() throws Exception {
        LOGGER.log(Level.INFO, "Running: testGracefulShutdown ({0})", transport.getName());

        final CountDownLatch latch = new CountDownLatch(2);
        final AtomicBoolean forcedNotCalled1 = new AtomicBoolean();
        final AtomicBoolean forcedNotCalled2 = new AtomicBoolean();
        transport.addShutdownListener(new GracefulShutdownListener() {
            @Override
            public void shutdownRequested(final ShutdownContext shutdownContext) {
                Thread t = new Thread() {
                    @Override
                    public void run() {
                        try {
                            Thread.sleep(5000);
                        } catch (InterruptedException ignored) {
                        }
                        shutdownContext.ready();
                        latch.countDown();
                    }
                };
                t.setDaemon(true);
                t.start();
            }

            @Override
            public void shutdownForced() {
                forcedNotCalled1.set(true);
            }
        });
        transport.addShutdownListener(new GracefulShutdownListener() {
            @Override
            public void shutdownRequested(final ShutdownContext shutdownContext) {
                Thread t = new Thread() {
                    @Override
                    public void run() {
                        try {
                            Thread.sleep(7000);
                        } catch (InterruptedException ignored) {
                        }
                        shutdownContext.ready();
                        latch.countDown();
                    }
                };
                t.setDaemon(true);
                t.start();
            }

            @Override
            public void shutdownForced() {
                forcedNotCalled2.set(true);
            }
        });
        transport.start();
        long start = System.currentTimeMillis();
        GrizzlyFuture<Transport> future = transport.shutdown();
        Transport tt = future.get(10, TimeUnit.SECONDS);
        long stop = System.currentTimeMillis();
        assertTrue((stop - start) >= 7000);
        assertEquals(transport, tt);
        assertTrue(transport.isStopped());
        assertFalse(forcedNotCalled1.get());
        assertFalse(forcedNotCalled2.get());
    }

    @Test
    public void testGracefulShutdownWithGracePeriod() throws Exception {
        LOGGER.log(Level.INFO, "Running: testGracefulShutdownWithGracePeriod ({0})", transport.getName());

        final AtomicBoolean forcedNotCalled1 = new AtomicBoolean();
        final AtomicBoolean forcedNotCalled2 = new AtomicBoolean();
        transport.addShutdownListener(new GracefulShutdownListener() {
            @Override
            public void shutdownRequested(final ShutdownContext shutdownContext) {
                Thread t = new Thread() {
                    @Override
                    public void run() {
                        try {
                            Thread.sleep(4000);
                        } catch (InterruptedException ignored) {
                        }
                        shutdownContext.ready();
                    }
                };
                t.setDaemon(true);
                t.start();
            }

            @Override
            public void shutdownForced() {
                forcedNotCalled1.set(true);
            }
        });
        transport.addShutdownListener(new GracefulShutdownListener() {
            @Override
            public void shutdownRequested(final ShutdownContext shutdownContext) {
                Thread t = new Thread() {
                    @Override
                    public void run() {
                        try {
                            Thread.sleep(3000);
                        } catch (InterruptedException ignored) {
                        }
                        shutdownContext.ready();
                    }
                };
                t.setDaemon(true);
                t.start();
            }

            @Override
            public void shutdownForced() {
                forcedNotCalled2.set(true);
            }
        });
        transport.start();
        GrizzlyFuture<Transport> future =
                transport.shutdown(5, TimeUnit.SECONDS);
        Transport tt = future.get(5100, TimeUnit.MILLISECONDS);
        assertTrue(transport.isStopped());
        assertEquals(transport, tt);
        assertFalse(forcedNotCalled1.get());
        assertFalse(forcedNotCalled2.get());
    }

    @Test
    public void testGracefulShutdownWithGracePeriodTimeout() throws Exception {
        LOGGER.log(Level.INFO, "Running: testGracefulShutdownWithGracePeriodTimeout ({0})", transport.getName());

        final AtomicBoolean forcedCalled1 = new AtomicBoolean();
        final AtomicBoolean forcedCalled2 = new AtomicBoolean();
        transport.addShutdownListener(new GracefulShutdownListener() {
            @Override
            public void shutdownRequested(final ShutdownContext shutdownContext) {
                Thread t = new Thread() {
                    @Override
                    public void run() {
                        try {
                            Thread.sleep(10000);
                        } catch (InterruptedException ignored) {
                        }
                        shutdownContext.ready();
                    }
                };
                t.setDaemon(true);
                t.start();
            }

            @Override
            public void shutdownForced() {
                forcedCalled1.set(true);
            }
        });
        transport.addShutdownListener(new GracefulShutdownListener() {
            @Override
            public void shutdownRequested(final ShutdownContext shutdownContext) {
                Thread t = new Thread() {
                    @Override
                    public void run() {
                        try {
                            Thread.sleep(7000);
                        } catch (InterruptedException ignored) {
                        }
                        shutdownContext.ready();
                    }
                };
                t.setDaemon(true);
                t.start();
            }

            @Override
            public void shutdownForced() {
                forcedCalled2.set(true);
            }
        });
        transport.start();
        GrizzlyFuture<Transport> future = transport.shutdown(5, TimeUnit.SECONDS);
        Transport tt = future.get(5100, TimeUnit.MILLISECONDS);
        assertTrue(transport.isStopped());
        assertEquals(transport, tt);
        assertTrue(forcedCalled1.get());
        assertTrue(forcedCalled2.get());
    }

    @Test
    public void testGracefulShutdownAndThenForced() throws Exception {
        LOGGER.log(Level.INFO, "Running: testGracefulShutdownAndThenForced ({0})", transport.getName());

        final AtomicBoolean listener1 = new AtomicBoolean();
        final AtomicBoolean listener2 = new AtomicBoolean();
        final CountDownLatch latch = new CountDownLatch(2);
        transport.addShutdownListener(new GracefulShutdownListener() {
            @Override
            public void shutdownRequested(final ShutdownContext shutdownContext) {
                listener1.compareAndSet(false, true);
                Thread t = new Thread() {
                    @Override
                    public void run() {
                        try {
                            Thread.sleep(20000);
                        } catch (InterruptedException ignored) {
                        }
                        shutdownContext.ready();
                    }
                };
                t.setDaemon(true);
                t.start();
            }

            @Override
            public void shutdownForced() {
                latch.countDown();
            }
        });
        transport.addShutdownListener(new GracefulShutdownListener() {
            @Override
            public void shutdownRequested(final ShutdownContext shutdownContext) {
                listener2.compareAndSet(false, true);
                Thread t = new Thread() {
                    @Override
                    public void run() {
                        try {
                            Thread.sleep(20000);
                        } catch (InterruptedException ignored) {
                        }
                        shutdownContext.ready();
                    }
                };
                t.setDaemon(true);
                t.start();
            }

            @Override
            public void shutdownForced() {
                latch.countDown();
            }
        });
        transport.start();
        GrizzlyFuture<Transport> future = transport.shutdown();
        Thread.sleep(3000);
        transport.shutdownNow();
        Transport tt = future.get(10, TimeUnit.SECONDS);
        latch.await(5, TimeUnit.SECONDS);
        assertEquals(transport, tt);
        assertTrue(transport.isStopped());
        assertTrue(listener1.get());
        assertTrue(listener2.get());
    }

    @Test
    public void testTimedGracefulShutdownAndThenForced() throws Exception {
        LOGGER.log(Level.INFO, "Running: testTimedGracefulShutdownAndThenForced ({0})", transport.getName());

        final CountDownLatch latch = new CountDownLatch(1);
        transport.addShutdownListener(new GracefulShutdownListener() {
            @Override
            public void shutdownRequested(ShutdownContext shutdownContext) {
                try {
                    Thread.sleep(20000);
                } catch (InterruptedException ignored) {
                }
                shutdownContext.ready();
            }

            @Override
            public void shutdownForced() {
                latch.countDown();
            }
        });

        transport.start();
        GrizzlyFuture<Transport> future = transport.shutdown(5, TimeUnit.MINUTES);
        Thread.sleep(3000);
        transport.shutdownNow();
        latch.await(5, TimeUnit.SECONDS);
        final Transport tt = future.get(1, TimeUnit.SECONDS);
        assertEquals(transport, tt);
        assertTrue(transport.isStopped());
    }

}
