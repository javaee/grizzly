/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2010 Sun Microsystems, Inc. All rights reserved.
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
 */

package com.sun.grizzly.websockets;

import com.sun.grizzly.http.SelectorThread;
import com.sun.grizzly.tcp.StaticResourcesAdapter;
import com.sun.grizzly.util.net.URL;
import org.testng.Assert;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
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
        WebSocketEngine.getEngine().register("/echo", new SimpleWebSocketApplication());
        ClientWebSocketApplication app = new ClientWebSocketApplication("http://localhost:1725/echo") {
            @Override
            public WebSocket createSocket(NetworkHandler handler, WebSocketListener... listeners) throws IOException {
                return super.createSocket(new FloodingNetworkHandler(this), listeners);
            }
        };

        final FloodListener listener = new FloodListener();
        final WebSocket webSocket = app.connect(listener);
        try {
            Assert.assertTrue(listener.waitOnMessage(), "Flood shouldn't affect message parsing");
            listener.reset();
            webSocket.send("just another dummy message");
            Assert.assertTrue(listener.waitOnMessage(), "Subsequent messages should come back, too.");
        } finally {
            webSocket.close();
            app.stop();
            thread.stopEndpoint();
        }
    }

    private class FloodingNetworkHandler extends ClientNetworkHandler {
        public FloodingNetworkHandler(ClientWebSocketApplication app) throws IOException {
            super(new URL(app.getAddress()), app);
        }

        @Override
        protected void doConnect(boolean finishNioConnect) throws IOException {
            super.doConnect(finishNioConnect);
            getWebSocket().send(MESSAGE);
        }
    }

    private static class FloodListener implements WebSocketListener {
        private CountDownLatch latch = new CountDownLatch(1);
        public void onClose(WebSocket socket) throws IOException {
        }

        public void onConnect(WebSocket socket) throws IOException {
        }

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
}
