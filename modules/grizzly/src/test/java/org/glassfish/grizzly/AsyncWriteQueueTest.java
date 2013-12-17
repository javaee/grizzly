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

import java.io.IOException;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.grizzly.asyncqueue.AsyncQueueWriter;
import org.glassfish.grizzly.asyncqueue.TaskQueue;
import org.glassfish.grizzly.asyncqueue.WritableMessage;
import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.FilterChain;
import org.glassfish.grizzly.filterchain.FilterChainBuilder;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.filterchain.TransportFilter;
import org.glassfish.grizzly.impl.FutureImpl;
import org.glassfish.grizzly.impl.SafeFutureImpl;
import org.glassfish.grizzly.memory.Buffers;
import org.glassfish.grizzly.memory.MemoryManager;
import org.glassfish.grizzly.nio.NIOConnection;
import org.glassfish.grizzly.nio.transport.TCPNIOConnectorHandler;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import org.glassfish.grizzly.nio.transport.TCPNIOTransportBuilder;
import org.glassfish.grizzly.streams.StreamReader;
import org.glassfish.grizzly.utils.Charsets;
import org.glassfish.grizzly.utils.EchoFilter;
import org.glassfish.grizzly.utils.Futures;
import org.glassfish.grizzly.utils.StringFilter;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import static org.junit.Assert.*;

/**
 * AsyncWriteQueue tests.
 * 
 * @author Alexey Stashok
 * @author Ryan Lubke
 */
@SuppressWarnings("unchecked")
@RunWith(Parameterized.class)
public class AsyncWriteQueueTest {
    public static final int PORT = 7781;

    private static final Logger LOGGER = Grizzly.logger(AsyncWriteQueueTest.class);

    @Parameters
    public static Collection<Object[]> getOptimizedForMultiplexing() {
        return Arrays.asList(new Object[][]{
                    {Boolean.FALSE},
                    {Boolean.TRUE}
                });
    }
    
    private final boolean isOptimizedForMultiplexing;
    
    public AsyncWriteQueueTest(boolean isOptimizedForMultiplexing) {
        this.isOptimizedForMultiplexing = isOptimizedForMultiplexing;
    }
    
    private static TCPNIOTransport createTransport(
            final boolean isOptimizedForMultiplexing) {
        
        return TCPNIOTransportBuilder
                .newInstance()
                .setOptimizedForMultiplexing(isOptimizedForMultiplexing)
                .build();
    }
    
    @Test
    public void testParallelWrites() throws Exception {
        Connection connection = null;

        final AtomicInteger serverRcvdMessages = new AtomicInteger();
        final AtomicInteger clientRcvdMessages = new AtomicInteger();

        FilterChainBuilder filterChainBuilder = FilterChainBuilder.stateless();
        filterChainBuilder.add(new TransportFilter());
        filterChainBuilder.add(new StringFilter(Charsets.UTF8_CHARSET));
        filterChainBuilder.add(new EchoFilter() {

            @Override
            public NextAction handleRead(FilterChainContext ctx)
                    throws IOException {
                serverRcvdMessages.incrementAndGet();
                return super.handleRead(ctx);
            }
        });

        final TCPNIOTransport transport = createTransport(isOptimizedForMultiplexing);
        transport.setProcessor(filterChainBuilder.build());

        try {
            transport.bind(PORT);
            transport.start();

            FilterChain clientFilterChain = FilterChainBuilder.stateless()
                    .add(new TransportFilter())
                    .add(new StringFilter(Charsets.UTF8_CHARSET))
                    .add(new BaseFilter() {

                @Override
                public NextAction handleRead(FilterChainContext ctx) throws IOException {
                    clientRcvdMessages.incrementAndGet();
                    return super.handleRead(ctx);
                }
            }).build();
            
            SocketConnectorHandler connectorHandler = 
                    TCPNIOConnectorHandler.builder(transport)
                    .processor(clientFilterChain)
                    .build();
            
            Future<Connection> future = connectorHandler.connect("localhost", PORT);
            connection = future.get(10, TimeUnit.SECONDS);
            assertTrue(connection != null);

            final AsyncQueueWriter asyncQueueWriter = transport.getAsyncQueueIO().getWriter();
            asyncQueueWriter.setMaxPendingBytesPerConnection(256);            

            final Connection finalConnection = connection;
            
            final int senderThreads = 16;
            final int packetsCount = 1000;
            
            Collection<Callable<Object>> sendTasks =
                    new ArrayList<Callable<Object>>(senderThreads);
            for (int i = 0; i < senderThreads; i++) {
                final int id = i;
                sendTasks.add(new Callable<Object>() {
                    @Override
                    public Object call() throws Exception {
                        final AtomicInteger acceptedPackets = new AtomicInteger();
                        final FutureImpl<Boolean> f = SafeFutureImpl.create();
                        final CompletionHandler<WriteResult> ch = new EmptyCompletionHandler<WriteResult>() {

                            @Override
                            public void failed(Throwable throwable) {
                                f.failure(throwable);
                            }
                        };
                        
                        asyncQueueWriter.notifyWritePossible(finalConnection,
                                                             new WriteHandler() {

                                                                 @Override
                                                                 public void onWritePossible()
                                                                 throws Exception {
                                                                     final int
                                                                             msgNum =
                                                                             acceptedPackets
                                                                                     .incrementAndGet();
                                                                     if (msgNum >= packetsCount) {
                                                                         if (msgNum == packetsCount) {
                                                                             f.result(
                                                                                     Boolean.TRUE);
                                                                         }
                                                                         return;
                                                                     }

                                                                     finalConnection
                                                                             .write(
                                                                                     "client(" + id + ")-" + msgNum,
                                                                                     ch);
                                                                     asyncQueueWriter
                                                                             .notifyWritePossible(
                                                                                     finalConnection,
                                                                                     this);
                                                                 }

                                                                 @Override
                                                                 public void onError(Throwable t) {
                                                                     f.failure(
                                                                             t);
                                                                 }
                                                             });
                                                
                        return f.get(10, TimeUnit.SECONDS);
                    }
                });
            }

            ExecutorService executorService = Executors.newFixedThreadPool(senderThreads);
            try {
                List<Future<Object>> futures = executorService.invokeAll(sendTasks);
                
                for (int i = 0; i < futures.size(); i++) {
                    try {
                        assertTrue(Boolean.TRUE.equals(futures.get(i).get(10, TimeUnit.SECONDS)));
                    } catch (Exception e) {
                        fail("Timeout on thread#" + i);
                        throw e;
                    }
                }
            } finally {
                executorService.shutdown();
            }
        } finally {
            if (connection != null) {
                connection.closeSilently();
            }

            transport.shutdownNow();
        }
    }
    
    @Test
    public void testAsyncWriteQueueEcho() throws Exception {
        Connection connection = null;
        StreamReader reader;

        final int packetNumber = 127;
        final int packetSize = 128000;

        final AtomicInteger serverRcvdBytes = new AtomicInteger();

        FilterChainBuilder filterChainBuilder = FilterChainBuilder.stateless();
        filterChainBuilder.add(new TransportFilter());
        filterChainBuilder.add(new EchoFilter() {

            @Override
            public NextAction handleRead(FilterChainContext ctx)
                    throws IOException {
                serverRcvdBytes.addAndGet(((Buffer) ctx.getMessage()).remaining());
                return super.handleRead(ctx);
            }
        });

        TCPNIOTransport transport = createTransport(isOptimizedForMultiplexing);
        transport.setProcessor(filterChainBuilder.build());

        try {
            final AsyncQueueWriter<SocketAddress> asyncQueueWriter =
                    transport.getAsyncQueueIO().getWriter();
            asyncQueueWriter.setMaxPendingBytesPerConnection(-1);

            transport.bind(PORT);
            transport.start();

            Future<Connection> future = transport.connect("localhost", PORT);
            connection = future.get(10, TimeUnit.SECONDS);
            assertTrue(connection != null);

            connection.configureStandalone(true);

            reader = ((StandaloneProcessor) connection.getProcessor()).getStreamReader(connection);

            final MemoryManager mm = transport.getMemoryManager();
            final Connection con = connection;

            final AtomicInteger counter = new AtomicInteger(packetNumber);
            final FutureImpl<Boolean> sentFuture = Futures.createSafeFuture();
            
            final CompletionHandler<WriteResult<WritableMessage, SocketAddress>> completionHandler =
                    new EmptyCompletionHandler<WriteResult<WritableMessage, SocketAddress>>() {
                @Override
                public void completed(WriteResult<WritableMessage, SocketAddress> result) {
                    if (counter.decrementAndGet() == 0) {
                        sentFuture.result(true);
                    }
                }

                @Override
                public void failed(Throwable throwable) {
                    sentFuture.failure(throwable);
                }
            };

            Collection<Callable<Object>> sendTasks =
                    new ArrayList<Callable<Object>>(packetNumber + 1);
            for (int i = 0; i < packetNumber; i++) {
                final byte b = (byte) i;
                sendTasks.add(new Callable<Object>() {
                    @Override
                    public Object call() throws Exception {
                        byte[] originalMessage = new byte[packetSize];
                        Arrays.fill(originalMessage, b);
                        Buffer buffer = Buffers.wrap(mm, originalMessage);
                        asyncQueueWriter.write(con, buffer, completionHandler);

                        return null;
                    }
                });
            }

            ExecutorService executorService = Executors.newFixedThreadPool(packetNumber / 10);
            try {
                executorService.invokeAll(sendTasks);
                if (!sentFuture.get(10, TimeUnit.SECONDS)) {
                    assertTrue("Send timeout!", false);
                }
            } finally {
                executorService.shutdown();
            }

            int responseSize = packetNumber * packetSize;
            Future<Integer> readFuture = reader.notifyAvailable(responseSize);
            Integer available = null;
            
            try {
                available = readFuture.get(10, TimeUnit.SECONDS);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "read error", e);
            }

            assertTrue("Read timeout. Server received: " +serverRcvdBytes.get() +
                    " bytes. Expected: " + packetNumber * packetSize,
                     available != null);

            byte[] echoMessage = new byte[responseSize];
            reader.readByteArray(echoMessage);

            // Check interleaving...

            boolean[] isByteUsed = new boolean[packetNumber];
            int offset = 0;
            for(int i=0; i<packetNumber; i++) {
                byte pattern = echoMessage[offset];

                assertEquals("Pattern: " + pattern + " was already used",
                        false, isByteUsed[pattern]);

                isByteUsed[pattern] = true;
                for(int j = 0; j < packetSize; j++) {
                    byte check = echoMessage[offset++];
                    assertEquals("Echo doesn't match. Offset: " + offset +
                            " pattern: " + pattern + " found: " + check,
                            pattern, check);
                }
            }

        } finally {
            if (connection != null) {
                connection.closeSilently();
            }

            transport.shutdownNow();
        }
    }

    @Test
    public void testQueueNotification() throws Exception {

        Connection connection = null;
        final int packetSize = 256000;

        FilterChainBuilder filterChainBuilder = FilterChainBuilder.stateless();
        filterChainBuilder.add(new TransportFilter());


        final TCPNIOTransport transport = createTransport(isOptimizedForMultiplexing);
        transport.setProcessor(filterChainBuilder.build());


        try {
            final AsyncQueueWriter asyncQueueWriter = transport.getAsyncQueueIO().getWriter();
            asyncQueueWriter.setMaxPendingBytesPerConnection(256000 * 10);
            System.out.println(
                    "Max Space: " + asyncQueueWriter.getMaxPendingBytesPerConnection());
            
            transport.bind(PORT);
            transport.start();

            Future<Connection> future = transport.connect("localhost", PORT);
            connection = future.get(10, TimeUnit.SECONDS);
            assertTrue(connection != null);
            connection.configureStandalone(true);

            final MemoryManager mm = transport.getMemoryManager();
            final Connection con = connection;

            transport.pause();
            final TaskQueue tqueue = ((NIOConnection) connection).getAsyncWriteQueue();

            do {
                byte[] originalMessage = new byte[packetSize];
                Arrays.fill(originalMessage, (byte) 1);
                Buffer buffer = Buffers.wrap(mm, originalMessage);
                try {
                    if (asyncQueueWriter.canWrite(con)) {
                        asyncQueueWriter.write(con, buffer);
                    }
                } catch (IOException e) {
                    assertTrue("IOException occurred: " + e.toString(), false);
                }
            } while (asyncQueueWriter.canWrite(con));  // fill the buffer

            // out of space.  Add a monitor to be notified when space is available
            tqueue.notifyWritePossible(new WriteQueueHandler(con));

            transport.resume(); // resume the transport so bytes start draining from the queue

            long start = 0;
            try {
                System.out.println("Waiting for free space notification.  Max wait time is 10000ms.");
                start = System.currentTimeMillis();
                Thread.sleep(10000);  // should be interrupted before time completes
                fail("Thread not interrupted within 10 seconds.");
            } catch (InterruptedException ie) {
                long result = (System.currentTimeMillis() - start);
                System.out.println("Notified in " + result + "ms");
            }
            assertTrue(tqueue.spaceInBytes() < asyncQueueWriter.getMaxPendingBytesPerConnection()); 
            System.out.println("Queue Space: " + tqueue.spaceInBytes());

        } finally {
            if (connection != null) {
                connection.closeSilently();
            }
            if (transport.isPaused()) {
                transport.resume();
            }
            transport.shutdownNow();
        }
    }

    @Test
    public void testAsyncWriteQueueReentrants() throws Exception {
        Connection connection = null;

        final AtomicInteger serverRcvdBytes = new AtomicInteger();

        FilterChainBuilder filterChainBuilder = FilterChainBuilder.stateless();
        filterChainBuilder.add(new TransportFilter());
        filterChainBuilder.add(new BaseFilter() {

            @Override
            public NextAction handleRead(FilterChainContext ctx)
                    throws IOException {
                serverRcvdBytes.addAndGet(((Buffer) ctx.getMessage()).remaining());
                return ctx.getStopAction();
            }
        });

        // reentrants limitation is applicable for non-multiplexing-optimized write queue
        TCPNIOTransport transport = createTransport(false);
        transport.setProcessor(filterChainBuilder.build());

        try {
            transport.bind(PORT);
            transport.start();

            Future<Connection> future = transport.connect("localhost", PORT);
            connection = future.get(10, TimeUnit.SECONDS);
            assertTrue(connection != null);

            connection.configureStandalone(true);

            final AsyncQueueWriter<SocketAddress> asyncQueueWriter = transport.getAsyncQueueIO().getWriter();
            
            final MemoryManager mm = transport.getMemoryManager();
            final Connection con = connection;

            final int maxAllowedReentrants = Writer.Reentrant.getMaxReentrants();
            final int reentrantsToTest = maxAllowedReentrants * 3;
            
            final AtomicInteger maxReentrantsNoticed = new AtomicInteger();

            final AtomicInteger packetCounter = new AtomicInteger();

            final FutureImpl<Boolean> resultFuture = SafeFutureImpl.create();

            Buffer buffer = Buffers.EMPTY_BUFFER;

            final ThreadLocal<Integer> reentrantsCounter = new ThreadLocal<Integer>() {
                @Override
                protected Integer initialValue() {
                    return -1;
                }
            };
            
            reentrantsCounter.set(0);

            try {
            asyncQueueWriter.write(con, buffer,
                    new EmptyCompletionHandler<WriteResult<WritableMessage, SocketAddress>>() {

                        @Override
                        public void completed(
                                WriteResult<WritableMessage, SocketAddress> result) {

                            final int packetNum = packetCounter.incrementAndGet();
                            if (packetNum <= reentrantsToTest) {
                                
                                final int reentrantNum = reentrantsCounter.get() + 1;
                                try {
                                    reentrantsCounter.set(reentrantNum);
                                    
                                    if (reentrantNum > maxReentrantsNoticed.get()) {
                                        maxReentrantsNoticed.set(reentrantNum);
                                    }
                                    
                                    Buffer bufferInner = Buffers.wrap(mm, ""
                                            + ((char) ('A' + packetNum)));
                                    asyncQueueWriter.write(con, bufferInner, this);
                                } finally {
                                    reentrantsCounter.set(reentrantNum - 1);
                                }
                                
                                
                            } else {
                                resultFuture.result(Boolean.TRUE);
                            }
                        }

                        @Override
                        public void failed(Throwable throwable) {
                            resultFuture.failure(throwable);
                        }
                    });
            } finally {
                reentrantsCounter.remove();
            }
            
            assertTrue(resultFuture.get(10, TimeUnit.SECONDS));

            assertTrue("maxReentrantNoticed=" + maxReentrantsNoticed + " maxAllowed=" + maxAllowedReentrants,
                    maxReentrantsNoticed.get() <= maxAllowedReentrants);

        } finally {
            if (connection != null) {
                connection.closeSilently();
            }

            transport.shutdownNow();
        }
    }
    
    // ---------------------------------------------------------- Nested Classes


    private static class WriteQueueHandler implements WriteHandler {

        private final Transport transport;
        private final Thread current;


        // -------------------------------------------------------- Constructors


        public WriteQueueHandler(final Connection c) {
            transport = c.getTransport();
            current = Thread.currentThread();
        }


        // -------------------------------------- Methods from WriteHandler

        @Override
        public void onWritePossible() throws Exception {
            transport.pause(); // prevent more writes
            current.interrupt(); // wake up the test thread
        }

        @Override
        public void onError(Throwable t) {
        }

    } // END WriteQueueFreeSpaceMonitor
}
