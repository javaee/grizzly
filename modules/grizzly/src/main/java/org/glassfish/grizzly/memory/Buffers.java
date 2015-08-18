/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2015 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.memory;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Formatter;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.grizzly.Appender;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.localization.LogMessages;

/**
 * Class has useful methods to simplify the work with {@link Buffer}s.
 *
 * @see Buffer
 *
 * @author Alexey Stashok
 */

public class Buffers {
    private static final Logger LOGGER = Grizzly.logger(Buffers.class);

    private static final Appender<Buffer> APPENDER_DISPOSABLE = new BuffersAppender(true);
    private static final Appender<Buffer> APPENDER_NOT_DISPOSABLE = new BuffersAppender(false);

    /**
     * Get the {@link Appender} which knows how to append {@link Buffer}s.
     * Returned {@link Appender} uses the same {@link Buffer} appending rules as
     * described here {@link Buffers#appendBuffers(org.glassfish.grizzly.memory.MemoryManager, org.glassfish.grizzly.Buffer, org.glassfish.grizzly.Buffer, boolean)}.
     * 
     * @param isCompositeBufferDisposable if as the result of {@link Buffer}s
     *      appending a new {@link CompositeBuffer} will be created - its
     *      {@link CompositeBuffer#allowBufferDispose(boolean)} value will be set
     *      according to this parameter.
     * @return the {@link Buffer} {@link Appender}.
     */
    public static Appender<Buffer> getBufferAppender(
            final boolean isCompositeBufferDisposable) {
        return isCompositeBufferDisposable ?
                APPENDER_DISPOSABLE : APPENDER_NOT_DISPOSABLE;
    }
    
    private static class BuffersAppender implements Appender<Buffer> {
        private final boolean isCompositeBufferDisposable;

        public BuffersAppender(boolean isCompositeBufferDisposable) {
            this.isCompositeBufferDisposable = isCompositeBufferDisposable;
        }
        
        @Override
        public Buffer append(final Buffer element1, final Buffer element2) {
            return Buffers.appendBuffers(null, element1, element2,
                    isCompositeBufferDisposable);
        }
    }
    
    public static final ByteBuffer EMPTY_BYTE_BUFFER = ByteBuffer.allocate(0);
    @SuppressWarnings("unused")
    public static final ByteBuffer[] EMPTY_BYTE_BUFFER_ARRAY = new ByteBuffer[0];

    public static final Buffer EMPTY_BUFFER;

    static {
        EMPTY_BUFFER = new ByteBufferWrapper(ByteBuffer.allocate(0)) {
            @Override
            public void dispose() {
            }
        };
    }

    /**
     * Returns {@link Buffer}, which wraps the {@link String}.
     *
     * @param memoryManager {@link MemoryManager}, which should be
     *                       used for wrapping.
     * @param s {@link String}
     *
     * @return {@link Buffer} wrapper on top of passed {@link String}.
     */
    public static Buffer wrap(final MemoryManager memoryManager,
            final String s) {
        return wrap(memoryManager, s, Charset.defaultCharset());
    }

    /**
     * Returns {@link Buffer}, which wraps the {@link String} with the specific
     * {@link Charset}.
     *
     * @param memoryManager {@link MemoryManager}, which should be
     *                       used for wrapping.
     * @param s {@link String}
     * @param charset {@link Charset}, which will be used, when converting
     * {@link String} to byte array.
     *
     * @return {@link Buffer} wrapper on top of passed {@link String}.
     */
    public static Buffer wrap(final MemoryManager memoryManager,
            final String s, final Charset charset) {
        try {
            final byte[] byteRepresentation = s.getBytes(charset.name());
            return wrap(memoryManager, byteRepresentation);
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Returns {@link Buffer}, which wraps the byte array.
     *
     * @param memoryManager {@link MemoryManager}, which should be
     *                       used for wrapping.
     * @param array byte array to wrap.
     *
     * @return {@link Buffer} wrapper on top of passed byte array.
     */
    public static Buffer wrap(final MemoryManager memoryManager,
            final byte[] array) {
        return wrap(memoryManager, array, 0, array.length);
    }

    /**
     * Returns {@link Buffer}, which wraps the part of byte array with
     * specific offset and length.
     *
     * @param memoryManager {@link MemoryManager}, which should be
     *                       used for wrapping.
     * @param array byte array to wrap
     * @param offset byte buffer offset
     * @param length byte buffer length
     *
     * @return {@link Buffer} wrapper on top of passed byte array.
     */
    public static Buffer wrap(MemoryManager memoryManager,
            final byte[] array, final int offset, final int length) {
        if (memoryManager == null) {
            memoryManager = getDefaultMemoryManager();
        }

        if (memoryManager instanceof WrapperAware) {
            return ((WrapperAware) memoryManager).wrap(array, offset, length);
        }

        final Buffer buffer = memoryManager.allocate(length);
        buffer.put(array, offset, length);
        buffer.flip();
        return buffer;
    }

    /**
     * Returns {@link Buffer}, which wraps the {@link ByteBuffer}.
     *
     * @param memoryManager {@link MemoryManager}, which should be
     *                       used for wrapping.
     * @param byteBuffer {@link ByteBuffer} to wrap
     *
     * @return {@link Buffer} wrapper on top of passed {@link ByteBuffer}.
     */
    public static Buffer wrap(final MemoryManager memoryManager,
            final ByteBuffer byteBuffer) {
        if (memoryManager instanceof WrapperAware) {
            return ((WrapperAware) memoryManager).wrap(byteBuffer);
        } else if (byteBuffer.hasArray()) {
            return wrap(memoryManager, byteBuffer.array(),
                    byteBuffer.arrayOffset() + byteBuffer.position(),
                    byteBuffer.remaining());
        }

        throw new IllegalStateException("Can not wrap ByteBuffer");
    }



    /**
     * Slice {@link ByteBuffer} of required size from big chunk.
     * Passed chunk position will be changed, after the slicing (chunk.position += size).
     *
     * @param chunk big {@link ByteBuffer} pool.
     * @param size required slice size.
     *
     * @return sliced {@link ByteBuffer} of required size.
     */
    public static ByteBuffer slice(final ByteBuffer chunk, final int size) {
        chunk.limit(chunk.position() + size);
        final ByteBuffer view = chunk.slice();
        chunk.position(chunk.limit());
        chunk.limit(chunk.capacity());

        return view;
    }


    /**
     * Get the {@link ByteBuffer}'s slice basing on its passed position and limit.
     * Position and limit values of the passed {@link ByteBuffer} won't be changed.
     * The result {@link ByteBuffer} position will be equal to 0, and limit
     * equal to number of sliced bytes (limit - position).
     *
     * @param byteBuffer {@link ByteBuffer} to slice/
     * @param position the position in the passed byteBuffer, the slice will start from.
     * @param limit the limit in the passed byteBuffer, the slice will be ended.
     *
     * @return sliced {@link ByteBuffer} of required size.
     */
    public static ByteBuffer slice(final ByteBuffer byteBuffer,
            final int position, final int limit) {
        final int oldPos = byteBuffer.position();
        final int oldLimit = byteBuffer.limit();

        setPositionLimit(byteBuffer, position, limit);

        final ByteBuffer slice = byteBuffer.slice();

        setPositionLimit(byteBuffer, oldPos, oldLimit);

        return slice;
    }

    public static String toStringContent(final ByteBuffer byteBuffer,
            Charset charset, final int position, final int limit) {

        if (charset == null) {
            charset = Charset.defaultCharset();
        }

        final int oldPosition = byteBuffer.position();
        final int oldLimit = byteBuffer.limit();
        setPositionLimit(byteBuffer, position, limit);
        
        try {
            return charset.decode(byteBuffer).toString();
        } finally {
            setPositionLimit(byteBuffer, oldPosition, oldLimit);
        }
    }

    public static void setPositionLimit(final Buffer buffer,
            final int position, final int limit) {
        buffer.limit(limit);
        buffer.position(position);
    }

    public static void setPositionLimit(final ByteBuffer buffer,
            final int position, final int limit) {
        buffer.limit(limit);
        buffer.position(position);
    }

    public static void put(final ByteBuffer srcBuffer, final int srcOffset,
            final int length, final ByteBuffer dstBuffer) {

        if (dstBuffer.remaining() < length) {
            LOGGER.log(Level.WARNING,
                    LogMessages.WARNING_GRIZZLY_BUFFERS_OVERFLOW_EXCEPTION(
                    srcBuffer, srcOffset, length, dstBuffer));
            throw new BufferOverflowException();
        }

        if (srcBuffer.hasArray() && dstBuffer.hasArray()) {

            System.arraycopy(srcBuffer.array(),
                    srcBuffer.arrayOffset() + srcOffset,
                    dstBuffer.array(),
                    dstBuffer.arrayOffset() + dstBuffer.position(),
                    length);
            dstBuffer.position(dstBuffer.position() + length);
        } else {
            for(int i = srcOffset; i < srcOffset + length; i++) {
                dstBuffer.put(srcBuffer.get(i));
            }
        }
    }

    public static void put(final Buffer src, final int position,
            final int length, final Buffer dstBuffer) {

        if (dstBuffer.remaining() < length) {
            throw new BufferOverflowException();
        }

        if (!src.isComposite()) {
            final ByteBuffer srcByteBuffer = src.toByteBuffer();
            if (srcByteBuffer.hasArray()) {
                dstBuffer.put(srcByteBuffer.array(),
                        srcByteBuffer.arrayOffset() + position, length);
            } else {
                for(int i=0; i<length; i++) {
                    dstBuffer.put(srcByteBuffer.get(position + i));
                }
            }
        } else {
            final ByteBufferArray array = src.toByteBufferArray(position,
                    position + length);

            final ByteBuffer[] srcByteBuffers = array.getArray();
            for (int i = 0; i < array.size(); i++) {
                final ByteBuffer srcByteBuffer = srcByteBuffers[i];
                final int initialPosition = srcByteBuffer.position();
                final int srcByteBufferLen = srcByteBuffer.remaining();

                if (srcByteBuffer.hasArray()) {
                    dstBuffer.put(srcByteBuffer.array(),
                            srcByteBuffer.arrayOffset() + initialPosition,
                            srcByteBufferLen);
                } else {
                    for (int j = 0; j < srcByteBufferLen; i++) {
                        dstBuffer.put(srcByteBuffer.get(initialPosition + j));
                    }
                }
            }

            array.restore();
            array.recycle();
        }
    }

    public static void get(final ByteBuffer srcBuffer,
            final byte[] dstBytes, final int dstOffset, final int length) {

        if (srcBuffer.hasArray()) {
            if (length > srcBuffer.remaining()) {
                throw new BufferUnderflowException();
            }

            System.arraycopy(srcBuffer.array(),
                    srcBuffer.arrayOffset() + srcBuffer.position(),
                    dstBytes, dstOffset, length);
            srcBuffer.position(srcBuffer.position() + length);
        } else {
            srcBuffer.get(dstBytes, dstOffset, length);
        }
    }

    public static void put(final byte[] srcBytes, final int srcOffset,
            final int length, final ByteBuffer dstBuffer) {
        if (dstBuffer.hasArray()) {
            if (length > dstBuffer.remaining()) {
                throw new BufferOverflowException();
            }

            System.arraycopy(srcBytes, srcOffset, dstBuffer.array(),
                    dstBuffer.arrayOffset() + dstBuffer.position(), length);
            dstBuffer.position(dstBuffer.position() + length);
        } else {
            dstBuffer.put(srcBytes, srcOffset, length);
        }
    }

    /**
     * Append two {@link Buffer}s.
     * If one of the {@link Buffer}s is null - then another {@link Buffer} will
     * be returned as result.
     * If the first {@link Buffer} is {@link CompositeBuffer} then the second
     * {@link Buffer} will be appended to it via
     * {@link CompositeBuffer#append(Buffer)}, else if the second
     * {@link Buffer} is {@link CompositeBuffer} then the first {@link Buffer}
     * will be prepended to it via
     * {@link CompositeBuffer#prepend(org.glassfish.grizzly.Buffer)}.
     * If none of the {@link Buffer} parameters is null nor
     * {@link CompositeBuffer}s - then new {@link CompositeBuffer} will be created
     * and both {@link Buffer}s will be added there. The resulting
     * {@link CompositeBuffer} will be disallowed for disposal.
     * 
     * @param memoryManager the {@link MemoryManager} to use if a new {@link Buffer}
     *                      needs to be allocated in order to perform the requested
     *                      operation.
     * @param buffer1 the {@link Buffer} to append to.
     * @param buffer2 the {@link Buffer} to append.
     * 
     * @return the result of appending of two {@link Buffer}s.
     */
    public static Buffer appendBuffers(final MemoryManager memoryManager,
            final Buffer buffer1, final Buffer buffer2) {
        return appendBuffers(memoryManager, buffer1, buffer2, false);
    }
    
    /**
     * Append two {@link Buffer}s.
     * If one of the {@link Buffer}s is null - then another {@link Buffer} will
     * be returned as result.
     * If the first {@link Buffer} is {@link CompositeBuffer} then the second
     * {@link Buffer} will be appended to it via
     * {@link CompositeBuffer#append(Buffer)}, else if the second
     * {@link Buffer} is {@link CompositeBuffer} then the first {@link Buffer}
     * will be prepended to it via
     * {@link CompositeBuffer#prepend(org.glassfish.grizzly.Buffer)}.
     * If none of the {@link Buffer} parameters is null nor
     * {@link CompositeBuffer}s - then new {@link CompositeBuffer} will be created
     * and both {@link Buffer}s will be added there. The resulting
     * {@link CompositeBuffer} will be assigned according to the
     * <code>isCompositeBufferDisposable</code> parameter.
     * 
     * @param memoryManager the {@link MemoryManager} to use if a new {@link Buffer}
     *                      needs to be allocated in order to perform the requested
     *                      operation.
     * @param buffer1 the {@link Buffer} to append to.
     * @param buffer2 the {@link Buffer} to append.
     * @param isCompositeBufferDisposable flag indicating whether or not the
     *                                    resulting composite buffer may be disposed.
     * 
     * @return the result of appending of two {@link Buffer}s.
     */
    public static Buffer appendBuffers(final MemoryManager memoryManager,
            final Buffer buffer1, final Buffer buffer2,
            final boolean isCompositeBufferDisposable) {

        if (buffer1 == null) {
            return buffer2;
        } else if (buffer2 == null) {
            return buffer1;
        }
        
        if (buffer1.order() != buffer2.order()) {
            LOGGER.fine("Appending buffers with different ByteOrder."
                    + "The result Buffer's order will be the same as the first Buffer's ByteOrder");
            buffer2.order(buffer1.order());
        }
        
        // we can only append to or prepend buffer1 if the limit()
        // is the same as capacity.  If it's not, then appending or
        // prepending effectively clobbers the limit causing an invalid
        // view of the data.  So instead, we allocate a new CompositeBuffer
        // and append buffer1 and buffer2 to it.  The underlying buffers
        // aren't changed so the limit they have is maintained.
        if (buffer1.isComposite() && (buffer1.capacity() == buffer1.limit())) {
            ((CompositeBuffer) buffer1).append(buffer2);
            return buffer1;
        } if (buffer2.isComposite() && (buffer2.position() == 0)) {
            ((CompositeBuffer) buffer2).prepend(buffer1);
            return buffer2;
        } else {
            final CompositeBuffer compositeBuffer =
                    CompositeBuffer.newBuffer(memoryManager);

            compositeBuffer.order(buffer1.order());
            compositeBuffer.append(buffer1);
            compositeBuffer.append(buffer2);
            compositeBuffer.allowBufferDispose(isCompositeBufferDisposable);
            
            return compositeBuffer;
        }
    }

    /**
     * Fill the {@link Buffer} with the specific byte value. {@link Buffer}'s
     * position won't be changed.
     *
     * @param buffer {@link Buffer}
     * @param b value
     */
    public static void fill(final Buffer buffer, final byte b) {
        fill(buffer, buffer.position(), buffer.limit(), b);
    }

    /**
     * Fill the {@link Buffer}'s part [position, limit) with the specific byte value starting from the
     * {@link Buffer}'s position won't be changed.
     *
     * @param buffer {@link Buffer}
     * @param position {@link Buffer} position to start with (inclusive)
     * @param limit {@link Buffer} limit, where filling ends (exclusive)
     * @param b value
     */
    public static void fill(final Buffer buffer, final int position,
            final int limit, final byte b) {
        if (!buffer.isComposite()) {
            final ByteBuffer byteBuffer = buffer.toByteBuffer();
            fill(byteBuffer, position, limit, b);
        } else {
            final ByteBufferArray array = buffer.toByteBufferArray(position, limit);
            final ByteBuffer[] byteBuffers = array.getArray();
            final int size = array.size();

            for (int i = 0; i < size; i++) {
                final ByteBuffer byteBuffer = byteBuffers[i];
                fill(byteBuffer, b);
            }

            array.restore();
            array.recycle();
        }
    }

    /**
     * Fill the {@link ByteBuffer} with the specific byte value. {@link ByteBuffer}'s
     * position won't be changed.
     *
     * @param byteBuffer {@link ByteBuffer}
     * @param b value
     */
    public static void fill(final ByteBuffer byteBuffer, final byte b) {
        fill(byteBuffer, byteBuffer.position(), byteBuffer.limit(), b);
    }

    /**
     * Fill the {@link ByteBuffer}'s part [position, limit) with the specific byte value starting from the
     * {@link ByteBuffer}'s position won't be changed.
     *
     * @param byteBuffer {@link ByteBuffer}
     * @param position {@link ByteBuffer} position to start with (inclusive)
     * @param limit {@link Buffer} limit, where filling ends (exclusive)
     * @param b value
     */
    public static void fill(final ByteBuffer byteBuffer, final int position,
            final int limit, final byte b) {
        if (byteBuffer.hasArray()) {
            final int arrayOffset = byteBuffer.arrayOffset();
            Arrays.fill(byteBuffer.array(), arrayOffset + position,
                    arrayOffset + limit, b);
        } else {
            for (int i = position; i < limit; i++) {
                byteBuffer.put(i, b);
            }
        }
    }

    /**
     * Clones the source {@link Buffer}.
     * The method returns a new {@link Buffer} instance, which has the same content.
     * Please note, source and result {@link Buffer}s have the same content,
     * but it is *not* shared, so the following content changes in one of the
     * {@link Buffer}s won't be visible in another one.
     *
     * @param srcBuffer the source {@link Buffer}.
     * @return the cloned {@link Buffer}.
     */
    public static Buffer cloneBuffer(final Buffer srcBuffer) {
        return cloneBuffer(srcBuffer, srcBuffer.position(), srcBuffer.limit());
    }

    /**
     * Clones the source {@link Buffer}.
     * The method returns a new {@link Buffer} instance, which has the same content.
     * Please note, source and result {@link Buffer}s have the same content,
     * but it is *not* shared, so the following content changes in one of the
     * {@link Buffer}s won't be visible in another one.
     *
     * @param srcBuffer the source {@link Buffer}.
     * @param position the start position in the srcBuffer
     * @param limit the end position in the srcBuffer
     * @return the cloned {@link Buffer}.
     */
    public static Buffer cloneBuffer(final Buffer srcBuffer,
            final int position, final int limit) {
        final int srcLength = limit - position;
        if (srcLength == 0) { // make sure clone doesn't return EMPTY_BUFFER 
            return wrap(getDefaultMemoryManager(), EMPTY_BYTE_BUFFER);
        }
        
        final Buffer clone = getDefaultMemoryManager().allocate(srcLength);
        clone.put(srcBuffer, position, srcLength);
        clone.order(srcBuffer.order());
        
        return clone.flip();
    }

    /**
     * Reads data from the {@link FileChannel} into the {@link Buffer}.
     * 
     * @param fileChannel the {@link FileChannel} to read data from.
     * @param buffer the destination {@link Buffer}.
     * @return the number of bytes read, or <tt>-1</tt> if the end of file is reached.
     * 
     * @throws IOException 
     */
    public static long readFromFileChannel(final FileChannel fileChannel,
            final Buffer buffer) throws IOException {

        final long bytesRead;
        
        if (!buffer.isComposite()) {
            final ByteBuffer bb = buffer.toByteBuffer();
            final int oldPos = bb.position();
            bytesRead = fileChannel.read(bb);
            bb.position(oldPos);
        } else {
            final ByteBufferArray array = buffer.toByteBufferArray();
            bytesRead = fileChannel.read(
                    array.getArray(), 0, array.size());

            array.restore();
            array.recycle();
        }
        
        if (bytesRead > 0) {
            buffer.position(buffer.position() + (int) bytesRead);
        }
        
        return bytesRead;
    }
    
    /**
     * Writes data from the {@link Buffer} into the {@link FileChannel}.
     * 
     * @param fileChannel the {@link FileChannel} to write data to.
     * @param buffer the source {@link Buffer}.
     * @return the number of bytes written, possibly zero.
     * 
     * @throws IOException 
     */
    @SuppressWarnings("UnusedDeclaration")
    public static long writeToFileChannel(final FileChannel fileChannel,
            final Buffer buffer) throws IOException {

        final long bytesWritten;
        
        if (!buffer.isComposite()) {
            final ByteBuffer bb = buffer.toByteBuffer();
            final int oldPos = bb.position();
            bytesWritten = fileChannel.write(bb);
            bb.position(oldPos);
        } else {
            final ByteBufferArray array = buffer.toByteBufferArray();
            bytesWritten = fileChannel.write(
                    array.getArray(), 0, array.size());

            array.restore();
            array.recycle();
        }

        if (bytesWritten > 0) {
            buffer.position(buffer.position() + (int) bytesWritten);
        }
        
        return bytesWritten;
    }

    /**
     * Returns the {@link Buffer}'s {@link String} representation in a form:
     * {@link Buffer#toString()} + "[" + <head-chunk> + "..." + <tail-chunk> + "]"
     * For example:
     * <pre>HeapBuffer (1781633478) [pos=0 lim=285 cap=285][abcde...xyz]</pre>
     * 
     * @param buffer the {@link Buffer}, could be <tt>null</tt>
     * @param headBytesCount the number of heading bytes to include (larger or equal to 0)
     * @param tailBytesCount the number of tailing bytes to include (larger or equal to 0)
     * @return the {@link Buffer}'s {@link String} representation, or <tt>null</tt>,
     *      if the {@link Buffer} is <tt>null</tt>
     */
    public String toStringContent(final Buffer buffer,
            final int headBytesCount, final int tailBytesCount) {
        if (buffer == null) {
            return null;
        }

        return toStringContent(buffer, headBytesCount, tailBytesCount,
                Charset.defaultCharset());
    }
    
    /**
     * Returns the {@link Buffer}'s {@link String} representation in a form:
     * {@link Buffer#toString()} + "[" + <head-chunk> + "..." + <tail-chunk> + "]"
     * For example:
     * <pre>HeapBuffer (1781633478) [pos=0 lim=285 cap=285][abcde...xyz]</pre>
     * 
     * @param buffer the {@link Buffer}, could be <tt>null</tt>
     * @param headBytesCount the number of heading bytes to include (larger or equal to 0)
     * @param tailBytesCount the number of tailing bytes to include (larger or equal to 0)
     * @param charset {@link Charset}, if null the {@link Charset#defaultCharset()} will
     *          be used
     * @return the {@link Buffer}'s {@link String} representation, or <tt>null</tt>,
     *      if the {@link Buffer} is <tt>null</tt>
     */
    public String toStringContent(final Buffer buffer,
            final int headBytesCount, final int tailBytesCount,
            final Charset charset) {
        if (buffer == null) {
            return null;
        }
        
        if (headBytesCount < 0 || tailBytesCount < 0) {
            throw new IllegalArgumentException("count can't be negative");
        }
        
        final String toString = buffer.toString();
        final StringBuilder sb = new StringBuilder(
                toString.length() + headBytesCount + tailBytesCount + 5);
        
        sb.append(toString);
        
        if (buffer.remaining() <= headBytesCount + tailBytesCount) {
            sb.append('[').append(buffer.toStringContent(charset)).append(']');
        } else {
            sb.append('[');
            if (headBytesCount > 0) {
                sb.append(buffer.toStringContent(charset,
                        buffer.position(), buffer.position() + headBytesCount));
            }
            sb.append("...");
            if (tailBytesCount > 0) {
                sb.append(buffer.toStringContent(charset,
                        buffer.limit() - tailBytesCount, buffer.limit()));
            }
            sb.append(']');
        }
        
        return sb.toString();
    }

    /**
     * Generates a hex dump of the provided {@link Buffer}.
     * @param appendable the {@link Appendable} to write the hex dump to.
     * @param buffer the {@link Buffer} to dump.
     *
     * @since 2.3.23
     */
    @SuppressWarnings("unused")
    public static void dumpBuffer(final Appendable appendable, final Buffer buffer) {
        final Formatter formatter = new Formatter(appendable);
        dumpBuffer0(formatter, appendable, buffer);
    }

    @SuppressWarnings("UnusedParameters")
    private static void dumpBuffer0(final Formatter formatter,
                                    final Appendable appendable,
                                    final Buffer buffer) {
        if (buffer.isComposite()) {
            final BufferArray bufferArray = buffer.toBufferArray();
            final int size = bufferArray.size();
            final Buffer[] buffers = bufferArray.getArray();
            formatter.format("%s\n", buffer.toString());
            for (int i = 0; i < size; i++) {
                dumpBuffer0(formatter, appendable, buffers[i]);
            }
            formatter.format("End CompositeBuffer (%d)", System.identityHashCode(buffer));
        } else {
            dumpBuffer0(formatter, buffer);
        }
    }


    private static void dumpBuffer0(final Formatter formatter, final Buffer buffer) {
        formatter.format("%s\n", buffer.toString());
        int line = 0;
        for (int i = 0, len = buffer.remaining() / 16; i < len; i++, line += 16) {
            byte b0  = buffer.get(line);
            byte b1  = buffer.get(line + 1);
            byte b2  = buffer.get(line + 2);
            byte b3  = buffer.get(line + 3);
            byte b4  = buffer.get(line + 4);
            byte b5  = buffer.get(line + 5);
            byte b6  = buffer.get(line + 6);
            byte b7  = buffer.get(line + 7);
            byte b8  = buffer.get(line + 8);
            byte b9  = buffer.get(line + 9);
            byte b10 = buffer.get(line + 10);
            byte b11 = buffer.get(line + 11);
            byte b12 = buffer.get(line + 12);
            byte b13 = buffer.get(line + 13);
            byte b14 = buffer.get(line + 14);
            byte b15 = buffer.get(line + 15);

            formatter.format(DumpStrings.DUMP_STRINGS[15],
                    line,
                    b0, b1, b2, b3, b4, b5, b6, b7, b8,
                    b9, b10, b11, b12, b13, b14, b15,
                    getChar(b0), getChar(b1), getChar(b2), getChar(b3),
                    getChar(b4), getChar(b5), getChar(b6), getChar(b7),
                    getChar(b8), getChar(b9), getChar(b10), getChar(b11),
                    getChar(b12), getChar(b13), getChar(b14), getChar(b15));
        }
        int remaining = buffer.remaining() % 16;
        if (remaining > 0) {
            final Object[] args = new Object[(remaining << 1) + 1];
            args[0] = remaining + line;
            for (int i = 0, aIdx = 1; i < remaining; i++, aIdx++) {
                final int b = buffer.get(line + i);
                args[aIdx] = b;
                args[aIdx + remaining] = getChar(b);
            }
            formatter.format(DumpStrings.DUMP_STRINGS[remaining - 1], args);
        }
    }


    // --------------------------------------------------------- Private Methods


    private static char getChar(final int val) {
        final char c = (char) val;
        return ((Character.isWhitespace(c) || Character.isISOControl(c)) ? '.' : c);
    }

    private static MemoryManager getDefaultMemoryManager() {
        return MemoryManager.DEFAULT_MEMORY_MANAGER;
    }


    // ---------------------------------------------------------- Nested Classes


    private static final class DumpStrings {

        private static final String[] DUMP_STRINGS = {
                "%10d   %02x                                                         %c\n",
                "%10d   %02x %02x                                                      %c%c\n",
                "%10d   %02x %02x  %02x                                                  %c%c%c\n",
                "%10d   %02x %02x  %02x %02x                                               %c%c%c%c\n",
                "%10d   %02x %02x  %02x %02x  %02x                                           %c%c%c%c%c\n",
                "%10d   %02x %02x  %02x %02x  %02x %02x                                        %c%c%c%c%c%c\n",
                "%10d   %02x %02x  %02x %02x  %02x %02x  %02x                                    %c%c%c%c%c%c%c\n",
                "%10d   %02x %02x  %02x %02x  %02x %02x  %02x %02x                                 %c%c%c%c%c%c%c%c\n",
                "%10d   %02x %02x  %02x %02x  %02x %02x  %02x %02x    %02x                           %c%c%c%c%c%c%c%c%c\n",
                "%10d   %02x %02x  %02x %02x  %02x %02x  %02x %02x    %02x %02x                        %c%c%c%c%c%c%c%c%c%c\n",
                "%10d   %02x %02x  %02x %02x  %02x %02x  %02x %02x    %02x %02x  %02x                    %c%c%c%c%c%c%c%c%c%c%c\n",
                "%10d   %02x %02x  %02x %02x  %02x %02x  %02x %02x    %02x %02x  %02x %02x                 %c%c%c%c%c%c%c%c%c%c%c%c\n",
                "%10d   %02x %02x  %02x %02x  %02x %02x  %02x %02x    %02x %02x  %02x %02x  %02x             %c%c%c%c%c%c%c%c%c%c%c%c%c\n",
                "%10d   %02x %02x  %02x %02x  %02x %02x  %02x %02x    %02x %02x  %02x %02x  %02x %02x          %c%c%c%c%c%c%c%c%c%c%c%c%c%c\n",
                "%10d   %02x %02x  %02x %02x  %02x %02x  %02x %02x    %02x %02x  %02x %02x  %02x %02x  %02x      %c%c%c%c%c%c%c%c%c%c%c%c%c%c%c\n",
                "%10d   %02x %02x  %02x %02x  %02x %02x  %02x %02x    %02x %02x  %02x %02x  %02x %02x  %02x %02x   %c%c%c%c%c%c%c%c%c%c%c%c%c%c%c%c\n"
        };

    } // END DumpStrings
}
