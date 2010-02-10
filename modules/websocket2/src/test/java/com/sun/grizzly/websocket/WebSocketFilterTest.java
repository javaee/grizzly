package com.sun.grizzly.websocket;

import com.sun.grizzly.arp.DefaultAsyncHandler;
import com.sun.grizzly.http.SelectorThread;
import com.sun.grizzly.http.servlet.ServletAdapter;
import com.sun.grizzly.tcp.Adapter;
import com.sun.grizzly.tcp.http11.Constants;
import com.sun.grizzly.tcp.http11.GrizzlyAdapter;
import com.sun.grizzly.tcp.http11.GrizzlyRequest;
import com.sun.grizzly.tcp.http11.GrizzlyResponse;
import com.sun.grizzly.util.buf.ByteChunk;
import com.sun.grizzly.util.http.MimeHeaders;
import junit.framework.TestCase;
import org.junit.Assert;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
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
    private static final byte[] TEST_DATA = "test data".getBytes();

    private int adapterCount = 0;

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

    public void atestSimpleConvo() throws IOException, InstantiationException, InterruptedException {
        final SelectorThread thread = createSelectorThread(1725, new ServletAdapter(new EchoServlet()));
        final Socket s = new Socket("localhost", 1725);
        try {
            final OutputStream outputStream = s.getOutputStream();
            System.out.println("writing:\n" + CLIENT_HANDSHAKE);
            outputStream.write(CLIENT_HANDSHAKE.getBytes());
            outputStream.flush();
            final SocketReader reader = new SocketReader(s.getInputStream());
            System.out.println("handshake from server: " + new String(reader.getBytes()));
            Assert.assertArrayEquals(SERVER_HANDSHAKE.getBytes("ASCII"), reader.getBytes());
            ByteChunk chunk = data();
            final byte[] b = chunk.getBytes();
            outputStream.write(b, 0, chunk.getEnd());

            Thread.sleep(3000);
            final byte[] bytes = reader.read();
            System.out.println("from server: " + Arrays.toString(bytes));

            Assert.assertTrue("Should get framed data", bytes.length > 0 && bytes[0] == 0
                    && bytes[bytes.length - 1] == (byte) 0xFF);
        } finally {
            s.close();
            thread.stopEndpoint();
        }
    }

    private ByteChunk data() throws IOException {
        ByteChunk chunk = new ByteChunk();
        chunk.setEncoding("UTF-8");
        chunk.append((byte) 0x00);
        chunk.append(TEST_DATA, 0, TEST_DATA.length);
        chunk.append((byte) 0xFF);
        return chunk;
    }

    public void atestHtmlPageInChrome() throws IOException, InstantiationException, InterruptedException {
        final SelectorThread thread = createSelectorThread(1725, new EchoAdapter());

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
        st.getAsyncHandler().addAsyncFilter(new WebSocketFilter());
        st.setTcpNoDelay(true);
//        st.setInputFilters(new WebSocketInputFilter());
//        st.setOutputFilters(new WebSocketOutputFilter());
//        st.setLinger(-1);
        st.listen();

        return st;
    }

    private static class SocketReader {
        private final InputStream stream;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        private int count;

        public SocketReader(InputStream stream) {
            this.stream = stream;
        }

        public byte[] read() {
            System.out.println("WebSocketFilterTest$SocketReader.read");
            count = 0;
            baos.reset();
            byte[] buf = new byte[1024];
            try {
                int tries = 0;
                while (tries++ < 10 && (count == 0 || ready())) {
                    if (ready()) {
                        count = stream.read(buf);
                        baos.write(buf, 0, count);
                    }
                    Thread.sleep(500);
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e.getMessage(), e);
            } catch (IOException e) {
                throw new RuntimeException(e.getMessage(), e);
            }

            return baos.toByteArray();
        }

        private boolean ready() throws IOException {
            return stream.available() > 0;
        }

        public byte[] getBytes() {
            if (baos.size() == 0) {
                read();
            }
            return baos.toByteArray();
        }
    }

    private class EchoAdapter extends GrizzlyAdapter {
        public void service(GrizzlyRequest request, GrizzlyResponse response) {
            try {
                System.out.println("WebSocketFilterTest.service: " + adapterCount++);
                final CharBuffer buffer = CharBuffer.allocate(1024);
                final int read = request.getReader().read(buffer);
                ByteChunk chunk = new ByteChunk();
                final char[] chars = buffer.array();
                for (int index = 0; index < buffer.position(); index++) {
                    chunk.append(chars[index]);
                }
                response.getOutputBuffer().write(buffer.array(), buffer.arrayOffset(), buffer.position());
/*
                final GrizzlyOutputBuffer outputBuffer = response.getOutputBuffer();
                final String s = new String(buffer.array(), 0, buffer.position());
                outputBuffer.write(buffer.array(), 0, buffer.position());
                outputBuffer.flush();
*/
            } catch (IOException e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        }
    }

    private class EchoServlet extends HttpServlet {
        private String contextPath;

        @Override
        public void init(ServletConfig config) throws ServletException {
            contextPath = config.getServletContext().getContextPath() + "/echo";
            WebSocketContext context = WebSocketEngine.getEngine().register(contextPath);
            context.setExpirationDelay(5 * 60 * 1000);
        }
    }
}