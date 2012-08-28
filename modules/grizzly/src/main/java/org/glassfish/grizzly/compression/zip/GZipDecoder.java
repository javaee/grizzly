/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2012 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.compression.zip;

import java.nio.ByteBuffer;
import java.util.zip.CRC32;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;
import org.glassfish.grizzly.AbstractTransformer;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.TransformationException;
import org.glassfish.grizzly.TransformationResult;
import org.glassfish.grizzly.attributes.AttributeStorage;
import org.glassfish.grizzly.memory.Buffers;
import org.glassfish.grizzly.memory.ByteBufferArray;
import org.glassfish.grizzly.memory.MemoryManager;

/**
 * This class implements a {@link org.glassfish.grizzly.Transformer} which decodes data
 * represented in the GZIP format.
 *
 * @author Alexey Stashok
 */
public class GZipDecoder extends AbstractTransformer<Buffer, Buffer> {
    protected enum DecodeStatus {
        INITIAL, FEXTRA1, FEXTRA2, FNAME, FCOMMENT, FHCRC, PAYLOAD, TRAILER, DONE
    }

    private static final int GZIP_MAGIC = 0x8b1f;

    /*
     * File header flags.
     */
    private final static int FTEXT	= 1;	// Extra text
    private final static int FHCRC	= 2;	// Header CRC
    private final static int FEXTRA	= 4;	// Extra field
    private final static int FNAME	= 8;	// File name
    private final static int FCOMMENT	= 16;	// File comment

    private final int bufferSize;

    public GZipDecoder() {
        this(512);
    }

    public GZipDecoder(int bufferSize) {
        this.bufferSize = bufferSize;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public String getName() {
        return "gzip-decoder";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasInputRemaining(AttributeStorage storage, Buffer input) {
        return input.hasRemaining();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected GZipInputState createStateObject() {
        return new GZipInputState();
    }

    @Override
    protected TransformationResult<Buffer, Buffer> transformImpl(
            AttributeStorage storage, Buffer input) throws TransformationException {
        final MemoryManager memoryManager = obtainMemoryManager(storage);

        final GZipInputState state = (GZipInputState) obtainStateObject(storage);

        if (!state.isInitialized()) {
            if (!initializeInput(input, state)) {
                return TransformationResult.createIncompletedResult(input);
            }
        }

        Buffer decodedBuffer = null;

        if (state.getDecodeStatus() == DecodeStatus.PAYLOAD) {
            if (input.hasRemaining()) {
                decodedBuffer = decodeBuffer(memoryManager, input, state);
            }
        }

        if (state.getDecodeStatus() == DecodeStatus.TRAILER && input.hasRemaining()) {
            if (decodeTrailer(input, state)) {
                state.setDecodeStatus(DecodeStatus.DONE);
                state.setInitialized(false);
            }
        }

        final boolean hasRemainder = input.hasRemaining();

        if (decodedBuffer == null || !decodedBuffer.hasRemaining()) {
            return TransformationResult.createIncompletedResult(hasRemainder ? input : null);
        }

        return TransformationResult.createCompletedResult(decodedBuffer,
                hasRemainder ? input : null);
    }

    private Buffer decodeBuffer(MemoryManager memoryManager, Buffer buffer,
            GZipInputState state) {

        final Inflater inflater = state.getInflater();
        final CRC32 inCrc32 = state.getCrc32();

        final ByteBufferArray byteBufferArray = buffer.toByteBufferArray();
        final ByteBuffer[] byteBuffers = byteBufferArray.getArray();
        final int size = byteBufferArray.size();

        Buffer resultBuffer = null;

        for (int i = 0; i < size; i++) {
            final ByteBuffer byteBuffer = byteBuffers[i];
            final int len = byteBuffer.remaining();

            final byte[] array;
            final int offset;
            if (byteBuffer.hasArray()) {
                array = byteBuffer.array();
                offset = byteBuffer.arrayOffset() + byteBuffer.position();
            } else {
                // @TODO allocate byte array via MemoryUtils
                array = new byte[len];
                offset = 0;
                byteBuffer.get(array);
                byteBuffer.position(byteBuffer.position() - len);
            }

            inflater.setInput(array, offset, len);

            int lastInflated;
            do {
                final Buffer decodedBuffer = memoryManager.allocate(bufferSize);
                final ByteBuffer decodedBB = decodedBuffer.toByteBuffer();
                final byte[] decodedArray = decodedBB.array();
                final int decodedArrayOffs = decodedBB.arrayOffset() + decodedBB.position();

                try {
                    lastInflated = inflater.inflate(decodedArray, decodedArrayOffs, bufferSize);
                } catch (DataFormatException e) {
                    decodedBuffer.dispose();
                    String s = e.getMessage();
                    throw new IllegalStateException(s != null ? s : "Invalid ZLIB data format");
                }

                if (lastInflated > 0) {
                    inCrc32.update(decodedArray, decodedArrayOffs, lastInflated);
                    decodedBuffer.position(lastInflated);
                    decodedBuffer.trim();
                    resultBuffer = Buffers.appendBuffers(memoryManager,
                            resultBuffer, decodedBuffer);
                } else {
                    decodedBuffer.dispose();
                    if (inflater.finished() || inflater.needsDictionary()) {
                        final int remainder = inflater.getRemaining();

                        final int remaining = byteBuffer.remaining();

                        byteBufferArray.restore();
                        byteBufferArray.recycle();

                        buffer.position(
                                buffer.position() + remaining - remainder);

                        state.setDecodeStatus(DecodeStatus.TRAILER);
                        return resultBuffer;
                    }
                }
            } while (lastInflated > 0);

            final int remaining = byteBuffer.remaining();

            byteBufferArray.restore();
            byteBufferArray.recycle();

            buffer.position(buffer.position() + remaining);
        }

        return resultBuffer;
    }

    private boolean initializeInput(final Buffer buffer,
            final GZipInputState state) {

        Inflater inflater = state.getInflater();
        if (inflater == null) {
            inflater = new Inflater(true);
            final CRC32 crc32 = new CRC32();
            crc32.reset();
            state.setInflater(inflater);
            state.setCrc32(crc32);
        } else if (state.getDecodeStatus() == DecodeStatus.DONE) {
            state.setDecodeStatus(DecodeStatus.INITIAL);
            inflater.reset();
            state.getCrc32().reset();
        }
        if (!parseHeader(buffer, state)) {
            return false;
        }

        state.getCrc32().reset();
        state.setInitialized(true);

        return true;
    }

    /*
     * Reads GZIP member header.
     */
    private boolean parseHeader(Buffer buffer, GZipInputState state) {

        final CRC32 crc32 = state.getCrc32();

        DecodeStatus decodeStatus;
        while((decodeStatus = state.getDecodeStatus()) != DecodeStatus.PAYLOAD) {

            switch (decodeStatus) {
                case INITIAL: {
                    if (buffer.remaining() < 10) {
                        return false;
                    }

                    // Check header magic
                    if (getUShort(buffer, crc32) != GZIP_MAGIC) {
                        throw new IllegalStateException("Not in GZIP format");
                    }

                    // Check compression method
                    if (getUByte(buffer, crc32) != 8) {
                        throw new IllegalStateException("Unsupported compression method");
                    }
                    // Read flags
                    final int flg = getUByte(buffer, crc32);
                    state.setHeaderFlag(flg);

                    // Skip MTIME, XFL, and OS fields
                    skipBytes(buffer, 6, crc32);

                    state.setDecodeStatus(DecodeStatus.FEXTRA1);
                }

        	// Skip optional extra field
                case FEXTRA1: {
                    if ((state.getHeaderFlag() & FEXTRA) != FEXTRA) {
                        state.setDecodeStatus(DecodeStatus.FNAME);
                        break;
                    }

                    if (buffer.remaining() < 2) {
                        return false;
                    }

                    state.setHeaderParseStateValue(getUShort(buffer, crc32));
                    state.setDecodeStatus(DecodeStatus.FEXTRA2);
                }

                case FEXTRA2: {
                    final int fextraSize = state.getHeaderParseStateValue();
                    if (buffer.remaining() < fextraSize) {
                        return false;
                    }

                    skipBytes(buffer, fextraSize, crc32);
                    state.setHeaderParseStateValue(0);
                    state.setDecodeStatus(DecodeStatus.FNAME);
                }

        	// Skip optional file name
                case FNAME: {
                    if ((state.getHeaderFlag() & FNAME) == FNAME) {
                        boolean found = false;
                        while (buffer.hasRemaining()) {
                            if (getUByte(buffer, crc32) == 0) {
                                found = true;
                                break;
                            }
                        }

                        if (!found) return false;
                    }

                    state.setDecodeStatus(DecodeStatus.FCOMMENT);
                }

        	// Skip optional file comment
                case FCOMMENT: {
                    if ((state.getHeaderFlag() & FCOMMENT) == FCOMMENT) {
                        boolean found = false;
                        while (buffer.hasRemaining()) {
                            if (getUByte(buffer, crc32) == 0) {
                                found = true;
                                break;
                            }
                        }

                        if (!found) return false;
                    }

                    state.setDecodeStatus(DecodeStatus.FHCRC);
                }

        	// Check optional header CRC
                case FHCRC: {
                    if ((state.getHeaderFlag() & FHCRC) == FHCRC) {
                        if (buffer.remaining() < 2) {
                            return false;
                        }

                        final int myCrc = (int) state.getCrc32().getValue() & 0xffff;
                        final int passedCrc = getUShort(buffer, crc32);

                        if (myCrc != passedCrc) {
                            throw new IllegalStateException("Corrupt GZIP header");
                        }
                    }

                    state.setDecodeStatus(DecodeStatus.PAYLOAD);
                }
            }
        }

        return true;
    }

    /*
     * Reads GZIP member trailer.
     */
    private boolean decodeTrailer(Buffer buffer, GZipInputState state)
            throws TransformationException {

        if (buffer.remaining() < 8) {
            return false;
        }

        final Inflater inflater = state.getInflater();
        final CRC32 crc32 = state.getCrc32();
	// Uses left-to-right evaluation order
        final long inCrc32Value = crc32.getValue();
	if ((getUInt(buffer, crc32) != inCrc32Value) ||
	    // rfc1952; ISIZE is the input size modulo 2^32
	    (getUInt(buffer, crc32) != (inflater.getBytesWritten() & 0xffffffffL)))
	    throw new TransformationException("Corrupt GZIP trailer");

        return true;
    }
    
    private static long getUInt(Buffer buffer, CRC32 crc32) {
        final int short1 = getUShort(buffer, crc32);
        final int short2 = getUShort(buffer, crc32);
        return (((long) short2 << 16) | short1);
    }

    private static int getUShort(Buffer buffer, CRC32 crc32) {
        final int b1 = getUByte(buffer, crc32);
        final int b2 = getUByte(buffer, crc32);

        return (b2 << 8) | b1;
    }

    private static int getUByte(Buffer buffer, CRC32 crc32) {
	final byte b = buffer.get();
        crc32.update(b);

	return b & 0xff;
    }

    private static void skipBytes(Buffer buffer, int num, CRC32 crc32) {
        for (int i=0; i<num; i++) {
            getUByte(buffer, crc32);
        }
    }
    
    protected static final class GZipInputState
            extends LastResultAwareState<Buffer, Buffer> {
        private boolean isInitialized;

        /**
         * CRC-32 of uncompressed data.
         */
        private CRC32 crc32;

        /**
         * Decompressor for this stream.
         */
        private Inflater inflater;

        private DecodeStatus decodeStatus = DecodeStatus.INITIAL;

        private int headerFlag;

        private int headerParseStateValue;

        public boolean isInitialized() {
            return isInitialized;
        }

        public void setInitialized(boolean isInitialized) {
            this.isInitialized = isInitialized;
        }

        public Inflater getInflater() {
            return inflater;
        }

        public void setInflater(Inflater inflater) {
            this.inflater = inflater;
        }

        public CRC32 getCrc32() {
            return crc32;
        }

        public void setCrc32(CRC32 crc32) {
            this.crc32 = crc32;
        }

        public DecodeStatus getDecodeStatus() {
            return decodeStatus;
        }

        public void setDecodeStatus(DecodeStatus decodeStatus) {
            this.decodeStatus = decodeStatus;
        }

        public int getHeaderFlag() {
            return headerFlag;
        }

        public void setHeaderFlag(int headerFlag) {
            this.headerFlag = headerFlag;
        }

        public int getHeaderParseStateValue() {
            return headerParseStateValue;
        }

        public void setHeaderParseStateValue(int headerParseStateValue) {
            this.headerParseStateValue = headerParseStateValue;
        }
    }
}
