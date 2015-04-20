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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.glassfish.grizzly.Connection;
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
 * The {@link MultiEndpointPool} tests.
 * 
 * @author Alexey Stashok
 */
public class MultiEndPointPoolTest {
    private static final int PORT = 18334;
    private static final int NUMBER_OF_PORTS_TO_BIND = 3;
    
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
        
        for (int i = 0; i < NUMBER_OF_PORTS_TO_BIND; i++) {
            transport.bind(PORT + i);
        }
        
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
        InetSocketAddress localAddress =
                new InetSocketAddress("localhost", 60000);
        final MultiEndpointPool<SocketAddress> pool = MultiEndpointPool
                        .builder(SocketAddress.class)
                        .maxConnectionsPerEndpoint(3)
                        .maxConnectionsTotal(15)
                        .keepAliveTimeout(-1, TimeUnit.SECONDS)
                        .build();
        final Endpoint<SocketAddress> key1 =
                Endpoint.Factory.<SocketAddress>create(
                        new InetSocketAddress("localhost", PORT),
                        localAddress,
                        transport);
        try {
            Connection c1 = pool.take(key1).get();
            assertEquals(localAddress, c1.getLocalAddress());
        } finally {
            pool.close();
        }
    }
    
    @Test
    public void testBasicPollRelease() throws Exception {
        final MultiEndpointPool<SocketAddress> pool = MultiEndpointPool
                .builder(SocketAddress.class)
                .maxConnectionsPerEndpoint(3)
                .maxConnectionsTotal(15)
                .keepAliveTimeout(-1, TimeUnit.SECONDS)
                .build();
        
        try {
        
            final Endpoint<SocketAddress> key1
                    = Endpoint.Factory.<SocketAddress>create(
                            new InetSocketAddress("localhost", PORT),
                            transport);

            final Endpoint<SocketAddress> key2
                    = Endpoint.Factory.<SocketAddress>create(
                            new InetSocketAddress("localhost", PORT + 1),
                            transport);

            Connection c11 = pool.take(key1).get();
            assertNotNull(c11);
            assertEquals(1, pool.size());
            Connection c12 = pool.take(key1).get();
            assertNotNull(c12);
            assertEquals(2, pool.size());

            Connection c21 = pool.take(key2).get();
            assertNotNull(c21);
            assertEquals(3, pool.size());
            Connection c22 = pool.take(key2).get();
            assertNotNull(c22);
            assertEquals(4, pool.size());

            assertTrue(pool.release(c11));
            assertEquals(4, pool.size());

            assertTrue(pool.release(c21));
            assertEquals(4, pool.size());

            c11 = pool.take(key1).get();
            assertNotNull(c11);
            assertEquals(4, pool.size());

            assertTrue(pool.detach(c11));
            assertEquals(3, pool.size());

            assertTrue(pool.attach(key1, c11));
            assertEquals(4, pool.size());

            assertTrue(pool.release(c11));

            assertEquals(4, pool.size());

            c11 = pool.take(key1).get();
            assertNotNull(c11);

            c21 = pool.take(key2).get();
            assertNotNull(c21);

            c11.close().get(10, TimeUnit.SECONDS);
            assertEquals(3, pool.size());

            c12.close().get(10, TimeUnit.SECONDS);
            assertEquals(2, pool.size());

            c21.close().get(10, TimeUnit.SECONDS);
            assertEquals(1, pool.size());

            c22.close().get(10, TimeUnit.SECONDS);
            assertEquals(0, pool.size());
        } finally {
            pool.close();
        }
    }
    
    @Test
    public void testTotalPoolSizeLimit() throws Exception {
        final MultiEndpointPool<SocketAddress> pool = MultiEndpointPool
                .builder(SocketAddress.class)
                .maxConnectionsPerEndpoint(2)
                .maxConnectionsTotal(2)
                .keepAliveTimeout(-1, TimeUnit.SECONDS)
                .build();
        
        try {
            final Endpoint<SocketAddress> key1
                    = Endpoint.Factory.<SocketAddress>create(
                            new InetSocketAddress("localhost", PORT),
                            transport);

            final Endpoint<SocketAddress> key2
                    = Endpoint.Factory.<SocketAddress>create(
                            new InetSocketAddress("localhost", PORT + 1),
                            transport);

            Connection c11 = pool.take(key1).get();
            assertNotNull(c11);
            assertEquals(1, pool.size());
            final Connection c12 = pool.take(key1).get();
            assertNotNull(c12);
            assertEquals(2, pool.size());
            
            
            final GrizzlyFuture<Connection> c21Future = pool.take(key2);
            try {
                c21Future.get(2, TimeUnit.SECONDS);
                fail("TimeoutException had to be thrown");
            } catch (TimeoutException e) {
            }
            
            assertTrue(c21Future.cancel(false));
            assertEquals(2, pool.size());

            final Thread t = new Thread() {

                @Override
                public void run() {
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException e) {
                    }

                    c12.closeSilently();
                }
            };
            t.start();

            final Connection c21 = pool.take(key2).get(10, TimeUnit.SECONDS);
            assertNotNull(c12);
            assertEquals(2, pool.size());

            c11.close().get(10, TimeUnit.SECONDS);
            assertEquals(1, pool.size());

            c21.close().get(10, TimeUnit.SECONDS);
            assertEquals(0, pool.size());
        } finally {
            pool.close();
        }
    }
    
    @Test
    public void testSingleEndpointClose() throws Exception {
        final int maxConnectionsPerEndpoint = 4;
        
        final MultiEndpointPool<SocketAddress> pool = MultiEndpointPool
                .builder(SocketAddress.class)
                .maxConnectionsPerEndpoint(maxConnectionsPerEndpoint)
                .maxConnectionsTotal(maxConnectionsPerEndpoint * 2)
                .keepAliveTimeout(-1, TimeUnit.SECONDS)
                .build();
        
        try {
            final Endpoint<SocketAddress> key1
                    = Endpoint.Factory.<SocketAddress>create(
                            new InetSocketAddress("localhost", PORT),
                            transport);

            final Endpoint<SocketAddress> key2
                    = Endpoint.Factory.<SocketAddress>create(
                            new InetSocketAddress("localhost", PORT + 1),
                            transport);

            final Connection[] e1Connections = new Connection[maxConnectionsPerEndpoint];
            final Connection[] e2Connections = new Connection[maxConnectionsPerEndpoint];
            
            for (int i = 0; i < maxConnectionsPerEndpoint; i++) {
                e1Connections[i] = pool.take(key1).get();
                assertNotNull(e1Connections[i]);
                assertEquals((i * 2) + 1, pool.size());
                
                e2Connections[i] = pool.take(key2).get();
                assertNotNull(e2Connections[i]);
                assertEquals((i * 2) + 2, pool.size());
            }
            
            final int numberOfReleasedConnections = maxConnectionsPerEndpoint / 2;
            for (int i = 0; i < numberOfReleasedConnections; i++) {
                pool.release(e1Connections[i]);
                assertNotNull(e1Connections[i]);
            }
            
            pool.close(key1);
            assertEquals(maxConnectionsPerEndpoint, pool.size());
            
            for (int i = 0; i < numberOfReleasedConnections; i++) {
                assertFalse(e1Connections[i].isOpen());
            }
            
            for (int i = numberOfReleasedConnections; i < maxConnectionsPerEndpoint; i++) {
                assertTrue(e1Connections[i].isOpen());
            }
            
            for (int i = numberOfReleasedConnections; i < maxConnectionsPerEndpoint; i++) {
                pool.release(e1Connections[i]);
            }
            
            for (int i = numberOfReleasedConnections; i < maxConnectionsPerEndpoint; i++) {
                assertFalse(e1Connections[i].isOpen());
            }
        } finally {
            pool.close();
        }
    }
    
    @Test
    public void testEmbeddedPollTimeout() throws Exception {
        final int maxConnectionsPerEndpoint = 2;

        final MultiEndpointPool<SocketAddress> pool = MultiEndpointPool
                .builder(SocketAddress.class)
                .maxConnectionsPerEndpoint(maxConnectionsPerEndpoint)
                .maxConnectionsTotal(maxConnectionsPerEndpoint * 2)
                .keepAliveTimeout(-1, TimeUnit.SECONDS)
                .asyncPollTimeout(2, TimeUnit.SECONDS)
                .build();

        try {
            final Endpoint<SocketAddress> key =
                    Endpoint.Factory.<SocketAddress>create(
                            new InetSocketAddress("localhost", PORT),
                            transport);

            Connection c1 = pool.take(key).get();
            assertNotNull(c1);
            assertEquals(1, pool.size());

            Connection c2 = pool.take(key).get();
            assertNotNull(c2);
            assertEquals(2, pool.size());

            final GrizzlyFuture<Connection> c3Future = pool.take(key);
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

            Connection c3 = pool.take(key).get(2, TimeUnit.SECONDS);
            assertNotNull(c3);
            assertEquals(2, pool.size());
        } finally {
            pool.close();
        }
    }
    
    @Test
    public void testEndpointPoolCustomizer() throws Exception {
        final Endpoint<SocketAddress> key1
                = Endpoint.Factory.<SocketAddress>create(
                        new InetSocketAddress("localhost", PORT),
                        transport);

        final Endpoint<SocketAddress> key2
                = Endpoint.Factory.<SocketAddress>create(
                        new InetSocketAddress("localhost", PORT + 1),
                        transport);

        final int maxConnectionsPerEndpoint = 2;

        final MultiEndpointPool<SocketAddress> pool = MultiEndpointPool
                .builder(SocketAddress.class)
                .maxConnectionsPerEndpoint(maxConnectionsPerEndpoint)
                .maxConnectionsTotal(maxConnectionsPerEndpoint * 2)
                .keepAliveTimeout(-1, TimeUnit.SECONDS)
                .endpointPoolCustomizer(new MultiEndpointPool.EndpointPoolCustomizer<SocketAddress>() {

                    @Override
                    public void customize(final Endpoint<SocketAddress> endpoint,
                            MultiEndpointPool.EndpointPoolBuilder<SocketAddress> builder) {
                        if (endpoint.equals(key1)) {
                            builder.keepAliveTimeout(0, TimeUnit.SECONDS); // no pooling
                        }
                    }
                })
                .build();

        try {
            Connection c1 = pool.take(key1).get();
            assertNotNull(c1);
            assertEquals(1, pool.size());

            Connection c2 = pool.take(key2).get();
            assertNotNull(c2);
            assertEquals(2, pool.size());

            pool.release(c2);
            assertEquals(2, pool.size());
            assertEquals(2, pool.getOpenConnectionsCount());

            // c1 should be closed immediately
            pool.release(c1);
            assertEquals(1, pool.size());
            assertEquals(1, pool.getOpenConnectionsCount());
        } finally {
            pool.close();
        }
    }    
}
