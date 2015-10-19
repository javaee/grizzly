/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2015 Oracle and/or its affiliates. All rights reserved.
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
package org.glassfish.grizzly.servlet;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.FilterChainBuilder;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.filterchain.TransportFilter;
import org.glassfish.grizzly.http.HttpClientFilter;
import org.glassfish.grizzly.http.HttpContent;
import org.glassfish.grizzly.http.HttpPacket;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.http.Method;
import org.glassfish.grizzly.http.Protocol;
import org.glassfish.grizzly.impl.FutureImpl;
import org.glassfish.grizzly.impl.SafeFutureImpl;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import org.glassfish.grizzly.nio.transport.TCPNIOTransportBuilder;
import org.glassfish.grizzly.utils.ChunkingFilter;

/**
 * Simple helper for sending a request
 *
 * @author Marc Arens <marc.arens@open-xchange.com>
 */
public class ClientUtil {
    
    @SuppressWarnings({"unchecked"})
    public static HttpPacket createRequest(String uri, int port, Map<String, String> headers) {

        HttpRequestPacket.Builder b = HttpRequestPacket.builder();
        b.method(Method.GET).protocol(Protocol.HTTP_1_1).uri(uri).header("Host", "localhost:" + port);
        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                b.header(entry.getKey(), entry.getValue());
            }
        }

        return b.build();
    }

    @SuppressWarnings("unchecked")
    public static HttpContent sendRequest(final HttpPacket request,
            final int timeout, final int port) throws Exception {

        final TCPNIOTransport clientTransport =
                TCPNIOTransportBuilder.newInstance().build();
        try {
            final FutureImpl<HttpContent> testResultFuture = SafeFutureImpl.create();

            FilterChainBuilder clientFilterChainBuilder = FilterChainBuilder.stateless();
            clientFilterChainBuilder.add(new TransportFilter());
            clientFilterChainBuilder.add(new ChunkingFilter(5));
            clientFilterChainBuilder.add(new HttpClientFilter());
            clientFilterChainBuilder.add(new ClientFilter(testResultFuture));
            clientTransport.setProcessor(clientFilterChainBuilder.build());

            clientTransport.start();

            Future<Connection> connectFuture = clientTransport.connect("localhost", port);
            Connection connection = null;
            try {
                connection = connectFuture.get(timeout, TimeUnit.SECONDS);
                connection.write(request);
                return testResultFuture.get(timeout, TimeUnit.SECONDS);
            } finally {
                // Close the client connection
                if (connection != null) {
                    connection.closeSilently();
                }
            }
        } finally {
            clientTransport.shutdownNow();
        }
    }

    private static class ClientFilter extends BaseFilter {
        private final static Logger logger = Grizzly.logger(ClientFilter.class);

        private final FutureImpl<HttpContent> testFuture;

        // -------------------------------------------------------- Constructors


        public ClientFilter(FutureImpl<HttpContent> testFuture) {

            this.testFuture = testFuture;

        }


        // ------------------------------------------------- Methods from Filter

        @Override
        public NextAction handleRead(FilterChainContext ctx)
                throws IOException {

            // Cast message to a HttpContent
            final HttpContent httpContent = ctx.getMessage();

            logger.log(Level.FINE, "Got HTTP response chunk");

            // Get HttpContent's Buffer
            final Buffer buffer = httpContent.getContent();

            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, "HTTP content size: {0}", buffer.remaining());
            }

            if (!httpContent.isLast()) {
                return ctx.getStopAction(httpContent);
            }

            testFuture.result(httpContent);

            return ctx.getStopAction();
        }

        @Override
        public NextAction handleClose(FilterChainContext ctx)
                throws IOException {
            close();
            return ctx.getStopAction();
        }

        private void close() throws IOException {

            if (!testFuture.isDone()) {
                //noinspection ThrowableInstanceNeverThrown
                testFuture.failure(new IOException("Connection was closed"));
            }

        }

    } // END ClientFilter
}
