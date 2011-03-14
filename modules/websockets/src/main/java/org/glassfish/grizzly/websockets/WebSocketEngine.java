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
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Connection.CloseListener;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.attributes.Attribute;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.localization.LogMessages;
import org.glassfish.grizzly.utils.Utils;

/**
 * WebSockets engine implementation (singleton), which handles {@link WebSocketApplication}s registration, responsible
 * for client and server handshake validation.
 *
 * @author Alexey Stashok.
 * @see WebSocket
 * @see WebSocketApplication
 */
public class WebSocketEngine {
    public static final String SEC_WS_ACCEPT = "Sec-WebSocket-Accept";
    public static final String SEC_WS_KEY_HEADER = "Sec-WebSocket-Key";
    public static final String SEC_WS_ORIGIN_HEADER = "Sec-WebSocket-Origin";
    public static final String SEC_WS_PROTOCOL_HEADER = "Sec-WebSocket-Protocol";
    public static final String SEC_WS_EXTENSIONS_HEADER = "Sec-WebSocket-Extensions";
    public static final String SEC_WS_VERSION = "Sec-WebSocket-Version";
    public static final String WEBSOCKET = "websocket";
    public static final String RESPONSE_CODE_MESSAGE = "Switching Protocols";
    public static final int RESPONSE_CODE_VALUE = 101;
    public static final String UPGRADE = "upgrade";
    public static final String CONNECTION = "connection";
    public static final int WS_VERSION = 6;
    public static final int INITIAL_BUFFER_SIZE = 8192;
    public static final int DEFAULT_TIMEOUT;
    private static final WebSocketEngine engine = new WebSocketEngine();
    private static volatile boolean isWebSocketEnabled = true;
    static final Logger logger = Logger.getLogger(WebSocketEngine.WEBSOCKET);
    public static final String SERVER_KEY_HASH = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
    public static final int MASK_SIZE = 4;
    static final SecureRandom random = new SecureRandom();
    private final List<WebSocketApplication> applications = new ArrayList<WebSocketApplication>();
    private final Attribute<WebSocketHolder> webSocketAttribute =
        Grizzly.DEFAULT_ATTRIBUTE_BUILDER.createAttribute("web-socket");

    static {
        if (Utils.isDebugVM()) {
            DEFAULT_TIMEOUT = 900;
        } else {
            DEFAULT_TIMEOUT = 30;
        }
    }

    private WebSocketEngine() {
    }

    /**
     * @return true is WebSockets are enabled.
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

    public static byte[] generateMask() {
        byte[] maskBytes = new byte[MASK_SIZE];
        synchronized (random) {
            random.nextBytes(maskBytes);
        }
        return maskBytes;
    }

    public WebSocketApplication getApplication(HttpRequestPacket request) {
        for (WebSocketApplication application : applications) {
            if (application.upgrade(request)) {
                return application;
            }
        }
        return null;
    }

    public boolean upgrade(FilterChainContext ctx, HttpRequestPacket request) {
        try {
            final WebSocketApplication app = WebSocketEngine.getEngine().getApplication(request);
            WebSocket socket = null;
            try {
                if (app != null) {
                    final Connection connection = ctx.getConnection();
                    socket = app.createSocket(connection, app);
                    final WebSocketHolder holder = new WebSocketHolder(true, socket);
                    webSocketAttribute.set(connection, holder);
                    ctx.write(new ServerHandshake(request).respond(request));

                    request.getConnection().addCloseListener(new CloseListener() {
                        @Override
                        public void onClosed(Connection connection) {
                            final WebSocket webSocket = getWebSocket(connection);
                            webSocket.close();
                            webSocket.onClose(new ClosingFrame(WebSocket.END_POINT_GOING_DOWN,
                                "Close detected on connection"));
                        }
                    });
                    socket.onConnect();
                    return true;
                }
            } catch (HandshakeException e) {
                logger.log(Level.SEVERE, e.getMessage(), e);
                if(socket != null) {
                    socket.close();
                }
            }
        } catch (IOException e) {
            return false;
        }
        return false;
    }

    public void register(String name, WebSocketApplication app) {
        register(app);
    }

    public void register(WebSocketApplication app) {
        if (!isWebSocketEnabled()) {
            throw new IllegalStateException(LogMessages.SEVERE_GRIZZLY_WS_NOT_ENABLED());
        }
        applications.add(app);
    }

    public void unregister(WebSocketApplication app) {
        applications.remove(app);
    }

    public void unregisterAll() {
        applications.clear();
    }
    /**
     * Returns <tt>true</tt> if passed Grizzly {@link Connection} is associated with a {@link WebSocket}, or
     * <tt>false</tt> otherwise.
     *
     * @param connection Grizzly {@link Connection}.
     *
     * @return <tt>true</tt> if passed Grizzly {@link Connection} is associated with a {@link WebSocket}, or
     *         <tt>false</tt> otherwise.
     */
    boolean isWebSocket(Connection connection) {
        return webSocketAttribute.get(connection) != null;
    }

    /**
     * Get the {@link WebSocket} associated with the Grizzly {@link Connection}, or <tt>null</tt>, if there none is
     * associated.
     *
     * @param connection Grizzly {@link Connection}.
     *
     * @return the {@link WebSocket} associated with the Grizzly {@link Connection}, or <tt>null</tt>, if there none is
     *         associated.
     */
    WebSocket getWebSocket(Connection connection) {
        final WebSocketHolder holder = getWebSocketHolder(connection);
        return holder == null ? null : holder.webSocket;
    }

    WebSocketHolder getWebSocketHolder(final Connection connection) {
        return webSocketAttribute.get(connection);
    }

    WebSocketHolder setWebSocketHolder(final Connection connection, WebSocket socket) {
        final WebSocketHolder holder = new WebSocketHolder(false, socket);
        holder.webSocket = socket;
        webSocketAttribute.set(connection, holder);
        
        return holder;
    }

    /**
     * WebSocketHolder object, which gets associated with the Grizzly {@link Connection}.
     */
    public static class WebSocketHolder {
        public volatile WebSocket webSocket;
        public volatile ClientHandshake handshake;
        public volatile DataFrame frame;
        public volatile boolean unmaskOnRead;
        public volatile Buffer buffer;

        public WebSocketHolder(final boolean unmaskOnRead, final WebSocket socket) {
            this.unmaskOnRead = unmaskOnRead;
            webSocket = socket;
        }
    }
}
