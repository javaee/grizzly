/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2009-2012 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.servlet;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.http.server.Response;

/**
 * Basic Servlet Test.
 *
 * @author Jeanfrancois Arcand
 */
public class BasicServletTest extends HttpServerAbstractTest {

    public static final int PORT = 18890;
    private static final Logger LOGGER = Grizzly.logger(BasicServletTest.class);
    private final String header = "text/html;charset=utf8";

    public void testServletName() throws IOException {
        LOGGER.fine("testServletName");
        try {
            newHttpServer(PORT);
            WebappContext ctx = new WebappContext("Test", "/contextPath");
            addServlet(ctx, "foobar", "/servletPath/*");
            ctx.deploy(httpServer);
            httpServer.start();
            HttpURLConnection conn = getConnection("/contextPath/servletPath/pathInfo", PORT);
            String s = conn.getHeaderField("Servlet-Name");
            assertEquals(s, "foobar");
        } finally {
            stopHttpServer();
        }
    }

    public void testSetHeaderTest() throws IOException {
        LOGGER.fine("testSetHeaderTest");
        try {
            startHttpServer(PORT);
            WebappContext ctx = new WebappContext("Test");
            String alias = "/1";
            addServlet(ctx, "TestServlet", alias);
            ctx.deploy(httpServer);
            HttpURLConnection conn = getConnection(alias, PORT);
            String s = conn.getHeaderField("Content-Type");
            assertEquals(header, s);
        } finally {
            stopHttpServer();
        }
    }

    public void testPathInfo() throws IOException {
        LOGGER.fine("testPathInfo");
        try {
            newHttpServer(PORT);
            WebappContext ctx = new WebappContext("Test", "/contextPath");
            addServlet(ctx, "TestServlet", "/servletPath/*");
            ctx.deploy(httpServer);
            httpServer.start();
            HttpURLConnection conn = getConnection("/contextPath/servletPath/pathInfo", PORT);
            String s = conn.getHeaderField("Path-Info");
            assertEquals("/pathInfo", s);
        } finally {
            stopHttpServer();
        }
    }

//    public void testNotAllowEncodedSlash() throws IOException {
//        LOGGER.fine("testNotAllowEncodedSlash");
//        try {
//            newHttpServer(PORT);
//            String alias = "/contextPath/servletPath/";
//            ServletHandler servletHandler = addHttpHandler(alias);
//            servletHandler.setContextPath("/contextPath");
//            servletHandler.setServletPath("/servletPath");
//            httpServer.start();
//            HttpURLConnection conn = getConnection("/contextPath/servletPath%5FpathInfo", PORT);
//            String s = conn.getHeaderField("Path-Info");
//            assertNotSame(s, "/pathInfo");
//        } finally {
//            stopHttpServer();
//        }
//    }
//
//    public void testAllowEncodedSlash() throws IOException {
//        LOGGER.fine("testAllowEncodedSlash");
//        try {
//            newHttpServer(PORT);
//            String alias = "/contextPath/servletPath/";
//            ServletHandler servletHandler = addHttpHandler(alias);
//            servletHandler.setAllowEncodedSlash(true);
//            servletHandler.setContextPath("/contextPath");
//            servletHandler.setServletPath("/servletPath");
//            httpServer.start();
//            HttpURLConnection conn = getConnection("/contextPath/servletPath%5FpathInfo", PORT);
//            String s = conn.getHeaderField("Path-Info");
//            assertNotSame(s, "/pathInfo");
//        } finally {
//            stopHttpServer();
//        }
//    }

    public void testDoubleSlash() throws IOException {
        LOGGER.fine("testDoubleSlash");
        try {
            newHttpServer(PORT);
            WebappContext ctx = new WebappContext("Test", "/");
            addServlet(ctx, "TestServet", "*.html");
            ctx.deploy(httpServer);
            httpServer.start();
            HttpURLConnection conn = getConnection("/index.html", PORT);
            assertEquals(HttpServletResponse.SC_OK,
                    getResponseCodeFromAlias(conn));
            String s = conn.getHeaderField("Request-Was");
            LOGGER.fine("s: " + s);
            assertEquals(s, "/index.html");
        } finally {
            stopHttpServer();
        }
    }
    
    public void testDefaultServletPaths() throws Exception {
        try {
            newHttpServer(PORT);
            WebappContext ctx = new WebappContext("Test");
            addServlet(ctx, "TestServlet", "");
            ctx.deploy(httpServer);
            httpServer.start();
            HttpURLConnection conn = getConnection("/index.html", PORT);
            assertEquals(HttpServletResponse.SC_OK,
                    getResponseCodeFromAlias(conn));
            String s = conn.getHeaderField("Request-Was");
            LOGGER.fine("s: " + s);
            assertEquals(s, "/index.html");
        } finally {
            stopHttpServer();
        }
    }
    
    public void testInvalidServletContextPathSpec() throws Exception {
        try {
            new WebappContext("Test", "/test/");
            fail("Expected IllegalArgumentException to be thrown when context path ends with '/'");
        } catch (IllegalArgumentException iae) {
            // expected
        } catch (Exception e) {
            fail("Unexpected exception: " + e);
        }
    }

    public void testInitParameters() throws IOException {
        LOGGER.fine("testContextParameters");
        try {
            newHttpServer(PORT);
            WebappContext ctx = new WebappContext("Test");
            ctx.addContextInitParameter("ctx", "something");
            ServletRegistration servlet1 = ctx.addServlet("Servlet1", new HttpServlet() {
                private ServletConfig config;
                @Override public void init(ServletConfig config) throws ServletException {
                    super.init(config);
                    this.config = config;
                }
                @Override protected void service(HttpServletRequest req, HttpServletResponse resp) {
                    String init = config.getInitParameter("servlet");
                    String ctx = config.getServletContext().getInitParameter("ctx");
                    boolean ok = "sa1".equals(init) && "something".equals(ctx);
                    resp.setStatus(ok ? 200 : 404);
                }
            });
            servlet1.setInitParameter("servlet", "sa1");
            servlet1.addMapping("/1");

            ServletRegistration servlet2 = ctx.addServlet("Servlet2", new HttpServlet() {
                private ServletConfig config;
                @Override public void init(ServletConfig config) throws ServletException {
                    super.init(config);
                    this.config = config;
                }
                @Override protected void service(HttpServletRequest req, HttpServletResponse resp) {
                    String init = config.getInitParameter("servlet");
                    String ctx = config.getServletContext().getInitParameter("ctx");
                    boolean ok = "sa2".equals(init) && "something".equals(ctx);
                    resp.setStatus(ok ? 200 : 404);
                }
            });
            servlet2.setInitParameter("servlet", "sa2");
            servlet2.addMapping("/2");
            ctx.deploy(httpServer);
            httpServer.start();

            assertEquals(200, getConnection("/1", PORT).getResponseCode());
            assertEquals(200, getConnection("/2", PORT).getResponseCode());
        } finally {
            stopHttpServer();
        }
    }

    /**
     * Covers issue with "No Content" returned by Servlet.
     * <a href="http://twitter.com/shock01/status/2136930089">http://twitter.com/shock01/status/2136930089</a>
     *
     * @throws IOException I/O
     */
    public void testNoContentServlet() throws IOException {
        try {
            startHttpServer(PORT);
            WebappContext ctx = new WebappContext("Test");
            ServletRegistration reg = ctx.addServlet("TestServlet", new HttpServlet() {
                @Override protected void service(HttpServletRequest req, HttpServletResponse resp) {
                    resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
                }
            });
            reg.addMapping("/NoContent");
            ctx.deploy(httpServer);

            assertEquals(HttpServletResponse.SC_NO_CONTENT, getConnection("/NoContent", PORT).getResponseCode());
        } finally {
            stopHttpServer();
        }
    }

    public void testInternalArtifacts() throws IOException {
        try {
            startHttpServer(PORT);
            WebappContext ctx = new WebappContext("Test");
            ServletRegistration reg = ctx.addServlet("TestServlet", new HttpServlet() {
                @Override protected void service(HttpServletRequest req, HttpServletResponse resp) {
                    Request grizzlyRequest = ServletUtils.getInternalRequest(req);
                    Response grizzlyResponse = ServletUtils.getInternalResponse(resp);
                    
                    resp.addHeader("Internal-Request", grizzlyRequest != null ? "present" : null);
                    resp.addHeader("Internal-Response", grizzlyResponse != null ? "present" : null);
                }
            });
            reg.addMapping("/internal");
            ctx.deploy(httpServer);
            
            final HttpURLConnection connection = getConnection("/internal", PORT);

            assertEquals(HttpServletResponse.SC_OK, connection.getResponseCode());

            assertEquals("present", connection.getHeaderField("Internal-Request"));
            assertEquals("present", connection.getHeaderField("Internal-Response"));
        } finally {
            stopHttpServer();
        }
    }
    
    public void testContextListener() throws IOException {
        LOGGER.fine("testContextListener");
        try {
            newHttpServer(PORT);
            WebappContext ctx = new WebappContext("Test", "/contextPath");
            ctx.addListener(MyContextListener.class);
            
            addServlet(ctx, "foobar", "/servletPath/*");
            ctx.deploy(httpServer);
            httpServer.start();
            HttpURLConnection conn = getConnection("/contextPath/servletPath/pathInfo", PORT);
            String s = conn.getHeaderField("Servlet-Name");
            assertEquals("foobar", s);
        } finally {
            stopHttpServer();
            
            assertEquals(MyContextListener.INITIALIZED, MyContextListener.events.poll());
            assertEquals(MyContextListener.DESTROYED, MyContextListener.events.poll());
        }
    }
    
    private ServletRegistration addServlet(final WebappContext ctx,
                                           final String name,
                                           final String alias) {
        final ServletRegistration reg = ctx.addServlet(name, new HttpServlet() {

            @Override
            protected void doGet(
                    HttpServletRequest req, HttpServletResponse resp)
                    throws IOException {
                LOGGER.log(Level.INFO, "{0} received request {1}", new Object[]{alias, req.getRequestURI()});
                resp.setStatus(HttpServletResponse.SC_OK);
                resp.setHeader("Content-Type", header);
                resp.setHeader("Path-Info", req.getPathInfo());
                resp.setHeader("Request-Was", req.getRequestURI());
                resp.setHeader("Servlet-Name", getServletName());
                resp.getWriter().write(alias);
            }
        });
        reg.addMapping(alias);

        return reg;
    }
    
    public static class MyContextListener implements ServletContextListener {
        static final String INITIALIZED = "initialized";
        static final String DESTROYED = "destroyed";
        
        static final Queue<String> events = new ConcurrentLinkedQueue<String>();

        public MyContextListener() {
            events.clear();
        }

        @Override
        public void contextInitialized(ServletContextEvent sce) {
            events.add(INITIALIZED);
        }

        @Override
        public void contextDestroyed(ServletContextEvent sce) {
            events.add(DESTROYED);
        }
    }
}
