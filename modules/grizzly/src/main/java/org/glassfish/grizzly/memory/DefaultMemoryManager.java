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

import org.glassfish.grizzly.Cacheable;
import org.glassfish.grizzly.ThreadCache;
import org.glassfish.grizzly.monitoring.jmx.JmxObject;
import java.nio.ByteBuffer;
import org.glassfish.grizzly.threadpool.DefaultWorkerThread;
import java.util.Arrays;

/**
 * Default {@link MemoryManager}, used in Grizzly.
 * <tt>DefaultMemory</tt> has simple {@link org.glassfish.grizzly.Buffer} pooling implementation,
 * which makes released {@link org.glassfish.grizzly.Buffer}'s memory to be reused.
 *
 * @author Alexey Stashok
 */
public final class DefaultMemoryManager extends ByteBufferManager {
//    private static final ThreadCache.CachedTypeIndex<   TrimAwareWrapper> CACHE_IDX =
//            ThreadCache.obtainIndex(TrimAwareWrapper.class, 2);
//
//    private final ThreadCache.CachedTypeIndex<SmallBuffer> SMALL_BUFFER_CACHE_IDX =
//            ThreadCache.obtainIndex(SmallBuffer.class.getName() + "." +
//            System.identityHashCode(this), SmallBuffer.class, 16);
//
//    private static TrimAwareWrapper createTrimAwareBuffer(
//            ByteBufferManager memoryManager,
//            ByteBuffer underlyingByteBuffer) {
//
//        final TrimAwareWrapper buffer = ThreadCache.takeFromCache(CACHE_IDX);
//        if (buffer != null) {
//            buffer.visible = underlyingByteBuffer;
//            return buffer;
//        }
//
//        return new TrimAwareWrapper(memoryManager, underlyingByteBuffer);
//    }
//
//    public static final int DEFAULT_MAX_BUFFER_SIZE = 1024 * 128;
//
//    public static final int DEFAULT_SMALL_BUFFER_SIZE = 32;
//
//    /**
//     * Max size of memory pool for one thread.
//     */
//    private final int maxThreadBufferSize;
//
//    private final int smallBufferSize;
//
//    public DefaultMemoryManager() {
//        this(DEFAULT_MAX_BUFFER_SIZE, DEFAULT_SMALL_BUFFER_SIZE);
//    }
//
//    public DefaultMemoryManager(final int maxThreadBufferSize, final int smallBufferSize) {
//        this.maxThreadBufferSize = maxThreadBufferSize;
//        this.smallBufferSize = smallBufferSize;
//    }
//
//    /**
//     * Get the maximum size of memory pool for one thread.
//     *
//     * @return the maximum size of memory pool for one thread.
//     */
//    public int getMaxThreadBufferSize() {
//        return maxThreadBufferSize;
//    }
//
//    /**
//     * {@inheritDoc}
//     */
//    @Override
//    public ByteBufferWrapper allocate(final int size) {
//        if (size <= smallBufferSize) {
//            final ByteBufferWrapper buffer = createSmallBuffer();
//            buffer.limit(size);
//            return buffer;
//        }
//
//        return super.allocate(size);
//    }
//
//    /**
//     * Allocates {@link ByteBuffer} of required size.
//     * First of all <tt>DefaultMemoryManager</tt> tries to reuse thread local
//     * memory pool. If it's not possible - it delegates allocation to
//     * {@link ByteBufferViewManager}.
//     *
//     * @param size number of bytes to be allocated.
//     *
//     * @return allocated {@link ByteBuffer}.
//     */
//    @Override
//    public ByteBuffer allocateByteBuffer(int size) {
//        if (size > maxThreadBufferSize) {
//            // Don't use pool
//            return super.allocateByteBuffer(size);
//        }
//
//        ThreadLocalPool threadLocalCache = getThreadLocalPool();
//        if (threadLocalCache != null) {
//
//            if (!threadLocalCache.hasRemaining()) {
//                threadLocalCache = reallocatePoolBuffer();
//                return allocateFromPool(threadLocalCache, size);
//            }
//
//            final ByteBuffer allocatedFromPool =
//                    allocateFromPool(threadLocalCache, size);
//
//            if (allocatedFromPool != null) {
//                return allocatedFromPool;
//            } else {
//                threadLocalCache = reallocatePoolBuffer();
//                return allocateFromPool(threadLocalCache, size);
//            }
//
//        } else {
//            return super.allocateByteBuffer(size);
//        }
//    }
//
//    private SmallBuffer createSmallBuffer() {
//
//        final SmallBuffer buffer = ThreadCache.takeFromCache(SMALL_BUFFER_CACHE_IDX);
//        if (buffer != null) {
//            ProbeNotifier.notifyBufferAllocatedFromPool(monitoringConfig,
//                    smallBufferSize);
//            return buffer;
//        }
//
//        return new SmallBuffer(allocateByteBuffer0(smallBufferSize));
//    }
//
//    private ThreadLocalPool reallocatePoolBuffer() {
//        final ByteBuffer byteBuffer =
//                super.allocateByteBuffer(maxThreadBufferSize);
//
//        final ThreadLocalPool threadLocalCache = getThreadLocalPool();
//        threadLocalCache.reset(byteBuffer);
//
//        return threadLocalCache;
//    }
//
//    /**
//     * Reallocate {@link ByteBuffer} to a required size.
//     * First of all <tt>DefaultMemoryManager</tt> tries to reuse thread local
//     * memory pool. If it's not possible - it delegates reallocation to
//     * {@link ByteBufferViewManager}.
//     *
//     * @param oldByteBuffer old {@link ByteBuffer} we want to reallocate.
//     * @param newSize required size.
//     *
//     * @return reallocated {@link ByteBuffer}.
//     */
//    @Override
//    public ByteBuffer reallocateByteBuffer(ByteBuffer oldByteBuffer, int newSize) {
//        if (oldByteBuffer.capacity() >= newSize) return oldByteBuffer;
//
//        final ThreadLocalPool<ByteBuffer> memoryPool = getThreadLocalPool();
//        if (memoryPool != null) {
//            final ByteBuffer newBuffer =
//                    memoryPool.reallocate(oldByteBuffer, newSize);
//
//            if (newBuffer != null) {
//                ProbeNotifier.notifyBufferAllocatedFromPool(monitoringConfig,
//                        newSize - oldByteBuffer.capacity());
//
//                return newBuffer;
//            }
//        }
//
//        return super.reallocateByteBuffer(oldByteBuffer, newSize);
//    }
//
//
//    private ByteBuffer allocateFromPool(final ThreadLocalPool<ByteBuffer> threadLocalCache,
//            final int size) {
//        if (threadLocalCache.remaining() >= size) {
//            ProbeNotifier.notifyBufferAllocatedFromPool(monitoringConfig, size);
//
//            return threadLocalCache.allocate(size);
//        }
//
//        return null;
//    }
//
//
//    /**
//     * Release {@link ByteBuffer}.
//     * <tt>DefaultMemoryManager</tt> will checks if it's possible to return
//     * the buffer to thread local pool. If not - let's garbage collector utilize
//     * the memory.
//     *
//     * @param byteBuffer {@link ByteBuffer} to be released.
//     */
//    @Override
//    public void releaseByteBuffer(ByteBuffer byteBuffer) {
//        ThreadLocalPool memoryPool = getThreadLocalPool();
//        if (memoryPool != null) {
//
//            if (memoryPool.release((ByteBuffer) byteBuffer.clear())) {
//                ProbeNotifier.notifyBufferReleasedToPool(monitoringConfig,
//                        byteBuffer.capacity());
//
//                return;
//            }
//        }
//
//        super.releaseByteBuffer(byteBuffer);
//    }
//
//
//
//    /**
//     * Get the size of local thread memory pool.
//     *
//     * @return the size of local thread memory pool.
//     */
//    public int getReadyThreadBufferSize() {
//       ThreadLocalPool threadLocalPool = getThreadLocalPool();
//        if (threadLocalPool != null) {
//            return threadLocalPool.remaining();
//        }
//
//        return 0;
//    }
//
//
//    @Override
//    public ByteBufferWrapper wrap(ByteBuffer byteBuffer) {
//        return createTrimAwareBuffer(this, byteBuffer);
//    }
//
//
//    /**
//     * Create the Memory Manager JMX management object.
//     *
//     * @return the Memory Manager JMX management object.
//     */
//    @Override
//    protected JmxObject createJmxManagementObject() {
//        return new org.glassfish.grizzly.memory.jmx.DefaultMemoryManager(this);
//    }
//
////    /**
////     * Get thread associated buffer pool.
////     *
////     * @return thread associated buffer pool.  This method may return
////     *  <code>null</code> if the current thread doesn't have a buffer pool
////     *  associated with it.
////     */
////    private static ThreadLocalPool<ByteBuffer> getThreadLocalPool() {
////        final Thread t = Thread.currentThread();
////        if (t instanceof DefaultWorkerThread) {
////            return (ThreadLocalPool<ByteBuffer>) ((DefaultWorkerThread) t).getMemoryPool();
////        } else {
////            return null;
////        }
////    }
//
//
//
//    /**
//     * {@link ByteBufferWrapper} implementation, which supports trimming. In
//     * other words it's possible to return unused {@link org.glassfish.grizzly.Buffer} space to
//     * pool.
//     */
//    private static final class TrimAwareWrapper extends ByteBufferWrapper
//            implements Cacheable {
//
//        private TrimAwareWrapper(ByteBufferManager memoryManager,
//                ByteBuffer underlyingByteBuffer) {
//            super(memoryManager, underlyingByteBuffer);
//        }
//
//        @Override
//        public void trim() {
//            final int sizeToReturn = visible.capacity() - visible.position();
//
//
//            if (sizeToReturn > 0) {
//                final ThreadLocalPool threadLocalCache = getThreadLocalPool();
//                if (threadLocalCache != null) {
//
//                    if (threadLocalCache.isLastAllocated(visible)) {
//                        visible.flip();
//
//                        visible = visible.slice();
//                        threadLocalCache.reduceLastAllocated(visible);
//
//                        return;
//                    } else if (threadLocalCache.wantReset(sizeToReturn)) {
//                        visible.flip();
//
//                        final ByteBuffer originalByteBuffer = visible;
//                        visible = visible.slice();
//                        originalByteBuffer.position(originalByteBuffer.limit());
//                        originalByteBuffer.limit(originalByteBuffer.capacity());
//
//                        threadLocalCache.tryReset(originalByteBuffer);
//                        return;
//                    }
//                }
//            }
//
//            super.trim();
//        }
//
//        @Override
//        public void recycle() {
//            allowBufferDispose = false;
//            disposeStackTrace = null;
//
//            ThreadCache.putToCache(CACHE_IDX, this);
//        }
//
//        @Override
//        public void dispose() {
//            super.dispose();
//            recycle();
//        }
//    }
//
//    /**
//     * {@link ByteBufferWrapper} implementation, which supports trimming. In
//     * other words it's possible to return unused {@link org.glassfish.grizzly.Buffer} space to
//     * pool.
//     */
//    private final class SmallBuffer extends ByteBufferWrapper
//            implements Cacheable {
//
//        private SmallBuffer(ByteBuffer underlyingByteBuffer) {
//            super(DefaultMemoryManager.this, underlyingByteBuffer);
//        }
//
//        @Override
//        public void dispose() {
//            super.prepareDispose();
//            visible.clear();
//            recycle();
//        }
//
//        @Override
//        public void recycle() {
//            if (visible.remaining() == smallBufferSize) {
//                allowBufferDispose = false;
//                disposeStackTrace = null;
//
//                if (ThreadCache.putToCache(SMALL_BUFFER_CACHE_IDX, this)) {
//                    ProbeNotifier.notifyBufferReleasedToPool(monitoringConfig,
//                            smallBufferSize);
//                }
//            }
//        }
//    }

}
