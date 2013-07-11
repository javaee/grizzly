/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011-2013 Oracle and/or its affiliates. All rights reserved.
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

import org.glassfish.grizzly.Closeable;
import org.glassfish.grizzly.CloseType;
import org.glassfish.grizzly.GenericCloseListener;
import org.glassfish.grizzly.utils.Charsets;
import org.junit.Test;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
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
import org.glassfish.grizzly.utils.Futures;
import static org.junit.Assert.*;
/**
 * Basic multipart tests.
 * 
 * @author Alexey Stashok
 */
@SuppressWarnings ("unchecked")
public class MultipartBasicTest {

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
                    
                    MultipartScanner.scan(request, new MultipartEntryHandler() {
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
                                response.getOutputStream().write(("FALSE: " + throwable).getBytes(Charsets.ASCII_CHARSET));
                            } catch (IOException e) {
                            } finally {
                                response.resume();
                            }
                        }
                        
                    });
                }
            }, "/");

            httpServer.start();

            final HttpPacket multipartPacket = createMultipartFormDataPacket();
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
            httpServer.shutdownNow();
//            httpClient.close();
        }
    }

    @Test
    public void multipartRelatedScannerTest() throws Exception {
        final HttpServer httpServer = createServer("0.0.0.0", PORT);

        final HttpClient httpClient = new HttpClient(
                httpServer.getListener("Grizzly").getTransport());
        try {
            final MultipartCheckInfo checkInfo = getMultipartRelatedPacketCheckInfo();
            final AtomicInteger partNum = new AtomicInteger();
            
            httpServer.getServerConfiguration().addHttpHandler(new HttpHandler() {

                @Override
                public void service(final Request request, final Response response)
                        throws Exception {
                    response.suspend();
                    
                    MultipartScanner.scan(request, new MultipartEntryHandler() {
                        @Override
                        public void handle(MultipartEntry part) throws Exception {
                            checkInfo.checkContext(part.getMultipartContext());
                            checkInfo.checkPart(partNum.getAndIncrement(), part);
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
                                response.getOutputStream().write(("FALSE: " + throwable).getBytes(Charsets.ASCII_CHARSET));
                            } catch (IOException e) {
                            } finally {
                                response.resume();
                            }
                        }
                        
                    });
                }
            }, "/");

            httpServer.start();

            final HttpPacket multipartPacket = createMultipartRelatedPacket();
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
            httpServer.shutdownNow();
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
                    
                    MultipartScanner.scan(request, new MultipartEntryHandler() {
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
                                response.getOutputStream().write(("FALSE: " + throwable).getBytes(Charsets.ASCII_CHARSET));
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
            assertTrue(responseContent.getContent().toStringContent(Charsets.ASCII_CHARSET),
                    responseContent.getContent().toStringContent(Charsets.ASCII_CHARSET).startsWith("FALSE"));
        } finally {
            httpServer.shutdownNow();
//            httpClient.close();
        }
    }
    
    private HttpPacket createMultipartFormDataPacket() {
        String boundary = "---------------------------===103832778631715===";
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

    private MultipartCheckInfo getMultipartRelatedPacketCheckInfo() {
        String boundary = "example-2";
        MultipartCheckInfo mpCheckInfo = new MultipartCheckInfo(boundary,
                "Multipart/Related; boundary=" + boundary + ";" +
                        "start=\"<950118.AEBH@XIson.com>\";" +
                        "type=\"Text/x-Okie\"");
        mpCheckInfo.addContentTypeAttribute("boundary", boundary);
        mpCheckInfo.addContentTypeAttribute("start", "<950118.AEBH@XIson.com>");
        mpCheckInfo.addContentTypeAttribute("type", "Text/x-Okie");
        
        PartCheckInfo p1 = new PartCheckInfo("Text/x-Okie; charset=iso-8859-1;" +
                            "declaration=\"<950118.AEB0@XIson.com>\"");
        p1.addHeader("Content-ID", "<950118.AEBH@XIson.com>");
        p1.addHeader("Content-Description", "Document");
        
        PartCheckInfo p2 = new PartCheckInfo("image/jpeg");
        p2.addHeader("Content-ID", "<950118.AFDH@XIson.com>");
        p2.addHeader("Content-Transfer-Encoding", "BASE64");
        p2.addHeader("Content-Description", "Picture A");
        
        PartCheckInfo p3 = new PartCheckInfo("image/jpeg");
        p3.addHeader("Content-ID", "<950118.AECB@XIson.com>");
        p3.addHeader("Content-Transfer-Encoding", "BASE64");
        p3.addHeader("Content-Description", "Picture B");
        
        mpCheckInfo.addPart(p1);
        mpCheckInfo.addPart(p2);
        mpCheckInfo.addPart(p3);

        return mpCheckInfo;
    }
    
    private HttpPacket createMultipartRelatedPacket() {
        String boundary = "example-2";
        MultipartPacketBuilder mpb = MultipartPacketBuilder.builder(boundary);
        mpb.preamble("preamble").epilogue("epilogue");

        mpb.addMultipartEntry(MultipartEntryPacket.builder()
                .contentType("Text/x-Okie; charset=iso-8859-1;" +
                            "declaration=\"<950118.AEB0@XIson.com>\"")
                .header("Content-ID", "<950118.AEBH@XIson.com>")
                .header("Content-Description", "Document")
                .content("{doc}"
                        + "This picture was taken by an automatic camera mounted ..."
                        + "{image file=cid:950118.AECB@XIson.com}"
                        + "{para}"
                        + "Now this is an enlargement of the area ..."
                        + "{image file=cid:950118:AFDH@XIson.com}"
                        + "{/doc}")
                .build());
        mpb.addMultipartEntry(MultipartEntryPacket.builder()
                .contentType("image/jpeg")
                .header("Content-ID", "<950118.AFDH@XIson.com>")
                .header("Content-Transfer-Encoding", "BASE64")
                .header("Content-Description", "Picture A")
                .content("[encoded jpeg image]")
                .build());
        mpb.addMultipartEntry(MultipartEntryPacket.builder()
                .contentType("image/jpeg")
                .header("Content-ID", "<950118.AECB@XIson.com>")
                .header("Content-Transfer-Encoding", "BASE64")
                .header("Content-Description", "Picture B")
                .content("[encoded jpeg image]")
                .build());

        final Buffer bodyBuffer = mpb.build();

        final HttpRequestPacket requestHeader = HttpRequestPacket.builder()
                .method(Method.POST)
                .uri("/multipart")
                .protocol(Protocol.HTTP_1_1)
                .header("host", "localhost")
                .contentType("Multipart/Related; boundary=" + boundary + ";" +
                        "start=\"<950118.AEBH@XIson.com>\";" +
                        "type=\"Text/x-Okie\"")
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

            final FutureImpl<Connection> future =
                    Futures.<Connection>createSafeFuture();
            
            connector.connect(new InetSocketAddress(host, port),
                    Futures.toCompletionHandler(future, 
                    new EmptyCompletionHandler<Connection>() {
                @Override
                public void completed(Connection result) {
                    connection = result;
                }
            }));
            
            return future;
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

            connection.addCloseListener(new GenericCloseListener() {

                @Override
                public void onClosed(Closeable closeable, CloseType type)
                        throws IOException {
                    localFuture.failure(new IOException());
                }
            });
            return localFuture;
        }

        public void close() {
            if (connection != null) {
                connection.closeSilently();
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
    
    class MultipartCheckInfo {
        final String boundary;
        final String contentType;
        final Map<String, String> contentTypeAttributes = new HashMap<String, String>();
        
        final List<PartCheckInfo> parts = new ArrayList<PartCheckInfo>();

        public MultipartCheckInfo(String boundary, String contentType) {
            this.boundary = boundary;
            this.contentType = contentType;
        }
        
        public void addContentTypeAttribute(String name, String value) {
            contentTypeAttributes.put(name, value);
        }
        
        public void addPart(PartCheckInfo part) {
            parts.add(part);
        }
        
        public boolean checkContext(MultipartContext context) {
            assertEquals(boundary, context.getBoundary());
            assertEquals(context.getContentTypeAttributes().get("boundary"),
                    context.getBoundary());
            assertEquals(contentType,
                    context.getContentType());

            assertEquals(contentTypeAttributes.size(),
                    context.getContentTypeAttributes().size());
            
            for (Map.Entry<String, String> contentTypeAttrEntry : context.getContentTypeAttributes().entrySet()) {
                assertTrue(contentTypeAttrEntry.getKey(),
                        contentTypeAttributes.containsKey(contentTypeAttrEntry.getKey()));
                assertEquals(contentTypeAttrEntry.getKey() + " values don't match",
                        contentTypeAttrEntry.getValue(),
                        contentTypeAttributes.get(contentTypeAttrEntry.getKey()));
            }
            return true;
        }

        private void checkPart(int partNum, MultipartEntry part) {
            final PartCheckInfo patternPart = parts.get(partNum);
            patternPart.check(part);
        }
    }
    
    class PartCheckInfo {
        final String contentType;
        final Map<String, String> headers = new HashMap<String, String>();

        public PartCheckInfo(String contentType) {
            this.contentType = contentType;
            addHeader("Content-Type", contentType);
        }
        
        public void addHeader(String name, String value) {
            headers.put(name.toLowerCase(), value);
        }

        private boolean check(MultipartEntry part) {
            assertEquals(contentType, part.getContentType());

            assertEquals(headers.size(),
                    part.getHeaderNames().size());
            
            for (String name : part.getHeaderNames()) {
                assertTrue(name,
                        headers.containsKey(name));
                assertEquals(name + " values don't match",
                        headers.get(name),
                        part.getHeader(name));
            }
            
            return true;
        }
    }
    
}
