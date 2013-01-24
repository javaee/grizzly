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
import org.glassfish.grizzly.http.util.BufferChunk;
import org.glassfish.grizzly.http.util.ByteChunk;
import org.glassfish.grizzly.http.util.DataChunk;
import org.glassfish.grizzly.http.util.Header;
import org.glassfish.grizzly.http.util.HttpCodecUtils;
import org.glassfish.grizzly.http.util.HttpStatus;
import org.glassfish.grizzly.http.util.MimeHeaders;
import org.glassfish.grizzly.npn.NextProtoNegSupport;
import org.glassfish.grizzly.spdy.compression.SpdyInflaterOutputStream;
import org.glassfish.grizzly.spdy.frames.CredentialFrame;
import org.glassfish.grizzly.spdy.frames.DataFrame;
import org.glassfish.grizzly.spdy.frames.GoAwayFrame;
import org.glassfish.grizzly.spdy.frames.HeadersFrame;
import org.glassfish.grizzly.spdy.frames.PingFrame;
import org.glassfish.grizzly.spdy.frames.RstStreamFrame;
import org.glassfish.grizzly.spdy.frames.SettingsFrame;
import org.glassfish.grizzly.spdy.frames.SpdyFrame;
import org.glassfish.grizzly.spdy.frames.SynReplyFrame;
import org.glassfish.grizzly.spdy.frames.SynStreamFrame;
import org.glassfish.grizzly.spdy.frames.WindowUpdateFrame;
import org.glassfish.grizzly.ssl.SSLFilter;
import org.glassfish.grizzly.utils.Charsets;

import static org.glassfish.grizzly.spdy.Constants.*;
import static org.glassfish.grizzly.spdy.frames.SettingsFrame.SETTINGS_INITIAL_WINDOW_SIZE;
import static org.glassfish.grizzly.spdy.frames.SettingsFrame.SETTINGS_MAX_CONCURRENT_STREAMS;
import static org.glassfish.grizzly.spdy.frames.SettingsFrame.SettingsFrameBuilder;

/**
 *
 * @author oleksiys
 */
public class SpdyHandlerFilter extends HttpBaseFilter {

    public static final int DEFAULT_MAX_CONCURRENT_STREAMS = 50;
    public static final int DEFAULT_INITIAL_WINDOW_SIZE = 64 * 1024;
    private final static Logger LOGGER = Grizzly.logger(SpdyHandlerFilter.class);

    private static final ClientNpnNegotiator CLIENT_NPN_NEGOTIATOR =
            new ClientNpnNegotiator();

    private static final TransferEncoding FIXED_LENGTH_ENCODING = new FixedLengthTransferEncoding();

    private final SpdyMode spdyMode;
    
    private final ExecutorService threadPool;

    // TODO: In the future, we may wish to allow this to be adjusted at runtime...
    private final int maxConcurrentStreams;
    private final int initialWindowSize;

    public SpdyHandlerFilter(final SpdyMode spdyMode,
            final ExecutorService threadPool) {
        this(spdyMode, threadPool, DEFAULT_MAX_CONCURRENT_STREAMS, DEFAULT_INITIAL_WINDOW_SIZE);
    }

    public SpdyHandlerFilter(final SpdyMode spdyMode,
                            final ExecutorService threadPool,
                            final int maxConcurrentStreams,
                            final int initialWindowSize) {
        this.spdyMode = spdyMode;
        this.threadPool = threadPool;
        this.maxConcurrentStreams = maxConcurrentStreams;
        this.initialWindowSize = initialWindowSize;
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
            final HttpContent httpContent = spdyStream.pollInputData();
            
            ctx.setMessage(httpContent);
            
            return ctx.getInvokeAction();
        }
        
        if (HttpPacket.isHttp(message)) {
            return ctx.getInvokeAction();
        }
        
        if (message instanceof SpdyFrame) {
            final SpdyFrame frame = (SpdyFrame) message;
            processInFrame(spdySession, ctx, frame);
        } else {
            final ArrayList<SpdyFrame> framesList = (ArrayList<SpdyFrame>) message;
            final int sz = framesList.size();
            try {
                for (int i = 0; i < sz; i++) {
                    processInFrame(spdySession, ctx, framesList.get(i));
                }
            } finally {
                // Don't forget to clean framesList, because it will be reused
                framesList.clear();
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
                                final FilterChainContext context,
                                final SpdyFrame frame) {

        if (frame.getHeader().isControl()) {
            processControlFrame(spdySession, context, frame);
        } else {
            processDataFrame(spdySession.getStream(frame.getHeader().getStreamId()), frame);
        }

    }

    private void processControlFrame(final SpdySession spdySession,
                                     final FilterChainContext context,
                                     final SpdyFrame frame) {

        switch(frame.getHeader().getType()) {
            case SynStreamFrame.TYPE: {
                processSynStream(spdySession, context, frame);
                break;
            }
            case SettingsFrame.TYPE: {
                processSettings(spdySession, context, frame);
                break;
            }
            case SynReplyFrame.TYPE: {
                processSynReply(spdySession, context, frame);
                break;
            }
            case PingFrame.TYPE: {
                processPing(spdySession, context, frame);
                break;
            }
            case RstStreamFrame.TYPE: {
                processRstStream(spdySession, context, frame);
                break;
            }
            case GoAwayFrame.TYPE: {
                processGoAwayFrame(spdySession, context, frame);
                break;
            }
            case WindowUpdateFrame.TYPE: {
                processWindowUpdateFrame(spdySession, context, frame);
                break;
            }
            case HeadersFrame.TYPE:
            case CredentialFrame.TYPE:
            default: {
                LOGGER.log(Level.WARNING, "Unknown or unhandled control-frame [version={0} type={1} flags={2} length={3}]",
                        new Object[]{frame.getHeader().getVersion(),
                                     frame.getHeader().getType(),
                                     frame.getHeader().getFlags(),
                                     frame.getHeader().getLength()});
            }
        }
    }

    private void processWindowUpdateFrame(final SpdySession spdySession,
                                          final FilterChainContext context,
                                          final SpdyFrame frame) {
        WindowUpdateFrame updateFrame = (WindowUpdateFrame) frame;
        final int streamId = updateFrame.getStreamId();
        final int delta = updateFrame.getDelta();

        final SpdyStream stream = spdySession.getStream(streamId);
        
        if (stream != null) {
            stream.onPeerWindowUpdate(delta);
        } else {
            if (LOGGER.isLoggable(Level.WARNING)) {
                final StringBuilder sb = new StringBuilder(64);
                sb.append("\nStream id=")
                        .append(streamId)
                        .append(" was not found. Ignoring the message");
                LOGGER.warning(sb.toString());
            }
        }
    }

    private void processGoAwayFrame(final SpdySession spdySession,
                                      final FilterChainContext context,
                                      final SpdyFrame frame) {

        GoAwayFrame goAwayFrame = (GoAwayFrame) frame;
        spdySession.setGoAway(goAwayFrame.getLastGoodStreamId());
    }
    
    private void processSettings(final SpdySession spdySession,
                                      final FilterChainContext context,
                                      final SpdyFrame frame) {

        SettingsFrame settingsFrame = (SettingsFrame) frame;
        int numberOfSettings = settingsFrame.getNumberOfSettings();
        if (numberOfSettings > 0) {
            final byte setSettings = settingsFrame.getSetSettings();
            for (int i = 0; i < SettingsFrame.MAX_DEFINED_SETTINGS && numberOfSettings != 0; i++) {
                if ((setSettings & (1 << i)) != 0) {
                    switch (i) {
                        case SettingsFrame.SETTINGS_UPLOAD_BANDWIDTH:
                            break;
                        case SettingsFrame.SETTINGS_DOWNLOAD_BANDWIDTH:
                            break;
                        case SettingsFrame.SETTINGS_ROUND_TRIP_TIME:
                            break;
                        case SettingsFrame.SETTINGS_MAX_CONCURRENT_STREAMS:
                            break;
                        case SettingsFrame.SETTINGS_CURRENT_CWND:
                            break;
                        case SettingsFrame.SETTINGS_DOWNLOAD_RETRANS_RATE:
                            break;
                        case SettingsFrame.SETTINGS_INITIAL_WINDOW_SIZE:
                            spdySession.setPeerInitialWindowSize(settingsFrame.getSetting(i));
                            break;
                        case SettingsFrame.SETTINGS_CLIENT_CERTIFICATE_VECTOR_SIZE:
                            break;
                    }
                    numberOfSettings--;
                }
            }
        }
    }

    private void processPing(final SpdySession spdySession,
                             final FilterChainContext context,
                             final SpdyFrame frame) {
        PingFrame pingFrame = (PingFrame) frame;

        // Send the same ping message back
        pingFrame.reset();
        spdySession.writeDownStream(pingFrame);
    }

    private void processRstStream(final SpdySession spdySession,
                                  final FilterChainContext context,
                                  final SpdyFrame frame) {

//        @TODO: implement RST_STREAM
    }
    
    private void processSynStream(final SpdySession spdySession,
                                  final FilterChainContext context,
                                  final SpdyFrame frame) {

        SynStreamFrame synStreamFrame = (SynStreamFrame) frame;
        final int streamId = synStreamFrame.getStreamId();
        final int associatedToStreamId = synStreamFrame.getAssociatedToStreamId();
        final int priority = synStreamFrame.getPriority();
        final int slot = synStreamFrame.getSlot();

        if (frame.getHeader().getVersion() != SPDY_VERSION) {
            sendRstStream(context, streamId, RstStreamFrame.UNSUPPORTED_VERSION, null);
            return;
        }

        final SpdyRequest spdyRequest = SpdyRequest.create();
        spdyRequest.setConnection(context.getConnection());
        final SpdyStream spdyStream = spdySession.acceptStream(spdyRequest,
                streamId, associatedToStreamId, priority, slot);
        if (spdyStream == null) { // GOAWAY has been sent, so ignoring this request
            spdyRequest.recycle();
            frame.getHeader().getUnderlying().dispose();
            frame.recycle();
            return;
        }
        
        final Buffer decoded;

        try {
            final SpdyInflaterOutputStream inflaterOutputStream = spdySession.getInflaterOutputStream();
            inflaterOutputStream.write(synStreamFrame.getCompressedHeaders());
            decoded = inflaterOutputStream.checkpoint();
        } catch (IOException dfe) {
            sendRstStream(context, streamId, RstStreamFrame.PROTOCOL_ERROR, null);
            return;
        } finally {
            frame.getHeader().getUnderlying().dispose();
        }

        if (decoded.hasArray()) {
            processSynStreamHeadersArray(spdyRequest, decoded);
        } else {
            processSynStreamHeadersBuffer(spdyRequest, decoded);
        }
        
        prepareIncomingRequest(spdyRequest);
        if (synStreamFrame.isFlagSet(SynStreamFrame.FLAG_FIN)) {
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

    private void processSynStreamHeadersArray(SpdyRequest spdyRequest, Buffer decoded) {
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
    }

    private void processSynStreamHeadersBuffer(SpdyRequest spdyRequest, Buffer decoded) {

        int position = decoded.position();
        final int headersCount = decoded.getInt(position);
        position += 4;

        for (int i = 0; i < headersCount; i++) {
            final boolean isServiceHeader = (decoded.get(position + 4) == ':');

            if (isServiceHeader) {
                position = processServiceSynStreamHeader(spdyRequest, decoded, position);
            } else {
                position = processNormalHeader(spdyRequest, decoded, position);
            }
        }
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

    private int processServiceSynStreamHeader(final SpdyRequest spdyRequest,
                                              final Buffer buffer, final int position) {

        final int nameSize = buffer.getInt(position);
        final int valueSize = buffer.getInt(position + nameSize + 4);

        final int nameStart = position + 5; // Skip headerNameSize and following ':'
        final int valueStart = position + nameSize + 8;
        final int valueEnd = valueStart + valueSize;

        switch (nameSize - 1) {
            case 4: {
                if (checkBufferContent(buffer, nameStart,
                        Constants.HOST_HEADER_BYTES)) {
                    spdyRequest.getHeaders().addValue(Header.Host)
                            .setBuffer(buffer, valueStart, valueEnd);

                    return valueEnd;
                } else if (checkBufferContent(buffer, nameStart,
                        Constants.PATH_HEADER_BYTES)) {

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
                        Constants.METHOD_HEADER_BYTES)) {
                    spdyRequest.getMethodDC().setBuffer(
                            buffer, valueStart, valueEnd);

                    return valueEnd;
                } else if (checkBufferContent(buffer, nameStart,
                        Constants.SCHEMA_HEADER_BYTES)) {

                    spdyRequest.setSecure(valueSize == 5); // support http and https only
                    return valueEnd;
                }
            }
            case 7: {
                if (checkBufferContent(buffer, nameStart,
                        Constants.VERSION_HEADER_BYTES)) {
                    spdyRequest.getProtocolDC().setBuffer(
                            buffer, valueStart, valueEnd);

                    return valueEnd;
                }
            }
        }

        LOGGER.log(Level.FINE, "Skipping unknown service header[{0}={1}",
                new Object[] {
                    buffer.toStringContent(Charsets.ASCII_CHARSET, position, nameSize),
                    buffer.toStringContent(Charsets.ASCII_CHARSET, valueStart, valueSize)});

        return valueEnd;
    }
    
    private void processSynReply(final SpdySession spdySession,
                                 final FilterChainContext context,
                                 final SpdyFrame frame) {

        SynReplyFrame synReplyFrame = (SynReplyFrame) frame;

        final int streamId = synReplyFrame.getStreamId();

        if (frame.getHeader().getVersion() != SPDY_VERSION) {
            sendRstStream(context, streamId, RstStreamFrame.UNSUPPORTED_VERSION, null);
            return;
        }

        final SpdyStream spdyStream = spdySession.getStream(streamId);
        
        if (spdyStream == null) { // Stream doesn't exist
            frame.getHeader().getUnderlying().dispose();
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
        
        final boolean isFin = synReplyFrame.isFlagSet(SynReplyFrame.FLAG_FIN);
        if (isFin) {
            spdyResponse.setExpectContent(false);
            spdyStream.closeInput();
        }
        
        final Buffer decoded;

        try {
            final SpdyInflaterOutputStream inflaterOutputStream = spdySession.getInflaterOutputStream();
            inflaterOutputStream.write(synReplyFrame.getCompressedHeaders());
            decoded = inflaterOutputStream.checkpoint();
        } catch (IOException dfe) {
            sendRstStream(context, streamId, RstStreamFrame.PROTOCOL_ERROR, null);
            return;
        } finally {
            frame.getHeader().getUnderlying().dispose();
        }

        if (decoded.hasArray()) {
            processSynReplyHeadersArray(spdyResponse, decoded);
        } else {
            processSynReplyHeadersBuffer(spdyResponse, decoded);
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

        if (spdyMode == SpdyMode.NPN) {
            final FilterChain filterChain = connection.getFilterChain();
            final int idx = filterChain.indexOfType(SSLFilter.class);
            if (idx != -1) { // use TLS NPN
                final SSLFilter sslFilter = (SSLFilter) filterChain.get(idx);
                NextProtoNegSupport.getInstance().configure(sslFilter);
                NextProtoNegSupport.getInstance().setClientSideNegotiator(
                        connection, CLIENT_NPN_NEGOTIATOR);

                sslFilter.handshake(connection, null);
            }
        }
        
        return ctx.getInvokeAction();
    }
    
    @Override
    public NextAction handleEvent(FilterChainContext ctx, Event event) throws IOException {
        if (event.type() == HttpServerFilter.RESPONSE_COMPLETE_EVENT.type()) {
            final HttpContext httpContext = HttpContext.get(ctx);
            final SpdyStream spdyStream = (SpdyStream) httpContext.getContextStorage();
            spdyStream.onProcessingComplete();
            
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

    private static int processServiceSynReplyHeader(final SpdyResponse spdyResponse,
                                                    final Buffer buffer,
                                                    int position) {

        final int nameSize = buffer.getInt(position);
        final int valueSize = buffer.getInt(position + nameSize + 4);

        final int nameStart = position + 5; // Skip headerNameSize and following ':'
        final int valueStart = position + nameSize + 8;
        final int valueEnd = valueStart + valueSize;

        switch (nameSize - 1) {
            case 6: {
                if (checkBufferContent(buffer, nameStart,
                        Constants.STATUS_HEADER_BYTES)) {
                    if (valueEnd < 3) {
                        throw new IllegalStateException("Unknown status code: " +
                                buffer.toStringContent(Charsets.ASCII_CHARSET, valueEnd, (valueEnd - valueStart)));
                    }

                    spdyResponse.setStatus(Ascii.parseInt(buffer, valueStart, 3));

                    final int reasonPhraseIdx =
                            HttpCodecUtils.skipSpaces(buffer, valueStart + 3, valueEnd);

                    if (reasonPhraseIdx != -1) {
                        int reasonPhraseEnd = skipLastSpaces(buffer,
                                valueStart + 3, valueEnd) + 1;
                        if (reasonPhraseEnd == 0) {
                            reasonPhraseEnd = valueEnd;
                        }

                        spdyResponse.getReasonPhraseDC().setBuffer(
                                buffer, reasonPhraseIdx, reasonPhraseEnd);
                    }

                    return valueEnd;
                }

                break;
            }

            case 7: {
                if (checkBufferContent(buffer, nameStart,
                        Constants.VERSION_HEADER_BYTES)) {
                    spdyResponse.setProtocol(Protocol.valueOf(buffer,
                            valueStart, valueEnd - valueStart));

                    return valueEnd;
                }
            }
        }

        LOGGER.log(Level.FINE, "Skipping unknown service header[{0}={1}",
                new Object[]{buffer.toStringContent(Charsets.ASCII_CHARSET, position, nameSize),
                             buffer.toStringContent(Charsets.ASCII_CHARSET, valueStart, valueSize)});

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
        for (int i = 0, len = b2.length; i < len; i++) {
            if (b1[pos + i] != b2[i]) {
                return false;
            }
        }

        return true;
    }

    private static boolean checkBufferContent(final Buffer toTest, final int pos, final byte[] control) {
        for (int i = 0, len = control.length; i < len; i++) {
            if (toTest.get(pos + i) != control[i]) {
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
    
    private static void sendRstStream(final FilterChainContext ctx,
            final int streamId, final int statusCode,
            final CompletionHandler<WriteResult> completionHandler) {

        RstStreamFrame rstStreamFrame = RstStreamFrame.builder().
                statusCode(statusCode).streamId(streamId).build();

        ctx.write(rstStreamFrame, completionHandler);
    }

    private SpdySession checkSpdySession(final FilterChainContext context,
            final boolean isUpStream) {
        final Connection connection = context.getConnection();
        
        SpdySession spdySession = SpdySession.get(connection);
        if (spdySession == null) {
            spdySession = new SpdySession(connection);
            SpdySession.bind(connection, spdySession);
            // send the configuration for this session
            final SettingsFrameBuilder builder = SettingsFrame.builder();
            builder.setting(SETTINGS_MAX_CONCURRENT_STREAMS, maxConcurrentStreams);
            builder.setting(SETTINGS_INITIAL_WINDOW_SIZE, initialWindowSize);
            builder.setFlag(SettingsFrame.FLAG_SETTINGS_CLEAR_SETTINGS);
            context.write(builder.build());
        }
        
        spdySession.initCommunication(context, isUpStream);

        return spdySession;
    }

    private static void processSynReplyHeadersArray(SpdyResponse spdyResponse, Buffer decoded) {
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
    }

    private static void processSynReplyHeadersBuffer(SpdyResponse spdyResponse, Buffer decoded) {
        int position = decoded.position();

        final int headersCount = decoded.getInt(position);
        position += 4;

        for (int i = 0; i < headersCount; i++) {
            final boolean isServiceHeader = (decoded.get(position + 4) == ':');

            if (isServiceHeader) {
                position = processServiceSynReplyHeader(spdyResponse, decoded, position);
            } else {
                position = processNormalHeader(spdyResponse, decoded, position);
            }
        }
    }

    private static void processDataFrame(final SpdyStream spdyStream,
                                         final SpdyFrame frame) {

        DataFrame dataFrame = (DataFrame) frame;
        spdyStream.offerInputData(dataFrame.getData(), dataFrame.isFlagSet(DataFrame.FLAG_FIN));
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
