/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2009-2011 Oracle and/or its affiliates. All rights reserved.
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
import java.net.DatagramSocket;
import java.net.SocketAddress;
import java.nio.channels.DatagramChannel;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.AbstractSocketConnectorHandler;
import org.glassfish.grizzly.CompletionHandler;
import org.glassfish.grizzly.Context;
import org.glassfish.grizzly.EmptyCompletionHandler;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.GrizzlyFuture;
import org.glassfish.grizzly.IOEventProcessingHandler;
import org.glassfish.grizzly.ProcessorResult.Status;
import org.glassfish.grizzly.impl.FutureImpl;
import org.glassfish.grizzly.impl.ReadyFutureImpl;
import org.glassfish.grizzly.impl.SafeFutureImpl;
import org.glassfish.grizzly.nio.NIOChannelDistributor;
import org.glassfish.grizzly.nio.NIOConnection;
import org.glassfish.grizzly.nio.RegisterChannelResult;

/**
 * UDP NIO transport client side ConnectorHandler implementation
 * 
 * @author Alexey Stashok
 */
public class UDPNIOConnectorHandler extends AbstractSocketConnectorHandler {

    private static final Logger LOGGER = Grizzly.logger(UDPNIOConnectorHandler.class);

    protected static final int DEFAULT_CONNECTION_TIMEOUT = 30000;
    protected boolean isReuseAddress;
    protected volatile long connectionTimeoutMillis = DEFAULT_CONNECTION_TIMEOUT;

    protected UDPNIOConnectorHandler(UDPNIOTransport transport) {
        super(transport);
        connectionTimeoutMillis = transport.getConnectionTimeout();
        isReuseAddress = transport.isReuseAddress();
    }

    /**
     * Creates non-connected UDP {@link Connection}.
     *
     * @return non-connected UDP {@link Connection}.
     * @throws java.io.IOException
     */
    public GrizzlyFuture<Connection> connect() throws IOException {
        return connect(null, null, null);
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
        GrizzlyFuture<Connection> future = connectAsync(remoteAddress, localAddress,
                completionHandler);
        waitNIOFuture(future);

        return future;
    }

    protected GrizzlyFuture<Connection> connectAsync(
            final SocketAddress remoteAddress,
            final SocketAddress localAddress,
            final CompletionHandler<Connection> completionHandler)
            throws IOException {

        final UDPNIOTransport nioTransport = (UDPNIOTransport) transport;
        UDPNIOConnection newConnection = null;

        try {
            final DatagramChannel datagramChannel = DatagramChannel.open();
            final DatagramSocket socket = datagramChannel.socket();

            newConnection = nioTransport.obtainNIOConnection(datagramChannel);

            socket.setReuseAddress(isReuseAddress);

            if (localAddress != null) {
                socket.bind(localAddress);
            }

            datagramChannel.configureBlocking(false);

            if (remoteAddress != null) {
                datagramChannel.connect(remoteAddress);
            }

            preConfigure(newConnection);

            newConnection.setProcessor(getProcessor());
            newConnection.setProcessorSelector(getProcessorSelector());

            final FutureImpl<Connection> connectFuture = SafeFutureImpl.create();

            final NIOChannelDistributor nioChannelDistributor =
                    nioTransport.getNIOChannelDistributor();

            if (nioChannelDistributor == null) {
                throw new IllegalStateException(
                        "NIOChannelDistributor is null. Is Transport running?");
            }

            // if connected immediately - register channel on selector with NO_INTEREST
            // interest
            final GrizzlyFuture<RegisterChannelResult> registerChannelFuture =
                    nioChannelDistributor.registerChannelAsync(datagramChannel,
                    0, newConnection,
                    new ConnectHandler(newConnection, connectFuture,
                    completionHandler));

            registerChannelFuture.markForRecycle(false);

            return connectFuture;
        } catch (Exception e) {
            if (newConnection != null) {
                newConnection.close();
            }

            if (completionHandler != null) {
                completionHandler.failed(e);
            }

            return ReadyFutureImpl.create(e);
        }

    }

    public boolean isReuseAddress() {
        return isReuseAddress;
    }

    public void setReuseAddress(boolean isReuseAddress) {
        this.isReuseAddress = isReuseAddress;
    }

    public long getSyncConnectTimeout(final TimeUnit timeUnit) {
        return timeUnit.convert(connectionTimeoutMillis, TimeUnit.MILLISECONDS);
    }

    public void setSyncConnectTimeout(final long timeout, final TimeUnit timeUnit) {
        this.connectionTimeoutMillis = TimeUnit.MILLISECONDS.convert(timeout, timeUnit);
    }

    protected <E> E waitNIOFuture(Future<E> future) throws IOException {
        try {
            return future.get(connectionTimeoutMillis, TimeUnit.MILLISECONDS);
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

    private static void fireConnectEvent(UDPNIOConnection connection,
            FutureImpl<Connection> connectFuture,
            CompletionHandler<Connection> completionHandler) throws IOException {

        try {
            final UDPNIOTransport udpTransport =
                    (UDPNIOTransport) connection.getTransport();

            udpTransport.fireIOEvent(IOEvent.CONNECTED, connection,
                    new EnableReadHandler(connectFuture, completionHandler));

        } catch (Exception e) {
            abortConnection(connection, connectFuture, completionHandler, e);
            throw new IOException("Connect exception", e);
        }
    }

    private static void abortConnection(final UDPNIOConnection connection,
            final FutureImpl<Connection> connectFuture,
            final CompletionHandler<Connection> completionHandler,
            final Throwable failure) {

        try {
            connection.close();
        } catch (IOException ignored) {
        }

        if (completionHandler != null) {
            completionHandler.failed(failure);
        }

        connectFuture.failure(failure);
    }

    private final class ConnectHandler extends EmptyCompletionHandler<RegisterChannelResult> {

        private final UDPNIOConnection connection;
        private final FutureImpl<Connection> connectFuture;
        private final CompletionHandler<Connection> completionHandler;

        private ConnectHandler(final UDPNIOConnection connection,
                final FutureImpl<Connection> connectFuture,
                final CompletionHandler<Connection> completionHandler) {
            this.connection = connection;
            this.connectFuture = connectFuture;
            this.completionHandler = completionHandler;
        }

        @Override
        public void completed(RegisterChannelResult result) {
            final UDPNIOTransport transport =
                    (UDPNIOTransport) UDPNIOConnectorHandler.this.transport;

            transport.registerChannelCompletionHandler.completed(result);

            try {
                connection.onConnect();

                fireConnectEvent(connection, connectFuture, completionHandler);
            } catch (Exception e) {
                LOGGER.log(Level.FINE, "Exception happened, when "
                        + "trying to connect the channel", e);
            }
        }

        @Override
        public void failed(final Throwable throwable) {
            abortConnection(connection, connectFuture, completionHandler, throwable);
        }
    }
    // COMPLETE, COMPLETE_LEAVE, REREGISTER, RERUN, ERROR, TERMINATE, NOT_RUN
    private final static boolean[] isRegisterMap = {true, false, true, false, false, false, true};

    // PostProcessor, which supposed to enable OP_READ interest, once Processor will be notified
    // about Connection CONNECT
    private static class EnableReadHandler implements IOEventProcessingHandler {

        private final FutureImpl<Connection> connectFuture;
        private final CompletionHandler<Connection> completionHandler;

        private EnableReadHandler(FutureImpl<Connection> connectFuture,
                CompletionHandler<Connection> completionHandler) {
            this.connectFuture = connectFuture;
            this.completionHandler = completionHandler;
        }

        @Override
        public void onComplete(Context context, Status status) throws IOException {
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

        @Override
        public void onSuspend(Context context) {
        }

        @Override
        public void onResume(Context context) {
        }
    }

    /**
     * Return the {@link UDPNIOConnectorHandler} builder.
     *
     * @param transport {@link UDPNIOTransport}.
     * @return the {@link UDPNIOConnectorHandler} builder.
     */
    public static Builder builder(final UDPNIOTransport transport) {
        return new UDPNIOConnectorHandler.Builder(transport);
    }

    public static class Builder extends AbstractSocketConnectorHandler.Builder<Builder> {
        protected Builder(final UDPNIOTransport transport) {
            super(new UDPNIOConnectorHandler(transport));
        }

        public UDPNIOConnectorHandler build() {
            return (UDPNIOConnectorHandler) connectorHandler;
        }

        public Builder setReuseAddress(final boolean isReuseAddress) {
            ((UDPNIOConnectorHandler) connectorHandler).setReuseAddress(isReuseAddress);
            return this;
        }

        public Builder setSyncConnectTimeout(final long timeout, final TimeUnit timeunit) {
            ((UDPNIOConnectorHandler) connectorHandler).setSyncConnectTimeout(timeout, timeunit);
            return this;
        }
    }
}
