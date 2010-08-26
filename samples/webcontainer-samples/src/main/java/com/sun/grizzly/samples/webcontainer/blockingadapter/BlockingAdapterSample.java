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
package com.sun.grizzly.samples.webcontainer.blockingadapter;

import com.sun.grizzly.Buffer;
import com.sun.grizzly.Connection;
import com.sun.grizzly.Grizzly;
import com.sun.grizzly.TransportFactory;
import com.sun.grizzly.filterchain.BaseFilter;
import com.sun.grizzly.filterchain.FilterChainBuilder;
import com.sun.grizzly.filterchain.FilterChainContext;
import com.sun.grizzly.filterchain.NextAction;
import com.sun.grizzly.filterchain.TransportFilter;
import com.sun.grizzly.http.HttpClientFilter;
import com.sun.grizzly.http.HttpContent;
import com.sun.grizzly.http.HttpRequestPacket;
import com.sun.grizzly.http.server.GrizzlyAdapter;
import com.sun.grizzly.http.server.GrizzlyRequest;
import com.sun.grizzly.http.server.GrizzlyResponse;
import com.sun.grizzly.http.server.GrizzlyWebServer;
import com.sun.grizzly.http.server.ServerConfiguration;
import com.sun.grizzly.impl.FutureImpl;
import com.sun.grizzly.impl.SafeFutureImpl;
import com.sun.grizzly.memory.MemoryManager;
import com.sun.grizzly.memory.MemoryUtils;
import com.sun.grizzly.nio.transport.TCPNIOTransport;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * <p>
 * This example demonstrates the use of a {@link GrizzlyAdapter} to echo
 * <code>HTTP</code> <code>POST</code> data sent by the client, back to the client.
 * </p>
 *
 * <p>
 * The is composed of two main parts (as nested classes of <code>BlockingAdapterSample</code>)
 * <ul>
 *    <li>
 *       Client: This is a simple <code>HTTP</code> based on the Grizzly {@link HttpClientFilter}.
 *               The client uses a custom {@link com.sun.grizzly.filterchain.Filter} on top
 *               of the {@link HttpClientFilter} to send the <code>POST</code> and
 *               read, and ultimately display, the response from the server.
 *    </li>
 *    <li>
 *       BlockingEchoAdapter: This {@link GrizzlyAdapter} is installed to the
 *                            {@link GrizzlyWebServer} instance and associated
 *                            with the path <code>/echo</code>.  This {@link GrizzlyAdapter}
 *                            is fairly simple.  The adapter uses the {@link com.sun.grizzly.http.server.io.GrizzlyReader}
 *                            returned by {@link GrizzlyRequest#getReader(boolean)} in blocking
 *                            mode.  As data is received, the same data is then immediately
 *                            written to the rsponse.
 *    </li>
 * </ul>
 * </p>
 *
 */
public class BlockingAdapterSample {

    private static final Logger LOGGER = Grizzly.logger(BlockingAdapterSample.class);


    public static void main(String[] args) {

        // create a basic server that listens on port 8080.
        final GrizzlyWebServer server = GrizzlyWebServer.createSimpleServer();

        final ServerConfiguration config = server.getServerConfiguration();

        server.getListener("grizzly").setKeepAliveTimeoutInSeconds(0);

        // Map the path, /echo, to the BlockingEchoAdapter
        config.addGrizzlyAdapter(new BlockingEchoAdapter(), "/echo");

        try {
            server.start();
            Client client = new Client();
            client.run();
        } catch (IOException ioe) {
            LOGGER.log(Level.SEVERE, ioe.toString(), ioe);
        } finally {
            server.stop();
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
            TCPNIOTransport transport = TransportFactory.getInstance().createTCPTransport();
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
                        connection.close();
                    }
                }
            } finally {
                // stop the transport
                transport.stop();

                // release TransportManager resources like ThreadPool
                TransportFactory.getInstance().close();
            }
        }


        // ------------------------------------------------------ Nested Classes

        private static final class ClientFilter extends BaseFilter {

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

                HttpRequestPacket.Builder builder = createRequest();
                HttpRequestPacket request = builder.build();
                request.addHeader("Host", HOST + ':' + PORT);
                System.out.println("Writing request:\n");
                System.out.println(request.toString());
                ctx.write(request); // write the request

                // for each of the content parts in CONTENT, wrap in a Buffer,
                // create the HttpContent to wrap the buffer and write the
                // content.
                MemoryManager mm = ctx.getConnection().getTransport().getMemoryManager();
                for (int i = 0, len = CONTENT.length; i < len; i++) {
                    HttpContent.Builder contentBuilder = request.httpContentBuilder();
                    Buffer b = MemoryUtils.wrap(mm, CONTENT[i]);
                    contentBuilder.content(b);
                    HttpContent content = contentBuilder.build();
                    System.out.println(b.toStringContent());
                    ctx.write(content);
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


            private HttpRequestPacket.Builder createRequest() {

                HttpRequestPacket.Builder builder = HttpRequestPacket.builder();
                builder.method("POST");
                builder.protocol("HTTP/1.1");
                builder.uri("/echo");
                builder.chunked(true);
                return builder;

            }

        }

    } // END Client


    /**
     * This adapter using blocking streams to read POST data and echo it back to the
     * client.
     */
    private static class BlockingEchoAdapter extends GrizzlyAdapter {


        // --------------------------------------------- Methods from GrizzlyAdapter


        @Override
        public void service(GrizzlyRequest request, GrizzlyResponse response) throws Exception {

            final char[] buf = new char[128];
            Reader in = null;
            Writer out = null;
            try {
                in = request.getReader(true); // true argument puts the stream in blocking mode
                out = response.getWriter();

                int read;
                while ((read = in.read(buf)) != -1) {
                    out.write(buf, 0, read); // echo the contents of 'buf' to the client
                }
                out.flush();
            } finally {
                if (in != null) {
                    try {
                        in.close();
                    } catch (IOException ignore) {
                    }
                }
                if (out != null) {
                    try {
                        out.close();
                    } catch (IOException ignore) {
                    }
                }
            }

        }

    } // END BlockingEchoAdapter
    
}
