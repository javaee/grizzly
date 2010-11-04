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

package org.glassfish.grizzly.web;

import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.TransportFactory;
import org.glassfish.grizzly.asyncqueue.AsyncQueueWriter;
import org.glassfish.grizzly.asyncqueue.TaskQueue;
import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.FilterChainBuilder;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.filterchain.TransportFilter;
import org.glassfish.grizzly.http.HttpClientFilter;
import org.glassfish.grizzly.http.HttpContent;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.http.Protocol;
import org.glassfish.grizzly.http.server.*;
import org.glassfish.grizzly.http.server.HttpRequestProcessor;
import org.glassfish.grizzly.http.server.io.NIOOutputStream;
import org.glassfish.grizzly.http.server.io.NIOWriter;
import org.glassfish.grizzly.http.server.io.WriteHandler;
import org.glassfish.grizzly.impl.FutureImpl;
import org.glassfish.grizzly.impl.SafeFutureImpl;
import org.glassfish.grizzly.nio.AbstractNIOConnection;
import org.glassfish.grizzly.nio.PendingWriteQueueLimitExceededException;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import junit.framework.TestCase;

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class NIOOutputSinksTest extends TestCase {

    private static final int PORT = 9339;

    public void testBinaryOutputSink() throws Exception {

        final HttpServer server = new HttpServer();
        final NetworkListener listener =
                new NetworkListener("Grizzly",
                                    NetworkListener.DEFAULT_NETWORK_HOST,
                                    PORT);
        final AsyncQueueWriter asyncQueueWriter =
                listener.getTransport().getAsyncQueueIO().getWriter();
        final int LENGTH = 256000;
        final int MAX_LENGTH = LENGTH * 2;
        asyncQueueWriter.setMaxPendingBytesPerConnection(MAX_LENGTH);
        server.addListener(listener);
        final FutureImpl<Integer> parseResult = SafeFutureImpl.create();
        FilterChainBuilder filterChainBuilder = FilterChainBuilder.stateless();
        filterChainBuilder.add(new TransportFilter());
        filterChainBuilder.add(new HttpClientFilter());
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
                ctx.write(httpRequest);

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
                    try {
                        check(sb, b.remaining());
                    } catch (Exception e) {
                        parseResult.failure(e);
                    }
                }

                if (message.isLast()) {
                    parseResult.result(sb.length());
                }
                return ctx.getStopAction();
            }
        });


        final TCPNIOTransport clientTransport = TransportFactory.getInstance().createTCPTransport();
        clientTransport.setProcessor(filterChainBuilder.build());
        final AtomicInteger writeCounter = new AtomicInteger();
        final AtomicBoolean callbackInvoked = new AtomicBoolean(false);
        final HttpRequestProcessor ga = new HttpRequestProcessor() {

            @Override
            public void service(final Request request, final Response response) throws Exception {
                
                clientTransport.pause();
                response.setContentType("text/plain");
                final NIOOutputStream out = response.getOutputStream();

                while (out.canWrite(LENGTH)) {
                    byte[] b = new byte[LENGTH];
                    fill(b);
                    writeCounter.addAndGet(b.length);
                    out.write(b);
                    out.flush();
                }

                Connection c = request.getContext().getConnection();
                final TaskQueue tqueue = ((AbstractNIOConnection) c).getAsyncWriteQueue();

                out.notifyCanWrite(new WriteHandler() {
                    @Override
                    public void onWritePossible() {
                        callbackInvoked.compareAndSet(false, true);
                        try {
                            clientTransport.pause();
                        } catch (IOException ioe) {
                            ioe.printStackTrace();
                        }

                        assertTrue(MAX_LENGTH - tqueue.spaceInBytes() >= LENGTH);

                        try {
                            clientTransport.resume();
                        } catch (IOException ioe) {
                            ioe.printStackTrace();
                        }
                        try {
                            byte[] b = new byte[LENGTH];
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
                }, (LENGTH));

                clientTransport.resume();
                response.suspend();
            }

        };


        server.getServerConfiguration().addHttpService(ga, "/path");

        try {
            server.start();
            clientTransport.start();

            Future<Connection> connectFuture = clientTransport.connect("localhost", PORT);
            Connection connection = null;
            try {
                connection = connectFuture.get(10, TimeUnit.SECONDS);
                int length = parseResult.get(10, TimeUnit.SECONDS);
                assertEquals(writeCounter.get(), length);
                assertTrue(callbackInvoked.get());
            } finally {
                // Close the client connection
                if (connection != null) {
                    connection.close();
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
            fail();
        } finally {
            clientTransport.stop();
            server.stop();
            TransportFactory.getInstance().close();
        }

    }
    
    
    public void testCharacterOutputSink() throws Exception {

        final HttpServer server = new HttpServer();
        final NetworkListener listener =
                new NetworkListener("Grizzly",
                                    NetworkListener.DEFAULT_NETWORK_HOST,
                                    PORT);
        final AsyncQueueWriter asyncQueueWriter =
                listener.getTransport().getAsyncQueueIO().getWriter();
        final int LENGTH = 256000;
        final int MAX_LENGTH = LENGTH * 2;
        asyncQueueWriter.setMaxPendingBytesPerConnection(MAX_LENGTH);
        server.addListener(listener);
        final FutureImpl<Integer> parseResult = SafeFutureImpl.create();
        FilterChainBuilder filterChainBuilder = FilterChainBuilder.stateless();
        filterChainBuilder.add(new TransportFilter());
        filterChainBuilder.add(new HttpClientFilter());
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
                ctx.write(httpRequest);

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
                    try {
                        check(sb, b.remaining());
                    } catch (Exception e) {
                        parseResult.failure(e);
                    }
                }
                
                if (message.isLast()) {
                    parseResult.result(sb.length());
                }
                return ctx.getStopAction();
            }
        });


        final TCPNIOTransport clientTransport = TransportFactory.getInstance().createTCPTransport();
        clientTransport.setProcessor(filterChainBuilder.build());
        final AtomicInteger writeCounter = new AtomicInteger();
        final AtomicBoolean callbackInvoked = new AtomicBoolean(false);
        final HttpRequestProcessor ga = new HttpRequestProcessor() {

            @Override
            public void service(final Request request, final Response response) throws Exception {
                
                clientTransport.pause();
                response.suspend();

                response.setContentType("text/plain");
                final NIOWriter out = response.getWriter();
                Connection c = request.getContext().getConnection();
                final TaskQueue tqueue = ((AbstractNIOConnection) c).getAsyncWriteQueue();


                while (!notifyCanWrite(out, tqueue, response)) {
                    char[] data = new char[LENGTH];
                    fill(data);
                    writeCounter.addAndGet(data.length);
                    out.write(data);
                    out.flush();
                }                

                clientTransport.resume();
            }

            private boolean notifyCanWrite(final NIOWriter out,
                    final TaskQueue tqueue, final Response response) {

                return out.notifyCanWrite(new WriteHandler() {

                    @Override
                    public void onWritePossible() {
                        callbackInvoked.compareAndSet(false, true);
                        try {
                            clientTransport.pause();
                        } catch (IOException ioe) {
                            ioe.printStackTrace();
                        }
                        assertTrue(MAX_LENGTH - tqueue.spaceInBytes() >= LENGTH);
                        try {
                            clientTransport.resume();
                        } catch (IOException ioe) {
                            ioe.printStackTrace();
                        }
                        try {
                            char[] c = new char[LENGTH];
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
                }, LENGTH);
            }

        };


        server.getServerConfiguration().addHttpService(ga, "/path");

        try {
            server.start();
            clientTransport.start();

            Future<Connection> connectFuture = clientTransport.connect("localhost", PORT);
            Connection connection = null;
            try {
                connection = connectFuture.get(10, TimeUnit.SECONDS);
                int length = parseResult.get(10, TimeUnit.SECONDS);
                assertEquals(writeCounter.get(), length);
                assertTrue(callbackInvoked.get());
            } finally {
                // Close the client connection
                if (connection != null) {
                    connection.close();
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
            fail();
        } finally {
            clientTransport.stop();
            server.stop();
            TransportFactory.getInstance().close();
        }

    }


    public void testWriteExceptionPropagation() throws Exception {

        final HttpServer server = new HttpServer();
        final NetworkListener listener =
                new NetworkListener("Grizzly",
                                    NetworkListener.DEFAULT_NETWORK_HOST,
                                    PORT);
        final AsyncQueueWriter asyncQueueWriter =
                listener.getTransport().getAsyncQueueIO().getWriter();
        final int LENGTH = 256000;
        final int MAX_LENGTH = LENGTH * 2;
        asyncQueueWriter.setMaxPendingBytesPerConnection(MAX_LENGTH);
        server.addListener(listener);
        final FutureImpl<Boolean> parseResult = SafeFutureImpl.create();
        FilterChainBuilder filterChainBuilder = FilterChainBuilder.stateless();
        filterChainBuilder.add(new TransportFilter());
        filterChainBuilder.add(new HttpClientFilter());
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
                ctx.write(httpRequest);

                // Return the stop action, which means we don't expect next filter to process
                // connect event
                return ctx.getStopAction();
            }

            @Override
            public NextAction handleRead(FilterChainContext ctx) throws IOException {
                return ctx.getSuspendAction();
            }
        });

        final TCPNIOTransport clientTransport = TransportFactory.getInstance().createTCPTransport();
        clientTransport.setProcessor(filterChainBuilder.build());
        final HttpRequestProcessor ga = new HttpRequestProcessor() {

            @Override
            public void service(final Request request, final Response response) throws Exception {

                //clientTransport.pause();
                response.setContentType("text/plain");
                final NIOWriter out = response.getWriter();

                char[] c = new char[LENGTH];
                Arrays.fill(c, 'a');
                
                for(;;) {
                    try {
                        out.write(c);
                    } catch (PendingWriteQueueLimitExceededException p) {
                        parseResult.result(Boolean.TRUE);
                        break;
                    } catch (Exception e) {
                        parseResult.failure(e);
                        break;
                    }
                    out.flush();
                }

            }

        };


        server.getServerConfiguration().addHttpService(ga, "/path");

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
                    connection.close();
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
            fail();
        } finally {
            clientTransport.stop();
            server.stop();
            TransportFactory.getInstance().close();
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

    private static void check(StringBuilder sb, int lastCameSize) {
        final int start = sb.length() - lastCameSize;

        for (int i=0; i<lastCameSize; i++) {
            final char c = sb.charAt(start + i);
            final char expect = (char) ('a' + (i + start) % ('z' - 'a'));
            if (c != expect) {
                throw new IllegalStateException("Result at [" + (i + start) + "] don't match. Expected=" + expect + " got=" + c);
            }
        }
    }
}
