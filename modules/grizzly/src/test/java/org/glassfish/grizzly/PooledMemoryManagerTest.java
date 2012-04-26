/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012 Oracle and/or its affiliates. All rights reserved.
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
package org.glassfish.grizzly;

import org.glassfish.grizzly.memory.PooledMemoryManager;
import org.junit.Test;

import static org.junit.Assert.*;

public class PooledMemoryManagerTest {


    @Test
    public void testDefaultPoolInitialization() throws Exception {

        // default configuration is 10% of heap will be used for memory.
        // Number of memory pools that will host this memory is based on the
        // number of processors available to the runtime.
        final int numProcessors = Runtime.getRuntime().availableProcessors();
        final long memoryPerPool = (long) (Runtime.getRuntime().maxMemory()
                * PooledMemoryManager.DEFAULT_HEAP_USAGE_PERCENTAGE
                    / numProcessors);
        final long totalMemory = memoryPerPool * numProcessors;

        // the total consumed memory should be equal to or greater than 'totalMemory'
        PooledMemoryManager mm = new PooledMemoryManager();
        PooledMemoryManager.BufferPool[] pools = mm.getBufferPools();
        assertEquals(numProcessors, pools.length);
        long consumedMemory = 0;
        for (int i = 0, len = pools.length; i < len; i++) {
            consumedMemory += pools[i].size() * 4096;
        }

        assertTrue(consumedMemory >= totalMemory);

        // finally, confirm the default buffer size is 4096
        assertEquals(4096, pools[0].poll().capacity());

    }

    @Test
    public void testCustomizedPoolInitialization() throws Exception {

        PooledMemoryManager mm = new PooledMemoryManager(2048, 1, 0.05f, false);
        final long memoryPerPool = (long) (Runtime.getRuntime().maxMemory() * 0.05f);
        PooledMemoryManager.BufferPool[] pools = mm.getBufferPools();

        // should only have one pool per the constructor
        assertEquals(1, pools.length);

        // consumed memory should be greater than or equal to 5% of the heap
        assertTrue(pools[0].size() * 2048 >= memoryPerPool);

        // buffer size should be 2048
        assertEquals(2048, pools[0].poll().capacity());

    }

    @Test
    public void testInvalidConstructorArguments() {

        // invalid buffer size
        try {
            new PooledMemoryManager(0, 1, PooledMemoryManager.DEFAULT_HEAP_USAGE_PERCENTAGE, false);
            fail();
        } catch (IllegalArgumentException iae) {
            // expected
        } catch (Exception e) {
            fail();
        }

        // invalid number of pools
        try {
            new PooledMemoryManager(1024, 0, PooledMemoryManager.DEFAULT_HEAP_USAGE_PERCENTAGE, false);
        } catch (IllegalArgumentException iae) {
            // expected
        } catch (Exception e) {
            fail();
        }

        // invalid heap percentage (lower bound)
        try {
            new PooledMemoryManager(1024, 1, 0, false);
        } catch (IllegalArgumentException iae) {
            // expected
        } catch (Exception e) {
            fail();
        }

        // invalid heap percentage (upper bound)
        try {
            new PooledMemoryManager(1024, 1, 1, false);
        } catch (IllegalArgumentException iae) {
            // expected
        } catch (Exception e) {
            fail();
        }
    }

    @Test
    public void testSimpleAllocationAndDispose() throws Exception {

        PooledMemoryManager mm =
                new PooledMemoryManager(PooledMemoryManager.DEFAULT_BUFFER_SIZE,
                                        1,
                                        PooledMemoryManager.DEFAULT_HEAP_USAGE_PERCENTAGE,
                                        false);

        PooledMemoryManager.BufferPool[] pools = mm.getBufferPools();
        // size before any allocations
        final int size = pools[0].size();

        // allocate a buffer and validate the configuration of said buffer
        Buffer b = mm.allocate(4096);
        assertEquals(4096, b.remaining());
        assertTrue(!b.isComposite());
        assertTrue(b.allowBufferDispose());
        assertEquals(PooledMemoryManager.DEFAULT_BUFFER_SIZE, b.capacity());

        // validate that pool size has been reduced by one.
        assertEquals(size - 1, pools[0].size());

        // dispose the buffer and validate the pool has returned to it's
        // original size
        b.tryDispose();
        assertEquals(size, pools[0].size());

    }

    @Test
    public void testSimpleCompositeAllocationAndDispose() throws Exception {

        PooledMemoryManager mm =
                new PooledMemoryManager(PooledMemoryManager.DEFAULT_BUFFER_SIZE,
                        1,
                        PooledMemoryManager.DEFAULT_HEAP_USAGE_PERCENTAGE,
                        false);

        PooledMemoryManager.BufferPool[] pools = mm.getBufferPools();
        // size before any allocations
        final int size = pools[0].size();

        // allocate a buffer and validate the configuration of said buffer
        Buffer b = mm.allocate(6000);
        assertEquals(6000, b.remaining());
        assertFalse(b.allowBufferDispose());
        assertEquals(6000, b.capacity());

        // validate that pool size has been reduced by two.
        assertEquals(size - 2, pools[0].size());

        // dispose the buffer and validate the pool has returned to it's
        // original size
        b.tryDispose();
        assertEquals(size, pools[0].size());

        // validate all buffers at this stage are of the expected capacity
        for (final PooledMemoryManager.PoolableByteBufferWrapper bb : mm.getBufferPools()[0]) {
            assertEquals(4096, bb.capacity());
        }

    }

    @Test
    public void testReallocate() throws Exception {

        PooledMemoryManager mm =
                new PooledMemoryManager(PooledMemoryManager.DEFAULT_BUFFER_SIZE,
                        1,
                        PooledMemoryManager.DEFAULT_HEAP_USAGE_PERCENTAGE,
                        false);

        // re-allocate request that is smaller than the default buffer size.
        // this should return the same buffer instance with the limit adjusted
        // downward.
        Buffer b = mm.allocate(2048);
        Buffer nb = mm.reallocate(b, 1024);
        assertEquals(1024, nb.limit());
        assertTrue(b == nb);
        b.tryDispose();
        nb.tryDispose();

        // re-allocate request that is smaller than the default buffer size.
        // this should return the same buffer instance with the limit with the
        // limit adjusted upward.
        b = mm.allocate(2048);
        nb = mm.reallocate(b, 4096);
        assertEquals(4096, nb.limit());
        assertTrue(b == nb);
        b.tryDispose();
        nb.tryDispose();

        // re-allocate request that is larger than the default buffer size.
        b = mm.allocate(2048);
        nb = mm.reallocate(b, 9999);
        assertEquals(9999, nb.limit());
        assertTrue(b != nb);
        b.tryDispose();
        nb.tryDispose();

        // re-allocate request that is larger than the default buffer size.
        b = mm.allocate(4096);
        nb = mm.reallocate(b, 4097);
        assertEquals(nb.limit(), 4097);
        assertTrue(b != nb);
        b.tryDispose();
        nb.tryDispose();

    }

    @Test
    public void testBufferTrim() throws Exception {

        PooledMemoryManager mm =
                new PooledMemoryManager(PooledMemoryManager.DEFAULT_BUFFER_SIZE,
                        1,
                        PooledMemoryManager.DEFAULT_HEAP_USAGE_PERCENTAGE,
                        false);

        PooledMemoryManager.BufferPool[] pools = mm.getBufferPools();
        // size before any allocations
        final int size = pools[0].size();
        Buffer b = mm.allocate(6666);
        assertEquals(size - 2, pools[0].size());
        b.position(1000);
        b.trim();
        assertEquals(4096, b.capacity());
        assertEquals(size - 1, pools[0].size());
        b.tryDispose();
        assertEquals(size, pools[0].size());

        b = mm.allocate(1023);
        assertEquals(size - 1, pools[0].size());
        b.trim();
        assertEquals(size - 1, pools[0].size());
        b.tryDispose();
        assertEquals(size, pools[0].size());

    }

    @Test
    public void testBufferShrink() throws Exception {

        PooledMemoryManager mm =
                new PooledMemoryManager(PooledMemoryManager.DEFAULT_BUFFER_SIZE,
                        1,
                        PooledMemoryManager.DEFAULT_HEAP_USAGE_PERCENTAGE,
                        false);

        PooledMemoryManager.BufferPool[] pools = mm.getBufferPools();
        // size before any allocations
        final int size = pools[0].size();
        Buffer b = mm.allocate(13000);
        assertEquals(size - 4, pools[0].size());
        b.position(6666);
        b.limit(7000);
        b.shrink();
        assertEquals(334, b.remaining());
        assertEquals(size - 1, pools[0].size());
        b.tryDispose();
        assertEquals(size, pools[0].size());

    }

    @Test
    public void testIllegalAllocationArgument() throws Exception {

        PooledMemoryManager mm =
                new PooledMemoryManager(PooledMemoryManager.DEFAULT_BUFFER_SIZE,
                        1,
                        PooledMemoryManager.DEFAULT_HEAP_USAGE_PERCENTAGE,
                        false);

        // allocation request must be greater than zero
        try {
            mm.allocate(-1);
            fail();
        } catch (IllegalArgumentException iae) {
            // expected
        } catch (Exception e) {
            fail();
        }

        try {
            mm.allocateAtLeast(-1);
            fail();
        } catch (IllegalArgumentException iae) {
            // expected
        } catch (Exception e) {
            fail();
        }

    }

    @Test
    public void testSingleBufferComplexDispose() {
        PooledMemoryManager mm =
                new PooledMemoryManager(PooledMemoryManager.DEFAULT_BUFFER_SIZE,
                        1,
                        PooledMemoryManager.DEFAULT_HEAP_USAGE_PERCENTAGE,
                        false);

        // === duplicate ================
        final int unusedPoolSize = mm.getBufferPools()[0].size();
        PooledMemoryManager.PoolableByteBufferWrapper b = (PooledMemoryManager.PoolableByteBufferWrapper)
                mm.allocate(4096); // allocate a single buffer
        assertEquals(unusedPoolSize - 1, mm.getBufferPools()[0].size());
        Buffer duplicate = b.duplicate();
        // pool size remains constant after duplicate
        assertEquals(unusedPoolSize - 1, mm.getBufferPools()[0].size());

        // dispose the original buffer.  It shouldn't be returned to the pool
        // as the duplicate buffer still holds a reference.
        b.tryDispose();
        assertEquals(unusedPoolSize - 1, mm.getBufferPools()[0].size());

        // now dispose the duplicate, pool should return to original size
        duplicate.tryDispose();
        assertEquals(unusedPoolSize, mm.getBufferPools()[0].size());

        // === read-only ================
        b = (PooledMemoryManager.PoolableByteBufferWrapper) mm.allocate(4096);
        assertEquals(unusedPoolSize - 1, mm.getBufferPools()[0].size());
        Buffer readOnlyBuffer = b.asReadOnlyBuffer();
        // pool size remains constant after duplicate
        assertEquals(unusedPoolSize - 1, mm.getBufferPools()[0].size());

        // dispose the original buffer.  It shouldn't be returned to the pool
        // as the duplicate buffer still holds a reference.
        b.tryDispose();
        assertEquals(unusedPoolSize - 1, mm.getBufferPools()[0].size());

        // now dispose the duplicate, pool should return to original size
        readOnlyBuffer.tryDispose();
        assertEquals(unusedPoolSize, mm.getBufferPools()[0].size());

        // === slice ====================
        b = (PooledMemoryManager.PoolableByteBufferWrapper) mm.allocate(4096);
        assertEquals(unusedPoolSize - 1, mm.getBufferPools()[0].size());
        b.position(10);
        Buffer slicedBuffer = b.asReadOnlyBuffer();
        // pool size remains constant after duplicate
        assertEquals(unusedPoolSize - 1, mm.getBufferPools()[0].size());

        // dispose the original buffer.  It shouldn't be returned to the pool
        // as the duplicate buffer still holds a reference.
        b.tryDispose();
        assertEquals(unusedPoolSize - 1, mm.getBufferPools()[0].size());

        // now dispose the duplicate, pool should return to original size
        slicedBuffer.tryDispose();
        assertEquals(unusedPoolSize, mm.getBufferPools()[0].size());

        // === split ====================
        b = (PooledMemoryManager.PoolableByteBufferWrapper) mm.allocate(4096);
        assertEquals(unusedPoolSize - 1, mm.getBufferPools()[0].size());
        Buffer splitBuffer = b.split(2048);
        // pool size remains constant after duplicate
        assertEquals(unusedPoolSize - 1, mm.getBufferPools()[0].size());

        // dispose the original buffer.  It shouldn't be returned to the pool
        // as the duplicate buffer still holds a reference.
        b.tryDispose();
        assertEquals(unusedPoolSize - 1, mm.getBufferPools()[0].size());

        // now dispose the duplicate, pool should return to original size
        splitBuffer.tryDispose();
        assertEquals(unusedPoolSize, mm.getBufferPools()[0].size());

        // split is a special case in that the visible portion of the original
        // buffer is replaced by the first half of the split result.  We need
        // to make sure that the returned result is the that first half, but
        // the full 4096.
        for (final PooledMemoryManager.PoolableByteBufferWrapper bb : mm.getBufferPools()[0]) {
            assertEquals(4096, bb.capacity());
        }

        // = time to mix it up a bit ====
        b = (PooledMemoryManager.PoolableByteBufferWrapper) mm.allocate(4096);
        assertEquals(unusedPoolSize - 1, mm.getBufferPools()[0].size());
        duplicate = b.duplicate();
        splitBuffer = duplicate.split(2048);
        readOnlyBuffer = splitBuffer.asReadOnlyBuffer();
        slicedBuffer = readOnlyBuffer.position(10).slice();

        // random disposes
        readOnlyBuffer.tryDispose();
        assertEquals(unusedPoolSize - 1, mm.getBufferPools()[0].size());
        slicedBuffer.tryDispose();
        assertEquals(unusedPoolSize - 1, mm.getBufferPools()[0].size());
        b.tryDispose();
        assertEquals(unusedPoolSize - 1, mm.getBufferPools()[0].size());
        splitBuffer.tryDispose();
        assertEquals(unusedPoolSize - 1, mm.getBufferPools()[0].size());
        duplicate.tryDispose();
        assertEquals(unusedPoolSize, mm.getBufferPools()[0].size());

        // split was performed at some point, make sure all buffers have
        // the expected capacities within the pool
        for (final PooledMemoryManager.PoolableByteBufferWrapper bb : mm.getBufferPools()[0]) {
            assertEquals(4096, bb.capacity());
        }
    }

}



