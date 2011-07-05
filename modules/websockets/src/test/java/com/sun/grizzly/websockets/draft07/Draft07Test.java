/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.grizzly.websockets.draft07;

import com.sun.grizzly.http.SelectorThread;
import com.sun.grizzly.http.servlet.ServletAdapter;
import com.sun.grizzly.tcp.Request;
import com.sun.grizzly.websockets.BaseWebSocketTestUtilities;
import com.sun.grizzly.websockets.DataFrame;
import com.sun.grizzly.websockets.EchoServlet;
import com.sun.grizzly.websockets.LocalNetworkHandler;
import com.sun.grizzly.websockets.WebSocket;
import com.sun.grizzly.websockets.WebSocketApplication;
import com.sun.grizzly.websockets.WebSocketEngine;
import com.sun.grizzly.websockets.draft06.ClosingFrame;
import com.sun.grizzly.websockets.frametypes.ClosingFrameType;
import com.sun.grizzly.websockets.frametypes.PingFrameType;
import com.sun.grizzly.websockets.frametypes.PongFrameType;
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

public class Draft07Test extends BaseWebSocketTestUtilities {
    @Test
    public void textFrameUnmasked() {
        Draft07Handler handler = new Draft07Handler(false);
        final LocalNetworkHandler localHandler = new LocalNetworkHandler();
        handler.setNetworkHandler(localHandler);
        handler.send("Hello");
        Assert.assertArrayEquals(new byte[]{(byte) 0x81, 0x05, 0x48, 0x65, 0x6c, 0x6c, 0x6f}, localHandler.getArray());

        Assert.assertEquals("Hello", handler.unframe().getTextPayload());
    }

    @Test
    public void textFrameMasked() {
        Draft07Handler handler = new Draft07Handler(true);
        final LocalNetworkHandler localHandler = new LocalNetworkHandler();
        handler.setNetworkHandler(localHandler);
        handler.send("Hello");
        Assert.assertEquals("Hello", handler.unframe().getTextPayload());
    }

    @Test
    public void fragmentedTextUnmasked() {
        final LocalNetworkHandler localHandler = new LocalNetworkHandler();

        Draft07Handler clientHandler = new Draft07Handler(false);
        clientHandler.setNetworkHandler(localHandler);

        Draft07Handler serverHandler = new Draft07Handler(false);
        serverHandler.setNetworkHandler(localHandler);

        clientHandler.stream(false, "Hel");
        Assert.assertArrayEquals(new byte[]{0x01, 0x03, 0x48, 0x65, 0x6c}, localHandler.getArray());

        Assert.assertEquals("Hel", serverHandler.unframe().getTextPayload());

        clientHandler.stream(true, "lo");
        Assert.assertArrayEquals(new byte[]{(byte) 0x80, 0x02, 0x6c, 0x6f}, localHandler.getArray());
        Assert.assertEquals("lo", serverHandler.unframe().getTextPayload());
    }

    @Test
    public void pingPong() {
        final LocalNetworkHandler localHandler = new LocalNetworkHandler();
        Draft07Handler clientHandler = new Draft07Handler(false);
        clientHandler.setNetworkHandler(localHandler);

        clientHandler.send(new DataFrame(new PingFrameType(), "Hello"));
        Assert.assertArrayEquals(new byte[]{(byte) 0x89, 0x05, 0x48, 0x65, 0x6c, 0x6c, 0x6f}, localHandler.getArray());

        clientHandler.send(new DataFrame(new PongFrameType(), "Hello"));
        Assert.assertArrayEquals(new byte[]{(byte) 0x8A, 0x05, 0x48, 0x65, 0x6c, 0x6c, 0x6f}, localHandler.getArray());
    }

    @Test
    public void closeMasked() {
        final LocalNetworkHandler localHandler = new LocalNetworkHandler();

        Draft07Handler clientHandler = new Draft07Handler(true);
        clientHandler.setNetworkHandler(localHandler);

        Draft07Handler serverHandler = new Draft07Handler(false);
        serverHandler.setNetworkHandler(localHandler);

        clientHandler.close(WebSocket.NORMAL_CLOSURE, "Test Message");
        byte[] bytes = new byte[2];
        System.arraycopy(localHandler.getArray(), 0, bytes, 0, 2);
        Assert.assertArrayEquals(new byte[]{(byte) 0x88, (byte) 0x8E}, bytes);

        final DataFrame frame = serverHandler.unframe();
        Assert.assertTrue(frame.getType() instanceof ClosingFrameType);

        final ClosingFrame close = (ClosingFrame) frame;
        Assert.assertEquals("Test Message", close.getReason());


    }

    @Test
    public void binaryFrameUnmasked() {
        checkArrays(256, new byte[]{(byte) 0x82, 0x7E, (byte) 0x01, 0x00});
    }

    @Test
    public void largeBinaryFrameUnmasked() {
        checkArrays(65536, new byte[]{(byte) 0x82, 0x7F, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00});
    }

    private void checkArrays(final int size, final byte[] expected) {
        Draft07Handler handler = new Draft07Handler(false);
        final LocalNetworkHandler localHandler = new LocalNetworkHandler();
        handler.setNetworkHandler(localHandler);
        final byte[] data = new byte[size];
        new Random().nextBytes(data);
        handler.send(data);
        final byte[] array = localHandler.getArray();
        byte[] header = new byte[expected.length];
        System.arraycopy(array, 0, header, 0, expected.length);
        Assert.assertArrayEquals(expected, header);
        Assert.assertArrayEquals(data, handler.unframe().getBytes());
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
                    "Sec-WebSocket-Version: 7\n\n");
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
