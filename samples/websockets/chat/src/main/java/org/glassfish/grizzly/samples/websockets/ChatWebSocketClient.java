/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.grizzly.samples.websockets;

import com.sun.grizzly.TransportFactory;
import com.sun.grizzly.filterchain.FilterChainBuilder;
import com.sun.grizzly.filterchain.TransportFilter;
import com.sun.grizzly.http.HttpClientFilter;
import com.sun.grizzly.nio.transport.TCPNIOTransport;
import com.sun.grizzly.websockets.WebSocket;
import com.sun.grizzly.websockets.WebSocketConnectorHandler;
import com.sun.grizzly.websockets.WebSocketFilter;
import com.sun.grizzly.websockets.frame.Frame;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Standalone Java web-socket chat client implementation.
 * 
 * @author Alexey Stashok
 */
public class ChatWebSocketClient {
    public static void main(String[] args) throws Exception {
        // Initiate the client filterchain to work with websockets
        final FilterChainBuilder serverFilterChainBuilder = FilterChainBuilder.stateless();
        // Transport filter
        serverFilterChainBuilder.add(new TransportFilter());
        // HTTP client filter
        serverFilterChainBuilder.add(new HttpClientFilter());
        // WebSocket filter
        serverFilterChainBuilder.add(new WebSocketFilter());

        // initialize transport
        final TCPNIOTransport transport = TransportFactory.getInstance().createTCPTransport();
        transport.setProcessor(serverFilterChainBuilder.build());

        ChatWebSocket websocket = null;
        
        try {
            // start transport
            transport.start();
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
            } while(login.isEmpty());

            // Initialize websocket connect and login to chat

            // 1. Create WebSocketConnectorHandler to execute connect on
            WebSocketConnectorHandler connectorHandler =
                    new WebSocketConnectorHandler(transport);

            // 2. Create WebSocketClientHandler, which will handle websocket lifecycle
            final ChatClientHandler clientHandler = new ChatClientHandler(login);

            // 3. Connect websocket
            Future<WebSocket> connectFuture = connectorHandler.connect(
                    new URI("ws://localhost:" +
                    ChatWebSocketServer.PORT +
                    "/grizzly-websockets-chat/chat"),
                    clientHandler);

            websocket = (ChatWebSocket) connectFuture.get(10, TimeUnit.SECONDS);

            // websocket client working cycle... Type the message and send it to the server
            String message = null;
            do {
//                System.out.print("Message ('q' to quit) >");
                message = reader.readLine();
                if (!websocket.isConnected() ||
                        message == null || "q".equals(message)) {
                    return;
                }

                message = message.trim();

                // if message is not empty - send it to the server
                if (message.length() > 0) {
                    websocket.send(Frame.createTextFrame(message));
                }
            } while (true);

        } finally {
            // close the websocket
            if (websocket != null) {
                websocket.close();
            }

            // stop the transport
            transport.stop();
            TransportFactory.getInstance().close();
        }
    }
}
