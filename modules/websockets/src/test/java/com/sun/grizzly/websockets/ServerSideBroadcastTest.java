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

package com.sun.grizzly.websockets;

import com.sun.grizzly.http.SelectorThread;
import com.sun.grizzly.http.servlet.ServletAdapter;
import com.sun.grizzly.tcp.Request;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.logging.Logger;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@SuppressWarnings({"StringContatenationInLoop"})
@RunWith(Parameterized.class)
public class ServerSideBroadcastTest extends BaseWebSocketTestUtilities {

    public static final int ITERATIONS = 50;
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
    public void broadcast() throws IOException, InstantiationException, ExecutionException, InterruptedException {
        final int websocketsCount = 5;
        
        final SelectorThread thread = createSelectorThread(PORT, new ServletAdapter(new BroadcastServlet(broadcaster)));

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
                final TrackingWebSocket socket =
                        new TrackingWebSocket(version, address, x + "",
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
            thread.stopEndpoint();
        }
    }

    private void time(String method, Date start, Date end) {
        final int total = 5 * ITERATIONS;
        final double time = (end.getTime() - start.getTime()) / 1000.0;
        System.out.printf("%s: sent %s messages in %.3fs for %.3f msg/s\n", method, total, time, total / time);
    }

public static class BroadcastServlet extends HttpServlet {
    private static final Logger logger = Logger.getLogger(WebSocketEngine.WEBSOCKET);
    public static final String RESPONSE_TEXT = "Nothing to see";
    private WebSocketApplication app;

    public BroadcastServlet(final Broadcaster broadcaster) {
        app = new WebSocketApplication() {

            @Override
            public WebSocket createWebSocket(ProtocolHandler protocolHandler, WebSocketListener... listeners) {
                final DefaultWebSocket ws =
                        (DefaultWebSocket) super.createWebSocket(protocolHandler, listeners);
                ws.setBroadcaster(broadcaster);
                return ws;
            }
            
            @Override
            public boolean isApplicationRequest(Request request) {
                return request.requestURI().equals("/broadcast");
            }

            public void onMessage(WebSocket socket, String data) {
                socket.broadcast(getWebSockets(), data);
            }

            public void onClose(WebSocket socket, DataFrame frame) {
            }
        };
        WebSocketEngine.getEngine().register(app);
    }

    @Override
    public void destroy() {
        WebSocketEngine.getEngine().unregister(app);
        super.destroy();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("text/plain; charset=iso-8859-1");
        resp.getWriter().write(RESPONSE_TEXT);
        resp.getWriter().flush();
    }
}    
}
