/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012-2016 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.nio;

import java.lang.ref.SoftReference;
import java.nio.ByteBuffer;
import java.util.Arrays;
import org.glassfish.grizzly.ThreadCache;
import org.glassfish.grizzly.memory.Buffers;

/**
 * Thread-local Direct {@link ByteBuffer} storage.
 *
 * @author Alexey Stashok
 */
public final class DirectByteBufferRecord {

    private static final ThreadCache.CachedTypeIndex<DirectByteBufferRecord> CACHE_IDX =
            ThreadCache.obtainIndex("direct-buffer-cache", DirectByteBufferRecord.class, 1);

    public static DirectByteBufferRecord get() {
        final DirectByteBufferRecord record =
                ThreadCache.getFromCache(CACHE_IDX);
        if (record != null) {
            return record;
        }
        final DirectByteBufferRecord recordLocal = new DirectByteBufferRecord();
        ThreadCache.putToCache(CACHE_IDX, recordLocal);
        return recordLocal;
    }
    
    
    private ByteBuffer directBuffer;
    private int sliceOffset;
    private ByteBuffer directBufferSlice;
    private SoftReference<ByteBuffer> softRef;
    private ByteBuffer array[];
    private int arraySize;

    DirectByteBufferRecord() {
        array = new ByteBuffer[8];
    }

    public ByteBuffer getDirectBuffer() {
        return directBuffer;
    }

    public ByteBuffer getDirectBufferSlice() {
        return directBufferSlice;
    }

    public ByteBuffer allocate(final int size) {
        ByteBuffer byteBuffer;
        if ((byteBuffer = switchToStrong()) != null && byteBuffer.remaining() >= size) {
            return byteBuffer;
        } else {
            byteBuffer = ByteBuffer.allocateDirect(size);
            reset(byteBuffer);
            return byteBuffer;
        }
    }
        
    public ByteBuffer sliceBuffer() {
        int oldLim = directBuffer.limit();
        Buffers.setPositionLimit(directBuffer, sliceOffset, directBuffer.capacity());
        directBufferSlice = directBuffer.slice();
        Buffers.setPositionLimit(directBuffer, 0, oldLim);        
        return directBufferSlice;
    }

    public void finishBufferSlice() {
        if (directBufferSlice != null) {
            directBufferSlice.flip();
            final int sliceSz = directBufferSlice.remaining();
            sliceOffset += sliceSz;

            if (sliceSz > 0) {
                putToArray(directBufferSlice);
            }

            directBufferSlice = null;
        }
    }

    public ByteBuffer[] getArray() {
        return array;
    }

    public int getArraySize() {
        return arraySize;
    }

    public void putToArray(ByteBuffer byteBuffer) {
        ensureArraySize();
        array[arraySize++] = byteBuffer;
    }

    public void release() {
        if (directBuffer != null) {
            directBuffer.clear();
            switchToSoft();
        }
        
        Arrays.fill(array, 0, arraySize, null);
        arraySize = 0;
        directBufferSlice = null;
        sliceOffset = 0;
    }

    private ByteBuffer switchToStrong() {
        if (directBuffer == null && softRef != null) {
            directBuffer = directBufferSlice = softRef.get();
        }
        return directBuffer;
    }

    private void switchToSoft() {
        if (directBuffer != null && softRef == null) {
            softRef = new SoftReference<ByteBuffer>(directBuffer);
        }
        directBuffer = null;
    }

    private void reset(ByteBuffer byteBuffer) {
        directBuffer = directBufferSlice = byteBuffer;
        softRef = null;
    }

    private void ensureArraySize() {
        if (arraySize == array.length) {
            array = Arrays.copyOf(array, (arraySize * 3) / 2 + 1);
        }
    }
}
