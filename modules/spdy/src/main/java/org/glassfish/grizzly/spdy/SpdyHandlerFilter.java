/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012-2014 Oracle and/or its affiliates. All rights reserved.
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
import java.util.Arrays;
import java.util.LinkedHashSet;
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
import org.glassfish.grizzly.IOEvent;
import org.glassfish.grizzly.filterchain.TransportFilter;
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
    
    private static final SpdyVersion[] ALL_SPDY_VERSIONS =
            {SpdyVersion.SPDY_3_1, SpdyVersion.SPDY_3};
    
    private static final TransferEncoding FIXED_LENGTH_ENCODING =
            new FixedLengthTransferEncoding();

    private final ClientNpnNegotiator DEFAULT_CLIENT_NPN_NEGOTIATOR =
            new ClientNpnNegotiator();

    private final SpdyMode spdyMode;
    private final SpdyVersion[] supportedSpdyVersions;
    
    private final ExecutorService threadPool;

    private volatile int maxConcurrentStreams = DEFAULT_MAX_CONCURRENT_STREAMS;
    private volatile int initialWindowSize = DEFAULT_INITIAL_WINDOW_SIZE;

    /**
     * Constructs SpdyHandlerFilter.
     * 
     * @param spdyMode the {@link SpdyMode}.
     */
    public SpdyHandlerFilter(final SpdyMode spdyMode) {
        this(spdyMode, null, ALL_SPDY_VERSIONS);
    }

    /**
     * Constructs SpdyHandlerFilter.
     * 
     * @param spdyMode the {@link SpdyMode}.
     * @param supportedSpdyVersions SPDY versions this filter has to support
     */
    public SpdyHandlerFilter(final SpdyMode spdyMode,
            final SpdyVersion... supportedSpdyVersions) {
        this(spdyMode, null, supportedSpdyVersions);
    }
    
    /**
     * Constructs SpdyHandlerFilter.
     * 
     * @param spdyMode the {@link SpdyMode}.
     * @param threadPool the {@link ExecutorService} to be used to process {@link SynStreamFrame} and
     * {@link SynReplyFrame} frames, if <tt>null</tt> mentioned frames will be processed in the same thread they were parsed.
     * @param supportedSpdyVersions SPDY versions this filter has to support
     */
    public SpdyHandlerFilter(final SpdyMode spdyMode,
            final ExecutorService threadPool,
            final SpdyVersion... supportedSpdyVersions) {
        this.spdyMode = spdyMode;
        this.threadPool = threadPool;
        
        this.supportedSpdyVersions =
                (supportedSpdyVersions == null || supportedSpdyVersions.length == 0)
                ? ALL_SPDY_VERSIONS
                : Arrays.copyOf(supportedSpdyVersions, supportedSpdyVersions.length);
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
                    
                    final int streamId = e.getStreamId();
                    
                    if (streamId == 0) {
                        throw new SpdySessionException(0,
                                GoAwayFrame.PROTOCOL_ERROR_STATUS);
                    }
                    
                    sendRstStream(ctx, streamId, e.getRstReason());
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
                            
                            final int streamId = e.getStreamId();
                            
                            if (streamId == 0) {
                                throw new SpdySessionException(0,
                                        GoAwayFrame.PROTOCOL_ERROR_STATUS);
                            }
                            
                            sendRstStream(ctx, streamId, e.getRstReason());
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
            
            sendGoAwayAndClose(ctx, spdySession, e.getStreamId(),
                    e.getGoAwayStatus(), e.getRstReason());            
            return ctx.getSuspendAction();
        } catch (IOException e) {
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.log(Level.FINE, "IOException occurred on connection=" +
                        ctx.getConnection() + " during SpdyFrame processing", e);
            }
            
            sendGoAwayAndClose(ctx, spdySession, -1,
                    GoAwayFrame.INTERNAL_ERROR_STATUS, -1);
            
            return ctx.getSuspendAction();
        }
        
        return ctx.getStopAction();
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
    
    private void processInFrame(final SpdySession spdySession,
                                final FilterChainContext context,
                                final SpdyFrame frame)
     throws SpdyStreamException, SpdySessionException, IOException {

        if (frame.isService()) {
            processServiceFrame(spdySession, (ServiceFrame) frame);
        } else if (frame.getHeader().isControl()) {
            processControlFrame(spdySession, context, frame);
        } else {
            processDataFrame(spdySession, context, (DataFrame) frame);
        }

    }

    private void processControlFrame(final SpdySession spdySession,
                                     final FilterChainContext context,
                                     final SpdyFrame frame)
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

        if (streamId == 0) {
            spdySession.getOutputSink().onPeerWindowUpdate(delta);
        } else {
            final SpdyStream stream = spdySession.getStream(streamId);

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

    private void processGoAwayFrame(final SpdySession spdySession,
                                    final SpdyFrame frame) {

        GoAwayFrame goAwayFrame = (GoAwayFrame) frame;
        spdySession.setGoAwayByPeer(goAwayFrame.getLastGoodStreamId());
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
                            spdySession.setPeerStreamWindowSize(settingsFrame.getSetting(i));
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
        spdySession.getOutputSink().writeDownStream(pingFrame);
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
            throw new SpdySessionException(streamId,
                    GoAwayFrame.PROTOCOL_ERROR_STATUS,
                    RstStreamFrame.UNSUPPORTED_VERSION);
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
            decodeHeaders(spdyRequest, spdyStream, synStreamFrame, context);
        } finally {
            frame.recycle();
        }

        prepareIncomingRequest(spdyStream, spdyRequest);
        spdyStream.onSynFrameRcv();
        
        if (!isUnidirectional) {
            // Bidirectional syn stream will be transformed to HTTP request packet
            if (isFinSet) {
                spdyRequest.setExpectContent(false);
            }

            final boolean isExpectContent = spdyRequest.isExpectContent();
            if (!isExpectContent) {
                spdyStream.inputBuffer.terminate(IN_FIN_TERMINATION);
            }

            sendUpstream(spdySession, spdyStream, spdyRequest, isExpectContent);
        } else {
            // Unidirectional syn stream will be transformed to HTTP response packet
            spdyRequest.setExpectContent(false);
            spdyStream.outputSink.terminate(OUT_FIN_TERMINATION);
            
            final HttpResponsePacket spdyResponse = spdyRequest.getResponse();
            
            if (isFinSet) {
                spdyResponse.setExpectContent(false);
            }

            sendUpstream(spdySession, spdyStream, spdyResponse, !isFinSet);
        }
    }

    private void decodeHeaders(final HttpHeader httpHeader,
                               final SpdyStream spdyStream,
                               final HeadersProviderFrame headersProviderFrame,
                               final FilterChainContext ctx)
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
            try {
                if (decodedHeaders.hasArray()) {
                    processSynReplyHeadersArray((SpdyResponse) httpHeader,
                            decodedHeaders,
                            ctx,
                            this);
                } else {
                    processSynReplyHeadersBuffer((SpdyResponse) httpHeader,
                            decodedHeaders,
                            ctx,
                            this);
                }
            } catch (Exception e) {
                onHttpHeaderError(httpHeader, ctx, e);
                throw (RuntimeException) e;
            }
        }
    }
    
    private void processSynReply(final SpdySession spdySession,
                                 final FilterChainContext context,
                                 final SpdyFrame frame)
    throws SpdySessionException, IOException {

        SynReplyFrame synReplyFrame = (SynReplyFrame) frame;

        final int streamId = synReplyFrame.getStreamId();

        if (frame.getHeader().getVersion() != SPDY_VERSION) {
            throw new SpdySessionException(streamId,
                    GoAwayFrame.PROTOCOL_ERROR_STATUS,
                    RstStreamFrame.UNSUPPORTED_VERSION);
        }

        final SpdyStream spdyStream = spdySession.getStream(streamId);
        if (spdyStream == null) { // Stream doesn't exist
            frame.getHeader().getUnderlying().dispose();
            frame.recycle();
            return;
        }
        
        if (spdyStream.isUnidirectional()) {
            throw new SpdyStreamException(streamId, RstStreamFrame.PROTOCOL_ERROR,
                    "SynReply received on unidirectional stream");
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
            spdyStream.inputBuffer.terminate(IN_FIN_TERMINATION);
        }
        
        try {
            decodeHeaders(spdyResponse, spdyStream, synReplyFrame, context);
        } finally {
            frame.recycle();
        }

        spdyStream.onSynFrameRcv();
        bind(spdyRequest, spdyResponse);

        if (isFin) {
            onHttpPacketParsed(spdyResponse, context);
        }

        sendUpstream(spdySession, spdyStream, spdyResponse, !isFin);
    }


    
    @Override
    @SuppressWarnings("unchecked")
    public NextAction handleWrite(final FilterChainContext ctx) throws IOException {
        final Object message = ctx.getMessage();
        
        final SpdySession spdySession = checkSpdySession(ctx, false);

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
            spdySession.getOutputSink().writeDownStream(message,
                    transportContext.getCompletionHandler(),
                        transportContext.getMessageCloner());
        }

        return ctx.getStopAction();
    }

    @SuppressWarnings("unchecked")
    private void processOutgoingRequest(final FilterChainContext ctx,
            final SpdySession spdySession,
            final HttpRequestPacket request,
            final HttpPacket entireHttpPacket) throws IOException {

        
        if (!request.isCommitted()) {
            prepareOutgoingRequest(request);
        }

        final SpdyStream spdyStream = SpdyStream.getSpdyStream(request);
        
        if (spdyStream == null) {
            processOutgoingRequestForNewStream(ctx, spdySession, request,
                    entireHttpPacket);
        } else {
            final TransportContext transportContext = ctx.getTransportContext();

            spdyStream.getOutputSink().writeDownStream(entireHttpPacket,
                                       ctx,
                                       transportContext.getCompletionHandler(),
                                       transportContext.getMessageCloner());
        }
    }
    
    @SuppressWarnings("unchecked")
    private void processOutgoingRequestForNewStream(final FilterChainContext ctx,
            final SpdySession spdySession,
            final HttpRequestPacket request,
            final HttpPacket entireHttpPacket) throws IOException {

        
        final ReentrantLock newStreamLock = spdySession.getNewClientStreamLock();
        newStreamLock.lock();
        
        try {
            final SpdyStream spdyStream = spdySession.openStream(
                    request,
                    spdySession.getNextLocalStreamId(),
                    0, 0, 0, false, !request.isExpectContent());

            if (spdyStream == null) {
                throw new IOException("SpdySession is closed");
            }
            final TransportContext transportContext = ctx.getTransportContext();

            spdyStream.getOutputSink().writeDownStream(entireHttpPacket,
                                       ctx,
                                       transportContext.getCompletionHandler(),
                                       transportContext.getMessageCloner());

        } finally {
            newStreamLock.unlock();
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
            pushAssociatedResoureses(ctx, spdyStream);
        }

        final TransportContext transportContext = ctx.getTransportContext();

        spdyStream.getOutputSink().writeDownStream(entireHttpPacket,
                                   ctx,
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
                
                throw new SpdySessionException(spdyStreamId,
                        GoAwayFrame.PROTOCOL_ERROR_STATUS,
                        RstStreamFrame.FRAME_TOO_LARGE);
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

        if (spdyMode == SpdyMode.NPN) {
            final FilterChain filterChain = (FilterChain) connection.getProcessor();
            final int idx = filterChain.indexOfType(SSLFilter.class);
            if (idx != -1) { // use TLS NPN
                final SSLFilter sslFilter = (SSLFilter) filterChain.get(idx);
                NextProtoNegSupport.getInstance().configure(sslFilter);
                NextProtoNegSupport.getInstance().setClientSideNegotiator(
                        connection, getClientNpnNegotioator());

                final NextAction suspendAction = ctx.getSuspendAction();
                ctx.suspend();
                
                sslFilter.handshake(connection, new EmptyCompletionHandler<SSLEngine>() {

                    @Override
                    public void completed(final SSLEngine result) {
                        ctx.resumeNext();
                    }

                    @Override
                    public void failed(Throwable throwable) {
                        ctx.fail(throwable);
                    }
                    
                });
                
                connection.enableIOEvent(IOEvent.READ);
                return suspendAction;
            }
        } else {
            createSpdySession(supportedSpdyVersions[0], connection, false);            
        }
        
        return ctx.getInvokeAction();
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public NextAction handleEvent(final FilterChainContext ctx,
            final FilterChainEvent event) throws IOException {
        final Object type = event.type();
        
        if (type == HttpServerFilter.RESPONSE_COMPLETE_EVENT.type()) {
            final HttpContext httpContext = HttpContext.get(ctx);
            final SpdyStream spdyStream = (SpdyStream) httpContext.getContextStorage();
            spdyStream.onProcessingComplete();
            
            return ctx.getStopAction();
        } else if (type == TransportFilter.FlushEvent.TYPE) {
            assert event instanceof TransportFilter.FlushEvent;
            
            final HttpContext httpContext = HttpContext.get(ctx);
            final SpdyStream spdyStream = (SpdyStream) httpContext.getContextStorage();
            
            final TransportFilter.FlushEvent flushEvent =
                    (TransportFilter.FlushEvent) event;
            
            spdyStream.outputSink.flush(flushEvent.getCompletionHandler());
            
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
     * @param spdyVersion
     * @param connection
     * @param isServer
     * @return 
     */
    protected SpdySession createSpdySession(final SpdyVersion spdyVersion,
            final Connection connection,
            final boolean isServer) {
        
        final SpdySession spdySession =
                spdyVersion.newSession(connection, isServer, this);
        
        spdySession.setLocalStreamWindowSize(initialWindowSize);
        spdySession.setLocalMaxConcurrentStreams(maxConcurrentStreams);
        
        SpdySession.bind(connection, spdySession);
        
        return spdySession;
    }
    
    private void sendUpstream(final SpdySession spdySession,
            final SpdyStream spdyStream, final HttpHeader httpHeader,
            final boolean isExpectContent) {
        
        final HttpRequestPacket spdyReq = spdyStream.getSpdyRequest();
        final HttpContext httpContext = HttpContext.newInstance(spdyStream,
                spdyStream, spdyStream, spdyReq);
        spdyReq.getProcessingState().setHttpContext(httpContext);

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
        response.setProtocol(Protocol.HTTP_1_1);

        String contentType = response.getContentType();
        if (contentType != null) {
            response.getHeaders().setValue(Header.ContentType).setString(contentType);
        }

        if (response.getContentLength() != -1) {
            // FixedLengthTransferEncoding will set proper Content-Length header
            FIXED_LENGTH_ENCODING.prepareSerialize(null, response, null);
        }
    }    

    private static void sendRstStream(final FilterChainContext ctx,
            final int streamId, final int statusCode) {

        RstStreamFrame rstStreamFrame = RstStreamFrame.builder()
                .statusCode(statusCode)
                .streamId(streamId)
                .build();

        ctx.write(rstStreamFrame);
    }

    @SuppressWarnings("unchecked")
    private static void sendGoAwayAndClose(final FilterChainContext ctx,
            final SpdySession spdySession, final int streamId,
            final int goAwayStatus, final int rstStatus) {

        final SpdyFrame goAwayFrame = 
                spdySession.setGoAwayLocally(goAwayStatus);

        if (goAwayFrame != null) {
            final Connection connection = ctx.getConnection();
            final Object outputMessage;
            
            // if rstStatus >= 0 - prepend RstFrame
            if (rstStatus >= 0) {
                final SpdyFrame rstStreamFrame = RstStreamFrame.builder()
                        .statusCode(rstStatus)
                        .streamId(streamId)
                        .build();
                final List<SpdyFrame> frames = new ArrayList<SpdyFrame>(2);
                frames.add(rstStreamFrame);
                frames.add(goAwayFrame);
                
                outputMessage = frames;
            } else {
                outputMessage = goAwayFrame;
            }
            
            ctx.write(outputMessage, new EmptyCompletionHandler() {
                @Override
                public void failed(Throwable throwable) {
                    connection.closeSilently();
                }

                @Override
                public void completed(Object result) {
                    connection.closeSilently();
                }
            });
        }
    }

    private SpdySession checkSpdySession(final FilterChainContext context,
            final boolean isUpStream) {
        final Connection connection = context.getConnection();
        
        SpdySession spdySession = SpdySession.get(connection);
        if (spdySession == null) {
            spdySession = createSpdySession(supportedSpdyVersions[0],
                    connection, true);
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
                spdySession.getLocalStreamWindowSize() != DEFAULT_INITIAL_WINDOW_SIZE;
        
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
    
    private static void processDataFrame(final SpdySession spdySession,
            final FilterChainContext context,
            final DataFrame dataFrame) throws SpdyStreamException {


        final Buffer data = dataFrame.getData();
        final SpdyStream spdyStream = spdySession.getStream(dataFrame.getHeader().getStreamId());

        if (spdyStream == null) {

            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.log(Level.FINE, "Data frame received for non-existent stream: connection={0}, frame={1}, stream={2}",
                        new Object[]{context.getConnection(), dataFrame, dataFrame.getHeader().getStreamId()});
            }

            final int dataSize = data.remaining();

            dataFrame.recycle();

            spdySession.sendWindowUpdate(dataSize);

            return;
        }

        final SpdyStreamException error = spdyStream.assertCanAcceptData();
        
        if (error != null) {
            final int dataSize = data.remaining();
            dataFrame.recycle();
            spdySession.sendWindowUpdate(dataSize);
            
            throw error;
        }
        
        spdyStream.offerInputData(data, dataFrame.isFlagSet(DataFrame.FLAG_FIN));
    }

    private void pushAssociatedResoureses(final FilterChainContext ctx,
            final SpdyStream spdyStream) throws IOException {
        final Map<String, PushResource> pushResourceMap =
                spdyStream.getAssociatedResourcesToPush();
        
        if (pushResourceMap != null) {           
            final SpdySession spdySession = spdyStream.getSpdySession();
            final ReentrantLock lock = spdySession.getNewClientStreamLock();
            lock.lock();
            
            try {
                for (Map.Entry<String, PushResource> entry : pushResourceMap.entrySet()) {
                    final PushResource pushResource = entry.getValue();
                    final Source source =
                            pushResource.getSource();
                    
                    final SpdyRequest spdyRequest = SpdyRequest.create();
                    final HttpResponsePacket spdyResponse = spdyRequest.getResponse();
                    
                    spdyRequest.setRequestURI(entry.getKey());
                    spdyResponse.setStatus(pushResource.getStatusCode());
                    spdyResponse.setProtocol(Protocol.HTTP_1_1);
                    spdyResponse.setContentType(pushResource.getContentType());
                    
                    if (source != null) {
                        spdyResponse.setContentLengthLong(source.remaining());
                    }
                    
                    // Add extra headers if any
                    final Map<String, String> extraHeaders = pushResource.getHeaders();
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
                                pushResource.getPriority(),
                                0,
                                true,
                                false);
                        pushStream.inputBuffer.terminate(IN_FIN_TERMINATION);
                        prepareOutgoingResponse(spdyResponse);
                        
                        pushStream.getOutputSink().writeDownStream(source, ctx);
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


    // ---------------------------------------------------------- Nested Classes


    private class ClientNpnNegotiator implements ClientSideNegotiator {
        
        @Override
        public boolean wantNegotiate(final SSLEngine engine) {
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.log(Level.FINE, "NPN wantNegotiate. Connection={0}",
                        new Object[]{NextProtoNegSupport.getConnection(engine)});
            }
            return true;
        }

        @Override
        public String selectProtocol(final SSLEngine engine,
                                     final LinkedHashSet<String> protocols) {
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.log(Level.FINE, "NPN selectProtocol. Connection={0}, protocols={1}",
                        new Object[]{NextProtoNegSupport.getConnection(engine), protocols});
            }
            
            for (SpdyVersion version : supportedSpdyVersions) {
                final String versionDef = version.toString();
                if (protocols.contains(versionDef)) {
                    
                    createSpdySession(version,
                            NextProtoNegSupport.getConnection(engine), false);
                    
                    return versionDef;
                }
            }
            
            return "";
        }

        @Override
        public void onNoDeal(final SSLEngine engine) {
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.log(Level.FINE, "NPN onNoDeal. Connection={0}",
                        new Object[]{NextProtoNegSupport.getConnection(engine)});
            }
        }
    }

}
