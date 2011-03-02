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

import java.io.IOException;

/**
 * BitTreeEncoder
 *
 * @author Igor Pavlov
 */
public class BitTreeEncoder {

    short[] Models;
    int NumBitLevels;

    public BitTreeEncoder(int numBitLevels) {
        NumBitLevels = numBitLevels;
        Models = new short[1 << numBitLevels];
    }

    public void init() {
        RangeDecoder.initBitModels(Models);
    }

    public void encode(RangeEncoder rangeEncoder, int symbol) throws IOException {
        int m = 1;
        for (int bitIndex = NumBitLevels; bitIndex != 0;) {
            bitIndex--;
            int bit = (symbol >>> bitIndex) & 1;
            rangeEncoder.encode(Models, m, bit);
            m = (m << 1) | bit;
        }
    }

    public void reverseEncode(RangeEncoder rangeEncoder, int symbol) throws IOException {
        int m = 1;
        for (int i = 0; i < NumBitLevels; i++) {
            int bit = symbol & 1;
            rangeEncoder.encode(Models, m, bit);
            m = (m << 1) | bit;
            symbol >>= 1;
        }
    }

    public int getPrice(int symbol) {
        int price = 0;
        int m = 1;
        for (int bitIndex = NumBitLevels; bitIndex != 0;) {
            bitIndex--;
            int bit = (symbol >>> bitIndex) & 1;
            price += RangeEncoder.getPrice(Models[m], bit);
            m = (m << 1) + bit;
        }
        return price;
    }

    public int reverseGetPrice(int symbol) {
        int price = 0;
        int m = 1;
        for (int i = NumBitLevels; i != 0; i--) {
            int bit = symbol & 1;
            symbol >>>= 1;
            price += RangeEncoder.getPrice(Models[m], bit);
            m = (m << 1) | bit;
        }
        return price;
    }

    public static int reverseGetPrice(short[] Models, int startIndex,
            int NumBitLevels, int symbol) {
        int price = 0;
        int m = 1;
        for (int i = NumBitLevels; i != 0; i--) {
            int bit = symbol & 1;
            symbol >>>= 1;
            price += RangeEncoder.getPrice(Models[startIndex + m], bit);
            m = (m << 1) | bit;
        }
        return price;
    }

    public static void reverseEncode(short[] Models, int startIndex,
            RangeEncoder rangeEncoder, int NumBitLevels, int symbol) throws IOException {
        int m = 1;
        for (int i = 0; i < NumBitLevels; i++) {
            int bit = symbol & 1;
            rangeEncoder.encode(Models, startIndex + m, bit);
            m = (m << 1) | bit;
            symbol >>= 1;
        }
    }
}
