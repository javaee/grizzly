/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2010 Sun Microsystems, Inc. All rights reserved.
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
 */

package com.sun.grizzly.websockets;

import com.sun.grizzly.http.SelectorThread;
import com.sun.grizzly.http.servlet.ServletAdapter;
import com.sun.grizzly.tcp.StaticResourcesAdapter;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Test
public class LifecycleTest {
    private static final String ADDRESS = "ws://localhost:" + WebSocketsTest.PORT + "/echo";

    public void detectClosed() throws Exception {
        final CountDownLatch close = new CountDownLatch(1);
        final SimpleWebSocketApplication app = new SimpleWebSocketApplication() {
            @Override
            public void onClose(WebSocket socket) throws IOException {
                super.onClose(socket);
                close.countDown();
            }
        };
        WebSocketEngine.getEngine().register("/echo", app);

        final SelectorThread thread =
                WebSocketsTest.createSelectorThread(WebSocketsTest.PORT, new StaticResourcesAdapter());

        try {
            Assert.assertEquals(app.getWebSockets().size(), 0, "There should be no clients connected");
            ClientWebSocketApplication wsClient = new ClientWebSocketApplication();
            final WebSocket client = wsClient.connect(ADDRESS).get();

            Assert.assertEquals(app.getWebSockets().size(), 1, "There should be 1 client connected");
            client.close();
            Thread.sleep(3000);
            close.await(30, TimeUnit.SECONDS);

            Assert.assertEquals(app.getWebSockets().size(), 0, "There should be 0 clients connected");
        } finally {
            thread.stopEndpoint();
        }

    }

    public void dirtyClose() throws Exception {
        final EchoServlet servlet = new EchoServlet();
        final CountDownLatch close = new CountDownLatch(1);
        final SimpleWebSocketApplication app = new SimpleWebSocketApplication() {
            @Override
            public void onClose(WebSocket socket) throws IOException {
                super.onClose(socket);
                close.countDown();
            }
        };
        WebSocketEngine.getEngine().register("/echo", app);

        final SelectorThread thread =
                WebSocketsTest.createSelectorThread(WebSocketsTest.PORT, new ServletAdapter(servlet));

        ClientWebSocketApplication wsClient = new BadClientWebSocketApplication();
        try {
            Assert.assertEquals(app.getWebSockets().size(), 0, "There should be no clients connected");

            BadWebSocketClient client = (BadWebSocketClient) wsClient.connect(ADDRESS).get();

            Assert.assertEquals(app.getWebSockets().size(), 1, "There should be 1 client connected");

            client.killConnection();

            close.await(30, TimeUnit.SECONDS);

            Assert.assertEquals(app.getWebSockets().size(), 0, "There should be 0 clients connected");
        } finally {
            thread.stopEndpoint();
            wsClient.stop();
        }

    }

    public void multipleClientClosing()
            throws IOException, InstantiationException, ExecutionException, InterruptedException {
        final CountDownLatch close = new CountDownLatch(1);
        final SimpleWebSocketApplication app = new SimpleWebSocketApplication() {
            @Override
            public void onClose(WebSocket socket) throws IOException {
                System.out.println("LifecycleTest.onClose: socket = " + socket);
                super.onClose(socket);
                close.countDown();
            }
        };
        WebSocketEngine.getEngine().register("/echo", app);

        final SelectorThread thread =
                WebSocketsTest.createSelectorThread(WebSocketsTest.PORT, new StaticResourcesAdapter());

        final AtomicBoolean received = new AtomicBoolean(false);
        try {
            System.out.println("\nclient 1 connecting");
            WebSocket client = newClient(received);

            System.out.println("\nclient 2 connecting");
            WebSocket client2 = newClient(received);

            System.out.println("\nclosing client 1");
            client.close();
            close.await(30, TimeUnit.SECONDS);
            checkSend(received, client);
            boolean connected = client2.isConnected();
            System.out.println("\nclosing client 2");
            client2.close();
            Thread.sleep(1000);
            Assert.assertTrue(connected);
            Assert.assertFalse(client2.isConnected());

            System.out.println("\nconnecting bad client");
            BadWebSocketClient badClient = (BadWebSocketClient) newClient(received);

            System.out.println("\nconnecting client 2 again");
            client2 = newClient(received);

            System.out.println("\nkilling bad client");
            badClient.killConnection();
            Thread.sleep(3000);
            checkSend(received, client2);
            connected = client2.isConnected();
            System.out.println("\nclosing client 2");
            client2.close();
            Assert.assertTrue(connected);

            Thread.sleep(3000);
            Assert.assertEquals(app.getWebSockets().size(), 0, "There should be 0 clients connected");
        } finally {
            thread.stopEndpoint();
        }

    }

    private WebSocket newClient(AtomicBoolean received) throws InterruptedException, ExecutionException, IOException {
        WebSocket client = new BadClientWebSocketApplication().connect(ADDRESS).get();
        client.add(new BadWebSocketListener(received));
        checkSend(received, client);
        return client;
    }

    private void checkSend(AtomicBoolean received, WebSocket client) throws IOException, InterruptedException {
        received.set(false);
        client.send("message");
        Thread.sleep(1000);
        Assert.assertTrue(received.get(), "Message should come back");
    }

    private static class BadWebSocketClient extends ClientWebSocket {
        public BadWebSocketClient(NetworkHandler handler, WebSocketListener... listeners) {
            super(handler, listeners);
        }

        void killConnection() throws IOException {
            ((ClientNetworkHandler) getNetworkHandler()).shutdown();
        }
    }

    private static class BadClientWebSocketApplication extends ClientWebSocketApplication {
        public BadClientWebSocketApplication() throws IOException {
        }

        @Override
        public WebSocket createSocket(NetworkHandler handler, WebSocketListener... listeners)
                throws IOException {
            return new BadWebSocketClient(handler, listeners);
        }
    }

    private static class BadWebSocketListener implements WebSocketListener {
        private final AtomicBoolean received;

        public BadWebSocketListener(AtomicBoolean received) {
            this.received = received;
        }

        public void onClose(WebSocket socket) throws IOException {
        }

        public void onConnect(WebSocket socket) throws IOException {
        }

        public void onMessage(WebSocket socket, DataFrame frame) throws IOException {
            received.set(true);
        }
    }
}
