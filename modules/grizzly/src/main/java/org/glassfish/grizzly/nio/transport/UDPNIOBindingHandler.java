/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012-2015 Oracle and/or its affiliates. All rights reserved.
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

import org.glassfish.grizzly.AbstractBindingHandler;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.utils.Exceptions;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.SocketAddress;
import java.nio.channels.DatagramChannel;
import java.util.concurrent.locks.Lock;

/**
 * This class may be used to apply a custom {@link org.glassfish.grizzly.Processor} and/or {@link org.glassfish.grizzly.ProcessorSelector}
 * atomically within a bind operation - not something that can normally be done using the {@link UDPNIOTransport} alone.
 *
 * Example usage:
 * <pre>
 *     UDPNIOBindingHandler handler = UDPNIOBindingHandler.builder(transport).setProcessor(custom).build();
 *     handler.bind(socketAddress);
 * </pre>
 *
 * @since 2.2.19
 */
public class UDPNIOBindingHandler extends AbstractBindingHandler {

    private final UDPNIOTransport udpTransport;

    // ------------------------------------------------------------ Constructors


    public UDPNIOBindingHandler(UDPNIOTransport udpTransport) {
        super(udpTransport);
        this.udpTransport = udpTransport;
    }


    // ------------------------------- Methods from AbstractBindingHandler


    @Override
    public UDPNIOServerConnection bind(SocketAddress socketAddress) throws IOException {
        return bind(socketAddress, -1);
    }

    @Override
    public UDPNIOServerConnection bind(SocketAddress socketAddress, int backlog) throws IOException {
        return bindToChannel(
                udpTransport.getSelectorProvider().openDatagramChannel(),
                socketAddress);
    }

    @Override
    public UDPNIOServerConnection bindToInherited() throws IOException {
        return bindToChannel(
                this.<DatagramChannel>getSystemInheritedChannel(DatagramChannel.class),
                null);
    }

    @Override
    public void unbind(Connection connection) {
        udpTransport.unbind(connection);
    }

    public static Builder builder(final UDPNIOTransport transport) {
        return new UDPNIOBindingHandler.Builder().transport(transport);
    }


    // --------------------------------------------------------- Private Methods


    private UDPNIOServerConnection bindToChannel(final DatagramChannel serverDatagramChannel,
                                                 final SocketAddress socketAddress)
    throws IOException {
        UDPNIOServerConnection serverConnection = null;

        final Lock lock = udpTransport.getState().getStateLocker().writeLock();
        lock.lock();
        try {
            udpTransport.getChannelConfigurator().preConfigure(transport,
                    serverDatagramChannel);

            if (socketAddress != null) {
                final DatagramSocket socket = serverDatagramChannel.socket();
                socket.bind(socketAddress);
            }

            udpTransport.getChannelConfigurator().postConfigure(transport,
                    serverDatagramChannel);

            serverConnection = udpTransport.obtainServerNIOConnection(serverDatagramChannel);
            serverConnection.setProcessor(getProcessor());
            serverConnection.setProcessorSelector(getProcessorSelector());
            udpTransport.serverConnections.add(serverConnection);

            if (!udpTransport.isStopped()) {
                serverConnection.register();
            }

            return serverConnection;
        } catch (Exception e) {
            if (serverConnection != null) {
                udpTransport.serverConnections.remove(serverConnection);

                serverConnection.closeSilently();
            } else {
                try {
                    serverDatagramChannel.close();
                } catch (IOException ignored) {
                }
            }

            throw Exceptions.makeIOException(e);
        } finally {
            lock.unlock();
        }
    }


    // ----------------------------------------------------------- Inner Classes


    public static class Builder extends AbstractBindingHandler.Builder<Builder> {

        private UDPNIOTransport transport;


        public UDPNIOBindingHandler build() {
            return (UDPNIOBindingHandler) super.build();
        }

        public Builder transport(UDPNIOTransport transport) {
            this.transport = transport;
            return this;
        }

        @Override
        protected AbstractBindingHandler create() {
            if (transport == null) {
                throw new IllegalStateException(
                        "Unable to create TCPNIOBindingHandler - transport is null");
            }
            return new UDPNIOBindingHandler(transport);
        }

    } // END UDPNIOSocketBindingHandlerBuilder
}
