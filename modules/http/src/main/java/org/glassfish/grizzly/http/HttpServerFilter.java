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

package org.glassfish.grizzly.http;

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

import static org.glassfish.grizzly.http.HttpResponsePacket.NON_PARSED_STATUS;

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

    public static final Object RESPONSE_COMPLETE_EVENT = new Object();

    private static final FlushAndCloseHandler FLUSH_AND_CLOSE_HANDLER =
            new FlushAndCloseHandler();

    private final Attribute<HttpRequestPacketImpl> httpRequestInProcessAttr;
    private final Attribute<KeepAliveContext> keepAliveContextAttr;

    private final DelayedExecutor.DelayQueue<KeepAliveContext> keepAliveQueue;

    private final KeepAlive keepAlive;

    private final boolean processKeepAlive;

    /**
     * Constructor, which creates <tt>HttpServerFilter</tt> instance
     */
    public HttpServerFilter() {
        this(null, true, DEFAULT_MAX_HTTP_PACKET_HEADER_SIZE, null, null);
    }

    /**
     * Constructor, which creates <tt>HttpServerFilter</tt> instance,
     * with the specific max header size parameter.
     *
     * @param maxHeadersSize the maximum size of the HTTP message header.
     */
    public HttpServerFilter(boolean chunkingEnabled,
                            int maxHeadersSize,
                            KeepAlive keepAlive,
                            DelayedExecutor executor) {
        this(null, chunkingEnabled, maxHeadersSize, keepAlive, executor);
    }

    /**
     * Constructor, which creates <tt>HttpServerFilter</tt> instance,
     * with the specific max header size parameter.
     *
     * @param isSecure <tt>true</tt>, if the Filter will be used for secured HTTPS communication,
     *                 or <tt>false</tt> otherwise. It's possible to pass <tt>null</tt>, in this
     *                 case Filter will try to autodetect security.
     * @param maxHeadersSize the maximum size of the HTTP message header.
     */
    public HttpServerFilter(Boolean isSecure,
                            boolean chunkingEnabled,
                            int maxHeadersSize,
                            KeepAlive keepAlive,
                            DelayedExecutor executor) {
        super(isSecure, chunkingEnabled, maxHeadersSize);

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

        contentEncodings.add(
                new GZipContentEncoding(
                GZipContentEncoding.DEFAULT_IN_BUFFER_SIZE,
                GZipContentEncoding.DEFAULT_OUT_BUFFER_SIZE));
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
    public NextAction handleRead(FilterChainContext ctx) throws IOException {
        Buffer input = (Buffer) ctx.getMessage();
        final Connection connection = ctx.getConnection();
        
        HttpRequestPacketImpl httpRequest = httpRequestInProcessAttr.get(connection);
        if (httpRequest == null) {



            final boolean isSecureLocal = isSecure;
            httpRequest = HttpRequestPacketImpl.create();
            httpRequest.initialize(connection, input.position(), maxHeadersSize);
            httpRequest.setSecure(isSecureLocal);
            HttpResponsePacketImpl response = HttpResponsePacketImpl.create();
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
    public NextAction handleEvent(FilterChainContext ctx, Object event)
    throws IOException {

        final Connection c = ctx.getConnection();
        
        if (event == RESPONSE_COMPLETE_EVENT && c.isOpen()) {

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
                    httpRequest.setSkipRemainder(true);
                }
            }
            keepAliveContext.request = null;
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
    final boolean onHttpPacketParsed(HttpHeader httpHeader, FilterChainContext ctx) {
        HttpRequestPacketImpl request = (HttpRequestPacketImpl) httpHeader;

        boolean error = request.getProcessingState().error;
        if (!error) {
            httpRequestInProcessAttr.remove(ctx.getConnection());
        }
        return error;
    }

    @Override
    void onHttpError(HttpHeader httpHeader, FilterChainContext ctx) throws IOException {

        HttpRequestPacketImpl request = (HttpRequestPacketImpl) httpHeader;

        // commit the response
        final HttpContent errorHttpResponse = customizeErrorResponse(request.getResponse());
        Buffer resBuf = encodeHttpPacket(ctx.getConnection(), errorHttpResponse);
        ctx.write(resBuf);
    }

    @Override
    protected Buffer encodeHttpPacket(Connection connection, HttpPacket input) {
        HttpHeader header;
        if (input.isHeader()) {
            header = (HttpHeader) input;
        } else {
            header = ((HttpContent) input).getHttpHeader();
        }
        HttpResponsePacketImpl response = (HttpResponsePacketImpl) header;
        if (response.getUpgrade() == null && !response.isCommitted()) {
            prepareResponse(response.getRequest(), response);
        }

        final Buffer encoded = super.encodeHttpPacket(connection, input);
        if (input instanceof HttpContent) {
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
        output = put(memoryManager, output, httpResponse.getStatusDC());
        output = put(memoryManager, output, Constants.SP);
        output = put(memoryManager, output, httpResponse.getReasonPhraseDC(true));

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


    private void prepareResponse(HttpRequestPacket request,
                                 HttpResponsePacketImpl response) {
        final Protocol protocol = request.getProtocol();

        response.setProtocol(protocol);
        if (response.parsedStatusInt == NON_PARSED_STATUS) {
            HttpStatus.OK_200.setValues(response);
        }
        final ProcessingState state = response.getProcessingState();

        if (protocol == Protocol.HTTP_0_9) {
            return;
        }

        boolean entityBody = true;
        int statusCode = response.getStatus();

        if ((statusCode == 204) || (statusCode == 205)
                || (statusCode == 304)) {
            // No entity body
            entityBody = false;
            response.setExpectContent(false);
            state.contentDelimitation = true;
        }

        final boolean isHttp11 = protocol == Protocol.HTTP_1_1;
        final MimeHeaders headers = response.getHeaders();

        long contentLength = response.getContentLength();
        if (contentLength != -1L) {
            state.contentDelimitation = true;
        } else {
            if (chunkingEnabled && entityBody && isHttp11) {
                state.contentDelimitation = true;
                response.setChunked(true);
            }
        }

        DataChunk methodBC = request.getMethodDC();
        if (methodBC.equals("HEAD")) {
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
        }

        if (!response.containsHeader("Date")) {
            String date = FastHttpDateFormat.getCurrentDate();
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

        final DataChunk methodBC = request.getMethodDC();

        if (methodBC.equals("GET")) {
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
        }

        MimeHeaders headers = request.getHeaders();

        // Check for a full URI (including protocol://host:port/)
        // Check for a full URI (including protocol://host:port/)
        final BufferChunk uriBC =
                request.getRequestURIRef().getRequestURIBC().getBufferChunk();
        if (uriBC.startsWithIgnoreCase("http", 0)) {

            int pos = uriBC.indexOf("://", 4);
            int uriBCStart = uriBC.getStart();
            int slashPos;
            if (pos != -1) {
                Buffer uriB = uriBC.getBuffer();
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
                DataChunk hostDC = headers.setValue("host");
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

        long contentLength = request.getContentLength();
        if (contentLength >= 0) {
            state.contentDelimitation = true;
        }

        final DataChunk hostBC = headers.getValue("host");

        // Check host header
        if (hostBC != null) {
            parseHost(hostBC.getBufferChunk(), request, response, state);
        } else if (isHttp11) {
            state.error = true;
            // 400 - Bad request
            HttpStatus.BAD_REQUEST_400.setValues(response);
            return;
        }

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

    protected HttpContent customizeErrorResponse(HttpResponsePacket response) {
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


    private static void parseHost(BufferChunk valueBC,
                                  HttpRequestPacket request,
                                  HttpResponsePacket response,
                                  ProcessingState state) {

        if (valueBC == null || valueBC.isNull()) {
            // HTTP/1.0
            // Default is what the socket tells us. Overridden if a host is
            // found/parsed
            Connection connection = request.getConnection();
            request.setServerPort(((InetSocketAddress) connection.getLocalAddress()).getPort());
            InetAddress localAddress = ((InetSocketAddress) connection.getLocalAddress()).getAddress();
            // Setting the socket-related fields. The adapter doesn't know
            // about socket.
            request.setLocalHost(localAddress.getHostName());
            request.serverName().setString(localAddress.getHostName());
            return;
        }


        int valueS = valueBC.getStart();
        int valueL = valueBC.getEnd() - valueS;
        int colonPos = -1;

        Buffer valueB = valueBC.getBuffer();
        boolean ipv6 = (valueB.get(valueS) == '[');
        boolean bracketClosed = false;
        for (int i = 0; i < valueL; i++) {
            char b = (char) valueB.get(i + valueS);
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
        } else {
            request.serverName().setBuffer(valueB, valueS, colonPos + valueS);

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

        if (processKeepAlive && (keepAliveContext =
                keepAliveContextAttr.get(connection)) != null) {

            return ++keepAliveContext.requestsProcessed <= keepAlive.getMaxRequestsCount();
        }

        return true;
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

        public KeepAliveWorker(KeepAlive keepAlive) {
            this.keepAlive = keepAlive;
        }

        @Override
        public void doWork(KeepAliveContext context) {
            try {
                KeepAlive.notifyProbesTimeout(keepAlive, context.connection);
                context.connection.close().markForRecycle(false);
            } catch (IOException ignored) {
            }
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
