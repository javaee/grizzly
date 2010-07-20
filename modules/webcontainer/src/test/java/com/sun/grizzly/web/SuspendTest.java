/*
 *
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2007-2010 Sun Microsystems, Inc. All rights reserved.
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
package com.sun.grizzly.web;

import com.sun.grizzly.Connection;
import com.sun.grizzly.EmptyCompletionHandler;
import com.sun.grizzly.SocketConnectorHandler;
import com.sun.grizzly.filterchain.BaseFilter;
import com.sun.grizzly.filterchain.FilterChainBuilder;
import com.sun.grizzly.filterchain.FilterChainContext;
import com.sun.grizzly.filterchain.NextAction;
import com.sun.grizzly.filterchain.TransportFilter;
import com.sun.grizzly.http.HttpClientFilter;
import com.sun.grizzly.http.HttpContent;
import com.sun.grizzly.http.HttpRequestPacket;
import com.sun.grizzly.http.server.GrizzlyListener;
import com.sun.grizzly.http.server.GrizzlyRequest;
import com.sun.grizzly.http.server.GrizzlyResponse;
import com.sun.grizzly.http.server.GrizzlyAdapter;
import com.sun.grizzly.http.server.GrizzlyWebServer;
import com.sun.grizzly.impl.FutureImpl;
import com.sun.grizzly.impl.SafeFutureImpl;
import com.sun.grizzly.nio.transport.TCPNIOConnectorHandler;
import com.sun.grizzly.ssl.SSLContextConfigurator;
import com.sun.grizzly.ssl.SSLEngineConfigurator;
import com.sun.grizzly.ssl.SSLFilter;
import com.sun.grizzly.util.buf.ByteChunk;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import static org.junit.Assert.*;

/**
 * Units test that exercise the {@link GrizzlyResponse#suspend() }, {@link GrizzlyResponse#resume() }
 * and {@link GrizzlyResponse#cancel() } API.
 *
 * @author Jeanfrancois Arcand
 * @author gustav trede
 */
@RunWith(Parameterized.class)
public class SuspendTest {

    private static Logger logger = Logger.getLogger("grizzly.test");
    public static final int PORT = 18890;
    private ScheduledThreadPoolExecutor scheduledThreadPool;
    private final String testString = "blabla test.";
    private final byte[] testData = testString.getBytes();
    private final boolean isSslEnabled;
    private GrizzlyWebServer gws;

    public SuspendTest(boolean isSslEnabled) {
        this.isSslEnabled = isSslEnabled;
    }

    @Parameters
    public static Collection<Object[]> getSslParameter() {
        return Arrays.asList(new Object[][]{
                    {Boolean.FALSE},
                    {Boolean.TRUE}
                });
    }

    @Before
    public void before() throws Exception {
        scheduledThreadPool = new ScheduledThreadPoolExecutor(1);
        configureWebServer();
    }

    @After
    public void after() throws Exception {
        if (gws != null) {
            gws.stop();
        }

        scheduledThreadPool.shutdown();
    }

    private void configureWebServer() throws Exception {
        gws = new GrizzlyWebServer();
        final GrizzlyListener listener =
                new GrizzlyListener("grizzly",
                                    GrizzlyListener.DEFAULT_NETWORK_HOST,
                                    PORT);
        if (isSslEnabled) {
            listener.setSecure(true);
            listener.setSSLEngineConfig(createSSLConfig(true));
        }
        gws.addListener(listener);

    }

    private static SSLEngineConfigurator createSSLConfig(boolean isServer) throws Exception {
        final SSLContextConfigurator sslContextConfigurator =
                new SSLContextConfigurator();
        final ClassLoader cl = SuspendTest.class.getClassLoader();
        // override system properties
        final URL cacertsUrl = cl.getResource("ssltest-cacerts.jks");
        if (cacertsUrl != null) {
            sslContextConfigurator.setTrustStoreFile(cacertsUrl.getFile());
            sslContextConfigurator.setTrustStorePass("changeit");
        }

        // override system properties
        final URL keystoreUrl = cl.getResource("ssltest-keystore.jks");
        if (keystoreUrl != null) {
            sslContextConfigurator.setKeyStoreFile(keystoreUrl.getFile());
            sslContextConfigurator.setKeyStorePass("changeit");
        }

        return new SSLEngineConfigurator(sslContextConfigurator.createSSLContext(),
                !isServer, false, false);
    }

    private void startWebServer(GrizzlyAdapter adapter) throws Exception {
        gws.getServerConfiguration().addGrizzlyAdapter(adapter);
        gws.start();
    }

    @Test
    public void testSuspendResumeSameTransaction() throws Exception {
        startWebServer(new TestStaticResourcesAdapter() {

            @Override
            public void service(final GrizzlyRequest req, final GrizzlyResponse res) {
                try {
                    res.suspend();
                    write(res, testData);
                    res.resume();
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        });

        runTest();
    }

    @Test
    public void testSuspendResumeOneTransaction() throws Exception {
        startWebServer(new TestStaticResourcesAdapter() {

            @Override
            public void service(final GrizzlyRequest req, final GrizzlyResponse res) {
                try {
                    res.suspend();
                    write(res, testData);
                    res.flush();
                    res.resume();
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        });

        runTest();
    }

    @Test
    public void testSuspendResumeNoArgs() throws Exception {
        startWebServer(new TestStaticResourcesAdapter() {

            @Override
            public void service(final GrizzlyRequest req, final GrizzlyResponse res) {
                try {
                    res.suspend();
                    writeToSuspendedClient(res);
                    res.resume();
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        });
        runTest();
    }

    @Test
    public void testSuspendNoArgs() throws Exception {
        startWebServer(new TestStaticResourcesAdapter() {

            @Override
            public void service(final GrizzlyRequest req, final GrizzlyResponse res) {
                res.suspend();
                scheduledThreadPool.schedule(new Runnable() {

                    @Override
                    public void run() {
                        writeToSuspendedClient(res);
                        res.resume();
                    }
                }, 2, TimeUnit.SECONDS);
            }
        });
        runTest();
    }

    @Test
    public void testSuspendResumedCompletionHandler() throws Exception {
        startWebServer(new TestStaticResourcesAdapter() {

            @Override
            public void dologic(final GrizzlyRequest req, final GrizzlyResponse res) throws Throwable {
                res.suspend(60, TimeUnit.SECONDS, new TestCompletionHandler() {

                    @Override
                    public void completed(Object result) {
                        writeToSuspendedClient(res);
                        try {
                            res.flush();
                        } catch (IOException ex) {
                            ex.printStackTrace();
                        }
                    }
                });

                resumeLater(res);
            }
        });
        runTest();
    }

    @Test
    public void testSuspendCancelledCompletionHandler() throws Exception {
        startWebServer(new TestStaticResourcesAdapter() {

            @Override
            public void dologic(final GrizzlyRequest req, final GrizzlyResponse res) throws Throwable {
                res.suspend(60, TimeUnit.SECONDS, new TestCompletionHandler() {

                    @Override
                    public void cancelled() {
                        writeToSuspendedClient(res);
                        try {
                            res.flush();
                        } catch (IOException ex) {
                            ex.printStackTrace();
                        }
                    }
                });
                cancelLater(res);
            }
        });
        runTest();
    }

    @Test
    public void testSuspendSuspendedExceptionCompletionHandler() throws Exception {
        startWebServer(new TestStaticResourcesAdapter() {

            @Override
            public void dologic(final GrizzlyRequest req, final GrizzlyResponse res) throws Throwable {
                res.suspend(60, TimeUnit.SECONDS, new TestCompletionHandler() {

                    private AtomicBoolean first = new AtomicBoolean(true);

                    @Override
                    public void completed(Object result) {
                        if (!first.compareAndSet(true, false)) {
                            fail("recursive resume");
                        }
                        try {
                            res.resume();
                            fail("should not reach here");
                        } catch (IllegalStateException ise) {
                            writeToSuspendedClient(res);
                        }
                    }
                });
                resumeLater(res);
            }
        });
        runTest();
    }

    @Test
    public void testSuspendTimeoutCompletionHandler() throws Exception {
        startWebServer(new TestStaticResourcesAdapter() {

            @Override
            public void dologic(final GrizzlyRequest req, final GrizzlyResponse res) throws Throwable {
                res.suspend(5, TimeUnit.SECONDS, new TestCompletionHandler() {

                    @Override
                    public void cancelled() {
                        try {
                            write(res, testData);
                        } catch (Throwable ex) {
                            ex.printStackTrace();
                        }
                    }
                });
            }
        });
        runTest();
    }

    @Test
    public void testSuspendDoubleSuspendInvokation() throws Exception {
        startWebServer(new TestStaticResourcesAdapter() {

            @Override
            public void dologic(final GrizzlyRequest req, final GrizzlyResponse res) throws Throwable {
                res.suspend(60, TimeUnit.SECONDS, new TestCompletionHandler() {

                    @Override
                    public void completed(Object result) {
//                        Utils.dumpErr("resumed");
                    }
                });

                scheduledThreadPool.schedule(new Runnable() {

                    @Override
                    public void run() {
                        try {
                            res.suspend();
                            fail("should not reach here");
                        } catch (IllegalStateException t) {
//                            Utils.dumpErr("catched suspended suspend");
                            writeToSuspendedClient(res);
                            try {
                                res.resume();
                            } catch (Throwable at) {
                                at.printStackTrace();
                                fail(at.getMessage());
                            }
                        }
                    }
                }, 2, TimeUnit.SECONDS);
            }
        });
        runTest();
    }

    @Test
    public void testSuspendDoubleResumeInvokation() throws Exception {
        startWebServer(new TestStaticResourcesAdapter() {

            @Override
            public void dologic(final GrizzlyRequest req, final GrizzlyResponse res) throws Throwable {
                res.suspend(60, TimeUnit.SECONDS, new TestCompletionHandler() {

                    @Override
                    public void completed(Object result) {
                        try {
//                            Utils.dumpErr("trying to resume");
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
        runTest();
    }

    @Test
    public void testSuspendResumedCompletionHandlerGrizzlyAdapter() throws Exception {
        startWebServer(new GrizzlyAdapter() {

            @Override
            public void service(final GrizzlyRequest req, final GrizzlyResponse res) {
                try {
                    res.suspend(60, TimeUnit.SECONDS, new TestCompletionHandler() {

                        @Override
                        public void completed(Object result) {
                            if (res.isSuspended()) {
//                                Utils.dumpErr("Resumed");
                                try {
                                    res.getWriter().write(testString);
                                } catch (Exception ex) {
                                    ex.printStackTrace();
                                }
                            } else {
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
        runTest();
    }

    @Test
    public void testSuspendTimeoutCompletionHandlerGrizzlyAdapter() throws Exception {
        startWebServer(new GrizzlyAdapter() {

            @Override
            public void service(final GrizzlyRequest req, final GrizzlyResponse res) {
                try {
                    final long t1 = System.currentTimeMillis();
                    res.suspend(10, TimeUnit.SECONDS, new TestCompletionHandler() {

                        @Override
                        public void cancelled() {
                            try {
//                                Utils.dumpErr("Cancelling TOOK: " + (System.currentTimeMillis() - t1));
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
        runTest();
    }

    @Test
    public void testFastSuspendResumeGrizzlyAdapter() throws Exception {
        startWebServer(new GrizzlyAdapter() {

            @Override
            public void service(final GrizzlyRequest req, final GrizzlyResponse res) {
                try {
                    final long t1 = System.currentTimeMillis();
                    res.suspend(10, TimeUnit.SECONDS, new TestCompletionHandler() {

                        @Override
                        public void completed(Object result) {
                            try {
//                                Utils.dumpErr("Resumed TOOK: " + (System.currentTimeMillis() - t1));
                                res.getWriter().write(testString);
                                res.finish();
                                // res.flushBuffer();
                            } catch (Exception ex) {
                                ex.printStackTrace();
                            }
                        }
                    });
                } catch (Throwable t) {
                    t.printStackTrace();
                }

                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            Thread.sleep(5000);
                        } catch (InterruptedException ex) {
                            ex.printStackTrace();
                            fail(ex.getMessage());
                        }

                        if (!res.isCommitted()) {
//                            Utils.dumpErr("Resuming");
                            res.resume();
                        } else {
                            fail("response is commited so we dont resume");
                        }
                    }
                }).start();
            }
        });
        runTest();
    }

    private void write(GrizzlyResponse response, byte[] data) throws IOException {
        ByteChunk bc = new ByteChunk();
        bc.setBytes(data, 0, data.length);

        response.setContentType("custom/response");
        response.getOutputBuffer().write(data);
        response.flush();
    }

    private void runTest() throws Exception {
        runTest(true);
    }

    private void runTest(boolean assertResponse)
            throws Exception {
        FutureImpl<Boolean> resultFuture = SafeFutureImpl.create();
        final Connection connection = runClient(testString, true, resultFuture);

        try {
            assertTrue(resultFuture.get(30, TimeUnit.SECONDS));
        } finally {
            connection.close();
        }
    }

    private Connection runClient(String testString, boolean checkResponse,
            FutureImpl<Boolean> resultFuture) throws Exception {
        final FilterChainBuilder builder = FilterChainBuilder.stateless();
        builder.add(new TransportFilter());
        if (isSslEnabled) {
            final SSLFilter sslFilter = new SSLFilter(createSSLConfig(true),
                    createSSLConfig(false));
            builder.add(sslFilter);
        }

        builder.add(new HttpClientFilter());
        builder.add(new ClientFilter(testString, checkResponse, resultFuture));

        SocketConnectorHandler connectorHandler = new TCPNIOConnectorHandler(gws.getListener("grizzly").getTransport());
        connectorHandler.setProcessor(builder.build());

        Future<Connection> connectFuture = connectorHandler.connect("localhost", PORT);
        return connectFuture.get(10, TimeUnit.SECONDS);
    }

    protected void resumeLater(final GrizzlyResponse res) {
        scheduledThreadPool.schedule(new Runnable() {

            @Override
            public void run() {
                if (res.isSuspended()) {
                    try {
                        res.resume();
                    } catch (Throwable ex) {
                        ex.printStackTrace();
                        fail("resume failed: " + ex.getMessage());
                    }
                } else {
                    fail("not suspended, so we dont resume");
                }
            }
        }, 2, TimeUnit.SECONDS);
    }

    protected void cancelLater(final GrizzlyResponse res) {
        scheduledThreadPool.schedule(new Runnable() {

            @Override
            public void run() {
                if (res.isSuspended()) {
                    try {
                        res.cancel();
                    } catch (Throwable ex) {
                        ex.printStackTrace();
                        fail("cancel failed: " + ex.getMessage());
                    }
                } else {
                    fail("not suspended, so we dont cancel");
                }
            }
        }, 2, TimeUnit.SECONDS);
    }

    private class TestStaticResourcesAdapter extends GrizzlyAdapter {

        @Override
        public void service(GrizzlyRequest req, GrizzlyResponse res) {
            try {
                if (!res.isSuspended()) {
                    dologic(req, res);
                }
            } catch (junit.framework.AssertionFailedError ae) {
            } catch (Throwable t) {
                t.printStackTrace();
                fail(t.getMessage());
            }
        }

        public void dologic(final GrizzlyRequest req, final GrizzlyResponse res) throws Throwable {
        }

        protected void writeToSuspendedClient(GrizzlyResponse resp) {
            if (resp.isSuspended()) {
                try {
                    write(resp, testData);
                } catch (Throwable ex) {
                    ex.printStackTrace();
                }
            } else {
                fail("Not Suspended.");
            }
        }
    }

    private static class ClientFilter extends BaseFilter {

        private final String testString;
        private final boolean checkResponse;
        private final FutureImpl<Boolean> resultFuture;

        public ClientFilter(String testString, boolean checkResponse,
                FutureImpl<Boolean> resultFuture) {
            this.testString = testString;
            this.checkResponse = checkResponse;
            this.resultFuture = resultFuture;
        }

        @Override
        public NextAction handleConnect(FilterChainContext ctx) throws IOException {
            final HttpRequestPacket request = HttpRequestPacket.builder().method("GET").uri("/non-static").protocol("HTTP/1.1").header("Host", "localhost:" + PORT).build();

            ctx.write(request);
            return ctx.getStopAction();
        }

        @Override
        public NextAction handleRead(FilterChainContext ctx) throws IOException {
            final HttpContent httpContent = (HttpContent) ctx.getMessage();
            if (!httpContent.isLast()) {
                return ctx.getStopAction(httpContent);
            }

            String strContent = httpContent.getContent().toStringContent();

            if (testString.equals(strContent)) {
                resultFuture.result(Boolean.TRUE);
            } else {
                resultFuture.failure(new IllegalStateException(
                        "Response doesn't match. Expected=" + testString + " got=" + strContent));
            }

            return ctx.getStopAction();
        }
    }

    private class TestCompletionHandler extends EmptyCompletionHandler {

        @Override
        public void cancelled() {
            fail("Unexpected Cancel");
        }

        @Override
        public void completed(Object result) {
            fail("Unexpected resume");
        }

        @Override
        public void failed(Throwable throwable) {
            fail("Unexpected failure");
        }
    }
}
