/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2010 Sun Microsystems, Inc. All rights reserved.
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
 */

package com.sun.grizzly.web;

import com.sun.grizzly.Buffer;
import com.sun.grizzly.Connection;
import com.sun.grizzly.Grizzly;
import com.sun.grizzly.TransportFactory;
import com.sun.grizzly.filterchain.BaseFilter;
import com.sun.grizzly.filterchain.FilterChainBuilder;
import com.sun.grizzly.filterchain.FilterChainContext;
import com.sun.grizzly.filterchain.NextAction;
import com.sun.grizzly.filterchain.TransportFilter;
import com.sun.grizzly.http.HttpClientFilter;
import com.sun.grizzly.http.HttpCodecFilter;
import com.sun.grizzly.http.HttpContent;
import com.sun.grizzly.http.HttpPacket;
import com.sun.grizzly.http.HttpRequestPacket;
import com.sun.grizzly.http.server.GrizzlyAdapter;
import com.sun.grizzly.http.server.GrizzlyListener;
import com.sun.grizzly.http.server.GrizzlyRequest;
import com.sun.grizzly.http.server.GrizzlyResponse;
import com.sun.grizzly.http.server.GrizzlyWebServer;
import com.sun.grizzly.http.server.io.DataHandler;
import com.sun.grizzly.http.server.io.GrizzlyInputStream;
import com.sun.grizzly.http.server.io.GrizzlyReader;
import com.sun.grizzly.impl.FutureImpl;
import com.sun.grizzly.impl.SafeFutureImpl;
import com.sun.grizzly.memory.ByteBuffersBuffer;
import com.sun.grizzly.memory.MemoryManager;
import com.sun.grizzly.memory.MemoryUtils;
import com.sun.grizzly.nio.transport.TCPNIOTransport;
import com.sun.grizzly.utils.ChunkingFilter;
import junit.framework.TestCase;

import java.io.IOException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Test case to exercise <code>AsyncStreamReader</code>.
 */
public class AsyncInputSourcesTest extends TestCase {

    private static final char[] ALPHA = "abcdefghijklmnopqrstuvwxyz".toCharArray();
    private static final int PORT = 8030;

    // ------------------------------------------------------------ Test Methods


    /*
     * <em>POST</em> a message body with a length of 5000 bytes.
     */
    public void testBasicAsyncRead() throws Throwable {

        final FutureImpl<String> testResult = SafeFutureImpl.create();
        final GrizzlyAdapter adapter = new EchoAdapter(testResult, 0);
        final String expected = buildString(5000);
        final HttpPacket request = createRequest("POST", expected);
        doTest(adapter, request, expected, testResult, null, 10);
        
    }


    /*
     * <em>POST</em> a message body with a length of 5000 bytes.
     * Adapter calls {@link AsyncStreamReader#
     */
    public void testBasicAsyncReadSpecifiedSize() throws Throwable {

        final FutureImpl<String> testResult = SafeFutureImpl.create();
        final GrizzlyAdapter adapter = new EchoAdapter(testResult, 1000);
        final String expected = buildString(5000);
        final HttpPacket request = createRequest("POST", expected);
        doTest(adapter, request, expected, testResult, null, 10);

    }


    public void testBasicAsyncReadSlowClient() throws Throwable {

        final FutureImpl<String> testResult = SafeFutureImpl.create();
        final GrizzlyAdapter adapter = new EchoAdapter(testResult, 0);
        final String expected = buildString(5000);
        final HttpPacket request = createRequest("POST", expected);
        final WriteStrategy strategy = new WriteStrategy() {
            @Override
            public void doWrite(FilterChainContext ctx) throws IOException {

                HttpRequestPacket.Builder b = HttpRequestPacket.builder();
                b.method("POST").protocol(HttpCodecFilter.HTTP_1_1).uri("/path").chunked(false).header("Host", "localhost:" + PORT);
                b.contentLength(expected.length());
                HttpRequestPacket request = b.build();
                ctx.write(request);
                MemoryManager mm = ctx.getConnection().getTransport().getMemoryManager();

                for (int i = 0, count = (5000 / 1000); i < count; i++) {
                    int start = 0;
                    if (i != 0) {
                        start = i * 1000;
                    }
                    int end = start + 1000;
                    String content = expected.substring(start, end);
                    Buffer buf = mm.allocate(content.length());
                    buf.put(content.getBytes());
                    buf.flip();
                    HttpContent.Builder cb = request.httpContentBuilder();
                    cb.content(buf);
                    HttpContent ct = cb.build();
                    ctx.write(ct);
                    try {
                        Thread.sleep(3000);
                    } catch (InterruptedException ie) {
                        ie.printStackTrace();
                        testResult.failure(ie);
                        break;
                    }
                }
            }
        };
        doTest(adapter, request, expected, testResult, strategy, 30);
        
    }

    public void testBasicAsyncReadSpecifiedSizeSlowClient() throws Throwable {

        final FutureImpl<String> testResult = SafeFutureImpl.create();
        final GrizzlyAdapter adapter = new EchoAdapter(testResult, 2000);
        final String expected = buildString(5000);
        final HttpPacket request = createRequest("POST", expected);
        final WriteStrategy strategy = new WriteStrategy() {
            @Override
            public void doWrite(FilterChainContext ctx) throws IOException {

                HttpRequestPacket.Builder b = HttpRequestPacket.builder();
                b.method("POST").protocol(HttpCodecFilter.HTTP_1_1).uri("/path").chunked(false).header("Host", "localhost:" + PORT);
                b.contentLength(expected.length());
                HttpRequestPacket request = b.build();
                ctx.write(request);
                MemoryManager mm = ctx.getConnection().getTransport().getMemoryManager();

                for (int i = 0, count = (5000 / 1000); i < count; i++) {
                    int start = 0;
                    if (i != 0) {
                        start = i * 1000;
                    }
                    int end = start + 1000;
                    String content = expected.substring(start, end);
                    Buffer buf = mm.allocate(content.length());
                    buf.put(content.getBytes());
                    buf.flip();
                    HttpContent.Builder cb = request.httpContentBuilder();
                    cb.content(buf);
                    HttpContent ct = cb.build();
                    ctx.write(ct);
                    try {
                        Thread.sleep(3000);
                    } catch (InterruptedException ie) {
                        ie.printStackTrace();
                        testResult.failure(ie);
                        break;
                    }
                }
            }
        };
        doTest(adapter, request, expected, testResult, strategy, 30);

    }


    /*
     * <em>POST</em> a message body with a length of 5000 bytes.
     */
    public void testBasicAsyncReadChar() throws Throwable {

        final FutureImpl<String> testResult = SafeFutureImpl.create();
        final GrizzlyAdapter adapter = new CharacterEchoAdapter(testResult, 0);
        final String expected = buildString(5000);
        final HttpPacket request = createRequest("POST", expected);
        doTest(adapter, request, expected, testResult, null, 30);

    }


    /*
     * <em>POST</em> a message body with a length of 5000 bytes.
     * Adapter calls {@link AsyncStreamReader#
     */
    public void testBasicAsyncReadCharSpecifiedSize() throws Throwable {

        final FutureImpl<String> testResult = SafeFutureImpl.create();
        final GrizzlyAdapter adapter = new CharacterEchoAdapter(testResult, 1000);
        final String expected = buildString(5000);
        final HttpPacket request = createRequest("POST", expected);
        doTest(adapter, request, expected, testResult, null, 10);

    }


    public void testBasicAsyncReadCharSlowClient() throws Throwable {

        final FutureImpl<String> testResult = SafeFutureImpl.create();
        final GrizzlyAdapter adapter = new EchoAdapter(testResult, 0);
        final String expected = buildString(5000);
        final HttpPacket request = createRequest("POST", expected);
        final WriteStrategy strategy = new WriteStrategy() {
            @Override
            public void doWrite(FilterChainContext ctx) throws IOException {

                HttpRequestPacket.Builder b = HttpRequestPacket.builder();
                b.method("POST").protocol(HttpCodecFilter.HTTP_1_1).uri("/path").chunked(false).header("Host", "localhost:" + PORT);
                b.contentLength(expected.length());
                HttpRequestPacket request = b.build();
                ctx.write(request);
                MemoryManager mm = ctx.getConnection().getTransport().getMemoryManager();

                for (int i = 0, count = (5000 / 1000); i < count; i++) {
                    int start = 0;
                    if (i != 0) {
                        start = i * 1000;
                    }
                    int end = start + 1000;
                    String content = expected.substring(start, end);
                    Buffer buf = mm.allocate(content.length());
                    buf.put(content.getBytes());
                    buf.flip();
                    HttpContent.Builder cb = request.httpContentBuilder();
                    cb.content(buf);
                    HttpContent ct = cb.build();
                    ctx.write(ct);
                    try {
                        Thread.sleep(3000);
                    } catch (InterruptedException ie) {
                        ie.printStackTrace();
                        testResult.failure(ie);
                        break;
                    }
                }
            }
        };
        doTest(adapter, request, expected, testResult, strategy, 30);

    }

    public void testBasicAsyncReadcharSpecifiedSizeSlowClient() throws Throwable {

        final FutureImpl<String> testResult = SafeFutureImpl.create();
        final GrizzlyAdapter adapter = new EchoAdapter(testResult, 2000);
        final String expected = buildString(5000);
        final HttpPacket request = createRequest("POST", expected);
        final WriteStrategy strategy = new WriteStrategy() {
            @Override
            public void doWrite(FilterChainContext ctx) throws IOException {

                HttpRequestPacket.Builder b = HttpRequestPacket.builder();
                b.method("POST").protocol(HttpCodecFilter.HTTP_1_1).uri("/path").chunked(false).header("Host", "localhost:" + PORT);
                b.contentLength(expected.length());
                HttpRequestPacket request = b.build();
                ctx.write(request);
                MemoryManager mm = ctx.getConnection().getTransport().getMemoryManager();

                for (int i = 0, count = (5000 / 1000); i < count; i++) {
                    int start = 0;
                    if (i != 0) {
                        start = i * 1000;
                    }
                    int end = start + 1000;
                    String content = expected.substring(start, end);
                    Buffer buf = mm.allocate(content.length());
                    buf.put(content.getBytes());
                    buf.flip();
                    HttpContent.Builder cb = request.httpContentBuilder();
                    cb.content(buf);
                    HttpContent ct = cb.build();
                    ctx.write(ct);
                    try {
                        Thread.sleep(3000);
                    } catch (InterruptedException ie) {
                        ie.printStackTrace();
                        testResult.failure(ie);
                        break;
                    }
                }
            }
        };
        doTest(adapter, request, expected, testResult, strategy, 30);

    }


    // --------------------------------------------------------- Private Methods


    private GrizzlyWebServer createWebServer(final GrizzlyAdapter adapter) {

        final GrizzlyWebServer server = new GrizzlyWebServer();
        final GrizzlyListener listener =
                new GrizzlyListener("grizzly",
                        GrizzlyListener.DEFAULT_NETWORK_HOST,
                        PORT);
        listener.setKeepAliveTimeoutInSeconds(0);
        server.addListener(listener);
        server.getServerConfiguration().addGrizzlyAdapter(adapter, "/path/*");

        return server;

    }


    private void doTest(final GrizzlyAdapter adapter,
                        final HttpPacket request,
                        final String expectedResult,
                        final FutureImpl<String> testResult,
                        final WriteStrategy strategy,
                        final int timeout)
            throws Exception {

        final TCPNIOTransport clientTransport =
                TransportFactory.getInstance().createTCPTransport();
        final GrizzlyWebServer server = createWebServer(adapter);
        try {
            server.start();
            FilterChainBuilder clientFilterChainBuilder = FilterChainBuilder.stateless();
            clientFilterChainBuilder.add(new TransportFilter());
            clientFilterChainBuilder.add(new ChunkingFilter(128));
            clientFilterChainBuilder.add(new HttpClientFilter());
            clientFilterChainBuilder.add(new ClientFilter(testResult,
                                                          request,
                                                          strategy));
            clientTransport.setProcessor(clientFilterChainBuilder.build());

            clientTransport.start();

            Future<Connection> connectFuture = clientTransport.connect("localhost", PORT);
            Connection connection = null;
            try {
                connection = connectFuture.get(timeout, TimeUnit.SECONDS);
                String res = testResult.get(timeout, TimeUnit.SECONDS);
                if (res != null) {
                    assertEquals("Expected a return content length of " + expectedResult.length() + ", received: " + res.length(),
                            expectedResult.length(),
                            res.length());
                    assertEquals(expectedResult, res);
                } else {
                    fail("No response content available.");
                }
            } finally {
                // Close the client connection
                if (connection != null) {
                    connection.close();
                }
            }
        } finally {
            clientTransport.stop();
            server.stop();
        }
    }


    private String buildString(int len) {

        final StringBuilder sb = new StringBuilder(len);
        for (int i = 0, j = 0; i < len; i++, j++) {
            if (j > 25) {
                j = 0;
            }
            sb.append(ALPHA[j]);
        }
        return sb.toString();

    }


    @SuppressWarnings({"unchecked"})
    private HttpPacket createRequest(final String method,
                                     final String content) {
        final Buffer contentBuffer = content != null ?
                MemoryUtils.wrap(TransportFactory.getInstance().getDefaultMemoryManager(), content) :
                null;

        HttpRequestPacket.Builder b = HttpRequestPacket.builder();
        b.method(method).protocol(HttpCodecFilter.HTTP_1_1).uri("/path").chunked(false).header("Host", "localhost:" + PORT);
        if (content != null) {
            b.contentLength(contentBuffer.remaining());
        }

        HttpRequestPacket request = b.build();

        if (content != null) {
            HttpContent.Builder cb = request.httpContentBuilder();
            cb.content(contentBuffer);
            return cb.build();
        }

        return request;
    }


    // ---------------------------------------------------------- Nested Classes

    private static interface WriteStrategy {

        void doWrite(FilterChainContext ctx) throws IOException;

    } // END WriteStrategy


    private static class EchoAdapter extends GrizzlyAdapter {

        private final FutureImpl<String> testResult;
        private final int readSize;


        // -------------------------------------------------------- Constructors


        EchoAdapter(final FutureImpl<String> testResult, final int readSize) {

            this.testResult = testResult;
            this.readSize = readSize;

        }


        // ----------------------------------------- Methods from GrizzlyAdapter

        @Override
        public void service(final GrizzlyRequest req,
                            final GrizzlyResponse res)
                throws Exception {

            try {
                final GrizzlyInputStream reader = req.getInputStream(false);
                int available = reader.available();
                if (available > 0) {
                    byte[] b = new byte[available];
                    reader.readByteArray(b);
                    res.getOutputStream().write(b);
                }
                if (reader.isFinished()) {
                    return;
                }
                final StringBuilder sb = new StringBuilder();
                reader.notifyAvailable(new DataHandler() {

                    @Override
                    public void onDataAvailable() {
                        try {
                            buffer(reader, sb);
                        } catch (IOException ioe) {
                            testResult.failure(ioe);
                        }
                        //reader.notifyAvailable(this, readSize);
                    }


                    @Override
                    public void onError(Throwable t) {

                        testResult.failure(t);
                        res.resume();

                    }


                    @Override
                    public void onAllDataRead() {
                        try {
                            buffer(reader, sb);
                        } catch (IOException ioe) {
                            testResult.failure(ioe);
                        }
                        try {
                            res.getOutputStream().write(sb.toString().getBytes());
                        } catch (Exception e) {
                            testResult.failure(e);
                        }
                        res.resume();

                    }
                }, readSize);
                res.suspend();
            } catch (Throwable t) {
                testResult.failure(t);
            }

        }

        private static void buffer(GrizzlyInputStream reader, StringBuilder sb) throws IOException {
            byte[] b = new byte[reader.available()];
            try {
                reader.readByteArray(b);
            } catch (IOException ioe) {
                throw new RuntimeException(ioe);
            }
            try {
                sb.append(new String(b));
            } catch (Throwable ioe) {
                throw new RuntimeException(ioe);
            }
        }

    } // END EchoAdapter


    private static class CharacterEchoAdapter extends GrizzlyAdapter {

        private final FutureImpl<String> testResult;
        private final int readSize;


        // -------------------------------------------------------- Constructors


        CharacterEchoAdapter(final FutureImpl<String> testResult,
                             final int readSize) {

            this.testResult = testResult;
            this.readSize = readSize;

        }


        // ----------------------------------------- Methods from GrizzlyAdapter

        @Override
        public void service(final GrizzlyRequest req,
                            final GrizzlyResponse res)
                throws Exception {

            try {
                final GrizzlyReader reader = req.getReader(false);
                int available = reader.available();
                if (available > 0) {
                    char[] b = new char[available];
                    reader.readCharArray(b);
                    res.getWriter().write(b);
                }
                if (reader.isFinished()) {
                    return;
                }
                final StringBuilder sb = new StringBuilder();
                reader.notifyAvailable(new DataHandler() {

                    @Override
                    public void onDataAvailable() {
                        try {
                            buffer(reader, sb);
                        } catch (IOException ioe) {
                            testResult.failure(ioe);
                        }
                        //reader.notifyAvailable(this, readSize);
                    }


                    @Override
                    public void onError(Throwable t) {

                        testResult.failure(t);
                        res.resume();

                    }


                    @Override
                    public void onAllDataRead() {
                        try {
                            buffer(reader, sb);
                        } catch (IOException ioe) {
                            testResult.failure(ioe);
                        }
                        try {
                            res.getWriter().write(sb.toString());
                        } catch (Exception e) {
                            testResult.failure(e);
                        }
                        res.resume();

                    }
                }, readSize);
                res.suspend();
            } catch (Throwable t) {
                testResult.failure(t);
            }

        }

        private static void buffer(GrizzlyReader reader, StringBuilder sb) throws IOException {
            char[] c = new char[reader.available()];
            try {
                reader.readCharArray(c);
            } catch (IOException ioe) {
                throw new RuntimeException(ioe);
            }
            try {
                sb.append(new String(c));
            } catch (Throwable ioe) {
                throw new RuntimeException(ioe);
            }
        }

    } // END CharacterEchoAdapter


    private static class ClientFilter extends BaseFilter {
        private final static Logger logger = Grizzly.logger(ClientFilter.class);

        private ByteBuffersBuffer buf = ByteBuffersBuffer.create();

        private FutureImpl<String> testFuture;

        // number of bytes downloaded
        private volatile int bytesDownloaded;

        private final HttpPacket request;

        private final WriteStrategy strategy;


        // -------------------------------------------------------- Constructors


        public ClientFilter(FutureImpl<String> testFuture,
                            HttpPacket request,
                            WriteStrategy strategy) {

            this.testFuture = testFuture;
            this.request = request;
            this.strategy = strategy;

        }


        // ------------------------------------------------- Methods from Filter


        @Override
        public NextAction handleConnect(FilterChainContext ctx)
                throws IOException {

            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE,
                        "Connected... Sending the request: " + request);
            }

            if (strategy == null) {
                // Write the request asynchronously
                ctx.write(request);
            } else {
                strategy.doWrite(ctx);
            }

            // Return the stop action, which means we don't expect next filter to process
            // connect event
            return ctx.getStopAction();
        }


        @Override
        public NextAction handleRead(FilterChainContext ctx)
                throws IOException {
            try {
                // Cast message to a HttpContent
                final HttpContent httpContent = (HttpContent) ctx.getMessage();

                logger.log(Level.FINE, "Got HTTP response chunk");

                // Get HttpContent's Buffer
                final Buffer buffer = httpContent.getContent();

                if (logger.isLoggable(Level.FINE)) {
                    logger.log(Level.FINE,
                            "HTTP content size: " + buffer.remaining());
                }
                if (buffer.remaining() > 0) {
                    bytesDownloaded += buffer.remaining();

                    buf.append(buffer);

                }

                if (httpContent.isLast()) {
                    if (logger.isLoggable(Level.FINE)) {
                        logger.log(Level.FINE,
                                "Response complete: "
                                        + bytesDownloaded
                                        + " bytes");
                    }
                    testFuture.result(buf.toStringContent());
                    close();
                }
            } catch (IOException e) {
                close();
            }

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

}
