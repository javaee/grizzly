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

import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.http.util.Ascii;
import org.glassfish.grizzly.http.util.DataChunk;
import org.glassfish.grizzly.http.util.CacheableDataChunk;
import org.glassfish.grizzly.memory.MemoryManager;
import org.glassfish.grizzly.http.util.MimeHeaders;
import org.glassfish.grizzly.memory.Buffers;
import org.glassfish.grizzly.monitoring.jmx.AbstractJmxMonitoringConfig;
import org.glassfish.grizzly.monitoring.jmx.JmxMonitoringAware;
import org.glassfish.grizzly.monitoring.jmx.JmxMonitoringConfig;
import org.glassfish.grizzly.monitoring.jmx.JmxObject;
import org.glassfish.grizzly.utils.ArraySet;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.glassfish.grizzly.http.util.Constants;
import org.glassfish.grizzly.ssl.SSLUtils;
import static org.glassfish.grizzly.http.util.HttpCodecUtils.*;

/**
 * The {@link org.glassfish.grizzly.filterchain.Filter}, responsible for transforming {@link Buffer} into
 * {@link HttpPacket} and vice versa in asynchronous mode.
 * When the <tt>HttpCodecFilter</tt> is added to a {@link org.glassfish.grizzly.filterchain.FilterChain}, on read phase
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
public abstract class HttpCodecFilter extends BaseFilter
        implements JmxMonitoringAware<HttpProbe> {

    public static final int DEFAULT_MAX_HTTP_PACKET_HEADER_SIZE = 8192;

    private final ArraySet<TransferEncoding> transferEncodings =
            new ArraySet<TransferEncoding>(TransferEncoding.class);
    
    protected final ArraySet<ContentEncoding> contentEncodings =
            new ArraySet<ContentEncoding>(ContentEncoding.class);

    protected final boolean chunkingEnabled;

    /**
     * File cache probes
     */
    protected final AbstractJmxMonitoringConfig<HttpProbe> monitoringConfig =
            new AbstractJmxMonitoringConfig<HttpProbe>(HttpProbe.class) {

        @Override
        public JmxObject createManagementObject() {
            return createJmxManagementObject();
        }

    };
    
    protected final int maxHeadersSize;

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
    abstract boolean decodeInitialLine(FilterChainContext ctx,
                                       HttpPacketParsing httpPacket,
                                       HeaderParsingState parsingState,
                                       Buffer input);

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
     *  the header portion of the HTTP request, otherwise returns
     *  <code>false</code>.s
     */
    protected abstract boolean onHttpPacketParsed(HttpHeader httpHeader, FilterChainContext ctx);


    /**
     * Callback invoked when the HTTP message header parsing is complete.
     *
     * @param httpHeader {@link HttpHeader}, which represents parsed HTTP packet header
     * @param buffer {@link Buffer} the header was parsed from
     * @param ctx processing context.
     *
     * @return <code>true</code> if an error has occurred while processing
     *  the header portion of the HTTP request, otherwise returns
     *  <code>false</code>.
     */
    protected abstract boolean onHttpHeaderParsed(HttpHeader httpHeader,
                                                  Buffer buffer,
                                                  FilterChainContext ctx);


    /**
     * <p>
     * Invoked when either the request line or status line has been parsed.
     *
     * </p>
     * @param httpHeader {@link HttpHeader}, which represents HTTP packet header
     * @param ctx processing context.
     */
    protected abstract void onInitialLineParsed(final HttpHeader httpHeader,
                                                final FilterChainContext ctx);


    /**
     * <p>
     * Invoked when all headers of the packet have been parsed.  Depending on the
     * transfer encoding being used by the current request, this method may be
     * invoked multiple times.
     * </p>
     *
     * @param httpHeader {@link HttpHeader}, which represents HTTP packet header
     * @param ctx processing context.
     */
    protected abstract void onHttpHeadersParsed(final HttpHeader httpHeader,
                                                final FilterChainContext ctx);


    /**
     * <p>
     * Invoked as request/response body content has been processed by this
     * {@link org.glassfish.grizzly.filterchain.Filter}.
     * </p>
     *
     * @param content request/response body content
     * @param ctx processing context.
     */
    protected abstract void onHttpContentParsed(final HttpContent content,
                                                final FilterChainContext ctx);


    /**
     * <p>
     * Callback which is invoked when parsing an HTTP message fails.
     * The processing logic has to take care about error handling and following
     * connection closing.
     * </p>
     *
     * @param httpHeader {@link HttpHeader}, which represents HTTP packet header
     * @param ctx the {@link FilterChainContext} processing this request
     */
    protected abstract void onHttpError(HttpHeader httpHeader,
                                        FilterChainContext ctx) throws IOException;

    /**
     * Constructor, which creates <tt>HttpCodecFilter</tt> instance, with the specific
     * max header size parameter.
     *
     * @param chunkingEnabled <code>true</code> if the chunked transfer encoding
     *  should be used when no explicit content length has been set.
     * @param maxHeadersSize the maximum size of the HTTP message header.
     */
    public HttpCodecFilter(final boolean chunkingEnabled,
                           final int maxHeadersSize) {
        this.maxHeadersSize = maxHeadersSize;
        this.chunkingEnabled = chunkingEnabled;
        transferEncodings.addAll(new FixedLengthTransferEncoding(),
                new ChunkedTransferEncoding(maxHeadersSize));
    }

    /**
     * <p>
     * Gets registered {@link TransferEncoding}s.
     * </p>
     *
     * @return registered {@link TransferEncoding}s.
     */
    public TransferEncoding[] getTransferEncodings() {
        return transferEncodings.obtainArrayCopy();
    }
    
    /**
     * <p>
     * Adds the specified {@link TransferEncoding} to the <code>HttpCodecFilter</code>.
     * </p>
     *
     * @param transferEncoding the {@link TransferEncoding} to add
     */
    public void addTransferEncoding(final TransferEncoding transferEncoding) {
        transferEncodings.add(transferEncoding);
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
    public boolean removeTransferEncoding(final TransferEncoding transferEncoding) {
        return transferEncodings.remove(transferEncoding);
    }

    /**
     * <p>
     * Gets registered {@link ContentEncoding}s.
     * </p>
     *
     * @return registered {@link ContentEncoding}s.
     */
    public ContentEncoding[] getContentEncodings() {
        return contentEncodings.obtainArrayCopy();
    }

    /**
     * <p>
     * Adds the specified {@link ContentEncoding} to the <code>HttpCodecFilter</code>.
     * </p>
     *
     * @param contentEncoding the {@link ContentEncoding} to add
     */
    public void addContentEncoding(final ContentEncoding contentEncoding) {
        contentEncodings.add(contentEncoding);
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
    public boolean removeContentEncoding(final ContentEncoding contentEncoding) {
        return contentEncodings.remove(contentEncoding);
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
    public final NextAction handleRead(final FilterChainContext ctx,
            final HttpPacketParsing httpPacket) throws IOException {

        // Get the input buffer
        Buffer input = (Buffer) ctx.getMessage();

        // Get the Connection
        final Connection connection = ctx.getConnection();

        HttpProbeNotifier.notifyDataReceived(this, connection, input);

         // Check if HTTP header has been parsed
        final boolean wasHeaderParsed = httpPacket.isHeaderParsed();
        final HttpHeader httpHeader = (HttpHeader) httpPacket;

        try {
            if (!wasHeaderParsed) {
                // if header wasn't parsed - parse
                if (!decodeHttpPacket(ctx, httpPacket, input)) {
                    // if there is not enough data to parse the HTTP header - stop
                    // filterchain processing
                    return ctx.getStopAction(input);
                } else {
                    final int headerSizeInBytes = input.position();
                    
                    // if headers get parsed - set the flag
                    httpPacket.setHeaderParsed(true);
                    // recycle header parsing state
                    httpPacket.getHeaderParsingState().recycle();

                    if (onHttpHeaderParsed(httpHeader, input, ctx)) {
                        throw new IllegalStateException("Bad HTTP headers");
                    }

                    input = input.hasRemaining() ? input.slice() : Buffers.EMPTY_BUFFER;

                    setTransferEncodingOnParsing(httpHeader);
                    setContentEncodingsOnParsing(httpHeader);

                    HttpProbeNotifier.notifyHeaderParse(this, connection,
                            (HttpHeader) httpPacket, headerSizeInBytes);
                }
            }


            final TransferEncoding transferEncoding = httpHeader.getTransferEncoding();

            // Check if appropriate HTTP transfer encoder was found
            if (transferEncoding != null) {
                return decodeWithTransferEncoding(ctx, httpHeader, input, wasHeaderParsed);
            } else if (!httpHeader.isChunked() && httpHeader.getContentLength() < 0) {
                if (input.hasRemaining()) {
                    // Transfer-encoding is unknown and there is no content-length header

                    // Build HttpContent message on top of existing content chunk and parsed Http message header
                    final HttpContent.Builder builder = httpHeader.httpContentBuilder();
                    final HttpContent message = builder.content(input).build();

                    final HttpContent decodedContent = decodeContent(ctx, message);
                    if (decodedContent != null) {
                        if (httpHeader.isSkipRemainder()) {  // Do we skip the remainder?
                            return ctx.getStopAction();
                        }

                        HttpProbeNotifier.notifyContentChunkParse(this,
                                connection, decodedContent);

                        ctx.setMessage(decodedContent);

                        // Instruct filterchain to continue the processing.
                        return ctx.getInvokeAction();
                    }
                }
                
                if (!wasHeaderParsed) { // If HTTP header was just parsed

                    // check if we expect any content
                    final boolean isLast = !httpHeader.isExpectContent();
                    if (isLast) { // if not - call onHttpPacketParsed
                        onHttpPacketParsed(httpHeader, ctx);
                    }

                    final HttpContent emptyContent = HttpContent.builder(httpHeader).last(isLast).build();

                    HttpProbeNotifier.notifyContentChunkParse(this,
                            connection, emptyContent);

                    ctx.setMessage(emptyContent);
                    return ctx.getInvokeAction();
                } else {
                    return ctx.getStopAction();
                }
            }

            throw new IllegalStateException(
                    "Error parsing HTTP packet: " + httpHeader);

        } catch (RuntimeException re) {
            HttpProbeNotifier.notifyProbesError(this, connection, re);
            onHttpError(httpHeader, ctx);

            // make the connection deaf to any following input
            // onHttpError call will take care of error processing
            // and closing the connection
            final NextAction suspendAction = ctx.getSuspendAction();
            ctx.completeAndRecycle();
            return suspendAction;
        }
    }

    private NextAction decodeWithTransferEncoding(final FilterChainContext ctx,
            final HttpHeader httpHeader, final Buffer input,
            final boolean wasHeaderParsed) {

        final Connection connection = ctx.getConnection();
        final ParsingResult result = parseWithTransferEncoding(
                ctx, httpHeader, input);

        final HttpContent httpContent = result.getHttpContent();
        final Buffer remainderBuffer = result.getRemainderBuffer();

        final boolean hasRemainder = remainderBuffer != null &&
                remainderBuffer.hasRemaining();

        result.recycle();

        boolean isLast = !httpHeader.isExpectContent();

        if (httpContent != null) {
            if (httpContent.isLast()) {
                isLast = true;
                // we don't expect any content anymore
                httpHeader.setExpectContent(false);
            }
            if (httpHeader.isSkipRemainder()) {
                if (isLast) {
                    onHttpPacketParsed(httpHeader, ctx);
                }
                if (remainderBuffer != null) {
                    // if there is a remainder - rerun this filter
                    ctx.setMessage(remainderBuffer);
                    return ctx.getRerunFilterAction();
                } else {
                    // if no remainder - just stop
                    return ctx.getStopAction();
                }
            }
            final HttpContent decodedContent = decodeContent(ctx, httpContent);
            if (isLast) {
                onHttpPacketParsed(httpHeader, ctx);
            }
            if (decodedContent != null) {
                HttpProbeNotifier.notifyContentChunkParse(this, connection, decodedContent);
                ctx.setMessage(decodedContent);
                // Instruct filterchain to continue the processing.
                return ctx.getInvokeAction(hasRemainder ? remainderBuffer : null);
            } else if (hasRemainder) {
                final HttpContent emptyContent = HttpContent.builder(httpHeader).last(isLast).build();
                HttpProbeNotifier.notifyContentChunkParse(this, connection, emptyContent);
                // Instruct filterchain to continue the processing.
                ctx.setMessage(emptyContent);
                return ctx.getInvokeAction(remainderBuffer);
            }
        }

        if (!wasHeaderParsed || isLast) {
            final HttpContent emptyContent = HttpContent.builder(httpHeader).last(isLast).build();
            HttpProbeNotifier.notifyContentChunkParse(this, connection, emptyContent);
            ctx.setMessage(emptyContent);
            return ctx.getInvokeAction(hasRemainder ? remainderBuffer : null);
        } else {
            return ctx.getStopAction(hasRemainder ? remainderBuffer : null);
        }
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
    public NextAction handleWrite(final FilterChainContext ctx) throws IOException {
        final Object message = ctx.getMessage();
        
        if (HttpPacket.isHttp(message)) {
            // Get HttpPacket
            final HttpPacket input = (HttpPacket) ctx.getMessage();
            // Get Connection
            final Connection connection = ctx.getConnection();

            try {
                // transform HttpPacket into Buffer
                final Buffer output = encodeHttpPacket(ctx, input);

                if (output != null) {
                    HttpProbeNotifier.notifyDataSent(this, connection, output);

                    ctx.setMessage(output);
                    // Invoke next filter in the chain.
                    return ctx.getInvokeAction();
                }

                return ctx.getStopAction();
            } catch (RuntimeException re) {
                HttpProbeNotifier.notifyProbesError(this, connection, re);
                throw re;
            }
        }

        return ctx.getInvokeAction();
    }
    
    protected boolean decodeHttpPacket(final FilterChainContext ctx,
                                       final HttpPacketParsing httpPacket,
                                       final Buffer input) {

        final HeaderParsingState parsingState = httpPacket.getHeaderParsingState();
        switch (parsingState.state) {
            case 0: { // parsing initial line
                if (!decodeInitialLine(ctx, httpPacket, parsingState, input)) {
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
                if (httpPacket.getHeaders().size() > 0) {
                    onHttpHeadersParsed((HttpHeader) httpPacket, ctx);
                }
                input.position(parsingState.offset);
                return true;
            }

            default: throw new IllegalStateException();
        }
    }

    protected Buffer encodeHttpPacket(final FilterChainContext ctx, final HttpPacket input) {
        final Connection connection = ctx.getConnection();
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
            if (!httpHeader.isRequest()) {
                final HttpResponsePacket response = (HttpResponsePacket) httpHeader;
                if (response.isAcknowledgement()) {
                    encodedBuffer = memoryManager.allocate(128);
                    encodedBuffer = encodeInitialLine(httpHeader,
                                                      encodedBuffer,
                                                      memoryManager);
                    encodedBuffer = put(memoryManager,
                                        encodedBuffer,
                                        Constants.CRLF_BYTES);
                    encodedBuffer = put(memoryManager,
                                        encodedBuffer,
                                        Constants.CRLF_BYTES);
                    encodedBuffer.trim();
                    encodedBuffer.allowBufferDispose(true);

                    HttpProbeNotifier.notifyHeaderSerialize(this, ctx.getConnection(),
                            httpHeader, encodedBuffer);

                    response.acknowledged();
                    return encodedBuffer; // DO NOT MARK COMMITTED
                }
            }
            setContentEncodingsOnSerializing(httpHeader);
            setTransferEncodingOnSerializing(ctx,
                                             httpHeader,
                                             httpContent);

            encodedBuffer = memoryManager.allocateAtLeast(2048);

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

            HttpProbeNotifier.notifyHeaderSerialize(this, connection, httpHeader,
                    encodedBuffer);
        }

        
        if (!isHeader && httpHeader.isExpectContent()) {
            final HttpContent encodedHttpContent;

            HttpProbeNotifier.notifyContentChunkSerialize(this, connection, httpContent);
            
            if ((encodedHttpContent = encodeContent(connection, httpContent)) == null) {
                return encodedBuffer;
            }
            
            final TransferEncoding contentEncoder = httpHeader.getTransferEncoding();

            final Buffer content = serializeWithTransferEncoding(ctx,
                                                   encodedHttpContent,
                                                   contentEncoder);
            encodedBuffer = Buffers.appendBuffers(memoryManager,
                    encodedBuffer, content);

            if (encodedBuffer.isComposite()) {
                // If during buffer appending - composite buffer was created -
                // allow buffer disposing
                encodedBuffer.allowBufferDispose(true);
            }
        }

        return encodedBuffer;
    }

    protected static Buffer encodeKnownHeaders(final MemoryManager memoryManager,
            Buffer buffer, final HttpHeader httpHeader) {
        
        final CacheableDataChunk name = CacheableDataChunk.create();
        final CacheableDataChunk value = CacheableDataChunk.create();

        name.setString(Constants.CONTENT_TYPE_HEADER);
        updateContentType(memoryManager, httpHeader, value);

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

    private static void updateContentType(MemoryManager memoryManager, HttpHeader httpHeader, CacheableDataChunk value) {
        if (httpHeader.isContentTypeSet()) {
            httpHeader.extractContentType(value);
        } else {
            final byte[] defaultType = httpHeader.getDefaultContentType();
            if (defaultType != null) {
                final Buffer b = Buffers.wrap(memoryManager, defaultType);
                b.allowBufferDispose(true);
                value.setBuffer(b, 0, b.limit());
            }
        }
    }

    private static Buffer encodeContentEncodingHeader(final MemoryManager memoryManager,
            Buffer buffer, final HttpHeader httpHeader,
            final CacheableDataChunk name, final CacheableDataChunk value) {

        final List<ContentEncoding> packetContentEncodings =
                httpHeader.getContentEncodings(true);

        name.setString(Constants.CONTENT_ENCODING_HEADER);
        httpHeader.extractContentEncoding(value);
        boolean needComma = !value.isNull();
        
        buffer = encodeMimeHeader(memoryManager, buffer, name, value, false);
        for (int i = 0; i < packetContentEncodings.size(); i++) {
            final ContentEncoding encoding = packetContentEncodings.get(i);
            if (needComma) {
                buffer = put(memoryManager, buffer, Constants.COMMA);
            }
            
            buffer = put(memoryManager, buffer, encoding.getName());
            needComma = true;
        }

        buffer = put(memoryManager, buffer, Constants.CRLF_BYTES);
        
        return buffer;
    }
    
    protected static Buffer encodeMimeHeaders(final MemoryManager memoryManager,
            Buffer buffer, final MimeHeaders mimeHeaders) {
        final int mimeHeadersNum = mimeHeaders.size();

        for (int i = 0; i < mimeHeadersNum; i++) {
            if (!mimeHeaders.getAndSetSerialized(i, true)) {
                final DataChunk value = mimeHeaders.getValue(i);
                if (!value.isNull()) {
                    buffer = encodeMimeHeader(memoryManager, buffer,
                            mimeHeaders.getName(i), value, true);
                }
            }
        }

        return buffer;
    }

    protected static Buffer encodeMimeHeader(final MemoryManager memoryManager,
            Buffer buffer, final DataChunk name, final DataChunk value,
            final boolean encodeLastCRLF) {

        buffer = put(memoryManager, buffer, name);
        buffer = put(memoryManager, buffer, Constants.COLON_BYTES);
        buffer = put(memoryManager, buffer, value);
        
        if (encodeLastCRLF) {
            buffer = put(memoryManager, buffer, Constants.CRLF_BYTES);
        }

        return buffer;
    }
    
    protected boolean parseHeaders(final HttpHeader httpHeader,
                                   final MimeHeaders mimeHeaders,
                                   final HeaderParsingState parsingState,
                                   final Buffer input) {
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
                parsingState.isUpgradeHeader = parseKnownHeaders && httpHeader.isRequest();
                parsingState.isExpect100Header = parseKnownHeaders && httpHeader.isRequest();
            }

            if (!parseHeader(httpHeader, mimeHeaders, parsingState, input)) {
                return false;
            }

        } while (true);
    }

    protected static boolean parseHeader(final HttpHeader httpHeader,
            final MimeHeaders mimeHeaders, final HeaderParsingState parsingState,
            final Buffer input) {
        
        while (true) {
            final int subState = parsingState.subState;

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
                        // Multiline header detected. Skip preceding spaces
                        parsingState.subState = 2;
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

    protected static boolean parseHeaderName(final HttpHeader httpHeader,
            final MimeHeaders mimeHeaders, final HeaderParsingState parsingState,
            final Buffer input) {
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

    protected static int parseHeaderValue(final HttpHeader httpHeader,
            final HeaderParsingState parsingState, final Buffer input) {
        
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

    private static void checkKnownHeaderNames(final HeaderParsingState parsingState,
            final byte b, final int idx) {
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

        if (parsingState.isExpect100Header) {
            parsingState.isExpect100Header =
                    (idx < Constants.EXPECT_100_CONTINUE_NAME_BYTES.length)
                    && b == Constants.EXPECT_100_CONTINUE_NAME_BYTES[idx];
        }
    }

    private static void finalizeKnownHeaderNames(final HttpHeader httpHeader,
            final HeaderParsingState parsingState, final int size) {
        
        if (parsingState.isContentLengthHeader) {
            parsingState.isContentLengthHeader =
                    (size == Constants.CONTENT_LENGTH_HEADER_BYTES.length);
            if (parsingState.isContentLengthHeader &&
                    parsingState.hasContentLength) {
                throw new IllegalStateException("Two content-length headers are not allowed");
            }
            
            parsingState.hasContentLength = true;
        } else if (parsingState.isTransferEncodingHeader) {
            parsingState.isTransferEncodingHeader =
                    (size == Constants.TRANSFER_ENCODING_HEADER_BYTES.length);
        } else if (parsingState.isUpgradeHeader) {
            parsingState.isUpgradeHeader =
                    (size == Constants.UPGRADE_HEADER_BYTES.length);
        } else if (parsingState.isExpect100Header) {
            if (size == Constants.EXPECT_100_CONTINUE_NAME_BYTES.length) {
                ((HttpRequestPacket) httpHeader).requiresAcknowledgement(true);
            }

            parsingState.isExpect100Header = false;
        }
    }

    private static void checkKnownHeaderValues(final HttpHeader httpHeader,
            final HeaderParsingState parsingState, final byte b) {
        if (parsingState.isContentLengthHeader) {
            if (Ascii.isDigit(b)) {
                parsingState.parsingNumericValue =
                        parsingState.parsingNumericValue * 10 + (b - '0');
                httpHeader.setContentLengthLong(parsingState.parsingNumericValue);
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

    private static void finalizeKnownHeaderValues(final HttpHeader httpHeader,
            final HeaderParsingState parsingState, final Buffer input) {

        parsingState.isTransferEncodingHeader = false;

        if (parsingState.isUpgradeHeader) {
            httpHeader.getUpgradeDC().setBuffer(input, parsingState.start,
                    parsingState.checkpoint2);
            parsingState.isUpgradeHeader = false;
        }
    }

    protected static int checkEOL(final HeaderParsingState parsingState, final Buffer input) {
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

    protected static boolean findEOL(final HeaderParsingState state, final Buffer input) {
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

    protected static int findSpace(final Buffer input, int offset,
            final int packetLimit) {
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

    protected static int skipSpaces(final Buffer input, int offset,
            final int packetLimit) {
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

    protected static int indexOf(final Buffer input, int offset,
            final byte b, final int packetLimit) {
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

    final void setTransferEncodingOnParsing(HttpHeader httpHeader) {
        final TransferEncoding[] encodings = transferEncodings.getArray();
        if (encodings == null) return;

        for (TransferEncoding encoding : encodings) {
            if (encoding.wantDecode(httpHeader)) {
                httpHeader.setTransferEncoding(encoding);
                return;
            }
        }
    }

    final void setTransferEncodingOnSerializing(final FilterChainContext ctx,
                                                final HttpHeader httpHeader,
                                                final HttpContent httpContent) {

        final TransferEncoding[] encodings = transferEncodings.getArray();
        if (encodings == null) return;
        
        for (TransferEncoding encoding : encodings) {
            if (encoding.wantEncode(httpHeader)) {
                encoding.prepareSerialize(ctx, httpHeader, httpContent);
                httpHeader.setTransferEncoding(encoding);
                return;
            }
        }
    }

    final HttpContent decodeContent(final FilterChainContext ctx,
                                    HttpContent httpContent) {

        if (!httpContent.getContent().hasRemaining()
                || isResponseToHeadRequest(httpContent.getHttpHeader())) {
            httpContent.recycle();
            return null;
        }

        final Connection connection = ctx.getConnection();
        
        final MemoryManager memoryManager = connection.getTransport().getMemoryManager();
        final HttpHeader httpHeader = httpContent.getHttpHeader();
        final ContentParsingState parsingState =
                ((HttpPacketParsing) httpHeader).getContentParsingState();
        final List<ContentEncoding> encodings = httpHeader.getContentEncodings(true);

        final int encodingsNum = encodings.size();
        for (int i = 0; i < encodingsNum; i++) {
            final ContentEncoding encoding = encodings.get(i);

            HttpProbeNotifier.notifyContentEncodingParse(this, connection,
                    httpHeader, httpContent.getContent(), encoding);
            
            // Check if there is a remainder buffer left from the last decoding
            final Buffer oldRemainder = parsingState.removeContentDecodingRemainder(i);
            if (oldRemainder != null) {
                // If yes - append the remainder and the new buffer
                final Buffer newChunk = httpContent.getContent();
                httpContent.setContent(
                        Buffers.appendBuffers(memoryManager, oldRemainder, newChunk));
            }

            // Decode
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
        onHttpContentParsed(httpContent, ctx);
        return httpContent;
    }

    final HttpContent encodeContent(final Connection connection,
            HttpContent httpContent) {

        final HttpHeader httpHeader = httpContent.getHttpHeader();
        final List<ContentEncoding> encodings = httpHeader.getContentEncodings(true);

        for (int i = 0, len = encodings.size(); i < len; i++) {
            // Encode
            final ContentEncoding encoding = encodings.get(i);

            HttpProbeNotifier.notifyContentEncodingSerialize(this, connection,
                    httpHeader, httpContent.getContent(), encoding);
            
            final HttpContent encodedContent = encoding.encode(connection, httpContent);

            if (encodedContent == null) {
                httpContent.recycle();
                return null;
            }

            httpContent = encodedContent;
        }

        return httpContent;
    }
    
    final void setContentEncodingsOnParsing(final HttpHeader httpHeader) {
        final DataChunk bc =
                httpHeader.getHeaders().getValue(Constants.CONTENT_ENCODING_HEADER);
        
        if (bc != null) {
            final List<ContentEncoding> encodings = httpHeader.getContentEncodings(true);
            int currentIdx = 0;

            int commaIdx;
            do {
                commaIdx = bc.indexOf(',', currentIdx);
                final ContentEncoding ce = lookupContentEncoding(bc, currentIdx,
                        commaIdx >= 0 ? commaIdx : bc.getLength());
                if (ce != null && ce.wantDecode(httpHeader)) {
                    encodings.add(ce);
                } else {
                    // if encoding was not found or doesn't want to decode -
                    // following could not be applied
                    return;
                }
                currentIdx = commaIdx + 1;
            } while (commaIdx >= 0);
        }
    }

    final void setContentEncodingsOnSerializing(final HttpHeader httpHeader) {

        final DataChunk bc =
                httpHeader.getHeaders().getValue(Constants.CONTENT_ENCODING_HEADER);

        final boolean isSomeEncodingApplied = bc != null && bc.getLength() > 0;

        final ContentEncoding[] encodingsLibrary = contentEncodings.getArray();
        if (encodingsLibrary == null) return;

        final List<ContentEncoding> httpPacketEncoders = httpHeader.getContentEncodings(true);

        final boolean specifiedLength = (httpHeader.getContentLength() >= 0);
        for (ContentEncoding encoding : encodingsLibrary) {
            if (isSomeEncodingApplied) {
                if (lookupAlias(encoding, bc, 0)) {
                    continue;
                }
            }

            if (encoding.wantEncode(httpHeader)) {
                if (specifiedLength && chunkingEnabled) {
                    httpHeader.setContentLength(-1);
                    httpHeader.setChunked(true);
                } else if (specifiedLength) {
                    continue;
                }
                httpPacketEncoders.add(encoding);
            }
        }
    }
    
    private ContentEncoding lookupContentEncoding(final DataChunk bc,
            final int startIdx, final int endIdx) {
        final ContentEncoding[] encodings = contentEncodings.getArray();

        if (encodings != null) {
            for (ContentEncoding encoding : encodings) {
                if (lookupAlias(encoding, bc, startIdx)) {
                    return encoding;
                }
            }
        }

        return null;
    }
    
    private ParsingResult parseWithTransferEncoding(final FilterChainContext ctx,
            final HttpHeader httpHeader, final Buffer input) {
        final TransferEncoding encoding = httpHeader.getTransferEncoding();

        HttpProbeNotifier.notifyTransferEncodingParse(this, ctx.getConnection(),
                httpHeader, input, encoding);
        
        return encoding.parsePacket(ctx, httpHeader, input);
    }

    private Buffer serializeWithTransferEncoding(final FilterChainContext ctx,
                                   final HttpContent httpContent,
                                   final TransferEncoding encoding) {
        if (encoding != null) {
            HttpProbeNotifier.notifyTransferEncodingParse(this, ctx.getConnection(),
                    httpContent.getHttpHeader(), httpContent.getContent(),
                    encoding);
            
            return encoding.serializePacket(ctx, httpContent);
        } else {
            // if no explicit TransferEncoding is available, then
            // assume "Identity"
            return httpContent.getContent();
        }
    }

    private static boolean lookupAlias(final ContentEncoding encoding,
                                       final DataChunk aliasBuffer,
                                       final int startIdx) {

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
     * flag, which indicates whether this <tt>HttpCodecFilter</tt> is dealing with
     * the secured HTTP packets. For this filter flag means nothing, it's just
     * a value, which is getting set to a {@link HttpRequestPacket} or
     * {@link HttpResponsePacket}.
     */
    protected static boolean isSecure(final Connection connection) {
        return SSLUtils.getSSLEngine(connection) != null;
    }

    protected static final class HeaderParsingState {
        public int packetLimit;

        public int state;
        public int subState;

        public int start;
        public int offset;
        public int checkpoint = -1; // extra parsing state field
        public int checkpoint2 = -1; // extra parsing state field

        public DataChunk headerValueStorage;
        public HttpCodecFilter codecFilter;

        public long parsingNumericValue;

        public boolean isContentLengthHeader;
        public boolean hasContentLength;
        public boolean isTransferEncodingHeader;
        public boolean isUpgradeHeader;
        public boolean isExpect100Header;

        public void initialize(final HttpCodecFilter codecFilter,
                               final int initialOffset,
                               final int maxHeaderSize) {
            this.codecFilter = codecFilter;
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
            isExpect100Header = false;
            hasContentLength = false;
        }

        public final void checkOverflow() {
            if (offset < packetLimit) return;

            throw new IllegalStateException("HTTP packet is too long");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public JmxMonitoringConfig<HttpProbe> getMonitoringConfig() {
        return monitoringConfig;
    }

    protected JmxObject createJmxManagementObject() {
        return new org.glassfish.grizzly.http.jmx.HttpCodecFilter(this);
    }

    private boolean isResponseToHeadRequest(HttpHeader header) {
        if (header.isRequest()) {
            return false;
        } else {
            HttpRequestPacket request = ((HttpResponsePacket) header).getRequest();
            return request.isHeadRequest();
        }
    }

    protected static final class ContentParsingState {
        public boolean isLastChunk;
        public int chunkContentStart = -1;
        public long chunkLength = -1;
        public long chunkRemainder = -1;
        public final MimeHeaders trailerHeaders = new MimeHeaders();

        private Buffer[] contentDecodingRemainders = new Buffer[1];
        private final Buffer[] contentEncodingRemainders = new Buffer[1];
        
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

    }
}
