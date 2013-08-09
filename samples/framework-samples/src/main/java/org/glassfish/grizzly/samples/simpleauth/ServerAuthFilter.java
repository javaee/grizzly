/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2013 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.samples.simpleauth;

import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;
import java.io.IOException;
import java.util.Map;
import java.util.Random;
import org.glassfish.grizzly.utils.DataStructures;

/**
 * Server authentication filter, which intercepts client<->server communication.
 * Filter checks, if coming message is authentication request, sent by client.
 * If yes - the filter generated client id and sends the authentication reponse
 * to a client. If incoming message is not authentication request - filter
 * checks whether client connection has been authenticated. If yes - filter
 * removes client authentication header ("auth-id: <connection-id>") from
 * a message and pass control to a next filter in a chain, otherwise -
 * throws an Exception.
 *
 * @author Alexey Stashok
 */
public class ServerAuthFilter extends BaseFilter {

    // Authenticated clients connection map
    private Map<Connection, String> authenticatedConnections =
            DataStructures.<Connection, String>getConcurrentMap();

    // Random, to generate client ids.
    private final Random random = new Random();

    /**
     * The method is called once we have received {@link MultiLinePacket} from
     * a client.
     * Filter check if incoming message is the client authentication request.
     * If yes - we generate new client id and send it back in the
     * authentication response. If the message is not authentication request -
     * we check message authentication header to correspond to a connection id
     * in the authenticated clients map. If it's ok - the filter removes
     * authentication header from the message and pass the message to a next
     * filter in a filter chain, otherwise, if authentication failed - the filter
     * throws an Exception
     * 
     * @param ctx Request processing context
     *
     * @return {@link NextAction}
     * @throws IOException
     */
    @Override
    public NextAction handleRead(FilterChainContext ctx) throws IOException {
        // Get the connection
        final Connection connection = ctx.getConnection();
        // Get the incoming packet
        final MultiLinePacket packet = (MultiLinePacket) ctx.getMessage();

        // get the command string
        final String command = packet.getLines().get(0);
        // check if it's authentication request from a client
        if (command.startsWith("authentication-request")) {
            // if yes - authenticate the client
            MultiLinePacket authResponse = authenticate(connection);
            // send authentication response back
            ctx.write(authResponse);

            // stop the packet processing
            return ctx.getStopAction();
        } else {
            // if it's some custom message
            // Get id line
            final String idLine = packet.getLines().get(1);

            // Check the client id
            if (checkAuth(connection, idLine)) {
                // if id corresponds to what server has -
                // Remove authentication header
                packet.getLines().remove(1);

                // Pass to a next filter
                return ctx.getInvokeAction();
            } else {
                // if authentication failed - throw an Exception.
                throw new IllegalStateException("Client is not authenticated!");
            }
        }
    }

    /**
     * The method is called each time, when server sends a message to a client.
     * First of all filter check if this packet is not authentication-response.
     * If yes - filter just passes control to a next filter in a chain, if not -
     * filter gets the client id from its local authenticated clients map and
     * adds "auth-id: <connection-id>" header to the outgoing message and
     * finally passes control to a next filter in a chain.
     *
     * @param ctx Response processing context
     *
     * @return {@link NextAction}
     * @throws IOException
     */
    @Override
    public NextAction handleWrite(FilterChainContext ctx) throws IOException {

        // Get the connection
        final Connection connection = ctx.getConnection();
        // Get the sending packet
        final MultiLinePacket packet = (MultiLinePacket) ctx.getMessage();

        // Get the message command
        final String command = packet.getLines().get(0);

        // if it's authentication-response
        if (command.equals("authentication-response")) {
            // just pass control to a next filter in a chain
            return ctx.getInvokeAction();
        } else {
            // if not - get connection id from authenticated connections map
            final String id = authenticatedConnections.get(connection);
            if (id != null) {
                // if id exists - add "auth-id" header to a packet
                packet.getLines().add(1, "auth-id: " + id);
                // pass control to a next filter in a chain
                return ctx.getInvokeAction();
            }

            // connection id wasn't found in a map of authenticated connections
            // throw an Exception
            throw new IllegalStateException("Client is not authenticated");
        }
    }



    /**
     * The method generates the key and builds the authentication response
     * packet.
     *
     * @param connection the {@link Connection}
     * @return authentication reponse packet
     */
    private MultiLinePacket authenticate(Connection connection) {
        // Generate the key
        String id = String.valueOf(System.currentTimeMillis() ^ random.nextLong());
        // put it to the authenticated connection map
        authenticatedConnections.put(connection, id);

        // Build authentication response packet
        final MultiLinePacket response = MultiLinePacket.create();
        response.getLines().add("authentication-response");
        response.getLines().add("auth-id: " + id);

        return response;
    }

    /**
     * Method checks, whether authentication header, sent in the message corresponds
     * to a value, stored in the server authentication map.
     * 
     * @param connection {@link Connection}
     * @param idLine authentication header string.
     * 
     * @return <tt>true</tt>, if authentication passed, or <tt>false</tt> otherwise.
     */
    private boolean checkAuth(Connection connection, String idLine) {
        // Get the connection id, from the server map
        final String registeredId = authenticatedConnections.get(connection);
        if (registeredId == null) return false;
        
        if (idLine.startsWith("auth-id:")) {
            // extract client id from the authentication header
            String id = getId(idLine);
            // check whether extracted id is equal to what server has in his map
            return registeredId.equals(id);
        } else {
            return false;
        }
    }

    /**
     * The method is called, when a connection gets closed.
     * We remove connection entry in authenticated connections map.
     *
     * @param ctx Request processing context
     *
     * @return {@link NextAction}
     * @throws IOException
     */
    @Override
    public NextAction handleClose(FilterChainContext ctx) throws IOException {
        authenticatedConnections.remove(ctx.getConnection());
        
        return ctx.getInvokeAction();
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
}
