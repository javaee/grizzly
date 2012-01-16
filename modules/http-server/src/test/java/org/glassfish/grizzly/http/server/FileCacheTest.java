/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2012 Oracle and/or its affiliates. All rights reserved.
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

import org.glassfish.grizzly.http.ContentEncoding;
import org.glassfish.grizzly.http.HttpProbe;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.ConnectionProbe;
import org.glassfish.grizzly.IOEvent;
import org.glassfish.grizzly.http.HttpHeader;
import org.glassfish.grizzly.http.TransferEncoding;
import org.glassfish.grizzly.http.server.filecache.FileCache;
import org.glassfish.grizzly.http.server.filecache.FileCacheEntry;
import org.glassfish.grizzly.http.EncodingFilter;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.SocketConnectorHandler;
import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.FilterChainBuilder;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.filterchain.TransportFilter;
import org.glassfish.grizzly.http.GZipContentEncoding;
import org.glassfish.grizzly.http.HttpClientFilter;
import org.glassfish.grizzly.http.HttpContent;
import org.glassfish.grizzly.http.HttpPacket;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.http.HttpResponsePacket;
import org.glassfish.grizzly.http.server.io.NIOWriter;
import org.glassfish.grizzly.impl.FutureImpl;
import org.glassfish.grizzly.impl.SafeFutureImpl;
import org.glassfish.grizzly.memory.ByteBufferWrapper;
import org.glassfish.grizzly.nio.transport.TCPNIOConnectorHandler;
import org.glassfish.grizzly.ssl.SSLContextConfigurator;
import org.glassfish.grizzly.ssl.SSLEngineConfigurator;
import org.glassfish.grizzly.ssl.SSLFilter;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.glassfish.grizzly.http.server.filecache.FileCacheProbe;
import org.glassfish.grizzly.http.server.util.MimeType;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import static org.junit.Assert.*;

/**
 * Test File Cache implementation
 * 
 * @author Alexey Stashok
 */
@RunWith(Parameterized.class)
@SuppressWarnings("unchecked")
public class FileCacheTest {

    public static final int PORT = 18891;
    private HttpServer httpServer;
    private final boolean isSslEnabled;

    public FileCacheTest(boolean isSslEnabled) {
        this.isSslEnabled = isSslEnabled;
    }

    @Parameters
    public static Collection<Object[]> getSslParameter() {
        return Arrays.asList(new Object[][]{
                    {Boolean.FALSE}, {Boolean.TRUE}
                });
    }

    @Before
    public void before() throws Exception {
        ByteBufferWrapper.DEBUG_MODE = true;
        configureHttpServer();
    }

    @After
    public void after() throws Exception {
        if (httpServer != null) {
            httpServer.stop();
        }
    }

    @Test
    public void testSimpleFile() throws Exception {
        final String fileName = "./pom.xml";

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
                        res.setHeader("Content-Type", "text/xml");
                        addToFileCache(req, new File(fileName));
                    } catch (Exception exception) {
                        error = exception.getMessage();
                    }

                    final NIOWriter writer = res.getNIOWriter();
                    writer.write(error == null
                            ? "Hello not cached data"
                            : "Error happened: " + error);
                    writer.close();

                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        });

        final HttpRequestPacket request1 = HttpRequestPacket.builder()
                .method("GET")
                .uri("/somedata")
                .protocol("HTTP/1.1")
                .header("Host", "localhost")
                .build();

        final HttpRequestPacket request2 = HttpRequestPacket.builder()
                .method("GET")
                .uri("/somedata")
                .protocol("HTTP/1.1")
                .header("Host", "localhost")
                .build();

        boolean isOk = false;
        try {
            final Future<HttpContent> responseFuture1 = send("localhost", PORT, request1);
            final HttpContent response1 = responseFuture1.get(10, TimeUnit.SECONDS);

            assertEquals("Not cached data mismatch\n" + cacheProbe, "Hello not cached data", response1.getContent().toStringContent());


            final File file = new File(fileName);
            InputStream fis = new FileInputStream(file);
            byte[] data = new byte[(int) file.length()];
            fis.read(data);
            fis.close();

            final String pattern = new String(data);

            final Future<HttpContent> responseFuture2 = send("localhost", PORT, request2);
            final HttpContent response2 = responseFuture2.get(10, TimeUnit.SECONDS);
            assertEquals("ContentType is wrong " + response2.getHttpHeader().getContentType(), "text/xml", response2.getHttpHeader().getContentType());
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
    
    /**
     * http://java.net/jira/browse/GRIZZLY-1014
     * "Content-type for files cached in the file cache is incorrect"
     */
    @Test
    public void testContentType() throws Exception {
        final String fileName = "./pom.xml";

        final StatsConnectionProbe connectionProbe = new StatsConnectionProbe();
        final StatsHttpProbe httpProbe = new StatsHttpProbe();
        final StatsCacheProbe cacheProbe = new StatsCacheProbe();
        httpServer.getServerConfiguration().getMonitoringConfig().getFileCacheConfig().addProbes(cacheProbe);
        httpServer.getServerConfiguration().getMonitoringConfig().getHttpConfig().addProbes(httpProbe);
        httpServer.getServerConfiguration().getMonitoringConfig().getConnectionConfig().addProbes(connectionProbe);

        startHttpServer(new StaticHttpHandler());

        final HttpRequestPacket request1 = HttpRequestPacket.builder()
                .method("GET")
                .uri("/pom.xml")
                .protocol("HTTP/1.1")
                .header("Host", "localhost")
                .build();

        final HttpRequestPacket request2 = HttpRequestPacket.builder()
                .method("GET")
                .uri("/pom.xml")
                .protocol("HTTP/1.1")
                .header("Host", "localhost")
                .build();

        boolean isOk = false;
        try {
            final File file = new File(fileName);
            InputStream fis = new FileInputStream(file);
            byte[] data = new byte[(int) file.length()];
            fis.read(data);
            fis.close();

            final String pattern = new String(data);
            final Future<HttpContent> responseFuture1 = send("localhost", PORT, request1);
            final HttpContent response1 = responseFuture1.get(10, TimeUnit.SECONDS);

            assertEquals("ContentType is wrong " + response1.getHttpHeader().getContentType(), MimeType.getByFilename(fileName), response1.getHttpHeader().getContentType());
            assertEquals("Direct data mismatch\n" + cacheProbe, pattern, response1.getContent().toStringContent());

            final Future<HttpContent> responseFuture2 = send("localhost", PORT, request2);
            final HttpContent response2 = responseFuture2.get(10, TimeUnit.SECONDS);
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
    
    @Test
    public void testGZip() throws Exception {
        final String fileName = "./pom.xml";

        final StatsCacheProbe probe = new StatsCacheProbe();
        httpServer.getServerConfiguration().getMonitoringConfig().getFileCacheConfig().addProbes(probe);

        startHttpServer(new StaticHttpHandler() {

            @Override
            public void onMissingResource(final Request req, final Response res) {
                try {
                    String error = null;
                    try {
                        addToFileCache(req, new File(fileName));
                    } catch (Exception exception) {
                        error = exception.getMessage();
                    }

                    final NIOWriter writer = res.getNIOWriter();
                    writer.write(error == null
                            ? "Hello not cached data"
                            : "Error happened: " + error);
                    writer.close();

                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        });

        final HttpRequestPacket request1 = HttpRequestPacket.builder()
                .method("GET")
                .uri("/somedata")
                .protocol("HTTP/1.1")
                .header("Host", "localhost")
                .header("Accept-Encoding", "gzip")
                .build();

        final HttpRequestPacket request2 = HttpRequestPacket.builder()
                .method("GET")
                .uri("/somedata")
                .protocol("HTTP/1.1")
                .header("Host", "localhost")
                .header("Accept-Encoding", "gzip")
                .build();

        boolean isOk = false;
        try {
            final Future<HttpContent> responseFuture1 = send("localhost", PORT, request1);
            final HttpContent response1 = responseFuture1.get(10, TimeUnit.SECONDS);

            assertEquals(probe.toString(), "gzip", response1.getHttpHeader().getHeader("Content-Encoding"));
            assertEquals("Not cached data mismatch\n" + probe, "Hello not cached data", response1.getContent().toStringContent());


            final File file = new File(fileName);
            InputStream fis = new FileInputStream(file);
            byte[] data = new byte[(int) file.length()];
            fis.read(data);
            fis.close();

            final String pattern = new String(data);

            final Future<HttpContent> responseFuture2 = send("localhost", PORT, request2);
            final HttpContent response2 = responseFuture2.get(10, TimeUnit.SECONDS);
            assertEquals(probe.toString(), "gzip", response2.getHttpHeader().getHeader("Content-Encoding"));
            assertEquals("Cached data mismatch\n" + probe, pattern, response2.getContent().toStringContent());
            isOk = true;
        } finally {
            if (!isOk) {
                System.err.println(probe);
            }
        }
    }

    @Test
    public void testIfModified() throws Exception {
        final String fileName = "./pom.xml";
        startHttpServer(new StaticHttpHandler(".") {
        });

        final HttpRequestPacket request1 = HttpRequestPacket.builder()
                .method("GET")
                .uri("/pom.xml")
                .protocol("HTTP/1.1")
                .header("Host", "localhost")
                .build();

        final File file = new File(fileName);
        InputStream fis = new FileInputStream(file);
        byte[] data = new byte[(int) file.length()];
        fis.read(data);
        fis.close();

        final String pattern = new String(data);

        final Future<HttpContent> responseFuture1 = send("localhost", PORT, request1);
        final HttpContent response1 = responseFuture1.get(10, TimeUnit.SECONDS);
        assertEquals("Cached data mismatch. Response=" + response1.getHttpHeader(),
                pattern, response1.getContent().toStringContent());

        final HttpRequestPacket request2 = HttpRequestPacket.builder()
                .method("GET")
                .uri("/pom.xml")
                .protocol("HTTP/1.1")
                .header("Host", "localhost")
                .header("If-Match", "W/\"" + file.length() + "-" + file.lastModified() + "\"")
                .header("If-Modified-Since", "" + file.lastModified())
                .build();

        final Future<HttpContent> responseFuture2 = send("localhost", PORT, request2);
        final HttpContent response2 = responseFuture2.get(10, TimeUnit.SECONDS);

        assertEquals("304 is expected", 304, ((HttpResponsePacket) response2.getHttpHeader()).getStatus());
        assertTrue("empty body is expected", !response2.getContent().hasRemaining());
    }

    private void configureHttpServer() throws Exception {
        httpServer = new HttpServer();
        final NetworkListener listener =
                new NetworkListener("grizzly",
                NetworkListener.DEFAULT_NETWORK_HOST,
                PORT);
        if (isSslEnabled) {
            listener.setSecure(true);
            listener.setSSLEngineConfig(createSSLConfig(true));
        }
        listener.getFileCache().setEnabled(true);
        listener.setCompression("FORCE");
        httpServer.addListener(listener);
    }

    private void startHttpServer(final HttpHandler httpHandler) throws Exception {
        httpServer.getServerConfiguration().addHttpHandler(httpHandler);
        httpServer.start();
    }

    private Future<HttpContent> send(String host, int port, HttpPacket request) throws Exception {
        final FutureImpl<HttpContent> future = SafeFutureImpl.create();

        final FilterChainBuilder builder = FilterChainBuilder.stateless();
        builder.add(new TransportFilter());

        if (isSslEnabled) {
            final SSLFilter sslFilter = new SSLFilter(createSSLConfig(true),
                    createSSLConfig(false));
            builder.add(sslFilter);
        }

        GZipContentEncoding gzipClientContentEncoding =
                new GZipContentEncoding(512, 512, new EncodingFilter() {
            @Override
            public boolean applyEncoding(HttpHeader httpPacket) {
                return false;
            }

            @Override
            public boolean applyDecoding(HttpHeader httpPacket) {
                return true;
            }
        });

        final HttpClientFilter httpClientFilter = new HttpClientFilter();
        httpClientFilter.addContentEncoding(gzipClientContentEncoding);

        builder.add(httpClientFilter);
        builder.add(new HttpMessageFilter(future));

        SocketConnectorHandler connectorHandler = TCPNIOConnectorHandler.builder(
                httpServer.getListener("grizzly").getTransport())
                .processor(builder.build())
                .build();

        Future<Connection> connectFuture = connectorHandler.connect(host, port);
        final Connection connection = connectFuture.get(10, TimeUnit.SECONDS);

        connection.write(request);

        return future;
    }

    private static SSLEngineConfigurator createSSLConfig(boolean isServer) throws Exception {
        final SSLContextConfigurator sslContextConfigurator =
                new SSLContextConfigurator();
        final ClassLoader cl = SuspendTest.class.getClassLoader();
        // override system properties
        final URL cacertsUrl = cl.getResource("ssltest-cacerts.jks");
        if (cacertsUrl != null) {
            sslContextConfigurator.setTrustStoreFile(cacertsUrl.getFile());
            sslContextConfigurator.setTrustStorePass("changeit");
        }

        // override system properties
        final URL keystoreUrl = cl.getResource("ssltest-keystore.jks");
        if (keystoreUrl != null) {
            sslContextConfigurator.setKeyStoreFile(keystoreUrl.getFile());
            sslContextConfigurator.setKeyStorePass("changeit");
        }

        return new SSLEngineConfigurator(sslContextConfigurator.createSSLContext(),
                !isServer, false, false);
    }

    private static class HttpMessageFilter extends BaseFilter {

        private final FutureImpl<HttpContent> future;

        public HttpMessageFilter(FutureImpl<HttpContent> future) {
            this.future = future;
        }

        @Override
        public NextAction handleRead(FilterChainContext ctx) throws IOException {
            final HttpContent content = (HttpContent) ctx.getMessage();
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

    private static class StatsConnectionProbe implements ConnectionProbe {
        final AtomicLong sentBytesCounter = new AtomicLong();
        final AtomicInteger receivedCounter = new AtomicInteger();

        @Override
        public void onBindEvent(Connection connection) {
        }

        @Override
        public void onAcceptEvent(Connection serverConnection,
                Connection clientConnection) {
        }

        @Override
        public void onConnectEvent(Connection connection) {
        }

        @Override
        public void onReadEvent(Connection connection, Buffer data, int size) {
            receivedCounter.addAndGet(size);
        }

        @Override
        public void onWriteEvent(Connection connection, Buffer data, long size) {
            sentBytesCounter.addAndGet(size);
        }

        @Override
        public void onErrorEvent(Connection connection, Throwable error) {
        }

        @Override
        public void onCloseEvent(Connection connection) {
        }

        @Override
        public void onIOEventReadyEvent(Connection connection, IOEvent ioEvent) {
        }

        @Override
        public void onIOEventEnableEvent(Connection connection, IOEvent ioEvent) {
        }

        @Override
        public void onIOEventDisableEvent(Connection connection, IOEvent ioEvent) {
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

    private static class StatsHttpProbe implements HttpProbe {
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
        public void onHeaderParseEvent(Connection connection, HttpHeader header, int size) {
        }

        @Override
        public void onHeaderSerializeEvent(Connection connection, HttpHeader header, Buffer buffer) {
        }

        @Override
        public void onContentChunkParseEvent(Connection connection, HttpContent content) {
        }

        @Override
        public void onContentChunkSerializeEvent(Connection connection, HttpContent content) {
        }

        @Override
        public void onContentEncodingParseEvent(Connection connection, HttpHeader header, Buffer buffer, ContentEncoding contentEncoding) {
        }

        @Override
        public void onContentEncodingSerializeEvent(Connection connection, HttpHeader header, Buffer buffer, ContentEncoding contentEncoding) {
        }

        @Override
        public void onTransferEncodingParseEvent(Connection connection, HttpHeader header, Buffer buffer, TransferEncoding transferEncoding) {
        }

        @Override
        public void onTransferEncodingSerializeEvent(Connection connection, HttpHeader header, Buffer buffer, TransferEncoding transferEncoding) {
        }

        @Override
        public void onErrorEvent(Connection connection, HttpPacket packet, Throwable error) {
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
