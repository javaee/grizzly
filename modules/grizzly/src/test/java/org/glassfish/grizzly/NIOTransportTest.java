/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved.
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
import org.glassfish.grizzly.filterchain.TransportFilter;
import org.glassfish.grizzly.memory.Buffers;
import org.glassfish.grizzly.nio.NIOTransport;
import org.glassfish.grizzly.nio.transport.TCPNIOTransportBuilder;
import org.glassfish.grizzly.nio.transport.UDPNIOTransportBuilder;
import org.glassfish.grizzly.strategies.SameThreadIOStrategy;
import org.glassfish.grizzly.strategies.WorkerThreadIOStrategy;
import org.glassfish.grizzly.threadpool.ThreadPoolConfig;
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
import java.util.concurrent.atomic.AtomicBoolean;

import static junit.framework.Assert.assertTrue;
import static org.junit.Assert.*;

@RunWith(Parameterized.class)
public class NIOTransportTest {

    private static final int PORT = 7777;

    @Parameterized.Parameters
    public static Collection<Object[]> getTransport() {
        return Arrays.asList(new Object[][]{
                {TCPNIOTransportBuilder.newInstance()},
                {UDPNIOTransportBuilder.newInstance()}
        });
    }

    private final NIOTransport transport;

    public NIOTransportTest(final NIOTransportBuilder<?> transportBuilder) {
        this.transport = transportBuilder.build();
    }


    // ------------------------------------------------------------ Test Methods

    @Test
    public void testStartStop() throws IOException {

        try {
            transport.bind(PORT);
            transport.start();
        } finally {
            transport.shutdownNow();
        }
    }

    @Test
    public void testStartStopStart() throws Exception {

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
        assertEquals(30, transport.getBlockingWriteTimeout(TimeUnit.SECONDS));
        assertEquals(30, transport.getBlockingReadTimeout(TimeUnit.SECONDS));
        transport.setBlockingReadTimeout(45, TimeUnit.MINUTES);
        assertEquals(TimeUnit.MILLISECONDS.convert(45, TimeUnit.MINUTES),
                     transport.getBlockingReadTimeout(TimeUnit.MILLISECONDS));
        assertEquals(30, transport.getBlockingWriteTimeout(TimeUnit.SECONDS));
        transport.setBlockingReadTimeout(-5, TimeUnit.SECONDS);
        assertEquals(-1, transport.getBlockingReadTimeout(TimeUnit.MILLISECONDS));
        transport.setBlockingReadTimeout(0, TimeUnit.SECONDS);
        assertEquals(-1, transport.getBlockingReadTimeout(TimeUnit.MILLISECONDS));
    }

    @Test
    public void testConnectorHandlerConnect() throws Exception {
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

    @SuppressWarnings("unchecked")
    @Test
    public void testConnectorHandlerConnectAndWrite() throws Exception {
        Connection connection = null;

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

            transport.shutdownNow();
        }
    }

    @Test
    public void testWorkerThreadPoolConfiguration() throws Exception {
        ThreadPoolConfig config = ThreadPoolConfig.newConfig();
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
        ThreadPoolConfig config = ThreadPoolConfig.newConfig();
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
