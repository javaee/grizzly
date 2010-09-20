/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2009-2010 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.grizzly.nio.transport;

import com.sun.grizzly.IOEvent;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.SocketAddress;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import com.sun.grizzly.Connection;
import com.sun.grizzly.AbstractSocketConnectorHandler;
import com.sun.grizzly.CompletionHandler;
import com.sun.grizzly.GrizzlyFuture;
import com.sun.grizzly.impl.ReadyFutureImpl;
import com.sun.grizzly.nio.RegisterChannelResult;

/**
 * UDP NIO transport client side ConnectorHandler implementation
 * 
 * @author Alexey Stashok
 */
public class UDPNIOConnectorHandler extends AbstractSocketConnectorHandler {
    
    protected static final int DEFAULT_CONNECTION_TIMEOUT = 30000;
    
    protected boolean isReuseAddress;
    protected int connectionTimeout = DEFAULT_CONNECTION_TIMEOUT;

    public UDPNIOConnectorHandler(UDPNIOTransport transport) {
        super(transport);
        connectionTimeout = transport.getConnectionTimeout();
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

    protected GrizzlyFuture<Connection> connectAsync(SocketAddress remoteAddress,
            SocketAddress localAddress,
            CompletionHandler<Connection> completionHandler) throws IOException {
        DatagramChannel datagramChannel = DatagramChannel.open();
        DatagramSocket socket = datagramChannel.socket();
        socket.setReuseAddress(isReuseAddress);
        
        if (localAddress != null) {
            socket.bind(localAddress);
        }

        datagramChannel.configureBlocking(false);

        UDPNIOTransport nioTransport = (UDPNIOTransport) transport;
        
        UDPNIOConnection newConnection = nioTransport.obtainNIOConnection(datagramChannel);

        preConfigure(newConnection);
        
        if (remoteAddress != null) {
            datagramChannel.connect(remoteAddress);
        }
        
        newConnection.setProcessor(getProcessor());
        newConnection.setProcessorSelector(getProcessorSelector());

        // if connected immediately - register channel on selector with OP_READ
        // interest
        Future<RegisterChannelResult> registerChannelFuture =
                nioTransport.getNioChannelDistributor().
                registerChannelAsync(datagramChannel,
                newConnection.isStandalone() ? 0 : SelectionKey.OP_READ,
                newConnection, null);

        // Wait until the SelectableChannel will be registered on the Selector
        RegisterChannelResult result = waitNIOFuture(registerChannelFuture);

        // make sure completion handler is called
        nioTransport.registerChannelCompletionHandler.completed(result);
        
        transport.fireIOEvent(IOEvent.CONNECTED, newConnection, null);
        
        if (completionHandler != null) {
            completionHandler.completed(newConnection);
        }
        
        return ReadyFutureImpl.<Connection>create(newConnection);
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
}
