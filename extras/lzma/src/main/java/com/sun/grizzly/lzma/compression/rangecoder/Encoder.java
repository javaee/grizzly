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

package com.sun.grizzly.lzma.compression.rangecoder;

import java.io.IOException;

/**
 * Encoder
 *
 * @author Igor Pavlov
 */
public class Encoder {

    static final int kTopMask = ~((1 << 24) - 1);
    static final int kNumBitModelTotalBits = 11;
    static final int kBitModelTotal = (1 << kNumBitModelTotalBits);
    static final int kNumMoveBits = 5;
    java.io.OutputStream Stream;
    long Low;
    int Range;
    int _cacheSize;
    int _cache;
    long _position;

    public void SetStream(java.io.OutputStream stream) {
        Stream = stream;
    }

    public void ReleaseStream() {
        Stream = null;
    }

    public void Init() {
        _position = 0;
        Low = 0;
        Range = -1;
        _cacheSize = 1;
        _cache = 0;
    }

    public void FlushData() throws IOException {
        for (int i = 0; i < 5; i++) {
            ShiftLow();
        }
    }

    public void FlushStream() throws IOException {
        Stream.flush();
    }

    public void ShiftLow() throws IOException {
        int LowHi = (int) (Low >>> 32);
        if (LowHi != 0 || Low < 0xFF000000L) {
            _position += _cacheSize;
            int temp = _cache;
            do {
                Stream.write(temp + LowHi);
                temp = 0xFF;
            } while (--_cacheSize != 0);
            _cache = (((int) Low) >>> 24);
        }
        _cacheSize++;
        Low = (Low & 0xFFFFFF) << 8;
    }

    public void EncodeDirectBits(int v, int numTotalBits) throws IOException {
        for (int i = numTotalBits - 1; i >= 0; i--) {
            Range >>>= 1;
            if (((v >>> i) & 1) == 1) {
                Low += Range;
            }
            if ((Range & Encoder.kTopMask) == 0) {
                Range <<= 8;
                ShiftLow();
            }
        }
    }

    public long GetProcessedSizeAdd() {
        return _cacheSize + _position + 4;
    }
    static final int kNumMoveReducingBits = 2;
    public static final int kNumBitPriceShiftBits = 6;

    public static void InitBitModels(short[] probs) {
        for (int i = 0; i < probs.length; i++) {
            probs[i] = (kBitModelTotal >>> 1);
        }
    }

    public void Encode(short[] probs, int index, int symbol) throws IOException {
        int prob = probs[index];
        int newBound = (Range >>> kNumBitModelTotalBits) * prob;
        if (symbol == 0) {
            Range = newBound;
            probs[index] = (short) (prob + ((kBitModelTotal - prob) >>> kNumMoveBits));
        } else {
            Low += (newBound & 0xFFFFFFFFL);
            Range -= newBound;
            probs[index] = (short) (prob - ((prob) >>> kNumMoveBits));
        }
        if ((Range & kTopMask) == 0) {
            Range <<= 8;
            ShiftLow();
        }
    }
    private static int[] ProbPrices = new int[kBitModelTotal >>> kNumMoveReducingBits];

    static {
        int kNumBits = (kNumBitModelTotalBits - kNumMoveReducingBits);
        for (int i = kNumBits - 1; i >= 0; i--) {
            int start = 1 << (kNumBits - i - 1);
            int end = 1 << (kNumBits - i);
            for (int j = start; j < end; j++) {
                ProbPrices[j] = (i << kNumBitPriceShiftBits) +
                        (((end - j) << kNumBitPriceShiftBits) >>> (kNumBits - i - 1));
            }
        }
    }

    static public int GetPrice(int Prob, int symbol) {
        return ProbPrices[(((Prob - symbol) ^ ((-symbol))) & (kBitModelTotal - 1)) >>> kNumMoveReducingBits];
    }

    static public int GetPrice0(int Prob) {
        return ProbPrices[Prob >>> kNumMoveReducingBits];
    }

    static public int GetPrice1(int Prob) {
        return ProbPrices[(kBitModelTotal - Prob) >>> kNumMoveReducingBits];
    }
}
