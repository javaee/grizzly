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
import org.glassfish.grizzly.filterchain.Filter;
import org.glassfish.grizzly.filterchain.FilterChain;
import org.glassfish.grizzly.http.HttpContent;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.http.Method;
import org.glassfish.grizzly.http.server.HttpHandler;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.http.server.Response;
import org.glassfish.grizzly.http.server.http2.PushBuilder;
import org.glassfish.grizzly.memory.Buffers;
import org.glassfish.grizzly.memory.ByteBufferWrapper;
import org.glassfish.grizzly.nio.transport.TCPNIOConnectorHandler;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import org.hamcrest.core.IsNull;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.nullValue;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

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


    /**
     * Ensure NullPointerException is thrown if null is
     * passed to PushBuilder.method().
     */
    @Test
    public void pushBuilderNullMethod() {

        final AtomicBoolean npeThrown = new AtomicBoolean();
        final CountDownLatch latch = new CountDownLatch(1);

        final HttpHandler handler = new HttpHandler() {
            @Override
            public void service(final Request request, final Response response) throws Exception {

                final PushBuilder builder = request.getPushBuilder();
                try {
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
                try {
                    assertThat("No NullPointerException or unexpected Exception thrown when providing null to PushBuilder.method()",
                               npeThrown.get(),
                               is(true));
                    return null;
                } catch (Throwable t) {
                    return t;
                }
            }
        };

        doSimpleTest(handler, result, latch);
    }


    /**
     * Ensure IllegalStateException is thrown for invalid Methods
     * passed to PushBuilder.method().
     */
    @Test
    public void pushBuilderInvalidMethod() {

        final HashMap<Method,AtomicBoolean> methodsMap = new HashMap<>();
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

                final PushBuilder builder = request.getPushBuilder();
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
                try {
                    // validate the AtomicBooleans in the map.  They should all be true.
                    for (Map.Entry<Method, AtomicBoolean> entry : methodsMap.entrySet()) {
                        assertThat(String.format("No IllegalStateException or unexpected Exception thrown when providing %s to PushBuilder.method()",
                                entry.getKey().getMethodString()), entry.getValue().get(), is(true));
                    }
                    return null;
                } catch (Throwable t) {
                    return t;
                }
            }
        };

        doSimpleTest(handler, result, latch);
    }


    /**
     * Ensure no exception is thrown for valid value passed to
     * PushBuilder.method().
     */
    @Test
    public void pushBuilderValidMethod() {

        final HashMap<Method,AtomicBoolean> methodsMap = new HashMap<>();
        methodsMap.put(Method.GET, new AtomicBoolean());
        methodsMap.put(Method.HEAD, new AtomicBoolean());

        final CountDownLatch latch = new CountDownLatch(1);

        final HttpHandler handler = new HttpHandler() {
            @Override
            public void service(final Request request, final Response response) throws Exception {

                final PushBuilder builder = request.getPushBuilder();
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
                try {
                    for (Map.Entry<Method, AtomicBoolean> entry : methodsMap.entrySet()) {
                        assertThat(String.format("Unexpected Exception thrown when providing %s to PushBuilder.method()",
                                entry.getKey().getMethodString()), entry.getValue().get(), is(true));
                    }
                    return null;
                } catch (Throwable t) {
                    return t;
                }
            }
        };

        doSimpleTest(handler, result, latch);

    }


    // -------------------------------------------------------- Private Methods

    private void doSimpleTest(final HttpHandler handler, final Callable<Throwable> validator, final CountDownLatch latch) {
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


    private void sendTestRequest(final HttpServer server) throws Exception {
        HttpRequestPacket request = HttpRequestPacket.builder()
                .method("GET")
                .header("Host", "localhost:" + PORT)
                .uri("/test")
                .protocol("HTTP/2.0")
                .build();

        final Connection c =
                getConnection(null,
                        server.getListener("grizzly").getTransport());
        final HttpContent content =
                HttpContent.builder(request).content(Buffers.EMPTY_BUFFER).last(true).build();
        c.write(content);
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

}
