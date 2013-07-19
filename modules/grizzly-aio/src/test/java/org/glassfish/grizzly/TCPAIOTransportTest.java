/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011 Oracle and/or its affiliates. All rights reserved.
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

import org.glassfish.grizzly.aio.transport.TCPAIOTransport;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.FilterChainBuilder;
import org.glassfish.grizzly.filterchain.TransportFilter;
import org.glassfish.grizzly.memory.ByteBufferWrapper;
import org.glassfish.grizzly.streams.StreamReader;
import org.glassfish.grizzly.streams.StreamWriter;
import org.glassfish.grizzly.utils.EchoFilter;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.grizzly.aio.transport.TCPAIOServerConnection;
import org.glassfish.grizzly.aio.transport.TCPAIOTransportBuilder;

/**
 * Unit test for {@link TCPAIOTransport}
 *
 * @author Alexey Stashok
 */
public class TCPAIOTransportTest extends GrizzlyTestCase {

    public static final int PORT = 7777;

    private static final Logger logger = Grizzly.logger(TCPAIOTransportTest.class);

    @Override
    protected void setUp() throws Exception {
        ByteBufferWrapper.DEBUG_MODE = true;
    }


    public void testStartStop() throws IOException {
        TCPAIOTransport transport = TCPAIOTransportBuilder.newInstance().build();

        try {
            transport.bind(PORT);
            transport.start();
        } finally {
            transport.shutdownNow();
        }
    }

    public void testConnectorHandlerConnect() throws Exception {
        Connection connection = null;
        TCPAIOTransport transport = TCPAIOTransportBuilder.newInstance().build();

        try {
            transport.bind(PORT);
            transport.start();

            Future<Connection> future = transport.connect("localhost", PORT);
            connection = future.get(10, TimeUnit.SECONDS);
            assertTrue(connection != null);
        } finally {
            if (connection != null) {
                connection.close();
            }

            transport.shutdownNow();
        }
    }

    public void testBindUnbind() throws Exception {
        Connection connection = null;
        TCPAIOTransport transport = TCPAIOTransportBuilder.newInstance().build();

        try {
            transport.bind(PORT);
            transport.start();

            Future<Connection> future = transport.connect("localhost", PORT);
            connection = future.get(10, TimeUnit.SECONDS);
            assertTrue(connection != null);
            connection.close();

            transport.unbindAll();

            future = transport.connect("localhost", PORT);
            try {
                connection = future.get(10, TimeUnit.SECONDS);
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
                connection.close();
            }

            transport.shutdownNow();
        }
    }

    public void testMultiBind() throws Exception {
        Connection connection = null;
        TCPAIOTransport transport = TCPAIOTransportBuilder.newInstance().build();

        try {
            final TCPAIOServerConnection serverConnection1 = transport.bind(PORT);
            final TCPAIOServerConnection serverConnection2 = transport.bind(PORT + 1);

            transport.start();

            Future<Connection> future = transport.connect("localhost", PORT);
            connection = future.get(10, TimeUnit.SECONDS);
            assertTrue(connection != null);
            connection.close();

            future = transport.connect("localhost", PORT + 1);
            connection = future.get(10, TimeUnit.SECONDS);
            assertTrue(connection != null);
            connection.close();

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
                connection.close();
            }

            transport.shutdownNow();
        }
    }

    public void testConnectorHandlerConnectAndWrite() throws Exception {
        Connection connection = null;
        StreamWriter writer = null;

        TCPAIOTransport transport = TCPAIOTransportBuilder.newInstance().build();

        try {
            transport.bind(PORT);
            transport.start();

            Future<Connection> connectFuture = transport.connect(
                    new InetSocketAddress("localhost", PORT),
                    new EmptyCompletionHandler<Connection>()  {

                        @Override
                        public void completed(final Connection connection) {
                            connection.configureStandalone(true);
                        }
                    });
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
                connection.close();
            }

            transport.shutdownNow();
        }
    }

    public void testSimpleEcho() throws Exception {
        Connection connection = null;
        StreamReader reader;
        StreamWriter writer;

        FilterChainBuilder filterChainBuilder = FilterChainBuilder.stateless();
        filterChainBuilder.add(new TransportFilter());
        filterChainBuilder.add(new EchoFilter());

        TCPAIOTransport transport = TCPAIOTransportBuilder.newInstance().build();
        transport.setProcessor(filterChainBuilder.build());

        try {
            transport.bind(PORT);
            transport.start();

            Future<Connection> connectFuture = transport.connect(
                    new InetSocketAddress("localhost", PORT),
                    new EmptyCompletionHandler<Connection>()  {

                        @Override
                        public void completed(final Connection connection) {
                            connection.configureStandalone(true);
                        }
                    });
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
                connection.close();
            }

            transport.shutdownNow();
        }
    }

    public void testSeveralPacketsEcho() throws Exception {
        int packetsNumber = 100;
        final int packetSize = 170644;
        
        Connection connection = null;
        StreamReader reader;
        StreamWriter writer;

        FilterChainBuilder filterChainBuilder = FilterChainBuilder.stateless();
        filterChainBuilder.add(new TransportFilter());
        filterChainBuilder.add(new EchoFilter());
        TCPAIOTransport transport = TCPAIOTransportBuilder.newInstance().build();
        transport.setProcessor(filterChainBuilder.build());

        try {
            transport.bind(PORT);
            transport.start();

            Future<Connection> connectFuture = transport.connect(
                    new InetSocketAddress("localhost", PORT),
                    new EmptyCompletionHandler<Connection>()  {

                        @Override
                        public void completed(final Connection connection) {
                            connection.configureStandalone(true);
                            connection.configureBlocking(true);
                        }
                    });
                    
            connection = connectFuture.get(10, TimeUnit.SECONDS);
            assertTrue(connection != null);

            reader = StandaloneProcessor.INSTANCE.getStreamReader(connection);
            writer = StandaloneProcessor.INSTANCE.getStreamWriter(connection);

            for (int i = 0; i < packetsNumber; i++) {
                byte[] originalMessage = new byte[packetSize];
                Arrays.fill(originalMessage, (byte) i);
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
                connection.close();
            }

            transport.shutdownNow();
        }
    }

    public void testAsyncReadWriteEcho() throws Exception {
        Connection connection = null;
        StreamReader reader;
        StreamWriter writer;

        FilterChainBuilder filterChainBuilder = FilterChainBuilder.stateless();
        filterChainBuilder.add(new TransportFilter());
        filterChainBuilder.add(new EchoFilter());
        
        TCPAIOTransport transport = TCPAIOTransportBuilder.newInstance().build();
        transport.setProcessor(filterChainBuilder.build());

        try {
            transport.bind(PORT);
            transport.start();

            Future<Connection> connectFuture = transport.connect(
                    new InetSocketAddress("localhost", PORT),
                    new EmptyCompletionHandler<Connection>()  {

                        @Override
                        public void completed(final Connection connection) {
                            connection.configureStandalone(true);
                        }
                    });
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
                connection.close();
            }

            transport.shutdownNow();
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

        TCPAIOTransport transport = TCPAIOTransportBuilder.newInstance().build();
        transport.setProcessor(filterChainBuilder.build());

        try {
            transport.bind(PORT);
            transport.start();

            Future<Connection> connectFuture = transport.connect(
                    new InetSocketAddress("localhost", PORT),
                    new EmptyCompletionHandler<Connection>()  {

                        @Override
                        public void completed(final Connection connection) {
                            connection.configureStandalone(true);
                        }
                    });
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
                        throwable.printStackTrace();
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
                            serverBytesCounter.get() + " bytes sendLatch=" + sendLatch, false);
                }
                assertTrue(future.isDone());
                reader.readByteArray(message);
                assertTrue(Arrays.equals(pattern, message));
            }

            assertEquals(0, sendLatch.getCount());
        } finally {
            if (connection != null) {
                connection.close();
            }

            transport.shutdownNow();
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

        TCPAIOTransport transport = TCPAIOTransportBuilder.newInstance().build();
        transport.setProcessor(filterChainBuilder.build());

        try {
            transport.bind(PORT);
            transport.start();

            Future<Connection> connectFuture = transport.connect(
                    new InetSocketAddress("localhost", PORT),
                    new EmptyCompletionHandler<Connection>()  {

                        @Override
                        public void completed(final Connection connection) {
                            connection.configureStandalone(true);
                        }
                    });
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
                connection.close();
            }

            transport.shutdownNow();
        }
    }
}
