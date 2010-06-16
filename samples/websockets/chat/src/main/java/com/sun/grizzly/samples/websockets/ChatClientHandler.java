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

package com.sun.grizzly.samples.websockets;

import com.sun.grizzly.Connection;
import com.sun.grizzly.websockets.ClientWebSocketMeta;
import com.sun.grizzly.websockets.WebSocketClientHandler;
import com.sun.grizzly.websockets.frame.Frame;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Chat web-socket client handler.
 * This {@link ChatClientHandler} customizes default {@link WebSocket}
 * with {@link ChatWebSocket}, which includes some chat specific properties and
 * logic.
 * 
 * @author Alexey Stashok
 */
public class ChatClientHandler extends WebSocketClientHandler<ChatWebSocket> {
    // regexp pattern to extract user name and message
    private static final Pattern PATTERN = Pattern.compile("window.parent.app.update\\(\\{ name: \"(.*)\", message: \"(.*)\" \\}\\);");
    
    // chat user name
    private final String login;
    
    /**
     * Construct a client {@link ChatClientHandler}
     * @param login user name
     */
    public ChatClientHandler(String login) {
        this.login = login;
    }

    /**
     * Creates a customized {@link WebSocket} implementation.
     *
     * @param connection underlying Grizzly {@link Connection}.
     * @param meta client-side {@link ClientWebSocketMeta}.
     * @return customized {@link WebSocket} implementation - {@link ChatWebSocket}
     */
    @Override
    protected ChatWebSocket createWebSocket(Connection connection,
            ClientWebSocketMeta meta) {
        return new ChatWebSocket(connection, meta, this);
    }

    /**
     * The method is called, when client-side {@link ChatWebSocket} gets connected.
     * At this stage we need to send login frame to the server.
     *
     * @param websocket connected {@link ChatWebSocket}.
     * @throws IOException
     */
    @Override
    public void onConnect(ChatWebSocket websocket) throws IOException {
        // set the user name
        websocket.setUser(login);
        // send login message to the server
        websocket.send(Frame.createTextFrame("login:" + login));
    }

    /**
     * Method is called, when {@link ChatWebSocket} receives a {@link Frame}.
     * @param websocket {@link ChatWebSocket}
     * @param frame {@link Frame}
     *
     * @throws IOException
     */
    @Override
    public void onMessage(ChatWebSocket websocket, Frame frame) throws IOException {
        // get the text message
        final String fullMessage = frame.getAsText();

        // try to extract user name and message text
        final Matcher matcher = PATTERN.matcher(fullMessage);
        if (matcher.find()) { // if extracted
            // print the message in the "user: message" format
            System.out.println(matcher.group(1) + ": " + matcher.group(2));
        } else { // if not
            // print the full message as it came
            System.out.println(fullMessage);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onClose(ChatWebSocket websocket) throws IOException {
        System.out.println("WebSocket is closed");
    }
}
