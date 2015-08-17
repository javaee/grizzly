/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2009-2015 Oracle and/or its affiliates. All rights reserved.
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

import java.io.UnsupportedEncodingException;
import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.InvalidMarkException;
import java.nio.charset.Charset;
import java.util.Arrays;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.ThreadCache;
import org.glassfish.grizzly.utils.ArrayUtils;

/**
 *
 * @author Alexey Stashok
 */
public final class BuffersBuffer extends CompositeBuffer {
    public static volatile boolean DEBUG_MODE = false;

    private static final ThreadCache.CachedTypeIndex<BuffersBuffer> CACHE_IDX =
            ThreadCache.obtainIndex(BuffersBuffer.class,
                    Integer.getInteger(BuffersBuffer.class.getName() + ".bb-cache-size", 5));

    /**
     * Construct <tt>BuffersBuffer</tt>.
     * @return {@link BuffersBuffer}
     */
    public static BuffersBuffer create() {
        return create(MemoryManager.DEFAULT_MEMORY_MANAGER,
                null, 0, false);
    }

    public static BuffersBuffer create(final MemoryManager memoryManager) {
        return create(memoryManager, null, 0, false);
    }

    public static BuffersBuffer create(final MemoryManager memoryManager,
            final Buffer... buffers) {
        return create(memoryManager, buffers, buffers.length, false);
    }

    public static BuffersBuffer create(final MemoryManager memoryManager,
            final Buffer[] buffers, final boolean isReadOnly) {
        return create(memoryManager, buffers, buffers.length, isReadOnly);
    }

    private static BuffersBuffer create(final MemoryManager memoryManager,
            final Buffer[] buffers, final int buffersSize,
            final boolean isReadOnly) {
        return create(memoryManager, buffers, buffersSize, ByteOrder.BIG_ENDIAN,
                isReadOnly);
    }
    
    private static BuffersBuffer create(final MemoryManager memoryManager,
            final Buffer[] buffers, final int buffersSize,
            final ByteOrder byteOrder, final boolean isReadOnly) {
        final BuffersBuffer buffer = ThreadCache.takeFromCache(CACHE_IDX);
        if (buffer != null) {
            buffer.isDisposed = false;
            buffer.order(byteOrder);
            buffer.set(memoryManager, buffers, buffersSize, isReadOnly);
            return buffer;
        }

        return new BuffersBuffer(memoryManager, buffers, buffersSize, isReadOnly);
    }

    protected Exception disposeStackTrace;
    
    private MemoryManager memoryManager;

    private ByteOrder byteOrder = ByteOrder.BIG_ENDIAN; // parity with ByteBuffer

    private boolean bigEndian = true;

    // Allow to dispose this BuffersBuffer
    private boolean allowBufferDispose = false;

    // Allow to try to dispose internal buffers
    private boolean allowInternalBuffersDispose = true;

    private boolean isDisposed;

    private boolean isReadOnly;

    private int mark = -1;

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
    

    protected BuffersBuffer(final MemoryManager memoryManager,
            final Buffer[] buffers, final int buffersSize,
            final boolean isReadOnly) {
        set(memoryManager, buffers, buffersSize, isReadOnly);
    }

    private void set(final MemoryManager memoryManager, final Buffer[] buffers,
            final int buffersSize, final boolean isReadOnly) {
        this.memoryManager = memoryManager != null
                ? memoryManager
                : MemoryManager.DEFAULT_MEMORY_MANAGER;

        if (buffers != null || this.buffers == null) {
            initBuffers(buffers, buffersSize);
            refreshBuffers();
            this.limit = capacity;
        }

        this.isReadOnly = isReadOnly;
    }

    private void initBuffers(final Buffer[] buffers, final int bufferSize) {
        this.buffers = buffers != null ? buffers : new Buffer[4];
        this.buffersSize = bufferSize;

        if (bufferBounds == null || bufferBounds.length < this.buffers.length) {
            bufferBounds = new int[this.buffers.length];
        }
    }

    private BuffersBuffer duplicateFrom(final BuffersBuffer that) {
        this.memoryManager = that.memoryManager;

        final Buffer[] ba = new Buffer[that.buffers.length];
        for (int i = 0, len = that.buffersSize; i < len; i++) {
            ba[i] = that.buffers[i].duplicate();
        }
        initBuffers(ba, that.buffersSize);
        System.arraycopy(that.bufferBounds, 0, this.bufferBounds, 0, that.buffersSize);
        
        this.position = that.position;
        this.limit = that.limit;
        this.capacity = that.capacity;
        this.isReadOnly = that.isReadOnly;
        this.byteOrder = that.byteOrder;
        
        return this;
    }

    @Override
    public final boolean tryDispose() {
        if (allowBufferDispose) {
            dispose();
            return true;
        } else if (allowInternalBuffersDispose) {
            removeAndDisposeBuffers();
        }

        return false;
    }

    @Override
    public void dispose() {
        checkDispose();
        isDisposed = true;
        removeAndDisposeBuffers();

        if (DEBUG_MODE) { // if debug is on - clear the buffer content
            // Use static logic class to help JIT optimize the code
            DebugLogic.doDebug(this);
        }
        
        ThreadCache.putToCache(CACHE_IDX, this);
    }

    @Override
    public final boolean isComposite() {
        return true;
    }

    @Override
    public BuffersBuffer append(final Buffer buffer) {
        if (buffer == this) {
            throw new IllegalArgumentException("CompositeBuffer can not append itself");
        }
        
        checkDispose();
        checkReadOnly();

        ensureBuffersCapacity(1);

        buffer.order(byteOrder); // assign the correct ByteOrder
        capacity += buffer.remaining();
        bufferBounds[buffersSize] = capacity;
        buffers[buffersSize++] = buffer;
        
        limit = capacity;

        resetLastLocation();
        
        return this;
    }

    @Override
    public BuffersBuffer prepend(final Buffer buffer) {
        if (buffer == this) {
            throw new IllegalArgumentException("CompositeBuffer can not append itself");
        }
        
        checkDispose();
        checkReadOnly();

        ensureBuffersCapacity(1);
        
        buffer.order(byteOrder);  // assign the correct ByteOrder
        System.arraycopy(buffers, 0, buffers, 1, buffersSize);
        buffers[0] = buffer;

        buffersSize++;
        refreshBuffers();
        position = 0;
        limit += buffer.remaining();

        resetLastLocation();

        return this;
    }

    @Override
    public boolean replace(final Buffer oldBuffer, final Buffer newBuffer) {
        if (newBuffer == this) {
            throw new IllegalArgumentException("CompositeBuffer can not append itself");
        }
        
        for (int i = 0; i < buffersSize; i++) {
            final Buffer b = buffers[i];
            if (b == oldBuffer) {
                buffers[i] = newBuffer;
                refreshBuffers();
                limit = capacity;

                if (position > limit) {
                    position = limit;
                }

                resetLastLocation();

                return true;
            } else if (b.isComposite()) {
                if (((CompositeBuffer) b).replace(oldBuffer, newBuffer)) {
                    break;
                }
            }
        }

        return false;
    }


    private void ensureBuffersCapacity(final int newElementsNum) {
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
    public BuffersBuffer position(final int newPosition) {
        checkDispose();
        setPosLim(newPosition, limit);
        if (mark > position) mark = -1;
        return this;
    }

    @Override
    public int limit() {
        checkDispose();
        return limit;
    }

    @Override
    public BuffersBuffer limit(final int newLimit) {
        checkDispose();
        setPosLim(position <= newLimit ? position : newLimit, newLimit);
        if (mark > limit) mark = -1;
        return this;
    }

    @Override
    public int capacity() {
        checkDispose();
        return capacity;
    }

    @Override
    public BuffersBuffer mark() {
        mark = position;
        return this;
    }

    @Override
    public BuffersBuffer reset() {
        int m = mark;
        if (m < 0)
            throw new InvalidMarkException();
        position = m;
        return this;
    }

    @Override
    public boolean isDirect() {
        return buffers[0].isDirect();
    }

    @Override
    public BuffersBuffer clear() {
        checkDispose();
        refreshBuffers();
        setPosLim(0, capacity);
        mark = -1;
        return this;
    }

    @Override
    public BuffersBuffer flip() {
        checkDispose();
        setPosLim(0, position);
        mark = -1;
        return this;
    }

    @Override
    public BuffersBuffer rewind() {
        checkDispose();
        setPosLim(0, limit);
        mark = -1;
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
        final BuffersBuffer buffer = create().duplicateFrom(this);
        buffer.isReadOnly = true;

        return buffer;
    }

    @Override
    public Buffer split(final int splitPosition) {
        checkDispose();
        if (splitPosition < 0 || splitPosition > capacity) {
            throw new IllegalArgumentException("Invalid splitPosition value, should be 0 <= splitPosition <= capacity");
        }

        final int oldPosition = position;
        final int oldLimit = limit;
        
        if (splitPosition == capacity) {
            return Buffers.EMPTY_BUFFER;
        } else if (splitPosition == 0) {
            final BuffersBuffer slice2Buffer = BuffersBuffer.create(
                    memoryManager, buffers, buffersSize, byteOrder, isReadOnly);
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

        final BuffersBuffer slice2Buffer =
                BuffersBuffer.create(memoryManager, null, 0, byteOrder, false);
        
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

        refreshBuffers();
        
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
            removeAndDisposeBuffers();
            return;
        }

        checkIndex(position);
        final int posBufferIndex = lastSegmentIndex;
        final int posBufferPosition = toActiveBufferPos(position);

        checkIndex(limit - 1);
        final int limitBufferIndex = lastSegmentIndex;

        final int rightTrim = buffersSize - limitBufferIndex - 1;

        int shift = 0;

        // Shift buffers left to position
        for (int i = 0; i < posBufferIndex; i++) {
            final Buffer buffer = buffers[i];
            shift += buffer.remaining();

            if (allowInternalBuffersDispose) {
                buffer.tryDispose();
            }
        }

        // Shift the position buffer
        final Buffer posBuffer = buffers[posBufferIndex];        
        final int diff = posBufferPosition - posBuffer.position();
        if (diff > 0) {
            posBuffer.position(posBufferPosition);
            posBuffer.shrink();
            shift += diff;
        }       
        
        setPosLim(position - shift, limit - shift);
        if (mark > position) mark = -1;

        for (int i = 0; i < rightTrim; i++) {
            final int idx = buffersSize - i - 1;
            final Buffer buffer = buffers[idx];
            buffers[idx] = null;

            if (allowInternalBuffersDispose) {
                buffer.tryDispose();
            }
        }

        buffersSize -= (posBufferIndex + rightTrim);

        if (posBufferIndex > 0) {
            System.arraycopy(buffers, posBufferIndex, buffers, 0, buffersSize);
            Arrays.fill(buffers, buffersSize, buffersSize + posBufferIndex, null);
        }

        refreshBuffers();
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

        //noinspection ConstantConditions
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

            return BuffersBuffer.create(memoryManager, newList, newList.length,
                    byteOrder, isReadOnly);
        }
    }

    @Override
    public BuffersBuffer duplicate() {
        checkDispose();
        return create().duplicateFrom(this);
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
        refreshBuffers();
        
        resetLastLocation();
        return this;
    }

    @Override
    public ByteOrder order() {
        checkDispose();
        return byteOrder;
    }

    @Override
    public BuffersBuffer order(final ByteOrder bo) {
        checkDispose();
        
        if (bo != byteOrder) {
            this.byteOrder = bo;
            this.bigEndian = (bo == ByteOrder.BIG_ENDIAN);
            
            // propagate ByteOrder to underlying Buffers
            for (int i = 0; i < buffersSize; i++) {
                buffers[i].order(bo);
            }
        }
        
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
        final int idx = index < bufferBounds[0]
                ? 0 
                : ArrayUtils.binarySearch(bufferBounds, 0,
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
    public BuffersBuffer get(final byte[] dst) {
        return get(dst, 0, dst.length);
    }

    @Override
    public BuffersBuffer get(final byte[] dst, int offset, int length) {
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
            final int bytesToCopy = Math.min(buffer.remaining(), length);
            buffer.get(dst, offset, bytesToCopy);
            buffer.position(oldPos);

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
    public BuffersBuffer put(final byte[] src) {
        return put(src, 0, src.length);
    }

    @Override
    public BuffersBuffer put(final byte[] src, int offset, int length) {
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
    public BuffersBuffer put8BitString(final String s) {
        final int len = s.length();
        if (remaining() < len) {
            throw new BufferOverflowException();
        }

        for (int i = 0; i < len; i++) {
            put((byte) s.charAt(i));
        }
        
        return this;
    }

    @Override
    public BuffersBuffer get(final ByteBuffer dst) {
        get(dst, dst.position(), dst.remaining());
        dst.position(dst.limit());

        return this;
    }

    @Override
    public BuffersBuffer get(final ByteBuffer dst, int offset, int length) {

        if (length == 0) {
            return this;
        }
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
            final int bytesToCopy = Math.min(buffer.remaining(), length);
            buffer.get(dst, offset, bytesToCopy);
            buffer.position(oldPos);

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
    public BuffersBuffer put(final ByteBuffer src) {
        put(src, 0, src.remaining());
        src.position(src.limit());

        return this;
    }

    @Override
    public BuffersBuffer put(final ByteBuffer src, int offset, int length) {
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
    public char getChar(final int index) {
        checkDispose();

        checkIndex(index);
        
        if (upperBound - index >= 2) {
            return activeBuffer.getChar(toActiveBufferPos(index));
        } else {
            return ((bigEndian) ? makeCharB(index) : makeCharL(index));
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
            if (bigEndian) {
                putCharB(index, value);
            } else {
                putCharL(index, value);
            }
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
    public short getShort(final int index) {
        checkDispose();

        checkIndex(index);

        if (upperBound - index >= 2) {
            return activeBuffer.getShort(toActiveBufferPos(index));
        } else {
            return ((bigEndian) ? makeShortB(index) : makeShortL(index));
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
            if (bigEndian) {
                putShortB(index, value);
            } else {
                putShortL(index, value);
            }
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
    public int getInt(final int index) {
        checkDispose();

        checkIndex(index);

        if (upperBound - index >= 4) {
            return activeBuffer.getInt(toActiveBufferPos(index));
        } else {
            return ((bigEndian) ? makeIntB(index) : makeIntL(index));
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
            if (bigEndian) {
                putIntB(index, value);
            } else {
                putIntL(index, value);
            }
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
    public long getLong(final int index) {
        checkDispose();

        checkIndex(index);

        if (upperBound - index >= 8) {
            return activeBuffer.getLong(toActiveBufferPos(index));
        } else {
            return ((bigEndian) ? makeLongB(index) : makeLongL(index));
        }
    }

    @Override
    public BuffersBuffer putLong(final int index, final long value) {
        checkDispose();
        checkReadOnly();

        checkIndex(index);

        if (upperBound - index >= 8) {
            activeBuffer.putLong(toActiveBufferPos(index), value);
        } else {
            if (bigEndian) {
                putLongB(index, value);
            } else {
                putLongL(index, value);
            }
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

    /**
     * {@inheritDoc}
     */
    @Override
    public int bulk(final BulkOperation operation) {
        return bulk(operation, position, limit);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int bulk(final BulkOperation operation,
            final int position, final int limit) {
        
        checkDispose();

        int length = limit - position;
        if (length == 0) return -1;

        int offset = position;
        
        checkIndex(position);

        int bufferIdx = lastSegmentIndex;
        Buffer buffer = activeBuffer;
        int bufferPosition = toActiveBufferPos(position);


        while(true) {
            final int bytesToProcess = Math.min(
                    buffer.limit() - bufferPosition, length);
            
            if (buffer.isComposite()) {
                final int findPos = ((CompositeBuffer) buffer).bulk(operation,
                        bufferPosition, bufferPosition + bytesToProcess);
                
                if (findPos != -1) {
                    return offset + (findPos - bufferPosition);
                }
            } else {
                setter.buffer = buffer;
                for (int i = bufferPosition; i < bufferPosition + bytesToProcess; i++) {
                    setter.position = i;
                    if (operation.processByte(buffer.get(i), setter)) {
                        return offset + (i - bufferPosition);
                    }
                }
            }
            
            
            length -= bytesToProcess;

            if (length == 0) return -1;

            offset += bytesToProcess;
            
            bufferIdx++;
            buffer = buffers[bufferIdx];
            bufferPosition = buffer.position();
        }
    }
    
    private final SetterImpl setter = new SetterImpl();

    private final static class SetterImpl implements Setter {
        private Buffer buffer;
        private int position;
                
        @Override
        public void set(final byte value) {
            buffer.put(position, value);
        }
        
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
    public String toStringContent(Charset charset, final int position,
            final int limit) {
        checkDispose();

        if (charset == null) {
            charset = Charset.defaultCharset();
        }

        final byte[] tmpBuffer = new byte[limit - position];

        int oldPosition = this.position;
        int oldLimit = this.limit;

        setPosLim(position, limit);
        get(tmpBuffer);
        setPosLim(oldPosition, oldLimit);

        try {
            return new String(tmpBuffer, charset.name());
        } catch (UnsupportedEncodingException e) {
            // Should never get here
            throw new IllegalStateException("We took charset name from Charset, why it's not unsupported?", e);
        }
    }

    @Override
    public void dumpHex(java.lang.Appendable appendable) {
        Buffers.dumpBuffer(appendable, this);
    }

    @Override
    public ByteBuffer toByteBuffer() {
        return toByteBuffer(position, limit);
    }

    @Override
    public ByteBuffer toByteBuffer(int position, int limit) {
        if (position < 0 || position > capacity || limit < 0 || limit > capacity)
            throw new IndexOutOfBoundsException("position=" + position + " limit=" + limit + "on " + toString());

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

        //noinspection ConstantConditions
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
            throw new IndexOutOfBoundsException("position=" + position + " limit=" + limit + "on " + toString());

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

        //noinspection ConstantConditions
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

    @Override
    public final BufferArray toBufferArray() {
        return toBufferArray(position, limit);
    }

    @Override
    public final BufferArray toBufferArray(final BufferArray array) {
        if (position == 0 && limit == capacity) {
            for (int i = 0; i < buffersSize; i++) {
                buffers[i].toBufferArray(array);
            }

            return array;
        } else {
            return toBufferArray(array, position, limit);
        }
    }

    @Override
    public final BufferArray toBufferArray(final int position, final int limit) {
        final BufferArray array = BufferArray.create();

        if (position == 0 && limit == capacity) {
            for (int i = 0; i < buffersSize; i++) {
                buffers[i].toBufferArray(array);
            }

            return array;
        } else {
            return toBufferArray(array, position, limit);
        }
    }

    @Override
    public final BufferArray toBufferArray(final BufferArray array,
            final int position, final int limit) {

        if (position < 0 || position > capacity || limit < 0 || limit > capacity)
            throw new IndexOutOfBoundsException("position=" + position + " limit=" + limit + "on " + toString());

        if (buffersSize == 0 || (position == limit)) {
            return array;
        } else if (buffersSize == 1) {
            final Buffer b = buffers[0];
            final int startPos = b.position();
            return b.toBufferArray(array, position + startPos,
                    limit + startPos);
        } else if (position == 0 && limit == capacity) {
            for (int i = 0; i < buffersSize; i++) {
                buffers[i].toBufferArray(array);
            }

            return array;
        }

        checkIndex(position);
        final int pos1 = lastSegmentIndex;
        final int bufPosition = toActiveBufferPos(position);

        checkIndex(limit - 1);
        final int pos2 = lastSegmentIndex;
        final int bufLimit = toActiveBufferPos(limit);

        //noinspection ConstantConditions
        if (pos1 == pos2) {
            final Buffer buffer = buffers[pos1];
            return buffer.toBufferArray(array, bufPosition, bufLimit);
        }

        final Buffer startBuffer = buffers[pos1];
        startBuffer.toBufferArray(array,
                bufPosition, startBuffer.limit());

        for(int i = pos1 + 1; i < pos2; i++) {
            final Buffer srcBuffer = buffers[i];
            srcBuffer.toBufferArray(array);
        }

        final Buffer endBuffer = buffers[pos2];
        endBuffer.toBufferArray(array,
                endBuffer.position(), bufLimit);

        return array;
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

    @Override
    public boolean hasArray() {
        return false;
    }

    @Override
    public byte[] array() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int arrayOffset() {
        throw new UnsupportedOperationException();
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
            h = 31 * h + (int) get(i);
        h = 31 * h + mark;
        return h;
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
    
    // --------------------------------------------------------- Private Methods


    private void fillByteBuffer(final ByteBuffer bb, final ByteBufferArray array) {
        final ByteBuffer[] bbs = array.getArray();
        final int size = array.size();

        for (int i = 0; i < size; i++) {
            final ByteBuffer srcByteBuffer = bbs[i];
            bb.put(srcByteBuffer);
        }
    }

    private void removeAndDisposeBuffers() {
        boolean isNulled = false;

        if (allowInternalBuffersDispose) {
            if (disposeOrder != DisposeOrder.FIRST_TO_LAST) {
                for (int i = buffersSize - 1; i >= 0; i--) {
                    final Buffer buffer = buffers[i];
                    buffer.tryDispose();
                    buffers[i] = null;
                }
            } else {
                for (int i = 0; i < buffersSize; i++) {
                    final Buffer buffer = buffers[i];
                    buffer.tryDispose();
                    buffers[i] = null;
                }
            }

            isNulled = true;
        }

        position = 0;
        limit = 0;
        capacity = 0;
        mark = -1;
        if (!isNulled) {
            Arrays.fill(buffers, 0, buffersSize, null);
        }

        buffersSize = 0;
        disposeOrder = DisposeOrder.LAST_TO_FIRST;
        allowBufferDispose = false;
        allowInternalBuffersDispose = true;
        resetLastLocation();
    }

    private void setPosLim(final int position, final int limit) {
        if (position > limit) {
            throw new IllegalArgumentException("Position exceeds a limit: " + position + '>' + limit);
        }

        this.position = position;
        this.limit = limit;
    }

    private void checkDispose() {
        if (isDisposed) {
            throw new IllegalStateException(
                    "CompositeBuffer has already been disposed",
                    disposeStackTrace);
        }
    }

    private void checkReadOnly() {
        if (isReadOnly) throw new IllegalStateException("Buffer is in read-only mode");
    }

    private void refreshBuffers() {
        int currentCapacity = 0;
        for(int i = 0; i < buffersSize; i++) {
            final Buffer buffer = buffers[i];
            currentCapacity += buffer.remaining();
            bufferBounds[i] = currentCapacity;
            buffer.order(byteOrder);
        }

        capacity = currentCapacity;
    }

    private void resetLastLocation() {
        lowerBound = 0;
        upperBound = 0;
        activeBuffer = null;
    }

    private long makeLongL(int index) {
        index += 7;
        checkIndex(index);
        final byte b1 = activeBuffer.get(toActiveBufferPos(index));
        checkIndex(--index);
        final byte b2 = activeBuffer.get(toActiveBufferPos(index));
        checkIndex(--index);
        final byte b3 = activeBuffer.get(toActiveBufferPos(index));
        checkIndex(--index);
        final byte b4 = activeBuffer.get(toActiveBufferPos(index));
        checkIndex(--index);
        final byte b5 = activeBuffer.get(toActiveBufferPos(index));
        checkIndex(--index);
        final byte b6 = activeBuffer.get(toActiveBufferPos(index));
        checkIndex(--index);
        final byte b7 = activeBuffer.get(toActiveBufferPos(index));
        checkIndex(--index);
        final byte b8 = activeBuffer.get(toActiveBufferPos(index));
        return Bits.makeLong(b1, b2, b3, b4, b5, b6, b7, b8);
    }

    private long makeLongB(int index) {
        final byte b1 = activeBuffer.get(toActiveBufferPos(index));
        checkIndex(++index);
        final byte b2 = activeBuffer.get(toActiveBufferPos(index));
        checkIndex(++index);
        final byte b3 = activeBuffer.get(toActiveBufferPos(index));
        checkIndex(++index);
        final byte b4 = activeBuffer.get(toActiveBufferPos(index));
        checkIndex(++index);
        final byte b5 = activeBuffer.get(toActiveBufferPos(index));
        checkIndex(++index);
        final byte b6 = activeBuffer.get(toActiveBufferPos(index));
        checkIndex(++index);
        final byte b7 = activeBuffer.get(toActiveBufferPos(index));
        checkIndex(++index);
        final byte b8 = activeBuffer.get(toActiveBufferPos(index));
        return Bits.makeLong(b1, b2, b3, b4, b5, b6, b7, b8);
    }

    private void putLongL(int index, long value) {
        index += 7;
        checkIndex(index);
        activeBuffer.put(toActiveBufferPos(index), Bits.long7(value));
        checkIndex(--index);
        activeBuffer.put(toActiveBufferPos(index), Bits.long6(value));
        checkIndex(--index);
        activeBuffer.put(toActiveBufferPos(index), Bits.long5(value));
        checkIndex(--index);
        activeBuffer.put(toActiveBufferPos(index), Bits.long4(value));
        checkIndex(--index);
        activeBuffer.put(toActiveBufferPos(index), Bits.long3(value));
        checkIndex(--index);
        activeBuffer.put(toActiveBufferPos(index), Bits.long2(value));
        checkIndex(--index);
        activeBuffer.put(toActiveBufferPos(index), Bits.long1(value));
        checkIndex(--index);
        activeBuffer.put(toActiveBufferPos(index), Bits.long0(value));
    }

    private void putLongB(int index, long value) {
        activeBuffer.put(toActiveBufferPos(index), Bits.long7(value));
        checkIndex(++index);
        activeBuffer.put(toActiveBufferPos(index), Bits.long6(value));
        checkIndex(++index);
        activeBuffer.put(toActiveBufferPos(index), Bits.long5(value));
        checkIndex(++index);
        activeBuffer.put(toActiveBufferPos(index), Bits.long4(value));
        checkIndex(++index);
        activeBuffer.put(toActiveBufferPos(index), Bits.long3(value));
        checkIndex(++index);
        activeBuffer.put(toActiveBufferPos(index), Bits.long2(value));
        checkIndex(++index);
        activeBuffer.put(toActiveBufferPos(index), Bits.long1(value));
        checkIndex(++index);
        activeBuffer.put(toActiveBufferPos(index), Bits.long0(value));
    }

    private void putIntL(int index, int value) {
        index += 3;
        checkIndex(index);
        activeBuffer.put(toActiveBufferPos(index), Bits.int3(value));
        checkIndex(--index);
        activeBuffer.put(toActiveBufferPos(index), Bits.int2(value));
        checkIndex(--index);
        activeBuffer.put(toActiveBufferPos(index), Bits.int1(value));
        checkIndex(--index);
        activeBuffer.put(toActiveBufferPos(index), Bits.int0(value));
    }

    private void putIntB(int index, int value) {
        activeBuffer.put(toActiveBufferPos(index), Bits.int3(value));
        checkIndex(++index);
        activeBuffer.put(toActiveBufferPos(index), Bits.int2(value));
        checkIndex(++index);
        activeBuffer.put(toActiveBufferPos(index), Bits.int1(value));
        checkIndex(++index);
        activeBuffer.put(toActiveBufferPos(index), Bits.int0(value));
    }

    private int makeIntL(int index) {
        index += 3;
        checkIndex(index);
        final byte b1 = activeBuffer.get(toActiveBufferPos(index));
        checkIndex(--index);
        final byte b2 = activeBuffer.get(toActiveBufferPos(index));
        checkIndex(--index);
        final byte b3 = activeBuffer.get(toActiveBufferPos(index));
        checkIndex(--index);
        final byte b4 = activeBuffer.get(toActiveBufferPos(index));
        return Bits.makeInt(b1, b2, b3, b4);
    }

    private int makeIntB(int index) {
        final byte b1 = activeBuffer.get(toActiveBufferPos(index));
        checkIndex(++index);
        final byte b2 = activeBuffer.get(toActiveBufferPos(index));
        checkIndex(++index);
        final byte b3 = activeBuffer.get(toActiveBufferPos(index));
        checkIndex(++index);
        final byte b4 = activeBuffer.get(toActiveBufferPos(index));
        return Bits.makeInt(b1, b2, b3, b4);
    }

    private void putShortL(int index, short value) {
        checkIndex(++index);
        activeBuffer.put(toActiveBufferPos(index), Bits.short0(value));
        checkIndex(--index);
        activeBuffer.put(toActiveBufferPos(index), Bits.short1(value));
    }

    private void putShortB(int index, short value) {
        activeBuffer.put(toActiveBufferPos(index), Bits.short1(value));
        checkIndex(++index);
        activeBuffer.put(toActiveBufferPos(index), Bits.short0(value));
    }

    private short makeShortL(int index) {
        final byte b2 = activeBuffer.get(toActiveBufferPos(index));
        checkIndex(++index);
        final byte b1 = activeBuffer.get(toActiveBufferPos(index));
        return Bits.makeShort(b2, b1);
    }

    private short makeShortB(int index) {
        final byte b1 = activeBuffer.get(toActiveBufferPos(index));
        checkIndex(++index);
        final byte b2 = activeBuffer.get(toActiveBufferPos(index));
        return Bits.makeShort(b1, b2);
    }

      private void putCharL(int index, char value) {
        checkIndex(++index);
        activeBuffer.put(toActiveBufferPos(index), Bits.char0(value));
        checkIndex(--index);
        activeBuffer.put(toActiveBufferPos(index), Bits.char1(value));
    }

    private void putCharB(int index, char value) {
        activeBuffer.put(toActiveBufferPos(index), Bits.char1(value));
        checkIndex(++index);
        activeBuffer.put(toActiveBufferPos(index), Bits.char0(value));
    }

     private char makeCharL(int index) {
        final byte b2 = activeBuffer.get(toActiveBufferPos(index));
        checkIndex(++index);
        final byte b1 = activeBuffer.get(toActiveBufferPos(index));
        return Bits.makeChar(b2, b1);
    }

    private char makeCharB(int index) {
        final byte b1 = activeBuffer.get(toActiveBufferPos(index));
        checkIndex(++index);
        final byte b2 = activeBuffer.get(toActiveBufferPos(index));
        return Bits.makeChar(b1, b2);
    }
    
    // ---------------------------------------------------------- Nested Classes

    private static class DebugLogic {
        static void doDebug(BuffersBuffer buffersBuffer) {
            buffersBuffer.disposeStackTrace = new Exception("BuffersBuffer was disposed from: ");
        }
    }
}
