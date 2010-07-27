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
import com.sun.grizzly.filterchain.Filter;
import com.sun.grizzly.filterchain.FilterChain;
import com.sun.grizzly.filterchain.FilterChainContext;
import com.sun.grizzly.filterchain.NextAction;
import com.sun.grizzly.http.util.Ascii;
import com.sun.grizzly.http.util.BufferChunk;
import com.sun.grizzly.http.util.CacheableBufferChunk;
import com.sun.grizzly.memory.MemoryManager;
import com.sun.grizzly.http.util.MimeHeaders;
import com.sun.grizzly.memory.BufferUtils;
import com.sun.grizzly.ssl.SSLFilter;
import com.sun.grizzly.utils.ArrayUtils;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * The {@link com.sun.grizzly.filterchain.Filter}, responsible for transforming {@link Buffer} into
 * {@link HttpPacket} and vice versa in asynchronous mode.
 * When the <tt>HttpCodecFilter</tt> is added to a {@link com.sun.grizzly.filterchain.FilterChain}, on read phase
 * it consumes incoming {@link Buffer} and provides {@link HttpContent} as
 * the result of transformation. On write phase the <tt>HttpCodecFilter</tt> consumes
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
public abstract class HttpCodecFilter extends BaseFilter {

    public static final String HTTP_0_9 = "HTTP/0.9";
    public static final String HTTP_1_0 = "HTTP/1.0";
    public static final String HTTP_1_1 = "HTTP/1.1";
    

    public static final int DEFAULT_MAX_HTTP_PACKET_HEADER_SIZE = 8192;

    private volatile TransferEncoding[] transferEncodings;
    
    private final Object transferEncodingSync = new Object();
    
    protected volatile ContentEncoding[] contentEncodings;

    private final Object contentEncodingSync = new Object();

    protected volatile boolean isSecure;
    private final boolean isSecureSet;

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
     * @param httpHeader {@link HttpHeader}, which represents parsed HTTP packet header
     * @param ctx processing context.
     *
     * @return <code>true</code> if an error has occurred while processing
     *  the header portion of the http request, otherwise returns
     *  <code>false</code>.s
     */
    abstract boolean onHttpPacketParsed(HttpHeader httpHeader, FilterChainContext ctx);


    /**
     * TODO Docs.
     * @param httpHeader {@link HttpHeader}, which represents parsed HTTP packet header
     * @param ctx
     *
     * @return <code>true</code> if an error has occurred while processing
     *  the header portion of the http request, otherwise returns
     *  <code>false</code>.
     */
    abstract boolean onHttpHeaderParsed(HttpHeader httpHeader, FilterChainContext ctx);

    
    /**
     * <p>
     * TODO Docs.
     * </p>
     *
     * @param httpHeader {@link HttpHeader}, which represents HTTP packet header
     * @param ctx the {@link FilterChainContext} procesing this request
     */
    abstract void onHttpError(HttpHeader httpHeader, FilterChainContext ctx) throws IOException;

    protected final int maxHeadersSize;

    /**
     * Constructor, which creates <tt>HttpCodecFilter</tt> instance, with the specific
     * max header size parameter.
     *
     * @param isSecure <tt>true</tt>, if the Filter will be used for secured HTTPS communication,
     *                 or <tt>false</tt> otherwise. It's possible to pass <tt>null</tt>, in this
     *                 case Filter will try to autodetect security.
     * @param maxHeadersSize the maximum size of the HTTP message header.
     */
    public HttpCodecFilter(Boolean isSecure, int maxHeadersSize) {
        if (isSecure == null) {
            isSecureSet = false;
        } else {
            isSecureSet = true;
            this.isSecure = isSecure;
        }

        this.maxHeadersSize = maxHeadersSize;
        transferEncodings = new TransferEncoding[] {
            new FixedLengthTransferEncoding(), new ChunkedTransferEncoding(maxHeadersSize)};
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onAdded(FilterChain filterChain) {
        if (!isSecureSet) {
            autoDetectSecure(filterChain);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onFilterChainChanged(FilterChain filterChain) {
        if (!isSecureSet) {
            autoDetectSecure(filterChain);
        }
    }


    /**
     * <p>
     * Gets registered {@link TransferEncoding}s.
     * </p>
     *
     * @return registered {@link TransferEncoding}s.
     */
    public TransferEncoding[] getTransferEncodings() {
        synchronized (transferEncodingSync) {
            return Arrays.copyOf(transferEncodings, transferEncodings.length);
        }
    }
    
    /**
     * <p>
     * Adds the specified {@link TransferEncoding} to the <code>HttpCodecFilter</code>.
     * </p>
     *
     * @param transferEncoding the {@link TransferEncoding} to add
     */
    public void addTransferEncoding(TransferEncoding transferEncoding) {
        if (transferEncoding == null) throw new IllegalArgumentException("Could not be null");

        synchronized (transferEncodingSync) {
            transferEncodings = ArrayUtils.addUnique(transferEncodings, transferEncoding);
        }
    }

    /**
     * <p>
     * Removes the specified {@link TransferEncoding} from the <code>HttpCodecFilter</code>.
     * </p>
     *
     * @param transferEncoding the {@link TransferEncoding} to remove
     * @return <code>true</code> if the {@link TransferEncoding} was removed, otherwise
     *  <code>false</code> indicating the {@link TransferEncoding} was not already
     *  present
     */
    public boolean removeTransferEncoding(TransferEncoding transferEncoding) {
        if (transferEncoding == null) throw new IllegalArgumentException("Could not be null");

        synchronized (transferEncodingSync) {
            final TransferEncoding[] oldEncodings = transferEncodings;
            transferEncodings = ArrayUtils.remove(oldEncodings, transferEncoding);

            return transferEncodings != oldEncodings;
        }
    }

    /**
     * <p>
     * Gets registered {@link ContentEncoding}s.
     * </p>
     *
     * @return registered {@link ContentEncoding}s.
     */
    public ContentEncoding[] getContentEncodings() {
        synchronized (contentEncodingSync) {
            return Arrays.copyOf(contentEncodings, contentEncodings.length);
        }
    }

    /**
     * <p>
     * Adds the specified {@link ContentEncoding} to the <code>HttpCodecFilter</code>.
     * </p>
     *
     * @param contentEncoding the {@link ContentEncoding} to add
     */
    public void addContentEncoding(ContentEncoding contentEncoding) {
        if (contentEncoding == null) throw new IllegalArgumentException("Could not be null");

        synchronized (contentEncodingSync) {
            contentEncodings = ArrayUtils.addUnique(contentEncodings, contentEncoding);
        }
    }


    /**
     * <p>
     * Removes the specified {@link ContentEncoding} from the <code>HttpCodecFilter</code>.
     * </p>
     *
     * @param contentEncoding the {@link ContentEncoding} to remove
     * @return <code>true</code> if the {@link ContentEncoding} was removed, otherwise
     *  <code>false</code> indicating the {@link ContentEncoding} was not already
     *  present
     */
    public boolean removeContentEncoding(ContentEncoding contentEncoding) {
        if (contentEncoding == null) throw new IllegalArgumentException("Could not be null");
        
        synchronized (contentEncodingSync) {
            final ContentEncoding[] oldEncodings = contentEncodings;
            contentEncodings = ArrayUtils.remove(oldEncodings, contentEncoding);

            return contentEncodings != oldEncodings;
        }
    }
    
    /**
     * The method is called by the specific <tt>HttpCodecFilter</tt> implementation,
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

        // Get the Connection
        final Connection connection = ctx.getConnection();

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

                if (onHttpHeaderParsed((HttpHeader) httpPacket,ctx)) {
                    onHttpError((HttpHeader) httpPacket,ctx);
                    return ctx.getStopAction();
                }

                input = input.slice();
                
                setTransferEncodingOnParsing((HttpHeader) httpPacket);
                setContentEncodingsOnParsing((HttpHeader) httpPacket);
            }
        }

        final HttpHeader httpHeader = (HttpHeader) httpPacket;
        final TransferEncoding transferEncoding = httpHeader.getTransferEncoding();

        // Check if appropriate HTTP content encoder was found
        if (transferEncoding != null) {

            final ParsingResult result = parseWithTransferEncoding(ctx.getConnection(), httpHeader, input);

            final HttpContent httpContent = result.getHttpContent();
            final Buffer remainderBuffer = result.getRemainderBuffer();

            result.recycle();

            boolean isLast = false;
            
            if (httpContent != null) {
                if (httpContent.isLast()) {
                    isLast = true;
                    onHttpPacketParsed(httpHeader, ctx);
                    // we don't expect any content anymore
                    httpHeader.setExpectContent(false);
                }

                // if consumer set "skip-content" flag - we don't interested in decoding the content - just skip it
                if (httpHeader.isSkipRemainder()) {
                    if (remainderBuffer != null) { // if there is a remainder - rerun this filter
                        ctx.setMessage(remainderBuffer);
                        return ctx.getRerunFilterAction();
                    } else { // if no remainder - just stop
                        return ctx.getStopAction();
                    }
                }

                final HttpContent decodedContent = decodeContent(connection, httpContent);
                if (decodedContent != null) {
                    ctx.setMessage(decodedContent);

                    // Instruct filterchain to continue the processing.
                    return ctx.getInvokeAction(remainderBuffer);
                } else if (remainderBuffer != null && remainderBuffer.hasRemaining()) {
                    // Instruct filterchain to continue the processing.
                    ctx.setMessage(
                            HttpContent.builder(httpHeader).last(isLast).build());
                    return ctx.getInvokeAction(remainderBuffer);
                }
            }

            if (!wasHeaderParsed || isLast) {
                ctx.setMessage(
                        HttpContent.builder(httpHeader)
                        .last(isLast)
                        .build());
                return ctx.getInvokeAction(remainderBuffer);
            } else {
                return ctx.getStopAction(remainderBuffer);
            }


        } else if (!httpHeader.isChunked() && httpHeader.getContentLength() < 0) {
            // Transfer-encoding is unknown and there is no content-length header
            
            // Build HttpContent message on top of existing content chunk and parsed Http message header
            final HttpContent.Builder builder = httpHeader.httpContentBuilder();
            final HttpContent message = builder.content(input).build();

            final HttpContent decodedContent = decodeContent(connection, message);
            if (decodedContent != null) {
                ctx.setMessage(decodedContent);

                // Instruct filterchain to continue the processing.
                return ctx.getInvokeAction();
            }

            if (!wasHeaderParsed) { // If HTTP header was just parsed

                // check if we expect any content
                final boolean isLast = !httpHeader.isExpectContent();
                if (isLast) { // if not - call onHttpPacketParsed
                    onHttpPacketParsed(httpHeader, ctx);
                }

                ctx.setMessage(
                        HttpContent.builder(httpHeader)
                        .last(isLast)
                        .build());
                return ctx.getInvokeAction();
            } else {
                return ctx.getStopAction();
            }
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
        final Object message = ctx.getMessage();
        
        if (message instanceof HttpPacket) {
            // Get HttpPacket
            final HttpPacket input = (HttpPacket) ctx.getMessage();
            // Get Connection
            final Connection connection = ctx.getConnection();

            // transform HttpPacket into Buffer
        final Buffer output = encodeHttpPacket(connection, input);

            if (output != null) {
                ctx.setMessage(output);
                // Invoke next filter in the chain.
                return ctx.getInvokeAction();
            }

            return ctx.getStopAction();
        }

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
        
        if (!httpHeader.isCommitted()) {
            setTransferEncodingOnSerializing(httpHeader, httpContent);
            setContentEncodingsOnSerializing(httpHeader);

            encodedBuffer = memoryManager.allocate(8192);

            encodedBuffer = encodeInitialLine(httpHeader, encodedBuffer, memoryManager);
            encodedBuffer = put(memoryManager, encodedBuffer, Constants.CRLF_BYTES);

            encodedBuffer = encodeKnownHeaders(memoryManager, encodedBuffer,
                    httpHeader);
            
            final MimeHeaders mimeHeaders = httpHeader.getHeaders();
            encodedBuffer = encodeMimeHeaders(memoryManager, encodedBuffer, mimeHeaders);

            encodedBuffer = put(memoryManager, encodedBuffer, Constants.CRLF_BYTES);
            encodedBuffer.trim();
            encodedBuffer.allowBufferDispose(true);
            
            httpHeader.setCommitted(true);
        }

        final HttpContent encodedHttpContent;
        if (!isHeader &&
                (encodedHttpContent = encodeContent(connection, httpContent)) != null) {
            
            final TransferEncoding contentEncoder = httpHeader.getTransferEncoding();

            final Buffer content = serializeWithTransferEncoding(connection,
                                                   encodedHttpContent,
                                                   contentEncoder);
            encodedBuffer = BufferUtils.appendBuffers(memoryManager,
                    encodedBuffer, content);

            if (encodedBuffer.isComposite()) {
                // If during buffer appending - composite buffer was created -
                // allow buffer disposing
                encodedBuffer.allowBufferDispose(true);
            }
        }

        return encodedBuffer;
    }

    protected Buffer encodeKnownHeaders(MemoryManager memoryManager,
            Buffer buffer, HttpHeader httpHeader) {
        
        final CacheableBufferChunk name = CacheableBufferChunk.create();
        final CacheableBufferChunk value = CacheableBufferChunk.create();

        name.setString(Constants.CONTENT_TYPE_HEADER);
        httpHeader.extractContentType(value);

        if (!value.isNull()) {
            buffer = encodeMimeHeader(memoryManager, buffer, name, value, true);
        }

        name.reset();
        value.reset();

        final List<ContentEncoding> packetContentEncodings =
                httpHeader.getContentEncodings(true);
        final boolean hasContentEncodings = !packetContentEncodings.isEmpty();
        
        if (hasContentEncodings) {
            buffer = encodeContentEncodingHeader(memoryManager, buffer,
                    httpHeader, name, value);
        }

        name.recycle();
        value.recycle();

        httpHeader.makeUpgradeHeader();

        return buffer;
    }

    private Buffer encodeContentEncodingHeader(final MemoryManager memoryManager,
            Buffer buffer, HttpHeader httpHeader,
            final CacheableBufferChunk name, final CacheableBufferChunk value) {

        final List<ContentEncoding> packetContentEncodings =
                httpHeader.getContentEncodings(true);

        name.setString(Constants.CONTENT_ENCODING_HEADER);
        httpHeader.extractContentEncoding(value);
        final boolean hasValue = !value.isNull();
        boolean needComma = hasValue;
        
        buffer = encodeMimeHeader(memoryManager, buffer, name, value, false);
        for (ContentEncoding encoding : packetContentEncodings) {
            if (needComma) {
                buffer = put(memoryManager, buffer, Constants.COMMA);
            }
            
            buffer = put(memoryManager, buffer, encoding.getName());
            needComma = true;
        }

        buffer = put(memoryManager, buffer, Constants.CRLF_BYTES);
        
        return buffer;
    }
    
    protected static Buffer encodeMimeHeaders(MemoryManager memoryManager,
            Buffer buffer, MimeHeaders mimeHeaders) {
        final int mimeHeadersNum = mimeHeaders.size();

        for (int i = 0; i < mimeHeadersNum; i++) {
            if (!mimeHeaders.getAndSetSerialized(i, true)) {
                final BufferChunk value = mimeHeaders.getValue(i);
                if (!value.isNull()) {
                    buffer = encodeMimeHeader(memoryManager, buffer,
                            mimeHeaders.getName(i), value, true);
                }
            }
        }

        return buffer;
    }

    protected static Buffer encodeMimeHeader(final MemoryManager memoryManager,
            Buffer buffer, final BufferChunk name, final BufferChunk value,
            final boolean encodeLastCRLF) {

        buffer = put(memoryManager, buffer, name);
        buffer = put(memoryManager, buffer, Constants.COLON_BYTES);
        buffer = put(memoryManager, buffer, value);
        
        if (encodeLastCRLF) {
            buffer = put(memoryManager, buffer, Constants.CRLF_BYTES);
        }

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
                parsingState.isUpgradeHeader = parseKnownHeaders;
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
                    if (!parseHeaderName(httpHeader, mimeHeaders, parsingState, input)) {
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

    protected static boolean parseHeaderName(HttpHeader httpHeader,
            MimeHeaders mimeHeaders, ParsingState parsingState, Buffer input) {
        final int limit = Math.min(input.limit(), parsingState.packetLimit);
        int start = parsingState.start;
        int offset = parsingState.offset;

        while(offset < limit) {
            byte b = input.get(offset);
            if (b == Constants.COLON) {

                parsingState.headerValueStorage =
                        mimeHeaders.addValue(input, parsingState.start, offset);
                parsingState.offset = offset + 1;
                finalizeKnownHeaderNames(httpHeader, parsingState, offset - start);

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
                        input.put(parsingState.checkpoint++, b2);
                        parsingState.offset = offset + 2;
                        return -2;
                    } else {
                        parsingState.offset = offset + 1;
                        finalizeKnownHeaderValues(httpHeader, parsingState, input);
                        parsingState.headerValueStorage.setBuffer(input,
                                parsingState.start, parsingState.checkpoint2);
                        return 0;
                    }
                }

                parsingState.offset = offset;
                return -1;
            } else if (b == Constants.SP) {
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

        if (parsingState.isUpgradeHeader) {
            parsingState.isUpgradeHeader =
                    (idx < Constants.UPGRADE_HEADER_BYTES.length)
                    && b == Constants.UPGRADE_HEADER_BYTES[idx];

        }

    }

    private static void finalizeKnownHeaderNames(HttpHeader httpHeader,
            ParsingState parsingState, int size) {
        
        if (parsingState.isContentLengthHeader) {
            parsingState.isContentLengthHeader =
                    (size == Constants.CONTENT_LENGTH_HEADER_BYTES.length);
        } else if (parsingState.isTransferEncodingHeader) {
            parsingState.isTransferEncodingHeader =
                    (size == Constants.TRANSFER_ENCODING_HEADER_BYTES.length);
        } else if (parsingState.isUpgradeHeader) {
            parsingState.isUpgradeHeader =
                    (size == Constants.UPGRADE_HEADER_BYTES.length);
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

    private static void finalizeKnownHeaderValues(HttpHeader httpHeader,
            ParsingState parsingState, Buffer input) {

        parsingState.isTransferEncodingHeader = false;
        if (parsingState.isUpgradeHeader) {
            httpHeader.getUpgradeBC().setBuffer(input, parsingState.start,
                    parsingState.checkpoint2);
        }
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
            Buffer dstBuffer, BufferChunk chunk) {

        if (chunk.isNull()) return dstBuffer;
        
        if (chunk.hasBuffer()) {
            final int length = chunk.getEnd() - chunk.getStart();
            if (dstBuffer.remaining() < length) {
                dstBuffer =
                        resizeBuffer(memoryManager, dstBuffer, length);
            }

            dstBuffer.put(chunk.getBuffer(), chunk.getStart(),
                    length);

            return dstBuffer;
        } else {
            return put(memoryManager, dstBuffer, chunk.toString());
        }
    }

    protected static Buffer put(MemoryManager memoryManager,
            Buffer dstBuffer, String s) {
        final int size = s.length();
        if (dstBuffer.remaining() < size) {
            dstBuffer =
                    resizeBuffer(memoryManager, dstBuffer, size);
        }

        for (int i = 0; i < size; i++) {
            dstBuffer.put((byte) s.charAt(i));
        }

        return dstBuffer;
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

    final void setTransferEncodingOnParsing(HttpHeader httpHeader) {
        final TransferEncoding[] encodings = transferEncodings;
        for (TransferEncoding encoding : encodings) {
            if (encoding.wantDecode(httpHeader)) {
                httpHeader.setTransferEncoding(encoding);
                return;
            }
        }
    }

    final void setTransferEncodingOnSerializing(HttpHeader httpHeader,
            HttpContent httpContent) {

        final TransferEncoding[] encodings = transferEncodings;
        for (TransferEncoding encoding : encodings) {
            if (encoding.wantEncode(httpHeader)) {
                encoding.prepareSerialize(httpHeader, httpContent);
                httpHeader.setTransferEncoding(encoding);
                return;
            }
        }
    }

    final HttpContent decodeContent(final Connection connection,
            HttpContent httpContent) {

        if (!httpContent.getContent().hasRemaining()) {
            httpContent.recycle();
            return null;
        }
        
        final MemoryManager memoryManager = connection.getTransport().getMemoryManager();
        final HttpHeader httpHeader = httpContent.getHttpHeader();
        final ContentParsingState parsingState =
                ((HttpPacketParsing) httpHeader).getContentParsingState();
        final List<ContentEncoding> encodings = httpHeader.getContentEncodings(true);

        final int encodingsNum = encodings.size();
        for (int i = 0; i < encodingsNum; i++) {

            // Check if there is a remainder buffer left from the last decoding
            final Buffer oldRemainder = parsingState.removeContentDecodingRemainder(i);
            if (oldRemainder != null) {
                // If yes - append the remainder and the new buffer
                final Buffer newChunk = httpContent.getContent();
                httpContent.setContent(
                        BufferUtils.appendBuffers(memoryManager, oldRemainder, newChunk));
            }

            // Decode
            final ContentEncoding encoding = encodings.get(i);
            final ParsingResult result = encoding.decode(connection, httpContent);

            // Check if there is remainder left after decoding
            final Buffer newRemainder = result.getRemainderBuffer();
            if (newRemainder != null) {
                // If yes - save it
                parsingState.setContentDecodingRemainder(i, newRemainder);
            }

            final HttpContent decodedContent = result.getHttpContent();
            
            result.recycle();

            if (decodedContent == null) {
                httpContent.recycle();
                return null;
            }

            httpContent = decodedContent;
        }

        return httpContent;
    }

    final HttpContent encodeContent(final Connection connection,
            HttpContent httpContent) {

        final HttpHeader httpHeader = httpContent.getHttpHeader();
        final List<ContentEncoding> encodings = httpHeader.getContentEncodings(true);

        final int encodingsNum = encodings.size();
        for (int i = 0; i < encodingsNum; i++) {
            // Encode
            final ContentEncoding encoding = encodings.get(i);
            final HttpContent encodedContent = encoding.encode(connection, httpContent);

            if (encodedContent == null) {
                httpContent.recycle();
                return null;
            }

            httpContent = encodedContent;
        }

        return httpContent;
    }
    
    final void setContentEncodingsOnParsing(HttpHeader httpHeader) {
        final BufferChunk bc =
                httpHeader.getHeaders().getValue(Constants.CONTENT_ENCODING_HEADER);
        
        if (bc != null) {
            final List<ContentEncoding> encodings = httpHeader.getContentEncodings(true);
            int currentIdx = 0;

            int commaIdx;
            while((commaIdx = bc.indexOf(',', currentIdx)) != -1) {
                final ContentEncoding ce = lookupContentEncoding(bc, currentIdx, commaIdx);
                encodings.add(ce);
                currentIdx = commaIdx + 1;
            }

            // last ContentEncoding
            final ContentEncoding ce = lookupContentEncoding(bc, currentIdx, bc.size());
            encodings.add(ce);
        }
    }

    final void setContentEncodingsOnSerializing(HttpHeader httpHeader) {
        // If user specified content-length - we don't encode the content
        if (httpHeader.getContentLength() >= 0) return;
        
        final BufferChunk bc =
                httpHeader.getHeaders().getValue(Constants.CONTENT_ENCODING_HEADER);

        final boolean isSomeEncodingApplied = bc != null && bc.size() > 0;

        final ContentEncoding[] encodingsLibrary = contentEncodings;
        final List<ContentEncoding> httpPacketEncoders = httpHeader.getContentEncodings(true);
        
        for (ContentEncoding encoding : encodingsLibrary) {
            if (isSomeEncodingApplied) {
                if (lookupAlias(encoding, bc, 0, bc.size())) {
                    continue;
                }
            }

            if (encoding.wantEncode(httpHeader)) {
                httpPacketEncoders.add(encoding);
            }
        }
    }
    
    private ContentEncoding lookupContentEncoding(BufferChunk bc,
            int startIdx, int endIdx) {
        final ContentEncoding[] encodings = contentEncodings;

        for (ContentEncoding encoding : encodings) {
            if (lookupAlias(encoding, bc, startIdx, endIdx)) {
                return encoding;
            }
        }

        throw new ContentEncodingException("Unknown content encoding: "
                + bc.toString().substring(startIdx, endIdx));
    }
    
    private ParsingResult parseWithTransferEncoding(Connection connection,
            HttpHeader httpHeader, Buffer input) {
        final TransferEncoding encoding = httpHeader.getTransferEncoding();
        return encoding.parsePacket(connection, httpHeader, input);
    }

    private Buffer serializeWithTransferEncoding(Connection connection,
                                   HttpContent httpContent,
                                   TransferEncoding encoding) {
        if (encoding != null) {
            return encoding.serializePacket(connection, httpContent);
        } else {
            // if no explicit TransferEncoding is available, then
            // assume "Identity"
            return httpContent.getContent();
        }
    }

    private static boolean lookupAlias(ContentEncoding encoding,
            BufferChunk aliasBuffer, int startIdx, int endIdx) {
        final String[] aliases = encoding.getAliases();
        
        for (String alias : aliases) {
            final int aliasLen = alias.length();

            for (int i = 0; i < aliasLen; i++) {
                if (aliasBuffer.startsWithIgnoreCase(alias, startIdx)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Autodetect whether we will deal with HTTP or HTTPS by trying to find
     * {@link SSLFilter} in the {@link FilterChain}.
     *
     * @param filterChain {@link FilterChain}
     */
    protected void autoDetectSecure(FilterChain filterChain) {
        final int httpFilterIdx = filterChain.indexOf(this);
        if (httpFilterIdx != -1) {
            for (int i=0; i<httpFilterIdx; i++) {
                final Filter filter = filterChain.get(i);
                if (filter.getClass().isAssignableFrom(SSLFilter.class)) {
                    isSecure = true;
                    return;
                }
            }
        }

        isSecure = false;
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
        public boolean isUpgradeHeader;

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
            isUpgradeHeader = false;
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

        private Buffer[] contentDecodingRemainders = new Buffer[1];
        private Buffer[] contentEncodingRemainders = new Buffer[1];
        
        public void recycle() {
            isLastChunk = false;
            chunkContentStart = -1;
            chunkLength = -1;
            chunkRemainder = -1;
            trailerHeaders.clear();
            Arrays.fill(contentDecodingRemainders, null);
            Arrays.fill(contentEncodingRemainders, null);
        }

        private Buffer removeContentDecodingRemainder(int i) {
            final Buffer remainder = contentDecodingRemainders[i];
            contentDecodingRemainders[i] = null;
            return remainder;
        }

        private void setContentDecodingRemainder(int i, Buffer remainder) {
            if (i >= contentDecodingRemainders.length) {
                contentDecodingRemainders = Arrays.copyOf(contentDecodingRemainders, i + 1);
            }
            
            contentDecodingRemainders[i] = remainder;
        }

        private Buffer removeContentEncodingRemainder(int i) {
            final Buffer remainder = contentEncodingRemainders[i];
            contentEncodingRemainders[i] = null;
            return remainder;
        }

        private void setContentEncodingRemainder(int i, Buffer remainder) {
            if (i >= contentEncodingRemainders.length) {
                contentEncodingRemainders = Arrays.copyOf(contentEncodingRemainders, i + 1);
            }
            
            contentEncodingRemainders[i] = remainder;
        }
    }
}
