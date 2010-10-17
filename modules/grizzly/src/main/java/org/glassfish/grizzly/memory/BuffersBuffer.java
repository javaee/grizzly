/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2009-2010 Oracle and/or its affiliates. All rights reserved.
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

import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.ThreadCache;
import org.glassfish.grizzly.TransportFactory;
import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.util.Arrays;
import org.glassfish.grizzly.utils.ArrayUtils;

/**
 *
 * @author Alexey Stashok
 */
public final class BuffersBuffer extends CompositeBuffer {
    private static final ThreadCache.CachedTypeIndex<BuffersBuffer> CACHE_IDX =
            ThreadCache.obtainIndex(BuffersBuffer.class, 5);

    /**
     * Construct <tt>BuffersBuffer</tt>.
     */
    public static BuffersBuffer create() {
        return create(TransportFactory.getInstance().getDefaultMemoryManager(),
                null, 0, false);
    }

    public static BuffersBuffer create(MemoryManager memoryManager) {
        return create(memoryManager, null, 0, false);
    }

    public static BuffersBuffer create(MemoryManager memoryManager,
            Buffer... buffers) {
        return create(memoryManager, buffers, buffers.length, false);
    }

    public static BuffersBuffer create(MemoryManager memoryManager,
            Buffer[] buffers, boolean isReadOnly) {
        return create(memoryManager, buffers, buffers.length, isReadOnly);
    }

    private static BuffersBuffer create(MemoryManager memoryManager,
            Buffer[] buffers, int buffersSize, boolean isReadOnly) {
        final BuffersBuffer buffer = ThreadCache.takeFromCache(CACHE_IDX);
        if (buffer != null) {
            buffer.set(memoryManager, buffers, buffersSize, isReadOnly);
            buffer.isDisposed = false;
            return buffer;
        }

        return new BuffersBuffer(memoryManager, buffers, buffersSize, isReadOnly);
    }

    private MemoryManager memoryManager;

    private ByteOrder byteOrder = ByteOrder.nativeOrder();

    // Allow to dispose this BuffersBuffer
    private boolean allowBufferDispose = false;

    // Allow to try to dispose internal buffers
    private boolean allowInternalBuffersDispose = true;

    private boolean isDisposed;

    private boolean isReadOnly;

    // absolute position
    private int position;

    // absolute limit
    private int limit;

    // absolute capacity
    private int capacity;

    // List of wrapped buffers
    private int[] bufferBounds;
    private Buffer[] buffers;
    private int buffersSize;

    private int lastSegmentIndex;
    private int lowerBound;
    private int upperBound;
    private int activeBufferLowerBound;
    private Buffer activeBuffer;
    

    protected BuffersBuffer(MemoryManager memoryManager,
            Buffer[] buffers, int buffersSize, boolean isReadOnly) {
        set(memoryManager, buffers, buffersSize, isReadOnly);
    }

    private void set(MemoryManager memoryManager, Buffer[] buffers,
            int buffersSize, boolean isReadOnly) {
        if (memoryManager != null) {
            this.memoryManager = memoryManager;
        } else {
            this.memoryManager = TransportFactory.getInstance().getDefaultMemoryManager();
        }

        if (buffers != null || this.buffers == null) {
            initBuffers(buffers, buffersSize);
            calcCapacity();
            this.limit = capacity;
        }

        this.isReadOnly = isReadOnly;
    }

    private void initBuffers(Buffer[] buffers, int bufferSize) {
        this.buffers = buffers != null ? buffers : new Buffer[4];
        this.buffersSize = bufferSize;
        this.bufferBounds = new int[this.buffers.length];
    }

    private BuffersBuffer copy(BuffersBuffer that) {
        this.memoryManager = that.memoryManager;
        initBuffers(Arrays.copyOf(that.buffers, that.buffers.length), that.buffersSize);
        System.arraycopy(that.bufferBounds, 0, this.bufferBounds, 0, that.buffersSize);
        
        this.position = that.position;
        this.limit = that.limit;
        this.capacity = that.capacity;
        this.isReadOnly = that.isReadOnly;

        return this;
    }

    @Override
    public final boolean tryDispose() {
        if (allowBufferDispose) {
            dispose();
            return true;
        } else if (allowInternalBuffersDispose) {
            removeAndDisposeBuffers(true);
        }

        return false;
    }

    @Override
    public void dispose() {
        checkDispose();
        isDisposed = true;
        removeAndDisposeBuffers(true);

        ThreadCache.putToCache(CACHE_IDX, this);
    }

    @Override
    public final boolean isComposite() {
        return true;
    }

    @Override
    public BuffersBuffer append(Buffer buffer) {
        checkDispose();
        checkReadOnly();

        ensureBuffersCapacity(1);

        capacity += buffer.remaining();
        bufferBounds[buffersSize] = capacity;
        buffers[buffersSize++] = buffer;

        limit = capacity;

        resetLastLocation();

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
        calcCapacity();
        limit = capacity;

        resetLastLocation();

        return this;
    }

    private void ensureBuffersCapacity(int newElementsNum) {
        final int newSize = buffersSize + newElementsNum;

        if (newSize > buffers.length) {
            final int newCapacity = Math.max(newSize, (buffers.length * 3) / 2 + 1);
            
            buffers = Arrays.copyOf(buffers, newCapacity);
            bufferBounds = Arrays.copyOf(bufferBounds, newCapacity);
        }
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
        calcCapacity();
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
        final BuffersBuffer buffer = create().copy(this);
        buffer.isReadOnly = true;

        return buffer;
    }

    @Override
    public Buffer split(int splitPosition) {
        checkDispose();

        final int oldPosition = position;
        final int oldLimit = limit;
        
        if (splitPosition == capacity) {
            return Buffers.EMPTY_BUFFER;
        } else if (splitPosition == 0) {
            final BuffersBuffer slice2Buffer = BuffersBuffer.create(
                    memoryManager, buffers, buffersSize, isReadOnly);
            slice2Buffer.setPosLim(position, limit);
            initBuffers(null, 0);
            position = 0;
            limit = 0;
            capacity = 0;
            resetLastLocation();
            
            return slice2Buffer;
        }

        checkIndex(splitPosition);

        final int splitBufferIdx = lastSegmentIndex;

        final int splitBufferPos = toActiveBufferPos(splitPosition);

        final BuffersBuffer slice2Buffer = BuffersBuffer.create(memoryManager);
        final Buffer splitBuffer = activeBuffer;

        int newSize = splitBufferIdx + 1;

        if (splitBufferPos == 0) {
            slice2Buffer.append(splitBuffer);
            buffers[splitBufferIdx] = null;
            newSize = splitBufferIdx;
        } else if (splitBufferPos < splitBuffer.limit()) {
            final Buffer splitBuffer2 = splitBuffer.split(splitBufferPos);
            slice2Buffer.append(splitBuffer2);
        }

        for (int i = splitBufferIdx + 1; i < buffersSize; i++) {
            slice2Buffer.append(buffers[i]);
            buffers[i] = null;
        }

        buffersSize = newSize;

        calcCapacity();
        
        if (oldPosition < splitPosition) {
            position = oldPosition;
        } else {
            position = capacity;
            slice2Buffer.position(oldPosition - splitPosition);
        }

        if (oldLimit < splitPosition) {
            limit = oldLimit;
            slice2Buffer.limit(0);
        } else {
            slice2Buffer.limit(oldLimit - splitPosition);
            limit = capacity;
        }

        resetLastLocation();

        return slice2Buffer;
    }

    @Override
    public void shrink() {
        checkDispose();

        if (position == limit) {
            removeAndDisposeBuffers(false);
            return;
        }

        checkIndex(position);
        final int posBufferIndex = lastSegmentIndex;
        final int posBufferPos = toActiveBufferPos(position);

        checkIndex(limit - 1);
        final int limitBufferIndex = lastSegmentIndex;
        final int limitBufferLimit = toActiveBufferPos(limit);

        final int rightTrim = buffersSize - limitBufferIndex - 1;

        if (posBufferIndex > 0) {
            for (int i = 0; i < posBufferIndex; i++) {
                final Buffer buffer = buffers[i];
                final int bufferSize = buffer.remaining();
                setPosLim(position - bufferSize, limit - bufferSize);

                if (allowInternalBuffersDispose) {
                    buffer.tryDispose();
                }
            }
        }

        if (rightTrim > 0) {
            for (int i = 0; i < rightTrim; i++) {
                final int idx = buffersSize - i - 1;
                final Buffer buffer = buffers[idx];
                buffers[idx] = null;

                if (allowInternalBuffersDispose) {
                    buffer.tryDispose();
                }
            }
        }

        buffersSize -= (posBufferIndex + rightTrim);

        if (posBufferIndex > 0) {
            System.arraycopy(buffers, posBufferIndex, buffers, 0, buffersSize);
            Arrays.fill(buffers, buffersSize, buffersSize + posBufferIndex, null);
        }

        if (posBufferPos > 0) {
            final Buffer firstBuffer = buffers[0];
            final int diff = posBufferPos - firstBuffer.position();
            if (diff > 0) {
                firstBuffer.position(posBufferPos);

                position = 0;
                limit -= diff;
            }
        }

        if (limitBufferLimit > 0) {
            final Buffer lastBuffer = buffers[buffersSize - 1];
            final int diff = lastBuffer.limit() - limitBufferLimit;
            if (diff > 0) {
                lastBuffer.limit(limitBufferLimit);
            }
        }

        calcCapacity();
        resetLastLocation();
    }

    @Override
    public void trim() {
        flip();

        if (limit == 0) {
            removeRightBuffers(0);
            capacity = 0;
        } else {
            checkIndex(limit - 1);
            capacity -= removeRightBuffers(lastSegmentIndex + 1);
        }
        
        resetLastLocation();
    }

    protected int removeRightBuffers(int startIndex) {
        int removedBytes = 0;

        for(int i=startIndex; i<buffersSize; i++) {
            final Buffer buffer = buffers[i];
            buffers[i] = null;
            removedBytes += buffer.remaining();

            if (allowInternalBuffersDispose) {
                buffer.tryDispose();
            }
        }

        buffersSize = startIndex;

        return removedBytes;
    }

    @Override
    public Buffer slice() {
        return slice(position, limit);
    }

    @Override
    public Buffer slice(final int position, final int limit) {
        checkDispose();

        if (buffersSize == 0 || (position == limit)) {
            return Buffers.EMPTY_BUFFER;
        } else if (buffersSize == 1) {
            return buffers[0].slice(position, limit);
        }

        checkIndex(position);
        final int posBufferIndex = lastSegmentIndex;
        final int posBufferPosition = toActiveBufferPos(position);

        checkIndex(limit - 1);
        final int limitBufferIndex = lastSegmentIndex;
        final int limitBufferPosition = toActiveBufferPos(limit);

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

            return BuffersBuffer.create(memoryManager, newList, newList.length, isReadOnly);
        }
    }

    @Override
    public BuffersBuffer duplicate() {
        checkDispose();
        return create().copy(this);
    }

    @Override
    public BuffersBuffer compact() {
        checkDispose();
        if (buffersSize == 0) {
            return this;
        } else if (buffersSize == 1) {
            final Buffer buffer = buffers[0];
            Buffers.setPositionLimit(buffer, buffer.position() + position,
                    buffer.position() + limit);
            buffer.compact();
        } else {
            checkIndex(position);
            final int posBufferIndex = lastSegmentIndex;
            activeBuffer.position(toActiveBufferPos(position));

            checkIndex(limit - 1);
            final int limitBufferIndex = lastSegmentIndex;
            activeBuffer.limit(toActiveBufferPos(limit));

            for(int i = posBufferIndex; i <= limitBufferIndex; i++) {
                final Buffer b1 = buffers[i - posBufferIndex];
                buffers[i - posBufferIndex] = buffers[i];
                buffers[i] = b1;
            }
        }
        
        setPosLim(0, position);
        calcCapacity();
        
        resetLastLocation();
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
    public boolean allowInternalBuffersDispose() {
        return allowInternalBuffersDispose;
    }

    @Override
    public void allowInternalBuffersDispose(boolean allowInternalBuffersDispose) {
        this.allowInternalBuffersDispose = allowInternalBuffersDispose;
    }

    @Override
    public byte get() {
       return get(position++);
    }

    @Override
    public BuffersBuffer put(byte b) {
        return put(position++, b);
    }

    @Override
    public byte get(final int index) {
        checkDispose();

        checkIndex(index);

        return activeBuffer.get(toActiveBufferPos(index));

    }
    
    @Override
    public BuffersBuffer put(int index, byte b) {
        checkDispose();
        checkReadOnly();

        checkIndex(index);

        activeBuffer.put(toActiveBufferPos(index), b);
        
        return this;
    }


    private void checkIndex(final int index) {
        if (index >= lowerBound & index < upperBound) {
            return;
        }

        recalcIndex(index);
    }

    private void recalcIndex(final int index) {
        final int idx = ArrayUtils.binarySearch(bufferBounds, 0,
                buffersSize - 1, index + 1);
        activeBuffer = buffers[idx];

        upperBound = bufferBounds[idx];
        lowerBound = upperBound - activeBuffer.remaining();
        lastSegmentIndex = idx;

        activeBufferLowerBound = lowerBound - activeBuffer.position();
    }

    private int toActiveBufferPos(final int index) {
        return index - activeBufferLowerBound;
    }

    @Override
    public BuffersBuffer get(byte[] dst) {
        return get(dst, 0, dst.length);
    }

    @Override
    public BuffersBuffer get(byte[] dst, int offset, int length) {
        checkDispose();
        if (length == 0) return this;

        if (remaining() < length) throw new BufferUnderflowException();

        checkIndex(position);

        int bufferIdx = lastSegmentIndex;
        Buffer buffer = activeBuffer;
        int bufferPosition = toActiveBufferPos(position);


        while(true) {
            int oldPos = buffer.position();
            buffer.position(bufferPosition);
            int bytesToCopy = Math.min(buffer.remaining(), length);
            buffer.get(dst, offset, bytesToCopy);
            buffer.position(oldPos);
            bufferPosition += (bytesToCopy - 1);

            length -= bytesToCopy;
            offset += bytesToCopy;
            position += bytesToCopy;

            if (length == 0) break;

            bufferIdx++;
            buffer = buffers[bufferIdx];
            bufferPosition = buffer.position();
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

        checkIndex(position);

        int bufferIdx = lastSegmentIndex;
        Buffer buffer = activeBuffer;
        int bufferPosition = toActiveBufferPos(position);

        while(true) {
            int oldPos = buffer.position();
            buffer.position(bufferPosition);
            int bytesToCopy = Math.min(buffer.remaining(), length);
            buffer.put(src, offset, bytesToCopy);
            buffer.position(oldPos);
            bufferPosition += (bytesToCopy - 1);

            length -= bytesToCopy;
            offset += bytesToCopy;
            position += bytesToCopy;

            if (length == 0) break;

            bufferIdx++;
            buffer = buffers[bufferIdx];
            bufferPosition = buffer.position();
        }

        return this;
    }

    @Override
    public BuffersBuffer put(Buffer src) {
        put(src, src.position(), src.remaining());
        src.position(src.limit());
        return this;
    }

    @Override
    public Buffer put(Buffer src, int position, int length) {
        checkDispose();
        checkReadOnly();

        Buffers.put(src, position, length, this);

        return this;
    }

    @Override
    public char getChar() {
        final char value = getChar(position);
        position += 2;
        
        return value;
    }

    @Override
    public BuffersBuffer putChar(final char value) {
        putChar(position, value);
        position += 2;
        return this;
    }

    @Override
    public char getChar(int index) {
        checkDispose();

        checkIndex(index);
        
        if (upperBound - index >= 2) {
            return activeBuffer.getChar(toActiveBufferPos(index));
        } else {
            final int ch1 = activeBuffer.get(toActiveBufferPos(index)) & 0xFF;

            checkIndex(++index);
            final int ch2 = activeBuffer.get(toActiveBufferPos(index)) & 0xFF;

            return (char) ((ch1 << 8) + (ch2));
        }
    }

    @Override
    public BuffersBuffer putChar(int index, final char value) {
        checkDispose();
        checkReadOnly();

        checkIndex(index);

        if (upperBound - index >= 2) {
            activeBuffer.putChar(toActiveBufferPos(index), value);
        } else {
            activeBuffer.put(toActiveBufferPos(index), (byte) (value >>> 8));

            checkIndex(++index);
            activeBuffer.put(toActiveBufferPos(index), (byte) (value & 0xFF));
        }

        return this;
    }

    @Override
    public short getShort() {
        final short value = getShort(position);
        position += 2;

        return value;
    }

    @Override
    public BuffersBuffer putShort(final short value) {
        putShort(position, value);
        position += 2;
        return this;
    }

    @Override
    public short getShort(int index) {
        checkDispose();

        checkIndex(index);

        if (upperBound - index >= 2) {
            return activeBuffer.getShort(toActiveBufferPos(index));
        } else {
            final int ch1 = activeBuffer.get(toActiveBufferPos(index)) & 0xFF;

            checkIndex(++index);

            final int ch2 = activeBuffer.get(toActiveBufferPos(index)) & 0xFF;

            return (short) ((ch1 << 8) + (ch2));
        }
    }

    @Override
    public BuffersBuffer putShort(int index, final short value) {
        checkDispose();
        checkReadOnly();

        checkIndex(index);

        if (upperBound - index >= 2) {
            activeBuffer.putShort(toActiveBufferPos(index), value);
        } else {
            activeBuffer.put(toActiveBufferPos(index), (byte) (value >>> 8));

            checkIndex(++index);

            activeBuffer.put(toActiveBufferPos(index), (byte) (value & 0xFF));
        }

        return this;
    }

    @Override
    public int getInt() {
        final int value = getInt(position);
        position += 4;

        return value;    }

    @Override
    public BuffersBuffer putInt(final int value) {
        putInt(position, value);
        position += 4;
        return this;
    }

    @Override
    public int getInt(int index) {
        checkDispose();

        checkIndex(index);

        if (upperBound - index >= 4) {
            return activeBuffer.getInt(toActiveBufferPos(index));
        } else {
            final int ch1 = activeBuffer.get(toActiveBufferPos(index)) & 0xFF;

            checkIndex(++index);
            final int ch2 = activeBuffer.get(toActiveBufferPos(index)) & 0xFF;

            checkIndex(++index);
            final int ch3 = activeBuffer.get(toActiveBufferPos(index)) & 0xFF;

            checkIndex(++index);
            final int ch4 = activeBuffer.get(toActiveBufferPos(index)) & 0xFF;

            return ((ch1 << 24) + (ch2 << 16) + (ch3 << 8) + (ch4));
        }
    }

    @Override
    public BuffersBuffer putInt(int index, final int value) {
        checkDispose();
        checkReadOnly();

        checkIndex(index);

        if (upperBound - index >= 4) {
            activeBuffer.putInt(toActiveBufferPos(index), value);
        } else {
            activeBuffer.put(toActiveBufferPos(index), (byte) ((value >>> 24) & 0xFF));

            checkIndex(++index);
            activeBuffer.put(toActiveBufferPos(index), (byte) ((value >>> 16) & 0xFF));

            checkIndex(++index);
            activeBuffer.put(toActiveBufferPos(index), (byte) ((value >>> 8) & 0xFF));

            checkIndex(++index);
            activeBuffer.put(toActiveBufferPos(index), (byte) ((value) & 0xFF));
        }

        return this;
    }

    @Override
    public long getLong() {
        final long value = getLong(position);
        position += 8;

        return value;
    }

    @Override
    public BuffersBuffer putLong(final long value) {
        putLong(position, value);
        position += 8;

        return this;
    }

    @Override
    public long getLong(int index) {
        checkDispose();

        checkIndex(index);

        if (upperBound - index >= 8) {
            return activeBuffer.getLong(toActiveBufferPos(index));
        } else {
            final int ch1 = activeBuffer.get(toActiveBufferPos(index)) & 0xFF;

            checkIndex(++index);
            final int ch2 = activeBuffer.get(toActiveBufferPos(index)) & 0xFF;

            checkIndex(++index);
            final int ch3 = activeBuffer.get(toActiveBufferPos(index)) & 0xFF;

            checkIndex(++index);
            final int ch4 = activeBuffer.get(toActiveBufferPos(index)) & 0xFF;

            checkIndex(++index);
            final int ch5 = activeBuffer.get(toActiveBufferPos(index)) & 0xFF;

            checkIndex(++index);
            final int ch6 = activeBuffer.get(toActiveBufferPos(index)) & 0xFF;

            checkIndex(++index);
            final int ch7 = activeBuffer.get(toActiveBufferPos(index)) & 0xFF;

            checkIndex(++index);
            final int ch8 = activeBuffer.get(toActiveBufferPos(index)) & 0xFF;

            return (((long) ch1 << 56) +
                ((long) ch2 << 48) +
		((long) ch3 << 40) +
                ((long) ch4 << 32) +
                ((long) ch5 << 24) +
                (ch6 << 16) +
                (ch7 <<  8) +
                (ch8));
        }
    }

    @Override
    public BuffersBuffer putLong(int index, final long value) {
        checkDispose();
        checkReadOnly();

        checkIndex(index);

        if (upperBound - index >= 8) {
            activeBuffer.putLong(toActiveBufferPos(index), value);
        } else {
            activeBuffer.put(toActiveBufferPos(index), (byte) ((value >>> 56) & 0xFF));

            checkIndex(++index);
            activeBuffer.put(toActiveBufferPos(index), (byte) ((value >>> 48) & 0xFF));

            checkIndex(++index);
            activeBuffer.put(toActiveBufferPos(index), (byte) ((value >>> 40) & 0xFF));

            checkIndex(++index);
            activeBuffer.put(toActiveBufferPos(index), (byte) ((value >>> 32) & 0xFF));

            checkIndex(++index);
            activeBuffer.put(toActiveBufferPos(index), (byte) ((value >>> 24) & 0xFF));

            checkIndex(++index);
            activeBuffer.put(toActiveBufferPos(index), (byte) ((value >>> 16) & 0xFF));

            checkIndex(++index);
            activeBuffer.put(toActiveBufferPos(index), (byte) ((value >>> 8) & 0xFF));

            checkIndex(++index);
            activeBuffer.put(toActiveBufferPos(index), (byte) ((value) & 0xFF));
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
	    if (v1 < v2)
		return -1;
	    return +1;
	}
	return this.remaining() - that.remaining();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("BuffersBuffer (" + System.identityHashCode(this) + ") [");
        sb.append("pos=").append(position);
        sb.append(" lim=").append(limit);
        sb.append(" cap=").append(capacity);
        sb.append(" bufferSize=").append(buffersSize);
        sb.append(" buffers=").append(Arrays.toString(buffers));
        sb.append(']');
        return sb.toString();
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

        byte[] tmpBuffer = new byte[limit - position];

        int oldPosition = this.position;
        int oldLimit = this.limit;

        setPosLim(position, limit);
        get(tmpBuffer);
        setPosLim(oldPosition, oldLimit);
        return new String(tmpBuffer, charset);
    }

    @Override
    public ByteBuffer toByteBuffer() {
        return toByteBuffer(position, limit);
    }

    @Override
    public ByteBuffer toByteBuffer(int position, int limit) {
        if (position < 0 || position > capacity || limit < 0 || limit > capacity)
            throw new IndexOutOfBoundsException();

        if (buffersSize == 0 || (position == limit)) {
            return Buffers.EMPTY_BYTE_BUFFER;
        } else if (buffersSize == 1) {
            final Buffer buffer = buffers[0];
            final int bufferPos = buffer.position();
            return buffer.toByteBuffer(bufferPos + position, bufferPos + limit);
        }

        checkIndex(position);
        final int pos1 = lastSegmentIndex;
        final int bufPosition = toActiveBufferPos(position);

        checkIndex(limit - 1);
        final int pos2 = lastSegmentIndex;
        final int bufLimit = toActiveBufferPos(limit);

        if (pos1 == pos2) {
            final Buffer buffer = buffers[pos1];
            return buffer.toByteBuffer(bufPosition, bufLimit);
        }

        final ByteBuffer resultByteBuffer = MemoryUtils.allocateByteBuffer(
                memoryManager, limit - position);

        final Buffer startBuffer = buffers[pos1];
        final ByteBufferArray array = ByteBufferArray.create();
        
        fillByteBuffer(resultByteBuffer,
                startBuffer.toByteBufferArray(array, bufPosition, startBuffer.limit()));

        for(int i = pos1 + 1; i < pos2; i++) {
            fillByteBuffer(resultByteBuffer, buffers[i].toByteBufferArray(array));
        }

        final Buffer endBuffer = buffers[pos2];
        fillByteBuffer(resultByteBuffer,
                endBuffer.toByteBufferArray(array, endBuffer.position(), bufLimit));

        array.restore();
        array.recycle();
        
        return (ByteBuffer) resultByteBuffer.flip();
    }

    private void fillByteBuffer(final ByteBuffer bb, final ByteBufferArray array) {
        final ByteBuffer[] bbs = array.getArray();
        final int size = array.size();
        
        for (int i = 0; i < size; i++) {
            final ByteBuffer srcByteBuffer = bbs[i];
            bb.put(srcByteBuffer);
        }
    }

    @Override
    public final ByteBufferArray toByteBufferArray() {
        return toByteBufferArray(position, limit);
    }

    @Override
    public final ByteBufferArray toByteBufferArray(final ByteBufferArray array) {
        if (position == 0 && limit == capacity) {
            for (int i = 0; i < buffersSize; i++) {
                buffers[i].toByteBufferArray(array);
            }

            return array;
        } else {
            return toByteBufferArray(array, position, limit);
        }
    }

    @Override
    public final ByteBufferArray toByteBufferArray(final int position, final int limit) {
        final ByteBufferArray array = ByteBufferArray.create();
        
        if (position == 0 && limit == capacity) {
            for (int i = 0; i < buffersSize; i++) {
                buffers[i].toByteBufferArray(array);
            }

            return array;
        } else {
            return toByteBufferArray(array, position, limit);
        }
    }
    
    @Override
    public final ByteBufferArray toByteBufferArray(final ByteBufferArray array,
            final int position, final int limit) {
        
        if (position < 0 || position > capacity || limit < 0 || limit > capacity)
            throw new IndexOutOfBoundsException();

        if (buffersSize == 0 || (position == limit)) {
            return array;
        } else if (buffersSize == 1) {
            final Buffer b = buffers[0];
            final int startPos = b.position();
            return b.toByteBufferArray(array, position + startPos,
                    limit + startPos);
        } else if (position == 0 && limit == capacity) {
            for (int i = 0; i < buffersSize; i++) {
                buffers[i].toByteBufferArray(array);
            }

            return array;
        }

        checkIndex(position);
        final int pos1 = lastSegmentIndex;
        final int bufPosition = toActiveBufferPos(position);

        checkIndex(limit - 1);
        final int pos2 = lastSegmentIndex;
        final int bufLimit = toActiveBufferPos(limit);

        if (pos1 == pos2) {
            final Buffer buffer = buffers[pos1];
            return buffer.toByteBufferArray(array, bufPosition, bufLimit);
        }

        final Buffer startBuffer = buffers[pos1];
        startBuffer.toByteBufferArray(array,
                bufPosition, startBuffer.limit());

        for(int i = pos1 + 1; i < pos2; i++) {
            final Buffer srcBuffer = buffers[i];
            srcBuffer.toByteBufferArray(array);
        }

        final Buffer endBuffer = buffers[pos2];
        endBuffer.toByteBufferArray(array,
                endBuffer.position(), bufLimit);

        return array;
    }
        
    private ByteBuffer[] ensureArrayCapacity(ByteBuffer[] originalArray, int newCapacity) {
        if (originalArray.length >= newCapacity) {
            return originalArray;
        }

        return Arrays.copyOf(originalArray,
                Math.max(newCapacity,(originalArray.length * 3) / 2 + 1));
    }

    private void removeAndDisposeBuffers(boolean force) {
        boolean isNulled = false;

        if (force || allowInternalBuffersDispose) {
            for (int i = buffersSize - 1; i >= 0; i--) {
                final Buffer buffer = buffers[i];
                buffer.tryDispose();
                buffers[i] = null;
            }

            isNulled = true;
        }

        position = 0;
        limit = 0;
        capacity = 0;
        if (!isNulled) {
            Arrays.fill(buffers, 0, buffersSize, null);
        }
        
        buffersSize = 0;
        
        resetLastLocation();
    }

    private void setPosLim(int position, int limit) {
        if (position > limit) {
            throw new IllegalArgumentException("Position exceeds a limit: " + position + ">" + limit);
        }

        this.position = position;
        this.limit = limit;
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

    private void calcCapacity() {
        int currentCapacity = 0;
        for(int i = 0; i < buffersSize; i++) {
            currentCapacity += buffers[i].remaining();
            bufferBounds[i] = currentCapacity;
        }

        capacity = currentCapacity;
    }

    private void resetLastLocation() {
        lowerBound = 0;
        upperBound = 0;
        activeBuffer = null;
    }

    @Override
    public void removeAll() {
        position = 0;
        limit = 0;
        capacity = 0;
        Arrays.fill(buffers, 0, buffersSize, null);

        buffersSize = 0;
        resetLastLocation();
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

    /**
     * Returns the current hash code of this buffer.
     *
     * <p> The hash code of a byte buffer depends only upon its remaining
     * elements; that is, upon the elements from <tt>position()</tt> up to, and
     * including, the element at <tt>limit()</tt>&nbsp;-&nbsp;<tt>1</tt>.
     *
     * <p> Because buffer hash codes are content-dependent, it is inadvisable
     * to use buffers as keys in hash maps or similar data structures unless it
     * is known that their contents will not change.  </p>
     *
     * @return  The current hash code of this buffer
     */
    @Override
    public int hashCode() {
	int h = 1;
	int p = position();
	for (int i = limit() - 1; i >= p; i--)
	    h = 31 * h + (int)get(i);
	return h;
    }
}
