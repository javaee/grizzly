/*
 *
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2007-2010 Sun Microsystems, Inc. All rights reserved.
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
 *
 */

package com.sun.grizzly;

import com.sun.grizzly.attributes.AttributeStorage;
import com.sun.grizzly.filterchain.AbstractCodecFilter;
import com.sun.grizzly.filterchain.Filter;
import com.sun.grizzly.filterchain.BaseFilter;
import com.sun.grizzly.filterchain.FilterChain;
import com.sun.grizzly.filterchain.FilterChainBuilder;
import com.sun.grizzly.filterchain.FilterChainContext;
import com.sun.grizzly.filterchain.NextAction;
import com.sun.grizzly.filterchain.TransportFilter;
import com.sun.grizzly.nio.transport.TCPNIOConnection;
import com.sun.grizzly.nio.transport.TCPNIOTransport;
import com.sun.grizzly.utils.ChunkingFilter;
import com.sun.grizzly.utils.LinkedTransferQueue;
import com.sun.grizzly.utils.StringFilter;
import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Alexey Stashok
 */
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
                new AbstractCodecFilter(new DelayTransformer(1000),
                new DelayTransformer(20)) {},
                new ChunkingFilter(1));
    }

    public void testAsyncDelayedSingleChunkedStringEcho() throws Exception {
        logger.info("This test execution may take several seconds");
        doTestStringEcho(false, 1,
                new AbstractCodecFilter(new DelayTransformer(1000),
                new DelayTransformer(20)){},
                new ChunkingFilter(1));
    }

    public void testSyncDelayed5ChunkedStringEcho() throws Exception {
        logger.info("This test execution may take several seconds");
        doTestStringEcho(true, 5,
                new AbstractCodecFilter(new DelayTransformer(1000),
                new DelayTransformer(20)) {},
                new ChunkingFilter(1));
    }

    public void testAsyncDelayed5ChunkedStringEcho() throws Exception {
        logger.info("This test execution may take several seconds");
        doTestStringEcho(false, 5,
                new AbstractCodecFilter(new DelayTransformer(1000),
                new DelayTransformer(20)) {},
                new ChunkingFilter(1));
    }

    protected final void doTestStringEcho(boolean blocking,
            int messageNum, Filter... filters) throws Exception {
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

                final String message = (String) ctx.getMessage();

                logger.log(Level.FINE, "Server got message: " + message);

                assertEquals(clientMessage + "-" + counter, message);

                ctx.write(serverMessage + "-" + counter++);
                return ctx.getStopAction();
            }
        });

        
        TCPNIOTransport transport = TransportFactory.getInstance().createTCPTransport();
        transport.setProcessor(filterChainBuilder.build());

        try {
            transport.bind(PORT);
            transport.start();

            final BlockingQueue<String> resultQueue = new LinkedTransferQueue<String>();
            
            Future<Connection> future = transport.connect("localhost", PORT);
            connection = (TCPNIOConnection) future.get(10, TimeUnit.SECONDS);
            assertTrue(connection != null);

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

            connection.setProcessor(clientFilterChain);

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
                connection.close();
            }

            transport.stop();
            TransportFactory.getInstance().close();
        }
    }

    private static class DelayTransformer extends AbstractTransformer {
        private final long timeoutMillis;

        public DelayTransformer(long timeoutMillis) {
            this.timeoutMillis = timeoutMillis;
        }

        @Override
        public String getName() {
            return "DelayTransformer-" + hashCode();
        }

        @Override
        protected TransformationResult transformImpl(AttributeStorage storage,
                Object input) throws TransformationException {
            try {
                Thread.sleep(timeoutMillis);
            } catch (Exception e) {
            }
            
            return TransformationResult.createCompletedResult(input, null);
        }

        @Override
        public boolean hasInputRemaining(AttributeStorage storage, Object input) {
            return false;
        }
    }
}
