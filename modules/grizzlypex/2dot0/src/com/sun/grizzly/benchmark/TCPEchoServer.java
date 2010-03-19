/*
 *
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2007-2008 Sun Microsystems, Inc. All rights reserved.
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

package com.sun.grizzly.benchmark;

import java.lang.reflect.Constructor;
import com.sun.grizzly.Strategy;
import com.sun.grizzly.Transport;
import com.sun.grizzly.TransportFactory;
import com.sun.grizzly.filterchain.FilterChainBuilder;
import com.sun.grizzly.filterchain.TransportFilter;
import com.sun.grizzly.memory.DefaultMemoryManager;
import com.sun.grizzly.memory.MemoryManagerMonitoringProbe;
import com.sun.grizzly.nio.transport.TCPNIOTransport;
import com.sun.grizzly.threadpool.GrizzlyExecutorService;
import com.sun.grizzly.threadpool.ThreadPoolConfig;
import com.sun.grizzly.utils.EchoFilter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

/**
 *
 * @author oleksiys
 */
public class TCPEchoServer {
    public static void main(String[] args) throws Exception {
        Settings settings = Settings.parse(args);
        System.out.println(settings);
        
        TransportFactory transportFactory = TransportFactory.getInstance();

        MemoryStatsProbe probe = null;
        if (settings.isMonitoringMemory()) {
            probe = new MemoryStatsProbe();
            DefaultMemoryManager memoryManager = new DefaultMemoryManager(probe);
            transportFactory.setDefaultMemoryManager(memoryManager);
        }

        int poolSize = (settings.getWorkerThreads());

        final ThreadPoolConfig tpc = ThreadPoolConfig.DEFAULT.clone().
                setPoolName("Grizzly-BM").
                setCorePoolSize(poolSize).setMaxPoolSize(poolSize);

        FilterChainBuilder builder = FilterChainBuilder.stateless();
        builder.add(new TransportFilter());
        builder.add(new EchoFilter());

        TCPNIOTransport transport = transportFactory.createTCPTransport();
        transport.setProcessor(builder.build());
        transport.setThreadPool(GrizzlyExecutorService.createInstance(tpc));
        transport.setSelectorRunnersCount(settings.getSelectorThreads());

        Strategy strategy = loadStrategy(settings.getStrategyClass(), transport);

        transport.setStrategy(strategy);

        try {
            transport.bind(settings.getHost(), settings.getPort());
            transport.start();

            System.out.println("Press enter to stop the server...");
            System.in.read();
        } finally {
            transport.stop();
            TransportFactory.getInstance().close();
        }

        if (probe != null) {
            System.out.println("Memory stats:\n" + probe.toString());
        }
    }

    public static Strategy loadStrategy(Class<? extends Strategy> strategy, Transport transport) {
        try {
            return strategy.newInstance();
        } catch (Exception e) {
            try {
                Constructor[] cs = strategy.getConstructors();
                for (Constructor c : cs) {
                    if (c.getParameterTypes().length == 1 && c.getParameterTypes()[0].isAssignableFrom(ExecutorService.class)) {
                        return (Strategy) c.newInstance(transport.getThreadPool());
                    }
                }

                throw new IllegalStateException("Can not initialize strategy: " + strategy);
            } catch (Exception ee) {
                throw new IllegalStateException("Can not initialize strategy: " + strategy + ". Error: " + ee.getClass() + ": " + ee.getMessage());
            }
        }
    }

    public static class MemoryStatsProbe implements MemoryManagerMonitoringProbe {
        private final AtomicInteger allocatedNew = new AtomicInteger();
        private final AtomicInteger allocatedFromPool = new AtomicInteger();
        private final AtomicInteger releasedToPool = new AtomicInteger();
        
        public void allocateNewBufferEvent(int i) {
            allocatedNew.addAndGet(i);
        }

        public void allocateBufferFromPoolEvent(int i) {
            allocatedFromPool.addAndGet(i);
        }

        public void releaseBufferToPoolEvent(int i) {
            releasedToPool.addAndGet(i);
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("allocated-memory=").append(allocatedNew.get());
            sb.append(" allocated-from-pool=").append(allocatedFromPool.get());
            sb.append(" released-to-pool=").append(releasedToPool.get());

            return sb.toString();
        }
    }
}
