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

package org.glassfish.grizzly.http;

import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.WriteResult;
import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.FilterChain;
import org.glassfish.grizzly.filterchain.FilterChainBuilder;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.filterchain.TransportFilter;
import org.glassfish.grizzly.http.util.HttpStatus;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import org.glassfish.grizzly.nio.transport.TCPNIOTransportBuilder;
import org.glassfish.grizzly.utils.ChunkingFilter;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import junit.framework.TestCase;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.SocketConnectorHandler;
import org.glassfish.grizzly.memory.Buffers;
import org.glassfish.grizzly.memory.MemoryManager;
import org.glassfish.grizzly.nio.transport.TCPNIOConnectorHandler;
import org.glassfish.grizzly.utils.DataStructures;

/**
 * Test HTTP communication
 * 
 * @author Alexey Stashok
 */
public class HttpCommTest extends TestCase {

    private static final Logger logger = Grizzly.logger(HttpCommTest.class);

    public static final int PORT = 19002;

    @SuppressWarnings("unchecked")
    public void testSinglePacket() throws Exception {
        final FilterChain serverFilterChain = FilterChainBuilder.stateless()
                .add(new TransportFilter())
                .add(new ChunkingFilter(2))
                .add(new HttpServerFilter())
                .add(new DummyServerFilter())
                .build();

        TCPNIOTransport transport = TCPNIOTransportBuilder.newInstance().build();
        transport.setProcessor(serverFilterChain);

        Connection connection = null;
        try {
            transport.bind(PORT);
            transport.start();
            
            final BlockingQueue<HttpPacket> resultQueue = DataStructures.getLTQInstance(HttpPacket.class);

            final FilterChain clientFilterChain = FilterChainBuilder.stateless()
                    .add(new TransportFilter())
                    .add(new ChunkingFilter(2))
                    .add(new HttpClientFilter())
                    .add(new BaseFilter() {
                @Override
                public NextAction handleRead(FilterChainContext ctx) throws IOException {
                    resultQueue.add((HttpPacket) ctx.getMessage());
                    return ctx.getStopAction();
                }
            }).build();
            
            final SocketConnectorHandler connectorHandler =
                    TCPNIOConnectorHandler.builder(transport)
                    .processor(clientFilterChain)
                    .build();
            
            
            Future<Connection> future = connectorHandler.connect("localhost", PORT);
            connection = future.get(10, TimeUnit.SECONDS);
            int clientPort = ((InetSocketAddress) connection.getLocalAddress()).getPort();
            assertNotNull(connection);

            HttpRequestPacket httpRequest = HttpRequestPacket.builder().method("GET").
                    uri("/dummyURL").query("p1=v1&p2=v2").protocol(Protocol.HTTP_1_0).
                    header("client-port",  Integer.toString(clientPort)).
                    header("Host", "localhost").build();

            Future<WriteResult> writeResultFuture = connection.write(httpRequest);
            writeResultFuture.get(10, TimeUnit.SECONDS);

            HttpContent response = (HttpContent) resultQueue.poll(10, TimeUnit.SECONDS);            
            HttpResponsePacket responseHeader = (HttpResponsePacket) response.getHttpHeader();

            assertEquals(httpRequest.getRequestURI(), responseHeader.getHeader("Found"));
            
        } finally {
            if (connection != null) {
                connection.closeSilently();
            }

            transport.shutdownNow();
        }
    }


    public static class DummyServerFilter extends BaseFilter {

        @Override
        public NextAction handleRead(FilterChainContext ctx)
                throws IOException {

            final HttpContent httpContent = ctx.getMessage();
            final HttpRequestPacket request = (HttpRequestPacket) httpContent.getHttpHeader();

            logger.log(Level.FINE, "Got the request: {0}", request);

            assertEquals(PORT, request.getLocalPort());
            assertTrue(isLocalAddress(request.getLocalAddress()));
            assertTrue(isLocalAddress(request.getRemoteHost()));
            assertTrue(isLocalAddress(request.getRemoteAddress()));
            assertEquals(request.getHeader("client-port"),
                         Integer.toString(request.getRemotePort()));

            HttpResponsePacket response = request.getResponse();
            HttpStatus.OK_200.setValues(response);

            response.addHeader("Content-Length", "0");
            
            // Set header using headers collection (just for testing reasons)            
            final String junk = "---junk---";
            final Buffer foundBuffer = 
                    Buffers.wrap(MemoryManager.DEFAULT_MEMORY_MANAGER, junk + "Found");
            response.getHeaders()
                    .addValue(foundBuffer, junk.length(), foundBuffer.remaining() - junk.length())
                    .setString(request.getRequestURI());
//            response.addHeader("Found", request.getRequestURI());
            
            ctx.write(response);

            return ctx.getStopAction();
        }
    }

    private static boolean isLocalAddress(String address) throws IOException {
        final InetAddress inetAddr = InetAddress.getByName(address);
        
        Enumeration<NetworkInterface> e = NetworkInterface.getNetworkInterfaces();
        while(e.hasMoreElements()) {
            NetworkInterface ni = e.nextElement();
            Enumeration<InetAddress> inetAddrs = ni.getInetAddresses();
            while(inetAddrs.hasMoreElements()) {
                InetAddress addr = inetAddrs.nextElement();
                if (addr.equals(inetAddr)) {
                    return true;
                }
            }
        }

        return false;
    }
}
