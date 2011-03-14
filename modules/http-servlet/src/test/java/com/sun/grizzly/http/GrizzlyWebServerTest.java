/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2009-2011 Oracle and/or its affiliates. All rights reserved.
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

import com.sun.grizzly.SSLConfig;
import com.sun.grizzly.http.embed.GrizzlyWebServer;
import com.sun.grizzly.http.servlet.ServletAdapter;
import com.sun.grizzly.tcp.http11.GrizzlyAdapter;
import com.sun.grizzly.tcp.http11.GrizzlyRequest;
import com.sun.grizzly.tcp.http11.GrizzlyResponse;
import com.sun.grizzly.util.Utils;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSession;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.logging.Logger;

/**
 * {@link GrizzlyWebServer} tests.
 *
 * @author Hubert Iwaniuk
 * @since Jan 22, 2009
 */
public class GrizzlyWebServerTest extends GrizzlyWebServerAbstractTest {

    public static final int PORT = 18900;
    private static final Logger logger = Logger.getLogger("grizzly.test");

    public void testAddGrizzlyAdapterAfterStart() throws IOException {
        Utils.dumpOut("testAddGrizzlyAdapterAfterStart");
        try {
            final int port = PORT + 1;
            startGrizzlyWebServer(port);
            String alias = "/1";
            addAdapter(alias);
            HttpURLConnection conn = getConnection(alias, port);
            assertEquals(HttpServletResponse.SC_OK,
                    getResponseCodeFromAlias(conn));
            assertEquals(alias, readResponse(conn));
        } finally {
            stopGrizzlyWebServer();
        }
    }

    public void testMultipleAddGrizzlyAdapterAfterStart() throws IOException {
        Utils.dumpOut("testMultipleAddGrizzlyAdapterAfterStart");
        try {
            final int port = PORT + 2;
            startGrizzlyWebServer(port);
            String[] aliases = new String[]{"/1", "/2", "/3"};
            for (String alias : aliases) {
                addAdapter(alias);
            }

            for (String alias : aliases) {
                HttpURLConnection conn = getConnection(alias, port);
                assertEquals(HttpServletResponse.SC_OK,
                        getResponseCodeFromAlias(conn));
                assertEquals(alias, readResponse(conn));
            }
        } finally {
            stopGrizzlyWebServer();
        }
    }

    public void testOverlapingAddGrizzlyAdapterAfterStart() throws IOException {
        Utils.dumpOut("testOverlapingAddGrizzlyAdapterAfterStart");
        try {
            final int port = PORT + 3;
            startGrizzlyWebServer(port);
            String[] aliases = new String[]{"/1", "/2", "/2/1", "/1/2/3/4/5"};
            for (String alias : aliases) {
                addAdapter(alias);
            }

            for (String alias : aliases) {
                HttpURLConnection conn = getConnection(alias, port);
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
        Utils.dumpOut("testAddRemoveMixAfterStart");
        try {
            final int port = PORT + 4;
            startGrizzlyWebServer(port);
            String[] aliases = new String[]{"/1", "/2", "/3"};
            ServletAdapter adapter = addAdapter("/0");
            for (String alias : aliases) {
                addAdapter(alias);
            }
            gws.removeGrizzlyAdapter(adapter);

            for (String alias : aliases) {
                HttpURLConnection conn = getConnection(alias, port);
                assertEquals(HttpServletResponse.SC_OK,
                        getResponseCodeFromAlias(conn));
                assertEquals(alias, readResponse(conn));
            }
            assertEquals(HttpServletResponse.SC_NOT_FOUND,
                    getResponseCodeFromAlias(getConnection("/0", port)));
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
        Utils.dumpOut("testStartContract");
        // lock port
        Socket soc = new Socket();
        int port = PORT + 5;
        try {
            soc.bind(new InetSocketAddress(port));
        } catch (IOException e) {
            fail("Could not bind to port: " + port + ". " + e.getMessage());
        }

        Utils.dumpOut("Bound to port: " + port);

        try {
            gws = new GrizzlyWebServer(port);
            gws.getSelectorThread().setReuseAddress(false);
            gws.start();
            fail("Should throw exception that can't bind to port.");
        } catch (IOException e) {
            Utils.dumpOut("Ha got you this time.");
        } finally {
            soc.close();
            stopGrizzlyWebServer();
        }
    }

    /**
     * Tests that {@link GrizzlyWebServer} should not start if default {@link SSLConfig} is supposed to
     * be used but defaults doesn't resolve to valid configuration.
     */
    public void testStartSecureFailDefault() {
        Utils.dumpOut("testStartSecureFailDefault");
        final int port = PORT + 6;
        gws = new GrizzlyWebServer(port, ".", true);
        try {
            gws.start();
            gws.stop();
            fail("Should not be able to start if default ssl configuration was not created.");
        } catch (IOException e) {
            e.printStackTrace();
            fail("Could not bind to port: " + port + ". " + e.getMessage());
        } catch (RuntimeException e) {
            // expected
        }
    }

    /**
     * Tests that {@link GrizzlyWebServer} will start properly in secure mode with modified configuration.
     *
     * @throws IOException Not much to say here.
     * @throws URISyntaxException Could not find keystore file.
     * @throws GeneralSecurityException Security failure.
     */
    public void testStartSecureWithConfiguration()
            throws IOException, URISyntaxException, GeneralSecurityException {
        Utils.dumpOut("testStartSecureWithConfiguration");
        URL resource = getClass().getClassLoader().getResource("test-keystore.jks");
        SSLConfig cfg = new SSLConfig(true);
        cfg.setKeyStorePass("changeit");
        if (resource != null) {
            cfg.setKeyStoreFile(new File(resource.toURI()).getAbsolutePath());
        } else {
            fail("Couldn't find keystore");
        }
        final int port = PORT + 7;
        gws = new GrizzlyWebServer(port, ".", true);
        gws.setSSLConfig(cfg);
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
            fail("Could not bind to port: " + port + ". " + e.getMessage());
        } catch (RuntimeException e) {
            fail("Should be able to start in secure mode.");
        }
        try {
            URL res = getClass().getClassLoader().getResource("test-truststore.jks");
            if (res != null) {
                URI uri = res.toURI();

                SSLConfig clientCfg = new SSLConfig(true);
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
                    (HttpURLConnection) new URL("https", "localhost", port, "/sec").openConnection();
            assertEquals(HttpServletResponse.SC_OK, getResponseCodeFromAlias(conn));
            assertEquals(encMsg, readResponse(conn));
        } finally {
            gws.stop();
        }
    }

    /**
     * Tests if {@link Filter} is getting destroyed on {@link GrizzlyWebServer#stop}.
     *
     * @throws IOException Couldn't start {@link GrizzlyWebServer}.
     */
    public void testServletFilterDestroy() throws IOException {

        final int port = PORT + 8;
        gws = new GrizzlyWebServer(port, ".", false);
        final boolean init[] = new boolean[]{false};
        final boolean filter[] = new boolean[]{false};
        final boolean destroy[] = new boolean[]{false};

        ServletAdapter sa = new ServletAdapter();
        sa.addFilter(new Filter() {
            public void init(final FilterConfig filterConfig) {
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
        Utils.dumpOut("testAddGrizzlyAdapterBeforeAndAfterStart");
        try {
            final int port = PORT + 9;
            gws = new GrizzlyWebServer(port);
            String[] aliases = new String[]{"/1"};
            for (String alias : aliases) {
                addAdapter(alias);
            }
            gws.start();
            for (String alias : aliases) {
                HttpURLConnection conn = getConnection(alias, port);
                assertEquals(HttpServletResponse.SC_OK,
                        getResponseCodeFromAlias(conn));
                assertEquals(alias, readResponse(conn));
            }
            String alias = "/2";
            addAdapter(alias);

            HttpURLConnection conn = getConnection(alias, port);
            assertEquals(HttpServletResponse.SC_OK,
                    getResponseCodeFromAlias(conn));
            assertEquals(alias, readResponse(conn));
        } finally {
            stopGrizzlyWebServer();
        }
    }

    public void testMultipleAddGrizzlyAdapterBeforeStartAndOneAfter() throws IOException {
        Utils.dumpOut("testAddGrizzlyAdapterBeforeAndAfterStart");
        try {
            final int port = PORT + 10;
            gws = new GrizzlyWebServer(port);
            String[] aliases = new String[]{"/1", "/2", "/3"};
            for (String alias : aliases) {
                addAdapter(alias);
            }
            gws.start();
            for (String alias : aliases) {
                HttpURLConnection conn = getConnection(alias, port);
                assertEquals(HttpServletResponse.SC_OK,
                        getResponseCodeFromAlias(conn));
                assertEquals(alias, readResponse(conn));
            }            
            String alias = "/4";
            addAdapter(alias);

            HttpURLConnection conn = getConnection(alias, port);
            assertEquals(HttpServletResponse.SC_OK,
                    getResponseCodeFromAlias(conn));
            assertEquals(alias, readResponse(conn));
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
                resp.getWriter().write(alias);
            }
        });
        gws.addGrizzlyAdapter(adapter, new String[]{alias});
        return adapter;
    }
}
