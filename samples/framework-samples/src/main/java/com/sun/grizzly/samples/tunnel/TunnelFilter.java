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

package com.sun.grizzly.samples.tunnel;

import com.sun.grizzly.CompletionHandler;
import com.sun.grizzly.Connection;
import com.sun.grizzly.Grizzly;
import com.sun.grizzly.SocketConnectorHandler;
import com.sun.grizzly.attributes.Attribute;
import com.sun.grizzly.filterchain.FilterAdapter;
import com.sun.grizzly.filterchain.FilterChainContext;
import com.sun.grizzly.filterchain.NextAction;
import com.sun.grizzly.streams.StreamReader;
import com.sun.grizzly.streams.StreamWriter;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

/**
 * Simple tunneling filter, which maps input of one connection to the output of
 * another and vise versa.
 *
 * @author Alexey Stashok
 */
public class TunnelFilter extends FilterAdapter {
    private Attribute<Connection> peerConnectionAttribute =
            Grizzly.DEFAULT_ATTRIBUTE_BUILDER.createAttribute("TunnelFilter.peerConnection");

    // Transport, which will be used to create peer connection
    private final SocketConnectorHandler transport;

    // Destination address for peer connections
    private final SocketAddress redirectAddress;

    public TunnelFilter(SocketConnectorHandler transport, String host, int port) {
        this(transport, new InetSocketAddress(host, port));
    }

    public TunnelFilter(SocketConnectorHandler transport, SocketAddress redirectAddress) {
        this.transport = transport;
        this.redirectAddress = redirectAddress;
    }
    
    /**
     * This method will be called, once {@link Connection} has some available data
     */
    @Override
    public NextAction handleRead(final FilterChainContext ctx,
            final NextAction nextAction) throws IOException {
        final Connection connection = ctx.getConnection();
        final Connection peerConnection = peerConnectionAttribute.get(connection);

        // if connection is closed - stop the execution
        if (!connection.isOpen()) {
            return ctx.getStopAction();
        }

        // if peerConnection wasn't created - create it (usually happens on first connection request)
        if (peerConnection == null) {
            // "Peer connect" phase could take some time - so execute it in non-blocking mode

            // Suspend current task execution to resume it, once peer connection will be connected
            ctx.suspend();

            // Connect peer connection and register completion handler
            transport.connect(redirectAddress, new ConnectCompletionHandler(ctx));

            // return suspend status
            return ctx.getSuspendAction();
        }

        // if peer connection is already created - just forward data to peer
        redirectToPeer(connection, peerConnection);

        return nextAction;
    }

    /**
     * This method will be called, to notify about {@link Connection} closing.
     */
    @Override
    public NextAction handleClose(FilterChainContext ctx, NextAction nextAction)
            throws IOException {
        final Connection connection = ctx.getConnection();
        final Connection peerConnection = peerConnectionAttribute.get(connection);

        // Close peer connection as well, if it wasn't closed before
        if (peerConnection != null && peerConnection.isOpen()) {
            peerConnection.close();
        }

        return nextAction;
    }

    /**
     * Redirect data from {@link Connection} to its peer.
     *
     * @param connection source {@link Connection}
     * @param peerConnection peer {@link Connection}
     * @throws IOException
     */
    private static void redirectToPeer(final Connection connection,
            final Connection peerConnection) throws IOException {
        final StreamReader reader = connection.getStreamReader();
        final StreamWriter peerWriter = peerConnection.getStreamWriter();

        peerWriter.writeStream(reader);
        peerWriter.flush();        
    }
    
    /**
     * Peer connect {@link CompletionHandler}
     */
    private class ConnectCompletionHandler implements CompletionHandler {
        private final FilterChainContext context;
        
        private ConnectCompletionHandler(FilterChainContext context) {
            this.context = context;
        }

        @Override
        public void cancelled(Connection connection) {
            close(context.getConnection());
            resumeContext();
        }

        @Override
        public void failed(Connection connection, Throwable throwable) {
            close(context.getConnection());
            resumeContext();
        }

        /**
         * If peer was successfully connected - map both connections to each other.
         */
        @Override
        public void completed(Connection connection, Object result) {
            final Connection peerConnection = context.getConnection();

            // Map connections
            peerConnectionAttribute.set(connection, peerConnection);
            peerConnectionAttribute.set(peerConnection, connection);

            // Resume filter chain execution
            resumeContext();
        }

        @Override
        public void updated(Connection connection, Object result) {
        }

        /**
         * Resume {@link FilterChain} execution on stage, where it was
         * earlier suspended.
         */
        private void resumeContext() {
            context.getProcessorRunnable().run();
        }

        /**
         * Close the {@link Connection}
         * @param connection {@link Connection}
         */
        private void close(Connection connection) {
            try {
                connection.close();
            } catch (IOException e) {
            }
        }
    }
}
