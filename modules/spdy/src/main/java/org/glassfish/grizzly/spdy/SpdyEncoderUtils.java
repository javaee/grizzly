/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012 Oracle and/or its affiliates. All rights reserved.
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
package org.glassfish.grizzly.spdy;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.http.HttpResponsePacket;
import org.glassfish.grizzly.http.util.BufferChunk;
import org.glassfish.grizzly.http.util.ByteChunk;
import org.glassfish.grizzly.http.util.DataChunk;
import org.glassfish.grizzly.http.util.Header;
import org.glassfish.grizzly.http.util.MimeHeaders;
import org.glassfish.grizzly.memory.Buffers;
import org.glassfish.grizzly.memory.CompositeBuffer;
import org.glassfish.grizzly.memory.MemoryManager;
import org.glassfish.grizzly.spdy.compression.SpdyDeflaterOutputStream;

import static org.glassfish.grizzly.spdy.Constants.*;

/**
 *
 * @author oleksiys
 */
class SpdyEncoderUtils {
    static Buffer encodeWindowUpdate(final MemoryManager memoryManager,
            final SpdyStream spdyStream,
            final int delta) {
        
        final Buffer buffer = memoryManager.allocate(16);
        
        buffer.putInt(0, 0x80000000 | (SPDY_VERSION << 16) | WINDOW_UPDATE_FRAME);  // C | SPDY_VERSION | WINDOW_UPDATE_FRAME
        buffer.putInt(4, 8); // FLAGS | LENGTH
        buffer.putInt(8, spdyStream.getStreamId() & 0x7FFFFFFF); // X | STREAM_ID
        buffer.putInt(12, delta & 0x7FFFFFFF);  // X | delta
        
        return buffer;
    }
    
    static Buffer encodeSpdyData(final MemoryManager memoryManager,
            final SpdyStream spdyStream,
            final Buffer data,
            final boolean isLast) {
        
        final Buffer headerBuffer = memoryManager.allocate(8);
        
        headerBuffer.putInt(0, spdyStream.getStreamId() & 0x7FFFFFFF);  // C | STREAM_ID
        final int flags = isLast ? 1 : 0;
        
        headerBuffer.putInt(4, (flags << 24) | data.remaining()); // FLAGS | LENGTH

        final Buffer resultBuffer = Buffers.appendBuffers(memoryManager, headerBuffer, data);
        
        if (resultBuffer.isComposite()) {
            resultBuffer.allowBufferDispose(true);
            ((CompositeBuffer) resultBuffer).allowInternalBuffersDispose(true);
            ((CompositeBuffer) resultBuffer).disposeOrder(CompositeBuffer.DisposeOrder.FIRST_TO_LAST);
        }
        
        return resultBuffer;
    }
    
    static Buffer encodeSynReply(final MemoryManager memoryManager,
            final SpdyStream spdyStream,
            final boolean isLast) throws IOException {
        
        final HttpResponsePacket response = spdyStream.getSpdyResponse();
        
        final SpdySession spdySession = spdyStream.getSpdySession();
        
        final Buffer initialBuffer = allocateHeapBuffer(memoryManager, 2048);
        initialBuffer.position(12); // skip the header for now
        Buffer resultBuffer;
        synchronized (spdySession) { // TODO This sync point should be revisited for a more optimal solution.
            resultBuffer = encodeSynReplyHeaders(spdySession,
                                         response,
                                         initialBuffer);
        }
        
        initialBuffer.putInt(0, 0x80000000 | (SPDY_VERSION << 16) | SYN_REPLY_FRAME);  // C | SPDY_VERSION | SYN_REPLY

        final int flags = isLast ? 1 : 0;
        
        initialBuffer.putInt(4, (flags << 24) | (resultBuffer.remaining() - 8)); // FLAGS | LENGTH
        initialBuffer.putInt(8, spdyStream.getStreamId() & 0x7FFFFFFF); // STREAM_ID
        
        return resultBuffer;
    }
    
    @SuppressWarnings("unchecked")
    private static Buffer encodeSynReplyHeaders(final SpdySession spdySession,
            final HttpResponsePacket response,
            final Buffer outputBuffer) throws IOException {
        
        final MimeHeaders headers = response.getHeaders();
        
        headers.removeHeader(Header.Connection);
        headers.removeHeader(Header.KeepAlive);
        headers.removeHeader(Header.ProxyConnection);
        headers.removeHeader(Header.TransferEncoding);
        
        final DataOutputStream dataOutputStream =
                spdySession.getDeflaterDataOutputStream();
        final SpdyDeflaterOutputStream deflaterOutputStream =
                spdySession.getDeflaterOutputStream();

        deflaterOutputStream.setInitialOutputBuffer(outputBuffer);
        
        final int mimeHeadersCount = headers.size();
        
        dataOutputStream.writeInt(mimeHeadersCount + 2);

        encodeHeaderValue(dataOutputStream, ":status".getBytes(),
                response.getHttpStatus().getStatusBytes());
        
        encodeHeaderValue(dataOutputStream, ":version".getBytes(),
                response.getProtocol().getProtocolBytes());
                
        encodeUserHeaders(spdySession, headers, dataOutputStream);
        
        dataOutputStream.flush();
        
        return deflaterOutputStream.checkpoint();
    }
    
    static Buffer encodeSynStream(final MemoryManager memoryManager,
            final SpdyStream spdyStream,
            final boolean isLast) throws IOException {
        
        final HttpRequestPacket request = spdyStream.getSpdyRequest();
        final SpdySession spdySession = spdyStream.getSpdySession();
        
        final Buffer initialBuffer = allocateHeapBuffer(memoryManager, 2048);
        initialBuffer.position(18); // skip the header for now
        Buffer resultBuffer;
        synchronized (spdySession) { // TODO This sync point should be revisited for a more optimal solution.
            resultBuffer = encodeSynStreamHeaders(spdySession,
                                         request,
                                         initialBuffer);
        }
        
        initialBuffer.putInt(0, 0x80000000 | (SPDY_VERSION << 16) | SYN_STREAM_FRAME);  // C | SPDY_VERSION | SYN_STREAM_FRAME

        final int flags = isLast ? 1 : 0;
        
        initialBuffer.putInt(4, (flags << 24) | (resultBuffer.remaining() - 8)); // FLAGS | LENGTH
        initialBuffer.putInt(8, spdyStream.getStreamId() & 0x7FFFFFFF); // STREAM_ID
        initialBuffer.putInt(12, spdyStream.getAssociatedToStreamId() & 0x7FFFFFFF); // ASSOCIATED_TO_STREAM_ID
        initialBuffer.putShort(16, (short) ((spdyStream.getPriority() << 13) | (spdyStream.getSlot() & 0xFF))); // PRI | UNUSED | SLOT
        
        return resultBuffer;
    }

    @SuppressWarnings("unchecked")
    private static Buffer encodeSynStreamHeaders(final SpdySession spdySession,
            final HttpRequestPacket request,
            final Buffer outputBuffer) throws IOException {
        
        final MimeHeaders headers = request.getHeaders();
        
        final DataOutputStream dataOutputStream =
                spdySession.getDeflaterDataOutputStream();
        final SpdyDeflaterOutputStream deflaterOutputStream =
                spdySession.getDeflaterOutputStream();

        deflaterOutputStream.setInitialOutputBuffer(outputBuffer);
        
        final String hostHeader = headers.getHeader(Header.Host);

        headers.removeHeader(Header.Connection);
        headers.removeHeader(Header.Host);
        headers.removeHeader(Header.KeepAlive);
        headers.removeHeader(Header.ProxyConnection);
        headers.removeHeader(Header.TransferEncoding);
        
        final int mimeHeadersCount = headers.size();
        
        dataOutputStream.writeInt(mimeHeadersCount + 5);
        
        final URI uri;
        try {
            uri = new URI(request.getRequestURI());
        } catch (URISyntaxException e) {
            throw new IOException(e);
        }
        encodeHeaderValue(dataOutputStream, ":method".getBytes(),
                request.getMethod().getMethodBytes());
        
        encodeHeaderValue(dataOutputStream, ":path".getBytes(),
                uri.getPath().getBytes());
        
        encodeHeaderValue(dataOutputStream, ":version".getBytes(),
                request.getProtocol().getProtocolBytes());

        encodeHeaderValue(dataOutputStream, ":host".getBytes(),
                hostHeader.getBytes());

        encodeHeaderValue(dataOutputStream, ":scheme".getBytes(),
                uri.getScheme() != null ? uri.getScheme().getBytes() : "http".getBytes());
        
        encodeUserHeaders(spdySession, headers, dataOutputStream);
        
        dataOutputStream.flush();
        
        return deflaterOutputStream.checkpoint();
    }

    @SuppressWarnings("unchecked")
    private static void encodeUserHeaders(final SpdySession spdySession,
            final MimeHeaders headers,
            final DataOutputStream dataOutputStream) throws IOException {
        
        final int mimeHeadersCount = headers.size();
        final List tmpList = spdySession.tmpList;
        
        for (int i = 0; i < mimeHeadersCount; i++) {
            int valueSize = 0;
        
            if (!headers.setSerialized(i, true)) {
                final DataChunk name = headers.getName(i);
                
                if (name.isNull() || name.getLength() == 0) {
                    continue;
                }
                
                final DataChunk value1 = headers.getValue(i);
                
                for (int j = i; j < mimeHeadersCount; j++) {
                    if (!headers.isSerialized(j) &&
                            name.equalsIgnoreCase(headers.getName(j))) {
                        headers.setSerialized(j, true);
                        final DataChunk value = headers.getValue(j);
                        if (!value.isNull()) {
                            tmpList.add(value);
                            valueSize += value.getLength();
                        }
                    }
                }
                
                encodeDataChunkWithLenPrefix(dataOutputStream, headers.getName(i));
                
                if (!tmpList.isEmpty()) {
                    final int extraValuesCount = tmpList.size();
                    
                    valueSize += extraValuesCount - 1; // 0 delims
                    
                    if (!value1.isNull()) {
                        valueSize += value1.getLength();
                        valueSize++; // 0 delim
                        
                        dataOutputStream.writeInt(valueSize);
                        encodeDataChunk(dataOutputStream, value1);
                        dataOutputStream.write(0);
                    } else {
                        dataOutputStream.writeInt(valueSize);
                    }
                    
                    for (int j = 0; j < extraValuesCount; j++) {
                        encodeDataChunk(dataOutputStream, (DataChunk) tmpList.get(j));
                        if (j < extraValuesCount - 1) {
                            dataOutputStream.write(0);
                        }
                    }
                    
                    tmpList.clear();
                } else {
                    encodeDataChunkWithLenPrefix(dataOutputStream, value1);
                }
            }
        }
    }
    
    private static void encodeDataChunkWithLenPrefix(final DataOutputStream dataOutputStream,
            final DataChunk dc) throws IOException {
        
        final int len = dc.getLength();
        dataOutputStream.writeInt(len);
        
        encodeDataChunk(dataOutputStream, dc);
    }

    private static void encodeDataChunk(final DataOutputStream dataOutputStream,
            final DataChunk dc) throws IOException {
        if (dc.isNull()) {
            return;
        }

        switch (dc.getType()) {
            case Bytes: {
                final ByteChunk bc = dc.getByteChunk();
                dataOutputStream.write(bc.getBuffer(), bc.getStart(),
                        bc.getLength());

                break;
            }
                
            case Buffer: {
                final BufferChunk bufferChunk = dc.getBufferChunk();
                encodeDataChunk(dataOutputStream, bufferChunk.getBuffer(),
                        bufferChunk.getStart(), bufferChunk.getLength());
                break;
            }
                
            default: {
                encodeDataChunk(dataOutputStream, dc.toString());
            }
        }
    }
    
    private static void encodeDataChunk(final DataOutputStream dataOutputStream,
            final Buffer buffer, final int offs, final int len) throws IOException {
        
        if (buffer.hasArray()) {
            dataOutputStream.write(buffer.array(), buffer.arrayOffset() + offs, len);
        } else {
            final int lim = offs + len;

            for (int i = offs; i < lim; i++) {
                dataOutputStream.write(buffer.get(i));
            }
        }
    }
    
    private static void encodeDataChunk(final DataOutputStream dataOutputStream,
            final String s) throws IOException {
        final int len = s.length();
        
        for (int i = 0; i < len; i++) {
            final char c = s.charAt(i);
            if (c != 0) {
                dataOutputStream.write(c);
            } else {
                dataOutputStream.write(' ');
            }
        }
    }    
    static Buffer allocateHeapBuffer(final MemoryManager mm, final int size) {
        if (!mm.willAllocateDirect(size)) {
            return mm.allocateAtLeast(size);
        } else {
            return Buffers.wrap(mm, new byte[size]);
        }
    }    

    private static void encodeHeaderValue(
            final DataOutputStream dataOutputStream,
            final byte[] nameBytes, final byte[] valueBytes) throws IOException {
        dataOutputStream.writeInt(nameBytes.length);
        dataOutputStream.write(nameBytes);
        dataOutputStream.writeInt(valueBytes.length);
        dataOutputStream.write(valueBytes);
    }
}
