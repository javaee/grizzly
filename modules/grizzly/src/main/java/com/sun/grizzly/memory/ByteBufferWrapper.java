/*
 *
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2007-2008 Sun Microsystems, Inc. All rights reserved.
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
 *
 */

package com.sun.grizzly.memory;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import com.sun.grizzly.Buffer;

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
public class ByteBufferWrapper implements Buffer<ByteBuffer> {
    public static boolean DEBUG_MODE = false;

    protected final ByteBufferManager memoryManager;
    
    protected ByteBuffer visible;

    private Exception disposeStackTrace;

    protected ByteBufferWrapper() {
        this(null, null);
    }

    public ByteBufferWrapper(ByteBufferManager memoryManager,
            ByteBuffer underlyingByteBuffer) {
        this.memoryManager = memoryManager;
        visible = underlyingByteBuffer;
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
    public void dispose() {
        checkDispose();
        memoryManager.release(this);

        visible = null;
        
        if (DEBUG_MODE) {
            disposeStackTrace = new Exception("ByteBufferWrapper was disposed from: ");
        }
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
        return visible.position();
    }

    @Override
    public ByteBufferWrapper position(int newPosition) {
        visible.position(newPosition);
        return this;
    }

    @Override
    public int limit() {
        return visible.limit();
    }

    @Override
    public ByteBufferWrapper limit(int newLimit) {
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
    public ByteBufferWrapper slice() {
        ByteBuffer slice = visible.slice();
        return memoryManager.wrap(slice);
    }

    @Override
    public ByteBufferWrapper duplicate() {
        ByteBuffer duplicate = visible.duplicate();
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
        visible.get(dst);
        return this;
    }

    @Override
    public ByteBufferWrapper get(byte[] dst, int offset, int length) {
        visible.get(dst, offset, length);
        return this;
    }

    @Override
    public ByteBufferWrapper put(Buffer src) {
        visible.put((ByteBuffer) src.underlying());
        return this;
    }

    @Override
    public ByteBufferWrapper put(byte[] src) {
        visible.put(src);
        return this;
    }

    @Override
    public ByteBufferWrapper put(byte[] src, int offset, int length) {
        visible.put(src, offset, length);
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
    public String toString() {
        StringBuilder sb = new StringBuilder("ByteBufferWrapper " + super.hashCode() + "[");
        sb.append("visible=[").append(visible).append(']');
        sb.append(']');
        return sb.toString();
    }

    @Override
    public int hashCode() {
        return visible.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof ByteBufferWrapper) {
            return visible.equals(((ByteBufferWrapper) obj).visible);
        }

        return false;
    }

    @Override
    public int compareTo(Buffer<ByteBuffer> o) {
        return visible.compareTo(o.underlying());
    }

    private void checkDispose() {
        if (visible == null) {
            throw new IllegalStateException(
                    "BufferWrapper has already been disposed",
                    disposeStackTrace) ;
        }
    }

    @Override
    public String contentAsString(Charset charset) {
        checkDispose();
        
        // Working with charset name to support JDK1.5
        String charsetName = charset.name();
        try {
            if (visible.hasArray()) {
                return new String(visible.array(),
                        visible.position() + visible.arrayOffset(),
                        visible.remaining(), charsetName);
            } else {
                int oldPosition = visible.position();
                byte[] tmpBuffer = new byte[visible.remaining()];
                visible.get(tmpBuffer);
                visible.position(oldPosition);
                return new String(tmpBuffer, charsetName);
            }
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("Unexpected exception", e);
        }
    }
}
