/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011-2011 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.grizzly.websockets.draft06;

import com.sun.grizzly.http.SelectorThread;
import com.sun.grizzly.http.servlet.ServletAdapter;
import com.sun.grizzly.tcp.Request;
import com.sun.grizzly.websockets.BaseWebSocketTestUtilities;
import com.sun.grizzly.websockets.EchoServlet;
import com.sun.grizzly.websockets.LocalNetworkHandler;
import com.sun.grizzly.websockets.DataFrame;
import com.sun.grizzly.websockets.WebSocketApplication;
import com.sun.grizzly.websockets.WebSocketEngine;
import com.sun.grizzly.websockets.frametypes.BinaryFrameType;
import com.sun.grizzly.websockets.frametypes.PingFrameType;
import com.sun.grizzly.websockets.frametypes.PongFrameType;
import com.sun.grizzly.websockets.frametypes.TextFrameType;
import org.junit.Assert;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Draft06Test extends BaseWebSocketTestUtilities {
    @Test
    public void textFrame() throws IOException {
        Draft06Handler handler = new Draft06Handler();
        final byte[] data = handler.frame(new DataFrame(new TextFrameType(), "Hello"));
        Assert.assertArrayEquals(new byte[]{(byte) 0x84, 0x05, 0x48, 0x65, 0x6c, 0x6c, 0x6f}, data);

        handler = new Draft06Handler(true);
        handler.setNetworkHandler(new LocalNetworkHandler(data));
        Assert.assertEquals("Hello", handler.unframe().getTextPayload());
    }

    @Test
    public void binaryFrame() throws IOException {
        Draft06Handler handler = new Draft06Handler();

        byte bytes[] = new byte[256];
        new Random().nextBytes(bytes);
        byte[] sample = new byte[260];
        System.arraycopy(new byte[]{(byte) 0x85, 0x7E, 0x01, 0x00}, 0, sample, 0, 4);
        System.arraycopy(bytes, 0, sample, 4, bytes.length);

        final byte[] data = handler.frame(new DataFrame(new BinaryFrameType(), bytes));
        Assert.assertArrayEquals(sample, data);

        handler = new Draft06Handler(true);
        handler.setNetworkHandler(new LocalNetworkHandler(data));
        Assert.assertArrayEquals(bytes, handler.unframe().getBytes());
    }

    @Test
    public void binaryFrameUnmasked() {
        checkArrays(256, new byte[]{(byte) 0x85, 0x7E, (byte) 0x01, 0x00});
    }

    @Test
    public void largeBinaryFrame() throws IOException {
        Draft06Handler handler = new Draft06Handler();
        byte bytes[] = new byte[65536];
        new Random().nextBytes(bytes);
        final byte[] prelude = {(byte) 0x85, 0x7F, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00};
        byte[] sample = new byte[bytes.length + prelude.length];
        System.arraycopy(prelude, 0, sample, 0, prelude.length);
        System.arraycopy(bytes, 0, sample, prelude.length, 65536);
        final byte[] data = handler.frame(new DataFrame(new BinaryFrameType(), bytes));
        Assert.assertArrayEquals(sample, data);

        handler = new Draft06Handler(true);
        handler.setNetworkHandler(new LocalNetworkHandler(data));
        Assert.assertArrayEquals(bytes, handler.unframe().getBytes());
    }

    @Test
    public void ping() throws IOException {
        Draft06Handler handler = new Draft06Handler();
        DataFrame frame = new DataFrame(new TextFrameType(), "Hello");
        final byte[] data = handler.frame(frame);
        handler = new Draft06Handler(true);
        handler.setNetworkHandler(new LocalNetworkHandler(data));

        Assert.assertArrayEquals(new byte[]{(byte) 0x84, 0x05, 0x48, 0x65, 0x6c, 0x6c, 0x6f}, data);
        Assert.assertEquals("Hello", handler.unframe().getTextPayload());
    }

    @Test
    public void pingPong() {
        final LocalNetworkHandler localHandler = new LocalNetworkHandler();
        Draft06Handler clientHandler = new Draft06Handler(false);
        clientHandler.setNetworkHandler(localHandler);

        clientHandler.send(new DataFrame(new PingFrameType(), "Hello"));
        Assert.assertArrayEquals(new byte[]{(byte) 0x82, 0x05, 0x48, 0x65, 0x6c, 0x6c, 0x6f}, localHandler.getArray());

        clientHandler.send(new DataFrame(new PongFrameType(), "Hello"));
        Assert.assertArrayEquals(new byte[]{(byte) 0x83, 0x05, 0x48, 0x65, 0x6c, 0x6c, 0x6f}, localHandler.getArray());
    }

    @Test
    public void convertToBytes() {
        compare(112);
        compare(1004);
        compare(8000);
        compare(10130);
        compare(Integer.MAX_VALUE);
        compare(Long.MAX_VALUE);
    }

    private void compare(final long length) {
        byte[] bytes = WebSocketEngine.toArray(length);
        Assert.assertEquals(WebSocketEngine.toLong(bytes, 0, bytes.length), length);
    }

    @Test
    public void close() throws IOException {
        Draft06Handler handler = new Draft06Handler();
        ClosingFrame frame = new ClosingFrame(1001, "test message");
        final byte[] bytes = handler.frame(frame);

        handler = new Draft06Handler(true);
        handler.setNetworkHandler(new LocalNetworkHandler(bytes));

        ClosingFrame after = (ClosingFrame) handler.unframe();

        Assert.assertEquals("test message", after.getReason());
        Assert.assertEquals(1001, after.getCode());
    }

    @Test
    public void largeBinaryFrameUnmasked() {
        checkArrays(65536, new byte[]{(byte) 0x85, 0x7F, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00});
    }

    private void checkArrays(final int size, final byte[] expected) {
        Draft06Handler handler = new Draft06Handler(false);
        final LocalNetworkHandler localHandler = new LocalNetworkHandler();
        handler.setNetworkHandler(localHandler);
        final byte[] data = new byte[size];
        new Random().nextBytes(data);
        handler.send(data);
        final byte[] array = localHandler.getArray();
        byte[] header = new byte[expected.length];
        System.arraycopy(array, 0, header, 0, expected.length);
        Assert.assertArrayEquals(expected, header);
        Draft06Handler clientHandler = new Draft06Handler(true);
        clientHandler.setNetworkHandler(localHandler);
        Assert.assertArrayEquals(data, clientHandler.unframe().getBytes());
    }
    @Test
    public void sampleHandShake() throws IOException, InstantiationException, InterruptedException {
        final SelectorThread thread = createSelectorThread(PORT, new ServletAdapter(new EchoServlet()));
        final WebSocketApplication app = new WebSocketApplication() {
            @Override
            public boolean isApplicationRequest(Request request) {
                return request.requestURI().equals("/chat");
            }

            @Override
            public List<String> getSupportedProtocols(List<String> subProtocols) {
                final List<String> list = new ArrayList<String>();
                if (subProtocols.contains("chat")) {
                    list.add("chat");
                }
                return list;
            }
        };
        WebSocketEngine.getEngine().register(app);
        Socket socket = new Socket("localhost", PORT);
        try {
            final OutputStream outputStream = socket.getOutputStream();
            PrintWriter writer = new PrintWriter(outputStream);
            writer.write("GET /chat HTTP/1.1\n" +
                    "Host: server.example.com\n" +
                    "Upgrade: websocket\n" +
                    "Connection: Upgrade\n" +
                    "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\n" +
                    "Sec-WebSocket-Origin: http://example.com\n" +
                    "Sec-WebSocket-Protocol: chat, superchat\n" +
                    "Sec-WebSocket-Version: 6\n\n");
            writer.flush();
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            StringBuilder builder = new StringBuilder();
            String line;
            while (!"".equals(line = reader.readLine())) {
                builder.append(line + "\n");
            }
            Assert.assertEquals("HTTP/1.1 101 Switching Protocols\n" +
                    "Upgrade: websocket\n" +
                    "Connection: Upgrade\n" +
                    "Sec-WebSocket-Accept: s3pPLMBiTxaQ9kYGzzhZRbK+xOo=\n" +
                    "Sec-WebSocket-Protocol: chat", builder.toString().trim());
        } finally {
            if (socket != null) {
                socket.close();
            }
            thread.stopEndpoint();
            WebSocketEngine.getEngine().unregister(app);
        }
    }

}