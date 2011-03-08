/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.http.ajp;

import org.glassfish.grizzly.memory.MemoryManager;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import java.io.IOException;
import org.glassfish.grizzly.filterchain.BaseFilter;
import java.util.concurrent.TimeUnit;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.nio.transport.TCPNIOConnectorHandler;
import org.glassfish.grizzly.SocketConnectorHandler;
import org.glassfish.grizzly.filterchain.TransportFilter;
import org.glassfish.grizzly.filterchain.FilterChainBuilder;
import org.glassfish.grizzly.impl.SafeFutureImpl;
import org.glassfish.grizzly.impl.FutureImpl;
import org.glassfish.grizzly.http.HttpContent;
import java.util.concurrent.Future;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.http.server.HttpHandler;
import org.glassfish.grizzly.http.server.NetworkListener;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.http.server.Response;
import org.glassfish.grizzly.memory.ByteBufferWrapper;
import org.glassfish.grizzly.http.server.HttpServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Test simple Ajp communication usecases.
 * 
 * @author Alexey Stashok
 */
public class BasicAjpTest {
    public static final int PORT = 19012;

    private AjpAddOn ajpAddon;
    private HttpServer httpServer;

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
    public void testPingPong() throws Exception {
        startHttpServer(new HttpHandler() {

            @Override
            public void service(Request request, Response response) throws Exception {
            }

        }, "/");

        final MemoryManager mm =
                httpServer.getListener("grizzly").getTransport().getMemoryManager();
        final Buffer request = mm.allocate(512);
        request.put((byte) 0x12);
        request.put((byte) 0x34);
        request.putShort((short) 1);
        request.put(AjpConstants.JK_AJP13_CPING_REQUEST);
        request.flip();

        final Future<Buffer> responseFuture = send("localhost", PORT, request);
        Buffer response = responseFuture.get(10, TimeUnit.SECONDS);

        assertEquals('A', response.get());
        assertEquals('B', response.get());
        assertEquals((short) 1, response.getShort());
        assertEquals(AjpConstants.JK_AJP13_CPONG_REPLY, response.get());
    }
    
    @Test
    public void testShutdownHandler() throws Exception {
        final FutureImpl<Boolean> shutdownFuture = SafeFutureImpl.<Boolean>create();
        final ShutdownHandler shutDownHandler = new ShutdownHandler() {

            @Override
            public void onShutdown(Connection initiator) {
                shutdownFuture.result(true);
            }
        };

        AjpAddOn myAjpAddon = new AjpAddOn() {

            @Override
            protected AjpHandlerFilter createAjpHandlerFilter() {
                final AjpHandlerFilter filter = new AjpHandlerFilter();
                filter.addShutdownHandler(shutDownHandler);
                return filter;
            }
        };

        final NetworkListener listener = httpServer.getListener("grizzly");

        listener.deregisterAddOn(ajpAddon);
        listener.registerAddOn(myAjpAddon);

        startHttpServer(new HttpHandler() {

            @Override
            public void service(Request request, Response response) throws Exception {
            }

        }, "/");

        final MemoryManager mm =
                listener.getTransport().getMemoryManager();
        final Buffer request = mm.allocate(512);
        request.put((byte) 0x12);
        request.put((byte) 0x34);
        request.putShort((short) 1);
        request.put(AjpConstants.JK_AJP13_SHUTDOWN);
        request.flip();

        send("localhost", PORT, request);
        final Boolean b = shutdownFuture.get(10, TimeUnit.SECONDS);
        assertTrue(b);
    }

    private Future<Buffer> send(String host, int port, Buffer request) throws Exception {
        final FutureImpl<Buffer> future = SafeFutureImpl.<Buffer>create();

        final FilterChainBuilder builder = FilterChainBuilder.stateless();
        builder.add(new TransportFilter());

        builder.add(new AjpClientMessageFilter());
        builder.add(new ResultFilter(future));

        SocketConnectorHandler connectorHandler = TCPNIOConnectorHandler.builder(
                httpServer.getListener("grizzly").getTransport())
                .processor(builder.build())
                .build();

        Future<Connection> connectFuture = connectorHandler.connect(host, port);
        final Connection connection = connectFuture.get(10, TimeUnit.SECONDS);

        connection.write(request);

        return future;
    }

    private void configureHttpServer() throws Exception {
        httpServer = new HttpServer();
        final NetworkListener listener =
                new NetworkListener("grizzly",
                NetworkListener.DEFAULT_NETWORK_HOST,
                PORT);

        ajpAddon = new AjpAddOn();
        listener.registerAddOn(ajpAddon);
        
        httpServer.addListener(listener);
    }

    private void startHttpServer(HttpHandler httpHandler, String... mappings) throws Exception {
        httpServer.getServerConfiguration().addHttpHandler(httpHandler, mappings);
        httpServer.start();
    }

    private static class ResultFilter extends BaseFilter {

        private final FutureImpl<Buffer> future;

        public ResultFilter(FutureImpl<Buffer> future) {
            this.future = future;
        }

        @Override
        public NextAction handleRead(FilterChainContext ctx) throws IOException {
            final Buffer content = ctx.getMessage();
            try {
                future.result(content);
            } catch (Exception e) {
                future.failure(e);
                e.printStackTrace();
            }

            return ctx.getStopAction();
        }
    }
}
