/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2009-2014 Oracle and/or its affiliates. All rights reserved.
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

import java.nio.ByteOrder;
import java.util.concurrent.TimeUnit;
import org.glassfish.grizzly.impl.FutureImpl;
import org.glassfish.grizzly.impl.SafeFutureImpl;
import org.glassfish.grizzly.threadpool.GrizzlyExecutorService;
import org.glassfish.grizzly.threadpool.ThreadPoolConfig;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.Grizzly;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author oleksiys
 */
@RunWith(Parameterized.class)
public class ThreadLocalMemoryManagerTest extends AbstractThreadLocalMemoryManagerTest {

    private static final Logger LOGGER = Grizzly.logger(ThreadLocalMemoryManagerTest.class);

    public ThreadLocalMemoryManagerTest(int mmType) {
        super(mmType);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testTrimDispose() throws Exception {
        mm.getMonitoringConfig().addProbes(new MyMemoryMonitoringProbe());
        Runnable r = new Runnable() {

            @Override
            public void run() {
                final int allocSize = 16384;

                // Initialize memory manager
                mm.allocate(33);

                final int initialSize = mm.getReadyThreadBufferSize();

                Buffer buffer = mm.allocate(allocSize);
                assertEquals(
                        initialSize - allocSize,
                        mm.getReadyThreadBufferSize());

                buffer.position(allocSize / 2);
                buffer.trim();

                assertEquals(initialSize - allocSize / 2,
                        mm.getReadyThreadBufferSize());

                buffer.dispose();

                assertEquals(initialSize,
                        mm.getReadyThreadBufferSize());
            }
        };

        testInWorkerThread(mm, r);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testDispose() throws Exception {
        mm.getMonitoringConfig().addProbes(new MyMemoryMonitoringProbe());
        Runnable r = new Runnable() {

            @Override
            public void run() {
                final int allocSize = 16384;

                // Initialize memory manager
                mm.allocate(33);

                final int initialSize = mm.getReadyThreadBufferSize();

                Buffer buffer = mm.allocate(allocSize);

                assertEquals(
                        initialSize - allocSize,
                        mm.getReadyThreadBufferSize());

                buffer.dispose();

                assertEquals(initialSize,
                        mm.getReadyThreadBufferSize());
            }
        };

        testInWorkerThread(mm, r);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testSimpleAllocateHistory() throws Exception {
        mm.getMonitoringConfig().addProbes(new MyMemoryMonitoringProbe());

        Runnable r = new Runnable() {

            @Override
            public void run() {

                // Initialize memory manager
                mm.allocate(33);

                final int initialSize = mm.getReadyThreadBufferSize();

                final int chunkSize = 4096;

                Buffer buffer1 = mm.allocate(chunkSize);
                assertEquals(
                        initialSize - chunkSize,
                        mm.getReadyThreadBufferSize());

                Buffer buffer2 = mm.allocate(chunkSize);
                assertEquals(
                        initialSize - chunkSize * 2,
                        mm.getReadyThreadBufferSize());

                Buffer buffer3 = mm.allocate(chunkSize);
                assertEquals(
                        initialSize - chunkSize * 3,
                        mm.getReadyThreadBufferSize());

                Buffer buffer4 = mm.allocate(chunkSize);
                assertEquals(
                        initialSize - chunkSize * 4,
                        mm.getReadyThreadBufferSize());

                buffer4.dispose();
                assertEquals(
                        initialSize - chunkSize * 3,
                        mm.getReadyThreadBufferSize());

                buffer3.dispose();
                assertEquals(
                        initialSize - chunkSize * 2,
                        mm.getReadyThreadBufferSize());

                buffer2.dispose();
                assertEquals(
                        initialSize - chunkSize,
                        mm.getReadyThreadBufferSize());

                buffer1.dispose();

                assertEquals(initialSize,
                        mm.getReadyThreadBufferSize());
            }
        };

        testInWorkerThread(mm, r);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testTrimAllocateHistory() throws Exception {
        mm.getMonitoringConfig().addProbes(new MyMemoryMonitoringProbe());
        Runnable r = new Runnable() {

            @Override
            public void run() {

                // Initialize memory manager
                mm.allocate(33);

                final int initialSize = mm.getReadyThreadBufferSize();

                final int chunkSize = 4096;

                Buffer buffer1 = mm.allocate(chunkSize);
                assertEquals(
                        initialSize - chunkSize,
                        mm.getReadyThreadBufferSize());

                buffer1.position(chunkSize / 2);
                buffer1.trim();
                assertEquals(
                        initialSize - chunkSize / 2,
                        mm.getReadyThreadBufferSize());

                Buffer buffer2 = mm.allocate(chunkSize);
                assertEquals(
                        initialSize - (chunkSize + chunkSize / 2),
                        mm.getReadyThreadBufferSize());

                buffer2.position(chunkSize / 2);
                buffer2.trim();
                assertEquals(
                        initialSize - chunkSize,
                        mm.getReadyThreadBufferSize());

                buffer2.dispose();
                assertEquals(
                        initialSize - chunkSize / 2,
                        mm.getReadyThreadBufferSize());

                buffer1.dispose();

                assertEquals(initialSize,
                        mm.getReadyThreadBufferSize());
            }
        };

        testInWorkerThread(mm, r);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testDisposeUnused() throws Exception {
        mm.getMonitoringConfig().addProbes(new MyMemoryMonitoringProbe());
        Runnable r = new Runnable() {

            @Override
            public void run() {
                // Initialize memory manager
                mm.allocate(33);

                final int initialSize = mm.getReadyThreadBufferSize();

                CompositeBuffer compositeBuffer = CompositeBuffer.newBuffer(mm);

                for (int i = 0; i < 11; i++) {
                    Buffer b = mm.allocate(1228);
                    b.allowBufferDispose(true);
                    compositeBuffer.append(b);
                }

                compositeBuffer.toByteBufferArray(0, 12280);
                compositeBuffer.limit(1228);

                compositeBuffer.shrink();

                assertEquals(initialSize - (1228 * 11 - 12280),
                        mm.getReadyThreadBufferSize());

                compositeBuffer.position(compositeBuffer.limit());
                compositeBuffer.shrink();

                assertEquals(initialSize,
                        mm.getReadyThreadBufferSize());
            }
        };

        testInWorkerThread(mm, r);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testReallocate() throws Exception {
        mm.getMonitoringConfig().addProbes(new MyMemoryMonitoringProbe());
        Runnable r = new Runnable() {

            @Override
            public void run() {
                final int allocSize = 16384;

                // Initialize memory manager
                mm.allocate(33);

                final int initialSize = mm.getReadyThreadBufferSize();

                Buffer buffer = mm.allocate(allocSize);
                assertEquals(
                        initialSize - allocSize,
                        mm.getReadyThreadBufferSize());

                buffer.position(allocSize / 2);
                buffer.trim();

                assertEquals(initialSize - allocSize / 2,
                        mm.getReadyThreadBufferSize());

                buffer.dispose();

                assertEquals(initialSize,
                        mm.getReadyThreadBufferSize());

                buffer = mm.allocate(allocSize / 2);
                assertEquals(initialSize - allocSize / 2,
                        mm.getReadyThreadBufferSize());

                buffer = mm.reallocate(buffer, allocSize);
                assertEquals(initialSize - allocSize,
                        mm.getReadyThreadBufferSize());

                buffer.dispose();

                assertEquals(initialSize,
                        mm.getReadyThreadBufferSize());
            }
        };

        testInWorkerThread(mm, r);
    }


    @Test
    @SuppressWarnings("unchecked")
    public void testCompositeBufferDispose() throws Exception {
        mm.getMonitoringConfig().addProbes(new MyMemoryMonitoringProbe());
        Runnable r = new Runnable() {

            @Override
            public void run() {
                // Initialize memory manager

                mm.allocate(33);

                final int initialSize = mm.getReadyThreadBufferSize();

                CompositeBuffer compositeBuffer = CompositeBuffer.newBuffer(mm);

                for (int i = 0; i < 3; i++) {
                    Buffer b = mm.allocate(100);
                    b.allowBufferDispose(true);
                    compositeBuffer.append(b);
                }

                compositeBuffer.toByteBuffer(0, 100);
                compositeBuffer.position(100);

                compositeBuffer.dispose();


                assertEquals(initialSize,
                        mm.getReadyThreadBufferSize());
            }
        };

        testInWorkerThread(mm, r);
    }

    @Test
    public void testByteOrderRestored() {
        Buffer b = mm.allocate(1024);
        if (b instanceof HeapBuffer) {
            b.order(ByteOrder.LITTLE_ENDIAN);
            assertEquals(ByteOrder.LITTLE_ENDIAN, b.order());
            b.dispose();
            assertEquals(ByteOrder.BIG_ENDIAN, b.order());
        }
    }

    private void testInWorkerThread(final MemoryManager mm,
                                    final Runnable task) throws Exception {
        final FutureImpl<Boolean> future = SafeFutureImpl.create();

        ThreadPoolConfig config = ThreadPoolConfig.defaultConfig();
        config.setMemoryManager(mm);
        ExecutorService threadPool = GrizzlyExecutorService.createInstance(config);

        threadPool.execute(new Runnable() {

            @Override
            public void run() {
                try {
                    task.run();
                    future.result(Boolean.TRUE);
                } catch (Throwable e) {
                    future.failure(e);
                }
            }
        });

        assertTrue(future.get(10, TimeUnit.SECONDS));
    }

    private static class MyMemoryMonitoringProbe implements MemoryProbe {

        @Override
        public void onBufferAllocateEvent(int size) {
            LOGGER.log(Level.INFO, "allocateNewBufferEvent: {0}", size);
        }

        @Override
        public void onBufferAllocateFromPoolEvent(int size) {
            LOGGER.log(Level.INFO, "allocateBufferFromPoolEvent: {0}", size);
        }

        @Override
        public void onBufferReleaseToPoolEvent(int size) {
            LOGGER.log(Level.INFO, "releaseBufferToPoolEvent: {0}", size);
        }
    }
}
