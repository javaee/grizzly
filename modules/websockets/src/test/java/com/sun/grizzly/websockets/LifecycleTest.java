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
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@Test
public class LifecycleTest {
    public void detectClosed() throws IOException, InstantiationException, InterruptedException {
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

        try {
            Assert.assertEquals(app.getWebSockets().size(), 0, "There should be no clients connected");
            final CountDownLatch connect = new CountDownLatch(1);
            WebSocket client = new WebSocketClient("ws://localhost:" + WebSocketsTest.PORT + "/echo", new WebSocketListener() {
                public void onClose(WebSocket socket) {
                }

                public void onConnect(WebSocket socket) {
                    connect.countDown();
                }

                public void onMessage(WebSocket socket, DataFrame data) {
                }
            });
            client.connect();

            connect.await(30, TimeUnit.SECONDS);
            Assert.assertEquals(app.getWebSockets().size(), 1, "There should be 1 client connected");
            client.close();
            close.await(30, TimeUnit.SECONDS);

            Assert.assertEquals(app.getWebSockets().size(), 0, "There should be 0 clients connected");
        } finally {
            thread.stopEndpoint();
        }

    }

    public void dirtyClose() throws IOException, InstantiationException, InterruptedException {
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

        try {
            Assert.assertEquals(app.getWebSockets().size(), 0, "There should be no clients connected");
            final CountDownLatch connect = new CountDownLatch(1);
            BadWebSocketClient client = new BadWebSocketClient(connect);
            client.connect();

            connect.await(30, TimeUnit.SECONDS);
            Assert.assertEquals(app.getWebSockets().size(), 1, "There should be 1 client connected");

            client.killConnection();

            close.await(3000, TimeUnit.SECONDS);

            Assert.assertEquals(app.getWebSockets().size(), 0, "There should be 0 clients connected");
        } finally {
            thread.stopEndpoint();
        }

    }

    private static class BadWebSocketClient extends WebSocketClient {
        public BadWebSocketClient(final CountDownLatch connect) throws IOException {
            super("ws://localhost:" + WebSocketsTest.PORT + "/echo", new WebSocketListener() {
                public void onClose(WebSocket socket) {
                }

                public void onConnect(WebSocket socket) {
                    connect.countDown();
                }

                public void onMessage(WebSocket socket, DataFrame data) {
                }
            });
        }

        void killConnection() throws IOException {
            getKey().cancel();
            getChannel().close();
        }
    }
}
