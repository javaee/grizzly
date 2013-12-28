/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved.
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
package org.glassfish.grizzly.http.get;

import org.glassfish.grizzly.IOStrategy;
import org.glassfish.grizzly.Transport;
import org.glassfish.grizzly.http.server.HttpHandler;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.grizzly.http.server.NetworkListener;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.http.server.Response;
import org.glassfish.grizzly.http.server.ServerConfiguration;
import org.glassfish.grizzly.http.util.ContentType;
import org.glassfish.grizzly.memory.MemoryProbe;
import org.glassfish.grizzly.nio.NIOTransport;
import org.glassfish.grizzly.strategies.SameThreadIOStrategy;
import org.glassfish.grizzly.threadpool.GrizzlyExecutorService;
import org.glassfish.grizzly.threadpool.ThreadPoolConfig;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicLong;
import org.glassfish.grizzly.utils.Charsets;

final class GetServer {
    private static final String LISTENER_NAME = "NetworkListenerBM";
    private static final String PATH = "/get";
    private static final String POOL_NAME = "GrizzlyPoolBM";
    private final HttpServer httpServer;
    private MemoryProbe probe;

    // -------------------------------------------------------- Constructors


    public GetServer(Settings settings) {
        httpServer = new HttpServer();
        final NetworkListener listener = new NetworkListener(LISTENER_NAME,
                                                             settings.getHost(),
                                                             settings.getPort());
        listener.getFileCache().setEnabled(false);
        httpServer.addListener(listener);
        configureServer(settings);
    }


    // ------------------------------------------------------ Public Methods

    @SuppressWarnings({"ResultOfMethodCallIgnored"})
    public static void main(String[] args) {
        final Settings settings = Settings.parse(args);
        final GetServer server = new GetServer(settings);
        try {
            server.run();
            System.out.println(settings.toString());
            Thread.currentThread().join();
        } catch (Exception ioe) {
            System.err.println(ioe);
            System.exit(1);
        } finally {
            try {
                server.stop();
            } catch (IOException ioe) {
                System.err.println(ioe);
            }
        }
    }

    public void run() throws IOException {
        httpServer.start();
    }

    public void stop() throws IOException {
        httpServer.shutdownNow();
        if (probe != null) {
            System.out.println(probe.toString());
        }
    }


    // --------------------------------------------------------- Private Methods


    @SuppressWarnings({"unchecked"})
    private void configureServer(final Settings settings) {
        final ServerConfiguration config = httpServer.getServerConfiguration();
        config.addHttpHandler(new GetHandler(), PATH);
        final Transport transport = httpServer.getListener(LISTENER_NAME).getTransport();

        httpServer.getListener(LISTENER_NAME).setMaxPendingBytes(-1);
        if (settings.isMonitoringMemory()) {
            probe = new MemoryStatsProbe();
            transport.getMemoryManager().getMonitoringConfig().addProbes(probe);
        }

        IOStrategy strategy = loadStrategy(settings.getStrategyClass());

        final int selectorCount = settings.getSelectorThreads();
        ((NIOTransport) transport).setSelectorRunnersCount(selectorCount);

        transport.setIOStrategy(strategy);
        final int poolSize = ((strategy instanceof SameThreadIOStrategy)
                ? selectorCount
                : settings.getWorkerThreads());
        settings.setWorkerThreads(poolSize);
        final ThreadPoolConfig tpc = ThreadPoolConfig.newConfig().
                setPoolName(POOL_NAME).
                setCorePoolSize(poolSize).setMaxPoolSize(poolSize);
        tpc.setMemoryManager(transport.getMemoryManager());
        transport.setWorkerThreadPool(GrizzlyExecutorService.createInstance(tpc));

    }


    private static IOStrategy loadStrategy(Class<? extends IOStrategy> strategy) {
        try {
            final Method m = strategy.getMethod("getInstance");
            return (IOStrategy) m.invoke(null);
        } catch (Exception e) {
            throw new IllegalStateException("Can not initialize IOStrategy: " + strategy + ". Error: " + e);
        }
    }


    // ---------------------------------------------------------- Nested Classes


    private static class GetHandler extends HttpHandler {

        private final byte[] data = "Hello World".getBytes(Charsets.ASCII_CHARSET);
        private final ContentType contentType = ContentType.newContentType("text/plain", "ASCII");

        // ------------------------------------------------- Methods HttpHandler


        @Override
        public void service(Request request, Response response)
        throws Exception {
            response.setContentType(contentType);
            response.getOutputStream().write(data);
        }

    } // END GetHandler


    public static class MemoryStatsProbe implements MemoryProbe {

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
            return "allocated-memory=" + allocatedNew.get()
                    + " allocated-from-pool=" + allocatedFromPool.get()
                    + " released-to-pool=" + releasedToPool.get();
        }
    }

} // END EchoServer
