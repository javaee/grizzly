/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010 Oracle and/or its affiliates. All rights reserved.
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
 * file and include the License file at glassfish/bootstrap/legal/LICENSE.txt.
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

package com.sun.grizzly.websockets;

import com.sun.grizzly.http.SelectorThread;
import com.sun.grizzly.http.servlet.ServletAdapter;
import com.sun.grizzly.tcp.StaticResourcesAdapter;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@Test
public class LifecycleTest {
    private static final String ADDRESS = "ws://localhost:" + WebSocketsTest.PORT + "/echo";

    public void detectClosed() throws Exception {
        final CountDownLatch close = new CountDownLatch(1);
        final SimpleWebSocketApplication serverApp = new SimpleWebSocketApplication() {
            @Override
            public void onClose(WebSocket socket) throws IOException {
                super.onClose(socket);
                close.countDown();
            }
        };
        WebSocketEngine.getEngine().register("/echo", serverApp);

        final SelectorThread thread = WebSocketsTest.createSelectorThread(WebSocketsTest.PORT, new StaticResourcesAdapter());

        try {
            Assert.assertEquals(serverApp.getWebSockets().size(), 0, "There should be no clients connected");

            BadClientWebSocketApplication clientApp = new BadClientWebSocketApplication(ADDRESS);
            final BadWebSocketClient client = (BadWebSocketClient) clientApp.connect();

            Assert.assertEquals(serverApp.getWebSockets().size(), 1, "There should be 1 client connected");
            
            client.close();
            client.waitForClosed();
            close.await(WebSocketEngine.DEFAULT_TIMEOUT, TimeUnit.SECONDS);
            
            Assert.assertEquals(serverApp.getWebSockets().size(), 0, "There should be 0 clients connected");
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

        ClientWebSocketApplication wsClient = new BadClientWebSocketApplication(ADDRESS);
        try {
            Assert.assertEquals(app.getWebSockets().size(), 0, "There should be no clients connected");

            BadWebSocketClient client = (BadWebSocketClient) wsClient.connect();
            Assert.assertEquals(app.getWebSockets().size(), 1, "There should be 1 client connected");

            client.killConnection();
            close.await(WebSocketEngine.DEFAULT_TIMEOUT, TimeUnit.SECONDS);
            Assert.assertEquals(app.getWebSockets().size(), 0, "There should be 0 clients connected");
        } finally {
            thread.stopEndpoint();
            wsClient.stop();
        }

    }

    public void multipleClientClosing() throws Exception {
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
            cleanDisconnect();
            dirtyDisconnect();
        } finally {
            thread.stopEndpoint();
        }

    }

    private void dirtyDisconnect() throws Exception {
        boolean connected;

        final BadClientWebSocketApplication app1 = new BadClientWebSocketApplication(ADDRESS);
        BadWebSocketClient badClient = newClient(app1);
        BadWebSocketClient client2 = newClient(app1);

        badClient.killConnection();

        checkSend(client2);
        connected = client2.isConnected();
        client2.close();
        Assert.assertTrue(connected);

        Thread.sleep(3000);
        Assert.assertEquals(app1.getWebSockets().size(), 0, "There should be 0 clients connected");
    }

    private void cleanDisconnect() throws Exception {

        final BadClientWebSocketApplication app = new BadClientWebSocketApplication(ADDRESS);
        BadWebSocketClient client = newClient(app);
        BadWebSocketClient client2 = newClient(app);

        client.close();
        client.waitForClosed();
        checkSend(client2);
        Assert.assertTrue(client2.isConnected());

        client2.close();
        client2.waitForClosed();
        Assert.assertFalse(client2.isConnected());

        Assert.assertEquals(app.getWebSockets().size(), 0, "There should be 0 clients connected");
    }

    private BadWebSocketClient newClient(final BadClientWebSocketApplication app) throws Exception {
        BadWebSocketClient client = (BadWebSocketClient) app.connect();
        checkSend(client);
        return client;
    }

    private void checkSend(BadWebSocketClient client) throws IOException, InterruptedException {
        client.send("message");
        Assert.assertTrue(client.waitForMessage(), "Message should come back");
    }

    private static class BadWebSocketClient extends ClientWebSocket {
        private CountDownLatch messages;
        private final CountDownLatch closed = new CountDownLatch(1);

        public BadWebSocketClient(NetworkHandler handler, WebSocketListener... listeners) {
            super(handler, listeners);
        }

        void killConnection() throws IOException {
            ((ClientNetworkHandler) getNetworkHandler()).shutdown();
        }

        @Override
        public void send(String data) throws IOException {
            messages = new CountDownLatch(1);
            super.send(data);
        }

        @Override
        public void onClose() throws IOException {
            super.onClose();
            closed.countDown();
        }

        @Override
        public void onMessage(DataFrame frame) throws IOException {
            super.onMessage(frame);
            messages.countDown();
        }

        public boolean waitForMessage() throws InterruptedException {
            return messages.await(WebSocketEngine.DEFAULT_TIMEOUT, TimeUnit.SECONDS);
        }

        public boolean waitForClosed() throws InterruptedException {
            return closed.await(WebSocketEngine.DEFAULT_TIMEOUT, TimeUnit.SECONDS);
        }

    }

    private static class BadClientWebSocketApplication extends ClientWebSocketApplication {
        public BadClientWebSocketApplication(String address) throws IOException {
            super(address);
        }

        @Override
        public WebSocket createSocket(NetworkHandler handler, WebSocketListener... listeners) throws IOException {
            return new BadWebSocketClient(handler, listeners);
        }
    }

}
