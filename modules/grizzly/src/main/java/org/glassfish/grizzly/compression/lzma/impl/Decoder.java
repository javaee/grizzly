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

package org.glassfish.grizzly.compression.lzma.impl;


import org.glassfish.grizzly.compression.lzma.LZMADecoder;
import org.glassfish.grizzly.compression.lzma.impl.lz.OutWindow;
import org.glassfish.grizzly.compression.lzma.impl.rangecoder.BitTreeDecoder;
import org.glassfish.grizzly.compression.lzma.impl.rangecoder.RangeDecoder;

import java.io.IOException;

/**
 * RangeDecoder
 *
 * @author Igor Pavlov
 */
public class Decoder {


    public enum State {
        ERR,
        NEED_MORE_DATA,
        DONE
    }

    static class LenDecoder {

        short[] m_Choice = new short[2];
        BitTreeDecoder[] m_LowCoder = new BitTreeDecoder[Base.kNumPosStatesMax];
        BitTreeDecoder[] m_MidCoder = new BitTreeDecoder[Base.kNumPosStatesMax];
        BitTreeDecoder m_HighCoder = new BitTreeDecoder(Base.kNumHighLenBits);
        int m_NumPosStates = 0;

        public void create(int numPosStates) {
            for (; m_NumPosStates < numPosStates; m_NumPosStates++) {
                m_LowCoder[m_NumPosStates] = new BitTreeDecoder(Base.kNumLowLenBits);
                m_MidCoder[m_NumPosStates] = new BitTreeDecoder(Base.kNumMidLenBits);
            }
        }

        public void init() {
            RangeDecoder.initBitModels(m_Choice);
            for (int posState = 0; posState < m_NumPosStates; posState++) {
                m_LowCoder[posState].init();
                m_MidCoder[posState].init();
            }
            m_HighCoder.init();
        }

        public int decode(RangeDecoder rangeDecoder, int posState) throws IOException {
            if (rangeDecoder.decodeBit(m_Choice, 0) == 0) {
                return m_LowCoder[posState].decode(rangeDecoder);
            }
            int symbol = Base.kNumLowLenSymbols;
            if (rangeDecoder.decodeBit(m_Choice, 1) == 0) {
                symbol += m_MidCoder[posState].decode(rangeDecoder);
            } else {
                symbol += Base.kNumMidLenSymbols + m_HighCoder.decode(rangeDecoder);
            }
            return symbol;
        }
    }

    class LiteralDecoder {

        class Decoder2 {

            short[] m_Decoders = new short[0x300];

            public void init() {
                RangeDecoder.initBitModels(m_Decoders);
            }

            public byte decodeNormal(RangeDecoder rangeDecoder) throws IOException {
                int symbol = 1;
                do {
                    symbol = (symbol << 1) | rangeDecoder.decodeBit(m_Decoders, symbol);
                } while (symbol < 0x100);
                return (byte) symbol;
            }

            public byte decodeWithMatchByte(RangeDecoder rangeDecoder, byte matchByte) throws IOException {
                int symbol = 1;
                do {
                    int matchBit = (matchByte >> 7) & 1;
                    matchByte <<= 1;
                    int bit = rangeDecoder.decodeBit(m_Decoders, ((1 + matchBit) << 8) + symbol);
                    symbol = (symbol << 1) | bit;
                    if (matchBit != bit) {
                        while (symbol < 0x100) {
                            symbol = (symbol << 1) | rangeDecoder.decodeBit(m_Decoders, symbol);
                        }
                        break;
                    }
                } while (symbol < 0x100);
                return (byte) symbol;
            }
        }
        Decoder2[] m_Coders;
        int m_NumPrevBits;
        int m_NumPosBits;
        int m_PosMask;

        public void create(int numPosBits, int numPrevBits) {
            if (m_Coders != null && m_NumPrevBits == numPrevBits && m_NumPosBits == numPosBits) {
                return;
            }
            m_NumPosBits = numPosBits;
            m_PosMask = (1 << numPosBits) - 1;
            m_NumPrevBits = numPrevBits;
            int numStates = 1 << (m_NumPrevBits + m_NumPosBits);
            m_Coders = new Decoder2[numStates];
            for (int i = 0; i < numStates; i++) {
                m_Coders[i] = new Decoder2();
            }
        }

        public void init() {
            int numStates = 1 << (m_NumPrevBits + m_NumPosBits);
            for (int i = 0; i < numStates; i++) {
                m_Coders[i].init();
            }
        }

        Decoder2 getDecoder(int pos, byte prevByte) {
            return m_Coders[((pos & m_PosMask) << m_NumPrevBits) + ((prevByte & 0xFF) >>> (8 - m_NumPrevBits))];
        }
    }
    OutWindow m_OutWindow = new OutWindow();
    RangeDecoder m_RangeDecoder = new RangeDecoder();
    short[] m_IsMatchDecoders = new short[Base.kNumStates << Base.kNumPosStatesBitsMax];
    short[] m_IsRepDecoders = new short[Base.kNumStates];
    short[] m_IsRepG0Decoders = new short[Base.kNumStates];
    short[] m_IsRepG1Decoders = new short[Base.kNumStates];
    short[] m_IsRepG2Decoders = new short[Base.kNumStates];
    short[] m_IsRep0LongDecoders = new short[Base.kNumStates << Base.kNumPosStatesBitsMax];
    BitTreeDecoder[] m_PosSlotDecoder = new BitTreeDecoder[Base.kNumLenToPosStates];
    short[] m_PosDecoders = new short[Base.kNumFullDistances - Base.kEndPosModelIndex];
    BitTreeDecoder m_PosAlignDecoder = new BitTreeDecoder(Base.kNumAlignBits);
    LenDecoder m_LenDecoder = new LenDecoder();
    LenDecoder m_RepLenDecoder = new LenDecoder();
    LiteralDecoder m_LiteralDecoder = new LiteralDecoder();
    int m_DictionarySize = -1;
    int m_DictionarySizeCheck = -1;
    int m_PosStateMask;

    public Decoder() {
        for (int i = 0; i < Base.kNumLenToPosStates; i++) {
            m_PosSlotDecoder[i] = new BitTreeDecoder(Base.kNumPosSlotBits);
        }
    }

    boolean setDictionarySize(int dictionarySize) {
        if (dictionarySize < 0) {
            return false;
        }
        if (m_DictionarySize != dictionarySize) {
            m_DictionarySize = dictionarySize;
            m_DictionarySizeCheck = Math.max(m_DictionarySize, 1);
            m_OutWindow.create(Math.max(m_DictionarySizeCheck, (1 << 12)));
        }
        return true;
    }

    boolean setLcLpPb(int lc, int lp, int pb) {
        if (lc > Base.kNumLitContextBitsMax || lp > 4 || pb > Base.kNumPosStatesBitsMax) {
            return false;
        }
        m_LiteralDecoder.create(lp, lc);
        int numPosStates = 1 << pb;
        m_LenDecoder.create(numPosStates);
        m_RepLenDecoder.create(numPosStates);
        m_PosStateMask = numPosStates - 1;
        return true;
    }

    void init() throws IOException {
        m_OutWindow.init(false);

        RangeDecoder.initBitModels(m_IsMatchDecoders);
        RangeDecoder.initBitModels(m_IsRep0LongDecoders);
        RangeDecoder.initBitModels(m_IsRepDecoders);
        RangeDecoder.initBitModels(m_IsRepG0Decoders);
        RangeDecoder.initBitModels(m_IsRepG1Decoders);
        RangeDecoder.initBitModels(m_IsRepG2Decoders);
        RangeDecoder.initBitModels(m_PosDecoders);

        m_LiteralDecoder.init();
        int i;
        for (i = 0; i < Base.kNumLenToPosStates; i++) {
            m_PosSlotDecoder[i].init();
        }
        m_LenDecoder.init();
        m_RepLenDecoder.init();
        m_PosAlignDecoder.init();
        m_RangeDecoder.init();
    }

    public State code(LZMADecoder.LZMAInputState state, long outSize) throws IOException {

        if (!state.decInitialized) {
            state.decInitialized = true;
            m_RangeDecoder.setBuffer(state.getSrc());
            m_OutWindow.setBuffer(state.getDst());
            init();
        }
        while (outSize < 0 || state.nowPos64 < outSize) {
            int posState = (int) state.nowPos64 & m_PosStateMask;
            if (m_RangeDecoder.decodeBit(m_IsMatchDecoders, (state.state << Base.kNumPosStatesBitsMax) + posState) == 0) {
                LiteralDecoder.Decoder2 decoder2 = m_LiteralDecoder.getDecoder((int) state.nowPos64, state.prevByte);
                if (!Base.stateIsCharState(state.state)) {
                    state.prevByte = decoder2.decodeWithMatchByte(m_RangeDecoder, m_OutWindow.getByte(state.rep0));
                } else {
                    state.prevByte = decoder2.decodeNormal(m_RangeDecoder);
                }
                m_OutWindow.putByte(state.prevByte);
                state.state = Base.stateUpdateChar(state.state);
                state.nowPos64++;
            } else {
                int len;
                if (m_RangeDecoder.decodeBit(m_IsRepDecoders, state.state) == 1) {
                    len = 0;
                    if (m_RangeDecoder.decodeBit(m_IsRepG0Decoders, state.state) == 0) {
                        if (m_RangeDecoder.decodeBit(m_IsRep0LongDecoders, (state.state << Base.kNumPosStatesBitsMax) + posState) == 0) {
                            state.state = Base.stateUpdateShortRep(state.state);
                            len = 1;
                        }
                    } else {
                        int distance;
                        if (m_RangeDecoder.decodeBit(m_IsRepG1Decoders, state.state) == 0) {
                            distance = state.rep1;
                        } else {
                            if (m_RangeDecoder.decodeBit(m_IsRepG2Decoders, state.state) == 0) {
                                distance = state.rep2;
                            } else {
                                distance = state.rep3;
                                state.rep3 = state.rep2;
                            }
                            state.rep2 = state.rep1;
                        }
                        state.rep1 = state.rep0;
                        state.rep0 = distance;
                    }
                    if (len == 0) {
                        len = m_RepLenDecoder.decode(m_RangeDecoder, posState) + Base.kMatchMinLen;
                        state.state = Base.stateUpdateRep(state.state);
                    }
                } else {
                    state.rep3 = state.rep2;
                    state.rep2 = state.rep1;
                    state.rep1 = state.rep0;
                    len = Base.kMatchMinLen + m_LenDecoder.decode(m_RangeDecoder, posState);
                    state.state = Base.stateUpdateMatch(state.state);
                    int posSlot = m_PosSlotDecoder[Base.getLenToPosState(len)].decode(m_RangeDecoder);
                    if (posSlot >= Base.kStartPosModelIndex) {
                        int numDirectBits = (posSlot >> 1) - 1;
                        state.rep0 = ((2 | (posSlot & 1)) << numDirectBits);
                        if (posSlot < Base.kEndPosModelIndex) {
                            state.rep0 += BitTreeDecoder.reverseDecode(m_PosDecoders,
                                    state.rep0 - posSlot - 1, m_RangeDecoder, numDirectBits);
                        } else {
                            state.rep0 += (m_RangeDecoder.decodeDirectBits(
                                    numDirectBits - Base.kNumAlignBits) << Base.kNumAlignBits);
                            state.rep0 += m_PosAlignDecoder.reverseDecode(m_RangeDecoder);
                            if (state.rep0 < 0) {
                                if (state.rep0 == -1) {
                                    break;
                                }
                                return State.ERR;
                            }
                        }
                    } else {
                        state.rep0 = posSlot;
                    }
                }
                if (state.rep0 >= state.nowPos64 || state.rep0 >= m_DictionarySizeCheck) {
                    // m_OutWindow.Flush();
                    return State.ERR;
                }
                m_OutWindow.copyBlock(state.rep0, len);
                state.nowPos64 += len;
                state.prevByte = m_OutWindow.getByte(0);
            }
        }
        m_OutWindow.flush();
        m_OutWindow.releaseBuffer();
        m_RangeDecoder.releaseBuffer();
        return State.DONE;
    }

    public boolean setDecoderProperties(byte[] properties) {
        if (properties.length < 5) {
            return false;
        }
        int val = properties[0] & 0xFF;
        int lc = val % 9;
        int remainder = val / 9;
        int lp = remainder % 5;
        int pb = remainder / 5;
        int dictionarySize = 0;
        for (int i = 0; i < 4; i++) {
            dictionarySize += ((int) (properties[1 + i]) & 0xFF) << (i * 8);
        }
        if (!setLcLpPb(lc, lp, pb)) {
            return false;
        }
        return setDictionarySize(dictionarySize);
    }
}
