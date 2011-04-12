/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.http.multipart;

import org.glassfish.grizzly.http.util.Charsets;
import org.junit.Test;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.EmptyCompletionHandler;
import org.glassfish.grizzly.SocketConnectorHandler;
import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.FilterChainBuilder;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.filterchain.TransportFilter;
import org.glassfish.grizzly.http.HttpClientFilter;
import org.glassfish.grizzly.http.HttpContent;
import org.glassfish.grizzly.http.HttpPacket;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.http.Method;
import org.glassfish.grizzly.http.Protocol;
import org.glassfish.grizzly.http.multipart.utils.MultipartEntryPacket;
import org.glassfish.grizzly.http.multipart.utils.MultipartPacketBuilder;
import org.glassfish.grizzly.http.server.HttpHandler;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.grizzly.http.server.NetworkListener;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.http.server.Response;
import org.glassfish.grizzly.impl.FutureImpl;
import org.glassfish.grizzly.impl.SafeFutureImpl;
import org.glassfish.grizzly.nio.transport.TCPNIOConnectorHandler;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import org.glassfish.grizzly.utils.ChunkingFilter;
import static org.junit.Assert.*;
/**
 * Basic multipart tests.
 * 
 * @author Alexey Stashok
 */
@SuppressWarnings ("unchecked")
public class MultipartBasicTests {

    private final int PORT = 18203;

    @Test
    public void multipartScannerAcceptTest() throws Exception {
        final HttpServer httpServer = createServer("0.0.0.0", PORT);

        final HttpClient httpClient = new HttpClient(
                httpServer.getListener("Grizzly").getTransport());
        try {
            httpServer.getServerConfiguration().addHttpHandler(new HttpHandler() {

                @Override
                public void service(final Request request, final Response response)
                        throws Exception {
                    response.suspend();
                    
                    MultipartScanner scanner = new MultipartScanner();

                    scanner.scan(request, new MultipartEntryHandler() {
                        @Override
                        public void handle(MultipartEntry part) throws Exception {
                            part.skip();
                        }
                    }, new EmptyCompletionHandler<Request>() {

                        @Override
                        public void completed(Request result) {
                            try {
                                response.getOutputStream().write("TRUE".getBytes(Charsets.ASCII_CHARSET));
                            } catch (IOException e) {
                            } finally {
                                response.resume();
                            }
                        }

                        @Override
                        public void failed(Throwable throwable) {
                            try {
                                response.getOutputStream().write("FALSE".getBytes(Charsets.ASCII_CHARSET));
                            } catch (IOException e) {
                            } finally {
                                response.resume();
                            }
                        }
                        
                    });
                }
            }, "/");

            httpServer.start();

            final HttpPacket multipartPacket = createMultipartPacket();
            final Future<Connection> connectFuture = httpClient.connect("localhost", PORT);
            connectFuture.get(10, TimeUnit.SECONDS);

            final Future<HttpPacket> responsePacketFuture =
                    httpClient.get(multipartPacket);
            final HttpPacket responsePacket = 
                    responsePacketFuture.get(10, TimeUnit.SECONDS);

            assertTrue(HttpContent.isContent(responsePacket));
            
            final HttpContent responseContent = (HttpContent) responsePacket;
            assertEquals("TRUE", responseContent.getContent().toStringContent(Charsets.ASCII_CHARSET));
        } finally {
            httpServer.stop();
//            httpClient.close();
        }
    }

    @Test
    public void multipartScannerRefuseTest() throws Exception {
        final HttpServer httpServer = createServer("0.0.0.0", PORT);

        final HttpClient httpClient = new HttpClient(
                httpServer.getListener("Grizzly").getTransport());
        try {
            httpServer.getServerConfiguration().addHttpHandler(new HttpHandler() {

                @Override
                public void service(final Request request, final Response response)
                        throws Exception {
                    response.suspend();
                    
                    MultipartScanner scanner = new MultipartScanner();

                    scanner.scan(request, new MultipartEntryHandler() {
                        @Override
                        public void handle(MultipartEntry part) throws Exception {
                            part.skip();
                        }
                    }, new EmptyCompletionHandler<Request>() {

                        @Override
                        public void completed(Request result) {
                            try {
                                response.getOutputStream().write("TRUE".getBytes(Charsets.ASCII_CHARSET));
                            } catch (IOException e) {
                            } finally {
                                response.resume();
                            }
                        }

                        @Override
                        public void failed(Throwable throwable) {
                            try {
                                response.getOutputStream().write("FALSE".getBytes(Charsets.ASCII_CHARSET));
                            } catch (IOException e) {
                            } finally {
                                response.resume();
                            }
                        }
                        
                    });
                }
            }, "/");

            httpServer.start();

            final HttpPacket multipartPacket = createPlainPacket();
            final Future<Connection> connectFuture = httpClient.connect("localhost", PORT);
            connectFuture.get(10, TimeUnit.SECONDS);

            final Future<HttpPacket> responsePacketFuture =
                    httpClient.get(multipartPacket);
            final HttpPacket responsePacket = 
                    responsePacketFuture.get(10, TimeUnit.SECONDS);

            assertTrue(HttpContent.isContent(responsePacket));
            
            final HttpContent responseContent = (HttpContent) responsePacket;
            assertEquals("FALSE", responseContent.getContent().toStringContent(Charsets.ASCII_CHARSET));
        } finally {
            httpServer.stop();
//            httpClient.close();
        }
    }
    
    private HttpPacket createMultipartPacket() {
        String boundary = "---------------------------103832778631715";
        MultipartPacketBuilder mpb = MultipartPacketBuilder.builder(boundary);
        mpb.preamble("preamble").epilogue("epilogue");

        mpb.addMultipartEntry(MultipartEntryPacket.builder()
                .contentDisposition("form-data; name=\"name\"")
                .content("not single")
                .build());
        mpb.addMultipartEntry(MultipartEntryPacket.builder()
                .contentDisposition("form-data; name=\"married\"")
                .content("MyName")
                .build());
        mpb.addMultipartEntry(MultipartEntryPacket.builder()
                .contentDisposition("form-data; name=\"male\"")
                .content("yes\r\r\r\r\r\ryes yes\r\n")
                .build());

        final Buffer bodyBuffer = mpb.build();

        final HttpRequestPacket requestHeader = HttpRequestPacket.builder()
                .method(Method.POST)
                .uri("/multipart")
                .protocol(Protocol.HTTP_1_1)
                .header("host", "localhost")
                .contentType("multipart/form-data; boundary=" + boundary)
                .contentLength(bodyBuffer.remaining())
                .build();

        final HttpContent request = HttpContent.builder(requestHeader)
                .content(bodyBuffer)
                .build();

        return request;
    }

    private HttpPacket createPlainPacket() {
        return HttpRequestPacket.builder()
                .method(Method.GET)
                .uri("/multipart")
                .protocol(Protocol.HTTP_1_1)
                .header("host", "localhost")
                .contentType("text/html")
                .build();
    }
    
    private static class HttpClient {
        private final TCPNIOTransport transport;
        private final int chunkSize;

        private volatile Connection connection;
        private volatile FutureImpl<HttpPacket> asyncFuture;

        public HttpClient(TCPNIOTransport transport) {
            this(transport, -1);
        }

        public HttpClient(TCPNIOTransport transport, int chunkSize) {
            this.transport = transport;
            this.chunkSize = chunkSize;
        }

        public Future<Connection> connect(String host, int port) throws IOException {
            FilterChainBuilder filterChainBuilder = FilterChainBuilder.stateless();
            filterChainBuilder.add(new TransportFilter());
            
            if (chunkSize > 0) {
                filterChainBuilder.add(new ChunkingFilter(chunkSize));
            }

            filterChainBuilder.add(new HttpClientFilter());
            filterChainBuilder.add(new HttpResponseFilter());

            final SocketConnectorHandler connector =
                    TCPNIOConnectorHandler.builder(transport)
                    .processor(filterChainBuilder.build())
                    .build();

            return connector.connect(new InetSocketAddress(host, port),
                    new EmptyCompletionHandler<Connection>() {
                @Override
                public void completed(Connection result) {
                    connection = result;
                }
            });
        }

        public Future<HttpPacket> get(HttpPacket request) throws IOException {
            final FutureImpl<HttpPacket> localFuture = SafeFutureImpl.<HttpPacket>create();
            asyncFuture = localFuture;
            connection.write(request, new EmptyCompletionHandler() {

                @Override
                public void failed(Throwable throwable) {
                    localFuture.failure(throwable);
                }
            });

            connection.addCloseListener(new Connection.CloseListener() {

                @Override
                public void onClosed(Connection connection) throws IOException {
                    localFuture.failure(new IOException());
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
                    final FutureImpl<HttpPacket> localFuture = asyncFuture;
                    asyncFuture = null;
                    localFuture.result(message);

                    return ctx.getStopAction();
                }

                return ctx.getStopAction(message);
            }
        }
    }

    private HttpServer createServer(String host, int port) {
        final NetworkListener networkListener = new NetworkListener(
                "Grizzly", host, port);
        final HttpServer httpServer = new HttpServer();
        httpServer.addListener(networkListener);

        return httpServer;
    }
}
