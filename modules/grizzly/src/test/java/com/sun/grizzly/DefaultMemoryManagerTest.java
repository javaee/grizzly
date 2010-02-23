/*
 *
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2007-2010 Sun Microsystems, Inc. All rights reserved.
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
 *
 */

package com.sun.grizzly;

import java.util.concurrent.TimeUnit;
import com.sun.grizzly.impl.FutureImpl;
import com.sun.grizzly.memory.ByteBufferWrapper;
import com.sun.grizzly.memory.DefaultMemoryManager;
import com.sun.grizzly.threadpool.FixedThreadPool;
import com.sun.grizzly.threadpool.ThreadPoolConfig;
import java.util.concurrent.ExecutorService;

/**
 *
 * @author oleksiys
 */
public class DefaultMemoryManagerTest extends GrizzlyTestCase {
    public void testDispose() throws Exception {
        final DefaultMemoryManager mm = new DefaultMemoryManager();
        Runnable r = new Runnable() {
            @Override
            public void run() {
                final int allocSize = 16384;

                // Initialize memory manager
                mm.allocate(0);

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

        testInWorkerThread(r);
    }

    public void testTrimDispose() throws Exception {
        final DefaultMemoryManager mm = new DefaultMemoryManager();
        Runnable r = new Runnable() {
            @Override
            public void run() {
                final int allocSize = 16384;

                // Initialize memory manager
                mm.allocate(0);

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

        testInWorkerThread(r);
    }

    public void testReallocate() throws Exception {
        final DefaultMemoryManager mm = new DefaultMemoryManager();
        Runnable r = new Runnable() {
            @Override
            public void run() {
                final int allocSize = 16384;

                // Initialize memory manager
                mm.allocate(0);

                final int initialSize = mm.getReadyThreadBufferSize();

                ByteBufferWrapper buffer = mm.allocate(allocSize);
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

        testInWorkerThread(r);
    }

    private void testInWorkerThread(final Runnable task) throws Exception {
        final FutureImpl<Boolean> future = FutureImpl.<Boolean>create();

        ExecutorService threadPool = new FixedThreadPool(ThreadPoolConfig.DEFAULT);
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
}
