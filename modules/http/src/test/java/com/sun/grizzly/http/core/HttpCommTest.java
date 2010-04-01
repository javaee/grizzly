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
package com.sun.grizzly.http.core;

import com.sun.grizzly.http.HttpContent;
import com.sun.grizzly.http.HttpResponse;
import com.sun.grizzly.http.HttpRequest;
import com.sun.grizzly.Connection;
import com.sun.grizzly.Grizzly;
import com.sun.grizzly.ReadResult;
import com.sun.grizzly.TransportFactory;
import com.sun.grizzly.WriteResult;
import com.sun.grizzly.filterchain.BaseFilter;
import com.sun.grizzly.filterchain.DefaultFilterChain;
import com.sun.grizzly.filterchain.FilterChain;
import com.sun.grizzly.filterchain.FilterChainBuilder;
import com.sun.grizzly.filterchain.FilterChainContext;
import com.sun.grizzly.filterchain.NextAction;
import com.sun.grizzly.filterchain.TransportFilter;
import com.sun.grizzly.http.HttpClientFilter;
import com.sun.grizzly.http.HttpServerFilter;
import com.sun.grizzly.nio.transport.TCPNIOTransport;
import com.sun.grizzly.utils.ChunkingFilter;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.util.Enumeration;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import junit.framework.TestCase;

/**
 * Test HTTP communication
 * 
 * @author Alexey Stashok
 */
public class HttpCommTest extends TestCase {

    private static final Logger logger = Grizzly.logger(HttpCommTest.class);

    public static int PORT = 8002;

    public void testSinglePacket() throws Exception {
        FilterChainBuilder serverFilterChainBuilder = FilterChainBuilder.stateless();
        serverFilterChainBuilder.add(new TransportFilter());
        serverFilterChainBuilder.add(new ChunkingFilter(2));
        serverFilterChainBuilder.add(new HttpServerFilter());
        serverFilterChainBuilder.add(new DummyServerFilter());

        TCPNIOTransport transport = TransportFactory.getInstance().createTCPTransport();
        transport.setProcessor(serverFilterChainBuilder.build());

        Connection connection = null;
        try {
            transport.bind(PORT);
            transport.start();

            Future<Connection> future = transport.connect("localhost", PORT);
            connection = future.get(10, TimeUnit.SECONDS);
            int clientPort = ((InetSocketAddress) connection.getLocalAddress()).getPort();
            assertTrue(connection != null);

            FilterChainBuilder clientFilterChainBuilder = FilterChainBuilder.stateless();
            clientFilterChainBuilder.add(new TransportFilter());
            clientFilterChainBuilder.add(new ChunkingFilter(2));
            clientFilterChainBuilder.add(new HttpClientFilter());
            FilterChain clientFilterChain = clientFilterChainBuilder.build();
            ((DefaultFilterChain) clientFilterChain).awareOfStandaloneRead(true);
            connection.setProcessor(clientFilterChain);

            HttpRequest httpRequest = HttpRequest.builder().method("GET").
                    uri("/dummyURL").query("p1=v1&p2=v2").protocol("HTTP/1.0").
                    header("client-port",  Integer.toString(clientPort)).
                    header("Host", "localhost").build();

            Future<WriteResult> writeResultFuture = connection.write(httpRequest);
            writeResultFuture.get(10, TimeUnit.SECONDS);

            Future<ReadResult> readResultFuture = connection.read();
            ReadResult<HttpContent, SocketAddress> readResult =
                    readResultFuture.get(1000, TimeUnit.SECONDS);

            HttpContent response = readResult.getMessage();
            HttpResponse responseHeader = (HttpResponse) response.getHttpHeader();

            assertEquals(httpRequest.getRequestURI(), responseHeader.getHeader("Found"));
            
        } finally {
            if (connection != null) {
                connection.close();
            }

            transport.stop();
            TransportFactory.getInstance().close();
        }
    }


    public static class DummyServerFilter extends BaseFilter {

        @Override
        public NextAction handleRead(FilterChainContext ctx)
                throws IOException {

            final HttpContent httpContent = (HttpContent) ctx.getMessage();
            final HttpRequest request = (HttpRequest) httpContent.getHttpHeader();

            System.out.println("Got the request: " + request);

            assertEquals(PORT, request.getLocalPort());
            assertTrue(isLocalAddress(request.getLocalAddress()));
            assertTrue(isLocalAddress(request.getRemoteHost()));
            assertTrue(isLocalAddress(request.getRemoteAddress()));
            assertEquals(request.getHeader("client-port"), 
                         Integer.toString(request.getRemotePort()));
            assertEquals("v1", request.getParameters().getParameter("p1"));
            assertEquals("v2", request.getParameters().getParameter("p2"));

            final HttpResponse response = HttpResponse.builder().
                    protocol(request.getProtocol()).status(200).
                    reasonPhrase("OK").header("Content-Length", "0").
                    header("Found", request.getRequestURI()).build();
            
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
