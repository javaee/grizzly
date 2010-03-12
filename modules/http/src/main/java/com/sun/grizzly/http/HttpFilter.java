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

package com.sun.grizzly.http;

import com.sun.grizzly.Buffer;
import com.sun.grizzly.Connection;
import com.sun.grizzly.filterchain.BaseFilter;
import com.sun.grizzly.filterchain.FilterChainContext;
import com.sun.grizzly.filterchain.NextAction;
import com.sun.grizzly.http.core.HttpContent;
import com.sun.grizzly.http.core.HttpHeader;
import com.sun.grizzly.http.core.HttpPacket;
import com.sun.grizzly.http.core.HttpTrailer;
import com.sun.grizzly.http.util.Ascii;
import com.sun.grizzly.http.core.BufferChunk;
import com.sun.grizzly.http.util.HexUtils;
import com.sun.grizzly.memory.MemoryManager;
import com.sun.grizzly.http.util.MimeHeaders;
import com.sun.grizzly.memory.BufferUtils;
import java.io.IOException;

/**
 *
 * @author oleksiys
 */
public abstract class HttpFilter extends BaseFilter {
    public static final String HTTP_1_0 = "HTTP/1.0";
    public static final String HTTP_1_1 = "HTTP/1.1";

    public static final int DEFAULT_MAX_HEADERS_SIZE = 8192;

    private static final int MAX_CHUNK_SIZE_LENGTH = 16;

    abstract boolean decodeInitialLine(HttpPacketParsing httpPacket,
            ParsingState parsingState, Buffer input);

    abstract Buffer encodeInitialLine(HttpPacket httpPacket, Buffer output,
            MemoryManager memoryManager);

    abstract void onHttpPacketParsed(FilterChainContext ctx);

    protected final int maxHeadersSize;

    public HttpFilter(int maxHeadersSize) {
        this.maxHeadersSize = maxHeadersSize;
    }

    public final NextAction handleRead(FilterChainContext ctx,
            HttpPacketParsing httpPacket) throws IOException {
        
        Buffer input = (Buffer) ctx.getMessage();

        final boolean wasHeaderParsed = httpPacket.isHeaderParsed();

        if (!wasHeaderParsed) {
            if (!decodeHttpPacket(httpPacket, input)) {
                return ctx.getStopAction(input);
            } else {
                httpPacket.setHeaderParsed(true);
                httpPacket.getHeaderParsingState().recycle();
            }
        }

        Buffer remainder = null;

        final ContentParsingState contentParsingState =
                httpPacket.getContentParsingState();
        
        boolean isLast = false;

        final HttpHeader httpHeader = (HttpHeader) httpPacket;
        final boolean isChunked = httpHeader.isChunked();
        
        if (input.hasRemaining()) {
            if (isChunked) {
                // if it's chunked HTTP message
                final boolean isLastChunk = contentParsingState.isLastChunk;
                if (!isLastChunk && contentParsingState.chunkRemainder == 0) {
                    // We expect next chunk header
                    if (!parseHttpChunkLength(httpPacket, input)) {
                        // if we don't have enough data to parse chunk length - stop execution
                        return ctx.getStopAction(input);
                    }
                } else {
                    contentParsingState.chunkContentStart = 0;
                }

                int chunkContentStart =
                        contentParsingState.chunkContentStart;

                if (contentParsingState.chunkLength == 0) {
                    if (!isLastChunk) {
                        contentParsingState.isLastChunk = true;
                        initTrailerParsing(httpPacket);
                    }

                    if (!parseLastChunkTrailer(httpPacket, input)) {
                        return ctx.getStopAction(input);
                    }

                    chunkContentStart = httpPacket.getHeaderParsingState().offset;
                }

                final long thisPacketRemaining =
                        contentParsingState.chunkRemainder;
                final int contentAvailable = input.limit() - chunkContentStart;

                if (contentAvailable > thisPacketRemaining) {
                    remainder = input.slice(
                            (int) (chunkContentStart + thisPacketRemaining),
                            input.limit());
                    input.limit((int) (chunkContentStart + thisPacketRemaining));
                } else if (chunkContentStart > 0) {
                    input.position(chunkContentStart);
                }

                contentParsingState.chunkRemainder -= (input.limit() - chunkContentStart);

                if (isLastChunk) {
                    onHttpPacketParsed(ctx);
                    ctx.setMessage(((HttpHeader) httpPacket).httpTrailerBuilder().
                            headers(contentParsingState.trailerHeaders).build());
                    
                    return ctx.getInvokeAction(remainder);
                }
            } else {
                if (!wasHeaderParsed) {
                    contentParsingState.chunkRemainder = httpHeader.getContentLength();
                }

                // if it's fixed length HTTP message
                final long thisPacketRemaining = contentParsingState.chunkRemainder;
                final int available = input.remaining();

                if (available > thisPacketRemaining) {
                    remainder = input.slice(
                            (int) (input.position() + thisPacketRemaining), input.limit());
                    input.limit((int) (input.position() + thisPacketRemaining));
                }

                contentParsingState.chunkRemainder -= input.remaining();
                if (contentParsingState.chunkRemainder == 0) {
                    isLast = true;
                    onHttpPacketParsed(ctx);
                }
            }
        } else if (!isChunked && httpHeader.getContentLength() <= 0) { // If content wasn't parsed this time - check if we expect any
            isLast = true;
            onHttpPacketParsed(ctx);
        }

        final HttpContent.Builder builder = ((HttpHeader) httpPacket).httpContentBuilder();
        final HttpContent message = builder.content(input).last(isLast).build();
        ctx.setMessage(message);
        
        return ctx.getInvokeAction(remainder);
    }

    @Override
    public NextAction handleWrite(FilterChainContext ctx) throws IOException {
        final HttpPacket input = (HttpPacket) ctx.getMessage();
        final Connection connection = ctx.getConnection();

        final Buffer output = encodeHttpPacket(
                connection.getTransport().getMemoryManager(), input);

        ctx.setMessage(output);
        return ctx.getInvokeAction();
    }
    
    private void initTrailerParsing(HttpPacketParsing httpPacket) {
        final ParsingState headerParsingState =
                httpPacket.getHeaderParsingState();
        final ContentParsingState contentParsingState =
                httpPacket.getContentParsingState();

        headerParsingState.subState = 0;
        final int start = contentParsingState.chunkContentStart;
        headerParsingState.start = start;
        headerParsingState.offset = start;
        headerParsingState.packetLimit = start + maxHeadersSize;
    }

    private boolean parseLastChunkTrailer(HttpPacketParsing httpPacket,
            Buffer input) {
        final ParsingState headerParsingState =
                httpPacket.getHeaderParsingState();
        final ContentParsingState contentParsingState =
                httpPacket.getContentParsingState();

        return parseHeaders(null, contentParsingState.trailerHeaders,
                headerParsingState, input);
    }

    private boolean parseHttpChunkLength(HttpPacketParsing httpPacket,
            Buffer input) {
        final ParsingState parsingState = httpPacket.getHeaderParsingState();

        while (true) {
            _1:
            switch (parsingState.state) {
                case 0: {// Initialize chunk parsing
                    final int pos = input.position();
                    parsingState.start = pos;
                    parsingState.offset = pos;
                    parsingState.packetLimit = pos + MAX_CHUNK_SIZE_LENGTH;
                    parsingState.state = 1;
                }

                case 1: { // Scan chunk size
                    int offset = parsingState.offset;
                    int limit = Math.min(parsingState.packetLimit, input.limit());
                    long value = parsingState.parsingNumericValue;

                    while (offset < limit) {
                        final byte b = input.get(offset);
                        if (b == Constants.CR || b == Constants.SEMI_COLON) {
                            parsingState.checkpoint = offset;
                        } else if (b == Constants.LF) {
                            final ContentParsingState contentParsingState =
                                    httpPacket.getContentParsingState();
                            contentParsingState.chunkContentStart = offset + 1;
                            contentParsingState.chunkLength = value;
                            contentParsingState.chunkRemainder = value;
                            parsingState.state = 2;

                            return true;
                        } else if (parsingState.checkpoint == -1) {
                            value = value * 16 + (HexUtils.DEC[b]);
                        } else {
                            throw new IllegalStateException("Unexpected HTTP chunk header");
                        }

                        offset++;
                    }

                    parsingState.parsingNumericValue = value;
                    parsingState.offset = offset;
                    parsingState.checkOverflow();
                    return false;

                }

                case 2: { // skip CRLF
                    while (input.hasRemaining()) {
                        if (input.get() == Constants.LF) {
                            parsingState.recycle();
                            if (input.hasRemaining()) {
                                break _1;
                            }

                            return false;
                        }
                    }

                    return false;
                }
            }
        }
    }
    
    protected boolean decodeHttpPacket(HttpPacketParsing httpPacket, Buffer input) {

        final ParsingState parsingState = httpPacket.getHeaderParsingState();

        switch (parsingState.state) {
            case 0: { // parsing initial line
                if (!decodeInitialLine(httpPacket, parsingState, input)) {
                    parsingState.checkOverflow();
                    return false;
                }

                parsingState.state++;
            }

            case 1: { // parsing headers
                if (!parseHeaders((HttpHeader) httpPacket,
                        httpPacket.getHeaders(), parsingState, input)) {
                    parsingState.checkOverflow();
                    return false;
                }

                parsingState.state++;
            }

            case 2: { // Headers are ready
                input.position(parsingState.offset);
                return true;
            }

            default: throw new IllegalStateException();
        }
    }

    protected Buffer encodeHttpPacket(MemoryManager memoryManager,
            HttpPacket input) {

        final boolean isHeader = input.isHeader();
        final HttpContent httpContent;
        final HttpHeader httpHeader;
        if (isHeader) {
            httpContent = null;
            httpHeader = (HttpHeader) input;
        } else {
            httpContent = (HttpContent) input;
            httpHeader = httpContent.getHttpHeader();
        }


        Buffer encodedBuffer = null;
        
        if (!httpHeader.isCommited()) {
            encodedBuffer = memoryManager.allocate(8192);

            encodedBuffer = encodeInitialLine(httpHeader, encodedBuffer, memoryManager);
            encodedBuffer = put(memoryManager, encodedBuffer, Constants.CRLF_BYTES);

            checkKnownHeaders(httpHeader, httpContent);
            
            final MimeHeaders mimeHeaders = httpHeader.getHeaders();
            encodedBuffer = encodeMimeHeaders(memoryManager, encodedBuffer, mimeHeaders);

            encodedBuffer = put(memoryManager, encodedBuffer, Constants.CRLF_BYTES);
            encodedBuffer.trim();
            encodedBuffer.allowBufferDispose(true);
            
            httpHeader.setCommited(true);
        }

        if (!isHeader) {
            final boolean isChunked = httpHeader.isChunked();
            final boolean isLastChunk = httpContent.isLast();
            
            final Buffer content = httpContent.getContent();

            if (content != null &&
                    (content.hasRemaining() || (isChunked && isLastChunk))) {
                
                if (isChunked) {
                    final Buffer chunkBuffer = encodeHttpChunk(memoryManager,
                            httpContent, isLastChunk);

                    encodedBuffer = BufferUtils.appendBuffers(memoryManager,
                            encodedBuffer, chunkBuffer);
                } else {
                    encodedBuffer = BufferUtils.appendBuffers(memoryManager,
                            encodedBuffer, content);
                }
                
                if (encodedBuffer.isComposite()) {
                    // If during buffer appending - composite buffer was created -
                    // allow buffer disposing
                    encodedBuffer.allowBufferDispose(true);
                }
            }
        }

        return encodedBuffer;
    }

    private Buffer encodeHttpChunk(MemoryManager memoryManager,
            HttpContent httpContent, boolean isLastChunk) {
        final Buffer content = httpContent.getContent();
        
        Buffer httpChunkBuffer = memoryManager.allocate(16);
        final int chunkSize = content.remaining();
        
        Ascii.intToHexString(httpChunkBuffer, chunkSize);
        httpChunkBuffer = put(memoryManager, httpChunkBuffer,
                Constants.CRLF_BYTES);
        httpChunkBuffer.trim();
        httpChunkBuffer.allowBufferDispose(true);

        final boolean hasContent = chunkSize > 0;
        
        if (hasContent) {
            httpChunkBuffer = BufferUtils.appendBuffers(memoryManager,
                    httpChunkBuffer, content);
        }
        
        Buffer httpChunkTrailer = memoryManager.allocate(256);

        if (!isLastChunk) {
            httpChunkTrailer = put(memoryManager, httpChunkTrailer,
                    Constants.CRLF_BYTES);
        } else {
            if (hasContent) {
                httpChunkTrailer = put(memoryManager, httpChunkTrailer,
                        Constants.CRLF_BYTES);
                httpChunkTrailer = put(memoryManager, httpChunkTrailer,
                        Constants.LAST_CHUNK_CRLF_BYTES);
            }
            
            final HttpTrailer httpTrailer = (HttpTrailer) httpContent;
            final MimeHeaders mimeHeaders = httpTrailer.getHeaders();
            httpChunkTrailer = encodeMimeHeaders(memoryManager,
                    httpChunkTrailer, mimeHeaders);
            
            httpChunkTrailer = put(memoryManager, httpChunkTrailer,
                    Constants.CRLF_BYTES);
        }

        httpChunkTrailer.trim();
        httpChunkTrailer.allowBufferDispose(true);

        return BufferUtils.appendBuffers(memoryManager, httpChunkBuffer,
                httpChunkTrailer);
    }

    protected void checkKnownHeaders(HttpHeader httpHeader,
            HttpContent httpContent) {
        
        final MimeHeaders mimeHeaders = httpHeader.getHeaders();
        
        if (httpHeader.isChunked()) {
            if (mimeHeaders.getValue(Constants.TRANSFER_ENCODING_HEADER) == null) {
                mimeHeaders.setValue(Constants.TRANSFER_ENCODING_HEADER).setString(
                        Constants.CHUNKED_ENCODING);
            }
        } else {
            if (mimeHeaders.getValue(Constants.CONTENT_LENGTH_HEADER) == null) {
                long contentLength = httpHeader.getContentLength();
                if (contentLength == -1) {
                    if (httpContent != null && httpContent.getContent().hasRemaining()) {
                        contentLength = httpContent.getContent().remaining();
                    }
                }

                if (contentLength != -1) {
                    mimeHeaders.setValue(Constants.CONTENT_LENGTH_HEADER).setString(
                            Long.toString(contentLength));
                }
            }
        }
    }
    
    private Buffer encodeMimeHeaders(MemoryManager memoryManager,
            Buffer buffer, MimeHeaders mimeHeaders) {
        final int mimeHeadersNum = mimeHeaders.size();

        for (int i = 0; i < mimeHeadersNum; i++) {
            buffer = put(memoryManager, buffer,
                    mimeHeaders.getName(i));

            buffer = put(memoryManager, buffer,
                    Constants.COLON_BYTES);

            buffer = put(memoryManager, buffer,
                    mimeHeaders.getValue(i));

            buffer = put(memoryManager, buffer, Constants.CRLF_BYTES);
        }

        return buffer;
    }
    
    protected static final boolean parseHeaders(HttpHeader httpHeader,
            MimeHeaders mimeHeaders, ParsingState parsingState, Buffer input) {
        
        do {
            if (parsingState.subState == 0) {
                final int eol = checkEOL(parsingState, input);
                if (eol == 0) { // EOL
                    return true;
                } else if (eol == -2) { // not enough data
                    return false;
                }

                final boolean parseKnownHeaders = (httpHeader != null);
                parsingState.isTransferEncodingHeader = parseKnownHeaders;
                parsingState.isContentLengthHeader = parseKnownHeaders;
            }

            if (!parseHeader(httpHeader, mimeHeaders, parsingState, input)) {
                return false;
            }

        } while (true);
    }

    protected static final boolean parseHeader(HttpHeader httpHeader,
            MimeHeaders mimeHeaders, ParsingState parsingState, Buffer input) {
        
        int subState = parsingState.subState;

        while (true) {
            switch (subState) {
                case 0: { // start to parse the header
                    parsingState.start = parsingState.offset;
                    parsingState.subState++;
                }
                case 1: { // parse header name
                    if (!parseHeaderName(mimeHeaders, parsingState, input)) {
                        return false;
                    }

                    parsingState.subState++;
                    parsingState.start = -1;
                }

                case 2: { // skip value preceding spaces
                    final int nonSpaceIdx = skipSpaces(input, parsingState.offset, parsingState.packetLimit);
                    if (nonSpaceIdx == -1) {
                        parsingState.offset = input.limit();
                        return false;
                    }

                    parsingState.subState++;
                    parsingState.offset = nonSpaceIdx;

                    if (parsingState.start == -1) { // Starting to parse header (will be called only for the first line of the multi line header)
                        parsingState.start = nonSpaceIdx;
                        parsingState.checkpoint = nonSpaceIdx;
                        parsingState.checkpoint2 = nonSpaceIdx;
                    }
                }

                case 3: { // parse header value
                    final int result = parseHeaderValue(httpHeader, parsingState, input);
                    if (result == -1) {
                        return false;
                    } else if (result == -2) {
                        break;
                    }

                    parsingState.subState = 0;
                    parsingState.start = -1;

                    return true;
                }

                default:
                    throw new IllegalStateException();
            }
        }
    }

    protected static final boolean parseHeaderName(MimeHeaders mimeHeaders,
            ParsingState parsingState, Buffer input) {
        final int limit = Math.min(input.limit(), parsingState.packetLimit);
        int start = parsingState.start;
        int offset = parsingState.offset;

        while(offset < limit) {
            byte b = input.get(offset);
            if (b == Constants.COLON) {
                final BufferChunk valueChunk = mimeHeaders.addValue(
                        input, parsingState.start, offset);

                parsingState.headerValueStorage = valueChunk;
                parsingState.offset = offset + 1;
                finalizeKnownHeaderNames(parsingState, offset - start);

                return true;
            } else if ((b >= Constants.A) && (b <= Constants.Z)) {
                b -= Constants.LC_OFFSET;
                input.put(offset, b);
            }

            checkKnownHeaderNames(parsingState, b, offset-start);

            offset++;
        }

        parsingState.offset = offset;
        return false;
    }

    protected static final int parseHeaderValue(HttpHeader httpHeader,
            ParsingState parsingState, Buffer input) {
        
        final int limit = Math.min(input.limit(), parsingState.packetLimit);
        
        final int start = parsingState.start;
        int offset = parsingState.offset;

        while(offset < limit) {
            final byte b = input.get(offset);
            if (b == Constants.CR) {
            } else if (b == Constants.LF) {
                // Check if it's not multi line header
                if (offset + 1 < limit) {
                    final byte b2 = input.get(offset + 1);
                    if (b2 == Constants.SP || b2 == Constants.HT) {
                        finalizeKnownHeaderValues(parsingState);
                        input.put(parsingState.checkpoint++, b2);
                        parsingState.offset = offset + 2;
                        return -2;
                    } else {
                        parsingState.offset = offset + 1;
                        parsingState.headerValueStorage.setBuffer(input,
                                parsingState.start, parsingState.checkpoint2);
                        return 0;
                    }
                }

                parsingState.offset = offset;
                return -1;
            } else if (b == Constants.SP) {
                finalizeKnownHeaderValues(parsingState);
                
                input.put(parsingState.checkpoint++, b);
            } else {
                checkKnownHeaderValues(httpHeader, parsingState, b);
                
                input.put(parsingState.checkpoint++, b);
                parsingState.checkpoint2 = parsingState.checkpoint;
            }

            offset++;
        }

        parsingState.offset = offset;
        return -1;
    }

    private static void checkKnownHeaderNames(ParsingState parsingState,
            byte b, int idx) {
        if (parsingState.isContentLengthHeader) {
            parsingState.isContentLengthHeader =
                    (idx < Constants.CONTENT_LENGTH_HEADER_BYTES.length)
                    && b == Constants.CONTENT_LENGTH_HEADER_BYTES[idx];

        }

        if (parsingState.isTransferEncodingHeader) {
            parsingState.isTransferEncodingHeader =
                    (idx < Constants.TRANSFER_ENCODING_HEADER_BYTES.length)
                    && b == Constants.TRANSFER_ENCODING_HEADER_BYTES[idx];
        }
    }

    private static void finalizeKnownHeaderNames(ParsingState parsingState, int size) {
        if (parsingState.isContentLengthHeader) {
            parsingState.isContentLengthHeader =
                    (size == Constants.CONTENT_LENGTH_HEADER_BYTES.length);
        } else if (parsingState.isTransferEncodingHeader) {
            parsingState.isTransferEncodingHeader =
                    (size == Constants.TRANSFER_ENCODING_HEADER_BYTES.length);
        }
    }

    private static void checkKnownHeaderValues(HttpHeader httpHeader,
            ParsingState parsingState, byte b) {
        if (parsingState.isContentLengthHeader) {
            if (Ascii.isDigit(b)) {
                parsingState.parsingNumericValue =
                        parsingState.parsingNumericValue * 10 + (b - '0');
                httpHeader.setContentLength(parsingState.parsingNumericValue);
            } else {
                throw new IllegalStateException("Content-length value is not digital");
            }
        } else if (parsingState.isTransferEncodingHeader) {
            final int idx = parsingState.checkpoint - parsingState.start;
            if (idx < Constants.CHUNKED_ENCODING_BYTES.length) {
                parsingState.isTransferEncodingHeader = (b == Constants.CHUNKED_ENCODING_BYTES[idx]);
                if (idx == Constants.CHUNKED_ENCODING_BYTES.length - 1
                        && parsingState.isTransferEncodingHeader) {
                    httpHeader.setChunked(true);
                    parsingState.isTransferEncodingHeader = false;
                }
            }
        }
    }

    private static void finalizeKnownHeaderValues(ParsingState parsingState) {
        parsingState.isTransferEncodingHeader = false;
    }

    protected static final int checkEOL(ParsingState parsingState, Buffer input) {
        final int offset = parsingState.offset;
        final int avail = input.limit() - offset;

        final byte b1;
        final byte b2;

        if (avail >= 2) { // if more than 2 bytes available
            final short s = input.getShort(offset);
            b1 = (byte) (s >>> 8);
            b2 = (byte) (s & 0xFF);
        } else if (avail == 1) {  // if one byte available
            b1 = input.get(offset);
            b2 = -1;
        } else {
            return -2;
        }

        if (b1 == Constants.CR) {
            if (b2 == Constants.LF) {
                parsingState.offset += 2;
                return 0;
            } else if (b2 == -1) {
                return -2;
            }
        } else if (b1 == Constants.LF) {
            parsingState.offset++;
            return 0;
        }

        return -1;
    }

    protected static final boolean findEOL(ParsingState state, Buffer input) {
        int offset = state.offset;
        final int limit = Math.min(input.limit(), state.packetLimit);

        while(offset < limit) {
            final byte b = input.get(offset);
            if (b == Constants.CR) {
                state.checkpoint = offset;
            } else if (b == Constants.LF) {
                if (state.checkpoint == -1) {
                    state.checkpoint = offset;
                }

                state.offset = offset + 1;
                return true;
            }

            offset++;
        }

        return false;
    }

    protected static final int findSpace(Buffer input, int offset, int packetLimit) {
        final int limit = Math.min(input.limit(), packetLimit);
        while(offset < limit) {
            final byte b = input.get(offset);
            if (b == Constants.SP || b == Constants.HT) {
                return offset;
            }

            offset++;
        }

        return -1;
    }

    protected static final int skipSpaces(Buffer input, int offset, int packetLimit) {
        final int limit = Math.min(input.limit(), packetLimit);
        while(offset < limit) {
            final byte b = input.get(offset);
            if (b != Constants.SP && b != Constants.HT) {
                return offset;
            }

            offset++;
        }

        return -1;
    }

    protected static final int indexOf(Buffer input, int offset, byte b, int packetLimit) {
        final int limit = Math.min(input.limit(), packetLimit);
        while(offset < limit) {
            final byte currentByte = input.get(offset);
            if (currentByte == b) {
                return offset;
            }

            offset++;
        }

        return -1;
    }

    protected static Buffer put(MemoryManager memoryManager,
            Buffer headerBuffer, BufferChunk bufferChunk) {

        if (bufferChunk.hasBuffer()) {
            final int length = bufferChunk.getEnd() - bufferChunk.getStart();
            if (headerBuffer.remaining() < length) {
                headerBuffer =
                        resizeBuffer(memoryManager, headerBuffer, length);
            }

            headerBuffer.put(bufferChunk.getBuffer(), bufferChunk.getStart(),
                    length);
        } else {
            byte[] bytes = bufferChunk.toString().getBytes(Constants.ASCII_CHARSET);
            if (headerBuffer.remaining() < bytes.length) {
                headerBuffer =
                        resizeBuffer(memoryManager, headerBuffer, bytes.length);
            }

            headerBuffer.put(bytes, 0, bytes.length);
        }

        return headerBuffer;
    }

    protected static Buffer put(MemoryManager memoryManager,
            Buffer headerBuffer, byte[] array) {

        if (headerBuffer.remaining() < array.length) {
            headerBuffer =
                    resizeBuffer(memoryManager, headerBuffer, array.length);
        }

        headerBuffer.put(array);

        return headerBuffer;
    }

    protected static Buffer put(MemoryManager memoryManager,
            Buffer headerBuffer, byte value) {

        if (!headerBuffer.hasRemaining()) {
            headerBuffer =
                    resizeBuffer(memoryManager, headerBuffer, 1);
        }

        headerBuffer.put(value);

        return headerBuffer;
    }

    protected static Buffer resizeBuffer(MemoryManager memoryManager,
            Buffer headerBuffer, int grow) {

        return memoryManager.reallocate(headerBuffer, Math.max(
                headerBuffer.capacity() + grow,
                (headerBuffer.capacity() * 3) / 2 + 1));
    }

    protected static final class ParsingState {
        public int packetLimit;

        public int state;
        public int subState;

        public int start;
        public int offset;
        public int checkpoint = -1; // extra parsing state field
        public int checkpoint2 = -1; // extra parsing state field

        public BufferChunk headerValueStorage;

        public long parsingNumericValue;

        public boolean isContentLengthHeader;
        public boolean isTransferEncodingHeader;

        public ParsingState(int initialOffset, int maxHeaderSize) {
            offset = initialOffset;
            packetLimit = offset + maxHeaderSize;
        }

        public void set(int state, int subState, int start, int offset) {
            this.state = state;
            this.subState = subState;
            this.start = start;
            this.offset = offset;
        }

        public void recycle() {
            state = 0;
            subState = 0;
            start = 0;
            offset = 0;
            checkpoint = -1;
            checkpoint2 = -1;
            headerValueStorage = null;
            parsingNumericValue = 0;
            isTransferEncodingHeader = false;
            isContentLengthHeader = false;
        }

        public final void checkOverflow() {
            if (offset < packetLimit) return;

            throw new IllegalStateException("HTTP packet is too long");
        }
    }

    protected static final class ContentParsingState {
        public boolean isLastChunk;
        public int chunkContentStart;
        public long chunkLength;
        public long chunkRemainder;
        public MimeHeaders trailerHeaders = new MimeHeaders();

        public void recycle() {
            isLastChunk = false;
            chunkContentStart = -1;
            chunkLength = -1;
            chunkRemainder = 0;
            trailerHeaders.clear();
        }
    }
}
