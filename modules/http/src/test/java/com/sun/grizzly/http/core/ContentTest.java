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

import com.sun.grizzly.Connection;
import com.sun.grizzly.Grizzly;
import com.sun.grizzly.TransportFactory;
import com.sun.grizzly.WriteResult;
import com.sun.grizzly.filterchain.BaseFilter;
import com.sun.grizzly.filterchain.FilterChain;
import com.sun.grizzly.filterchain.FilterChainBuilder;
import com.sun.grizzly.filterchain.FilterChainContext;
import com.sun.grizzly.filterchain.NextAction;
import com.sun.grizzly.filterchain.TransportFilter;
import com.sun.grizzly.http.HttpClientFilter;
import com.sun.grizzly.http.HttpServerFilter;
import com.sun.grizzly.impl.FutureImpl;
import com.sun.grizzly.memory.MemoryUtils;
import com.sun.grizzly.nio.transport.TCPNIOConnection;
import com.sun.grizzly.nio.transport.TCPNIOTransport;
import com.sun.grizzly.utils.ChunkingFilter;
import java.io.IOException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import junit.framework.TestCase;

/**
 *
 * @author oleksiys
 */
public class ContentTest extends TestCase {
    private static final Logger logger = Grizzly.logger(ContentTest.class);

    public static int PORT = 8003;

    public void testExplicitContentLength() throws Exception {
        HttpRequest httpRequest = HttpRequest.builder().method("POST").protocol("HTTP/1.1").uri("/default").contentLength(10).build();
        HttpContent content = httpRequest.httpContentBuilder().content(MemoryUtils.wrap(TransportFactory.getInstance().getDefaultMemoryManager(), "1234567890")).build();

        doHttpRequestTest(content);
    }

    public void testHeaderContentLength() throws Exception {
        HttpRequest httpRequest = HttpRequest.builder().method("POST").protocol("HTTP/1.1").uri("/default").header("Content-Length", "10").build();
        HttpContent content = httpRequest.httpContentBuilder().content(MemoryUtils.wrap(TransportFactory.getInstance().getDefaultMemoryManager(), "1234567890")).build();

        doHttpRequestTest(content);
    }

    public void testSimpleChunked() throws Exception {
        HttpRequest httpRequest = HttpRequest.builder().method("POST").protocol("HTTP/1.1").uri("/default").chunked(true).build();
        HttpContent content = httpRequest.httpTrailerBuilder().content(MemoryUtils.wrap(TransportFactory.getInstance().getDefaultMemoryManager(), "1234567890")).build();

        doHttpRequestTest(content);
    }

    public void testSeveralChunked() throws Exception {
        HttpRequest httpRequest = HttpRequest.builder().method("POST").protocol("HTTP/1.1").uri("/default").chunked(true).build();
        HttpContent content1 = httpRequest.httpContentBuilder().content(MemoryUtils.wrap(TransportFactory.getInstance().getDefaultMemoryManager(), "1234567890")).build();
        HttpContent content2 = httpRequest.httpContentBuilder().content(MemoryUtils.wrap(TransportFactory.getInstance().getDefaultMemoryManager(), "0987654321")).build();
        HttpContent content3 = httpRequest.httpTrailerBuilder().content(MemoryUtils.wrap(TransportFactory.getInstance().getDefaultMemoryManager(), "final")).build();

        doHttpRequestTest(content1, content2, content3);
    }

    private void doHttpRequestTest(HttpContent... patternContentMessages)
            throws Exception {

        final FutureImpl<HttpPacket> parseResult = FutureImpl.create();

        Connection connection = null;

        FilterChainBuilder filterChainBuilder = FilterChainBuilder.singleton();
        filterChainBuilder.add(new TransportFilter());
        filterChainBuilder.add(new ChunkingFilter(2));
        filterChainBuilder.add(new HttpServerFilter());
        filterChainBuilder.add(new HTTPRequestMergerFilter(parseResult));
        FilterChain filterChain = filterChainBuilder.build();
        
        TCPNIOTransport transport = TransportFactory.getInstance().createTCPTransport();
        transport.setProcessor(filterChain);

        try {
            transport.bind(PORT);
            transport.start();

            Future<Connection> future = transport.connect("localhost", PORT);
            connection = (TCPNIOConnection) future.get(10, TimeUnit.SECONDS);
            assertTrue(connection != null);

            FilterChainBuilder clientFilterChainBuilder = FilterChainBuilder.singleton();
            clientFilterChainBuilder.add(new TransportFilter());
            clientFilterChainBuilder.add(new ChunkingFilter(2));
            clientFilterChainBuilder.add(new HttpClientFilter());
            FilterChain clientFilterChain = clientFilterChainBuilder.build();
            connection.setProcessor(clientFilterChain);

            for (HttpContent content : patternContentMessages) {
                Future<WriteResult> writeFuture = connection.write(content);
                WriteResult writeResult = writeFuture.get(10, TimeUnit.SECONDS);
            }

            HttpContent result = (HttpContent) parseResult.get(1000, TimeUnit.SECONDS);
            HttpHeader resultHeader = result.getHttpHeader();

            HttpContent mergedPatternContent = patternContentMessages[0];
            for(int i=1; i<patternContentMessages.length; i++) {
                mergedPatternContent = mergedPatternContent.append(patternContentMessages[i]);
            }
            
            HttpHeader patternHeader = mergedPatternContent.getHttpHeader();
            
            assertEquals(patternHeader.getContentLength(), resultHeader.getContentLength());
            assertEquals(patternHeader.isChunked(), resultHeader.isChunked());
            assertEquals(mergedPatternContent.getContent(), result.getContent());
            
        } finally {
            if (connection != null) {
                connection.close();
            }

            transport.stop();
            TransportFactory.getInstance().close();
        }
    }

    public class HTTPRequestMergerFilter extends BaseFilter {
        private final FutureImpl<HttpPacket> parseResult;

        public HTTPRequestMergerFilter(FutureImpl<HttpPacket> parseResult) {
            this.parseResult = parseResult;
        }

        @Override
        public NextAction handleRead(FilterChainContext ctx) throws IOException {
            HttpContent httpContent = (HttpContent) ctx.getMessage();
            HttpRequest httpRequest = (HttpRequest) httpContent.getHttpHeader();

            if (!httpContent.isLast()) {
                return ctx.getStopAction(httpContent);
            }

            parseResult.result(httpContent);
            return ctx.getStopAction();
        }
    }
}
