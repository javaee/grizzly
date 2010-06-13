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
import com.sun.grizzly.filterchain.BaseFilter;
import com.sun.grizzly.filterchain.FilterChainContext;
import com.sun.grizzly.filterchain.NextAction;
import com.sun.grizzly.http.Constants;
import com.sun.grizzly.http.HttpContent;
import com.sun.grizzly.http.HttpHeader;
import com.sun.grizzly.http.HttpPacket;
import com.sun.grizzly.http.HttpRequestPacket;
import com.sun.grizzly.http.HttpResponsePacket;
import com.sun.grizzly.memory.MemoryManager;
import com.sun.grizzly.memory.MemoryUtils;
import com.sun.grizzly.websockets.frame.ParseResult;
import com.sun.grizzly.websockets.frame.Frame;
import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author oleksiys
 */
public class WebSocketFilter extends BaseFilter {
    private static final Logger logger = Grizzly.logger(WebSocketFilter.class);

    private static final String WEB_SOCKET = "websocket";
    private static final String SEC_WS_PROTOCOL_HEADER = "Sec-WebSocket-Protocol";
    private static final String SEC_WS_KEY1_HEADER = "Sec-WebSocket-Key1";
    private static final String SEC_WS_KEY2_HEADER = "Sec-WebSocket-Key2";
    private static final String CLIENT_WS_ORIGIN_HEADER = "Origin";
    private static final String SERVER_SEC_WS_ORIGIN_HEADER = "Sec-WebSocket-Origin";
    private static final String SERVER_SEC_WS_LOCATION_HEADER = "Sec-WebSocket-Location";
    
    @Override
    public NextAction handleConnect(FilterChainContext ctx) throws IOException {
        logger.log(Level.FINEST, "handleConnect");

        final Connection connection = ctx.getConnection();

        if (!isWebSocketConnection(connection)) {
            return ctx.getInvokeAction();
        }

        final ClientWebSocketMeta meta = (ClientWebSocketMeta) getWebSocketMeta(connection);
        final HttpContent request = composeWSRequest(connection, meta);

        ctx.write(request);

        return ctx.getInvokeAction();
    }

    @Override
    public NextAction handleClose(FilterChainContext ctx) throws IOException {
        final Connection connection = ctx.getConnection();

        if (isWebSocketConnection(connection)) {
            final WebSocket ws = getWebSocket(connection);
            if (ws != null) {
                ws.close();
            } else {
                WebSocketConnectHandler connectHandler =
                        removeWebSocketConnectHandler(connection);
                if (connectHandler != null) {
                    connectHandler.failed(new ConnectException());
                }
            }
        }

        return ctx.getInvokeAction();
    }

    @Override
    public NextAction handleRead(FilterChainContext ctx) throws IOException {
        final Connection connection = ctx.getConnection();
        final HttpContent content = (HttpContent) ctx.getMessage();
        final HttpHeader header = content.getHttpHeader();

        WebSocket ws = getWebSocket(connection);

        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, "handleRead websocket: " + ws +
                    " content-size=" + content.getContent().remaining() +
                    " headers=\n" + header);
        }

        if (ws == null) {
            if (!isWebSocketConnection(connection) &&
                    (header.getUpgrade() == null ||
                    !WEB_SOCKET.equalsIgnoreCase(header.getUpgrade()))) {
                return ctx.getInvokeAction();
            }

            final NextAction next = handleHandshake(ctx, content);
            if (next != null) {
                return next;
            }
        }
        

        if (content.getContent().hasRemaining()) {
            final Buffer buffer = content.getContent();
            if (ws == null) {
                ws = getWebSocket(connection);
            }

            final WebSocketBase wsBase = (WebSocketBase) ws;

            Frame decodingFrame = wsBase.getDecodingFrame();

            if (decodingFrame == null) {
                decodingFrame = Frame.createFrame(
                        buffer.get(buffer.position()) & 0xFF, (Buffer) null);
                wsBase.setDecodingFrame(decodingFrame);
            }

            final ParseResult result = decodingFrame.parse(buffer);
            
            final boolean isCompleted = result.isCompleted();
            final Buffer remainder = result.getRemainder();
            result.recycle();
            
            if (isCompleted) {
                final HttpContent httpContentRemainder =
                        (remainder == null ?
                            null :
                            HttpContent.builder(header).content(remainder).last(content.isLast()).build());

                content.recycle();
                wsBase.setDecodingFrame(null);

                if (!decodingFrame.isClose()) {
                    ws.getHandler().onMessage(ws, decodingFrame);
                } else {
                    ws.close();
                }
                
                ctx.setMessage(null);
                return ctx.getInvokeAction(httpContentRemainder);
            }
        }

        return ctx.getStopAction();
    }

    @Override
    public NextAction handleWrite(FilterChainContext ctx) throws IOException {
        final WebSocket websocket = getWebSocket(ctx.getConnection());

        if (websocket != null) {
            final Frame frame = (Frame) ctx.getMessage();
            final Buffer buffer = frame.serialize();

            ctx.setMessage(buffer);
        }

        return ctx.getInvokeAction();
    }

    private NextAction handleHandshake(FilterChainContext ctx,
            HttpContent content) throws IOException {
        
        if (content.getHttpHeader().isRequest()) { // server handshake
            final int remaining = content.getContent().remaining();
            if (remaining >= 8) {
                handleServerHandshake(ctx, content);
            } else if (remaining > 0) {
                return ctx.getStopAction(content);
            }

            return ctx.getStopAction();
        } else { // client handshake
            final HttpResponsePacket response = (HttpResponsePacket) content.getHttpHeader();

            if (response.getStatus() != 101) {
                final WebSocketConnectHandler connectHandler =
                        removeWebSocketConnectHandler(ctx.getConnection());
                final HandshakeException exception =
                        new HandshakeException(response.getStatus(), response.getReasonPhrase());
                if (connectHandler != null) {
                    connectHandler.failed(exception);
                }

                throw new IOException(exception);
            }

            final int remaining = content.getContent().remaining();

            if (remaining >= 16) {
                try {
                    handleClientHandshake(ctx, content);
                } catch (HandshakeException e) {
                    throw new IOException(e);
                }
            } else if (remaining > 0) {
                return ctx.getStopAction(content);
            }
        }

        return null;
    }

    private void handleServerHandshake(FilterChainContext ctx,
            HttpContent requestContent) throws IOException {

        HttpRequestPacket request = (HttpRequestPacket) requestContent.getHttpHeader();
        HttpPacket response;

        try {
            final ClientWebSocketMeta clientMeta;
            
            try {
                clientMeta = composeClientWSMeta(requestContent);
            } catch (IllegalArgumentException e) {
                logger.log(Level.WARNING, "Bad client credentials", e);
                throw new HandshakeException(400, "Bad client credentials");
            } catch (URISyntaxException e) {
                throw new HandshakeException(400, "Bad client credentials");
            }

            final ServerWebSocket websocket =
                    WebSocketEngine.getEngine().handleServerHandshake(
                    ctx.getConnection(), clientMeta);

            response = composeWSResponse(ctx.getConnection(), request,
                    (ServerWebSocketMeta) websocket.getMeta());

            websocket.getApplication().onAccept(websocket);

        } catch (HandshakeException e) {
            response = composeHandshakeError(request, e);
        }

        ctx.write(response);
    }

    private void handleClientHandshake(FilterChainContext ctx,
            HttpContent responseContent) throws HandshakeException, IOException {

        final Connection connection = ctx.getConnection();

        final ServerWebSocketMeta serverMeta = composeServerWSMeta(responseContent);

        ClientWebSocket websocket =
                WebSocketEngine.getEngine().handleClientHandshake(
                connection, serverMeta);

        ((WebSocketClientHandler) websocket.getHandler()).onConnect(websocket);
    }

    private WebSocket getWebSocket(final Connection connection) {
        return WebSocketEngine.getEngine().getWebSocket(connection);
    }
    
    private boolean isWebSocketConnection(final Connection connection) {
        return WebSocketEngine.getEngine().isWebSocket(connection);
    }

    private WebSocketMeta getWebSocketMeta(final Connection connection) {
        return WebSocketEngine.getEngine().getWebSocketMeta(connection);
    }

    private WebSocketConnectHandler removeWebSocketConnectHandler(
            final Connection connection) {
        return WebSocketEngine.getEngine().removeWebSocketConnectHandler(connection);
    }

    private ClientWebSocketMeta composeClientWSMeta(HttpContent requestContent)
            throws URISyntaxException {

        final HttpRequestPacket request = (HttpRequestPacket) requestContent.getHttpHeader();
        final Buffer buffer = requestContent.getContent();
        final byte[] key3 = new byte[8];
        buffer.get(key3);

        final ClientWebSocketMeta clientMeta = new ClientWebSocketMeta(
                new URI(request.getRequestURI()),
                request.getHeader("host"),
                request.getHeader(CLIENT_WS_ORIGIN_HEADER),
                request.getHeader(SEC_WS_PROTOCOL_HEADER),
                request.getHeader(SEC_WS_KEY1_HEADER),
                request.getHeader(SEC_WS_KEY2_HEADER),
                key3);

        return clientMeta;
    }

    private ServerWebSocketMeta composeServerWSMeta(
            HttpContent responseContent) {

        final HttpResponsePacket response = (HttpResponsePacket) responseContent.getHttpHeader();
        final Buffer buffer = responseContent.getContent();
        final byte[] serverKey = new byte[16];
        buffer.get(serverKey);

        ServerWebSocketMeta serverMeta = new ServerWebSocketMeta(
                null, serverKey,
                response.getHeader(SERVER_SEC_WS_ORIGIN_HEADER),
                response.getHeader(SERVER_SEC_WS_LOCATION_HEADER),
                response.getHeader(SEC_WS_PROTOCOL_HEADER));

        return serverMeta;
    }

    private HttpContent composeWSRequest(Connection connection,
            ClientWebSocketMeta meta) {
        
        final URI uri = meta.getURI();

        final HttpRequestPacket.Builder builder = HttpRequestPacket.builder()
                .method("GET")
                .uri(uri.getPath())
                .protocol(Constants.HTTP_11)
                .upgrade("WebSocket")
                .header("Connection", "Upgrade")
                .header("Host", uri.getHost())
                .header(CLIENT_WS_ORIGIN_HEADER, meta.getOrigin());

        if (meta.getProtocol() != null) {
            builder.header(SEC_WS_PROTOCOL_HEADER, meta.getProtocol());
        }

        builder.header(SEC_WS_KEY1_HEADER, meta.getKey1().getSecKey());
        builder.header(SEC_WS_KEY2_HEADER, meta.getKey2().getSecKey());

        final HttpRequestPacket httpRequest = builder.build();

        final MemoryManager mm = connection.getTransport().getMemoryManager();
        final HttpContent content = HttpContent.builder(httpRequest)
                .content(MemoryUtils.wrap(mm, meta.getKey3()))
                .build();

        return content;
    }

    private HttpContent composeWSResponse(Connection connection,
            HttpRequestPacket request, ServerWebSocketMeta meta) {

        final MemoryManager mm = connection.getTransport().getMemoryManager();

        final HttpResponsePacket response = request.getResponse();

        response.setStatus(101);
        response.setReasonPhrase("Web Socket Protocol Handshake");

        response.setUpgrade("WebSocket");
        response.setHeader("Connection", "Upgrade");

        response.setHeader("WebSocket-Origin", meta.getOrigin());
        response.setHeader("WebSocket-Location", meta.getLocation());
        response.setHeader("Sec-WebSocket-Protocol", meta.getProtocol());

        final Buffer serverKeyBuffer = MemoryUtils.wrap(mm, meta.getKey());

        final HttpContent httpContent = HttpContent.builder(response)
                .content(serverKeyBuffer)
                .build();

        return httpContent;
    }

    private HttpResponsePacket composeHandshakeError(HttpRequestPacket request,
            HandshakeException e) {
        final HttpResponsePacket response = request.getResponse();

        response.setStatus(e.getCode());
        response.setReasonPhrase(e.getMessage());

        return response;
    }
}
