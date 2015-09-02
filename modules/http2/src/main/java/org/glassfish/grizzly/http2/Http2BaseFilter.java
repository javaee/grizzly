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
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.filterchain.FilterChain;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.FilterChainContext.TransportContext;
import org.glassfish.grizzly.filterchain.FilterChainEvent;
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
import org.glassfish.grizzly.http.util.FastHttpDateFormat;
import org.glassfish.grizzly.http.util.Header;
import org.glassfish.grizzly.http.util.HttpStatus;
import org.glassfish.grizzly.http.util.MimeHeaders;
import org.glassfish.grizzly.http2.compression.HeadersDecoder;
import org.glassfish.grizzly.http2.frames.ContinuationFrame;
import org.glassfish.grizzly.http2.frames.DataFrame;
import org.glassfish.grizzly.http2.frames.ErrorCode;
import org.glassfish.grizzly.http2.frames.GoAwayFrame;
import org.glassfish.grizzly.http2.frames.HeadersFrame;
import org.glassfish.grizzly.http2.frames.Http2Frame;
import org.glassfish.grizzly.http2.frames.PingFrame;
import org.glassfish.grizzly.http2.frames.PushPromiseFrame;
import org.glassfish.grizzly.http2.frames.RstStreamFrame;
import org.glassfish.grizzly.http2.frames.SettingsFrame;
import org.glassfish.grizzly.http2.frames.WindowUpdateFrame;
import org.glassfish.grizzly.threadpool.Threads;
import org.glassfish.grizzly.utils.Charsets;
import org.glassfish.grizzly.utils.Pair;

import static org.glassfish.grizzly.http2.Constants.*;
import org.glassfish.grizzly.http2.frames.HeaderBlockFragment;
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
 * @author Grizzly team
 */
public class Http2BaseFilter extends HttpBaseFilter {
    private final static Logger LOGGER = Grizzly.logger(Http2BaseFilter.class);
    
    private static final TransferEncoding FIXED_LENGTH_ENCODING =
            new FixedLengthTransferEncoding();

    static final String HTTP2_CLEAR_TCP_UPGRADE_SIGNATURE = "h2c";
    static final DraftVersion[] ALL_HTTP2_DRAFTS =
            {DraftVersion.DRAFT_14};
    
    static final byte[] PRI_MSG = "PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n".getBytes(Charsets.ASCII_CHARSET);
    static final byte[] PRI_PAYLOAD = "SM\r\n\r\n".getBytes(Charsets.ASCII_CHARSET);
    
    final Http2FrameCodec frameCodec = new Http2FrameCodec();
    
    private final DraftVersion[] supportedHttp2Drafts;
    
    private final ExecutorService threadPool;
    
    private volatile int maxConcurrentStreams = -1;
    private volatile int initialWindowSize = -1;

    private int localMaxFramePayloadSize;
    
    /**
     * Constructs Http2HandlerFilter.
     */
    public Http2BaseFilter() {
        this(null, ALL_HTTP2_DRAFTS);
    }

    /**
     * Constructs Http2HandlerFilter.
     * 
     * @param supportedDraftVersions HTTP2 draft versions this filter has to support
     */
    public Http2BaseFilter(final DraftVersion... supportedDraftVersions) {
        this(null, supportedDraftVersions);
    }
    
    /**
     * Constructs Http2HandlerFilter.
     * 
     * @param threadPool the {@link ExecutorService} to be used to process {@link SynStreamFrame} and
     * {@link SynReplyFrame} frames, if <tt>null</tt> mentioned frames will be processed in the same thread they were parsed.
     * @param supportedDraftVersions HTTP2 draft versions this filter has to support
     */
    public Http2BaseFilter(final ExecutorService threadPool,
            final DraftVersion... supportedDraftVersions) {
        this.threadPool = threadPool;
        
        this.supportedHttp2Drafts =
                (supportedDraftVersions == null || supportedDraftVersions.length == 0)
                ? Arrays.copyOf(ALL_HTTP2_DRAFTS, ALL_HTTP2_DRAFTS.length)
                : Arrays.copyOf(supportedDraftVersions, supportedDraftVersions.length);
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
     * Sets the default maximum number of concurrent streams allowed for one session.
     * Negative value means "unlimited".
     * @param maxConcurrentStreams
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
     * Sets the default initial stream window size (in bytes) for new HTTP2 connections.
     * @param initialWindowSize
     */
    public void setInitialWindowSize(final int initialWindowSize) {
        this.initialWindowSize = initialWindowSize;
    }

    /**
     * @return the default initial stream window size (in bytes) for new HTTP2 connections.
     */
    public int getInitialWindowSize() {
        return initialWindowSize;
    }    

    protected boolean processFrames(final FilterChainContext ctx,
            final Http2Connection http2Connection,
            final List<Http2Frame> framesList) {
        
        if (framesList == null || framesList.isEmpty()) {
            return true;
        }
        
        try {
            try {
                for (Http2Frame inFrame : framesList) {
                    try {
                        processInFrame(http2Connection, ctx, inFrame);
                    } catch (Http2StreamException e) {
                        if (LOGGER.isLoggable(Level.FINE)) {
                            LOGGER.log(Level.FINE, "Http2StreamException occurred on connection=" +
                                    ctx.getConnection() + " during Http2Frame processing", e);
                        }

                        final int streamId = e.getStreamId();

                        if (streamId == 0) {
                            throw new Http2ConnectionException(ErrorCode.PROTOCOL_ERROR);
                        }

                        sendRstStream(ctx, http2Connection,
                                streamId, e.getErrorCode());
                    }
                }
            } finally {
                // Don't forget to clean framesList, because it will be reused
                framesList.clear();
            }

            final List<Http2Stream> streamsToFlushInput =
                    http2Connection.streamsToFlushInput;
            for (Http2Stream streamsToFlush : streamsToFlushInput) {
                streamsToFlush.flushInputData();
            }
            streamsToFlushInput.clear();
            
            return true;
        } catch (Http2ConnectionException e) {
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.log(Level.FINE, "Http2ConnectionException occurred on connection=" +
                        ctx.getConnection() + " during Http2Frame processing", e);
            }
            sendGoAwayAndClose(ctx, http2Connection, e.getErrorCode());
        } catch (IOException e) {
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.log(Level.FINE, "IOException occurred on connection=" +
                        ctx.getConnection() + " during Http2Frame processing", e);
            }
            sendGoAwayAndClose(ctx, http2Connection, ErrorCode.INTERNAL_ERROR);
        }
        
        return false;
    }


    DraftVersion[] getSupportedHttp2Drafts() {
        return supportedHttp2Drafts;
    }
    
    protected boolean checkRequestHeadersOnUpgrade(
            final HttpRequestPacket httpRequest) {
        
        if (httpRequest.getUpgradeDC().isNull()) {
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
                LOGGER.finest("checkRequestHeadersOnUpgrade: failed incorrect connection: " + connectionHeaderDC);
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
        
        if (httpResponse.getUpgradeDC().isNull()) {
            // No Upgrade header
            return false;
        }
        
        // Check "Connection: Upgrade, HTTP2-Settings" header
        final DataChunk connectionHeaderDC =
                httpResponse.getHeaders().getValue(Header.Connection);
        
        if (connectionHeaderDC == null || connectionHeaderDC.isNull() ||
                !connectionHeaderDC.equals(Header.Upgrade.getBytes())) {
            // No "Connection: Upgrade"
            return false;
        }
            
        return true;
    }
    
    protected SettingsFrame getHttp2UpgradeSettings(
            final HttpRequestPacket httpRequest) {
        
        final DataChunk http2Settings =
                httpRequest.getHeaders().getValue(Header.HTTP2Settings);
        
        return http2Settings != null
                ? SettingsFrame.fromBase64Uri(http2Settings)
                : null;
    }
    
    protected DraftVersion getHttp2UpgradingVersion(
            final HttpHeader httpHeader) {
        final DataChunk upgradeDC= httpHeader.getUpgradeDC();

        assert upgradeDC != null && !upgradeDC.isNull(); // should've been check before
        
        // Check "Upgrade: h2c-XX" header
        if (!upgradeDC.startsWith(HTTP2_CLEAR_TCP_UPGRADE_SIGNATURE, 0)) {
            return null;
        }

        final int versionOffs = HTTP2_CLEAR_TCP_UPGRADE_SIGNATURE.length() + 1;
        final int versionLen = upgradeDC.getLength() - versionOffs;

        final String version = versionLen <= 0
                ? "0" // reserved for HTTP/2.0 standard
                : upgradeDC.toString(versionOffs, versionOffs + versionLen);

        DraftVersion foundVersion = null;
        for (DraftVersion draft : getSupportedHttp2Drafts()) {
            if (draft.getVersion().equals(version)) {
                foundVersion = draft;
            }
        }
        
        return foundVersion;
    }
    
    // ------------------------------------------------------- Protected Methods


    /**
     * Callback method, called when {@link HttpPacket} parsing has been completed.
     *
     * @param httpHeader {@link HttpHeader}, which represents parsed HTTP packet header
     * @param ctx        processing context.
     * @return <code>true</code> if an error has occurred while processing
     *         the header portion of the HTTP request, otherwise returns
     *         <code>false</code>.
     *
     * @since 2.3.3
     */
    protected boolean onHttpPacketParsed(HttpHeader httpHeader, FilterChainContext ctx) {
        return false;
    }


    /**
     * Callback invoked when the HTTP message header parsing is complete.
     *
     * @param httpHeader {@link HttpHeader}, which represents parsed HTTP packet header
     * @param buffer     {@link Buffer} the header was parsed from
     * @param ctx        processing context.
     * @return <code>true</code> if an error has occurred while processing
     *         the header portion of the HTTP request, otherwise returns
     *         <code>false</code>.
     *
     * @since 2.3.3
     */
    protected boolean onHttpHeaderParsed(HttpHeader httpHeader,
                                         Buffer buffer,
                                         FilterChainContext ctx) {
        return false;
    }


    /**
     * <p>
     * Invoked when either the request line or status line has been parsed.
     * <p/>
     * </p>
     *
     * @param httpHeader {@link HttpHeader}, which represents HTTP packet header
     * @param ctx        processing context.
     *
     * @since 2.3.3
     */
    protected void onInitialLineParsed(final HttpHeader httpHeader,
                                       final FilterChainContext ctx) {
    }


    /**
     * <p>
     * Invoked when the intial response line has been  encoded in preparation
     * to being transmitted to the user-agent.
     * </p>
     *
     * @param httpHeader {@link HttpHeader}, which represents HTTP packet header
     * @param ctx        processing context.
     *
     * @since 2.3.3
     */
    protected void onInitialLineEncoded(final HttpHeader httpHeader,
                                        final FilterChainContext ctx) {
    }


    /**
     * <p>
     * Invoked when all headers of the packet have been parsed.  Depending on the
     * transfer encoding being used by the current request, this method may be
     * invoked multiple times.
     * </p>
     *
     * @param httpHeader {@link HttpHeader}, which represents HTTP packet header
     * @param ctx        processing context.
     *
     * @since 2.3.3
     */
    protected void onHttpHeadersParsed(final HttpHeader httpHeader,
                                       final FilterChainContext ctx) {
    }


    /**
     * <p>
     * Invoked when HTTP headers have been encoded in preparation to being
     * transmitted to the user-agent.
     * </p>
     *
     * @param httpHeader {@link HttpHeader}, which represents HTTP packet header
     * @param ctx        processing context.
     *
     * @since 2.3.3
     */
    protected void onHttpHeadersEncoded(final HttpHeader httpHeader,
                                        final FilterChainContext ctx) {
    }


    /**
     * <p>
     * Invoked as request/response body content has been processed by this
     * {@link org.glassfish.grizzly.filterchain.Filter}.
     * </p>
     *
     * @param content request/response body content
     * @param ctx     processing context.
     *
     * @since 2.3.3
     */
    protected void onHttpContentParsed(final HttpContent content,
                                       final FilterChainContext ctx) {
    }

    /**
     * <p>
     * Invoked when a HTTP body chunk has been encoded in preparation to being
     * transmitted to the user-agent.
     * </p>
     *
     * @param content {@link HttpContent}, which represents HTTP packet header
     * @param ctx     processing context.
     *
     * @since 2.3.3
     */
    protected void onHttpContentEncoded(final HttpContent content,
                                        final FilterChainContext ctx) {
    }


    /**
     * <p>
     * Callback which is invoked when parsing an HTTP message header fails.
     * The processing logic has to take care about error handling and following
     * connection closing.
     * </p>
     *
     * @param httpHeader {@link HttpHeader}, which represents HTTP packet header
     * @param ctx        the {@link FilterChainContext} processing this request
     * @param t          the cause of the error
     *
     * @since 2.3.3
     */
    protected void onHttpHeaderError(final HttpHeader httpHeader,
                                     final FilterChainContext ctx,
                                     final Throwable t) throws IOException {
    }

    /**
     * <p>
     * Callback which is invoked when parsing an HTTP message payload fails.
     * The processing logic has to take care about error handling and following
     * connection closing.
     * </p>
     *
     * @param httpHeader {@link HttpHeader}, which represents HTTP packet header
     * @param ctx        the {@link FilterChainContext} processing this request
     * @param t          the cause of the error
     *
     * @since 2.3.3
     */
    protected void onHttpContentError(HttpHeader httpHeader,
                                      FilterChainContext ctx,
                                      Throwable t) throws IOException {
    }


    // --------------------------------------------------------- Private Methods
    
    private void processInFrame(final Http2Connection http2Connection,
                                final FilterChainContext context,
                                final Http2Frame frame)
     throws Http2StreamException, Http2ConnectionException, IOException {

        http2Connection.checkFrameSequenceSemantics(frame);
        
        switch (frame.getType()) {
            case DataFrame.TYPE: {
                processDataFrame(http2Connection, context, (DataFrame) frame);
                break;
            }
            case PriorityFrame.TYPE: {
                // @TODO
                break;
            }
            case HeadersFrame.TYPE:
            case PushPromiseFrame.TYPE:
            case ContinuationFrame.TYPE: {
                processHeadersFrame(http2Connection, context, frame);
                break;
            }
            case SettingsFrame.TYPE: {
                processSettingsFrame(http2Connection, context, frame);
                break;
            }
            case PingFrame.TYPE: {
                processPingFrame(http2Connection, frame);
                break;
            }
            case RstStreamFrame.TYPE: {
                processRstStreamFrame(http2Connection, frame);
                break;
            }
            case GoAwayFrame.TYPE: {
                processGoAwayFrame(http2Connection, frame);
                break;
            }
            case WindowUpdateFrame.TYPE: {
                processWindowUpdateFrame(http2Connection, frame);
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

    private void processWindowUpdateFrame(final Http2Connection http2Connection,
            final Http2Frame frame) throws Http2StreamException {
        
        WindowUpdateFrame updateFrame = (WindowUpdateFrame) frame;
        final int streamId = updateFrame.getStreamId();
        final int delta = updateFrame.getWindowSizeIncrement();

        if (streamId == 0) {
            http2Connection.getOutputSink().onPeerWindowUpdate(delta);
        } else {
            final Http2Stream stream = http2Connection.getStream(streamId);

            if (stream != null) {
                stream.getOutputSink().onPeerWindowUpdate(delta);
            } else {
                if (LOGGER.isLoggable(Level.FINE)) {
                    final StringBuilder sb = new StringBuilder(64);
                    sb.append("\nStream id=")
                            .append(streamId)
                            .append(" was not found. Ignoring the message.");
                    LOGGER.fine(sb.toString());
                }
            }
        }
    }

    private void processGoAwayFrame(final Http2Connection http2Connection,
                                    final Http2Frame frame) {

        GoAwayFrame goAwayFrame = (GoAwayFrame) frame;
        http2Connection.setGoAwayByPeer(goAwayFrame.getLastStreamId());
    }
    
    private void processSettingsFrame(final Http2Connection http2Connection,
            final FilterChainContext context, final Http2Frame frame)
            throws Http2ConnectionException {
        
        final SettingsFrame settingsFrame = (SettingsFrame) frame;
        
        if (settingsFrame.isAck()) {
            // ignore for now
            return;
        }
        
        applySettings(http2Connection, settingsFrame);
        
        sendSettingsAck(http2Connection, context);
    }
    
    void applySettings(final Http2Connection http2Connection,
            final SettingsFrame settingsFrame) throws Http2ConnectionException {

        final int numberOfSettings = settingsFrame.getNumberOfSettings();
        for (int i = 0; i < numberOfSettings; i++) {
            final SettingsFrame.Setting setting = settingsFrame.getSettingByIndex(i);
            
            switch (setting.getId()) {
                case SettingsFrame.SETTINGS_HEADER_TABLE_SIZE:
                    break;
                case SettingsFrame.SETTINGS_ENABLE_PUSH:
                    break;
                case SettingsFrame.SETTINGS_MAX_CONCURRENT_STREAMS:
                    http2Connection.setPeerMaxConcurrentStreams(setting.getValue());
                    break;
                case SettingsFrame.SETTINGS_INITIAL_WINDOW_SIZE:
                    http2Connection.setPeerStreamWindowSize(setting.getValue());
                    break;
                case SettingsFrame.SETTINGS_MAX_FRAME_SIZE:
                    http2Connection.setPeerMaxFramePayloadSize(setting.getValue());
                    break;
                case SettingsFrame.SETTINGS_MAX_HEADER_LIST_SIZE:
                    break;
            }
        }
    }

    private void processPingFrame(final Http2Connection http2Connection,
                             final Http2Frame frame) {
        PingFrame pingFrame = (PingFrame) frame;
        // Send the same ping message back, but set the ack flag
        pingFrame.setFlag(PingFrame.ACK_FLAG);
        http2Connection.getOutputSink().writeDownStream(pingFrame);
    }

    private void processRstStreamFrame(final Http2Connection http2Connection,
                                  final Http2Frame frame) {

        final RstStreamFrame rstFrame = (RstStreamFrame) frame;
        final int streamId = rstFrame.getStreamId();
        final Http2Stream stream = http2Connection.getStream(streamId);
        if (stream == null) {
            // If the stream is not found - just ignore the rst
            frame.recycle();
            return;
        }
        
        // Notify the stream that it has been reset remotely
        stream.resetRemotely();
    }
    
    private void processHeadersFrame(final Http2Connection http2Connection,
                                  final FilterChainContext context,
                                  final Http2Frame frame) throws IOException {
        final HeaderBlockFragment headerFragment = (HeaderBlockFragment) frame;
        
        final HeadersDecoder headersDecoder = http2Connection.getHeadersDecoder();
        
        if (headerFragment.getCompressedHeaders().hasRemaining()) {
            headersDecoder.append(headerFragment.takePayload());
        }

        final boolean isEOH = headerFragment.isEndHeaders();
        
        if (!headersDecoder.isProcessingHeaders()) { // first headers frame (either HeadersFrame or PushPromiseFrame)
            headersDecoder.setFirstHeaderFrame((HeaderBlockHead) headerFragment);
        } else {
            headerFragment.recycle();
        }
        
        if (!isEOH) {
            return; // wait for more header frames to come
        }
        
        final HeaderBlockHead firstHeaderFrame =
                headersDecoder.finishHeader();

        try {
            processCompleteHeader(http2Connection, context, firstHeaderFrame);
        } finally {
            firstHeaderFrame.recycle();
        }
    }

    protected void processCompleteHeader(
            final Http2Connection http2Connection,
            final FilterChainContext context,
            final HeaderBlockHead firstHeaderFrame) throws IOException {
        assert firstHeaderFrame.getType() == HeadersFrame.TYPE;
        
        if (http2Connection.isServer()) {
            // it's a request
            processInRequest(http2Connection, context,
                    (HeadersFrame) firstHeaderFrame);
        } else {
            // it's response
            processInResponse(http2Connection, context,
                    (HeadersFrame) firstHeaderFrame);
        }
    }

    private void processInRequest(final Http2Connection http2Connection,
            final FilterChainContext context, final HeadersFrame headersFrame)
            throws IOException {

        final Http2Request request = Http2Request.create();
        request.setConnection(context.getConnection());

        final Http2Stream stream;

        stream = http2Connection.acceptStream(request,
                headersFrame.getStreamId(), 0, 0);
        if (stream == null) { // GOAWAY has been sent, so ignoring this request
            request.recycle();
            return;
        }

        DecoderUtils.decodeRequestHeaders(http2Connection, request);
        onHttpHeadersParsed(request, context);        

        prepareIncomingRequest(stream, request);
        stream.onHeaderBlockRcv(headersFrame);
        
        // stream HEADERS frame will be transformed to HTTP request packet
        if (headersFrame.isEndStream()) {
            request.setExpectContent(false);
        }

        final boolean isExpectContent = request.isExpectContent();
        if (!isExpectContent) {
            stream.inputBuffer.terminate(IN_FIN_TERMINATION);
        }

        sendUpstream(http2Connection, stream, request, isExpectContent);
    }

    private void processInResponse(final Http2Connection http2Connection,
            final FilterChainContext context,
            final HeadersFrame headersFrame)
            throws Http2ConnectionException, IOException {

        final Http2Stream stream = http2Connection.getStream(
                headersFrame.getStreamId());
        if (stream == null) { // Stream doesn't exist
            return;
        }
        
        final HttpRequestPacket request = stream.getRequest();
        
        HttpResponsePacket response = request.getResponse();
        if (response == null) {
            response = Http2Response.create();
        }
        
        final boolean isEOS = headersFrame.isEndStream();
        if (isEOS) {
            response.setExpectContent(false);
            stream.inputBuffer.terminate(IN_FIN_TERMINATION);
        }
        
        DecoderUtils.decodeResponseHeaders(http2Connection, response);
        onHttpHeadersParsed(response, context);        

        stream.onHeaderBlockRcv(headersFrame);
        bind(request, response);

        if (isEOS) {
            onHttpPacketParsed(response, context);
        }

        sendUpstream(http2Connection, stream, response, !isEOS);
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public NextAction handleWrite(final FilterChainContext ctx) throws IOException {
        final Http2State http2State = Http2State.get(ctx.getConnection());
        
        if (http2State == null || http2State.isNeverHttp2()) {
            return ctx.getInvokeAction();
        }
        
        final Object message = ctx.getMessage();
        
        final Http2Connection http2Connection = obtainHttp2Connection(ctx, false);

        if (http2Connection.isHttp2OutputEnabled() &&
                HttpPacket.isHttp(message)) {

            // Get HttpPacket
            final HttpPacket httpPacket = ctx.getMessage();
            final HttpHeader httpHeader = httpPacket.getHttpHeader();

            if (httpHeader.isRequest()) {
                // client side, sending HTTP request
                processOutgoingRequest(ctx, http2Connection,
                        (HttpRequestPacket) httpHeader, httpPacket);
            } else {
                // server side, sending back the HTTP response
                processOutgoingResponse(ctx, http2Connection,
                        (HttpResponsePacket) httpHeader, httpPacket);
            }

        } else {
            final TransportContext transportContext = ctx.getTransportContext();
            http2Connection.getOutputSink().writeDownStream(message,
                    transportContext.getCompletionHandler(),
                        transportContext.getMessageCloner());
        }

        return ctx.getStopAction();
    }

    @SuppressWarnings("unchecked")
    private void processOutgoingRequest(final FilterChainContext ctx,
            final Http2Connection http2Connection,
            final HttpRequestPacket request,
            final HttpPacket entireHttpPacket) throws IOException {

        if (!http2Connection.isHttp2OutputEnabled()) {
            // HTTP2 output is not enabled yet
            return;
        }
        
        if (!request.isCommitted()) {
            prepareOutgoingRequest(request);
        }

        final Http2Stream stream = Http2Stream.getStreamFor(request);
        
        if (stream == null) {
            processOutgoingRequestForNewStream(ctx, http2Connection, request,
                    entireHttpPacket);
        } else {
            final TransportContext transportContext = ctx.getTransportContext();

            stream.getOutputSink().writeDownStream(entireHttpPacket,
                                       ctx,
                                       transportContext.getCompletionHandler(),
                                       transportContext.getMessageCloner());
        }
    }
    
    @SuppressWarnings("unchecked")
    private void processOutgoingRequestForNewStream(final FilterChainContext ctx,
            final Http2Connection http2Connection,
            final HttpRequestPacket request,
            final HttpPacket entireHttpPacket) throws IOException {

        
        final ReentrantLock newStreamLock = http2Connection.getNewClientStreamLock();
        newStreamLock.lock();
        
        try {
            final Http2Stream stream = http2Connection.openStream(
                    request,
                    http2Connection.getNextLocalStreamId(),
                    0, 0, !request.isExpectContent());

            if (stream == null) {
                throw new IOException("Http2Connection is closed");
            }
            
            // Make sure request contains the association with the HTTP2 stream
            request.setAttribute(Http2Stream.HTTP2_STREAM_ATTRIBUTE, stream);
            
            final TransportContext transportContext = ctx.getTransportContext();

            stream.getOutputSink().writeDownStream(entireHttpPacket,
                                       ctx,
                                       transportContext.getCompletionHandler(),
                                       transportContext.getMessageCloner());

        } finally {
            newStreamLock.unlock();
        }
    }
    
    @SuppressWarnings("unchecked")
    private void processOutgoingResponse(final FilterChainContext ctx,
            final Http2Connection http2Connection,
            final HttpResponsePacket response,
            final HttpPacket entireHttpPacket) throws IOException {

        final Http2Stream stream = Http2Stream.getStreamFor(response);
        assert stream != null;

        if (!response.isCommitted()) {
            prepareOutgoingResponse(response);
            pushAssociatedResoureses(ctx, stream);
        }

        final TransportContext transportContext = ctx.getTransportContext();

        stream.getOutputSink().writeDownStream(entireHttpPacket,
                                   ctx,
                                   transportContext.getCompletionHandler(),
                                   transportContext.getMessageCloner());
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public NextAction handleEvent(final FilterChainContext ctx,
            final FilterChainEvent event) throws IOException {
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
     * Creates {@link Http2Connection} with preconfigured initial-windows-size and
     * max-concurrent-streams
     * @param draftVersion
     * @param connection
     * @param isServer
     * @return {@link Http2Connection}
     */
    protected Http2Connection createHttp2Connection(final DraftVersion draftVersion,
            final Connection connection,
            final boolean isServer) {
        
        final Http2Connection http2Connection =
                draftVersion.newConnection(connection, isServer, this);
        
        if (initialWindowSize != -1) {
            http2Connection.setLocalStreamWindowSize(initialWindowSize);
        }
    
        if (maxConcurrentStreams != -1) {
            http2Connection.setLocalMaxConcurrentStreams(maxConcurrentStreams);
        }
        
        Http2Connection.bind(connection, http2Connection);
        
        return http2Connection;
    }
    
    protected void onPrefaceReceived(final Http2Connection http2Connection) {
    }
    
    void sendUpstream(final Http2Connection http2Connection,
            final Http2Stream stream, final HttpHeader httpHeader,
            final boolean isExpectContent) {
        
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
                http2Connection.sendMessageUpstream(stream,
                        HttpContent.builder(httpHeader)
                        .last(!isExpectContent)
                        .build());
            } finally {
                Threads.setService(false);
            }
        } else {
            threadPool.execute(new Runnable() {
                @Override
                public void run() {
                    http2Connection.sendMessageUpstream(stream,
                            HttpContent.builder(httpHeader)
                            .last(!isExpectContent)
                            .build());
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
        response.setProtocol(Protocol.HTTP_2_0);

        String contentType = response.getContentType();
        if (contentType != null) {
            response.getHeaders().setValue(Header.ContentType).setString(contentType);
        }

        if (response.getContentLength() != -1) {
            // FixedLengthTransferEncoding will set proper Content-Length header
            FIXED_LENGTH_ENCODING.prepareSerialize(null, response, null);
        }
        
        if (!response.containsHeader(Header.Date)) {
            response.getHeaders().addValue(Header.Date)
                    .setBytes(FastHttpDateFormat.getCurrentDateBytes());
        }
    }    

    void sendRstStream(final FilterChainContext ctx,
            final Http2Connection http2Connection,
            final int streamId, final ErrorCode errorCode) {

        final RstStreamFrame rstStreamFrame = RstStreamFrame.builder()
                .errorCode(errorCode)
                .streamId(streamId)
                .build();

        ctx.write(frameCodec.serializeAndRecycle(http2Connection, rstStreamFrame));
    }

    @SuppressWarnings("unchecked")
    private void sendGoAwayAndClose(final FilterChainContext ctx,
            final Http2Connection http2Connection, final ErrorCode errorCode) {

        final Http2Frame goAwayFrame = 
                http2Connection.setGoAwayLocally(errorCode);

        if (goAwayFrame != null) {
            final Connection connection = ctx.getConnection();
            ctx.write(frameCodec.serializeAndRecycle(http2Connection, goAwayFrame));
            connection.closeSilently();
        }
    }

    /**
     * Obtain {@link Http2Connection} associated with the {@link Connection}
     * and prepare it for use.
     * 
     * @param context {@link FilterChainContext}
     * @param isUpStream <tt>true</tt> if the {@link FilterChainContext} represents
     *          upstream {@link FilterChain} execution, or <tt>false</tt> otherwise
     * @return {@link Http2Connection} associated with the {@link Connection}
     *          and prepare it for use
     */
    protected final Http2Connection obtainHttp2Connection(
            final FilterChainContext context,
            final boolean isUpStream) {
        
        return obtainHttp2Connection(null, context, isUpStream);
    }

    /**
     * Obtain {@link Http2Connection} associated with the {@link Connection}
     * and prepare it for use.
     * 
     * @param http2State {@link Http2State} associated with the {@link Connection}
     * @param context {@link FilterChainContext}
     * @param isUpStream <tt>true</tt> if the {@link FilterChainContext} represents
     *          upstream {@link FilterChain} execution, or <tt>false</tt> otherwise
     * @return {@link Http2Connection} associated with the {@link Connection}
     *          and prepare it for use
     */
    final Http2Connection obtainHttp2Connection(
            final Http2State http2State,
            final FilterChainContext context,
            final boolean isUpStream) {
        final Connection connection = context.getConnection();
        
        Http2Connection http2Connection = http2State != null
                ? http2State.getHttp2Connection()
                : null;
        
        if (http2Connection == null) {
            http2Connection = Http2Connection.get(connection);
            if (http2Connection == null) {

                http2Connection = createHttp2Connection(supportedHttp2Drafts[0],
                        connection, true);
            }
        }
        
        http2Connection.setupFilterChains(context, isUpStream);

        return http2Connection;
    }

    protected void sendSettings(final Http2Connection http2Connection,
            final FilterChainContext context) {

        context.write(
                frameCodec.serializeAndRecycle(
                        http2Connection,
                        http2Connection.prepareSettings().build()));
    }
    
    private void sendSettingsAck(final Http2Connection http2Connection,
            final FilterChainContext context) {

        final SettingsFrame frame = SettingsFrame.builder()
                .setAck()
                .build();
        
        context.write(
                frameCodec.serializeAndRecycle(
                        http2Connection, frame)
        );
    }
    
    private static void processDataFrame(final Http2Connection http2Connection,
            final FilterChainContext context,
            final DataFrame dataFrame) throws Http2StreamException {


        final Buffer data = dataFrame.getData();
        final Http2Stream stream = http2Connection.getStream(dataFrame.getStreamId());

        if (stream == null) {

            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.log(Level.FINE, "Data frame received for non-existent stream: connection={0}, frame={1}, stream={2}",
                        new Object[]{context.getConnection(), dataFrame, dataFrame.getStreamId()});
            }

            final int dataSize = data.remaining();

            dataFrame.recycle();

            http2Connection.ackConsumedData(dataSize);

            throw new Http2StreamException(dataFrame.getStreamId(), ErrorCode.STREAM_CLOSED);
        }

        final Http2StreamException error = stream.assertCanAcceptData();
        
        if (error != null) {
            final int dataSize = data.remaining();
            dataFrame.recycle();
            http2Connection.ackConsumedData(dataSize);
            
            throw error;
        }
        
        stream.offerInputData(data, dataFrame.isFlagSet(DataFrame.END_STREAM));
    }

    private void pushAssociatedResoureses(final FilterChainContext ctx,
            final Http2Stream stream) throws IOException {
        final Map<String, PushResource> pushResourceMap =
                stream.getAssociatedResourcesToPush();
        
        if (pushResourceMap != null) {           
            final int streamId = stream.getId();
            final HttpRequestPacket streamReq = stream.getRequest();
            final String referer = composeRefererOf(stream.getRequest());
            
            final List<Pair<Http2Stream, Source>> pushStreams =
                    new ArrayList<Pair<Http2Stream, Source>>(pushResourceMap.size());
            
            final Http2Connection http2Connection = stream.getHttp2Connection();
            
            boolean isNewClientStreamLocked = true;
            boolean isDeflaterLocked = true;
            http2Connection.getNewClientStreamLock().lock();
            
            try {
                for (Map.Entry<String, PushResource> entry : pushResourceMap.entrySet()) {
                    final PushResource pushResource = entry.getValue();
                    final Source source = pushResource.getSource();
                    
                    final Http2Request request = Http2Request.create();
                    final HttpResponsePacket response = request.getResponse();
                    
                    request.setRequestURI(entry.getKey());
                    request.setProtocol(Protocol.HTTP_2_0);
                    request.setMethod(Method.GET);
                    
                    final MimeHeaders reqHeaders = request.getHeaders();
                    
                    DataChunk valueDC = reqHeaders.setValue(Header.Host);
                    if (valueDC.isNull()) {
                        valueDC.setString(streamReq.getHeader(Header.Host));
                    }
                    
                    final String userAgent = streamReq.getHeader(Header.UserAgent);
                    if (userAgent != null) {
                        valueDC = reqHeaders.setValue(Header.UserAgent);
                        if (valueDC.isNull()) {
                            valueDC.setString(userAgent);
                        }
                    }
                    
                    valueDC = reqHeaders.setValue(Header.Referer);
                    if (valueDC.isNull()) {
                        valueDC.setString(referer);
                    }
                    
                    for (String cookie : streamReq.getHeaders().values(Header.Cookie)) {
                        request.addHeader(Header.Cookie, cookie);
                    }
                    
                    request.setSecure(streamReq.isSecure());
                    
                    response.setStatus(pushResource.getStatusCode());
                    response.setProtocol(Protocol.HTTP_2_0);
                    response.setContentType(pushResource.getContentType());
                    
                    if (source != null) {
                        response.setContentLengthLong(source.remaining());
                    }
                    
                    // Add extra headers if any
                    final Map<String, String> extraHeaders = pushResource.getHeaders();
                    if (extraHeaders != null) {
                        for (Map.Entry<String, String> headerEntry : extraHeaders.entrySet()) {
                            response.addHeader(headerEntry.getKey(), headerEntry.getValue());
                        }
                    }
                    
                    prepareOutgoingRequest(request);
                    prepareOutgoingResponse(response);
                    
                    try {
                        final Http2Stream pushStream = http2Connection.openStream(
                                request,
                                http2Connection.getNextLocalStreamId(), streamId,
                                pushResource.getPriority(),
                                false);
                        pushStream.inputBuffer.terminate(IN_FIN_TERMINATION);
                        
                        pushStreams.add(
                                new Pair<Http2Stream, Source>(pushStream, source));
                    } catch (Exception e) {
                        LOGGER.log(Level.FINE,
                                "Can not push: " + entry.getKey(), e);
                    }
                }
                
                // Push streams are created - now we're ready to to serialize
                // push promises.
                // Lock deflater, unlock newClientStreamLock.
                // This order guarantees that nobody can create a new stream
                // and serialize its headers asynchronously before we finish
                // the push promises serialization.
                http2Connection.getDeflaterLock().lock();
                http2Connection.getNewClientStreamLock().unlock();
                isDeflaterLocked = true;
                isNewClientStreamLocked = false;

                List<Http2Frame> pushPromiseFrames = null;
                
                for (Pair<Http2Stream, Source> pair : pushStreams) {
                    final Http2Stream pushStream = pair.getFirst();
                    pushPromiseFrames =
                            http2Connection.encodeHttpRequestAsPushPromiseFrames(
                                    ctx, pushStream.getRequest(), streamId,
                                    pushStream.getId(), pushPromiseFrames);
                }
                
                http2Connection.getOutputSink().writeDownStream(
                        pushPromiseFrames);
                
                // release the deflater lock
                http2Connection.getDeflaterLock().unlock();
                isDeflaterLocked = false;
                
                // now we're ready to initiate payload push
                for (Pair<Http2Stream, Source> pair : pushStreams) {
                    final Http2Stream pushStream = pair.getFirst();
                    pushStream.getOutputSink().writeDownStream(
                            pair.getSecond(), ctx);
                }
            } finally {
                if (isDeflaterLocked) {
                    http2Connection.getDeflaterLock().unlock();
                }
                
                if (isNewClientStreamLocked) {
                    http2Connection.getNewClientStreamLock().unlock();
                }
            }
        }
    }

    private String composeRefererOf(final HttpRequestPacket request) {
        return new StringBuilder().append(request.isSecure() ? "https" : "http")
                .append("://")
                .append(request.getHeader(Header.Host))
                .append(request.getRequestURI())
                .toString();
    }
}
