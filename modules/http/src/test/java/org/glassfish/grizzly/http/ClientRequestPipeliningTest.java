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

import junit.framework.TestCase;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.GrizzlyFuture;
import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.Filter;
import org.glassfish.grizzly.filterchain.FilterChainBuilder;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.filterchain.TransportFilter;
import org.glassfish.grizzly.http.util.Header;
import org.glassfish.grizzly.http.util.HttpStatus;
import org.glassfish.grizzly.memory.Buffers;
import org.glassfish.grizzly.memory.MemoryManager;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import org.glassfish.grizzly.nio.transport.TCPNIOTransportBuilder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class ClientRequestPipeliningTest extends TestCase {

    private static final int PORT = 9933;


    // ------------------------------------------------------------ Test Methods


    /*
     * This test merely ensures that it's possible to rapidly fire requests
     * without waiting for a response.  The code will now poll() a queue
     * to associate a request with a response.  This *does not* however mean
     * that we do any validation of the connect (i.e., keep-alive), error
     * handling or other pipeline semantics (yet).
     */
    @SuppressWarnings({"unchecked"})
    public void testPipelinedRequests() throws Exception {

        final int requestCount = 5;
        final CountDownLatch latch = new CountDownLatch(requestCount);
        final ResponseCollectingFilter responseFilter = new ResponseCollectingFilter(latch);
        final TCPNIOTransport clientTransport = createClientTransport(responseFilter);
        final TCPNIOTransport serverTransport = createServerTransport();
        try {
            serverTransport.bind(PORT);
            serverTransport.start();
            clientTransport.start();

            final GrizzlyFuture<Connection> connFuture =
                    clientTransport.connect("localhost", PORT);

            final Connection c = connFuture.get(15, TimeUnit.SECONDS);
            for (int i = 0; i < requestCount; i++) {
                final HttpRequestPacket.Builder reqBuilder =
                        HttpRequestPacket.builder();
                reqBuilder.method(Method.GET);
                reqBuilder.uri("/");
                reqBuilder.protocol(Protocol.HTTP_1_1);
                reqBuilder.header(Header.Host, "localhost:" + PORT);
                reqBuilder.contentLength(0);
                c.write(reqBuilder.build());
            }
            latch.await(30, TimeUnit.SECONDS);

            assertEquals(requestCount, responseFilter.responses.size());
            for (int i = 0; i < requestCount; i++) {
                assertEquals(i + 1, responseFilter.responses.get(i).intValue());
            }
        } finally {
            clientTransport.shutdownNow();
            serverTransport.shutdownNow();
        }
    }


    // --------------------------------------------------------- Private Methods


    private TCPNIOTransport createClientTransport(final Filter clientFilter) {

        final TCPNIOTransport transport =
                TCPNIOTransportBuilder.newInstance().build();
        final FilterChainBuilder b = FilterChainBuilder.stateless();
        b.add(new TransportFilter());
        b.add(new HttpClientFilter());
        b.add(clientFilter);
        transport.setProcessor(b.build());
        return transport;

    }

    private TCPNIOTransport createServerTransport() {

        final TCPNIOTransport transport =
                TCPNIOTransportBuilder.newInstance().build();
        final FilterChainBuilder b = FilterChainBuilder.stateless();
        b.add(new TransportFilter());
        b.add(new HttpServerFilter());
        b.add(new SimpleResponseFilter());
        transport.setProcessor(b.build());
        return transport;
    }


    // ---------------------------------------------------------- Nested Classes


    private static final class ResponseCollectingFilter extends BaseFilter {

        private final CountDownLatch latch;
        final List<Integer> responses = new ArrayList<Integer>();

        ResponseCollectingFilter(CountDownLatch latch) {
            this.latch = latch;
        }

        @Override
        public NextAction handleRead(FilterChainContext ctx) throws IOException {

            final Object message = ctx.getMessage();
            if (message instanceof HttpContent) {
                final HttpContent content = (HttpContent) message;
                if (content.getContent().hasRemaining()) {
                    final String result = content.getContent().toStringContent();
                    responses.add(Integer.parseInt(result));
                    latch.countDown();
                }
            }
            return ctx.getStopAction();
        }

    } // END ResponseCollectingFilter


    private static final class SimpleResponseFilter extends BaseFilter {

        private final AtomicInteger counter = new AtomicInteger();


        @Override
        public NextAction handleRead(FilterChainContext ctx) throws IOException {

            final int count = counter.incrementAndGet();
            HttpRequestPacket request = (HttpRequestPacket) ((HttpContent) ctx.getMessage()).getHttpHeader();
            HttpResponsePacket response = request.getResponse();
            response.setStatus(HttpStatus.OK_200);
            final HttpContent content = response.httpContentBuilder().content(
                    Buffers.wrap(MemoryManager.DEFAULT_MEMORY_MANAGER,
                                 Integer.toString(count))).build();
            content.setLast(true);
            ctx.write(content);
            return ctx.getStopAction();

        }

    } // END SimpleResponseFilter

}
