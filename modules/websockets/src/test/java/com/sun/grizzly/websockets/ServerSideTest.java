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
import com.sun.grizzly.tcp.http11.Constants;
import com.sun.grizzly.util.Utils;
import org.testng.Assert;
import org.testng.annotations.Test;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
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
    private static final boolean DEBUG_MODE = Utils.isDebugVM();
    // when debugging, sleep() will wait while this is true.  use your debugger to set this to false when
    // you're ready to move on
    private boolean wait = true;

    public void synchronous() throws IOException, InstantiationException, InterruptedException {
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
            Utils.dumpOut("ServerSideTest.synchronous: message/ms = " + time(start, end));

        } finally {
            socket.close();
            thread.stopEndpoint();
        }
    }

    @Test(enabled = false)
    public void timeouts() throws IOException, InstantiationException, InterruptedException {
        final SelectorThread thread = createSelectorThread(PORT, new ServletAdapter(new EchoServlet()));

        Socket socket = new Socket("localhost", PORT);
        try {
            final OutputStream outputStream = handshake(socket);
            int count = 0;

            while (count++ < ITERATIONS) {
                validate(socket, outputStream, "test message");
                sleep(5000);
                validate(socket, outputStream, "let's try again");
            }
        } finally {
            socket.close();
            thread.stopEndpoint();
        }
    }

    private void sleep(final int delay) throws InterruptedException {
        wait = true;
        while(DEBUG_MODE && wait) {
            Thread.sleep(10000);
        }
        Thread.sleep(delay);
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
            Utils.dumpOut("ServerSideTest.asynchronous: message/ms = " + time(start, end));
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
            for (int x = 0; x < 1; x++) {
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

    @Test(enabled = false)  // i think this use case is probably inherently busted.  investigate later.
    public void applessServlet() throws IOException, InstantiationException {
        final SelectorThread thread = createSelectorThread(PORT, new ServletAdapter(new HttpServlet() {
            @Override
            protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                    throws IOException {
                final ServletOutputStream outputStream = resp.getOutputStream();
                outputStream.write(req.getRequestURI().getBytes());
                outputStream.flush();
            }
        }));

        Socket socket = new Socket("localhost", PORT);
        try {
            write(socket, "GET /echo/me/right/back HTTP/1.1");
            write(socket, "Upgrade: WebSocket");
            write(socket, "Connection: Upgrade");
            write(socket, "Host: localhost");
            write(socket, "Origin: http://localhost:1726");
            write(socket, "");
            final ByteBuffer buffer = read(socket);

            byte[] b = new byte[buffer.limit()];
            System.arraycopy(buffer.array(), 0, b, 0, buffer.limit());
            System.out.println("ServerSideTest.applessServlet: b = " + Arrays.toString(b));
            System.out.println("ServerSideTest.applessServlet: b = " + new String(b));
            Assert.assertTrue(b[0] == (byte) 0x00);
            Assert.assertTrue(b[b.length - 1] == (byte) 0xFF);
        } finally {
            thread.stopEndpoint();
            socket.close();
        }
    }

    @Test(enabled = false)
    public void bigPayload() throws IOException, InstantiationException {
        final SelectorThread thread = createSelectorThread(PORT, new ServletAdapter(new EchoServlet()));

        Socket socket = new Socket("localhost", PORT);
        try {
            final OutputStream outputStream = handshake(socket);
            StringBuilder sb = new StringBuilder();
            for (int x = 0; x < 10; x++) {
                sb.append("Lorem ipsum dolor sit amet, consectetur adipiscing elit. Vivamus quis lectus odio, et" +
                        " dictum purus. Suspendisse id ante ac tortor facilisis porta. Nullam aliquet dapibus dui, ut" +
                        " scelerisque diam luctus sit amet. Donec faucibus aliquet massa, eget iaculis velit ullamcorper" +
                        " eu. Fusce quis condimentum magna. Vivamus eu feugiat mi. Cras varius convallis gravida. Vivamus" +
                        " et elit lectus. Aliquam egestas, erat sed dapibus dictum, sem ligula suscipit mauris, a" +
                        " consectetur massa augue vel est. Nam bibendum varius lobortis. In tincidunt, sapien quis" +
                        " hendrerit vestibulum, lorem turpis faucibus enim, non rhoncus nisi diam non neque. Aliquam eu" +
                        " urna urna, molestie aliquam sapien. Nullam volutpat, erat condimentum interdum viverra, tortor" +
                        " lacus venenatis neque, vitae mattis sem felis pellentesque quam. Nullam sodales vestibulum" +
                        " ligula vitae porta. Aenean ultrices, ligula quis dapibus sodales, nulla risus sagittis sapien," +
                        " id posuere turpis lectus ac sapien. Pellentesque sed ante nisi. Quisque eget posuere sapien.");
            }
            validate(socket, outputStream, sb.toString());
        } finally {
            socket.close();
            thread.stopEndpoint();
        }

    }

    private void write(Socket socket, final String text) throws IOException {
        final OutputStream os = socket.getOutputStream();
        os.write(text.getBytes());
        os.write(Constants.CRLF_BYTES);
    }

    private Thread syncClient(final String name) {
        final Thread thread = new Thread(new Runnable() {
            public void run() {
                Socket socket;
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
        } else {
            if (!DEBUG_MODE) {
                Assert.fail("Failed to read anything from the server.");
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
        final byte[] b = new byte[limit];
        final InputStream is = socket.getInputStream();
        while (count == limit) {
            count = is.read(b);
            if (count > 0) {
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
