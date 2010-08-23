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

/**
 * Base
 *
 * @author Igor Pavlov
 */
public class Base {

    public static final int kNumRepDistances = 4;
    public static final int kNumStates = 12;

    public static final int StateInit() {
        return 0;
    }

    public static final int StateUpdateChar(int index) {
        if (index < 4) {
            return 0;
        }
        if (index < 10) {
            return index - 3;
        }
        return index - 6;
    }

    public static final int StateUpdateMatch(int index) {
        return (index < 7 ? 7 : 10);
    }

    public static final int StateUpdateRep(int index) {
        return (index < 7 ? 8 : 11);
    }

    public static final int StateUpdateShortRep(int index) {
        return (index < 7 ? 9 : 11);
    }

    public static final boolean StateIsCharState(int index) {
        return index < 7;
    }
    public static final int kNumPosSlotBits = 6;
    public static final int kDicLogSizeMin = 0;
    // public static final int kDicLogSizeMax = 28;
    // public static final int kDistTableSizeMax = kDicLogSizeMax * 2;
    public static final int kNumLenToPosStatesBits = 2; // it's for speed optimization
    public static final int kNumLenToPosStates = 1 << kNumLenToPosStatesBits;
    public static final int kMatchMinLen = 2;

    public static final int GetLenToPosState(int len) {
        len -= kMatchMinLen;
        if (len < kNumLenToPosStates) {
            return len;
        }
        return (int) (kNumLenToPosStates - 1);
    }
    public static final int kNumAlignBits = 4;
    public static final int kAlignTableSize = 1 << kNumAlignBits;
    public static final int kAlignMask = (kAlignTableSize - 1);
    public static final int kStartPosModelIndex = 4;
    public static final int kEndPosModelIndex = 14;
    public static final int kNumPosModels = kEndPosModelIndex - kStartPosModelIndex;
    public static final int kNumFullDistances = 1 << (kEndPosModelIndex / 2);
    public static final int kNumLitPosStatesBitsEncodingMax = 4;
    public static final int kNumLitContextBitsMax = 8;
    public static final int kNumPosStatesBitsMax = 4;
    public static final int kNumPosStatesMax = (1 << kNumPosStatesBitsMax);
    public static final int kNumPosStatesBitsEncodingMax = 4;
    public static final int kNumPosStatesEncodingMax = (1 << kNumPosStatesBitsEncodingMax);
    public static final int kNumLowLenBits = 3;
    public static final int kNumMidLenBits = 3;
    public static final int kNumHighLenBits = 8;
    public static final int kNumLowLenSymbols = 1 << kNumLowLenBits;
    public static final int kNumMidLenSymbols = 1 << kNumMidLenBits;
    public static final int kNumLenSymbols = kNumLowLenSymbols + kNumMidLenSymbols +
            (1 << kNumHighLenBits);
    public static final int kMatchMaxLen = kMatchMinLen + kNumLenSymbols - 1;
}
