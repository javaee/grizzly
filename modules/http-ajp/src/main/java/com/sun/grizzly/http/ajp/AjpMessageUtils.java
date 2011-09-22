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
import com.sun.grizzly.util.buf.HexUtils;
import com.sun.grizzly.util.buf.MessageBytes;
import com.sun.grizzly.util.http.HttpMessages;
import com.sun.grizzly.util.http.MimeHeaders;
import com.sun.grizzly.util.net.SSLSupport;

import java.io.CharConversionException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;


/**
 * Utility method for Ajp message parsing and serialization.
 *
 * @author Alexey Stashok
 */
final class AjpMessageUtils {

    private static final int BODY_CHUNK_HEADER_SIZE = 7;
    private static final int MAX_BODY_CHUNK_CONTENT_SIZE = AjpConstants.MAX_READ_SIZE - BODY_CHUNK_HEADER_SIZE;

    public static void decodeForwardRequest(ByteBuffer buffer, boolean tomcatAuthentication, AjpHttpRequest request)
            throws IOException {
        // Translate the HTTP method code to a String.
        byte methodCode = buffer.get();
        if (methodCode != AjpConstants.SC_M_JK_STORED) {
            String mName = AjpConstants.methodTransArray[(int) methodCode - 1];
            request.method().setString(mName);
        }

        getBytesToByteChunk(buffer, request.protocol());
        getBytesToByteChunk(buffer, request.requestURI());
        getBytesToByteChunk(buffer, request.remoteAddr());
        getBytesToByteChunk(buffer, request.remoteHost());
        getBytesToByteChunk(buffer, request.localName());

        request.setLocalPort(getShort(buffer));

        final boolean isSSL = buffer.get() != 0;
        request.setSecure(isSSL);
        ((AjpHttpResponse) request.getResponse()).setSecure(isSSL);

        decodeHeaders(request, buffer);

        decodeAttributes(buffer, request, tomcatAuthentication);
        request.unparsedURI().setString(request.requestURI() +
                (request.queryString().getLength() != 0 ? "?" + request.queryString() : ""));
    }

    private static void decodeAttributes(final ByteBuffer requestContent,
            final AjpHttpRequest req, final boolean tomcatAuthentication) {

        boolean moreAttr = true;

        while (moreAttr) {
            final byte attributeCode = requestContent.get();
            if (attributeCode == AjpConstants.SC_A_ARE_DONE) {
                return;
            }

            /* Special case ( XXX in future API make it separate type !)
             */
            if (attributeCode == AjpConstants.SC_A_SSL_KEY_SIZE) {
                // Bug 1326: it's an Integer.
                req.setAttribute(SSLSupport.KEY_SIZE_KEY, getShort(requestContent));
            }

            if (attributeCode == AjpConstants.SC_A_REQ_ATTRIBUTE) {
                // 2 strings ???...
                setStringAttribute(req, requestContent);
            }


            // 1 string attributes
            switch (attributeCode) {
                case AjpConstants.SC_A_CONTEXT:
                    skipBytes(requestContent);
                    break;
                case AjpConstants.SC_A_SERVLET_PATH:
                    skipBytes(requestContent);
                    break;
                case AjpConstants.SC_A_REMOTE_USER:
                    if (tomcatAuthentication) {
                        // ignore server
                        skipBytes(requestContent);
                    } else {
                        getBytesToByteChunk(requestContent, req.getRemoteUser());
                    }
                    break;
                case AjpConstants.SC_A_AUTH_TYPE:
                    if (tomcatAuthentication) {
                        // ignore server
                        skipBytes(requestContent);
                    } else {
                        getBytesToByteChunk(requestContent, req.getAuthType());
                    }
                    break;

                case AjpConstants.SC_A_QUERY_STRING:
                    getBytesToByteChunk(requestContent, req.queryString());
                    break;

                case AjpConstants.SC_A_JVM_ROUTE:
                    getBytesToByteChunk(requestContent, req.instanceId());
                    break;

                case AjpConstants.SC_A_SSL_CERT:
                    req.setSecure(true);
                    // SSL certificate extraction is costly, initialize on demand
                    getBytesToByteChunk(requestContent, req.sslCert());
                    break;

                case AjpConstants.SC_A_SSL_CIPHER:
                    req.setSecure(true);
                    setStringAttributeValue(req, SSLSupport.CIPHER_SUITE_KEY, requestContent);
                    break;

                case AjpConstants.SC_A_SSL_SESSION:
                    req.setSecure(true);
                    setStringAttributeValue(req, SSLSupport.SESSION_ID_KEY, requestContent);
                    break;

                case AjpConstants.SC_A_SECRET:
                    final int secretLen = getShort(requestContent);
                    req.setSecret(getString(requestContent));
                    break;

                case AjpConstants.SC_A_STORED_METHOD:
                    getBytesToByteChunk(requestContent, req.method());
                    break;

                default:
                    break; // ignore, we don't know about it - backward compat
            }
        }
    }

    public static String getString(ByteBuffer buffer) {
        final int length = getShort(buffer);
        final String s = new String(buffer.array(), buffer.position(), length);
        // Don't forget to skip the terminating \0 (that's why "+ 1")
        advance(buffer, length + 1);

        return s;
    }

    private static void decodeHeaders(final Request req, final ByteBuffer buf) {
        // Decode headers
        final MimeHeaders headers = req.getMimeHeaders();

        final int hCount = getShort(buf);

        for (int i = 0; i < hCount; i++) {
            String hName;

            // Header names are encoded as either an integer code starting
            // with 0xA0, or as a normal string (in which case the first
            // two bytes are the length).
            int isc = getShort(buf);
            int hId = isc & 0xFF;

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

                advance(buf, -2);
                valueDC = headers.addValue(getString(buf));
            }

            getBytesToByteChunk(buf, valueDC);

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
    }

    private static void getBytesToByteChunk(final ByteBuffer buffer, final MessageBytes bytes) {
        final int length = getShort(buffer);
        if (length != 0xFFFF) {
            bytes.setBytes(buffer.array(), buffer.position(), length);
            // Don't forget to skip the terminating \0 (that's why "+ 1")
            advance(buffer, length + 1);
        }
    }

    private static void setStringAttribute(final AjpHttpRequest req, final ByteBuffer buffer) {
        req.setAttribute(getString(buffer), getString(buffer));
    }

    private static void advance(ByteBuffer buffer, int length) {
        buffer.position(buffer.position() + length);
    }

    private static void setStringAttributeValue(final AjpHttpRequest req, final String key, final ByteBuffer buffer) {
        req.setAttribute(key, getString(buffer));
        // Don't forget to skip the terminating \0 (that's why "+ 1")
        advance(buffer, 1);
    }

    public static ByteChunk encodeHeaders(AjpHttpResponse response) {
        try {
            ByteChunk headerBuffer = new ByteChunk(4096);

            headerBuffer.append(AjpConstants.JK_AJP13_SEND_HEADERS);
            putShort(headerBuffer, (short) response.getStatus());
            String message = null;
            if (response.isAllowCustomReasonPhrase()) {
                message = response.getMessage();
            }

            if (message == null) {
                message = HttpMessages.getMessage(response.getStatus());
            }

            putBytes(headerBuffer, message.getBytes());;

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

                final Enumeration<String> names = headers.names();
                while (names.hasMoreElements()) {
                    final String name = names.nextElement();
                    final Enumeration<String> value = headers.values(name);
                    while (value.hasMoreElements()) {
                        putBytes(headerBuffer, name.getBytes());
                        putBytes(headerBuffer, value.nextElement().getBytes());
                    }
                }
            }

            // Add Ajp message header
            ByteChunk encodedBuffer = new ByteChunk(4096);
            encodedBuffer.append((byte) 'A');
            encodedBuffer.append((byte) 'B');

            putShort(encodedBuffer, (short) headerBuffer.getLength());
            encodedBuffer.append(headerBuffer);

            return encodedBuffer;
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    /**
     * Parse host.
     */
    private static void parseHost(final MessageBytes bytes, final AjpHttpRequest request)
            throws CharConversionException {

        if (bytes == null) {
            // HTTP/1.0
            // Default is what the socket tells us. Overridden if a host is
            // found/parsed
            request.setServerPort(request.getLocalPort());
            request.serverName().setString(request.localName().getString());
            return;
        }

        final ByteBuffer valueB = bytes.getByteChunk().toByteBuffer();
        final int valueS = valueB.position();
        final int valueL = bytes.getLength();
        int colonPos = -1;

        final boolean ipv6 = valueB.get(valueS) == '[';
        boolean bracketClosed = false;
        for (int i = 0; i < valueL; i++) {
            final byte b = valueB.get(i + valueS);
            if (b == ']') {
                bracketClosed = true;
            } else if (b == ':') {
                if (!ipv6 || bracketClosed) {
                    colonPos = i;
                    break;
                }
            }
        }

        if (colonPos < 0) {
            request.setServerPort(request.isSecure() ? 443 : 80);
            request.serverName().setBytes(valueB.array(), valueS, valueL);
        } else {
            request.serverName().setBytes(valueB.array(), valueS, colonPos);

            int port = 0;
            int mult = 1;
            for (int i = valueL - 1; i > colonPos; i--) {
                int charValue = HexUtils.DEC[(int) valueB.get(i + valueS)];
                if (charValue == -1) {
                    // Invalid character
                    throw new CharConversionException("Invalid char in port: " + valueB.get(i + valueS));
                }
                port = port + charValue * mult;
                mult = 10 * mult;
            }
            request.setServerPort(port);
        }
    }

    private static void skipBytes(final ByteBuffer buffer) {
        if (getShort(buffer) != 0xFFFF) {
            // Don't forget to skip the terminating \0 (that's why "+ 1")
            advance(buffer, 1);
        }
    }

    static int getShort(final ByteBuffer buffer) {
        return buffer.getShort() & 0xFFFF;
    }

    public static void putShort(ByteChunk chunk, short value) throws IOException {
        chunk.append((byte) (value >> 8));
        chunk.append((byte) (value & 0xFF));
    }

    private static void putString(ByteChunk dstBuffer, String value) throws IOException {
        byte[] bytes = value.getBytes();
        putShort(dstBuffer, (short) bytes.length);
        dstBuffer.append(bytes, 0, bytes.length);
        dstBuffer.append((byte) 0);
    }

    private static void putBytes(ByteChunk dstBuffer, final ByteChunk chunk) throws IOException {
        if (!chunk.isNull()) {
            putBytes(dstBuffer, chunk.getBytes());
        }
    }

    private static void putBytes(ByteChunk dstBuffer, byte[] bytes) throws IOException {
        putShort(dstBuffer, (short) bytes.length);
        dstBuffer.append(bytes, 0, bytes.length);
        dstBuffer.append((byte) 0);
    }

    public static ByteBuffer createAjpPacket(final byte type, ByteBuffer src) {
        final int length = src.remaining();
        final ByteBuffer ajpHeader = ByteBuffer.allocate(5 + length);
        ajpHeader.put((byte) 'A');
        ajpHeader.put((byte) 'B');
        ajpHeader.putShort((short) (length + 1));
        ajpHeader.put(type);
        ajpHeader.put(src);
        ajpHeader.flip();
        return ajpHeader;
    }

    public static List<AjpResponse> parseResponse(ByteBuffer buffer) {
        List<AjpResponse> responses = new ArrayList<AjpResponse>();
        while (buffer.hasRemaining()) {
            final AjpResponse ajpResponse = new AjpResponse();
            final int position = buffer.position();
            final short magic = buffer.getShort();
            if (magic != 0x4142) {
                throw new RuntimeException("Invalid magic number: " + magic + " buffer: \n" + dumpByteTable(buffer));
            }
            final short packetSize = buffer.getShort();
            if (packetSize > AjpConstants.MAX_PACKET_SIZE - 2) {
                throw new RuntimeException("Packet size too large: " + packetSize);
            }
            int start = buffer.position();
            final byte type = buffer.get();
            ajpResponse.setType(type);
            byte[] body;
            switch (type) {
                case AjpConstants.JK_AJP13_SEND_HEADERS:
                    ajpResponse.setResponseCode(buffer.getShort());
                    ajpResponse.setResponseMessage(getString(buffer));
                    AjpHttpRequest request = new AjpHttpRequest();
                    request.getMimeHeaders().addValue("content-type");
                    decodeHeaders(request, buffer);
                    ajpResponse.setHeaders(request.getMimeHeaders());
                    break;
                case AjpConstants.JK_AJP13_SEND_BODY_CHUNK:
                    final short size = buffer.getShort();
                    body = new byte[size];
                    buffer.get(body);
                    ajpResponse.setBody(body);
                    buffer.get();  // consume terminating 0x00
                    break;
                case AjpConstants.JK_AJP13_END_RESPONSE:
                    body = new byte[1];
                    buffer.get(body);
                    ajpResponse.setBody(body);
                    break;
            }
            final int end = buffer.position();
            if (end - start != packetSize) {
                throw new RuntimeException(String.format("packet size mismatch: %s vs %s", end - start, packetSize));
            }
            responses.add(ajpResponse);
        }
        return responses;
    }

    public static byte[] toBytes(short size) {
        return new byte[]{(byte) (size >> 8), (byte) (size & 0xFF)};
    }

    public static String dumpByteTable(ByteBuffer buffer) {
        int pos = buffer.position();
        StringBuilder bytes = new StringBuilder();
        StringBuilder chars = new StringBuilder();
        StringBuilder table = new StringBuilder();
        int count = 0;
        while (buffer.remaining() > 0) {
            count++;
            byte cur = buffer.get();
            bytes.append(String.format("%02x ", cur));
            chars.append(printable(cur));
            if (count % 16 == 0) {
                table.append(String.format("%s   %s", bytes, chars).trim());
                table.append("\n");
                chars = new StringBuilder();
                bytes = new StringBuilder();
            } else if (count % 8 == 0) {
                table.append(String.format("%s   ", bytes));
                bytes = new StringBuilder();
            }
        }
        if (bytes.length() > 0) {
            final int i = 50 - count % 8;
            final String format = "%-" + i + "s   %s";
            System.out.println("format = " + format);
            table.append(String.format(format, bytes, chars).trim());
        }
        buffer.position(pos);

        return table.toString();
    }

    private static char printable(byte cur) {
        if ((cur & (byte) 0xa0) == 0xa0) {
            return '?';
        }
        return cur < 127 && cur > 31 || Character.isLetterOrDigit(cur) ? (char) cur : '.';
    }
}