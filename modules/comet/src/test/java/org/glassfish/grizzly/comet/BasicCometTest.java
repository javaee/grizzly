/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2009-2012 Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.Collection;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import junit.framework.TestCase;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.grizzly.http.server.NetworkListener;

/**
 * Basic Comet Test.
 *
 * @author Jeanfrancois Arcand
 * @author Gustav Trede
 */
public class BasicCometTest extends TestCase {
    private static final Logger LOGGER = Grizzly.logger(BasicCometTest.class);

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
        cometContext = CometEngine.getEngine().<String>register("GrizzlyAdapter");
    }

    @Override
    protected void tearDown() throws Exception {
        stopHttpServer();
        super.tearDown();
    }

    public void testOnInterruptExpirationDelay() throws Exception {
        LOGGER.fine("testOnInterruptExpirationDelay - will wait 2 seconds");
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
        LOGGER.fine("testClientCloseConnection");
        cometContext.setExpirationDelay(-1);
        String alias = "/OnClientCloseConnection";
        final CometHttpHandler ga = addHttpHandler(alias, true);
        Socket s = new Socket("localhost", PORT);
        s.setSoLinger(false, 0);
        s.setSoTimeout(500);
        OutputStream os = s.getOutputStream();
        String a = "GET " + alias + " HTTP/1.1\n" + "Host: localhost:" + PORT + "\n\n";
        LOGGER.log(Level.FINE, "     {0}", a);
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
        LOGGER.fine("testOnTerminate ");
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

    public void testOnEvent() throws Exception {
        LOGGER.fine("testOnEvent ");
        final String alias = "/OnEvent";
        cometContext.setExpirationDelay(-1);
        final CountDownHttpHandler httpHandler = new CountDownHttpHandler(cometContext, true);
        httpServer.getServerConfiguration().addHttpHandler(httpHandler, alias);
        HttpURLConnection conn = getConnection(alias, 2000);
        conn.getContent();
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
            httpServer.stop();
        }
    }
}