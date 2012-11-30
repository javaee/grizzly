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
import org.glassfish.grizzly.WriteResult;
import org.glassfish.grizzly.asyncqueue.TaskQueue;
import org.glassfish.grizzly.http.HttpContent;
import org.glassfish.grizzly.http.HttpHeader;
import org.glassfish.grizzly.http.HttpPacket;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.http.HttpResponsePacket;
import org.glassfish.grizzly.memory.Buffers;
import org.glassfish.grizzly.spdy.frames.DataFrame;
import org.glassfish.grizzly.spdy.frames.SpdyFrame;
import org.glassfish.grizzly.spdy.frames.SynReplyFrame;
import org.glassfish.grizzly.spdy.frames.SynStreamFrame;
import org.glassfish.grizzly.spdy.frames.WindowUpdateFrame;


/**
 * Class represents an output sink associated with specific {@link SpdyStream}. 
 * 
 * The implementation is aligned with SPDY/3 requirements with regards to message
 * flow control.
 * 
 * @author Alexey Stashok
 */
final class SpdyOutputSink {
    private static final OutputQueueRecord TERMINATING_QUEUE_RECORD =
            new OutputQueueRecord(null, null, true);
    
    // current output queue size
    private final AtomicInteger outputQueueSize = new AtomicInteger();
    final TaskQueue<OutputQueueRecord> outputQueue =
            TaskQueue.createTaskQueue();
    // current output record
    //private final AtomicReference<OutputQueueRecord> currentQueueRecord =
    //        new AtomicReference<OutputQueueRecord>();
    // output records queue
    //private final ConcurrentLinkedQueue<OutputQueueRecord> outputQueue =
    //        new ConcurrentLinkedQueue<OutputQueueRecord>();
    
    // number of sent bytes, which are still unconfirmed by server via (WINDOW_UPDATE) message
    private final AtomicInteger unconfirmedBytes = new AtomicInteger();

    // true, if last output frame has been queued
    private volatile boolean isLastFrameQueued;
    // true, if last output frame has been sent or forcibly terminated
    private boolean isTerminated;
    
    // associated spdy session
    private final SpdySession spdySession;
    // associated spdy stream
    private final SpdyStream spdyStream;

    SpdyOutputSink(final SpdyStream spdyStream) {
        this.spdyStream = spdyStream;
        spdySession = spdyStream.getSpdySession();
    }

    /**
     * The method is called by Spdy Filter once WINDOW_UPDATE message comes from the peer.
     * 
     * @param delta the delta.
     */
    public void onPeerWindowUpdate(final int delta) {
        // update the unconfirmed bytes counter
        final int unconfirmedBytesNow = unconfirmedBytes.addAndGet(-delta);
        
        // get the current peer's window size limit
        final int windowSizeLimit = spdySession.getPeerInitialWindowSize();
        
        // try to write until window limit allows
        while (unconfirmedBytesNow < (windowSizeLimit * 3 / 4) &&
                outputQueueSize.get() > 0) {
            
            // pick up the first output record in the queue
            OutputQueueRecord outputQueueRecord =
                    outputQueue.poll();

            // if there is nothing to write - return
            if (outputQueueRecord == null) {
                return;
            }
            
            // if it's terminating record - processFin
            if (outputQueueRecord == TERMINATING_QUEUE_RECORD) {
                writeEmptyFin();
                return;
            }
            
            CompletionHandler<WriteResult> completionHandler =
                    outputQueueRecord.completionHandler;
            boolean isLast = outputQueueRecord.isLast;
            
            // check if output record's buffer is fitting into window size
            // if not - split it into 2 parts: part to send, part to keep in the queue
            final Buffer dataChunkToStore = checkOutputWindow(outputQueueRecord.buffer);
            final Buffer dataChunkToSend = outputQueueRecord.buffer;

            outputQueueRecord = null;
            
            // if there is a chunk to store
            if (dataChunkToStore != null && dataChunkToStore.hasRemaining()) {
                // Create output record for the chunk to be stored
                outputQueueRecord =
                        new OutputQueueRecord(dataChunkToStore,
                        completionHandler, isLast);
                
                // reset completion handler and isLast for the current chunk
                completionHandler = null;
                isLast = false;
            }

            // if there is a chunk to sent
            if (dataChunkToSend != null &&
                    (dataChunkToSend.hasRemaining() || isLast)) {
                // update unconfirmed bytes counter
                unconfirmedBytes.addAndGet(dataChunkToSend.remaining());

                DataFrame dataFrame = new DataFrame();
                dataFrame.setData(dataChunkToSend);
                dataFrame.setLast(isLast);
                dataFrame.setStreamId(spdyStream.getStreamId());

                // send a spdydata frame
                writeDownStream(dataFrame, completionHandler, isLast);
            }
            
            if (outputQueueRecord != null) {
                // if there is a chunk to be stored - store it and return
                outputQueue.setCurrentElement(outputQueueRecord);
                break;
            } else {
                // if current record has been sent - try to take the next one for output queue
                outputQueueSize.decrementAndGet();
            }
        }
    }
    
    /**
     * Send an {@link HttpPacket} to the {@link SpdyStream}.
     * 
     * @param httpPacket {@link HttpPacket} to send
     * @throws IOException 
     */
    public synchronized void writeDownStream(final HttpPacket httpPacket)
            throws IOException {
        writeDownStream(httpPacket, null);
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
            CompletionHandler<WriteResult> completionHandler)
            throws IOException {

        // if the last frame (fin flag == 1) has been queued already - throw an IOException
        if (isTerminated) {
            throw new IOException("The output stream has been terminated");
        }
        
        final HttpHeader httpHeader = httpPacket.getHttpHeader();
        final HttpContent httpContent = HttpContent.isContent(httpPacket) ? (HttpContent) httpPacket : null;
        
        SpdyFrame spdyFrame;
        
        // If HTTP header hasn't been commited - commit it
        if (!httpHeader.isCommitted()) {
            // do we expect any HTTP payload?
            final boolean isNoContent = !httpHeader.isExpectContent() ||
                    (httpContent != null && httpContent.isLast() &&
                    !httpContent.getContent().hasRemaining());
            
            // encode HTTP packet header
            if (!httpHeader.isRequest()) {
                SynReplyFrame synReplyFrame = new SynReplyFrame();
                synReplyFrame.setStreamId(spdyStream.getStreamId());
                synReplyFrame.setLast(isNoContent);
                synchronized (spdySession) { // TODO This sync point should be revisited for a more optimal solution.
                    synReplyFrame.setCompressedHeaders(
                            SpdyEncoderUtils.encodeSynReplyHeaders(spdySession,
                                                                   (HttpResponsePacket) httpHeader));

                }
                spdyFrame = synReplyFrame;
            } else {
                SynStreamFrame synStreamFrame = new SynStreamFrame();
                synStreamFrame.setStreamId(spdyStream.getStreamId());
                synStreamFrame.setAssociatedToStreamId(spdyStream.getAssociatedToStreamId());
                synStreamFrame.setLast(isNoContent);
                synchronized (spdySession) {
                    synStreamFrame.setCompressedHeaders(
                            SpdyEncoderUtils.encodeSynStreamHeaders(spdySession,
                                                                   (HttpRequestPacket) httpHeader));
                }
                spdyFrame = synStreamFrame;
            }

            httpHeader.setCommitted(true);

            writeDownStream(spdyFrame, completionHandler, isNoContent);
            if (isNoContent) {
                // if we don't expect any HTTP payload, mark this frame as
                // last and return
                return;
            }
        }
        
        OutputQueueRecord outputQueueRecord = null;
        boolean isLast;
        
        // if there is a payload to send now
        if (httpContent != null) {
            
            isLast = httpContent.isLast();
            final Buffer data = httpContent.getContent();
            
            // Check if output queue is not empty - add new element
            if (outputQueueSize.getAndIncrement() > 0) {
                outputQueueRecord = new OutputQueueRecord(
                        data, completionHandler, isLast);
                outputQueue.offer(outputQueueRecord);
                
                // check if our element wasn't forgotten (async)
                if (outputQueueSize.get() != 1 ||
                        !outputQueue.remove(outputQueueRecord)) {
                    // if not - return
                    return;
                }
            }
            
            // our element is first in the output queue
            
            
            // check if output record's buffer is fitting into window size
            // if not - split it into 2 parts: part to send, part to keep in the queue
            Buffer dataChunkToStore = checkOutputWindow(data);

            // if there is a chunk to store
            if (dataChunkToStore != null && dataChunkToStore.hasRemaining()) {
                // Create output record for the chunk to be stored
                outputQueueRecord =
                        new OutputQueueRecord(dataChunkToStore, completionHandler, isLast);
                // reset completion handler and isLast for the current chunk
                completionHandler = null;
                isLast = false;
            }
            
            // if there is a chunk to send
            if (data != null &&
                    (data.hasRemaining() || isLast)) {
                // update unconfirmed bytes counter
                unconfirmedBytes.addAndGet(data.remaining());

                // encode spdydata frame
                DataFrame dataFrame = new DataFrame();
                dataFrame.setStreamId(spdyStream.getStreamId());
                dataFrame.setData(data);
                dataFrame.setLast(isLast);
                writeDownStream(dataFrame, completionHandler, isLast);
            }
        }

//        // append header and payload buffers
//        final Buffer resultBuffer = Buffers.appendBuffers(memoryManager,
//                                     headerBuffer, contentBuffer, true);
//
//        if (resultBuffer != null) {
//            // if the result buffer != null
//            if (resultBuffer.isComposite()) {
//                ((CompositeBuffer) resultBuffer).disposeOrder(
//                        CompositeBuffer.DisposeOrder.LAST_TO_FIRST);
//            }
//
//            // send the buffer
//            writeDownStream(resultBuffer, completionHandler, isLast);
//        }

        if (outputQueueRecord == null) {
            // if we managed to send entire HttpPacket - decrease the counter
            //if (contentBuffer != null) {
                outputQueueSize.decrementAndGet();
            //}
            
            return;
        }
        
        do { // Make sure current outputQueueRecord is not forgotten
            
            // set the outputQueueRecord as the current
            outputQueue.setCurrentElement(outputQueueRecord);

            // check if situation hasn't changed and we can't send the data chunk now
            if (unconfirmedBytes.get() == 0 && outputQueueSize.get() == 1 &&
                    outputQueue.compareAndSetCurrentElement(outputQueueRecord, null)) {
                
                // if we can send the output record now - do that
                
                completionHandler = outputQueueRecord.completionHandler;
                isLast = outputQueueRecord.isLast;
                
                final Buffer dataChunkToStore = checkOutputWindow(outputQueueRecord.buffer);
                final Buffer dataChunkToSend = outputQueueRecord.buffer;
                
                outputQueueRecord = null;

                // if there is a chunk to store
                if (dataChunkToStore != null && dataChunkToStore.hasRemaining()) {
                    // Create output record for the chunk to be stored
                    outputQueueRecord =
                            new OutputQueueRecord(dataChunkToStore, completionHandler, isLast);
                    // reset completion handler and isLast for the current chunk
                    completionHandler = null;
                    isLast = false;
                }

                // if there is a chunk to send
                if (dataChunkToSend != null &&
                        (dataChunkToSend.hasRemaining() || isLast)) {
                        // update unconfirmed bytes counter
                        unconfirmedBytes.addAndGet(dataChunkToSend.remaining());

                    // encode spdydata frame
                    DataFrame frame = new DataFrame();
                    frame.setStreamId(spdyStream.getStreamId());
                    frame.setData(dataChunkToSend);
                    frame.setLast(isLast);
                    writeDownStream(frame, completionHandler, isLast);
                }
                
                if (outputQueueRecord == null) {
                    // if we managed to send entire HttpPacket - decrease the counter
                    outputQueueSize.decrementAndGet();
                }
            } else {
                break; // will be (or already) written asynchronously
            }
        } while (outputQueueRecord != null);
    }
    
    /**
     * The method is responsible for checking the current output window size
     * and split (if needed) current buffer into 2 chunked: the one to be sent
     * now and the one to be stored in the output queue and processed later.
     * 
     * @param data the output queue data.
     * 
     * @return the chunk to be stored, the passed Buffer will represent the chunk
     * to be sent.
     */
    private Buffer checkOutputWindow(final Buffer data) {
        final int size = data.remaining();
        
        // take a snapshot of the current output window state
        final int unconfirmedBytesNow = unconfirmedBytes.get();
        final int windowSizeLimit = spdySession.getPeerInitialWindowSize();

        Buffer dataChunkToStore = null;
        
        // Check if data chunk is overflowing the output window
        if (unconfirmedBytesNow + size > windowSizeLimit) { // Window overflowed
            final int dataSizeAllowedToSend = windowSizeLimit - unconfirmedBytesNow;

            // Split data chunk into 2 chunks - one to be sent now and one to be stored in the output queue

            dataChunkToStore =
                    data.split(data.position() + dataSizeAllowedToSend);
        }
        
        return dataChunkToStore;
    }
    
    void writeWindowUpdate(final int currentUnackedBytes) {
        WindowUpdateFrame frame = new WindowUpdateFrame();
        frame.setStreamId(spdyStream.getStreamId());
        frame.setDelta(currentUnackedBytes);
        writeDownStream0(frame, null);
    }
    
    private void writeDownStream(final SpdyFrame frame,
            final CompletionHandler<WriteResult> completionHandler,
            final boolean isLast) {
        writeDownStream0(frame, completionHandler);
        
        if (isLast) {
            terminate();
        }
    }
    
    private void writeDownStream0(final SpdyFrame frame,
            final CompletionHandler<WriteResult> completionHandler) {
        spdySession.getDownstreamChain().write(spdySession.getConnection(),
                null, frame, completionHandler, null);
    }
    
    synchronized void close() {
        if (!isLastFrameQueued && !isTerminated) {
            isLastFrameQueued = true;
            
            if (outputQueueSize.getAndIncrement() == 0) {
                writeEmptyFin();
                return;
            }
            
            outputQueue.offer(TERMINATING_QUEUE_RECORD);
            
            if (outputQueueSize.get() == 1 &&
                    outputQueue.remove(TERMINATING_QUEUE_RECORD)) {
                writeEmptyFin();
            }
        }
    }

    synchronized void terminate() {
        if (!isTerminated) {
            isTerminated = true;
            // NOTIFY STREAM
            spdyStream.onOutputClosed();
        }
    }
    
    boolean isTerminated() {
        return isTerminated;
    }
    
    private void writeEmptyFin() {
        if (!isTerminated) {
            // SEND LAST
            DataFrame dataFrame = new DataFrame();
            dataFrame.setStreamId(spdyStream.getStreamId());
            dataFrame.setData(Buffers.EMPTY_BUFFER);
            dataFrame.setLast(true);
            writeDownStream0(dataFrame, null);

            terminate();
        }
    }
    
    private static class OutputQueueRecord {
        private final Buffer buffer;
        private final CompletionHandler<WriteResult> completionHandler;
        private final boolean isLast;
        
        public OutputQueueRecord(final Buffer buffer,
                final CompletionHandler<WriteResult> completionHandler,
                final boolean isLast) {
            this.buffer = buffer;
            this.completionHandler = completionHandler;
            this.isLast = isLast;
        }
    }    
}
