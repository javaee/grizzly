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

import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.http.util.MimeHeaders;
import org.junit.After;
import org.junit.Before;
import java.util.Queue;
import org.junit.runners.Parameterized;
import org.junit.runner.RunWith;
import java.util.Arrays;
import java.util.Collection;
import org.junit.runners.Parameterized.Parameters;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.SocketConnectorHandler;
import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.FilterChainBuilder;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.filterchain.TransportFilter;
import org.glassfish.grizzly.impl.FutureImpl;
import org.glassfish.grizzly.impl.UnsafeFutureImpl;
import org.glassfish.grizzly.memory.Buffers;
import org.glassfish.grizzly.memory.MemoryManager;
import org.glassfish.grizzly.nio.transport.TCPNIOConnectorHandler;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import org.glassfish.grizzly.nio.transport.TCPNIOTransportBuilder;
import org.glassfish.grizzly.utils.Charsets;
import org.glassfish.grizzly.utils.ChunkingFilter;
import org.glassfish.grizzly.utils.DataStructures;
import org.glassfish.grizzly.utils.Pair;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Chunked Transfer-Encoding tests.
 * 
 * @author Alexey Stashok
 */
@RunWith(Parameterized.class)
public class ChunkedTransferEncodingTest {
    public static final int PORT = 19007;

    private final String eol;
    private final boolean isChunkWhenParsing;
    
    private TCPNIOTransport transport;
    private Connection connection;
    private HTTPRequestCheckFilter httpRequestCheckFilter;
    
    final BlockingQueue<Future<Boolean>> resultQueue =
            DataStructures.getLTQInstance();
    
    @Parameters
    public static Collection<Object[]> getMode() {
        return Arrays.asList(new Object[][]{
                    {"\r\n", Boolean.FALSE},
                    {"\r\n", Boolean.TRUE},
                    {"\n", Boolean.FALSE},
                    {"\n", Boolean.TRUE}
                });
    }

    @Before
    public void before() throws Exception {
        Grizzly.setTrackingThreadCache(true);

        FilterChainBuilder filterChainBuilder = FilterChainBuilder.stateless();
        filterChainBuilder.add(new TransportFilter());
        if (isChunkWhenParsing) {
            filterChainBuilder.add(new ChunkingFilter(2));
        }
        final HttpServerFilter httpServerFilter =
                new HttpServerFilter(true,
                                     HttpServerFilter.DEFAULT_MAX_HTTP_PACKET_HEADER_SIZE,
                                     null,
                                     null,
                                     null,
                                     MimeHeaders.MAX_NUM_HEADERS_UNBOUNDED,
                                     MimeHeaders.MAX_NUM_HEADERS_UNBOUNDED);
        filterChainBuilder.add(httpServerFilter);
        httpRequestCheckFilter = new HTTPRequestCheckFilter(resultQueue);
        filterChainBuilder.add(httpRequestCheckFilter);

        transport = TCPNIOTransportBuilder.newInstance().build();
        transport.getAsyncQueueIO().getWriter().setMaxPendingBytesPerConnection(-1);
        
        transport.setProcessor(filterChainBuilder.build());
        
        transport.bind(PORT);
        transport.start();

        FilterChainBuilder clientFilterChainBuilder = FilterChainBuilder.stateless();
        clientFilterChainBuilder.add(new TransportFilter());

        SocketConnectorHandler connectorHandler = TCPNIOConnectorHandler
                .builder(transport)
                .processor(clientFilterChainBuilder.build())
                .build();

        Future<Connection> future = connectorHandler.connect("localhost", PORT);
        connection = future.get(10, TimeUnit.SECONDS);
        assertTrue(connection != null);
    }
    
    @After
    public void after() throws Exception {
        if (connection != null) {
            connection.closeSilently();
        }

        if (transport != null) {
            try {
                transport.shutdownNow();
            } catch (Exception ignored) {
            }
        }
    }

    public ChunkedTransferEncodingTest(String eol, boolean isChunkWhenParsing) {
        this.eol = eol;
        this.isChunkWhenParsing = isChunkWhenParsing;
    }
        
    @Test
    public void testNoTrailerHeaders() throws Exception {
        final int packetsNum = 5;
        
        doHttpRequestTest(packetsNum, true,
                Collections.<String, Pair<String, String>>emptyMap());
        
        for (int i = 0; i < packetsNum; i++) {
            Future<Boolean> result = resultQueue.poll(10, TimeUnit.SECONDS);
            assertNotNull("Timeout for result#" + i, result);
            assertTrue(result.get(10, TimeUnit.SECONDS));
        }

    }

    @Test
    public void testTrailerHeaders() throws Exception {
        Map<String, Pair<String, String>> headers =
                new HashMap<String, Pair<String, String>>();
        headers.put("X-Host", new Pair<String,String>("localhost", "localhost"));
        headers.put("X-Content-length", new Pair<String,String>("2345", "2345"));
        
        final int packetsNum = 5;
        
        doHttpRequestTest(packetsNum, true, headers);
        
        for (int i = 0; i < packetsNum; i++) {
            Future<Boolean> result = resultQueue.poll(10, TimeUnit.SECONDS);
            assertNotNull("Timeout for result#" + i, result);
            assertTrue(result.get(10, TimeUnit.SECONDS));
        }
    }

    @Test
    public void testTrailerHeadersWithoutContent() throws Exception {
        Map<String, Pair<String, String>> headers =
                new HashMap<String, Pair<String, String>>();
        headers.put("X-Host", new Pair<String,String>("localhost", "localhost"));
        headers.put("X-Content-length", new Pair<String,String>("2345", "2345"));
        
        final int packetsNum = 5;
        
        doHttpRequestTest(packetsNum, false, headers);
        
        for (int i = 0; i < packetsNum; i++) {
            Future<Boolean> result = resultQueue.poll(10, TimeUnit.SECONDS);
            assertNotNull("Timeout for result#" + i, result);
            assertTrue(result.get(10, TimeUnit.SECONDS));
        }
    }

    @Test
    public void testTrailerHeadersOverflow() throws Exception {
        Map<String, Pair<String, String>> headers =
                new HashMap<String, Pair<String, String>>();
        // This number of headers should be enough to overflow socket's read buffer,
        // so trailer headers will not fit into socket read window
        for (int i = 0; i < HttpCodecFilter.DEFAULT_MAX_HTTP_PACKET_HEADER_SIZE; i++) {
            headers.put("X-Host-" + i, new Pair<String,String>("localhost", "localhost"));
        }
        
        doHttpRequestTest(1, true, headers);
        
        Future<Boolean> result = resultQueue.poll(10, TimeUnit.SECONDS);
        assertNotNull("Timeout", result);
        try {
            result.get(10, TimeUnit.SECONDS);
            fail("Expected error");
        } catch (ExecutionException e) {
            assertTrue(e.getCause() instanceof HttpBrokenContentException);
            assertEquals("The chunked encoding trailer header is too large",
                    e.getCause().getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testInvalidHexByteInChunkLength() throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("POST / HTTP/1.1\r\n");
        sb.append("Host: localhost:").append(PORT).append("\r\n");
        sb.append("Transfer-Encoding: chunked\r\n\r\n");
        sb.append((char) 193).append("\r\n");
        
        Buffer b = Buffers.wrap(MemoryManager.DEFAULT_MEMORY_MANAGER,
                                sb.toString(),
                                Charsets.ASCII_CHARSET);
        Future f = connection.write(b);
        f.get(10, TimeUnit.SECONDS);
        Future<Boolean> result = resultQueue.poll(10, TimeUnit.SECONDS);
        try {
            result.get(10, TimeUnit.SECONDS);
            fail("Expected HttpBrokenContentException to be thrown on server side");
        } catch (ExecutionException ee) {
            assertEquals(HttpBrokenContentException.class, ee.getCause().getClass());
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testSpacesInChunkSizeHeader() throws Exception {
        final String msg = "abc";
        final String msgLen = Integer.toHexString(msg.length());
        
        httpRequestCheckFilter.setCheckParameters(
                Buffers.wrap(connection.getMemoryManager(), msg),
                Collections.<String, Pair<String, String>>emptyMap());
        
        StringBuilder sb = new StringBuilder();
        sb.append("POST / HTTP/1.1\r\n");
        sb.append("Host: localhost:").append(PORT).append("\r\n");
        sb.append("Transfer-Encoding: chunked\r\n\r\n");
        sb.append("  ").append(msgLen).append("  ").append(eol).append(msg).append(eol);
        sb.append("  0  ").append(eol).append(eol);
        
        Buffer b = Buffers.wrap(MemoryManager.DEFAULT_MEMORY_MANAGER,
                                sb.toString(),
                                Charsets.ASCII_CHARSET);
        Future f = connection.write(b);
        f.get(10, TimeUnit.SECONDS);
        Future<Boolean> result = resultQueue.poll(10, TimeUnit.SECONDS);
        assertTrue(result.get(10, TimeUnit.SECONDS));
    }
    
    /**
     * Test private method {@link ChunkedTransferEncoding#checkOverflow(long)}
     * via reflection.
     * 
     * @throws Exception 
     */
    public void testChunkLenOverflow() throws Exception {
        final java.lang.reflect.Method method =
                ChunkedTransferEncoding.class.getDeclaredMethod("checkOverflow",
                        Long.class);
        method.setAccessible(true);
        
        final long cornerValue = Long.MAX_VALUE >> 4;
        
        final long value1 = cornerValue;
        assertTrue((value1 << 4) > 0);
        assertTrue((Boolean) method.invoke(null, value1));
        
        final long value2 = cornerValue + 1;
        assertFalse((value2 << 4) > 0);
        assertFalse((Boolean) method.invoke(null, value1));
    }
    
    @SuppressWarnings("unchecked")
    private void doHttpRequestTest(
            int packetsNum,
            boolean hasContent,
            Map<String, Pair<String, String>> trailerHeaders) throws Exception {
        
        final Buffer content;
        if (hasContent) {
            content = Buffers.wrap(MemoryManager.DEFAULT_MEMORY_MANAGER,
                "a=0&b=1", Charsets.ASCII_CHARSET);
        } else {
            content = Buffers.EMPTY_BUFFER;
        }

        httpRequestCheckFilter.setCheckParameters(content, trailerHeaders);
        
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < packetsNum; i++) {
            sb.append("POST / HTTP/1.1").append(eol)
                    .append("Host: localhost:").append(PORT).append(eol)
                    .append("Transfer-encoding: chunked").append(eol)
                    .append("Content-Type: application/x-www-form-urlencoded").append(eol);
            
            if (i == packetsNum - 1) {
                sb.append("Connection: close").append(eol);
            }
            
            sb.append(eol);

            if (hasContent) {
                sb.append("3").append(eol)
                        .append("a=0").append(eol)
                        .append("4").append(eol)
                        .append("&b=1").append(eol);
            }

            sb.append("0").append(eol);


            for (Entry<String, Pair<String, String>> entry : trailerHeaders.entrySet()) {
                final String value = entry.getValue().getFirst();
                if (value != null) {
                    sb.append(entry.getKey()).append(": ").append(value).append(eol);
                }
            }

            sb.append(eol);
        }
        
        connection.write(Buffers.wrap(transport.getMemoryManager(),
                sb.toString(), Charsets.ASCII_CHARSET));
    }
    
    public static class HTTPRequestCheckFilter extends BaseFilter {
        private final Queue<Future<Boolean>> resultQueue;
        private volatile Buffer content;
        private volatile Map<String, Pair<String, String>> trailerHeaders;

        public HTTPRequestCheckFilter(
                final Queue<Future<Boolean>> resultQueue) {
            this.resultQueue = resultQueue;
        }

        public void setCheckParameters(
                final Buffer content,
                final Map<String, Pair<String, String>> trailerHeaders) {
            this.content = content;
            this.trailerHeaders = trailerHeaders;
            
        }
        @Override
        public NextAction handleRead(FilterChainContext ctx)
                throws IOException {
            final HttpContent httpContent = ctx.getMessage();
            if (!httpContent.isLast()) {
                return ctx.getStopAction(httpContent);
            }
            
            try {
                assertEquals(content, httpContent.getContent());
                assertTrue(HttpTrailer.isTrailer(httpContent));
                final HttpTrailer httpTrailer = (HttpTrailer) httpContent;
                for(Entry<String, Pair<String, String>> entry : trailerHeaders.entrySet()) {
                    assertEquals(entry.getValue().getSecond(),
                            httpTrailer.getHeader(entry.getKey()));
                }

                FutureImpl<Boolean> future = UnsafeFutureImpl.create();
                future.result(Boolean.TRUE);
                resultQueue.offer(future);
            } catch (Throwable e) {
                FutureImpl<Boolean> future = UnsafeFutureImpl.create();
                future.failure(e);
                resultQueue.offer(future);
            }

            return ctx.getStopAction();
        }
    }
}
