/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2013 Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.servlet.Servlet;

import org.glassfish.grizzly.GrizzlyFuture;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.grizzly.http.server.NetworkListener;
import org.glassfish.grizzly.http.server.ServerConfiguration;
import org.glassfish.grizzly.impl.FutureImpl;
import org.glassfish.grizzly.impl.SafeFutureImpl;
import org.glassfish.grizzly.servlet.ServletRegistration;
import org.glassfish.grizzly.servlet.WebappContext;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@SuppressWarnings({"StringContatenationInLoop"})
@RunWith(Parameterized.class)
public class WebSocketsTest extends BaseWebSocketTestUtilities {
    private static final int MESSAGE_COUNT = 5;
    private final Version version;

    public WebSocketsTest(Version version) {
        this.version = version;
    }

    @After
    public void tearDown() {
        WebSocketEngine.getEngine().unregisterAll();
    }
    
    @Test
    public void simpleConversationWithApplication() throws Exception {
        run(new EchoServlet());
    }

    private void run(final Servlet servlet) throws Exception {
        HttpServer httpServer = HttpServer.createSimpleServer(".", PORT);
        WebappContext ctx = new WebappContext("WS Test", "/");
        final ServletRegistration reg = ctx.addServlet("TestServlet", servlet);
        reg.addMapping("/");
        final ServerConfiguration configuration = httpServer.getServerConfiguration();
        configuration.setName("WebSocket Server");
        for (NetworkListener networkListener : httpServer.getListeners()) {
            networkListener.registerAddOn(new WebSocketAddOn());
        }
        ctx.deploy(httpServer);
        httpServer.start();

        final Set<String> sent = new ConcurrentSkipListSet<String>();
        final CountDownLatch connected = new CountDownLatch(1);
        final CountDownLatch received = new CountDownLatch(MESSAGE_COUNT);

        WebSocketClient client = null;
        try {
            client = new WebSocketClient(String.format("ws://localhost:%s/echo", PORT), version,
                new CountDownAdapter(sent, received, connected)) {
                @Override
                public GrizzlyFuture<DataFrame> send(String data) {
                    sent.add(data);
                    return super.send(data);
                }
            };

            client.connect();
            for (int count = 0; count < MESSAGE_COUNT; count++) {
                client.send("message " + count);
            }

            Assert.assertTrue(String.format("Waited %ss for the messages to echo back", WebSocketEngine.DEFAULT_TIMEOUT),
                received.await(WebSocketEngine.DEFAULT_TIMEOUT, TimeUnit.SECONDS));

            Assert.assertEquals(String.format("Should have received all %s messages back. " + sent, MESSAGE_COUNT), 0, sent.size());
        } catch (InterruptedException e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            if (client != null) {
                client.close();
            }
            httpServer.shutdownNow();
        }
    }

//    @Test
    public void ssl() throws Exception {
        WebSocketServer server = WebSocketServer.createServer(PORT);
        server.register("", "/echo", new EchoApplication());
        server.start();

        final EchoWebSocketApplication app = new EchoWebSocketApplication();
        WebSocketClient socket = null;
        try {
            WebSocketEngine.getEngine().register(app);
            socket = new WebSocketClient("wss://localhost:" + PORT + "/echo", version);
            socket.connect();

        } finally {
            if (socket != null) {
                socket.close();
            }
            server.stop();
        }
    }

    @Test
    public void testGetOnWebSocketApplication() throws IOException, InstantiationException, InterruptedException {
        final WebSocketApplication app = new WebSocketApplication() {
            public void onMessage(WebSocket socket, String data) {
                Assert.fail("A GET should never get here.");
            }

            @Override
            public boolean isApplicationRequest(HttpRequestPacket request) {
                return true;
            }
        };
        WebSocketEngine.getEngine().register(app);

        HttpServer httpServer = HttpServer.createSimpleServer(".", PORT);
        final ServerConfiguration configuration = httpServer.getServerConfiguration();
        WebappContext ctx = new WebappContext("WS Test", "/");
        final ServletRegistration registration =
                ctx.addServlet("TestServlet", new EchoServlet());
        registration.addMapping("/echo");
        configuration.setHttpServerName("WebSocket Server");
        configuration.setName("WebSocket Server");
        for (NetworkListener networkListener : httpServer.getListeners()) {
            networkListener.registerAddOn(new WebSocketAddOn());
        }
        ctx.deploy(httpServer);
        httpServer.start();

        URL url = new URL("http://localhost:" + PORT + "/echo");
        final URLConnection urlConnection = url.openConnection();
        final InputStream is = urlConnection.getInputStream();
        try {
            final byte[] bytes = new byte[1024];
            Assert.assertEquals(EchoServlet.RESPONSE_TEXT, new String(bytes, 0, is.read(bytes)));
        } finally {
            is.close();
            httpServer.shutdownNow();
        }
    }

    @Test
    public void testGetOnServlet() throws IOException, InstantiationException, InterruptedException {
        HttpServer httpServer = HttpServer.createSimpleServer(".", PORT);
        final ServerConfiguration configuration = httpServer.getServerConfiguration();
        WebappContext ctx = new WebappContext("WS Test", "/");
        final ServletRegistration registration =
                ctx.addServlet("TestServlet", new EchoServlet());
        registration.addMapping("/echo");
        configuration.setHttpServerName("WebSocket Server");
        configuration.setName("WebSocket Server");
        for (NetworkListener networkListener : httpServer.getListeners()) {
            networkListener.registerAddOn(new WebSocketAddOn());
        }
        ctx.deploy(httpServer);
        httpServer.start();

        URL url = new URL("http://localhost:" + PORT + "/echo");
        final URLConnection urlConnection = url.openConnection();
        final InputStream content = (InputStream) urlConnection.getContent();
        try {
            final byte[] bytes = new byte[1024];
            Assert.assertEquals(EchoServlet.RESPONSE_TEXT, new String(bytes, 0, content.read(bytes)));
        } finally {
            content.close();
            httpServer.shutdownNow();
        }
    }

    @Test
    public void testCloseHandler() throws Exception {
        final WebSocketApplication app = new WebSocketApplication() {
            @Override
            public boolean isApplicationRequest(HttpRequestPacket request) {
                return true;
            }
        };
        
        final HttpServer server = HttpServer.createSimpleServer(".", 8051);
        server.getListener("grizzly").registerAddOn(new WebSocketAddOn());
        WebSocketEngine.getEngine().register("", "/chat", app);
        
        final FutureImpl<Boolean> isConnectedStateWhenClosed = SafeFutureImpl.<Boolean>create();
        WebSocketClient client = new WebSocketClient("ws://localhost:8051/chat",
                new WebSocketAdapter() {
            @Override
            public void onClose(WebSocket socket, DataFrame frame) {
                isConnectedStateWhenClosed.result(socket.isConnected());
            }
        });
        
        
        try {
            server.start();
            client.connect();
            
            server.shutdownNow();
            
            Assert.assertFalse(isConnectedStateWhenClosed.get(10, TimeUnit.SECONDS));
        } finally {
            client.close();
            server.shutdownNow();
        }
    }
    
    private static class CountDownAdapter extends WebSocketAdapter {
        private final Set<String> sent;
        private final CountDownLatch received;
        private final CountDownLatch connected;

        public CountDownAdapter(Set<String> sent, CountDownLatch received, CountDownLatch connected) {
            this.sent = sent;
            this.received = received;
            this.connected = connected;
        }

        @Override
        public void onMessage(WebSocket socket, String data) {
            sent.remove(data);
            received.countDown();
        }

        @Override
        public void onConnect(WebSocket socket) {
            connected.countDown();
        }
    }
}
