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

import java.io.EOFException;
import java.io.IOException;
import java.net.SocketAddress;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.grizzly.AbstractReader;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.CompletionHandler;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.IOEvent;
import org.glassfish.grizzly.Context;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.Interceptor;
import org.glassfish.grizzly.ProcessorResult;
import org.glassfish.grizzly.ReadResult;
import org.glassfish.grizzly.Reader;
import org.glassfish.grizzly.asyncqueue.AsyncQueue;
import org.glassfish.grizzly.asyncqueue.AsyncQueueProcessor;
import org.glassfish.grizzly.asyncqueue.AsyncQueueReader;
import org.glassfish.grizzly.asyncqueue.AsyncReadQueueRecord;
import org.glassfish.grizzly.impl.FutureImpl;
import org.glassfish.grizzly.util.LinkedTransferQueue;
import org.glassfish.grizzly.util.ObjectPool;

/**
 * The {@link AsyncQueueReader} implementation, based on the Java NIO
 * 
 * @author Alexey Stashok
 */
public abstract class AbstractNIOAsyncQueueReader
        extends AbstractReader<SocketAddress>
        implements AsyncQueueReader<SocketAddress> {

    public static final int DEFAULT_BUFFER_SIZE = 8192;

    protected int defaultBufferSize = DEFAULT_BUFFER_SIZE;

    protected NIOTransport transport;
    private Logger logger = Grizzly.logger;
    
    public AbstractNIOAsyncQueueReader(NIOTransport transport) {
        this.transport = transport;

    }

    /**
     * {@inheritDoc}
     */
    public Future<ReadResult<Buffer, SocketAddress>> read(
            final Connection connection,
            final Buffer buffer,
            final CompletionHandler<ReadResult<Buffer, SocketAddress>> completionHandler,
            final Interceptor<ReadResult> interceptor)
            throws IOException {
        
        if (connection == null) {
            throw new IOException("Connection is null");
        } else if (!connection.isOpen()) {
            throw new IOException("Connection is closed");
        }

        int finalInterceptorEvent;

        // Create future
        final FutureImpl<ReadResult<Buffer, SocketAddress>> future =
                new FutureImpl<ReadResult<Buffer, SocketAddress>>();
        final ReadResult currentResult = new ReadResult(connection);
        currentResult.setMessage(null);
        currentResult.setReadSize(0);

        // Get connection async read queue
        final AsyncQueue<AsyncReadQueueRecord> connectionQueue =
                ((AbstractNIOConnection) connection).obtainAsyncReadQueue();

        final LinkedTransferQueue<AsyncReadQueueRecord> queue =
                connectionQueue.getQueue();
        final AtomicReference<AsyncReadQueueRecord> currentElement =
                connectionQueue.getCurrentElement();
        final ReentrantLock lock = connectionQueue.getQueuedActionLock();
        boolean isLockedByMe = false;

        // create and initialize the read queue record
        final AsyncReadQueueRecord queueRecord = new AsyncReadQueueRecord();
        queueRecord.set(buffer, future, currentResult, completionHandler,
                interceptor);

        // If AsyncQueue is empty - try to read Buffer here
        try {

            if (currentElement.get() == null && // Weak comparison for null
                    lock.tryLock()) {
                isLockedByMe = true;
                // Strong comparison for null, because we're in locked region
                if (currentElement.compareAndSet(null, queueRecord)) {
                    doRead(connection, currentResult, buffer);
                } else {
                    isLockedByMe = false;
                    lock.unlock();
                }
            }

            final int interceptInstructions = intercept(connection,
                    Reader.READ_EVENT, queueRecord,
                    currentResult);

            final boolean registerForReadingInstr = interceptor == null ||
                    (interceptInstructions & AsyncQueueProcessor.NOT_REGISTER_KEY) == 0;

            if ((interceptInstructions & Interceptor.COMPLETED) != 0 ||
                    (interceptor == null && isFinished(currentResult))) {
                // If message was written directly - set next queue element as current
                if (isLockedByMe) {
                    AsyncReadQueueRecord nextRecord = queue.poll();
                    if (nextRecord != null) { // if there is something in queue
                        currentElement.set(nextRecord);
                        lock.unlock();
                        isLockedByMe = false;
                        if (registerForReadingInstr) {
                            onReadyToRead(connection);
                        }
                    } else { // if nothing in queue
                        currentElement.set(null);
                        lock.unlock();  // unlock
                        isLockedByMe = false;
                        if (registerForReadingInstr &&
                                queue.peek() != null) {  // check one more time
                            onReadyToRead(connection);
                        }
                    }
                }

                // Notify callback handler
                onReadCompleted(connection, queueRecord);

                finalInterceptorEvent = Reader.COMPLETE_EVENT;
            } else { // If there are no bytes available for writing
                if ((interceptInstructions & Interceptor.RESET) != 0) {
                    queueRecord.setCurrentResult(new ReadResult(connection));
                    queueRecord.setBuffer(null);
                }

                boolean isRegisterForReading = false;

                // add new element to the queue, if it's not current
                if (currentElement.get() != queueRecord) {
                    queue.offer(queueRecord); // add to queue
                    if (!lock.isLocked()) {
                        isRegisterForReading = true;
                    }
                } else {  // if element was written direct (not fully written)
                    onReadIncompleted(connection, queueRecord);
                    isRegisterForReading = true;
                    if (isLockedByMe) {
                        isLockedByMe = false;
                        lock.unlock();
                    }
                }

                if (registerForReadingInstr && isRegisterForReading) {
                    onReadyToRead(connection);
                }

                finalInterceptorEvent = INCOMPLETE_EVENT;
            }
        } catch(IOException e) {
            onReadFailure(connection, queueRecord, e);
            throw e;
        } finally {
            if (isLockedByMe) {
                lock.unlock();
            }
        }

        intercept(connection, finalInterceptorEvent, queueRecord, null);
        return future;
    }
    
    /**
     * {@inheritDoc}
     */
    public boolean isReady(final Connection connection) {
        AsyncQueue connectionQueue =
                ((AbstractNIOConnection) connection).getAsyncReadQueue();

        return connectionQueue != null &&
                (connectionQueue.getCurrentElement().get() != null ||
                (connectionQueue.getQueue() != null &&
                !connectionQueue.getQueue().isEmpty()));
    }

    /**
     * {@inheritDoc}
     */
    public void processAsync(final Connection connection) throws IOException {
        final AsyncQueue<AsyncReadQueueRecord> connectionQueue =
                ((AbstractNIOConnection) connection).obtainAsyncReadQueue();

        final LinkedTransferQueue<AsyncReadQueueRecord> queue =
                connectionQueue.getQueue();
        final AtomicReference<AsyncReadQueueRecord> currentElement =
                connectionQueue.getCurrentElement();
        final ReentrantLock lock = connectionQueue.getQueuedActionLock();
        boolean isLockedByMe = false;

        if (currentElement.get() == null) {
            AsyncReadQueueRecord nextRecord = queue.peek();
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

        int finalInterceptorEvent = Reader.COMPLETE_EVENT;

        AsyncReadQueueRecord queueRecord = null;
        try {
            while (currentElement.get() != null) {
                queueRecord = currentElement.get();

                final ReadResult currentResult = queueRecord.getCurrentResult();
                final Buffer message = queueRecord.getBuffer();
                doRead(connection, currentResult, message);

                final Interceptor<ReadResult> interceptor =
                        queueRecord.getInterceptor();
                // check if message was completely read
                final int interceptInstructions = intercept(connection,
                        Reader.READ_EVENT, queueRecord,
                        currentResult);
                
                final boolean registerForReadingInstr = interceptor == null ||
                        (interceptInstructions & AsyncQueueProcessor.NOT_REGISTER_KEY) == 0;

                if ((interceptInstructions & Interceptor.COMPLETED) != 0 ||
                        (interceptor == null && isFinished(currentResult))) {
                    currentElement.set(queue.poll());
                    onReadCompleted(connection, queueRecord);

                    intercept(connection, Reader.COMPLETE_EVENT,
                            queueRecord, null);

                    // If last element in queue is null - we have to be careful
                    if (currentElement.get() == null) {
                        if (isLockedByMe) {
                            isLockedByMe = false;
                            lock.unlock();
                        }
                        AsyncReadQueueRecord nextRecord = queue.peek();
                        if (nextRecord != null && lock.tryLock()) {
                            isLockedByMe = true;
                            if (!queue.isEmpty() &&
                                    currentElement.compareAndSet(null, nextRecord)) {
                                queue.remove();
                            }
                            
                            continue;
                        } else {
                            break;
                        }
                    }
                } else { // if there is still some data in current message
                    if ((interceptInstructions & Interceptor.RESET) != 0) {
                        queueRecord.setCurrentResult(new ReadResult(connection));
                        queueRecord.setBuffer(null);
                    }

                    onReadIncompleted(connection, queueRecord);

                    if (isLockedByMe) {
                        isLockedByMe = false;
                        lock.unlock();
                    }
                    
                    if (registerForReadingInstr) {
                        onReadyToRead(connection);
                    }

                    finalInterceptorEvent = Reader.INCOMPLETE_EVENT;
                    break;
                }
            }
        } catch (IOException e) {
            onReadFailure(connection, queueRecord, e);
        } catch (Exception e) {
            String message = "Unexpected exception occurred in AsyncQueueReader";
            logger.log(Level.SEVERE, message, e);
            IOException ioe = new IOException(e.getClass() + ": " + message);
            onReadFailure(connection, queueRecord, ioe);
        } finally {
            if (isLockedByMe) {
                connectionQueue.getQueuedActionLock().unlock();
            }
        }

        if (finalInterceptorEvent == Reader.INCOMPLETE_EVENT) {
            intercept(connection, finalInterceptorEvent, queueRecord, null);
        }
    }

    /**
     * {@inheritDoc}
     */
    public void onClose(Connection connection) {
        AbstractNIOConnection nioConnection = (AbstractNIOConnection) connection;
        AsyncQueue<AsyncReadQueueRecord> readQueue =
                nioConnection.getAsyncReadQueue();
        if (readQueue != null) {
            readQueue.getQueuedActionLock().lock();
            try {
                AsyncReadQueueRecord record =
                        readQueue.getCurrentElement().getAndSet(null);

                failReadRecord(connection, record,
                        new IOException("Connection closed"));

                LinkedTransferQueue<AsyncReadQueueRecord> recordsQueue =
                        readQueue.getQueue();
                if (recordsQueue != null) {
                    while(!recordsQueue.isEmpty()) {
                        failReadRecord(connection, recordsQueue.poll(),
                                new IOException("Connection closed"));
                    }
                }
            } finally {
                readQueue.getQueuedActionLock().unlock();
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
        return ioEvent == IOEvent.READ;
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
     * Performs real read on the NIO channel
     * 
     * @param connection the {@link Connection} to read from
     * @param readFuture the asynchronous operation result holder
     * @param message the message to read to
     * @throws java.io.IOException
     */
    protected int doRead(final Connection connection,
            final ReadResult currentResult, final Buffer message)
            throws IOException {
        
        final Buffer buffer = (Buffer) message;
        final int readBytes = read0(connection, buffer, currentResult);
        if (readBytes == -1) {
            throw new EOFException();
        }

        return readBytes;
    }


    protected void onReadCompleted(Connection connection,
            AsyncReadQueueRecord record)
            throws IOException {

        FutureImpl future = (FutureImpl) record.getFuture();
        ReadResult currentResult = record.getCurrentResult();
        future.setResult(currentResult);
        CompletionHandler<ReadResult> completionHandler =
                record.getCompletionHandler();

        if (completionHandler != null) {
            completionHandler.completed(connection, currentResult);
        }
    }

    protected void onReadIncompleted(Connection connection,
            AsyncReadQueueRecord record)
            throws IOException {

        ReadResult currentResult = record.getCurrentResult();
        CompletionHandler<ReadResult> completionHandler =
                record.getCompletionHandler();

        if (completionHandler != null) {
            completionHandler.updated(connection, currentResult);
        }
    }

    protected void onReadFailure(Connection connection,
            AsyncReadQueueRecord failedRecord, IOException e) {

        failReadRecord(connection, failedRecord, e);
        try {
            connection.close();
        } catch (IOException ioe) {
        }
    }

    protected void failReadRecord(Connection connection,
            AsyncReadQueueRecord record, IOException e) {
        if (record == null) return;

        FutureImpl future = (FutureImpl) record.getFuture();
        if (!future.isDone()) {
            CompletionHandler<ReadResult> completionHandler =
                    record.getCompletionHandler();

            if (completionHandler != null) {
                completionHandler.failed(connection, e);
            }

            future.failure(e);
        }
    }

    private int intercept(Connection connection, int event,
            AsyncReadQueueRecord asyncQueueRecord, ReadResult currentResult) {
        Interceptor<ReadResult> interceptor = asyncQueueRecord.getInterceptor();
        if (interceptor != null) {
            return interceptor.intercept(event, asyncQueueRecord, currentResult);
        }

        return Interceptor.DEFAULT;
    }

    private <E> boolean isFinished(ReadResult<E, ?> readResult) {
        E message = readResult.getMessage();
        return readResult.getReadSize() > 0 ||
                !((Buffer) message).hasRemaining();
    }

    protected abstract int read0(Connection connection, Buffer buffer,
            ReadResult<Buffer, SocketAddress> currentResult)throws IOException;

    protected abstract void onReadyToRead(Connection connection)
            throws IOException;
}
