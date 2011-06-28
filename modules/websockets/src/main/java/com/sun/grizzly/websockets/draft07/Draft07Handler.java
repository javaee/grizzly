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

package com.sun.grizzly.websockets.draft07;

import com.sun.grizzly.tcp.Request;
import com.sun.grizzly.util.net.URL;
import com.sun.grizzly.websockets.DataFrame;
import com.sun.grizzly.websockets.FrameType;
import com.sun.grizzly.websockets.FramingException;
import com.sun.grizzly.websockets.HandShake;
import com.sun.grizzly.websockets.Masker;
import com.sun.grizzly.websockets.ProtocolHandler;
import com.sun.grizzly.websockets.ServerNetworkHandler;
import com.sun.grizzly.websockets.WebSocketEngine;
import com.sun.grizzly.websockets.WebSocketException;
import com.sun.grizzly.websockets.frametypes.BinaryFrameType;
import com.sun.grizzly.websockets.frametypes.ClosingFrameType;
import com.sun.grizzly.websockets.frametypes.ContinuationFrameType;
import com.sun.grizzly.websockets.frametypes.PingFrameType;
import com.sun.grizzly.websockets.frametypes.PongFrameType;
import com.sun.grizzly.websockets.frametypes.TextFrameType;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Draft07Handler extends ProtocolHandler {

    public static boolean DEBUG = false;
    private PrintWriter OUT;
    private boolean newline = true;
    private SimpleDateFormat format = new SimpleDateFormat("hh:mm:ss: ");

    public Draft07Handler(boolean maskData) {
        super(maskData);
    }

    @Override
    public byte[] frame(DataFrame frame) {
        try {
            out("frame: handler = %s", handler);
            byte opcode = getOpcode(frame.getType());
            if (!frame.isLast()) {
                if (midstream && opcode != fragmentedType && !isControlFrame(opcode)) {
                    throw new WebSocketException("Attempting to send a message while sending fragments of another");
                }
                if (midstream) {
                    opcode = (byte) 0x80;
                } else {
                    fragmentedType = opcode;
                    midstream = true;
                    opcode &= 0x7F;
                }
            } else if (midstream) {
                opcode = (byte) 0x80;
            } else {
                opcode |= 0x80;
            }
            out(", opcode = %s, handler = %s", Integer.toHexString(opcode & 0xFF), handler);
            final byte[] bytes = frame.getType().getBytes(frame);
            final byte[] lengthBytes = encodeLength(bytes.length);

            int length = 1 + lengthBytes.length + bytes.length + (maskData ? WebSocketEngine.MASK_SIZE : 0);
            int payloadStart = 1 + lengthBytes.length + (maskData ? WebSocketEngine.MASK_SIZE : 0);
            final byte[] packet = new byte[length];
            packet[0] = opcode;
            System.arraycopy(lengthBytes, 0, packet, 1, lengthBytes.length);
            if (maskData) {
                Masker masker = new Masker(handler);
                masker.generateMask();
                packet[1] |= 0x80;
                masker.mask(packet, payloadStart, bytes);
                System.arraycopy(masker.getMask(), 0, packet, payloadStart - WebSocketEngine.MASK_SIZE,
                        WebSocketEngine.MASK_SIZE);
            } else {
                System.arraycopy(bytes, 0, packet, payloadStart, bytes.length);
            }
            out(", packet=%s", WebSocketEngine.toString(packet, 0, packet.length));
            return packet;
        } finally {
            out("\n");
        }
    }

    private void out(final String text, final Object... params) {
        if (DEBUG) {
            if(OUT == null) {
                try {
                    OUT = new PrintWriter(handler instanceof ServerNetworkHandler ? "snh.out" : "cnh.out");
                } catch (FileNotFoundException e) {
                    throw new RuntimeException(e.getMessage(), e);
                }
            }
            if(newline) {
                OUT.write(format.format(new Date()));
                newline = false;
            }
            OUT.write(String.format(text, params));
            OUT.flush();
            newline = text.endsWith("\n");
        }
    }

    private boolean isControlFrame(byte opcode) {
        return opcode == 0x08
                || opcode == 0x09
                || opcode == 0x0A;
    }

    @Override
    public DataFrame unframe() {
        try {
            out("unframe: handler = %s", handler);
            byte opcode = handler.get();
            boolean finalFragment = (opcode & 0x80) == 0x80;
            opcode &= 0x7F;
            out(", opcode = %s ", Integer.toHexString(opcode & 0xFF));
            if (!finalFragment && !midstream) {
                midstream = true;
                fragmentedType = opcode;
            }
            FrameType type = valueOf(midstream, opcode);
            midstream = !finalFragment;

            byte lengthCode = handler.get();
            Masker masker = new Masker(handler);

            final boolean masked = (lengthCode & 0x80) == 0x80;
            if (masked) {
                lengthCode ^= 0x80;
            }
            long length;
            if (lengthCode <= 125) {
                length = lengthCode;
            } else {
                length = decodeLength(handler.get(lengthCode == 126 ? 2 : 8));
            }
            if (masked) {
                masker.setMask(handler.get(WebSocketEngine.MASK_SIZE));
            }
            final byte[] data = masker.unmask((int) length);
            if (data.length != length) {
                throw new FramingException(String.format("Data read (%s) is not the expected" +
                        " size (%s)", data.length, length));
            }
            final DataFrame frame = type.create(finalFragment, data);
            out(", dataframe = %s", frame);
            return frame;
        } finally {
            out("\n");
        }
    }

    private byte getOpcode(FrameType type) {
        if (type instanceof TextFrameType) {
            return 0x01;
        } else if (type instanceof BinaryFrameType) {
            return 0x02;
        } else if (type instanceof ClosingFrameType) {
            return 0x08;
        } else if (type instanceof PingFrameType) {
            return 0x09;
        } else if (type instanceof PongFrameType) {
            return 0x0A;
        }

        throw new FramingException("Unknown frame type: " + type.getClass().getName());
    }

    private void validateOpcode(byte opcode) {
        if (midstream && opcode != fragmentedType) {
            throw new WebSocketException(
                    "Attempting to process a message while receiving fragments of another");
        }
    }

    private FrameType valueOf(boolean midstream, byte value) {
        final int opcode = value & 0xF;
        if (midstream) {
            return new ContinuationFrameType((fragmentedType & 0x01) == 0x01);
        } else {
            switch (opcode) {
                case 0x01:
                    return new TextFrameType();
                case 0x02:
                    return new BinaryFrameType();
                case 0x08:
                    return new ClosingFrameType();
                case 0x09:
                    return new PingFrameType();
                case 0x0A:
                    return new PongFrameType();
                default:
                    throw new FramingException(String.format("Unknown frame type: %s (%s)", opcode, Integer.toString(opcode & 0xFF, 16).toUpperCase()));
            }
        }
    }

    @Override
    protected HandShake createHandShake(Request request) {
        return new HandShake07(request.getMimeHeaders());
    }

    @Override
    protected HandShake createHandShake(URL url) {
        return new HandShake07(url);
    }
}
