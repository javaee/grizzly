/*
 * 
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2007-2008 Sun Microsystems, Inc. All rights reserved.
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
package org.glassfish.grizzly.nio;

import java.io.IOException;
import java.net.SocketAddress;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;
import org.glassfish.grizzly.AbstractWriter;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.CompletionHandler;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.IOEvent;
import org.glassfish.grizzly.Context;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.Interceptor;
import org.glassfish.grizzly.ProcessorResult;
import org.glassfish.grizzly.WriteResult;
import org.glassfish.grizzly.asyncqueue.AsyncQueue;
import org.glassfish.grizzly.asyncqueue.AsyncQueueWriter;
import org.glassfish.grizzly.asyncqueue.AsyncWriteQueueRecord;
import org.glassfish.grizzly.asyncqueue.MessageCloner;
import org.glassfish.grizzly.impl.FutureImpl;
import org.glassfish.grizzly.util.LinkedTransferQueue;
import org.glassfish.grizzly.util.ObjectPool;

/**
 * The {@link AsyncQueueWriter} implementation, based on the Java NIO
 * 
 * @author Alexey Stashok
 */
public abstract class AbstractNIOAsyncQueueWriter
        extends AbstractWriter<SocketAddress>
        implements AsyncQueueWriter<SocketAddress> {

    protected NIOTransport transport;

    private final static Logger logger = Grizzly.logger;

    public AbstractNIOAsyncQueueWriter(NIOTransport transport) {
        this.transport = transport;
    }

    /**
     * {@inheritDoc}
     */
    public Future<WriteResult<Buffer, SocketAddress>> write(
            Connection connection, SocketAddress dstAddress,
            Buffer buffer,
            CompletionHandler<WriteResult<Buffer, SocketAddress>> completionHandler,
            Interceptor<WriteResult> interceptor) throws IOException {
        return write(connection, dstAddress, buffer, completionHandler,
                interceptor, null);
    }

    /**
     * {@inheritDoc}
     */
    public Future<WriteResult<Buffer, SocketAddress>> write(
            Connection connection, SocketAddress dstAddress,
            Buffer buffer,
            CompletionHandler<WriteResult<Buffer, SocketAddress>> completionHandler,
            Interceptor<WriteResult> interceptor,
            MessageCloner<Buffer> cloner) throws IOException {

        if (connection == null) {
            throw new IOException("Connection is null");
        } else if (!connection.isOpen()) {
            throw new IOException("Connection is closed");
        }

        // Create future
        FutureImpl future = new FutureImpl();
        WriteResult currentResult = new WriteResult(connection);
        currentResult.setMessage(buffer);
        currentResult.setDstAddress(dstAddress);
        currentResult.setWrittenSize(0);

        // Get connection async write queue
        AsyncQueue<AsyncWriteQueueRecord> connectionQueue =
                obtainAsyncWriteQueue(connection);

        LinkedTransferQueue<AsyncWriteQueueRecord> queue =
                connectionQueue.getQueue();
        AtomicReference<AsyncWriteQueueRecord> currentElement =
                connectionQueue.getCurrentElement();
        ReentrantLock lock = connectionQueue.getQueuedActionLock();
        boolean isLockedByMe = false;

        // create and initialize the write queue record
        AsyncWriteQueueRecord record = new AsyncWriteQueueRecord();
        record.set(buffer, future, currentResult, completionHandler,
                interceptor, dstAddress);

        // If AsyncQueue is empty - try to write Buffer here
        try {

            if (currentElement.get() == null && // Weak comparison for null
                    lock.tryLock()) {
                isLockedByMe = true;
                // Strong comparison for null, because we're in locked region
                if (currentElement.compareAndSet(null, record)) {
                    doWrite(connection, currentResult, completionHandler,
                            (SocketAddress) dstAddress, buffer);
                } else {
                    isLockedByMe = false;
                    lock.unlock();
                }
            }

            if (isLockedByMe && isFinished(connection, buffer)) {
                // If buffer was written directly - set next queue element as current
                // Notify callback handler
                onWriteCompleted(connection, record);
                
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
                    buffer = cloner.clone(connection, buffer);
                    record.setBuffer(buffer);
                    record.setCloned(true);
                }

                boolean isRegisterForWriting = false;

                // add new element to the queue, if it's not current
                if (currentElement.get() != record) {
                    queue.offer(record); // add to queue
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
            onWriteFailure(connection, record, e);
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
    public boolean isReady(Connection connection) {
        AsyncQueue connectionQueue = getAsyncWriteQueue(connection);

        return connectionQueue != null &&
                (connectionQueue.getCurrentElement().get() != null ||
                (connectionQueue.getQueue() != null &&
                !connectionQueue.getQueue().isEmpty()));
    }

    /**
     * {@inheritDoc}
     */
    public void processAsync(Connection connection) throws IOException {
        AsyncQueue<AsyncWriteQueueRecord> connectionQueue =
                obtainAsyncWriteQueue(connection);

        LinkedTransferQueue<AsyncWriteQueueRecord> queue =
                connectionQueue.getQueue();
        AtomicReference<AsyncWriteQueueRecord> currentElement =
                connectionQueue.getCurrentElement();
        ReentrantLock lock = connectionQueue.getQueuedActionLock();
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

                WriteResult currentResult = queueRecord.getCurrentResult();
                Buffer buffer = queueRecord.getBuffer();
                doWrite(connection, currentResult, queueRecord.getCompletionHandler(),
                        (SocketAddress) queueRecord.getDstAddress(),
                        buffer);

                // check if buffer was completely written
                if (isFinished(connection, buffer)) {
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
    public void onClose(Connection connection) {
        AbstractNIOConnection nioConnection =
                (AbstractNIOConnection) connection;
        AsyncQueue<AsyncWriteQueueRecord> writeQueue =
                nioConnection.getAsyncWriteQueue();
        if (writeQueue != null) {
            writeQueue.getQueuedActionLock().lock();
            try {
                AsyncWriteQueueRecord record =
                        writeQueue.getCurrentElement().getAndSet(null);

                Throwable error = new IOException("Connection closed");
                failWriteRecord(connection, record, error);

                LinkedTransferQueue<AsyncWriteQueueRecord> recordsQueue =
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
    public boolean isInterested(IOEvent ioEvent) {
        return ioEvent == IOEvent.WRITE;
    }

    /**
     * {@inheritDoc}
     */
    public ProcessorResult process(Context context)
            throws IOException {
        processAsync(context.getConnection());
        return null;
    }

    /**
     * {@inheritDoc}
     */
    public void setInterested(IOEvent ioEvent, boolean isInterested) {
    }

    /**
     * {@inheritDoc}
     */
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
    protected <E> void doWrite(Connection connection, WriteResult currentResult,
            CompletionHandler completionHandler,
            SocketAddress dstAddress, Buffer buffer)
            throws IOException {

        write0(connection, dstAddress, buffer, currentResult);
    }

    protected void onWriteCompleted(Connection connection,
            AsyncWriteQueueRecord<?> record)
            throws IOException {

        FutureImpl future = (FutureImpl) record.getFuture();
        WriteResult currentResult = record.getCurrentResult();
        future.setResult(currentResult);
        CompletionHandler<WriteResult> completionHandler =
                record.getCompletionHandler();

        if (completionHandler != null) {
            completionHandler.completed(connection, currentResult);
        }
    }

    protected void onWriteIncompleted(Connection connection,
            AsyncWriteQueueRecord<?> record)
            throws IOException {

        WriteResult currentResult = record.getCurrentResult();
        CompletionHandler<WriteResult> completionHandler =
                record.getCompletionHandler();

        if (completionHandler != null) {
            completionHandler.updated(connection, currentResult);
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
                completionHandler.failed(connection, e);
            }

            future.failure(e);
        }
    }

    private boolean isFinished(Connection connection, Buffer originalBuffer) {
        return !originalBuffer.hasRemaining();
    }

    protected abstract int write0(Connection connection,
            SocketAddress dstAddress, Buffer buffer,
            WriteResult<Buffer, SocketAddress> currentResult)
            throws IOException;

    protected abstract AsyncQueue<AsyncWriteQueueRecord> getAsyncWriteQueue(
            Connection connection);

    protected abstract AsyncQueue<AsyncWriteQueueRecord> obtainAsyncWriteQueue(
            Connection connection);

    protected abstract void onReadyToWrite(Connection connection)
            throws IOException;
}
