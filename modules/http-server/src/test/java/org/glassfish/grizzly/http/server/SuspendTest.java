/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2015 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.http.server;

import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import junit.framework.AssertionFailedError;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.EmptyCompletionHandler;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.SocketConnectorHandler;
import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.FilterChainBuilder;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.filterchain.TransportFilter;
import org.glassfish.grizzly.http.HttpClientFilter;
import org.glassfish.grizzly.http.HttpContent;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.impl.FutureImpl;
import org.glassfish.grizzly.impl.SafeFutureImpl;
import org.glassfish.grizzly.nio.transport.TCPNIOConnectorHandler;
import org.glassfish.grizzly.ssl.SSLContextConfigurator;
import org.glassfish.grizzly.ssl.SSLEngineConfigurator;
import org.glassfish.grizzly.ssl.SSLFilter;
import org.glassfish.grizzly.utils.Futures;
import org.junit.After;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/**
 * Units test that exercise the {@link Response#suspend() }, {@link Response#resume() }
 * and {@link Response#cancel() } API.
 *
 * @author Jeanfrancois Arcand
 * @author gustav trede
 */
@RunWith(Parameterized.class)
public class SuspendTest {

    private static final Logger LOGGER = Grizzly.logger(SuspendTest.class);
    
    public static final int PORT = 18890;
    private ScheduledThreadPoolExecutor scheduledThreadPool;
    private final String testString = "blabla test.";
    private final byte[] testData = testString.getBytes();
    private final boolean isSslEnabled;
    private HttpServer httpServer;

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
        configureHttpServer();
    }

    @After
    public void after() throws Exception {
        if (httpServer != null) {
            httpServer.shutdownNow();
        }

        scheduledThreadPool.shutdown();
    }

    private void configureHttpServer() throws Exception {
        httpServer = new HttpServer();
        final NetworkListener listener =
                new NetworkListener("grizzly",
                                    NetworkListener.DEFAULT_NETWORK_HOST,
                                    PORT);
        if (isSslEnabled) {
            listener.setSecure(true);
            listener.setSSLEngineConfig(createSSLConfig(true));
        }
        httpServer.addListener(listener);

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

    private void startHttpServer(HttpHandler httpHandler) throws Exception {
        httpServer.getServerConfiguration().addHttpHandler(httpHandler);
        httpServer.start();
    }

    @Test
    public void testSuspendResumeSameTransaction() throws Exception {
        startHttpServer(new TestStaticHttpHandler() {
            @Override
            public void service(Request req, Response res) {
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
        startHttpServer(new TestStaticHttpHandler() {

            @Override
            public void service(Request req, Response res) {
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
        startHttpServer(new TestStaticHttpHandler() {

            @Override
            public void service(Request req, Response res) {
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
        startHttpServer(new TestStaticHttpHandler() {

            @Override
            public void service(Request req, final Response res) {
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
    public void testResumeAfterClose() throws Exception {
        final FutureImpl<Boolean> resultFuture = Futures.createSafeFuture();

        
        startHttpServer(new TestStaticHttpHandler() {

            @Override
            public void service(final Request req, final Response res) {
                res.suspend();
                scheduledThreadPool.schedule(new Runnable() {

                    @Override
                    public void run() {
                        try {
                            req.getRequest().getConnection().closeSilently();
                            res.resume();
                            resultFuture.result(Boolean.TRUE);
                        } catch (Throwable t) {
                            resultFuture.failure(t);
                        }
                    }
                }, 2, TimeUnit.SECONDS);
            }
        });
        
        runClient(testString, false, null);
        assertTrue(resultFuture.get(10, TimeUnit.SECONDS));
    }
    
    @Test
    public void testSuspendResumedCompletionHandler() throws Exception {
        startHttpServer(new TestStaticHttpHandler() {

            @Override
            public void doLogic(Request req, final Response res) throws Throwable {
                res.suspend(60, TimeUnit.SECONDS, new TestCompletionHandler<Response>() {

                    @Override
                    public void completed(Response result) {
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
        startHttpServer(new TestStaticHttpHandler() {

            @Override
            public void doLogic(Request req, final Response res) throws Throwable {
                res.suspend(60, TimeUnit.SECONDS, new TestCompletionHandler<Response>() {

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
        startHttpServer(new TestStaticHttpHandler() {

            @Override
            public void doLogic(Request req, final Response res) throws Throwable {
                res.suspend(60, TimeUnit.SECONDS, new TestCompletionHandler<Response>() {

                    private final AtomicBoolean first = new AtomicBoolean(true);

                    @Override
                    public void completed(Response result) {
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
        startHttpServer(new TestStaticHttpHandler() {

            @Override
            public void doLogic(Request req, final Response res) throws Throwable {
                res.suspend(5, TimeUnit.SECONDS, new TestCompletionHandler<Response>() {

                    @Override
                    public void cancelled() {
                        try {
                            write(res, testData);
                        } catch (Throwable ex) {
                            ex.printStackTrace();
                        } finally {
                            res.resume();
                        }
                    }
                });
            }
        });
        runTest();
    }

    @Test
    public void testSuspendTimeoutTimeoutHandler() throws Exception {
        final AtomicReference<byte[]> responseData = new AtomicReference<byte[]>();
        responseData.set("bad response".getBytes());

        startHttpServer(new TestStaticHttpHandler() {

            @Override
            public void doLogic(Request req, final Response res) throws Throwable {
                res.suspend(5, TimeUnit.SECONDS, new TestCompletionHandler<Response>() {

                    @Override
                    public void cancelled() {
                        try {
                            write(res, responseData.get());
                        } catch (Throwable ex) {
                            ex.printStackTrace();
                        } finally {
                            res.resume();
                        }
                    }
                }, new TimeoutHandler() {
                    int counter = 0;

                    @Override
                    public boolean onTimeout(Response response) {
                        if (++counter == 1) {
                            response.getSuspendContext().setTimeout(5, TimeUnit.SECONDS);
                            return false;
                        } else if (counter == 2) {
                            responseData.set(testData);
                            return true;
                        }

                        throw new IllegalStateException();
                    }
                });
            }
        });
        runTest();
    }

    @Test
    public void testSuspendDoubleSuspendInvokation() throws Exception {
        startHttpServer(new TestStaticHttpHandler() {

            @Override
            public void doLogic(Request req, final Response res) throws Throwable {
                res.suspend(60, TimeUnit.SECONDS, new TestCompletionHandler<Response>() {

                    @Override
                    public void completed(Response result) {
//                        System.out.println("resumed");
                    }
                });

                scheduledThreadPool.schedule(new Runnable() {

                    @Override
                    public void run() {
                        try {
                            res.suspend();
                            fail("should not reach here");
                        } catch (IllegalStateException t) {
//                            System.out.println("catched suspended suspend");
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
        startHttpServer(new TestStaticHttpHandler() {

            @Override
            public void doLogic(Request req, final Response res) throws Throwable {
                res.suspend(60, TimeUnit.SECONDS, new TestCompletionHandler<Response>() {

                    @Override
                    public void completed(Response result) {
                        try {
//                            System.out.println("trying to resume");
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
    public void testSuspendResumedCompletionHandlerHttpHandler() throws Exception {
        startHttpServer(new HttpHandler() {

            @Override
            public void service(Request req, final Response res) {
                try {
                    res.suspend(60, TimeUnit.SECONDS, new TestCompletionHandler<Response>() {

                        @Override
                        public void completed(Response result) {
                            if (res.isSuspended()) {
//                                System.out.println("Resumed");
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
    public void testSuspendTimeoutCompletionHandlerHttpHandler() throws Exception {
        startHttpServer(new HttpHandler() {

            @Override
            public void service(Request req, final Response res) {
                try {
                    final long t1 = System.currentTimeMillis();
                    res.suspend(10, TimeUnit.SECONDS, new TestCompletionHandler<Response>() {

                        @Override
                        public void cancelled() {
                            try {
//                                System.out.println("Cancelling TOOK: " + (System.currentTimeMillis() - t1));
                                res.getWriter().write(testString);
                            } catch (Throwable ex) {
                                ex.printStackTrace();
                            } finally {
                                res.resume();
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
    public void testFastSuspendResumeHttpHandler() throws Exception {
        startHttpServer(new HttpHandler() {

            @Override
            public void service(Request req, final Response res) {
                try {
                    final long t1 = System.currentTimeMillis();
                    res.suspend(10, TimeUnit.SECONDS, new TestCompletionHandler<Response>() {

                        @Override
                        public void completed(Response result) {
                            try {
                                System.out.println("Resumed TOOK: " + (System.currentTimeMillis() - t1));
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
                            System.out.println("Resuming");
                            res.resume();
                        } else {
                            fail("response is committed so we don't resume");
                        }
                    }
                }).start();
            }
        });
        runTest();
    }

    private void write(Response response, byte[] data) throws IOException {
        response.setContentType("custom/response");
        response.getOutputBuffer().write(data);
        response.flush();
    }

    private void runTest() throws Exception {
        runTest(true);
    }

    private void runTest(boolean assertResponse) throws Exception {
        FutureImpl<Boolean> resultFuture = SafeFutureImpl.create();
        final Connection connection = runClient(testString, true, resultFuture);

        try {
            assertTrue(resultFuture.get(30, TimeUnit.SECONDS));
        } finally {
            connection.closeSilently();
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

        SocketConnectorHandler connectorHandler = TCPNIOConnectorHandler.builder(
                httpServer.getListener("grizzly").getTransport())
                .processor(builder.build())
                .build();

        Future<Connection> connectFuture = connectorHandler.connect("localhost", PORT);
        return connectFuture.get(10, TimeUnit.SECONDS);
    }

    protected void resumeLater(final Response res) {
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

    protected void cancelLater(final Response res) {
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

    private class TestStaticHttpHandler extends HttpHandler {

        @Override
        public void service(Request req, Response res) {
            try {
                if (!res.isSuspended()) {
                    doLogic(req, res);
                }
            } catch (AssertionFailedError ae) {
            } catch (Throwable t) {
                t.printStackTrace();
                fail(t.getMessage());
            }
        }

        public void doLogic(Request req, Response res) throws Throwable {
        }

        protected void writeToSuspendedClient(Response resp) {
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
            final HttpRequestPacket request = HttpRequestPacket.builder()
                    .method("GET")
                    .uri("/non-static")
                    .protocol("HTTP/1.1")
                    .header("Host", "localhost:" + PORT)
                    .build();

            ctx.write(request);
            return ctx.getStopAction();
        }

        @Override
        public NextAction handleRead(FilterChainContext ctx) throws IOException {
            final HttpContent httpContent = ctx.getMessage();
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

    private static class TestCompletionHandler<E> extends EmptyCompletionHandler<E> {

        @Override
        public void cancelled() {
            fail("Unexpected Cancel");
        }

        @Override
        public void completed(E result) {
            fail("Unexpected resume");
        }

        @Override
        public void failed(Throwable throwable) {
            fail("Unexpected failure");
        }
    }
}
