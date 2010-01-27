/*
 * 
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2007-2008 Sun Microsystems, Inc. All rights reserved.
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
import com.sun.grizzly.Context;
import com.sun.grizzly.ProcessorResult;
import com.sun.grizzly.nio.NIOConnection;
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
import com.sun.grizzly.AbstractProcessor;
import com.sun.grizzly.Connection;
import com.sun.grizzly.AbstractSocketConnectorHandler;
import com.sun.grizzly.CompletionHandler;
import com.sun.grizzly.impl.FutureImpl;
import com.sun.grizzly.impl.ReadyFutureImpl;
import com.sun.grizzly.nio.RegisterChannelResult;

/**
 * TCP NIO transport client side ConnectorHandler implementation
 * 
 * @author Alexey Stashok
 */
public class TCPNIOConnectorHandler extends AbstractSocketConnectorHandler {
    
    protected static final int DEFAULT_CONNECTION_TIMEOUT = 30000;
    
    protected boolean isReuseAddress;
    protected int connectionTimeout = DEFAULT_CONNECTION_TIMEOUT;

    public TCPNIOConnectorHandler(TCPNIOTransport transport) {
        super(transport);
        TCPNIOTransport nioTransport = (TCPNIOTransport) transport;
        connectionTimeout = nioTransport.getConnectionTimeout();
        isReuseAddress = nioTransport.isReuseAddress();
    }

    @Override
    public Future<Connection> connect(SocketAddress remoteAddress,
            SocketAddress localAddress,
            CompletionHandler<Connection> completionHandler) throws IOException {
        
        if (!transport.isBlocking()) {
            return connectAsync(remoteAddress, localAddress, completionHandler);
        } else {
            return connectSync(remoteAddress, localAddress, completionHandler);
        }
    }

    protected Future<Connection> connectSync(SocketAddress remoteAddress,
            SocketAddress localAddress,
            CompletionHandler<Connection> completionHandler) throws IOException {
        
        Future<Connection> future = connectAsync(remoteAddress, localAddress,
                completionHandler);
        waitNIOFuture(future);

        return future;
    }

    protected Future<Connection> connectAsync(final SocketAddress remoteAddress,
            final SocketAddress localAddress,
            final CompletionHandler<Connection> completionHandler)
            throws IOException {
        SocketChannel socketChannel = SocketChannel.open();
        Socket socket = socketChannel.socket();
        socket.setReuseAddress(isReuseAddress);
        
        if (localAddress != null) {
            socket.bind(localAddress);
        }

        socketChannel.configureBlocking(false);

        TCPNIOTransport nioTransport = (TCPNIOTransport) transport;
        
        TCPNIOConnection newConnection = (TCPNIOConnection)
                nioTransport.obtainNIOConnection(socketChannel);
        
        FutureImpl connectFuture = new FutureImpl();
        
        ConnectorEventProcessor finishConnectProcessor =
                new ConnectorEventProcessor(connectFuture, completionHandler);
        newConnection.setProcessor(finishConnectProcessor);

        try {
            boolean isConnected = socketChannel.connect(remoteAddress);

            if (isConnected) {
                // if connected immediately - register channel on selector with
                // OP_READ interest
                Future<RegisterChannelResult> registerChannelFuture =
                        nioTransport.getNioChannelDistributor().
                        registerChannelAsync(socketChannel, SelectionKey.OP_READ,
                        newConnection, null);

                // Wait until the SelectableChannel will be registered on the Selector
                RegisterChannelResult result = waitNIOFuture(registerChannelFuture);

                // make sure completion handler is called
                nioTransport.registerChannelCompletionHandler.completed(null, result);

                transport.fireIOEvent(IOEvent.CONNECTED, newConnection);
            } else {
                Future registerChannelFuture =
                        nioTransport.getNioChannelDistributor().registerChannelAsync(
                        socketChannel, SelectionKey.OP_CONNECT, newConnection,
                        nioTransport.registerChannelCompletionHandler);

                // Wait until the SelectableChannel will be registered on the Selector
                waitNIOFuture(registerChannelFuture);
            }
        } catch (Exception e) {
            if (completionHandler != null) {
                completionHandler.failed(null, e);
            }
            
            return new ReadyFutureImpl(e);
        }
        
        return connectFuture;
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
                throw new IOException("Unexpected exception connection exception. " +
                        internalException.getClass().getName() + ": " +
                        internalException.getMessage());
            }
        } catch (CancellationException e) {
            throw new IOException("Connection was cancelled!");
        }
    }

    /**
     * Processor, which will be notified, once OP_CONNECT will be ready
     */
    protected class ConnectorEventProcessor extends AbstractProcessor {
        private final FutureImpl<Connection> connectFuture;
        private final CompletionHandler<Connection> completionHandler;

        public ConnectorEventProcessor(FutureImpl<Connection> future,
                CompletionHandler completionHandler) {
            this.connectFuture = future;
            this.completionHandler = completionHandler;
        }
        
        /**
         * Method will be called by framework, when async connect will be completed
         * 
         * @param context processing context
         * @throws java.io.IOException
         */
        @Override
        public ProcessorResult process(Context context)
                throws IOException {
            try {
                final NIOConnection connection = (NIOConnection)
                        context.getConnection();
                
                final TCPNIOTransport transport =
                        (TCPNIOTransport) connection.getTransport();

                final SocketChannel channel = (SocketChannel) connection.getChannel();
                if (!channel.isConnected()) {
                    channel.finishConnect();
                }

                // Unregister OP_CONNECT interest
                transport.getSelectorHandler().unregisterKey(
                        connection.getSelectorRunner(),
                        connection.getSelectionKey(),
                        SelectionKey.OP_CONNECT);

                transport.configureChannel(channel);
                connection.setProcessor(defaultProcessor);
                connection.setProcessorSelector(defaultProcessorSelector);

                // Execute CONNECTED event one more time for default {@link Processor}
                transport.fireIOEvent(IOEvent.CONNECTED, connection);
                
                transport.getSelectorHandler().registerKey(
                        connection.getSelectorRunner(),
                        connection.getSelectionKey(),
                        SelectionKey.OP_READ);

                if (completionHandler != null) {
                    completionHandler.completed(connection, connection);
                }

                connectFuture.result(connection);
            } catch (Exception e) {
                if (completionHandler != null) {
                    completionHandler.failed(null, e);
                }
                
                connectFuture.failure(e);
            }
            return null;
        }

        @Override
        public boolean isInterested(IOEvent ioEvent) {
            return true;
        }

        @Override
        public void setInterested(IOEvent ioEvent, boolean isInterested) {
        }
    }
}
