/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2012 Oracle and/or its affiliates. All rights reserved.
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
import com.sun.grizzly.tcp.Request;
import com.sun.grizzly.tcp.Response;
import com.sun.grizzly.util.Utils;
import com.sun.grizzly.util.http.MimeHeaders;
import com.sun.grizzly.util.http.mapper.Mapper;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class WebSocketEngine {
    public static final String SEC_WS_ACCEPT = "Sec-WebSocket-Accept";
    public static final String SEC_WS_KEY_HEADER = "Sec-WebSocket-Key";
    public static final String SEC_WS_ORIGIN_HEADER = "Sec-WebSocket-Origin";
    public static final String SEC_WS_PROTOCOL_HEADER = "Sec-WebSocket-Protocol";
    public static final String SEC_WS_EXTENSIONS_HEADER = "Sec-WebSocket-Extensions";
    public static final String SEC_WS_VERSION = "Sec-WebSocket-Version";
    public static final String WEBSOCKET = "websocket";
    public static final String RESPONSE_CODE_HEADER = "Response Code";
    public static final String RESPONSE_CODE_MESSAGE = "Switching Protocols";
    public static final String RESPONSE_CODE_VALUE = "101";
    public static final String UPGRADE = "upgrade";
    public static final String CONNECTION = "connection";
    public static final String CLIENT_WS_ORIGIN_HEADER = "Origin";
    public static final Version DEFAULT_VERSION = Version.DRAFT17;
    public static final int INITIAL_BUFFER_SIZE = 8192;

    public static final int DEFAULT_TIMEOUT;
    private static final WebSocketEngine engine = new WebSocketEngine();
    static final Logger logger = Logger.getLogger(WebSocketEngine.WEBSOCKET);
    public static final String SERVER_KEY_HASH = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
    public static final int MASK_SIZE = 4;
    private final List<WebSocketApplication> applications = new ArrayList<WebSocketApplication>();
    private final WebSocketCloseHandler closeHandler = new WebSocketCloseHandler();

    static {
        if (Utils.isDebugVM()) {
            DEFAULT_TIMEOUT = 900;
        } else {
            DEFAULT_TIMEOUT = 30;
        }
    }

    private WebSocketEngine() {
    }

    public static WebSocketEngine getEngine() {
        return engine;
    }

    public static byte[] toArray(long length) {
        long value = length;
        byte[] b = new byte[8];
        for (int i = 7; i >= 0 && value > 0; i--) {
            b[i] = (byte) (value & 0xFF);
            value >>= 8;
        }
        return b;
    }

    public static long toLong(byte[] bytes, int start, int end) {
        long value = 0;
        for (int i = start; i < end; i++) {
            value <<= 8;
            value ^= (long) bytes[i] & 0xFF;
        }
        return value;
    }

    public static ProtocolHandler loadHandler(MimeHeaders headers) {
        for (Version version : Version.values()) {
            if(version.validate(headers)) {
                return version.createHandler(false);
            }
        }
        return null;
    }

    public WebSocketApplication getApplication(Request request) {
        for (WebSocketApplication application : applications) {
            if (application.isApplicationRequest(request)) {
                return application;
            }
        }
        return null;
    }

    // maintained for compatibility - useful still for most cases.
    public boolean upgrade(AsyncExecutor asyncExecutor) {
        return upgrade(asyncExecutor, null);
    }

    // Mapper will be non-null when integrated in GF.
    public boolean upgrade(AsyncExecutor asyncExecutor, Mapper mapper) {
        Request request = asyncExecutor.getProcessorTask().getRequest();
        final MimeHeaders mimeHeaders = request.getMimeHeaders();
        if (isUpgradable(request)) {
            try {
                final WebSocketApplication app = getApplication(request);
                WebSocket socket = null;
                try {
                    if (app != null) {

                        ProcessorTask task = asyncExecutor.getProcessorTask();
                        AsyncProcessorTask asyncTask =
                                (AsyncProcessorTask) asyncExecutor.getAsyncTask();
                        final SelectionKey key = task.getSelectionKey();

                        final ProtocolHandler protocolHandler = loadHandler(mimeHeaders);
                        if (protocolHandler == null) {
                            try {
                                handleUnsupportedVersion(request.getResponse());
                                return true;
                            } catch (IOException ignored) {
                            }
                        }
                        final ServerNetworkHandler handler = new ServerNetworkHandler(
                                request, request.getResponse(), protocolHandler, mapper);
                        protocolHandler.setNetworkHandler(handler);
                        protocolHandler.setKey(key);
                        protocolHandler.setProcessorTask(task);
                        protocolHandler.setAsyncTask(asyncTask);

                        protocolHandler.handshake(app, request);

                        socket = app.createWebSocket(protocolHandler, app, new KeyWebSocketListener(key));
                        if (socket instanceof DefaultWebSocket) {

                        }

                        ((BaseSelectionKeyHandler) task.getSelectorHandler().getSelectionKeyHandler())
                                .setConnectionCloseHandler(closeHandler);

                        key.attach(
                                new WebSocketSelectionKeyAttachment(protocolHandler,
                                                                    handler,
                                                                    task,
                                                                    asyncTask));
                        
                        socket.onConnect();
                        enableRead(task, key);
                        return true;
                    }
                } catch (HandshakeException e) {
                    logger.log(Level.SEVERE, e.getMessage(), e);
                    if (socket != null) {
                        socket.close();
                    }
                }
            } catch (WebSocketException e) {
                return false;
            }
        }

        return false;
    }

    private static void handleUnsupportedVersion(final Response response) 
    throws IOException {
        response.setStatus(400);
        response.setMessage("Bad Request");
        response.addHeader(WebSocketEngine.SEC_WS_VERSION, Version.getSupportedWireProtocolVersions());
        response.sendHeaders();
        response.flush();
    }

    private boolean isUpgradable(Request request) {
        final String s = request.getHeader("Upgrade");
        return "WebSocket".equalsIgnoreCase(s);
    }

    final void enableRead(ProcessorTask task, SelectionKey key) {
        task.getSelectorHandler().register(key, SelectionKey.OP_READ);
    }

    @Deprecated
    public void register(String name, WebSocketApplication app) {
        register(app);
    }

    public void register(WebSocketApplication app) {
        applications.add(app);
    }

    /**
     * When invoked, the all websockets currently connected to the
     * application will be closed.
     *
     * @param app the application to de-register
     */
    public void unregister(WebSocketApplication app) {
        applications.remove(app);
        app.shutdown();
    }

}
