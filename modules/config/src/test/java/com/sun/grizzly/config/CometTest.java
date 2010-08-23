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

package com.sun.grizzly.config;

import com.sun.grizzly.comet.CometContext;
import com.sun.grizzly.comet.CometEngine;
import com.sun.grizzly.comet.CometEvent;
import com.sun.grizzly.comet.CometHandler;
import com.sun.grizzly.http.servlet.ServletAdapter;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

@SuppressWarnings({"IOResourceOpenedButNotSafelyClosed"})
@Test
public class CometTest extends BaseGrizzlyConfigTest {
    private GrizzlyConfig grizzlyConfig;

    @BeforeClass
    public void setup() {
        grizzlyConfig = new GrizzlyConfig("comet-config.xml");
        grizzlyConfig.setupNetwork();
        final GrizzlyServiceListener listener = grizzlyConfig.getListeners().get(0);
        listener.getEmbeddedHttp().setAdapter(new ServletAdapter(new CometEchoServlet()));
    }

    @AfterClass
    public void tearDown() {
        grizzlyConfig.shutdown();
    }

    public void echo() throws Exception {
            String urlStr = "http://localhost:38082/echo";
            int numOfClients = 10;
            CountDownLatch startSignal = new CountDownLatch(numOfClients);
            CountDownLatch endSignal = new CountDownLatch(numOfClients);
            ExecutorService executorService = Executors.newFixedThreadPool(numOfClients);
            List<Future<String>> futures = new ArrayList<Future<String>>();
            for (int i = 0; i < numOfClients; i++) {
                futures.add(executorService.submit(new Worker(urlStr, startSignal, endSignal)));
            }
            boolean ss = startSignal.await(numOfClients * 2000, TimeUnit.MILLISECONDS);
            String message = "abc";
            writeMessage(urlStr, message);
            boolean es = endSignal.await(numOfClients * 1000, TimeUnit.MILLISECONDS);
            boolean valid = true;
            for (Future<String> future : futures) {
                String returnedMessage = future.get(1000, TimeUnit.MILLISECONDS);
                valid = valid && (message.equals(returnedMessage));
            }
    }

    private void writeMessage(String urlStr, String message) throws Exception {
        OutputStream os;
        BufferedWriter bw = null;
        try {
            URL url = new URL(urlStr);
            String data = "msg=" + URLEncoder.encode(message, "UTF-8");
            HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setRequestMethod("POST");
            urlConnection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            urlConnection.setDoOutput(true);
            urlConnection.connect();
            os = urlConnection.getOutputStream();
            bw = new BufferedWriter(new OutputStreamWriter(os));
            bw.write(data);
            bw.flush();
            int statusCode = urlConnection.getResponseCode();
            if (HttpURLConnection.HTTP_OK != statusCode) {
                throw new IllegalStateException("Incorrect return code: " + statusCode);
            }
        } finally {
            if (bw != null) {
                bw.close();
            }
        }
    }

    private static class Worker implements Callable<String> {
        private String urlStr;
        private CountDownLatch startSignal;
        private CountDownLatch endSignal;

        public Worker(String urlStr, CountDownLatch startSignal,
            CountDownLatch endSignal) throws Exception {
            this.urlStr = urlStr;
            this.startSignal = startSignal;
            this.endSignal = endSignal;
        }

        public String call() throws IOException {
            String listenMessage;
            InputStream is;
            BufferedReader br = null;
            try {
                URL url = new URL(urlStr);
                HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.setRequestMethod("GET");
                urlConnection.connect();
                is = urlConnection.getInputStream();
                br = new BufferedReader(new InputStreamReader(is));
                // OK message
                startSignal.countDown();
                listenMessage = br.readLine();
                endSignal.countDown();
            } catch (Exception ex) {
                ex.printStackTrace();
                throw new IllegalStateException("Test UNPREDICTED-FAILURE");
            } finally {
                if (br != null) {
                    br.close();
                }
            }
            return listenMessage;
        }
    }

    public class CometEchoServlet extends HttpServlet {
        private String contextPath;

        public class ChatListenerHandler implements CometHandler<PrintWriter> {
            private PrintWriter writer;

            public void attach(PrintWriter writer) {
                this.writer = writer;
            }

            public void onEvent(CometEvent event) throws IOException {
                if (event.getType() == CometEvent.NOTIFY) {
                    String output = (String) event.attachment();
                    writer.println(output);
                    writer.flush();
                }
            }

            public void onInitialize(CometEvent event) throws IOException {
            }

            public void onInterrupt(CometEvent event) throws IOException {
                removeThisFromContext();
            }

            public void onTerminate(CometEvent event) throws IOException {
                removeThisFromContext();
            }

            private void removeThisFromContext() {
                writer.close();
                CometContext context = CometEngine.getEngine().getCometContext(contextPath);
                context.removeCometHandler(this);
            }
        }

        @Override
        public void init(ServletConfig config) throws ServletException {
            contextPath = config.getServletContext().getContextPath() + "/echo";
            CometContext context = CometEngine.getEngine().register(contextPath);
            context.setExpirationDelay(5 * 60 * 1000);
        }

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
            res.setContentType("text/html");
            res.setHeader("Cache-Control", "private");
            res.setHeader("Pragma", "no-cache");
            PrintWriter writer = res.getWriter();
            ChatListenerHandler handler = new ChatListenerHandler();
            handler.attach(writer);
            CometContext context = CometEngine.getEngine().register(contextPath);
            context.addCometHandler(handler);
            writer.println("OK");
            writer.flush();
        }

        @Override
        @SuppressWarnings("unchecked")
        protected void doPost(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
            res.setContentType("text/plain");
            res.setHeader("Cache-Control", "private");
            res.setHeader("Pragma", "no-cache");
            req.setCharacterEncoding("UTF-8");
            String message = req.getParameter("msg");
            CometContext context = CometEngine.getEngine().register(contextPath);
            context.notify(message);
            PrintWriter writer = res.getWriter();
            writer.println("OK");
            writer.flush();
        }
    }
}
