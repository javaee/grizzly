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

package org.glassfish.grizzly.http.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import junit.framework.TestCase;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.FilterChainBuilder;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.filterchain.TransportFilter;
import org.glassfish.grizzly.http.HttpClientFilter;
import org.glassfish.grizzly.http.HttpContent;
import org.glassfish.grizzly.http.HttpPacket;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.http.Method;
import org.glassfish.grizzly.http.Protocol;
import org.glassfish.grizzly.impl.FutureImpl;
import org.glassfish.grizzly.impl.SafeFutureImpl;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import org.glassfish.grizzly.nio.transport.TCPNIOTransportBuilder;
import org.glassfish.grizzly.utils.ChunkingFilter;

/**
 * Session parsing tests
 * 
 * @author Alexey Stashok
 */
@SuppressWarnings("unchecked")
public class HttpSessionTest extends TestCase {
    private static final int PORT = 8039;

    public void testPassedSessionId() throws Exception {
        final HttpHandler httpHandler = new HttpSessionHandler();
        final HttpPacket request = createRequest("/index.html;jsessionid=123456", null);
        final HttpContent response = doTest(httpHandler, request, 10);

        final String responseContent = response.getContent().toStringContent();
        Map<String, String> props = new HashMap<String, String>();

        BufferedReader reader = new BufferedReader(new StringReader(responseContent));
        String line;
        while((line = reader.readLine()) != null) {
            String[] nameValue = line.split("=");
            assertEquals(2, nameValue.length);
            props.put(nameValue[0], nameValue[1]);
        }

        String sessionId = props.get("session-id");
        assertNotNull(sessionId);
        assertEquals("123456", sessionId);
    }

    public void testPassedSessionId2() throws Exception {
        final HttpHandler httpHandler = new HttpSessionHandler();
        final HttpPacket request = createRequest("/index.html;jsessionid=123456;var=abc", null);
        final HttpContent response = doTest(httpHandler, request, 10);

        final String responseContent = response.getContent().toStringContent();
        Map<String, String> props = new HashMap<String, String>();

        BufferedReader reader = new BufferedReader(new StringReader(responseContent));
        String line;
        while((line = reader.readLine()) != null) {
            String[] nameValue = line.split("=");
            assertEquals(2, nameValue.length);
            props.put(nameValue[0], nameValue[1]);
        }

        String sessionId = props.get("session-id");
        assertNotNull(sessionId);
        assertEquals("123456", sessionId);
    }

    public void testPassedSessionIdAndJRoute() throws Exception {
        final HttpHandler httpHandler = new HttpSessionHandler();
        final HttpPacket request = createRequest("/index.html;jsessionid=123456:987", null);
        final HttpContent response = doTest(httpHandler, request, 10);

        final String responseContent = response.getContent().toStringContent();
        Map<String, String> props = new HashMap<String, String>();

        BufferedReader reader = new BufferedReader(new StringReader(responseContent));
        String line;
        while((line = reader.readLine()) != null) {
            String[] nameValue = line.split("=");
            assertEquals(2, nameValue.length);
            props.put(nameValue[0], nameValue[1]);
        }

        String sessionId = props.get("session-id");
        assertNotNull(sessionId);
        assertEquals("123456", sessionId);

        String jrouteId = props.get("jroute-id");
        assertNotNull(jrouteId);
        assertEquals("987", jrouteId);

    }

    public void testCreateSession() throws Exception {
        final HttpHandler httpHandler = new HttpCreaeteSessionHandler();
        final HttpPacket request = createRequest("/session", null);
        final HttpContent response = doTest(httpHandler, request, 10);

        final String responseContent = response.getContent().toStringContent();
        Map<String, String> props = new HashMap<String, String>();

        String cookieSessionId = null;
        int sessionCookiesNum = 0;
        Iterable<String> it = response.getHttpHeader().getHeaders().values("Set-Cookie");
        for (String value : it) {
            sessionCookiesNum++;
            cookieSessionId = value;
        }

        assertEquals(1, sessionCookiesNum);

        // Check session-id in the content
        BufferedReader reader = new BufferedReader(new StringReader(responseContent));
        String line;
        while((line = reader.readLine()) != null) {
            String[] nameValue = line.split("=");
            assertEquals(2, nameValue.length);
            props.put(nameValue[0], nameValue[1]);
        }

        String sessionId = props.get("session-id");
        assertNotNull(sessionId);
        assertTrue(cookieSessionId.indexOf(sessionId) >= 0);
    }
    
    @SuppressWarnings({"unchecked"})
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

    private HttpContent doTest(final HttpHandler httpHandler,
            final HttpPacket request,
            final int timeout)
            throws Exception {

        final TCPNIOTransport clientTransport =
                TCPNIOTransportBuilder.newInstance().build();
        final HttpServer server = createWebServer(httpHandler);
        try {
            final FutureImpl<HttpContent> testResultFuture = SafeFutureImpl.create();

            server.start();
            FilterChainBuilder clientFilterChainBuilder = FilterChainBuilder.stateless();
            clientFilterChainBuilder.add(new TransportFilter());
            clientFilterChainBuilder.add(new ChunkingFilter(5));
            clientFilterChainBuilder.add(new HttpClientFilter());
            clientFilterChainBuilder.add(new ClientFilter(testResultFuture));
            clientTransport.setProcessor(clientFilterChainBuilder.build());

            clientTransport.start();

            Future<Connection> connectFuture = clientTransport.connect("localhost", PORT);
            Connection connection = null;
            try {
                connection = connectFuture.get(timeout, TimeUnit.SECONDS);
                connection.write(request);
                return testResultFuture.get(timeout, TimeUnit.SECONDS);
            } finally {
                // Close the client connection
                if (connection != null) {
                    connection.close();
                }
            }
        } finally {
            clientTransport.stop();
            server.stop();
        }
    }

    private HttpServer createWebServer(final HttpHandler httpHandler) {

        final HttpServer server = new HttpServer();
        final NetworkListener listener =
                new NetworkListener("grizzly",
                        NetworkListener.DEFAULT_NETWORK_HOST,
                        PORT);
        listener.getKeepAlive().setIdleTimeoutInSeconds(-1);
        server.addListener(listener);
        server.getServerConfiguration().addHttpHandler(httpHandler, "/");

        return server;

    }


    private static class ClientFilter extends BaseFilter {
        private final static Logger logger = Grizzly.logger(ClientFilter.class);

        private FutureImpl<HttpContent> testFuture;

        // -------------------------------------------------------- Constructors


        public ClientFilter(FutureImpl<HttpContent> testFuture) {

            this.testFuture = testFuture;

        }


        // ------------------------------------------------- Methods from Filter

        @Override
        public NextAction handleRead(FilterChainContext ctx)
                throws IOException {

            // Cast message to a HttpContent
            final HttpContent httpContent = (HttpContent) ctx.getMessage();

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

    public class HttpSessionHandler extends HttpHandler {

        @Override
        public void service(Request request, Response response) throws Exception {
            String sessionId = request.getRequestedSessionId();
            if (sessionId != null) {
                response.getWriter().write("session-id=" + sessionId + "\n");
            }

            String jrouteId = request.getJrouteId();
            if (jrouteId != null) {
                response.getWriter().write("jroute-id=" + jrouteId + "\n");
            }
        }

    }

    public class HttpCreaeteSessionHandler extends HttpHandler {

        @Override
        public void service(Request request, Response response) throws Exception {
            final Session session = request.getSession(true);
            if (session != null) {
                response.getWriter().write("session-id=" + session.getIdInternal() + "\n");
            } else {
                response.getWriter().write("FAILED\n");
            }
        }
    }
}
