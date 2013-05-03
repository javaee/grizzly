/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012-2013 Oracle and/or its affiliates. All rights reserved.
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
import java.util.ArrayList;
import java.util.List;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.http.HttpResponsePacket;
import org.glassfish.grizzly.http.util.Ascii;
import org.glassfish.grizzly.http.util.BufferChunk;
import org.glassfish.grizzly.http.util.ByteChunk;
import org.glassfish.grizzly.http.util.DataChunk;
import org.glassfish.grizzly.http.util.Header;
import org.glassfish.grizzly.http.util.HttpStatus;
import org.glassfish.grizzly.http.util.MimeHeaders;
import org.glassfish.grizzly.memory.MemoryManager;
import org.glassfish.grizzly.spdy.compression.SpdyDeflaterOutputStream;
import org.glassfish.grizzly.utils.BufferOutputStream;
import org.glassfish.grizzly.utils.Exceptions;

import static org.glassfish.grizzly.http.util.Constants.*;
import static org.glassfish.grizzly.http.util.DataChunk.Type.Buffer;
import static org.glassfish.grizzly.http.util.DataChunk.Type.Bytes;
import static org.glassfish.grizzly.http.util.HttpCodecUtils.*;

/**
 * HTTP Packet -> SpdyFrames encoder utils.
 * 
 * @author Grizzly team
 */
class SpdyEncoderUtils {
    private static final byte[] SPACE_BYTES = {' '};
    private static final byte[] HTTPS_BYTES = "https".getBytes(DEFAULT_HTTP_CHARSET);
    
    @SuppressWarnings("unchecked")
    static Buffer encodeSynReplyHeadersAndLock(final SpdySession spdySession,
            final HttpResponsePacket response) throws IOException {
        final MemoryManager mm = spdySession.getMemoryManager();

        final Buffer compressedBuffer = mm.allocate(2048);
        final Buffer plainBuffer = mm.allocate(2048);
        
        final MimeHeaders headers = response.getHeaders();
        
        headers.removeHeader(Header.Connection);
        headers.removeHeader(Header.KeepAlive);
        headers.removeHeader(Header.ProxyConnection);
        headers.removeHeader(Header.TransferEncoding);
        
        // Serialize user headers first, so we know their count
        final BufferOutputStream userHeadersBOS = new BufferOutputStream(mm);
        final DataOutputStream userHeadersDOS = new DataOutputStream(userHeadersBOS);
        userHeadersBOS.setInitialOutputBuffer(plainBuffer);
        
        final int userHeadersNum = encodeUserHeaders(spdySession, headers, userHeadersDOS);
        userHeadersDOS.flush();
        final Buffer userHeadersBuffer = userHeadersBOS.getBuffer();
        userHeadersBOS.reset();
        
        // get the plain user headers buffer
        userHeadersBuffer.trim();
        
        // !!!! LOCK the deflater
        spdySession.getDeflaterLock().lock();
        
        try {
            final DataOutputStream dataOutputStream =
                    spdySession.getDeflaterDataOutputStream();
            final SpdyDeflaterOutputStream deflaterOutputStream =
                    spdySession.getDeflaterOutputStream();

            deflaterOutputStream.setInitialOutputBuffer(compressedBuffer);

            dataOutputStream.writeInt(userHeadersNum + 2);


            if (response.isCustomReasonPhraseSet()) {
                // don't apply HttpStatus.filter(DataChunk)
                encodeHeaderValue(dataOutputStream, Constants.STATUS_HEADER_BYTES,
                        response.getHttpStatus().getStatusBytes(), SPACE_BYTES,
                        response.getReasonPhraseDC().toString().getBytes(DEFAULT_HTTP_CHARACTER_ENCODING));
            } else {
                encodeHeaderValue(dataOutputStream, Constants.STATUS_HEADER_BYTES,
                        response.getHttpStatus().getStatusBytes(), SPACE_BYTES,
                        response.getHttpStatus().getReasonPhraseBytes());
            }

            encodeHeaderValue(dataOutputStream, Constants.VERSION_HEADER_BYTES,
                    response.getProtocol().getProtocolBytes());


            dataOutputStream.flush();
            deflaterOutputStream.write(userHeadersBuffer);
            return deflaterOutputStream.checkpoint();
        } catch (Exception e) {
            spdySession.getDeflaterLock().unlock();
            throw Exceptions.makeIOException(e);
        } finally {
            userHeadersBuffer.dispose();
        }
    }
    
    @SuppressWarnings("unchecked")
    static Buffer encodeSynStreamHeadersAndLock(final SpdyStream spdyStream,
            final HttpRequestPacket request) throws IOException {

        // ----------------- Parse URI scheme and path ----------------
        int schemeStart = -1;
        int schemeLen = -1;

        final byte[] requestURI =
                request.getRequestURI().getBytes(DEFAULT_HTTP_CHARACTER_ENCODING);
        final int len = requestURI.length;

        final int nonSpaceIdx = skipSpaces(requestURI, 0, len, len);
        final int idx = ByteChunk.indexOf(requestURI, nonSpaceIdx, len, '/');

        if (idx > 0 && idx < len - 1 &&
                requestURI[idx - 1] == ':' && requestURI[idx + 1] == '/') {
            schemeStart = nonSpaceIdx;
            schemeLen = idx - schemeStart - 1;
        }

        
        final int pathStart = schemeStart == -1 ?
                idx :
                ByteChunk.indexOf(requestURI, idx + 2, len, '/');
        final int pathLen = len - pathStart;

        if (pathStart == -1) {
            throw new IllegalStateException("Request URI path is not set");
        }
        // ---------------------------------------------------------------

        final MimeHeaders headers = request.getHeaders();
        
        final String hostHeader = headers.getHeader(Header.Host);
        final byte[] hostHeaderBytes;
        final int hostHeaderStart;
        final int hostHeaderLen;
        
        if (hostHeader == null) {
            if (schemeStart == -1) {
                throw new IllegalStateException("Missing the Host header");
            }
            
            hostHeaderBytes = requestURI;
            hostHeaderStart = schemeStart + schemeLen + 3;
            hostHeaderLen = pathStart - hostHeaderStart;
        } else {
            hostHeaderBytes =
                    hostHeader.getBytes(DEFAULT_HTTP_CHARACTER_ENCODING);
            hostHeaderStart = 0;
            hostHeaderLen = hostHeaderBytes.length;
        }
        
        final SpdySession spdySession = spdyStream.getSpdySession();
        final MemoryManager mm = spdySession.getMemoryManager();

        final Buffer compressedBuffer = mm.allocate(2048);
        final Buffer plainBuffer = mm.allocate(2048);
        
        headers.removeHeader(Header.Connection);
        headers.removeHeader(Header.Host);
        headers.removeHeader(Header.KeepAlive);
        headers.removeHeader(Header.ProxyConnection);
        headers.removeHeader(Header.TransferEncoding);
        
        
        // Serialize user headers first, so we know their count
        final BufferOutputStream userHeadersBOS = new BufferOutputStream(mm);
        final DataOutputStream userHeadersDOS = new DataOutputStream(userHeadersBOS);
        userHeadersBOS.setInitialOutputBuffer(plainBuffer);
        
        final int userHeadersNum = encodeUserHeaders(spdySession, headers, userHeadersDOS);
        userHeadersDOS.flush();
        final Buffer userHeadersBuffer = userHeadersBOS.getBuffer();
        userHeadersBOS.reset();
        
        // get the plain user headers buffer
        userHeadersBuffer.trim();

        // !!!! LOCK the deflater
        spdySession.getDeflaterLock().lock();
        
        try {
            final DataOutputStream dataOutputStream =
                    spdySession.getDeflaterDataOutputStream();
            final SpdyDeflaterOutputStream deflaterOutputStream =
                    spdySession.getDeflaterOutputStream();

            deflaterOutputStream.setInitialOutputBuffer(compressedBuffer);

            dataOutputStream.writeInt(userHeadersNum + 5);

            encodeHeaderValue(dataOutputStream, Constants.METHOD_HEADER_BYTES,
                    request.getMethod().getMethodBytes());

            encodeHeaderValue(dataOutputStream, Constants.PATH_HEADER_BYTES,
                    requestURI, pathStart, pathLen);

            encodeHeaderValue(dataOutputStream, Constants.VERSION_HEADER_BYTES,
                    request.getProtocol().getProtocolBytes());
            
            encodeHeaderValue(dataOutputStream, Constants.HOST_HEADER_BYTES,
                    hostHeaderBytes, hostHeaderStart, hostHeaderLen);

            if (schemeLen > 0) {
                encodeHeaderValue(dataOutputStream, Constants.SCHEMA_HEADER_BYTES,
                        requestURI, schemeStart, schemeLen);
            } else {
                encodeHeaderValue(dataOutputStream, Constants.SCHEMA_HEADER_BYTES,
                        HTTPS_BYTES);
            }

            dataOutputStream.flush();
            deflaterOutputStream.write(userHeadersBuffer);
            
            return deflaterOutputStream.checkpoint();
        } catch (Exception e) {
            spdySession.getDeflaterLock().unlock();
            throw Exceptions.makeIOException(e);
        } finally {
            userHeadersBuffer.dispose();
        }
    }

    @SuppressWarnings("unchecked")
    static Buffer encodeUnidirectionalSynStreamHeadersAndLock(final SpdyStream spdyStream,
            final SpdyResponse response) throws IOException {

        // ----------------- Parse URI scheme and path ----------------
        int schemeStart = -1;
        int schemeLen = -1;

        final byte[] requestURI = response.getRequest().getRequestURI()
                .getBytes(DEFAULT_HTTP_CHARACTER_ENCODING);
        final int len = requestURI.length;

        final int nonSpaceIdx = skipSpaces(requestURI, 0, len, len);
        final int idx = ByteChunk.indexOf(requestURI, nonSpaceIdx, len, '/');

        if (idx > 0 && idx < len - 1 &&
                requestURI[idx - 1] == ':' && requestURI[idx + 1] == '/') {
            schemeStart = nonSpaceIdx;
            schemeLen = idx - schemeStart - 1;
        }

        
        final int pathStart = schemeStart == -1 ?
                idx :
                ByteChunk.indexOf(requestURI, idx + 2, len, '/');
        final int pathLen = len - pathStart;

        if (pathStart == -1) {
            throw new IllegalStateException("Request URI path is not set");
        }
        // ---------------------------------------------------------------

        final MimeHeaders headers = response.getHeaders();
        
        final String hostHeader = headers.getHeader(Header.Host);
        final byte[] hostHeaderBytes;
        final int hostHeaderStart;
        final int hostHeaderLen;
        
        if (hostHeader == null) {
            if (schemeStart == -1) {
                throw new IllegalStateException("Missing the Host header");
            }
            
            hostHeaderBytes = requestURI;
            hostHeaderStart = schemeStart + schemeLen + 3;
            hostHeaderLen = pathStart - hostHeaderStart;
        } else {
            hostHeaderBytes =
                    hostHeader.getBytes(DEFAULT_HTTP_CHARACTER_ENCODING);
            hostHeaderStart = 0;
            hostHeaderLen = hostHeaderBytes.length;
        }
        
        final SpdySession spdySession = spdyStream.getSpdySession();
        final MemoryManager mm = spdySession.getMemoryManager();

        final Buffer compressedBuffer = mm.allocate(2048);
        final Buffer plainBuffer = mm.allocate(2048);
        
        headers.removeHeader(Header.Connection);
        headers.removeHeader(Header.Host);
        headers.removeHeader(Header.KeepAlive);
        headers.removeHeader(Header.ProxyConnection);
        headers.removeHeader(Header.TransferEncoding);
        
        
        // Serialize user headers first, so we know their count
        final BufferOutputStream userHeadersBOS = new BufferOutputStream(mm);
        final DataOutputStream userHeadersDOS = new DataOutputStream(userHeadersBOS);
        userHeadersBOS.setInitialOutputBuffer(plainBuffer);
        
        final int userHeadersNum = encodeUserHeaders(spdySession, headers, userHeadersDOS);
        userHeadersDOS.flush();
        final Buffer userHeadersBuffer = userHeadersBOS.getBuffer();
        userHeadersBOS.reset();
        
        // get the plain user headers buffer
        userHeadersBuffer.trim();

        // !!!! LOCK the deflater
        spdySession.getDeflaterLock().lock();
        
        try {
            final DataOutputStream dataOutputStream =
                    spdySession.getDeflaterDataOutputStream();
            final SpdyDeflaterOutputStream deflaterOutputStream =
                    spdySession.getDeflaterOutputStream();

            deflaterOutputStream.setInitialOutputBuffer(compressedBuffer);

            dataOutputStream.writeInt(userHeadersNum + 5);

            if (schemeLen > 0) {
                encodeHeaderValue(dataOutputStream, Constants.SCHEMA_HEADER_BYTES,
                        requestURI, schemeStart, schemeLen);
            } else {
                encodeHeaderValue(dataOutputStream, Constants.SCHEMA_HEADER_BYTES,
                        HTTPS_BYTES);
            }

            encodeHeaderValue(dataOutputStream, Constants.HOST_HEADER_BYTES,
                    hostHeaderBytes, hostHeaderStart, hostHeaderLen);

            encodeHeaderValue(dataOutputStream, Constants.PATH_HEADER_BYTES,
                    requestURI, pathStart, pathLen);

            encodeHeaderValue(dataOutputStream, Constants.VERSION_HEADER_BYTES,
                    response.getProtocol().getProtocolBytes());
            
            final HttpStatus httpStatus = response.getHttpStatus();

            if (response.isCustomReasonPhraseSet()) {
                encodeHeaderValue(dataOutputStream, Constants.STATUS_HEADER_BYTES,
                        httpStatus.getStatusBytes(), SPACE_BYTES,
                        response.getReasonPhraseRawDC()
                        .toString().getBytes(DEFAULT_HTTP_CHARACTER_ENCODING));
            } else {
                encodeHeaderValue(dataOutputStream, Constants.STATUS_HEADER_BYTES,
                        httpStatus.getStatusBytes(), SPACE_BYTES,
                        httpStatus.getReasonPhraseBytes());
            }
            
            dataOutputStream.flush();
            deflaterOutputStream.write(userHeadersBuffer);
            
            return deflaterOutputStream.checkpoint();
        } catch (Exception e) {
            spdySession.getDeflaterLock().unlock();
            throw Exceptions.makeIOException(e);
        } finally {
            userHeadersBuffer.dispose();
        }
    }
    
    @SuppressWarnings("unchecked")
    private static int encodeUserHeaders(final SpdySession spdySession,
            final MimeHeaders headers,
            final DataOutputStream dataOutputStream) throws IOException {
        
        final int mimeHeadersCount = headers.size();
        List<DataChunk> tmpList = null;
        
        int headersNum = 0;
        
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
                            if (tmpList == null) {
                                tmpList = new ArrayList<DataChunk>(2);
                            }
                            tmpList.add(value);
                            valueSize += value.getLength();
                        }
                    }
                }
                
                headersNum++;
                encodeDataChunkWithLenPrefixLowerCase(dataOutputStream, headers.getName(i));
                
                if (tmpList != null && !tmpList.isEmpty()) {
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
        
        return headersNum;
    }
    
    private static void encodeDataChunkWithLenPrefix(final DataOutputStream dataOutputStream,
            final DataChunk dc) throws IOException {
        
        final int len = dc.getLength();
        dataOutputStream.writeInt(len);
        
        encodeDataChunk(dataOutputStream, dc);
    }

    private static void encodeDataChunkWithLenPrefixLowerCase(final DataOutputStream dataOutputStream,
            final DataChunk dc) throws IOException {
        
        final int len = dc.getLength();
        dataOutputStream.writeInt(len);
        
        encodeDataChunkLowerCase(dataOutputStream, dc);
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

    private static void encodeDataChunkLowerCase(final DataOutputStream dataOutputStream,
            final DataChunk dc) throws IOException {
        if (dc.isNull()) {
            return;
        }

        switch (dc.getType()) {
            case Bytes: {
                final ByteChunk bc = dc.getByteChunk();
                encodeDataChunkLowerCase(dataOutputStream, bc.getBuffer(), bc.getStart(),
                        bc.getLength());

                break;
            }
                
            case Buffer: {
                final BufferChunk bufferChunk = dc.getBufferChunk();
                encodeDataChunkLowerCase(dataOutputStream, bufferChunk.getBuffer(),
                        bufferChunk.getStart(), bufferChunk.getLength());
                break;
            }
                
            default: {
                encodeDataChunkLowerCase(dataOutputStream, dc.toString());
            }
        }
    }
    
    private static void encodeDataChunkLowerCase(final DataOutputStream dataOutputStream,
            final byte[] array, final int offs, final int len) throws IOException {
        
            final int lim = offs + len;
            
            for (int i = offs; i < lim; i++) {
                dataOutputStream.write(Ascii.toLower(array[i]));
            }
    }
    
    private static void encodeDataChunkLowerCase(final DataOutputStream dataOutputStream,
            final Buffer buffer, final int offs, final int len) throws IOException {
        
        if (buffer.hasArray()) {
            encodeDataChunkLowerCase(dataOutputStream, buffer.array(),
                    buffer.arrayOffset() + offs, len);
        } else {
            final int lim = offs + len;

            for (int i = offs; i < lim; i++) {
                dataOutputStream.write(Ascii.toLower(buffer.get(i)));
            }
        }
    }

    private static void encodeDataChunkLowerCase(
            final DataOutputStream dataOutputStream,
            final String s) throws IOException {
        final int len = s.length();
        
        for (int i = 0; i < len; i++) {
            final char c = s.charAt(i);
            if (c != 0) {
                dataOutputStream.write(Ascii.toLower(c));
            } else {
                dataOutputStream.write(' ');
            }
        }
    }    

    private static void encodeHeaderValue(
            final DataOutputStream dataOutputStream,
            final byte[] nameBytes, final byte[] valueBytes) throws IOException {
        encodeHeaderValue(dataOutputStream, nameBytes, valueBytes, 0, valueBytes.length);
    }
    
    private static void encodeHeaderValue(
            final DataOutputStream dataOutputStream,
            final byte[] nameBytes, final byte[] valueBytes,
            final int valueStart, final int valueLen) throws IOException {
        dataOutputStream.writeInt(nameBytes.length);
        dataOutputStream.write(nameBytes);
        dataOutputStream.writeInt(valueLen);
        dataOutputStream.write(valueBytes, valueStart, valueLen);
    }
    
    private static void encodeHeaderValue(
            final DataOutputStream dataOutputStream,
            final byte[] nameBytes,
            final byte[] valuePart1, final byte[] valuePart2, final byte[] valuePart3)
            throws IOException {
        
        dataOutputStream.writeInt(nameBytes.length);
        dataOutputStream.write(nameBytes);
        dataOutputStream.writeInt(valuePart1.length + valuePart2.length + valuePart3.length);
        dataOutputStream.write(valuePart1);
        dataOutputStream.write(valuePart2);
        dataOutputStream.write(valuePart3);
    }    
}
