/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2014-2017 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://oss.oracle.com/licenses/CDDL+GPL-1.1
 * or LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at LICENSE.txt.
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
import java.util.Map;

import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.http.HttpResponsePacket;
import org.glassfish.grizzly.http.util.Ascii;
import org.glassfish.grizzly.http.util.BufferChunk;
import org.glassfish.grizzly.http.util.ByteChunk;
import org.glassfish.grizzly.http.util.DataChunk;
import org.glassfish.grizzly.http.util.Header;
import org.glassfish.grizzly.http.util.MimeHeaders;

import static org.glassfish.grizzly.http.util.DataChunk.Type.Buffer;
import static org.glassfish.grizzly.http.util.DataChunk.Type.Bytes;

import org.glassfish.grizzly.memory.Buffers;
import org.glassfish.grizzly.ssl.SSLUtils;
import org.glassfish.grizzly.utils.Charsets;

/**
 * HTTP Packet -> HTTP/2 frames encoder utils.
 * 
 * @author Grizzly team
 */
class EncoderUtils extends EncoderDecoderUtilsBase {
    private static final String HTTP = "http";
    private static final String HTTPS = "https";

    @SuppressWarnings("unchecked")
    static Buffer encodeResponseHeaders(final Http2Session http2Session,
                                        final HttpResponsePacket response,
                                        final Map<String,String> capture)
            throws IOException {
        
        assert http2Session.getDeflaterLock().isLocked();
        
        final MimeHeaders headers = response.getHeaders();
        
        headers.removeHeader(Header.Connection);
        headers.removeHeader(Header.KeepAlive);
        headers.removeHeader(Header.ProxyConnection);
        headers.removeHeader(Header.TransferEncoding);
        headers.removeHeader(Header.Upgrade);
        
        final HeadersEncoder encoder = http2Session.getHeadersEncoder();

//        encoder.encodeHeader(Constants.STATUS_HEADER_BYTES,
//                response.getHttpStatus().getStatusBytes(), false);
        
        encoder.encodeHeader(STATUS_HEADER,
                String.valueOf(response.getHttpStatus().getStatusCode()), capture);

        encodeUserHeaders(headers, encoder, capture);

        return encoder.flushHeaders();
    }
    
    @SuppressWarnings("unchecked")
    static Buffer encodeRequestHeaders(
            final Http2Session http2Session,
            final HttpRequestPacket request,
            final Map<String,String> capture) throws IOException {

        assert http2Session.getDeflaterLock().isLocked();
        
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
        //headers.removeHeader(Header.Host);
        headers.removeHeader(Header.KeepAlive);
        headers.removeHeader(Header.ProxyConnection);
        headers.removeHeader(Header.TransferEncoding);
        headers.removeHeader(Header.Upgrade);
        
        final HeadersEncoder encoder = http2Session.getHeadersEncoder();

        encoder.encodeHeader(METHOD_HEADER,
                request.getMethod().toString(), capture);

        if (schemeLen > 0) {
            encoder.encodeHeader(SCHEMA_HEADER,
                    requestURI.substring(0, schemeLen), capture);
        } else {
            // guess
            encoder.encodeHeader(SCHEMA_HEADER,
                    ((SSLUtils.getSSLEngine(http2Session.getConnection()) == null)
                        ? HTTP
                        : HTTPS),
                    capture);
        }

        encoder.encodeHeader(AUTHORITY_HEADER, hostHeader, capture);

        String path = (pathLen == requestURI.length())
                ? requestURI
                : requestURI.substring(pathStart, pathStart + pathLen);
        final DataChunk query = request.getQueryStringDC();
        if (!query.isNull()) {
            path += '?' + query.toString(Charsets.UTF8_CHARSET);
        }
        encoder.encodeHeader(PATH_HEADER, path, capture);
        
        encodeUserHeaders(headers, encoder, capture);

        return encoder.flushHeaders();
    }

    static Buffer encodeTrailerHeaders(final Http2Session http2Session,
                                       final MimeHeaders trailers,
                                       final Map<String,String> capture) {
        assert http2Session.getDeflaterLock().isLocked();

        if (trailers == null || trailers.size() == 0) {
            return Buffers.EMPTY_BUFFER;
        }

        final HeadersEncoder encoder = http2Session.getHeadersEncoder();
        for (final String name : trailers.names()) {
            encoder.encodeHeader(name, trailers.getHeader(name), capture);
        }

        return encoder.flushHeaders();
    }

    @SuppressWarnings("unchecked")
    private static void encodeUserHeaders(final MimeHeaders headers,
                                          final HeadersEncoder encoder,
                                          final Map<String,String> capture)
            throws IOException {
        
        final int mimeHeadersCount = headers.size();

        for (int i = 0; i < mimeHeadersCount; i++) {

            if (!headers.setSerialized(i, true)) {
                final String nameStr = nameToLowerCase(headers.getName(i));
                final DataChunk value = headers.getValue(i);
                if (!value.isNull()) {
                    encoder.encodeHeader(nameStr, value.toString(), capture);
                }

            }
        }
    }
    
    @SuppressWarnings("unused")
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
    
    @SuppressWarnings("unused")
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

    @SuppressWarnings("unused")
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
