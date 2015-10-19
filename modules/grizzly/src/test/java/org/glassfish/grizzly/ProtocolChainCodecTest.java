/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2015 Oracle and/or its affiliates. All rights reserved.
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

import org.glassfish.grizzly.filterchain.Filter;
import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.FilterChain;
import org.glassfish.grizzly.filterchain.FilterChainBuilder;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.filterchain.TransportFilter;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import org.glassfish.grizzly.nio.transport.TCPNIOTransportBuilder;
import org.glassfish.grizzly.utils.ChunkingFilter;
import org.glassfish.grizzly.utils.DelayFilter;
import org.glassfish.grizzly.utils.StringFilter;
import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.grizzly.nio.transport.TCPNIOConnectorHandler;
import org.glassfish.grizzly.utils.DataStructures;

/**
 *
 * @author Alexey Stashok
 */
@SuppressWarnings("unchecked")
public class ProtocolChainCodecTest extends GrizzlyTestCase {
    private static final Logger logger = Grizzly.logger(ProtocolChainCodecTest.class);
    public static final int PORT = 7784;
    
    public void testSyncSingleStringEcho() throws Exception {
        doTestStringEcho(true, 1);
    }

    public void testAsyncSingleStringEcho() throws Exception {
        doTestStringEcho(false, 1);
    }

    public void testSync20StringEcho() throws Exception {
        doTestStringEcho(true, 20);
    }

    public void testAsync20SingleStringEcho() throws Exception {
        doTestStringEcho(false, 20);
    }

    public void testSyncSingleChunkedStringEcho() throws Exception {
        doTestStringEcho(true, 1, new ChunkingFilter(1));
    }

    public void testAsyncSingleChunkedStringEcho() throws Exception {
        doTestStringEcho(false, 1, new ChunkingFilter(1));
    }

    public void testSync20ChunkedStringEcho() throws Exception {
        doTestStringEcho(true, 20, new ChunkingFilter(1));
    }

    public void testAsync20ChunkedStringEcho() throws Exception {
        doTestStringEcho(false, 20, new ChunkingFilter(1));
    }

    public void testSyncDelayedSingleChunkedStringEcho() throws Exception {
        logger.info("This test execution may take several seconds");
        doTestStringEcho(true, 1,
                new DelayFilter(1000, 20),
                new ChunkingFilter(1));
    }

    public void testAsyncDelayedSingleChunkedStringEcho() throws Exception {
        logger.info("This test execution may take several seconds");
        doTestStringEcho(false, 1,
                new DelayFilter(1000, 20),
                new ChunkingFilter(1));
    }

    public void testSyncDelayed5ChunkedStringEcho() throws Exception {
        logger.info("This test execution may take several seconds");
        doTestStringEcho(true, 5,
                new DelayFilter(1000, 20),
                new ChunkingFilter(1));
    }

    public void testAsyncDelayed5ChunkedStringEcho() throws Exception {
        logger.info("This test execution may take several seconds");
        doTestStringEcho(false, 5,
                new DelayFilter(1000, 20),
                new ChunkingFilter(1));
    }

    protected final void doTestStringEcho(boolean blocking,
                                          int messageNum,
                                          Filter... filters) throws Exception {
        Connection connection = null;

        final String clientMessage = "Hello server! It's a client";
        final String serverMessage = "Hello client! It's a server";

        FilterChainBuilder filterChainBuilder = FilterChainBuilder.stateless();
        filterChainBuilder.add(new TransportFilter());
        for (Filter filter : filters) {
            filterChainBuilder.add(filter);
        }
        filterChainBuilder.add(new StringFilter());
        filterChainBuilder.add(new BaseFilter() {
            volatile int counter;
            @Override
            public NextAction handleRead(FilterChainContext ctx)
                    throws IOException {

                final String message = ctx.getMessage();

                logger.log(Level.FINE, "Server got message: " + message);

                assertEquals(clientMessage + "-" + counter, message);

                ctx.write(serverMessage + "-" + counter++);
                return ctx.getStopAction();
            }
        });

        
        TCPNIOTransport transport = TCPNIOTransportBuilder.newInstance().build();
        transport.setProcessor(filterChainBuilder.build());

        try {
            transport.bind(PORT);
            transport.start();

            final BlockingQueue<String> resultQueue = DataStructures.getLTQInstance(String.class);
            
            FilterChainBuilder clientFilterChainBuilder =
                    FilterChainBuilder.stateless();
            clientFilterChainBuilder.add(new TransportFilter());
            clientFilterChainBuilder.add(new StringFilter());
            clientFilterChainBuilder.add(new BaseFilter() {

                @Override
                public NextAction handleRead(FilterChainContext ctx) throws IOException {
                    resultQueue.add((String) ctx.getMessage());
                    return ctx.getStopAction();
                }

            });
            final FilterChain clientFilterChain = clientFilterChainBuilder.build();

            SocketConnectorHandler connectorHandler =
                    TCPNIOConnectorHandler.builder(transport)
                    .processor(clientFilterChain)
                    .build();
            
            Future<Connection> future = connectorHandler.connect("localhost", PORT);
            connection = future.get(10, TimeUnit.SECONDS);
            assertTrue(connection != null);

            for (int i = 0; i < messageNum; i++) {
                Future<WriteResult> writeFuture = connection.write(
                        clientMessage + "-" + i);

                assertTrue("Write timeout loop: " + i,
                        writeFuture.get(10, TimeUnit.SECONDS) != null);


                final String message = resultQueue.poll(10, TimeUnit.SECONDS);

                assertEquals("Unexpected response (" + i + ")",
                        serverMessage + "-" + i, message);
            }
        } finally {
            if (connection != null) {
                connection.closeSilently();
            }

            transport.shutdownNow();
        }
    }
}
