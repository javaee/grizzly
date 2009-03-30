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
import com.sun.grizzly.SSLConfig;
import com.sun.grizzly.tcp.http11.GrizzlyAdapter;
import com.sun.grizzly.tcp.http11.GrizzlyRequest;
import com.sun.grizzly.tcp.http11.GrizzlyResponse;
import junit.framework.TestCase;

import javax.servlet.ServletException;
import javax.servlet.Filter;
import javax.servlet.FilterConfig;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.FilterChain;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;
import java.io.*;
import java.net.*;
import java.util.logging.Logger;
import java.util.HashMap;

/**
 * {@link GrizzlyWebServer} tests.
 *
 * @author Hubert Iwaniuk
 * @since Jan 22, 2009
 */
public class GrizzlyWebServerTest extends TestCase {

    public static final int PORT = 18890+10;
    private static Logger logger = Logger.getLogger("grizzly.test");
    private GrizzlyWebServer gws;

    public void testAddGrizzlyAdapterAfterStart() throws IOException {
        System.out.println("testAddGrizzlyAdapterAfterStart");
        try {
            startGrizzlyWebServer(PORT);
            String alias = "/1";
            addAdapter(alias);
            HttpURLConnection conn = getConnection(alias);
            assertEquals(HttpServletResponse.SC_OK,
                    getResponseCodeFromAlias(conn));
            assertEquals(alias, readResponse(conn));
        } finally {
            stopGrizzlyWebServer();
        }
    }

    public void testMultipleAddGrizzlyAdapterAfterStart() throws IOException {
        System.out.println("testMultipleAddGrizzlyAdapterAfterStart");
        try {
            startGrizzlyWebServer(PORT);
            String[] aliases = new String[]{"/1", "/2", "/3"};
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

    public void testOverlapingAddGrizzlyAdapterAfterStart() throws IOException {
        System.out.println("testOverlapingAddGrizzlyAdapterAfterStart");
        try {
            startGrizzlyWebServer(PORT);
            String[] aliases = new String[]{"/1", "/2", "/2/1", "/1/2/3/4/5"};
            for (String alias : aliases) {
                addAdapter(alias);
            }

            for (String alias : aliases) {
                HttpURLConnection conn = getConnection(alias);
                assertEquals(HttpServletResponse.SC_OK,
                        getResponseCodeFromAlias(conn));
                if (alias.startsWith(readResponse(conn))){
                    assertTrue(true);
                } else {
                    assertFalse(false);
                }
            }
        } finally {
            stopGrizzlyWebServer();
        }
    }

    public void testAddRemoveMixAfterStart() throws IOException {
        System.out.println("testAddRemoveMixAfterStart");
        try {
            startGrizzlyWebServer(PORT);
            String[] aliases = new String[]{"/1", "/2", "/3"};
            ServletAdapter adapter = addAdapter("/0");
            for (String alias : aliases) {
                addAdapter(alias);
            }
            gws.removeGrizzlyAdapter(adapter);

            for (String alias : aliases) {
                HttpURLConnection conn = getConnection(alias);
                assertEquals(HttpServletResponse.SC_OK,
                        getResponseCodeFromAlias(conn));
                assertEquals(alias, readResponse(conn));
            }
            assertEquals(HttpServletResponse.SC_NOT_FOUND,
                    getResponseCodeFromAlias(getConnection("/0")));
        } finally {
            stopGrizzlyWebServer();
        }

    }

    /**
     * Test if {@link GrizzlyWebServer#start} throws {@link IOException} if can't bind.
     *
     * @throws IOException Error.
     */
    public void testStartContract() throws IOException {
        System.out.println("testStartContract");
        // lock port
        Socket soc = new Socket();
        int port = PORT + 1;
        try {
            soc.bind(new InetSocketAddress(port));
        } catch (IOException e) {
            fail("Could not bind to port: " + port + ". " + e.getMessage());
        }

        System.out.println("Bound to port: " + port);

        try {
            gws = new GrizzlyWebServer(port);
            gws.getSelectorThread().setReuseAddress(false);
            gws.start();
            fail("Should throw exception that can't bind to port.");
        } catch (IOException e) {
            System.out.println("Ha got you this time.");
        } finally {
            soc.close();
            stopGrizzlyWebServer();
        }
    }

    /**
     * Tests that {@link GrizzlyWebServer} should not start if default {@link com.sun.grizzly.SSLConfig} is supposed to
     * be used but defaults doesn't resolve to valid configuration.
     */
    public void testStartSecureFailDefault() {
        System.out.println("testStartSecureFailDefault");
        gws = new GrizzlyWebServer(PORT, ".", true);
        try {
            gws.start();
            fail("Should not be able to start if default ssl configuration was not created.");
        } catch (IOException e) {
            e.printStackTrace();
            fail("Could not bind to port: " + PORT+ ". " + e.getMessage());
        } catch (RuntimeException e) {
            // expected
        }
    }

    /**
     * Tests that {@link GrizzlyWebServer} will start properly in secure mode with modified configuration.
     * TODO: work in progress
     *
     * @throws java.io.IOException Not much to say here.
     * @throws java.net.URISyntaxException Could not find keystore file.
     */
    public void _testStartSecureWithConfiguration() throws IOException, URISyntaxException {
        System.out.println("testStartSecureWithConfiguration");
        URL resource = getClass().getClassLoader().getResource("test-keystore.jks");
        if (resource != null) {
            URI uri = resource.toURI();
            System.setProperty(SSLConfig.KEY_STORE_FILE, new File(uri).getAbsolutePath());
        }
        gws = new GrizzlyWebServer(PORT, ".", true);
        gws.addGrizzlyAdapter(new GrizzlyAdapter() {
            @Override public void service(GrizzlyRequest request, GrizzlyResponse response) {
                response.setStatus(200);
                try {
                    response.getOutputBuffer().write("Secured.");
                } catch (IOException e) {
                    response.setStatus(500, "Server made a boo.");
                }
            }
        }, new String[]{"/sec"});
        try {
            gws.start();
        } catch (IOException e) {
            e.printStackTrace();
            fail("Could not bind to port: " + PORT+ ". " + e.getMessage());
        } catch (RuntimeException e) {
            fail("Should be able to start in secure mode.");
        }
        try {
            HttpsURLConnection conn =
                    (HttpsURLConnection) new URL("https", "localhost", PORT, "/sec").openConnection();
            conn.setHostnameVerifier(new HostnameVerifier() {
                public boolean verify(String s, SSLSession sslSession) {
                    return true;
                }
            });
            assertEquals(HttpServletResponse.SC_OK, getResponseCodeFromAlias(conn));
            assertEquals("Secured.", getResponseCodeFromAlias(conn));
        } finally {
            gws.stop();
        }
    }

    /**
     * Tests if {@link Filter} is getting destroyed on {@link GrizzlyWebServer#stop}.
     *
     * @throws java.io.IOException Couldn't start {@link GrizzlyWebServer}.
     */
    public void testServletFilterDestroy() throws IOException {

        gws = new GrizzlyWebServer(PORT, ".", false);
        final boolean init[] = new boolean[]{false};
        final boolean filter[] = new boolean[]{false};
        final boolean destroy[] = new boolean[]{false};

        ServletAdapter sa = new ServletAdapter();
        sa.addFilter(new Filter() {
            public void init(final FilterConfig filterConfig)
                throws ServletException {
                init[0] = true;
            }

            public void doFilter(
                final ServletRequest request, final ServletResponse response,
                final FilterChain chain) throws IOException, ServletException {
                filter[0] = true;
                chain.doFilter(request, response);
            }

            public void destroy() {
                destroy[0] = true;
            }
        }, "filter", new HashMap(0));

        gws.addGrizzlyAdapter(sa, new String[]{"/"});
        gws.start();

        gws.stop();
        assertTrue(destroy[0]);
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
                logger.info(alias + " received request " + req.getRequestURI());
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
