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

package org.glassfish.grizzly.nio.transport;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.SocketChannel;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.CloseReason;
import org.glassfish.grizzly.CloseType;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.FileTransfer;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.IOEvent;
import org.glassfish.grizzly.WriteResult;
import org.glassfish.grizzly.asyncqueue.AsyncQueueWriter;
import org.glassfish.grizzly.asyncqueue.AsyncWriteQueueRecord;
import org.glassfish.grizzly.asyncqueue.RecordWriteResult;
import org.glassfish.grizzly.asyncqueue.TaskQueue;
import org.glassfish.grizzly.asyncqueue.WritableMessage;
import org.glassfish.grizzly.attributes.Attribute;
import org.glassfish.grizzly.memory.BufferArray;
import org.glassfish.grizzly.memory.CompositeBuffer;
import org.glassfish.grizzly.nio.AbstractNIOAsyncQueueWriter;
import org.glassfish.grizzly.nio.DirectByteBufferRecord;
import org.glassfish.grizzly.nio.NIOConnection;
import org.glassfish.grizzly.nio.NIOTransport;

/**
 * The TCP transport {@link AsyncQueueWriter} implementation, based on
 * the Java NIO
 *
 * @author Alexey Stashok
 */
public final class TCPNIOAsyncQueueWriter extends AbstractNIOAsyncQueueWriter {
    private final static Logger LOGGER = Grizzly.logger(TCPNIOAsyncQueueWriter.class);

    public TCPNIOAsyncQueueWriter(final NIOTransport transport) {
        super(transport);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected RecordWriteResult write0(final NIOConnection connection,
            final AsyncWriteQueueRecord queueRecord) throws IOException {
        
        if (queueRecord instanceof CompositeQueueRecord) {
            return writeCompositeRecord(connection,
                    (CompositeQueueRecord) queueRecord);
        }
        
        final RecordWriteResult writeResult = queueRecord.getCurrentResult();
        
        if (queueRecord.remaining() == 0) {
            return writeResult.lastWriteResult(0,
                    queueRecord.isUncountable()
                            ? AsyncWriteQueueRecord.UNCOUNTABLE_RECORD_SPACE_VALUE
                            : 0);
            
        }
        
        final long written = write0(connection,
                queueRecord.getWritableMessage(), writeResult);
        
        return writeResult.lastWriteResult(written, written);
    }

    @SuppressWarnings("unchecked")
    protected long write0(final NIOConnection connection,
            final WritableMessage message,
            final WriteResult<WritableMessage, SocketAddress> currentResult)
            throws IOException {
        final long written;

        if (message instanceof Buffer) {
            final Buffer buffer = (Buffer) message;
            
            try {
                if (!buffer.hasRemaining()) {
                    written = 0;
                } else if (!buffer.isComposite()) {  // Simple buffer
                    written = TCPNIOUtils.writeSimpleBuffer(
                            (TCPNIOConnection) connection, buffer);
                } else { // Composite buffer
                    written = TCPNIOUtils.writeCompositeBuffer(
                            (TCPNIOConnection) connection, (CompositeBuffer) buffer);
                }

                ((TCPNIOConnection) connection).onWrite(buffer, written);
            } catch (IOException e) {
                // Mark connection as closed remotely.
                ((TCPNIOConnection) connection).terminate0(null,
                        new CloseReason(CloseType.REMOTELY, e));
                throw e;
            }
        } else if (message instanceof FileTransfer) {
            written = ((FileTransfer) message).writeTo((SocketChannel) connection.getChannel());
            ((TCPNIOConnection) connection).onWrite(null, written);
        } else {
            throw new IllegalStateException("Unhandled message type");
        }

        if (currentResult != null) {
            currentResult.setMessage(message);
            currentResult.setWrittenSize(currentResult.getWrittenSize()
                    + written);
            currentResult.setDstAddressHolder(
                    ((TCPNIOConnection) connection).peerSocketAddressHolder);
        }

        return written;
    }
    
    private RecordWriteResult writeCompositeRecord(final NIOConnection connection,
            final CompositeQueueRecord queueRecord) throws IOException {
        
        int written = 0;
        
        if (LOGGER.isLoggable(Level.FINEST)) {
            LOGGER.log(Level.FINEST,
                    "writeCompositeRecord connection={0}, queueRecord={1},"
                            + " queueRecord.remaining={2},"
                            + " queueRecord.queue.size()={3}",
                    new Object[] {connection, queueRecord, queueRecord.remaining(),
                        queueRecord.queue.size()});
        }
        
        if (queueRecord.size > 0) {
            final int bufferSize = Math.min(queueRecord.size,
                    connection.getWriteBufferSize() * 3 / 2);

            final DirectByteBufferRecord directByteBufferRecord =
                    DirectByteBufferRecord.get();

            try {
                final SocketChannel socketChannel = (SocketChannel) connection.getChannel();

                fill(queueRecord, bufferSize, directByteBufferRecord);
                directByteBufferRecord.finishBufferSlice();

                final int arraySize = directByteBufferRecord.getArraySize();

                written = arraySize == 1 ?

                        TCPNIOUtils.flushByteBuffer(
                        socketChannel, directByteBufferRecord.getArray()[0]) :

                        TCPNIOUtils.flushByteBuffers(
                        socketChannel, directByteBufferRecord.getArray(), 0, arraySize) ;

            } catch (IOException e) {
                // Mark connection as closed remotely.
                ((TCPNIOConnection) connection).terminate0(null,
                        new CloseReason(CloseType.REMOTELY, e));
                throw e;
            } finally {
                directByteBufferRecord.release();
            }
        }
        
        return update(queueRecord, written);
    }
    
    private static void fill(final CompositeQueueRecord queueRecord,
            final int totalBufferSize,            
            final DirectByteBufferRecord ioRecord) {
        
//        int dstBufferRemaining = dstByteBuffer.remaining();
//        
//        dstByteBuffer.limit(0);

        int totalRemaining = totalBufferSize;
        final Deque<AsyncWriteQueueRecord> queue = queueRecord.queue;
        final ArrayList<BufferArray> savedBufferStates = queueRecord.savedBufferStates;
        
        for (final Iterator<AsyncWriteQueueRecord> it = queue.iterator();
                it.hasNext() && totalRemaining > 0; ) {
            
            final AsyncWriteQueueRecord record = it.next();
            
            if (record.isUncountable()) {
                continue;
            }
            
            final Buffer message = record.getMessage();
            final int pos = message.position();
            
            final int messageRemaining = message.remaining();

            final BufferArray bufferArray =
                    (totalRemaining >= messageRemaining) ?
                    message.toBufferArray() :
                    message.toBufferArray(pos, pos + totalRemaining);

            savedBufferStates.add(bufferArray);
            TCPNIOUtils.fill(bufferArray, totalRemaining, ioRecord);
            
            totalRemaining -= messageRemaining;
        }
    }
    
    private RecordWriteResult update(final CompositeQueueRecord queueRecord,
            final int written) {
        
        // Restore buffer state
        for (int i = 0; i < queueRecord.savedBufferStates.size(); i++) {
            final BufferArray savedState = queueRecord.savedBufferStates.get(i);
            if (savedState != null) {
                savedState.restore();
                savedState.recycle();
            }
        }
        
        int extraBytesToRelease = 0;
        queueRecord.savedBufferStates.clear();
        
        int remainder = written;
        queueRecord.size -= written;
        
        final Connection connection = queueRecord.getConnection();
        final Deque<AsyncWriteQueueRecord> queue = queueRecord.queue;
        
        AsyncWriteQueueRecord record;
        while (remainder > 0) {
            record = queue.peekFirst();
            
            assert record != null;
            
            if (record.isUncountable()) {
                queue.removeFirst();
                record.notifyCompleteAndRecycle();
                extraBytesToRelease += AsyncWriteQueueRecord.UNCOUNTABLE_RECORD_SPACE_VALUE;
                continue;
            }

            final WriteResult firstResult = record.getCurrentResult();
            final Buffer firstMessage = record.getMessage();
            final long firstMessageRemaining =
                    record.getInitialMessageSize() - firstResult.getWrittenSize();

            if (remainder >= firstMessageRemaining) {
                remainder -= firstMessageRemaining;
                queue.removeFirst();
                firstResult.setWrittenSize(record.getInitialMessageSize());
                firstMessage.position(firstMessage.limit());
                
                ((TCPNIOConnection) connection).onWrite(firstMessage, firstMessageRemaining);
                                
                record.notifyCompleteAndRecycle();
            } else {
                firstMessage.position(firstMessage.position() + remainder);
                firstResult.setWrittenSize(
                        firstResult.getWrittenSize() + remainder);
                
                ((TCPNIOConnection) connection).onWrite(firstMessage, remainder);
                return queueRecord.getCurrentResult().lastWriteResult(written,
                        written + extraBytesToRelease);
            }
        }

        while ((record = queue.peekFirst()) != null && record.isUncountable()) {
            queue.removeFirst();
            record.notifyCompleteAndRecycle();
            extraBytesToRelease += AsyncWriteQueueRecord.UNCOUNTABLE_RECORD_SPACE_VALUE;
        }

        return queueRecord.getCurrentResult().lastWriteResult(written,
                written + extraBytesToRelease);
    }
    
    @Override
    protected final void onReadyToWrite(final NIOConnection connection) throws IOException {
        connection.enableIOEvent(IOEvent.WRITE);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected AsyncWriteQueueRecord aggregate(
            final TaskQueue<AsyncWriteQueueRecord> writeTaskQueue) {
        final int queueSize = writeTaskQueue.size();
        
        if (queueSize == 0) {
            return null;
        }
        
        final AsyncWriteQueueRecord currentRecord = writeTaskQueue.poll();

        if (currentRecord == null ||
                !canBeAggregated(currentRecord) ||
                queueSize == currentRecord.remaining()) {
            return currentRecord;
        }
        
        AsyncWriteQueueRecord nextRecord = checkAndGetNextRecord(writeTaskQueue);
        
        if (nextRecord == null) {
            return currentRecord;
        }
        
        final CompositeQueueRecord compositeQueueRecord =
                createCompositeQueueRecord(currentRecord);
        
        do {
            compositeQueueRecord.append(nextRecord);
        } while(compositeQueueRecord.remaining() < queueSize &&
                (nextRecord = checkAndGetNextRecord(writeTaskQueue)) != null);
        
        return compositeQueueRecord;
    }

    private static AsyncWriteQueueRecord checkAndGetNextRecord(
            final TaskQueue<AsyncWriteQueueRecord> writeTaskQueue) {

        final AsyncWriteQueueRecord nextRecord = writeTaskQueue.getQueue().poll();
        if (nextRecord == null) {
            return null;
        } else if (!canBeAggregated(nextRecord)) {
            writeTaskQueue.setCurrentElement(nextRecord);
            return null;
        }

        return nextRecord;
    }
    
    private static boolean canBeAggregated(final AsyncWriteQueueRecord record) {
        return record.canBeAggregated();
    }
    
    private static final Attribute<CompositeQueueRecord> COMPOSITE_BUFFER_ATTR =
            Grizzly.DEFAULT_ATTRIBUTE_BUILDER.createAttribute(
                    TCPNIOAsyncQueueWriter.class.getName() + ".compositeBuffer");

    private CompositeQueueRecord createCompositeQueueRecord(
            final AsyncWriteQueueRecord currentRecord) {
        
        if (!(currentRecord instanceof CompositeQueueRecord)) {
            final Connection connection = currentRecord.getConnection();
            
            CompositeQueueRecord compositeQueueRecord =
                    COMPOSITE_BUFFER_ATTR.get(connection);
            if (compositeQueueRecord == null) {
                compositeQueueRecord = CompositeQueueRecord.create(connection);
                COMPOSITE_BUFFER_ATTR.set(connection, compositeQueueRecord);
            }

            compositeQueueRecord.append(currentRecord);
            return compositeQueueRecord;
        } else {
            return (CompositeQueueRecord) currentRecord;
        }
    }

    private static final class CompositeQueueRecord extends AsyncWriteQueueRecord {
        
        private final ArrayList<BufferArray> savedBufferStates =
                new ArrayList<BufferArray>(2);
        
        private final Deque<AsyncWriteQueueRecord> queue =
                new ArrayDeque<AsyncWriteQueueRecord>(2);
        
        private int size;
        
        
        public static CompositeQueueRecord create(final Connection connection) {
            return new CompositeQueueRecord(connection);
        }

        public CompositeQueueRecord(final Connection connection) {
            super(connection, null, null, null, null, false);
        }

        public void append(final AsyncWriteQueueRecord queueRecord) {
            if (LOGGER.isLoggable(Level.FINEST)) {
                LOGGER.log(Level.FINEST,
                        "CompositeQueueRecord.append. connection={0}, this={1}, comp-size={2}, elem-count={3}, queueRecord={4}, newrec-size={5}, isEmpty={6}",
                        new Object[] {connection, this, size, queue.size(), queueRecord, queueRecord.remaining(), queueRecord.isUncountable()});
            }
            
            size += queueRecord.remaining();
            queue.add(queueRecord);
        }

        @Override
        public boolean isUncountable() {
            return false;
        }

        @Override
        public boolean isFinished() {
            return size == 0;
        }

        @Override
        public boolean canBeAggregated() {
            return true;
        }       
        
        @Override
        public long remaining() {
            return size;
        }

        @Override
        public void notifyCompleteAndRecycle() {
        }

        @Override
        public void notifyFailure(final Throwable e) {
            AsyncWriteQueueRecord record;
            while ((record = queue.poll()) != null) {
                record.notifyFailure(e);
            }
        }

        @Override
        public void recycle() {
        }
    }
}
