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

package org.glassfish.grizzly.compression.lzma.impl.rangecoder;

import org.glassfish.grizzly.Buffer;

import java.io.IOException;
import org.glassfish.grizzly.compression.lzma.LZMADecoder;

/**
 * RangeDecoder
 *
 * @author Igor Pavlov
 */
public class RangeDecoder {

    static final int kTopMask = ~((1 << 24) - 1);
    static final int kNumBitModelTotalBits = 11;
    static final int kBitModelTotal = (1 << kNumBitModelTotalBits);
    static final int kNumMoveBits = 5;
    int Range;
    int Code;
    Buffer inputBuffer;

    int newBound;

    int decodeBitState;

    int decodeDirectBitsState;
    int decodeDirectBitsResult;
    int decodeDirectBitsI;

    public final void initFromState(final LZMADecoder.LZMAInputState decoderState) {
        this.inputBuffer = decoderState.getSrc();
    }

    public final void releaseBuffer() {
        inputBuffer = null;
    }

    public final void init() throws IOException {
        Code = 0;
        Range = -1;
        decodeBitState = 0;
        decodeDirectBitsState = 0;
        for (int i = 0; i < 5; i++) {
            Code = (Code << 8) | (inputBuffer.get() & 0xFF);
        }
    }

    public final boolean decodeDirectBits(LZMADecoder.LZMAInputState decodeState,
            int numTotalBits) throws IOException {
        do {
            switch (decodeDirectBitsState) {
                case 0:
                {
                    decodeDirectBitsResult = 0;
                    decodeDirectBitsI = numTotalBits;
                    decodeDirectBitsState = 1;
                }
                case 1:
                {
                    if (decodeDirectBitsI == 0) {
                        decodeDirectBitsState = 4;
                        continue;
                    }

                    Range >>>= 1;
                    final int t = ((Code - Range) >>> 31);
                    Code -= Range & (t - 1);
                    decodeDirectBitsResult = (decodeDirectBitsResult << 1) | (1 - t);
                    final boolean condition = (Range & kTopMask) == 0;
                    decodeDirectBitsState = condition ? 2 : 3;
                    continue;
                }
                case 2: {
                    if (!inputBuffer.hasRemaining()) {
                        return false;
                    }
                    Code = (Code << 8) | (inputBuffer.get() & 0xFF);
                    Range <<= 8;
                }
                case 3: {
                    decodeDirectBitsI--;
                    decodeDirectBitsState = 1;
                    continue;
                }
                case 4: {
                    decodeState.lastMethodResult = decodeDirectBitsResult;
                    decodeDirectBitsState = 0;
                    return true;
                }
            }
        } while (true);
    }

    public boolean decodeBit(LZMADecoder.LZMAInputState decodeState, short[] probs, int index)
            throws IOException {

        do {
            switch (decodeBitState) {
                case 0: {
                    int prob = probs[index];
                    newBound = (Range >>> kNumBitModelTotalBits) * prob;
                    final boolean condition = (Code ^ 0x80000000) < (newBound ^ 0x80000000);
                    decodeBitState = condition ? 1 : 4;
                    continue;
                }
                case 1: {
                    int prob = probs[index];
                    Range = newBound;
                    probs[index] = (short) (prob + ((kBitModelTotal - prob) >>> kNumMoveBits));
                    final boolean condition = (Range & kTopMask) == 0;
                    decodeBitState = condition ? 2 : 3;
                    continue;
                }
                case 2: {
                    if (!inputBuffer.hasRemaining()) {
                        return false;
                    }
                    Code = (Code << 8) | (inputBuffer.get() & 0xFF);
                    Range <<= 8;
                }
                case 3: {
                    decodeState.lastMethodResult = 0;
                    decodeBitState = 0;
                    return true;
                }
                case 4: {
                    int prob = probs[index];
                    Range -= newBound;
                    Code -= newBound;
                    probs[index] = (short) (prob - ((prob) >>> kNumMoveBits));
                    final boolean condition = (Range & kTopMask) == 0;
                    decodeBitState = condition ? 5 : 6;
                    continue;
                }
                case 5: {
                    if (!inputBuffer.hasRemaining()) {
                        return false;
                    }
                    Code = (Code << 8) | (inputBuffer.get() & 0xFF);
                    Range <<= 8;
                }
                case 6: {
                    decodeState.lastMethodResult = 1;
                    decodeBitState = 0;
                    return true;
                }
            }
        } while (true);
    }

    public static void initBitModels(short[] probs) {
        for (int i = 0; i < probs.length; i++) {
            probs[i] = (kBitModelTotal >>> 1);
        }
    }
}
