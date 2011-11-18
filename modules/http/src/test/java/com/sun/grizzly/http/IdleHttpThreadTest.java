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

import com.sun.grizzly.ControllerStateListenerAdapter;
import com.sun.grizzly.http.utils.SelectorThreadUtils;
import com.sun.grizzly.tcp.Request;
import com.sun.grizzly.tcp.Response;
import com.sun.grizzly.tcp.StaticResourcesAdapter;
import com.sun.grizzly.util.Utils;
import com.sun.grizzly.util.buf.ByteChunk;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.logging.Level;
import java.util.logging.Logger;

import junit.framework.TestCase;

/**
 * Units test that exercise the killing of http idle thread.
 * @author Jeanfrancois Arcand
 */
public class IdleHttpThreadTest extends TestCase {

    public static final int PORT = 18890;
    private static final Logger logger = Logger.getLogger("grizzly.test");
    private SelectorThread st;

    public void createSelectorThread() {
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
        st.setDisplayConfiguration(Utils.VERBOSE_TESTS);

    }

    public void testKillIdleThread() throws Exception {
        Utils.dumpOut("Test: testKillIdleThread");
        final ScheduledThreadPoolExecutor pe = new ScheduledThreadPoolExecutor(1);
        final String testString = "killed";
        String response = "";
        
        try {
            createSelectorThread();
            st.setAdapter(new StaticResourcesAdapter() {

                @Override
                public void service(final Request req, final Response res) throws IOException {
                    try {
                        Utils.dumpOut("Pausing for 5 seconds");
                        Thread.currentThread().join();
                    } catch (Throwable t) {
                        Thread.interrupted();
                        t.printStackTrace();
                    } finally {
                        final String response = testString + "\n";
                        res.setContentLength(response.length());
                        ByteChunk bc = new ByteChunk();
                        bc.append(response.getBytes(), 0, response.length());
                        res.doWrite(bc);
                    }
                }

                @Override
                public void afterService(final Request req, final Response res) {
                    try {
                        super.afterService(req, res);
                    } catch (Exception ex) {
                        ex.printStackTrace();
                        fail(ex.getMessage());
                    }
                }
            });

            st.setTransactionTimeout(5 * 1000);

            st.listen();
            st.enableMonitoring();

            Socket s = new Socket("localhost", PORT);
            s.setSoTimeout(10 * 1000);
            OutputStream os = s.getOutputStream();

            Utils.dumpOut("GET / HTTP/1.1\n");
            os.write("GET / HTTP/1.1\n".getBytes());
            os.write(("Host: localhost:" + PORT + "\n").getBytes());
            os.write(("Connection: close\n").getBytes());
            os.write("\n".getBytes());

            String line;
            try {
                InputStream is = new DataInputStream(s.getInputStream());
                BufferedReader br = new BufferedReader(new InputStreamReader(is));

                while ((line = br.readLine()) != null) {
                    Utils.dumpOut("-> " + line);
                    response = line;
                }
            } catch (IOException ex) {
                //ex.printStackTrace();
                Utils.dumpOut("Timeout!");
            }
            
            assertTrue("response doesn't match!\n" + response, testString.equals(response));
        } finally {
            SelectorThreadUtils.stopSelectorThread(st);
            pe.shutdown();
        }
    }
    
    public void testKeepAliveIdleConnection() throws Exception {
        Utils.dumpOut("Test: testKeepAliveIdleConnection");
        final ScheduledThreadPoolExecutor pe = new ScheduledThreadPoolExecutor(1);
        final String testString = "timedout";
        try {
            createSelectorThread();
            st.setAdapter(new StaticResourcesAdapter() {

                @Override
                public void service(final Request req, final Response res) throws IOException {
                    Utils.dumpOut("Invoking Adapter.service");
                    ByteChunk bc = new ByteChunk();
                    bc.append(testString.getBytes(), 0, testString.length());
                    res.doWrite(bc);
                }

                @Override
                public void afterService(final Request req, final Response res) {
                    try {
                        super.afterService(req, res);
                        return;
                    } catch (Exception ex) {
                        ex.printStackTrace();
                        fail(ex.getMessage());
                    }
                }
            });

                st.setKeepAliveTimeoutInSeconds(5);
                st.listen();
                st.enableMonitoring();

            Socket s = new Socket("localhost", PORT);
            s.setSoTimeout(10 * 1000);
            OutputStream os = s.getOutputStream();

            Utils.dumpOut("GET / HTTP/1.1\n");
            os.write("GET / HTTP/1.1\n".getBytes());
            os.write(("Host: localhost:" + PORT + "\n").getBytes());
            os.write("\n".getBytes());
                
            String line;
            String response = null;
            try {
                InputStream is = new DataInputStream(s.getInputStream());
                BufferedReader br = new BufferedReader(new InputStreamReader(is));

                while ((line = br.readLine()) != null) {
                    Utils.dumpOut("-> " + line);
                    if (line.contains(testString)) {
                        response = line;
                    }
                }
            } catch (IOException ex) {
                //ex.printStackTrace();
                Utils.dumpOut("Timeout!");
            }
            assertEquals(testString, response);
        } finally {
            SelectorThreadUtils.stopSelectorThread(st);
            pe.shutdown();
        }
    }
    
    public void testKillLoopingWhileThread() throws Exception {
        Utils.dumpOut("Test: testKillLoopingWhileThread");
        final ScheduledThreadPoolExecutor pe = new ScheduledThreadPoolExecutor(1);
        final String testString = "killed";
        String response = "";
        
        try {
            createSelectorThread();
            st.setAdapter(new StaticResourcesAdapter() {

                @Override
                public void service(final Request req, final Response res) throws IOException {
                    try {
                        while(true){
                            // This will continue to spin forever as we can kill a Thread
                            // properly, but at least Grizzly will try.
                        }
                    } catch (Throwable t) {
                        t.printStackTrace();
                    } finally {
                        ByteChunk bc = new ByteChunk();
                        bc.append(testString.getBytes(), 0, testString.length());
                        res.doWrite(bc);
                    }
                }

                @Override
                public void afterService(final Request req, final Response res) {
                    try {
                        super.afterService(req, res);
                        return;
                    } catch (Exception ex) {
                        ex.printStackTrace();
                        fail(ex.getMessage());
                    }
                }
            });

            st.setTransactionTimeout(5 * 1000);

            st.listen();
            st.enableMonitoring();

            Socket s = new Socket("localhost", PORT);
            s.setSoTimeout(10 * 1000);
            OutputStream os = s.getOutputStream();

            Utils.dumpOut("GET / HTTP/1.1\n");
            os.write("GET / HTTP/1.1\n".getBytes());
            os.write(("Host: localhost:" + PORT + "\n").getBytes());
            os.write("\n".getBytes());

            String line;
            try {
                InputStream is = new DataInputStream(s.getInputStream());
                BufferedReader br = new BufferedReader(new InputStreamReader(is));

                while ((line = br.readLine()) != null) {
                    Utils.dumpOut("-> " + line);
                    response = line;
                }
            } catch (IOException ex) {
                //ex.printStackTrace();
                Utils.dumpOut("Timeout!");
            }
            assertNotSame(testString, response);
        } finally {
            SelectorThreadUtils.stopSelectorThread(st);
            pe.shutdown();
        }
    }    
    
   public void testKillBlockingQueueThread() throws Exception {
        Utils.dumpOut("Test: testKillBlockingQueueThread");
        final ScheduledThreadPoolExecutor pe = new ScheduledThreadPoolExecutor(1);
        final String testString = "killed";
        String response = "";

        try {
            createSelectorThread();
            st.setAdapter(new StaticResourcesAdapter() {

                @Override
                public void service(final Request req, final Response res) throws IOException {
                    try {
                        BlockingQueue q = new ArrayBlockingQueue(10);
                        q.take();
                    } catch (InterruptedException e) {
//                        e.printStackTrace();
                        throw new IOException(e.getMessage());
                    }
                }

                @Override
                public void afterService(final Request req, final Response res) {
                    try {
                        super.afterService(req, res);
                        return;
                    } catch (Exception ex) {
                        ex.printStackTrace();
                        fail(ex.getMessage());
                    }
                }
            });


            st.setTransactionTimeout(5 * 1000);

            st.listen();
            st.enableMonitoring();


            Socket s = new Socket("localhost", PORT);
            s.setSoTimeout(10 * 1000);
            OutputStream os = s.getOutputStream();

            Utils.dumpOut("GET / HTTP/1.1\n");
            os.write("GET / HTTP/1.1\n".getBytes());
            os.write(("Host: localhost:" + PORT + "\n").getBytes());
            os.write("\n".getBytes());

            String line;
            try {
                InputStream is = new DataInputStream(s.getInputStream());
                BufferedReader br = new BufferedReader(new InputStreamReader(is));

                while ((line = br.readLine()) != null) {
                    Utils.dumpOut("-> " + line);
                    response = line;
                }
            } catch (IOException ex) {
                //ex.printStackTrace();
                Utils.dumpOut("Timeout!");
            }
            assertNotSame(testString, response);
        } finally {
            SelectorThreadUtils.stopSelectorThread(st);
            pe.shutdown();
        }
    }    
}
