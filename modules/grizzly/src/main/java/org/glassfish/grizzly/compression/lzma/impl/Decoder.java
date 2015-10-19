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

package org.glassfish.grizzly.compression.lzma.impl;

import org.glassfish.grizzly.compression.lzma.LZMADecoder;
import org.glassfish.grizzly.compression.lzma.LZMADecoder.LZMAInputState;
import org.glassfish.grizzly.compression.lzma.impl.lz.OutWindow;
import org.glassfish.grizzly.compression.lzma.impl.rangecoder.BitTreeDecoder;
import org.glassfish.grizzly.compression.lzma.impl.rangecoder.RangeDecoder;

import java.io.IOException;
import org.glassfish.grizzly.Buffer;

/**
 * RangeDecoder
 *
 * @author Igor Pavlov
 */
public class Decoder {

    public enum State {
        ERR,
        NEED_MORE_DATA,
        DONE,
        CONTINUE // internal only
    }

    static class LenDecoder {

        final short[] m_Choice = new short[2];
        final BitTreeDecoder[] m_LowCoder = new BitTreeDecoder[Base.kNumPosStatesMax];
        final BitTreeDecoder[] m_MidCoder = new BitTreeDecoder[Base.kNumPosStatesMax];
        final BitTreeDecoder m_HighCoder = new BitTreeDecoder(Base.kNumHighLenBits);
        int m_NumPosStates = 0;

        public void create(int numPosStates) {
            for (; m_NumPosStates < numPosStates; m_NumPosStates++) {
                m_LowCoder[m_NumPosStates] = new BitTreeDecoder(Base.kNumLowLenBits);
                m_MidCoder[m_NumPosStates] = new BitTreeDecoder(Base.kNumMidLenBits);
            }
        }

        public void init() {
            decodeMethodState = 0;

            RangeDecoder.initBitModels(m_Choice);
            for (int posState = 0; posState < m_NumPosStates; posState++) {
                m_LowCoder[posState].init();
                m_MidCoder[posState].init();
            }
            m_HighCoder.init();
        }
        private int decodeMethodState;

        public boolean decode(LZMADecoder.LZMAInputState decoderState,
                RangeDecoder rangeDecoder, int posState) throws IOException {
            do {
                switch (decodeMethodState) {
                    case 0: {
                        if (!rangeDecoder.decodeBit(decoderState, m_Choice, 0)) {
                            return false;
                        }

                        decodeMethodState = decoderState.lastMethodResult == 0
                                ? 1 : 2;
                        continue;
                    }
                    case 1: {
                        if (!m_LowCoder[posState].decode(decoderState, rangeDecoder)) {
                            return false;
                        }

                        // using last result from m_LowCoder[posState].decode(...)
                        decodeMethodState = 5;
                        continue;
                    }
                    case 2: {
                        if (!rangeDecoder.decodeBit(decoderState, m_Choice, 1)) {
                            return false;
                        }
                        decodeMethodState = decoderState.lastMethodResult == 0
                                ? 3 : 4;
                        continue;
                    }
                    case 3: {
                        if (!m_MidCoder[posState].decode(decoderState, rangeDecoder)) {
                            return false;
                        }

                        decoderState.lastMethodResult += Base.kNumLowLenSymbols;

                        decodeMethodState = 5;
                        continue;
                    }
                    case 4: {
                        if (!m_HighCoder.decode(decoderState, rangeDecoder)) {
                            return false;
                        }

                        decoderState.lastMethodResult += Base.kNumLowLenSymbols + Base.kNumMidLenSymbols;
                        decodeMethodState = 5;
                        continue;
                    }
                    case 5: {
                        decodeMethodState = 0;
                        return true;
                    }
                }
            } while (true);
        }
    }

    public static class LiteralDecoder {

        public static class Decoder2 {

            final short[] m_Decoders = new short[0x300];
            int decodeNormalMethodState;
            int decodeWithMatchByteMethodState;
            int symbol;
            int matchBit;
            byte matchByte;
            
            public void init() {
                decodeNormalMethodState = 0;
                decodeWithMatchByteMethodState = 0;
                RangeDecoder.initBitModels(m_Decoders);
            }

            public boolean decodeNormal(LZMADecoder.LZMAInputState decoderState,
                    RangeDecoder rangeDecoder) throws IOException {

                do {
                    switch (decodeNormalMethodState) {
                        case 0:
                        {
                            symbol = 1;
                            decodeNormalMethodState = 1;
                        }
                        case 1:
                        {
                            if (!rangeDecoder.decodeBit(decoderState,
                                    m_Decoders, symbol)) {
                                return false;
                            }

                            symbol = (symbol << 1) | decoderState.lastMethodResult;

                            if (symbol >= 0x100) {
                                decodeNormalMethodState = 0;
                                decoderState.lastMethodResult = symbol;
                                return true;
                            }
                        }
                    }
                } while(true);
            }

            public boolean decodeWithMatchByte(LZMADecoder.LZMAInputState decoderState,
                    RangeDecoder rangeDecoder, byte matchByteParam) throws IOException {

                do {
                    switch (decodeWithMatchByteMethodState) {
                        case 0:
                        {
                            symbol = 1;
                            this.matchByte = matchByteParam;
//                            decodeWithMatchByteMethodState = 1;
                        }
                        case 1:
                        {
                            matchBit = (matchByte >> 7) & 1;
                            matchByte <<= 1;
                            decodeWithMatchByteMethodState = 2;
                        }
                        case 2:
                        {
                            if (!rangeDecoder.decodeBit(decoderState, m_Decoders,
                                    ((1 + matchBit) << 8) + symbol)) {
                                return false;
                            }

                            final int bit = decoderState.lastMethodResult;
                            symbol = (symbol << 1) | bit;
                            if (matchBit == bit) {
                                if (symbol >= 0x100) { // outter while(symbol < 0x100)
                                    decodeWithMatchByteMethodState = 4; // break
                                    continue;
                                }

                                // loop
                                decodeWithMatchByteMethodState = 1;
                                continue;
                            }

                            decodeWithMatchByteMethodState = 3;
                        }
                        case 3:
                        {
                            if (symbol >= 0x100) { // inner while(symbol < 0x100)
                                decodeWithMatchByteMethodState = 4; // break
                                continue;
                            }

                            if (!rangeDecoder.decodeBit(decoderState, m_Decoders,
                                    symbol)) {
                                return false;
                            }

                            symbol = (symbol << 1) | decoderState.lastMethodResult;
                            continue;
                        }

                        case 4:
                        {
                            decodeWithMatchByteMethodState = 0;
                            decoderState.lastMethodResult = symbol;
                            return true;
                        }
                    }
                } while(true);
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
    final OutWindow m_OutWindow = new OutWindow();
    final RangeDecoder m_RangeDecoder = new RangeDecoder();
    final short[] m_IsMatchDecoders = new short[Base.kNumStates << Base.kNumPosStatesBitsMax];
    final short[] m_IsRepDecoders = new short[Base.kNumStates];
    final short[] m_IsRepG0Decoders = new short[Base.kNumStates];
    final short[] m_IsRepG1Decoders = new short[Base.kNumStates];
    final short[] m_IsRepG2Decoders = new short[Base.kNumStates];
    final short[] m_IsRep0LongDecoders = new short[Base.kNumStates << Base.kNumPosStatesBitsMax];
    final BitTreeDecoder[] m_PosSlotDecoder = new BitTreeDecoder[Base.kNumLenToPosStates];
    final short[] m_PosDecoders = new short[Base.kNumFullDistances - Base.kEndPosModelIndex];
    final BitTreeDecoder m_PosAlignDecoder = new BitTreeDecoder(Base.kNumAlignBits);
    final LenDecoder m_LenDecoder = new LenDecoder();
    final LenDecoder m_RepLenDecoder = new LenDecoder();
    final LiteralDecoder m_LiteralDecoder = new LiteralDecoder();
    int m_DictionarySize = -1;
    int m_DictionarySizeCheck = -1;
    int m_PosStateMask;

    public Decoder() {
        for (int i = 0; i < Base.kNumLenToPosStates; i++) {
            m_PosSlotDecoder[i] = new BitTreeDecoder(Base.kNumPosSlotBits);
        }
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
        return setLcLpPb(lc, lp, pb) && setDictionarySize(dictionarySize);
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

    public State code(LZMADecoder.LZMAInputState decoderState, long outSize) throws IOException {
//        Init();
        final Buffer inputBuffer = decoderState.getSrc();
        m_RangeDecoder.initFromState(decoderState);
        m_OutWindow.initFromState(decoderState);
        if (!decoderState.isInitialized()) {
            if (inputBuffer.remaining() < 13) {
                return State.NEED_MORE_DATA;
            }

            decoderState.initialize(inputBuffer);
            init();
        }

//        final RangeDecoder m_RangeDecoder = decoderState.m_RangeDecoder;
//        final OutWindow m_OutWindow = decoderState.m_OutWindow;
//


//        int state = Base.StateInit();
//        int rep0 = 0, rep1 = 0, rep2 = 0, rep3 = 0;
//
//        long nowPos64 = 0;
//        byte prevByte = 0;
        _outter:
        while (true) {
            switch (decoderState.inner1State) {
                case 0: {
                    if (outSize >= 0 && decoderState.nowPos64 >= outSize) {
                        break _outter;
                    }
                    decoderState.inner1State = 1;
                }

                case 1: {
                    decoderState.posState = (int) decoderState.nowPos64 & m_PosStateMask;
                    if (!m_RangeDecoder.decodeBit(decoderState, m_IsMatchDecoders,
                            (decoderState.state << Base.kNumPosStatesBitsMax)
                            + decoderState.posState)) {
                        return State.NEED_MORE_DATA;

                    }

                    final int result = decoderState.lastMethodResult;
                    decoderState.inner1State = result == 0 ? 2 : 3;
                    break;
                }

                case 2: {
                    if (!processState2(decoderState)) {
                        return State.NEED_MORE_DATA;
                    }
                    decoderState.inner1State = 0;
                    break;
                }

                case 3: {
                    final State internalState = processState3(decoderState);
                    if (internalState == State.NEED_MORE_DATA ||
                            internalState == State.ERR) {
                        return internalState;
                    }

                    decoderState.inner1State = 0;
                    
                    if (internalState == State.DONE) {
                        break _outter;
                    }
                }
            }
        }

        m_OutWindow.flush();
        m_OutWindow.releaseBuffer();
        m_RangeDecoder.releaseBuffer();
        return State.DONE;
    }

    private boolean processState2(final LZMADecoder.LZMAInputState decoderState) throws IOException {
        do {
            switch (decoderState.inner2State) {
                case 0: {
                    decoderState.decoder2 = m_LiteralDecoder.getDecoder(
                            (int) decoderState.nowPos64,
                            decoderState.prevByte);
                    decoderState.inner2State = (!Base.stateIsCharState(decoderState.state)) ? 1 : 2;
                    continue;
                }

                case 1: {
                    if (!decoderState.decoder2.decodeWithMatchByte(decoderState,
                            m_RangeDecoder, m_OutWindow.getByte(decoderState.rep0))) {
                        return false;
                    }
                    decoderState.prevByte = (byte) decoderState.lastMethodResult;
                    decoderState.inner2State = 3;
                    continue;
                }
                case 2: {
                    if (!decoderState.decoder2.decodeNormal(decoderState,
                            m_RangeDecoder)) {
                        return false;
                    }
                    
                    decoderState.prevByte = (byte) decoderState.lastMethodResult;
                    decoderState.inner2State = 3;
                }
                case 3: {
//                    if (!decoderState.decoder2.decodeNormal(
//                            decoderState, m_RangeDecoder)) {
//                        return false;
//                    }
//                    decoderState.prevByte = (byte) decoderState.lastMethodResult;
                    m_OutWindow.putByte(decoderState.prevByte);
                    decoderState.state = Base.stateUpdateChar(decoderState.state);
                    decoderState.nowPos64++;

                    decoderState.inner2State = 0;
                    
                    return true;
                }
            }
        } while (true);
    }

    private State processState3(final LZMADecoder.LZMAInputState decoderState)
            throws IOException {
//        int len;

        do {
            switch (decoderState.inner2State) {
                case 0:
                {
                    if (!m_RangeDecoder.decodeBit(decoderState, m_IsRepDecoders,
                            decoderState.state)) {
                        return State.NEED_MORE_DATA;
                    }
                    decoderState.inner2State = decoderState.lastMethodResult == 1 ?
                        1 : 2;
                    continue;
                }

                case 1:
                {
                    if (!processState31(decoderState)) {
                        return State.NEED_MORE_DATA;
                    }

                    decoderState.inner2State = 3;
                    continue;
                }
                case 2:
                {
                    final State internalResult = processState32(decoderState);
                    if (internalResult != State.CONTINUE) {
                        return internalResult;
                    }

                    decoderState.inner2State = 3;
                }
                case 3:
                {
                    if (decoderState.rep0 >= decoderState.nowPos64
                            || decoderState.rep0 >= m_DictionarySizeCheck) {
                        // m_OutWindow.Flush();
                        return State.ERR;
                    }
                    m_OutWindow.copyBlock(decoderState.rep0, decoderState.state3Len);
                    decoderState.nowPos64 += decoderState.state3Len;
                    decoderState.prevByte = m_OutWindow.getByte(0);

                    decoderState.inner2State = 0;
                    return State.CONTINUE;
                }

            }
        } while (true);
    }

    private boolean processState31(final LZMAInputState decoderState)
            throws IOException {
        do {
            switch (decoderState.state31) {
                case 0: {
                    decoderState.state3Len = 0;

                    if (!m_RangeDecoder.decodeBit(decoderState, m_IsRepG0Decoders,
                            decoderState.state)) {
                        return false;
                    }

                    decoderState.state31 = decoderState.lastMethodResult == 0 ? 1 : 2;
                    continue;
                }

                case 1: {
                    if (!m_RangeDecoder.decodeBit(decoderState, m_IsRep0LongDecoders,
                            (decoderState.state << Base.kNumPosStatesBitsMax)
                            + decoderState.posState)) {
                        return false;
                    }

                    if (decoderState.lastMethodResult == 0) {
                        decoderState.state = Base.stateUpdateShortRep(decoderState.state);
                        decoderState.state3Len = 1;
                    }

                    decoderState.state31 = 3;
                    continue;
                }

                case 2: {
                    if (!processState311(decoderState)) {
                        return false;
                    }

                    decoderState.state31 = 3;
                }

                case 3: {
                    if (decoderState.state3Len != 0) {
                        decoderState.state31 = 0;
                        return true;
                    }
                    decoderState.state31 = 4;
                }
                case 4: {
                    if (!m_RepLenDecoder.decode(decoderState, m_RangeDecoder,
                            decoderState.posState)) {
                        return false;
                    }

                    decoderState.state3Len = decoderState.lastMethodResult + Base.kMatchMinLen;
                    decoderState.state = Base.stateUpdateRep(decoderState.state);

                    decoderState.state31 = 0;
                    return true;
                }
            }
        } while (true);
    }

    private boolean processState311(final LZMAInputState decoderState)
            throws IOException {
        do {
            switch(decoderState.state311) {
                case 0: {
                    if (!m_RangeDecoder.decodeBit(decoderState, m_IsRepG1Decoders,
                            decoderState.state)) {
                        return false;
                    }

                    decoderState.state311 = decoderState.lastMethodResult == 0 ? 1 : 2;
                    continue;
                }

                case 1: {
                    decoderState.state311Distance = decoderState.rep1;
                    decoderState.state311 = 3;
                    continue;
                }

                case 2: {
                    if (!m_RangeDecoder.decodeBit(decoderState,
                            m_IsRepG2Decoders, decoderState.state)) {
                        return false;
                    }

                    if (decoderState.lastMethodResult == 0) {
                        decoderState.state311Distance = decoderState.rep2;
                    } else {
                        decoderState.state311Distance = decoderState.rep3;
                        decoderState.rep3 = decoderState.rep2;
                    }
                    
                    decoderState.rep2 = decoderState.rep1;
                }

                case 3: {
                    decoderState.rep1 = decoderState.rep0;
                    decoderState.rep0 = decoderState.state311Distance;
                    decoderState.state311 = 0;
                    return true;
                }
            }
        } while (true);
    }

    private State processState32(final LZMAInputState decoderState)
            throws IOException {
        do {
            switch (decoderState.state32) {
                case 0:
                {
                    decoderState.rep3 = decoderState.rep2;
                    decoderState.rep2 = decoderState.rep1;
                    decoderState.rep1 = decoderState.rep0;
                    decoderState.state32 = 1;
                }
                case 1:
                {
                    if (!m_LenDecoder.decode(decoderState, m_RangeDecoder,
                            decoderState.posState)) {
                        return State.NEED_MORE_DATA;
                    }

                    decoderState.state3Len = Base.kMatchMinLen + decoderState.lastMethodResult;
                    decoderState.state = Base.stateUpdateMatch(decoderState.state);

                    decoderState.state32 = 2;
                }
                case 2:
                {
                    if (!m_PosSlotDecoder[Base.getLenToPosState(decoderState.state3Len)].decode(
                            decoderState, m_RangeDecoder)) {
                        return State.NEED_MORE_DATA;
                    }

                    decoderState.state32PosSlot = decoderState.lastMethodResult;
                    decoderState.state32 = (decoderState.state32PosSlot >= Base.kStartPosModelIndex) ? 3 : 4;
                    continue;
                }
                case 3:
                {
                    final State localState = processState321(decoderState);
                    if (localState == State.CONTINUE) {
                        decoderState.state32 = 0;
                    }
                    
                    return localState;
                }
                case 4:
                {
                    decoderState.rep0 = decoderState.state32PosSlot;
                    decoderState.state32 = 0;
                    return State.CONTINUE;
                }
            }
        } while (true);
    }

    private State processState321(final LZMAInputState decoderState)
            throws IOException {
        do {
            switch (decoderState.state321) {
                case 0: {
                    decoderState.state321NumDirectBits = (decoderState.state32PosSlot >> 1) - 1;
                    decoderState.rep0 = ((2 | (decoderState.state32PosSlot & 1)) << decoderState.state321NumDirectBits);
                    decoderState.state321 = (decoderState.state32PosSlot < Base.kEndPosModelIndex) ? 1 : 2;
                    continue;
                }
                case 1: {
                    if (!BitTreeDecoder.reverseDecode(decoderState, m_PosDecoders,
                            decoderState.rep0 - decoderState.state32PosSlot - 1, m_RangeDecoder, decoderState.state321NumDirectBits)) {
                        return State.NEED_MORE_DATA;
                    }

                    decoderState.rep0 += decoderState.lastMethodResult;
                    decoderState.state321 = 0;
                    return State.CONTINUE;
                }

                case 2: {
                    if (!m_RangeDecoder.decodeDirectBits(decoderState,
                            decoderState.state321NumDirectBits - Base.kNumAlignBits)) {
                        return State.NEED_MORE_DATA;
                    }

                    decoderState.rep0 += (decoderState.lastMethodResult << Base.kNumAlignBits);
                    decoderState.state321 = 3;
                    continue;
                }
                case 3: {
                    if (!m_PosAlignDecoder.reverseDecode(decoderState, m_RangeDecoder)) {
                        return State.NEED_MORE_DATA;
                    }

                    decoderState.rep0 += decoderState.lastMethodResult;

                    decoderState.state321 = 0;

                    if (decoderState.rep0 < 0) {
                        if (decoderState.rep0 == -1) {
                            return State.DONE;
                        }

                        return State.ERR;
                    }
                    return State.CONTINUE;
                }
            }
        } while (true);
    }
}
