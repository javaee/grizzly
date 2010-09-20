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

package org.glassfish.grizzly.http;

import org.glassfish.grizzly.http.servlet.ServletAdapter;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import junit.framework.TestCase;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.net.URL;
import java.util.logging.Logger;
import org.glassfish.grizzly.http.embed.GrizzlyWebServer;

/**
 * Test {@link GrizzlyAdapterChain} use of the {@link Mapper}
 *
 * @author Jeanfrancois Arcand
 */
public class MapperTest extends TestCase {

    public static final int PORT = 18080;
    private static Logger logger = Logger.getLogger("grizzly.test");
    private GrizzlyWebServer gws;
  
    
   public void testOverlapingMapping() throws IOException {
        System.out.println("testOverlapingMapping");
        try {
            startGrizzlyWebServer(PORT);
            String[] aliases = new String[]{"/aaa/bbb", "/aaa/ccc"};
            for (String alias : aliases) {
                addAdapter(alias);
            }

            for (String alias : aliases) {
                HttpURLConnection conn = getConnection(alias);
                assertEquals(HttpServletResponse.SC_OK,
                        getResponseCodeFromAlias(conn));
                assertEquals(alias, readResponse(conn));
            }
        } finally {
            stopGrizzlyWebServer();
        }
   }
    
    public void testRootMapping() throws IOException {
        System.out.println("testRootMapping");
        try {
            startGrizzlyWebServer(PORT);
            String alias = "/";
            addAdapter(alias);
            HttpURLConnection conn = getConnection("/index.html");
            assertEquals(HttpServletResponse.SC_OK,
                    getResponseCodeFromAlias(conn));
            assertEquals(alias, readResponse(conn));
        } finally {
            stopGrizzlyWebServer();
        }
    }
    
    public void testWrongMapping() throws IOException {
        System.out.println("testWrongMapping");
        try {
            startGrizzlyWebServer(PORT);
            String alias = "/a/b/c";
            addAdapter(alias);
            HttpURLConnection conn = getConnection("/aaa.html");
            assertEquals(HttpServletResponse.SC_NOT_FOUND,
                    getResponseCodeFromAlias(conn));
        } finally {
            stopGrizzlyWebServer();
        }
    }

    public void testComplexMapping() throws IOException {
        System.out.println("testComplexMapping");
        try {
            startGrizzlyWebServer(PORT);
            String alias = "/a/b/c/*.html";
            addAdapter(alias);
            HttpURLConnection conn = getConnection("/a/b/c/index.html");
            assertEquals(HttpServletResponse.SC_OK,
                    getResponseCodeFromAlias(conn));
            assertEquals(alias, readResponse(conn));
        } finally {
            stopGrizzlyWebServer();
        }
    }
    
    public void testWildcardMapping() throws IOException {
        System.out.println("testWildcardMapping");
        try {
            startGrizzlyWebServer(PORT);
            String alias = "/*.html";
            addAdapter(alias);
            HttpURLConnection conn = getConnection("/index.html");
            assertEquals(HttpServletResponse.SC_OK,
                    getResponseCodeFromAlias(conn));
            assertEquals(alias, readResponse(conn));
        } finally {
            stopGrizzlyWebServer();
        }
    }



    private String readResponse(HttpURLConnection conn) throws IOException {
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream()));
        return reader.readLine();
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
                logger.info("Servlet : " + alias + " received request " + req.getRequestURI());
                resp.setStatus(HttpServletResponse.SC_OK);
                resp.getWriter().write(alias);
            }
        });
        gws.addGrizzlyAdapter(adapter, new String[]{alias});
        return adapter;
    }

    private void startGrizzlyWebServer(int port) throws IOException {
        gws = new GrizzlyWebServer(port);
        gws.start();
    }

    private void stopGrizzlyWebServer() {
        gws.stop();
    }
}
