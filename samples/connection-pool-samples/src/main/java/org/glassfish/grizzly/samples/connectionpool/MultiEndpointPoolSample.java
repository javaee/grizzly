/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved.
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
package org.glassfish.grizzly.samples.connectionpool;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Collections;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.ConnectorHandler;
import org.glassfish.grizzly.EmptyCompletionHandler;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.Transport;
import org.glassfish.grizzly.connectionpool.EndpointKey;
import org.glassfish.grizzly.connectionpool.MultiEndpointPool;
import org.glassfish.grizzly.filterchain.FilterChain;
import org.glassfish.grizzly.filterchain.FilterChainBuilder;
import org.glassfish.grizzly.filterchain.TransportFilter;
import org.glassfish.grizzly.nio.transport.TCPNIOConnectorHandler;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import org.glassfish.grizzly.nio.transport.TCPNIOTransportBuilder;
import org.glassfish.grizzly.utils.Charsets;
import org.glassfish.grizzly.utils.DataStructures;
import org.glassfish.grizzly.utils.StringFilter;

/**
 * The sample to demonstrate how Grizzly client-side connection pool could be
 * used in multi-threaded application in asynchronous/non-blocking fashion.
 * 
 * To simulate real-world usecase we initialize 2 TCP-based echo servers,
 * listening on two different TCP ports. On the client-side we initialize
 * {@link MultiEndpointPool}, which caches TCP {@link Connection}s to the
 * servers.
 * To test the connection pool - we run 100000 requests simultaneously
 * using custom {@link ExecutorService} (we randomly chose the
 * target server for each request). After we got responses from the
 * servers - we print out statistics: number of requests sent
 * (expected value is 100000), number of responses missed (expected value is 0),
 * the total number of client-side {@link Connection}s been established
 * (expected value is &lt;= 4).
 */
public class MultiEndpointPoolSample implements ClientCallback {
    private static final Logger LOGGER =
            Grizzly.logger(MultiEndpointPoolSample.class);
    
    private static final Random RANDOM = new Random();
    
    public static void main(String[] args) throws Exception {
        new MultiEndpointPoolSample().exec();
    }

    // EchoServer #1
    private EchoServer server1;
    // EchoServer #2
    private EchoServer server2;
    
    // Client Transport
    private Transport clientTransport;
    // ConnectorHandler to be used to create client connections
    private ConnectorHandler<SocketAddress> connectorHandler;
    // connection pool
    private MultiEndpointPool<SocketAddress> connectionPool;
    
    // counter to track the total number of established connections
    private final AtomicInteger clientConnectionsCounter = new AtomicInteger();
    // the message tracker set, where we add the message before sending a request
    // to a server and remove the message when getting a response
    private final Set<String> messageTracker = Collections.newSetFromMap(
            DataStructures.<String, Boolean>getConcurrentMap());
    
    // countdown latch used to wait for all responses to come
    private CountDownLatch responsesCountDownLatch;
    
    public void exec() throws Exception {
        try {
            // starting demo servers
            startServers();
            // initialize the client transport
            initializeClientTransport();
            
            // create a connection-pool EndpointKey for the server #1
            final EndpointKey<SocketAddress> server1EndpointKey =
                    new EndpointKey<SocketAddress>("server1",
                    server1.getEndpointAddress());
            // create a connection-pool EndpointKey for the server #2
            final EndpointKey<SocketAddress> server2EndpointKey =
                    new EndpointKey<SocketAddress>("server2",
                    server2.getEndpointAddress());

            // create a connection pool
            connectionPool = MultiEndpointPool
                            .builder(SocketAddress.class)
                            .connectorHandler(connectorHandler)
                            .maxConnectionsPerEndpoint(2)
                            .maxConnectionsTotal(4)
                            .build();

            
            // create an aux. thread-pool, which will be used to asynchronously
            // send requests
            final ExecutorService testingThreadPool =
                    Executors.newFixedThreadPool(256);
            
            try {
                final int requestsCount = 100000;
                
                // initialize count down latch
                responsesCountDownLatch = new CountDownLatch(requestsCount);
                
                LOGGER.log(Level.INFO, "Making {0} requests...", requestsCount);
                
                final long startTime = System.currentTimeMillis();
                
                // send 100000 requests
                for (int i = 0; i < requestsCount; i++) {
                    // randomly pick up a target server
                    final EndpointKey<SocketAddress> serverEndpoint =
                            RANDOM.nextBoolean() ?
                            server1EndpointKey :
                            server2EndpointKey;
                    final String testMessage = "Message #" + (i + 1);
                    // add the message to the tracking set
                    messageTracker.add(testMessage);
                    
                    // make the request asynchronously
                    testingThreadPool.execute(new Runnable() {
                        @Override
                        public void run() {
                            // allocate a Connection from the pool asynchronously
                            // using CompletionHandler. Once the Connection is
                            // allocated - send a test message.
                            // Same logic could have been implemented using Future:
                            //
                            // ---------------------------
                            // Future<Connection> allocFuture = pool.take(serverEndpoint);
                            // Connection c = allocFuture.get();
                            // c.write(testMessage);
                            // ---------------------------
                            //
                            // but in this case it would have been blocking,
                            // because allocFuture.get() can block the thread
                            // and wait until the Connection is available.
                            connectionPool.take(serverEndpoint,
                                    new EmptyCompletionHandler<Connection>() {

                                @Override
                                public void failed(Throwable throwable) {
                                    LOGGER.log(Level.WARNING,
                                            "Can't allocate a Connection", throwable);
                                }

                                @Override
                                @SuppressWarnings("unchecked")
                                public void completed(final Connection connection) {
                                    // once a Connection is obtained from the
                                    // pool - send the request
                                    connection.write(testMessage);
                                }
                            });
                            // Now the Connection will be allocated and the test
                            // message will be send.
                            // When a response from the server will be received -
                            // the Connection will be returned back by the
                            // ClientFilter.
                        }
                    });
                }
                
                // waiting for all responses to come
                responsesCountDownLatch.await(30, TimeUnit.SECONDS);
                
                final long runTime = (System.currentTimeMillis() - startTime) / 1000;
                
                // print out stats
                LOGGER.log(Level.INFO, "Completed in {0} seconds\nRequests sent: "
                        + "{1}\nResponses missed: {2}\nConnections created: {3}",
                        new Object[]{runTime, requestsCount,
                            responsesCountDownLatch.getCount(),
                            clientConnectionsCounter.get()});
                
            } finally {
                // shutdown the aux. thread-pool
                testingThreadPool.shutdownNow();
                // shutdown the connection-pool
                connectionPool.close();
            }
        } finally {
            // stop the client transport
            stopClientTransport();
            // stop the demo servers
            stopServers();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onConnectionEstablished(final Connection connection) {
        // new connection has been established - increment the counter
        clientConnectionsCounter.incrementAndGet();
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public void onResponseReceived(final Connection connection,
            final String responseMessage) {
        // response has been received
        // remove the message from the tracker
        if (messageTracker.remove(responseMessage)) {
            // decrease the counter
            responsesCountDownLatch.countDown();
        } else {
            // if message is not tracked - it's a bug
            LOGGER.log(Level.WARNING, "Received unexpected response: {0}",
                    responseMessage);
        }
        
        // return the connection back to the pool
        connectionPool.release(connection);
    }
    
    // initializes client Transport
    private void initializeClientTransport() throws IOException {
        final TCPNIOTransport tcpTransport =
                TCPNIOTransportBuilder.newInstance().build();
        tcpTransport.start();
        clientTransport = tcpTransport;
        
        final FilterChain clientFilterChain = FilterChainBuilder.stateless()
                .add(new TransportFilter())
                .add(new StringFilter(Charsets.UTF8_CHARSET))
                .add(new ClientFilter(this))
                .build();
        
        connectorHandler = TCPNIOConnectorHandler.builder(tcpTransport)
                .processor(clientFilterChain)
                .build();
    }
    
    private void stopClientTransport() throws IOException {
        if (clientTransport != null) {
            final Transport localTransport = clientTransport;
            clientTransport = null;
            
            try {
                localTransport.shutdownNow();
            } catch (IOException ignored) {
            }
        }
    }
    
    private void startServers() throws IOException {
        server1 = new EchoServer(new InetSocketAddress("0.0.0.0", 18080));
        server2 = new EchoServer(new InetSocketAddress("0.0.0.0", 18081));
        
        server1.start();
        server2.start();
    }
    
    private void stopServers() {
        if (server1 != null) {
            try {
                server1.stop();
            } catch (IOException ignored) {
            }
            
            server1 = null;
        }
        
        if (server2 != null) {
            try {
                server2.stop();
            } catch (IOException ignored) {
            }
            
            server2 = null;
        }
    }
}
