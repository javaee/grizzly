/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2010 Oracle and/or its affiliates. All rights reserved.
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
package org.glassfish.grizzly.nio.transport;

import org.glassfish.grizzly.IOEvent;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.AbstractSocketConnectorHandler;
import org.glassfish.grizzly.CompletionHandler;
import org.glassfish.grizzly.GrizzlyFuture;
import org.glassfish.grizzly.impl.FutureImpl;
import org.glassfish.grizzly.impl.ReadyFutureImpl;
import org.glassfish.grizzly.impl.SafeFutureImpl;
import org.glassfish.grizzly.nio.RegisterChannelResult;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.grizzly.Context;
import org.glassfish.grizzly.EmptyCompletionHandler;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.PostProcessor;
import org.glassfish.grizzly.ProcessorResult.Status;
import org.glassfish.grizzly.nio.NIOConnection;
import org.glassfish.grizzly.nio.SelectionKeyHandler;

/**
 * TCP NIO transport client side ConnectorHandler implementation
 * 
 * @author Alexey Stashok
 */
public class TCPNIOConnectorHandler extends AbstractSocketConnectorHandler {

    private static final Logger LOGGER = Grizzly.logger(TCPNIOConnectorHandler.class);
    protected static final int DEFAULT_CONNECTION_TIMEOUT = 30000;
    private final InstantConnectHandler instantConnectHandler;
    protected boolean isReuseAddress;
    protected int connectionTimeout = DEFAULT_CONNECTION_TIMEOUT;

    public TCPNIOConnectorHandler(TCPNIOTransport transport) {
        super(transport);
        connectionTimeout = transport.getConnectionTimeout();
        isReuseAddress = transport.isReuseAddress();
        instantConnectHandler = new InstantConnectHandler();
    }

    @Override
    public GrizzlyFuture<Connection> connect(SocketAddress remoteAddress,
            SocketAddress localAddress,
            CompletionHandler<Connection> completionHandler) throws IOException {

        if (!transport.isBlocking()) {
            return connectAsync(remoteAddress, localAddress, completionHandler);
        } else {
            return connectSync(remoteAddress, localAddress, completionHandler);
        }
    }

    protected GrizzlyFuture<Connection> connectSync(SocketAddress remoteAddress,
            SocketAddress localAddress,
            CompletionHandler<Connection> completionHandler) throws IOException {

        GrizzlyFuture<Connection> future = connectAsync(remoteAddress,
                localAddress, completionHandler);
        waitNIOFuture(future);

        return future;
    }

    protected GrizzlyFuture<Connection> connectAsync(
            final SocketAddress remoteAddress,
            final SocketAddress localAddress,
            final CompletionHandler<Connection> completionHandler)
            throws IOException {
        final SocketChannel socketChannel = SocketChannel.open();
        final Socket socket = socketChannel.socket();
        socket.setReuseAddress(isReuseAddress);

        if (localAddress != null) {
            socket.bind(localAddress);
        }

        socketChannel.configureBlocking(false);

        final TCPNIOTransport nioTransport = (TCPNIOTransport) transport;
        final TCPNIOConnection newConnection = nioTransport.obtainNIOConnection(socketChannel);

        preConfigure(newConnection);

        newConnection.setProcessor(getProcessor());
        newConnection.setProcessorSelector(getProcessorSelector());

        try {
            final FutureImpl connectFuture = SafeFutureImpl.create();

            final boolean isConnected = socketChannel.connect(remoteAddress);

            newConnection.setConnectHandler(
                    new Callable<Connection>() {

                        @Override
                        public Connection call() throws Exception {
                            onConnectedAsync(newConnection, connectFuture,
                                    completionHandler);
                            return null;
                        }
                    });

            final GrizzlyFuture<RegisterChannelResult> registerChannelFuture;

            if (isConnected) {
                registerChannelFuture =
                        nioTransport.getNioChannelDistributor().registerChannelAsync(
                        socketChannel, 0, newConnection,
                        instantConnectHandler);
            } else {
                registerChannelFuture =
                        nioTransport.getNioChannelDistributor().registerChannelAsync(
                        socketChannel, SelectionKey.OP_CONNECT, newConnection,
                        nioTransport.selectorRegistrationHandler);
            }

            registerChannelFuture.markForRecycle(false);

            return connectFuture;
        } catch (Exception e) {
            if (completionHandler != null) {
                completionHandler.failed(e);
            }

            return ReadyFutureImpl.create(e);
        }
    }

    protected static void onConnectedAsync(TCPNIOConnection connection,
            FutureImpl<Connection> connectFuture,
            CompletionHandler<Connection> completionHandler) throws IOException {

        try {
            final TCPNIOTransport tcpTransport =
                    (TCPNIOTransport) connection.getTransport();

            final SocketChannel channel = (SocketChannel) connection.getChannel();
            if (!channel.isConnected()) {
                channel.finishConnect();
            }

            connection.resetAddresses();

            // Unregister OP_CONNECT interest
            connection.disableIOEvent(IOEvent.CLIENT_CONNECTED);

            tcpTransport.configureChannel(channel);

            tcpTransport.fireIOEvent(IOEvent.CONNECTED, connection,
                    new EnableReadPostProcessor(connectFuture, completionHandler));

        } catch (Exception e) {
            if (completionHandler != null) {
                completionHandler.failed(e);
            }

            connectFuture.failure(e);

            throw new IOException("Connect exception", e);
        }
    }

    public boolean isReuseAddress() {
        return isReuseAddress;
    }

    public void setReuseAddress(boolean isReuseAddress) {
        this.isReuseAddress = isReuseAddress;
    }

    public int getConnectionTimeout() {
        return connectionTimeout;
    }

    public void setConnectionTimeout(int connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    protected <E> E waitNIOFuture(Future<E> future) throws IOException {
        try {
            return future.get(connectionTimeout, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            throw new IOException("Connection was interrupted!");
        } catch (TimeoutException e) {
            throw new IOException("Channel registration on Selector timeout!");
        } catch (ExecutionException e) {
            Throwable internalException = e.getCause();
            if (internalException instanceof IOException) {
                throw (IOException) internalException;
            } else {
                throw new IOException("Unexpected exception connection exception. "
                        + internalException.getClass().getName() + ": "
                        + internalException.getMessage());
            }
        } catch (CancellationException e) {
            throw new IOException("Connection was cancelled!");
        }
    }

    private class InstantConnectHandler extends EmptyCompletionHandler<RegisterChannelResult> {

        @Override
        public void completed(RegisterChannelResult result) {
            final TCPNIOTransport transport =
                    (TCPNIOTransport) TCPNIOConnectorHandler.this.transport;

            transport.selectorRegistrationHandler.completed(result);

            final SelectionKey selectionKey = result.getSelectionKey();
            final SelectionKeyHandler selectionKeyHandler = transport.getSelectionKeyHandler();

            final TCPNIOConnection connection =
                    (TCPNIOConnection) selectionKeyHandler.getConnectionForKey(selectionKey);

            try {
                connection.onConnect();

            } catch (Exception e) {
                LOGGER.log(Level.FINE, "Exception happened, when "
                        + "trying to connect the channel", e);
            }
        }
    }
    // COMPLETE, COMPLETE_LEAVE, REREGISTER, RERUN, ERROR, TERMINATE, NOT_RUN
    private final static boolean[] isRegisterMap = {true, false, true, false, false, false, true};

    // PostProcessor, which supposed to enable OP_READ interest, once Processor will be notified
    // about Connection CONNECT
    private static class EnableReadPostProcessor implements PostProcessor {

        private final FutureImpl<Connection> connectFuture;
        private final CompletionHandler<Connection> completionHandler;

        private EnableReadPostProcessor(FutureImpl connectFuture,
                CompletionHandler<Connection> completionHandler) {
            this.connectFuture = connectFuture;
            this.completionHandler = completionHandler;
        }

        @Override
        public void process(Context context, Status status) throws IOException {
            if (isRegisterMap[status.ordinal()]) {
                final NIOConnection connection = (NIOConnection) context.getConnection();

                if (completionHandler != null) {
                    completionHandler.completed(connection);
                }

                connectFuture.result(connection);

                if (!connection.isStandalone()) {
                    connection.enableIOEvent(IOEvent.READ);
                }
            }
        }
    }
}
