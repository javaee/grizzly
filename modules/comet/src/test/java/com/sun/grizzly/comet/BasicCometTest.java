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
package com.sun.grizzly.comet;

import com.sun.grizzly.http.embed.GrizzlyWebServer;
import com.sun.grizzly.tcp.http11.GrizzlyAdapter;
import com.sun.grizzly.tcp.http11.GrizzlyRequest;
import com.sun.grizzly.tcp.http11.GrizzlyResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.util.logging.Level;
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
    private int PORT = 18890;
    CometContext test;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        test = CometEngine.getEngine().register("GrizzlyAdapter");
        test.setBlockingNotification(false);
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        test.handlers.clear();
        stopGrizzlyWebServer();       
    }

    public void testOnInterruptExpirationDelay() throws Exception {
        System.out.println("testOnInterruptExpirationDelay - will wait 2 seconds");
        final int delay = 2000;
        test.setExpirationDelay(delay);
        newGWS(PORT+=1);
        String alias = "/OnInterrupt";
        addAdapter(alias, false);
        gws.start();

        HttpURLConnection conn = getConnection(alias,delay+4000);
        long t1 = System.currentTimeMillis();
        assertEquals(conn.getHeaderField(onInitialize), onInitialize);
        assertEquals(conn.getHeaderField(onInterrupt), onInterrupt);
        long delta = System.currentTimeMillis() - t1;
        assertTrue("comet idletimeout was too fast,"+delta+"ms",delta > delay-250);
        assertTrue("comet idletimeout was too late,"+delta+"ms",delta < delay+3000);
    }
    
    public void testClientCloseConnection() throws Exception {
        System.out.println("testClientCloseConnection");
        newGWS(PORT+=2);
        test.setExpirationDelay(-1);
        String alias = "/OnClientCloseConnection";
        final CometGrizzlyAdapter ga = addAdapter(alias, true);
        gws.start();

        Socket s = new Socket("localhost", PORT);
        s.setSoLinger(false, 0);
        s.setSoTimeout(1 * 1000);
        OutputStream os = s.getOutputStream();
        String a = "GET " + alias + " HTTP/1.1\n"+"Host: localhost:" + PORT + "\n\n";
        System.out.println("     "+a);
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

   /* public void testOnTerminate() throws IOException {
        System.out.println("testOnTerminate ");
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
        System.out.println("testOnEvent ");
        newGWS(PORT+=4);
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
        gws.addGrizzlyAdapter(c, new String[]{alias});
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
            System.out.println("     -> onEvent Handler:"+this.hashCode());
            response.addHeader("onEvent", event.attachment().toString());
            response.getWriter().print("onEvent");
            if (resume) {
                event.getCometContext().resumeCometHandler(this);
            }
        }

        public void onInitialize(CometEvent event) throws IOException {
           System.out.println("     -> onInitialize Handler:"+this.hashCode());
             
            String test = (String) event.attachment();
            if (test == null) {
                test = onInitialize;
            }
            response.addHeader(onInitialize, test);
        }

        public void onTerminate(CometEvent event) throws IOException {
            System.out.println("    -> onTerminate Handler:"+this.hashCode());
 
            response.addHeader(onTerminate, event.attachment().toString());
            response.getWriter().print(onTerminate);
        }

        public void onInterrupt(CometEvent event) throws IOException {
            System.out.println("    -> onInterrupt Handler:"+this.hashCode());
             
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
