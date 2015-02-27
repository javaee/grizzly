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
package org.glassfish.grizzly.websockets;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Processor;
import org.glassfish.grizzly.EmptyCompletionHandler;
import org.glassfish.grizzly.filterchain.FilterChainBuilder;
import org.glassfish.grizzly.filterchain.TransportFilter;
import org.glassfish.grizzly.http.HttpClientFilter;
import org.glassfish.grizzly.impl.FutureImpl;
import org.glassfish.grizzly.nio.transport.TCPNIOConnectorHandler;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import org.glassfish.grizzly.nio.transport.TCPNIOTransportBuilder;
import org.glassfish.grizzly.utils.Futures;

public class WebSocketClient extends SimpleWebSocket {
    private static final Logger logger = Logger.getLogger(Constants.WEBSOCKET);
    private Version version;
    private final URI address;
    private final ExecutorService executorService = Executors.newFixedThreadPool(2);
    protected TCPNIOTransport transport;

    public WebSocketClient(String uri, WebSocketListener... listeners) {
        this(uri, WebSocketEngine.DEFAULT_VERSION, listeners);
    }

    public WebSocketClient(String uri, Version version, WebSocketListener... listeners) {
        super(version.createHandler(true), listeners);
        this.version = version;
        try {
            address = new URI(uri);
        } catch (URISyntaxException e) {
            throw new WebSocketException(e.getMessage(), e);
        }
        add(new WebSocketCloseAdapter());
    }

    public URI getAddress() {
        return address;
    }

    public void execute(Runnable runnable) {
        executorService.submit(runnable);
    }

    /**
     * @return this on successful connection
     */
    public WebSocket connect() {
        return connect(WebSocketEngine.DEFAULT_TIMEOUT, TimeUnit.SECONDS);
    }

    /**
     * @param timeout number of seconds to timeout trying to connect
     * @param unit time unit to use
     *
     * @return this on successful connection
     */
    public WebSocket connect(long timeout, TimeUnit unit) {
        try {
            buildTransport();
            transport.start();
            final TCPNIOConnectorHandler connectorHandler = new TCPNIOConnectorHandler(transport) {
                @Override
                protected void preConfigure(Connection conn) {
                    super.preConfigure(conn);
//                    final ProtocolHandler handler = version.createHandler(true);
                    /*
                    holder.handshake = handshake;
                     */
                    protocolHandler.setConnection(conn);
                    final WebSocketHolder holder = WebSocketHolder.set(conn, protocolHandler,
                            WebSocketClient.this);
                    holder.handshake = protocolHandler.createClientHandShake(address);
                }
            };
            final FutureImpl<Boolean> completeFuture = Futures.createSafeFuture();
            add(new WebSocketAdapter() {
                @Override
                public void onConnect(final WebSocket socket) {
                    super.onConnect(socket);
                    completeFuture.result(Boolean.TRUE);
                }
            });
            
            
            connectorHandler.setProcessor(createFilterChain(completeFuture));
            // start connect
            connectorHandler.connect(new InetSocketAddress(
                    address.getHost(), address.getPort()),
                    new EmptyCompletionHandler<Connection>() {

                        @Override
                        public void failed(Throwable throwable) {
                            completeFuture.failure(throwable);
                        }

                        @Override
                        public void cancelled() {
                            completeFuture.failure(new CancellationException());
                        }
                        
                    });
            
            completeFuture.get(timeout, unit);
            return this;
        } catch (Throwable e) {
            if (e instanceof ExecutionException) {
                e = e.getCause();
            }
            
            if (e instanceof HandshakeException) {
                throw (HandshakeException) e;
            }
            
            throw new HandshakeException(e.getMessage());
        }
    }

    protected void buildTransport() {
        transport = TCPNIOTransportBuilder.newInstance().build();
    }

    private static Processor createFilterChain(
            final FutureImpl<Boolean> completeFuture) {
        
        FilterChainBuilder clientFilterChainBuilder = FilterChainBuilder.stateless();
        clientFilterChainBuilder.add(new TransportFilter());
        clientFilterChainBuilder.add(new HttpClientFilter());
        clientFilterChainBuilder.add(new WebSocketClientFilter() {

            @Override
            protected void onHandshakeFailure(Connection connection, HandshakeException e) {
                completeFuture.failure(e);
            }
        });

        return clientFilterChainBuilder.build();
    }

    private class WebSocketCloseAdapter extends WebSocketAdapter {
        @Override
        public void onClose(WebSocket socket, DataFrame frame) {
            super.onClose(socket, frame);
            if (transport != null) {
                try {
                    transport.shutdownNow();
                } catch (IOException e) {
                    logger.log(Level.INFO, e.getMessage(), e);
                }
            }
        }
    }
}
