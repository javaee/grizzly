/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2009-2012 Oracle and/or its affiliates. All rights reserved.
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
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.ConnectionProbe;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.asyncqueue.AsyncQueueWriter;
import org.glassfish.grizzly.localization.LogMessages;
import org.glassfish.grizzly.nio.NIOConnection;
import org.glassfish.grizzly.nio.SelectorRunner;
import org.glassfish.grizzly.utils.Holder;
import org.glassfish.grizzly.utils.NullaryFunction;

/**
 * {@link org.glassfish.grizzly.Connection} implementation
 * for the {@link UDPNIOTransport}
 *
 * @author Alexey Stashok
 */
public class UDPNIOConnection extends NIOConnection {

    private static final Logger LOGGER = Grizzly.logger(UDPNIOConnection.class);
    
    Holder<SocketAddress> localSocketAddressHolder;
    Holder<SocketAddress> peerSocketAddressHolder;

    private SettableIntHolder readBufferSizeHolder;
    private SettableIntHolder writeBufferSizeHolder;

    public UDPNIOConnection(UDPNIOTransport transport,
            DatagramChannel channel) {
        super(transport);

        this.channel = channel;

        resetProperties();
    }

    public boolean isConnected() {
        return channel != null && ((DatagramChannel) channel).isConnected();
    }

    @Override
    protected void setSelectionKey(SelectionKey selectionKey) {
        super.setSelectionKey(selectionKey);
    }

    @Override
    protected void setSelectorRunner(SelectorRunner selectorRunner) {
        super.setSelectorRunner(selectorRunner);
    }

    protected boolean notifyReady() {
        return connectCloseSemaphor.compareAndSet(null, NOTIFICATION_INITIALIZED);
    }

    /**
     * Returns the address of the endpoint this <tt>Connection</tt> is
     * connected to, or <tt>null</tt> if it is unconnected.
     * @return the address of the endpoint this <tt>Connection</tt> is
     *         connected to, or <tt>null</tt> if it is unconnected.
     */
    @Override
    public SocketAddress getPeerAddress() {
        return peerSocketAddressHolder.get();
    }

    /**
     * Returns the local address of this <tt>Connection</tt>,
     * or <tt>null</tt> if it is unconnected.
     * @return the local address of this <tt>Connection</tt>,
     *      or <tt>null</tt> if it is unconnected.
     */
    @Override
    public SocketAddress getLocalAddress() {
        return localSocketAddressHolder.get();
    }

    protected final void resetProperties() {
        if (channel != null) {
            readBufferSizeHolder = new SettableIntHolder() {
                @Override
                protected int evaluate() {
                    try {
                        return ((DatagramChannel) channel).socket().getReceiveBufferSize();
                    } catch (IOException e) {
                        throw new IllegalStateException("Can not evaluate receive buffer size", e);
                    }
                }
            };
            
            writeBufferSizeHolder = new SettableIntHolder() {
                @Override
                protected int evaluate() {
                    try {
                        return ((DatagramChannel) channel).socket().getSendBufferSize();
                    } catch (IOException e) {
                        throw new IllegalStateException("Can not evaluate send buffer size", e);
                    }
                }
            };
            
            setReadBufferSize(transport.getReadBufferSize());
            setWriteBufferSize(transport.getWriteBufferSize());

            final int transportMaxAsyncWriteQueueSize =
                    ((UDPNIOTransport) transport).getAsyncQueueIO()
                    .getWriter().getMaxPendingBytesPerConnection();
            
            setMaxAsyncWriteQueueSize(
                    transportMaxAsyncWriteQueueSize == AsyncQueueWriter.AUTO_SIZE ?
                    getWriteBufferSize() * 4 :
                    transportMaxAsyncWriteQueueSize);
            
            localSocketAddressHolder = Holder.<SocketAddress>lazyHolder(
                    new NullaryFunction<SocketAddress>() {
                        @Override
                        public SocketAddress evaluate() {
                            return ((DatagramChannel) channel).socket().getLocalSocketAddress();
                        }
                    });

            peerSocketAddressHolder = Holder.<SocketAddress>lazyHolder(
                    new NullaryFunction<SocketAddress>() {
                        @Override
                        public SocketAddress evaluate() {
                            return ((DatagramChannel) channel).socket().getRemoteSocketAddress();
                        }
                    });
        }
    }

    @Override
    public int getReadBufferSize() {
        if (readBufferSizeHolder == null) {
            throw new IllegalStateException("TCPNIOConnection is not initialized");
        }
        
        return readBufferSizeHolder.getInt();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setReadBufferSize(final int readBufferSize) {
        if (writeBufferSizeHolder == null) {
            throw new IllegalStateException("TCPNIOConnection is not initialized");
        }
        
        if (readBufferSize > 0) {
            try {
                ((DatagramChannel) channel).socket().setReceiveBufferSize(readBufferSize);
                readBufferSizeHolder.setInt(readBufferSize);
            } catch (IOException e) {
                LOGGER.log(Level.WARNING,
                        LogMessages.WARNING_GRIZZLY_CONNECTION_SET_READBUFFER_SIZE_EXCEPTION(),
                        e);
            }
        }
    }

    @Override
    public int getWriteBufferSize() {
        if (writeBufferSizeHolder == null) {
            throw new IllegalStateException("TCPNIOConnection is not initialized");
        }

        return writeBufferSizeHolder.getInt();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setWriteBufferSize(int writeBufferSize) {
        if (writeBufferSizeHolder == null) {
            throw new IllegalStateException("TCPNIOConnection is not initialized");
        }
        
        if (writeBufferSize > 0) {
            try {
                ((DatagramChannel) channel).socket().setSendBufferSize(writeBufferSize);
                writeBufferSizeHolder.setInt(writeBufferSize);
            } catch (IOException e) {
                LOGGER.log(Level.WARNING,
                        LogMessages.WARNING_GRIZZLY_CONNECTION_SET_WRITEBUFFER_SIZE_EXCEPTION(),
                        e);
            }
        }
    }

    /**
     * Method will be called, when the connection gets connected.
     * @throws IOException
     */
    protected final void onConnect() throws IOException {
        notifyProbesConnect(this);
    }

    /**
     * Method will be called, when some data was read on the connection
     */
    protected final void onRead(Buffer data, int size) {
        if (size > 0) {
            notifyProbesRead(this, data, size);
        }
        checkEmptyRead(size);
    }

    /**
     * Method will be called, when some data was written on the connection
     */
    protected final void onWrite(Buffer data, int size) {
        notifyProbesWrite(this, data, size);
    }

    /**
     * Set the monitoringProbes array directly.
     * @param monitoringProbes
     */
    void setMonitoringProbes(final ConnectionProbe[] monitoringProbes) {
        this.monitoringConfig.addProbes(monitoringProbes);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("UDPNIOConnection");
        sb.append("{localSocketAddress=").append(localSocketAddressHolder);
        sb.append(", peerSocketAddress=").append(peerSocketAddressHolder);
        sb.append('}');
        return sb.toString();
    }
}
