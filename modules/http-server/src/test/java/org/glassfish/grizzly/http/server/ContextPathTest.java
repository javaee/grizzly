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

package org.glassfish.grizzly.http.server;

import java.io.IOException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.SocketConnectorHandler;
import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.FilterChainBuilder;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.filterchain.TransportFilter;
import org.glassfish.grizzly.http.HttpClientFilter;
import org.glassfish.grizzly.http.HttpContent;
import org.glassfish.grizzly.http.HttpPacket;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.http.HttpResponsePacket;
import org.glassfish.grizzly.impl.FutureImpl;
import org.glassfish.grizzly.impl.SafeFutureImpl;
import org.glassfish.grizzly.memory.ByteBufferWrapper;
import org.glassfish.grizzly.nio.transport.TCPNIOConnectorHandler;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;


/**
 * Testing {@link HttpHandler} context-path
 * 
 * @author Alexey Stashok
 */
@SuppressWarnings("unchecked")
public class ContextPathTest {
    public static final int PORT = 18896;

    private HttpServer httpServer;

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

    @Test
    public void testRuntimeContextPathResolving() throws Exception {
        startHttpServer(new StaticHttpHandler() {

        }, "/context-path");

        final HttpRequestPacket request1 = HttpRequestPacket.builder()
                .method("GET")
                .uri("/pom.xml")
                .protocol("HTTP/1.1")
                .header("Host", "localhost")
                .build();

        final HttpRequestPacket request2 = HttpRequestPacket.builder()
                .method("GET")
                .uri("/context-path/pom.xml")
                .protocol("HTTP/1.1")
                .header("Host", "localhost")
                .build();

        final Future<HttpContent> responseFuture1 = send("localhost", PORT, request1);
        final HttpContent response1 = responseFuture1.get(10, TimeUnit.SECONDS);

        assertEquals(404, ((HttpResponsePacket) response1.getHttpHeader()).getStatus());

        final Future<HttpContent> responseFuture2 = send("localhost", PORT, request2);
        final HttpContent response2 = responseFuture2.get(10, TimeUnit.SECONDS);
        assertEquals(200, ((HttpResponsePacket) response2.getHttpHeader()).getStatus());
    }

    private void configureHttpServer() throws Exception {
        httpServer = new HttpServer();
        final NetworkListener listener =
                new NetworkListener("grizzly",
                NetworkListener.DEFAULT_NETWORK_HOST,
                PORT);

        httpServer.addListener(listener);
    }

    private void startHttpServer(HttpHandler httpHandler, String... mappings) throws Exception {
        httpServer.getServerConfiguration().addHttpHandler(httpHandler, mappings);
        httpServer.start();
    }

    private Future<HttpContent> send(String host, int port, HttpPacket request) throws Exception {
        final FutureImpl<HttpContent> future = SafeFutureImpl.create();

        final FilterChainBuilder builder = FilterChainBuilder.stateless();
        builder.add(new TransportFilter());

        builder.add(new HttpClientFilter());
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

    private static class HttpMessageFilter extends BaseFilter {

        private final FutureImpl<HttpContent> future;

        public HttpMessageFilter(FutureImpl<HttpContent> future) {
            this.future = future;
        }

        @Override
        public NextAction handleRead(FilterChainContext ctx) throws IOException {
            final HttpContent content = ctx.getMessage();
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
