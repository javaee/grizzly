/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2014-2015 Oracle and/or its affiliates. All rights reserved.
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
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.ReadHandler;
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
import org.glassfish.grizzly.http.HttpResponsePacket;
import org.glassfish.grizzly.http.io.NIOInputStream;
import org.glassfish.grizzly.impl.FutureImpl;
import org.glassfish.grizzly.impl.SafeFutureImpl;
import org.glassfish.grizzly.memory.Buffers;
import org.glassfish.grizzly.memory.ByteBufferWrapper;
import org.glassfish.grizzly.memory.MemoryManager;
import org.glassfish.grizzly.nio.transport.TCPNIOConnectorHandler;
import org.glassfish.grizzly.utils.Charsets;
import org.glassfish.grizzly.utils.Futures;
import org.junit.After;
import org.junit.Test;
import org.junit.Before;

import static org.junit.Assert.*;

/**
 * Test the max post size limitation.
 * 
 * @author Alexey Stashok
 */
public class MaxPostSizeTest {
    private static final int PORT = 18907;
    
    private HttpServer httpServer;

    @Before
    public void before() throws Exception {
        ByteBufferWrapper.DEBUG_MODE = true;
        configureHttpServer();
    }

    @After
    public void after() throws Exception {
        if (httpServer != null) {
            httpServer.shutdownNow();
        }
    }

    @Test
    public void testContentLength() throws Exception {
        final String message1 = "01234";
        final String message2 = "0123456789";
        httpServer.getServerConfiguration().setMaxPostSize(message1.length());
        
        startHttpServer(new HttpHandler() {

            @Override
            public void service(Request request, Response response) throws Exception {
            }
        }, "/test");

        final HttpRequestPacket request1 = HttpRequestPacket.builder()
                .method("POST")
                .uri("/test")
                .protocol("HTTP/1.1")
                .header("Host", "localhost")
                .contentLength(message1.length())
                .build();
        final HttpContent content1 = HttpContent.builder(request1)
                .content(Buffers.wrap(MemoryManager.DEFAULT_MEMORY_MANAGER, message1))
                .build();

        final Future<HttpContent> responseFuture1 = send("localhost", PORT, content1);
        final HttpContent response1 = responseFuture1.get(10, TimeUnit.SECONDS);
        assertEquals(200, ((HttpResponsePacket) response1.getHttpHeader()).getStatus());

        final HttpRequestPacket request2 = HttpRequestPacket.builder()
                .method("POST")
                .uri("/test")
                .protocol("HTTP/1.1")
                .header("Host", "localhost")
                .contentLength(message2.length())
                .build();
        final HttpContent content2 = HttpContent.builder(request2)
                .content(Buffers.wrap(MemoryManager.DEFAULT_MEMORY_MANAGER, message2))
                .build();

        final Future<HttpContent> responseFuture2 = send("localhost", PORT, content2);
        final HttpContent response2 = responseFuture2.get(10, TimeUnit.SECONDS);
        assertEquals(400, ((HttpResponsePacket) response2.getHttpHeader()).getStatus());
    }
    
    @Test
    public void testBlockingChunkedTransferEncoding() throws Exception {
        final String newLine = "\n";
        final BlockingQueue<Future<String>> receivedChunksQueue =
                new ArrayBlockingQueue<Future<String>>(16);
        
        final String message = "0123456789" + newLine;
        final int messagesAllowed = 3;
        
        httpServer.getServerConfiguration().setMaxPostSize(message.length() * messagesAllowed);
        
        startHttpServer(new HttpHandler() {

            @Override
            public void service(Request request, Response response) throws Exception {
                final BufferedReader reader = new BufferedReader(request.getReader());
                
                try {
                    for (int i = 0; i <= messagesAllowed; i++) {
                        final String chunk = reader.readLine();
                        receivedChunksQueue.add(Futures.createReadyFuture(chunk + newLine));
                    }
                } catch (Exception e) {
                    receivedChunksQueue.add(Futures.<String>createReadyFuture(e));
                    response.sendError(400);
                }
            }
        }, "/test");

        final FutureImpl<HttpContent> responseFuture = Futures.createSafeFuture();
        final Connection c = createConnection(responseFuture, "localhost", PORT);
        
        final HttpRequestPacket request = HttpRequestPacket.builder()
                .method("POST")
                .uri("/test")
                .protocol("HTTP/1.1")
                .header("Host", "localhost")
                .chunked(true)
                .build();
        
        for (int i = 0; i < messagesAllowed; i++) {
            final HttpContent content = HttpContent.builder(request)
                    .content(Buffers.wrap(MemoryManager.DEFAULT_MEMORY_MANAGER, message))
                    .build();
            c.write(content);
            final Future<String> receivedFuture =
                    receivedChunksQueue.poll(10, TimeUnit.SECONDS);
            assertNotNull(receivedFuture);
            assertEquals(message, receivedFuture.get());
        }

        final HttpContent content = HttpContent.builder(request)
                .content(Buffers.wrap(MemoryManager.DEFAULT_MEMORY_MANAGER, message))
                .build();
        c.write(content);
        final Future<String> failFuture
                = receivedChunksQueue.poll(10, TimeUnit.SECONDS);
        assertNotNull(failFuture);
        
        try {
            failFuture.get();
            fail("Should have faild with the IOException");
        } catch (ExecutionException e) {
            assertTrue(e.getCause() instanceof IOException);
        }

        final HttpContent response2 = responseFuture.get(10, TimeUnit.SECONDS);
        assertEquals(400, ((HttpResponsePacket) response2.getHttpHeader()).getStatus());
    }
    
    @Test
    public void testNonBlockingChunkedTransferEncoding() throws Exception {
        final BlockingQueue<Future<byte[]>> receivedChunksQueue =
                new ArrayBlockingQueue<Future<byte[]>>(16);
        
        final byte[] message = "0123456789".getBytes(Charsets.ASCII_CHARSET);
        
        final int messagesAllowed = 3;
        
        httpServer.getServerConfiguration().setMaxPostSize(message.length * messagesAllowed);
        
        startHttpServer(new HttpHandler() {

            @Override
            public void service(final Request request, final Response response)
                    throws Exception {
                
                response.suspend();
                
                final NIOInputStream inputStream = request.getNIOInputStream();
                inputStream.notifyAvailable(new ReadHandler() {

                    @Override
                    public void onDataAvailable() throws Exception {
                        final byte[] buffer = new byte[message.length];
                        final int bytesRead = inputStream.read(buffer);
                        assert bytesRead == message.length;
                        receivedChunksQueue.add(Futures.createReadyFuture(buffer));
                        
                        inputStream.notifyAvailable(this, message.length);
                    }

                    @Override
                    public void onAllDataRead() throws Exception {
                        response.resume();
                    }
                    
                    @Override
                    public void onError(Throwable t) {
                        receivedChunksQueue.add(Futures.<byte[]>createReadyFuture(t));
                        try {
                            response.sendError(400);
                        } catch (IOException ex) {
                        } finally {
                            response.resume();
                        }
                    }
                }, message.length);
            }
        }, "/test");

        final FutureImpl<HttpContent> responseFuture = Futures.createSafeFuture();
        final Connection c = createConnection(responseFuture, "localhost", PORT);
        
        final HttpRequestPacket request = HttpRequestPacket.builder()
                .method("POST")
                .uri("/test")
                .protocol("HTTP/1.1")
                .header("Host", "localhost")
                .chunked(true)
                .build();
        
        for (int i = 0; i < messagesAllowed; i++) {
            final HttpContent content = HttpContent.builder(request)
                    .content(Buffers.wrap(MemoryManager.DEFAULT_MEMORY_MANAGER, message))
                    .build();
            c.write(content);
            final Future<byte[]> receivedFuture =
                    receivedChunksQueue.poll(10, TimeUnit.SECONDS);
            assertNotNull(receivedFuture);
            assertTrue(Arrays.equals(message, receivedFuture.get()));
        }

        final HttpContent content = HttpContent.builder(request)
                .content(Buffers.wrap(MemoryManager.DEFAULT_MEMORY_MANAGER, message))
                .build();
        c.write(content);
        final Future<byte[]> failFuture
                = receivedChunksQueue.poll(10, TimeUnit.SECONDS);
        assertNotNull(failFuture);
        
        try {
            failFuture.get();
            fail("Should have faild with the IOException");
        } catch (ExecutionException e) {
            assertTrue(e.getCause() instanceof IOException);
        }

        final HttpContent response2 = responseFuture.get(10, TimeUnit.SECONDS);
        assertEquals(400, ((HttpResponsePacket) response2.getHttpHeader()).getStatus());
    }
    
    private void configureHttpServer() throws Exception {
        httpServer = new HttpServer();
        final NetworkListener listener =
                new NetworkListener("grizzly",
                NetworkListener.DEFAULT_NETWORK_HOST,
                PORT);

        httpServer.addListener(listener);
    }

    private void startHttpServer(HttpHandler httpHandler, String... mappings) throws Exception {
        httpServer.getServerConfiguration().addHttpHandler(httpHandler, mappings);
        httpServer.start();
    }
    
    private Future<HttpContent> send(String host, int port, HttpPacket request) throws Exception {
        final FutureImpl<HttpContent> future = SafeFutureImpl.create();
        Connection connection = createConnection(future, host, port);

        connection.write(request);

        return future;
    }

    private Connection createConnection(final FutureImpl<HttpContent> future,
            final String host, final int port) throws Exception {
        final FilterChainBuilder builder = FilterChainBuilder.stateless();
        builder.add(new TransportFilter());
        builder.add(new HttpClientFilter());
        builder.add(new HttpMessageFilter(future));
        SocketConnectorHandler connectorHandler = TCPNIOConnectorHandler.builder(
                httpServer.getListener("grizzly").getTransport())
                .processor(builder.build())
                .build();
        Future<Connection> connectFuture = connectorHandler.connect(host, port);
        return connectFuture.get(10, TimeUnit.SECONDS);
    }
    
    private static class HttpMessageFilter extends BaseFilter {

        private final FutureImpl<HttpContent> future;

        public HttpMessageFilter(FutureImpl<HttpContent> future) {
            this.future = future;
        }

        @Override
        public NextAction handleRead(FilterChainContext ctx) throws IOException {
            final HttpContent content = ctx.getMessage();
            try {
                if (!content.isLast()) {
                    return ctx.getStopAction(content);
                }

                future.result(content);
            } catch (Exception e) {
                future.failure(e);
                e.printStackTrace();
            }

            return ctx.getStopAction();
        }
    }    
}
