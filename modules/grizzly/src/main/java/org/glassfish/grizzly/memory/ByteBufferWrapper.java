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

import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
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
 */
public class ByteBufferWrapper implements Buffer {
    public static volatile boolean DEBUG_MODE = false;

    protected ByteBuffer visible;

    // Dispose underlying Buffer flag
    protected boolean allowBufferDispose = false;
    
    protected Exception disposeStackTrace;

    protected ByteBufferWrapper() {
        this(null);
    }

    public ByteBufferWrapper(final ByteBuffer underlyingByteBuffer) {
        visible = underlyingByteBuffer;
    }

    @Override
    public final boolean isComposite() {
        return false;
    }

    @Override
    public ByteBufferWrapper prepend(final Buffer header) {
        checkDispose();
        return this;
    }

    @Override
    public void trim() {
        checkDispose() ;
        flip();
    }

    @Override
    public void shrink() {
        checkDispose();
    }

    @Override
    public boolean isDirect() {
        checkDispose();
        return visible.isDirect();
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
        visible = null;
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
        return visible;
    }

    @Override
    public final int capacity() {
        return visible.capacity();
    }

    @Override
    public final int position() {
        checkDispose();
        return visible.position();
    }

    @Override
    public final ByteBufferWrapper position(final int newPosition) {
        checkDispose();
        visible.position(newPosition);
        return this;
    }

    @Override
    public final int limit() {
        checkDispose();
        return visible.limit();
    }

    @Override
    public final ByteBufferWrapper limit(final int newLimit) {
        checkDispose();
        visible.limit(newLimit);
        return this;
    }

    @Override
    public final ByteBufferWrapper mark() {
        checkDispose();
        visible.mark();
        return this;
    }

    @Override
    public final ByteBufferWrapper reset() {
        checkDispose();
        visible.reset();
        return this;
    }

    @Override
    public final ByteBufferWrapper clear() {
        checkDispose();
        visible.clear();
        return this;
    }

    @Override
    public final ByteBufferWrapper flip() {
        checkDispose();
        visible.flip();
        return this;
    }

    @Override
    public final ByteBufferWrapper rewind() {
        checkDispose();
        visible.rewind();
        return this;
    }

    @Override
    public final int remaining() {
        checkDispose();
        return visible.remaining();
    }

    @Override
    public final boolean hasRemaining() {
        checkDispose();
        return visible.hasRemaining();
    }

    @Override
    public boolean isReadOnly() {
        checkDispose();
        return visible.isReadOnly();
    }

    @Override
    public Buffer split(final int splitPosition) {
        checkDispose();
        final int cap = capacity();
        
        if (splitPosition < 0 || splitPosition > cap) {
            throw new IllegalArgumentException("Invalid splitPosition value, should be 0 <= splitPosition <= capacity");
        }
        
        if (splitPosition == cap) {
            return Buffers.EMPTY_BUFFER;
        }
        
        final int oldPosition = position();
        final int oldLimit = limit();

        Buffers.setPositionLimit(visible, 0, splitPosition);
        ByteBuffer slice1 = visible.slice();
        Buffers.setPositionLimit(visible, splitPosition, visible.capacity());
        ByteBuffer slice2 = visible.slice();

        if (oldPosition < splitPosition) {
            slice1.position(oldPosition);
        } else {
            slice1.position(slice1.capacity());
            slice2.position(oldPosition - splitPosition);
        }

        if (oldLimit < splitPosition) {
            slice1.limit(oldLimit);
            slice2.limit(0);
        } else {
            slice2.limit(oldLimit - splitPosition);
        }

        this.visible = slice1;

        return wrapByteBuffer(slice2);
//        return memoryManager.wrap(slice2);
    }

    @Override
    public ByteBufferWrapper slice() {
        return slice(position(), limit());
    }

    @Override
    public ByteBufferWrapper slice(int position, int limit) {
        checkDispose();
        final int oldPosition = position();
        final int oldLimit = limit();

        try {
            Buffers.setPositionLimit(visible, position, limit);

            final ByteBuffer slice = visible.slice();
            return wrapByteBuffer(slice);
        } finally {
            Buffers.setPositionLimit(visible, oldPosition, oldLimit);
        }
    }


    @Override
    public ByteBufferWrapper duplicate() {
        checkDispose();
        final ByteBuffer duplicate = visible.duplicate();
        return wrapByteBuffer(duplicate);
    }

    @Override
    public ByteBufferWrapper asReadOnlyBuffer() {
        checkDispose();
        return wrapByteBuffer(visible.asReadOnlyBuffer());
    }

    @Override
    public byte get() {
        checkDispose();
        return visible.get();
    }

    @Override
    public byte get(int index) {
        checkDispose();
        return visible.get(index);
    }

    @Override
    public ByteBufferWrapper put(byte b) {
        checkDispose();
        visible.put(b);
        return this;
    }

    @Override
    public ByteBufferWrapper put(int index, byte b) {
        checkDispose();
        visible.put(index, b);
        return this;
    }

    @Override
    public ByteBufferWrapper get(final byte[] dst) {
        return get(dst, 0, dst.length);
    }

    @Override
    public ByteBufferWrapper get(final byte[] dst, final int offset, final int length) {
        checkDispose();
        Buffers.get(visible, dst, offset, length);
        return this;
    }

    @Override
    public ByteBufferWrapper put(final Buffer src) {
        put(src, src.position(), src.remaining());
        src.position(src.limit());
        return this;
    }

    @Override
    public ByteBufferWrapper put(final Buffer src, final int position, final int length) {
        final int oldPos = src.position();
        final int oldLim = limit();
        
        src.position(position);
        limit(position() + length);
        
        try {
            src.get(visible);
        } finally {
            src.position(oldPos);
            limit(oldLim);
        }
        
        return this;
    }

    @Override
    public Buffer get(final ByteBuffer dst) {
        checkDispose();
        final int length = dst.remaining();
        
        if (visible.remaining() < length) {
            throw new BufferUnderflowException();
        }
        
        final int srcPos = visible.position();
        final int oldSrcLim = visible.limit();
        try {
            visible.limit(srcPos + length);
            dst.put(visible);
        } finally {
            visible.limit(oldSrcLim);
        }
        
        return this;
    }

    @Override
    public Buffer get(final ByteBuffer dst, final int position, final int length) {
        checkDispose();
        if (visible.remaining() < length) {
            throw new BufferUnderflowException();
        }
        
        final int srcPos = visible.position();
        final int oldSrcLim = visible.limit();
        final int oldDstPos = dst.position();
        final int oldDstLim = dst.limit();
        
        Buffers.setPositionLimit(dst, position, position + length);
        try {
            visible.limit(srcPos + length);
            dst.put(visible);
        } finally {
            visible.limit(oldSrcLim);
            Buffers.setPositionLimit(dst, oldDstPos, oldDstLim);
        }

        return this;
    }


    @Override
    public Buffer put(final ByteBuffer src) {
        checkDispose();
        visible.put(src);
        return this;
    }

    @Override
    public Buffer put(final ByteBuffer src, final int position, final int length) {
        checkDispose();
        final int oldPos = src.position();
        final int oldLim = src.limit();

        try {
            Buffers.setPositionLimit(src, position, position + length);
            visible.put(src);
        } finally {
            Buffers.setPositionLimit(src, oldPos, oldLim);
        }

        return this;
    }

    @Override
    public ByteBufferWrapper put(byte[] src) {
        return put(src, 0, src.length);
    }

    @Override
    public ByteBufferWrapper put(byte[] src, int offset, int length) {
        checkDispose();
        Buffers.put(src, offset, length, visible);
        return this;
    }

    @SuppressWarnings("deprecation")
    @Override
    public Buffer put8BitString(final String s) {
        checkDispose();
        final int len = s.length();
        if (remaining() < len) {
            throw new BufferOverflowException();
        }

        for (int i = 0; i < len; i++) {
            visible.put((byte) s.charAt(i));
        }
        
        return this;
    }

    @Override
    public ByteBufferWrapper compact() {
        checkDispose();
        visible.compact();
        return this;
    }

    @Override
    public ByteOrder order() {
        checkDispose();
        return visible.order();
    }

    @Override
    public ByteBufferWrapper order(ByteOrder bo) {
        checkDispose();
        visible.order(bo);
        return this;
    }

    @Override
    public char getChar() {
        checkDispose();
        return visible.getChar();
    }

    @Override
    public char getChar(int index) {
        checkDispose();
        return visible.getChar(index);
    }

    @Override
    public ByteBufferWrapper putChar(char value) {
        checkDispose();
        visible.putChar(value);
        return this;
    }

    @Override
    public ByteBufferWrapper putChar(int index, char value) {
        checkDispose();
        visible.putChar(index, value);
        return this;
    }

    @Override
    public short getShort() {
        checkDispose();
        return visible.getShort();
    }

    @Override
    public short getShort(int index) {
        checkDispose();
        return visible.getShort(index);
    }

    @Override
    public ByteBufferWrapper putShort(short value) {
        checkDispose();
        visible.putShort(value);
        return this;
    }

    @Override
    public ByteBufferWrapper putShort(int index, short value) {
        checkDispose();
        visible.putShort(index, value);
        return this;
    }

    @Override
    public int getInt() {
        checkDispose();
        return visible.getInt();
    }

    @Override
    public int getInt(int index) {
        checkDispose();
        return visible.getInt(index);
    }

    @Override
    public ByteBufferWrapper putInt(int value) {
        checkDispose();
        visible.putInt(value);
        return this;
    }

    @Override
    public ByteBufferWrapper putInt(int index, int value) {
        checkDispose();
        visible.putInt(index, value);
        return this;
    }

    @Override
    public long getLong() {
        checkDispose();
        return visible.getLong();
    }

    @Override
    public long getLong(int index) {
        checkDispose();
        return visible.getLong(index);
    }

    @Override
    public ByteBufferWrapper putLong(long value) {
        checkDispose();
        visible.putLong(value);
        return this;
    }

    @Override
    public ByteBufferWrapper putLong(int index, long value) {
        checkDispose();
        visible.putLong(index, value);
        return this;
    }

    @Override
    public float getFloat() {
        checkDispose();
        return visible.getFloat();
    }

    @Override
    public float getFloat(int index) {
        checkDispose();
        return visible.getFloat(index);
    }

    @Override
    public ByteBufferWrapper putFloat(float value) {
        checkDispose();
        visible.putFloat(value);
        return this;
    }

    @Override
    public ByteBufferWrapper putFloat(int index, float value) {
        checkDispose();
        visible.putFloat(index, value);
        return this;
    }

    @Override
    public double getDouble() {
        checkDispose();
        return visible.getDouble();
    }

    @Override
    public double getDouble(int index) {
        checkDispose();
        return visible.getDouble(index);
    }

    @Override
    public ByteBufferWrapper putDouble(double value) {
        checkDispose();
        visible.putDouble(value);
        return this;
    }

    @Override
    public ByteBufferWrapper putDouble(int index, double value) {
        checkDispose();
        visible.putDouble(index, value);
        return this;
    }
    
    @Override
    public int hashCode() {
        return visible.hashCode();
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
        if (visible == null) {
            throw new IllegalStateException(
                    "BufferWrapper has already been disposed",
                    disposeStackTrace) ;
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("ByteBufferWrapper (" +
                System.identityHashCode(this) + ") [");
        sb.append("visible=[").append(visible).append(']');
        sb.append(']');
        return sb.toString();
    }

    @Override
    public String toStringContent() {
        return toStringContent(Charset.defaultCharset(), position(), limit());
    }

    @Override
    public String toStringContent(Charset charset) {
        return toStringContent(charset, position(), limit());
    }

    @Override
    public String toStringContent(Charset charset, int position, int limit) {
        checkDispose();
        return Buffers.toStringContent(visible, charset, position, limit);
    }

    @Override
    public void dumpHex(java.lang.Appendable appendable) {
        Buffers.dumpBuffer(appendable, this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final ByteBuffer toByteBuffer() {
        checkDispose();
        return visible;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final ByteBuffer toByteBuffer(int position, int limit) {
        checkDispose();
        final int currentPosition = visible.position();
        final int currentLimit = visible.limit();

        if (position == currentPosition && limit == currentLimit) {
            return toByteBuffer();
        }

        Buffers.setPositionLimit(visible, position, limit);

        final ByteBuffer resultBuffer = visible.slice();

        Buffers.setPositionLimit(visible, currentPosition, currentLimit);

        return resultBuffer;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final ByteBufferArray toByteBufferArray() {
        checkDispose();
        final ByteBufferArray array = ByteBufferArray.create();
        array.add(visible);

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
        checkDispose();
        array.add(visible);
        return array;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final ByteBufferArray toByteBufferArray(final ByteBufferArray array,
            final int position, final int limit) {
        checkDispose();

        final int oldPos = visible.position();
        final int oldLim = visible.limit();

        Buffers.setPositionLimit(visible, position, limit);
        array.add(visible, oldPos, oldLim);

        return array;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final BufferArray toBufferArray() {
        checkDispose();
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
        checkDispose();
        array.add(this);
        return array;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final BufferArray toBufferArray(final BufferArray array,
            final int position, final int limit) {
        checkDispose();

        final int oldPos = visible.position();
        final int oldLim = visible.limit();

        Buffers.setPositionLimit(visible, position, limit);
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
        return visible.hasArray();
    }

    @Override
    public byte[] array() {
        return visible.array();
    }

    @Override
    public int arrayOffset() {
        return visible.arrayOffset();
    }

    protected ByteBufferWrapper wrapByteBuffer(final ByteBuffer byteBuffer) {
        return new ByteBufferWrapper(byteBuffer);
    }

    private static class DebugLogic {
        static void doDebug(ByteBufferWrapper wrapper) {
            wrapper.visible.clear();
            while(wrapper.visible.hasRemaining()) {
                wrapper.visible.put((byte) 0xFF);
            }
            wrapper.visible.flip();
            wrapper.disposeStackTrace = new Exception("ByteBufferWrapper was disposed from: ");
        }
    }
}
