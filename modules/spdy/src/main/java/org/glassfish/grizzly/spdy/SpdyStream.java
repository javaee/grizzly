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
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.CloseListener;
import org.glassfish.grizzly.CloseType;
import org.glassfish.grizzly.Closeable;
import org.glassfish.grizzly.CompletionHandler;
import org.glassfish.grizzly.GrizzlyFuture;
import org.glassfish.grizzly.OutputSink;
import org.glassfish.grizzly.WriteHandler;
import org.glassfish.grizzly.WriteResult;
import org.glassfish.grizzly.asyncqueue.LifeCycleHandler;
import org.glassfish.grizzly.asyncqueue.TaskQueue;
import org.glassfish.grizzly.attributes.Attribute;
import org.glassfish.grizzly.attributes.AttributeBuilder;
import org.glassfish.grizzly.attributes.AttributeHolder;
import org.glassfish.grizzly.attributes.AttributeStorage;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.http.HttpContent;
import org.glassfish.grizzly.http.HttpHeader;
import org.glassfish.grizzly.http.HttpPacket;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.http.HttpResponsePacket;
import org.glassfish.grizzly.impl.FutureImpl;
import org.glassfish.grizzly.memory.Buffers;
import org.glassfish.grizzly.memory.CompositeBuffer;
import org.glassfish.grizzly.memory.CompositeBuffer.DisposeOrder;
import org.glassfish.grizzly.spdy.frames.RstStreamFrame;
import org.glassfish.grizzly.utils.DataStructures;
import org.glassfish.grizzly.utils.Futures;

import static org.glassfish.grizzly.spdy.Constants.*;
/**
 * The abstraction representing SPDY stream.
 * 
 * @author Grizzly team
 */
public class SpdyStream implements AttributeStorage, OutputSink, Closeable {
    public static final String SPDY_STREAM_ATTRIBUTE = SpdyStream.class.getName();

    private enum CompletionUnit {
        Input, Output, Complete
    }
    
    private static final Attribute<SpdyStream> HTTP_RQST_SPDY_STREAM_ATTR =
            AttributeBuilder.DEFAULT_ATTRIBUTE_BUILDER.createAttribute("http.request.spdy.stream");
    
    private final HttpRequestPacket spdyRequest;
    private final int streamId;
    private final int associatedToStreamId;
    private final int priority;
    private final int slot;
    private final boolean isUnidirectional;
    
    private final SpdySession spdySession;
    
    private volatile int peerWindowSize = -1;
    private volatile int localWindowSize = -1;
    
    private final AttributeHolder attributes =
            AttributeBuilder.DEFAULT_ATTRIBUTE_BUILDER.createSafeAttributeHolder();

    final SpdyInputBuffer inputBuffer;
    final SpdyOutputSink outputSink;
    
    // closeTypeFlag, "null" value means the connection is open.
    final AtomicReference<CloseType> closeTypeFlag =
            new AtomicReference<CloseType>();
    private final Queue<CloseListener> closeListeners =
            new ConcurrentLinkedQueue<CloseListener>();
    private final AtomicInteger completeFinalizationCounter = new AtomicInteger();
    
    // flag, which is indicating if SpdyStream processing has been marked as complete by external code
    volatile boolean isProcessingComplete;
    
    // flag, which is indicating if SynStream or SynReply frames have already come for this SpdyStream
    private boolean isSynFrameRcv;
    
    private Map<String, PushResource> associatedResourcesToPush;
    private Set<SpdyStream> associatedSpdyStreams;
        
    public static SpdyStream getSpdyStream(final HttpHeader httpHeader) {
        final HttpRequestPacket request;
        
        if (httpHeader.isRequest()) {
            assert httpHeader instanceof HttpRequestPacket;
            request = (HttpRequestPacket) httpHeader;
        } else {
            assert httpHeader instanceof HttpResponsePacket;
            request = ((HttpResponsePacket) httpHeader).getRequest();
        }
        
        
        if (request != null) {
            return HTTP_RQST_SPDY_STREAM_ATTR.get(request);
        }
        
        return null;
    }

    static SpdyStream create(final SpdySession spdySession,
            final HttpRequestPacket spdyRequest,
            final int streamId, final int associatedToStreamId,
            final int priority, final int slot,
            final boolean isUnidirectional) {
        final SpdyStream spdyStream = new SpdyStream(spdySession, spdyRequest,
                streamId, associatedToStreamId, priority, slot, isUnidirectional);
        
        HTTP_RQST_SPDY_STREAM_ATTR.set(spdyRequest, spdyStream);
        
        return spdyStream;
    }
    
    private SpdyStream(final SpdySession spdySession,
            final HttpRequestPacket spdyRequest,
            final int streamId, final int associatedToStreamId,
            final int priority, final int slot,
            final boolean isUnidirectional) {
        this.spdySession = spdySession;
        this.spdyRequest = spdyRequest;
        this.streamId = streamId;
        this.associatedToStreamId = associatedToStreamId;
        this.priority = priority;
        this.slot = slot;
        this.isUnidirectional = isUnidirectional;
        
        inputBuffer = new SpdyInputBuffer(this);
        outputSink = new SpdyOutputSink(this);
    }

    SpdySession getSpdySession() {
        return spdySession;
    }

    public int getPeerWindowSize() {
        return peerWindowSize != -1 ? peerWindowSize :
                spdySession.getPeerInitialWindowSize();
    }

    public int getLocalWindowSize() {
        return localWindowSize != -1 ? localWindowSize :
                spdySession.getLocalInitialWindowSize();
    }
    
    public HttpRequestPacket getSpdyRequest() {
        return spdyRequest;
    }
    
    public HttpResponsePacket getSpdyResponse() {
        return spdyRequest.getResponse();
    }
    
    public PushResource addPushResource(final String url,
            final PushResource pushResource) {
        
        if (associatedResourcesToPush == null) {
            associatedResourcesToPush = new HashMap<String, PushResource>();
        }
        
        return associatedResourcesToPush.put(url, pushResource);
    }

    public PushResource removePushResource(final String url) {
        
        if (associatedResourcesToPush == null) {
            return null;
        }
        
        return associatedResourcesToPush.remove(url);
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

    public boolean isUnidirectional() {
        return isUnidirectional;
    }
    
    public boolean isLocallyInitiatedStream() {
        assert streamId > 0;
        
        return spdySession.isServer() ^ ((streamId % 2) != 0);
        
//        Same as
//        return spdySession.isServer() ?
//                (streamId % 2) == 0 :
//                (streamId % 2) == 1;        
    }

    public boolean isClosed() {
        return completeFinalizationCounter.get() >= 2;
    }

    @Override
    public AttributeHolder getAttributes() {
        return attributes;
    }

    @Override
    public boolean canWrite() {
        final int peerWindowSizeNow = getPeerWindowSize();
        
        // TODO:  remove this inspection
        //noinspection ConstantConditions
        if (peerWindowSizeNow < 0) {
            return true;
        }

        final TaskQueue taskQueue =
                outputSink.outputQueue;
        final int size = taskQueue.size();

        return size == 0 || size < peerWindowSizeNow;
    }

    @Override
    public void notifyWritePossible(WriteHandler writeHandler) {
        // pass peer-window-size as max, even though these values are independent.
        // later we may want to decouple outputQueue's max-size and peer-window-size
        outputSink.outputQueue.notifyWhenOperable(writeHandler, getPeerWindowSize());
    }

    void onPeerWindowUpdate(final int delta) throws SpdyStreamException {
        outputSink.onPeerWindowUpdate(delta);
    }
    
    void writeDownStream(final HttpPacket httpPacket) throws IOException {
        outputSink.writeDownStream(httpPacket, null);
    }
    
    void writeDownStream(final Source resource) throws IOException {
        outputSink.writeDownStream(resource);
    }

    void writeDownStream(final HttpPacket httpPacket,
                         final FilterChainContext ctx,
                         final CompletionHandler<WriteResult> completionHandler)
    throws IOException {
        outputSink.writeDownStream(httpPacket, ctx, completionHandler);
    }
    
    @SuppressWarnings("unchecked")
    void writeDownStream(final HttpPacket httpPacket,
                         final FilterChainContext ctx,
            final CompletionHandler<WriteResult> completionHandler,
            final LifeCycleHandler lifeCycleHandler)
            throws IOException {
        outputSink.writeDownStream(httpPacket, ctx, completionHandler,
                lifeCycleHandler);
    }

    @Override
    public GrizzlyFuture<Closeable> close() {
        final FutureImpl<Closeable> future = Futures.createSafeFuture();
        close(Futures.toCompletionHandler(future));
        
        return future;
    }

    @Override
    public void close(final CompletionHandler<Closeable> completionHandler) {
        close(completionHandler, true);
    }

    void close(
            final CompletionHandler<Closeable> completionHandler,
            final boolean isClosedLocally) {
        
        if (closeTypeFlag.compareAndSet(null,
                isClosedLocally ? CloseType.LOCALLY : CloseType.REMOTELY)) {
            
            final Termination termination = isClosedLocally ?
                    LOCAL_CLOSE_TERMINATION : 
                    PEER_CLOSE_TERMINATION;
            
            // Terminate the input, dicard already bufferred data
            inputBuffer.terminate(termination);
            // Terminate the output, discard all the pending data in the output buffer
            outputSink.terminate(termination);
            
            notifyCloseListeners();

            if (completionHandler != null) {
                completionHandler.completed(this);
            }
        }
    }

    /**
     * Notifies the SpdyStream that it's been closed remotely.
     */
    void closedRemotely() {
        if (closeTypeFlag.compareAndSet(null, CloseType.REMOTELY)) {
            // Schedule (add to the stream's input queue) the Termination,
            // which will be invoked once read by the user code.
            // This way we simulate Java Socket behavior
            inputBuffer.close(
                    new Termination(TerminationType.PEER_CLOSE, CLOSED_BY_PEER_STRING) {
                @Override
                public void doTask() {
                    close(null, false);
                }
            });
        }
    }
    
    /**
     * Notify the SpdyStream that peer sent RST_FRAME.
     */
    void resetRemotely() {
        if (closeTypeFlag.compareAndSet(null, CloseType.REMOTELY)) {
            // initiat graceful shutdown for input, so user is able to read
            // the bufferred data
            inputBuffer.close(RESET_TERMINATION);
            
            // forcibly terminate the output, so no more data will be sent
            outputSink.terminate(RESET_TERMINATION);
        }
        
        rstAssociatedStreams();
    }
    
    void onProcessingComplete() {
        isProcessingComplete = true;
        if (closeTypeFlag.compareAndSet(null, CloseType.LOCALLY)) {
            
            final Termination termination = 
                    LOCAL_CLOSE_TERMINATION;
            
            inputBuffer.terminate(termination);
            outputSink.close();
            
            notifyCloseListeners();
        }
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public void addCloseListener(CloseListener closeListener) {
        CloseType closeType = closeTypeFlag.get();
        
        // check if connection is still open
        if (closeType == null) {
            // add close listener
            closeListeners.add(closeListener);
            // check the connection state again
            closeType = closeTypeFlag.get();
            if (closeType != null && closeListeners.remove(closeListener)) {
                // if connection was closed during the method call - notify the listener
                try {
                    closeListener.onClosed(this, closeType);
                } catch (IOException ignored) {
                }
            }
        } else { // if connection is closed - notify the listener
            try {
                closeListener.onClosed(this, closeType);
            } catch (IOException ignored) {
            }
        }
    }

    @Override
    public boolean removeCloseListener(CloseListener closeListener) {
        return closeListeners.remove(closeListener);
    }

    void onInputClosed() {
        if (completeFinalizationCounter.incrementAndGet() == 2) {
            closeStream();
        }
    }

    void onOutputClosed() {
        if (completeFinalizationCounter.incrementAndGet() == 2) {
            closeStream();
        }
    }
    
    /**
     * On first received data frame we have to figure out the peer
     * window size used for this stream.
     */
    void onDataFrameReceive() {
        if (peerWindowSize == -1) {
            peerWindowSize = spdySession.getPeerInitialWindowSize();
        }
    }
    
    /**
     * On first sent data frame we have to figure out our local
     * window size used for this stream.
     */
    void onDataFrameSend() {
        if (localWindowSize == -1) {
            localWindowSize = spdySession.getLocalInitialWindowSize();
        }
    }

    void onSynFrameRcv() throws SpdyStreamException {
        if (!isSynFrameRcv) {
            isSynFrameRcv = true;
        } else {
            inputBuffer.close(UNEXPECTED_FRAME_TERMINATION);
            throw new SpdyStreamException(getStreamId(),
                    RstStreamFrame.PROTOCOL_ERROR, "Only one syn frame is allowed");
        }
    }
    
    private Buffer cachedInputBuffer;
    private boolean cachedIsLast;
    
    void offerInputData(final Buffer data, final boolean isLast)
            throws SpdyStreamException {
        if (!isSynFrameRcv) {
            close(null, true);
            
            throw new SpdyStreamException(getStreamId(),
                    RstStreamFrame.PROTOCOL_ERROR, "DataFrame came before SynReply");
        }
        
        onDataFrameReceive();
        
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
    
    HttpContent pollInputData() throws IOException {
        return inputBuffer.poll();
    }
    
    private void closeStream() {
        spdySession.deregisterStream(this);
    }
    
    HttpHeader getInputHttpHeader() {
        return (isLocallyInitiatedStream() ^ isUnidirectional()) ?
                spdyRequest.getResponse() :
                spdyRequest;
    }
    
    HttpHeader getOutputHttpHeader() {
        return (!isLocallyInitiatedStream() ^ isUnidirectional()) ?
                spdyRequest.getResponse() :
                spdyRequest;
    }
    
    final Map<String, PushResource> getAssociatedResourcesToPush() {
        return associatedResourcesToPush;
    }
    
    /**
     * Add associated stream, so it might be closed when this stream is closed.
     */
    final void addAssociatedStream(final SpdyStream spdyStream)
            throws SpdyStreamException {
        if (associatedSpdyStreams == null) {
            associatedSpdyStreams = Collections.newSetFromMap(
                    DataStructures.<SpdyStream, Boolean>getConcurrentMap(8));
        }
        
        associatedSpdyStreams.add(spdyStream);
        
        if (isClosed() && associatedSpdyStreams.remove(spdyStream)) {
            throw new SpdyStreamException(spdyStream.getStreamId(),
                    RstStreamFrame.REFUSED_STREAM, "The parent stream is closed");
        }
    }

    /**
     * Reset associated streams, when peer resets the parent stream.
     */
    final void rstAssociatedStreams() {
        if (associatedSpdyStreams != null) {
            for (Iterator<SpdyStream> it = associatedSpdyStreams.iterator(); it.hasNext(); ) {
                final SpdyStream associatedStream = it.next();
                it.remove();
                
                try {
                    associatedStream.resetRemotely();
                } catch (Exception ignored) {
                }
            }
        }
    }
    
    /**
     * Notify all close listeners
     */
    @SuppressWarnings("unchecked")
    private void notifyCloseListeners() {
        final CloseType closeType = closeTypeFlag.get();
        
        CloseListener closeListener;
        while ((closeListener = closeListeners.poll()) != null) {
            try {
                closeListener.onClosed(this, closeType);
            } catch (IOException ignored) {
            }
        }
    }
    
    protected enum TerminationType {
        FIN, RST, LOCAL_CLOSE, PEER_CLOSE, FORCED
    }
    
    protected static class Termination {
        private final TerminationType type;
        private final String description;

        public Termination(final TerminationType type, final String description) {
            this.type = type;
            this.description = description;
        }

        public TerminationType getType() {
            return type;
        }

        public String getDescription() {
            return description;
        }
        
        public void doTask() {
        }
    }
}