/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2011 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.http.core;

import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.EmptyCompletionHandler;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.NIOTransportBuilder;
import org.glassfish.grizzly.WriteResult;
import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.Filter;
import org.glassfish.grizzly.filterchain.FilterChain;
import org.glassfish.grizzly.filterchain.FilterChainBuilder;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.filterchain.TransportFilter;
import org.glassfish.grizzly.http.HttpClientFilter;
import org.glassfish.grizzly.http.HttpContent;
import org.glassfish.grizzly.http.HttpPacket;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.http.HttpResponsePacket;
import org.glassfish.grizzly.http.HttpServerFilter;
import org.glassfish.grizzly.http.KeepAlive;
import org.glassfish.grizzly.http.Protocol;
import org.glassfish.grizzly.http.util.HttpStatus;
import org.glassfish.grizzly.impl.FutureImpl;
import org.glassfish.grizzly.impl.SafeFutureImpl;
import org.glassfish.grizzly.memory.MemoryManager;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import org.glassfish.grizzly.utils.ChunkingFilter;
import junit.framework.TestCase;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.grizzly.memory.Buffers;


/**
 * Test cases to validate HTTP protocol semantics.
 */
public class HttpSemanticsTest extends TestCase {

    public static final int PORT = 19004;
    private final FutureImpl<Throwable> exception = SafeFutureImpl.create();


    // ------------------------------------------------------------ Test Methods


    public void testUnsupportedProtocol() throws Throwable {

        HttpRequestPacket request = HttpRequestPacket.builder()
                .method("GET")
                .header("Host", "localhost:" + PORT)
                .uri("/path")
                .protocol("HTTP/1.2")
                .build();

        ExpectedResult result = new ExpectedResult();
        result.setProtocol("HTTP/1.1");
        result.setStatusCode(505);
        result.addHeader("Connection", "close");
        result.setStatusMessage("http version not supported");
        doTest(request, result);
 
    }


    public void testHttp11NoHostHeader() throws Throwable {

        HttpRequestPacket request = HttpRequestPacket.builder()
                .method("GET")
                .uri("/path")
                .protocol(Protocol.HTTP_1_1)
                .build();

        ExpectedResult result = new ExpectedResult();
        result.setProtocol("HTTP/1.1");
        result.setStatusCode(400);
        result.addHeader("Connection", "close");
        result.setStatusMessage("bad request");
        doTest(request, result);

    }


    public void testHttp09ConnectionCloseTest() throws Throwable {

        HttpRequestPacket request = HttpRequestPacket.builder()
                .method("GET")
                .uri("/path")
                .protocol("HTTP/0.9")
                .build();

        ExpectedResult result = new ExpectedResult();
        result.setProtocol("HTTP/0.9");
        result.setStatusCode(200);
        result.addHeader("!Connection", "close");
        result.setStatusMessage("ok");
        doTest(request, result);
        
    }


    public void testHttp10ConnectionCloseNoConnectionRequestHeaderTest() throws Throwable {

        HttpRequestPacket request = HttpRequestPacket.builder()
                .method("GET")
                .uri("/path")
                .protocol("HTTP/1.0")
                .build();

        ExpectedResult result = new ExpectedResult();
        result.setProtocol("HTTP/1.0");
        result.setStatusCode(200);
        result.addHeader("Connection", "close");
        result.setStatusMessage("ok");
        doTest(request, result);
        
    }


    public void testHttp11RequestCloseTest() throws Throwable {

        HttpRequestPacket request = HttpRequestPacket.builder()
                .method("GET")
                .uri("/path")
                .protocol("HTTP/1.1")
                .header("Host", "localhost:" + PORT)
                .header("Connection", "close")
                .build();

        ExpectedResult result = new ExpectedResult();
        result.setProtocol("HTTP/1.1");
        result.setStatusCode(200);
        result.addHeader("Connection", "close");
        result.setStatusMessage("ok");
        doTest(request, result);

    }

    public void testHttp11NoExplicitRequestCloseTest() throws Throwable {

        HttpRequestPacket request = HttpRequestPacket.builder()
                .method("GET")
                .uri("/path")
                .header("Host", "localhost:" + PORT)
                .protocol("HTTP/1.1")
                .build();

        ExpectedResult result = new ExpectedResult();
        result.setProtocol("HTTP/1.1");
        result.setStatusCode(200);
        result.addHeader("!Connection", "close");
        result.setStatusMessage("ok");
        doTest(request, result);

    }

    public void testHttp10NoContentLength() throws Throwable {

        HttpRequestPacket request = HttpRequestPacket.builder()
                .method("GET")
                .uri("/path")
                .header("Host", "localhost:" + PORT)
                .protocol("HTTP/1.0")
                .build();
        ExpectedResult result = new ExpectedResult();
        result.setProtocol("HTTP/1.0");
        result.setStatusCode(200);
        result.addHeader("Connection", "close");
        result.setStatusMessage("ok");
        doTest(request, result, new BaseFilter() {
            @Override
            public NextAction handleRead(FilterChainContext ctx) throws IOException {
                HttpRequestPacket request =
                        (HttpRequestPacket)
                                ((HttpContent) ctx.getMessage()).getHttpHeader();
                HttpResponsePacket response = request.getResponse();
                HttpStatus.OK_200.setValues(response);
                MemoryManager mm = ctx.getConnection().getTransport().getMemoryManager();
                HttpContent content = response.httpContentBuilder().content(Buffers.wrap(mm, "Content")).build();
                content.setLast(true);        
                ctx.write(content);
                ctx.flush(new FlushAndCloseHandler());
                return ctx.getStopAction();
            }
        });
    }


    public void testHttp1NoContentLengthNoChunking() throws Throwable {

        final HttpRequestPacket request = HttpRequestPacket.builder()
                .method("GET")
                .uri("/path")
                .chunked(false)
                .header("Host", "localhost:" + PORT)
                .protocol("HTTP/1.1")
                .build();

        ExpectedResult result = new ExpectedResult();
        result.setProtocol("HTTP/1.1");
        result.setStatusCode(200);
        result.addHeader("Connection", "close");
        result.addHeader("!Transfer-Encoding", "chunked");
        result.setStatusMessage("ok");
        doTest(request, result, new BaseFilter() {
            @Override
            public NextAction handleRead(FilterChainContext ctx) throws IOException {
                HttpRequestPacket request =
                        (HttpRequestPacket)
                                ((HttpContent) ctx.getMessage()).getHttpHeader();
                HttpResponsePacket response = request.getResponse();
                HttpStatus.OK_200.setValues(response);
                MemoryManager mm = ctx.getConnection().getTransport().getMemoryManager();
                HttpContent content = response.httpContentBuilder().content(Buffers.wrap(mm, "Content")).build();
                content.setLast(true);
                ctx.write(content);
                ctx.flush(new FlushAndCloseHandler());
                return ctx.getStopAction();
            }
        });
    }

    // --------------------------------------------------------- Private Methods

    
    private void reportThreadErrors() throws Throwable {
        Throwable t = exception.getResult();
        if (t != null) {
            throw t;
        }
    }

    private void doTest(HttpPacket request, ExpectedResult expectedResults, Filter serverFilter)
    throws Throwable {
        final FutureImpl<Boolean> testResult = SafeFutureImpl.create();
        FilterChainBuilder filterChainBuilder = FilterChainBuilder.stateless();
        filterChainBuilder.add(new TransportFilter());
        filterChainBuilder.add(new ChunkingFilter(1024));
        filterChainBuilder.add(new HttpServerFilter(false, 8192, new KeepAlive(), null));
        filterChainBuilder.add(serverFilter);
        FilterChain filterChain = filterChainBuilder.build();

        TCPNIOTransport transport = NIOTransportBuilder.defaultTCPTransportBuilder().build();
        transport.setProcessor(filterChain);
        TCPNIOTransport ctransport = NIOTransportBuilder.defaultTCPTransportBuilder().build();
        try {
            transport.bind(PORT);
            transport.start();

            FilterChainBuilder clientFilterChainBuilder = FilterChainBuilder.stateless();
            clientFilterChainBuilder.add(new TransportFilter());
            clientFilterChainBuilder.add(new ChunkingFilter(1024));
            clientFilterChainBuilder.add(new HttpClientFilter());
            clientFilterChainBuilder.add(new ClientFilter(request,
                                                          testResult,
                                                          expectedResults));
            ctransport.setProcessor(clientFilterChainBuilder.build());

            ctransport.start();

            Future<Connection> connectFuture = ctransport.connect("localhost", PORT);
            Connection connection = null;
            try {
                connection = connectFuture.get(10, TimeUnit.SECONDS);
                testResult.get(10, TimeUnit.SECONDS);
            } finally {
                // Close the client connection
                if (connection != null) {
                    connection.close();
                }
            }
        } finally {
            transport.stop();
            ctransport.stop();
            reportThreadErrors();
        }
    }

    private void doTest(HttpPacket request, ExpectedResult expectedResults)
    throws Throwable {

        doTest(request, expectedResults, new SimpleResponseFilter());
        
    }


    private class ClientFilter extends BaseFilter {
        private final Logger logger = Grizzly.logger(ClientFilter.class);

        private HttpPacket request;
        private FutureImpl<Boolean> testResult;
        private ExpectedResult expectedResult;
        private boolean validated;
        private HttpContent currentContent;

        // -------------------------------------------------------- Constructors


        public ClientFilter(HttpPacket request,
                            FutureImpl<Boolean> testResult,
                            ExpectedResult expectedResults) {

            this.request = request;
            this.testResult = testResult;
            this.expectedResult = expectedResults;

        }


        // ------------------------------------------------ Methods from Filters


        @Override
        public NextAction handleConnect(FilterChainContext ctx)
              throws IOException {
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, "Connected... Sending the request: {0}", request);
            }

            ctx.write(request);

            return ctx.getStopAction();
        }


        @Override
        public NextAction handleRead(FilterChainContext ctx)
              throws IOException {

            final HttpContent httpContent = (HttpContent) ctx.getMessage();

            logger.log(Level.FINE, "Got HTTP response chunk");


            if (httpContent.isLast()) {
                validate(httpContent);
            } else {
                currentContent = httpContent;
            }

            return ctx.getStopAction();
        }

        private void validate(HttpContent httpContent) {
            if (validated) {
                return;
            }
            validated = true;
            try {
                HttpResponsePacket response =
                        (HttpResponsePacket) httpContent.getHttpHeader();
                if (expectedResult.getStatusCode() != -1) {
                    assertEquals(expectedResult.getStatusCode(),
                                 response.getStatus());
                }
                if (expectedResult.getProtocol() != null) {
                    assertEquals(expectedResult.getProtocol(),
                                 response.getProtocol().getProtocolString());
                }
                if (expectedResult.getStatusMessage() != null) {
                    assertEquals(expectedResult.getStatusMessage().toLowerCase(),
                                 response.getReasonPhrase().toLowerCase());
                }
                if (!expectedResult.getExpectedHeaders().isEmpty()) {
                    for (Map.Entry<String,String> entry : expectedResult.getExpectedHeaders().entrySet()) {
                        if (entry.getKey().charAt(0) != '!') {
                            assertTrue("Missing header: " + entry.getKey(),
                                       response.containsHeader(entry.getKey()));
                            assertEquals(entry.getValue().toLowerCase(),
                                         response.getHeader(entry.getKey()).toLowerCase());
                        } else {
                            assertFalse("Header should not be present: " + entry.getKey().substring(1),
                                       response.containsHeader(entry.getKey().substring(1)));
                        }
                    }
                }
                testResult.result(Boolean.TRUE);
            } catch (Throwable t) {
                testResult.result(Boolean.FALSE);
                exception.result(t);
            }
        }

        @Override
        public NextAction handleClose(FilterChainContext ctx)
              throws IOException {
            validate(currentContent);
            return ctx.getStopAction();
        }

    }


    private static final class SimpleResponseFilter extends BaseFilter {
        @Override
        public NextAction handleRead(FilterChainContext ctx) throws IOException {

            HttpRequestPacket request =
                    (HttpRequestPacket)
                            ((HttpContent) ctx.getMessage()).getHttpHeader();
            HttpResponsePacket response = request.getResponse();
            HttpStatus.OK_200.setValues(response);
            response.setContentLength(0);
            ctx.write(response);
            return ctx.getStopAction();
        }
    }


    private static final class ExpectedResult {

        private int statusCode = -1;
        private Map<String,String> expectedHeaders =
                new HashMap<String,String>();
        private String protocol;
        private String statusMessage;

        public int getStatusCode() {
            return statusCode;
        }

        public void setStatusCode(int statusCode) {
            this.statusCode = statusCode;
        }

        public void addHeader(String name, String value) {
            expectedHeaders.put(name, value);
        }

        public Map<String, String> getExpectedHeaders() {
            return Collections.unmodifiableMap(expectedHeaders);
        }

        public String getProtocol() {
            return protocol;
        }

        public void setProtocol(String protocol) {
            this.protocol = protocol;
        }

        public String getStatusMessage() {
            return statusMessage;
        }

        public void setStatusMessage(String statusMessage) {
            this.statusMessage = statusMessage;
        }
    }

    private static class FlushAndCloseHandler extends EmptyCompletionHandler {
        @Override
        public void completed(Object result) {
            final WriteResult wr = (WriteResult) result;
            try {
                wr.getConnection().close().markForRecycle(false);
            } catch (IOException ignore) {
            } finally {
                wr.recycle();
            }
        }
    }
}
