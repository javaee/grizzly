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
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.logging.Level;
import junit.framework.TestCase;


import java.net.HttpURLConnection;
import java.net.URL;
import java.util.logging.Logger;

/**
 * Basic Servlet Test.
 *
 * @author Jeanfrancois Arcand
 */
public class BasicCometTest extends TestCase {

    public static final int PORT = 18890;
    private static Logger logger = Logger.getLogger("grizzly.test");
    private GrizzlyWebServer gws;
    private String onInitialize = "onInitialize";
    private String onTerminate = "onTerminate";
    private String onInterrupt = "onInterrupt";
    private String onEvent = "onEvent";
    final CometContext test = CometEngine.getEngine().register("GrizzlyAdapter");

    public void testOnEvent() throws IOException {
        System.out.println("testOnEvent - will wait 5 seconds");
        try {
            newGWS(PORT);
            String alias = "/OnEvemt";
            addAdapter(alias);

            gws.start();
            new Thread(){

                @Override
                public void run() {
                    try {
                        Thread.sleep(5 * 1000);
                        test.notify(onEvent);
                    } catch (Throwable ex) {
                        Logger.getLogger(BasicCometTest.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            }.start();

            HttpURLConnection conn = getConnection(alias);
            String s = conn.getHeaderField(onInitialize);
            assertEquals(s, onInitialize);

            s = conn.getHeaderField(onEvent);
            assertEquals(s, onEvent);

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

    private GrizzlyAdapter addAdapter(final String alias) {
        GrizzlyAdapter adapter = new GrizzlyAdapter() {

            @Override
            public void service(GrizzlyRequest request, GrizzlyResponse response) {
                DefaultCometHandler c = new DefaultCometHandler();
                c.attach(response);
                test.addCometHandler(c);
            }
        };
        gws.addGrizzlyAdapter(adapter, new String[]{alias});
        return adapter;
    }

    private void newGWS(int port) throws IOException {
        gws = new GrizzlyWebServer(port);
        gws.addAsyncFilter(new CometAsyncFilter());
    }

    private void stopGrizzlyWebServer() {
        gws.stop();
    }

    static class DefaultCometHandler implements CometHandler<GrizzlyResponse> {

        private GrizzlyResponse response;

        public void attach(GrizzlyResponse response) {
            this.response = response;
        }

        public void onEvent(CometEvent event) throws IOException {
            System.out.println("-> " + event.attachment());
            response.addHeader("onEvent", event.attachment().toString());
            response.getWriter().print("onEvent");
            event.getCometContext().resumeCometHandler(this);
        }

        public void onInitialize(CometEvent event) throws IOException {
            String test = (String) event.attachment();
            if (test == null) {
                test = "onInitialize";
            }
            response.addHeader("onInitialize", test);
        }

        public void onTerminate(CometEvent event) throws IOException {
            response.addHeader("onTerminate", event.attachment().toString());
            response.getWriter().print("onTerminate");
        }

        public void onInterrupt(CometEvent event) throws IOException {
            response.addHeader("onInterrupt", event.attachment().toString());
            response.getWriter().print("onInterrupt");
        }
    }
}
