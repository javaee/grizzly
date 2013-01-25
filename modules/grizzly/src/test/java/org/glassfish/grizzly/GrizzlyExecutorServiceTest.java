/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2013 Oracle and/or its affiliates. All rights reserved.
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

import java.util.concurrent.ExecutorService;
import org.glassfish.grizzly.threadpool.GrizzlyExecutorService;
import org.glassfish.grizzly.threadpool.ThreadPoolConfig;
import java.util.LinkedList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.glassfish.grizzly.threadpool.ThreadPoolProbe;
import org.glassfish.grizzly.utils.DelayedExecutor;

/**
 *
 * @author gustav trede
 */
public class GrizzlyExecutorServiceTest extends GrizzlyTestCase {

    public GrizzlyExecutorServiceTest() {
    }

    public void testCreateInstance() throws Exception {
        int threads = 100;
        ThreadPoolConfig cfg = ThreadPoolConfig.defaultConfig()
                .setPoolName("test")
                .setCorePoolSize(-1).setMaxPoolSize(threads)
                .setQueue(null).setQueueLimit(-1)
                .setKeepAliveTime(-1, TimeUnit.MILLISECONDS)
                .setPriority(Thread.NORM_PRIORITY)
                .setTransactionTimeout(-1, TimeUnit.MILLISECONDS);
        
        GrizzlyExecutorService r = GrizzlyExecutorService.createInstance(cfg);
        final int tasks = 2000000;
        doTest(r,tasks);

        final ThreadPoolConfig config1 = r.getConfiguration();
        assertTrue(config1.getMaxPoolSize() == threads);
        assertTrue(config1.getQueueLimit() == cfg.getQueueLimit());
        assertTrue(config1.getQueue().getClass().getSimpleName().contains("LinkedTransferQueue"));

        doTest(r.reconfigure(r.getConfiguration().setQueueLimit(tasks)),tasks);
        final ThreadPoolConfig config2 = r.getConfiguration();
        assertTrue(config2.getQueueLimit() == tasks);

        int coresize = r.getConfiguration().getMaxPoolSize()+1;
        doTest( r.reconfigure(r.getConfiguration().
                setQueue(new LinkedList<Runnable>()).
                setCorePoolSize(coresize).
                setKeepAliveTime(1, TimeUnit.MILLISECONDS).
                setMaxPoolSize(threads+=50)),tasks);
        final ThreadPoolConfig config3 = r.getConfiguration();

        assertTrue(config3.getQueue().getClass().getSimpleName().contains("LinkedList"));
        assertEquals(config3.getPoolName(), cfg.getPoolName());
        assertTrue(config3.getQueueLimit() == tasks);
        assertTrue(config3.getCorePoolSize() == coresize);
        assertTrue(config3.getMaxPoolSize() == threads);
        r.shutdownNow();
       /* long a = r.getCompletedTaskCount();
        assertTrue(a+"!="+tasks,a == tasks);*/
    }

    public void testTransactionTimeout() throws Exception {
        final ExecutorService threadPool = Executors.newSingleThreadExecutor();

        try {
            final DelayedExecutor delayedExecutor =
                    new DelayedExecutor(threadPool);

            final int tasksNum = 10;
            final long transactionTimeoutMillis = 5000;

            final CountDownLatch cdl = new CountDownLatch(tasksNum);
            final ThreadPoolConfig tpc = ThreadPoolConfig.defaultConfig().copy()
                    .setTransactionTimeout(delayedExecutor, transactionTimeoutMillis, TimeUnit.MILLISECONDS)
                    .setCorePoolSize(tasksNum / 2).setMaxPoolSize(tasksNum / 2);

            final GrizzlyExecutorService ges = GrizzlyExecutorService.createInstance(tpc);

            for (int i = 0; i < tasksNum; i++) {
                ges.execute(new Runnable() {

                    @Override
                    public void run() {
                        try {
                            Thread.sleep(transactionTimeoutMillis);
                        } catch (InterruptedException e) {
                            cdl.countDown();
                        }
                    }
                });
            }

            cdl.await(transactionTimeoutMillis * 3 / 2, TimeUnit.MILLISECONDS);
        } finally {
            threadPool.shutdownNow();
        }
    }

    public void testAwaitTermination() throws Exception {
        int threads = 100;
        ThreadPoolConfig cfg = ThreadPoolConfig.defaultConfig()
                .setPoolName("test")
                .setCorePoolSize(-1).setMaxPoolSize(threads)
                .setQueue(null).setQueueLimit(-1)
                .setKeepAliveTime(-1, TimeUnit.MILLISECONDS)
                .setPriority(Thread.NORM_PRIORITY)
                .setTransactionTimeout(-1, TimeUnit.MILLISECONDS);

        GrizzlyExecutorService r = GrizzlyExecutorService.createInstance(cfg);
        final int tasks = 2000;
        runTasks(r,tasks);
        r.shutdown();
        assertTrue(r.awaitTermination(10, TimeUnit.SECONDS));
        assertTrue(r.isTerminated());

        r = GrizzlyExecutorService.createInstance(cfg.setQueueLimit(tasks));
        runTasks(r,tasks);
        r.shutdown();
        assertTrue(r.awaitTermination(10, TimeUnit.SECONDS));
        assertTrue(r.isTerminated());
        
        int coresize = cfg.getMaxPoolSize()+1;
        r = GrizzlyExecutorService.createInstance(cfg.setQueueLimit(tasks)
                .setQueue(new LinkedList<Runnable>())
                .setCorePoolSize(coresize)
                .setKeepAliveTime(1, TimeUnit.MILLISECONDS)
                .setMaxPoolSize(threads+=50));
        doTest(r,tasks);
        runTasks(r,tasks);
        r.shutdown();
        assertTrue(r.awaitTermination(10, TimeUnit.SECONDS));
        assertTrue(r.isTerminated());
    }
    
    public void testMonitoringProbesCopying() {
        final ThreadPoolProbe probe = new ThreadPoolProbe.Adapter();
        
        final ThreadPoolConfig tpc1 = ThreadPoolConfig.defaultConfig().copy();
        tpc1.getInitialMonitoringConfig().addProbes(probe);

        final ThreadPoolConfig tpc2 = tpc1.copy();
        
        assertFalse(tpc1.getInitialMonitoringConfig().getProbes().length == 0);
        assertFalse(tpc2.getInitialMonitoringConfig().getProbes().length == 0);
        
        tpc1.getInitialMonitoringConfig().removeProbes(probe);

        assertTrue(tpc1.getInitialMonitoringConfig().getProbes().length == 0);
        assertFalse(tpc2.getInitialMonitoringConfig().getProbes().length == 0);
    }
    
    public void testThreadPoolConfig() throws Exception {
        ThreadPoolConfig defaultThreadPool = ThreadPoolConfig.defaultConfig();
        assertNotNull(defaultThreadPool);
        assertNotNull(defaultThreadPool.toString());
    }
    
    private void doTest(GrizzlyExecutorService r, int tasks) throws Exception{
        final CountDownLatch cl = new CountDownLatch(tasks);
        while(tasks-->0){
            r.execute(new Runnable() {
                @Override
                public void run() {
                  cl.countDown();
                }
            });
        }
        assertTrue("latch timed out",cl.await(30, TimeUnit.SECONDS));
    }
    
    private void runTasks(GrizzlyExecutorService r, int tasks) throws Exception{
        while(tasks-->0){
            r.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        Thread.sleep(50);
                    } catch (Exception ignore) {
                    }
                }
            });
        }
    }
    
}
