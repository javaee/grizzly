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

import com.sun.grizzly.arp.DefaultAsyncHandler;
import com.sun.grizzly.http.SelectorThread;
import com.sun.grizzly.http.servlet.ServletAdapter;
import com.sun.grizzly.tcp.Adapter;
import com.sun.grizzly.tcp.StaticResourcesAdapter;
import com.sun.grizzly.util.Utils;
import org.testng.Assert;
import org.testng.annotations.Test;

import javax.servlet.Servlet;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URL;
import java.net.URLConnection;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@Test
public class WebSocketsTest {
    private static final Object SLUG = new Object();
    private static final int MESSAGE_COUNT = 10;

    public void securityKeys() {
        validate("&2^3 4  1l6h85  F  3Z  31");
        validate("k ]B28GZ8  0  x *95 Y 6  92q0");
    }

    private void validate(final String key) {
        SecKey key1 = SecKey.create(key);
        long number = Long.parseLong(key.replaceAll("\\D", ""));
        int spaces = key.replaceAll("\\S", "").length();
        Assert.assertEquals(key1.getSecKeyValue(), number / spaces);
    }

    @Test
    public void simpleConversationWithApplication() throws IOException, InstantiationException {
        final EchoServlet servlet = new EchoServlet();
        final SimpleWebSocketApplication app = new SimpleWebSocketApplication();
        WebSocketEngine.getEngine().register("/echo", app);
        run(servlet);
    }

    private void run(final Servlet servlet) throws IOException, InstantiationException {
        final SelectorThread thread = createSelectorThread(1725, new ServletAdapter(servlet));
        WebSocket client = new WebSocketClient("ws://localhost:1725/echo");
        final Map<String, Object> sent = new ConcurrentHashMap<String, Object>();
        client.add(new WebSocketListener() {
            public void onMessage(WebSocket socket, DataFrame data) {
                sent.remove(data.getTextPayload());
            }

            public void onConnect(WebSocket socket) {
            }

            public void onClose(WebSocket socket) {
                Utils.dumpOut("closed");
            }
        });
        try {
            while (!client.isConnected()) {
                Utils.dumpOut("WebSocketsTest.run: client = " + client);
                Thread.sleep(1000);
            }

            for (int count = 0; count < MESSAGE_COUNT; count++) {
                final String data = "message " + count;
                sent.put(data, "");
                client.send(data);
            }

            int count = 0;
            while (!sent.isEmpty() && count++ < 60) {
                Thread.sleep(1000);
            }

            Assert.assertEquals(0, sent.size(), String.format("Should have received all %s messages back.",
                    MESSAGE_COUNT));
        } catch (InterruptedException e) {
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            client.close();
            thread.stopEndpoint();
        }
    }

    public void timeouts() throws IOException, InstantiationException, InterruptedException {
        SelectorThread thread = null;
        try {
            WebSocketEngine.getEngine().register("/echo", new SimpleWebSocketApplication());
            thread = createSelectorThread(1725, new StaticResourcesAdapter());
            WebSocket client = new WebSocketClient("ws://localhost:1725/echo");
            final Map<String, Object> messages = new ConcurrentHashMap<String, Object>();
            final CountDownLatch latch = new CountDownLatch(1);
            client.add(new WebSocketListener() {
                public void onMessage(WebSocket socket, DataFrame data) {
                    Assert.assertNotNull(messages.remove(data.getTextPayload()));
                }

                public void onConnect(WebSocket socket) {
                    latch.countDown();
                }

                public void onClose(WebSocket socket) {
                    Utils.dumpOut("closed");
                }
            });

            latch.await(10, TimeUnit.SECONDS);

            for (int index = 0; index < 5; index++) {
                send(client, messages, "test " + index);
                Thread.sleep(3000);
            }

            Assert.assertTrue(messages.isEmpty(), "All messages should have been echoed back: " + messages);
        } finally {
            if (thread != null) {
                thread.stopEndpoint();
            }
        }
    }

    private void send(WebSocket client, Map<String, Object> messages, final String message) throws IOException {
        messages.put(message, SLUG);
        client.send(message);
    }

    @Test
    public void testVer75ServerHandShake() throws Exception {
        List<String> responseHeaders = new ArrayList<String>(Arrays.asList(
                "HTTP/1.1 101 Switching Protocols",
                "Upgrade: WebSocket",
                "Connection: Upgrade",
                "WebSocket-Origin: http://localhost:1725",
                "WebSocket-Location: ws://localhost:1725/echo"
        ));
        final SelectorThread thread = createSelectorThread(1725, new StaticResourcesAdapter());
        try {
            WebSocketEngine.getEngine().register("/echo", new WebSocketApplication() {
                public void onMessage(WebSocket socket, DataFrame data) {
                    Assert.fail("A GET should never get here.");
                }

                public void onConnect(WebSocket socket) {
                }

                public void onClose(WebSocket socket) {
                }
            });

            Socket socket = new Socket("localhost", 1725);
            try {
                final OutputStream os = socket.getOutputStream();
                write(os, "GET /echo HTTP/1.1");
                write(os, "Host: localhost:1725");
                write(os, "Connection: Upgrade");
                write(os, "Upgrade: WebSocket");
                write(os, "Origin: http://localhost:1725");
                write(os, "");
                os.flush();

                final BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                List<String> receivedHeaders = new ArrayList<String>();
                String line;
                while (!"".equals(line = reader.readLine())) {
                    receivedHeaders.add(line);
                }

                Assert.assertEquals(receivedHeaders.remove(0), responseHeaders.remove(0));
                while(!receivedHeaders.isEmpty()) {
                    final String next = receivedHeaders.remove(0);
                    Assert.assertTrue(responseHeaders.remove(next), String.format("Looking for '%s'", next));
                }
            } finally {
                socket.close();
            }
        } finally {
            thread.stopEndpoint();
        }
    }

    private void write(OutputStream os, String text) throws IOException {
        os.write((text + "\r\n").getBytes("UTF-8"));
    }

    private String createString(ByteBuffer buf) {
        return new String(buf.array(), buf.position(), buf.limit());
    }

    public void testGetOnWebSocketApplication() throws IOException, InstantiationException, InterruptedException {
        final SelectorThread thread = createSelectorThread(1725, new ServletAdapter(new EchoServlet() {
            {
                WebSocketEngine.getEngine().register("/echo", new WebSocketApplication() {
                    public void onMessage(WebSocket socket, DataFrame data) {
                        Assert.fail("A GET should never get here.");
                    }

                    public void onConnect(WebSocket socket) {
                    }

                    public void onClose(WebSocket socket) {
                    }
                });
            }
        }));
        URL url = new URL("http://localhost:1725/echo");
        final URLConnection urlConnection = url.openConnection();
        final InputStream content = (InputStream) urlConnection.getContent();
        try {
            final byte[] bytes = new byte[1024];
            final int i = content.read(bytes);
            final String text = new String(bytes, 0, i);
            Assert.assertEquals(EchoServlet.RESPONSE_TEXT, text);
        } finally {
            content.close();
            thread.stopEndpoint();
        }
    }

/*
    public void testGetOnServlet() throws IOException, InstantiationException, InterruptedException {
        final SelectorThread thread = createSelectorThread(1725, new ServletAdapter(new EchoServlet()));
        URL url = new URL("http://localhost:1725/echo");
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

    public static SelectorThread createSelectorThread(final int port, final Adapter adapter)
            throws IOException, InstantiationException {
        SelectorThread st = new SelectorThread();

        st.setSsBackLog(8192);
        st.setCoreThreads(2);
        st.setMaxThreads(2);
        st.setPort(port);
        st.setDisplayConfiguration(Utils.VERBOSE_TESTS);
        st.setAdapter(adapter);
        st.setAsyncHandler(new DefaultAsyncHandler());
        st.setEnableAsyncExecution(true);
        st.getAsyncHandler().addAsyncFilter(new WebSocketAsyncFilter());
        st.setTcpNoDelay(true);
        st.listen();

        return st;
    }

}