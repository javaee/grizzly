/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2011 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.websockets;

import org.glassfish.grizzly.NIOTransportBuilder;
import org.glassfish.grizzly.filterchain.FilterChain;
import org.glassfish.grizzly.filterchain.FilterChainBuilder;
import org.glassfish.grizzly.filterchain.TransportFilter;
import org.glassfish.grizzly.http.HttpClientFilter;
import org.glassfish.grizzly.http.HttpServerFilter;
import org.glassfish.grizzly.impl.FutureImpl;
import org.glassfish.grizzly.impl.SafeFutureImpl;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import org.glassfish.grizzly.utils.ChunkingFilter;
import org.glassfish.grizzly.websockets.frame.Frame;
import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import junit.framework.TestCase;

/**
 * Test basic {@link WebSocket} communication.
 *
 * @author Alexey Stashok
 */
public class WSCommTest extends TestCase {
    public static int PORT = 11001;

    public void testEchoText() throws Exception {
        FilterChainBuilder serverFilterChainBuilder = FilterChainBuilder.stateless();
        serverFilterChainBuilder.add(new TransportFilter());
        serverFilterChainBuilder.add(new ChunkingFilter(2));
        serverFilterChainBuilder.add(new HttpServerFilter());
        serverFilterChainBuilder.add(new WebSocketFilter());

        TCPNIOTransport transport = NIOTransportBuilder.defaultTCPTransportBuilder().build();
        transport.setProcessor(serverFilterChainBuilder.build());

        FutureImpl<String> serverFuture = SafeFutureImpl.create();
        FutureImpl<String> clientFuture = SafeFutureImpl.create();

        final EchoApplication echoApplication = new EchoApplication(serverFuture);

        WebSocketEngine.getEngine().registerApplication("/echo", echoApplication);
        WebSocket clientWebSocket = null;

        try {
            transport.bind(PORT);
            transport.start();

            FilterChainBuilder clientFilterChainBuilder = FilterChainBuilder.stateless();
            clientFilterChainBuilder.add(new TransportFilter());
            clientFilterChainBuilder.add(new ChunkingFilter(2));
            clientFilterChainBuilder.add(new HttpClientFilter());
            clientFilterChainBuilder.add(new WebSocketFilter());

            FilterChain clientFilterChain = clientFilterChainBuilder.build();

            WebSocketConnectorHandler connectorHandler =
                    new WebSocketConnectorHandler(transport, clientFilterChain);

            final String message = "Hello world. Non secured";
            MyClientWSHandler clientWSHandler = new MyClientWSHandler( message, clientFuture);
            Future<WebSocket> connectFuture = connectorHandler.connect(new URI("ws://localhost:" + PORT + "/echo"), clientWSHandler);
            final WebSocket ws = connectFuture.get(10, TimeUnit.SECONDS);

            assertNotNull(ws);

            try {
                assertEquals(message, serverFuture.get(10, TimeUnit.SECONDS));
                assertEquals("Echo: " + message, clientFuture.get(10, TimeUnit.SECONDS));
            } catch (TimeoutException e) {
                assertTrue("Timeout. server-state=" + echoApplication.getState() +
                        " client-state=" +  clientWSHandler.getState(), false);
            }

        } finally {
            if (clientWebSocket != null) {
                clientWebSocket.close();
            }

            transport.stop();
        }
    }

    private static class MyClientWSHandler extends WebSocketClientHandler {
        private volatile String state = "INITIAL";

        private final String message;

        final FutureImpl<String> cycleCompleteFuture;

        public MyClientWSHandler(String message, FutureImpl<String> cycleCompleteFuture) {
            this.message = message;
            this.cycleCompleteFuture = cycleCompleteFuture;
        }

        @Override
        public void onConnect(WebSocket socket) throws IOException {
            if (socket.getMeta().isSecure()) {
                final IllegalStateException e = new IllegalStateException("Client websocket is secured?");
                cycleCompleteFuture.failure(e);
                throw e;
            }

            state = "CONNECTED";
            Frame frame = Frame.createTextFrame(message);
            socket.send(frame);
            state = "SENT";
        }

        @Override
        public void onClose(WebSocket socket) {
            cycleCompleteFuture.failure(new ConnectException());
        }

        @Override
        public void onMessage(WebSocket socket, Frame data) {
            state = "GOT RESPONSE";
            cycleCompleteFuture.result(data.getAsText());
        }

        public String getState() {
            return state;
        }
    }

    private static class EchoApplication extends WebSocketApplication {
        private volatile String state = "INITIAL";
        final FutureImpl<String> cycleCompleteFuture;

        public EchoApplication(FutureImpl<String> cycleCompleteFuture) {
            this.cycleCompleteFuture = cycleCompleteFuture;
        }

        @Override
        public void onAccept(WebSocket socket) throws IOException {
            super.onAccept(socket);
            if (socket.getMeta().isSecure()) {
                final IllegalStateException e = new IllegalStateException("Server websocket is secured?");
                cycleCompleteFuture.failure(e);
                throw e;
            }

            state = "ACCEPTED";
        }

        @Override
        public void onMessage(WebSocket socket, Frame data) throws IOException {
            state = "GOT MESSAGE";
            socket.send(Frame.createTextFrame("Echo: " + data.getAsText()));
            
            state = "SENT";
            cycleCompleteFuture.result(data.getAsText());
        }

        public String getState() {
            return state;
        }
    }
}
