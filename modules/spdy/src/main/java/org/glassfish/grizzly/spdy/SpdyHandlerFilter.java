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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.CompletionHandler;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.WriteResult;
import org.glassfish.grizzly.filterchain.FilterChain;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.FilterChainContext.TransportContext;
import org.glassfish.grizzly.filterchain.FilterChainEvent;
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
import org.glassfish.grizzly.http.util.DataChunk;
import org.glassfish.grizzly.http.util.Header;
import org.glassfish.grizzly.http.util.HttpCodecUtils;
import org.glassfish.grizzly.http.util.HttpStatus;
import org.glassfish.grizzly.http.util.MimeHeaders;
import org.glassfish.grizzly.npn.ClientSideNegotiator;
import org.glassfish.grizzly.spdy.compression.SpdyInflaterOutputStream;
import org.glassfish.grizzly.spdy.frames.CredentialFrame;
import org.glassfish.grizzly.spdy.frames.DataFrame;
import org.glassfish.grizzly.spdy.frames.GoAwayFrame;
import org.glassfish.grizzly.spdy.frames.HeadersFrame;
import org.glassfish.grizzly.spdy.frames.HeadersProviderFrame;
import org.glassfish.grizzly.spdy.frames.PingFrame;
import org.glassfish.grizzly.spdy.frames.RstStreamFrame;
import org.glassfish.grizzly.spdy.frames.SettingsFrame;
import org.glassfish.grizzly.spdy.frames.SettingsFrame.SettingsFrameBuilder;
import org.glassfish.grizzly.spdy.frames.SpdyFrame;
import org.glassfish.grizzly.spdy.frames.SynReplyFrame;
import org.glassfish.grizzly.spdy.frames.SynStreamFrame;
import org.glassfish.grizzly.spdy.frames.WindowUpdateFrame;
import org.glassfish.grizzly.ssl.SSLFilter;

import javax.net.ssl.SSLEngine;
import org.glassfish.grizzly.EmptyCompletionHandler;
import org.glassfish.grizzly.memory.Buffers;
import org.glassfish.grizzly.spdy.frames.OversizedFrame;
import org.glassfish.grizzly.spdy.frames.ServiceFrame;

import static org.glassfish.grizzly.spdy.SpdyDecoderUtils.*;
import static org.glassfish.grizzly.spdy.Constants.*;
import static org.glassfish.grizzly.spdy.frames.SettingsFrame.SETTINGS_INITIAL_WINDOW_SIZE;
import static org.glassfish.grizzly.spdy.frames.SettingsFrame.SETTINGS_MAX_CONCURRENT_STREAMS;

/**
 * The {@link org.glassfish.grizzly.filterchain.Filter} serves as a bridge
 * between SPDY and HTTP layers by converting {@link SpdyFrame}s into
 * {@link HttpPacket}s and passing them up/down by the {@link FilterChain}.
 * 
 * Additionally this {@link org.glassfish.grizzly.filterchain.Filter} has
 * logic responsible for checking SPDY protocol semantics and fire correspondent
 * events and messages in case when SPDY semantics is broken.
 * 
 * @author Grizzly team
 */
public class SpdyHandlerFilter extends HttpBaseFilter {
    private final static Logger LOGGER = Grizzly.logger(SpdyHandlerFilter.class);

    private static final ClientNpnNegotiator DEFAULT_CLIENT_NPN_NEGOTIATOR =
            new ClientNpnNegotiator();

    private static final TransferEncoding FIXED_LENGTH_ENCODING =
            new FixedLengthTransferEncoding();
    
    private final SpdyMode spdyMode;
    
    private final ExecutorService threadPool;

    private volatile int maxConcurrentStreams = DEFAULT_MAX_CONCURRENT_STREAMS;
    private volatile int initialWindowSize = DEFAULT_INITIAL_WINDOW_SIZE;

    /**
     * Constructs SpdyHandlerFilter.
     * 
     * @param spdyMode the {@link SpdyMode}.
     */
    public SpdyHandlerFilter(final SpdyMode spdyMode) {
        this(spdyMode, null);
    }

    /**
     * Constructs SpdyHandlerFilter.
     * 
     * @param spdyMode the {@link SpdyMode}.
     * @param threadPool the {@link ExecutorService} to be used to process {@link SynStreamFrame} and
     * {@link SynReplyFrame} frames, if <tt>null</tt> mentioned frames will be processed in the same thread they were parsed.
     */
    public SpdyHandlerFilter(final SpdyMode spdyMode,
            final ExecutorService threadPool) {
        this.spdyMode = spdyMode;
        this.threadPool = threadPool;
    }

    /**
     * Sets the default maximum number of concurrent streams allowed for one session.
     * Negative value means "unlimited".
     */
    public void setMaxConcurrentStreams(final int maxConcurrentStreams) {
        this.maxConcurrentStreams = maxConcurrentStreams;
    }

    /**
     * Returns the default maximum number of concurrent streams allowed for one session.
     * Negative value means "unlimited".
     */
    public int getMaxConcurrentStreams() {
        return maxConcurrentStreams;
    }

    /**
     * Sets the default initial stream window size (in bytes) for new SPDY sessions.
     */
    public void setInitialWindowSize(final int initialWindowSize) {
        this.initialWindowSize = initialWindowSize;
    }

    /**
     * Returns the default initial stream window size (in bytes) for new SPDY sessions.
     */
    public int getInitialWindowSize() {
        return initialWindowSize;
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
        
        try {
            if (message instanceof SpdyFrame) {
                final SpdyFrame frame = (SpdyFrame) message;
                try {
                    processInFrame(spdySession, ctx, frame);
                } catch (SpdyStreamException e) {
                    if (LOGGER.isLoggable(Level.FINE)) {
                        LOGGER.log(Level.FINE, "SpdyStreamException occurred on connection=" +
                                ctx.getConnection() + " during SpdyFrame processing", e);
                    }
                    
                    sendRstStream(ctx, e.getStreamId(), e.getRstReason(), null);
                }
            } else {
                final ArrayList<SpdyFrame> framesList = (ArrayList<SpdyFrame>) message;
                final int sz = framesList.size();
                try {
                    for (int i = 0; i < sz; i++) {
                        try {
                            processInFrame(spdySession, ctx, framesList.get(i));
                        } catch (SpdyStreamException e) {
                            if (LOGGER.isLoggable(Level.FINE)) {
                                LOGGER.log(Level.FINE, "SpdyStreamException occurred on connection=" +
                                        ctx.getConnection() + " during SpdyFrame processing", e);
                            }
                            
                            sendRstStream(ctx, e.getStreamId(), e.getRstReason(), null);
                        }
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
        } catch (SpdySessionException e) {
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.log(Level.FINE, "SpdySessionException occurred on connection=" +
                        ctx.getConnection() + " during SpdyFrame processing", e);
            }
            
            final Connection connection = ctx.getConnection();
            
            sendRstStream(ctx, e.getStreamId(), e.getRstReason(),
                    new EmptyCompletionHandler<WriteResult>() {
                @Override
                public void completed(WriteResult result) {
                    connection.closeSilently();
                }
            });
            
            return ctx.getSuspendAction();
        } catch (IOException e) {
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.log(Level.FINE, "IOException occurred on connection=" +
                        ctx.getConnection() + " during SpdyFrame processing", e);
            }            
            final Connection connection = ctx.getConnection();
            ctx.write(Buffers.EMPTY_BUFFER,
                    new EmptyCompletionHandler<WriteResult>() {
                @Override
                public void completed(WriteResult result) {
                    connection.closeSilently();
                }
            });
            
            return ctx.getSuspendAction();
        }
        
        return ctx.getStopAction();
    }
    
    private void processInFrame(final SpdySession spdySession,
                                final FilterChainContext context,
                                final SpdyFrame frame)
            throws SpdyStreamException, SpdySessionException, IOException {

        if (frame.isService()) {
            processServiceFrame(spdySession, (ServiceFrame) frame);
        } else if (frame.getHeader().isControl()) {
            processControlFrame(spdySession, context, frame);
        } else {
            final SpdyStream spdyStream = spdySession.getStream(frame.getHeader().getStreamId());
            
            if (spdyStream == null) {
                if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.log(Level.FINE, "DataFrame came for unexisting stream: connection={0}, frame={1}, stream={2}",
                            new Object[]{context.getConnection(), frame, spdyStream});
                }
                
                frame.recycle();
                
                return;
            }

            if (spdyStream.isUnidirectional() &&
                    spdyStream.isLocallyInitiatedStream()) {
                throw new SpdyStreamException(spdyStream.getStreamId(),
                        RstStreamFrame.PROTOCOL_ERROR,
                        "Data frame came on unidirectional stream");
            }
            
            processDataFrame(spdyStream, frame);
        }

    }

    private void processControlFrame(final SpdySession spdySession,
            final FilterChainContext context, final SpdyFrame frame)
            throws SpdyStreamException, SpdySessionException, IOException {

        switch (frame.getHeader().getType()) {
            case SynStreamFrame.TYPE: {
                processSynStream(spdySession, context, frame);
                break;
            }
            case SettingsFrame.TYPE: {
                processSettings(spdySession, frame);
                break;
            }
            case SynReplyFrame.TYPE: {
                processSynReply(spdySession, context, frame);
                break;
            }
            case PingFrame.TYPE: {
                processPing(spdySession, frame);
                break;
            }
            case RstStreamFrame.TYPE: {
                processRstStream(spdySession, frame);
                break;
            }
            case GoAwayFrame.TYPE: {
                processGoAwayFrame(spdySession, frame);
                break;
            }
            case WindowUpdateFrame.TYPE: {
                processWindowUpdateFrame(spdySession, frame);
                break;
            }
            case HeadersFrame.TYPE:
            case CredentialFrame.TYPE: // Will remain unimplemented for the time being.
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
            final SpdyFrame frame) throws SpdyStreamException {
        
        WindowUpdateFrame updateFrame = (WindowUpdateFrame) frame;
        final int streamId = updateFrame.getStreamId();
        final int delta = updateFrame.getDelta();

        final SpdyStream stream = spdySession.getStream(streamId);
        
        if (stream != null) {
            stream.onPeerWindowUpdate(delta);
        } else {
            if (LOGGER.isLoggable(Level.FINE)) {
                final StringBuilder sb = new StringBuilder(64);
                sb.append("\nStream id=")
                        .append(streamId)
                        .append(" was not found. Ignoring the message");
                LOGGER.fine(sb.toString());
            }
        }
    }

    private void processGoAwayFrame(final SpdySession spdySession,
                                    final SpdyFrame frame) {

        GoAwayFrame goAwayFrame = (GoAwayFrame) frame;
        spdySession.setGoAway(goAwayFrame.getLastGoodStreamId());
    }
    
    private void processSettings(final SpdySession spdySession,
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
                            spdySession.setPeerMaxConcurrentStreams(settingsFrame.getSetting(i));
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
                             final SpdyFrame frame) {
        PingFrame pingFrame = (PingFrame) frame;

        // Send the same ping message back
        spdySession.writeDownStream(pingFrame);
    }

    private void processRstStream(final SpdySession spdySession,
                                  final SpdyFrame frame) {

        final RstStreamFrame rstFrame = (RstStreamFrame) frame;
        final int streamId = rstFrame.getStreamId();
        final SpdyStream spdyStream = spdySession.getStream(streamId);
        if (spdyStream == null) {
            // If the stream is not found - just ignore the rst
            frame.recycle();
            return;
        }
        
        // Notify the stream that it has been reset remotely
        spdyStream.resetRemotely();
    }
    
    private void processSynStream(final SpdySession spdySession,
                                  final FilterChainContext context,
                                  final SpdyFrame frame) throws IOException {

        SynStreamFrame synStreamFrame = (SynStreamFrame) frame;
        final int streamId = synStreamFrame.getStreamId();
        final int associatedToStreamId = synStreamFrame.getAssociatedToStreamId();
        final int priority = synStreamFrame.getPriority();
        final int slot = synStreamFrame.getSlot();
        final boolean isUnidirectional = synStreamFrame.isFlagSet(
                SynStreamFrame.FLAG_UNIDIRECTIONAL);

        if (frame.getHeader().getVersion() != SPDY_VERSION) {
            throw new SpdySessionException(streamId, RstStreamFrame.UNSUPPORTED_VERSION);
        }

        final SpdyRequest spdyRequest = SpdyRequest.create();
        spdyRequest.setConnection(context.getConnection());

        final boolean isFinSet = synStreamFrame.isFlagSet(SynStreamFrame.FLAG_FIN);
        final SpdyStream spdyStream;

        try {
            spdyStream = spdySession.acceptStream(spdyRequest, streamId,
                    associatedToStreamId, priority, slot, isUnidirectional);
            if (spdyStream == null) { // GOAWAY has been sent, so ignoring this request
                frame.getHeader().getUnderlying().tryDispose();
                spdyRequest.recycle();
                return;
            }
            decodeHeaders(spdyRequest, spdyStream, synStreamFrame);
        } finally {
            frame.recycle();
        }

        prepareIncomingRequest(spdyStream, spdyRequest);
        spdyStream.onSynFrameCome();
        
        if (!isUnidirectional) {
            // Bidirectional syn stream will be transformed to HTTP request packet
            if (isFinSet) {
                spdyRequest.setExpectContent(false);
            }

            final boolean isExpectContent = spdyRequest.isExpectContent();
            if (!isExpectContent) {
                spdyStream.inputBuffer.close(IN_FIN_TERMINATION);
            }

            sendUpstream(spdySession, spdyStream, spdyRequest, isExpectContent);
        } else {
            // Unidirectional syn stream will be transformed to HTTP response packet
            spdyRequest.setExpectContent(false);
            spdyStream.outputSink.terminate(IN_FIN_TERMINATION);
            
            final HttpResponsePacket spdyResponse = spdyRequest.getResponse();
            
            if (isFinSet) {
                spdyResponse.setExpectContent(false);
            }

            sendUpstream(spdySession, spdyStream, spdyResponse, !isFinSet);
        }
    }

    private void decodeHeaders(final HttpHeader httpHeader,
            final SpdyStream spdyStream,
            final HeadersProviderFrame headersProviderFrame)
            throws IOException {
        
        final SpdyInflaterOutputStream inflaterOutputStream =
                spdyStream.getSpdySession().getInflaterOutputStream();
        
        inflaterOutputStream.write(headersProviderFrame.getCompressedHeaders());
        final Buffer decodedHeaders = inflaterOutputStream.checkpoint();
        if (httpHeader.isRequest()) {
            if (decodedHeaders.hasArray()) {
                if (!spdyStream.isUnidirectional()) {
                    processSynStreamHeadersArray((SpdyRequest) httpHeader,
                            decodedHeaders);
                } else {
                    processUSynStreamHeadersArray((SpdyRequest) httpHeader,
                            decodedHeaders);
                }
            } else {
                if (!spdyStream.isUnidirectional()) {
                    processSynStreamHeadersBuffer((SpdyRequest) httpHeader,
                            decodedHeaders);
                } else {
                    processUSynStreamHeadersBuffer((SpdyRequest) httpHeader,
                            decodedHeaders);
                }
            }
        } else {
            if (decodedHeaders.hasArray()) {
                processSynReplyHeadersArray((SpdyResponse) httpHeader,
                        decodedHeaders);
            } else {
                processSynReplyHeadersBuffer((SpdyResponse) httpHeader,
                        decodedHeaders);
            }
        }
    }
    
    private void processSynReply(final SpdySession spdySession,
            final FilterChainContext context, final SpdyFrame frame)
            throws SpdySessionException, IOException {

        SynReplyFrame synReplyFrame = (SynReplyFrame) frame;

        final int streamId = synReplyFrame.getStreamId();

        if (frame.getHeader().getVersion() != SPDY_VERSION) {
            throw new SpdySessionException(streamId, RstStreamFrame.UNSUPPORTED_VERSION);
        }

        final SpdyStream spdyStream = spdySession.getStream(streamId);
        
        if (spdyStream == null) { // Stream doesn't exist
            frame.getHeader().getUnderlying().dispose();
            frame.recycle();
            return;
        }
        
        if (spdyStream.isUnidirectional()) {
            throw new SpdyStreamException(streamId, RstStreamFrame.PROTOCOL_ERROR,
                    "SynReply came on unidirectional stream");
        }
        
        final HttpRequestPacket spdyRequest = spdyStream.getSpdyRequest();
        
        final SpdyResponse spdyResponse;
        
        final HttpResponsePacket response = spdyRequest.getResponse();
        if (response == null) {
            spdyResponse = SpdyResponse.create();
        } else if (response instanceof SpdyResponse) {
            spdyResponse = (SpdyResponse) response;
        } else {
            frame.getHeader().getUnderlying().dispose();
            frame.recycle();
            throw new IllegalStateException("Unexpected response type: " + response.getClass());
        }
        
        final boolean isFin = synReplyFrame.isFlagSet(SynReplyFrame.FLAG_FIN);
        if (isFin) {
            spdyResponse.setExpectContent(false);
            spdyStream.inputBuffer.close(IN_FIN_TERMINATION);
        }
        
        try {
            decodeHeaders(spdyResponse, spdyStream, synReplyFrame);
        } finally {
            frame.recycle();
        }

        spdyStream.onSynFrameCome();
        
        bind(spdyRequest, spdyResponse);
        sendUpstream(spdySession, spdyStream, spdyResponse, !isFin);
    }


    
    @Override
    @SuppressWarnings("unchecked")
    public NextAction handleWrite(final FilterChainContext ctx) throws IOException {
        final Object message = ctx.getMessage();
        
        final SpdySession spdySession = checkSpdySession(ctx, false);

        if (spdySession != null) {
            if (HttpPacket.isHttp(message)) {

                // Get HttpPacket
                final HttpPacket httpPacket = ctx.getMessage();
                final HttpHeader httpHeader = httpPacket.getHttpHeader();

                if (httpHeader.isRequest()) {
                    processOutgoingRequest(ctx, spdySession,
                            (HttpRequestPacket) httpHeader, httpPacket);
                } else {
                    processOutgoingResponse(ctx, spdySession,
                            (HttpResponsePacket) httpHeader, httpPacket);
                }
                
            } else {
                final TransportContext transportContext = ctx.getTransportContext();
                spdySession.writeDownStream(message,
                        transportContext.getCompletionHandler(),
                        transportContext.getMessageCloner());
            }
            
            return ctx.getStopAction();
        }

        return ctx.getInvokeAction();
    }

    @SuppressWarnings("unchecked")
    private void processOutgoingRequest(final FilterChainContext ctx,
            final SpdySession spdySession,
            final HttpRequestPacket request,
            final HttpPacket entireHttpPacket) throws IOException {

        final boolean isNewStream = !request.isCommitted();
        
        if (isNewStream) {
            prepareOutgoingRequest(request);
            spdySession.getNewClientStreamLock().lock();
        }
        
        try {
            SpdyStream spdyStream = SpdyStream.getSpdyStream(request);

            if (spdyStream == null) {
                spdyStream = spdySession.openStream(
                        request,
                        spdySession.getNextLocalStreamId(),
                        0, 0, 0, false, !request.isExpectContent());
            }

            assert spdyStream != null;

            final TransportContext transportContext = ctx.getTransportContext();

            spdyStream.writeDownStream(entireHttpPacket,
                    transportContext.getCompletionHandler(),
                    transportContext.getMessageCloner());

        } finally {
            if (isNewStream) {
                spdySession.getNewClientStreamLock().unlock();
            }
        }
    }
    
    @SuppressWarnings("unchecked")
    private void processOutgoingResponse(final FilterChainContext ctx,
            final SpdySession spdySession,
            final HttpResponsePacket response,
            final HttpPacket entireHttpPacket) throws IOException {

        final SpdyStream spdyStream = SpdyStream.getSpdyStream(response);
        assert spdyStream != null;

        if (!response.isCommitted()) {
            prepareOutgoingResponse(response);
            pushAssociatedResoureses(spdyStream);
        }

        final TransportContext transportContext = ctx.getTransportContext();

        spdyStream.writeDownStream(entireHttpPacket,
                transportContext.getCompletionHandler(),
                transportContext.getMessageCloner());
    }
    
    private void processServiceFrame(
            final SpdySession spdySession,final ServiceFrame frame)
            throws SpdyStreamException, SpdySessionException {
        if (frame.getServiceCode() == OversizedFrame.CODE) {
            final org.glassfish.grizzly.spdy.frames.SpdyHeader header =
                    frame.getHeader();
            
            if (header.isControl()) { // Control frame
                int spdyStreamId = header.getStreamId();
                final Buffer message = header.getUnderlying();
                
                // try to get stream-id if possible
                if (spdyStreamId == -1 && message.remaining() >= 4
                        && CTRL_FRAMES_WITH_STREAM_ID.contains(header.getType())) {
                    spdyStreamId = message.getInt(message.position()) & 0x7FFFFFFF;
                }
                
                throw new SpdySessionException(spdyStreamId, RstStreamFrame.FRAME_TOO_LARGE);
            } else { // DataFrame
                final int streamId = header.getStreamId();
                final SpdyStream spdyStream = spdySession.getStream(streamId);
                
                if (spdyStream != null) {
                    // Known SpdyStream
                    spdyStream.inputBuffer.close(FRAME_TOO_LARGE_TERMINATION);
                    throw new SpdyStreamException(streamId, RstStreamFrame.FRAME_TOO_LARGE);
                } else {
                    throw new SpdyStreamException(streamId, RstStreamFrame.INVALID_STREAM);
                }
            }
        }
    }
    
    @Override
    public NextAction handleConnect(final FilterChainContext ctx) throws IOException {
        final Connection connection = ctx.getConnection();
        
        createSpdySession(connection, false);

        if (spdyMode == SpdyMode.NPN) {
            final FilterChain filterChain = (FilterChain) connection.getProcessor();
            final int idx = filterChain.indexOfType(SSLFilter.class);
            if (idx != -1) { // use TLS NPN
                final SSLFilter sslFilter = (SSLFilter) filterChain.get(idx);
                NextProtoNegSupport.getInstance().configure(sslFilter);
                NextProtoNegSupport.getInstance().setClientSideNegotiator(
                        connection, getClientNpnNegotioator());

                sslFilter.handshake(connection, null);
            }
        }
        
        return ctx.getInvokeAction();
    }
    
    @Override
    public NextAction handleEvent(FilterChainContext ctx, FilterChainEvent event) throws IOException {
        if (event.type() == HttpServerFilter.RESPONSE_COMPLETE_EVENT.type()) {
            final HttpContext httpContext = HttpContext.get(ctx);
            final SpdyStream spdyStream = (SpdyStream) httpContext.getContextStorage();
            spdyStream.onProcessingComplete();
            
            return ctx.getStopAction();
        }

        return ctx.getInvokeAction();
    }

    /**
     * Returns the client NPN negotiator to be used if this filter is used in
     * the client-side filter chain.
     */
    protected ClientSideNegotiator getClientNpnNegotioator() {
        return DEFAULT_CLIENT_NPN_NEGOTIATOR;
    }

    /**
     * Creates {@link SpdySession} with preconfigured initial-windows-size and
     * max-concurrent-
     * @param connection
     * @return 
     */
    private SpdySession createSpdySession(final Connection connection,
            final boolean isServer) {
        
        final SpdySession spdySession = new SpdySession(connection, isServer);
        spdySession.setLocalInitialWindowSize(initialWindowSize);
        spdySession.setLocalMaxConcurrentStreams(maxConcurrentStreams);
        
        SpdySession.bind(connection, spdySession);
        
        return spdySession;
    }
    
    private void sendUpstream(final SpdySession spdySession,
            final SpdyStream spdyStream, final HttpHeader httpHeader,
            final boolean isExpectContent) {
        if (threadPool == null) {
            spdySession.sendMessageUpstream(spdyStream,
                    HttpContent.builder(httpHeader)
                    .last(!isExpectContent)
                    .build());
        } else {
            threadPool.execute(new Runnable() {
                @Override
                public void run() {
                    spdySession.sendMessageUpstream(spdyStream,
                            HttpContent.builder(httpHeader)
                            .last(!isExpectContent)
                            .build());
                }
            });
        }
    }
    
    private void prepareIncomingRequest(final SpdyStream spdyStream,
            final SpdyRequest request) {

        final ProcessingState state = request.getProcessingState();
        final HttpResponsePacket response = request.getResponse();

        final Method method = request.getMethod();

        if (!spdyStream.isUnidirectional() &&
                (Method.GET.equals(method)
                || Method.HEAD.equals(method)
                || (!Method.CONNECT.equals(method)
                        && request.getContentLength() == 0))) {
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

        if (response.getContentLength() != -1) {
            // FixedLengthTransferEncoding will set proper Content-Length header
            FIXED_LENGTH_ENCODING.prepareSerialize(null, response, null);
        }
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
            spdySession = createSpdySession(connection, true);
        }
        
        if (spdySession.initCommunication(context, isUpStream)) {
            sendSettingsIfNeeded(spdySession, context);
        }

        return spdySession;
    }

    private void sendSettingsIfNeeded(final SpdySession spdySession,
            final FilterChainContext context) {

        final boolean isConcurrentStreamsUpdated =
                spdySession.getLocalMaxConcurrentStreams() >= 0;
        final boolean isInitialWindowUpdated =
                spdySession.getLocalInitialWindowSize() != DEFAULT_INITIAL_WINDOW_SIZE;
        
        if (isConcurrentStreamsUpdated || isInitialWindowUpdated) {
            final SettingsFrameBuilder builder = SettingsFrame.builder();
            
            if (isConcurrentStreamsUpdated) {
                builder.setting(SETTINGS_MAX_CONCURRENT_STREAMS, maxConcurrentStreams);
            }
            
            if (isInitialWindowUpdated) {
                builder.setting(SETTINGS_INITIAL_WINDOW_SIZE, initialWindowSize);
            }
            
            builder.setFlag(SettingsFrame.FLAG_SETTINGS_CLEAR_SETTINGS);
            context.write(builder.build());
        }
    }
    
    private static void processDataFrame(final SpdyStream spdyStream,
            final SpdyFrame frame) throws SpdyStreamException {

        DataFrame dataFrame = (DataFrame) frame;
        spdyStream.offerInputData(dataFrame.getData(), dataFrame.isFlagSet(DataFrame.FLAG_FIN));
    }

    private void pushAssociatedResoureses(final SpdyStream spdyStream)
            throws IOException {
        final Map<String, PushData> pushDataMap =
                spdyStream.getAssociatedResourcesToPush();
        
        if (pushDataMap != null) {           
            final SpdySession spdySession = spdyStream.getSpdySession();
            final ReentrantLock lock = spdySession.getNewClientStreamLock();
            lock.lock();
            
            try {
                for (Map.Entry<String, PushData> entry : pushDataMap.entrySet()) {
                    final PushData pushData = entry.getValue();
                    final OutputResource outputResource =
                            pushData.getOutputResource();
                    
                    final SpdyRequest spdyRequest = SpdyRequest.create();
                    final HttpResponsePacket spdyResponse = spdyRequest.getResponse();
                    
                    spdyRequest.setRequestURI(entry.getKey());
                    spdyResponse.setStatus(pushData.getStatusCode());
                    spdyResponse.setProtocol(Protocol.HTTP_1_1);
                    spdyResponse.setContentType(pushData.getContentType());
                    
                    if (outputResource != null) {
                        spdyResponse.setContentLengthLong(outputResource.remaining());
                    }
                    
                    // Add extra headers if any
                    final Map<String, String> extraHeaders = pushData.getHeaders();
                    if (extraHeaders != null) {
                        for (Map.Entry<String, String> headerEntry : extraHeaders.entrySet()) {
                            spdyResponse.addHeader(headerEntry.getKey(), headerEntry.getValue());
                        }
                    }
                    
                    try {
                        final SpdyStream pushStream = spdySession.openStream(
                                spdyRequest,
                                spdySession.getNextLocalStreamId(),
                                spdyStream.getStreamId(),
                                pushData.getPriority(),
                                0,
                                true,
                                false);
                        prepareOutgoingResponse(spdyResponse);
                        
                        pushStream.writeDownStream(outputResource);
                    } catch (Exception e) {
                        LOGGER.log(Level.FINE,
                                "Can not push: " + entry.getKey(), e);
                    }
                }
            } finally {
                lock.unlock();
            }
        }
    }

    private static class ClientNpnNegotiator implements ClientSideNegotiator {
        
        private static final String SPDY3_PROTOCOL = "spdy/3";

        @Override
        public boolean wantNegotiate(final SSLEngine engine) {
            final Connection connection = NextProtoNegSupport.getConnection(engine);
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.log(Level.FINE, "NPN wantNegotiate. Connection={0}",
                        new Object[]{connection});
            }
            return true;
        }

        @Override
        public String selectProtocol(final SSLEngine engine,
                                     final LinkedHashSet<String> protocols) {
            final Connection connection = NextProtoNegSupport.getConnection(engine);
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.log(Level.FINE, "NPN selectProtocol. Connection={0}, protocols={1}",
                        new Object[]{connection, protocols});
            }
            
            return (protocols.contains(SPDY3_PROTOCOL) ? SPDY3_PROTOCOL : "");
        }

        @Override
        public void onNoDeal(final SSLEngine engine) {
            final Connection connection = NextProtoNegSupport.getConnection(engine);
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.log(Level.FINE, "NPN onNoDeal. Connection={0}",
                        new Object[]{connection});
            }
        }
    }    
}
