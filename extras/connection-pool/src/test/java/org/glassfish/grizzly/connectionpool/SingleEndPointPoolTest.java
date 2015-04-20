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
package org.glassfish.grizzly.connectionpool;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.EmptyCompletionHandler;
import org.glassfish.grizzly.GrizzlyFuture;
import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.FilterChain;
import org.glassfish.grizzly.filterchain.FilterChainBuilder;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.filterchain.TransportFilter;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import org.glassfish.grizzly.nio.transport.TCPNIOTransportBuilder;
import org.glassfish.grizzly.utils.DataStructures;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * The {@link SingleEndpointPool} tests.
 * 
 * @author Alexey Stashok
 */
public class SingleEndPointPoolTest {
    private static final int PORT = 18333;
    
    private final Set<Connection> serverSideConnections =
            Collections.newSetFromMap(
            DataStructures.<Connection, Boolean>getConcurrentMap());
    
    private TCPNIOTransport transport;
    
    @Before
    public void init() throws IOException {
        final FilterChain filterChain = FilterChainBuilder.stateless()
                .add(new TransportFilter())
                .add(new BaseFilter() {

            @Override
            public NextAction handleAccept(FilterChainContext ctx) throws IOException {
                serverSideConnections.add(ctx.getConnection());
                return ctx.getStopAction();
            }

            @Override
            public NextAction handleClose(FilterChainContext ctx) throws IOException {
                serverSideConnections.remove(ctx.getConnection());
                return ctx.getStopAction();
            }
        }).build();
        
        transport = TCPNIOTransportBuilder.newInstance().build();
        transport.setProcessor(filterChain);
        
        transport.bind(PORT);
        transport.start();
    }
    
    @After
    public void tearDown() throws IOException {
        serverSideConnections.clear();
        
        if (transport != null) {
            transport.shutdownNow();
        }
    }

    @Test
    public void testLocalAddress() throws Exception {
        InetSocketAddress localAddress = new InetSocketAddress("localhost", 60000);
        final SingleEndpointPool<SocketAddress> pool = SingleEndpointPool
                        .builder(SocketAddress.class)
                        .connectorHandler(transport)
                        .endpointAddress(new InetSocketAddress("localhost", PORT))
                        .localEndpointAddress(localAddress)
                        .build();

        try {
            Connection c1 = pool.take().get();
            assertEquals(localAddress, c1.getLocalAddress());
        } finally {
            pool.close();
        }
    }
    
    @Test
    public void testBasicPollRelease() throws Exception {
        final SingleEndpointPool<SocketAddress> pool = SingleEndpointPool
                .builder(SocketAddress.class)
                .connectorHandler(transport)
                .endpointAddress(new InetSocketAddress("localhost", PORT))
                .build();
        
        try {
            Connection c1 = pool.take().get();
            assertNotNull(c1);
            assertEquals(1, pool.size());
            Connection c2 = pool.take().get();
            assertNotNull(c2);
            assertEquals(2, pool.size());

            assertTrue(pool.release(c1));
            assertEquals(2, pool.size());

            assertTrue(pool.release(c2));
            assertEquals(2, pool.size());

            c1 = pool.take().get();
            assertNotNull(c1);
            assertEquals(2, pool.size());

            assertTrue(pool.detach(c1));
            assertEquals(1, pool.size());

            assertTrue(pool.attach(c1));
            assertEquals(2, pool.size());
            assertEquals(1, pool.getReadyConnectionsCount());

            assertTrue(pool.release(c1));

            assertEquals(2, pool.size());
            assertEquals(2, pool.getReadyConnectionsCount());

            c1 = pool.take().get();
            assertNotNull(c1);
            assertEquals(1, pool.getReadyConnectionsCount());

            c2 = pool.take().get();
            assertNotNull(c2);
            assertEquals(0, pool.getReadyConnectionsCount());

            c1.close().get(10, TimeUnit.SECONDS);
            assertEquals(1, pool.size());

            c2.close().get(10, TimeUnit.SECONDS);
            assertEquals(0, pool.size());
        } finally {
            pool.close();
        }
    }
    
    @Test
    public void testPollWaitForRelease() throws Exception {
        final SingleEndpointPool<SocketAddress> pool = SingleEndpointPool
                .builder(SocketAddress.class)
                .connectorHandler(transport)
                .endpointAddress(new InetSocketAddress("localhost", PORT))
                .maxPoolSize(2)
                .build();
        
        try {
            final Connection c1 = pool.take().get();
            assertNotNull(c1);
            assertEquals(1, pool.size());
            final Connection c2 = pool.take().get();
            assertNotNull(c2);
            assertEquals(2, pool.size());

            final Thread t = new Thread() {

                @Override
                public void run() {
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException e) {
                    }
                    
                    pool.release(c2);
                }
            };
            t.start();
            
            final Connection c3 = pool.take().get(10, TimeUnit.SECONDS);
            assertNotNull(c3);
            assertEquals(2, pool.size());
            
            pool.release(c1);
            assertEquals(2, pool.size());

            pool.release(c3);
            assertEquals(2, pool.size());
        } finally {
            pool.close();
        }
    }
    
    @Test
    public void testPollTimeout() throws Exception {
        final SingleEndpointPool<SocketAddress> pool = SingleEndpointPool
                .builder(SocketAddress.class)
                .connectorHandler(transport)
                .endpointAddress(new InetSocketAddress("localhost", PORT))
                .corePoolSize(2)
                .maxPoolSize(2)
                .build();

        try {
            Connection c1 = pool.take().get();
            assertNotNull(c1);
            assertEquals(1, pool.size());

            Connection c2 = pool.take().get();
            assertNotNull(c2);
            assertEquals(2, pool.size());

            final GrizzlyFuture<Connection> c3Future = pool.take();
            try {
                c3Future.get(2, TimeUnit.SECONDS);
                fail("TimeoutException had to be thrown");
            } catch (TimeoutException e) {
            }
            
            assertTrue(c3Future.cancel(false));
            
            assertEquals(2, pool.size());

            pool.release(c2);

            Connection c3 = pool.take().get(2, TimeUnit.SECONDS);
            assertNotNull(c3);
            assertEquals(2, pool.size());
        } finally {
            pool.close();
        }
    }
    
    @Test
    public void testEmbeddedPollTimeout() throws Exception {
        final SingleEndpointPool<SocketAddress> pool = SingleEndpointPool
                .builder(SocketAddress.class)
                .connectorHandler(transport)
                .endpointAddress(new InetSocketAddress("localhost", PORT))
                .corePoolSize(2)
                .maxPoolSize(2)
                .asyncPollTimeout(2, TimeUnit.SECONDS)
                .build();

        try {
            Connection c1 = pool.take().get();
            assertNotNull(c1);
            assertEquals(1, pool.size());

            Connection c2 = pool.take().get();
            assertNotNull(c2);
            assertEquals(2, pool.size());

            final GrizzlyFuture<Connection> c3Future = pool.take();
            try {
                c3Future.get();
            } catch (ExecutionException e) {
                final Throwable cause = e.getCause();
                assertTrue("Unexpected exception " + cause, cause instanceof TimeoutException);
            } catch (Throwable e) {
                fail("Unexpected exception " + e);
            }
            
            assertFalse(c3Future.cancel(false));
            
            assertEquals(2, pool.size());

            pool.release(c2);

            Connection c3 = pool.take().get(2, TimeUnit.SECONDS);
            assertNotNull(c3);
            assertEquals(2, pool.size());
        } finally {
            pool.close();
        }
    }
    
    @Test
    public void testKeepAliveTimeout() throws Exception {
        final long keepAliveTimeoutMillis = 5000;
        final long keepAliveCheckIntervalMillis = 1000;
        
        final int corePoolSize = 2;
        final int maxPoolSize = 5;
        
        final SingleEndpointPool<SocketAddress> pool = SingleEndpointPool
                .builder(SocketAddress.class)
                .connectorHandler(transport)
                .endpointAddress(new InetSocketAddress("localhost", PORT))
                .corePoolSize(corePoolSize)
                .maxPoolSize(maxPoolSize)
                .keepAliveTimeout(keepAliveTimeoutMillis, TimeUnit.MILLISECONDS)
                .keepAliveCheckInterval(keepAliveCheckIntervalMillis, TimeUnit.MILLISECONDS)
                .build();

        try {
            final Connection[] connections = new Connection[maxPoolSize];

            for (int i = 0; i < maxPoolSize; i++) {
                connections[i] = pool.take().get();
                assertNotNull(connections[i]);
                assertEquals(i + 1, pool.size());
            }

            for (int i = 0; i < maxPoolSize; i++) {
                pool.release(connections[i]);
                assertEquals(i + 1, pool.getReadyConnectionsCount());
            }

            Thread.sleep(keepAliveTimeoutMillis + keepAliveCheckIntervalMillis * 2);

            assertEquals(corePoolSize, pool.size());
            assertEquals(corePoolSize, serverSideConnections.size());
        } finally {
            pool.close();
        }
    }
    
    @Test
    public void testReconnect() throws Exception {
        final long reconnectDelayMillis = 1000;
        
        final FilterChain filterChain = FilterChainBuilder.stateless()
                .add(new TransportFilter())
                .build();
        
        final TCPNIOTransport clientTransport = TCPNIOTransportBuilder.newInstance()
                .setProcessor(filterChain)
                .build();
        
        final Thread t = new Thread() {

            @Override
            public void run() {
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                }

                try {
                    init();
                } catch (IOException e) {
                }
            }
        };
        
        final SingleEndpointPool<SocketAddress> pool = SingleEndpointPool
                .builder(SocketAddress.class)
                .connectorHandler(clientTransport)
                .endpointAddress(new InetSocketAddress("localhost", PORT))
                .corePoolSize(4)
                .maxPoolSize(5)
                .keepAliveTimeout(-1, TimeUnit.SECONDS)
                .reconnectDelay(reconnectDelayMillis, TimeUnit.MILLISECONDS)
                .build();
        
        try {
            clientTransport.start();
            transport.shutdownNow();
            
            t.start();
            
            final Connection c1 = pool.take().get(10, TimeUnit.SECONDS);
            assertNotNull(c1);
            assertEquals(1, pool.size());
            
        } finally {
            t.join();
            pool.close();
            clientTransport.shutdownNow();
        }
    }


    @Test
    public void testReconnectFailureNotification() throws Exception {
        final long reconnectDelayMillis = 1000;

        final FilterChain filterChain = FilterChainBuilder.stateless()
                .add(new TransportFilter())
                .build();

        final TCPNIOTransport clientTransport =
                TCPNIOTransportBuilder.newInstance()
                        .setProcessor(filterChain)
                        .build();



        final SingleEndpointPool<SocketAddress> pool = SingleEndpointPool
                .builder(SocketAddress.class)
                .connectorHandler(clientTransport)
                .endpointAddress(new InetSocketAddress("localhost", PORT))
                .corePoolSize(4)
                .maxPoolSize(5)
                .keepAliveTimeout(-1, TimeUnit.SECONDS)
                .reconnectDelay(reconnectDelayMillis, TimeUnit.MILLISECONDS)
                .build();

        try {
            clientTransport.start();
            transport.shutdownNow();
            final AtomicBoolean notified = new AtomicBoolean();
            final AtomicReference<Connection> connection =
                    new AtomicReference<Connection>();
            final CountDownLatch latch = new CountDownLatch(1);
            pool.take(
                    new EmptyCompletionHandler<Connection>() {
                        @Override
                        public void failed(Throwable throwable) {
                            notified.set(true);
                            latch.countDown();
                        }

                        @Override
                        public void completed(Connection result) {
                            connection.set(result);
                            latch.countDown();
                        }
                    });
            latch.await(15, TimeUnit.SECONDS);
            assertNull(connection.get());
            assertTrue(notified.get());
            assertEquals(0, pool.size());
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            pool.close();
            clientTransport.shutdownNow();
        }
    }
    
    @Test
    public void testConnectionTTL() throws Exception {
        final SingleEndpointPool<SocketAddress> pool = SingleEndpointPool
                .builder(SocketAddress.class)
                .connectorHandler(transport)
                .endpointAddress(new InetSocketAddress("localhost", PORT))
                .connectionTTL(2, TimeUnit.SECONDS)
                .build();
        
        try {
            Connection c1 = pool.take().get();
            assertNotNull(c1);
            assertEquals(1, pool.size());
            Connection c2 = pool.take().get();
            assertNotNull(c2);
            assertEquals(2, pool.size());
            
            pool.release(c1);
            
            final long t1 = System.currentTimeMillis();
            while (pool.size() > 0) {
                assertTrue("Timeout. pool size is still: " + pool.size(),
                        System.currentTimeMillis() - t1 <= 5000);
                Thread.sleep(1000);
            }
            
            assertEquals(0, pool.size()); // both connection should be detached
            assertTrue(!c1.isOpen());
            assertTrue(c2.isOpen());
            
            pool.release(c2);
            assertTrue(!c2.isOpen());
            
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            pool.close();
            transport.shutdownNow();
        }
    }
    
    @Test
    public void testKeepAliveZero() throws Exception {
        final SingleEndpointPool<SocketAddress> pool = SingleEndpointPool
                .builder(SocketAddress.class)
                .corePoolSize(2)
                .maxPoolSize(4)
                .failFastWhenMaxSizeReached(true)
                .connectorHandler(transport)
                .endpointAddress(new InetSocketAddress("localhost", PORT))
                .keepAliveTimeout(0, TimeUnit.MILLISECONDS)
                .build();
        
        try {
            Connection c1 = pool.take().get();
            assertNotNull(c1);
            assertEquals(1, pool.size());
            Connection c2 = pool.take().get();
            assertNotNull(c2);
            assertEquals(2, pool.size());
            Connection c3 = pool.take().get();
            assertNotNull(c3);
            assertEquals(3, pool.size());
            Connection c4 = pool.take().get();
            assertNotNull(c4);
            assertEquals(4, pool.size());
            
            pool.release(c1);
            assertEquals(3, pool.size());
            pool.release(c2);
            assertEquals(2, pool.size()); // core pool size
            pool.release(c3);
            assertEquals(2, pool.size()); // core pool size
            pool.release(c4);
            assertEquals(2, pool.size()); // core pool size
            
            assertTrue(!c1.isOpen());
            assertTrue(!c2.isOpen());
            assertTrue(c3.isOpen());
            assertTrue(c4.isOpen());
            
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            pool.close();
            transport.shutdownNow();
        }
    }    
}
