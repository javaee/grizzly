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

import com.sun.grizzly.BaseSelectionKeyHandler;
import com.sun.grizzly.arp.AsyncExecutor;
import com.sun.grizzly.arp.AsyncProcessorTask;
import com.sun.grizzly.http.ProcessorTask;
import com.sun.grizzly.http.SelectorThread;
import com.sun.grizzly.tcp.Request;
import com.sun.grizzly.tcp.Response;
import com.sun.grizzly.util.LogMessages;
import com.sun.grizzly.util.Utils;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
    public static final int INITIAL_BUFFER_SIZE = 8192;
    public static final int DEFAULT_TIMEOUT;

    private static final WebSocketEngine engine = new WebSocketEngine();
    private static volatile boolean isWebSocketEnabled = true;
    static final Logger logger = Logger.getLogger(WebSocketEngine.WEBSOCKET);
    private final List<WebSocketApplication> applications = new ArrayList<WebSocketApplication>();
    private final Map<WebSocketApplication, StackTraceElement[]> map = new HashMap<WebSocketApplication, StackTraceElement[]>();
    public static final boolean constrainApplications = Boolean.getBoolean("grizzly.websockets.constrainApplications");
    private final WebSocketCloseHandler closeHandler = new WebSocketCloseHandler();

    static {
        if(Utils.isDebugVM()) {
            DEFAULT_TIMEOUT = 900;
        } else {
            DEFAULT_TIMEOUT = 30;
        }
    }

    private WebSocketEngine() {
        SecKey.init();
    }

    /**
     * Return true is Comet is enabled, e.g. {@link SelectorThread#setEnableAsyncExecution(boolean)}
     * has been set to <tt>true</tt>
     * @return
     */
    public static boolean isWebSocketEnabled() {
        return isWebSocketEnabled;
    }

    public static void setWebSocketEnabled(boolean webSocketEnabled) {
        isWebSocketEnabled = webSocketEnabled;
    }

    public static WebSocketEngine getEngine() {
        return engine;
    }

    public WebSocketApplication getApplication(Request request) {
        WebSocketApplication app = null;
        for (WebSocketApplication application : applications) {
            if(application.upgrade(request)) {
                if(app == null) {
                    app = application;
                } else if(constrainApplications) {
                    for (Map.Entry<WebSocketApplication, StackTraceElement[]> entry : map.entrySet()) {
                        final StackTraceElement[] traceElements = entry.getValue();
                        for (StackTraceElement element : traceElements) {
                            System.out.println(element);
                        }
                    }
                    throw new HandshakeException(LogMessages.WARNING_GRIZZLY_WS_MULTIPLE_APPS());
                }
            }
        }
        return app;
    }

    public boolean upgrade(AsyncExecutor asyncExecutor) {
        try {
            Request request = asyncExecutor.getProcessorTask().getRequest();
            final WebSocketApplication app = WebSocketEngine.getEngine().getApplication(request);
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

                    socket = (BaseServerWebSocket) app.createSocket(app, new KeyWebSocketListener(key));
                    socket.setNetworkHandler(handler);
                    handler.handshake(task.getSSLSupport() != null);

                    key.attach(handler.getAttachment());
                    enableRead(task, key);
                }
            } catch (IOException e) {
                logger.log(Level.SEVERE, e.getMessage(), e);
                socket = null;
            } catch (HandshakeException e) {
                logger.log(Level.SEVERE, e.getMessage(), e);
                socket.close();
                socket = null;
            }
            return socket != null;
        } catch (IOException e) {
            return false;
        }
    }

    final void enableRead(ProcessorTask task, SelectionKey key) {
        task.getSelectorHandler().register(key, SelectionKey.OP_READ);
    }

    @Deprecated
    public void register(String name, WebSocketApplication app) {
        register(app);
    }

    public void register(WebSocketApplication app) {
        if (!isWebSocketEnabled()){
            throw new IllegalStateException(LogMessages.SEVERE_GRIZZLY_WS_NOT_ENABLED());
        }

        applications.add(app);
        map.put(app, Thread.currentThread().getStackTrace());
    }

    public void unregister(WebSocketApplication app) {
        applications.remove(app);
        map.remove(app);
    }

    private static class KeyWebSocketListener extends WebSocketAdapter {
        private final SelectionKey key;

        public KeyWebSocketListener(SelectionKey key) {
            this.key = key;
        }

        @Override
        public void onClose(WebSocket socket) throws IOException {
            key.cancel();
            key.channel().close();
        }
    }
}
