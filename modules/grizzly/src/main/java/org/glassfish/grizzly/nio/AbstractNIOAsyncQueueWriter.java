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

import java.io.IOException;
import java.net.SocketAddress;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.grizzly.AbstractWriter;
import org.glassfish.grizzly.CompletionHandler;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Context;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.IOEvent;
import org.glassfish.grizzly.WriteHandler;
import org.glassfish.grizzly.WriteResult;
import org.glassfish.grizzly.asyncqueue.AsyncQueueWriter;
import org.glassfish.grizzly.asyncqueue.AsyncWriteQueueRecord;
import org.glassfish.grizzly.asyncqueue.MessageCloner;
import org.glassfish.grizzly.asyncqueue.PushBackHandler;
import org.glassfish.grizzly.asyncqueue.RecordWriteResult;
import org.glassfish.grizzly.asyncqueue.TaskQueue;
import org.glassfish.grizzly.asyncqueue.WritableMessage;


/**
 * The {@link AsyncQueueWriter} implementation, based on the Java NIO
 * 
 * @author Alexey Stashok
 * @author Ryan Lubke
 * @author Gustav Trede
 */
@SuppressWarnings({"unchecked", "deprecation"})
public abstract class AbstractNIOAsyncQueueWriter
        extends AbstractWriter<SocketAddress>
        implements AsyncQueueWriter<SocketAddress> {

    private final static Logger LOGGER = Grizzly.logger(AbstractNIOAsyncQueueWriter.class);

    protected final NIOTransport transport;

    protected volatile int maxPendingBytes = AUTO_SIZE;

    protected volatile int maxWriteReentrants = 10;
    
    private volatile boolean isAllowDirectWrite = true;
    
    public AbstractNIOAsyncQueueWriter(NIOTransport transport) {
        this.transport = transport;
    }


    /**
     * {@inheritDoc}
     */
    @Deprecated
    @Override
    public boolean canWrite(final Connection<SocketAddress> connection,
            final int size) {
        return canWrite(connection);
    }

    @Override
    public boolean canWrite(final Connection<SocketAddress> connection) {
        final NIOConnection nioConnection = (NIOConnection) connection;
        final int connectionMaxPendingBytes = nioConnection.getMaxAsyncWriteQueueSize();
        
        if (connectionMaxPendingBytes < 0) {
            return true;
        }
        
        final TaskQueue<AsyncWriteQueueRecord> connectionQueue =
                (nioConnection).getAsyncWriteQueue();
        final int size = connectionQueue.spaceInBytes();
        
        return size == 0 || size < connectionMaxPendingBytes;
    }

    /**
     * {@inheritDoc}
     */
    @Deprecated
    @Override
    public void notifyWritePossible(final Connection<SocketAddress> connection,
            final WriteHandler writeHandler, final int size) {
        notifyWritePossible(connection, writeHandler);
    }
    
    @Override
    public void notifyWritePossible(final Connection<SocketAddress> connection,
            final WriteHandler writeHandler) {
        ((NIOConnection) connection).getAsyncWriteQueue()
                .notifyWritePossible(writeHandler);
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
    public boolean isAllowDirectWrite() {
        return isAllowDirectWrite;
    }

    /**
     * {@inheritDoc}
     */
    public void setAllowDirectWrite(final boolean isAllowDirectWrite) {
        this.isAllowDirectWrite = isAllowDirectWrite;
    }

    @Override
    public void write(final Connection<SocketAddress> connection,
            final SocketAddress dstAddress, final WritableMessage message,
            final CompletionHandler<WriteResult<WritableMessage, SocketAddress>> completionHandler,
            final MessageCloner<WritableMessage> cloner) {
        write(connection, dstAddress, message, completionHandler, null, cloner);
    }

    
    @Override
    @Deprecated
    public void write(
            final Connection<SocketAddress> connection, SocketAddress dstAddress,
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
    @Deprecated
    public void write(
            final Connection<SocketAddress> connection,
            final SocketAddress dstAddress,
            final WritableMessage message,
            final CompletionHandler<WriteResult<WritableMessage, SocketAddress>> completionHandler,
            final PushBackHandler pushBackHandler,
            final MessageCloner<WritableMessage> cloner) {
        

        final NIOConnection nioConnection = (NIOConnection) connection;

        // create and initialize the write queue record
        final AsyncWriteQueueRecord queueRecord = createRecord(
                nioConnection, message, completionHandler,
                dstAddress, pushBackHandler,
                !message.hasRemaining() || message.isExternal());


        if (nioConnection == null) {
            queueRecord.notifyFailure(new IOException("Connection is null"));
            return;
        }

        if (!nioConnection.isOpen()) {
            onWriteFailure(nioConnection, queueRecord,
                    nioConnection.getCloseReason().getCause());
            return;
        }

        // Get connection async write queue
        final TaskQueue<AsyncWriteQueueRecord> writeTaskQueue =
                nioConnection.getAsyncWriteQueue();

        // For empty buffer reserve 1 byte space
        final int bytesToReserve = (int) queueRecord.getBytesToReserve();

        final int pendingBytes = writeTaskQueue.reserveSpace(bytesToReserve);
        final boolean isCurrent = (pendingBytes == bytesToReserve);

        final boolean isLogFine = LOGGER.isLoggable(Level.FINEST);

        if (isLogFine) {
            doFineLog("AsyncQueueWriter.write connection={0}, record={1}, "
                    + "directWrite={2}, size={3}, isUncountable={4}, "
                    + "bytesToReserve={5}, pendingBytes={6}",
                    nioConnection, queueRecord, isCurrent, queueRecord.remaining(),
                    queueRecord.isUncountable(), bytesToReserve, pendingBytes);
        }

        final Reentrant reentrants = Reentrant.getWriteReentrant();

        try {
            if (!reentrants.inc()) {
                // Max number of reentrants is reached

                queueRecord.setMessage(
                        cloneRecordIfNeeded(nioConnection, cloner, message));

                if (isCurrent) { //current but can't write because of maxReentrants limit
                    writeTaskQueue.setCurrentElement(queueRecord);
                    nioConnection.simulateIOEvent(IOEvent.WRITE);
                } else {
                    writeTaskQueue.offer(queueRecord);
                }

                return;
            }

            if (isCurrent && isAllowDirectWrite) {

                // If we can write directly - do it w/o creating queue record (simple)
                final RecordWriteResult writeResult = write0(nioConnection, queueRecord);
                final int bytesToRelease = (int) writeResult.bytesToReleaseAfterLastWrite();

                final boolean isFinished = queueRecord.isFinished();
                
                final int pendingBytesAfterRelease =
                        writeTaskQueue.releaseSpaceAndNotify(bytesToRelease);

                final boolean isQueueEmpty = (pendingBytesAfterRelease == 0);

                if (isLogFine) {
                    doFineLog("AsyncQueueWriter.write directWrite connection={0}, record={1}, "
                            + "isFinished={2}, remaining={3}, isUncountable={4}, "
                            + "bytesToRelease={5}, pendingBytesAfterRelease={6}",
                            nioConnection, queueRecord, isFinished, queueRecord.remaining(),
                            queueRecord.isUncountable(), bytesToRelease, pendingBytesAfterRelease);
                }
                
                if (isFinished) {
                    queueRecord.notifyCompleteAndRecycle();
                    if (!isQueueEmpty) {
                        nioConnection.simulateIOEvent(IOEvent.WRITE);
                    }
                    return;
                }
            }

            queueRecord.setMessage(
                    cloneRecordIfNeeded(nioConnection, cloner, message));

            if (isLogFine) {
                doFineLog("AsyncQueueWriter.write queuing connection={0}, record={1}, "
                        + "size={2}, isUncountable={3}",
                        nioConnection, queueRecord, queueRecord.remaining(),
                        queueRecord.isUncountable());
            }

            if (isCurrent) { //current but not finished.
                writeTaskQueue.setCurrentElement(queueRecord);
                onReadyToWrite(nioConnection);
            } else {
                writeTaskQueue.offer(queueRecord);
            }
        } catch (IOException e) {
            if (isLogFine) {
                LOGGER.log(Level.FINEST,
                        "AsyncQueueWriter.write exception. connection=" +
                        nioConnection + " record=" + queueRecord, e);
            }

            onWriteFailure(nioConnection, queueRecord, e);
        } finally {
            reentrants.dec();
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
        
        int bytesReleased = 0;
        
        boolean done = true;
        
        AsyncWriteQueueRecord queueRecord = null;
        try {
            while ((queueRecord = aggregate(writeTaskQueue)) != null) {
                if (isLogFine) {
                    doFineLog("AsyncQueueWriter.processAsync beforeWrite "
                            + "connection={0} record={1}",
                            nioConnection, queueRecord);
                }                 

                final RecordWriteResult writeResult = write0(nioConnection, queueRecord);
                final int bytesToRelease = (int) writeResult.bytesToReleaseAfterLastWrite();
                
                done = queueRecord.isFinished();
                
                bytesReleased += bytesToRelease;

                if (isLogFine) {
                    doFineLog("AsyncQueueWriter.processAsync written "
                            + "connection={0}, written={1}, done={2}, "
                            + "bytesToRelease={3}, bytesReleased={4}",
                            nioConnection, writeResult.lastWrittenBytes(), done,
                            bytesToRelease, bytesReleased);
                }                 

                if (done) {
                    finishQueueRecord(nioConnection, queueRecord);
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
                    break;
                }
            }

            boolean isComplete = false;
            
            // Notify completed records' handlers (if any)
            if (bytesReleased > 0) {
                // Is here a chance that queue becomes empty?
                // If yes - we need to switch to manual io event processing
                // mode to *disable WRITE interest for SameThreadStrategy*,
                // so we don't have either neverending WRITE events processing
                // or stuck, when other thread tried to add data to the queue.
                if (done && !context.isManualIOEventControl()
                        && writeTaskQueue.spaceInBytes() - bytesReleased <= 0) {
                    if (isLogFine) {
                        doFineLog("AsyncQueueWriter.processAsync setManualIOEventControl "
                                + "connection={0}", nioConnection);
                    }
                    
                    context.setManualIOEventControl();
                }
                
                isComplete = (writeTaskQueue.releaseSpace(bytesReleased) == 0);
            }

            if (isLogFine) {
                doFineLog("AsyncQueueWriter.processAsync exit "
                        + "connection={0}, done={1}, isComplete={2}, "
                        + "bytesReleased={3}, queueSize={4}",
                        nioConnection, done, isComplete, bytesReleased, writeTaskQueue.size());
            }
            
            final AsyncResult result = !done ? AsyncResult.INCOMPLETE :
                    (!isComplete ? AsyncResult.EXPECTING_MORE : AsyncResult.COMPLETE);

            if (bytesReleased > 0) {
                // Finish the context processing (enable OP_WRITE if needed),
                // so following notification calls will not block the async write
                // queue write process
                context.complete(result.toProcessorResult());
                
                writeTaskQueue.doNotify();

                return AsyncResult.TERMINATE;
            }
            
            return result;
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
            doFineLog("AsyncQueueWriter.processAsync finishQueueRecord "
                    + "connection={0} queueRecord={1}",
                    nioConnection, queueRecord);
        }
    }
    
    private static WritableMessage cloneRecordIfNeeded(
            final Connection connection,
            final MessageCloner<WritableMessage> cloner,
            final WritableMessage message) {
        
        if (LOGGER.isLoggable(Level.FINEST)) {
            LOGGER.log(Level.FINEST,
                    "AsyncQueueWriter.write clone. connection={0} cloner={1} size={2}",
                    new Object[] {connection, cloner, message.remaining()});
        }
        
        return cloner == null ? message : cloner.clone(connection, message);
    }

    protected AsyncWriteQueueRecord createRecord(final Connection connection,
            final WritableMessage message,
            final CompletionHandler<WriteResult<WritableMessage, SocketAddress>> completionHandler,
            final SocketAddress dstAddress,
            final PushBackHandler pushBackHandler,
            final boolean isUncountable) {
        return AsyncWriteQueueRecord.create(connection, message,
                completionHandler, dstAddress, pushBackHandler, isUncountable);
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
        writeQueue.onClose(nioConnection.getCloseReason().getCause());
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
    
    protected abstract RecordWriteResult write0(NIOConnection connection,
            AsyncWriteQueueRecord queueRecord)
            throws IOException;

    protected abstract void onReadyToWrite(NIOConnection connection)
            throws IOException;

    /**
     * Aggregates records in a queue to be written as one chunk.
     */
    protected AsyncWriteQueueRecord aggregate(
            final TaskQueue<AsyncWriteQueueRecord> connectionQueue) {
        return connectionQueue.poll();
    }
}
