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

import org.glassfish.grizzly.asyncqueue.WritableMessage;
import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.FilterChain;
import org.glassfish.grizzly.filterchain.FilterChainBuilder;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.filterchain.TransportFilter;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import org.glassfish.grizzly.nio.transport.TCPNIOTransportBuilder;
import org.glassfish.grizzly.utils.EchoFilter;
import org.glassfish.grizzly.utils.StringEncoder;
import org.glassfish.grizzly.utils.StringFilter;
import java.io.EOFException;
import java.io.IOException;
import java.net.SocketAddress;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import junit.framework.TestCase;
import org.glassfish.grizzly.memory.CompositeBuffer;
import org.glassfish.grizzly.nio.transport.TCPNIOConnectorHandler;
import org.glassfish.grizzly.utils.DataStructures;

/**
 * Test {@link FilterChain} blocking read.
 * 
 * @author Alexey Stashok
 */
@SuppressWarnings("unchecked")
public class FilterChainReadTest extends TestCase {
    public static final int PORT = 7785;

    private static final Logger logger = Grizzly.logger(FilterChainReadTest.class);

    public void testBlockingRead() throws Exception {
        final String[] clientMsgs = {"Hello", "from", "client"};
        
        Connection connection = null;
        int messageNum = 3;

        final BlockingQueue<String> intermResultQueue = DataStructures.getLTQInstance(String.class);
        FilterChainBuilder filterChainBuilder = FilterChainBuilder.stateless();
        filterChainBuilder.add(new TransportFilter());
        filterChainBuilder.add(new StringFilter());
        filterChainBuilder.add(new BaseFilter() {
            @Override
            public NextAction handleRead(FilterChainContext ctx)
                    throws IOException {

                String message = ctx.getMessage();

                logger.log(Level.INFO, "First chunk come: {0}", message);
                intermResultQueue.add(message);

                Connection connection = ctx.getConnection();
                connection.setReadTimeout(10, TimeUnit.SECONDS);

                for (int i = 0; i < clientMsgs.length - 1; i++) {
                    final ReadResult rr = ctx.read();
                    final String blckMsg = (String) rr.getMessage();

                    rr.recycle();
                    logger.log(Level.INFO, "Blocking chunk come: {0}", blckMsg);
                    intermResultQueue.add(blckMsg);
                    message += blckMsg;
                }

                ctx.setMessage(message);

                return ctx.getInvokeAction();
            }
        });
        filterChainBuilder.add(new EchoFilter());


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
                String clientMessage = "";

                for (int j = 0; j < clientMsgs.length; j++) {
                    String msg = clientMsgs[j] + "-" + i;
                    Future<WriteResult> writeFuture = connection.write(msg);

                    assertTrue("Write timeout loop: " + i,
                            writeFuture.get(10, TimeUnit.SECONDS) != null);

                    final String srvInterm = intermResultQueue.poll(10, TimeUnit.SECONDS);

                    assertEquals("Unexpected interm. response (" + i + ", " + j + ")", msg, srvInterm);

                    clientMessage += msg;
                }


                final String message = resultQueue.poll(10, TimeUnit.SECONDS);

                assertEquals("Unexpected response (" + i + ")",
                        clientMessage, message);
            }
        } finally {
            if (connection != null) {
                connection.closeSilently();
            }

            transport.shutdownNow();
        }
    }

    public void testBlockingReadWithRemainder() throws Exception {
        final String[] clientMsgs = {"Hello", "from", "client"};

        Connection connection = null;
        int messageNum = 3;

        final BlockingQueue<String> intermResultQueue = DataStructures.getLTQInstance(String.class);
        FilterChainBuilder filterChainBuilder = FilterChainBuilder.stateless();
        filterChainBuilder.add(new TransportFilter());
        filterChainBuilder.add(new StringFilter());
        filterChainBuilder.add(new BaseFilter() {
            @Override
            public NextAction handleRead(FilterChainContext ctx)
                    throws IOException {

                String message = ctx.getMessage();

                logger.log(Level.INFO, "First chunk come: {0}", message);
                intermResultQueue.add(message);

                Connection connection = ctx.getConnection();
                connection.setReadTimeout(10, TimeUnit.SECONDS);

                for (int i = 0; i < clientMsgs.length - 1; i++) {
                    final ReadResult rr = ctx.read();
                    final String blckMsg = (String) rr.getMessage();

                    rr.recycle();
                    logger.log(Level.INFO, "Blocking chunk come: {0}", blckMsg);
                    intermResultQueue.add(blckMsg);
                    message += blckMsg;
                }

                ctx.setMessage(message);

                return ctx.getInvokeAction();
            }
        });
        filterChainBuilder.add(new EchoFilter());


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
                String clientMessage = "";

                CompositeBuffer bb = CompositeBuffer.newBuffer(transport.getMemoryManager());
                
                for (int j = 0; j < clientMsgs.length; j++) {
                    String msg = clientMsgs[j] + "-" + i;
                    clientMessage += msg;
                    StringEncoder stringEncoder = new StringEncoder();
                    TransformationResult<String, Buffer> result =
                            stringEncoder.transform(connection, msg);
                    Buffer buffer = result.getMessage();
                    bb.append(buffer);
                }


                Future<WriteResult<WritableMessage, SocketAddress>> writeFuture =
                        transport.getAsyncQueueIO().getWriter().write(connection, bb);

                assertTrue("Write timeout loop: " + i,
                        writeFuture.get(10, TimeUnit.SECONDS) != null);

                for (int j = 0; j < clientMsgs.length; j++) {
                    String msg = clientMsgs[j] + "-" + i;
                    final String srvInterm = intermResultQueue.poll(10, TimeUnit.SECONDS);

                    assertEquals("Unexpected interm. response (" + i + ", " + j + ")", msg, srvInterm);
                }


                final String message = resultQueue.poll(10, TimeUnit.SECONDS);

                assertEquals("Unexpected response (" + i + ")",
                        clientMessage, message);
            }
        } finally {
            if (connection != null) {
                connection.closeSilently();
            }

            transport.shutdownNow();
        }
    }

    public void testBlockingReadError() throws Exception {
        Connection connection = null;

        final BlockingQueue<Object> intermResultQueue = DataStructures.getLTQInstance(Object.class);
        FilterChainBuilder filterChainBuilder = FilterChainBuilder.stateless();
        filterChainBuilder.add(new TransportFilter());
        filterChainBuilder.add(new StringFilter());
        filterChainBuilder.add(new BaseFilter() {
            @Override
            public NextAction handleRead(FilterChainContext ctx)
                    throws IOException {

                String message = ctx.getMessage();

                logger.log(Level.INFO, "First chunk come: {0}", message);
                intermResultQueue.add(message);

                Connection connection = ctx.getConnection();
                connection.setReadTimeout(10, TimeUnit.SECONDS);

                try {
                    final ReadResult rr = ctx.read();
                    intermResultQueue.add(rr);
                } catch (Exception e) {
                    intermResultQueue.add(e);
                }

                return ctx.getStopAction();
            }
        });


        TCPNIOTransport transport = TCPNIOTransportBuilder.newInstance().build();
        transport.setProcessor(filterChainBuilder.build());

        try {
            transport.bind(PORT);
            transport.start();

            FilterChainBuilder clientFilterChainBuilder =
                    FilterChainBuilder.stateless();
            clientFilterChainBuilder.add(new TransportFilter());
            clientFilterChainBuilder.add(new StringFilter());
            final FilterChain clientFilterChain = clientFilterChainBuilder.build();

            SocketConnectorHandler connectorHandler =
                    TCPNIOConnectorHandler.builder(transport)
                    .processor(clientFilterChain)
                    .build();
            
            Future<Connection> future = connectorHandler.connect("localhost", PORT);
            connection = future.get(10, TimeUnit.SECONDS);
            assertTrue(connection != null);

            String msg = "Hello";
            Future<WriteResult> writeFuture = connection.write(msg);

            assertTrue("Write timeout",
                    writeFuture.get(10, TimeUnit.SECONDS) != null);

            final String srvInterm = (String) intermResultQueue.poll(10, TimeUnit.SECONDS);

            assertEquals("Unexpected interm. response", msg, srvInterm);

            connection.closeSilently();
            connection = null;
            
            final Exception e = (Exception) intermResultQueue.poll(10, TimeUnit.SECONDS);

            assertTrue("Unexpected response. Exception: " + e.getClass() + ": " + e.getMessage(),
                    e instanceof EOFException);
        } finally {
            if (connection != null) {
                connection.closeSilently();
            }

            transport.shutdownNow();
        }
    }
}
