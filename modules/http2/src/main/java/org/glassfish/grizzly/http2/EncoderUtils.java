/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2014-2015 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.http2;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.http.HttpResponsePacket;
import org.glassfish.grizzly.http.util.Ascii;
import org.glassfish.grizzly.http.util.BufferChunk;
import org.glassfish.grizzly.http.util.ByteChunk;
import org.glassfish.grizzly.http.util.DataChunk;
import org.glassfish.grizzly.http.util.Header;
import org.glassfish.grizzly.http.util.MimeHeaders;

import static org.glassfish.grizzly.http.util.Constants.*;
import static org.glassfish.grizzly.http.util.DataChunk.Type.Buffer;
import static org.glassfish.grizzly.http.util.DataChunk.Type.Bytes;
import org.glassfish.grizzly.http.util.FastHttpDateFormat;
import static org.glassfish.grizzly.http.util.HttpCodecUtils.*;
import org.glassfish.grizzly.http2.compression.HeadersEncoder;
import org.glassfish.grizzly.memory.Buffers;
import org.glassfish.grizzly.ssl.SSLUtils;

/**
 * HTTP Packet -> SpdyFrames encoder utils.
 * 
 * @author Grizzly team
 */
class EncoderUtils {
    private static final String HTTP = "http";
    private static final byte[] HTTP_BYTES = HTTP.getBytes(DEFAULT_HTTP_CHARSET);

    private static final String HTTPS = "https";
    private static final byte[] HTTPS_BYTES = HTTPS.getBytes(DEFAULT_HTTP_CHARSET);
    
    @SuppressWarnings("unchecked")
    static Buffer encodeResponseHeaders(final Http2Connection http2Connection,
            final HttpResponsePacket response) throws IOException {
        
        assert http2Connection.getDeflaterLock().isLocked();
        
        final MimeHeaders headers = response.getHeaders();
        
        headers.removeHeader(Header.Connection);
        headers.removeHeader(Header.KeepAlive);
        headers.removeHeader(Header.ProxyConnection);
        headers.removeHeader(Header.TransferEncoding);
        headers.removeHeader(Header.Upgrade);
        
        final HeadersEncoder encoder = http2Connection.getHeadersEncoder();

//        encoder.encodeHeader(Constants.STATUS_HEADER_BYTES,
//                response.getHttpStatus().getStatusBytes(), false);
        
        encoder.encodeHeader(Constants.STATUS_HEADER,
                String.valueOf(response.getHttpStatus().getStatusCode()));

        encodeUserHeaders(headers, encoder);

        return encoder.flushHeaders();
    }
    
    @SuppressWarnings("unchecked")
    static Buffer encodeRequestHeaders(
            final Http2Connection http2Connection,
            final HttpRequestPacket request) throws IOException {

        assert http2Connection.getDeflaterLock().isLocked();
        
        // ----------------- Parse URI scheme and path ----------------
//        int schemeStart = -1;
//        int schemeLen = -1;
//
//        final byte[] requestURI =
//                request.getRequestURI().getBytes(DEFAULT_HTTP_CHARACTER_ENCODING);
//        final int len = requestURI.length;
//
//        final int nonSpaceIdx = skipSpaces(requestURI, 0, len, len);
//        final int idx = ByteChunk.indexOf(requestURI, nonSpaceIdx, len, '/');
//
//        if (idx > 0 && idx < len - 1 &&
//                requestURI[idx - 1] == ':' && requestURI[idx + 1] == '/') {
//            schemeStart = nonSpaceIdx;
//            schemeLen = idx - schemeStart - 1;
//        }
//
//        
//        final int pathStart = schemeStart == -1 ?
//                idx :
//                ByteChunk.indexOf(requestURI, idx + 2, len, '/');
//        final int pathLen = len - pathStart;
//
//        if (pathStart == -1) {
//            throw new IllegalStateException("Request URI path is not set");
//        }
        
        int schemeStart = -1;
        int schemeLen = -1;

        final String requestURI = request.getRequestURI().trim();
        
        final int len = requestURI.length();

        final int idx = requestURI.indexOf('/');

        if (idx > 0 && idx < len - 1 &&
                requestURI.charAt(idx - 1) == ':' && requestURI.charAt(idx + 1) == '/') {
            schemeStart = 0;
            schemeLen = idx - 1;
        }

        
        final int pathStart = schemeStart == -1 ?
                idx :
                requestURI.indexOf('/', idx + 2);
        final int pathLen = len - pathStart;

        if (pathStart == -1) {
            throw new IllegalStateException("Request URI path is not set");
        }
        
// ---------------------------------------------------------------

        final MimeHeaders headers = request.getHeaders();
        
        String hostHeader = headers.getHeader(Header.Host);
        
        if (hostHeader == null) {
            if (schemeStart == -1) {
                throw new IllegalStateException("Missing the Host header");
            }

            hostHeader = requestURI.substring(schemeStart + schemeLen + 3, pathStart);
        }
        
        headers.removeHeader(Header.Connection);
        headers.removeHeader(Header.Host);
        headers.removeHeader(Header.KeepAlive);
        headers.removeHeader(Header.ProxyConnection);
        headers.removeHeader(Header.TransferEncoding);
        headers.removeHeader(Header.Upgrade);
        
        final HeadersEncoder encoder = http2Connection.getHeadersEncoder();

        encoder.encodeHeader(Constants.METHOD_HEADER,
                request.getMethod().toString());

        if (schemeLen > 0) {
            encoder.encodeHeader(Constants.SCHEMA_HEADER,
                    requestURI.substring(0, schemeLen));
        } else {
            // guess
            encoder.encodeHeader(Constants.SCHEMA_HEADER,
                    ((SSLUtils.getSSLEngine(http2Connection.getConnection()) == null)
                    ? HTTP
                    : HTTPS));
        }

        encoder.encodeHeader(Constants.AUTHORITY_HEADER, hostHeader);

        encoder.encodeHeader(Constants.PATH_HEADER,
                (pathLen == requestURI.length())
                    ? requestURI
                    : requestURI.substring(pathStart, pathStart + pathLen));
        
//        encoder.encodeHeader(Constants.METHOD_HEADER_BYTES,
//                request.getMethod().getMethodBytes(), false);
//
//        if (schemeLen > 0) {
//            encoder.encodeHeader(Constants.SCHEMA_HEADER_BYTES,
//                    Arrays.copyOfRange(requestURI, schemeStart,
//                            schemeStart + schemeLen), false);
//        } else {
//            encoder.encodeHeader(Constants.SCHEMA_HEADER_BYTES,
//                    ((SSLUtils.getSSLEngine(http2Connection.getConnection()) == null)
//                    ? HTTP_BYTES
//                    : HTTPS_BYTES),
//                    false);
//        }
//
//        encoder.encodeHeader(Constants.AUTHORITY_HEADER_BYTES,
//                (hostHeaderLen == hostHeaderBytes.length)
//                    ? hostHeaderBytes
//                    : Arrays.copyOfRange(hostHeaderBytes, hostHeaderStart, hostHeaderStart + hostHeaderLen),
//                false);
//
//        encoder.encodeHeader(Constants.PATH_HEADER_BYTES,
//                (pathLen == requestURI.length)
//                    ? requestURI
//                    : Arrays.copyOfRange(requestURI, pathStart, pathStart + pathLen),
//                false);

        encodeUserHeaders(headers, encoder);

        return encoder.flushHeaders();
    }

    @SuppressWarnings("unchecked")
    private static void encodeUserHeaders(final MimeHeaders headers,
            final HeadersEncoder encoder) throws IOException {
        
        final int mimeHeadersCount = headers.size();
        List<DataChunk> tmpList = null;
        
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
                                if (!value1.isNull()) {
                                    tmpList.add(value1);
                                    valueSize += value1.getLength();
                                }
                            }
                            tmpList.add(value);
                            valueSize += value.getLength();
                        }
                    }
                }
                
////                encodeDataChunkWithLenPrefixLowerCase(encoder, headers.getName(i));
//                final byte[] nameBytes = nameToLowerCaseByteArray(headers.getName(i));
//                final byte[] valueBytes;
//                if (tmpList != null && !tmpList.isEmpty()) {
//                    final int valuesCount = tmpList.size();
//                    
//                    valueSize += valuesCount - 1; // 0 delims
//                    
//                    valueBytes = new byte[valueSize];
//                    int offs = 0;
//                    for (int j = 0; j < valuesCount; j++) {
//                        offs += valueToByteArray(tmpList.get(j), valueBytes, offs);
//                        offs++; // 0 delim
//                    }
//                    
//                    tmpList.clear();
//                } else {
//                    valueBytes = valueToByteArray(value1);
//                }
//                
//                encoder.encodeHeader(nameBytes, valueBytes, false);

                final String nameStr = nameToLowerCase(name);
                
                String valueStr;
                if (tmpList != null && !tmpList.isEmpty()) {
                    final int valuesCount = tmpList.size();
                    
                    valueSize += valuesCount - 1; // 0 delims
                    
                    final StringBuilder sb = new StringBuilder(valueSize);
                    int j = 0;
                    
                    for (;j < valuesCount - 1; j++) {
                        sb.append(tmpList.get(j).toString());
                        sb.append('\u0000');
                    }
                    
                    sb.append(tmpList.get(j).toString());
                    
                    valueStr = sb.toString();
                    tmpList.clear();
                } else {
                    valueStr = value1.toString();
                }
                
                encoder.encodeHeader(nameStr, valueStr);
            
            }
        }
    }
    
    private static byte[] nameToLowerCaseByteArray(final DataChunk name) {
        final int length = name.getLength();
        final byte[] lowercase = new byte[length];
        
        if (name.getType() == Bytes) {
            final ByteChunk byteChunk = name.getByteChunk();
            final byte[] bytes = byteChunk.getBuffer();
            final int offs = byteChunk.getStart();
            for (int i = 0; i < length; i++) {
                lowercase[i] = (byte) Ascii.toLower(bytes[i + offs]);
            }
        } else if (name.getType() == Buffer) {
            final BufferChunk bufferChunk = name.getBufferChunk();
            final Buffer buffer = bufferChunk.getBuffer();
            final int offs = bufferChunk.getStart();
            for (int i = 0; i < length; i++) {
                lowercase[i] = (byte) Ascii.toLower(buffer.get(i + offs));
            }
        } else {
            final String s = name.toString();
            for (int i = 0; i < length; i++) {
                lowercase[i] = (byte) Ascii.toLower(s.charAt(i));
            }
        }
        
        return lowercase;
    }

    private static String nameToLowerCase(final DataChunk name) {
        final int length = name.getLength();
        final StringBuilder sb = new StringBuilder(length);
        
        if (name.getType() == Bytes) {
            final ByteChunk byteChunk = name.getByteChunk();
            final byte[] bytes = byteChunk.getBuffer();
            final int offs = byteChunk.getStart();
            for (int i = 0; i < length; i++) {
                sb.append((char) Ascii.toLower(bytes[i + offs]));
            }
        } else if (name.getType() == Buffer) {
            final BufferChunk bufferChunk = name.getBufferChunk();
            final Buffer buffer = bufferChunk.getBuffer();
            final int offs = bufferChunk.getStart();
            for (int i = 0; i < length; i++) {
                sb.append((char) Ascii.toLower(buffer.get(i + offs)));
            }
        } else {
            final String s = name.toString();
            for (int i = 0; i < length; i++) {
                sb.append((char) Ascii.toLower(s.charAt(i)));
            }
        }
        
        return sb.toString();
    }
    
    private static int valueToByteArray(final DataChunk value,
            final byte[] dstArray, int arrayOffs) {
        
        final int length = value.getLength();

        if (value.getType() == Bytes) {
            final ByteChunk byteChunk = value.getByteChunk();
            final byte[] bytes = byteChunk.getBuffer();
            final int offs = byteChunk.getStart();
            System.arraycopy(bytes, offs, dstArray, arrayOffs, length);
        } else if (value.getType() == Buffer) {
            final BufferChunk bufferChunk = value.getBufferChunk();
            final Buffer buffer = bufferChunk.getBuffer();
            final int offs = bufferChunk.getStart();
            
            final int oldPos = buffer.position();
            final int oldLim = buffer.limit();
            
            Buffers.setPositionLimit(buffer, offs, offs + length);
            buffer.get(dstArray, arrayOffs, length);
            Buffers.setPositionLimit(buffer, oldPos, oldLim);
        } else {
            final String s = value.toString();
            for (int i = 0; i < length; i++) {
                dstArray[arrayOffs + i] = (byte) s.charAt(i);
            }
        }
        
        return length;
    }

    private static byte[] valueToByteArray(final DataChunk value) {
        final int length = value.getLength();

        if (value.getType() == Bytes) {
            final ByteChunk byteChunk = value.getByteChunk();
            final byte[] bytes = byteChunk.getBuffer();
            final int offs = byteChunk.getStart();
            
            if (bytes.length == length) {
                return bytes;
            }
            
            final byte[] dstArray = new byte[length];
            System.arraycopy(bytes, offs, dstArray, 0, length);
            return dstArray;
        } else if (value.getType() == Buffer) {
            final BufferChunk bufferChunk = value.getBufferChunk();
            final Buffer buffer = bufferChunk.getBuffer();
            
            if (buffer.hasArray() && buffer.array().length == length) {
                return buffer.array();
            }
            
            final byte[] dstArray = new byte[length];
            final int offs = bufferChunk.getStart();
            
            final int oldPos = buffer.position();
            final int oldLim = buffer.limit();
            
            Buffers.setPositionLimit(buffer, offs, offs + length);
            buffer.get(dstArray);
            Buffers.setPositionLimit(buffer, oldPos, oldLim);
            return dstArray;
        } else {
            final byte[] dstArray = new byte[length];
            final String s = value.toString();
            for (int i = 0; i < length; i++) {
                dstArray[i] = (byte) s.charAt(i);
            }
            
            return dstArray;
        }
    }
}
