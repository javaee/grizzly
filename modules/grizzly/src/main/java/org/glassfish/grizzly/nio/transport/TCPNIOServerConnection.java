/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2011 Oracle and/or its affiliates. All rights reserved.
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
import org.glassfish.grizzly.CompletionHandler;
import org.glassfish.grizzly.nio.NIOConnection;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.Future;
import java.util.logging.Level;
import org.glassfish.grizzly.EmptyCompletionHandler;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Context;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.GrizzlyFuture;
import org.glassfish.grizzly.PostProcessor;
import org.glassfish.grizzly.ProcessorResult.Status;
import org.glassfish.grizzly.impl.FutureImpl;
import org.glassfish.grizzly.impl.SafeFutureImpl;
import org.glassfish.grizzly.nio.RegisterChannelResult;
import org.glassfish.grizzly.nio.SelectionKeyHandler;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

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
        final CompletionHandler registerCompletionHandler =
                ((TCPNIOTransport) transport).selectorRegistrationHandler;

        final Future future =
                transport.getNIOChannelDistributor().registerChannelAsync(
                channel, SelectionKey.OP_ACCEPT, this, registerCompletionHandler);
        try {
            future.get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IOException("Error registering server channel key", e);
        }

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
            final FutureImpl future = SafeFutureImpl.create();
            final SocketChannel acceptedChannel = doAccept();
            if (acceptedChannel != null) {
                configureAcceptedChannel(acceptedChannel);
                registerAcceptedChannel(acceptedChannel, future);
            } else {
                acceptListener = future;
                enableIOEvent(IOEvent.SERVER_ACCEPT);
            }

            return future;
        }
    }

    private SocketChannel doAccept() throws IOException {
        final ServerSocketChannel serverChannel =
                (ServerSocketChannel) getChannel();
        return serverChannel.accept();
    }

    private void configureAcceptedChannel(SocketChannel acceptedChannel)
            throws IOException {
        final TCPNIOTransport tcpNIOTransport = (TCPNIOTransport) transport;
        tcpNIOTransport.configureChannel(acceptedChannel);
    }

    private void registerAcceptedChannel(SocketChannel acceptedChannel,
            FutureImpl listener) throws IOException {

        final TCPNIOTransport tcpNIOTransport = (TCPNIOTransport) transport;
        final NIOConnection connection =
                tcpNIOTransport.obtainNIOConnection(acceptedChannel);

        final CompletionHandler handler = (listener == null)
                ? defaultCompletionHandler
                : new RegisterAcceptedChannelCompletionHandler(listener);

        if (processor != null) {
            connection.setProcessor(processor);
        }

        if (processorSelector != null) {
            connection.setProcessorSelector(processorSelector);
        }

        tcpNIOTransport.getNIOChannelDistributor().registerChannelAsync(
                acceptedChannel, 0, connection, handler);
    }

    @Override
    public void preClose() {
        if (acceptListener != null) {
            acceptListener.failure(new IOException("Connection is closed"));
        }

        try {
            ((TCPNIOTransport) transport).unbind(this);
        } catch (IOException e) {
            LOGGER.log(Level.FINE,
                    "Exception occurred, when unbind connection: " + this, e);
        }

        super.preClose();
    }


    /**
     * Method will be called by framework, when async accept will be ready
     *
     * @throws java.io.IOException
     */
    public void onAccept() throws IOException {

        if (!isStandalone()) {
            final SocketChannel acceptedChannel = doAccept();
            if (acceptedChannel == null) {
                return;
            }

            configureAcceptedChannel(acceptedChannel);
            registerAcceptedChannel(acceptedChannel, acceptListener);
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
                registerAcceptedChannel(acceptedChannel, acceptListener);
                acceptListener = null;
            }
        }

        notifyProbesAccept(this);
    }

    @Override
    public void setReadBufferSize(final int readBufferSize) {
        final ServerSocket socket = ((ServerSocketChannel) channel).socket();

        try {
            final int socketReadBufferSize = socket.getReceiveBufferSize();
            if (readBufferSize != -1) {
                if (readBufferSize > socketReadBufferSize) {
                    socket.setReceiveBufferSize(readBufferSize);
                }
            }

            this.readBufferSize = readBufferSize;
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Error setting read buffer size", e);
        }
    }

    @Override
    public void setWriteBufferSize(final int writeBufferSize) {
            this.writeBufferSize = writeBufferSize;
    }

    protected final class RegisterAcceptedChannelCompletionHandler
            extends EmptyCompletionHandler<RegisterChannelResult> {

        private final FutureImpl listener;

        public RegisterAcceptedChannelCompletionHandler() {
            this(null);
        }

        public RegisterAcceptedChannelCompletionHandler(
                FutureImpl listener) {
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

                connection.resetProperties();
                if (listener != null) {
                    listener.result(connection);
                }

                // if not standalone - enable OP_READ after IOEvent.ACCEPTED will be processed
                transport.fireIOEvent(IOEvent.ACCEPTED, connection,
                        !isStandalone() ? enableInterestPostProcessor : null);
            } catch (Exception e) {
                LOGGER.log(Level.FINE, "Exception happened, when "
                        + "trying to accept the connection", e);
            }
        }
    }
    // COMPLETE, COMPLETE_LEAVE, REREGISTER, RERUN, ERROR, TERMINATE, NOT_RUN
    private final static boolean[] isRegisterMap = {true, false, true, false, false, false, true};
    // PostProcessor, which supposed to enable OP_READ interest, once Processor will be notified
    // about Connection ACCEPT
    protected final static PostProcessor enableInterestPostProcessor =
            new EnableReadPostProcessor();

    private static class EnableReadPostProcessor implements PostProcessor {

        @Override
        public void process(Context context, Status status) throws IOException {
            if (isRegisterMap[status.ordinal()]) {
                final NIOConnection nioConnection = (NIOConnection) context.getConnection();
                nioConnection.enableIOEvent(IOEvent.READ);
            }
        }
    }
}
