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

package com.sun.grizzly.comet;

import com.sun.grizzly.http.embed.GrizzlyWebServer;
import com.sun.grizzly.tcp.http11.GrizzlyAdapter;
import com.sun.grizzly.tcp.http11.GrizzlyRequest;
import com.sun.grizzly.tcp.http11.GrizzlyResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.util.logging.Level;

import com.sun.grizzly.util.Utils;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import junit.framework.TestCase;


import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.logging.Logger;

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
    
    private GrizzlyWebServer gws;
    private static final int PORT = 18890;
    CometContext test;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        test = CometEngine.getEngine().register("GrizzlyAdapter");
        test.setBlockingNotification(false);
        test.setDetectClosedConnections(true);
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        test.handlers.clear();
        stopGrizzlyWebServer();       
    }

    public void testOnInterruptExpirationDelay() throws Exception {
        Utils.dumpOut("testOnInterruptExpirationDelay - will wait 2 seconds");
        final int delay = 2000;
        test.setExpirationDelay(delay);
        newGWS(PORT);
        String alias = "/OnInterrupt";
        addAdapter(alias, false);
        gws.start();

        HttpURLConnection conn = getConnection(alias,delay+4000);
        long t1 = System.currentTimeMillis();
        assertEquals(onInitialize, conn.getHeaderField(onInitialize));
        assertEquals(onInterrupt, conn.getHeaderField(onInterrupt));
        long delta = System.currentTimeMillis() - t1;
        assertTrue("comet idletimeout was too fast,"+delta+"ms",delta > delay-250);
        assertTrue("comet idletimeout was too late,"+delta+"ms",delta < delay+3000);
    }
    
    public void testClientCloseConnection() throws Exception {
        Utils.dumpOut("testClientCloseConnection");
        newGWS(PORT);
        test.setExpirationDelay(-1);
        String alias = "/OnClientCloseConnection";
        final CometGrizzlyAdapter ga = addAdapter(alias, true);
        gws.start();

        Socket s = new Socket("localhost", PORT);
        s.setSoLinger(false, 0);
        s.setSoTimeout(1 * 1000);
        OutputStream os = s.getOutputStream();
        String a = "GET " + alias + " HTTP/1.1\n"+"Host: localhost:" + PORT + "\n\n";
        Utils.dumpOut("     "+a);
        os.write(a.getBytes());
        os.flush();
        try {
            s.getInputStream().read();
            fail("client socket read did not read timeout");
        } catch (SocketTimeoutException ex) {
            s.close();
            Thread.sleep(500);
            assertEquals(onInterrupt, ga.c.wasInterrupt);
        }
    }

    public void testHttpPipeline() throws Exception {
        Utils.dumpOut("testHttpPipeline");
        newGWS(PORT);
        test.setExpirationDelay(10000);
        test.setDetectClosedConnections(false);
        final String alias = "/testPipeline";
        final CometGrizzlyAdapter ga = addAdapter(alias, true);
        gws.addGrizzlyAdapter(new GrizzlyAdapter() {

            @Override
            public void service(GrizzlyRequest request, GrizzlyResponse response) throws Exception {
                CometEngine.getEngine().getCometContext("GrizzlyAdapter").notify("Ping");
                response.getWriter().write("Done");
            }
        }, new String[] {"/notify"});

        gws.addGrizzlyAdapter(new GrizzlyAdapter() {

            @Override
            public void service(GrizzlyRequest request, GrizzlyResponse response) throws Exception {
                response.getWriter().write("Static");
            }
        }, new String[] {"/static"});
        
        gws.start();

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
//                        System.out.println(line);

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
        Utils.dumpOut("testHttpPipeline2");
        newGWS(PORT);
        test.setExpirationDelay(10000);
        test.setDetectClosedConnections(false);
        final String alias = "/testPipeline2";
        final CometGrizzlyAdapter ga = addAdapter(alias, true);
        gws.addGrizzlyAdapter(new GrizzlyAdapter() {

            @Override
            public void service(GrizzlyRequest request, GrizzlyResponse response) throws Exception {
                CometEngine.getEngine().getCometContext("GrizzlyAdapter").notify("Ping");
                response.getWriter().write("Done");
            }
        }, new String[] {"/notify"});

        gws.addGrizzlyAdapter(new GrizzlyAdapter() {

            @Override
            public void service(GrizzlyRequest request, GrizzlyResponse response) throws Exception {
                response.getWriter().write("Static");
            }
        }, new String[] {"/static"});
        
        gws.start();

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
//                        System.out.println(line);

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
    
    /* public void testOnTerminate() throws IOException {
        Utils.dumpOut("testOnTerminate ");
        test.setExpirationDelay(-1);
        newGWS(PORT+=3);
        String alias = "/OnTerminate";
        final CometGrizzlyAdapter ga = addAdapter(alias,true);
        gws.start();
        new Thread() {
            @Override
            public void run() {
                try {
                    Thread.sleep(200);
                    CometEngine.getEngine().unregister(test.topic);
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

    public void testOnEvent() throws Exception {
        Utils.dumpOut("testOnEvent ");
        newGWS(PORT);
        String alias = "/OnEvent";
        addAdapter(alias, true);
        test.setExpirationDelay(-1);
        gws.start();
        int iter = 10;
        while(iter-->0){
            new Thread() {
                @Override
                public void run() {
                    try {
                        Thread.sleep(150);
                        test.notify(onEvent);
                    } catch (Throwable ex) {
                        Logger.getLogger(BasicCometTest.class.getName()).log(Level.SEVERE, null, ex);
                        fail("sleep/notify exception:"+ex.getMessage());
                    }
                }
            }.start();
            HttpURLConnection conn = getConnection(alias,1000);
            assertEquals(conn.getHeaderField(onInitialize), onInitialize);
            assertEquals(conn.getHeaderField(onEvent),   onEvent);
            conn.disconnect();
        }
    }
    
    private HttpURLConnection getConnection(String alias) throws IOException {
        return getConnection(alias, 40*1000);
    }

    private HttpURLConnection getConnection(String alias, int readtimeout) throws IOException {
        URL url = new URL("http", "localhost", PORT, alias);
        HttpURLConnection urlConn = (HttpURLConnection) url.openConnection();
        urlConn.setConnectTimeout(5*1000);
        urlConn.setReadTimeout(readtimeout);
        urlConn.connect();
        return urlConn;
    }

    private int getResponseCodeFromAlias(HttpURLConnection urlConn)
            throws IOException {
        return urlConn.getResponseCode();
    }

    private CometGrizzlyAdapter addAdapter(final String alias, final boolean resume) {
        CometGrizzlyAdapter c = new CometGrizzlyAdapter(resume);
        gws.addGrizzlyAdapter(c, new String[] {alias});
        return c;
    }

    private void newGWS(int PORT) throws IOException {
        gws = new GrizzlyWebServer(PORT);
        gws.addAsyncFilter(new CometAsyncFilter());
    }

    private void stopGrizzlyWebServer() {
        gws.stop();
    }

    class CometGrizzlyAdapter extends GrizzlyAdapter {

        private final boolean resume;
        
        volatile DefaultCometHandler c;

        public CometGrizzlyAdapter(boolean resume) {
            this.resume = resume;
        }

        @Override
        public void service(GrizzlyRequest request, GrizzlyResponse response) {
            c = new DefaultCometHandler(resume);
            c.attach(response);
            test.addCometHandler(c);
        }
    }

    static class DefaultCometHandler implements CometHandler<GrizzlyResponse> {

        private final boolean resume;
        private volatile GrizzlyResponse response;        
                volatile String wasInterrupt = "";

        public DefaultCometHandler(boolean resume) {
            this.resume = resume;
        }

        public void attach(GrizzlyResponse response) {
            this.response = response;
        }

        public void onEvent(CometEvent event) throws IOException {
            Utils.dumpOut("     -> onEvent Handler:"+this.hashCode());
            response.addHeader("onEvent", event.attachment().toString());
            response.getWriter().print("onEvent");
            if (resume) {
                event.getCometContext().resumeCometHandler(this);
            }
        }

        public void onInitialize(CometEvent event) throws IOException {
           Utils.dumpOut("     -> onInitialize Handler:"+this.hashCode());
             
            String test = (String) event.attachment();
            if (test == null) {
                test = onInitialize;
            }
            response.addHeader(onInitialize, test);
        }

        public void onTerminate(CometEvent event) throws IOException {
            Utils.dumpOut("    -> onTerminate Handler:"+this.hashCode());
 
            response.addHeader(onTerminate, event.attachment().toString());
            response.getWriter().print(onTerminate);
        }

        public void onInterrupt(CometEvent event) throws IOException {
            Utils.dumpOut("    -> onInterrupt Handler:"+this.hashCode());
             
            wasInterrupt = onInterrupt;
            String test = (String) event.attachment();
            if (test == null) {
                test = onInterrupt;
            }
            response.addHeader(onInterrupt, test);
            response.getWriter().print(onInterrupt);
        }
    }
}
