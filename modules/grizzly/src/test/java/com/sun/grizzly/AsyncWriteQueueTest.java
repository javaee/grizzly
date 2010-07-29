/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2003-2008 Sun Microsystems, Inc. All rights reserved.
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
 */

package com.sun.grizzly;

import com.sun.grizzly.asyncqueue.AsyncQueueWriter;
import com.sun.grizzly.filterchain.FilterChainBuilder;
import com.sun.grizzly.filterchain.FilterChainContext;
import com.sun.grizzly.filterchain.NextAction;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import com.sun.grizzly.filterchain.TransportFilter;
import com.sun.grizzly.memory.MemoryManager;
import com.sun.grizzly.memory.MemoryUtils;
import com.sun.grizzly.nio.AbstractNIOConnection;
import com.sun.grizzly.nio.PendingWriteQueueLimitExceededException;
import com.sun.grizzly.nio.transport.TCPNIOTransport;
import com.sun.grizzly.streams.StreamReader;
import com.sun.grizzly.streams.StreamWriter;
import com.sun.grizzly.utils.EchoFilter;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * AsyncWriteQueue tests.
 * 
 * @author Alexey Stashok
 * @author Ryan Lubke
 */
public class AsyncWriteQueueTest extends GrizzlyTestCase {
    public static final int PORT = 7781;

    private static Logger logger = Grizzly.logger(AsyncWriteQueueTest.class);

    public void testAsyncWriteQueueEcho() throws Exception {
        Connection connection = null;
        StreamReader reader = null;
        StreamWriter writer = null;

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

        TCPNIOTransport transport = TransportFactory.getInstance().createTCPTransport();
        transport.setProcessor(filterChainBuilder.build());

        try {
            transport.bind(PORT);
            transport.start();

            Future<Connection> future = transport.connect("localhost", PORT);
            connection = future.get(10, TimeUnit.SECONDS);
            assertTrue(connection != null);

            connection.configureStandalone(true);

            reader = ((StandaloneProcessor) connection.getProcessor()).getStreamReader(connection);

            final Writer asyncQueueWriter = transport.getAsyncQueueIO().getWriter();
            final MemoryManager mm = transport.getMemoryManager();
            final Connection con = connection;

            final CountDownLatch latch = new CountDownLatch(packetNumber);

            final CompletionHandler completionHandler =
                    new EmptyCompletionHandler() {
                @Override
                public void completed(Object result) {
                    latch.countDown();
                }
            };

            Collection<Callable<Object>> sendTasks =
                    new ArrayList<Callable<Object>>(packetNumber + 1);
            for (int i = 0; i < packetNumber; i++) {
                final byte b = (byte) i;
                sendTasks.add(new Callable() {
                    @Override
                    public Object call() throws Exception {
                        byte[] originalMessage = new byte[packetSize];
                        Arrays.fill(originalMessage, b);
                        Buffer buffer = MemoryUtils.wrap(mm, originalMessage);
                        try {
                            asyncQueueWriter.write(con, buffer, completionHandler);
                        } catch (IOException e) {
                            assertTrue("IOException occurred", false);
                        }

                        return null;
                    }
                });
            }

            ExecutorService executorService = Executors.newFixedThreadPool(packetNumber / 10);
            try {
                executorService.invokeAll(sendTasks);
                if (!latch.await(10, TimeUnit.SECONDS)) {
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
                logger.log(Level.WARNING, "read error", e);
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
                connection.close();
            }

            transport.stop();
            TransportFactory.getInstance().close();
        }
    }


    public void testAsyncWriteQueueLimits() throws Exception {

        Connection connection = null;
        final int packetSize = 256000;
        final int queueLimit = packetSize * 2 + 1;

        FilterChainBuilder filterChainBuilder = FilterChainBuilder.stateless();
        filterChainBuilder.add(new TransportFilter());
        

        TCPNIOTransport transport = TransportFactory.getInstance().createTCPTransport();
        transport.setProcessor(filterChainBuilder.build());


        try {
            transport.bind(PORT);
            transport.start();

            Future<Connection> future = transport.connect("localhost", PORT);
            connection = future.get(10, TimeUnit.SECONDS);
            assertTrue(connection != null);
            connection.configureStandalone(true);

            final AsyncQueueWriter asyncQueueWriter = transport.getAsyncQueueIO().getWriter();
            asyncQueueWriter.setMaxPendingBytesPerConnection(queueLimit);
            final MemoryManager mm = transport.getMemoryManager();
            final Connection con = connection;

            final AtomicBoolean failed = new AtomicBoolean(false);

            transport.pause();
            
            int i = 0;
            int loopCount = 0;
            final AtomicBoolean exceptionThrown = new AtomicBoolean(false);
            final AtomicInteger exceptionAtLoopCount = new AtomicInteger();
            while (!failed.get() && loopCount < 4) {
                final int lc = loopCount;
                final byte b = (byte) i;
                byte[] originalMessage = new byte[packetSize];
                Arrays.fill(originalMessage, b);
                Buffer buffer = MemoryUtils.wrap(mm, originalMessage);
                try {
                    if (asyncQueueWriter.canWrite(con, buffer.remaining())) {
                        asyncQueueWriter.write(con, buffer);
                    } else {
                        if (loopCount == 3) {
                            asyncQueueWriter.write(con, buffer,
                                    new EmptyCompletionHandler() {

                                        @Override
                                        public void failed(Throwable throwable) {
                                            if (throwable instanceof PendingWriteQueueLimitExceededException) {
                                                exceptionThrown.compareAndSet(false, true);
                                                exceptionAtLoopCount.set(lc);
                                                assertTrue(((AbstractNIOConnection) con).getAsyncWriteQueue().spaceInBytes() + packetSize > queueLimit);
                                            }
                                            failed.compareAndSet(false, true);
                                        }
                                    });
                        } else {
                            loopCount++;
                            transport.resume();
                            Thread.sleep(5000);
                            transport.pause();
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    assertTrue("IOException occurred: " + e.toString(), false);

                }
                i++;
            }

            if (!exceptionThrown.get()) {
                fail("No Exception thrown when queue write limit exceeded");
            }
            if (exceptionAtLoopCount.get() != 3) {
                fail("Expected exception to occur at 4th iteration of test loop.  Occurred at: " + exceptionAtLoopCount);
            }

        } finally {
            if (connection != null) {
                connection.close();
            }
            if (transport.isPaused()) {
                transport.resume();
            }
            transport.stop();
            TransportFactory.getInstance().close();
        }
    }
}
