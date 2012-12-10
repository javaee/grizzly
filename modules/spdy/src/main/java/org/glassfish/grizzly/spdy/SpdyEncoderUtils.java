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
import org.glassfish.grizzly.memory.MemoryManager;
import org.glassfish.grizzly.spdy.compression.SpdyDeflaterOutputStream;

import static org.glassfish.grizzly.http.util.Constants.*;
import static org.glassfish.grizzly.http.util.HttpCodecUtils.*;

/**
 *
 * @author oleksiys
 */
class SpdyEncoderUtils {
    

    
    @SuppressWarnings("unchecked")
    static Buffer encodeSynReplyHeaders(final SpdySession spdySession,
            final HttpResponsePacket response) throws IOException {

        Buffer buffer = spdySession.getMemoryManager().allocateAtLeast(2048);
        final MimeHeaders headers = response.getHeaders();
        
        headers.removeHeader(Header.Connection);
        headers.removeHeader(Header.KeepAlive);
        headers.removeHeader(Header.ProxyConnection);
        headers.removeHeader(Header.TransferEncoding);
        
        final DataOutputStream dataOutputStream =
                spdySession.getDeflaterDataOutputStream();
        final SpdyDeflaterOutputStream deflaterOutputStream =
                spdySession.getDeflaterOutputStream();

        deflaterOutputStream.setInitialOutputBuffer(buffer);
        
        final int mimeHeadersCount = headers.size();
        
        dataOutputStream.writeInt(mimeHeadersCount + 2);


        if (response.isCustomReasonPhraseSet()) {
            // don't apply HttpStatus.filter(DataChunk)
            encodeHeaderValue(dataOutputStream, ":status".getBytes(),
                    response.getHttpStatus().getStatusBytes(), " ".getBytes(),
                    response.getReasonPhraseDC().toString().getBytes(DEFAULT_HTTP_CHARACTER_ENCODING));
        } else {
            encodeHeaderValue(dataOutputStream, ":status".getBytes(),
                    response.getHttpStatus().getStatusBytes(), " ".getBytes(),
                    response.getHttpStatus().getReasonPhraseBytes());
        }
        
        encodeHeaderValue(dataOutputStream, ":version".getBytes(),
                response.getProtocol().getProtocolBytes());
                
        encodeUserHeaders(spdySession, headers, dataOutputStream);
        
        dataOutputStream.flush();
        
        return deflaterOutputStream.checkpoint();
    }
    
    @SuppressWarnings("unchecked")
    static Buffer encodeSynStreamHeaders(final SpdySession spdySession,
            final HttpRequestPacket request) throws IOException {

        Buffer outputBuffer = spdySession.getMemoryManager().allocateAtLeast(2048);
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
        
        encodeHeaderValue(dataOutputStream, ":method".getBytes(),
                request.getMethod().getMethodBytes());
        
        encodeHeaderValue(dataOutputStream, ":path".getBytes(),
                requestURI, pathStart, pathLen);
        
        encodeHeaderValue(dataOutputStream, ":version".getBytes(),
                request.getProtocol().getProtocolBytes());

        encodeHeaderValue(dataOutputStream, ":host".getBytes(),
                hostHeader.getBytes(DEFAULT_HTTP_CHARACTER_ENCODING));

        if (schemeLen > 0) {
            encodeHeaderValue(dataOutputStream, ":scheme".getBytes(),
                    requestURI, schemeStart, schemeLen);
        } else {
            encodeHeaderValue(dataOutputStream, ":scheme".getBytes(),
                    "https".getBytes(DEFAULT_HTTP_CHARACTER_ENCODING));
        }
        
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
