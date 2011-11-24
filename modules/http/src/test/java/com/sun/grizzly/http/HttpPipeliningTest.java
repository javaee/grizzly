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
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import static org.junit.Assert.*;

/**
 * HTTP pipelining test
 * 
 * @author Alexey Stashok
 */
@RunWith(Parameterized.class)
public class HttpPipeliningTest {
    private static final Logger logger = Logger.getLogger("grizzly.test");
    private SelectorThread st;

    @Parameters
    public static Collection<Object[]> getAsyncParameters() {
        // fill array with all possible FALSE/TRUE combinations
        
        final int params = 3;
        final int cases = (int) Math.pow(2, params);
        final Object[][] array = new Object[cases][params];
        for (int i = 0; i < cases; i++) {
            for (int j = 0; j < params; j++) {
                final int step = (int) Math.pow(2, params - j - 1);
                array[i][j] = ((i / step) % 2) == 0 ? Boolean.FALSE : Boolean.TRUE;
            }
        }
        
        return Arrays.asList(array);
    }

    private final boolean isArpEnabled;
    private final boolean isSuspendResumeEnabled;
    private final boolean isSplitRequests;

    public HttpPipeliningTest(boolean isArpEnabled, boolean isSuspendResumeEnabled,
            boolean isSplitRequests) {
        this.isArpEnabled = isArpEnabled;
        this.isSuspendResumeEnabled = isSuspendResumeEnabled;
        this.isSplitRequests = isSplitRequests;
    }
    
    @Test
    public void test() throws IOException, InstantiationException {
        try {
            startSelectorThread(0, isArpEnabled, isSuspendResumeEnabled);
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
        Socket s = new Socket("localhost", port);
        s.setSoTimeout(5000);
        OutputStream os = s.getOutputStream();
        if (!isSplitRequests) {
            String pipeline = request1 + request2 + request3;
            os.write(pipeline.getBytes());
            os.flush();
        } else {
            os.write(request1.getBytes());
            os.flush();
            Thread.yield();
            os.write(request2.getBytes());
            os.flush();
            Thread.yield();
            os.write(request3.getBytes());
            os.flush();
            Thread.yield();
        }
        
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
        private final AtomicInteger count = new AtomicInteger();
        private final boolean isSuspendResume;

        public MyAdapter(boolean isSuspendResume) {
            this.isSuspendResume = isSuspendResume;
        }
        
        public void service(final GrizzlyRequest request, final GrizzlyResponse response)
                throws Exception {
            if (!isSuspendResume) {
                doTask(request, response);
            } else {
                response.suspend();
                new Thread() {
                    @Override
                    public void run() {
                        try {
                            Thread.sleep(500);
                            doTask(request, response);
                        } catch (Exception e) {
                            response.setStatus(500, e.getClass().getName() + ": " + e.getMessage());
                        } finally {
                            response.resume();
                        }
                    }
                    
                }.start();
            }
        }

        private void doTask(GrizzlyRequest request, GrizzlyResponse response) throws IOException {
            InputStream is = request.getInputStream();
            while(is.read() != -1);

            OutputStream os = response.getOutputStream();
            os.write(("Response #" + count.getAndIncrement() + "\n").getBytes());
        }
    }

    private void startSelectorThread(final int port, final boolean isAsync,
            final boolean isSuspendResume)
            throws IOException, InstantiationException {
        
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

        st.setAdapter(new MyAdapter(isSuspendResume));
        st.setPort(port);
        st.setDisplayConfiguration(Utils.VERBOSE_TESTS);

        if (isAsync) {
            st.setEnableAsyncExecution(true);
            st.setAsyncHandler(new DefaultAsyncHandler());
        }

        st.listen();
    }
}
