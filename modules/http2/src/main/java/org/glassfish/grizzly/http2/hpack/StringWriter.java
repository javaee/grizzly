/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2016-2017 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://oss.oracle.com/licenses/CDDL+GPL-1.1
 * or LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at LICENSE.txt.
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

package org.glassfish.grizzly.http2.hpack;

import org.glassfish.grizzly.Buffer;

import java.util.Arrays;

//
//          0   1   2   3   4   5   6   7
//        +---+---+---+---+---+---+---+---+
//        | H |    String Length (7+)     |
//        +---+---------------------------+
//        |  String Data (Length octets)  |
//        +-------------------------------+
//
// StringWriter does not require a notion of endOfInput (isLast) in 'write'
// methods due to the nature of string representation in HPACK. Namely, the
// length of the string is put before string's contents. Therefore the length is
// always known beforehand.
//
// Expected use:
//
//     configure write* (reset configure write*)*
//
final class StringWriter {

    private static final byte NEW            = 0x0;
    private static final byte CONFIGURED     = 0x1;
    private static final byte LENGTH_WRITTEN = 0x2;
    private static final byte DONE           = 0x4;

    private final IntegerWriter intWriter = new IntegerWriter();
    private final Huffman.Writer huffmanWriter = new Huffman.Writer();
    private final ISO_8859_1.Writer plainWriter = new ISO_8859_1.Writer();

    private byte state = NEW;
    private boolean huffman;

    StringWriter configure(CharSequence input, boolean huffman) {
        return configure(input, 0, input.length(), huffman);
    }

    StringWriter configure(CharSequence input, int start, int end,
                           boolean huffman) {
        if (start < 0 || end < 0 || end > input.length() || start > end) {
            throw new IndexOutOfBoundsException(
                    String.format("input.length()=%s, start=%s, end=%s",
                            input.length(), start, end));
        }
        if (!huffman) {
            plainWriter.configure(input, start, end);
            intWriter.configure(end - start, 7, 0b0000_0000);
        } else {
            huffmanWriter.from(input, start, end);
            intWriter.configure(Huffman.INSTANCE.lengthOf(input, start, end),
                    7, 0b1000_0000);
        }

        this.huffman = huffman;
        state = CONFIGURED;
        return this;
    }

    boolean write(Buffer output) {
        if (state == DONE) {
            return true;
        }
        if (state == NEW) {
            throw new IllegalStateException("Configure first");
        }
        if (!output.hasRemaining()) {
            return false;
        }
        if (state == CONFIGURED) {
            if (intWriter.write(output)) {
                state = LENGTH_WRITTEN;
            } else {
                return false;
            }
        }
        if (state == LENGTH_WRITTEN) {
            boolean written = huffman
                    ? huffmanWriter.write(output)
                    : plainWriter.write(output);
            if (written) {
                state = DONE;
                return true;
            } else {
                return false;
            }
        }
        throw new InternalError(Arrays.toString(new Object[]{state, huffman}));
    }

    void reset() {
        intWriter.reset();
        if (huffman) {
            huffmanWriter.reset();
        } else {
            plainWriter.reset();
        }
        state = NEW;
    }
}
