/*
 * 
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2007-2010 Sun Microsystems, Inc. All rights reserved.
 * 
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License. You can obtain
 * a copy of the License at https://glassfish.dev.java.net/public/CDDL+GPL.html
 * or glassfish/bootstrap/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 * 
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/bootstrap/legal/LICENSE.txt.
 * Sun designates this particular file as subject to the "Classpath" exception
 * as provided by Sun in the GPL Version 2 section of the License file that
 * accompanied this code.  If applicable, add the following below the License
 * Header, with the fields enclosed by brackets [] replaced by your own
 * identifying information: "Portions Copyrighted [year]
 * [name of copyright owner]"
 * 
 * Contributor(s):
 * 
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
 *
 */
package com.sun.grizzly.nio;

import com.sun.grizzly.Transformer;
import java.io.IOException;
import java.net.SocketAddress;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;
import com.sun.grizzly.AbstractWriter;
import com.sun.grizzly.Buffer;
import com.sun.grizzly.CompletionHandler;
import com.sun.grizzly.Connection;
import com.sun.grizzly.IOEvent;
import com.sun.grizzly.Context;
import com.sun.grizzly.Grizzly;
import com.sun.grizzly.Interceptor;
import com.sun.grizzly.ProcessorResult;
import com.sun.grizzly.TransformationResult;
import com.sun.grizzly.WriteResult;
import com.sun.grizzly.asyncqueue.AsyncQueue;
import com.sun.grizzly.asyncqueue.AsyncQueueWriter;
import com.sun.grizzly.asyncqueue.AsyncWriteQueueRecord;
import com.sun.grizzly.asyncqueue.MessageCloner;
import com.sun.grizzly.impl.FutureImpl;
import com.sun.grizzly.utils.ObjectPool;
import java.io.EOFException;
import java.util.Queue;

/**
 * The {@link AsyncQueueWriter} implementation, based on the Java NIO
 * 
 * @author Alexey Stashok
 */
public abstract class AbstractNIOAsyncQueueWriter
        extends AbstractWriter<SocketAddress>
        implements AsyncQueueWriter<SocketAddress> {

    protected NIOTransport transport;

    private final static Logger logger = Grizzly.logger(AbstractNIOAsyncQueueWriter.class);

    public AbstractNIOAsyncQueueWriter(NIOTransport transport) {
        this.transport = transport;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <M> Future<WriteResult<M, SocketAddress>> write(
            Connection connection,
            SocketAddress dstAddress, M message,
            CompletionHandler<WriteResult<M, SocketAddress>> completionHandler,
            Transformer<M, Buffer> transformer,
            Interceptor<WriteResult> interceptor) throws IOException {
        return write(connection, dstAddress, message, completionHandler,
                transformer, interceptor, null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <M> Future<WriteResult<M, SocketAddress>> write(
            Connection connection, SocketAddress dstAddress,
            M message,
            CompletionHandler<WriteResult<M, SocketAddress>> completionHandler,
            Transformer<M, Buffer> transformer,
            Interceptor<WriteResult> interceptor,
            MessageCloner<M> cloner) throws IOException {

        if (connection == null) {
            throw new IOException("Connection is null");
        } else if (!connection.isOpen()) {
            throw new IOException("Connection is closed");
        }

        // Create future
        final FutureImpl future = new FutureImpl();
        final WriteResult currentResult = new WriteResult(connection);
        currentResult.setMessage(message);
        currentResult.setDstAddress(dstAddress);
        currentResult.setWrittenSize(0);

        // Get connection async write queue
        final AsyncQueue<AsyncWriteQueueRecord> connectionQueue =
                ((AbstractNIOConnection) connection).getAsyncWriteQueue();

        final Queue<AsyncWriteQueueRecord> queue = connectionQueue.getQueue();
        final AtomicReference<AsyncWriteQueueRecord> currentElement =
                connectionQueue.getCurrentElementAtomic();
        final ReentrantLock lock = connectionQueue.getQueuedActionLock();
        boolean isLockedByMe = false;

        // create and initialize the write queue record
        final AsyncWriteQueueRecord queueRecord = new AsyncWriteQueueRecord(
                message, future, currentResult, completionHandler,
                transformer, interceptor, dstAddress,
                transformer == null ? (Buffer) message : null,
                false);

        // If AsyncQueue is empty - try to write Buffer here
        try {

            if (currentElement.get() == null && // Weak comparison for null
                    lock.tryLock()) {
                isLockedByMe = true;
                // Strong comparison for null, because we're in locked region
                if (currentElement.compareAndSet(null, queueRecord)) {
                    doWrite(connection, queueRecord);
                } else {
                    isLockedByMe = false;
                    lock.unlock();
                }
            }

            if (isLockedByMe && isFinished(connection, queueRecord)) {
                // If buffer was written directly - set next queue element as current
                // Notify callback handler
                onWriteCompleted(connection, queueRecord);
                
                AsyncWriteQueueRecord nextRecord = queue.poll();
                if (nextRecord != null) { // if there is something in queue
                    currentElement.set(nextRecord);
                    isLockedByMe = false;
                    lock.unlock();
                    onReadyToWrite(connection);
                } else { // if nothing in queue
                    currentElement.set(null);
                    isLockedByMe = false;
                    lock.unlock();  // unlock
                    if (queue.peek() != null) {  // check one more time
                        onReadyToWrite(connection);
                    }
                }
            } else { // If there are no bytes available for writing
                if (cloner != null) {
                    // clone message
                    message = cloner.clone(connection, message);
                    queueRecord.setMessage(message);

                    if (transformer == null) {
                        queueRecord.setOutputBuffer((Buffer) message);
                    }
                    
                    queueRecord.setCloned(true);
                }

                boolean isRegisterForWriting = false;

                // add new element to the queue, if it's not current
                if (currentElement.get() != queueRecord) {
                    queue.offer(queueRecord); // add to queue
                    if (!lock.isLocked()) {
                        isRegisterForWriting = true;
                    }
                } else {  // if element was written direct (not fully written)
                    isRegisterForWriting = true;
                    if (isLockedByMe) {
                        isLockedByMe = false;
                        lock.unlock();
                    }
                }

                if (isRegisterForWriting) {
                    onReadyToWrite(connection);
                }
            }
        } catch (IOException e) {
            onWriteFailure(connection, queueRecord, e);
            throw e;
        } finally {
            if (isLockedByMe) {
                lock.unlock();
            }
        }

        return future;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isReady(Connection connection) {
        AsyncQueue connectionQueue =
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
        final AsyncQueue<AsyncWriteQueueRecord> connectionQueue =
                ((AbstractNIOConnection) connection).getAsyncWriteQueue();

        final Queue<AsyncWriteQueueRecord> queue = connectionQueue.getQueue();
        final AtomicReference<AsyncWriteQueueRecord> currentElement =
                connectionQueue.getCurrentElementAtomic();
        final ReentrantLock lock = connectionQueue.getQueuedActionLock();
        boolean isLockedByMe = false;

        if (currentElement.get() == null) {
            AsyncWriteQueueRecord nextRecord = queue.peek();
            if (nextRecord != null && lock.tryLock()) {
                if (!queue.isEmpty() &&
                        currentElement.compareAndSet(null, nextRecord)) {
                    queue.remove();
                }
            } else {
                return;
            }
        } else if (!lock.tryLock()) {
            return;
        }

        isLockedByMe = true;
        AsyncWriteQueueRecord<SocketAddress> queueRecord = null;
        try {
            while (currentElement.get() != null) {
                queueRecord = currentElement.get();

                doWrite(connection, queueRecord);

                // check if buffer was completely written
                if (isFinished(connection, queueRecord)) {
                    currentElement.set(queue.poll());

                    onWriteCompleted(connection, queueRecord);

                    // If last element in queue is null - we have to be careful
                    if (currentElement.get() == null) {
                        if (isLockedByMe) {
                            isLockedByMe = false;
                            lock.unlock();
                        }
                        AsyncWriteQueueRecord nextRecord = queue.peek();
                        if (nextRecord != null && lock.tryLock()) {
                            isLockedByMe = true;
                            if (!queue.isEmpty() &&
                                    currentElement.compareAndSet(null,
                                    nextRecord)) {
                                queue.remove();
                            }

                            continue;
                        } else {
                            break;
                        }
                    }
                } else { // if there is still some data in current message
                    if (isLockedByMe) {
                        isLockedByMe = false;
                        lock.unlock();
                    }
                    onReadyToWrite(connection);
                    break;
                }
            }
        } catch (IOException e) {
            onWriteFailure(connection, queueRecord, e);
        } finally {
            if (isLockedByMe) {
                connectionQueue.getQueuedActionLock().unlock();
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onClose(Connection connection) {
        final AbstractNIOConnection nioConnection =
                (AbstractNIOConnection) connection;
        final AsyncQueue<AsyncWriteQueueRecord> writeQueue =
                nioConnection.getAsyncWriteQueue();
        if (writeQueue != null) {
            writeQueue.getQueuedActionLock().lock();
            try {
                AsyncWriteQueueRecord record =
                        writeQueue.getCurrentElementAtomic().getAndSet(null);

                Throwable error = new IOException("Connection closed");
                failWriteRecord(connection, record, error);

                Queue<AsyncWriteQueueRecord> recordsQueue =
                        writeQueue.getQueue();
                if (recordsQueue != null) {
                    while (!recordsQueue.isEmpty()) {
                        failWriteRecord(connection, recordsQueue.poll(),
                                error);
                    }
                }
            } finally {
                writeQueue.getQueuedActionLock().unlock();
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    public ObjectPool getContextPool() {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isInterested(IOEvent ioEvent) {
        return ioEvent == IOEvent.WRITE;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ProcessorResult process(Context context)
            throws IOException {
        processAsync(context.getConnection());
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setInterested(IOEvent ioEvent, boolean isInterested) {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() {
    }

    /**
     * Performs real write on the NIO channel

     * @param connection the {@link Connection} to write to
     * @param currentResult current result of the write operation
     * @param dstAddress destination address
     * @param message the message to write
     * @param writePreProcessor write post-processor
     * @throws java.io.IOException
     */
    protected final <E> void doWrite(final Connection connection,
            final AsyncWriteQueueRecord<SocketAddress> queueRecord) throws IOException {
        final Transformer transformer = queueRecord.getTransformer();
        if (transformer == null) {
            final Buffer outputBuffer = queueRecord.getOutputBuffer();
            final SocketAddress dstAddress = queueRecord.getDstAddress();
            final WriteResult currentResult = queueRecord.getCurrentResult();
            final int bytesWritten = write0(connection, dstAddress,
                    outputBuffer, currentResult);

            if (bytesWritten == -1) {
                throw new EOFException();
            }
            
        } else {
            final WriteResult writeResult = new WriteResult(connection);
            do {
                final Object message = queueRecord.getMessage();
                final Buffer outputBuffer = queueRecord.getOutputBuffer();

                if (outputBuffer != null && outputBuffer.hasRemaining()) {

                    final SocketAddress dstAddress = queueRecord.getDstAddress();
                    final WriteResult currentResult = queueRecord.getCurrentResult();
                    writeResult.setMessage(null);
                    writeResult.setWrittenSize(0);
                    
                    final int bytesWritten = write0(connection, dstAddress,
                            outputBuffer, writeResult);
                    if (bytesWritten == -1) {
                        throw new EOFException();
                    }

                    currentResult.setWrittenSize(
                            currentResult.getWrittenSize() + bytesWritten);

                    if (!outputBuffer.hasRemaining()) {
                        if (queueRecord.getOriginalMessage() != outputBuffer) {
                            outputBuffer.dispose();
                        }
                        
                        queueRecord.setOutputBuffer(null);
                    } else {
                        return;
                    }
                }

                TransformationResult tResult = transformer.getLastResult(connection);
                if (tResult != null &&
                        tResult.getStatus() == TransformationResult.Status.COMPLETED &&
                        !tResult.hasInternalRemainder() &&
                        !transformer.hasInputRemaining(message)) {
                    return;
                }

                tResult = transformer.transform(connection, message);

                if (tResult.getStatus() == TransformationResult.Status.COMPLETED) {
                    queueRecord.setOutputBuffer((Buffer) tResult.getMessage());
                    queueRecord.setMessage(tResult.getExternalRemainder());
                } else if (tResult.getStatus() == TransformationResult.Status.INCOMPLETED) {
                    throw new IOException("Transformation exception: provided message is incompleted");
                } else {
                    throw new IOException("Transformation error (" +
                            tResult.getErrorCode() + "): " +
                            tResult.getErrorDescription());
                }                
            } while (true);
        }
    }

    protected void onWriteCompleted(Connection connection,
            AsyncWriteQueueRecord<?> record)
            throws IOException {

        final Transformer transformer = record.getTransformer();
        if (transformer != null) {
            transformer.release(connection);
        }
        
        final FutureImpl future = (FutureImpl) record.getFuture();
        final WriteResult currentResult = record.getCurrentResult();
        future.result(currentResult);
        final CompletionHandler<WriteResult> completionHandler =
                record.getCompletionHandler();

        if (completionHandler != null) {
            completionHandler.completed(currentResult);
        }
    }

    protected void onWriteIncompleted(Connection connection,
            AsyncWriteQueueRecord<?> record)
            throws IOException {

        WriteResult currentResult = record.getCurrentResult();
        CompletionHandler<WriteResult> completionHandler =
                record.getCompletionHandler();

        if (completionHandler != null) {
            completionHandler.updated(currentResult);
        }
    }

    protected void onWriteFailure(Connection connection,
            AsyncWriteQueueRecord failedRecord, IOException e) {

        failWriteRecord(connection, failedRecord, e);
        try {
            connection.close();
        } catch (IOException ioe) {
        }
    }

    protected void failWriteRecord(Connection connection,
            AsyncWriteQueueRecord record, Throwable e) {
        if (record == null) {
            return;
        }

        FutureImpl future = (FutureImpl) record.getFuture();
        if (!future.isDone()) {
            CompletionHandler<WriteResult> completionHandler =
                    record.getCompletionHandler();

            if (completionHandler != null) {
                completionHandler.failed(e);
            }

            future.failure(e);
        }
    }

    private boolean isFinished(final Connection connection,
            final AsyncWriteQueueRecord queueRecord) {
        final Transformer trasformer = queueRecord.getTransformer();
        final Buffer buffer = queueRecord.getOutputBuffer();
        
        if (trasformer == null) {
            return !buffer.hasRemaining();
        } else {
            final TransformationResult tResult =
                    trasformer.getLastResult(connection);
            return buffer == null && tResult != null &&
                    tResult.getStatus() == TransformationResult.Status.COMPLETED;
        }
    }

    protected abstract int write0(Connection connection,
            SocketAddress dstAddress, Buffer buffer,
            WriteResult<Buffer, SocketAddress> currentResult)
            throws IOException;

    protected abstract void onReadyToWrite(Connection connection)
            throws IOException;
}
