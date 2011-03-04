/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2011 Oracle and/or its affiliates. All rights reserved.
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

import org.glassfish.grizzly.PendingWriteQueueLimitExceededException;
import java.io.IOException;
import java.net.SocketAddress;
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

import java.util.concurrent.Future;
import java.util.logging.Level;

/**
 * The {@link AsyncQueueWriter} implementation, based on the Java NIO
 * 
 * @author Alexey Stashok
 * @author Ryan Lubke
 */
@SuppressWarnings("unchecked")
public abstract class AbstractNIOAsyncQueueWriter
        extends AbstractWriter<SocketAddress>
        implements AsyncQueueWriter<SocketAddress> {

    private final static Logger logger = Grizzly.logger(AbstractNIOAsyncQueueWriter.class);

    static final AsyncWriteQueueRecord LOCK_RECORD =
            AsyncWriteQueueRecord.create(null, null, null, null, null, null,
            null, null, false);
    
    protected final NIOTransport transport;

    protected volatile int maxPendingBytes = -1;

    // Cached IOException to throw from onClose()
    // Probably we shouldn't even care it's not volatile
    private IOException cachedIOException;

    public AbstractNIOAsyncQueueWriter(NIOTransport transport) {
        this.transport = transport;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public boolean canWrite(Connection connection, int size) {
        if (maxPendingBytes < 0) {
            return true;
        }
        final TaskQueue<AsyncWriteQueueRecord> connectionQueue =
                ((NIOConnection) connection).getAsyncWriteQueue();
        return connectionQueue.spaceInBytes() + size < maxPendingBytes;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public void setMaxPendingBytesPerConnection(final int maxPendingBytes) {
        if (maxPendingBytes <= 0) {
            this.maxPendingBytes = -1;
        } else {
            this.maxPendingBytes = maxPendingBytes;
        }
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
        final NIOConnection nioConnection = (NIOConnection) connection;

        // Get connection async write queue
        final TaskQueue<AsyncWriteQueueRecord> connectionQueue =
                nioConnection.getAsyncWriteQueue();


        final WriteResult<Buffer, SocketAddress> currentResult =
                WriteResult.create(connection,
                        buffer, dstAddress, 0);
        
        // create and initialize the write queue record
        final AsyncWriteQueueRecord queueRecord = createRecord(
                connection, buffer, null, currentResult, completionHandler,
                interceptor, dstAddress, buffer, false);

        final int bufferSize = buffer.remaining();

        int pendingBytes;
        if (maxPendingBytes > 0) {
            pendingBytes = connectionQueue.reserveSpace(bufferSize);
        } else {
            pendingBytes = bufferSize;
        }
        
        final boolean isLocked = connectionQueue.reserveCurrentElement();
        if (isLogFine) {
            logger.log(Level.FINEST, "AsyncQueueWriter.write connection={0} record={1} directWrite={2}",
                    new Object[]{connection, queueRecord, isLocked});
        }

        try {
            if (isLocked) {
                final int bytesWritten = write0(nioConnection, queueRecord);
                if (maxPendingBytes > 0) {
                    connectionQueue.releaseSpaceAndNotify(bytesWritten);
                }
            } else if (maxPendingBytes > 0 && pendingBytes > maxPendingBytes && bufferSize > 0) {
                connectionQueue.releaseSpace(bufferSize);
                throw new PendingWriteQueueLimitExceededException(
                        "Max queued data limit exceeded: " +
                        pendingBytes + '>' + maxPendingBytes);
            }

            if (isLocked && isFinished(queueRecord)) { // if direct write was completed
                // If buffer was written directly - set next queue element as current
                // Notify callback handler

                final AsyncWriteQueueRecord nextRecord =
                        connectionQueue.doneCurrentElement();

                if (isLogFine) {
                    logger.log(Level.FINEST, "AsyncQueueWriter.write completed connection={0} record={1} nextRecord={2}",
                            new Object[]{connection, queueRecord, nextRecord});
                }
                
                onWriteComplete(queueRecord);

                if (nextRecord != null) {
                    if (isLogFine) {
                        logger.log(Level.FINEST, "AsyncQueueWriter.write onReadyToWrite. connection={0}",
                                connection);
                    }

                    onReadyToWrite(connection);
                }

                return ReadyFutureImpl.create(currentResult);
            } else { // If either write is not completed or queue is not empty
                
                // Create future
                final FutureImpl<WriteResult<Buffer, SocketAddress>> future =
                        SafeFutureImpl.create();
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

                    connectionQueue.setCurrentElement(queueRecord);
                    onReadyToWrite(connection);
                } else {  // if queue wasn't empty
                    if (isLogFine) {
                        logger.log(Level.FINEST, "AsyncQueueWriter.write queue record. connection={0} record={1}",
                                new Object[]{connection, queueRecord});
                    }

                    final boolean isBecameCurrent = connectionQueue.offer(queueRecord);
                    if (isBecameCurrent) {
                        if (isLogFine) {
                            logger.log(Level.FINEST, "AsyncQueueWriter.write onReadyToWrite. connection={0}",
                                    connection);
                        }

                        onReadyToWrite(connection);
                    }

                    // Check whether connection is still open
                    if (!connection.isOpen() && connectionQueue.remove(queueRecord)) {
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

    protected AsyncWriteQueueRecord createRecord(final Connection connection,
            final Buffer message,
            final Future<WriteResult<Buffer, SocketAddress>> future,
            final WriteResult<Buffer, SocketAddress> currentResult,
            final CompletionHandler<WriteResult<Buffer, SocketAddress>> completionHandler,
            final Interceptor<WriteResult<Buffer, SocketAddress>> interceptor,
            final SocketAddress dstAddress,
            final Buffer outputBuffer,
            final boolean isCloned) {
        return AsyncWriteQueueRecord.create(connection, message, future,
                currentResult, completionHandler, interceptor, dstAddress,
                outputBuffer, isCloned);
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public final boolean isReady(final Connection connection) {
        final TaskQueue connectionQueue =
                ((NIOConnection) connection).getAsyncWriteQueue();

        return connectionQueue != null && !connectionQueue.isEmpty();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void processAsync(Connection connection) throws IOException {
        final boolean isLogFine = logger.isLoggable(Level.FINEST);
        final NIOConnection nioConnection = (NIOConnection) connection;
        
        final TaskQueue<AsyncWriteQueueRecord> connectionQueue =
                nioConnection.getAsyncWriteQueue();

        // Current element shouldn't be null here!
        // Though result queueRecord can be null, which means it is locked
        AsyncWriteQueueRecord queueRecord =
                connectionQueue.getCurrentElementAndReserve();

        if (isLogFine) {
            logger.log(Level.FINEST, "AsyncQueueWriter.processAsync connection={0} record={1} isLockRecord={2}",
                    new Object[]{connection, queueRecord, queueRecord == LOCK_RECORD});
        }

        assert queueRecord != null;
        
        try {
            while (queueRecord != null) {
                if (isLogFine) {
                    logger.log(Level.FINEST, "AsyncQueueWriter.processAsync doWrite connection={0} record={1}",
                            new Object[]{connection, queueRecord});
                }
                
                final int bytesWritten = write0(nioConnection, queueRecord);
                connectionQueue.releaseSpaceAndNotify(bytesWritten);

                // check if buffer was completely written
                if (isFinished(queueRecord)) {

                    if (isLogFine) {
                        logger.log(Level.FINEST, "AsyncQueueWriter.processAsync finished connection={0} record={1}",
                                new Object[]{connection, queueRecord});
                    }
                    
                    final AsyncWriteQueueRecord nextRecord =
                            connectionQueue.doneCurrentElement();

                    // Do compareAndSet, because connection might have been close
                    // from another thread, and failReadRecord has been invoked already
                    onWriteComplete(queueRecord);

                    if (isLogFine) {
                        logger.log(Level.FINEST, "AsyncQueueWriter.processAsync nextRecord connection={0} nextRecord={1}",
                                new Object[]{connection, queueRecord});
                    }
                    // check if there is ready element in the queue
                    if (nextRecord == null ||
                            // make sure it's still available
                            (queueRecord = connectionQueue.getCurrentElementAndReserve()) == null) {
                        break;
                    }

                } else { // if there is still some data in current message
                    connectionQueue.setCurrentElement(queueRecord);
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
        final NIOConnection nioConnection =
                (NIOConnection) connection;
        final TaskQueue<AsyncWriteQueueRecord> writeQueue =
                nioConnection.getAsyncWriteQueue();
        if (writeQueue != null) {
            AsyncWriteQueueRecord record =
                    writeQueue.getCurrentElementAndReserve();

            IOException error = cachedIOException;
            if (error == null) {
                error = new IOException("Connection closed");
                cachedIOException = error;
            }
            
            if (record != null) {
                failWriteRecord(record, error);
            }

            while ((record = writeQueue.doneCurrentElement()) != null) {
                failWriteRecord(record, error);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final void close() {
    }

    protected final void onWriteComplete(AsyncWriteQueueRecord record) throws IOException {

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

    protected final void onWriteIncomplete(AsyncWriteQueueRecord record)
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

        failWriteRecord(failedRecord, e);
        try {
            connection.close().markForRecycle(true);
        } catch (IOException ignored) {
        }
    }

    protected final void failWriteRecord(AsyncWriteQueueRecord record, Throwable e) {
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

    private boolean isFinished(final AsyncWriteQueueRecord queueRecord) {
        final Buffer buffer = queueRecord.getOutputBuffer();
        return !buffer.hasRemaining();
    }

    protected abstract int write0(final NIOConnection connection,
            final AsyncWriteQueueRecord queueRecord)
            throws IOException;

    protected abstract void onReadyToWrite(Connection connection)
            throws IOException;


}
