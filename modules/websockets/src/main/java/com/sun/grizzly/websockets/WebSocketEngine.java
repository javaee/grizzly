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

import com.sun.grizzly.BaseSelectionKeyHandler;
import com.sun.grizzly.arp.AsyncExecutor;
import com.sun.grizzly.arp.AsyncProcessorTask;
import com.sun.grizzly.http.ProcessorTask;
import com.sun.grizzly.tcp.Request;
import com.sun.grizzly.tcp.Response;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class WebSocketEngine {
    public static final String SEC_WS_PROTOCOL_HEADER = "Sec-WebSocket-Protocol";
    public static final String SEC_WS_KEY1_HEADER = "Sec-WebSocket-Key1";
    public static final String SEC_WS_KEY2_HEADER = "Sec-WebSocket-Key2";
    public static final String CLIENT_WS_ORIGIN_HEADER = "Origin";
    public static final String SERVER_SEC_WS_ORIGIN_HEADER = "Sec-WebSocket-Origin";
    public static final String SERVER_SEC_WS_LOCATION_HEADER = "Sec-WebSocket-Location";
    public static final String WEBSOCKET = "websocket";

    static final Logger logger = Logger.getLogger(WebSocketEngine.WEBSOCKET);
    private static final WebSocketEngine engine = new WebSocketEngine();
    private final Map<String, WebSocketApplication> applications = new HashMap<String, WebSocketApplication>();
    static final int INITIAL_BUFFER_SIZE = 8192;
    private final WebSocketCloseHandler closeHandler = new WebSocketCloseHandler();

    private WebSocketEngine() {
        SecKey.init();
    }

    public static WebSocketEngine getEngine() {
        return engine;
    }

    public WebSocketApplication getApplication(String uri) {
        return applications.get(uri);
    }

    public boolean handle(AsyncExecutor asyncExecutor) {
        WebSocket socket = null;
        try {
            Request request = asyncExecutor.getProcessorTask().getRequest();
            if ("WebSocket".equalsIgnoreCase(request.getHeader("Upgrade"))) {
                socket = getWebSocket(asyncExecutor, request);
            }
        } catch (IOException e) {
            return false;
        }
        return socket != null;
    }

    protected WebSocket getWebSocket(AsyncExecutor asyncExecutor, Request request) throws IOException {
        final WebSocketApplication app = WebSocketEngine.getEngine().getApplication(request.requestURI().toString());
        BaseServerWebSocket socket = null;
        try {
            if (app != null) {
                final Response response = request.getResponse();
                ProcessorTask task = asyncExecutor.getProcessorTask();
                AsyncProcessorTask asyncTask = (AsyncProcessorTask) asyncExecutor.getAsyncTask();
                final SelectionKey key = task.getSelectionKey();
                final ServerNetworkHandler handler = new ServerNetworkHandler(task, asyncTask, request, response);
                ((BaseSelectionKeyHandler) task.getSelectorHandler().getSelectionKeyHandler())
                        .setConnectionCloseHandler(closeHandler);

                socket = (BaseServerWebSocket) app.createSocket(handler, app, new KeyWebSocketListener(key));
                handler.handshake(task.getSSLSupport() != null);

                enableRead(task, key);
                key.attach(handler.getAttachment());
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, e.getMessage(), e);
            socket = null;
        } catch (HandshakeException e) {
            logger.log(Level.SEVERE, e.getMessage(), e);
            socket = null;
        }
        return socket;
    }

    final void enableRead(ProcessorTask task, SelectionKey key) {
        task.getSelectorHandler().register(key, SelectionKey.OP_READ);
    }

    public void register(String name, WebSocketApplication app) {
        applications.put(name, app);
    }

    private static class KeyWebSocketListener implements WebSocketListener {
        private final SelectionKey key;

        public KeyWebSocketListener(SelectionKey key) {
            this.key = key;
        }

        public void onClose(WebSocket socket) throws IOException {
            key.cancel();
            key.channel().close();
        }

        public void onConnect(WebSocket socket) {
        }

        public void onMessage(WebSocket socket, DataFrame frame) throws IOException {
        }
    }
}