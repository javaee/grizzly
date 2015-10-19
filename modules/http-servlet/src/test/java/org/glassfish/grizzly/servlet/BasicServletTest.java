/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2009-2015 Oracle and/or its affiliates. All rights reserved.
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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.SocketFactory;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import junit.framework.AssertionFailedError;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.http.server.Response;
import org.glassfish.grizzly.impl.FutureImpl;
import org.glassfish.grizzly.utils.Futures;
import org.junit.Test;


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
        System.out.println("testServletName");
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
        System.out.println("testSetHeaderTest");
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
        System.out.println("testPathInfo");
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
//        System.out.println("testNotAllowEncodedSlash");
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
//        System.out.println("testAllowEncodedSlash");
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
        System.out.println("testDoubleSlash");
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
            System.out.println("s: " + s);
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
            System.out.println("s: " + s);
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
        System.out.println("testContextParameters");
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
        System.out.println("testContextListener");
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
    
    /**
     * Tests isCommitted().
     *
     * @throws Exception
     *             If an unexpected IO error occurred.
     */
    public void testIsCommitted() throws Exception {
        System.out.println("testIsCommitted");
        try {
            final FutureImpl<Boolean> resultFuture = Futures.createSafeFuture();
            
            newHttpServer(PORT);
            final WebappContext ctx = new WebappContext("example", "/example");
            final ServletRegistration reg = ctx.addServlet("managed", new HttpServlet() {
                @Override
                protected void service(HttpServletRequest req, HttpServletResponse resp)
                        throws ServletException, IOException {
                    req.startAsync();
                    resp.isCommitted();
                    resp.sendError(400, "Four hundred");
                    try {
                        resp.isCommitted();
                        resultFuture.result(Boolean.TRUE);
                    } catch (Exception e) {
                        resultFuture.failure(e);
                    }
                }
            });
            reg.addMapping("/managed/*");
            ctx.deploy(httpServer);

            httpServer.start();
            
            HttpURLConnection conn = getConnection("/example/managed/users", PORT);
            assertEquals(400, conn.getResponseCode());
            assertTrue(resultFuture.get(10, TimeUnit.SECONDS));
        } finally {
            httpServer.shutdownNow();
        }
    }
    
    /**
     * Related to the issue
     * https://java.net/jira/browse/GRIZZLY-1578
     */
    @Test
    public void testInputStreamMarkReset() throws Exception {
        final String param1Name = "j_username";
        final String param2Name = "j_password";
        final String param1Value = "admin";
        final String param2Value = "admin";
        
        newHttpServer(PORT);
        final WebappContext ctx = new WebappContext("example", "/");
        final ServletRegistration reg = ctx.addServlet("paramscheck", new HttpServlet() {
            @Override
            protected void doPost(HttpServletRequest req, HttpServletResponse resp)
                    throws ServletException, IOException {
                
                try {
                    final InputStream is = req.getInputStream();
                    assertTrue(is.markSupported());
                    is.mark(1);
                    assertEquals('j', is.read());
                    is.reset();
                    assertEquals(param1Value, req.getParameter(param1Name));
                    assertEquals(param2Value, req.getParameter(param2Name));
                } catch (Throwable t) {
                    LOGGER.log(Level.SEVERE, "Error", t);
                    resp.sendError(500, t.getMessage());
                }
                
            }
        });
        reg.addMapping("/");
        ctx.deploy(httpServer);

        Socket s = null;
        try {
            httpServer.start();
            final String postHeader = "POST / HTTP/1.1\r\n"
                    + "Host: localhost:" + PORT + "\r\n"
                    + "User-Agent: Mozilla/5.0 (iPod; CPU iPhone OS 5_1_1 like Mac OS X) AppleWebKit/534.46 (KHTML, like Gecko) Version/5.1 Mobile/9B206 Safari/7534.48.3\r\n"
                    + "Content-Length: 33\r\n"
                    + "Accept: */*\r\n"
                    + "Origin: http://192.168.1.165:9998\r\n"
                    + "X-Requested-With: XMLHttpRequest\r\n"
                    + "Content-Type: application/x-www-form-urlencoded; charset=UTF-8\r\n"
                    + "Referer: http://192.168.1.165:9998/\r\n"
                    + "Accept-Language: en-us\r\n"
                    + "Accept-Encoding: gzip, deflate\r\n"
                    + "Cookie: JSESSIONID=716476212401473028\r\n"
                    + "Connection: keep-alive\r\n\r\n";

            final String postBody = param1Name + "=" + param1Value + "&" +
                    param2Name + "=" + param2Value;
        
            s = SocketFactory.getDefault().createSocket("localhost", PORT);
            OutputStream out = s.getOutputStream();
            BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()));

            out.write(postHeader.getBytes());
            out.flush();
            Thread.sleep(100);
            out.write(postBody.getBytes());
            out.flush();

            assertEquals("HTTP/1.1 200 OK", in.readLine());

        } finally {
            httpServer.shutdownNow();
            if (s != null) {
                s.close();
            }
        }

    }

    /**
     * https://java.net/jira/browse/GRIZZLY-1772
     */
    public void testLoadServletDuringParallelRequests() throws Exception {
        System.out.println("testLoadServletDuringParallelRequests");

        final InitBlocker blocker = new InitBlocker();
        InitBlockingServlet.setBlocker(blocker);
        try {
            newHttpServer(PORT);
            WebappContext ctx = new WebappContext("testParallelLoadServlet");
            ServletRegistration servlet = ctx.addServlet("InitBlockingServlet", InitBlockingServlet.class);
            servlet.addMapping("/initBlockingServlet");

            ctx.deploy(httpServer);
            httpServer.start();

            final FutureTask<Void> request1 = new FutureTask<Void>(new Callable<Void>() {
                @Override
                public Void call() throws Exception {
                    assertEquals(200, getConnection("/initBlockingServlet", PORT).getResponseCode());
                    return null;
                }
            });

            final FutureTask<Void> request2 = new FutureTask<Void>(new Callable<Void>() {
                @Override
                public Void call() throws Exception {
                    assertEquals(200, getConnection("/initBlockingServlet", PORT).getResponseCode());
                    return null;
                }
            });

            new Thread(request1).start();
            blocker.waitUntilInitCalled();
            new Thread(request2).start();

            try {
                request2.get(1, TimeUnit.SECONDS);
            } catch (TimeoutException ignored) {
                // request2 should block until the servlet instance is initialized by request1
            } finally {
                blocker.releaseInitCall();
            }

            request1.get();
            request2.get();

        } finally {
            try {
                blocker.releaseInitCall();
                InitBlockingServlet.removeBlocker(blocker);
            } finally {
                stopHttpServer();
            }
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

    private static final class InitBlocker {
        private boolean initReleased, initCalled;

        synchronized void notifyInitCalledAndWaitForRelease() throws InterruptedException {
            assertFalse("init has already been called", initCalled);
            initCalled = true;
            notifyAll();
            while (!initReleased) {
                wait();
            }
        }

        synchronized void releaseInitCall() {
            initReleased = true;
            notifyAll();
        }

        synchronized void waitUntilInitCalled() throws InterruptedException {
            while (!initCalled) {
                wait();
            }
        }
    }

    public static final class InitBlockingServlet extends HttpServlet {
        private static final AtomicReference<InitBlocker> BLOCKER = new AtomicReference<InitBlocker>();

        private volatile boolean initialized;

        static void setBlocker(InitBlocker blocker) {
            assertNotNull(blocker);
            assertTrue(BLOCKER.compareAndSet(null, blocker));
        }

        static void removeBlocker(InitBlocker blocker) {
            assertTrue(BLOCKER.compareAndSet(blocker, null));
        }

        @Override
        public void init(ServletConfig config) throws ServletException {
            super.init(config);

            InitBlocker blocker = BLOCKER.get();
            assertNotNull(blocker);

            try {
                blocker.notifyInitCalledAndWaitForRelease();
            } catch (InterruptedException e) {
                throw (Error) new AssertionFailedError().initCause(e);
            }

            initialized = true;
        }

        @Override
        protected void service(HttpServletRequest req, HttpServletResponse resp) {
            boolean ok = initialized;
            resp.setStatus(ok ? 200 : 500);
        }
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
