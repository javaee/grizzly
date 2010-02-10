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

package com.sun.grizzly.memory;

import com.sun.grizzly.Buffer;
import com.sun.grizzly.TransportFactory;
import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.util.Arrays;

/**
 *
 * @author Alexey Stashok
 */
public final class BuffersBuffer implements CompositeBuffer {
    private static final Buffer[] EMPTY_BUFFER_ARRAY = new Buffer[0];

    private boolean isDisposed;

    private final boolean isReadOnly;

    // absolute position
    private int position;

    // absolute limit
    private int limit;

    // absolute capacity
    private int capacity;

    // Index of buffer in the buffer list, which was used with a last get/set
    private int lastBufferIndex = -1;
    // Last buffer position, which was used with a last get/set
    private int lastBufferPosition = -1;
    // Last buffer, which was used with a last get/set
    private Buffer lastBuffer;

    // Allow to dispose this BuffersBuffer
    private boolean allowBufferDispose = false;

    // List of wrapped buffers
    private Buffer[] buffers;
    private int buffersSize;

    private final MemoryManager memoryManager;

    private ByteOrder byteOrder = ByteOrder.nativeOrder();

    public BuffersBuffer() {
        this(TransportFactory.getInstance().getDefaultMemoryManager(), null, false);
    }

    public BuffersBuffer(MemoryManager memoryManager) {
        this(memoryManager, null, false);
    }

    public BuffersBuffer(MemoryManager memoryManager, Buffer... buffers) {
        this(memoryManager, buffers, false);
    }

    protected BuffersBuffer(MemoryManager memoryManager,
            Buffer[] buffers, boolean isReadOnly) {

        if (memoryManager != null) {
            this.memoryManager = memoryManager;
        } else {
            this.memoryManager = TransportFactory.getInstance().getDefaultMemoryManager();
        }

        if (buffers == null) {
            this.buffers = new Buffer[4];
        } else {
            this.buffers = buffers;
            buffersSize = buffers.length;
        }

        capacity = calcCapacity();

        this.limit = capacity;
        this.isReadOnly = isReadOnly;
    }

    protected BuffersBuffer(BuffersBuffer that) {
        this(that, that.isReadOnly);
    }

    protected BuffersBuffer(BuffersBuffer that, boolean isReadOnly) {
        this.memoryManager = that.memoryManager;
        this.buffers = that.buffers;
        this.buffersSize = that.buffersSize;
        this.position = that.position;
        this.limit = that.limit;
        this.capacity = that.capacity;
        this.isReadOnly = isReadOnly;
    }

    @Override
    public final void tryDispose() {
        if (allowBufferDispose) {
            dispose();
        }
    }


    @Override
    public void dispose() {
        checkDispose();
        isDisposed = true;
        removeBuffers(true);
    }

    @Override
    public final boolean isComposite() {
        return true;
    }

    private void ensureBuffersCapacity(int newElementsNum) {
        final int newSize = buffersSize + newElementsNum;

        if (newSize > buffers.length) {
            buffers = Arrays.copyOf(buffers,
                    Math.max(newSize, (buffers.length * 3) / 2 + 1));
        }
    }

    @Override
    public BuffersBuffer append(Buffer buffer) {
        checkDispose();
        checkReadOnly();

        ensureBuffersCapacity(1);

        buffers[buffersSize++] = buffer;

        capacity += buffer.remaining();
        limit = capacity;
        return this;
    }

    @Override
    public BuffersBuffer prepend(Buffer buffer) {
        checkDispose();
        checkReadOnly();

        ensureBuffersCapacity(1);

        System.arraycopy(buffers, 0, buffers, 1, buffersSize);
        buffers[0] = buffer;

        buffersSize++;
        capacity += buffer.remaining();
        limit = capacity;
        return this;
    }

    @Override
    public Buffer[] underlying() {
        checkDispose();
        return buffers;
    }

    @Override
    public int position() {
        checkDispose();
        return position;
    }

    @Override
    public BuffersBuffer position(int newPosition) {
        checkDispose();
        setPosLim(newPosition, limit);
        return this;
    }

    @Override
    public int limit() {
        checkDispose();
        return limit;
    }

    @Override
    public BuffersBuffer limit(int newLimit) {
        checkDispose();
        setPosLim(position, newLimit);
        return this;
    }

    @Override
    public int capacity() {
        checkDispose();
        return capacity;
    }

    @Override
    public BuffersBuffer mark() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public BuffersBuffer reset() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public BuffersBuffer clear() {
        checkDispose();
        capacity = calcCapacity();
        setPosLim(0, capacity);
        return this;
    }

    @Override
    public BuffersBuffer flip() {
        checkDispose();
        setPosLim(0, position);
        return this;
    }

    @Override
    public BuffersBuffer rewind() {
        checkDispose();
        setPosLim(0, limit);
	return this;
    }

    @Override
    public int remaining() {
        checkDispose();
        return limit - position;
    }

    @Override
    public boolean hasRemaining() {
        checkDispose();
        return limit > position;
    }

    @Override
    public boolean isReadOnly() {
        checkDispose();
        return isReadOnly;
    }

    @Override
    public BuffersBuffer asReadOnlyBuffer() {
        checkDispose();
        return new BuffersBuffer(this, true);
    }

    public void shrink() {
        checkDispose();
        flip();

        if (limit < capacity) {
            final long bufferLocation = locateBufferLimit(limit);

            if (bufferLocation == -1) {
                throw new IllegalStateException("Bad state");
            }

            final int bufferIndex = getBufferIndex(bufferLocation);

            final int bytesRemoved = removeRightBuffers(bufferIndex + 1);

            final Buffer currentBuffer = buffers[bufferIndex];
            final int bufferPosition = getBufferPosition(bufferLocation);

            final int lastBufferRemoved = currentBuffer.limit() - bufferPosition;
            currentBuffer.limit(bufferPosition);

            capacity = capacity - bytesRemoved - lastBufferRemoved;
        }
    }

    @Override
    public void trim() {
        checkDispose();
        flip();

        final long bufferLocation = locateBufferLimit(limit);

        if (bufferLocation == -1) throw new IllegalStateException("Bad state");

        final int bufferIndex = getBufferIndex(bufferLocation);

        final int bytesRemoved = removeRightBuffers(bufferIndex + 1);
        capacity -= bytesRemoved;
    }

    protected int removeRightBuffers(int startIndex) {
        int removedBytes = 0;

        for(int i=startIndex; i<buffersSize; i++) {
            final Buffer buffer = buffers[i];
            removedBytes += buffer.remaining();

            if (allowBufferDispose) {
                buffer.dispose();
            }
        }

        buffersSize = startIndex;

        return removedBytes;
    }

    @Override
    public void trimRegion() {
        checkDispose();

        if (position == limit) {
            removeBuffers(false);
            
            clear();
            return;
        }

        final long posLocation = locateBufferPosition(position);
        final long limitLocation = locateBufferLimit(limit);

        final int posBufferIndex = getBufferIndex(posLocation);
        final int limitBufferIndex = getBufferIndex(limitLocation);

        final int leftTrim = posBufferIndex;
        final int rightTrim = buffersSize - limitBufferIndex - 1;

        if (leftTrim == 0 && rightTrim == 0) {
            return;
        }

        for(int i=0; i<leftTrim; i++) {
            final Buffer buffer = buffers[i];
            final int bufferSize = buffer.remaining();
            setPosLim(position - bufferSize, limit - bufferSize);
            capacity -= bufferSize;

            if (allowBufferDispose) {
                buffer.dispose();
            }
        }

        for(int i=0; i<rightTrim; i++) {
            final Buffer buffer = buffers[buffersSize - i - 1];
            final int bufferSize = buffer.remaining();
            capacity -= bufferSize;

            if (allowBufferDispose) {
                buffer.dispose();
            }
        }

        buffersSize -= (leftTrim + rightTrim);

        System.arraycopy(buffers, leftTrim, buffers, 0, buffersSize);
    }

    @Override
    public Buffer slice() {
        return slice(position, limit);
    }

    @Override
    public Buffer slice(int position, int limit) {
        checkDispose();

        if (buffersSize == 0) {
            return BufferUtils.EMPTY_BUFFER;
        } else if (buffersSize == 1) {
            return buffers[0].slice(position, limit);
        }

        final long posLocation = locateBufferPosition(position);
        final long limitLocation = locateBufferLimit(limit);

        final int posBufferIndex = getBufferIndex(posLocation);
        final int posBufferPosition = getBufferPosition(posLocation);

        final int limitBufferIndex = getBufferIndex(limitLocation);
        final int limitBufferPosition = getBufferPosition(limitLocation);

        if (posBufferIndex == limitBufferIndex) {
            return buffers[posBufferIndex].slice(
                    posBufferPosition, limitBufferPosition);
        } else {
            final Buffer[] newList = new Buffer[limitBufferIndex - posBufferIndex + 1];

            final Buffer posBuffer = buffers[posBufferIndex];
            newList[0] = posBuffer.slice(posBufferPosition, posBuffer.limit());

            int index = 1;
            for (int i = posBufferIndex + 1; i < limitBufferIndex; i++) {
                newList[index++] = buffers[i].slice();
            }

            final Buffer limitBuffer = buffers[limitBufferIndex];
            newList[index] = limitBuffer.slice(limitBuffer.position(),
                    limitBufferPosition);

            return new BuffersBuffer(memoryManager, newList, isReadOnly);
        }
    }

    @Override
    public BuffersBuffer duplicate() {
        checkDispose();
        return new BuffersBuffer(this);
    }

    @Override
    public BuffersBuffer compact() {
        checkDispose();

        if (buffersSize == 1) {
            final Buffer buffer = buffers[0];
            BufferUtils.setPositionLimit(buffer, buffer.position() + position,
                    buffer.position() + limit);
            buffer.compact();
        } else {
            final long posBufferLocation = locateBufferPosition(position);
            final long limitBufferLocation = locateBufferLimit(limit);

            final int posBufferIndex = getBufferIndex(posBufferLocation);
            final int limitBufferIndex = getBufferIndex(limitBufferLocation);

            final Buffer posBuffer = buffers[posBufferIndex];
            final Buffer limitBuffer = buffers[limitBufferIndex];

            final int posBufferPosition = getBufferPosition(posBufferLocation);
            posBuffer.position(posBufferPosition);

            final int limitBufferPosition = getBufferPosition(limitBufferLocation);
            limitBuffer.limit(limitBufferPosition);

            for(int i = posBufferIndex; i <= limitBufferIndex; i++) {
                final Buffer b1 = buffers[i - posBufferIndex];
                buffers[i - posBufferIndex] = buffers[i];
                buffers[i] = b1;
            }

            calcCapacity();
        }
        setPosLim(0, position);
        return this;
    }

    @Override
    public ByteOrder order() {
        checkDispose();
        return byteOrder;
    }

    @Override
    public BuffersBuffer order(ByteOrder bo) {
        checkDispose();
        this.byteOrder = bo;
        return this;
    }

    @Override
    public boolean allowBufferDispose() {
        return allowBufferDispose;
    }

    @Override
    public void allowBufferDispose(boolean allow) {
        this.allowBufferDispose = allow;
    }

    @Override
    public byte get() {
        checkDispose();
        prepareLastBuffer();
        position++;
        return lastBuffer.get(lastBufferPosition);
    }

    @Override
    public BuffersBuffer put(byte b) {
        checkDispose();
        checkReadOnly();

        prepareLastBuffer();
        lastBuffer.put(lastBufferPosition, b);
        position++;
        return this;
    }

    @Override
    public byte get(int index) {
        checkDispose();

        long location = locateBufferPosition(index);
        return bufferGet(location);
    }

    @Override
    public BuffersBuffer put(int index, byte b) {
        checkDispose();
        checkReadOnly();

        long location = locateBufferPosition(index);
        return bufferPut(location, b);
    }

    @Override
    public BuffersBuffer get(byte[] dst) {
        checkDispose();

        return get(dst, 0, dst.length);
    }

    @Override
    public BuffersBuffer get(byte[] dst, int offset, int length) {
        checkDispose();
        if (remaining() < length) throw new BufferUnderflowException();

        prepareLastBuffer();
        while(true) {
            int oldPos = lastBuffer.position();
            lastBuffer.position(lastBufferPosition);
            int bytesToCopy = Math.min(lastBuffer.remaining(), length);
            lastBuffer.get(dst, offset, bytesToCopy);
            lastBuffer.position(oldPos);
            lastBufferPosition += (bytesToCopy - 1);

            length -= bytesToCopy;
            offset += bytesToCopy;
            position += bytesToCopy;

            if (length == 0) break;

            lastBufferIndex++;
            lastBuffer = buffers[lastBufferIndex];
            lastBufferPosition = lastBuffer.position();
        }

        return this;
    }

    @Override
    public BuffersBuffer put(byte[] src) {
        return put(src, 0, src.length);
    }

    @Override
    public BuffersBuffer put(byte[] src, int offset, int length) {
        checkDispose();
        checkReadOnly();

        if (remaining() < length) throw new BufferOverflowException();

        prepareLastBuffer();
        while(true) {
            int oldPos = lastBuffer.position();
            lastBuffer.position(lastBufferPosition);
            int bytesToCopy = Math.min(lastBuffer.remaining(), length);
            lastBuffer.put(src, offset, bytesToCopy);
            lastBuffer.position(oldPos);
            lastBufferPosition += (bytesToCopy - 1);

            length -= bytesToCopy;
            offset += bytesToCopy;
            position += bytesToCopy;

            if (length == 0) break;

            lastBufferIndex++;
            lastBuffer = buffers[lastBufferIndex];
            lastBufferPosition = lastBuffer.position();
        }

        return this;
    }

    @Override
    public BuffersBuffer put(Buffer src) {
        checkDispose();
        checkReadOnly();

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
    public char getChar() {
        int ch1 = get() & 0xFF;
        int ch2 = get() & 0xFF;

        return (char) ((ch1 << 8) + (ch2 << 0));
    }

    @Override
    public BuffersBuffer putChar(char value) {
        put((byte) (value >>> 8));
        put((byte) (value & 0xFF));

        return this;
    }

    @Override
    public char getChar(int index) {
        checkDispose();

        long location = locateBufferPosition(index);
        final int bufferIndex = getBufferIndex(location);
        final int bufferPosition = getBufferPosition(location);
        final Buffer buffer = buffers[bufferIndex];

        if (buffer.limit() - bufferPosition >= 2) {
            return buffer.getChar(bufferPosition);
        } else {
            int ch1 = buffer.get(bufferPosition) & 0xFF;

            location = incLocation(location);
            int ch2 = bufferGet(location) & 0xFF;

            return (char) ((ch1 << 8) + (ch2 << 0));
        }
    }

    @Override
    public BuffersBuffer putChar(int index, char value) {
        checkDispose();
        checkReadOnly();

        long location = locateBufferPosition(index);
        final int bufferIndex = getBufferIndex(location);
        final int bufferPosition = getBufferPosition(location);
        final Buffer buffer = buffers[bufferIndex];

        if (buffer.limit() - bufferPosition >= 2) {
            buffer.putChar(bufferPosition, value);
        } else {
            buffer.put(bufferPosition, (byte) (value >>> 8));

            location = incLocation(location);
            bufferPut(location, (byte) (value & 0xFF));
        }

        return this;
    }

    @Override
    public short getShort() {
        final byte v1 = get();
        final byte v2 = get();

        final short shortValue = (short) (((v1 & 0xFF) << 8) | (v2 & 0xFF));
        return shortValue;
    }

    @Override
    public BuffersBuffer putShort(short value) {
        put((byte) (value >>> 8));
        put((byte) (value & 0xFF));

        return this;
    }

    @Override
    public short getShort(int index) {
        checkDispose();

        long location = locateBufferPosition(index);
        final int bufferIndex = getBufferIndex(location);
        final int bufferPosition = getBufferPosition(location);
        final Buffer buffer = buffers[bufferIndex];

        if (buffer.limit() - bufferPosition >= 2) {
            return buffer.getShort(bufferPosition);
        } else {
            int ch1 = buffer.get(bufferPosition) & 0xFF;

            location = incLocation(location);
            int ch2 = bufferGet(location) & 0xFF;

            return (short) ((ch1 << 8) + (ch2 << 0));
        }
    }

    @Override
    public BuffersBuffer putShort(int index, short value) {
        checkDispose();
        checkReadOnly();

        long location = locateBufferPosition(index);
        final int bufferIndex = getBufferIndex(location);
        final int bufferPosition = getBufferPosition(location);
        final Buffer buffer = buffers[bufferIndex];

        if (buffer.limit() - bufferPosition >= 2) {
            buffer.putShort(bufferPosition, value);
        } else {
            buffer.put(bufferPosition, (byte) (value >>> 8));

            location = incLocation(location);
            bufferPut(location, (byte) (value & 0xFF));
        }

        return this;
    }

    @Override
    public int getInt() {
        final short v1 = getShort();
        final short v2 = getShort();

        final int intValue = ((v1 & 0xFFFF) << 16) | (v2 & 0xFFFF);
        return intValue;
    }

    @Override
    public BuffersBuffer putInt(int value) {
        put((byte) ((value >>> 24) & 0xFF));
        put((byte) ((value >>> 16) & 0xFF));
        put((byte) ((value >>>  8) & 0xFF));
        put((byte) ((value >>>  0) & 0xFF));

        return this;
    }

    @Override
    public int getInt(int index) {
        checkDispose();

        long location = locateBufferPosition(index);
        final int bufferIndex = getBufferIndex(location);
        final int bufferPosition = getBufferPosition(location);
        final Buffer buffer = buffers[bufferIndex];

        if (buffer.limit() - bufferPosition >= 4) {
            return buffer.getInt(bufferPosition);
        } else {
            int ch1 = buffer.get(bufferPosition) & 0xFF;

            location = incLocation(location);
            int ch2 = bufferGet(location) & 0xFF;

            location = incLocation(location);
            int ch3 = bufferGet(location) & 0xFF;

            location = incLocation(location);
            int ch4 = bufferGet(location) & 0xFF;

            return ((ch1 << 24) + (ch2 << 16) + (ch3 << 8) + (ch4 << 0));
        }
    }

    @Override
    public BuffersBuffer putInt(int index, int value) {
        checkDispose();
        checkReadOnly();

        long location = locateBufferPosition(index);
        final int bufferIndex = getBufferIndex(location);
        final int bufferPosition = getBufferPosition(location);
        final Buffer buffer = buffers[bufferIndex];

        if (buffer.limit() - bufferPosition >= 4) {
            buffer.putInt(bufferPosition, value);
        } else {
            buffer.put(bufferPosition, (byte) ((value >>> 24) & 0xFF));

            location = incLocation(location);
            bufferPut(location, (byte) ((value >>> 16) & 0xFF));

            location = incLocation(location);
            bufferPut(location, (byte) ((value >>> 8) & 0xFF));

            location = incLocation(location);
            bufferPut(location, (byte) ((value >>> 0) & 0xFF));
        }

        return this;
    }

    @Override
    public long getLong() {
        final int v1 = getInt();
        final int v2 = getInt();

        final long longValue = ((v1 & 0xFFFFFFFFL) << 32) | (v2 & 0xFFFFFFFFL);
        return longValue;
    }

    @Override
    public BuffersBuffer putLong(long value) {
        put((byte) ((value >>> 56) & 0xFF));
        put((byte) ((value >>> 48) & 0xFF));
        put((byte) ((value >>> 40) & 0xFF));
        put((byte) ((value >>> 32) & 0xFF));
        put((byte) ((value >>> 24) & 0xFF));
        put((byte) ((value >>> 16) & 0xFF));
        put((byte) ((value >>> 8) & 0xFF));
        put((byte) ((value >>> 0) & 0xFF));

        return this;
    }

    @Override
    public long getLong(int index) {
        checkDispose();

        long location = locateBufferPosition(index);
        final int bufferIndex = getBufferIndex(location);
        final int bufferPosition = getBufferPosition(location);
        final Buffer buffer = buffers[bufferIndex];

        if (buffer.limit() - bufferPosition >= 8) {
            return buffer.getLong(bufferPosition);
        } else {
            int ch1 = buffer.get(bufferPosition);

            location = incLocation(location);
            int ch2 = bufferGet(location) & 0xFF;

            location = incLocation(location);
            int ch3 = bufferGet(location) & 0xFF;

            location = incLocation(location);
            int ch4 = bufferGet(location) & 0xFF;

            location = incLocation(location);
            int ch5 = bufferGet(location) & 0xFF;

            location = incLocation(location);
            int ch6 = bufferGet(location) & 0xFF;

            location = incLocation(location);
            int ch7 = bufferGet(location) & 0xFF;

            location = incLocation(location);
            int ch8 = bufferGet(location) & 0xFF;

            return (((long) ch1 << 56) +
                ((long) ch2 << 48) +
		((long) ch3 << 40) +
                ((long) ch4 << 32) +
                ((long) ch5 << 24) +
                (ch6 << 16) +
                (ch7 <<  8) +
                (ch8 <<  0));
        }
    }

    @Override
    public BuffersBuffer putLong(int index, long value) {
        checkDispose();
        checkReadOnly();

        long location = locateBufferPosition(index);
        final int bufferIndex = getBufferIndex(location);
        final int bufferPosition = getBufferPosition(location);
        final Buffer buffer = buffers[bufferIndex];

        if (buffer.limit() - bufferPosition >= 8) {
            buffer.putLong(bufferPosition, value);
        } else {
            buffer.put(bufferPosition, (byte) ((value >>> 56) & 0xFF));

            location = incLocation(location);
            bufferPut(location, (byte) ((value >>> 48) & 0xFF));

            location = incLocation(location);
            bufferPut(location, (byte) ((value >>> 40) & 0xFF));

            location = incLocation(location);
            bufferPut(location, (byte) ((value >>> 32) & 0xFF));

            location = incLocation(location);
            bufferPut(location, (byte) ((value >>> 24) & 0xFF));

            location = incLocation(location);
            bufferPut(location, (byte) ((value >>> 16) & 0xFF));

            location = incLocation(location);
            bufferPut(location, (byte) ((value >>> 8) & 0xFF));

            location = incLocation(location);
            bufferPut(location, (byte) ((value >>> 0) & 0xFF));
        }

        return this;
    }

    @Override
    public float getFloat() {
        return Float.intBitsToFloat(getInt());
    }

    @Override
    public BuffersBuffer putFloat(float value) {
        return putInt(Float.floatToIntBits(value));
    }

    @Override
    public float getFloat(int index) {
        return Float.intBitsToFloat(getInt(index));
    }

    @Override
    public BuffersBuffer putFloat(int index, float value) {
        return putInt(index, Float.floatToIntBits(value));
    }

    @Override
    public double getDouble() {
        return Double.longBitsToDouble(getLong());
    }

    @Override
    public BuffersBuffer putDouble(double value) {
        return putLong(Double.doubleToLongBits(value));
    }

    @Override
    public double getDouble(int index) {
        return Double.longBitsToDouble(getLong(index));
    }

    @Override
    public BuffersBuffer putDouble(int index, double value) {
        return putLong(index, Double.doubleToLongBits(value));
    }

    @Override
    public int compareTo(Buffer that) {
        checkDispose();

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
    public String toStringContent() {
        return toStringContent(null, position, limit);
    }

    @Override
    public String toStringContent(Charset charset) {
        return toStringContent(charset, position, limit);
    }

    @Override
    public String toStringContent(Charset charset, int position, int limit) {
        checkDispose();

        if (charset == null) {
            charset = Charset.defaultCharset();
        }

        final long posLocation = locateBufferPosition(position);
        final long limLocation = locateBufferLimit(limit);

        final Buffer posBuffer = buffers[getBufferIndex(posLocation)];
        final Buffer limBuffer = buffers[getBufferIndex(limLocation)];

        if (posBuffer == limBuffer) {
            return posBuffer.toStringContent(charset,
                    getBufferPosition(posLocation),
                    getBufferPosition(limLocation));
        } else {
            byte[] tmpBuffer = new byte[limit - position];

            int oldPosition = this.position;
            int oldLimit = this.limit;

            setPosLim(position, limit);
            get(tmpBuffer);
            setPosLim(oldPosition, oldLimit);
            return new String(tmpBuffer, charset);
        }
    }

    @Override
    public ByteBuffer toByteBuffer() {
        return toByteBuffer(position, limit);
    }

    @Override
    public ByteBuffer toByteBuffer(int position, int limit) {
        if (buffersSize == 0) {
            return BufferUtils.EMPTY_BYTE_BUFFER;
        } else if (buffersSize == 1) {
            return buffers[0].toByteBuffer();
        }

        final long bufferLocation1 = locateBufferPosition(position);
        final long bufferLocation2 = locateBufferLimit(limit);

        final int pos1 = getBufferIndex(bufferLocation1);
        final int pos2 = getBufferIndex(bufferLocation2);

        final int bufPosition = getBufferPosition(bufferLocation1);
        final int bufLimit = getBufferPosition(bufferLocation2);

        if (pos1 == pos2) {
            final Buffer buffer = buffers[pos1];
            return buffer.toByteBuffer(bufPosition, bufLimit);
        }

        final ByteBuffer resultByteBuffer = MemoryUtils.allocateByteBuffer(
                memoryManager, limit - position);


        final Buffer startBuffer = buffers[pos1];
        fillByteBuffer(resultByteBuffer,
                startBuffer.toByteBufferArray(bufPosition, startBuffer.limit()));

        for(int i = pos1 + 1; i < pos2; i++) {
            fillByteBuffer(resultByteBuffer, buffers[i].toByteBufferArray());
        }

        final Buffer endBuffer = buffers[pos2];
        fillByteBuffer(resultByteBuffer,
                endBuffer.toByteBufferArray(endBuffer.position(), bufLimit));

        return (ByteBuffer) resultByteBuffer.flip();
    }

    private void fillByteBuffer(ByteBuffer bb, ByteBuffer[] bbs) {
        for(ByteBuffer srcByteBuffer : bbs) {
            bb.put(srcByteBuffer);
        }
    }

    @Override
    public ByteBuffer[] toByteBufferArray() {
        return toByteBufferArray(position, limit);
    }

    @Override
    public ByteBuffer[] toByteBufferArray(int position, int limit) {
        if (buffersSize == 0) {
            return BufferUtils.EMPTY_BYTE_BUFFER_ARRAY;
        } else if (buffersSize == 1) {
            return buffers[0].toByteBufferArray();
        }

        final long bufferLocation1 = locateBufferPosition(position);
        final long bufferLocation2 = locateBufferLimit(limit);

        final int pos1 = getBufferIndex(bufferLocation1);
        final int pos2 = getBufferIndex(bufferLocation2);

        final int bufPosition = getBufferPosition(bufferLocation1);
        final int bufLimit = getBufferPosition(bufferLocation2);

        if (pos1 == pos2) {
            final Buffer buffer = buffers[pos1];
            return buffer.toByteBufferArray(bufPosition, bufLimit);
        }

        int resultBuffersSize = 0;
        ByteBuffer[] resultBuffers = new ByteBuffer[8];

        final Buffer startBuffer = buffers[pos1];
        final ByteBuffer[] startBuffers = startBuffer.toByteBufferArray(
                bufPosition, startBuffer.limit());
        int length = startBuffers.length;
        resultBuffers = ensureArrayCapacity(resultBuffers,
                resultBuffersSize + length);
        System.arraycopy(startBuffers, 0, resultBuffers, 0, length);
        resultBuffersSize += length;

        for(int i = pos1 + 1; i < pos2; i++) {
            final Buffer srcBuffer = buffers[i];
            final ByteBuffer[] srcByteBufferArray = srcBuffer.toByteBufferArray();
            length = srcByteBufferArray.length;
            resultBuffers = ensureArrayCapacity(resultBuffers,
                    resultBuffersSize + length);
            System.arraycopy(srcByteBufferArray, 0, resultBuffers,
                    resultBuffersSize, length);
            resultBuffersSize += length;
        }

        final Buffer endBuffer = buffers[pos2];
        final ByteBuffer[] endBuffers = endBuffer.toByteBufferArray(
                endBuffer.position(), bufLimit);
        length = endBuffers.length;
        resultBuffers = ensureArrayCapacity(resultBuffers,
                resultBuffersSize + length);
        System.arraycopy(endBuffers, 0, resultBuffers, resultBuffersSize, length);
        resultBuffersSize += length;

        if (resultBuffersSize != resultBuffers.length) {
            resultBuffers = Arrays.copyOf(resultBuffers, resultBuffersSize);
        }
        
        return resultBuffers;
    }

    private ByteBuffer[] ensureArrayCapacity(ByteBuffer[] originalArray, int newCapacity) {
        if (originalArray.length >= newCapacity) {
            return originalArray;
        }

        return Arrays.copyOf(originalArray,
                Math.max(newCapacity,(originalArray.length * 3) / 2 + 1));
    }

    private void removeBuffers(boolean force) {
        if (force || allowBufferDispose) {
            for (Buffer buffer : buffers) {
                buffer.dispose();
            }
        }

        buffersSize = 0;
        Arrays.fill(buffers, null);
    }

    private void resetLastBuffer() {
        lastBuffer = null;
    }

    private void prepareLastBuffer() {
        if (lastBuffer != null) {
            if (++lastBufferPosition >= lastBuffer.limit()) {
                lastBufferIndex++;
                lastBuffer = buffers[lastBufferIndex];
                lastBufferPosition = lastBuffer.position();
            }
        } else {
            long location = locateBufferPosition(position);
            lastBufferIndex = getBufferIndex(location);
            lastBufferPosition = getBufferPosition(location);
            lastBuffer = buffers[lastBufferIndex];
        }
    }

    private long locateBufferPosition(int position) {
        if (buffersSize == 0) return -1;

        Buffer buffer = buffers[0];
        int currentOffset = buffer.remaining();
        if (position < currentOffset) {
            return position + buffer.position();
        }

        for (int i = 1; i < buffersSize; i++) {
            buffer = buffers[i];

            final int newOffset = currentOffset + buffer.remaining();
            if (position < newOffset) {
                return makeLocation(i, position - currentOffset);
            }

            currentOffset = newOffset;
        }

        throw new IndexOutOfBoundsException("Position " + position + " is out of bounds");
    }

    public long locateBufferLimit(int limit) {
        if (buffersSize == 0) return -1;

        Buffer buffer = buffers[0];
        int currentOffset = buffer.remaining();
        if (limit <= currentOffset) {
            return limit + buffer.position();
        }

        for (int i = 1; i < buffersSize; i++) {
            buffer = buffers[i];

            final int newOffset = currentOffset + buffer.remaining();
            if (limit <= newOffset) {
                return makeLocation(i, limit - currentOffset);
            }

            currentOffset = newOffset;
        }

        throw new IndexOutOfBoundsException("Limit " + limit + " is out of bounds");
    }

    private long incLocation(final long location) {
        int bufferIndex = getBufferIndex(location);
        int bufferPosition = getBufferPosition(location);
        Buffer buffer = buffers[bufferIndex];

        if (bufferPosition + 1 < buffer.limit()) {
            return location + 1;
        }

        for (int i = bufferIndex + 1; i < buffersSize; i++) {
            buffer = buffers[bufferIndex];
            if (buffer.hasRemaining()) {
                return makeLocation(i, buffer.position());
            }
        }

        throw new IndexOutOfBoundsException();
    }

    private byte bufferGet(final long location) {
        final int bufferIndex = getBufferIndex(location);
        final int bufferPosition = getBufferPosition(location);
        final Buffer buffer = buffers[bufferIndex];

        return buffer.get(bufferPosition);
    }

    private BuffersBuffer bufferPut(final long location, final byte value) {
        final int bufferIndex = getBufferIndex(location);
        final int bufferPosition = getBufferPosition(location);
        final Buffer buffer = buffers[bufferIndex];

        buffer.put(bufferPosition, value);
        return this;
    }

    private static int getBufferIndex(long bufferLocation) {
        return (int) (bufferLocation >>> 32);
    }

    private static int getBufferPosition(long bufferLocation) {
        return (int) (bufferLocation & 0xFFFFFFFF);
    }

    private static long makeLocation(final int bufferIndex,
            final int bufferPosition) {
        return (long) (((long) bufferIndex) << 32) | (long) bufferPosition;
    }

    private void setPosLim(int position, int limit) {
        if (position > limit) {
            throw new IllegalArgumentException("Position exceeds a limit: " + position + ">" + limit);
        }

        this.position = position;
        this.limit = limit;
        resetLastBuffer();
    }

    private void checkDispose() {
        if (isDisposed) {
            throw new IllegalStateException(
                    "CompositeBuffer has already been disposed");
        }
    }

    private void checkReadOnly() {
        if (isReadOnly) throw new IllegalStateException("Buffer is in read-only mode");
    }

    private int calcCapacity() {
        int currentCapacity = 0;
        for(int i=0; i<buffersSize; i++) {
            currentCapacity += buffers[i].remaining();
        }

        return currentCapacity;
    }

    @Override
    public void removeAll() {
        buffersSize = 0;
        position = 0;
        limit = 0;
        Arrays.fill(buffers, null);
    }
}
