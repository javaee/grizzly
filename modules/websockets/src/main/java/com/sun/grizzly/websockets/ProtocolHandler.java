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

package com.sun.grizzly.websockets;

import com.sun.grizzly.tcp.Request;
import com.sun.grizzly.util.net.URL;
import com.sun.grizzly.websockets.draft06.ClosingFrame;
import com.sun.grizzly.websockets.frametypes.BinaryFrameType;
import com.sun.grizzly.websockets.frametypes.TextFrameType;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public abstract class ProtocolHandler {
    protected NetworkHandler handler;
    private boolean isHeaderParsed;
    private WebSocket webSocket;
    protected byte inFragmentedType;
    protected byte outFragmentedType;
    protected final boolean maskData;

    public ProtocolHandler(boolean maskData) {
        this.maskData = maskData;
    }

    public HandShake handshake(WebSocketApplication app, Request request) {
        final HandShake handshake = createHandShake(request);
        handshake.respond(request.getResponse());

        List<String> protocols = app.getSupportedProtocols();
        final List<String> subProtocol = handshake.getSubProtocol();
        return handshake;
    }

    public HandShake handshake(URL url) throws IOException {
        HandShake handshake = createHandShake(url);
        handshake.initiate(handler);
        handshake.validateServerResponse(readResponse());

        return handshake;
    }

    public void send(DataFrame frame) {
        handler.write(frame(frame));
    }

    public NetworkHandler getNetworkHandler() {
        return handler;
    }

    public void setNetworkHandler(NetworkHandler handler) {
        this.handler = handler;
    }

    public WebSocket getWebSocket() {
        return webSocket;
    }

    public void setWebSocket(WebSocket webSocket) {
        this.webSocket = webSocket;
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
                    final int index = line.indexOf(":");
                    headers.put(line.substring(0, index).trim(), line.substring(index+1).trim());
                }
            }
        }

        return headers;
    }

    public abstract byte[] frame(DataFrame frame);

    public void readFrame() {
        while (handler.ready()) {
            try {
                unframe().respond(getWebSocket());
            } catch (FramingException fe) {
                fe.printStackTrace();
                System.out.println("handler = " + handler);
                getWebSocket().close();
            }
        }
    }

    protected abstract HandShake createHandShake(Request request);

    protected abstract HandShake createHandShake(URL url);

    public void send(byte[] data) {
        send(new DataFrame(new BinaryFrameType(), data));
    }

    public void send(String data) {
        send(new DataFrame(new TextFrameType(), data));
    }

    public void stream(boolean last, byte[] bytes, int off, int len) {
        send(new DataFrame(new BinaryFrameType(), bytes, last));
    }

    public void stream(boolean last, String fragment) {
        send(new DataFrame(new TextFrameType(), fragment, last));
    }

    public void close(int code, String reason) {
        send(new ClosingFrame(code, reason));
    }

    public abstract DataFrame unframe();

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

    protected void validate(final byte fragmentType, byte opcode) {
        if (fragmentType != 0 && opcode != fragmentType && !isControlFrame(opcode)) {
            throw new WebSocketException("Attempting to send a message while sending fragments of another");
        }
    }

    protected abstract boolean isControlFrame(byte opcode);

    protected byte checkForLastFrame(DataFrame frame, byte opcode) {
        byte local = opcode;
        if (!frame.isLast()) {
            validate(outFragmentedType, local);
            if (outFragmentedType != 0) {
                local = 0x00;
            } else {
                outFragmentedType = local;
                local &= 0x7F;
            }
        } else if (outFragmentedType != 0) {
            local = (byte) 0x80;
            outFragmentedType = 0;
        } else {
            local |= 0x80;
        }
        return local;
    }
}
