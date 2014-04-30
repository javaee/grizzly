/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2014 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.spdy.v31;

import org.glassfish.grizzly.spdy.*;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.CompletionHandler;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.WriteHandler;
import org.glassfish.grizzly.WriteResult;
import org.glassfish.grizzly.asyncqueue.AsyncQueueRecord;
import org.glassfish.grizzly.asyncqueue.MessageCloner;
import org.glassfish.grizzly.asyncqueue.TaskQueue;
import org.glassfish.grizzly.spdy.frames.DataFrame;
import org.glassfish.grizzly.spdy.frames.SpdyFrame;

import static org.glassfish.grizzly.spdy.Constants.*;
import org.glassfish.grizzly.spdy.frames.WindowUpdateFrame;
import org.glassfish.grizzly.spdy.utils.ChunkedCompletionHandler;

/**
 * Class represents an output sink associated with specific {@link SpdySession}. 
 * 
 * The implementation is aligned with SPDY/3.1 requirements with regards to message
 * flow control.
 * 
 * @author Alexey Stashok
 */
final class SessionOutputSink31 extends SessionOutputSink {
    private static final Logger LOGGER = Grizzly.logger(SessionOutputSink31.class);
    private static final Level LOGGER_LEVEL = Level.FINE;

    private static final int MAX_OUTPUT_QUEUE_SIZE = 65536;
    
    // async output queue
    final TaskQueue<OutputQueueRecord> outputQueue =
            TaskQueue.createTaskQueue(new TaskQueue.MutableMaxQueueSize() {

        @Override
        public int getMaxQueueSize() {
            return MAX_OUTPUT_QUEUE_SIZE;
        }
    });
 
    private final AtomicInteger availConnectionWindowSize =
            new AtomicInteger(DEFAULT_INITIAL_WINDOW_SIZE);
    private final List<SpdyFrame> tmpFramesList = new LinkedList<SpdyFrame>();
    private final AtomicBoolean writerLock = new AtomicBoolean();

    // number of bytes reported to be read, but still unacked to the peer
    private final AtomicInteger unackedReadBytes  = new AtomicInteger();

    
    public SessionOutputSink31(final SpdySession session) {
        super(session);
    }
    
    
    @Override
    protected int getAvailablePeerConnectionWindowSize() {
        return availConnectionWindowSize.get();
    }
    
    @Override
    protected boolean canWrite() {
        return outputQueue.size() < MAX_OUTPUT_QUEUE_SIZE;
    }
    
    @Override
    protected void notifyCanWrite(final WriteHandler writeHandler) {
        outputQueue.notifyWritePossible(writeHandler, MAX_OUTPUT_QUEUE_SIZE);
    }
    
    @Override
    protected void onPeerWindowUpdate(final int delta) {
        // @TODO check overflow
        final int newWindowSize = availConnectionWindowSize.addAndGet(delta);
        if (LOGGER.isLoggable(LOGGER_LEVEL)) {
            LOGGER.log(LOGGER_LEVEL, "SpdySession. Expand connection window size by {0} bytes. Current connection window size is: {1}",
                    new Object[] {delta, newWindowSize});
        }
        
        flushOutputQueue();
    }
    
    void sendWindowUpdate(final int delta) {
        final int currentUnackedBytes
                = unackedReadBytes.addAndGet(delta);
        final int windowSize = session.getLocalConnectionWindowSize();

        // if not forced - send update window message only in case currentUnackedBytes > windowSize / 2
        if (currentUnackedBytes > (windowSize / 3)
                && unackedReadBytes.compareAndSet(currentUnackedBytes, 0)) {

            writeDownStream(
                    WindowUpdateFrame.builder()
                    .streamId(0)
                    .delta(currentUnackedBytes)
                    .build(),
                    null);
        }
    }
    
    @Override
    protected void writeDataDownStream(final SpdyStream spdyStream,
            final SpdyFrame headerFrame,
            Buffer data,
            final CompletionHandler<WriteResult> completionHandler,
            final MessageCloner<Buffer> messageCloner,
            final boolean isLast) {
        
        if (data == null ||
                (!data.hasRemaining() && spdyStream.getUnflushedWritesCount() == 1)) {
            // if there's no data - write now.
            // if there's zero-data and it's the only pending write for the stream - write now.
            super.writeDataDownStream(spdyStream, headerFrame, data,
                    completionHandler, messageCloner, isLast);
            
            return;
        } else if (headerFrame != null) {
            // flush the headers now in this thread,
            // because we have to keep compression state consistent
            writeDownStream(headerFrame);
        }
        
        final int dataSize = data.remaining();
        
        if (messageCloner != null) {
            data = messageCloner.clone(session.getConnection(), data);
        }
        
        final OutputQueueRecord record = new OutputQueueRecord(
                spdyStream.getStreamId(), data,
                completionHandler, isLast);
        
        outputQueue.offer(record);
        outputQueue.reserveSpace(record.isZeroSizeData() ? 1 : dataSize);
        
        flushOutputQueue();
    }

    private void flushOutputQueue() {        
        int backoffDelay = 0;
        
        int availWindowSize;
        int queueSize;
        
        boolean needToNotify = false;
        
        // for debug purposes only
        int tmpcnt = 0;
        
        // try to flush entire output queue
        
        // relaxed check if we have free window space and output queue is not empty 
        // if yes - lock the writer (only one thread can flush)
        while (availConnectionWindowSize.get() > 0
                && !outputQueue.isEmpty()
                && writerLock.compareAndSet(false, true)) {

            // get the values after the writer is locked
            availWindowSize = availConnectionWindowSize.get();
            queueSize = outputQueue.size();
            
            CompletionHandler<WriteResult> writeCompletionHandler = null;
            int writeCompletionHandlerBytes = 0;
            
            int bytesToTransfer = 0;
            int queueSizeToFree = 0;
            
            AggrCompletionHandler completionHandlers = null;
            
            // gather all available output data frames
            while (availWindowSize > bytesToTransfer &&
                    queueSize > queueSizeToFree) {

                final OutputQueueRecord record = outputQueue.poll();
                
                if (record == null) {
                    // keep this warning for now
                    // should be reported when null record is spotted
                    LOGGER.log(Level.WARNING, "UNEXPECTED NULL RECORD. Queue-size: {0} "
                            + "tmpcnt={1} byteToTransfer={2} queueSizeToFree={3} queueSize={4}",
                            new Object[]{outputQueue.size(), tmpcnt, bytesToTransfer, queueSizeToFree, queueSize});
                }
                
                assert record != null;

                final int serializedBytes = record.serializeTo(
                        tmpFramesList, availWindowSize - bytesToTransfer);
                bytesToTransfer += serializedBytes;
                queueSizeToFree += serializedBytes;

                if (record.isFinished()) {
                    if (record.isZeroSizeData()) {
                        queueSizeToFree++;
                    }
                } else {
                    outputQueue.setCurrentElement(record);
                }
                
                final CompletionHandler<WriteResult> recordCompletionHandler =
                        record.getCompletionHandler();
                
                // add this record CompletionHandler to the list of 
                // CompletionHandlers to be notified once all the frames are
                // written
                if (recordCompletionHandler != null) {
                    if (completionHandlers != null) {
                        completionHandlers.register(recordCompletionHandler,
                                serializedBytes);
                    } else if (writeCompletionHandler == null) {
                        writeCompletionHandler = recordCompletionHandler;
                        writeCompletionHandlerBytes = serializedBytes;
                    } else {
                        completionHandlers = new AggrCompletionHandler();
                        completionHandlers.register(writeCompletionHandler,
                                writeCompletionHandlerBytes);
                        completionHandlers.register(recordCompletionHandler,
                                serializedBytes);
                        writeCompletionHandler = completionHandlers;
                    }
                }
            }
            
            // if at least one byte was consumed from the output queue
            if (queueSizeToFree > 0) {
                assert !tmpFramesList.isEmpty();
                
                // write the frame list
                writeDownStream(tmpFramesList, writeCompletionHandler, null);

                final int newWindowSize =
                        availConnectionWindowSize.addAndGet(-bytesToTransfer);

                outputQueue.releaseSpace(queueSizeToFree);
                
                needToNotify = true;
                if (LOGGER.isLoggable(LOGGER_LEVEL)) {
                    LOGGER.log(LOGGER_LEVEL, "SpdySession. Shrink connection window size by {0} bytes. Current connection window size is: {1}",
                            new Object[] {bytesToTransfer, newWindowSize});
                }

            }
            
            // release the writer lock, so other thread can start to write
            writerLock.set(false);
            
            // we don't want this thread to write all the time - so give more
            // time for another thread to start writing
            LockSupport.parkNanos(backoffDelay++);
            tmpcnt++;
        }

        if (needToNotify) {
            outputQueue.doNotify();
        }
    }

    @Override
    public void close() {
        outputQueue.onClose();
    }
    
    private static class OutputQueueRecord extends AsyncQueueRecord<WriteResult> {
        private int streamId;
        
        private ChunkedCompletionHandler chunkedCompletionHandler;
        private CompletionHandler<WriteResult> originalCompletionHandler;
        private Buffer buffer;
        private boolean isLast;
        
        private boolean isZeroSizeData;
        
        public OutputQueueRecord(final int streamId,
                final Buffer buffer,
                final CompletionHandler<WriteResult> completionHandler,
                final boolean isLast) {
            super(null, null, null);
            
            this.streamId = streamId;
            this.buffer = buffer;
            this.isZeroSizeData = !buffer.hasRemaining();
            this.originalCompletionHandler = completionHandler;
            this.isLast = isLast;
        }

        public CompletionHandler<WriteResult> getCompletionHandler() {
            return chunkedCompletionHandler != null ?
                    chunkedCompletionHandler :
                    originalCompletionHandler;
        }

        @Override
        public void notifyFailure(final Throwable e) {
            final CompletionHandler<WriteResult> chLocal = getCompletionHandler();
            if (chLocal != null) {
                chLocal.failed(e);
            }
        }
        
        @Override
        public void recycle() {
        }

        @Override
        public WriteResult getCurrentResult() {
            return null;
        }

        private boolean isZeroSizeData() {
            return isZeroSizeData;
        }
        
        private boolean isFinished() {
            return buffer == null;
        }
        
        private int serializeTo(final List<SpdyFrame> frames,
                final int maxDataSize) {
            
            final int recordSize = buffer.remaining();

            if (recordSize <= maxDataSize) {
                final DataFrame dataFrame = DataFrame.builder()
                        .streamId(streamId)
                        .data(buffer).last(isLast)
                        .build();

                frames.add(dataFrame);
                
                buffer = null;
                
                return recordSize;
            } else {
                if (originalCompletionHandler != null && chunkedCompletionHandler == null) {
                    chunkedCompletionHandler = new ChunkedCompletionHandler(originalCompletionHandler);
                }
                
                if (chunkedCompletionHandler != null) {
                    chunkedCompletionHandler.incChunks();
                }
                
                final Buffer remainder = buffer.split(buffer.position() + maxDataSize);
                
                final DataFrame dataFrame = DataFrame.builder()
                        .streamId(streamId)
                        .data(buffer).last(false)
                        .build();

                frames.add(dataFrame);
                
                buffer = remainder;
                
                return maxDataSize;
            }
        }
    }    
}
