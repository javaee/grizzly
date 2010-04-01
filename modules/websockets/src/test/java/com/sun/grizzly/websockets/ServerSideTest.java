package com.sun.grizzly.websockets;

import com.sun.grizzly.arp.DefaultAsyncHandler;
import com.sun.grizzly.http.SelectorThread;
import com.sun.grizzly.http.servlet.ServletAdapter;
import com.sun.grizzly.tcp.Adapter;
import com.sun.grizzly.tcp.http11.Constants;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

@Test
public class ServerSideTest {
    private static final int PORT = 1726;

    private static final String CLIENT_HANDSHAKE = "GET /echo HTTP/1.1" + Constants.CRLF
            + "Upgrade: WebSocket" + Constants.CRLF
            + "Connection: Upgrade" + Constants.CRLF
            + "Host: localhost" + Constants.CRLF
            + "Origin: http://localhost" + Constants.CRLF
            + Constants.CRLF;
    private static final int ITERATIONS = 10000;

    public void synchronous() throws IOException, InstantiationException {
        final SelectorThread thread = createSelectorThread(PORT, new ServletAdapter(new EchoServlet()));

        Socket socket = new Socket("localhost", PORT);
        try {
            final OutputStream outputStream = handshake(socket);
            int count = 0;
            final Date start = new Date();
            while (count++ < ITERATIONS) {
                validate(socket, outputStream, "test message");
                validate(socket, outputStream, "let's try again");
                validate(socket, outputStream, "3rd time's the charm!");
                validate(socket, outputStream, "ok.  just one more.");
                validate(socket, outputStream, "now, we're done");
            }
            final Date end = new Date();
            System.out.println("ServerSideTest.synchronous: message/ms = " + time(start, end));

        } finally {
            socket.close();
            thread.stopEndpoint();
        }
    }

    @SuppressWarnings({"StringContatenationInLoop"})
    public void asynchronous() throws IOException, InstantiationException, InterruptedException {
        final SelectorThread thread = createSelectorThread(PORT, new ServletAdapter(new EchoServlet()));

        final Socket socket = new Socket("localhost", PORT);
        try {
            final OutputStream outputStream = handshake(socket);
            int count = 0;
            final Date start = new Date();
            final ArrayList<String> sent = new ArrayList<String>();
            while (count++ < ITERATIONS) {
                queue(outputStream, "test message " + count, sent);
                queue(outputStream, "let's try again " + count, sent);
                queue(outputStream, "3rd time's the charm! " + count, sent);
                compare(socket, sent);
                compare(socket, sent);
                queue(outputStream, "ok.  just one more. " + count, sent);
                queue(outputStream, "now, we're done " + count, sent);
                compare(socket, sent);
                compare(socket, sent);
                compare(socket, sent);
            }
            final Date end = new Date();
            System.out.println("ServerSideTest.asynchronous: message/ms = " + time(start, end));
            Assert.assertTrue(sent.isEmpty(), "Should have gotten everything back by now");
        } finally {
            socket.close();
            thread.stopEndpoint();
        }
    }

    public void synchronousMultiClient() throws IOException, InstantiationException {
        final SelectorThread thread = createSelectorThread(PORT, new ServletAdapter(new EchoServlet()));

        List<Thread> clients = new ArrayList<Thread>();
        try {
            for (int x = 0; x < 5; x++) {
                clients.add(syncClient("client " + x));
            }
            while (!clients.isEmpty()) {
                final Iterator<Thread> it = clients.iterator();
                while (it.hasNext()) {
                    if (!it.next().isAlive()) {
                        it.remove();
                    }
                }
            }
        } finally {
            thread.stopEndpoint();
        }
    }

    private Thread syncClient(final String name) throws IOException {
        final Thread thread = new Thread(new Runnable() {
            public void run() {
                Socket socket = null;
                try {
                    socket = new Socket("localhost", PORT);
                    try {
                        final OutputStream outputStream = handshake(socket);
                        validate(socket, outputStream, name + ": test message");
                        validate(socket, outputStream, name + ": let's try again");
                        validate(socket, outputStream, name + ": 3rd time's the charm!");
                        validate(socket, outputStream, name + ": ok.  just one more.");
                        validate(socket, outputStream, name + ": now, we're done");
                    } finally {
                        socket.close();
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e.getMessage(), e);
                }
            }
        }, "syncClient " + name);
        thread.start();

        return thread;
    }

    private long time(Date start, Date end) {
        return 5 * ITERATIONS / (end.getTime() - start.getTime());
    }

    private OutputStream handshake(Socket socket) throws IOException {
        final OutputStream outputStream = socket.getOutputStream();
        outputStream.write(CLIENT_HANDSHAKE.getBytes());
        outputStream.flush();
        read(socket);
        return outputStream;
    }

    private void queue(OutputStream outputStream, String text, final List<String> sent) throws IOException {
        sent.add(text);
        write(outputStream, text);
    }

    private void validate(Socket socket, OutputStream outputStream, final String text) throws IOException {
        write(outputStream, text);
        compare(socket, text);
    }

    private void compare(Socket socket, String text) throws IOException {
        final ByteBuffer buffer = read(socket);
        if (buffer.limit() > 0) {
            DataFrame frame = new DataFrame(buffer);
            if (text != null) {
                Assert.assertEquals(frame.getTextPayload(), text);
            }
        }
    }

    private void compare(Socket socket, ArrayList<String> sent) throws IOException {
        if (!sent.isEmpty()) {
            final ByteBuffer buffer = read(socket);
            while (buffer.hasRemaining()) {
                Assert.assertEquals(new DataFrame(buffer).getTextPayload(), sent.remove(0));
            }
        }
    }

    private void write(OutputStream outputStream, String text) throws IOException {
        outputStream.write(new DataFrame(text).frame());
        outputStream.flush();
    }

    private ByteBuffer read(Socket socket) throws IOException {
        final int limit = 512;
        int count = limit;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        while (count > -1 && count == limit) {
            final byte[] b = new byte[limit];
            count = socket.getInputStream().read(b);
            if (count > -1) {
                baos.write(b, 0, count);
            }
        }
        final ByteBuffer buffer = ByteBuffer.allocate(baos.size());
        if (baos.size() > 0) {
            buffer.put(baos.toByteArray(), 0, baos.size());
        }
        buffer.flip();

        return buffer;
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
        st.listen();

        return st;
    }
}
