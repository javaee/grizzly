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

package org.glassfish.grizzly.http2;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.WriteHandler;
import org.glassfish.grizzly.Writer;
import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.FilterChain;
import org.glassfish.grizzly.filterchain.FilterChainBuilder;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.filterchain.TransportFilter;
import org.glassfish.grizzly.http.HttpContent;
import org.glassfish.grizzly.http.HttpHeader;
import org.glassfish.grizzly.http.HttpPacket;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.http.HttpResponsePacket;
import org.glassfish.grizzly.http.Protocol;
import org.glassfish.grizzly.http.io.NIOOutputStream;
import org.glassfish.grizzly.http.io.NIOWriter;
import org.glassfish.grizzly.http.server.AddOn;
import org.glassfish.grizzly.http.server.HttpHandler;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.grizzly.http.server.NetworkListener;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.http.server.Response;
import org.glassfish.grizzly.impl.FutureImpl;
import org.glassfish.grizzly.impl.SafeFutureImpl;
import org.glassfish.grizzly.memory.Buffers;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import org.glassfish.grizzly.nio.transport.TCPNIOTransportBuilder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import static org.junit.Assert.*;

@RunWith(Parameterized.class)
public class NIOOutputSinksTest extends AbstractHttp2Test {
    private static final Logger LOGGER = Grizzly.logger(NIOOutputSinksTest.class);
    private static final int PORT = 9339;

    private final boolean isSecure;
    
    public NIOOutputSinksTest(final boolean isSecure) {
        this.isSecure = isSecure;
    }
    
    @Parameterized.Parameters
    public static Collection<Object[]> isSecure() {
        return AbstractHttp2Test.isSecure();
    }
    
    @Test
    public void testBinaryOutputSink() throws Exception {
        final int singleMessageSize = 256000;
        final int maxWindowSize = singleMessageSize * 2;
        
        final FutureImpl<Integer> parseResult = SafeFutureImpl.create();
        
        FilterChainBuilder filterChainBuilder =
                createClientFilterChainAsBuilder(isSecure);
        filterChainBuilder.add(new BaseFilter() {

            private int bytesRead;
            
            @Override
            public NextAction handleConnect(FilterChainContext ctx) throws IOException {
                // Build the HttpRequestPacket, which will be sent to a server
                // We construct HTTP request version 1.1 and specifying the URL of the
                // resource we want to download
                final HttpRequestPacket httpRequest = HttpRequestPacket.builder().method("GET")
                        .uri("/path").protocol(Protocol.HTTP_1_1)
                        .header("Host", "localhost:" + PORT).build();

                // Write the request asynchronously
                ctx.write(HttpContent.builder(httpRequest)
                        .content(Buffers.EMPTY_BUFFER)
                        .last(true)
                        .build());

                // Return the stop action, which means we don't expect next filter to process
                // connect event
                return ctx.getStopAction();
            }

            @Override
            public NextAction handleRead(FilterChainContext ctx) throws IOException {
                HttpContent message = (HttpContent) ctx.getMessage();
                Buffer b = message.getContent();
                final int remaining = b.remaining();
                
                if (b.hasRemaining()) {
                    try {
                        check(b.toStringContent(), bytesRead % singleMessageSize, remaining, singleMessageSize);
                    } catch (Exception e) {
                        parseResult.failure(e);
                    }
                    
                    bytesRead += remaining;
                }
                
                if (message.isLast()) {
                    parseResult.result(bytesRead);
                }
                return ctx.getStopAction();
            }
        });


        final TCPNIOTransport clientTransport = TCPNIOTransportBuilder.newInstance().build();
        
        final FilterChain clientChain = filterChainBuilder.build();
        setInitialHttp2WindowSize(clientChain, maxWindowSize);
        
        clientTransport.setProcessor(clientChain);
        final AtomicInteger writeCounter = new AtomicInteger();
        final AtomicBoolean callbackInvoked = new AtomicBoolean(false);
        
        final HttpHandler httpHandler = new HttpHandler() {

            @Override
            public void service(final Request request, final Response response) throws Exception {
                
                clientTransport.pause();
                response.setContentType("text/plain");
                final NIOOutputStream out = response.getNIOOutputStream();

                while (out.canWrite()) {
                    byte[] b = new byte[singleMessageSize];
                    fill(b);
                    writeCounter.addAndGet(b.length);
                    out.write(b);
                    Thread.yield();
                }
                
                response.suspend();

                final Connection c = request.getContext().getConnection();

                out.notifyCanWrite(new WriteHandler() {
                    @Override
                    public void onWritePossible() {
                        callbackInvoked.compareAndSet(false, true);
                        clientTransport.pause();

                        assertTrue(out.canWrite());

                        clientTransport.resume();
                        try {
                            byte[] b = new byte[singleMessageSize];
                            fill(b);
                            writeCounter.addAndGet(b.length);
                            out.write(b);
                            out.flush();
                            out.close();
                        } catch (IOException ioe) {
                            ioe.printStackTrace();
                        }
                        response.resume();

                    }

                    @Override
                    public void onError(Throwable t) {
                        response.resume();
                        throw new RuntimeException(t);
                    }
                });

                clientTransport.resume();
            }

        };

        final HttpServer server = createWebServer(httpHandler);
        http2Addon.setInitialWindowSize(maxWindowSize);

        try {
            server.start();
            
            clientTransport.start();

            Future<Connection> connectFuture = clientTransport.connect("localhost", PORT);
            Connection connection = null;
            try {
                connection = connectFuture.get(10, TimeUnit.SECONDS);
                int length = parseResult.get(30, TimeUnit.SECONDS);
                assertEquals(writeCounter.get(), length);
                assertTrue(callbackInvoked.get());
            } finally {
                LOGGER.log(Level.INFO, "Written {0}", writeCounter);
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
    
    @Test
    public void testBlockingBinaryOutputSink() throws Exception {
        final int bufferSize = 4096;
        final int maxWindowSize = bufferSize * 3 / 4;
        final int bytesToSend = bufferSize * 1024 * 4;

        final FutureImpl<Integer> parseResult = SafeFutureImpl.create();
        FilterChainBuilder filterChainBuilder =
                createClientFilterChainAsBuilder(isSecure);
        filterChainBuilder.add(new BaseFilter() {

            private int bytesRead;
            
            @Override
            public NextAction handleConnect(FilterChainContext ctx) throws IOException {
                // Build the HttpRequestPacket, which will be sent to a server
                // We construct HTTP request version 1.1 and specifying the URL of the
                // resource we want to download
                final HttpRequestPacket httpRequest = HttpRequestPacket.builder().method("GET")
                        .uri("/path").protocol(Protocol.HTTP_1_1)
                        .header("Host", "localhost:" + PORT).build();

                // Write the request asynchronously
                ctx.write(HttpContent.builder(httpRequest)
                        .content(Buffers.EMPTY_BUFFER)
                        .last(true)
                        .build());

                // Return the stop action, which means we don't expect next filter to process
                // connect event
                return ctx.getStopAction();
            }

            @Override
            public NextAction handleRead(FilterChainContext ctx) throws IOException {
                HttpContent message = (HttpContent) ctx.getMessage();
                Buffer b = message.getContent();
                final int remaining = b.remaining();
                
                if (b.hasRemaining()) {
                    try {
                        check(b.toStringContent(), bytesRead % bufferSize, remaining, bufferSize);
                    } catch (Exception e) {
                        parseResult.failure(e);
                    }
                    
                    bytesRead += remaining;
                }
                
                if (message.isLast()) {
                    parseResult.result(bytesRead);
                }
                return ctx.getStopAction();
            }
        });


        final TCPNIOTransport clientTransport = TCPNIOTransportBuilder.newInstance().build();
        final FilterChain clientChain = filterChainBuilder.build();
        setInitialHttp2WindowSize(clientChain, maxWindowSize);
        
        clientTransport.setProcessor(clientChain);

        final AtomicInteger writeCounter = new AtomicInteger();
        
        final HttpHandler httpHandler = new HttpHandler() {

            @Override
            public void service(final Request request, final Response response) throws Exception {
                response.setContentType("text/plain");
                final NIOOutputStream out = response.getNIOOutputStream();

                int sent = 0;
                
                byte[] b = new byte[bufferSize];
                fill(b);
                try {
                    while (sent < bytesToSend) {
                        out.write(b);
                        sent += bufferSize;
                        writeCounter.addAndGet(bufferSize);
                    }
                } catch (Throwable e) {
                    LOGGER.log(Level.SEVERE, "Unexpected error", e);
                    parseResult.failure(new IllegalStateException("Error", e));
                }
            }
        };

        final HttpServer server = createWebServer(httpHandler);
        http2Addon.setInitialWindowSize(maxWindowSize);

        try {
            server.start();
            
            clientTransport.start();

            Future<Connection> connectFuture = clientTransport.connect("localhost", PORT);
            Connection connection = null;
            try {
                connection = connectFuture.get(10, TimeUnit.SECONDS);
                int length = parseResult.get(60, TimeUnit.SECONDS);
                assertEquals("Received " + length + " bytes", bytesToSend, length);
            } finally {
                LOGGER.log(Level.INFO, "Written {0}", writeCounter);
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
    
    @Test
    public void testCharacterOutputSink() throws Exception {
        final int singleMessageSize = 256000;
        final int maxWindowSize = singleMessageSize * 2;
        
        final FutureImpl<Integer> parseResult = SafeFutureImpl.create();
        
        FilterChainBuilder filterChainBuilder =
                createClientFilterChainAsBuilder(isSecure);
        filterChainBuilder.add(new BaseFilter() {

            private int bytesRead;

            @Override
            public NextAction handleConnect(FilterChainContext ctx) throws IOException {
                // Build the HttpRequestPacket, which will be sent to a server
                // We construct HTTP request version 1.1 and specifying the URL of the
                // resource we want to download
                final HttpRequestPacket httpRequest = HttpRequestPacket.builder().method("GET")
                        .uri("/path").protocol(Protocol.HTTP_1_1)
                        .header("Host", "localhost:" + PORT).build();

                // Write the request asynchronously
                ctx.write(HttpContent.builder(httpRequest)
                        .content(Buffers.EMPTY_BUFFER)
                        .last(true)
                        .build());

                // Return the stop action, which means we don't expect next filter to process
                // connect event
                return ctx.getStopAction();
            }

            @Override
            public NextAction handleRead(FilterChainContext ctx) throws IOException {
                HttpContent message = (HttpContent) ctx.getMessage();
                Buffer b = message.getContent();
                final int remaining = b.remaining();
                
                if (b.hasRemaining()) {
                    try {
                        check(b.toStringContent(), bytesRead % singleMessageSize, remaining, singleMessageSize);
                    } catch (Exception e) {
                        parseResult.failure(e);
                    }
                    
                    bytesRead += remaining;
                }
                
                if (message.isLast()) {
                    parseResult.result(bytesRead);
                }
                return ctx.getStopAction();
            }
        });


        final TCPNIOTransport clientTransport = TCPNIOTransportBuilder.newInstance().build();
        final FilterChain clientChain = filterChainBuilder.build();
        setInitialHttp2WindowSize(clientChain, maxWindowSize);
        
        clientTransport.setProcessor(clientChain);
        
        final AtomicInteger writeCounter = new AtomicInteger();
        final AtomicBoolean callbackInvoked = new AtomicBoolean(false);
        final HttpHandler httpHandler = new HttpHandler() {

            @Override
            public void service(final Request request, final Response response) throws Exception {
                clientTransport.pause();

                response.setContentType("text/plain");
                final NIOWriter out = response.getNIOWriter();
                Connection c = request.getContext().getConnection();

                while (out.canWrite()) {
                    char[] data = new char[singleMessageSize];
                    fill(data);
                    writeCounter.addAndGet(data.length);
                    out.write(data);
                    Thread.yield();
                }                

                response.suspend();
                notifyCanWrite(c, out, response);
                
                clientTransport.resume();
            }

            private void notifyCanWrite(final Connection c,
                    final NIOWriter out,
                    final Response response) {

                out.notifyCanWrite(new WriteHandler() {

                    @Override
                    public void onWritePossible() {
                        callbackInvoked.compareAndSet(false, true);
                        clientTransport.pause();
                        assertTrue(out.canWrite());
                        clientTransport.resume();
                        try {
                            char[] c = new char[singleMessageSize];
                            fill(c);
                            writeCounter.addAndGet(c.length);
                            out.write(c);
                            out.flush();
                            out.close();
                        } catch (IOException ioe) {
                            ioe.printStackTrace();
                        }
                        response.resume();
                    }

                    @Override
                    public void onError(Throwable t) {
                        response.resume();
                        throw new RuntimeException(t);
                    }
                });
            }

        };

        final HttpServer server = createWebServer(httpHandler);
        http2Addon.setInitialWindowSize(maxWindowSize);

        try {
            server.start();
            
            clientTransport.start();

            Future<Connection> connectFuture = clientTransport.connect("localhost", PORT);
            Connection connection = null;
            try {
                connection = connectFuture.get(10, TimeUnit.SECONDS);
                int length = parseResult.get(30, TimeUnit.SECONDS);
                assertEquals(writeCounter.get(), length);
                assertTrue(callbackInvoked.get());
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


    @Test
    public void testBlockingCharacterOutputSink() throws Exception {

        final int bufferSize = 4096;
        final int maxWindowSize = bufferSize * 3 / 4;
        final int bytesToSend = bufferSize * 1024 * 4;
        
        final FutureImpl<Integer> parseResult = SafeFutureImpl.create();
        FilterChainBuilder filterChainBuilder =
                createClientFilterChainAsBuilder(isSecure);
        filterChainBuilder.add(new BaseFilter() {

            private int bytesRead;
            
            @Override
            public NextAction handleConnect(FilterChainContext ctx) throws IOException {
                // Build the HttpRequestPacket, which will be sent to a server
                // We construct HTTP request version 1.1 and specifying the URL of the
                // resource we want to download
                final HttpRequestPacket httpRequest = HttpRequestPacket.builder().method("GET")
                        .uri("/path").protocol(Protocol.HTTP_1_1)
                        .header("Host", "localhost:" + PORT).build();

                // Write the request asynchronously
                ctx.write(HttpContent.builder(httpRequest)
                        .content(Buffers.EMPTY_BUFFER)
                        .last(true)
                        .build());

                // Return the stop action, which means we don't expect next filter to process
                // connect event
                return ctx.getStopAction();
            }

            @Override
            public NextAction handleRead(FilterChainContext ctx) throws IOException {
                HttpContent message = (HttpContent) ctx.getMessage();
                Buffer b = message.getContent();
                final int remaining = b.remaining();
                
                if (b.hasRemaining()) {
                    try {
                        check(b.toStringContent(), bytesRead % bufferSize, remaining, bufferSize);
                    } catch (Exception e) {
                        parseResult.failure(e);
                    }
                    
                    bytesRead += remaining;
                }
                
                if (message.isLast()) {
                    parseResult.result(bytesRead);
                }
                return ctx.getStopAction();
            }
        });

        final TCPNIOTransport clientTransport = TCPNIOTransportBuilder.newInstance().build();
        final FilterChain clientChain = filterChainBuilder.build();
        setInitialHttp2WindowSize(clientChain, maxWindowSize);
        
        clientTransport.setProcessor(clientChain);

        final AtomicInteger writeCounter = new AtomicInteger();
        final HttpHandler httpHandler = new HttpHandler() {

            @Override
            public void service(final Request request, final Response response) throws Exception {
                response.setContentType("text/plain");
                final NIOWriter out = response.getNIOWriter();

                int sent = 0;
                
                char[] b = new char[bufferSize];
                fill(b);
                while (sent < bytesToSend) {
                    out.write(b);
                    sent += bufferSize;
                    writeCounter.addAndGet(bufferSize);
                }
            }
        };


        final HttpServer server = createWebServer(httpHandler);
        http2Addon.setInitialWindowSize(maxWindowSize);

        try {
            server.start();
            
            clientTransport.start();

            Future<Connection> connectFuture = clientTransport.connect("localhost", PORT);
            Connection connection = null;
            try {
                connection = connectFuture.get(10, TimeUnit.SECONDS);
                int length = parseResult.get(60, TimeUnit.SECONDS);
                assertEquals("Received " + length + " bytes", bytesToSend, length);
            } finally {
                LOGGER.log(Level.INFO, "Written {0}", writeCounter);
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
    
    @Test
    public void testWriteExceptionPropagation() throws Exception {
        final int size = 1024;        
        
        FilterChainBuilder filterChainBuilder =
                createClientFilterChainAsBuilder(isSecure);
        filterChainBuilder.add(new BaseFilter() {

            @Override
            public NextAction handleConnect(FilterChainContext ctx) throws IOException {
                // Build the HttpRequestPacket, which will be sent to a server
                // We construct HTTP request version 1.1 and specifying the URL of the
                // resource we want to download
                final HttpRequestPacket httpRequest = HttpRequestPacket.builder().method("GET")
                        .uri("/path").protocol(Protocol.HTTP_1_1)
                        .header("Host", "localhost:" + PORT).build();

                // Write the request asynchronously
                ctx.write(HttpContent.builder(httpRequest)
                        .content(Buffers.EMPTY_BUFFER)
                        .last(true)
                        .build());

                // Return the stop action, which means we don't expect next filter to process
                // connect event
                return ctx.getStopAction();
            }

            @Override
            public NextAction handleRead(FilterChainContext ctx) throws IOException {
                return ctx.getStopAction();
            }
        });

        final TCPNIOTransport clientTransport = TCPNIOTransportBuilder.newInstance().build();
        clientTransport.setProcessor(filterChainBuilder.build());
        
        final FutureImpl<Boolean> parseResult = SafeFutureImpl.create();
        
        final HttpHandler httpHandler = new HttpHandler() {

            @Override
            public void service(final Request request, final Response response) throws Exception {

                //clientTransport.pause();
                response.setContentType("text/plain");
                final NIOWriter out = response.getNIOWriter();

                char[] c = new char[size];
                Arrays.fill(c, 'a');
                
                for(;;) {
                    try {
                        out.write(c);
                        out.flush();
                        Thread.yield();
                    } catch (IOException e) {
                        if ((e instanceof CustomIOException) ||
                                (e.getCause() instanceof CustomIOException)) {
                            parseResult.result(Boolean.TRUE);
                        } else {
                            System.out.println("NOT CUSTOM");
                            parseResult.failure(e);
                        }
                        break;
                    } catch (Exception e) {
                        System.out.println("NOT CUSTOM");
                        parseResult.failure(e);
                        break;
                    }
                }

            }

        };

        final HttpServer server = createWebServer(httpHandler);
        
        final NetworkListener listener = server.getListener("grizzly");
        listener.registerAddOn(new AddOn() {

            @Override
            public void setup(NetworkListener networkListener, FilterChainBuilder builder) {
                final int idx = builder.indexOfType(TransportFilter.class);
                builder.add(idx + 1, new BaseFilter() {
                    final AtomicInteger counter = new AtomicInteger();
                    @Override
                    public NextAction handleWrite(FilterChainContext ctx)
                            throws IOException {
                        final Buffer buffer = ctx.getMessage();
                        if (counter.addAndGet(buffer.remaining()) > size * 8) {
                            throw new CustomIOException();
                        }
                        
                        return ctx.getInvokeAction();
                    }
                });
            }
            
        });
        
        try {
            server.start();
            clientTransport.start();

            Future<Connection> connectFuture = clientTransport.connect("localhost", PORT);
            Connection connection = null;
            try {
                connection = connectFuture.get(10, TimeUnit.SECONDS);
                boolean exceptionThrown = parseResult.get(10, TimeUnit.SECONDS);
                assertTrue("Unexpected Exception thrown.", exceptionThrown);
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

    @Test
    public void testOutputBufferDirectWrite() throws Exception {
        final int bufferSize = 65536;
        final int maxWindowSize = bufferSize * 10;

        final FutureImpl<String> parseResult = SafeFutureImpl.create();
        
        FilterChainBuilder filterChainBuilder =
                createClientFilterChainAsBuilder(isSecure);
        filterChainBuilder.add(new BaseFilter() {
            private final StringBuilder sb = new StringBuilder();
            
            @Override
            public NextAction handleConnect(FilterChainContext ctx) throws IOException {
                // Build the HttpRequestPacket, which will be sent to a server
                // We construct HTTP request version 1.1 and specifying the URL of the
                // resource we want to download
                final HttpRequestPacket httpRequest = HttpRequestPacket.builder().method("GET")
                        .uri("/path").protocol(Protocol.HTTP_1_1)
                        .header("Host", "localhost:" + PORT).build();

                // Write the request asynchronously
                ctx.write(HttpContent.builder(httpRequest)
                        .content(Buffers.EMPTY_BUFFER)
                        .last(true)
                        .build());

                // Return the stop action, which means we don't expect next filter to process
                // connect event
                return ctx.getStopAction();
            }

            @Override
            public NextAction handleRead(FilterChainContext ctx) throws IOException {

                HttpContent message = (HttpContent) ctx.getMessage();
                Buffer b = message.getContent();
                if (b.hasRemaining()) {
                    sb.append(b.toStringContent());
                }

                if (message.isLast()) {
                    parseResult.result(sb.toString());
                }
                return ctx.getStopAction();
            }
        });

        final TCPNIOTransport clientTransport = TCPNIOTransportBuilder.newInstance().build();
        final FilterChain clientChain = filterChainBuilder.build();
        setInitialHttp2WindowSize(clientChain, maxWindowSize);
        
        clientTransport.setProcessor(clientChain);
        
        final AtomicInteger writeCounter = new AtomicInteger();
        final HttpHandler httpHandler = new HttpHandler() {

            @Override
            public void service(final Request request, final Response response) throws Exception {
                
                clientTransport.pause();
                response.setContentType("text/plain");
                final NIOOutputStream out = response.getNIOOutputStream();
                
                // in order to enable direct writes - set the buffer size less than byte[] length
                response.setBufferSize(bufferSize / 8);

                final byte[] b = new byte[bufferSize];
                
                int i = 0;
                while (out.canWrite()) {
                    Arrays.fill(b, (byte) ('a' + (i++ % ('z' - 'a'))));
                    writeCounter.addAndGet(b.length);
                    out.write(b);
                    Thread.yield();
                }

                clientTransport.resume();
            }
        };

        final HttpServer server = createWebServer(httpHandler);
        http2Addon.setInitialWindowSize(maxWindowSize);

        try {
            server.start();
            
            clientTransport.start();

            Future<Connection> connectFuture = clientTransport.connect("localhost", PORT);
            Connection connection = null;
            try {
                connection = connectFuture.get(10, TimeUnit.SECONDS);
                String resultStr = parseResult.get(10, TimeUnit.SECONDS);
                assertEquals(writeCounter.get(), resultStr.length());
                check1(resultStr, bufferSize);
                
            } finally {
                LOGGER.log(Level.INFO, "Written {0}", writeCounter);
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
    
    @Test
    public void testWritePossibleReentrants() throws Exception {

        final FutureImpl<HttpHeader> parseResult = SafeFutureImpl.<HttpHeader>create();
        final FilterChainBuilder filterChainBuilder =
                createClientFilterChainAsBuilder(isSecure);
        filterChainBuilder.add(new BaseFilter() {

            @Override
            public NextAction handleConnect(FilterChainContext ctx) throws IOException {
                // Build the HttpRequestPacket, which will be sent to a server
                // We construct HTTP request version 1.1 and specifying the URL of the
                // resource we want to download
                final HttpRequestPacket httpRequest = HttpRequestPacket.builder().method("GET")
                        .uri("/path").protocol(Protocol.HTTP_1_1)
                        .header("Host", "localhost:" + PORT).build();

                // Write the request asynchronously
                ctx.write(HttpContent.builder(httpRequest)
                        .content(Buffers.EMPTY_BUFFER)
                        .last(true)
                        .build());

                // Return the stop action, which means we don't expect next filter to process
                // connect event
                return ctx.getStopAction();
            }

            @Override
            public NextAction handleRead(FilterChainContext ctx) throws IOException {
                final HttpPacket message = ctx.getMessage();
                final HttpHeader header = message.isHeader() ?
                        (HttpHeader) message :
                        ((HttpContent) message).getHttpHeader();
                
                parseResult.result(header);
                
                return ctx.getStopAction();
            }
        });
        
        final int maxAllowedReentrants = Writer.Reentrant.getMaxReentrants();
        final AtomicInteger maxReentrantsNoticed = new AtomicInteger();

        final TCPNIOTransport clientTransport = TCPNIOTransportBuilder.newInstance().build();
        clientTransport.setProcessor(filterChainBuilder.build());
        final HttpHandler httpHandler = new HttpHandler() {

            int reentrants = maxAllowedReentrants * 3;
            final ThreadLocal<Integer> reentrantsCounter = new ThreadLocal<Integer>() {

                @Override
                protected Integer initialValue() {
                    return -1;
                }
            };
            
            @Override
            public void service(final Request request, final Response response) throws Exception {
                response.suspend();
                
                //clientTransport.pause();
                final NIOOutputStream outputStream = response.getNIOOutputStream();
                reentrantsCounter.set(0);
                
                try {
                    outputStream.notifyCanWrite(new WriteHandler() {

                        @Override
                        public void onWritePossible() throws Exception {
                            if (reentrants-- >= 0) {
                                final int reentrantNum = reentrantsCounter.get() + 1;
                                
                                try {
                                    reentrantsCounter.set(reentrantNum);

                                    if (reentrantNum > maxReentrantsNoticed.get()) {
                                        maxReentrantsNoticed.set(reentrantNum);
                                    }

                                    outputStream.notifyCanWrite(this);
                                } finally {
                                    reentrantsCounter.set(reentrantNum - 1);
                                }
                            } else {
                                finish(200);
                            }
                        }

                        @Override
                        public void onError(Throwable t) {
                            finish(500);
                        }

                        private void finish(int code) {
                            response.setStatus(code);
                            response.resume();
                        }
                    });
                } finally {
                    reentrantsCounter.remove();
                }
            }
        };

        final HttpServer server = createWebServer(httpHandler);

        try {
            server.start();
            clientTransport.start();

            Future<Connection> connectFuture = clientTransport.connect("localhost", PORT);
            Connection connection = null;
            try {
                connection = connectFuture.get(10, TimeUnit.SECONDS);
                final HttpHeader header = parseResult.get(10, TimeUnit.SECONDS);
                assertEquals(200, ((HttpResponsePacket) header).getStatus());

                assertTrue("maxReentrantNoticed=" + maxReentrantsNoticed + " maxAllowed=" + maxAllowedReentrants,
                        maxReentrantsNoticed.get() <= maxAllowedReentrants);
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
    
    @Test
    public void testWritePossibleNotification() throws Exception {
        final int notificationsNum = 5;
        final int size = 8192;
                
        final FutureImpl<Integer> parseResult = SafeFutureImpl.<Integer>create();
        FilterChainBuilder filterChainBuilder =
                createClientFilterChainAsBuilder(isSecure);
        filterChainBuilder.add(new BaseFilter() {
            private int bytesRead;

            @Override
            public NextAction handleConnect(FilterChainContext ctx) throws IOException {
                // Build the HttpRequestPacket, which will be sent to a server
                // We construct HTTP request version 1.1 and specifying the URL of the
                // resource we want to download
                final HttpRequestPacket httpRequest = HttpRequestPacket.builder().method("GET")
                        .uri("/path").protocol(Protocol.HTTP_1_1)
                        .header("Host", "localhost:" + PORT).build();

                // Write the request asynchronously
                ctx.write(HttpContent.builder(httpRequest)
                        .content(Buffers.EMPTY_BUFFER)
                        .last(true)
                        .build());

                // Return the stop action, which means we don't expect next filter to process
                // connect event
                return ctx.getStopAction();
            }

            @Override
            public NextAction handleRead(FilterChainContext ctx) throws IOException {
                HttpContent message = (HttpContent) ctx.getMessage();
                Buffer b = message.getContent();
                final int remaining = b.remaining();
                
                if (b.hasRemaining()) {
                    try {
                        check(b.toStringContent(), bytesRead % size, remaining, size);
                    } catch (Exception e) {
                        parseResult.failure(e);
                    }
                    
                    bytesRead += remaining;
                }
                
                if (message.isLast()) {
                    parseResult.result(bytesRead);
                }
                return ctx.getStopAction();
            }
        });
        
        final TCPNIOTransport clientTransport = TCPNIOTransportBuilder.newInstance().build();
        clientTransport.setProcessor(filterChainBuilder.build());

        final AtomicInteger sentBytesCount = new AtomicInteger();
        final AtomicInteger notificationsCount = new AtomicInteger();
        
        final HttpHandler httpHandler = new HttpHandler() {

            @Override
            public void service(final Request request, final Response response) throws Exception {
                response.suspend();
                
                final NIOOutputStream outputStream = response.getNIOOutputStream();
                outputStream.notifyCanWrite(new WriteHandler() {

                    @Override
                    public void onWritePossible() throws Exception {
                        clientTransport.pause();
                        
                        try {
                            while (outputStream.canWrite()) {
                                byte[] b = new byte[size];
                                fill(b);
                                outputStream.write(b);
                                sentBytesCount.addAndGet(size);
                                Thread.yield();
                            }

                            if (notificationsCount.incrementAndGet() < notificationsNum) {
                                outputStream.notifyCanWrite(this);
                            } else {
                                finish(200);
                            }
                        } finally {
                            clientTransport.resume();
                        }
                    }

                    @Override
                    public void onError(Throwable t) {
                        finish(500);
                    }
                    
                    private void finish(int code) {
                        response.setStatus(code);
                        response.resume();
                    }
                });
            }
        };

        final HttpServer server = createWebServer(httpHandler);

        try {
            server.start();
            clientTransport.start();

            Future<Connection> connectFuture = clientTransport.connect("localhost", PORT);
            Connection connection = null;
            try {
                connection = connectFuture.get(10, TimeUnit.SECONDS);
                final int responseContentLength =
                        parseResult.get(10, TimeUnit.SECONDS);
                
                assertEquals(notificationsNum, notificationsCount.get());
                assertEquals(sentBytesCount.get(), responseContentLength);
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

    private static void fill(byte[] array) {
        for (int i=0; i<array.length; i++) {
            array[i] = (byte) ('a' + i % ('z' - 'a'));
        }
    }

    private static void fill(char[] array) {
        for (int i=0; i<array.length; i++) {
            array[i] = (char) ('a' + i % ('z' - 'a'));
        }
    }

//    private static void check(String s, int lastCameSize, int bufferSize) {
//        check(s, 0, lastCameSize, bufferSize);
//    }

    private static void check(String s, int offset, int lastCameSize, int bufferSize) {
        final int start = s.length() - lastCameSize;

        for (int i=0; i<lastCameSize; i++) {
            final char c = s.charAt(start + i);
            final char expect = (char) ('a' + ((i + start + offset) % bufferSize) % ('z' - 'a'));
            if (c != expect) {
                throw new IllegalStateException("Result at [" + (i + start) + "] don't match. Expected=" + expect + " got=" + c);
            }
        }
    }

    private void check1(final String resultStr, final int LENGTH) {
        for (int i = 0; i < resultStr.length() / LENGTH; i++) {
            final char expect = (char) ('a' + (i % ('z' - 'a')));
            for (int j = 0; j < LENGTH; j++) {
                final char charAt = resultStr.charAt(i * LENGTH + j);
                if (charAt != expect) {
                    throw new IllegalStateException("Result at [" + (i * LENGTH + j) + "] don't match. Expected=" + expect + " got=" + charAt);
                }
            }
        }
    }
    
    private HttpServer createWebServer(final HttpHandler httpHandler) {
        final HttpServer httpServer = createServer(null, PORT, isSecure,
                HttpHandlerRegistration.of(httpHandler, "/path/*"));
        
        final NetworkListener listener = httpServer.getListener("grizzly");
        listener.getKeepAlive().setIdleTimeoutInSeconds(-1);

        return httpServer;

    }

    private void setInitialHttp2WindowSize(final FilterChain filterChain,
            final int windowSize) {
        
        final int http2FilterIdx = filterChain.indexOfType(Http2BaseFilter.class);
        final Http2BaseFilter http2Filter =
                (Http2BaseFilter) filterChain.get(http2FilterIdx);
        http2Filter.setInitialWindowSize(windowSize);
    }

    private static final class CustomIOException extends IOException {
        private static final long serialVersionUID = 1L;
    }
}
