/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2014 Oracle and/or its affiliates. All rights reserved.
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
package org.glassfish.grizzly.spdy;

import org.glassfish.grizzly.http.HttpProbe;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.ConnectionProbe;
import org.glassfish.grizzly.http.HttpHeader;
import org.glassfish.grizzly.http.Method;
import org.glassfish.grizzly.http.Protocol;
import org.glassfish.grizzly.http.server.filecache.FileCache;
import org.glassfish.grizzly.http.server.filecache.FileCacheEntry;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.SocketConnectorHandler;
import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.http.HttpContent;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.http.HttpResponsePacket;
import org.glassfish.grizzly.memory.ByteBufferWrapper;
import org.glassfish.grizzly.nio.transport.TCPNIOConnectorHandler;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.Locale;
import java.util.Random;
import java.util.TimeZone;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.glassfish.grizzly.filterchain.FilterChain;
import org.glassfish.grizzly.http.HttpPacket;
import org.glassfish.grizzly.http.server.HttpHandler;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.http.server.Response;
import org.glassfish.grizzly.http.server.StaticHttpHandler;

import org.glassfish.grizzly.http.server.filecache.FileCacheProbe;
import org.glassfish.grizzly.http.util.MimeType;
import org.glassfish.grizzly.memory.Buffers;
import org.glassfish.grizzly.utils.DataStructures;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import static org.junit.Assert.*;

/**
 * The subset of HTTP File Cache tests to make sure SPDY works fine with FileCache.
 * 
 * @author Alexey Stashok
 */
@RunWith(Parameterized.class)
@SuppressWarnings("unchecked")
public class FileCacheTest extends AbstractSpdyTest {

    public static final int PORT = 18891;
    private HttpServer httpServer;

    private final SpdyVersion spdyVersion;
    private final SpdyMode spdyMode;
    private final boolean isSecure;
    
    public FileCacheTest(final SpdyVersion spdyVersion, final SpdyMode spdyMode,
            final boolean isSecure) {
        this.spdyVersion = spdyVersion;
        this.spdyMode = spdyMode;
        this.isSecure = isSecure;
    }

    @Parameters
    public static Collection<Object[]> getSpdyModes() {
        return AbstractSpdyTest.getSpdyModes();
    }

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

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Test
    public void testSimpleFile() throws Exception {
        final File file = createTempFile();

        final StatsConnectionProbe connectionProbe = new StatsConnectionProbe();
        final StatsHttpProbe httpProbe = new StatsHttpProbe();
        final StatsCacheProbe cacheProbe = new StatsCacheProbe();
        httpServer.getServerConfiguration().getMonitoringConfig().getFileCacheConfig().addProbes(cacheProbe);
        httpServer.getServerConfiguration().getMonitoringConfig().getHttpConfig().addProbes(httpProbe);
        httpServer.getServerConfiguration().getMonitoringConfig().getConnectionConfig().addProbes(connectionProbe);

        startHttpServer(new StaticHttpHandler() {

            @Override
            protected void onMissingResource(final Request req, final Response res) {
                try {
                    String error = null;
                    try {
                        res.setHeader("Content-Type", "text/plain");
                        addToFileCache(req, null, file);
                    } catch (Exception exception) {
                        error = exception.getMessage();
                    }

                    final Writer writer = res.getWriter();
                    writer.write(error == null
                                         ? "Hello not cached data"
                                         : "Error happened: " + error);
                    writer.close();

                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        });

        HttpRequestPacket.Builder builder = HttpRequestPacket.builder();
        builder.method(Method.GET)
                .uri("/somedata")
                .protocol(Protocol.HTTP_1_1)
                .host("localhost:" + PORT);

        final HttpPacket request1 = lastHttpPacket(
                builder.build());

        final HttpPacket request2 = lastHttpPacket(
                builder.build());

        final HttpPacket request3 = lastHttpPacket(
                builder.method(Method.POST).contentLength(0).build());

        boolean isOk = false;
        try {
            final BlockingQueue<HttpContent> inQueue =
                    DataStructures.getLTQInstance(HttpContent.class);
            final Connection c = getConnection("localhost", PORT, inQueue);
            c.write(request1);
            final HttpContent response1 = inQueue.poll(10, TimeUnit.SECONDS);

            assertNotNull("response1 is null", response1);
            assertEquals("Not cached data mismatch\n" + cacheProbe, "Hello not cached data", response1.getContent().toStringContent());

            InputStream fis = new FileInputStream(file);
            byte[] data = new byte[(int) file.length()];
            fis.read(data);
            fis.close();

            final String pattern = new String(data);
            c.write(request2);
            final HttpContent response2 = inQueue.poll(10, TimeUnit.SECONDS);
            assertNotNull("response2 is null", response2);
            assertEquals("ContentType is wrong " + response2.getHttpHeader().getContentType(), "text/plain", response2.getHttpHeader().getContentType());
            assertEquals("Cached data mismatch\n" + cacheProbe, pattern, response2.getContent().toStringContent());

            // Make sure cache is bypassed on POST request
            // and 405 status returned from StaticHttpHandler
            c.write(request3);
            final HttpContent response3 = inQueue.poll(10, TimeUnit.SECONDS);

            assertNotNull("response3 is null", response3);
            assertEquals("Not cached data mismatch\n" + cacheProbe, "Hello not cached data", response3.getContent().toStringContent());
            
            isOk = true;
        } finally {
            if (!isOk) {
                System.err.println(connectionProbe);
                System.err.println(httpProbe);
                System.err.println(cacheProbe);
            }
        }
    }
    
    /**
     * http://java.net/jira/browse/GRIZZLY-1014
     * "Content-type for files cached in the file cache is incorrect"
     */
    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Test
    public void testContentType() throws Exception {
        final File file = createTempFile();
        final String fileName = file.getName();
        final String requestPath = "/" + fileName;

        final StatsConnectionProbe connectionProbe = new StatsConnectionProbe();
        final StatsHttpProbe httpProbe = new StatsHttpProbe();
        final StatsCacheProbe cacheProbe = new StatsCacheProbe();
        httpServer.getServerConfiguration().getMonitoringConfig().getFileCacheConfig().addProbes(cacheProbe);
        httpServer.getServerConfiguration().getMonitoringConfig().getHttpConfig().addProbes(httpProbe);
        httpServer.getServerConfiguration().getMonitoringConfig().getConnectionConfig().addProbes(connectionProbe);

        startHttpServer(new StaticHttpHandler(file.getParent()));

        final HttpPacket request1 = lastHttpPacket(
                HttpRequestPacket.builder()
                .method("GET")
                .uri(requestPath)
                .protocol("HTTP/1.1")
                .header("Host", "localhost")
                .build());

        final HttpPacket request2 = lastHttpPacket(
                HttpRequestPacket.builder()
                .method("GET")
                .uri(requestPath)
                .protocol("HTTP/1.1")
                .header("Host", "localhost")
                .build());

        boolean isOk = false;
        try {
            InputStream fis = new FileInputStream(file);
            byte[] data = new byte[(int) file.length()];
            fis.read(data);
            fis.close();
            final BlockingQueue<HttpContent> inQueue =
                    DataStructures.getLTQInstance(HttpContent.class);
            final String pattern = new String(data);
            final Connection c = getConnection("localhost", PORT, inQueue);
            c.write(request1);
            final HttpContent response1 = inQueue.poll(10, TimeUnit.SECONDS);

            assertNotNull("response1 is null", response1);
            assertEquals("ContentType is wrong " + response1.getHttpHeader().getContentType(), MimeType.getByFilename(fileName), response1.getHttpHeader().getContentType());
            assertEquals("Direct data mismatch\n" + cacheProbe, pattern, response1.getContent().toStringContent());

            c.write(request2);
            final HttpContent response2 = inQueue.poll(10, TimeUnit.SECONDS);
            assertNotNull("response2 is null", response2);
            assertEquals("ContentType is wrong " + response2.getHttpHeader().getContentType(), MimeType.getByFilename(fileName), response2.getHttpHeader().getContentType());
            assertEquals("Cached data mismatch\n" + cacheProbe, pattern, response2.getContent().toStringContent());
            isOk = true;
        } finally {
            if (!isOk) {
                System.err.println(connectionProbe);
                System.err.println(httpProbe);
                System.err.println(cacheProbe);
            }
        }
    }


    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Test
    public void testIfModifiedSince() throws Exception {
        final File file = createTempFile();
        final String fileName = file.getName();
        final String requestPath = "/" + fileName;
        startHttpServer(new StaticHttpHandler(file.getParent()) {
        });

        final HttpPacket request1 = lastHttpPacket(
                HttpRequestPacket.builder()
                .method("GET")
                .uri(requestPath)
                .protocol("HTTP/1.1")
                .header("Host", "localhost")
                .build());

        InputStream fis = new FileInputStream(file);
        byte[] data = new byte[(int) file.length()];
        fis.read(data);
        fis.close();

        final String pattern = new String(data);
        final BlockingQueue<HttpContent> inQueue =
                DataStructures.getLTQInstance(HttpContent.class);
        final Connection c = getConnection("localhost", PORT, inQueue);
        c.write(request1);
        final HttpContent response1 = inQueue.poll(10, TimeUnit.SECONDS);
        assertNotNull("response1 is null", response1);
        assertEquals("Cached data mismatch. Response=" + response1.getHttpHeader(),
                pattern, response1.getContent().toStringContent());

        final String ifModifiedSinceValue = convertToDate(file.lastModified());
        for (int i = 0; i < 1000; i++) {
            final HttpPacket request2 = lastHttpPacket(
                    HttpRequestPacket.builder()
                    .method("GET")
                    .uri(requestPath)
                    .protocol("HTTP/1.1")
                    .header("Host", "localhost")
                    .header("If-Modified-Since", ifModifiedSinceValue)
                    .build());
            c.write(request2);
            final HttpContent response2 = inQueue.poll(10, TimeUnit.SECONDS);

            assertNotNull("response2 is null, iteration#" + i, response2);
            assertEquals("304 is expected", 304, ((HttpResponsePacket) response2.getHttpHeader()).getStatus());
            assertTrue("empty body is expected", !response2.getContent().hasRemaining());
        }

    }
    

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Test
    public void test304NonCachedFile() throws Exception {
        final File file = createTempFile();
        final String fileName = file.getName();
        final String requestPath = "/" + fileName;
        startHttpServer(new StaticHttpHandler(file.getParent()) {
        });
        httpServer.getListener("grizzly").getFileCache().setMaxEntrySize(100);

        final HttpPacket request1 = lastHttpPacket(
                HttpRequestPacket.builder()
                .method("GET")
                .uri(requestPath)
                .protocol("HTTP/1.1")
                .header("Host", "localhost")
                .build());

        InputStream fis = new FileInputStream(file);
        byte[] data = new byte[(int) file.length()];
        fis.read(data);
        fis.close();

        final String pattern = new String(data);
        final BlockingQueue<HttpContent> inQueue =
                DataStructures.getLTQInstance(HttpContent.class);
        final Connection c = getConnection("localhost", PORT, inQueue);
        c.write(request1);
        
        final HttpContent response1 = inQueue.poll(10, TimeUnit.SECONDS);
        assertNotNull("response1 is null", response1);
        assertEquals("Cached data mismatch. Response=" + response1.getHttpHeader(),
                pattern, response1.getContent().toStringContent());

        String lastModified = convertToDate(file.lastModified());
        final HttpPacket request2 = lastHttpPacket(
                HttpRequestPacket.builder()
                .method("GET")
                .uri(requestPath)
                .protocol("HTTP/1.1")
                .header("Host", "localhost")
                .header("If-Modified-Since", lastModified)
                .build());
        c.write(request2);
        final HttpContent response2 = inQueue.poll(10, TimeUnit.SECONDS);

        assertNotNull("response2 is null", response2);
        assertEquals("304 is expected", 304, ((HttpResponsePacket) response2.getHttpHeader()).getStatus());
        assertTrue("empty body is expected", !response2.getContent().hasRemaining());

    }


    private void configureHttpServer() throws Exception {
        httpServer = createServer(null, PORT, spdyVersion, spdyMode, isSecure, true);
        httpServer.getListener("grizzly").getKeepAlive().setIdleTimeoutInSeconds(-1);
    }

    private void startHttpServer(final HttpHandler httpHandler) throws Exception {
        httpServer.getServerConfiguration().addHttpHandler(httpHandler);
        httpServer.start();
    }

    private Connection getConnection(String host,
                                     int port,
                                     BlockingQueue<HttpContent> inQueue)
    throws Exception {

        final FilterChain clientChain =
                createClientFilterChainAsBuilder(spdyVersion, spdyMode, isSecure,
                new HttpMessageFilter(inQueue))
                .build();

        
        SocketConnectorHandler connectorHandler = TCPNIOConnectorHandler.builder(
                httpServer.getListener("grizzly").getTransport())
                .processor(clientChain)
                .build();

        Future<Connection> connectFuture = connectorHandler.connect(host, port);
        return connectFuture.get(10, TimeUnit.SECONDS);

    }

    private static String convertToDate(final long date) {

        SimpleDateFormat format = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz",
                                                  Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("GMT"));
        return format.format(new Date(date));

    }

    private static File createTempFile() throws IOException {
        final File f = File.createTempFile("grizzly-file-cache", ".txt");
        f.deleteOnExit();
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(f);
            Random r = new Random(System.currentTimeMillis());
            for (int i = 0; i < 100; i++) {
                out.write(Long.toString(r.nextLong()).getBytes());
            }
        } finally {
            if (out != null) {
                try {
                    out.flush();
                    out.close();
                } catch (IOException ignored) {
                }

            }
        }
        return f;
    }
    
    private static HttpPacket lastHttpPacket(HttpHeader httpHeader) {
        return HttpContent.builder(httpHeader)
                .content(Buffers.EMPTY_BUFFER)
                .last(true)
                .build();
    }

    private static class HttpMessageFilter extends BaseFilter {

        private final BlockingQueue<HttpContent> queue;

        public HttpMessageFilter(final BlockingQueue<HttpContent> queue) {
            this.queue = queue;
        }

        @Override
        public NextAction handleRead(FilterChainContext ctx) throws IOException {
            final HttpContent content = ctx.getMessage();
//            try {
                if (!content.isLast()) {
                    return ctx.getStopAction(content);
                }

                queue.add(content);
//            } catch (Exception e) {
//                future.failure(e);
//                e.printStackTrace();
//            }

            return ctx.getStopAction();
        }
    }

    private static class StatsConnectionProbe extends ConnectionProbe.Adapter {
        final AtomicLong sentBytesCounter = new AtomicLong();
        final AtomicInteger receivedCounter = new AtomicInteger();

        @Override
        public void onReadEvent(Connection connection, Buffer data, int size) {
            receivedCounter.addAndGet(size);
        }

        @Override
        public void onWriteEvent(Connection connection, Buffer data, long size) {
            sentBytesCounter.addAndGet(size);
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("connection-stats[received=")
            .append(receivedCounter.get())
            .append(", sent=").append(sentBytesCounter.get())
            .append("]");

            return sb.toString();
        }
    }

    private static class StatsHttpProbe extends HttpProbe.Adapter {
        final AtomicInteger sentBytesCounter = new AtomicInteger();
        final AtomicInteger receivedCounter = new AtomicInteger();

        @Override
        public void onDataReceivedEvent(Connection connection, Buffer buffer) {
            receivedCounter.addAndGet(buffer.remaining());
        }

        @Override
        public void onDataSentEvent(Connection connection, Buffer buffer) {
            sentBytesCounter.addAndGet(buffer.remaining());
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("http-stats[received=")
            .append(receivedCounter.get())
            .append(", sent=").append(sentBytesCounter.get())
            .append("]");

            return sb.toString();
        }
    }
    
    private static class StatsCacheProbe implements FileCacheProbe {

        final AtomicInteger entryAddedCounter = new AtomicInteger();
        final AtomicInteger entryRemovedCounter = new AtomicInteger();
        final AtomicInteger entryHitCounter = new AtomicInteger();
        final AtomicInteger entryMissedCounter = new AtomicInteger();
        final AtomicInteger entryErrorCounter = new AtomicInteger();

        @Override
        public void onEntryAddedEvent(FileCache fileCache, FileCacheEntry entry) {
            entryAddedCounter.incrementAndGet();
        }

        @Override
        public void onEntryRemovedEvent(FileCache fileCache, FileCacheEntry entry) {
            entryRemovedCounter.incrementAndGet();
        }

        @Override
        public void onEntryHitEvent(FileCache fileCache, FileCacheEntry entry) {
            entryHitCounter.incrementAndGet();
        }

        @Override
        public void onEntryMissedEvent(FileCache fileCache, String host, String requestURI) {
            entryMissedCounter.incrementAndGet();
        }

        @Override
        public void onErrorEvent(FileCache fileCache, Throwable error) {
            entryErrorCounter.incrementAndGet();
        }

        public int getEntryAddedCounter() {
            return entryAddedCounter.get();
        }

        public int getEntryRemovedCounter() {
            return entryRemovedCounter.get();
        }
        public int getEntryHitCounter() {
            return entryHitCounter.get();
        }
        public int getEntryMissedCounter() {
            return entryMissedCounter.get();
        }
        public int getEntryErrorCounter() {
            return entryErrorCounter.get();
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("file-cache-stats[added=")
            .append(getEntryAddedCounter())
            .append(", removed=").append(getEntryRemovedCounter())
            .append(", hit=").append(getEntryHitCounter())
            .append(", missed=").append(getEntryMissedCounter())
            .append(", error=").append(getEntryErrorCounter())
            .append("]");

            return sb.toString();
        }
    }
}
