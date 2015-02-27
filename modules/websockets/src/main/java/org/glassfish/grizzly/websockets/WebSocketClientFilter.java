/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2013-2015 Oracle and/or its affiliates. All rights reserved.
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
package org.glassfish.grizzly.websockets;

import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.http.HttpContent;
import org.glassfish.grizzly.http.HttpResponsePacket;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Grizzly;

public class WebSocketClientFilter extends BaseWebSocketFilter {
    private static final Logger LOGGER = Grizzly.logger(WebSocketClientFilter.class);

    // ----------------------------------------------------- Methods from Filter
    
    /**
     * Method handles Grizzly {@link Connection} connect phase. Check if the {@link Connection} is a client-side {@link
     * WebSocket}, if yes - creates websocket handshake packet and send it to a server. Otherwise, if it's not websocket
     * connection - pass processing to the next {@link Filter} in a chain.
     *
     * @param ctx {@link FilterChainContext}
     *
     * @return {@link NextAction} instruction for {@link FilterChain}, how it should continue the execution
     * 
     * @throws java.io.IOException
     */
    @Override
    public NextAction handleConnect(FilterChainContext ctx) throws IOException {
        LOGGER.log(Level.FINEST, "handleConnect");
        // Get connection
        final Connection connection = ctx.getConnection();
        // check if it's websocket connection
        if (!webSocketInProgress(connection)) {
            // if not - pass processing to a next filter
            return ctx.getInvokeAction();
        }

        WebSocketHolder.get(connection).handshake.initiate(ctx);
        // call the next filter in the chain
        return ctx.getInvokeAction();
    }
    
    // ---------------------------------------- Methods from BaseWebSocketFilter


    @Override
    protected NextAction handleHandshake(FilterChainContext ctx, HttpContent content) throws IOException {
        return handleClientHandShake(ctx, content);
    }


    // --------------------------------------------------------- Private Methods


    private static NextAction handleClientHandShake(FilterChainContext ctx, HttpContent content) {
        final WebSocketHolder holder = WebSocketHolder.get(ctx.getConnection());
        holder.handshake.validateServerResponse((HttpResponsePacket) content.getHttpHeader());
        holder.webSocket.onConnect();
        
        if (content.getContent().hasRemaining()) {
            return ctx.getRerunFilterAction();
        } else {
            content.recycle();
            return ctx.getStopAction();
        }
    }
}
