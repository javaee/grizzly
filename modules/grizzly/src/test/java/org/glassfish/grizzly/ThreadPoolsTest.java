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

package org.glassfish.grizzly;

import org.glassfish.grizzly.threadpool.AbstractThreadPool;
import org.glassfish.grizzly.threadpool.FixedThreadPool;
import org.glassfish.grizzly.threadpool.SyncThreadPool;
import org.glassfish.grizzly.threadpool.ThreadPoolConfig;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;

import static junit.framework.Assert.assertEquals;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import org.glassfish.grizzly.nio.transport.TCPNIOTransportBuilder;
import org.glassfish.grizzly.strategies.SameThreadIOStrategy;
import org.glassfish.grizzly.strategies.WorkerThreadIOStrategy;

public class ThreadPoolsTest {

    /**
     * Added for http://java.net/jira/browse/GRIZZLY-1435.
     * @throws Exception
     */
    @Test
    public void testThreadPoolCoreThreadInitialization() throws Exception {
        final ThreadPoolConfig config = ThreadPoolConfig.defaultConfig();
        config.setCorePoolSize(5);
        config.setMaxPoolSize(5);
        Field workers = AbstractThreadPool.class.getDeclaredField("workers");
        workers.setAccessible(true);

        final SyncThreadPool syncThreadPool = new SyncThreadPool(config);
        assertEquals("Pool did not properly initialize threads based on core pool size configuration.", 5, ((Map) workers.get(syncThreadPool)).size());

        config.setQueue(new ArrayBlockingQueue<Runnable>(5));
        final FixedThreadPool fixedThreadPool = new FixedThreadPool(config);
        assertEquals("Pool did not properly initialize threads based on core pool size configuration.", 5, ((Map) workers.get(fixedThreadPool)).size());
    }

    @Test
    public void testCustomThreadPoolSameThreadStrategy() throws Exception {

        final int poolSize = Math.max(Runtime.getRuntime().availableProcessors()/2, 1);
        final ThreadPoolConfig poolCfg = ThreadPoolConfig.defaultConfig();
        poolCfg.setCorePoolSize(poolSize).setMaxPoolSize(poolSize);

        final TCPNIOTransport tcpTransport = TCPNIOTransportBuilder.newInstance()
                .setReuseAddress(true)
                .setIOStrategy(SameThreadIOStrategy.getInstance())
                .setSelectorThreadPoolConfig(poolCfg)
                .setWorkerThreadPoolConfig(null)
                .build();
        try {
            tcpTransport.start();
        } finally {
            tcpTransport.shutdownNow();
        }
    }

    @Test
    public void testCustomThreadPoolWorkerThreadStrategy() throws Exception {

        final int selectorPoolSize =  Math.max(Runtime.getRuntime().availableProcessors()/2, 1);
        final ThreadPoolConfig selectorPoolCfg = ThreadPoolConfig.defaultConfig();
        selectorPoolCfg.setCorePoolSize(selectorPoolSize).setMaxPoolSize(selectorPoolSize);

        final int workerPoolSize = Runtime.getRuntime().availableProcessors() * 2 ;
        final ThreadPoolConfig workerPoolCfg = ThreadPoolConfig.defaultConfig();
        workerPoolCfg.setCorePoolSize(workerPoolSize).setMaxPoolSize(workerPoolSize);

       final TCPNIOTransport tcpTransport = TCPNIOTransportBuilder.newInstance()
                .setReuseAddress(true)
                .setIOStrategy(WorkerThreadIOStrategy.getInstance())
                .setSelectorThreadPoolConfig(selectorPoolCfg)
                .setWorkerThreadPoolConfig(workerPoolCfg)
                .build();
        try {
            tcpTransport.start();
        } finally {
            tcpTransport.shutdownNow();
        }
    }    
}
