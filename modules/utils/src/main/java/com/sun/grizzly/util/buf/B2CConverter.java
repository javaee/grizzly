/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2007-2011 Oracle and/or its affiliates. All rights reserved.
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
 *
 *
 * This file incorporates work covered by the following copyright and
 * permission notice:
 *
 * Copyright 2004 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sun.grizzly.util.buf;

import com.sun.grizzly.util.Charsets;
import com.sun.grizzly.util.LoggerUtils;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CoderResult;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;

/** Efficient conversion of bytes  to character .
 *
 * Now uses NIO directly
 */
public class B2CConverter {

    /**
     * Default Logger.
     */
    private static final boolean IS_OLD_IO_MODE = Boolean.getBoolean(B2CConverter.class.getName() + ".blockingMode");

    private static final Logger logger = LoggerUtils.getLogger();
    private static final int MAX_NUMBER_OF_BYTES_PER_CHARACTER = 16;

    private Charset charset;
    private CharsetDecoder decoder;
    private final ByteBuffer remainder = ByteBuffer.allocate(MAX_NUMBER_OF_BYTES_PER_CHARACTER);

    // Support old blocking converter
    private B2CConverterBlocking blockingConverter;

    protected B2CConverter() {
        init("US_ASCII");
    }

    /** Create a converter, with bytes going to a byte buffer
     */
    public B2CConverter(String encoding) throws IOException {
        init(encoding);
    }

    protected void init(String encoding) {
        if (IS_OLD_IO_MODE) {
            try {
                blockingConverter = new B2CConverterBlocking(encoding);
            } catch (IOException e) {
                throw new IllegalStateException("Can not initialize blocking converter");
            }
        } else {
            charset = Charsets.lookupCharset(encoding);
            decoder = charset.newDecoder().
                    onMalformedInput(CodingErrorAction.REPLACE).
                    onUnmappableCharacter(CodingErrorAction.REPLACE);
        }
    }

    /** Reset the internal state, empty the buffers.
     *  The encoding remain in effect, the internal buffers remain allocated.
     */
    public void recycle() {
        if (IS_OLD_IO_MODE) {
            blockingConverter.recycle();
        }
    }

    /** Convert a buffer of bytes into a chars
     * @deprecated
     */
    public void convert(ByteChunk bb, CharChunk cb)
            throws IOException {
        convert(bb, cb, cb.getBuffer().length - cb.getEnd());
    }

    public void convert(ByteChunk bb, CharChunk cb, int limit)
            throws IOException {
        if (IS_OLD_IO_MODE) {
            blockingConverter.convert(bb, cb, limit);
            return;
        }

        try {
            final int bbAvailable = bb.getEnd() - bb.getStart();
            if (limit > bbAvailable) {
                limit = bbAvailable;
            }

            byte[] barr = bb.getBuffer();
            int boff = bb.getOffset();
            ByteBuffer tmp_bb = ByteBuffer.wrap(barr, boff, limit);

            char[] carr = cb.getBuffer();
            int coff = cb.getEnd();
            final int remain = carr.length - coff;
            final int cbLimit = cb.getLimit();
            if (remain < limit && (cbLimit < 0 || cbLimit > carr.length)) {
                cb.makeSpace(limit);
                carr = cb.getBuffer();
                coff = cb.getEnd();
            }

            CharBuffer tmp_cb = CharBuffer.wrap(carr, coff, carr.length - coff);

            if (remainder.position() > 0) {
                flushRemainder(tmp_bb, tmp_cb);
            }

            CoderResult cr = decoder.decode(tmp_bb, tmp_cb, false);
            cb.setEnd(tmp_cb.position());

            while (cr == CoderResult.OVERFLOW) {
                cb.flushBuffer();
                coff = cb.getEnd();
                carr = cb.getBuffer();
                tmp_cb = CharBuffer.wrap(carr, coff, carr.length - coff);
                cr = decoder.decode(tmp_bb, tmp_cb, false);
                cb.setEnd(tmp_cb.position());
            }
            bb.setOffset(tmp_bb.position());
            if (tmp_bb.hasRemaining()) {
                remainder.put(tmp_bb);
            }

            if (cr != CoderResult.UNDERFLOW) {
                throw new IOException("Encoding error");
            }
        } catch (IOException ex) {
            if (debug > 0) {
                log("B2CConverter " + ex.toString());
            }
            decoder.reset();
            throw ex;
        }
    }

    // START CR 6309511
    /**
     * Character conversion of a US-ASCII MessageBytes.
     */
    public static void convertASCII(MessageBytes mb) {
        if (IS_OLD_IO_MODE) {
            B2CConverterBlocking.convertASCII(mb);
            return;
        }

        // This is of course only meaningful for bytes
        if (mb.getType() != MessageBytes.T_BYTES) {
            return;
        }

        ByteChunk bc = mb.getByteChunk();
        CharChunk cc = mb.getCharChunk();
        int length = bc.getLength();
        cc.allocate(length, -1);

        // Default encoding: fast conversion
        byte[] bbuf = bc.getBuffer();
        char[] cbuf = cc.getBuffer();
        int start = bc.getStart();
        for (int i = 0; i < length; i++) {
            cbuf[i] = (char) (bbuf[i + start] & 0xff);
        }
        mb.setChars(cbuf, 0, length);

    }
    // END CR 6309511

    public void reset() throws IOException {
        if (IS_OLD_IO_MODE) {
            blockingConverter.reset();
            return;
        }

        if (decoder != null) {
            decoder.reset();
            remainder.clear();
        }
    }

    private final int debug = 0;

    void log(String s) {
        if (logger.isLoggable(Level.FINEST)) {
            logger.log(Level.FINEST, "B2CConverter: " + s);
        }
    }

    private void flushRemainder(ByteBuffer tmp_bb, CharBuffer tmp_cb) {
        while(remainder.position() > 0 && tmp_bb.hasRemaining()) {
            remainder.put(tmp_bb.get());
            remainder.flip();
            CoderResult cr = decoder.decode(remainder, tmp_cb, false);
            if (cr == CoderResult.OVERFLOW) {
                // Shouldn't happen, because we allocated required output buffer before
                throw new IllegalStateException("CharChunk is not big enough");
            }

            if (!remainder.hasRemaining()) {
                remainder.clear();
                break;
            }

            remainder.compact();
        }
    }
}
