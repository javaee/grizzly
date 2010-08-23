/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2010 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.grizzly;


import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import junit.framework.Assert;

import com.sun.grizzly.memory.MemoryManager;
import com.sun.grizzly.memory.slab.Slab;
import com.sun.grizzly.memory.slab.SlabMemoryManagerFactory;


/**
 * Tests getting and returning of ByteWrappers to their corresponding
 * Slabs.
 *
 * @author Ken Cavanaugh
 * @author John Vieten
 */
public class SlabMemoryManagerTest extends GrizzlyTestCase {
    public static final int PORT = 7778;
    private static final int SLAB_SIZE = 90000;
    private Slab testSlab;
    private static Logger logger = Grizzly.logger(SlabMemoryManagerTest.class);

    @Override
    protected void setUp() {
        testSlab = new Slab(SLAB_SIZE, false);
        Assert.assertTrue(testSlab.getState() == Slab.State.EMPTY);
    }

    public void testTotalSize() {
        Assert.assertEquals(testSlab.totalSize(), SLAB_SIZE);
    }

    private static final int[] ALLOCATION_SIZES = new int[]{
            12, 200, 34, 5732, 45, 12340, 1200, 2000, 2500, 456, 5200
    };

    private void testSlabState(Slab testSlab, int available, int allocated,
                               int disposed, Slab.State state) {

        Assert.assertEquals(testSlab.sizeAvailable(), available);
        Assert.assertEquals(testSlab.sizeAllocated(), allocated);
        Assert.assertEquals(testSlab.sizeDisposed(), disposed);
        Assert.assertEquals(testSlab.getState(), state);
    }

    // Test allocate and dispose, along with state changes and sizeXXX methods.
    public void testAllocate() {
        testSlab.markEmpty();

        testSlabState(testSlab, SLAB_SIZE, 0, 0, Slab.State.EMPTY);

        int totalAllocations = 0;
        ByteBuffer[] allocations = new ByteBuffer[ALLOCATION_SIZES.length];
        for (int ctr = 0; ctr < ALLOCATION_SIZES.length; ctr++) {
            int size = ALLOCATION_SIZES[ctr];
            allocations[ctr] = testSlab.allocate(size);
            totalAllocations += size;
        }

        Assert.assertNull(testSlab.allocate(SLAB_SIZE));

        testSlabState(testSlab, SLAB_SIZE - totalAllocations,
                totalAllocations, 0, Slab.State.PARTIAL);

        for (ByteBuffer buffer : allocations) {
            testSlab.dispose(buffer);
        }

        testSlabState(testSlab, SLAB_SIZE - totalAllocations,
                totalAllocations, totalAllocations, Slab.State.PARTIAL);

        // This call to markFull should actually cause the buffer to become 
        // empty!
        testSlab.markFull();

        testSlabState(testSlab, SLAB_SIZE, 0, 0, Slab.State.EMPTY);

        testSlab.markEmpty();

        testSlabState(testSlab, SLAB_SIZE, 0, 0, Slab.State.EMPTY);
    }

    public void testTrim() {
        testSlab.markEmpty();

        int testSize = 5400;

        testSlabState(testSlab, SLAB_SIZE, 0, 0, Slab.State.EMPTY);

        // just throw some space away
        testSlab.allocate(testSize);

        int startPos = testSlab.currentPosition();
        ByteBuffer buff = testSlab.allocate(testSize);

        testSlabState(testSlab, SLAB_SIZE - 2 * testSize, 2 * testSize,
                0, Slab.State.PARTIAL);

        Assert.assertEquals(buff.capacity(), testSize);
        Assert.assertEquals(buff.limit(), buff.capacity());
        Assert.assertEquals(buff.position(), 0);

        ByteBuffer newBuffer = testSlab.trim(startPos, buff, testSize / 2);
        Assert.assertNotSame(buff, newBuffer);

        int expectedSpaceAllocated = 2 * testSize - testSize / 2;

        testSlabState(testSlab, SLAB_SIZE - expectedSpaceAllocated,
                expectedSpaceAllocated, 0, Slab.State.PARTIAL);
    }

    public void testBufferWrapper() {
        //byte[] data1 = new byte[100] ;
        byte[] data2 = new byte[500];


        for (int ctr = 0; ctr < data2.length; ctr++)
            data2[ctr] = (byte) ((ctr + 10) & 255);


        MemoryManager alloc1 =
                SlabMemoryManagerFactory.makeAllocator(10000, false);
        Buffer eb1 = alloc1.allocate(data2.length);
        eb1.put(data2);
        eb1.flip();
        byte[] result1 = new byte[data2.length];
        eb1.get(result1);
        eb1.flip();

        Assert.assertEquals(true, Arrays.equals(result1, data2));
    }

    private AtomicInteger receivedByteCount;


  
//    public void testThreadPoolReadBytes() {
//        receivedByteCount = new AtomicInteger();
//        final int sendByteCount = 512 * 100;
//        final int REPEAT_COUNT = 10;
//        final CountDownLatch latch = new CountDownLatch(1);
//        TCPNIOTransport servertransport = TransportFactory.getInstance().createTCPTransport();
//        final SlabMemoryManagerFactory.SlabPool pool = SlabMemoryManagerFactory.makeDefaultSlabPool();
//        MemoryManager manager = SlabMemoryManagerFactory.makePoolAllocator(pool);
//        servertransport.setMemoryManager(manager);
//
//        servertransport.getFilterChain().add(new TransportFilter());
//        servertransport.getFilterChain().add(new FilterAdapter() {
//            @Override
//            public NextAction handleRead(FilterChainContext ctx,
//                                         NextAction nextAction) throws IOException {
//                ByteBufferWrapper wr = (ByteBufferWrapper) ctx.getMessage();
//                receivedByteCount.addAndGet(wr.limit());
//                wr.dispose();
//                if (receivedByteCount.get() == sendByteCount * REPEAT_COUNT) latch.countDown();
//                return nextAction;
//            }
//        });
//
//
//        byte[] data = new byte[sendByteCount];
//        for (int ctr = 0; ctr < data.length; ctr++)
//            data[ctr] = (byte) ((ctr + 10) & 255);
//
//
//        try {
//            servertransport.bind(PORT);
//            servertransport.configureBlocking(false);
//            servertransport.start();
//        } catch (Exception ex) {
//            logger.log(Level.SEVERE, "Server start error", ex);
//        }
//        TCPNIOTransport clienttransport = servertransport;
//        try {
//            clienttransport.configureBlocking(true);
//
//            Future<Connection> future = clienttransport.connect("localhost", PORT);
//            Connection connection = future.get(10, TimeUnit.SECONDS);
//            StreamWriter writer = connection.getStreamWriter();
//            for (int i = 0; i < REPEAT_COUNT; i++) {
//                writer.writeByteArray(data);
//                writer.flush();
//            }
//
//            try {
//                latch.await(2000, TimeUnit.MILLISECONDS);
//            } catch (InterruptedException e) {
//
//            }
//            Assert.assertEquals(receivedByteCount.get(), sendByteCount*REPEAT_COUNT);
//            receivedByteCount=null;
//            servertransport.stop();
//
//        } catch (Exception ex) {
//            logger.log(Level.SEVERE, "Server start error", ex);
//        }
//    }
//    /**
//     * Use all space of a Slab and check after deposing the used space if Slab gets recycled.
//     */
//
//    public void testSlabThreadPoolAllocation() {
//        final int SLAB_SIZE = 1000;
//        final CountDownLatch latch = new CountDownLatch(1);
//        final TCPNIOTransport servertransport = TransportFactory.getInstance().createTCPTransport();
//        servertransport.setWorkerThreadPool(new DefaultThreadPool());
//        final SlabMemoryManagerFactory.SlabPool pool =
//                SlabMemoryManagerFactory.makeSlabPool(SLAB_SIZE,SLAB_SIZE*3,SLAB_SIZE*10,false);
//        MemoryManager manager = SlabMemoryManagerFactory.makePoolAllocator(pool);
//        servertransport.setMemoryManager(manager);
//        servertransport.getFilterChain().add(new FilterAdapter() {
//            @Override
//            public NextAction handleRead(FilterChainContext ctx,
//                                         NextAction nextAction) throws IOException {
//                MemoryManager<ByteBufferWrapper> allocator =servertransport.getMemoryManager();
//                TCPNIOConnection connection = (TCPNIOConnection) ctx.getConnection();
//                 // just read in all space of a Slab
//                // call buffer.dispose
//                // check if Slab is reused
//                 for(int i=0;i<10;i++) {
//                     ByteBufferWrapper buffer = allocator.allocate(100);
//                     assertTrue("Pool should have allocated some space",pool.allocatedSpace()!=0);
//
//
//                     if(i==0){
//                         // have to at least read the send byte otherwise another read event might be issued
//                         connection.readNow0(buffer, null);
//                     }
//                     buffer.dispose();
//                 }
//                 latch.countDown();
//                return new StopAction();
//            }
//        });
//
//
//        try {
//            servertransport.bind(PORT);
//            servertransport.configureBlocking(false);
//            servertransport.setWorkerThreadPool(new DefaultThreadPool());
//            servertransport.start();
//        } catch (Exception ex) {
//            logger.log(Level.SEVERE, "Server start error", ex);
//        }
//        TCPNIOTransport clienttransport = servertransport;
//        try {
//            clienttransport.configureBlocking(true);
//
//            Future<Connection> future = clienttransport.connect("localhost", PORT);
//            Connection connection = future.get(10, TimeUnit.SECONDS);
//            MemoryManager memoryManager = new ByteBufferViewManager();
//
//            Buffer<ByteBuffer> byteBuffer = memoryManager.allocate(1);
//            // just make server filterchain start on WorkerThread
//            connection.write(byteBuffer);
//
//
//            try {
//                latch.await(1000, TimeUnit.MILLISECONDS);
//            } catch (InterruptedException e) {
//
//            }
//            assertTrue("Slab should be empty and Pool allocated space == 0 ",pool.allocatedSpace()==0);
//            servertransport.stop();
//
//        } catch (Exception ex) {
//            logger.log(Level.SEVERE, "Server start error", ex);
//        }
//    }


    @Override
    protected void tearDown() {
        testSlab = null;
    }
}


