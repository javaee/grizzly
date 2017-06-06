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

/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
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
final class StringReader {

    private static final byte NEW             = 0x0;
    private static final byte FIRST_BYTE_READ = 0x1;
    private static final byte LENGTH_READ     = 0x2;
    private static final byte DONE            = 0x4;

    private final IntegerReader intReader = new IntegerReader();
    private final Huffman.Reader huffmanReader = new Huffman.Reader();
    private final ISO_8859_1.Reader plainReader = new ISO_8859_1.Reader();

    private byte state = NEW;

    private boolean huffman;
    private int remainingLength;

    boolean read(Buffer input, Appendable output) {
        if (state == DONE) {
            return true;
        }
        if (!input.hasRemaining()) {
            return false;
        }
        if (state == NEW) {
            int p = input.position();
            huffman = (input.get(p) & 0b10000000) != 0;
            state = FIRST_BYTE_READ;
            intReader.configure(7);
        }
        if (state == FIRST_BYTE_READ) {
            boolean lengthRead = intReader.read(input);
            if (!lengthRead) {
                return false;
            }
            remainingLength = intReader.get();
            state = LENGTH_READ;
        }
        if (state == LENGTH_READ) {
            boolean isLast = input.remaining() >= remainingLength;
            int oldLimit = input.limit();
            if (isLast) {
                input.limit(input.position() + remainingLength);
            }
            remainingLength -= Math.min(input.remaining(), remainingLength);
            if (huffman) {
                huffmanReader.read(input, output, isLast);
            } else {
                plainReader.read(input, output);
            }
            if (isLast) {
                input.limit(oldLimit);
                state = DONE;
            }
            return isLast;
        }
        throw new InternalError(Arrays.toString(
                new Object[]{state, huffman, remainingLength}));
    }

    boolean isHuffmanEncoded() {
        if (state < FIRST_BYTE_READ) {
            throw new IllegalStateException("Has not been fully read yet");
        }
        return huffman;
    }

    void reset() {
        if (huffman) {
            huffmanReader.reset();
        } else {
            plainReader.reset();
        }
        intReader.reset();
        state = NEW;
    }
}
