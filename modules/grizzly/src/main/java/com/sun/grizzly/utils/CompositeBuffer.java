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

package com.sun.grizzly.utils;

import com.sun.grizzly.Buffer;
import java.io.UnsupportedEncodingException;
import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;

/**
 *
 * @author Alexey Stashok
 */
public final class CompositeBuffer implements Buffer<List<Buffer>> {
    private boolean isDisposed;
    
    // absolute offset of first element of <tt>CompositeBuffer</tt>
    private final int initialOffset;
    
    // List of wrapped buffers
    private final List<Buffer> buffers;

    // absolute position
    private int position;

    // absolute limit
    private int limit;

    // absolute capacity
    private int capacity;

    private final boolean isReadOnly;

    // Index of buffer in the buffer list, which was used with a last get/set
    private int lastBufferIndex = -1;
    // Last buffer position, which was used with a last get/set
    private int lastBufferPosition = -1;
    // Last buffer, which was used with a last get/set
    private Buffer lastBuffer;

    private ByteOrder byteOrder = ByteOrder.nativeOrder();

    // Dispose underlying Buffer, when remove if from buffers list
    private boolean allowBufferDispose = false;

    public CompositeBuffer() {
        this(new ArrayList<Buffer>());
    }

    public CompositeBuffer(Buffer... buffers) {
        this(Arrays.asList(buffers));
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
        if (capacity == -1) {
            capacity = calcCapacity();
        }

        this.capacity = capacity;
        this.limit = capacity;
        this.isReadOnly = isReadOnly;
    }

    @Override
    public CompositeBuffer prepend(Buffer header) {
        checkDispose();
        checkReadOnly();
        
        buffers.add(0, header);
        return this;
    }

    @Override
    public void dispose() {
        checkDispose();
        isDisposed = true;
        disposeBuffers();
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
    public CompositeBuffer position(int newPosition) {
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
    public CompositeBuffer limit(int newLimit) {
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
    public CompositeBuffer mark() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public CompositeBuffer reset() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public CompositeBuffer clear() {
        checkDispose();
        setPosLim(0, capacity());
        return this;
    }

    @Override
    public CompositeBuffer flip() {
        checkDispose();
        setPosLim(0, position);
        return this;
    }

    @Override
    public CompositeBuffer rewind() {
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
    public CompositeBuffer asReadOnlyBuffer() {
        checkDispose();
        return new CompositeBuffer(this, true);
    }

    @Override
    public void trim() {
        checkDispose();
        flip();

        final long bufferLocation = locateBuffer(limit);

        if (bufferLocation == -1) throw new IllegalStateException("Bad state");

        int bufferIndex = getBufferIndex(bufferLocation);

        int size = buffers.size();
        int buffersToDispose = size - bufferIndex - 1;

        for(int i=0; i<buffersToDispose; i++) {
            Buffer buffer = buffers.remove(size - 1);
            size--;
            
            if (allowBufferDispose) {
                buffer.dispose();
            }
        }

        capacity = limit;
    }

    @Override
    public CompositeBuffer slice() {
        checkDispose();
        long posLocation = locateBuffer(position);
        long limitLocation = locateBuffer(limit);


        ArrayList<Buffer> newList = new ArrayList<Buffer>();

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
    public CompositeBuffer duplicate() {
        checkDispose();
        return new CompositeBuffer(this);
    }


    @Override
    public CompositeBuffer compact() {
        checkDispose();
        for(int i = position; i < limit; i++) {
            put(i - position, get(i));
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
    public CompositeBuffer order(ByteOrder bo) {
        checkDispose();
        this.byteOrder = bo;
        return this;
    }

    public boolean allowBufferDispose() {
        return allowBufferDispose;
    }

    public void allowBufferDispose(boolean allowBufferDispose) {
        this.allowBufferDispose = allowBufferDispose;
    }

    @Override
    public byte get() {
        checkDispose();
        prepareLastBuffer();
        position++;
        return lastBuffer.get(lastBufferPosition);
    }

    @Override
    public CompositeBuffer put(byte b) {
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
        
        long location = locateBuffer(index);
        return bufferGet(location);
    }

    @Override
    public CompositeBuffer put(int index, byte b) {
        checkDispose();
        checkReadOnly();

        long location = locateBuffer(index);
        return bufferPut(location, b);
    }

    @Override
    public CompositeBuffer get(byte[] dst) {
        checkDispose();

        return get(dst, 0, dst.length);
    }

    @Override
    public CompositeBuffer get(byte[] dst, int offset, int length) {
        checkDispose();

        if (remaining() < length) throw new BufferUnderflowException();

        prepareLastBuffer();
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
    public CompositeBuffer put(byte[] src) {
        return put(src, 0, src.length);
    }

    @Override
    public CompositeBuffer put(byte[] src, int offset, int length) {
        checkDispose();
        checkReadOnly();

        if (remaining() < length) throw new BufferOverflowException();

        while(length > 0) {
            prepareLastBuffer();

            int oldPos = lastBuffer.position();
            int bytesToCopy = Math.min(lastBuffer.remaining(), length);
            lastBuffer.put(src, offset, bytesToCopy);
            lastBuffer.position(oldPos);

            length -= bytesToCopy;
            offset += bytesToCopy;
            lastBufferPosition += bytesToCopy;
        }

        position += length;

        return this;
    }

    @Override
    public CompositeBuffer put(Buffer src) {
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
    public CompositeBuffer putChar(char value) {
        put((byte) (value >>> 8));
        put((byte) (value & 0xFF));
        
        return this;
    }

    @Override
    public char getChar(int index) {
        checkDispose();

        long location = locateBuffer(index);
        int bufferIndex = getBufferIndex(location);
        int bufferPosition = getBufferPosition(location);
        Buffer buffer = buffers.get(bufferIndex);

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
    public CompositeBuffer putChar(int index, char value) {
        checkDispose();
        checkReadOnly();

        long location = locateBuffer(index);
        int bufferIndex = getBufferIndex(location);
        int bufferPosition = getBufferPosition(location);
        Buffer buffer = buffers.get(bufferIndex);

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
        return (short) ((get() & 0xFF) << 8 | get() & 0xFF);
    }

    @Override
    public CompositeBuffer putShort(short value) {
        put((byte) (value >>> 8));
        put((byte) (value & 0xFF));

        return this;
    }

    @Override
    public short getShort(int index) {
        checkDispose();

        long location = locateBuffer(index);
        int bufferIndex = getBufferIndex(location);
        int bufferPosition = getBufferPosition(location);
        Buffer buffer = buffers.get(bufferIndex);

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
    public CompositeBuffer putShort(int index, short value) {
        checkDispose();
        checkReadOnly();

        long location = locateBuffer(index);
        int bufferIndex = getBufferIndex(location);
        int bufferPosition = getBufferPosition(location);
        Buffer buffer = buffers.get(bufferIndex);

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
        return (getShort() & 0xFFFF) << 16 | getShort() & 0xFFFF;
    }

    @Override
    public CompositeBuffer putInt(int value) {
        put((byte) ((value >>> 24) & 0xFF));
        put((byte) ((value >>> 16) & 0xFF));
        put((byte) ((value >>>  8) & 0xFF));
        put((byte) ((value >>>  0) & 0xFF));

        return this;
    }

    @Override
    public int getInt(int index) {
        checkDispose();

        long location = locateBuffer(index);
        int bufferIndex = getBufferIndex(location);
        int bufferPosition = getBufferPosition(location);
        Buffer buffer = buffers.get(bufferIndex);

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
    public CompositeBuffer putInt(int index, int value) {
        checkDispose();
        checkReadOnly();

        long location = locateBuffer(index);
        int bufferIndex = getBufferIndex(location);
        int bufferPosition = getBufferPosition(location);
        Buffer buffer = buffers.get(bufferIndex);

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
        return (getInt() & 0xFFFFFFFFL) << 32 | getInt() & 0xFFFFFFFFL;
    }

    @Override
    public CompositeBuffer putLong(long value) {
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

        long location = locateBuffer(index);
        int bufferIndex = getBufferIndex(location);
        int bufferPosition = getBufferPosition(location);
        Buffer buffer = buffers.get(bufferIndex);

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
    public CompositeBuffer putLong(int index, long value) {
        checkDispose();
        checkReadOnly();

        long location = locateBuffer(index);
        int bufferIndex = getBufferIndex(location);
        int bufferPosition = getBufferPosition(location);
        Buffer buffer = buffers.get(bufferIndex);

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
    public CompositeBuffer putFloat(float value) {
        return putInt(Float.floatToIntBits(value));
    }

    @Override
    public float getFloat(int index) {
        return Float.intBitsToFloat(getInt(index));
    }

    @Override
    public CompositeBuffer putFloat(int index, float value) {
        return putInt(index, Float.floatToIntBits(value));
    }

    @Override
    public double getDouble() {
        return Double.longBitsToDouble(getLong());
    }

    @Override
    public CompositeBuffer putDouble(double value) {
        return putLong(Double.doubleToLongBits(value));
    }

    @Override
    public double getDouble(int index) {
        return Double.longBitsToDouble(getLong(index));
    }

    @Override
    public CompositeBuffer putDouble(int index, double value) {
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

    private void disposeBuffers() {
        if (allowBufferDispose) {
            for(Buffer buffer : buffers) {
                buffer.dispose();
            }
        }

        buffers.clear();
    }

    private void resetLastBuffer() {
        lastBuffer = null;
    }   

    private void prepareLastBuffer() {
        if (lastBuffer != null) {
            if (++lastBufferPosition >= lastBuffer.limit()) {
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

    protected long locateBuffer(int offset) {
        if (buffers.isEmpty()) return -1;

        Buffer buffer = buffers.get(0);
        int currentOffset = buffer.remaining() - initialOffset;
        if (offset < currentOffset) {
            return offset + initialOffset + buffer.position();
        }

        for (int i = 1; i < buffers.size(); i++) {
            buffer = buffers.get(i);
            
            final int newOffset = currentOffset + buffer.remaining();
            if (offset < newOffset) {
                return makeLocation(i, offset - currentOffset);
            }

            currentOffset = newOffset;
        }

        throw new IndexOutOfBoundsException();
    }

    protected long incLocation(final long location) {
        int bufferIndex = getBufferIndex(location);
        int bufferPosition = getBufferPosition(location);
        Buffer buffer = buffers.get(bufferIndex);
        
        if (bufferPosition + 1 < buffer.limit()) {
            return location + 1;
        }

        for (int i = bufferIndex + 1; i < buffers.size(); i++) {
            buffer = buffers.get(bufferIndex);
            if (buffer.hasRemaining()) {
                return makeLocation(i, buffer.position());
            }
        }

        throw new IndexOutOfBoundsException();
    }

    private byte bufferGet(final long location) {
        final int bufferIndex = getBufferIndex(location);
        final int bufferPosition = getBufferPosition(location);
        final Buffer buffer = buffers.get(bufferIndex);

        return buffer.get(bufferPosition);
    }

    private CompositeBuffer bufferPut(final long location, final byte value) {
        final int bufferIndex = getBufferIndex(location);
        final int bufferPosition = getBufferPosition(location);
        final Buffer buffer = buffers.get(bufferIndex);

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
        if (buffers.isEmpty()) return 0;

        int currentCapacity = -initialOffset;
        for(int i=0; i<buffers.size(); i++) {
            currentCapacity += buffers.get(i).remaining();
        }

        if (currentCapacity < 0) {
            return 0;
        }

        return currentCapacity;
    }
}
