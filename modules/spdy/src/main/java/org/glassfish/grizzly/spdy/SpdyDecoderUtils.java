/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved.
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

import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.Cacheable;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.ThreadCache;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.http.HttpHeader;
import org.glassfish.grizzly.http.HttpResponsePacket;
import org.glassfish.grizzly.http.Protocol;
import org.glassfish.grizzly.http.util.Ascii;
import org.glassfish.grizzly.http.util.BufferChunk;
import org.glassfish.grizzly.http.util.ByteChunk;
import org.glassfish.grizzly.http.util.DataChunk;
import org.glassfish.grizzly.http.util.Header;
import org.glassfish.grizzly.http.util.HttpCodecUtils;
import org.glassfish.grizzly.http.util.MimeHeaders;
import org.glassfish.grizzly.utils.Charsets;

/**
 * SpdyFrames -> HTTP Packet decoder utils.
 * 
 * @author Grizzly team
 */
class SpdyDecoderUtils {
    private final static Logger LOGGER = Grizzly.logger(SpdyDecoderUtils.class);

    static void processSynStreamHeadersArray(final SpdyRequest spdyRequest,
            final Buffer decoded) {
        
        final byte[] headersArray = decoded.array();
        int position = decoded.arrayOffset() + decoded.position();

        final int headersCount = getInt(headersArray, position);
        position += 4;

        for (int i = 0; i < headersCount; i++) {
            final boolean isServiceHeader = (headersArray[position + 4] == ':');

            if (isServiceHeader) {
                position = processServiceSynStreamHeader(spdyRequest,
                        headersArray, position);
            } else {
                position = processNormalHeader(spdyRequest,
                        headersArray, position);
            }
        }
    }

    static void processSynStreamHeadersBuffer(final SpdyRequest spdyRequest,
            final Buffer headersBuffer) {

        int position = headersBuffer.position();
        final int headersCount = headersBuffer.getInt(position);
        position += 4;

        for (int i = 0; i < headersCount; i++) {
            final boolean isServiceHeader = (headersBuffer.get(position + 4) == ':');

            if (isServiceHeader) {
                position = processServiceSynStreamHeader(spdyRequest,
                        headersBuffer, position);
            } else {
                position = processNormalHeader(spdyRequest,
                        headersBuffer, position);
            }
        }
    }
    
    static void processUSynStreamHeadersArray(final SpdyRequest spdyRequest,
            final Buffer decoded) {
        
        final byte[] headersArray = decoded.array();
        int position = decoded.arrayOffset() + decoded.position();

        final int headersCount = getInt(headersArray, position);
        position += 4;

        final SpdyResponse spdyResponse = (SpdyResponse) spdyRequest.getResponse();
        
        for (int i = 0; i < headersCount; i++) {
            final boolean isServiceHeader = (headersArray[position + 4] == ':');

            if (isServiceHeader) {
                position = processServiceUSynStreamHeader(spdyRequest,
                        headersArray, position);
            } else {
                position = processNormalHeader(spdyResponse,
                        headersArray, position);
            }
        }
    }

    static void processUSynStreamHeadersBuffer(final SpdyRequest spdyRequest,
            final Buffer headersBuffer) {

        int position = headersBuffer.position();
        final int headersCount = headersBuffer.getInt(position);
        position += 4;

        final SpdyResponse spdyResponse = (SpdyResponse) spdyRequest.getResponse();

        for (int i = 0; i < headersCount; i++) {
            final boolean isServiceHeader = (headersBuffer.get(position + 4) == ':');

            if (isServiceHeader) {
                position = processServiceUSynStreamHeader(spdyRequest,
                        headersBuffer, position);
            } else {
                position = processNormalHeader(spdyResponse,
                        headersBuffer, position);
            }
        }
    }

    private static int processServiceSynStreamHeader(final SpdyRequest spdyRequest,
                                                     final byte[] headersArray,
                                                     final int position) {

        final int nameSize = getInt(headersArray, position);
        final int valueSize = getInt(headersArray, position + nameSize + 4);

        final int nameStart = position + 5; // Skip headerNameSize and following ':'
        final int valueStart = position + nameSize + 8;
        final int valueEnd = valueStart + valueSize;

        switch (nameSize - 1) {
            case 4: {
                if (checkArraysContent(headersArray, nameStart,
                        Constants.HOST_HEADER_BYTES, 1)) {
                    spdyRequest.getHeaders().addValue(Header.Host)
                            .setBytes(headersArray, valueStart, valueEnd);

                    return valueEnd;
                } else if (checkArraysContent(headersArray, nameStart,
                        Constants.PATH_HEADER_BYTES, 1)) {

                    int questionIdx = -1;
                    for (int i = 0; i < valueSize; i++) {
                        if (headersArray[valueStart + i] == '?') {
                            questionIdx = i + valueStart;
                            break;
                        }
                    }

                    if (questionIdx == -1) {
                        spdyRequest.getRequestURIRef().init(headersArray, valueStart, valueEnd);
                    } else {
                        spdyRequest.getRequestURIRef().init(headersArray, valueStart, questionIdx);
                        if (questionIdx < valueEnd - 1) {
                            spdyRequest.getQueryStringDC()
                                    .setBytes(headersArray, questionIdx + 1, valueEnd);
                        }
                    }

                    return valueEnd;
                }

                break;
            } case 6: {
                if (checkArraysContent(headersArray, nameStart,
                        Constants.METHOD_HEADER_BYTES, 1)) {
                    spdyRequest.getMethodDC().setBytes(
                            headersArray, valueStart, valueEnd);

                    return valueEnd;
                } else if (checkArraysContent(headersArray, nameStart,
                        Constants.SCHEMA_HEADER_BYTES, 1)) {

                    spdyRequest.setSecure(valueSize == 5); // support http and https only
                    return valueEnd;
                }
                
                break;
            } case 7: {
                if (checkArraysContent(headersArray, nameStart,
                                Constants.VERSION_HEADER_BYTES, 1)) {
                    spdyRequest.getProtocolDC().setBytes(
                            headersArray, valueStart, valueEnd);

                    return valueEnd;
                }
            }
        }

        LOGGER.log(Level.FINE, "Skipping unknown service header[{0}={1}",
                new Object[]{new String(headersArray, nameStart - 1, nameSize, Charsets.ASCII_CHARSET),
                    new String(headersArray, valueStart, valueSize, Charsets.ASCII_CHARSET)});

        return valueEnd;
    }
    
    private static int processServiceSynStreamHeader(final SpdyRequest spdyRequest,
                                              final Buffer buffer, final int position) {

        final int nameSize = buffer.getInt(position);
        final int valueSize = buffer.getInt(position + nameSize + 4);

        final int nameStart = position + 5; // Skip headerNameSize and following ':'
        final int valueStart = position + nameSize + 8;
        final int valueEnd = valueStart + valueSize;

        switch (nameSize - 1) {
            case 4: {
                if (checkBufferContent(buffer, nameStart,
                        Constants.HOST_HEADER_BYTES, 1)) {
                    spdyRequest.getHeaders().addValue(Header.Host)
                            .setBuffer(buffer, valueStart, valueEnd);

                    return valueEnd;
                } else if (checkBufferContent(buffer, nameStart,
                        Constants.PATH_HEADER_BYTES, 1)) {

                    int questionIdx = -1;
                    for (int i = 0; i < valueSize; i++) {
                        if (buffer.get(valueStart + i) == '?') {
                            questionIdx = i + valueStart;
                            break;
                        }
                    }

                    if (questionIdx == -1) {
                        spdyRequest.getRequestURIRef().init(buffer, valueStart, valueEnd);
                    } else {
                        spdyRequest.getRequestURIRef().getOriginalRequestURIBC()
                                .setBuffer(buffer, valueStart, questionIdx);
                        if (questionIdx < valueEnd - 1) {
                            spdyRequest.getQueryStringDC()
                                    .setBuffer(buffer, questionIdx + 1, valueEnd);
                        }
                    }

                    return valueEnd;
                }

                break;
            }
            case 6: {
                if (checkBufferContent(buffer, nameStart,
                        Constants.METHOD_HEADER_BYTES, 1)) {
                    spdyRequest.getMethodDC().setBuffer(
                            buffer, valueStart, valueEnd);

                    return valueEnd;
                } else if (checkBufferContent(buffer, nameStart,
                        Constants.SCHEMA_HEADER_BYTES, 1)) {

                    spdyRequest.setSecure(valueSize == 5); // support http and https only
                    return valueEnd;
                }
                
                break;
            }
            case 7: {
                if (checkBufferContent(buffer, nameStart,
                        Constants.VERSION_HEADER_BYTES, 1)) {
                    spdyRequest.getProtocolDC().setBuffer(
                            buffer, valueStart, valueEnd);

                    return valueEnd;
                }
            }
        }

        LOGGER.log(Level.FINE, "Skipping unknown service header[{0}={1}",
                new Object[] {
                    buffer.toStringContent(Charsets.ASCII_CHARSET, nameStart - 1, nameSize),
                    buffer.toStringContent(Charsets.ASCII_CHARSET, valueStart, valueSize)});

        return valueEnd;
    }
    
    private static int processServiceUSynStreamHeader(final SpdyRequest spdyRequest,
            final byte[] headersArray, final int position) {

        final int nameSize = getInt(headersArray, position);
        final int valueSize = getInt(headersArray, position + nameSize + 4);

        final int nameStart = position + 5; // Skip headerNameSize and following ':'
        final int valueStart = position + nameSize + 8;
        final int valueEnd = valueStart + valueSize;

        switch (nameSize - 1) {
            case 4: {
                if (checkArraysContent(headersArray, nameStart,
                        Constants.HOST_HEADER_BYTES, 1)) {
                    spdyRequest.getHeaders().addValue(Header.Host)
                            .setBytes(headersArray, valueStart, valueEnd);

                    return valueEnd;
                } else if (checkArraysContent(headersArray, nameStart,
                        Constants.PATH_HEADER_BYTES, 1)) {

                    int questionIdx = -1;
                    for (int i = 0; i < valueSize; i++) {
                        if (headersArray[valueStart + i] == '?') {
                            questionIdx = i + valueStart;
                            break;
                        }
                    }

                    if (questionIdx == -1) {
                        spdyRequest.getRequestURIRef().init(headersArray, valueStart, valueEnd);
                    } else {
                        spdyRequest.getRequestURIRef().init(headersArray, valueStart, questionIdx);
                        if (questionIdx < valueEnd - 1) {
                            spdyRequest.getQueryStringDC()
                                    .setBytes(headersArray, questionIdx + 1, valueEnd);
                        }
                    }

                    return valueEnd;
                }

                break;
            } case 6: {
                if (checkArraysContent(headersArray, nameStart,
                        Constants.SCHEMA_HEADER_BYTES, 1)) {

                    spdyRequest.setSecure(valueSize == 5); // support http and https only
                    return valueEnd;
                } else if (checkArraysContent(headersArray, nameStart,
                        Constants.STATUS_HEADER_BYTES, 1)) { // support :status for unidirectional requests
                    if (valueEnd < 3) {
                        throw new IllegalStateException("Unknown status code: " +
                                new String(headersArray, valueStart, valueEnd - valueStart, Charsets.UTF8_CHARSET));
                    }

                    final HttpResponsePacket spdyResponse =
                            spdyRequest.getResponse();
                    
                    spdyResponse.setStatus(Ascii.parseInt(headersArray,
                                                          valueStart,
                                                          3));
                    
                    final int reasonPhraseIdx =
                            HttpCodecUtils.skipSpaces(headersArray,
                            valueStart + 3, valueEnd, valueEnd);
                    
                    if (reasonPhraseIdx != -1) {
                        int reasonPhraseEnd = skipLastSpaces(headersArray,
                                valueStart + 3, valueEnd) + 1;
                        if (reasonPhraseEnd == 0) {
                            reasonPhraseEnd = valueEnd;
                        }
                        
                        spdyResponse.getReasonPhraseRawDC().setBytes(
                                headersArray, reasonPhraseIdx, reasonPhraseEnd);
                    }
                    
                    return valueEnd;
                }
                
                break;
            } case 7: {
                if (checkArraysContent(headersArray, nameStart,
                                Constants.VERSION_HEADER_BYTES, 1)) {
                    spdyRequest.getProtocolDC().setBytes(
                            headersArray, valueStart, valueEnd);
                    spdyRequest.getResponse().getProtocolDC().setBytes(
                            headersArray, valueStart, valueEnd);
                    
                    return valueEnd;
                }
            }
        }

        LOGGER.log(Level.FINE, "Skipping unknown service header[{0}={1}",
                new Object[]{new String(headersArray, nameStart - 1, nameSize, Charsets.ASCII_CHARSET),
                    new String(headersArray, valueStart, valueSize, Charsets.ASCII_CHARSET)});

        return valueEnd;
    }
    
    private static int processServiceUSynStreamHeader(final SpdyRequest spdyRequest,
                                              final Buffer buffer, final int position) {

        final int nameSize = buffer.getInt(position);
        final int valueSize = buffer.getInt(position + nameSize + 4);

        final int nameStart = position + 5; // Skip headerNameSize and following ':'
        final int valueStart = position + nameSize + 8;
        final int valueEnd = valueStart + valueSize;

        switch (nameSize - 1) {
            case 4: {
                if (checkBufferContent(buffer, nameStart,
                        Constants.HOST_HEADER_BYTES, 1)) {
                    spdyRequest.getHeaders().addValue(Header.Host)
                            .setBuffer(buffer, valueStart, valueEnd);

                    return valueEnd;
                } else if (checkBufferContent(buffer, nameStart,
                        Constants.PATH_HEADER_BYTES, 1)) {

                    int questionIdx = -1;
                    for (int i = 0; i < valueSize; i++) {
                        if (buffer.get(valueStart + i) == '?') {
                            questionIdx = i + valueStart;
                            break;
                        }
                    }

                    if (questionIdx == -1) {
                        spdyRequest.getRequestURIRef().init(buffer, valueStart, valueEnd);
                    } else {
                        spdyRequest.getRequestURIRef().getOriginalRequestURIBC()
                                .setBuffer(buffer, valueStart, questionIdx);
                        if (questionIdx < valueEnd - 1) {
                            spdyRequest.getQueryStringDC()
                                    .setBuffer(buffer, questionIdx + 1, valueEnd);
                        }
                    }

                    return valueEnd;
                }

                break;
            }
            case 6: {
                if (checkBufferContent(buffer, nameStart,
                        Constants.SCHEMA_HEADER_BYTES, 1)) {

                    spdyRequest.setSecure(valueSize == 5); // support http and https only
                    return valueEnd;
                } else if (checkBufferContent(buffer, nameStart,
                        Constants.STATUS_HEADER_BYTES, 1)) { // support :status for unidirectional requests
                    if (valueEnd < 3) {
                        throw new IllegalStateException("Unknown status code: " +
                                buffer.toStringContent(Charsets.ASCII_CHARSET, valueEnd, (valueEnd - valueStart)));
                    }

                    final HttpResponsePacket spdyResponse =
                            spdyRequest.getResponse();
                    
                    spdyResponse.setStatus(
                            Ascii.parseInt(buffer, valueStart, 3));

                    final int reasonPhraseIdx =
                            HttpCodecUtils.skipSpaces(buffer, valueStart + 3, valueEnd);

                    if (reasonPhraseIdx != -1) {
                        int reasonPhraseEnd = skipLastSpaces(buffer,
                                valueStart + 3, valueEnd) + 1;
                        if (reasonPhraseEnd == 0) {
                            reasonPhraseEnd = valueEnd;
                        }

                        spdyResponse.getReasonPhraseRawDC().setBuffer(
                                buffer, reasonPhraseIdx, reasonPhraseEnd);
                    }

                    return valueEnd;
                }
                
                break;
            }
            case 7: {
                if (checkBufferContent(buffer, nameStart,
                        Constants.VERSION_HEADER_BYTES, 1)) {
                    spdyRequest.getProtocolDC().setBuffer(
                            buffer, valueStart, valueEnd);
                    spdyRequest.getResponse().getProtocolDC().setBuffer(
                            buffer, valueStart, valueEnd);
                    return valueEnd;
                }
            }
        }

        LOGGER.log(Level.FINE, "Skipping unknown service header[{0}={1}",
                new Object[] {
                    buffer.toStringContent(Charsets.ASCII_CHARSET, nameStart - 1, nameSize),
                    buffer.toStringContent(Charsets.ASCII_CHARSET, valueStart, valueSize)});

        return valueEnd;
    }
    
    private static int processServiceSynReplyHeader(final SpdyResponse spdyResponse,
                                                    final byte[] headersArray,
                                                    final int position,
                                                    final InitialLineParsingState state) {

        final int nameSize = getInt(headersArray, position);
        final int valueSize = getInt(headersArray, position + nameSize + 4);

        final int nameStart = position + 5; // Skip headerNameSize and following ':'
        final int valueStart = position + nameSize + 8;
        final int valueEnd = valueStart + valueSize;

        switch (nameSize - 1) {
            case 6: {
                if (checkArraysContent(headersArray, nameStart,
                        Constants.STATUS_HEADER_BYTES, 1)) {
                    if (valueEnd < 3) {
                        throw new IllegalStateException("Unknown status code: " +
                                new String(headersArray, valueStart, valueEnd - valueStart, Charsets.UTF8_CHARSET));
                    }

                    
                    spdyResponse.setStatus(Ascii.parseInt(headersArray,
                                                          valueStart,
                                                          3));
                    state.statusCodeParsed();

                    final int reasonPhraseIdx =
                            HttpCodecUtils.skipSpaces(headersArray,
                            valueStart + 3, valueEnd, valueEnd);
                    
                    if (reasonPhraseIdx != -1) {
                        int reasonPhraseEnd = skipLastSpaces(headersArray,
                                valueStart + 3, valueEnd) + 1;
                        if (reasonPhraseEnd == 0) {
                            reasonPhraseEnd = valueEnd;
                        }
                        
                        spdyResponse.getReasonPhraseRawDC().setBytes(
                                headersArray, reasonPhraseIdx, reasonPhraseEnd);
                    }
                    state.reasonPhraseParsed();
                    
                    return valueEnd;
                }

                break;
            }

            case 7: {
                if (checkArraysContent(headersArray, nameStart,
                        Constants.VERSION_HEADER_BYTES, 1)) {
                    spdyResponse.setProtocol(Protocol.valueOf(headersArray,
                            valueStart, valueEnd - valueStart));
                    state.protocolParsed();

                    return valueEnd;
                }
            }
        }

        LOGGER.log(Level.FINE, "Skipping unknown service header[{0}={1}",
                new Object[]{new String(headersArray, position, nameSize, Charsets.ASCII_CHARSET),
                    new String(headersArray, valueStart, valueSize, Charsets.ASCII_CHARSET)});

        return valueEnd;
    }

    private static int processServiceSynReplyHeader(final SpdyResponse spdyResponse,
                                                    final Buffer buffer,
                                                    int position,
                                                    final InitialLineParsingState state) {

        final int nameSize = buffer.getInt(position);
        final int valueSize = buffer.getInt(position + nameSize + 4);

        final int nameStart = position + 5; // Skip headerNameSize and following ':'
        final int valueStart = position + nameSize + 8;
        final int valueEnd = valueStart + valueSize;

        switch (nameSize - 1) {
            case 6: {
                if (checkBufferContent(buffer, nameStart,
                        Constants.STATUS_HEADER_BYTES, 1)) {
                    if (valueEnd < 3) {
                        throw new IllegalStateException("Unknown status code: " +
                                buffer.toStringContent(Charsets.ASCII_CHARSET, valueEnd, (valueEnd - valueStart)));
                    }

                    spdyResponse.setStatus(Ascii.parseInt(buffer, valueStart, 3));
                    state.statusCodeParsed();

                    final int reasonPhraseIdx =
                            HttpCodecUtils.skipSpaces(buffer, valueStart + 3, valueEnd);

                    if (reasonPhraseIdx != -1) {
                        int reasonPhraseEnd = skipLastSpaces(buffer,
                                valueStart + 3, valueEnd) + 1;
                        if (reasonPhraseEnd == 0) {
                            reasonPhraseEnd = valueEnd;
                        }

                        spdyResponse.getReasonPhraseRawDC().setBuffer(
                                buffer, reasonPhraseIdx, reasonPhraseEnd);
                    }
                    state.reasonPhraseParsed();

                    return valueEnd;
                }

                break;
            }

            case 7: {
                if (checkBufferContent(buffer, nameStart,
                        Constants.VERSION_HEADER_BYTES, 1)) {
                    spdyResponse.setProtocol(Protocol.valueOf(buffer,
                            valueStart, valueEnd - valueStart));
                    state.protocolParsed();
                    return valueEnd;
                }
            }
        }

        LOGGER.log(Level.FINE, "Skipping unknown service header[{0}={1}",
                new Object[]{buffer.toStringContent(Charsets.ASCII_CHARSET, position, nameSize),
                             buffer.toStringContent(Charsets.ASCII_CHARSET, valueStart, valueSize)});

        return valueEnd;
    }
    
    static void processSynReplyHeadersArray(final SpdyResponse spdyResponse,
                                            final Buffer decoded,
                                            final FilterChainContext ctx,
                                            final SpdyHandlerFilter handlerFilter) {
        final byte[] headersArray = decoded.array();
        int position = decoded.arrayOffset() + decoded.position();

        final int headersCount = getInt(headersArray, position);
        position += 4;

        InitialLineParsingState state = InitialLineParsingState.create();

        for (int i = 0; i < headersCount && !spdyResponse.isSkipRemainder(); i++) {
            final boolean isServiceHeader = (headersArray[position + 4] == ':');

            if (isServiceHeader) {
                position = processServiceSynReplyHeader(spdyResponse, headersArray, position, state);
                if (state != null && state.isParseComplete()) {
                    handlerFilter.onInitialLineParsed(spdyResponse, ctx);
                    state.recycle();
                    state = null;
                }
            } else {
                position = processNormalHeader(spdyResponse, headersArray, position);
            }
        }
        handlerFilter.onHttpHeadersParsed(spdyResponse, ctx);
    }

    static void processSynReplyHeadersBuffer(final SpdyResponse spdyResponse,
                                             final Buffer decoded,
                                             final FilterChainContext ctx,
                                             final SpdyHandlerFilter handlerFilter) {
        int position = decoded.position();

        final int headersCount = decoded.getInt(position);
        position += 4;

        InitialLineParsingState state = InitialLineParsingState.create();

        for (int i = 0; i < headersCount && !spdyResponse.isSkipRemainder(); i++) {
            final boolean isServiceHeader = (decoded.get(position + 4) == ':');

            if (isServiceHeader) {
                position = processServiceSynReplyHeader(spdyResponse, decoded, position, state);
                if (state != null && state.isParseComplete()) {
                    handlerFilter.onInitialLineParsed(spdyResponse, ctx);
                    state = null;
                }
            } else {
                position = processNormalHeader(spdyResponse, decoded, position);
            }
        }
        handlerFilter.onHttpHeadersParsed(spdyResponse, ctx);
    }
    
    
    private static int processNormalHeader(final HttpHeader httpHeader,
            final byte[] headersArray, int position) {

        final MimeHeaders mimeHeaders = httpHeader.getHeaders();

        final int headerNameSize = getInt(headersArray, position);
        position += 4;

        final int headerNamePosition = position;
        
        final DataChunk valueChunk =
                mimeHeaders.addValue(headersArray, headerNamePosition, headerNameSize);

        position += headerNameSize;

        final int headerValueSize = getInt(headersArray, position);
        position += 4;

        for (int i = 0; i < headerValueSize; i++) {
            final byte b = headersArray[position + i];
            if (b == 0) {
                headersArray[position + i] = ',';
            }
        }

        final int end = position + headerValueSize;

        valueChunk.setBytes(headersArray, position, end);
        
        finalizeKnownHeader(httpHeader, headersArray,
                headerNamePosition, headerNameSize,
                position, end - position);
        
        return end;
    }

    private static int processNormalHeader(final HttpHeader httpHeader,
                                           final Buffer headerBuffer,
                                           int position) {

        final MimeHeaders mimeHeaders = httpHeader.getHeaders();

        final int headerNameSize = headerBuffer.getInt(position);
        position += 4;

        final int headerNamePosition = position;

        final DataChunk valueChunk =
                mimeHeaders.addValue(headerBuffer, headerNamePosition, headerNameSize);

        position += headerNameSize;

        final int headerValueSize = headerBuffer.getInt(position);
        position += 4;

        for (int i = 0; i < headerValueSize; i++) {
            final byte b = headerBuffer.get(position + i);
            if (b == 0) {
                headerBuffer.put(position + i, (byte) ',');
            }
        }

        final int end = position + headerValueSize;

        valueChunk.setBuffer(headerBuffer, position, end);

        finalizeKnownHeader(httpHeader, headerBuffer,
                headerNamePosition, headerNameSize,
                position, end - position);

        return end;
    }
    
    private static void finalizeKnownHeader(final HttpHeader httpHeader,
            final byte[] array,
            final int nameStart, final int nameLen,
            final int valueStart, final int valueLen) {
        
        final int nameEnd = nameStart + nameLen;
        
        if (nameLen == Header.ContentLength.getLowerCaseBytes().length) {
            if (httpHeader.getContentLength() == -1
                    && ByteChunk.equalsIgnoreCaseLowerCase(array, nameStart, nameEnd,
                    Header.ContentLength.getLowerCaseBytes())) {
                httpHeader.setContentLengthLong(Ascii.parseLong(
                        array, valueStart, valueLen));
            }
        } else if (nameLen == Header.Upgrade.getLowerCaseBytes().length) {
            if (ByteChunk.equalsIgnoreCaseLowerCase(array, nameStart, nameEnd,
                    Header.Upgrade.getLowerCaseBytes())) {
                httpHeader.getUpgradeDC().setBytes(array, valueStart,
                        valueStart + valueLen);
            }
        } else if (nameLen == Header.Expect.getLowerCaseBytes().length) {
            if (ByteChunk.equalsIgnoreCaseLowerCase(array, nameStart, nameEnd,
                    Header.Expect.getLowerCaseBytes())) {
                ((SpdyRequest) httpHeader).requiresAcknowledgement(true);
            }
        }
    }

    private static void finalizeKnownHeader(final HttpHeader httpHeader,
                                            final Buffer buffer,
                                            final int nameStart, final int nameLen,
                                            final int valueStart, final int valueLen) {

        final int nameEnd = nameStart + nameLen;

        if (nameLen == Header.ContentLength.getLowerCaseBytes().length) {
            if (httpHeader.getContentLength() == -1
                    && BufferChunk.equalsIgnoreCaseLowerCase(buffer, nameStart, nameEnd,
                    Header.ContentLength.getLowerCaseBytes())) {
                httpHeader.setContentLengthLong(Ascii.parseLong(
                        buffer, valueStart, valueLen));
            }
        } else if (nameLen == Header.Upgrade.getLowerCaseBytes().length) {
            if (BufferChunk.equalsIgnoreCaseLowerCase(buffer,
                                                      nameStart,
                                                      nameEnd,
                                                      Header.Upgrade.getLowerCaseBytes())) {
                httpHeader.getUpgradeDC().setBuffer(buffer, valueStart,
                        valueStart + valueLen);
            }
        } else if (nameLen == Header.Expect.getLowerCaseBytes().length) {
            if (BufferChunk.equalsIgnoreCaseLowerCase(buffer,
                                                      nameStart,
                                                      nameEnd,
                                                      Header.Expect.getLowerCaseBytes())) {
                ((SpdyRequest) httpHeader).requiresAcknowledgement(true);
            }
        }
    }
    
    private static int getInt(final byte[] array, final int position) {
        return ((array[position] & 0xFF) << 24) +
                ((array[position + 1] & 0xFF) << 16) +
                ((array[position + 2] & 0xFF) << 8) +
                (array[position + 3] & 0xFF);
    }

    private static boolean checkArraysContent(final byte[] b1, final int pos1,
            final byte[] control, final int pos2) {
        for (int i = 0, len = control.length - pos2; i < len; i++) {
            if (b1[pos1 + i] != control[pos2 + i]) {
                return false;
            }
        }

        return true;
    }

    private static boolean checkBufferContent(final Buffer toTest, final int pos,
            final byte[] control, final int pos2) {
        for (int i = 0, len = control.length - pos2; i < len; i++) {
            if (toTest.get(pos + i) != control[pos2 + i]) {
                return false;
            }
        }

        return true;
    }
    
    private static int skipLastSpaces(byte[] array, int start, int end) {
        for (int i = end - 1; i >= start; i--) {
            if (HttpCodecUtils.isNotSpaceAndTab(array[i])) {
                return i;
            }
        }
        
        return -1;
    }

    private static int skipLastSpaces(Buffer buffer, int start, int end) {
        for (int i = end - 1; i >= start; i--) {
            if (HttpCodecUtils.isNotSpaceAndTab(buffer.get(i))) {
                return i;
            }
        }

        return -1;
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


        void protocolParsed() {
            parseState |= (1 << 2);
        }

        void reasonPhraseParsed() {
            parseState |= (1 << 1);
        }

        void statusCodeParsed() {
            parseState |= 1;
        }

        boolean isParseComplete() {
            return parseState == 0x7;
        }


        // ---------------------------------------------- Methods from Cacheable


        @Override
        public void recycle() {
            parseState = 0;
            ThreadCache.putToCache(CACHE_IDX, this);
        }
    }

}
