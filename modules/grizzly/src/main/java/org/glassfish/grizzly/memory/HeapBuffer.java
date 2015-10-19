/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2015 Oracle and/or its affiliates. All rights reserved.
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

import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.InvalidMarkException;
import java.nio.charset.Charset;
import java.util.Arrays;

import org.glassfish.grizzly.Buffer;

/**
 * {@link Buffer} implementation, which uses the {@link ByteBuffer} underneath.
 *
 * @see Buffer
 * @see MemoryManager
 * @see ByteBuffer
 *
 * @author Ken Cavanaugh
 * @author John Vieten
 * @author Alexey Stashok
 * @author Ryan Lubke
 *
 * @since 2.0
 */
public class HeapBuffer implements Buffer {
    public static volatile boolean DEBUG_MODE = false;

    // Dispose underlying Buffer flag
    protected boolean allowBufferDispose = false;

    protected Exception disposeStackTrace;

    protected byte[] heap;

    protected int offset;

    protected int pos;

    protected int cap;

    protected int lim;

    protected int mark = -1;

    protected boolean readOnly;

    protected ByteOrder order = ByteOrder.BIG_ENDIAN;

    protected boolean bigEndian = true;

    protected ByteBuffer byteBuffer;


    // ------------------------------------------------------------ Constructors

    protected HeapBuffer() {
    }

    protected HeapBuffer(final byte[] heap,
                         final int offset,
                         final int cap) {
        this.heap = heap;
        this.offset = offset;
        this.cap = cap;
        this.lim = this.cap;
    }

    @Override
    public final boolean isComposite() {
        return false;
    }

    @Override
    public HeapBuffer prepend(final Buffer header) {
        checkDispose();
        return this;
    }

    @Override
    public void trim() {
        checkDispose();
        flip();
    }
    
    @Override
    public void shrink() {
        checkDispose();
    }

    @Override
    public final boolean allowBufferDispose() {
        return allowBufferDispose;
    }

    @Override
    public final void allowBufferDispose(boolean allowBufferDispose) {
        this.allowBufferDispose = allowBufferDispose;
    }

    @Override
    public final boolean tryDispose() {
        if (allowBufferDispose) {
            dispose();
            return true;
        }

        return false;
    }

    @Override
    public void dispose() {
        prepareDispose();
        
        byteBuffer = null;
        heap = null;
        pos = 0;
        offset = 0;
        lim = 0;
        cap = 0;
        order = ByteOrder.BIG_ENDIAN;
        bigEndian = true;
    }

    protected final void prepareDispose() {
        checkDispose();
        if (DEBUG_MODE) { // if debug is on - clear the buffer content
            // Use static logic class to help JIT optimize the code
            DebugLogic.doDebug(this);
        }
    }

    @Override
    public ByteBuffer underlying() {
        checkDispose();
        return toByteBuffer();
    }

    @Override
    public final int capacity() {
        checkDispose();
        return cap;
    }

    @Override
    public final int position() {
        checkDispose();
        return pos;
    }

    @Override
    public final HeapBuffer position(final int newPosition) {
        checkDispose();
        pos = newPosition;
        if (mark > pos) mark = -1;
        return this;
    }

    @Override
    public final int limit() {
        checkDispose();
        return lim;
    }

    @Override
    public final HeapBuffer limit(final int newLimit) {
        checkDispose();
        lim = newLimit;
        if (mark > lim) mark = -1;
        return this;
    }

    @Override
    public final HeapBuffer mark() {
        mark = pos;
        return this;
    }

    @Override
    public final HeapBuffer reset() {
        int m = mark;
        if (m < 0)
            throw new InvalidMarkException();
        pos = m;
        return this;
    }

    @Override
    public final HeapBuffer clear() {
        pos = 0;
        lim = cap;
        mark = -1;
        return this;
    }

    @Override
    public final HeapBuffer flip() {
        lim = pos;
        pos = 0;
        mark = -1;
        return this;
    }

    @Override
    public final HeapBuffer rewind() {
        pos = 0;
        mark = -1;
        return this;
    }

    @Override
    public final int remaining() {
        return (lim - pos);
    }

    @Override
    public final boolean hasRemaining() {
        return (pos < lim);
    }

    @Override
    public boolean isReadOnly() {
        return readOnly;
    }

    @Override
    public final boolean isDirect() {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Buffer split(final int splitPosition) {
        checkDispose();
        
        if (splitPosition < 0 || splitPosition > cap) {
            throw new IllegalArgumentException("Invalid splitPosition value, should be 0 <= splitPosition <= capacity");
        }
        
        final int oldPosition = pos;
        final int oldLimit = lim;

        final HeapBuffer ret = createHeapBuffer(
                               splitPosition,
                               cap - splitPosition);

        cap = splitPosition;

        if (oldPosition < splitPosition) {
            pos = oldPosition;
        } else {
            pos = cap;
            ret.position(oldPosition - splitPosition);
        }

        if (oldLimit < splitPosition) {
            lim = oldLimit;
            ret.limit(0);
        } else {
            lim = cap;
            ret.limit(oldLimit - splitPosition);
        }

        return ret;
    }

    @Override
    public HeapBuffer slice() {
        return slice(pos, lim);
    }

    @Override
    public HeapBuffer slice(final int position, final int limit) {
        checkDispose();
        return createHeapBuffer(position, limit - position);
    }


    @Override
    public HeapBuffer duplicate() {
        checkDispose();

        final HeapBuffer duplicate =
                createHeapBuffer(0, cap);
        duplicate.position(pos);
        duplicate.limit(lim);
        return duplicate;
    }

    @Override
    public HeapBuffer asReadOnlyBuffer() {
        checkDispose();
        
        onShareHeap();
        final HeapBuffer b = new ReadOnlyHeapBuffer(heap, offset, cap);
        b.pos = pos;
        b.lim = lim;
        return b;
    }

    @Override
    public byte get() {
        if (!hasRemaining()) {
            throw new BufferUnderflowException();
        }
        return heap[offset + pos++];
    }

    @Override
    public byte get(int index) {
        if (index < 0 || index >= lim) {
            throw new IndexOutOfBoundsException();
        }
        return heap[offset + index];
    }

    @Override
    public HeapBuffer put(byte b) {
        if (!hasRemaining()) {
            throw new BufferOverflowException();
        }
        heap[offset + pos++] = b;
        return this;
    }

    @Override
    public HeapBuffer put(int index, byte b) {
        if (index < 0 || index >= lim) {
            throw new IndexOutOfBoundsException();
        }
        heap[offset + index] = b;
        return this;
    }

    @Override
    public HeapBuffer get(final byte[] dst) {
        return get(dst, 0, dst.length);
    }

    @Override
    public HeapBuffer get(final byte[] dst, final int offset, final int length) {
        if (remaining() < length) {
            throw new BufferUnderflowException();
        }
        System.arraycopy(heap, (this.offset + pos), dst, offset, length);
        pos += length;
        return this;
    }

    @Override
    public HeapBuffer put(final Buffer src) {
        put(src, src.position(), src.remaining());
        src.position(src.limit());
        return this;
    }

    @Override
    public HeapBuffer put(final Buffer src, final int position, final int length) {
        if (remaining() < length) {
            throw new BufferOverflowException();
        }

        final int oldPos = src.position();
        final int oldLim = src.limit();

        final int thisPos = pos; // Save the current pos for case, if src == this
        Buffers.setPositionLimit(src, position, position + length);

        src.get(heap, offset + thisPos, length);
        Buffers.setPositionLimit(src, oldPos, oldLim);
        pos = thisPos + length;


        return this;
    }

    @Override
    public Buffer get(final ByteBuffer dst) {
        final int length = dst.remaining();
        
        dst.put(heap, offset + pos, length);
        pos += length;
        
        return this;
    }

    @Override
    public Buffer get(final ByteBuffer dst, final int position, final int length) {
        final int oldPos = dst.position();
        final int oldLim = dst.limit();

        try {
            Buffers.setPositionLimit(dst, position, position + length);
            dst.put(heap, offset + pos, length);
            pos += length;
        } finally {
            Buffers.setPositionLimit(dst, oldPos, oldLim);
        }

        return this;
    }
    
    @Override
    public Buffer put(final ByteBuffer src) {
        final int length = src.remaining();
        
        src.get(heap, offset + pos, length);
        pos += length;
        
        return this;
    }

    @Override
    public Buffer put(final ByteBuffer src, final int position, final int length) {
        final int oldPos = src.position();
        final int oldLim = src.limit();

        try {
            Buffers.setPositionLimit(src, position, position + length);
            src.get(heap, offset + pos, length);
            pos += length;
        } finally {
            Buffers.setPositionLimit(src, oldPos, oldLim);
        }

        return this;
    }

    public static HeapBuffer wrap(byte[] heap) {
        return wrap(heap, 0, heap.length);
    }


    public static HeapBuffer wrap(byte[] heap, int off, int len) {
        return new HeapBuffer(heap, off, len);
    }


    @Override
    public HeapBuffer put(byte[] src) {
        return put(src, 0, src.length);
    }

    @Override
    public HeapBuffer put(byte[] src, int offset, int length) {
        if (remaining() < length) {
            throw new BufferOverflowException();
        }

        System.arraycopy(src, offset, heap, this.offset + pos, length);
        pos += length;

        return this;
    }

    @SuppressWarnings("deprecation")
    @Override
    public HeapBuffer put8BitString(final String s) {
        final int len = s.length();
        if (remaining() < len) {
            throw new BufferOverflowException();
        }

        s.getBytes(0, len, heap, offset + pos);
        pos += len;

        return this;
    }

    @Override
    public HeapBuffer compact() {
        final int length = remaining();
        System.arraycopy(heap, offset + pos, heap, offset, length);
        pos = length;
        lim = cap;
        return this;
    }

    @Override
    public ByteOrder order() {
        return order;
    }

    @Override
    public HeapBuffer order(ByteOrder bo) {
        order = bo;
        bigEndian = (order == ByteOrder.BIG_ENDIAN);
        return this;
    }

    @Override
    public char getChar() {
        if (remaining() < 2) {
            throw new BufferUnderflowException();
        }
        final char c = Bits.getChar(heap, offset + pos, bigEndian);
        pos += 2;
        return c;
    }

    @Override
    public char getChar(int index) {
        if (index < 0 || index >= (lim - 1)) {
            throw new IndexOutOfBoundsException();
        }
        return Bits.getChar(heap, offset + index, bigEndian);
    }

    @Override
    public HeapBuffer putChar(char value) {
        if (remaining() < 2) {
            throw new BufferUnderflowException();
        }

        Bits.putChar(heap, offset + pos, value, bigEndian);
        pos += 2;
        return this;
    }

    @Override
    public HeapBuffer putChar(int index, char value) {
        if (index < 0 || index >= (lim - 1)) {
            throw new IndexOutOfBoundsException();
        }
        Bits.putChar(heap, offset + index, value, bigEndian);
        return this;
    }

    @Override
    public short getShort() {
        if (remaining() < 2) {
            throw new BufferUnderflowException();
        }
        final short s = Bits.getShort(heap, offset + pos, bigEndian);
        pos += 2;
        return s;
    }

    @Override
    public short getShort(int index) {
        if (index < 0 || index >= (lim - 1)) {
            throw new IndexOutOfBoundsException();
        }
        return Bits.getShort(heap, offset + index, bigEndian);
    }

    @Override
    public HeapBuffer putShort(short value) {
        if (remaining() < 2) {
            throw new BufferUnderflowException();
        }
        Bits.putShort(heap, offset + pos, value, bigEndian);
        pos += 2;
        return this;
    }

    @Override
    public HeapBuffer putShort(int index, short value) {
        if (index < 0 || index >= (lim - 1)) {
            throw new IndexOutOfBoundsException();
        }
        Bits.putShort(heap, offset + index, value, bigEndian);
        return this;
    }

    @Override
    public int getInt() {
        if (remaining() < 4) {
            throw new BufferUnderflowException();
        }
        final int i = Bits.getInt(heap, offset + pos, bigEndian);
        pos += 4;
        return i;
    }

    @Override
    public int getInt(int index) {
        if (index < 0 || index >= (lim - 3)) {
            throw new IndexOutOfBoundsException();
        }
        return Bits.getInt(heap, offset + index, bigEndian);
    }

    @Override
    public HeapBuffer putInt(int value) {
        if (remaining() < 4) {
            throw new BufferUnderflowException();
        }
        Bits.putInt(heap, offset + pos, value, bigEndian);
        pos += 4;
        return this;
    }

    @Override
    public HeapBuffer putInt(int index, int value) {
        if (index < 0 || index >= (lim - 3)) {
            throw new IndexOutOfBoundsException();
        }
        Bits.putInt(heap, offset + index, value, bigEndian);
        return this;
    }

    @Override
    public long getLong() {
        if (remaining() < 8) {
            throw new BufferUnderflowException();
        }
        final long l = Bits.getLong(heap, offset + pos, bigEndian);
        pos += 8;
        return l;
    }

    @Override
    public long getLong(int index) {
        if (index < 0 || index >= (lim - 7)) {
            throw new IndexOutOfBoundsException();
        }
        return Bits.getLong(heap, offset + index, bigEndian);
    }

    @Override
    public HeapBuffer putLong(long value) {
        if (remaining() < 8) {
            throw new BufferUnderflowException();
        }
        Bits.putLong(heap, offset + pos, value, bigEndian);
        pos += 8;
        return this;
    }

    @Override
    public HeapBuffer putLong(int index, long value) {
        if (index < 0 || index >= (lim - 7)) {
            throw new IndexOutOfBoundsException();
        }
        Bits.putLong(heap, offset + index, value, bigEndian);
        return this;
    }

    @Override
    public float getFloat() {
        if (remaining() < 4) {
            throw new BufferUnderflowException();
        }
        final float f = Bits.getFloat(heap, offset + pos, bigEndian);
        pos += 4;
        return f;
    }

    @Override
    public float getFloat(int index) {
        if (index < 0 || index >= (lim - 3)) {
            throw new IndexOutOfBoundsException();
        }
        return Bits.getFloat(heap, offset + index, bigEndian);
    }

    @Override
    public HeapBuffer putFloat(float value) {
        if (remaining() < 4) {
            throw new BufferUnderflowException();
        }
        Bits.putFloat(heap, offset + pos, value, bigEndian);
        pos += 4;
        return this;
    }

    @Override
    public HeapBuffer putFloat(int index, float value) {
        if (index < 0 || index >= (lim - 3)) {
            throw new IndexOutOfBoundsException();
        }
        Bits.putFloat(heap, offset + index, value, bigEndian);
        return this;
    }

    @Override
    public double getDouble() {
        if (remaining() < 8) {
            throw new BufferUnderflowException();
        }
        final double d = Bits.getDouble(heap, offset + pos, bigEndian);
        pos += 8;
        return d;
    }

    @Override
    public double getDouble(int index) {
        if (index < 0 || index >= (lim - 7)) {
            throw new IndexOutOfBoundsException();
        }
        return Bits.getDouble(heap, offset + index, bigEndian);
    }

    @Override
    public HeapBuffer putDouble(double value) {
        if (remaining() < 8) {
            throw new BufferUnderflowException();
        }
        Bits.putDouble(heap, offset + pos, value, bigEndian);
        pos += 8;
        return this;
    }

    @Override
    public HeapBuffer putDouble(int index, double value) {
        if (index < 0 || index >= (lim - 7)) {
            throw new IndexOutOfBoundsException();
        }
        Bits.putDouble(heap, offset + index, value, bigEndian);
        return this;
    }
    
    @Override
    public int hashCode() {
        int result = (allowBufferDispose ? 1 : 0);
        result = 31 * result + (disposeStackTrace != null ? disposeStackTrace.hashCode() : 0);
        result = 31 * result + (heap != null ? Arrays.hashCode(heap) : 0);
        result = 31 * result + offset;
        result = 31 * result + pos;
        result = 31 * result + cap;
        result = 31 * result + lim;
        result = 31 * result + mark;
        result = 31 * result + (readOnly ? 1 : 0);
        result = 31 * result + (order != null ? order.hashCode() : 0);
        result = 31 * result + (bigEndian ? 1 : 0);
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Buffer) {
            Buffer that = (Buffer) obj;
            if (this.remaining() != that.remaining()) {
                return false;
            }
            int p = this.position();
            for (int i = this.limit() - 1, j = that.limit() - 1; i >= p; i--, j--) {
                byte v1 = this.get(i);
                byte v2 = that.get(j);
                if (v1 != v2) {
                    return false;
                }
            }
            return true;
        }

        return false;
    }

    @Override
    public int compareTo(Buffer o) {
        // taken from ByteBuffer#compareTo(...)
        int n = position() + Math.min(remaining(), o.remaining());
        for (int i = this.position(), j = o.position(); i < n; i++, j++) {
            byte v1 = this.get(i);
            byte v2 = o.get(j);
            if (v1 == v2)
                continue;
            if (v1 < v2)
                return -1;
            return +1;
        }

        return remaining() - o.remaining();
    }

    protected void checkDispose() {
        if (heap == null) {
            throw new IllegalStateException(
                    "HeapBuffer has already been disposed",
                    disposeStackTrace);
        }
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("HeapBuffer (" +
                System.identityHashCode(this) + ") ");
        sb.append("[pos=");
        sb.append(pos);
        sb.append(" lim=");
        sb.append(lim);
        sb.append(" cap=");
        sb.append(cap);
        sb.append(']');
        return sb.toString();
    }

    @Override
    public String toStringContent() {
        return toStringContent(Charset.defaultCharset(), pos, lim);
    }

    @Override
    public String toStringContent(final Charset charset) {
        return toStringContent(charset, pos, lim);
    }

    @Override
    public String toStringContent(Charset charset, final int position,
            final int limit) {
        checkDispose();
        if (charset == null) {
            charset = Charset.defaultCharset();
        }

        final boolean isRestoreByteBuffer = byteBuffer != null;
        int oldPosition = 0;
        int oldLimit = 0;

        if (isRestoreByteBuffer) {
            // ByteBuffer can be used by outer code - so save its state
            oldPosition = byteBuffer.position();
            oldLimit = byteBuffer.limit();
        }
        
        final ByteBuffer bb = toByteBuffer0(position, limit, false);

        try {
            return charset.decode(bb).toString();
        } finally {
            if (isRestoreByteBuffer) {
                Buffers.setPositionLimit(byteBuffer, oldPosition, oldLimit);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void dumpHex(java.lang.Appendable appendable) {
        Buffers.dumpBuffer(appendable, this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ByteBuffer toByteBuffer() {
        return toByteBuffer(pos, lim);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ByteBuffer toByteBuffer(final int position, final int limit) {
        return toByteBuffer0(position, limit, false);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final ByteBufferArray toByteBufferArray() {
        final ByteBufferArray array = ByteBufferArray.create();
        array.add(toByteBuffer());

        return array;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final ByteBufferArray toByteBufferArray(final int position,
                                                   final int limit) {
        return toByteBufferArray(ByteBufferArray.create(), position, limit);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final ByteBufferArray toByteBufferArray(final ByteBufferArray array) {
        array.add(toByteBuffer());
        return array;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final ByteBufferArray toByteBufferArray(final ByteBufferArray array,
            final int position, final int limit) {

        array.add(toByteBuffer(position, limit));

        return array;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final BufferArray toBufferArray() {
        final BufferArray array = BufferArray.create();
        array.add(this);

        return array;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final BufferArray toBufferArray(final int position,
                                                   final int limit) {
        return toBufferArray(BufferArray.create(), position, limit);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final BufferArray toBufferArray(final BufferArray array) {
        array.add(this);
        return array;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final BufferArray toBufferArray(final BufferArray array,
            final int position, final int limit) {

        final int oldPos = pos;
        final int oldLim = lim;

        pos = position;
        lim = limit;

        array.add(this, oldPos, oldLim);
        return array;
    }

    @Override
    public boolean release() {
        return tryDispose();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isExternal() {
        return false;
    }

    @Override
    public boolean hasArray() {
        return true;
    }

    @Override
    public int arrayOffset() {
        return offset;
    }

    @Override
    public byte[] array() {
        return heap;
    }

    // ------------------------------------------------------- Protected Methods


    protected void onShareHeap() {
    }

    /**
     * Create a new {@link HeapBuffer} based on the current heap.
     * 
     * @param offs relative offset, the absolute value will calculated as (this.offset + offs)
     * @param capacity
     * @return
     */
    protected HeapBuffer createHeapBuffer(final int offs, final int capacity) {
        onShareHeap();
        
        return new HeapBuffer(
                heap,
                offs + offset,
                capacity);
    }

    protected ByteBuffer toByteBuffer0(final int pos,
                                       final int lim,
                                       final boolean slice) {
        if (byteBuffer == null) {
            byteBuffer = ByteBuffer.wrap(heap);
        }

        Buffers.setPositionLimit(byteBuffer, offset + pos, offset + lim);

        return ((slice) ? byteBuffer.slice() : byteBuffer);

    }


    // ---------------------------------------------------------- Nested Classes

    private static class DebugLogic {
        static void doDebug(HeapBuffer heapBuffer) {
            heapBuffer.clear();
            while(heapBuffer.hasRemaining()) {
                heapBuffer.put((byte) 0xFF);
            }
            heapBuffer.flip();
            heapBuffer.disposeStackTrace = new Exception("HeapBuffer was disposed from: ");
        }
    }

}
