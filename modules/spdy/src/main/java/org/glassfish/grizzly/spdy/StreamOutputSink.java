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

import org.glassfish.grizzly.spdy.utils.ChunkedCompletionHandler;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.CompletionHandler;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.WriteHandler;
import org.glassfish.grizzly.WriteResult;
import org.glassfish.grizzly.asyncqueue.MessageCloner;
import org.glassfish.grizzly.asyncqueue.AsyncQueueRecord;
import org.glassfish.grizzly.asyncqueue.TaskQueue;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.http.HttpContent;
import org.glassfish.grizzly.http.HttpHeader;
import org.glassfish.grizzly.http.HttpPacket;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.http.HttpResponsePacket;
import org.glassfish.grizzly.memory.Buffers;
import org.glassfish.grizzly.spdy.SpdyStream.Termination;
import org.glassfish.grizzly.spdy.frames.SpdyFrame;
import org.glassfish.grizzly.spdy.frames.SynReplyFrame;
import org.glassfish.grizzly.spdy.frames.SynStreamFrame;
import org.glassfish.grizzly.spdy.frames.WindowUpdateFrame;

import static org.glassfish.grizzly.spdy.Constants.*;

/**
 * Class represents an output sink associated with specific {@link SpdyStream}. 
 * 
 * The implementation is aligned with SPDY/3 requirements with regards to message
 * flow control.
 * 
 * @author Alexey Stashok
 */
final class StreamOutputSink {
    private static final Logger LOGGER = Grizzly.logger(StreamOutputSink.class);
    private static final Level LOGGER_LEVEL = Level.INFO;

    private static final int MAX_OUTPUT_QUEUE_SIZE = 65536;

    private static final int ZERO_QUEUE_RECORD_SIZE = 1;
    
    private static final OutputQueueRecord TERMINATING_QUEUE_RECORD =
            new OutputQueueRecord(null, null, true, true);
    
    // async output queue
    final TaskQueue<OutputQueueRecord> outputQueue =
            TaskQueue.createTaskQueue(new TaskQueue.MutableMaxQueueSize() {

        @Override
        public int getMaxQueueSize() {
            return MAX_OUTPUT_QUEUE_SIZE;
        }
    });
    
    // the space (in bytes) in flow control window, that still could be used.
    // in other words the number of bytes, which could be sent to the peer
    private final AtomicInteger availStreamWindowSize;

    // true, if last output frame has been queued
    private volatile boolean isLastFrameQueued;
    // not null if last output frame has been sent or forcibly terminated
    private Termination terminationFlag;
    
    // associated spdy session
    private final SpdySession spdySession;
    // associated spdy stream
    private final SpdyStream spdyStream;

    // counter for unflushed writes
    private final AtomicInteger unflushedWritesCounter = new AtomicInteger();
    // sync object to count/notify flush handlers
    private final Object flushHandlersSync = new Object();
    // flush handlers queue
    private BundleQueue<CompletionHandler<SpdyStream>> flushHandlersQueue;
    
    StreamOutputSink(final SpdyStream spdyStream) {
        this.spdyStream = spdyStream;
        spdySession = spdyStream.getSpdySession();
        availStreamWindowSize = new AtomicInteger(spdyStream.getPeerWindowSize());
    }

    public boolean canWrite() {
        return outputQueue.size() < MAX_OUTPUT_QUEUE_SIZE;
    }

    public void notifyWritePossible(final WriteHandler writeHandler) {
        outputQueue.notifyWritePossible(writeHandler, MAX_OUTPUT_QUEUE_SIZE);
    }

    public void assertReady() throws IOException {
        // if the last frame (fin flag == 1) has been queued already - throw an IOException
        if (isTerminated()) {
            throw new IOException(terminationFlag.getDescription());
        } else if (isLastFrameQueued) {
            throw new IOException("Write beyond end of stream");
        }
    }

    /**
     * The method is called by Spdy Filter once WINDOW_UPDATE message comes
     * for this {@link SpdyStream}.
     * 
     * @param delta the delta.
     */
    public void onPeerWindowUpdate(final int delta) throws SpdyStreamException {
        // update the available window size
        availStreamWindowSize.addAndGet(delta);
        
        // try to write until window limit allows
        while (isWantToWrite() &&
                !outputQueue.isEmpty()) {
            
            // pick up the first output record in the queue
            OutputQueueRecord outputQueueRecord = outputQueue.poll();

            // if there is nothing to write - return
            if (outputQueueRecord == null) {
                return;
            }
            
            // if it's terminating record - processFin
            if (outputQueueRecord == TERMINATING_QUEUE_RECORD) {
                // if it's TERMINATING_QUEUE_RECORD - don't forget to release ATOMIC_QUEUE_RECORD_SIZE
                releaseWriteQueueSpace(0, true, true);
                writeEmptyFin();
                return;
            }
            
            final FlushCompletionHandler completionHandler =
                    outputQueueRecord.chunkedCompletionHandler;
            boolean isLast = outputQueueRecord.isLast;
            final boolean isZeroSizeData = outputQueueRecord.isZeroSizeData;
            final Source resource = outputQueueRecord.resource;
            
            // check if output record's buffer is fitting into window size
            // if not - split it into 2 parts: part to send, part to keep in the queue
            final int bytesToSend = checkOutputWindow(resource.remaining());
            final Buffer dataChunkToSend = resource.read(bytesToSend);
            final boolean hasRemaining = resource.hasRemaining();
            
            
            // if there is a chunk to store
            if (hasRemaining) {
                // Create output record for the chunk to be stored
                outputQueueRecord.reset(resource, completionHandler, isLast);
                outputQueueRecord.incChunksCounter();
                
                // reset isLast for the current chunk
                isLast = false;
            } else {
                outputQueueRecord.release();
                outputQueueRecord = null;
            }

            // if there is a chunk to sent
            if (dataChunkToSend != null &&
                    (dataChunkToSend.hasRemaining() || isLast)) {
                final int dataChunkToSendSize = dataChunkToSend.remaining();
                
                // send a spdydata frame
                writeDownStream0(null, dataChunkToSend, completionHandler,
                        null, isLast);
                
                // update the available window size bytes counter
                availStreamWindowSize.addAndGet(-dataChunkToSendSize);
                releaseWriteQueueSpace(dataChunkToSendSize,
                        isZeroSizeData, outputQueueRecord == null);
                
                outputQueue.doNotify();
            } else if (isZeroSizeData && outputQueueRecord == null) {
                // if it's atomic and no remainder left - don't forget to release ATOMIC_QUEUE_RECORD_SIZE
                releaseWriteQueueSpace(0, true, true);
                outputQueue.doNotify();
            }
            
            if (outputQueueRecord != null) {
                // if there is a chunk to be stored - store it and return
                outputQueue.setCurrentElement(outputQueueRecord);
                break;
            }
        }
    }
    
    public void writeDownStream(final HttpPacket httpPacket) throws IOException {
        assertReady();
        
        // Write the message starting at upstream FilterChain, because it has
        // to pass SpdyHandlerFilter
        spdySession.getUpstreamChain().write(spdySession.getConnection(), null,
                httpPacket, null);
    }
    
    /**
     * Send an {@link HttpPacket} to the {@link SpdyStream}.
     * 
     * @param httpPacket {@link HttpPacket} to send
     * @throws IOException 
     */
    public synchronized void writeDownStream(final HttpPacket httpPacket,
                                             final FilterChainContext ctx)
    throws IOException {
        writeDownStream(httpPacket, ctx, null);
    }
    
    /**
     * Send an {@link HttpPacket} to the {@link SpdyStream}.
     * 
     * The writeDownStream(...) methods have to be synchronized with shutdown().
     * 
     * @param httpPacket {@link HttpPacket} to send
     * @param completionHandler the {@link CompletionHandler},
     *          which will be notified about write progress.
     * @throws IOException 
     */
    public synchronized void writeDownStream(final HttpPacket httpPacket,
                                             final FilterChainContext ctx,
                                             final CompletionHandler<WriteResult> completionHandler)
    throws IOException {
        writeDownStream(httpPacket, ctx, completionHandler, null);
    }

    /**
     * Send an {@link HttpPacket} to the {@link SpdyStream}.
     * 
     * The writeDownStream(...) methods have to be synchronized with shutdown().
     * 
     * @param httpPacket {@link HttpPacket} to send
     * @param completionHandler the {@link CompletionHandler},
     *          which will be notified about write progress.
     * @param messageCloner the {@link MessageCloner}, which will be able to
     *          clone the message in case it can't be completely written in the
     *          current thread.
     * @throws IOException 
     */
    synchronized <E> void writeDownStream(final HttpPacket httpPacket,
                                          final FilterChainContext ctx,
                                          final CompletionHandler<WriteResult> completionHandler,
                                          final MessageCloner<Buffer> messageCloner)
    throws IOException {
        assert ctx != null;

        assertReady();
        
        final HttpHeader httpHeader = spdyStream.getOutputHttpHeader();
        final HttpContent httpContent = HttpContent.isContent(httpPacket) ? (HttpContent) httpPacket : null;
        
        SpdyFrame headerFrame = null;
        OutputQueueRecord outputQueueRecord = null;
        
        boolean isDeflaterLocked = false;
        
        try { // try-finally block to release deflater lock if needed
            
            // If HTTP header hasn't been commited - commit it
            if (!httpHeader.isCommitted()) {
                // do we expect any HTTP payload?
                final boolean isNoPayload = !httpHeader.isExpectContent() ||
                        (httpContent != null && httpContent.isLast() &&
                        !httpContent.getContent().hasRemaining());
                
                headerFrame = encodeHttpHeader(ctx, httpHeader, isNoPayload);

                isDeflaterLocked = true;
                httpHeader.setCommitted(true);

                if (isNoPayload || httpContent == null) {
                    // if we don't expect any HTTP payload, mark this frame as
                    // last and return
                    unflushedWritesCounter.incrementAndGet();
                    writeDownStream0(headerFrame, null,
                            new FlushCompletionHandler(completionHandler),
                            messageCloner, isNoPayload);
                    return;
                }
            }

            // if there is nothing to write - return
            if (httpContent == null) {
                return;
            }
            
            spdySession.handlerFilter.onHttpContentEncoded(httpContent, ctx);

            Buffer dataToSend = null;
            boolean isLast = httpContent.isLast();
            Buffer data = httpContent.getContent();
            final int dataSize = data.remaining();

            if (isLast && dataSize == 0) {
                close();
                return;
            }

            unflushedWritesCounter.incrementAndGet();
            final FlushCompletionHandler flushCompletionHandler =
                    new FlushCompletionHandler(completionHandler);

            boolean isDataCloned = false;

            final boolean isZeroSizeData = (dataSize == 0);
            final int spaceToReserve = isZeroSizeData ? ZERO_QUEUE_RECORD_SIZE : dataSize;

            // Check if output queue is not empty - add new element
            if (reserveWriteQueueSpace(spaceToReserve) > spaceToReserve) {
                // if the queue is not empty - the headers should have been sent
                assert headerFrame == null;

                if (messageCloner != null) {
                    data = (Buffer) messageCloner.clone(
                            spdySession.getConnection(), data);
                    isDataCloned = true;
                }

                outputQueueRecord = new OutputQueueRecord(
                        Source.factory(spdyStream)
                            .createBufferSource(data),
                        flushCompletionHandler, isLast, isZeroSizeData);

                outputQueue.offer(outputQueueRecord);

                // check if our element wasn't forgotten (async)
                if (outputQueue.size() != spaceToReserve ||
                        !outputQueue.remove(outputQueueRecord)) {
                    // if not - return
                    return;
                }

                outputQueueRecord = null;
            }

            // our element is first in the output queue

            final int remaining = data.remaining();

            // check if output record's buffer is fitting into window size
            // if not - split it into 2 parts: part to send, part to keep in the queue
            final int fitWindowLen = checkOutputWindow(remaining);

            // if there is a chunk to store
            if (fitWindowLen < remaining) {
                if (!isDataCloned && messageCloner != null) {
                    data = (Buffer) messageCloner.clone(
                            spdySession.getConnection(), data);
                    isDataCloned = true;
                }

                final Buffer dataChunkToStore = splitOutputBufferIfNeeded(
                        data, fitWindowLen);

                // Create output record for the chunk to be stored
                outputQueueRecord = new OutputQueueRecord(
                        Source.factory(spdyStream)
                            .createBufferSource(dataChunkToStore),
                        flushCompletionHandler,
                        isLast, isZeroSizeData);

                // reset completion handler and isLast for the current chunk
                isLast = false;
            }

            // if there is a chunk to send
            if (data != null &&
                    (data.hasRemaining() || isLast)) {

                final int dataChunkToSendSize = data.remaining();

                // update the available window size bytes counter
                availStreamWindowSize.addAndGet(-dataChunkToSendSize);
                releaseWriteQueueSpace(dataChunkToSendSize,
                        isZeroSizeData, outputQueueRecord == null);

                dataToSend = data;
            }

            // if there's anything to send - send it
            if (headerFrame != null || dataToSend != null) {
                
                // if another part of data is stored in the queue -
                // we have to increase CompletionHandler counter to avoid
                // premature notification
                if (outputQueueRecord != null) {
                    outputQueueRecord.incChunksCounter();
                }
                
                writeDownStream0(headerFrame, dataToSend, flushCompletionHandler,
                        isDataCloned ? null : messageCloner,
                        isLast);
            }
        
        } finally {
            if (isDeflaterLocked) {
                spdySession.getDeflaterLock().unlock();
            }
        }
        
        if (outputQueueRecord == null) {
            return;
        }
        
        addOutputQueueRecord(outputQueueRecord);
    }

    /**
     * Send the data represented by the {@link Source} to the {@link SpdyStream}.
     * Unlike {@link #writeDownStream(org.glassfish.grizzly.http.HttpPacket, org.glassfish.grizzly.filterchain.FilterChainContext)} ,
     * here we assume the resource is going to be send on non-commited header and
     * it will be the only resource sent over this {@link SpdyStream} (isLast flag will be set).
     * 
     * The writeDownStream(...) methods have to be synchronized with shutdown().
     * 
     * @param source {@link Source} to send
     * @throws IOException 
     */
    synchronized void writeDownStream(final Source source,
            final FilterChainContext ctx) throws IOException {
        
        assert ctx != null;
        
        assertReady();

        isLastFrameQueued = true;
        
        final HttpHeader httpHeader = spdyStream.getOutputHttpHeader();
        
        if (httpHeader.isCommitted()) {
            throw new IllegalStateException("Headers have been already commited");
        }
        
        SpdyFrame headerFrame;
        OutputQueueRecord outputQueueRecord = null;
        
        boolean isDeflaterLocked = false;
        
        try { // try-finally block to release deflater lock if needed
            
            // We assume HTTP header hasn't been commited
            
            // do we expect any HTTP payload?
            final boolean isNoPayload =
                    !httpHeader.isExpectContent() ||
                    source == null || !source.hasRemaining();

            headerFrame = encodeHttpHeader(ctx, httpHeader, isNoPayload);

            isDeflaterLocked = true;
            httpHeader.setCommitted(true);

            if (isNoPayload) {
                unflushedWritesCounter.incrementAndGet();
                // if we don't expect any HTTP payload, mark this frame as
                // last and return
                writeDownStream0(headerFrame, null, new FlushCompletionHandler(null),
                        null, isNoPayload);
                return;
            }

            final long dataSize = source.remaining();

            if (dataSize == 0) {
                close();
                return;
            }

            // our element is first in the output queue
            reserveWriteQueueSpace(ZERO_QUEUE_RECORD_SIZE);

            boolean isLast = true;

            // check if output record's buffer is fitting into window size
            // if not - split it into 2 parts: part to send, part to keep in the queue
            final int fitWindowLen = checkOutputWindow(dataSize);

            // if there is a chunk to store
            if (fitWindowLen < dataSize) {
                // Create output record for the chunk to be stored
                outputQueueRecord = new OutputQueueRecord(
                        source, null, true, true);
                isLast = false;
            }

            final Buffer bufferToSend = source.read(fitWindowLen);
            
            final int dataChunkToSendSize = bufferToSend.remaining();

            // update the available window size bytes counter
            availStreamWindowSize.addAndGet(-dataChunkToSendSize);
            releaseWriteQueueSpace(dataChunkToSendSize, true,
                    outputQueueRecord == null);

            unflushedWritesCounter.incrementAndGet();
            writeDownStream0(headerFrame, bufferToSend,
                    new FlushCompletionHandler(null), null, isLast);
        
        } finally {
            if (isDeflaterLocked) {
                spdySession.getDeflaterLock().unlock();
            }
        }
        
        if (outputQueueRecord == null) {
            return;
        }
        
        addOutputQueueRecord(outputQueueRecord);
    }
    
    /**
     * Flush {@link SpdyStream} output and notify {@link CompletionHandler} once
     * all output data has been flushed.
     * 
     * @param completionHandler {@link CompletionHandler} to be notified
     */
    public void flush(
            final CompletionHandler<SpdyStream> completionHandler) {
        
        // check if there are pending unflushed data
        if (unflushedWritesCounter.get() > 0) {
            // if yes - synchronize do disallow descrease counter from other thread (increasing is ok)
            synchronized (flushHandlersSync) {
                // double check the pending flushes counter
                final int counterNow = unflushedWritesCounter.get();
                if (counterNow > 0) {
                    // if there are pending flushes
                    if (flushHandlersQueue == null) {
                        // create a flush handlers queue
                        flushHandlersQueue =
                                new BundleQueue<CompletionHandler<SpdyStream>>();
                    }
                    
                    // add the handler to the queue
                    flushHandlersQueue.add(counterNow, completionHandler);
                    
                    return;
                }
            }
        }
        
        // if there are no pending flushes - notify the handler
        completionHandler.completed(spdyStream);
    }
    
    /**
     * The method is responsible for checking the current output window size.
     * The returned integer value is the size of the data, which could be
     * sent now.
     * 
     * @param size check the provided size against the window size limit.
     *
     * @return the amount of data that may be written.
     */
    private int checkOutputWindow(final long size) {
        // take a snapshot of the current output window state
        return Math.min(availStreamWindowSize.get(), (int) size);
    }

    private Buffer splitOutputBufferIfNeeded(final Buffer buffer,
            final int length) {
        if (length == buffer.remaining()) {
            return null;
        }

        return buffer.split(buffer.position() + length);
    }

    void writeWindowUpdate(final int currentUnackedBytes) {
        spdySession.getOutputSink().writeDownStream(
                WindowUpdateFrame.builder()
                        .streamId(spdyStream.getStreamId())
                        .delta(currentUnackedBytes)
                        .build());
    }

    private void writeDownStream0(final SpdyFrame headerFrame,
            final Buffer data,
            final CompletionHandler<WriteResult> completionHandler,
            final MessageCloner<Buffer> messageCloner,
            final boolean isLast) {
        
        spdySession.getOutputSink().writeDataDownStream(spdyStream, headerFrame,
                data, completionHandler, messageCloner, isLast);
        
        if (isLast) {
            terminate(OUT_FIN_TERMINATION);
        }
    }

    /**
     * Closes the output sink by adding last DataFrame with the FIN flag to a queue.
     * If the output sink is already closed - method does nothing.
     */
    synchronized void close() {
        if (!isClosed()) {
            isLastFrameQueued = true;
            
            if (outputQueue.isEmpty()) {
                writeEmptyFin();
                return;
            }
            
            outputQueue.reserveSpace(ZERO_QUEUE_RECORD_SIZE);
            outputQueue.offer(TERMINATING_QUEUE_RECORD);
            
            if (outputQueue.size() == ZERO_QUEUE_RECORD_SIZE &&
                    outputQueue.remove(TERMINATING_QUEUE_RECORD)) {
                writeEmptyFin();
            }
        }
    }

    /**
     * Unlike {@link #close()} this method forces the output sink termination
     * by setting termination flag and canceling all the pending writes.
     */
    synchronized void terminate(final Termination terminationFlag) {
        if (!isTerminated()) {
            this.terminationFlag = terminationFlag;
            outputQueue.onClose();
            // NOTIFY STREAM
            spdyStream.onOutputClosed();
        }
    }
    
    boolean isClosed() {
        return isLastFrameQueued || isTerminated();
    }
    
    /**
     * @return the number of writes (not bytes), that haven't reached network layer
     */
    int getUnflushedWritesCount() {
        return unflushedWritesCounter.get();
    }
    
    private boolean isTerminated() {
        return terminationFlag != null;
    }
    
    private void writeEmptyFin() {
        if (!isTerminated()) {
            unflushedWritesCounter.incrementAndGet();
            writeDownStream0(null, Buffers.EMPTY_BUFFER,
                    new FlushCompletionHandler(null), null, true);
        }
    }

    private boolean isWantToWrite() {
        // update the available window size
        final int availableWindowSizeBytesNow = availStreamWindowSize.get();

        // get the current peer's window size limit
        final int windowSizeLimit = spdyStream.getPeerWindowSize();

        return availableWindowSizeBytesNow >= (windowSizeLimit / 4);
    }

    private SpdyFrame encodeHttpHeader(final FilterChainContext ctx,
            final HttpHeader httpHeader,
            final boolean isLast)
            throws IOException {
        
        SpdyFrame headerFrame;
        // encode HTTP packet header
        if (!httpHeader.isRequest()) {
            final Buffer compressedSynStreamHeaders;
            if (!spdyStream.isUnidirectional()) {
                final Buffer compressedHeaders =
                        SpdyEncoderUtils.encodeSynReplyHeadersAndLock(
                                spdySession, (HttpResponsePacket) httpHeader);
                headerFrame = SynReplyFrame.builder().streamId(spdyStream.getStreamId()).
                        last(isLast).compressedHeaders(compressedHeaders).build();
            } else {
                compressedSynStreamHeaders =
                        SpdyEncoderUtils.encodeUnidirectionalSynStreamHeadersAndLock(
                                spdyStream, (SpdyResponse) httpHeader);
                
                headerFrame = SynStreamFrame.builder().streamId(spdyStream.getStreamId()).
                        associatedStreamId(spdyStream.getAssociatedToStreamId()).
                        unidirectional(true).
                        last(isLast).compressedHeaders(compressedSynStreamHeaders).build();
            }
        } else {
            final Buffer compressedHeaders =
                    SpdyEncoderUtils.encodeSynStreamHeadersAndLock(spdyStream,
                            (HttpRequestPacket) httpHeader);
            headerFrame = SynStreamFrame.builder().streamId(spdyStream.getStreamId()).
                    associatedStreamId(spdyStream.getAssociatedToStreamId()).
                    unidirectional(spdyStream.isUnidirectional()).
                    last(isLast).compressedHeaders(compressedHeaders).build();
            
            spdySession.handlerFilter.onHttpHeadersEncoded(httpHeader, ctx);
        }
        return headerFrame;
    }
    
    private void addOutputQueueRecord(OutputQueueRecord outputQueueRecord)
            throws SpdyStreamException {
        
        do { // Make sure current outputQueueRecord is not forgotten
            
            // set the outputQueueRecord as the current
            outputQueue.setCurrentElement(outputQueueRecord);

            // check if situation hasn't changed and we can't send the data chunk now
            if (isWantToWrite() &&
                    outputQueue.compareAndSetCurrentElement(outputQueueRecord, null)) {
                
                // if we can send the output record now - do that
                
                final FlushCompletionHandler chunkedCompletionHandler =
                        outputQueueRecord.chunkedCompletionHandler;

                boolean isLast = outputQueueRecord.isLast;
                final boolean isZeroSizeData = outputQueueRecord.isZeroSizeData;
                
                final Source currentResource = outputQueueRecord.resource;
                
                final int fitWindowLen = checkOutputWindow(currentResource.remaining());
                final Buffer dataChunkToSend = currentResource.read(fitWindowLen);
                
                
                // if there is a chunk to store
                if (currentResource.hasRemaining()) {
                    // Create output record for the chunk to be stored
                    outputQueueRecord.reset(currentResource,
                            chunkedCompletionHandler,
                            isLast);
                    outputQueueRecord.incChunksCounter();
                    
                    // reset isLast for the current chunk
                    isLast = false;
                } else {
                    outputQueueRecord.release();
                    outputQueueRecord = null;
                }

                // if there is a chunk to send
                if (dataChunkToSend != null &&
                        (dataChunkToSend.hasRemaining() || isLast)) {
                    final int dataChunkToSendSize = dataChunkToSend.remaining();

                    writeDownStream0(null, dataChunkToSend,
                            chunkedCompletionHandler, null, isLast);
                    
                    // update the available window size bytes counter
                    availStreamWindowSize.addAndGet(-dataChunkToSendSize);
                    releaseWriteQueueSpace(dataChunkToSendSize, isZeroSizeData,
                            outputQueueRecord == null);
                    
                } else if (isZeroSizeData && outputQueueRecord == null) {
                    // if it's atomic and no remainder left - don't forget to release ATOMIC_QUEUE_RECORD_SIZE
                    releaseWriteQueueSpace(0, true, true);
                }
            } else {
                break; // will be (or already) written asynchronously
            }
        } while (outputQueueRecord != null);
    }
    
    private int reserveWriteQueueSpace(final int spaceToReserve) {
        return outputQueue.reserveSpace(spaceToReserve);
    }

    private void releaseWriteQueueSpace(final int justSentBytes, final boolean isAtomic,
            final boolean isEndOfChunk) {
        if (isEndOfChunk) {
            outputQueue.releaseSpace(isAtomic ? ZERO_QUEUE_RECORD_SIZE : justSentBytes);
        } else if (!isAtomic) {
            outputQueue.releaseSpace(justSentBytes);
        }
    }

    private static class OutputQueueRecord extends AsyncQueueRecord<WriteResult> {
        private Source resource;
        private FlushCompletionHandler chunkedCompletionHandler;
        
        private boolean isLast;
        
        private final boolean isZeroSizeData;
        
        public OutputQueueRecord(final Source resource,
                final FlushCompletionHandler completionHandler,
                final boolean isLast, final boolean isZeroSizeData) {
            super(null, null, null);
            
            this.resource = resource;
            this.chunkedCompletionHandler = completionHandler;
            this.isLast = isLast;
            this.isZeroSizeData = isZeroSizeData;
        }

        private void incChunksCounter() {
            if (chunkedCompletionHandler != null) {
                chunkedCompletionHandler.incChunks();
            }
        }

        private void reset(final Source resource,
                final FlushCompletionHandler completionHandler,
                final boolean last) {
            this.resource = resource;
            this.chunkedCompletionHandler = completionHandler;
            this.isLast = last;
        }
        
        public void release() {
            if (resource != null) {
                resource.release();
                resource = null;
            }
        }

        @Override
        public void notifyFailure(final Throwable e) {
            final CompletionHandler chLocal = chunkedCompletionHandler;
            chunkedCompletionHandler = null;
            try {
                if (chLocal != null) {
                    chLocal.failed(e);
                }
            } finally {
                release();
            }
        }
        
        @Override
        public void recycle() {
        }

        @Override
        public WriteResult getCurrentResult() {
            return null;
        }
    }
    
    /**
     * Flush {@link CompletionHandler}, which will be passed on each
     * {@link SpdyStream} write to make sure the data reached the wires.
     * 
     * Usually <tt>FlushCompletionHandler</tt> is also used as a wrapper for
     * custom {@link CompletionHandler} provided by users.
     */
    private final class FlushCompletionHandler extends ChunkedCompletionHandler {

        public FlushCompletionHandler(
                final CompletionHandler<WriteResult> parentCompletionHandler) {
            super(parentCompletionHandler);
        }

        @Override
        protected void done0() {
            synchronized (flushHandlersSync) { // synchronize with flush()
                unflushedWritesCounter.decrementAndGet();
                if (flushHandlersQueue == null ||
                        !flushHandlersQueue.nextBundle()) {
                        return;
                }
            }
            
            boolean hasNext;
            CompletionHandler<SpdyStream> handler;
            
            do {
                synchronized (flushHandlersSync) {
                    handler = flushHandlersQueue.next();
                    hasNext = flushHandlersQueue.hasNext();
                }
                
                try {
                    handler.completed(spdyStream);
                } catch (Exception ignored) {
                }
            } while (hasNext);
        }
    }
}
