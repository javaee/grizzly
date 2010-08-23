/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2009-2010 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.grizzly.lzma.compression.lzma;

import com.sun.grizzly.lzma.compression.rangecoder.BitTreeDecoder;
import com.sun.grizzly.lzma.compression.lz.OutWindow;

import java.io.IOException;

/**
 * Decoder
 *
 * @author Igor Pavlov
 */
public class Decoder {

    class LenDecoder {

        short[] m_Choice = new short[2];
        BitTreeDecoder[] m_LowCoder = new BitTreeDecoder[Base.kNumPosStatesMax];
        BitTreeDecoder[] m_MidCoder = new BitTreeDecoder[Base.kNumPosStatesMax];
        BitTreeDecoder m_HighCoder = new BitTreeDecoder(Base.kNumHighLenBits);
        int m_NumPosStates = 0;

        public void Create(int numPosStates) {
            for (; m_NumPosStates < numPosStates; m_NumPosStates++) {
                m_LowCoder[m_NumPosStates] = new BitTreeDecoder(Base.kNumLowLenBits);
                m_MidCoder[m_NumPosStates] = new BitTreeDecoder(Base.kNumMidLenBits);
            }
        }

        public void Init() {
            com.sun.grizzly.lzma.compression.rangecoder.Decoder.InitBitModels(m_Choice);
            for (int posState = 0; posState < m_NumPosStates; posState++) {
                m_LowCoder[posState].Init();
                m_MidCoder[posState].Init();
            }
            m_HighCoder.Init();
        }

        public int Decode(com.sun.grizzly.lzma.compression.rangecoder.Decoder rangeDecoder, int posState) throws IOException {
            if (rangeDecoder.DecodeBit(m_Choice, 0) == 0) {
                return m_LowCoder[posState].Decode(rangeDecoder);
            }
            int symbol = Base.kNumLowLenSymbols;
            if (rangeDecoder.DecodeBit(m_Choice, 1) == 0) {
                symbol += m_MidCoder[posState].Decode(rangeDecoder);
            } else {
                symbol += Base.kNumMidLenSymbols + m_HighCoder.Decode(rangeDecoder);
            }
            return symbol;
        }
    }

    class LiteralDecoder {

        class Decoder2 {

            short[] m_Decoders = new short[0x300];

            public void Init() {
                com.sun.grizzly.lzma.compression.rangecoder.Decoder.InitBitModels(m_Decoders);
            }

            public byte DecodeNormal(com.sun.grizzly.lzma.compression.rangecoder.Decoder rangeDecoder) throws IOException {
                int symbol = 1;
                do {
                    symbol = (symbol << 1) | rangeDecoder.DecodeBit(m_Decoders, symbol);
                } while (symbol < 0x100);
                return (byte) symbol;
            }

            public byte DecodeWithMatchByte(com.sun.grizzly.lzma.compression.rangecoder.Decoder rangeDecoder, byte matchByte) throws IOException {
                int symbol = 1;
                do {
                    int matchBit = (matchByte >> 7) & 1;
                    matchByte <<= 1;
                    int bit = rangeDecoder.DecodeBit(m_Decoders, ((1 + matchBit) << 8) + symbol);
                    symbol = (symbol << 1) | bit;
                    if (matchBit != bit) {
                        while (symbol < 0x100) {
                            symbol = (symbol << 1) | rangeDecoder.DecodeBit(m_Decoders, symbol);
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

        public void Create(int numPosBits, int numPrevBits) {
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

        public void Init() {
            int numStates = 1 << (m_NumPrevBits + m_NumPosBits);
            for (int i = 0; i < numStates; i++) {
                m_Coders[i].Init();
            }
        }

        Decoder2 GetDecoder(int pos, byte prevByte) {
            return m_Coders[((pos & m_PosMask) << m_NumPrevBits) + ((prevByte & 0xFF) >>> (8 - m_NumPrevBits))];
        }
    }
    OutWindow m_OutWindow = new OutWindow();
    com.sun.grizzly.lzma.compression.rangecoder.Decoder m_RangeDecoder = new com.sun.grizzly.lzma.compression.rangecoder.Decoder();
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

    boolean SetDictionarySize(int dictionarySize) {
        if (dictionarySize < 0) {
            return false;
        }
        if (m_DictionarySize != dictionarySize) {
            m_DictionarySize = dictionarySize;
            m_DictionarySizeCheck = Math.max(m_DictionarySize, 1);
            m_OutWindow.Create(Math.max(m_DictionarySizeCheck, (1 << 12)));
        }
        return true;
    }

    boolean SetLcLpPb(int lc, int lp, int pb) {
        if (lc > Base.kNumLitContextBitsMax || lp > 4 || pb > Base.kNumPosStatesBitsMax) {
            return false;
        }
        m_LiteralDecoder.Create(lp, lc);
        int numPosStates = 1 << pb;
        m_LenDecoder.Create(numPosStates);
        m_RepLenDecoder.Create(numPosStates);
        m_PosStateMask = numPosStates - 1;
        return true;
    }

    void Init() throws IOException {
        m_OutWindow.Init(false);

        com.sun.grizzly.lzma.compression.rangecoder.Decoder.InitBitModels(m_IsMatchDecoders);
        com.sun.grizzly.lzma.compression.rangecoder.Decoder.InitBitModels(m_IsRep0LongDecoders);
        com.sun.grizzly.lzma.compression.rangecoder.Decoder.InitBitModels(m_IsRepDecoders);
        com.sun.grizzly.lzma.compression.rangecoder.Decoder.InitBitModels(m_IsRepG0Decoders);
        com.sun.grizzly.lzma.compression.rangecoder.Decoder.InitBitModels(m_IsRepG1Decoders);
        com.sun.grizzly.lzma.compression.rangecoder.Decoder.InitBitModels(m_IsRepG2Decoders);
        com.sun.grizzly.lzma.compression.rangecoder.Decoder.InitBitModels(m_PosDecoders);

        m_LiteralDecoder.Init();
        int i;
        for (i = 0; i < Base.kNumLenToPosStates; i++) {
            m_PosSlotDecoder[i].Init();
        }
        m_LenDecoder.Init();
        m_RepLenDecoder.Init();
        m_PosAlignDecoder.Init();
        m_RangeDecoder.Init();
    }

    public boolean Code(java.io.InputStream inStream, java.io.OutputStream outStream,
            long outSize) throws IOException {
        m_RangeDecoder.SetStream(inStream);
        m_OutWindow.SetStream(outStream);
        Init();

        int state = Base.StateInit();
        int rep0 = 0, rep1 = 0, rep2 = 0, rep3 = 0;

        long nowPos64 = 0;
        byte prevByte = 0;
        while (outSize < 0 || nowPos64 < outSize) {
            int posState = (int) nowPos64 & m_PosStateMask;
            if (m_RangeDecoder.DecodeBit(m_IsMatchDecoders, (state << Base.kNumPosStatesBitsMax) + posState) == 0) {
                LiteralDecoder.Decoder2 decoder2 = m_LiteralDecoder.GetDecoder((int) nowPos64, prevByte);
                if (!Base.StateIsCharState(state)) {
                    prevByte = decoder2.DecodeWithMatchByte(m_RangeDecoder, m_OutWindow.GetByte(rep0));
                } else {
                    prevByte = decoder2.DecodeNormal(m_RangeDecoder);
                }
                m_OutWindow.PutByte(prevByte);
                state = Base.StateUpdateChar(state);
                nowPos64++;
            } else {
                int len;
                if (m_RangeDecoder.DecodeBit(m_IsRepDecoders, state) == 1) {
                    len = 0;
                    if (m_RangeDecoder.DecodeBit(m_IsRepG0Decoders, state) == 0) {
                        if (m_RangeDecoder.DecodeBit(m_IsRep0LongDecoders, (state << Base.kNumPosStatesBitsMax) + posState) == 0) {
                            state = Base.StateUpdateShortRep(state);
                            len = 1;
                        }
                    } else {
                        int distance;
                        if (m_RangeDecoder.DecodeBit(m_IsRepG1Decoders, state) == 0) {
                            distance = rep1;
                        } else {
                            if (m_RangeDecoder.DecodeBit(m_IsRepG2Decoders, state) == 0) {
                                distance = rep2;
                            } else {
                                distance = rep3;
                                rep3 = rep2;
                            }
                            rep2 = rep1;
                        }
                        rep1 = rep0;
                        rep0 = distance;
                    }
                    if (len == 0) {
                        len = m_RepLenDecoder.Decode(m_RangeDecoder, posState) + Base.kMatchMinLen;
                        state = Base.StateUpdateRep(state);
                    }
                } else {
                    rep3 = rep2;
                    rep2 = rep1;
                    rep1 = rep0;
                    len = Base.kMatchMinLen + m_LenDecoder.Decode(m_RangeDecoder, posState);
                    state = Base.StateUpdateMatch(state);
                    int posSlot = m_PosSlotDecoder[Base.GetLenToPosState(len)].Decode(m_RangeDecoder);
                    if (posSlot >= Base.kStartPosModelIndex) {
                        int numDirectBits = (posSlot >> 1) - 1;
                        rep0 = ((2 | (posSlot & 1)) << numDirectBits);
                        if (posSlot < Base.kEndPosModelIndex) {
                            rep0 += BitTreeDecoder.ReverseDecode(m_PosDecoders,
                                    rep0 - posSlot - 1, m_RangeDecoder, numDirectBits);
                        } else {
                            rep0 += (m_RangeDecoder.DecodeDirectBits(
                                    numDirectBits - Base.kNumAlignBits) << Base.kNumAlignBits);
                            rep0 += m_PosAlignDecoder.ReverseDecode(m_RangeDecoder);
                            if (rep0 < 0) {
                                if (rep0 == -1) {
                                    break;
                                }
                                return false;
                            }
                        }
                    } else {
                        rep0 = posSlot;
                    }
                }
                if (rep0 >= nowPos64 || rep0 >= m_DictionarySizeCheck) {
                    // m_OutWindow.Flush();
                    return false;
                }
                m_OutWindow.CopyBlock(rep0, len);
                nowPos64 += len;
                prevByte = m_OutWindow.GetByte(0);
            }
        }
        m_OutWindow.Flush();
        m_OutWindow.ReleaseStream();
        m_RangeDecoder.ReleaseStream();
        return true;
    }

    public boolean SetDecoderProperties(byte[] properties) {
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
        if (!SetLcLpPb(lc, lp, pb)) {
            return false;
        }
        return SetDictionarySize(dictionarySize);
    }
}
