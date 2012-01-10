/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011-2012 Oracle and/or its affiliates. All rights reserved.
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

import com.sun.grizzly.tcp.Request;
import com.sun.grizzly.util.net.URL;
import com.sun.grizzly.websockets.DataFrame;
import com.sun.grizzly.websockets.FrameType;
import com.sun.grizzly.websockets.HandShake;
import com.sun.grizzly.websockets.Masker;
import com.sun.grizzly.websockets.ProtocolError;
import com.sun.grizzly.websockets.ProtocolHandler;
import com.sun.grizzly.websockets.WebSocketEngine;
import com.sun.grizzly.websockets.frametypes.BinaryFrameType;
import com.sun.grizzly.websockets.frametypes.ClosingFrameType;
import com.sun.grizzly.websockets.frametypes.ContinuationFrameType;
import com.sun.grizzly.websockets.frametypes.PingFrameType;
import com.sun.grizzly.websockets.frametypes.PongFrameType;
import com.sun.grizzly.websockets.frametypes.TextFrameType;

public class Draft07Handler extends ProtocolHandler {
    public Draft07Handler(boolean maskData) {
        super(maskData);
    }

    @Override
    public byte[] frame(DataFrame frame) {
        byte opcode = checkForLastFrame(frame, getOpcode(frame.getType()));
        final byte[] bytes = frame.getType().getBytes(frame);
        final int payloadLen = ((bytes != null) ? bytes.length : 0);
        final byte[] lengthBytes = encodeLength(payloadLen);

        int length = 1 + lengthBytes.length + payloadLen + (maskData ? WebSocketEngine.MASK_SIZE : 0);
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
            System.arraycopy(bytes, 0, packet, payloadStart, payloadLen);
        }
        return packet;
    }

    @Override
    public DataFrame unframe() {
        byte opcode = handler.get();
        boolean rsvBitSet = isBitSet(opcode, 6) 
                || isBitSet(opcode, 5)
                || isBitSet(opcode, 4);
        if (rsvBitSet) {
            throw new ProtocolError("RSV bit(s) incorrectly set.");
        }
        final boolean finalFragment = isBitSet(opcode, 7);
        final boolean controlFrame = isControlFrame(opcode);
        opcode &= 0x7F;
        FrameType type = valueOf(inFragmentedType, opcode);
        if (!finalFragment && controlFrame) {
            throw new ProtocolError("Fragmented control frame");
        }

        if (!controlFrame) {
            if (isContinuationFrame(opcode) && !processingFragment) {
                throw new ProtocolError("End fragment sent, but wasn't processing any previous fragments");
            }
            if (processingFragment && !isContinuationFrame(opcode)) {
                throw new ProtocolError("Fragment sent but opcode was 0");
            }
            if (!finalFragment && !isContinuationFrame(opcode)) {
                processingFragment = true;
            }
            if (!finalFragment) {
                if (inFragmentedType == 0) {
                    inFragmentedType = opcode;
                }
            } else {
                processingFragment = false;
            }
        }

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
            if (controlFrame) {
                throw new ProtocolError("Control frame payloads must be no greater than 125 bytes.");
            }
            length = decodeLength(handler.get(lengthCode == 126 ? 2 : 8));
        }
        if (masked) {
            masker.setMask(handler.get(WebSocketEngine.MASK_SIZE));
        }
        final byte[] data = masker.unmask((int) length);
        if (data.length != length) {
            throw new ProtocolError(String.format("Data read (%s) is not the expected" +
                    " size (%s)", data.length, length));
        }

        DataFrame dataFrame = type.create(finalFragment, data);

        if (!controlFrame && (isTextFrame(opcode) || inFragmentedType == 1)) {
            utf8Decode(finalFragment, data, dataFrame);
        }

        if (!controlFrame && finalFragment) {
            inFragmentedType = 0;
        }
        return dataFrame;
    }



    @Override
    protected boolean isControlFrame(byte opcode) {
        return (opcode & 0x08) == 0x08;
    }
    
    private boolean isBitSet(final byte b, int bit) {
        return ((b >> bit & 1) != 0);
    }

    private boolean isContinuationFrame(byte opcode) {
        return opcode == 0;
    }

    private boolean isTextFrame(byte opcode) {
        return opcode == 1;
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

        throw new ProtocolError("Unknown frame type: " + type.getClass().getName());
    }

    private FrameType valueOf(byte fragmentType, byte value) {
        final int opcode = value & 0xF;
        switch (opcode) {
            case 0x00:
                return new ContinuationFrameType((fragmentType & 0x01) == 0x01);
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
                throw new ProtocolError(String.format("Unknown frame type: %s, %s",
                        Integer.toHexString(opcode & 0xFF).toUpperCase(), handler));
        }
    }

    @Override
    protected HandShake createHandShake(Request request) {
        return new HandShake07(request);
    }

    @Override
    protected HandShake createHandShake(URL url) {
        return new HandShake07(url);
    }
}
