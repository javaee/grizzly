/*
 *
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2007-2010 Sun Microsystems, Inc. All rights reserved.
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
 *
 */

package com.sun.grizzly.web;

import com.sun.grizzly.Connection;
import com.sun.grizzly.SocketConnectorHandler;
import com.sun.grizzly.filterchain.BaseFilter;
import com.sun.grizzly.filterchain.FilterChainBuilder;
import com.sun.grizzly.filterchain.FilterChainContext;
import com.sun.grizzly.filterchain.NextAction;
import com.sun.grizzly.filterchain.TransportFilter;
import com.sun.grizzly.http.HttpClientFilter;
import com.sun.grizzly.http.HttpContent;
import com.sun.grizzly.http.HttpPacket;
import com.sun.grizzly.http.HttpRequestPacket;
import com.sun.grizzly.http.HttpResponsePacket;
import com.sun.grizzly.http.server.GrizzlyListener;
import com.sun.grizzly.http.server.GrizzlyRequest;
import com.sun.grizzly.http.server.GrizzlyResponse;
import com.sun.grizzly.http.server.GrizzlyWebServer;
import com.sun.grizzly.http.server.adapter.GrizzlyAdapter;
import com.sun.grizzly.impl.FutureImpl;
import com.sun.grizzly.impl.SafeFutureImpl;
import com.sun.grizzly.memory.ByteBufferWrapper;
import com.sun.grizzly.nio.transport.TCPNIOConnectorHandler;
import com.sun.grizzly.ssl.SSLContextConfigurator;
import com.sun.grizzly.ssl.SSLEngineConfigurator;
import com.sun.grizzly.ssl.SSLFilter;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.URL;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
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

    private ScheduledThreadPoolExecutor scheduledThreadPool;
    private GrizzlyWebServer gws;
    private final boolean isSslEnabled;

    public FileCacheTest(boolean isSslEnabled) {
        this.isSslEnabled = isSslEnabled;
    }

    @Parameters
    public static Collection<Object[]> getSslParameter() {
        return Arrays.asList(new Object[][]{
                    {Boolean.FALSE},
                    {Boolean.TRUE}
                });
    }

    @Before
    public void before() throws Exception {
        ByteBufferWrapper.DEBUG_MODE = true;
        scheduledThreadPool = new ScheduledThreadPoolExecutor(1);
        configureWebServer();
    }

    @After
    public void after() throws Exception {
        if (gws != null) {
            gws.stop();
        }

        scheduledThreadPool.shutdown();
    }

//    @Ignore
    @Test
    public void testSimpleFile() throws Exception {
        final String fileName = "./pom.xml";

        startWebServer(new GrizzlyAdapter() {

            @Override
            public void service(final GrizzlyRequest req, final GrizzlyResponse res) {
                try {
                    String error = null;
                    try {
                        addToFileCache(req, new File(fileName));
                    } catch (Exception exception) {
                        error = exception.getMessage();
                    }

                    final PrintWriter writer = res.getWriter();
                    writer.write(error == null ?
                        "Hello not cached data" :
                        "Error happened: " + error);
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

        final Future<HttpContent> responseFuture1 = send("localhost", PORT, request1);
        final HttpContent response1 = responseFuture1.get(10, TimeUnit.SECONDS);

        assertEquals("Not cached data mismatch", "Hello not cached data", response1.getContent().toStringContent());

        
        final File file = new File(fileName);
        InputStream fis = new FileInputStream(file);
        byte[] data = new byte[(int) file.length()];
        fis.read(data);
        fis.close();

        final String pattern = new String(data);

        final Future<HttpContent> responseFuture2 = send("localhost", PORT, request2);
        final HttpContent response2 = responseFuture2.get(10, TimeUnit.SECONDS);
        assertEquals("Cached data mismatch", pattern, response2.getContent().toStringContent());
    }

//    @Ignore
    @Test
    public void testGZip() throws Exception {
        final String fileName = "./pom.xml";
        startWebServer(new GrizzlyAdapter() {

            @Override
            public void service(final GrizzlyRequest req, final GrizzlyResponse res) {
                try {
                    String error = null;
                    try {
                        addToFileCache(req, new File(fileName));
                    } catch (Exception exception) {
                        error = exception.getMessage();
                    }

                    final PrintWriter writer = res.getWriter();
                    writer.write(error == null ?
                        "Hello not cached data" :
                        "Error happened: " + error);
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

        final Future<HttpContent> responseFuture1 = send("localhost", PORT, request1);
        final HttpContent response1 = responseFuture1.get(10, TimeUnit.SECONDS);

        assertEquals("gzip", response1.getHttpHeader().getHeader("Content-Encoding"));
        assertEquals("Not cached data mismatch", "Hello not cached data", response1.getContent().toStringContent());


        final File file = new File(fileName);
        InputStream fis = new FileInputStream(file);
        byte[] data = new byte[(int) file.length()];
        fis.read(data);
        fis.close();

        final String pattern = new String(data);

        final Future<HttpContent> responseFuture2 = send("localhost", PORT, request2);
        final HttpContent response2 = responseFuture2.get(10, TimeUnit.SECONDS);
        assertEquals("gzip", response2.getHttpHeader().getHeader("Content-Encoding"));
        assertEquals("Cached data mismatch", pattern, response2.getContent().toStringContent());
    }

    @Test
    public void testIfModified() throws Exception {
        final String fileName = "./pom.xml";
        startWebServer(new GrizzlyAdapter());

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
        assertEquals("Cached data mismatch", pattern, response1.getContent().toStringContent());

        final HttpRequestPacket request2 = HttpRequestPacket.builder()
                .method("GET")
                .uri("/pom.xml")
                .protocol("HTTP/1.1")
                .header("Host", "localhost")
                .header("If-Match", "W/\"" + file.length() + "-" + file.lastModified() + "\"")
                .header("If-Modified-Since", "" + file.lastModified())
                .build();

        final Future<HttpContent> responseFuture2 = send("localhost", PORT, request2);
        final HttpContent response2 = responseFuture2.get(1000, TimeUnit.SECONDS);

        assertEquals("304 is expected", 304, ((HttpResponsePacket) response2.getHttpHeader()).getStatus());
        assertTrue("empty body is expected", !response2.getContent().hasRemaining());
    }
    
    private void configureWebServer() throws Exception {
        gws = new GrizzlyWebServer();
        final GrizzlyListener listener =
                new GrizzlyListener("grizzly",
                                    GrizzlyListener.DEFAULT_NETWORK_HOST,
                                    PORT);
        if (isSslEnabled) {
            listener.setSecure(true);
            listener.setSSLEngineConfig(createSSLConfig(true));
        }

        gws.addListener(listener);
    }

    private void startWebServer(GrizzlyAdapter adapter) throws Exception {
        gws.getServerConfiguration().addGrizzlyAdapter(adapter);
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
}
