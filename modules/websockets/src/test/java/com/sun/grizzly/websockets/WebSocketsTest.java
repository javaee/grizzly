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
import com.sun.grizzly.tcp.Request;
import com.sun.grizzly.util.Utils;
import com.sun.grizzly.util.http.MimeHeaders;
import org.junit.Assert;
import org.testng.annotations.Test;

import javax.servlet.Servlet;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Test
public class WebSocketsTest {
    private int messageCount = 3;

    @Test(enabled = false)
    public void simpleConversationWithApplication() throws Exception {
        while (messageCount++ < 30) {
            final EchoServlet servlet = new EchoServlet();
            final SimpleWebSocketApplication app = new SimpleWebSocketApplication(servlet);
            WebSocketEngine.getEngine().register("/echo", app);
            run(servlet);
        }
    }

    private void run(final Servlet servlet) throws IOException, InstantiationException {

        Utils.dumpOut("\n\n***** Starting new conversation with " + messageCount + " elements\n\n");
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

            for (int count = 0; count < messageCount; count++) {
                final String data = "message " + count;
                sent.put(data, Boolean.TRUE);
                client.send(data);
            }

            int count = 0;
            while (!sent.isEmpty() && count++ < 60) {
                System.out.printf("WebSocketsTest.run: total = %s, count = %s, sent = %s\n", messageCount, count, sent);
                Thread.sleep(1000);
            }
            Assert.assertEquals(String.format("Should have received all %s messages back.",
                    messageCount), messageCount, messageCount - sent.size());
        } catch (InterruptedException e) {
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            client.close();
            thread.stopEndpoint();
        }
    }

    public void testServerHandShake() throws Exception {
        Request request = new Request();
        final MimeHeaders headers = request.getMimeHeaders();
        headers.addValue("upgrade").setString("WebSocket");
        headers.addValue("connection").setString("Upgrade");
        headers.addValue("host").setString("localhost");
        headers.addValue("origin").setString("http://localhost");
        request.requestURI().setString("/echo");
        final ClientHandShake clientHandshake = new ClientHandShake(request, false);
        ServerHandShake shake = new ServerHandShake(clientHandshake);
        final ByteBuffer buf = shake.generate();
        Assert.assertNotNull("handshake complete", buf);
        System.out.println("WebSocketsTest.testServerHandShake: buf = '" + createString(buf) + "'");
//        Assert.assertEquals("Response should match spec", SERVER_HANDSHAKE, new String(buf.array()));
    }

    private String createString(ByteBuffer buf) {
        return new String(buf.array(), buf.position(), buf.limit());
    }

    /*
        public void testClient() throws IOException, InstantiationException {
            final SelectorThread thread = createSelectorThread(1725, new ServletAdapter());

            WebSocket client = new WebSocketClient("ws://localhost:1725/echo");
        }
    */

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

/*
    public void testTimeOut()
            throws IOException, InstantiationException, InterruptedException {
        final SelectorThread thread = createSelectorThread(1725, new ServletAdapter(new HttpServlet() {
            @Override
            protected void service(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                resp.setContentType("text/plain; charset=iso-8859-1");
                resp.getWriter().write(req.getReader().readLine());
                resp.getWriter().flush();
            }
        }));
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
        st.setKeepAliveTimeoutInSeconds(12);
        st.listen();

        return st;
    }

}