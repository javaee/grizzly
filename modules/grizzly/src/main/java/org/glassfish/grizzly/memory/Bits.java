/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2011 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.memory;

/**
 * Modified java.nio.Bits source for Grizzly Purposes.
 *
 * @since 2.0
 */

class Bits {

    private Bits() { }

    // -- get/put char --

    static char makeChar(byte b1, byte b0) {
        return (char)((b1 << 8) | (b0 & 0xff));
    }

    static char getCharL(byte[] bb, int bi) {
        return makeChar(bb[bi + 1],
                        bb[(bi)]);
    }

    static char getCharB(byte[] bb, int bi) {
        return makeChar(bb[bi],
                        bb[bi + 1]);
    }

    static char getChar(byte[] bb, int bi, boolean bigEndian) {
        return (bigEndian ? getCharB(bb, bi) : getCharL(bb, bi));
    }

    static byte char1(char x) { return (byte)(x >> 8); }
    static byte char0(char x) { return (byte)(x); }

    static void putCharL(byte[] bb, int bi, char x) {
        bb[bi] =char0(x);
        bb[bi + 1] = char1(x);
    }

    static void putCharB(byte[] bb, int bi, char x) {
        bb[bi] = char1(x);
        bb[bi + 1] = char0(x);
    }

    static void putChar(byte[] bb, int bi, char x, boolean bigEndian) {
        if (bigEndian)
            putCharB(bb, bi, x);
        else
            putCharL(bb, bi, x);
    }


    // -- get/put short --

    static short makeShort(byte b1, byte b0) {
        return (short)((b1 << 8) | (b0 & 0xff));
    }

    static short getShortL(byte[] bb, int bi) {
        return makeShort(bb[bi + 1],
                         bb[bi]);
    }

    static short getShortB(byte[] bb, int bi) {
        return makeShort(bb[bi],
                         bb[bi + 1]);
    }

    static short getShort(byte[] bb, int bi, boolean bigEndian) {
        return (bigEndian ? getShortB(bb, bi) : getShortL(bb, bi));
    }

    static byte short1(short x) { return (byte)(x >> 8); }
    static byte short0(short x) { return (byte)(x); }

    static void putShortL(byte[] bb, int bi, short x) {
        bb[bi] = short0(x);
        bb[bi + 1] = short1(x);
    }

    static void putShortB(byte[] bb, int bi, short x) {
        bb[bi] = short1(x);
        bb[bi + 1] =  short0(x);
    }

    static void putShort(byte[] bb, int bi, short x, boolean bigEndian) {
        if (bigEndian)
            putShortB(bb, bi, x);
        else
            putShortL(bb, bi, x);
    }


    // -- get/put int --

    static int makeInt(byte b3, byte b2, byte b1, byte b0) {
        return (((b3 & 0xff) << 24) |
                ((b2 & 0xff) << 16) |
                ((b1 & 0xff) <<  8) |
                ((b0 & 0xff)));
    }

    static int getIntL(byte[] bb, int bi) {
        return makeInt(bb[bi + 3],
                       bb[bi + 2],
                       bb[bi + 1],
                       bb[bi]);
    }

    static int getIntB(byte[] bb, int bi) {
        return makeInt(bb[bi],
                       bb[bi + 1],
                       bb[bi + 2],
                       bb[bi + 3]);
    }

    static int getInt(byte[] bb, int bi, boolean bigEndian) {
        return (bigEndian ? getIntB(bb, bi) : getIntL(bb, bi));
    }

    static byte int3(int x) { return (byte)(x >> 24); }
    static byte int2(int x) { return (byte)(x >> 16); }
    static byte int1(int x) { return (byte)(x >>  8); }
    static byte int0(int x) { return (byte)(x); }

    static void putIntL(byte[] bb, int bi, int x) {
        bb[bi + 3] = int3(x);
        bb[bi + 2] = int2(x);
        bb[bi + 1] = int1(x);
        bb[bi] = int0(x);
    }

    static void putIntB(byte[] bb, int bi, int x) {
        bb[bi] = int3(x);
        bb[bi + 1] = int2(x);
        bb[bi + 2] = int1(x);
        bb[bi + 3] = int0(x);
    }

    static void putInt(byte[] bb, int bi, int x, boolean bigEndian) {
        if (bigEndian)
            putIntB(bb, bi, x);
        else
            putIntL(bb, bi, x);
    }

    // -- get/put long --

    static long makeLong(byte b7, byte b6, byte b5, byte b4,
                                 byte b3, byte b2, byte b1, byte b0) {
        return ((((long)b7 & 0xff) << 56) |
                (((long)b6 & 0xff) << 48) |
                (((long)b5 & 0xff) << 40) |
                (((long)b4 & 0xff) << 32) |
                (((long)b3 & 0xff) << 24) |
                (((long)b2 & 0xff) << 16) |
                (((long)b1 & 0xff) <<  8) |
                (((long)b0 & 0xff)));
    }

    static long getLongL(byte[] bb, int bi) {
        return makeLong(bb[bi + 7],
                        bb[bi + 6],
                        bb[bi + 5],
                        bb[bi + 4],
                        bb[bi + 3],
                        bb[bi + 2],
                        bb[bi + 1],
                        bb[bi]);
    }

    static long getLongB(byte[] bb, int bi) {
        return makeLong(bb[bi],
                        bb[bi + 1],
                        bb[bi + 2],
                        bb[bi + 3],
                        bb[bi + 4],
                        bb[bi + 5],
                        bb[bi + 6],
                        bb[bi + 7]);
    }

    static long getLong(byte[] bb, int bi, boolean bigEndian) {
        return (bigEndian ? getLongB(bb, bi) : getLongL(bb, bi));
    }

    static byte long7(long x) { return (byte)(x >> 56); }
    static byte long6(long x) { return (byte)(x >> 48); }
    static byte long5(long x) { return (byte)(x >> 40); }
    static byte long4(long x) { return (byte)(x >> 32); }
    static byte long3(long x) { return (byte)(x >> 24); }
    static byte long2(long x) { return (byte)(x >> 16); }
    static byte long1(long x) { return (byte)(x >>  8); }
    static byte long0(long x) { return (byte)(x); }

    static void putLongL(byte[] bb, int bi, long x) {
        bb[bi + 7] = long7(x);
        bb[bi + 6] = long6(x);
        bb[bi + 5] = long5(x);
        bb[bi + 4] = long4(x);
        bb[bi + 3] =  long3(x);
        bb[bi + 2] =  long2(x);
        bb[bi + 1] =  long1(x);
        bb[bi] =  long0(x);
    }

    static void putLongB(byte[] bb, int bi, long x) {
        bb[bi] = long7(x);
        bb[bi + 1] = long6(x);
        bb[bi + 2] = long5(x);
        bb[bi + 3] = long4(x);
        bb[bi + 4] = long3(x);
        bb[bi + 5] = long2(x);
        bb[bi + 6] = long1(x);
        bb[bi + 7] = long0(x);
    }

    static void putLong(byte[] bb, int bi, long x, boolean bigEndian) {
        if (bigEndian)
            putLongB(bb, bi, x);
        else
            putLongL(bb, bi, x);
    }

    // -- get/put float --

    static float getFloatL(byte[] bb, int bi) {
        return Float.intBitsToFloat(getIntL(bb, bi));
    }

    static float getFloatB(byte[] bb, int bi) {
        return Float.intBitsToFloat(getIntB(bb, bi));
    }

    static float getFloat(byte[] bb, int bi, boolean bigEndian) {
        return (bigEndian ? getFloatB(bb, bi) : getFloatL(bb, bi));
    }

    static void putFloatL(byte[] bb, int bi, float x) {
        putIntL(bb, bi, Float.floatToRawIntBits(x));
    }

    static void putFloatB(byte[] bb, int bi, float x) {
        putIntB(bb, bi, Float.floatToRawIntBits(x));
    }

    static void putFloat(byte[] bb, int bi, float x, boolean bigEndian) {
        if (bigEndian)
            putFloatB(bb, bi, x);
        else
            putFloatL(bb, bi, x);
    }

    // -- get/put double --

    static double getDoubleL(byte[] bb, int bi) {
        return Double.longBitsToDouble(getLongL(bb, bi));
    }

    static double getDoubleB(byte[] bb, int bi) {
        return Double.longBitsToDouble(getLongB(bb, bi));
    }

    static double getDouble(byte[] bb, int bi, boolean bigEndian) {
        return (bigEndian ? getDoubleB(bb, bi) : getDoubleL(bb, bi));
    }

    static void putDoubleL(byte[] bb, int bi, double x) {
        putLongL(bb, bi, Double.doubleToRawLongBits(x));
    }

    static void putDoubleB(byte[] bb, int bi, double x) {
        putLongB(bb, bi, Double.doubleToRawLongBits(x));
    }

    static void putDouble(byte[] bb, int bi, double x, boolean bigEndian) {
        if (bigEndian)
            putDoubleB(bb, bi, x);
        else
            putDoubleL(bb, bi, x);
    }

}
