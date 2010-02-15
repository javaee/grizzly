package com.sun.grizzly.websocket;

import com.sun.grizzly.arp.DefaultAsyncHandler;
import com.sun.grizzly.http.SelectorThread;
import com.sun.grizzly.http.servlet.ServletAdapter;
import com.sun.grizzly.tcp.Adapter;
import com.sun.grizzly.tcp.http11.Constants;
import com.sun.grizzly.util.buf.ByteChunk;
import com.sun.grizzly.util.http.MimeHeaders;
import junit.framework.TestCase;
import org.junit.Assert;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class WebSocketFilterTest extends TestCase {
    private static final String CLIENT_HANDSHAKE = "GET /demo HTTP/1.1" + Constants.CRLF
            + "Upgrade: WebSocket" + Constants.CRLF
            + "Connection: Upgrade" + Constants.CRLF
            + "Host: localhost" + Constants.CRLF
            + "Origin: http://localhost" + Constants.CRLF
            + Constants.CRLF;
    private static final String SERVER_HANDSHAKE = "HTTP/1.1 101 Web Socket Protocol Handshake" + Constants.CRLF
            + "Upgrade: WebSocket" + Constants.CRLF
            + "Connection: Upgrade" + Constants.CRLF
            + "WebSocket-Origin: http://localhost" + Constants.CRLF
            + "WebSocket-Location: ws://localhost/demo" + Constants.CRLF
            + Constants.CRLF;
    private final boolean inputFilterAdded = true;

    public void test() {
    }

    public void atestServerHandShake() throws Exception {
        MimeHeaders headers = new MimeHeaders();
        headers.addValue("upgrade").setString("WebSocket");
        headers.addValue("connection").setString("Upgrade");
        headers.addValue("host").setString("localhost");
        headers.addValue("origin").setString("http://localhost");
        final ClientHandShake clientHandshake = new ClientHandShake(headers, false, "/demo");
        ServerHandShake shake = new ServerHandShake(headers, clientHandshake);
        final ByteBuffer buf = shake.generate();
        Assert.assertNotNull("handshake complete", buf);
        Assert.assertEquals("Response should match spec", SERVER_HANDSHAKE, new String(buf.array()));
    }

    public void atestSimpleConversation() throws IOException, InstantiationException, InterruptedException {
        final SelectorThread thread = createSelectorThread(1725, new ServletAdapter(new EchoServlet()));
        final Socket s = new Socket("localhost", 1725);
        try {
            final OutputStream outputStream = s.getOutputStream();
            outputStream.write(CLIENT_HANDSHAKE.getBytes());
            outputStream.flush();
            final SocketReader reader = new SocketReader(s.getInputStream());
            Assert.assertArrayEquals(SERVER_HANDSHAKE.getBytes("ASCII"), reader.getBytes());
            for(int count = 1; count <= 3; count++) {
                frame(outputStream, reader, "message " + count, count);
            }
        } finally {
            s.close();
            thread.stopEndpoint();
        }
    }

    private void frame(OutputStream outputStream, SocketReader reader, final String text, int count)
            throws IOException, InterruptedException {
        ByteChunk chunk = new ByteChunk();
        byte[] bytes = text.getBytes("UTF-8");
        chunk.setEncoding("UTF-8");
        chunk.append((byte) 0x00);
        chunk.append(bytes, 0, bytes.length);
        chunk.append((byte) 0xFF);
        outputStream.write(chunk.getBytes(), 0, chunk.getLength());
        outputStream.flush();
        Thread.sleep(1000);
        bytes = reader.read();
        System.out.println("frame " + count + " :  from server: " + Arrays.toString(bytes) + " ==> '" + new String(bytes) + "'");
        Assert.assertTrue("Should get framed data", bytes.length > 0 && bytes[0] == 0
                && bytes[bytes.length - 1] == (byte) 0xFF);
        if(inputFilterAdded) {
            Assert.assertEquals("Should get data stream back", text, new String(bytes, 1, bytes.length - 2));
        }
    }

    public void testHtmlPageInChrome() throws IOException, InstantiationException, InterruptedException {
        createSelectorThread(1725, new ServletAdapter(new EchoServlet()));

        boolean wait = true;
        while (wait) {
            Thread.sleep(1000);
            wait = true;
        }
    }

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
        if(inputFilterAdded) {
            st.setInputFilters(new WebSocketInputFilter());
        }
        st.setOutputFilters(new WebSocketOutputFilter());
//        st.setLinger(-1);
        st.listen();

        return st;
    }

}