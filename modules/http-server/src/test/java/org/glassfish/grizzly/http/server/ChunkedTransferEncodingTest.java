/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011-2015 Oracle and/or its affiliates. All rights reserved.
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

import org.glassfish.grizzly.http.util.HttpStatus;
import org.glassfish.grizzly.http.HttpHeader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.List;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.ReadHandler;
import org.glassfish.grizzly.http.HttpRequestPacket.Builder;
import org.glassfish.grizzly.http.util.MimeHeaders;
import org.junit.After;
import org.junit.Before;
import org.glassfish.grizzly.http.HttpPacket;
import org.junit.runners.Parameterized;
import org.junit.runner.RunWith;
import java.util.Arrays;
import java.util.Collection;
import org.junit.runners.Parameterized.Parameters;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.SocketConnectorHandler;
import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.FilterChainBuilder;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.filterchain.TransportFilter;
import org.glassfish.grizzly.http.HttpClientFilter;
import org.glassfish.grizzly.http.HttpCodecFilter;
import org.glassfish.grizzly.http.HttpContent;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.http.HttpResponsePacket;
import org.glassfish.grizzly.http.HttpTrailer;
import org.glassfish.grizzly.http.Method;
import org.glassfish.grizzly.http.Protocol;
import org.glassfish.grizzly.http.io.NIOInputStream;
import org.glassfish.grizzly.http.io.NIOOutputStream;
import org.glassfish.grizzly.http.util.Header;
import org.glassfish.grizzly.memory.Buffers;
import org.glassfish.grizzly.memory.MemoryManager;
import org.glassfish.grizzly.nio.transport.TCPNIOConnectorHandler;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import org.glassfish.grizzly.utils.Charsets;
import org.glassfish.grizzly.utils.ChunkingFilter;
import org.glassfish.grizzly.utils.DataStructures;
import org.glassfish.grizzly.utils.Pair;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Chunked Transfer-Encoding and HttpHandler tests.
 * 
 * @author Alexey Stashok
 */
@RunWith(Parameterized.class)
public class ChunkedTransferEncodingTest {
    private static final int PORT = 18898;

    private final boolean isChunkWhenParsing;
    private final boolean isAsyncHttpHandler;
    
    private HttpServer httpServer;
    private EchoHandler echoHandler;
    private Connection connection;
    
    @Parameters
    public static Collection<Object[]> getMode() {
        return Arrays.asList(new Object[][]{
                    {Boolean.FALSE, Boolean.FALSE},
                    {Boolean.FALSE, Boolean.TRUE},
                    {Boolean.TRUE, Boolean.FALSE},
                    {Boolean.TRUE, Boolean.TRUE},
                });
    }

    @Before
    public void before() throws Exception {
        Grizzly.setTrackingThreadCache(true);
        echoHandler = new EchoHandler();
        configureHttpServer();
        startHttpServer(echoHandler);
    }
    
    @After
    public void after() throws Exception {
        if (connection != null) {
            connection.closeSilently();
        }

        if (httpServer != null) {
            try {
                httpServer.shutdownNow();
            } catch (Exception ignored) {
            }
        }
    }

    public ChunkedTransferEncodingTest(boolean isChunkWhenParsing,
            boolean isAsyncHttpHandler) {
        this.isChunkWhenParsing = isChunkWhenParsing;
        this.isAsyncHttpHandler = isAsyncHttpHandler;
    }
        
    @Test
    public void testNoTrailerHeaders() throws Exception {
        Map<String, Pair<String, String>> headers =
                new HashMap<String, Pair<String, String>>();

        final int packetsNum = 5;
        
        doHttpRequestTest(packetsNum, true, headers, 200);
    }

    @Test
    public void testTrailerHeaders() throws Exception {
        Map<String, Pair<String, String>> headers =
                new HashMap<String, Pair<String, String>>();
        headers.put("X-Host", new Pair<String,String>("localhost", "localhost"));
        headers.put("X-Content-length", new Pair<String,String>("2345", "2345"));
        
        final int packetsNum = 5;
        
        doHttpRequestTest(packetsNum, true, headers, 200);
    }

    @Test
    public void testTrailerHeadersWithoutContent() throws Exception {
        Map<String, Pair<String, String>> headers =
                new HashMap<String, Pair<String, String>>();
        headers.put("X-Host", new Pair<String,String>("localhost", "localhost"));
        headers.put("X-Content-length", new Pair<String,String>("2345", "2345"));
        
        final int packetsNum = 10;
        
        doHttpRequestTest(packetsNum, false, headers, 200);
    }

    @Test
    public void testTrailerHeadersOverflow() throws Exception {
        Map<String, Pair<String, String>> headers =
                new HashMap<String, Pair<String, String>>();
        // This number of headers should be enough to overflow socket's read buffer,
        // so trailer headers will not fit into socket read window
        final int headersNum = HttpCodecFilter.DEFAULT_MAX_HTTP_PACKET_HEADER_SIZE / 2;
        for (int i = 0; i < headersNum; i++) {
            headers.put("X-Host-" + i, new Pair<String,String>("localhost", "localhost"));
        }
        
        doHttpRequestTest(1, true, headers, 500);
    }

    @SuppressWarnings("unchecked")
    private void doHttpRequestTest(
            int packetsNum,
            boolean hasContent,
            Map<String, Pair<String, String>> trailerHeaders,
            int expectedResponseCode)
            throws Exception {
        
        final BlockingQueue<HttpContent> queue = DataStructures.getLTQInstance();
                
        final NetworkListener networkListener = httpServer.getListener("grizzly");
        final TCPNIOTransport transport = networkListener.getTransport();
        
        FilterChainBuilder clientFilterChainBuilder = FilterChainBuilder.stateless();
        clientFilterChainBuilder.add(new TransportFilter())
                .add(new HttpClientFilter())
                .add(new HTTPResponseFilter(queue));

        SocketConnectorHandler connectorHandler = TCPNIOConnectorHandler
                .builder(transport)
                .processor(clientFilterChainBuilder.build())
                .build();

        Future<Connection> future = connectorHandler.connect("localhost", PORT);
        connection = future.get(10, TimeUnit.SECONDS);
        assertTrue(connection != null);

        for (int i = 0; i < packetsNum; i++) {
            final Buffer content;
            if (hasContent) {
                content = Buffers.wrap(MemoryManager.DEFAULT_MEMORY_MANAGER,
                    "a=0&b=1", Charsets.ASCII_CHARSET);
            } else {
                content = Buffers.EMPTY_BUFFER;
            }

            List<HttpPacket> httpPackets =
                    constructMessage(hasContent, trailerHeaders,
                    i == packetsNum - 1);


            for (HttpPacket packet : httpPackets) {
                connection.write(packet);
            }
            
            if (expectedResponseCode < 400) {
                final HttpContent responseHttpContent = queue.poll(10, TimeUnit.SECONDS);
                assertNotNull("timeout. packet#" + i, responseHttpContent);

                final HttpResponsePacket responsePacket =
                        (HttpResponsePacket) responseHttpContent.getHttpHeader();

                assertEquals("packet#" + i, expectedResponseCode, responsePacket.getStatus());
                
                // if we don't expect error response - check the content
                assertEquals("packet#" + i, content, responseHttpContent.getContent());
                final HttpHeader responseHeader = responseHttpContent.getHttpHeader();
                for (Entry<String, Pair<String, String>> entry : trailerHeaders.entrySet()) {
                    assertEquals("packet#" + i, entry.getValue().getSecond(),
                            responseHeader.getHeader(entry.getKey()));
                }
            } else {
                Throwable t = echoHandler.errors.poll(10, TimeUnit.SECONDS);
                assertTrue("Unexpected exception " + t, t instanceof IOException);
                assertTrue("there are other errors: " + echoHandler.errors, echoHandler.errors.isEmpty());
            }
        }
    }

    private List<HttpPacket> constructMessage(
            final boolean hasContent,
            final Map<String, Pair<String, String>> trailerHeaders,
            final boolean isAddCloseHeader) {

        final List<HttpPacket> packetList = new ArrayList<HttpPacket>();
        
        final Builder requestPacketBuilder = HttpRequestPacket.builder();
        requestPacketBuilder
                .method(Method.POST)
                .uri("/")
                .protocol(Protocol.HTTP_1_1)
                .header(Header.Host, "localhost")
                .contentType("application/x-www-form-urlencoded")
                .chunked(true);

        if (isAddCloseHeader) {
            requestPacketBuilder.header(Header.Connection, "close");
        }
        
        final HttpRequestPacket requestPacket = requestPacketBuilder.build();
        requestPacket.getHeaders().setMaxNumHeaders(-1);
        
        packetList.add(requestPacket);
        
        if (hasContent) {
            final HttpContent contentPacket1 = HttpContent.builder(requestPacket)
                    .content(Buffers.wrap(MemoryManager.DEFAULT_MEMORY_MANAGER,
                                            "a=0", Charsets.ASCII_CHARSET))
                    .build();
            final HttpContent contentPacket2 = HttpContent.builder(requestPacket)
                    .content(Buffers.wrap(MemoryManager.DEFAULT_MEMORY_MANAGER,
                                            "&b=1", Charsets.ASCII_CHARSET))
                    .build();

            packetList.add(contentPacket1);
            packetList.add(contentPacket2);
        }
        
        final HttpTrailer.Builder trailerBuilder = HttpTrailer.builder(requestPacket);
        final MimeHeaders headers = new MimeHeaders();
        headers.setMaxNumHeaders(-1);
        trailerBuilder.headers(headers);

        for (Entry<String, Pair<String, String>> entry : trailerHeaders.entrySet()) {
            final String value = entry.getValue().getFirst();
            trailerBuilder.header(entry.getKey(), value);
        }
    
        final HttpTrailer trailer = trailerBuilder.build();
        
        packetList.add(trailer);

        return packetList;
    }
    
    public static class HTTPResponseFilter extends BaseFilter {
        private final BlockingQueue<HttpContent> queue;

        public HTTPResponseFilter(
                final BlockingQueue<HttpContent> queue) {
            this.queue = queue;
        }

        @Override
        public NextAction handleRead(FilterChainContext ctx)
                throws IOException {
            final HttpContent httpContent = ctx.getMessage();
            if (!httpContent.isLast()) {
                return ctx.getStopAction(httpContent);
            }

            queue.offer(httpContent);
            return ctx.getStopAction();
        }
    }
    
    private void configureHttpServer() throws Exception {
        httpServer = new HttpServer();
        final NetworkListener listener =
                new NetworkListener("grizzly",
                                    NetworkListener.DEFAULT_NETWORK_HOST,
                                    PORT);
        listener.setMaxRequestHeaders(-1);
        listener.setMaxResponseHeaders(-1);
        listener.getTransport().getAsyncQueueIO().getWriter().setMaxPendingBytesPerConnection(-1);
        if (isChunkWhenParsing) {
            listener.registerAddOn(new AddOn() {

                @Override
                public void setup(final NetworkListener networkListener,
                        final FilterChainBuilder builder) {
                    final int idx = builder.indexOfType(TransportFilter.class);
                    builder.add(idx + 1, new ChunkingFilter(2));
                }
            });
        }

        httpServer.addListener(listener);
    }

    private void startHttpServer(HttpHandler httpHandler) throws Exception {
        httpServer.getServerConfiguration().addHttpHandler(httpHandler);
        httpServer.start();
    }
    
    private class EchoHandler extends HttpHandler {
        private final BlockingQueue<Throwable> errors =
                DataStructures.getLTQInstance(Throwable.class);
        
        @Override
        public void service(Request request, Response response) throws Exception {
            if (isAsyncHttpHandler) {
                doAsync(request, response);
            } else {
                doSync(request, response);
            }
        }

        public void doAsync(final Request request,
                            final Response response)
                throws Exception {

            final NIOInputStream reader = request.getNIOInputStream();
            final NIOOutputStream writer = response.getNIOOutputStream();                

            response.suspend();

            reader.notifyAvailable(new ReadHandler() {

                final ByteArrayOutputStream baos = new ByteArrayOutputStream();

                @Override
                public void onDataAvailable() throws Exception {
                    buffer();
                    reader.notifyAvailable(this);
                }

                @Override
                public void onAllDataRead() throws Exception {
                    buffer();
                    echo();
                    response.resume();

                }

                @Override
                public void onError(Throwable t) {
                    errors.offer(t);
                    
                    response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR_500);
                    response.setDetailMessage("Internal Error");
                    if (response.isSuspended()) {
                        response.resume();
                    }
                }
                
                private void buffer() throws IOException {
                    int available = reader.readyData();
                    if (available > 0) {
                        byte[] b = new byte[available];
                        int read = reader.read(b);
                        baos.write(b, 0, read);
                    }
                }
                
                private void echo() throws IOException {
                    for (String headerName : request.getHeaderNames()) {
                        if (headerName.startsWith("x-"));
                        response.addHeader(headerName, request.getHeader(headerName));
                    }
                    
                    writer.write(baos.toByteArray());
                }
            });

        }

        private void doSync(Request request, Response response) throws IOException {
            try {
                final InputStream is = request.getInputStream();
                final ByteArrayOutputStream bos = new ByteArrayOutputStream();

                int b;
                while((b = is.read()) != -1) {
                    bos.write(b);
                }

                bos.close();
                final byte[] output = bos.toByteArray();

                final OutputStream os = response.getOutputStream();

                for (String headerName : request.getHeaderNames()) {
                    if (headerName.startsWith("x-"));
                    response.addHeader(headerName, request.getHeader(headerName));
                }

                os.write(output);
            } catch (Throwable t) {
                errors.offer(t);
                throw new IOException(t);
            }
        }
        
    }
}
