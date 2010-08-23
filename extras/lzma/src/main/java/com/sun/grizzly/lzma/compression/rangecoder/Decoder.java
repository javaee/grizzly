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
 * Decoder
 *
 * @author Igor Pavlov
 */
public class Decoder {

    static final int kTopMask = ~((1 << 24) - 1);
    static final int kNumBitModelTotalBits = 11;
    static final int kBitModelTotal = (1 << kNumBitModelTotalBits);
    static final int kNumMoveBits = 5;
    int Range;
    int Code;
    java.io.InputStream Stream;

    public final void SetStream(java.io.InputStream stream) {
        Stream = stream;
    }

    public final void ReleaseStream() {
        Stream = null;
    }

    public final void Init() throws IOException {
        Code = 0;
        Range = -1;
        for (int i = 0; i < 5; i++) {
            Code = (Code << 8) | Stream.read();
        }
    }

    public final int DecodeDirectBits(int numTotalBits) throws IOException {
        int result = 0;
        for (int i = numTotalBits; i != 0; i--) {
            Range >>>= 1;
            int t = ((Code - Range) >>> 31);
            Code -= Range & (t - 1);
            result = (result << 1) | (1 - t);

            if ((Range & kTopMask) == 0) {
                Code = (Code << 8) | Stream.read();
                Range <<= 8;
            }
        }
        return result;
    }

    public int DecodeBit(short[] probs, int index) throws IOException {
        int prob = probs[index];
        int newBound = (Range >>> kNumBitModelTotalBits) * prob;
        if ((Code ^ 0x80000000) < (newBound ^ 0x80000000)) {
            Range = newBound;
            probs[index] = (short) (prob + ((kBitModelTotal - prob) >>> kNumMoveBits));
            if ((Range & kTopMask) == 0) {
                Code = (Code << 8) | Stream.read();
                Range <<= 8;
            }
            return 0;
        } else {
            Range -= newBound;
            Code -= newBound;
            probs[index] = (short) (prob - ((prob) >>> kNumMoveBits));
            if ((Range & kTopMask) == 0) {
                Code = (Code << 8) | Stream.read();
                Range <<= 8;
            }
            return 1;
        }
    }

    public static void InitBitModels(short[] probs) {
        for (int i = 0; i < probs.length; i++) {
            probs[i] = (kBitModelTotal >>> 1);
        }
    }
}
