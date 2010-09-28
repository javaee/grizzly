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

package com.sun.grizzly.websockets;

import com.sun.grizzly.http.SelectorThread;
import com.sun.grizzly.tcp.StaticResourcesAdapter;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@Test
public class FloodTest {
    private static final String MESSAGE =
            "I'm a flooding message.  I'm being sent right on the heels of the handshake.  Do I get echoed back?";

    public void flood() throws IOException, InterruptedException, InstantiationException {
        SelectorThread thread = WebSocketsTest.createSelectorThread(WebSocketsTest.PORT, new StaticResourcesAdapter());
        final EchoWebSocketApplication app = new EchoWebSocketApplication();
        WebSocketEngine.getEngine().register(app);

        ClientWebSocket webSocket = null;
        try {
            final FloodListener listener = new FloodListener();
            webSocket = new FloodingWebSocket(listener);
            Assert.assertTrue(listener.waitOnMessage(), "Flood shouldn't affect message parsing");
            listener.reset();
            webSocket.send("just another dummy message");
            Assert.assertTrue(listener.waitOnMessage(), "Subsequent messages should come back, too.");
        } finally {
            if (webSocket != null) {
                webSocket.close();
            }
            thread.stopEndpoint();
            WebSocketEngine.getEngine().unregister(app);
        }
    }

    private class FloodingNetworkHandler extends ClientNetworkHandler {
        private final ClientWebSocket socket;

        public FloodingNetworkHandler(ClientWebSocket socket) {
            super(socket);
            this.socket = socket;
        }

        @Override
        protected void handshake() throws IOException {
            super.handshake();
            send(new DataFrame(MESSAGE));
        }
    }

    private static class FloodListener extends WebSocketAdapter {
        private CountDownLatch latch = new CountDownLatch(1);

        public void onMessage(WebSocket socket, DataFrame frame) throws IOException {
            latch.countDown();
        }

        public boolean waitOnMessage() throws InterruptedException {
            return latch.await(WebSocketEngine.DEFAULT_TIMEOUT, TimeUnit.SECONDS);
        }

        public void reset() {
            latch = new CountDownLatch(1);
        }
    }

    private class FloodingWebSocket extends ClientWebSocket {
        public FloodingWebSocket(FloodListener listener) throws IOException {
            super("ws://localhost:1725/echo", listener);
        }

        @Override
        public ClientNetworkHandler createNetworkHandler() {
            return new FloodingNetworkHandler(this);
        }
    }
}
