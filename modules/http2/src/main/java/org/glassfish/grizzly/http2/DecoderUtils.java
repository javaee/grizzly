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
import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.http.HttpHeader;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.http.HttpResponsePacket;
import org.glassfish.grizzly.http.Protocol;
import org.glassfish.grizzly.http.util.DataChunk;
import org.glassfish.grizzly.http.util.Header;
import org.glassfish.grizzly.http.util.MimeHeaders;
import org.glassfish.grizzly.http2.hpack.DecodingCallback;

/**
 * Http2Frames -> HTTP Packet decoder utils.
 * 
 * @author Grizzly team
 */
class DecoderUtils extends EncoderDecoderUtilsBase {
    private final static Logger LOGGER = Grizzly.logger(DecoderUtils.class);

    private static final String COOKIE_SEP = "; ";
    private static final String HEADER_SEP = ", ";
    private static final String WIRE_DELIM = "\u0000";

    private static final String INVALID_CHARACTER_MESSAGE =
            "Invalid character 0x%02x at index '%s' found in header %s [%s: %s]";

    @SuppressWarnings("DuplicateThrows")
    static void decodeRequestHeaders(final Http2Connection http2Connection,
                                     final HttpRequestPacket request)
            throws InvalidCharacterException, IOException {

        try {
            http2Connection.getHeadersDecoder().decode(new DecodingCallback() {

                @Override
                public void onDecoded(CharSequence name, CharSequence value) {
                    if (name.charAt(0) == ':') {
                        processServiceRequestHeader(request, name.toString(), value.toString());
                    } else {
                        processNormalHeader(request, name.toString(), value.toString());
                    }
                }

            });
        } catch (RuntimeException re) {
            throw new InvalidCharacterException(re);
        } finally {
            request.setProtocol(Protocol.HTTP_2_0);
            request.getResponse().setProtocol(Protocol.HTTP_2_0);
        }
    }

    @SuppressWarnings("DuplicateThrows")
    static void decodeResponseHeaders(final Http2Connection http2Connection,
                                      final HttpResponsePacket response)
            throws InvalidCharacterException, IOException {

        try {
            http2Connection.getHeadersDecoder().decode(new DecodingCallback() {

                @Override
                public void onDecoded(final CharSequence name, final CharSequence value) {
                    if (name.charAt(0) == ':') {
                        processServiceResponseHeader(response, name.toString(), value.toString());
                    } else {
                        processNormalHeader(response, name.toString(), value.toString());
                    }
                }

            });
        } catch (RuntimeException re) {
            throw new InvalidCharacterException(re);
        } finally {
            response.setProtocol(Protocol.HTTP_2_0);
            response.getRequest().setProtocol(Protocol.HTTP_2_0);
        }

    }

    @SuppressWarnings("DuplicateThrows")
    static void decodeTrailerHeaders(final Http2Connection http2Connection,
                                     final HttpHeader header)
            throws InvalidCharacterException, IOException {
        try {
            final MimeHeaders headers = header.getHeaders();
            http2Connection.getHeadersDecoder().decode(new DecodingCallback() {

                @Override
                public void onDecoded(final CharSequence name, final CharSequence value) {
                    // TODO trailer validation
                    headers.addValue(name.toString()).setString(value.toString());
                }

            });
        } catch (RuntimeException re) {
            throw new InvalidCharacterException(re);
        }
    }

    private static void processServiceRequestHeader(
            final HttpRequestPacket request,
            final String name, final String value) {

        final int valueLen = value.length();
        
        switch (name) {
            case PATH_HEADER: {
                int questionIdx = value.indexOf('?');

                if (questionIdx == -1) {
                    request.getRequestURIRef().init(value);
                } else {
                    request.getRequestURIRef().init(value.substring(0, questionIdx));
                    if (questionIdx < valueLen - 1) {
                        request.getQueryStringDC().setString(value.substring(questionIdx + 1));
                    }
                }
                
                return;
            }
            case METHOD_HEADER: {
                request.getMethodDC().setString(value);
                return;
            }
            case SCHEMA_HEADER: {
                request.setSecure(valueLen == 5); // support http and https only
                return;
            }
            case AUTHORITY_HEADER: {
                request.getHeaders().addValue(Header.Host)
                        .setString(value);
                return;
            }
        }
        
        LOGGER.log(Level.FINE, "Skipping unknown service header[{0}={1}",
                new Object[]{name, value});
    }    
    
    private static void processServiceResponseHeader(
            final HttpResponsePacket response,
            final String name, final String value) {
        validateHeaderCharacters(name, value);
        final int valueLen = value.length();
        switch (name) {
            case STATUS_HEADER: {
                if ((valueLen) != 3) {
                    throw new IllegalStateException("Unexpected status code: " + value);
                }
                
                response.setStatus(Integer.parseInt(value));
            }
        }
        
        LOGGER.log(Level.FINE, "Skipping unknown service header[{0}={1}",
                new Object[]{name, value});
    }
    
    private static void processNormalHeader(final HttpHeader httpHeader,
            final String name, final String value) {

        final MimeHeaders mimeHeaders = httpHeader.getHeaders();

        final DataChunk valueChunk =
                mimeHeaders.addValue(name);

        valueChunk.setString(value.replace(WIRE_DELIM, getDelimiter(name)));
        validateHeaderCharacters(name, valueChunk.toString());
        finalizeKnownHeader(httpHeader, name, value);
    }

    private static String getDelimiter(final String name) {
        return ((Header.Cookie.getLowerCase().equals(name) || Header.SetCookie.getLowerCase().equals(name))
                ? COOKIE_SEP
                : HEADER_SEP);
    }

    private static void finalizeKnownHeader(final HttpHeader httpHeader,
            final String name, final String value) {
        
        switch (name) {
            case "content-length": {
                httpHeader.setContentLengthLong(Long.parseLong(value));
                return;
            }
            
            case "upgrade": {
                httpHeader.getUpgradeDC().setString(value);
                return;
            }
            
            case "expect": {
                ((Http2Request) httpHeader).requiresAcknowledgement(true);
            }
        }
    }

    private static void validateHeaderCharacters(final CharSequence name, final CharSequence value) {
        assert (name != null);
        assert (value != null);
        int idx = ensureRange(name);
        if (idx != -1) {
            final String msg = String.format(INVALID_CHARACTER_MESSAGE, (int) name.charAt(idx), idx, "name", name, value);
            throw new RuntimeException(msg);
        }
        idx = ensureRange(value);
        if (idx != -1) {
            final String msg = String.format(INVALID_CHARACTER_MESSAGE, (int) name.charAt(idx), idx, "value", name, value);
            throw new RuntimeException(msg);
        }
    }

    private static int ensureRange(final CharSequence cs) {
        for (int i = 0, len = cs.length(); i < len; i++) {
            final char c = cs.charAt(i);
            if (c < 0x20 || c > 0xFF) {
                return i;
            }
        }
        return -1;
    }

}
