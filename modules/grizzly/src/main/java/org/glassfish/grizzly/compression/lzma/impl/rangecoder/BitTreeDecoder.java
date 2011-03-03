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

import org.glassfish.grizzly.compression.lzma.LZMADecoder;

/**
 * BitTreeDecoder
 *
 * @author Igor Pavlov
 */
public class BitTreeDecoder {

    short[] Models;
    int NumBitLevels;

    // decode state
    int decodeMethodState;
    int m;
    int bitIndex;

    // reverseDecode state
    int reverseDecodeMethodState;
    int symbol;

    public BitTreeDecoder(int numBitLevels) {
        NumBitLevels = numBitLevels;
        Models = new short[1 << numBitLevels];
    }

    public void init() {
        decodeMethodState = 0;
        reverseDecodeMethodState = 0;

        RangeDecoder.initBitModels(Models);
    }

    public boolean decode(LZMADecoder.LZMAInputState decodeState,
            RangeDecoder rangeDecoder) throws java.io.IOException {

        do {
            switch(decodeMethodState) {
                case 0:
                {
                    m = 1;
                    bitIndex = NumBitLevels;
                    decodeMethodState = 1;
                }
                case 1:
                {
                    if (bitIndex == 0) {
                        decodeMethodState = 3;
                        continue;
                    }

                    decodeMethodState = 2;
                }
                case 2:
                {
                    if (!rangeDecoder.decodeBit(decodeState, Models, m)) {
                        return false;
                    }
                    
                    m = (m << 1) + decodeState.lastMethodResult;
                    bitIndex--;

                    decodeMethodState = 1;
                    continue;
                }
                case 3:
                {
                    decodeState.lastMethodResult = m - (1 << NumBitLevels);
                    decodeMethodState = 0;
                    return true;
                }

            }
        } while (true);
    }

    public boolean reverseDecode(LZMADecoder.LZMAInputState decodeState,
            RangeDecoder rangeDecoder) throws java.io.IOException {
        
        do {
            switch(reverseDecodeMethodState) {
                case 0:
                {
                    m = 1;
                    symbol = 0;
                    bitIndex = 0;
                    reverseDecodeMethodState = 1;
                }
                case 1:
                {
                    if (bitIndex >= NumBitLevels) {
                        reverseDecodeMethodState = 3;
                        continue;
                    }

                    reverseDecodeMethodState = 2;
                }
                case 2:
                {
                    if (!rangeDecoder.decodeBit(decodeState, Models, m)) {
                        return false;
                    }

                    final int bit = decodeState.lastMethodResult;
                    m <<= 1;
                    m += bit;
                    symbol |= (bit << bitIndex);

                    bitIndex++;
                    reverseDecodeMethodState = 1;
                    continue;
                }
                case 3:
                {
                    decodeState.lastMethodResult = symbol;
                    reverseDecodeMethodState = 0;
                    return true;
                }

            }
        } while (true);
    }

    public static boolean reverseDecode(LZMADecoder.LZMAInputState decodeState,
            short[] Models, int startIndex, RangeDecoder rangeDecoder,
            int NumBitLevels) throws java.io.IOException {

        do {
            switch(decodeState.staticReverseDecodeMethodState) {
                case 0:
                {
                    decodeState.staticM = 1;
                    decodeState.staticSymbol = 0;
                    decodeState.staticBitIndex = 0;
                    decodeState.staticReverseDecodeMethodState = 1;
                }
                case 1:
                {
                    if (decodeState.staticBitIndex >= NumBitLevels) {
                        decodeState.staticReverseDecodeMethodState = 3;
                        continue;
                    }

                    decodeState.staticReverseDecodeMethodState = 2;
                }
                case 2:
                {
                    if (!rangeDecoder.decodeBit(decodeState, Models,
                            startIndex + decodeState.staticM)) {
                        return false;
                    }

                    final int bit = decodeState.lastMethodResult;
                    decodeState.staticM <<= 1;
                    decodeState.staticM += bit;
                    decodeState.staticSymbol |= (bit << decodeState.staticBitIndex);

                    decodeState.staticBitIndex++;
                    decodeState.staticReverseDecodeMethodState = 1;
                    continue;
                }
                case 3:
                {
                    decodeState.lastMethodResult = decodeState.staticSymbol;
                    decodeState.staticReverseDecodeMethodState = 0;
                    return true;
                }

            }
        } while (true);
    }
}
