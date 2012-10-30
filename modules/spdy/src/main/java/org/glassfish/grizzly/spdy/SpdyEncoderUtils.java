/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.glassfish.grizzly.spdy;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.http.HttpResponsePacket;
import org.glassfish.grizzly.http.util.BufferChunk;
import org.glassfish.grizzly.http.util.ByteChunk;
import org.glassfish.grizzly.http.util.DataChunk;
import org.glassfish.grizzly.http.util.Header;
import org.glassfish.grizzly.memory.Buffers;
import org.glassfish.grizzly.memory.CompositeBuffer;
import org.glassfish.grizzly.memory.MemoryManager;
import org.glassfish.grizzly.spdy.compression.SpdyDeflaterOutputStream;
import org.glassfish.grizzly.spdy.compression.SpdyMimeHeaders;

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
            final SpdyResponse response,
            final boolean isLast) throws IOException {
        
        final SpdyStream spdyStream = response.getSpdyStream();
        final SpdySession spdySession = spdyStream.getSpdySession();
        
        final Buffer initialBuffer = allocateHeapBuffer(memoryManager, 2048);
        initialBuffer.position(12); // skip the header for now
        Buffer resultBuffer;
        synchronized (spdySession) { // TODO This sync point should be revisited for a more optimal solution.
            resultBuffer = encodeHeaders(spdySession,
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
    private static Buffer encodeHeaders(final SpdySession spdySession,
            final HttpResponsePacket response,
            final Buffer outputBuffer) throws IOException {
        
        final SpdyMimeHeaders headers = (SpdyMimeHeaders) response.getHeaders();
        
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
        
        dataOutputStream.writeInt(7);
        dataOutputStream.write(":status".getBytes());
        final byte[] statusBytes = response.getHttpStatus().getStatusBytes();
        dataOutputStream.writeInt(statusBytes.length);
        dataOutputStream.write(statusBytes);
        
        dataOutputStream.writeInt(8);
        dataOutputStream.write(":version".getBytes());
        final byte[] protocolBytes = response.getProtocol().getProtocolBytes();
        dataOutputStream.writeInt(protocolBytes.length);
        dataOutputStream.write(protocolBytes);
        
        final List tmpList = spdySession.tmpList;
        
        for (int i = 0; i < mimeHeadersCount; i++) {
            int valueSize = 0;
        
            if (!headers.getAndSetSerialized(i, true)) {
                final DataChunk name = headers.getName(i);
                
                if (name.isNull() || name.getLength() == 0) {
                    continue;
                }
                
                final DataChunk value1 = headers.getValue(i);
                
                for (int j = i; j < mimeHeadersCount; j++) {
                    if (!headers.isSerialized(j) &&
                            name.equals(headers.getName(j))) {
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
        
        dataOutputStream.flush();
        
        return deflaterOutputStream.checkpoint();
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
}
