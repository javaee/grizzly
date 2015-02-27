/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011-2015 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.websockets.rfc6455;

import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.http.HttpContent;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.websockets.Constants;
import org.glassfish.grizzly.websockets.DataFrame;
import org.glassfish.grizzly.websockets.FrameType;
import org.glassfish.grizzly.websockets.HandShake;
import org.glassfish.grizzly.websockets.Masker;
import org.glassfish.grizzly.websockets.ProtocolError;
import org.glassfish.grizzly.websockets.ProtocolHandler;
import org.glassfish.grizzly.websockets.frametypes.BinaryFrameType;
import org.glassfish.grizzly.websockets.frametypes.ClosingFrameType;
import org.glassfish.grizzly.websockets.frametypes.ContinuationFrameType;
import org.glassfish.grizzly.websockets.frametypes.PingFrameType;
import org.glassfish.grizzly.websockets.frametypes.PongFrameType;
import org.glassfish.grizzly.websockets.frametypes.TextFrameType;

import java.net.URI;
import java.util.Locale;

public class RFC6455Handler extends ProtocolHandler {

    private final ParsingState state = new ParsingState();

    // ------------------------------------------------------------ Constructors


    public RFC6455Handler(boolean mask) {
        super(mask);
    }


    // -------------------------------------------- Methods from ProtocolHandler


    @Override
    public HandShake createClientHandShake(URI uri) {
        return new RFC6455HandShake(uri);
    }

    @Override
    public HandShake createServerHandShake(HttpContent requestContent) {
        return new RFC6455HandShake(
                (HttpRequestPacket) requestContent.getHttpHeader());
    }

    @Override
    public byte[] frame(DataFrame frame) {
        byte opcode = checkForLastFrame(frame, getOpcode(frame.getType()));
        final byte[] bytes = frame.getType().getBytes(frame);
        final byte[] lengthBytes = encodeLength(bytes.length);

        int length = 1 + lengthBytes.length + bytes.length + (maskData
                ? Constants.MASK_SIZE
                : 0);
        int payloadStart =
                1 + lengthBytes.length + (maskData ? Constants.MASK_SIZE : 0);
        final byte[] packet = new byte[length];
        packet[0] = opcode;
        System.arraycopy(lengthBytes, 0, packet, 1, lengthBytes.length);
        if (maskData) {
            Masker masker = new Masker();
            packet[1] |= 0x80;
            masker.mask(packet, payloadStart, bytes);
            System.arraycopy(masker.getMask(), 0, packet,
                             payloadStart - Constants.MASK_SIZE,
                             Constants.MASK_SIZE);
        } else {
            System.arraycopy(bytes, 0, packet, payloadStart, bytes.length);
        }
        return packet;
    }

    @Override
    public DataFrame parse(Buffer buffer) {

        DataFrame dataFrame;
        try {
            switch (state.state) {
                case 0:
                    if (buffer.remaining() < 2) {
                        // Don't have enough bytes to read opcode and lengthCode
                        return null;
                    }

                    byte opcode = buffer.get();
                    boolean rsvBitSet = isBitSet(opcode, 6)
                            || isBitSet(opcode, 5)
                            || isBitSet(opcode, 4);
                    if (rsvBitSet) {
                        throw new ProtocolError("RSV bit(s) incorrectly set.");
                    }
                    state.finalFragment = isBitSet(opcode, 7);
                    state.controlFrame = isControlFrame(opcode);
                    state.opcode = (byte) (opcode & 0x7f);
                    state.frameType = valueOf(inFragmentedType, state.opcode);
                    if (!state.finalFragment && state.controlFrame) {
                        throw new ProtocolError("Fragmented control frame");
                    }

                    if (!state.controlFrame) {
                        if (isContinuationFrame(
                                state.opcode) && !processingFragment) {
                            throw new ProtocolError(
                                    "End fragment sent, but wasn't processing any previous fragments");
                        }
                        if (processingFragment && !isContinuationFrame(
                                state.opcode)) {
                            throw new ProtocolError(
                                    "Fragment sent but opcode was not 0");
                        }
                        if (!state.finalFragment && !isContinuationFrame(
                                state.opcode)) {
                            processingFragment = true;
                        }
                        if (!state.finalFragment) {
                            if (inFragmentedType == 0) {
                                inFragmentedType = state.opcode;
                            }
                        }
                    }
                    byte lengthCode = buffer.get();

                    state.masked = (lengthCode & 0x80) == 0x80;
                    state.masker = new Masker(buffer);
                    if (state.masked) {
                        lengthCode ^= 0x80;
                    }
                    state.lengthCode = lengthCode;

                    state.state++;

                case 1:
                    if (state.lengthCode <= 125) {
                        state.length = state.lengthCode;
                    } else {
                        if (state.controlFrame) {
                            throw new ProtocolError(
                                    "Control frame payloads must be no greater than 125 bytes.");
                        }

                        final int lengthBytes = state.lengthCode == 126 ? 2 : 8;
                        if (buffer.remaining() < lengthBytes) {
                            // Don't have enought bytes to read length
                            return null;
                        }
                        state.masker.setBuffer(buffer);
                        state.length =
                                decodeLength(state.masker.unmask(lengthBytes));
                    }
                    state.state++;
                case 2:
                    if (state.masked) {
                        if (buffer.remaining() < Constants.MASK_SIZE) {
                            // Don't have enough bytes to read mask
                            return null;
                        }
                        state.masker.setBuffer(buffer);
                        state.masker.readMask();
                    }
                    state.state++;
                case 3:
                    if (buffer.remaining() < state.length) {
                        return null;
                    }

                    state.masker.setBuffer(buffer);
                    final byte[] data = state.masker.unmask((int) state.length);
                    if (data.length != state.length) {
                        throw new ProtocolError(String.format(
                                "Data read (%s) is not the expected" +
                                        " size (%s)", data.length,
                                state.length));
                    }
                    dataFrame =
                            state.frameType.create(state.finalFragment, data);

                    if (!state.controlFrame && (isTextFrame(
                            state.opcode) || inFragmentedType == 1)) {
                        utf8Decode(state.finalFragment, data, dataFrame);
                    }

                    if (!state.controlFrame && state.finalFragment) {
                        inFragmentedType = 0;
                        processingFragment = false;
                    }
                    state.recycle();

                    break;
                default:
                    // Should never get here
                    throw new IllegalStateException(
                            "Unexpected state: " + state.state);
            }
        } catch (Exception e) {
            state.recycle();
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            } else {
                throw new RuntimeException(e);
            }
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

        throw new ProtocolError(
                "Unknown frame type: " + type.getClass().getName());
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
                throw new ProtocolError(
                        String.format("Unknown frame type: %s, %s",
                                      Integer.toHexString(opcode & 0xFF)
                                              .toUpperCase(Locale.US),
                                      connection));
        }
    }


    // ---------------------------------------------------------- Nested Classes


    private static class ParsingState {
        int state = 0;
        byte opcode = (byte) -1;
        long length = -1;
        FrameType frameType;
        boolean masked;
        Masker masker;
        boolean finalFragment;
        boolean controlFrame;
        private byte lengthCode = -1;

        void recycle() {
            state = 0;
            opcode = (byte) -1;
            length = -1;
            lengthCode = -1;
            masked = false;
            masker = null;
            finalFragment = false;
            controlFrame = false;
            frameType = null;
        }
    }

}
