/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2011 Oracle and/or its affiliates. All rights reserved.
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Processor;
import org.glassfish.grizzly.filterchain.FilterChainBuilder;
import org.glassfish.grizzly.filterchain.TransportFilter;
import org.glassfish.grizzly.http.HttpClientFilter;
import org.glassfish.grizzly.impl.FutureImpl;
import org.glassfish.grizzly.impl.SafeFutureImpl;
import org.glassfish.grizzly.nio.transport.TCPNIOConnectorHandler;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import org.glassfish.grizzly.nio.transport.TCPNIOTransportBuilder;
import org.glassfish.grizzly.websockets.WebSocketEngine.WebSocketHolder;

public class WebSocketClient extends DefaultWebSocket {
    private static final Logger logger = Logger.getLogger(WebSocketEngine.WEBSOCKET);
    private Version version;
    private final URI address;
    private final ExecutorService executorService = Executors.newFixedThreadPool(2);
    private TCPNIOTransport transport;

    public WebSocketClient(String uri, WebSocketListener... listeners) throws URISyntaxException {
        this(WebSocketEngine.DEFAULT_VERSION, new URI(uri), listeners);
    }

    public WebSocketClient(Version version, URI uri, WebSocketListener... listeners) {
        super(listeners);
        this.version = version;
        address = uri;
        add(new WebSocketCloseAdapter());
    }

    @Override
    public void onClose(DataFrame frame) {
        super.onClose(frame);
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
        return connect(WebSocketEngine.DEFAULT_TIMEOUT);
    }

    /**
     * @param timeout number of seconds to timeout trying to connect
     *
     * @return this on successful connection
     */
    public WebSocket connect(long timeout) {
        try {
            final FutureImpl<Connection> future = SafeFutureImpl.create();
            transport = TCPNIOTransportBuilder.newInstance().build();
            transport.start();
            final TCPNIOConnectorHandler connectorHandler = new TCPNIOConnectorHandler(transport) {
                @Override
                protected void preConfigure(Connection conn) {
                    super.preConfigure(conn);
                    final ProtocolHandler handler = version.createHandler(true);
                    /*
                    holder.handshake = handshake;
                     */
                    final WebSocketHolder holder = WebSocketEngine.getEngine().setWebSocketHolder(conn, handler,
                        WebSocketClient.this);
                    holder.handshake = handler.createHandShake(address);
                    connection = conn;
                }
            };
            final CountDownLatch latch = new CountDownLatch(1);
            add(new WebSocketAdapter() {
                @Override
                public void onConnect(final WebSocket socket) {
                    super.onConnect(socket);
                    latch.countDown();
                }
            });
            connectorHandler.setProcessor(createFilterChain());
            // start connect
            connectorHandler.connect(new InetSocketAddress(address.getHost(), address.getPort()),
                new WebSocketCompletionHandler(future));

            connection = future.get(timeout, TimeUnit.SECONDS);
            latch.await(timeout, TimeUnit.SECONDS);

            return this;
        } catch (Exception e) {
            e.printStackTrace();
            throw new HandshakeException(e.getMessage());
        }
    }

    private static Processor createFilterChain() {
        FilterChainBuilder clientFilterChainBuilder = FilterChainBuilder.stateless();
        clientFilterChainBuilder.add(new TransportFilter());
        clientFilterChainBuilder.add(new HttpClientFilter());
        clientFilterChainBuilder.add(new WebSocketFilter());

        return clientFilterChainBuilder.build();
    }

    private class WebSocketCloseAdapter extends WebSocketAdapter {
        @Override
        public void onClose(WebSocket socket, DataFrame frame) {
            super.onClose(socket, frame);
            if (transport != null) {
                try {
                    transport.stop();
                } catch (IOException e) {
                    logger.log(Level.INFO, e.getMessage(), e);
                }
            }
        }
    }
}