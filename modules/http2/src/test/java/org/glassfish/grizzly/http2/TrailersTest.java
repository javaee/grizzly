/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2017 Oracle and/or its affiliates. All rights reserved.
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

import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.SocketConnectorHandler;
import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.Filter;
import org.glassfish.grizzly.filterchain.FilterChain;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.http.HttpContent;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.http.HttpTrailer;
import org.glassfish.grizzly.http.Method;
import org.glassfish.grizzly.http.Protocol;
import org.glassfish.grizzly.http.server.HttpHandler;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.http.server.Response;
import org.glassfish.grizzly.http.util.MimeHeaders;
import org.glassfish.grizzly.memory.Buffers;
import org.glassfish.grizzly.memory.MemoryManager;
import org.glassfish.grizzly.nio.transport.TCPNIOConnectorHandler;
import org.junit.After;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class TrailersTest extends AbstractHttp2Test {

    private static final int PORT = 18903;

    private HttpServer httpServer;

    // ----------------------------------------------------------- Test Methods

    @After
    public void tearDown() {
        if (httpServer != null) {
            httpServer.shutdownNow();
        }
    }


    @Test
    public void testTrailers() throws Exception {
        configureHttpServer();
        startHttpServer(new HttpHandler() {
            @Override
            public void service(Request request, Response response) throws Exception {
                response.setContentType("text/plain");
                final InputStream in = request.getInputStream();
                StringBuilder sb = new StringBuilder();
                int b;
                while ((b = in.read()) != -1) {
                    sb.append((char) b);
                }

                response.setTrailers(new Supplier<Map<String, String>>() {
                    @Override
                    public Map<String, String> get() {
                        return request.getTrailers();
                    }
                });
                response.getWriter().write(sb.toString());
                response.flush();
            }
        });
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<Throwable> error = new AtomicReference<>();

        final Filter filter = new BaseFilter() {
            @Override
            public NextAction handleRead(FilterChainContext ctx) throws IOException {
                final HttpContent httpContent = ctx.getMessage();
                try {
                    if (httpContent.isLast()) {
                        assertTrue(httpContent instanceof HttpTrailer);
                        final MimeHeaders trailers = ((HttpTrailer) httpContent).getHeaders();

                        assertEquals(2, trailers.size());
                        assertEquals("value-a", trailers.getHeader("trailer-a"));
                        assertEquals("value-b", trailers.getHeader("trailer-b"));
                        latch.countDown();
                    } else {
                        if (httpContent.getContent().remaining() > 0) {
                            assertEquals("a=b&c=d", httpContent.getContent().toStringContent());
                        }
                    }
                } catch (Throwable t) {
                    error.set(t);
                }

                return ctx.getStopAction();
            }
        };
        final Connection c = getConnection("localhost", PORT, filter);
        HttpRequestPacket.Builder builder = HttpRequestPacket.builder();
        HttpRequestPacket request = builder.method(Method.POST)
                .uri("/echo")
                .protocol(Protocol.HTTP_2_0)
                .host("localhost:" + PORT).build();
        c.write(HttpContent.builder(request)
                .content(Buffers.wrap(MemoryManager.DEFAULT_MEMORY_MANAGER, "a=b&c=d"))
                .last(false)
                .build());
        c.write(HttpTrailer.builder(request)
                .content(Buffers.EMPTY_BUFFER)
                .last(true)
                .header("trailer-a", "value-a")
                .header("trailer-b", "value-b")
                .build());
        assertTrue(latch.await(10, TimeUnit.SECONDS));
        final Throwable t = error.get();
        if (t != null) {
            t.printStackTrace();
            fail();
        }
    }

    @Test
    public void testNoContentTrailers() throws Exception {
        configureHttpServer();
        startHttpServer(new HttpHandler() {
            @Override
            public void service(Request request, Response response) throws Exception {
                response.setContentType("text/plain");
                final InputStream in = request.getInputStream();
                //noinspection StatementWithEmptyBody
                while (in.read() != -1) {
                }

                response.setTrailers(new Supplier<Map<String, String>>() {
                    @Override
                    public Map<String, String> get() {
                        return request.getTrailers();
                    }
                });
            }
        });
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<Throwable> error = new AtomicReference<>();

        final Filter filter = new BaseFilter() {
            @Override
            public NextAction handleRead(FilterChainContext ctx) throws IOException {
                final HttpContent httpContent = ctx.getMessage();
                try {
                    if (httpContent.isLast()) {
                        assertTrue(httpContent instanceof HttpTrailer);
                        final MimeHeaders trailers = ((HttpTrailer) httpContent).getHeaders();

                        assertEquals(2, trailers.size());
                        assertEquals("value-a", trailers.getHeader("trailer-a"));
                        assertEquals("value-b", trailers.getHeader("trailer-b"));
                        latch.countDown();
                    }
                } catch (Throwable t) {
                    error.set(t);
                }

                return ctx.getStopAction();
            }
        };
        final Connection c = getConnection("localhost", PORT, filter);
        HttpRequestPacket.Builder builder = HttpRequestPacket.builder();
        HttpRequestPacket request = builder.method(Method.POST)
                .uri("/echo")
                .protocol(Protocol.HTTP_2_0)
                .host("localhost:" + PORT).build();
        c.write(HttpContent.builder(request)
                .last(false)
                .build());
        c.write(HttpTrailer.builder(request)
                .content(Buffers.EMPTY_BUFFER)
                .last(true)
                .header("trailer-a", "value-a")
                .header("trailer-b", "value-b")
                .build());
        assertTrue(latch.await(10, TimeUnit.SECONDS));
        final Throwable t = error.get();
        if (t != null) {
            t.printStackTrace();
            fail();
        }
    }


    // -------------------------------------------------------- Private Methods


    private void configureHttpServer() throws Exception {
        httpServer = createServer(null, PORT, false, true);
        httpServer.getListener("grizzly").getKeepAlive().setIdleTimeoutInSeconds(-1);
    }

    private void startHttpServer(final HttpHandler handler) throws Exception {
        httpServer.getServerConfiguration().addHttpHandler(handler, "/echo");
        httpServer.start();
    }

    private Connection getConnection(final String host,
                                     final int port,
                                     final Filter clientFilter)
            throws Exception {

        final FilterChain clientChain =
                createClientFilterChainAsBuilder(false, true, clientFilter).build();

        SocketConnectorHandler connectorHandler = TCPNIOConnectorHandler.builder(
                httpServer.getListener("grizzly").getTransport())
                .processor(clientChain)
                .build();

        Future<Connection> connectFuture = connectorHandler.connect(host, port);
        return connectFuture.get(10, TimeUnit.SECONDS);

    }

}
