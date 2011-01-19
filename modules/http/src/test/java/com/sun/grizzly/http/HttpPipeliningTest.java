/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011 Oracle and/or its affiliates. All rights reserved.
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
import com.sun.grizzly.arp.DefaultAsyncHandler;
import com.sun.grizzly.http.utils.SelectorThreadUtils;
import com.sun.grizzly.tcp.http11.GrizzlyAdapter;
import com.sun.grizzly.tcp.http11.GrizzlyRequest;
import com.sun.grizzly.tcp.http11.GrizzlyResponse;
import com.sun.grizzly.util.Utils;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Level;
import java.util.logging.Logger;
import junit.framework.TestCase;

/**
 * HTTP pipelining test
 * 
 * @author Alexey Stashok
 */
public class HttpPipeliningTest extends TestCase {
    private static final Logger logger = Logger.getLogger("grizzly.test");
    private SelectorThread st;

    public void testSync() throws IOException, InstantiationException {
        try {
            startSelectorThread(0, false);
            runClient();
        } finally {
            SelectorThreadUtils.stopSelectorThread(st);
        }
    }

    public void testAsync() throws IOException, InstantiationException {
        try {
            startSelectorThread(0, true);
            runClient();
        } finally {
            SelectorThreadUtils.stopSelectorThread(st);
        }
    }

    private void runClient() throws SocketException, IOException {
        int port = st.getPort();
        final int expectedResponseNumber = 3;
        String request1 = "GET / HTTP/1.1\nHost: localhost:" + port + "\n\n";
        String request2 = "POST / HTTP/1.1\nHost: localhost:" + port + "\nContent-length: 10\n\n0123456789";
        String request3 = "GET / HTTP/1.1\nHost: localhost:" + port + "\n\n";
        String pipeline = request1 + request2 + request3;
        Socket s = new Socket("localhost", port);
        s.setSoTimeout(5000);
        OutputStream os = s.getOutputStream();
        os.write(pipeline.getBytes());
        os.flush();
        BufferedReader reader = new BufferedReader(new InputStreamReader(s.getInputStream()));
        String responseReportString = "Response #";
        String line;
        int responseNumber = 0;
        try {
            while ((line = reader.readLine()) != null) {
//                System.out.println(line);
                if ((responseReportString + responseNumber).equals(line)) {
                    responseNumber++;
                    if (expectedResponseNumber == responseNumber) {
                        break;
                    }
                }
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error. The expected response was '" + (responseReportString + responseNumber), e);
            fail();
        }

        assertEquals(expectedResponseNumber, responseNumber);
    }
    
    private static class MyAdapter extends GrizzlyAdapter {
        private int count = 0;

        public void service(GrizzlyRequest request, GrizzlyResponse response) throws Exception {
            InputStream is = request.getInputStream();
            while(is.read() != -1);

            OutputStream os = response.getOutputStream();
            os.write(("Response #" + count++ + "\n").getBytes());
        }
    }

    private void startSelectorThread(int port, boolean isAsync) throws IOException, InstantiationException {
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

        st.setAdapter(new MyAdapter());
        st.setPort(port);
        st.setDisplayConfiguration(Utils.VERBOSE_TESTS);

        if (isAsync) {
            st.setEnableAsyncExecution(true);
            st.setAsyncHandler(new DefaultAsyncHandler());
        }

        st.listen();
    }
}
