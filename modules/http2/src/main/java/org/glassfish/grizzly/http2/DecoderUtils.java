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
import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.grizzly.Cacheable;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.ThreadCache;
import org.glassfish.grizzly.http.HttpHeader;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.http.HttpResponsePacket;
import org.glassfish.grizzly.http.Protocol;
import org.glassfish.grizzly.http.util.DataChunk;
import org.glassfish.grizzly.http.util.Header;
import org.glassfish.grizzly.http.util.MimeHeaders;
import org.glassfish.grizzly.http2.compression.HeaderListener;

/**
 * Http2Frames -> HTTP Packet decoder utils.
 * 
 * @author Grizzly team
 */
class DecoderUtils {
    private final static Logger LOGGER = Grizzly.logger(DecoderUtils.class);

    static void decodeRequestHeaders(final Http2Connection http2Connection,
            final HttpRequestPacket request) throws IOException {
        
        http2Connection.getHeadersDecoder().decode(new HeaderListener() {

            @Override
            public void onDecodedHeader(String name, String value) {
                if (name.charAt(0) == ':') {
                    processServiceRequestHeader(request, name, value);
                } else {
                    processNormalHeader(request, name, value);
                }
            }
            
//            public void addHeader(final byte[] name, final byte[] value,
//                    final boolean isSensitive) {
//                if (name[0] == ':') {
//                    processServiceRequestHeader(request, name, value);
//                } else {
//                    processNormalHeader(request, name, value);
//                }
//            }
//
//            public void emitHeader(byte[] name, byte[] value, boolean isSensitive) {
//                addHeader(name, value, isSensitive);
//            }
//
//            public void emitHeader(byte[] name, byte[] value) {
//                addHeader(name, value, false);
//            }
        });
        
        request.setProtocol(Protocol.HTTP_2_0);
    }

    static void decodeResponseHeaders(final Http2Connection http2Connection,
            final HttpResponsePacket response) throws IOException {
        
        http2Connection.getHeadersDecoder().decode(new HeaderListener() {

            @Override
            public void onDecodedHeader(String name, String value) {
                if (name.charAt(0) == ':') {
                    processServiceResponseHeader(response, name, value);
                } else {
                    processNormalHeader(response, name, value);
                }
            }
            
//            @Override
//            public void addHeader(final byte[] name, final byte[] value,
//                    final boolean isSensitive) {
//                if (name[0] == ':') {
//                    processServiceResponseHeader(response, name, value);
//                } else {
//                    processNormalHeader(response, name, value);
//                }
//            }
//            
//            public void emitHeader(byte[] name, byte[] value, boolean isSensitive) {
//                addHeader(name, value, isSensitive);
//            }
//            
//            public void emitHeader(byte[] name, byte[] value) {
//                addHeader(name, value, false);
//            }
        });
        
        response.setProtocol(Protocol.HTTP_2_0);
    }

    private static void processServiceRequestHeader(
            final HttpRequestPacket request,
            final String name, final String value) {

        final int valueLen = value.length();
        
        switch (name) {
            case Constants.PATH_HEADER: {
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
            case Constants.METHOD_HEADER: {
                request.getMethodDC().setString(value);
                return;
            }
            case Constants.SCHEMA_HEADER: {
                request.setSecure(valueLen == 5); // support http and https only
                return;
            }
            case Constants.AUTHORITY_HEADER: {
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

        final int valueLen = value.length();
        switch (name) {
            case Constants.STATUS_HEADER: {
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

        valueChunk.setString(value.replace('\u0000', ','));
        
        finalizeKnownHeader(httpHeader, name, value);
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
                return;
            }
        }
    }    
    
//    private static void processServiceRequestHeader(
//            final HttpRequestPacket request,
//            final byte[] name, final byte[] value) {
//
//        final int valueLen = value.length;
//        
//        switch (name.length - 1) {
//            case 4: {
//                if (checkArraysContent(name, 1,
//                        Constants.PATH_HEADER_BYTES, 1)) {
//
//                    int questionIdx = -1;
//                    
//                    for (int i = 0; i < valueLen; i++) {
//                        if (value[i] == '?') {
//                            questionIdx = i;
//                            break;
//                        }
//                    }
//
//                    if (questionIdx == -1) {
//                        request.getRequestURIRef().init(value, 0, valueLen);
//                    } else {
//                        request.getRequestURIRef().init(value, 0, questionIdx);
//                        if (questionIdx < valueLen - 1) {
//                            request.getQueryStringDC()
//                                    .setBytes(value, questionIdx + 1, valueLen);
//                        }
//                    }
//
//                    return;
//                }
//
//                break;
//            } case 6: {
//                if (checkArraysContent(name, 1,
//                        Constants.METHOD_HEADER_BYTES, 1)) {
//                    request.getMethodDC().setBytes(value);
//
//                    return;
//                } else if (checkArraysContent(name, 1,
//                        Constants.SCHEMA_HEADER_BYTES, 1)) {
//
//                    request.setSecure(valueLen == 5); // support http and https only
//                    return;
//                }
//                
//                break;
//            } case 9: {
//                if (checkArraysContent(name, 1,
//                        Constants.AUTHORITY_HEADER_BYTES, 1)) {
//                    request.getHeaders().addValue(Header.Host)
//                            .setBytes(value, 0, valueLen);
//
//                    return;
//                }
//            }
//        }
//
//        LOGGER.log(Level.FINE, "Skipping unknown service header[{0}={1}",
//                new Object[]{new String(name, Charsets.ASCII_CHARSET),
//                    new String(value, Charsets.ASCII_CHARSET)});
//    }    
//    
//    private static void processServiceResponseHeader(
//            final HttpResponsePacket response,
//            final byte[] name, final byte[] value) {
//
//        switch (name.length - 1) {
//            case 6: {
//                if (checkArraysContent(name, 1,
//                        Constants.STATUS_HEADER_BYTES, 1)) {
//                    if ((value.length) != 3) {
//                        throw new IllegalStateException("Unexpected status code: " +
//                                new String(value, Charsets.UTF8_CHARSET));
//                    }
//
//                    
//                    response.setStatus(Ascii.parseInt(value, 0, 3));
//                }
//
//                break;
//            }
//        }
//
//        LOGGER.log(Level.FINE, "Skipping unknown service header[{0}={1}",
//                new Object[]{new String(name, Charsets.ASCII_CHARSET),
//                    new String(value, Charsets.ASCII_CHARSET)});
//    }
//    
//    private static void processNormalHeader(final HttpHeader httpHeader,
//            final byte[] header, final byte[] value) {
//
//        final MimeHeaders mimeHeaders = httpHeader.getHeaders();
//
//        final DataChunk valueChunk =
//                mimeHeaders.addValue(header, 0, header.length);
//
//        final int valueLen = value.length;
//        
//        for (int i = 0; i < valueLen; i++) {
//            final byte b = value[i];
//            if (b == 0) {
//                value[i] = ',';
//            }
//        }
//
//        valueChunk.setBytes(value, 0, valueLen);
//        
//        finalizeKnownHeader(httpHeader, header, value);
//    }
//
//    private static void finalizeKnownHeader(final HttpHeader httpHeader,
//            final byte[] name, final byte[] value) {
//        
//        final int nameLen = name.length;
//        
//        if (nameLen == Header.ContentLength.getLowerCaseBytes().length) {
//            if (httpHeader.getContentLength() == -1
//                    && ByteChunk.equalsIgnoreCaseLowerCase(name, 0, nameLen,
//                    Header.ContentLength.getLowerCaseBytes())) {
//                httpHeader.setContentLengthLong(Ascii.parseLong(
//                        value, 0, value.length));
//            }
//        } else if (nameLen == Header.Upgrade.getLowerCaseBytes().length) {
//            if (ByteChunk.equalsIgnoreCaseLowerCase(name, 0, nameLen,
//                    Header.Upgrade.getLowerCaseBytes())) {
//                httpHeader.getUpgradeDC().setBytes(value);
//            }
//        } else if (nameLen == Header.Expect.getLowerCaseBytes().length) {
//            if (ByteChunk.equalsIgnoreCaseLowerCase(name, 0, nameLen,
//                    Header.Expect.getLowerCaseBytes())) {
//                ((Http2Request) httpHeader).requiresAcknowledgement(true);
//            }
//        }
//    }

    private static boolean checkArraysContent(final byte[] b1, final int pos1,
            final byte[] control, final int pos2) {
        for (int i = 0, len = control.length - pos2; i < len; i++) {
            if (b1[pos1 + i] != control[pos2 + i]) {
                return false;
            }
        }

        return true;
    }

    // ---------------------------------------------------------- Nested Classes


    private static final class InitialLineParsingState implements Cacheable {

        private static final ThreadCache.CachedTypeIndex<InitialLineParsingState> CACHE_IDX =
                ThreadCache.obtainIndex(InitialLineParsingState.class, 8);

        private byte parseState;


        // --------------------------------------------- Package Private Methods


        static InitialLineParsingState create() {
            InitialLineParsingState state = ThreadCache.getFromCache(CACHE_IDX);
            if (state == null) {
                state = new InitialLineParsingState();
            }
            return state;
        }


        void statusCodeParsed() {
            parseState |= 1;
        }

        boolean isParseComplete() {
            return parseState == 0x1;
        }


        // ---------------------------------------------- Methods from Cacheable


        @Override
        public void recycle() {
            parseState = 0;
            ThreadCache.putToCache(CACHE_IDX, this);
        }
    }

}
