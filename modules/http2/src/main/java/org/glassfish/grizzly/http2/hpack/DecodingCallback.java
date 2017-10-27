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

package org.glassfish.grizzly.http2.hpack;

import org.glassfish.grizzly.Buffer;


/**
 * Delivers results of the {@link Decoder#decode(Buffer, boolean,
 * DecodingCallback) decoding operation}.
 *
 * <p> Methods of the callback are never called by a decoder with any of the
 * arguments being {@code null}.
 *
 * <p> The callback provides methods for all possible <a
 * href="https://tools.ietf.org/html/rfc7541#section-6">binary
 * representations</a>. This could be useful for implementing an intermediary,
 * logging, debugging, etc.
 *
 * <p> The callback is an interface in order to interoperate with lambdas (in
 * the most common use case):
 * <pre>{@code
 *     DecodingCallback callback = (name, value) -> System.out.println(name + ", " + value);
 * }</pre>
 *
 * <p> Names and values are {@link CharSequence}s rather than {@link String}s in
 * order to allow users to decide whether or not they need to create objects. A
 * {@code CharSequence} might be used in-place, for example, to be appended to
 * an {@link Appendable} (e.g. {@link StringBuilder}) and then discarded.
 *
 * <p> That said, if a passed {@code CharSequence} needs to outlast the method
 * call, it needs to be copied.
 */
@SuppressWarnings("UnusedParameters")
public abstract class DecodingCallback {

    /**
     * A method the more specific methods of the callback forward their calls
     * to.
     *
     * @param name
     *         header name
     * @param value
     *         header value
     */
    public abstract void onDecoded(CharSequence name, CharSequence value);

    /**
     * A more finer-grained version of {@link #onDecoded(CharSequence,
     * CharSequence)} that also reports on value sensitivity.
     *
     * <p> Value sensitivity must be considered, for example, when implementing
     * an intermediary. A {@code value} is sensitive if it was represented as <a
     * href="https://tools.ietf.org/html/rfc7541#section-6.2.3">Literal Header
     * Field Never Indexed</a>.
     *
     * <p> It is required that intermediaries MUST use the {@linkplain
     * Encoder#header(CharSequence, CharSequence, boolean) same representation}
     * for encoding this header field in order to protect its value which is not
     * to be put at risk by compressing it.
     *
     * <p> The default implementation invokes {@code onDecoded(name, value)}.
     *
     * @param name
     *         header name
     * @param value
     *         header value
     * @param sensitive
     *         whether or not the value is sensitive
     *
     * @see #onLiteralNeverIndexed(int, CharSequence, CharSequence, boolean)
     * @see #onLiteralNeverIndexed(CharSequence, boolean, CharSequence, boolean)
     */
    public void onDecoded(CharSequence name, CharSequence value,
                           boolean sensitive) {
        onDecoded(name, value);
    }

    /**
     * An <a href="https://tools.ietf.org/html/rfc7541#section-6.1">Indexed
     * Header Field</a> decoded.
     *
     * <p> The default implementation invokes
     * {@code onDecoded(name, value, false)}.
     *
     * @param index
     *         index of an entry in the table
     * @param name
     *         header name
     * @param value
     *         header value
     */
    public void onIndexed(int index, CharSequence name, CharSequence value) {
        onDecoded(name, value, false);
    }

    /**
     * A <a href="https://tools.ietf.org/html/rfc7541#section-6.2.2">Literal
     * Header Field without Indexing</a> decoded, where a {@code name} was
     * referred by an {@code index}.
     *
     * <p> The default implementation invokes
     * {@code onDecoded(name, value, false)}.
     *
     * @param index
     *         index of an entry in the table
     * @param name
     *         header name
     * @param value
     *         header value
     * @param valueHuffman
     *         if the {@code value} was Huffman encoded
     */
    public void onLiteral(int index, CharSequence name,
                           CharSequence value, boolean valueHuffman) {
        onDecoded(name, value, false);
    }

    /**
     * A <a href="https://tools.ietf.org/html/rfc7541#section-6.2.2">Literal
     * Header Field without Indexing</a> decoded, where both a {@code name} and
     * a {@code value} were literal.
     *
     * <p> The default implementation invokes
     * {@code onDecoded(name, value, false)}.
     *
     * @param name
     *         header name
     * @param nameHuffman
     *         if the {@code name} was Huffman encoded
     * @param value
     *         header value
     * @param valueHuffman
     *         if the {@code value} was Huffman encoded
     */
    public void onLiteral(CharSequence name, boolean nameHuffman,
                           CharSequence value, boolean valueHuffman) {
        onDecoded(name, value, false);
    }

    /**
     * A <a href="https://tools.ietf.org/html/rfc7541#section-6.2.3">Literal
     * Header Field Never Indexed</a> decoded, where a {@code name}
     * was referred by an {@code index}.
     *
     * <p> The default implementation invokes
     * {@code onDecoded(name, value, true)}.
     *
     * @param index
     *         index of an entry in the table
     * @param name
     *         header name
     * @param value
     *         header value
     * @param valueHuffman
     *         if the {@code value} was Huffman encoded
     */
    public void onLiteralNeverIndexed(int index, CharSequence name,
                                       CharSequence value,
                                       boolean valueHuffman) {
        onDecoded(name, value, true);
    }

    /**
     * A <a href="https://tools.ietf.org/html/rfc7541#section-6.2.3">Literal
     * Header Field Never Indexed</a> decoded, where both a {@code
     * name} and a {@code value} were literal.
     *
     * <p> The default implementation invokes
     * {@code onDecoded(name, value, true)}.
     *
     * @param name
     *         header name
     * @param nameHuffman
     *         if the {@code name} was Huffman encoded
     * @param value
     *         header value
     * @param valueHuffman
     *         if the {@code value} was Huffman encoded
     */
    public void onLiteralNeverIndexed(CharSequence name, boolean nameHuffman,
                                       CharSequence value, boolean valueHuffman) {
        onDecoded(name, value, true);
    }

    /**
     * A <a href="https://tools.ietf.org/html/rfc7541#section-6.2.1">Literal
     * Header Field with Incremental Indexing</a> decoded, where a {@code name}
     * was referred by an {@code index}.
     *
     * <p> The default implementation invokes
     * {@code onDecoded(name, value, false)}.
     *
     * @param index
     *         index of an entry in the table
     * @param name
     *         header name
     * @param value
     *         header value
     * @param valueHuffman
     *         if the {@code value} was Huffman encoded
     */
    public void onLiteralWithIndexing(int index,
                                       CharSequence name,
                                       CharSequence value, boolean valueHuffman) {
        onDecoded(name, value, false);
    }

    /**
     * A <a href="https://tools.ietf.org/html/rfc7541#section-6.2.1">Literal
     * Header Field with Incremental Indexing</a> decoded, where both a {@code
     * name} and a {@code value} were literal.
     *
     * <p> The default implementation invokes
     * {@code onDecoded(name, value, false)}.
     *
     * @param name
     *         header name
     * @param nameHuffman
     *         if the {@code name} was Huffman encoded
     * @param value
     *         header value
     * @param valueHuffman
     *         if the {@code value} was Huffman encoded
     */
    public void onLiteralWithIndexing(CharSequence name, boolean nameHuffman,
                                       CharSequence value, boolean valueHuffman) {
        onDecoded(name, value, false);
    }

    /**
     * A <a href="https://tools.ietf.org/html/rfc7541#section-6.3">Dynamic Table
     * Size Update</a> decoded.
     *
     * <p> The default implementation does nothing.
     *
     * @param capacity
     *         new capacity of the header table
     */
    public void onSizeUpdate(int capacity) { }
}
