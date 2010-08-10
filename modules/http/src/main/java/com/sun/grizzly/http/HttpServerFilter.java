/*
 *
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2010 Sun Microsystems, Inc. All rights reserved.
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
 * * This file incorporates work covered by the following copyright and
 * permission notice:
 *
 * Copyright 2004 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.sun.grizzly.http;

import com.sun.grizzly.Buffer;
import com.sun.grizzly.Connection;
import com.sun.grizzly.Grizzly;
import com.sun.grizzly.attributes.Attribute;
import com.sun.grizzly.filterchain.FilterChainContext;
import com.sun.grizzly.filterchain.NextAction;
import com.sun.grizzly.http.util.BufferChunk;
import com.sun.grizzly.http.util.FastHttpDateFormat;
import com.sun.grizzly.http.util.HexUtils;
import com.sun.grizzly.http.util.MimeHeaders;
import com.sun.grizzly.memory.MemoryManager;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;

import static com.sun.grizzly.http.HttpResponsePacket.NON_PARSED_STATUS;

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

    private final Attribute<HttpRequestPacketImpl> httpRequestInProcessAttr;

    private boolean chunking = true;

    /**
     * Constructor, which creates <tt>HttpServerFilter</tt> instance
     */
    public HttpServerFilter() {
        this(null, DEFAULT_MAX_HTTP_PACKET_HEADER_SIZE);
    }

    /**
     * Constructor, which creates <tt>HttpServerFilter</tt> instance,
     * with the specific max header size parameter.
     *
     * @param maxHeadersSize the maximum size of the HTTP message header.
     */
    public HttpServerFilter(int maxHeadersSize) {
        this(null, maxHeadersSize);
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
    public HttpServerFilter(Boolean isSecure, int maxHeadersSize) {
        super(isSecure, maxHeadersSize);

        this.httpRequestInProcessAttr =
                Grizzly.DEFAULT_ATTRIBUTE_BUILDER.createAttribute(
                "HttpServerFilter.httpRequest");
        contentEncodings.add(
                    new GZipContentEncoding(GZipContentEncoding.DEFAULT_IN_BUFFER_SIZE,
                    GZipContentEncoding.DEFAULT_OUT_BUFFER_SIZE,
                    new EncodingFilter() {
                        @Override
                        public boolean applyEncoding(HttpHeader httpPacket) {
                            if (!httpPacket.isRequest()) {
                                final HttpResponsePacket response = (HttpResponsePacket) httpPacket;
                                final HttpRequestPacket request;
                                final BufferChunk acceptEncoding;
                                if (response.isChunked() && (request = response.getRequest()) != null &&
                                        (acceptEncoding = request.getHeaders().getValue("Accept-Encoding")) != null &&
                                        acceptEncoding.indexOf("gzip", 0) >= 0) {
                                    return true;
                                }
                            }

                            return false;
                        }
                    })
                );
    }
    
    // ----------------------------------------------------------- Configuration


    public boolean isChunking() {
        return chunking;
    }

    public void setChunking(boolean chunking) {
        this.chunking = chunking;
    }

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
            
            httpRequestInProcessAttr.set(connection, httpRequest);
        }

        return handleRead(ctx, httpRequest);
    }

    @Override
    boolean onHttpHeaderParsed(final HttpHeader httpHeader, final Buffer buffer,
            final FilterChainContext ctx) {

        final HttpRequestPacketImpl request = (HttpRequestPacketImpl) httpHeader;

        // If it's upgraded HTTP - don't check semantics
        if (!request.getUpgradeBC().isNull()) return false;

        
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

        return super.encodeHttpPacket(connection, input);
    }

    @Override
    final boolean decodeInitialLine(HttpPacketParsing httpPacket,
            ParsingState parsingState, Buffer input) {

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

                    httpRequest.getMethodBC().setBuffer(input,
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
                        httpRequest.getProtocolBC().setBuffer(
                                input, parsingState.start,
                                parsingState.checkpoint);
                    } else {
                        httpRequest.setProtocol("");
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
        output = put(memoryManager, output, httpResponse.getProtocolBC());
        output = put(memoryManager, output, Constants.SP);
        output = put(memoryManager, output, httpResponse.getStatusBC());
        output = put(memoryManager, output, Constants.SP);
        output = put(memoryManager, output, httpResponse.getReasonPhraseBC());

        return output;
    }

    private static boolean parseRequestURI(HttpRequestPacketImpl httpRequest,
            ParsingState state, Buffer input) {
        
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
            httpRequest.getRequestURIRef().getRequestURIBC().setBuffer(input, state.start, offset);
            if (state.checkpoint != -1) {
                // cut RequestURI to not include query string
                httpRequest.getRequestURIRef().getRequestURIBC().setEnd(state.checkpoint);

                httpRequest.getQueryStringBC().setBuffer(input,
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

        response.setProtocol(HttpCodecFilter.HTTP_1_1);
        if (response.parsedStatusInt == NON_PARSED_STATUS) {
            response.setStatus(200);
        }
        ProcessingState state = response.getProcessingState();

        if (state.http09) {
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

//        // TODO: Check for compression
//        boolean useCompression = false;
//        if (entityBody && (compressionLevel > 0)) {
//            useCompression = isCompressable();
//
//            // Change content-length to -1 to force chunking
//            if (useCompression) {
//                response.setContentLength(-1);
//            }
//        }

        MimeHeaders headers = response.getHeaders();

        long contentLength = response.getContentLength();
        if (contentLength != -1L) {
            state.contentDelimitation = true;
        } else {
            if (isChunking() && entityBody && state.http11) {
                state.contentDelimitation = true;
                response.setChunked(true);
            }
        }

        BufferChunk methodBC = request.getMethodBC();
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
        }

        // If we know that the request is bad this early, add the
        // Connection: close header.
        state.keepAlive = (state.keepAlive &&
                !statusDropsConnection(response.getStatus()));
        //       && !dropConnection;

        if (!state.keepAlive) {
            headers.setValue("Connection").setString("close");
            //connectionHeaderValueSet = false;
        } else if (!state.http11 && !state.error) {
            headers.setValue("Connection").setString("Keep-Alive");
        }

    }
    

    private static void prepareRequest(final HttpRequestPacketImpl request,
            final boolean hasReadyContent) {

        final ProcessingState state = request.getProcessingState();
        final HttpResponsePacket response = request.getResponse();

        final BufferChunk methodBC = request.getMethodBC();

        if (methodBC.equals("GET")) {
            request.setExpectContent(false);
        }

        final BufferChunk protocolBC = request.getProtocolBC();
        if (protocolBC.equals(HttpCodecFilter.HTTP_1_1)) {
            protocolBC.setString(HttpCodecFilter.HTTP_1_1);
            state.http11 = true;
        } else if (protocolBC.equals(HttpCodecFilter.HTTP_1_0)) {
            protocolBC.setString(HttpCodecFilter.HTTP_1_0);
            state.http11 = false;
            state.keepAlive = false;
        } else if (protocolBC.equals(HttpCodecFilter.HTTP_0_9)) {
            protocolBC.setString(HttpCodecFilter.HTTP_0_9);
            state.http09 = true;
            state.http11 = false;
            state.keepAlive = false;
        } else {
            // Unsupported protocol
            state.http11 = false;
            state.error = true;
            // Send 505; Unsupported HTTP version
            response.setStatus(505);
            response.setReasonPhrase("Unsupported Protocol Version");
        }

        MimeHeaders headers = request.getHeaders();

        // Check for a full URI (including protocol://host:port/)
        // Check for a full URI (including protocol://host:port/)
        BufferChunk uriBC = request.getRequestURIRef().getRequestURIBC();
        if (uriBC.startsWithIgnoreCase("http", 0)) {

            int pos = uriBC.indexOf("://", 4);
            int uriBCStart = uriBC.getStart();
            int slashPos;
            if (pos != -1) {
                Buffer uriB = uriBC.getBuffer();
                slashPos = uriBC.indexOf('/', pos + 3);
                if (slashPos == -1) {
                    slashPos = uriBC.length();
                    // Set URI as "/"
                    uriBC.setBuffer(uriB, uriBCStart + pos + 1, 1);
                } else {
                    uriBC.setBuffer(uriB,
                                    uriBCStart + slashPos,
                                    uriBC.length() - slashPos);
                }
                BufferChunk hostMB = headers.setValue("host");
                hostMB.setBuffer(uriB,
                                 uriBCStart + pos + 3,
                                 slashPos - pos - 3);
            }

        }

        BufferChunk connectionValueMB = headers.getValue("connection");
        if (connectionValueMB != null) {
            if (connectionValueMB.findBytesAscii(Constants.CLOSE_BYTES) != -1) {
                state.keepAlive = false;
                //connectionHeaderValueSet = false;
            } else if (connectionValueMB.findBytesAscii(Constants.KEEPALIVE_BYTES) != -1) {
                state.keepAlive = true;
                // connectionHeaderValueSet = true
            }
        }

        long contentLength = request.getContentLength();
        if (contentLength >= 0) {
            state.contentDelimitation = true;
        }

        BufferChunk hostBC = headers.getValue("host");

        // Check host header
        if (state.http11 && hostBC == null) {
            state.error = true;
            // 400 - Bad request
            response.setStatus(400);
            response.setReasonPhrase("Bad Request");
        }

        parseHost(hostBC, request, response, state);

        if (!state.contentDelimitation) {
            // If there's no content length
            // (broken HTTP/1.0 or HTTP/1.1), assume
            // the client is not broken and didn't send a body
            state.contentDelimitation = true;
        }

        if (request.requiresAcknowledgement()) {
            // if we have any request content, we can ignore the Expect
            // request
            request.requiresAcknowledgement(state.http11 && !hasReadyContent);
        }
    }

    protected HttpContent customizeErrorResponse(HttpResponsePacket response) {
        response.setContentLength(0);
        final HttpContent content = HttpContent.builder(response).last(true).build();
        return content;
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
                    response.setStatus(400);
                    response.setReasonPhrase("Bad Request");
                    return;
                }
                port = port + (charValue * mult);
                mult = 10 * mult;
            }
            request.setServerPort(port);

        }

    }
}
