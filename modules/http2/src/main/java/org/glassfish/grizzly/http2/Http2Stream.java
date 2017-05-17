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
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.CloseListener;
import org.glassfish.grizzly.CloseReason;
import org.glassfish.grizzly.CloseType;
import org.glassfish.grizzly.Closeable;
import org.glassfish.grizzly.CompletionHandler;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.GrizzlyFuture;
import org.glassfish.grizzly.ICloseType;
import org.glassfish.grizzly.OutputSink;
import org.glassfish.grizzly.WriteHandler;
import org.glassfish.grizzly.attributes.Attribute;
import org.glassfish.grizzly.attributes.AttributeBuilder;
import org.glassfish.grizzly.attributes.AttributeHolder;
import org.glassfish.grizzly.attributes.AttributeStorage;
import org.glassfish.grizzly.http.HttpContent;
import org.glassfish.grizzly.http.HttpHeader;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.http.HttpResponsePacket;
import org.glassfish.grizzly.http2.Termination.TerminationType;
import org.glassfish.grizzly.impl.FutureImpl;
import org.glassfish.grizzly.memory.Buffers;
import org.glassfish.grizzly.memory.CompositeBuffer;
import org.glassfish.grizzly.memory.CompositeBuffer.DisposeOrder;
import org.glassfish.grizzly.utils.Futures;

import org.glassfish.grizzly.http2.frames.ErrorCode;

import static org.glassfish.grizzly.http2.Termination.CLOSED_BY_PEER_STRING;
import static org.glassfish.grizzly.http2.Termination.LOCAL_CLOSE_TERMINATION;
import static org.glassfish.grizzly.http2.Termination.PEER_CLOSE_TERMINATION;
import static org.glassfish.grizzly.http2.Termination.RESET_TERMINATION;
import static org.glassfish.grizzly.http2.Termination.UNEXPECTED_FRAME_TERMINATION;

/**
 * The abstraction representing HTTP2 stream.
 * 
 * @author Grizzly team
 */
public class Http2Stream implements AttributeStorage, OutputSink, Closeable {

    public enum State {
        IDLE,
        OPEN,
        RESERVED_LOCAL,
        RESERVED_REMOTE,
        HALF_CLOSED_LOCAL,
        HALF_CLOSED_REMOTE,
        CLOSED
    }

    private static final Logger LOGGER = Grizzly.logger(Http2Stream.class);

    public static final String HTTP2_STREAM_ATTRIBUTE =
            HttpRequestPacket.READ_ONLY_ATTR_PREFIX + Http2Stream.class.getName();
    public static final String HTTP2_PARENT_STREAM_ATTRIBUTE =
            HttpRequestPacket.READ_ONLY_ATTR_PREFIX + "parent." + Http2Stream.class.getName();

    static final int UPGRADE_STREAM_ID = 1;
    
    private static final Attribute<Http2Stream> HTTP_RQST_HTTP2_STREAM_ATTR =
            AttributeBuilder.DEFAULT_ATTRIBUTE_BUILDER.createAttribute("http2.request.stream");

    State state = State.IDLE;
    
    private final HttpRequestPacket request;
    private final int streamId;
    private final int parentStreamId;
    private final int priority;
    private final boolean exclusive;

    private final Http2Session http2Session;
    
    private final AttributeHolder attributes =
            AttributeBuilder.DEFAULT_ATTRIBUTE_BUILDER.createSafeAttributeHolder();

    final StreamInputBuffer inputBuffer;
    final StreamOutputSink outputSink;
    
    // number of bytes reported to be read, but still unacked to the peer
    static final AtomicIntegerFieldUpdater<Http2Stream> unackedReadBytesUpdater =
            AtomicIntegerFieldUpdater.newUpdater(Http2Stream.class, "unackedReadBytes");
    @SuppressWarnings("unused")
    private volatile int unackedReadBytes;
    
    // closeReasonRef, "null" value means the connection is open.
    private static final AtomicReferenceFieldUpdater<Http2Stream, CloseReason> closeReasonUpdater =
            AtomicReferenceFieldUpdater.newUpdater(Http2Stream.class, CloseReason.class, "closeReason");
    @SuppressWarnings("unused")
    private volatile CloseReason closeReason;

    private volatile GrizzlyFuture<CloseReason> closeFuture;
    
    private final Queue<CloseListener> closeListeners =
            new ConcurrentLinkedQueue<>();
    
    private static final AtomicIntegerFieldUpdater<Http2Stream> completeFinalizationCounterUpdater =
            AtomicIntegerFieldUpdater.newUpdater(Http2Stream.class, "completeFinalizationCounter");
    @SuppressWarnings("unused")
    private volatile int completeFinalizationCounter;

    // flag, which is indicating if Http2Stream processing has been marked as complete by external code
    volatile boolean isProcessingComplete;
    
    // the counter for inbound HeaderFrames
    private int inboundHeaderFramesCounter;
    
    public static Http2Stream getStreamFor(final HttpHeader httpHeader) {
        final HttpRequestPacket request;

        //noinspection Duplicates
        if (httpHeader.isRequest()) {
            assert httpHeader instanceof HttpRequestPacket;
            request = (HttpRequestPacket) httpHeader;
        } else {
            assert httpHeader instanceof HttpResponsePacket;
            request = ((HttpResponsePacket) httpHeader).getRequest();
        }
        
        
        if (request != null) {
            return HTTP_RQST_HTTP2_STREAM_ATTR.get(request);
        }
        
        return null;
    }

    /**
     * Create HTTP2 stream.
     * 
     * @param http2Session the {@link Http2Session} for this {@link Http2Stream}.
     * @param request the {@link HttpRequestPacket} initiating the stream.
     * @param streamId this stream's ID.
     * @param parentStreamId the parent stream, if any.
     * @param priority the priority of this stream.
     */
    protected Http2Stream(final Http2Session http2Session,
            final HttpRequestPacket request,
            final int streamId, final int parentStreamId,
            final boolean exclusive, final int priority) {
        this.http2Session = http2Session;
        this.request = request;
        this.streamId = streamId;
        this.parentStreamId = parentStreamId;
        this.exclusive = exclusive;
        this.priority = priority;
        this.state = State.IDLE;

        inputBuffer = new DefaultInputBuffer(this);
        outputSink = new DefaultOutputSink(this);
        
        HTTP_RQST_HTTP2_STREAM_ATTR.set(request, this);
    }

    /**
     * Construct upgrade stream, which is half HTTP, half HTTP2
     *
     * @param http2Session the {@link Http2Session} for this {@link Http2Stream}.
     * @param request the {@link HttpRequestPacket} initiating the stream.
     * @param priority the priority of this stream.
     */
    protected Http2Stream(final Http2Session http2Session,
            final HttpRequestPacket request,
            final int priority) {
        this.http2Session = http2Session;
        this.request = request;
        this.streamId = UPGRADE_STREAM_ID;
        this.parentStreamId = 0;
        this.priority = priority;

        this.exclusive = false;
        inputBuffer = http2Session.isServer()
                ? new UpgradeInputBuffer(this)
                : new DefaultInputBuffer(this);
        
        outputSink = http2Session.isServer()
                ? new DefaultOutputSink(this)
                : new UpgradeOutputSink(http2Session);
        
        HTTP_RQST_HTTP2_STREAM_ATTR.set(request, this);
    }
    
    Http2Session getHttp2Session() {
        return http2Session;
    }

    public int getPeerWindowSize() {
        return http2Session.getPeerStreamWindowSize();
    }

    public int getLocalWindowSize() {
        return http2Session.getLocalStreamWindowSize();
    }
    
    /**
     * @return the number of writes (not bytes), that haven't reached network layer
     */
    public int getUnflushedWritesCount() {
        return outputSink.getUnflushedWritesCount() ;
    }
    
    public HttpRequestPacket getRequest() {
        return request;
    }
    
    public HttpResponsePacket getResponse() {
        return request.getResponse();
    }

    @SuppressWarnings("unused")
    public boolean isPushEnabled() {
        return http2Session.isPushEnabled();
    }
    
    public int getId() {
        return streamId;
    }

    @SuppressWarnings("unused")
    public int getParentStreamId() {
        return parentStreamId;
    }

    @SuppressWarnings("unused")
    public int getPriority() {
        return priority;
    }

    public boolean isPushStream() {
        return (streamId & 1) == 0;
    }
    
    public boolean isLocallyInitiatedStream() {
        return http2Session.isLocallyInitiatedStream(streamId);
    }

    @Override
    public boolean isOpen() {
        return completeFinalizationCounter < 2;
    }

    @Override
    public void assertOpen() throws IOException {
        if (!isOpen()) {
            final CloseReason cr = this.closeReason;
            assert cr != null;
            
            throw new IOException("closed", cr.getCause());
        }
    }
    
    @Override
    public AttributeHolder getAttributes() {
        return attributes;
    }

    @Override
    @Deprecated
    public boolean canWrite(int length) {
        return canWrite();
    }
    
    @Override
    public boolean canWrite() {
        return outputSink.canWrite();
    }

    @Override
    @Deprecated
    public void notifyCanWrite(final WriteHandler handler, final int length) {
        notifyCanWrite(handler);
    }
    
    @Override
    public void notifyCanWrite(final WriteHandler writeHandler) {
        outputSink.notifyWritePossible(writeHandler);
    }

    StreamOutputSink getOutputSink() {
        return outputSink;
    }
    
    @Override
    public GrizzlyFuture<Closeable> terminate() {
        final FutureImpl<Closeable> future = Futures.createSafeFuture();
        close0(Futures.toCompletionHandler(future), CloseType.LOCALLY, null, false);
        
        return future;
    }

    @Override
    public void terminateSilently() {
        close0(null, CloseType.LOCALLY, null, false);
    }

    @Override
    public void terminateWithReason(final IOException cause) {
        close0(null, CloseType.LOCALLY, cause, false);
    }

    @Override
    public GrizzlyFuture<Closeable> close() {
        final FutureImpl<Closeable> future = Futures.createSafeFuture();
        close0(Futures.toCompletionHandler(future), CloseType.LOCALLY, null, true);
        
        return future;
    }

    @Override
    public void closeSilently() {
        close0(null, CloseType.LOCALLY, null, true);
    }

    /**
     * {@inheritDoc}
     * @deprecated please use {@link #close()} with the following {@link GrizzlyFuture#addCompletionHandler(org.glassfish.grizzly.CompletionHandler)} call
     */
    @Override
    public void close(final CompletionHandler<Closeable> completionHandler) {
        close0(completionHandler, CloseType.LOCALLY, null, true);
    }

    @Override
    public void closeWithReason(final IOException cause) {
        close0(null, CloseType.LOCALLY, cause, false);
    }

    void close0(
            final CompletionHandler<Closeable> completionHandler,
            final CloseType closeType,
            final IOException cause,
            final boolean isCloseOutputGracefully) {
        
        if (closeReason != null) {
            return;
        }
        
        if (closeReasonUpdater.compareAndSet(this, null,
                new CloseReason(closeType, cause))) {
            
            final Termination termination = closeType == CloseType.LOCALLY ?
                    LOCAL_CLOSE_TERMINATION : 
                    PEER_CLOSE_TERMINATION;
            
            // Terminate the input, discard already buffered data
            inputBuffer.terminate(termination);
            
            if (isCloseOutputGracefully) {
                outputSink.close();
            } else {
                // Terminate the output, discard all the pending data in the output buffer
                outputSink.terminate(termination);
            }
            
            notifyCloseListeners();

            if (completionHandler != null) {
                completionHandler.completed(this);
            }
        }
    }

    /**
     * Notifies the Http2Stream that it's been closed remotely.
     */
    void closedRemotely() {
        // Schedule (add to the stream's input queue) the Termination,
        // which will be invoked once read by the user code.
        // This way we simulate Java Socket behavior
        inputBuffer.terminate(
                new Termination(TerminationType.PEER_CLOSE, CLOSED_BY_PEER_STRING, true) {
            @Override
            public void doTask() {
                close0(null, CloseType.REMOTELY, null, false);
            }
        });
    }
    
    /**
     * Notify the Http2Stream that peer sent RST_FRAME.
     */
    void resetRemotely() {
        if (closeReasonUpdater.compareAndSet(this, null,
                new CloseReason(CloseType.REMOTELY, null))) {
            onReset();
            // initial graceful shutdown for input, so user is able to read
            // the buffered data
            inputBuffer.terminate(RESET_TERMINATION);
            
            // forcibly terminate the output, so no more data will be sent
            outputSink.terminate(RESET_TERMINATION);
        }
        
//        rstAssociatedStreams();
    }
    
    void onProcessingComplete() {
        isProcessingComplete = true;
        close();
//        if (closeReasonUpdater.compareAndSet(this, null,
//                new CloseReason(CloseType.LOCALLY, null))) {
//
//            inputBuffer.terminate(LOCAL_CLOSE_TERMINATION);
//            outputSink.close();
//
//            notifyCloseListeners();
//        }
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public void addCloseListener(final CloseListener closeListener) {
        CloseReason cr = closeReason;
        
        // check if connection is still open
        if (cr == null) {
            // add close listener
            closeListeners.add(closeListener);
            // check the connection state again
            cr = closeReason;
            if (cr != null && closeListeners.remove(closeListener)) {
                // if connection was closed during the method call - notify the listener
                try {
                    closeListener.onClosed(this, cr.getType());
                } catch (IOException ignored) {
                }
            }
        } else { // if connection is closed - notify the listener
            try {
                closeListener.onClosed(this, cr.getType());
            } catch (IOException ignored) {
            }
        }
    }

    @Override
    public boolean removeCloseListener(final CloseListener closeListener) {
        return closeListeners.remove(closeListener);
    }

    @SuppressWarnings("Duplicates")
    @Override
    public GrizzlyFuture<CloseReason> closeFuture() {
        if (closeFuture == null) {
            synchronized (this) {
                if (closeFuture == null) {
                    final CloseReason cr = closeReason;

                    if (cr == null) {
                        final FutureImpl<CloseReason> f
                                = Futures.createSafeFuture();
                        addCloseListener(new org.glassfish.grizzly.CloseListener() {

                            @Override
                            public void onClosed(Closeable closeable, @SuppressWarnings("deprecation") ICloseType type)
                                    throws IOException {
                                final CloseReason cr = closeReason;
                                assert cr != null;
                                f.result(cr);
                            }
                        });

                        closeFuture = f;
                    } else {
                        closeFuture = Futures.createReadyFuture(cr);
                    }
                }
            }
        }
        
        return closeFuture;
    }
    
    void onInputClosed() {
        if (completeFinalizationCounterUpdater.incrementAndGet(this) == 2) {
            closeStream();
        }
    }

    void onOutputClosed() {
        if (completeFinalizationCounterUpdater.incrementAndGet(this) == 2) {
            closeStream();
        }
    }

    int getInboundHeaderFramesCounter() {
        return inboundHeaderFramesCounter;
    }

    /**
     * The method is called when an inbound headers are decoded for the stream,
     * which means all the header frames arrived and we're ready to parse the headers.
     * 
     * @param isEOS flag indicating if the end-of-stream has been reached
     *
     * @throws Http2StreamException if an error occurs processing the headers frame
     */
    void onRcvHeaders(final boolean isEOS) throws Http2StreamException {

        inboundHeaderFramesCounter++;
        
        switch (inboundHeaderFramesCounter) {
            case 1: // first header block to process
                
                // change the state
                onReceiveHeaders();
                if (isEOS) {
                    onReceiveEndOfStream();
                }

                break;
            case 2: {   // might be a trailing headers, which is ok
                if (isEOS) {
                    break;  // trailing headers
                }
                
                // otherwise goto "default" and throw an error
            }
            default: { // WHAT?
                inputBuffer.close(UNEXPECTED_FRAME_TERMINATION);
                throw new Http2StreamException(getId(),
                        ErrorCode.PROTOCOL_ERROR, "Unexpected headers frame");
            }
        }
    }
    
    /**
     * The method is called when an outbound headers are about to be sent to the peer.
     *
     * @param isEOS flag indicating if the end-of-stream has been reached
     */
    void onSndHeaders(final boolean isEOS) {
        // change the state
        onSendHeaders();
        if (isEOS) {
            onSendEndOfStream();
        }
    }

    private Buffer cachedInputBuffer;
    private boolean cachedIsLast;
    
    Http2StreamException assertCanAcceptData() {
        if (isPushStream() && isLocallyInitiatedStream()) {
            return new Http2StreamException(getId(),
                    ErrorCode.PROTOCOL_ERROR,
                    "Data frame received on a push-stream");
        }

        if (getState() == State.HALF_CLOSED_REMOTE) {

            close0(null, CloseType.LOCALLY,
                    new IOException("Received DATA frame on HALF_CLOSED_REMOTE stream."), false);
            return new Http2StreamException(getId(),
                    ErrorCode.STREAM_CLOSED, "Received DATA frame on HALF_CLOSED_REMOTE stream.");
        }

        if (inboundHeaderFramesCounter != 1) { // we accept data only if we received one HeadersFrame
            close0(null, CloseType.LOCALLY,
                    new IOException("DATA frame came before HEADERS frame."), false);
            
            return new Http2StreamException(getId(),
                    ErrorCode.PROTOCOL_ERROR, "DATA frame came before HEADERS frame.");
        }
        
        return null;
    }
    
    void offerInputData(final Buffer data, final boolean isLast) {
        final boolean isFirstBufferCached = (cachedInputBuffer == null);
        cachedIsLast |= isLast;
        cachedInputBuffer = Buffers.appendBuffers(
                http2Session.getMemoryManager(),
                cachedInputBuffer, data);
        
        if (isFirstBufferCached) {
            http2Session.streamsToFlushInput.add(this);
        }
    }
    
    void flushInputData() {
        final Buffer cachedInputBufferLocal = cachedInputBuffer;
        final boolean cachedIsLastLocal = cachedIsLast;
        cachedInputBuffer = null;
        cachedIsLast = false;

        if (LOGGER.isLoggable(Level.FINEST)) {
            LOGGER.log(Level.FINEST, "{0} streamId={1}: flushInputData cachedInputBufferLocal={2}",
                    new Object[]{Thread.currentThread().getName(), getId(),
                        cachedInputBufferLocal != null
                                ? cachedInputBufferLocal.toString()
                                : null});
        }
        if (cachedInputBufferLocal != null) {
            if (cachedInputBufferLocal.isComposite()) {
                ((CompositeBuffer) cachedInputBufferLocal).allowInternalBuffersDispose(true);
                cachedInputBufferLocal.allowBufferDispose(true);
                ((CompositeBuffer) cachedInputBufferLocal).disposeOrder(DisposeOrder.LAST_TO_FIRST);
            }
            
            final int size = cachedInputBufferLocal.remaining();
            if (!inputBuffer.offer(cachedInputBufferLocal, cachedIsLastLocal)) {
                // if we can't add this buffer to the stream input buffer -
                // we have to release the part of connection window allocated
                // for the buffer
                http2Session.ackConsumedData(size);
            }
        }
    }
    
    HttpContent pollInputData() throws IOException {
        return inputBuffer.poll();
    }
    
    private void closeStream() {
        // TODO ensure stream proper transitions to CLOSED state
        //Http2StreamState.close(this);
        http2Session.deregisterStream();
    }
    
    HttpHeader getInputHttpHeader() {
        return (isLocallyInitiatedStream() ^ isPushStream()) ?
                request.getResponse() :
                request;
    }
    
    HttpHeader getOutputHttpHeader() {
        return (!isLocallyInitiatedStream() ^ isPushStream()) ?
                request.getResponse() :
                request;
    }
    
    /**
     * Notify all close listeners
     */
    @SuppressWarnings("unchecked")
    private void notifyCloseListeners() {
        final CloseReason cr = closeReason;
        
        CloseListener closeListener;
        while ((closeListener = closeListeners.poll()) != null) {
            try {
                closeListener.onClosed(this, cr.getType());
            } catch (IOException ignored) {
            }
        }
    }

    /**
     * The state of the specified stream.
     *
     * @return the current State.
     */
     State getState() {
        synchronized (this) {
            return state;
        }
    }

    /**
     * Checks if the specified stream is closed.
     *
     * @return true if the stream is closed, otherwise false.
     */
    boolean isClosed() {
        synchronized (this) {
            return (state == State.CLOSED);
        }
    }

    /**
     * Checks if the specified stream is idle.
     *
     * @return true if the stream is idle, otherwise false.
     */

    boolean isIdle() {
        synchronized (this) {
            return (state == State.IDLE);
        }
    }

    /**
     * Checks if the specified stream is can send frames.
     *
     * @return true if the stream can send frames, otherwise false.
     */
     boolean canSendFrames() {
        synchronized (this) {
            return (state != State.CLOSED && state != State.HALF_CLOSED_LOCAL);
        }
    }

    /**
     * Checks if the specified stream is can receive frames.
     *
     * @return true if the stream can receive frames, otherwise false.
     */
    boolean canReceiveFrames() {
        synchronized (this) {
            return (state != State.CLOSED && state != State.HALF_CLOSED_REMOTE);
        }
    }


    /**
     * Transition the stream from IDLE to RESERVED_LOCAL.
     */
    void onSendPushPromise() {
        synchronized (this) {
            if (state == State.IDLE) {
                state = State.RESERVED_LOCAL;
            }
        }
    }

    /**
     * Transition the stream from IDLE to RESERVED_REMOTE.
     */
    void onReceivePushPromise() {
        synchronized (this) {
            if (state == State.IDLE) {
                state = State.RESERVED_REMOTE;
            }
        }
    }

    /**
     * Transition the stream from:
     *   - IDLE           -> OPEN
     *   - RESERVED_LOCAL -> HALF_CLOSED_REMOTE
     */
    private void onSendHeaders() {
        synchronized (this) {
            switch (state) {
                case IDLE:
                    state = State.OPEN;
                    break;
                case RESERVED_LOCAL:
                    state = State.HALF_CLOSED_REMOTE;
                    break;
            }
        }
    }

    /**
     * Transition the stream from:
     *   - IDLE            -> OPEN
     *   - RESERVED_REMOTE -> HALF_CLOSED_LOCAL
     */
    private void onReceiveHeaders() {
        synchronized (this) {
            switch (state) {
                case IDLE:
                    state = State.OPEN;
                    break;
                case RESERVED_REMOTE:
                    state = State.HALF_CLOSED_LOCAL;
                    break;
            }
        }
    }

    /**
     * Transition the stream from:
     *   - OPEN               -> HALF_CLOSED_LOCAL
     *   - HALF_CLOSED_REMOTE -> CLOSED
     */
    private void onSendEndOfStream() {
        synchronized (this) {
            switch (state) {
                case OPEN:
                    state = State.HALF_CLOSED_LOCAL;
                    break;
                case HALF_CLOSED_REMOTE:
                    state = State.CLOSED;
                    break;
            }
        }
    }

    /**
     * Transition the stream from:
     *   - OPEN               -> HALF_CLOSED_REMOTE
     *   - HALF_CLOSED_LOCAL  -> CLOSED
     */
    private void onReceiveEndOfStream() {
        synchronized (this) {
            switch (state) {
                case OPEN:
                    state = State.HALF_CLOSED_REMOTE;
                    break;
                case HALF_CLOSED_LOCAL:
                    state = State.CLOSED;
                    break;
            }
        }
    }

    /**
     * Transition the stream from:
     *   - OPEN               -> CLOSED
     *   - RESERVED_LOCAL     -> CLOSED
     *   - RESERVED_REMOTE    -> CLOSED
     *   - HALF_CLOSED_LOCAL  -> CLOSED
     *   - HALF_CLOSED_REMOTE -> CLOSED
     */
    private void onReset() {
        synchronized (this) {
            switch (state) {
                case OPEN:
                case RESERVED_LOCAL:
                case RESERVED_REMOTE:
                case HALF_CLOSED_LOCAL:
                case HALF_CLOSED_REMOTE:
                    state = State.CLOSED;
                    break;
            }
        }
    }
}
