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
package com.sun.grizzly.samples.simpleauth;

import com.sun.grizzly.Connection;
import com.sun.grizzly.filterchain.BaseFilter;
import com.sun.grizzly.filterchain.FilterChainContext;
import com.sun.grizzly.filterchain.NextAction;
import java.io.IOException;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client authentication filter, which intercepts client<->server communication,
 * and checks whether client connection has been authenticated. If not - filter
 * suspends current message write and initialize authentication. Once authentication
 * is done - filter resumes all the suspended writes. If connection is authenticated -
 * filter adds "auth-id: <connection-id>" header to the outgoing message.
 *
 * @author Alexey Stashok
 */
public class ClientAuthFilter extends BaseFilter {

    // Map of authenticated connections
    private ConcurrentHashMap<Connection, ConnectionAuthInfo> authenticatedConnections =
            new ConcurrentHashMap<Connection, ConnectionAuthInfo>();

    // Authentication packet (authentication request). The packet is the same for all connections.
    private static final MultiLinePacket authPacket;

    static {
        authPacket = MultiLinePacket.create();
        authPacket.getLines().add("authentication-request");
    }

    /**
     * The method is called once we have received {@link MultiLinePacket}.
     * Filter check if incoming message is the server authentication response.
     * If yes - we suppose client authentication is completed, store client id
     * (assigned by the server), and resume all the pending writes. If client
     * was authenticated before - we pass the response to a next filter in chain.
     *
     * @param ctx Request processing context
     * @param nextAction default {@link NextAction}.
     *
     * @return {@link NextAction}
     * @throws IOException
     */
    @Override
    public NextAction handleRead(FilterChainContext ctx, NextAction nextAction)
            throws IOException {
        // Get the connection
        final Connection connection = ctx.getConnection();
        // Get the processing packet
        final MultiLinePacket packet = (MultiLinePacket) ctx.getMessage();

        final String command = packet.getLines().get(0);

        // Check if the packet is authentication response
        if (command.startsWith("authentication-response")) {
            // if yes - retrieve the id, assigned by server
            final String id = getId(packet.getLines().get(1));

            synchronized(connection) {
                // store id in the map
                ConnectionAuthInfo info = authenticatedConnections.get(connection);
                info.id = id;

                // resume pending writes
                for (FilterChainContext pendedContext : info.pendingMessages) {
                    pendedContext.resume();
                }

                info.pendingMessages = null;
            }

            // if it's authentication response - we don't pass processing to a next filter in a chain.
            return ctx.getStopAction();
        } else {
            // otherwise pass control to a next filter in a chain
            return nextAction;
        }
    }

    /**
     * The method is called each time, when client sends a message to a server.
     * First of all filter check if this connection has been authenticated.
     * If yes - add "auth-id: <connection-id>" header to a message and pass it
     * to a next filter in a chain. If appears, that client wasn't authenticated yet -
     * filter initialize authentication (only once for the very first message),
     * suspends current write and adds suspended context to a queue to resume it,
     * once authentication will be completed.
     * 
     * @param ctx Request processing context
     * @param nextAction default {@link NextAction}.
     *
     * @return {@link NextAction}
     * @throws IOException
     */
    @Override
    public NextAction handleWrite(final FilterChainContext ctx,
            final NextAction nextAction) throws IOException {

        // Get the connection
        final Connection connection = ctx.getConnection();
        // Get the sending packet
        final MultiLinePacket packet = (MultiLinePacket) ctx.getMessage();

        // Get the connection authentication information
        ConnectionAuthInfo authInfo =
                authenticatedConnections.get(connection);
        
        if (authInfo == null) {
            // connection is not authenticated
            authInfo = new ConnectionAuthInfo();
            final ConnectionAuthInfo existingInfo =
                    authenticatedConnections.putIfAbsent(connection, authInfo);
            if (existingInfo == null) {
                // it's the first message for this client - we need to start authentication process
                // sending authentication packet
                ctx.write(authPacket);
            } else {
                // authentication has been already started.
                authInfo = existingInfo;
            }
        }

        if (authInfo.pendingMessages != null) {
            // it might be a sign, that authentication has been completed on another thread
            // synchronize and check one more time
            synchronized (connection) {
                if (authInfo.pendingMessages != null) {
                    if (authInfo.id == null) {
                        // Authentication hs been started by another thread, but it is still in progress
                        // add suspended write context to a queue
                        ctx.suspend();
                        authInfo.pendingMessages.add(ctx);
                        return ctx.getSuspendAction();
                    }
                }
            }

        }

        // Authentication has been completed - add "auth-id" header and pass the message to a next filter in chain.
        packet.getLines().add(1, "auth-id: " + authInfo.id);
        return nextAction;
    }

    /**
     * The method is called, when a connection gets closed.
     * We remove connection entry in authenticated connections map.
     * 
     * @param ctx Request processing context
     * @param nextAction default {@link NextAction}.
     *
     * @return {@link NextAction}
     * @throws IOException
     */
    @Override
    public NextAction handleClose(FilterChainContext ctx,
            NextAction nextAction) throws IOException {
        authenticatedConnections.remove(ctx.getConnection());
        
        return nextAction;
    }

    /**
     * Retrieve connection id from a packet header
     *
     * @param idLine header, which looks like "auth-id: <connection-id>".
     * @return connection id
     */
    private String getId(String idLine) {
        return idLine.split(":")[1].trim();
    }

    /**
     * Single connection authentication info.
     */
    public static class ConnectionAuthInfo {
        // Connection id
        public volatile String id;

        // Queue of the pending writes
        public volatile Queue<FilterChainContext> pendingMessages;

        public ConnectionAuthInfo() {
            pendingMessages = new LinkedList<FilterChainContext>();
        }
    }
}
