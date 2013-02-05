/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012 Oracle and/or its affiliates. All rights reserved.
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
package org.glassfish.grizzly.spdy;

import java.util.List;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.ssl.SSLEngine;
import org.glassfish.grizzly.CompletionHandler;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.ConnectorHandler;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.filterchain.FilterChain;
import org.glassfish.grizzly.impl.FutureImpl;
import org.glassfish.grizzly.npn.NextProtoNegSupport;
import org.glassfish.grizzly.ssl.SSLFilter;
import org.glassfish.grizzly.utils.CompletionHandlerAdapter;
import org.glassfish.grizzly.utils.Futures;
import org.glassfish.grizzly.utils.GenericAdapter;

/**
 *
 * @author oleksiys
 */
public class SpdyConnectorHandler<E> implements ConnectorHandler<E> {
    private static final Logger LOGGER = Grizzly.logger(SpdyConnectorHandler.class);

    private final ConnectorHandler<E> transportConnectorHandler;
    
    public SpdyConnectorHandler(
            final ConnectorHandler<E> transportConnectorHandler) {
        this.transportConnectorHandler = transportConnectorHandler;
    }
    
    @Override
    public Future<Connection> connect(final E remoteAddress) {
        final FutureImpl<Connection> futureImpl =
                Futures.<Connection>createSafeFuture();
        connect(remoteAddress, Futures.toCompletionHandler(futureImpl));
        return futureImpl;
    }

    @Override
    public void connect(final E remoteAddress,
            final CompletionHandler<Connection> completionHandler) {
        transportConnectorHandler.connect(remoteAddress,
                new TransportCompletionHandler(completionHandler));
    }

    @Override
    public Future<Connection> connect(E remoteAddress, E localAddress) {
        return connect(remoteAddress);
    }

    @Override
    public void connect(E remoteAddress, E localAddress, CompletionHandler<Connection> completionHandler) {
        connect(remoteAddress, completionHandler);
    }

    private static class TransportCompletionHandler
            implements CompletionHandler<Connection> {
        
        private static final NpnNegotiator NPN_NEGOTIATOR = new NpnNegotiator();

        private final CompletionHandler<Connection> userCompletionHandler;
        
        public TransportCompletionHandler(
                final CompletionHandler<Connection> userCompletionHandler) {
            this.userCompletionHandler = userCompletionHandler;
        }

        @Override
        public void completed(final Connection connection) {
            final SpdySession spdySession = new SpdySession(connection, false);
            SpdySession.bind(connection, spdySession);
            
            final FilterChain filterChain = (FilterChain) connection.getProcessor();
            final int idx = filterChain.indexOfType(SSLFilter.class);
            if (idx == -1) { // No SSL filter -> no NPN
                userCompletionHandler.completed(connection);
            } else {  // use TLS NPN
                final SSLFilter sslFilter = (SSLFilter) filterChain.get(idx);
                NextProtoNegSupport.getInstance().configure(sslFilter);
                NextProtoNegSupport.getInstance().setClientSideNegotiator(
                        connection, NPN_NEGOTIATOR);
                
                try {
                    sslFilter.handshake(connection,
                            new CompletionHandlerAdapter<Connection, SSLEngine>(
                            null, userCompletionHandler,
                            new GenericAdapter<SSLEngine, Connection>() {

                                @Override
                                public Connection adapt(SSLEngine result) {
                                    return connection;
                                }
                    }));
                } catch (Exception e) {
                    userCompletionHandler.failed(e);
                }
            }
        }
        
        @Override
        public void cancelled() {
            userCompletionHandler.cancelled();
        }

        @Override
        public void failed(Throwable throwable) {
            userCompletionHandler.failed(throwable);
        }

        @Override
        public void updated(Connection connection) {
        }
    }
    
    private static class NpnNegotiator
            implements NextProtoNegSupport.ClientSideNegotiator {
        
        private static final String SPDY3_PROTOCOL = "spdy/3";

        @Override
        public boolean wantNegotiate(final Connection connection) {
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.log(Level.FINE, "NPN wantNegotiate. Connection={0}",
                        new Object[]{connection});
            }
            return true;
        }

        @Override
        public String selectProtocol(final Connection connection,
                final List<String> protocols) {
            
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.log(Level.FINE, "NPN selectProtocol. Connection={0}, protocols={1}",
                        new Object[]{connection, protocols});
            }
            
            if (protocols.indexOf(SPDY3_PROTOCOL) != -1) {
                return SPDY3_PROTOCOL;
            }
            
            return "";
        }

        @Override
        public void onNoDeal(Connection connection) {
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.log(Level.FINE, "NPN onNoDeal. Connection={0}",
                        new Object[]{connection});
            }
        }
    }
}
