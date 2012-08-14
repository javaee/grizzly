/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012 Oracle and/or its affiliates. All rights reserved.
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
package org.glassfish.grizzly.servlet.async;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.AsyncContext;
import javax.servlet.AsyncEvent;
import javax.servlet.AsyncListener;
import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.servlet.HttpServerAbstractTest;
import org.glassfish.grizzly.servlet.ServletRegistration;
import org.glassfish.grizzly.servlet.WebappContext;

/**
 * Basic {@link AsyncContext} tests.
 */
public class AsyncContextTest extends HttpServerAbstractTest {
    private static final Logger LOGGER = Grizzly.logger(AsyncContextTest.class);
    
    public static final int PORT = 18890 + 15;

    public void testAsyncContextComplete() throws IOException {
        System.out.println("testAsyncContextComplete");
        try {
            newHttpServer(PORT);
            WebappContext ctx = new WebappContext("Test", "/contextPath");
            addServlet(ctx, "foobar", "/servletPath/*", new HttpServlet() {

                @Override
                protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
                    if (!req.isAsyncSupported()) {
                        throw new ServletException("Async not supported when it should");
                    }

                    final AsyncContext ac = req.startAsync();

                    Timer asyncTimer = new Timer("AsyncTimer", true);
                    asyncTimer.schedule(
                            new TimerTask() {
                                @Override
                                public void run() {
                                    try {
                                        final ServletResponse response = ac.getResponse();
                                        response.setContentType("text/plain");
                                        final PrintWriter writer = response.getWriter();
                                        writer.println("Hello world");
                                        ac.complete();
                                    } catch (IOException ioe) {
                                        ioe.printStackTrace();
                                    }
                                }
                            },
                            1000);
                }
            });
            ctx.deploy(httpServer);
            httpServer.start();
            HttpURLConnection conn = getConnection("/contextPath/servletPath/pathInfo", PORT);
            assertEquals(200, conn.getResponseCode());
            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            assertEquals("Hello world", reader.readLine());
        } finally {
            stopHttpServer();
        }
    }
    
    public void testAsyncListenerOnComplete() throws IOException {
        System.out.println("testAsyncListenerOnComplete");
        try {
            newHttpServer(PORT);
            WebappContext ctx = new WebappContext("Test", "/contextPath");
            addServlet(ctx, "foobar", "/servletPath/*", new HttpServlet() {

                @Override
                protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
                    if (!req.isAsyncSupported()) {
                        throw new ServletException("Async not supported when it should");
                    }

                    AsyncContext ac = req.startAsync(req, resp);
                    ac.addListener(ac.createListener(MyAsyncListener.class));
                    ac.complete();
                }
            });
            ctx.deploy(httpServer);
            httpServer.start();
            HttpURLConnection conn = getConnection("/contextPath/servletPath/pathInfo", PORT);
            assertEquals(200, conn.getResponseCode());
            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            assertEquals("onComplete", reader.readLine());
        } finally {
            stopHttpServer();
        }
    }

    public void testAsyncListenerOnTimeout() throws IOException {
        System.out.println("testAsyncListenerOnTimeout");
        try {
            newHttpServer(PORT);
            WebappContext ctx = new WebappContext("Test", "/contextPath");
            addServlet(ctx, "foobar", "/servletPath/*", new HttpServlet() {

                @Override
                protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
                    if (!req.isAsyncSupported()) {
                        throw new ServletException("Async not supported when it should");
                    }

                    AsyncContext ac = req.startAsync(req, resp);
                    ac.setTimeout(1000);
                    ac.addListener(ac.createListener(MyAsyncListener.class));
                }
            });
            ctx.deploy(httpServer);
            httpServer.start();
            HttpURLConnection conn = getConnection("/contextPath/servletPath/pathInfo", PORT);
            assertEquals(200, conn.getResponseCode());
            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            assertEquals("onTimeout", reader.readLine());
        } finally {
            stopHttpServer();
        }
    }

    private ServletRegistration addServlet(final WebappContext ctx,
            final String name,
            final String alias,
            Servlet servlet
            ) {
        
        final ServletRegistration reg = ctx.addServlet(name, servlet);
        reg.addMapping(alias);

        return reg;
    }
    
    public static class MyAsyncListener implements AsyncListener {

        @Override
        public void onComplete(AsyncEvent event) throws IOException {
            event.getAsyncContext().getResponse().getWriter().println("onComplete");
        }

        @Override
        public void onTimeout(AsyncEvent event) throws IOException {
            event.getAsyncContext().getResponse().getWriter().println("onTimeout");
            event.getAsyncContext().complete();
        }

        @Override
        public void onError(AsyncEvent event) throws IOException {
        }

        @Override
        public void onStartAsync(AsyncEvent event) throws IOException {
            event.getAsyncContext().getResponse().getWriter().println("onStartAsync");
        }
    }
    
}
