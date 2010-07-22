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

import com.sun.grizzly.threadpool.GrizzlyExecutorService;
import com.sun.grizzly.threadpool.ThreadPoolConfig;
import java.util.LinkedList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 *
 * @author gustav trede
 */
public class GrizzlyExecutorServiceTest extends GrizzlyTestCase {

    public GrizzlyExecutorServiceTest() {
    }

    public void testCreateInstance() throws Exception {
        int threads = 100;
        ThreadPoolConfig cfg = new ThreadPoolConfig("test", -1, threads,
                null, -1, 0, null, null, Thread.NORM_PRIORITY, null);
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
}