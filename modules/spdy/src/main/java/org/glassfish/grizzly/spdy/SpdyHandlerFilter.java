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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.locks.Lock;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.CompletionHandler;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Event;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.WriteResult;
import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.FilterChain;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.http.FixedLengthTransferEncoding;
import org.glassfish.grizzly.http.HttpBaseFilter;
import org.glassfish.grizzly.http.HttpContent;
import org.glassfish.grizzly.http.HttpContext;
import org.glassfish.grizzly.http.HttpHeader;
import org.glassfish.grizzly.http.HttpPacket;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.http.HttpResponsePacket;
import org.glassfish.grizzly.http.HttpServerFilter;
import org.glassfish.grizzly.http.Method;
import org.glassfish.grizzly.http.ProcessingState;
import org.glassfish.grizzly.http.Protocol;
import org.glassfish.grizzly.http.TransferEncoding;
import org.glassfish.grizzly.http.util.Ascii;
import org.glassfish.grizzly.http.util.ByteChunk;
import org.glassfish.grizzly.http.util.DataChunk;
import org.glassfish.grizzly.http.util.Header;
import org.glassfish.grizzly.http.util.HttpCodecUtils;
import org.glassfish.grizzly.http.util.HttpStatus;
import org.glassfish.grizzly.http.util.HttpUtils;
import org.glassfish.grizzly.http.util.MimeHeaders;
import org.glassfish.grizzly.npn.NextProtoNegSupport;
import org.glassfish.grizzly.spdy.compression.SpdyInflaterOutputStream;
import org.glassfish.grizzly.ssl.SSLFilter;
import org.glassfish.grizzly.utils.Charsets;

import static org.glassfish.grizzly.spdy.Constants.*;
/**
 *
 * @author oleksiys
 */
public class SpdyHandlerFilter extends HttpBaseFilter {
    private final static Logger LOGGER = Grizzly.logger(SpdyHandlerFilter.class);

    private static final ClientNpnNegotiator CLIENT_NPN_NEGOTIATOR =
            new ClientNpnNegotiator();

    private static final TransferEncoding FIXED_LENGTH_ENCODING = new FixedLengthTransferEncoding();
    
    private final ExecutorService threadPool;

    public SpdyHandlerFilter(ExecutorService threadPool) {
        this.threadPool = threadPool;
    }


    @SuppressWarnings("unchecked")
    @Override
    public NextAction handleRead(final FilterChainContext ctx)
            throws IOException {
        
        final SpdySession spdySession = checkSpdySession(ctx, true);
        
        final Object message = ctx.getMessage();

        if (message == null) {  // If message == null - it means it's initiated by blocking ctx.read() call
            // we have to check SpdyStream associated input queue if there are any data we can return
            // otherwise block until input data is available
            final SpdyStream spdyStream =
                    (SpdyStream) HttpContext.get(ctx).getContextStorage();
            final Buffer data = spdyStream.pollInputData();
            
            final HttpContent httpContent = HttpContent.builder(spdyStream.getInputHttpHeader())
                    .content(data)
                    .last(spdyStream.isInputTerminated())
                    .build();
            
            ctx.setMessage(httpContent);
            
            return ctx.getInvokeAction();
        }
        
        if (HttpPacket.isHttp(message)) {
            return ctx.getInvokeAction();
        }
        
        if (message instanceof Buffer) {
            final Buffer frameBuffer = (Buffer) message;
            processInFrame(spdySession, ctx, frameBuffer);
        } else {
            final ArrayList<Buffer> framesList = (ArrayList<Buffer>) message;
            final int sz = framesList.size();
            for (int i = 0; i < sz; i++) {
                processInFrame(spdySession, ctx, framesList.get(i));
            }
        }

        final List<SpdyStream> streamsToFlushInput =
                spdySession.streamsToFlushInput;
        for (int i = 0; i < streamsToFlushInput.size(); i++) {
            streamsToFlushInput.get(i).flushInputData();
        }
        streamsToFlushInput.clear();
        
        return ctx.getStopAction();
    }

    private void processInFrame(final SpdySession spdySession,
            final FilterChainContext context, final Buffer frame) {

        final long header = frame.getLong();

        final int first32 = (int) (header >>> 32);
        final int second32 = (int) (header & 0xFFFFFFFFL);

        final boolean isControlMessage = (first32 & 0x80000000) != 0;
        if (isControlMessage) {
            final int version = (first32 >>> 16) & 0x7FFF;
            final int type = first32 & 0xFFFF;
            final int flags = second32 >>> 24;
            final int length = second32 & 0xFFFFFF;
            processControlFrame(spdySession, context, frame, version, type,
                    flags, length);
        } else {
            final int streamId = first32 & 0x7FFFFFFF;
            final int flags = second32 >>> 24;
            final int length = second32 & 0xFFFFFF;
            final SpdyStream spdyStream = spdySession.getStream(streamId);
            
            processDataFrame(spdyStream, frame, flags, length);
        }
    }

    private void processControlFrame(final SpdySession spdySession,
            final FilterChainContext context, final Buffer frame,
            final int version, final int type, final int flags, final int length) {

        switch(type) {
            case SYN_STREAM_FRAME: {
                processSynStream(spdySession, context, frame, version, type, flags, length);
                break;
            }
            case SETTINGS_FRAME: {
                processSettings(spdySession, context, frame, version, type, flags, length);
                break;
            }
            case SYN_REPLY_FRAME: {
                processSynReply(spdySession, context, frame, version, type, flags, length);
                break;
            }
            case PING_FRAME: {
                processPing(spdySession, context, frame, version, type, flags, length);
                break;
            }
            case RST_STREAM_FRAME: {
                processRstStream(spdySession, context, frame, version, type, flags, length);
                break;
            }
            case GOAWAY_FRAME: {
                processGoAwayFrame(spdySession, context, frame, version, type, flags, length);
                break;
            }
            case HEADERS_FRAME:
            case CREDENTIAL_FRAME:
            case WINDOW_UPDATE_FRAME: {
                processWindowUpdateFrame(spdySession,
                                         context,
                                         frame,
                                         version,
                                         type,
                                         flags,
                                         length);
                break;
            }

            default: {
                LOGGER.log(Level.WARNING, "Unknown control-frame [version={0} type={1} flags={2} length={3}]",
                        new Object[]{version, type, flags, length});
            }
        }
    }

    private void processWindowUpdateFrame(final SpdySession spdySession,
                                          final FilterChainContext context,
                                          final Buffer frame,
                                          final int version,
                                          final int type,
                                          final int flags,
                                          final int length) {
        final int streamId = frame.getInt() & 0x7FFFFFF;
        final int delta = frame.getInt() & 0x7FFFFFF;
        if (LOGGER.isLoggable(Level.INFO)) { // TODO Change level
            final StringBuilder sb = new StringBuilder(64);
            sb.append("\n{WINDOW_UPDATE : streamId=")
                    .append(streamId)
                    .append(" delta-window-size=")
                    .append(delta)
                    .append("}\n");
            LOGGER.info(sb.toString()); // TODO: CHANGE LEVEL
        }
        final SpdyStream stream = spdySession.getStream(streamId);
        
        if (stream != null) {
            stream.onPeerWindowUpdate(delta);
        } else {
            if (LOGGER.isLoggable(Level.INFO)) { // TODO Change level
                final StringBuilder sb = new StringBuilder(64);
                sb.append("\nStream id=")
                        .append(streamId)
                        .append(" was not found. Ignoring the message");
                LOGGER.info(sb.toString()); // TODO: CHANGE LEVEL
            }
        }
    }

    private void processGoAwayFrame(final SpdySession spdySession,
                                      final FilterChainContext context,
                                      final Buffer frame,
                                      final int version,
                                      final int type,
                                      final int flags,
                                      final int length) {
        
        final int lastGoodStreamId = frame.getInt() & 0x7FFFFFFF;
        
        final int statusCode;
        if (frame.hasRemaining()) {
            statusCode = frame.getInt();
        } else { // most probably firefox bug: for SPDY/3 it doesn't send the statusCode
            statusCode = 0;
        }
        
        if (LOGGER.isLoggable(Level.INFO)) { // TODO Change level
            final StringBuilder sb = new StringBuilder(64);
            sb.append("\n{GOAWAY : lastGoodStreamId=")
                    .append(lastGoodStreamId)
                    .append(" statusCode=")
                    .append(statusCode)
                    .append("}\n");
            LOGGER.info(sb.toString()); // TODO: CHANGE LEVEL
        }
        
        frame.dispose();
        
        spdySession.setGoAway(lastGoodStreamId);
    }
    
    private void processSettings(final SpdySession spdySession,
                                      final FilterChainContext context,
                                      final Buffer frame,
                                      final int version,
                                      final int type,
                                      final int flags,
                                      final int length) {

        final boolean log = LOGGER.isLoggable(Level.INFO); // TODO Change level

        final int numEntries = frame.getInt();
        StringBuilder sb = null;
        if (log) {
            sb = new StringBuilder(64);
            sb.append("\n{SETTINGS : count=").append(numEntries);
        }
        for (int i = 0; i < numEntries; i++) {
            final int eHeader = frame.getInt();
            final int eFlags = eHeader >>> 24;
            final int eId = eHeader & 0xFFFFFF;
            final int value = frame.getInt();
            if (log) {
                sb.append("\n    {SETTING : flags=")
                        .append(eFlags)
                        .append(" id=")
                        .append(OPTION_TEXT[eId])
                        .append(" value=")
                        .append(value)
                        .append('}');
            }
            switch (eId) {
                case UPLOAD_BANDWIDTH:
                    break;
                case DOWNLOAD_BANDWIDTH:
                    break;
                case ROUND_TRIP_TIME:
                    break;
                case MAX_CONCURRENT_STREAMS:
                    break;
                case CURRENT_CWND:
                    break;
                case DOWNLOAD_RETRANS_RATE:
                    break;
                case INITIAL_WINDOW_SIZE:
                    spdySession.setPeerInitialWindowSize(value);
                    break;
                case CLIENT_CERTIFICATE_VECTOR_SIZE:
                    break;
                default:
                    LOGGER.log(Level.WARNING, "Setting specified but not handled: {0}", eId);
            }

        }
        if (log) {
            sb.append("\n}\n");
            LOGGER.info(sb.toString()); // TODO: CHANGE LEVEL
        }
    }

    private void processPing(final SpdySession spdySession,
                                      final FilterChainContext context,
                                      final Buffer frame,
                                      final int version,
                                      final int type,
                                      final int flags,
                                      final int length) {
        final int pingId = frame.getInt();
        
        if (LOGGER.isLoggable(Level.INFO)) { // TODO Change level
            final StringBuilder sb = new StringBuilder(32);
            sb.append("\n{PING : id=")
                    .append((long) pingId)
                    .append("}\n");
            LOGGER.info(sb.toString()); // TODO: CHANGE LEVEL
        }
        
        frame.flip();
        
        // Send the same ping message back
        spdySession.writeDownStream(frame);
    }

    private void processRstStream(final SpdySession spdySession,
                                      final FilterChainContext context,
                                      final Buffer frame,
                                      final int version,
                                      final int type,
                                      final int flags,
                                      final int length) {
        final int streamId = frame.getInt() & 0x7fffffff;
        final int statusCode = frame.getInt();
        
        if (LOGGER.isLoggable(Level.INFO)) { // TODO Change level
            final StringBuilder sb = new StringBuilder(32);
            sb.append("\n{RST_STREAM : streamId=")
                    .append((long) streamId)
                    .append(" statusCode=")
                    .append((long) statusCode)
                    .append("}\n");
            LOGGER.info(sb.toString()); // TODO: CHANGE LEVEL
        }
        
//        @TODO: implement RST_STREAM
    }
    
    private void processSynStream(final SpdySession spdySession,
            final FilterChainContext context,
            final Buffer frame, final int version, final int type,
            final int flags, final int length) {

        final int streamId = frame.getInt() & 0x7FFFFFFF;
        final int associatedToStreamId = frame.getInt() & 0x7FFFFFFF;
        int tmpInt = frame.getShort() & 0xFFFF;
        final int priority = tmpInt >> 13;
        final int slot = tmpInt & 0xFF;

        if (version != SPDY_VERSION) {
            sendRstStream(context, streamId, UNSUPPORTED_VERSION, null);
            return;
        }

        if (LOGGER.isLoggable(Level.INFO)) {  // TODO: Change level
            StringBuilder sb = new StringBuilder();
            if (flags == 0) {
                sb.append("NONE");
            } else {
                if ((flags & 0x01) == SYN_STREAM_FLAG_FIN) {
                    sb.append(SYN_STREAM_FLAG_TEXT[1]);
                }
                if ((flags & 0x02) == SYN_STREAM_FLAG_UNIDIRECTIONAL) {
                    if (sb.length() == 0) {
                        sb.append(',');
                    }
                    sb.append(SYN_STREAM_FLAG_TEXT[2]);
                }
            }
            LOGGER.log(Level.INFO, "'{'SYN_STREAM : flags={0} streamID={1} associatedToStreamId={2} priority={3} slot={4}'}'",
                    new Object[]{sb.toString(), streamId, associatedToStreamId, priority, slot});
        }
        
        final SpdyRequest spdyRequest = SpdyRequest.create();
        spdyRequest.setConnection(context.getConnection());
        final SpdyStream spdyStream = spdySession.acceptStream(spdyRequest,
                streamId, associatedToStreamId, priority, slot);
        if (spdyStream == null) { // GOAWAY has been sent, so ignoring this request
            spdyRequest.recycle();
            frame.dispose();
            return;
        }
        
        Buffer payload = frame;
        if (frame.isComposite()) {
            payload = frame.slice();
        }

        final Buffer decoded;

        try {
            final SpdyInflaterOutputStream inflaterOutputStream = spdySession.getInflaterOutputStream();
            inflaterOutputStream.write(payload);
            decoded = inflaterOutputStream.checkpoint();
        } catch (IOException dfe) {
            sendRstStream(context, streamId, PROTOCOL_ERROR, null);
            return;
        } finally {
            frame.dispose();
        }

        assert decoded.hasArray();

        final byte[] headersArray = decoded.array();
        int position = decoded.arrayOffset() + decoded.position();

        final int headersCount = getInt(headersArray, position);
        position += 4;

        for (int i = 0; i < headersCount; i++) {
            final boolean isServiceHeader = (headersArray[position + 4] == ':');

            if (isServiceHeader) {
                position = processServiceSynStreamHeader(spdyRequest, headersArray, position);
            } else {
                position = processNormalHeader(spdyRequest,
                        headersArray, position);
            }
        }
        
        prepareIncomingRequest(spdyRequest);
        if ((flags & SYN_STREAM_FLAG_FIN) != 0) {
            spdyRequest.setExpectContent(false);
        }
        
        final boolean isExpectContent = spdyRequest.isExpectContent();
        if (!isExpectContent) {
            spdyStream.closeInput();
        }
        
        threadPool.execute(new Runnable() {
            @Override
            public void run() {
                spdySession.sendMessageUpstream(spdyStream,
                        HttpContent.builder(spdyRequest)
                        .last(!isExpectContent)
                        .build());
            }
        });
    }

    private int processServiceSynStreamHeader(final SpdyRequest spdyRequest,
            final byte[] headersArray, final int position) {

        final int nameSize = getInt(headersArray, position);
        final int valueSize = getInt(headersArray, position + nameSize + 4);

        final int nameStart = position + 5; // Skip headerNameSize and following ':'
        final int valueStart = position + nameSize + 8;
        final int valueEnd = valueStart + valueSize;

        switch (nameSize - 1) {
            case 4: {
                if (checkArraysContent(headersArray, nameStart,
                        Constants.HOST_HEADER_BYTES)) {
                    spdyRequest.getHeaders().addValue(Header.Host)
                            .setBytes(headersArray, valueStart, valueEnd);

                    return valueEnd;
                } else if (checkArraysContent(headersArray, nameStart,
                        Constants.PATH_HEADER_BYTES)) {

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
                        spdyRequest.getRequestURIRef().getOriginalRequestURIBC()
                                .setBytes(headersArray, valueStart, questionIdx);
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
                        Constants.METHOD_HEADER_BYTES)) {
                    spdyRequest.getMethodDC().setBytes(
                            headersArray, valueStart, valueEnd);

                    return valueEnd;
                } else if (checkArraysContent(headersArray, nameStart,
                        Constants.SCHEMA_HEADER_BYTES)) {

                    spdyRequest.setSecure(valueSize == 5); // support http and https only
                    return valueEnd;
                }
            } case 7: {
                if (checkArraysContent(headersArray, nameStart,
                                Constants.VERSION_HEADER_BYTES)) {
                    spdyRequest.getProtocolDC().setBytes(
                            headersArray, valueStart, valueEnd);

                    return valueEnd;
                }
            }
        }

        LOGGER.log(Level.FINE, "Skipping unknown service header[{0}={1}",
                new Object[]{new String(headersArray, position, nameSize, Charsets.ASCII_CHARSET),
                    new String(headersArray, valueStart, valueSize, Charsets.ASCII_CHARSET)});

        return valueEnd;
    }
    
    private void processSynReply(final SpdySession spdySession,
            final FilterChainContext context,
            final Buffer frame, final int version, final int type,
            final int flags, final int length) {

        final int streamId = frame.getInt() & 0x7FFFFFFF;

        if (version != SPDY_VERSION) {
            sendRstStream(context, streamId, UNSUPPORTED_VERSION, null);
            return;
        }

        if (LOGGER.isLoggable(Level.INFO)) {  // TODO: Change level
            StringBuilder sb = new StringBuilder();
            if (flags == 0) {
                sb.append("NONE");
            } else {
                if ((flags & 0x01) == SYN_STREAM_FLAG_FIN) {
                    sb.append(SYN_STREAM_FLAG_TEXT[1]);
                }
                if ((flags & 0x02) == SYN_STREAM_FLAG_UNIDIRECTIONAL) {
                    if (sb.length() == 0) {
                        sb.append(',');
                    }
                    sb.append(SYN_STREAM_FLAG_TEXT[2]);
                }
            }
            LOGGER.log(Level.INFO, "'{'SYN_REPLY : flags={0} streamID={1}'}'",
                    new Object[]{sb.toString(), streamId});
        }
        
        final SpdyStream spdyStream = spdySession.getStream(streamId);
        
        if (spdyStream == null) { // Stream doesn't exist
            frame.dispose();
            return;
        }
        
        final HttpRequestPacket spdyRequest = spdyStream.getSpdyRequest();
        
        final SpdyResponse spdyResponse;
        
        final HttpResponsePacket response = spdyRequest.getResponse();
        if (response == null) {
            spdyResponse = SpdyResponse.create();
            spdyResponse.setRequest(spdyRequest);
        } else if (response instanceof SpdyResponse) {
            spdyResponse = (SpdyResponse) response;
        } else {
            throw new IllegalStateException("Unexpected response type: " + response.getClass());
        }
        
        final boolean isFin = (flags & SYN_STREAM_FLAG_FIN) != 0;
        if (isFin) {
            spdyResponse.setExpectContent(false);
            spdyStream.closeInput();
        }
        
        Buffer payload = frame;
        if (frame.isComposite()) {
            payload = frame.slice();
        }

        final Buffer decoded;

        try {
            final SpdyInflaterOutputStream inflaterOutputStream = spdySession.getInflaterOutputStream();
            inflaterOutputStream.write(payload);
            decoded = inflaterOutputStream.checkpoint();
        } catch (IOException dfe) {
            sendRstStream(context, streamId, PROTOCOL_ERROR, null);
            return;
        } finally {
            frame.dispose();
        }

        assert decoded.hasArray();

        final byte[] headersArray = decoded.array();
        int position = decoded.arrayOffset() + decoded.position();

        final int headersCount = getInt(headersArray, position);
        position += 4;

        for (int i = 0; i < headersCount; i++) {
            final boolean isServiceHeader = (headersArray[position + 4] == ':');

            if (isServiceHeader) {
                position = processServiceSynReplyHeader(spdyResponse, headersArray, position);
            } else {
                position = processNormalHeader(spdyResponse, headersArray, position);
            }
        }
        
        bind(spdyRequest, spdyResponse);
        
        threadPool.execute(new Runnable() {
            @Override
            public void run() {
                spdySession.sendMessageUpstream(spdyStream,
                        HttpContent.builder(spdyResponse)
                        .last(isFin)
                        .build());
            }
        });
    }
    
    private void processDataFrame(final SpdyStream spdyStream,
            final Buffer frame, final int flags,
            final int length) {
        
        final boolean isFinFrame = (flags & SYN_STREAM_FLAG_FIN) != 0;

        if (LOGGER.isLoggable(Level.INFO)) {  // TODO: Change level
            LOGGER.log(Level.INFO, "'{'DATA: flags={0} streamID={1} len={2}'}'",
                    new Object[]{isFinFrame ? "FIN" : "NONE",
                        spdyStream.getStreamId(), frame.remaining()});
        }
        
        spdyStream.offerInputData(frame, isFinFrame);
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public NextAction handleWrite(final FilterChainContext ctx) throws IOException {
        final Object message = ctx.getMessage();
        
        final SpdySession spdySession = checkSpdySession(ctx, false);

        if (spdySession != null &&
                HttpPacket.isHttp(message)) {
            
            // Get HttpPacket
            final HttpPacket input = (HttpPacket) ctx.getMessage();
            final HttpHeader httpHeader = input.getHttpHeader();

            if (!httpHeader.isCommitted()) {
                if (httpHeader.isRequest()) {
                    prepareOutgoingRequest((HttpRequestPacket) httpHeader);
                } else {
                    prepareOutgoingResponse((HttpResponsePacket) httpHeader);
                }
            }
            
            final boolean isNewStream = httpHeader.isRequest() &&
                    !httpHeader.isCommitted();
            
            final Lock newStreamLock = spdySession.getNewClientStreamLock();
            
            if (isNewStream) {
                newStreamLock.lock();
            }
            
            try {
                SpdyStream spdyStream = SpdyStream.getSpdyStream(httpHeader);
                
                if (isNewStream && spdyStream == null) {
                    spdyStream = spdySession.openStream(
                            (HttpRequestPacket) httpHeader,
                            spdySession.getNextLocalStreamId(),
                            0, 0, 0);
                }

                assert spdyStream != null;

                spdyStream.writeDownStream(input,
                        ctx.getTransportContext().getCompletionHandler());
                
                return ctx.getStopAction();
            } finally {
                if (isNewStream) {
                    newStreamLock.unlock();
                }
            }
        }

        return ctx.getInvokeAction();
    }

    @Override
    public NextAction handleConnect(final FilterChainContext ctx) throws IOException {
        final Connection connection = ctx.getConnection();
        final SpdySession spdySession = new SpdySession(connection, false);
        SpdySession.bind(connection, spdySession);

        final FilterChain filterChain = connection.getFilterChain();
        final int idx = filterChain.indexOfType(SSLFilter.class);
        if (idx != -1) { // use TLS NPN
            final SSLFilter sslFilter = (SSLFilter) filterChain.get(idx);
            NextProtoNegSupport.getInstance().configure(sslFilter);
            NextProtoNegSupport.getInstance().setClientSideNegotiator(
                    connection, CLIENT_NPN_NEGOTIATOR);

            sslFilter.handshake(connection, null);
        }
        
        return ctx.getInvokeAction();
    }
    
    @Override
    public NextAction handleEvent(FilterChainContext ctx, Event event) throws IOException {
        if (event.type() == HttpServerFilter.RESPONSE_COMPLETE_EVENT.type()) {
            final HttpContext httpContext = HttpContext.get(ctx);
            final SpdyStream spdyStream = (SpdyStream) httpContext.getContextStorage();
            spdyStream.terminateInput();
            spdyStream.closeOutput();
            
            return ctx.getStopAction();
        }/* else if (event.type() == InputBuffer.REREGISTER_FOR_READ_EVENT.type()) {
            ctx.fork(ctx.getStopAction());
            return ctx.getStopAction();
        }*/

        return ctx.getInvokeAction();
    }

    private static int processServiceSynReplyHeader(final SpdyResponse spdyResponse,
            final byte[] headersArray, final int position) {

        final int nameSize = getInt(headersArray, position);
        final int valueSize = getInt(headersArray, position + nameSize + 4);

        final int nameStart = position + 5; // Skip headerNameSize and following ':'
        final int valueStart = position + nameSize + 8;
        final int valueEnd = valueStart + valueSize;

        switch (nameSize - 1) {
            case 6: {
                if (checkArraysContent(headersArray, nameStart,
                        Constants.STATUS_HEADER_BYTES)) {
                    if (valueEnd < 3) {
                        throw new IllegalStateException("Unknown status code: " +
                                new String(headersArray, valueStart, valueEnd - valueStart));
                    }

                    
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
                        
                        spdyResponse.getReasonPhraseDC().setBytes(
                                headersArray, reasonPhraseIdx, reasonPhraseEnd);
                    }
                    
                    return valueEnd;
                }

                break;
            }

            case 7: {
                if (checkArraysContent(headersArray, nameStart,
                        Constants.VERSION_HEADER_BYTES)) {
                    spdyResponse.setProtocol(Protocol.valueOf(headersArray,
                            valueStart, valueEnd - valueStart));

                    return valueEnd;
                }
            }
        }

        LOGGER.log(Level.FINE, "Skipping unknown service header[{0}={1}",
                new Object[]{new String(headersArray, position, nameSize, Charsets.ASCII_CHARSET),
                    new String(headersArray, valueStart, valueSize, Charsets.ASCII_CHARSET)});

        return valueEnd;
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
    
    private void prepareIncomingRequest(final SpdyRequest request) {

        final ProcessingState state = request.getProcessingState();
        final HttpResponsePacket response = request.getResponse();

        final Method method = request.getMethod();

        if (Method.GET.equals(method)
                || Method.HEAD.equals(method)
                || (!Method.CONNECT.equals(method)
                        && request.getContentLength() == 0)) {
            request.setExpectContent(false);
        }

        try {
            request.getProtocol();
        } catch (IllegalStateException e) {
            state.setError(true);
            // Send 505; Unsupported HTTP version
            HttpStatus.HTTP_VERSION_NOT_SUPPORTED_505.setValues(response);
            request.setProtocol(Protocol.HTTP_1_1);
            
            return;
        }

        final MimeHeaders headers = request.getHeaders();
        
        final DataChunk hostDC = headers.getValue(Header.Host);

        // Check host header
        if (hostDC == null) {
            state.setError(true);
            return;
        }

        HttpCodecUtils.parseHost(hostDC, request, response, state);

        if (request.serverName().getLength() == 0) {
            state.setError(true);
            return;
        }
    }
    
    private void prepareOutgoingRequest(final HttpRequestPacket request) {
        String contentType = request.getContentType();
        if (contentType != null) {
            request.getHeaders().setValue(Header.ContentType).setString(contentType);
        }
        
        if (request.getContentLength() != -1) {
            // FixedLengthTransferEncoding will set proper Content-Length header
            FIXED_LENGTH_ENCODING.prepareSerialize(null, request, null);
        }
    }    

    private void prepareOutgoingResponse(final HttpResponsePacket response) {
        response.setProtocol(Protocol.HTTP_1_1);
    }    

    private static int getInt(final byte[] array, final int position) {
        return ((array[position] & 0xFF) << 24) +
                ((array[position + 1] & 0xFF) << 16) +
                ((array[position + 2] & 0xFF) << 8) +
                (array[position + 3] & 0xFF);
    }

    private static boolean checkArraysContent(final byte[] b1, final int pos,
            final byte[] b2) {
        for (int i = 0; i < b2.length; i++) {
            if (b1[pos + i] != b2[i]) {
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
    
    private static void sendRstStream(final FilterChainContext ctx,
            final int streamId, final int statusCode,
            final CompletionHandler<WriteResult> completionHandler) {

        final Buffer rstStreamFrame = ctx.getMemoryManager().allocate(16);
        rstStreamFrame.putInt(0x80000000 | (SPDY_VERSION << 16) | RST_STREAM_FRAME); // "C", version, RST_STREAM_FRAME
        rstStreamFrame.putInt(8); // Flags, Length
        rstStreamFrame.putInt(streamId & 0x7FFFFFFF); // Stream-ID
        rstStreamFrame.putInt(statusCode); // Status code
        rstStreamFrame.flip();

        ctx.write(rstStreamFrame, completionHandler);
    }

    private SpdySession checkSpdySession(final FilterChainContext context,
            final boolean isUpStream) {
        final Connection connection = context.getConnection();
        
        SpdySession spdySession = SpdySession.get(connection);
        if (spdySession == null) {
            spdySession = new SpdySession(connection);
            SpdySession.bind(connection, spdySession);
        }
        
        spdySession.initCommunication(context, isUpStream);

        return spdySession;
    }

    private static class ClientNpnNegotiator
            implements NextProtoNegSupport.ClientSideNegotiator {
        
        private static final String SPDY3_PROTOCOL = "spdy/3";

        @Override
        public boolean wantNegotiate(final Connection connection) {
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.log(Level.FINE, "NPN wantNegotiate. Connection={0}",
                        new Object[]{connection});
            }
            return true;
        }

        @Override
        public String selectProtocol(final Connection connection,
                final List<String> protocols) {
            
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.log(Level.FINE, "NPN selectProtocol. Connection={0}, protocols={1}",
                        new Object[]{connection, protocols});
            }
            
            if (protocols.indexOf(SPDY3_PROTOCOL) != -1) {
                return SPDY3_PROTOCOL;
            }
            
            return "";
        }

        @Override
        public void onNoDeal(Connection connection) {
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.log(Level.FINE, "NPN onNoDeal. Connection={0}",
                        new Object[]{connection});
            }
        }
    }    
}
