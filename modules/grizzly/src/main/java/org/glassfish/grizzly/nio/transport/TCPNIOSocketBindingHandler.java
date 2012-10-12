/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012 Oracle and/or its affiliates. All rights reserved.
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

import org.glassfish.grizzly.AbstractSocketBindingHandler;
import org.glassfish.grizzly.utils.Exceptions;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.SocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.util.concurrent.locks.Lock;

/**
 * This class may be used to apply a custom {@link org.glassfish.grizzly.Processor} and/or {@link org.glassfish.grizzly.ProcessorSelector}
 * atomically within a bind operation - not something that can normally be done using the {@link TCPNIOTransport} alone.
 *
 * Example usage:
 * <pre>
 *     TCPNIOSocketBindingHandler handler = TCPNIOSocketBindingHandler.builder(transport).setProcessor(custom).build();
 *     handler.bind(socketAddress);
 * </pre>
 *
 * @since 2.2.19
 */
public class TCPNIOSocketBindingHandler extends AbstractSocketBindingHandler<TCPNIOServerConnection> {

    private final TCPNIOTransport tcpTransport;

    // ------------------------------------------------------------ Constructors


    TCPNIOSocketBindingHandler(final TCPNIOTransport tcpTransport) {
        super(tcpTransport);
        this.tcpTransport = tcpTransport;
    }


    // ------------------------------- Methods from AbstractSocketBindingHandler


    @Override
    public TCPNIOServerConnection bind(SocketAddress socketAddress) throws IOException {
        return bind(socketAddress, tcpTransport.getServerConnectionBackLog());
    }

    @Override
    public TCPNIOServerConnection bind(SocketAddress socketAddress, int backlog) throws IOException {
        return bindToChannelAndAddress(
                tcpTransport.getSelectorProvider().openServerSocketChannel(),
                socketAddress,
                backlog);
    }

    @Override
    public TCPNIOServerConnection bindToInherited() throws IOException {
        return bindToChannelAndAddress(
                this.<ServerSocketChannel>getSystemInheritedChannel(ServerSocketChannel.class),
                null,
                -1);
    }

    @Override
    public void unbind(TCPNIOServerConnection connection) throws IOException {
        tcpTransport.unbind(connection);
    }

    public static Builder builder(final TCPNIOTransport transport) {
       return new TCPNIOSocketBindingHandler.Builder(transport);
    }


    // --------------------------------------------------------- Private Methods


    private TCPNIOServerConnection bindToChannelAndAddress(final ServerSocketChannel serverSocketChannel,
                                                           final SocketAddress socketAddress,
                                                           final int backlog)
    throws IOException {
        TCPNIOServerConnection serverConnection = null;

        final Lock lock = tcpTransport.getState().getStateLocker().writeLock();
        lock.lock();
        try {

            final ServerSocket serverSocket = serverSocketChannel.socket();

            serverSocket.setReuseAddress(tcpTransport.reuseAddress);

            serverSocket.setSoTimeout(tcpTransport.serverSocketSoTimeout);

            if (socketAddress != null) {
                serverSocket.bind(socketAddress, backlog);
            }

            serverSocketChannel.configureBlocking(false);

            serverConnection = tcpTransport.obtainServerNIOConnection(serverSocketChannel);
            tcpTransport.serverConnections.add(serverConnection);
            serverConnection.resetProperties();

            if (!tcpTransport.isStopped()) {
                tcpTransport.listenServerConnection(serverConnection);
            }

            return serverConnection;
        } catch (Exception e) {
            if (serverConnection != null) {
                tcpTransport.serverConnections.remove(serverConnection);

                serverConnection.closeSilently();
            } else {
                try {
                    serverSocketChannel.close();
                } catch (IOException ignored) {
                }
            }

            throw Exceptions.makeIOException(e);
        } finally {
            lock.unlock();
        }
    }


    // ----------------------------------------------------------- Inner Classes


    public static class Builder extends AbstractSocketBindingHandler.Builder<Builder> {


        // -------------------------------------------------------- Constructors


        public Builder(TCPNIOTransport transport) {
            super(new TCPNIOSocketBindingHandler(transport));
        }

        public TCPNIOSocketBindingHandler build() {
            return (TCPNIOSocketBindingHandler) bindingHandler;
        }

    } // END Builder


}
