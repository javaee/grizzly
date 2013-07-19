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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.FilterChainBuilder;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.filterchain.TransportFilter;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import org.glassfish.grizzly.nio.transport.TCPNIOTransportBuilder;
import org.glassfish.grizzly.utils.DelayedExecutor;
import org.glassfish.grizzly.utils.IdleTimeoutFilter;

/**
 * Test {@link IdleTimeoutFilter}
 * 
 * @author Alexey Stashok
 */
public class IdleConnectionFilterTest extends GrizzlyTestCase {
    public static final int PORT = 7782;

    public void testAcceptedConnectionIdleTimeout() throws Exception {
        Connection connection = null;

        final CountDownLatch latch = new CountDownLatch(1);
        final DelayedExecutor timeoutExecutor = IdleTimeoutFilter.createDefaultIdleDelayedExecutor();
        timeoutExecutor.start();
        IdleTimeoutFilter idleTimeoutFilter =
                new IdleTimeoutFilter(timeoutExecutor, 2, TimeUnit.SECONDS);

        FilterChainBuilder filterChainBuilder = FilterChainBuilder.stateless();
        filterChainBuilder.add(new TransportFilter());
        filterChainBuilder.add(idleTimeoutFilter);
        filterChainBuilder.add(new BaseFilter() {
                private volatile Connection acceptedConnection;
                @Override
                public NextAction handleAccept(FilterChainContext ctx)
                        throws IOException {
                    acceptedConnection = ctx.getConnection();
                    return ctx.getInvokeAction();
                }

                @Override
                public NextAction handleClose(FilterChainContext ctx)
                        throws IOException {
                    if (ctx.getConnection().equals(acceptedConnection)) {
                        latch.countDown();
                    }

                    return ctx.getInvokeAction();
                }

            });

        TCPNIOTransport transport = TCPNIOTransportBuilder.newInstance().build();
        transport.setProcessor(filterChainBuilder.build());
        
        try {
            transport.bind(PORT);
            transport.start();

            Future<Connection> future = transport.connect("localhost", PORT);
            connection = future.get(10, TimeUnit.SECONDS);
            assertTrue(connection != null);

            assertTrue(latch.await(10, TimeUnit.SECONDS));
        } finally {
            if (connection != null) {
                connection.closeSilently();
            }
            timeoutExecutor.stop();
            timeoutExecutor.destroy();
            transport.shutdownNow();
        }
    }

    public void testConnectedConnectionIdleTimeout() throws Exception {
        Connection connection = null;
        final CountDownLatch latch = new CountDownLatch(1);

        final DelayedExecutor timeoutExecutor = IdleTimeoutFilter.createDefaultIdleDelayedExecutor();
        timeoutExecutor.start();
        IdleTimeoutFilter idleTimeoutFilter =
                new IdleTimeoutFilter(timeoutExecutor, 2, TimeUnit.SECONDS);

        FilterChainBuilder filterChainBuilder = FilterChainBuilder.stateless();
        filterChainBuilder.add(new TransportFilter());
        filterChainBuilder.add(idleTimeoutFilter);
        filterChainBuilder.add(new BaseFilter() {
            private volatile Connection connectedConnection;

            @Override
            public NextAction handleConnect(FilterChainContext ctx)
                    throws IOException {
                connectedConnection = ctx.getConnection();
                return ctx.getInvokeAction();
            }

            @Override
            public NextAction handleClose(FilterChainContext ctx)
                    throws IOException {
                if (ctx.getConnection().equals(connectedConnection)) {
                    latch.countDown();
                }

                return ctx.getInvokeAction();
            }
        });
        
        TCPNIOTransport transport = TCPNIOTransportBuilder.newInstance().build();
        transport.setProcessor(filterChainBuilder.build());
        
        try {
            transport.bind(PORT);

            transport.start();

            Future<Connection> future = transport.connect("localhost", PORT);
            connection = future.get(10, TimeUnit.SECONDS);
            assertTrue(connection != null);

            assertTrue(latch.await(10, TimeUnit.SECONDS));
        } finally {
            if (connection != null) {
                connection.closeSilently();
            }
            timeoutExecutor.stop();
            timeoutExecutor.destroy();
            transport.shutdownNow();
        }
    }
    
    public void testInfiniteIdleTimeout() throws Exception {
        Connection connection = null;

        final CountDownLatch latch = new CountDownLatch(1);
        final DelayedExecutor timeoutExecutor = IdleTimeoutFilter.createDefaultIdleDelayedExecutor();
        timeoutExecutor.start();
        IdleTimeoutFilter idleTimeoutFilter =
                new IdleTimeoutFilter(timeoutExecutor, -1, TimeUnit.SECONDS);

        FilterChainBuilder filterChainBuilder = FilterChainBuilder.stateless();
        filterChainBuilder.add(new TransportFilter());
        filterChainBuilder.add(idleTimeoutFilter);
        filterChainBuilder.add(new BaseFilter() {
                private volatile Connection acceptedConnection;
                @Override
                public NextAction handleAccept(FilterChainContext ctx)
                        throws IOException {
                    acceptedConnection = ctx.getConnection();
                    return ctx.getInvokeAction();
                }

                @Override
                public NextAction handleClose(FilterChainContext ctx)
                        throws IOException {
                    if (ctx.getConnection().equals(acceptedConnection)) {
                        latch.countDown();
                    }

                    return ctx.getInvokeAction();
                }

            });

        TCPNIOTransport transport = TCPNIOTransportBuilder.newInstance().build();
        transport.setProcessor(filterChainBuilder.build());
        
        try {
            transport.bind(PORT);
            transport.start();

            Future<Connection> future = transport.connect("localhost", PORT);
            connection = future.get(10, TimeUnit.SECONDS);
            assertTrue(connection != null);

            assertFalse(latch.await(2, TimeUnit.SECONDS));
        } finally {
            if (connection != null) {
                connection.closeSilently();
            }
            timeoutExecutor.stop();
            timeoutExecutor.destroy();
            transport.shutdownNow();
        }
    }
}
