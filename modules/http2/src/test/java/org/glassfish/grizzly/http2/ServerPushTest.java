/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2013-2015 Oracle and/or its affiliates. All rights reserved.
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
package org.glassfish.grizzly.http2;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.TimeUnit;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.FilterChain;
import org.glassfish.grizzly.filterchain.FilterChainBuilder;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.http.HttpContent;
import org.glassfish.grizzly.http.HttpHeader;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.http.HttpResponsePacket;
import org.glassfish.grizzly.http.Protocol;
import org.glassfish.grizzly.http.server.HttpHandler;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.http.server.Response;
import org.glassfish.grizzly.memory.Buffers;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import org.glassfish.grizzly.nio.transport.TCPNIOTransportBuilder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import static org.junit.Assert.*;

/**
 * HTTP2 server push tests.
 * 
 * @author Alexey Stashok
 */
@RunWith(Parameterized.class)
@SuppressWarnings("unchecked")
public class ServerPushTest extends AbstractHttp2Test {
    private static final int PORT = 8004;
    private static final Random RND = new Random();

    private final boolean isSecure;
    
    public ServerPushTest(final boolean isSecure) {
        this.isSecure = isSecure;
    }

    @Parameterized.Parameters
    public static Collection<Object[]> isSecure() {
        return AbstractHttp2Test.isSecure();
    }
    
    @Test
    public void testFilePush() throws Exception {
        final File tmpFile = createTempFile(16384);
                
        final InputStream fis = new FileInputStream(tmpFile);
        byte[] data = new byte[(int) tmpFile.length()];
        fis.read(data);
        fis.close();
        
        doTestPushResource(new TestResourceFactory() {

            @Override
            public Source create(final Http2Stream http2Stream) throws IOException {
                return Source.factory(http2Stream).createFileSource(tmpFile);
            }
        }, data);
    }
    
    @Test
    public void testByteArrayPush() throws Exception {
        final byte[] testPayload = createTestPayload(16384);
        
        doTestPushResource(new TestResourceFactory() {

            @Override
            public Source create(final Http2Stream http2Stream) {
                return Source.factory(http2Stream).createByteArraySource(testPayload);
            }
        }, testPayload);
    }
    
    @Test
    public void testBufferPush() throws Exception {
        final byte[] testPayload = createTestPayload(16384);
        
        doTestPushResource(new TestResourceFactory() {

            @Override
            public Source create(final Http2Stream http2Stream) {
                return Source.factory(http2Stream).createBufferSource(Buffers.wrap(
                        http2Stream.getHttp2Connection().getMemoryManager(), testPayload));
            }
        }, testPayload);
    }
    
    @Test
    public void testStringPush() throws Exception {
        final byte[] testPayload = createTestPayload(16384);
        
        doTestPushResource(new TestResourceFactory() {

            @Override
            public Source create(final Http2Stream http2Stream) {
                return Source.factory(http2Stream).createStringSource(new String(testPayload,
                        org.glassfish.grizzly.http.util.Constants.DEFAULT_HTTP_CHARSET));
            }
        }, testPayload);
    }

    private void doTestPushResource(final TestResourceFactory resourceFactory,
            final byte[] resourceAsciiPayloadToCheck) throws Exception {
        
        final String extraHeaderName = "Extra-Header";
        final String extraHeaderValue = "Extra-Value";
        
        final BlockingQueue<HttpContent> resultQueue =
                new LinkedTransferQueue<HttpContent>();
        
        final FilterChainBuilder filterChainBuilder =
                createClientFilterChainAsBuilder(isSecure);
        filterChainBuilder.add(new ClientAggregatorFilter(resultQueue));
        
        final TCPNIOTransport clientTransport = TCPNIOTransportBuilder.newInstance().build();
        final FilterChain clientFilterChain = filterChainBuilder.build();
        setInitialWindowSize(clientFilterChain, resourceAsciiPayloadToCheck.length / 16);
        clientTransport.setProcessor(clientFilterChain);
        
        final boolean hasExtraHeader = RND.nextBoolean();
        
        final HttpHandler httpHandler = new HttpHandler() {

            @Override
            public void service(final Request request, final Response response) throws Exception {
                final Http2Stream http2Stream =
                        (Http2Stream) request.getAttribute(Http2Stream.HTTP2_STREAM_ATTRIBUTE);
                final PushResource.PushResourceBuilder pushResourceBuilder = PushResource.builder()
                        .contentType("image/png")
                        .statusCode(200, "PUSH")
                        .source(resourceFactory.create(http2Stream));

                if (hasExtraHeader) {
                    pushResourceBuilder.header(extraHeaderName, extraHeaderValue);
                }

                http2Stream.addPushResource(
                        "https://localhost:7070/getimages/push",
                        pushResourceBuilder.build());

                response.setStatus(200, "DONE");
            }
        };

        final HttpServer server = createServer(null, PORT, isSecure,
                HttpHandlerRegistration.of(httpHandler, "/path/*"));

        final HttpRequestPacket request = HttpRequestPacket.builder()
                .method("GET").protocol(Protocol.HTTP_1_1).uri("/path")
                .header("Host", "localhost:" + PORT)
                .build();
        
        try {
            server.start();
            clientTransport.start();

            Future<Connection> connectFuture = clientTransport.connect("localhost", PORT);
            Connection connection = null;
            try {
                connection = connectFuture.get(10, TimeUnit.SECONDS);
                
                connection.write(HttpContent.builder(request)
                        .content(Buffers.EMPTY_BUFFER)
                        .last(true)
                        .build());
                
                final HttpContent content1 = resultQueue.poll(10, TimeUnit.SECONDS);
                assertNotNull("First HttpContent is null", content1);
                final HttpHeader header1 = content1.getHttpHeader();
                
                final HttpContent content2 = resultQueue.poll(10, TimeUnit.SECONDS);
                assertNotNull("Second HttpContent is null", content2);
                final HttpHeader header2 = content2.getHttpHeader();

                final HttpResponsePacket pushedResponse;
                final HttpContent pushedContent;
                final HttpResponsePacket mainResponse;
                
                if (Http2Stream.getStreamFor(header1).isPushStream()) {
                    pushedResponse = (HttpResponsePacket) header1;
                    pushedContent = content1;
                    mainResponse = (HttpResponsePacket) header2;
                } else {
                    pushedResponse = (HttpResponsePacket) header2;
                    pushedContent = content2;
                    mainResponse = (HttpResponsePacket) header1;
                }
                
                assertEquals(200, pushedResponse.getStatus());
                assertEquals("OK", pushedResponse.getReasonPhrase());
                assertEquals(resourceAsciiPayloadToCheck.length, pushedResponse.getContentLength());
                assertEquals(resourceAsciiPayloadToCheck.length, pushedContent.getContent().remaining());
                if (hasExtraHeader) {
                    assertEquals(extraHeaderValue, pushedResponse.getHeader(extraHeaderName));
                }
                
                assertEquals(200, mainResponse.getStatus());
                assertEquals("OK", mainResponse.getReasonPhrase());
                
                final String pattern = new String(resourceAsciiPayloadToCheck);
                assertEquals("Pushed data mismatch", pattern, pushedContent.getContent().toStringContent());

            } finally {
                // Close the client connection
                if (connection != null) {
                    connection.closeSilently();
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
            fail();
        } finally {
            clientTransport.shutdownNow();
            server.shutdownNow();
        }
    }
    
    private void setInitialWindowSize(final FilterChain filterChain,
            final int initialWindowSize) {
        final int http2FilterIdx = filterChain.indexOfType(Http2BaseFilter.class);
        final Http2BaseFilter http2Filter =
                (Http2BaseFilter) filterChain.get(http2FilterIdx);
        http2Filter.setInitialWindowSize(initialWindowSize);
    }
    
    private static File createTempFile(final int size) throws IOException {
        final File f = File.createTempFile("grizzly-file-cache", ".txt");
        f.deleteOnExit();
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(f);
            out.write(createTestPayload(size));
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException ignored) {
                }
            }
        }
        return f;
    }

    private static byte[] createTestPayload(final int size) {
        final byte[] array = new byte[size];
        final Random r = new Random(System.currentTimeMillis());
        
        for (int i = 0; i < size; i++) {
            array[i] = (byte) (r.nextInt('Z' - 'A') + 'A');
        }

        return array;
    }
    
    private static class ClientAggregatorFilter extends BaseFilter {
        private final BlockingQueue<HttpContent> resultQueue;
        private final Map<Http2Stream, HttpContent> remaindersMap =
                new HashMap<Http2Stream, HttpContent>();

        public ClientAggregatorFilter(BlockingQueue<HttpContent> resultQueue) {
            this.resultQueue = resultQueue;
        }

        @Override
        public NextAction handleRead(FilterChainContext ctx) throws IOException {
            final HttpContent message = ctx.getMessage();
            final Http2Stream http2Stream = Http2Stream.getStreamFor(message.getHttpHeader());

            final HttpContent remainder = remaindersMap.get(http2Stream);
            final HttpContent sum = remainder != null
                    ? remainder.append(message) : message;

            if (!sum.isLast()) {
                remaindersMap.put(http2Stream, sum);
                return ctx.getStopAction();
            }

            resultQueue.add(sum);

            return ctx.getStopAction();
        }
    }

    private static abstract class TestResourceFactory {
        protected TestResourceFactory() {
        }
        
        public abstract Source create(final Http2Stream http2Stream)
                throws IOException;
    }
}
