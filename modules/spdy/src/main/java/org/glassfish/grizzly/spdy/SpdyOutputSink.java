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
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.CompletionHandler;
import org.glassfish.grizzly.WriteResult;
import org.glassfish.grizzly.http.HttpContent;
import org.glassfish.grizzly.http.HttpHeader;
import org.glassfish.grizzly.http.HttpPacket;
import org.glassfish.grizzly.memory.Buffers;
import org.glassfish.grizzly.memory.CompositeBuffer;
import org.glassfish.grizzly.memory.MemoryManager;

import static org.glassfish.grizzly.spdy.SpdyEncoderUtils.*;

/**
 *
 * @author oleksiys
 */
final class SpdyOutputSink {
    private static final OutputQueueRecord TERMINATING_QUEUE_RECORD =
            new OutputQueueRecord(null, null, true);
    
    private final AtomicInteger outputQueueSize = new AtomicInteger();
    private final AtomicReference<OutputQueueRecord> currentQueueRecord =
            new AtomicReference<OutputQueueRecord>();
    private final ConcurrentLinkedQueue<OutputQueueRecord> outputQueue =
            new ConcurrentLinkedQueue<OutputQueueRecord>();
    
    private final AtomicInteger unconfirmedBytes = new AtomicInteger();

    private volatile boolean isLastFrameQueued;
    private boolean isLastFrameSent;
    
    private final SpdySession spdySession;
    private final SpdyStream spdyStream;

    SpdyOutputSink(final SpdyStream spdyStream) {
        this.spdyStream = spdyStream;
        spdySession = spdyStream.getSpdySession();
    }
    
    int updateCounter = 0;
    public void onPeerWindowUpdate(final int delta) {
        updateCounter++;
        
        final int unconfirmedBytesNow = unconfirmedBytes.addAndGet(-delta);
        final int windowSizeLimit = spdySession.getPeerInitialWindowSize();
        
        while (unconfirmedBytesNow < (windowSizeLimit * 3 / 4) &&
                outputQueueSize.get() > 0) {
            OutputQueueRecord outputQueueRecord =
                    currentQueueRecord.getAndSet(null);
            if (outputQueueRecord == null) {
                outputQueueRecord = outputQueue.poll();
            }
            
            if (outputQueueRecord == null) {
                return;
            }
            
            if (outputQueueRecord == TERMINATING_QUEUE_RECORD) {
                processFin();
                return;
            }
            
            CompletionHandler<WriteResult> completionHandler =
                    outputQueueRecord.completionHandler;
            boolean isLast = outputQueueRecord.isLast;
            
            final Buffer dataChunkToStore = checkOutputWindow(outputQueueRecord.buffer);
            final Buffer dataChunkToSend = outputQueueRecord.buffer;

            outputQueueRecord = null;
            
            if (dataChunkToStore != null && dataChunkToStore.hasRemaining()) {
                // Create output record for the chunk to be stored
                outputQueueRecord =
                        new OutputQueueRecord(dataChunkToStore,
                        completionHandler, isLast);
                completionHandler = null;
                isLast = false;
            }

            if (dataChunkToSend != null && dataChunkToSend.hasRemaining()) {
                unconfirmedBytes.addAndGet(dataChunkToSend.remaining());

                final Buffer contentBuffer = encodeSpdyData(
                        spdySession.getMemoryManager(),
                        spdyStream, dataChunkToSend,
                        isLast);

                writeDownStream(contentBuffer, completionHandler, isLast);
            }
            
            if (outputQueueRecord != null) {
                currentQueueRecord.set(outputQueueRecord);
                break;
            } else {
                outputQueueSize.decrementAndGet();
            }
        }
    }
    
    public synchronized void writeDownStream(final HttpPacket httpPacket) throws IOException {
        writeDownStream(httpPacket, null);
    }
    
    int counter = 0;
    
    public synchronized void writeDownStream(final HttpPacket httpPacket,
            CompletionHandler<WriteResult> completionHandler)
            throws IOException {

        if (isLastFrameQueued) {
            throw new IOException("The last frame has been queued");
        }
        
        counter++;
        
        final MemoryManager memoryManager = spdySession.getMemoryManager();
        final HttpHeader httpHeader = httpPacket.getHttpHeader();
        final HttpContent httpContent = HttpContent.isContent(httpPacket) ? (HttpContent) httpPacket : null;
        
        Buffer headerBuffer = null;
        
        if (!httpHeader.isCommitted()) {
            final boolean isNoContent = !httpHeader.isExpectContent() ||
                    (httpContent != null && httpContent.isLast() &&
                    !httpContent.getContent().hasRemaining());
            
            if (!httpHeader.isRequest()) {
                headerBuffer = encodeSynReply(
                        memoryManager,
                        (SpdyResponse) httpHeader, isNoContent);
            } else {
                throw new IllegalStateException("Not implemented yet");
            }

            httpHeader.setCommitted(true);
            
            if (isNoContent) {
                writeDownStream(headerBuffer, completionHandler, true);
                return;
            }
        }
        
        OutputQueueRecord outputQueueRecord = null;
        Buffer contentBuffer = null;
        boolean isLast = false;
        
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
            
            Buffer dataChunkToStore = checkOutputWindow(data);
            Buffer dataChunkToSend = data;

            if (dataChunkToStore != null && dataChunkToStore.hasRemaining()) {
                // Create output record for the chunk to be stored
                outputQueueRecord =
                        new OutputQueueRecord(dataChunkToStore, completionHandler, isLast);
                completionHandler = null;
                isLast = false;
            }
            
            
            if (dataChunkToSend != null && dataChunkToSend.hasRemaining()) {
                unconfirmedBytes.addAndGet(dataChunkToSend.remaining());
                
                contentBuffer = encodeSpdyData(memoryManager, spdyStream,
                        dataChunkToSend, isLast);
            }
        }

        final Buffer resultBuffer = Buffers.appendBuffers(memoryManager,
                                     headerBuffer, contentBuffer, true);

        if (resultBuffer != null) {
            if (resultBuffer.isComposite()) {
                ((CompositeBuffer) resultBuffer).disposeOrder(
                        CompositeBuffer.DisposeOrder.LAST_TO_FIRST);
            }

            writeDownStream(resultBuffer, completionHandler, isLast);
        }

        if (outputQueueRecord == null) {
            if (contentBuffer != null) {
                outputQueueSize.decrementAndGet();
            }
            
            return;
        }
        
        do { // Make sure current outputQueueRecord is not forgotten
            currentQueueRecord.set(outputQueueRecord);

            if (unconfirmedBytes.get() == 0 && outputQueueSize.get() == 1 &&
                    currentQueueRecord.compareAndSet(outputQueueRecord, null)) {
                
                completionHandler = outputQueueRecord.completionHandler;
                isLast = outputQueueRecord.isLast;
                
                final Buffer dataChunkToStore = checkOutputWindow(outputQueueRecord.buffer);
                final Buffer dataChunkToSend = outputQueueRecord.buffer;
                
                outputQueueRecord = null;
                
                if (dataChunkToStore != null && dataChunkToStore.hasRemaining()) {
                    // Create output record for the chunk to be stored
                    outputQueueRecord =
                            new OutputQueueRecord(dataChunkToStore, completionHandler, isLast);
                    completionHandler = null;
                    isLast = false;
                }

                if (dataChunkToSend != null && dataChunkToSend.hasRemaining()) {
                    unconfirmedBytes.addAndGet(dataChunkToSend.remaining());

                    contentBuffer = encodeSpdyData(memoryManager, spdyStream,
                            dataChunkToSend, isLast);
                    
                    writeDownStream(contentBuffer, completionHandler, isLast);
                }
                
                if (outputQueueRecord == null) {
                    outputQueueSize.decrementAndGet();
                }
            } else {
                break; // will be (or already) written asynchronously
            }
        } while (outputQueueRecord != null);
        
    }
    
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
    
    void writeDownStream(final Buffer frame,
            final CompletionHandler<WriteResult> completionHandler,
            final boolean isLast) {
        writeDownStream0(frame, completionHandler);
        
        if (isLast) {
            isLastFrameSent = true;
            isLastFrameQueued = true;
            processFin();
        }
    }
    
    void writeDownStream0(final Buffer frame,
            final CompletionHandler<WriteResult> completionHandler) {
        spdyStream.downstreamContext.write(frame, completionHandler);
    }
    
    synchronized void shutdown() {
        if (!isLastFrameQueued) {
            isLastFrameQueued = true;
            
            if (outputQueueSize.getAndIncrement() == 0) {
                processFin();
                return;
            }
            
            outputQueue.add(TERMINATING_QUEUE_RECORD);
            
            if (outputQueueSize.get() == 1 &&
                    outputQueue.remove(TERMINATING_QUEUE_RECORD)) {
                processFin();
            }
        }
    }

    boolean isTerminated() {
        return isLastFrameQueued;
    }
    
    private void processFin() {
        if (!isLastFrameSent) {
            // SEND LAST
            
            isLastFrameSent = true;
            writeDownStream0(
                    encodeSpdyData(spdySession.getMemoryManager(), spdyStream,
                    Buffers.EMPTY_BUFFER, true),
                    null);
        }
        
        // NOTIFY STREAM
        spdyStream.onOutputClosed();
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
