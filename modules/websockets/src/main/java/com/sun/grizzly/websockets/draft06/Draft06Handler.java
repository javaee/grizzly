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
 * http://glassfish.java.net/public/CDDL+GPL_1_1.html
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

import com.sun.grizzly.tcp.Request;
import com.sun.grizzly.util.net.URL;
import com.sun.grizzly.websockets.ClientHandShake;
import com.sun.grizzly.websockets.DataFrame;
import com.sun.grizzly.websockets.FrameType;
import com.sun.grizzly.websockets.FramingException;
import com.sun.grizzly.websockets.HandShake;
import com.sun.grizzly.websockets.HandshakeException;
import com.sun.grizzly.websockets.NetworkHandler;
import com.sun.grizzly.websockets.WebSocket;
import com.sun.grizzly.websockets.WebSocketApplication;
import com.sun.grizzly.websockets.WebSocketEngine;
import com.sun.grizzly.websockets.WebSocketHandler;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class Draft06Handler implements WebSocketHandler {
    private final SecureRandom random = new SecureRandom();
    private final boolean applyMask;
    private NetworkHandler handler;
    private byte[] mask;
    private int maskIndex;
    private WebSocket webSocket;
    private boolean isHeaderParsed;

    public Draft06Handler() {
        applyMask = false;
    }

    public Draft06Handler(boolean applyMask) {
        this.applyMask = applyMask;
    }

    public void send(DataFrame frame) {
        handler.write(frame(frame));
    }

    public HandShake handshake(WebSocketApplication app, Request request) {
        final boolean secure = "https".equalsIgnoreCase(request.scheme().toString());

        final HandShake06 handshake = new HandShake06(secure, request.getMimeHeaders(), request.requestURI().toString());
        List<String> protocols = app.getSupportedProtocols();
        final List<String> subProtocol = handshake.getSubProtocol();

        handshake.respond(request.getResponse());
        return handshake;
    }

    public void handshake(URL url) throws IOException {
        final boolean isSecure = "wss".equals(url.getProtocol());

        final StringBuilder origin = new StringBuilder();
        origin.append(isSecure ? "https://" : "http://");
        origin.append(url.getHost());
        if (!isSecure && url.getPort() != 80 || isSecure && url.getPort() != 443) {
            origin.append(":")
                    .append(url.getPort());
        }
        String path = url.getPath();
        if ("".equals(path)) {
            path = "/";
        }

        ClientHandShake clientHS = new ClientHandShake(isSecure, origin.toString(), url.getHost(),
                String.valueOf(url.getPort()), path);
        handler.write(clientHS.getBytes());
        final Map<String, String> headers = readResponse();

        if (headers.isEmpty()) {
            throw new HandshakeException("No response headers received");
        }  // not enough data

        try {
            clientHS.validateServerResponse(headers);
        } catch (HandshakeException e) {
            throw new IOException(e.getMessage());
        }

    }

    public byte[] frame(DataFrame frame) {
        byte[] payloadBytes = frame.getBytes();
//        ByteBuffer buffer = ByteBuffer.allocateDirect(payloadBytes + 8);
        final byte[] lengthBytes = encodeLength(payloadBytes.length);
        int packetLength = 1 + lengthBytes.length;

        byte[] packet = new byte[packetLength + payloadBytes.length];
        packet[0] = frame.getType().setOpcode(frame.isLast() ? (byte) 0x80 : 0);
        System.arraycopy(lengthBytes, 0, packet, 1, lengthBytes.length);
        System.arraycopy(payloadBytes, 0, packet, packetLength, payloadBytes.length);

        if (applyMask) {
            byte[] masked = new byte[packet.length + 4];
            generateMask();
            System.arraycopy(mask, 0, masked, 0, WebSocketEngine.MASK_SIZE);
            for (int i = 0; i < packet.length; i++) {
                masked[i + WebSocketEngine.MASK_SIZE] = (byte) (packet[i] ^ mask[i % WebSocketEngine.MASK_SIZE]);
            }
            packet = masked;
        }

        return packet;
    }

    public DataFrame unframe() {
        byte opcodes = get();
        boolean fin = (opcodes & 0x80) == 0x80;
        byte lengthCode = get();
        long length;
        if (lengthCode <= 125) {
            length = lengthCode;
        } else {
            length = decodeLength(get(lengthCode == 126 ? 2 : 8));
        }
        FrameType type = FrameType.valueOf(opcodes);
        final byte[] data = get((int) length);
        if (data.length != length) {
            final FramingException e = new FramingException(String.format("Data read (%s) is not the expected" +
                    " size (%s)", data.length, length));
            e.printStackTrace();
            throw e;
        }
        final DataFrame frame = type.create();
        frame.setLast(fin);
        type.unframe(frame, data);
        return frame;
    }

    private byte[] get(final int count) {
        final byte[] bytes = handler.get(count);
        if (!applyMask) {
            for (int i = 0; i < bytes.length; i++) {
                bytes[i] ^= mask[maskIndex++ % WebSocketEngine.MASK_SIZE];
            }
        }
        return bytes;
    }

    private byte get() {
        byte b = handler.get();
        if(!applyMask) {
            b ^= mask[maskIndex++ % WebSocketEngine.MASK_SIZE];
        }
        return b;
    }

    public void readFrame() {
//        fill();
        while (handler.ready()) {
            try {
                if (!applyMask) {
                    mask = handler.get(WebSocketEngine.MASK_SIZE);
                    maskIndex = 0;
                }
                unframe().respond(getWebSocket());
            } catch(FramingException fe) {
                fe.printStackTrace();
                getWebSocket().close();
            }
        }
    }

    public WebSocket getWebSocket() {
        return webSocket;
    }

    public void setWebSocket(WebSocket webSocket) {
        this.webSocket = webSocket;
    }

    public void setNetworkHandler(NetworkHandler handler) {
        this.handler = handler;
    }

    public void generateMask() {
        mask = new byte[WebSocketEngine.MASK_SIZE];
        synchronized (random) {
            random.nextBytes(mask);
        }
    }

    /**
     * Convert a byte[] to a long.  Used for rebuilding payload length.
     */
    public long decodeLength(byte[] bytes) {
        return WebSocketEngine.toLong(bytes, 0, bytes.length);
    }


    /**
     * Converts the length given to the appropriate framing data:
     * <ol>
     * <li>0-125 one element that is the payload length.
     * <li>up to 0xFFFF, 3 element array starting with 126 with the following 2 bytes interpreted as
     * a 16 bit unsigned integer showing the payload length.
     * <li>else 9 element array starting with 127 with the following 8 bytes interpreted as a 64-bit
     * unsigned integer (the high bit must be 0) showing the payload length.
     * </ol>
     *
     * @param length the payload size
     * @return the array
     */
    public byte[] encodeLength(final long length) {
        byte[] lengthBytes;
        if (length <= 125) {
            lengthBytes = new byte[1];
            lengthBytes[0] = (byte) length;
        } else {
            byte[] b = WebSocketEngine.toArray(length);
            if (length <= 0xFFFF) {
                lengthBytes = new byte[3];
                lengthBytes[0] = 126;
                System.arraycopy(b, 6, lengthBytes, 1, 2);
            } else {
                lengthBytes = new byte[9];
                lengthBytes[0] = 127;
                System.arraycopy(b, 0, lengthBytes, 1, 8);
            }
        }

        return lengthBytes;
    }

    private Map<String, String> readResponse() throws IOException {
        Map<String, String> headers = new TreeMap<String, String>(new Comparator<String>() {
            public int compare(String o, String o1) {
                return o.compareToIgnoreCase(o1);
            }
        });
        if (!isHeaderParsed) {
            String line = new String(handler.readLine(), "ASCII").trim();
            headers.put(WebSocketEngine.RESPONSE_CODE_HEADER, line.split(" ")[1]);
            while (!isHeaderParsed) {
                line = new String(handler.readLine(), "ASCII").trim();

                if (line.length() == 0) {
                    isHeaderParsed = true;
                } else {
                    String[] parts = line.split(":");
                    headers.put(parts[0].trim(), parts[1].trim());
                }
            }
        }

        return headers;
    }
}
