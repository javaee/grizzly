/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.glassfish.grizzly.http.HttpRequestPacket;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@SuppressWarnings({"StringContatenationInLoop"})
@RunWith(Parameterized.class)
public class ServerSideBroadcastTest extends BaseWebSocketTestUtilities {
    public static final int ITERATIONS = 100;
    private final Version version;
    private final Broadcaster broadcaster;
    
    @Parameterized.Parameters
    public static List<Object[]> parameters() {
        final Broadcaster[] broadcasters = {new DummyBroadcaster(), new OptimizedBroadcaster()};
        
        final List<Object[]> versions = BaseWebSocketTestUtilities.parameters();
        final List<Object[]> resultList = new ArrayList<Object[]>();
        
        for (int i = 0; i < broadcasters.length; i++) {
            for (int j = 0; j < versions.size(); j++) {
                final Broadcaster broadcaster = broadcasters[i];
                final Version version = (Version) versions.get(j)[0];
                resultList.add(new Object[] {version, broadcaster});
            }
        }
        return resultList;
    }
    
    public ServerSideBroadcastTest(Version version, Broadcaster broadcaster) {
        this.version = version;
        this.broadcaster = broadcaster;
    }

    @Test
    public void broadcast()
        throws IOException, InstantiationException, ExecutionException, InterruptedException, URISyntaxException {
        final int websocketsCount = 5;
        
        WebSocketServer server = WebSocketServer.createServer(PORT);
        server.register("", "/broadcast", new BroadcastApplication(broadcaster));
        server.start();
        List<TrackingWebSocket> clients = new ArrayList<TrackingWebSocket>();
        try {
            String[] messages = {
                "test message",
                "let's try again",
                "3rd time's the charm!",
                "ok.  just one more",
                "now, we're done"
            };
            
            final String address = String.format("ws://localhost:%s/broadcast", PORT);
            for (int x = 0; x < websocketsCount; x++) {
                final TrackingWebSocket socket = new TrackingWebSocket(
                        address, x + "", version,
                        messages.length * websocketsCount * ITERATIONS);
                
                socket.connect();
                clients.add(socket);
            }
            
            for (int count = 0; count < ITERATIONS; count++) {
                for (String message : messages) {
                    for (TrackingWebSocket socket : clients) {

                        final String msgToSend =
                                String.format("%s: count %s: %s", socket.getName(), count, message);

                        for (TrackingWebSocket rcpts : clients) {
                            rcpts.sent.add(msgToSend);
                        }

                        socket.send(msgToSend);
                    }
                }
            }
            for (TrackingWebSocket socket : clients) {
                Assert.assertTrue("All messages should come back: " + socket.getReceived(), socket.waitOnMessages());
            }
        } finally {
            server.stop();
        }
    }

    public static class BroadcastApplication extends WebSocketApplication {
        private final Broadcaster broadcaster;

        public BroadcastApplication(Broadcaster broadcaster) {
            this.broadcaster = broadcaster;
        }

        @Override
        public boolean isApplicationRequest(HttpRequestPacket request) {
            return "/broadcast".equals(request.getRequestURI());
        }

        @Override
        public WebSocket createSocket(ProtocolHandler handler,
                HttpRequestPacket requestPacket, WebSocketListener... listeners) {
            final DefaultWebSocket ws =
                    (DefaultWebSocket) super.createSocket(handler,
                    requestPacket, listeners);
            
            ws.setBroadcaster(broadcaster);
            return ws;
        }
        
        @Override
        public void onMessage(WebSocket socket, String data) {
            socket.broadcast(getWebSockets(), data);
        }
    }
}
