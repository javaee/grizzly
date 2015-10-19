/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012-2015 Oracle and/or its affiliates. All rights reserved.
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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.filterchain.*;
import org.glassfish.grizzly.http.*;
import org.glassfish.grizzly.impl.FutureImpl;
import org.glassfish.grizzly.impl.SafeFutureImpl;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import org.glassfish.grizzly.nio.transport.TCPNIOTransportBuilder;
import org.glassfish.grizzly.utils.Charsets;
import org.glassfish.grizzly.utils.ChunkingFilter;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * {@link BackendConfiguration} related tests.
 * 
 * @author Alexey Stashok
 */
public class BackendConfigTest {
    private static final int PORT = 18901;

    @Test
    public void testSchemeOverriding() throws Exception {
        final BackendConfiguration backendConfiguration = new BackendConfiguration();
        backendConfiguration.setScheme("https");

        final HttpPacket request = createRequest("/test", null);

        Map<String, String> props = doTest(new HttpHandler() {

            @Override
            public void service(Request request, Response response) throws Exception {
                response.getWriter().write("scheme=" + request.getScheme() + "\n");
            }
        }, backendConfiguration, request);

        String scheme = props.get("scheme");
        assertNotNull(scheme);
        assertEquals("https", scheme);
    }
    
    @Test
    public void testMappedSchemeNull() throws Exception {
        final BackendConfiguration backendConfiguration = new BackendConfiguration();
        backendConfiguration.setSchemeMapping("my-scheme");

        final HttpPacket request = createRequest("/test", null);

        Map<String, String> props = doTest(new HttpHandler() {

            @Override
            public void service(Request request, Response response) throws Exception {
                response.getWriter().write("scheme=" + request.getScheme() + "\n");

            }
        }, backendConfiguration, request);

        String scheme = props.get("scheme");
        assertNotNull(scheme);
        assertEquals("http", scheme);
    }

    @Test
    public void testMappedScheme() throws Exception {
        final BackendConfiguration backendConfiguration = new BackendConfiguration();
        backendConfiguration.setSchemeMapping("my-scheme");

        final HttpPacket request = createRequest("/test",
                Collections.singletonMap("my-scheme", "https"));

        Map<String, String> props = doTest(new HttpHandler() {

            @Override
            public void service(Request request, Response response) throws Exception {
                response.getWriter().write("scheme=" + request.getScheme() + "\n");

            }
        }, backendConfiguration, request);

        String scheme = props.get("scheme");
        assertNotNull(scheme);
        assertEquals("https", scheme);
    }

    @Test
    public void testMappedRemoteUserNull() throws Exception {
        final BackendConfiguration backendConfiguration = new BackendConfiguration();
        backendConfiguration.setRemoteUserMapping("my-user");

        final HttpPacket request = createRequest("/test", null);

        Map<String, String> props = doTest(new HttpHandler() {

            @Override
            public void service(Request request, Response response) throws Exception {
                if (request.getRemoteUser() != null) {
                    response.getWriter().write("remote-user=" + request.getRemoteUser() + "\n");
                }

            }
        }, backendConfiguration, request);

        String remoteUser = props.get("remote-user");
        assertNull(remoteUser);
    }

    @Test
    public void testMappedRemoteUser() throws Exception {
        final BackendConfiguration backendConfiguration = new BackendConfiguration();
        backendConfiguration.setRemoteUserMapping("my-user");

        final HttpPacket request = createRequest("/test",
                Collections.singletonMap("my-user", "grizzly"));

        Map<String, String> props = doTest(new HttpHandler() {

            @Override
            public void service(Request request, Response response) throws Exception {
                if (request.getRemoteUser() != null) {
                    response.getWriter().write("remote-user=" + request.getRemoteUser() + "\n");
                }

            }
        }, backendConfiguration, request);

        String remoteUser = props.get("remote-user");
        assertNotNull(remoteUser);
        assertEquals("grizzly", remoteUser);
    }

    private Map<String, String> doTest(final HttpHandler httpHandler,
            final BackendConfiguration backendConfiguration,
            final HttpPacket request
            ) throws Exception {
        final HttpServer server = createWebServer(httpHandler);

        // Override the scheme
        server.getListeners().iterator().next().setBackendConfiguration(backendConfiguration);
		
		// Set the default query encoding.
        server.getServerConfiguration().setDefaultQueryEncoding(Charsets.UTF8_CHARSET);
        try {
            server.start();
            return runRequest(request, 10);
        } finally {
            server.shutdownNow();
        }
    }
    
    @SuppressWarnings("unchecked")
    private Map<String, String> runRequest(
            final HttpPacket request,
            final int timeout)
            throws Exception {

        final TCPNIOTransport clientTransport =
                TCPNIOTransportBuilder.newInstance().build();
        try {
            final FutureImpl<HttpContent> testResultFuture = SafeFutureImpl.create();

            FilterChainBuilder clientFilterChainBuilder = FilterChainBuilder.stateless();
            clientFilterChainBuilder.add(new TransportFilter());
            clientFilterChainBuilder.add(new ChunkingFilter(4));
            clientFilterChainBuilder.add(new HttpClientFilter());
            clientFilterChainBuilder.add(new ClientFilter(testResultFuture));
            clientTransport.setProcessor(clientFilterChainBuilder.build());

            clientTransport.start();

            Future<Connection> connectFuture = clientTransport.connect("localhost", PORT);
            Connection connection = null;
            try {
                connection = connectFuture.get(timeout, TimeUnit.SECONDS);
                connection.write(request);
                HttpContent response = testResultFuture.get(timeout, TimeUnit.SECONDS);
                
                final String responseContent = response.getContent().toStringContent(Charsets.UTF8_CHARSET);
                Map<String, String> props = new HashMap<String, String>();

                BufferedReader reader = new BufferedReader(new StringReader(responseContent));
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] nameValue = line.split("=");
                    assertEquals(2, nameValue.length);
                    props.put(nameValue[0], nameValue[1]);
                }
                
                return props;
            } finally {
                // Close the client connection
                if (connection != null) {
                    connection.closeSilently();
                }
            }
        } finally {
            clientTransport.shutdownNow();
        }
    }

    private HttpPacket createRequest(String uri, Map<String, String> headers) {

        HttpRequestPacket.Builder b = HttpRequestPacket.builder();
        b.method(Method.GET).protocol(Protocol.HTTP_1_1).uri(uri).header("Host", "localhost:" + PORT);
        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                b.header(entry.getKey(), entry.getValue());
            }
        }

        return b.build();
    }
    
    private HttpServer createWebServer(final HttpHandler... httpHandlers) {

        final HttpServer server = new HttpServer();
        final NetworkListener listener =
                new NetworkListener("grizzly",
                        NetworkListener.DEFAULT_NETWORK_HOST,
                        PORT);
        listener.getKeepAlive().setIdleTimeoutInSeconds(-1);
        server.addListener(listener);
        server.getServerConfiguration().addHttpHandler(httpHandlers[0], "/");

        for (int i = 1; i < httpHandlers.length; i++) {
            // associate handlers with random context-roots
            server.getServerConfiguration().addHttpHandler(httpHandlers[i], "/" + i + "/*");
        }
        return server;

    }


    private static class ClientFilter extends BaseFilter {
        private final static Logger logger = Grizzly.logger(ClientFilter.class);

        private final FutureImpl<HttpContent> testFuture;

        // -------------------------------------------------------- Constructors


        public ClientFilter(FutureImpl<HttpContent> testFuture) {

            this.testFuture = testFuture;

        }


        // ------------------------------------------------- Methods from Filter

        @Override
        public NextAction handleRead(FilterChainContext ctx)
                throws IOException {

            // Cast message to a HttpContent
            final HttpContent httpContent = ctx.getMessage();

            logger.log(Level.FINE, "Got HTTP response chunk");

            // Get HttpContent's Buffer
            final Buffer buffer = httpContent.getContent();

            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, "HTTP content size: {0}", buffer.remaining());
            }

            if (!httpContent.isLast()) {
                return ctx.getStopAction(httpContent);
            }

            testFuture.result(httpContent);

            return ctx.getStopAction();
        }

        @Override
        public NextAction handleClose(FilterChainContext ctx)
                throws IOException {
            close();
            return ctx.getStopAction();
        }

        private void close() throws IOException {

            if (!testFuture.isDone()) {
                //noinspection ThrowableInstanceNeverThrown
                testFuture.failure(new IOException("Connection was closed"));
            }

        }

    } // END ClientFilter
    
    
}
