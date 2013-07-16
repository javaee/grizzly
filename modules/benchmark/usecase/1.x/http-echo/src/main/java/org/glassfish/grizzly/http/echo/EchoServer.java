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

package org.glassfish.grizzly.http.echo;

import com.sun.grizzly.Controller;
import com.sun.grizzly.http.SelectorThread;
import com.sun.grizzly.http.StatsThreadPool;
import com.sun.grizzly.http.embed.GrizzlyWebServer;
import com.sun.grizzly.tcp.http11.GrizzlyAdapter;
import com.sun.grizzly.tcp.http11.GrizzlyRequest;
import com.sun.grizzly.tcp.http11.GrizzlyResponse;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.util.concurrent.TimeUnit;

final class EchoServer {

    private GrizzlyWebServer httpServer;
    private final Settings settings;

    // -------------------------------------------------------- Constructors


    public EchoServer(final Settings settings) {
        this.settings = settings;
        httpServer = new GrizzlyWebServer(settings.getPort());
        httpServer.addGrizzlyAdapter(new BlockingEchoAdapter(), new String[] { "/echo" });
        final int poolSize = (settings.getWorkerThreads() + settings.getSelectorThreads());
        final SelectorThread selector = httpServer.getSelectorThread();
        StatsThreadPool pool = new StatsThreadPool(poolSize, poolSize, -1, StatsThreadPool.DEFAULT_IDLE_THREAD_KEEPALIVE_TIMEOUT, TimeUnit.MILLISECONDS);
        pool.start();
        selector.setThreadPool(pool);
        selector.setUseChunking(settings.isChunked());
        final Controller controller = new Controller();
        controller.setReadThreadsCount(settings.getSelectorThreads() - 1);
        controller.setHandleReadWriteConcurrently(true);
        controller.useLeaderFollowerStrategy(settings.isUseLeaderFollower());
        selector.setController(controller);

    }


    // ------------------------------------------------------ Public Methods


    @SuppressWarnings({"ResultOfMethodCallIgnored"})
    public static void main(String[] args) {
        final Settings settings = Settings.parse(args);
        EchoServer server = new EchoServer(settings);
        try {
            server.run();
            System.out.println(settings.toString());
            Thread.currentThread().join();
        } catch (Exception ioe) {
            System.err.println(ioe);
            System.exit(1);
        } finally {
            try {
                server.shutdownNow();
            } catch (IOException ioe) {
                System.err.println(ioe);
            }
        }
    }

    public void run() throws IOException {
        httpServer.start();
        System.out.println("Echo Server listener on port: " + settings.getPort());
    }

    public void stop() throws IOException {
        httpServer.shutdownNow();
    }


    // ------------------------------------------------------ Nested Classes


    private final class BlockingEchoAdapter extends GrizzlyAdapter {


        // ---------------------------------------- Methods from HttpService


        /**
         * This method should contains the logic for any http extension to the
         * Grizzly HTTP Webserver.
         *
         * @param request  The  {@link com.sun.grizzly.tcp.http11.GrizzlyRequest}
         * @param response The  {@link com.sun.grizzly.tcp.http11.GrizzlyResponse}
         */
        @Override
        public void service(GrizzlyRequest request, GrizzlyResponse response) {
            try {
                if (settings.isBinary()) {
                    InputStream in = request.getInputStream();
                    OutputStream out = response.getOutputStream();
                    byte[] buf = new byte[1024];
                    int read;
                    try {
                        while ((read = in.read(buf)) != -1) {
                            out.write(buf, 0, read);
                        }
                    } finally {
                        in.close();
                        out.close();
                    }
                } else {
                    Reader in = request.getReader();
                    Writer out = response.getWriter();
                    char[] buf = new char[1024];
                    int read;
                    try {
                        while ((read = in.read(buf)) != -1) {
                            out.write(buf, 0, read);
                        }
                    } finally {
                        in.close();
                        out.close();
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

    } // END BlockingEchoAdapter


} // END EchoServer
