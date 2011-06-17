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

package com.sun.grizzly.websockets.draft06;

import com.sun.grizzly.tcp.Request;
import com.sun.grizzly.util.net.URL;
import com.sun.grizzly.websockets.DataFrame;
import com.sun.grizzly.websockets.FrameType;
import com.sun.grizzly.websockets.FramingException;
import com.sun.grizzly.websockets.HandShake;
import com.sun.grizzly.websockets.WebSocketEngine;
import com.sun.grizzly.websockets.WebSocketHandler;

import java.security.SecureRandom;

public class Draft06Handler extends WebSocketHandler {
    private final SecureRandom random = new SecureRandom();
    private final boolean applyMask;
    private byte[] mask;
    private int maskIndex;

    public Draft06Handler() {
        applyMask = false;
    }

    public Draft06Handler(boolean applyMask) {
        this.applyMask = applyMask;
    }

    @Override
    protected HandShake createHandShake(Request request) {
        return new HandShake06(request.getMimeHeaders());
    }

    @Override
    protected HandShake createHandShake(URL url) {
        return new HandShake06(url);
    }

    public byte[] frame(DataFrame frame) {
        byte[] payloadBytes = frame.getBytes();
//        ByteBuffer buffer = ByteBuffer.allocateDirect(payloadBytes + 8);
        final byte[] lengthBytes = encodeLength(payloadBytes.length);
        int packetLength = 1 + lengthBytes.length;

        byte[] packet = new byte[packetLength + payloadBytes.length];
        final byte opcode = frame.getType().getOpCode();
        packet[0] = (byte) (opcode | (frame.isLast() ? (byte) 0x80 : 0));
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

    @Override
    public DataFrame unframe() {
        if (!applyMask) {
            mask = handler.get(WebSocketEngine.MASK_SIZE);
            maskIndex = 0;
        }
        byte opcodes = get();
        boolean fin = (opcodes & 0x80) == 0x80;
        byte lengthCode = get();
        long length;
        if (lengthCode <= 125) {
            length = lengthCode;
        } else {
            length = decodeLength(get(lengthCode == 126 ? 2 : 8));
        }
        FrameType type = Draft06FrameType.valueOf(opcodes);
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
        if (!applyMask) {
            b ^= mask[maskIndex++ % WebSocketEngine.MASK_SIZE];
        }
        return b;
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
    @Override
    public void send(byte[] data) {
        send(new DataFrame(data, Draft06FrameType.BINARY));
    }

    @Override
    public void send(String data) {
        send(new DataFrame(data, Draft06FrameType.TEXT));
    }

    @Override
    public void stream(boolean last, byte[] bytes, int off, int len) {
        DataFrame frame = new DataFrame(midstream ? Draft06FrameType.CONTINUATION : Draft06FrameType.BINARY);
        midstream = !last;
        frame.setLast(last);
        byte[] data = new byte[len];
        System.arraycopy(bytes, off, data, 0, len);
        frame.setPayload(data);
        send(frame);
    }

    @Override
    public void close(int code, String reason) {
        send(new ClosingFrame(code, reason));
    }
}