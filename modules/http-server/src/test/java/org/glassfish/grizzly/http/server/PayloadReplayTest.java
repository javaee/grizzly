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

package org.glassfish.grizzly.http.server;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.SocketConnectorHandler;
import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.FilterChainBuilder;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.filterchain.TransportFilter;
import org.glassfish.grizzly.http.HttpClientFilter;
import org.glassfish.grizzly.http.HttpContent;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.http.Method;
import org.glassfish.grizzly.http.Protocol;
import org.glassfish.grizzly.http.util.Header;
import org.glassfish.grizzly.memory.Buffers;
import org.glassfish.grizzly.memory.MemoryManager;
import org.glassfish.grizzly.nio.transport.TCPNIOConnectorHandler;
import org.glassfish.grizzly.utils.Charsets;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * Test payload replay feature.
 * 
 * @author Alexey Stashok
 */
public class PayloadReplayTest {
    private static final int PORT = 18905;
    private static final Logger LOGGER = Grizzly.logger(PayloadReplayTest.class);

    private HttpServer httpServer;
    
    @Before
    public void before() throws Exception {
        httpServer = createServer();
    }

    @After
    public void after() throws Exception {
        if (httpServer != null) {
            httpServer.shutdownNow();
        }
    }
       
    /**
     * Check basic replay mechanism
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testBasicReplay() throws Exception {
        final byte[] payloadToReplay = new byte[] {11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1, 0};
        
        startServer(new HttpHandler() {

            @Override
            public void service(Request request, Response response) throws Exception {
                try {
                    request.replayPayload(Buffers.wrap(
                            request.getContext().getMemoryManager(), payloadToReplay));
                    
                    response.getWriter().write("Replay should have caused IllegalStateException");
                    return;
                } catch (IllegalStateException expectedException) {
                }
                
                final InputStream is = request.getInputStream();
                while (is.read() != -1);
                
                request.replayPayload(Buffers.wrap(
                        request.getContext().getMemoryManager(), payloadToReplay));
                
                byte[] buffer = new byte[payloadToReplay.length * 2];
                
                int len = 0;
                while (true) {
                    int readNow = is.read(buffer, len, buffer.length - len);
                    
                    if (readNow == -1) {
                        break;
                    }
                    
                    len += readNow;
                }
                
                response.getOutputStream().write(buffer, 0, len);
            }
        });
        
        final BlockingQueue<HttpContent> resultQueue =
                new LinkedBlockingQueue<HttpContent>();
        
        final Connection client = openClient(resultQueue);
        try {
            HttpRequestPacket request = HttpRequestPacket.builder()
                    .method(Method.POST)
                    .uri("/")
                    .protocol(Protocol.HTTP_1_1)
                    .header(Header.Host, "localhost:" + PORT)
                    .contentLength(10)
                    .build();
            
            client.write(request);
            Thread.sleep(20);
            final byte[] payload = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
            
            final MemoryManager mm = client.getMemoryManager();
            HttpContent payload1 = HttpContent.builder(request)
                    .content(Buffers.wrap(mm, payload, 0, 5))
                    .build();
            HttpContent payload2 = HttpContent.builder(request)
                    .content(Buffers.wrap(mm, payload, 5, 5))
                    .build();
            
            client.write(payload1);
            Thread.sleep(20);
            client.write(payload2);
            
            final HttpContent result = resultQueue.poll(10, TimeUnit.SECONDS);
            final Buffer responseBuffer = result.getContent();
            
            final byte[] responseArray = new byte[responseBuffer.remaining()];
            responseBuffer.get(responseArray);
            
            Assert.assertArrayEquals(payloadToReplay, responseArray);
        } finally {
            if (client != null) {
                client.closeSilently();
            }
        }        
    }
    
    /**
     * Check post parameters replay.
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testParametersReplay() throws Exception {
        final String argRus = "\u0430\u0440\u0433\u0443\u043c\u0435\u043d\u0442";
        final String arg1Rus = argRus + "1";
        final String arg2Rus = argRus + "2";
        
        final String valueRus = "\u0437\u043d\u0430\u0447\u0435\u043d\u0438\u0435";
        final String value1Rus = valueRus + "1";
        final String value2Rus = valueRus + "2";
        
        final String rusParam = arg1Rus + "=" + value1Rus + "&" + arg2Rus + "=" + value2Rus;
        final String rusParamEncoded = URLEncoder.encode(arg1Rus, "UTF-8") + "=" + URLEncoder.encode(value1Rus, "UTF-8") + "&" + URLEncoder.encode(arg2Rus, "UTF-8") + "=" + URLEncoder.encode(value2Rus, "UTF-8");

        final byte[] payloadToReplay = rusParamEncoded.getBytes(Charsets.ASCII_CHARSET);
        
        startServer(new HttpHandler() {

            @Override
            public void service(Request request, Response response) throws Exception {
                try {
                    request.replayPayload(Buffers.wrap(
                            request.getContext().getMemoryManager(), payloadToReplay));
                    
                    response.getWriter().write("Replay should have caused IllegalStateException");
                    return;
                } catch (IllegalStateException expectedException) {
                }
                
                String value1 = request.getParameter(arg1Rus);
                String value2 = request.getParameter(arg2Rus);
                
                if (value1 != null || value2 != null) {
                    response.getWriter().write("Parameter is unexpectedly found. value1= " + value1 + " value2=" + value2);
                    return;
                }
                
                request.replayPayload(Buffers.wrap(
                        request.getContext().getMemoryManager(), payloadToReplay));
                request.setCharacterEncoding("UTF-8");
                
                value1 = request.getParameter(arg1Rus);
                value2 = request.getParameter(arg2Rus);
                
                if (!value1Rus.equals(value1) || !value2Rus.equals(value2)) {
                    response.getWriter().write("Parameter values don't match. value1= " + value1 + " value2=" + value2);
                    return;
                }
                
                response.setCharacterEncoding("UTF-8");
                response.getWriter().write(value1 + value2);
            }
        });
        
        final BlockingQueue<HttpContent> resultQueue =
                new LinkedBlockingQueue<HttpContent>();
        
        final Connection client = openClient(resultQueue);
        try {
            HttpRequestPacket request = HttpRequestPacket.builder()
                    .method(Method.POST)
                    .uri("/")
                    .protocol(Protocol.HTTP_1_1)
                    .header(Header.Host, "localhost:" + PORT)
                    .contentLength(payloadToReplay.length)
                    .contentType("application/x-www-form-urlencoded")
                    .build();
            
            client.write(request);
            Thread.sleep(20);
            
            final MemoryManager mm = client.getMemoryManager();
            HttpContent payload = HttpContent.builder(request)
                    .content(Buffers.wrap(mm, payloadToReplay))
                    .build();
            
            client.write(payload);
            
            final HttpContent result = resultQueue.poll(10, TimeUnit.SECONDS);
            final Buffer responseBuffer = result.getContent();
            
            Assert.assertEquals(value1Rus + value2Rus, responseBuffer.toStringContent(Charsets.UTF8_CHARSET));
        } finally {
            if (client != null) {
                client.closeSilently();
            }
        }        
    }
    
    private Connection openClient(BlockingQueue<HttpContent> resultQueue)
            throws Exception {
        final FilterChainBuilder builder = FilterChainBuilder.stateless();
        builder.add(new TransportFilter());

        builder.add(new HttpClientFilter());
        builder.add(new ClientFilter(resultQueue));

        SocketConnectorHandler connectorHandler = TCPNIOConnectorHandler.builder(
                httpServer.getListener("test").getTransport())
                .processor(builder.build())
                .build();

        Future<Connection> connectFuture = connectorHandler.connect("localhost", PORT);
        return connectFuture.get(10, TimeUnit.SECONDS);
    }
    
    private static HttpServer createServer()
            throws Exception {
        final HttpServer server = new HttpServer();
        final NetworkListener listener = 
                new NetworkListener("test", 
                                    NetworkListener.DEFAULT_NETWORK_HOST, 
                                    PORT);
        
        server.addListener(listener);
        
        return server;
    }
    
    private void startServer(HttpHandler httpHandler) throws IOException {
        httpServer.getServerConfiguration().addHttpHandler(httpHandler, "/");
        httpServer.start();
    }
    
    private static class ClientFilter extends BaseFilter {

        private final BlockingQueue<HttpContent> resultQueue;

        public ClientFilter(BlockingQueue<HttpContent> resultQueue) {
            this.resultQueue = resultQueue;
        }

        @Override
        public NextAction handleRead(FilterChainContext ctx) throws IOException {
            final HttpContent httpContent = ctx.getMessage();
            if (!httpContent.isLast()) {
                return ctx.getStopAction(httpContent);
            }

            resultQueue.add(httpContent);

            return ctx.getStopAction();
        }
    }    
}
