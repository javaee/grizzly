/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.sun.grizzly.http;

import com.sun.grizzly.Buffer;
import com.sun.grizzly.filterchain.BaseFilter;
import com.sun.grizzly.http.core.HttpContent;
import com.sun.grizzly.http.core.HttpHeader;
import com.sun.grizzly.http.core.HttpPacket;
import com.sun.grizzly.http.util.Ascii;
import com.sun.grizzly.http.util.BufferChunk;
import com.sun.grizzly.http.util.HexUtils;
import com.sun.grizzly.memory.ByteBuffersBuffer;
import com.sun.grizzly.memory.CompositeBuffer;
import com.sun.grizzly.memory.MemoryManager;
import com.sun.grizzly.http.util.MimeHeaders;
import java.nio.charset.Charset;

/**
 *
 * @author oleksiys
 */
public abstract class HttpFilter extends BaseFilter {
    protected static final Charset ASCII_CHARSET = Charset.forName("ASCII");

    private static final int MAX_CHUNK_SIZE_LENGTH = 16;

    abstract boolean decodeInitialLine(HttpPacketParsing httpPacket,
            ParsingState parsingState, Buffer input);

    abstract Buffer encodeInitialLine(HttpPacket httpPacket, Buffer output,
            MemoryManager memoryManager);

    protected boolean parseHttpChunkLength(HttpPacketParsing httpPacket,
            Buffer input) {
        final ParsingState parsingState = httpPacket.getHeaderParsingState();
        if (parsingState.start == -1) {
            final int pos = input.position();
            parsingState.start = pos;
            parsingState.offset = pos;
            parsingState.packetLimit = pos + MAX_CHUNK_SIZE_LENGTH;
        }

        int offset = parsingState.offset;
        int limit = Math.min(parsingState.packetLimit, input.limit());
        long value = parsingState.parsingNumericValue;

        while(offset < limit) {
            final byte b = input.get(offset);
            if (b == Constants.CR || b == Constants.SEMI_COLON) {
                parsingState.checkpoint = offset;
            } else if (b == Constants.LF) {
                final ContentParsingState contentParsingState =
                        httpPacket.getContentParsingState();
                contentParsingState.chunkContentStart = offset + 1;
                contentParsingState.chunkLength = value;
                contentParsingState.chunkRemainder = value;

                parsingState.recycle();
                return true;
            } else if (parsingState.checkpoint == -1) {
                value = value * 16 + (HexUtils.DEC[b]);
            } else {
                throw new IllegalStateException("Unexpected HTTP chunk header");
            }

            offset++;
        }

        parsingState.offset = offset;
        parsingState.checkOverflow();

        return false;
    }

    protected void checkContent(HttpPacketParsing httpRequest) {
        final BufferChunk transferEncodingChunk =
                httpRequest.getHeaders().getValue("transfer-encoding");

        final ContentParsingState contentParsingState =
                httpRequest.getContentParsingState();

        if (transferEncodingChunk != null &&
                transferEncodingChunk.startsWithIgnoreCase("chunked", 0)) {
            contentParsingState.isChunked = true;
            return;
        }

        contentParsingState.isChunked = false;

        final BufferChunk contentLengthChunk =
                httpRequest.getHeaders().getValue("content-length");

        if (contentLengthChunk != null) {
            final int start = contentLengthChunk.getStart();
            final int length = contentLengthChunk.getEnd() - start;
            final long contentLength =
                    Ascii.parseLong(contentLengthChunk.getBuffer(), start, length);

            contentParsingState.chunkLength = contentLength;
        } else {
            contentParsingState.chunkLength = 0;
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
                if (!parseHeaders(httpPacket, parsingState, input)) {
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
        final HttpHeader httpHeader = isHeader ?
            (HttpHeader) input : ((HttpContent) input).getHttpHeader();


        Buffer encodedBuffer = null;
        
        if (!httpHeader.isCommited()) {
            encodedBuffer = memoryManager.allocate(8192);

            encodedBuffer = encodeInitialLine(input, encodedBuffer, memoryManager);
            encodedBuffer = put(memoryManager, encodedBuffer, Constants.CRLF_BYTES);

            final HttpHeader httpHeaderPacket = (HttpHeader) input;

            final MimeHeaders mimeHeaders = httpHeaderPacket.getHeaders();
            final int mimeHeadersNum = mimeHeaders.size();

            for (int i = 0; i < mimeHeadersNum; i++) {
                encodedBuffer = put(memoryManager, encodedBuffer,
                        mimeHeaders.getName(i));

                encodedBuffer = put(memoryManager, encodedBuffer,
                        Constants.COLON_BYTES);

                encodedBuffer = put(memoryManager, encodedBuffer,
                        mimeHeaders.getValue(i));

                encodedBuffer = put(memoryManager, encodedBuffer, Constants.CRLF_BYTES);
            }

            encodedBuffer = put(memoryManager, encodedBuffer, Constants.CRLF_BYTES);
            encodedBuffer.trim();

            httpHeader.setCommited(true);
        }

        if (!isHeader) {
            final Buffer content = ((HttpContent) input).getContent();
            if (content != null && content.hasRemaining()) {
                if (encodedBuffer != null) {
                    CompositeBuffer compositeBuffer = ByteBuffersBuffer.create(memoryManager);
                    compositeBuffer.append(encodedBuffer);
                    compositeBuffer.append(content);

                    encodedBuffer = compositeBuffer;
                } else {
                    encodedBuffer = content;
                }
            }
        }

        return encodedBuffer;
    }
    
    protected static final boolean parseHeaders(HttpPacketParsing httpPacket,
            ParsingState parsingState, Buffer input) {
        
        do {
            if (parsingState.subState == 0) {
                final int eol = checkEOL(parsingState, input);
                if (eol == 0) { // EOL
                    return true;
                } else if (eol == -2) { // not enough data
                    return false;
                }
            }

            if (!parseHeader(httpPacket, parsingState, input)) {
                return false;
            }

        } while (true);
    }

    protected static final boolean parseHeader(HttpPacketParsing httpPacket,
            ParsingState parsingState, Buffer input) {
        int subState = parsingState.subState;

        while (true) {
            switch (subState) {
                case 0: { // start to parse the header
                    parsingState.start = parsingState.offset;
                    parsingState.subState++;
                }
                case 1: { // parse header name
                    if (!parseHeaderName(httpPacket, parsingState, input)) {
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
                    parsingState.offset = nonSpaceIdx + 1;

                    if (parsingState.start == -1) { // Starting to parse header (will be called only for the first line of the multi line header)
                        parsingState.start = nonSpaceIdx;
                        parsingState.checkpoint = nonSpaceIdx + 1;
                        parsingState.checkpoint2 = nonSpaceIdx + 1;
                    }
                }

                case 3: { // parse header value
                    final int result = parseHeaderValue(parsingState, input);
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

    protected static final int parseHeaderValue(ParsingState parsingState, Buffer input) {
        final int limit = Math.min(input.limit(), parsingState.packetLimit);
        int offset = parsingState.offset;

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
                        parsingState.headerValueStorage.setBuffer(input,
                                parsingState.start, parsingState.checkpoint2);
                        return 0;
                    }
                }

                parsingState.offset = offset;
                return -1;
            } else if (b == Constants.SP) {
                input.put(parsingState.checkpoint++, b);
            } else {
                input.put(parsingState.checkpoint++, b);
                parsingState.checkpoint2 = parsingState.checkpoint;
            }

            offset++;
        }

        parsingState.offset = offset;
        return -1;
    }

    protected static final boolean parseHeaderName(HttpPacketParsing httpPacket,
            ParsingState parsingState, Buffer input) {
        final int limit = Math.min(input.limit(), parsingState.packetLimit);
        int offset = parsingState.offset;

        while(offset < limit) {
            final byte b = input.get(offset);
            if (b == Constants.COLON) {
                final BufferChunk valueChunk = httpPacket.getHeaders().addValue(
                        input, parsingState.start, offset);

                parsingState.headerValueStorage = valueChunk;
                parsingState.offset = offset + 1;

                return true;
            } else if ((b >= Constants.A) && (b <= Constants.Z)) {
                input.put(offset, (byte) (b - Constants.LC_OFFSET));
            }

            offset++;
        }

        parsingState.offset = offset;
        return false;
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
            byte[] bytes = bufferChunk.toString().getBytes(ASCII_CHARSET);
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
            parsingNumericValue = -1;
        }

        public final void checkOverflow() {
            if (offset < packetLimit) return;

            throw new IllegalStateException("HTTP packet is too long");
        }
    }

    protected static final class ContentParsingState {
        public boolean isChunked;
        public int chunkContentStart;
        public long chunkLength;
        public long chunkRemainder;


        public void recycle() {
            isChunked = false;
            chunkContentStart = -1;
            chunkLength = -1;
            chunkRemainder = -1;
        }
    }
}
