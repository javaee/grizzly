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

import com.sun.grizzly.Buffer;
import com.sun.grizzly.Connection;
import com.sun.grizzly.Grizzly;
import com.sun.grizzly.filterchain.BaseFilter;
import com.sun.grizzly.filterchain.FilterChainContext;
import com.sun.grizzly.filterchain.NextAction;
import com.sun.grizzly.http.Constants;
import com.sun.grizzly.http.HttpClientFilter;
import com.sun.grizzly.http.HttpContent;
import com.sun.grizzly.http.HttpHeader;
import com.sun.grizzly.http.HttpPacket;
import com.sun.grizzly.http.HttpRequestPacket;
import com.sun.grizzly.http.HttpResponsePacket;
import com.sun.grizzly.http.HttpServerFilter;
import com.sun.grizzly.http.Protocol;
import com.sun.grizzly.http.util.HttpStatus;
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
 * WebSocket {@link com.sun.grizzly.filterchain.Filter} implementation, which supposed to be placed into a
 * {@link com.sun.grizzly.filterchain.FilterChain} right after HTTP Filter: {@link HttpServerFilter}, {@link HttpClientFilter};
 * depending whether it's server or client side.
 * The <tt>WebSocketFilter</tt> handles websocket connection, handshake phases and, when
 * receives a websocket frame - redirects it to appropriate handler ({@link WebSocketApplication}, {@link WebSocketClientHandler}) for processing.
 *
 * @author Alexey Stashok
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

    /**
     * Method handles Grizzly {@link Connection} connect phase. Check if the {@link Connection}
     * is a client-side {@link WebSocket}, if yes - creates websocket handshake packet
     * and send it to a server. Otherwise, if it's not websocket connection - pass processing
     * to the next {@link com.sun.grizzly.filterchain.Filter} in a chain.
     * 
     * @param ctx {@link FilterChainContext}
     * @return {@link NextAction} instruction for {@link com.sun.grizzly.filterchain.FilterChain},
     *  how it should continue the execution
     * @throws {@link java.io.IOException}
     */
    @Override
    public NextAction handleConnect(FilterChainContext ctx) throws IOException {
        logger.log(Level.FINEST, "handleConnect");

        // Get connection
        final Connection connection = ctx.getConnection();

        // check if it's websocket connection
        if (!isWebSocketConnection(connection)) {
            // if not - pass processing to a next filter
            return ctx.getInvokeAction();
        }

        // get client websocket meta data
        final ClientWebSocketMeta meta = (ClientWebSocketMeta) getWebSocketMeta(connection);
        // compose client handshake packet
        final HttpContent request = composeWSRequest(connection, meta);

        // send it to a server
        ctx.write(request);

        // call the next filter in the chain
        return ctx.getInvokeAction();
    }

    /**
     * Method handles Grizzly {@link Connection} close phase. Check if the {@link Connection}
     * is a {@link WebSocket}, if yes - tries to close the websocket gracefully (sending close frame)
     * and calls {@link WebSocketHandler#onClose(com.sun.grizzly.websockets.WebSocket)}.
     * If the Grizzly {@link Connection} is not websocket - passes processing to the next filter in the chain.
     *
     * @param ctx {@link FilterChainContext}
     * @return {@link NextAction} instruction for {@link com.sun.grizzly.filterchain.FilterChain},
     *  how it should continue the execution
     * @throws {@link java.io.IOException}
     */
    @Override
    public NextAction handleClose(FilterChainContext ctx) throws IOException {
        // Get the Connection
        final Connection connection = ctx.getConnection();

        // check if Connection has associated WebSocket (is websocket)
        if (isWebSocketConnection(connection)) {
            // if yes - get websocket
            final WebSocket ws = getWebSocket(connection);
            if (ws != null) {
                // if there is associated websocket object (which means handshake was passed)
                // close it gracefully
                ws.close();
            } else {
                // if handshake wasn't passed
                WebSocketConnectHandler connectHandler =
                        removeWebSocketConnectHandler(connection);

                // check if it's client socket in connect phase
                if (connectHandler != null) {
                    // if yes - notify connect handler
                    connectHandler.failed(new ConnectException());
                }
            }
        }

        return ctx.getInvokeAction();
    }

    /**
     * Handle Grizzly {@link Connection} read phase.
     * If the {@link Connection} has associated {@link WebSocket} object (websocket connection),
     * we check if websocket handshake has been completed for this connection, if not - initiate/validate handshake.
     * If handshake has been completed - parse websocket {@link Frame}s one by one and
     * pass processing to appropriate {@link WebSocketHandler}: {@link WebSocketApplication} or {@link WebSocketClientHandler}
     * for server- and client- side connections.
     *
     * @param ctx {@link FilterChainContext}
     * @return {@link NextAction} instruction for {@link com.sun.grizzly.filterchain.FilterChain},
     *  how it should continue the execution
     * @throws {@link java.io.IOException}
     */
    @Override
    public NextAction handleRead(FilterChainContext ctx) throws IOException {
        // Get the Grizzly Connection
        final Connection connection = ctx.getConnection();
        // Get the parsed HttpContent (we assume prev. filter was HTTP)
        final HttpContent content = (HttpContent) ctx.getMessage();
        // Get the HTTP header
        final HttpHeader header = content.getHttpHeader();

        // Try to obtain associated WebSocket
        WebSocket ws = getWebSocket(connection);

        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, "handleRead websocket: {0} content-size={1} headers=\n{2}",
                    new Object[]{ws, content.getContent().remaining(), header});
        }

        // If websocket is null - it means either non-websocket Connection, or websocket with incompleted handshake
        if (ws == null) {
            if (!isWebSocketConnection(connection) &&
                    (header.getUpgrade() == null ||
                    !WEB_SOCKET.equalsIgnoreCase(header.getUpgrade()))) {

                // if it's not a websocket connection - pass the processing to the next filter
                return ctx.getInvokeAction();
            }

            // Handle handshake
            final NextAction next = handleHandshake(ctx, content);
            if (next != null) {
                // we expect peers response, so exit the processing
                return next;
            }
        }
        
        // this is websocket with the completed handshake
        if (content.getContent().hasRemaining()) {
            // get the frame(s) content
            Buffer buffer = content.getContent();
            content.recycle();
            
            if (ws == null) {
                // make sure we got a WebSocket object
                ws = getWebSocket(connection);
            }

            final WebSocketBase wsBase = (WebSocketBase) ws;
            // check if we're currently parsing a frame
            Frame parsingFrame = wsBase.getParsingFrame();

            while (buffer != null && buffer.hasRemaining()) {
                if (parsingFrame == null) { // if not
                    // create a frame object to decode the payload to
                    parsingFrame = Frame.createFrame(
                            buffer.get(buffer.position()) & 0xFF, (Buffer) null);
                    wsBase.setParsingFrame(parsingFrame);
                }

                // parse the frame
                final ParseResult result = parsingFrame.parse(buffer);

                final boolean isComplete = result.isComplete();
                // assign remainder to a buffer
                buffer = result.getRemainder();
                result.recycle();

                // check if frame is complete
                if (!isComplete) break;

                wsBase.setParsingFrame(null);

                if (parsingFrame.isClose()) { // if parsed frame is NOT a "close" frame
                    // if it's "close" frame - gracefully close the websocket.
                    ws.close();
                    break;
                }
                
                // call appropriate handler
                ws.getHandler().onMessage(ws, parsingFrame);

                parsingFrame = null;
            }
        }

        return ctx.getStopAction();
    }

    /**
     * Handle Grizzly {@link Connection} write phase.
     * If the {@link Connection} has associated {@link WebSocket} object (websocket connection),
     * we assume that message is websocket {@link Frame} and serialize it into a {@link Buffer}.
     *
     * @param ctx {@link FilterChainContext}
     * @return {@link NextAction} instruction for {@link com.sun.grizzly.filterchain.FilterChain},
     *  how it should continue the execution
     * @throws {@link java.io.IOException}
     */
    @Override
    public NextAction handleWrite(FilterChainContext ctx) throws IOException {
        // get the associated websocket
        final WebSocket websocket = getWebSocket(ctx.getConnection());

        // if there is one
        if (websocket != null) {
            // take a message as a websocket frame
            final Frame frame = (Frame) ctx.getMessage();
            // serialize it into a Buffer
            final Buffer buffer = frame.serialize();

            // set Buffer as message on the context
            ctx.setMessage(buffer);
        }

        // invoke next filter in the chain
        return ctx.getInvokeAction();
    }

    /**
     * Handle websocket handshake
     *
     * @param ctx {@link FilterChainContext}
     * @param content HTTP message
     * 
     * @return {@link NextAction} instruction for {@link com.sun.grizzly.filterchain.FilterChain},
     *  how it should continue the execution
     * @throws {@link java.io.IOException}
     */
    private NextAction handleHandshake(FilterChainContext ctx,
            HttpContent content) throws IOException {
        
        // check if it's server or client side handshake
        if (content.getHttpHeader().isRequest()) { // server handshake
            final int remaining = content.getContent().remaining();
            // the content size should be at least 8 bytes (key3)
            if (remaining >= 8) {
                // if we have 8 bytes avail - perform server handshake
                handleServerHandshake(ctx, content);
            } else if (remaining > 0) {
                // stop the handshake and pass remainder
                return ctx.getStopAction(content);
            }

            // stop the handshake
            return ctx.getStopAction();
        } else { // client handshake
            final HttpResponsePacket response = (HttpResponsePacket) content.getHttpHeader();

            // check the server handshake response code
            if (response.getStatus() != 101) {
                // if not 101 - error occurred
                final WebSocketConnectHandler connectHandler =
                        removeWebSocketConnectHandler(ctx.getConnection());
                final HandshakeException exception =
                        new HandshakeException(response.getStatus(), response.getReasonPhrase());
                // if there is a connect handler registered - notify it
                if (connectHandler != null) {
                    connectHandler.failed(exception);
                }

                throw new IOException(exception);
            }

            // if the server response code is fine (101) - process the handshake
            final int remaining = content.getContent().remaining();

            if (remaining >= 16) { // we expect 16bytes content (security key length).
                // handle client handshake
                handleClientHandshake(ctx, content);
            } else if (remaining > 0) {
                // return stop action and save the remainder
                return ctx.getStopAction(content);
            }
        }

        // handshake is completed
        return null;
    }

    /**
     * Handle server-side websocket handshake
     *
     * @param ctx {@link FilterChainContext}
     * @param requestContent HTTP message
     *
     * @throws {@link java.io.IOException}
     */
    private void handleServerHandshake(FilterChainContext ctx,
            HttpContent requestContent) throws IOException {
        // get HTTP request headers
        final HttpRequestPacket request = (HttpRequestPacket) requestContent.getHttpHeader();
        HttpPacket response;

        try {
            final ClientWebSocketMeta clientMeta;
            
            try {
                // compose the client meta basing on the HTTP request
                clientMeta = composeClientWSMeta(requestContent);
            } catch (IllegalArgumentException e) {
                logger.log(Level.WARNING, "Bad client credentials", e);
                throw new HandshakeException(400, "Bad client credentials");
            } catch (URISyntaxException e) {
                throw new HandshakeException(400, "Bad client credentials");
            }

            // do handshake
            final WebSocket websocket =
                    WebSocketEngine.getEngine().handleServerHandshake(
                    ctx.getConnection(), clientMeta);

            // compose HTTP response basing on server meta data
            response = composeWSResponse(ctx.getConnection(), request,
                    (ServerWebSocketMeta) websocket.getMeta());

            // notify webapplication about new websocket
            ((WebSocketApplication) websocket.getHandler()).onAccept(websocket);

        } catch (HandshakeException e) {
            response = composeHandshakeError(request, e);
        }

        // send the response
        ctx.write(response);
    }

    /**
     * Handle client-side websocket handshake
     *
     * @param ctx {@link FilterChainContext}
     * @param responseContent HTTP message
     *
     * @throws {@link java.io.IOException}
     */
    private void handleClientHandshake(FilterChainContext ctx,
            HttpContent responseContent) throws IOException {

        // Get associated Grizzly connection
        final Connection connection = ctx.getConnection();
        // Compose server meta data basing on the server HTTP response
        final ServerWebSocketMeta serverMeta = composeServerWSMeta(responseContent);

        try {
            // do handshake
            WebSocket websocket =
                    WebSocketEngine.getEngine().handleClientHandshake(
                    connection, serverMeta);

            // notify the client handler about websocket connection
            ((WebSocketClientHandler) websocket.getHandler()).onConnect(websocket);
        } catch (HandshakeException e) {
            throw new IOException(e);
        }
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

        return new ClientWebSocketMeta(
                new URI(request.getRequestURI()),
                request.getHeader(CLIENT_WS_ORIGIN_HEADER),
                request.getHeader(SEC_WS_PROTOCOL_HEADER),
                request.getHeader("host"),
                request.getHeader(SEC_WS_KEY1_HEADER),
                request.getHeader(SEC_WS_KEY2_HEADER),
                key3,
                request.isSecure());
    }

    private ServerWebSocketMeta composeServerWSMeta(
            HttpContent responseContent) {

        final HttpResponsePacket response = (HttpResponsePacket) responseContent.getHttpHeader();
        final Buffer buffer = responseContent.getContent();
        final byte[] serverKey = new byte[16];
        buffer.get(serverKey);

        return new ServerWebSocketMeta(
                null,
                response.getHeader(SERVER_SEC_WS_ORIGIN_HEADER),
                response.getHeader(SERVER_SEC_WS_LOCATION_HEADER),
                response.getHeader(SEC_WS_PROTOCOL_HEADER),
                serverKey,
                response.isSecure());
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

        return HttpContent.builder(httpRequest)
                .content(MemoryUtils.wrap(mm, meta.getKey3()))
                .build();
    }

    private HttpContent composeWSResponse(Connection connection,
            HttpRequestPacket request, ServerWebSocketMeta meta) {

        final MemoryManager mm = connection.getTransport().getMemoryManager();

        final HttpResponsePacket response = request.getResponse();

        HttpStatus.WEB_SOCKET_PROTOCOL_HANDSHAKE_101.setValues(response);

        response.setProtocol(Protocol.HTTP_1_1);
        response.setUpgrade("WebSocket");
        response.setHeader("Connection", "Upgrade");

        response.setHeader("Sec-WebSocket-Origin", meta.getOrigin());
        response.setHeader("Sec-WebSocket-Location", meta.getLocation());

        final String protocol = meta.getProtocol();
        if (protocol != null) {
            response.setHeader("Sec-WebSocket-Protocol", protocol);
        }

        final Buffer serverKeyBuffer = MemoryUtils.wrap(mm, meta.getKey());

        return HttpContent.builder(response)
                .content(serverKeyBuffer)
                .build();
    }

    private HttpResponsePacket composeHandshakeError(HttpRequestPacket request,
            HandshakeException e) {
        final HttpResponsePacket response = request.getResponse();

        response.setStatus(e.getCode());
        response.setReasonPhrase(e.getMessage());

        return response;
    }
}
