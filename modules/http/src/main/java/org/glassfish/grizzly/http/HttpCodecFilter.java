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

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.http.util.Ascii;
import org.glassfish.grizzly.http.util.BufferChunk;
import org.glassfish.grizzly.http.util.ByteChunk;
import org.glassfish.grizzly.http.util.CacheableDataChunk;
import org.glassfish.grizzly.http.util.Constants;
import org.glassfish.grizzly.http.util.DataChunk;
import org.glassfish.grizzly.http.util.Header;
import org.glassfish.grizzly.http.util.MimeHeaders;
import org.glassfish.grizzly.memory.Buffers;
import org.glassfish.grizzly.memory.CompositeBuffer;
import org.glassfish.grizzly.memory.CompositeBuffer.DisposeOrder;
import org.glassfish.grizzly.memory.MemoryManager;
import org.glassfish.grizzly.monitoring.MonitoringAware;
import org.glassfish.grizzly.monitoring.MonitoringConfig;
import org.glassfish.grizzly.monitoring.DefaultMonitoringConfig;
import org.glassfish.grizzly.monitoring.MonitoringUtils;
import org.glassfish.grizzly.ssl.SSLUtils;
import org.glassfish.grizzly.utils.ArraySet;

import static org.glassfish.grizzly.http.util.HttpCodecUtils.*;
import static org.glassfish.grizzly.utils.Charsets.ASCII_CHARSET;

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
public abstract class HttpCodecFilter extends HttpBaseFilter
        implements MonitoringAware<HttpProbe> {

    public static final int DEFAULT_MAX_HTTP_PACKET_HEADER_SIZE = 8192;

    private final static Logger LOGGER = Grizzly.logger(HttpCodecFilter.class);
    
    private final static byte[] CHUNKED_ENCODING_BYTES =
            Constants.CHUNKED_ENCODING.getBytes(ASCII_CHARSET);
    /**
     * Colon bytes.
     */
    static final byte[] COLON_BYTES = {(byte) ':', (byte) ' '};
    /**
     * CRLF bytes.
     */
    static final byte[] CRLF_BYTES = {(byte) '\r', (byte) '\n'};

    /**
     * Close bytes.
     */
    protected static final byte[] CLOSE_BYTES = {
        (byte) 'c',
        (byte) 'l',
        (byte) 'o',
        (byte) 's',
        (byte) 'e'
    };
    /**
     * Keep-alive bytes.
     */
    protected static final byte[] KEEPALIVE_BYTES = {
        (byte) 'k',
        (byte) 'e',
        (byte) 'e',
        (byte) 'p',
        (byte) '-',
        (byte) 'a',
        (byte) 'l',
        (byte) 'i',
        (byte) 'v',
        (byte) 'e'
    };
    
    private final ArraySet<TransferEncoding> transferEncodings =
            new ArraySet<TransferEncoding>(TransferEncoding.class);
    
    protected final ArraySet<ContentEncoding> contentEncodings =
            new ArraySet<ContentEncoding>(ContentEncoding.class);

    protected final boolean chunkingEnabled;

    /**
     * The maximum request payload remainder (in bytes) HttpServerFilter will try
     * to swallow after HTTP request processing is over in order to keep the
     * connection alive. If the remainder is too large - HttpServerFilter will
     * close the connection.
     */
    protected long maxPayloadRemainderToSkip = -1;
    
    /**
     * File cache probes
     */
    protected final DefaultMonitoringConfig<HttpProbe> monitoringConfig =
            new DefaultMonitoringConfig<HttpProbe>(HttpProbe.class) {

        @Override
        public Object createManagementObject() {
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
    abstract boolean decodeInitialLineFromBuffer(FilterChainContext ctx,
                                       HttpPacketParsing httpPacket,
                                       HeaderParsingState parsingState,
                                       Buffer input);

    /**
     * Method is responsible for parsing initial line of HTTP message (different
     * for {@link HttpRequestPacket} and {@link HttpResponsePacket}).
     *
     * @param httpPacket HTTP packet, which is being parsed
     * @param parsingState HTTP packet parsing state
     * @param input input
     * @param end index of the last available byte in the input array (offset is passed inside parsingState)
     *
     * @return <tt>true</tt>, if initial line has been parsed,
     * or <tt>false</tt> otherwise.
     */
    abstract boolean decodeInitialLineFromBytes(FilterChainContext ctx,
                                       HttpPacketParsing httpPacket,
                                       HeaderParsingState parsingState,
                                       byte[] input,
                                       int end);
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
     * Invoked when the initial response line has been  encoded in preparation
     * to being transmitted to the user-agent.
     * </p>
     *
     * @param httpHeader {@link HttpHeader}, which represents HTTP packet header
     * @param ctx processing context.
     *
     * @since 2.1.2
     */
    protected abstract void onInitialLineEncoded(final HttpHeader httpHeader,
                                                 final FilterChainContext ctx);


    /**
     * <p>
     * Invoked when HTTP headers portion comes for {@link HttpHeader} message.
     * Depending on the transfer encoding being used by the current request, this method may be
     * invoked multiple times.
     * </p>
     *
     * @param httpHeader {@link HttpHeader}, which represents HTTP packet header
     * @param headers the headers portion, that has been parsed
     * @param ctx processing context.
     */
    protected abstract void onHttpHeadersParsed(final HttpHeader httpHeader,
                                                final MimeHeaders headers,
                                                final FilterChainContext ctx);


    /**
     * <p>
     *  Invoked when HTTP headers have been encoded in preparation to being
     *  transmitted to the user-agent.
     * </p>
     *
     * @param httpHeader {@link HttpHeader}, which represents HTTP packet header
     * @param ctx processing context.
     *
     * @since 2.1.2
     */
    protected abstract void onHttpHeadersEncoded(final HttpHeader httpHeader,
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
     *  Invoked when a HTTP body chunk has been encoded in preparation to being
     *  transmitted to the user-agent.
     * </p>
     *
     * @param content {@link HttpContent}, which represents HTTP packet header
     * @param ctx processing context.
     *
     * @since 2.1.2
     */
    protected abstract void onHttpContentEncoded(final HttpContent content,
                                                 final FilterChainContext ctx);


    /**
     * <p>
     * Callback which is invoked when parsing an HTTP message header fails.
     * The processing logic has to take care about error handling and following
     * connection closing.
     * </p>
     *
     * @param httpHeader {@link HttpHeader}, which represents HTTP packet header
     * @param ctx the {@link FilterChainContext} processing this request
     * @param t the cause of the error
     * @throws java.io.IOException
     */
    protected abstract void onHttpHeaderError(HttpHeader httpHeader,
                                        FilterChainContext ctx,
                                        Throwable t) throws IOException;

    /**
     * <p>
     * Callback which is invoked when parsing an HTTP message payload fails.
     * The processing logic has to take care about error handling and following
     * connection closing.
     * </p>
     *
     * @param httpHeader {@link HttpHeader}, which represents HTTP packet header
     * @param ctx the {@link FilterChainContext} processing this request
     * @param t the cause of the error
     * @throws java.io.IOException
     */
    protected abstract void onHttpContentError(HttpHeader httpHeader,
                                        FilterChainContext ctx,
                                        Throwable t) throws IOException;
    
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
     * @return the maximum request payload remainder (in bytes) HttpServerFilter
     * will try to swallow after HTTP request processing is over in order to
     * keep the connection alive. If the remainder is too large - the connection
     * will be closed. <tt>-1</tt> means no limits will be applied.
     * 
     * @since 2.3.13
     */
    public long getMaxPayloadRemainderToSkip() {
        return maxPayloadRemainderToSkip;
    }

    /**
     * Set the maximum request payload remainder (in bytes) HttpServerFilter
     * will try to swallow after HTTP request processing is over in order to
     * keep the connection alive. If the remainder is too large - the connection
     * will be closed. <tt>-1</tt> means no limits will be applied.
     * 
     * @param maxPayloadRemainderToSkip
     * @since 2.3.13
     */
    public void setMaxPayloadRemainderToSkip(long maxPayloadRemainderToSkip) {
        this.maxPayloadRemainderToSkip = maxPayloadRemainderToSkip;
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
     * Return <code>true</code> if chunked transfer-encoding may be used.
     *
     * @return <code>true</code> if chunked transfer-encoding may be used.
     *
     * @since 2.1.2
     */
    protected boolean isChunkingEnabled() {
        return chunkingEnabled;
    }

    //------------------------------------------------ Parsing
    
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
     * @param httpHeader the current {@link HttpHeader}, which is being processed.
     *
     * @return {@link NextAction}
     * @throws IOException
     */
    public final NextAction handleRead(final FilterChainContext ctx,
            final HttpHeader httpHeader) throws IOException {

        // Get the input buffer
        Buffer input = ctx.getMessage();

        // Get the Connection
        final Connection connection = ctx.getConnection();

        HttpProbeNotifier.notifyDataReceived(this, connection, input);

        final HttpPacketParsing parsingState = httpHeader.getParsingState();
        
         // Check if HTTP header has been parsed
        final boolean wasHeaderParsed = parsingState == null ||
                parsingState.isHeaderParsed();
        
        if (!wasHeaderParsed) {
            try {
                assert parsingState != null;

                // if header wasn't parsed - parse
                if (!decodeHttpPacket(ctx, parsingState, input)) {
                    // if there is not enough data to parse the HTTP header - stop
                    // filterchain processing
                    return ctx.getStopAction(input);
                } else {
                    final int headerSizeInBytes = input.position();

                    if (!httpHeader.getUpgradeDC().isNull()) {
                        onIncomingUpgrade(ctx, httpHeader);
                    }
                    
                    if (onHttpHeaderParsed(httpHeader, input, ctx)) {
                        throw new IllegalStateException("Bad HTTP headers");
                    }

                    // if headers get parsed - set the flag
                    parsingState.setHeaderParsed(true);

                    // recycle header parsing state
                    parsingState.getHeaderParsingState().recycle();

                    final Buffer remainder = input.hasRemaining()
                            ? input.split(input.position()) : Buffers.EMPTY_BUFFER;
                    httpHeader.setHeaderBuffer(input);
                    input = remainder;

                    if (httpHeader.isExpectContent()) {
                        setTransferEncodingOnParsing(httpHeader);
                        setContentEncodingsOnParsing(httpHeader);
                    }

                    HttpProbeNotifier.notifyHeaderParse(this, connection,
                            httpHeader, headerSizeInBytes);
                }
            } catch (Exception e) {
                LOGGER.log(Level.FINE, "Error parsing HTTP header", e);
                
                HttpProbeNotifier.notifyProbesError(this, connection, httpHeader, e);
                onHttpHeaderError(httpHeader, ctx, e);

                // make the connection deaf to any following input
                // onHttpError call will take care of error processing
                // and closing the connection
                final NextAction suspendAction = ctx.getSuspendAction();
                ctx.completeAndRecycle();
                return suspendAction;
            }
        }

        if (httpHeader.isExpectContent()) {
            
            if (httpHeader.isIgnoreContentModifiers()) {
                // If this header suggests to ignore content modifiers - just pass
                // the payload as it is
                // Build HttpContent message on top of existing content chunk and parsed Http message header
                final HttpContent message = HttpContent.create(httpHeader);
                message.setContent(input);
                ctx.setMessage(message);

                // Instruct filterchain to continue the processing.
                return ctx.getInvokeAction();
            }
            
            try {
                final TransferEncoding transferEncoding =
                        httpHeader.getTransferEncoding();

                // Check if appropriate HTTP transfer encoder was found
                if (transferEncoding != null) {
                    return decodeWithTransferEncoding(ctx, httpHeader,
                            input, wasHeaderParsed);
                }

                if (input.hasRemaining()) {
                    // Transfer-encoding is unknown and there is no content-length header

                    // Build HttpContent message on top of existing content chunk and parsed Http message header
                    final HttpContent message = HttpContent.create(httpHeader);
                    message.setContent(input);

                    final HttpContent decodedContent = decodeContent(ctx, message);
                    if (decodedContent != null) {
                        if (httpHeader.isSkipRemainder()) {  // Do we skip the remainder?
                            if (!checkRemainderOverflow(httpHeader,
                                    decodedContent.getContent().remaining())) {
                                // if remainder is too large - close the connection
                                httpHeader.getProcessingState().getHttpContext().close();
                            }
                            
                            return ctx.getStopAction();
                        }

                        HttpProbeNotifier.notifyContentChunkParse(this,
                                connection, decodedContent);

                        ctx.setMessage(decodedContent);

                        // Instruct filterchain to continue the processing.
                        return ctx.getInvokeAction();
                    }
                }
            } catch (Exception e) {
                LOGGER.log(Level.FINE, "Error parsing HTTP payload", e);
                
                httpHeader.getProcessingState().setError(true);
                HttpProbeNotifier.notifyProbesError(this, connection,
                        httpHeader, e);
                onHttpContentError(httpHeader, ctx, e);
                onHttpPacketParsed(httpHeader, ctx);

                // create broken content message
                final HttpContent brokenContent =
                        HttpBrokenContent.builder(httpHeader).error(e).build();

                ctx.setMessage(brokenContent);
                return ctx.getInvokeAction();
            }
            
            // We expect more content
            if (!wasHeaderParsed) { // If HTTP header was just parsed
                // create empty message, which contains headers
                final HttpContent emptyContent = HttpContent.create(httpHeader);

                HttpProbeNotifier.notifyContentChunkParse(this,
                        connection, emptyContent);

                ctx.setMessage(emptyContent);
                return ctx.getInvokeAction();
            } else {
                return ctx.getStopAction();
            }
        } else { // We don't expect content
            // process headers
            onHttpPacketParsed(httpHeader, ctx);
            final HttpContent emptyContent = HttpContent.create(httpHeader, true);

            HttpProbeNotifier.notifyContentChunkParse(this,
                    connection, emptyContent);

            ctx.setMessage(emptyContent);
            if (input.remaining() > 0) {
                return ctx.getInvokeAction(input);
            }
            return ctx.getInvokeAction();
        }
    }

    protected boolean decodeHttpPacket(final FilterChainContext ctx,
                                       final HttpPacketParsing httpPacket,
                                       final Buffer input) {

        if (input.hasArray()) {
            return decodeHttpPacketFromBytes(ctx, httpPacket, input);
        } else {
            return decodeHttpPacketFromBuffer(ctx, httpPacket, input);
        }
    }

    protected boolean decodeHttpPacketFromBytes(final FilterChainContext ctx,
                                                 final HttpPacketParsing httpPacket,
                                                 final Buffer inputBuffer) {
        
        final HeaderParsingState parsingState = httpPacket.getHeaderParsingState();
        
        parsingState.arrayOffset = inputBuffer.arrayOffset();
        final int end = parsingState.arrayOffset + inputBuffer.limit();
        
        final byte[] input = inputBuffer.array();
        
        switch (parsingState.state) {
            case 0: { // parsing initial line
                if (!decodeInitialLineFromBytes(ctx, httpPacket, parsingState, input, end)) {
                    parsingState.checkOverflow(inputBuffer.limit(),
                            "HTTP packet intial line is too large");
                    return false;
                }

                parsingState.state++;
            }

            case 1: { // parsing headers
                if (!parseHeadersFromBytes((HttpHeader) httpPacket,
                        httpPacket.getHeaders(), parsingState, input, end)) {
                    parsingState.checkOverflow(inputBuffer.limit(),
                            "HTTP packet header is too large");
                    return false;
                }

                parsingState.state++;
            }

            case 2: { // Headers are ready
                onHttpHeadersParsed((HttpHeader) httpPacket,
                        httpPacket.getHeaders(), ctx);
                if (httpPacket.getHeaders().size() == 0) {
                    // no headers - do not expect further content
                    ((HttpHeader) httpPacket).setExpectContent(false);
                }
                inputBuffer.position(parsingState.offset);
                return true;
            }

            default: throw new IllegalStateException();
        }
    }
    
    protected boolean parseHeadersFromBytes(final HttpHeader httpHeader,
                                   final MimeHeaders mimeHeaders,
                                   final HeaderParsingState parsingState,
                                   final byte[] input,
                                   final int end) {
        do {
            if (parsingState.subState == 0) {
                final int eol = checkEOL(parsingState, input, end);
                if (eol == 0) { // EOL
                    return true;
                } else if (eol == -2) { // not enough data
                    return false;
                }
            }

            if (!parseHeaderFromBytes(httpHeader, mimeHeaders, parsingState, input, end)) {
                return false;
            }

        } while (true);
    }

    protected static boolean parseHeaderFromBytes(final HttpHeader httpHeader,
            final MimeHeaders mimeHeaders, final HeaderParsingState parsingState,
            final byte[] input, final int end) {
        
        final int arrayOffs = parsingState.arrayOffset;
        final int packetLim = arrayOffs + parsingState.packetLimit;
        while (true) {
            final int subState = parsingState.subState;

            switch (subState) {
                case 0: { // start to parse the header
                    parsingState.start = parsingState.offset;
                    parsingState.subState++;
                }
                case 1: { // parse header name
                    if (!parseHeaderName(httpHeader, mimeHeaders, parsingState, input, end)) {
                        return false;
                    }

                    parsingState.subState++;
                    parsingState.start = -1;
                }

                case 2: { // skip value preceding spaces
                    final int nonSpaceIdx = skipSpaces(input,
                            arrayOffs + parsingState.offset,
                            end,
                            packetLim) - arrayOffs;
                    if (nonSpaceIdx < 0) {
                        parsingState.offset = end - arrayOffs;
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
                    final int result = parseHeaderValue(httpHeader, parsingState, input, end);
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
            final byte[] input, final int end) {
        final int arrayOffs = parsingState.arrayOffset;
        
        final int limit = Math.min(end, arrayOffs + parsingState.packetLimit);
        final int start = arrayOffs + parsingState.start;
        int offset = arrayOffs + parsingState.offset;

        while(offset < limit) {
            byte b = input[offset];
            if (b == Constants.COLON) {

                parsingState.headerValueStorage =
                        mimeHeaders.addValue(input, start, offset - start);
                parsingState.offset = offset + 1 - arrayOffs;
                finalizeKnownHeaderNames(httpHeader, parsingState, input,
                        start, offset);

                return true;
            } else if ((b >= Constants.A) && (b <= Constants.Z)) {
                b -= Constants.LC_OFFSET;
                input[offset] =  b;
            }

            offset++;
        }

        parsingState.offset = offset - arrayOffs;
        return false;
    }

    protected static int parseHeaderValue(final HttpHeader httpHeader,
            final HeaderParsingState parsingState, final byte[] input,
            final int end) {
        
        final int arrayOffs = parsingState.arrayOffset;        
        final int limit = Math.min(end, arrayOffs + parsingState.packetLimit);
        
        int offset = arrayOffs + parsingState.offset;

        final boolean hasShift = (offset != (arrayOffs + parsingState.checkpoint));
        
        while (offset < limit) {
            final byte b = input[offset];
            if (b == Constants.CR) {
            } else if (b == Constants.LF) {
                // Check if it's not multi line header
                if (offset + 1 < limit) {
                    final byte b2 = input[offset + 1];
                    if (b2 == Constants.SP || b2 == Constants.HT) {
                        input[arrayOffs + parsingState.checkpoint++] = b2;
                        parsingState.offset = offset + 2 - arrayOffs;
                        return -2;
                    } else {
                        parsingState.offset = offset + 1 - arrayOffs;
                        finalizeKnownHeaderValues(httpHeader, parsingState, input,
                                arrayOffs + parsingState.start,
                                arrayOffs + parsingState.checkpoint2);
                        parsingState.headerValueStorage.setBytes(input,
                                arrayOffs + parsingState.start,
                                arrayOffs + parsingState.checkpoint2);
                        return 0;
                    }
                }

                parsingState.offset = offset - arrayOffs;
                return -1;
            } else if (b == Constants.SP) {
                if (hasShift) {
                    input[arrayOffs + parsingState.checkpoint++] = b;
                } else {
                    parsingState.checkpoint++;
                }
            } else {
                if (hasShift) {
                    input[arrayOffs + parsingState.checkpoint++] = b;
                } else {
                    parsingState.checkpoint++;
                }
                parsingState.checkpoint2 = parsingState.checkpoint;
            }

            offset++;
        }
        parsingState.offset = offset - arrayOffs;
        return -1;
    }
    
    private static void finalizeKnownHeaderNames(final HttpHeader httpHeader,
            final HeaderParsingState parsingState, final byte[] input,
            final int start, final int end) {
        
        final int size = end - start;
        if (size == Header.ContentLength.getLowerCaseBytes().length) {
            if (ByteChunk.equalsIgnoreCaseLowerCase(input, start, end,
                    Header.ContentLength.getLowerCaseBytes())) {
                parsingState.isContentLengthHeader = true;
            }
        } else if (size == Header.TransferEncoding.getLowerCaseBytes().length) {
            if (ByteChunk.equalsIgnoreCaseLowerCase(input, start, end,
                    Header.TransferEncoding.getLowerCaseBytes())) {
                parsingState.isTransferEncodingHeader = true;
            }
        } else if (size == Header.Upgrade.getLowerCaseBytes().length) {
            if (ByteChunk.equalsIgnoreCaseLowerCase(input, start, end,
                    Header.Upgrade.getLowerCaseBytes())) {
                parsingState.isUpgradeHeader = true;
            }
        } else if (size == Header.Expect.getLowerCaseBytes().length) {
            if (ByteChunk.equalsIgnoreCaseLowerCase(input, start, end,
                    Header.Expect.getLowerCaseBytes())) {
                ((HttpRequestPacket) httpHeader).requiresAcknowledgement(true);
            }
        }
    }

    private static void finalizeKnownHeaderValues(final HttpHeader httpHeader,
            final HeaderParsingState parsingState, final byte[] input,
            final int start, final int end) {

        if (parsingState.isContentLengthHeader) {
            final long contentLengthLong = Ascii.parseLong(input, start, end - start);
            
            if (parsingState.contentLengthHeadersCount++ == 0) {
                // There may be multiple content lengths.  Only use the
                // first value.
                httpHeader.setContentLengthLong(contentLengthLong);
            } else if (httpHeader.getContentLength() != contentLengthLong) {
                parsingState.contentLengthsDiffer = true;
            }
            
            parsingState.isContentLengthHeader = false;
        } else if (parsingState.isTransferEncodingHeader) {
            // here we do case-insensitive ByteChunk.startsWith(...)
            if (end - start >= CHUNKED_ENCODING_BYTES.length &&
                    ByteChunk.equalsIgnoreCaseLowerCase(input, start,
                    start + CHUNKED_ENCODING_BYTES.length,
                    CHUNKED_ENCODING_BYTES)) {
                httpHeader.setChunked(true);
            }
            parsingState.isTransferEncodingHeader = false;            
        } else if (parsingState.isUpgradeHeader) {
            httpHeader.getUpgradeDC().setBytes(input, start, end);
            parsingState.isUpgradeHeader = false;
        }
    }
    
    protected boolean decodeHttpPacketFromBuffer(final FilterChainContext ctx,
                                                 final HttpPacketParsing httpPacket,
                                                 final Buffer input) {
        final HeaderParsingState parsingState = httpPacket.getHeaderParsingState();
        switch (parsingState.state) {
            case 0: { // parsing initial line
                if (!decodeInitialLineFromBuffer(ctx, httpPacket, parsingState, input)) {
                    parsingState.checkOverflow(input.limit(),
                            "HTTP packet intial line is too large");
                    return false;
                }

                parsingState.state++;
            }

            case 1: { // parsing headers
                if (!parseHeadersFromBuffer((HttpHeader) httpPacket,
                        httpPacket.getHeaders(), parsingState, input)) {
                    parsingState.checkOverflow(input.limit(),
                            "HTTP packet header is too large");
                    return false;
                }

                parsingState.state++;
            }

            case 2: { // Headers are ready
                onHttpHeadersParsed((HttpHeader) httpPacket,
                        httpPacket.getHeaders(), ctx);
                if (httpPacket.getHeaders().size() == 0) {
                    // no headers - do not expect further content
                    ((HttpHeader) httpPacket).setExpectContent(false);
                }
                input.position(parsingState.offset);
                return true;
            }

            default: throw new IllegalStateException();
        }
    }
    
    protected boolean parseHeadersFromBuffer(final HttpHeader httpHeader,
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
            }

            if (!parseHeaderFromBuffer(httpHeader, mimeHeaders, parsingState, input)) {
                return false;
            }

        } while (true);
    }
    
    protected static boolean parseHeaderFromBuffer(final HttpHeader httpHeader,
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
        final int start = parsingState.start;
        int offset = parsingState.offset;

        while(offset < limit) {
            byte b = input.get(offset);
            if (b == Constants.COLON) {

                parsingState.headerValueStorage =
                        mimeHeaders.addValue(input, start, offset - start);
                parsingState.offset = offset + 1;
                finalizeKnownHeaderNames(httpHeader, parsingState, input,
                        start, offset);

                return true;
            } else if ((b >= Constants.A) && (b <= Constants.Z)) {
                b -= Constants.LC_OFFSET;
                input.put(offset, b);
            }

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
                        finalizeKnownHeaderValues(httpHeader, parsingState, input,
                                parsingState.start, parsingState.checkpoint2);
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

    private static void finalizeKnownHeaderNames(final HttpHeader httpHeader,
            final HeaderParsingState parsingState, final Buffer input,
            final int start, final int end) {
        
        final int size = end - start;
        if (size == Header.ContentLength.getLowerCaseBytes().length) {
            if (BufferChunk.equalsIgnoreCaseLowerCase(input, start, end,
                    Header.ContentLength.getLowerCaseBytes())) {
                parsingState.isContentLengthHeader = true;
            }
        } else if (size == Header.TransferEncoding.getLowerCaseBytes().length) {
            if (BufferChunk.equalsIgnoreCaseLowerCase(input, start, end,
                    Header.TransferEncoding.getLowerCaseBytes())) {
                parsingState.isTransferEncodingHeader = true;
            }
        } else if (size == Header.Upgrade.getLowerCaseBytes().length) {
            if (BufferChunk.equalsIgnoreCaseLowerCase(input, start, end,
                    Header.Upgrade.getLowerCaseBytes())) {
                parsingState.isUpgradeHeader = true;
            }
        } else if (size == Header.Expect.getLowerCaseBytes().length) {
            if (BufferChunk.equalsIgnoreCaseLowerCase(input, start, end,
                    Header.Expect.getLowerCaseBytes())) {
                ((HttpRequestPacket) httpHeader).requiresAcknowledgement(true);
            }
        }
    }

    private static void finalizeKnownHeaderValues(final HttpHeader httpHeader,
            final HeaderParsingState parsingState, final Buffer input,
            final int start, final int end) {

        if (parsingState.isContentLengthHeader) {
            final long contentLengthLong = Ascii.parseLong(input, start, end - start);
            
            if (parsingState.contentLengthHeadersCount++ == 0) {
                // There may be multiple content lengths.  Only use the
                // first value.
                httpHeader.setContentLengthLong(contentLengthLong);
            } else if (httpHeader.getContentLength() != contentLengthLong) {
                parsingState.contentLengthsDiffer = true;
            }
            
            parsingState.isContentLengthHeader = false;
        } else if (parsingState.isTransferEncodingHeader) {
            if (BufferChunk.startsWith(input, start, end,
                    CHUNKED_ENCODING_BYTES)) {
                httpHeader.setChunked(true);
            }
            parsingState.isTransferEncodingHeader = false;            
        } else if (parsingState.isUpgradeHeader) {
            httpHeader.getUpgradeDC().setBuffer(input, start, end);
            parsingState.isUpgradeHeader = false;
        }
    }
    
    private NextAction decodeWithTransferEncoding(final FilterChainContext ctx,
            final HttpHeader httpHeader, final Buffer input,
            final boolean wasHeaderParsed) throws IOException {

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
                    if (!httpHeader.getProcessingState().isStayAlive()) {
                        httpHeader.getProcessingState().getHttpContext().close();
                        return ctx.getStopAction();
                    } else if (remainderBuffer != null) {
                        // if there is a remainder with the next HTTP message - rerun this filter
                        ctx.setMessage(remainderBuffer);
                        return ctx.getRerunFilterAction();
                    }
                    
                    return ctx.getStopAction();
                }
                
                if (!checkRemainderOverflow(httpHeader,
                        httpContent.getContent().remaining())) {
                    // if remainder is too large - close the connection
                    httpHeader.getProcessingState().getHttpContext().close();
                } else if (remainderBuffer != null) {
                    // if there is a remainder with the next chunk - rerun this filter
                    ctx.setMessage(remainderBuffer);
                    return ctx.getRerunFilterAction();
                }
                
                // if remainder could be still skept - just stop
                return ctx.getStopAction();
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
                final HttpContent emptyContent = HttpContent.create(httpHeader, isLast);
                
                HttpProbeNotifier.notifyContentChunkParse(this, connection, emptyContent);
                // Instruct filterchain to continue the processing.
                ctx.setMessage(emptyContent);
                return ctx.getInvokeAction(remainderBuffer);
            }
        }

        if (!wasHeaderParsed || isLast) {
            final HttpContent emptyContent = HttpContent.create(httpHeader, isLast);
            HttpProbeNotifier.notifyContentChunkParse(this, connection, emptyContent);
            ctx.setMessage(emptyContent);
            return ctx.getInvokeAction(hasRemainder ? remainderBuffer : null);
        } else {
            return ctx.getStopAction(hasRemainder ? remainderBuffer : null);
        }
    }

    final HttpContent decodeContent(final FilterChainContext ctx,
                                    HttpContent httpContent) {

        if (!httpContent.getContent().hasRemaining()
                || isResponseToHeadRequest(httpContent.getHttpHeader())) {
            
            if (httpContent.isLast()) {
                // If it's HttpContent is empty, but it's the last one - return it
                // so it would be passed upstream to next filter
                return httpContent;
            } else {
                // Otherwise recycle and return null
                httpContent.recycle();
                return null;
            }
        }

        final Connection connection = ctx.getConnection();
        
        final MemoryManager memoryManager = connection.getMemoryManager();
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

            HttpProbeNotifier.notifyContentEncodingParseResult(this,
                                                               connection,
                                                               httpHeader,
                                                               decodedContent.getContent(),
                                                               encoding);

            httpContent = decodedContent;
        }
        onHttpContentParsed(httpContent, ctx);
        return httpContent;
    }

    //------------------------------------------------ Serializing
    
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
                HttpProbeNotifier.notifyProbesError(this, connection, input, re);
                throw re;
            }
        }

        return ctx.getInvokeAction();
    }
    
    /**
     * The method is called, when a peer sends an upgrade HTTP packet
     * (either request or response).
     * 
     * @param ctx
     * @param httpHeader 
     */
    protected void onIncomingUpgrade(final FilterChainContext ctx,
            final HttpHeader httpHeader) {
        httpHeader.setIgnoreContentModifiers(true);
        
        ctx.notifyUpstream(
                HttpEvents.createIncomingUpgradeEvent(httpHeader));
    }
    
    protected void onOutgoingUpgrade(final FilterChainContext ctx,
            final HttpHeader httpHeader) {
        httpHeader.setIgnoreContentModifiers(true);

        ctx.notifyUpstream(
                HttpEvents.createOutgoingUpgradeEvent(httpHeader));
    }
    
    protected Buffer encodeHttpPacket(final FilterChainContext ctx, final HttpPacket input) {
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

        return encodeHttpPacket(ctx, httpHeader, httpContent, false);
    }

    protected final Buffer encodeHttpPacket(final FilterChainContext ctx,
            final HttpHeader httpHeader, final HttpContent httpContent,
            final boolean isContentAlreadyEncoded) {
        final Connection connection = ctx.getConnection();
        final MemoryManager memoryManager = ctx.getMemoryManager();

        Buffer encodedBuffer = null;
        
        if (!httpHeader.isCommitted()) {
            
            if (!httpHeader.getUpgradeDC().isNull()) {
                onOutgoingUpgrade(ctx, httpHeader);
            }
            
            if (!httpHeader.isRequest()) {
                final HttpResponsePacket response = (HttpResponsePacket) httpHeader;
                if (response.isAcknowledgement()) {
                    encodedBuffer = memoryManager.allocate(128);
                    encodedBuffer = encodeInitialLine(httpHeader,
                                                      encodedBuffer,
                                                      memoryManager);
                    encodedBuffer = put(memoryManager,
                                        encodedBuffer,
                                        CRLF_BYTES);
                    encodedBuffer = put(memoryManager,
                                        encodedBuffer,
                                        CRLF_BYTES);
                    onInitialLineEncoded(httpHeader, ctx);
                    encodedBuffer.trim();
                    encodedBuffer.allowBufferDispose(true);

                    HttpProbeNotifier.notifyHeaderSerialize(this, connection,
                            httpHeader, encodedBuffer);

                    response.acknowledged();
                    return encodedBuffer; // DO NOT MARK COMMITTED
                }
            }
            
            if (httpHeader.isExpectContent()) {
                setContentEncodingsOnSerializing(httpHeader);
                setTransferEncodingOnSerializing(ctx,
                                                 httpHeader,
                                                 httpContent);
            }

            encodedBuffer = memoryManager.allocateAtLeast(2048);

            encodedBuffer = encodeInitialLine(httpHeader, encodedBuffer, memoryManager);
            encodedBuffer = put(memoryManager, encodedBuffer, CRLF_BYTES);
            onInitialLineEncoded(httpHeader, ctx);

            encodedBuffer = encodeKnownHeaders(memoryManager, encodedBuffer,
                    httpHeader);

            final MimeHeaders mimeHeaders = httpHeader.getHeaders();
            final byte[] tempEncodingBuffer = httpHeader.getTempHeaderEncodingBuffer();
            encodedBuffer = encodeMimeHeaders(memoryManager, encodedBuffer, mimeHeaders, tempEncodingBuffer);
            onHttpHeadersEncoded(httpHeader, ctx);
            encodedBuffer = put(memoryManager, encodedBuffer, CRLF_BYTES);
            encodedBuffer.trim();
            encodedBuffer.allowBufferDispose(true);
            
            httpHeader.setCommitted(true);

            HttpProbeNotifier.notifyHeaderSerialize(this, connection, httpHeader,
                    encodedBuffer);
        }
        
        
        if (httpContent != null && httpHeader.isExpectContent()) {

            HttpProbeNotifier.notifyContentChunkSerialize(this, connection, httpContent);
            
            final HttpContent encodedHttpContent = isContentAlreadyEncoded ?
                    httpContent : encodeContent(connection, httpContent);
            
            if (encodedHttpContent == null) {
                return encodedBuffer;
            }
            
            final TransferEncoding contentEncoder = httpHeader.getTransferEncoding();

            final Buffer content = serializeWithTransferEncoding(ctx,
                                                   encodedHttpContent,
                                                   contentEncoder);
            onHttpContentEncoded(encodedHttpContent, ctx);

            if (content != null && content.hasRemaining()) {
                encodedBuffer = Buffers.appendBuffers(memoryManager,
                        encodedBuffer, content);
            }

            if (encodedBuffer != null && encodedBuffer.isComposite()) {
                // If during buffer appending - composite buffer was created -
                // allow buffer disposing
                encodedBuffer.allowBufferDispose(true);
                ((CompositeBuffer) encodedBuffer).disposeOrder(DisposeOrder.FIRST_TO_LAST);
            }
        }

        return encodedBuffer;
    }

    protected static Buffer encodeKnownHeaders(final MemoryManager memoryManager,
            Buffer buffer, final HttpHeader httpHeader) {

//        buffer = httpHeader.serializeContentType(memoryManager, buffer);
        
        final CacheableDataChunk name = CacheableDataChunk.create();
        final CacheableDataChunk value = CacheableDataChunk.create();

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

    private static Buffer encodeContentEncodingHeader(final MemoryManager memoryManager,
            Buffer buffer, final HttpHeader httpHeader,
            final CacheableDataChunk name, final CacheableDataChunk value) {

        final List<ContentEncoding> packetContentEncodings =
                httpHeader.getContentEncodings(true);

        name.setBytes(Header.ContentEncoding.toByteArray());
        value.reset();
        httpHeader.extractContentEncoding(value);
        boolean needComma = !value.isNull();
        final byte[] tempBuffer = httpHeader.getTempHeaderEncodingBuffer();
        
        buffer = encodeMimeHeader(memoryManager, buffer, name, value, tempBuffer, false);
        for (ContentEncoding encoding : packetContentEncodings) {
            if (needComma) {
                buffer = put(memoryManager, buffer, Constants.COMMA);
            }
            
            buffer = put(memoryManager, buffer, tempBuffer, encoding.getName());
            needComma = true;
        }

        buffer = put(memoryManager, buffer, CRLF_BYTES);
        
        return buffer;
    }
    
    protected static Buffer encodeMimeHeaders(final MemoryManager memoryManager,
                                              Buffer buffer,
                                              final MimeHeaders mimeHeaders,
                                              final byte[] tempEncodingBuffer) {
        final int mimeHeadersNum = mimeHeaders.size();

        for (int i = 0; i < mimeHeadersNum; i++) {
            if (!mimeHeaders.setSerialized(i, true)) {
                final DataChunk value = mimeHeaders.getValue(i);
                if (!value.isNull()) {
                    buffer = encodeMimeHeader(memoryManager,
                                              buffer,
                                              mimeHeaders.getName(i),
                                              value,
                                              tempEncodingBuffer,
                                              true);
                }
            }
        }

        return buffer;
    }

    protected static Buffer encodeMimeHeader(final MemoryManager memoryManager,
                                             Buffer buffer,
                                             final DataChunk name,
                                             final DataChunk value,
                                             final byte[] tempBuffer,
                                             final boolean encodeLastCRLF) {

        buffer = put(memoryManager, buffer, tempBuffer, name);
        buffer = put(memoryManager, buffer, HttpCodecFilter.COLON_BYTES);
        buffer = put(memoryManager, buffer, tempBuffer, value);
        
        if (encodeLastCRLF) {
            buffer = put(memoryManager, buffer, CRLF_BYTES);
        }

        return buffer;
    }
    

    final void setTransferEncodingOnParsing(HttpHeader httpHeader) {
        if (httpHeader.isIgnoreContentModifiers()) {
            // ignore the transfer encoding
            return;
        }
        
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
        
        if (httpHeader.isIgnoreContentModifiers()) {
            // ignore the transfer encoding
            return;
        }

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

            HttpProbeNotifier.notifyContentEncodingSerializeResult(this,
                                                                   connection,
                                                                   httpHeader,
                                                                   encodedContent.getContent(),
                                                                   encoding);

            httpContent = encodedContent;
        }

        return httpContent;
    }
    
    final void setContentEncodingsOnParsing(final HttpHeader httpHeader) {
        if (httpHeader.isIgnoreContentModifiers()) {
            // ignore the content encoding
            return;
        }
        
        final DataChunk bc =
                httpHeader.getHeaders().getValue(Header.ContentEncoding);
        
        if (bc != null) {
            final List<ContentEncoding> encodings = httpHeader.getContentEncodings(true);
            int currentIdx = 0;

            int commaIdx;
            do {
                commaIdx = bc.indexOf(',', currentIdx);
                final ContentEncoding ce = lookupContentEncoding(bc, currentIdx,
                        commaIdx >= 0 ? commaIdx : bc.getLength());
                if (ce != null && ce.wantDecode(httpHeader)) {
                    encodings.add(0, ce);
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
        if (httpHeader.isIgnoreContentModifiers()) {
            // ignore the content encoding
            return;
        }
        
        // If content encoders have been already set - skip lookup phase
        if (httpHeader.isContentEncodingsSelected()) return;

        httpHeader.setContentEncodingsSelected(true);
        
        final ContentEncoding[] encodingsLibrary = contentEncodings.getArray();
        if (encodingsLibrary == null) return;

        final DataChunk bc =
                httpHeader.getHeaders().getValue(Header.ContentEncoding);
        
        final boolean isSomeEncodingApplied = bc != null && bc.getLength() > 0;
        if (isSomeEncodingApplied && bc.equals("identity")) {
            // remove the header as it's illegal to include the content-encoding
            // header with a value of identity.  Since the value is identity,
            // return without applying any transformation.
            httpHeader.getHeaders().removeHeader(Header.ContentEncoding);
            return;
        }

        final List<ContentEncoding> httpPacketEncoders = httpHeader.getContentEncodings(true);
        
        for (ContentEncoding encoding : encodingsLibrary) {
            if (isSomeEncodingApplied) {
                // If the current encoding is already applied - don't add it to httpPacketEncoders
                if (lookupAlias(encoding, bc, 0)) {
                    continue;
                }
            }

            if (encoding.wantEncode(httpHeader)) {
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
            HttpProbeNotifier.notifyTransferEncodingSerialize(this, ctx.getConnection(),
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
     * 
     * @param connection {@link Connection}
     * @return <tt>true</tt>, if the {@link Connection} is secured, or <tt>false</tt>
     *          otherwise
     */
    protected static boolean isSecure(final Connection connection) {
        return SSLUtils.getSSLEngine(connection) != null;
    }

    /**
     * Determine if we must drop the connection because of the HTTP status
     * code. Use the same list of codes as Apache/httpd.
     */
    protected static boolean statusDropsConnection(int status) {
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
    
    /**
     * 
     * @param httpHeader
     * @param payloadChunkSize
     * @return <tt>true</tt>, if payload remainder read doesn't exceed the limit
     */
    private boolean checkRemainderOverflow(final HttpHeader httpHeader,
            final int payloadChunkSize) {
        if (maxPayloadRemainderToSkip < 0) {
            return true;
        }
        
        final ContentParsingState parsingState =
                ((HttpPacketParsing) httpHeader).getContentParsingState();
        final long newSize = (parsingState.remainderBytesRead += payloadChunkSize);
        
        return newSize <= maxPayloadRemainderToSkip;
    }

    // ---------------------------------------------------------- Nested Classes
    
    public static final class HeaderParsingState {
        public int packetLimit;

        public int state;
        public int subState;

        public int start;
        public int offset;
        public int checkpoint = -1; // extra parsing state field
        public int checkpoint2 = -1; // extra parsing state field

        public int arrayOffset;

        public DataChunk headerValueStorage;
        public HttpCodecFilter codecFilter;

        public long parsingNumericValue;

        public boolean isContentLengthHeader;
        public int contentLengthHeadersCount;   // number of Content-Length headers in the HTTP header
        public boolean contentLengthsDiffer;
        public boolean isTransferEncodingHeader;
        public boolean isUpgradeHeader;

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
            contentLengthHeadersCount = 0;
            contentLengthsDiffer = false;
        }

        public final void checkOverflow(final int pos,
                final String errorDescriptionIfOverflow) {
            if (pos < packetLimit) {
                return;
            }

            throw new IllegalStateException(errorDescriptionIfOverflow);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MonitoringConfig<HttpProbe> getMonitoringConfig() {
        return monitoringConfig;
    }

    protected Object createJmxManagementObject() {
        return MonitoringUtils.loadJmxObject(
                "org.glassfish.grizzly.http.jmx.HttpCodecFilter", this,
                HttpCodecFilter.class);
    }

    private boolean isResponseToHeadRequest(HttpHeader header) {
        if (header.isRequest()) {
            return false;
        } else {
            HttpRequestPacket request = ((HttpResponsePacket) header).getRequest();
            return request.isHeadRequest();
        }
    }

    public static final class ContentParsingState {
        public boolean isLastChunk;
        public int chunkContentStart = -1;
        public long chunkLength = -1;
        public long chunkRemainder = -1;
        
        // the payload bytes read after processing was complete
        public long remainderBytesRead;
        
        public final MimeHeaders trailerHeaders = new MimeHeaders();

        private Buffer[] contentDecodingRemainders = new Buffer[1];
        
        public void recycle() {
            isLastChunk = false;
            chunkContentStart = -1;
            chunkLength = -1;
            chunkRemainder = -1;
            remainderBytesRead = 0;
            trailerHeaders.clear();
            contentDecodingRemainders = null;
//            Arrays.fill(contentDecodingRemainders, null);
        }

        private Buffer removeContentDecodingRemainder(final int i) {
            if (contentDecodingRemainders == null ||
                    i >= contentDecodingRemainders.length) {
                return null;
            }
            
            final Buffer remainder = contentDecodingRemainders[i];
            contentDecodingRemainders[i] = null;
            return remainder;
        }

        private void setContentDecodingRemainder(final int i, final Buffer remainder) {
            if (contentDecodingRemainders == null) {
                contentDecodingRemainders = new Buffer[i + 1];
            } else if (i >= contentDecodingRemainders.length) {
                contentDecodingRemainders = Arrays.copyOf(contentDecodingRemainders, i + 1);
            }
            
            contentDecodingRemainders[i] = remainder;
        }

    }
}
