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

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.servlet.Servlet;

import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.grizzly.http.server.NetworkListener;
import org.glassfish.grizzly.http.server.ServerConfiguration;
import org.glassfish.grizzly.servlet.ServletHandler;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

@SuppressWarnings({"StringContatenationInLoop"})
public class WebSocketsTest {
    private static final Object SLUG = new Object();
    private static final int MESSAGE_COUNT = 10;
    public static final int PORT = 1725;

    @Before
    public void enable() {
        WebSocketEngine.setWebSocketEnabled(true);
    }

    @Test
    public void simpleConversationWithApplication() throws Exception {
        run(new EchoServlet());
    }


    @Test(expected = IllegalStateException.class)
    public void websocketsNotEnabled() {
        WebSocketEngine.setWebSocketEnabled(false);
        final WebSocketApplication app = new WebSocketApplication() {
            @Override
            public boolean isApplicationRequest(HttpRequestPacket request) {
                return false;
            }
        };
        try {
            WebSocketEngine.getEngine().register(app);
        } finally {
            WebSocketEngine.getEngine().unregister(app);
            WebSocketEngine.setWebSocketEnabled(true);
        }
    }

    private void run(final Servlet servlet) throws Exception {
        WebSocketEngine.setWebSocketEnabled(true);
        HttpServer httpServer = HttpServer.createSimpleServer(".", PORT);
        final ServerConfiguration configuration = httpServer.getServerConfiguration();
        configuration.addHttpHandler(new ServletHandler(servlet));
        configuration.setHttpServerName("WebSocket Server");
        configuration.setName("WebSocket Server");
        for (NetworkListener networkListener : httpServer.getListeners()) {
            networkListener.registerAddOn(new WebSocketAddOn());
        }
        httpServer.start();

        final Map<String, Object> sent = new ConcurrentHashMap<String, Object>();
        final CountDownLatch connected = new CountDownLatch(1);
        final CountDownLatch received = new CountDownLatch(MESSAGE_COUNT);

        WebSocketClient client = null;
        try {
            client = new WebSocketClient(String.format("ws://localhost:%s/echo", PORT), new WebSocketAdapter() {
                @Override
                public void onMessage(WebSocket socket, String data) {
                    sent.remove(data);
                    received.countDown();
                }

                @Override
                public void onConnect(WebSocket socket) {
                    connected.countDown();
                }
            });

            client.connect();
            for (int count = 0; count < MESSAGE_COUNT; count++) {
                send(client, sent, "message " + count);
            }

            Assert.assertTrue(String.format("Waited %ss for the messages to echo back", WebSocketEngine.DEFAULT_TIMEOUT),
                received.await(WebSocketEngine.DEFAULT_TIMEOUT, TimeUnit.SECONDS));

            Assert.assertEquals(String.format("Should have received all %s messages back.", MESSAGE_COUNT), 0, sent.size());
        } catch (InterruptedException e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            if (client != null) {
                client.close();
            }
            httpServer.stop();
            WebSocketEngine.getEngine().unregisterAll();
        }
    }

    @Test
    public void timeouts() throws Exception {
        WebSocketServer server = new WebSocketServer(PORT);
        server.register("/echo", new EchoApplication());
        server.start();

        WebSocketClient client = null;
        final EchoWebSocketApplication app = new EchoWebSocketApplication();
        try {
            WebSocketEngine.getEngine().register(app);
            final Map<String, Object> messages = new ConcurrentHashMap<String, Object>();
            final CountDownLatch received = new CountDownLatch(MESSAGE_COUNT);
            client = new WebSocketClient(String.format("ws://localhost:%s/echo", PORT), new WebSocketAdapter() {
                @Override
                public void onMessage(WebSocket socket, String data) {
                    messages.remove(data);
                    received.countDown();
                }
            });
            client.connect();

            for (int index = 0; index < MESSAGE_COUNT; index++) {
                send(client, messages, "test " + index);
                Thread.sleep(5000);
            }

            Assert.assertTrue(String.format("Waited %ss for everything to come back", WebSocketEngine.DEFAULT_TIMEOUT),
                received.await(WebSocketEngine.DEFAULT_TIMEOUT, TimeUnit.SECONDS));
            Assert.assertTrue("All messages should have been echoed back: " + messages, messages.isEmpty());
        } finally {
            WebSocketEngine.getEngine().unregister(app);
            if (client != null) {
                client.close();
            }
            if (server != null) {
                server.stop();
            }
        }
    }

    @Test
    public void ssl() throws Exception {
        WebSocketServer server = new WebSocketServer(PORT);
        server.register("/echo", new EchoApplication());
        server.start();

        final EchoWebSocketApplication app = new EchoWebSocketApplication();
        WebSocketClient socket = null;
        try {
            WebSocketEngine.getEngine().register(app);
            socket = new WebSocketClient("wss://localhost:" + PORT + "/echo");
            socket.connect();

        } finally {
            if (socket != null) {
                socket.close();
            }
            server.stop();
            WebSocketEngine.getEngine().unregister(app);
        }
    }

    private void send(WebSocket client, Map<String, Object> messages, final String message) {
        messages.put(message, SLUG);
        client.send(message);
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

            public void onConnect(WebSocket socket) {
            }

            public void onClose(WebSocket socket) {
            }
        };
        WebSocketEngine.getEngine().register(app);

        WebSocketEngine.setWebSocketEnabled(true);
        HttpServer httpServer = HttpServer.createSimpleServer(".", PORT);
        final ServerConfiguration configuration = httpServer.getServerConfiguration();
        configuration.addHttpHandler(new ServletHandler(new EchoServlet()), "/echo");
        configuration.setHttpServerName("WebSocket Server");
        configuration.setName("WebSocket Server");
        for (NetworkListener networkListener : httpServer.getListeners()) {
            networkListener.registerAddOn(new WebSocketAddOn());
        }
        httpServer.start();

        URL url = new URL("http://localhost:" + PORT + "/echo");
        final URLConnection urlConnection = url.openConnection();
        final InputStream is = urlConnection.getInputStream();
        try {
            final byte[] bytes = new byte[1024];
            final int i = is.read(bytes);
            final String text = new String(bytes, 0, i);
            Assert.assertEquals(EchoServlet.RESPONSE_TEXT, text);
        } finally {
            is.close();
            httpServer.stop();
            WebSocketEngine.getEngine().unregister(app);
        }
    }

/*
    public void testGetOnServlet() throws IOException, InstantiationException, InterruptedException {
        final SelectorThread thread = createSelectorThread(PORT, new ServletAdapter(new EchoServlet()));
        URL url = new URL("http://localhost:" + PORT + "/echo");
        final URLConnection urlConnection = url.openConnection();
        final InputStream content = (InputStream) urlConnection.getContent();
        try {
            final byte[] bytes = new byte[1024];
            final int i = content.read(bytes);
            Assert.assertEquals(EchoServlet.RESPONSE_TEXT, new String(bytes, 0, i));
        } finally {
            content.close();
            thread.stopEndpoint();
        }
    }
*/

/*
    public void testSimpleConversationWithoutApplication()
            throws IOException, InstantiationException, InterruptedException {
        run(new HttpServlet() {
            @Override
            protected void service(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                resp.setContentType("text/plain; charset=iso-8859-1");
                resp.getWriter().write(req.getReader().readLine());
                resp.getWriter().flush();
            }
        });
    }
*/
}
