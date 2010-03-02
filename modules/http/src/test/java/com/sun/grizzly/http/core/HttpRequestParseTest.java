/*
 *
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2007-2010 Sun Microsystems, Inc. All rights reserved.
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
import com.sun.grizzly.StandaloneProcessor;
import com.sun.grizzly.TransportFactory;
import com.sun.grizzly.filterchain.BaseFilter;
import com.sun.grizzly.filterchain.FilterChainBuilder;
import com.sun.grizzly.filterchain.FilterChainContext;
import com.sun.grizzly.filterchain.NextAction;
import com.sun.grizzly.filterchain.TransportFilter;
import com.sun.grizzly.http.HttpServerFilter;
import com.sun.grizzly.impl.FutureImpl;
import com.sun.grizzly.memory.MemoryManager;
import com.sun.grizzly.memory.MemoryUtils;
import com.sun.grizzly.nio.AbstractNIOConnection;
import com.sun.grizzly.nio.transport.TCPNIOConnection;
import com.sun.grizzly.nio.transport.TCPNIOTransport;
import com.sun.grizzly.streams.StreamReader;
import com.sun.grizzly.streams.StreamWriter;
import com.sun.grizzly.utils.ChunkingFilter;
import com.sun.grizzly.utils.Pair;
import java.io.IOException;
import java.net.SocketAddress;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import junit.framework.TestCase;

/**
 * Testing HTTP request parsing
 * 
 * @author Alexey Stashok
 */
public class HttpRequestParseTest extends TestCase {
    private static final Logger logger = Grizzly.logger(HttpRequestParseTest.class);
    
    public static int PORT = 8000;

    public void testHeaderlessRequestLine() throws Exception {
        doHttpRequestTest("GET", "/index.html", "HTTP/1.0", Collections.EMPTY_MAP, "\r\n");
    }

    public void testSimpleHeaders() throws Exception {
        Map<String, Pair<String, String>> headers =
                new HashMap<String, Pair<String, String>>();
        headers.put("Host", new Pair("localhost", "localhost"));
        headers.put("Content-length", new Pair("2345", "2345"));
        doHttpRequestTest("GET", "/index.html", "HTTP/1.1", headers, "\r\n");
    }

    public void testMultiLineHeaders() throws Exception {
        Map<String, Pair<String, String>> headers =
                new HashMap<String, Pair<String, String>>();
        headers.put("Host", new Pair("localhost", "localhost"));
        headers.put("Multi-line", new Pair("first\r\n          second\r\n       third", "first seconds third"));
        headers.put("Content-length", new Pair("2345", "2345"));
        doHttpRequestTest("GET", "/index.html", "HTTP/1.1", headers, "\r\n");
    }

    public void testHeadersN() throws Exception {
        Map<String, Pair<String, String>> headers =
                new HashMap<String, Pair<String, String>>();
        headers.put("Host", new Pair("localhost", "localhost"));
        headers.put("Multi-line", new Pair("first\r\n          second\n       third", "first seconds third"));
        headers.put("Content-length", new Pair("2345", "2345"));
        doHttpRequestTest("GET", "/index.html", "HTTP/1.1", headers, "\n");
    }

    public void testDecoderOK() {
        try {
            doTestDecoder("GET /index.html HTTP/1.0\n\n", 4096);
            assertTrue(true);
        } catch (IllegalStateException e) {
            logger.log(Level.SEVERE, "exception", e);
            assertTrue("Unexpected exception", false);
        }
    }

    public void testDecoderOverflowMethod() {
        try {
            doTestDecoder("GET /index.html HTTP/1.0\n\n", 2);
            assertTrue("Overflow exception had to be thrown", false);
        } catch (IllegalStateException e) {
            assertTrue(true);
        }
    }

    public void testDecoderOverflowURI() {
        try {
            doTestDecoder("GET /index.html HTTP/1.0\n\n", 8);
            assertTrue("Overflow exception had to be thrown", false);
        } catch (IllegalStateException e) {
            assertTrue(true);
        }
    }

    public void testDecoderOverflowProtocol() {
        try {
            doTestDecoder("GET /index.html HTTP/1.0\n\n", 19);
            assertTrue("Overflow exception had to be thrown", false);
        } catch (IllegalStateException e) {
            assertTrue(true);
        }
    }

    public void testDecoderOverflowHeader() {
        try {
            doTestDecoder("GET /index.html HTTP/1.0\nHost: localhost\n\n", 30);
            assertTrue("Overflow exception had to be thrown", false);
        } catch (IllegalStateException e) {
            assertTrue(true);
        }
    }

    private HttpPacket doTestDecoder(String request, int limit) {

        MemoryManager mm = TransportFactory.getInstance().getDefaultMemoryManager();
        Buffer input = MemoryUtils.wrap(mm, request);
        
        HttpServerFilter filter = new HttpServerFilter(limit);
        FilterChainContext ctx = FilterChainContext.create();
        ctx.setMessage(input);
        ctx.setConnection(new StandaloneConnection());

        try {
            filter.handleRead(ctx, null);
            return (HttpPacket) ctx.getMessage();
        } catch (IOException e) {
            throw new IllegalStateException(e.getMessage());
        }
    }

    private void doHttpRequestTest(String method, String requestURI,
            String protocol, Map<String, Pair<String, String>> headers, String eol)
            throws Exception {
        
        final FutureImpl<Boolean> parseResult = FutureImpl.create();

        Connection connection = null;
        StreamReader reader = null;
        StreamWriter writer = null;

        FilterChainBuilder filterChainBuilder = FilterChainBuilder.singleton();
        filterChainBuilder.add(new TransportFilter());
        filterChainBuilder.add(new ChunkingFilter(2));
        filterChainBuilder.add(new HttpServerFilter());
        filterChainBuilder.add(new HTTPRequestCheckFilter(parseResult,
                method, requestURI, protocol, Collections.EMPTY_MAP));

        TCPNIOTransport transport = TransportFactory.getInstance().createTCPTransport();
        transport.setProcessor(filterChainBuilder.build());
        
        try {
            transport.bind(PORT);
            transport.start();

            Future<Connection> future = transport.connect("localhost", PORT);
            connection = (TCPNIOConnection) future.get(10, TimeUnit.SECONDS);
            assertTrue(connection != null);

            connection.configureStandalone(true);

            StringBuffer sb = new StringBuffer();

            sb.append(method + " " + requestURI + " " + protocol + eol);

            for (Entry<String, Pair<String, String>> entry : headers.entrySet()) {
                sb.append(entry.getKey()).append(": ").append(entry.getValue().getFirst()).append(eol);
            }

            sb.append(eol);

            byte[] message = sb.toString().getBytes();
            
            writer = StandaloneProcessor.INSTANCE.getStreamWriter(connection);
            writer.writeByteArray(message);
            Future<Integer> writeFuture = writer.flush();

            assertTrue("Write timeout", writeFuture.isDone());
            assertEquals(message.length, (int) writeFuture.get());

            assertTrue(parseResult.get(10000, TimeUnit.SECONDS));
        } finally {
            if (connection != null) {
                connection.close();
            }

            transport.stop();
            TransportFactory.getInstance().close();
        }
    }

    public class HTTPRequestCheckFilter extends BaseFilter {
        private final FutureImpl<Boolean> parseResult;
        private final String method;
        private final String requestURI;
        private final String protocol;
        private final Map<String, Pair<String, String>> headers;

        public HTTPRequestCheckFilter(FutureImpl parseResult, String method,
                String requestURI, String protocol,
                Map<String, Pair<String, String>> headers) {
            this.parseResult = parseResult;
            this.method = method;
            this.requestURI = requestURI;
            this.protocol = protocol;
            this.headers = headers;
        }

        @Override
        public NextAction handleRead(FilterChainContext ctx)
                throws IOException {
            HttpContent httpContent = (HttpContent) ctx.getMessage();
            HttpRequest httpRequest = (HttpRequest) httpContent.getHttpHeader();
            
            try {
                assertEquals(method, httpRequest.getMethod());
                assertEquals(requestURI, httpRequest.getRequestURI());
                assertEquals(protocol, httpRequest.getProtocol());

                for(Entry<String, Pair<String, String>> entry : headers.entrySet()) {
                    assertEquals(entry.getValue().getSecond(),
                            httpRequest.getHeader(entry.getKey()));
                }

                parseResult.result(Boolean.TRUE);
            } catch (Throwable e) {
                parseResult.failure(e);
            }

            return ctx.getInvokeAction();
        }
    }

    protected static final class StandaloneConnection extends AbstractNIOConnection {

        public StandaloneConnection() {
            super(TransportFactory.getInstance().createTCPTransport());
        }

        @Override
        protected void preClose() {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public SocketAddress getPeerAddress() {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public SocketAddress getLocalAddress() {
            throw new UnsupportedOperationException("Not supported yet.");
        }
    }
}
