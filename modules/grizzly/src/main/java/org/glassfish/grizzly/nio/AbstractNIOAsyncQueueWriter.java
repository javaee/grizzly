/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2010 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.nio;

import java.io.IOException;
import java.net.SocketAddress;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import org.glassfish.grizzly.AbstractWriter;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.CompletionHandler;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.GrizzlyFuture;
import org.glassfish.grizzly.Interceptor;
import org.glassfish.grizzly.WriteResult;
import org.glassfish.grizzly.asyncqueue.TaskQueue;
import org.glassfish.grizzly.asyncqueue.AsyncQueueWriter;
import org.glassfish.grizzly.asyncqueue.AsyncWriteQueueRecord;
import org.glassfish.grizzly.asyncqueue.MessageCloner;
import org.glassfish.grizzly.impl.FutureImpl;
import org.glassfish.grizzly.impl.ReadyFutureImpl;
import org.glassfish.grizzly.impl.SafeFutureImpl;

import java.util.Queue;
import java.util.concurrent.Future;
import java.util.logging.Level;

/**
 * The {@link AsyncQueueWriter} implementation, based on the Java NIO
 * 
 * @author Alexey Stashok
 * @author Ryan Lubke
 */
public abstract class AbstractNIOAsyncQueueWriter
        extends AbstractWriter<SocketAddress>
        implements AsyncQueueWriter<SocketAddress> {

    private final static Logger logger = Grizzly.logger(AbstractNIOAsyncQueueWriter.class);

    private static final AsyncWriteQueueRecord LOCK_RECORD =
            AsyncWriteQueueRecord.create(null, null, null, null, null, null,
            null, false);
    
    protected final NIOTransport transport;

    protected volatile int maxPendingBytes = Integer.MAX_VALUE;

    public AbstractNIOAsyncQueueWriter(NIOTransport transport) {
        this.transport = transport;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public boolean canWrite(Connection connection, int size) {
        final TaskQueue<AsyncWriteQueueRecord> connectionQueue =
                ((AbstractNIOConnection) connection).getAsyncWriteQueue();
        return connectionQueue.spaceInBytes() + size < maxPendingBytes;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public void setMaxPendingBytesPerConnection(final int maxPendingBytes) {
        this.maxPendingBytes = maxPendingBytes;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getMaxPendingBytesPerConnection() {
        return maxPendingBytes;
    }

    @Override
    public GrizzlyFuture<WriteResult<Buffer, SocketAddress>> write(
            Connection connection, SocketAddress dstAddress, Buffer buffer,
            CompletionHandler<WriteResult<Buffer, SocketAddress>> completionHandler,
            Interceptor<WriteResult<Buffer, SocketAddress>> interceptor)
            throws IOException {
        return write(connection, dstAddress, buffer, completionHandler,
                interceptor, null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public GrizzlyFuture<WriteResult<Buffer, SocketAddress>> write(
            Connection connection, SocketAddress dstAddress, Buffer buffer,
            CompletionHandler<WriteResult<Buffer, SocketAddress>> completionHandler,
            Interceptor<WriteResult<Buffer, SocketAddress>> interceptor,
            MessageCloner<Buffer> cloner) throws IOException {
        
        final boolean isLogFine = logger.isLoggable(Level.FINEST);
        
        if (connection == null) {
            throw new IOException("Connection is null");
        } else if (!connection.isOpen()) {
            throw new IOException("Connection is closed");
        }

        // Get connection async write queue
        final TaskQueue<AsyncWriteQueueRecord> connectionQueue =
                ((AbstractNIOConnection) connection).getAsyncWriteQueue();


        final WriteResult currentResult = WriteResult.create(connection,
                buffer, dstAddress, 0);
        
        // create and initialize the write queue record
        final AsyncWriteQueueRecord queueRecord = createRecord(
                buffer, null, currentResult, completionHandler,
                interceptor, dstAddress, buffer, false);

        final Queue<AsyncWriteQueueRecord> queue = connectionQueue.getQueue();
        final AtomicReference<AsyncWriteQueueRecord> currentElement =
                connectionQueue.getCurrentElementAtomic();

        final int bufferSize = buffer.remaining();
        final int pendingBytes = connectionQueue.reserveSpace(bufferSize);
        
        final boolean isLocked = currentElement.compareAndSet(null, LOCK_RECORD);
        if (isLogFine) {
            logger.log(Level.FINEST, "AsyncQueueWriter.write connection={0} record={1} directWrite={2}",
                    new Object[]{connection, queueRecord, isLocked});
        }

        try {
            if (isLocked) {
                final int bytesWritten = write0(connection, queueRecord);
                connectionQueue.releaseSpace(bytesWritten, true);
            } else if (pendingBytes > maxPendingBytes && bufferSize > 0) {
                connectionQueue.releaseSpace(bufferSize, false);
                throw new PendingWriteQueueLimitExceededException(
                        "Max queued data limit exceeded: " +
                        pendingBytes + ">" + maxPendingBytes);
            }

            if (isLocked && isFinished(connection, queueRecord)) { // if direct write was completed
                // If buffer was written directly - set next queue element as current
                // Notify callback handler
                AsyncWriteQueueRecord nextRecord = queue.poll();
                currentElement.set(nextRecord);
                
                if (isLogFine) {
                    logger.log(Level.FINEST, "AsyncQueueWriter.write completed connection={0} record={1} nextRecord={2}",
                            new Object[]{connection, queueRecord, nextRecord});
                }
                
                onWriteComplete(connection, queueRecord);

                if (nextRecord == null) { // if nothing in queue
                    // try one more time
                    nextRecord = queue.peek();
                    if (isLogFine) {
                        logger.log(Level.FINEST, "AsyncQueueWriter.write peek connection={0} nextRecord={1}",
                                new Object[]{connection, nextRecord});
                    }
                    if (nextRecord != null
                            && currentElement.compareAndSet(null, nextRecord)) {
                        if (isLogFine) {
                            logger.log(Level.FINEST, "AsyncQueueWriter.write peek, onReadyToWrite. connection={0}",
                                    connection);
                        }
                        if (queue.remove(nextRecord)) {
                            onReadyToWrite(connection);
                        }
                    }
                } else { // if there is something in queue
                    if (isLogFine) {
                        logger.log(Level.FINEST, "AsyncQueueWriter.write onReadyToWrite. connection={0}",
                                connection);
                    }
                    
                    onReadyToWrite(connection);
                }

                return ReadyFutureImpl.<WriteResult<Buffer, SocketAddress>>create(currentResult);
            } else { // If either write is not completed or queue is not empty
                
                // Create future
                final FutureImpl future = SafeFutureImpl.create();
                queueRecord.setFuture(future);

                if (cloner != null) {
                    if (isLogFine) {
                        logger.log(Level.FINEST, "AsyncQueueWriter.write clone. connection={0}",
                                connection);
                    }
                    // clone message
                    buffer = cloner.clone(connection, buffer);
                    queueRecord.setMessage(buffer);
                    queueRecord.setOutputBuffer(buffer);
                    queueRecord.setCloned(true);
                }

                if (isLocked) { // If write wasn't completed
                    if (isLogFine) {
                        logger.log(Level.FINEST, "AsyncQueueWriter.write onReadyToWrite. connection={0}",
                                connection);
                    }

                    currentElement.set(queueRecord);
                    onReadyToWrite(connection);
                } else {  // if queue wasn't empty
                    if (isLogFine) {
                        logger.log(Level.FINEST, "AsyncQueueWriter.write queue record. connection={0} record={1}",
                                new Object[]{connection, queueRecord});
                    }
                    
                    connectionQueue.getQueue().offer(queueRecord);

                    if (currentElement.compareAndSet(null, queueRecord)) {
                        if (isLogFine) {
                            logger.log(Level.FINEST, "AsyncQueueWriter.write set record as current. connection={0} record={1}",
                                    new Object[]{connection, queueRecord});
                        }
                        
                        if (queue.remove(queueRecord)) {
                            onReadyToWrite(connection);
                        }
                    }

                    // Check whether connection is still open
                    if (!connection.isOpen() && queue.remove(queueRecord)) {
                        if (isLogFine) {
                            logger.log(Level.FINEST, "AsyncQueueWriter.write connection is closed. connection={0} record={1}",
                                    new Object[]{connection, queueRecord});
                        }
                        onWriteFailure(connection, queueRecord,
                                new IOException("Connection is closed"));
                    }
                }

                return future;
            }
        } catch (IOException e) {
            if (isLogFine) {
                logger.log(Level.FINEST, "AsyncQueueWriter.write exception. connection=" + connection + " record=" + queueRecord, e);
            }
            onWriteFailure(connection, queueRecord, e);
            return ReadyFutureImpl.create(e);
        }
    }

    protected AsyncWriteQueueRecord createRecord(final Object message,
            final Future future,
            final WriteResult currentResult,
            final CompletionHandler completionHandler,
            final Interceptor interceptor,
            final Object dstAddress,
            final Buffer outputBuffer,
            final boolean isCloned) {
        return AsyncWriteQueueRecord.create(message, future, currentResult,
                completionHandler, interceptor, dstAddress, outputBuffer,
                isCloned);
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public final boolean isReady(Connection connection) {
        TaskQueue connectionQueue =
                ((AbstractNIOConnection) connection).getAsyncWriteQueue();

        return connectionQueue != null &&
                (connectionQueue.getCurrentElement() != null ||
                (connectionQueue.getQueue() != null &&
                !connectionQueue.getQueue().isEmpty()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void processAsync(Connection connection) throws IOException {
        final boolean isLogFine = logger.isLoggable(Level.FINEST);
        
        final TaskQueue<AsyncWriteQueueRecord> connectionQueue =
                ((AbstractNIOConnection) connection).getAsyncWriteQueue();

        final Queue<AsyncWriteQueueRecord> queue = connectionQueue.getQueue();
        final AtomicReference<AsyncWriteQueueRecord> currentElement =
                connectionQueue.getCurrentElementAtomic();

        AsyncWriteQueueRecord queueRecord = currentElement.get();
        if (isLogFine) {
            logger.log(Level.FINEST, "AsyncQueueWriter.processAsync connection={0} record={1} isLockRecord={2}",
                    new Object[]{connection, queueRecord, queueRecord == LOCK_RECORD});
        }
        
        if (queueRecord == LOCK_RECORD) return;
        
        try {
            while (queueRecord != null) {
                if (isLogFine) {
                    logger.log(Level.FINEST, "AsyncQueueWriter.processAsync doWrite connection={0} record={1}",
                            new Object[]{connection, queueRecord});
                }
                
                final int bytesWritten = write0(connection, queueRecord);
                connectionQueue.releaseSpace(bytesWritten, true);

                // check if buffer was completely written
                if (isFinished(connection, queueRecord)) {

                    if (isLogFine) {
                        logger.log(Level.FINEST, "AsyncQueueWriter.processAsync finished connection={0} record={1}",
                                new Object[]{connection, queueRecord});
                    }
                    
                    final AsyncWriteQueueRecord nextRecord = queue.poll();
                    currentElement.set(nextRecord);
                    
                    onWriteComplete(connection, queueRecord);

                    queueRecord = nextRecord;
                    if (isLogFine) {
                        logger.log(Level.FINEST, "AsyncQueueWriter.processAsync nextRecord connection={0} nextRecord={1}",
                                new Object[]{connection, queueRecord});
                    }
                    // check if there is ready element in the queue
                    if (queueRecord == null) {
                        queueRecord = queue.peek();
                        if (isLogFine) {
                            logger.log(Level.FINEST, "AsyncQueueWriter.processAsync peekRecord connection={0} peekRecord={1}",
                                    new Object[]{connection, queueRecord});
                        }
                        if (queueRecord != null
                                && currentElement.compareAndSet(null, queueRecord)) {

                            if (isLogFine) {
                                logger.log(Level.FINEST, "AsyncQueueWriter.processAsync set as current connection={0} peekRecord={1}",
                                        new Object[]{connection, queueRecord});
                            }
                            
                            if (!queue.remove(queueRecord)) { // if the record was picked up by another thread
                                break;
                            }

                        } else { // If there are no elements - return
                            break;
                        }
                    }
                } else { // if there is still some data in current message
                    if (isLogFine) {
                        logger.log(Level.FINEST, "AsyncQueueWriter.processAsync onReadyToWrite connection={0} peekRecord={1}",
                                new Object[]{connection, queueRecord});
                    }
                    onReadyToWrite(connection);
                    break;
                }
            }
        } catch (IOException e) {
            if (isLogFine) {
                logger.log(Level.FINEST, "AsyncQueueWriter.processAsync exception connection=" + connection + " peekRecord=" + queueRecord, e);
            }
            onWriteFailure(connection, queueRecord, e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onClose(Connection connection) {
        final AbstractNIOConnection nioConnection =
                (AbstractNIOConnection) connection;
        final TaskQueue<AsyncWriteQueueRecord> writeQueue =
                nioConnection.getAsyncWriteQueue();
        if (writeQueue != null) {
            AsyncWriteQueueRecord record =
                    writeQueue.getCurrentElementAtomic().getAndSet(LOCK_RECORD);

            final Throwable error = new IOException("Connection closed");
            
            if (record != LOCK_RECORD) {
                failWriteRecord(connection, record, error);
            }

            final Queue<AsyncWriteQueueRecord> recordsQueue =
                    writeQueue.getQueue();
            if (recordsQueue != null) {
                while ((record = recordsQueue.poll()) != null) {
                    failWriteRecord(connection, record, error);
                }
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final void close() {
    }

//    /**
//     * Performs real write on the NIO channel
//
//     * @param connection the {@link Connection} to write to
//     * @param queueRecord the record to write
//     * @throws java.io.IOException
//     */
//    protected final int doWrite(final Connection connection,
//            final AsyncWriteQueueRecord queueRecord) throws IOException {
//
//        final Buffer outputBuffer = queueRecord.getOutputBuffer();
//        final SocketAddress dstAddress = (SocketAddress) queueRecord.getDstAddress();
//        final WriteResult currentResult = queueRecord.getCurrentResult();
//        final int bytesWritten = write0(connection, dstAddress,
//                outputBuffer, currentResult);
//
//        if (bytesWritten == -1) {
//            throw new IOException("Connection is closed");
//        }
//
//        return bytesWritten;
//    }

    protected final void onWriteComplete(Connection connection,
            AsyncWriteQueueRecord record) throws IOException {

        final WriteResult currentResult = record.getCurrentResult();
        final FutureImpl future = (FutureImpl) record.getFuture();
        final CompletionHandler<WriteResult> completionHandler =
                record.getCompletionHandler();
        final Object originalMessage = record.getOriginalMessage();

        record.recycle();
        
        if (future != null) {
            future.result(currentResult);
        }        

        if (completionHandler != null) {
            completionHandler.completed(currentResult);
        }

        if (originalMessage instanceof Buffer) {
            // try to dispose originalBuffer (if allowed)
            ((Buffer) originalMessage).tryDispose();
        }
    }

    protected final void onWriteIncomplete(Connection connection,
            AsyncWriteQueueRecord record)
            throws IOException {

        WriteResult currentResult = record.getCurrentResult();
        CompletionHandler<WriteResult> completionHandler =
                record.getCompletionHandler();

        if (completionHandler != null) {
            completionHandler.updated(currentResult);
        }
    }

    protected final void onWriteFailure(Connection connection,
            AsyncWriteQueueRecord failedRecord, IOException e) {

        failWriteRecord(connection, failedRecord, e);
        try {
            connection.close().markForRecycle(true);
        } catch (IOException ignored) {
        }
    }

    protected final void failWriteRecord(Connection connection,
            AsyncWriteQueueRecord record, Throwable e) {
        if (record == null) {
            return;
        }

        final FutureImpl future = (FutureImpl) record.getFuture();
        final boolean hasFuture = (future != null);
        
        if (!hasFuture || !future.isDone()) {
            CompletionHandler<WriteResult> completionHandler =
                    record.getCompletionHandler();

            if (completionHandler != null) {
                completionHandler.failed(e);
            }

            if (hasFuture) {
                future.failure(e);
            }
        }
    }

    private boolean isFinished(final Connection connection,
            final AsyncWriteQueueRecord queueRecord) {
        final Buffer buffer = queueRecord.getOutputBuffer();
        return !buffer.hasRemaining();
    }

    protected abstract int write0(final Connection connection,
            final AsyncWriteQueueRecord queueRecord)
            throws IOException;

    protected abstract void onReadyToWrite(Connection connection)
            throws IOException;


}
