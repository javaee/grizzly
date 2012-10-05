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
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.CompletionHandler;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.ProcessorExecutor;
import org.glassfish.grizzly.WriteResult;
import org.glassfish.grizzly.attributes.Attribute;
import org.glassfish.grizzly.attributes.AttributeBuilder;
import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.http.HttpContent;
import org.glassfish.grizzly.http.HttpHeader;
import org.glassfish.grizzly.http.HttpPacket;
import org.glassfish.grizzly.http.HttpResponsePacket;
import org.glassfish.grizzly.http.util.DataChunk;
import org.glassfish.grizzly.http.util.Header;
import org.glassfish.grizzly.http.util.MimeHeaders;
import org.glassfish.grizzly.memory.BufferArray;
import org.glassfish.grizzly.memory.Buffers;
import org.glassfish.grizzly.memory.CompositeBuffer;
import org.glassfish.grizzly.memory.MemoryManager;
import org.glassfish.grizzly.utils.Charsets;
import org.glassfish.grizzly.utils.NullaryFunction;

/**
 *
 * @author oleksiys
 */
public class SpdyHandlerFilter extends BaseFilter {
    private final static Logger LOGGER = Grizzly.logger(SpdyHandlerFilter.class);
    
    private static final int SYN_STREAM_FRAME = 1;
    private static final int SYN_REPLY_FRAME = 2;
    private static final int RST_STREAM_FRAME = 3;
    private static final int SETTINGS_FRAME = 4;
    private static final int PING_FRAME = 6;
    private static final int GOAWAY_FRAME = 7;
    private static final int HEADERS_FRAME = 8;
    private static final int WINDOW_UPDATE_FRAME = 9;
    private static final int CREDENTIAL_FRAME = 11;
    
    private static final int PROTOCOL_ERROR = 1;
    private static final int INVALID_STREAM = 2;
    private static final int REFUSED_STREAM = 3;
    private static final int UNSUPPORTED_VERSION = 4;
    private static final int CANCEL = 5;
    private static final int INTERNAL_ERROR = 6;
    private static final int FLOW_CONTROL_ERROR = 7;
    private static final int STREAM_IN_USE = 8;
    private static final int STREAM_ALREADY_CLOSED = 9;
    private static final int INVALID_CREDENTIALS = 10;
    private static final int FRAME_TOO_LARGE = 11;
    
              
    private static final int SPDY_VERSION = 3;
    
    private final Attribute<SpdySession> spdySessionAttr =
            AttributeBuilder.DEFAULT_ATTRIBUTE_BUILDER.createAttribute(
            SpdySession.class.getName(), new NullaryFunction<SpdySession>() {
        @Override
        public SpdySession evaluate() {
            return new SpdySession();
        }
    });
    
    private final ExecutorService threadPool;

    public SpdyHandlerFilter(ExecutorService threadPool) {
        this.threadPool = threadPool;
    }
    
    
    @Override
    public NextAction handleRead(final FilterChainContext ctx)
            throws IOException {
        final Object message = ctx.getMessage();
        
        if (message instanceof HttpPacket) { // It's SpdyStream upstream filter chain invocation. Skip this filter for now.
            return ctx.getInvokeAction();
        }
        
        if (message instanceof Buffer) {
            final Buffer frameBuffer = (Buffer) message;
            processInFrame(ctx, frameBuffer);
        } else {
            final ArrayList<Buffer> framesList = (ArrayList<Buffer>) message;
            final int sz = framesList.size();
            for (int i = 0; i < sz; i++) {
                processInFrame(ctx, framesList.get(i));
            }
        }
        
        return ctx.getStopAction();
    }

    @Override
    public NextAction handleWrite(FilterChainContext ctx) throws IOException {
        final Object message = ctx.getMessage();
        
        if (HttpPacket.isHttp(message)
                && (((HttpPacket) message).getHttpHeader() instanceof SpdyPacket)) {
            // Get HttpPacket
            final HttpPacket input = (HttpPacket) ctx.getMessage();
            // Get Connection
            final Connection connection = ctx.getConnection();

            try {
                // transform HttpPacket into Buffer
                final Buffer output = encodeSpdyPacket(ctx, input);

                if (output != null) {
//                    HttpProbeNotifier.notifyDataSent(this, connection, output);

                    ctx.setMessage(output);
                    // Invoke next filter in the chain.
                    return ctx.getInvokeAction();
                }

                return ctx.getStopAction();
            } catch (RuntimeException re) {
//                HttpProbeNotifier.notifyProbesError(this, connection, input, re);
                throw re;
            }
        }

        return ctx.getInvokeAction();
    }

    private Buffer encodeSpdyPacket(FilterChainContext ctx, HttpPacket input) {
        final HttpHeader header = input.getHttpHeader();
        final HttpContent content = HttpContent.isContent(input) ? (HttpContent) input : null;
        
        Buffer headerBuffer = null;
        
        if (!header.isCommitted()) {
            final boolean isLast = !header.isExpectContent() ||
                    (content != null && content.isLast() &&
                    !content.getContent().hasRemaining());
            
            if (!header.isRequest()) {
                headerBuffer = encodeSynReply(ctx, (HttpResponsePacket) header, isLast);
            } else {
                throw new IllegalStateException("Not implemented yet");
            }
        }
        
        Buffer contentBuffer = null;
        if (HttpContent.isContent(input)) {
            contentBuffer = encodeSpdyData(ctx, input);
        }
        
        final Buffer resultBuffer = Buffers.appendBuffers(ctx.getMemoryManager(),
                                     headerBuffer, contentBuffer, true);
        if (resultBuffer.isComposite()) {
            ((CompositeBuffer) resultBuffer).disposeOrder(
                    CompositeBuffer.DisposeOrder.LAST_TO_FIRST);
        }
        
        return resultBuffer;
    }
    
    private Buffer encodeSynReply(final FilterChainContext ctx,
            final HttpResponsePacket response, final boolean isLast) {
        final MemoryManager mm = ctx.getMemoryManager();
        
        Buffer outputBuffer = allocateHeapBuffer(mm, 2048);
        
        outputBuffer = encodeServiceHeaders(response, outputBuffer);
        
        return outputBuffer;
    }

    private Buffer encodeServiceHeaders(final HttpResponsePacket response,
            final Buffer outputBuffer) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    private Buffer encodeSpdyData(FilterChainContext ctx, HttpPacket input) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    private void processInFrame(final FilterChainContext context,
            final Buffer frame) {
        
        final long header = frame.getLong();
        
        final int first32 = (int) (header >>> 32);
        final int second32 = (int) (header & 0xFFFFFFFF);
        
        final boolean isControlMessage = (first32 & 0x80000000) != 0;
        if (isControlMessage) {
            final int version = (first32 >>> 16) & 0x7FFF;
            final int type = first32 & 0xFFFF;
            final int flags = second32 >>> 24;
            final int length = second32 & 0xFFFFFF;
            processControlFrame(context, frame, version, type, flags, length);
        } else {
            final int streamId = first32 & 0x7FFFFFFF;
            final int flags = second32 >>> 24;
            final int length = second32 & 0xFFFFFF;
            processDataFrame(frame, streamId, flags, length);
        }
    }    

    private void processControlFrame(final FilterChainContext context,
            final Buffer frame, final int version, final int type,
            final int flags, final int length) {
        
        switch(type) {
            case SYN_STREAM_FRAME: {
                processSynStream(context, frame, version, type, flags, length);
                break;
            }
                
            default: {
                System.out.println("Unknown control-frame [version=" + version + " type=" + type + " flags=" + flags + " length=" + length + "]");
            }
        }
    }

    private void processSynStream(final FilterChainContext context,
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
        
        final SpdySession spdySession = spdySessionAttr.get(context.getConnection());

        Buffer payload = frame;
        if (frame.isComposite()) {
            payload = frame.slice();
        }
        
        final Buffer decoded;
        
        try {
            decoded = inflate(spdySession, context.getMemoryManager(), payload);
        } catch (DataFormatException dfe) {
            sendRstStream(context, streamId, PROTOCOL_ERROR, null);
            return;
        } finally {
            frame.dispose();
        }
        
        assert decoded.hasArray();
        
        final SpdyRequest spdyRequest = SpdyRequest.create();
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
        
        final SpdyStream spdyStream = spdySession.acceptStream(context,
                spdyRequest, streamId, associatedToStreamId, priority, slot);
        
        threadPool.execute(new Runnable() {
            @Override
            public void run() {
                ProcessorExecutor.execute(
                        spdyStream.getUpstreamContext().getInternalContext());
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
    
    private void processDataFrame(Buffer frame, int streamId, int flags, int length) {
        throw new UnsupportedOperationException("Not yet implemented");
    }
    
    private static int get24(final Buffer buffer) {
        return ((buffer.get() & 0xFF) << 16)
                + ((buffer.get() & 0xFF) << 8)
                + (buffer.get() & 0xFF);        
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
        
        final Buffer rstStreamBuffer = ctx.getMemoryManager().allocate(16);
        rstStreamBuffer.putInt(0x80000000 | (SPDY_VERSION << 16) | RST_STREAM_FRAME); // "C", version, RST_STREAM_FRAME
        rstStreamBuffer.putInt(8); // Flags, Length
        rstStreamBuffer.putInt(streamId & 0x7FFFFFFF); // Stream-ID
        rstStreamBuffer.putInt(statusCode); // Status code
        rstStreamBuffer.flip();
        
        ctx.write(rstStreamBuffer, completionHandler);
    }

    private static Buffer inflate(final SpdySession spdySession,
            final MemoryManager memoryManager, final Buffer payload) throws DataFormatException {

        final Inflater inflater = spdySession.getInflater();
        
        if (!payload.isComposite()) {
            return inflateSimpleBuffer(inflater, memoryManager, payload, null);
        } else {
            final BufferArray bufferArray = payload.toBufferArray();
            final Buffer[] array = bufferArray.getArray();
            
            final int sz = bufferArray.size();
            
            Buffer resultBuffer = null;
            for (int i = 0; i < sz; i++) {
                final Buffer simplePayloadBuffer = array[i];
                resultBuffer = inflateSimpleBuffer(inflater,
                        memoryManager,
                        simplePayloadBuffer,
                        resultBuffer);
            }
            
            bufferArray.recycle();
            
            return resultBuffer;
        }
    }
    
    private static Buffer inflateSimpleBuffer(final Inflater inflater,
            final MemoryManager memoryManager, final Buffer payload,
            Buffer outputBuffer)
            throws DataFormatException {
        
        CompositeBuffer compositeOutputBuffer = null;
        Buffer currentOutputBuffer = null;
        Buffer prevOutputBuffer = null;
        if (outputBuffer != null) {
            if (!outputBuffer.isComposite()) {
                currentOutputBuffer = outputBuffer;
            } else {
                compositeOutputBuffer = (CompositeBuffer) outputBuffer;
            }
        }
        
        if (payload.hasArray()) {
            inflater.setInput(payload.array(),
                    payload.arrayOffset() + payload.position(),
                    payload.remaining());

            currentOutputBuffer = allocateHeapBuffer(memoryManager,
                    payload.remaining() * 3);

            do {
                if (!currentOutputBuffer.hasRemaining()) {
                    currentOutputBuffer.flip();
                    
                    if (prevOutputBuffer != null) {
                        if (compositeOutputBuffer == null) {
                            compositeOutputBuffer = CompositeBuffer.newBuffer(memoryManager, prevOutputBuffer);
                        } else {
                            compositeOutputBuffer.append(prevOutputBuffer);
                        }
                    }
                    
                    prevOutputBuffer = currentOutputBuffer;
                    
                    currentOutputBuffer = allocateHeapBuffer(memoryManager,
                            inflater.getRemaining() * 3);
                }

                final int lastInflated = inflater.inflate(
                        currentOutputBuffer.array(),
                        currentOutputBuffer.arrayOffset() + currentOutputBuffer.position(),
                        currentOutputBuffer.remaining());

                if (lastInflated == 0) {
                    if (!inflater.needsDictionary()) {
                        if (currentOutputBuffer.position() == 0) {
                            currentOutputBuffer.dispose();
                            currentOutputBuffer = null;
                        } else {
                            currentOutputBuffer.trim();
                        }

                        break;
                    }
                    inflater.setDictionary(Constants.SPDY_ZLIB_DICTIONARY);
                } else {
                    currentOutputBuffer.position(currentOutputBuffer.position() + lastInflated);
                }
            } while (true);
        } else {
            
        }
        
        assert inflater.getRemaining() == 0;
        
        if (compositeOutputBuffer != null) {
            compositeOutputBuffer.append(prevOutputBuffer);
            if (currentOutputBuffer != null) {
                compositeOutputBuffer.append(currentOutputBuffer);
            }
            
            return compositeOutputBuffer;
        } else if (prevOutputBuffer != null) {
            if (currentOutputBuffer == null) {
                return prevOutputBuffer;
            }
            
            return CompositeBuffer.newBuffer(memoryManager,
                    prevOutputBuffer, currentOutputBuffer);
        }
        
        return currentOutputBuffer;        
    }
    
    private static Buffer allocateHeapBuffer(final MemoryManager mm, final int size) {
        if (!mm.willAllocateDirect(size)) {
            return mm.allocateAtLeast(size);
        } else {
            return Buffers.wrap(mm, new byte[size]);
        }
    }
}
