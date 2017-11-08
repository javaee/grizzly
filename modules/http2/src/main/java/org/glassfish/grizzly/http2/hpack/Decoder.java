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

import java.net.ProtocolException;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

/**
 * Decodes headers from their binary representation.
 *
 * <p>Typical lifecycle looks like this:
 *
 * <p> {@link #Decoder(int) new Decoder}
 * ({@link #setMaxCapacity(int) setMaxCapacity}?
 * {@link #decode(Buffer, boolean, DecodingCallback) decode})*
 *
 * <p> The design intentions behind Decoder were to facilitate flexible and
 * incremental style of processing.
 *
 * <p> {@code Decoder} does not require a complete header block in a single
 * {@code ByteBuffer}. The header block can be spread across many buffers of any
 * size and decoded one-by-one the way it makes most sense for the user. This
 * way also allows not to limit the size of the header block.
 *
 * <p> Headers are delivered to the {@linkplain DecodingCallback callback} as
 * soon as they become decoded. Using the callback also gives the user a freedom
 * to decide how headers are processed. The callback does not limit the number
 * of headers decoded during single decoding operation.
 *
 */
public final class Decoder {

    private static final State[] states = new State[256];

    static {
        // To be able to do a quick lookup, each of 256 possibilities are mapped
        // to corresponding states.
        //
        // We can safely do this since patterns 1, 01, 001, 0001, 0000 are
        // Huffman prefixes and therefore are inherently not ambiguous.
        //
        // I do it mainly for better debugging (to not go each time step by step
        // through if...else tree). As for performance win for the decoding, I
        // believe is negligible.
        for (int i = 0; i < states.length; i++) {
            if ((i & 0b1000_0000) == 0b1000_0000) {
                states[i] = State.INDEXED;
            } else if ((i & 0b1100_0000) == 0b0100_0000) {
                states[i] = State.LITERAL_WITH_INDEXING;
            } else if ((i & 0b1110_0000) == 0b0010_0000) {
                states[i] = State.SIZE_UPDATE;
            } else if ((i & 0b1111_0000) == 0b0001_0000) {
                states[i] = State.LITERAL_NEVER_INDEXED;
            } else if ((i & 0b1111_0000) == 0b0000_0000) {
                states[i] = State.LITERAL;
            } else {
                throw new InternalError(String.valueOf(i));
            }
        }
    }

    private final HeaderTable table;

    private State state = State.READY;
    private final IntegerReader integerReader;
    private final StringReader stringReader;
    private final StringBuilder name;
    private final StringBuilder value;
    private int intValue;
    private boolean firstValueRead;
    private boolean firstValueIndex;
    private boolean nameHuffmanEncoded;
    private boolean valueHuffmanEncoded;
    private int capacity;

    /**
     * Constructs a {@code Decoder} with the specified initial capacity of the
     * header table.
     *
     * <p> The value has to be agreed between decoder and encoder out-of-band,
     * e.g. by a protocol that uses HPACK (see <a
     * href="https://tools.ietf.org/html/rfc7541#section-4.2">4.2. Maximum Table
     * Size</a>).
     *
     * @param capacity
     *         a non-negative integer
     *
     * @throws IllegalArgumentException
     *         if capacity is negative
     */
    public Decoder(int capacity) {
        setMaxCapacity(capacity);
        table = new HeaderTable(capacity);
        integerReader = new IntegerReader();
        stringReader = new StringReader();
        name = new StringBuilder(512);
        value = new StringBuilder(1024);
    }

    /**
     * Sets a maximum capacity of the header table.
     *
     * <p> The value has to be agreed between decoder and encoder out-of-band,
     * e.g. by a protocol that uses HPACK (see <a
     * href="https://tools.ietf.org/html/rfc7541#section-4.2">4.2. Maximum Table
     * Size</a>).
     *
     * @param capacity
     *         a non-negative integer
     *
     * @throws IllegalArgumentException
     *         if capacity is negative
     */
    public void setMaxCapacity(int capacity) {
        if (capacity < 0) {
            throw new IllegalArgumentException("capacity >= 0: " + capacity);
        }
        // FIXME: await capacity update if less than what was prior to it
        this.capacity = capacity;
    }

    /**
     * Decodes a header block from the given buffer to the given callback.
     *
     * <p> Suppose a header block is represented by a sequence of {@code
     * ByteBuffer}s in the form of {@code Iterator<ByteBuffer>}. And the
     * consumer of decoded headers is represented by the callback. Then to
     * decode the header block, the following approach might be used:
     *
     * <pre>{@code
     * while (buffers.hasNext()) {
     *     ByteBuffer input = buffers.next();
     *     decoder.decode(input, callback, !buffers.hasNext());
     * }
     * }</pre>
     *
     * <p> The decoder reads as much as possible of the header block from the
     * given buffer, starting at the buffer's position, and increments its
     * position to reflect the bytes read. The buffer's mark and limit will not
     * be modified.
     *
     * <p> Once the method is invoked with {@code endOfHeaderBlock == true}, the
     * current header block is deemed ended, and inconsistencies, if any, are
     * reported immediately by throwing an {@code UncheckedIOException}.
     *
     * <p> Each callback method is called only after the implementation has
     * processed the corresponding bytes. If the bytes revealed a decoding
     * error, the callback method is not called.
     *
     * <p> In addition to exceptions thrown directly by the method, any
     * exceptions thrown from the {@code callback} will bubble up.
     *
     * The method asks for {@code endOfHeaderBlock} flag instead of
     * returning it for two reasons. The first one is that the user of the
     * decoder always knows which chunk is the last. The second one is to throw
     * the most detailed exception possible, which might be useful for
     * diagnosing issues.
     *
     * This implementation is not atomic in respect to decoding
     * errors. In other words, if the decoding operation has thrown a decoding
     * error, the decoder is no longer usable.
     *
     * @param headerBlock
     *         the chunk of the header block, may be empty
     * @param endOfHeaderBlock
     *         true if the chunk is the final (or the only one) in the sequence
     *
     * @param consumer
     *         the callback
     * @throws RuntimeException
     *         in case of a decoding error
     * @throws NullPointerException
     *         if either headerBlock or consumer are null
     */
    public void decode(Buffer headerBlock, boolean endOfHeaderBlock,
                       DecodingCallback consumer) {
        requireNonNull(headerBlock, "headerBlock");
        requireNonNull(consumer, "consumer");
        while (headerBlock.hasRemaining()) {
            proceed(headerBlock, consumer);
        }
        if (endOfHeaderBlock && state != State.READY) {
            throw new RuntimeException(
                    new ProtocolException("Unexpected end of header block"));
        }
    }

    private void proceed(Buffer input, DecodingCallback action) {
        switch (state) {
            case READY:
                resumeReady(input);
                break;
            case INDEXED:
                resumeIndexed(input, action);
                break;
            case LITERAL:
                resumeLiteral(input, action);
                break;
            case LITERAL_WITH_INDEXING:
                resumeLiteralWithIndexing(input, action);
                break;
            case LITERAL_NEVER_INDEXED:
                resumeLiteralNeverIndexed(input, action);
                break;
            case SIZE_UPDATE:
                resumeSizeUpdate(input, action);
                break;
            default:
                throw new InternalError(
                        "Unexpected decoder state: " + String.valueOf(state));
        }
    }

    private void resumeReady(Buffer input) {
        int b = input.get(input.position()) & 0xff; // absolute read
        State s = states[b];
        switch (s) {
            case INDEXED:
                integerReader.configure(7);
                state = State.INDEXED;
                firstValueIndex = true;
                break;
            case LITERAL:
                state = State.LITERAL;
                firstValueIndex = (b & 0b0000_1111) != 0;
                if (firstValueIndex) {
                    integerReader.configure(4);
                }
                break;
            case LITERAL_WITH_INDEXING:
                state = State.LITERAL_WITH_INDEXING;
                firstValueIndex = (b & 0b0011_1111) != 0;
                if (firstValueIndex) {
                    integerReader.configure(6);
                }
                break;
            case LITERAL_NEVER_INDEXED:
                state = State.LITERAL_NEVER_INDEXED;
                firstValueIndex = (b & 0b0000_1111) != 0;
                if (firstValueIndex) {
                    integerReader.configure(4);
                }
                break;
            case SIZE_UPDATE:
                integerReader.configure(5);
                state = State.SIZE_UPDATE;
                firstValueIndex = true;
                break;
            default:
                throw new InternalError(String.valueOf(s));
        }
        if (!firstValueIndex) {
            input.get(); // advance, next stop: "String Literal"
        }
    }

    //              0   1   2   3   4   5   6   7
    //            +---+---+---+---+---+---+---+---+
    //            | 1 |        Index (7+)         |
    //            +---+---------------------------+
    //
    private void resumeIndexed(Buffer input, DecodingCallback action) {
        if (!integerReader.read(input)) {
            return;
        }
        intValue = integerReader.get();
        integerReader.reset();
        try {
            HeaderTable.HeaderField f = table.get(intValue);
            action.onIndexed(intValue, f.name, f.value);
        } finally {
            state = State.READY;
        }
    }

    //              0   1   2   3   4   5   6   7
    //            +---+---+---+---+---+---+---+---+
    //            | 0 | 0 | 0 | 0 |  Index (4+)   |
    //            +---+---+-----------------------+
    //            | H |     Value Length (7+)     |
    //            +---+---------------------------+
    //            | Value String (Length octets)  |
    //            +-------------------------------+
    //
    //              0   1   2   3   4   5   6   7
    //            +---+---+---+---+---+---+---+---+
    //            | 0 | 0 | 0 | 0 |       0       |
    //            +---+---+-----------------------+
    //            | H |     Name Length (7+)      |
    //            +---+---------------------------+
    //            |  Name String (Length octets)  |
    //            +---+---------------------------+
    //            | H |     Value Length (7+)     |
    //            +---+---------------------------+
    //            | Value String (Length octets)  |
    //            +-------------------------------+
    //
    private void resumeLiteral(Buffer input, DecodingCallback action) {
        if (!completeReading(input)) {
            return;
        }
        try {
            if (firstValueIndex) {
                HeaderTable.HeaderField f = table.get(intValue);
                action.onLiteral(intValue, f.name, value, valueHuffmanEncoded);
            } else {
                action.onLiteral(name, nameHuffmanEncoded, value, valueHuffmanEncoded);
            }
        } finally {
            cleanUpAfterReading();
        }
    }

    //
    //              0   1   2   3   4   5   6   7
    //            +---+---+---+---+---+---+---+---+
    //            | 0 | 1 |      Index (6+)       |
    //            +---+---+-----------------------+
    //            | H |     Value Length (7+)     |
    //            +---+---------------------------+
    //            | Value String (Length octets)  |
    //            +-------------------------------+
    //
    //              0   1   2   3   4   5   6   7
    //            +---+---+---+---+---+---+---+---+
    //            | 0 | 1 |           0           |
    //            +---+---+-----------------------+
    //            | H |     Name Length (7+)      |
    //            +---+---------------------------+
    //            |  Name String (Length octets)  |
    //            +---+---------------------------+
    //            | H |     Value Length (7+)     |
    //            +---+---------------------------+
    //            | Value String (Length octets)  |
    //            +-------------------------------+
    //
    private void resumeLiteralWithIndexing(Buffer input, DecodingCallback action) {
        if (!completeReading(input)) {
            return;
        }
        try {
            //
            // 1. (name, value) will be stored in the table as strings
            // 2. Most likely the callback will also create strings from them
            // ------------------------------------------------------------------------
            //    Let's create those string beforehand (and only once!) to benefit everyone
            //
            String n;
            String v = value.toString();
            if (firstValueIndex) {
                HeaderTable.HeaderField f = table.get(intValue);
                n = f.name;
                action.onLiteralWithIndexing(intValue, n, v, valueHuffmanEncoded);
            } else {
                n = name.toString();
                action.onLiteralWithIndexing(n, nameHuffmanEncoded, v, valueHuffmanEncoded);
            }
            table.put(n, v);
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new RuntimeException(
                    new ProtocolException().initCause(e));
        } finally {
            cleanUpAfterReading();
        }
    }

    //              0   1   2   3   4   5   6   7
    //            +---+---+---+---+---+---+---+---+
    //            | 0 | 0 | 0 | 1 |  Index (4+)   |
    //            +---+---+-----------------------+
    //            | H |     Value Length (7+)     |
    //            +---+---------------------------+
    //            | Value String (Length octets)  |
    //            +-------------------------------+
    //
    //              0   1   2   3   4   5   6   7
    //            +---+---+---+---+---+---+---+---+
    //            | 0 | 0 | 0 | 1 |       0       |
    //            +---+---+-----------------------+
    //            | H |     Name Length (7+)      |
    //            +---+---------------------------+
    //            |  Name String (Length octets)  |
    //            +---+---------------------------+
    //            | H |     Value Length (7+)     |
    //            +---+---------------------------+
    //            | Value String (Length octets)  |
    //            +-------------------------------+
    //
    private void resumeLiteralNeverIndexed(Buffer input, DecodingCallback action) {
        if (!completeReading(input)) {
            return;
        }
        try {
            if (firstValueIndex) {
                HeaderTable.HeaderField f = table.get(intValue);
                action.onLiteralNeverIndexed(intValue, f.name, value, valueHuffmanEncoded);
            } else {
                action.onLiteralNeverIndexed(name, nameHuffmanEncoded, value, valueHuffmanEncoded);
            }
        } finally {
            cleanUpAfterReading();
        }
    }

    //              0   1   2   3   4   5   6   7
    //            +---+---+---+---+---+---+---+---+
    //            | 0 | 0 | 1 |   Max size (5+)   |
    //            +---+---------------------------+
    //
    private void resumeSizeUpdate(Buffer input, DecodingCallback action) {
        if (!integerReader.read(input)) {
            return;
        }
        intValue = integerReader.get();
        assert intValue >= 0;
        if (intValue > capacity) {
            throw new RuntimeException(new ProtocolException(
                    format("Received capacity exceeds expected: " +
                            "capacity=%s, expected=%s", intValue, capacity)));
        }
        integerReader.reset();
        try {
            action.onSizeUpdate(intValue);
            table.setMaxSize(intValue);
        } finally {
            state = State.READY;
        }
    }

    private boolean completeReading(Buffer input) {
        if (!firstValueRead) {
            if (firstValueIndex) {
                if (!integerReader.read(input)) {
                    return false;
                }
                intValue = integerReader.get();
                integerReader.reset();
            } else {
                if (!stringReader.read(input, name)) {
                    return false;
                }
                nameHuffmanEncoded = stringReader.isHuffmanEncoded();
                stringReader.reset();
            }
            firstValueRead = true;
            return false;
        } else {
            if (!stringReader.read(input, value)) {
                return false;
            }
        }
        valueHuffmanEncoded = stringReader.isHuffmanEncoded();
        stringReader.reset();
        return true;
    }

    private void cleanUpAfterReading() {
        name.setLength(0);
        value.setLength(0);
        firstValueRead = false;
        state = State.READY;
    }

    private enum State {
        READY,
        INDEXED,
        LITERAL_NEVER_INDEXED,
        LITERAL,
        LITERAL_WITH_INDEXING,
        SIZE_UPDATE
    }

    HeaderTable getTable() {
        return table;
    }
}
