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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.glassfish.grizzly.GrizzlyFuture;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class LifecycleTest extends BaseWebSocketTestUtilities {
    protected static final String BASE_URL = "ws://localhost:" + WebSocketsTest.PORT;
    private static final String ADDRESS = BASE_URL + "/echo";
    private CountDownLatch closeLatch;
    private CountDownLatch connectedLatch;
    private Version version;

    public LifecycleTest(Version version) {
        this.version = version;
    }

    @Test
    public void detectClosed() throws Exception {
        final String name = "/detect";
        final WebSocketApplication serverApp = createServerApp(name);
        WebSocketServer server = WebSocketServer.createServer(WebSocketsTest.PORT);
        server.register("", name, serverApp);
        server.start();
        try {
            Assert.assertEquals("There should be no clients connected", 0, serverApp.getWebSockets().size());
            BadWebSocketClient client = new BadWebSocketClient(BASE_URL + name, version);
            client.connect();
            connectedLatch.await(WebSocketEngine.DEFAULT_TIMEOUT, TimeUnit.SECONDS);
            Assert.assertEquals("There should be 1 client connected", 1, serverApp.getWebSockets().size());
            checkSend(client);
            client.close();
            Assert.assertTrue(client.waitForClosed());
            Assert.assertTrue("Should get the close event",
                closeLatch.await(WebSocketEngine.DEFAULT_TIMEOUT, TimeUnit.SECONDS));
            Assert.assertEquals("There should be 0 clients connected", 0, serverApp.getWebSockets().size());
        } finally {
            server.stop();
            WebSocketEngine.getEngine().unregister(serverApp);
        }
    }

    @Test
    public void dirtyClose() throws Exception {
        final String name = "/dirty";
        final WebSocketApplication app = createServerApp(name);
        WebSocketServer server = WebSocketServer.createServer(WebSocketsTest.PORT);
        server.register("", name, app);
        server.start();
        try {
            Assert.assertEquals("There should be no clients connected", 0, app.getWebSockets().size());
            BadWebSocketClient client = new BadWebSocketClient(BASE_URL + name, version);
            client.connect();
            Assert.assertTrue(connectedLatch.await(WebSocketEngine.DEFAULT_TIMEOUT, TimeUnit.SECONDS));
            Assert.assertEquals("There should be 1 client connected", 1, app.getWebSockets().size());
            client.killConnection();
            Assert.assertTrue("Should get the close event", closeLatch.await(1, TimeUnit.MINUTES));
            Assert.assertEquals("There should be 0 clients connected", 0, app.getWebSockets().size());
        } finally {
            server.stop();
        }

    }

    private WebSocketApplication createServerApp(final String name) {
        closeLatch = new CountDownLatch(1);
        connectedLatch = new CountDownLatch(1);
        return new WebSocketApplication() {
            @Override
            public void onConnect(WebSocket socket) {
                super.onConnect(socket);
                connectedLatch.countDown();
            }

            @Override
            public void onMessage(WebSocket socket, String text) {
                socket.send(text);
            }

            @Override
            public boolean isApplicationRequest(HttpRequestPacket request) {
                return true;
            }

            @Override
            public void onClose(WebSocket socket, DataFrame frame) {
                super.onClose(socket, frame);
                closeLatch.countDown();
            }
        };
    }

    @Test
    @Ignore
    public void multipleClientClosing() throws Exception {
        final CountDownLatch close = new CountDownLatch(1);
        final EchoWebSocketApplication app = new EchoWebSocketApplication() {
            @Override
            public void onClose(WebSocket socket, DataFrame frame) {
                super.onClose(socket, frame);
                close.countDown();
            }
        };
        WebSocketServer server = WebSocketServer.createServer(WebSocketsTest.PORT);
        server.register("", "/echo", app);
        server.start();
        try {
            cleanDisconnect(app);
            dirtyDisconnect(app);
        } finally {
            server.stop();
        }

    }

    private void dirtyDisconnect(EchoWebSocketApplication app) throws Exception {
        Assert.assertEquals("There should be 0 clients connected", 0, app.getWebSockets().size());
        BadWebSocketClient badClient = newClient();
        BadWebSocketClient client2 = newClient();
        badClient.killConnection();
        checkSend(client2);
        boolean connected = client2.isConnected();
        client2.close();
        Assert.assertTrue(connected);
        Thread.sleep(3000);
        Assert.assertEquals("There should be 0 clients connected", 0, app.getWebSockets().size());
    }

    private void cleanDisconnect(WebSocketApplication app) throws Exception {
        Assert.assertEquals("There should be 0 clients connected", 0, app.getWebSockets().size());
        BadWebSocketClient client = newClient();
        BadWebSocketClient client2 = newClient();
        Assert.assertTrue(client.isConnected());
        client.close();
        Assert.assertTrue(client.waitForClosed());
        Assert.assertFalse(client.isConnected());
        Assert.assertEquals("There should be 1 client connected", 1, app.getWebSockets().size());
        checkSend(client2);
        Assert.assertTrue(client2.isConnected());
        client2.close();
        Assert.assertTrue(client2.waitForClosed());
        Assert.assertFalse(client2.isConnected());
        Assert.assertEquals("There should be 0 clients connected", 0, app.getWebSockets().size());
    }

    private BadWebSocketClient newClient() throws Exception {
        BadWebSocketClient client = new BadWebSocketClient(ADDRESS, version);
        client.connect();
        checkSend(client);
        return client;
    }

    private void checkSend(BadWebSocketClient client) throws InterruptedException, ExecutionException {
        client.send("are you alive?");
        Assert.assertTrue("Message should come back", client.waitForMessage());
    }

    private static class BadWebSocketClient extends WebSocketClient {
        private CountDownLatch messages;
        private final CountDownLatch closed = new CountDownLatch(1);

        public BadWebSocketClient(String address, Version version, WebSocketListener... listeners) {
            super(address, version, listeners);
        }

        void killConnection() throws IOException {
            transport.shutdownNow();
        }

        @Override
        public GrizzlyFuture<DataFrame> send(String data) {
            messages = new CountDownLatch(1);
            return super.send(data);
        }

        @Override
        public void onClose(DataFrame frame) {
            super.onClose(frame);
            closed.countDown();
        }

        @Override
        public void onMessage(String frame) {
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
}
