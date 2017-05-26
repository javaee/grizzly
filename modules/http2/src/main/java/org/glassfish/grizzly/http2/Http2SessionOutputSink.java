/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2014-2017 Oracle and/or its affiliates. All rights reserved.
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
import org.glassfish.grizzly.http2.frames.DataFrame;
import org.glassfish.grizzly.http2.frames.ErrorCode;
import org.glassfish.grizzly.http2.frames.Http2Frame;
import org.glassfish.grizzly.http2.utils.ChunkedCompletionHandler;

/**
 * Class represents an output sink associated with specific {@link Http2Session}
 * and is responsible for session (connection) level flow control.
 * 
 * @author Alexey Stashok
 */
public class Http2SessionOutputSink {
    protected final Http2Session http2Session;
    private static final Logger LOGGER = Grizzly.logger(Http2SessionOutputSink.class);
    private static final Level LOGGER_LEVEL = Level.FINE;

    private static final int MAX_FRAME_PAYLOAD_SIZE = 16383;
    private static final int MAX_OUTPUT_QUEUE_SIZE = 65536;

    // async output queue
    private final TaskQueue<Http2SessionOutputSink.OutputQueueRecord> outputQueue =
            TaskQueue.createTaskQueue(new TaskQueue.MutableMaxQueueSize() {

                @Override
                public int getMaxQueueSize() {
                    return MAX_OUTPUT_QUEUE_SIZE;
                }
            });

    private final AtomicInteger availConnectionWindowSize;
    private final List<Http2Frame> tmpFramesList = new LinkedList<>();
    private final AtomicBoolean writerLock = new AtomicBoolean();

    public Http2SessionOutputSink(Http2Session session) {
        this.http2Session = session;
        availConnectionWindowSize = new AtomicInteger(
                http2Session.getDefaultConnectionWindowSize());
    }

    protected Http2FrameCodec frameCodec() {
        return http2Session.handlerFilter.frameCodec;
    }
    
    protected void writeDownStream(final Http2Frame frame) {
        
        http2Session.getHttp2SessionChain().write(
                http2Session.getConnection(), null,
                frameCodec().serializeAndRecycle(http2Session, frame),
                null, (MessageCloner) null);
    }

    protected void writeDownStream(final List<Http2Frame> frames) {
        
        http2Session.getHttp2SessionChain().write(
                http2Session.getConnection(), null,
                frameCodec().serializeAndRecycle(http2Session, frames),
                null, (MessageCloner) null);
    }
    
    @SuppressWarnings("unchecked")
    protected <K> void writeDownStream(final K anyMessage,
            final CompletionHandler<WriteResult> completionHandler,
            final MessageCloner<Buffer> messageCloner) {
        
        // Encode Http2Frame -> Buffer
        final Object msg;
        if (anyMessage instanceof List) {
            msg = frameCodec().serializeAndRecycle(
                    http2Session, (List<Http2Frame>) anyMessage);
        } else if (anyMessage instanceof Http2Frame) {
            msg = frameCodec().serializeAndRecycle(
                    http2Session, (Http2Frame) anyMessage);
        } else {
            msg = anyMessage;
        }
        
        http2Session.getHttp2SessionChain().write(http2Session.getConnection(),
                null, msg, completionHandler, messageCloner);        
    }

    protected int getAvailablePeerConnectionWindowSize() {
        return availConnectionWindowSize.get();
    }

    protected boolean canWrite() {
        return outputQueue.size() < MAX_OUTPUT_QUEUE_SIZE;
    }

    protected void notifyCanWrite(final WriteHandler writeHandler) {
        outputQueue.notifyWritePossible(writeHandler, MAX_OUTPUT_QUEUE_SIZE);
    }

    protected void onPeerWindowUpdate(final int delta) throws Http2SessionException {
        final int currentWindow = availConnectionWindowSize.get();
        if (delta > 0 && currentWindow > 0 && currentWindow + delta < 0) {
            throw new Http2SessionException(ErrorCode.FLOW_CONTROL_ERROR, "Session flow-control window overflow.");
        }
        final int newWindowSize = availConnectionWindowSize.addAndGet(delta);
        if (LOGGER.isLoggable(LOGGER_LEVEL)) {
            LOGGER.log(LOGGER_LEVEL, "Http2Session. Expand connection window size by {0} bytes. Current connection window size is: {1}",
                    new Object[] {delta, newWindowSize});
        }

        flushOutputQueue();
    }

    protected void writeDataDownStream(final Http2Stream stream,
                                       final List<Http2Frame> headerFrames,
                                       Buffer data,
                                       final CompletionHandler<WriteResult> completionHandler,
                                       final MessageCloner<Buffer> messageCloner,
                                       final boolean isLast) {

        if (data == null ||
                (!data.hasRemaining() && stream.getUnflushedWritesCount() == 1)) {
            // if there's no data - write now.
            // if there's zero-data and it's the only pending write for the stream - write now.
            if (data == null) {
                writeDownStream(headerFrames, completionHandler, messageCloner);
                return;
            }

            final DataFrame dataFrame = DataFrame.builder()
                    .streamId(stream.getId())
                    .data(data).endStream(isLast)
                    .build();

            final Object msg;
            if (headerFrames != null && !headerFrames.isEmpty()) {
                headerFrames.add(dataFrame);
                msg = headerFrames;
            } else {
                msg = dataFrame;
            }

            writeDownStream(msg, completionHandler, messageCloner);

            return;
        } else if (headerFrames != null && !headerFrames.isEmpty()) {
            // flush the headers now in this thread,
            // because we have to keep compression state consistent
            writeDownStream(headerFrames);
        }

        final int dataSize = data.remaining();

        if (messageCloner != null) {
            data = messageCloner.clone(http2Session.getConnection(), data);
        }

        final Http2SessionOutputSink.OutputQueueRecord record = new Http2SessionOutputSink.OutputQueueRecord(
                stream.getId(), data,
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

                final Http2SessionOutputSink.OutputQueueRecord record = outputQueue.poll();

                if (record == null) {
                    // keep this warning for now
                    // should be reported when null record is spotted
                    LOGGER.log(Level.WARNING, "UNEXPECTED NULL RECORD. Queue-size: {0} "
                                    + "tmpcnt={1} byteToTransfer={2} queueSizeToFree={3} queueSize={4}",
                            new Object[]{outputQueue.size(), tmpcnt, bytesToTransfer, queueSizeToFree, queueSize});
                }

                assert record != null;

                final int serializedBytes = record.serializeTo(
                        tmpFramesList,
                        Math.min(MAX_FRAME_PAYLOAD_SIZE, availWindowSize - bytesToTransfer));
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
                    LOGGER.log(LOGGER_LEVEL, "Http2Session. Shrink connection window size by {0} bytes. Current connection window size is: {1}",
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

    public void close() {
        outputQueue.onClose();
    }

    private static class OutputQueueRecord extends AsyncQueueRecord<WriteResult> {
        private final int streamId;

        private ChunkedCompletionHandler chunkedCompletionHandler;
        private final CompletionHandler<WriteResult> originalCompletionHandler;
        private Buffer buffer;
        private final boolean isLast;

        private final boolean isZeroSizeData;

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

        private int serializeTo(final List<Http2Frame> frames,
                                final int maxDataSize) {

            final int recordSize = buffer.remaining();

            if (recordSize <= maxDataSize) {
                final DataFrame dataFrame = DataFrame.builder()
                        .streamId(streamId)
                        .data(buffer).endStream(isLast)
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
                        .data(buffer).endStream(false)
                        .build();

                frames.add(dataFrame);

                buffer = remainder;

                return maxDataSize;
            }
        }
    }
}
