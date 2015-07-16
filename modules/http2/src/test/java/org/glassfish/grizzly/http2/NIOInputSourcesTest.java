/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2015 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.http2;

import java.io.EOFException;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.ReadHandler;
import org.glassfish.grizzly.Transport;
import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.http.HttpContent;
import org.glassfish.grizzly.http.HttpHeader;
import org.glassfish.grizzly.http.HttpPacket;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.http.Protocol;
import org.glassfish.grizzly.http.io.NIOInputStream;
import org.glassfish.grizzly.http.io.NIOOutputStream;
import org.glassfish.grizzly.http.io.NIOReader;
import org.glassfish.grizzly.impl.FutureImpl;
import org.glassfish.grizzly.impl.SafeFutureImpl;
import org.glassfish.grizzly.memory.ByteBufferManager;
import org.glassfish.grizzly.memory.CompositeBuffer;
import org.glassfish.grizzly.memory.MemoryManager;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import org.glassfish.grizzly.nio.transport.TCPNIOTransportBuilder;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.grizzly.EmptyCompletionHandler;
import org.glassfish.grizzly.WriteResult;
import org.glassfish.grizzly.filterchain.FilterChain;
import org.glassfish.grizzly.http.server.HttpHandler;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.grizzly.http.server.NetworkListener;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.http.server.Response;
import org.glassfish.grizzly.http.util.Header;
import org.glassfish.grizzly.memory.Buffers;
import org.glassfish.grizzly.memory.ByteBufferWrapper;
import org.glassfish.grizzly.threadpool.GrizzlyExecutorService;
import org.glassfish.grizzly.utils.Exceptions;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import static org.junit.Assert.*;

/**
 * Test case to exercise <code>AsyncStreamReader</code>.
 */
@RunWith(Parameterized.class)
public class NIOInputSourcesTest extends AbstractHttp2Test {

    private static final char[] ALPHA = "abcdefghijklmnopqrstuvwxyz".toCharArray();
    private static final int PORT = 18301;

    private final boolean isSecure;
    
    public NIOInputSourcesTest(final boolean isSecure) {
        this.isSecure = isSecure;
    }

    @Parameterized.Parameters
    public static Collection<Object[]> isSecure() {
        return AbstractHttp2Test.isSecure();
    }
    
    @Before
    public void setUp() throws Exception {
        ByteBufferWrapper.DEBUG_MODE = true;
    }

    // ------------------------------------------------------------ Test Methods


    /*
     * <em>POST</em> a message body with a length of 5000 bytes.
     */
    @Test
    public void testBasicAsyncRead() throws Throwable {

        final FutureImpl<String> testResult = SafeFutureImpl.create();
        final EchoHandler httpHandler = new EchoHttpHandler(testResult, 1);
        final String expected = buildString(5000);
        final HttpPacket request = createRequest(PORT, "POST", expected, null);
        doTest(httpHandler, request, expected, testResult, null, 20);
        
    }


    /*
     * <em>POST</em> a message body with a length of 5000 bytes.
     * HttpHandler calls {@link AsyncStreamReader#
     */
    @Test
    public void testBasicAsyncReadSpecifiedSize() throws Throwable {

        final FutureImpl<String> testResult = SafeFutureImpl.create();
        final EchoHandler httpHandler = new EchoHttpHandler(testResult, 1000);
        final String expected = buildString(5000);
        final HttpPacket request = createRequest(PORT, "POST", expected, null);
        doTest(httpHandler, request, expected, testResult, null, 20);

    }


    @Test
    public void testBasicAsyncReadSlowClient() throws Throwable {

        final FutureImpl<String> testResult = SafeFutureImpl.create();
        final EchoHandler httpHandler = new EchoHttpHandler(testResult, 1);
        final String expected = buildString(5000);

        final HttpRequestPacket.Builder b = HttpRequestPacket.builder();
        b.method("POST").protocol(Protocol.HTTP_1_1).uri("/path").chunked(false).header("Host", "localhost:" + PORT);
        b.contentLength(expected.length());
        final HttpRequestPacket request = b.build();

        final WriteStrategy strategy = new WriteStrategy() {
            @Override
            public void doWrite(FilterChainContext ctx) throws IOException {

                ctx.write(request);
                MemoryManager mm = ctx.getMemoryManager();

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
                        Thread.sleep(300);
                    } catch (InterruptedException ie) {
                        ie.printStackTrace();
                        testResult.failure(ie);
                        break;
                    }
                }
            }
        };
        doTest(httpHandler, request, expected, testResult, strategy, 60);
        
    }

    @Test
    public void testBasicAsyncReadSpecifiedSizeSlowClient() throws Throwable {

        final FutureImpl<String> testResult = SafeFutureImpl.create();
        final EchoHandler httpHandler = new EchoHttpHandler(testResult, 2000);
        final String expected = buildString(5000);

        final HttpRequestPacket.Builder b = HttpRequestPacket.builder();
        b.method("POST").protocol(Protocol.HTTP_1_1).uri("/path").chunked(false).header("Host", "localhost:" + PORT);
        b.contentLength(expected.length());
        final HttpRequestPacket request = b.build();

        final WriteStrategy strategy = new WriteStrategy() {
            @Override
            public void doWrite(FilterChainContext ctx) throws IOException {

                ctx.write(request);
                MemoryManager mm = ctx.getMemoryManager();

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
                        Thread.sleep(300);
                    } catch (InterruptedException ie) {
                        ie.printStackTrace();
                        testResult.failure(ie);
                        break;
                    }
                }
            }
        };
        doTest(httpHandler, request, expected, testResult, strategy, 60);

    }

    @Test
    public void testDirectAsyncReadSpecifiedSizeSlowClient() throws Throwable {

        final FutureImpl<String> testResult = SafeFutureImpl.create();
        final EchoHandler httpHandler = new DirectBufferEchoHttpHandler(testResult, 2000);
        final String expected = buildString(5000);

        final HttpRequestPacket.Builder b = HttpRequestPacket.builder();
        b.method("POST").protocol(Protocol.HTTP_1_1).uri("/path").chunked(false).header("Host", "localhost:" + PORT);
        b.contentLength(expected.length());
        final HttpRequestPacket request = b.build();

        final WriteStrategy strategy = new WriteStrategy() {
            @Override
            public void doWrite(FilterChainContext ctx) throws IOException {

                ctx.write(request);
                MemoryManager mm = ctx.getMemoryManager();

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
                        Thread.sleep(300);
                    } catch (InterruptedException ie) {
                        ie.printStackTrace();
                        testResult.failure(ie);
                        break;
                    }
                }
            }
        };

        HttpServer httpServer = createWebServer(httpHandler);

        Transport transport = httpServer.getListeners().iterator().next().getTransport();
        final ByteBufferManager memoryManager = new ByteBufferManager();
        memoryManager.setDirect(true);

        transport.setMemoryManager(memoryManager);
        doTest(httpServer, httpHandler, request, expected, testResult, strategy, 60);
    }
    
    @Test
    public void testAsyncReadStartedOutsideHttpHandler() throws Throwable {

        final ExecutorService threadPool = GrizzlyExecutorService.createInstance();
        try {
            final FutureImpl<String> testResult = SafeFutureImpl.create();
            final EchoHandler httpHandler = new EchoHttpHandler2(testResult, 1, threadPool);
            final String expected = buildString(5000);

            final HttpRequestPacket.Builder b = HttpRequestPacket.builder();
            b.method("POST").protocol(Protocol.HTTP_1_1).uri("/path").chunked(false).header("Host", "localhost:" + PORT);
            b.contentLength(expected.length());
            final HttpRequestPacket request = b.build();

            final WriteStrategy strategy = new WriteStrategy() {
                @Override
                public void doWrite(FilterChainContext ctx) throws IOException {

                    ctx.write(request);
                    MemoryManager mm = ctx.getMemoryManager();

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
                            Thread.sleep(300);
                        } catch (InterruptedException ie) {
                            ie.printStackTrace();
                            testResult.failure(ie);
                            break;
                        }
                    }
                }
            };
            doTest(httpHandler, request, expected, testResult, strategy, 60);
        } finally {
            threadPool.shutdownNow();
        }
        
    }
    
    /*
     * <em>POST</em> a message body with a length of 5000 bytes.
     */
    @Test
    public void testBasicAsyncReadChar() throws Throwable {

        final FutureImpl<String> testResult = SafeFutureImpl.create();
        final EchoHandler httpHandler = new CharacterEchoHttpHandler(testResult, 1, null);
        final String expected = buildString(5000);
        final HttpPacket request = createRequest(PORT, "POST", expected, null);
        doTest(httpHandler, request, expected, testResult, null, 60);

    }


    /*
     * <em>POST</em> a message body with a length of 5000 bytes.
     */
    @Test
    public void testBasicAsyncReadMultiByteChar() throws Throwable {

        final FutureImpl<String> testResult = SafeFutureImpl.create();
        final String encoding = "UTF-16";
        final EchoHandler httpHandler = new CharacterEchoHttpHandler(testResult, 1, encoding);
        final String expected = buildString(5000);
        final HttpPacket request = createRequest(PORT, "POST", expected, encoding);
        ClientFilter filter = new ClientFilter(testResult, request, null);
        doTest(httpHandler, expected, testResult, filter, 60);

    }


    /*
     * <em>POST</em> a message body with a length of 5000 bytes.
     * HttpHandler calls {@link AsyncStreamReader#
     */
    @Test
    public void testBasicAsyncReadCharSpecifiedSize() throws Throwable {

        final FutureImpl<String> testResult = SafeFutureImpl.create();
        final EchoHandler httpHandler = new CharacterEchoHttpHandler(testResult, 1000, null);
        final String expected = buildString(5000);
        final HttpPacket request = createRequest(PORT, "POST", expected, null);
        doTest(httpHandler, request, expected, testResult, null, 20);

    }


    @Test
    public void testBasicAsyncReadCharSlowClient() throws Throwable {

        final FutureImpl<String> testResult = SafeFutureImpl.create();
        final EchoHandler httpHandler = new CharacterEchoHttpHandler(testResult, 1, null);
        final String expected = buildString(5000);

        final HttpRequestPacket.Builder b = HttpRequestPacket.builder();
        b.method("POST").protocol(Protocol.HTTP_1_1).uri("/path").chunked(false).header("Host", "localhost:" + PORT);
        b.contentLength(expected.length());
        final HttpRequestPacket request = b.build();


        final WriteStrategy strategy = new WriteStrategy() {
            @Override
            public void doWrite(FilterChainContext ctx) throws IOException {

                ctx.write(request);
                MemoryManager mm = ctx.getMemoryManager();

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
                        Thread.sleep(300);
                    } catch (InterruptedException ie) {
                        ie.printStackTrace();
                        testResult.failure(ie);
                        break;
                    }
                }
            }
        };
        doTest(httpHandler, request, expected, testResult, strategy, 60);

    }

    @Test
    public void testBasicAsyncReadCharSpecifiedSizeSlowClient() throws Throwable {

        final FutureImpl<String> testResult = SafeFutureImpl.create();
        final EchoHandler httpHandler = new CharacterEchoHttpHandler(testResult, 2000, null);
        final String expected = buildString(5000);

        final HttpRequestPacket.Builder b = HttpRequestPacket.builder();
        b.method("POST").protocol(Protocol.HTTP_1_1).uri("/path").chunked(false).header("Host", "localhost:" + PORT);
        b.contentLength(expected.length());
        final HttpRequestPacket request = b.build();

        final WriteStrategy strategy = new WriteStrategy() {
            @Override
            public void doWrite(FilterChainContext ctx) throws IOException {

                ctx.write(request);
                MemoryManager mm = ctx.getMemoryManager();

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
                        Thread.sleep(300);
                    } catch (InterruptedException ie) {
                        ie.printStackTrace();
                        testResult.failure(ie);
                        break;
                    }
                }
            }
        };
        doTest(httpHandler, request, expected, testResult, strategy, 60);

    }

    /**
     * Test ReadHandler.onError to be notified, when client unexpectedly
     * terminates the connection
     */
    @SuppressWarnings({"unchecked"})
    @Test
    public void testDisconnect() throws Throwable {

        final AtomicInteger bytesRead = new AtomicInteger();
        final FutureImpl<Integer> resultFuture = SafeFutureImpl.<Integer>create();
        final CountDownLatch chunkReceivedLatch = new CountDownLatch(1);
        final TCPNIOTransport clientTransport = TCPNIOTransportBuilder.newInstance().build();
        clientTransport.setProcessor(createClientFilterChain(isSecure));
        
        final HttpHandler httpHandler = new HttpHandler() {

            @Override
            public void service(final Request request,
                    final Response response) throws Exception {
                response.suspend();
                final NIOInputStream inputStream = (NIOInputStream) request.getInputStream();

                inputStream.notifyAvailable(new ReadHandler() {

                    @Override
                    public void onDataAvailable() throws IOException {
                        chunkReceivedLatch.countDown();
                        
                        final int readyData = inputStream.readyData();
                        inputStream.skip(readyData);
                        bytesRead.addAndGet(readyData);

                        inputStream.notifyAvailable(this);
                    }


                    @Override
                    public void onAllDataRead() throws IOException {
                        final int readyData = inputStream.readyData();
                        inputStream.skip(readyData);
                        bytesRead.addAndGet(readyData);
                        resultFuture.failure(new IllegalStateException("Connection should have been terminated"));

                        response.resume();
                    }

                    @Override
                    public void onError(Throwable t) {
                        resultFuture.failure(t);
                        
                        response.resume();
                    }
                });
            }

        };

        final HttpServer server = createWebServer(httpHandler);

        try {
            server.start();
            clientTransport.start();

            Future<Connection> connectFuture = clientTransport.connect("localhost", PORT);
            Connection connection = null;
            try {
                connection = connectFuture.get(10, TimeUnit.SECONDS);
                HttpRequestPacket packet = (HttpRequestPacket) createRequest(PORT, "POST", null, null);
                packet.setContentLength(5000);
                connection.write(packet);

                HttpContent content = HttpContent.builder(packet).content(
                        Buffers.wrap(null, buildString(2500))).build();

                connection.write(content, new EmptyCompletionHandler<WriteResult>() {

                    @Override
                    public void completed(final WriteResult result) {
                        final Connection c = result.getConnection();
                        
                        new Thread() {
                            @Override
                            public void run() {
                                try {
                                    chunkReceivedLatch.await(10, TimeUnit.SECONDS);
                                    c.closeSilently();
                                } catch (InterruptedException e) {
                                }
                            }
                        }.start();
                    }
                });

                try {
                    final Integer i = resultFuture.get(10, TimeUnit.SECONDS);
                    fail("Wrapped EOFException expected");
                } catch (ExecutionException e) {
                    assertEquals("NOT EOF Exception:\n" +
                            Exceptions.getStackTraceAsString(e.getCause()),
                            EOFException.class, e.getCause().getClass());
                }
            } finally {
                // Close the client connection
                if (connection != null) {
                    connection.closeSilently();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            fail();
        } finally {
            clientTransport.shutdownNow();
            server.shutdownNow();
        }
    }

    // --------------------------------------------------------- Private Methods


    private HttpServer createWebServer(final HttpHandler httpHandler) {
        final HttpServer httpServer = createServer(null, PORT, isSecure,
                HttpHandlerRegistration.of(httpHandler, "/path/*"));
        
        final NetworkListener listener = httpServer.getListener("grizzly");
        listener.getKeepAlive().setIdleTimeoutInSeconds(-1);

        return httpServer;

    }

    private void doTest(final EchoHandler httpHandler,
            final HttpPacket request,
            final String expectedResult,
            final FutureImpl<String> testResult,
            final WriteStrategy strategy,
            final int timeoutSeconds)
            throws Exception {

        doTest(httpHandler,
                expectedResult,
                testResult,
                new ClientFilter(testResult, request, strategy),
                timeoutSeconds);

    }

    private void doTest(final HttpServer httpServer,
            final EchoHandler httpHandler,
            final HttpPacket request,
            final String expectedResult,
            final FutureImpl<String> testResult,
            final WriteStrategy strategy,
            final int timeoutSeconds)
            throws Exception {

        doTest(httpServer,
                httpHandler,
                expectedResult,
                testResult,
                new ClientFilter(testResult, request, strategy),
                timeoutSeconds);

    }

    private void doTest(final EchoHandler httpHandler,
            final String expectedResult,
            final FutureImpl<String> testResult,
            final ClientFilter filter,
            final int timeoutSeconds)
            throws Exception {
        doTest(createWebServer(httpHandler), httpHandler, expectedResult, testResult, filter, timeoutSeconds);
    }

    private void doTest(final HttpServer server,
            final EchoHandler httpHandler,
            final String expectedResult,
            final FutureImpl<String> testResult,
            final ClientFilter filter,
            final int timeoutSeconds)
            throws Exception {
        
        final TCPNIOTransport clientTransport =
                TCPNIOTransportBuilder.newInstance().build();

        try {
            server.start();
            
            FilterChain clientFilterChain =
                    createClientFilterChain(isSecure, filter);
            
            clientTransport.setProcessor(clientFilterChain);
            clientTransport.start();

            Future<Connection> connectFuture = clientTransport.connect("localhost", PORT);
            Connection connection = null;
            try {
                connection = connectFuture.get(timeoutSeconds, TimeUnit.SECONDS);
                String res = testResult.get(timeoutSeconds, TimeUnit.SECONDS);
                if (res != null) {
                    assertEquals("Expected a return content length of " + expectedResult.length() + ", received: " + res.length(),
                            expectedResult.length(),
                            res.length());
                    assertEquals("Server echoed string=" + httpHandler.getEchoedString(), expectedResult, res);
                } else {
                    fail("No response content available.");
                }
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

    // ---------------------------------------------------------- Nested Classes

    private static interface WriteStrategy {

        void doWrite(FilterChainContext ctx) throws IOException;

    } // END WriteStrategy


    private static class EchoHttpHandler extends EchoHandler {

        private final FutureImpl<String> testResult;
        private final int readSize;

        private final StringBuffer echoedString = new StringBuffer();

        // -------------------------------------------------------- Constructors


        EchoHttpHandler(final FutureImpl<String> testResult, final int readSize) {

            this.testResult = testResult;
            this.readSize = readSize;

        }


        // ----------------------------------------- Methods from HttpHandler

        @Override
        public void service(final Request req,
                            final Response res)
                throws Exception {

            try {
                final NIOInputStream reader = req.getNIOInputStream();
                final NIOOutputStream writer = res.getNIOOutputStream();

                res.suspend();

                reader.notifyAvailable(new ReadHandler() {

                    @Override
                    public void onDataAvailable() {
                        try {
                            echo(reader, writer, echoedString);
                        } catch (Exception ioe) {
                            testResult.failure(ioe);
                        }
                        reader.notifyAvailable(this, readSize);
                    }

                    @Override
                    public void onAllDataRead() {
                        try {
                            echo(reader, writer, echoedString);
                        } catch (Exception ioe) {
                            testResult.failure(ioe);
                        }
                        res.resume();

                    }

                    @Override
                    public void onError(Throwable t) {
                        res.resume();
                        throw new RuntimeException(t);
                    }
                }, readSize);
            } catch (Throwable t) {
                testResult.failure(t);
            }

        }

        private static void echo(NIOInputStream reader, NIOOutputStream writer,
                StringBuffer sb) throws IOException {
            
            int available = reader.readyData();
            if (available > 0) {
                byte[] b = new byte[available];
                int read = reader.read(b);
                sb.append(new String(b, 0, read));
                writer.write(b, 0, read);
            }
        }

        @Override
        public String getEchoedString() {
            return echoedString.toString();
        }


    } // END EchoHttpHandler

    private static class EchoHttpHandler2 extends EchoHandler {

        private final FutureImpl<String> testResult;
        private final int readSize;
        private final ExecutorService threadPool;

        private final StringBuffer echoedString = new StringBuffer();

        // -------------------------------------------------------- Constructors


        EchoHttpHandler2(final FutureImpl<String> testResult, final int readSize,
                final ExecutorService threadPool) {

            this.testResult = testResult;
            this.readSize = readSize;
            this.threadPool = threadPool;

        }


        // ----------------------------------------- Methods from HttpHandler

        @Override
        public void service(final Request req,
                            final Response res)
                throws Exception {

            try {
                res.suspend();

                threadPool.execute(new Runnable() {

                    @Override
                    public void run() {
                        final NIOInputStream reader = req.getNIOInputStream();
                        final NIOOutputStream writer = res.getNIOOutputStream();
                        
                        reader.notifyAvailable(new ReadHandler() {

                            @Override
                            public void onDataAvailable() {
                                try {
                                    echo(reader, writer, echoedString);
                                } catch (Exception ioe) {
                                    testResult.failure(ioe);
                                }
                                reader.notifyAvailable(this, readSize);
                            }

                            @Override
                            public void onAllDataRead() {
                                try {
                                    echo(reader, writer, echoedString);
                                } catch (Exception ioe) {
                                    testResult.failure(ioe);
                                }
                                res.resume();

                            }

                            @Override
                            public void onError(Throwable t) {
                                res.resume();
                                throw new RuntimeException(t);
                            }
                        }, readSize);
                    }
                });
            } catch (Throwable t) {
                testResult.failure(t);
            }

        }

        private static void echo(NIOInputStream reader, NIOOutputStream writer,
                StringBuffer sb) throws IOException {
            
            int available = reader.readyData();
            if (available > 0) {
                byte[] b = new byte[available];
                int read = reader.read(b);
                sb.append(new String(b, 0, read));
                writer.write(b, 0, read);
            }
        }

        @Override
        public String getEchoedString() {
            return echoedString.toString();
        }


    } // END EchoHttpHandler
    
    
    private static class DirectBufferEchoHttpHandler extends EchoHandler {

        private final FutureImpl<String> testResult;
        private final int readSize;

        private final StringBuffer echoedString = new StringBuffer();

        // -------------------------------------------------------- Constructors


        DirectBufferEchoHttpHandler(final FutureImpl<String> testResult, final int readSize) {

            this.testResult = testResult;
            this.readSize = readSize;

        }


        // ----------------------------------------- Methods from HttpHandler

        @Override
        public void service(final Request req,
                            final Response res)
                throws Exception {

            try {
                final NIOInputStream reader = req.getNIOInputStream();
                final NIOOutputStream writer = res.getNIOOutputStream();

                res.suspend();

                reader.notifyAvailable(new ReadHandler() {

                    @Override
                    public void onDataAvailable() {
                        try {
                            echo(reader, writer, echoedString);
                            reader.notifyAvailable(this);
                        } catch (Exception ioe) {
                            testResult.failure(ioe);
                        }
                    }

                    @Override
                    public void onAllDataRead() {
                        try {
                            echo(reader, writer, echoedString);
                        } catch (Exception ioe) {
                            testResult.failure(ioe);
                        }
                        res.resume();

                    }

                    @Override
                    public void onError(Throwable t) {
                        res.resume();
                        throw new RuntimeException(t);
                    }
                });
            } catch (Throwable t) {
                testResult.failure(t);
            }

        }

        private static void echo(NIOInputStream reader, NIOOutputStream writer,
                StringBuffer sb) throws IOException {
            final int readyData = reader.readyData();
            if (readyData > 0) {
                final Buffer buffer = reader.readBuffer();
                if (!buffer.isDirect()) {
                    throw new RuntimeException("Direct buffer is expected!");
                }
                
                sb.append(buffer.toStringContent());
                writer.write(buffer);
            }
        }

        @Override
        public String getEchoedString() {
            return echoedString.toString();
        }


    } // END EchoHttpHandler
    
    private static class CharacterEchoHttpHandler extends EchoHandler {

        private final FutureImpl<String> testResult;
        private final int readSize;
        private final String encoding;

        private final StringBuffer echoedString = new StringBuffer();


        // -------------------------------------------------------- Constructors


        CharacterEchoHttpHandler(final FutureImpl<String> testResult,
                             final int readSize,
                             final String encoding) {

            this.testResult = testResult;
            this.readSize = readSize;
            this.encoding = encoding;

        }


        // ----------------------------------------- Methods from HttpHandler

        @Override
        public void service(final Request req,
                            final Response res)
                throws Exception {

            try {
                if (encoding != null) {
                    res.setContentType("text/plain;charset=" + encoding);
                }
                final NIOReader reader = req.getNIOReader();
                int available = reader.readyData();
                if (available > 0) {
                    char[] b = new char[available];
                    int read = reader.read(b);
                    res.getWriter().write(b, 0, read);
                }
                if (reader.isFinished()) {
                    return;
                }
                res.suspend();
                
                final StringBuilder sb = new StringBuilder();
                reader.notifyAvailable(new ReadHandler() {

                    @Override
                    public void onDataAvailable() {
                        try {
                            buffer(reader, sb);
                        } catch (IOException ioe) {
                            testResult.failure(ioe);
                        }
                        reader.notifyAvailable(this, readSize);
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

                    @Override
                    public void onError(Throwable t) {
                        res.resume();
                        throw new RuntimeException(t);
                    }
                }, readSize);
            } catch (Throwable t) {
                testResult.failure(t);
            }

        }

        private static void buffer(NIOReader reader, StringBuilder sb)
        throws IOException {
            char[] c = new char[reader.readyData()];
            int read;
            try {
                read = reader.read(c);
            } catch (IOException ioe) {
                throw new RuntimeException(ioe);
            }
            try {
                sb.append(new String(c, 0, read));
            } catch (Throwable ioe) {
                throw new RuntimeException(ioe);
            }
        }

        @Override
        public String getEchoedString() {
            return echoedString.toString();
        }

    } // END CharacterEchoHttpHandler

    private static abstract class EchoHandler extends HttpHandler {
        public abstract String getEchoedString();
    }

    private static class ClientFilter extends BaseFilter {
        private final static Logger logger = Grizzly.logger(ClientFilter.class);

        private CompositeBuffer buf = CompositeBuffer.newBuffer();

        private FutureImpl<String> testFuture;

        // number of bytes downloaded
        private volatile int bytesDownloaded;

        protected final HttpPacket request;

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
                logger.log(Level.FINE, "Connected... Sending the request: {0}", request);
            }

            if (strategy == null) {
                // Write the request asynchronously
                ctx.write(request);
            } else {
                strategy.doWrite(ctx);
            }

            HttpHeader header;
            if (request.isHeader()) {
                header = ((HttpHeader) request);
            } else {
                header = ((HttpContent) request).getHttpHeader();
            }

            if (header.isChunked()) {
                ctx.write(header.httpTrailerBuilder().build());
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
                    logger.log(Level.FINE, "HTTP content size: {0}", buffer.remaining());
                }
                if (buffer.hasRemaining()) {
                    bytesDownloaded += buffer.remaining();

                    buf.append(buffer);

                }

                if (httpContent.isLast()) {
                    if (logger.isLoggable(Level.FINE)) {
                        logger.log(Level.FINE, "Response complete: {0} bytes",
                                bytesDownloaded);
                    }
                    final String encoding = parseCharacterEncoding(
                            httpContent.getHttpHeader().getHeader(Header.ContentType));
                    if (encoding != null) {
                        testFuture.result(buf.toStringContent(Charset.forName(encoding)));
                    } else {
                        testFuture.result(buf.toStringContent());
                    }
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

        private String parseCharacterEncoding(String contentType) {
            if (contentType == null) {
                return null;
            }
            
            final int idx = contentType.indexOf("charset=");
            if (idx == -1) {
                return null;
            }
            
            int endIdx = contentType.indexOf(';', idx + 8);
            if (endIdx == -1) {
                endIdx = contentType.length();
            }
            
            return contentType.substring(idx + 8, endIdx);
        }

    } // END ClientFilter

}
