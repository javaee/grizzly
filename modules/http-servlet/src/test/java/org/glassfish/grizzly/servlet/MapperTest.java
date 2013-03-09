/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2009-2013 Oracle and/or its affiliates. All rights reserved.
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
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.http.server.HttpHandlerChain;

/**
 * Test {@link HttpHandlerChain} use of the {@link MapperTest}
 *
 * @author Jeanfrancois Arcand
 */
public class MapperTest extends HttpServerAbstractTest {

    public static final int PORT = 18080;
    private static final Logger LOGGER = Grizzly.logger(MapperTest.class);

    public void testOverlappingMapping() throws IOException {
        System.out.println("testOverlappingMapping");
        try {
            startHttpServer(PORT);
            WebappContext ctx = new WebappContext("Test");
            String[] aliases = new String[]{"/aaa/bbb", "/aaa/ccc"};
            for (String alias : aliases) {
                addServlet(ctx, alias);
            }
            ctx.deploy(httpServer);
            for (String alias : aliases) {
                HttpURLConnection conn = getConnection(alias, PORT);
                assertEquals(HttpServletResponse.SC_OK,
                        getResponseCodeFromAlias(conn));
                assertEquals(alias, readResponse(conn));
                assertEquals(alias, conn.getHeaderField("servlet-path"));
                assertNull(alias, conn.getHeaderField("path-info"));
            }
        } finally {
            stopHttpServer();
        }
   }


    public void testOverlappingMapping2() throws IOException {
        System.out.println("testOverlappingMapping2");
        try {
            startHttpServer(PORT);

            String[] alias = new String[]{"*.jsp", "/jsp/*"};

            WebappContext ctx = new WebappContext("Test");
            addServlet(ctx, "*.jsp");
            addServlet(ctx, "/jsp/*");
            ctx.deploy(httpServer);

            HttpURLConnection conn = getConnection("/jsp/index.jsp", PORT);
            assertEquals(HttpServletResponse.SC_OK, getResponseCodeFromAlias(conn));
            assertEquals(alias[1], readResponse(conn));
            assertEquals("/jsp", conn.getHeaderField("servlet-path"));
            assertEquals("/index.jsp", conn.getHeaderField("path-info"));

        } finally {
            stopHttpServer();
        }
   }


    public void testRootStarMapping() throws IOException {
        System.out.println("testRootMapping");
        try {
            startHttpServer(PORT);
            WebappContext ctx = new WebappContext("Test");
            String alias = "/*";
            addServlet(ctx, alias);   // overrides the static resource handler
            ctx.deploy(httpServer);
            HttpURLConnection conn = getConnection("/index.html", PORT);
            assertEquals(HttpServletResponse.SC_OK,
                    getResponseCodeFromAlias(conn));
            assertEquals(alias, readResponse(conn));
            assertEquals("", conn.getHeaderField("servlet-path"));
            assertEquals("/index.html", conn.getHeaderField("path-info"));
        } finally {
            stopHttpServer();
        }
    }

    public void testRootStarMapping2() throws IOException {
            System.out.println("testRootMapping");
            try {
                startHttpServer(PORT);
                WebappContext ctx = new WebappContext("Test");
                String alias = "/*";
                addServlet(ctx, alias);   // overrides the static resource handler
                ctx.deploy(httpServer);
                HttpURLConnection conn = getConnection("/foo/index.html", PORT);
                assertEquals(HttpServletResponse.SC_OK,
                        getResponseCodeFromAlias(conn));
                assertEquals(alias, readResponse(conn));
                assertEquals("", conn.getHeaderField("servlet-path"));
                assertEquals("/foo/index.html", conn.getHeaderField("path-info"));
            } finally {
                stopHttpServer();
            }
        }

    public void testRootMapping() throws IOException {
        System.out.println("testRootMapping");
        try {
            startHttpServer(PORT);
            WebappContext ctx = new WebappContext("Test");
            String alias = "/";
            addServlet(ctx, alias);   // overrides the static resource handler
            ctx.deploy(httpServer);
            HttpURLConnection conn = getConnection("/", PORT);
            assertEquals(HttpServletResponse.SC_OK,
                    getResponseCodeFromAlias(conn));
            assertEquals(alias, readResponse(conn));
            assertEquals("/", conn.getHeaderField("servlet-path"));
            assertEquals(null, conn.getHeaderField("path-info"));
        } finally {
            stopHttpServer();
        }
    }

    public void testRootMapping2() throws IOException {
            System.out.println("testRootMapping");
            try {
                startHttpServer(PORT);
                WebappContext ctx = new WebappContext("Test");
                String alias = "/";
                addServlet(ctx, alias);   // overrides the static resource handler
                ctx.deploy(httpServer);
                HttpURLConnection conn = getConnection("/foo/index.html", PORT);
                assertEquals(HttpServletResponse.SC_OK,
                        getResponseCodeFromAlias(conn));
                assertEquals(alias, readResponse(conn));
                assertEquals("/foo/index.html", conn.getHeaderField("servlet-path"));
                assertEquals(null, conn.getHeaderField("path-info"));
            } finally {
                stopHttpServer();
            }
        }

    public void testWrongMapping() throws IOException {
        System.out.println("testWrongMapping");
        try {
            startHttpServer(PORT);
            WebappContext ctx = new WebappContext("Test");
            String alias = "/a/b/c";
            addServlet(ctx, alias);
            ctx.deploy(httpServer);
            HttpURLConnection conn = getConnection("/aaa.html", PORT);
            assertEquals(HttpServletResponse.SC_NOT_FOUND,
                    getResponseCodeFromAlias(conn));
        } finally {
            stopHttpServer();
        }
    }
//
//    public void testComplexMapping() throws IOException {
//        System.out.println("testComplexMapping");
//        try {
//            startHttpServer(PORT);
//            String alias = "/a/b/c/*.html";
//            addHttpHandler(alias);
//            HttpURLConnection conn = getConnection("/a/b/c/index.html", PORT);
//            assertEquals(HttpServletResponse.SC_OK,
//                    getResponseCodeFromAlias(conn));
//            assertEquals(alias, readResponse(conn));
//        } finally {
//            stopHttpServer();
//        }
//    }
//
    public void testWildcardMapping() throws IOException {
        System.out.println("testWildcardMapping");
        try {
            startHttpServer(PORT);
            WebappContext ctx = new WebappContext("Test");
            String alias = "*.html";
            addServlet(ctx, alias);
            ctx.deploy(httpServer);
            HttpURLConnection conn = getConnection("/index.html", PORT);
            assertEquals(HttpServletResponse.SC_OK,
                    getResponseCodeFromAlias(conn));
            assertEquals(alias, readResponse(conn));
            assertEquals("/index.html", conn.getHeaderField("servlet-path"));
            assertNull(conn.getHeaderField("path-info"));
        } finally {
            stopHttpServer();
        }
    }

     public void testWrongMappingRootContext() throws IOException {
        System.out.println("testWrongMappingRootContext");
        try {
            startHttpServer(PORT);
            WebappContext ctx = new WebappContext("Test");
            String alias = "*.a";
            addServlet(ctx, alias);
            ctx.deploy(httpServer);
            HttpURLConnection conn = getConnection("/aaa.html", PORT);
            assertEquals(HttpServletResponse.SC_NOT_FOUND, getResponseCodeFromAlias(conn));
        } finally {
            stopHttpServer();
        }
    }


    public void testDefaultServletOverride() throws Exception {
        try {
            startHttpServer(PORT);
            WebappContext ctx = new WebappContext("Test");
            String alias = "/";
            addServlet(ctx, alias);
            ctx.deploy(httpServer);
            HttpURLConnection conn = getConnection("/", PORT);
            assertEquals(HttpServletResponse.SC_OK, getResponseCodeFromAlias(conn));
            assertEquals("/", conn.getHeaderField("servlet-path"));
        } finally {
            stopHttpServer();
        }
    }

    public void testDefaultContext() throws Exception {
        try {
            startHttpServer(PORT);
            WebappContext ctx = new WebappContext("Test", "/");
            String alias = "/foo/*";
            addServlet(ctx, alias);
            ctx.deploy(httpServer);
            HttpURLConnection conn = getConnection("/foo/bar/baz", PORT);
            assertEquals(HttpServletResponse.SC_OK, getResponseCodeFromAlias(conn));
            assertEquals("/foo/bar/baz", conn.getHeaderField("request-uri"));
            assertEquals("", conn.getHeaderField("context-path"));
            assertEquals("/foo", conn.getHeaderField("servlet-path"));
            assertEquals("/bar/baz", conn.getHeaderField("path-info"));
        } finally {
            stopHttpServer();
        }
    }


    // --------------------------------------------------------- Private Methods


    private static ServletRegistration addServlet(final WebappContext ctx,
                                                  final String alias) {
        final ServletRegistration reg = ctx.addServlet(alias, new HttpServlet() {

            @Override
            protected void doGet(
                    HttpServletRequest req, HttpServletResponse resp)
                    throws IOException {
                LOGGER.log(Level.INFO, "{0} received request {1}", new Object[]{alias, req.getRequestURI()});
                resp.setStatus(HttpServletResponse.SC_OK);
                resp.setHeader("Path-Info", req.getPathInfo());
                resp.setHeader("Servlet-Path", req.getServletPath());
                resp.setHeader("Request-Was", req.getRequestURI());
                resp.setHeader("Servlet-Name", getServletName());
                resp.setHeader("Request-Uri", req.getRequestURI());
                resp.setHeader("Context-Path", req.getContextPath());
                resp.getWriter().write(alias);
            }
        });
        reg.addMapping(alias);

        return reg;
    }

}
