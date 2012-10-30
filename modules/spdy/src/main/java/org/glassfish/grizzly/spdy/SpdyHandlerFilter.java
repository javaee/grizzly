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
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.CompletionHandler;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Event;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.ProcessorExecutor;
import org.glassfish.grizzly.WriteResult;
import org.glassfish.grizzly.attributes.Attribute;
import org.glassfish.grizzly.attributes.AttributeBuilder;
import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.http.HttpContent;
import org.glassfish.grizzly.http.HttpContext;
import org.glassfish.grizzly.http.HttpHeader;
import org.glassfish.grizzly.http.HttpPacket;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.http.HttpServerFilter;
import org.glassfish.grizzly.http.Protocol;
import org.glassfish.grizzly.http.util.DataChunk;
import org.glassfish.grizzly.http.util.Header;
import org.glassfish.grizzly.http.util.MimeHeaders;
import org.glassfish.grizzly.spdy.compression.SpdyInflaterOutputStream;
import org.glassfish.grizzly.utils.Charsets;

import static org.glassfish.grizzly.spdy.Constants.*;
/**
 *
 * @author oleksiys
 */
public class SpdyHandlerFilter extends BaseFilter {
    private final static Logger LOGGER = Grizzly.logger(SpdyHandlerFilter.class);

    private final Attribute<SpdySession> spdySessionAttr =
            AttributeBuilder.DEFAULT_ATTRIBUTE_BUILDER.createAttribute(
            SpdySession.class.getName());

    private final ExecutorService threadPool;

    public SpdyHandlerFilter(ExecutorService threadPool) {
        this.threadPool = threadPool;
    }


    @SuppressWarnings("unchecked")
    @Override
    public NextAction handleRead(final FilterChainContext ctx)
            throws IOException {
        
        final SpdySession spdySession = checkSpdySession(ctx);
        
        final Object message = ctx.getMessage();

        if (message == null) {  // If message == null - it means it's initiated by ctx.read() call
            // we have to check SpdyStream associated input queue if there are any data we can return
            // otherwise block until input data is available
            final SpdyStream spdyStream =
                    (SpdyStream) HttpContext.get(ctx).getContextStorage();
            final Buffer data = spdyStream.pollInputData();
            
            final HttpContent httpContent = HttpContent.builder(spdyStream.getInputHttpHeader())
                    .content(data)
                    .last(spdyStream.isLastInputDataPolled())
                    .build();
            
            System.out.println("Passing data=" + data + " isLast=" + spdyStream.isLastInputDataPolled());
            ctx.setMessage(httpContent);
            
            return ctx.getInvokeAction();
        }
        
        if (message instanceof HttpPacket) { // It's SpdyStream upstream filter chain invocation. Skip this filter for now.
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
                processSettingsFrame(spdySession, context, frame, version, type, flags, length);
                break;
            }
            case SYN_REPLY_FRAME: {
                break;
            }
            case PING_FRAME: {
                processPingFrame(spdySession, context, frame, version, type, flags, length);
                break;
            }
            case RST_STREAM_FRAME: {
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
                System.out.println("Unknown control-frame [version=" + version + " type=" + type + " flags=" + flags + " length=" + length + "]");
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
        
        spdySession.getStream(streamId).onPeerWindowUpdate(delta);
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
    
    private void processSettingsFrame(final SpdySession spdySession,
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

    private void processPingFrame(final SpdySession spdySession,
                                      final FilterChainContext context,
                                      final Buffer frame,
                                      final int version,
                                      final int type,
                                      final int flags,
                                      final int length) {
        final int numEntries = frame.getInt();
        
        if (LOGGER.isLoggable(Level.INFO)) { // TODO Change level
            final StringBuilder sb = new StringBuilder(32);
            sb.append("\n{PING : id=")
                    .append((long) numEntries)
                    .append("}\n");
            LOGGER.info(sb.toString()); // TODO: CHANGE LEVEL
        }
        
        frame.flip();
        
        // Send the same ping message back
        spdySession.writeDownStream(frame);
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
        final SpdyStream spdyStream = spdySession.acceptStream(context,
                spdyRequest, streamId, associatedToStreamId, priority, slot);
        
        if (spdyStream == null) { // GOAWAY has been sent, so ignoring this request
            spdyRequest.recycle();
            frame.dispose();
            return;
        }
        
        spdyRequest.setSpdyStream(spdyStream);

        
        if ((flags & SYN_STREAM_FLAG_FIN) != 0) {
            spdyRequest.setExpectContent(false);
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

        final MimeHeaders mimeHeaders = spdyRequest.getHeaders();

        final byte[] headersArray = decoded.array();
        int position = decoded.arrayOffset() + decoded.position();

        final int headersCount = getInt(headersArray, position);
        position += 4;

        for (int i = 0; i < headersCount; i++) {
            final boolean isServiceHeader = (headersArray[position + 4] == ':');

            if (isServiceHeader) {
                position = processServiceHeader(spdyRequest, headersArray, position);
            } else {
                position = processNormalHeader(mimeHeaders, headersArray, position);
            }
        }
        
        threadPool.execute(new Runnable() {
            @Override
            public void run() {
                final FilterChainContext ctx = spdyStream.getUpstreamContext();
                HttpContext.newInstance(ctx, spdyStream);
                ProcessorExecutor.execute(ctx.getInternalContext());
            }
        });
    }

    private int processServiceHeader(final SpdyRequest spdyRequest,
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

    private int processNormalHeader(final MimeHeaders mimeHeaders,
            final byte[] headersArray, int position) {

        final int headerNameSize = getInt(headersArray, position);
        position += 4;

        final DataChunk valueChunk =
                mimeHeaders.addValue(headersArray, position, headerNameSize);

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
        return end;
    }

    private void processDataFrame(final SpdyStream spdyStream,
            final Buffer frame, final int flags,
            final int length) {
        
        final boolean isFinFrame = (flags & SYN_STREAM_FLAG_FIN) != 0;
        
        spdyStream.offerInputData(frame, isFinFrame);
        
        System.out.println("ISFIN=" + isFinFrame);
        if (isFinFrame) {
            spdyStream.closeInput();
        }
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public NextAction handleWrite(final FilterChainContext ctx) throws IOException {
        final Object message = ctx.getMessage();
        
        if (HttpPacket.isHttp(message)
                && (((HttpPacket) message).getHttpHeader() instanceof SpdyPacket)) {
            // Get HttpPacket
            final HttpPacket input = (HttpPacket) ctx.getMessage();
            final HttpHeader httpHeader = input.getHttpHeader();

            if (!httpHeader.isRequest() && !httpHeader.isCommitted()) {
                prepareResponse((SpdyResponse) httpHeader);
            }
            
            final SpdyStream spdyStream =
                    ((SpdyPacket) httpHeader).getSpdyStream();

            spdyStream.writeDownStream(input,
                    ctx.getTransportContext().getCompletionHandler());
        }

        return ctx.getInvokeAction();
    }

    @Override
    public NextAction handleEvent(FilterChainContext ctx, Event event) throws IOException {
        final Connection c = ctx.getConnection();
        
        if (event.type() == HttpServerFilter.RESPONSE_COMPLETE_EVENT.type()) {
            final HttpContext httpContext = HttpContext.get(ctx);
            final SpdyStream spdyStream = (SpdyStream) httpContext.getContextStorage();
            spdyStream.closeOutput();
            
            return ctx.getStopAction();
        }

        return ctx.getInvokeAction();
    }


    private void prepareResponse(final SpdyResponse response) {
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

    private SpdySession checkSpdySession(final FilterChainContext context) {
        final Connection connection = context.getConnection();
        
        SpdySession spdySession = spdySessionAttr.get(connection);
        if (spdySession == null) {
            spdySession = new SpdySession(connection);
            spdySession.initCommunication(context);
            spdySessionAttr.set(connection, spdySession);
        }
        
        return spdySession;
    }
}
