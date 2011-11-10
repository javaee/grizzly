/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011 Oracle and/or its affiliates. All rights reserved.
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
package com.sun.grizzly.http.ajp;

import com.sun.grizzly.tcp.Request;
import com.sun.grizzly.util.buf.Ascii;
import com.sun.grizzly.util.buf.ByteChunk;
import com.sun.grizzly.util.buf.CharChunk;
import com.sun.grizzly.util.buf.MessageBytes;
import com.sun.grizzly.util.http.HttpMessages;
import com.sun.grizzly.util.http.MimeHeaders;
import com.sun.grizzly.util.net.SSLSupport;

import java.io.IOException;
import java.nio.ByteBuffer;


/**
 * Utility method for Ajp message parsing and serialization.
 *
 * @author Alexey Stashok
 */
final class AjpMessageUtils {

    private static final int BODY_CHUNK_HEADER_SIZE = 7;
    private static final int MAX_BODY_CHUNK_CONTENT_SIZE = AjpConstants.MAX_READ_SIZE - BODY_CHUNK_HEADER_SIZE;

    private static final byte[] EMPTY_ARRAY = new byte[0];
    
    public static int decodeForwardRequest(byte[] buffer, int pos,
            boolean tomcatAuthentication, AjpHttpRequest request) {
        
        // Translate the HTTP method code to a String.
        byte methodCode = buffer[pos++];
        if (methodCode != AjpConstants.SC_M_JK_STORED) {
            String mName = AjpConstants.methodTransArray[(int) methodCode - 1];
            request.method().setString(mName);
        }

        pos = getBytesToByteChunk(buffer, pos, request.protocol());
        pos = getBytesToByteChunk(buffer, pos, request.requestURI());
        pos = getBytesToByteChunk(buffer, pos, request.remoteAddr());
        pos = getBytesToByteChunk(buffer, pos, request.remoteHost());
        pos = getBytesToByteChunk(buffer, pos, request.localName());

        request.setLocalPort(getShort(buffer, pos));
        pos += 2;

        final boolean isSSL = buffer[pos++] != 0;
        if (isSSL) {
            request.scheme().setString("https");
        }
        
        pos = decodeHeaders(request, buffer, pos);

        pos = decodeAttributes(buffer, pos, request, tomcatAuthentication);
        request.unparsedURI().setString(request.requestURI() +
                (request.queryString().getLength() != 0 ? "?" + request.queryString() : ""));
        
        return pos;
    }

    private static int decodeAttributes(final byte[] requestContent, int pos,
            final AjpHttpRequest req, final boolean tomcatAuthentication) {

        final MessageBytes tmpMessageBytes = req.tmpMessageBytes;
        
        boolean moreAttr = true;

        while (moreAttr) {
            final byte attributeCode = requestContent[pos++];
            if (attributeCode == AjpConstants.SC_A_ARE_DONE) {
                return pos;
            }

            /* Special case ( XXX in future API make it separate type !)
             */
            if (attributeCode == AjpConstants.SC_A_SSL_KEY_SIZE) {
                // Bug 1326: it's an Integer.
                req.setAttribute(SSLSupport.KEY_SIZE_KEY,
                        getShort(requestContent, pos));
                pos += 2;
            }

            if (attributeCode == AjpConstants.SC_A_REQ_ATTRIBUTE) {
                // 2 strings ???...
                pos = setStringAttribute(req, requestContent, pos);
            }


            // 1 string attributes
            switch (attributeCode) {
                case AjpConstants.SC_A_CONTEXT:
                    pos = skipBytes(requestContent, pos);
                    // nothing
                    break;

                case AjpConstants.SC_A_SERVLET_PATH:
                    pos = skipBytes(requestContent, pos);
                    // nothing
                    break;

                case AjpConstants.SC_A_REMOTE_USER:
                    if (tomcatAuthentication) {
                        // ignore server
                        pos = skipBytes(requestContent, pos);
                    } else {
                        pos = getBytesToByteChunk(requestContent, pos,
                                req.getRemoteUser());
                    }
                    break;

                case AjpConstants.SC_A_AUTH_TYPE:
                    if (tomcatAuthentication) {
                        // ignore server
                        pos = skipBytes(requestContent, pos);
                    } else {
                        pos = getBytesToByteChunk(requestContent, pos,
                                req.getAuthType());
                    }
                    break;

                case AjpConstants.SC_A_QUERY_STRING:
                    pos = getBytesToByteChunk(requestContent, pos,
                            req.queryString());
                    break;

                case AjpConstants.SC_A_JVM_ROUTE:
                    pos = getBytesToByteChunk(requestContent, pos,
                            req.instanceId());
                    break;

                case AjpConstants.SC_A_SSL_CERT:
                    req.scheme().setString("https");
                    // SSL certificate extraction is costy, initialize on demand
                    pos = getBytesToByteChunk(requestContent, pos, req.sslCert());
                    break;

                case AjpConstants.SC_A_SSL_CIPHER:
                    req.scheme().setString("https");
                    pos = setStringAttributeValue(req,
                            SSLSupport.CIPHER_SUITE_KEY, requestContent, pos);
                    break;

                case AjpConstants.SC_A_SSL_SESSION:
                    req.scheme().setString("https");
                    pos = setStringAttributeValue(req,
                            SSLSupport.SESSION_ID_KEY, requestContent, pos);
                    break;

                case AjpConstants.SC_A_SECRET:
                    pos = getBytesToByteChunk(requestContent, pos, tmpMessageBytes);

                    req.setSecret(tmpMessageBytes.toString());
                    tmpMessageBytes.recycle();

                    break;

                case AjpConstants.SC_A_STORED_METHOD:
                    pos = getBytesToByteChunk(requestContent, pos, req.method());
                    break;

                default:
                    break; // ignore, we don't know about it - backward compat
            }
        }
        
        return pos;
    }

    public static String getString(ByteBuffer buffer) {
        final int length = getShort(buffer);
        final String s = new String(buffer.array(), buffer.position(), length);
        // Don't forget to skip the terminating \0 (that's why "+ 1")
        advance(buffer, length + 1);

        return s;
    }

    public static int decodeHeaders(final Request req, final byte[] buf, int pos) {
        // Decode headers
        final MimeHeaders headers = req.getMimeHeaders();

        final int hCount = getShort(buf, pos);
        pos += 2;

        for (int i = 0; i < hCount; i++) {
            String hName;

            // Header names are encoded as either an integer code starting
            // with 0xA0, or as a normal string (in which case the first
            // two bytes are the length).
            int isc = getShort(buf, pos);
            int hId = isc & 0xFF;

            pos += 2;
            
            MessageBytes valueDC;
            isc &= 0xFF00;
            if (0xA000 == isc) {
                hName = AjpConstants.headerTransArray[hId - 1];
                valueDC = headers.addValue(hName);
            } else {
                // reset hId -- if the header currently being read
                // happens to be 7 or 8 bytes long, the code below
                // will think it's the content-type header or the
                // content-length header - SC_REQ_CONTENT_TYPE=7,
                // SC_REQ_CONTENT_LENGTH=8 - leading to unexpected
                // behaviour.  see bug 5861 for more information.
                hId = -1;

                pos -= 2;
                
                final int length = getShort(buf, pos);
                pos += 2;

                valueDC = headers.addValue(buf, pos, length);
                // Don't forget to skip the terminating \0 (that's why "+ 1")
                pos += length + 1;
            }

            pos = getBytesToByteChunk(buf, pos, valueDC);

            // Get the last added header name (the one we need)
            final MessageBytes headerNameDC = headers.getName(headers.size() - 1);

            if (hId == AjpConstants.SC_REQ_CONTENT_LENGTH
                    || hId == -1 && headerNameDC.equalsIgnoreCase("Content-Length")) {
                // just read the content-length header, so set it
                final ByteChunk bc = valueDC.getByteChunk();
                final long cl = Ascii.parseLong(bc.getBytes(), bc.getStart(), bc.getEnd() - bc.getStart());
                if (cl < Integer.MAX_VALUE) {
                    req.setContentLength((int) cl);
                }
            } else if (hId == AjpConstants.SC_REQ_CONTENT_TYPE
                    || hId == -1 && headerNameDC.equalsIgnoreCase("Content-Type")) {
                // just read the content-type header, so set it
                req.setContentType(valueDC.toString());
            }
        }
        
        return pos;
    }

    static int getBytesToByteChunk(final byte[] buffer, int pos,
            final MessageBytes bytes) {
        final int length = getShort(buffer, pos);
        pos += 2;
        
        if (length != 0xFFFF) {
            bytes.setBytes(buffer, pos, length);
            // Don't forget to skip the terminating \0 (that's why "+ 1")
            pos += length + 1;
        }
        
        return pos;
    }

    private static void advance(ByteBuffer buffer, int length) {
        buffer.position(buffer.position() + length);
    }

    private static int setStringAttribute(final AjpHttpRequest req,
            final byte[] buffer, int offset) {
        final MessageBytes tmpMessageBytes = req.tmpMessageBytes;

        offset = getBytesToByteChunk(buffer, offset, tmpMessageBytes);
        final String key = tmpMessageBytes.toString();

        tmpMessageBytes.recycle();

        offset = getBytesToByteChunk(buffer, offset, tmpMessageBytes);        
        final String value = tmpMessageBytes.toString();
        
        tmpMessageBytes.recycle();

        req.setAttribute(key, value);
        
        return offset;
    }

    private static int setStringAttributeValue(final AjpHttpRequest req,
            final String key, final byte[] buffer, int pos) {

        final MessageBytes tmpMessageBytes = req.tmpMessageBytes;
        
        pos = getBytesToByteChunk(buffer, pos, tmpMessageBytes);
        final String value = tmpMessageBytes.toString();
        
        tmpMessageBytes.recycle();

        req.setAttribute(key, value);
        return pos;
    }

    public static ByteChunk encodeHeaders(AjpHttpResponse response) {
        try {
            final ByteChunk headerBuffer = response.tmpHeaderByteChunk;
            final int start = headerBuffer.getStart();

            headerBuffer.setEnd(start + 4); // reserve 4 bytes for AJP header
            
            headerBuffer.append(AjpConstants.JK_AJP13_SEND_HEADERS);
            putShort(headerBuffer, (short) response.getStatus());
            String message = null;
            if (response.isAllowCustomReasonPhrase()) {
                message = response.getMessage();
            }

            if (message == null) {
                message = HttpMessages.getMessage(response.getStatus());
            }

            putBytes(headerBuffer, message);

            if (false/*response.isAcknowledgement()*/) {
                // If it's acknowledgment packet - don't encode the headers
                // Serialize 0 num_headers
                putShort(headerBuffer, (short) 0);
            } else {
                final MimeHeaders headers = response.getMimeHeaders();
                final String contentType = response.getContentType();
                if (contentType != null) {
                    headers.setValue("Content-Type").setString(contentType);
                }
                final String contentLanguage = response.getContentLanguage();
                if (contentLanguage != null) {
                    headers.setValue("Content-Language").setString(contentLanguage);
                }
                final long contentLength = response.getContentLength();
                if (contentLength >= 0) {
                    headers.setValue("Content-Length").setLong(contentLength);
                }

                final int numHeaders = headers.size();

                putShort(headerBuffer, (short) numHeaders);

                for (int i = 0; i < numHeaders; i++) {
                    putBytes(headerBuffer, headers.getName(i));
                    putBytes(headerBuffer, headers.getValue(i));
                }
            }

            // Add Ajp message header
            final byte[] headerBytes = headerBuffer.getBuffer();
            headerBytes[start] = ((byte) 'A');
            headerBytes[start + 1] = ((byte) 'B');

            
            putShort(headerBytes, start + 2, (short) (headerBuffer.getLength() - 4));

            return headerBuffer;
        } catch (IOException e) {
            throw new IllegalStateException("Unexpected exception", e);
        }
    }
    private static int skipBytes(final byte[] buffer, int pos) {

        final int length = getShort(buffer, pos);
        pos += 2;
        
        if (!isNullLength(length)) {
            pos += length;
        }

        // Don't forget to skip the terminating \0 (that's why "+ 1")
        pos++;
        
        return pos;
    }

    static int getShort(final ByteBuffer buffer) {
        return buffer.getShort() & 0xFFFF;
    }

    static int getShort(byte[] b, int off) {
	return ((short) (((b[off + 1] & 0xFF)) + 
			((b[off + 0]) << 8))) & 0xFFFF;
    }
    
    static int getInt(byte[] b, int off) {
	return ((b[off + 3] & 0xFF)) +
	       ((b[off + 2] & 0xFF) << 8) +
	       ((b[off + 1] & 0xFF) << 16) +
	       ((b[off + 0]) << 24);
    }
    
    public static void putShort(final ByteChunk chunk, final short value)
            throws IOException {
        chunk.append((byte) (value >> 8));
        chunk.append((byte) (value & 0xFF));
    }

    public static void putShort(final byte[] b, final int off, final short value) {
        b[off] = ((byte) (value >> 8));
        b[off + 1] = ((byte) (value & 0xFF));
    }
    
    private static void putBytes(ByteChunk dstBuffer, final MessageBytes mb)
            throws IOException {
        
        if (mb == null || mb.isNull()) {
            putBytes(dstBuffer, EMPTY_ARRAY);
            return;
        }
        
        if (mb.getType() == MessageBytes.T_BYTES) {
            final ByteChunk bc = mb.getByteChunk();
            putBytes(dstBuffer, bc.getBytes(), bc.getStart(), bc.getLength());
        } else if (mb.getType() == MessageBytes.T_CHARS) {
            final CharChunk cc = mb.getCharChunk();
            putBytes(dstBuffer, cc);
        } else {
            putBytes(dstBuffer, mb.toString());
        }
    }

    private static void putBytes(final ByteChunk dstBuffer, final byte[] bytes)
            throws IOException {
        putBytes(dstBuffer, bytes, 0, bytes.length);
    }

    private static void putBytes(final ByteChunk dstBuffer,
            final byte[] bytes, final int start, final int length)
            throws IOException {
        
        dstBuffer.makeSpace(length + 3);
        
        final byte[] dstArray = dstBuffer.getBuffer();
        int pos = dstBuffer.getEnd();
        putShort(dstArray, pos, (short) length);
        pos += 2;

        System.arraycopy(bytes, start, dstArray, pos, length);
        pos += length;
        
        dstArray[pos++] = 0;

        dstBuffer.setEnd(pos);
    }

    private static void putBytes(final ByteChunk dstBuffer, final CharChunk cc)
            throws IOException {
        
        if (cc == null || cc.isNull()) {
            putBytes(dstBuffer, EMPTY_ARRAY);
            return;
        }
        
        final int length = cc.getLength();
        
        dstBuffer.makeSpace(length + 3);
        
        final byte[] dstArray = dstBuffer.getBuffer();
        int pos = dstBuffer.getEnd();
        
        putShort(dstArray, pos, (short) length);
        pos += 2;
        
        final int start = cc.getStart();
        final int end = cc.getEnd();
        final char[] cbuf = cc.getBuffer();
        for (int i = start; i < end; i++) {
            char c = cbuf[i];
            if ((c <= 31 && c != 9) || c == 127 || c > 255) {
                c = ' ';
            }
            
            dstArray[pos++] = (byte) c;
        }
        
        dstArray[pos++] = 0;
        
        dstBuffer.setEnd(pos);
    }

    private static void putBytes(final ByteChunk dstBuffer, final String s)
            throws IOException {
        
        if (s == null) {
            putBytes(dstBuffer, EMPTY_ARRAY);
            return;
        }
        
        final int length = s.length();
        
        dstBuffer.makeSpace(length + 3);
        
        final byte[] dstArray = dstBuffer.getBuffer();
        int pos = dstBuffer.getEnd();
        
        putShort(dstArray, pos, (short) length);
        pos += 2;
        
        for (int i = 0; i < length; i++) {
            char c = s.charAt(i);
            if ((c <= 31 && c != 9) || c == 127 || c > 255) {
                c = ' ';
            }
            
            dstArray[pos++] = (byte) c;
        }
        
        dstArray[pos++] = 0;
        
        dstBuffer.setEnd(pos);
    }

    private static boolean isNullLength(final int length) {
        return length == 0xFFFF || length == -1;
    }
    
    public static byte[] createAjpPacket(final byte type, byte[] payload) {
        final int length = payload.length;
        final byte[] ajpMessage = new byte[5 + length];

        ajpMessage[0] = 'A';
        ajpMessage[1] = 'B';
        putShort(ajpMessage, 2, (short) (length + 1));
        ajpMessage[4] = type;

        System.arraycopy(payload, 0, ajpMessage, 5, length);

        return ajpMessage;
    }

    public static byte[] toBytes(short size) {
        return new byte[]{(byte) (size >> 8), (byte) (size & 0xFF)};
    }
}