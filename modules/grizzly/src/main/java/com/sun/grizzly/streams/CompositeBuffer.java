/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 1997-2007 Sun Microsystems, Inc. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License. You can obtain
 * a copy of the License at https://glassfish.dev.java.net/public/CDDL+GPL.html
 * or glassfish/bootstrap/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/bootstrap/legal/LICENSE.txt.
 * Sun designates this particular file as subject to the "Classpath" exception
 * as provided by Sun in the GPL Version 2 section of the License file that
 * accompanied this code.  If applicable, add the following below the License
 * Header, with the fields enclosed by brackets [] replaced by your own
 * identifying information: "Portions Copyrighted [year]
 * [name of copyright owner]"
 *
 * Contributor(s):
 *
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

package com.sun.grizzly.streams;

import com.sun.grizzly.Buffer;
import com.sun.grizzly.utils.LightArrayList;
import java.io.UnsupportedEncodingException;
import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.List;

/**
 *
 * @author Alexey Stashok
 */
public final class CompositeBuffer implements Buffer<List<Buffer>> {
    private boolean isDisposed;
    
    private final int initialOffset;
    
    private final List<Buffer> buffers;
    private int position;
    private int limit;
    private int capacity;

    private final boolean isReadOnly;

    private int lastBufferIndex = -1;
    private int lastBufferPosition = -1;
    private Buffer lastBuffer;

    private ByteOrder byteOrder = ByteOrder.nativeOrder();

    public CompositeBuffer() {
        this(new LightArrayList<Buffer>());
    }

    public CompositeBuffer(List<Buffer> buffers) {
        this(buffers, false);
    }

    public CompositeBuffer(List<Buffer> buffers, boolean isReadOnly) {
        this(buffers, 0, -1, isReadOnly);
    }

    protected CompositeBuffer(CompositeBuffer that) {
        this(that, that.isReadOnly);
    }

    protected CompositeBuffer(CompositeBuffer that, boolean isReadOnly) {
        this.buffers = that.buffers;
        this.position = that.position;
        this.initialOffset = that.initialOffset;
        this.limit = that.limit;
        this.isReadOnly = isReadOnly;
    }

    protected CompositeBuffer(List<Buffer> buffers,
            int initialOffset, int capacity, boolean isReadOnly) {
        this.buffers = buffers;
        this.initialOffset = initialOffset;
        this.capacity = capacity;
        this.isReadOnly = isReadOnly;
    }

    @Override
    public List<Buffer> prepend(List<Buffer> header) {
        checkDispose();
        buffers.addAll(0, header);
        return buffers;
    }

    @Override
    public void dispose() {
        checkDispose();
        isDisposed = true;
    }

    @Override
    public List<Buffer> underlying() {
        checkDispose();
        return buffers;
    }

    @Override
    public int position() {
        checkDispose();
        return position;
    }

    @Override
    public Buffer<List<Buffer>> position(int newPosition) {
        checkDispose();
        position = newPosition;
        return this;
    }

    @Override
    public int limit() {
        checkDispose();
        return limit;
    }

    @Override
    public Buffer<List<Buffer>> limit(int newLimit) {
        checkDispose();
        limit = newLimit;
        return this;
    }

    @Override
    public int capacity() {
        return capacity;
    }

    @Override
    public Buffer<List<Buffer>> mark() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Buffer<List<Buffer>> reset() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Buffer<List<Buffer>> clear() {
        checkDispose();
        position = 0;
        limit = capacity();
        return this;
    }

    @Override
    public Buffer<List<Buffer>> flip() {
        checkDispose();
        limit = position;
        position = 0;
        resetLastBuffer();
        return this;
    }

    @Override
    public Buffer<List<Buffer>> rewind() {
        checkDispose();
	position = 0;
	return this;
    }

    @Override
    public int remaining() {
        return limit - position;
    }

    @Override
    public boolean hasRemaining() {
        return limit > position;
    }

    @Override
    public boolean isReadOnly() {
        return isReadOnly;
    }

    @Override
    public Buffer<List<Buffer>> asReadOnlyBuffer() {
        return new CompositeBuffer(this, true);
    }

    @Override
    public void trim() {
        flip();

        final long bufferLocation = locateBuffer(limit);

        if (bufferLocation == -1) throw new IllegalStateException("Bad state");

        int bufferIndex = getBufferIndex(bufferLocation);

        int size = buffers.size();
        int buffersToDispose = size - bufferIndex - 1;

        for(int i=0; i<buffersToDispose; i++) {
            Buffer buffer = buffers.remove(size - 1);
            size--;
            buffer.dispose();
        }

        capacity = limit;
    }

    @Override
    public Buffer<List<Buffer>> slice() {
        long posLocation = locateBuffer(position);
        long limitLocation = locateBuffer(limit);


        LightArrayList<Buffer> newList = new LightArrayList<Buffer>();

        int posBufferIndex = getBufferIndex(posLocation);
        int limitBufferIndex = getBufferIndex(limitLocation);

        for(int i=posBufferIndex; i<=limitBufferIndex; i++) {
            newList.add(buffers.get(i));
        }

        int newInitialOffset = getBufferPosition(posLocation);
        int newCapacity = limit - position;

        return new CompositeBuffer(newList, newInitialOffset, newCapacity,
                isReadOnly);
    }

    @Override
    public Buffer<List<Buffer>> duplicate() {
        return new CompositeBuffer(this);
    }

    @Override
    public byte get() {
        incLocation();
        position++;
        return lastBuffer.get(lastBufferPosition);
    }

    @Override
    public Buffer<List<Buffer>> put(byte b) {
        incLocation();
        lastBuffer.put(lastBufferPosition, b);
        position++;
        return this;
    }

    @Override
    public byte get(int index) {
        long location = locateBuffer(index);
        int bufferIndex = getBufferIndex(location);
        int bufferPos = getBufferPosition(location);

        return buffers.get(bufferIndex).get(bufferPos);
    }

    @Override
    public Buffer<List<Buffer>> put(int index, byte b) {
        long location = locateBuffer(index);
        int bufferIndex = getBufferIndex(location);
        int bufferPos = getBufferPosition(location);

        buffers.get(bufferIndex).put(bufferPos, b);
        return this;
    }

    @Override
    public Buffer<List<Buffer>> get(byte[] dst) {
        return get(dst, 0, dst.length);
    }

    @Override
    public Buffer<List<Buffer>> get(byte[] dst, int offset, int length) {
        if (remaining() < length) throw new BufferUnderflowException();

        incLocation();
        while(length > 0) {
            int oldPos = lastBuffer.position();
            int bytesToCopy = Math.min(lastBuffer.remaining(), length);
            lastBuffer.get(dst, offset, bytesToCopy);
            lastBuffer.position(oldPos);

            length -= bytesToCopy;
            offset += bytesToCopy;

            lastBufferIndex++;
            lastBuffer = buffers.get(lastBufferIndex);
            lastBufferPosition = lastBuffer.position();
        }

        position += length;
        
        return this;
    }

    @Override
    public Buffer<List<Buffer>> put(byte[] src) {
        return put(src, 0, src.length);
    }

    @Override
    public Buffer<List<Buffer>> put(byte[] src, int offset, int length) {
        if (remaining() < length) throw new BufferOverflowException();

        incLocation();
        while(length > 0) {
            int oldPos = lastBuffer.position();
            int bytesToCopy = Math.min(lastBuffer.remaining(), length);
            lastBuffer.put(src, offset, bytesToCopy);
            lastBuffer.position(oldPos);

            length -= bytesToCopy;
            offset += bytesToCopy;

            lastBufferIndex++;
            lastBuffer = buffers.get(lastBufferIndex);
            lastBufferPosition = lastBuffer.position();
        }

        position += length;

        return this;
    }

    @Override
    public Buffer<List<Buffer>> put(Buffer src) {
        if (remaining() < src.remaining()) throw new BufferOverflowException();

        Object underlying = src.underlying();
        ByteBuffer bb;
        if (underlying instanceof ByteBuffer && (bb = (ByteBuffer) underlying).hasArray()) {
            return put(bb.array(), bb.arrayOffset() + bb.position(), bb.remaining());
        }

        while(src.hasRemaining()) {
            put(src.get());
        }

        return this;
    }

    @Override
    public Buffer<List<Buffer>> compact() {
        for(int i = position; i < limit; i++) {
            put(i - position, get(i));
        }
        position = 0;
        limit = position;

        return this;
    }

    @Override
    public ByteOrder order() {
        return byteOrder;
    }

    @Override
    public Buffer<List<Buffer>> order(ByteOrder bo) {
        this.byteOrder = bo;
        return this;
    }

    @Override
    public char getChar() {
        incLocation();
        if (lastBuffer.limit() - lastBufferPosition >= 2) {
            position += 2;
            lastBufferPosition++;
            return lastBuffer.getChar(lastBufferIndex);
        } else {
            int ch1 = get() & 0xFF;
            int ch2 = get() & 0xFF;
            return (char)((ch1 << 8) + (ch2 << 0));
        }
    }

    @Override
    public Buffer<List<Buffer>> putChar(char value) {
        incLocation();
        if (lastBuffer.limit() - lastBufferPosition >= 2) {
            position += 2;
            lastBufferPosition++;
            lastBuffer.putChar(lastBufferIndex, value);
        } else {
            put((byte) (value >>> 8));
            put((byte) (value & 0xFF));
        }
        
        return this;
    }

    @Override
    public char getChar(int index) {
        long location = locateBuffer(index);
        int bufferIndex = getBufferIndex(location);
        int bufferPosition = getBufferPosition(location);
        Buffer buffer = buffers.get(bufferIndex);

        if (buffer.limit() - bufferPosition >= 2) {
            return buffer.getChar(bufferPosition);
        } else {
            int ch1 = get(index) & 0xFF;
            int ch2 = get(index + 1) & 0xFF;
            return (char)((ch1 << 8) + (ch2 << 0));
        }
    }

    @Override
    public Buffer<List<Buffer>> putChar(int index, char value) {
        long location = locateBuffer(index);
        int bufferIndex = getBufferIndex(location);
        int bufferPosition = getBufferPosition(location);
        Buffer buffer = buffers.get(bufferIndex);

        if (buffer.limit() - bufferPosition >= 2) {
            buffer.putChar(bufferPosition, value);
        } else {
            put(index, (byte) (value >>> 8));
            put(index + 1, (byte) (value & 0xFF));
        }

        return this;
    }

    @Override
    public short getShort() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Buffer<List<Buffer>> putShort(short value) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public short getShort(int index) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Buffer<List<Buffer>> putShort(int index, short value) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public int getInt() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Buffer<List<Buffer>> putInt(int value) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public int getInt(int index) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Buffer<List<Buffer>> putInt(int index, int value) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public long getLong() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Buffer<List<Buffer>> putLong(long value) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public long getLong(int index) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Buffer<List<Buffer>> putLong(int index, long value) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public float getFloat() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Buffer<List<Buffer>> putFloat(float value) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public float getFloat(int index) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Buffer<List<Buffer>> putFloat(int index, float value) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public double getDouble() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Buffer<List<Buffer>> putDouble(double value) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public double getDouble(int index) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Buffer<List<Buffer>> putDouble(int index, double value) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public int compareTo(Buffer that) {
	int n = this.position() + Math.min(this.remaining(), that.remaining());
	for (int i = this.position(), j = that.position(); i < n; i++, j++) {
	    byte v1 = this.get(i);
	    byte v2 = that.get(j);
	    if (v1 == v2)
		continue;
	    if ((v1 != v1) && (v2 != v2)) 	// For float and double
		continue;
	    if (v1 < v2)
		return -1;
	    return +1;
	}
	return this.remaining() - that.remaining();
    }

    @Override
    public String contentAsString(Charset charset) {
        checkDispose();

        // Working with charset name to support JDK1.5
        String charsetName = charset.name();
        try {
            int oldPosition = position;
            byte[] tmpBuffer = new byte[remaining()];
            get(tmpBuffer);
            position = oldPosition;
            return new String(tmpBuffer, charsetName);
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("Unexpected exception", e);
        }
    }

    private void checkDispose() {
        if (isDisposed) {
            throw new IllegalStateException(
                    "CompositeBuffer has already been disposed");
        }
    }

    private long locateBuffer(int offset) {
        if (buffers.isEmpty()) return -1;

        Buffer buffer = buffers.get(0);
        int currentOffset = buffer.remaining() - initialOffset;
        if (offset < currentOffset) {
            return offset + initialOffset + buffer.position();
        }

        int index = 1;
        Iterator<Buffer> it = buffers.iterator();
        it.next();

        for(; it.hasNext(); ) {
            buffer = it.next();
            final int newOffset = currentOffset + buffer.remaining();
            if (offset < newOffset) {
                return (long) (((long) index) << 32) |
                        (long) (offset - currentOffset);
            }

            currentOffset = newOffset;
            index++;
        }

        return -1;
    }

    private void resetLastBuffer() {
        lastBuffer = null;
    }
    
    private static int getBufferIndex(long bufferLocation) {
        return (int) (bufferLocation >>> 32);
    }

    private static int getBufferPosition(long bufferLocation) {
        return (int) (bufferLocation & 0xFFFFFFFF);
    }

    private void incLocation() {
        if (lastBuffer != null) {
            if (lastBuffer.limit() < lastBufferPosition) {
                lastBufferPosition++;
            } else {
                lastBufferIndex++;
                lastBuffer = buffers.get(lastBufferIndex);
                lastBufferPosition = lastBuffer.position();
            }
        } else {
            long location = locateBuffer(position);
            lastBufferIndex = getBufferIndex(location);
            lastBufferPosition = getBufferPosition(location);
            lastBuffer = buffers.get(lastBufferIndex);
        }
    }

}
