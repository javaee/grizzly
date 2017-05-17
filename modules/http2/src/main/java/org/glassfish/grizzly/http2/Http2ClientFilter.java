/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2015-2017 Oracle and/or its affiliates. All rights reserved.
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
import java.util.concurrent.locks.ReentrantLock;
import javax.net.ssl.SSLEngine;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.EmptyCompletionHandler;
import org.glassfish.grizzly.IOEvent;
import org.glassfish.grizzly.filterchain.FilterChain;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.FilterChainContext.TransportContext;
import org.glassfish.grizzly.filterchain.FilterChainEvent;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.http.HttpContent;
import org.glassfish.grizzly.http.HttpContext;
import org.glassfish.grizzly.http.HttpEvents;
import org.glassfish.grizzly.http.HttpEvents.OutgoingHttpUpgradeEvent;
import org.glassfish.grizzly.http.HttpHeader;
import org.glassfish.grizzly.http.HttpPacket;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.http.HttpResponsePacket;
import org.glassfish.grizzly.http.HttpTrailer;
import org.glassfish.grizzly.http.Method;
import org.glassfish.grizzly.http.Protocol;
import org.glassfish.grizzly.http.util.Header;
import org.glassfish.grizzly.http.util.HeaderValue;
import org.glassfish.grizzly.http.util.HttpStatus;

import org.glassfish.grizzly.http.util.MimeHeaders;
import org.glassfish.grizzly.http2.frames.ErrorCode;
import org.glassfish.grizzly.http2.frames.HeaderBlockHead;
import org.glassfish.grizzly.http2.frames.Http2Frame;
import org.glassfish.grizzly.http2.frames.PushPromiseFrame;
import org.glassfish.grizzly.http2.frames.HeadersFrame;
import org.glassfish.grizzly.http2.frames.SettingsFrame;
import org.glassfish.grizzly.memory.Buffers;
import org.glassfish.grizzly.npn.AlpnClientNegotiator;
import org.glassfish.grizzly.ssl.SSLFilter;

import static org.glassfish.grizzly.http2.Termination.IN_FIN_TERMINATION;
import static org.glassfish.grizzly.http2.Termination.OUT_FIN_TERMINATION;
import static org.glassfish.grizzly.http2.frames.SettingsFrame.SETTINGS_ENABLE_PUSH;
import static org.glassfish.grizzly.http2.frames.SettingsFrame.SETTINGS_INITIAL_WINDOW_SIZE;
import static org.glassfish.grizzly.http2.frames.SettingsFrame.SETTINGS_MAX_CONCURRENT_STREAMS;

/**
 *
 * @author oleksiys
 */
public class Http2ClientFilter extends Http2BaseFilter {
    private final AlpnClientNegotiatorImpl defaultClientAlpnNegotiator;
    
    private boolean isNeverForceUpgrade;
    private boolean sendPushRequestUpstream;
    private final HeaderValue defaultHttp2Upgrade;
    private final HeaderValue connectionUpgradeHeaderValue;
    
    public Http2ClientFilter(final Http2Configuration configuration) {
        super(configuration);
        defaultClientAlpnNegotiator =
                new AlpnClientNegotiatorImpl(this);
        
        defaultHttp2Upgrade = HeaderValue.newHeaderValue(HTTP2_CLEAR);
        connectionUpgradeHeaderValue =
                HeaderValue.newHeaderValue("Upgrade, HTTP2-Settings");
    }

    /**
     * @return <code>true</code> if an upgrade to HTTP/2 will not be performed, otherwise <code>false</code>
     */
    @SuppressWarnings("unused")
    public boolean isNeverForceUpgrade() {
        return isNeverForceUpgrade;
    }

    /**
     * Configure this filter to completely disable attempts to upgrade to HTTP/2.
     *
     * @param neverForceUpgrade <code>true</code> to disable upgrade attempts, otherwise <code>false</code>
     */
    @SuppressWarnings("unused")
    public void setNeverForceUpgrade(boolean neverForceUpgrade) {
        this.isNeverForceUpgrade = neverForceUpgrade;
    }

    /**
     * @return <tt>true</tt> if the push request has to be sent upstream, so
     *         a user have a chance to process it, or <tt>false</tt> otherwise
     */
    @SuppressWarnings("unused")
    public boolean isSendPushRequestUpstream() {
        return sendPushRequestUpstream;
    }

    /**
     * @param sendPushRequestUpstream <tt>true</tt> if the push request has to
     *         be sent upstream, so a user have a chance to process it,
     *         or <tt>false</tt> otherwise
     */
    @SuppressWarnings("unused")
    public void setSendPushRequestUpstream(boolean sendPushRequestUpstream) {
        this.sendPushRequestUpstream = sendPushRequestUpstream;
    }
        
    @Override
    public NextAction handleConnect(final FilterChainContext ctx) throws IOException {
        final Connection connection = ctx.getConnection();

        final FilterChain filterChain = (FilterChain) connection.getProcessor();
        final int idx = filterChain.indexOfType(SSLFilter.class);
        
        if (idx != -1) { // use TLS ALPN
            final SSLFilter sslFilter = (SSLFilter) filterChain.get(idx);
            AlpnSupport.getInstance().configure(sslFilter);
            AlpnSupport.getInstance().setClientSideNegotiator(
                    connection, getClientAlpnNegotiator());

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
        } else if (getConfiguration().isPriorKnowledge()) {
            final Http2Session http2Session = createClientHttp2Session(connection);
            final Http2State state = http2Session.getHttp2State();
            state.setDirectUpgradePhase();
            http2Session.sendPreface();
            final NextAction suspendAction = ctx.getSuspendAction();
            ctx.suspend();
            state.addReadyListener(new Http2State.ReadyListener() {
                @Override
                public void ready(Http2Session http2Session) {
                    state.onClientHttpUpgradeRequestFinished();
                    http2Session.setupFilterChains(ctx, true);
                    ctx.resumeNext();
                }
            });
            connection.enableIOEvent(IOEvent.READ);
            return suspendAction;
        }
        
        return ctx.getInvokeAction();
    }
    
    @SuppressWarnings("unchecked")
    @Override
    public NextAction handleRead(final FilterChainContext ctx)
            throws IOException {

        // if it's a stream chain (the stream is already assigned) - just
        // bypass the parsing part
        if (checkIfHttp2StreamChain(ctx)) {
            return ctx.getInvokeAction();
        }
        
        final Connection connection = ctx.getConnection();
        Http2State http2State = Http2State.get(connection);
        
        if (http2State == null || http2State.isNeverHttp2()) {
            // NOT HTTP2 connection and never will be
            return ctx.getInvokeAction();
        }
        
        final HttpContent httpContent = ctx.getMessage();
        final HttpHeader httpHeader = httpContent.getHttpHeader();
        
        if (http2State.isHttpUpgradePhase()) { // Not HTTP/2 (yet?)
            assert !httpHeader.isRequest();
            
            final HttpResponsePacket httpResponse = (HttpResponsePacket) httpHeader;
            final HttpRequestPacket httpRequest = httpResponse.getRequest();
            
            if (!tryHttpUpgrade(ctx, http2State, httpRequest, httpResponse)) {
                // upgrade didn't work out
                http2State.setNeverHttp2();
                return ctx.getInvokeAction();
            }
        }
        
        final Http2Session http2Session =
                obtainHttp2Session(http2State, ctx, true);
        
        final Buffer framePayload = httpContent.getContent();

        httpContent.recycle();
        
        final List<Http2Frame> framesList =
                frameCodec.parse(http2Session,
                        http2State.getFrameParsingState(),
                        framePayload);
        
        if (!processFrames(ctx, http2Session, framesList)) {
            return ctx.getSuspendAction();
        }
        
        return ctx.getStopAction();
    }

    @Override
    public NextAction handleWrite(final FilterChainContext ctx)
            throws IOException {
        
        final Connection connection = ctx.getConnection();
        Http2State http2State = Http2State.get(connection);
        
        if (http2State != null && http2State.isNeverHttp2()) {
            return ctx.getInvokeAction();
        }

        if (http2State == null) {
            http2State = Http2State.create(connection);
            final Object msg = ctx.getMessage();
            
            if (!tryInsertHttpUpgradeHeaders(connection, msg)) {
                http2State.setNeverHttp2();
            }
            
            assert HttpPacket.isHttp(ctx.getMessage());
            
            checkIfLastHttp11Chunk(ctx, http2State, msg);
            
            return ctx.getInvokeAction();
        } else {
            if (http2State.isHttpUpgradePhase()) {
                // We still don't have the server response regarding HTTP2 upgrade offer
                final Object msg = ctx.getMessage();
                if (HttpPacket.isHttp(msg)) {
                    if (!((HttpPacket) msg).getHttpHeader().isCommitted()) {
                        throw new IllegalStateException("Can't pipeline HTTP requests because it's still not clear if HTTP/1.x or HTTP/2 will be used");
                    }
                    
                    checkIfLastHttp11Chunk(ctx, http2State, msg);
                }
                
                return ctx.getInvokeAction();
            }
        }
        
        return super.handleWrite(ctx);
    }

    @Override
    @SuppressWarnings("unchecked")
    public NextAction handleEvent(final FilterChainContext ctx,
            final FilterChainEvent event) throws IOException {
        if (!Http2State.isHttp2(ctx.getConnection())) {
            return ctx.getInvokeAction();
        }
        
        final Object type = event.type();
        
        if (type == OutgoingHttpUpgradeEvent.TYPE) {
            assert event instanceof OutgoingHttpUpgradeEvent;
            
            final OutgoingHttpUpgradeEvent outUpgradeEvent =
                    (OutgoingHttpUpgradeEvent) event;
            // If it's HTTP2 outgoing upgrade message - we have to re-enable content modifiers control
            outUpgradeEvent.getHttpHeader().setIgnoreContentModifiers(false);
            
            return ctx.getStopAction();
        }
        
        return ctx.getInvokeAction();
    }
    
    /**
     * Process the provided outbound header/packet.
     *
     * @param ctx the current {@link FilterChainContext}
     * @param http2Session the {@link Http2Session} that's being written to.
     * @param httpHeader the {@link HttpHeader} to write.
     * @param entireHttpPacket the {@link HttpPacket} to write.
     * @throws IOException if an I/O error occurs.
     */
    @SuppressWarnings("unchecked")
    @Override
    protected void processOutgoingHttpHeader(final FilterChainContext ctx,
            final Http2Session http2Session,
            final HttpHeader httpHeader,
            final HttpPacket entireHttpPacket) throws IOException {

        if (!http2Session.isHttp2OutputEnabled()) {
            // HTTP2 output is not enabled yet
            return;
        }
        
        final HttpRequestPacket request = (HttpRequestPacket) httpHeader;
        
        if (!request.isCommitted()) {
            prepareOutgoingRequest(request);
        }

        final Http2Stream stream = Http2Stream.getStreamFor(request);
        
        if (stream == null) {
            processOutgoingRequestForNewStream(ctx, http2Session, request,
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
            final Http2Session http2Session,
            final HttpRequestPacket request,
            final HttpPacket entireHttpPacket) throws IOException {

        
        final ReentrantLock newStreamLock = http2Session.getNewClientStreamLock();
        newStreamLock.lock();
        
        try {
            final Http2Stream stream = http2Session.openStream(
                    request,
                    http2Session.getNextLocalStreamId(),
                    0, false, 0);

            if (stream == null) {
                throw new IOException("Http2Session is closed");
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
    
    /**
     * Creates client-side {@link Http2Session} with pre-configured
     * initial-windows-size and max-concurrent-streams.
     * 
     * Note: Should be called with disabled OP_READ (or during OP_READ processing),
     *       because peer frames must not be processed at the time this method
     *       is running.
     * 
     * @param connection the TCP connection
     * @return {@link Http2Session}
     */
    protected Http2Session createClientHttp2Session(final Connection connection) {

        return createHttp2Session(connection, false);
    }
    
    protected AlpnClientNegotiator getClientAlpnNegotiator() {
        return defaultClientAlpnNegotiator;
    }    

    /**
     * The method is called once a client receives an HTTP response to its initial
     * propose to establish HTTP/2.0 connection.
     * 
     * @param ctx the current {@link FilterChainContext}
     * @param http2State the HTTP2 connection state
     * @param httpRequest the HTTP request
     * @param httpResponse the HTTP response
     * @return <code>true</code> if the connection was successfully upgraded, otherwise <code>false</code>
     * @throws Http2StreamException if an exception occurs with the stream
     * @throws IOException if a general I/O error occurs
     */
    @SuppressWarnings("DuplicateThrows")
    private boolean tryHttpUpgrade(final FilterChainContext ctx,
                                   final Http2State http2State,
                                   final HttpRequestPacket httpRequest,
                                   final HttpResponsePacket httpResponse)
            throws Http2StreamException, IOException {

        if (httpRequest == null) {
            return false;
        }

        // check the initial request, if it was correct HTTP/2.0 Upgrade request
        if (!checkRequestHeadersOnUpgrade(httpRequest)) {
            return false;
        }
        
        // check the server's response, if it accepts HTTP/2.0 upgrade
        if (!checkResponseHeadersOnUpgrade(httpResponse)) {
            return false;
        }

        final Connection connection = ctx.getConnection();
        
        // Create HTTP/2.0 connection for the given Grizzly Connection
        http2State.setDirectUpgradePhase();  // expecting preface (settings frame)
        
        final Http2Session http2Session =
                createClientHttp2Session(connection);
        
        if (http2State.tryLockClientPreface()) {
            http2Session.sendPreface();
        }
        
        http2Session.setupFilterChains(ctx, true);
                
        // reset the response object
        httpResponse.setStatus(HttpStatus.OK_200);
        httpResponse.getHeaders().clear();
        httpRequest.setProtocol(Protocol.HTTP_2_0);
        httpResponse.setProtocol(Protocol.HTTP_2_0);
        httpResponse.getUpgradeDC().recycle();
        httpResponse.getProcessingState().setKeepAlive(true);
        
        // create a virtual stream for this request

        if (http2Session.isGoingAway()) {
            return false;
        }

        final Http2Stream stream = http2Session.openUpgradeStream(
                httpRequest, 0);
        
        final HttpContext oldHttpContext =
                httpResponse.getProcessingState().getHttpContext();
        
        // replace the HttpContext
        final HttpContext httpContext = HttpContext.newInstance(stream,
                stream, stream, httpRequest);
        httpRequest.getProcessingState().setHttpContext(httpContext);
        httpContext.attach(ctx);
        
        final HttpRequestPacket dummyRequestPacket = HttpRequestPacket.builder()
                .method(Method.PRI)
                .uri("/dummy_pri")
                .protocol(Protocol.HTTP_2_0)
                .build();
        
        final HttpResponsePacket dummyResponsePacket =
                HttpResponsePacket.builder(dummyRequestPacket)
                .status(200)
                .reasonPhrase("OK")
                .protocol(Protocol.HTTP_2_0)
                .build();
        
        dummyResponsePacket.getProcessingState().setHttpContext(oldHttpContext);
        dummyResponsePacket.setIgnoreContentModifiers(true);
        
        // change the HttpClientFilter's HttpResponsePacket associated with the Connection
        ctx.notifyDownstream(
                HttpEvents.createChangePacketInProgressEvent(dummyResponsePacket));
                
        return true;
    }

    private boolean tryInsertHttpUpgradeHeaders(final Connection connection,
            final Object msg) {
        // we can offer a peer to upgrade to HTTP2
        if (isNeverForceUpgrade) {
            // we aren't allowed to insert HTTP2 Upgrade headers
            return false;
        }
            
        if (!HttpPacket.isHttp(msg)) {
            return false;
        }

        final HttpHeader httpHeader = ((HttpPacket) msg).getHttpHeader();
        if (!httpHeader.isRequest() // it's a response??? don't know what to do with it
                || httpHeader.isUpgrade() // already has Upgrade header?
                || httpHeader.getProtocol() != Protocol.HTTP_1_1 // only HTTP/1.1 is considered for upgrade
                || httpHeader.containsHeader(Header.Connection) // if there's a Connection header - skip it
                ) {
            // The HTTP request packet headers don't allow us to
            // insert HTTP/2.0 upgrade headers.
            return false;
        }

        // Ok, here we know that it's a request, which we can use to offer
        // a peer to upgrade to HTTP 2.0
        httpHeader.addHeader(Header.Upgrade, defaultHttp2Upgrade);
        httpHeader.addHeader(Header.Connection, connectionUpgradeHeaderValue);

        httpHeader.addHeader(Header.HTTP2Settings,
                prepareSettings(Http2Session.get(connection)).build().toBase64Uri());
        
        // pass the updated request downstream
        return true;
    }

    @Override
    protected void processCompleteHeader(
            final Http2Session http2Session,
            final FilterChainContext context,
            final HeaderBlockHead firstHeaderFrame) throws IOException {

        if (!ignoreFrameForStreamId(http2Session, firstHeaderFrame.getStreamId())) {
            switch (firstHeaderFrame.getType()) {
                case PushPromiseFrame.TYPE:
                    processInPushPromise(http2Session, context,
                            (PushPromiseFrame) firstHeaderFrame);
                    break;
                default:
                    processInResponse(http2Session, context,
                            (HeadersFrame) firstHeaderFrame);
            }
        }
    }

    @SuppressWarnings("DuplicateThrows")
    private void processInResponse(final Http2Session http2Session,
            final FilterChainContext context,
            final HeadersFrame headersFrame)
            throws Http2SessionException, IOException {

        final Http2Stream stream = http2Session.getStream(
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

        bind(request, response);

        stream.onRcvHeaders(isEOS);
        final HttpContent content;
        if (stream.getInboundHeaderFramesCounter() == 1) {
            if (isEOS) {
                response.setExpectContent(false);
                stream.inputBuffer.terminate(IN_FIN_TERMINATION);
            }
            DecoderUtils.decodeResponseHeaders(http2Session, response);
            onHttpHeadersParsed(response, context);
            response.getHeaders().mark();
            content = response.httpContentBuilder().content(Buffers.EMPTY_BUFFER).last(isEOS).build();
        } else {
            DecoderUtils.decodeTrailerHeaders(http2Session, response);
            final HttpTrailer trailer = response.httpTrailerBuilder().content(Buffers.EMPTY_BUFFER).last(isEOS).build();
            content = trailer;
            final MimeHeaders mimeHeaders = response.getHeaders();
            if (mimeHeaders.trailerSize() > 0) {
                for (final String name : mimeHeaders.trailerNames()) {
                    trailer.addHeader(name, mimeHeaders.getHeader(name));
                }
            }
            stream.flushInputData();
            //stream.inputBuffer.terminate(IN_FIN_TERMINATION);
        }

        if (isEOS) {
            onHttpPacketParsed(response, context);
        }

        sendUpstream(http2Session, stream, content);
    }

    @SuppressWarnings("DuplicateThrows")
    private void processInPushPromise(final Http2Session http2Session,
            final FilterChainContext context,
            final PushPromiseFrame pushPromiseFrame)
            throws Http2StreamException, IOException {

        if (http2Session.isGoingAway()) {
            return;
        }

        final Http2Request request = Http2Request.create();
        request.setConnection(context.getConnection());

        final int refStreamId = pushPromiseFrame.getStreamId();
        final Http2Stream refStream = http2Session.getStream(refStreamId);
        if (refStream == null) {
            throw new Http2StreamException(refStreamId, ErrorCode.REFUSED_STREAM,
                    "PushPromise is sent over unknown stream: " + refStreamId);
        }

        final Http2Stream stream = http2Session.acceptStream(request,
                pushPromiseFrame.getPromisedStreamId(), refStreamId, false, 0);
        
        DecoderUtils.decodeRequestHeaders(http2Session, request);
        onHttpHeadersParsed(request, context);

        prepareIncomingRequest(stream, request);
        
        stream.outputSink.terminate(OUT_FIN_TERMINATION);
        stream.onReceivePushPromise();
        // send the push request upstream only in case, when user explicitly wants it
        if (sendPushRequestUpstream) {
            sendUpstream(http2Session,
                         stream,
                         request.httpContentBuilder().content(Buffers.EMPTY_BUFFER).last(false).build());
        }
    }

    protected SettingsFrame.SettingsFrameBuilder prepareSettings(
            final Http2Session http2Session) {
        
        SettingsFrame.SettingsFrameBuilder builder = SettingsFrame.builder();

        final int maxConcStreams = getConfiguration().getMaxConcurrentStreams();
        
        if (maxConcStreams != -1 &&
                maxConcStreams != http2Session.getDefaultMaxConcurrentStreams()) {
            builder.setting(SETTINGS_MAX_CONCURRENT_STREAMS, maxConcStreams);
        }

        final int initWindSize = getConfiguration().getInitialWindowSize();
        if (initWindSize != -1
                && http2Session != null
                && initWindSize != http2Session.getDefaultStreamWindowSize()) {
            builder.setting(SETTINGS_INITIAL_WINDOW_SIZE, initWindSize);
        }

        builder.setting(SETTINGS_ENABLE_PUSH, ((getConfiguration().isPushEnabled()) ? 1 : 0));
        
        return builder;
    }    

    private void checkIfLastHttp11Chunk(final FilterChainContext ctx,
            final Http2State http2State, final Object msg) {
        if (HttpContent.isContent((HttpPacket) msg)) {
            // HTTP content of the upgrade request
            if (((HttpContent) msg).isLast()) {
                http2State.onClientHttpUpgradeRequestFinished();

                // send the preface once the last payload chunk reaches the
                // network layer
                ctx.addCompletionListener(
                        new FilterChainContext.CompletionListener() {

                            @Override
                            public void onComplete(final FilterChainContext context) {
                                if (http2State.tryLockClientPreface()) {
                                    final Http2Session http2Session =
                                            http2State.getHttp2Session();
                                    assert http2Session != null;

                                    http2Session.sendPreface();
                                }
                            }
                        });
            }
        }
    }
}
