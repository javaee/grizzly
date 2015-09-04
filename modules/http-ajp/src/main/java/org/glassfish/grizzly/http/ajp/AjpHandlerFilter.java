/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2015 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.http.ajp;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.Properties;
import java.util.Queue;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.attributes.Attribute;
import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.FilterChainEvent;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.http.HttpContent;
import org.glassfish.grizzly.http.HttpContext;
import org.glassfish.grizzly.http.HttpHeader;
import org.glassfish.grizzly.http.HttpPacket;
import org.glassfish.grizzly.http.HttpResponsePacket;
import org.glassfish.grizzly.http.HttpServerFilter;
import org.glassfish.grizzly.http.util.DataChunk;
import org.glassfish.grizzly.memory.Buffers;
import org.glassfish.grizzly.memory.MemoryManager;
import org.glassfish.grizzly.utils.DataStructures;

/**
 * Filter is working as Codec between Ajp and Http packets.
 * In other words it's responsible for decoding Ajp message to HttpRequestPacket,
 * and encoding HttpResponsePacket to Ajp message back.
 *
 * @author Alexey Stashok
 */
public class AjpHandlerFilter extends BaseFilter {
    private static final Logger LOGGER =
            Grizzly.logger(AjpHandlerFilter.class);

    private final Attribute<AjpHttpRequest> httpRequestInProcessAttr =
            Grizzly.DEFAULT_ATTRIBUTE_BUILDER.createAttribute(
            HttpServerFilter.HTTP_SERVER_REQUEST_ATTR_NAME);

    private String secret;
    private boolean isTomcatAuthentication = true;

    private final Buffer NEED_MORE_DATA_MESSAGE = Buffers.cloneBuffer(
            Buffers.EMPTY_BUFFER);

    private final Queue<ShutdownHandler> shutdownHandlers =
            DataStructures.getLTQInstance(ShutdownHandler.class);

    /**
     * Configure Ajp Filter using properties.
     * We support following properties: request.useSecret, request.secret, tomcatAuthentication.
     *
     * @param properties
     */
    @SuppressWarnings("UnusedDeclaration")
    public void configure(final Properties properties) {
        if (Boolean.parseBoolean(properties.getProperty("request.useSecret"))) {
            secret = Double.toString(new SecureRandom().nextDouble());
        }

        secret = properties.getProperty("request.secret", secret);
        isTomcatAuthentication =
                Boolean.parseBoolean(properties.getProperty(
                "tomcatAuthentication", "true"));
    }

    /**
     * If set to true, the authentication will be done in Grizzly.
     * Otherwise, the authenticated principal will be propagated from the
     * native webserver and used for authorization in Grizzly.
     * The default value is true.
     *
     * @return true, if the authentication will be done in Grizzly.
     * Otherwise, the authenticated principal will be propagated from the
     * native webserver and used for authorization in Grizzly.
     */
    @SuppressWarnings("UnusedDeclaration")
    public boolean isTomcatAuthentication() {
        return isTomcatAuthentication;
    }

    /**
    /**
     * If set to true, the authentication will be done in Grizzly.
     * Otherwise, the authenticated principal will be propagated from the
     * native webserver and used for authorization in Grizzly.
     * The default value is true.
     *
     * @param isTomcatAuthentication if true, the authentication will be done in Grizzly.
     * Otherwise, the authenticated principal will be propagated from the
     * native webserver and used for authorization in Grizzly.
     */
    public void setTomcatAuthentication(boolean isTomcatAuthentication) {
        this.isTomcatAuthentication = isTomcatAuthentication;
    }

    /**
     * If not null, only requests from workers with this secret keyword will
     * be accepted.
     *
     * @return not null, if only requests from workers with this secret keyword will
     * be accepted, or null otherwise.
     */
    @SuppressWarnings("UnusedDeclaration")
    public String getSecret() {
        return secret;
    }

    /**
     * If not null, only requests from workers with this secret keyword will
     * be accepted.
     *
     * @param requiredSecret if not null, only requests from workers with this
     * secret keyword will be accepted.
     */
    public void setSecret(String requiredSecret) {
        this.secret = requiredSecret;
    }

    /**
     * Add the {@link ShutdownHandler}, which will be called, when shutdown
     * request received.
     *
     * @param handler {@link ShutdownHandler}
     */
    public void addShutdownHandler(final ShutdownHandler handler) {
        shutdownHandlers.add(handler);
    }

    /**
     * Remove the {@link ShutdownHandler}.
     *
     * @param handler {@link ShutdownHandler}
     */
    @SuppressWarnings("UnusedDeclaration")
    public void removeShutdownHandler(final ShutdownHandler handler) {
        shutdownHandlers.remove(handler);
    }

    /**
     * Handle the Ajp message.
     *
     * @param ctx the {@link FilterChainContext} for the current
     *  {@link org.glassfish.grizzly.filterchain.FilterChain} invocation.
     * @return the {@link NextAction}
     * @throws IOException if an I/O error occurs
     */
    @Override
    public NextAction handleRead(final FilterChainContext ctx) throws IOException {
        final Buffer message = ctx.getMessage();

        if (message == NEED_MORE_DATA_MESSAGE) {
            // Upper layer tries to read additional data
            // We need to send request to a server to obtain another data chunk.
            sendMoreDataRequestIfNeeded(ctx);
            return ctx.getStopAction();
        }

        final int type = extractType(ctx, message);

        switch (type) {
            case AjpConstants.JK_AJP13_FORWARD_REQUEST:
            {
                return processForwardRequest(ctx, message);
            }
            case AjpConstants.JK_AJP13_DATA:
            {
                return processData(ctx, message);
            }

            case AjpConstants.JK_AJP13_SHUTDOWN:
            {
                return processShutdown(ctx, message);
            }

            case AjpConstants.JK_AJP13_CPING_REQUEST:
            {
                return processCPing(ctx, message);
            }

            default:
            {
                throw new IllegalStateException("Unknown message " + type);
            }

        }
    }

    /**
     * Encoding HttpResponsePacket or HttpContent to Ajp message.
     *
     * @param ctx the {@link FilterChainContext} for the current
     *  {@link org.glassfish.grizzly.filterchain.FilterChain} invocation.
     * @return the {@link NextAction}
     * @throws IOException if an I/O error occurs
     */
    @Override
    public NextAction handleWrite(final FilterChainContext ctx) throws IOException {
        final Connection connection = ctx.getConnection();
        final HttpPacket httpPacket = ctx.getMessage();

        final Buffer encodedPacket = encodeHttpPacket(connection, httpPacket);
        ctx.setMessage(encodedPacket);

        return ctx.getInvokeAction();
    }

    private Buffer encodeHttpPacket(final Connection connection,
            final HttpPacket httpPacket) {

        final MemoryManager memoryManager = connection.getTransport().getMemoryManager();
        final boolean isHeader = httpPacket.isHeader();
        final HttpHeader httpHeader = isHeader ? (HttpHeader) httpPacket :
            httpPacket.getHttpHeader();
        final HttpResponsePacket httpResponsePacket = (HttpResponsePacket) httpHeader;
        Buffer encodedBuffer = null;
        if (!httpHeader.isCommitted()) {
            encodedBuffer = AjpMessageUtils.encodeHeaders(memoryManager, httpResponsePacket);
            if (httpResponsePacket.isAcknowledgement()) {
                encodedBuffer.trim();

                httpResponsePacket.acknowledged();
                return encodedBuffer; // DO NOT MARK COMMITTED
            }

            httpHeader.setCommitted(true);
        }

        if (!isHeader) {
            final HttpContent httpContentPacket = (HttpContent) httpPacket;
            final Buffer contentBuffer = httpContentPacket.getContent();
            if (contentBuffer.hasRemaining()) {
                return AjpMessageUtils.appendContentAndTrim(memoryManager,
                        encodedBuffer, contentBuffer);
            }
        }

        assert encodedBuffer != null;
        encodedBuffer.trim();
        return encodedBuffer;
    }

    /**
     * Handling Http request completion event sent by Http server filter and
     * send the Ajp end response message.
     *
     * @param ctx the {@link FilterChainContext} for the current
     *  {@link org.glassfish.grizzly.filterchain.FilterChain} invocation.
     * @param event the event triggering the invocation of this method.
     * @return the {@link NextAction}.
     * @throws IOException if an I/O error occurs.
     */
    @Override
    public NextAction handleEvent(final FilterChainContext ctx,
            final FilterChainEvent event) throws IOException {

        final Connection c = ctx.getConnection();

        if (event.type() == HttpServerFilter.RESPONSE_COMPLETE_EVENT.type()
                && c.isOpen()) {
            final HttpContext context = HttpContext.get(ctx);
            if ((httpRequestInProcessAttr.remove(context)) != null) {
                sendEndResponse(ctx);
            }

        }

        return ctx.getStopAction();
    }

    private NextAction processData(final FilterChainContext ctx,
            final Buffer messageContent) {

        final AjpHttpRequest httpRequestPacket = httpRequestInProcessAttr.get(
                ctx.getConnection());
        httpRequestPacket.getProcessingState().getHttpContext().attach(ctx);

        if (messageContent.hasRemaining()) {
            // Skip the content length field - we know the size from the packet header
            messageContent.position(messageContent.position() + 2);
        }

        // Figure out if the content is last
        if (httpRequestPacket.isExpectContent()) {
            if (!messageContent.hasRemaining()) {
                // if zero-length content came
                httpRequestPacket.setExpectContent(false);
            } else {
                int contentBytesRemaining = httpRequestPacket.getContentBytesRemaining();
                // if we know the content-length
                if (contentBytesRemaining > 0) {
                    contentBytesRemaining -= messageContent.remaining();
                    httpRequestPacket.setContentBytesRemaining(contentBytesRemaining);
                    // do we have more content remaining?
                    if (contentBytesRemaining <= 0) {
                        httpRequestPacket.setExpectContent(false);
                    }
                }
            }
        }

        final HttpContent content = HttpContent.builder(httpRequestPacket)
                .content(messageContent)
                .last(!httpRequestPacket.isExpectContent())
                .build();

        ctx.setMessage(content);

        // If we may expect more data - do the following trick:
        // set NEED_MORE_DATA_MESSAGE as remainder, so when more data will be requested
        // this filter will be invoked. This way we'll be able to send a request
        // for more data to web server.
        // See handleRead() and sendMoreDataRequestIfNeeded() methods
        return ctx.getInvokeAction(httpRequestPacket.isExpectContent() ?
            NEED_MORE_DATA_MESSAGE : null);
    }

    /**
     * Process ForwardRequest request message.
     *
     * @param ctx the {@link FilterChainContext} for the current
     *  {@link org.glassfish.grizzly.filterchain.FilterChain} invocation.
     * @param content the content of the forwarded request
     *
     * @return {@link NextAction}
     * @throws IOException if an I/O error occurs
     */
    private NextAction processForwardRequest(final FilterChainContext ctx,
            final Buffer content) throws IOException {
        final Connection connection = ctx.getConnection();

        final AjpHttpRequest httpRequestPacket =
                AjpHttpRequest.create();
        final HttpContext httpContext = HttpContext.newInstance(connection,
                connection, connection, httpRequestPacket)
                .attach(ctx);
        
        httpRequestPacket.setConnection(connection);

        httpRequestPacket.getProcessingState().setHttpContext(httpContext);
        
        AjpMessageUtils.decodeRequest(content, httpRequestPacket,
                isTomcatAuthentication);

        if (secret != null) {
            final String epSecret = httpRequestPacket.getSecret();
            if (epSecret == null || !secret.equals(epSecret)) {
                throw new IllegalStateException("Secret doesn't match");
            }
        }
        httpRequestInProcessAttr.set(httpContext, httpRequestPacket);
        ctx.setMessage(HttpContent.builder(httpRequestPacket).build());

        final long contentLength = httpRequestPacket.getContentLength();
        if (contentLength > 0) {
            // if content-length > 0 - the first data chunk will come immediately,
            // so let's wait for it
            httpRequestPacket.setContentBytesRemaining((int) contentLength);
            httpRequestPacket.setExpectContent(true);
            return ctx.getStopAction();
        } else if (contentLength < 0) {
            // We don't know if there is any content in the message, but we're
            // sure no message is following immediately
            httpRequestPacket.setExpectContent(true);
            return ctx.getInvokeAction(NEED_MORE_DATA_MESSAGE);
        } else {
            // content-length == 0 - no content is expected
            httpRequestPacket.setExpectContent(false);
            return ctx.getInvokeAction();
        }
    }

    /**
     * Process CPing request message.
     * We send CPong response back as plain Grizzly {@link Buffer}.
     *
     * @param ctx
     * @param message
     * @return
     * @throws IOException
     */
    private NextAction processCPing(final FilterChainContext ctx,
            final Buffer message) throws IOException {

        message.clear();

        message.put((byte) 'A');
        message.put((byte) 'B');
        message.putShort((short) 1);
        message.put(AjpConstants.JK_AJP13_CPONG_REPLY);
        message.flip();

        // Write the buffer
        ctx.write(message);

        // Notify about response complete event
        ctx.notifyDownstream(HttpServerFilter.RESPONSE_COMPLETE_EVENT);

        return ctx.getStopAction();
    }

    /**
     * Process Shutdown request message.
     * For now just ignore it.
     *
     * @param ctx
     * @param message
     * @return
     * @throws IOException
     */
    private NextAction processShutdown(final FilterChainContext ctx,
            final Buffer message) {

        String shutdownSecret = null;

        if (message.remaining() > 2) {
            // Secret is available
            int offset = message.position();

            final DataChunk tmpDataChunk = DataChunk.newInstance();
            AjpMessageUtils.getBytesToDataChunk(message, offset, tmpDataChunk);
            
            shutdownSecret = tmpDataChunk.toString();
        }

        if (secret != null &&
                !secret.equals(shutdownSecret)) {
            throw new IllegalStateException("Secret doesn't match, no shutdown");
        }

        final Connection connection = ctx.getConnection();

        for (ShutdownHandler handler : shutdownHandlers) {
            try {
                handler.onShutdown(connection);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING,
                        "Exception during ShutdownHandler execution", e);
            }
        }

        return ctx.getStopAction();
    }

    private void sendMoreDataRequestIfNeeded(final FilterChainContext ctx)
            throws IOException {

        final Connection connection = ctx.getConnection();
        final HttpContext context = HttpContext.get(ctx);
        // Check if message is still in process
        if (httpRequestInProcessAttr.isSet(context)) {
            final MemoryManager mm = connection.getTransport().getMemoryManager();
            final Buffer buffer = mm.allocate(7);

            buffer.put((byte) 'A');
            buffer.put((byte) 'B');
            buffer.putShort((short) 3);
            buffer.put(AjpConstants.JK_AJP13_GET_BODY_CHUNK);
            buffer.putShort((short) AjpConstants.SUGGESTED_MAX_PAYLOAD_SIZE);

            buffer.flip();
            buffer.allowBufferDispose(true);

            ctx.write(buffer);
        }
    }

    private void sendEndResponse(final FilterChainContext ctx) throws IOException {
        final Connection connection = ctx.getConnection();

        final MemoryManager mm = connection.getTransport().getMemoryManager();
        final Buffer buffer = mm.allocate(6);

        buffer.put((byte) 'A');
        buffer.put((byte) 'B');
        buffer.putShort((short) 2);
        buffer.put(AjpConstants.JK_AJP13_END_RESPONSE);
        buffer.put((byte) 1);

        buffer.flip();
        buffer.allowBufferDispose(true);

        ctx.write(buffer);
    }

    private int extractType(final FilterChainContext ctx, final Buffer buffer) {
        return !httpRequestInProcessAttr.isSet(ctx.getConnection()) ?
                // if request is no in process - it should be a new Ajp message
                buffer.get() & 0xFF :
                // Ajp Data Packet
                AjpConstants.JK_AJP13_DATA;
    }
}
