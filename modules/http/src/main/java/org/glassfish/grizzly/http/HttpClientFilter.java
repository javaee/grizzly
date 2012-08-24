/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2012 Oracle and/or its affiliates. All rights reserved.
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

import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.attributes.Attribute;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.http.util.MimeHeaders;
import org.glassfish.grizzly.memory.MemoryManager;
import java.io.IOException;
import java.util.Queue;
import org.glassfish.grizzly.ThreadCache;

import org.glassfish.grizzly.http.util.Ascii;
import org.glassfish.grizzly.http.util.Constants;
import org.glassfish.grizzly.http.util.DataChunk;
import org.glassfish.grizzly.http.util.Header;
import org.glassfish.grizzly.utils.DataStructures;

import static org.glassfish.grizzly.http.util.HttpCodecUtils.*;

/**
 * Client side {@link HttpCodecFilter} implementation, which is responsible for
 * decoding {@link HttpResponsePacket} and encoding {@link HttpRequestPacket} messages.
 *
 * This <tt>Filter</tt> is usually used, when we build an asynchronous HTTP client
 * connection.
 *
 * @see HttpCodecFilter
 * @see HttpServerFilter
 *
 * @author Alexey Stashok
 */
public class HttpClientFilter extends HttpCodecFilter {

    private final Attribute<Queue<HttpRequestPacket>> httpRequestQueueAttr;
    private final Attribute<ClientHttpResponseImpl> httpResponseInProcessAttr;

    /**
     * Constructor, which creates <tt>HttpClientFilter</tt> instance
     */
    public HttpClientFilter() {
        this(DEFAULT_MAX_HTTP_PACKET_HEADER_SIZE);
    }

    /**
     * Constructor, which creates <tt>HttpClientFilter</tt> instance,
     * with the specific secure and max header size parameter.
     *
     * @param maxHeadersSize the maximum size of the HTTP message header.
     */
    public HttpClientFilter(int maxHeadersSize) {
        super(true, maxHeadersSize);

        this.httpResponseInProcessAttr =
                Grizzly.DEFAULT_ATTRIBUTE_BUILDER.createAttribute(
                "HttpClientFilter.httpResponse");
        this.httpRequestQueueAttr =
                Grizzly.DEFAULT_ATTRIBUTE_BUILDER.createAttribute(
                "HttpClientFilter.httpRequest");

        contentEncodings.add(new GZipContentEncoding());
        contentEncodings.add(new LZMAContentEncoding());
    }

    @Override
    public NextAction handleWrite(FilterChainContext ctx) throws IOException {

        final Connection c = ctx.getConnection();
        final Queue<HttpRequestPacket> requestQueue = getRequestQueue(c);
        final Object message = ctx.getMessage();
        //noinspection SuspiciousMethodCalls
        if (requestQueue.isEmpty() || !requestQueue.contains(message)) {
            if (HttpHeader.isHttp(message)) {
                HttpHeader header;
                if (message instanceof HttpHeader) {
                    header = (HttpHeader) message;
                } else {
                    header = ((HttpContent) message).getHttpHeader();
                }
                if (header.isRequest()) {
                    requestQueue.offer((HttpRequestPacket) header);
                }
            }
        }
        return super.handleWrite(ctx);

    }

    /**
     * The method is called, once we have received a {@link Buffer},
     * which has to be transformed into HTTP response packet part.
     *
     * Filter gets {@link Buffer}, which represents a part or complete HTTP
     * response message. As the result of "read" transformation - we will get
     * {@link HttpContent} message, which will represent HTTP response packet
     * content (might be zero length content) and reference
     * to a {@link HttpHeader}, which contains HTTP response message header.
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
        
        ClientHttpResponseImpl httpResponse = httpResponseInProcessAttr.get(connection);
        if (httpResponse == null) {
            httpResponse = ClientHttpResponseImpl.create();
            final Queue<HttpRequestPacket> requestQueue = getRequestQueue(connection);
            httpResponse.setRequest(requestQueue.poll());
            httpResponse.initialize(this, input.position(), maxHeadersSize, MimeHeaders.MAX_NUM_HEADERS_UNBOUNDED);
            httpResponse.setSecure(isSecure(connection));
            httpResponseInProcessAttr.set(connection, httpResponse);
        }

        return handleRead(ctx, httpResponse);
    }


    
    @Override
    protected boolean onHttpPacketParsed(HttpHeader httpHeader, FilterChainContext ctx) {
        final Connection connection = ctx.getConnection();
        clearResponse(connection);
        return false;
    }

    @Override
    protected boolean onHttpHeaderParsed(final HttpHeader httpHeader, final Buffer buffer,
            final FilterChainContext ctx) {
        final ClientHttpResponseImpl response = (ClientHttpResponseImpl) httpHeader;
        final HttpRequestPacket request = response.getRequest();
        final int statusCode = response.getStatus();

        if ((statusCode == 204) || (statusCode == 205)
                || (statusCode == 304)
                || ((request != null) && request.isHeadRequest())) {
            // No entity body
            response.setExpectContent(false);
        }
        
        return false;
    }

    @Override
    protected void onHttpHeaderError(final HttpHeader httpHeader,
                               final FilterChainContext ctx,
                               final Throwable t) throws IOException {
        throw new IllegalStateException(t);
    }

    @Override
    protected void onHttpContentError(final HttpHeader httpHeader,
                               final FilterChainContext ctx,
                               final Throwable t) throws IOException {
        httpHeader.setContentBroken(true);
        throw new IllegalStateException(t);
    }

    @Override
    protected void onInitialLineParsed(final HttpHeader httpHeader,
                                       final FilterChainContext ctx) {
        // no-op
    }

    @Override
    protected void onInitialLineEncoded(HttpHeader header, FilterChainContext ctx) {

        // no-op

    }

    @Override
    protected void onHttpHeadersParsed(final HttpHeader httpHeader,
                                       final FilterChainContext ctx) {
        // no-op
    }

    @Override
    protected void onHttpHeadersEncoded(HttpHeader httpHeader, FilterChainContext ctx) {

        // no-op

    }

    @Override
    protected void onHttpContentParsed(HttpContent content, FilterChainContext ctx) {

        // no-op

    }

    @Override
    protected void onHttpContentEncoded(HttpContent content, FilterChainContext ctx) {

        // no-op

    }

    protected final void clearResponse(final Connection connection) {

        httpResponseInProcessAttr.remove(connection);

    }

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
        
        final HttpRequestPacket request = (HttpRequestPacket) header;
        if (!request.isCommitted()) {
            prepareRequest(request);
        }

        return super.encodeHttpPacket(ctx, header, content, false);
    }

    @Override
    final boolean decodeInitialLineFromBytes(final FilterChainContext ctx,
            final HttpPacketParsing httpPacket,
            final HeaderParsingState parsingState,
            final byte[] input,
            final int end) {
        
        final HttpResponsePacket httpResponse = (HttpResponsePacket) httpPacket;
        
        final int arrayOffs = parsingState.arrayOffset;
        final int packetLimit = arrayOffs + parsingState.packetLimit;

        //noinspection LoopStatementThatDoesntLoop
        while(true) {
            int subState = parsingState.subState;

            switch(subState) {
                case 0: { // HTTP protocol
                    final int spaceIdx =
                            findSpace(input, arrayOffs + parsingState.offset, end, packetLimit);
                    if (spaceIdx == -1) {
                        parsingState.offset = end - arrayOffs;
                        return false;
                    }

                    httpResponse.getProtocolDC().setBytes(input,
                            arrayOffs + parsingState.start, spaceIdx);

                    parsingState.start = -1;
                    parsingState.offset = spaceIdx - arrayOffs;

                    parsingState.subState++;
                }

                case 1: { // skip spaces after the HTTP protocol
                    final int nonSpaceIdx =
                            skipSpaces(input, arrayOffs + parsingState.offset,
                            end, packetLimit) - arrayOffs;
                    
                    if (nonSpaceIdx < 0) {
                        parsingState.offset = end - arrayOffs;
                        return false;
                    }

                    parsingState.start = nonSpaceIdx;
                    parsingState.offset = nonSpaceIdx + 1;
                    parsingState.subState++;
                }

                case 2 : { // parse the status code
                    if (parsingState.offset + 3 > end - arrayOffs) {
                        return false;
                    }

                    httpResponse.setStatus(Ascii.parseInt(input,
                                                          arrayOffs + parsingState.start,
                                                          3));

                    parsingState.start = -1;
                    parsingState.offset += 3;
                    parsingState.subState++;
                }

                case 3: { // skip spaces after the status code
                    final int nonSpaceIdx =
                            skipSpaces(input, arrayOffs + parsingState.offset,
                            end, packetLimit) - arrayOffs;
                    if (nonSpaceIdx < 0) {
                        parsingState.offset = end - arrayOffs;
                        return false;
                    }

                    parsingState.start = nonSpaceIdx;
                    parsingState.offset = nonSpaceIdx;
                    parsingState.subState++;
                }

                case 4: { // HTTP response reason-phrase
                    if (!findEOL(parsingState, input, end)) {
                        parsingState.offset = end - arrayOffs;
                        return false;
                    }
                    
                    httpResponse.getReasonPhraseRawDC().setBytes(
                            input, arrayOffs + parsingState.start,
                            arrayOffs + parsingState.checkpoint);

                    parsingState.subState = 0;
                    parsingState.start = -1;
                    parsingState.checkpoint = -1;
                    onInitialLineParsed(httpResponse, ctx);
                    if (httpResponse.getStatus() == 100) {
                        // reset the parsing state in preparation to parse
                        // another initial line which represents the final
                        // response from the server after it has sent a
                        // 100-Continue.
                        parsingState.offset += 2;
                        parsingState.start = 0;
//                        input.position(parsingState.offset);
//                        input.shrink();
//                        parsingState.offset = 0;
                        return false;
                    }
                    return true;
                }

                default: throw new IllegalStateException();
            }
        }
    }
    
    
    @Override
    final boolean decodeInitialLineFromBuffer(final FilterChainContext ctx,
                                    final HttpPacketParsing httpPacket,
                                    final HeaderParsingState parsingState,
                                    final Buffer input) {

        final HttpResponsePacket httpResponse = (HttpResponsePacket) httpPacket;
        
        final int packetLimit = parsingState.packetLimit;

        //noinspection LoopStatementThatDoesntLoop
        while(true) {
            int subState = parsingState.subState;

            switch(subState) {
                case 0: { // HTTP protocol
                    final int spaceIdx =
                            findSpace(input, parsingState.offset, packetLimit);
                    if (spaceIdx == -1) {
                        parsingState.offset = input.limit();
                        return false;
                    }

                    httpResponse.getProtocolDC().setBuffer(input,
                            parsingState.start, spaceIdx);

                    parsingState.start = -1;
                    parsingState.offset = spaceIdx;

                    parsingState.subState++;
                }

                case 1: { // skip spaces after the HTTP protocol
                    final int nonSpaceIdx =
                            skipSpaces(input, parsingState.offset, packetLimit);
                    if (nonSpaceIdx == -1) {
                        parsingState.offset = input.limit();
                        return false;
                    }

                    parsingState.start = nonSpaceIdx;
                    parsingState.offset = nonSpaceIdx + 1;
                    parsingState.subState++;
                }

                case 2 : { // parse the status code
                    if (parsingState.offset + 3 > input.limit()) {
                        return false;
                    }

                    httpResponse.setStatus(Ascii.parseInt(input,
                                                          parsingState.start,
                                                          3));

                    parsingState.start = -1;
                    parsingState.offset += 3;
                    parsingState.subState++;
                }

                case 3: { // skip spaces after the status code
                    final int nonSpaceIdx =
                            skipSpaces(input, parsingState.offset, packetLimit);
                    if (nonSpaceIdx == -1) {
                        parsingState.offset = input.limit();
                        return false;
                    }

                    parsingState.start = nonSpaceIdx;
                    parsingState.offset = nonSpaceIdx;
                    parsingState.subState++;
                }

                case 4: { // HTTP response reason-phrase
                    if (!findEOL(parsingState, input)) {
                        parsingState.offset = input.limit();
                        return false;
                    }
                    
                    httpResponse.getReasonPhraseRawDC().setBuffer(
                            input, parsingState.start,
                            parsingState.checkpoint);

                    parsingState.subState = 0;
                    parsingState.start = -1;
                    parsingState.checkpoint = -1;
                    onInitialLineParsed(httpResponse, ctx);
                    if (httpResponse.getStatus() == 100) {
                        // reset the parsing state in preparation to parse
                        // another initial line which represents the final
                        // response from the server after it has sent a
                        // 100-Continue.
                        parsingState.offset += 2;
                        parsingState.start = 0;
                        input.position(parsingState.offset);
                        input.shrink();
                        parsingState.offset = 0;
                        return false;
                    }
                    return true;
                }

                default: throw new IllegalStateException();
            }
        }
    }

    @Override
    Buffer encodeInitialLine(HttpPacket httpPacket, Buffer output, MemoryManager memoryManager) {
        final HttpRequestPacket httpRequest = (HttpRequestPacket) httpPacket;
        output = put(memoryManager, output, httpRequest.getMethodDC());
        output = put(memoryManager, output, Constants.SP);
        output = put(memoryManager, output, httpRequest.getRequestURIRef().getRequestURIBC());
        if (!httpRequest.getQueryStringDC().isNull()) {
            output = put(memoryManager, output, (byte) '?'); 
            output = put(memoryManager, output, httpRequest.getQueryStringDC());
        }
        output = put(memoryManager, output, Constants.SP);
        output = put(memoryManager, output, httpRequest.getProtocolString());

        return output;
    }

    private void prepareRequest(final HttpRequestPacket request) {
        String contentType = request.getContentType();
        if (contentType != null) {
            request.getHeaders().setValue(Header.ContentType).setString(contentType);
        }
    }

    private Queue<HttpRequestPacket> getRequestQueue(final Connection c) {

        Queue<HttpRequestPacket> q = httpRequestQueueAttr.get(c);
        if (q == null) {
            q = DataStructures.getLTQInstance(HttpRequestPacket.class);
            httpRequestQueueAttr.set(c, q);
        }
        return q;

    }

    private static final class ClientHttpResponseImpl
            extends HttpResponsePacket implements HttpPacketParsing {

        private static final ThreadCache.CachedTypeIndex<ClientHttpResponseImpl> CACHE_IDX =
                ThreadCache.obtainIndex(ClientHttpResponseImpl.class, 16);

        public static ClientHttpResponseImpl create() {
            final ClientHttpResponseImpl httpResponseImpl =
                    ThreadCache.takeFromCache(CACHE_IDX);
            if (httpResponseImpl != null) {
                return httpResponseImpl;
            }

            return new ClientHttpResponseImpl();
        }
        
        /**
         * Char encoding parsed flag.
         */
        private boolean charEncodingParsed = false;
        private boolean contentTypeParsed;
        
        private boolean isHeaderParsed;
        private final HttpCodecFilter.HeaderParsingState headerParsingState;
        private final HttpCodecFilter.ContentParsingState contentParsingState;

        private ClientHttpResponseImpl() {
            this.headerParsingState = new HttpCodecFilter.HeaderParsingState();
            this.contentParsingState = new HttpCodecFilter.ContentParsingState();
        }

        public void initialize(final HttpCodecFilter filter,
                final int initialOffset,
                final int maxHeaderSize,
                final int maxNumberOfHeaders) {
            headerParsingState.initialize(filter, initialOffset, maxHeaderSize);
            headers.setMaxNumHeaders(maxNumberOfHeaders);
            contentParsingState.trailerHeaders.setMaxNumHeaders(maxNumberOfHeaders);
        }

        @Override
        public String getCharacterEncoding() {
            if (characterEncoding != null || charEncodingParsed) {
                return characterEncoding;
            }

            getContentType(); // charEncoding is set as a side-effect of this call
            charEncodingParsed = true;

            return characterEncoding;
        }

        @Override
        public String getContentType() {
            if (!contentTypeParsed) {
                contentTypeParsed = true;

                if (contentType == null) {
                    final DataChunk dc = headers.getValue(Header.ContentType);

                    if (dc != null && !dc.isNull()) {
                        setContentType(dc.toString());
                    }
                }
            }
            
            return super.getContentType();
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
        public ProcessingState getProcessingState() {
            return getRequest().getProcessingState();
        }

        @Override
        public boolean isHeaderParsed() {
            return isHeaderParsed;
        }

        @Override
        public void setHeaderParsed(boolean isHeaderParsed) {
            this.isHeaderParsed = isHeaderParsed;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected void reset() {
            charEncodingParsed = false;
            contentTypeParsed = false;
            isHeaderParsed = false;
            headerParsingState.recycle();
            contentParsingState.recycle();
            super.reset();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void recycle() {
            if (getRequest().isExpectContent()) {
                return;
            }
            reset();
            ThreadCache.putToCache(CACHE_IDX, this);
        }
    }
}