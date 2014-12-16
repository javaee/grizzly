/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2014 Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.grizzly.*;
import org.glassfish.grizzly.impl.FutureImpl;
import org.glassfish.grizzly.impl.ReadyFutureImpl;
import org.glassfish.grizzly.nio.NIOChannelDistributor;
import org.glassfish.grizzly.nio.RegisterChannelResult;
import org.glassfish.grizzly.nio.SelectionKeyHandler;
import org.glassfish.grizzly.utils.Exceptions;
import org.glassfish.grizzly.utils.Futures;

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
    protected volatile long connectionTimeoutMillis = DEFAULT_CONNECTION_TIMEOUT;

    protected TCPNIOConnectorHandler(final TCPNIOTransport transport) {
        super(transport);
        connectionTimeoutMillis = transport.getConnectionTimeout();
        isReuseAddress = transport.isReuseAddress();
        instantConnectHandler = new InstantConnectHandler();
    }

    @Override
    public void connect(final SocketAddress remoteAddress,
            final SocketAddress localAddress,
            final CompletionHandler<Connection> completionHandler) {

        if (!transport.isBlocking()) {
            connectAsync(remoteAddress, localAddress, completionHandler, false);
        } else {
            connectSync(remoteAddress, localAddress, completionHandler);
        }
    }

    protected void connectSync(SocketAddress remoteAddress, SocketAddress localAddress,
            CompletionHandler<Connection> completionHandler) {

        final FutureImpl<Connection> future = connectAsync(remoteAddress,
                localAddress, completionHandler, true);

        waitNIOFuture(future, completionHandler);
    }

    @Override
    protected FutureImpl<Connection> connectAsync(
            final SocketAddress remoteAddress,
            final SocketAddress localAddress,
            final CompletionHandler<Connection> completionHandler,
            final boolean needFuture) {
        
        final TCPNIOTransport nioTransport = (TCPNIOTransport) transport;
        TCPNIOConnection newConnection = null;
        try {
            final SocketChannel socketChannel =
                    nioTransport.getSelectorProvider().openSocketChannel();

            newConnection = nioTransport.obtainNIOConnection(socketChannel);

            final TCPNIOConnection finalConnection = newConnection;

            final Socket socket = socketChannel.socket();
            
            nioTransport.getChannelConfigurator().preConfigure(
                    nioTransport, socketChannel);
            
            final boolean reuseAddr = isReuseAddress;
            if (reuseAddr != nioTransport.isReuseAddress()) {
                socket.setReuseAddress(reuseAddr);
            }

            if (localAddress != null) {
                socket.bind(localAddress);
            }

            preConfigure(finalConnection);

            finalConnection.setProcessor(getProcessor());
            finalConnection.setProcessorSelector(getProcessorSelector());

            final boolean isConnected = socketChannel.connect(remoteAddress);

            
            final CompletionHandler<Connection> completionHandlerToPass;
            final FutureImpl<Connection> futureToReturn;
            
            if (needFuture) {
                futureToReturn = makeCancellableFuture(finalConnection);
                
                completionHandlerToPass = Futures.toCompletionHandler(
                        futureToReturn, completionHandler);
                
            } else {
                completionHandlerToPass = completionHandler;
                futureToReturn = null;
            }
            
            newConnection.setConnectResultHandler(
                    new TCPNIOConnection.ConnectResultHandler() {
                @Override
                public void connected() throws IOException {
                    onConnectedAsync(finalConnection, completionHandlerToPass);
                }

                @Override
                public void failed(Throwable throwable) {
                    abortConnection(finalConnection,
                            completionHandlerToPass, throwable);
                }
            });

            final NIOChannelDistributor nioChannelDistributor =
                    nioTransport.getNIOChannelDistributor();

            if (nioChannelDistributor == null) {
                throw new IllegalStateException(
                        "NIOChannelDistributor is null. Is Transport running?");
            }

            if (isConnected) {
                nioChannelDistributor.registerChannelAsync(
                        socketChannel, 0, newConnection,
                        instantConnectHandler);
            } else {
                nioChannelDistributor.registerChannelAsync(
                        socketChannel, SelectionKey.OP_CONNECT, newConnection,
                        new RegisterChannelCompletionHandler(newConnection));
            }
            
            return futureToReturn;
        } catch (Exception e) {
            if (newConnection != null) {
                newConnection.closeSilently();
            }

            if (completionHandler != null) {
                completionHandler.failed(e);
            }

            return needFuture ? ReadyFutureImpl.<Connection>create(e) : null;
        }
    }

    protected static void onConnectedAsync(final TCPNIOConnection connection,
            final CompletionHandler<Connection> completionHandler)
            throws IOException {

        final TCPNIOTransport tcpTransport =
                (TCPNIOTransport) connection.getTransport();
        final SocketChannel channel = (SocketChannel) connection.getChannel();
        
        try {
            if (!channel.isConnected()) {
                channel.finishConnect();
            }

            connection.resetProperties();

            // Deregister OP_CONNECT interest
            connection.disableIOEvent(IOEvent.CLIENT_CONNECTED);

            // we can call configure for ready channel
            tcpTransport.getChannelConfigurator().postConfigure(tcpTransport, channel);
        } catch (Exception e) {
            abortConnection(connection, completionHandler, e);
            throw Exceptions.makeIOException(e);
        }
        
        if (connection.notifyReady()) {
            tcpTransport.fireIOEvent(IOEvent.CONNECTED, connection,
                    new EnableReadHandler(completionHandler));
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

    protected void waitNIOFuture(final FutureImpl<Connection> future,
            final CompletionHandler<Connection> completionHandler) {
        
        try {
            future.get(connectionTimeoutMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Futures.notifyFailure(future, completionHandler, e);
        } catch (TimeoutException e) {
            Futures.notifyFailure(future, completionHandler,
                    new IOException("Channel registration on Selector timeout!"));
        } catch (Exception ignored) {
        }
    }

    private static void abortConnection(final TCPNIOConnection connection,
            final CompletionHandler<Connection> completionHandler,
            final Throwable failure) {

        connection.closeSilently();

        if (completionHandler != null) {
            completionHandler.failed(failure);
        }
    }
    
    private class InstantConnectHandler extends
            EmptyCompletionHandler<RegisterChannelResult> {
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

    private static class RegisterChannelCompletionHandler
            extends EmptyCompletionHandler<RegisterChannelResult> {

        private final TCPNIOConnection connection;

        public RegisterChannelCompletionHandler(TCPNIOConnection connection) {
            this.connection = connection;
        }

        @Override
        public void completed(final RegisterChannelResult result) {
            final TCPNIOTransport transport = (TCPNIOTransport) connection.getTransport();
            transport.selectorRegistrationHandler.completed(result);
        }

        @Override
        public void failed(final Throwable throwable) {
            connection.checkConnectFailed(throwable);
        }
    }
    
    // COMPLETE, COMPLETE_LEAVE, REREGISTER, RERUN, ERROR, TERMINATE, NOT_RUN
//    private final static boolean[] isRegisterMap = {true, false, true, false, false, false, true};

    // PostProcessor, which supposed to enable OP_READ interest, once Processor will be notified
    // about Connection CONNECT
    private static final class EnableReadHandler extends IOEventLifeCycleListener.Adapter {

        private final CompletionHandler<Connection> completionHandler;

        private EnableReadHandler(
                final CompletionHandler<Connection> completionHandler) {
            this.completionHandler = completionHandler;
        }

        @Override
        public void onReregister(final Context context) throws IOException {
            onComplete(context, null);
        }

        @Override
        public void onNotRun(final Context context) throws IOException {
            onComplete(context, null);
        }

        @Override
        public void onComplete(final Context context, final Object data)
                throws IOException {
            final TCPNIOConnection connection =
                    (TCPNIOConnection) context.getConnection();

            if (completionHandler != null) {
                completionHandler.completed(connection);
            }

            if (!connection.isStandalone()) {
                connection.enableInitialOpRead();
            }
        }

        @Override
        public void onError(final Context context, final Object description)
                throws IOException {
            context.getConnection().closeSilently();
        }
    }

    /**
     * Return the {@link TCPNIOConnectorHandler} builder.
     * 
     * @param transport {@link TCPNIOTransport}.
     * @return the {@link TCPNIOConnectorHandler} builder.
     */
    public static Builder builder(final TCPNIOTransport transport) {
        return new TCPNIOConnectorHandler.Builder().setTransport(transport);
    }

    public static class Builder extends AbstractSocketConnectorHandler.Builder<Builder> {

        private TCPNIOTransport transport;
        private Boolean reuseAddress;
        private Long timeout;
        private TimeUnit timeoutTimeunit;

        public TCPNIOConnectorHandler build() {
            TCPNIOConnectorHandler handler = (TCPNIOConnectorHandler) super.build();
            if (reuseAddress != null) {
                handler.setReuseAddress(reuseAddress);
            }
            if (timeout != null) {
                handler.setSyncConnectTimeout(timeout, timeoutTimeunit);
            }
            return handler;
        }

        public Builder setTransport(final TCPNIOTransport transport) {
            this.transport = transport;
            return this;
        }

        public Builder setReuseAddress(final boolean reuseAddress) {
            this.reuseAddress = reuseAddress;
            return this;
        }

        public Builder setSyncConnectTimeout(final long timeout, final TimeUnit timeunit) {
            this.timeout = timeout;
            timeoutTimeunit = timeunit;
            return this;
        }

        @Override
        protected AbstractSocketConnectorHandler create() {
            if (transport == null) {
                throw new IllegalStateException(
                        "Unable to create TCPNIOConnectorHandler - transport is null");
            }
            return new TCPNIOConnectorHandler(transport);
        }
    }
}
