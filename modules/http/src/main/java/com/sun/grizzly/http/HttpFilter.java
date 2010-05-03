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
import com.sun.grizzly.http.TransferEncoding.ParsingResult;
import com.sun.grizzly.http.util.Ascii;
import com.sun.grizzly.http.util.BufferChunk;
import com.sun.grizzly.http.util.CacheableBufferChunk;
import com.sun.grizzly.memory.MemoryManager;
import com.sun.grizzly.http.util.MimeHeaders;
import com.sun.grizzly.memory.BufferUtils;
import java.io.IOException;
import java.util.Arrays;

/**
 * The {@link com.sun.grizzly.filterchain.Filter}, responsible for transforming {@link Buffer} into
 * {@link HttpPacket} and vice versa in asynchronous mode.
 * When the <tt>HttpFilter</tt> is added to a {@link com.sun.grizzly.filterchain.FilterChain}, on read phase
 * it consumes incoming {@link Buffer} and provides {@link HttpContent} as
 * the result of transformation. On write phase the <tt>HttpFilter</tt> consumes
 * input {@link HttpPacket} and serializes it to a {@link Buffer}, which
 * gets passed farther as the result of transformation.
 * So transformations, provided by this filter are following:
 * (read phase): {@link Buffer} -> {@link HttpContent}
 * (write phase): {@link HttpPacket} -> {@link Buffer}.
 *
 * @see HttpServerFilter
 * @see HttpClientFilter
 * 
 * @author Alexey Stashok
 */
public abstract class HttpFilter extends BaseFilter {
    public static final String HTTP_1_0 = "HTTP/1.0";
    public static final String HTTP_1_1 = "HTTP/1.1";

    public static final int DEFAULT_MAX_HTTP_PACKET_HEADER_SIZE = 8192;

    private volatile TransferEncoding[] transferEncodings;
    
    private final Object encodingSync = new Object();
    
    /**
     * Method is responsible for parsing initial line of HTTP message (different
     * for {@link HttpRequestPacket} and {@link HttpResponsePacket}).
     *
     * @param httpPacket HTTP packet, which is being parsed
     * @param parsingState HTTP packet parsing state
     * @param input input {@link Buffer}
     *
     * @return <tt>true</tt>, if initial line has been parsed,
     * or <tt>false</tt> otherwise.
     */
    abstract boolean decodeInitialLine(HttpPacketParsing httpPacket,
            ParsingState parsingState, Buffer input);

    /**
     * Method is responsible for serializing initial line of HTTP message (different
     * for {@link HttpRequestPacket} and {@link HttpResponsePacket}).
     *
     * @param httpPacket HTTP packet, which is being serialized
     * @param output output {@link Buffer}
     * @param memoryManager {@link MemoryManager}
     *
     * @return result {@link Buffer}.
     */
    abstract Buffer encodeInitialLine(HttpPacket httpPacket, Buffer output,
            MemoryManager memoryManager);

    /**
     * Callback method, called when {@link HttpPacket} parsing has been completed.
     * @param ctx processing context.
     */
    abstract void onHttpPacketParsed(FilterChainContext ctx);

    protected final int maxHeadersSize;

    /**
     * Constructor, which creates <tt>HttpFilter</tt> instance, with the specific
     * max header size parameter.
     *
     * @param maxHeadersSize the maximum size of the HTTP message header.
     */
    public HttpFilter(int maxHeadersSize) {
        this.maxHeadersSize = maxHeadersSize;
        transferEncodings = new TransferEncoding[] {
            new FixedLengthTransferEncoding(), new ChunkedTransferEncoding(maxHeadersSize)};
    }

    public boolean addTransferEncoding(TransferEncoding transferEncoding) {
        synchronized (encodingSync) {
            if (indexOf(transferEncodings, transferEncoding) == -1) {
                TransferEncoding[] encodings = Arrays.copyOf(
                        transferEncodings, transferEncodings.length + 1);
                encodings[transferEncodings.length] = transferEncoding;
                transferEncodings = encodings;

                return true;
            }

            return false;
        }
    }

    public boolean removeTransferEncoding(TransferEncoding transferEncoding) {
        synchronized (encodingSync) {
            final int idx = indexOf(transferEncodings, transferEncoding);
            if (idx != -1) {
                TransferEncoding[] encodings =
                        new TransferEncoding[transferEncodings.length - 1];
                if (idx > 0) {
                    System.arraycopy(transferEncodings, 0, encodings, 0, idx);
                }

                final int bytesToCopy2 = transferEncodings.length - idx - 1;
                if (bytesToCopy2 > 0) {
                    System.arraycopy(transferEncodings, idx + 1, encodings, idx, bytesToCopy2);
                }

                transferEncodings = encodings;

                return true;
            }

            return false;
        }
    }

    /**
     * The method is called by the specific <tt>HttpFilter</tt> implementation,
     * once we have received a {@link Buffer}, which has to be transformed
     * into HTTP packet part.
     *
     * Filter gets {@link Buffer}, which represents a part or complete HTTP
     * message. As the result of "read" transformation - we will get
     * {@link HttpContent} message, which will represent HTTP packet content
     * (might be zero length content) and reference to a {@link HttpHeader},
     * which contains HTTP message header.
     *
     * @param ctx Request processing context
     * @param httpPacket the current HttpPacket, which is being processed.
     *
     * @return {@link NextAction}
     * @throws IOException
     */
    public final NextAction handleRead(FilterChainContext ctx,
            HttpPacketParsing httpPacket) throws IOException {
        
        // Get the input buffer
        Buffer input = (Buffer) ctx.getMessage();

        // Check if HTTP header has been parsed
        final boolean wasHeaderParsed = httpPacket.isHeaderParsed();

        if (!wasHeaderParsed) {
            // if header wasn't parsed - parse
            if (!decodeHttpPacket(httpPacket, input)) {
                // if there is not enough data to parse the HTTP header - stop
                // filterchain processing
                return ctx.getStopAction(input);
            } else {
                // if headers get parsed - set the flag
                httpPacket.setHeaderParsed(true);
                // recycle header parsing state
                httpPacket.getHeaderParsingState().recycle();

                setTransferEncoding((HttpHeader) httpPacket);
            }
        }

        final HttpHeader httpHeader = (HttpHeader) httpPacket;
        final TransferEncoding encoding = httpHeader.getTransferEncoding();

        // Check if appropriate HTTP content encoder was found
        if (encoding != null) {

            final ParsingResult result = parsePacket(ctx.getConnection(), httpHeader, input);

            final HttpContent httpContent = result.getHttpContent();
            final Buffer remainderBuffer = result.getRemainderBuffer();

            result.recycle();

            if (httpContent != null) {
                if (httpContent.isLast()) {
                    onHttpPacketParsed(ctx);
                }

                ctx.setMessage(httpContent);

                // Instruct filterchain to continue the processing.
                return ctx.getInvokeAction(remainderBuffer);
            } else {
                return ctx.getStopAction(remainderBuffer);
            }


        } else if (!httpHeader.isChunked() && httpHeader.getContentLength() <= 0) {
            // If content is not present this time - check if we expect any
            onHttpPacketParsed(ctx);

            // Build HttpContent message on top of existing content chunk and parsed Http message header
            final HttpContent.Builder builder = ((HttpHeader) httpPacket).httpContentBuilder();
            final HttpContent message = builder.content(input).last(true).build();
            ctx.setMessage(message);

            // Instruct filterchain to continue the processing.
            return ctx.getInvokeAction(null);
        }

        throw new IllegalStateException("Error parsing HTTP packet: " + httpHeader);
    }

    /**
     * The method is called, once we need to serialize a {@link HttpPacket},
     * which may represent HTTP packet header, content or content chunk.
     *
     * Filter gets {@link HttpPacket}, which represents a HTTP header, content,
     * or content part. As the result of "write" transformation - we will get
     * {@link Buffer}, which will represent serialized HTTP packet.
     *
     * @param ctx Request processing context
     *
     * @return {@link NextAction}
     * @throws IOException
     */
    @Override
    public NextAction handleWrite(FilterChainContext ctx) throws IOException {
        // Get HttpPacket
        final HttpPacket input = (HttpPacket) ctx.getMessage();
        // Get Connection
        final Connection connection = ctx.getConnection();

        // transform HttpPacket into Buffer
        final Buffer output = encodeHttpPacket(connection, input);

        ctx.setMessage(output);
        // Invoke next filter in the chain.
        return ctx.getInvokeAction();
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

    protected Buffer encodeHttpPacket(Connection connection, HttpPacket input) {
        final MemoryManager memoryManager = connection.getTransport().getMemoryManager();
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

            encodedBuffer = encodeKnownHeaders(memoryManager, encodedBuffer,
                    httpHeader, httpContent);
            
            final MimeHeaders mimeHeaders = httpHeader.getHeaders();
            encodedBuffer = encodeMimeHeaders(memoryManager, encodedBuffer, mimeHeaders);

            encodedBuffer = put(memoryManager, encodedBuffer, Constants.CRLF_BYTES);
            encodedBuffer.trim();
            encodedBuffer.allowBufferDispose(true);
            
            httpHeader.setCommited(true);

            setTransferEncoding(httpHeader);
        }

        if (!isHeader) {
            final TransferEncoding contentEncoder = httpHeader.getTransferEncoding();

            if (contentEncoder != null) {
                final Buffer content = serializePacket(connection, httpContent);
                encodedBuffer = BufferUtils.appendBuffers(memoryManager,
                        encodedBuffer, content);

                if (encodedBuffer.isComposite()) {
                    // If during buffer appending - composite buffer was created -
                    // allow buffer disposing
                    encodedBuffer.allowBufferDispose(true);
                }
            } else {
                throw new IllegalStateException("Error serializing HTTP packet: " + httpHeader);
            }
        }

        return encodedBuffer;
    }

    protected Buffer encodeKnownHeaders(MemoryManager memoryManager,
            Buffer buffer, HttpHeader httpHeader, HttpContent httpContent) {
        
        final CacheableBufferChunk name = CacheableBufferChunk.create();
        final CacheableBufferChunk value = CacheableBufferChunk.create();

        if (httpHeader.isChunked()) {
            name.setString(Constants.TRANSFER_ENCODING_HEADER);
            httpHeader.extractTransferEncoding(value);
        } else {
            name.setString(Constants.CONTENT_LENGTH_HEADER);
            httpHeader.extractContentLength(value);
            if (value.isNull() && httpContent != null
                    && httpContent.getContent().hasRemaining()) {
                final int contentLength = httpContent.getContent().remaining();
                httpHeader.setContentLength(contentLength);
                value.setString(Integer.toString(contentLength));
            }
        }

        if (!value.isNull()) {
            buffer = encodeMimeHeader(memoryManager, buffer, name, value);
        }

        name.reset();
        value.reset();
        
        name.setString(Constants.CONTENT_TYPE_HEADER);
        httpHeader.extractContentType(value);

        if (!value.isNull()) {
            buffer = encodeMimeHeader(memoryManager, buffer, name, value);
        }
        
        name.recycle();
        value.recycle();

        return buffer;
    }
    
    protected static Buffer encodeMimeHeaders(MemoryManager memoryManager,
            Buffer buffer, MimeHeaders mimeHeaders) {
        final int mimeHeadersNum = mimeHeaders.size();

        for (int i = 0; i < mimeHeadersNum; i++) {
            if (!mimeHeaders.getAndSetSerialized(i, true)) {
                buffer = encodeMimeHeader(memoryManager, buffer,
                        mimeHeaders.getName(i), mimeHeaders.getValue(i));
            }
        }

        return buffer;
    }

    protected static Buffer encodeMimeHeader(MemoryManager memoryManager,
            Buffer buffer, BufferChunk name, BufferChunk value) {

        buffer = put(memoryManager, buffer, name);
        buffer = put(memoryManager, buffer, Constants.COLON_BYTES);
        buffer = put(memoryManager, buffer, value);
        buffer = put(memoryManager, buffer, Constants.CRLF_BYTES);

        return buffer;
    }
    
    protected static boolean parseHeaders(HttpHeader httpHeader,
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

    protected static boolean parseHeader(HttpHeader httpHeader,
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

    protected static boolean parseHeaderName(MimeHeaders mimeHeaders,
            ParsingState parsingState, Buffer input) {
        final int limit = Math.min(input.limit(), parsingState.packetLimit);
        int start = parsingState.start;
        int offset = parsingState.offset;

        while(offset < limit) {
            byte b = input.get(offset);
            if (b == Constants.COLON) {

                parsingState.headerValueStorage =
                        mimeHeaders.addValue(input, parsingState.start, offset);
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

    protected static int parseHeaderValue(HttpHeader httpHeader,
            ParsingState parsingState, Buffer input) {
        
        final int limit = Math.min(input.limit(), parsingState.packetLimit);
        
        int offset = parsingState.offset;

        final boolean hasShift = (offset != parsingState.checkpoint);
        
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

                if (hasShift) {
                    input.put(parsingState.checkpoint++, b);
                } else {
                    parsingState.checkpoint++;
                }
            } else {
                checkKnownHeaderValues(httpHeader, parsingState, b);
                
                if (hasShift) {
                    input.put(parsingState.checkpoint++, b);
                } else {
                    parsingState.checkpoint++;
                }
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

    protected static int checkEOL(ParsingState parsingState, Buffer input) {
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

    protected static boolean findEOL(ParsingState state, Buffer input) {
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

        state.offset = offset;
        
        return false;
    }

    protected static int findSpace(Buffer input, int offset, int packetLimit) {
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

    protected static int skipSpaces(Buffer input, int offset, int packetLimit) {
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

    protected static int indexOf(Buffer input, int offset, byte b, int packetLimit) {
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
            final String s = bufferChunk.toString();
            final int size = s.length();
            if (headerBuffer.remaining() < size) {
                headerBuffer =
                        resizeBuffer(memoryManager, headerBuffer, size);
            }

            for(int i = 0; i < size; i++) {
                headerBuffer.put((byte) s.charAt(i));
            }
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

    @SuppressWarnings({"unchecked"})
    protected static Buffer resizeBuffer(MemoryManager memoryManager,
            Buffer headerBuffer, int grow) {

        return memoryManager.reallocate(headerBuffer, Math.max(
                headerBuffer.capacity() + grow,
                (headerBuffer.capacity() * 3) / 2 + 1));
    }

    private static int indexOf(Object[] array, Object element) {
        for (int i = 0; i < array.length; i++) {
            if (array[i].equals(element)) {
                return i;
            }
        }

        return -1;
    }

    final void setTransferEncoding(HttpHeader httpHeader) {
        final TransferEncoding[] encodings = transferEncodings;
        if (httpHeader.isRequest()) {
            HttpRequestPacket httpRequestPacket = (HttpRequestPacket) httpHeader;
            for (TransferEncoding encoding : encodings) {
                if (encoding.isEncodeRequest(httpRequestPacket)) {
                    httpRequestPacket.setTransferEncoding(encoding);
                    return;
                }
            }
        } else {
            HttpResponsePacket httpResponsePacket = (HttpResponsePacket) httpHeader;
            for (TransferEncoding encoding : encodings) {
                if (encoding.isEncodeResponse(httpResponsePacket)) {
                    httpResponsePacket.setTransferEncoding(encoding);
                    return;
                }
            }
        }
    }

    private ParsingResult parsePacket(Connection connection,
            HttpHeader httpHeader, Buffer input) {
        final TransferEncoding encoding = httpHeader.getTransferEncoding();
        if (httpHeader.isRequest()) {
            return encoding.parseRequest(connection, (HttpRequestPacket) httpHeader, input);
        } else {
            return encoding.parseResponse(connection, (HttpResponsePacket) httpHeader, input);
        }
    }

    private Buffer serializePacket(Connection connection, HttpContent httpContent) {
        final HttpHeader httpHeader = httpContent.getHttpHeader();
        final TransferEncoding encoding = httpHeader.getTransferEncoding();
        if (httpHeader.isRequest()) {
            return encoding.serializeRequest(connection, httpContent);
        } else {
            return encoding.serializeResponse(connection, httpContent);
        }
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

        public void initialize(int initialOffset, int maxHeaderSize) {
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
        public int chunkContentStart = -1;
        public long chunkLength = -1;
        public long chunkRemainder = -1;
        public MimeHeaders trailerHeaders = new MimeHeaders();

        public void recycle() {
            isLastChunk = false;
            chunkContentStart = -1;
            chunkLength = -1;
            chunkRemainder = -1;
            trailerHeaders.clear();
        }
    }
}
