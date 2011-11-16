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

package org.glassfish.grizzly.websockets.draft06;

import java.net.URI;

import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.http.HttpContent;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.websockets.DataFrame;
import org.glassfish.grizzly.websockets.FrameType;
import org.glassfish.grizzly.websockets.FramingException;
import org.glassfish.grizzly.websockets.HandShake;
import org.glassfish.grizzly.websockets.Masker;
import org.glassfish.grizzly.websockets.ProtocolError;
import org.glassfish.grizzly.websockets.ProtocolHandler;
import org.glassfish.grizzly.websockets.WebSocketEngine;
import org.glassfish.grizzly.websockets.frametypes.BinaryFrameType;
import org.glassfish.grizzly.websockets.frametypes.ClosingFrameType;
import org.glassfish.grizzly.websockets.frametypes.ContinuationFrameType;
import org.glassfish.grizzly.websockets.frametypes.PingFrameType;
import org.glassfish.grizzly.websockets.frametypes.PongFrameType;
import org.glassfish.grizzly.websockets.frametypes.TextFrameType;

public class Draft06Handler extends ProtocolHandler {

    public Draft06Handler() {
        super(false);
    }

    public Draft06Handler(boolean maskData) {
        super(maskData);
    }

    @Override
    protected HandShake createHandShake(HttpContent requestContent) {
        return new HandShake06((HttpRequestPacket) requestContent.getHttpHeader());
    }

    @Override
    protected HandShake createHandShake(URI uri) {
        return new HandShake06(uri);
    }

    @Override
    public byte[] frame(DataFrame frame) {
        byte opcode = checkForLastFrame(frame, getOpcode(frame.getType()));
        byte[] payloadBytes = frame.getBytes();

//        ByteBuffer buffer = ByteBuffer.allocate(payloadBytes.length  + 8);
        final byte[] lengthBytes = encodeLength(payloadBytes.length);
        int packetLength = 1 + lengthBytes.length;

        byte[] packet = new byte[packetLength + payloadBytes.length];
        packet[0] = opcode;
        System.arraycopy(lengthBytes, 0, packet, 1, lengthBytes.length);
        System.arraycopy(payloadBytes, 0, packet, packetLength, payloadBytes.length);

        if (maskData) {
            packet = new Masker().maskAndPrepend(packet);
        }

        return packet;
    }

    @Override
    public DataFrame parse(Buffer buffer) {
        Masker masker = new Masker(buffer);
        if (!maskData) {
            if (buffer.remaining() < WebSocketEngine.MASK_SIZE) {
                // Don't have enough bytes to read mask
                return null;
            }
            
            masker.readMask();
        }
        
        if (buffer.remaining() < 2) {
            // Don't have enough bytes to read opcode and lengthCode
            return null;
        }
        
        byte opcode = masker.unmask();
        boolean finalFragment = (opcode & 0x80) == 0x80;
        final boolean controlFrame = isControlFrame(opcode);
        opcode &= 0x7F;
        FrameType type = valueOf(inFragmentedType, opcode);
        if (!finalFragment) {
            if(inFragmentedType == 0) {
                inFragmentedType = opcode;
            }
        } else {
            inFragmentedType = 0;
        }

        byte lengthCode = masker.unmask();
        long length;
        if (lengthCode <= 125) {
            length = lengthCode;
        } else {
            final int lengthBytes = lengthCode == 126 ? 2 : 8;
            if (buffer.remaining() < lengthBytes) {
                // Don't have enought bytes to read length
                return null;
            }
            length = decodeLength(masker.unmask(lengthBytes));
        }
        
        if (buffer.remaining() < length) {
            // Don't have enought bytes to read data
            return null;
        }
        
        final byte[] data = masker.unmask((int) length);
        if (data.length != length) {
            throw new ProtocolError(String.format("Data read (%s) is not the expected" +
                    " size (%s)", data.length, length));
        }
        DataFrame dataFrame = type.create(finalFragment, data);

        if (!controlFrame && (isTextFrame(opcode) || inFragmentedType == 4)) {
            utf8Decode(finalFragment, data, dataFrame);
        }

        if (!controlFrame && finalFragment) {
            inFragmentedType = 0;
        }
        return dataFrame;
    }

    private boolean isTextFrame(byte opcode) {
        return opcode == 4;
    }

    @Override
    protected boolean isControlFrame(byte opcode) {
        return opcode == 0x01
                || opcode == 0x02
                || opcode == 0x03;
    }

    private byte getOpcode(FrameType type) {
        if (type instanceof ClosingFrameType) {
            return 0x01;
        } else if (type instanceof PingFrameType) {
            return 0x02;
        } else if (type instanceof PongFrameType) {
            return 0x03;
        } else if (type instanceof TextFrameType) {
            return 0x04;
        } else if (type instanceof BinaryFrameType) {
            return 0x05;
        }

        throw new ProtocolError("Unknown frame type: " + type.getClass().getName());
    }

    private FrameType valueOf(byte fragmentType, byte value) {
        final int opcode = value & 0xF;
        switch (opcode & 0xF) {
            case 0x00:
                return new ContinuationFrameType((fragmentType & 0x04) == 0x04);
            case 0x01:
                return new ClosingFrameType();
            case 0x02:
                return new PingFrameType();
            case 0x03:
                return new PongFrameType();
            case 0x04:
                return new TextFrameType();
            case 0x05:
                return new BinaryFrameType();
            default:
                throw new ProtocolError("Unknown frame type: " + value);
        }
    }
}