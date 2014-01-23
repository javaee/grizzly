/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2014 Oracle and/or its affiliates. All rights reserved.
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
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Arrays;
import org.glassfish.grizzly.monitoring.MonitoringConfig;
import org.glassfish.grizzly.monitoring.MonitoringUtils;

/**
 * The simple Buffer manager implementation, which works as wrapper above
 * {@link ByteBuffer}s. It's possible to work either with direct or heap
 * {@link ByteBuffer}s.
 *
 * @see MemoryManager
 * @see ByteBuffer
 * 
 * @author Jean-Francois Arcand
 * @author Alexey Stashok
 */
public class ByteBufferManager extends AbstractMemoryManager<ByteBufferWrapper> implements
        WrapperAware, ByteBufferAware {

    /**
     * TODO: Document
     */
    public static final int DEFAULT_SMALL_BUFFER_SIZE = 32;

    private static final ThreadCache.CachedTypeIndex<TrimAwareWrapper> CACHE_IDX =
            ThreadCache.obtainIndex(TrimAwareWrapper.class,
                    Integer.getInteger(ByteBufferManager.class.getName() + ".taw-cache-size", 2));
                    
    private final ThreadCache.CachedTypeIndex<SmallByteBufferWrapper> SMALL_BUFFER_CACHE_IDX =
            ThreadCache.obtainIndex(SmallByteBufferWrapper.class.getName() + '.' +
            System.identityHashCode(this), SmallByteBufferWrapper.class,
            Integer.getInteger(ByteBufferManager.class.getName() + ".sbbw-cache-size", 16));

    /**
     * Is direct ByteBuffer should be used?
     */
    protected boolean isDirect;

    protected final int maxSmallBufferSize;

    public ByteBufferManager() {
        this(false,
             DEFAULT_MAX_BUFFER_SIZE,
             DEFAULT_SMALL_BUFFER_SIZE);
    }

    public ByteBufferManager(final boolean isDirect) {
        this(isDirect,
             DEFAULT_MAX_BUFFER_SIZE,
             DEFAULT_SMALL_BUFFER_SIZE);
    }

    public ByteBufferManager(final boolean isDirect,
                             final int maxBufferSize,
                             final int maxSmallBufferSize) {
        super(maxBufferSize);
        this.maxSmallBufferSize = maxSmallBufferSize;
        this.isDirect = isDirect;
    }

    public int getMaxSmallBufferSize() {
        return maxSmallBufferSize;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ByteBufferWrapper allocate(final int size) {
        if (size <= maxSmallBufferSize) {
            final SmallByteBufferWrapper buffer = createSmallBuffer();
            buffer.limit(size);
            return buffer;
        }
        return wrap(allocateByteBuffer(size));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ByteBufferWrapper allocateAtLeast(int size) {
        if (size <= maxSmallBufferSize) {
            final SmallByteBufferWrapper buffer = createSmallBuffer();
            buffer.limit(size);
            return buffer;
        }
        return wrap(allocateByteBufferAtLeast(size));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ByteBufferWrapper reallocate(ByteBufferWrapper oldBuffer,
            int newSize) {
        return wrap(reallocateByteBuffer(oldBuffer.underlying(), newSize));
    }
    
    /**
     * Lets JVM Garbage collector to release buffer.
     */
    @Override
    public void release(ByteBufferWrapper buffer) {
        releaseByteBuffer(buffer.underlying());
    }

    /**
     * Returns <tt>true</tt>, if <tt>ByteBufferManager</tt> works with direct
     * {@link ByteBuffer}s, or <tt>false</tt> otherwise.
     * 
     * @return <tt>true</tt>, if <tt>ByteBufferManager</tt> works with direct
     * {@link ByteBuffer}s, or <tt>false</tt> otherwise.
     */
    public boolean isDirect() {
        return isDirect;
    }

    /**
     * Set <tt>true</tt>, if <tt>ByteBufferManager</tt> works with direct
     * {@link ByteBuffer}s, or <tt>false</tt> otherwise.
     *
     * @param isDirect <tt>true</tt>, if <tt>ByteBufferManager</tt> works with
     * direct {@link ByteBuffer}s, or <tt>false</tt> otherwise.
     */
    public void setDirect(boolean isDirect) {
        this.isDirect = isDirect;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean willAllocateDirect(int size) {
        return isDirect;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ByteBufferWrapper wrap(byte[] data) {
        return wrap(data, 0, data.length);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ByteBufferWrapper wrap(byte[] data, int offset, int length) {
        return wrap(ByteBuffer.wrap(data, offset, length));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ByteBufferWrapper wrap(String s) {
        return wrap(s, Charset.defaultCharset());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ByteBufferWrapper wrap(String s, Charset charset) {
        try {
            byte[] byteRepresentation = s.getBytes(charset.name());
            return wrap(ByteBuffer.wrap(byteRepresentation));
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public ThreadLocalPool createThreadLocalPool() {
        return new ByteBufferThreadLocalPool();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ByteBufferWrapper wrap(final ByteBuffer byteBuffer) {
        return createTrimAwareBuffer(byteBuffer);
    }

    /**
     * Allocates {@link ByteBuffer} of required size.
     *
     * @param size {@link ByteBuffer} size.
     * @return allocated {@link ByteBuffer}.
     */
    @Override
    @SuppressWarnings("unchecked")
    public ByteBuffer allocateByteBuffer(final int size) {
          if (size > maxBufferSize) {
            // Don't use pool
            return allocateByteBuffer0(size);
        }

        final ThreadLocalPool<ByteBuffer> threadLocalCache =
                getByteBufferThreadLocalPool();
        if (threadLocalCache != null) {
            final int remaining = threadLocalCache.remaining();

            if (remaining == 0 || remaining < size) {
                reallocatePoolBuffer();
            }

            return (ByteBuffer) allocateFromPool(threadLocalCache, size);
        } else {
            return allocateByteBuffer0(size);
        }

    }

    /**
     * Allocates {@link ByteBuffer} of required size.
     *
     * @param size {@link ByteBuffer} size.
     * @return allocated {@link ByteBuffer}.
     */
    @Override
    @SuppressWarnings("unchecked")
    public ByteBuffer allocateByteBufferAtLeast(final int size) {
          if (size > maxBufferSize) {
            // Don't use pool
            return allocateByteBuffer0(size);
        }

        final ThreadLocalPool<ByteBuffer> threadLocalCache =
                getByteBufferThreadLocalPool();
        if (threadLocalCache != null) {
            int remaining = threadLocalCache.remaining();

            if (remaining == 0 || remaining < size) {
                reallocatePoolBuffer();
                remaining = threadLocalCache.remaining();
            }

            return (ByteBuffer) allocateFromPool(threadLocalCache, remaining);
        } else {
            return allocateByteBuffer0(size);
        }
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public ByteBuffer reallocateByteBuffer(ByteBuffer oldByteBuffer, int newSize) {
        if (oldByteBuffer.capacity() >= newSize) return oldByteBuffer;

        final ThreadLocalPool<ByteBuffer> memoryPool =
                getByteBufferThreadLocalPool();
        if (memoryPool != null) {
            final ByteBuffer newBuffer =
                    memoryPool.reallocate(oldByteBuffer, newSize);

            if (newBuffer != null) {
                ProbeNotifier.notifyBufferAllocatedFromPool(monitoringConfig,
                        newSize - oldByteBuffer.capacity());

                return newBuffer;
            }
        }
        ByteBuffer newByteBuffer = allocateByteBuffer(newSize);
        oldByteBuffer.flip();
        return newByteBuffer.put(oldByteBuffer);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void releaseByteBuffer(ByteBuffer byteBuffer) {
        ThreadLocalPool<ByteBuffer> memoryPool = getByteBufferThreadLocalPool();
        if (memoryPool != null) {

            if (memoryPool.release((ByteBuffer) byteBuffer.clear())) {
                ProbeNotifier.notifyBufferReleasedToPool(monitoringConfig,
                        byteBuffer.capacity());
            }
        }

    }


    protected SmallByteBufferWrapper createSmallBuffer() {
        final SmallByteBufferWrapper buffer = ThreadCache.takeFromCache(SMALL_BUFFER_CACHE_IDX);
        if (buffer != null) {
            ProbeNotifier.notifyBufferAllocatedFromPool(monitoringConfig,
                    maxSmallBufferSize);
            return buffer;
        }

        return new SmallByteBufferWrapper(allocateByteBuffer0(maxSmallBufferSize));
    }

    // ------- Monitoring section ----------------------

    @Override
    public MonitoringConfig<MemoryProbe> getMonitoringConfig() {
        return monitoringConfig;
    }

    /**
     * Create the Memory Manager JMX management object.
     *
     * @return the Memory Manager JMX management object.
     */
    @Override
    protected Object createJmxManagementObject() {
        return MonitoringUtils.loadJmxObject(
                "org.glassfish.grizzly.memory.jmx.ByteBufferManager", this,
                ByteBufferManager.class);
    }

    protected final ByteBuffer allocateByteBuffer0(final int size) {
        
        ProbeNotifier.notifyBufferAllocated(monitoringConfig, size);
        if (isDirect) {
            return ByteBuffer.allocateDirect(size);
        } else {
            return ByteBuffer.allocate(size);
        }
    }


    private TrimAwareWrapper createTrimAwareBuffer(
            final ByteBuffer underlyingByteBuffer) {

        final TrimAwareWrapper buffer = ThreadCache.takeFromCache(CACHE_IDX);
        if (buffer != null) {
            buffer.visible = underlyingByteBuffer;
            return buffer;
        }

        return new TrimAwareWrapper(underlyingByteBuffer);
    }

    @SuppressWarnings({"unchecked"})
    private void reallocatePoolBuffer() {
        final ByteBuffer byteBuffer =
                allocateByteBuffer0(maxBufferSize);

        final ThreadLocalPool<ByteBuffer> threadLocalCache = getByteBufferThreadLocalPool();
        if (threadLocalCache != null) {
            threadLocalCache.reset(byteBuffer);
        }
    }

    @SuppressWarnings("unchecked")
    private static ByteBufferThreadLocalPool getByteBufferThreadLocalPool() {
        final ThreadLocalPool pool = getThreadLocalPool();
        return ((pool instanceof ByteBufferThreadLocalPool)
                ? (ByteBufferThreadLocalPool) pool
                : null);
    }

    // ---------------------------------------------------------- Nested Classes


    /**
     * Information about thread associated memory pool.
     */
    private static final class ByteBufferThreadLocalPool implements ThreadLocalPool<ByteBuffer> {
        /**
         * Memory pool
         */
        private ByteBuffer pool;

        /**
         * {@link ByteBuffer} allocation history.
         */
        private Object[] allocationHistory;
        private int lastAllocatedIndex;

        public ByteBufferThreadLocalPool() {
            allocationHistory = new Object[8];
        }

        @Override
        public void reset(ByteBuffer pool) {
            Arrays.fill(allocationHistory, 0, lastAllocatedIndex, null);
            lastAllocatedIndex = 0;
            this.pool = pool;
        }

        @Override
        public ByteBuffer allocate(int size) {
            final ByteBuffer allocated = Buffers.slice(pool, size);
            return addHistory(allocated);
        }

        @Override
        public ByteBuffer reallocate(ByteBuffer oldByteBuffer, int newSize) {
            if (isLastAllocated(oldByteBuffer)
                    && remaining() + oldByteBuffer.capacity() >= newSize) {

                lastAllocatedIndex--;

                pool.position(pool.position() - oldByteBuffer.capacity());
                final ByteBuffer newByteBuffer = Buffers.slice(pool, newSize);
                newByteBuffer.position(oldByteBuffer.position());

                return addHistory(newByteBuffer);
            }

            return null;
        }

        @Override
        public boolean release(ByteBuffer underlyingBuffer) {
            if (isLastAllocated(underlyingBuffer)) {
                pool.position(pool.position() - underlyingBuffer.capacity());
                allocationHistory[--lastAllocatedIndex] = null;

                return true;
            } else if (wantReset(underlyingBuffer.capacity())) {
                reset(underlyingBuffer);
                return true;
            }

            return false;
        }

        @Override
        public boolean wantReset(int size) {
            return !hasRemaining() ||
                    (lastAllocatedIndex == 0 && pool.remaining() < size);
        }

        @Override
        public boolean isLastAllocated(ByteBuffer oldByteBuffer) {
            return lastAllocatedIndex > 0 &&
                    allocationHistory[lastAllocatedIndex - 1] == oldByteBuffer;
        }

        @Override
        public ByteBuffer reduceLastAllocated(ByteBuffer byteBuffer) {
            final ByteBuffer oldLastAllocated =
                    (ByteBuffer) allocationHistory[lastAllocatedIndex - 1];

            pool.position(pool.position() - (oldLastAllocated.capacity() -
                    byteBuffer.capacity()));
            allocationHistory[lastAllocatedIndex - 1] = byteBuffer;

            return oldLastAllocated;
        }

        @Override
        public int remaining() {
            return pool != null ? pool.remaining() : 0;
        }

        @Override
        public boolean hasRemaining() {
            return remaining() > 0;
        }

        private ByteBuffer addHistory(ByteBuffer allocated) {
            if (lastAllocatedIndex >= allocationHistory.length) {
                allocationHistory =
                        Arrays.copyOf(allocationHistory,
                        (allocationHistory.length * 3) / 2 + 1);
            }

            allocationHistory[lastAllocatedIndex++] = allocated;
            return allocated;
        }

        @Override
        public String toString() {
            return "(pool=" + pool +
                    " last-allocated-index=" + (lastAllocatedIndex - 1) +
                    " allocation-history=" + Arrays.toString(allocationHistory)
                    + ')';
        }

    } // END ByteBufferThreadLocalPool


    /**
     * {@link ByteBufferWrapper} implementation, which supports trimming. In
     * other words it's possible to return unused {@link org.glassfish.grizzly.Buffer} space to
     * pool.
     */
    private final class TrimAwareWrapper extends ByteBufferWrapper
            implements TrimAware {

        private TrimAwareWrapper(ByteBuffer underlyingByteBuffer) {
            super(underlyingByteBuffer);
        }

        @Override
        @SuppressWarnings("unchecked")
        public void trim() {
            final int sizeToReturn = visible.capacity() - visible.position();


            if (sizeToReturn > 0) {
                final ThreadLocalPool<ByteBuffer> threadLocalCache =
                        getByteBufferThreadLocalPool();
                if (threadLocalCache != null) {

                    if (threadLocalCache.isLastAllocated(visible)) {
                        visible.flip();

                        visible = visible.slice();
                        threadLocalCache.reduceLastAllocated(visible);

                        return;
                    } else if (threadLocalCache.wantReset(sizeToReturn)) {
                        visible.flip();

                        final ByteBuffer originalByteBuffer = visible;
                        visible = visible.slice();
                        originalByteBuffer.position(originalByteBuffer.limit());
                        originalByteBuffer.limit(originalByteBuffer.capacity());

                        threadLocalCache.reset(originalByteBuffer);
                        return;
                    }
                }
            }

            super.trim();
        }

        @Override
        public void recycle() {
            allowBufferDispose = false;

            ThreadCache.putToCache(CACHE_IDX, this);
        }

        @Override
        public void dispose() {
            prepareDispose();
            ByteBufferManager.this.release(this);
            visible = null;
            recycle();
        }

        @Override
        protected ByteBufferWrapper wrapByteBuffer(ByteBuffer byteBuffer) {
            return ByteBufferManager.this.wrap(byteBuffer);
        }



    } // END TrimAwareWrapper


    /**
     * {@link ByteBufferWrapper} implementation, which supports trimming. In
     * other words it's possible to return unused {@link org.glassfish.grizzly.Buffer} space to
     * pool.
     */
    protected final class SmallByteBufferWrapper extends ByteBufferWrapper implements Cacheable {

        private SmallByteBufferWrapper(ByteBuffer underlyingByteBuffer) {
            super(underlyingByteBuffer);
        }

        @Override
        public void dispose() {
            super.prepareDispose();
            visible.clear();
            recycle();
        }

        @Override
        public void recycle() {
            if (visible.remaining() == maxSmallBufferSize) {
                allowBufferDispose = false;
                disposeStackTrace = null;

                if (ThreadCache.putToCache(SMALL_BUFFER_CACHE_IDX, this)) {
                    ProbeNotifier.notifyBufferReleasedToPool(monitoringConfig,
                            maxSmallBufferSize);
                }
            }
        }

        @Override
        protected ByteBufferWrapper wrapByteBuffer(final ByteBuffer byteBuffer) {
            return ByteBufferManager.this.wrap(byteBuffer);
        }

    } // END SmallByteBufferWrapper

}
