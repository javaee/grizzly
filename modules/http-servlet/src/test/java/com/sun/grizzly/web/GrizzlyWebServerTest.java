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

import java.util.logging.Level;
import com.sun.grizzly.http.servlet.ServletAdapter;
import junit.framework.TestCase;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSession;
import javax.servlet.*;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.*;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.logging.Logger;
import com.sun.grizzly.nio.transport.TCPNIOTransport;
import com.sun.grizzly.ssl.SSLContextConfigurator;
import com.sun.grizzly.ssl.SSLEngineConfigurator;
import com.sun.grizzly.tcp.http11.GrizzlyAdapter;
import com.sun.grizzly.tcp.http11.GrizzlyRequest;
import com.sun.grizzly.tcp.http11.GrizzlyResponse;
import com.sun.grizzly.http.embed.GrizzlyWebServer;

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

    public void ttestAddGrizzlyAdapterAfterStart() throws IOException {
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

    public void ttestMultipleAddGrizzlyAdapterAfterStart() throws IOException {
        System.out.println("testMultipleAddGrizzlyAdapterAfterStart");
        try {
            startGrizzlyWebServer(PORT);
            String[] aliases = new String[]{"/1", "/2", "/3"};
            for (String alias : aliases) {
                addAdapter(alias);
            }

            for (String alias : aliases) {
                System.out.println("alias: " + alias);
                HttpURLConnection conn = getConnection(alias);
                assertEquals(HttpServletResponse.SC_OK,
                        getResponseCodeFromAlias(conn));
                assertEquals(alias, readResponse(conn));
            }
        } finally {
            stopGrizzlyWebServer();
        }
    }

    public void ttestOverlapingAddGrizzlyAdapterAfterStart() throws IOException {
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

    public void ttestAddRemoveMixAfterStart() throws IOException {
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
    public void ttestStartContract() throws IOException {
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
            ((TCPNIOTransport) gws.getTransport()).setReuseAddress(false);
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
     * Tests that {@link GrizzlyWebServer} will start properly in secure mode with modified configuration.
     *
     * @throws java.io.IOException Not much to say here.
     * @throws java.net.URISyntaxException Could not find keystore file.
     * @throws java.security.GeneralSecurityException Security failure.
     */
    public void ttestStartSecureWithConfiguration()
            throws IOException, URISyntaxException, GeneralSecurityException {
        System.out.println("testStartSecureWithConfiguration");
        URL resource = getClass().getClassLoader().getResource("test-keystore.jks");
        SSLContextConfigurator cfg = new SSLContextConfigurator(true);
        if (resource != null) {
            cfg.setKeyStoreFile(new File(resource.toURI()).getAbsolutePath());
        } else {
            fail("Couldn't find keystore");
        }
        gws = new GrizzlyWebServer(PORT, ".", true);
        gws.setSSLConfiguration(new SSLEngineConfigurator(
                cfg.createSSLContext(), false, false, false));
        final String encMsg = "Secured.";
        gws.addGrizzlyAdapter(new GrizzlyAdapter() {
            public void service(GrizzlyRequest request, GrizzlyResponse response) {
                response.setStatus(200);
                try {
                    response.getOutputBuffer().write(encMsg);
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
            URL res = getClass().getClassLoader().getResource("test-truststore.jks");
            if (res != null) {
                URI uri = res.toURI();

                SSLContextConfigurator clientCfg = new SSLContextConfigurator(true);
                clientCfg.setTrustStoreFile(new File(uri).getAbsolutePath());

                HttpsURLConnection.setDefaultSSLSocketFactory(
                        clientCfg.createSSLContext().getSocketFactory());
            } else {
                fail("Couldn't find truststore");
            }

            HttpsURLConnection.setDefaultHostnameVerifier(new HostnameVerifier() {
                public boolean verify(String s, SSLSession sslSession) {
                    return true;
                }
            });
            HttpURLConnection conn =
                    (HttpURLConnection) new URL("https", "localhost", PORT, "/sec").openConnection();
            assertEquals(HttpServletResponse.SC_OK, getResponseCodeFromAlias(conn));
            assertEquals(encMsg, readResponse(conn));
        } finally {
            gws.stop();
        }
    }

    /**
     * Tests if {@link Filter} is getting destroyed on {@link GrizzlyWebServer#stop}.
     *
     * @throws java.io.IOException Couldn't start {@link GrizzlyWebServer}.
     */
    public void ttestServletFilterDestroy() throws IOException {

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

    /**
     * Test for https://grizzly.dev.java.net/issues/show_bug.cgi?id=513
     *
     * @throws IOException Fail.
     */
    public void testAddGrizzlyAdapterBeforeAndAfterStart() throws IOException {
        System.out.println("testAddGrizzlyAdapterBeforeAndAfterStart");
        try {
            gws = new GrizzlyWebServer(PORT);
            String[] aliases = new String[] {"/1"};
            for (String alias : aliases) {
                addAdapter(alias);
            }
            gws.start();
            for (String alias : aliases) {
                HttpURLConnection conn = getConnection(alias);
                assertEquals(HttpServletResponse.SC_OK,
                        getResponseCodeFromAlias(conn));
                assertEquals(alias, readResponse(conn));
            }
            String alias = "/2";
            addAdapter(alias);

            HttpURLConnection conn = getConnection(alias);
            assertEquals(HttpServletResponse.SC_OK,
                    getResponseCodeFromAlias(conn));
            assertEquals(alias, readResponse(conn));
        } finally {
            stopGrizzlyWebServer();
        }
    }

    public void ttestMultipleAddGrizzlyAdapterBeforeStartAndOneAfter() throws IOException {
        System.out.println("testAddGrizzlyAdapterBeforeAndAfterStart");
        try {
            gws = new GrizzlyWebServer(PORT);
            String[] aliases = new String[]{"/1", "/2", "/3"};
            for (String alias : aliases) {
                addAdapter(alias);
            }
            gws.start();

            for (String alias : aliases) {
                HttpURLConnection conn = getConnection(alias);
                assertEquals(HttpServletResponse.SC_OK,
                        getResponseCodeFromAlias(conn));
                assertEquals(alias, readResponse(conn));
            }            
            String alias = "/4";
            addAdapter(alias);

            HttpURLConnection conn = getConnection(alias);
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
