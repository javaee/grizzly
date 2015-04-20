/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2015 Oracle and/or its affiliates. All rights reserved.
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
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static java.lang.String.format;
import org.glassfish.grizzly.Buffer;

//
// Responsible for coding algorithms, that is --
//  translating from binary to primitives and vice versa
//
public final class BinaryPrimitives {

    private static final int MAX_INTEGER = Integer.MAX_VALUE;

    private static final HuffmanCoding huffmanCoding = HuffmanCoding.getInstance();

    private BinaryPrimitives() {
    }
    
    public static int readInteger(Buffer source, int N) {
        return readInteger(source, N, MAX_INTEGER);
    }

    private static int readInteger(Buffer source, int n, int maxInteger) {
        checkPrefix(n);

        final int nBitMask = 0xFF >> (8 - n);
        final int nBits = source.get() & nBitMask;
        if (nBits != nBitMask) {
            return nBits;
        }
        // variable-length quantity (VLQ)
        int r = nBitMask;
        int b = 0;
        do {
            final byte i = source.get();
            final long newVal = (((long) (i & 0x7F)) << b) + r;
            if (newVal > maxInteger) {
                break;
            }

            r = (int) newVal;
            if ((i & 0x80) == 0) {
                return r;
            }
            
            b += 7;
        } while (b < 32);
        
        throw new IllegalArgumentException("Integer overflow");
    }

    static void writeInteger(OutputStream destination, int value, int n)
            throws IOException {
        writeInteger(destination, value, n, 0);
    }

    /**
     * Writes an integer to a wire.
     * <p>
     * Since we can only write bytewise to an output stream,
     * apart from an integer value itself, we need the data that goes before
     * the N bits of prefix (i.e. goes into the first 8 - N bits).
     * Since the data ({@code payload}) is of type {@code int} only the the
     * bits with numbers from (8 - N) (exclusive) to (N - 1) (inclusive) are
     * considered.
     *
     * @param destination output stream to write value and payload to
     * @param value       value to be written to the output stream
     * @param n           prefix length
     * @param payload     value to be written before the prefix
     */
    static void writeInteger(OutputStream destination, int value, int n,
            int payload) throws IOException {
        
        Objects.requireNonNull(destination);
        checkPrefix(n);
        if (value < 0) {
            throw new IllegalArgumentException("value < 0: value=" + value);
        }

        assert payload == (payload & 0xFF & (0xFFFFFFFF << n));

        final int nBitMask = 0xFF >> (8 - n);
        
        int i = value;
        if (i < nBitMask) {
            destination.write((byte) (payload | i));
            return;
        }

        destination.write((byte) (payload | nBitMask));
        i -= nBitMask;
        while (i >= 0x80) {
            destination.write((i & 0x7F) | 0x80);
            i >>= 7;
        }
        destination.write((byte) i);
    }

    //          0   1   2   3   4   5   6   7
    //        +---+---+---+---+---+---+---+---+
    //        | H |    String Length (7+)     |
    //        +---+---------------------------+
    //        |  String Data (Length octets)  |
    //        +-------------------------------+
    //
    public static void readString(Buffer source, ObjectHolder<String> result) {

        int m = source.position();
        boolean huffman = (source.get() & 0b10000000) != 0;
        source.position(m);
        int len = readInteger(source, 7);
        if (source.limit() < source.position() + len) {
            throw new IllegalArgumentException(format(
                    "String representation supposed to consist of more octets "
                            + "than is available: expected string length=%s",
                    len));
        }
        int l = source.limit();
        source.limit(source.position() + len);
        if (!huffman) {
            result.setObj(source.toStringContent(StandardCharsets.ISO_8859_1));
        } else {
            result.setObj(huffmanCoding.from(source));
        }
        source.limit(l);
    }

    public static void writeString(final OutputStream destination,
            final String value, final boolean useHuffmanCoding)
            throws IOException {
        if (useHuffmanCoding) {
            writeHuffmanString(destination, value);
        } else {
            writeString(destination, value);
        }
    }

    public static void writeString(OutputStream destination,
            byte[] value, boolean useHuffmanCoding) throws IOException {
        writeString(destination, value, 0, value.length, useHuffmanCoding);
    }

    public static void writeString(final OutputStream destination,
            final byte[] value, final int off, final int len,
            boolean useHuffmanCoding) throws IOException {
        if (useHuffmanCoding) {
            writeHuffmanString(destination, value, off, len);
        } else {
            writeString(destination, value, off, len);
        }
    }
    
    /**
     * Writes a string to a wire in a {@link StandardCharsets#ISO_8859_1 plain}
     * encoding.
     * <p>
     * Some obvious comments. Both the output stream and the string
     * supposed not to be {@code null}. Moreover, the output stream
     * supposed not to be closed. In case of any problem occurred inside,
     * the method won't try to close or modify the output stream in any way.
     * It will also never flush it. Those are the calls the owner of the
     * stream has to make.
     *
     * @param destination output stream to write to
     * @param value       string, represented as byte array, to be written to the output stream
     * @param off         payload offset
     * @param len         payload length
     * @throws java.io.IOException
     */
    private static void writeString(final OutputStream destination,
            final byte[] value, final int off, final int len)
            throws IOException {
        
        Objects.requireNonNull(destination, "destination == null");
        Objects.requireNonNull(value, "value == null");
        
        writeInteger(destination, len, 7, 0);
        destination.write(value, off, len);
    }
    
    /**
     * Writes a string to a wire in a {@link StandardCharsets#ISO_8859_1 plain}
     * encoding.
     * <p>
     * Some obvious comments. Both the output stream and the string
     * supposed not to be {@code null}. Moreover, the output stream
     * supposed not to be closed. In case of any problem occurred inside,
     * the method won't try to close or modify the output stream in any way.
     * It will also never flush it. Those are the calls the owner of the
     * stream has to make.
     *
     * @param destination output stream to write to
     * @param value       string to be written to the output stream
     * @throws java.io.IOException
     */
    private static void writeString(final OutputStream destination,
            final String value)
            throws IOException {
        
        Objects.requireNonNull(destination, "destination == null");
        Objects.requireNonNull(value, "value == null");
        
        final int len = value.length();
        writeInteger(destination, len, 7, 0);
        for (int i = 0; i < len; i++) {
            destination.write(value.charAt(i));
        }
    }
    
    private static void writeHuffmanString(final OutputStream destination,
            final byte[] value, final int off, final int len) throws IOException {
        Objects.requireNonNull(destination, "destination == null");
        Objects.requireNonNull(value, "value == null");
        
        writeInteger(destination,
                huffmanCoding.lengthOf(value, off, len), 7, 0b10000000);
        huffmanCoding.to(destination, value, off, len);
    }

    private static void writeHuffmanString(final OutputStream destination,
            final String value) throws IOException {
        Objects.requireNonNull(destination, "destination == null");
        Objects.requireNonNull(value, "value == null");
        
        writeInteger(destination,
                huffmanCoding.lengthOf(value), 7, 0b10000000);
        huffmanCoding.to(destination, value);
    }

    private static void checkPrefix(int N) {
        if (N < 1 || N > 8) {
            throw new IllegalArgumentException(
                    "N is always between 1 and 8 bits: N= " + N);
        }
    }
}
