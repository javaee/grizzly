/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012 Oracle and/or its affiliates. All rights reserved.
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
package com.sun.grizzly.websockets;

import com.sun.grizzly.http.SelectorThread;
import com.sun.grizzly.tcp.Request;
import com.sun.grizzly.tcp.StaticResourcesAdapter;

import java.io.UnsupportedEncodingException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

public class PingPongTest extends BaseWebSocketTestUtilities {

    private static final int PORT = 9009;

    @Test
    public void testPingFromClientToServer() throws Exception {

        final CountDownLatch latch = new CountDownLatch(2);

        SelectorThread thread = createSelectorThread(PORT, new StaticResourcesAdapter());
        WebSocketApplication app = new WebSocketApplication() {
            @Override
            public boolean isApplicationRequest(Request request) {
                return (request.requestURI().toString().contains("/ping"));
            }

            @Override
            public void onPing(WebSocket socket, byte[] bytes) {
                System.out.println("[server] ping received!");
                super.onPing(socket, bytes);
                latch.countDown();
            }

        };
        WebSocketEngine.getEngine().register(app);

        WebSocketClient client = new WebSocketClient(
                "ws://localhost:" + PORT + "/ping",
                new WebSocketAdapter() {
                    @Override
                    public void onPong(WebSocket socket, byte[] bytes) {
                        System.out.println("[client] pong received!");
                        super.onPong(socket, bytes);
                        latch.countDown();
                    }
                });
        try {
            client.connect(5, TimeUnit.SECONDS);
            client.sendPing(null);
            assertTrue(latch.await(10, TimeUnit.SECONDS));
        } finally {
            client.close();
            thread.stopEndpoint();
            WebSocketEngine.getEngine().unregister(app);
        }
    }

    @Test
    public void testPingFromServerToClient() throws Exception {

        final CountDownLatch latch = new CountDownLatch(2);

        SelectorThread thread = createSelectorThread(PORT, new StaticResourcesAdapter());
        WebSocketApplication app = new WebSocketApplication() {
            @Override
            public boolean isApplicationRequest(Request request) {
                return (request.requestURI().toString().contains("/ping"));
            }

            @Override
            public void onConnect(WebSocket socket) {
                System.out.println("[server] client connected!");
                try {
                    socket.sendPing("Hi There!".getBytes("UTF-8"));
                } catch (UnsupportedEncodingException e) {
                    fail();
                }
            }

            @Override
            public void onPong(WebSocket socket, byte[] bytes) {
                System.out.println("[server] pong received!");
                latch.countDown();
            }
        };
        WebSocketEngine.getEngine().register(app);

        WebSocketClient client = new WebSocketClient(
                "ws://localhost:" + PORT + "/ping",
                new WebSocketAdapter() {
                    @Override
                    public void onPing(WebSocket socket, byte[] bytes) {
                        System.out.println("[client] ping received!");
                        super.onPing(socket, bytes);
                        latch.countDown();
                    }
                });
        try {
            client.connect(5, TimeUnit.SECONDS);
            assertTrue(latch.await(10, TimeUnit.SECONDS));
        } finally {
            WebSocketEngine.getEngine().unregister(app);
            client.close();
            thread.stopEndpoint();
        }
    }

    @Test
    public void testUnsolicitedPongFromClientToServer() throws Exception {

        final CountDownLatch latch = new CountDownLatch(1);
        SelectorThread thread = createSelectorThread(PORT, new StaticResourcesAdapter());
        WebSocketApplication app = new WebSocketApplication() {
            @Override
            public boolean isApplicationRequest(Request request) {
                return (request.requestURI().toString().contains("/ping"));
            }

            @Override
            public void onPong(WebSocket socket, byte[] bytes) {
                System.out.println("[server] pong received!");
                super.onPong(socket, bytes);
                latch.countDown();
            }
        };
        WebSocketEngine.getEngine().register(app);

        WebSocketClient client = new WebSocketClient(
                "ws://localhost:" + PORT + "/ping",
                new WebSocketAdapter() {
                    @Override
                    public void onMessage(WebSocket socket, String text) {
                        fail("No response expected for unsolicited pong");
                    }

                    @Override
                    public void onMessage(WebSocket socket, byte[] bytes) {
                        fail("No response expected for unsolicited pong");
                    }

                    @Override
                    public void onPing(WebSocket socket, byte[] bytes) {
                        fail("No response expected for unsolicited pong");
                    }

                    @Override
                    public void onPong(WebSocket socket, byte[] bytes) {
                        fail("No response expected for unsolicited pong");
                    }

                    @Override
                    public void onFragment(WebSocket socket, String fragment, boolean last) {
                        fail("No response expected for unsolicited pong");
                    }

                    @Override
                    public void onFragment(WebSocket socket, byte[] fragment, boolean last) {
                        fail("No response expected for unsolicited pong");
                    }
                });
        try {
            client.connect(5, TimeUnit.SECONDS);
            client.sendPong("pong".getBytes("UTF-8"));
            assertTrue(latch.await(10, TimeUnit.SECONDS));

            // give enough time for a response
            Thread.sleep(5000);
        } finally {
            WebSocketEngine.getEngine().unregister(app);
            client.close();
            thread.stopEndpoint();
        }
    }


    @Test
    public void testUnsolicitedPongFromServerToClient() throws Exception {

        final CountDownLatch latch = new CountDownLatch(1);
        SelectorThread thread = createSelectorThread(PORT, new StaticResourcesAdapter());
        WebSocketApplication app = new WebSocketApplication() {
            @Override
            public boolean isApplicationRequest(Request request) {
                return (request.requestURI().toString().contains("/ping"));
            }

            @Override
            public void onConnect(WebSocket socket) {
                try {
                    socket.sendPong("Surprise!".getBytes("UTF-8"));
                } catch (UnsupportedEncodingException e) {
                    fail();
                }
            }

            @Override
            public void onMessage(WebSocket socket, String text) {
                fail("No response expected for unsolicited pong");
            }

            @Override
            public void onMessage(WebSocket socket, byte[] bytes) {
                fail("No response expected for unsolicited pong");
            }

            @Override
            public void onPing(WebSocket socket, byte[] bytes) {
                fail("No response expected for unsolicited pong");
            }

            @Override
            public void onPong(WebSocket socket, byte[] bytes) {
                fail("No response expected for unsolicited pong");
            }

            @Override
            public void onFragment(WebSocket socket, String fragment, boolean last) {
                fail("No response expected for unsolicited pong");
            }

            @Override
            public void onFragment(WebSocket socket, byte[] fragment, boolean last) {
                fail("No response expected for unsolicited pong");
            }
        };
        WebSocketEngine.getEngine().register(app);

        WebSocketClient client = new WebSocketClient(
                "ws://localhost:" + PORT + "/ping",
                new WebSocketAdapter() {
                    @Override
                    public void onPong(WebSocket socket, byte[] bytes) {
                        System.out.println("[client] pong received!");
                        latch.countDown();
                    }
                });
        try {
            client.connect(5, TimeUnit.SECONDS);
            assertTrue(latch.await(10, TimeUnit.SECONDS));

            // give enough time for a response
            Thread.sleep(5000);
        } finally {
            WebSocketEngine.getEngine().unregister(app);
            client.close();
            thread.stopEndpoint();
        }
    }

}
