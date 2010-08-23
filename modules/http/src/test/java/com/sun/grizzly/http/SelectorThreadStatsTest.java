/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2010 Oracle and/or its affiliates. All rights reserved.
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

import com.sun.grizzly.ControllerStateListenerAdapter;
import com.sun.grizzly.http.utils.SelectorThreadUtils;
import com.sun.grizzly.tcp.Adapter;
import com.sun.grizzly.tcp.OutputBuffer;
import com.sun.grizzly.tcp.Request;
import com.sun.grizzly.tcp.Response;
import com.sun.grizzly.util.ExtendedThreadPool;
import com.sun.grizzly.util.Utils;
import com.sun.grizzly.util.buf.ByteChunk;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URL;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Level;
import java.util.logging.Logger;

import junit.framework.TestCase;

/**
 *
 * @author Peter Speck
 * @author Jeanfrancois Arcand
 */
public class SelectorThreadStatsTest extends TestCase {

    public static final int PORT = 18890;
    private static Logger logger = Logger.getLogger("grizzly.test");
    private SelectorThread st;

    public void createSelectorThread() throws Exception {
        st = new SelectorThread() {

            /**
             * Start the SelectorThread using its own thread and don't block the Thread.
             *  This method should be used when Grizzly is embedded.
             */
            @Override
            public void listen() throws IOException, InstantiationException {
                initEndpoint();
                final CountDownLatch latch = new CountDownLatch(1);
                controller.addStateListener(new ControllerStateListenerAdapter() {

                    @Override
                    public void onReady() {
                        enableMonitoring();
                        latch.countDown();
                    }

                    @Override
                    public void onException(Throwable e) {
                        if (latch.getCount() > 0) {
                            logger().log(Level.SEVERE, "Exception during " +
                                    "starting the controller", e);
                            latch.countDown();
                        } else {
                            logger().log(Level.SEVERE, "Exception during " +
                                    "controller processing", e);
                        }
                    }
                });

                super.start();

                try {
                    latch.await();
                } catch (InterruptedException ex) {
                }

                if (!controller.isStarted()) {
                    throw new IllegalStateException("Controller is not started!");
                }
            }
        };
        
        st.setPort(PORT);
        st.setAdapter(new MyAdapter());
        st.setCompression("off"); // don't let proxy compress stuff that's already compressed.
        st.setDisplayConfiguration(Utils.VERBOSE_TESTS);
        st.setFileCacheIsEnabled(false);
        st.setLargeFileCacheEnabled(false);
        st.setBufferResponse(false);
        st.setKeepAliveTimeoutInSeconds(30000);

        st.initThreadPool();
        ((ExtendedThreadPool) st.getThreadPool()).setMaximumPoolSize(50);
        
        st.setBufferSize(32768);
        st.setMaxKeepAliveRequests(8196);
        //st.setKeepAliveThreadCount(500);

            st.listen();
            st.enableMonitoring();

    }

    public void testKeepAliveConnection() throws Exception {
        try {
            createSelectorThread();
            String testString = "KAS:  conns=1, flushes=0, hits=1, refusals=0, timeouts=0";

            byte[] testData = testString.getBytes();
            byte[] response = new byte[testData.length];

            URL url = new URL("http://localhost:" + PORT);
            HttpURLConnection connection =
                    (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            OutputStream os = connection.getOutputStream();
            os.write(testString.getBytes());
            os.flush();
            InputStream is = new DataInputStream(connection.getInputStream());
            response = new byte[testData.length];
            is.read(response);
            Utils.dumpOut("Response: " + new String(response));
            assertEquals(testString, new String(response));
            connection.disconnect();
        } finally {
            SelectorThreadUtils.stopSelectorThread(st);
        }
    }

    public void testGetHits() throws Exception {

        try {
            createSelectorThread();
            String testString = "KAS:  conns=1, flushes=0, hits=2, refusals=0, timeouts=0";

            byte[] testData = testString.getBytes();
            String response = "";

            
            Socket socket = new Socket("localhost", PORT);
            socket.setSoTimeout(30 * 1000);
            DataOutputStream os = new DataOutputStream(socket.getOutputStream());

            os.write("POST / HTTP/1.1\r\n".getBytes());
            os.write("Content-type: text/plain\r\n".getBytes());
            os.write("Host: localhost\r\n".getBytes());
            os.write(("Content-Length: " + testData.length +"\r\n\r\n").getBytes());
            os.write(testData);
            os.flush();

            InputStream is = socket.getInputStream();
            BufferedReader bis = new BufferedReader(new InputStreamReader(is));
            String line = null;

            boolean flip = true;
            boolean first = true;
            while ((line = bis.readLine()) != null) {
                Utils.dumpOut("-> " + line);
                if (line.startsWith("HTTP/1.1 200") && flip){
                    Utils.dumpOut("Post second request");
                    os.write("POST / HTTP/1.1\r\n".getBytes());
                    os.write("Content-type: text/plain\r\n".getBytes());
                    os.write("Host: localhost\r\n".getBytes());
                    os.write(("Content-Length: " + testData.length +"\r\n\r\n").getBytes());
                    os.write(testData);
                    os.flush();        
                    flip = false;
                } else if (line.startsWith("KAS: ")){
                    if (first) {
                        first = false;
                        continue;
                    } else {
                        response = line;
                        break;
                    }         
                }
            }  

            Utils.dumpOut("Response: " + response);
             
            assertEquals(testString, response);
        } finally {
            SelectorThreadUtils.stopSelectorThread(st);
        }
    }

    class MyAdapter implements Adapter {
        // Just in case this code is cut&pasted
        public synchronized void service(Request request, Response response) throws Exception {
            
            Utils.dumpOut("Request: " + request);
            
            KeepAliveStats kas = st.getKeepAliveStats();
            String s;
            if (!kas.isEnabled()) {
                s = "KAS: is disabled\n";
            } else {
                s = "KAS:  conns=" + kas.getCountConnections() + ", flushes="
                        + kas.getCountFlushes() + ", hits=" + kas.getCountHits()
                        + ", refusals=" + kas.getCountRefusals() + ", timeouts=" 
                        + kas.getCountTimeouts() + "\n";
            }
            Utils.dumpOut("----->" + s);
            byte[] b = s.getBytes("iso-8859-1");
            sendPlainText(response, b);
        }

        private void sendPlainText(Response response, byte[] b) throws IOException {
            response.setContentType("text/plain");
            response.setContentLength(b.length);
            ByteChunk chunk = new ByteChunk();
            chunk.append(b, 0, b.length);
            OutputBuffer buffer = response.getOutputBuffer();
            buffer.doWrite(chunk, response);
            response.finish();
        }

        public void afterService(Request request, Response response) throws Exception {
            request.recycle();
            response.recycle();
        }

        public void fireAdapterEvent(String string, Object object) {
        }
    }
}
