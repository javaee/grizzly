/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2014 Oracle and/or its affiliates. All rights reserved.
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
package org.glassfish.grizzly.spdy;

import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.http.HttpContent;
import org.glassfish.grizzly.http.HttpPacket;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.http.HttpResponsePacket;
import org.glassfish.grizzly.http.server.HttpHandler;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.grizzly.http.server.NetworkListener;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.http.server.Response;
import org.glassfish.grizzly.impl.FutureImpl;
import org.glassfish.grizzly.impl.SafeFutureImpl;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import org.glassfish.grizzly.nio.transport.TCPNIOTransportBuilder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import static org.glassfish.grizzly.spdy.AbstractSpdyTest.createClientFilterChain;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests Spdy semantics on HTTP layer
 * @author Alexey Stashok
 */
@RunWith(Parameterized.class)
public class HttpLayerSemanticsTest extends AbstractSpdyTest {
    private static final int PORT = 18304;
    
    private final SpdyVersion spdyVersion;
    private final SpdyMode spdyMode;
    private final boolean isSecure;
    
    public HttpLayerSemanticsTest(final SpdyVersion spdyVersion,
            final SpdyMode spdyMode,
            final boolean isSecure) {
        this.spdyVersion = spdyVersion;
        this.spdyMode = spdyMode;
        this.isSecure = isSecure;
    }

    @Parameterized.Parameters
    public static Collection<Object[]> getSpdyModes() {
        return AbstractSpdyTest.getSpdyModes();
    }
    
    // ----------------------------------------------------- Binary Test Methods


    @Test
    public void testHttpHeadersDuplication() throws Throwable {
        final String reqHeaderName = "req-header";
        final String resHeaderName = "res-header";

        final String value1 = "value1";
        final String value2 = "value2";
        final String value12 = value1 + "," + value2;
        final String value21 = value2 + "," + value1;
        
        final HttpRequestPacket requestPacket =
                (HttpRequestPacket) createRequest(PORT, "GET", null, null, null);
        
        requestPacket.addHeader(reqHeaderName, value1);
        requestPacket.addHeader(reqHeaderName, value2);
        
        final HttpContent resContent = doTest(requestPacket, 2000, new HttpHandler() {

            @Override
            public void service(Request request, Response response) throws Exception {
                response.addHeader(reqHeaderName, request.getHeader(reqHeaderName));
                response.addHeader(resHeaderName, value1);
                response.addHeader(resHeaderName, value2);
            }
        });

        final HttpResponsePacket responsePacket =
                (HttpResponsePacket) resContent.getHttpHeader();
        final String reqDuplicatedHeader = responsePacket.getHeader(reqHeaderName);
        final String resDuplicatedHeader = responsePacket.getHeader(resHeaderName);
        
        assertTrue(reqDuplicatedHeader,
                value12.equals(reqDuplicatedHeader) ||
                value21.equals(reqDuplicatedHeader));
        assertTrue(resDuplicatedHeader,
                value12.equals(resDuplicatedHeader) ||
                value21.equals(resDuplicatedHeader));
    }
    
    @Test
    public void testSpdyStreamFromHttpHandler() throws Throwable {
        final HttpRequestPacket requestPacket =
                (HttpRequestPacket) createRequest(PORT, "GET", null, null, null);
        final String spdyStreamIdHeader = "Spdy-Stream-Id";
        final HttpContent resContent = doTest(requestPacket, 2000, new HttpHandler() {

            @Override
            public void service(Request request, Response response) throws Exception {
                final SpdyStream spdyStream =
                        (SpdyStream) request.getAttribute(SpdyStream.SPDY_STREAM_ATTRIBUTE);
                response.addHeader(spdyStreamIdHeader,
                        String.valueOf(spdyStream.getStreamId()));
            }
        });

        final HttpResponsePacket responsePacket =
                (HttpResponsePacket) resContent.getHttpHeader();

        assertEquals("1", responsePacket.getHeader(spdyStreamIdHeader));
    }    
    
    // --------------------------------------------------------- Private Methods
    
    private HttpServer createWebServer(final HttpHandler httpHandler) {
        final HttpServer httpServer = createServer(null, PORT, spdyVersion,
                spdyMode, isSecure,
                AbstractSpdyTest.HttpHandlerRegistration.of(httpHandler, "/path/*"));
        
        final NetworkListener listener = httpServer.getListener("grizzly");
        listener.getKeepAlive().setIdleTimeoutInSeconds(-1);

        return httpServer;

    }

    @SuppressWarnings("unchecked")
    private HttpContent doTest(
            final HttpPacket request,
            final int timeout,
            final HttpHandler httpHandler)
            throws Exception {

        final TCPNIOTransport clientTransport =
                TCPNIOTransportBuilder.newInstance().build();
        final HttpServer server = createWebServer(httpHandler);


        try {
            final FutureImpl<HttpContent> testResultFuture = SafeFutureImpl.create();

            server.start();
            
            clientTransport.setProcessor(createClientFilterChain(spdyVersion,
                    spdyMode, isSecure,
                    new ClientFilter(testResultFuture)));

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
                    connection.closeSilently();
                }
            }
        } finally {
            clientTransport.shutdownNow();
            server.shutdownNow();
        }
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
            //noinspection ThrowableInstanceNeverThrown
            testFuture.failure(new IOException("Connection was closed"));
        }

    } // END ClientFilter      
}
