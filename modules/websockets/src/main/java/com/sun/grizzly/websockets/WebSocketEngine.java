/*
 *
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2007-2010 Sun Microsystems, Inc. All rights reserved.
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
 *
 */

package com.sun.grizzly.websockets;

import com.sun.grizzly.Buffer;
import com.sun.grizzly.Connection;
import com.sun.grizzly.Grizzly;
import com.sun.grizzly.TransportFactory;
import com.sun.grizzly.attributes.Attribute;
import com.sun.grizzly.memory.MemoryManager;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class WebSocketEngine {
    private final Attribute<WebSocketHolder> webSocketAttr =
            Grizzly.DEFAULT_ATTRIBUTE_BUILDER.createAttribute("web-socket");
    
    private static final Logger logger = Grizzly.logger(WebSocketEngine.class);
    
    private static final WebSocketEngine engine = new WebSocketEngine();
    private final Map<String, WebSocketApplication> applications =
            new ConcurrentHashMap<String, WebSocketApplication>();

    public static WebSocketEngine getEngine() {
        return engine;
    }

    public WebSocketApplication getApplication(String uri) {
        return applications.get(uri);
    }

    boolean isWebSocket(Connection connection) {
        return webSocketAttr.get(connection) != null;
    }

    WebSocket getWebSocket(final Connection connection) {
        final WebSocketHolder holder = webSocketAttr.get(connection);
        return holder != null ? holder.websocket : null;
    }

    WebSocketHolder getWebSocketHolder(final Connection connection) {
        return webSocketAttr.get(connection);
    }

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

    void setClientConnectContext(Connection connection,
            ClientWebSocketMeta meta, WebSocketClientHandler handler,
            WebSocketConnectHandler connectHandler) {

        webSocketAttr.set(connection, new WebSocketHolder(null,
                new Object[] {meta, handler, connectHandler}));
    }

    public void register(String name, WebSocketApplication app) {
        applications.put(name, app);
    }

    ServerWebSocket handleServerHandshake(
            final Connection connection, final ClientWebSocketMeta clientMeta)
            throws HandshakeException {
        
        final WebSocketApplication app =
                getApplication(clientMeta.getURI().getPath());
        if (app == null) {
            throw new HandshakeException(404, "Application was not found");
        }

        final byte[] serverKey = generateServerKey(clientMeta);
        app.handshake(clientMeta);
        final ServerWebSocketMeta serverMeta = new ServerWebSocketMeta(
                clientMeta.getURI(), serverKey, clientMeta.getOrigin(),
                clientMeta.getHost(), clientMeta.getProtocol());


        ServerWebSocket websocket = app.createWebSocket(connection, serverMeta);
        if (websocket == null) {
            websocket = new ServerWebSocket(connection, serverMeta, app);

        }

        webSocketAttr.set(connection, new WebSocketHolder(websocket, null));

        return websocket;
    }

    ClientWebSocket handleClientHandshake(Connection connection,
            ServerWebSocketMeta serverMeta) {

        final WebSocketHolder holder = getWebSocketHolder(connection);
        final Object[] context = (Object[]) holder.context;
        final ClientWebSocketMeta meta = (ClientWebSocketMeta) context[0];
        final WebSocketClientHandler handler = (WebSocketClientHandler) context[1];
        final WebSocketConnectHandler connectHandler = (WebSocketConnectHandler) context[2];
        
        try {
            final byte[] patternServerKey =
                    generateServerKey(meta);
            final byte[] serverKey = serverMeta.getKey();

            if (!Arrays.equals(patternServerKey, serverKey)) {
                throw new HandshakeException("Keys do not match");
            }

            ClientWebSocket websocket = handler.createWebSocket(connection,
                    meta, handler);
            if (websocket == null) {
                websocket = new ClientWebSocket(connection, meta, handler);
            }
            
            handler.handshake(websocket, serverMeta);

            holder.websocket = websocket;
            holder.context = null;

            connectHandler.completed(websocket);
            
            return websocket;
        } catch (Exception e) {
            connectHandler.failed(e);
            throw new IllegalStateException(e);
        }
    }

    private static byte[] generateServerKey(ClientWebSocketMeta clientMeta)
            throws HandshakeException {
        
        final long key1 = clientMeta.getKey1().getSecKeyValue();
        final long key2 = clientMeta.getKey2().getSecKeyValue();
        final byte[] key3 = clientMeta.getKey3();

        try {
            MemoryManager mm = TransportFactory.getInstance().getDefaultMemoryManager();
            final Buffer b = mm.allocate(8);
            b.putInt((int) key1);
            b.putInt((int) key2);
            b.flip();
            final ByteBuffer bb = b.toByteBuffer();

            MessageDigest md = MessageDigest.getInstance("MD5");
            
            md.update(bb);
            b.dispose();
            
            final byte[] serverKey = md.digest(key3);
            md.reset();

            assert serverKey.length == 16;
            
            return serverKey;
        } catch (NoSuchAlgorithmException e) {
            throw new HandshakeException(500, "Can not digest using MD5");
        }
    }

    private static class WebSocketHolder {
        public volatile WebSocket websocket;
        public volatile Object context;

        public WebSocketHolder(WebSocket webSocket, Object context) {
            this.websocket = webSocket;
            this.context = context;
        }
    }
}