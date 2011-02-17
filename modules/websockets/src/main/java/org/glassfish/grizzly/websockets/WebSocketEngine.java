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

import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.attributes.Attribute;
import java.net.URI;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import org.glassfish.grizzly.impl.FutureImpl;

/**
 * WebSockets engine implementation (singlton), which handles {@link WebSocketApplication}s
 * registration, responsible for client and server handshake validation.
 * 
 * @see WebSocket
 * @see WebSocketApplication
 *
 * @author Alexey Stashok.
 */
public class WebSocketEngine {

    // Grizzly Connection "websocket" attribute.
    private final Attribute<WebSocketHolder> webSocketAttr =
            Grizzly.DEFAULT_ATTRIBUTE_BUILDER.createAttribute("web-socket");
    
    private static final Logger logger = Grizzly.logger(WebSocketEngine.class);
    
    // WebSocketEngine singleton instance
    private static final WebSocketEngine engine = new WebSocketEngine();

    // WebSocketApplications map <app-name, WebSocketApplication>
    private final Map<String, WebSocketApplication> applications =
            new ConcurrentHashMap<String, WebSocketApplication>();

    /**
     * Get <tt>WebSocketEngine</tt> instance (singleton).
     *
     * @return <tt>WebSocketEngine</tt> instance (singleton).
     */
    public static WebSocketEngine getEngine() {
        return engine;
    }

    /**
     * Get the {@link WebSocketApplication}, associated with the passed name.
     * Name is usually taken as URL's path.
     *
     * @param name the {@link WebSocketApplication} name, usually represented by URL's path.
     * @return {@link WebSocketApplication}.
     */
    public WebSocketApplication lookupApplication(String name) {
        return applications.get(name);
    }

    /**
     * Register the {@link WebSocketApplication} and associate it with the given name.
     * Name is usually taken as URL's path.
     *
     * @param name {@link WebSocketApplication} name (usually taken as URL's path).
     * @param app {@link WebSocketApplication}
     */
    public void registerApplication(String name, WebSocketApplication app) {
        applications.put(name, app);
    }

    /**
     * Returns <tt>true</tt> if passed Grizzly {@link Connection} is associated with a {@link WebSocket},
     * or <tt>false</tt> otherwise.
     * 
     * @param connection Grizzly {@link Connection}.
     * @return <tt>true</tt> if passed Grizzly {@link Connection} is associated with a {@link WebSocket},
     * or <tt>false</tt> otherwise.
     */
    boolean isWebSocket(Connection connection) {
        return webSocketAttr.get(connection) != null;
    }

    /**
     * Get the {@link WebSocket} associated with the Grizzly {@link Connection},
     * or <tt>null</tt>, if there none is associated.
     * 
     * @param connection Grizzly {@link Connection}.
     * @return the {@link WebSocket} associated with the Grizzly {@link Connection},
     * or <tt>null</tt>, if there none is associated.
     */
    WebSocket getWebSocket(final Connection connection) {
        final WebSocketHolder holder = webSocketAttr.get(connection);
        return holder != null ? holder.websocket : null;
    }

    /**
     * Get the {@link WebSocketHolder} associated with the Grizzly {@link Connection},
     * or <tt>null</tt>, if there none is associated.
     *
     * @param connection Grizzly {@link Connection}.
     * @return the {@link WebSocketHolder} associated with the Grizzly {@link Connection},
     * or <tt>null</tt>, if there none is associated.
     */
    WebSocketHolder getWebSocketHolder(final Connection connection) {
        return webSocketAttr.get(connection);
    }

    /**
     * Get the {@link WebSocketMeta} associated with the Grizzly {@link Connection},
     * or <tt>null</tt>, if there none is associated.
     *
     * @param connection Grizzly {@link Connection}.
     * @return the {@link WebSocketMeta} associated with the Grizzly {@link Connection},
     * or <tt>null</tt>, if there none is associated.
     */
    WebSocketMeta getWebSocketMeta(Connection connection) {
        final WebSocketHolder holder = webSocketAttr.get(connection);
        final WebSocket ws = holder.websocket;
        final Object context = holder.context;

        if (ws != null) {
            return ws.getMeta();
        } else if (context != null) {
            return (WebSocketMeta) ((Object[]) context)[0];
        }

        return null;
    }

    /**
     * Remove client-side {@link WebSocketConnectHandler}, associated with the
     * passed Grizzly {@link Connection}.
     *
     * @param connection Grizzly {@link Connection}.
     * @return removed associated {@link WebSocketConnectHandler}.
     */
    WebSocketConnectHandler removeWebSocketConnectHandler(Connection connection) {
        final WebSocketHolder holder = webSocketAttr.get(connection);
        final Object context = holder.context;

        if (context != null) {
            final Object value = ((Object[]) context)[2];
            ((Object[]) context)[2] = null;
            return (WebSocketConnectHandler) value;
        }

        return null;
    }

    /**
     * Associate client-side {@link WebSocket} connect context with the Grizzly
     * {@link Connection}.
     *
     * @param connection Grizzly {@link Connection} to associate the context with.
     * @param meta {@link ClientWebSocketMeta}.
     * @param handler {@link WebSocketClientHandler}.
     * @param connectHandler {@link WebSocketConnectHandler}.
     */
    void setClientConnectContext(Connection connection,
            ClientWebSocketMeta meta, WebSocketClientHandler handler,
            FutureImpl<WebSocket> future) {

        webSocketAttr.set(connection, new WebSocketHolder(null,
                new Object[] {meta, handler, future}));
    }

    /**
     * Process and validate server-side handshake.
     * 
     * @param connection Grizzly {@link Connection}.
     * @param clientMeta client-side meta data {@link ClientWebSocketMeta}.
     *
     * @return {@link WebSocket}, if validation passed fine, or exception
     * will be thrown otherwise.
     * 
     * @throws HandshakeException
     */
    @SuppressWarnings("unchecked")
    WebSocket handleServerHandshake(
            final Connection connection, final ClientWebSocketMeta clientMeta)
            throws HandshakeException {
        
        final WebSocketApplication app =
                lookupApplication(clientMeta.getURI().getPath());
        if (app == null) {
            throw new HandshakeException(404, "Application was not found");
        }

        final byte[] serverKey = generateServerKey(clientMeta);
        app.handshake(clientMeta);
        final ServerWebSocketMeta serverMeta = new ServerWebSocketMeta(
                clientMeta.getURI(), clientMeta.getOrigin(),
                composeServerMetaLocation(clientMeta),
                clientMeta.getProtocol(), serverKey,  clientMeta.isSecure());


        WebSocket websocket = app.createWebSocket(connection, serverMeta);
        if (websocket == null) {
            websocket = new WebSocketBase(connection, serverMeta, app);
        }

        webSocketAttr.set(connection, new WebSocketHolder(websocket, null));

        return websocket;
    }

    /**
     * Process and validate client-side handshake.
     *
     * @param connection Grizzly {@link Connection}.
     * @param serverMeta server-side meta data {@link ServerWebSocketMeta}.
     *
     * @return {@link WebSocket}, if validation passed fine, or exception
     * will be thrown otherwise.
     *
     * @throws HandshakeException
     */
    @SuppressWarnings("unchecked")
    WebSocket handleClientHandshake(Connection connection,
            ServerWebSocketMeta serverMeta) throws HandshakeException {

        final WebSocketHolder holder = getWebSocketHolder(connection);
        final Object[] context = (Object[]) holder.context;
        final ClientWebSocketMeta meta = (ClientWebSocketMeta) context[0];
        final WebSocketClientHandler handler = (WebSocketClientHandler) context[1];
        final FutureImpl<WebSocket> future = (FutureImpl<WebSocket>) context[2];
        
        try {
            final byte[] patternServerKey =
                    generateServerKey(meta);
            final byte[] serverKey = serverMeta.getKey();

            if (!Arrays.equals(patternServerKey, serverKey)) {
                throw new HandshakeException("Keys do not match");
            }

            WebSocket websocket = handler.createWebSocket(connection, meta);
            if (websocket == null) {
                websocket = new WebSocketBase(connection, meta, handler);
            }
            
            handler.handshake(websocket, serverMeta);

            holder.websocket = websocket;
            holder.context = null;

            future.result(websocket);
            
            return websocket;
        } catch (HandshakeException e) {
            future.failure(e);
            throw e;
        } catch (Exception e) {
            future.failure(e);
            throw new HandshakeException(e.getClass().getName() + ": " + e.getMessage());
        }
    }

    /**
     * Generate server-side security key, which gets passed to the client during
     * the handshake phase as part of message payload.
     *
     * @param clientMeta client's meta data.
     * @return server key.
     *
     * @throws HandshakeException
     */
    private static byte[] generateServerKey(ClientWebSocketMeta clientMeta)
            throws HandshakeException {
        try {
            return SecKey.generateServerKey(clientMeta.getKey1(),
                    clientMeta.getKey2(), clientMeta.getKey3());
            
        } catch (NoSuchAlgorithmException e) {
            throw new HandshakeException(500, "Can not digest using MD5");
        }
    }

    private static String composeServerMetaLocation(ClientWebSocketMeta clientMeta) {
        final StringBuilder sb = new StringBuilder(128);
        final URI uri = clientMeta.getURI();
        if (uri.getScheme() != null) {
            sb.append(uri.getScheme()).append("://");
        } else {
            sb.append(clientMeta.isSecure() ? "wss://" : "ws://");
        }

        sb.append(clientMeta.getHost());
        sb.append(uri.getPath());

        return sb.toString();
    }

    /**
     * WebSocketHolder object, which gets associated with the Grizzly {@link Connection}.
     */
    private static class WebSocketHolder {
        public volatile WebSocket websocket;
        public volatile Object context;

        public WebSocketHolder(WebSocket webSocket, Object context) {
            this.websocket = webSocket;
            this.context = context;
        }
    }
}
