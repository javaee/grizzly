/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.glassfish.grizzly.spdy;

import java.io.IOException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
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
    private final AtomicInteger outputQueueSize = new AtomicInteger();
    private final AtomicReference<OutputQueueRecord> currentQueueRecord =
            new AtomicReference<OutputQueueRecord>();
    private final ConcurrentLinkedQueue<OutputQueueRecord> outputQueue =
            new ConcurrentLinkedQueue<OutputQueueRecord>();
    
    private final AtomicInteger unconfirmedBytes = new AtomicInteger();

    private final AtomicBoolean isOutputClosed = new AtomicBoolean();
    
    private final SpdySession spdySession;
    private final SpdyStream spdyStream;

    SpdyOutputSink(final SpdyStream spdyStream) {
        this.spdyStream = spdyStream;
        spdySession = spdyStream.getSpdySession();
    }
    
    
    public void onPeerWindowUpdate(final int delta) {
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
            
            CompletionHandler<WriteResult> completionHandler =
                    outputQueueRecord.completionHandler;
            boolean isLast = outputQueueRecord.isLast;
            
            final Buffer dataChunkToStore = checkOutputWindow(outputQueueRecord.buffer);
            final Buffer dataChunkToSend = outputQueueRecord.buffer;

            outputQueueRecord = null;
            
            if (dataChunkToStore != null) {
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

                writeDownStream0(contentBuffer, completionHandler);
            }
            
            if (outputQueueRecord != null) {
                currentQueueRecord.set(outputQueueRecord);
            }
        }
    }
    
    public void writeDownStream(final HttpPacket httpPacket) throws IOException {
        writeDownStream(httpPacket, null);
    }
    
    public void writeDownStream(final HttpPacket httpPacket,
            CompletionHandler<WriteResult> completionHandler)
            throws IOException {

        
        final MemoryManager memoryManager = spdySession.getMemoryManager();
        final HttpHeader httpHeader = httpPacket.getHttpHeader();
        final HttpContent httpContent = HttpContent.isContent(httpPacket) ? (HttpContent) httpPacket : null;
        
        
        Buffer headerBuffer = null;
        
        if (!httpHeader.isCommitted()) {
            final boolean isLast = !httpHeader.isExpectContent() ||
                    (httpContent != null && httpContent.isLast() &&
                    !httpContent.getContent().hasRemaining());
            
            if (!httpHeader.isRequest()) {
                headerBuffer = encodeSynReply(
                        memoryManager,
                        (SpdyResponse) httpHeader, isLast);
            } else {
                throw new IllegalStateException("Not implemented yet");
            }

            httpHeader.setCommitted(true);
            
            if (isLast) {
                writeDownStream0(headerBuffer, completionHandler);
                return;
            }
        }
        
        OutputQueueRecord outputQueueRecord = null;
        Buffer contentBuffer = null;
        if (httpContent != null) {
            
            boolean isLast = httpContent.isLast();
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

            if (dataChunkToStore != null) {
                // Create output record for the chunk to be stored
                outputQueueRecord =
                        new OutputQueueRecord(dataChunkToStore, completionHandler, isLast);
                completionHandler = null;
                isLast = false;
            }
            
            
            if (dataChunkToSend != null && dataChunkToSend.hasRemaining()) {
                unconfirmedBytes.addAndGet(dataChunkToSend.remaining());
                
                contentBuffer = encodeSpdyData(memoryManager, spdyStream, dataChunkToSend,
                        isLast);
            }
        }

        final Buffer resultBuffer = Buffers.appendBuffers(memoryManager,
                                     headerBuffer, contentBuffer, true);

        if (resultBuffer.isComposite()) {
            ((CompositeBuffer) resultBuffer).disposeOrder(
                    CompositeBuffer.DisposeOrder.LAST_TO_FIRST);
        }
        
        writeDownStream0(resultBuffer, completionHandler);

        if (outputQueueRecord == null) {
            return;
        }
        
        do { // Make sure current outputQueueRecord is not forgotten
            currentQueueRecord.set(outputQueueRecord);

            if (unconfirmedBytes.get() == 0 && outputQueueSize.get() == 1 &&
                    currentQueueRecord.compareAndSet(outputQueueRecord, null)) {
                
                completionHandler = outputQueueRecord.completionHandler;
                boolean isLast = outputQueueRecord.isLast;
                
                final Buffer dataChunkToStore = checkOutputWindow(outputQueueRecord.buffer);
                final Buffer dataChunkToSend = outputQueueRecord.buffer;
                
                outputQueueRecord = null;
                
                if (dataChunkToStore != null) {
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
                    
                    writeDownStream0(contentBuffer, completionHandler);
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

            if (dataSizeAllowedToSend > 0) {
                dataChunkToStore =
                        data.split(data.position() + dataSizeAllowedToSend);
            } else {
                dataChunkToStore = data;
            }
        }
        
        return dataChunkToStore;
    }
    
    void writeDownStream0(final Buffer frame,
            final CompletionHandler<WriteResult> completionHandler) {
        spdyStream.downstreamContext.write(frame, completionHandler);
    }
    
    boolean close() {
        return isOutputClosed.compareAndSet(false, true);
    }
    
    boolean isClosed() {
        return isOutputClosed.get();
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
