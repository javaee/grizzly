/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2010 Sun Microsystems, Inc. All rights reserved.
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
 */

package com.sun.grizzly.http.core;

import com.sun.grizzly.Connection;
import com.sun.grizzly.Grizzly;
import com.sun.grizzly.TransportFactory;
import com.sun.grizzly.filterchain.BaseFilter;
import com.sun.grizzly.filterchain.FilterChain;
import com.sun.grizzly.filterchain.FilterChainBuilder;
import com.sun.grizzly.filterchain.FilterChainContext;
import com.sun.grizzly.filterchain.NextAction;
import com.sun.grizzly.filterchain.TransportFilter;
import com.sun.grizzly.http.HttpClientFilter;
import com.sun.grizzly.http.HttpContent;
import com.sun.grizzly.http.HttpPacket;
import com.sun.grizzly.http.HttpRequestPacket;
import com.sun.grizzly.http.HttpResponsePacket;
import com.sun.grizzly.http.HttpServerFilter;
import com.sun.grizzly.impl.FutureImpl;
import com.sun.grizzly.impl.SafeFutureImpl;
import com.sun.grizzly.nio.transport.TCPNIOTransport;
import com.sun.grizzly.utils.ChunkingFilter;
import junit.framework.TestCase;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * Test cases to validate HTTP protocol semantics.
 */
public class HttpSemanticsTest extends TestCase {

    public static final int PORT = 8003;
    private final FutureImpl<Throwable> exception = SafeFutureImpl.create();


    // ------------------------------------------------------------ Test Methods


    public void testUnsupportedProtocol() throws Throwable {

        HttpRequestPacket request = HttpRequestPacket.create();
        request.setMethod("GET");
        request.setHeader("Host", "localhost:" + PORT);
        request.setRequestURI("/path");
        request.setProtocol("HTTP/1.2");

        ExpectedResults results = new ExpectedResults();
        results.setProtocol("HTTP/1.1");
        results.setStatusCode(505);
        results.addHeader("Connection", "close");
        results.setStatusMessage("unsupported protocol version");
        doTest(request, results);
 
    }


    public void testHttp11NoHostHeader() throws Throwable {

        HttpRequestPacket request = HttpRequestPacket.create();
        request.setMethod("GET");
        request.setRequestURI("/path");
        request.setProtocol("HTTP/1.1");

        ExpectedResults results = new ExpectedResults();
        results.setProtocol("HTTP/1.1");
        results.setStatusCode(400);
        results.addHeader("Connection", "close");
        results.setStatusMessage("bad request");
        doTest(request, results);

    }


    public void testHttp09ConnectionCloseTest() throws Throwable {

        HttpRequestPacket request = HttpRequestPacket.create();
        request.setMethod("GET");
        request.setRequestURI("/path");
        request.setProtocol("HTTP/0.9");

        ExpectedResults results = new ExpectedResults();
        results.setProtocol("HTTP/1.1");
        results.setStatusCode(200);
        results.addHeader("!Connection", "close");
        results.setStatusMessage("ok");
        doTest(request, results);
        
    }


    public void testHttp10ConnectionCloseNoConnectionRequestHeaderTest() throws Throwable {

        HttpRequestPacket request = HttpRequestPacket.create();
        request.setMethod("GET");
        request.setRequestURI("/path");
        request.setProtocol("HTTP/1.0");

        ExpectedResults results = new ExpectedResults();
        results.setProtocol("HTTP/1.1");
        results.setStatusCode(200);
        results.addHeader("Connection", "close");
        results.setStatusMessage("ok");
        doTest(request, results);
        
    }


    public void testHttp11RequestCloseTest() throws Throwable {

        HttpRequestPacket request = HttpRequestPacket.create();
        request.setMethod("GET");
        request.setRequestURI("/path");
        request.setProtocol("HTTP/1.1");
        request.addHeader("Host", "localhost:" + PORT);
        request.addHeader("Connection", "close");

        ExpectedResults results = new ExpectedResults();
        results.setProtocol("HTTP/1.1");
        results.setStatusCode(200);
        results.addHeader("Connection", "close");
        results.setStatusMessage("ok");
        doTest(request, results);

    }

    public void testHttp11NoExplicitRequestCloseTest() throws Throwable {

        HttpRequestPacket request = HttpRequestPacket.create();
        request.setMethod("GET");
        request.setRequestURI("/path");
        request.addHeader("Host", "localhost:" + PORT);
        request.setProtocol("HTTP/1.1");

        ExpectedResults results = new ExpectedResults();
        results.setProtocol("HTTP/1.1");
        results.setStatusCode(200);
        results.addHeader("!Connection", "close");
        results.setStatusMessage("ok");
        doTest(request, results);

    }

    // --------------------------------------------------------- Private Methods

    
    private void reportThreadErrors() throws Throwable {
        Throwable t = exception.getResult();
        if (t != null) {
            throw t;
        }
    }

    private void doTest(HttpPacket request, ExpectedResults expectedResults)
    throws Throwable {

        final FutureImpl<Boolean> testResult = SafeFutureImpl.create();
        FilterChainBuilder filterChainBuilder = FilterChainBuilder.stateless();
        filterChainBuilder.add(new TransportFilter());
        filterChainBuilder.add(new ChunkingFilter(1024));
        filterChainBuilder.add(new HttpServerFilter());
        filterChainBuilder.add(new SimpleResponseFilter());
        FilterChain filterChain = filterChainBuilder.build();

        TCPNIOTransport transport = TransportFactory.getInstance().createTCPTransport();
        transport.setProcessor(filterChain);
        TCPNIOTransport ctransport = TransportFactory.getInstance().createTCPTransport();
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
                testResult.get();
            } finally {
                // Close the client connection
                if (connection != null) {
                    connection.close();
                }
            }
        } finally {
            transport.stop();
            ctransport.stop();
            TransportFactory.getInstance().close();
            reportThreadErrors();
        }
    }


    private class ClientFilter extends BaseFilter {
        private final Logger logger = Grizzly.logger(ClientFilter.class);

        private HttpPacket request;
        private FutureImpl<Boolean> testResult;
        private ExpectedResults expectedResults;

        // -------------------------------------------------------- Constructors


        public ClientFilter(HttpPacket request,
                            FutureImpl<Boolean> testResult,
                            ExpectedResults expectedResults) {

            this.request = request;
            this.testResult = testResult;
            this.expectedResults = expectedResults;

        }


        // ------------------------------------------------ Methods from Filters


        @Override
        public NextAction handleConnect(FilterChainContext ctx)
              throws IOException {
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE,
                           "Connected... Sending the request: " + request);
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
                try {
                    HttpResponsePacket response =
                            (HttpResponsePacket) httpContent.getHttpHeader();
                    if (expectedResults.getStatusCode() != -1) {
                        assertEquals(expectedResults.getStatusCode(),
                                     response.getStatus());
                    }
                    if (expectedResults.getProtocol() != null) {
                        assertEquals(expectedResults.getProtocol(),
                                     response.getProtocol());
                    }
                    if (expectedResults.getStatusMessage() != null) {
                        assertEquals(expectedResults.getStatusMessage().toLowerCase(),
                                     response.getReasonPhrase().toLowerCase());
                    }
                    if (!expectedResults.getExpectedHeaders().isEmpty()) {
                        for (Map.Entry<String,String> entry : expectedResults.getExpectedHeaders().entrySet()) {
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

            return ctx.getStopAction();
        }

        @Override
        public NextAction handleClose(FilterChainContext ctx)
              throws IOException {
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
            response.setStatus(200);
            response.setReasonPhrase("OK");
            ctx.write(response);
            return ctx.getStopAction();
        }
    }


    private static final class ExpectedResults {

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


}
