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
import java.io.EOFException;
import java.io.IOException;
import java.net.SocketAddress;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import com.sun.grizzly.AbstractReader;
import com.sun.grizzly.Buffer;
import com.sun.grizzly.CompletionHandler;
import com.sun.grizzly.Connection;
import com.sun.grizzly.IOEvent;
import com.sun.grizzly.Context;
import com.sun.grizzly.Grizzly;
import com.sun.grizzly.Interceptor;
import com.sun.grizzly.ProcessorResult;
import com.sun.grizzly.ReadResult;
import com.sun.grizzly.Reader;
import com.sun.grizzly.TransformationResult;
import com.sun.grizzly.asyncqueue.AsyncQueue;
import com.sun.grizzly.asyncqueue.AsyncQueueProcessor;
import com.sun.grizzly.asyncqueue.AsyncQueueReader;
import com.sun.grizzly.asyncqueue.AsyncReadQueueRecord;
import com.sun.grizzly.impl.FutureImpl;
import com.sun.grizzly.memory.ByteBuffersBuffer;
import com.sun.grizzly.memory.CompositeBuffer;
import com.sun.grizzly.utils.ObjectPool;
import java.util.Queue;

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
    private Logger logger = Grizzly.logger(AbstractNIOAsyncQueueReader.class);
    
    public AbstractNIOAsyncQueueReader(NIOTransport transport) {
        this.transport = transport;

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <M> Future<ReadResult<M, SocketAddress>> read(
            final Connection connection, M message,
            final CompletionHandler<ReadResult<M, SocketAddress>> completionHandler,
            final Transformer<Buffer, M> transformer,
            final Interceptor<ReadResult> interceptor) throws IOException {

        if (connection == null) {
            throw new IOException("Connection is null");
        } else if (!connection.isOpen()) {
            throw new IOException("Connection is closed");
        }

        int finalInterceptorEvent;

        // Create future
        final FutureImpl<ReadResult<M, SocketAddress>> future =
                new FutureImpl<ReadResult<M, SocketAddress>>();
        final ReadResult currentResult = new ReadResult(connection);
        currentResult.setMessage(null);
        currentResult.setReadSize(0);

        // Get connection async read queue
        final AsyncQueue<AsyncReadQueueRecord> connectionQueue =
                ((AbstractNIOConnection) connection).getAsyncReadQueue();

        final Queue<AsyncReadQueueRecord> queue = connectionQueue.getQueue();
        final AtomicReference<AsyncReadQueueRecord> currentElement =
                connectionQueue.getCurrentElementAtomic();
        final ReentrantLock lock = connectionQueue.getQueuedActionLock();
        boolean isLockedByMe = false;

        // create and initialize the read queue record
        final AsyncReadQueueRecord queueRecord = new AsyncReadQueueRecord(
                message, future, currentResult, completionHandler,
                transformer, interceptor);

        // If AsyncQueue is empty - try to read Buffer here
        try {

            if (currentElement.get() == null && // Weak comparison for null
                    lock.tryLock()) {
                isLockedByMe = true;
                // Strong comparison for null, because we're in locked region
                if (currentElement.compareAndSet(null, queueRecord)) {
                    doRead(connection, queueRecord);
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
                    (interceptor == null && isFinished(connection, queueRecord))) {
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
                    currentResult.setMessage(null);
                    currentResult.setReadSize(0);
                    queueRecord.setMessage(null);
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
    @Override
    public boolean isReady(final Connection connection) {
        AsyncQueue connectionQueue =
                ((AbstractNIOConnection) connection).getAsyncReadQueue();

        return connectionQueue != null &&
                (connectionQueue.getCurrentElement() != null ||
                (connectionQueue.getQueue() != null &&
                !connectionQueue.getQueue().isEmpty()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void processAsync(final Connection connection) throws IOException {
        final AsyncQueue connectionQueue =
                ((AbstractNIOConnection) connection).getAsyncReadQueue();

        final Queue<AsyncReadQueueRecord> queue =
                connectionQueue.getQueue();
        final AtomicReference<AsyncReadQueueRecord> currentElement =
                connectionQueue.getCurrentElementAtomic();
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
                doRead(connection, queueRecord);

                final Interceptor<ReadResult> interceptor =
                        queueRecord.getInterceptor();
                // check if message was completely read
                final int interceptInstructions = intercept(connection,
                        Reader.READ_EVENT, queueRecord,
                        currentResult);
                
                final boolean registerForReadingInstr = interceptor == null ||
                        (interceptInstructions & AsyncQueueProcessor.NOT_REGISTER_KEY) == 0;

                if ((interceptInstructions & Interceptor.COMPLETED) != 0 ||
                        (interceptor == null && isFinished(connection, queueRecord))) {
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
                        currentResult.setMessage(null);
                        currentResult.setReadSize(0);
                        queueRecord.setMessage(null);
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
    @Override
    public void onClose(Connection connection) {
        final AbstractNIOConnection nioConnection = (AbstractNIOConnection) connection;
        final AsyncQueue<AsyncReadQueueRecord> readQueue =
                nioConnection.getAsyncReadQueue();

        if (readQueue != null) {
            readQueue.getQueuedActionLock().lock();
            try {
                AsyncReadQueueRecord record =
                        readQueue.getCurrentElementAtomic().getAndSet(null);

                failReadRecord(connection, record,
                        new IOException("Connection closed"));

                final Queue<AsyncReadQueueRecord> recordsQueue =
                        readQueue.getQueue();
                while (!recordsQueue.isEmpty()) {
                    failReadRecord(connection, recordsQueue.poll(),
                            new IOException("Connection closed"));
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
    @Override
    public boolean isInterested(IOEvent ioEvent) {
        return ioEvent == IOEvent.READ;
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
     * Performs real read on the NIO channel
     * 
     * @param connection the {@link Connection} to read from
     * @param readFuture the asynchronous operation result holder
     * @param message the message to read to
     * @throws java.io.IOException
     */
    final protected int doRead(final Connection connection,
            final AsyncReadQueueRecord queueRecord) throws IOException {
        
        final Transformer transformer = queueRecord.getTransformer();
        final Object message = queueRecord.getMessage();
        
        if (transformer == null) {
            final Buffer buffer = (Buffer) message;
            final ReadResult currentResult = queueRecord.getCurrentResult();

            final int readBytes = read0(connection, buffer, currentResult);
            
            if (readBytes == -1) {
                throw new EOFException();
            }

            return readBytes;
        } else {
            final ReadResult readResult = new ReadResult(connection);
            final int readBytes = read0(connection, null, readResult);

            if (readBytes > 0) {
                final ReadResult currentResult = queueRecord.getCurrentResult();
                currentResult.setReadSize(currentResult.getReadSize() + readBytes);

                Buffer buffer = (Buffer) readResult.getMessage();
                buffer.trim();

                final Buffer remainderBuffer = queueRecord.getRemainderBuffer();
                if (remainderBuffer != null) {
                    queueRecord.setRemainderBuffer(null);
                    
                    if (remainderBuffer.isComposite()) {
                        ((CompositeBuffer) remainderBuffer).append(buffer);
                        buffer = remainderBuffer;
                    } else {
                        final CompositeBuffer compositeBuffer =
                                new ByteBuffersBuffer(transport.getMemoryManager(),
                                remainderBuffer.toByteBuffer(),
                                buffer.toByteBuffer());
                        compositeBuffer.allowBufferDispose(true);
                    }
                }
                transformer.setOutput(connection, message);

                do {
                    final TransformationResult tResult = transformer.transform(
                            connection, buffer);

                    if (tResult.getStatus() == TransformationResult.Status.COMPLETED) {
                        currentResult.setMessage(tResult.getMessage());

                        final Buffer remainder = (Buffer) tResult.getExternalRemainder();
                        final boolean hasRemaining = transformer.hasInputRemaining(remainder);
                        if (hasRemaining) {
                            buffer.trimRegion();
                            queueRecord.setRemainderBuffer(buffer);
                            queueRecord.setMessage(remainder);
                        } else if (buffer != null && !tResult.hasInternalRemainder()) {
                            buffer.dispose();
                        }

                        return readBytes;
                    } else if (tResult.getStatus() == TransformationResult.Status.INCOMPLETED) {
                        final Buffer remainder = (Buffer) tResult.getExternalRemainder();
                        final boolean hasRemaining = transformer.hasInputRemaining(remainder);

                        if (hasRemaining) {
                            remainderBuffer.trimRegion();
                            queueRecord.setRemainderBuffer(remainderBuffer);
                        } else if (buffer != null && !tResult.hasInternalRemainder()) {
                            buffer.dispose();
                        }

                        if (!tResult.hasInternalRemainder()) {
                            return readBytes;
                        }

                        buffer = remainder;
                    } else if (tResult.getStatus() == TransformationResult.Status.ERROR) {
                        throw new IOException("Transformation exception ("
                                + tResult.getErrorCode() + "): "
                                + tResult.getErrorDescription());
                    }
                } while (true);
            } else if (readBytes == -1) {
                throw new EOFException();
            }

            return readBytes;
        }
    }


    protected void onReadCompleted(Connection connection,
            AsyncReadQueueRecord record)
            throws IOException {

        final Transformer transformer = record.getTransformer();
        if (transformer != null) {
            transformer.release(connection);
        }

        FutureImpl future = (FutureImpl) record.getFuture();
        ReadResult currentResult = record.getCurrentResult();
        future.result(currentResult);
        CompletionHandler<ReadResult> completionHandler =
                record.getCompletionHandler();

        if (completionHandler != null) {
            completionHandler.completed(currentResult);
        }
    }

    protected void onReadIncompleted(Connection connection,
            AsyncReadQueueRecord record)
            throws IOException {

        ReadResult currentResult = record.getCurrentResult();
        CompletionHandler<ReadResult> completionHandler =
                record.getCompletionHandler();

        if (completionHandler != null) {
            completionHandler.updated(currentResult);
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
                completionHandler.failed(e);
            }

            future.failure(e);
        }
    }

    private int intercept(final Connection connection,
            final int event,
            final AsyncReadQueueRecord asyncQueueRecord,
            final ReadResult currentResult) {
        final Interceptor<ReadResult> interceptor = asyncQueueRecord.getInterceptor();
        if (interceptor != null) {
            return interceptor.intercept(event, asyncQueueRecord, currentResult);
        }

        return Interceptor.DEFAULT;
    }

    private <E> boolean isFinished(final Connection connection,
            final AsyncReadQueueRecord queueRecord) {

        final ReadResult readResult = queueRecord.getCurrentResult();
        final Object message = readResult.getMessage();
        final Transformer transformer = queueRecord.getTransformer();

        if (transformer == null) {
            return readResult.getReadSize() > 0
                    || !((Buffer) message).hasRemaining();
        } else {
            final TransformationResult tResult = transformer.getLastResult(connection);
            return tResult != null &&
                    tResult.getStatus() == TransformationResult.Status.COMPLETED;
        }
    }

    protected abstract int read0(Connection connection, Buffer buffer,
            ReadResult<Buffer, SocketAddress> currentResult)throws IOException;

    protected abstract void onReadyToRead(Connection connection)
            throws IOException;
}
