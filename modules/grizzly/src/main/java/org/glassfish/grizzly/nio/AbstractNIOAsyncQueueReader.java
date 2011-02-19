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

import java.io.EOFException;
import java.io.IOException;
import java.net.SocketAddress;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.grizzly.AbstractReader;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.CompletionHandler;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.GrizzlyFuture;
import org.glassfish.grizzly.Interceptor;
import org.glassfish.grizzly.ReadResult;
import org.glassfish.grizzly.Reader;
import org.glassfish.grizzly.asyncqueue.TaskQueue;
import org.glassfish.grizzly.asyncqueue.AsyncQueueReader;
import org.glassfish.grizzly.asyncqueue.AsyncReadQueueRecord;
import org.glassfish.grizzly.impl.FutureImpl;
import org.glassfish.grizzly.impl.ReadyFutureImpl;
import org.glassfish.grizzly.impl.SafeFutureImpl;
import java.util.Queue;

/**
 * The {@link AsyncQueueReader} implementation, based on the Java NIO
 * 
 * @author Alexey Stashok
 */
@SuppressWarnings("unchecked")
public abstract class AbstractNIOAsyncQueueReader
        extends AbstractReader<SocketAddress>
        implements AsyncQueueReader<SocketAddress> {

    private static final Logger LOGGER = Grizzly.logger(AbstractNIOAsyncQueueReader.class);

    private static final AsyncReadQueueRecord LOCK_RECORD =
            AsyncReadQueueRecord.create(null, null, null, null, null, null);
    public static final int DEFAULT_BUFFER_SIZE = 8192;
    protected int defaultBufferSize = DEFAULT_BUFFER_SIZE;
    protected final NIOTransport transport;

    // Cached EOFException to throw from onClose()
    // Probably we shouldn't even care it's not volatile
    private EOFException cachedEOFException;

    public AbstractNIOAsyncQueueReader(NIOTransport transport) {
        this.transport = transport;

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public GrizzlyFuture<ReadResult<Buffer, SocketAddress>> read(
            final Connection connection, Buffer buffer,
            final CompletionHandler<ReadResult<Buffer, SocketAddress>> completionHandler,
            final Interceptor<ReadResult> interceptor) throws IOException {

        if (connection == null) {
            throw new IOException("Connection is null");
        } else if (!connection.isOpen()) {
            throw new IOException("Connection is closed");
        }

        // Get connection async read queue
        final TaskQueue<AsyncReadQueueRecord> connectionQueue =
                ((NIOConnection) connection).getAsyncReadQueue();


        final ReadResult<Buffer, SocketAddress> currentResult =
                ReadResult.create(connection,
                        buffer, null, 0);

        // create and initialize the read queue record
        final AsyncReadQueueRecord queueRecord = AsyncReadQueueRecord.create(
                connection, buffer, null, currentResult,
                completionHandler, interceptor);

        final Queue<AsyncReadQueueRecord> queue = connectionQueue.getQueue();
        final AtomicReference<AsyncReadQueueRecord> currentElement =
                connectionQueue.getCurrentElementAtomic();

        final boolean isLocked = currentElement.compareAndSet(null, LOCK_RECORD);

        try {

            if (isLocked) { // If AsyncQueue is empty - try to read Buffer here
                doRead(connection, queueRecord);

                final int interceptInstructions = intercept(
                        Reader.READ_EVENT, queueRecord, currentResult);

                if ((interceptInstructions & Interceptor.COMPLETED) != 0
                        || (interceptor == null && isFinished(queueRecord))) { // if direct read is completed

                    // If message was read directly - set next queue element as current
                    AsyncReadQueueRecord nextRecord = queue.poll();
                    currentElement.set(nextRecord);
                    
                    // Notify callback handler
                    onReadComplete(queueRecord);

                    if (nextRecord == null) { // if nothing in queue
                        // try one more time
                        nextRecord = queue.peek();
                        if (nextRecord != null &&
                                currentElement.compareAndSet(null, nextRecord)) {
                            if (queue.remove(nextRecord)) {
                                onReadyToRead(connection);
                            }
                        }
                    } else { // if there is something in queue
                        onReadyToRead(connection);
                    }

                    intercept(COMPLETE_EVENT, queueRecord, null);
                    queueRecord.recycle();
                    return ReadyFutureImpl.create(
                            currentResult);
                } else { // If direct read is not finished
                // Create future
                    if ((interceptInstructions & Interceptor.RESET) != 0) {
                        currentResult.setMessage(null);
                        currentResult.setReadSize(0);
                        queueRecord.setMessage(null);
                    }

                    final FutureImpl<ReadResult<Buffer, SocketAddress>> future =
                            SafeFutureImpl.create();
                    queueRecord.setFuture(future);
                    currentElement.set(queueRecord);
                    
                    onReadIncomplete(queueRecord);
                    onReadyToRead(connection);

                    intercept(INCOMPLETE_EVENT, queueRecord, null);

                    return future;
                }

            } else { // Read queue is not empty - add new element to a queue
                // Create future
                final FutureImpl<ReadResult<Buffer, SocketAddress>> future =
                        SafeFutureImpl.create();
                queueRecord.setFuture(future);

                connectionQueue.getQueue().offer(queueRecord);

                if (currentElement.compareAndSet(null, queueRecord)) { // if queue became empty
                    // set this element as current and remove it from a queue
                    if (queue.remove(queueRecord)) {
                        onReadyToRead(connection);
                    }
                }

                // Check whether connection is still open
                if (!connection.isOpen() && queue.remove(queueRecord)) {
                    onReadFailure(connection, queueRecord,
                            new EOFException("Connection is closed"));
                }

                return future;            }
        } catch (IOException e) {
            onReadFailure(connection, queueRecord, e);
            return ReadyFutureImpl.create(e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final boolean isReady(final Connection connection) {
        final TaskQueue connectionQueue =
                ((NIOConnection) connection).getAsyncReadQueue();

        return connectionQueue != null
                && (connectionQueue.getCurrentElement() != null
                || (connectionQueue.getQueue() != null
                && !connectionQueue.getQueue().isEmpty()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void processAsync(final Connection connection) throws IOException {
        final TaskQueue<AsyncReadQueueRecord> connectionQueue =
                ((NIOConnection) connection).getAsyncReadQueue();

        final Queue<AsyncReadQueueRecord> queue = connectionQueue.getQueue();
        final AtomicReference<AsyncReadQueueRecord> currentElement =
                connectionQueue.getCurrentElementAtomic();

        AsyncReadQueueRecord queueRecord = currentElement.get();
        if (queueRecord == LOCK_RECORD) return;

        try {
            while (queueRecord != null) {
                final ReadResult currentResult = queueRecord.getCurrentResult();
                doRead(connection, queueRecord);

                final Interceptor<ReadResult> interceptor =
                        queueRecord.getInterceptor();
                // check if message was completely read
                final int interceptInstructions = intercept(
                        Reader.READ_EVENT, queueRecord,
                        currentResult);

                if ((interceptInstructions & Interceptor.COMPLETED) != 0
                        || (interceptor == null && isFinished(queueRecord))) {

                    AsyncReadQueueRecord nextRecord = queue.poll();
                    currentElement.set(nextRecord);

                    onReadComplete(queueRecord);

                    intercept(Reader.COMPLETE_EVENT,
                            queueRecord, null);
                    queueRecord.recycle();

                    queueRecord = nextRecord;
                    // If last element in queue is null - we have to be careful
                    if (queueRecord == null) {
                        queueRecord = queue.peek();
                        if (queueRecord != null &&
                                currentElement.compareAndSet(null, queueRecord)) {
                            if (!queue.remove(queueRecord)) { // if the record was picked up by another thread
                                break;
                            }
                        } else { // If there are no elements - return
                            break;
                        }
                    }
                } else { // if there is still some data in current message
                    if ((interceptInstructions & Interceptor.RESET) != 0) {
                        currentResult.setMessage(null);
                        currentResult.setReadSize(0);
                        queueRecord.setMessage(null);
                    }

                    onReadIncomplete(queueRecord);
                    intercept(Reader.INCOMPLETE_EVENT,
                            queueRecord, null);

                    onReadyToRead(connection);
                    break;
                }
            }
        } catch (IOException e) {
            onReadFailure(connection, queueRecord, e);
        } catch (Exception e) {
            String message = "Unexpected exception occurred in AsyncQueueReader";
            LOGGER.log(Level.SEVERE, message, e);
            IOException ioe = new IOException(e.getClass() + ": " + message);
            onReadFailure(connection, queueRecord, ioe);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onClose(Connection connection) {
        final NIOConnection nioConnection =
                (NIOConnection) connection;
        final TaskQueue<AsyncReadQueueRecord> readQueue =
                nioConnection.getAsyncReadQueue();

        if (readQueue != null) {
            AsyncReadQueueRecord record =
                    readQueue.getCurrentElementAtomic().getAndSet(LOCK_RECORD);

            EOFException error = cachedEOFException;
            if (error == null) {
                error = new EOFException("Connection closed");
                cachedEOFException = error;
            }

            if (record != LOCK_RECORD) {
                failReadRecord(record, error);
            }

            final Queue<AsyncReadQueueRecord> recordsQueue =
                    readQueue.getQueue();
            if (recordsQueue != null) {
                while ((record = recordsQueue.poll()) != null) {
                    failReadRecord(record, error);
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

    /**
     * Performs real read on the NIO channel
     * 
     * @param connection the {@link Connection} to read from
     * @param queueRecord the record to be read to
     * @throws java.io.IOException
     */
    final protected int doRead(final Connection connection,
            final AsyncReadQueueRecord queueRecord) throws IOException {

        final Object message = queueRecord.getMessage();

        final Buffer buffer = (Buffer) message;
        final ReadResult currentResult = queueRecord.getCurrentResult();

        final int readBytes = read0(connection, buffer, currentResult);

        if (readBytes == -1) {
            throw new EOFException();
        }

        return readBytes;
    }

    protected final void onReadComplete(AsyncReadQueueRecord record)
            throws IOException {

        final ReadResult currentResult = record.getCurrentResult();
        final FutureImpl future = (FutureImpl) record.getFuture();
        if (future != null) {
            future.result(currentResult);
        }

        final CompletionHandler<ReadResult> completionHandler =
                record.getCompletionHandler();

        if (completionHandler != null) {
            completionHandler.completed(currentResult);
        }
    }

    protected final void onReadIncomplete(AsyncReadQueueRecord record)
            throws IOException {

        final ReadResult currentResult = record.getCurrentResult();
        final CompletionHandler<ReadResult> completionHandler =
                record.getCompletionHandler();

        if (completionHandler != null) {
            completionHandler.updated(currentResult);
        }
    }

    protected final void onReadFailure(Connection connection,
            AsyncReadQueueRecord failedRecord, IOException e) {

        failReadRecord(failedRecord, e);
        try {
            connection.close().markForRecycle(true);
        } catch (IOException ignored) {
        }
    }

    protected final void failReadRecord(AsyncReadQueueRecord record, Throwable e) {
        if (record == null) {
            return;
        }

        final FutureImpl future = (FutureImpl) record.getFuture();
        final boolean hasFuture = (future != null);

        if (!hasFuture || !future.isDone()) {
            CompletionHandler<ReadResult> completionHandler =
                    record.getCompletionHandler();

            if (completionHandler != null) {
                completionHandler.failed(e);
            }

            if (hasFuture) {
                future.failure(e);
            }
        }
    }

    private int intercept(final int event,
                          final AsyncReadQueueRecord asyncQueueRecord,
                          final ReadResult currentResult) {
        final Interceptor<ReadResult> interceptor = asyncQueueRecord.getInterceptor();
        if (interceptor != null) {
            return interceptor.intercept(event, asyncQueueRecord, currentResult);
        }

        return Interceptor.DEFAULT;
    }

    private <E> boolean isFinished(final AsyncReadQueueRecord queueRecord) {

        final ReadResult readResult = queueRecord.getCurrentResult();
        final Object message = readResult.getMessage();

        return readResult.getReadSize() > 0
                || !((Buffer) message).hasRemaining();
    }

    protected abstract int read0(Connection connection, Buffer buffer,
            ReadResult<Buffer, SocketAddress> currentResult) throws IOException;

    protected abstract void onReadyToRead(Connection connection)
            throws IOException;
}
