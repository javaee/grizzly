/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2012 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.asyncqueue;

import java.io.IOException;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.glassfish.grizzly.WriteHandler;

/**
 * Class represents common implementation of asynchronous processing queue.
 *
 * @author Alexey Stashok
 */
public final class TaskQueue<E> {
    private volatile boolean isClosed;
    
    /**
     * The queue of tasks, which will be processed asynchronously
     */
    private final Queue<E> queue;
    
    private final AtomicReference<E> currentElement;
    private final AtomicInteger spaceInBytes = new AtomicInteger();
    
    // refused/(pushed back) bytes counter
    private final AtomicInteger refusedBytes = new AtomicInteger();
    
    private final MutableMaxQueueSize maxQueueSizeHolder;
    
    private final AtomicInteger writeHandlersCounter = new AtomicInteger();
    protected final Queue<WriteHandler> writeHandlersQueue =
            new ConcurrentLinkedQueue<WriteHandler>();
    // ------------------------------------------------------------ Constructors


    protected TaskQueue(final MutableMaxQueueSize maxQueueSizeHolder) {
        this.maxQueueSizeHolder = maxQueueSizeHolder;
        currentElement = new AtomicReference<E>();
        queue = new ConcurrentLinkedQueue<E>();
    }

    // ---------------------------------------------------------- Public Methods


    public static <E> TaskQueue<E> createTaskQueue(
            final MutableMaxQueueSize maxQueueSizeHolder) {
        return new TaskQueue<E>(maxQueueSizeHolder);
    }

    /**
     * Reserves memory space in the queue.
     *
     * @return the new memory (in bytes) consumed by the queue.
     */
    public int reserveSpace(final int amount) {
        return spaceInBytes.addAndGet(amount);
    }

    /**
     * Releases memory space in the queue.
     *
     * @return the new memory (in bytes) consumed by the queue.
     */
    public int releaseSpace(final int amount) {
        return spaceInBytes.addAndGet(-amount);
    }

    /**
     * Releases memory space in the queue and notifies registered
     * {@link QueueMonitor}s about the update.
     *
     * @return the new memory (in bytes) consumed by the queue.
     */
    public int releaseSpaceAndNotify(final int amount) {
        final int space = releaseSpace(amount);
        doNotify();
        return space;
    }
    
    /**
     * Returns the number of queued bytes.
     * 
     * @return the number of queued bytes.
     */
    public int spaceInBytes() {
        return spaceInBytes.get();
    }

//    /**
//     * Get refused bytes counter.
//     * 
//     * @deprecated bytes must never be refused.
//     */
//    public AtomicInteger getRefusedBytes() {
//        return refusedBytes;
//    }

    /**
     * Get the current processing task, if the current in not set, take the
     * task from the queue.
     * 
     * @return the current processing task
     */
    public E obtainCurrentElement() {
        final E current = currentElement.get();
        return current != null ? current : queue.poll();
    }

    /**
     * Gets the current processing task and reserves its place.
     * 
     * @return the current processing task
     */
    public E obtainCurrentElementAndReserve() {
        E current = currentElement.getAndSet(null);
        return current != null ? current : queue.poll();
    }
    
    /**
     * Get the queue of tasks, which will be processed asynchronously
     * @return the queue of tasks, which will be processed asynchronously
     */
    public Queue<E> getQueue() {
        return queue;
    }

    @Deprecated
    public void notifyWritePossible(final WriteHandler writeHandler,
            final int size) {
        notifyWritePossible(writeHandler);
    }
    
    public void notifyWritePossible(final WriteHandler writeHandler) {
        
        if (writeHandler == null) {
            return;
        }
        
        if (isClosed) {
            writeHandler.onError(new IOException("Connection is closed"));
            return;
        }
        
        final int maxQueueSize = maxQueueSizeHolder.getMaxQueueSize();
        
        if (maxQueueSize < 0 || spaceInBytes() < maxQueueSize) {
            try {
                writeHandler.onWritePossible();
            } catch (Throwable e) {
                writeHandler.onError(e);
            }
            
            return;
        }
        
//        final WriteHandler record =
//                new WriteHandlerQueueRecord(writeHandler, size);
        offerWriteHandler(writeHandler);
        
        if (spaceInBytes() < maxQueueSize && removeWriteHandler(writeHandler)) {
            try {
                writeHandler.onWritePossible();
            } catch (Throwable e) {
                writeHandler.onError(e);
            }
        } else {
            checkWriteHandlerOnClose(writeHandler);
        }
    }

    public final boolean forgetWritePossible(final WriteHandler writeHandler) {
        return removeWriteHandler(writeHandler);
    }
    
    private void checkWriteHandlerOnClose(final WriteHandler writeHandler) {
        if (isClosed && removeWriteHandler(writeHandler)) {
            writeHandler.onError(new IOException("Connection is closed"));
        }
    }
    // ------------------------------------------------------- Protected Methods


    public void doNotify() {
        if (maxQueueSizeHolder == null ||
                writeHandlersCounter.get() == 0) {
            return;
        }
        
        final int maxQueueSize = maxQueueSizeHolder.getMaxQueueSize();
        
        while(spaceInBytes() <= maxQueueSize) {
            WriteHandler writeHandler = pollWriteHandler();
            if (writeHandler == null) {
                return;
            }
            
            try {
                writeHandler.onWritePossible();
            } catch (Throwable e) {
                writeHandler.onError(e);
            }
        }
    }
    
    /**
     * Set current task element.
     * @param task current element.
     */
    public void setCurrentElement(final E task) {
        currentElement.set(task);
    }

    /**
     * Remove the task from queue.
     * @param task the task to remove.
     * @return <tt>true</tt> if tasked was removed, or <tt>false</tt> otherwise.
     */
    public boolean remove(final E task) {
        return queue.remove(task);
    }
    
    /**
     * Add the new task into the task queue.
     *
     * @param task new task.
     */
    public void offer(final E task) {
        queue.offer(task);
    }
    
    public boolean isEmpty() {        
        return spaceInBytes.get() == 0;
    }

    public void onClose() {
        isClosed = true;
        
        IOException error = null;
        if (!isEmpty()) {
            if (error == null) {
                error = new IOException("Connection closed");
            }
            
            AsyncWriteQueueRecord record;
            while ((record = (AsyncWriteQueueRecord) obtainCurrentElementAndReserve()) != null) {
                record.notifyFailure(error);
            }
        }
        
        WriteHandler writeHandler;
        while ((writeHandler = pollWriteHandler()) != null) {
            if (error == null) {
                error = new IOException("Connection closed");
            }
            writeHandler.onError(error);
        }
        
    }
    
    private void offerWriteHandler(final WriteHandler writeHandler) {
        writeHandlersCounter.incrementAndGet();
        writeHandlersQueue.offer(writeHandler);
    }
    
    private boolean removeWriteHandler(final WriteHandler writeHandler) {
        if (writeHandlersQueue.remove(writeHandler)) {
            writeHandlersCounter.decrementAndGet();
            return true;
        }
        
        return false;
    }

    private WriteHandler pollWriteHandler() {
        final WriteHandler record = writeHandlersQueue.poll();
        if (record != null) {
            writeHandlersCounter.decrementAndGet();
            return record;
        }
        
        return null;
    }
    
    //----------------------------------------------------------- Nested Classes
    
//    private static final class WriteHandlerQueueRecord {
//        private final int size;
//        private final WriteHandler writeHandler;
//
//        public WriteHandlerQueueRecord(final WriteHandler writeHandler,
//                final int size) {
//            this.writeHandler = writeHandler;
//            this.size = size;
//        }
//
//        @Override
//        public boolean equals(Object obj) {
//            if (obj == null) {
//                return false;
//            }
//            if (getClass() != obj.getClass()) {
//                return false;
//            }
//            final WriteHandlerQueueRecord other = (WriteHandlerQueueRecord) obj;
//            if (this.writeHandler != other.writeHandler &&
//                    (this.writeHandler == null || !this.writeHandler.equals(other.writeHandler))) {
//                return false;
//            }
//            return true;
//        }
//
//        @Override
//        public int hashCode() {
//            int hash = 7;
//            hash = 31 * hash + 
//                    (this.writeHandler != null ?
//                    this.writeHandler.hashCode() :
//                    0);
//            return hash;
//        }
//    }
    
    public interface MutableMaxQueueSize {
        public int getMaxQueueSize();
    }
}
