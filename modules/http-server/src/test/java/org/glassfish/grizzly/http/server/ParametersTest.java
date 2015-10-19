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

package org.glassfish.grizzly.http.server;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.GrizzlyFuture;
import org.glassfish.grizzly.filterchain.FilterChainBuilder;
import org.glassfish.grizzly.filterchain.TransportFilter;
import org.glassfish.grizzly.http.HttpClientFilter;
import org.glassfish.grizzly.http.HttpContent;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.http.Method;
import org.glassfish.grizzly.http.Protocol;
import org.glassfish.grizzly.http.util.Header;
import org.glassfish.grizzly.memory.Buffers;
import org.glassfish.grizzly.nio.transport.TCPNIOConnectorHandler;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import org.glassfish.grizzly.nio.transport.TCPNIOTransportBuilder;
import org.glassfish.grizzly.utils.ChunkingFilter;
import org.glassfish.grizzly.utils.DelayFilter;
import org.junit.Test;

import java.util.Random;
import java.util.concurrent.TimeUnit;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static org.glassfish.grizzly.http.server.NetworkListener.DEFAULT_NETWORK_HOST;
import org.glassfish.grizzly.impl.FutureImpl;
import org.glassfish.grizzly.utils.Charsets;
import org.glassfish.grizzly.utils.Futures;

@SuppressWarnings("unchecked")
public class ParametersTest {

    private static final int PORT = 8766;

    /**
     * Testcase for http://java.net/jira/browse/GRIZZLY-1438
     */
    @Test
    public void testPostBodyChunked() throws Exception {
        final HttpServer server = createServer();
        final String body = generatePostBody(1024 * 3);
        final String[][] paramParts = getParts(body);
        final FutureImpl<Boolean> resultFuture = Futures.createSafeFuture();
        server.getServerConfiguration().addHttpHandler(
                new HttpHandler() {
                    @Override
                    public void service(Request request, Response response) throws Exception {
                        for (int i = 0, len = paramParts.length; i < len; i++) {
                            final String value = request.getParameter(paramParts[i][0]);
                            try {
                                assertNotNull("value is null", value);
                                assertEquals(paramParts[i][1], value);
                                resultFuture.result(Boolean.TRUE);
                            } catch (Throwable t) {
                                resultFuture.failure(t);
                            }
                        }
                    }
                }
                , "/*");

        final TCPNIOTransport clientTransport =
                TCPNIOTransportBuilder.newInstance().build();
        try {
            FilterChainBuilder clientFilterChainBuilder = FilterChainBuilder.stateless();
            clientFilterChainBuilder.add(new TransportFilter());
            clientFilterChainBuilder.add(new DelayFilter(0, 500));
            clientFilterChainBuilder.add(new ChunkingFilter(256));
            clientFilterChainBuilder.add(new HttpClientFilter());
            clientTransport.setProcessor(clientFilterChainBuilder.build());
            clientTransport.start();

            server.start();
            TCPNIOConnectorHandler handler = TCPNIOConnectorHandler.builder(clientTransport).build();
            GrizzlyFuture<Connection> future = handler.connect("localhost", PORT);
            final Buffer bodyBuffer = Buffers.wrap(clientTransport.getMemoryManager(), body);
            HttpRequestPacket request = HttpRequestPacket.builder()
                    .chunked(true)
                    .method(Method.POST)
                    .uri("/")
                    .header(Header.Host, "localhost:" + PORT)
                    .contentType("application/x-www-form-urlencoded; charset=ISO-8859-1")
                    .protocol(Protocol.HTTP_1_1).build();
            HttpContent content = HttpContent.builder(request).content(bodyBuffer).last(true).build();
            Connection c = future.get(10, TimeUnit.SECONDS);
            c.write(content);
            resultFuture.get(10, TimeUnit.SECONDS);
        } finally {
            server.shutdownNow();
            clientTransport.shutdownNow();
        }

    }

    /**
     * Override charset encoding
     */
    @Test
    public void testOverrideCharsetEncoding() throws Exception {
        final HttpServer server = createServer();
        final String body = generatePostBody(1024 * 3);
        final String[][] paramParts = getParts(body);
        final FutureImpl<Boolean> resultFuture = Futures.createSafeFuture();
        server.getServerConfiguration().addHttpHandler(
                new HttpHandler() {
                    @Override
                    public void service(Request request, Response response) throws Exception {
                        // !!!! Override character encoding
                        request.setCharacterEncoding("UTF-8");
                        for (int i = 0, len = paramParts.length; i < len; i++) {
                            final String value = request.getParameter(paramParts[i][0]);
                            try {
                                assertEquals(request.getCharacterEncoding(), "UTF-8");
                                assertNotNull("value is null", value);
                                assertEquals(paramParts[i][1], value);
                                resultFuture.result(Boolean.TRUE);
                            } catch (Throwable t) {
                                resultFuture.failure(t);
                            }
                        }
                    }
                }
                , "/*");

        final TCPNIOTransport clientTransport =
                TCPNIOTransportBuilder.newInstance().build();
        try {
            FilterChainBuilder clientFilterChainBuilder = FilterChainBuilder.stateless();
            clientFilterChainBuilder.add(new TransportFilter());
            clientFilterChainBuilder.add(new HttpClientFilter());
            clientTransport.setProcessor(clientFilterChainBuilder.build());
            clientTransport.start();

            server.start();
            TCPNIOConnectorHandler handler = TCPNIOConnectorHandler.builder(clientTransport).build();
            GrizzlyFuture<Connection> future = handler.connect("localhost", PORT);
            final Buffer bodyBuffer = Buffers.wrap(clientTransport.getMemoryManager(), body);
            HttpRequestPacket request = HttpRequestPacket.builder()
                    .chunked(true)
                    .method(Method.POST)
                    .uri("/")
                    .header(Header.Host, "localhost:" + PORT)
                    .contentType("application/x-www-form-urlencoded; charset=ISO-8859-1")
                    .protocol(Protocol.HTTP_1_1).build();
            HttpContent content = HttpContent.builder(request).content(bodyBuffer).last(true).build();
            Connection c = future.get(10, TimeUnit.SECONDS);
            c.write(content);
            resultFuture.get(10, TimeUnit.SECONDS);
        } finally {
            server.shutdownNow();
            clientTransport.shutdownNow();
        }
    }
    
    /**
     * Test customized query string encoding
     * https://java.net/jira/browse/GRIZZLY-1794
     */
    @Test
    public void testQueryStringCharsetEncoding() throws Exception {
        final HttpServer server = createServer();
        final FutureImpl<Boolean> resultFuture = Futures.createSafeFuture();
        server.getServerConfiguration().setDefaultQueryEncoding(Charsets.UTF8_CHARSET);
        server.getServerConfiguration().addHttpHandler(
                new HttpHandler() {
                    @Override
                    public void service(Request request, Response response) throws Exception {
                        try {
                            assertEquals("msg=Hello\\World+With+SpecChars+§*)$!±@-_=;`:\\,~|", request.getQueryString());
                            resultFuture.result(Boolean.TRUE);
                        } catch (Throwable t) {
                            resultFuture.failure(t);
                        }
                    }
                }
                , "/*");

        Socket socket = null;
        try {
            server.start();
            
            // Low level approach with sockets is used, because common Java HTTP clients are using java.net.URI,
            // which fails when unencoded curly bracket is part of the URI
            socket = new Socket("localhost", PORT);
            final PrintWriter pw = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())));

            pw.println("GET /app/test?msg=Hello\\World+With+SpecChars+§*)$!±@-_=;`:\\,~| HTTP/1.0");
            pw.println(); // http request should end with a blank line
            pw.flush();

            final BufferedReader br = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            while (br.readLine() != null);
            pw.close();
            br.close();

            resultFuture.get(10, TimeUnit.SECONDS);

        } finally {
            server.shutdownNow();
            if (socket != null) {
                socket.close();
            }
        }
    }
    
    // -------------------------------------------------------- Private Methods

    private static HttpServer createServer() {
        HttpServer server = new HttpServer();
        NetworkListener l = new NetworkListener("test", DEFAULT_NETWORK_HOST, PORT);
        server.addListener(l);
        return server;
    }

    private static String[][] getParts(final String body) {
        String[] p = body.split("&");
        String[][] parts = new String[p.length][2];
        for (int i = 0, len = p.length; i < len; i++) {
            String[] sp = p[i].split("=");
            parts[i][0] = sp[0];
            parts[i][1] = sp[1];
        }
        return parts;
    }


    private static String generatePostBody(final int len) {
        Random r = new Random();
        StringBuilder sb = new StringBuilder();
        boolean prepend = false;
        while (sb.length() < len) {
            if (!prepend) {
                prepend = true;
            } else {
                sb.append('&');
            }
            sb.append(r.nextLong()).append('=').append(r.nextLong());
        }
        return sb.toString();
    }
}
