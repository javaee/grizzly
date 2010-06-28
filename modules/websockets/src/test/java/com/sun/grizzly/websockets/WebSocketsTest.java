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

import com.sun.grizzly.SSLConfig;
import com.sun.grizzly.arp.DefaultAsyncHandler;
import com.sun.grizzly.http.SelectorThread;
import com.sun.grizzly.http.servlet.ServletAdapter;
import com.sun.grizzly.ssl.SSLSelectorThread;
import com.sun.grizzly.tcp.Adapter;
import com.sun.grizzly.tcp.StaticResourcesAdapter;
import com.sun.grizzly.util.Utils;
import com.sun.grizzly.util.net.jsse.JSSEImplementation;
import org.testng.Assert;
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
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
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
        final SelectorThread thread = createSelectorThread(PORT, new ServletAdapter(servlet));
        final Map<String, Object> sent = new ConcurrentHashMap<String, Object>();
        WebSocket client = new WebSocketClient("ws://localhost:" + PORT + "/echo",
                new WebSocketListener() {
                    public void onMessage(WebSocket socket, DataFrame data) {
                        sent.remove(data.getTextPayload());
                    }

                    public void onConnect(WebSocket socket) {
                    }

                    public void onClose(WebSocket socket) {
                        Utils.dumpOut("closed");
                    }
                });
        client.connect();
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
            thread = createSelectorThread(PORT, new StaticResourcesAdapter());
            final Map<String, Object> messages = new ConcurrentHashMap<String, Object>();
            final CountDownLatch latch = new CountDownLatch(1);
            WebSocket client = new WebSocketClient("ws://localhost:" + PORT + "/echo", new WebSocketListener() {
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
            client.connect();

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

    public void ssl() throws Exception {
        final ArrayList<String> headers = new ArrayList<String>(Arrays.asList(
                "HTTP/1.1 101 Switching Protocols",
                "Upgrade: WebSocket",
                "Connection: Upgrade",
                "WebSocket-Origin: https://localhost:" + PORT,
                "WebSocket-Location: wss://localhost:" + PORT + "/echo"
        ));
        SelectorThread thread = null;
        SSLSocket socket = null;
        try {
            WebSocketEngine.getEngine().register("/echo", new SimpleWebSocketApplication());
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

    private void send(WebSocket client, Map<String, Object> messages, final String message) throws IOException {
        messages.put(message, SLUG);
        client.send(message);
    }

    @Test
    public void testVer75ServerHandShake() throws Exception {
        final SelectorThread thread = createSelectorThread(PORT, new StaticResourcesAdapter());
        WebSocketEngine.getEngine().register("/echo", new WebSocketApplication() {
            public void onMessage(WebSocket socket, DataFrame data) {
                Assert.fail("A GET should never get here.");
            }

            public void onConnect(WebSocket socket) {
            }

            public void onClose(WebSocket socket) {
            }
        });
        final ArrayList<String> headers = new ArrayList<String>(Arrays.asList(
                "HTTP/1.1 101 Switching Protocols",
                "Upgrade: WebSocket",
                "Connection: Upgrade",
                "WebSocket-Origin: http://localhost:" + PORT,
                "WebSocket-Location: ws://localhost:" + PORT + "/echo"
        ));
        Socket socket = new Socket("localhost", PORT);
        try {
            handshake(socket, headers, false);
        } finally {
            socket.close();
            thread.stopEndpoint();
        }
    }

    private void handshake(Socket socket, final List<String> headers, boolean secure) throws IOException {
        final OutputStream os = socket.getOutputStream();
        write(os, "GET /echo HTTP/1.1");
        write(os, "Host: localhost:" + PORT);
        write(os, "Connection: Upgrade");
        write(os, "Upgrade: WebSocket");
        final String origin = secure ? "Origin: https://localhost:" : "Origin: http://localhost:";
        write(os, origin + PORT);
        write(os, "");
        os.flush();

        @SuppressWarnings({"IOResourceOpenedButNotSafelyClosed"})
        final BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        List<String> receivedHeaders = new ArrayList<String>();
        String line;
        while (!"".equals(line = reader.readLine())) {
            receivedHeaders.add(line);
        }

        Assert.assertEquals(receivedHeaders.remove(0), headers.remove(0));
        while (!receivedHeaders.isEmpty()) {
            final String next = receivedHeaders.remove(0);
            Assert.assertTrue(headers.remove(next), String.format("Looking for '%s'", next));
        }
    }

    private void write(OutputStream os, String text) throws IOException {
        os.write((text + "\r\n").getBytes("UTF-8"));
    }

    private String createString(ByteBuffer buf) {
        return new String(buf.array(), buf.position(), buf.limit());
    }

    public void testGetOnWebSocketApplication() throws IOException, InstantiationException, InterruptedException {
        final SelectorThread thread = createSelectorThread(PORT, new ServletAdapter(new EchoServlet() {
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
        URL url = new URL("http://localhost:" + PORT + "/echo");
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