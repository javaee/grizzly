/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2014-2015 Oracle and/or its affiliates. All rights reserved.
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

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.grizzly.Buffer;

import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import static org.glassfish.grizzly.memory.PooledMemoryManager.*;
import static org.junit.Assert.*;

@RunWith(Parameterized.class)
public class PooledMemoryManagerTest {

    @Parameters
    public static Collection<Object[]> isDirect() {
        return Arrays.asList(new Object[][]{
                    {Boolean.FALSE},
                    {Boolean.TRUE}
                });
    }
    
    private final boolean isDirect;
    
    public PooledMemoryManagerTest(boolean isDirect) {
        this.isDirect = isDirect;
    }
    
    @Test
    public void testDefaultPoolInitialization() throws Exception {

        // default configuration will use about 10% of heap for the buffer pools.
        // Contains 3 sub-pools with sizes 4096, 16384, 65536
        // Number of slices per memory sub-pool is based on the
        // number of processors available to the runtime.
        final int numProcessors = Runtime.getRuntime().availableProcessors();
        final long maxMemory = Runtime.getRuntime().maxMemory();
        final long totalMemory = (long) (maxMemory * DEFAULT_HEAP_USAGE_PERCENTAGE);
        final long upperBound = (long) (maxMemory * .105f);
        // the total consumed memory should be equal to or greater than 'totalMemory'
        // because the pools are rounded up to the nearest multiple of 16.
        PooledMemoryManager mm = new PooledMemoryManager();
        PooledMemoryManager.Pool[] pools = mm.getPools();
        assertEquals(DEFAULT_NUMBER_OF_POOLS, pools.length);
        
        int bufSize = DEFAULT_BASE_BUFFER_SIZE;
        for (int i = 0, len = pools.length; i < len; i++) {
            assertEquals(bufSize, pools[i].getBufferSize());
            bufSize <<= DEFAULT_GROWTH_FACTOR;
        }

        PooledMemoryManager.PoolSlice[] slices = pools[0].getSlices();
        assertEquals(numProcessors, slices.length);
        
        // check the number of buffers preallocated
        assertEquals(slices[0].getMaxElementsCount(),
                slices[0].elementsCount());
        
        long consumedMemory = 0;
        for (int i = 0, len = pools.length; i < len; i++) {
            consumedMemory += pools[i].size();
        }

        assertTrue(consumedMemory + " >= " + totalMemory + " failed",
                   consumedMemory >= totalMemory);
        assertTrue(consumedMemory + " <= " + upperBound + " failed",
                   consumedMemory <= upperBound);
    }

    @Test
    public void testCustomizedPoolInitialization() throws Exception {
        final float memoryPercentage = 0.05f;
        final int processors = Runtime.getRuntime().availableProcessors();
        final long maxMemory = Runtime.getRuntime().maxMemory();
        PooledMemoryManager mm = new PooledMemoryManager(2048,
                                                               1,
                                                               0,
                                                               processors,
                                                               memoryPercentage,
                                                               1.0f,
                                                               isDirect);
        final long memoryPerPool = (long) (maxMemory * memoryPercentage);
        final long upperBound = (long) (maxMemory * 0.0505f);
        PooledMemoryManager.Pool[] pools = mm.getPools();

        // should only have one pool per the constructor
        assertEquals(1, pools.length);

        // consumed memory should be greater than or equal to 5% of the heap
        // due to rounding done within the implementation.
        assertTrue(pools[0].size() + " >= " + memoryPerPool + " failed",
                   pools[0].size() >= memoryPerPool);
        // consumed memory should not exceed the upperBound.
        assertTrue(pools[0].size() + " <= " + upperBound + " failed",
                           pools[0].size() <= upperBound);

        // buffer size should be 2048
        assertEquals(2048, pools[0].getBufferSize());
        
        final float preallocatedPercentage = 0.25f;
        
        mm = new PooledMemoryManager(2048,
                                           1,
                                           0,
                                           processors,
                                           memoryPercentage,
                                           preallocatedPercentage,
                                           isDirect);
        pools = mm.getPools();
        final PoolSlice slice0 = pools[0].getSlices()[0];

        // check the number of buffers preallocated
        assertEquals((int) (slice0.getMaxElementsCount() * preallocatedPercentage),
                slice0.elementsCount());
    }


    @Test
    public void testInvalidConstructorArguments() {

        // invalid buffer size
        try {
            new PooledMemoryManager(0, 1, 0, 1, DEFAULT_HEAP_USAGE_PERCENTAGE,
                DEFAULT_PREALLOCATED_BUFFERS_PERCENTAGE, isDirect);
            fail();
        } catch (IllegalArgumentException iae) {
            // expected
        } catch (Exception e) {
            fail();
        }

        // invalid number of pools
        try {
            new PooledMemoryManager(1024, 0, 0, 1, DEFAULT_HEAP_USAGE_PERCENTAGE,
                DEFAULT_PREALLOCATED_BUFFERS_PERCENTAGE, isDirect);
        } catch (IllegalArgumentException iae) {
            // expected
        } catch (Exception e) {
            fail();
        }

        // invalid growth factor (negative)
        try {
            new PooledMemoryManager(1024, 1, -1, 1, DEFAULT_HEAP_USAGE_PERCENTAGE,
                    DEFAULT_PREALLOCATED_BUFFERS_PERCENTAGE, isDirect);
        } catch (IllegalArgumentException iae) {
            // expected
        } catch (Exception e) {
            fail();
        }

        // invalid growth factor (zero, when pools number > 1)
        try {
            new PooledMemoryManager(1024, 2, 0, 1, DEFAULT_HEAP_USAGE_PERCENTAGE
                    , DEFAULT_PREALLOCATED_BUFFERS_PERCENTAGE, isDirect);
        } catch (IllegalArgumentException iae) {
            // expected
        } catch (Exception e) {
            fail();
        }

        // invalid number of slices
        try {
            new PooledMemoryManager(1024, 1, 0, 0, DEFAULT_HEAP_USAGE_PERCENTAGE,
                DEFAULT_PREALLOCATED_BUFFERS_PERCENTAGE, isDirect);
        } catch (IllegalArgumentException iae) {
            // expected
        } catch (Exception e) {
            fail();
        }
        
        // invalid heap percentage (lower bound)
        try {
            new PooledMemoryManager(1024, 1, 0, 1, 0,
                    DEFAULT_PREALLOCATED_BUFFERS_PERCENTAGE, isDirect);
        } catch (IllegalArgumentException iae) {
            // expected
        } catch (Exception e) {
            fail();
        }

        // invalid heap percentage (upper bound)
        try {
            new PooledMemoryManager(1024, 1, 0, 1, 1,
                    DEFAULT_PREALLOCATED_BUFFERS_PERCENTAGE, isDirect);
        } catch (IllegalArgumentException iae) {
            // expected
        } catch (Exception e) {
            fail();
        }
        
        // invalid preallocated buffers percentage (lower bound)
        try {
            new PooledMemoryManager(1024, 1, 0, 1, DEFAULT_HEAP_USAGE_PERCENTAGE,
                    -0.01f, isDirect);
        } catch (IllegalArgumentException iae) {
            // expected
        } catch (Exception e) {
            fail();
        }

        // invalid preallocated buffers percentage (upper bound)
        try {
            new PooledMemoryManager(1024, 1, 0, 1, DEFAULT_HEAP_USAGE_PERCENTAGE,
                    1.01f, isDirect);
        } catch (IllegalArgumentException iae) {
            // expected
        } catch (Exception e) {
            fail();
        }        
    }

    @Test
    public void testSimpleAllocationAndDispose() throws Exception {

        PooledMemoryManager mm =
                new PooledMemoryManager(DEFAULT_BASE_BUFFER_SIZE,
                                        1,
                                        0,
                                        1,
                                        DEFAULT_HEAP_USAGE_PERCENTAGE,
                                        DEFAULT_PREALLOCATED_BUFFERS_PERCENTAGE,
                                        isDirect);

        final TestProbe probe = new TestProbe();
        mm.getMonitoringConfig().addProbes(probe);

        // allocate a buffer and validate the configuration of said buffer
        Buffer b = mm.allocate(4096);
        assertEquals(4096, b.remaining());
        assertTrue(!b.isComposite());
        assertTrue(b.allowBufferDispose());
        assertEquals(DEFAULT_BASE_BUFFER_SIZE, b.capacity());

        // validate that pool returned a buffer.
        assertEquals(0, probe.bufferAllocated.get());
        assertEquals(0, probe.bufferReleasedToPool.get());
        assertEquals(1, probe.bufferAllocatedFromPool.get());

        // dispose the buffer and validate the pool has returned to it's
        // original size
        b.tryDispose();
        // validate that buffer was returned to the pool.
        assertEquals(0, probe.bufferAllocated.get());
        assertEquals(1, probe.bufferReleasedToPool.get());
        assertEquals(1, probe.bufferAllocatedFromPool.get());
    }

    @Test
    public void testSimpleCompositeAllocationAndDispose() throws Exception {

        PooledMemoryManager mm =
                new PooledMemoryManager(DEFAULT_BASE_BUFFER_SIZE,
                        1,
                        0,
                        1,
                        DEFAULT_HEAP_USAGE_PERCENTAGE,
                        DEFAULT_PREALLOCATED_BUFFERS_PERCENTAGE,
                        isDirect);

        final TestProbe probe = new TestProbe();
        mm.getMonitoringConfig().addProbes(probe);

        // allocate a buffer and validate the configuration of said buffer
        Buffer b = mm.allocate(6000);
        assertEquals(6000, b.remaining());
        assertTrue(b.allowBufferDispose());

         // validate that pool returned a buffer.
        assertEquals(0, probe.bufferAllocated.get());
        assertEquals(0, probe.bufferReleasedToPool.get());
        assertEquals(2, probe.bufferAllocatedFromPool.get());

        // dispose the buffer and validate the pool has returned to it's
        // original elements count
        b.tryDispose();
        // validate that the buffers were returned to the pool.
        assertEquals(0, probe.bufferAllocated.get());
        assertEquals(2, probe.bufferReleasedToPool.get());
        assertEquals(2, probe.bufferAllocatedFromPool.get());

        // validate all buffers at this stage are of the expected capacity
        final PooledMemoryManager.PoolSlice slice = mm.getPools()[0].getSlices()[0];
        PooledMemoryManager.PoolBuffer first = slice.poll();
        PooledMemoryManager.PoolBuffer buffer = first;
        do {
            assertNotNull(buffer);

            assertTrue(buffer.free());
            buffer.free(false); // otherwise following buffer.capacity() will throw an exception
            
            assertEquals(4096, buffer.capacity());
            buffer.free(true); // restore the flag
            
            slice.offer(buffer);
        } while ((buffer = slice.poll()) != first);

    }

    @Test
    public void testReallocate() throws Exception {

        PooledMemoryManager mm =
                new PooledMemoryManager(DEFAULT_BASE_BUFFER_SIZE,
                        1,
                        0,
                        1,
                        DEFAULT_HEAP_USAGE_PERCENTAGE,
                        DEFAULT_PREALLOCATED_BUFFERS_PERCENTAGE,
                        isDirect);

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
                new PooledMemoryManager(DEFAULT_BASE_BUFFER_SIZE,
                        1,
                        0,
                        1,
                        DEFAULT_HEAP_USAGE_PERCENTAGE,
                        DEFAULT_PREALLOCATED_BUFFERS_PERCENTAGE,
                        isDirect);

        final TestProbe probe = new TestProbe();
        mm.getMonitoringConfig().addProbes(probe);
        Buffer b = mm.allocate(6666);
        assertEquals(0, probe.bufferAllocated.get());
        assertEquals(0, probe.bufferReleasedToPool.get());
        assertEquals(2, probe.bufferAllocatedFromPool.get());
        b.position(1000);
        b.trim();
        assertEquals(4096, b.capacity());
        assertEquals(0, probe.bufferAllocated.get());
        assertEquals(1, probe.bufferReleasedToPool.get());
        assertEquals(2, probe.bufferAllocatedFromPool.get());
        b.tryDispose();
        assertEquals(0, probe.bufferAllocated.get());
        assertEquals(2, probe.bufferReleasedToPool.get());
        assertEquals(2, probe.bufferAllocatedFromPool.get());

        b = mm.allocate(1023);
        assertEquals(0, probe.bufferAllocated.get());
        assertEquals(2, probe.bufferReleasedToPool.get());
        assertEquals(3, probe.bufferAllocatedFromPool.get());
        b.trim();
        assertEquals(0, probe.bufferAllocated.get());
        assertEquals(2, probe.bufferReleasedToPool.get());
        assertEquals(3, probe.bufferAllocatedFromPool.get());
        b.tryDispose();
        assertEquals(0, probe.bufferAllocated.get());
        assertEquals(3, probe.bufferReleasedToPool.get());
        assertEquals(3, probe.bufferAllocatedFromPool.get());

    }

    @Test
    public void testBufferShrink() throws Exception {

        PooledMemoryManager mm =
                new PooledMemoryManager(DEFAULT_BASE_BUFFER_SIZE,
                        1,
                        0,
                        1,
                        DEFAULT_HEAP_USAGE_PERCENTAGE,
                        DEFAULT_PREALLOCATED_BUFFERS_PERCENTAGE,
                        isDirect);

        final TestProbe probe = new TestProbe();
        mm.getMonitoringConfig().addProbes(probe);
        Buffer b = mm.allocate(13000);
        assertEquals(0, probe.bufferAllocated.get());
        assertEquals(0, probe.bufferReleasedToPool.get());
        assertEquals(4, probe.bufferAllocatedFromPool.get());
        b.position(6666);
        b.limit(7000);
        b.shrink();
        assertEquals(334, b.remaining());
        assertEquals(0, probe.bufferAllocated.get());
        assertEquals(3, probe.bufferReleasedToPool.get());
        assertEquals(4, probe.bufferAllocatedFromPool.get());
        b.tryDispose();
        assertEquals(0, probe.bufferAllocated.get());
        assertEquals(4, probe.bufferReleasedToPool.get());
        assertEquals(4, probe.bufferAllocatedFromPool.get());

    }


    @Test
    public void testIllegalAllocationArgument() throws Exception {

        PooledMemoryManager mm =
                new PooledMemoryManager(DEFAULT_BASE_BUFFER_SIZE,
                        1,
                        0,
                        1,
                        DEFAULT_HEAP_USAGE_PERCENTAGE,
                        DEFAULT_PREALLOCATED_BUFFERS_PERCENTAGE,
                        isDirect);

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
                new PooledMemoryManager(DEFAULT_BASE_BUFFER_SIZE,
                        1,
                        0,
                        1,
                        DEFAULT_HEAP_USAGE_PERCENTAGE,
                        DEFAULT_PREALLOCATED_BUFFERS_PERCENTAGE,
                        isDirect);

        final TestProbe probe = new TestProbe();
        mm.getMonitoringConfig().addProbes(probe);

        // === duplicate ================
        PooledMemoryManager.PoolBuffer b = (PooledMemoryManager.PoolBuffer)
                mm.allocate(4096); // allocate a single buffer
        assertEquals(0, probe.bufferAllocated.get());
        assertEquals(0, probe.bufferReleasedToPool.get());
        assertEquals(1, probe.bufferAllocatedFromPool.get());
        Buffer duplicate = b.duplicate();
        // pool size remains constant after duplicate
        assertEquals(0, probe.bufferAllocated.get());
        assertEquals(0, probe.bufferReleasedToPool.get());
        assertEquals(1, probe.bufferAllocatedFromPool.get());

        // dispose the original buffer.  It shouldn't be returned to the pool
        // as the duplicate buffer still holds a reference.
        b.tryDispose();
        assertEquals(0, probe.bufferAllocated.get());
        assertEquals(0, probe.bufferReleasedToPool.get());
        assertEquals(1, probe.bufferAllocatedFromPool.get());

        // now dispose the duplicate, pool should return to original elements count
        duplicate.tryDispose();
        assertEquals(0, probe.bufferAllocated.get());
        assertEquals(1, probe.bufferReleasedToPool.get());
        assertEquals(1, probe.bufferAllocatedFromPool.get());

        // === read-only ================
        probe.bufferReleasedToPool.set(0);
        probe.bufferAllocatedFromPool.set(0);
        b = (PooledMemoryManager.PoolBuffer) mm.allocate(4096);
        assertEquals(0, probe.bufferAllocated.get());
        assertEquals(0, probe.bufferReleasedToPool.get());
        assertEquals(1, probe.bufferAllocatedFromPool.get());
        Buffer readOnlyBuffer = b.asReadOnlyBuffer();
        // pool size remains constant after duplicate
        assertEquals(0, probe.bufferAllocated.get());
        assertEquals(0, probe.bufferReleasedToPool.get());
        assertEquals(1, probe.bufferAllocatedFromPool.get());

        // dispose the original buffer.  It shouldn't be returned to the pool
        // as the duplicate buffer still holds a reference.
        b.tryDispose();
        assertEquals(0, probe.bufferAllocated.get());
        assertEquals(0, probe.bufferReleasedToPool.get());
        assertEquals(1, probe.bufferAllocatedFromPool.get());

        // now dispose the duplicate, pool should return to original elements count
        readOnlyBuffer.tryDispose();
        assertEquals(0, probe.bufferAllocated.get());
        assertEquals(1, probe.bufferReleasedToPool.get());
        assertEquals(1, probe.bufferAllocatedFromPool.get());

        // === slice ====================
        probe.bufferReleasedToPool.set(0);
        probe.bufferAllocatedFromPool.set(0);
        b = (PooledMemoryManager.PoolBuffer) mm.allocate(4096);
        assertEquals(0, probe.bufferAllocated.get());
        assertEquals(0, probe.bufferReleasedToPool.get());
        assertEquals(1, probe.bufferAllocatedFromPool.get());
        b.position(10);
        Buffer slicedBuffer = b.asReadOnlyBuffer();
        // pool size remains constant after duplicate
        assertEquals(0, probe.bufferAllocated.get());
        assertEquals(0, probe.bufferReleasedToPool.get());
        assertEquals(1, probe.bufferAllocatedFromPool.get());

        // dispose the original buffer.  It shouldn't be returned to the pool
        // as the duplicate buffer still holds a reference.
        b.tryDispose();
        assertEquals(0, probe.bufferAllocated.get());
        assertEquals(0, probe.bufferReleasedToPool.get());
        assertEquals(1, probe.bufferAllocatedFromPool.get());

        // now dispose the duplicate, pool should return to original elements count
        slicedBuffer.tryDispose();
        assertEquals(0, probe.bufferAllocated.get());
        assertEquals(1, probe.bufferReleasedToPool.get());
        assertEquals(1, probe.bufferAllocatedFromPool.get());

        // === split ====================
        probe.bufferReleasedToPool.set(0);
        probe.bufferAllocatedFromPool.set(0);
        b = (PooledMemoryManager.PoolBuffer) mm.allocate(4096);
        assertEquals(0, probe.bufferAllocated.get());
        assertEquals(0, probe.bufferReleasedToPool.get());
        assertEquals(1, probe.bufferAllocatedFromPool.get());
        Buffer splitBuffer = b.split(2048);
        // pool size remains constant after duplicate
        assertEquals(0, probe.bufferAllocated.get());
        assertEquals(0, probe.bufferReleasedToPool.get());
        assertEquals(1, probe.bufferAllocatedFromPool.get());

        // dispose the original buffer.  It shouldn't be returned to the pool
        // as the duplicate buffer still holds a reference.
        b.tryDispose();
        assertEquals(0, probe.bufferAllocated.get());
        assertEquals(0, probe.bufferReleasedToPool.get());
        assertEquals(1, probe.bufferAllocatedFromPool.get());

        // now dispose the duplicate, pool should return to original elements count
        splitBuffer.tryDispose();
        assertEquals(0, probe.bufferAllocated.get());
        assertEquals(1, probe.bufferReleasedToPool.get());
        assertEquals(1, probe.bufferAllocatedFromPool.get());

        // split is a special case in that the visible portion of the original
        // buffer is replaced by the first half of the split result.  We need
        // to make sure that the returned result is the that first half, but
        // the full 4096.
        PooledMemoryManager.PoolSlice slice0 = mm.getPools()[0].getSlices()[0];
        PooledMemoryManager.PoolBuffer first = slice0.poll();
        PooledMemoryManager.PoolBuffer buffer = first;
        do {
            assertTrue(buffer.free());
            buffer.free(false); // otherwise following buffer.capacity() will throw an exception
            
            assertEquals(4096, buffer.capacity());
            buffer.free(true); // restore the flag
            
            slice0.offer(buffer);
        } while ((buffer = slice0.poll()) != first);
        slice0.offer(buffer);

        // = time to mix it up a bit ====
        probe.bufferReleasedToPool.set(0);
        probe.bufferAllocatedFromPool.set(0);
        b = (PooledMemoryManager.PoolBuffer) mm.allocate(4096);
        assertEquals(0, probe.bufferAllocated.get());
        assertEquals(0, probe.bufferReleasedToPool.get());
        assertEquals(1, probe.bufferAllocatedFromPool.get());
        duplicate = b.duplicate();
        splitBuffer = duplicate.split(2048);
        readOnlyBuffer = splitBuffer.asReadOnlyBuffer();
        slicedBuffer = readOnlyBuffer.position(10).slice();

        // random disposes
        readOnlyBuffer.tryDispose();
        assertEquals(0, probe.bufferAllocated.get());
        assertEquals(0, probe.bufferReleasedToPool.get());
        assertEquals(1, probe.bufferAllocatedFromPool.get());
        slicedBuffer.tryDispose();
        assertEquals(0, probe.bufferAllocated.get());
        assertEquals(0, probe.bufferReleasedToPool.get());
        assertEquals(1, probe.bufferAllocatedFromPool.get());
        b.tryDispose();
        assertEquals(0, probe.bufferAllocated.get());
        assertEquals(0, probe.bufferReleasedToPool.get());
        assertEquals(1, probe.bufferAllocatedFromPool.get());
        splitBuffer.tryDispose();
        assertEquals(0, probe.bufferAllocated.get());
        assertEquals(0, probe.bufferReleasedToPool.get());
        assertEquals(1, probe.bufferAllocatedFromPool.get());
        duplicate.tryDispose();
        assertEquals(0, probe.bufferAllocated.get());
        assertEquals(1, probe.bufferReleasedToPool.get());
        assertEquals(1, probe.bufferAllocatedFromPool.get());

        // split was performed at some point, make sure all buffers have
        // the expected capacities within the pool
        slice0 = mm.getPools()[0].getSlices()[0];
        buffer = first = slice0.poll();
        do {
            assertTrue(buffer.free());
            buffer.free(false); // otherwise following buffer.capacity() will throw an exception
            
            assertEquals(4096, buffer.capacity());
            buffer.free(true); // restore the flag
            
            slice0.offer(buffer);
        } while ((buffer = slice0.poll()) != first);
    }


    @Test
    public void circularityBoundaryTest() {
        final PooledMemoryManager mm = new PooledMemoryManager(
                128, 1, 0, 1,
                1024.0f / Runtime.getRuntime().maxMemory(),
                DEFAULT_PREALLOCATED_BUFFERS_PERCENTAGE,
                isDirect);
        final TestProbe probe = new TestProbe();
        mm.getMonitoringConfig().addProbes(probe);

        final PooledMemoryManager.PoolSlice slice0 = mm.getPools()[0].getSlices()[0];
        final ArrayList<PooledMemoryManager.PoolBuffer> tempStorage =
                new ArrayList<PooledMemoryManager.PoolBuffer>();
        int elementCount = slice0.elementsCount();
        assertTrue(elementCount > 0);
        assertFalse(slice0.offer(slice0.allocate()));
        for (int i = 0; i < elementCount; i++) {
            PooledMemoryManager.PoolBuffer b = slice0.poll();
            assertNotNull(b);
            tempStorage.add(b);
        }
        assertNull(slice0.poll());
        assertEquals(0, slice0.elementsCount());
        assertEquals(elementCount, probe.bufferAllocatedFromPool.get());
        assertTrue(slice0.offer(tempStorage.get(0)));
        assertTrue(slice0.offer(tempStorage.get(1)));
        assertEquals(2, probe.bufferReleasedToPool.get());
        assertEquals(2, slice0.elementsCount());
        PooledMemoryManager.PoolBuffer b = slice0.poll();
        assertNotNull(b);
        tempStorage.add(b);
        b = slice0.poll();
        assertNotNull(b);
        tempStorage.add(b);
        assertEquals(elementCount + 2, probe.bufferAllocatedFromPool.get());
        assertNull(slice0.poll());

        for (int i = 0; i < elementCount; i++) {
            assertTrue(slice0.offer(tempStorage.get(i)));
        }
        assertEquals(elementCount + 2, probe.bufferReleasedToPool.get());
        assertEquals(elementCount, slice0.elementsCount());
        assertFalse(slice0.offer(slice0.allocate()));
    }

    @Test
    public void stressTest() {
        final int poolsNum = 3;
        
        final int numTestThreads =
                Runtime.getRuntime().availableProcessors() * 8;
        final PooledMemoryManager mm = new PooledMemoryManager(
                4096, poolsNum, 1, Runtime.getRuntime().availableProcessors(), .10f,
                DEFAULT_PREALLOCATED_BUFFERS_PERCENTAGE, isDirect);
        final ThreadFactory f =
                new ThreadFactory() {
                    final AtomicInteger ii =
                            new AtomicInteger();

                    @Override
                    public Thread newThread(Runnable r) {
                        final Thread t = new Thread(r);
                        t.setName("Stress-" + ii.incrementAndGet());
                        t.setDaemon(true);
                        return t;
                    }
                };
        ExecutorService service =
                Executors.newFixedThreadPool(numTestThreads, f);
        int[] expectedElementCount = new int[poolsNum];
        for (int i = 0; i < poolsNum; i++) {
            expectedElementCount[i] = mm.getPools()[i].elementsCount();
        }
        final CountDownLatch latch = new CountDownLatch(numTestThreads);
        final Throwable[] errors = new Throwable[numTestThreads];
        final AtomicBoolean errorsSeen = new AtomicBoolean();
        for (int i = 0; i < numTestThreads; i++) {
            final int thread = i;
            service.submit(new Runnable() {
                final Random random = new Random(hashCode());

                @Override
                public void run() {
                    for (int i = 0; i < 100000; i++) {
                        try {
                            Buffer b = mm.allocate(random.nextInt(9000) + 1);
                            Buffer b1 = mm.allocate(random.nextInt(33000) + 1);
                            assertNotNull(b);
                            assertNotNull(b1);
                            assertTrue("Buffer=" + b, b.tryDispose());
                            assertTrue("Buffer=" + b1, b1.tryDispose());
                        } catch (Throwable t) {
                            errorsSeen.set(true);
                            System.out.println("Failed at iteration: " + i);
                            t.printStackTrace();
                            errors[thread] = t;
                            break;
                        }
                    }
                    latch.countDown();
                }
            });
        }

        try {
            latch.await(10, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        if (errorsSeen.get()) {
            for (int i = 0, len = errors.length; i < len; i++) {
                if (errors[i] != null) {
                    Logger.getAnonymousLogger().log(Level.SEVERE,
                                                    "Error in test thread " + (i + 1) + ": " + errors[i]
                                                            .getMessage(),
                                                    errors[i]);
                }
            }
            fail("Test failed!  See log for details.");
        }

        for (int i = 0; i < poolsNum; i++) {
            PooledMemoryManager.Pool pool = mm.getPools()[i];
            assertEquals("Pool[" + Integer.toHexString(pool.hashCode()) + "] at index " + i + ", has an incorrect size.  Expected: "
                                 + expectedElementCount[i] + ", actual: " + pool.elementsCount() + "\npool: " + pool.toString(),
                         expectedElementCount[i],
                         pool.elementsCount());
        }

    }


    // ---------------------------------------------------------- Nested Classes


    static final class TestProbe implements MemoryProbe {
        final AtomicInteger bufferAllocated = new AtomicInteger();
        final AtomicInteger bufferAllocatedFromPool = new AtomicInteger();
        final AtomicInteger bufferReleasedToPool = new AtomicInteger();

        @Override
        public void onBufferAllocateEvent(int size) {
            bufferAllocated.incrementAndGet();
        }

        @Override
        public void onBufferAllocateFromPoolEvent(int size) {
            bufferAllocatedFromPool.incrementAndGet();
        }

        @Override
        public void onBufferReleaseToPoolEvent(int size) {
            bufferReleasedToPool.incrementAndGet();
        }
    }
}
