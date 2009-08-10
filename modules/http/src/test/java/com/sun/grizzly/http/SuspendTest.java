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
import com.sun.grizzly.tcp.Adapter;
import com.sun.grizzly.tcp.CompletionHandler;
import com.sun.grizzly.tcp.Request;
import com.sun.grizzly.tcp.Response;
import com.sun.grizzly.tcp.StaticResourcesAdapter;
import com.sun.grizzly.tcp.http11.GrizzlyAdapter;
import com.sun.grizzly.tcp.http11.GrizzlyRequest;
import com.sun.grizzly.tcp.http11.GrizzlyResponse;
import com.sun.grizzly.util.WorkerThreadImpl;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import junit.framework.TestCase;

/**
 * Units test that exercise the {@link Response#suspend}, {@link Response#resume}
 * and {@link Response.cancel} API.
 * 
 * @author Jeanfrancois Arcand
 * @author gustav trede
 */
public class SuspendTest extends TestCase {

    public static final int PORT = 18890;
    private ScheduledThreadPoolExecutor pe;
    private SelectorThread st;
    private final String testString = "blabla test.";
    private final byte[] testData = testString.getBytes();

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        pe = new ScheduledThreadPoolExecutor(1);
        createSelectorThread();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        pe.shutdown();
        SelectorThreadUtils.stopSelectorThread(st);
    }

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

    private void setAdapterAndListen(Adapter adapter) throws Exception{
        st.setAdapter(adapter);
        st.listen();
        st.enableMonitoring();
    }

    // See https://grizzly.dev.java.net/issues/show_bug.cgi?id=592
    public void __testSuspendDoubleCancelInvokation() throws Exception {
        System.err.println("Test: testSuspendDoubleCancelInvokation");
        final CountDownLatch latch = new CountDownLatch(1);
        setAdapterAndListen(new TestStaticResourcesAdapter() {
            @Override
            public void dologic(final Request req, final Response res) throws Throwable{
                res.suspend(60 * 1000, this, new TestCompletionHandler<StaticResourcesAdapter>() {
                    @Override
                    public void cancelled(StaticResourcesAdapter attachment) {
                        System.err.println("cancelled");
                        latch.countDown();
                    }
                });

                cancelLater(res);
                
                if(!latch.await(5, TimeUnit.SECONDS)){
                    fail("was canceled too late");
                }
                try {
                    res.cancel();
                    fail("should not reach here");
                } catch (IllegalStateException t) {
                    res.getChannel().write(ByteBuffer.wrap(testData));
                }
            }
        });
        sendRequest(false);
    }

   public void testSuspendResumeSameTransaction() throws Exception {
        System.err.println("Test: testSuspendResumeSameTransaction");
        setAdapterAndListen(new TestStaticResourcesAdapter() {
            @Override
            public void service(final Request req, final Response res){
                try{
                    res.suspend();
                    res.resume();
                    res.getChannel().write(ByteBuffer.wrap(testData));
                    res.finish();
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        });
        sendRequest();
    }


    public void testSuspendResumeNoArgs() throws Exception {
        System.err.println("Test: testSuspendResumeNoArgs");
        setAdapterAndListen(new TestStaticResourcesAdapter() {
            @Override
            public void service(final Request req, final Response res){
                try{
                    res.suspend();
                    writeToSuspendedClient(res);
                    res.resume();
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        });
        sendRequest();
    }

    public void testSuspendNoArgs() throws Exception {
        System.err.println("Test: testSuspendNoArgs");
        setAdapterAndListen(new TestStaticResourcesAdapter() {
            @Override
            public void service(final Request req, final Response res) {
                res.suspend();
                pe.schedule(new Runnable() {
                    public void run() {
                        writeToSuspendedClient(res);
                        res.resume();
                    }
                }, 2, TimeUnit.SECONDS);
            }
        });
        sendRequest();
    }

    public void testSuspendResumedCompletionHandler() throws Exception {
        System.err.println("Test: testSuspendResumedCompletionHandler");
        setAdapterAndListen(new TestStaticResourcesAdapter() {
            @Override
            public void dologic(final Request req, final Response res) throws Throwable{
                res.suspend(60 * 1000, this, new TestCompletionHandler<StaticResourcesAdapter>() {
                    @Override
                    public void resumed(StaticResourcesAdapter attachment) {
                        writeToSuspendedClient(res);
                    }
                });
                resumeLater(res);
            }
        });
        sendRequest();
    }

    public void testSuspendCancelledCompletionHandler() throws Exception {
        System.err.println("Test: testSuspendCancelledCompletionHandler");
        setAdapterAndListen(new TestStaticResourcesAdapter() {
            @Override
            public void dologic(final Request req, final Response res) throws  Throwable{
                res.suspend(60 * 1000, this, new TestCompletionHandler<StaticResourcesAdapter>() {
                    @Override
                    public void cancelled(StaticResourcesAdapter attachment) {
                        writeToSuspendedClient(res);
                        try {
                            res.flush();
                        } catch (IOException ex) {
                            ex.printStackTrace();
                        }
                    }
                });
                this.cancelLater(res);
            }
        });
        sendRequest();
    }

    public void testSuspendSuspendedExceptionCompletionHandler() throws Exception {
        System.err.println("Test: testSuspendSuspendedExceptionCompletionHandler");
        setAdapterAndListen(new TestStaticResourcesAdapter() {
            @Override
            public void dologic(final Request req, final Response res) throws Throwable{
                res.suspend(60 * 1000, this, new TestCompletionHandler<StaticResourcesAdapter>() {
                    private AtomicBoolean first = new AtomicBoolean(true);
                    @Override
                    public void resumed(StaticResourcesAdapter attachment) {
                        if (!first.compareAndSet(true, false)){
                            fail("recursive resume");
                        }
                        System.err.println("Resumed.");
                        try{
                            res.resume();
                            fail("should not reach here");;
                        }catch(IllegalStateException ise){
                            writeToSuspendedClient(res);
                        }
                    }
                });
                resumeLater(res);
            }
        });
        sendRequest(true);
    }

    public void testSuspendTimeoutCompletionHandler() throws Exception {
        System.err.println("Test: testSuspendTimeoutCompletionHandler");
        setAdapterAndListen(new TestStaticResourcesAdapter() {
            @Override
            public void dologic(final Request req, final Response res) throws Throwable{
                res.suspend(5 * 1000, this, new TestCompletionHandler<StaticResourcesAdapter>() {
                    @Override
                    public void cancelled(StaticResourcesAdapter attachment) {
                        try {
                            System.err.println("Time out");
                            res.getChannel().write(ByteBuffer.wrap(testData));
                        } catch (Throwable ex) {
                            ex.printStackTrace();
                        }
                    }
                });
            }
        });
        sendRequest();
    }

    public void testSuspendDoubleSuspendInvokation() throws Exception {
        System.err.println("Test: testSuspendDoubleSuspendInvokation");
        setAdapterAndListen(new TestStaticResourcesAdapter() {
            @Override
            public void dologic(final Request req, final Response res) throws Throwable{
                res.suspend(60 * 1000, this, new TestCompletionHandler<StaticResourcesAdapter>() {
                    @Override
                    public void resumed(StaticResourcesAdapter attachment) {
                        System.err.println("resumed");
                    }
                });

                pe.schedule(new Runnable() {
                    public void run() {
                        try {
                            res.suspend();
                            fail("should not reach here");
                        } catch (IllegalStateException t) {
                            System.err.println("catched suspended suspend");
                            writeToSuspendedClient(res);
                            try{
                                res.resume();
                            }catch(Throwable at){
                                at.printStackTrace();
                                fail(at.getMessage());
                            }
                        }
                    }
                }, 2, TimeUnit.SECONDS);
            }
        });
        sendRequest();
    }

    public void testSuspendDoubleResumeInvokation() throws Exception {
        System.err.println("Test: testSuspendDoubleResumeInvokation");
        setAdapterAndListen(new TestStaticResourcesAdapter() {
            @Override
            public void dologic(final Request req, final Response res) throws Throwable {
                res.suspend(60 * 1000, this, new TestCompletionHandler<StaticResourcesAdapter>() {
                    @Override
                    public void resumed(StaticResourcesAdapter attachment) {
                        try {
                            System.err.println("trying to resume");
                            res.resume();
                            fail("should no get here");
                        } catch (IllegalStateException ex) {
                            writeToSuspendedClient(res);
                        }
                    }
                });
               resumeLater(res);
            }
        });
        sendRequest();
    }

    public void testSuspendResumedCompletionHandlerGrizzlyAdapter() throws Exception {
        System.err.println("Test: testSuspendResumedCompletionHandlerGrizzlyAdapter");;
        setAdapterAndListen(new GrizzlyAdapter() {
            @Override
            public void service(final GrizzlyRequest req, final GrizzlyResponse res) {
                try {
                    res.suspend(60 * 1000, this, new TestCompletionHandler<GrizzlyAdapter>() {
                        @Override
                        public void resumed(GrizzlyAdapter attachment) {
                            if (res.isSuspended()) {
                                System.err.println("Resumed");
                                try{
                                    res.getWriter().write(testString);
                                } catch (Exception ex) {
                                    ex.printStackTrace();
                                }
                            }else{
                                fail("resumed without being suspended");
                            }
                        }
                    });

                    resumeLater(res);
                } catch (Throwable t) {
                    t.printStackTrace();
                    fail(t.getMessage());
                }
            }
        });
        sendRequest();
    }

    public void testSuspendTimeoutCompletionHandlerGrizzlyAdapter() throws Exception {
        System.err.println("Test: testSuspendTimeoutCompletionHandlerGrizzlyAdapter");
        setAdapterAndListen(new GrizzlyAdapter() {
            @Override
            public void service(final GrizzlyRequest req, final GrizzlyResponse res) {
                try {
                    final long t1 = System.currentTimeMillis();
                    res.suspend(10 * 1000, "foo", new TestCompletionHandler<String>() {
                        @Override
                        public void cancelled(String attachment) {
                            try {
                                System.err.println("Cancelling TOOK: " + (System.currentTimeMillis() - t1));
                                res.getWriter().write(testString);
                            } catch (Throwable ex) {
                                ex.printStackTrace();
                            }
                        }
                    });
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }
        });
        sendRequest();
    }

    public void testFastSuspendResumeGrizzlyAdapter() throws Exception {
        System.err.println("Test: testFastSuspendResumeGrizzlyAdapter");
        setAdapterAndListen(new GrizzlyAdapter() {
            @Override
            public void service(final GrizzlyRequest req, final GrizzlyResponse res) {
                try {
                    final long t1 = System.currentTimeMillis();
                    res.suspend(10 * 1000, "foo", new TestCompletionHandler<String>() {
                        @Override
                        public void resumed(String attachment) {
                            try {
                                System.err.println("Resumed TOOK: " + (System.currentTimeMillis() - t1));
                                res.getWriter().write(testString);
                                res.finishResponse(); 
                            // res.flushBuffer();
                            } catch (Exception ex) {
                                ex.printStackTrace();
                            } 
                        }
                    });
                } catch (Throwable t) {
                    t.printStackTrace();
                }

                new WorkerThreadImpl(new Runnable() {
                    public void run() {
                        try {
                            Thread.sleep(5000);
                        } catch (InterruptedException ex) {
                            ex.printStackTrace();
                            fail(ex.getMessage());
                        }

                        if (!res.isCommitted()) {
                            System.err.println("Resuming");
                            res.resume();
                        }else{
                            fail("response is commited so we dont resume");
                        }
                    }
                }).start();
            }
        });
        sendRequest();
    }



    public void testSuspendResumeOneTransaction() throws Exception {
        System.err.println("Test: testSuspendResumeOneTransaction");
        setAdapterAndListen(new TestStaticResourcesAdapter() {
            @Override
                public void service(final Request req, final Response res) {                
                try {
                    res.suspend();
                    res.getChannel().write(ByteBuffer.wrap(testData));
                    res.flush();
                    res.resume();
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        });
        sendRequest();
    }

    
    private void resumeLater(final GrizzlyResponse res){
        pe.schedule(new Runnable() {
            public void run() {                
                if (res.isSuspended()) {
                    try {
                        System.err.println("Now Resuming");
                        res.resume();
                    } catch (Throwable ex) {
                        ex.printStackTrace();
                        fail("resume failed: "+ex.getMessage());
                    }
                }else{
                    fail("not suspended, so we dont resume");
                }
            }
        }, 2, TimeUnit.SECONDS);
    }
    
    private void sendRequest() throws Exception {
        sendRequest(true);
    }

    private void sendRequest(boolean assertResponse) throws Exception {
        Socket s = new Socket("localhost", PORT);
        try{
            s.setSoTimeout(30 * 1000);
            OutputStream os = s.getOutputStream();

            System.err.println(("GET / HTTP/1.1\n"));
            os.write(("GET / HTTP/1.1\n").getBytes());
            os.write(("Host: localhost:" + PORT + "\n").getBytes());
            os.write("\n".getBytes());
            os.flush();

            InputStream is = new DataInputStream(s.getInputStream());
            BufferedReader br = new BufferedReader(new InputStreamReader(is));
            String line = null;
            System.err.println("================== reading the response");
            boolean gotCorrectResponse = false;
            while ((line = br.readLine()) != null) {
                System.err.println("-> " + line + " --> " + line.startsWith(testString));
                if (line.startsWith(testString)) {
                    gotCorrectResponse = true;
                    break;
                }
            }
            if (assertResponse)
                assertTrue(gotCorrectResponse);
        }finally{
        }
    }
    

    private class TestStaticResourcesAdapter extends StaticResourcesAdapter {
        @Override
        public void service(Request req,Response res) {
                try {
                    if (res.isSuspended()) {
                        super.service(req, res);
                    }else{
                        dologic(req,res);
                    }
                }catch(junit.framework.AssertionFailedError ae){
                }
                catch (Throwable t) {
                    t.printStackTrace();
                    fail(t.getMessage());
                }
            }

        public void dologic(final Request req, final Response res) throws Throwable {
        }

        @Override
        public void afterService(final Request req, final Response res) {
            if (res.isSuspended()) {
                try {
                    super.afterService(req, res);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        }

        protected void writeToSuspendedClient(Response resp){
            if (resp.isSuspended()) {
                try {
                    resp.getChannel().write(ByteBuffer.wrap(testData));
                } catch (Throwable ex) {
                    ex.printStackTrace();
                }
            }else{
                fail("Not Suspended.");
            }
        }
        
        protected void cancelLater(final Response res){
            pe.schedule(new Runnable() {
                public void run() {
                    if (res.isSuspended()) {
                        try {
                            System.err.println("Now cancel");
                            res.cancel();
                        } catch (Throwable ex) {
                            ex.printStackTrace();
                            fail("cancel failed: "+ex.getMessage());
                        }
                    }else{
                        fail("not suspended, so we dont cancel");
                    }
                }
            }, 2, TimeUnit.SECONDS);
        }

        protected void resumeLater(final Response res){
            pe.schedule(new Runnable() {
                public void run() {
                    if (res.isSuspended()) {
                        try {
                            System.err.println("Now Resuming");
                            res.resume();
                        } catch (Throwable ex) {
                            ex.printStackTrace();
                            fail("resume failed: "+ex.getMessage());
                        }
                    }else{
                        fail("not suspended, so we dont resume");
                    }
                }
            }, 2, TimeUnit.SECONDS);
        }
    }

    
    private class TestCompletionHandler<StaticResourcesAdapter> implements CompletionHandler<StaticResourcesAdapter> {
        public void resumed(StaticResourcesAdapter attachment) {
            fail("Unexpected resume");
        }
        public void cancelled(StaticResourcesAdapter attachment) {
            fail("Unexpected Cancel");
        }
    }
}
