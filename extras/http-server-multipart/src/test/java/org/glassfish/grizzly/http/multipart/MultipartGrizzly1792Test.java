/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2015 Oracle and/or its affiliates. All rights reserved.
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

import org.glassfish.grizzly.*;
import org.glassfish.grizzly.filterchain.*;
import org.glassfish.grizzly.http.HttpClientFilter;
import org.glassfish.grizzly.http.HttpContent;
import org.glassfish.grizzly.http.HttpPacket;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.http.Method;
import org.glassfish.grizzly.http.Protocol;
import org.glassfish.grizzly.http.io.NIOInputStream;
import org.glassfish.grizzly.http.multipart.utils.MultipartEntryPacket;
import org.glassfish.grizzly.http.multipart.utils.MultipartPacketBuilder;
import org.glassfish.grizzly.http.server.HttpHandler;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.grizzly.http.server.NetworkListener;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.http.server.Response;
import org.glassfish.grizzly.http.util.HttpStatus;
import org.glassfish.grizzly.impl.FutureImpl;
import org.glassfish.grizzly.impl.SafeFutureImpl;
import org.glassfish.grizzly.memory.Buffers;
import org.glassfish.grizzly.memory.MemoryManager;
import org.glassfish.grizzly.memory.PooledMemoryManager;
import org.glassfish.grizzly.nio.transport.TCPNIOConnectorHandler;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import org.glassfish.grizzly.utils.ChunkingFilter;
import org.glassfish.grizzly.utils.Futures;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.zip.CRC32;

import static org.junit.Assert.assertEquals;

/**
 * Test case for https://java.net/jira/browse/GRIZZLY-1792.
 */
public class MultipartGrizzly1792Test {

    private static final int PORT = 18203;


    // ------------------------------------------------------------ Test Methods


    @Test
    public void multipartGrizzly1792Test() throws Exception {
        final HttpServer httpServer = createServer("0.0.0.0", PORT);
        final HttpClient httpClient = new HttpClient(
                httpServer.getListener("Grizzly").getTransport());
        try {
            httpServer.start();


            final byte[] content = generateTestContent();
            CRC32 crc = new CRC32();
            crc.update(content);
            final long expectedCRC = crc.getValue();

            final HttpPacket request = createMPRequest(content);

            final Future<Connection> connectFuture = httpClient.connect("localhost", PORT);
            connectFuture.get(10, TimeUnit.SECONDS);

            final Future<HttpPacket> responsePacketFuture =
                    httpClient.get(request);
            final HttpPacket responsePacket =
                    responsePacketFuture.get(1000, TimeUnit.SECONDS);

            assertEquals(expectedCRC, (long) Long.valueOf(responsePacket.getHttpHeader().getHeader("test-crc")));
        } finally {
            httpServer.shutdownNow();
        }

    }


    // --------------------------------------------------------- Private Methods

    private HttpPacket createMPRequest(final byte[] data) {
        final String boundary = "---------------------------===103832778631715===";
        MultipartPacketBuilder mpb = MultipartPacketBuilder.builder(boundary);
        mpb.preamble("preamble").epilogue("epilogue");
        mpb.addMultipartEntry(MultipartEntryPacket.builder()
                .contentDisposition("form-data; name=\"file\"")
                .content(Buffers.wrap(MemoryManager.DEFAULT_MEMORY_MANAGER, data))
                .build());
        final Buffer bodyBuffer = mpb.build();

        final HttpRequestPacket requestHeader = HttpRequestPacket.builder()
                .method(Method.POST)
                .uri("/post")
                .protocol(Protocol.HTTP_1_1)
                .header("host", "localhost")
                .contentType("multipart/form-data; boundary=" + boundary)
                .contentLength(bodyBuffer.remaining())
                .build();

        return HttpContent.builder(requestHeader)
                .content(bodyBuffer)
                .build();
    }


    private HttpServer createServer(String host, int port) {
        final NetworkListener networkListener = new NetworkListener(
                "Grizzly", host, port);
        networkListener.getTransport().setMemoryManager(new PooledMemoryManager());
        final HttpServer httpServer = new HttpServer();
        httpServer.addListener(networkListener);
        httpServer.getServerConfiguration().addHttpHandler(new PostHandler(), "/post");
        return httpServer;
    }

    private static byte[] generateTestContent() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int i = 0; i < 230000; i++) {
            out.write((byte) i);
        }
        out.flush();
        out.close();
        return out.toByteArray();
    }


    // ---------------------------------------------------------- Nested Classes


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
                    Futures.createSafeFuture();

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

        @SuppressWarnings("unchecked")
        public Future<HttpPacket> get(HttpPacket request) throws IOException {
            final FutureImpl<HttpPacket> localFuture = SafeFutureImpl.create();
            asyncFuture = localFuture;
            connection.write(request, new EmptyCompletionHandler() {

                @Override
                public void failed(Throwable throwable) {
                    localFuture.failure(throwable);
                }
            });

            //noinspection deprecation
            connection.addCloseListener(new GenericCloseListener() {

                @Override
                public void onClosed(Closeable closeable, CloseType type)
                        throws IOException {
                    localFuture.failure(new IOException());
                }
            });
            return localFuture;
        }

        private class HttpResponseFilter extends BaseFilter {
            @Override
            public NextAction handleRead(FilterChainContext ctx) throws IOException {
                HttpContent message = ctx.getMessage();
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


    public static class PostHandler extends HttpHandler {
        public void service(Request request, Response response) throws Exception {
            CRC32 checksum = new CRC32();
            PartHandler partHandler = new PartHandler(checksum, response);
            CompletePost compHandler = new CompletePost(response);

            response.suspend();

            MultipartScanner.scan(request, partHandler, compHandler);
        }
    }

    public static class PartHandler implements MultipartEntryHandler {
        private CRC32 checksum;
        private Response response;

        PartHandler(CRC32 checksum, Response response) {
            this.checksum = checksum;
            this.response = response;
        }

        public void handle(MultipartEntry multipartEntry) throws Exception {
            ContentDisposition contentDisposition = multipartEntry.getContentDisposition();
            String name = contentDisposition.getDispositionParamUnquoted("name");

            if (name.equalsIgnoreCase("file")) {
                PostReader reader = new PostReader(checksum, response, multipartEntry.getNIOInputStream());
                reader.start();
            } else {
                multipartEntry.skip();
            }
        }
    }

    public static class PostReader implements ReadHandler {
        private CRC32 checksum;
        private Response response;
        private NIOInputStream in = null;
        private long totalBytes = 0;
        private final byte[] buffer = new byte[4096];

        PostReader(CRC32 checksum, Response response, NIOInputStream in) {
            this.checksum = checksum;
            this.response = response;
            this.in = in;
        }

        public void start() {
            in.notifyAvailable(this);
        }

        public void onDataAvailable() throws Exception {
            storeAvailableBytes();

            // Repeat when more data is available.
            in.notifyAvailable(this);
        }

        public void onError(Throwable t) {
            t.printStackTrace();
            response.setStatus(500, t.toString());
            finish(true);
        }

        public void onAllDataRead() throws Exception {
            storeAvailableBytes();
            response.setStatus(HttpStatus.ACCEPTED_202);
            finish(false);
        }

        private void storeAvailableBytes() throws IOException {
            while (in.isReady()) {
                int nBytes = in.read(buffer, 0, buffer.length);
                if (nBytes < 0) break;
                totalBytes += nBytes;
                checksum.update(buffer, 0, nBytes);
            }
        }

        private void finish(boolean errorFlag) {
            try {
                in.close();
            } catch (Throwable t) {
                t.printStackTrace();
                if (!errorFlag) {
                    response.setStatus(500, "I/O error on input");
                    errorFlag = true;
                }
            }

            if (!errorFlag) {
                System.out.println("Received a total of " + totalBytes + " bytes.");
                long crc = checksum.getValue();
                response.setContentLength(0);
                response.setHeader("test-crc", Long.toString(crc));
            }
        }

    }

    public static class CompletePost extends EmptyCompletionHandler<Request> {
        private Response response;

        CompletePost(Response response) {
            this.response = response;
        }

        public void completed(Request request) {
            // Protected against double invocation.
            if (response == null) return;

            response.resume();
            response = null;
        }

        public void failed(Throwable t) {
            // Protected against double invocation.
            if (response == null) return;

            t.printStackTrace();

            response.resume();
            response = null;
        }
    }

}
