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

package org.glassfish.grizzly.http;

import org.glassfish.grizzly.http.util.ContentType;
import org.glassfish.grizzly.filterchain.FilterChainEvent;
import org.glassfish.grizzly.http.util.Constants;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.attributes.Attribute;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.http.util.DataChunk;
import org.glassfish.grizzly.http.util.FastHttpDateFormat;
import org.glassfish.grizzly.http.util.Header;
import org.glassfish.grizzly.http.util.HttpStatus;
import org.glassfish.grizzly.http.util.MimeHeaders;
import org.glassfish.grizzly.memory.MemoryManager;
import org.glassfish.grizzly.utils.DelayedExecutor;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.glassfish.grizzly.ThreadCache;

import static org.glassfish.grizzly.http.Method.PayloadExpectation;
import static org.glassfish.grizzly.http.util.HttpCodecUtils.*;
import org.glassfish.grizzly.http.util.HttpUtils;

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
    public static final String HTTP_SERVER_REQUEST_ATTR_NAME =
            HttpServerFilter.class.getName() + ".HttpRequest";

    public static final FilterChainEvent RESPONSE_COMPLETE_EVENT =
            new HttpEvents.ResponseCompleteEvent();

    
    private final Attribute<ServerHttpRequestImpl> httpRequestInProcessAttr;
    private final Attribute<KeepAliveContext> keepAliveContextAttr;

    private final DelayedExecutor.DelayQueue<KeepAliveContext> keepAliveQueue;

    private final KeepAlive keepAlive;

    private String defaultResponseContentType;
    private byte[] defaultResponseContentTypeBytes;
    private byte[] defaultResponseContentTypeBytesNoCharset;
    
    private final boolean allowKeepAlive;
    private final int maxRequestHeaders;
    private final int maxResponseHeaders;
    
    // flag, which enables/disables payload support for HTTP methods,
    // for which HTTP spec doesn't clearly state whether they support payload.
    // Known "undefined" methods are: GET, HEAD, DELETE
    private boolean allowPayloadForUndefinedHttpMethods;
    
    /**
     * Constructor, which creates <tt>HttpServerFilter</tt> instance
     *
     * @deprecated Next major release will include builders for filters requiring configuration.  Constructors will be hidden.
     */
    @Deprecated
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
     *
     * @deprecated Next major release will include builders for filters requiring configuration.  Constructors will be hidden.
     */
    @Deprecated
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
     * @param executor {@link DelayedExecutor} for handling keep-alive. If <tt>null</tt> -
     *  keep-alive idle connections should be managed outside HttpServerFilter.
     *
     * @deprecated Next major release will include builders for filters requiring configuration.  Constructors will be hidden.
     */
    @Deprecated
    public HttpServerFilter(boolean chunkingEnabled,
                            int maxHeadersSize,
                            String defaultResponseContentType,
                            KeepAlive keepAlive,
                            DelayedExecutor executor) {
        this(chunkingEnabled,
             maxHeadersSize,
             defaultResponseContentType,
             keepAlive,
             executor,
             MimeHeaders.MAX_NUM_HEADERS_DEFAULT,
             MimeHeaders.MAX_NUM_HEADERS_DEFAULT);
    }

    /**
     * Constructor, which creates <tt>HttpServerFilter</tt> instance,
     * with the specific max header size parameter.
     *
     * @param chunkingEnabled            flag indicating whether or not chunking should
     *                                   be allowed or not.
     * @param maxHeadersSize             the maximum size of an inbound HTTP message header.
     * @param defaultResponseContentType the content type that the response should
     *                                   use if no content had been specified at the time the response is committed.
     * @param keepAlive                  keep-alive configuration for this filter instance.
     * @param executor                   {@link DelayedExecutor} for handling keep-alive. If <tt>null</tt> -
     *                                   keep-alive idle connections should be managed outside HttpServerFilter.
     * @param maxRequestHeaders          maximum number of request headers allowed for a single request.
     * @param maxResponseHeaders         maximum number of response headers allowed for a single response.
     *
     * @since 2.2.11
     *
     * @deprecated Next major release will include builders for filters requiring configuration.  Constructors will be hidden.
     */
    @Deprecated
    public HttpServerFilter(boolean chunkingEnabled,
                            int maxHeadersSize,
                            String defaultResponseContentType,
                            KeepAlive keepAlive,
                            DelayedExecutor executor,
                            int maxRequestHeaders,
                            int maxResponseHeaders) {
        super(chunkingEnabled, maxHeadersSize);

        this.httpRequestInProcessAttr =
                Grizzly.DEFAULT_ATTRIBUTE_BUILDER.
                        createAttribute(HTTP_SERVER_REQUEST_ATTR_NAME);
        this.keepAliveContextAttr = Grizzly.DEFAULT_ATTRIBUTE_BUILDER.
                createAttribute("HttpServerFilter.KeepAliveContext");

        keepAliveQueue = executor != null ?
                executor.createDelayQueue(
                        new KeepAliveWorker(keepAlive), new KeepAliveResolver()) :
                null;

        this.allowKeepAlive = keepAlive != null;
        this.keepAlive = allowKeepAlive ? new KeepAlive(keepAlive) : null;

        if (defaultResponseContentType != null && !defaultResponseContentType.isEmpty()) {
            setDefaultResponseContentType(defaultResponseContentType);
        }
        this.maxRequestHeaders = maxRequestHeaders;
        this.maxResponseHeaders = maxResponseHeaders;
    }
    
    // ----------------------------------------------------------- Configuration
    
    @SuppressWarnings("UnusedDeclaration")
    public String getDefaultResponseContentType() {
        return defaultResponseContentType;
    }

    public final void setDefaultResponseContentType(final String contentType) {
        this.defaultResponseContentType = contentType;
        if (contentType != null) {
            defaultResponseContentTypeBytes = toCheckedByteArray(contentType);
            defaultResponseContentTypeBytesNoCharset =
                    ContentType.removeCharset(defaultResponseContentTypeBytes);
        } else {
            defaultResponseContentTypeBytes =
                    defaultResponseContentTypeBytesNoCharset = null;
        }
    }

    /**
     * The flag, which enables/disables payload support for HTTP methods,
     * for which HTTP spec doesn't clearly state whether they support payload.
     * Known "undefined" methods are: GET, HEAD, DELETE.
     * 
     * @return <tt>true</tt> if "undefined" methods support payload, or <tt>false</tt> otherwise
     * @since 2.3.12
     */
    public boolean isAllowPayloadForUndefinedHttpMethods() {
        return allowPayloadForUndefinedHttpMethods;
    }

    /**
     * The flag, which enables/disables payload support for HTTP methods,
     * for which HTTP spec doesn't clearly state whether they support payload.
     * Known "undefined" methods are: GET, HEAD, DELETE.
     * 
     * @param allowPayloadForUndefinedHttpMethods <tt>true</tt> if "undefined" methods support payload, or <tt>false</tt> otherwise
     * @since 2.3.12
     */
    public void setAllowPayloadForUndefinedHttpMethods(boolean allowPayloadForUndefinedHttpMethods) {
        this.allowPayloadForUndefinedHttpMethods = allowPayloadForUndefinedHttpMethods;
    }

    
    // ----------------------------------------------------------- Parsing
    
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
        final Buffer input = ctx.getMessage();
        final Connection connection = ctx.getConnection();
        ServerHttpRequestImpl httpRequest = httpRequestInProcessAttr.get(connection);
        
        if (httpRequest == null) {
            final boolean isSecureLocal = isSecure(connection);
            httpRequest = ServerHttpRequestImpl.create();
            httpRequest.initialize(connection, this, input.position(), maxHeadersSize, maxRequestHeaders);
            httpRequest.setSecure(isSecureLocal);
            final HttpResponsePacket response = httpRequest.getResponse();
            response.setSecure(isSecureLocal);
            response.getHeaders().setMaxNumHeaders(maxResponseHeaders);
            httpRequest.setResponse(response);
            response.setRequest(httpRequest);
            
            final HttpContext httpContext = HttpContext.newInstance(
                    connection, connection, connection, httpRequest)
                    .attach(ctx);
            
            httpRequest.getProcessingState().setHttpContext(httpContext);

            if (allowKeepAlive) {
                KeepAliveContext keepAliveContext = keepAliveContextAttr.get(httpContext);
                if (keepAliveContext == null) {
                    keepAliveContext = new KeepAliveContext(connection);
                    keepAliveContextAttr.set(httpContext, keepAliveContext);
                } else if (keepAliveQueue != null) {
                    keepAliveQueue.remove(keepAliveContext);
                }
                
                final int requestsProcessed = keepAliveContext.requestsProcessed;
                if (requestsProcessed > 0) {
                    KeepAlive.notifyProbesHit(keepAlive,
                                              connection,
                                              requestsProcessed);
                }
                
                
            }
            httpRequestInProcessAttr.set(httpContext, httpRequest);
        } else if (httpRequest.isContentBroken()) {
            // if payload of the current/last HTTP request associated with the
            // Connection is broken - stop processing here
            return ctx.getStopAction();
        } else {
            httpRequest.getProcessingState().getHttpContext().attach(ctx);
        }

        return handleRead(ctx, httpRequest);
    }

    @Override
    final boolean decodeInitialLineFromBytes(final FilterChainContext ctx,
                                    final HttpPacketParsing httpPacket,
                                    final HeaderParsingState parsingState,
                                    final byte[] input,
                                    final int end) {

        final ServerHttpRequestImpl httpRequest = (ServerHttpRequestImpl) httpPacket;

        final int arrayOffs = parsingState.arrayOffset;
        final int reqLimit = arrayOffs + parsingState.packetLimit;

        //noinspection LoopStatementThatDoesntLoop
        while(true) {
            int subState = parsingState.subState;

            switch(subState) {
                case 0 : { // parse the method name
                    final int spaceIdx =
                            findSpace(input, arrayOffs + parsingState.offset, end, reqLimit);
                    if (spaceIdx == -1) {
                        parsingState.offset = end - arrayOffs;
                        return false;
                    }

                    httpRequest.getMethodDC().setBytes(input,
                            arrayOffs + parsingState.start,
                            spaceIdx);

                    parsingState.start = -1;
                    parsingState.offset = spaceIdx - arrayOffs;

                    parsingState.subState++;
                }

                case 1: { // skip spaces after the method name
                    final int nonSpaceIdx =
                            skipSpaces(input, arrayOffs + parsingState.offset,
                            end, reqLimit) - arrayOffs;
                    if (nonSpaceIdx < 0) {
                        parsingState.offset = end - arrayOffs;
                        return false;
                    }

                    parsingState.start = nonSpaceIdx;
                    parsingState.offset = nonSpaceIdx + 1;
                    parsingState.subState++;
                }

                case 2: { // parse the requestURI
                    if (!parseRequestURI(httpRequest, parsingState, input, end)) {
                        return false;
                    }
                }

                case 3: { // skip spaces after requestURI
                    final int nonSpaceIdx =
                            skipSpaces(input, arrayOffs + parsingState.offset, end, reqLimit) - arrayOffs;
                    if (nonSpaceIdx < 0) {
                        parsingState.offset = end - arrayOffs;
                        return false;
                    }

                    parsingState.start = nonSpaceIdx;
                    parsingState.offset = nonSpaceIdx;
                    parsingState.subState++;
                }

                case 4: { // HTTP protocol
                    if (!findEOL(parsingState, input, end)) {
                        parsingState.offset = end - arrayOffs;
                        return false;
                    }

                    if (parsingState.checkpoint > parsingState.start) {
                        httpRequest.getProtocolDC().setBytes(
                                input, arrayOffs + parsingState.start,
                                arrayOffs + parsingState.checkpoint);
                    } else {
                        httpRequest.getProtocolDC().setString("");
                    }

                    parsingState.subState = 0;
                    parsingState.start = -1;
                    parsingState.checkpoint = -1;
                    onInitialLineParsed(httpRequest, ctx);
                    return true;
                }

                default: throw new IllegalStateException();
            }
        }
    }
    
    private static boolean parseRequestURI(final ServerHttpRequestImpl httpRequest,
            final HeaderParsingState state, final byte[] input, final int end) {

        final int arrayOffs = state.arrayOffset;
        final int limit = Math.min(end, arrayOffs + state.packetLimit);

        int offset = arrayOffs + state.offset;

        boolean found = false;

        while (offset < limit) {
            final byte b = input[offset];
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
                state.checkpoint = offset - arrayOffs;
            }

            offset++;
        }

        if (found) {
            int requestURIEnd = offset;
            if (state.checkpoint != -1) {
                // cut RequestURI to not include query string
                requestURIEnd = arrayOffs + state.checkpoint;

                httpRequest.getQueryStringDC().setBytes(input,
                        requestURIEnd + 1, offset);
            }

            httpRequest.getRequestURIRef().init(input, arrayOffs + state.start, requestURIEnd);

            state.start = -1;
            state.checkpoint = -1;
            state.subState++;
        }

        state.offset = offset - arrayOffs;
        return found;
    }
    
    @Override
    final boolean decodeInitialLineFromBuffer(final FilterChainContext ctx,
                                    final HttpPacketParsing httpPacket,
                                    final HeaderParsingState parsingState,
                                    final Buffer input) {

        final ServerHttpRequestImpl httpRequest = (ServerHttpRequestImpl) httpPacket;

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
                    onInitialLineParsed(httpRequest, ctx);
                    return true;
                }

                default: throw new IllegalStateException();
            }
        }
    }
    
    private static boolean parseRequestURI(ServerHttpRequestImpl httpRequest,
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
            int requestURIEnd = offset;
            if (state.checkpoint != -1) {
                // cut RequestURI to not include query string
                requestURIEnd = state.checkpoint;

                httpRequest.getQueryStringDC().setBuffer(input,
                        state.checkpoint + 1, offset);
            }

            httpRequest.getRequestURIRef().init(input, state.start, requestURIEnd);

            state.start = -1;
            state.checkpoint = -1;
            state.subState++;
        }

        state.offset = offset;
        return found;
    }
    
    @Override
    protected boolean onHttpHeaderParsed(final HttpHeader httpHeader,
                                         final Buffer buffer,
                                         final FilterChainContext ctx) {

        final ServerHttpRequestImpl request = (ServerHttpRequestImpl) httpHeader;

        prepareRequest(request, buffer.hasRemaining());
        return request.getProcessingState().error;
    }

    private void prepareRequest(final ServerHttpRequestImpl request,
            final boolean hasReadyContent) {

        final ProcessingState state = request.getProcessingState();
        final HttpResponsePacket response = request.getResponse();

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

        // set the default chunking mode
        request.getResponse().setChunkingAllowed(
                !request.getUpgradeDC().isNull() || isChunkingEnabled());
        
        if (request.getHeaderParsingState().contentLengthsDiffer) {
            request.getProcessingState().error = true;
            return;
        }
        
        final MimeHeaders headers = request.getHeaders();
        
        DataChunk hostDC = null;
        
        // Check for a full URI (including protocol://host:port/)
        
        final DataChunk uriBC = request.getRequestURIRef().getRequestURIBC();
        
        if (uriBC.startsWithIgnoreCase("http", 0)) {

            int pos = uriBC.indexOf("://", 4);
            int uriBCStart = uriBC.getStart();
            int slashPos;
            if (pos != -1) {
//                final Buffer uriB = uriBC.getBuffer();
                slashPos = uriBC.indexOf('/', pos + 3);
                if (slashPos == -1) {
                    slashPos = uriBC.getLength();
                    // Set URI as "/"
                    uriBC.setStart(uriBCStart + pos + 1);
                    uriBC.setEnd(uriBCStart + pos + 2);
                } else {
                    uriBC.setStart(uriBCStart + slashPos);
                    uriBC.setEnd(uriBC.getEnd());
                }
                hostDC = headers.setValue(Header.Host);
                hostDC.set(uriBC, uriBCStart + pos + 3, uriBCStart + slashPos);
            }

        }

        // --------------------------

        if (hostDC == null) {
            hostDC = headers.getValue(Header.Host);
        }

        final boolean isHttp11 = protocol == Protocol.HTTP_1_1;
        
        // Check host header
        if (isHttp11 && (hostDC == null || hostDC.isNull())) {
            state.error = true;
            return;
        }
        request.unparsedHostC = hostDC;
        
        // Check if we have to ignore content modifiers like HTTP method,
        // Transfer and Content encoding
        if (request.isIgnoreContentModifiers()) {
            return;
        }

        final Method method = request.getMethod();
        
        final PayloadExpectation payloadExpectation = method.getPayloadExpectation();
        if (payloadExpectation != PayloadExpectation.NOT_ALLOWED) {
            final boolean hasPayload =
                    request.getContentLength() > 0 || request.isChunked();
            
            if (hasPayload && payloadExpectation == PayloadExpectation.UNDEFINED &&
                    !allowPayloadForUndefinedHttpMethods) {
                // if payload is not allowed for the "undefined" methods
                state.error = true;
                // Send 400; Bad Request
                HttpStatus.BAD_REQUEST_400.setValues(response);
                return;
            }
            
            request.setExpectContent(hasPayload);
        } else {
            request.setExpectContent(method == Method.CONNECT ||
                    method == Method.PRI);
        }
        
        // ------ Set keep-alive flag
        if (method == Method.CONNECT) {
            state.keepAlive = false;
        } else {
            final DataChunk connectionValueDC = headers.getValue(Header.Connection);
            final boolean isConnectionClose = (connectionValueDC != null &&
                    connectionValueDC.equalsIgnoreCaseLowerCase(CLOSE_BYTES));

            if (!isConnectionClose) {
                state.keepAlive = allowKeepAlive && (isHttp11 ||
                        (connectionValueDC != null &&
                        connectionValueDC.equalsIgnoreCaseLowerCase(KEEPALIVE_BYTES)));
            }
        }
        
        if (request.requiresAcknowledgement()) {
            // if we have any request content, we can ignore the Expect
            // request
            request.requiresAcknowledgement(isHttp11 && !hasReadyContent);
        }
    }

    @Override
    protected final boolean onHttpPacketParsed(final HttpHeader httpHeader,
            final FilterChainContext ctx) {
        final ServerHttpRequestImpl request = (ServerHttpRequestImpl) httpHeader;

        final boolean error = request.getProcessingState().error;
        if (!error) {
            // remove the Connection -> HttpRequestPacket association
            httpRequestInProcessAttr.remove(ctx.getConnection());
        }
        return error;
    }

    @Override
    protected void onInitialLineParsed(final HttpHeader httpHeader,
                                       final FilterChainContext ctx) {
        // no-op

    }

    @Override
    protected void onHttpHeadersParsed(final HttpHeader httpHeader,
                                       final MimeHeaders headers,
                                       final FilterChainContext ctx) {
        // no-op

    }

    @Override
    protected void onHttpContentParsed(HttpContent content, FilterChainContext ctx) {

        // no-op

    }

    @Override
    protected void onHttpHeaderError(final HttpHeader httpHeader,
                               final FilterChainContext ctx,
                               final Throwable t) throws IOException {

        final ServerHttpRequestImpl request = (ServerHttpRequestImpl) httpHeader;
        final HttpResponsePacket response = request.getResponse();

        sendBadRequestResponse(ctx, response);
    }



    @Override
    protected void onHttpContentError(final HttpHeader httpHeader,
            final FilterChainContext ctx,
            final Throwable t) throws IOException {
        final ServerHttpRequestImpl request = (ServerHttpRequestImpl) httpHeader;
        final HttpResponsePacket response = request.getResponse();
        if (!response.isCommitted()) {
            sendBadRequestResponse(ctx, response);
        }
        httpHeader.setContentBroken(true);

    }
    
    // ----------------------------------------------------------- Serializing
    
    @Override
    protected Buffer encodeHttpPacket(final FilterChainContext ctx,
            final HttpPacket input) {
        final HttpHeader header;
        HttpContent content;
        
        final boolean isHeaderPacket = input.isHeader();        
        if (isHeaderPacket) {
            header = (HttpHeader) input;
            content = null;
        } else {
            content = (HttpContent) input;
            header = content.getHttpHeader();
        }
        
        boolean wasContentAlreadyEncoded = false;
        final HttpResponsePacket response = (HttpResponsePacket) header;
        if (!response.isCommitted()) {
            final HttpContent encodedHttpContent = prepareResponse(
                    ctx, response.getRequest(), response, content);
            
            if (encodedHttpContent != null) {
                content = encodedHttpContent;
                wasContentAlreadyEncoded = true;
            }
        }

        final Buffer encoded = super.encodeHttpPacket(ctx, header, content,
                wasContentAlreadyEncoded);
        if (!isHeaderPacket) {
            input.recycle();
        }
        return encoded;
    }

    /**
     * Prepare Http response
     * @return encoded HttpContent, if content encoders have been applied, or
     *      <tt>null</tt>, if HttpContent wasn't changed.
     */
    private HttpContent prepareResponse(final FilterChainContext ctx,
            final HttpRequestPacket request,
            final HttpResponsePacket response,
            final HttpContent httpContent) {
        
        // If it's upgraded HTTP - don't check semantics
        if (request.isIgnoreContentModifiers() ||
                response.isIgnoreContentModifiers()) {
            return httpContent;
        }
        
        final Protocol requestProtocol = request.getProtocol();

        if (requestProtocol == Protocol.HTTP_0_9) {
            return null;
        }
        
        boolean entityBody = true;
        final int statusCode = response.getStatus();

        if ((statusCode == 204) || (statusCode == 205)
                || (statusCode == 304)) {
            // No entity body
            entityBody = false;
            response.setExpectContent(false);
        }
        
        final boolean isHttp11OrHigher = (requestProtocol.compareTo(Protocol.HTTP_1_1) >= 0);

        HttpContent encodedHttpContent = null;
        
        final Method method = request.getMethod();
        
        if (!Method.CONNECT.equals(method)) {
            // @TODO consider moving underlying "if"-logic to HttpCodecFilter
            // to make it common for client and server sides.
            if (entityBody) {
                // Check if any compression would be applied
                setContentEncodingsOnSerializing(response);
                
                if (response.getContentLength() == -1L && !response.isChunked()) {
                    // If neither content-length not chunking is explicitly set -
                    // try to apply one of those depending on headers and content
                    if (httpContent != null && httpContent.isLast()) {
                        // if this is first and last data chunk - set the content-length
                        if (!response.getContentEncodings(true).isEmpty()) {
                            // optimization...
                            // if content encodings have to be applied - apply them here
                            // to be able to set correct content-length
                            encodedHttpContent = encodeContent(ctx.getConnection(), httpContent);
                        }

                        response.setContentLength(httpContent.getContent().remaining());
                    } else if (chunkingEnabled && isHttp11OrHigher) {
                        // otherwise use chunking if possible
                        response.setChunked(true);
                    }
                }
            }
            
            if (Method.HEAD.equals(method)) {
                // No entity body
                response.setExpectContent(false);
                setContentEncodingsOnSerializing(response);
                setTransferEncodingOnSerializing(ctx,
                                                 response,
                                                 httpContent);
            }
        } else { // Method.CONNECT
            // Disable all encodings
            response.setContentEncodingsSelected(true);
            response.setContentLength(-1);
            response.setChunked(false);
        }

        final MimeHeaders headers = response.getHeaders();
        
        if (!entityBody) {
            response.setContentLength(-1);
        } else {
            String contentLanguage = response.getContentLanguage();
            if (contentLanguage != null) {
                headers.setValue(Header.ContentLanguage).setString(contentLanguage);
            }
            
            // Optimize content-type serialization depending on its state
            final ContentType contentType = response.getContentTypeHolder();
            if (contentType.isMimeTypeSet()) {
                final DataChunk contentTypeValue = headers.setValue(Header.ContentType);
                if (contentTypeValue.isNull()) {
                    contentType.serializeToDataChunk(contentTypeValue);
                }
            } else if (defaultResponseContentType != null) {
                final DataChunk contenTypeValue = headers.setValue(Header.ContentType);
                if (contenTypeValue.isNull()) {
                    final String ce = response.getCharacterEncoding();
                    if (ce == null) {
                        contenTypeValue.setBytes(defaultResponseContentTypeBytes);
                    } else {
                        final byte[] array = ContentType.compose(
                                defaultResponseContentTypeBytesNoCharset, ce);
                        contenTypeValue.setBytes(array);
                    }
                }
            }
        }

        if (!response.containsHeader(Header.Date)) {
            response.getHeaders().addValue(Header.Date)
                    .setBytes(FastHttpDateFormat.getCurrentDateBytes());
        }

        final ProcessingState state = response.getProcessingState();
        final boolean isHttp11 = (requestProtocol == Protocol.HTTP_1_1);

        if (state.keepAlive) {
            if (entityBody && !isHttp11 && response.getContentLength() == -1) {
                // HTTP 1.0 response with no content-length having been set.
                // Close the connection to signal the response as being complete.
                state.keepAlive = false;
            } else if (entityBody && !response.isChunked() && response.getContentLength() == -1) {
                // HTTP 1.1 response with chunking disabled and no content-length having been set.
                // Close the connection to signal the response as being complete.
                state.keepAlive = false;
            } else if (!checkKeepAliveRequestsCount(state.getHttpContext())) {
                // We processed max allowed HTTP requests over the keep alive connection
                state.keepAlive = false;
            } else {
                final DataChunk dc = headers.getValue(Header.Connection);
                if (dc != null && !dc.isNull() && dc.equalsIgnoreCase(CLOSE_BYTES)) {
                    state.keepAlive = false;
                }
            }

            // If we know that the request is bad this early, add the
            // Connection: close header.
            state.keepAlive = (state.keepAlive
                    && !statusDropsConnection(response.getStatus()));
        }

        if (!state.keepAlive) {
            headers.setValue(Header.Connection).setBytes(CLOSE_BYTES);
        } else if (!isHttp11 && !state.error) {
            headers.setValue(Header.Connection).setBytes(KEEPALIVE_BYTES);
        }

        return encodedHttpContent;
    }        
    
    @Override
    Buffer encodeInitialLine(HttpPacket httpPacket, Buffer output, MemoryManager memoryManager) {
        final HttpResponsePacket httpResponse = (HttpResponsePacket) httpPacket;
        output = put(memoryManager, output, httpResponse.getProtocol().getProtocolBytes());
        output = put(memoryManager, output, Constants.SP);
        output = put(memoryManager, output, httpResponse.getHttpStatus().getStatusBytes());
        output = put(memoryManager, output, Constants.SP);
        if (httpResponse.isCustomReasonPhraseSet()) {
            
            final DataChunk customReasonPhrase =
                    httpResponse.isHtmlEncodingCustomReasonPhrase() ?
                    HttpUtils.filter(httpResponse.getReasonPhraseDC()) :
                    HttpUtils.filterNonPrintableCharacters(httpResponse.getReasonPhraseDC());
            
            output = put(memoryManager, output,
                    httpResponse.getTempHeaderEncodingBuffer(),
                    customReasonPhrase);
        } else {
            output = put(memoryManager, output,
                    httpResponse.getHttpStatus().getReasonPhraseBytes());
        }

        return output;
    }

    @Override
    protected void onInitialLineEncoded(HttpHeader header, FilterChainContext ctx) {

        // no-op

    }

    @Override
    protected void onHttpHeadersEncoded(HttpHeader httpHeader, FilterChainContext ctx) {

        // no-op

    }

    @Override
    protected void onHttpContentEncoded(HttpContent content, FilterChainContext ctx) {

        // no-op

    }

    @Override
    public NextAction handleEvent(final FilterChainContext ctx,
            final FilterChainEvent event) throws IOException {

        if (event.type() == HttpEvents.ResponseCompleteEvent.TYPE) {
            
            if (ctx.getConnection().isOpen()) {
                final HttpContext context = HttpContext.get(ctx);
                final HttpRequestPacket httpRequest = context.getRequest();

                if (allowKeepAlive) {
                    if (keepAliveQueue != null) {
                        final KeepAliveContext keepAliveContext =
                                keepAliveContextAttr.get(context);

                        keepAliveQueue.add(keepAliveContext,
                                keepAlive.getIdleTimeoutInSeconds(),
                                TimeUnit.SECONDS);
                    }

                    final boolean isStayAlive =
                            httpRequest.getProcessingState().isKeepAlive();

                    processResponseComplete(ctx, httpRequest, isStayAlive);
                } else {
                    processResponseComplete(ctx, httpRequest, false);
                }
            }
            
            return ctx.getStopAction();
        }

        return ctx.getInvokeAction();
    }
    
    @Override
    public NextAction handleClose(final FilterChainContext ctx) throws IOException {
        final ServerHttpRequestImpl httpRequest =
                httpRequestInProcessAttr.get(ctx.getConnection());
        if (httpRequest != null && !httpRequest.isContentBroken()) {
            // if we still have HTTP request in progress and this HTTP request
            // doesn't have specified TransferEncoder - it means we parse
            // it till EOF... so now it's time to notify that this packet has
            // been parsed completely
            if (httpRequest.isExpectContent() &&
                    httpRequest.getTransferEncoding() == null) {
                
                httpRequest.setExpectContent(false);
                // notify processed. If packet has transfer encoding - the notification should be called elsewhere
                onHttpPacketParsed(httpRequest, ctx);
            }
        }

        return ctx.getInvokeAction();
    }

    private void processResponseComplete(final FilterChainContext ctx,
            final HttpRequestPacket httpRequest, final boolean isStayAlive)
            throws IOException {
        
        // if this is upgraded HTTP connection - close it
        if (!httpRequest.getUpgradeDC().isNull()) {
            httpRequest.getProcessingState().getHttpContext().close();
            return;
        }
        
        if (httpRequest.isExpectContent()) {
            if (!httpRequest.isContentBroken() &&
                    checkContentLengthRemainder(httpRequest)) {
                // If transfer encoding is defined and we can determine the message body length
                // we will check HTTP keep-alive settings once remainder is fully read
                httpRequest.setSkipRemainder(true);
            } else {
                // if the packet is broken notify it's been parsed and
                // close the connection
                httpRequest.setExpectContent(false);
                onHttpPacketParsed(httpRequest, ctx);
                // no matter it's keep-alive or not - we close the connection
                httpRequest.getProcessingState().getHttpContext().close();
//                flushAndClose(ctx);
            }
        } else if (!isStayAlive) {
            // if we don't expect more data on the request and it's not in keep-alive mode
            // close it
            httpRequest.getProcessingState().getHttpContext().close();
//            flushAndClose(ctx);
        } /* else {
            we don't expect more data on the request, but it's keep-alive
        }*/
        
    }
    
    protected HttpContent customizeErrorResponse(
            final HttpResponsePacket response) {
        
        response.setContentLength(0);
        return HttpContent.builder(response).last(true).build();
    }

    private boolean checkKeepAliveRequestsCount(final HttpContext httpContext) {
        if (!allowKeepAlive) {
            return false;
        }
        
        final KeepAliveContext keepAliveContext = keepAliveContextAttr.get(httpContext);
        final int requestsProcessed = keepAliveContext.requestsProcessed++;
        final int maxRequestCount = keepAlive.getMaxRequestsCount();
        final boolean isKeepAlive = (maxRequestCount == -1 ||
                keepAliveContext.requestsProcessed <= maxRequestCount);
        
        if (requestsProcessed == 0) {
            if (isKeepAlive) { // New keep-alive connection
                KeepAlive.notifyProbesConnectionAccepted(keepAlive,
                        keepAliveContext.connection);
            } else { // Refused keep-alive connection
                KeepAlive.notifyProbesRefused(keepAlive, keepAliveContext.connection);
            }
        }

        return isKeepAlive;
    }

    private void sendBadRequestResponse(final FilterChainContext ctx,
                                        final HttpResponsePacket response) {
        if (response.getHttpStatus().getStatusCode() < 400) {
            // 400 - Bad request
            HttpStatus.BAD_REQUEST_400.setValues(response);
        }
        commitAndCloseAsError(ctx, response);
    }

    /*
     * caller has the responsibility to set the status of th response.
     */
    private void commitAndCloseAsError(FilterChainContext ctx, HttpResponsePacket response) {
        final HttpContent errorHttpResponse = customizeErrorResponse(response);
        final Buffer resBuf = encodeHttpPacket(ctx, errorHttpResponse);
        ctx.write(resBuf);
        response.getProcessingState().getHttpContext().close();
    }

    /**
     * @param httpRequest
     * @return <tt>false</tt> if the request payload size is specified by the
     *      content-length header and according to the request parsing state
     *      the remaining payload size is larger than {@link #getMaxPayloadRemainderToSkip()}.
     *      Otherwise return <tt>true</tt>
     */
    private boolean checkContentLengthRemainder(final HttpRequestPacket httpRequest) {
        return maxPayloadRemainderToSkip < 0 ||
                httpRequest.getContentLength() <= 0 ||
                ((HttpPacketParsing) httpRequest).getContentParsingState().chunkRemainder <= maxPayloadRemainderToSkip;
    }

    // ---------------------------------------------------------- Nested Classes

     private static class KeepAliveContext {
        private final Connection connection;

        public KeepAliveContext(Connection connection) {
            this.connection = connection;
        }

        private volatile long keepAliveTimeoutMillis = DelayedExecutor.UNSET_TIMEOUT;
        private int requestsProcessed;
    } // END KeepAliveContext


    private static class KeepAliveWorker implements DelayedExecutor.Worker<KeepAliveContext> {

        private final KeepAlive keepAlive;

        public KeepAliveWorker(final KeepAlive keepAlive) {
            this.keepAlive = keepAlive;
        }

        @Override
        public boolean doWork(final KeepAliveContext context) {
            KeepAlive.notifyProbesTimeout(keepAlive, context.connection);
            context.connection.closeSilently();

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
        public long getTimeoutMillis(KeepAliveContext element) {
            return element.keepAliveTimeoutMillis;
        }

        @Override
        public void setTimeoutMillis(KeepAliveContext element, long timeoutMillis) {
            element.keepAliveTimeoutMillis = timeoutMillis;
        }

    } // END KeepAliveResolver

    private static final class ServerHttpRequestImpl extends HttpRequestPacket
            implements HttpPacketParsing {

        private static final ThreadCache.CachedTypeIndex<ServerHttpRequestImpl> CACHE_IDX =
                ThreadCache.obtainIndex(ServerHttpRequestImpl.class, 16);

        public static ServerHttpRequestImpl create() {
            final ServerHttpRequestImpl httpRequestImpl =
                    ThreadCache.takeFromCache(CACHE_IDX);
            if (httpRequestImpl != null) {
                return httpRequestImpl;
            }

            return new ServerHttpRequestImpl();
        }
        
        /**
         * Char encoding parsed flag.
         */
        private boolean contentTypeParsed;
        
        private boolean isHeaderParsed;
        private final HttpCodecFilter.HeaderParsingState headerParsingState;
        private final HttpCodecFilter.ContentParsingState contentParsingState;
        private final ProcessingState processingState;

        private final HttpResponsePacket finalHttpResponse;
        
        private ServerHttpRequestImpl() {
            this.headerParsingState = new HttpCodecFilter.HeaderParsingState();
            this.contentParsingState = new HttpCodecFilter.ContentParsingState();
            this.processingState = new ProcessingState();
            finalHttpResponse = new HttpResponsePacketImpl();
            isExpectContent = true;
        }

        public void initialize(final Connection connection,
                final HttpCodecFilter filter,
                final int initialOffset,
                final int maxHeaderSize,
                final int maxNumberOfHeaders) {
            headerParsingState.initialize(filter, initialOffset, maxHeaderSize);
            contentParsingState.trailerHeaders.setMaxNumHeaders(maxNumberOfHeaders);
            headers.setMaxNumHeaders(maxNumberOfHeaders);
            finalHttpResponse.setProtocol(Protocol.HTTP_1_1);
            setResponse(finalHttpResponse);
            setConnection(connection);
        }

        @Override
        public String getCharacterEncoding() {
            if (!contentTypeParsed) {
                parseContentTypeHeader();
            }

            return super.getCharacterEncoding();
        }

        @Override
        public void setCharacterEncoding(final String charset) {
            if (!contentTypeParsed) {
                parseContentTypeHeader();
            }

            super.setCharacterEncoding(charset);
        }

        @Override
        public String getContentType() {
            if (!contentTypeParsed) {
                parseContentTypeHeader();
            }
            
            return super.getContentType();
        }

        private void parseContentTypeHeader() {
            contentTypeParsed = true;
            
            if (!contentType.isSet()) {
                final DataChunk dc = headers.getValue(Header.ContentType);
                
                if (dc != null && !dc.isNull()) {
                    setContentType(dc.toString());
                }
            }
        }
        
        @Override
        public ProcessingState getProcessingState() {
            return processingState;
        }

        @Override
        public HttpCodecFilter.HeaderParsingState getHeaderParsingState() {
            return headerParsingState;
        }

        @Override
        public ContentParsingState getContentParsingState() {
            return contentParsingState;
        }

        @Override
        public boolean isHeaderParsed() {
            return isHeaderParsed;
        }

        @Override
        public void setHeaderParsed(final boolean isHeaderParsed) {
            if (isHeaderParsed && isExpectContent() && !isChunked) {
                contentParsingState.chunkRemainder = getContentLength();
            }

            this.isHeaderParsed = isHeaderParsed;
        }

        @Override
        protected HttpPacketParsing getParsingState() {
            return this;
        }
        
        /**
         * {@inheritDoc}
         */
        @Override
        protected void reset() {
            contentTypeParsed = false;
            isHeaderParsed = false;
            headerParsingState.recycle();
            contentParsingState.recycle();
            processingState.recycle();
            
            super.reset();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void recycle() {
            if (isExpectContent()) {
                return;
            }
            reset();
            ThreadCache.putToCache(CACHE_IDX, this);
        }
    }
}
