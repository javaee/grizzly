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

import com.sun.grizzly.ProtocolChain;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletResponse;

import junit.framework.TestCase;

import com.sun.grizzly.http.embed.GrizzlyWebServer;
import com.sun.grizzly.http.servlet.ServletAdapter;
import java.io.PrintWriter;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;

/**
 * {@link GrizzlyWebServer} tests.
 *
 * @author Sebastien Dionne
 * @since 2009/04/15
 */
public class HelloGrizzlyWebServerTest extends TestCase {

    public static final int PORT = 18890 + 11;
    private static Logger logger = Logger.getLogger("grizzly.test");
    private GrizzlyWebServer gws;

    public void testNPERegression() throws IOException {
        System.out.println("testNPERegression");
        try {
            startGrizzlyWebServer(PORT);
            String[] aliases = new String[] { "*.php" };

            String context = "/";
            String servletPath = "war_autodeploy/php_test";
            String rootFolder = ".";
           
            ServletAdapter adapter = new ServletAdapter();
            adapter.setServletInstance(new HelloServlet());

            adapter.setContextPath(context);
            adapter.setServletPath(servletPath);
            adapter.setRootFolder(rootFolder);

            gws.addGrizzlyAdapter(adapter, aliases);

            gws.start();
           
            String url = context + servletPath + "/index.php";
            HttpURLConnection conn = getConnection(url);
            assertEquals(HttpServletResponse.SC_OK, getResponseCodeFromAlias(conn));
           
            String response = readResponse(conn).toString();
            assertEquals("Hello, world!", response.trim());
           
        } finally {
            stopGrizzlyWebServer();
        }
    }
    
    public void testMultiPath() throws IOException {
        System.out.println("testMultiPath");
        try {
            startGrizzlyWebServer(PORT);
            String[] aliases = new String[] { "*.php" };

            String context = "/";
            String servletPath = "notvalid/php_test";
            String rootFolder = ".";
           
            ServletAdapter adapter = new ServletAdapter();
            adapter.setServletInstance(new HelloServlet());

            adapter.setContextPath(context);
            adapter.setServletPath(servletPath);
            adapter.setRootFolder(rootFolder);

            gws.addGrizzlyAdapter(adapter, aliases);

            gws.start();
           
            String url = null;
            HttpURLConnection conn = null;
            String response = null;
            
            url = context + servletPath + "/index.php";
            conn = getConnection(url);
            assertEquals(HttpServletResponse.SC_OK, getResponseCodeFromAlias(conn));
           
            response = readResponse(conn).toString();
            assertEquals("Hello, world!", response.trim());
            
            // should failed
            url = context + servletPath + "/hello.1";
            conn = getConnection(url);
            assertEquals(HttpServletResponse.SC_NOT_FOUND, getResponseCodeFromAlias(conn));
           
           
        } finally {
            stopGrizzlyWebServer();
        }
    }
   
   
    private StringBuffer readResponse(HttpURLConnection conn) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
       
        StringBuffer sb = new StringBuffer();
        String line = null;
       
        while((line = reader.readLine())!=null){
            logger.info("received line " + line);
            sb.append(line).append("\n");
        }
       
        return sb;
    }

    public void testProtocolFilter() throws IOException {
        System.out.println("testProtocolFilter");
        try {
            String[] aliases = new String[] { "*.foo" };

            ServletAdapter adapter = new ServletAdapter();
            adapter.setServletInstance(new HelloServlet());
            gws = new GrizzlyWebServer(PORT);
            gws.addGrizzlyAdapter(adapter, aliases);
            gws.start();

            ProtocolChain pc = gws.getSelectorThread().getController().getProtocolChainInstanceHandler().poll();
            System.out.println("ProtcolChain: " + pc);
            assertNotNull(pc);
        } finally {
            stopGrizzlyWebServer();
        }
    }

    private HttpURLConnection getConnection(String path) throws IOException {
        logger.info("sending request to " + path);
        URL url = new URL("http", "localhost", PORT, path);
        HttpURLConnection urlConn = (HttpURLConnection) url.openConnection();
        urlConn.connect();
        return urlConn;
    }

    private int getResponseCodeFromAlias(HttpURLConnection urlConn) throws IOException {
        return urlConn.getResponseCode();
    }

   
    private void startGrizzlyWebServer(int port) throws IOException {
        gws = new GrizzlyWebServer(port);

    }

    private void stopGrizzlyWebServer() {
        gws.stop();
    }
    
    /**
     * Hello world servlet.  Most servlets will extend
     * javax.servlet.http.HttpServlet as this one does.
     */
    public class HelloServlet extends HttpServlet {
      /**
       * Implements the HTTP GET method.  The GET method is the standard
       * browser method.
       *
       * @param request the request object, containing data from the browser
       * @param repsonse the response object to send data to the browser
       */
      public void doGet (HttpServletRequest request,
                         HttpServletResponse response)
        throws ServletException, IOException
      {

        // Returns a writer to write to the browser
        PrintWriter out = response.getWriter();

        // Writes the string to the browser.
        out.println("Hello, world!");
        out.close();
      }
    }
}
