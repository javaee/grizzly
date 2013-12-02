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

package org.glassfish.grizzly.samples.httpserver.nonblockinghandler;

import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.ReadHandler;
import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.FilterChainBuilder;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.filterchain.TransportFilter;
import org.glassfish.grizzly.http.HttpClientFilter;
import org.glassfish.grizzly.http.HttpContent;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.http.server.HttpHandler;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.http.server.Response;
import org.glassfish.grizzly.http.server.ServerConfiguration;
import org.glassfish.grizzly.http.io.NIOReader;
import org.glassfish.grizzly.http.io.NIOWriter;
import org.glassfish.grizzly.http.util.Header;
import org.glassfish.grizzly.http.util.HeaderValue;
import org.glassfish.grizzly.impl.FutureImpl;
import org.glassfish.grizzly.impl.SafeFutureImpl;
import org.glassfish.grizzly.memory.MemoryManager;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;

import java.io.IOException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.grizzly.memory.Buffers;
import org.glassfish.grizzly.nio.transport.TCPNIOTransportBuilder;


/**
 * <p>
 * This example demonstrates the use of a {@link HttpHandler} to echo
 * <code>HTTP</code> <code>POST</code> data sent by the client, back to the client using
 * non-blocking streams introduced in Grizzly 2.0.
 * </p>
 *
 * <p>
 * The is composed of two main parts (as nested classes of <code>BlockingHttpHandlerSample</code>)
 * <ul>
 *    <li>
 *       Client: This is a simple <code>HTTP</code> based on the Grizzly {@link org.glassfish.grizzly.http.HttpClientFilter}.
 *               The client uses a custom {@link org.glassfish.grizzly.filterchain.Filter} on top
 *               of the {@link org.glassfish.grizzly.http.HttpClientFilter} to send the <code>POST</code> and
 *               read, and ultimately display, the response from the server.  To
 *               better demonstrate the callbacks defined by {@link ReadHandler},
 *               the client will send each data chunk two seconds apart.
 *
 *    </li>
 *    <li>
 *       NoneBlockingEchoHandler: This {@link HttpHandler} is installed to the
 *                                {@link org.glassfish.grizzly.http.server.HttpServer} instance and associated
 *                                with the path <code>/echo</code>.  The handler uses the {@link org.glassfish.grizzly.http.io.NIOReader}
 *                                returned by {@link org.glassfish.grizzly.http.server.Request#getReader()}.
 *                                As data is received asynchronously, the {@link ReadHandler} callbacks are
 *                                invoked at this time data is then written to the response.
 *    </li>
 * </ul>
 * </p>
 *
 */
public class NonBlockingHttpHandlerSample {

    private static final Logger LOGGER = Grizzly.logger(NonBlockingHttpHandlerSample.class);


    public static void main(String[] args) {

        // create a basic server that listens on port 8080.
        final HttpServer server = HttpServer.createSimpleServer();

        final ServerConfiguration config = server.getServerConfiguration();

        // Map the path, /echo, to the NonBlockingEchoHandler
        config.addHttpHandler(new NonBlockingEchoHandler(), "/echo");

        try {
            server.start();
            Client client = new Client();
            client.run();
        } catch (IOException ioe) {
            LOGGER.log(Level.SEVERE, ioe.toString(), ioe);
        } finally {
            server.shutdownNow();
        }
    }


    // ---------------------------------------------------------- Nested Classes


    private static final class Client {

        private static final String HOST = "localhost";
        private static final int PORT = 8080;

        public void run() throws IOException {
            final FutureImpl<String> completeFuture = SafeFutureImpl.create();

            // Build HTTP client filter chain
            FilterChainBuilder clientFilterChainBuilder = FilterChainBuilder.stateless();
            // Add transport filter
            clientFilterChainBuilder.add(new TransportFilter());

            // Add HttpClientFilter, which transforms Buffer <-> HttpContent
            clientFilterChainBuilder.add(new HttpClientFilter());
            // Add ClientFilter
            clientFilterChainBuilder.add(new ClientFilter(completeFuture));


            // Initialize Transport
            final TCPNIOTransport transport =
                    TCPNIOTransportBuilder.newInstance().build();
            // Set filterchain as a Transport Processor
            transport.setProcessor(clientFilterChainBuilder.build());

            try {
                // start the transport
                transport.start();

                Connection connection = null;

                // Connecting to a remote Web server
                Future<Connection> connectFuture = transport.connect(HOST, PORT);
                try {
                    // Wait until the client connect operation will be completed
                    // Once connection has been established, the POST will
                    // be sent to the server.
                    connection = connectFuture.get(10, TimeUnit.SECONDS);

                    // Wait no longer than 30 seconds for the response from the
                    // server to be complete.
                    String result = completeFuture.get(30, TimeUnit.SECONDS);

                    // Display the echoed content
                    System.out.println("\nEchoed POST Data: " + result + '\n');
                } catch (Exception e) {
                    if (connection == null) {
                        LOGGER.log(Level.WARNING, "Connection failed.  Server is not listening.");
                    } else {
                        LOGGER.log(Level.WARNING, "Unexpected error communicating with the server.");
                    }
                } finally {
                    // Close the client connection
                    if (connection != null) {
                        connection.closeSilently();
                    }
                }
            } finally {
                // stop the transport
                transport.shutdownNow();
            }
        }


        // ------------------------------------------------------ Nested Classes

        private static final class ClientFilter extends BaseFilter {
            private static final HeaderValue HOST_HEADER_VALUE =
                    HeaderValue.newHeaderValue(HOST + ':' + PORT).prepare();

            private static final String[] CONTENT = {
                "contentA-",
                "contentB-",
                "contentC-",
                "contentD"
            };

            private FutureImpl<String> future;

            private StringBuilder sb = new StringBuilder();

            // ---------------------------------------------------- Constructors


            private ClientFilter(FutureImpl<String> future) {
                this.future = future;                
            }


            // ----------------------------------------- Methods from BaseFilter


            @SuppressWarnings({"unchecked"})
            @Override
            public NextAction handleConnect(FilterChainContext ctx) throws IOException {
                System.out.println("\nClient connected!\n");

                HttpRequestPacket request = createRequest();
                System.out.println("Writing request:\n");
                System.out.println(request.toString());
                ctx.write(request); // write the request

                // for each of the content parts in CONTENT, wrap in a Buffer,
                // create the HttpContent to wrap the buffer and write the
                // content.
                MemoryManager mm = ctx.getConnection().getTransport().getMemoryManager();
                for (int i = 0, len = CONTENT.length; i < len; i++) {
                    HttpContent.Builder contentBuilder = request.httpContentBuilder();
                    Buffer b = Buffers.wrap(mm, CONTENT[i]);
                    contentBuilder.content(b);
                    HttpContent content = contentBuilder.build();
                    System.out.printf("(Client writing: %s)\n", b.toStringContent());
                    ctx.write(content);
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException e) {
                        e.printStackTrace(); 
                    }
                }

                // since the request created by createRequest() is chunked,
                // we need to write the trailer to signify the end of the
                // POST data
                ctx.write(request.httpTrailerBuilder().build());

                System.out.println("\n");

                return ctx.getStopAction(); // discontinue filter chain execution

            }


            @Override
            public NextAction handleRead(FilterChainContext ctx) throws IOException {

                HttpContent c = (HttpContent) ctx.getMessage();
                Buffer b = c.getContent();
                if (b.hasRemaining()) {
                    sb.append(b.toStringContent());
                }

                // Last content from the server, set the future result so
                // the client can display the result and gracefully exit.
                if (c.isLast()) {
                    future.result(sb.toString());
                }
                return ctx.getStopAction(); // discontinue filter chain execution

            }


            // ------------------------------------------------- Private Methods


            private HttpRequestPacket createRequest() {

                HttpRequestPacket.Builder builder = HttpRequestPacket.builder();
                builder.method("POST");
                builder.protocol("HTTP/1.1");
                builder.uri("/echo");
                builder.chunked(true);
                HttpRequestPacket packet = builder.build();
                packet.addHeader(Header.Host, HOST_HEADER_VALUE);
                return packet;

            }

        }

    } // END Client


    /**
     * This handler using non-blocking streams to read POST data and echo it
     * back to the client.
     */
    private static class NonBlockingEchoHandler extends HttpHandler {


        // -------------------------------------------- Methods from HttpHandler


        @Override
        public void service(final Request request,
                            final Response response) throws Exception {

            final char[] buf = new char[128];
            final NIOReader in = request.getNIOReader(); // return the non-blocking InputStream
            final NIOWriter out = response.getNIOWriter();

            response.suspend();

            // If we don't have more data to read - onAllDataRead() will be called
            in.notifyAvailable(new ReadHandler() {

                @Override
                public void onDataAvailable() throws Exception {
                    System.out.printf("[onDataAvailable] echoing %d bytes\n", in.readyData());
                    echoAvailableData(in, out, buf);
                    in.notifyAvailable(this);
                }

                @Override
                public void onError(Throwable t) {
                    System.out.println("[onError]" + t);
                    response.resume();
                }

                @Override
                public void onAllDataRead() throws Exception {
                    System.out.printf("[onAllDataRead] length: %d\n", in.readyData());
                    try {
                        echoAvailableData(in, out, buf);
                    } finally {
                        try {
                            in.close();
                        } catch (IOException ignored) {
                        }

                        try {
                            out.close();
                        } catch (IOException ignored) {
                        }

                        response.resume();
                    }
                }
            });

        }

        private void echoAvailableData(NIOReader in, NIOWriter out, char[] buf)
                throws IOException {
            
            while(in.isReady()) {
                int len = in.read(buf);
                out.write(buf, 0, len);
            }
        }

    } // END NonBlockingEchoHandler
    
}
