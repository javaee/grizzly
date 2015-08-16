/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012-2015 Oracle and/or its affiliates. All rights reserved.
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.CloseType;
import org.glassfish.grizzly.Closeable;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Context;
import org.glassfish.grizzly.GenericCloseListener;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.IOEvent;
import org.glassfish.grizzly.IOEventLifeCycleListener;
import org.glassfish.grizzly.ProcessorExecutor;
import org.glassfish.grizzly.WriteHandler;
import org.glassfish.grizzly.filterchain.FilterChain;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.memory.Buffers;
import org.glassfish.grizzly.http.HttpContent;
import org.glassfish.grizzly.http.HttpContext;
import org.glassfish.grizzly.http.HttpHeader;
import org.glassfish.grizzly.http.HttpPacket;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.http.HttpResponsePacket;
import org.glassfish.grizzly.http.Method;
import org.glassfish.grizzly.http.Protocol;
import org.glassfish.grizzly.http.util.Header;

import org.glassfish.grizzly.http2.compression.HeadersDecoder;
import org.glassfish.grizzly.http2.compression.HeadersEncoder;
import org.glassfish.grizzly.http2.frames.ContinuationFrame;
import org.glassfish.grizzly.http2.frames.ErrorCode;
import org.glassfish.grizzly.http2.frames.GoAwayFrame;
import org.glassfish.grizzly.http2.frames.HeadersFrame;
import org.glassfish.grizzly.http2.frames.Http2Frame;
import org.glassfish.grizzly.http2.frames.PushPromiseFrame;
import org.glassfish.grizzly.http2.frames.RstStreamFrame;
import org.glassfish.grizzly.http2.frames.SettingsFrame;
import org.glassfish.grizzly.memory.MemoryManager;
import org.glassfish.grizzly.utils.DataStructures;
import org.glassfish.grizzly.utils.Holder;
import org.glassfish.grizzly.utils.NullaryFunction;

import static org.glassfish.grizzly.http2.frames.SettingsFrame.*;
import static org.glassfish.grizzly.http2.Constants.*;
import static org.glassfish.grizzly.http2.Http2BaseFilter.PRI_PAYLOAD;
import org.glassfish.grizzly.http2.frames.HeaderBlockFragment;
import org.glassfish.grizzly.http2.frames.WindowUpdateFrame;



/**
 * The HTTP2 connection abstraction.
 * 
 * @author Alexey Stashok
 */
public abstract class Http2Connection {
    private static final Logger LOGGER = Grizzly.logger(Http2Connection.class);
    private static final Level LOGGER_LEVEL = Level.INFO;

    private final boolean isServer;
    private final Connection<?> connection;
    Http2State http2State;
    
    private HeadersDecoder headersDecoder;
    private HeadersEncoder headersEncoder;

    private final ReentrantLock deflaterLock = new ReentrantLock();
    
    private int lastPeerStreamId;
    private int lastLocalStreamId;

    private final ReentrantLock newClientStreamLock = new ReentrantLock();
    
    private volatile FilterChain http2StreamChain;
    private volatile FilterChain http2ConnectionChain;
    
    private final Map<Integer, Http2Stream> streamsMap =
            DataStructures.<Integer, Http2Stream>getConcurrentMap();
    
    // (Optimization) We may read several DataFrames belonging to the same
    // SpdyStream, so in order to not process every DataFrame separately -
    // we buffer them and only then passing for processing.
    final List<Http2Stream> streamsToFlushInput = new ArrayList<Http2Stream>();
    
    // The List object used to store header frames. Could be used by
    // Http2Connection streams, when they write headers
    protected final List<Http2Frame> tmpHeaderFramesList =
            new ArrayList<Http2Frame>(2);
    
    private final Object sessionLock = new Object();
    
    private CloseType closeFlag;
    
    private int peerStreamWindowSize = getDefaultStreamWindowSize();
    private volatile int localStreamWindowSize = getDefaultStreamWindowSize();
    
    private volatile int localConnectionWindowSize = getDefaultConnectionWindowSize();
    
    private volatile int localMaxConcurrentStreams = getDefaultMaxConcurrentStreams();
    private int peerMaxConcurrentStreams = getDefaultMaxConcurrentStreams();

    private final StreamBuilder streamBuilder = new StreamBuilder();
    
    private final Http2ConnectionOutputSink outputSink;
    
    // true, if this connection is ready to accept frames or false if the first
    // HTTP/1.1 Upgrade is still in progress
    private volatile boolean isPrefaceReceived;
    private volatile boolean isPrefaceSent;
    
    public static Http2Connection get(final Connection connection) {
        final Http2State http2State = Http2State.get(connection);
        return http2State != null
                ? http2State.getHttp2Connection()
                : null;
    }
    
    static void bind(final Connection connection,
            final Http2Connection http2Connection) {
        Http2State.obtain(connection).setHttp2Connection(http2Connection);
    }
    
    private final Holder<?> addressHolder;

    final Http2BaseFilter handlerFilter;

    private final int localMaxFramePayloadSize;
    private int peerMaxFramePayloadSize = getSpecDefaultFramePayloadSize();
    
    private boolean isFirstInFrame = true;
    
    private final AtomicInteger unackedReadBytes  = new AtomicInteger();
        
    public Http2Connection(final Connection<?> connection,
                       final boolean isServer,
                       final Http2BaseFilter handlerFilter) {
        this.connection = connection;
        this.isServer = isServer;
        this.handlerFilter = handlerFilter;
        
        final int customMaxFramePayloadSz
                = handlerFilter.getLocalMaxFramePayloadSize() > 0
                ? handlerFilter.getLocalMaxFramePayloadSize()
                : -1;
        
        // apply custom max frame value only if it's in [getSpecMinFramePayloadSize(); getSpecMaxFramePayloadSize()] range
        localMaxFramePayloadSize =
                customMaxFramePayloadSz >= getSpecMinFramePayloadSize() &&
                customMaxFramePayloadSz <= getSpecMaxFramePayloadSize()
                ? customMaxFramePayloadSz
                : getSpecDefaultFramePayloadSize();
        
        if (isServer) {
            lastLocalStreamId = 0;
            lastPeerStreamId = -1;
        } else {
            lastLocalStreamId = -1;
            lastPeerStreamId = 0;
        }
        
        addressHolder = Holder.<Object>lazyHolder(new NullaryFunction<Object>() {
            @Override
            public Object evaluate() {
                return connection.getPeerAddress();
            }
        });
        
        connection.addCloseListener(new ConnectionCloseListener());
        
        this.outputSink = newOutputSink();
    }

    public abstract DraftVersion getVersion();
    protected abstract Http2ConnectionOutputSink newOutputSink();
    protected abstract int getSpecDefaultFramePayloadSize();
    protected abstract int getSpecMinFramePayloadSize();
    protected abstract int getSpecMaxFramePayloadSize();
    
    public abstract int getFrameHeaderSize();
    
    public abstract void serializeHttp2FrameHeader(Http2Frame frame,
            Buffer buffer);
    
    public abstract Http2Frame parseHttp2FrameHeader(final Buffer buffer)
            throws Http2ConnectionException;

    public abstract int getDefaultConnectionWindowSize();
    public abstract int getDefaultStreamWindowSize();
    public abstract int getDefaultMaxConcurrentStreams();
    
    protected abstract boolean isFrameReady(final Buffer buffer);
    
    /**
     * Returns the total frame size (including header size), or <tt>-1</tt>
     * if the buffer doesn't contain enough bytes to read the size.
     * 
     * @param buffer
     * @return the total frame size (including header size), or <tt>-1</tt>
     * if the buffer doesn't contain enough bytes to read the size
     */
    protected abstract int getFrameSize(final Buffer buffer);
    
    protected Http2Stream newStream(final HttpRequestPacket request,
            final int streamId, final int refStreamId,
            final int priority) {
        
        return new Http2Stream(this, request, streamId, refStreamId,
                priority);
    }
    
    protected Http2Stream newUpgradeStream(final HttpRequestPacket request,
            final int priority) {
        
        return new Http2Stream(this, request, priority);
    }

    protected void checkFrameSequenceSemantics(final Http2Frame frame)
            throws Http2ConnectionException {
        
        final int frameType = frame.getType();
        
        if (isFirstInFrame) {
            if (frameType != SettingsFrame.TYPE) {
                if (LOGGER.isLoggable(LOGGER_LEVEL)) {
                    LOGGER.log(LOGGER_LEVEL, "First in frame should be a SettingsFrame (preface)", frame);
                }
                
                throw new Http2ConnectionException(ErrorCode.PROTOCOL_ERROR);
            }
            
            isPrefaceReceived = true;
            handlerFilter.onPrefaceReceived(this);
            
            // Preface received - change the HTTP2 connection state
            Http2State.get(connection).setOpen();
            
            isFirstInFrame = false;
        }
        
        // 1) make sure the header frame sequence comes without interleaving
        if (isParsingHeaders()) {
            if (frameType != ContinuationFrame.TYPE) {
                if (LOGGER.isLoggable(LOGGER_LEVEL)) {
                    LOGGER.log(LOGGER_LEVEL, "ContinuationFrame is expected, but {0} came", frame);
                }

                throw new Http2ConnectionException(ErrorCode.PROTOCOL_ERROR);
            }
        } else if (frameType == ContinuationFrame.TYPE) {
            // isParsing == false, so no ContinuationFrame expected
            if (LOGGER.isLoggable(LOGGER_LEVEL)) {
                LOGGER.log(LOGGER_LEVEL, "ContinuationFrame is not expected");
            }

            throw new Http2ConnectionException(ErrorCode.PROTOCOL_ERROR);
        }
    }
    
    protected void onOversizedFrame(final Buffer buffer)
            throws Http2ConnectionException {
        
        final int oldPos = buffer.position();
        try {
            final Http2Frame oversizedFrame = parseHttp2FrameHeader(buffer);
            final int streamId = oversizedFrame.getStreamId();
            if (streamId > 0) {
                final Http2Stream stream = getStream(streamId);
                if (stream != null) {
                    // Known Http2Stream
                    stream.inputBuffer.close(FRAME_TOO_LARGE_TERMINATION);
                    sendRstFrame(ErrorCode.FRAME_SIZE_ERROR, streamId);
                } else {
                    sendRstFrame(ErrorCode.STREAM_CLOSED, streamId);
                }
            } else {
                throw new Http2ConnectionException(ErrorCode.FRAME_SIZE_ERROR);
            }
        } finally {
            buffer.position(oldPos);
        }
    }
    
    boolean isParsingHeaders() {
        return headersDecoder != null && headersDecoder.isProcessingHeaders();
    }
    
    /**
     * @return The max <tt>payload</tt> size to be accepted by this side
     */
    public final int getLocalMaxFramePayloadSize() {
        return localMaxFramePayloadSize;
    }

    /**
     * @return The max <tt>payload</tt> size to be accepted by the peer
     */
    public int getPeerMaxFramePayloadSize() {
        return peerMaxFramePayloadSize;
    }

    /**
     * Sets the max <tt>payload</tt> size to be accepted by the peer.
     * The method is called during the {@link SettingsFrame} processing.
     * 
     * @param peerMaxFramePayloadSize
     * @throws Http2ConnectionException if the peerMaxFramePayloadSize violates the limits
     */
    protected void setPeerMaxFramePayloadSize(final int peerMaxFramePayloadSize)
            throws Http2ConnectionException {
        if (peerMaxFramePayloadSize < getSpecMinFramePayloadSize() ||
                peerMaxFramePayloadSize > getSpecMaxFramePayloadSize()) {
            throw new Http2ConnectionException(ErrorCode.FRAME_SIZE_ERROR);
        }
        this.peerMaxFramePayloadSize = peerMaxFramePayloadSize;
    }
    
    
    boolean canWrite() {
        return outputSink.canWrite();
    }
    
    void notifyCanWrite(final WriteHandler writeHandler) {
        outputSink.notifyCanWrite(writeHandler);
    }
    
    public int getLocalStreamWindowSize() {
        return localStreamWindowSize;
    }

    public void setLocalStreamWindowSize(int localStreamWindowSize) {
        this.localStreamWindowSize = localStreamWindowSize;
    }
    
    public int getPeerStreamWindowSize() {
        return peerStreamWindowSize;
    }
    
    void setPeerStreamWindowSize(final int peerStreamWindowSize) {
        synchronized (sessionLock) {
            final int delta = peerStreamWindowSize - this.peerStreamWindowSize;
            
            this.peerStreamWindowSize = peerStreamWindowSize;
            
            for (Http2Stream stream : streamsMap.values()) {
                try {
                    stream.getOutputSink().onPeerWindowUpdate(delta);
                } catch (Http2StreamException e) {
                    if (LOGGER.isLoggable(LOGGER_LEVEL)) {
                        LOGGER.log(LOGGER_LEVEL, "SpdyStreamException occurred on stream="
                                + stream + " during stream window update", e);
                    }

                    sendRstFrame(e.getErrorCode(), e.getStreamId());
                }
            }
        }
    }

    public int getLocalConnectionWindowSize() {
        return localConnectionWindowSize;
    }

    public void setLocalConnectionWindowSize(final int localConnectionWindowSize) {
        this.localConnectionWindowSize = localConnectionWindowSize;
    }
    
    public int getAvailablePeerConnectionWindowSize() {
        return outputSink.getAvailablePeerConnectionWindowSize();
    }
    
    /**
     * Returns the maximum number of concurrent streams allowed for this session by our side.
     */
    public int getLocalMaxConcurrentStreams() {
        return localMaxConcurrentStreams;
    }

    /**
     * Sets the default maximum number of concurrent streams allowed for this session by our side.
     */
    public void setLocalMaxConcurrentStreams(int localMaxConcurrentStreams) {
        this.localMaxConcurrentStreams = localMaxConcurrentStreams;
    }

    /**
     * Returns the maximum number of concurrent streams allowed for this session by peer.
     */
    public int getPeerMaxConcurrentStreams() {
        return peerMaxConcurrentStreams;
    }

    /**
     * Sets the default maximum number of concurrent streams allowed for this session by peer.
     */
    void setPeerMaxConcurrentStreams(int peerMaxConcurrentStreams) {
        this.peerMaxConcurrentStreams = peerMaxConcurrentStreams;
    }

    
    public int getNextLocalStreamId() {
        lastLocalStreamId += 2;
        return lastLocalStreamId;
    }
    
    public StreamBuilder getStreamBuilder() {
        return streamBuilder;
    }
    
    public Connection getConnection() {
        return connection;
    }

    public MemoryManager getMemoryManager() {
        return connection.getMemoryManager();
    }
    
    public boolean isServer() {
        return isServer;
    }

    public boolean isLocallyInitiatedStream(final int streamId) {
        assert streamId > 0;
        
        return isServer() ^ ((streamId % 2) != 0);
        
//        Same as
//        return isServer() ?
//                (streamId % 2) == 0 :
//                (streamId % 2) == 1;        
    }
    
    Http2State getHttp2State() {
        return http2State;
    }
    
    boolean isHttp2InputEnabled() {
        return isPrefaceReceived;
    }

    boolean isHttp2OutputEnabled() {
        return isPrefaceSent;
    }

    public Http2Stream getStream(final int streamId) {
        return streamsMap.get(streamId);
    }
    
    protected Http2ConnectionOutputSink getOutputSink() {
        return outputSink;
    }
    
    /**
     * If the session is still open - closes it and sends GOAWAY frame to a peer,
     * otherwise if the session was already closed - does nothing.
     * 
     * @param errorCode GOAWAY status code.
     */
    public void goAway(final ErrorCode errorCode) {
        final Http2Frame goAwayFrame = setGoAwayLocally(errorCode);
        if (goAwayFrame != null) {
            outputSink.writeDownStream(goAwayFrame);
        }
    }

    GoAwayFrame setGoAwayLocally(final ErrorCode errorCode) {
        final int lastPeerStreamIdLocal = close();
        if (lastPeerStreamIdLocal == -1) {
            return null; // SpdySession is already in go-away state
        }
        
        return GoAwayFrame.builder()
                .lastStreamId(lastPeerStreamIdLocal)
                .errorCode(errorCode)
                .build();
    }

    protected void sendWindowUpdate(final int streamId, final int delta) {
        outputSink.writeDownStream(
                WindowUpdateFrame.builder()
                        .streamId(streamId)
                        .windowSizeIncrement(delta)
                        .build());
    }
    
    boolean sendPreface() {
        if (!isPrefaceSent) {
            synchronized (sessionLock) {
                if (!isPrefaceSent) {
                    if (isServer) {
                        sendServerPreface();
                    } else {
                        sendClientPreface();
                    }

                    isPrefaceSent = true;
                    
                    if (!isServer) {
                        // If it's HTTP2 client, which uses HTTP/1.1 upgrade mechanism -
                        // it can have unacked user data sent from the server.
                        // So it's right time to ack this data and let the server send
                        // more data if needed.
                        ackConsumedData(getStream(0), 0);
                    }
                    return true;
                }
            }
        }
        
        return false;
    }
    
    protected void sendServerPreface() {
        final SettingsFrame settingsFrame = prepareSettings().build();
        
        if (LOGGER.isLoggable(LOGGER_LEVEL)) {
            LOGGER.log(LOGGER_LEVEL, "Tx: server preface (the settings frame)."
                    + "Connection={0}, settingsFrame={1}",
                    new Object[]{connection, settingsFrame});
        }
        
        // server preface
        connection.write(settingsFrame.toBuffer(this));
    }
    
    protected void sendClientPreface() {
        // send preface (PRI * ....)
        final HttpRequestPacket request =
                HttpRequestPacket.builder()
                .method(Method.PRI)
                .uri("*")
                .protocol(Protocol.HTTP_2_0)
                .build();
        
        final Buffer priPayload =
                Buffers.wrap(connection.getMemoryManager(), PRI_PAYLOAD);
        
        final SettingsFrame settingsFrame = prepareSettings().build();        
        final Buffer settingsBuffer = settingsFrame.toBuffer(this);
        
        final Buffer payload = Buffers.appendBuffers(
                connection.getMemoryManager(), priPayload, settingsBuffer);
        
        final HttpContent content = HttpContent.builder(request)
                .content(payload)
                .build();
        
        if (LOGGER.isLoggable(LOGGER_LEVEL)) {
            LOGGER.log(LOGGER_LEVEL, "Tx: client preface including the settings "
                    + "frame. Connection={0}, settingsFrame={1}",
                    new Object[]{connection, settingsFrame});
        }
        
        connection.write(content);
    }
    
    protected void sendRstFrame(final ErrorCode errorCode, final int streamId) {
        outputSink.writeDownStream(
                RstStreamFrame.builder()
                        .errorCode(errorCode)
                        .streamId(streamId)
                        .build());
    }
    
    HeadersDecoder getHeadersDecoder() {
        if (headersDecoder == null) {
            headersDecoder = new HeadersDecoder(getMemoryManager(), 4096, 4096);
        }
        
        return headersDecoder;
    }

    ReentrantLock getDeflaterLock() {
        return deflaterLock;
    }

    HeadersEncoder getHeadersEncoder() {
        if (headersEncoder == null) {
            headersEncoder = new HeadersEncoder(getMemoryManager(), 4096);
        }
        
        return headersEncoder;
    }

    /**
     * Encodes the {@link HttpHeader} and locks the compression lock.
     * 
     * @param ctx
     * @param httpHeader
     * @param streamId
     * @param isLast
     * @param toList the target {@link List}, to which the frames will be serialized
     * 
     * @return the HTTP2 header frames sequence
     * @throws IOException 
     */
    protected List<Http2Frame> encodeHttpHeaderAsHeaderFrames(
            final FilterChainContext ctx,
            final HttpHeader httpHeader,
            final int streamId,
            final boolean isLast,
            final List<Http2Frame> toList)
            throws IOException {
        
        final Buffer compressedHeaders = !httpHeader.isRequest()
                ? EncoderUtils.encodeResponseHeaders(
                        this, (HttpResponsePacket) httpHeader)
                : EncoderUtils.encodeRequestHeaders(
                        this, (HttpRequestPacket) httpHeader);
        
        final List<Http2Frame> headerFrames =
                bufferToHeaderFrames(streamId, compressedHeaders, isLast, toList);
        
        handlerFilter.onHttpHeadersEncoded(httpHeader, ctx);

        return headerFrames;
    }
    
    /**
     * Encodes the {@link HttpRequestPacket} as a {@link PushPromiseFrame}
     * and locks the compression lock.
     * 
     * @param ctx
     * @param httpRequest
     * @param streamId
     * @param promisedStreamId
     * @param toList the target {@link List}, to which the frames will be serialized
     * @return the HTTP2 push promise frames sequence
     * 
     * @throws IOException 
     */
    protected List<Http2Frame> encodeHttpRequestAsPushPromiseFrames(
            final FilterChainContext ctx,
            final HttpRequestPacket httpRequest,
            final int streamId,
            final int promisedStreamId,
            final List<Http2Frame> toList)
            throws IOException {
        
        final List<Http2Frame> headerFrames =
                bufferToPushPromiseFrames(
                        streamId,
                        promisedStreamId,
                        EncoderUtils.encodeRequestHeaders(this, httpRequest),
                        toList);
        
        handlerFilter.onHttpHeadersEncoded(httpRequest, ctx);

        return headerFrames;
    }
    
    /**
     * Encodes a compressed header buffer as a {@link HeadersFrame} and
     * a sequence of 0 or more {@link ContinuationFrame}s.
     * 
     * @param streamId
     * @param compressedHeaders
     * @param isEos
     * @param toList the {@link List} to which {@link Http2Frame}s will be added
     * @return the result {@link List} with the frames
     */
    private List<Http2Frame> bufferToHeaderFrames(final int streamId,
            final Buffer compressedHeaders, final boolean isEos,
            final List<Http2Frame> toList) {
        final HeadersFrame.HeadersFrameBuilder builder =
                HeadersFrame.builder()
                        .streamId(streamId)
                        .endStream(isEos);
        
        return completeHeadersProviderFrameSerialization(builder,
                streamId, compressedHeaders, toList);
    }
    
    /**
     * Encodes a compressed header buffer as a {@link PushPromiseFrame} and
     * a sequence of 0 or more {@link ContinuationFrame}s.
     * 
     * @param streamId
     * @param promisedStreamId
     * @param compressedHeaders
     * @param toList the {@link List} to which {@link Http2Frame}s will be added
     * @return the result {@link List} with the frames
     */
    private List<Http2Frame> bufferToPushPromiseFrames(final int streamId,
            final int promisedStreamId, final Buffer compressedHeaders,
            final List<Http2Frame> toList) {
        
        final PushPromiseFrame.PushPromiseFrameBuilder builder =
                PushPromiseFrame.builder()
                        .streamId(streamId)
                        .promisedStreamId(promisedStreamId);
        
        return completeHeadersProviderFrameSerialization(builder,
                streamId, compressedHeaders, toList);
    }
    
    /**
     * Completes the {@link AbstractHeadersFrame} sequence serialization.
     * 
     * @param streamId
     * @param compressedHeaders
     * @param toList the {@link List} to which {@link Http2Frame}s will be added
     * @return the result {@link List} with the frames
     */
    private List<Http2Frame> completeHeadersProviderFrameSerialization(
            final HeaderBlockFragment.HeaderBlockFragmentBuilder builder,
            final int streamId,
            final Buffer compressedHeaders,
            List<Http2Frame> toList) {
        // we assume deflaterLock is acquired and held by this thread
        assert deflaterLock.isHeldByCurrentThread();
        
        if (toList == null) {
            toList = tmpHeaderFramesList;
        }
        
        if (compressedHeaders.remaining() <= peerMaxFramePayloadSize) {
            toList.add(
                    builder.endHeaders(true)
                    .compressedHeaders(compressedHeaders)
                    .build()
            );

            return toList;
        }
        
        Buffer remainder = compressedHeaders.split(
                compressedHeaders.position() + peerMaxFramePayloadSize);
        
        toList.add(
                builder.endHeaders(false)
                .compressedHeaders(compressedHeaders)
                .build());
        
        assert remainder != null;
        
        do {
            final Buffer buffer = remainder;
            
            remainder = buffer.remaining() <= peerMaxFramePayloadSize
                    ? null
                    : buffer.split(buffer.position() + peerMaxFramePayloadSize);

            toList.add(
                    ContinuationFrame.builder().streamId(streamId)
                    .endHeaders(remainder == null)
                    .compressedHeaders(buffer)
                    .build());
        } while (remainder != null);
        
        return toList;
    }
    
    /**
     * The {@link ReentrantLock}, which assures that requests assigned to newly
     * allocated stream IDs will be sent to the server in their order.
     * So that request associated with the stream ID '5' won't be sent before
     * the request associated with the stream ID '3' etc.
     * 
     * @return the {@link ReentrantLock}
     */
    public ReentrantLock getNewClientStreamLock() {
        return newClientStreamLock;
    }

    Http2Stream acceptStream(final HttpRequestPacket request,
            final int streamId, final int refStreamId, 
            final int priority)
            throws Http2ConnectionException {
        
        final Http2Stream stream = newStream(request,
                streamId, refStreamId,
                priority);
        
        synchronized(sessionLock) {
            if (isClosed()) {
                return null; // if the session is closed is set - return null to ignore stream creation
            }
            
            if (streamsMap.size() >= getLocalMaxConcurrentStreams()) {
                // throw Session level exception because headers were not decompressed,
                // so compression context is lost
                throw new Http2ConnectionException(ErrorCode.REFUSED_STREAM);
            }
            
            streamsMap.put(streamId, stream);
            lastPeerStreamId = streamId;
        }
        
        return stream;
    }

    /**
     * Method is not thread-safe, it is expected that it will be called
     * within {@link #getNewClientStreamLock()} lock scope.
     * The caller code is responsible for obtaining and releasing the mentioned
     * {@link #getNewClientStreamLock()} lock.
     * @param request
     * @param streamId
     * @param refStreamId
     * @param priority
     * @param fin
     * @return 
     * @throws org.glassfish.grizzly.http2.Http2StreamException 
     */
    public Http2Stream openStream(final HttpRequestPacket request,
            final int streamId, final int refStreamId, 
            final int priority, final boolean fin)
            throws Http2StreamException {
        
        request.setExpectContent(!fin);
        final Http2Stream stream = newStream(request,
                streamId, refStreamId,
                priority);
        
        synchronized(sessionLock) {
            if (isClosed()) {
                throw new Http2StreamException(streamId,
                        ErrorCode.REFUSED_STREAM, "Session is closed");
            }
            
            if (streamsMap.size() >= getLocalMaxConcurrentStreams()) {
                throw new Http2StreamException(streamId, ErrorCode.REFUSED_STREAM);
            }
            
            if (refStreamId > 0) {
                final Http2Stream mainStream = getStream(refStreamId);
                if (mainStream == null) {
                    throw new Http2StreamException(streamId, ErrorCode.REFUSED_STREAM,
                            "The parent stream does not exist");
                }
                
//                mainStream.addAssociatedStream(stream);
            }
            
            streamsMap.put(streamId, stream);
            lastLocalStreamId = streamId;
        }
        
        return stream;
    }

    /**
     * Method is not thread-safe, it is expected that it will be called
     * within {@link #getNewClientStreamLock()} lock scope.
     * The caller code is responsible for obtaining and releasing the mentioned
     * {@link #getNewClientStreamLock()} lock.
     * @param request
     * @param priority
     * @param fin
     * @return 
     * @throws org.glassfish.grizzly.http2.Http2StreamException 
     */
    public Http2Stream openUpgradeStream(final HttpRequestPacket request,
            final int priority, final boolean fin)
            throws Http2StreamException {
        
        request.setExpectContent(!fin);
        final Http2Stream stream = newUpgradeStream(request, priority);
        
        synchronized(sessionLock) {
            if (isClosed()) {
                throw new Http2StreamException(Http2Stream.UPGRADE_STREAM_ID,
                        ErrorCode.REFUSED_STREAM, "Session is closed");
            }
            
            streamsMap.put(Http2Stream.UPGRADE_STREAM_ID, stream);
            lastLocalStreamId = Http2Stream.UPGRADE_STREAM_ID;
        }
        
        return stream;
    }
    
    /**
     * Initializes HTTP2 communication (if not initialized before) by forming
     * HTTP2 connection and stream {@link FilterChain}s.
     * 
     * @param ctx {@link FilterChainContext}
     * @param isUpStream 
     */
    boolean setupFilterChains(final FilterChainContext context,
            final boolean isUpStream) {
        
        if (http2ConnectionChain == null) {
            synchronized(this) {
                if (http2ConnectionChain == null) {
                    if (isUpStream) {
                        http2StreamChain = (FilterChain) context.getFilterChain().subList(
                                context.getFilterIdx(), context.getEndIdx());

                        http2ConnectionChain = (FilterChain) context.getFilterChain().subList(
                                context.getStartIdx(), context.getFilterIdx());
                    } else {
                        http2StreamChain = (FilterChain) context.getFilterChain().subList(
                                context.getFilterIdx(), context.getFilterChain().size());

                        http2ConnectionChain = (FilterChain) context.getFilterChain().subList(
                                context.getEndIdx() + 1, context.getFilterIdx());
                    }
                    
                    return true;
                }
            }
        }
        
        return false;
    }
    
    FilterChain getHttp2StreamChain() {
        return http2StreamChain;
    }
    
    FilterChain getHttp2ConnectionChain() {
        return http2ConnectionChain;
    }
    
    /**
     * Method is called, when the session closing is initiated locally.
     */
    private int close() {
        synchronized (sessionLock) {
            if (isClosed()) {
                return -1;
            }
            
            closeFlag = CloseType.LOCALLY;
            return lastPeerStreamId > 0 ? lastPeerStreamId : 0;
        }
    }
    
    /**
     * Method is called, when GOAWAY is initiated by peer
     */
    void setGoAwayByPeer(final int lastGoodStreamId) {
        synchronized (sessionLock) {
            // @TODO Notify pending SYNC_STREAMS if streams were aborted
            closeFlag = CloseType.REMOTELY;
        }
    }
    
    Object getSessionLock() {
        return sessionLock;
    }

    /**
     * Called from {@link Http2Stream} once stream is completely closed.
     */
    void deregisterStream(final Http2Stream spdyStream) {
        streamsMap.remove(spdyStream.getId());
        
        final boolean isCloseSession;
        synchronized (sessionLock) {
            // If we're in GOAWAY state and there are no streams left - close this session
            isCloseSession = isClosed() && streamsMap.isEmpty();
        }
        
        if (isCloseSession) {
            closeSession();
        }
    }

    /**
     * Close the session
     */
    private void closeSession() {
        connection.closeSilently();
        outputSink.close();
    }

    private boolean isClosed() {
        return closeFlag != null;
    }

    void sendMessageUpstreamWithParseNotify(final Http2Stream stream,
                                            final HttpContent httpContent) {
        final FilterChainContext upstreamContext =
                        http2StreamChain.obtainFilterChainContext(connection, stream);

        final HttpContext httpContext = httpContent.getHttpHeader()
                .getProcessingState().getHttpContext();
        httpContext.attach(upstreamContext);
        
        handlerFilter.onHttpContentParsed(httpContent, upstreamContext);
        final HttpHeader header = httpContent.getHttpHeader();
        if (httpContent.isLast()) {
            handlerFilter.onHttpPacketParsed(header, upstreamContext);
        }

        if (header.isSkipRemainder()) {
            return;
        }

        sendMessageUpstream(stream, httpContent, upstreamContext);
    }

    void sendMessageUpstream(final Http2Stream stream,
                             final HttpPacket message) {
        final FilterChainContext upstreamContext =
                http2StreamChain.obtainFilterChainContext(connection, stream);
        
        final HttpContext httpContext = message.getHttpHeader()
                .getProcessingState().getHttpContext();
        httpContext.attach(upstreamContext);
        
        sendMessageUpstream(stream, message, upstreamContext);
    }

    private void sendMessageUpstream(final Http2Stream stream,
                                     final HttpPacket message,
                                     final FilterChainContext upstreamContext) {

        upstreamContext.getInternalContext().setIoEvent(IOEvent.READ);
        upstreamContext.getInternalContext().addLifeCycleListener(
                new IOEventLifeCycleListener.Adapter() {
                    @Override
                    public void onReregister(final Context context) throws IOException {
                        stream.inputBuffer.onReadEventComplete();
                    }

                    @Override
                    public void onComplete(Context context, Object data) throws IOException {
                        stream.inputBuffer.onReadEventComplete();
                    }
                });

        upstreamContext.setMessage(message);
        upstreamContext.setAddressHolder(addressHolder);

        ProcessorExecutor.execute(upstreamContext.getInternalContext());
    }

    protected SettingsFrameBuilder prepareSettings() {
        return prepareSettings(null);
    }

    protected SettingsFrameBuilder prepareSettings(
            SettingsFrameBuilder builder) {
        
        if (builder == null) {
            builder = SettingsFrame.builder();
        }
        
        if (getLocalMaxConcurrentStreams() != getDefaultMaxConcurrentStreams()) {
            builder.setting(SETTINGS_MAX_CONCURRENT_STREAMS, getLocalMaxConcurrentStreams());
        }

        if (getLocalStreamWindowSize() != getDefaultStreamWindowSize()) {
            builder.setting(SETTINGS_INITIAL_WINDOW_SIZE, getLocalStreamWindowSize());
        }
        
        return builder;
    }

    /**
     * Acknowledge that certain amount of data has been read.
     * Depending on the total amount of un-acknowledge data the HTTP2 connection
     * can decide to send a window_update message to the peer.
     * 
     * @param sz 
     */
    void ackConsumedData(final int sz) {
        ackConsumedData(null, sz);
    }
    /**
     * Acknowledge that certain amount of data has been read.
     * Depending on the total amount of un-acknowledge data the HTTP2 connection
     * can decide to send a window_update message to the peer.
     * Unlike the {@link #ackConsumedData(int)}, this method also requests an
     * HTTP2 stream to acknowledge consumed data to the peer.
     * 
     * @param stream
     * @param sz 
     */
    void ackConsumedData(final Http2Stream stream, final int sz) {
        final int currentUnackedBytes
                = unackedReadBytes.addAndGet(sz);
        
        if (isPrefaceSent) {
            // ACK HTTP2 connection flow control
            final int windowSize = getLocalConnectionWindowSize();

            // if not forced - send update window message only in case currentUnackedBytes > windowSize / 2
            if (currentUnackedBytes > (windowSize / 3)
                    && unackedReadBytes.compareAndSet(currentUnackedBytes, 0)) {

                sendWindowUpdate(0, currentUnackedBytes);
            }
            
            if (stream != null) {
                // ACK HTTP2 stream flow control
                final int streamUnackedBytes
                        = stream.unackedReadBytes.addAndGet(sz);
                final int streamWindowSize = stream.getLocalWindowSize();

                // send update window message only in case currentUnackedBytes > windowSize / 2
                if (streamUnackedBytes > 0
                        && (streamUnackedBytes > (streamWindowSize / 2))
                        && stream.unackedReadBytes.compareAndSet(streamUnackedBytes, 0)) {

                    sendWindowUpdate(stream.getId(), streamUnackedBytes);
                }
            }
        }
    }

    public final class StreamBuilder {

        private StreamBuilder() {
        }
        
        public RegularStreamBuilder regular() {
            return new RegularStreamBuilder();
        }
        
        public PushStreamBuilder push() {
            return new PushStreamBuilder();
        }
    }
    
    public final class PushStreamBuilder extends HttpHeader.Builder<PushStreamBuilder> {

        private int associatedToStreamId;
        private int priority;
        private boolean isFin;
        private String uri;
        private String query;
        
        /**
         * Set the request URI.
         *
         * @param uri the request URI.
         */
        public PushStreamBuilder uri(final String uri) {
            this.uri = uri;
            return this;
        }

        /**
         * Set the <code>query</code> portion of the request URI.
         *
         * @param query the query String
         *
         * @return the current <code>Builder</code>
         */
        public PushStreamBuilder query(final String query) {
            this.query = query;
            return this;
        }

        /**
         * Set the <code>associatedToStreamId</code> parameter of a {@link Http2Stream}.
         *
         * @param associatedToStreamId the associatedToStreamId
         *
         * @return the current <code>Builder</code>
         */
        public PushStreamBuilder associatedToStreamId(final int associatedToStreamId) {
            this.associatedToStreamId = associatedToStreamId;
            return this;
        }

        /**
         * Set the <code>priority</code> parameter of a {@link Http2Stream}.
         *
         * @param priority the priority
         *
         * @return the current <code>Builder</code>
         */
        public PushStreamBuilder priority(final int priority) {
            this.priority = priority;
            return this;
        }
        
        /**
         * Sets the <code>fin</code> flag of a {@link Http2Stream}.
         * 
         * @param fin
         * 
         * @return the current <code>Builder</code>
         */
        public PushStreamBuilder fin(final boolean fin) {
            this.isFin = fin;
            return this;
        }
        
        /**
         * Build the <tt>HttpRequestPacket</tt> message.
         *
         * @return <tt>HttpRequestPacket</tt>
         */
        @SuppressWarnings("unchecked")
        public final Http2Stream open() throws Http2StreamException {
            final Http2Request request = build();
            newClientStreamLock.lock();
            try {
                final Http2Stream stream = openStream(
                        request,
                        getNextLocalStreamId(),
                        associatedToStreamId, priority, isFin);
                
                
                connection.write(request.getResponse());
                
                return stream;
            } finally {
                newClientStreamLock.unlock();
            }
        }

        @Override
        public Http2Request build() {
            Http2Request request = (Http2Request) super.build();
            if (uri != null) {
                request.setRequestURI(uri);
            }
            if (query != null) {
                request.setQueryString(query);
            }
            return request;
        }

        @Override
        protected HttpHeader create() {
            Http2Request request = Http2Request.create();
            HttpResponsePacket packet = request.getResponse();
            packet.setSecure(true);
            return request;
        }
    }
    
    public final class RegularStreamBuilder extends HttpHeader.Builder<RegularStreamBuilder> {
        private int priority;
        private boolean isFin;
        private Method method;
        private String methodString;
        private String uri;
        private String query;
        private String host;
        

        /**
         * Set the HTTP request method.
         * @param method the HTTP request method..
         */
        public RegularStreamBuilder method(final Method method) {
            this.method = method;
            methodString = null;
            return this;
        }

        /**
         * Set the HTTP request method.
         * @param methodString the HTTP request method. Format is "GET|POST...".
         */
        public RegularStreamBuilder method(final String methodString) {
            this.methodString = methodString;
            method = null;
            return this;
        }

        /**
         * Set the request URI.
         *
         * @param uri the request URI.
         */
        public RegularStreamBuilder uri(final String uri) {
            this.uri = uri;
            return this;
        }

        /**
         * Set the <code>query</code> portion of the request URI.
         *
         * @param query the query String
         *
         * @return the current <code>Builder</code>
         */
        public RegularStreamBuilder query(final String query) {
            this.query = query;
            return this;
        }

        /**
         * Set the value of the Host header.
         *
         * @param host the value of the Host header.
         *
         * @return this;
         */
        public RegularStreamBuilder host(final String host) {
            this.host = host;
            return this;
        }

        /**
         * Set the <code>priority</code> parameter of a {@link Http2Stream}.
         *
         * @param priority the priority
         *
         * @return the current <code>Builder</code>
         */
        public RegularStreamBuilder priority(final int priority) {
            this.priority = priority;
            return this;
        }

        /**
         * Sets the <code>fin</code> flag of a {@link Http2Stream}.
         * 
         * @param fin
         * 
         * @return the current <code>Builder</code>
         */
        public RegularStreamBuilder fin(final boolean fin) {
            this.isFin = fin;
            return this;
        }
        
        /**
         * Build the <tt>HttpRequestPacket</tt> message.
         *
         * @return <tt>HttpRequestPacket</tt>
         */
        @SuppressWarnings("unchecked")
        public final Http2Stream open() throws Http2StreamException {
            Http2Request request = build();
            newClientStreamLock.lock();
            try {
                final Http2Stream stream = openStream(
                        request,
                        getNextLocalStreamId(),
                        0, priority, isFin);
                
                
                connection.write(request);
                
                return stream;
            } finally {
                newClientStreamLock.unlock();
            }
        }

        @Override
        public Http2Request build() {
            Http2Request request = (Http2Request) super.build();
            if (method != null) {
                request.setMethod(method);
            }
            if (methodString != null) {
                request.setMethod(methodString);
            }
            if (uri != null) {
                request.setRequestURI(uri);
            }
            if (query != null) {
                request.setQueryString(query);
            }
            if (host != null) {
                request.addHeader(Header.Host, host);
            }
            return request;
        }

        @Override
        protected HttpHeader create() {
            Http2Request request = Http2Request.create();
            request.setSecure(true);
            return request;
        }
    }
    
    private final class ConnectionCloseListener implements GenericCloseListener {

        @Override
        public void onClosed(final Closeable closeable, final CloseType type)
                throws IOException {
            
            final boolean isClosing;
            synchronized (sessionLock) {
                isClosing = !isClosed();
                if (isClosing) {
                    closeFlag = type;
                }
            }
            
            if (isClosing) {
                for (Http2Stream stream : streamsMap.values()) {
                    stream.closedRemotely();
                }
            }
        }
    }
}
