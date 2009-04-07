/*
 *
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2007-2008 Sun Microsystems, Inc. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License. You can obtain
 * a copy of the License at https://glassfish.dev.java.net/public/CDDL+GPL.html
 * or glassfish/bootstrap/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/bootstrap/legal/LICENSE.txt.
 * Sun designates this particular file as subject to the "Classpath" exception
 * as provided by Sun in the GPL Version 2 section of the License file that
 * accompanied this code.  If applicable, add the following below the License
 * Header, with the fields enclosed by brackets [] replaced by your own
 * identifying information: "Portions Copyrighted [year]
 * [name of copyright owner]"
 *
 * Contributor(s):
 *
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
 *
 */
package com.sun.grizzly.http;

import com.sun.grizzly.http.embed.GrizzlyWebServer;
import com.sun.grizzly.http.servlet.ServletAdapter;
import junit.framework.TestCase;

import javax.servlet.ServletException;
import javax.servlet.ServletConfig;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.logging.Logger;

/**
 * Basic Servlet Test.
 *
 * @author Jeanfrancois Arcand
 */
public class BasicServletTest extends TestCase {

    public static final int PORT = 18890;
    private static Logger logger = Logger.getLogger("grizzly.test");
    private GrizzlyWebServer gws;
    private String header = "text/html;charset=utf8";

    public void testSetHeaderTest() throws IOException {
        System.out.println("testSetHeaderTest");
        try {
            newGWS(PORT);
            gws.start();
            String alias = "/1";
            addAdapter(alias);
            HttpURLConnection conn = getConnection(alias);
            String s = conn.getHeaderField("Content-Type");
            assertEquals(s, header);
        } finally {
            stopGrizzlyWebServer();
        }
    }
    
    public void testPathInfo() throws IOException {
        System.out.println("testPathInfo");
        try {
            newGWS(PORT);
            String alias = "/contextPath/servletPath/";
            ServletAdapter servletAdapter = addAdapter(alias);
            servletAdapter.setContextPath("/contextPath");
            servletAdapter.setServletPath("/servletPath");
            gws.start();
            HttpURLConnection conn = getConnection("/contextPath/servletPath/pathInfo");
            String s = conn.getHeaderField("Path-Info");
            assertEquals(s, "/pathInfo");
        } finally {
            stopGrizzlyWebServer();
        }
    }
    
    public void testDoubleSlash() throws IOException {
        System.out.println("testDoubleSlash");
        try {
            newGWS(PORT);
            String alias = "/*.html";
            ServletAdapter servletAdapter = addAdapter(alias);
            servletAdapter.setContextPath("/");
            servletAdapter.setServletPath("/");
            gws.start();
            HttpURLConnection conn = getConnection("/index.html");
            assertEquals(HttpServletResponse.SC_OK,
                    getResponseCodeFromAlias(conn));
            String s = conn.getHeaderField("Request-Was");
            System.out.println("s: " +s );
            assertEquals(s, "/index.html");
        } finally {
            stopGrizzlyWebServer();
        }
    }

    public void testInitParameters() throws IOException {
        System.out.println("testContextParameters");
        try {
            newGWS(PORT);
            ServletAdapter sa1 = new ServletAdapter(new HttpServlet() {
                private ServletConfig config;
                @Override public void init(ServletConfig config) throws ServletException {
                    super.init(config);
                    this.config = config;
                }
                @Override protected void service(HttpServletRequest req, HttpServletResponse resp)
                        throws ServletException, IOException {
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
                @Override protected void service(HttpServletRequest req, HttpServletResponse resp)
                        throws ServletException, IOException {
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

            assertEquals(200, getConnection("/1").getResponseCode());
            assertEquals(200, getConnection("/2").getResponseCode());
        } finally {
            stopGrizzlyWebServer();
        }
    }

    private HttpURLConnection getConnection(String alias) throws IOException {
        URL url = new URL("http", "localhost", PORT, alias);
        HttpURLConnection urlConn = (HttpURLConnection) url.openConnection();
        urlConn.connect();
        return urlConn;
    }

    private int getResponseCodeFromAlias(HttpURLConnection urlConn)
            throws IOException {
        return urlConn.getResponseCode();
    }

    private ServletAdapter addAdapter(final String alias) {
        ServletAdapter adapter = new ServletAdapter(new HttpServlet() {

            @Override
            protected void doGet(
                    HttpServletRequest req, HttpServletResponse resp)
                    throws ServletException, IOException {
                logger.info(alias + " received request " + req.getRequestURI());
                resp.setStatus(HttpServletResponse.SC_OK);
                resp.setHeader("Content-Type", header);
                System.out.println("pathInfo -> " + req.getPathInfo());
                resp.setHeader("Path-Info", req.getPathInfo());
                resp.setHeader("Request-Was", req.getRequestURI());
                resp.getWriter().write(alias);
            }
        });
        gws.addGrizzlyAdapter(adapter, new String[]{alias});
        return adapter;
    }

    private void newGWS(int port) throws IOException {
        gws = new GrizzlyWebServer(port);
    }

    private void stopGrizzlyWebServer() {
        gws.stop();
    }
}
