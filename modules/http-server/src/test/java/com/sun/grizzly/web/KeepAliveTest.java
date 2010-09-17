/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.grizzly.web;

import com.sun.grizzly.Buffer;
import com.sun.grizzly.Connection;
import com.sun.grizzly.EmptyCompletionHandler;
import com.sun.grizzly.SocketConnectorHandler;
import com.sun.grizzly.TransportFactory;
import com.sun.grizzly.filterchain.BaseFilter;
import com.sun.grizzly.filterchain.FilterChainBuilder;
import com.sun.grizzly.filterchain.FilterChainContext;
import com.sun.grizzly.filterchain.NextAction;
import com.sun.grizzly.filterchain.TransportFilter;
import com.sun.grizzly.http.HttpClientFilter;
import com.sun.grizzly.http.HttpContent;
import com.sun.grizzly.http.HttpRequestPacket;
import com.sun.grizzly.http.Protocol;
import com.sun.grizzly.http.server.*;
import com.sun.grizzly.http.server.HttpService;
import com.sun.grizzly.impl.FutureImpl;
import com.sun.grizzly.impl.SafeFutureImpl;
import com.sun.grizzly.nio.transport.TCPNIOConnectorHandler;
import com.sun.grizzly.nio.transport.TCPNIOTransport;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import junit.framework.TestCase;

/**
 * Testing HTTP keep-alive
 * 
 * @author Alexey Stashok
 */
public class KeepAliveTest extends TestCase {
    private static final int PORT = 18895;
    
    public void testHttp11KeepAlive() throws Exception {
        final String msg = "Hello world #";
        
        HttpServer server = createServer(new HttpService() {
            private final AtomicInteger ai = new AtomicInteger();
            
            @Override
            public void service(Request request,
                    Response response) throws Exception {
                response.setContentType("text/plain");
                response.getWriter().write(msg + ai.getAndIncrement());
            }

        }, "/path");

        final TCPNIOTransport clientTransport = TransportFactory.getInstance().createTCPTransport();
        final HttpClient client = new HttpClient(clientTransport);

        try {
            server.start();
            clientTransport.start();

            Future<Connection> connectFuture = client.connect("localhost", PORT);
            connectFuture.get(10, TimeUnit.SECONDS);

            Future<Buffer> resultFuture = client.get(HttpRequestPacket.builder().method("GET")
                        .uri("/path").protocol(Protocol.HTTP_1_1)
                        .header("Host", "localhost:" + PORT).build());

            Buffer buffer = resultFuture.get(10, TimeUnit.SECONDS);

            assertEquals("Hello world #0", buffer.toStringContent());

            resultFuture = client.get(HttpRequestPacket.builder().method("GET")
                        .uri("/path").protocol(Protocol.HTTP_1_1)
                        .header("Host", "localhost:" + PORT).build());

            buffer = resultFuture.get(10, TimeUnit.SECONDS);

            assertEquals("Hello world #1", buffer.toStringContent());

        } catch (IOException e) {
            e.printStackTrace();
            fail();
        } finally {
            client.close();
            clientTransport.stop();
            server.stop();
            TransportFactory.getInstance().close();
        }
    }

    public void testHttp11KeepAliveHeaderClose() throws Exception {
        final String msg = "Hello world #";

        HttpServer server = createServer(new HttpService() {
            private final AtomicInteger ai = new AtomicInteger();

            @Override
            public void service(Request request,
                    Response response) throws Exception {
                response.setContentType("text/plain");
                response.getWriter().write(msg + ai.getAndIncrement());
            }

        }, "/path");

        final TCPNIOTransport clientTransport = TransportFactory.getInstance().createTCPTransport();
        final HttpClient client = new HttpClient(clientTransport);

        try {
            server.start();
            clientTransport.start();

            Future<Connection> connectFuture = client.connect("localhost", PORT);
            connectFuture.get(10, TimeUnit.SECONDS);

            Future<Buffer> resultFuture = client.get(HttpRequestPacket.builder()
                    .method("GET")
                        .uri("/path").protocol(Protocol.HTTP_1_1)
                        .header("Connection", "close")
                        .header("Host", "localhost:" + PORT)
                        .build());

            Buffer buffer = resultFuture.get(10, TimeUnit.SECONDS);

            assertEquals("Hello world #0", buffer.toStringContent());

            try {
                resultFuture = client.get(HttpRequestPacket.builder()
                        .method("GET")
                        .uri("/path")
                        .protocol(Protocol.HTTP_1_1)
                        .header("Host", "localhost:" + PORT)
                        .build());

                buffer = resultFuture.get(10, TimeUnit.SECONDS);

                fail("IOException expected");
            } catch (ExecutionException ee) {
                final Throwable cause = ee.getCause();
                assertTrue("IOException expected, but got" + cause.getClass() +
                        " " + cause.getMessage(), cause instanceof IOException);
            }
        } catch (IOException e) {
            e.printStackTrace();
            fail();
        } finally {
            client.close();
            clientTransport.stop();
            server.stop();
            TransportFactory.getInstance().close();
        }
    }

    public void testHttp11KeepAliveMaxRequests() throws Exception {
        final String msg = "Hello world #";

        final int maxKeepAliveRequests = 5;

        HttpServer server = createServer(new HttpService() {
            private final AtomicInteger ai = new AtomicInteger();

            @Override
            public void service(Request request,
                    Response response) throws Exception {
                response.setContentType("text/plain");
                response.getWriter().write(msg + ai.getAndIncrement());
            }

        }, "/path");
        server.getListeners().next().getKeepAlive().setMaxRequestsCount(maxKeepAliveRequests);

        final TCPNIOTransport clientTransport = TransportFactory.getInstance().createTCPTransport();
        final HttpClient client = new HttpClient(clientTransport);

        try {
            server.start();
            clientTransport.start();

            Future<Connection> connectFuture = client.connect("localhost", PORT);
            connectFuture.get(10, TimeUnit.SECONDS);

            for (int i=0; i <= maxKeepAliveRequests; i++) {
                final Future<Buffer> resultFuture = client.get(HttpRequestPacket.builder()
                        .method("GET")
                            .uri("/path").protocol(Protocol.HTTP_1_1)
                            .header("Host", "localhost:" + PORT)
                            .build());

                final Buffer buffer = resultFuture.get(10, TimeUnit.SECONDS);

                assertEquals("Hello world #" + i, buffer.toStringContent());
            }

            try {
                final Future<Buffer> resultFuture = client.get(HttpRequestPacket.builder()
                        .method("GET")
                        .uri("/path")
                        .protocol(Protocol.HTTP_1_1)
                        .header("Host", "localhost:" + PORT)
                        .build());

                final Buffer buffer = resultFuture.get(10000, TimeUnit.SECONDS);

                fail("IOException expected");
            } catch (ExecutionException ee) {
                final Throwable cause = ee.getCause();
                assertTrue("IOException expected, but got" + cause.getClass() +
                        " " + cause.getMessage(), cause instanceof IOException);
            }
        } catch (IOException e) {
            e.printStackTrace();
            fail();
        } finally {
            client.close();
            clientTransport.stop();
            server.stop();
            TransportFactory.getInstance().close();
        }
    }
    
    // --------------------------------------------------------- Private Methods

    private static class HttpClient {
        private final TCPNIOTransport transport;
        private volatile Connection connection;
        private volatile FutureImpl<Buffer> asyncFuture;

        public HttpClient(TCPNIOTransport transport) {
            this.transport = transport;
        }

        public Future<Connection> connect(String host, int port) throws IOException {
            FilterChainBuilder filterChainBuilder = FilterChainBuilder.stateless();
            filterChainBuilder.add(new TransportFilter());
            filterChainBuilder.add(new HttpClientFilter());
            filterChainBuilder.add(new HttpResponseFilter());

            final SocketConnectorHandler connector =
                    new TCPNIOConnectorHandler(transport);

            connector.setProcessor(filterChainBuilder.build());

            return connector.connect(new InetSocketAddress(host, port), new EmptyCompletionHandler<Connection>() {
                @Override
                public void completed(Connection result) {
                    connection = result;
                }
            });
        }

        public Future<Buffer> get(HttpRequestPacket request) throws IOException {
            final FutureImpl<Buffer> localFuture = SafeFutureImpl.<Buffer>create();
            asyncFuture = localFuture;
            connection.write(request, new EmptyCompletionHandler() {

                @Override
                public void failed(Throwable throwable) {
                    localFuture.failure(throwable);
                }

            });
            return localFuture;
        }

        public void close() throws IOException {
            if (connection != null) {
                connection.close();
            }
        }

        private class HttpResponseFilter extends BaseFilter {
            @Override
            public NextAction handleRead(FilterChainContext ctx) throws IOException {
                HttpContent message = (HttpContent) ctx.getMessage();
                if (message.isLast()) {
                    final FutureImpl<Buffer> localFuture = asyncFuture;
                    asyncFuture = null;
                    localFuture.result(message.getContent());

                    return ctx.getStopAction();
                }

                return ctx.getStopAction(message);
            }

            @Override
            public NextAction handleClose(FilterChainContext ctx) throws IOException {
                final FutureImpl<Buffer> localFuture = asyncFuture;
                asyncFuture = null;
                if (localFuture != null) {
                    localFuture.failure(new EOFException());
                }

                return ctx.getInvokeAction();
            }


        }


    }

    private HttpServer createServer(final HttpService httpService,
                                          final String... mappings) {

        HttpServer server = new HttpServer();
        NetworkListener listener =
                new NetworkListener("grizzly",
                                    NetworkListener.DEFAULT_NETWORK_HOST,
                                    PORT);
        server.addListener(listener);
        if (httpService != null) {
            server.getServerConfiguration().addHttpService(httpService, mappings);
        }
        return server;

    }
}
