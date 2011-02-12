/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011 Oracle and/or its affiliates. All rights reserved.
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
package org.glassfish.grizzly.aio.transport;

import org.glassfish.grizzly.IOEvent;
import java.io.IOException;
import java.net.StandardSocketOption;
import java.net.SocketAddress;
import java.nio.channels.AsynchronousSocketChannel;
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
import java.util.logging.Logger;
import org.glassfish.grizzly.Context;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.PostProcessor;
import org.glassfish.grizzly.ProcessorResult.Status;
import org.glassfish.grizzly.aio.AIOConnection;

/**
 * TCP NIO transport client side ConnectorHandler implementation
 * 
 * @author Alexey Stashok
 */
public class TCPAIOConnectorHandler extends AbstractSocketConnectorHandler {
    
    private static final Logger LOGGER = Grizzly.logger(TCPAIOConnectorHandler.class);
    protected static final int DEFAULT_CONNECTION_TIMEOUT = 30000;
    protected boolean isReuseAddress;
    protected volatile long connectionTimeoutMillis = DEFAULT_CONNECTION_TIMEOUT;

    protected TCPAIOConnectorHandler(final TCPAIOTransport transport) {
        super(transport);
        connectionTimeoutMillis = transport.getConnectionTimeout();
        isReuseAddress = transport.isReuseAddress();
    }

    @Override
    public GrizzlyFuture<Connection> connect(final SocketAddress remoteAddress,
            final SocketAddress localAddress,
            final CompletionHandler<Connection> completionHandler) throws IOException {

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
        final TCPAIOTransport aioTransport = (TCPAIOTransport) transport;

        final AsynchronousSocketChannel socketChannel =
                AsynchronousSocketChannel.open(aioTransport.getAsynchronousChannelGroup());

        socketChannel.setOption(StandardSocketOption.SO_REUSEADDR,
                isReuseAddress);

        if (localAddress != null) {
            socketChannel.bind(localAddress);
        }

        final TCPAIOConnection connection = aioTransport.obtainAIOConnection(socketChannel);

        preConfigure(connection);

        connection.setProcessor(getProcessor());
        connection.setProcessorSelector(getProcessorSelector());

        try {
            final FutureImpl connectFuture = SafeFutureImpl.create();

            socketChannel.connect(remoteAddress, null,
                    new java.nio.channels.CompletionHandler<Void, Object>()  {

                        @Override
                        public void completed(Void result, Object attachment) {
                            try {

                                connection.resetProperties();

                                aioTransport.configureChannel(socketChannel);
                                
                                connection.onConnect();
                                
                                aioTransport.fireIOEvent(IOEvent.CONNECTED, connection,
                                        new EnableReadPostProcessor(connectFuture, completionHandler));

                            } catch (Exception e) {
                                failed(e, attachment);
                            }
                        }

                        @Override
                        public void failed(Throwable e, Object attachment) {
                            if (completionHandler != null) {
                                completionHandler.failed(e);
                            }

                            connectFuture.failure(e);
                        }
                    });
            
            return connectFuture;
        } catch (Exception e) {
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
                final AIOConnection connection = (AIOConnection) context.getConnection();

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

    /**
     * Return the {@link TCPNIOConnectorHandler} builder.
     * 
     * @param transport {@link TCPNIOTransport}.
     * @return the {@link TCPNIOConnectorHandler} builder.
     */
    public static Builder builder(final TCPAIOTransport transport) {
        return new TCPAIOConnectorHandler.Builder(transport);
    }

    public static class Builder extends AbstractSocketConnectorHandler.Builder<Builder> {
        protected Builder(final TCPAIOTransport transport) {
            super(new TCPAIOConnectorHandler(transport));
        }

        public TCPAIOConnectorHandler build() {
            return (TCPAIOConnectorHandler) connectorHandler;
        }

        public Builder setReuseAddress(final boolean isReuseAddress) {
            ((TCPAIOConnectorHandler) connectorHandler).setReuseAddress(isReuseAddress);
            return this;
        }

        public Builder setSyncConnectTimeout(final long timeout, final TimeUnit timeunit) {
            ((TCPAIOConnectorHandler) connectorHandler).setSyncConnectTimeout(timeout, timeunit);
            return this;
        }
    }
}
