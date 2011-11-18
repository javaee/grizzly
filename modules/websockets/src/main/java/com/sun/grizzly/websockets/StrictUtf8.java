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

package com.sun.grizzly.websockets;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;

/* Legal UTF-8 Byte Sequences
 *
 * #    Code Points      Bits   Bit/Byte pattern
 * 1                     7      0xxxxxxx
 *      U+0000..U+007F          00..7F
 *
 * 2                     11     110xxxxx    10xxxxxx
 *      U+0080..U+07FF          C2..DF      80..BF
 *
 * 3                     16     1110xxxx    10xxxxxx    10xxxxxx
 *      U+0800..U+0FFF          E0          A0..BF      80..BF
 *      U+1000..U+FFFF          E1..EF      80..BF      80..BF
 *
 * 4                     21     11110xxx    10xxxxxx    10xxxxxx    10xxxxxx
 *     U+10000..U+3FFFF         F0          90..BF      80..BF      80..BF
 *     U+40000..U+FFFFF         F1..F3      80..BF      80..BF      80..BF
 *    U+100000..U10FFFF         F4          80..8F      80..BF      80..BF
 *
 */

public class StrictUtf8 extends Charset
{
    public StrictUtf8() {
        super("StrictUtf8", new String[] {});
    }

    public CharsetDecoder newDecoder() {
        return new Decoder(this);
    }

    public CharsetEncoder newEncoder() {
        return new Encoder(this);
    }

    @Override
    public boolean contains(Charset cs) {
        return "StrictUtf8".equals(cs.name());
    }
    
    private static boolean isSurrogate(char ch) {
        return ch >= Character.MIN_SURROGATE && ch < (Character.MAX_SURROGATE + 1);
    }

    private static char highSurrogate(int codePoint) {
        return (char) ((codePoint >>> 10)
                + (Character.MIN_HIGH_SURROGATE - (Character.MIN_SUPPLEMENTARY_CODE_POINT >>> 10)));
    }
    
    public static char lowSurrogate(int codePoint) {
        return (char) ((codePoint & 0x3ff) + Character.MIN_LOW_SURROGATE);
    }

    private static void updatePositions(ByteBuffer src, int sp,
                                        CharBuffer dst, int dp) {
        src.position(sp - src.arrayOffset());
        dst.position(dp - dst.arrayOffset());
    }

    private static void updatePositions(CharBuffer src, int sp,
                                        ByteBuffer dst, int dp) {
        src.position(sp - src.arrayOffset());
        dst.position(dp - dst.arrayOffset());
    }

    private static class Decoder extends CharsetDecoder
                                 implements ArrayDecoder {
        private Decoder(Charset cs) {
            super(cs, 1.0f, 1.0f);
        }

        private static boolean isNotContinuation(int b) {
            return (b & 0xc0) != 0x80;
        }

        // decode() now checks the first byte of 2-byte sequence as
        //     if ((b1 >> 5) == -2 && (b1 & 0x1e) != 0)
        //     ...
        // no longer need to check b1 against c1 & c0 for malformed
        //
        //  [C2..DF] [80..BF]
        //private static boolean isMalformed2(int b1, int b2) {
        //    return (b1 & 0x1e) == 0x0 || (b2 & 0xc0) != 0x80;
        //}


        //  [E0]     [A0..BF] [80..BF]
        //  [E1..EF] [80..BF] [80..BF]
        private static boolean isMalformed3(int b1, int b2, int b3) {
            return (b1 == (byte)0xe0 && (b2 & 0xe0) == 0x80) ||
                   (b2 & 0xc0) != 0x80 || (b3 & 0xc0) != 0x80;
        }

        // only used when there is only one byte left in src buffer
        private static boolean isMalformed3_2(int b1, int b2) {
            return (b1 == (byte)0xe0 && (b2 & 0xe0) == 0x80) ||
                   (b2 & 0xc0) != 0x80;
        }


        //  [F0]     [90..BF] [80..BF] [80..BF]
        //  [F1..F3] [80..BF] [80..BF] [80..BF]
        //  [F4]     [80..8F] [80..BF] [80..BF]
        //  only check 80-be range here, the [0xf0,0x80...] and [0xf4,0x90-...]
        //  will be checked by Character.isSupplementaryCodePoint(uc)
        private static boolean isMalformed4(int b2, int b3, int b4) {
            return (b2 & 0xc0) != 0x80 || (b3 & 0xc0) != 0x80 ||
                   (b4 & 0xc0) != 0x80;
        }

        // only used when there is less than 4 bytes left in src buffer
        private static boolean isMalformed4_2(int b1, int b2) {
            return (b1 == 0xf0 && b2 == 0x90) ||
                   (b2 & 0xc0) != 0x80;
        }

        private static boolean isMalformed4_3(int b3) {
            return (b3 & 0xc0) != 0x80;
        }

        private static CoderResult malformedN(ByteBuffer src, int nb) {
            switch (nb) {
            case 1:
            case 2:                    // always 1
                return CoderResult.malformedForLength(1);
            case 3:
                int b1 = src.get();
                int b2 = src.get();    // no need to lookup b3
                return CoderResult.malformedForLength(
                    ((b1 == (byte)0xe0 && (b2 & 0xe0) == 0x80) ||
                     isNotContinuation(b2)) ? 1 : 2);
            case 4:  // we don't care the speed here
                b1 = src.get() & 0xff;
                b2 = src.get() & 0xff;
                if (b1 > 0xf4 ||
                    (b1 == 0xf0 && (b2 < 0x90 || b2 > 0xbf)) ||
                    (b1 == 0xf4 && (b2 & 0xf0) != 0x80) ||
                    isNotContinuation(b2))
                    return CoderResult.malformedForLength(1);
                if (isNotContinuation(src.get()))
                    return CoderResult.malformedForLength(2);
                return CoderResult.malformedForLength(3);
            default:
                assert false;
                return null;
            }
        }

        private static CoderResult malformed(ByteBuffer src, int sp,
                                             CharBuffer dst, int dp,
                                             int nb)
        {
            src.position(sp - src.arrayOffset());
            CoderResult cr = malformedN(src, nb);
            updatePositions(src, sp, dst, dp);
            return cr;
        }

 
        private static CoderResult malformed(ByteBuffer src,
                                             int mark, int nb)
        {
            src.position(mark);
            CoderResult cr = malformedN(src, nb);
            src.position(mark);
            return cr;
        }

        private static CoderResult malformedForLength(ByteBuffer src,
                                                      int sp,
                                                      CharBuffer dst,
                                                      int dp,
                                                      int malformedNB)
        {
            updatePositions(src, sp, dst, dp);
            return CoderResult.malformedForLength(malformedNB);
        }

        private static CoderResult malformedForLength(ByteBuffer src,
                                                      int mark,
                                                      int malformedNB)
        {
            src.position(mark);
            return CoderResult.malformedForLength(malformedNB);
        }


        private static CoderResult xflow(ByteBuffer src, int sp, int sl,
                                         CharBuffer dst, int dp, int nb) {
            updatePositions(src, sp, dst, dp);
            return (nb == 0 || sl - sp < nb)
                   ? CoderResult.UNDERFLOW : CoderResult.OVERFLOW;
        }

        private static CoderResult xflow(Buffer src, int mark, int nb) {
            src.position(mark);
            return (nb == 0 || src.remaining() < nb)
                   ? CoderResult.UNDERFLOW : CoderResult.OVERFLOW;
        }

        private CoderResult decodeArrayLoop(ByteBuffer src,
                                            CharBuffer dst)
        {
            // This method is optimized for ASCII input.
            byte[] sa = src.array();
            int sp = src.arrayOffset() + src.position();
            int sl = src.arrayOffset() + src.limit();

            char[] da = dst.array();
            int dp = dst.arrayOffset() + dst.position();
            int dl = dst.arrayOffset() + dst.limit();
            int dlASCII = dp + Math.min(sl - sp, dl - dp);

            // ASCII only loop
            while (dp < dlASCII && sa[sp] >= 0)
                da[dp++] = (char) sa[sp++];
            while (sp < sl) {
                int b1 = sa[sp];
                if (b1 >= 0) {
                    // 1 byte, 7 bits: 0xxxxxxx
                    if (dp >= dl)
                        return xflow(src, sp, sl, dst, dp, 1);
                    da[dp++] = (char) b1;
                    sp++;
                } else if ((b1 >> 5) == -2 && (b1 & 0x1e) != 0) {
                    // 2 bytes, 11 bits: 110xxxxx 10xxxxxx
                    if (sl - sp < 2 || dp >= dl)
                        return xflow(src, sp, sl, dst, dp, 2);
                    int b2 = sa[sp + 1];
                    if (isNotContinuation(b2))
                        return malformedForLength(src, sp, dst, dp, 1);
                    da[dp++] = (char) (((b1 << 6) ^ b2)
                                       ^
                                       (((byte) 0xC0 << 6) ^
                                        ((byte) 0x80)));
                    sp += 2;
                } else if ((b1 >> 4) == -2) {
                    // 3 bytes, 16 bits: 1110xxxx 10xxxxxx 10xxxxxx
                    int srcRemaining = sl - sp;
                    if (srcRemaining < 3 || dp >= dl) {
                        if (srcRemaining > 1 && isMalformed3_2(b1, sa[sp + 1]))
                            return malformedForLength(src, sp, dst, dp, 1);
                        return xflow(src, sp, sl, dst, dp, 3);
                    }
                    int b2 = sa[sp + 1];
                    int b3 = sa[sp + 2];
                    if (isMalformed3(b1, b2, b3))
                        return malformed(src, sp, dst, dp, 3);
                    char c = (char)
                        ((b1 << 12) ^
                         (b2 <<  6) ^
                         (b3 ^
                          (((byte) 0xE0 << 12) ^
                           ((byte) 0x80 <<  6) ^
                           ((byte) 0x80))));
                    if (isSurrogate(c))
                        return malformedForLength(src, sp, dst, dp, 3);
                    da[dp++] = c;
                    sp += 3;
                } else if ((b1 >> 3) == -2) {
                    // 4 bytes, 21 bits: 11110xxx 10xxxxxx 10xxxxxx 10xxxxxx
                    int srcRemaining = sl - sp;
                    if (srcRemaining < 4 || dl - dp < 2) {  
                        if (srcRemaining > 1 && isMalformed4_2(b1, sa[sp + 1]))
                            return malformedForLength(src, sp, dst, dp, 1);
                        if (srcRemaining > 2 && isMalformed4_3(sa[sp + 2]))
                            return malformedForLength(src, sp, dst, dp, 2);
                        return xflow(src, sp, sl, dst, dp, 4);
                    }
                    int b2 = sa[sp + 1];
                    int b3 = sa[sp + 2];
                    int b4 = sa[sp + 3];
                    int uc = ((b1 << 18) ^
                              (b2 << 12) ^
                              (b3 <<  6) ^
                              (b4 ^
                               (((byte) 0xF0 << 18) ^
                                ((byte) 0x80 << 12) ^
                                ((byte) 0x80 <<  6) ^
                                ((byte) 0x80))));
                    if (isMalformed4(b2, b3, b4) ||
                        // shortest form check
                        !Character.isSupplementaryCodePoint(uc)) {
                        return malformed(src, sp, dst, dp, 4);
                    }
                    da[dp++] = highSurrogate(uc);
                    da[dp++] = lowSurrogate(uc);
                    sp += 4;
                } else
                    return malformed(src, sp, dst, dp, 1);
            }
            return xflow(src, sp, sl, dst, dp, 0);
        }

        private CoderResult decodeBufferLoop(ByteBuffer src,
                                             CharBuffer dst)
        {
            int mark = src.position();
            int limit = src.limit();
            while (mark < limit) {
                int b1 = src.get();
                if (b1 >= 0) {
                    // 1 byte, 7 bits: 0xxxxxxx
                    if (dst.remaining() < 1)
                        return xflow(src, mark, 1); // overflow
                    dst.put((char) b1);
                    mark++;
                } else if ((b1 >> 5) == -2 && (b1 & 0x1e) != 0) {
                    // 2 bytes, 11 bits: 110xxxxx 10xxxxxx
                    if (limit - mark < 2|| dst.remaining() < 1)
                        return xflow(src, mark, 2);
                    int b2 = src.get();
                    if (isNotContinuation(b2))
                        return malformedForLength(src, mark, 1);
                     dst.put((char) (((b1 << 6) ^ b2)
                                    ^
                                    (((byte) 0xC0 << 6) ^
                                     ((byte) 0x80))));
                    mark += 2;
                } else if ((b1 >> 4) == -2) {
                    // 3 bytes, 16 bits: 1110xxxx 10xxxxxx 10xxxxxx
                    int srcRemaining = limit - mark;
                    if (srcRemaining < 3 || dst.remaining() < 1) {
                        if (srcRemaining > 1 && isMalformed3_2(b1, src.get()))
                            return malformedForLength(src, mark, 1);
                        return xflow(src, mark, 3);
                    }
                    int b2 = src.get();
                    int b3 = src.get();
                    if (isMalformed3(b1, b2, b3))
                        return malformed(src, mark, 3);
                    char c = (char)
                        ((b1 << 12) ^
                         (b2 <<  6) ^
                         (b3 ^
                          (((byte) 0xE0 << 12) ^
                           ((byte) 0x80 <<  6) ^
                           ((byte) 0x80))));
                    if (isSurrogate(c))
                        return malformedForLength(src, mark, 3);
                    dst.put(c);
                    mark += 3;
                } else if ((b1 >> 3) == -2) {
                    // 4 bytes, 21 bits: 11110xxx 10xxxxxx 10xxxxxx 10xxxxxx
                    int srcRemaining = limit - mark;
                    if (srcRemaining < 4 || dst.remaining() < 2) {
                        if (srcRemaining > 1 && isMalformed4_2(b1, src.get()))
                            return malformedForLength(src, mark, 1);
                        if (srcRemaining > 2 && isMalformed4_3(src.get()))
                            return malformedForLength(src, mark, 2);
                        return xflow(src, mark, 4);
                    }
                    int b2 = src.get();
                    int b3 = src.get();
                    int b4 = src.get();
                    int uc = ((b1 << 18) ^
                              (b2 << 12) ^
                              (b3 <<  6) ^
                              (b4 ^
                               (((byte) 0xF0 << 18) ^
                                ((byte) 0x80 << 12) ^
                                ((byte) 0x80 <<  6) ^
                                ((byte) 0x80))));
                    if (isMalformed4(b2, b3, b4) ||
                        // shortest form check
                        !Character.isSupplementaryCodePoint(uc)) {
                        return malformed(src, mark, 4);
                    }
                    dst.put(highSurrogate(uc));
                    dst.put(lowSurrogate(uc));
                    mark += 4;
                } else {
                    return malformed(src, mark, 1);
                }
            }
            return xflow(src, mark, 0);
        }

        protected CoderResult decodeLoop(ByteBuffer src,
                                         CharBuffer dst)
        {
            if (src.hasArray() && dst.hasArray())
                return decodeArrayLoop(src, dst);
            else
                return decodeBufferLoop(src, dst);
        }

        private static ByteBuffer getByteBuffer(ByteBuffer bb, byte[] ba, int sp)
        {
            if (bb == null)
                bb = ByteBuffer.wrap(ba);
            bb.position(sp);
            return bb;
        }

        // returns -1 if there is/are malformed byte(s) and the
        // "action" for malformed input is not REPLACE.
        public int decode(byte[] sa, int sp, int len, char[] da) {
            final int sl = sp + len;
            int dp = 0;
            int dlASCII = Math.min(len, da.length);
            ByteBuffer bb = null;  // only necessary if malformed

            // ASCII only optimized loop
            while (dp < dlASCII && sa[sp] >= 0)
                da[dp++] = (char) sa[sp++];

            while (sp < sl) {
                int b1 = sa[sp++];
                if (b1 >= 0) {
                    // 1 byte, 7 bits: 0xxxxxxx
                    da[dp++] = (char) b1;
                } else if ((b1 >> 5) == -2 && (b1 & 0x1e) != 0) {
                    // 2 bytes, 11 bits: 110xxxxx 10xxxxxx
                    if (sp < sl) {
                        int b2 = sa[sp++];
                        if (isNotContinuation(b2)) {
                            if (malformedInputAction() != CodingErrorAction.REPLACE)
                                return -1;
                            da[dp++] = replacement().charAt(0);
                            sp--;            // malformedN(bb, 2) always returns 1
                        } else {
                            da[dp++] = (char) (((b1 << 6) ^ b2)^
                                           (((byte) 0xC0 << 6) ^
                                            ((byte) 0x80)));
                        }
                        continue;
                    }
                    if (malformedInputAction() != CodingErrorAction.REPLACE)
                        return -1;
                    da[dp++] = replacement().charAt(0);
                    return dp;
                } else if ((b1 >> 4) == -2) {
                    // 3 bytes, 16 bits: 1110xxxx 10xxxxxx 10xxxxxx
                    if (sp + 1 < sl) {
                        int b2 = sa[sp++];
                        int b3 = sa[sp++];
                        if (isMalformed3(b1, b2, b3)) {
                            if (malformedInputAction() != CodingErrorAction.REPLACE)
                                return -1;
                            da[dp++] = replacement().charAt(0);
                            sp -= 3;
                            bb = getByteBuffer(bb, sa, sp);
                            sp += malformedN(bb, 3).length();
                        } else {
                            char c = (char)((b1 << 12) ^
                                              (b2 <<  6) ^
                                              (b3 ^
                                              (((byte) 0xE0 << 12) ^
                                              ((byte) 0x80 <<  6) ^
                                              ((byte) 0x80))));
                            if (isSurrogate(c)) {
                                if (malformedInputAction() != CodingErrorAction.REPLACE)
                                    return -1;
                                da[dp++] = replacement().charAt(0);
                            } else {
                                da[dp++] = c;
                            }
                        }
                        continue;
                    }
                    if (malformedInputAction() != CodingErrorAction.REPLACE)
                        return -1;
                    if (sp  < sl && isMalformed3_2(b1, sa[sp])) {
                        da[dp++] = replacement().charAt(0);
                        continue;

                    }
                    da[dp++] = replacement().charAt(0);
                    return dp;
                } else if ((b1 >> 3) == -2) {
                    // 4 bytes, 21 bits: 11110xxx 10xxxxxx 10xxxxxx 10xxxxxx
                    if (sp + 2 < sl) {
                        int b2 = sa[sp++];
                        int b3 = sa[sp++];
                        int b4 = sa[sp++];
                        int uc = ((b1 << 18) ^
                                  (b2 << 12) ^
                                  (b3 <<  6) ^
                                  (b4 ^
                                   (((byte) 0xF0 << 18) ^
                                   ((byte) 0x80 << 12) ^
                                   ((byte) 0x80 <<  6) ^
                                   ((byte) 0x80))));
                        if (isMalformed4(b2, b3, b4) ||
                            // shortest form check
                            !Character.isSupplementaryCodePoint(uc)) {
                            if (malformedInputAction() != CodingErrorAction.REPLACE)
                                return -1;
                            da[dp++] = replacement().charAt(0);
                            sp -= 4;
                            bb = getByteBuffer(bb, sa, sp);
                            sp += malformedN(bb, 4).length();
                        } else {
                            da[dp++] = highSurrogate(uc);
                            da[dp++] = lowSurrogate(uc);
                        }
                        continue;
                    }
                    if (malformedInputAction() != CodingErrorAction.REPLACE)
                        return -1;

                    if (sp  < sl && isMalformed4_2(b1, sa[sp])) {
                        da[dp++] = replacement().charAt(0);
                        continue;
                    }
                    sp++;
                    if (sp  < sl && isMalformed4_3(sa[sp])) {
                        da[dp++] = replacement().charAt(0);
                        continue;
                    }
                    da[dp++] = replacement().charAt(0);
                    return dp;
                } else {
                    if (malformedInputAction() != CodingErrorAction.REPLACE)
                        return -1;
                    da[dp++] = replacement().charAt(0);
                }
            }
            return dp;
        }
    }

    protected static final class Encoder extends CharsetEncoder
                                 implements ArrayEncoder {

        private Encoder(Charset cs) {
            super(cs, 1.1f, 3.0f);
        }

        public boolean canEncode(char c) {
            return !isSurrogate(c);
        }

        public boolean isLegalReplacement(byte[] repl) {
            return ((repl.length == 1 && repl[0] >= 0) ||
                    super.isLegalReplacement(repl));
        }

        private static CoderResult overflow(CharBuffer src, int sp,
                                            ByteBuffer dst, int dp) {
            updatePositions(src, sp, dst, dp);
            return CoderResult.OVERFLOW;
        }

        private static CoderResult overflow(CharBuffer src, int mark) {
            src.position(mark);
            return CoderResult.OVERFLOW;
        }

        private Parser sgp;
        private CoderResult encodeArrayLoop(CharBuffer src,
                                            ByteBuffer dst)
        {
            char[] sa = src.array();
            int sp = src.arrayOffset() + src.position();
            int sl = src.arrayOffset() + src.limit();

            byte[] da = dst.array();
            int dp = dst.arrayOffset() + dst.position();
            int dl = dst.arrayOffset() + dst.limit();
            int dlASCII = dp + Math.min(sl - sp, dl - dp);

            // ASCII only loop
            while (dp < dlASCII && sa[sp] < '\u0080')
                da[dp++] = (byte) sa[sp++];
            while (sp < sl) {
                char c = sa[sp];
                if (c < 0x80) {
                    // Have at most seven bits
                    if (dp >= dl)
                        return overflow(src, sp, dst, dp);
                    da[dp++] = (byte)c;
                } else if (c < 0x800) {
                    // 2 bytes, 11 bits
                    if (dl - dp < 2)
                        return overflow(src, sp, dst, dp);
                    da[dp++] = (byte)(0xc0 | (c >> 6));
                    da[dp++] = (byte)(0x80 | (c & 0x3f));
                } else if (isSurrogate(c)) {
                    // Have a surrogate pair
                    if (sgp == null)
                        sgp = new Parser();
                    int uc = sgp.parse(c, sa, sp, sl);
                    if (uc < 0) {
                        updatePositions(src, sp, dst, dp);
                        return sgp.error();
                    }
                    if (dl - dp < 4)
                        return overflow(src, sp, dst, dp);
                    da[dp++] = (byte)(0xf0 | ((uc >> 18)));
                    da[dp++] = (byte)(0x80 | ((uc >> 12) & 0x3f));
                    da[dp++] = (byte)(0x80 | ((uc >>  6) & 0x3f));
                    da[dp++] = (byte)(0x80 | (uc & 0x3f));
                    sp++;  // 2 chars
                } else {
                    // 3 bytes, 16 bits
                    if (dl - dp < 3)
                        return overflow(src, sp, dst, dp);
                    da[dp++] = (byte)(0xe0 | ((c >> 12)));
                    da[dp++] = (byte)(0x80 | ((c >>  6) & 0x3f));
                    da[dp++] = (byte)(0x80 | (c & 0x3f));
                }
                sp++;
            }
            updatePositions(src, sp, dst, dp);
            return CoderResult.UNDERFLOW;
        }

        private CoderResult encodeBufferLoop(CharBuffer src,
                                             ByteBuffer dst)
        {
            int mark = src.position();
            while (src.hasRemaining()) {
                char c = src.get();
                if (c < 0x80) {
                    // Have at most seven bits
                    if (!dst.hasRemaining())
                        return overflow(src, mark);
                    dst.put((byte)c);
                } else if (c < 0x800) {
                    // 2 bytes, 11 bits
                    if (dst.remaining() < 2)
                        return overflow(src, mark);
                    dst.put((byte)(0xc0 | (c >> 6)));
                    dst.put((byte)(0x80 | (c & 0x3f)));
                } else if (isSurrogate(c)) {
                    // Have a surrogate pair
                    if (sgp == null)
                        sgp = new Parser();
                    int uc = sgp.parse(c, src);
                    if (uc < 0) {
                        src.position(mark);
                        return sgp.error();
                    }
                    if (dst.remaining() < 4)
                        return overflow(src, mark);
                    dst.put((byte)(0xf0 | ((uc >> 18))));
                    dst.put((byte)(0x80 | ((uc >> 12) & 0x3f)));
                    dst.put((byte)(0x80 | ((uc >>  6) & 0x3f)));
                    dst.put((byte)(0x80 | (uc & 0x3f)));
                    mark++;  // 2 chars
                } else {
                    // 3 bytes, 16 bits
                    if (dst.remaining() < 3)
                        return overflow(src, mark);
                    dst.put((byte)(0xe0 | ((c >> 12))));
                    dst.put((byte)(0x80 | ((c >>  6) & 0x3f)));
                    dst.put((byte)(0x80 | (c & 0x3f)));
                }
                mark++;
            }
            src.position(mark);
            return CoderResult.UNDERFLOW;
        }

        protected final CoderResult encodeLoop(CharBuffer src,
                                               ByteBuffer dst)
        {
            if (src.hasArray() && dst.hasArray())
                return encodeArrayLoop(src, dst);
            else
                return encodeBufferLoop(src, dst);
        }

        // returns -1 if there is malformed char(s) and the
        // "action" for malformed input is not REPLACE.
        public int encode(char[] sa, int sp, int len, byte[] da) {
            int sl = sp + len;
            int dp = 0;
            int dlASCII = dp + Math.min(len, da.length);

            // ASCII only optimized loop
            while (dp < dlASCII && sa[sp] < '\u0080')
                da[dp++] = (byte) sa[sp++];

            while (sp < sl) {
                char c = sa[sp++];
                if (c < 0x80) {
                    // Have at most seven bits
                    da[dp++] = (byte)c;
                } else if (c < 0x800) {
                    // 2 bytes, 11 bits
                    da[dp++] = (byte)(0xc0 | (c >> 6));
                    da[dp++] = (byte)(0x80 | (c & 0x3f));
                } else if (isSurrogate(c)) {
                    if (sgp == null)
                        sgp = new Parser();
                    int uc = sgp.parse(c, sa, sp - 1, sl);
                    if (uc < 0) {
                        if (malformedInputAction() != CodingErrorAction.REPLACE)
                            return -1;
                        da[dp++] = replacement()[0];
                    } else {
                        da[dp++] = (byte)(0xf0 | ((uc >> 18)));
                        da[dp++] = (byte)(0x80 | ((uc >> 12) & 0x3f));
                        da[dp++] = (byte)(0x80 | ((uc >>  6) & 0x3f));
                        da[dp++] = (byte)(0x80 | (uc & 0x3f));
                        sp++;  // 2 chars
                    }
                } else {
                    // 3 bytes, 16 bits
                    da[dp++] = (byte)(0xe0 | ((c >> 12)));
                    da[dp++] = (byte)(0x80 | ((c >>  6) & 0x3f));
                    da[dp++] = (byte)(0x80 | (c & 0x3f));
                }
            }
            return dp;
        }
    }

    /**
     * Surrogate parsing support.  Charset implementations may use instances of
     * this class to handle the details of parsing UTF-16 surrogate pairs.
     */
    public static class Parser {

        private int character;          // UCS-4
        private CoderResult error = CoderResult.UNDERFLOW;

        /**
         * If the previous parse operation detected an error, return the object
         * describing that error.
         */
        public CoderResult error() {
            assert (error != null);
            return error;
        }

        /**
         * Parses a UCS-4 character from the given source buffer, handling
         * surrogates.
         *
         * @param c  The first character
         * @param in The source buffer, from which one more character
         *           will be consumed if c is a high surrogate
         * @returns Either a parsed UCS-4 character, in which case the isPair()
         * and increment() methods will return meaningful values, or
         * -1, in which case error() will return a descriptive result
         * object
         */
        public int parse(char c, CharBuffer in) {
            if (Character.isHighSurrogate(c)) {
                if (!in.hasRemaining()) {
                    error = CoderResult.UNDERFLOW;
                    return -1;
                }
                char d = in.get();
                if (Character.isLowSurrogate(d)) {
                    character = Character.toCodePoint(c, d);
                    error = null;
                    return character;
                }
                error = CoderResult.malformedForLength(1);
                return -1;
            }
            if (Character.isLowSurrogate(c)) {
                error = CoderResult.malformedForLength(1);
                return -1;
            }
            character = c;
            error = null;
            return character;
        }

        /**
         * Parses a UCS-4 character from the given source buffer, handling
         * surrogates.
         *
         * @param c  The first character
         * @param ia The input array, from which one more character
         *           will be consumed if c is a high surrogate
         * @param ip The input index
         * @param il The input limit
         * @returns Either a parsed UCS-4 character, in which case the isPair()
         * and increment() methods will return meaningful values, or
         * -1, in which case error() will return a descriptive result
         * object
         */
        public int parse(char c, char[] ia, int ip, int il) {
            assert (ia[ip] == c);
            if (Character.isHighSurrogate(c)) {
                if (il - ip < 2) {
                    error = CoderResult.UNDERFLOW;
                    return -1;
                }
                char d = ia[ip + 1];
                if (Character.isLowSurrogate(d)) {
                    character = Character.toCodePoint(c, d);
                    error = null;
                    return character;
                }
                error = CoderResult.malformedForLength(1);
                return -1;
            }
            if (Character.isLowSurrogate(c)) {
                error = CoderResult.malformedForLength(1);
                return -1;
            }
            character = c;
            error = null;
            return character;
        }

    }
}
