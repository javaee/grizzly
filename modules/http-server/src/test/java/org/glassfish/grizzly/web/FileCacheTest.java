/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010 Oracle and/or its affiliates. All rights reserved.
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
package org.glassfish.grizzly.web;

import org.glassfish.grizzly.http.HttpHeader;
import org.glassfish.grizzly.http.server.filecache.FileCache;
import org.glassfish.grizzly.http.server.filecache.FileCacheEntry;
import org.glassfish.grizzly.http.util.DataChunk;
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
import org.glassfish.grizzly.http.server.*;
import org.glassfish.grizzly.http.server.HttpService;
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
import org.glassfish.grizzly.http.server.filecache.FileCacheProbe;
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
public class FileCacheTest {

    public static final int PORT = 18891;
    private HttpServer gws;
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
        configureWebServer();
    }

    @After
    public void after() throws Exception {
        if (gws != null) {
            gws.stop();
        }
    }

//    @Ignore
    @Test
    public void testSimpleFile() throws Exception {
        final String fileName = "./pom.xml";

        final StatsCacheProbe probe = new StatsCacheProbe();
        gws.getServerConfiguration().getMonitoringConfig().getFileCacheConfig().addProbes(probe);

        startWebServer(new HttpService() {

            @Override
            public void service(final Request req, final Response res) {
                try {
                    String error = null;
                    try {
                        staticResourcesHandler.addToFileCache(req, new File(fileName));
                    } catch (Exception exception) {
                        error = exception.getMessage();
                    }

                    final NIOWriter writer = res.getWriter();
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

            assertEquals("Not cached data mismatch\n" + probe, "Hello not cached data", response1.getContent().toStringContent());


            final File file = new File(fileName);
            InputStream fis = new FileInputStream(file);
            byte[] data = new byte[(int) file.length()];
            fis.read(data);
            fis.close();

            final String pattern = new String(data);

            final Future<HttpContent> responseFuture2 = send("localhost", PORT, request2);
            final HttpContent response2 = responseFuture2.get(10, TimeUnit.SECONDS);
            assertEquals("Cached data mismatch\n" + probe, pattern, response2.getContent().toStringContent());
            isOk = true;
        } finally {
            if (!isOk) {
                System.err.println(probe);
            }
        }
    }

//    @Ignore
    @Test
    public void testGZip() throws Exception {
        final String fileName = "./pom.xml";

        final StatsCacheProbe probe = new StatsCacheProbe();
        gws.getServerConfiguration().getMonitoringConfig().getFileCacheConfig().addProbes(probe);

        startWebServer(new HttpService() {

            @Override
            public void service(final Request req, final Response res) {
                try {
                    String error = null;
                    try {
                        staticResourcesHandler.addToFileCache(req, new File(fileName));
                    } catch (Exception exception) {
                        error = exception.getMessage();
                    }

                    final NIOWriter writer = res.getWriter();
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
        startWebServer(new HttpService(".") {

            @Override
            public void service(Request request, Response response)
                    throws Exception {
            }
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

    private void configureWebServer() throws Exception {
        gws = new HttpServer();
        final NetworkListener listener =
                new NetworkListener("grizzly",
                NetworkListener.DEFAULT_NETWORK_HOST,
                PORT);
        if (isSslEnabled) {
            listener.setSecure(true);
            listener.setSSLEngineConfig(createSSLConfig(true));
        }
        listener.getFileCache().setEnabled(true);
        listener.getContentEncodings().add(new GZipContentEncoding(
                GZipContentEncoding.DEFAULT_IN_BUFFER_SIZE,
                GZipContentEncoding.DEFAULT_OUT_BUFFER_SIZE,
                new EncodingFilter() {

                    @Override
                    public boolean applyEncoding(HttpHeader httpPacket) {
                        if (!httpPacket.isRequest()) {
                            final HttpResponsePacket response = (HttpResponsePacket) httpPacket;
                            final HttpRequestPacket request;
                            final DataChunk acceptEncoding;
                            if (response.isChunked() && (request = response.getRequest()) != null
                                    && (acceptEncoding = request.getHeaders().getValue("Accept-Encoding")) != null
                                    && acceptEncoding.indexOf("gzip", 0) >= 0) {
                                return true;
                            }
                        }

                        return false;
                    }
                }));

        gws.addListener(listener);
    }

    private void startWebServer(HttpService httpService) throws Exception {
        gws.getServerConfiguration().addHttpService(httpService);
        gws.start();
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

        builder.add(new HttpClientFilter());
        builder.add(new HttpMessageFilter(future));

        SocketConnectorHandler connectorHandler = new TCPNIOConnectorHandler(
                gws.getListener("grizzly").getTransport());
        connectorHandler.setProcessor(builder.build());

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
