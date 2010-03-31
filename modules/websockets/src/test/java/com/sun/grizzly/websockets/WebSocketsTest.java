package com.sun.grizzly.websockets;

import com.sun.grizzly.arp.DefaultAsyncHandler;
import com.sun.grizzly.http.SelectorThread;
import com.sun.grizzly.http.servlet.ServletAdapter;
import com.sun.grizzly.tcp.Adapter;
import junit.framework.TestCase;
import org.junit.Assert;
import org.testng.annotations.Test;

import javax.servlet.Servlet;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;

public class WebSocketsTest {
    private static int MESSAGE_COUNT = 3;

    @Test(enabled = false)
    public void simpleConversationWithApplication() throws Exception {
        while (MESSAGE_COUNT++ < 30) {
            final EchoServlet servlet = new EchoServlet();
            final SimpleWebSocketApplication app = new SimpleWebSocketApplication(servlet);
            WebSocketEngine.getEngine().register("/echo", app);
            run(servlet);
        }
    }

    private void run(final Servlet servlet) throws IOException, InstantiationException {

        System.out.println("\n\n***** Starting new conversation with " + MESSAGE_COUNT + " elements\n\n");
        final SelectorThread thread = createSelectorThread(1725, new ServletAdapter(servlet));
        WebSocket client = new WebSocketClient("ws://localhost:1725/echo");
        final Set<String> sent = new ConcurrentSkipListSet<String>();
        client.add(new WebSocketListener() {
            public void onRead(WebSocket socket, DataFrame data) {
                sent.remove(data.getTextPayload());
            }

            public void onConnect(WebSocket socket) {
            }

            public void onClose(WebSocket socket) {
                System.out.println("closed");
            }
        });
        try {
            while (!client.isConnected()) {
                System.out.println("WebSocketsTest.run: client = " + client);
                Thread.sleep(1000);
            }

            for (int count = 0; count < MESSAGE_COUNT; count++) {
                final String data = "message " + count;
                sent.add(data);
                client.send(data);
            }

            int count = 0;
            while (!sent.isEmpty() && count++ < 60) {
                System.out.printf("WebSocketsTest.run: total = %s, count = %s, sent = %s\n", MESSAGE_COUNT, count, sent);
                Thread.sleep(1000);
            }
            Assert.assertEquals(String.format("Should have received all %s messages back.",
                    MESSAGE_COUNT), MESSAGE_COUNT, MESSAGE_COUNT - sent.size());
        } catch (InterruptedException e) {
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            client.close();
            thread.stopEndpoint();
        }
    }

/*
    public void testServerHandShake() throws Exception {
        MimeHeaders headers = new MimeHeaders();
        headers.addValue("upgrade").setString("WebSocket");
        headers.addValue("connection").setString("Upgrade");
        headers.addValue("host").setString("localhost");
        headers.addValue("origin").setString("http://localhost");
        final ClientHandShake clientHandshake = new ClientHandShake(headers, false, "/echo");
        ServerHandShake shake = new ServerHandShake(headers, clientHandshake);
        final ByteBuffer buf = shake.generate();
        Assert.assertNotNull("handshake complete", buf);
//        Assert.assertEquals("Response should match spec", SERVER_HANDSHAKE, new String(buf.array()));
    }
*/

    /*
        public void testClient() throws IOException, InstantiationException {
            final SelectorThread thread = createSelectorThread(1725, new ServletAdapter());

            WebSocket client = new WebSocketClient("ws://localhost:1725/echo");
        }
    */

/*
    public void testGetOnWebSocketApplication()
            throws IOException, InstantiationException, InterruptedException {
        final SelectorThread thread = createSelectorThread(1725, new ServletAdapter(new EchoServlet() {
            {
                WebSocketEngine.getEngine().register("/echo", new WebSocketApplication() {
                    public void onRead(WebSocket socket, DataFrame data) {
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
*/

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

    private SelectorThread createSelectorThread(final int port, final Adapter adapter)
            throws IOException, InstantiationException {
        SelectorThread st = new SelectorThread();

        st.setSsBackLog(8192);
        st.setCoreThreads(2);
        st.setMaxThreads(2);
        st.setPort(port);
        st.setDisplayConfiguration(false);
        st.setAdapter(adapter);
        st.setAsyncHandler(new DefaultAsyncHandler());
        st.setEnableAsyncExecution(true);
        st.getAsyncHandler().addAsyncFilter(new WebSocketAsyncFilter());
        st.setTcpNoDelay(true);
        st.listen();

        return st;
    }

    private static class SimpleWebSocketApplication extends WebSocketApplication {
        private final EchoServlet servlet;

        public SimpleWebSocketApplication(EchoServlet echoServlet) {
            servlet = echoServlet;
        }

        public void onRead(WebSocket socket, DataFrame data) {
            servlet.read(socket, data);
        }

        public void onConnect(WebSocket socket) {
        }

        public void onClose(WebSocket socket) {
        }
    }
}