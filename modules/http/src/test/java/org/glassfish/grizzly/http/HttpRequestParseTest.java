/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2016 Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import junit.framework.TestCase;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.SocketConnectorHandler;
import org.glassfish.grizzly.WriteHandler;
import org.glassfish.grizzly.WriteResult;
import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.FilterChainBuilder;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.filterchain.TransportFilter;
import org.glassfish.grizzly.http.util.MimeHeaders;
import org.glassfish.grizzly.impl.FutureImpl;
import org.glassfish.grizzly.impl.SafeFutureImpl;
import org.glassfish.grizzly.memory.Buffers;
import org.glassfish.grizzly.memory.MemoryManager;
import org.glassfish.grizzly.nio.NIOConnection;
import org.glassfish.grizzly.nio.transport.TCPNIOConnectorHandler;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import org.glassfish.grizzly.nio.transport.TCPNIOTransportBuilder;
import org.glassfish.grizzly.utils.ChunkingFilter;
import org.glassfish.grizzly.utils.Pair;

/**
 * Testing HTTP request parsing
 * 
 * @author Alexey Stashok
 */
public class HttpRequestParseTest extends TestCase {

    public static final int PORT = 19000;

    public void testCustomMethod() throws Exception {
        doHttpRequestTest("TAKE", "/index.html", "HTTP/1.0", Collections.<String, Pair<String, String>>emptyMap(), "\r\n");
    }

    public void testHeaderlessRequestLine() throws Exception {
        doHttpRequestTest("GET", "/index.html", "HTTP/1.0", Collections.<String, Pair<String, String>>emptyMap(), "\r\n");
    }

    public void testSimpleHeaders() throws Exception {
        Map<String, Pair<String, String>> headers =
                new HashMap<String, Pair<String, String>>();
        headers.put("Host", new Pair<String,String>("localhost", "localhost"));
        headers.put("Content-length", new Pair<String,String>("2345", "2345"));
        doHttpRequestTest("POST", "/index.html", "HTTP/1.1", headers, "\r\n");
    }

    public void testSimpleHeadersPreserveCase() throws Exception {
        Map<String, Pair<String, String>> headers =
                new HashMap<String, Pair<String, String>>();
        headers.put("Host", new Pair<String,String>("localhost", "localhost"));
        headers.put("Content-length", new Pair<String,String>("2345", "2345"));
        doHttpRequestTest("POST", "/index.html", "HTTP/1.1", headers, "\r\n", true);
    }

    public void testMultiLineHeaders() throws Exception {
        Map<String, Pair<String, String>> headers =
                new HashMap<String, Pair<String, String>>();
        headers.put("Host", new Pair<String,String>("localhost", "localhost"));
        headers.put("Multi-line", new Pair<String,String>("first\r\n          second\r\n       third", "first second third"));
        headers.put("Content-length", new Pair<String,String>("2345", "2345"));
        doHttpRequestTest("POST", "/index.html", "HTTP/1.1", headers, "\r\n");
    }

    public void testHeadersN() throws Exception {
        Map<String, Pair<String, String>> headers =
                new HashMap<String, Pair<String, String>>();
        headers.put("Host", new Pair<String,String>("localhost", "localhost"));
        headers.put("Multi-line", new Pair<String,String>("first\r\n          second\n       third", "first second third"));
        headers.put("Content-length", new Pair<String,String>("2345", "2345"));
        doHttpRequestTest("POST", "/index.html", "HTTP/1.1", headers, "\n");
    }

    public void testCompleteURI() throws Exception {
        Map<String, Pair<String, String>> headers =
                new HashMap<String, Pair<String, String>>();
        headers.put("Host", new Pair<String,String>(null, "localhost:8180"));
        headers.put("Content-length", new Pair<String,String>("2345", "2345"));
        doHttpRequestTest(new Pair<String, String>("POST", "POST"),
                new Pair<String, String>("http://localhost:8180/index.html", "/index.html"),
                new Pair<String,String>("HTTP/1.1", "HTTP/1.1"), headers, "\n", false);
    }

    public void testCompleteEmptyURI() throws Exception {
        Map<String, Pair<String, String>> headers =
                new HashMap<String, Pair<String, String>>();
        headers.put("Host", new Pair<String,String>(null, "localhost:8180"));
        headers.put("Content-length", new Pair<String,String>("2345", "2345"));
        doHttpRequestTest(new Pair<String, String>("POST", "POST"),
                new Pair<String, String>("http://localhost:8180", "/"),
                new Pair<String,String>("HTTP/1.1", "HTTP/1.1"), headers, "\n", false);
    }

    public void testDecoderOK() {
        doTestDecoder("GET /index.html HTTP/1.0\n\n", 4096);
    }

    public void testDecoderOverflowMethod() {
        try {
            doTestDecoder("GET /index.html HTTP/1.0\n\n", 2);
            fail("Overflow exception had to be thrown");
        } catch (IllegalStateException e) {
            // expected
        }
    }

    public void testDecoderOverflowURI() {
        try {
            doTestDecoder("GET /index.html HTTP/1.0\n\n", 8);
            fail("Overflow exception had to be thrown");
        } catch (IllegalStateException e) {
            // expected
        }
    }

    public void testDecoderOverflowProtocol() {
        try {
            doTestDecoder("GET /index.html HTTP/1.0\n\n", 19);
            fail("Overflow exception had to be thrown");
        } catch (IllegalStateException e) {
            // expected
        }
    }

    public void testDecoderOverflowHeader1() {
        try {
            doTestDecoder("GET /index.html HTTP/1.0\nHost: localhost\n\n", 41);
            fail("Overflow exception had to be thrown");
        } catch (IllegalStateException e) {
            // expected
        }
    }

    public void testDecoderOverflowHeader2() {
        doTestDecoder("GET /index.html HTTP/1.0\nHost: localhost\n\n", 42);
    }
    
    public void testDecoderOverflowHeader3() {
        try {
            doTestDecoder("GET /index.html HTTP/1.0\nHost: localhost\r\n\r\n", 43);
            fail("Overflow exception had to be thrown");
        } catch (IllegalStateException e) {
            // expected
        }
    }

    public void testDecoderOverflowHeader4() {
        doTestDecoder("GET /index.html HTTP/1.0\nHost: localhost\r\n\r\n", 44);
    }
    
    public void testChunkedTransferEncodingCaseInsensitive() {
        HttpPacket packet = doTestDecoder(
                "POST /index.html HTTP/1.1\nHost: localhost\nTransfer-Encoding: CHUNked\r\n\r\n", 4096);
        assertTrue(packet.getHttpHeader().isChunked());
    }
    
    @SuppressWarnings({"unchecked"})
    private HttpPacket doTestDecoder(String request, int limit) {

        MemoryManager mm = MemoryManager.DEFAULT_MEMORY_MANAGER;
        Buffer input = Buffers.wrap(mm, request);
        
        HttpServerFilter filter = new HttpServerFilter(true, limit, null, null) {

            @Override
            protected void onHttpHeaderError(final HttpHeader httpHeader,
                    final FilterChainContext ctx,
                    final Throwable t) throws IOException {
                throw new IllegalStateException(t);
            }
        };
        FilterChainContext ctx = FilterChainContext.create(new StandaloneConnection());
        ctx.setMessage(input);

        try {
            filter.handleRead(ctx);
            return (HttpPacket) ctx.getMessage();
        } catch (IOException e) {
            throw new IllegalStateException(e.getMessage());
        }
    }

    private void doHttpRequestTest(String method, String requestURI,
            String protocol, Map<String, Pair<String, String>> headers, String eol)
            throws Exception {
        doHttpRequestTest(new Pair<String, String>(method, method),
                new Pair<String,String>(requestURI, requestURI), new Pair<String,String>(protocol, protocol),
                headers, eol, false);
    }

    private void doHttpRequestTest(String method, String requestURI,
                                   String protocol, Map<String, Pair<String, String>> headers, String eol,
                                   boolean preserveCase)
            throws Exception {
        doHttpRequestTest(new Pair<String, String>(method, method),
                new Pair<String,String>(requestURI, requestURI), new Pair<String,String>(protocol, protocol),
                headers, eol, preserveCase);
    }


    @SuppressWarnings("unchecked")
    private void doHttpRequestTest(Pair<String, String> method,
            Pair<String, String> requestURI, Pair<String, String> protocol,
            Map<String, Pair<String, String>> headers, String eol,
            boolean preserveHeaderCase)
            throws Exception {
        
        final FutureImpl<Boolean> parseResult = SafeFutureImpl.create();

        Connection connection = null;

        final HttpServerFilter serverFilter = new HttpServerFilter();
        serverFilter.setPreserveHeaderCase(preserveHeaderCase);

        FilterChainBuilder filterChainBuilder = FilterChainBuilder.stateless()
                .add(new TransportFilter())
                .add(new ChunkingFilter(2))
                .add(serverFilter)
                .add(new HTTPRequestCheckFilter(parseResult,
                        method, requestURI, protocol, headers, preserveHeaderCase));

        TCPNIOTransport transport = TCPNIOTransportBuilder.newInstance().build();
        transport.setProcessor(filterChainBuilder.build());
        
        try {
            transport.bind(PORT);
            transport.start();

            FilterChainBuilder clientFilterChainBuilder = FilterChainBuilder.stateless()
                    .add(new TransportFilter());
            
            SocketConnectorHandler connectorHandler =
                    TCPNIOConnectorHandler.builder(transport)
                    .processor(clientFilterChainBuilder.build())
                    .build();
            
            Future<Connection> future = connectorHandler.connect("localhost", PORT);
            connection = future.get(10, TimeUnit.SECONDS);
            assertTrue(connection != null);

            StringBuilder sb = new StringBuilder();

            sb.append(method.getFirst()).append(" ")
                    .append(requestURI.getFirst()).append(" ")
                    .append(protocol.getFirst()).append(eol);

            for (Entry<String, Pair<String, String>> entry : headers.entrySet()) {
                final String value = entry.getValue().getFirst();
                if (value != null) {
                    sb.append(entry.getKey()).append(": ").append(value).append(eol);
                }
            }

            sb.append(eol);

            final Buffer message = Buffers.wrap(transport.getMemoryManager(),
                    sb.toString());
            final int messageLen = message.remaining();
            Future<WriteResult> writeFuture = connection.write(message);
            
            assertEquals(messageLen, writeFuture.get().getWrittenSize());

            assertTrue(parseResult.get(10, TimeUnit.SECONDS));
        } finally {
            if (connection != null) {
                connection.closeSilently();
            }

            transport.shutdownNow();
        }
    }

    public static class HTTPRequestCheckFilter extends BaseFilter {
        private final FutureImpl<Boolean> parseResult;
        private final String method;
        private final String requestURI;
        private final String protocol;
        private final Map<String, Pair<String, String>> headers;
        private final boolean preserveCase;

        public HTTPRequestCheckFilter(FutureImpl<Boolean> parseResult,
                Pair<String, String> method,
                Pair<String, String> requestURI,
                Pair<String, String> protocol,
                Map<String, Pair<String, String>> headers,
                boolean preserveCase) {
            this.parseResult = parseResult;
            this.method = method.getSecond();
            this.requestURI = requestURI.getSecond();
            this.protocol = protocol.getSecond();
            this.headers = headers;
            this.preserveCase = preserveCase;
        }

        @Override
        public NextAction handleRead(FilterChainContext ctx)
                throws IOException {
            HttpContent httpContent = ctx.getMessage();
            HttpRequestPacket httpRequest = (HttpRequestPacket) httpContent.getHttpHeader();
            
            try {
                assertEquals(method, httpRequest.getMethod().getMethodString());
                assertEquals(requestURI, httpRequest.getRequestURI());
                assertEquals(protocol, httpRequest.getProtocol().getProtocolString());

                MimeHeaders mimeHeaders = httpRequest.getHeaders();
                _outer: for (String original : headers.keySet()) {
                    for (String name : mimeHeaders.names()) {
                        if (preserveCase) {
                            if (original.equals(name)) {
                                continue _outer;
                            }
                        } else {
                            if (original.equalsIgnoreCase(name)) {
                                continue _outer;
                            }
                        }
                    }
                    fail(String.format("Unable to find header %s in headers %s", original, mimeHeaders));
                }

                for(Entry<String, Pair<String, String>> entry : headers.entrySet()) {
                    assertEquals(entry.getValue().getSecond(),
                            httpRequest.getHeader(entry.getKey()));
                }

                parseResult.result(Boolean.TRUE);
            } catch (Throwable e) {
                parseResult.failure(e);
            }

            return ctx.getStopAction();
        }
    }

    protected static final class StandaloneConnection extends NIOConnection {

        private final SocketAddress localAddress;
        private final SocketAddress peerAddress;

        public StandaloneConnection() {
            super(TCPNIOTransportBuilder.newInstance().build());
            localAddress = new InetSocketAddress("127.0.0.1", 0);
            peerAddress = new InetSocketAddress("127.0.0.1", 0);
        }

        @Override
        protected void preClose() {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public SocketAddress getPeerAddress() {
            return peerAddress;
        }

        @Override
        public SocketAddress getLocalAddress() {
            return localAddress;
        }

        @Override
        public int getReadBufferSize() {
            return 65536;
        }

        @Override
        public void setReadBufferSize(int readBufferSize) {
        }

        @Override
        public int getWriteBufferSize() {
            return 65536;
        }

        @Override
        public void setWriteBufferSize(int writeBufferSize) {
        }

        @Override
        public void notifyCanWrite(WriteHandler handler) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public void notifyCanWrite(WriteHandler handler, int length) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public boolean canWrite() {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public boolean canWrite(int length) {
            throw new UnsupportedOperationException("Not supported yet.");
        }
    }
}
