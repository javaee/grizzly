/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2014 Oracle and/or its affiliates. All rights reserved.
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
import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.grizzly.*;
import org.glassfish.grizzly.asyncqueue.AsyncQueueReader;
import org.glassfish.grizzly.asyncqueue.AsyncReadQueueRecord;
import org.glassfish.grizzly.asyncqueue.TaskQueue;

/**
 * The {@link AsyncQueueReader} implementation, based on the Java NIO
 * 
 * @author Alexey Stashok
 * @author Ryan Lubke
 * @author Gustav Trede
 */
@SuppressWarnings("unchecked")
public abstract class AbstractNIOAsyncQueueReader
        extends AbstractReader<SocketAddress>
        implements AsyncQueueReader<SocketAddress> {

    private static final Logger LOGGER = Grizzly.logger(AbstractNIOAsyncQueueReader.class);

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
    public void read(
            final Connection<SocketAddress> connection, Buffer buffer,
            final CompletionHandler<ReadResult<Buffer, SocketAddress>> completionHandler,
            final Interceptor<ReadResult> interceptor) {

        if (connection == null) {
            failure(new IOException("Connection is null"),
                    completionHandler);
            return;
        }

        if (!connection.isOpen()) {
            failure(new IOException("Connection is closed"),
                    completionHandler);
            return;
        }
        
        // Get connection async read queue
        final TaskQueue<AsyncReadQueueRecord> connectionQueue =
                ((NIOConnection) connection).getAsyncReadQueue();


        // create and initialize the read queue record
        final AsyncReadQueueRecord queueRecord = AsyncReadQueueRecord.create(
                connection, buffer, completionHandler, interceptor);

        final ReadResult<Buffer, SocketAddress> currentResult =
                queueRecord.getCurrentResult();
        
        final boolean isCurrent = (connectionQueue.reserveSpace(1) == 1);

        try {

            if (isCurrent) { // If AsyncQueue is empty - try to read Buffer here
                doRead(connection, queueRecord);

                final int interceptInstructions = intercept(
                        Reader.READ_EVENT, queueRecord, currentResult);

                if ((interceptInstructions & Interceptor.COMPLETED) != 0
                        || (interceptor == null && queueRecord.isFinished())) { // if direct read is completed

                    // If message was read directly - set next queue element as current
                    final boolean isQueueEmpty =
                        (connectionQueue.releaseSpaceAndNotify(1) == 0);

                    // Notify callback handler
                    queueRecord.notifyComplete();

                    if (!isQueueEmpty) {
                        onReadyToRead(connection);
                    }

                    intercept(COMPLETE_EVENT, queueRecord, null);
                    queueRecord.recycle();
                } else { // If direct read is not finished
                // Create future
                    if ((interceptInstructions & Interceptor.RESET) != 0) {
                        currentResult.setMessage(null);
                        currentResult.setReadSize(0);
                        queueRecord.setMessage(null);
                    }

                    connectionQueue.setCurrentElement(queueRecord);
                    
                    queueRecord.notifyIncomplete();
                    onReadyToRead(connection);

                    intercept(INCOMPLETE_EVENT, queueRecord, null);
                }

            } else { // Read queue is not empty - add new element to a queue
                connectionQueue.offer(queueRecord);

                // Check whether connection is still open
                if (!connection.isOpen() && connectionQueue.remove(queueRecord)) {
                    onReadFailure(connection, queueRecord,
                            new EOFException("Connection is closed"));
                }
            }
        } catch (IOException e) {
            onReadFailure(connection, queueRecord, e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final boolean isReady(final Connection connection) {
        final TaskQueue connectionQueue =
                ((NIOConnection) connection).getAsyncReadQueue();

        return connectionQueue != null && !connectionQueue.isEmpty();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AsyncResult processAsync(final Context context) {
        final NIOConnection nioConnection = (NIOConnection) context.getConnection();
        if (!nioConnection.isOpen()) {
            return AsyncResult.COMPLETE;
        }
        final TaskQueue<AsyncReadQueueRecord> connectionQueue =
                nioConnection.getAsyncReadQueue();


        boolean done = false;
        AsyncReadQueueRecord queueRecord = null;
        
        try {
            while ((queueRecord =
                    connectionQueue.poll()) != null) {

                final ReadResult currentResult = queueRecord.getCurrentResult();
                doRead(nioConnection, queueRecord);

                final Interceptor<ReadResult> interceptor =
                        queueRecord.getInterceptor();
                // check if message was completely read
                final int interceptInstructions = intercept(
                        Reader.READ_EVENT, queueRecord,
                        currentResult);

                if ((interceptInstructions & Interceptor.COMPLETED) != 0
                        || (interceptor == null && queueRecord.isFinished())) {

                    // Is here a chance that queue becomes empty?
                    // If yes - we need to switch to manual io event processing
                    // mode to *disable READ interest for SameThreadStrategy*,
                    // so we don't get stuck, when other thread tried to add data
                    // to the queue.
                    if (!context.isManualIOEventControl() &&
                            connectionQueue.spaceInBytes() - 1 <= 0) {
                        context.setManualIOEventControl();
                    }

                    done = (connectionQueue.releaseSpaceAndNotify(1) == 0);

                    queueRecord.notifyComplete();

                    intercept(Reader.COMPLETE_EVENT, queueRecord, null);
                    queueRecord.recycle();

                    // check if there is ready element in the queue
                    if (done) {
                        break;
                    }
                } else { // if there is still some data in current message
                    if ((interceptInstructions & Interceptor.RESET) != 0) {
                        currentResult.setMessage(null);
                        currentResult.setReadSize(0);
                        queueRecord.setMessage(null);
                    }

                    connectionQueue.setCurrentElement(queueRecord);
                    queueRecord.notifyIncomplete();
                    intercept(Reader.INCOMPLETE_EVENT, queueRecord, null);

//                    onReadyToRead(nioConnection);
                    return AsyncResult.INCOMPLETE;
                }
            }

            if (!done) {
                // Counter shows there should be some elements in queue,
                // but seems write() method still didn't add them to a queue
                // so we can release the thread for now
//                onReadyToRead(nioConnection);
                return AsyncResult.EXPECTING_MORE;
            }
        } catch (IOException e) {
            onReadFailure(nioConnection, queueRecord, e);
        } catch (Exception e) {
            String message = "Unexpected exception occurred in AsyncQueueReader";
            LOGGER.log(Level.SEVERE, message, e);
            IOException ioe = new IOException(e.getClass() + ": " + message);
            onReadFailure(nioConnection, queueRecord, ioe);
        }

        return AsyncResult.COMPLETE;
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

        if (!readQueue.isEmpty()) {
            EOFException error = cachedEOFException;
            if (error == null) {
                error = new EOFException("Connection closed");
                cachedEOFException = error;
            }
            AsyncReadQueueRecord record;
            while ((record = readQueue.poll()) != null) {
                record.notifyFailure(error);
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

    protected final void onReadFailure(final Connection connection,
            final AsyncReadQueueRecord failedRecord, final IOException e) {

        if (failedRecord != null) {
            failedRecord.notifyFailure(e);
        }
        
        connection.closeSilently();
    }

    private static void failure(
            final Throwable failure,
            final CompletionHandler<ReadResult<Buffer, SocketAddress>> completionHandler) {
        if (completionHandler != null) {
            completionHandler.failed(failure);
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

    protected abstract int read0(Connection connection, Buffer buffer,
            ReadResult<Buffer, SocketAddress> currentResult) throws IOException;

    protected abstract void onReadyToRead(Connection connection)
            throws IOException;
}
