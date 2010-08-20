/*
 *
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
 *
 */

package com.sun.grizzly.http.core;

import com.sun.grizzly.Buffer;
import com.sun.grizzly.Connection;
import com.sun.grizzly.Grizzly;
import com.sun.grizzly.TransportFactory;
import com.sun.grizzly.filterchain.BaseFilter;
import com.sun.grizzly.filterchain.FilterChain;
import com.sun.grizzly.filterchain.FilterChainBuilder;
import com.sun.grizzly.filterchain.FilterChainContext;
import com.sun.grizzly.filterchain.NextAction;
import com.sun.grizzly.filterchain.TransportFilter;
import com.sun.grizzly.http.ContentEncoding;
import com.sun.grizzly.http.EncodingFilter;
import com.sun.grizzly.http.GZipContentEncoding;
import com.sun.grizzly.http.HttpClientFilter;
import com.sun.grizzly.http.HttpContent;
import com.sun.grizzly.http.HttpHeader;
import com.sun.grizzly.http.HttpPacket;
import com.sun.grizzly.http.HttpRequestPacket;
import com.sun.grizzly.http.HttpResponsePacket;
import com.sun.grizzly.http.HttpServerFilter;
import com.sun.grizzly.http.util.BufferChunk;
import com.sun.grizzly.http.util.HttpStatus;
import com.sun.grizzly.impl.FutureImpl;
import com.sun.grizzly.impl.SafeFutureImpl;
import com.sun.grizzly.memory.MemoryManager;
import com.sun.grizzly.memory.MemoryUtils;
import com.sun.grizzly.nio.transport.TCPNIOTransport;
import com.sun.grizzly.utils.ChunkingFilter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPOutputStream;
import junit.framework.TestCase;

/**
 *
 * @author Alexey Stashok
 */
public class GZipEncodingTest extends TestCase {
    private static final Logger logger = Grizzly.logger(GZipEncodingTest.class);

    public static int PORT = 8005;

    private final FutureImpl<Throwable> exception = SafeFutureImpl.create();

    public void testGZipResponse() throws Throwable {
        GZipContentEncoding gzipServerContentEncoding =
                new GZipContentEncoding(512, 512, new EncodingFilter() {
            @Override
            public boolean applyEncoding(HttpHeader httpPacket) {
                final HttpResponsePacket httpResponse = (HttpResponsePacket) httpPacket;
                final HttpRequestPacket httpRequest = httpResponse.getRequest();

                final BufferChunk bc = httpRequest.getHeaders().getValue("accept-encoding");

                return bc != null && bc.indexOf("gzip", 0) != -1;
            }
        });

        HttpRequestPacket request = HttpRequestPacket.builder()
            .method("GET")
            .header("Host", "localhost:" + PORT)
            .uri("/path")
            .header("accept-encoding", "gzip")
            .protocol("HTTP/1.1")
            .build();

        ExpectedResult result = new ExpectedResult();
        result.setProtocol("HTTP/1.1");
        result.setStatusCode(200);
        result.addHeader("content-encoding", "gzip");

        final MemoryManager mm = TransportFactory.getInstance().getDefaultMemoryManager();
        result.setContent(MemoryUtils.wrap(mm, "Echo: <nothing>"));
        doTest(request, result, gzipServerContentEncoding, null);
    }


    public void testGZipRequest() throws Throwable {
        final MemoryManager mm = TransportFactory.getInstance().getDefaultMemoryManager();

        String reqString = "GZipped hello. Works?";
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        GZIPOutputStream go = new GZIPOutputStream(baos);
        go.write(reqString.getBytes());
        go.finish();
        go.close();

        byte[] gzippedContent = baos.toByteArray();

        HttpRequestPacket request = HttpRequestPacket.builder()
            .method("POST")
            .header("Host", "localhost:" + PORT)
            .uri("/path")
            .protocol("HTTP/1.1")
            .header("content-encoding", "gzip")
            .contentLength(gzippedContent.length)
            .build();
        
        HttpContent reqHttpContent = HttpContent.builder(request)
                .last(true)
                .content(MemoryUtils.wrap(mm, gzippedContent))
                .build();

        ExpectedResult result = new ExpectedResult();
        result.setProtocol("HTTP/1.1");
        result.setStatusCode(200);
        result.addHeader("!content-encoding", "gzip");
        result.setContent(MemoryUtils.wrap(mm, "Echo: " + reqString));

        doTest(reqHttpContent, result, null, null);
    }


    public void testGZipRequestResponse() throws Throwable {
        GZipContentEncoding gzipServerContentEncoding =
                new GZipContentEncoding(512, 512, new EncodingFilter() {
            @Override
            public boolean applyEncoding(HttpHeader httpPacket) {
                final HttpResponsePacket httpResponse = (HttpResponsePacket) httpPacket;
                final HttpRequestPacket httpRequest = httpResponse.getRequest();

                final BufferChunk bc = httpRequest.getHeaders().getValue("accept-encoding");

                return bc != null && bc.indexOf("gzip", 0) != -1;
            }
        });

        final MemoryManager mm = TransportFactory.getInstance().getDefaultMemoryManager();

        String reqString = generateBigString(16384);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        GZIPOutputStream go = new GZIPOutputStream(baos);
        go.write(reqString.getBytes());
        go.finish();
        go.close();

        byte[] gzippedContent = baos.toByteArray();

        HttpRequestPacket request = HttpRequestPacket.builder()
            .method("POST")
            .header("Host", "localhost:" + PORT)
            .uri("/path")
            .protocol("HTTP/1.1")
            .header("accept-encoding", "gzip")
            .header("content-encoding", "gzip")
            .contentLength(gzippedContent.length)
            .build();

        HttpContent reqHttpContent = HttpContent.builder(request)
                .last(true)
                .content(MemoryUtils.wrap(mm, gzippedContent))
                .build();

        ExpectedResult result = new ExpectedResult();
        result.setProtocol("HTTP/1.1");
        result.setStatusCode(200);
        result.addHeader("content-encoding", "gzip");
        result.setContent(MemoryUtils.wrap(mm, "Echo: " + reqString));

        doTest(reqHttpContent, result, gzipServerContentEncoding, null);
    }

    public void testGZipRequestResponseChunking() throws Throwable {
        GZipContentEncoding gzipServerContentEncoding =
                new GZipContentEncoding(512, 512, new EncodingFilter() {
            @Override
            public boolean applyEncoding(HttpHeader httpPacket) {
                final HttpResponsePacket httpResponse = (HttpResponsePacket) httpPacket;
                final HttpRequestPacket httpRequest = httpResponse.getRequest();

                final BufferChunk bc = httpRequest.getHeaders().getValue("accept-encoding");

                return bc != null && bc.indexOf("gzip", 0) != -1;
            }
        });

        final MemoryManager mm = TransportFactory.getInstance().getDefaultMemoryManager();

        String reqString = generateBigString(16384);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        GZIPOutputStream go = new GZIPOutputStream(baos);
        go.write(reqString.getBytes());
        go.finish();
        go.close();

        byte[] gzippedContent = baos.toByteArray();

        HttpRequestPacket request = HttpRequestPacket.builder()
                .method("POST")
                .header("Host", "localhost:" + PORT)
                .uri("/path")
                .protocol("HTTP/1.1")
                .header("accept-encoding", "gzip")
                .header("content-encoding", "gzip")
                .chunked(true)
                .build();

        HttpContent reqHttpContent = HttpContent.builder(request)
                .last(true)
                .content(MemoryUtils.wrap(mm, gzippedContent))
                .build();

        ExpectedResult result = new ExpectedResult();
        result.setProtocol("HTTP/1.1");
        result.setStatusCode(200);
        result.addHeader("content-encoding", "gzip");
        result.setContent(MemoryUtils.wrap(mm, "Echo: " + reqString));

        doTest(reqHttpContent, result, gzipServerContentEncoding, null);
    }
    
    // --------------------------------------------------------- Private Methods


    private void reportThreadErrors() throws Throwable {
        Throwable t = exception.getResult();
        if (t != null) {
            throw t;
        }
    }

    private void doTest(HttpPacket request, ExpectedResult expectedResults,
            ContentEncoding serverContentEncoding, ContentEncoding clientContentEncoding)
    throws Throwable {

        final FutureImpl<Boolean> testResult = SafeFutureImpl.create();
        FilterChainBuilder filterChainBuilder = FilterChainBuilder.stateless();
        filterChainBuilder.add(new TransportFilter());
        filterChainBuilder.add(new ChunkingFilter(2));

        final HttpServerFilter httpServerFilter = new HttpServerFilter();
        if (serverContentEncoding != null) {
            httpServerFilter.addContentEncoding(serverContentEncoding);
        }
        filterChainBuilder.add(httpServerFilter);

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
            clientFilterChainBuilder.add(new ChunkingFilter(2));

            final HttpClientFilter httpClientFilter = new HttpClientFilter();
            if (clientContentEncoding != null) {
                httpClientFilter.addContentEncoding(clientContentEncoding);
            }
            clientFilterChainBuilder.add(httpClientFilter);

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
            TransportFactory.getInstance().close();
            reportThreadErrors();
        }
    }


    private class ClientFilter extends BaseFilter {
        private final Logger logger = Grizzly.logger(ClientFilter.class);

        private HttpPacket request;
        private FutureImpl<Boolean> testResult;
        private ExpectedResult expectedResult;

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
                    if (expectedResult.getStatusCode() != -1) {
                        assertEquals(expectedResult.getStatusCode(),
                                     response.getStatus());
                    }
                    if (expectedResult.getProtocol() != null) {
                        assertEquals(expectedResult.getProtocol(),
                                     response.getProtocol());
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

                    if (expectedResult.getContent() != null) {
                        assertEquals("Unexpected content", expectedResult.getContent(), httpContent.getContent());
                    }
                    
                    testResult.result(Boolean.TRUE);
                } catch (Throwable t) {
                    testResult.result(Boolean.FALSE);
                    exception.result(t);
                }
            }

            return ctx.getStopAction(httpContent);
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
            final HttpContent httpContent = (HttpContent) ctx.getMessage();

            if (httpContent.isLast()) {
                final HttpRequestPacket request = (HttpRequestPacket) httpContent.getHttpHeader();

                final HttpResponsePacket response = request.getResponse();
                HttpStatus.OK_200.setValues(response);
                response.setChunked(true);

                final Buffer requestContent = httpContent.getContent();

                final StringBuilder sb = new StringBuilder("Echo: ")
                        .append(requestContent.hasRemaining() ?
                            requestContent.toStringContent() :
                            "<nothing>");
                final MemoryManager mm = ctx.getConnection().getTransport().getMemoryManager();

                final HttpContent responseContent = HttpContent.builder(response)
                        .last(true)
                        .content(MemoryUtils.wrap(mm, sb.toString()))
                        .build();

                ctx.write(responseContent);
                return ctx.getStopAction();
            }

            return ctx.getStopAction(httpContent);
        }
    }

    private String generateBigString(int size) {
        final Random r = new Random();
        
        StringBuilder sb = new StringBuilder(size);
        for (int i = 0; i < size; i++) {
            sb.append((char) ('A' + r.nextInt('Z' - 'A')));
        }

        return sb.toString();
    }

    private static final class ExpectedResult {

        private int statusCode = -1;
        private Map<String,String> expectedHeaders =
                new HashMap<String,String>();
        private String protocol;
        private String statusMessage;
        private Buffer content;

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

        public Buffer getContent() {
            return content;
        }

        public void setContent(Buffer content) {
            this.content = content;
        }
    }
}
