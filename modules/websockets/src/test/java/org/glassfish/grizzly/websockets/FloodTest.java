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

package org.glassfish.grizzly.websockets;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.Assert;
import org.junit.Test;

public class FloodTest {
    private static final String MESSAGE =
            "I'm a flooding message.  I'm being sent right on the heels of the handshake.  Do I get echoed back?";

    @Test
    public void flood() throws IOException, InterruptedException, InstantiationException, URISyntaxException {
        WebSocketServer server = WebSocketServer.createSimpleServer(WebSocketsTest.PORT);
        final EchoWebSocketApplication app = new EchoWebSocketApplication();
        server.register("/echo", app);
        server.start();

        WebSocketClient webSocket = null;
        try {
            final FloodListener listener = new FloodListener();
            webSocket = new FloodingWebSocket(listener);
            webSocket.connect();
            Assert.assertTrue("Flood shouldn't affect message parsing", listener.waitOnMessage());
            listener.reset();
            webSocket.send("just another dummy message");
            Assert.assertTrue("Subsequent messages should come back, too.", listener.waitOnMessage());
        } finally {
            if (webSocket != null) {
                webSocket.close();
            }
            server.stop();
            WebSocketEngine.getEngine().unregister(app);
        }
    }

    private static class FloodListener extends WebSocketAdapter {
        private CountDownLatch latch = new CountDownLatch(1);

        public void onMessage(WebSocket socket, String frame) {
            latch.countDown();
        }

        public boolean waitOnMessage() throws InterruptedException {
            return latch.await(WebSocketEngine.DEFAULT_TIMEOUT, TimeUnit.SECONDS);
        }

        public void reset() {
            latch = new CountDownLatch(1);
        }
    }

    private class FloodingWebSocket extends WebSocketClient {
        public FloodingWebSocket(FloodListener listener) throws URISyntaxException {
            super("ws://localhost:1725/echo", listener);
        }

        @Override
        public void onConnect() {
            super.onConnect();
            send(MESSAGE);
            send(MESSAGE);
            send(MESSAGE);
            send(MESSAGE);
            send(MESSAGE);
            send(MESSAGE);
            send(MESSAGE);
            send(MESSAGE);
            send(MESSAGE);
            send(MESSAGE);
            send(MESSAGE);
            send(MESSAGE);
            send(MESSAGE);
        }
    }
}
