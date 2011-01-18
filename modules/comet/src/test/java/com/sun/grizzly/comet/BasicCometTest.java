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
package com.sun.grizzly.comet;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

import junit.framework.TestCase;
import org.glassfish.grizzly.comet.CometContext;
import org.glassfish.grizzly.comet.CometEngine;
import org.glassfish.grizzly.http.server.HttpHandler;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.http.server.Response;
import org.glassfish.grizzly.utils.Utils;

/**
 * Basic Comet Test.
 *
 * @author Jeanfrancois Arcand
 * @author Gustav Trede
 */
public class BasicCometTest extends TestCase {
    private final static Logger logger = Logger.getLogger("grizzly.test");
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
        CometEngine.setCometSupported(true);
        cometContext = CometEngine.getEngine().<String>register("GrizzlyAdapter");
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        stopHttpServer();
    }

    public void atestOnInterruptExpirationDelay() throws Exception {
        Utils.dumpOut("testOnInterruptExpirationDelay - will wait 2 seconds");
        final int delay = 2000;
        cometContext.setExpirationDelay(delay);
        newHttpServer(PORT += 1);
        String alias = "/OnInterrupt";
        addHttpHandler(alias, false);
        httpServer.start();
        HttpURLConnection conn = getConnection(alias, delay + 4000);
        long t1 = System.currentTimeMillis();
        assertEquals(onInitialize, conn.getHeaderField(onInitialize));
        assertEquals(onInterrupt, conn.getHeaderField(onInterrupt));
        long delta = System.currentTimeMillis() - t1;
        assertTrue("comet idletimeout was too fast," + delta + "ms", delta > delay - 250);
        assertTrue("comet idletimeout was too late," + delta + "ms", delta < delay + 3000);
    }

    public void testClientCloseConnection() throws Exception {
        Utils.dumpOut("testClientCloseConnection");
        newHttpServer(PORT += 2);
        cometContext.setExpirationDelay(-1);
        String alias = "/OnClientCloseConnection";
        final CometHttpHandler ga = addHttpHandler(alias, true);
        httpServer.start();
        Socket s = new Socket("localhost", PORT);
        s.setSoLinger(false, 0);
        s.setSoTimeout(500);
        OutputStream os = s.getOutputStream();
        String a = "GET " + alias + " HTTP/1.1\n" + "Host: localhost:" + PORT + "\n\n";
        Utils.dumpOut("     " + a);
        os.write(a.getBytes());
        os.flush();
        try {
            System.out.println("reading: " + new Date());
            s.getInputStream().read();
            fail("client socket read did not read timeout");
        } catch (SocketTimeoutException ex) {
            System.out.println("exception: " + new Date());
            s.close();
            Thread.sleep(5000);
            assertEquals(onInterrupt, ga.c.wasInterrupt);
        }
    }
    /* public void testOnTerminate() throws IOException {
        Utils.dumpOut("testOnTerminate ");
        cometContext.setExpirationDelay(-1);
        newGWS(PORT+=3);
        String alias = "/OnTerminate";
        final CometGrizzlyAdapter ga = addAdapter(alias,true);
        httpServer.start();
        new Thread() {
            @Override
            public void run() {
                try {
                    Thread.sleep(200);
                    CometEngine.getEngine().unregister(cometContext.topic);
                } catch (Throwable ex) {
                    ex.printStackTrace();
                    fail("exception:"+ex.getMessage());
                }
            }
        }.start();
        HttpURLConnection conn = getConnection(alias,1000);
        assertEquals(conn.getHeaderField(onInitialize)  , onInitialize);
        assertEquals(conn.getHeaderField(onTerminate), onTerminate);
    }*/

    public void atestOnEvent() throws Exception {
        Utils.dumpOut("testOnEvent ");
        newHttpServer(PORT += 4);
        String alias = "/OnEvent";
        addHttpHandler(alias, true);
        cometContext.setExpirationDelay(-1);
        httpServer.start();
        int iter = 10;
        while (iter-- > 0) {
            new Thread() {
                @Override
                public void run() {
                    try {
                        Thread.sleep(150);
                        cometContext.notify(onEvent);
                    } catch (Throwable ex) {
                        Logger.getLogger(BasicCometTest.class.getName()).log(Level.SEVERE, null, ex);
                        fail("sleep/notify exception:" + ex.getMessage());
                    }
                }
            }.start();
            HttpURLConnection conn = getConnection(alias, 1000);
            assertEquals(onInitialize, conn.getHeaderField(onInitialize));
            assertEquals(onEvent, conn.getHeaderField(onEvent));
            conn.disconnect();
        }
    }

    private HttpURLConnection getConnection(String alias) throws IOException {
        return getConnection(alias, 40 * 1000);
    }

    private HttpURLConnection getConnection(String alias, int readtimeout) throws IOException {
        URL url = new URL("http", "localhost", PORT, alias);
        HttpURLConnection urlConn = (HttpURLConnection) url.openConnection();
        urlConn.setConnectTimeout(5 * 1000);
        urlConn.setReadTimeout(readtimeout);
        urlConn.connect();
        return urlConn;
    }

    private int getResponseCodeFromAlias(HttpURLConnection urlConn)
        throws IOException {
        return urlConn.getResponseCode();
    }

    private CometHttpHandler addHttpHandler(final String alias, final boolean resume) {
        final CometHttpHandler c = new CometHttpHandler(resume);
        httpServer.getServerConfiguration().addHttpHandler(c, alias);
        return c;
    }

    private void newHttpServer(int port) throws IOException {
        httpServer = HttpServer.createSimpleServer("./", port);
    }

    private void stopHttpServer() {
        if(httpServer != null) {
            httpServer.stop();
        }
    }

    class CometHttpHandler extends HttpHandler {
        private final boolean resume;
        private DefaultCometHandler c;

        public CometHttpHandler(boolean resume) {
            this.resume = resume;
        }

        @Override
        public void service(Request request, Response response) {
            c = new DefaultCometHandler(cometContext, response, resume);
            cometContext.addCometHandler(c);
        }
    }

}
