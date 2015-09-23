/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2015 Oracle and/or its affiliates. All rights reserved.
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

import org.glassfish.grizzly.asyncqueue.RecordWriteResult;
import java.io.IOException;
import java.net.SocketAddress;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.grizzly.*;
import org.glassfish.grizzly.asyncqueue.*;
import org.glassfish.grizzly.attributes.Attribute;
import org.glassfish.grizzly.attributes.NullaryFunction;
import org.glassfish.grizzly.threadpool.WorkerThread;


/**
 * The {@link AsyncQueueWriter} implementation, based on the Java NIO
 * 
 * @author Alexey Stashok
 * @author Ryan Lubke
 * @author Gustav Trede
 */
@SuppressWarnings("unchecked")
public abstract class AbstractNIOAsyncQueueWriter
        extends AbstractWriter<SocketAddress>
        implements AsyncQueueWriter<SocketAddress> {

    private final static Logger LOGGER = Grizzly.logger(AbstractNIOAsyncQueueWriter.class);

    private final ThreadLocal<Reentrant> REENTRANTS_COUNTER =
            new ThreadLocal<Reentrant>() {

        @Override
        protected Reentrant initialValue() {
            return new Reentrant();
        }
    };

    protected final NIOTransport transport;

    protected volatile int maxPendingBytes = AUTO_SIZE;

    protected volatile int maxWriteReentrants = 10;
    
    private volatile boolean isAllowDirectWrite = true;
    
    private static final Attribute<Reentrant> REENTRANTS_ATTR =
            Grizzly.DEFAULT_ATTRIBUTE_BUILDER.createAttribute(
            AbstractNIOAsyncQueueWriter.class.getName() + ".reentrant",
            new NullaryFunction<Reentrant>() {

                @Override
                public Reentrant evaluate() {
                    return new Reentrant();
                }
            });

    public AbstractNIOAsyncQueueWriter(NIOTransport transport) {
        this.transport = transport;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public boolean canWrite(final Connection connection, final int size) {
        final int connectionMaxPendingBytes = connection.getMaxAsyncWriteQueueSize();
        
        if (connectionMaxPendingBytes < 0) {
            return true;
        }
        final TaskQueue<AsyncWriteQueueRecord> connectionQueue =
                ((NIOConnection) connection).getAsyncWriteQueue();
        return connectionQueue.spaceInBytes() + size < connectionMaxPendingBytes;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void notifyWritePossible(final Connection connection,
            final WriteHandler writeHandler, final int size) {
        ((NIOConnection) connection).getAsyncWriteQueue()
                .notifyWritePossible(writeHandler, size);
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public void setMaxPendingBytesPerConnection(final int maxPendingBytes) {
        this.maxPendingBytes = maxPendingBytes < AUTO_SIZE ? AUTO_SIZE : maxPendingBytes;
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

    /**
     * Returns <tt>true</tt>, if async write queue is allowed to write buffer
     * directly during write(...) method call, w/o adding buffer to the
     * queue, or <tt>false</tt> otherwise.
     * 
     * @return <tt>true</tt>, if async write queue is allowed to write buffer
     * directly during write(...) method call, w/o adding buffer to the
     * queue, or <tt>false</tt> otherwise.
     */
    public boolean isAllowDirectWrite() {
        return isAllowDirectWrite;
    }

    /**
     * Set <tt>true</tt>, if async write queue is allowed to write buffer
     * directly during write(...) method call, w/o adding buffer to the
     * queue, or <tt>false</tt> otherwise.
     * 
     * @param isAllowDirectWrite  <tt>true</tt>, if async write queue is allowed
     * to write buffer directly during write(...) method call, w/o adding buffer
     * to the queue, or <tt>false</tt> otherwise.
     */
    public void setAllowDirectWrite(final boolean isAllowDirectWrite) {
        this.isAllowDirectWrite = isAllowDirectWrite;
    }
    
    @Override
    public void write(
            final Connection connection, SocketAddress dstAddress,
            final WritableMessage message,
            final CompletionHandler<WriteResult<WritableMessage, SocketAddress>> completionHandler,
            final PushBackHandler pushBackHandler) {
        write(connection, dstAddress, message, completionHandler,
                pushBackHandler, null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void write(
            final Connection connection, final SocketAddress dstAddress,
            final WritableMessage message,
            final CompletionHandler<WriteResult<WritableMessage, SocketAddress>> completionHandler,
            final PushBackHandler pushBackHandler,
            final MessageCloner<WritableMessage> cloner) {
        

        // create and initialize the write queue record
        final AsyncWriteQueueRecord queueRecord = createRecord(
                connection, message, completionHandler,
                dstAddress, pushBackHandler,
                !message.hasRemaining() || message.isExternal());
        
        writeQueueRecord(queueRecord, cloner, null);
    }

    protected void writeQueueRecord(final AsyncWriteQueueRecord queueRecord,
            final MessageCloner<WritableMessage> cloner,
            final PushBackContext pushBackContext) {
        
        final NIOConnection connection = (NIOConnection) queueRecord.getConnection();
        if (connection == null) {
            queueRecord.notifyFailure(new IOException("Connection is null"));
            return;
        }

        if (!connection.isOpen()) {
            onWriteFailure(connection, queueRecord,
                    new IOException("Connection is closed"));
            return;
        }
        
        // Get connection async write queue
        final TaskQueue<AsyncWriteQueueRecord> writeTaskQueue =
                connection.getAsyncWriteQueue();
        
        final WritableMessage message = queueRecord.getWritableMessage();
        // For empty buffer reserve 1 byte space        
        final int bytesToReserve = (int) queueRecord.getBytesToReserve();
        
        final int pendingBytes = writeTaskQueue.reserveSpace(bytesToReserve);
        final boolean isCurrent = (pendingBytes == bytesToReserve);
        queueRecord.setMomentumQueueSize(pendingBytes);

        final boolean isLogFine = LOGGER.isLoggable(Level.FINEST);

        if (isLogFine) {
            doFineLog("AsyncQueueWriter.write connection={0} record={1} directWrite={2}",
                    connection, queueRecord, isCurrent);
        }

        final Reentrant reentrants = getWriteReentrant();
        
        try {
            if (reentrants.incAndGet() >= maxWriteReentrants) {
                // Max number of reentrants is reached
                
                queueRecord.setMessage(
                        cloneRecordIfNeeded(connection, cloner, message));
                
                if (isCurrent) { //current but not finished.                
                    writeTaskQueue.setCurrentElement(queueRecord);
                    connection.simulateIOEvent(IOEvent.WRITE);
                } else {
                    offerToTaskQueue(connection, queueRecord, writeTaskQueue);
                }

                return;
            }

            if (!checkQueueSize(queueRecord, pushBackContext)) {
                assert !isCurrent;
                
                writeTaskQueue.getRefusedBytes().addAndGet(bytesToReserve);
                return;
            }
            
            if (isCurrent && isAllowDirectWrite) {
                
                // If we can write directly - do it w/o creating queue record (simple)
                final RecordWriteResult writeResult = write0(connection, queueRecord);
                final int bytesToRelease = (int) writeResult.bytesToReleaseAfterLastWrite();
                
                final boolean isFinished = queueRecord.isFinished();                
                
                final boolean isQueueEmpty =
                        (writeTaskQueue.releaseSpaceAndNotify(bytesToRelease) == 0);

                if (isFinished) {
                    queueRecord.notifyCompleteAndRecycle();
                    if (!isQueueEmpty) {
                        connection.simulateIOEvent(IOEvent.WRITE);
                    }
                    return;
                }
            }
            
            queueRecord.setMessage(
                    cloneRecordIfNeeded(connection, cloner, message));
            
            if (isCurrent) { //current but not finished.                
                writeTaskQueue.setCurrentElement(queueRecord);
                onReadyToWrite(connection);
            } else {
                offerToTaskQueue(connection, queueRecord, writeTaskQueue);
            }
        } catch (IOException e) {
            if (isLogFine) {
                LOGGER.log(Level.FINEST,
                        "AsyncQueueWriter.write exception. connection=" +
                        connection + " record=" + queueRecord, e);
            }
            
            onWriteFailure(connection, queueRecord, e);
        } finally {
            reentrants.decAndGet();
        }
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public AsyncResult processAsync(final Context context) {
        final boolean isLogFine = LOGGER.isLoggable(Level.FINEST);
        final NIOConnection nioConnection = (NIOConnection) context.getConnection();
        if (!nioConnection.isOpen()) {
            return AsyncResult.COMPLETE;
        }
        
        final TaskQueue<AsyncWriteQueueRecord> writeTaskQueue =
                nioConnection.getAsyncWriteQueue();
        
        if (checkRefusedBytes(writeTaskQueue)) {
            return AsyncResult.COMPLETE;
        }
        
        boolean done = false;
        AsyncWriteQueueRecord queueRecord = null;

        try {
            while ((queueRecord = aggregate(writeTaskQueue)) != null) {

                if (isLogFine) {
                    doFineLog("AsyncQueueWriter.processAsync doWrite"
                            + "connection={0} record={1}",
                            nioConnection, queueRecord);
                }                 

                final int bytesToReleaseBefore = (int) queueRecord.getBytesToReserve();
                
                final boolean isFinished;
                final int bytesToRelease;
                
                if (!queueRecord.isChecked() &&
                        !checkQueueSize(queueRecord, null)) {
                    bytesToRelease = bytesToReleaseBefore;
                    isFinished = true;
                    queueRecord = null;
                } else {
                    final RecordWriteResult writeResult = write0(nioConnection, queueRecord);
                    bytesToRelease = (int) writeResult.bytesToReleaseAfterLastWrite();

                    isFinished = queueRecord.isFinished();
                }

                if (isFinished) {
                    // Is here a chance that queue becomes empty?
                    // If yes - we need to switch to manual io event processing
                    // mode to *disable WRITE interest for SameThreadStrategy*,
                    // so we don't have either neverending WRITE events processing
                    // or stuck, when other thread tried to add data to the queue.
                    if (!context.isManualIOEventControl() &&
                            writeTaskQueue.spaceInBytes() - bytesToRelease <= 0) {
                        context.setManualIOEventControl();
                    }
                }
                
                done = (writeTaskQueue.releaseSpaceAndNotify(bytesToRelease) == 0);
                
                if (isFinished) {
                    finishQueueRecord(nioConnection, queueRecord);
                    
                    if (done) {
                        return AsyncResult.COMPLETE;
                    }
                } else { // if there is still some data in current message
                    queueRecord.notifyIncomplete();
                    writeTaskQueue.setCurrentElement(queueRecord);
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

            if (!done) {
                // Counter shows there should be some elements in queue,
                // but seems write() method still didn't add them to a queue
                // so we can release the thread for now
                return AsyncResult.EXPECTING_MORE;
            }
        } catch (IOException e) {
            if (isLogFine) {
                LOGGER.log(Level.FINEST, "AsyncQueueWriter.processAsync "
                        + "exception connection=" + nioConnection + " peekRecord=" +
                        queueRecord, e);
            }
            onWriteFailure(nioConnection, queueRecord, e);
        }
        
        return AsyncResult.COMPLETE;
    }

    /**
     * Return <tt>true</tt> if async write queue counter got zero-value after
     * subtracting bytes scheduled for release.
     */
    private static boolean checkRefusedBytes(
            final TaskQueue<AsyncWriteQueueRecord> writeTaskQueue) {
        final AtomicInteger refusedBytesCounter =
                writeTaskQueue.getRefusedBytes();
        
        final int refusedBytes = refusedBytesCounter.getAndSet(0);
        return refusedBytes > 0 &&
            writeTaskQueue.releaseSpaceAndNotify(refusedBytes) == 0;
    }

    private static void finishQueueRecord(final NIOConnection nioConnection,
            final AsyncWriteQueueRecord queueRecord) {
        final boolean isLogFine = LOGGER.isLoggable(Level.FINEST);
        
        if (isLogFine) {
            doFineLog("AsyncQueueWriter.processAsync finished "
                    + "connection={0} record={1}",
                    nioConnection, queueRecord);
        }

        if (queueRecord != null) {
            queueRecord.notifyCompleteAndRecycle();
        }
        
        if (isLogFine) {
            doFineLog("AsyncQueueWriter.processAsync nextRecord "
                    + "connection={0} nextRecord={1}",
                    nioConnection, queueRecord);
        }
    }
    
    protected static void offerToTaskQueue(
            final NIOConnection nioConnection,
            final AsyncWriteQueueRecord queueRecord,
            final TaskQueue<AsyncWriteQueueRecord> taskQueue) {
        
        taskQueue.offer(queueRecord);
        if (!nioConnection.isOpen() && taskQueue.remove(queueRecord)) {
            onWriteFailure(nioConnection, queueRecord, new IOException("Connection is closed"));
        }
    }
    
    private static WritableMessage cloneRecordIfNeeded(
            final Connection connection,
            final MessageCloner<WritableMessage> cloner,
            final WritableMessage message) {
        
        if (LOGGER.isLoggable(Level.FINEST)) {
            LOGGER.log(Level.FINEST,
                    "AsyncQueueWriter.write clone. connection={0} cloner={1}",
                    new Object[] {connection, cloner});
        }
        
        return cloner == null ? message : cloner.clone(connection, message);
    }

    protected AsyncWriteQueueRecord createRecord(final Connection connection,
            final WritableMessage message,
            final CompletionHandler<WriteResult<WritableMessage, SocketAddress>> completionHandler,
            final SocketAddress dstAddress,
            final PushBackHandler pushBackHandler,
            final boolean isEmptyRecord) {
        return AsyncWriteQueueRecord.create(connection, message,
                completionHandler, dstAddress, pushBackHandler,
                isEmptyRecord);
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
       
    private static void doFineLog(final String msg, final Object... params) {
        LOGGER.log(Level.FINEST, msg, params);
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
        writeQueue.onClose();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Reentrant getWriteReentrant() {
        final Thread t = Thread.currentThread();
        // If it's a Grizzly WorkerThread - use GrizzlyAttribute
        if (WorkerThread.class.isAssignableFrom(t.getClass())) {
            return REENTRANTS_ATTR.get((WorkerThread) t);
        }

        // ThreadLocal otherwise
        return REENTRANTS_COUNTER.get();
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
            final AsyncWriteQueueRecord failedRecord, final Throwable e) {

        failedRecord.notifyFailure(e);
        connection.closeSilently();
    }
    
    protected abstract RecordWriteResult write0(final NIOConnection connection,
            final AsyncWriteQueueRecord queueRecord)
            throws IOException;

    protected abstract void onReadyToWrite(NIOConnection connection)
            throws IOException;

    /**
     * Aggregates records in a queue to be written as one chunk.
     */
    protected AsyncWriteQueueRecord aggregate(TaskQueue<AsyncWriteQueueRecord> connectionQueue) {
        return connectionQueue.obtainCurrentElementAndReserve();
    }

    /**
     * {@link AsyncWriteQueueRecord} was added w/o size check (because of reentrants
     * limit), so check it.
     */
    private boolean checkQueueSize(
            final AsyncWriteQueueRecord queueRecord,
            final PushBackContext pushBackContext) {
        final NIOConnection connection = (NIOConnection) queueRecord.getConnection();
        final PushBackHandler pushBackHandler = queueRecord.getPushBackHandler();
        final WritableMessage message = queueRecord.getWritableMessage();
        
        // For empty buffer reserve 1 byte space        
        final int bytesToReserve = (int) queueRecord.getBytesToReserve();
        
        final int pendingBytes = queueRecord.getMomentumQueueSize();
        queueRecord.setMomentumQueueSize(-1);
        final boolean isCurrent = (pendingBytes == bytesToReserve);

        final int maxPendingBytesLocal = connection.getMaxAsyncWriteQueueSize();

        // Check if the buffer size matches maxPendingBytes
        if (!isCurrent
                && maxPendingBytesLocal > 0 && pendingBytes > maxPendingBytesLocal) {
            
            if (pushBackHandler == null) {
                final Throwable error =
                        new PendingWriteQueueLimitExceededException(
                        "Max queued data limit exceeded: "
                        + pendingBytes + '>' + maxPendingBytesLocal);
                queueRecord.notifyFailure(error);
            } else {
                final PushBackContext pbContextLocal = pushBackContext == null ?
                        new PushBackContextImpl(queueRecord) :
                        pushBackContext;
                pushBackHandler.onPushBack(connection, message, pbContextLocal);
            }
            
            return false;
        }

        if (pushBackHandler != null) {
            pushBackHandler.onAccept(connection, message);
        }
        
//        return CheckResult.CONTINUE;
        return true;
    }
    
    private final class PushBackContextImpl extends PushBackContext
            implements WriteHandler {

        public PushBackContextImpl(final AsyncWriteQueueRecord queueRecord) {
            super(queueRecord);
        }

        @Override
        public void retryWhenPossible() {
            final NIOConnection connection = (NIOConnection) queueRecord.getConnection();
            notifyWritePossible(connection, this, (int) queueRecord.remaining());
        }

        @Override
        public void retryNow() {
            onWritePossible();
        }

        @Override
        public void cancel() {
            queueRecord.notifyFailure(
                    new CancellationException("write cancelled"));
        }
        
        @Override
        public void onWritePossible() {
            writeQueueRecord(queueRecord, null, this);
        }

        @Override
        public void onError(Throwable t) {
            queueRecord.notifyFailure(t);
        }
    }
}
