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

package org.glassfish.grizzly.http;

import org.glassfish.grizzly.http.util.Constants;
import org.glassfish.grizzly.http.util.BufferChunk;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.EmptyCompletionHandler;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.WriteResult;
import org.glassfish.grizzly.attributes.Attribute;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.http.util.DataChunk;
import org.glassfish.grizzly.http.util.FastHttpDateFormat;
import org.glassfish.grizzly.http.util.HexUtils;
import org.glassfish.grizzly.http.util.HttpStatus;
import org.glassfish.grizzly.http.util.MimeHeaders;
import org.glassfish.grizzly.memory.MemoryManager;
import org.glassfish.grizzly.utils.DelayedExecutor;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;
import org.glassfish.grizzly.filterchain.FilterChainEvent;

/**
 * Server side {@link HttpCodecFilter} implementation, which is responsible for
 * decoding {@link HttpRequestPacket} and encoding {@link HttpResponsePacket} messages.
 *
 * This <tt>Filter</tt> is usually used, when we build an asynchronous HTTP server
 * connection.
 *
 * @see HttpCodecFilter
 * @see HttpClientFilter
 *
 * @author Alexey Stashok
 */
public class HttpServerFilter extends HttpCodecFilter {

    public static final FilterChainEvent RESPONSE_COMPLETE_EVENT =
            new FilterChainEvent() {
        @Override
        public Object type() {
            return "RESPONSE_COMPLETE_EVENT";
        }
    };

    private static final FlushAndCloseHandler FLUSH_AND_CLOSE_HANDLER =
            new FlushAndCloseHandler();

    private final Attribute<HttpRequestPacketImpl> httpRequestInProcessAttr;
    private final Attribute<KeepAliveContext> keepAliveContextAttr;

    private final DelayedExecutor.DelayQueue<KeepAliveContext> keepAliveQueue;

    private final KeepAlive keepAlive;

    private final boolean processKeepAlive;
    private boolean authPassthroughEnabled;
    private boolean traceEnabled;
    private String defaultResponseContentType;

    /**
     * Constructor, which creates <tt>HttpServerFilter</tt> instance
     */
    public HttpServerFilter() {
        this(true, DEFAULT_MAX_HTTP_PACKET_HEADER_SIZE, null, null);
    }

    /**
     * Constructor, which creates <tt>HttpServerFilter</tt> instance,
     * with the specific max header size parameter.
     *
     * @param chunkingEnabled flag indicating whether or not chunking should
     *  be allowed or not.
     * @param maxHeadersSize the maximum size of an inbound HTTP message header.
     * @param keepAlive keep-alive configuration for this filter instance.
     * @param executor {@link DelayedExecutor} for handling keep-alive.
     */
    public HttpServerFilter(boolean chunkingEnabled,
                            int maxHeadersSize,
                            KeepAlive keepAlive,
                            DelayedExecutor executor) {
        this(chunkingEnabled,
             maxHeadersSize,
             Constants.DEFAULT_RESPONSE_TYPE,
             keepAlive,
             executor);
    }


    /**
     * Constructor, which creates <tt>HttpServerFilter</tt> instance,
     * with the specific max header size parameter.
     *
     * @param chunkingEnabled flag indicating whether or not chunking should
     *  be allowed or not.
     * @param maxHeadersSize the maximum size of an inbound HTTP message header.
     * @param defaultResponseContentType the content type that the response should
     *  use if no content had been specified at the time the response is committed.
     * @param keepAlive keep-alive configuration for this filter instance.
     * @param executor {@link DelayedExecutor} for handling keep-alive.
     */
    public HttpServerFilter(boolean chunkingEnabled,
                            int maxHeadersSize,
                            String defaultResponseContentType,
                            KeepAlive keepAlive,
                            DelayedExecutor executor) {
        super(chunkingEnabled, maxHeadersSize);

        this.httpRequestInProcessAttr =
                Grizzly.DEFAULT_ATTRIBUTE_BUILDER.
                        createAttribute("HttpServerFilter.HttpRequest");
        this.keepAliveContextAttr = Grizzly.DEFAULT_ATTRIBUTE_BUILDER.
                createAttribute("HttpServerFilter.KeepAliveContext");

        if (executor != null) {
            keepAliveQueue = executor.createDelayQueue(new KeepAliveWorker(keepAlive),
                                                       new KeepAliveResolver());
            this.keepAlive = keepAlive;
            processKeepAlive = true;
        } else {
            keepAliveQueue = null;
            this.keepAlive = null;
            processKeepAlive = false;
        }
        this.defaultResponseContentType = defaultResponseContentType;

        final ContentEncoding enc = new GZipContentEncoding(
                GZipContentEncoding.DEFAULT_IN_BUFFER_SIZE,
                GZipContentEncoding.DEFAULT_OUT_BUFFER_SIZE);
        contentEncodings.add(enc);
    }

    // ----------------------------------------------------------- Configuration


    /**
     * The method is called, once we have received a {@link Buffer},
     * which has to be transformed into HTTP request packet part.
     *
     * Filter gets {@link Buffer}, which represents a part or complete HTTP
     * request message. As the result of "read" transformation - we will get
     * {@link HttpContent} message, which will represent HTTP request packet
     * content (might be zero length content) and reference
     * to a {@link HttpHeader}, which contains HTTP request message header.
     *
     * @param ctx Request processing context
     *
     * @return {@link NextAction}
     * @throws IOException
     */
    @Override
    public NextAction handleRead(final FilterChainContext ctx) throws IOException {
        final Buffer input = (Buffer) ctx.getMessage();
        final Connection connection = ctx.getConnection();
        
        HttpRequestPacketImpl httpRequest = httpRequestInProcessAttr.get(connection);
        if (httpRequest == null) {
            final boolean isSecureLocal = isSecure(connection);
            httpRequest = HttpRequestPacketImpl.create();
            httpRequest.initialize(connection, input.position(), maxHeadersSize);
            httpRequest.setSecure(isSecureLocal);
            final HttpResponsePacketImpl response = HttpResponsePacketImpl.create();
            response.setUpgrade(httpRequest.getUpgrade());
            response.setSecure(isSecureLocal);
            httpRequest.setResponse(response);
            response.setRequest(httpRequest);
            if (processKeepAlive) {
                KeepAliveContext keepAliveContext = keepAliveContextAttr.get(connection);
                if (keepAliveContext == null) {
                    keepAliveContext = new KeepAliveContext(connection);
                    keepAliveContextAttr.set(connection, keepAliveContext);
                }
                keepAliveContext.request = httpRequest;
                final int requestsProcessed = keepAliveContext.requestsProcessed;
                if (requestsProcessed > 0) {
                    KeepAlive.notifyProbesHit(keepAlive,
                                              connection,
                                              requestsProcessed);
                }
                keepAliveQueue.remove(keepAliveContext);
            }
            httpRequestInProcessAttr.set(connection, httpRequest);
        }

        return handleRead(ctx, httpRequest);
    }


    @Override
    public NextAction handleEvent(final FilterChainContext ctx,
            final FilterChainEvent event) throws IOException {

        final Connection c = ctx.getConnection();
        
        if (event == RESPONSE_COMPLETE_EVENT && c.isOpen()) {

            if (processKeepAlive) {
                final KeepAliveContext keepAliveContext =
                        keepAliveContextAttr.get(c);
                keepAliveQueue.add(keepAliveContext,
                        keepAlive.getIdleTimeoutInSeconds(),
                        TimeUnit.SECONDS);
                final HttpRequestPacket httpRequest = keepAliveContext.request;
                final boolean isStayAlive = isKeepAlive(httpRequest, keepAliveContext);
                if (!isStayAlive) {
                    ctx.flush(FLUSH_AND_CLOSE_HANDLER);
                } else {
                    if (httpRequest.isExpectContent()) {
                        // If transfer encoding is defined and we can determine the message body length
                        if (httpRequest.getTransferEncoding() != null) {
                            httpRequest.setSkipRemainder(true);
                        } else {
                            // if we can not determine the message body length - assume this packet as processed
                            httpRequest.setExpectContent(false);
                            onHttpPacketParsed(httpRequest, ctx);
                        }
                    }
                }
                keepAliveContext.request = null;
            } else {
                ctx.flush(FLUSH_AND_CLOSE_HANDLER);
            }

            return ctx.getStopAction();
        }

        return ctx.getInvokeAction();
        
    }


    @Override
    boolean onHttpHeaderParsed(final HttpHeader httpHeader, final Buffer buffer,
            final FilterChainContext ctx) {

        final HttpRequestPacketImpl request = (HttpRequestPacketImpl) httpHeader;

        // If it's upgraded HTTP - don't check semantics
        if (!request.getUpgradeDC().isNull()) return false;

        
        prepareRequest(request, buffer.hasRemaining());
        return request.getProcessingState().error;
    }

    @Override
    final boolean onHttpPacketParsed(final HttpHeader httpHeader,
            final FilterChainContext ctx) {
        final HttpRequestPacketImpl request = (HttpRequestPacketImpl) httpHeader;

        final boolean error = request.getProcessingState().error;
        if (!error) {
            httpRequestInProcessAttr.remove(ctx.getConnection());
        }
        return error;
    }

    @Override
    protected void onHttpError(final HttpHeader httpHeader,
            final FilterChainContext ctx) throws IOException {

        final HttpRequestPacketImpl request = (HttpRequestPacketImpl) httpHeader;
        final HttpResponsePacket response = request.getResponse();

        // If error response status is not set - use 400
        if (response.getHttpStatus().getStatusCode() < 400) {
            // 400 - Bad request
            HttpStatus.BAD_REQUEST_400.setValues(response);
        }

        // commit the response
        final HttpContent errorHttpResponse = customizeErrorResponse(response);
        final Buffer resBuf = encodeHttpPacket(ctx.getConnection(), errorHttpResponse);
        ctx.write(resBuf);
        ctx.flush(FLUSH_AND_CLOSE_HANDLER);
    }

    @Override
    protected Buffer encodeHttpPacket(final Connection connection,
            final HttpPacket input) {
        final HttpHeader header;
        if (input.isHeader()) {
            header = (HttpHeader) input;
        } else {
            header = ((HttpContent) input).getHttpHeader();
        }
        final HttpResponsePacketImpl response = (HttpResponsePacketImpl) header;
        if (!response.isCommitted() && response.getUpgrade() == null) {
            prepareResponse(response.getRequest(), response);
        }

        final Buffer encoded = super.encodeHttpPacket(connection, input);
        if (HttpContent.isContent(input)) {
            input.recycle();
        }
        return encoded;
    }

    @Override
    final boolean decodeInitialLine(HttpPacketParsing httpPacket,
            HeaderParsingState parsingState, Buffer input) {

        final HttpRequestPacketImpl httpRequest = (HttpRequestPacketImpl) httpPacket;

        final int reqLimit = parsingState.packetLimit;

        //noinspection LoopStatementThatDoesntLoop
        while(true) {
            int subState = parsingState.subState;

            switch(subState) {
                case 0 : { // parse the method name
                    final int spaceIdx =
                            findSpace(input, parsingState.offset, reqLimit);
                    if (spaceIdx == -1) {
                        parsingState.offset = input.limit();
                        return false;
                    }

                    httpRequest.getMethodDC().setBuffer(input,
                            parsingState.start, spaceIdx);

                    parsingState.start = -1;
                    parsingState.offset = spaceIdx;

                    parsingState.subState++;
                }

                case 1: { // skip spaces after the method name
                    final int nonSpaceIdx =
                            skipSpaces(input, parsingState.offset, reqLimit);
                    if (nonSpaceIdx == -1) {
                        parsingState.offset = input.limit();
                        return false;
                    }

                    parsingState.start = nonSpaceIdx;
                    parsingState.offset = nonSpaceIdx + 1;
                    parsingState.subState++;
                }

                case 2: { // parse the requestURI
                    if (!parseRequestURI(httpRequest, parsingState, input)) {
                        return false;
                    }
                }

                case 3: { // skip spaces after requestURI
                    final int nonSpaceIdx =
                            skipSpaces(input, parsingState.offset, reqLimit);
                    if (nonSpaceIdx == -1) {
                        parsingState.offset = input.limit();
                        return false;
                    }

                    parsingState.start = nonSpaceIdx;
                    parsingState.offset = nonSpaceIdx;
                    parsingState.subState++;
                }

                case 4: { // HTTP protocol
                    if (!findEOL(parsingState, input)) {
                        parsingState.offset = input.limit();
                        return false;
                    }

                    if (parsingState.checkpoint > parsingState.start) {
                        httpRequest.getProtocolDC().setBuffer(
                                input, parsingState.start,
                                parsingState.checkpoint);
                    } else {
                        httpRequest.getProtocolDC().setString("");
                    }

                    parsingState.subState = 0;
                    parsingState.start = -1;
                    parsingState.checkpoint = -1;

                    return true;
                }

                default: throw new IllegalStateException();
            }
        }
    }

    @Override
    Buffer encodeInitialLine(HttpPacket httpPacket, Buffer output, MemoryManager memoryManager) {
        final HttpResponsePacket httpResponse = (HttpResponsePacket) httpPacket;
        output = put(memoryManager, output, httpResponse.getProtocol().getProtocolBytes());
        output = put(memoryManager, output, Constants.SP);
        output = put(memoryManager, output, httpResponse.getHttpStatus().getStatusBytes());
        output = put(memoryManager, output, Constants.SP);
        output = put(memoryManager, output, httpResponse.getReasonPhraseDC());

        return output;
    }

    private static boolean parseRequestURI(HttpRequestPacketImpl httpRequest,
            HeaderParsingState state, Buffer input) {
        
        final int limit = Math.min(input.limit(), state.packetLimit);

        int offset = state.offset;

        boolean found = false;

        while(offset < limit) {
            final byte b = input.get(offset);
            if (b == Constants.SP || b == Constants.HT) {
                found = true;
                break;
            } else if ((b == Constants.CR)
                       || (b == Constants.LF)) {
                // HTTP/0.9 style request
                found = true;
                break;
            } else if ((b == Constants.QUESTION)
                       && (state.checkpoint == -1)) {
                state.checkpoint = offset;
            }

            offset++;
        }

        if (found) {
            final DataChunk requestURIBC = httpRequest.getRequestURIRef().getRequestURIBC();
            requestURIBC.setBuffer(input, state.start, offset);
            if (state.checkpoint != -1) {
                // cut RequestURI to not include query string
                requestURIBC.getBufferChunk().setEnd(state.checkpoint);

                httpRequest.getQueryStringDC().setBuffer(input,
                        state.checkpoint + 1, offset);
            }

            state.start = -1;
            state.checkpoint = -1;
            state.subState++;
        }

        state.offset = offset;
        return found;
    }


    private void prepareResponse(final HttpRequestPacket request,
                                 final HttpResponsePacketImpl response) {
        final Protocol protocol = request.getProtocol();

        response.setProtocol(protocol);
        final ProcessingState state = response.getProcessingState();

        if (protocol == Protocol.HTTP_0_9) {
            return;
        }

        boolean entityBody = true;
        final int statusCode = response.getStatus();

        if ((statusCode == 204) || (statusCode == 205)
                || (statusCode == 304)) {
            // No entity body
            entityBody = false;
            response.setExpectContent(false);
            state.contentDelimitation = true;
        }

        final boolean isHttp11 = protocol == Protocol.HTTP_1_1;
        final MimeHeaders headers = response.getHeaders();

        final long contentLength = response.getContentLength();
        if (contentLength != -1L) {
            state.contentDelimitation = true;
        } else {
            if (chunkingEnabled && entityBody && isHttp11) {
                state.contentDelimitation = true;
                response.setChunked(true);
            }
        }

        final Method method = request.getMethod();
        if (Method.HEAD.equals(method)) {
            // No entity body
            state.contentDelimitation = true;
        }

        if (!entityBody) {
            response.setContentLength(-1);
        } else {
            String contentLanguage = response.getContentLanguage();
            if (contentLanguage != null) {
                headers.setValue("Content-Language").setString(contentLanguage);
            }
            if (response.getContentType() == null) {
                if (defaultResponseContentType != null) {
                    response.setContentType(defaultResponseContentType);
                }
            }
        }

        if (!response.containsHeader("Date")) {
            final String date = FastHttpDateFormat.getCurrentDate();
            response.addHeader("Date", date);
        }

        if ((entityBody) && (!state.contentDelimitation)) {
            // Mark as close the connection after the request, and add the
            // connection: close header
            state.keepAlive = false;
        } else if (entityBody && !isHttp11 && response.getContentLength() == -1) {
            // HTTP 1.0 response with no content-length having been set.
            // Close the connection to signal the response as being complete.
            state.keepAlive = false;
        } else if (entityBody && !response.isChunked() && response.getContentLength() == -1) {
            // HTTP 1.1 response with chunking disabled and no content-length having been set.
            // Close the connection to signal the response as being complete.
            state.keepAlive = false;
        } else if (!checkKeepAliveRequestsCount(request.getConnection())) {
            // We processed max allowed HTTP requests over the keep alive connection
            state.keepAlive = false;
        }

        // If we know that the request is bad this early, add the
        // Connection: close header.
        state.keepAlive = (state.keepAlive &&
                !statusDropsConnection(response.getStatus()));

        if (!state.keepAlive) {
            headers.setValue("Connection").setString("close");
        } else if (!isHttp11 && !state.error) {
            headers.setValue("Connection").setString("Keep-Alive");
        }

    }
    

    private static void prepareRequest(final HttpRequestPacketImpl request,
            final boolean hasReadyContent) {

        final ProcessingState state = request.getProcessingState();
        final HttpResponsePacket response = request.getResponse();

        final Method method = request.getMethod();

        if (Method.GET.equals(method)) {
            request.setExpectContent(false);
        }

        Protocol protocol;
        try {
            protocol = request.getProtocol();
        } catch (IllegalStateException e) {
            state.error = true;
            // Send 505; Unsupported HTTP version
            HttpStatus.HTTP_VERSION_NOT_SUPPORTED_505.setValues(response);
            protocol = Protocol.HTTP_1_1;
            request.setProtocol(protocol);
            
            return;
        }

        final MimeHeaders headers = request.getHeaders();

        DataChunk hostDC = null;
        
        // Check for a full URI (including protocol://host:port/)
        // Check for a full URI (including protocol://host:port/)
        final BufferChunk uriBC =
                request.getRequestURIRef().getRequestURIBC().getBufferChunk();
        if (uriBC.startsWithIgnoreCase("http", 0)) {

            int pos = uriBC.indexOf("://", 4);
            int uriBCStart = uriBC.getStart();
            int slashPos;
            if (pos != -1) {
                final Buffer uriB = uriBC.getBuffer();
                slashPos = uriBC.indexOf('/', pos + 3);
                if (slashPos == -1) {
                    slashPos = uriBC.getLength();
                    // Set URI as "/"
                    uriBC.setBufferChunk(uriB, uriBCStart + pos + 1, 1);
                } else {
                    uriBC.setBufferChunk(uriB,
                                    uriBCStart + slashPos,
                                    uriBC.getLength() - slashPos);
                }
                hostDC = headers.setValue("host");
                hostDC.setBuffer(uriB,
                                 uriBCStart + pos + 3,
                                 slashPos - pos - 3);
            }

        }

        final boolean isHttp11 = protocol == Protocol.HTTP_1_1;

        // ------ Set keep-alive flag
        final DataChunk connectionValueDC = headers.getValue("connection");
        final boolean isConnectionClose = (connectionValueDC != null &&
                connectionValueDC.getBufferChunk().findBytesAscii(Constants.CLOSE_BYTES) != -1);

        if (!isConnectionClose) {
            state.keepAlive = isHttp11 ||
                    (connectionValueDC != null &&
                    connectionValueDC.getBufferChunk().findBytesAscii(Constants.KEEPALIVE_BYTES) != -1);
        }
        // --------------------------

        final long contentLength = request.getContentLength();
        if (contentLength >= 0) {
            state.contentDelimitation = true;
        }

        if (hostDC == null) {
            hostDC = headers.getValue("host");
        }

        // Check host header
        if (hostDC == null && isHttp11) {
            state.error = true;
            return;
        }

        parseHost(hostDC, request, response, state);

        if (!state.contentDelimitation) {
            // If there's no content length
            // (broken HTTP/1.0 or HTTP/1.1), assume
            // the client is not broken and didn't send a body
            state.contentDelimitation = true;
        }

        if (request.requiresAcknowledgement()) {
            // if we have any request content, we can ignore the Expect
            // request
            request.requiresAcknowledgement(isHttp11 && !hasReadyContent);
        }
    }

    protected HttpContent customizeErrorResponse(
            final HttpResponsePacket response) {
        
        response.setContentLength(0);
        return HttpContent.builder(response).last(true).build();
    }

    /**
     * Determine if we must drop the connection because of the HTTP status
     * code.  Use the same list of codes as Apache/httpd.
     */
    private static boolean statusDropsConnection(int status) {
        return status == 400 /* SC_BAD_REQUEST */ ||
               status == 408 /* SC_REQUEST_TIMEOUT */ ||
               status == 411 /* SC_LENGTH_REQUIRED */ ||
               status == 413 /* SC_REQUEST_ENTITY_TOO_LARGE */ ||
               status == 414 /* SC_REQUEST_URI_TOO_LARGE */ ||
               status == 417 /* FAILED EXPECTATION */ || 
               status == 500 /* SC_INTERNAL_SERVER_ERROR */ ||
               status == 503 /* SC_SERVICE_UNAVAILABLE */ ||
               status == 501 /* SC_NOT_IMPLEMENTED */ ||
               status == 505 /* SC_VERSION_NOT_SUPPORTED */;
    }


    private static void parseHost(final DataChunk hostDC,
                                  final HttpRequestPacket request,
                                  final HttpResponsePacket response,
                                  final ProcessingState state) {

        if (hostDC == null) {
            // HTTP/1.0
            // Default is what the socket tells us. Overridden if a host is
            // found/parsed
            final Connection connection = request.getConnection();
            request.setServerPort(((InetSocketAddress) connection.getLocalAddress()).getPort());
            final InetAddress localAddress = ((InetSocketAddress) connection.getLocalAddress()).getAddress();
            // Setting the socket-related fields. The adapter doesn't know
            // about socket.
            request.setLocalHost(localAddress.getHostName());
            request.serverName().setString(localAddress.getHostName());
            return;
        }

        final BufferChunk valueBC = hostDC.getBufferChunk();
        final int valueS = valueBC.getStart();
        final int valueL = valueBC.getEnd() - valueS;
        int colonPos = -1;

        final Buffer valueB = valueBC.getBuffer();
        final boolean ipv6 = (valueB.get(valueS) == '[');
        boolean bracketClosed = false;
        for (int i = 0; i < valueL; i++) {
            final byte b = valueB.get(i + valueS);
            if (b == ']') {
                bracketClosed = true;
            } else if (b == ':') {
                if (!ipv6 || bracketClosed) {
                    colonPos = i;
                    break;
                }
            }
        }

        if (colonPos < 0) {
            if (!request.isSecure()) {
                // 80 - Default HTTTP port
                request.setServerPort(80);
            } else {
                // 443 - Default HTTPS port
                request.setServerPort(443);
            }
            request.serverName().setBuffer(valueB, valueS, valueS + valueL);
        } else {
            request.serverName().setBuffer(valueB, valueS, valueS + colonPos);

            int port = 0;
            int mult = 1;
            for (int i = valueL - 1; i > colonPos; i--) {
                int charValue = HexUtils.DEC[(int) valueB.get(i + valueS)];
                if (charValue == -1) {
                    // Invalid character
                    state.error = true; 
                    // 400 - Bad request
                    HttpStatus.BAD_REQUEST_400.setValues(response);
                    return;
                }
                port = port + (charValue * mult);
                mult = 10 * mult;
            }
            request.setServerPort(port);

        }

    }


    private boolean isKeepAlive(final HttpRequestPacket request,
                                final KeepAliveContext keepAliveContext) {

        final ProcessingState ps = request.getProcessingState();
        boolean isKeepAlive = !ps.isError() && ps.isKeepAlive();

        if (isKeepAlive && keepAliveContext != null) {
            if (keepAliveContext.requestsProcessed == 1) {
                if (isKeepAlive) { // New keep-alive connection
                    KeepAlive.notifyProbesConnectionAccepted(keepAlive,
                            keepAliveContext.connection);
                } else { // Refused keep-alive connection
                    KeepAlive.notifyProbesRefused(keepAlive, keepAliveContext.connection);
                }
            }
        }

        return isKeepAlive;
    }

    private boolean checkKeepAliveRequestsCount(final Connection connection) {
        final KeepAliveContext keepAliveContext;
        
        return !(processKeepAlive && (keepAliveContext =
            keepAliveContextAttr.get(connection)) != null) ||
            ++keepAliveContext.requestsProcessed <= keepAlive.getMaxRequestsCount();

    }

    public boolean isAuthPassthroughEnabled() {
        return authPassthroughEnabled;
    }

    public void setAuthPassthroughEnabled(final boolean enabled) {
        authPassthroughEnabled = enabled;
    }

    public boolean isTraceEnabled() {
        return traceEnabled;
    }

    public void setTraceEnabled(final boolean enabled) {
        traceEnabled = enabled;
    }

    public String getDefaultResponseContentType() {
        return defaultResponseContentType;
    }

    public void setDefaultResponseContentType(String defaultResponseContentType) {
        this.defaultResponseContentType = defaultResponseContentType;
    }

    // ---------------------------------------------------------- Nested Classes


    private static class FlushAndCloseHandler extends EmptyCompletionHandler {

        @Override
        public void completed(Object result) {

            final WriteResult wr = (WriteResult) result;
            try {
                wr.getConnection().close().markForRecycle(false);
            } catch (IOException ignore) {
            } finally {
                wr.recycle();
            }

        }

    } // END FlushAndCloseHandler


     private static class KeepAliveContext {
        private final Connection connection;

        public KeepAliveContext(Connection connection) {
            this.connection = connection;
        }

        private volatile long keepAliveTimeoutMillis = DelayedExecutor.UNSET_TIMEOUT;
        private int requestsProcessed;
        private HttpRequestPacket request;

    } // END KeepAliveContext


    private static class KeepAliveWorker implements DelayedExecutor.Worker<KeepAliveContext> {

        private final KeepAlive keepAlive;

        public KeepAliveWorker(final KeepAlive keepAlive) {
            this.keepAlive = keepAlive;
        }

        @Override
        public boolean doWork(final KeepAliveContext context) {
            try {
                KeepAlive.notifyProbesTimeout(keepAlive, context.connection);
                context.connection.close().markForRecycle(false);
            } catch (IOException ignored) {
            }

            return true;
        }

    } // END KeepAliveWorker


    private static class KeepAliveResolver implements
            DelayedExecutor.Resolver<KeepAliveContext> {

        @Override
        public boolean removeTimeout(KeepAliveContext context) {
            if (context.keepAliveTimeoutMillis != DelayedExecutor.UNSET_TIMEOUT) {
                context.keepAliveTimeoutMillis = DelayedExecutor.UNSET_TIMEOUT;
                return true;
            }

            return false;
        }

        @Override
        public Long getTimeoutMillis(KeepAliveContext element) {
            return element.keepAliveTimeoutMillis;
        }

        @Override
        public void setTimeoutMillis(KeepAliveContext element, long timeoutMillis) {
            element.keepAliveTimeoutMillis = timeoutMillis;
        }

    } // END KeepAliveResolver

}
