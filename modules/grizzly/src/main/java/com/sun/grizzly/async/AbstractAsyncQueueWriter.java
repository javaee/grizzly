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

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.sun.grizzly.async;

import com.sun.grizzly.Controller;
import com.sun.grizzly.SelectorHandler;
import com.sun.grizzly.async.AsyncQueue.AsyncQueueEntry;
import com.sun.grizzly.util.FutureImpl;
import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.WritableByteChannel;
import java.util.Queue;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;

/**
 *
 * @author oleksiys
 */
public abstract class AbstractAsyncQueueWriter implements AsyncQueueWriter {
    protected final SelectorHandler selectorHandler;
    private final AsyncQueue<SelectableChannel, AsyncQueueWriteUnit> writeQueue;
    
    public AbstractAsyncQueueWriter(SelectorHandler selectorHandler) {
        this.selectorHandler = selectorHandler;
        writeQueue = new AsyncQueue<SelectableChannel, AsyncQueueWriteUnit>();
    }

    public Future<AsyncQueueWriteUnit> write(SelectionKey key,
            ByteBuffer buffer) throws IOException {
        return write(key, null, buffer, null);
    }
    
    public Future<AsyncQueueWriteUnit> write(SelectionKey key,
            ByteBuffer buffer,
            AsyncWriteCallbackHandler callbackHandler) throws IOException {
        return write(key, null, buffer, callbackHandler, null);
    }

    public Future<AsyncQueueWriteUnit> write(SelectionKey key,
            ByteBuffer buffer,
            AsyncWriteCallbackHandler callbackHandler, 
            AsyncQueueDataProcessor writePreProcessor) throws IOException {
        return write(key, null, buffer, callbackHandler, writePreProcessor,
                null);
    }

    public Future<AsyncQueueWriteUnit> write(SelectionKey key,
            ByteBuffer buffer,
            AsyncWriteCallbackHandler callbackHandler, 
            AsyncQueueDataProcessor writePreProcessor, ByteBufferCloner cloner)
            throws IOException {
        return write(key, null, buffer, callbackHandler, writePreProcessor,
                cloner);
    }

    public Future<AsyncQueueWriteUnit> write(SelectionKey key,
            SocketAddress dstAddress,
            ByteBuffer buffer) throws IOException {
        return write(key, dstAddress, buffer, null);
    }
    
    public Future<AsyncQueueWriteUnit> write(SelectionKey key,
            SocketAddress dstAddress,
            ByteBuffer buffer, AsyncWriteCallbackHandler callbackHandler) 
            throws IOException {
        return write(key, dstAddress, buffer, callbackHandler, null);
    }

    public Future<AsyncQueueWriteUnit> write(SelectionKey key,
            SocketAddress dstAddress,
            ByteBuffer buffer, AsyncWriteCallbackHandler callbackHandler,
            AsyncQueueDataProcessor writePreProcessor) throws IOException {
        return write(key, dstAddress, buffer, callbackHandler,
                writePreProcessor, null);
    }

    public Future<AsyncQueueWriteUnit> write(SelectionKey key,
            SocketAddress dstAddress,
            ByteBuffer buffer, AsyncWriteCallbackHandler callbackHandler,
            AsyncQueueDataProcessor writePreProcessor, ByteBufferCloner cloner)
            throws IOException {
        
        if (key == null) {
            throw new IOException("SelectionKey is null! " +
                    "Probably key was cancelled or connection was closed?");
        }

        FutureImpl<AsyncQueueWriteUnit> future =
                new FutureImpl<AsyncQueueWriteUnit>();
        SelectableChannel channel = key.channel();
        AsyncQueueEntry channelEntry =
                writeQueue.obtainAsyncQueueEntry(channel);

        // Update statistics
        channelEntry.totalElementsCount.incrementAndGet();

        AsyncQueueWriteUnit record = new AsyncQueueWriteUnit();

        final Queue<AsyncQueueWriteUnit> queue = channelEntry.queue;
        final AtomicReference<AsyncQueueWriteUnit> currentElement = channelEntry.currentElement;
        ReentrantLock lock = channelEntry.queuedActionLock;

        // If AsyncQueue is empty - try to write ByteBuffer here
        final int holdState = lock.getHoldCount();
        try {

            boolean isCurrentElement = false;

            if (currentElement.get() == null && // Weak comparison for null
                    lock.tryLock()) {
                // Strong comparison for null, because we're in locked region
                if (currentElement.compareAndSet(null, record)) {
                    isCurrentElement = true;
                    OperationResult dstResult = channelEntry.tmpResult;
                    doWrite((WritableByteChannel) channel,
                            dstAddress, buffer, writePreProcessor, dstResult);

                    channelEntry.processedDataSize.addAndGet(
                            dstResult.bytesProcessed);
                } else {
                    lock.unlock();
                }
            }

            if (!isCurrentElement || buffer.hasRemaining() || (writePreProcessor != null
                    && writePreProcessor.getInternalByteBuffer().hasRemaining())) {

                // clone ByteBuffer if required
                if (cloner != null) {
                    buffer = cloner.clone(buffer);
                    record.setCloned(true);
                }

                // Update statistics
                channelEntry.queuedElementsCount.incrementAndGet();

                record.set(buffer, callbackHandler, writePreProcessor,
                        dstAddress, cloner, future);

                boolean isRegisterForWriting = false;

                // add new element to the queue, if it's not current
                if (currentElement.get() != record) {
                    queue.offer(record); // add to queue
                    if (!lock.isLocked()) {
                        isRegisterForWriting = true;
                    }
                } else {  // if element was written direct (not fully written)
                    isRegisterForWriting = true;
                    lock.unlock();
                }

                if (isRegisterForWriting) {
                    registerForWriting(key);
                }
            } else { // If there are no bytes available for writing

                record.set(buffer, callbackHandler, writePreProcessor,
                        dstAddress, cloner, future);
                future.setResult(record);

                // Update statistics
                channelEntry.processedElementsCount.incrementAndGet();

                // Notify callback handler
                if (callbackHandler != null) {
                    callbackHandler.onWriteCompleted(key, record);
                }

                // If buffer was written directly - set next queue element as current
                AsyncQueueWriteUnit nextRecord = queue.poll();
                if (nextRecord != null) { // if there is something in queue
                    currentElement.set(nextRecord);
                    lock.unlock();
                    registerForWriting(key);
                } else { // if nothing in queue
                    currentElement.set(null);
                    lock.unlock();  // unlock
                    if (queue.peek() != null) {  // check one more time
                        registerForWriting(key);
                    }
                }
            }
        } catch (Exception e) {
            if (record.callbackHandler != null) {
                record.callbackHandler.onException(e, key,
                        buffer, queue);
            }

            onClose(channel);

            if (e instanceof IOException) {
                throw (IOException) e;
            }
            throw new IOException(e.getMessage());
        } finally {
            if (lock.isHeldByCurrentThread() && holdState < lock.getHoldCount()) {
                lock.unlock();
            }
        }

        return future;
    }

    public boolean isReady(SelectionKey key) {
        AsyncQueueEntry channelEntry = 
                writeQueue.getAsyncQueueEntry(key.channel());
        
        return channelEntry != null && (channelEntry.currentElement.get() != null || 
                (channelEntry.queue != null && !channelEntry.queue.isEmpty()));
    }

    public AsyncQueueEntry getAsyncQueue(SelectionKey key) {
        return writeQueue.getAsyncQueueEntry(key.channel());
    }
    
    public void onWrite(SelectionKey key) throws IOException {
        SelectableChannel channel = key.channel();
        
        AsyncQueueEntry channelEntry = 
                writeQueue.obtainAsyncQueueEntry(channel);
        
        final Queue<AsyncQueueWriteUnit> queue = channelEntry.queue;
        final AtomicReference<AsyncQueueWriteUnit> currentElement = channelEntry.currentElement;
        ReentrantLock lock = channelEntry.queuedActionLock;

        if (currentElement.get() == null) {
            AsyncQueueWriteUnit nextRecord = queue.peek();
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

        try {
            OperationResult dstResult = channelEntry.tmpResult;
            while (currentElement.get() != null) {
                AsyncQueueWriteUnit queueRecord = currentElement.get();

                ByteBuffer byteBuffer = queueRecord.byteBuffer;
                AsyncQueueDataProcessor writePreProcessor = queueRecord.writePreProcessor;
                try {
                    doWrite((WritableByteChannel) channel,
                            queueRecord.dstAddress, byteBuffer,
                            writePreProcessor, dstResult);
                    channelEntry.processedDataSize.addAndGet(dstResult.bytesProcessed);
                } catch (Exception e) {
                    ((FutureImpl) queueRecord.future).setException(e);
                    
                    if (queueRecord.callbackHandler != null) {
                        queueRecord.callbackHandler.onException(e, key,
                                byteBuffer, queue);
                    } else {
                        Controller.logger().log(Level.SEVERE,
                                "Exception occured when executing " +
                                "asynchronous queue writing", e);
                    }

                    onClose(channel);
                }

                // check if buffer was completely written
                if (!byteBuffer.hasRemaining() &&
                        (writePreProcessor == null ||
                        !writePreProcessor.getInternalByteBuffer().hasRemaining())) {

                    // Update statistics
                    channelEntry.processedElementsCount.incrementAndGet();

                    if (queueRecord.callbackHandler != null) {
                        queueRecord.callbackHandler.onWriteCompleted(key, queueRecord);
                    }

                    currentElement.set(queue.poll());

                    // If last element in queue is null - we have to be careful
                    if (currentElement.get() == null) {
                        lock.unlock();
                        AsyncQueueWriteUnit nextRecord = queue.peek();
                        if (nextRecord != null && lock.tryLock()) {
                            if (!queue.isEmpty() && 
                                    currentElement.compareAndSet(null, nextRecord)) {
                                queue.remove();
                            }
                            
                            continue;
                        } else {
                            break;
                        }
                    }
                } else { // if there is still some data in current buffer
                    lock.unlock();
                    registerForWriting(key);
                    break;
                }
            }
        } finally {
            if (lock.isHeldByCurrentThread()) {
                channelEntry.queuedActionLock.unlock();
            }
        }
    }
    
    public void onClose(SelectableChannel channel) {
        writeQueue.removeEntry(channel);
    }
    
    public void close() {
        writeQueue.clear();
    }
    
    protected OperationResult doWrite(WritableByteChannel channel,
            SocketAddress dstAddress, ByteBuffer byteBuffer,
            AsyncQueueDataProcessor writePreProcessor,
            OperationResult dstResult)
            throws IOException {
        if (writePreProcessor != null) {
            ByteBuffer resultByteBuffer = null;
            int written = 0;

            do {
                if (byteBuffer.hasRemaining()) {
                    writePreProcessor.process(byteBuffer);
                }
                
                resultByteBuffer = writePreProcessor.getInternalByteBuffer();
                if (resultByteBuffer != null) {
                    doWrite(channel, dstAddress, resultByteBuffer,
                            dstResult);
                    written += dstResult.bytesProcessed;
                }
            } while(byteBuffer.hasRemaining() && 
                    (resultByteBuffer == null ||
                    !resultByteBuffer.hasRemaining()));

            dstResult.bytesProcessed = written;
            return dstResult;
        } else {
            return doWrite(channel, dstAddress, byteBuffer, dstResult);
        }
    }

    protected abstract OperationResult doWrite(WritableByteChannel channel,
            SocketAddress dstAddress, ByteBuffer byteBuffer,
            OperationResult dstResult) throws IOException;
    
    protected void registerForWriting(SelectionKey key) {
        selectorHandler.register(key, SelectionKey.OP_WRITE);
    }
}
