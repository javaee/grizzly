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
package org.glassfish.grizzly.comet;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.Collection;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
 
import junit.framework.TestCase;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.http.server.*;

/**
 * Basic Comet Test.
 *
 * @author Jeanfrancois Arcand
 * @author Gustav Trede
 */
public class BasicCometTest extends TestCase {
    private static final Logger LOGGER = Grizzly.logger(BasicCometTest.class);
    
    private static final String TEST_TOPIC = "/test-topic";

    final static String onInitialize = "onInitialize";
    final static String onTerminate = "onTerminate";
    final static String onInterrupt = "onInterrupt";
    final static String onEvent = "onEvent";
    private HttpServer httpServer;
    private int PORT = 18890;
    CometContext<String> cometContext;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        stopHttpServer();
        httpServer = HttpServer.createSimpleServer("./", PORT);
        final Collection<NetworkListener> listeners = httpServer.getListeners();
        for (NetworkListener listener : listeners) {
            listener.registerAddOn(new CometAddOn());
        }
        httpServer.start();
        cometContext = CometEngine.getEngine().<String>register(TEST_TOPIC);
    }

    @Override
    protected void tearDown() throws Exception {
        CometEngine.getEngine().deregister(TEST_TOPIC);
        stopHttpServer();
        super.tearDown();
    }

    public void testOnInterruptExpirationDelay() throws Exception {
        System.out.println("testOnInterruptExpirationDelay - will wait 2 seconds");
        final int delay = 2000;
        cometContext.setExpirationDelay(delay);
        String alias = "/OnInterrupt";
        final CometHttpHandler httpHandler = addHttpHandler(alias, false);
        HttpURLConnection conn = getConnection(alias, delay + 4000);
        long t1 = System.currentTimeMillis();
        conn.getHeaderFields();
        final DefaultTestCometHandler cometHandler = httpHandler.cometHandler;
        assertNotNull("Should get a comet handler registered", cometHandler);
        assertTrue(cometHandler.onInitializeCalled.get());
        assertTrue(cometHandler.onInterruptCalled.get());
        assertEquals(onInitialize, conn.getHeaderField(onInitialize));

        long delta = System.currentTimeMillis() - t1;
        assertTrue("comet idle timeout was too fast," + delta + "ms", delta > delay - 250);
        assertTrue("comet idle timeout was too late," + delta + "ms", delta < delay + 3000);
    }

    public void testClientCloseConnection() throws Exception {
        System.out.println("testClientCloseConnection");
        cometContext.setExpirationDelay(-1);
        String alias = "/OnClientCloseConnection";
        final CometHttpHandler ga = addHttpHandler(alias, true);
        Socket s = new Socket("localhost", PORT);
        s.setSoLinger(false, 0);
        s.setSoTimeout(500);
        OutputStream os = s.getOutputStream();
        String a = "GET " + alias + " HTTP/1.1\n" + "Host: localhost:" + PORT + "\n\n";
        System.out.println("     " + a);
        os.write(a.getBytes());
        os.flush();
        try {
            s.getInputStream().read();
            fail("client socket read did not read timeout");
        } catch (SocketTimeoutException ex) {
            s.close();
            Thread.sleep(5000);
            assertTrue(ga.cometHandler.onInterruptCalled.get());
        }
    }

    public void testOnTerminate() throws IOException, InterruptedException {
        System.out.println("testOnTerminate ");
        cometContext.setExpirationDelay(-1);
        String alias = "/OnTerminate";
        final CountDownHttpHandler httpHandler = new CountDownHttpHandler(cometContext, true);
        httpServer.getServerConfiguration().addHttpHandler(httpHandler, alias);
        HttpURLConnection conn = getConnection(alias, 5000);
        conn.getHeaderFields();

        CometEngine.getEngine().deregister(cometContext.topic);
        final CountDownCometHandler cometHandler = (CountDownCometHandler) httpHandler.cometHandler;
        assertTrue(cometHandler.onTerminate.await(10, TimeUnit.SECONDS));
        assertEquals(conn.getHeaderField(onInitialize), onInitialize);
        assertTrue(cometHandler.onTerminateCalled.get());
    }

    public void testHttpPipeline() throws Exception {
        LOGGER.fine("testHttpPipeline");
        cometContext.setExpirationDelay(10000);
        cometContext.setDetectClosedConnections(false);
        final String alias = "/testPipeline";
        
        addHttpHandler(alias, true);
        
        httpServer.getServerConfiguration().addHttpHandler(new HttpHandler() {

            @Override
            public void service(Request request, Response response) throws Exception {
                CometEngine.getEngine().getCometContext(TEST_TOPIC).notify("Ping");
                response.setContentType("plain/text");
                response.getWriter().write("Done");
                response.getWriter().flush();
            }
        }, "/notify");

        httpServer.getServerConfiguration().addHttpHandler(new HttpHandler() {

            @Override
            public void service(Request request, Response response) throws Exception {
                response.setContentType("plain/text");
                response.getWriter().write("Static");
                response.getWriter().flush();
            }
        }, "/static");

        Socket s = new Socket("localhost", PORT);
        s.setSoTimeout(10 * 1000);
        OutputStream os = s.getOutputStream();
        String cometRequest = "GET " + alias + " HTTP/1.1\nHost: localhost:" + PORT + "\n\n";
        String staticRequest = "GET /static HTTP/1.1\nHost: localhost:" + PORT + "\n\n";
        
        
        String lastCometRequest = "GET " + alias + " HTTP/1.1\n"+"Host: localhost:" + PORT + "\nConnection: close\n\n";
        
        
        String pipelinedRequest1 = cometRequest + staticRequest + cometRequest;
        String pipelinedRequest2 = cometRequest + staticRequest + lastCometRequest;
        
        String[] pipelineRequests = new String[] {pipelinedRequest1, pipelinedRequest2};
        
        try {
            for (String piplineRequest : pipelineRequests) {
                os.write(piplineRequest.getBytes());
                os.flush();

                BufferedReader reader = new BufferedReader(new InputStreamReader(s.getInputStream()));
                String line;

                int numberOfPipelinedRequests = 3;

                _outter:
                for (int i = 0; i < numberOfPipelinedRequests; i++) {
                    boolean expectStatus = true;

                    if (i % 2 == 0) {
                        // pause to give some time for comet request to reach the server
                        Thread.sleep(1000);

                        new URL("http://localhost:" + PORT + "/notify").getContent();
                    }

                    boolean expectEmpty = false;
                    while (true) {
                        line = reader.readLine();
                        //System.out.println(line);

                        if (expectEmpty) {
                            assertEquals("", line);
                            break;
                        }

                        if (expectStatus) {
                            assertEquals("HTTP/1.1 200 OK", line);
                            expectStatus = false;
                        }

                        if (line == null) {
                            break _outter;
                        } else if (line.equals("0")) {
                            expectEmpty = true;
                        }
                    }
                }
            }
        } finally {
            s.close();
        }
    }
    
    public void testHttpPipeline2() throws Exception {
        LOGGER.fine("testHttpPipeline2");
        cometContext.setExpirationDelay(10000);
        cometContext.setDetectClosedConnections(false);
        final String alias = "/testPipeline2";
        addHttpHandler(alias, true);
        
        httpServer.getServerConfiguration().addHttpHandler(new HttpHandler() {

            @Override
            public void service(Request request, Response response) throws Exception {
                CometEngine.getEngine().getCometContext(TEST_TOPIC).notify("Ping");
                response.setContentType("plain/text");
                response.getWriter().write("Done");
                response.getWriter().flush();
            }
        }, "/notify");

        httpServer.getServerConfiguration().addHttpHandler(new HttpHandler() {

            @Override
            public void service(Request request, Response response) throws Exception {
                response.setContentType("plain/text");
                response.getWriter().write("Static");
                response.getWriter().flush();
            }
        }, "/static");
        
        Socket s = new Socket("localhost", PORT);
        s.setSoTimeout(10 * 1000);
        OutputStream os = s.getOutputStream();
        String cometRequest = "GET " + alias + " HTTP/1.1\nHost: localhost:" + PORT + "\n\n";
        String staticRequest = "GET /static HTTP/1.1\nHost: localhost:" + PORT + "\n\n";
        
        
        try {
            os.write(cometRequest.getBytes());
            os.flush();
            Thread.sleep(1000);
            os.write(staticRequest.getBytes());
            os.flush();
            
            new URL("http://localhost:" + PORT + "/notify").getContent();
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(s.getInputStream()));
            String line;

            int numberOfPipelinedRequests = 2;

            _outter:
            for (int i = 0; i < numberOfPipelinedRequests; i++) {
                boolean expectStatus = true;

                boolean expectEmpty = false;
                while (true) {
                    line = reader.readLine();
                        // System.out.println(line);

                    if (expectEmpty) {
                        assertEquals("", line);
                        break;
                    }

                    if (expectStatus) {
                        assertEquals("HTTP/1.1 200 OK", line);
                        expectStatus = false;
                    }

                    if (line == null) {
                        break _outter;
                    } else if (line.equals("0")) {
                        expectEmpty = true;
                    }
                }
            }
        } finally {
            s.close();
        }
    }
    
    public void testOnEvent() throws Exception {
        System.out.println("testOnEvent ");
        final String alias = "/OnEvent";
        cometContext.setExpirationDelay(-1);
        final CountDownHttpHandler httpHandler = new CountDownHttpHandler(cometContext, true);
        httpServer.getServerConfiguration().addHttpHandler(httpHandler, alias);
        HttpURLConnection conn = getConnection(alias, 2000);
        conn.getContent();
        assertEquals("close", conn.getHeaderField("Connection"));
        final CountDownCometHandler cometHandler = (CountDownCometHandler) httpHandler.cometHandler;
        assertTrue("Should see onInitialize() get called", cometHandler.onInitialize.await(10, TimeUnit.SECONDS));
        cometContext.notify(onEvent);
        assertTrue("Should see onEvent() get called", cometHandler.onEvent.await(10, TimeUnit.SECONDS));
        conn.disconnect();
    }

    private HttpURLConnection getConnection(String alias, int timeout) throws IOException {
        HttpURLConnection urlConn = (HttpURLConnection) new URL("http", "localhost", PORT, alias).openConnection();
        urlConn.setConnectTimeout(5 * 1000);
        urlConn.setReadTimeout(timeout);
        urlConn.connect();
        return urlConn;
    }

    private CometHttpHandler addHttpHandler(String alias, boolean resume) {
        final CometHttpHandler c = new CometHttpHandler(cometContext, resume);
        httpServer.getServerConfiguration().addHttpHandler(c, alias);
        return c;
    }

    private void stopHttpServer() {
        if (httpServer != null) {
            httpServer.shutdownNow();
        }
    }
}
