/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012 Oracle and/or its affiliates. All rights reserved.
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

import com.sun.grizzly.ControllerStateListenerAdapter;
import com.sun.grizzly.arp.AsyncHandler;
import com.sun.grizzly.arp.DefaultAsyncHandler;
import com.sun.grizzly.http.KeepAliveThreadAttachment;
import com.sun.grizzly.http.SelectorThread;
import com.sun.grizzly.tcp.http11.GrizzlyAdapter;
import com.sun.grizzly.tcp.http11.GrizzlyRequest;
import com.sun.grizzly.tcp.http11.GrizzlyResponse;
import com.sun.grizzly.util.FutureImpl;
import com.sun.grizzly.util.SelectionKeyAttachment;
import com.sun.grizzly.util.Utils;
import java.io.*;
import java.net.Socket;
import java.nio.channels.SelectionKey;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.After;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;

/**
 * Check keepalive, transaction, comet-context timeouts during request/response processing
 * 
 * @author Alexey Stashok
 */
public class TimeoutsTest {
    private static final Logger logger = Logger.getLogger("grizzly.test");
    private static final String TOPIC = "/mytopic";
    
    private static final int TRANSACTION_TIMEOUT_MILLIS = 15 * 60 * 1000;
    private static final int KEEP_ALIVE_TIMEOUT_SECONDS = 25;
    private static final int SUSPEND_TIMEOUT_MILLIS = 5 * 60 * 1000;
    
    private SelectorThread st;
    
    private volatile FutureImpl<SelectionKey> serverSideSelectionKeyFuture;
    private final BlockingQueue<FutureImpl<Boolean>> serverSideCheckFutureQueue =
            new LinkedBlockingQueue<FutureImpl<Boolean>>();
    
    private CometContext cometContext;
    
    public TimeoutsTest() {
    }

    @Before
    public void before() {
        cometContext = CometEngine.getEngine().register(TOPIC);
        cometContext.setExpirationDelay(SUSPEND_TIMEOUT_MILLIS);
    }
    
    @After
    public void after() {
        CometEngine.getEngine().unregister(TOPIC);
    }
    
    @Test
    public void test() throws Exception {
        try {
            startSelectorThread(0);
            runTest(3, 3);
        } finally {
            st.stopEndpoint();
        }
    }

    private void runTest(int attempts, int pipelinedRequests) throws Exception {
        serverSideSelectionKeyFuture = new FutureImpl<SelectionKey>();
        
        final int port = st.getPort();
        Socket s = new Socket("localhost", port);
        s.setSoTimeout(5000);
        
        BufferedReader reader = new BufferedReader(new InputStreamReader(s.getInputStream()));
        OutputStream os = s.getOutputStream();
        
        String request = "";
        for (int i = 0; i < pipelinedRequests; i++) {
            request += "GET / HTTP/1.1\nHost: localhost:" + port + "\n\n";
        }
        String responseReportString = "Response #";
        int responseNumber = 0;
        
        SelectionKey selectionKey = null;
        try {
            for (int i = 0; i < attempts; i++) {
                os.write(request.getBytes());
                os.flush();

                for (int j = 0; j < pipelinedRequests; j++) {
                
                    final FutureImpl<Boolean> localServerSideCheckFuture =
                            new FutureImpl<Boolean>();
                    serverSideCheckFutureQueue.offer(localServerSideCheckFuture);
                    
                    if (selectionKey == null) {
                        selectionKey = serverSideSelectionKeyFuture.get(5, TimeUnit.SECONDS);
                    }

                    assertTrue(localServerSideCheckFuture.get(5, TimeUnit.SECONDS));

                    String line = reader.readLine();
                    assertEquals("HTTP/1.1 200 OK", line);
                
                    try {
                        while ((line = reader.readLine()) != null) {
//                                            System.out.println(line);
                            if ((responseReportString + responseNumber).equals(line)) {
                                break;
                            }
                        }
                    } catch (Exception e) {
                        logger.log(Level.WARNING, "Error. The expected response was '" + (responseReportString + responseNumber), e);
                        fail();
                    }
                
                    responseNumber++;
                }
                
                Thread.sleep(1000);  // Give sometime to server to return connection to idle state
                
                KeepAliveThreadAttachment attachment = (KeepAliveThreadAttachment) selectionKey.attachment();
                assertEquals(SelectionKeyAttachment.UNSET_TIMEOUT, attachment.getIdleTimeoutDelay());
            }
        } catch (Throwable e) {
            throw new Exception("Message #" + responseNumber + " failed:", e);
        } finally {
            s.close();
        }
    }
    
    
    private void startSelectorThread(final int port)
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

        st.setAdapter(new MyAdapter());
        st.setPort(port);
        st.setDisplayConfiguration(Utils.VERBOSE_TESTS);

        st.setEnableAsyncExecution(true);
        AsyncHandler asyncHandler = new DefaultAsyncHandler();
        asyncHandler.addAsyncFilter(new CometAsyncFilter());
        st.setAsyncHandler(asyncHandler);

        st.setTransactionTimeout(TRANSACTION_TIMEOUT_MILLIS);
        st.setKeepAliveTimeoutInSeconds(KEEP_ALIVE_TIMEOUT_SECONDS);
        
        st.listen();
    }

    private class MyAdapter extends GrizzlyAdapter {
        private final AtomicInteger count = new AtomicInteger();

        public MyAdapter() {
        }
        
        public void service(final GrizzlyRequest request, final GrizzlyResponse response)
                throws Exception {
            
            final FutureImpl<Boolean> serverSideCheckFuture =
                serverSideCheckFutureQueue.poll(5, TimeUnit.SECONDS);
            
            try {

                final SelectionKey selectionKey = response.getResponse().getSelectionKey();
                serverSideSelectionKeyFuture.setResult(selectionKey);
            
                assertEquals(TRANSACTION_TIMEOUT_MILLIS, ((KeepAliveThreadAttachment) selectionKey.attachment()).getIdleTimeoutDelay());
                
                cometContext.addCometHandler(new MyCometHandler(count.getAndIncrement(), serverSideCheckFuture, response));

                new Thread() {
                    @Override
                    public void run() {
                        try {
                            Thread.sleep(500);
                            cometContext.notify(null);
                        } catch (Exception ignored) {
                        }
                    }

                }.start();
            } catch (Throwable e) {
                serverSideCheckFuture.setException(e);
            }
        }
    }
    
    private class MyCometHandler implements CometHandler<GrizzlyResponse> {
        private final FutureImpl<Boolean> serverSideCheckFuture;
        private GrizzlyResponse response;
        private final int id;

        public MyCometHandler(int id,
                final FutureImpl<Boolean> serverSideCheckFuture,
                GrizzlyResponse response) {
            this.id = id;
            this.serverSideCheckFuture = serverSideCheckFuture;
            this.response = response;
        }
        
        public void attach(GrizzlyResponse response) {
            this.response = response;
        }

        public void onEvent(CometEvent event) throws IOException {
            if (event.getType() == CometEvent.NOTIFY) {
                try {
                    final SelectionKey selectionKey = response.getResponse().getSelectionKey();
                    assertEquals(SUSPEND_TIMEOUT_MILLIS, ((CometTask) selectionKey.attachment()).getIdleTimeoutDelay());

                    OutputStream os = response.getOutputStream();
                    os.write(("Response #" + id + "\n").getBytes());
                    serverSideCheckFuture.setResult(Boolean.TRUE);
                    
                    cometContext.resumeCometHandler(this);
                } catch (Throwable e) {
                    serverSideCheckFuture.setException(e);
                }

            }
        }

        public void onInitialize(CometEvent event) throws IOException {
        }

        public void onTerminate(CometEvent event) throws IOException {
        }

        public void onInterrupt(CometEvent event) throws IOException {
        }
        
    }
}
