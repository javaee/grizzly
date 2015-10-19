/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011-2015 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.http;

import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.FilterChain;
import org.glassfish.grizzly.filterchain.FilterChainBuilder;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.filterchain.TransportFilter;
import org.glassfish.grizzly.utils.Charsets;
import org.glassfish.grizzly.http.util.DataChunk;
import org.glassfish.grizzly.http.util.HttpStatus;
import org.glassfish.grizzly.impl.FutureImpl;
import org.glassfish.grizzly.impl.SafeFutureImpl;
import org.glassfish.grizzly.memory.MemoryManager;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import org.glassfish.grizzly.nio.transport.TCPNIOTransportBuilder;
import org.glassfish.grizzly.utils.ChunkingFilter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import junit.framework.TestCase;
import org.glassfish.grizzly.filterchain.Filter;
import org.glassfish.grizzly.memory.Buffers;

/**
 *
 * @author Alexey Stashok
 */
public class CompressionSemanticsTest extends TestCase {
    private static final Logger logger = Grizzly.logger(CompressionSemanticsTest.class);

    public static final int PORT = 19005;

    private final FutureImpl<Throwable> exception = SafeFutureImpl.create();

    public void testImplicitContentLength() throws Throwable {
        ContentEncoding gzipServerContentEncoding =
                getGzipServerContentEncoding();

        ContentEncoding gzipClientContentEncoding =
                getGzipClientContentEncoding();

        HttpRequestPacket request = HttpRequestPacket.builder()
            .method("GET")
            .header("Host", "localhost:" + PORT)
            .uri("/path")
            .header("accept-encoding", "gzip")
            .protocol(Protocol.HTTP_1_1)
            .build();

        ExpectedResult result = new ExpectedResult();
        result.setProtocol("HTTP/1.1");
        result.setStatusCode(200);
        result.addHeader("content-encoding", "gzip");

        final MemoryManager mm = MemoryManager.DEFAULT_MEMORY_MANAGER;
        result.setContent(Buffers.wrap(mm, "Echo: <nothing>"));
        result.addHeader("Content-Length", "35");
        result.addHeader("!Transfer-Encoding", "chunked");
        
        doTest(request, result,
                Collections.singletonList(gzipServerContentEncoding),
                Collections.singletonList(gzipClientContentEncoding),
                new BaseFilter() {

            @Override
            public NextAction handleRead(FilterChainContext ctx) throws IOException {
                final HttpContent httpContent = ctx.getMessage();

                if (httpContent.isLast()) {
                    final HttpRequestPacket request = (HttpRequestPacket) httpContent.getHttpHeader();

                    final HttpResponsePacket response = request.getResponse();
                    HttpStatus.OK_200.setValues(response);

                    final Buffer requestContent = httpContent.getContent();

                    final StringBuilder sb = new StringBuilder("Echo: ").append(requestContent.hasRemaining()
                            ? requestContent.toStringContent()
                            : "<nothing>");

                    final HttpContent responseContent =
                            HttpContent.builder(response)
                            .last(true)
                            .content(Buffers.wrap(ctx.getMemoryManager(), sb.toString()))
                            .build();

                    ctx.write(responseContent);
                    return ctx.getStopAction();
                }

                return ctx.getStopAction(httpContent);
            }
        });
    }
    
    public void testImplicitChunking() throws Throwable {
        ContentEncoding gzipServerContentEncoding =
                getGzipServerContentEncoding();

        ContentEncoding gzipClientContentEncoding =
                getGzipClientContentEncoding();

        HttpRequestPacket request = HttpRequestPacket.builder()
            .method("GET")
            .header("Host", "localhost:" + PORT)
            .uri("/path")
            .header("accept-encoding", "gzip")
            .protocol(Protocol.HTTP_1_1)
            .build();

        ExpectedResult result = new ExpectedResult();
        result.setProtocol("HTTP/1.1");
        result.setStatusCode(200);
        result.addHeader("content-encoding", "gzip");

        final MemoryManager mm = MemoryManager.DEFAULT_MEMORY_MANAGER;
        result.setContent(Buffers.wrap(mm, "Echo: <nothing>"));
        result.addHeader("Transfer-Encoding", "chunked");
        result.addHeader("!Content-Length", "35");
        
        doTest(request, result,
                Collections.singletonList(gzipServerContentEncoding),
                Collections.singletonList(gzipClientContentEncoding),
                new BaseFilter() {

            @Override
            public NextAction handleRead(FilterChainContext ctx) throws IOException {
                final HttpContent httpContent = ctx.getMessage();

                if (httpContent.isLast()) {
                    final HttpRequestPacket request = (HttpRequestPacket) httpContent.getHttpHeader();

                    final HttpResponsePacket response = request.getResponse();
                    HttpStatus.OK_200.setValues(response);

                    final Buffer requestContent = httpContent.getContent();

                    final StringBuilder sb = new StringBuilder("Echo: ").append(requestContent.hasRemaining()
                            ? requestContent.toStringContent()
                            : "<nothing>");
                    final String content1 = sb.substring(0, sb.length() / 2);
                    final String content2 = sb.substring(sb.length() / 2, sb.length());

                    final HttpContent responseContent1 =
                            HttpContent.builder(response)
                            .last(false)
                            .content(Buffers.wrap(ctx.getMemoryManager(), content1))
                            .build();

                    final HttpContent responseContent2 =
                            HttpContent.builder(response)
                            .last(true)
                            .content(Buffers.wrap(ctx.getMemoryManager(), content2))
                            .build();

                    ctx.write(responseContent1);
                    ctx.write(responseContent2);
                    return ctx.getStopAction();
                }

                return ctx.getStopAction(httpContent);
            }
        });        
    }
    
    public void testMultipleEncodings() throws Throwable {
        ContentEncoding gzipServerContentEncoding =
                getGzipServerContentEncoding();
        ContentEncoding gzipClientContentEncoding =
                getGzipClientContentEncoding();

        ContentEncoding lzmaServerContentEncoding =
                getLzmaServerContentEncoding();
        ContentEncoding lzmaClientContentEncoding =
                getLzmaClientContentEncoding();

        HttpRequestPacket request = HttpRequestPacket.builder()
            .method("GET")
            .header("Host", "localhost:" + PORT)
            .uri("/path")
            .header("accept-encoding", "gzip, lzma")
            .protocol(Protocol.HTTP_1_1)
            .build();

        ExpectedResult result = new ExpectedResult();
        result.setProtocol("HTTP/1.1");
        result.setStatusCode(200);
        result.addHeader("content-encoding", "gzip,lzma");

        final MemoryManager mm = MemoryManager.DEFAULT_MEMORY_MANAGER;
        result.setContent(Buffers.wrap(mm, "Echo: <nothing>"));
        result.addHeader("Transfer-Encoding", "chunked");
        result.addHeader("!Content-Length", "35");
        
        doTest(request, result,
                Arrays.asList(gzipServerContentEncoding, lzmaServerContentEncoding),
                Arrays.asList(gzipClientContentEncoding, lzmaClientContentEncoding),
                new BaseFilter() {

            @Override
            public NextAction handleRead(FilterChainContext ctx) throws IOException {
                final HttpContent httpContent = ctx.getMessage();

                if (httpContent.isLast()) {
                    final HttpRequestPacket request = (HttpRequestPacket) httpContent.getHttpHeader();

                    final HttpResponsePacket response = request.getResponse();
                    HttpStatus.OK_200.setValues(response);

                    final Buffer requestContent = httpContent.getContent();

                    final StringBuilder sb = new StringBuilder("Echo: ").append(requestContent.hasRemaining()
                            ? requestContent.toStringContent()
                            : "<nothing>");
                    final String content1 = sb.substring(0, sb.length() / 2);
                    final String content2 = sb.substring(sb.length() / 2, sb.length());

                    final HttpContent responseContent1 =
                            HttpContent.builder(response)
                            .last(false)
                            .content(Buffers.wrap(ctx.getMemoryManager(), content1))
                            .build();

                    final HttpContent responseContent2 =
                            HttpContent.builder(response)
                            .last(true)
                            .content(Buffers.wrap(ctx.getMemoryManager(), content2))
                            .build();

                    ctx.write(responseContent1);
                    ctx.write(responseContent2);
                    return ctx.getStopAction();
                }

                return ctx.getStopAction(httpContent);
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

    private void doTest(HttpPacket request, ExpectedResult expectedResults,
            List<ContentEncoding> serverContentEncoding,
            List<ContentEncoding> clientContentEncoding,
            Filter serverFilter)
    throws Throwable {

        final FutureImpl<Boolean> testResult = SafeFutureImpl.create();
        FilterChainBuilder filterChainBuilder = FilterChainBuilder.stateless();
        filterChainBuilder.add(new TransportFilter());
        filterChainBuilder.add(new ChunkingFilter(2));

        final HttpServerFilter httpServerFilter = new HttpServerFilter();
        if (serverContentEncoding != null) {
            for (ContentEncoding ce : serverContentEncoding) {
                httpServerFilter.addContentEncoding(ce);
            }
        }
        filterChainBuilder.add(httpServerFilter);

        filterChainBuilder.add(serverFilter);
        FilterChain filterChain = filterChainBuilder.build();

        TCPNIOTransport transport = TCPNIOTransportBuilder.newInstance().build();
        transport.setProcessor(filterChain);

        TCPNIOTransport ctransport = TCPNIOTransportBuilder.newInstance().build();
        try {
            transport.bind(PORT);
            transport.start();

            FilterChainBuilder clientFilterChainBuilder = FilterChainBuilder.stateless();
            clientFilterChainBuilder.add(new TransportFilter());
            clientFilterChainBuilder.add(new ChunkingFilter(2));

            final HttpClientFilter httpClientFilter = new HttpClientFilter();
            if (clientContentEncoding != null) {
                for (ContentEncoding ce : clientContentEncoding) {
                    httpClientFilter.addContentEncoding(ce);
                }
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
                    connection.closeSilently();
                }
            }
        } finally {
            transport.shutdownNow();
            ctransport.shutdownNow();
            reportThreadErrors();
        }
    }


    private class ClientFilter extends BaseFilter {
        private final Logger logger = Grizzly.logger(ClientFilter.class);

        private final HttpPacket request;
        private final FutureImpl<Boolean> testResult;
        private final ExpectedResult expectedResult;

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
                logger.log(Level.FINE, "Connected... Sending the request: {0}",
                        request);
            }

            ctx.write(request);

            return ctx.getStopAction();
        }


        @Override
        public NextAction handleRead(FilterChainContext ctx)
              throws IOException {

            final HttpContent httpContent = ctx.getMessage();

            logger.log(Level.FINE, "Got HTTP response chunk; last: {0}", httpContent.isLast());


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

                    if (expectedResult.getContent() != null) {
                        assertEquals("Unexpected content",
                                     expectedResult.getContent().toStringContent(Charsets.UTF8_CHARSET),
                                     httpContent.getContent().toStringContent(Charsets.UTF8_CHARSET));
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


    private static final class ExpectedResult {

        private int statusCode = -1;
        private final Map<String,String> expectedHeaders =
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
    
    private ContentEncoding getGzipClientContentEncoding() {
        return new GZipContentEncoding(512, 512, new EncodingFilter() {
           @Override
           public boolean applyEncoding(HttpHeader httpPacket) {
               return false;
           }

           @Override
           public boolean applyDecoding(HttpHeader httpPacket) {
               return true;
           }
       });
    }

    private ContentEncoding getGzipServerContentEncoding() {
        return new GZipContentEncoding(512, 512, new EncodingFilter() {
           @Override
           public boolean applyEncoding(HttpHeader httpPacket) {
               final HttpResponsePacket httpResponse = (HttpResponsePacket) httpPacket;
               final HttpRequestPacket httpRequest = httpResponse.getRequest();

               final DataChunk bc = httpRequest.getHeaders().getValue("accept-encoding");

               return bc != null && bc.indexOf("gzip", 0) != -1;
           }

           @Override
           public boolean applyDecoding(HttpHeader httpPacket) {
               return false;
           }
       });
    }
    
    private ContentEncoding getLzmaClientContentEncoding() {
        return new LZMAContentEncoding(new EncodingFilter() {
           @Override
           public boolean applyEncoding(HttpHeader httpPacket) {
               return false;
           }

           @Override
           public boolean applyDecoding(HttpHeader httpPacket) {
               return true;
           }
       });
    }

    private ContentEncoding getLzmaServerContentEncoding() {
        return new LZMAContentEncoding(new EncodingFilter() {
           @Override
           public boolean applyEncoding(HttpHeader httpPacket) {
               final HttpResponsePacket httpResponse = (HttpResponsePacket) httpPacket;
               final HttpRequestPacket httpRequest = httpResponse.getRequest();

               final DataChunk bc = httpRequest.getHeaders().getValue("accept-encoding");

               return bc != null && bc.indexOf("lzma", 0) != -1;
           }

           @Override
           public boolean applyDecoding(HttpHeader httpPacket) {
               return false;
           }
       });
    }
}
