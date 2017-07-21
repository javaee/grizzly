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
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Event;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.filterchain.FilterChain;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.FilterChainContext.TransportContext;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.filterchain.TransportFilter;
import org.glassfish.grizzly.http.FixedLengthTransferEncoding;
import org.glassfish.grizzly.http.HttpBaseFilter;
import org.glassfish.grizzly.http.HttpContent;
import org.glassfish.grizzly.http.HttpContext;
import org.glassfish.grizzly.http.HttpHeader;
import org.glassfish.grizzly.http.HttpPacket;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.http.HttpResponsePacket;
import org.glassfish.grizzly.http.Method;
import org.glassfish.grizzly.http.ProcessingState;
import org.glassfish.grizzly.http.Protocol;
import org.glassfish.grizzly.http.TransferEncoding;
import org.glassfish.grizzly.http.util.DataChunk;
import org.glassfish.grizzly.http.util.Header;
import org.glassfish.grizzly.http.util.HttpStatus;
import org.glassfish.grizzly.http.util.MimeHeaders;
import org.glassfish.grizzly.http2.frames.ContinuationFrame;
import org.glassfish.grizzly.http2.frames.DataFrame;
import org.glassfish.grizzly.http2.frames.ErrorCode;
import org.glassfish.grizzly.http2.frames.GoAwayFrame;
import org.glassfish.grizzly.http2.frames.HeaderBlockFragment;
import org.glassfish.grizzly.http2.frames.HeadersFrame;
import org.glassfish.grizzly.http2.frames.Http2Frame;
import org.glassfish.grizzly.http2.frames.PingFrame;
import org.glassfish.grizzly.http2.frames.PushPromiseFrame;
import org.glassfish.grizzly.http2.frames.RstStreamFrame;
import org.glassfish.grizzly.http2.frames.SettingsFrame;
import org.glassfish.grizzly.http2.frames.WindowUpdateFrame;
import org.glassfish.grizzly.threadpool.GrizzlyExecutorService;
import org.glassfish.grizzly.threadpool.ThreadPoolConfig;
import org.glassfish.grizzly.threadpool.Threads;
import org.glassfish.grizzly.utils.Charsets;

import org.glassfish.grizzly.http2.frames.HeaderBlockHead;
import org.glassfish.grizzly.http2.frames.PriorityFrame;


/**
 * The {@link org.glassfish.grizzly.filterchain.Filter} serves as a bridge
 * between HTTP2 frames and upper-level HTTP layers by converting {@link Http2Frame}s into
 * {@link HttpPacket}s and passing them up/down by the {@link FilterChain}.
 * 
 * Additionally this {@link org.glassfish.grizzly.filterchain.Filter} has
 * logic responsible for checking HTTP2 protocol semantics and fire correspondent
 * events and messages in case when HTTP2 semantics is broken.
 * 
 *
 */
public abstract class Http2BaseFilter extends HttpBaseFilter {
    private final static Logger LOGGER = Grizzly.logger(Http2BaseFilter.class);

    /**
     * Token for clear-text HTTP/2.0.
     */
    static final String HTTP2_CLEAR = "h2c";

    /**
     * Attribute name for associating push status.
     */
    static final String HTTP2_PUSH_ENABLED = "http2-push-enabled";
    
    static final byte[] PRI_PAYLOAD = "SM\r\n\r\n".getBytes(Charsets.ASCII_CHARSET);
    
    protected static final TransferEncoding FIXED_LENGTH_ENCODING =
            new FixedLengthTransferEncoding();

    final Http2FrameCodec frameCodec = new Http2FrameCodec();

    private final Http2Configuration configuration;
    
    protected final ExecutorService threadPool;
    
    private int localMaxFramePayloadSize;

    /**
     * Constructs Http2HandlerFilter.
     */
    public Http2BaseFilter(final Http2Configuration configuration) {
        this.configuration = configuration;
        final ThreadPoolConfig tpConfig = configuration.getThreadPoolConfig();
        threadPool = ((tpConfig != null)
                ? GrizzlyExecutorService.createInstance(tpConfig)
                : configuration.getExecutorService());
    }

    /**
     * @return the maximum allowed HTTP2 frame payload size
     */
    public int getLocalMaxFramePayloadSize() {
        return localMaxFramePayloadSize;
    }

    /**
     * Sets the maximum allowed HTTP2 frame size.
     * @param localMaxFramePayloadSize the maximum allowed HTTP2 frame size
     */
    public void setLocalMaxFramePayloadSize(final int localMaxFramePayloadSize) {
        this.localMaxFramePayloadSize = localMaxFramePayloadSize;
    }

    /**
     * @return the {@link Http2Configuration} backing this filter.
     */
    public Http2Configuration getConfiguration() {
        return configuration;
    }

    protected boolean processFrames(final FilterChainContext ctx,
            final Http2Session http2Session,
            final List<Http2Frame> framesList) {
        
        if (framesList == null || framesList.isEmpty()) {
            return true;
        }

        try {
            try {
                for (Http2Frame inFrame : framesList) {
                    NetLogger.log(NetLogger.Context.RX, http2Session, inFrame);
                    try {
                        processInFrame(http2Session, ctx, inFrame);
                    } catch (Http2StreamException e) {
                        if (LOGGER.isLoggable(Level.FINE)) {
                            LOGGER.log(Level.FINE, "Http2StreamException occurred on connection=" +
                                    ctx.getConnection() + " during Http2Frame processing", e);
                        }

                        final int streamId = e.getStreamId();

                        if (streamId == 0) {
                            throw new Http2SessionException(ErrorCode.PROTOCOL_ERROR);
                        }

                        sendRstStream(ctx, http2Session,
                                streamId, e.getErrorCode());
                    }
                }
            } finally {
                // Don't forget to clean framesList, because it will be reused
                framesList.clear();
            }

            final List<Http2Stream> streamsToFlushInput =
                    http2Session.streamsToFlushInput;
            for (Http2Stream streamsToFlush : streamsToFlushInput) {
                streamsToFlush.flushInputData();
            }
            streamsToFlushInput.clear();
            
            return true;
        } catch (Http2SessionException e) {
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.log(Level.FINE, "Http2SessionException occurred on connection=" +
                        ctx.getConnection() + " during Http2Frame processing", e);
            }
            http2Session.terminate(e.getErrorCode(), e.getMessage());
        } catch (IOException e) {
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.log(Level.FINE, "IOException occurred on connection=" +
                        ctx.getConnection() + " during Http2Frame processing", e);
            }
            http2Session.terminate(ErrorCode.INTERNAL_ERROR, e.getMessage());
        }
        
        return false;
    }

    protected boolean checkRequestHeadersOnUpgrade(
            final HttpRequestPacket httpRequest) {
        
        if (!httpRequest.isUpgrade()) {
            if (LOGGER.isLoggable(Level.FINEST)) {
                LOGGER.finest("checkRequestHeadersOnUpgrade: failed no upgrade");
            }
            return false;
        }
        
        // Check "Connection: Upgrade, HTTP2-Settings" header
        final DataChunk connectionHeaderDC =
                httpRequest.getHeaders().getValue(Header.Connection);
        
        if (connectionHeaderDC == null) {
            if (LOGGER.isLoggable(Level.FINEST)) {
                LOGGER.finest("checkRequestHeadersOnUpgrade: failed no connection");
            }
            return false;
        }
        
        boolean upgradeFound = false;
        boolean http2SettingsFound = false;
        
        int pos = 0;
        final int len = connectionHeaderDC.getLength();
        while (pos < len) {
            final int comma = connectionHeaderDC.indexOf(",", pos);
            final int valueEnd = comma != -1 ? comma : len;
            
            final String value = connectionHeaderDC.toString(pos, valueEnd).trim();
            
            upgradeFound = upgradeFound || "Upgrade".equals(value);
            http2SettingsFound = http2SettingsFound || "HTTP2-Settings".equals(value);
            
            pos = valueEnd + 1;
        }
        
        if (!upgradeFound || !http2SettingsFound) {
            if (LOGGER.isLoggable(Level.FINEST)) {
                LOGGER.log(Level.FINEST, "checkRequestHeadersOnUpgrade: failed incorrect connection: {0}", connectionHeaderDC);
            }
            return false;
        }
        
        // Check Http2-Settings header
        
        if (!httpRequest.getHeaders().contains(Header.HTTP2Settings)) {
            if (LOGGER.isLoggable(Level.FINEST)) {
                LOGGER.finest("checkRequestHeadersOnUpgrade: failed no settings");
            }
            return false;
        }
        
        return true;
    }
    
    protected boolean checkResponseHeadersOnUpgrade(
            final HttpResponsePacket httpResponse) {
        
        if (httpResponse.getStatus() != 101) {
            // Not "HTTP/1.1 101 Switching Protocols"
            return false;
        }
        
        if (!httpResponse.isUpgrade()) {
            // No Upgrade header
            return false;
        }
        
        // Check "Connection: Upgrade, HTTP2-Settings" header
        final DataChunk connectionHeaderDC =
                httpResponse.getHeaders().getValue(Header.Connection);

        return !(connectionHeaderDC == null || connectionHeaderDC.isNull() ||
                !connectionHeaderDC.equals(Header.Upgrade.getBytes()));

    }
    
    protected SettingsFrame getHttp2UpgradeSettings(
            final HttpRequestPacket httpRequest) {
        
        final DataChunk http2Settings =
                httpRequest.getHeaders().getValue(Header.HTTP2Settings);
        
        return http2Settings != null
                ? SettingsFrame.fromBase64Uri(http2Settings)
                : null;
    }
    
    protected boolean isHttp2UpgradingVersion(
            final HttpHeader httpHeader) {
        final DataChunk upgradeDC= httpHeader.getUpgradeDC();

        assert upgradeDC != null && !upgradeDC.isNull(); // should've been check before
        
        // Check "Upgrade: h2c" header
        return upgradeDC.equals(HTTP2_CLEAR);
    }
    
    // ------------------------------------------------------- Protected Methods


    /**
     * @inheritDoc
     */
    @SuppressWarnings({"UnusedReturnValue", "unused"})
    protected boolean onHttpPacketParsed(HttpHeader httpHeader, FilterChainContext ctx) {
        return false;
    }


    /**
     * @inheritDoc
     */
    @SuppressWarnings({"UnusedReturnValue", "unused"})
    protected boolean onHttpHeaderParsed(HttpHeader httpHeader,
                                         Buffer buffer,
                                         FilterChainContext ctx) {
        return false;
    }


    /**
     * @inheritDoc
     */
    @SuppressWarnings("unused")
    protected void onInitialLineParsed(final HttpHeader httpHeader,
                                       final FilterChainContext ctx) {
    }


    /**
     * @inheritDoc
     */
    @SuppressWarnings("unused")
    protected void onInitialLineEncoded(final HttpHeader httpHeader,
                                        final FilterChainContext ctx) {
    }


    /**
     * @inheritDoc
     */
    @SuppressWarnings("unused")
    protected void onHttpHeadersParsed(final HttpHeader httpHeader,
                                       final FilterChainContext ctx) {
    }


    /**
     * @inheritDoc
     */
    @SuppressWarnings("unused")
    protected void onHttpHeadersEncoded(final HttpHeader httpHeader,
                                        final FilterChainContext ctx) {
    }


    /**
     * @inheritDoc
     */
    @SuppressWarnings("unused")
    protected void onHttpContentParsed(final HttpContent content,
                                       final FilterChainContext ctx) {
    }

    /**
     * @inheritDoc
     */
    @SuppressWarnings("unused")
    protected void onHttpContentEncoded(final HttpContent content,
                                        final FilterChainContext ctx) {
    }


    /**
     * @inheritDoc
     */
    @SuppressWarnings("unused")
    protected void onHttpHeaderError(final HttpHeader httpHeader,
                                     final FilterChainContext ctx,
                                     final Throwable t) throws IOException {
    }

    /**
     * @inheritDoc
     */
    @SuppressWarnings("unused")
    protected void onHttpContentError(HttpHeader httpHeader,
                                      FilterChainContext ctx,
                                      Throwable t) throws IOException {
    }


    // --------------------------------------------------------- Private Methods
    
    @SuppressWarnings("DuplicateThrows")
    private void processInFrame(final Http2Session http2Session,
                                final FilterChainContext context,
                                final Http2Frame frame)
     throws Http2StreamException, Http2SessionException, IOException {

        http2Session.checkFrameSequenceSemantics(frame);

        switch (frame.getType()) {
            case DataFrame.TYPE: {
                processDataFrame(http2Session, context, frame);
                break;
            }
            case PriorityFrame.TYPE: {
                processPriorityFrame(frame);
                break;
            }
            case HeadersFrame.TYPE:
            case PushPromiseFrame.TYPE:
            case ContinuationFrame.TYPE: {
                processHeadersFrame(http2Session, context, frame);
                break;
            }
            case SettingsFrame.TYPE: {
                processSettingsFrame(http2Session, context, frame);
                break;
            }
            case PingFrame.TYPE: {
                processPingFrame(http2Session, frame);
                break;
            }
            case RstStreamFrame.TYPE: {
                processRstStreamFrame(http2Session, frame);
                break;
            }
            case GoAwayFrame.TYPE: {
                processGoAwayFrame(http2Session, frame);
                break;
            }
            case WindowUpdateFrame.TYPE: {
                processWindowUpdateFrame(http2Session, frame);
                break;
            }
            default: {
                LOGGER.log(Level.WARNING, "Unknown or unhandled frame [type={0} flags={1} length={2} streamId={3}]",
                        new Object[]{frame.getType(),
                                     frame.getFlags(),
                                     frame.getLength(),
                                     frame.getStreamId()});
            }
        }

    }

    private void processPriorityFrame(final Http2Frame frame)
    throws Http2SessionException, Http2StreamException {
        final int streamId = frame.getStreamId();
        try {
            if (streamId == 0) {
                throw new Http2SessionException(ErrorCode.PROTOCOL_ERROR, "PRIORITY frame on stream ID zero.");
            }
            if (frame.getLength() != 5) {
                throw new Http2StreamException(streamId, ErrorCode.FRAME_SIZE_ERROR);
            }
            if (streamId == ((PriorityFrame) frame).getStreamDependency()) {
                throw new Http2SessionException(ErrorCode.PROTOCOL_ERROR, "PRIORITY frame dependent on itself.");
            }
        } finally {
            frame.recycle();
        }
    }

    private void processWindowUpdateFrame(final Http2Session http2Session,
            final Http2Frame frame) throws Http2StreamException, Http2SessionException {
        
        WindowUpdateFrame updateFrame = (WindowUpdateFrame) frame;
        final int streamId = updateFrame.getStreamId();
        final int delta = updateFrame.getWindowSizeIncrement();
        updateFrame.recycle();


        if (streamId == 0) {
            if (delta == 0) {
                throw new Http2SessionException(ErrorCode.PROTOCOL_ERROR, "Illegal WINDOW_UPDATE with a delta of 0.");
            }
            http2Session.getOutputSink().onPeerWindowUpdate(delta);
        } else {
            if (delta == 0) {
                throw new Http2StreamException(streamId, ErrorCode.PROTOCOL_ERROR, "Illegal WINDOW_UPDATE with a delta of 0.");
            }
            final Http2Stream stream = http2Session.getStream(streamId);

            //noinspection Duplicates
            if (stream != null) {
                stream.getOutputSink().onPeerWindowUpdate(delta);
            } else {
                throw new Http2SessionException(ErrorCode.PROTOCOL_ERROR);
            }
        }
    }

    private void processGoAwayFrame(final Http2Session http2Session,
                                    final Http2Frame frame)
    throws Http2SessionException {

        if (frame.getStreamId() != 0) {
            throw new Http2SessionException(ErrorCode.PROTOCOL_ERROR, "GOAWAY frame for non-zero stream ID.");
        }

        http2Session.setGoAwayByPeer(((GoAwayFrame) frame).getLastStreamId());
        frame.recycle();
    }
    
    private void processSettingsFrame(final Http2Session http2Session,
            final FilterChainContext context, final Http2Frame frame)
    throws Http2SessionException, Http2StreamException {

        if (frame.getStreamId() != 0) {
            throw new Http2SessionException(ErrorCode.PROTOCOL_ERROR, "SETTINGS frame with non-zero stream ID.");
        }

        final SettingsFrame settingsFrame = (SettingsFrame) frame;

        try {
            if (settingsFrame.isAck()) {
                if (settingsFrame.getLength() != 0) {
                    throw new Http2SessionException(ErrorCode.FRAME_SIZE_ERROR, "SETTINGS frame ack with a non-zero length.");
                }
                return;
            }

            if (settingsFrame.getLength() % 6 != 0) {
                throw new Http2SessionException(ErrorCode.FRAME_SIZE_ERROR, "SETTINGS frame length not multiple of six.");
            }
            sendSettingsAck(http2Session, context);
            applySettings(http2Session, settingsFrame);
        } finally {
            frame.recycle();
        }
        

    }
    
    void applySettings(final Http2Session http2Session, final SettingsFrame settingsFrame)
    throws Http2SessionException, Http2StreamException {

        for (int i = 0, numberOfSettings = settingsFrame.getNumberOfSettings(); i < numberOfSettings; i++) {
            final SettingsFrame.Setting setting = settingsFrame.getSettingByIndex(i);
            
            switch (setting.getId()) {
                case SettingsFrame.SETTINGS_HEADER_TABLE_SIZE:
                    break;
                case SettingsFrame.SETTINGS_ENABLE_PUSH:
                    final int val = setting.getValue();
                    if (val < 0 || val > 1) {
                        throw new Http2SessionException(ErrorCode.PROTOCOL_ERROR, "Invalid value for SETTINGS_ENABLE_PUSH.");
                    }
                    final boolean pushEnabled = (val == 1);
                    http2Session.getConnection().getAttributes().setAttribute(HTTP2_PUSH_ENABLED, pushEnabled);
                    http2Session.setPushEnabled(pushEnabled);
                    break;
                case SettingsFrame.SETTINGS_MAX_CONCURRENT_STREAMS:
                    http2Session.setPeerMaxConcurrentStreams(setting.getValue());
                    break;
                case SettingsFrame.SETTINGS_INITIAL_WINDOW_SIZE:
                    if (setting.getValue() == Integer.MIN_VALUE) {
                        throw new Http2SessionException(ErrorCode.FLOW_CONTROL_ERROR, "SETTINGS_INITIAL_WINDOW_SIZE greater than 2^31-1.");
                    }
                    http2Session.setPeerStreamWindowSize(setting.getValue());
                    break;
                case SettingsFrame.SETTINGS_MAX_FRAME_SIZE:
                    http2Session.setPeerMaxFramePayloadSize(setting.getValue());
                    break;
                case SettingsFrame.SETTINGS_MAX_HEADER_LIST_SIZE:
                    break;
            }
        }
    }

    private void processPingFrame(final Http2Session http2Session,
                                  final Http2Frame frame)
    throws Http2SessionException {

        if (frame.getStreamId() != 0) {
            throw new Http2SessionException(ErrorCode.PROTOCOL_ERROR, "PING frame with non-zero stream ID.");
        }

        if (frame.getLength() != 8) {
            throw new Http2SessionException(ErrorCode.FRAME_SIZE_ERROR, "PING frame with invalid length.");
        }

        PingFrame pingFrame = (PingFrame) frame;

        if (pingFrame.isAckSet()) {
            return;
        }

        // Send the same ping message back, but set the ack flag
        pingFrame.setFlag(PingFrame.ACK_FLAG);
        http2Session.getOutputSink().writeDownStream(pingFrame);
    }

    private void processRstStreamFrame(final Http2Session http2Session,
                                       final Http2Frame frame)
    throws Http2SessionException {


        final int streamId = frame.getStreamId();
        frame.recycle();
        if (streamId == 0) {
            throw new Http2SessionException(ErrorCode.PROTOCOL_ERROR, "RST frame on stream ID zero.");
        }

        if (ignoreFrameForStreamId(http2Session, streamId)) {
            return;
        }
        final Http2Stream stream = http2Session.getStream(streamId);
        if (stream == null) {
            if (streamId > http2Session.lastPeerStreamId) {
                // consider this case an idle stream without creating one
                throw new Http2SessionException(ErrorCode.PROTOCOL_ERROR, "Received RST frame on IDLE stream.");
            }
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine("Received RST frame on on existent stream.  Ignoring frame.");
            }
            return;
        }
        if (stream.isIdle()) {
            throw new Http2SessionException(ErrorCode.PROTOCOL_ERROR, "Illegal attempt to RST IDLE stream.");
        }
        
        // Notify the stream that it has been reset remotely
        stream.resetRemotely();
    }
    
    private void processHeadersFrame(final Http2Session http2Session,
                                  final FilterChainContext context,
                                  final Http2Frame frame) throws IOException {
        final HeaderBlockFragment headerBlockFragment = (HeaderBlockFragment) frame;
        if (headerBlockFragment.getStreamId() == 0) {
            throw new Http2SessionException(ErrorCode.PROTOCOL_ERROR, "HEADERS frame received on stream 0.");
        }
        if (frame instanceof HeaderBlockHead) {
            final HeaderBlockHead blockHead = (HeaderBlockHead) frame;
            if (blockHead.isPadded() && blockHead.getPadLength() >= headerBlockFragment.getLength()) {
                throw new Http2SessionException(ErrorCode.PROTOCOL_ERROR, "Pad length greater than or equal to the payload length.");
            }
        }
        if (frame instanceof HeadersFrame) {
            final HeadersFrame headersFrame = (HeadersFrame) frame;
            if (headersFrame.getStreamId() == headersFrame.getStreamDependency()) {
                throw new Http2SessionException(ErrorCode.PROTOCOL_ERROR, "HEADER frame dependent upon itself.");
            }
            headersFrame.normalize();
        }
        if (http2Session.isServer() && frame.getType() == PushPromiseFrame.TYPE) {
            throw new Http2SessionException(ErrorCode.PROTOCOL_ERROR, "Server endpoint received PUSH_PROMISE frame.");
        }

        final HeadersDecoder headersDecoder = http2Session.getHeadersDecoder();
        
        if (headerBlockFragment.getCompressedHeaders().hasRemaining()) {
            if (!headersDecoder.append(headerBlockFragment.takePayload())) {
                headersDecoder.setFirstHeaderFrame((HeaderBlockHead) headerBlockFragment);
                final HeaderBlockHead firstHeaderFrame =
                        headersDecoder.finishHeader();
                firstHeaderFrame.setTruncated();
                try {
                    processCompleteHeader(http2Session, context, firstHeaderFrame);
                } finally {
                    firstHeaderFrame.recycle();
                }

                return;
            }
        }

        final boolean isEOH = headerBlockFragment.isEndHeaders();
        
        if (!headersDecoder.isProcessingHeaders()) { // first headers frame (either HeadersFrame or PushPromiseFrame)
            headersDecoder.setFirstHeaderFrame((HeaderBlockHead) headerBlockFragment);
        } else {
            headerBlockFragment.recycle();
        }
        
        if (!isEOH) {
            return; // wait for more header frames to come
        }
        
        final HeaderBlockHead firstHeaderFrame =
                headersDecoder.finishHeader();

        try {
            processCompleteHeader(http2Session, context, firstHeaderFrame);
        } finally {
            firstHeaderFrame.recycle();
        }
    }

    /**
     * The method is called once complete HTTP header block arrives on {@link Http2Session}.
     * 
     * @param http2Session the {@link Http2Session} associated with this header.
     * @param context the current {@link FilterChainContext}
     * @param firstHeaderFrame the first {@link HeaderBlockHead} from the first {@link HeadersFrame} received.
     * @throws IOException if an error occurs when dealing with the event.
     */
    protected abstract void processCompleteHeader(
            final Http2Session http2Session,
            final FilterChainContext context,
            final HeaderBlockHead firstHeaderFrame) throws IOException;

    @Override
    @SuppressWarnings("unchecked")
    public NextAction handleWrite(final FilterChainContext ctx) throws IOException {
        final Http2State http2State = Http2State.get(ctx.getConnection());
        
        if (http2State == null || http2State.isNeverHttp2()) {
            return ctx.getInvokeAction();
        }
        
        final Object message = ctx.getMessage();
        
        final Http2Session http2Session = obtainHttp2Session(ctx, false);

        if (http2Session.isHttp2OutputEnabled() &&
                HttpPacket.isHttp(message)) {

            // Get HttpPacket
            final HttpPacket httpPacket = ctx.getMessage();
            final HttpHeader httpHeader = httpPacket.getHttpHeader();

            processOutgoingHttpHeader(ctx, http2Session, httpHeader, httpPacket);
        } else {
            final TransportContext transportContext = ctx.getTransportContext();
            http2Session.getOutputSink().writeDownStream(message,
                    transportContext.getCompletionHandler(),
                        transportContext.getLifeCycleHandler());
        }

        return ctx.getStopAction();
    }

    protected abstract void processOutgoingHttpHeader(final FilterChainContext ctx,
            final Http2Session http2Session,
            final HttpHeader httpHeader,
            final HttpPacket entireHttpPacket) throws IOException;
    

    protected void prepareOutgoingRequest(final HttpRequestPacket request) {
        String contentType = request.getContentType();
        if (contentType != null) {
            request.getHeaders().setValue(Header.ContentType).setString(contentType);
        }
        
        if (request.getContentLength() != -1) {
            // FixedLengthTransferEncoding will set proper Content-Length header
            FIXED_LENGTH_ENCODING.prepareSerialize(null, request, null);
        }
    }    
    
    @Override
    @SuppressWarnings("unchecked")
    public NextAction handleEvent(final FilterChainContext ctx, final Event event) throws IOException {
        if (!Http2State.isHttp2(ctx.getConnection())) {
            return ctx.getInvokeAction();
        }
        
        final Object type = event.type();
        
        if (type == TransportFilter.FlushEvent.TYPE) {
            assert event instanceof TransportFilter.FlushEvent;
            
            final HttpContext httpContext = HttpContext.get(ctx);
            final Http2Stream stream = (Http2Stream) httpContext.getContextStorage();
            
            final TransportFilter.FlushEvent flushEvent =
                    (TransportFilter.FlushEvent) event;
            
            stream.outputSink.flush(flushEvent.getCompletionHandler());
            
            return ctx.getStopAction();
        }
        
        return ctx.getInvokeAction();
    }

    boolean checkIfHttp2StreamChain(final FilterChainContext ctx)
            throws IOException {
        final Object message = ctx.getMessage();

        if (message == null) {  // If message == null - it means it's initiated by blocking ctx.read() call
            // we have to check Http2Stream associated input queue if there are any data we can return
            // otherwise block until input data is available
            final Http2Stream http2Stream =
                    (Http2Stream) HttpContext.get(ctx).getContextStorage();
            ctx.setMessage(http2Stream.pollInputData());
            
            return true;
        }
        
        final HttpContent httpContent = (HttpContent) message;
        final HttpHeader httpHeader = httpContent.getHttpHeader();
        
        // if the stream is assigned - it means we process HTTP/2.0 request
        // in the upstream (HTTP2 stream chain)
        return Http2Stream.getStreamFor(httpHeader) != null;
    }
    
    /**
     * Creates {@link Http2Session} with pre-configured initial-windows-size and
     * max-concurrent-streams
     * @param connection the TCP {@link Connection}
     * @param isServer flag indicating whether this connection is server side or not.
     * @return {@link Http2Session}
     */
    protected Http2Session createHttp2Session(final Connection connection,
                                              final boolean isServer) {
        
        final Http2Session http2Session =
            new Http2Session(connection, isServer, this);

        final int initialWindowSize = configuration.getInitialWindowSize();
        if (initialWindowSize != -1) {
            http2Session.setLocalStreamWindowSize(initialWindowSize);
        }

        final int maxConcurrentStreams = configuration.getMaxConcurrentStreams();
        if (maxConcurrentStreams != -1) {
            http2Session.setLocalMaxConcurrentStreams(maxConcurrentStreams);
        }
        
        Http2Session.bind(connection, http2Session);
        
        return http2Session;
    }
    
    protected void onPrefaceReceived(final Http2Session http2Session) {
    }
    
    void sendUpstream(final Http2Session http2Session,
            final Http2Stream stream, final HttpContent content) {
        
        final HttpRequestPacket request = stream.getRequest();
        final HttpContext httpContext = HttpContext.newInstance(stream,
                stream, stream, request);
        request.getProcessingState().setHttpContext(httpContext);

        if (threadPool == null) {
            // mark this thread as a service to let filters upstream know, that
            // it must not be blocked, because otherwise entire HTTP2 connection
            // can stall
            Threads.setService(true);
            try {
                http2Session.sendMessageUpstream(stream, content);
            } finally {
                Threads.setService(false);
            }
        } else {
            threadPool.execute(new Runnable() {
                @Override
                public void run() {
                    http2Session.sendMessageUpstream(stream, content);
                }
            });
        }
    }
    
    void prepareIncomingRequest(final Http2Stream stream,
            final Http2Request request) {

        final ProcessingState state = request.getProcessingState();
        final HttpResponsePacket response = request.getResponse();

        final Method method = request.getMethod();

        if (stream.isPushStream() ||
                Method.GET.equals(method)
                || Method.HEAD.equals(method)
                || (!Method.CONNECT.equals(method)
                        && request.getContentLength() == 0)) {
            request.setExpectContent(false);
        }

        //noinspection Duplicates
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
        if (hostDC == null || hostDC.getLength() == 0) {
            state.setError(true);
        }

    }
    
    void sendRstStream(final FilterChainContext ctx,
            final Http2Session http2Session,
            final int streamId, final ErrorCode errorCode) {

        final RstStreamFrame rstStreamFrame = RstStreamFrame.builder()
                .errorCode(errorCode)
                .streamId(streamId)
                .build();

        ctx.write(frameCodec.serializeAndRecycle(http2Session, rstStreamFrame));
    }

    /**
     * Obtain {@link Http2Session} associated with the {@link Connection}
     * and prepare it for use.
     * 
     * @param context {@link FilterChainContext}
     * @param isUpStream <tt>true</tt> if the {@link FilterChainContext} represents
     *          upstream {@link FilterChain} execution, or <tt>false</tt> otherwise
     * @return {@link Http2Session} associated with the {@link Connection}
     *          and prepare it for use
     */
    @SuppressWarnings("SameParameterValue")
    protected final Http2Session obtainHttp2Session(
            final FilterChainContext context,
            final boolean isUpStream) {
        
        return obtainHttp2Session(null, context, isUpStream);
    }

    /**
     * Obtain {@link Http2Session} associated with the {@link Connection}
     * and prepare it for use.
     * 
     * @param http2State {@link Http2State} associated with the {@link Connection}
     * @param context {@link FilterChainContext}
     * @param isUpStream <tt>true</tt> if the {@link FilterChainContext} represents
     *          upstream {@link FilterChain} execution, or <tt>false</tt> otherwise
     * @return {@link Http2Session} associated with the {@link Connection}
     *          and prepare it for use
     */
    final Http2Session obtainHttp2Session(
            final Http2State http2State,
            final FilterChainContext context,
            final boolean isUpStream) {
        final Connection connection = context.getConnection();
        
        Http2Session http2Session = http2State != null
                ? http2State.getHttp2Session()
                : null;
        
        if (http2Session == null) {
            http2Session = Http2Session.get(connection);
            if (http2Session == null) {

                http2Session = createHttp2Session(connection, true);
            }
        }
        
        http2Session.setupFilterChains(context, isUpStream);

        return http2Session;
    }

    private void sendSettingsAck(final Http2Session http2Session,
            final FilterChainContext context) {

        final SettingsFrame frame = SettingsFrame.builder()
                .setAck()
                .build();
        
        context.write(
                frameCodec.serializeAndRecycle(
                        http2Session, frame)
        );
    }
    
    private static void processDataFrame(final Http2Session http2Session,
            final FilterChainContext context,
            final Http2Frame frame) throws IOException {

        final DataFrame dataFrame = (DataFrame) frame;
        final Buffer data;
        final int streamId;
        final boolean fin;
        try {
            if (dataFrame.isPadded() && dataFrame.getPadLength() >= dataFrame.getLength()) {
                throw new Http2SessionException(ErrorCode.PROTOCOL_ERROR, "Pad length greater than or equal to the payload length.");
            }
            dataFrame.normalize();
            data = dataFrame.getData();
            streamId = dataFrame.getStreamId();
            fin = dataFrame.isFlagSet(DataFrame.END_STREAM);

        } finally {
            dataFrame.recycle();
        }

        // Always ACK the data to maintain flow-control state
        http2Session.ackConsumedData(data.remaining());

        // If we're going away, ignore any frames for streams greater than the last stream ID from the goaway frame.
        if (ignoreFrameForStreamId(http2Session, streamId)) {
            return;
        }

        final Http2Stream stream = http2Session.getStream(streamId);
        if (stream == null && streamId > http2Session.lastPeerStreamId) {
            // consider this case an idle stream without creating one
            throw new Http2SessionException(ErrorCode.PROTOCOL_ERROR, "Received DATA frame on IDLE stream.");
        }
        // @TODO null stream may happen if stream state has been cleaned up.  Need to deal with this better.
        if (stream == null) {
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.log(Level.FINE, "Data frame received for non-existent stream: connection={0}, frame={1}, stream={2}",
                        new Object[]{context.getConnection(), dataFrame, streamId});
            }
            throw new Http2StreamException(streamId, ErrorCode.STREAM_CLOSED);
        }

        // @TODO Is there a better spot for this check?
        // Check stream state to ensure we can send this upstream
        final IOException error = stream.assertCanAcceptData(fin);
        if (error != null) {
            throw error;
        }

        stream.offerInputData(data, fin);
    }

    protected static boolean ignoreFrameForStreamId(final Http2Session session, final int streamId) {
        return (session.isGoingAway() && streamId > session.getGoingAwayLastStreamId());
    }
}
