/*
 * 
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2007-2010 Sun Microsystems, Inc. All rights reserved.
 * 
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License. You can obtain
 * a copy of the License at https://glassfish.dev.java.net/public/CDDL+GPL.html
 * or glassfish/bootstrap/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 * 
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/bootstrap/legal/LICENSE.txt.
 * Sun designates this particular file as subject to the "Classpath" exception
 * as provided by Sun in the GPL Version 2 section of the License file that
 * accompanied this code.  If applicable, add the following below the License
 * Header, with the fields enclosed by brackets [] replaced by your own
 * identifying information: "Portions Copyrighted [year]
 * [name of copyright owner]"
 * 
 * Contributor(s):
 * 
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
 *
 */
package com.sun.grizzly.nio.transport;

import com.sun.grizzly.IOEvent;
import com.sun.grizzly.CompletionHandler;
import com.sun.grizzly.nio.NIOConnection;
import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.Future;
import java.util.logging.Level;
import com.sun.grizzly.EmptyCompletionHandler;
import com.sun.grizzly.Connection;
import com.sun.grizzly.Grizzly;
import com.sun.grizzly.GrizzlyFuture;
import com.sun.grizzly.impl.FutureImpl;
import com.sun.grizzly.impl.SafeFutureImpl;
import com.sun.grizzly.nio.RegisterChannelResult;
import com.sun.grizzly.nio.SelectionKeyHandler;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 *
 * @author oleksiys
 */
public final class TCPNIOServerConnection extends TCPNIOConnection {

    private static Logger logger = Grizzly.logger(TCPNIOServerConnection.class);
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
                transport.getNioChannelDistributor().registerChannelAsync(
                channel, SelectionKey.OP_ACCEPT, this, registerCompletionHandler);
        try {
            future.get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IOException("Error registering server channel key", e);
        }
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
     * Accept a {@link Connection}. Could be used only in standalone mode. See {@link Connection#configureStandalone(boolean)}.
     *
     * @return {@link Future}
     * @throws java.io.IOException
     */
    public GrizzlyFuture<Connection> accept() throws IOException {
        if (!isStandalone()) {
            throw new IllegalStateException("Accept could be used in standlone mode only");
        }

        final GrizzlyFuture<Connection> future = acceptAsync();

        if (isBlocking()) {
            try {
                future.get();
            } catch (Exception e) {
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
        final SocketChannel acceptedChannel = serverChannel.accept();
        return acceptedChannel;
    }

    private void configureAcceptedChannel(SocketChannel acceptedChannel) throws IOException {
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
        } else {
            connection.setProcessor(transport.getProcessor());
        }

        if (processorSelector != null) {
            connection.setProcessorSelector(processorSelector);
        } else {
            connection.setProcessorSelector(transport.getProcessorSelector());
        }

        tcpNIOTransport.getNioChannelDistributor().registerChannelAsync(
                acceptedChannel, SelectionKey.OP_READ, connection, handler);
    }

    @Override
    public void preClose() {
        if (acceptListener != null) {
            acceptListener.failure(new IOException("Connection is closed"));
        }

        try {
            ((TCPNIOTransport) transport).unbind(this);
        } catch (IOException e) {
            logger.log(Level.FINE,
                    "Exception occurred, when unbind connection: " + this, e);
        }

        super.preClose();
    }

    protected void throwUnsupportReadWrite() {
        throw new UnsupportedOperationException("TCPNIOServerConnection "
                + "doesn't support neither read nor write operations.");
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
                        (TCPNIOConnection) selectionKeyHandler.
                        getConnectionForKey(acceptedConnectionKey);

                connection.resetAddresses();
                if (listener != null) {
                    listener.result(connection);
                }

                transport.fireIOEvent(IOEvent.ACCEPTED, connection, null);
            } catch (Exception e) {
                logger.log(Level.FINE, "Exception happened, when "
                        + "trying to accept the connection", e);
            }
        }
    }
}
