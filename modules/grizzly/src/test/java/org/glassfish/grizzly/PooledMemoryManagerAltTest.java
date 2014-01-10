/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2014 Oracle and/or its affiliates. All rights reserved.
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

import org.glassfish.grizzly.memory.MemoryProbe;
import org.glassfish.grizzly.memory.PooledMemoryManagerAlt;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
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

import static org.junit.Assert.*;
import org.junit.Ignore;

public class PooledMemoryManagerAltTest {


    @Test
    @Ignore
    public void testDefaultPoolInitialization() throws Exception {

        // default configuration is 10% of heap will be used for memory.
        // Contains 3 sub-pools with sizes 4096, 16384, 65536
        // Number of slices per memory sub-pool is based on the
        // number of processors available to the runtime.
        final int numProcessors = Runtime.getRuntime().availableProcessors();
        final long totalMemory = (long) (Runtime.getRuntime().maxMemory()
                * PooledMemoryManagerAlt.DEFAULT_HEAP_USAGE_PERCENTAGE);

        // the total consumed memory should be equal to or greater than 'totalMemory'
        PooledMemoryManagerAlt mm = new PooledMemoryManagerAlt();
        PooledMemoryManagerAlt.Pool[] pools = mm.getPools();
        assertEquals(PooledMemoryManagerAlt.DEFAULT_NUMBER_OF_POOLS, pools.length);
        
        int bufSize = PooledMemoryManagerAlt.DEFAULT_BASE_BUFFER_SIZE;
        for (int i = 0, len = pools.length; i < len; i++) {
            assertEquals(bufSize, pools[i].getBufferSize());
            bufSize <<= PooledMemoryManagerAlt.DEFAULT_GROWTH_FACTOR;
        }

        PooledMemoryManagerAlt.PoolSlice[] slices = pools[0].getSlices();
        assertEquals(numProcessors, slices.length);
        long consumedMemory = 0;
        for (int i = 0, len = pools.length; i < len; i++) {
            consumedMemory += pools[i].size();
        }

        assertTrue(consumedMemory + "<=" + totalMemory + " failed",
        consumedMemory <= totalMemory);
    }

    @Test
    @Ignore
    public void testCustomizedPoolInitialization() throws Exception {
        final float memoryPercentage = 0.05f;
        
        PooledMemoryManagerAlt mm = new PooledMemoryManagerAlt(2048, 1, 0,
                Runtime.getRuntime().availableProcessors(), memoryPercentage);
        final long memoryPerPool = (long) (Runtime.getRuntime().maxMemory() * memoryPercentage);
        PooledMemoryManagerAlt.Pool[] pools = mm.getPools();

        // should only have one pool per the constructor
        assertEquals(1, pools.length);

        // consumed memory should be less than or equal to 5% of the heap
        assertTrue(pools[0].size() + "<=" + memoryPerPool + " failed",
                pools[0].size() <= memoryPerPool);

        // buffer size should be 2048
        assertEquals(2048, pools[0].getBufferSize());
    }


    @Test
    public void testInvalidConstructorArguments() {

        // invalid buffer size
        try {
            new PooledMemoryManagerAlt(0, 1, 0, 1, PooledMemoryManagerAlt.DEFAULT_HEAP_USAGE_PERCENTAGE);
            fail();
        } catch (IllegalArgumentException iae) {
            // expected
        } catch (Exception e) {
            fail();
        }

        // invalid number of pools
        try {
            new PooledMemoryManagerAlt(1024, 0, 0, 1, PooledMemoryManagerAlt.DEFAULT_HEAP_USAGE_PERCENTAGE);
        } catch (IllegalArgumentException iae) {
            // expected
        } catch (Exception e) {
            fail();
        }

        // invalid growth factor (negative)
        try {
            new PooledMemoryManagerAlt(1024, 1, -1, 1, PooledMemoryManagerAlt.DEFAULT_HEAP_USAGE_PERCENTAGE);
        } catch (IllegalArgumentException iae) {
            // expected
        } catch (Exception e) {
            fail();
        }

        // invalid growth factor (zero, when pools number > 1)
        try {
            new PooledMemoryManagerAlt(1024, 2, 0, 1, PooledMemoryManagerAlt.DEFAULT_HEAP_USAGE_PERCENTAGE);
        } catch (IllegalArgumentException iae) {
            // expected
        } catch (Exception e) {
            fail();
        }

        // invalid number of slices
        try {
            new PooledMemoryManagerAlt(1024, 1, 0, 0, PooledMemoryManagerAlt.DEFAULT_HEAP_USAGE_PERCENTAGE);
        } catch (IllegalArgumentException iae) {
            // expected
        } catch (Exception e) {
            fail();
        }
        
        // invalid heap percentage (lower bound)
        try {
            new PooledMemoryManagerAlt(1024, 1, 0, 1, 0);
        } catch (IllegalArgumentException iae) {
            // expected
        } catch (Exception e) {
            fail();
        }

        // invalid heap percentage (upper bound)
        try {
            new PooledMemoryManagerAlt(1024, 1, 0, 1, 1);
        } catch (IllegalArgumentException iae) {
            // expected
        } catch (Exception e) {
            fail();
        }
    }

    @Test
    public void testSimpleAllocationAndDispose() throws Exception {

        PooledMemoryManagerAlt mm =
                new PooledMemoryManagerAlt(PooledMemoryManagerAlt.DEFAULT_BASE_BUFFER_SIZE,
                                        1,
                                        0,
                                        1,
                                        PooledMemoryManagerAlt.DEFAULT_HEAP_USAGE_PERCENTAGE);

        final TestProbe probe = new TestProbe();
        mm.getMonitoringConfig().addProbes(probe);

        // allocate a buffer and validate the configuration of said buffer
        Buffer b = mm.allocate(4096);
        assertEquals(4096, b.remaining());
        assertTrue(!b.isComposite());
        assertTrue(b.allowBufferDispose());
        assertEquals(PooledMemoryManagerAlt.DEFAULT_BASE_BUFFER_SIZE, b.capacity());

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

        PooledMemoryManagerAlt mm =
                new PooledMemoryManagerAlt(PooledMemoryManagerAlt.DEFAULT_BASE_BUFFER_SIZE,
                        1,
                        0,
                        1,
                        PooledMemoryManagerAlt.DEFAULT_HEAP_USAGE_PERCENTAGE);

        final TestProbe probe = new TestProbe();
        mm.getMonitoringConfig().addProbes(probe);

        // allocate a buffer and validate the configuration of said buffer
        Buffer b = mm.allocate(6000);
        assertEquals(6000, b.remaining());
        assertTrue(b.allowBufferDispose());
        assertTrue((b.capacity() % PooledMemoryManagerAlt.DEFAULT_BASE_BUFFER_SIZE) == 0);

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
        final PooledMemoryManagerAlt.PoolSlice slice = mm.getPools()[0].getSlices()[0];
        PooledMemoryManagerAlt.PoolBuffer first = slice.poll();
        PooledMemoryManagerAlt.PoolBuffer buffer = first;
        do {
            assertNotNull(buffer);
            assertEquals(4096, buffer.capacity());
            slice.offer(buffer);
        } while ((buffer = slice.poll()) != first);

    }

    @Test
    public void testReallocate() throws Exception {

        PooledMemoryManagerAlt mm =
                new PooledMemoryManagerAlt(PooledMemoryManagerAlt.DEFAULT_BASE_BUFFER_SIZE,
                        1,
                        0,
                        1,
                        PooledMemoryManagerAlt.DEFAULT_HEAP_USAGE_PERCENTAGE);

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

        PooledMemoryManagerAlt mm =
                new PooledMemoryManagerAlt(PooledMemoryManagerAlt.DEFAULT_BASE_BUFFER_SIZE,
                        1,
                        0,
                        1,
                        PooledMemoryManagerAlt.DEFAULT_HEAP_USAGE_PERCENTAGE);

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

        PooledMemoryManagerAlt mm =
                new PooledMemoryManagerAlt(PooledMemoryManagerAlt.DEFAULT_BASE_BUFFER_SIZE,
                        1,
                        0,
                        1,
                        PooledMemoryManagerAlt.DEFAULT_HEAP_USAGE_PERCENTAGE);

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

        PooledMemoryManagerAlt mm =
                new PooledMemoryManagerAlt(PooledMemoryManagerAlt.DEFAULT_BASE_BUFFER_SIZE,
                        1,
                        0,
                        1,
                        PooledMemoryManagerAlt.DEFAULT_HEAP_USAGE_PERCENTAGE);

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
        PooledMemoryManagerAlt mm =
                new PooledMemoryManagerAlt(PooledMemoryManagerAlt.DEFAULT_BASE_BUFFER_SIZE,
                        1,
                        0,
                        1,
                        PooledMemoryManagerAlt.DEFAULT_HEAP_USAGE_PERCENTAGE);

        final TestProbe probe = new TestProbe();
        mm.getMonitoringConfig().addProbes(probe);

        // === duplicate ================
        PooledMemoryManagerAlt.PoolBuffer b = (PooledMemoryManagerAlt.PoolBuffer)
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
        b = (PooledMemoryManagerAlt.PoolBuffer) mm.allocate(4096);
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
        b = (PooledMemoryManagerAlt.PoolBuffer) mm.allocate(4096);
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
        b = (PooledMemoryManagerAlt.PoolBuffer) mm.allocate(4096);
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
        PooledMemoryManagerAlt.PoolSlice slice0 = mm.getPools()[0].getSlices()[0];
        PooledMemoryManagerAlt.PoolBuffer first = slice0.poll();
        PooledMemoryManagerAlt.PoolBuffer buffer = first;
        do {
            assertEquals(4096, buffer.capacity());
            slice0.offer(buffer);
        } while ((buffer = slice0.poll()) != first);
        slice0.offer(buffer);

        // = time to mix it up a bit ====
        probe.bufferReleasedToPool.set(0);
        probe.bufferAllocatedFromPool.set(0);
        b = (PooledMemoryManagerAlt.PoolBuffer) mm.allocate(4096);
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
            assertEquals(4096, buffer.capacity());
            slice0.offer(buffer);
        } while ((buffer = slice0.poll()) != first);
    }


    @Test
    public void circularityBoundaryTest() {
        final PooledMemoryManagerAlt mm = new PooledMemoryManagerAlt(
                128, 1, 0, 1,
                1024.0f / Runtime.getRuntime().maxMemory());
        final TestProbe probe = new TestProbe();
        mm.getMonitoringConfig().addProbes(probe);

        final PooledMemoryManagerAlt.PoolSlice slice0 = mm.getPools()[0].getSlices()[0];
        final ArrayList<PooledMemoryManagerAlt.PoolBuffer> tempStorage =
                new ArrayList<PooledMemoryManagerAlt.PoolBuffer>();
        int elementCount = slice0.elementsCount();
        assertTrue(elementCount > 0);
        for (int i = 0; i < elementCount; i++) {
            PooledMemoryManagerAlt.PoolBuffer b = slice0.poll();
            assertNotNull(b);
            tempStorage.add(b);
        }
        assertNull(slice0.poll());
        assertEquals(elementCount, probe.bufferAllocatedFromPool.get());
        assertTrue(slice0.offer(tempStorage.get(0)));
        assertTrue(slice0.offer(tempStorage.get(1)));
        assertEquals(2, probe.bufferReleasedToPool.get());
        PooledMemoryManagerAlt.PoolBuffer b = slice0.poll();
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
    }

    @Test
    public void stressTest() {
        final int poolsNum = 3;
        
        final int numTestThreads =
                Runtime.getRuntime().availableProcessors() * 8;
        final PooledMemoryManagerAlt mm = new PooledMemoryManagerAlt(
                4096, poolsNum, 1, Runtime.getRuntime().availableProcessors(), .10f);
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
        for (int i = 0, len = poolsNum; i < len; i++) {
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
            PooledMemoryManagerAlt.Pool pool = mm.getPools()[i];
            assertEquals("Pool[" + Integer.toHexString(pool.hashCode()) + "] at index " + i + ", has an incorrect size.  Expected: "
                                 + expectedElementCount[i] + ", actual: " + pool.elementsCount() + "\npool: " + pool.toString(),
                         expectedElementCount[i],
                         pool.elementsCount());
        }

    }


    // ---------------------------------------------------------- Nested Classes


    static final class TestProbe implements MemoryProbe {
        AtomicInteger bufferAllocated = new AtomicInteger();
        AtomicInteger bufferAllocatedFromPool = new AtomicInteger();
        AtomicInteger bufferReleasedToPool = new AtomicInteger();

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
