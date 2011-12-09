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
import org.glassfish.grizzly.asyncqueue.WriteQueueMessage;
import org.glassfish.grizzly.impl.ReadyFutureImpl;
import org.glassfish.grizzly.impl.SafeFutureImpl;

import java.util.concurrent.Future;
import java.util.logging.Level;
import org.glassfish.grizzly.Context;
import org.glassfish.grizzly.IOEvent;


/**
 * The {@link AsyncQueueWriter} implementation, based on the Java NIO,
 * which performs better for usecases, when we send messages
 * simultaneously from different threads.
 * 
 * @author Alexey Stashok
 * @author Ryan Lubke
 * @author Gustav Trede
 */
@SuppressWarnings("unchecked")
public abstract class AbstractNIOMultiplexingAsyncQueueWriter
        extends AbstractWriter<SocketAddress>
        implements AsyncQueueWriter<SocketAddress> {

    private final static Logger logger = Grizzly.logger(AbstractNIOMultiplexingAsyncQueueWriter.class);

    protected final static int EMPTY_RECORD_SPACE_VALUE = 1;

    protected final NIOTransport transport;

    protected volatile int maxPendingBytes = -1;

    protected volatile int maxWriteReentrants = 10;
    
    // Cached IOException to throw from onClose()
    // Probably we shouldn't even care it's not volatile
    private IOException cachedIOException;

    private final static Reentrant DUMMY_REENTRANT = new Reentrant();
    
    public AbstractNIOMultiplexingAsyncQueueWriter(NIOTransport transport) {
        this.transport = transport;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public boolean canWrite(final Connection connection, final int size) {
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
        this.maxPendingBytes = maxPendingBytes <= 0 ? -1 : maxPendingBytes;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getMaxPendingBytesPerConnection() {
        return maxPendingBytes;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getMaxWriteReentrants() {
        return maxWriteReentrants;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setMaxWriteReentrants(int maxWriteReentrants) {
        this.maxWriteReentrants = maxWriteReentrants;
    }

    @Override
    public GrizzlyFuture<WriteResult<WriteQueueMessage, SocketAddress>> write(
            final Connection connection, SocketAddress dstAddress,
            final WriteQueueMessage message,
            final CompletionHandler<WriteResult<WriteQueueMessage, SocketAddress>> completionHandler,
            final Interceptor<WriteResult<WriteQueueMessage, SocketAddress>> interceptor)
            throws IOException {
        return write(connection, dstAddress, message, completionHandler,
                interceptor, null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public GrizzlyFuture<WriteResult<WriteQueueMessage, SocketAddress>> write(
            final Connection connection, final SocketAddress dstAddress,
            final WriteQueueMessage message,
            final CompletionHandler<WriteResult<WriteQueueMessage, SocketAddress>> completionHandler,
            final Interceptor<WriteResult<WriteQueueMessage, SocketAddress>> interceptor,
            final MessageCloner<WriteQueueMessage> cloner) throws IOException {
        
        if (connection == null) {
            return failure(new IOException("Connection is null"),
                    completionHandler);
        }

        if (!connection.isOpen()) {
            return failure(new IOException("Connection is closed"),
                    completionHandler);
        }
        
        final NIOConnection nioConnection = (NIOConnection) connection;

        // Get connection async write queue
        final TaskQueue<AsyncWriteQueueRecord> connectionQueue =
                nioConnection.getAsyncWriteQueue();


        final WriteResult<WriteQueueMessage, SocketAddress> currentResult =
                WriteResult.create(nioConnection, message, dstAddress, 0);
        
        final int bufferSize = message.remaining();
        final boolean isEmptyRecord = bufferSize == 0 ||
                !message.reserveQueueSpace();

        final SafeFutureImpl<WriteResult<WriteQueueMessage, SocketAddress>> future =
                SafeFutureImpl.create();

        // create and initialize the write queue record
        final AsyncWriteQueueRecord queueRecord = createRecord(
                nioConnection, message, future, currentResult, completionHandler,
                dstAddress, isEmptyRecord);

        // For empty buffer reserve 1 byte space        
        final int bytesToReserve = isEmptyRecord 
                                 ? EMPTY_RECORD_SPACE_VALUE 
                                 : bufferSize;

        final boolean isLogFine = logger.isLoggable(Level.FINEST);


        if (cloner != null) {
            if (isLogFine) {
                logger.log(Level.FINEST,
                        "AsyncQueueWriter.write clone. connection={0}",
                        nioConnection);
            }

            queueRecord.setMessage(cloner.clone(nioConnection, message));
        }

        connectionQueue.offer(queueRecord);
        
        
        final int pendingBytes = connectionQueue.reserveSpace(bytesToReserve);        
        final boolean isCurrent = (pendingBytes == bytesToReserve);
        if (isLogFine) {
            doFineLog("AsyncQueueWriter.write connection={0} record={1} directWrite={2}",
                    connection, queueRecord, isCurrent);
        }

        try {
            // Check if the buffer size matches maxPendingBytes
            if (maxPendingBytes > 0 && pendingBytes > maxPendingBytes &&
                    connectionQueue.remove(queueRecord)) {
                connectionQueue.releaseSpace(bytesToReserve);
                throw new PendingWriteQueueLimitExceededException(
                        "Max queued data limit exceeded: "
                        + pendingBytes + '>' + maxPendingBytes);
            }

            if (!nioConnection.isOpen() && connectionQueue.remove(queueRecord)) {
                onWriteFailure(nioConnection, queueRecord, new IOException("Connection is closed"));
            }                

            if (isCurrent) { //current but not finished.                
                nioConnection.simulateIOEvent(IOEvent.WRITE);
            }

            return future;
            
        } catch (IOException e) {
            if (isLogFine) {
                logger.log(Level.FINEST, "AsyncQueueWriter.write exception. connection=" + nioConnection + " record=" + queueRecord, e);
            }
            
            onWriteFailure(nioConnection, queueRecord, e);
            return ReadyFutureImpl.create(e);
        }
    }

    protected AsyncWriteQueueRecord createRecord(final Connection connection,
            final WriteQueueMessage message,
            final Future<WriteResult<WriteQueueMessage, SocketAddress>> future,
            final WriteResult<WriteQueueMessage, SocketAddress> currentResult,
            final CompletionHandler<WriteResult<WriteQueueMessage, SocketAddress>> completionHandler,
            final SocketAddress dstAddress,
            final boolean isEmptyRecord) {
        return AsyncWriteQueueRecord.create(connection, message, future,
                currentResult, completionHandler, dstAddress, isEmptyRecord);
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
    public AsyncResult processAsync(final Context context) throws IOException {
        final boolean isLogFine = logger.isLoggable(Level.FINEST);
        final NIOConnection nioConnection = (NIOConnection) context.getConnection();
        
        final TaskQueue<AsyncWriteQueueRecord> connectionQueue =
                nioConnection.getAsyncWriteQueue();
                
        boolean done = false;
        AsyncWriteQueueRecord queueRecord = null;

        try {
            while ((queueRecord = aggregate(connectionQueue)) != null) {

                if (isLogFine) {
                    doFineLog("AsyncQueueWriter.processAsync doWrite"
                            + "connection={0} record={1}",
                            nioConnection, queueRecord);
                }                 

                final int written = queueRecord.remaining() > 0 ?
                        (int) write0(nioConnection, queueRecord) :
                        0;
                
                final boolean isFinished = queueRecord.isFinished();

                // If we can write directly - do it w/o creating queue record (simple)
                final int bytesToRelease = !queueRecord.isEmptyRecord() ?
                        written :
                        (isFinished ? EMPTY_RECORD_SPACE_VALUE : 0);

                if (isFinished) {
                    // Is here a chance that queue becomes empty?
                    // If yes - we need to switch to manual io event processing
                    // mode to *disable WRITE interest for SameThreadStrategy*,
                    // so we don't have either neverending WRITE events processing
                    // or stuck, when other thread tried to add data to the queue.
                    if (!context.isManualIOEventControl() &&
                            connectionQueue.spaceInBytes() - bytesToRelease <= 0) {
                        context.setManualIOEventControl();
                    }
                }
                
                done = (connectionQueue.releaseSpaceAndNotify(bytesToRelease) == 0);
                if (isFinished) {
                    if (isLogFine) {
                        doFineLog("AsyncQueueWriter.processAsync finished "
                                + "connection={0} record={1}",
                                nioConnection, queueRecord);
                    }
                    // Do compareAndSet, because connection might have been close
                    // from another thread, and failReadRecord has been invoked already
                    queueRecord.notifyCompleteAndRecycle();
                    if (isLogFine) {
                        doFineLog("AsyncQueueWriter.processAsync nextRecord "
                                + "connection={0} nextRecord={1}",
                                nioConnection, queueRecord);
                    }
                    if (done) {
                        return AsyncResult.COMPLETE;
                    }
                } else { // if there is still some data in current message
                    connectionQueue.setCurrentElement(queueRecord);
                    if (isLogFine) {
                        doFineLog("AsyncQueueWriter.processAsync onReadyToWrite "
                                + "connection={0} peekRecord={1}",
                                nioConnection, queueRecord);
                    }

                    // If connection is closed - this will fail,
                    // and onWriteFailure called properly
                    return AsyncResult.INCOMPLETE;
                }
            }

            if (!done && connectionQueue.spaceInBytes() > 0) {
                // Counter shows there should be some elements in queue,
                // but seems write() method still didn't add them to a queue
                // so we can release the thread for now
                return AsyncResult.EXPECTING_MORE;
            }
        } catch (IOException e) {
            if (isLogFine) {
                logger.log(Level.FINEST, "AsyncQueueWriter.processAsync "
                        + "exception connection=" + nioConnection + " peekRecord=" +
                        queueRecord, e);
            }
            onWriteFailure(nioConnection, queueRecord, e);
        }

        return AsyncResult.COMPLETE;
    }
       
    private static void doFineLog(String msg, Object... params) {
        logger.log(Level.FINEST, msg, params);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onClose(final Connection connection) {
        final NIOConnection nioConnection =
                (NIOConnection) connection;
        final TaskQueue<AsyncWriteQueueRecord> writeQueue =
                nioConnection.getAsyncWriteQueue();
        if (!writeQueue.isEmpty()) {
            IOException error = cachedIOException;
            if (error == null) {
                error = new IOException("Connection closed");
                cachedIOException = error;
            }
            
            AsyncWriteQueueRecord record;
            while ((record = writeQueue.obtainCurrentElementAndReserve()) != null) {
                record.notifyFailure(error);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Reentrant getWriteReentrant() {
        return DUMMY_REENTRANT;
        }

    @Override
    public boolean isMaxReentrantsReached(final Reentrant reentrant) {
        return reentrant.get() >= getMaxWriteReentrants();
    }

    
    /**
     * {@inheritDoc}
     */
    @Override
    public final void close() {
    }
    
    protected static void onWriteFailure(final Connection connection,
            final AsyncWriteQueueRecord failedRecord, final IOException e) {

        failedRecord.notifyFailure(e);
        try {
            connection.close().markForRecycle(true);
        } catch (IOException ignored) {
        }
    }
    
    private static GrizzlyFuture<WriteResult<WriteQueueMessage, SocketAddress>> failure(
            final Throwable failure,
            final CompletionHandler<WriteResult<WriteQueueMessage, SocketAddress>> completionHandler) {
        if (completionHandler != null) {
            completionHandler.failed(failure);
        }
        
        return ReadyFutureImpl.create(failure);
    }
    
    protected abstract long write0(final NIOConnection connection,
            final AsyncWriteQueueRecord queueRecord)
            throws IOException;

    protected abstract void onReadyToWrite(NIOConnection connection)
            throws IOException;

    protected AsyncWriteQueueRecord aggregate(TaskQueue<AsyncWriteQueueRecord> connectionQueue) {
        return connectionQueue.obtainCurrentElementAndReserve();
    }

}