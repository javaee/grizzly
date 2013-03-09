/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2012 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.samples.websockets;

import java.awt.Frame;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.websockets.DataFrame;
import org.glassfish.grizzly.websockets.ProtocolHandler;
import org.glassfish.grizzly.websockets.WebSocket;
import org.glassfish.grizzly.websockets.WebSocketApplication;
import org.glassfish.grizzly.websockets.WebSocketListener;

/**
 * Chat websockets based application.
 * This {@link WebSocketApplication} customizes default {@link WebSocket}
 * with {@link ChatWebSocket}, which includes some chat specific properties and
 * logic.
 *
 * @author Alexey Stashok
 * @author Justin Lee
 */
public class ChatApplication extends WebSocketApplication {
    private static final Logger logger = Grizzly.logger(ChatApplication.class);

    /**
     * Creates a customized {@link WebSocket} implementation.
     * 
     * @return customized {@link WebSocket} implementation - {@link ChatWebSocket}
     */
    @Override
    public WebSocket createSocket(ProtocolHandler handler,
                                  HttpRequestPacket request,
                                  WebSocketListener... listeners) {
        return new ChatWebSocket(handler, request, listeners);
    }

    /**
     * Method is called, when {@link ChatWebSocket} receives a {@link Frame}.
     * @param websocket {@link ChatWebSocket}
     * @param data {@link Frame}
     *
     * @throws IOException
     */
    @Override
    public void onMessage(WebSocket websocket, String data) {
        // check if it's login notification
        if (data.startsWith("login:")) {
            // process login
            login((ChatWebSocket) websocket, data);
        } else {
            // broadcast the message
            broadcast(((ChatWebSocket)websocket).getUser(), data);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onClose(WebSocket websocket, DataFrame frame) {
        broadcast("system", ((ChatWebSocket)websocket).getUser() + " left the chat");
    }

    /**
     * Broadcasts the text message from the user.
     *
     * @param user the user name
     * @param text the text message
     */
    private void broadcast(String user, String text) {
        logger.log(Level.INFO, "Broadcasting: {0} from: {1}", new Object[]{text, user});
        for (WebSocket websocket : getWebSockets()) {
            if (!websocket.isConnected()) {
                continue;
            }
            final ChatWebSocket chat = (ChatWebSocket) websocket;
            if (chat.getUser() != null) {  // it may happen some websocket is on the list, but not logged in to the chat
                chat.sendJson(user, text);
            }
        }
    }

    /**
     * Process chat user log in.
     *
     * @param websocket {@link ChatWebSocket}
     * @param frame login {@link Frame}
     */
    private void login(ChatWebSocket websocket, String frame) {
        if (websocket.getUser() == null) { // check if it's not registered user
            logger.info("ChatApplication.login");
            // set the user name
            websocket.setUser(frame.split(":")[1].trim());
            // broadcast the login notification
            broadcast("system", websocket.getUser() + " has joined the chat.");
        }
    }
}
