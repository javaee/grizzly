/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2011 Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.util.Arrays;
import java.util.logging.Logger;

/**
 * In memory representation of a websocket frame.
 *
 * @see <a href="http://tools.ietf.org/html/draft-ietf-hybi-thewebsocketprotocol-05#section-4.3">Frame Definition</a>
 */
public class DataFrame {
    private static final Logger logger = Logger.getLogger(WebSocketEngine.WEBSOCKET);
    private String payload;
    private byte[] bytes;
    private FrameType type;

    public DataFrame() {
    }

    public DataFrame(FrameType type) {
        this.type = type;
    }

    public DataFrame(String data) {
        setPayload(data);
    }

    public DataFrame(byte[] data) {
        setPayload(data);
    }

    public DataFrame(FrameType type, byte[] bytes) {
        this.bytes = bytes;
        this.type = type;
    }

    public FrameType getType() {
        return type;
    }

    public void setType(FrameType type) {
        this.type = type;
    }

    public String getTextPayload() {
        if (type != FrameType.TEXT) {
            throw new FramingException("Frame data is not text: " + type);
        }
        return payload;
    }

    public void setPayload(String payload) {
        type = FrameType.TEXT;
        this.payload = payload;
    }

    public void setPayload(byte[] bytes) {
        this.bytes = bytes;
    }

    public byte[] getBinaryPayload() {
        if (type == FrameType.TEXT) {
            throw new FramingException("Frame data is not binary: " + type);
        }
        return bytes;
    }

    public byte[] frame() {
        byte[] payloadBytes = type.frame(this);
        final byte[] lengthBytes = convert(payloadBytes.length);
        int packetLength = 1 + lengthBytes.length;

        byte[] packet = new byte[packetLength + payloadBytes.length];
        packet[0] = type.setOpcode((byte) 0x80);
        System.arraycopy(lengthBytes, 0, packet, 1, lengthBytes.length);
        System.arraycopy(payloadBytes, 0, packet, packetLength, payloadBytes.length);
        return packet;
    }

    public void unframe(NetworkHandler handler) throws IOException {
        byte opcodes = handler.get();
        boolean fin = (opcodes & 0x80) == 0x80;
        byte lengthCode = handler.get();
        long length;
        if (lengthCode <= 125) {
            length = lengthCode;
        } else {
            length = convert(handler.get(lengthCode == 126 ? 2 : 8));
        }
        type = FrameType.valueOf(opcodes);
        final byte[] data = handler.get((int) length);
        if (data.length != length) {
            final FramingException e = new FramingException(String.format("Data read (%s) is not the expected" +
                    " size (%s)", data.length, length));
            e.printStackTrace();
            throw e;
        }
        type.unframe(this, data);
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
    public byte[] convert(final long length) {
        byte[] lengthBytes;
        if (length <= 125) {
            lengthBytes = new byte[1];
            lengthBytes[0] = (byte) length;
        } else {
            byte[] b = toArray(length);
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

    public static byte[] toArray(long length) {
        byte[] b = new byte[8];
        for (int i = 0; i < 8; i++) {
            b[7 - i] = (byte) (length >>> i * 8);
        }
        return b;
    }

    /**
     * Convert a byte[] to a long.  Used for rebuilding payload length.
     */
    public long convert(byte[] bytes) {
        long value = 0;
        for (byte aByte : bytes) {
            value <<= 8;
            value ^= (long) aByte & 0xFF;
        }

        return value;
    }

    public void respond(WebSocket socket) throws IOException {
        getType().respond(socket, this);
    }

    @Override
    public String toString() {
        return new StringBuilder("DataFrame")
                .append("{")
                .append("type=").append(type)
                .append(", payload='").append(getTextPayload()).append('\'')
                .append(", bytes=").append(Arrays.toString(bytes))
                .append('}')
                .toString();
    }
}
