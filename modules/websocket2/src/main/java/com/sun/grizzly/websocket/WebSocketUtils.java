/*
 *
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2007-2009 Sun Microsystems, Inc. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License. You can obtain
 * a copy of the License at https://glassfish.dev.java.net/public/CDDL+GPL.html
 * or glassfish/bootstrap/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/bootstrap/legal/LICENSE.txt.
 * Sun designates this particular file as subject to the "Classpath" exception
 * as provided by Sun in the GPL Version 2 section of the License file that
 * accompanied this code.  If applicable, add the following below the License
 * Header, with the fields enclosed by brackets [] replaced by your own
 * identifying information: "Portions Copyrighted [year]
 * [name of copyright owner]"
 *
 * Contributor(s):
 *
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
 *
 */
package com.sun.grizzly.websocket;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;

/**
 * @author gustav trede
 */
final class WebSocketUtils {

    final static Charset utf8charset = Charset.forName("UTF8");

    /**
     * TODO: ensure we send correct version of unicode and map chars correctly as the websocket draft wants it.
     * <p/>
     * SERVER: Handling errors in UTF-8
     * When a server is to interpret a byte stream as UTF-8 but finds that
     * the byte stream is not in fact a valid UTF-8 stream, behaviour is
     * undefined. A server could close the connection, convert invalid byte
     * sequences to U+FFFD REPLACEMENT CHARACTERs, store the data verbatim,
     * or perform application-specific processing. Subprotocols layered on
     * the Web Socket protocol might define specific behavior for servers.
     * <p/>
     * CLIENT  Handling errors in UTF-8
     * When a client is to interpret a byte stream as UTF-8 but finds that
     * the byte stream is not in fact a valid UTF-8 stream, then any bytes
     * or sequences of bytes that are not valid UTF-8 sequences must be
     * interpreted as a U+FFFD REPLACEMENT CHARACTER.
     * <p/>
     * <p/>
     * TODO: Evaluate if its better to store the coders in a worker thread base
     * class instead ?. Perf is better, but it restricts usage to our specific
     * threads.
     */
    final static ThreadLocal<CharsetEncoder> utf8encoder = new ThreadLocal<CharsetEncoder>() {
        @Override
        protected CharsetEncoder initialValue() {
            return utf8charset.newEncoder();
        }
    };
    final static ThreadLocal<CharsetDecoder> utf8decoder = new ThreadLocal<CharsetDecoder>() {
        @Override
        protected CharsetDecoder initialValue() {
            return utf8charset.newDecoder();
        }
    };
    protected final static byte TEXT_TYPE = (byte) 0x00;
    protected final static byte BINARY_TYPE = (byte) 0x80;
    protected final static byte TEXT_TERMINATE = (byte) 0xff;


    static ByteBuffer encode(String textUTF8)
            throws CharacterCodingException {
        return encode(CharBuffer.wrap(textUTF8));
    }

    static ByteBuffer encode(CharBuffer cb)
            throws CharacterCodingException {
        ByteBuffer bb = ByteBuffer.allocate(5 + (cb.length() * 5 >> 2));
        bb.put(TEXT_TYPE);
        CharsetEncoder encoder = null;
        try {
            encoder = utf8encoder.get();
            do {
                CoderResult cr = encoder.encode(cb, bb, true);
                if (cr.isError()) {
                    cr.throwException();
                }
                if (cr.isOverflow()) {
                    bb.flip();
                    bb = ByteBuffer.allocate(bb.capacity() * 5 >> 2).put(bb);
                    continue;
                }
                if (!bb.hasRemaining()) { //should happen very infrequently.
                    bb.flip();
                    bb = ByteBuffer.allocate(bb.capacity() + 1).put(bb);
                }
                bb.put(TEXT_TERMINATE);
                bb.flip();
                return bb;
            } while (true);
        } finally {
            if (encoder != null) {
                encoder.reset();
            }
        }
    }

    static CharBuffer decode(ByteBuffer textframe)
            throws CharacterCodingException {
        textframe.position(textframe.position() + 1); //skip frametype byte.
        textframe.limit(textframe.limit() - 1); //skip frame terminal byte.
        CharBuffer cb = CharBuffer.allocate(1 + (textframe.remaining() * 3 >> 2));
        CharsetDecoder decoder = null;
        try {
            decoder = utf8decoder.get();
            do {
                CoderResult cr = decoder.decode(textframe, cb, true);
                if (cr.isError()) {
                    cr.throwException();
                }
                if (cr.isOverflow()) {
                    cb.flip();
                    cb = CharBuffer.allocate(cb.capacity() * 5 >> 2).put(cb);
                    continue;
                }
                cb.flip();
                return cb;
            } while (true);
        } finally {
            if (decoder != null) {
                decoder.reset();
            }
        }
    }


    private WebSocketUtils() {
    }


/* final static void updatePositions(Buffer src, int sp,
                                  Buffer dst, int dp) {
    src.position(sp - src.arrayOffset()); //arrayOffset not in jdk 1.5
    dst.position(dp - dst.arrayOffset()); //arrayOffset not in jdk 1.5
}

 //Copyright 2000-2009 Sun Microsystems, Inc.  All rights reserved.
 //SUN PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 //
 // copied from JDK7 b75 . the licence above is unchanged.
public final static class Decoder extends CharsetDecoder {
    private Decoder(Charset cs) {
        super(cs, 1.0f, 1.0f);
    }

    private static boolean isNotContinuation(int b) {
        return (b & 0xc0) != 0x80;
    }

    //  [C2..DF] [80..BF]
    private static boolean isMalformed2(int b1, int b2) {
        return (b1 & 0x1e) == 0x0 || (b2 & 0xc0) != 0x80;
    }

    //  [E0]     [A0..BF] [80..BF]
    //  [E1..EF] [80..BF] [80..BF]
    private static boolean isMalformed3(int b1, int b2, int b3) {
        return (b1 == (byte)0xe0 && (b2 & 0xe0) == 0x80) ||
               (b2 & 0xc0) != 0x80 || (b3 & 0xc0) != 0x80;
    }

    //  [F0]     [90..BF] [80..BF] [80..BF]
    //  [F1..F3] [80..BF] [80..BF] [80..BF]
    //  [F4]     [80..8F] [80..BF] [80..BF]
    //  only check 80-be range here, the [0xf0,0x80...] and [0xf4,0x90-...]
    //  will be checked by Surrogate.neededFor(uc)
    private static boolean isMalformed4(int b2, int b3, int b4) {
        return (b2 & 0xc0) != 0x80 || (b3 & 0xc0) != 0x80 ||
               (b4 & 0xc0) != 0x80;
    }

    private static CoderResult lookupN(ByteBuffer src, int n)
    {
        for (int i = 1; i < n; i++) {
           if (isNotContinuation(src.get()))
               return CoderResult.malformedForLength(i);
        }
        return CoderResult.malformedForLength(n);
    }

    private static CoderResult malformedN(ByteBuffer src, int nb) {
        switch (nb) {
        case 1:
            int b1 = src.get();
            if ((b1 >> 2) == -2) {
                // 5 bytes 111110xx 10xxxxxx 10xxxxxx 10xxxxxx 10xxxxxx
                if (src.remaining() < 4)
                    return CoderResult.UNDERFLOW;
                return lookupN(src, 5);
            }
            if ((b1 >> 1) == -2) {
                // 6 bytes 1111110x 10xxxxxx 10xxxxxx 10xxxxxx 10xxxxxx 10xxxxxx
                if (src.remaining() < 5)
                    return CoderResult.UNDERFLOW;
                return lookupN(src, 6);
            }
            return CoderResult.malformedForLength(1);
        case 2:                    // always 1
            return CoderResult.malformedForLength(1);
        case 3:
            b1 = src.get();
            int b2 = src.get();    // no need to lookup b3
            return CoderResult.malformedForLength(
                ((b1 == (byte)0xe0 && (b2 & 0xe0) == 0x80) ||
                 isNotContinuation(b2))?1:2);
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

    private static CoderResult xflow(Buffer src, int sp, int sl,
                                     Buffer dst, int dp, int nb) {
        updatePositions(src, sp, dst, dp);
        return (nb == 0 || sl - sp < nb)
               ?CoderResult.UNDERFLOW:CoderResult.OVERFLOW;
    }

    private static CoderResult xflow(Buffer src, int mark, int nb) {
        CoderResult cr = (nb == 0 || src.remaining() < (nb - 1))
                         ?CoderResult.UNDERFLOW:CoderResult.OVERFLOW;
        src.position(mark);
        return cr;
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
            da[dp++] = (char)sa[sp++];

        while (sp < sl) {
            int b1 = sa[sp];
            if (b1  >= 0) {
                // 1 byte, 7 bits: 0xxxxxxx
                if (dp >= dl)
                    return xflow(src, sp, sl, dst, dp, 1);
                da[dp++] = (char)b1;
                sp++;
            } else if ((b1 >> 5) == -2) {
                // 2 bytes, 11 bits: 110xxxxx 10xxxxxx
                if (sl - sp < 2 || dp >= dl)
                    return xflow(src, sp, sl, dst, dp, 2);
                int b2 = sa[sp + 1];
                if (isMalformed2(b1, b2))
                    return malformed(src, sp, dst, dp, 2);
                da[dp++] = (char) (((b1 << 6) ^ b2) ^ 0x0f80);
                sp += 2;
            } else if ((b1 >> 4) == -2) {
                // 3 bytes, 16 bits: 1110xxxx 10xxxxxx 10xxxxxx
                if (sl - sp < 3 || dp >= dl)
                    return xflow(src, sp, sl, dst, dp, 3);
                int b2 = sa[sp + 1];
                int b3 = sa[sp + 2];
                if (isMalformed3(b1, b2, b3))
                    return malformed(src, sp, dst, dp, 3);
                da[dp++] = (char) (((b1 << 12) ^ (b2 << 6) ^ b3) ^ 0x1f80);
                sp += 3;
            } else if ((b1 >> 3) == -2) {
                // 4 bytes, 21 bits: 11110xxx 10xxxxxx 10xxxxxx 10xxxxxx
                if (sl - sp < 4 || dl - dp < 2)
                    return xflow(src, sp, sl, dst, dp, 4);
                int b2 = sa[sp + 1];
                int b3 = sa[sp + 2];
                int b4 = sa[sp + 3];
                int uc = ((b1 & 0x07) << 18) |
                         ((b2 & 0x3f) << 12) |
                         ((b3 & 0x3f) << 06) |
                         (b4 & 0x3f);
                if (isMalformed4(b2, b3, b4) ||
                    !Surrogate.neededFor(uc)) {
                    return malformed(src, sp, dst, dp, 4);
                }
                da[dp++] = Surrogate.high(uc);
                da[dp++] = Surrogate.low(uc);
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
                    return xflow(src, mark, 1);  //overflow
                dst.put((char)b1);
                mark++;
            } else if ((b1 >> 5) == -2) {
                // 2 bytes, 11 bits: 110xxxxx 10xxxxxx
                if (limit - mark < 2|| dst.remaining() < 1)
                    return xflow(src, mark, 2);
                int b2 = src.get();
                if (isMalformed2(b1, b2))
                    return malformed(src, mark, 2);
                dst.put((char) (((b1 << 6) ^ b2) ^ 0x0f80));
                mark += 2;
            } else if ((b1 >> 4) == -2) {
                // 3 bytes, 16 bits: 1110xxxx 10xxxxxx 10xxxxxx
                if (limit - mark < 3 || dst.remaining() < 1)
                    return xflow(src, mark, 3);
                int b2 = src.get();
                int b3 = src.get();
                if (isMalformed3(b1, b2, b3))
                    return malformed(src, mark, 3);
                dst.put((char) (((b1 << 12) ^ (b2 << 6) ^ b3) ^ 0x1f80));
                mark += 3;
            } else if ((b1 >> 3) == -2) {
                // 4 bytes, 21 bits: 11110xxx 10xxxxxx 10xxxxxx 10xxxxxx
                if (limit - mark < 4 || dst.remaining() < 2)
                    return xflow(src, mark, 4);
                int b2 = src.get();
                int b3 = src.get();
                int b4 = src.get();
                int uc = ((b1 & 0x07) << 18) |
                         ((b2 & 0x3f) << 12) |
                         ((b3 & 0x3f) << 06) |
                         (b4 & 0x3f);
                if (isMalformed4(b2, b3, b4) ||
                    !Surrogate.neededFor(uc)) { // shortest form check
                    return malformed(src, mark, 4);
                }
                dst.put(Surrogate.high(uc));
                dst.put(Surrogate.low(uc));
                mark += 4;
            } else {
                return malformed(src, mark, 1);
            }
        }
        return xflow(src, mark, 0);
    }

    public CoderResult decodeLoop(ByteBuffer src,
                                     CharBuffer dst)
    {
        if (src.hasArray() && dst.hasArray())
            return decodeArrayLoop(src, dst);
        else
            return decodeBufferLoop(src, dst);
    }
}

 // Copyright 2000-2009 Sun Microsystems, Inc.  All rights reserved.
 // SUN PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 // copied from JDK7 b75 . the licence above is unchanged.
public final static class Encoder extends CharsetEncoder {

    private Encoder(Charset cs) {
        super(cs, 1.1f, 4.0f);
    }

    public boolean canEncode(char c) {
        return !Character.isSurrogate(c); //method not in jdk 1.5
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

    private Surrogate.Parser sgp;
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

        //ASCII only loop
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
                da[dp++] = (byte)(0xc0 | ((c >> 06)));
                da[dp++] = (byte)(0x80 | (c & 0x3f));
            } else if (Character.isSurrogate(c)) {//method not in jdk 1.5
                // Have a surrogate pair
                if (sgp == null)
                    sgp = new Surrogate.Parser();
                int uc = sgp.parse(c, sa, sp, sl);
                if (uc < 0) {
                    updatePositions(src, sp, dst, dp);
                    return sgp.error();
                }
                if (dl - dp < 4)
                    return overflow(src, sp, dst, dp);
                da[dp++] = (byte)(0xf0 | ((uc >> 18)));
                da[dp++] = (byte)(0x80 | ((uc >> 12) & 0x3f));
                da[dp++] = (byte)(0x80 | ((uc >> 06) & 0x3f));
                da[dp++] = (byte)(0x80 | (uc & 0x3f));
                sp++;  // 2 chars
            } else {
                // 3 bytes, 16 bits
                if (dl - dp < 3)
                    return overflow(src, sp, dst, dp);
                da[dp++] = (byte)(0xe0 | ((c >> 12)));
                da[dp++] = (byte)(0x80 | ((c >> 06) & 0x3f));
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
                dst.put((byte)(0xc0 | ((c >> 06))));
                dst.put((byte)(0x80 | (c & 0x3f)));
            } else if (Character.isSurrogate(c)) {//method not in jdk 1.5
                // Have a surrogate pair
                if (sgp == null)
                    sgp = new Surrogate.Parser();
                int uc = sgp.parse(c, src);
                if (uc < 0) {
                    src.position(mark);
                    return sgp.error();
                }
                if (dst.remaining() < 4)
                    return overflow(src, mark);
                dst.put((byte)(0xf0 | ((uc >> 18))));
                dst.put((byte)(0x80 | ((uc >> 12) & 0x3f)));
                dst.put((byte)(0x80 | ((uc >> 06) & 0x3f)));
                dst.put((byte)(0x80 | (uc & 0x3f)));
                mark++;  //2 chars
            } else {
                // 3 bytes, 16 bits
                if (dst.remaining() < 3)
                    return overflow(src, mark);
                dst.put((byte)(0xe0 | ((c >> 12))));
                dst.put((byte)(0x80 | ((c >> 06) & 0x3f)));
                dst.put((byte)(0x80 | (c & 0x3f)));
            }
            mark++;
        }
        src.position(mark);
        return CoderResult.UNDERFLOW;
    }

    public final CoderResult encodeLoop(CharBuffer src,
                                           ByteBuffer dst)
    {
        if (src.hasArray() && dst.hasArray())
            return encodeArrayLoop(src, dst);
        else
            return encodeBufferLoop(src, dst);
    }
}*/
}
