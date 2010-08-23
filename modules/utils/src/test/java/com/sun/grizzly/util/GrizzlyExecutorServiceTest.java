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

package com.sun.grizzly.util;

import java.util.LinkedList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author gustav trede
 */
public class GrizzlyExecutorServiceTest {

    public GrizzlyExecutorServiceTest() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
    }

    @Before
    public void setUp() {
    }

    @After
    public void tearDown() {
    }

    @Test
    public void testCreateInstance() throws Exception {
        int threads = 100;
        ThreadPoolConfig cfg = new ThreadPoolConfig("test", -1, threads,
                null, -1, 0, null, null, Thread.NORM_PRIORITY, null);
        GrizzlyExecutorService r = GrizzlyExecutorService.createInstance(cfg);        
        final int tasks = 2000000;
        doTest(r,tasks);
        assertTrue(r.getMaximumPoolSize() == threads);
        assertTrue(r.getActiveCount() == 0);
        assertTrue(r.getCompletedTaskCount() == 0);
        assertTrue(r.getMaxQueuedTasksCount() == cfg.getQueueLimit());
        int jver = Integer.valueOf(System.getProperty("java.version").substring(0,3).replace(".", ""));
        assertTrue(jver < 16 || r.getQueue().getClass().getSimpleName().contains("LinkedTransferQueue"));

        doTest(r.reconfigure(r.getConfiguration().setQueueLimit(tasks)),tasks);
        assertTrue(r.getMaxQueuedTasksCount() == tasks);

        int coresize = r.getConfiguration().getMaxPoolSize()+1;
        doTest( r.reconfigure(r.getConfiguration().
                setQueue(new LinkedList<Runnable>()).
                setCorePoolSize(coresize).
                setKeepAliveTime(1, TimeUnit.MILLISECONDS).
                setMaxPoolSize(threads+=50)),tasks);
        assertTrue(r.getQueue().getClass().getSimpleName().contains("LinkedList"));
        assertTrue(r.getName() == cfg.getPoolName());
        assertTrue(r.getMaxQueuedTasksCount() == tasks);
        assertTrue(r.getCorePoolSize() == coresize);
        assertTrue(r.getMaximumPoolSize() == threads);
        r.shutdownNow();
       /* long a = r.getCompletedTaskCount();
        assertTrue(a+"!="+tasks,a == tasks);*/
    }

    private void doTest(GrizzlyExecutorService r,int tasks) throws Exception{        
        Utils.dumpErr("threadpool queueImpl : "+r.getQueue().getClass());
        final CountDownLatch cl = new CountDownLatch(tasks);
        while(tasks-->0){
            r.execute(new Runnable() {
                public void run() {
                  cl.countDown();
                }
            });
        }
        assertTrue("latch timed out",cl.await(30, TimeUnit.SECONDS));
    }
}
