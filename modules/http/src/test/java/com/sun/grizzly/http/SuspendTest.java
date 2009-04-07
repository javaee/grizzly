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
package com.sun.grizzly.http;

import com.sun.grizzly.ControllerStateListenerAdapter;
import com.sun.grizzly.http.utils.SelectorThreadUtils;
import com.sun.grizzly.tcp.CompletionHandler;
import com.sun.grizzly.tcp.Request;
import com.sun.grizzly.tcp.Response;
import com.sun.grizzly.tcp.StaticResourcesAdapter;
import com.sun.grizzly.tcp.http11.GrizzlyAdapter;
import com.sun.grizzly.tcp.http11.GrizzlyRequest;
import com.sun.grizzly.tcp.http11.GrizzlyResponse;
import com.sun.grizzly.util.WorkerThreadImpl;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import junit.framework.TestCase;

/**
 * Units test that exercise the {@link Response#suspend}, {@link Response#resume}
 * and {@link Response.cancel} API.
 * 
 * @author Jeanfrancois Arcand
 */
public class SuspendTest extends TestCase {

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
        st.setDisplayConfiguration(true);

    }

    public void testSuspendDoubleCancelInvokation() throws IOException {
        System.out.println("Test: testSuspendDoubleCancelInvokation");          
        final ScheduledThreadPoolExecutor pe = new ScheduledThreadPoolExecutor(1);
        final String testString = "Resuming the response";
        final byte[] testData = testString.getBytes();
        final CountDownLatch latch = new CountDownLatch(1);
        final CountDownLatch latch2 = new CountDownLatch(1);
        try {
            createSelectorThread();
            st.setAdapter(new StaticResourcesAdapter() {

                @Override
                public void service(final Request req, final Response res) {
                    try {
                        if (res.isSuspended()) {
                            super.service(req, res);
                            return;
                        }

                        res.suspend(60 * 1000, this, new CompletionHandler<StaticResourcesAdapter>() {

                            public void resumed(StaticResourcesAdapter attachment) {
                                System.out.println("Not supposed to be here");

                            }

                            public void cancelled(StaticResourcesAdapter attachment) {
                                try {
                                    System.out.println("cancelled");
                                } catch (Exception ex) {
                                    ex.printStackTrace();
                                } finally {
                                    latch.countDown();
                                }
                            }
                        });


                        pe.schedule(new Runnable() {

                            public void run() {
                                try {
                                    if (res.isSuspended()) {
                                        res.cancel();
                                    }
                                } catch (Throwable ex) {
                                    ex.printStackTrace();
                                }
                            }
                        }, 2, TimeUnit.SECONDS);


                        try {
                            latch.await(5, TimeUnit.SECONDS);

                            res.cancel();
                        } catch (Throwable t) {
                            t.printStackTrace();
                            res.getChannel().write(ByteBuffer.wrap(testData));
                        }

                    } catch (Throwable t) {
                        t.printStackTrace();
                    }
                }

                @Override
                public void afterService(final Request req, final Response res) {
                    if (res.isSuspended()) {
                        try {
                            super.afterService(req, res);
                            return;
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        }
                    }
                }
            });

            try {
                st.listen();
                st.enableMonitoring();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
            String r = sendRequest(testData, testString, false);
            System.out.println("Response: " + r);
            assertEquals(testString, r);
        } finally {
            SelectorThreadUtils.stopSelectorThread(st);
            pe.shutdown();
        }
    }
    
    public void testSuspendNoArgs() throws IOException {
        System.out.println("Test: testSuspendNoArgs");
        final ScheduledThreadPoolExecutor pe = new ScheduledThreadPoolExecutor(1);
        final String testString = "Resuming the response";
        final byte[] testData = testString.getBytes();
        final CountDownLatch latch = new CountDownLatch(1);
        try {
            createSelectorThread();
            st.setAdapter(new StaticResourcesAdapter() {

                @Override
                public void service(final Request req, final Response res) {
                    res.suspend();

                    pe.schedule(new Runnable() {

                        public void run() {
                            try {
                                System.out.println("Resuming");
                                if (res.isSuspended()) {
                                    res.getChannel().write(ByteBuffer.wrap(testData));
                                    res.resume();
                                }
                                latch.countDown();
                            } catch (Throwable ex) {
                                ex.printStackTrace();
                            }
                        }
                    }, 2, TimeUnit.SECONDS);

                    try {
                        latch.await(5, TimeUnit.SECONDS);
                    } catch (Throwable t) {
                        t.printStackTrace();
                    } finally {

                    }
                }

                @Override
                public void afterService(final Request req, final Response res) {
                    if (res.isSuspended()) {
                        try {
                            super.afterService(req, res);
                            return;
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        }
                    }
                }
            });

            try {
                st.listen();
                st.enableMonitoring();
            } catch (Exception ex) {
                ex.printStackTrace();
            }

            sendRequest(testData, testString);


        } finally {
            SelectorThreadUtils.stopSelectorThread(st);
            pe.shutdown();
        }
    }

    public void testSuspendResumedCompletionHandler() throws IOException {
        System.out.println("Test: testSuspendResumedCompletionHandler");
        final ScheduledThreadPoolExecutor pe = new ScheduledThreadPoolExecutor(1);
        final String testString = "Resuming the response";
        final byte[] testData = testString.getBytes();
        final CountDownLatch latch = new CountDownLatch(1);
        try {
            createSelectorThread();
            st.setAdapter(new StaticResourcesAdapter() {

                @Override
                public void service(final Request req, final Response res) {
                    try {
                        if (res.isSuspended()) {
                            super.service(req, res);
                            return;
                        }

                        res.suspend(60 * 1000, this, new CompletionHandler<StaticResourcesAdapter>() {

                            public void resumed(StaticResourcesAdapter attachment) {
                                try {
                                    System.out.println("Resuming");
                                    if (res.isSuspended()) {
                                        res.getChannel().write(ByteBuffer.wrap(testData));
                                    }
                                } catch (Exception ex) {
                                    ex.printStackTrace();
                                } finally {
                                    latch.countDown();
                                }
                            }

                            public void cancelled(StaticResourcesAdapter attachment) {
                                System.out.println("Not supposed to be here");
                            }
                        });


                        pe.schedule(new Runnable() {

                            public void run() {
                                try {
                                    System.out.println("Resuming");
                                    if (res.isSuspended()) {
                                        res.resume();
                                    }
                                } catch (Throwable ex) {
                                    ex.printStackTrace();
                                }
                            }
                        }, 2, TimeUnit.SECONDS);


                        try {
                            latch.await(5, TimeUnit.SECONDS);
                        } catch (Throwable t) {
                            t.printStackTrace();
                        } finally {

                        }
                    } catch (Throwable t) {
                        t.printStackTrace();
                    }
                }

                @Override
                public void afterService(final Request req, final Response res) {
                    if (res.isSuspended()) {
                        try {
                            super.afterService(req, res);
                            return;
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        }
                    }
                }
            });

            try {
                st.listen();
                st.enableMonitoring();
            } catch (Exception ex) {
                ex.printStackTrace();
            }

            sendRequest(testData, testString);


        } finally {
            SelectorThreadUtils.stopSelectorThread(st);
            pe.shutdown();
        }
    }

    public void testSuspendCancelledCompletionHandler() throws IOException {
        System.out.println("Test: testSuspendCancelledCompletionHandler");        
        final ScheduledThreadPoolExecutor pe = new ScheduledThreadPoolExecutor(1);
        final String testString = "Resuming the response";
        final byte[] testData = testString.getBytes();
        final CountDownLatch latch = new CountDownLatch(1);
        try {
            createSelectorThread();
            st.setAdapter(new StaticResourcesAdapter() {

                @Override
                public void service(final Request req, final Response res) {
                    try {
                        if (res.isSuspended()) {
                            super.service(req, res);
                            return;
                        }

                        res.suspend(60 * 1000, this, new CompletionHandler<StaticResourcesAdapter>() {

                            public void resumed(StaticResourcesAdapter attachment) {
                                System.out.println("Not supposed to be here");

                            }

                            public void cancelled(StaticResourcesAdapter attachment) {
                                try {
                                    System.out.println("cancelled");
                                    if (res.isSuspended()) {
                                        res.getChannel().write(ByteBuffer.wrap(testData));
                                    }
                                } catch (Exception ex) {
                                    ex.printStackTrace();
                                } finally {
                                    latch.countDown();
                                }
                            }
                        });


                        pe.schedule(new Runnable() {

                            public void run() {
                                try {
                                    System.out.println("Cancelling");
                                    if (res.isSuspended()) {
                                        res.cancel();
                                    }
                                } catch (Throwable ex) {
                                    ex.printStackTrace();
                                }
                            }
                        }, 2, TimeUnit.SECONDS);


                        try {
                            latch.await(5, TimeUnit.SECONDS);
                        } catch (Throwable t) {
                            t.printStackTrace();
                        } finally {

                        }
                    } catch (Throwable t) {
                        t.printStackTrace();
                    }
                }

                @Override
                public void afterService(final Request req, final Response res) {
                    if (res.isSuspended()) {
                        try {
                            super.afterService(req, res);
                            return;
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        }
                    }
                }
            });

            try {
                st.listen();
                st.enableMonitoring();
            } catch (Exception ex) {
                ex.printStackTrace();
            }

            sendRequest(testData, testString);


        } finally {
            SelectorThreadUtils.stopSelectorThread(st);
            pe.shutdown();
        }
    }

    public void testSuspendSuspendedExceptionCompletionHandler() throws IOException {
        System.out.println("Test: testSuspendSuspendedExceptionCompletionHandler");         
        final ScheduledThreadPoolExecutor pe = new ScheduledThreadPoolExecutor(1);
        final String testString = "Resuming the response";
        final byte[] testData = testString.getBytes();
        final CountDownLatch latch = new CountDownLatch(1);
        try {
            createSelectorThread();
            st.setAdapter(new StaticResourcesAdapter() {

                @Override
                public void service(final Request req, final Response res) {
                    try {
                        if (res.isSuspended()) {
                            super.service(req, res);
                            return;
                        }

                        res.suspend(60 * 1000, this, new CompletionHandler<StaticResourcesAdapter>() {

                            public void resumed(StaticResourcesAdapter attachment) {
                                try {
                                    System.out.println("Resuming");
                                    res.getChannel().write(ByteBuffer.wrap(testData));
                                    res.resume();
                                } catch (Exception ex) {
                                    ex.printStackTrace();
                                } finally {
                                    latch.countDown();
                                }
                            }

                            public void cancelled(StaticResourcesAdapter attachment) {
                                System.out.println("Not supposed to be here");
                            }
                        });


                        pe.schedule(new Runnable() {

                            public void run() {
                                try {
                                    System.out.println("Resuming");
                                    if (res.isSuspended()) {
                                        res.resume();
                                    }
                                } catch (Throwable ex) {
                                    ex.printStackTrace();
                                }
                            }
                        }, 2, TimeUnit.SECONDS);


                        try {
                            latch.await(5, TimeUnit.SECONDS);
                        } catch (Throwable t) {
                            t.printStackTrace();
                        } finally {

                        }
                    } catch (Throwable t) {
                        t.printStackTrace();
                    }
                }

                @Override
                public void afterService(final Request req, final Response res) {
                    if (res.isSuspended()) {
                        try {
                            super.afterService(req, res);
                            return;
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        }
                    }
                }
            });

            try {
                st.listen();
                st.enableMonitoring();
            } catch (Exception ex) {
                ex.printStackTrace();
            }

            sendRequest(testData, testString);


        } finally {
            SelectorThreadUtils.stopSelectorThread(st);
            pe.shutdown();
        }
    }

    public void testSuspendTimeoutCompletionHandler() throws IOException {
        System.out.println("Test: testSuspendTimeoutCompletionHandler");        
        final ScheduledThreadPoolExecutor pe = new ScheduledThreadPoolExecutor(1);
        final String testString = "Resuming the response";
        final byte[] testData = testString.getBytes();
        final CountDownLatch latch = new CountDownLatch(1);
        try {
            createSelectorThread();
            st.setAdapter(new StaticResourcesAdapter() {

                @Override
                public void service(final Request req, final Response res) {
                    try {
                        if (res.isSuspended()) {
                            super.service(req, res);
                            return;
                        }

                        res.suspend(10 * 1000, this, new CompletionHandler<StaticResourcesAdapter>() {

                            public void resumed(StaticResourcesAdapter attachment) {
                                System.out.println("Not supposed to be here");
                            }

                            public void cancelled(StaticResourcesAdapter attachment) {
                                try {
                                    System.out.println("Time out");
                                    res.getChannel().write(ByteBuffer.wrap(testData));
                                } catch (Exception ex) {
                                    ex.printStackTrace();
                                } finally {
                                    latch.countDown();
                                }
                            }
                        });

                        try {
                            latch.await(15, TimeUnit.SECONDS);
                        } catch (Throwable t) {
                            t.printStackTrace();
                        } finally {
			   System.out.println("Timeout failed");
                        }
                    } catch (Throwable t) {
                        t.printStackTrace();
                    }
                }

                @Override
                public void afterService(final Request req, final Response res) {
                    if (res.isSuspended()) {
                        try {
                            super.afterService(req, res);
                            return;
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        }
                    }
                }
            });

            try {
                st.listen();
                st.enableMonitoring();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
            sendRequest(testData, testString);

        } finally {
            SelectorThreadUtils.stopSelectorThread(st);
            pe.shutdown();
        }
    }

    public void testSuspendDoubleSuspendInvokation() throws IOException {
        System.out.println("Test: testSuspendDoubleSuspendInvokation");         
        final ScheduledThreadPoolExecutor pe = new ScheduledThreadPoolExecutor(1);
        final String testString = "Resuming the response";
        final byte[] testData = testString.getBytes();
        final CountDownLatch latch = new CountDownLatch(1);
        final CountDownLatch latch2 = new CountDownLatch(1);
        try {
            createSelectorThread();
            st.setAdapter(new StaticResourcesAdapter() {

                @Override
                public void service(final Request req, final Response res) {
                    try {
                        if (res.isSuspended()) {
                            super.service(req, res);
                            return;
                        }

                        res.suspend(60 * 1000, this, new CompletionHandler<StaticResourcesAdapter>() {

                            public void resumed(StaticResourcesAdapter attachment) {
                                try {
                                    System.out.println("Suspended");
                                } catch (Exception ex) {
                                    ex.printStackTrace();
                                } finally {
                                    latch.countDown();
                                }
                            }

                            public void cancelled(StaticResourcesAdapter attachment) {
                                System.out.println("Not supposed to be here");
                            }
                        });


                        pe.schedule(new Runnable() {

                            public void run() {
                                try {
                                    if (!res.isSuspended()) {
                                        res.suspend();
                                    }
                                } catch (Throwable ex) {
                                    ex.printStackTrace();
                                }
                            }
                        }, 2, TimeUnit.SECONDS);


                        try {
                            latch.await(5, TimeUnit.SECONDS);

                            res.suspend();
                        } catch (Throwable t) {
                            t.printStackTrace();
                            res.getChannel().write(ByteBuffer.wrap(testData));
                        }

                    } catch (Throwable t) {
                        t.printStackTrace();
                    }
                }

                @Override
                public void afterService(final Request req, final Response res) {
                    if (res.isSuspended()) {
                        try {
                            super.afterService(req, res);
                            return;
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        }
                    }
                }
            });

            try {
                st.listen();
                st.enableMonitoring();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
            String r = sendRequest(testData, testString, false);
            assertEquals(testString, r);
        } finally {
            SelectorThreadUtils.stopSelectorThread(st);
            pe.shutdown();
        }
    }

    public void testSuspendDoubleResumeInvokation() throws IOException {
        System.out.println("Test: testSuspendDoubleSuspendInvokation");        
        final ScheduledThreadPoolExecutor pe = new ScheduledThreadPoolExecutor(1);
        final String testString = "Resuming the response";
        final byte[] testData = testString.getBytes();
        final CountDownLatch latch = new CountDownLatch(1);
        final CountDownLatch latch2 = new CountDownLatch(1);
        try {
            createSelectorThread();
            st.setAdapter(new StaticResourcesAdapter() {

                @Override
                public void service(final Request req, final Response res) {
                    try {
                        if (res.isSuspended()) {
                            super.service(req, res);
                            return;
                        }

                        res.suspend(60 * 1000, this, new CompletionHandler<StaticResourcesAdapter>() {

                            public void resumed(StaticResourcesAdapter attachment) {
                                try {
                                    System.out.println("trying to resume");
                                    res.resume();
                                    System.out.println("Oups should have failed");
                                } catch (Exception ex) {
                                    ex.printStackTrace();
                                    try {
                                        res.getChannel().write(ByteBuffer.wrap(testData));
                                    } catch (IOException ex2) {
                                    }
                                } finally {
                                    latch.countDown();
                                }
                            }

                            public void cancelled(StaticResourcesAdapter attachment) {
                                System.out.println("Not supposed to be here");
                            }
                        });


                        pe.schedule(new Runnable() {

                            public void run() {
                                try {
                                    System.out.println("About to resume");
                                    res.resume();
                                    System.out.println("Done");
                                } catch (Throwable ex) {
                                    ex.printStackTrace();
                                }
                            }
                        }, 2, TimeUnit.SECONDS);


                        try {
                            latch.await(5, TimeUnit.SECONDS);
                        } catch (Throwable t) {
                            t.printStackTrace();
                        }

                    } catch (Throwable t) {
                        t.printStackTrace();
                    }
                }

                @Override
                public void afterService(final Request req, final Response res) {
                    if (res.isSuspended()) {
                        try {
                            super.afterService(req, res);
                            return;
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        }
                    }
                }
            });

            try {
                st.listen();
                st.enableMonitoring();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
            sendRequest(testData, testString, true);
        } finally {
            SelectorThreadUtils.stopSelectorThread(st);
            pe.shutdown();
        }
    }

    
    public void testSuspendResumedCompletionHandlerGrizzlyAdapter() throws IOException {
        System.out.println("Test: testSuspendResumedCompletionHandlerGrizzlyAdapter");
        final ScheduledThreadPoolExecutor pe = new ScheduledThreadPoolExecutor(1);
        final String testString = "Resuming the response";
        final byte[] testData = testString.getBytes();
        final CountDownLatch latch = new CountDownLatch(1);
        try {
            createSelectorThread();
            st.setAdapter(new GrizzlyAdapter() {

                @Override
                public void service(final GrizzlyRequest req, final GrizzlyResponse res) {
                    try {

                        res.suspend(60 * 1000, this, new CompletionHandler<GrizzlyAdapter>() {

                            public void resumed(GrizzlyAdapter attachment) {
                                try {
                                    System.out.println("Resuming");
                                    if (res.isSuspended()) {
                                        res.getWriter().write(testString);
                                    }
                                } catch (Exception ex) {
                                    ex.printStackTrace();
                                } finally {
                                    latch.countDown();
                                }
                            }

                            public void cancelled(GrizzlyAdapter attachment) {
                                System.out.println("Not supposed to be here");
                            }
                        });


                        pe.schedule(new Runnable() {

                            public void run() {
                                try {
                                    System.out.println("Now Resuming");
                                    if (res.isSuspended()) {
                                        res.resume();
                                    }
                                } catch (Throwable ex) {
                                    ex.printStackTrace();
                                }
                            }
                        }, 2, TimeUnit.SECONDS);


                        try {
                            latch.await(5, TimeUnit.SECONDS);
                        } catch (Throwable t) {
                            t.printStackTrace();
                        } finally {

                        }
                    } catch (Throwable t) {
                        t.printStackTrace();
                    }
                }
            });

            try {
                st.listen();
                st.enableMonitoring();
            } catch (Exception ex) {
                ex.printStackTrace();
            }

            sendRequest(testData, testString);


        } finally {
            SelectorThreadUtils.stopSelectorThread(st);
            pe.shutdown();
        }
    }
    
    
    public void testSuspendTimeoutCompletionHandlerGrizzlyAdapter() throws IOException {
        System.out.println("Test: testSuspendTimeoutCompletionHandlerGrizzlyAdapter");
        final ScheduledThreadPoolExecutor pe = new ScheduledThreadPoolExecutor(1);
        final String testString = "Resuming the response";
        final byte[] testData = testString.getBytes();
        final CountDownLatch latch = new CountDownLatch(1);
        try {
            createSelectorThread();
            st.setAdapter(new GrizzlyAdapter() {

                @Override
                public void service(final GrizzlyRequest req, final GrizzlyResponse res) {
                    try {
                        final long t1 = System.currentTimeMillis();
                        res.suspend(10 * 1000, "foo", new CompletionHandler<String>() {

                            public void cancelled(String attachment) {
                                try {
                                    System.out.println("TOOK: " + (System.currentTimeMillis() - t1));
                                    System.out.println("Cancelling");
                                    res.getWriter().write(testString);
                                } catch (Exception ex) {
                                    ex.printStackTrace();
                                } finally {
                                    latch.countDown();
                                }
                            }

                            public void resumed(String attachment) {
                                System.out.println("Not supposed to be here");
                            }
                        });

                    } catch (Throwable t) {
                        t.printStackTrace();
                    }
                }
            });

            try {
                st.listen();
                st.enableMonitoring();
            } catch (Exception ex) {
                ex.printStackTrace();
            }

            sendRequest(testData, testString);


        } finally {
            SelectorThreadUtils.stopSelectorThread(st);
            pe.shutdown();
        }
    }
    
    public void testFastSuspendResumeGrizzlyAdapter() throws IOException {
        System.out.println("Test: testFastSuspendResumeGrizzlyAdapter");
        final ScheduledThreadPoolExecutor pe = new ScheduledThreadPoolExecutor(1);
        final String testString = "Resuming the response";
        final byte[] testData = testString.getBytes();
        final CountDownLatch latch = new CountDownLatch(1);
        try {
            createSelectorThread();
            st.setAdapter(new GrizzlyAdapter() {

                @Override
                public void service(final GrizzlyRequest req, final GrizzlyResponse res) {
                    try {
                        final long t1 = System.currentTimeMillis();
                        res.suspend(5 * 1000, "foo", new CompletionHandler<String>() {

                            public void resumed(String attachment) {
                                try {
                                    System.out.println("TOOK: " + (System.currentTimeMillis() - t1));
                                    System.out.println("Resumed");
                                    res.getWriter().write(testString);
                                    // res.flushBuffer();
                                } catch (Exception ex) {
                                    ex.printStackTrace();
                                } finally {
                                    latch.countDown();
                                }
                            }

                            public void cancelled(String attachment) {
                                System.out.println("Not supposed to be here");
                            }
                        });

                    } catch (Throwable t) {
                        t.printStackTrace();
                    }

                    new WorkerThreadImpl(new Runnable(){
                        public void run(){
                            try {

                                Thread.sleep(1000);

                            } catch (InterruptedException ex) {
                                Logger.getLogger(SuspendTest.class.getName()).log(Level.SEVERE, null, ex);
                            }
                            
                            if (!res.isCommitted()) {
                                System.out.println("Resuming");
                                res.resume();
                            }
                        }
                    }).start();
            }
            });

            try {
                st.listen();
                st.enableMonitoring();
            } catch (Exception ex) {
                ex.printStackTrace();
            }

            sendRequest(testData, testString);


        } finally {
            SelectorThreadUtils.stopSelectorThread(st);
            pe.shutdown();
        }
    }

    public void testSuspendResumeNoArgs() throws IOException {
        System.out.println("Test: testSuspendNoArgs");
        final ScheduledThreadPoolExecutor pe = new ScheduledThreadPoolExecutor(1);
        final String testString = "Resuming the response";
        final byte[] testData = testString.getBytes();
        try {
            createSelectorThread();
            st.setAdapter(new StaticResourcesAdapter() {

                @Override
                public void service(final Request req, final Response res) throws IOException {
                    res.suspend();
                    res.getChannel().write(ByteBuffer.wrap(testData));
                    res.resume();
                }

                @Override
                public void afterService(final Request req, final Response res) {
                    if (res.isSuspended()) {
                        try {
                            super.afterService(req, res);
                            return;
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        }
                    }
                }
            });

            try {
                st.listen();
                st.enableMonitoring();
            } catch (Exception ex) {
                ex.printStackTrace();
            }

            sendRequest(testData, testString);


        } finally {
            SelectorThreadUtils.stopSelectorThread(st);
            pe.shutdown();
        }
    }
    
     public void testSuspendResumeOneTransaction() throws IOException {
        System.out.println("Test: testSuspendResumeOneTransaction");
        final ScheduledThreadPoolExecutor pe = new ScheduledThreadPoolExecutor(1);
        final String testString = "Resuming the response";
        final byte[] testData = testString.getBytes();
        try {
            createSelectorThread();
            st.setAdapter(new StaticResourcesAdapter() {

                @Override
                public void service(final Request req, final Response res) throws IOException {
                    res.suspend();
                    res.getChannel().write(ByteBuffer.wrap(testData));
                    res.flush();
                    res.resume();
                }

                @Override
                public void afterService(final Request req, final Response res) {
                    if (res.isSuspended()) {
                        try {
                            super.afterService(req, res);
                            return;
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        }
                    }
                }
            });

            try {
                st.listen();
                st.enableMonitoring();
            } catch (Exception ex) {
                ex.printStackTrace();
            }

            sendRequest(testData, testString);


        } finally {
            SelectorThreadUtils.stopSelectorThread(st);
            pe.shutdown();
        }
    }

    
    private String sendRequest(byte[] testData, String testString)
            throws MalformedURLException, ProtocolException, IOException {

        return sendRequest(testData, testString, true);
    }

    private String sendRequest(byte[] testData, String testString, boolean assertTrue)
            throws MalformedURLException, ProtocolException, IOException {
        byte[] response = new byte[testData.length];

        URL url = new URL("http://localhost:" + PORT);
        HttpURLConnection connection =
                (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        OutputStream os = connection.getOutputStream();
        os.write("Hello".getBytes());
        os.flush();

        InputStream is = new DataInputStream(connection.getInputStream());
        response = new byte[testData.length];
        is.read(response);


        String r = new String(response);
        if (assertTrue) {
            System.out.println("Response: " + r);
            assertEquals(testString, r);
        }
        connection.disconnect();
        return r;
    }
}
