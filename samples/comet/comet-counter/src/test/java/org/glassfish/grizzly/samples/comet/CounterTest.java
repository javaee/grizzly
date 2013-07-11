/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011-2013 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.samples.comet;

import org.glassfish.grizzly.samples.comet.LongPollingServlet;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Collection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.glassfish.grizzly.comet.CometAddOn;

import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.grizzly.http.server.NetworkListener;
import org.glassfish.grizzly.servlet.WebappContext;
import org.glassfish.grizzly.servlet.ServletRegistration;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class CounterTest {
    public static final String QUERY_PATH = "/grizzly-comet-counter/long_polling";
    private HttpServer httpServer;
    private static final int PORT = 18893;
    private ExecutorService executorService = Executors.newFixedThreadPool(5);

    @Before
    public void setUp() throws Exception {
        httpServer = HttpServer.createSimpleServer("./", PORT);
        final WebappContext ctx =
                new WebappContext("Counter", "/grizzly-comet-counter");
        final ServletRegistration servletRegistration =
            ctx.addServlet("LongPollingServlet", LongPollingServlet.class);
        servletRegistration.addMapping("/long_polling");

        ctx.deploy(httpServer);

        final Collection<NetworkListener> listeners = httpServer.getListeners();
        for (NetworkListener listener : listeners) {
            listener.registerAddOn(new CometAddOn());
        }
        httpServer.start();
    }

    @After
    public void tearDown() {
        stopHttpServer();
    }
    
    @Test
    public void testCounter() throws IOException, ExecutionException, TimeoutException, InterruptedException {
        int attempt = 0;
        while (attempt++ < 20) {
            Future request = request(attempt);
            Thread.sleep(500);
            executorService.submit(new HttpRequest("POST")).get(10, TimeUnit.SECONDS);
            request.get(30, TimeUnit.SECONDS);
        }
    }

    private Future<?> request(int attempt) throws InterruptedException {
        final HttpRequest task = new HttpRequest("GET");
        final Future<?> future = executorService.submit(task);
        task.pause();
        return future;
    }

    private void stopHttpServer() {
        if (httpServer != null) {
            httpServer.shutdownNow();
        }
    }

    private class HttpRequest implements Runnable {
        private CountDownLatch connected = new CountDownLatch(1);
        private String method;
        private OutputStream os;

        public HttpRequest(String method) {
            this.method = method;
        }

        @Override
        public void run() {
            try {
                Socket socket = new Socket("localhost", PORT);
                try {
                    os = socket.getOutputStream();
                    write(method + " " + QUERY_PATH + " HTTP/1.1");
                    write("Host: localhost:" + PORT);
                    write("Connection: close");
                    write("");
                    connected.countDown();
                    readResponse(socket.getInputStream());
                } finally {
                    socket.close();
                }
            } catch (IOException e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        }

        public void readResponse(InputStream inputStream) throws IOException {
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            try {
                while(reader.readLine() != null);
            } finally {
                try {
                    reader.close();
                } catch (IOException e) {
                    throw new RuntimeException(e.getMessage(), e);
                }
            }
        }

        private void write(String line) throws IOException {
            os.write(line.getBytes());
            os.write("\r\n".getBytes());
            os.flush();
        }

        public void pause() throws InterruptedException {
            connected.await(10, TimeUnit.SECONDS);
        }
    }
}
