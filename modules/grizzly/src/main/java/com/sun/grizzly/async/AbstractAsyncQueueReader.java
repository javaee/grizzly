/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2010 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.grizzly.async;

import com.sun.grizzly.Controller;
import com.sun.grizzly.SelectorHandler;
import com.sun.grizzly.async.AsyncQueue.AsyncQueueEntry;
import com.sun.grizzly.util.FutureImpl;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.util.Queue;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;

/**
 *
 * @author oleksiys
 */
public abstract class AbstractAsyncQueueReader implements AsyncQueueReader {
    protected final SelectorHandler selectorHandler;
    private final AsyncQueue<SelectableChannel, AsyncQueueReadUnit> readQueue;
    
    public AbstractAsyncQueueReader(SelectorHandler selectorHandler) {
        this.selectorHandler = selectorHandler;
        readQueue = new AsyncQueue<SelectableChannel, AsyncQueueReadUnit>();
    }
    
    public Future<AsyncQueueReadUnit> read(SelectionKey key, ByteBuffer buffer,
            AsyncReadCallbackHandler callbackHandler) throws IOException {
        return read(key, buffer, callbackHandler, null);
    }

    public Future<AsyncQueueReadUnit> read(SelectionKey key, ByteBuffer buffer,
            AsyncReadCallbackHandler callbackHandler, 
            AsyncReadCondition condition) throws IOException {
        return read(key, buffer, callbackHandler, condition, null);
    }
    
    public Future<AsyncQueueReadUnit> read(SelectionKey key, ByteBuffer buffer,
            AsyncReadCallbackHandler callbackHandler, 
            AsyncReadCondition condition, 
            AsyncQueueDataProcessor readPostProcessor) throws IOException {

        if (key == null) {
            throw new IOException("SelectionKey is null! " +
                    "Probably key was cancelled or connection was closed?");
        }

        FutureImpl<AsyncQueueReadUnit> future =
                new FutureImpl<AsyncQueueReadUnit>();
        
        SelectableChannel channel = (SelectableChannel) key.channel();
        AsyncQueueEntry channelEntry = 
                readQueue.obtainAsyncQueueEntry(channel);
        
        // Update statistics
        channelEntry.totalElementsCount.incrementAndGet();

        AsyncQueueReadUnit record = new AsyncQueueReadUnit();

        final Queue<AsyncQueueReadUnit> queue = channelEntry.queue;
        final AtomicReference<AsyncQueueReadUnit> currentElement = channelEntry.currentElement;
        ReentrantLock lock = channelEntry.queuedActionLock;
        
        final int holdState = lock.getHoldCount();
        // If AsyncQueue is empty - try to read ByteBuffer here
        try {
            OperationResult dstResult = channelEntry.tmpResult;
            boolean isDirectReadCompleted = false;

            if (currentElement.get() == null && // Weak comparison for null
                    lock.tryLock()) {
                // Strong comparison for null, because we're in locked region
                if (currentElement.compareAndSet(null, record)) {
                    
                    // Do direct reading
                    do {
                        dstResult = doRead((ReadableByteChannel) channel, buffer,
                                readPostProcessor, dstResult);
                        channelEntry.processedDataSize.addAndGet(dstResult.bytesProcessed);
                        // If some data was read - we need to check "condition"
                        // Check is performed for each message separately, not like for TCP
                        if (dstResult.address != null &&
                                (!buffer.hasRemaining() || (condition != null &&
                                condition.checkAsyncReadCompleted(key,
                                dstResult.address, buffer)))) {
                            isDirectReadCompleted = true;
                            break;
                        }
                    } while (dstResult.address != null);
                } else {
                    lock.unlock();
                }
            }

            if (!isDirectReadCompleted && buffer.hasRemaining()) {
                // Update statistics
                channelEntry.queuedElementsCount.incrementAndGet();

                record.set(buffer, callbackHandler, condition,
                        readPostProcessor, future);

                boolean isRegisterForReading = false;
                
                // add new element to the queue, if it's not current
                if (currentElement.get() != record) {
                    queue.offer(record); // add to queue
                    if (!lock.isLocked()) {
                        isRegisterForReading = true;
                    }
                } else {  // if element was read direct (not fully read)
                    isRegisterForReading = true;
                    lock.unlock();
                }
                
                if (isRegisterForReading) {
                    registerForReading(key);
                }
            } else { // If there are no bytes available for reading                
                boolean isReregister = false;

                // Update statistics
                channelEntry.processedElementsCount.incrementAndGet();

                // If buffer was read directly - set next queue element as current
                if (lock.isHeldByCurrentThread()) {
                    AsyncQueueReadUnit nextRecord = queue.poll();
                    if (nextRecord != null) { // if there is something in queue
                        currentElement.set(nextRecord); 
                        lock.unlock();
                        isReregister = true;
                    } else { // if nothing in queue
                        currentElement.set(null);
                        lock.unlock();  // unlock
                        if (queue.peek() != null) {  // check one more time
                            isReregister = true;
                        }
                    }
                }
                

                // Notify callback handler
                record.set(buffer, callbackHandler, condition,
                        readPostProcessor, future);
                
                future.setResult(record);
                
                if (callbackHandler != null) {
                    callbackHandler.onReadCompleted(key,
                            dstResult.address, record);
                }

                if (isReregister) {
                    registerForReading(key);
                }
            }
        } catch(Exception e) {
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
                readQueue.getAsyncQueueEntry(key.channel());
        
        return channelEntry != null && (channelEntry.currentElement.get() != null ||
                (channelEntry.queue != null && !channelEntry.queue.isEmpty()));
    }

    public AsyncQueueEntry getAsyncQueue(SelectionKey key) {
        return readQueue.getAsyncQueueEntry(key.channel());
    }

    public void onRead(SelectionKey key) throws IOException {
        SelectableChannel channel = key.channel();
        
        AsyncQueueEntry channelEntry = 
                readQueue.obtainAsyncQueueEntry(channel);
        
        final Queue<AsyncQueueReadUnit> queue = channelEntry.queue;
        final AtomicReference<AsyncQueueReadUnit> currentElement = channelEntry.currentElement;
        ReentrantLock lock = channelEntry.queuedActionLock;

        if (currentElement.get() == null) {
            AsyncQueueReadUnit nextRecord = queue.peek();
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
                AsyncQueueReadUnit queueRecord = currentElement.get();

                ByteBuffer byteBuffer = queueRecord.byteBuffer;
                AsyncQueueDataProcessor readPostProcessor = queueRecord.readPostProcessor;
                try {
                    doRead((ReadableByteChannel) channel, byteBuffer, readPostProcessor, dstResult);
                    channelEntry.processedDataSize.addAndGet(dstResult.bytesProcessed);
                } catch (Exception e) {
                    if (queueRecord.callbackHandler != null) {
                        queueRecord.callbackHandler.onException(e, key,
                                byteBuffer, queue);
                    } else {
                        Controller.logger().log(Level.SEVERE,
                                "Exception occured when executing " +
                                "asynchronous queue reading", e);
                    }

                    onClose(channel);
                }

                // check if buffer was completely read
                AsyncReadCondition condition = queueRecord.condition;
                if (!byteBuffer.hasRemaining() || (condition != null &&
                        condition.checkAsyncReadCompleted(key, dstResult.address, byteBuffer))) {
                    currentElement.set(queue.poll());

                    ((FutureImpl) queueRecord.future).setResult(queueRecord);
                    
                    // Update statistics
                    channelEntry.processedElementsCount.incrementAndGet();

                    if (queueRecord.callbackHandler != null) {
                        queueRecord.callbackHandler.onReadCompleted(
                                key, dstResult.address, queueRecord);
                    }

                    // If last element in queue is null - we have to be careful
                    if (currentElement.get() == null) {
                        lock.unlock();
                        AsyncQueueReadUnit nextRecord = queue.peek();
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
                    registerForReading(key);
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
        readQueue.removeEntry(channel);
    }
    
    public void close() {
        readQueue.clear();
        //readQueue = null;
    }

    protected abstract OperationResult doRead(ReadableByteChannel channel,
            ByteBuffer byteBuffer, AsyncQueueDataProcessor readPostProcessor,
            OperationResult dstResult) throws IOException;

    private void registerForReading(SelectionKey key) {
        selectorHandler.register(key, SelectionKey.OP_READ);
    }
}
