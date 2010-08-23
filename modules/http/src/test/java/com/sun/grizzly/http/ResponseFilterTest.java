/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2009-2010 Oracle and/or its affiliates. All rights reserved.
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
import com.sun.grizzly.tcp.ResponseFilter;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.logging.Level;
import java.util.logging.Logger;

import junit.framework.TestCase;

/**
 * Units test that exercise the {@link Response#addResponseFilter}
 * @author Jeanfrancois Arcand
 */
public class ResponseFilterTest extends TestCase {

    public static final int PORT = 18890;
    private static Logger logger = Logger.getLogger("grizzly.test");
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

    public void testResponseFilter() throws Exception {
        Utils.dumpOut("Test: testResponseFilter");
        final ScheduledThreadPoolExecutor pe = new ScheduledThreadPoolExecutor(1);
        final String testString = "Added after invoking Adapter";
        final byte[] testData = testString.getBytes();
        try {
            createSelectorThread();
            st.setAdapter(new StaticResourcesAdapter() {

                @Override
                public void service(final Request req, final Response res) throws IOException {
                    //res.flushHeaders();
                    res.addResponseFilter(new ResponseFilter() {

                        public void filter(ByteChunk bc) {
                            try {
                                bc.append(testData, 0, testData.length);
                            } catch (IOException ex) {
                                ex.printStackTrace();
                                fail(ex.getMessage());
                            }
                        }
                    });
                    ByteChunk bc = new ByteChunk();
                    bc.append("7777777\n".getBytes(), 0, "7777777\n".length());
                    res.doWrite(bc);
                    res.flush();
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


            st.listen();
            st.enableMonitoring();


            Socket s = new Socket("localhost", PORT);
            s.setSoTimeout(500000);
            OutputStream os = s.getOutputStream();

            Utils.dumpOut(("GET / HTTP/1.1\n"));
            os.write(("GET / HTTP/1.1\n").getBytes());
            os.write(("Host: localhost:" + PORT + "\n").getBytes());
            os.write("\n".getBytes());

            InputStream is = new DataInputStream(s.getInputStream());
            BufferedReader br = new BufferedReader(new InputStreamReader(is));
            String line = null;
            while ((line = br.readLine()) != null) {
                Utils.dumpOut("-> " + line);
                if (line.contains(testString)) {
                    assertTrue(true);
                    return;
                }
            }

        } finally {
            SelectorThreadUtils.stopSelectorThread(st);
            pe.shutdown();
        }
    }

    public void testCompleteNewBCResponseFilter() throws Exception {
        Utils.dumpOut("Test: testCompleteNewBCResponseFilter");
        final ScheduledThreadPoolExecutor pe = new ScheduledThreadPoolExecutor(1);
        final String testString = "Added after invoking Adapter";
        final byte[] testData = testString.getBytes();
        try {
            createSelectorThread();
            st.setAdapter(new StaticResourcesAdapter() {

                @Override
                public void service(final Request req, final Response res) throws IOException {
                    //res.flushHeaders();
                    res.addResponseFilter(new ResponseFilter() {

                        public void filter(ByteChunk bc) {
                            bc.setBytes("AppendingNewBytes".getBytes(), 0,
                                    "AppendingNewBytes".getBytes().length);
                        }
                    });
                    ByteChunk bc = new ByteChunk();
                    bc.append("7777777\n".getBytes(), 0, "7777777\n".length());
                    res.doWrite(bc);
                    res.flush();
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

            st.listen();
            st.enableMonitoring();


            Socket s = new Socket("localhost", PORT);
            s.setSoTimeout(500000);
            OutputStream os = s.getOutputStream();

            Utils.dumpOut(("GET / HTTP/1.1\n"));
            os.write(("GET / HTTP/1.1\n").getBytes());
            os.write(("Host: localhost:" + PORT + "\n").getBytes());
            os.write("\n".getBytes());

            InputStream is = new DataInputStream(s.getInputStream());
            BufferedReader br = new BufferedReader(new InputStreamReader(is));
            String line = null;
            while ((line = br.readLine()) != null) {
                Utils.dumpOut("-> " + line);
                if (line.contains("AppendingNewBytes")) {
                    assertTrue(true);
                    return;
                }
            }

        } finally {
            SelectorThreadUtils.stopSelectorThread(st);
            pe.shutdown();
        }
    }

    public void testComplexByteChunkManipulation() throws Exception {
        Utils.dumpOut("Test: testComplexByteChunkManipulation");
        final ScheduledThreadPoolExecutor pe = new ScheduledThreadPoolExecutor(1);
        try {
            createSelectorThread();
            st.setAdapter(new StaticResourcesAdapter() {

                @Override
                public void service(final Request req, final Response res) throws IOException {
                    //res.flushHeaders();
                    res.addResponseFilter(new ResponseFilter() {

                        public void filter(ByteChunk bc) {
                            bc.recycle();
                            bc.setBytes("AppendingNewBytes".getBytes(), 0,
                                    "AppendingNewBytes".getBytes().length);
                        }
                    });
                    ByteChunk bc = new ByteChunk();
                    bc.append("7777777\n".getBytes(), 0, "7777777\n".length());
                    res.doWrite(bc);
                    res.flush();
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

            st.listen();
            st.enableMonitoring();


            Socket s = new Socket("localhost", PORT);
            s.setSoTimeout(500000);
            OutputStream os = s.getOutputStream();

            Utils.dumpOut(("GET / HTTP/1.1\n"));
            os.write(("GET / HTTP/1.1\n").getBytes());
            os.write(("Host: localhost:" + PORT + "\n").getBytes());
            os.write("\n".getBytes());


            InputStream is = new DataInputStream(s.getInputStream());
            BufferedReader br = new BufferedReader(new InputStreamReader(is));
            String line = null;
            while ((line = br.readLine()) != null) {
                Utils.dumpOut("-> " + line);
                if (line.contains("AppendingNewBytes")) {
                    assertTrue(true);
                    return;
                }
            }
        } finally {
            SelectorThreadUtils.stopSelectorThread(st);
            pe.shutdown();
        }
    }
}
