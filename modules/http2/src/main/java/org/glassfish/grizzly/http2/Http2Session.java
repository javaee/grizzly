/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012-2017 Oracle and/or its affiliates. All rights reserved.
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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.CloseListener;
import org.glassfish.grizzly.CloseType;
import org.glassfish.grizzly.Closeable;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Context;
import org.glassfish.grizzly.EmptyCompletionHandler;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.IOEvent;
import org.glassfish.grizzly.IOEventLifeCycleListener;
import org.glassfish.grizzly.ProcessorExecutor;
import org.glassfish.grizzly.WriteResult;
import org.glassfish.grizzly.filterchain.FilterChain;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.http.util.MimeHeaders;
import org.glassfish.grizzly.http2.frames.DataFrame;
import org.glassfish.grizzly.http2.frames.PingFrame;
import org.glassfish.grizzly.http2.frames.PriorityFrame;
import org.glassfish.grizzly.http2.frames.UnknownFrame;
import org.glassfish.grizzly.impl.FutureImpl;
import org.glassfish.grizzly.memory.Buffers;
import org.glassfish.grizzly.http.HttpContent;
import org.glassfish.grizzly.http.HttpContext;
import org.glassfish.grizzly.http.HttpHeader;
import org.glassfish.grizzly.http.HttpPacket;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.http.HttpResponsePacket;
import org.glassfish.grizzly.http.Method;
import org.glassfish.grizzly.http.Protocol;

import org.glassfish.grizzly.http2.frames.ContinuationFrame;
import org.glassfish.grizzly.http2.frames.ErrorCode;
import org.glassfish.grizzly.http2.frames.GoAwayFrame;
import org.glassfish.grizzly.http2.frames.HeadersFrame;
import org.glassfish.grizzly.http2.frames.Http2Frame;
import org.glassfish.grizzly.http2.frames.PushPromiseFrame;
import org.glassfish.grizzly.http2.frames.RstStreamFrame;
import org.glassfish.grizzly.http2.frames.SettingsFrame;
import org.glassfish.grizzly.memory.MemoryManager;
import org.glassfish.grizzly.ssl.SSLBaseFilter;
import org.glassfish.grizzly.utils.Futures;
import org.glassfish.grizzly.utils.Holder;
import org.glassfish.grizzly.utils.NullaryFunction;

import static org.glassfish.grizzly.http2.frames.SettingsFrame.*;
import static org.glassfish.grizzly.http2.Http2BaseFilter.PRI_PAYLOAD;
import org.glassfish.grizzly.http2.frames.HeaderBlockFragment;
import org.glassfish.grizzly.http2.frames.WindowUpdateFrame;


/**
 * The HTTP2 session abstraction.
 * 
 * @author Alexey Stashok
 */
public class Http2Session {
    private static final Logger LOGGER = Grizzly.logger(Http2Session.class);

    private final boolean isServer;
    private final Connection<?> connection;
    Http2State http2State;
    
    private HeadersDecoder headersDecoder;
    private HeadersEncoder headersEncoder;

    private final ReentrantLock deflaterLock = new ReentrantLock();
    
    private int lastPeerStreamId;
    private int lastLocalStreamId;
    private boolean pushEnabled = true;

    private final ReentrantLock newClientStreamLock = new ReentrantLock();
    
    private volatile FilterChain http2StreamChain;
    private volatile FilterChain htt2SessionChain;

    private static final AtomicIntegerFieldUpdater<Http2Session> concurrentStreamCountUpdater =
            AtomicIntegerFieldUpdater.newUpdater(Http2Session.class, "concurrentStreamsCount");
    @SuppressWarnings("unused")
    private volatile int concurrentStreamsCount;

    private final TreeMap<Integer, Http2Stream> streamsMap = new TreeMap<>();
    
    // (Optimization) We may read several DataFrames belonging to the same
    // Http2Stream, so in order to not process every DataFrame separately -
    // we buffer them and only then passing for processing.
    final List<Http2Stream> streamsToFlushInput = new ArrayList<>();
    
    // The List object used to store header frames. Could be used by
    // Http2Session streams, when they write headers
    protected final List<Http2Frame> tmpHeaderFramesList =
            new ArrayList<>(2);
    
    private final Object sessionLock = new Object();
    
    private volatile CloseType closeFlag;
    
    private int peerStreamWindowSize = getDefaultStreamWindowSize();
    private volatile int localStreamWindowSize = getDefaultStreamWindowSize();
    
    private volatile int localConnectionWindowSize = getDefaultConnectionWindowSize();

    private volatile int maxHeaderListSize;
    
    private volatile int localMaxConcurrentStreams = getDefaultMaxConcurrentStreams();
    private int peerMaxConcurrentStreams = getDefaultMaxConcurrentStreams();

    private final Http2SessionOutputSink outputSink;

    private final Http2Configuration http2Configuration;

    private volatile int streamsHighWaterMark;
    private int checkCount;

    private int goingAwayLastStreamId = Integer.MIN_VALUE;
    private FutureImpl<Http2Session> sessionClosed;

    // true, if this connection is ready to accept frames or false if the first
    // HTTP/1.1 Upgrade is still in progress
    private volatile boolean isPrefaceReceived;
    private volatile boolean isPrefaceSent;
    
    public static Http2Session get(final Connection connection) {
        final Http2State http2State = Http2State.get(connection);
        return http2State != null
                ? http2State.getHttp2Session()
                : null;
    }
    
    static void bind(final Connection connection,
            final Http2Session http2Session) {
        Http2State.obtain(connection).setHttp2Session(http2Session);
    }
    
    private final Holder<?> addressHolder;

    final Http2BaseFilter handlerFilter;

    private final int localMaxFramePayloadSize;
    private int peerMaxFramePayloadSize = getSpecDefaultFramePayloadSize();
    
    private boolean isFirstInFrame = true;
    private volatile SSLBaseFilter sslFilter;
    
    private final AtomicInteger unackedReadBytes  = new AtomicInteger();
        
    public Http2Session(final Connection<?> connection,
                        final boolean isServer,
                        final Http2BaseFilter handlerFilter) {
        this.connection = connection;
        final FilterChain chain = (FilterChain) connection.getProcessor();
        final int sslIdx = chain.indexOfType(SSLBaseFilter.class);
        if (sslIdx != -1) {
            sslFilter = (SSLBaseFilter) chain.get(sslIdx);
        }
        this.isServer = isServer;
        this.handlerFilter = handlerFilter;

        this.http2Configuration = handlerFilter.getConfiguration();
        streamsHighWaterMark =
                Float.valueOf(
                        getDefaultMaxConcurrentStreams() * http2Configuration.getStreamsHighWaterMark())
                        .intValue();
        
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

        maxHeaderListSize = handlerFilter.getConfiguration().getMaxHeaderListSize();

        if (isServer) {
            lastLocalStreamId = 0;
            lastPeerStreamId = -1;
        } else {
            lastLocalStreamId = -1;
            lastPeerStreamId = 0;
        }
        
        addressHolder = Holder.lazyHolder(new NullaryFunction<Object>() {
            @Override
            public Object evaluate() {
                return connection.getPeerAddress();
            }
        });
        
        connection.addCloseListener(new ConnectionCloseListener());
        
        this.outputSink = newOutputSink();

        NetLogger.logOpen(this);
    }

    protected Http2SessionOutputSink newOutputSink() {
        return new Http2SessionOutputSink(this);
    }

    protected int getSpecDefaultFramePayloadSize() {
        return 16384; //2^14
    }

    protected int getSpecMinFramePayloadSize() {
        return 16384; //2^14
    }

    protected int getSpecMaxFramePayloadSize() {
        return 0xffffff; // 2^24-1 (16,777,215)
    }

    public int getDefaultConnectionWindowSize() {
        return 65535;
    }

    public int getDefaultStreamWindowSize() {
        return 65535;
    }

    public int getDefaultMaxConcurrentStreams() {
        return 100;
    }

    /**
     * @return the maximum size, in bytes, of header list.  If not explicitly configured, the default of
     *  <code>8192</code> is used.
     */
    public int getMaxHeaderListSize() {
        return maxHeaderListSize;
    }

    /**
     * Set the maximum size, in bytes, of the header list.
     *
     * @param maxHeaderListSize size, in bytes, of the header list.
     */
    @SuppressWarnings("unused")
    public void setMaxHeaderListSize(int maxHeaderListSize) {
        this.maxHeaderListSize = maxHeaderListSize;
    }

    /**
     * Returns the total frame size (including header size), or <tt>-1</tt>
     * if the buffer doesn't contain enough bytes to read the size.
     *
     * @param buffer the buffer containing the frame data
     *
     * @return the total frame size (including header size), or <tt>-1</tt>
     * if the buffer doesn't contain enough bytes to read the size
     */
    protected int getFrameSize(final Buffer buffer) {
        return buffer.remaining() < 4 // even though we need just 3 bytes - we require 4 for simplicity
                ? -1
                : (buffer.getInt(buffer.position()) >>> 8) + Http2Frame.FRAME_HEADER_SIZE;
    }

    public Http2Frame parseHttp2FrameHeader(final Buffer buffer)
            throws Http2SessionException {
        // we assume the passed buffer represents only this frame, no remainders allowed
        final int len = getFrameSize(buffer);
        assert buffer.remaining() == len;

        final int i1 = buffer.getInt();

        final int type =  i1 & 0xff;

        final int flags = buffer.get() & 0xff;
        final int streamId = buffer.getInt() & 0x7fffffff;

        switch (type) {
            case DataFrame.TYPE:
                return DataFrame.fromBuffer(flags, streamId, buffer)
                        .normalize(); // remove padding
            case HeadersFrame.TYPE:
                return HeadersFrame.fromBuffer(flags, streamId, buffer)
                        .normalize(); // remove padding
            case PriorityFrame.TYPE:
                return PriorityFrame.fromBuffer(streamId, buffer);
            case RstStreamFrame.TYPE:
                return RstStreamFrame.fromBuffer(flags, streamId, buffer);
            case SettingsFrame.TYPE:
                return SettingsFrame.fromBuffer(flags, buffer);
            case PushPromiseFrame.TYPE:
                return PushPromiseFrame.fromBuffer(flags, streamId, buffer);
            case PingFrame.TYPE:
                return PingFrame.fromBuffer(flags, buffer);
            case GoAwayFrame.TYPE:
                return GoAwayFrame.fromBuffer(buffer);
            case WindowUpdateFrame.TYPE:
                return WindowUpdateFrame.fromBuffer(flags, streamId, buffer);
            case ContinuationFrame.TYPE:
                return ContinuationFrame.fromBuffer(flags, streamId, buffer);
            default:
                return new UnknownFrame(type, len);
        }
    }

    protected Http2Stream newStream(final HttpRequestPacket request,
            final int streamId, final int refStreamId,
            final boolean exclusive, final int priority) {
        
        return new Http2Stream(this, request, streamId, refStreamId,
                               exclusive, priority);
    }

    protected Http2Stream newUpgradeStream(final HttpRequestPacket request, final int priority) {
        
        return new Http2Stream(this, request, priority);
    }

    protected void checkFrameSequenceSemantics(final Http2Frame frame)
            throws Http2SessionException {
        
        final int frameType = frame.getType();
        
        if (isFirstInFrame) {
            if (frameType != SettingsFrame.TYPE) {
                if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.log(Level.FINE, "First in frame should be a SettingsFrame (preface)", frame);
                }
                
                throw new Http2SessionException(ErrorCode.PROTOCOL_ERROR);
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
                if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.log(Level.FINE, "ContinuationFrame is expected, but {0} came", frame);
                }

                throw new Http2SessionException(ErrorCode.PROTOCOL_ERROR);
            }
        } else if (frameType == ContinuationFrame.TYPE) {
            // isParsing == false, so no ContinuationFrame expected
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.log(Level.FINE, "ContinuationFrame is not expected");
            }

            throw new Http2SessionException(ErrorCode.PROTOCOL_ERROR);
        }
    }
    
    protected void onOversizedFrame(final Buffer buffer)
            throws Http2SessionException {
        
        final int oldPos = buffer.position();
        try {
            throw new Http2SessionException(ErrorCode.FRAME_SIZE_ERROR);
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
    @SuppressWarnings("unused")
    public int getPeerMaxFramePayloadSize() {
        return peerMaxFramePayloadSize;
    }

    /**
     * Sets the max <tt>payload</tt> size to be accepted by the peer.
     * The method is called during the {@link SettingsFrame} processing.
     * 
     * @param peerMaxFramePayloadSize max payload size accepted by the peer.
     * @throws Http2SessionException if the peerMaxFramePayloadSize violates the limits
     */
    protected void setPeerMaxFramePayloadSize(final int peerMaxFramePayloadSize)
            throws Http2SessionException {
        if (peerMaxFramePayloadSize < getSpecMinFramePayloadSize() ||
                peerMaxFramePayloadSize > getSpecMaxFramePayloadSize()) {
            throw new Http2SessionException(ErrorCode.FRAME_SIZE_ERROR);
        }
        this.peerMaxFramePayloadSize = peerMaxFramePayloadSize;
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

            for (final Http2Stream stream : streamsMap.values()) {
                if (stream.isClosed()) {
                    continue;
                }
                try {
                    stream.getOutputSink().onPeerWindowUpdate(delta);
                } catch (Http2StreamException e) {
                    if (LOGGER.isLoggable(Level.SEVERE)) {
                        LOGGER.log(Level.SEVERE, "Http2StreamException occurred on stream="
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

    @SuppressWarnings("unused")
    public void setLocalConnectionWindowSize(final int localConnectionWindowSize) {
        this.localConnectionWindowSize = localConnectionWindowSize;
    }
    
    @SuppressWarnings("unused")
    public int getAvailablePeerConnectionWindowSize() {
        return outputSink.getAvailablePeerConnectionWindowSize();
    }
    
    /**
     * @return the maximum number of concurrent streams allowed for this session by our side.
     */
    public int getLocalMaxConcurrentStreams() {
        return localMaxConcurrentStreams;
    }

    /**
     * Sets the default maximum number of concurrent streams allowed for this session by our side.
     * @param localMaxConcurrentStreams max number of streams locally allowed
     */
    public void setLocalMaxConcurrentStreams(int localMaxConcurrentStreams) {
        this.localMaxConcurrentStreams = localMaxConcurrentStreams;
        streamsHighWaterMark = Float.valueOf(localMaxConcurrentStreams * 0.5f).intValue();
    }

    /**
     * @return the maximum number of concurrent streams allowed for this session by peer.
     */
    @SuppressWarnings("unused")
    public int getPeerMaxConcurrentStreams() {
        return peerMaxConcurrentStreams;
    }

    /**
     * Sets the default maximum number of concurrent streams allowed for this session by peer.
     */
    void setPeerMaxConcurrentStreams(int peerMaxConcurrentStreams) {
        this.peerMaxConcurrentStreams = peerMaxConcurrentStreams;
    }

    /**
     * @return <code>true</code> if push is enabled for this {@link Http2Session}, otherwise
     *  returns <code>false</code>.  Push is enabled by default.
     */
    public boolean isPushEnabled() {
        return pushEnabled;
    }

    /**
     * Configure whether or not push is enabled on this {@link Http2Session}.
     *
     * @param pushEnabled flag toggling push support.
     */
    public void setPushEnabled(final boolean pushEnabled) {
        if (isGoingAway()) {
            return;
        }
        this.pushEnabled = pushEnabled;
    }

    
    public int getNextLocalStreamId() {
        lastLocalStreamId += 2;
        return lastLocalStreamId;
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
    
    protected Http2SessionOutputSink getOutputSink() {
        return outputSink;
    }

    /**
     * TODO
     */
    FutureImpl<Http2Session> terminateGracefully() {
        if (!isServer) {
            throw new IllegalStateException("Illegal use of graceful termination on client.");
        }
        final GoAwayFrame frame = setGoAwayLocally(ErrorCode.NO_ERROR, "Shutting Down", true);
        if (frame != null) {
            sessionClosed = Futures.createSafeFuture();
            outputSink.writeDownStream(frame);
        }
        return sessionClosed;
    }
    
    /**
     * Terminate the HTTP2 session sending a GOAWAY frame using the specified
     * error code and optional detail.  Once the GOAWAY frame is on the wire, the
     * underlying TCP connection will be closed.
     *
     * @param errorCode an RFC 7540 error code.
     * @param detail optional details.
     */
    void terminate(final ErrorCode errorCode, final String detail) {
        sendGoAwayAndClose(setGoAwayLocally(errorCode, detail, false));
    }

    private void sendGoAwayAndClose(final Http2Frame frame) {
        if (frame != null) {
            outputSink.writeDownStream(frame, new EmptyCompletionHandler<WriteResult>() {

                private void close() {
                    connection.closeSilently();
                    outputSink.close();
                }

                @Override
                public void failed(final Throwable throwable) {
                    if (LOGGER.isLoggable(Level.FINE)) {
                        LOGGER.log(Level.FINE, "Unable to write GOAWAY.  Terminating session.", throwable);
                    }
                    close();
                }

                @Override
                public void completed(final WriteResult result) {
                    close();
                }

                @Override
                public void cancelled() {
                    if (LOGGER.isLoggable(Level.FINE)) {
                        LOGGER.log(Level.FINE, "GOAWAY write cancelled.  Terminating session.");
                    }
                    close();
                }
            }, null);
        }
    }

    private GoAwayFrame setGoAwayLocally(final ErrorCode errorCode,
                                         final String detail,
                                         final boolean graceful) {
        synchronized (sessionLock) {

            // If sending a GOAWAY for the first time, goingAwayLastStreamId will be
            // Integer.MIN_VALUE.  It won't be possible to send another GOAWAY frame
            // Unless a graceful session termination was attempted.  In this case,
            // the value will be Integer.MAX_VALUE and it will be possible to send
            // another GO_AWAY if a change of state is necessary i.e., the graceful
            // termination timed out and the session is being forcefully terminated.
            if (goingAwayLastStreamId == Integer.MIN_VALUE
                    || (goingAwayLastStreamId == Integer.MAX_VALUE && !graceful)) {
                closeFlag = CloseType.LOCALLY;
                goingAwayLastStreamId = ((graceful)
                        ? Integer.MAX_VALUE
                        : ((lastPeerStreamId > 0)
                        ? lastPeerStreamId
                        : 0));
                if (goingAwayLastStreamId != Integer.MAX_VALUE) {
                    if (concurrentStreamsCount != 0) {
                        pruneStreams();
                    }
                }
                return GoAwayFrame.builder()
                        .lastStreamId(goingAwayLastStreamId)
                        .additionalDebugData(((detail != null)
                                ? Buffers.wrap(getMemoryManager(), detail)
                                : null))
                        .errorCode(errorCode)
                        .build();

            }
            return null; // already in GOAWAY state.
        }
    }

    // Must be locked by sessionLock
    private void pruneStreams() {
        // close streams that rank above the last stream ID specified by the GOAWAY frame.
        // Allow other streams to continue processing.  Once the concurrent stream count reaches zero,
        // the session will be closed.
        Map<Integer, Http2Stream> invalidStreams =
                streamsMap.subMap(goingAwayLastStreamId,
                        false,
                        Integer.MAX_VALUE,
                        true);
        if (!invalidStreams.isEmpty()) {
            for (final Http2Stream stream : invalidStreams.values()) {
                stream.closedRemotely();
                deregisterStream();
            }
        }
    }

    /**
     * Method is called, when GOAWAY is initiated by peer
     */
    void setGoAwayByPeer(final int lastStreamId) {
        synchronized (sessionLock) {
            pushEnabled = false;
            goingAwayLastStreamId = lastStreamId;
            closeFlag = CloseType.REMOTELY;
            pruneStreams();
            if (isServer || lastStreamId != Integer.MAX_VALUE) {
                sendGoAwayAndClose(GoAwayFrame.builder()
                        .lastStreamId(goingAwayLastStreamId)
                        .additionalDebugData(Buffers.wrap(getMemoryManager(), "Peer Requested."))
                        .errorCode(ErrorCode.NO_ERROR)
                        .build());
            }
        }
    }

    boolean isGoingAway() {
        return (closeFlag != null);
    }

    public int getGoingAwayLastStreamId() {
        return goingAwayLastStreamId;
    }

    protected void sendWindowUpdate(final int streamId, final int delta) {
        final WindowUpdateFrame f = WindowUpdateFrame.builder()
                .streamId(streamId)
                .windowSizeIncrement(delta)
                .build();
        NetLogger.log(NetLogger.Context.TX, this, f);
        outputSink.writeDownStream(f);
    }
    
    void sendPreface() {
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
                }
            }
        }
    }
    
    protected void sendServerPreface() {
        final SettingsFrame settingsFrame = prepareSettings().build();

        NetLogger.log(NetLogger.Context.TX, this, settingsFrame);

        // server preface
        //noinspection unchecked
        connection.write(settingsFrame.toBuffer(getMemoryManager()), ((sslFilter != null) ? new EmptyCompletionHandler() {
            @Override
            public void completed(Object result) {
                sslFilter.setRenegotiationDisabled(true);
            }
        } : null));
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
        final Buffer settingsBuffer = settingsFrame.toBuffer(getMemoryManager());
        
        final Buffer payload = Buffers.appendBuffers(
                connection.getMemoryManager(), priPayload, settingsBuffer);
        
        final HttpContent content = HttpContent.builder(request)
                .content(payload)
                .build();

        NetLogger.log(NetLogger.Context.TX, this, settingsFrame);

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
            headersDecoder = new HeadersDecoder(getMemoryManager(), getMaxHeaderListSize(), 4096);
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
     * @param ctx the current {@link FilterChainContext}
     * @param httpHeader the {@link HttpHeader} to encode
     * @param streamId the stream associated with this request
     * @param isLast is this the last frame?
     * @param toList the target {@link List}, to which the frames will be serialized
     * 
     * @return the HTTP2 header frames sequence
     * @throws IOException if an error occurs encoding the header
     */
    @SuppressWarnings("SameParameterValue")
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
     * Encodes the {@link Map} of header values into header frames to be sent as trailer headers.
     *
     * @param streamId the stream associated with this request
     * @param toList the target {@link List}, to which the frames will be serialized.
     * @param trailerHeaders a {@link MimeHeaders} of headers to be transmitted as trailers.
     *
     * @return the HTTP2 header frames sequence
     *
     * @throws IOException if an error occurs encoding the header
     */
    protected List<Http2Frame> encodeTrailersAsHeaderFrames(final int streamId,
                                                            final List<Http2Frame> toList,
                                                            final MimeHeaders trailerHeaders)
    throws IOException {
        final Buffer compressedHeaders = EncoderUtils.encodeTrailerHeaders(this, trailerHeaders);
        return bufferToHeaderFrames(streamId, compressedHeaders, true, toList);
    }
    
    /**
     * Encodes the {@link HttpRequestPacket} as a {@link PushPromiseFrame}
     * and locks the compression lock.
     * 
     * @param ctx the current {@link FilterChainContext}
     * @param httpRequest the  {@link HttpRequestPacket} to encode.
     * @param streamId the stream associated with this request.
     * @param promisedStreamId the push promise stream ID.
     * @param toList the target {@link List}, to which the frames will be serialized
     * @return the HTTP2 push promise frames sequence
     * 
     * @throws IOException if an error occurs encoding the request
     */
    @SuppressWarnings("SameParameterValue")
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
     * @param streamId the stream associated with the headers.
     * @param compressedHeaders a {@link Buffer} containing compressed headers
     * @param isEos will any additional data be sent after these headers?
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
     * @param streamId the stream associated with these headers
     * @param promisedStreamId the stream of the push promise
     * @param compressedHeaders the compressed headers to be sent
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
     * Completes the {@link HeaderBlockFragment} sequence serialization.
     * 
     * @param streamId the stream associated with this {@link HeaderBlockFragment}
     * @param compressedHeaders the {@link Buffer} containing the compressed headers
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

    @SuppressWarnings("SameParameterValue")
    Http2Stream acceptStream(final HttpRequestPacket request,
                             final int streamId, final int parentStreamId,
                             final boolean exclusive, final int priority)
    throws Http2SessionException {
        
        final Http2Stream stream = newStream(request,
                streamId, parentStreamId, exclusive, priority);
        
        synchronized(sessionLock) {
            if (isClosed()) {
                return null; // if the session is closed is set - return null to ignore stream creation
            }
            
            if (concurrentStreamsCount >= getLocalMaxConcurrentStreams()) {
                // throw Session level exception because headers were not decompressed,
                // so compression context is lost
                throw new Http2SessionException(ErrorCode.REFUSED_STREAM);
            }
            
            registerStream(streamId, stream);
            lastPeerStreamId = streamId;
        }
        
        return stream;
    }

    /**
     * Method is not thread-safe, it is expected that it will be called
     * within {@link #getNewClientStreamLock()} lock scope.
     * The caller code is responsible for obtaining and releasing the mentioned
     * {@link #getNewClientStreamLock()} lock.
     * @param request the request that initiated the stream
     * @param streamId the ID of this new stream
     * @param parentStreamId the parent stream
     * @param priority the priority of this stream
     *
     * @return a new {@link Http2Stream} for this request
     *
     * @throws org.glassfish.grizzly.http2.Http2StreamException if an error occurs opening the stream.
     */
    public Http2Stream openStream(final HttpRequestPacket request,
            final int streamId, final int parentStreamId,
            final boolean exclusive,
            final int priority)
            throws Http2StreamException {
        
        final Http2Stream stream = newStream(request,
                streamId, parentStreamId, exclusive,
                priority);
        
        synchronized(sessionLock) {
            if (isClosed()) {
                throw new Http2StreamException(streamId,
                        ErrorCode.REFUSED_STREAM, "Session is closed");
            }
            
            if (concurrentStreamsCount >= getLocalMaxConcurrentStreams()) {
                throw new Http2StreamException(streamId, ErrorCode.REFUSED_STREAM);
            }
            
            if (parentStreamId > 0) {
                final Http2Stream mainStream = getStream(parentStreamId);
                if (mainStream == null) {
                    throw new Http2StreamException(streamId, ErrorCode.REFUSED_STREAM,
                            "The parent stream does not exist");
                }
            }
            
            registerStream(streamId, stream);
            lastLocalStreamId = streamId;
        }
        
        return stream;
    }

    /**
     * The method is called to create an {@link Http2Stream} initiated via
     * HTTP/1.1 Upgrade mechanism.
     * 
     * @param request the request that initiated the upgrade
     * @param priority the stream priority
     * @param fin is more content expected?
     *
     * @return a new {@link Http2Stream} for this request
     *
     * @throws org.glassfish.grizzly.http2.Http2StreamException if an error occurs opening the stream.
     */
    public Http2Stream acceptUpgradeStream(final HttpRequestPacket request,
            final int priority, final boolean fin)
            throws Http2StreamException {
        
        request.setExpectContent(!fin);
        final Http2Stream stream = newUpgradeStream(request, priority);
        stream.onRcvHeaders(fin);
        
        synchronized (sessionLock) {
            if (isClosed()) {
                throw new Http2StreamException(Http2Stream.UPGRADE_STREAM_ID,
                        ErrorCode.REFUSED_STREAM, "Session is closed");
            }
            registerStream(Http2Stream.UPGRADE_STREAM_ID, stream);
            lastLocalStreamId = Http2Stream.UPGRADE_STREAM_ID;
        }
        
        return stream;
    }

    /**
     * The method is called on the client side, when the server confirms
     * HTTP/1.1 -> HTTP/2.0 upgrade with '101' response.
     * 
     * @param request the request that initiated the upgrade
     * @param priority the priority of the stream
     *
     * @return a new {@link Http2Stream} for this request
     *
     * @throws org.glassfish.grizzly.http2.Http2StreamException if an error occurs opening the stream.
     */
    public Http2Stream openUpgradeStream(final HttpRequestPacket request,
            final int priority)
            throws Http2StreamException {
        
        // we already sent headers - so the initial state is OPEN
        final Http2Stream stream = newUpgradeStream(request, priority);
        
        synchronized(sessionLock) {
            if (isClosed()) {
                throw new Http2StreamException(Http2Stream.UPGRADE_STREAM_ID,
                        ErrorCode.REFUSED_STREAM, "Session is closed");
            }

            registerStream(Http2Stream.UPGRADE_STREAM_ID, stream);
            lastLocalStreamId = Http2Stream.UPGRADE_STREAM_ID;
        }
        
        return stream;
    }
    
    /**
     * Initializes HTTP2 communication (if not initialized before) by forming
     * HTTP2 connection and stream {@link FilterChain}s.
     *  @param context the current {@link FilterChainContext}
     * @param isUpStream flag denoting the direction of the chain
     */
    void setupFilterChains(final FilterChainContext context,
                           final boolean isUpStream) {

        if (htt2SessionChain == null) {
            synchronized(this) {
                if (htt2SessionChain == null) {
                    if (isUpStream) {
                        http2StreamChain = (FilterChain) context.getFilterChain().subList(
                                context.getFilterIdx(), context.getEndIdx());

                        htt2SessionChain = (FilterChain) context.getFilterChain().subList(
                                context.getStartIdx(), context.getFilterIdx());
                    } else {
                        http2StreamChain = (FilterChain) context.getFilterChain().subList(
                                context.getFilterIdx(), context.getFilterChain().size());

                        htt2SessionChain = (FilterChain) context.getFilterChain().subList(
                                context.getEndIdx() + 1, context.getFilterIdx());
                    }
                }
            }
        }
    }
    
    FilterChain getHttp2SessionChain() {
        return htt2SessionChain;
    }
    
    /**
     * Called from {@link Http2Stream} once stream is completely closed.
     */
    void deregisterStream() {
        decStreamCount();
        
        final boolean isCloseSession;
        synchronized (sessionLock) {
            // If we're in GOAWAY state and there are no streams left - close this session
            isCloseSession = isGoingAway() && concurrentStreamsCount == 0;
            if (!isCloseSession) {
                if (checkCount++ > http2Configuration.getCleanFrequencyCheck() && streamsMap.size() > streamsHighWaterMark) {
                    checkCount = 0;
                    int maxCount = Float.valueOf(streamsHighWaterMark * http2Configuration.getCleanPercentage()).intValue();
                    int count = 0;
                    for (final Iterator<Map.Entry<Integer,Http2Stream>> streamIds = streamsMap.entrySet().iterator(); (streamIds.hasNext() && count < maxCount);) {
                        final Map.Entry<Integer,Http2Stream> entry = streamIds.next();
                        if (entry.getValue().isClosed()) {
                            streamIds.remove();
                        }
                        count++;
                    }
                }
            }
        }
        
        if (isCloseSession) {
            if (sessionClosed != null) {
                sessionClosed.result(this);
            } else {
                terminate(ErrorCode.NO_ERROR, "Session closed");
            }
        }
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
        
        final SettingsFrameBuilder builder = SettingsFrame.builder();
        
        if (getLocalMaxConcurrentStreams() != getDefaultMaxConcurrentStreams()) {
            builder.setting(SETTINGS_MAX_CONCURRENT_STREAMS, getLocalMaxConcurrentStreams());
        }

        if (getLocalStreamWindowSize() != getDefaultStreamWindowSize()) {
            builder.setting(SETTINGS_INITIAL_WINDOW_SIZE, getLocalStreamWindowSize());
        }

        builder.setting(SETTINGS_MAX_HEADER_LIST_SIZE, getMaxHeaderListSize());
        
        return builder;
    }

    /**
     * Acknowledge that certain amount of data has been read.
     * Depending on the total amount of un-acknowledge data the HTTP2 connection
     * can decide to send a window_update message to the peer.
     * 
     * @param sz size, in bytes, of the data being acknowledged
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
     * @param stream the stream that data is being ack'd on.
     * @param sz size, in bytes, of the data being acknowledged
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
                        = Http2Stream.unackedReadBytesUpdater.addAndGet(stream, sz);
                final int streamWindowSize = stream.getLocalWindowSize();

                // send update window message only in case currentUnackedBytes > windowSize / 2
                if (streamUnackedBytes > 0
                        && (streamUnackedBytes > (streamWindowSize / 2))
                        && Http2Stream.unackedReadBytesUpdater.compareAndSet(stream, streamUnackedBytes, 0)) {

                    sendWindowUpdate(stream.getId(), streamUnackedBytes);
                }
            }
        }
    }

    /*
     * This method is not thread safe and should be guarded by the session lock.
     */
    void registerStream(final int streamId, final Http2Stream stream) {
        if (streamId < 1) {
            throw new IllegalArgumentException("Invalid stream ID");
        }
        if (stream == null) {
            throw new NullPointerException("Attempt to register null stream");
        }

        streamsMap.put(streamId, stream);
        incStreamCount();
    }

    void incStreamCount() {
        concurrentStreamCountUpdater.incrementAndGet(this);
    }

    void decStreamCount() {
        concurrentStreamCountUpdater.decrementAndGet(this);
    }

    private final class ConnectionCloseListener implements CloseListener<Closeable, CloseType> {

        @Override
        public void onClosed(final Closeable closeable, final CloseType type)
                throws IOException {

            NetLogger.logClose(Http2Session.this);
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
