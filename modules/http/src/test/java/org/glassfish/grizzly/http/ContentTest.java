/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2015 Oracle and/or its affiliates. All rights reserved.
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
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import junit.framework.TestCase;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.SocketConnectorHandler;
import org.glassfish.grizzly.WriteResult;
import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.FilterChain;
import org.glassfish.grizzly.filterchain.FilterChainBuilder;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.filterchain.TransportFilter;
import org.glassfish.grizzly.http.util.Header;
import org.glassfish.grizzly.impl.FutureImpl;
import org.glassfish.grizzly.impl.SafeFutureImpl;
import org.glassfish.grizzly.memory.Buffers;
import org.glassfish.grizzly.memory.MemoryManager;
import org.glassfish.grizzly.nio.transport.TCPNIOConnection;
import org.glassfish.grizzly.nio.transport.TCPNIOConnectorHandler;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import org.glassfish.grizzly.nio.transport.TCPNIOTransportBuilder;
import org.glassfish.grizzly.utils.ChunkingFilter;

/**
 *
 * @author oleksiys
 */
public class ContentTest extends TestCase {

    public static final int PORT = 19003;

    @SuppressWarnings({"unchecked"})
    public void testExplicitContentLength() throws Exception {
        HttpRequestPacket httpRequest = HttpRequestPacket.builder().method("POST").protocol(Protocol.HTTP_1_1).uri("/default").contentLength(10).build();
        httpRequest.addHeader(Header.Host, "localhost:" + PORT);
        HttpContent content = httpRequest.httpContentBuilder().content(Buffers.wrap(MemoryManager.DEFAULT_MEMORY_MANAGER, "1234567890")).build();

        doHttpRequestTest(content);
    }

    @SuppressWarnings({"unchecked"})
    public void testHeaderContentLength() throws Exception {
        HttpRequestPacket httpRequest = HttpRequestPacket.builder().method("POST").protocol(Protocol.HTTP_1_1).uri("/default").header("Content-Length", "10").build();
        httpRequest.addHeader("Host", "localhost:" + PORT);
        HttpContent content = httpRequest.httpContentBuilder().content(Buffers.wrap(MemoryManager.DEFAULT_MEMORY_MANAGER, "1234567890")).build();

        doHttpRequestTest(content);
    }

    @SuppressWarnings({"unchecked"})
    public void testSimpleChunked() throws Exception {
        HttpRequestPacket httpRequest = HttpRequestPacket.builder().method("POST").protocol(Protocol.HTTP_1_1).uri("/default").chunked(true).build();
        httpRequest.addHeader("Host", "localhost:" + PORT);
        HttpContent content = httpRequest.httpTrailerBuilder().content(Buffers.wrap(MemoryManager.DEFAULT_MEMORY_MANAGER, "1234567890")).build();

        doHttpRequestTest(content);
    }

    @SuppressWarnings({"unchecked"})
    public void testSeveralChunked() throws Exception {
        HttpRequestPacket httpRequest = HttpRequestPacket.builder().method("POST").protocol(Protocol.HTTP_1_1).uri("/default").chunked(true).build();
        httpRequest.addHeader("Host", "localhost:" + PORT);
        HttpContent content1 = httpRequest.httpContentBuilder().content(Buffers.wrap(MemoryManager.DEFAULT_MEMORY_MANAGER, "1234567890")).build();
        HttpContent content2 = httpRequest.httpContentBuilder().content(Buffers.wrap(MemoryManager.DEFAULT_MEMORY_MANAGER, "0987654321")).build();
        HttpContent content3 = httpRequest.httpTrailerBuilder().content(Buffers.wrap(MemoryManager.DEFAULT_MEMORY_MANAGER, "final")).build();

        doHttpRequestTest(content1, content2, content3);
    }

    private void doHttpRequestTest(HttpContent... patternContentMessages)
            throws Exception {

        final FutureImpl<HttpPacket> parseResult = SafeFutureImpl.create();

        Connection<SocketAddress> connection = null;

        FilterChainBuilder filterChainBuilder = FilterChainBuilder.stateless();
        filterChainBuilder.add(new TransportFilter());
        filterChainBuilder.add(new ChunkingFilter(2));
        filterChainBuilder.add(new HttpServerFilter());
        filterChainBuilder.add(new HTTPRequestMergerFilter(parseResult));
        FilterChain filterChain = filterChainBuilder.build();
        
        TCPNIOTransport transport = TCPNIOTransportBuilder.newInstance().build();
        transport.setProcessor(filterChain);

        try {
            transport.bind(PORT);
            transport.start();

            FilterChainBuilder clientFilterChainBuilder = FilterChainBuilder.stateless();
            clientFilterChainBuilder.add(new TransportFilter());
            clientFilterChainBuilder.add(new ChunkingFilter(2));
            clientFilterChainBuilder.add(new HttpClientFilter());
            FilterChain clientFilterChain = clientFilterChainBuilder.build();
            
            SocketConnectorHandler connectorHandler =
                    TCPNIOConnectorHandler.builder(transport)
                    .processor(clientFilterChain)
                    .build();

            Future<Connection> future = connectorHandler.connect("localhost", PORT);
            connection = (TCPNIOConnection) future.get(10, TimeUnit.SECONDS);
            assertTrue(connection != null);

            final HttpHeader patternHeader = patternContentMessages[0].getHttpHeader();

            byte[] patternContent = new byte[0];
            for(int i = 0; i<patternContentMessages.length; i++) {
                int oldLen = patternContent.length;
                final ByteBuffer bb = patternContentMessages[i].getContent().toByteBuffer().duplicate();
                patternContent = Arrays.copyOf(patternContent, oldLen + bb.remaining());
                bb.get(patternContent, oldLen, bb.remaining());
            }

            for (HttpContent content : patternContentMessages) {
                Future<WriteResult<HttpContent, SocketAddress>> writeFuture = connection.write(content);
                writeFuture.get(10, TimeUnit.SECONDS);
            }

            HttpContent result = (HttpContent) parseResult.get(10, TimeUnit.SECONDS);
            HttpHeader resultHeader = result.getHttpHeader();

            assertEquals(patternHeader.getContentLength(), resultHeader.getContentLength());
            assertEquals(patternHeader.isChunked(), resultHeader.isChunked());

            byte[] resultContent = new byte[result.getContent().remaining()];
            result.getContent().get(resultContent);
            assertTrue(Arrays.equals(patternContent, resultContent));
            
        } finally {
            if (connection != null) {
                connection.closeSilently();
            }

            transport.shutdownNow();
        }
    }

    public static class HTTPRequestMergerFilter extends BaseFilter {
        private final FutureImpl<HttpPacket> parseResult;

        public HTTPRequestMergerFilter(FutureImpl<HttpPacket> parseResult) {
            this.parseResult = parseResult;
        }

        @Override
        public NextAction handleRead(FilterChainContext ctx) throws IOException {
            HttpContent httpContent = ctx.getMessage();

            if (!httpContent.isLast()) {
                return ctx.getStopAction(httpContent);
            }

            parseResult.result(httpContent);
            return ctx.getStopAction();
        }
    }
}
