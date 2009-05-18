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

package org.glassfish.grizzly.memory;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicLong;
import org.glassfish.grizzly.threadpool.DefaultWorkerThread;

/**
 * Default {@link MemoryManager}, used in Grizzly.
 * <tt>DefaultMemory</tt> has simple {@link Buffer} pooling implementation,
 * which makes released {@link Buffer}'s memory to be reused.
 *
 * @author Alexey Stashok
 */
public class DefaultMemoryManager extends ByteBufferViewManager {
    public static final int DEFAULT_MAX_BUFFER_SIZE = 1024 * 128;
    
    /**
     * Max size of memory pool for one thread.
     */
    private int maxThreadBufferSize = DEFAULT_MAX_BUFFER_SIZE;

    private boolean isMonitoring;

    /**
     * Real amount of bytes, which was allocated.
     */
    private AtomicLong totalBytesAllocated = new AtomicLong();
    
    /**
     * Get the maximum size of memory pool for one thread.
     *
     * @return the maximum size of memory pool for one thread.
     */
    public int getMaxThreadBufferSize() {
        return maxThreadBufferSize;
    }

    /**
     * Set the maximum size of memory pool for one thread.
     *
     * @param maxThreadBufferSize the maximum size of memory pool for one thread.
     */
    public void setMaxThreadBufferSize(int maxThreadBufferSize) {
        this.maxThreadBufferSize = maxThreadBufferSize;
    }

    /**
     * Is monotoring enabled.
     * 
     * @return <tt>true</tt>, if monitoring is enabled, or <tt>false</tt>
     * otherwise.
     */
    public boolean isMonitoring() {
        return isMonitoring;
    }

    /**
     * Set monotoring mode.
     *
     * @param isMonitoring <tt>true</tt>, if monitoring is enabled, or
     * <tt>false</tt> otherwise.
     */
    public void setMonitoring(boolean isMonitoring) {
        this.isMonitoring = isMonitoring;
        ByteBufferWrapper.DEBUG_MODE = isMonitoring;
    }

    /**
     * Get real number of bytes allocated by this {@link MemoryManager}.
     * It doesn't count bytes, which were pooled and then reused.
     * 
     * @return real number of bytes allocated by this {@link MemoryManager}.
     */
    public long getTotalBytesAllocated() {
        return totalBytesAllocated.get();
    }

    /**
     * Allocates {@link Buffer} of required size.
     * First of all <tt>DefaultMemoryManager</tt> tries to reuse thread local
     * memory pool. If it's not possible - it delegates allocation to 
     * {@link ByteBufferViewManager}.
     * 
     * @param size number of bytes to be allocated.
     * 
     * @return allocated {@link ByteBufferWrapper}.
     */
    @Override
    public ByteBufferWrapper allocate(int size) {
        if (isDefaultWorkerThread()) {
            BufferInfo bufferInfo = getThreadBuffer();

            if (bufferInfo == null || bufferInfo.buffer == null) {
                return incAllocated(super.allocate(size));
            }

            ByteBufferWrapper allocatedFromPool = allocateFromPool(bufferInfo, size);

            if (allocatedFromPool != null) {
                return allocatedFromPool;
            } else {
                return incAllocated(super.allocate(size));
            }

        } else {
            return incAllocated(super.allocate(size));
        }
    }

    /**
     * Reallocate {@link Buffer} to a required size.
     * First of all <tt>DefaultMemoryManager</tt> tries to reuse thread local
     * memory pool. If it's not possible - it delegates reallocation to
     * {@link ByteBufferViewManager}.
     *
     * @param oldBuffer old {@link Buffer} we want to reallocate.
     * @param newSize {@link Buffer} required size.
     *
     * @return reallocated {@link Buffer}.
     */

    @Override
    public ByteBufferWrapper reallocate(ByteBufferWrapper oldBuffer,
            int newSize) {
        if (oldBuffer.capacity() <= newSize) return oldBuffer;
        
        if (isDefaultWorkerThread()) {
            final BufferInfo bufferInfo = getThreadBuffer();
            if (bufferInfo != null &&
                    bufferInfo.buffer != null &&
                    bufferInfo.lastAllocatedBuffer == oldBuffer.visible &&
                    bufferInfo.buffer.remaining() + oldBuffer.capacity() >= newSize) {
                final ByteBuffer bufferPool = bufferInfo.buffer;
                bufferPool.position(bufferPool.position() - oldBuffer.capacity());

                return allocateFromPool(bufferInfo, newSize);
            }
        }

        return incAllocated(super.reallocate(oldBuffer, newSize));
    }

    private ByteBufferWrapper allocateFromPool(final BufferInfo bufferInfo,
            final int size) {
        if (bufferInfo == null || bufferInfo.buffer == null) return null;
        final ByteBuffer bufferPool = bufferInfo.buffer;
        if (bufferPool.remaining() >= size) {
            ByteBuffer allocatedByteBuffer;

            if (bufferPool.position() == 0) {
                allocatedByteBuffer = bufferPool;
                bufferInfo.buffer = null;
            } else {
                allocatedByteBuffer = slice(bufferPool, size);
            }

            bufferInfo.lastAllocatedBuffer = allocatedByteBuffer;
            return wrap(allocatedByteBuffer);
        }

        return null;
    }


    /**
     * Release {@link Buffer}.
     * <tt>DefaultMemoryManager</tt> will checks if it's possible to return
     * the buffer to thread local pool. If not - let's garbage collector utilize
     * the memory.
     *
     * @param buffer {@link Buffer} to be released.
     */
    @Override
    public void release(ByteBufferWrapper buffer) {
        if (isDefaultWorkerThread()) {
            ByteBuffer underlyingBuffer = buffer.underlying();
            
            BufferInfo bufferInfo = getThreadBuffer();

            if (prepend(bufferInfo, underlyingBuffer)) return;

            if (bufferInfo == null || bufferInfo.buffer == null ||
                    (bufferInfo.buffer.capacity() <= underlyingBuffer.capacity() &&
                    underlyingBuffer.capacity() <= maxThreadBufferSize)) {

                boolean isNewBufferInfo = false;
                if (bufferInfo == null) {
                    bufferInfo = new BufferInfo();
                    isNewBufferInfo = true;
                }
                
                underlyingBuffer.clear();
                bufferInfo.buffer = underlyingBuffer;
                bufferInfo.lastAllocatedBuffer = null;

                if (isNewBufferInfo) {
                    setThreadBuffer(bufferInfo);
                }
                
                return;
            }

        }
        super.release(buffer);
    }

    /**
     * Get the size of local thread memory pool.
     * 
     * @return the size of local thread memory pool.
     */
    public int getReadyThreadBufferSize() {
        if (isDefaultWorkerThread()) {
            BufferInfo bi = getThreadBuffer();
            if (bi != null && bi.buffer != null) {
                return bi.buffer.remaining();
            }
        }

        return 0;
    }


    @Override
    public ByteBufferWrapper wrap(ByteBuffer byteBuffer) {
        return new TrimAwareWrapper(this, byteBuffer);
    }

    /**
     * Get thread associated memory pool info.
     * 
     * @return thread associated memory pool info.
     */
    private BufferInfo getThreadBuffer() {
        DefaultWorkerThread workerThread =
                (DefaultWorkerThread) Thread.currentThread();
        return workerThread.getAssociatedBuffer();
    }

    /**
     * Set thread associated memory pool info.
     * 
     * @param bufferInfo thread associated memory pool info.
     */
    private void setThreadBuffer(BufferInfo bufferInfo) {
        DefaultWorkerThread workerThread =
                (DefaultWorkerThread) Thread.currentThread();
        workerThread.setAssociatedBuffer(bufferInfo);
    }

    /**
     * Counts total allocated memory size.
     */
    private ByteBufferWrapper incAllocated(ByteBufferWrapper allocated) {
        if (isMonitoring) {
            totalBytesAllocated.addAndGet(allocated.capacity());
        }

        return allocated;
    }

    /**
     * If possible, prepends passed underlyingBuffer to thread local memory
     * pool.
     * 
     * @param bufferInfo thread associated memory pool info.
     * @param underlyingBuffer {@link ByteBuffer} to prepend
     * @return <tt>true</tt>, if {@link ByteBuffer} was prepended,
     * or <tt>false</tt> otherwise.
     */
    private boolean prepend(BufferInfo bufferInfo, ByteBuffer underlyingBuffer) {
        if (bufferInfo != null &&
                bufferInfo.lastAllocatedBuffer == underlyingBuffer) {
            bufferInfo.lastAllocatedBuffer = null;
            if (bufferInfo.buffer != null) {
                ByteBuffer chunk = bufferInfo.buffer;
                chunk.position(chunk.position() - underlyingBuffer.capacity());
            } else {
                bufferInfo.buffer = underlyingBuffer;
            }

            return true;

        }

        return false;
    }

    private boolean isDefaultWorkerThread() {
        return Thread.currentThread() instanceof DefaultWorkerThread;
    }

    /**
     * Information about thread associated memory pool.
     */
    public class BufferInfo {
        /**
         * Memory pool
         */
        public ByteBuffer buffer;

        /**
         * Last allocated {@link ByteBuffer}.
         */
        public ByteBuffer lastAllocatedBuffer;

        @Override
        public String toString() {
            return "(buffer=" + buffer + " lastAllocatedBuffer=" + lastAllocatedBuffer + ")";
        }


    }

    /**
     * {@link ByteBufferWrapper} implementation, which supports triming. In
     * other words it's possible to return unused {@link Buffer} space to
     * pool.
     */
    public class TrimAwareWrapper extends ByteBufferWrapper {

        public TrimAwareWrapper(ByteBufferManager memoryManager,
                ByteBuffer underlyingByteBuffer) {
            super(memoryManager, underlyingByteBuffer);
        }

        @Override
        public void trim() {
            int sizeToReturn = visible.capacity() - visible.position();

            BufferInfo bufferInfo;
            
            if (sizeToReturn > 0 && isDefaultWorkerThread() &&
                    ((bufferInfo = getThreadBuffer()) == null
                    || bufferInfo.lastAllocatedBuffer == visible)) {
                visible.flip();

                ByteBuffer originalByteBuffer = visible;
                visible = visible.slice();
                if (bufferInfo == null) {
                    originalByteBuffer.position(originalByteBuffer.limit());
                    originalByteBuffer.limit(originalByteBuffer.capacity());
                    bufferInfo = new BufferInfo();
                    bufferInfo.buffer = originalByteBuffer;
                    bufferInfo.lastAllocatedBuffer = visible;
                    setThreadBuffer(bufferInfo);

                } else if (bufferInfo.lastAllocatedBuffer == originalByteBuffer) {
                    if (bufferInfo.buffer == null) {
                        originalByteBuffer.position(originalByteBuffer.limit());
                        originalByteBuffer.limit(originalByteBuffer.capacity());
                        bufferInfo.buffer = originalByteBuffer;
                    } else {
                        bufferInfo.buffer.position(
                                bufferInfo.buffer.position() - sizeToReturn);
                    }
                    bufferInfo.lastAllocatedBuffer = visible;
                }
            } else {
                super.trim();
            }
        }
    }
}
