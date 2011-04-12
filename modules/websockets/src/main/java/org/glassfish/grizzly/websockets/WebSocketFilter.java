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
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.NIOTransportBuilder;
import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.Filter;
import org.glassfish.grizzly.filterchain.FilterChain;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.http.HttpClientFilter;
import org.glassfish.grizzly.http.HttpContent;
import org.glassfish.grizzly.http.HttpHeader;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.http.HttpResponsePacket;
import org.glassfish.grizzly.http.HttpServerFilter;
import org.glassfish.grizzly.memory.Buffers;
import org.glassfish.grizzly.memory.MemoryManager;
import org.glassfish.grizzly.websockets.WebSocketEngine.WebSocketHolder;

/**
 * WebSocket {@link Filter} implementation, which supposed to be placed into a {@link FilterChain} right after HTTP
 * Filter: {@link HttpServerFilter}, {@link HttpClientFilter}; depending whether it's server or client side. The
 * <tt>WebSocketFilter</tt> handles websocket connection, handshake phases and, when receives a websocket frame -
 * redirects it to appropriate handler ({@link WebSocketApplication}, {@link WebSocket}) for processing.
 *
 * @author Alexey Stashok
 */
public class WebSocketFilter extends BaseFilter {
    private static final Logger logger = Grizzly.logger(WebSocketFilter.class);
    private static final Random random = new Random();
    private static final MemoryManager memManager = MemoryManager.DEFAULT_MEMORY_MANAGER;

    /**
     * Method handles Grizzly {@link Connection} connect phase. Check if the {@link Connection} is a client-side {@link
     * WebSocket}, if yes - creates websocket handshake packet and send it to a server. Otherwise, if it's not websocket
     * connection - pass processing to the next {@link Filter} in a chain.
     *
     * @param ctx {@link FilterChainContext}
     *
     * @return {@link NextAction} instruction for {@link FilterChain}, how it should continue the execution
     *
     * @throws {@link IOException}
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
        ctx.write(WebSocketEngine.getEngine().getWebSocketHolder(connection).handshake.composeHeaders());
        ctx.flush(null);
        // call the next filter in the chain
        return ctx.getInvokeAction();
    }

    /**
     * Method handles Grizzly {@link Connection} close phase. Check if the {@link Connection} is a {@link WebSocket}, if
     * yes - tries to close the websocket gracefully (sending close frame) and calls {@link
     * WebSocket#onClose(DataFrame)}. If the Grizzly {@link Connection} is not websocket - passes processing to the next
     * filter in the chain.
     *
     * @param ctx {@link FilterChainContext}
     *
     * @return {@link NextAction} instruction for {@link FilterChain}, how it should continue the execution
     *
     * @throws {@link IOException}
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
            }
        }
        return ctx.getInvokeAction();
    }

    /**
     * Handle Grizzly {@link Connection} read phase. If the {@link Connection} has associated {@link WebSocket} object
     * (websocket connection), we check if websocket handshake has been completed for this connection, if not -
     * initiate/validate handshake. If handshake has been completed - parse websocket {@link DataFrame}s one by one and
     * pass processing to appropriate {@link WebSocket}: {@link WebSocketApplication} for server- and client- side
     * connections.
     *
     * @param ctx {@link FilterChainContext}
     *
     * @return {@link NextAction} instruction for {@link FilterChain}, how it should continue the execution
     *
     * @throws {@link IOException}
     */
    @Override
    @SuppressWarnings("unchecked")
    public NextAction handleRead(FilterChainContext ctx) throws IOException {
        // Get the Grizzly Connection
        final Connection connection = ctx.getConnection();
        // Get the parsed HttpContent (we assume prev. filter was HTTP)
        final HttpContent message = (HttpContent) ctx.getMessage();
        // Get the HTTP header
        final HttpHeader header = message.getHttpHeader();
        // Try to obtain associated WebSocket
        final WebSocketHolder holder = WebSocketEngine.getEngine().getWebSocketHolder(connection);
        WebSocket ws = getWebSocket(connection);
        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, "handleRead websocket: {0} content-size={1} headers=\n{2}",
                new Object[]{ws, message.getContent().remaining(), header});
        }
        if (ws == null || !ws.isConnected()) {
            // If websocket is null - it means either non-websocket Connection, or websocket with incomplete handshake
            if (!isWebSocketConnection(connection) &&
                !WebSocketEngine.WEBSOCKET.equalsIgnoreCase(header.getUpgrade())) {
                // if it's not a websocket connection - pass the processing to the next filter
                return ctx.getInvokeAction();
            }
            // Handle handshake
            return handleHandshake(ctx, message);
        }
        // this is websocket with the completed handshake
        if (message.getContent().hasRemaining()) {
            // get the frame(s) content
            Buffer buffer = message.getContent();
            message.recycle();
            // check if we're currently parsing a frame
            try {
                while (buffer != null && buffer.hasRemaining()) {
                    DataFrame parsingFrame = holder.frame;
                    if (parsingFrame == null) {
                        parsingFrame = new DataFrame();
                    } else {
                        if (holder.buffer != null) {
                            buffer = Buffers
                                .appendBuffers(MemoryManager.DEFAULT_MEMORY_MANAGER, holder.buffer, buffer);
                            holder.buffer = null;
                            holder.frame = null;
                        }
                    }
                    final ParseResult result = parsingFrame.unframe(holder.unmaskOnRead, buffer);
                    buffer = result.getRemainder();
                    final boolean complete = result.isComplete();
                    result.recycle();
                    if (!complete) {
                        holder.frame = parsingFrame;
                        holder.buffer = buffer;
                        break;
                    } else {
                        parsingFrame.respond(holder.webSocket);
                    }
                }
            } catch (FramingException e) {
                if (e.getCode() != -1) {
                    holder.webSocket.close(e.getCode(), e.getMessage());
                } else {
                    holder.webSocket.close();
                }
            }
        }
        return ctx.getStopAction();
    }

    /**
     * Handle Grizzly {@link Connection} write phase. If the {@link Connection} has associated {@link WebSocket} object
     * (websocket connection), we assume that message is websocket {@link DataFrame} and serialize it into a {@link
     * Buffer}.
     *
     * @param ctx {@link FilterChainContext}
     *
     * @return {@link NextAction} instruction for {@link FilterChain}, how it should continue the execution
     *
     * @throws {@link IOException}
     */
    @Override
    public NextAction handleWrite(FilterChainContext ctx) throws IOException {
        // get the associated websocket
        final WebSocket websocket = getWebSocket(ctx.getConnection());
        // if there is one
        if (websocket != null) {
            // take a message as a websocket frame
            final DataFrame frame = (DataFrame) ctx.getMessage();
            // serialize it into a Buffer
            byte[] bytes = frame.frame();
            if (!WebSocketEngine.getEngine().getWebSocketHolder(ctx.getConnection()).unmaskOnRead) {
                byte[] masked = new byte[bytes.length + 4];
                final byte[] mask = generateMask();
                System.arraycopy(mask, 0, masked, 0, WebSocketEngine.MASK_SIZE);
                for (int i = 0; i < bytes.length; i++) {
                    masked[i + WebSocketEngine.MASK_SIZE] = (byte) (bytes[i] ^ mask[i % WebSocketEngine.MASK_SIZE]);
                }
                bytes = masked;
            }
            // set Buffer as message on the context
            ctx.setMessage(Buffers.wrap(memManager, bytes));
        }
        // invoke next filter in the chain
        return ctx.getInvokeAction();
    }

    public static byte[] generateMask() {
        byte[] maskBytes = new byte[WebSocketEngine.MASK_SIZE];
        synchronized (random) {
            random.nextBytes(maskBytes);
        }
        return maskBytes;
    }

    /**
     * Handle websocket handshake
     *
     * @param ctx {@link FilterChainContext}
     * @param content HTTP message
     *
     * @return {@link NextAction} instruction for {@link FilterChain}, how it should continue the execution
     *
     * @throws {@link IOException}
     */
    private NextAction handleHandshake(FilterChainContext ctx, HttpContent content) throws IOException {
        // check if it's server or client side handshake
        return content.getHttpHeader().isRequest()
            ? handleServerHandshake(ctx, content)
            : handleClientHandShake(ctx, content);
    }

    private NextAction handleClientHandShake(FilterChainContext ctx, HttpContent content) {
        final HttpResponsePacket response = (HttpResponsePacket) content.getHttpHeader();
        final WebSocketHolder holder = WebSocketEngine.getEngine().getWebSocketHolder(ctx.getConnection());
        holder.unmaskOnRead = false;
        if (response.getStatus() != 101) {
            // if not 101 - error occurred
            throw new HandshakeException(WebSocket.PROTOCOL_ERROR,
                String.format("Invalid response code returned (%s) with message: %s", response.getStatus(),
                    response.getReasonPhrase()));
        }
        holder.handshake.validateServerResponse(response);
        holder.webSocket.onConnect();
        return ctx.getStopAction(content);
    }

    /**
     * Handle server-side websocket handshake
     *
     * @param ctx {@link FilterChainContext}
     * @param requestContent HTTP message
     *
     * @throws {@link IOException}
     */
    @SuppressWarnings("unchecked")
    private NextAction handleServerHandshake(FilterChainContext ctx, HttpContent requestContent) throws IOException {
        // get HTTP request headers
        final HttpRequestPacket request = (HttpRequestPacket) requestContent.getHttpHeader();
        try {
            WebSocketEngine.getEngine().upgrade(ctx, request);
        } catch (HandshakeException e) {
            ctx.write(composeHandshakeError(request, e));
        }
        ctx.flush(null);
        return ctx.getStopAction();
    }

    private WebSocket getWebSocket(Connection connection) {
        return WebSocketEngine.getEngine().getWebSocket(connection);
    }

    private boolean isWebSocketConnection(Connection connection) {
        return WebSocketEngine.getEngine().isWebSocket(connection);
    }

    private HttpResponsePacket composeHandshakeError(HttpRequestPacket request, HandshakeException e) {
        final HttpResponsePacket response = request.getResponse();
        response.setStatus(e.getCode());
        response.setReasonPhrase(e.getMessage());
        return response;
    }
}
