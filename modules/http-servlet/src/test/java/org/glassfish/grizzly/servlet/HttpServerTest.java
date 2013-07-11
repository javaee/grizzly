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

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.util.logging.Level;
import java.util.logging.Logger;
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

import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.http.server.HttpHandler;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.http.server.Response;
import org.glassfish.grizzly.ssl.SSLContextConfigurator;
import org.glassfish.grizzly.ssl.SSLEngineConfigurator;

/**
 * {@link HttpServer} tests.
 *
 * @author Hubert Iwaniuk
 * @since Jan 22, 2009
 */
public class HttpServerTest extends HttpServerAbstractTest {

    public static final int PORT = 18890+10;
    private static final Logger logger = Grizzly.logger(HttpServerTest.class);

    public void testAddHttpHandlerAfterStart() throws IOException {
        System.out.println("testAddHttpHandlerAfterStart");
        try {
            final int port = PORT + 1;
            startHttpServer(port);
            String alias = "/1";
            WebappContext ctx = new WebappContext("Test");

            addServlet(ctx, alias);
            ctx.deploy(httpServer);
            HttpURLConnection conn = getConnection(alias, port);
            assertEquals(HttpServletResponse.SC_OK,
                    getResponseCodeFromAlias(conn));
            assertEquals(alias, readResponse(conn));
        } finally {
            stopHttpServer();
        }
    }

    public void testMultipleAddHttpHandlerAfterStart() throws IOException {
        System.out.println("testMultipleAddHttpHandlerAfterStart");
        try {
            final int port = PORT + 2;
            startHttpServer(port);
            String[] aliases = new String[]{"/1", "/2", "/3"};
            WebappContext ctx = new WebappContext("Test");
            for (String alias : aliases) {
                addServlet(ctx, alias);
            }
            ctx.deploy(httpServer);
            for (String alias : aliases) {
                HttpURLConnection conn = getConnection(alias, port);
                assertEquals(HttpServletResponse.SC_OK,
                        getResponseCodeFromAlias(conn));
                assertEquals(alias, readResponse(conn));
            }
        } finally {
            stopHttpServer();
        }
    }

    public void testOverlapingAddHttpHandlerAfterStart() throws IOException {
        System.out.println("testOverlapingAddHttpHandlerAfterStart");
        try {
            final int port = PORT + 3;
            startHttpServer(port);
            WebappContext ctx = new WebappContext("Test");
            String[] aliases = new String[]{"/1", "/2", "/2/1", "/1/2/3/4/5"};
            for (String alias : aliases) {
                addServlet(ctx, alias);
            }
            ctx.deploy(httpServer);
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
            stopHttpServer();
        }
    }

//    public void testAddRemoveMixAfterStart() throws IOException {
//        System.out.println("testAddRemoveMixAfterStart");
//        try {
//            final int port = PORT + 4;
//            startHttpServer(port);
//            String[] aliases = new String[]{"/1", "/2", "/3"};
//            WebappContext ctx = new WebappContext("Test");
//            ServletHandler servletHandler = addHttpHandler("/0");
//            for (String alias : aliases) {
//                addHttpHandler(alias);
//            }
//            httpServer.getServerConfiguration().removeHttpHandler(servletHandler);
//
//            for (String alias : aliases) {
//                HttpURLConnection conn = getConnection(alias, port);
//                assertEquals(HttpServletResponse.SC_OK,
//                        getResponseCodeFromAlias(conn));
//                assertEquals(alias, readResponse(conn));
//            }
//            assertEquals(HttpServletResponse.SC_NOT_FOUND,
//                    getResponseCodeFromAlias(getConnection("/0", port)));
//        } finally {
//            stopHttpServer();
//        }
//
//    }

    /**
     * Test if {@link HttpServer#start} throws {@link IOException} if can't bind.
     *
     * @throws IOException Error.
     */
    public void testStartContract() throws IOException {
        System.out.println("testStartContract");
        // lock port
        int port = PORT + 5;
        ServerSocket soc = null;
        try {
            soc = new ServerSocket(port);
        } catch (IOException e) {
            fail("Could not bind to port: " + port + ". " + e.getMessage());
        }

        System.out.println("Bound to port: " + port);

        try {
            httpServer = HttpServer.createSimpleServer(".", port);
            httpServer.getListener("grizzly").getTransport().setReuseAddress(false);
            httpServer.start();
            fail("Should throw exception that can't bind to port.");
        } catch (IOException e) {
            System.out.println("Ha got you this time.");
        } finally {
            soc.close();
            stopHttpServer();
        }
    }

    /**
     * Tests that {@link HttpServer} will start properly in secure mode with modified configuration.
     *
     * @throws IOException Not much to say here.
     * @throws URISyntaxException Could not find keystore file.
     * @throws GeneralSecurityException Security failure.
     */
    public void testStartSecureWithConfiguration()
            throws IOException, URISyntaxException, GeneralSecurityException {
        System.out.println("testStartSecureWithConfiguration");
        URL resource = getClass().getClassLoader().getResource("test-keystore.jks");

        SSLContextConfigurator sslContextConfig = new SSLContextConfigurator();
        sslContextConfig.setKeyStorePass("changeit");
        if (resource != null) {
            sslContextConfig.setKeyStoreFile(new File(resource.toURI()).getAbsolutePath());
        } else {
            fail("Couldn't find keystore");
        }
        final int port = PORT + 7;
        httpServer = HttpServer.createSimpleServer(".", port);

        httpServer.getListener("grizzly").setSecure(true);
        httpServer.getListener("grizzly").setSSLEngineConfig(
                new SSLEngineConfigurator(sslContextConfig.createSSLContext(), false, false, false));

//        gws.setSSLConfig(cfg);
        final String encMsg = "Secured.";
        httpServer.getServerConfiguration().addHttpHandler(new HttpHandler() {
            @Override
            public void service(Request request, Response response) {
                response.setStatus(200);
                try {
                    response.getWriter().write(encMsg);
                } catch (IOException e) {
                    response.setStatus(500, "Server made a boo.");
                }
            }
        }, "/sec");
        try {
            httpServer.start();
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

                SSLContextConfigurator sslClientContextConfig = new SSLContextConfigurator();
                sslClientContextConfig.setTrustStoreFile(new File(uri).getAbsolutePath());
                sslClientContextConfig.setTrustStorePass("changeit");

                HttpsURLConnection.setDefaultSSLSocketFactory(
                        sslClientContextConfig.createSSLContext().getSocketFactory());
            } else {
                fail("Couldn't find truststore");
            }

            HttpsURLConnection.setDefaultHostnameVerifier(new HostnameVerifier() {
                @Override
                public boolean verify(String s, SSLSession sslSession) {
                    return true;
                }
            });
            HttpURLConnection conn =
                    (HttpURLConnection) new URL("https", "localhost", port, "/sec").openConnection();
            assertEquals(HttpServletResponse.SC_OK, getResponseCodeFromAlias(conn));
            assertEquals(encMsg, readResponse(conn));
        } finally {
            httpServer.shutdownNow();
        }
    }

    /**
     * Tests if {@link Filter} is getting destroyed on {@link HttpServer#stop}.
     *
     * @throws IOException Couldn't start {@link HttpServer}.
     */
    public void testFilterLifecycle() throws IOException {

        final int port = PORT + 8;
        httpServer = HttpServer.createSimpleServer(".", port);
        final boolean init[] = new boolean[]{false};
        final boolean filter[] = new boolean[]{false};
        final boolean destroy[] = new boolean[]{false};
        WebappContext ctx = new WebappContext("Test");

        // Overload the test by not adding a Servlet to the webapp.
        // This means the default Servlet will be invoked in the FilterChain.
        // The default servlet should return a 404 as /foo can't be resolved.
        FilterRegistration reg = ctx.addFilter("filter", new Filter() {
            @Override
            public void init(final FilterConfig filterConfig) {
                assertEquals("filter", filterConfig.getFilterName());
                init[0] = true;
            }

            @Override
            public void doFilter(
                final ServletRequest request, final ServletResponse response,
                final FilterChain chain) throws IOException, ServletException {
                filter[0] = true;
                chain.doFilter(request, response);
            }

            @Override
            public void destroy() {
                destroy[0] = true;
            }
        });
        reg.addMappingForUrlPatterns(null, "/*");
        ctx.deploy(httpServer);

        httpServer.start();
        HttpURLConnection conn =
                    (HttpURLConnection) new URL("http", "localhost", port, "/foo").openConnection();
            assertEquals(HttpServletResponse.SC_NOT_FOUND, getResponseCodeFromAlias(conn));
        ctx.undeploy();
        httpServer.shutdownNow();
        assertTrue(init[0]);
        assertTrue(filter[0]);
        assertTrue(destroy[0]);
    }

    public void testFilterLifecycleByServletName() throws IOException {

        final int port = PORT + 8;
        httpServer = HttpServer.createSimpleServer(".", port);
        final boolean init[] = new boolean[]{false};
        final boolean filter[] = new boolean[]{false};
        final boolean destroy[] = new boolean[]{false};
        WebappContext ctx = new WebappContext("Test");

        addServlet(ctx, "/test");

        FilterRegistration reg = ctx.addFilter("filter", new Filter() {
            @Override
            public void init(final FilterConfig filterConfig) {
                assertEquals("filter", filterConfig.getFilterName());
                init[0] = true;
            }

            @Override
            public void doFilter(
                    final ServletRequest request, final ServletResponse response,
                    final FilterChain chain) throws IOException, ServletException {
                filter[0] = true;
                chain.doFilter(request, response);
            }

            @Override
            public void destroy() {
                destroy[0] = true;
            }
        });
        reg.addMappingForServletNames(null, "/test");
        ctx.deploy(httpServer);

        httpServer.start();
        HttpURLConnection conn =
                (HttpURLConnection) new URL("http", "localhost", port, "/test").openConnection();
        assertEquals(HttpServletResponse.SC_OK, getResponseCodeFromAlias(conn));
        ctx.undeploy();
        httpServer.shutdownNow();
        assertTrue(init[0]);
        assertTrue(filter[0]);
        assertTrue(destroy[0]);
    }

    /**
     * Test for https://grizzly.dev.java.net/issues/show_bug.cgi?id=513
     *
     * @throws IOException Fail.
     */
    public void testAddHttpHandlerBeforeAndAfterStart() throws IOException {
        System.out.println("testAddHttpHandlerBeforeAndAfterStart");
        try {
            final int port = PORT + 9;
            httpServer = HttpServer.createSimpleServer(".", port);
            WebappContext ctx = new WebappContext("Test");
            String[] aliases = new String[]{"/1"};
            for (String alias : aliases) {
                addServlet(ctx, alias);
            }
            ctx.deploy(httpServer);
            httpServer.start();
            for (String alias : aliases) {
                HttpURLConnection conn = getConnection(alias, port);
                assertEquals(HttpServletResponse.SC_OK,
                        getResponseCodeFromAlias(conn));
                assertEquals(alias, readResponse(conn));
            }
            String context = "/ctx2";
            WebappContext ctx2 = new WebappContext("Test2", context);
            String alias = "/2";
            addServlet(ctx2, alias);
            ctx2.deploy(httpServer);

            HttpURLConnection conn = getConnection(context + alias, port);
            assertEquals(HttpServletResponse.SC_OK,
                    getResponseCodeFromAlias(conn));
            assertEquals(alias, readResponse(conn));
        } finally {
            stopHttpServer();
        }
    }

    public void testMultipleAddHttpHandlerBeforeStartAndOneAfter() throws IOException {
        System.out.println("testMultipleAddHttpHandlerBeforeStartAndOneAfter");
        try {
            final int port = PORT + 10;
            httpServer = HttpServer.createSimpleServer(".", port);
            WebappContext ctx = new WebappContext("Test");
            String[] aliases = new String[]{"/1", "/2", "/3"};
            for (String alias : aliases) {
                addServlet(ctx, alias);
            }
            ctx.deploy(httpServer);
            httpServer.start();
            for (String alias : aliases) {
                HttpURLConnection conn = getConnection(alias, port);
                assertEquals(HttpServletResponse.SC_OK,
                        getResponseCodeFromAlias(conn));
                assertEquals(alias, readResponse(conn));
            }
            String context = "/ctx2";
            WebappContext ctx2 = new WebappContext("Test2", context);
            String alias = "/4";
            addServlet(ctx2, alias);
            ctx2.deploy(httpServer);

            HttpURLConnection conn = getConnection(context + alias, port);
            assertEquals(HttpServletResponse.SC_OK,
                    getResponseCodeFromAlias(conn));
            assertEquals(alias, readResponse(conn));
        } finally {
            stopHttpServer();
        }
    }

    private ServletRegistration addServlet(final WebappContext ctx,
                                           final String alias) {
        ServletRegistration reg = ctx.addServlet(alias, new HttpServlet() {

            @Override
            protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                logger.log(Level.INFO, "{0} received request {1}", new Object[]{alias, req.getRequestURI()});
                resp.setStatus(HttpServletResponse.SC_OK);
                resp.getWriter().write(alias);
            }
        });
        reg.addMapping(alias);
        return reg;
    }
}
