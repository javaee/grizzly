/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.grizzly.web;

import com.sun.grizzly.Buffer;
import com.sun.grizzly.Connection;
import com.sun.grizzly.SocketConnectorHandler;
import com.sun.grizzly.TransportFactory;
import com.sun.grizzly.filterchain.BaseFilter;
import com.sun.grizzly.filterchain.FilterChainBuilder;
import com.sun.grizzly.filterchain.FilterChainContext;
import com.sun.grizzly.filterchain.NextAction;
import com.sun.grizzly.filterchain.TransportFilter;
import com.sun.grizzly.http.HttpClientFilter;
import com.sun.grizzly.http.HttpContent;
import com.sun.grizzly.http.HttpRequestPacket;
import com.sun.grizzly.http.server.GrizzlyListener;
import com.sun.grizzly.http.server.GrizzlyRequest;
import com.sun.grizzly.http.server.GrizzlyResponse;
import com.sun.grizzly.http.server.GrizzlyWebServer;
import com.sun.grizzly.http.server.GrizzlyAdapter;
import com.sun.grizzly.impl.FutureImpl;
import com.sun.grizzly.impl.SafeFutureImpl;
import com.sun.grizzly.memory.ByteBufferWrapper;
import com.sun.grizzly.memory.MemoryManager;
import com.sun.grizzly.memory.MemoryUtils;
import com.sun.grizzly.nio.transport.TCPNIOConnectorHandler;
import com.sun.grizzly.utils.LinkedTransferQueue;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;


/**
 * Test how GrizzlyWebServer skips HTTP packet remainder, if Adapter didn't read
 * the complete message.
 * 
 * @author Alexey Stashok
 */

public class SkipRemainderTest {
    public static final int PORT = 18892;

    private GrizzlyWebServer gws;

    @Before
    public void before() throws Exception {
        ByteBufferWrapper.DEBUG_MODE = true;
        configureWebServer();
    }

    @After
    public void after() throws Exception {
        if (gws != null) {
            gws.stop();
        }
    }

//    @Ignore
    @Test
    public void testKeepAliveConnection() throws Exception {
        final LinkedTransferQueue<Integer> transferQueue = new LinkedTransferQueue<Integer>();
        
        final AtomicInteger counter = new AtomicInteger();
        final int contentSizeHalf = 32;
        final FutureImpl[] futures = {
            SafeFutureImpl.create(), SafeFutureImpl.create()
        };
        
        startWebServer(new GrizzlyAdapter() {
            @Override
            public void service(GrizzlyRequest req, GrizzlyResponse res)
                    throws Exception {
                InputStream is = req.getInputStream(true);
                try {
                    for (int i = 0; i < contentSizeHalf; i++) {
                        int c = is.read();
                        if (c != i) {
                            futures[counter.get()].failure(
                                    new IllegalStateException("Assertion failed on request#"
                                    + counter + ": expected=" + i + " got=" + c));
                        }
                    }

                    OutputStream os = res.getOutputStream();
                    os.write("OK".getBytes());
                    os.flush();
                } catch (Exception e) {
                    futures[counter.get()].failure(e);
                }
            }
        });


        byte[] content = new byte[contentSizeHalf * 2];
        for (int i = 0; i < content.length; i++) {
            content[i] = (byte) i;
        }

        Future<Connection> connectFuture = connect("localhost", PORT, transferQueue);
        Connection connection = connectFuture.get(10, TimeUnit.SECONDS);

        try {
            sendContentByHalfs(connection, content, transferQueue);
            sendContentByHalfs(connection, content, transferQueue);
        } catch (Exception e) {
            for (FutureImpl future : futures) {
                future.get(0, TimeUnit.SECONDS);
            }
        }
    }

    private void sendContentByHalfs(Connection connection, byte[] content,
            final LinkedTransferQueue<Integer> transferQueue)
            throws Exception, InterruptedException {

        final MemoryManager mm = TransportFactory.getInstance().getDefaultMemoryManager();
        
        final int contentSizeHalf = content.length / 2;
        
        final HttpRequestPacket request1 = HttpRequestPacket.builder()
                .method("POST").uri("/hello")
                .protocol("HTTP/1.1")
                .header("Host", "localhost")
                .contentLength(contentSizeHalf * 2).build();

        connection.write(request1);

        final Buffer chunk1 = MemoryUtils.wrap(mm, content, 0, contentSizeHalf);
        final Buffer chunk2 = MemoryUtils.wrap(mm, content, contentSizeHalf, contentSizeHalf);

        final HttpContent httpContent11 = HttpContent.builder(request1)
                .content(chunk1)
                .build();
        
        connection.write(httpContent11);

        Integer responseSize = transferQueue.poll(10, TimeUnit.SECONDS);
        if (responseSize == null) throw new TimeoutException("No response from server");
        assertEquals("Unexpected response size", (Integer) 2, responseSize);

        final HttpContent httpContent12 = HttpContent.builder(request1)
                .content(chunk2)
                .last(true)
                .build();
        
        connection.write(httpContent12);
    }

    private Future<Connection> connect(String host, int port,
            LinkedTransferQueue<Integer> transferQueue) throws Exception {

        final FilterChainBuilder builder = FilterChainBuilder.stateless();
        builder.add(new TransportFilter());
        builder.add(new HttpClientFilter());
        builder.add(new HttpMessageFilter(transferQueue));
        
        SocketConnectorHandler connectorHandler = new TCPNIOConnectorHandler(
                gws.getListener("grizzly").getTransport());

        connectorHandler.setProcessor(builder.build());
        return connectorHandler.connect(host, port);
    }

    private void configureWebServer() throws Exception {
        gws = new GrizzlyWebServer();
        final GrizzlyListener listener =
                new GrizzlyListener("grizzly",
                                    GrizzlyListener.DEFAULT_NETWORK_HOST,
                                    PORT);
        gws.addListener(listener);
    }

    private void startWebServer(GrizzlyAdapter adapter) throws Exception {
        gws.getServerConfiguration().addGrizzlyAdapter(adapter);
        gws.start();
    }

    private static class HttpMessageFilter extends BaseFilter {
        private final LinkedTransferQueue<Integer> transferQueue;

        public HttpMessageFilter(LinkedTransferQueue<Integer> transferQueue) {
            this.transferQueue = transferQueue;
        }

        @Override
        public NextAction handleRead(FilterChainContext ctx) throws IOException {
            final HttpContent content = (HttpContent) ctx.getMessage();
            if (!content.isLast()) {
                return ctx.getStopAction(content);
            }

            transferQueue.offer(content.getContent().remaining());

            return ctx.getStopAction();
        }
    }
}
