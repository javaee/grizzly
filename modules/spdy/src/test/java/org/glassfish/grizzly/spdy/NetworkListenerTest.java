/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2013-2014 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.spdy;

import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.glassfish.grizzly.Connection;

import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.FilterChain;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.http.HttpContent;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.http.HttpResponsePacket;
import org.glassfish.grizzly.http.server.HttpHandler;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.grizzly.http.server.NetworkListener;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.http.server.Response;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import org.glassfish.grizzly.nio.transport.TCPNIOTransportBuilder;
import org.glassfish.grizzly.utils.Charsets;
import org.junit.Test;

import static org.glassfish.grizzly.spdy.AbstractSpdyTest.createClientFilterChainAsBuilder;
import static org.junit.Assert.*;
/**
 * {@link NetworkListener} tests.
 * 
 * @author Alexey Stashok
 */
@SuppressWarnings("unchecked")
public class NetworkListenerTest extends AbstractSpdyTest {
    public static final int PORT = 18897;

    @Test
    public void testGracefulShutdown() throws Exception {
        final String msg = "Hello World";
        final byte[] msgBytes = msg.getBytes(Charsets.UTF8_CHARSET);
        
        final BlockingQueue<HttpContent> clientInQueue =
                new LinkedBlockingQueue<HttpContent>();
        
        final FilterChain filterChain =
                createClientFilterChainAsBuilder(SpdyVersion.SPDY_3_1,
                        SpdyMode.PLAIN, true,
                new BaseFilter() {
            @Override
            public NextAction handleRead(FilterChainContext ctx) throws IOException {
                final HttpContent httpContent = ctx.getMessage();
                clientInQueue.add(httpContent);

                return ctx.getStopAction();
            }
        }).build();
        
        final TCPNIOTransport clientTransport =
                TCPNIOTransportBuilder.newInstance()
                .setProcessor(filterChain)
                .build();

        final HttpServer server = createServer(null, PORT, SpdyVersion.SPDY_3_1,
                SpdyMode.PLAIN, true,
                HttpHandlerRegistration.of(new HttpHandler() {
                    @Override
                    public void service(Request request, Response response) throws Exception {
                        response.setContentType("text/plain");
                        response.setCharacterEncoding(Charsets.UTF8_CHARSET.name());
                        response.setContentLength(msgBytes.length);
                        response.flush();
                        Thread.sleep(2000);
                        response.getOutputStream().write(msgBytes);
                    }
                }, "/path"));

        try {
            server.start();
            clientTransport.start();

            Future<Connection> connectFuture = clientTransport.connect("localhost", PORT);
            Connection connection = null;
            try {
                connection = connectFuture.get(10, TimeUnit.SECONDS);
                final HttpRequestPacket requestPacket =
                        (HttpRequestPacket) createRequest(PORT, "GET",
                        null, null, null);
                connection.write(requestPacket);
                
                HttpContent response = clientInQueue.poll(10,
                        TimeUnit.SECONDS);
                assertNotNull("Response can't be null", response);
                
                final HttpResponsePacket responseHeader =
                        (HttpResponsePacket) response.getHttpHeader();
                
                assertEquals(200, responseHeader.getStatus());
                assertEquals(msgBytes.length, responseHeader.getContentLength());
                
                final Future<HttpServer> gracefulFuture = server.shutdown();

                while (!response.isLast()) {
                    final HttpContent chunk = clientInQueue.poll(10, TimeUnit.SECONDS);
                    assertNotNull("Chunk can't be null", chunk);
                    
                    response = response.append(chunk);
                };

                final String content = response.getContent().toStringContent(
                        Charsets.UTF8_CHARSET);
                assertEquals(msg, content);
                
                assertNotNull(gracefulFuture.get(5, TimeUnit.SECONDS));
            } finally {
                // Close the client connection
                if (connection != null) {
                    connection.closeSilently();
                }
            }            
        } finally {
            clientTransport.shutdownNow();
            server.shutdownNow();
        }
    }    
}
