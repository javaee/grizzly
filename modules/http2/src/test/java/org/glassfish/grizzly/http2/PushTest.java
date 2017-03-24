/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2017 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.http2;

import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.SocketConnectorHandler;
import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.Filter;
import org.glassfish.grizzly.filterchain.FilterChain;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.http.Cookie;
import org.glassfish.grizzly.http.Cookies;
import org.glassfish.grizzly.http.HttpContent;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.http.HttpResponsePacket;
import org.glassfish.grizzly.http.Method;
import org.glassfish.grizzly.http.Protocol;
import org.glassfish.grizzly.http.server.*;
import org.glassfish.grizzly.http.server.http2.PushBuilder;
import org.glassfish.grizzly.http.server.http2.PushEvent;
import org.glassfish.grizzly.http.server.util.Globals;
import org.glassfish.grizzly.http.util.CookieParserUtils;
import org.glassfish.grizzly.http.util.Header;
import org.glassfish.grizzly.http.util.MimeHeaders;
import org.glassfish.grizzly.memory.Buffers;
import org.glassfish.grizzly.memory.ByteBufferWrapper;
import org.glassfish.grizzly.nio.transport.TCPNIOConnectorHandler;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import org.glassfish.grizzly.utils.Pair;
import org.hamcrest.core.IsNull;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

/**
 * This is an odd test case due to how the modules are currently
 * organized.  The http-server module currently can't depend on http2
 * as http2 relies on http-server.  So we have to do some of the http2
 * server-related tests here.  We'll eventually fix this when time permits.
 */
@RunWith(Parameterized.class)
public class PushTest extends AbstractHttp2Test {

    private static final String TEMP_DIR = System.getProperty("java.io.tmpdir");
    private static final int PORT = 19999;

    private final boolean isSecure;


    // ----------------------------------------------------------- Constructors


    public PushTest(final boolean isSecure) {
        this.isSecure = isSecure;
    }


    // ----------------------------------------------------- Test Configuration


    @Parameterized.Parameters
    public static Collection<Object[]> isSecure() {
        return AbstractHttp2Test.isSecure();
    }

    @Before
    public void before() throws Exception {
        ByteBufferWrapper.DEBUG_MODE = true;
    }


    // ----------------------------------------------------------- Test Methods


    @Test
    public void pushBuilderNullMethod() {

        final AtomicBoolean npeThrown = new AtomicBoolean();
        final CountDownLatch latch = new CountDownLatch(1);

        final HttpHandler handler = new HttpHandler() {
            @Override
            public void service(final Request request, final Response response) throws Exception {

                final PushBuilder builder = request.newPushBuilder();
                try {
                    //noinspection ConstantConditions
                    builder.method(null);
                } catch (NullPointerException npe) {
                    npeThrown.compareAndSet(false, true);
                } catch (Exception e) {
                    System.out.println("Unexpected exception thrown: " + e);
                }
                latch.countDown();
            }
        };

        final Callable<Throwable> result = new Callable<Throwable>() {
            @Override
            public Throwable call() throws Exception {
                assertThat("No NullPointerException or unexpected Exception thrown when providing null to PushBuilder.method()",
                        npeThrown.get(),
                        is(true));
                return null;
            }
        };

        doApiTest(handler, result, latch);
    }

    @Test
    public void pushBuilderInvalidMethod() {

        final HashMap<Method, AtomicBoolean> methodsMap = new HashMap<>();
        methodsMap.put(Method.OPTIONS, new AtomicBoolean());
        methodsMap.put(Method.POST, new AtomicBoolean());
        methodsMap.put(Method.PUT, new AtomicBoolean());
        methodsMap.put(Method.DELETE, new AtomicBoolean());
        methodsMap.put(Method.TRACE, new AtomicBoolean());
        methodsMap.put(Method.CONNECT, new AtomicBoolean());
        methodsMap.put(Method.PATCH, new AtomicBoolean());

        final CountDownLatch latch = new CountDownLatch(1);

        final HttpHandler handler = new HttpHandler() {
            @Override
            public void service(final Request request, final Response response) throws Exception {

                final PushBuilder builder = request.newPushBuilder();
                for (Map.Entry<Method, AtomicBoolean> entry : methodsMap.entrySet()) {
                    try {
                        builder.method(entry.getKey());
                    } catch (IllegalArgumentException iae) {
                        entry.getValue().compareAndSet(false, true);
                    } catch (Exception e) {
                        System.out.println('[' + entry.getKey().getMethodString() + "] Unexpected exception: " + e);
                    }
                }
                latch.countDown();
            }
        };

        final Callable<Throwable> result = new Callable<Throwable>() {
            @Override
            public Throwable call() throws Exception {
                // validate the AtomicBooleans in the map.  They should all be true.
                for (Map.Entry<Method, AtomicBoolean> entry : methodsMap.entrySet()) {
                    assertThat(String.format("No IllegalStateException or unexpected Exception thrown when providing %s to PushBuilder.method()",
                            entry.getKey().getMethodString()), entry.getValue().get(), is(true));
                }
                return null;
            }
        };

        doApiTest(handler, result, latch);
    }

    @Test
    public void pushBuilderValidMethod() {

        final HashMap<Method, AtomicBoolean> methodsMap = new HashMap<>();
        methodsMap.put(Method.GET, new AtomicBoolean());
        methodsMap.put(Method.HEAD, new AtomicBoolean());

        final CountDownLatch latch = new CountDownLatch(1);

        final HttpHandler handler = new HttpHandler() {
            @Override
            public void service(final Request request, final Response response) throws Exception {

                final PushBuilder builder = request.newPushBuilder();
                for (Map.Entry<Method, AtomicBoolean> entry : methodsMap.entrySet()) {
                    try {
                        builder.method(entry.getKey());
                        entry.getValue().compareAndSet(false, true);
                    } catch (Exception e) {
                        System.out.println('[' + entry.getKey().getMethodString() + "] Unexpected exception: " + e);
                    }
                }
                latch.countDown();
            }
        };

        final Callable<Throwable> result = new Callable<Throwable>() {
            @Override
            public Throwable call() throws Exception {
                for (Map.Entry<Method, AtomicBoolean> entry : methodsMap.entrySet()) {
                    assertThat(String.format("Unexpected Exception thrown when providing %s to PushBuilder.method()",
                            entry.getKey().getMethodString()), entry.getValue().get(), is(true));
                }
                return null;
            }
        };

        doApiTest(handler, result, latch);

    }

    @Test
    public void pushBuilderNullOrEmptyQueryString() {
        final HashMap<Pair<String, String>, AtomicBoolean> methodsMap = new HashMap<>();
        methodsMap.put(new Pair<>("null", (String) null), new AtomicBoolean());
        methodsMap.put(new Pair<>("empty", ""), new AtomicBoolean());


        final CountDownLatch latch = new CountDownLatch(1);

        final HttpHandler handler = new HttpHandler() {
            @Override
            public void service(final Request request, final Response response) throws Exception {

                final PushBuilder builder = request.newPushBuilder();
                for (Map.Entry<Pair<String, String>, AtomicBoolean> entry : methodsMap.entrySet()) {
                    try {
                        builder.queryString(entry.getKey().getSecond());
                        if (builder.getQueryString() == null) {
                            entry.getValue().compareAndSet(false, true);
                        }
                    } catch (Exception e) {
                        System.out.println('[' + entry.getKey().getFirst() + "] Unexpected exception: " + e);
                    }
                }
                latch.countDown();
            }
        };

        final Callable<Throwable> result = new Callable<Throwable>() {
            @Override
            public Throwable call() throws Exception {
                for (Map.Entry<Pair<String, String>, AtomicBoolean> entry : methodsMap.entrySet()) {
                    assertThat(String.format("Unexpected value returned or exception thrown when providing %s to PushBuilder.queryString()",
                            entry.getKey().getFirst()), entry.getValue().get(), is(true));
                }
                return null;
            }
        };

        doApiTest(handler, result, latch);
    }

    @Test
    public void pushBuilderValidQueryString() {
        final HashMap<Pair<String, String>, AtomicBoolean> methodsMap = new HashMap<>();
        methodsMap.put(new Pair<>("valid query string", "a=1&b=2"), new AtomicBoolean());

        final CountDownLatch latch = new CountDownLatch(1);

        final HttpHandler handler = new HttpHandler() {
            @Override
            public void service(final Request request, final Response response) throws Exception {

                final PushBuilder builder = request.newPushBuilder();
                for (Map.Entry<Pair<String, String>, AtomicBoolean> entry : methodsMap.entrySet()) {
                    try {
                        builder.queryString(entry.getKey().getSecond());
                        if (Objects.equals(builder.getQueryString(), entry.getKey().getSecond())) {
                            entry.getValue().compareAndSet(false, true);
                        }
                    } catch (Exception e) {
                        System.out.println('[' + entry.getKey().getFirst() + "] Unexpected exception: " + e);
                    }
                }
                latch.countDown();
            }
        };

        final Callable<Throwable> result = new Callable<Throwable>() {
            @Override
            public Throwable call() throws Exception {
                for (Map.Entry<Pair<String, String>, AtomicBoolean> entry : methodsMap.entrySet()) {
                    assertThat(String.format("Unexpected value returned or exception thrown when providing %s to PushBuilder.queryString()",
                            entry.getKey().getFirst()), entry.getValue().get(), is(true));
                }
                return null;
            }
        };

        doApiTest(handler, result, latch);
    }

    @Test
    public void pushBuilderNullOrEmptySessionIdString() {
        final HashMap<Pair<String, String>, AtomicBoolean> methodsMap = new HashMap<>();
        methodsMap.put(new Pair<>("null", (String) null), new AtomicBoolean());
        methodsMap.put(new Pair<>("empty", ""), new AtomicBoolean());


        final CountDownLatch latch = new CountDownLatch(1);

        final HttpHandler handler = new HttpHandler() {
            @Override
            public void service(final Request request, final Response response) throws Exception {

                final PushBuilder builder = request.newPushBuilder();
                for (Map.Entry<Pair<String, String>, AtomicBoolean> entry : methodsMap.entrySet()) {
                    try {
                        builder.sessionId(entry.getKey().getSecond());
                        if (builder.getSessionId() == null) {
                            entry.getValue().compareAndSet(false, true);
                        }
                    } catch (Exception e) {
                        System.out.println('[' + entry.getKey().getFirst() + "] Unexpected exception: " + e);
                    }
                }
                latch.countDown();
            }
        };

        final Callable<Throwable> result = new Callable<Throwable>() {
            @Override
            public Throwable call() throws Exception {
                for (Map.Entry<Pair<String, String>, AtomicBoolean> entry : methodsMap.entrySet()) {
                    assertThat(String.format("Unexpected value returned or exception thrown when providing %s to PushBuilder.sessionId()",
                            entry.getKey().getFirst()), entry.getValue().get(), is(true));
                }
                return null;
            }
        };

        doApiTest(handler, result, latch);
    }

    @Test
    public void pushBuilderValidSessionIdString() {
        final HashMap<Pair<String, String>, AtomicBoolean> methodsMap = new HashMap<>();
        methodsMap.put(new Pair<>("valid session string", "somesortofsessionid"), new AtomicBoolean());

        final CountDownLatch latch = new CountDownLatch(1);

        final HttpHandler handler = new HttpHandler() {
            @Override
            public void service(final Request request, final Response response) throws Exception {

                final PushBuilder builder = request.newPushBuilder();
                for (Map.Entry<Pair<String, String>, AtomicBoolean> entry : methodsMap.entrySet()) {
                    try {
                        builder.sessionId(entry.getKey().getSecond());
                        if (Objects.equals(builder.getSessionId(), entry.getKey().getSecond())) {
                            entry.getValue().compareAndSet(false, true);
                        }
                    } catch (Exception e) {
                        System.out.println('[' + entry.getKey().getFirst() + "] Unexpected exception: " + e);
                    }
                }
                latch.countDown();
            }
        };

        final Callable<Throwable> result = new Callable<Throwable>() {
            @Override
            public Throwable call() throws Exception {
                for (Map.Entry<Pair<String, String>, AtomicBoolean> entry : methodsMap.entrySet()) {
                    assertThat(String.format("Unexpected value returned or exception thrown when providing %s to PushBuilder.sessionId()",
                            entry.getKey().getFirst()), entry.getValue().get(), is(true));
                }
                return null;
            }
        };

        doApiTest(handler, result, latch);
    }

    @Test
    public void pushBuilderNullOrEmptyPathString() {
        final HashMap<Pair<String, String>, AtomicBoolean> methodsMap = new HashMap<>();
        methodsMap.put(new Pair<>("null", (String) null), new AtomicBoolean());
        methodsMap.put(new Pair<>("empty", ""), new AtomicBoolean());


        final CountDownLatch latch = new CountDownLatch(1);

        final HttpHandler handler = new HttpHandler() {
            @Override
            public void service(final Request request, final Response response) throws Exception {

                final PushBuilder builder = request.newPushBuilder();
                for (Map.Entry<Pair<String, String>, AtomicBoolean> entry : methodsMap.entrySet()) {
                    try {
                        builder.path(entry.getKey().getSecond());
                        if (builder.getPath() == null) {
                            entry.getValue().compareAndSet(false, true);
                        }
                    } catch (Exception e) {
                        System.out.println('[' + entry.getKey().getFirst() + "] Unexpected exception: " + e);
                    }
                }
                latch.countDown();
            }
        };

        final Callable<Throwable> result = new Callable<Throwable>() {
            @Override
            public Throwable call() throws Exception {
                for (Map.Entry<Pair<String, String>, AtomicBoolean> entry : methodsMap.entrySet()) {
                    assertThat(String.format("Unexpected value returned or exception thrown when providing %s to PushBuilder.path()",
                            entry.getKey().getFirst()), entry.getValue().get(), is(true));
                }
                return null;
            }
        };

        doApiTest(handler, result, latch);
    }

    @Test
    public void pushBuilderValidPathString() {
        final HashMap<Pair<String, String>, AtomicBoolean> methodsMap = new HashMap<>();
        methodsMap.put(new Pair<>("valid path string", "/path"), new AtomicBoolean());

        final CountDownLatch latch = new CountDownLatch(1);

        final HttpHandler handler = new HttpHandler() {
            @Override
            public void service(final Request request, final Response response) throws Exception {

                final PushBuilder builder = request.newPushBuilder();
                for (Map.Entry<Pair<String, String>, AtomicBoolean> entry : methodsMap.entrySet()) {
                    try {
                        builder.path(entry.getKey().getSecond());
                        if (Objects.equals(builder.getPath(), entry.getKey().getSecond())) {
                            entry.getValue().compareAndSet(false, true);
                        }
                    } catch (Exception e) {
                        System.out.println('[' + entry.getKey().getFirst() + "] Unexpected exception: " + e);
                    }
                }
                latch.countDown();
            }
        };

        final Callable<Throwable> result = new Callable<Throwable>() {
            @Override
            public Throwable call() throws Exception {
                for (Map.Entry<Pair<String, String>, AtomicBoolean> entry : methodsMap.entrySet()) {
                    assertThat(String.format("Unexpected value returned or exception thrown when providing %s to PushBuilder.path()",
                            entry.getKey().getFirst()), entry.getValue().get(), is(true));
                }
                return null;
            }
        };

        doApiTest(handler, result, latch);
    }

    @Test
    public void pushBuilderNullOrEmptyHeaderAddNameOrValueString() {
        final HashMap<Pair<String, String>, AtomicBoolean> methodsMap = new HashMap<>();
        methodsMap.put(new Pair<>("null", (String) null), new AtomicBoolean());
        methodsMap.put(new Pair<>("empty", ""), new AtomicBoolean());


        final CountDownLatch latch = new CountDownLatch(1);

        final HttpHandler handler = new HttpHandler() {
            @Override
            public void service(final Request request, final Response response) throws Exception {

                final PushBuilder builder = request.newPushBuilder();

                // clear all headers first
                final Iterator<String> existingHeaders = builder.getHeaderNames().iterator();
                while (existingHeaders.hasNext()) {
                    existingHeaders.next();
                    existingHeaders.remove();
                }

                for (Map.Entry<Pair<String, String>, AtomicBoolean> entry : methodsMap.entrySet()) {
                    try {
                        builder.addHeader(entry.getKey().getSecond(), "value");
                        if (!builder.getHeaderNames().iterator().hasNext()) {
                            builder.addHeader("name", entry.getKey().getSecond());
                            if (!builder.getHeaderNames().iterator().hasNext()) {
                                entry.getValue().compareAndSet(false, true);
                            }
                        }
                    } catch (Exception e) {
                        System.out.println('[' + entry.getKey().getFirst() + "] Unexpected exception: " + e);
                    }
                }
                latch.countDown();
            }
        };

        final Callable<Throwable> result = new Callable<Throwable>() {
            @Override
            public Throwable call() throws Exception {
                for (Map.Entry<Pair<String, String>, AtomicBoolean> entry : methodsMap.entrySet()) {
                    assertThat(String.format("Unexpected header returned or exception thrown when providing %s to PushBuilder.addHeader() (either name or value)",
                            entry.getKey().getFirst()), entry.getValue().get(), is(true));
                }
                return null;
            }
        };

        doApiTest(handler, result, latch);
    }

    @Test
    public void pushBuilderValidHeaderSetNameAndValueString() {
        final HashMap<Pair<String, String>, AtomicBoolean> methodsMap = new HashMap<>();
        methodsMap.put(new Pair<>("header-name", "header-value"), new AtomicBoolean());

        final CountDownLatch latch = new CountDownLatch(1);

        final HttpHandler handler = new HttpHandler() {
            @Override
            public void service(final Request request, final Response response) throws Exception {

                final PushBuilder builder = request.newPushBuilder();
                for (Map.Entry<Pair<String, String>, AtomicBoolean> entry : methodsMap.entrySet()) {
                    try {
                        builder.addHeader(entry.getKey().getFirst(), entry.getKey().getSecond());
                        if (Objects.equals(builder.getHeader(entry.getKey().getFirst()), entry.getKey().getSecond())) {
                            entry.getValue().compareAndSet(false, true);
                        }
                    } catch (Exception e) {
                        System.out.println('[' + entry.getKey().getFirst() + "] Unexpected exception: " + e);
                    }
                }
                latch.countDown();
            }
        };

        final Callable<Throwable> result = new Callable<Throwable>() {
            @Override
            public Throwable call() throws Exception {
                for (Map.Entry<Pair<String, String>, AtomicBoolean> entry : methodsMap.entrySet()) {
                    assertThat("Unexpected value returned or exception thrown when providing a valid name/value to PushBuilder.addHeader()",
                            entry.getValue().get(), is(true));
                }
                return null;
            }
        };

        doApiTest(handler, result, latch);
    }

    @Test
    public void pushBuilderNullOrEmptyHeaderSetNameOrValueString() {
        final HashMap<Pair<String, String>, AtomicBoolean> methodsMap = new HashMap<>();
        methodsMap.put(new Pair<>("null", (String) null), new AtomicBoolean());
        methodsMap.put(new Pair<>("empty", ""), new AtomicBoolean());


        final CountDownLatch latch = new CountDownLatch(1);

        final HttpHandler handler = new HttpHandler() {
            @Override
            public void service(final Request request, final Response response) throws Exception {

                final PushBuilder builder = request.newPushBuilder();

                // clear all headers first
                final Iterator<String> existingHeaders = builder.getHeaderNames().iterator();
                while (existingHeaders.hasNext()) {
                    existingHeaders.next();
                    existingHeaders.remove();
                }

                for (Map.Entry<Pair<String, String>, AtomicBoolean> entry : methodsMap.entrySet()) {
                    try {
                        builder.setHeader(entry.getKey().getSecond(), "value");
                        if (!builder.getHeaderNames().iterator().hasNext()) {
                            builder.setHeader("name", entry.getKey().getSecond());
                            if (!builder.getHeaderNames().iterator().hasNext()) {
                                entry.getValue().compareAndSet(false, true);
                            }
                        }
                    } catch (Exception e) {
                        System.out.println('[' + entry.getKey().getFirst() + "] Unexpected exception: " + e);
                    }
                }
                latch.countDown();
            }
        };

        final Callable<Throwable> result = new Callable<Throwable>() {
            @Override
            public Throwable call() throws Exception {
                for (Map.Entry<Pair<String, String>, AtomicBoolean> entry : methodsMap.entrySet()) {
                    assertThat(String.format("Unexpected header returned or exception thrown when providing %s to PushBuilder.setHeader() (either name or value)",
                            entry.getKey().getFirst()), entry.getValue().get(), is(true));
                }
                return null;
            }
        };

        doApiTest(handler, result, latch);
    }

    @Test
    public void pushBuilderValidHeaderAddNameAndValueString() {
        final HashMap<Pair<String, String>, AtomicBoolean> methodsMap = new HashMap<>();
        methodsMap.put(new Pair<>("header-name", "header-value"), new AtomicBoolean());

        final CountDownLatch latch = new CountDownLatch(1);

        final HttpHandler handler = new HttpHandler() {
            @Override
            public void service(final Request request, final Response response) throws Exception {

                final PushBuilder builder = request.newPushBuilder();
                for (Map.Entry<Pair<String, String>, AtomicBoolean> entry : methodsMap.entrySet()) {
                    try {
                        builder.setHeader(entry.getKey().getFirst(), entry.getKey().getSecond());
                        if (Objects.equals(builder.getHeader(entry.getKey().getFirst()), entry.getKey().getSecond())) {
                            entry.getValue().compareAndSet(false, true);
                        }
                    } catch (Exception e) {
                        System.out.println('[' + entry.getKey().getFirst() + "] Unexpected exception: " + e);
                    }
                }
                latch.countDown();
            }
        };

        final Callable<Throwable> result = new Callable<Throwable>() {
            @Override
            public Throwable call() throws Exception {
                for (Map.Entry<Pair<String, String>, AtomicBoolean> entry : methodsMap.entrySet()) {
                    assertThat("Unexpected value returned or exception thrown when providing a valid name/value to PushBuilder.setHeader()",
                            entry.getValue().get(), is(true));
                }
                return null;
            }
        };

        doApiTest(handler, result, latch);
    }

    @Test
    public void pushBuilderRemoveHeader() {
        final HashMap<Pair<String, String>, AtomicBoolean> methodsMap = new HashMap<>();
        methodsMap.put(new Pair<>("header-name", "header-value"), new AtomicBoolean());

        final CountDownLatch latch = new CountDownLatch(1);

        final HttpHandler handler = new HttpHandler() {
            @Override
            public void service(final Request request, final Response response) throws Exception {

                final PushBuilder builder = request.newPushBuilder();
                for (Map.Entry<Pair<String, String>, AtomicBoolean> entry : methodsMap.entrySet()) {
                    try {
                        builder.setHeader(entry.getKey().getFirst(), entry.getKey().getSecond());
                        if (Objects.equals(builder.getHeader(entry.getKey().getFirst()), entry.getKey().getSecond())) {
                            builder.removeHeader(entry.getKey().getFirst());
                            if (builder.getHeader(entry.getKey().getFirst()) == null) {
                                entry.getValue().compareAndSet(false, true);
                            }
                        }
                    } catch (Exception e) {
                        System.out.println('[' + entry.getKey().getFirst() + "] Unexpected exception: " + e);
                    }
                }
                latch.countDown();
            }
        };

        final Callable<Throwable> result = new Callable<Throwable>() {
            @Override
            public Throwable call() throws Exception {
                for (Map.Entry<Pair<String, String>, AtomicBoolean> entry : methodsMap.entrySet()) {
                    assertThat("Unexpected result or exception thrown when validating PushBuilder.removeHeader()",
                            entry.getValue().get(), is(true));
                }
                return null;
            }
        };

        doApiTest(handler, result, latch);
    }

    @Test
    public void pushBuilderPushNoPath() {

        final AtomicBoolean iseException = new AtomicBoolean();
        final CountDownLatch latch = new CountDownLatch(1);

        final HttpHandler handler = new HttpHandler() {
            @Override
            public void service(final Request request, final Response response) throws Exception {

                final PushBuilder builder = request.newPushBuilder();
                try {
                    //noinspection ConstantConditions
                    builder.push();
                } catch (IllegalStateException ise) {
                    iseException.compareAndSet(false, true);
                } catch (Exception e) {
                    System.out.println("Unexpected exception thrown: " + e);
                }
                latch.countDown();
            }
        };

        final Callable<Throwable> result = new Callable<Throwable>() {
            @Override
            public Throwable call() throws Exception {
                assertThat("No IllegalStateException not thrown or unexpected Exception thrown when PushBuilder.push() without setting a path.",
                        iseException.get(),
                        is(true));
                return null;
            }
        };

        doApiTest(handler, result, latch);
    }

    @Test
    public void pushBuilderValidateNoSessionId() {
        final AtomicBoolean sessionIdNull = new AtomicBoolean();

        final CountDownLatch latch = new CountDownLatch(1);

        final HttpHandler handler = new HttpHandler() {
            @Override
            public void service(final Request request, final Response response) throws Exception {

                final PushBuilder builder = request.newPushBuilder();
                sessionIdNull.compareAndSet(false, builder.getSessionId() == null);
                latch.countDown();
            }
        };

        final Callable<Throwable> result = new Callable<Throwable>() {
            @Override
            public Throwable call() throws Exception {
                assertThat("Non-null value returned by PushBuilder.getSessionId() when no session or requested session ID was present.",
                        sessionIdNull.get(), is(true));
                return null;
            }
        };

        doApiTest(handler, result, latch);
    }

    @Test
    public void pushBuilderValidateSessionIdFromSession() {
        final AtomicBoolean sessionIdBySession = new AtomicBoolean();

        final CountDownLatch latch = new CountDownLatch(1);

        final HttpHandler handler = new HttpHandler() {
            @Override
            public void service(final Request request, final Response response) throws Exception {
                final Session session = request.getSession(true);
                final PushBuilder builder = request.newPushBuilder();
                sessionIdBySession.compareAndSet(false, builder.getSessionId().equals(session.getIdInternal()));
                latch.countDown();
            }
        };

        final Callable<Throwable> result = new Callable<Throwable>() {
            @Override
            public Throwable call() throws Exception {
                assertThat("Unexpected value returned by PushBuilder.getSessionId().  Value returned did not match the ID of the current session.",
                        sessionIdBySession.get(), is(true));
                return null;
            }
        };

        doApiTest(handler, result, latch);
    }

    @Test
    public void pushBuilderValidateSessionIdFromRequestedSessionId() {
        final AtomicBoolean sessionIdBySession = new AtomicBoolean();
        final String expectedId = "some-value";

        final CountDownLatch latch = new CountDownLatch(1);

        final HttpHandler handler = new HttpHandler() {
            @Override
            public void service(final Request request, final Response response) throws Exception {
                final PushBuilder builder = request.newPushBuilder();
                sessionIdBySession.compareAndSet(false, builder.getSessionId().equals(expectedId));
                latch.countDown();
            }
        };

        final Callable<Throwable> result = new Callable<Throwable>() {
            @Override
            public Throwable call() throws Exception {
                assertThat("Unexpected value returned by PushBuilder.getSessionId().  Value returned did not match the ID of the current session.",
                        sessionIdBySession.get(), is(true));
                return null;
            }
        };

        HttpRequestPacket request = HttpRequestPacket.builder()
                .method(Method.GET)
                .header(Header.Host, "localhost:" + PORT)
                .header(Header.Cookie, Globals.SESSION_COOKIE_NAME + '=' + expectedId)
                .uri("/test")
                .protocol(Protocol.HTTP_2_0)
                .build();

        doApiTest(request, handler, result, latch);
    }

    @Test
    public void basicPush() {
        final BlockingQueue<HttpContent> resultQueue =
                new LinkedTransferQueue<>();

        final HttpHandler mainHandler = new HttpHandler() {

            @Override
            public void service(final Request request, final Response response) throws Exception {
                final PushBuilder builder = request.newPushBuilder();
                builder.path("/resource1");
                builder.push();
                builder.path("/resource2");
                builder.push();
                response.setCharacterEncoding("UTF-8");
                response.setContentType("text/plain");
                response.getWriter().write("main");
            }
        };

        final HttpHandler resource1 = new HttpHandler() {
            @Override
            public void service(final Request request, final Response response) throws Exception {
                response.setCharacterEncoding("UTF-8");
                response.setContentType("text/plain");
                response.getWriter().write("resource1");
            }
        };

        final HttpHandler resource2 = new HttpHandler() {
            @Override
            public void service(final Request request, final Response response) throws Exception {
                response.setCharacterEncoding("UTF-8");
                response.setContentType("text/plain");
                response.getWriter().write("resource2");
            }
        };

        final HttpRequestPacket request = HttpRequestPacket.builder()
                .method(Method.GET).protocol(Protocol.HTTP_2_0).uri("/main")
                .header(Header.Host, "localhost:" + PORT)
                .build();

        final Callable<Throwable> validator = new Callable<Throwable>() {
            @Override
            public Throwable call() throws Exception {
                final HttpContent content1 = resultQueue.poll(5, TimeUnit.SECONDS);
                assertThat("First HttpContent is null", content1, IsNull.<HttpContent>notNullValue());

                final HttpContent content2 = resultQueue.poll(5, TimeUnit.SECONDS);
                assertThat("Second HttpContent is null", content1, IsNull.<HttpContent>notNullValue());

                final HttpContent content3 = resultQueue.poll(5, TimeUnit.SECONDS);
                assertThat("Third HttpContent is null", content1, IsNull.<HttpContent>notNullValue());

                final HttpContent[] contents = new HttpContent[]{
                        content1, content2, content3
                };

                for (int i = 0, len = contents.length; i < len; i++) {
                    final HttpContent content = contents[i];
                    final HttpResponsePacket res = (HttpResponsePacket) content.getHttpHeader();
                    final HttpRequestPacket req = res.getRequest();
                    final Http2Stream stream = Http2Stream.getStreamFor(res);
                    assertThat(stream, IsNull.<Http2Stream>notNullValue());
                    assertThat(res.getContentType(), is("text/plain;charset=UTF-8"));
                    switch (req.getRequestURI()) {
                        case "/main":
                            assertThat(stream.isPushStream(), is(false));
                            assertThat(content.getContent().toStringContent(), is("main"));
                            break;
                        case "/resource1":
                            assertThat(stream.isPushStream(), is(true));
                            assertThat(content.getContent().toStringContent(), is("resource1"));
                            break;
                        case "/resource2":
                            assertThat(stream.isPushStream(), is(true));
                            assertThat(content.getContent().toStringContent(), is("resource2"));
                            break;
                        default:
                            fail("Unexpected URI: " + req.getRequestURI());
                    }
                }
                return null;

            }
        };

        doPushTest(request, validator, resultQueue,
                HttpHandlerRegistration.of(mainHandler, "/main"),
                HttpHandlerRegistration.of(resource1, "/resource1"),
                HttpHandlerRegistration.of(resource2, "/resource2"));
    }

    @Test
    public void pushValidateRemovedHeaders() throws Exception {
        final BlockingQueue<HttpContent> resultQueue =
                new LinkedTransferQueue<>();

        final HttpHandler mainHandler = new HttpHandler() {

            @Override
            public void service(final Request request, final Response response) throws Exception {
                final PushBuilder builder = request.newPushBuilder();
                builder.path("/resource1");
                builder.push();
                response.setCharacterEncoding("UTF-8");
                response.setContentType("text/plain");
                response.getWriter().write("main");
            }
        };

        final HttpHandler resource1 = new HttpHandler() {
            @Override
            public void service(final Request request, final Response response) throws Exception {
                response.setCharacterEncoding("UTF-8");
                response.setContentType("text/plain");
                response.getWriter().write("resource1");
            }
        };

        final Field removeHeadersField =
                PushBuilder.class.getDeclaredField("REMOVE_HEADERS");
        removeHeadersField.setAccessible(true);
        final Field conditionalHeadersField =
                PushBuilder.class.getDeclaredField("CONDITIONAL_HEADERS");
        conditionalHeadersField.setAccessible(true);

        final Header[] removeHeaders = (Header[]) removeHeadersField.get(null);
        final Header[] conditionalHeaders = (Header[]) conditionalHeadersField.get(null);

        final HttpRequestPacket.Builder requestBuilder = HttpRequestPacket.builder()
                .method(Method.GET).protocol(Protocol.HTTP_2_0).uri("/main")
                .header(Header.Host, "localhost:" + PORT);

        for (int i = 0, len = removeHeaders.length; i < len; i++) {
            requestBuilder.header(removeHeaders[i], removeHeaders[i].getLowerCase() + '=' + removeHeaders[i].getLowerCase());
        }
        for (int i = 0, len = conditionalHeaders.length; i < len; i++) {
            requestBuilder.header(conditionalHeaders[i], conditionalHeaders[i].getLowerCase());
        }

        final HttpRequestPacket request = requestBuilder.build();


        final Callable<Throwable> validator = new Callable<Throwable>() {
            @Override
            public Throwable call() throws Exception {
                final HttpContent content1 = resultQueue.poll(5, TimeUnit.SECONDS);
                assertThat("First HttpContent is null", content1, IsNull.<HttpContent>notNullValue());

                final HttpContent content2 = resultQueue.poll(5, TimeUnit.SECONDS);
                assertThat("Second HttpContent is null", content1, IsNull.<HttpContent>notNullValue());

                final HttpContent[] contents = new HttpContent[]{
                        content1, content2
                };

                for (int i = 0, len = contents.length; i < len; i++) {
                    final HttpContent content = contents[i];
                    final HttpResponsePacket res = (HttpResponsePacket) content.getHttpHeader();
                    final HttpRequestPacket req = res.getRequest();
                    final Http2Stream stream = Http2Stream.getStreamFor(res);
                    assertThat(stream, IsNull.<Http2Stream>notNullValue());
                    assertThat(res.getContentType(), is("text/plain;charset=UTF-8"));
                    System.out.println(content.getContent().toStringContent());
                    switch (req.getRequestURI()) {
                        case "/main":
                            continue;
                        case "/resource1":
                            for (int j = 0, jlen = removeHeaders.length; j < jlen; j++) {
                                // special case, 'referer' is added back to request with a new value
                                if (removeHeaders[j] == Header.Referer || removeHeaders[j] == Header.Cookie) {
                                    continue;
                                }
                                assertThat(req.getHeader(removeHeaders[j]), IsNull.nullValue());
                            }
                            for (int j = 0, jlen = conditionalHeaders.length; j < jlen; j++) {
                                assertThat(req.getHeader(conditionalHeaders[j]), IsNull.nullValue());
                            }
                            break;
                        default:
                            fail("Unexpected URI: " + req.getRequestURI());
                    }
                }
                return null;

            }
        };

        doPushTest(request, validator, resultQueue,
                HttpHandlerRegistration.of(mainHandler, "/main"),
                HttpHandlerRegistration.of(resource1, "/resource1"));
    }

    @Test
    public void pushValidateManualPushUsingEvent() {
        final BlockingQueue<HttpContent> resultQueue =
                new LinkedTransferQueue<>();

        final HttpHandler mainHandler = new HttpHandler() {

            @Override
            public void service(final Request request, final Response response) throws Exception {
                final MimeHeaders headers = new MimeHeaders();
                headers.copyFrom(request.getRequest().getHeaders());
                headers.setValue(Header.Referer).setString("http://locahost:" + PORT + "/main");
                PushEvent pushEvent = PushEvent.builder()
                        .path("/resource1")
                        .headers(headers)
                        .httpRequest(request.getRequest()).build();
                request.getContext().notifyDownstream(pushEvent);
                response.setCharacterEncoding("UTF-8");
                response.setContentType("text/plain");
                response.getWriter().write("main");
            }
        };

        final HttpHandler resource1 = new HttpHandler() {
            @Override
            public void service(final Request request, final Response response) throws Exception {
                response.setCharacterEncoding("UTF-8");
                response.setContentType("text/plain");
                response.getWriter().write("resource1");
            }
        };

        final HttpRequestPacket request = HttpRequestPacket.builder()
                .method(Method.GET).protocol(Protocol.HTTP_2_0).uri("/main")
                .header(Header.Host, "localhost:" + PORT).build();

        final Callable<Throwable> validator = new Callable<Throwable>() {
            @Override
            public Throwable call() throws Exception {
                final HttpContent content1 = resultQueue.poll(5, TimeUnit.SECONDS);
                assertThat("First HttpContent is null", content1, IsNull.<HttpContent>notNullValue());

                final HttpContent content2 = resultQueue.poll(5, TimeUnit.SECONDS);
                assertThat("Second HttpContent is null", content1, IsNull.<HttpContent>notNullValue());

                final HttpContent[] contents = new HttpContent[]{
                        content1, content2
                };

                for (int i = 0, len = contents.length; i < len; i++) {
                    final HttpContent content = contents[i];
                    final HttpResponsePacket res = (HttpResponsePacket) content.getHttpHeader();
                    final HttpRequestPacket req = res.getRequest();
                    final Http2Stream stream = Http2Stream.getStreamFor(res);
                    assertThat(stream, IsNull.<Http2Stream>notNullValue());
                    assertThat(res.getContentType(), is("text/plain;charset=UTF-8"));
                    switch (req.getRequestURI()) {
                        case "/main":
                            assertThat(content.getContent().toStringContent(), is("main"));
                            break;
                        case "/resource1":
                            assertThat(content.getContent().toStringContent(), is("resource1"));
                            break;
                        default:
                            fail("Unexpected URI: " + req.getRequestURI());
                    }
                }
                return null;
            }
        };

        doPushTest(request, validator, resultQueue,
                HttpHandlerRegistration.of(mainHandler, "/main"),
                HttpHandlerRegistration.of(resource1, "/resource1"));
    }

    @Test
    public void pushValidateCookies() {
        final BlockingQueue<HttpContent> resultQueue =
                new LinkedTransferQueue<>();

        final HttpHandler mainHandler = new HttpHandler() {

            @Override
            public void service(final Request request, final Response response) throws Exception {
                request.getSession(true);
                final Cookie resCookie = new Cookie("chocolate", "chip");
                resCookie.setMaxAge(Integer.MAX_VALUE);
                response.addCookie(resCookie);
                final PushBuilder builder = request.newPushBuilder();
                builder.path("/resource1").push();
                response.setCharacterEncoding("UTF-8");
                response.setContentType("text/plain");
                response.getWriter().write("main");
            }
        };

        final HttpHandler resource1 = new HttpHandler() {
            @Override
            public void service(final Request request, final Response response) throws Exception {
                response.setCharacterEncoding("UTF-8");
                response.setContentType("text/plain");
                response.getWriter().write("resource1");
            }
        };

        final HttpRequestPacket request = HttpRequestPacket.builder()
                .method(Method.GET).protocol(Protocol.HTTP_2_0).uri("/main")
                .header(Header.Cookie, "ginger=snap")
                .header(Header.Host, "localhost:" + PORT).build();

        final Callable<Throwable> validator = new Callable<Throwable>() {
            @Override
            public Throwable call() throws Exception {
                final HttpContent content1 = resultQueue.poll(5, TimeUnit.SECONDS);
                assertThat("First HttpContent is null", content1, IsNull.<HttpContent>notNullValue());

                final HttpContent content2 = resultQueue.poll(5, TimeUnit.SECONDS);
                assertThat("Second HttpContent is null", content1, IsNull.<HttpContent>notNullValue());

                final HttpContent[] contents = new HttpContent[]{
                        content1, content2
                };

                for (int i = 0, len = contents.length; i < len; i++) {
                    final HttpContent content = contents[i];
                    final HttpResponsePacket res = (HttpResponsePacket) content.getHttpHeader();
                    final HttpRequestPacket req = res.getRequest();
                    final Http2Stream stream = Http2Stream.getStreamFor(res);
                    assertThat(stream, IsNull.<Http2Stream>notNullValue());
                    assertThat(res.getContentType(), is("text/plain;charset=UTF-8"));
                    switch (req.getRequestURI()) {
                        case "/main":
                            break;
                        case "/resource1":
                            final Cookies cookies = new Cookies();
                            CookieParserUtils.parseServerCookies(cookies, req.getHeader(Header.Cookie), true, true);
                            final Cookie[] cookies1 = cookies.get();
                            assertThat(cookies1.length, is(3));
                            boolean sessionFound = false;
                            boolean gingerFound = false;
                            boolean chocolateFound = false;
                            for (int j = 0, jlen = cookies1.length; j < jlen; j++) {
                                final Cookie c = cookies1[j];
                                switch (c.getName()) {
                                    case Globals.SESSION_COOKIE_NAME:
                                        sessionFound = true;
                                        break;
                                    case "ginger":
                                        assertThat(c.getValue(), is("snap"));
                                        gingerFound = true;
                                        break;
                                    case "chocolate":
                                        assertThat(c.getValue(), is("chip"));
                                        chocolateFound = true;
                                        break;
                                    default:
                                        fail("Unexpected cookie found:" + c.getName());
                                }
                            }
                            assertThat(sessionFound, is(true));
                            assertThat(gingerFound, is(true));
                            assertThat(chocolateFound, is(true));
                            break;
                        default:
                            fail("Unexpected URI: " + req.getRequestURI());
                    }
                }
                return null;
            }
        };

        doPushTest(request, validator, resultQueue,
                HttpHandlerRegistration.of(mainHandler, "/main"),
                HttpHandlerRegistration.of(resource1, "/resource1"));
    }


    // -------------------------------------------------------- Private Methods


    private void doPushTest(final HttpRequestPacket request,
                            final Callable<Throwable> validator,
                            final BlockingQueue<HttpContent> queue,
                            final HttpHandlerRegistration... registrations) {
        final HttpServer server = createServer(registrations);
        try {
            server.start();
            sendRequest(server, request, queue);
            assertThat(validator.call(), IsNull.<Throwable>nullValue());
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        } finally {
            server.shutdownNow();
        }
    }

    private void doApiTest(final HttpHandler handler,
                           final Callable<Throwable> validator,
                           final CountDownLatch latch) {
        final HttpServer server = createServer(HttpHandlerRegistration.of(handler, "/test"));
        try {
            server.start();

            sendTestRequest(server);
            assertThat(latch.await(5, TimeUnit.SECONDS), is(true));
            assertThat(validator.call(), IsNull.<Throwable>nullValue());
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        } finally {
            server.shutdownNow();
        }
    }


    private void doApiTest(final HttpRequestPacket request,
                           final HttpHandler handler,
                           final Callable<Throwable> validator,
                           final CountDownLatch latch) {
        final HttpServer server = createServer(HttpHandlerRegistration.of(handler, "/test"));
        try {
            server.start();

            sendRequest(server, request);
            assertThat(latch.await(5, TimeUnit.SECONDS), is(true));
            assertThat(validator.call(), IsNull.<Throwable>nullValue());
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        } finally {
            server.shutdownNow();
        }
    }

    private void sendTestRequest(final HttpServer server) throws Exception {
        HttpRequestPacket request = HttpRequestPacket.builder()
                .method(Method.GET)
                .header(Header.Host, "localhost:" + PORT)
                .uri("/test")
                .protocol(Protocol.HTTP_2_0)
                .build();

        sendRequest(server, request);
    }

    private void sendRequest(final HttpServer server, final HttpRequestPacket request, final BlockingQueue<HttpContent> queue)
            throws Exception {
        final Connection c =
                getConnection(((queue != null)
                                ? new ClientAggregatorFilter(queue)
                                : null),
                        server.getListener("grizzly").getTransport());
        final HttpContent content =
                HttpContent.builder(request).content(Buffers.EMPTY_BUFFER).last(true).build();
        c.write(content);
    }

    private void sendRequest(final HttpServer server, final HttpRequestPacket request) throws Exception {
        sendRequest(server, request, null);
    }

    private HttpServer createServer(HttpHandlerRegistration... registrations) {
        return createServer(TEMP_DIR, PORT, isSecure, registrations);
    }

    private Connection getConnection(final Filter filter,
                                     final TCPNIOTransport transport)
            throws Exception {

        final FilterChain clientChain =
                createClientFilterChainAsBuilder(isSecure, true).build();

        if (filter != null) {
            clientChain.add(filter);
        }

        SocketConnectorHandler connectorHandler =
                TCPNIOConnectorHandler.builder(transport)
                        .processor(clientChain)
                        .build();

        Future<Connection> connectFuture = connectorHandler.connect("localhost", PORT);
        return connectFuture.get(10, TimeUnit.SECONDS);
    }


    // --------------------------------------------------------- Nested Classes


    private static class ClientAggregatorFilter extends BaseFilter {
        private final BlockingQueue<HttpContent> resultQueue;
        private final Map<Http2Stream, HttpContent> remaindersMap =
                new HashMap<>();

        public ClientAggregatorFilter(BlockingQueue<HttpContent> resultQueue) {
            this.resultQueue = resultQueue;
        }

        @Override
        public NextAction handleRead(FilterChainContext ctx) throws IOException {
            final HttpContent message = ctx.getMessage();
            final Http2Stream http2Stream = Http2Stream.getStreamFor(message.getHttpHeader());

            final HttpContent remainder = remaindersMap.get(http2Stream);
            final HttpContent sum = remainder != null
                    ? remainder.append(message) : message;

            if (!sum.isLast()) {
                remaindersMap.put(http2Stream, sum);
                return ctx.getStopAction();
            }

            resultQueue.add(sum);

            return ctx.getStopAction();
        }
    }

}
