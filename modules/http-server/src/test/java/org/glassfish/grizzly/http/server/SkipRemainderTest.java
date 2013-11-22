/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2013 Oracle and/or its affiliates. All rights reserved.
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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.BlockingQueue;
import org.glassfish.grizzly.http.HttpRequestPacket.Builder;
import org.glassfish.grizzly.utils.DataStructures;
import org.glassfish.grizzly.memory.Buffers;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.SocketConnectorHandler;
import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.FilterChainBuilder;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.filterchain.TransportFilter;
import org.glassfish.grizzly.http.HttpClientFilter;
import org.glassfish.grizzly.http.HttpContent;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.impl.FutureImpl;
import org.glassfish.grizzly.impl.SafeFutureImpl;
import org.glassfish.grizzly.memory.ByteBufferWrapper;
import org.glassfish.grizzly.memory.MemoryManager;
import org.glassfish.grizzly.nio.transport.TCPNIOConnectorHandler;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.glassfish.grizzly.http.util.Header;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;


/**
 * Test how HttpServer skips HTTP packet remainder, if HttpHandler didn't read
 * the complete message.
 * 
 * @author Alexey Stashok
 */

@SuppressWarnings("unchecked")
public class SkipRemainderTest {
    public static final int PORT = 18892;

    private HttpServer gws;

    @Before
    public void before() throws Exception {
        ByteBufferWrapper.DEBUG_MODE = true;
        configureWebServer();
    }

    @After
    public void after() throws Exception {
        if (gws != null) {
            gws.shutdownNow();
        }
    }

    @Test
    public void testKeepAliveConnection() throws Exception {
        final BlockingQueue<Integer> transferQueue = DataStructures.getLTQInstance(Integer.class);
        
        final AtomicInteger counter = new AtomicInteger();
        final int contentSizeHalf = 32;
        final FutureImpl[] futures = {
            SafeFutureImpl.create(), SafeFutureImpl.create()
        };
        
        startWebServer(new HttpHandler() {
            @Override
            public void service(Request req, Response res)
                    throws Exception {
                InputStream is = req.getInputStream();
                try {
                    for (int i = 0; i < contentSizeHalf; i++) {
                        int c = is.read();
                        if (c != i) {
                            futures[counter.get()].failure(
                                    new IllegalStateException("Assertion failed on request#"
                                    + counter + ": expected=" + i + " got=" + c));
                            
                            return;
                        }
                    }

                    OutputStream os = res.getOutputStream();
                    os.write("OK".getBytes());
                    os.flush();
                    
                    futures[counter.get()].result("OK");
                    
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

        sendContentByHalfs(connection, content, transferQueue);
        counter.incrementAndGet();
        sendContentByHalfs(connection, content, transferQueue);
        
        for (FutureImpl future : futures) {
            future.get(10, TimeUnit.SECONDS);
        }        
    }

    @Test
    public void testNonKeepAliveConnection() throws Exception {
        final BlockingQueue<Integer> transferQueue = DataStructures.getLTQInstance(Integer.class);
        
        final int contentSizeHalf = 256 * 1024;
        final FutureImpl future = SafeFutureImpl.create();
        
        startWebServer(new HttpHandler() {
            @Override
            public void service(Request req, Response res)
                    throws Exception {
                InputStream is = req.getInputStream();
                try {
                    for (int i = 0; i < contentSizeHalf; i++) {
                        int c = is.read();
                        if (c != (i % 256)) {
                            future.failure(
                                    new IllegalStateException(
                                            "Assertion failed: expected=" + i +
                                    " got=" + c));
                            return;
                        }
                    }

                    res.setStatus(200, "FINE");
                    OutputStream os = res.getOutputStream();
                    os.write("OK".getBytes());
                    os.flush();
                    
                    future.result("OK");
                    
                } catch (Exception e) {
                    future.failure(e);
                }
            }
        });


        byte[] content = new byte[contentSizeHalf * 2];
        for (int i = 0; i < content.length; i++) {
            content[i] = (byte) i;
        }

        Future<Connection> connectFuture = connect("localhost", PORT, transferQueue);
        Connection connection = connectFuture.get(10, TimeUnit.SECONDS);

        sendContentByHalfs(connection, content, transferQueue, false);
        
        future.get(10, TimeUnit.SECONDS);
    }

    @Test
    // http://java.net/jira/browse/GRIZZLY-1113
    public void testSuspendResume() throws Exception {
        final BlockingQueue<Integer> transferQueue = DataStructures.getLTQInstance(Integer.class);
        
        final int contentSizeHalf = 256 * 1024;
        final FutureImpl future = SafeFutureImpl.create();
        
        final ExecutorService tp = Executors.newFixedThreadPool(1);
        
        final AtomicInteger calls = new AtomicInteger();
        try {
            startWebServer(new HttpHandler() {
                @Override
                public void service(final Request req, final Response res)
                        throws Exception {
                    calls.incrementAndGet();
                    
                    res.suspend();
                    tp.submit(new Runnable() {

                        @Override
                        public void run() {
                            try {
                                InputStream is = req.getInputStream();
                                for (int i = 0; i < contentSizeHalf; i++) {
                                    int c = is.read();
                                    if (c != (i % 256)) {
                                        future.failure(
                                                new IllegalStateException(
                                                        "Assertion failed: expected=" + i +
                                                " got=" + c));
                                        return;
                                    }
                                }

                                res.setStatus(200, "FINE");
                                OutputStream os = res.getOutputStream();
                                os.write("OK".getBytes());
                                os.flush();

                                future.result("OK");

                            } catch (Exception e) {
                                future.failure(e);
                            } finally {
                                res.resume();
                            }
                        }
                    });
                }
            });


            byte[] content = new byte[contentSizeHalf * 2];
            for (int i = 0; i < content.length; i++) {
                content[i] = (byte) i;
            }

            Future<Connection> connectFuture = connect("localhost", PORT, transferQueue);
            Connection connection = connectFuture.get(10, TimeUnit.SECONDS);

            sendContentByHalfs(connection, content, transferQueue, false);

            future.get(10, TimeUnit.SECONDS);
            
            Thread.sleep(500);
            
            assertEquals(1, calls.get());
        } finally {
            tp.shutdownNow();
        }
    }
    
    private void sendContentByHalfs(Connection connection, byte[] content,
            final BlockingQueue<Integer> transferQueue)
            throws Exception {
        sendContentByHalfs(connection, content, transferQueue, true);
    }
    
    private void sendContentByHalfs(Connection connection, byte[] content,
            final BlockingQueue<Integer> transferQueue, final boolean isKeepAlive)
            throws Exception {

        final MemoryManager mm = MemoryManager.DEFAULT_MEMORY_MANAGER;
        
        final int contentSizeHalf = content.length / 2;
        final Builder packetBuilder = HttpRequestPacket.builder()
           .method("POST").uri("/hello")
           .protocol("HTTP/1.1")
           .header("Host", "localhost")
           .contentLength(contentSizeHalf * 2);
        
        if (!isKeepAlive) {
            packetBuilder.header(Header.Connection, "close");
        }
        
        final HttpRequestPacket request1 = packetBuilder.build();

        connection.write(request1);

        final Buffer chunk1 = Buffers.wrap(mm, content, 0, contentSizeHalf);
        final Buffer chunk2 = Buffers.wrap(mm, content, contentSizeHalf, contentSizeHalf);

        final HttpContent httpContent11 = HttpContent.builder(request1)
                .content(chunk1)
                .build();
        
        connection.write(httpContent11).get(10, TimeUnit.SECONDS);

        Thread.sleep(200);
        
        final HttpContent httpContent12 = HttpContent.builder(request1)
                .content(chunk2)
                .last(true)
                .build();
        
        connection.write(httpContent12).get(10, TimeUnit.SECONDS);
        
        Thread.sleep(200);
        
        Integer responseSize = transferQueue.poll(10, TimeUnit.SECONDS);
        if (responseSize == null) throw new TimeoutException("No response from server");
        assertEquals("Unexpected response size", (Integer) 2, responseSize);
    }

    private Future<Connection> connect(String host, int port,
            BlockingQueue<Integer> transferQueue) throws Exception {

        final FilterChainBuilder builder = FilterChainBuilder.newInstance();
        builder.add(new TransportFilter());
        builder.add(new HttpClientFilter());
        builder.add(new HttpMessageFilter(transferQueue));
        
        SocketConnectorHandler connectorHandler = TCPNIOConnectorHandler.builder(
                gws.getListener("grizzly").getTransport())
                .filterChain(builder.build())
                .build();
        
        return connectorHandler.connect(host, port);
    }

    private void configureWebServer() throws Exception {
        gws = new HttpServer();
        final NetworkListener listener =
                new NetworkListener("grizzly",
                                    NetworkListener.DEFAULT_NETWORK_HOST,
                                    PORT);
        listener.setMaxPendingBytes(-1);
        gws.addListener(listener);
    }

    private void startWebServer(final HttpHandler httpHandler) throws Exception {
        gws.getServerConfiguration().addHttpHandler(httpHandler);
        gws.start();
    }

    private static class HttpMessageFilter extends BaseFilter {
        private final BlockingQueue<Integer> transferQueue;

        public HttpMessageFilter(BlockingQueue<Integer> transferQueue) {
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
