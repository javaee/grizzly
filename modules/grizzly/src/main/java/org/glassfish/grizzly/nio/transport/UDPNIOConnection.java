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

package org.glassfish.grizzly.nio.transport;

import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.CompletionHandler;
import org.glassfish.grizzly.ReadResult;
import org.glassfish.grizzly.WriteResult;
import org.glassfish.grizzly.nio.AbstractNIOConnection;
import org.glassfish.grizzly.IOEvent;
import org.glassfish.grizzly.nio.SelectorRunner;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.SocketAddress;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.Interceptor;
import org.glassfish.grizzly.Reader;
import org.glassfish.grizzly.streams.StreamReader;
import org.glassfish.grizzly.streams.AbstractStreamReader;
import org.glassfish.grizzly.streams.StreamWriter;
import org.glassfish.grizzly.streams.AbstractStreamWriter;
import org.glassfish.grizzly.util.conditions.Condition;

/**
 * {@link org.glassfish.grizzly.Connection} implementation
 * for the {@link UDPNIOTransport}
 *
 * @author Alexey Stashok
 */
public class UDPNIOConnection extends AbstractNIOConnection {
    private Logger logger = Grizzly.logger;

    private final AbstractStreamReader streamReader;

    private final AbstractStreamWriter streamWriter;
    
    public UDPNIOConnection(UDPNIOTransport transport,
            DatagramChannel channel) {
        super(transport);
        
        this.channel = channel;

        setReadBufferSize(transport.getReadBufferSize());
        setWriteBufferSize(transport.getWriteBufferSize());
        
        streamReader = new UDPNIOStreamReader(this);
        streamWriter = new UDPNIOStreamWriter(this);
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

    public StreamReader getStreamReader() {
        return streamReader;
    }

    public StreamWriter getStreamWriter() {
        return streamWriter;
    }

    protected void preClose() {
        try {
            transport.fireIOEvent(IOEvent.CLOSED, this);
        } catch (IOException e) {
            Grizzly.logger.log(Level.FINE, "Unexpected IOExcption occurred, " +
                    "when firing CLOSE event");
        }
    }

    /**
     * Returns the address of the endpoint this <tt>Connection</tt> is
     * connected to, or <tt>null</tt> if it is unconnected.
     * @return the address of the endpoint this <tt>Connection</tt> is
     *         connected to, or <tt>null</tt> if it is unconnected.
     */
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
    public Future<ReadResult<Buffer, SocketAddress>> read(final Buffer buffer,
            final CompletionHandler<ReadResult<Buffer, SocketAddress>> completionHandler,
            final Condition<ReadResult<Buffer, SocketAddress>> condition)
            throws IOException {
        final UDPNIOTransport udpTransport = (UDPNIOTransport) transport;
        Interceptor<ReadResult<Buffer, SocketAddress>> interceptor = null;

        if (condition != null) {
            interceptor = new Interceptor<ReadResult<Buffer, SocketAddress>>() {

                @Override
                public int intercept(int event, Object context,
                        ReadResult<Buffer, SocketAddress> result) {
                    if (event == Reader.READ_EVENT) {
                        if (condition.check(result)) {
                            return Interceptor.COMPLETED;
                        }

                        return Interceptor.INCOMPLETED;
                    }

                    return Interceptor.DEFAULT;
                }
            };
        }

        if (isBlocking) {
            return udpTransport.getTemporarySelectorIO().getReader().read(this,
                    buffer, completionHandler, interceptor);
        } else {
            return udpTransport.getAsyncQueueIO().getReader().read(this,
                    buffer, completionHandler, interceptor);
        }
    }

    @Override
    public Future<WriteResult<Buffer, SocketAddress>> write(
            SocketAddress dstAddress, Buffer buffer,
            CompletionHandler<WriteResult<Buffer, SocketAddress>> completionHandler)
            throws IOException {
        final UDPNIOTransport udpTransport = (UDPNIOTransport) transport;
        
        if (isBlocking) {
            return udpTransport.getTemporarySelectorIO().getWriter().write(this,
                    dstAddress, buffer, completionHandler);
        } else {
            return udpTransport.getAsyncQueueIO().getWriter().write(this,
                    dstAddress, buffer, completionHandler);
        }
    }
}
    
