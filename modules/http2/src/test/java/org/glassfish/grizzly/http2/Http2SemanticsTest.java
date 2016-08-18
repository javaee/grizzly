/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2016 Oracle and/or its affiliates. All rights reserved.
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
import org.glassfish.grizzly.filterchain.Filter;
import org.glassfish.grizzly.filterchain.FilterChain;
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
import org.glassfish.grizzly.utils.Charsets;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Collection;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

@RunWith(Parameterized.class)
public class Http2SemanticsTest extends AbstractHttp2Test {

    private final boolean isSecure;
    private HttpServer httpServer;
    private static final int PORT = 18893;


    // ----------------------------------------------------------- Constructors


    public Http2SemanticsTest(final boolean isSecure) {
        this.isSecure = isSecure;
    }


    // -------------------------------------------------- Junit Support Methods


    @Parameterized.Parameters
    public static Collection<Object[]> isSecure() {
        return AbstractHttp2Test.isSecure();
    }

    @Before
    public void before() throws Exception {
        configureHttpServer();
    }

    @After
    public void after() throws Exception {
        httpServer.shutdownNow();
    }


    // ----------------------------------------------------------- Test Methods


    @Test
    public void invalidHeaderCharactersTest() throws Exception {
        startHttpServer(new HttpHandler() {
            @Override
            public void service(Request request, Response response) throws Exception {
                response.setContentType("text/plain");
                response.getWriter().write("FAILED");
            }
        }, "/path");

        byte[] headerName = "test".getBytes();
        byte[] temp = new byte[headerName.length + 1];
        System.arraycopy(headerName, 0, temp, 0, headerName.length);
        temp[temp.length - 1] = 0x7; //visual bell

        final Connection c = getConnection("localhost", PORT, null);
        HttpRequestPacket.Builder builder = HttpRequestPacket.builder();
        HttpRequestPacket request = builder.method(Method.GET)
                .uri("/echo")
                .protocol(Protocol.HTTP_1_1)
                .host("localhost:" + PORT).build();
        request.setHeader(new String(temp, Charsets.ASCII_CHARSET), "value");
        c.write(HttpContent.builder(request).content(Buffers.EMPTY_BUFFER).last(true).build());
        Thread.sleep(1000);
        final Http2Stream stream = Http2Stream.getStreamFor(request);
        assertNotNull(stream);
        assertFalse(stream.isOpen());
    }


    // -------------------------------------------------------- Private Methods


    private void configureHttpServer() throws Exception {
        httpServer = createServer(null, PORT, isSecure, true);
        httpServer.getListener("grizzly").getKeepAlive().setIdleTimeoutInSeconds(-1);
    }

    private void startHttpServer(final HttpHandler handler, final String path) throws Exception {
        httpServer.getServerConfiguration().addHttpHandler(handler, path);
        httpServer.start();
    }

    private Connection getConnection(final String host,
                                     int port,
                                     final Filter filter)
            throws Exception {

        final FilterChain clientChain =
                createClientFilterChainAsBuilder(isSecure).build();

        if (filter != null) {
            clientChain.add(filter);
        }

        final int idx = clientChain.indexOfType(Http2ClientFilter.class);
        assert (idx != -1);
        final Http2ClientFilter clientFilter = (Http2ClientFilter) clientChain.get(idx);
        clientFilter.setPriorKnowledge(true);


        SocketConnectorHandler connectorHandler = TCPNIOConnectorHandler.builder(
                httpServer.getListener("grizzly").getTransport())
                .processor(clientChain)
                .build();

        Future<Connection> connectFuture = connectorHandler.connect(host, port);
        return connectFuture.get(10, TimeUnit.SECONDS);
    }

}
