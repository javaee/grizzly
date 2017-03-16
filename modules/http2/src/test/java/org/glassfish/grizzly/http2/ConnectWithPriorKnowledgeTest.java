/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2016-2017 Oracle and/or its affiliates. All rights reserved.
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
import org.glassfish.grizzly.filterchain.FilterChain;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.http.HttpContent;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.http.Method;
import org.glassfish.grizzly.http.Protocol;
import org.glassfish.grizzly.http.server.HttpHandler;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.http.server.Response;
import org.glassfish.grizzly.memory.Buffers;
import org.glassfish.grizzly.nio.transport.TCPNIOConnectorHandler;
import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static junit.framework.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ConnectWithPriorKnowledgeTest extends AbstractHttp2Test {

    private static String MESSAGE = "ECHO ECHO ECHO";
    private static final int PORT = 18892;
    private HttpServer httpServer;


    // ----------------------------------------------------------- Test Methods


    @Test
    public void testConnectWithPriorKnowledge() throws Exception {
        configureHttpServer();
        startHttpServer();
        final CountDownLatch latch = new CountDownLatch(1);
        final Connection c = getConnection("localhost", PORT, latch);
        HttpRequestPacket.Builder builder = HttpRequestPacket.builder();
        HttpRequestPacket request = builder.method(Method.GET)
                .uri("/echo")
                .protocol(Protocol.HTTP_2_0)
                .host("localhost:" + PORT).build();
        c.write(HttpContent.builder(request).content(Buffers.EMPTY_BUFFER).last(true).build());
        assertTrue(latch.await(100, TimeUnit.SECONDS));
    }


    // -------------------------------------------------------- Private Methods


    private void configureHttpServer() throws Exception {
        httpServer = createServer(null, PORT, false, true);
        httpServer.getListener("grizzly").getKeepAlive().setIdleTimeoutInSeconds(-1);
    }

    private void startHttpServer() throws Exception {
        httpServer.getServerConfiguration().addHttpHandler(new HttpHandler() {
            @Override
            public void service(Request request, Response response) throws Exception {
                response.setContentType("text/plain");
                response.getWriter().write(MESSAGE);
            }
        }, "/echo");
        httpServer.start();
    }

    private Connection getConnection(final String host, final int port, final CountDownLatch latch)
            throws Exception {

        final FilterChain clientChain =
                createClientFilterChainAsBuilder(false, true, new BaseFilter() {
                    @Override
                    public NextAction handleRead(FilterChainContext ctx) throws IOException {
                        final HttpContent httpContent = ctx.getMessage();
                        if (httpContent.isLast()) {
                            assertEquals(MESSAGE, httpContent.getContent().toStringContent());
                            latch.countDown();
                        }
                        return ctx.getStopAction();
                    }
                }).build();

        SocketConnectorHandler connectorHandler = TCPNIOConnectorHandler.builder(
                httpServer.getListener("grizzly").getTransport())
                .processor(clientChain)
                .build();

        Future<Connection> connectFuture = connectorHandler.connect(host, port);
        return connectFuture.get(10, TimeUnit.SECONDS);

    }
}
