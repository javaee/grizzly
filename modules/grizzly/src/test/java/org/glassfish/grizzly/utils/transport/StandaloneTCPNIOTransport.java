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
package org.glassfish.grizzly.utils.transport;

import org.glassfish.grizzly.AbstractSocketConnectorHandler;
import org.glassfish.grizzly.CompletionHandler;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.ConnectionProbe;
import org.glassfish.grizzly.Context;
import org.glassfish.grizzly.EmptyCompletionHandler;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.GrizzlyFuture;
import org.glassfish.grizzly.Event;
import org.glassfish.grizzly.PortRange;
import org.glassfish.grizzly.impl.FutureImpl;
import org.glassfish.grizzly.nio.NIOChannelDistributor;
import org.glassfish.grizzly.nio.NIOConnection;
import org.glassfish.grizzly.nio.RegisterChannelResult;
import org.glassfish.grizzly.nio.SelectionKeyHandler;
import org.glassfish.grizzly.nio.transport.TCPNIOConnection;
import org.glassfish.grizzly.nio.transport.TCPNIOConnectorHandler;
import org.glassfish.grizzly.nio.transport.TCPNIOServerConnection;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import org.glassfish.grizzly.utils.Exceptions;
import org.glassfish.grizzly.utils.Futures;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.Lock;
import java.util.logging.Level;
import java.util.logging.Logger;

public class StandaloneTCPNIOTransport extends TCPNIOTransport {


    // -------------------------------------------------- Methods from Transport


    /**
     * {@inheritDoc}
     */
    @Override
    public StandaloneTCPNIOServerConnection bind(final int port) throws IOException {
        return bind(new InetSocketAddress(port));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public StandaloneTCPNIOServerConnection bind(final String host, final int port)
            throws IOException {
        return bind(host, port, getServerConnectionBackLog());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public StandaloneTCPNIOServerConnection bind(final String host, final int port,
                                       final int backlog) throws IOException {
        return bind(new InetSocketAddress(host, port), backlog);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public StandaloneTCPNIOServerConnection bind(final SocketAddress socketAddress)
            throws IOException {
        return bind(socketAddress, getServerConnectionBackLog());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public StandaloneTCPNIOServerConnection bind(final SocketAddress socketAddress,
                                       final int backlog)
            throws IOException {
        final Lock lock = state.getStateLocker().writeLock();
        lock.lock();
        StandaloneTCPNIOServerConnection serverConnection = null;
        final ServerSocketChannel serverSocketChannel =
                getSelectorProvider().openServerSocketChannel();
        try {
            final ServerSocket serverSocket = serverSocketChannel.socket();
            serverSocket.setReuseAddress(isReuseAddress());
            serverSocket.setSoTimeout(getServerSocketSoTimeout());

            serverSocket.bind(socketAddress, backlog);

            serverSocketChannel.configureBlocking(false);

            serverConnection = obtainServerNIOConnection(serverSocketChannel);
            serverConnections.add(serverConnection);
            serverConnection.resetProperties();

            if (!isStopped()) {
                listenServerConnection(serverConnection);
            }

            return serverConnection;
        } catch (Exception e) {
            if (serverConnection != null) {
                serverConnections.remove(serverConnection);

                serverConnection.closeSilently();
            } else {
                try {
                    serverSocketChannel.close();
                } catch (IOException ignored) {
                }
            }

            throw Exceptions.makeIOException(e);
        } finally {
            lock.unlock();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TCPNIOServerConnection bind(final String host,
                                       final PortRange portRange, final int backlog) throws IOException {

        IOException ioException;

        final int lower = portRange.getLower();
        final int range = portRange.getUpper() - lower + 1;

        int offset = RANDOM.nextInt(range);
        final int start = offset;

        do {
            final int port = lower + offset;

            try {
                return bind(host, port, backlog);
            } catch (IOException e) {
                ioException = e;
            }

            offset = (offset + 1) % range;
        } while (offset != start);

        throw ioException;
    }



    // --------------------------------------------------------- Private Methods


    StandaloneTCPNIOServerConnection obtainServerNIOConnection(final ServerSocketChannel channel) {
        final StandaloneTCPNIOServerConnection connection = new StandaloneTCPNIOServerConnection(this, channel);
        configureNIOConnection(connection);

        return connection;
    }

    void configureNIOConnection(final TCPNIOConnection connection) {
        connection.configureBlocking(isBlocking);
        connection.setProcessor(processor);
        ((StandaloneTCPNIOServerConnection) connection).setMonitoringProbes(connectionMonitoringConfig.getProbes());
    }

    private void listenServerConnection(TCPNIOServerConnection serverConnection)
                throws IOException {
            serverConnection.listen();
    }


    // ---------------------------------------------------------- Nested Classes


    public static final class StandaloneTCPNIOServerConnection extends TCPNIOServerConnection {


        // -------------------------------------------------------- Constructors


        public StandaloneTCPNIOServerConnection(StandaloneTCPNIOTransport transport, ServerSocketChannel serverSocketChannel) {
            super(transport, serverSocketChannel);
        }


        // ------------------------------------------------------ Public Methods


        public GrizzlyFuture<Connection> accept() throws IOException {

            final GrizzlyFuture<Connection> future = acceptAsync();

            if (isBlocking()) {
                try {
                    future.get();
                } catch (Exception ignored) {
                }
            }

            return future;
        }


        @Override
        protected void setMonitoringProbes(ConnectionProbe[] monitoringProbes) {
            super.setMonitoringProbes(monitoringProbes);
        }

        @Override
        protected void resetProperties() {
            super.resetProperties();
        }
    }

}
