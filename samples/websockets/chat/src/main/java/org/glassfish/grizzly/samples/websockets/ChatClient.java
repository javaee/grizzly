/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2011 Oracle and/or its affiliates. All rights reserved.
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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.glassfish.grizzly.websockets.WebSocketClient;
import org.glassfish.grizzly.websockets.DataFrame;
import org.glassfish.grizzly.websockets.WebSocket;

/**
 * Chat websocket client handler. This {@link ChatClient} customizes default {@link WebSocket} with {@link
 * ChatWebSocket}, which includes some chat specific properties and logic.
 *
 * @author Alexey Stashok
 */
public class ChatClient extends WebSocketClient {
    // regexp pattern to extract user name and message
    private static final Pattern PATTERN = Pattern.compile(
        "window.parent.app.update\\(\\{ name: \"(.*)\", message: \"(.*)\" \\}\\);");
    // chat user name
    private final String login;

    /**
     * Construct a client {@link ChatClient}
     *
     * @param login user name
     */
    public ChatClient(URI uri, String login) throws IOException {
        super(uri);
        this.login = login;
    }

    /**
     * The method is called, when client-side {@link ChatWebSocket} gets connected. At this stage we need to send login
     * frame to the server.
     */
    @Override
    public void onConnect() {
        super.onConnect();
        send("login:" + login);
    }

    /**
     * Method is called, when {@link ChatWebSocket} receives a message.
     */
    @Override
    public void onMessage(String fullMessage) {
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

    @Override
    public void onClose(DataFrame frame) {
        System.out.println("WebSocket is closed");
    }

    public static void main(String[] args) throws Exception {
        // initialize transport
        ChatClient websocket = null;
        try {
            // initialize console reader
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
            System.out.println("Type 'q' and <enter> any time to exit");
            String login = null;
            // Ask for the user name
            do {
                System.out.print("Login: ");
                login = reader.readLine();
                if (login == null || "q".equals(login)) {
                    return;
                }
                login = login.trim();
            } while (login.isEmpty());
            // Initialize websocket connect and login to chat
            final URI uri = new URI("ws://localhost:" + ChatWebSocketServer.PORT + "/grizzly-websockets-chat/chat");
            final ChatClient client = new ChatClient(uri, login);
            websocket = (ChatClient) client.connect();
            // websocket client working cycle... Type the message and send it to the server
            String message = null;
            do {
                System.out.print("Message ('q' to quit) >");
                message = reader.readLine();
                if (!websocket.isConnected() ||
                    message == null || "q".equals(message)) {
                    return;
                }
                message = message.trim();
                // if message is not empty - send it to the server
                if (message.length() > 0) {
                    websocket.send(message);
                }
            } while (true);

        } finally {
            // close the websocket
            if (websocket != null) {
                websocket.close();
            }
        }
    }
}
