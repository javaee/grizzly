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

package org.glassfish.grizzly.websockets.draft07;

import java.net.URI;

import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.http.HttpContent;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.websockets.DataFrame;
import org.glassfish.grizzly.websockets.FrameType;
import org.glassfish.grizzly.websockets.FramingException;
import org.glassfish.grizzly.websockets.HandShake;
import org.glassfish.grizzly.websockets.Masker;
import org.glassfish.grizzly.websockets.ProtocolHandler;
import org.glassfish.grizzly.websockets.WebSocketEngine;
import org.glassfish.grizzly.websockets.frametypes.BinaryFrameType;
import org.glassfish.grizzly.websockets.frametypes.ClosingFrameType;
import org.glassfish.grizzly.websockets.frametypes.ContinuationFrameType;
import org.glassfish.grizzly.websockets.frametypes.PingFrameType;
import org.glassfish.grizzly.websockets.frametypes.PongFrameType;
import org.glassfish.grizzly.websockets.frametypes.TextFrameType;

public class Draft07Handler extends ProtocolHandler {
    public Draft07Handler(boolean maskData) {
        super(maskData);
    }

    @Override
    public byte[] frame(DataFrame frame) {
        byte opcode = checkForLastFrame(frame, getOpcode(frame.getType()));
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
        return packet;
    }

    @Override
    public DataFrame parse() {
        byte opcode = handler.get();
        boolean finalFragment = (opcode & 0x80) == 0x80;
        opcode &= 0x7F;
        FrameType type = valueOf(inFragmentedType, opcode);
        if (!finalFragment) {
            if (inFragmentedType == 0) {
                inFragmentedType = opcode;
            }
        } else {
            inFragmentedType = 0;
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
        return type.create(finalFragment, data);
    }

    @Override
    protected boolean isControlFrame(byte opcode) {
        return (opcode & 0x08) == 0x08;
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
                throw new FramingException(String.format("Unknown frame type: %s, %s",
                        Integer.toHexString(opcode & 0xFF).toUpperCase(), handler));
        }
    }

    @Override
    protected HandShake createHandShake(HttpContent requestContent) {
        return new HandShake07((HttpRequestPacket) requestContent.getHttpHeader());
    }

    @Override
    protected HandShake createHandShake(URI uri) {
        return new HandShake07(uri);
    }
}
