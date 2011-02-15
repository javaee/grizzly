/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2011 Oracle and/or its affiliates. All rights reserved.
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
package org.glassfish.grizzly.http.echo;

import org.glassfish.grizzly.IOStrategy;
import org.glassfish.grizzly.Transport;
import org.glassfish.grizzly.http.server.*;
import org.glassfish.grizzly.http.server.io.NIOInputStream;
import org.glassfish.grizzly.http.server.io.NIOReader;
import org.glassfish.grizzly.http.server.io.NIOWriter;
import org.glassfish.grizzly.http.server.io.ReadHandler;
import org.glassfish.grizzly.memory.MemoryProbe;
import org.glassfish.grizzly.nio.NIOTransport;
import org.glassfish.grizzly.strategies.SameThreadIOStrategy;
import org.glassfish.grizzly.threadpool.GrizzlyExecutorService;
import org.glassfish.grizzly.threadpool.ThreadPoolConfig;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicLong;
import org.glassfish.grizzly.http.server.io.NIOOutputStream;

final class EchoServer {
    private static final String LISTENER_NAME = "NetworkListenerBM";
    private static final String PATH = "/echo";
    private static final String POOL_NAME = "GrizzlyPoolBM";
    private final HttpServer httpServer;
    private final Settings settings;
    private MemoryProbe probe;

    // -------------------------------------------------------- Constructors


    public EchoServer(Settings settings) {
        this.settings = settings;
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
        final EchoServer server = new EchoServer(settings);
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
        httpServer.stop();
        if (probe != null) {
            probe.toString();
        }
    }


    // --------------------------------------------------------- Private Methods


    @SuppressWarnings({"unchecked"})
    private void configureServer(final Settings settings) {
        final ServerConfiguration config = httpServer.getServerConfiguration();
        config.addHttpHandler(((settings.isBlocking())
                                      ? new BlockingEchoHandler()
                                      : new NonBlockingEchoHandler()),
                                PATH);
        final Transport transport = httpServer.getListener(LISTENER_NAME).getTransport();
        if (settings.isMonitoringMemory()) {
            probe = new MemoryStatsProbe();
            transport.getMemoryManager().getMonitoringConfig().addProbes(probe);
        }

        IOStrategy strategy = loadStrategy(settings.getStrategyClass(), transport);

        final int selectorCount = settings.getSelectorThreads();
        ((NIOTransport) transport).setSelectorRunnersCount(selectorCount);

        transport.setIOStrategy(strategy);
        final int poolSize = ((strategy instanceof SameThreadIOStrategy)
                ? selectorCount
                : settings.getWorkerThreads());
        settings.setWorkerThreads(poolSize);
        final ThreadPoolConfig tpc = ThreadPoolConfig.defaultConfig().clone().
                setPoolName(POOL_NAME).
                setCorePoolSize(poolSize).setMaxPoolSize(poolSize);

        transport.setWorkerThreadPool(GrizzlyExecutorService.createInstance(tpc));

    }


    private static IOStrategy loadStrategy(Class<? extends IOStrategy> strategy, Transport transport) {
        try {
            final Method m = strategy.getMethod("getInstance");
            return (IOStrategy) m.invoke(null);
        } catch (Exception e) {
            throw new IllegalStateException("Can not initialize IOStrategy: " + strategy + ". Error: " + e);
        }
    }


    // ----------------------------------------------------------- Inner Classes


    private final class BlockingEchoHandler extends HttpHandler {


        // ---------------------------------------- Methods from HttpHandler

        @Override
        public void service(Request request, Response response)
        throws Exception {

            if (!settings.isChunked()) {
                response.setContentLength(request.getContentLength());
            }
            if (settings.isBinary()) {
                NIOInputStream in = request.getInputStream(true);
                NIOOutputStream out = response.getOutputStream();
                byte[] buf = new byte[1024];
                int read;
                int total = 0;
                try {
                    while ((read = in.read(buf)) != -1) {
                        total += read;
                        out.write(buf, 0, read);
                        if (total > 1024) {
                            total = 0;
                            out.flush();
                        }
                    }
                } finally {
                    in.close();
                    out.close();
                }
            } else {
                Reader in = request.getReader(true);
                Writer out = response.getWriter();
                char[] buf = new char[1024];
                int read;
                int total = 0;
                try {
                    while ((read = in.read(buf)) != -1) {
                        total += read;
                        out.write(buf, 0, read);
                        if (total > 1024) {
                            total = 0;
                            out.flush();
                        }
                    }
                } finally {
                    in.close();
                    out.close();
                }
            }
        }

    } // END BlockingEchoHandler


    public final class NonBlockingEchoHandler extends HttpHandler {


        // ---------------------------------------- Methods from HttpHandler

        @Override
        public void service(final Request request, final Response response)
        throws Exception {

            if (!settings.isChunked()) {
                response.setContentLength(request.getContentLength());
            }
            if (settings.isBinary()) {
                final NIOInputStream in = request.getInputStream(false);
                final OutputStream out = response.getOutputStream();
                final byte[] buf = new byte[1024];
                do {
                    // continue reading ready data until no more can be read without
                    // blocking
                    while (in.isReady()) {
                        int read = in.read(buf);
                        if (read == -1) {
                            break;
                        }
                        out.write(buf, 0, read);
                    }

                    if (in.isFinished()) {
                        break;
                    }

                    // try to install a ReadHandler.  If this fails,
                    // continue at the top of the loop and read available data,
                    // otherwise break the look and allow the handler to wait for
                    // data to become available.
                    boolean installed = in.notifyAvailable(new ReadHandler() {

                        @Override
                        public void onDataAvailable() {
                            doWrite(this, in, buf, out, false);
                        }

                        @Override
                        public void onError(Throwable t) {
                        }

                        @Override
                        public void onAllDataRead() {
                            doWrite(this, in, buf, out, true);
                            response.resume();
                        }
                    });

                    if (installed) {
                        break;
                    }
                } while (!in.isFinished());
                if (!in.isFinished()) {
                    response.suspend();
                }
            } else {
                final NIOReader in = request.getReader(false);
                final NIOWriter out = response.getWriter();
                final char[] buf = new char[1024];
                do {
                    // continue reading ready data until no more can be read without
                    // blocking
                    while (in.isReady()) {
                        int read = in.read(buf);
                        if (read == -1) {
                            break;
                        }
                        out.write(buf, 0, read);
                    }

                    if (in.isFinished()) {
                        break;
                    }

                    // try to install a ReadHandler.  If this fails,
                    // continue at the top of the loop and read available data,
                    // otherwise break the look and allow the handler to wait for
                    // data to become available.
                    boolean installed = in.notifyAvailable(new ReadHandler() {

                        @Override
                        public void onDataAvailable() {
                            doWrite(this, in, buf, out, false);
                        }

                        @Override
                        public void onError(Throwable t) {
                        }

                        @Override
                        public void onAllDataRead() {
                            doWrite(this, in, buf, out, true);
                            response.resume();
                        }
                    });

                    if (installed) {
                        break;
                    }
                } while (!in.isFinished());

                if (!in.isFinished()) {
                    response.suspend();
                }
            }
        }

        // ----------------------------------------------------- Private Methods

        private void doWrite(ReadHandler handler,
                             NIOInputStream in,
                             byte[] buf,
                             OutputStream out,
                             boolean flush) {
            try {
                if (in.readyData() <= 0) {
                    if (flush) {
                        out.flush();
                    }
                    return;
                }
                for (;;) {
                    int len = in.read(buf);
                    out.write(buf, 0, len);
                    if (flush) {
                        out.flush();
                    }
                    if (!in.isReady()) {
                        // no more data is available, isntall the handler again.
                        in.notifyAvailable(handler);
                        break;
                    }
                }
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
        }

        private void doWrite(ReadHandler handler,
                             NIOReader in,
                             char[] buf,
                             Writer out,
                             boolean flush) {
            try {
                if (in.readyData() <= 0) {
                    if (flush) {
                        out.flush();
                    }
                    return;
                }
                for (;;) {
                    int len = in.read(buf);
                    out.write(buf, 0, len);
                    if (flush) {
                        out.flush();
                    }
                    if (!in.isReady()) {
                        // no more data is available, isntall the handler again.
                        in.notifyAvailable(handler);
                        break;
                    }
                }
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
        }

    } // END NonBlockingEchoHandler


    // ---------------------------------------------------------- Nested Classes


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
            StringBuilder sb = new StringBuilder();
            sb.append("allocated-memory=").append(allocatedNew.get());
            sb.append(" allocated-from-pool=").append(allocatedFromPool.get());
            sb.append(" released-to-pool=").append(releasedToPool.get());

            return sb.toString();
        }
    }

} // END EchoServer
