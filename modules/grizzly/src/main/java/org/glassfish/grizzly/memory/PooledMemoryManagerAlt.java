/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2013-2014 Oracle and/or its affiliates. All rights reserved.
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

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.locks.LockSupport;

import org.glassfish.grizzly.monitoring.DefaultMonitoringConfig;
import org.glassfish.grizzly.monitoring.MonitoringConfig;
import org.glassfish.grizzly.monitoring.MonitoringUtils;

/**
 * A {@link MemoryManager} implementation based on a series of shared memory pools containing
 * multiple fixed-length buffers.
 *
 * There are several tuning options for this {@link MemoryManager} implementation.
 * <ul>
 *     <li>The size of the buffers managed by this manager.</li>
 *     <li>The number of pools that this manager will stripe allocation requests across.</li>
 *     <li>The percentage of the heap that this manager will use when populating the pools.</li>
 * </ul>
 *
 * If no explicit configuration is provided, the following defaults will be used:
 * <ul>
 *     <li>Buffer size: 4 KiB ({@link #DEFAULT_BUFFER_SIZE}).</li>
 *     <li>Number of pools: Based on the return value of <code>Runtime.getRuntime().availableProcessors()</code>.</li>
 *     <li>Percentage of heap: 10% ({@link #DEFAULT_HEAP_USAGE_PERCENTAGE}).</li>
 * </ul>
 *
 * The main advantage of this manager over {@link org.glassfish.grizzly.memory.HeapMemoryManager} or
 * {@link org.glassfish.grizzly.memory.ByteBufferManager} is that this implementation doesn't use ThreadLocal pools
 * and as such, doesn't suffer from the memory fragmentation/reallocation cycle that can impact the ThreadLocal versions.
 *
 * @since 3.0
 */
public class PooledMemoryManagerAlt implements MemoryManager<Buffer>, WrapperAware {

    public static final int DEFAULT_BUFFER_SIZE = 4 * 1024;
    public static final float DEFAULT_HEAP_USAGE_PERCENTAGE = 0.1f;

    /**
     * Basic monitoring support.  Concrete implementations of this class need
     * only to implement the {@link #createJmxManagementObject()}  method
     * to plug into the Grizzly 2.0 JMX framework.
     */
    protected final DefaultMonitoringConfig<MemoryProbe> monitoringConfig =
            new DefaultMonitoringConfig<MemoryProbe>(MemoryProbe.class) {

                @Override
                public Object createManagementObject() {
                    return createJmxManagementObject();
                }

            };

    private final BufferPool[] pools;
    private final int bufferSize;
    //private final AtomicInteger allocDistributor = new AtomicInteger();


    // ------------------------------------------------------------ Constructors


    /**
     * Creates a new <code>PooledMemoryManager</code> using the following defaults:
     * <ul>
     *     <li>4 KiB buffer size.</li>
     *     <li>Number of pools based on <code>Runtime.getRuntime().availableProcessors()</code></li>
     *     <li>The initial allocation will use 10% of the heap.</li>
     * </ul>
     */
    public PooledMemoryManagerAlt() {
        this(DEFAULT_BUFFER_SIZE,
                Runtime.getRuntime().availableProcessors(),
                DEFAULT_HEAP_USAGE_PERCENTAGE);
    }


    /**
     * Creates a new <code>PooledMemoryManager</code> using the specified parameters for configuration.
     *
     * @param bufferSize the size of the individual buffers within the pool(s).
     * @param numberOfPools the number of pools to which allocation requests will be striped against.
     * @param percentOfHeap percentage of the heap that will be used when populating the pools.
     */
    public PooledMemoryManagerAlt(final int bufferSize,
                                  final int numberOfPools,
                                  final float percentOfHeap) {
        if (bufferSize <= 0) {
            throw new IllegalArgumentException("bufferSize must be greater than zero");
        }
        if (!isPowerOfTwo(bufferSize)) {
            throw new IllegalArgumentException("bufferSize must be a power of two");
        }
        if (numberOfPools <= 0) {
            throw new IllegalArgumentException("numberOfPools must be greater than zero");
        }
        if (percentOfHeap <= 0.0f || percentOfHeap >= 1.0f) {
            throw new IllegalArgumentException("percentOfHeap must be greater than zero and less than 1.");
        }
        this.bufferSize = bufferSize;
        final long heapSize = Runtime.getRuntime().maxMemory();
        final long memoryPerPool = (long) (heapSize * percentOfHeap / numberOfPools);
        pools = new BufferPool[numberOfPools];
        for (int i = 0; i < numberOfPools; i++) {
            pools[i] = new BufferPool(memoryPerPool, bufferSize, monitoringConfig);
        }
    }


    // ---------------------------------------------- Methods from MemoryManager


    /**
     * For this implementation, this method simply calls through to
     * {@link #allocateAtLeast(int)};
     */
    @Override
    public Buffer allocate(final int size) {
        if (size < 0) {
            throw new IllegalArgumentException("Requested allocation size must be greater than or equal to zero.");
        }
        return allocateAtLeast(size).limit(size);
    }

    /**
     * Allocates a buffer of at least the size requested.
     * <p/>
     * Keep in mind that the capacity of the buffer may be greater than the
     * allocation request.  The limit however, will be set to the specified
     * size.  The memory beyond the limit, is available for use.
     *
     * @param size the min {@link Buffer} size to be allocated.
     * @return a buffer with a limit of the specified <tt>size</tt>.
     */
    @Override
    public Buffer allocateAtLeast(final int size) {
        if (size < 0) {
            throw new IllegalArgumentException("Requested allocation size must be greater than or equal to zero.");
        }
        final Buffer b;
        if (size <= bufferSize) {
            b = allocateSingle();
        } else {
            b = allocateComposite(size);
        }
        b.clear();
        return b;
    }

    /**
     * Reallocates an existing buffer to at least the specified size.
     *
     * @param oldBuffer old {@link Buffer} to be reallocated.
     * @param newSize   new {@link Buffer} required size.
     *
     * @return potentially a new buffer of at least the specified size.
     */
    @Override
    public Buffer reallocate(final Buffer oldBuffer, final int newSize) {
        if (newSize <= bufferSize) {
            oldBuffer.limit(newSize);
            return oldBuffer;
        }
        BuffersBuffer newBuffer;
        boolean appendable = false;
        final int pos = oldBuffer.position();
        if (oldBuffer instanceof PoolBuffer) {
            newBuffer = BuffersBuffer.create(this);
            oldBuffer.position(0);
            final int cap = oldBuffer.capacity();
            if (oldBuffer.limit() != cap) {
                oldBuffer.limit(cap);
            }
            newBuffer.append(oldBuffer);
        } else {
            newBuffer = (BuffersBuffer) oldBuffer;
            appendable = newBuffer.isAppendable();
            newBuffer.setAppendable(true);
            Buffer b = newBuffer.buffers[newBuffer.buffersSize - 1];
            final int cap = b.capacity();
            if (b.limit() != cap) {
                b.limit(cap);
            }
            newBuffer.calcCapacity();
        }

        // append new buffers to existing
        // determine number of buffers we need to add
        final int totalBufferCount = estimateBufferArraySize(newSize);
        int bufferDiffCount = totalBufferCount - newBuffer.buffersSize;
        if (bufferDiffCount == 0) {
            PoolBuffer p = (PoolBuffer)
                    newBuffer.buffers[newBuffer.buffersSize - 1];
            p.limit(bufferSize - (((totalBufferCount * bufferSize)) - newSize));
            newBuffer.limit(newSize);
            newBuffer.calcCapacity();
        } else {
            BufferPool pool = getPool();
            for (int i = 0; i < bufferDiffCount; i++) {
                PoolBuffer p = pool.poll();
                if (p == null) {
                    p = pool.allocate();
                }
                p.allowBufferDispose(true);
                p.free = false;
                if (i == bufferDiffCount - 1) {
                    p.limit(bufferSize - (((totalBufferCount * bufferSize)) - newSize));
                }
                newBuffer.append(p);
            }
        }
        newBuffer.position(pos);
        newBuffer.setAppendable(appendable);
        return newBuffer;

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void release(final Buffer buffer) {
        buffer.tryDispose();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean willAllocateDirect(int size) {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MonitoringConfig<MemoryProbe> getMonitoringConfig() {
        return monitoringConfig;
    }


    // ----------------------------------------------- Methods from WrapperAware


    @Override
    public Buffer wrap(byte[] data) {
        return wrap(ByteBuffer.wrap(data));
    }

    @Override
    public Buffer wrap(byte[] data, int offset, int length) {
        return wrap(ByteBuffer.wrap(data, offset, length));
    }

    @Override
    public Buffer wrap(String s) {
        return wrap(s.getBytes(Charset.defaultCharset()));
    }

    @Override
    public Buffer wrap(String s, Charset charset) {
        return wrap(s.getBytes(charset));
    }

    @Override
    public Buffer wrap(ByteBuffer byteBuffer) {
        return new ByteBufferWrapper(byteBuffer);
    }


    // ---------------------------------------------------------- Public Methods


    public BufferPool[] getBufferPools() {
        return pools;
    }


    // ------------------------------------------------------- Protected Methods


    protected Object createJmxManagementObject() {
        
        return MonitoringUtils.loadJmxObject(
                "org.glassfish.grizzly.memory.jmx.PooledMemoryManager", this,
                PooledMemoryManager.class);
    }


    // --------------------------------------------------------- Private Methods


    @SuppressWarnings("unchecked")
    private BufferPool getPool() {
        //final int idx = ((allocDistributor.getAndIncrement() & 0x7fffffff) % pools.length);
        //return pools[idx];
        return pools[ThreadLocalRandom.current().nextInt(0, pools.length)];
    }

    private int estimateBufferArraySize(final int allocationRequest) {
        return allocationRequest / bufferSize + (allocationRequest % bufferSize != 0 ? 1 : 0);
//        return (int) Math.ceil((float) allocationRequest / (float) bufferSize);
    }

    private Buffer allocateSingle() {
        final BufferPool pool = getPool();
        PoolBuffer p = pool.poll();
        if (p == null) {
            p = pool.allocate();
        }
        p.allowBufferDispose(true);
        p.free = false;
        return p;
    }

    private Buffer allocateComposite(final int size) {
        Buffer[] buffers = new Buffer[estimateBufferArraySize(size)];
        BufferPool pool = getPool();
        for (int i = 0, len = buffers.length; i < len; i++) {
            PoolBuffer p = pool.poll();
            if (p == null) {
                p = pool.allocate();
            }
            p.free = false;
            p.allowBufferDispose(true);
            if (i == buffers.length - 1) {
                p.limit(bufferSize - (((buffers.length * bufferSize)) - size));
            }
            buffers[i] = p;
        }
        CompositeBuffer cb = CompositeBuffer.newBuffer(this, buffers);
        cb.allowInternalBuffersDispose(true);
        cb.allowBufferDispose(true);
        cb.setAppendable(false);
        return cb;
    }

    private static boolean isPowerOfTwo(final int valueToCheck) {
        return ((valueToCheck & (valueToCheck - 1)) == 0);
    }

    /*
     *   This array backed by this pool can only support
     *   2^30-1 elements instead of the usual 2^32-1.
     *   This is because we use bit 30 to store information about the read
     *   and write pointer 'wrapping' status.  Without these bits, it's
     *   difficult to tell if the array is full or empty when both read and
     *   write pointers are equal.
     *   The pool is considered full when read and write pointers
     *   refer to the same index and the aforementioned bits are equal.
     *   The same logic is applied to determine if the pool is empty, except
     *   the bits are not equal.
     */
    public static class BufferPool {

        // Apply this mask to obtain the first 30 bits of an integer
        // less the bits for wrap bits.
        private static final int MASK = 0x3FFFFFFF;

        // Apply this mask to obtain the wrap status bit.
        private static final int WRAP_BIT_MASK = 0x40000000;

        // Using an AtomicReferenceArray to ensure proper visibility of items
        // within the pool which while be shared across threads.
        private final AtomicReferenceArray<PoolBuffer> pool1, pool2;

        // Maintain two different pointers for reading/writing to reduce
        // contention.
        private final AtomicInteger pollIdx = new AtomicInteger(0);
        private final AtomicInteger offerIdx = new AtomicInteger(WRAP_BIT_MASK);

        // The max size of the pool.
        private final int maxPoolSize;

        // individual buffer size.
        private final int bufferSize;

        // MemoryProbe configuration.
        private final DefaultMonitoringConfig<MemoryProbe> monitoringConfig;


        // -------------------------------------------------------- Constructors


        BufferPool(final long totalPoolSize,
                   final int bufferSize,
                   final DefaultMonitoringConfig<MemoryProbe> monitoringConfig) {

            this.bufferSize = bufferSize;
            this.monitoringConfig = monitoringConfig;
            maxPoolSize = (int) (totalPoolSize / ((long) bufferSize));

            // poolSize must be less than or equal to 2^30 - 1.
            if (((maxPoolSize & WRAP_BIT_MASK) >> 30 != 0)) {
                throw new IllegalStateException(
                        "Cannot manage a pool larger than 2^30-1");
            }
            pool1 = new AtomicReferenceArray<PoolBuffer>(maxPoolSize);
            for (int i = 0; i < maxPoolSize; i++) {
                PoolBuffer b = allocate();
                b.allowBufferDispose(true);
                b.free = true;
                pool1.lazySet(i, b);
            }
            pool2 = new AtomicReferenceArray<PoolBuffer>(maxPoolSize);
        }


        // ------------------------------------------------------ Public Methods


        public final PoolBuffer poll() {
            int pollIdx;
            for (;;) {
                pollIdx = this.pollIdx.get();
                final int offerIdx = this.offerIdx.get();
                if (isEmpty(pollIdx, offerIdx)) {
                    return null;
                }
                
                final int nextPollIdx = nextIndex(pollIdx);
                if (this.pollIdx.compareAndSet(pollIdx, nextPollIdx)) {
                    break;
                } else {
                    LockSupport.parkNanos(1);
                }
            }
            
            final int unmaskedPollIdx = unmask(pollIdx);
            PoolBuffer pb;
            for (;;) {
                // unmask the current read value to the actual array index.
                pb = pool(pollIdx).getAndSet(unmaskedPollIdx, null);
                if (pb != null) {
                    break;
                }
                LockSupport.parkNanos(1);
            }
            
            ProbeNotifier.notifyBufferAllocatedFromPool(monitoringConfig,
                    bufferSize);
            return pb;
        }

        public final boolean offer(final PoolBuffer b) {
            if (b.owner != this) {
                return false;
            }
            
            int offerIdx;
            for (;;) {
                offerIdx = this.offerIdx.get();
                final int pollIdx = this.pollIdx.get();
                if (isFull(pollIdx, offerIdx)) {
                    return false;
                }
                final int nextOfferIndex = nextIndex(offerIdx);
                if (this.offerIdx.compareAndSet(offerIdx, nextOfferIndex)) {
                    break;
                } else {
                    LockSupport.parkNanos(1);
                }
            }
            
            for (;;) {
                // unmask the current write value to the actual array index.
                if (pool(offerIdx).compareAndSet(unmask(offerIdx), null, b)) {
                    break;
                }

                LockSupport.parkNanos(1);
            }
            
            ProbeNotifier.notifyBufferReleasedToPool(monitoringConfig,
                    bufferSize);
            
            return true;
        }

        public final int size() {
            final int curPoll = pollIdx.get();
            final int curOffer = offerIdx.get();
            return ((getWrappingBit(curPoll) == getWrappingBit(curOffer))
                    ? (unmask(curOffer) - unmask(curPoll))
                    : maxPoolSize - (unmask(curPoll) - unmask(curOffer)));
        }

        public void clear() {
            //noinspection StatementWithEmptyBody
            while (poll() != null) ;
        }

        public PoolBuffer allocate() {
            final PoolBuffer buffer = new PoolBuffer(
                    ByteBuffer.allocate(bufferSize),
                    this);
            ProbeNotifier.notifyBufferAllocated(monitoringConfig, bufferSize);
            return buffer;
        }


        // ----------------------------------------------------- Private Methods


        private static boolean isFull(final int pollIdx, final int offerIdx) {
            return ((unmask(pollIdx) == unmask(offerIdx))
                    && (getWrappingBit(pollIdx) != getWrappingBit(offerIdx)));
        }

        private static boolean isEmpty(final int pollIdx, final int offerIdx) {
            return pollIdx == offerIdx;
        }

        private AtomicReferenceArray<PoolBuffer> pool(final int idx) {
            return (idx & WRAP_BIT_MASK) == 0 ? pool1 : pool2;
        }
        
        private int nextIndex(final int currentIdx) {
            
            return unmask(currentIdx) + 1 < maxPoolSize
                    ? currentIdx + 1
                    : // set lower 30 bits to 0 and invert wrap bit
                    WRAP_BIT_MASK ^ (currentIdx & WRAP_BIT_MASK);
        }

        private static int unmask(final int val) {
            return val & MASK;
        }

        private static int getWrappingBit(final int val) {
            return val & WRAP_BIT_MASK;
        }

        @Override
        public String toString() {
            return toString(pollIdx.get(), offerIdx.get());
        }

        private String toString(final int ridx, final int widx) {
            return "BufferPool[" + Integer.toHexString(hashCode()) + "] {" +
                                "offer index=" + unmask(widx) +
                                ", offer wrap bit=" + getWrappingBit(widx) +
                                ", poll index=" + unmask(ridx) +
                                ", poll wrap bit=" + getWrappingBit(ridx) +
                                ", maxPoolSize=" + maxPoolSize +
                                '}';
        }

    } // END BufferPool


    public static class PoolBuffer extends ByteBufferWrapper {

        // The pool to which this Buffer instance will be returned.
        private final BufferPool owner;

        // When this Buffer instance resides in the pool, this flag will
        // be true.
        boolean free;

        // represents the number of 'child' buffers that have been created using
        // this as the foundation.  This source buffer can't be returned
        // to the pool unless this value is zero.
        protected final AtomicInteger shareCount;

        // represents the original buffer from the pool.  This value will be
        // non-null in any 'child' buffers created from the original.
        protected final PoolBuffer source;

        // Used for the special case of the split() method.  This maintains
        // the original wrapper from the pool which must ultimately be returned.
        private ByteBuffer origVisible;


        // ------------------------------------------------------------ Constructors


        /**
         * Creates a new PoolBuffer instance wrapping the specified
         * {@link java.nio.ByteBuffer}.
         *
         * @param underlyingByteBuffer the {@link java.nio.ByteBuffer} instance to wrap.
         * @param owner the {@link org.glassfish.grizzly.memory.PooledMemoryManager.BufferPool} that owns
         *              this <tt>PoolBuffer</tt> instance.
         */
        PoolBuffer(final ByteBuffer underlyingByteBuffer,
                   final BufferPool owner) {
            this(underlyingByteBuffer, owner, null, new AtomicInteger());
        }

        /**
         * Creates a new PoolBuffer instance wrapping the specified
         * {@link java.nio.ByteBuffer}.
         *
         * @param underlyingByteBuffer the {@link java.nio.ByteBuffer} instance to wrap.
         * @param owner                the {@link org.glassfish.grizzly.memory.PooledMemoryManager.BufferPool} that owns
         *                             this <tt>PoolBuffer</tt> instance.
         *                             May be <tt>null</tt>.
         * @param source               the <tt>PoolBuffer</tt> that is the
         *                             'parent' of this new buffer instance.  May be <tt>null</tt>.
         * @param shareCount          shared reference to an {@link java.util.concurrent.atomic.AtomicInteger} that enables
         *                             shared buffer book-keeping.
         *
         * @throws IllegalArgumentException if <tt>underlyingByteBuffer</tt> or <tt>shareCount</tt>
         *                                  are <tt>null</tt>.
         */
        private PoolBuffer(final ByteBuffer underlyingByteBuffer,
                           final BufferPool owner,
                           final PoolBuffer source,
                           final AtomicInteger shareCount) {
            super(underlyingByteBuffer);
            if (underlyingByteBuffer == null) {
                throw new IllegalArgumentException("underlyingByteBuffer cannot be null.");
            }
            if (shareCount == null) {
                throw new IllegalArgumentException("shareCount cannot be null");
            }

            this.owner = owner;
            this.shareCount = shareCount;
            this.source = source;
        }


        // ------------------------------------------ Methods from ByteBufferWrapper


        /**
         * Overrides the default behavior to only consider a buffer disposed when
         * it is no longer shared.  When invoked and this buffer isn't shared,
         * the buffer will be cleared and returned back to the pool.
         */
        @Override
        public void dispose() {
            if (free) {
                return;
            }
            free = true;
            // if shared count is greater than 0, decrement and take no further
            // action
            if (shareCount.get() != 0) {
                shareCount.decrementAndGet();
            } else {
                // if source is available and has been disposed, we can now
                // safely return source back to the queue
                if (source != null && source.free) {
                    // this block will be executed if this buffer was split.
                    if (source.origVisible != null) {
                        source.visible = source.origVisible;
                    }
                    source.visible.clear();
                    if (!source.owner.offer(source)) {
                        // queue couldn't accept the buffer, allow GC to reclaim it
                        source.visible = null;
                    }
                } else {
                    // this block executes in the simple case where the original
                    // buffer is allocated and returned to the pool without sharing
                    // the data across multiple buffer instances
                    if (owner != null) {
                        // this block will be executed if this buffer was split.
                        if (origVisible != null) {
                            visible = origVisible;
                        }
                        visible.clear();
                        if (!owner.offer(this)) {
                            // queue couldn't accept the buffer, allow GC to reclaim it
                            visible = null;
                        }
                    }
                }
            }
        }


        /**
         * Override the default implementation to check the <tt>free</tt> status
         * of this buffer (i.e., once released, operations on the buffer will no
         * longer succeed).
         */
        @Override
        protected final void checkDispose() {
            if (free) {
                throw new IllegalStateException(
                        "PoolBuffer has already been disposed",
                        disposeStackTrace);
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Buffer split(final int splitPosition) {
            checkDispose();
            final int oldPosition = position();
            final int oldLimit = limit();
            // we have to save the original ByteBuffer before we split
            // in order to restore the result prior to returning the result
            // to the pool.
            origVisible = visible;
            Buffers.setPositionLimit(visible, 0, splitPosition);
            ByteBuffer slice1 = visible.slice();
            Buffers.setPositionLimit(visible, splitPosition, visible.capacity());
            ByteBuffer slice2 = visible.slice();

            if (oldPosition < splitPosition) {
                slice1.position(oldPosition);
            } else {
                slice1.position(slice1.capacity());
                slice2.position(oldPosition - splitPosition);
            }

            if (oldLimit < splitPosition) {
                slice1.limit(oldLimit);
                slice2.limit(0);
            } else {
                slice2.limit(oldLimit - splitPosition);
            }


            this.visible = slice1;

            return wrap(slice2);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public ByteBufferWrapper asReadOnlyBuffer() {
            checkDispose();
            return wrap(visible.asReadOnlyBuffer());
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public ByteBufferWrapper duplicate() {
            checkDispose();
            return wrap(visible.duplicate());
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public ByteBufferWrapper slice() {
            checkDispose();
            return wrap(visible.slice());
        }


        // ----------------------------------------------------- Private Methods


        private ByteBufferWrapper wrap(final ByteBuffer buffer) {
            final PoolBuffer b =
                    new PoolBuffer(buffer,
                            null, // don't keep track of the owner for child buffers
                            ((source == null) ? this : source), // pass the 'parent' buffer along
                            shareCount); // pass the shareCount
            b.allowBufferDispose(true);
            b.shareCount.incrementAndGet();

            return b;
        }

    } // END PoolBuffer

}
