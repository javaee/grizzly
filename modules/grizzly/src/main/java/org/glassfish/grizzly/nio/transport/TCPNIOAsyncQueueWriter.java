/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2013 Oracle and/or its affiliates. All rights reserved.
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
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import org.glassfish.grizzly.*;
import org.glassfish.grizzly.asyncqueue.AsyncQueueWriter;
import org.glassfish.grizzly.asyncqueue.AsyncWriteQueueRecord;
import org.glassfish.grizzly.asyncqueue.TaskQueue;
import org.glassfish.grizzly.asyncqueue.WritableMessage;
import org.glassfish.grizzly.attributes.Attribute;
import org.glassfish.grizzly.memory.Buffers;
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

    public TCPNIOAsyncQueueWriter(final NIOTransport transport) {
        super(transport);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected long write0(final NIOConnection connection,
            final AsyncWriteQueueRecord queueRecord) throws IOException {

        if (queueRecord instanceof CompositeQueueRecord) {
            return writeComposite0(connection, (CompositeQueueRecord) queueRecord);
        }
        
        final WriteResult<WritableMessage, SocketAddress> currentResult =
                queueRecord.getCurrentResult();
        final WritableMessage message = queueRecord.getMessage();

        final long written;

        if (message instanceof Buffer) {
            final Buffer buffer = (Buffer) message;
            final int oldPos = buffer.position();
            final int bufferSize = Math.min(buffer.remaining(),
                    connection.getWriteBufferSize() * 2);

            if (bufferSize == 0) {
                written = 0;
            } else {                

                final DirectByteBufferRecord directByteBufferRecord =
                        DirectByteBufferRecord.allocate(bufferSize);
                
                try {
                    final ByteBuffer directByteBuffer = directByteBufferRecord.getByteBuffer();
                    final SocketChannel socketChannel = (SocketChannel) connection.getChannel();

                    fillByteBuffer(buffer, 0, bufferSize, directByteBuffer);
                    written = TCPNIOTransport.flushByteBuffer(
                            socketChannel, directByteBuffer);

                    buffer.position(oldPos + (int) written);
                    ((TCPNIOConnection) connection).onWrite(buffer, written);
                } catch (IOException e) {
                    // Mark connection as closed remotely.
                    ((TCPNIOConnection) connection).close0(null, false);
                    throw e;
                } finally {
                    directByteBufferRecord.release();
                }
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

    private int writeComposite0(final NIOConnection connection,
            final CompositeQueueRecord queueRecord) throws IOException {
        
        final int bufferSize = Math.min(queueRecord.size,
                connection.getWriteBufferSize() * 2);
        
        final DirectByteBufferRecord directByteBufferRecord =
                DirectByteBufferRecord.allocate(bufferSize);

        try {
            final ByteBuffer directByteBuffer = directByteBufferRecord.getByteBuffer();
            final SocketChannel socketChannel = (SocketChannel) connection.getChannel();
        
            fillByteBuffer(queueRecord.queue, directByteBuffer);
            
            final int written = TCPNIOTransport.flushByteBuffer(socketChannel,
                    directByteBuffer);

            return update(queueRecord, written);
        } catch (IOException e) {
            // Mark connection as closed remotely.
            ((TCPNIOConnection) connection).close0(null, false);
            throw e;
        } finally {
            directByteBufferRecord.release();
        }
    }
    
    private static void fillByteBuffer(final Buffer src, final int offset,
            final int size, final ByteBuffer dstByteBuffer) {
        
        dstByteBuffer.limit(size);
        final int oldPos = src.position();
        src.position(oldPos + offset);
        
        src.get(dstByteBuffer);
        
        dstByteBuffer.position(0);
        src.position(oldPos);
    }

    private static void fillByteBuffer(final Deque<AsyncWriteQueueRecord> queue,
            final ByteBuffer dstByteBuffer) {
        
        int dstBufferRemaining = dstByteBuffer.remaining();
        
        dstByteBuffer.limit(0);

        for (final Iterator<AsyncWriteQueueRecord> it = queue.iterator();
                it.hasNext() && dstBufferRemaining > 0; ) {
            
            final AsyncWriteQueueRecord record = it.next();
            
            if (record.isEmptyRecord()) continue;
            
            final Buffer message = record.getMessage();
            final int oldPos = message.position();
            final int oldLim = message.limit();
            
            final int remaining = message.remaining();
            
            if (dstBufferRemaining >= remaining) {
                dstByteBuffer.limit(dstByteBuffer.limit() + remaining);
            } else {
                dstByteBuffer.limit(dstByteBuffer.capacity());
                message.limit(oldPos + dstBufferRemaining);
            }

            message.get(dstByteBuffer);
            Buffers.setPositionLimit(message, oldPos, oldLim);
            
            dstBufferRemaining -= remaining;
        }
        
        dstByteBuffer.position(0);
    }

    private int update(final CompositeQueueRecord queueRecord, int written) {
        int remainder = written;
        queueRecord.size -= written;
        
        final Connection connection = queueRecord.getConnection();
        final Deque<AsyncWriteQueueRecord> queue = queueRecord.queue;
        
        AsyncWriteQueueRecord record;
        while (remainder > 0) {
            record = queue.peekFirst();
            
            assert record != null;
            
            if (record.isEmptyRecord()) {
                queue.removeFirst();
                record.notifyCompleteAndRecycle();
                written += EMPTY_RECORD_SPACE_VALUE;
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
                return written;
            }
        }

        while ((record = queue.peekFirst()) != null && record.isEmptyRecord()) {
            queue.removeFirst();
            record.notifyCompleteAndRecycle();
            written += EMPTY_RECORD_SPACE_VALUE;
        }

        return written;
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
        final int queueSize = writeTaskQueue.spaceInBytes();
        
        if (queueSize == 0) {
            return null;
        }
        
        final AsyncWriteQueueRecord currentRecord =
                writeTaskQueue.obtainCurrentElementAndReserve();

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
            final NIOConnection connection = (NIOConnection) nextRecord.getConnection();
            offerToTaskQueue(connection, nextRecord, writeTaskQueue);
            return null;
        }

        return nextRecord;
    }
    
    private static boolean canBeAggregated(final AsyncWriteQueueRecord record) {
        return record.canBeAggregated();
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
        
        private final Deque<AsyncWriteQueueRecord> queue =
                new ArrayDeque<AsyncWriteQueueRecord>(2);
        
        private int size;
        
        public static CompositeQueueRecord create(final Connection connection) {
            return new CompositeQueueRecord(connection);
        }

        public CompositeQueueRecord(final Connection connection) {
            super(connection, null, null, null,
                    null, null, false);
        }

        public void append(final AsyncWriteQueueRecord queueRecord) {
            size += queueRecord.remaining();
            queue.add(queueRecord);
        }

        @Override
        public boolean isEmptyRecord() {
            return false;
        }

        @Override
        public boolean isFinished() {
            return size == 0;
        }

//        @Override
//        public boolean isChecked() {
//            return true;
//        }

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
