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

import com.sun.grizzly.SSLConfig;
import com.sun.grizzly.arp.DefaultAsyncHandler;
import com.sun.grizzly.http.SelectorThread;
import com.sun.grizzly.http.servlet.ServletAdapter;
import com.sun.grizzly.ssl.SSLSelectorThread;
import com.sun.grizzly.tcp.Adapter;
import com.sun.grizzly.tcp.Request;
import com.sun.grizzly.tcp.StaticResourcesAdapter;
import com.sun.grizzly.util.Utils;
import com.sun.grizzly.util.net.jsse.JSSEImplementation;
import org.testng.Assert;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Test;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.servlet.Servlet;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@SuppressWarnings({"StringContatenationInLoop"})
@Test
public class WebSocketsTest {
    private static final Object SLUG = new Object();
    private static final int MESSAGE_COUNT = 10;
    private static SSLConfig sslConfig;
    public static final int PORT = 1725;

    @BeforeSuite
    public void enable() {
        WebSocketEngine.setWebSocketEnabled(true);
    }

    @Test
    public void simpleConversationWithApplication() throws Exception {
        run(new EchoServlet());
    }


    @Test(expectedExceptions = {IllegalStateException.class})
    public void websocketsNotEnabled() {
        WebSocketEngine.setWebSocketEnabled(false);
        final WebSocketApplication app = new WebSocketApplication() {
            @Override
            public boolean isApplicationRequest(Request request) {
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
        final SelectorThread thread = createSelectorThread(PORT, new ServletAdapter(servlet));
        final Map<String, Object> sent = new ConcurrentHashMap<String, Object>();
        final CountDownLatch connected = new CountDownLatch(1);
        final CountDownLatch received = new CountDownLatch(MESSAGE_COUNT);

        WebSocket client = null;
        try {
            client = new ClientWebSocket(String.format("ws://localhost:%s/echo", PORT), new WebSocketAdapter() {
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

            for (int count = 0; count < MESSAGE_COUNT; count++) {
                send(client, sent, "message " + count);
            }

            Assert.assertTrue(received.await(WebSocketEngine.DEFAULT_TIMEOUT, TimeUnit.SECONDS),
                    String.format("Waited %ss for the messages to echo back", WebSocketEngine.DEFAULT_TIMEOUT));

            Assert.assertEquals(0, sent.size(), String.format("Should have received all %s messages back.",
                    MESSAGE_COUNT));
        } catch (InterruptedException e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            if (client != null) {
                client.close();
            }
            thread.stopEndpoint();
        }
    }

    public void timeouts() throws Exception {
        SelectorThread thread = null;
        WebSocket client = null;
        final EchoWebSocketApplication app = new EchoWebSocketApplication();
        try {
            WebSocketEngine.getEngine().register(app);
            thread = createSelectorThread(PORT, new StaticResourcesAdapter());
            final Map<String, Object> messages = new ConcurrentHashMap<String, Object>();
            final CountDownLatch received = new CountDownLatch(MESSAGE_COUNT);
            client = new ClientWebSocket(String.format("ws://localhost:%s/echo", PORT), new WebSocketAdapter() {
                @Override
                public void onMessage(WebSocket socket, String data) {
                    messages.remove(data);
                    received.countDown();
                }
            });

            for (int index = 0; index < MESSAGE_COUNT; index++) {
                send(client, messages, "test " + index);
                Thread.sleep(5000);
            }

            Assert.assertTrue(received.await(WebSocketEngine.DEFAULT_TIMEOUT, TimeUnit.SECONDS),
                    String.format("Waited %ss for everything to come back", WebSocketEngine.DEFAULT_TIMEOUT));
            Assert.assertTrue(messages.isEmpty(), "All messages should have been echoed back: " + messages);
        } finally {
            WebSocketEngine.getEngine().unregister(app);
            if (client != null) {
                client.close();
            }
            if (thread != null) {
                thread.stopEndpoint();
            }
        }
    }

    public void ssl() throws Exception {
        final ArrayList<String> headers = new ArrayList<String>(Arrays.asList(
                WebSocketEngine.UPGRADE,
                WebSocketEngine.CONNECTION,
                WebSocketEngine.SEC_WS_ACCEPT
        ));
        SelectorThread thread = null;
        SSLSocket socket = null;
        final EchoWebSocketApplication app = new EchoWebSocketApplication();
        try {
            WebSocketEngine.getEngine().register(app);
            thread = createSSLSelectorThread(PORT, new StaticResourcesAdapter());
            SSLSocketFactory sslsocketfactory = getSSLSocketFactory();

            socket = (SSLSocket) sslsocketfactory.createSocket("localhost", PORT);
            handshake(socket, headers, true);
        } finally {
            if (thread != null) {
                thread.stopEndpoint();
            }
            if (socket != null) {
                socket.close();
            }
            WebSocketEngine.getEngine().unregister(app);
        }
    }

    private static void setup() throws URISyntaxException {
        sslConfig = new SSLConfig();
        ClassLoader cl = WebSocketsTest.class.getClassLoader();
        // override system properties
        URL cacertsUrl = cl.getResource("ssltest-cacerts.jks");
        String trustStoreFile = new File(cacertsUrl.toURI()).getAbsolutePath();
        if (cacertsUrl != null) {
            sslConfig.setTrustStoreFile(trustStoreFile);
            sslConfig.setTrustStorePass("changeit");
        }

        // override system properties
        URL keystoreUrl = cl.getResource("ssltest-keystore.jks");
        String keyStoreFile = new File(keystoreUrl.toURI()).getAbsolutePath();
        if (keystoreUrl != null) {
            sslConfig.setKeyStoreFile(keyStoreFile);
            sslConfig.setKeyStorePass("changeit");
        }

        SSLConfig.DEFAULT_CONFIG = sslConfig;

        System.setProperty("javax.net.ssl.trustStore", trustStoreFile);
        System.setProperty("javax.net.ssl.trustStorePassword", "changeit");
        System.setProperty("javax.net.ssl.keyStore", keyStoreFile);
        System.setProperty("javax.net.ssl.keyStorePassword", "changeit");
    }

    public SSLSocketFactory getSSLSocketFactory() throws IOException {
        try {
            //---------------------------------
            // Create a trust manager that does not validate certificate chains
            TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        public X509Certificate[] getAcceptedIssuers() {
                            return null;
                        }

                        public void checkClientTrusted(
                                X509Certificate[] certs, String authType) {
                        }

                        public void checkServerTrusted(
                                X509Certificate[] certs, String authType) {
                        }
                    }
            };
            // Install the all-trusting trust manager
            SSLContext sc = SSLContext.getInstance("SSL");
            sc.init(null, trustAllCerts, new SecureRandom());
            //---------------------------------
            return sc.getSocketFactory();
        } catch (Exception e) {
            e.printStackTrace();
            throw new IOException(e.getMessage());
        } finally {
        }
    }

    private void send(WebSocket client, Map<String, Object> messages, final String message) {
        messages.put(message, SLUG);
        client.send(message);
    }

    @SuppressWarnings({"IOResourceOpenedButNotSafelyClosed"})
    private void handshake(Socket socket, final List<String> headers, boolean secure) throws IOException {
        final OutputStream os = socket.getOutputStream();
        write(os, "GET /echo HTTP/1.1");
        write(os, "Host: localhost:" + PORT);
        write(os, "Connection: Upgrade");
        write(os, "Upgrade: WebSocket");
        final String origin = WebSocketEngine.SEC_WS_ORIGIN_HEADER + (secure ? ": https://localhost:" : ": http://localhost:");
        final String host = "Host" + (secure ? ": https://localhost:" : ": http://localhost:");
        write(os, origin + PORT);
        write(os, host + PORT);
        write(os, WebSocketEngine.SEC_WS_KEY_HEADER + ": " + new SecKey().getSecKey());
        write(os, "");
        os.flush();

        final BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        Map<String, String> receivedHeaders = new TreeMap<String, String>();
        String line;
        String response = null;
        while (!"".equals(line = reader.readLine())) {
            if(response == null) {
                response = line;
                Assert.assertEquals(line, String.format("HTTP/1.1 %s %s", WebSocketEngine.RESPONSE_CODE_VALUE,
                        WebSocketEngine.RESPONSE_CODE_MESSAGE));
            } else {
                String[] parts = line.split(":");
                receivedHeaders.put(parts[0].toLowerCase(), parts[1].trim());
            }
        }

        for (String header : headers) {
            Assert.assertTrue(receivedHeaders.containsKey(header.toLowerCase()), String.format("Looking for '%s'", header));
        }
    }

    private void write(OutputStream os, String text) throws IOException {
        os.write((text + "\r\n").getBytes("UTF-8"));
    }

    public void testGetOnWebSocketApplication() throws IOException, InstantiationException, InterruptedException {
        final WebSocketApplication app = new WebSocketApplication() {
            public void onMessage(WebSocket socket, String data) {
                Assert.fail("A GET should never get here.");
            }

            @Override
            public boolean isApplicationRequest(Request request) {
                return true;
            }

            public void onConnect(WebSocket socket) {
            }

            public void onClose(WebSocket socket) {
            }
        };
        WebSocketEngine.getEngine().register(app);
        final SelectorThread thread = createSelectorThread(PORT, new ServletAdapter(new EchoServlet()));
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
            thread.stopEndpoint();
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

    public static SelectorThread createSelectorThread(final int port, final Adapter adapter)
            throws IOException, InstantiationException {
        SelectorThread st = new SelectorThread();

        configure(port, adapter, st);
        st.listen();

        return st;
    }

    public static SSLSelectorThread createSSLSelectorThread(int port, Adapter adapter) throws Exception {
        setup();
        SSLSelectorThread st = new SSLSelectorThread();
        configure(port, adapter, st);

        st.setSSLConfig(sslConfig);
        try {
            st.setSSLImplementation(new JSSEImplementation());
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }

        st.listen();

        return st;

    }

    private static void configure(int port, Adapter adapter, SelectorThread st) {
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
    }

}
