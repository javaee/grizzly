/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2017 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://oss.oracle.com/licenses/CDDL+GPL-1.1
 * or LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at LICENSE.txt.
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
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.nio.channels.SelectableChannel;
import java.util.Arrays;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.TimeUnit;
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
import org.glassfish.grizzly.streams.StreamReader;
import org.glassfish.grizzly.streams.StreamWriter;
import org.glassfish.grizzly.threadpool.ThreadPoolConfig;
import org.glassfish.grizzly.utils.ClientCheckFilter;
import org.glassfish.grizzly.utils.EchoFilter;
import org.glassfish.grizzly.utils.Futures;
import org.glassfish.grizzly.utils.ParallelWriteFilter;
import org.glassfish.grizzly.utils.RandomDelayOnWriteFilter;
import org.glassfish.grizzly.utils.StringFilter;
import org.junit.Before;
import org.junit.Test;

import static junit.framework.Assert.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;


/**
 * Unit test for {@link TCPNIOTransport}
 *
 * @author Alexey Stashok
 */
public class TCPNIOTransportTest {

    public static final int PORT = 7777;

    private static final Logger logger = Grizzly.logger(TCPNIOTransportTest.class);

    @Before
    public void setUp() throws Exception {
        ByteBufferWrapper.DEBUG_MODE = true;
    }

    @Test
    public void testBindUnbind() throws Exception {
        Connection connection = null;
        TCPNIOTransport transport =
                TCPNIOTransportBuilder.newInstance().build();
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
                fail("Server connection should be closed!");
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

            transport.shutdownNow();
        }
    }

    @Test
    public void testMultiBind() throws Exception {
        Connection connection = null;
        TCPNIOTransport transport =
                TCPNIOTransportBuilder.newInstance().build();
        try {
            final Connection serverConnection1 =
                    transport.bind(PORT);
            final Connection serverConnection2 =
                    transport.bind(PORT + 1);

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
                fail("Server connection should be closed!");
            } catch (ExecutionException e) {
                assertTrue(e.getCause() instanceof IOException);
            }

            transport.unbind(serverConnection2);
            future = transport.connect("localhost", PORT + 1);
            try {
                connection = future.get(10, TimeUnit.SECONDS);
                fail("Server connection should be closed!");
            } catch (ExecutionException e) {
                assertTrue(e.getCause() instanceof IOException);
            }

        } finally {
            if (connection != null) {
                connection.closeSilently();
            }

            transport.shutdownNow();
        }
    }

    @Test
    public void testClose() throws Exception {
        final BlockingQueue<Connection> acceptedQueue = new LinkedTransferQueue<>();
        
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

            //noinspection deprecation
            connectedConnection.addCloseListener(new GenericCloseListener() {

                @Override
                public void onClosed(Closeable closeable, CloseType type) throws IOException {
                    connectedCloseFuture.result(type == CloseType.LOCALLY);
                }
            });

            //noinspection deprecation
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

            transport.shutdownNow();
        }        
    }

    @Test
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
                    Futures.createSafeFuture();
            transport.connect(
                    new InetSocketAddress("localhost", PORT),
                    Futures.toCompletionHandler(
                    connectFuture, new EmptyCompletionHandler<Connection>()  {

                        @Override
                        public void completed(final Connection connection) {
                            synchronized (this) {
                                //noinspection deprecation
                                connection.configureStandalone(true);
                            }
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

            transport.shutdownNow();
        }
    }

    @Test
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
            transport.shutdownNow();
        }
    }

    @Test
    public void testParallelWritesBlockingMode() throws Exception {
        doTestParallelWrites(100, 100000, true);
    }

    @Test
    public void testThreadInterruptionDuringAcceptDoesNotMakeServerDeaf() throws Exception {
        final Field interruptField = TCPNIOServerConnection.class.getDeclaredField("DISABLE_INTERRUPT_CLEAR");
        interruptField.setAccessible(true);
        interruptField.setBoolean(null, true);

        final TCPNIOTransport transport = TCPNIOTransportBuilder.newInstance().build();
        transport.setSelectorRunnersCount(1);
        transport.setKernelThreadPoolConfig(ThreadPoolConfig.defaultConfig().setCorePoolSize(1).setMaxPoolSize(1));
        transport.setIOStrategy(new SameThreadIOStrategyInterruptWrapper(true));
        transport.bind(PORT);
        transport.start();

        final TCPNIOTransport clientTransport = TCPNIOTransportBuilder.newInstance().build();
        clientTransport.setIOStrategy(SameThreadIOStrategy.getInstance());

        try {
            clientTransport.start();
            SocketConnectorHandler connectorHandler = TCPNIOConnectorHandler
                    .builder(clientTransport)
                    .processor(FilterChainBuilder.stateless().add(new TransportFilter()).build())
                    .build();
            try {
                final Future<Connection> f1 = connectorHandler.connect("localhost", PORT);
                final Connection c = f1.get(5, TimeUnit.SECONDS);
                Thread.sleep(500); // Give a little time for the remote RST to be acknowledged.
                if (c.isOpen()) {
                    System.out.println("Shouldn't have received an open connection.");
                    fail();
                }
            } catch (Exception e1) {
                System.out.println(e1.toString() + ".  This is expected.");
            }

            int successfulAttempts = 0;

            for (int i = 0; i < 10; i++) {
                try {
                    final Future f2 = connectorHandler.connect("localhost", PORT);
                    f2.get(5, TimeUnit.SECONDS);
                    System.out.println("Successful connection in " + ++successfulAttempts + " attempts.");
                    break;
                } catch (Exception e2) {
                    System.out.println(e2.toString() + ": not recovered yet...");
                }
            }

        } catch (Exception e) {
            fail("Unexpected Error: " + e.toString());
            e.printStackTrace();
        } finally {
            interruptField.setBoolean(null, false);
            clientTransport.shutdownNow();
            transport.shutdownNow();
        }
    }

    @Test
    public void testThreadInterruptionElsewhereDoesNotMakeServerDeaf() throws Exception {
        final TCPNIOTransport transport = TCPNIOTransportBuilder.newInstance().build();
        transport.setSelectorRunnersCount(1);
        transport.setKernelThreadPoolConfig(ThreadPoolConfig.defaultConfig().setCorePoolSize(1).setMaxPoolSize(1));
        transport.setIOStrategy(new SameThreadIOStrategyInterruptWrapper(false));
        transport.bind(PORT);
        transport.start();

        final TCPNIOTransport clientTransport = TCPNIOTransportBuilder.newInstance().build();
        clientTransport.setIOStrategy(SameThreadIOStrategy.getInstance());

        try {

            clientTransport.start();
            SocketConnectorHandler connectorHandler = TCPNIOConnectorHandler
                    .builder(clientTransport)
                    .processor(FilterChainBuilder.stateless().add(new TransportFilter()).build())
                    .build();

            int successfulAttempts = 0;

            for (int i = 0; i < 10; i++) {
                try {
                    final Future f2 = connectorHandler.connect("localhost", PORT);
                    f2.get(5, TimeUnit.SECONDS);
                    System.out.println("Successful connection (" + ++successfulAttempts + ").");
                } catch (Exception e2) {
                    e2.printStackTrace();
                    fail();
                }
            }

        } catch (Exception e) {
            fail("Unexpected Error: " + e.toString());
            e.printStackTrace();
        } finally {
            clientTransport.shutdownNow();
            transport.shutdownNow();
        }
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
            } catch (Exception ignored) {
            }

            if (connection != null) {
                try {
                    connection.close();
                } catch (Exception ignored) {
                }
            }

            try {
                transport.shutdownNow();
            } catch (Exception ignored) {
            }

        }
    }
    
    
    // ---------------------------------------------------------- Nested Classes

    static class SameThreadIOStrategyInterruptWrapper implements IOStrategy {
        private final IOStrategy delegate = SameThreadIOStrategy.getInstance();
        private volatile boolean interruptedOnce = false;
        private final boolean interruptBefore;

        SameThreadIOStrategyInterruptWrapper(boolean interruptBefore) {
            this.interruptBefore = interruptBefore;
        }

        @Override
        public boolean executeIoEvent(final Connection connection, final IOEvent ioEvent) throws IOException {
            if (interruptBefore) {
                if(!interruptedOnce && ioEvent.equals(IOEvent.SERVER_ACCEPT)) {
                    Thread.currentThread().interrupt();
                    interruptedOnce = true;
                }
                return delegate.executeIoEvent(connection, ioEvent);
            } else {
                boolean result = delegate.executeIoEvent(connection, ioEvent);
                if(ioEvent.equals(IOEvent.SERVER_ACCEPT)) {
                    Thread.currentThread().interrupt();
                }
                return result;
            }
        }

        @Override
        public boolean executeIoEvent(final Connection connection, final IOEvent ioEvent, final boolean isIoEventEnabled) throws IOException {
            return delegate.executeIoEvent(connection, ioEvent, isIoEventEnabled);
        }

        @Override
        public Executor getThreadPoolFor(final Connection connection, final IOEvent ioEvent) {
            return delegate.getThreadPoolFor(connection, ioEvent);
        }

        @Override
        public ThreadPoolConfig createDefaultWorkerPoolConfig(final Transport transport) {
            return delegate.createDefaultWorkerPoolConfig(transport);
        }
    }

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
