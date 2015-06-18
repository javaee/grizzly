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

import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.http.HttpCodecFilter.ContentParsingState;
import org.glassfish.grizzly.http.HttpCodecFilter.HeaderParsingState;
import org.glassfish.grizzly.http.util.Ascii;
import org.glassfish.grizzly.http.util.Constants;
import org.glassfish.grizzly.http.util.HexUtils;
import org.glassfish.grizzly.http.util.MimeHeaders;
import org.glassfish.grizzly.memory.Buffers;
import org.glassfish.grizzly.memory.CompositeBuffer;
import org.glassfish.grizzly.memory.CompositeBuffer.DisposeOrder;
import org.glassfish.grizzly.memory.MemoryManager;

import static org.glassfish.grizzly.http.util.HttpCodecUtils.*;
import static org.glassfish.grizzly.utils.Charsets.ASCII_CHARSET;

/**
 * Chunked transfer encoding implementation.
 *
 * @see TransferEncoding
 * 
 * @author Alexey Stashok
 */
public final class ChunkedTransferEncoding implements TransferEncoding {
    private static final int MAX_HTTP_CHUNK_SIZE_LENGTH = 16;
    private static final long CHUNK_SIZE_OVERFLOW = Long.MAX_VALUE >> 4;
    
    private static final int CHUNK_LENGTH_PARSED_STATE = 3;
    private static final byte[] LAST_CHUNK_CRLF_BYTES = "0\r\n".getBytes(ASCII_CHARSET);
    private static final int[] DEC = HexUtils.getDecBytes();

    private final int maxHeadersSize;

    public ChunkedTransferEncoding(final int maxHeadersSize) {
        this.maxHeadersSize = maxHeadersSize;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean wantDecode(final HttpHeader httpPacket) {
        return httpPacket.isChunked();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean wantEncode(final HttpHeader httpPacket) {
        return httpPacket.isChunked();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void prepareSerialize(final FilterChainContext ctx,
                                 final HttpHeader httpHeader,
                                 final HttpContent content) {
        httpHeader.makeTransferEncodingHeader(Constants.CHUNKED_ENCODING);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @SuppressWarnings({"UnusedDeclaration"})
    public ParsingResult parsePacket(final FilterChainContext ctx,
            final HttpHeader httpPacket, Buffer buffer) {
        final HttpPacketParsing httpPacketParsing = (HttpPacketParsing) httpPacket;

        // Get HTTP content parsing state
        final ContentParsingState contentParsingState =
                httpPacketParsing.getContentParsingState();

        // if it's chunked HTTP message
        boolean isLastChunk = contentParsingState.isLastChunk;
        // Check if HTTP chunk length was parsed
        if (!isLastChunk && contentParsingState.chunkRemainder <= 0) {
            // We expect next chunk header
            buffer = parseTrailerCRLF(httpPacketParsing, buffer);
            if (buffer == null) {
                return ParsingResult.create(null, null);
            }

            if (!parseHttpChunkLength(httpPacketParsing, buffer)) {

                // It could be the header we're processing is in response
                // to a HEAD request that is using this transfer encoding,
                // but no body is sent.
                if (isHeadRequest(httpPacket)) {
                    return ParsingResult.create(httpPacket.httpTrailerBuilder().
                    headers(contentParsingState.trailerHeaders).build(), null);
                }

                // if not a HEAD request and we don't have enough data to
                // parse chunk length - stop execution
                return ParsingResult.create(null, buffer);
            }
        } else {
            // HTTP content starts from position 0 in the input Buffer (HTTP chunk header is not part of the input Buffer)
            contentParsingState.chunkContentStart = 0;
        }

        // Get the position in the input Buffer, where actual HTTP content starts
        int chunkContentStart =
                contentParsingState.chunkContentStart;

        if (contentParsingState.chunkLength == 0) {
            // if it's the last HTTP chunk
            if (!isLastChunk) {
                // set it's the last chunk
                contentParsingState.isLastChunk = true;
                isLastChunk = true;
                // start trailer parsing
                initTrailerParsing(httpPacketParsing);
            }

            // Check if trailer is present
            if (!parseLastChunkTrailer(ctx, httpPacket, httpPacketParsing, buffer)) {
                // if yes - and there is not enough input data - stop the
                // filterchain processing
                return ParsingResult.create(null, buffer);
            }

            // move the content start position after trailer parsing
            chunkContentStart = httpPacketParsing.getHeaderParsingState().offset;
        }

        // Get the number of bytes remaining in the current chunk
        final long thisPacketRemaining =
                contentParsingState.chunkRemainder;
        // Get the number of content bytes available in the current input Buffer
        final int contentAvailable = buffer.limit() - chunkContentStart;

        Buffer remainder = null;
        if (contentAvailable > thisPacketRemaining) {
            // If input Buffer has part of the next message - slice it
            remainder = buffer.split(
                    (int) (chunkContentStart + thisPacketRemaining));
            buffer.position(chunkContentStart);
        } else if (chunkContentStart > 0) {
            buffer.position(chunkContentStart);
        }

        if (isLastChunk) {
            // Build last chunk content message
            return ParsingResult.create(httpPacket.httpTrailerBuilder().
                    headers(contentParsingState.trailerHeaders).build(), remainder);

        }

        buffer.shrink();
        if (buffer.hasRemaining()) { // if input still has some data
            // recalc the HTTP chunk remaining content
            contentParsingState.chunkRemainder -= buffer.remaining();
        } else { // if not
            buffer.tryDispose();
            buffer = Buffers.EMPTY_BUFFER;
        }

        return ParsingResult.create(httpPacket.httpContentBuilder().content(buffer).build(), remainder);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Buffer serializePacket(final FilterChainContext ctx,
            final HttpContent httpContent) {
        return encodeHttpChunk(ctx.getMemoryManager(),
                               httpContent,
                               httpContent.isLast());
    }

    private void initTrailerParsing(HttpPacketParsing httpPacket) {
        final HeaderParsingState headerParsingState =
                httpPacket.getHeaderParsingState();
        final ContentParsingState contentParsingState =
                httpPacket.getContentParsingState();

        headerParsingState.subState = 0;
        final int start = contentParsingState.chunkContentStart;
        headerParsingState.start = start;
        headerParsingState.offset = start;
        headerParsingState.packetLimit = start + maxHeadersSize;
    }

    private boolean parseLastChunkTrailer(final FilterChainContext ctx,
                                          final HttpHeader httpHeader,
                                          final HttpPacketParsing httpPacket,
                                          final Buffer input) {
        final HeaderParsingState headerParsingState =
                httpPacket.getHeaderParsingState();
        final ContentParsingState contentParsingState =
                httpPacket.getContentParsingState();

        final HttpCodecFilter filter = headerParsingState.codecFilter;
        final boolean result = filter.parseHeadersFromBuffer(httpHeader,
                                                   contentParsingState.trailerHeaders,
                                                   headerParsingState,
                                                   input);
        if (result) {
            if (contentParsingState.trailerHeaders.size() > 0) {
                filter.onHttpHeadersParsed(httpHeader,
                        contentParsingState.trailerHeaders, ctx);
            }
        } else {
            headerParsingState.checkOverflow(input.limit(),
                    "The chunked encoding trailer header is too large");
        }
        
        return result;
    }

    private static boolean parseHttpChunkLength(
            final HttpPacketParsing httpPacket,
            final Buffer input) {
        final HeaderParsingState parsingState = httpPacket.getHeaderParsingState();

        while (true) {
            switch (parsingState.state) {
                case 0: {// Initialize chunk parsing
                    final int pos = input.position();
                    parsingState.start = pos;
                    parsingState.offset = pos;
                    parsingState.packetLimit = pos + MAX_HTTP_CHUNK_SIZE_LENGTH;
                }

                case 1: { // Skip heading spaces (it's not allowed by the spec, but some servers put it there)
                    final int nonSpaceIdx = skipSpaces(input,
                            parsingState.offset, parsingState.packetLimit);
                    if (nonSpaceIdx == -1) {
                        parsingState.offset = input.limit();
                        parsingState.state = 1;
                        
                        parsingState.checkOverflow(input.limit(),
                                "The chunked encoding length prefix is too large");
                        return false;
                    }
                    
                    parsingState.offset = nonSpaceIdx;
                    parsingState.state = 2;
                }
                
                case 2: { // Scan chunk size
                    int offset = parsingState.offset;
                    int limit = Math.min(parsingState.packetLimit, input.limit());
                    long value = parsingState.parsingNumericValue;

                    while (offset < limit) {
                        final byte b = input.get(offset);
                        if (isSpaceOrTab(b) || /*trailing spaces are not allowed by the spec, but some server put it there*/
                                b == Constants.CR || b == Constants.SEMI_COLON) {
                            parsingState.checkpoint = offset;
                        } else if (b == Constants.LF) {
                            final ContentParsingState contentParsingState =
                                    httpPacket.getContentParsingState();
                            contentParsingState.chunkContentStart = offset + 1;
                            contentParsingState.chunkLength = value;
                            contentParsingState.chunkRemainder = value;
                            
                            parsingState.state = CHUNK_LENGTH_PARSED_STATE;
                            
                            return true;
                        } else if (parsingState.checkpoint == -1) {
                            if (DEC[b & 0xFF] != -1 && checkOverflow(value)) {
                                value = (value << 4) + (DEC[b & 0xFF]);
                            } else {
                                throw new HttpBrokenContentException("Invalid byte representing a hex value within a chunk length encountered : " + b);
                            }
                        } else {
                            throw new HttpBrokenContentException("Unexpected HTTP chunk header");
                        }

                        offset++;
                    }

                    parsingState.parsingNumericValue = value;
                    parsingState.offset = limit;
                    parsingState.checkOverflow(limit, "The chunked encoding length prefix is too large");
                    return false;

                }
            }
        }
    }

    /**
     * @param value
     * @return <tt>false</tt> if next left bit-shift by 4 bits will cause overflow,
     *          or <tt>true</tt> otherwise
     */
    private static boolean checkOverflow(final long value) {
        return value <= CHUNK_SIZE_OVERFLOW;
    }
    
    private static boolean isHeadRequest(final HttpHeader header) {

        final HttpRequestPacket request = header.isRequest()
                ? (HttpRequestPacket) header
                : ((HttpResponsePacket) header).getRequest();
        
        return request.isHeadRequest();
    }

    private static Buffer parseTrailerCRLF(final HttpPacketParsing httpPacket,
            final Buffer input) {
        final HeaderParsingState parsingState = httpPacket.getHeaderParsingState();

        if (parsingState.state == CHUNK_LENGTH_PARSED_STATE) {
            while (input.hasRemaining()) {
                if (input.get() == Constants.LF) {
                    parsingState.recycle();
                    if (input.hasRemaining()) {
                        return input.slice();
                    }

                    return null;
                }
            }

            return null;
        }

        return input;
    }

    private static Buffer encodeHttpChunk(
            final MemoryManager memoryManager,
            final HttpContent httpContent,
            final boolean isLastChunk) {
        
        final Buffer content = httpContent.getContent();

        Buffer httpChunkBuffer = memoryManager.allocate(16);
        final int chunkSize = content.remaining();

        Ascii.intToHexString(httpChunkBuffer, chunkSize);
        httpChunkBuffer = put(memoryManager, httpChunkBuffer,
                HttpCodecFilter.CRLF_BYTES);
        httpChunkBuffer.trim();
        httpChunkBuffer.allowBufferDispose(true);

        final boolean hasContent = chunkSize > 0;

        if (hasContent) {
            httpChunkBuffer = Buffers.appendBuffers(memoryManager,
                    httpChunkBuffer, content);
            if (httpChunkBuffer.isComposite()) {
                httpChunkBuffer.allowBufferDispose(true);
                ((CompositeBuffer) httpChunkBuffer).allowInternalBuffersDispose(true);
                ((CompositeBuffer) httpChunkBuffer).disposeOrder(DisposeOrder.FIRST_TO_LAST);
            }
        }
        
        Buffer httpChunkTrailer;
        if (!isLastChunk) {
            httpChunkTrailer = memoryManager.allocate(2);
        } else {
            final boolean isTrailer = HttpTrailer.isTrailer(httpContent) &&
                    ((HttpTrailer) httpContent).getHeaders().size() > 0;

            if (!isTrailer) {
                httpChunkTrailer = memoryManager.allocate(8);
            } else {
                httpChunkTrailer = memoryManager.allocate(256);
            }

            if (hasContent) {
                httpChunkTrailer = put(memoryManager, httpChunkTrailer,
                        HttpCodecFilter.CRLF_BYTES);
                httpChunkTrailer = put(memoryManager, httpChunkTrailer,
                        LAST_CHUNK_CRLF_BYTES);
            }

            if (isTrailer) {
                final HttpTrailer httpTrailer = (HttpTrailer) httpContent;
                final MimeHeaders mimeHeaders = httpTrailer.getHeaders();
                httpChunkTrailer = HttpCodecFilter.encodeMimeHeaders(memoryManager,
                        httpChunkTrailer, mimeHeaders, httpContent.getHttpHeader().getTempHeaderEncodingBuffer());
            }

        }
        
        httpChunkTrailer = put(memoryManager, httpChunkTrailer,
                HttpCodecFilter.CRLF_BYTES);

        httpChunkTrailer.trim();
        httpChunkTrailer.allowBufferDispose(true);

        return Buffers.appendBuffers(memoryManager, httpChunkBuffer,
                httpChunkTrailer);
    }
}
