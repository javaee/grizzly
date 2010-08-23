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

package com.sun.grizzly.http;

import com.sun.grizzly.http.servlet.ServletAdapter;
import com.sun.grizzly.util.Utils;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.logging.Logger;

/**
 * Basic Servlet Test.
 *
 * @author Jeanfrancois Arcand
 */
public class BasicServletTest extends GrizzlyWebServerAbstractTest {

    public static final int PORT = 18890;
    private static final Logger logger = Logger.getLogger("grizzly.test");
    private final String header = "text/html;charset=utf8";

    public void testServletName() throws IOException {
        Utils.dumpOut("testServletName");
        try {
            newGWS(PORT);
            String alias = "/contextPath/servletPath/";
            ServletAdapter servletAdapter = addAdapter(alias);
            servletAdapter.setContextPath("/contextPath");
            servletAdapter.setServletPath("/servletPath");
            servletAdapter.setProperty("servlet-name", "foobar");
            gws.start();
            HttpURLConnection conn = getConnection("/contextPath/servletPath/pathInfo", PORT);
            String s = conn.getHeaderField("Servlet-Name");
            assertEquals(s, "foobar");
        } finally {
            stopGrizzlyWebServer();
        }
    }

    public void testSetHeaderTest() throws IOException {
        Utils.dumpOut("testSetHeaderTest");
        try {
            startGrizzlyWebServer(PORT);
            String alias = "/1";
            addAdapter(alias);
            HttpURLConnection conn = getConnection(alias, PORT);
            String s = conn.getHeaderField("Content-Type");
            assertEquals(s, header);
        } finally {
            stopGrizzlyWebServer();
        }
    }

    public void testPathInfo() throws IOException {
        Utils.dumpOut("testPathInfo");
        try {
            newGWS(PORT);
            String alias = "/contextPath/servletPath/";
            ServletAdapter servletAdapter = addAdapter(alias);
            servletAdapter.setContextPath("/contextPath");
            servletAdapter.setServletPath("/servletPath");
            gws.start();
            HttpURLConnection conn = getConnection("/contextPath/servletPath/pathInfo", PORT);
            String s = conn.getHeaderField("Path-Info");
            assertEquals(s, "/pathInfo");
        } finally {
            stopGrizzlyWebServer();
        }
    }

    public void testNotAllowEncodedSlash() throws IOException {
        Utils.dumpOut("testNotAllowEncodedSlash");
        try {
            newGWS(PORT);
            String alias = "/contextPath/servletPath/";
            ServletAdapter servletAdapter = addAdapter(alias);
            servletAdapter.setContextPath("/contextPath");
            servletAdapter.setServletPath("/servletPath");
            gws.start();
            HttpURLConnection conn = getConnection("/contextPath/servletPath%5FpathInfo", PORT);
            String s = conn.getHeaderField("Path-Info");
            assertNotSame(s, "/pathInfo");
        } finally {
            stopGrizzlyWebServer();
        }
    }

    public void testAllowEncodedSlash() throws IOException {
        Utils.dumpOut("testAllowEncodedSlash");
        try {
            newGWS(PORT);
            String alias = "/contextPath/servletPath/";
            ServletAdapter servletAdapter = addAdapter(alias);
            servletAdapter.setAllowEncodedSlash(true);
            servletAdapter.setContextPath("/contextPath");
            servletAdapter.setServletPath("/servletPath");
            gws.start();
            HttpURLConnection conn = getConnection("/contextPath/servletPath%5FpathInfo", PORT);
            String s = conn.getHeaderField("Path-Info");
            assertNotSame(s, "/pathInfo");
        } finally {
            stopGrizzlyWebServer();
        }
    }

    public void testDoubleSlash() throws IOException {
        Utils.dumpOut("testDoubleSlash");
        try {
            newGWS(PORT);
            String alias = "/*.html";
            ServletAdapter servletAdapter = addAdapter(alias);
            servletAdapter.setContextPath("/");
            servletAdapter.setServletPath("/");
            gws.start();
            HttpURLConnection conn = getConnection("/index.html", PORT);
            assertEquals(HttpServletResponse.SC_OK,
                    getResponseCodeFromAlias(conn));
            String s = conn.getHeaderField("Request-Was");
            Utils.dumpOut("s: " +s );
            assertEquals(s, "/index.html");
        } finally {
            stopGrizzlyWebServer();
        }
    }

    public void testInitParameters() throws IOException {
        Utils.dumpOut("testContextParameters");
        try {
            newGWS(PORT);
            ServletAdapter sa1 = new ServletAdapter(new HttpServlet() {
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
            sa1.addInitParameter("servlet", "sa1");
            sa1.addContextParameter("ctx", "something");
            ServletAdapter sa2 = sa1.newServletAdapter(new HttpServlet() {
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
            sa2.addInitParameter("servlet", "sa2");
            gws.addGrizzlyAdapter(sa1, new String[]{"/1"});
            gws.addGrizzlyAdapter(sa2, new String[]{"/2"});
            gws.start();

            assertEquals(200, getConnection("/1", PORT).getResponseCode());
            assertEquals(200, getConnection("/2", PORT).getResponseCode());
        } finally {
            stopGrizzlyWebServer();
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
            newGWS(PORT);
            ServletAdapter noContent = new ServletAdapter(new HttpServlet() {
                @Override protected void service(HttpServletRequest req, HttpServletResponse resp) {
                    resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
                }
            });
            gws.addGrizzlyAdapter(noContent, new String[]{"/NoContent"});
            gws.start();

            assertEquals(HttpServletResponse.SC_NO_CONTENT, getConnection("/NoContent", PORT).getResponseCode());
        } finally {
            stopGrizzlyWebServer();
        }
    }

    private ServletAdapter addAdapter(final String alias) {
        ServletAdapter adapter = new ServletAdapter(new HttpServlet() {

            @Override
            protected void doGet(
                    HttpServletRequest req, HttpServletResponse resp)
                    throws IOException {
                logger.info(alias + " received request " + req.getRequestURI());
                resp.setStatus(HttpServletResponse.SC_OK);
                resp.setHeader("Content-Type", header);
                resp.setHeader("Path-Info", req.getPathInfo());
                resp.setHeader("Request-Was", req.getRequestURI());
                resp.setHeader("Servlet-Name",getServletName());
                resp.getWriter().write(alias);
            }
        });
        addAdapter(alias, adapter);
        return adapter;
    }
}
