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

import com.sun.grizzly.Buffer;
import com.sun.grizzly.CompletionHandler;
import com.sun.grizzly.ReadResult;
import com.sun.grizzly.Transformer;
import com.sun.grizzly.WriteResult;
import com.sun.grizzly.nio.AbstractNIOConnection;
import com.sun.grizzly.IOEvent;
import com.sun.grizzly.nio.SelectorRunner;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.SocketAddress;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;
import com.sun.grizzly.Connection;
import com.sun.grizzly.Grizzly;
import com.sun.grizzly.Interceptor;

/**
 * {@link com.sun.grizzly.Connection} implementation
 * for the {@link UDPNIOTransport}
 *
 * @author Alexey Stashok
 */
public class UDPNIOConnection extends AbstractNIOConnection {
    private static Logger logger = Grizzly.logger(UDPNIOConnection.class);

    public UDPNIOConnection(UDPNIOTransport transport,
            DatagramChannel channel) {
        super(transport);
        
        this.channel = channel;

        setReadBufferSize(transport.getReadBufferSize());
        setWriteBufferSize(transport.getWriteBufferSize());
    }

    public boolean isConnected() {
        return channel != null && ((DatagramChannel) channel).isConnected();
    }

    public Future register() throws IOException {
        return transport.getNioChannelDistributor().registerChannelAsync(
                channel, SelectionKey.OP_READ, this,
                ((UDPNIOTransport) transport).registerChannelCompletionHandler);
    }

    @Override
    protected void setSelectionKey(SelectionKey selectionKey) {
        super.setSelectionKey(selectionKey);
    }

    @Override
    protected void setSelectorRunner(SelectorRunner selectorRunner) {
        super.setSelectorRunner(selectorRunner);
    }

    @Override
    protected void preClose() {
        try {
            transport.fireIOEvent(IOEvent.CLOSED, this);
        } catch (IOException e) {
            logger.log(Level.FINE, "Unexpected IOExcption occurred, " +
                    "when firing CLOSE event");
        }
    }

    /**
     * Returns the address of the endpoint this <tt>Connection</tt> is
     * connected to, or <tt>null</tt> if it is unconnected.
     * @return the address of the endpoint this <tt>Connection</tt> is
     *         connected to, or <tt>null</tt> if it is unconnected.
     */
    @Override
    public SocketAddress getPeerAddress() {
        if (channel != null) {
            return ((DatagramChannel) channel).socket().getRemoteSocketAddress();
        }

        return null;
    }
    
    /**
     * Returns the local address of this <tt>Connection</tt>,
     * or <tt>null</tt> if it is unconnected.
     * @return the local address of this <tt>Connection</tt>,
     *      or <tt>null</tt> if it is unconnected.
     */
    @Override
    public SocketAddress getLocalAddress() {
        if (channel != null) {
            return ((DatagramChannel) channel).socket().getLocalSocketAddress();
        }

        return null;
    }

    @Override
    public void setReadBufferSize(final int readBufferSize) {
        final DatagramSocket socket = ((DatagramChannel) channel).socket();

        try {
            final int socketReadBufferSize = socket.getReceiveBufferSize();
            if (readBufferSize != -1) {
                if (readBufferSize > socketReadBufferSize) {
                    socket.setReceiveBufferSize(readBufferSize);
                }
                super.setReadBufferSize(readBufferSize);
            } else {
                super.setReadBufferSize(socketReadBufferSize);
            }
        } catch (IOException e) {
            logger.log(Level.WARNING, "Error setting read buffer size", e);
        }
    }

    @Override
    public void setWriteBufferSize(int writeBufferSize) {
        final DatagramSocket socket = ((DatagramChannel) channel).socket();

        try {
            final int socketWriteBufferSize = socket.getSendBufferSize();
            if (writeBufferSize != -1) {
                if (writeBufferSize > socketWriteBufferSize) {
                    socket.setSendBufferSize(writeBufferSize);
                }
                super.setWriteBufferSize(writeBufferSize);
            } else {
                super.setWriteBufferSize(socketWriteBufferSize);
            }
        } catch (IOException e) {
            logger.log(Level.WARNING, "Error setting write buffer size", e);
        }
    }

    @Override
    public <M> Future<ReadResult<M, SocketAddress>> read(
            final M message,
            final CompletionHandler<ReadResult<M, SocketAddress>> completionHandler,
            final Transformer<Buffer, M> transformer,
            final Interceptor<ReadResult> interceptor) throws IOException {
        
        final UDPNIOTransport udpTransport = (UDPNIOTransport) transport;

        if (isBlocking) {
            return udpTransport.getTemporarySelectorIO().getReader().read(this,
                    message, completionHandler, transformer, interceptor);
        } else {
            return udpTransport.getAsyncQueueIO().getReader().read(this,
                    message, completionHandler, transformer, interceptor);
        }
    }

    @Override
    public <M> Future<WriteResult<M, SocketAddress>> write(
            final SocketAddress dstAddress,
            final M message,
            final CompletionHandler<WriteResult<M, SocketAddress>> completionHandler,
            final Transformer<M, Buffer> transformer) throws IOException {
        final UDPNIOTransport udpTransport = (UDPNIOTransport) transport;
        
        if (isBlocking) {
            return udpTransport.getTemporarySelectorIO().getWriter().write(this,
                    dstAddress, message, completionHandler, transformer);
        } else {
            return udpTransport.getAsyncQueueIO().getWriter().write(this,
                    dstAddress, message, completionHandler, transformer);
        }
    }
}
    
