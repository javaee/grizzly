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
import java.net.SocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.grizzly.*;
import org.glassfish.grizzly.impl.FutureImpl;
import org.glassfish.grizzly.impl.SafeFutureImpl;
import org.glassfish.grizzly.nio.RegisterChannelResult;
import org.glassfish.grizzly.nio.SelectionKeyHandler;
import org.glassfish.grizzly.utils.CompletionHandlerAdapter;
import org.glassfish.grizzly.utils.Exceptions;
import org.glassfish.grizzly.utils.Holder;
import org.glassfish.grizzly.utils.NullaryFunction;

/**
 *
 * @author oleksiys
 */
public final class TCPNIOServerConnection extends TCPNIOConnection {

    private static final Logger LOGGER = Grizzly.logger(TCPNIOServerConnection.class);
    private FutureImpl<Connection> acceptListener;
    private final RegisterAcceptedChannelCompletionHandler defaultCompletionHandler;
    private final Object acceptSync = new Object();

    public TCPNIOServerConnection(TCPNIOTransport transport,
            ServerSocketChannel serverSocketChannel) {
        super(transport, serverSocketChannel);
        defaultCompletionHandler =
                new RegisterAcceptedChannelCompletionHandler();
    }

    public void listen() throws IOException {
        final CompletionHandler<RegisterChannelResult> registerCompletionHandler =
                ((TCPNIOTransport) transport).selectorRegistrationHandler;

        final FutureImpl<RegisterChannelResult> future =
                SafeFutureImpl.create();
        
        transport.getNIOChannelDistributor().registerServiceChannelAsync(
                channel, SelectionKey.OP_ACCEPT, this,
                new CompletionHandlerAdapter<RegisterChannelResult, RegisterChannelResult>(
                future, registerCompletionHandler));
        try {
            future.get(10, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            throw Exceptions.makeIOException(e.getCause());
        } catch (Exception e) {
            throw Exceptions.makeIOException(e);
        }

        notifyReady();
        notifyProbesBind(this);
    }

    @Override
    public boolean isBlocking() {
        return transport.isBlocking();
    }

    @Override
    public boolean isStandalone() {
        return transport.isStandalone();
    }

    /**
     * Accept a {@link Connection}. Could be used only in standalone mode.
     * See {@link Connection#configureStandalone(boolean)}.
     *
     * @return {@link Future}
     * @throws java.io.IOException
     */
    public GrizzlyFuture<Connection> accept() throws IOException {
        if (!isStandalone()) {
            throw new IllegalStateException("Accept could be used in standalone mode only");
        }

        final GrizzlyFuture<Connection> future = acceptAsync();

        if (isBlocking()) {
            try {
                future.get();
            } catch (Exception ignored) {
            }
        }

        return future;
    }

    /**
     * Asynchronously accept a {@link Connection}
     *
     * @return {@link Future}
     * @throws java.io.IOException
     */
    protected GrizzlyFuture<Connection> acceptAsync() throws IOException {
        if (!isOpen()) {
            throw new IOException("Connection is closed");
        }

        synchronized (acceptSync) {
            final FutureImpl<Connection> future = SafeFutureImpl.create();
            final SocketChannel acceptedChannel = doAccept();
            if (acceptedChannel != null) {
                configureAcceptedChannel(acceptedChannel);
                final TCPNIOConnection clientConnection =
                        createClientConnection(acceptedChannel);
                registerAcceptedChannel(clientConnection,
                        new RegisterAcceptedChannelCompletionHandler(future),
                        0);
            } else {
                acceptListener = future;
                enableIOEvent(IOEvent.SERVER_ACCEPT);
            }

            return future;
        }
    }

    private SocketChannel doAccept() throws IOException {
        return ((ServerSocketChannel) getChannel()).accept();
    }

    private void configureAcceptedChannel(final SocketChannel acceptedChannel)
            throws IOException {
        final TCPNIOTransport tcpNIOTransport = (TCPNIOTransport) transport;
        tcpNIOTransport.getChannelConfigurator()
                .preConfigure(transport, acceptedChannel);
        tcpNIOTransport.getChannelConfigurator()
                .postConfigure(transport, acceptedChannel);
    }

    private TCPNIOConnection createClientConnection(final SocketChannel acceptedChannel) {
        final TCPNIOTransport tcpNIOTransport = (TCPNIOTransport) transport;
        final TCPNIOConnection connection =
                tcpNIOTransport.obtainNIOConnection(acceptedChannel);

        if (processor != null) {
            connection.setProcessor(processor);
        }

        if (processorSelector != null) {
            connection.setProcessorSelector(processorSelector);
        }

        connection.resetProperties();
        
        return connection;
    }
    
    private void registerAcceptedChannel(final TCPNIOConnection acceptedConnection,
            final CompletionHandler<RegisterChannelResult> completionHandler,
            final int initialSelectionKeyInterest)
            throws IOException {

        final TCPNIOTransport tcpNIOTransport = (TCPNIOTransport) transport;

        tcpNIOTransport.getNIOChannelDistributor().registerChannelAsync(
                acceptedConnection.getChannel(), initialSelectionKeyInterest,
                acceptedConnection, completionHandler);
    }

    @Override
    public void preClose() {
        if (acceptListener != null) {
            acceptListener.failure(new IOException("Connection is closed"));
        }

        ((TCPNIOTransport) transport).unbind(this);

        super.preClose();
    }


    /**
     * Method will be called by framework, when async accept will be ready
     *
     * @throws java.io.IOException
     */
    public void onAccept() throws IOException {

        final TCPNIOConnection acceptedConnection;
        
        if (!isStandalone()) {
            final SocketChannel acceptedChannel = doAccept();
            if (acceptedChannel == null) {
                return;
            }

            configureAcceptedChannel(acceptedChannel);
            acceptedConnection = createClientConnection(acceptedChannel);
            
            notifyProbesAccept(this, acceptedConnection);
            
            registerAcceptedChannel(acceptedConnection,
                    defaultCompletionHandler, SelectionKey.OP_READ);
        } else {
            synchronized (acceptSync) {
                if (acceptListener == null) {
                    TCPNIOServerConnection.this.disableIOEvent(
                            IOEvent.SERVER_ACCEPT);
                    return;
                }

                final SocketChannel acceptedChannel = doAccept();
                if (acceptedChannel == null) {
                    return;
                }

                configureAcceptedChannel(acceptedChannel);
                acceptedConnection = createClientConnection(acceptedChannel);
                
                notifyProbesAccept(this, acceptedConnection);
                
                registerAcceptedChannel(acceptedConnection,
                        new RegisterAcceptedChannelCompletionHandler(acceptListener),
                        0);
                acceptListener = null;
            }
        }
    }
    
    @Override
    public void setReadBufferSize(final int readBufferSize) {
        throw new IllegalStateException("Use TCPNIOTransport.setReadBufferSize()");
    }

    @Override
    public void setWriteBufferSize(final int writeBufferSize) {
        throw new IllegalStateException("Use TCPNIOTransport.setWriteBufferSize()");
    }

    @Override
    public int getReadBufferSize() {
        return transport.getReadBufferSize();
    }

    @Override
    public int getWriteBufferSize() {
        return transport.getWriteBufferSize();
    }

    @Override
    protected void closeGracefully0(final CompletionHandler<Closeable> completionHandler,
            final CloseReason closeReason) {
        terminate0(completionHandler, closeReason);
    }
    
    @Override
    @SuppressWarnings("unchecked")
    protected void resetProperties() {
        localSocketAddressHolder = Holder.lazyHolder(
                new NullaryFunction<SocketAddress>() {

                    @Override
                    public SocketAddress evaluate() {
                        return ((ServerSocketChannel) channel).socket().getLocalSocketAddress();
                    }
                });

        peerSocketAddressHolder = Holder.staticHolder(null);
    }

    
    protected final class RegisterAcceptedChannelCompletionHandler
            extends EmptyCompletionHandler<RegisterChannelResult> {

        private final FutureImpl<Connection> listener;

        public RegisterAcceptedChannelCompletionHandler() {
            this(null);
        }

        public RegisterAcceptedChannelCompletionHandler(
                FutureImpl<Connection> listener) {
            this.listener = listener;
        }

        @Override
        public void completed(RegisterChannelResult result) {
            try {
                final TCPNIOTransport nioTransport = (TCPNIOTransport) transport;

                nioTransport.selectorRegistrationHandler.completed(result);

                final SelectionKeyHandler selectionKeyHandler =
                        nioTransport.getSelectionKeyHandler();
                final SelectionKey acceptedConnectionKey =
                        result.getSelectionKey();
                final TCPNIOConnection connection =
                        (TCPNIOConnection) selectionKeyHandler.getConnectionForKey(acceptedConnectionKey);

                if (listener != null) {
                    listener.result(connection);
                }

                if (connection.notifyReady()) {
                    transport.fireIOEvent(IOEvent.ACCEPTED, connection, null);
                }
            } catch (Exception e) {
                LOGGER.log(Level.FINE, "Exception happened, when "
                        + "trying to accept the connection", e);
            }
        }
    }        
}
