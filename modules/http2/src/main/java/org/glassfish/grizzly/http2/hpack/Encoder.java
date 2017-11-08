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

import java.nio.ReadOnlyBufferException;
import java.util.LinkedList;
import java.util.List;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

/**
 * Encodes headers to their binary representation.
 *
 * <p>Typical lifecycle looks like this:
 *
 * <p> {@link #Encoder(int) new Encoder}
 * ({@link #setMaxCapacity(int) setMaxCapacity}?
 * {@link #encode(Buffer) encode})*
 *
 * <p> Suppose headers are represented by {@code Map<String, List<String>>}. A
 * supplier and a consumer of {@link Buffer}s in forms of {@code
 * Supplier<ByteBuffer>} and {@code Consumer<ByteBuffer>} respectively. Then to
 * encode headers, the following approach might be used:
 *
 * <pre>{@code
 *     for (Map.Entry<String, List<String>> h : headers.entrySet()) {
 *         String name = h.getKey();
 *         for (String value : h.getValue()) {
 *             encoder.header(name, value);        // Set up header
 *             boolean encoded;
 *             do {
 *                 ByteBuffer b = buffersSupplier.get();
 *                 encoded = encoder.encode(b);    // Encode the header
 *                 buffersConsumer.accept(b);
 *             } while (!encoded);
 *         }
 *     }
 * }</pre>
 *
 * <p> Though the specification <a
 * href="https://tools.ietf.org/html/rfc7541#section-2"> does not define</a> how
 * an encoder is to be implemented, a default implementation is provided by the
 * method {@link #header(CharSequence, CharSequence, boolean)}.
 *
 * <p> To provide a custom encoding implementation, {@code Encoder} has to be
 * extended. A subclass then can access methods for encoding using specific
 * representations (e.g. {@link #literal(int, CharSequence, boolean) literal},
 * {@link #indexed(int) indexed}, etc.)
 *
 * <p> An Encoder provides an incremental way of encoding headers.
 * {@link #encode(Buffer)} takes a buffer a returns a boolean indicating
 * whether, or not, the buffer was sufficiently sized to hold the
 * remaining of the encoded representation.
 *
 * <p> This way, there's no need to provide a buffer of a specific size, or to
 * resize (and copy) the buffer on demand, when the remaining encoded
 * representation will not fit in the buffer's remaining space. Instead, an
 * array of existing buffers can be used, prepended with a frame that encloses
 * the resulting header block afterwards.
 *
 * <p> Splitting the encoding operation into header set up and header encoding,
 * separates long lived arguments ({@code name}, {@code value}, {@code
 * sensitivity}, etc.) from the short lived ones (e.g. {@code buffer}),
 * simplifying each operation itself.
 *
 * <p> The default implementation does not use dynamic table. It reports to a
 * coupled Decoder a size update with the value of {@code 0}, and never changes
 * it afterwards.
 */
public class Encoder {

    // TODO: enum: no huffman/smart huffman/always huffman
    private static final boolean DEFAULT_HUFFMAN = true;

    private final IndexedWriter indexedWriter = new IndexedWriter();
    private final LiteralWriter literalWriter = new LiteralWriter();
    private final LiteralNeverIndexedWriter literalNeverIndexedWriter
            = new LiteralNeverIndexedWriter();
    private final LiteralWithIndexingWriter literalWithIndexingWriter
            = new LiteralWithIndexingWriter();
    private final SizeUpdateWriter sizeUpdateWriter = new SizeUpdateWriter();
    private final BulkSizeUpdateWriter bulkSizeUpdateWriter
            = new BulkSizeUpdateWriter();

    private BinaryRepresentationWriter writer;
    private final HeaderTable headerTable;

    private boolean encoding;

    private int maxCapacity;
    private int currCapacity;
    private int lastCapacity;
    private long minCapacity;
    private boolean capacityUpdate;
    private boolean configuredCapacityUpdate;

    /**
     * Constructs an {@code Encoder} with the specified maximum capacity of the
     * header table.
     *
     * <p> The value has to be agreed between decoder and encoder out-of-band,
     * e.g. by a protocol that uses HPACK (see <a
     * href="https://tools.ietf.org/html/rfc7541#section-4.2">4.2. Maximum Table
     * Size</a>).
     *
     * @param maxCapacity
     *         a non-negative integer
     *
     * @throws IllegalArgumentException
     *         if maxCapacity is negative
     */
    public Encoder(int maxCapacity) {
        if (maxCapacity < 0) {
            throw new IllegalArgumentException("maxCapacity >= 0: " + maxCapacity);
        }
        // Initial maximum capacity update mechanics
        minCapacity = Long.MAX_VALUE;
        currCapacity = -1;
        setMaxCapacity(maxCapacity);
        headerTable = new HeaderTable(lastCapacity);
    }

    /**
     * Sets up the given header {@code (name, value)}.
     *
     * <p> Fixates {@code name} and {@code value} for the duration of encoding.
     *
     * @param name
     *         the name
     * @param value
     *         the value
     *
     * @throws NullPointerException
     *         if any of the arguments are {@code null}
     * @throws IllegalStateException
     *         if the encoder hasn't fully encoded the previous header, or
     *         hasn't yet started to encode it
     * @see #header(CharSequence, CharSequence, boolean)
     */
    public void header(CharSequence name, CharSequence value)
            throws IllegalStateException {
        header(name, value, false);
    }

    /**
     * Sets up the given header {@code (name, value)} with possibly sensitive
     * value.
     *
     * <p> Fixates {@code name} and {@code value} for the duration of encoding.
     *
     * @param name
     *         the name
     * @param value
     *         the value
     * @param sensitive
     *         whether or not the value is sensitive
     *
     * @throws NullPointerException
     *         if any of the arguments are {@code null}
     * @throws IllegalStateException
     *         if the encoder hasn't fully encoded the previous header, or
     *         hasn't yet started to encode it
     * @see #header(CharSequence, CharSequence)
     * @see DecodingCallback#onDecoded(CharSequence, CharSequence, boolean)
     */
    public void header(CharSequence name, CharSequence value,
                       boolean sensitive) throws IllegalStateException {
        // Arguably a good balance between complexity of implementation and
        // efficiency of encoding
        requireNonNull(name, "name");
        requireNonNull(value, "value");
        HeaderTable t = getHeaderTable();
        int index = t.indexOf(name, value);
        if (index > 0) {
            indexed(index);
        } else if (index < 0) {
            if (sensitive) {
                literalNeverIndexed(-index, value, DEFAULT_HUFFMAN);
            } else {
                literal(-index, value, DEFAULT_HUFFMAN);
            }
        } else {
            if (sensitive) {
                literalNeverIndexed(name, DEFAULT_HUFFMAN, value, DEFAULT_HUFFMAN);
            } else {
                literal(name, DEFAULT_HUFFMAN, value, DEFAULT_HUFFMAN);
            }
        }
    }

    /**
     * Sets a maximum capacity of the header table.
     *
     * <p> The value has to be agreed between decoder and encoder out-of-band,
     * e.g. by a protocol that uses HPACK (see <a
     * href="https://tools.ietf.org/html/rfc7541#section-4.2">4.2. Maximum Table
     * Size</a>).
     *
     * <p> May be called any number of times after or before a complete header
     * has been encoded.
     *
     * <p> If the encoder decides to change the actual capacity, an update will
     * be encoded before a new encoding operation starts.
     *
     * @param capacity
     *         a non-negative integer
     *
     * @throws IllegalArgumentException
     *         if capacity is negative
     * @throws IllegalStateException
     *         if the encoder hasn't fully encoded the previous header, or
     *         hasn't yet started to encode it
     */
    public void setMaxCapacity(int capacity) {
        checkEncoding();
        if (capacity < 0) {
            throw new IllegalArgumentException("capacity >= 0: " + capacity);
        }
        int calculated = calculateCapacity(capacity);
        if (calculated < 0 || calculated > capacity) {
            throw new IllegalArgumentException(
                    format("0 <= calculated <= capacity: calculated=%s, capacity=%s",
                            calculated, capacity));
        }
        capacityUpdate = true;
        // maxCapacity needs to be updated unconditionally, so the encoder
        // always has the newest one (in case it decides to update it later
        // unsolicited)
        // Suppose maxCapacity = 4096, and the encoder has decided to use only
        // 2048. It later can choose anything else from the region [0, 4096].
        maxCapacity = capacity;
        lastCapacity = calculated;
        minCapacity = Math.min(minCapacity, lastCapacity);
    }

    @SuppressWarnings("UnusedParameters")
    protected int calculateCapacity(int maxCapacity) {
        // Default implementation of the Encoder won't add anything to the
        // table, therefore no need for a table space
        return 0;
    }

    /**
     * Encodes the {@linkplain #header(CharSequence, CharSequence) set up}
     * header into the given buffer.
     *
     * <p> The encoder writes as much as possible of the header's binary
     * representation into the given buffer, starting at the buffer's position,
     * and increments its position to reflect the bytes written. The buffer's
     * mark and limit will not be modified.
     *
     * <p> Once the method has returned {@code true}, the current header is
     * deemed encoded. A new header may be set up.
     *
     * @param headerBlock
     *         the buffer to encode the header into, may be empty
     *
     * @return {@code true} if the current header has been fully encoded,
     *         {@code false} otherwise
     *
     * @throws NullPointerException
     *         if the buffer is {@code null}
     * @throws ReadOnlyBufferException
     *         if this buffer is read-only
     * @throws IllegalStateException
     *         if there is no set up header
     */
    public final boolean encode(Buffer headerBlock) {
        if (!encoding) {
            throw new IllegalStateException("A header hasn't been set up");
        }
        if (!prependWithCapacityUpdate(headerBlock)) {
            return false;
        }
        boolean done = writer.write(headerTable, headerBlock);
        if (done) {
            writer.reset(); // FIXME: WHY?
            encoding = false;
        }
        return done;
    }

    private boolean prependWithCapacityUpdate(Buffer headerBlock) {
        if (capacityUpdate) {
            if (!configuredCapacityUpdate) {
                List<Integer> sizes = new LinkedList<>();
                if (minCapacity < currCapacity) {
                    sizes.add((int) minCapacity);
                    if (minCapacity != lastCapacity) {
                        sizes.add(lastCapacity);
                    }
                } else if (lastCapacity != currCapacity) {
                    sizes.add(lastCapacity);
                }
                bulkSizeUpdateWriter.maxHeaderTableSizes(sizes);
                configuredCapacityUpdate = true;
            }
            boolean done = bulkSizeUpdateWriter.write(headerTable, headerBlock);
            if (done) {
                minCapacity = lastCapacity;
                currCapacity = lastCapacity;
                bulkSizeUpdateWriter.reset();
                capacityUpdate = false;
                configuredCapacityUpdate = false;
            }
            return done;
        }
        return true;
    }

    protected final void indexed(int index) throws IndexOutOfBoundsException {
        checkEncoding();
        encoding = true;
        writer = indexedWriter.index(index);
    }

    protected final void literal(int index, CharSequence value,
                                 boolean useHuffman)
            throws IndexOutOfBoundsException {
        checkEncoding();
        encoding = true;
        writer = literalWriter
                .index(index).value(value, useHuffman);
    }

    protected final void literal(CharSequence name, boolean nameHuffman,
                                 CharSequence value, boolean valueHuffman) {
        checkEncoding();
        encoding = true;
        writer = literalWriter
                .name(name, nameHuffman).value(value, valueHuffman);
    }

    protected final void literalNeverIndexed(int index,
                                             CharSequence value,
                                             boolean valueHuffman)
            throws IndexOutOfBoundsException {
        checkEncoding();
        encoding = true;
        writer = literalNeverIndexedWriter
                .index(index).value(value, valueHuffman);
    }

    protected final void literalNeverIndexed(CharSequence name,
                                             boolean nameHuffman,
                                             CharSequence value,
                                             boolean valueHuffman) {
        checkEncoding();
        encoding = true;
        writer = literalNeverIndexedWriter
                .name(name, nameHuffman).value(value, valueHuffman);
    }

    @SuppressWarnings("unused")
    protected final void literalWithIndexing(int index,
                                             CharSequence value,
                                             boolean valueHuffman)
            throws IndexOutOfBoundsException {
        checkEncoding();
        encoding = true;
        writer = literalWithIndexingWriter
                .index(index).value(value, valueHuffman);
    }

    @SuppressWarnings("unused")
    protected final void literalWithIndexing(CharSequence name,
                                             boolean nameHuffman,
                                             CharSequence value,
                                             boolean valueHuffman) {
        checkEncoding();
        encoding = true;
        writer = literalWithIndexingWriter
                .name(name, nameHuffman).value(value, valueHuffman);
    }

    @SuppressWarnings("unused")
    protected final void sizeUpdate(int capacity)
            throws IllegalArgumentException {
        checkEncoding();
        // Ensure subclass follows the contract
        if (capacity > this.maxCapacity) {
            throw new IllegalArgumentException(
                    format("capacity <= maxCapacity: capacity=%s, maxCapacity=%s",
                            capacity, maxCapacity));
        }
        writer = sizeUpdateWriter.maxHeaderTableSize(capacity);
    }

    @SuppressWarnings("unused")
    protected final int getMaxCapacity() {
        return maxCapacity;
    }

    protected final HeaderTable getHeaderTable() {
        return headerTable;
    }

    protected final void checkEncoding() {
        if (encoding) {
            throw new IllegalStateException(
                    "Previous encoding operation hasn't finished yet");
        }
    }
}
