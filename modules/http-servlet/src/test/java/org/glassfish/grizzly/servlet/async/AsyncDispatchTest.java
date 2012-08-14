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
import java.net.HttpURLConnection;
import java.util.Enumeration;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Logger;
import javax.servlet.AsyncContext;
import javax.servlet.Servlet;
import javax.servlet.ServletException;
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
public class AsyncDispatchTest extends HttpServerAbstractTest {
    private static final Logger LOGGER = Grizzly.logger(AsyncDispatchTest.class);
    
    public static final int PORT = 18890 + 16;

    public void testAsyncContextDispatchZeroArg() throws IOException {
        System.out.println("testAsyncContextDispatchZeroArg");
        try {
            newHttpServer(PORT);
            WebappContext ctx = new WebappContext("Test", "/contextPath");
            addServlet(ctx, "foobar", "/servletPath/*", new HttpServlet() {

                @Override
                protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
                    if (!req.isAsyncSupported()) {
                        throw new ServletException("Async not supported when it should");
                    }
                    if (!"MYVALUE".equals(req.getAttribute("MYNAME"))) {
                        final AsyncContext ac = req.startAsync();
                        req.setAttribute("MYNAME", "MYVALUE");

                        Timer asyncTimer = new Timer("AsyncTimer", true);
                        asyncTimer.schedule(
                                new TimerTask() {
                                    @Override
                                    public void run() {
                                        ac.dispatch();
                                    }
                                },
                                1000);
                    } else {
                        // Async re-dispatched request
                        res.getWriter().println("Hello world");
                    }
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
    
    public void testAsyncContextDispatchToPath() throws IOException {
        System.out.println("testAsyncContextDispatchToPath");
        try {
            newHttpServer(PORT);
            WebappContext ctx = new WebappContext("Test", "/contextPath");
            addServlet(ctx, "TestServlet", "/TestServlet", new HttpServlet() {

                @Override
                protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
                    if (!req.isAsyncSupported()) {
                        throw new ServletException("Async not supported when it should");
                    }

                    final AsyncContext ac = req.startAsync();
                    req.setAttribute("MYNAME", "MYVALUE");

                    final String target = req.getParameter("target");

                    Timer asyncTimer = new Timer("AsyncTimer", true);
                    asyncTimer.schedule(
                            new TimerTask() {
                                @Override
                                public void run() {
                                    ac.dispatch(target);
                                }
                            },
                            1000);
                }
            });
            addServlet(ctx, "DispatchTarget", "/DispatchTargetWithPath", new HttpServlet() {
                private static final String EXPECTED_ASYNC_REQUEST_URI =
                    "/contextPath/TestServlet";

                private static final String EXPECTED_ASYNC_SERVLET_PATH =
                    "/TestServlet";

                private static final String EXPECTED_ASYNC_QUERY_STRING =
                    "target=DispatchTargetWithPath";
                @Override
                protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
                    Enumeration<String> attrNames = req.getAttributeNames();
                    if (attrNames == null) {
                        throw new ServletException("Missing ASYNC dispatch related "
                                + "request attributes");
                    }

                    if (!"MYVALUE".equals(req.getAttribute("MYNAME"))) {
                        throw new ServletException("Missing custom request attribute");
                    }

                    int asyncRequestAttributeFound = 0;
                    while (attrNames.hasMoreElements()) {
                        String attrName = attrNames.nextElement();
                        if (AsyncContext.ASYNC_REQUEST_URI.equals(attrName)) {
                            if (!EXPECTED_ASYNC_REQUEST_URI.equals(
                                    req.getAttribute(attrName))) {
                                throw new ServletException("Wrong value for "
                                        + AsyncContext.ASYNC_REQUEST_URI
                                        + " request attribute. Found: "
                                        + req.getAttribute(attrName) + ", expected: "
                                        + EXPECTED_ASYNC_REQUEST_URI);
                            }
                            asyncRequestAttributeFound++;
                        } else if (AsyncContext.ASYNC_CONTEXT_PATH.equals(attrName)) {
                            if (!getServletContext().getContextPath().equals(
                                    req.getAttribute(attrName))) {
                                throw new ServletException("Wrong value for "
                                        + AsyncContext.ASYNC_CONTEXT_PATH
                                        + " request attribute. Found: "
                                        + req.getAttribute(attrName) + ", expected: "
                                        + getServletContext().getContextPath());
                            }
                            asyncRequestAttributeFound++;
                        } else if (AsyncContext.ASYNC_PATH_INFO.equals(attrName)) {
                            if (req.getAttribute(attrName) != null) {
                                throw new ServletException("Wrong value for "
                                        + AsyncContext.ASYNC_PATH_INFO
                                        + " request attribute");
                            }
                            asyncRequestAttributeFound++;
                        } else if (AsyncContext.ASYNC_SERVLET_PATH.equals(attrName)) {
                            if (!EXPECTED_ASYNC_SERVLET_PATH.equals(
                                    req.getAttribute(attrName))) {
                                throw new ServletException("Wrong value for "
                                        + AsyncContext.ASYNC_SERVLET_PATH
                                        + " request attribute. Found "
                                        + req.getAttribute(attrName) + ", expected: "
                                        + EXPECTED_ASYNC_SERVLET_PATH);
                            }
                            asyncRequestAttributeFound++;
                        } else if (AsyncContext.ASYNC_QUERY_STRING.equals(attrName)) {
                            if (!EXPECTED_ASYNC_QUERY_STRING.equals(
                                    req.getAttribute(attrName))) {
                                throw new ServletException("Wrong value for "
                                        + AsyncContext.ASYNC_QUERY_STRING
                                        + " request attribute. Found: "
                                        + req.getAttribute(attrName) + ", expected: "
                                        + EXPECTED_ASYNC_QUERY_STRING);
                            }
                            asyncRequestAttributeFound++;
                        }
                    }

                    if (asyncRequestAttributeFound != 5) {
                        throw new ServletException("Wrong number of ASYNC dispatch "
                                + "related request attributes");
                    }

                    res.getWriter().println("Hello world");
                }
            });
            
            ctx.deploy(httpServer);
            httpServer.start();
            HttpURLConnection conn = getConnection("/contextPath/TestServlet?target=DispatchTargetWithPath", PORT);
            assertEquals(200, conn.getResponseCode());
            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            assertEquals("Hello world", reader.readLine());
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
}
