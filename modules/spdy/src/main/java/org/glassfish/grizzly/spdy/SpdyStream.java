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
import java.util.concurrent.atomic.AtomicInteger;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.CompletionHandler;
import org.glassfish.grizzly.WriteHandler;
import org.glassfish.grizzly.WriteQueryAndNotification;
import org.glassfish.grizzly.WriteResult;
import org.glassfish.grizzly.asyncqueue.TaskQueue;
import org.glassfish.grizzly.attributes.Attribute;
import org.glassfish.grizzly.attributes.AttributeBuilder;
import org.glassfish.grizzly.attributes.AttributeHolder;
import org.glassfish.grizzly.attributes.AttributeStorage;
import org.glassfish.grizzly.attributes.IndexedAttributeHolder;
import org.glassfish.grizzly.http.HttpHeader;
import org.glassfish.grizzly.http.HttpPacket;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.http.HttpResponsePacket;
import org.glassfish.grizzly.http.io.InputBuffer;
import org.glassfish.grizzly.memory.Buffers;
import org.glassfish.grizzly.memory.CompositeBuffer;
import org.glassfish.grizzly.memory.CompositeBuffer.DisposeOrder;

/**
 *
 * @author oleksiys
 */
public class SpdyStream implements AttributeStorage, WriteQueryAndNotification {
    private static final Attribute<SpdyStream> HTTP_RQST_SPDY_STREAM_ATTR =
            AttributeBuilder.DEFAULT_ATTRIBUTE_BUILDER.createAttribute("http.request.spdy.stream");
    
    private final HttpRequestPacket spdyRequest;
    private final int streamId;
    private final int associatedToStreamId;
    private final int priority;
    private final int slot;
    private final SpdySession spdySession;
    final int outboundQueueSizeInBytes = 1204 * 1024; // TODO:  We need a realistic setting here
    
    private final AtomicInteger completeCloseIndicator = new AtomicInteger();
    private final AttributeHolder attributes =
            new IndexedAttributeHolder(AttributeBuilder.DEFAULT_ATTRIBUTE_BUILDER);

    final SpdyInputBuffer inputBuffer;
    final SpdyOutputSink outputSink;
    
    public static SpdyStream getSpdyStream(final HttpHeader httpHeader) {
        final HttpRequestPacket request = httpHeader.isRequest() ?
                (HttpRequestPacket) httpHeader :
                ((HttpResponsePacket) httpHeader).getRequest();
        
        
        if (request != null) {
            return HTTP_RQST_SPDY_STREAM_ATTR.get(request);
        }
        
        return null;
    }

    static SpdyStream create(final SpdySession spdySession,
            final HttpRequestPacket spdyRequest,
            final int streamId, final int associatedToStreamId,
            final int priority, final int slot) {
        final SpdyStream spdyStream = new SpdyStream(spdySession, spdyRequest,
                streamId, associatedToStreamId, priority, slot);
        
        HTTP_RQST_SPDY_STREAM_ATTR.set(spdyRequest, spdyStream);
        
        return spdyStream;
    }
    
    private SpdyStream(final SpdySession spdySession,
            final HttpRequestPacket spdyRequest,
            final int streamId, final int associatedToStreamId,
            final int priority, final int slot) {
        this.spdySession = spdySession;
        this.spdyRequest = spdyRequest;
        this.streamId = streamId;
        this.associatedToStreamId = associatedToStreamId;
        this.priority = priority;
        this.slot = slot;
        
        inputBuffer = new SpdyInputBuffer(this);
        outputSink = new SpdyOutputSink(this);
    }

    SpdySession getSpdySession() {
        return spdySession;
    }

    public HttpRequestPacket getSpdyRequest() {
        return spdyRequest;
    }
    
    public HttpResponsePacket getSpdyResponse() {
        return spdyRequest.getResponse();
    }
    
    public int getStreamId() {
        return streamId;
    }

    public int getAssociatedToStreamId() {
        return associatedToStreamId;
    }

    public int getPriority() {
        return priority;
    }

    public int getSlot() {
        return slot;
    }
    
    public boolean isLocallyInitiatedStream() {
        return spdySession.isServer() ?
                (streamId % 2) == 0 :
                (streamId % 2) == 1;
    }

    public boolean isClosed() {
        return completeCloseIndicator.get() >= 2;
    }

    @Override
    public AttributeHolder getAttributes() {
        return attributes;
    }

    @Override
    public boolean canWrite() {
        // TODO:  remove this inspection
        //noinspection ConstantConditions
        if (outboundQueueSizeInBytes < 0) {
            return true;
        }

        final TaskQueue taskQueue =
                outputSink.outputQueue;
        final int size = taskQueue.size();

        return size == 0 || size < outboundQueueSizeInBytes;
    }

    @Override
    public void notifyWritePossible(WriteHandler writeHandler) {
        outputSink.outputQueue.notifyWhenOperable(writeHandler, outboundQueueSizeInBytes);
    }

    void onPeerWindowUpdate(final int delta) {
        outputSink.onPeerWindowUpdate(delta);
    }
    
    void writeDownStream(final HttpPacket httpPacket) throws IOException {
        outputSink.writeDownStream(httpPacket);
    }
    
    void writeDownStream(final HttpPacket httpPacket,
            CompletionHandler<WriteResult> completionHandler)
            throws IOException {
        outputSink.writeDownStream(httpPacket, completionHandler);
    }
    
    void shutdown() {
        shutdownInput();
        shutdownOutput();
    }
    
    void shutdownNow() {
        completeCloseIndicator.set(2);
        closeStream();
        shutdown();
    }
    
    void shutdownInput() {
        inputBuffer.close();
    }
    
    boolean isInputTerminated() {
        return inputBuffer.isTerminated();
    }
    
    void shutdownOutput() {
        outputSink.shutdown();
    }
    
    boolean isOutputTerminated() {
        return outputSink.isTerminated();
    }

    void onInputClosed() {
        if (completeCloseIndicator.incrementAndGet() == 2) {
            closeStream();
        }
    }

    void onOutputClosed() {
        if (completeCloseIndicator.incrementAndGet() == 2) {
            closeStream();
        }
    }
    
    private Buffer cachedInputBuffer;
    private boolean cachedIsLast;
    
    void offerInputData(final Buffer data, final boolean isLast) {
        final boolean isFirstBufferCached = (cachedInputBuffer == null);
        cachedIsLast |= isLast;
        cachedInputBuffer = Buffers.appendBuffers(
                spdySession.getMemoryManager(),
                cachedInputBuffer, data);
        
        if (isFirstBufferCached) {
            spdySession.streamsToFlushInput.add(this);
        }
    }
    
    void flushInputData() {
        final Buffer cachedInputBufferLocal = cachedInputBuffer;
        final boolean cachedIsLastLocal = cachedIsLast;
        cachedInputBuffer = null;
        cachedIsLast = false;
        
        if (cachedInputBufferLocal != null) {
            if (cachedInputBufferLocal.isComposite()) {
                ((CompositeBuffer) cachedInputBufferLocal).allowInternalBuffersDispose(true);
                ((CompositeBuffer) cachedInputBufferLocal).allowBufferDispose(true);
                ((CompositeBuffer) cachedInputBufferLocal).disposeOrder(DisposeOrder.LAST_TO_FIRST);
            }
            
            inputBuffer.offer(cachedInputBufferLocal, cachedIsLastLocal);
        }
    }
    
    Buffer pollInputData() throws IOException {
        return inputBuffer.poll();
    }
    
    private void closeStream() {
        spdySession.deregisterStream(this);
    }
    
    HttpHeader getInputHttpHeader() {
        return isLocallyInitiatedStream() ?
                spdyRequest.getResponse() :
                spdyRequest;
    }
    
    HttpHeader getOutputHttpHeader() {
        return !isLocallyInitiatedStream() ?
                spdyRequest.getResponse() :
                spdyRequest;
    }
}