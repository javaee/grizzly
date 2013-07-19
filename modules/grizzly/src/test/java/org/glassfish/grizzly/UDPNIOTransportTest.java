/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2009-2013 Oracle and/or its affiliates. All rights reserved.
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

import org.glassfish.grizzly.filterchain.FilterChainBuilder;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.glassfish.grizzly.filterchain.*;
import org.glassfish.grizzly.impl.FutureImpl;
import org.glassfish.grizzly.memory.ByteBufferWrapper;
import org.glassfish.grizzly.nio.transport.UDPNIOConnectorHandler;
import org.glassfish.grizzly.nio.transport.UDPNIOServerConnection;
import org.glassfish.grizzly.nio.transport.UDPNIOTransport;
import org.glassfish.grizzly.nio.transport.UDPNIOTransportBuilder;
import org.glassfish.grizzly.strategies.SameThreadIOStrategy;
import org.glassfish.grizzly.strategies.WorkerThreadIOStrategy;
import org.glassfish.grizzly.streams.StreamReader;
import org.glassfish.grizzly.streams.StreamWriter;
import org.glassfish.grizzly.threadpool.ThreadPoolConfig;
import org.glassfish.grizzly.utils.EchoFilter;
import org.glassfish.grizzly.utils.Futures;

/**
 * Unit test for {@link UDPNIOTransport}
 *
 * @author Alexey Stashok
 */
public class UDPNIOTransportTest extends GrizzlyTestCase {
    public static final int PORT = 7777;

    @Override
    protected void setUp() throws Exception {
        ByteBufferWrapper.DEBUG_MODE = true;
    }


    public void testStartStop() throws IOException {
        UDPNIOTransport transport = (UDPNIOTransport) UDPNIOTransportBuilder.newInstance().build();

        try {
            transport.bind(PORT);
            transport.start();
        } catch (Exception e) {
            e.printStackTrace(System.out);
            assertTrue("Exception!!!", false);
        } finally {
            transport.stop();
        }
    }

    public void testStartStopStart() throws Exception {
        UDPNIOTransport transport = UDPNIOTransportBuilder.newInstance().build();

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
        UDPNIOTransport transport = UDPNIOTransportBuilder.newInstance().build();
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

    public void testPortRangeBind() throws Exception {
        final int portsTest = 10;
        final PortRange portRange = new PortRange(PORT, PORT + portsTest - 1);

        Connection connection = null;
        UDPNIOTransport transport = UDPNIOTransportBuilder.newInstance()
                .setReuseAddress(false)
                .build();
        
        try {
            for (int i = 0; i < portsTest; i++) {
                final UDPNIOServerConnection serverConnection =
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
                Future<Connection> future = transport.connect("localhost", PORT + i);
                connection = future.get(10, TimeUnit.SECONDS);
                assertTrue(connection != null);
                connection.closeSilently();
            }
        } finally {
            transport.stop();
        }
    }

    public void testConnectorHandlerConnect() throws Exception {
        Connection connection = null;
        UDPNIOTransport transport = (UDPNIOTransport) UDPNIOTransportBuilder.newInstance().build();

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

    public void testConnectorHandlerConnectAndWrite() throws Exception {
        Connection connection = null;
        StreamWriter writer = null;

        UDPNIOTransport transport = (UDPNIOTransport) UDPNIOTransportBuilder.newInstance().build();

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

    public void testSimpleEcho() throws Exception {
        Connection connection = null;
        StreamReader reader;
        StreamWriter writer;

        FilterChainBuilder filterChainBuilder = FilterChainBuilder.stateless();
        filterChainBuilder.add(new TransportFilter());
        filterChainBuilder.add(new EchoFilter());

        UDPNIOTransport transport = (UDPNIOTransport) UDPNIOTransportBuilder.newInstance().build();
        transport.setProcessor(filterChainBuilder.build());

        try {
            transport.bind(PORT);
            transport.start();

            final FutureImpl<Connection> connectFuture =
                    Futures.<Connection>createSafeFuture();
            transport.connect(
                    new InetSocketAddress("localhost", PORT),
                    Futures.<Connection>toCompletionHandler(
                            connectFuture, new EmptyCompletionHandler<Connection>() {

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

        UDPNIOTransport transport = (UDPNIOTransport) UDPNIOTransportBuilder.newInstance().build();
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

        UDPNIOTransport transport = (UDPNIOTransport) UDPNIOTransportBuilder.newInstance().build();
        transport.setProcessor(filterChainBuilder.build());

        try {
            transport.bind(PORT);
            transport.start();

            final FutureImpl<Connection> connectFuture =
                    Futures.<Connection>createSafeFuture();
            transport.connect(
                    new InetSocketAddress("localhost", PORT),
                    Futures.<Connection>toCompletionHandler(
                            connectFuture, new EmptyCompletionHandler<Connection>() {

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
        int packetsNumber = 100;
        final int packetSize = 32;

        Connection connection = null;
        StreamReader reader;
        StreamWriter writer;

        FilterChainBuilder filterChainBuilder = FilterChainBuilder.stateless();
        filterChainBuilder.add(new TransportFilter());
        filterChainBuilder.add(new EchoFilter());
        
        UDPNIOTransport transport = (UDPNIOTransport) UDPNIOTransportBuilder.newInstance().build();
        transport.setProcessor(filterChainBuilder.build());

        try {
            transport.setReadBufferSize(2048);
            transport.setWriteBufferSize(2048);

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

            transport.stop();
        }
    }

    public void testWorkerThreadPoolConfiguration() throws Exception {
        UDPNIOTransport t = UDPNIOTransportBuilder.newInstance().build();
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
        UDPNIOTransport t = UDPNIOTransportBuilder.newInstance().build();
        ThreadPoolConfig config = ThreadPoolConfig.defaultConfig();
        config.setCorePoolSize(1);
        config.setMaxPoolSize(1);
        config.setPoolName("custom");
        t.setWorkerThreadPoolConfig(config);
        t.setIOStrategy(SameThreadIOStrategy.getInstance());
        assertNull(t.getWorkerThreadPoolConfig());
        assertNull(t.getWorkerThreadPool());
    }

    public void testConnectFutureCancel() throws Exception {
        UDPNIOTransport transport = UDPNIOTransportBuilder.newInstance().build();

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
        
        SocketConnectorHandler connectorHandler = UDPNIOConnectorHandler
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
}
