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

package org.glassfish.grizzly.benchmark;

import org.glassfish.grizzly.Strategy;
import org.glassfish.grizzly.Transport;
import org.glassfish.grizzly.TransportFactory;
import org.glassfish.grizzly.http.server.GrizzlyListener;
import org.glassfish.grizzly.http.server.GrizzlyWebServer;
import org.glassfish.grizzly.http.server.ServerConfiguration;
import org.glassfish.grizzly.memory.DefaultMemoryManager;
import org.glassfish.grizzly.memory.MemoryProbe;
import org.glassfish.grizzly.nio.NIOTransport;
import org.glassfish.grizzly.threadpool.GrizzlyExecutorService;
import org.glassfish.grizzly.threadpool.ThreadPoolConfig;
import java.lang.reflect.Constructor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicLong;

/**
 * HTTP GET server.
 * 
 * @author Alexey Stashok
 */
public class HttpGetServer {
    public static void main(String[] args) throws Exception {
        Settings settings = Settings.parse(args);
        System.out.println(settings);

        TransportFactory transportFactory = TransportFactory.getInstance();

        MemoryStatsProbe probe = null;
        if (settings.isMonitoringMemory()) {
            probe = new MemoryStatsProbe();
            DefaultMemoryManager memoryManager = new DefaultMemoryManager();
            memoryManager.getMonitoringConfig().addProbes(probe);
            transportFactory.setDefaultMemoryManager(memoryManager);
        }

        // create a basic server that listens on port.
        final GrizzlyWebServer server = new GrizzlyWebServer();

        final ServerConfiguration config = server.getServerConfiguration();
        config.setDocRoot(".");
        // Map the path, /echo, to the BlockingEchoAdapter
        config.addGrizzlyAdapter(new HttpGetAdapter(), "/");

        final GrizzlyListener listener =
                new GrizzlyListener("grizzly",
                                    settings.getHost(),
                                    settings.getPort());
        
        configureTransport(listener.getTransport(), settings);

        server.addListener(listener);

        try {
            server.start();
            System.out.println("Press enter to stop the server...");
            System.in.read();
        } finally {
            server.shutdownNow();
            TransportFactory.getInstance().close();
        }

        if (probe != null) {
            System.out.println("Memory stats:\n" + probe.toString());
        }
    }

    private static void configureTransport(NIOTransport transport, Settings settings) {
        int poolSize = (settings.getWorkerThreads());

        final ThreadPoolConfig tpc = ThreadPoolConfig.DEFAULT.clone().
                setPoolName("Grizzly-BM").
                setCorePoolSize(poolSize).setMaxPoolSize(poolSize);


        transport.setThreadPool(GrizzlyExecutorService.createInstance(tpc));
        transport.setSelectorRunnersCount(settings.getSelectorThreads());

        Strategy strategy = loadStrategy(settings.getStrategyClass(), transport);

        transport.setStrategy(strategy);

    }

    private static Strategy loadStrategy(Class<? extends Strategy> strategy, Transport transport) {
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

    private static class MemoryStatsProbe implements MemoryProbe {
        private final AtomicLong allocatedNew = new AtomicLong();
        private final AtomicLong allocatedFromPool = new AtomicLong();
        private final AtomicLong releasedToPool = new AtomicLong();

        public void onBufferAllocateEvent(int i) {
            allocatedNew.addAndGet(i);
        }

        public void onBufferAllocateFromPoolEvent(int i) {
            allocatedFromPool.addAndGet(i);
        }

        public void onBufferReleaseToPoolEvent(int i) {
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
