/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2013 Oracle and/or its affiliates. All rights reserved.
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
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Grizzly;
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
import org.glassfish.grizzly.utils.IdleTimeoutFilter;
import org.glassfish.grizzly.websockets.WebSocketEngine.WebSocketHolder;
import org.glassfish.grizzly.websockets.draft06.ClosingFrame;

/**
 * WebSocket {@link Filter} implementation, which supposed to be placed into a {@link FilterChain} right after HTTP
 * Filter: {@link HttpServerFilter}, {@link HttpClientFilter}; depending whether it's server or client side. The
 * <tt>WebSocketFilter</tt> handles websocket connection, handshake phases and, when receives a websocket frame -
 * redirects it to appropriate connection ({@link WebSocketApplication}, {@link WebSocket}) for processing.
 *
 * @author Alexey Stashok
 */
public class WebSocketFilter extends BaseFilter {
    
    private static final Logger logger = Grizzly.logger(WebSocketFilter.class);
    private static final long DEFAULT_WS_IDLE_TIMEOUT_IN_SECONDS = 15 * 60;
    private final long wsTimeoutMS;
    
    
    // ------------------------------------------------------------ Constructors


    /**
     * Constructs a new <code>WebSocketFilter</code> with a default idle connection
     * timeout of 15 minutes;
     */
    public WebSocketFilter()  {
        this(DEFAULT_WS_IDLE_TIMEOUT_IN_SECONDS);
    }

    /**
     * Constructs a new <code>WebSocketFilter</code> with a default idle connection
     * timeout of 15 minutes;
     */
    public WebSocketFilter(final long wsTimeoutInSeconds) {
        if (wsTimeoutInSeconds <= 0) {
            this.wsTimeoutMS = IdleTimeoutFilter.FOREVER;
        } else {
            this.wsTimeoutMS = wsTimeoutInSeconds * 1000;
        }
    }
    
    // ----------------------------------------------------- Methods from Filter
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
        if (!webSocketInProgress(connection)) {
            // if not - pass processing to a next filter
            return ctx.getInvokeAction();
        }

        WebSocketEngine.getEngine().getWebSocketHolder(connection).handshake.initiate(ctx);
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
        if (webSocketInProgress(connection)) {
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
            if (!webSocketInProgress(connection) &&
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
                    if (holder.buffer != null) {
                        buffer = Buffers.appendBuffers(
                                ctx.getMemoryManager(), holder.buffer, buffer);
                        
                        holder.buffer = null;
                    }
                    final DataFrame result = holder.handler.unframe(buffer);
                    if (result == null) {
                        holder.buffer = buffer;
                        break;
                    } else {
                        result.respond(holder.webSocket);
                    }
                }
            } catch (FramingException e) {
                holder.webSocket.onClose(new ClosingFrame(e.getClosingCode(), e.getMessage()));
            } catch (Exception wse) {
                if (holder.application.onError(holder.webSocket, wse)) {
                    holder.webSocket.onClose(new ClosingFrame(1011, wse.getMessage()));
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
        final Object msg = ctx.getMessage();
        // if there is one
        if (websocket != null && DataFrame.isDataFrame(msg)) {
            final DataFrame frame = (DataFrame) msg;
            final WebSocketHolder holder = WebSocketEngine.getEngine().getWebSocketHolder(ctx.getConnection());
            final Buffer wrap = Buffers.wrap(ctx.getMemoryManager(), holder.handler.frame(frame));
            ctx.setMessage(wrap);
            ctx.flush(null);
        }
        // invoke next filter in the chain
        return ctx.getInvokeAction();
    }
    
    
    // --------------------------------------------------------- Private Methods

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

    private static NextAction handleClientHandShake(FilterChainContext ctx, HttpContent content) {
        final WebSocketHolder holder = WebSocketEngine.getEngine().getWebSocketHolder(ctx.getConnection());
/*
        if (response.getStatus() != 101) {
            // if not 101 - error occurred
            throw new HandshakeException(WebSocket.PROTOCOL_ERROR,
                String.format("Invalid response code returned (%s) with message: %s", response.getStatus(),
                    response.getReasonPhrase()));
        }
*/
        holder.handshake.validateServerResponse((HttpResponsePacket) content.getHttpHeader());
        holder.webSocket.onConnect();
        
        if (content.getContent().hasRemaining()) {
            return ctx.getRerunFilterAction();
        } else {
            content.recycle();
            return ctx.getStopAction();
        }
    }

    /**
     * Handle server-side websocket handshake
     *
     * @param ctx {@link FilterChainContext}
     * @param requestContent HTTP message
     *
     * @throws {@link IOException}
     */
    private NextAction handleServerHandshake(final FilterChainContext ctx,
                                             final HttpContent requestContent)
    throws IOException {

        // get HTTP request headers
        final HttpRequestPacket request = (HttpRequestPacket) requestContent.getHttpHeader();
        try {
            if (doServerUpgrade(ctx, requestContent)) {
                return ctx.getInvokeAction(); // not a WS request, pass to the next filter.
            }
            setIdleTimeout(ctx);
        } catch (HandshakeException e) {
            ctx.write(composeHandshakeError(request, e));
        }
        ctx.flush(null);
        
        requestContent.recycle();
        
        return ctx.getStopAction();

    }

    protected boolean doServerUpgrade(final FilterChainContext ctx,
            final HttpContent requestContent) throws IOException {
        return !WebSocketEngine.getEngine().upgrade(ctx, requestContent);
    }

    private static WebSocket getWebSocket(final Connection connection) {
        return WebSocketEngine.getEngine().getWebSocket(connection);
    }

    private static boolean webSocketInProgress(final Connection connection) {
        return WebSocketEngine.getEngine().webSocketInProgress(connection);
    }

    private static HttpResponsePacket composeHandshakeError(final HttpRequestPacket request,
                                                            final HandshakeException e) {
        final HttpResponsePacket response = request.getResponse();
        response.setStatus(e.getCode());
        response.setReasonPhrase(e.getMessage());
        return response;
    }
    
    private void setIdleTimeout(final FilterChainContext ctx) {
        final FilterChain filterChain = ctx.getFilterChain();
        if (filterChain.indexOfType(IdleTimeoutFilter.class) >= 0) {
            IdleTimeoutFilter.setCustomTimeout(ctx.getConnection(),
                    wsTimeoutMS, TimeUnit.MILLISECONDS);
        }
    }
}
