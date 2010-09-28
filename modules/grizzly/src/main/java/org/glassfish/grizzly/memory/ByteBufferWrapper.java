/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2010 Oracle and/or its affiliates. All rights reserved.
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

    protected final ByteBufferManager memoryManager;
    
    protected ByteBuffer visible;

    // Dispose underlying Buffer flag
    protected boolean allowBufferDispose = false;
    
    protected Exception disposeStackTrace;

    protected ByteBufferWrapper() {
        this(null, null);
    }

    public ByteBufferWrapper(ByteBufferManager memoryManager,
            ByteBuffer underlyingByteBuffer) {
        this.memoryManager = memoryManager;
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
        checkDispose();
        if (DEBUG_MODE) { // if debug is on - clear the buffer content
            visible.clear();
            while(visible.hasRemaining()) {
                visible.put((byte) 0xFF);
            }

            disposeStackTrace = new Exception("ByteBufferWrapper was disposed from: ");
        }

        memoryManager.release(this);
        visible = null;
    }

    @Override
    public ByteBuffer underlying() {
        checkDispose();
        return visible;
    }

    @Override
    public int capacity() {
        return visible.capacity();
    }

    @Override
    public int position() {
        checkDispose();
        return visible.position();
    }

    @Override
    public ByteBufferWrapper position(int newPosition) {
        checkDispose();
        visible.position(newPosition);
        return this;
    }

    @Override
    public int limit() {
        checkDispose();
        return visible.limit();
    }

    @Override
    public ByteBufferWrapper limit(int newLimit) {
        checkDispose();
        visible.limit(newLimit);
        return this;
    }

    @Override
    public ByteBufferWrapper mark() {
        visible.mark();
        return this;
    }

    @Override
    public ByteBufferWrapper reset() {
        visible.reset();
        return this;
    }

    @Override
    public ByteBufferWrapper clear() {
        visible.clear();
        return this;
    }

    @Override
    public ByteBufferWrapper flip() {
        visible.flip();
        return this;
    }

    @Override
    public ByteBufferWrapper rewind() {
        visible.rewind();
        return this;
    }

    @Override
    public int remaining() {
        return visible.remaining();
    }

    @Override
    public boolean hasRemaining() {
        return visible.hasRemaining();
    }

    @Override
    public boolean isReadOnly() {
        return visible.isReadOnly();
    }

    @Override
    public Buffer split(int splitPosition) {
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

        return memoryManager.wrap(slice2);
    }

    @Override
    public ByteBufferWrapper slice() {
        return slice(position(), limit());
    }

    @Override
    public ByteBufferWrapper slice(int position, int limit) {
        final int oldPosition = position();
        final int oldLimit = limit();

        try {
            Buffers.setPositionLimit(visible, position, limit);

            final ByteBuffer slice = visible.slice();
            return memoryManager.wrap(slice);
        } finally {
            Buffers.setPositionLimit(visible, oldPosition, oldLimit);
        }
    }


    @Override
    public ByteBufferWrapper duplicate() {
        final ByteBuffer duplicate = visible.duplicate();
        return memoryManager.wrap(duplicate);
    }

    @Override
    public ByteBufferWrapper asReadOnlyBuffer() {
        visible.asReadOnlyBuffer();
        return this;
    }

    @Override
    public byte get() {
        return visible.get();
    }

    @Override
    public byte get(int index) {
        return visible.get(index);
    }

    @Override
    public ByteBufferWrapper put(byte b) {
        visible.put(b);
        return this;
    }

    @Override
    public ByteBufferWrapper put(int index, byte b) {
        visible.put(index, b);
        return this;
    }

    @Override
    public ByteBufferWrapper get(byte[] dst) {
        return get(dst, 0, dst.length);
    }

    @Override
    public ByteBufferWrapper get(byte[] dst, int offset, int length) {
        Buffers.get(visible, dst, offset, length);
        return this;
    }

    @Override
    public ByteBufferWrapper put(Buffer src) {
        put(src, src.position(), src.remaining());
        src.position(src.limit());
        return this;
    }

    @Override
    public ByteBufferWrapper put(Buffer src, int position, int length) {
        if (!src.isComposite()) {
            Buffers.put(src.toByteBuffer(), position, length, visible);
        } else {
            final ByteBuffer[] bbs =
                    src.toByteBufferArray(position, position + length);
            
            for(ByteBuffer bb : bbs) {
                final int pos = bb.position();
                Buffers.put(bb, pos, bb.remaining(), visible);
                bb.position(pos);
            }
        }
        
        return this;
    }


    @Override
    public ByteBufferWrapper put(byte[] src) {
        return put(src, 0, src.length);
    }

    @Override
    public ByteBufferWrapper put(byte[] src, int offset, int length) {
        Buffers.put(src, offset, length, visible);
        return this;
    }

    @Override
    public ByteBufferWrapper compact() {
        visible.compact();
        return this;
    }

    @Override
    public ByteOrder order() {
        return visible.order();
    }

    @Override
    public ByteBufferWrapper order(ByteOrder bo) {
        visible.order(bo);
        return this;
    }

    @Override
    public char getChar() {
        return visible.getChar();
    }

    @Override
    public char getChar(int index) {
        return visible.getChar(index);
    }

    @Override
    public ByteBufferWrapper putChar(char value) {
        visible.putChar(value);
        return this;
    }

    @Override
    public ByteBufferWrapper putChar(int index, char value) {
        visible.putChar(index, value);
        return this;
    }

    @Override
    public short getShort() {
        return visible.getShort();
    }

    @Override
    public short getShort(int index) {
        return visible.getShort(index);
    }

    @Override
    public ByteBufferWrapper putShort(short value) {
        visible.putShort(value);
        return this;
    }

    @Override
    public ByteBufferWrapper putShort(int index, short value) {
        visible.putShort(index, value);
        return this;
    }

    @Override
    public int getInt() {
        return visible.getInt();
    }

    @Override
    public int getInt(int index) {
        return visible.getInt(index);
    }

    @Override
    public ByteBufferWrapper putInt(int value) {
        visible.putInt(value);
        return this;
    }

    @Override
    public ByteBufferWrapper putInt(int index, int value) {
        visible.putInt(index, value);
        return this;
    }

    @Override
    public long getLong() {
        return visible.getLong();
    }

    @Override
    public long getLong(int index) {
        return visible.getLong(index);
    }

    @Override
    public ByteBufferWrapper putLong(long value) {
        visible.putLong(value);
        return this;
    }

    @Override
    public ByteBufferWrapper putLong(int index, long value) {
        visible.putLong(index, value);
        return this;
    }

    @Override
    public float getFloat() {
        return visible.getFloat();
    }

    @Override
    public float getFloat(int index) {
        return visible.getFloat(index);
    }

    @Override
    public ByteBufferWrapper putFloat(float value) {
        visible.putFloat(value);
        return this;
    }

    @Override
    public ByteBufferWrapper putFloat(int index, float value) {
        visible.putFloat(index, value);
        return this;
    }

    @Override
    public double getDouble() {
        return visible.getDouble();
    }

    @Override
    public double getDouble(int index) {
        return visible.getDouble(index);
    }

    @Override
    public ByteBufferWrapper putDouble(double value) {
        visible.putDouble(value);
        return this;
    }

    @Override
    public ByteBufferWrapper putDouble(int index, double value) {
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

    private void checkDispose() {
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
    public ByteBuffer toByteBuffer() {
        return visible;
    }

    @Override
    public ByteBuffer toByteBuffer(int position, int limit) {
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

    @Override
    public ByteBuffer[] toByteBufferArray() {
        return new ByteBuffer[] {toByteBuffer()};
    }

    @Override
    public ByteBuffer[] toByteBufferArray(int position, int limit) {
        return new ByteBuffer[] {toByteBuffer(position, limit)};
    }
}
