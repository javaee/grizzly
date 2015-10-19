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


import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.compression.lzma.LZMAEncoder;
import org.glassfish.grizzly.compression.lzma.impl.lz.BinTree;
import org.glassfish.grizzly.compression.lzma.impl.rangecoder.BitTreeEncoder;
import org.glassfish.grizzly.compression.lzma.impl.rangecoder.RangeEncoder;
import org.glassfish.grizzly.memory.MemoryManager;

import java.io.IOException;

/**
 * RangeEncoder
 *
 * @author Igor Pavlov
 */
public class Encoder {

    public static final int EMatchFinderTypeBT2 = 0;
    public static final int EMatchFinderTypeBT4 = 1;
    static final int kIfinityPrice = 0xFFFFFFF;
    static final byte[] g_FastPos = new byte[1 << 11];

    static {
        int kFastSlots = 22;
        int c = 2;
        g_FastPos[0] = 0;
        g_FastPos[1] = 1;
        for (int slotFast = 2; slotFast < kFastSlots; slotFast++) {
            int k = (1 << ((slotFast >> 1) - 1));
            for (int j = 0; j < k; j++, c++) {
                g_FastPos[c] = (byte) slotFast;
            }
        }
    }

    static int getPosSlot(int pos) {
        if (pos < (1 << 11)) {
            return g_FastPos[pos];
        }
        if (pos < (1 << 21)) {
            return (g_FastPos[pos >> 10] + 20);
        }
        return (g_FastPos[pos >> 20] + 40);
    }

    static int getPosSlot2(int pos) {
        if (pos < (1 << 17)) {
            return (g_FastPos[pos >> 6] + 12);
        }
        if (pos < (1 << 27)) {
            return (g_FastPos[pos >> 16] + 32);
        }
        return (g_FastPos[pos >> 26] + 52);
    }
    int _state = Base.stateInit();
    byte _previousByte;
    final int[] _repDistances = new int[Base.kNumRepDistances];

    void baseInit() {
        _state = Base.stateInit();
        _previousByte = 0;
        for (int i = 0; i < Base.kNumRepDistances; i++) {
            _repDistances[i] = 0;
        }
    }
    static final int kDefaultDictionaryLogSize = 22;
    static final int kNumFastBytesDefault = 0x20;

    static class LiteralEncoder {

        static class Encoder2 {

            final short[] m_Encoders = new short[0x300];

            public void init() {
                RangeEncoder.initBitModels(m_Encoders);
            }

            public void encode(RangeEncoder rangeEncoder, byte symbol) throws IOException {
                int context = 1;
                for (int i = 7; i >= 0; i--) {
                    int bit = ((symbol >> i) & 1);
                    rangeEncoder.encode(m_Encoders, context, bit);
                    context = (context << 1) | bit;
                }
            }

            public void encodeMatched(RangeEncoder rangeEncoder, byte matchByte, byte symbol) throws IOException {
                int context = 1;
                boolean same = true;
                for (int i = 7; i >= 0; i--) {
                    int bit = ((symbol >> i) & 1);
                    int state = context;
                    if (same) {
                        int matchBit = ((matchByte >> i) & 1);
                        state += ((1 + matchBit) << 8);
                        same = (matchBit == bit);
                    }
                    rangeEncoder.encode(m_Encoders, state, bit);
                    context = (context << 1) | bit;
                }
            }

            public int getPrice(boolean matchMode, byte matchByte, byte symbol) {
                int price = 0;
                int context = 1;
                int i = 7;
                if (matchMode) {
                    for (; i >= 0; i--) {
                        int matchBit = (matchByte >> i) & 1;
                        int bit = (symbol >> i) & 1;
                        price += RangeEncoder.getPrice(m_Encoders[((1 + matchBit) << 8) + context], bit);
                        context = (context << 1) | bit;
                        if (matchBit != bit) {
                            i--;
                            break;
                        }
                    }
                }
                for (; i >= 0; i--) {
                    int bit = (symbol >> i) & 1;
                    price += RangeEncoder.getPrice(m_Encoders[context], bit);
                    context = (context << 1) | bit;
                }
                return price;
            }
        }
        Encoder2[] m_Coders;
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
            m_Coders = new Encoder2[numStates];
            for (int i = 0; i < numStates; i++) {
                m_Coders[i] = new Encoder2();
            }
        }

        public void init() {
            int numStates = 1 << (m_NumPrevBits + m_NumPosBits);
            for (int i = 0; i < numStates; i++) {
                m_Coders[i].init();
            }
        }

        public Encoder2 getSubCoder(int pos, byte prevByte) {
            return m_Coders[((pos & m_PosMask) << m_NumPrevBits) + ((prevByte & 0xFF) >>> (8 - m_NumPrevBits))];
        }
    }

    static class LenEncoder {

        final short[] _choice = new short[2];
        final BitTreeEncoder[] _lowCoder = new BitTreeEncoder[Base.kNumPosStatesEncodingMax];
        final BitTreeEncoder[] _midCoder = new BitTreeEncoder[Base.kNumPosStatesEncodingMax];
        final BitTreeEncoder _highCoder = new BitTreeEncoder(Base.kNumHighLenBits);

        public LenEncoder() {
            for (int posState = 0; posState < Base.kNumPosStatesEncodingMax; posState++) {
                _lowCoder[posState] = new BitTreeEncoder(Base.kNumLowLenBits);
                _midCoder[posState] = new BitTreeEncoder(Base.kNumMidLenBits);
            }
        }

        public void init(int numPosStates) {
            RangeEncoder.initBitModels(_choice);

            for (int posState = 0; posState < numPosStates; posState++) {
                _lowCoder[posState].init();
                _midCoder[posState].init();
            }
            _highCoder.init();
        }

        public void encode(RangeEncoder rangeEncoder, int symbol, int posState) throws IOException {
            if (symbol < Base.kNumLowLenSymbols) {
                rangeEncoder.encode(_choice, 0, 0);
                _lowCoder[posState].encode(rangeEncoder, symbol);
            } else {
                symbol -= Base.kNumLowLenSymbols;
                rangeEncoder.encode(_choice, 0, 1);
                if (symbol < Base.kNumMidLenSymbols) {
                    rangeEncoder.encode(_choice, 1, 0);
                    _midCoder[posState].encode(rangeEncoder, symbol);
                } else {
                    rangeEncoder.encode(_choice, 1, 1);
                    _highCoder.encode(rangeEncoder, symbol - Base.kNumMidLenSymbols);
                }
            }
        }

        public void setPrices(int posState, int numSymbols, int[] prices, int st) {
            int a0 = RangeEncoder.getPrice0(_choice[0]);
            int a1 = RangeEncoder.getPrice1(_choice[0]);
            int b0 = a1 + RangeEncoder.getPrice0(_choice[1]);
            int b1 = a1 + RangeEncoder.getPrice1(_choice[1]);
            int i;
            for (i = 0; i < Base.kNumLowLenSymbols; i++) {
                if (i >= numSymbols) {
                    return;
                }
                prices[st + i] = a0 + _lowCoder[posState].getPrice(i);
            }
            for (; i < Base.kNumLowLenSymbols + Base.kNumMidLenSymbols; i++) {
                if (i >= numSymbols) {
                    return;
                }
                prices[st + i] = b0 + _midCoder[posState].getPrice(i - Base.kNumLowLenSymbols);
            }
            for (; i < numSymbols; i++) {
                prices[st + i] = b1 + _highCoder.getPrice(i - Base.kNumLowLenSymbols - Base.kNumMidLenSymbols);
            }
        }
    }

    public static final int kNumLenSpecSymbols = Base.kNumLowLenSymbols + Base.kNumMidLenSymbols;

    static class LenPriceTableEncoder extends LenEncoder {

        final int[] _prices = new int[Base.kNumLenSymbols << Base.kNumPosStatesBitsEncodingMax];
        int _tableSize;
        final int[] _counters = new int[Base.kNumPosStatesEncodingMax];

        public void setTableSize(int tableSize) {
            _tableSize = tableSize;
        }

        public int getPrice(int symbol, int posState) {
            return _prices[posState * Base.kNumLenSymbols + symbol];
        }

        void updateTable(int posState) {
            setPrices(posState, _tableSize, _prices, posState * Base.kNumLenSymbols);
            _counters[posState] = _tableSize;
        }

        public void updateTables(int numPosStates) {
            for (int posState = 0; posState < numPosStates; posState++) {
                updateTable(posState);
            }
        }

        public void encode(RangeEncoder rangeEncoder, int symbol, int posState) throws IOException {
            super.encode(rangeEncoder, symbol, posState);
            if (--_counters[posState] == 0) {
                updateTable(posState);
            }
        }
    }
    static final int kNumOpts = 1 << 12;

    static class Optimal {

        public int State;
        public boolean Prev1IsChar;
        public boolean Prev2;
        public int PosPrev2;
        public int BackPrev2;
        public int Price;
        public int PosPrev;
        public int BackPrev;
        public int Backs0;
        public int Backs1;
        public int Backs2;
        public int Backs3;

        public void makeAsChar() {
            BackPrev = -1;
            Prev1IsChar = false;
        }

        public void makeAsShortRep() {
            BackPrev = 0;
            Prev1IsChar = false;
        }

        public boolean isShortRep() {
            return (BackPrev == 0);
        }
    }

    final Optimal[] _optimum = new Optimal[kNumOpts];
    BinTree _matchFinder = null;
    final RangeEncoder _rangeEncoder = new RangeEncoder();
    final short[] _isMatch = new short[Base.kNumStates << Base.kNumPosStatesBitsMax];
    final short[] _isRep = new short[Base.kNumStates];
    final short[] _isRepG0 = new short[Base.kNumStates];
    final short[] _isRepG1 = new short[Base.kNumStates];
    final short[] _isRepG2 = new short[Base.kNumStates];
    final short[] _isRep0Long = new short[Base.kNumStates << Base.kNumPosStatesBitsMax];
    final BitTreeEncoder[] _posSlotEncoder = new BitTreeEncoder[Base.kNumLenToPosStates]; // kNumPosSlotBits
    final short[] _posEncoders = new short[Base.kNumFullDistances - Base.kEndPosModelIndex];
    final BitTreeEncoder _posAlignEncoder = new BitTreeEncoder(Base.kNumAlignBits);
    final LenPriceTableEncoder _lenEncoder = new LenPriceTableEncoder();
    final LenPriceTableEncoder _repMatchLenEncoder = new LenPriceTableEncoder();
    final LiteralEncoder _literalEncoder = new LiteralEncoder();
    final int[] _matchDistances = new int[Base.kMatchMaxLen * 2 + 2];
    int _numFastBytes = kNumFastBytesDefault;
    int _longestMatchLength;
    int _numDistancePairs;
    int _additionalOffset;
    int _optimumEndIndex;
    int _optimumCurrentIndex;
    boolean _longestMatchWasFound;
    final int[] _posSlotPrices = new int[1 << (Base.kNumPosSlotBits + Base.kNumLenToPosStatesBits)];
    final int[] _distancesPrices = new int[Base.kNumFullDistances << Base.kNumLenToPosStatesBits];
    final int[] _alignPrices = new int[Base.kAlignTableSize];
    int _alignPriceCount;
    int _distTableSize = (kDefaultDictionaryLogSize * 2);
    int _posStateBits = 2;
    int _posStateMask = (4 - 1);
    int _numLiteralPosStateBits = 0;
    int _numLiteralContextBits = 3;
    int _dictionarySize = (1 << kDefaultDictionaryLogSize);
    int _dictionarySizePrev = -1;
    int _numFastBytesPrev = -1;
    long nowPos64;
    boolean _finished;
    Buffer _src;
    int _matchFinderType = EMatchFinderTypeBT4;
    boolean _writeEndMark = false;
    boolean _needReleaseMFStream = false;

    void create() {
        if (_matchFinder == null) {
            BinTree bt = new BinTree();
            int numHashBytes = 4;
            if (_matchFinderType == EMatchFinderTypeBT2) {
                numHashBytes = 2;
            }
            bt.setType(numHashBytes);
            _matchFinder = bt;
        }
        _literalEncoder.create(_numLiteralPosStateBits, _numLiteralContextBits);

        if (_dictionarySize == _dictionarySizePrev && _numFastBytesPrev == _numFastBytes) {
            return;
        }
        _matchFinder.create(_dictionarySize, kNumOpts, _numFastBytes, Base.kMatchMaxLen + 1);
        _dictionarySizePrev = _dictionarySize;
        _numFastBytesPrev = _numFastBytes;
    }

    public Encoder() {
        for (int i = 0; i < kNumOpts; i++) {
            _optimum[i] = new Optimal();
        }
        for (int i = 0; i < Base.kNumLenToPosStates; i++) {
            _posSlotEncoder[i] = new BitTreeEncoder(Base.kNumPosSlotBits);
        }
    }

    void setWriteEndMarkerMode(boolean writeEndMarker) {
        _writeEndMark = writeEndMarker;
    }

    void init() {
        baseInit();
        _rangeEncoder.init();

        RangeEncoder.initBitModels(_isMatch);
        RangeEncoder.initBitModels(_isRep0Long);
        RangeEncoder.initBitModels(_isRep);
        RangeEncoder.initBitModels(_isRepG0);
        RangeEncoder.initBitModels(_isRepG1);
        RangeEncoder.initBitModels(_isRepG2);
        RangeEncoder.initBitModels(_posEncoders);







        _literalEncoder.init();
        for (int i = 0; i < Base.kNumLenToPosStates; i++) {
            _posSlotEncoder[i].init();
        }



        _lenEncoder.init(1 << _posStateBits);
        _repMatchLenEncoder.init(1 << _posStateBits);

        _posAlignEncoder.init();

        _longestMatchWasFound = false;
        _optimumEndIndex = 0;
        _optimumCurrentIndex = 0;
        _additionalOffset = 0;
    }

    int readMatchDistances() throws java.io.IOException {
        int lenRes = 0;
        _numDistancePairs = _matchFinder.getMatches(_matchDistances);
        if (_numDistancePairs > 0) {
            lenRes = _matchDistances[_numDistancePairs - 2];
            if (lenRes == _numFastBytes) {
                lenRes += _matchFinder.getMatchLen(lenRes - 1, _matchDistances[_numDistancePairs - 1],
                        Base.kMatchMaxLen - lenRes);
            }
        }
        _additionalOffset++;
        return lenRes;
    }

    void movePos(int num) throws java.io.IOException {
        if (num > 0) {
            _matchFinder.skip(num);
            _additionalOffset += num;
        }
    }

    int getRepLen1Price(int state, int posState) {
        return RangeEncoder.getPrice0(_isRepG0[state]) +
                RangeEncoder.getPrice0(_isRep0Long[(state << Base.kNumPosStatesBitsMax) + posState]);
    }

    int getPureRepPrice(int repIndex, int state, int posState) {
        int price;
        if (repIndex == 0) {
            price = RangeEncoder.getPrice0(_isRepG0[state]);
            price += RangeEncoder.getPrice1(_isRep0Long[(state << Base.kNumPosStatesBitsMax) + posState]);
        } else {
            price = RangeEncoder.getPrice1(_isRepG0[state]);
            if (repIndex == 1) {
                price += RangeEncoder.getPrice0(_isRepG1[state]);
            } else {
                price += RangeEncoder.getPrice1(_isRepG1[state]);
                price += RangeEncoder.getPrice(_isRepG2[state], repIndex - 2);
            }
        }
        return price;
    }

    int getRepPrice(int repIndex, int len, int state, int posState) {
        int price = _repMatchLenEncoder.getPrice(len - Base.kMatchMinLen, posState);
        return price + getPureRepPrice(repIndex, state, posState);
    }

    int getPosLenPrice(int pos, int len, int posState) {
        int price;
        int lenToPosState = Base.getLenToPosState(len);
        if (pos < Base.kNumFullDistances) {
            price = _distancesPrices[(lenToPosState * Base.kNumFullDistances) + pos];
        } else {
            price = _posSlotPrices[(lenToPosState << Base.kNumPosSlotBits) + getPosSlot2(pos)] +
                    _alignPrices[pos & Base.kAlignMask];
        }
        return price + _lenEncoder.getPrice(len - Base.kMatchMinLen, posState);
    }

    int backward(int cur) {
        _optimumEndIndex = cur;
        int posMem = _optimum[cur].PosPrev;
        int backMem = _optimum[cur].BackPrev;
        do {
            if (_optimum[cur].Prev1IsChar) {
                _optimum[posMem].makeAsChar();
                _optimum[posMem].PosPrev = posMem - 1;
                if (_optimum[cur].Prev2) {
                    _optimum[posMem - 1].Prev1IsChar = false;
                    _optimum[posMem - 1].PosPrev = _optimum[cur].PosPrev2;
                    _optimum[posMem - 1].BackPrev = _optimum[cur].BackPrev2;
                }
            }
            int posPrev = posMem;
            int backCur = backMem;

            backMem = _optimum[posPrev].BackPrev;
            posMem = _optimum[posPrev].PosPrev;

            _optimum[posPrev].BackPrev = backCur;
            _optimum[posPrev].PosPrev = cur;
            cur = posPrev;
        } while (cur > 0);
        backRes = _optimum[0].BackPrev;
        _optimumCurrentIndex = _optimum[0].PosPrev;
        return _optimumCurrentIndex;
    }
    final int[] reps = new int[Base.kNumRepDistances];
    final int[] repLens = new int[Base.kNumRepDistances];
    int backRes;

    int getOptimum(int position) throws IOException {
        if (_optimumEndIndex != _optimumCurrentIndex) {
            int lenRes = _optimum[_optimumCurrentIndex].PosPrev - _optimumCurrentIndex;
            backRes = _optimum[_optimumCurrentIndex].BackPrev;
            _optimumCurrentIndex = _optimum[_optimumCurrentIndex].PosPrev;
            return lenRes;
        }
        _optimumCurrentIndex = _optimumEndIndex = 0;

        int lenMain, numDistancePairs;
        if (!_longestMatchWasFound) {
            lenMain = readMatchDistances();
        } else {
            lenMain = _longestMatchLength;
            _longestMatchWasFound = false;
        }
        numDistancePairs = _numDistancePairs;

        int numAvailableBytes = _matchFinder.getNumAvailableBytes() + 1;
        if (numAvailableBytes < 2) {
            backRes = -1;
            return 1;
        }
        if (numAvailableBytes > Base.kMatchMaxLen) {
            numAvailableBytes = Base.kMatchMaxLen;
        }

        int repMaxIndex = 0;
        int i;
        for (i = 0; i < Base.kNumRepDistances; i++) {
            reps[i] = _repDistances[i];
            repLens[i] = _matchFinder.getMatchLen(0 - 1, reps[i], Base.kMatchMaxLen);
            if (repLens[i] > repLens[repMaxIndex]) {
                repMaxIndex = i;
            }
        }
        if (repLens[repMaxIndex] >= _numFastBytes) {
            backRes = repMaxIndex;
            int lenRes = repLens[repMaxIndex];
            movePos(lenRes - 1);
            return lenRes;
        }

        if (lenMain >= _numFastBytes) {
            backRes = _matchDistances[numDistancePairs - 1] + Base.kNumRepDistances;
            movePos(lenMain - 1);
            return lenMain;
        }

        byte currentByte = _matchFinder.getIndexByte(0 - 1);
        byte matchByte = _matchFinder.getIndexByte(0 - _repDistances[0] - 1 - 1);

        if (lenMain < 2 && currentByte != matchByte && repLens[repMaxIndex] < 2) {
            backRes = -1;
            return 1;
        }

        _optimum[0].State = _state;

        int posState = (position & _posStateMask);

        _optimum[1].Price = RangeEncoder.getPrice0(_isMatch[(_state << Base.kNumPosStatesBitsMax) + posState]) +
                _literalEncoder.getSubCoder(position, _previousByte).getPrice(!Base.stateIsCharState(_state), matchByte, currentByte);
        _optimum[1].makeAsChar();

        int matchPrice = RangeEncoder.getPrice1(_isMatch[(_state << Base.kNumPosStatesBitsMax) + posState]);
        int repMatchPrice = matchPrice + RangeEncoder.getPrice1(_isRep[_state]);

        if (matchByte == currentByte) {
            int shortRepPrice = repMatchPrice + getRepLen1Price(_state, posState);
            if (shortRepPrice < _optimum[1].Price) {
                _optimum[1].Price = shortRepPrice;
                _optimum[1].makeAsShortRep();
            }
        }

        int lenEnd = ((lenMain >= repLens[repMaxIndex]) ? lenMain : repLens[repMaxIndex]);

        if (lenEnd < 2) {
            backRes = _optimum[1].BackPrev;
            return 1;
        }

        _optimum[1].PosPrev = 0;

        _optimum[0].Backs0 = reps[0];
        _optimum[0].Backs1 = reps[1];
        _optimum[0].Backs2 = reps[2];
        _optimum[0].Backs3 = reps[3];

        int len = lenEnd;
        do {
            _optimum[len--].Price = kIfinityPrice;
        } while (len >= 2);

        for (i = 0; i < Base.kNumRepDistances; i++) {
            int repLen = repLens[i];
            if (repLen < 2) {
                continue;
            }
            int price = repMatchPrice + getPureRepPrice(i, _state, posState);
            do {
                int curAndLenPrice = price + _repMatchLenEncoder.getPrice(repLen - 2, posState);
                Optimal optimum = _optimum[repLen];
                if (curAndLenPrice < optimum.Price) {
                    optimum.Price = curAndLenPrice;
                    optimum.PosPrev = 0;
                    optimum.BackPrev = i;
                    optimum.Prev1IsChar = false;
                }
            } while (--repLen >= 2);
        }

        int normalMatchPrice = matchPrice + RangeEncoder.getPrice0(_isRep[_state]);

        len = ((repLens[0] >= 2) ? repLens[0] + 1 : 2);
        if (len <= lenMain) {
            int offs = 0;
            while (len > _matchDistances[offs]) {
                offs += 2;
            }
            for (;; len++) {
                int distance = _matchDistances[offs + 1];
                int curAndLenPrice = normalMatchPrice + getPosLenPrice(distance, len, posState);
                Optimal optimum = _optimum[len];
                if (curAndLenPrice < optimum.Price) {
                    optimum.Price = curAndLenPrice;
                    optimum.PosPrev = 0;
                    optimum.BackPrev = distance + Base.kNumRepDistances;
                    optimum.Prev1IsChar = false;
                }
                if (len == _matchDistances[offs]) {
                    offs += 2;
                    if (offs == numDistancePairs) {
                        break;
                    }
                }
            }
        }

        int cur = 0;

        while (true) {
            cur++;
            if (cur == lenEnd) {
                return backward(cur);
            }
            int newLen = readMatchDistances();
            numDistancePairs = _numDistancePairs;
            if (newLen >= _numFastBytes) {

                _longestMatchLength = newLen;
                _longestMatchWasFound = true;
                return backward(cur);
            }
            position++;
            int posPrev = _optimum[cur].PosPrev;
            int state;
            if (_optimum[cur].Prev1IsChar) {
                posPrev--;
                if (_optimum[cur].Prev2) {
                    state = _optimum[_optimum[cur].PosPrev2].State;
                    if (_optimum[cur].BackPrev2 < Base.kNumRepDistances) {
                        state = Base.stateUpdateRep(state);
                    } else {
                        state = Base.stateUpdateMatch(state);
                    }
                } else {
                    state = _optimum[posPrev].State;
                }
                state = Base.stateUpdateChar(state);
            } else {
                state = _optimum[posPrev].State;
            }
            if (posPrev == cur - 1) {
                if (_optimum[cur].isShortRep()) {
                    state = Base.stateUpdateShortRep(state);
                } else {
                    state = Base.stateUpdateChar(state);
                }
            } else {
                int pos;
                if (_optimum[cur].Prev1IsChar && _optimum[cur].Prev2) {
                    posPrev = _optimum[cur].PosPrev2;
                    pos = _optimum[cur].BackPrev2;
                    state = Base.stateUpdateRep(state);
                } else {
                    pos = _optimum[cur].BackPrev;
                    if (pos < Base.kNumRepDistances) {
                        state = Base.stateUpdateRep(state);
                    } else {
                        state = Base.stateUpdateMatch(state);
                    }
                }
                Optimal opt = _optimum[posPrev];
                if (pos < Base.kNumRepDistances) {
                    if (pos == 0) {
                        reps[0] = opt.Backs0;
                        reps[1] = opt.Backs1;
                        reps[2] = opt.Backs2;
                        reps[3] = opt.Backs3;
                    } else if (pos == 1) {
                        reps[0] = opt.Backs1;
                        reps[1] = opt.Backs0;
                        reps[2] = opt.Backs2;
                        reps[3] = opt.Backs3;
                    } else if (pos == 2) {
                        reps[0] = opt.Backs2;
                        reps[1] = opt.Backs0;
                        reps[2] = opt.Backs1;
                        reps[3] = opt.Backs3;
                    } else {
                        reps[0] = opt.Backs3;
                        reps[1] = opt.Backs0;
                        reps[2] = opt.Backs1;
                        reps[3] = opt.Backs2;
                    }
                } else {
                    reps[0] = (pos - Base.kNumRepDistances);
                    reps[1] = opt.Backs0;
                    reps[2] = opt.Backs1;
                    reps[3] = opt.Backs2;
                }
            }
            _optimum[cur].State = state;
            _optimum[cur].Backs0 = reps[0];
            _optimum[cur].Backs1 = reps[1];
            _optimum[cur].Backs2 = reps[2];
            _optimum[cur].Backs3 = reps[3];
            int curPrice = _optimum[cur].Price;

            currentByte = _matchFinder.getIndexByte(0 - 1);
            matchByte = _matchFinder.getIndexByte(0 - reps[0] - 1 - 1);

            posState = (position & _posStateMask);

            int curAnd1Price = curPrice +
                    RangeEncoder.getPrice0(_isMatch[(state << Base.kNumPosStatesBitsMax) + posState]) +
                    _literalEncoder.getSubCoder(position, _matchFinder.getIndexByte(0 - 2)).
                    getPrice(!Base.stateIsCharState(state), matchByte, currentByte);

            Optimal nextOptimum = _optimum[cur + 1];

            boolean nextIsChar = false;
            if (curAnd1Price < nextOptimum.Price) {
                nextOptimum.Price = curAnd1Price;
                nextOptimum.PosPrev = cur;
                nextOptimum.makeAsChar();
                nextIsChar = true;
            }

            matchPrice = curPrice + RangeEncoder.getPrice1(_isMatch[(state << Base.kNumPosStatesBitsMax) + posState]);
            repMatchPrice = matchPrice + RangeEncoder.getPrice1(_isRep[state]);

            if (matchByte == currentByte &&
                    !(nextOptimum.PosPrev < cur && nextOptimum.BackPrev == 0)) {
                int shortRepPrice = repMatchPrice + getRepLen1Price(state, posState);
                if (shortRepPrice <= nextOptimum.Price) {
                    nextOptimum.Price = shortRepPrice;
                    nextOptimum.PosPrev = cur;
                    nextOptimum.makeAsShortRep();
                    nextIsChar = true;
                }
            }

            int numAvailableBytesFull = _matchFinder.getNumAvailableBytes() + 1;
            numAvailableBytesFull = Math.min(kNumOpts - 1 - cur, numAvailableBytesFull);
            numAvailableBytes = numAvailableBytesFull;

            if (numAvailableBytes < 2) {
                continue;
            }
            if (numAvailableBytes > _numFastBytes) {
                numAvailableBytes = _numFastBytes;
            }
            if (!nextIsChar && matchByte != currentByte) {
                // try Literal + rep0
                int t = Math.min(numAvailableBytesFull - 1, _numFastBytes);
                int lenTest2 = _matchFinder.getMatchLen(0, reps[0], t);
                if (lenTest2 >= 2) {
                    int state2 = Base.stateUpdateChar(state);

                    int posStateNext = (position + 1) & _posStateMask;
                    int nextRepMatchPrice = curAnd1Price +
                            RangeEncoder.getPrice1(_isMatch[(state2 << Base.kNumPosStatesBitsMax) + posStateNext]) +
                            RangeEncoder.getPrice1(_isRep[state2]);
                    {
                        int offset = cur + 1 + lenTest2;
                        while (lenEnd < offset) {
                            _optimum[++lenEnd].Price = kIfinityPrice;
                        }
                        int curAndLenPrice = nextRepMatchPrice + getRepPrice(
                                0, lenTest2, state2, posStateNext);
                        Optimal optimum = _optimum[offset];
                        if (curAndLenPrice < optimum.Price) {
                            optimum.Price = curAndLenPrice;
                            optimum.PosPrev = cur + 1;
                            optimum.BackPrev = 0;
                            optimum.Prev1IsChar = true;
                            optimum.Prev2 = false;
                        }
                    }
                }
            }

            int startLen = 2; // speed optimization

            for (int repIndex = 0; repIndex < Base.kNumRepDistances; repIndex++) {
                int lenTest = _matchFinder.getMatchLen(0 - 1, reps[repIndex], numAvailableBytes);
                if (lenTest < 2) {
                    continue;
                }
                int lenTestTemp = lenTest;
                do {
                    while (lenEnd < cur + lenTest) {
                        _optimum[++lenEnd].Price = kIfinityPrice;
                    }
                    int curAndLenPrice = repMatchPrice + getRepPrice(repIndex, lenTest, state, posState);
                    Optimal optimum = _optimum[cur + lenTest];
                    if (curAndLenPrice < optimum.Price) {
                        optimum.Price = curAndLenPrice;
                        optimum.PosPrev = cur;
                        optimum.BackPrev = repIndex;
                        optimum.Prev1IsChar = false;
                    }
                } while (--lenTest >= 2);
                lenTest = lenTestTemp;

                if (repIndex == 0) {
                    startLen = lenTest + 1;
                }

                // if (_maxMode)
                if (lenTest < numAvailableBytesFull) {
                    int t = Math.min(numAvailableBytesFull - 1 - lenTest, _numFastBytes);
                    int lenTest2 = _matchFinder.getMatchLen(lenTest, reps[repIndex], t);
                    if (lenTest2 >= 2) {
                        int state2 = Base.stateUpdateRep(state);

                        int posStateNext = (position + lenTest) & _posStateMask;
                        int curAndLenCharPrice =
                                repMatchPrice + getRepPrice(repIndex, lenTest, state, posState) +
                                RangeEncoder.getPrice0(_isMatch[(state2 << Base.kNumPosStatesBitsMax) + posStateNext]) +
                                _literalEncoder.getSubCoder(position + lenTest,
                                        _matchFinder.getIndexByte(lenTest - 1 - 1)).getPrice(true,
                                        _matchFinder.getIndexByte(lenTest - 1 - (reps[repIndex] + 1)),
                                        _matchFinder.getIndexByte(lenTest - 1));
                        state2 = Base.stateUpdateChar(state2);
                        posStateNext = (position + lenTest + 1) & _posStateMask;
                        int nextMatchPrice = curAndLenCharPrice + RangeEncoder.getPrice1(_isMatch[(state2 << Base.kNumPosStatesBitsMax) + posStateNext]);
                        int nextRepMatchPrice = nextMatchPrice + RangeEncoder.getPrice1(_isRep[state2]);

                        // for(; lenTest2 >= 2; lenTest2--)
                        {
                            int offset = lenTest + 1 + lenTest2;
                            while (lenEnd < cur + offset) {
                                _optimum[++lenEnd].Price = kIfinityPrice;
                            }
                            int curAndLenPrice = nextRepMatchPrice + getRepPrice(0, lenTest2, state2, posStateNext);
                            Optimal optimum = _optimum[cur + offset];
                            if (curAndLenPrice < optimum.Price) {
                                optimum.Price = curAndLenPrice;
                                optimum.PosPrev = cur + lenTest + 1;
                                optimum.BackPrev = 0;
                                optimum.Prev1IsChar = true;
                                optimum.Prev2 = true;
                                optimum.PosPrev2 = cur;
                                optimum.BackPrev2 = repIndex;
                            }
                        }
                    }
                }
            }

            if (newLen > numAvailableBytes) {
                newLen = numAvailableBytes;
                for (numDistancePairs = 0; newLen > _matchDistances[numDistancePairs]; numDistancePairs += 2);
                _matchDistances[numDistancePairs] = newLen;
                numDistancePairs += 2;
            }
            if (newLen >= startLen) {
                normalMatchPrice = matchPrice + RangeEncoder.getPrice0(_isRep[state]);
                while (lenEnd < cur + newLen) {
                    _optimum[++lenEnd].Price = kIfinityPrice;
                }

                int offs = 0;
                while (startLen > _matchDistances[offs]) {
                    offs += 2;
                }

                for (int lenTest = startLen;; lenTest++) {
                    int curBack = _matchDistances[offs + 1];
                    int curAndLenPrice = normalMatchPrice + getPosLenPrice(curBack, lenTest, posState);
                    Optimal optimum = _optimum[cur + lenTest];
                    if (curAndLenPrice < optimum.Price) {
                        optimum.Price = curAndLenPrice;
                        optimum.PosPrev = cur;
                        optimum.BackPrev = curBack + Base.kNumRepDistances;
                        optimum.Prev1IsChar = false;
                    }

                    if (lenTest == _matchDistances[offs]) {
                        if (lenTest < numAvailableBytesFull) {
                            int t = Math.min(numAvailableBytesFull - 1 - lenTest, _numFastBytes);
                            int lenTest2 = _matchFinder.getMatchLen(lenTest, curBack, t);
                            if (lenTest2 >= 2) {
                                int state2 = Base.stateUpdateMatch(state);

                                int posStateNext = (position + lenTest) & _posStateMask;
                                int curAndLenCharPrice = curAndLenPrice +
                                        RangeEncoder.getPrice0(_isMatch[(state2 << Base.kNumPosStatesBitsMax) + posStateNext]) +
                                        _literalEncoder.getSubCoder(position + lenTest,
                                                _matchFinder.getIndexByte(lenTest - 1 - 1)).
                                        getPrice(true,
                                                _matchFinder.getIndexByte(lenTest - (curBack + 1) - 1),
                                                _matchFinder.getIndexByte(lenTest - 1));
                                state2 = Base.stateUpdateChar(state2);
                                posStateNext = (position + lenTest + 1) & _posStateMask;
                                int nextMatchPrice = curAndLenCharPrice + RangeEncoder
                                        .getPrice1(_isMatch[(state2 << Base.kNumPosStatesBitsMax) + posStateNext]);
                                int nextRepMatchPrice = nextMatchPrice + RangeEncoder
                                        .getPrice1(_isRep[state2]);

                                int offset = lenTest + 1 + lenTest2;
                                while (lenEnd < cur + offset) {
                                    _optimum[++lenEnd].Price = kIfinityPrice;
                                }
                                curAndLenPrice = nextRepMatchPrice + getRepPrice(0, lenTest2, state2, posStateNext);
                                optimum = _optimum[cur + offset];
                                if (curAndLenPrice < optimum.Price) {
                                    optimum.Price = curAndLenPrice;
                                    optimum.PosPrev = cur + lenTest + 1;
                                    optimum.BackPrev = 0;
                                    optimum.Prev1IsChar = true;
                                    optimum.Prev2 = true;
                                    optimum.PosPrev2 = cur;
                                    optimum.BackPrev2 = curBack + Base.kNumRepDistances;
                                }
                            }
                        }
                        offs += 2;
                        if (offs == numDistancePairs) {
                            break;
                        }
                    }
                }
            }
        }
    }

    boolean changePair(int smallDist, int bigDist) {
        int kDif = 7;
        return (smallDist < (1 << (32 - kDif)) && bigDist >= (smallDist << kDif));
    }

    void writeEndMarker(int posState) throws IOException {
        if (!_writeEndMark) {
            return;
        }
        _rangeEncoder.encode(_isMatch, (_state << Base.kNumPosStatesBitsMax) + posState, 1);
        _rangeEncoder.encode(_isRep, _state, 0);
        _state = Base.stateUpdateMatch(_state);
        int len = Base.kMatchMinLen;
        _lenEncoder.encode(_rangeEncoder, len - Base.kMatchMinLen, posState);
        int posSlot = (1 << Base.kNumPosSlotBits) - 1;
        int lenToPosState = Base.getLenToPosState(len);
        _posSlotEncoder[lenToPosState].encode(_rangeEncoder, posSlot);
        int footerBits = 30;
        int posReduced = (1 << footerBits) - 1;
        _rangeEncoder.encodeDirectBits(posReduced >> Base.kNumAlignBits, footerBits - Base.kNumAlignBits);
        _posAlignEncoder.reverseEncode(_rangeEncoder, posReduced & Base.kAlignMask);
    }

    void flush(int nowPos) throws IOException {
        releaseMFBuffer();
        writeEndMarker(nowPos & _posStateMask);
        _rangeEncoder.flushData();
    }

    public void codeOneBlock(long[] inSize, long[] outSize, boolean[] finished) throws IOException {
        inSize[0] = 0;
        outSize[0] = 0;
        finished[0] = true;

        if (_src != null) {
            _matchFinder.setBuffer(_src);
            _matchFinder.init();
            _needReleaseMFStream = true;
            _src = null;
        }

        if (_finished) {
            return;
        }
        _finished = true;


        long progressPosValuePrev = nowPos64;
        if (nowPos64 == 0) {
            if (_matchFinder.getNumAvailableBytes() == 0) {
                flush((int) nowPos64);
                return;
            }

            readMatchDistances();
            int posState = (int) (nowPos64) & _posStateMask;
            _rangeEncoder.encode(_isMatch, (_state << Base.kNumPosStatesBitsMax) + posState, 0);
            _state = Base.stateUpdateChar(_state);
            byte curByte = _matchFinder.getIndexByte(0 - _additionalOffset);
            _literalEncoder.getSubCoder((int) (nowPos64), _previousByte).encode(_rangeEncoder, curByte);
            _previousByte = curByte;
            _additionalOffset--;
            nowPos64++;
        }
        if (_matchFinder.getNumAvailableBytes() == 0) {
            flush((int) nowPos64);
            return;
        }
        while (true) {

            int len = getOptimum((int) nowPos64);
            int pos = backRes;
            int posState = ((int) nowPos64) & _posStateMask;
            int complexState = (_state << Base.kNumPosStatesBitsMax) + posState;
            if (len == 1 && pos == -1) {
                _rangeEncoder.encode(_isMatch, complexState, 0);
                byte curByte = _matchFinder.getIndexByte(0 - _additionalOffset);
                LiteralEncoder.Encoder2 subCoder = _literalEncoder.getSubCoder((int) nowPos64, _previousByte);
                if (!Base.stateIsCharState(_state)) {
                    byte matchByte = _matchFinder.getIndexByte(0 - _repDistances[0] - 1 - _additionalOffset);
                    subCoder.encodeMatched(_rangeEncoder, matchByte, curByte);
                } else {
                    subCoder.encode(_rangeEncoder, curByte);
                }
                _previousByte = curByte;
                _state = Base.stateUpdateChar(_state);
            } else {
                _rangeEncoder.encode(_isMatch, complexState, 1);
                if (pos < Base.kNumRepDistances) {
                    _rangeEncoder.encode(_isRep, _state, 1);
                    if (pos == 0) {
                        _rangeEncoder.encode(_isRepG0, _state, 0);
                        if (len == 1) {
                            _rangeEncoder.encode(_isRep0Long, complexState, 0);
                        } else {
                            _rangeEncoder.encode(_isRep0Long, complexState, 1);
                        }
                    } else {
                        _rangeEncoder.encode(_isRepG0, _state, 1);
                        if (pos == 1) {
                            _rangeEncoder.encode(_isRepG1, _state, 0);
                        } else {
                            _rangeEncoder.encode(_isRepG1, _state, 1);
                            _rangeEncoder.encode(_isRepG2, _state, pos - 2);
                        }
                    }
                    if (len == 1) {
                        _state = Base.stateUpdateShortRep(_state);
                    } else {
                        _repMatchLenEncoder.encode(_rangeEncoder, len - Base.kMatchMinLen, posState);
                        _state = Base.stateUpdateRep(_state);
                    }
                    int distance = _repDistances[pos];
                    if (pos != 0) {
                        System.arraycopy(_repDistances, 0, _repDistances, 1, pos);
                        _repDistances[0] = distance;
                    }
                } else {
                    _rangeEncoder.encode(_isRep, _state, 0);
                    _state = Base.stateUpdateMatch(_state);
                    _lenEncoder.encode(_rangeEncoder, len - Base.kMatchMinLen, posState);
                    pos -= Base.kNumRepDistances;
                    int posSlot = getPosSlot(pos);
                    int lenToPosState = Base.getLenToPosState(len);
                    _posSlotEncoder[lenToPosState].encode(_rangeEncoder, posSlot);

                    if (posSlot >= Base.kStartPosModelIndex) {
                        int footerBits = (posSlot >> 1) - 1;
                        int baseVal = ((2 | (posSlot & 1)) << footerBits);
                        int posReduced = pos - baseVal;

                        if (posSlot < Base.kEndPosModelIndex) {
                            BitTreeEncoder.reverseEncode(_posEncoders,
                                    baseVal - posSlot - 1, _rangeEncoder, footerBits, posReduced);
                        } else {
                            _rangeEncoder.encodeDirectBits(posReduced >> Base.kNumAlignBits, footerBits - Base.kNumAlignBits);
                            _posAlignEncoder.reverseEncode(_rangeEncoder, posReduced & Base.kAlignMask);
                            _alignPriceCount++;
                        }
                    }
                    int distance = pos;
                    System.arraycopy(_repDistances, 0, _repDistances, 1, Base.kNumRepDistances - 1);
                    _repDistances[0] = distance;
                    _matchPriceCount++;
                }
                _previousByte = _matchFinder.getIndexByte(len - 1 - _additionalOffset);
            }
            _additionalOffset -= len;
            nowPos64 += len;
            if (_additionalOffset == 0) {
                // if (!_fastMode)
                if (_matchPriceCount >= (1 << 7)) {
                    fillDistancesPrices();
                }
                if (_alignPriceCount >= Base.kAlignTableSize) {
                    fillAlignPrices();
                }
                inSize[0] = nowPos64;
                outSize[0] = _rangeEncoder.getProcessedSizeAdd();
                if (_matchFinder.getNumAvailableBytes() == 0) {
                    flush((int) nowPos64);
                    return;
                }

                if (nowPos64 - progressPosValuePrev >= (1 << 12)) {
                    _finished = false;
                    finished[0] = false;
                    return;
                }
            }
        }
    }

    void releaseMFBuffer() {
        if (_matchFinder != null && _needReleaseMFStream) {
            _matchFinder.releaseBuffer();
            _needReleaseMFStream = false;
        }
    }

    void setDstBuffer(Buffer dst, MemoryManager mm) {
        _rangeEncoder.setBuffer(dst, mm);
    }

    Buffer releaseDstBuffer() {
        return _rangeEncoder.releaseBuffer();
    }

    void releaseBuffers(LZMAEncoder.LZMAOutputState state) {
        releaseMFBuffer();
        state.setDst(releaseDstBuffer());
    }

    void setStreams(Buffer src, Buffer dst, MemoryManager mm, long inSize, long outSize) {
        _src = src;
        _finished = false;
        create();
        setDstBuffer(dst, mm);
        init();

        // if (!_fastMode)
        {
            fillDistancesPrices();
            fillAlignPrices();
        }

        _lenEncoder.setTableSize(_numFastBytes + 1 - Base.kMatchMinLen);
        _lenEncoder.updateTables(1 << _posStateBits);
        _repMatchLenEncoder.setTableSize(_numFastBytes + 1 - Base.kMatchMinLen);
        _repMatchLenEncoder.updateTables(1 << _posStateBits);

        nowPos64 = 0;
    }
    final long[] processedInSize = new long[1];
    final long[] processedOutSize = new long[1];
    final boolean[] finished = new boolean[1];

    public void code(LZMAEncoder.LZMAOutputState state, long inSize, long outSize) throws IOException {
        _needReleaseMFStream = false;
        try {
            setStreams(state.getSrc(), state.getDst(), state.getMemoryManager(), inSize, outSize);
            while (true) {
                codeOneBlock(processedInSize, processedOutSize, finished);
                if (finished[0]) {
                    return;
                }
            }
        } finally {
            releaseBuffers(state);
        }
    }

//	public static final int kPropSize = 5;
//	byte[] properties = new byte[kPropSize];
    public void writeCoderProperties(Buffer dst) throws IOException {
//		properties[0] = (byte)((_posStateBits * 5 + _numLiteralPosStateBits) * 9 + _numLiteralContextBits);
//		for (int i = 0; i < 4; i++)
//			properties[1 + i] = (byte)(_dictionarySize >> (8 * i));
//		outStream.write(properties, 0, kPropSize);

        dst.put((byte) ((_posStateBits * 5 + _numLiteralPosStateBits) * 9 + _numLiteralContextBits));
        for (int i = 0; i < 4; i++) {
            dst.put((byte) (_dictionarySize >> (8 * i)));
        }
    }
    final int[] tempPrices = new int[Base.kNumFullDistances];
    int _matchPriceCount;

    void fillDistancesPrices() {
        for (int i = Base.kStartPosModelIndex; i < Base.kNumFullDistances; i++) {
            int posSlot = getPosSlot(i);
            int footerBits = (posSlot >> 1) - 1;
            int baseVal = ((2 | (posSlot & 1)) << footerBits);
            tempPrices[i] = BitTreeEncoder.reverseGetPrice(_posEncoders,
                    baseVal - posSlot - 1, footerBits, i - baseVal);
        }

        for (int lenToPosState = 0; lenToPosState < Base.kNumLenToPosStates; lenToPosState++) {
            int posSlot;
            BitTreeEncoder encoder = _posSlotEncoder[lenToPosState];

            int st = (lenToPosState << Base.kNumPosSlotBits);
            for (posSlot = 0; posSlot < _distTableSize; posSlot++) {
                _posSlotPrices[st + posSlot] = encoder.getPrice(posSlot);
            }
            for (posSlot = Base.kEndPosModelIndex; posSlot < _distTableSize; posSlot++) {
                _posSlotPrices[st + posSlot] += ((((posSlot >> 1) - 1) - Base.kNumAlignBits) << RangeEncoder.kNumBitPriceShiftBits);
            }

            int st2 = lenToPosState * Base.kNumFullDistances;
            int i;
            for (i = 0; i < Base.kStartPosModelIndex; i++) {
                _distancesPrices[st2 + i] = _posSlotPrices[st + i];
            }
            for (; i < Base.kNumFullDistances; i++) {
                _distancesPrices[st2 + i] = _posSlotPrices[st + getPosSlot(i)] + tempPrices[i];
            }
        }
        _matchPriceCount = 0;
    }

    void fillAlignPrices() {
        for (int i = 0; i < Base.kAlignTableSize; i++) {
            _alignPrices[i] = _posAlignEncoder.reverseGetPrice(i);
        }
        _alignPriceCount = 0;
    }

    public boolean setAlgorithm(int algorithm) {
        /*
        _fastMode = (algorithm == 0);
        _maxMode = (algorithm >= 2);
         */
        return true;
    }

    public boolean setDictionarySize(int dictionarySize) {
        int kDicLogSizeMaxCompress = 29;
        if (dictionarySize < (1 << Base.kDicLogSizeMin) || dictionarySize > (1 << kDicLogSizeMaxCompress)) {
            return false;
        }
        _dictionarySize = dictionarySize;
        int dicLogSize;
        for (dicLogSize = 0; dictionarySize > (1 << dicLogSize); dicLogSize++);
        _distTableSize = dicLogSize * 2;
        return true;
    }

    public boolean setNumFastBytes(int numFastBytes) {
        if (numFastBytes < 5 || numFastBytes > Base.kMatchMaxLen) {
            return false;
        }
        _numFastBytes = numFastBytes;
        return true;
    }

    public boolean setMatchFinder(int matchFinderIndex) {
        if (matchFinderIndex < 0 || matchFinderIndex > 2) {
            return false;
        }
        int matchFinderIndexPrev = _matchFinderType;
        _matchFinderType = matchFinderIndex;
        if (_matchFinder != null && matchFinderIndexPrev != _matchFinderType) {
            _dictionarySizePrev = -1;
            _matchFinder = null;
        }
        return true;
    }

    public boolean setLcLpPb(int lc, int lp, int pb) {
        if (lp < 0 || lp > Base.kNumLitPosStatesBitsEncodingMax ||
                lc < 0 || lc > Base.kNumLitContextBitsMax ||
                pb < 0 || pb > Base.kNumPosStatesBitsEncodingMax) {
            return false;
        }
        _numLiteralPosStateBits = lp;
        _numLiteralContextBits = lc;
        _posStateBits = pb;
        _posStateMask = ((1) << _posStateBits) - 1;
        return true;
    }

    public void setEndMarkerMode(boolean endMarkerMode) {
        _writeEndMark = endMarkerMode;
    }
}

