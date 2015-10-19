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

import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.ReadHandler;
import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.FilterChainBuilder;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.filterchain.TransportFilter;
import org.glassfish.grizzly.http.HttpClientFilter;
import org.glassfish.grizzly.http.HttpContent;
import org.glassfish.grizzly.http.HttpHeader;
import org.glassfish.grizzly.http.HttpPacket;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.http.Method;
import org.glassfish.grizzly.http.Protocol;
import org.glassfish.grizzly.http.io.NIOInputStream;
import org.glassfish.grizzly.http.io.NIOOutputStream;
import org.glassfish.grizzly.impl.FutureImpl;
import org.glassfish.grizzly.impl.SafeFutureImpl;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import org.glassfish.grizzly.nio.transport.TCPNIOTransportBuilder;
import org.glassfish.grizzly.utils.ChunkingFilter;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;

/**
 * Test transfer-encoding application
 * 
 * @author Alexey Stashok
 */
@SuppressWarnings("unchecked")
@RunWith(Parameterized.class)
public class TransferEncodingTest {
    private static final int PORT = 8041;

    @Parameterized.Parameters
    public static Collection<Object[]> getIsBinary() {
        return Arrays.asList(new Object[][]{
                    {Boolean.FALSE},
                    {Boolean.TRUE}
                });
    }

    private final boolean isBinary;

    public TransferEncodingTest(boolean isBinary) {
        this.isBinary = isBinary;
    }    
    
    @Test
    public void testExplicitContentType() throws Exception {
        final int msgSize = 10;

        final HttpHandler httpHandler = new ExplicitContentLengthHandler(msgSize);
        final HttpPacket request = createRequest("/index.html", null);
        final HttpContent response = doTest(httpHandler, request, 10);

        assertEquals(msgSize, response.getHttpHeader().getContentLength());
    }

    @Test
    public void testExplicitChunking() throws Exception {
        final int msgSize = 10;

        final HttpHandler httpHandler = new ExplicitChunkingHandler(msgSize);
        final HttpPacket request = createRequest("/index.html", null);
        final HttpContent response = doTest(httpHandler, request, 10);

        assertTrue(response.getHttpHeader().isChunked());
    }

    @Test
    public void testSmallMessageAutoContentLength() throws Exception {
        final int msgSize = 10;

        final HttpPacket request1 = createRequest("/index.html", null);
        final HttpHandler httpHandler1 = new AutoTransferEncodingHandler(msgSize, false);
        final HttpContent response1 = doTest(httpHandler1, request1, 10);

        assertEquals(msgSize, response1.getHttpHeader().getContentLength());
        
        final HttpPacket request2 = createRequest("/index.html", null);
        final HttpHandler httpHandler2 = new AutoTransferEncodingHandler(msgSize, true);
        final HttpContent response2 = doTest(httpHandler2, request2, 10);

        assertEquals(msgSize, response2.getHttpHeader().getContentLength());
        
    }

    @Test
    public void testLargeMessageChunkingDisabled() throws Exception {
        final int msgSize = 1024 * 24;

        final HttpPacket request1 = createRequest("/index.html", null);
        final HttpHandler httpHandler1 = new AutoTransferEncodingHandler(msgSize, false) {
            @Override
            public void service(Request request, Response response) throws Exception {
                // disable chunking
                response.getResponse().setChunkingAllowed(false);
                super.service(request, response);
            }
        };
        
        final HttpContent response1 = doTest(httpHandler1, request1, 10);
        assertEquals(msgSize, response1.getHttpHeader().getContentLength());
        
        final HttpPacket request2 = createRequest("/index.html", null);
        final HttpHandler httpHandler2 = new AutoTransferEncodingHandler(msgSize, true) {
            @Override
            public void service(Request request, Response response) throws Exception {
                // disable chunking
                response.getResponse().setChunkingAllowed(false);
                super.service(request, response);
            }
        };
        
        final HttpContent response2 = doTest(httpHandler2, request2, 10);
        assertEquals(msgSize, response2.getHttpHeader().getContentLength());        
    }

    @Test
    public void testLargeMessageAutoChunking() throws Exception {
        final int msgSize = 1024 * 24;

        final HttpPacket request1 = createRequest("/index.html", null);
        final HttpHandler httpHandler1 = new AutoTransferEncodingHandler(msgSize, false);
        final HttpContent response1 = doTest(httpHandler1, request1, 10);

        assertTrue(response1.getHttpHeader().isChunked());
        
        final HttpPacket request2 = createRequest("/index.html", null);
        final HttpHandler httpHandler2 = new AutoTransferEncodingHandler(msgSize, true);
        final HttpContent response2 = doTest(httpHandler2, request2, 10);

        assertTrue(response2.getHttpHeader().isChunked());
        
    }
    
    @Test
    public void testLongContentLength() throws Exception {
        final long contentLength = Long.MAX_VALUE;
        HttpRequestPacket.Builder b = HttpRequestPacket.builder();
        b.method(Method.POST).protocol(Protocol.HTTP_1_1).uri("/postit")
                .header("Host", "localhost:" + PORT);
        b.contentLength(contentLength);

        final HttpHandler httpHandler = new EchoHandler();
        final HttpHeader response = doTestHeader(httpHandler, b.build(), 10);

        assertEquals(contentLength, response.getContentLength());
    }
    
    @SuppressWarnings({"unchecked"})
    private HttpPacket createRequest(String uri, Map<String, String> headers) {

        HttpRequestPacket.Builder b = HttpRequestPacket.builder();
        b.method(Method.GET).protocol(Protocol.HTTP_1_1).uri(uri).header("Host", "localhost:" + PORT);
        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                b.header(entry.getKey(), entry.getValue());
            }
        }

        return b.build();
    }

    private HttpContent doTest(final HttpHandler httpHandler,
            final HttpPacket request,
            final int timeout)
            throws Exception {

        final TCPNIOTransport clientTransport =
                TCPNIOTransportBuilder.newInstance().build();
        final HttpServer server = createWebServer(httpHandler);
        try {
            final FutureImpl<HttpContent> testResultFuture = SafeFutureImpl.create();

            server.start();
            FilterChainBuilder clientFilterChainBuilder = FilterChainBuilder.stateless();
            clientFilterChainBuilder.add(new TransportFilter());
            clientFilterChainBuilder.add(new ChunkingFilter(3));
            clientFilterChainBuilder.add(new HttpClientFilter());
            clientFilterChainBuilder.add(new ClientFilter(testResultFuture));
            clientTransport.setProcessor(clientFilterChainBuilder.build());

            clientTransport.start();

            Future<Connection> connectFuture = clientTransport.connect("localhost", PORT);
            Connection connection = null;
            try {
                connection = connectFuture.get(timeout, TimeUnit.SECONDS);
                connection.write(request);
                return testResultFuture.get(timeout, TimeUnit.SECONDS);
            } finally {
                // Close the client connection
                if (connection != null) {
                    connection.closeSilently();
                }
            }
        } finally {
            clientTransport.shutdownNow();
            server.shutdownNow();
        }
    }

    private HttpHeader doTestHeader(final HttpHandler httpHandler,
            final HttpPacket request,
            final int timeout)
            throws Exception {

        final TCPNIOTransport clientTransport =
                TCPNIOTransportBuilder.newInstance().build();
        final HttpServer server = createWebServer(httpHandler);
        try {
            final FutureImpl<HttpHeader> testResultFuture = SafeFutureImpl.create();

            server.start();
            FilterChainBuilder clientFilterChainBuilder = FilterChainBuilder.stateless();
            clientFilterChainBuilder.add(new TransportFilter());
            clientFilterChainBuilder.add(new ChunkingFilter(3));
            clientFilterChainBuilder.add(new HttpClientFilter());
            clientFilterChainBuilder.add(new HeaderTestClientFilter(testResultFuture));
            clientTransport.setProcessor(clientFilterChainBuilder.build());

            clientTransport.start();

            Future<Connection> connectFuture = clientTransport.connect("localhost", PORT);
            Connection connection = null;
            try {
                connection = connectFuture.get(timeout, TimeUnit.SECONDS);
                connection.write(request);
                return testResultFuture.get(timeout, TimeUnit.SECONDS);
            } finally {
                // Close the client connection
                if (connection != null) {
                    connection.closeSilently();
                }
            }
        } finally {
            clientTransport.shutdownNow();
            server.shutdownNow();
        }
    }

    private HttpServer createWebServer(final HttpHandler httpHandler) {

        final HttpServer server = new HttpServer();
        final NetworkListener listener =
                new NetworkListener("grizzly",
                        NetworkListener.DEFAULT_NETWORK_HOST,
                        PORT);
        listener.getKeepAlive().setIdleTimeoutInSeconds(-1);
        server.addListener(listener);
        server.getServerConfiguration().addHttpHandler(httpHandler, "/");

        return server;

    }


    private static class ClientFilter extends BaseFilter {
        private final static Logger logger = Grizzly.logger(ClientFilter.class);

        private final FutureImpl<HttpContent> testFuture;

        // -------------------------------------------------------- Constructors


        public ClientFilter(FutureImpl<HttpContent> testFuture) {

            this.testFuture = testFuture;

        }


        // ------------------------------------------------- Methods from Filter

        @Override
        public NextAction handleRead(FilterChainContext ctx)
                throws IOException {

            // Cast message to a HttpContent
            final HttpContent httpContent = ctx.getMessage();

            logger.log(Level.FINE, "Got HTTP response chunk");

            // Get HttpContent's Buffer
            final Buffer buffer = httpContent.getContent();

            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, "HTTP content size: {0}", buffer.remaining());
            }

            if (!httpContent.isLast()) {
                return ctx.getStopAction(httpContent);
            }

            testFuture.result(httpContent);

            return ctx.getStopAction();
        }

        @Override
        public NextAction handleClose(FilterChainContext ctx)
                throws IOException {
            close();
            return ctx.getStopAction();
        }

        private void close() throws IOException {

            if (!testFuture.isDone()) {
                //noinspection ThrowableInstanceNeverThrown
                testFuture.failure(new IOException("Connection was closed"));
            }

        }

    } // END ClientFilter

    private static class HeaderTestClientFilter extends BaseFilter {
        private final static Logger logger = Grizzly.logger(ClientFilter.class);

        private final FutureImpl<HttpHeader> testFuture;

        // -------------------------------------------------------- Constructors


        public HeaderTestClientFilter(FutureImpl<HttpHeader> testFuture) {

            this.testFuture = testFuture;

        }


        // ------------------------------------------------- Methods from Filter

        @Override
        public NextAction handleRead(FilterChainContext ctx)
                throws IOException {

            // Cast message to a HttpContent
            final HttpContent httpContent = ctx.getMessage();

            logger.log(Level.FINE, "Got HTTP response chunk");

            // Get HttpContent's Buffer
            final Buffer buffer = httpContent.getContent();

            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, "HTTP content size: {0}", buffer.remaining());
            }

            testFuture.result(httpContent.getHttpHeader());

            return ctx.getStopAction();
        }

        @Override
        public NextAction handleClose(FilterChainContext ctx)
                throws IOException {
            close();
            return ctx.getStopAction();
        }

        private void close() throws IOException {

            if (!testFuture.isDone()) {
                //noinspection ThrowableInstanceNeverThrown
                testFuture.failure(new IOException("Connection was closed"));
            }

        }

    } // END ClientFilter

    public class ExplicitContentLengthHandler extends HttpHandler {
        private final int length;

        public ExplicitContentLengthHandler(int length) {
            this.length = length;
        }

        @Override
        public void service(Request request, Response response) throws Exception {
            response.setContentLength(length);
            
            if (isBinary) {
                final byte[] buf = new byte[length];
                for (int i = 0; i < length; i++) {
                    buf[i] = (byte) ('0' + (i % 10));
                }
                
                response.getOutputStream().write(buf);
            } else {
                final StringBuilder sb = new StringBuilder(length);
                for (int i = 0; i < length; i++) {
                    sb.append((char) ('0' + (i % 10)));
                }

                response.getWriter().write(sb.toString());
            }
        }
    }

    public class ExplicitChunkingHandler extends HttpHandler {
        private final int length;

        public ExplicitChunkingHandler(int length) {
            this.length = length;
        }

        @Override
        public void service(Request request, Response response) throws Exception {
            response.getResponse().setChunked(true);

            if (isBinary) {
                final byte[] buf = new byte[length];
                for (int i = 0; i < length; i++) {
                    buf[i] = (byte) ('0' + (i % 10));
                }
                
                response.getOutputStream().write(buf);
            } else {
                final StringBuilder sb = new StringBuilder(length);
                for (int i = 0; i < length; i++) {
                    sb.append((char) ('0' + (i % 10)));
                }

                response.getWriter().write(sb.toString());
            }
        }
    }

    public class AutoTransferEncodingHandler extends HttpHandler {
        private final int length;
        private final boolean isBatch;
        
        public AutoTransferEncodingHandler(int length, boolean isBatch) {
            this.length = length;
            this.isBatch = isBatch;
        }
        
        @Override
        public void service(Request request, Response response) throws Exception {
            if (isBinary) {
                final OutputStream os = response.getOutputStream();
                if (isBatch) {
                    final byte[] buf = new byte[length];
                    for (int i = 0; i < length; i++) {
                        buf[i] = (byte) ('0' + (i % 10));
                    }
                    
                    os.write(buf);
                } else {
                    for (int i = 0; i < length; i++) {
                        os.write(('0' + (i % 10)));
                    }
                }
                
            } else {
                final Writer writer = response.getWriter();
                if (isBatch) {
                    final char[] buf = new char[length];
                    for (int i = 0; i < length; i++) {
                        buf[i] = (char) ('0' + (i % 10));
                    }
                    
                    writer.write(buf);
                } else {
                    for (int i = 0; i < length; i++) {
                        writer.write((char) ('0' + (i % 10)));
                    }
                }
            }
        }
    }

    public static class EchoHandler extends HttpHandler {
        @Override
        public void service(final Request request, final Response response)
                throws Exception {
            response.setContentLengthLong(request.getContentLengthLong());
            response.flush();

            response.suspend();

            final NIOInputStream inputStream = request.getNIOInputStream();
            final NIOOutputStream outputStream = response.getNIOOutputStream();
            
            inputStream.notifyAvailable(new ReadHandler() {

                @Override
                public void onDataAvailable() throws IOException {
                    echo();
                }

                @Override
                public void onAllDataRead() throws IOException {
                    echo();
                    response.resume();
                }

                @Override
                public void onError(Throwable t) {
                }

                private void echo() throws IOException {
                    final int availabe = inputStream.available();
                    final byte[] buf = new byte[availabe];
                    inputStream.read(buf);
                    outputStream.write(buf);
                    outputStream.flush();
                }

            });
        }
    }

}
