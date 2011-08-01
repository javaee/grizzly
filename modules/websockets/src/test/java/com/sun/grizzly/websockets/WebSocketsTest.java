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

package com.sun.grizzly.websockets;

import com.sun.grizzly.http.SelectorThread;
import com.sun.grizzly.http.servlet.ServletAdapter;
import com.sun.grizzly.tcp.Request;
import com.sun.grizzly.tcp.StaticResourcesAdapter;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import javax.servlet.Servlet;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@SuppressWarnings({"StringContatenationInLoop"})
@RunWith(Parameterized.class)
public class WebSocketsTest extends BaseWebSocketTestUtilities {
    private static final Object SLUG = new Object();
    private static final int MESSAGE_COUNT = 5;
    private final Version version;

    public WebSocketsTest(Version version) {
        this.version = version;
    }

    @Test
    public void simpleConversationWithApplication() throws Exception {
        run(new EchoServlet());
    }

    private void run(final Servlet servlet) throws Exception {
        final SelectorThread thread = createSelectorThread(PORT, new ServletAdapter(servlet));
        WebSocketClient client = null;
        try {
            final Map<String, Object> sent = new ConcurrentHashMap<String, Object>();
            final CountDownLatch connected = new CountDownLatch(1);
            final CountDownLatch received = new CountDownLatch(MESSAGE_COUNT);

            client = new WebSocketClient(String.format("ws://localhost:%s/echo", PORT), version, new WebSocketAdapter() {
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

            Assert.assertTrue(
                    String.format("Waited %ss for the messages to echo back", WebSocketEngine.DEFAULT_TIMEOUT),
                    received.await(WebSocketEngine.DEFAULT_TIMEOUT, TimeUnit.SECONDS));

            Assert.assertEquals(String.format("Should have received all %s messages back.",
                    MESSAGE_COUNT), 0, sent.size());
        } catch (InterruptedException e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            thread.stopEndpoint();
            if (client != null) {
                client.close();
            }
        }
    }

    @Test
    public void ssl() throws Exception {
        SelectorThread thread = null;
        final EchoWebSocketApplication app = new EchoWebSocketApplication();
        WebSocketClient client = null;
        try {
            WebSocketEngine.getEngine().register(app);
            thread = createSSLSelectorThread(PORT, new StaticResourcesAdapter());

            client = new WebSocketClient(String.format("wss://localhost:%s/echo", PORT), version);
            client.connect();
            Assert.assertTrue(client.isConnected());
        } finally {
            if (client != null) {
                client.close();
            }
            if (thread != null) {
                thread.stopEndpoint();
            }
            WebSocketEngine.getEngine().unregister(app);
        }
    }



    private void send(WebSocketClient client, Map<String, Object> messages, final String message) {
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
            public boolean isApplicationRequest(Request request) {
                return true;
            }
        };
        WebSocketEngine.getEngine().register(app);
        final SelectorThread thread = createSelectorThread(PORT, new ServletAdapter(new EchoServlet()));
        InputStream is = null;
        try {
            URL url = new URL("http://localhost:" + PORT + "/echo");
            final URLConnection urlConnection = url.openConnection();
            is = urlConnection.getInputStream();
            final byte[] bytes = new byte[1024];
            Assert.assertEquals(EchoServlet.RESPONSE_TEXT, new String(bytes, 0, is.read(bytes)));
        } finally {
            if (thread != null) {
                thread.stopEndpoint();
            }
            if (is != null) {
                is.close();
            }
            WebSocketEngine.getEngine().unregister(app);
        }
    }

    @Test
    public void testGetOnServlet() throws IOException, InstantiationException, InterruptedException {
        final SelectorThread thread = createSelectorThread(PORT, new ServletAdapter(new EchoServlet()));
        URL url = new URL("http://localhost:" + PORT + "/echo");
        final URLConnection urlConnection = url.openConnection();
        final InputStream content = (InputStream) urlConnection.getContent();
        try {
            final byte[] bytes = new byte[1024];
            Assert.assertEquals(EchoServlet.RESPONSE_TEXT, new String(bytes, 0, content.read(bytes)));
        } finally {
            content.close();
            thread.stopEndpoint();
        }
    }



}
