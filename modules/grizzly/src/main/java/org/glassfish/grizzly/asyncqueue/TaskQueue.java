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
import org.glassfish.grizzly.CompletionHandler;

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
    
   private final AtomicInteger handlersCounter = new AtomicInteger();
   protected final Queue<HandlerRecord> handlersQueue =
            new ConcurrentLinkedQueue<HandlerRecord>();    
    // ------------------------------------------------------------ Constructors


    protected TaskQueue() {
        currentElement = new AtomicReference<E>();
        queue = new ConcurrentLinkedQueue<E>();
    }

    // ---------------------------------------------------------- Public Methods


    public static <E> TaskQueue<E> createTaskQueue() {
        return new TaskQueue<E>();
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
     * Returns the number of queued bytes.
     * 
     * @return the number of queued bytes.
     */
    public int size() {
        return spaceInBytes.get();
    }

    /**
     * Get the current processing task, if the current in not set, take the
     * task from the queue.
     * 
     * @return the current processing task
     */
    public E peek() {
        final E current = currentElement.get();
        return current != null ? current : queue.poll();
    }

    /**
     * Gets the current processing task and reserves its place.
     * 
     * @return the current processing task
     */
    public E poll() {
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

    public void notifyWhenLE(final int size,
            final CompletionHandler<Integer> completionHandler) {
        
        if (completionHandler == null) {
            return;
        }
        
        if (size < 0) {
            throw new IllegalArgumentException("size argument can't be less than 0");
        }
        
        if (isClosed) {
            completionHandler.failed(new IOException("Connection is closed"));
            return;
        }
        
        int currentQueueSize = size();
        
        if (currentQueueSize <= size) {
            try {
                completionHandler.completed(currentQueueSize);
            } catch (Exception ignored) {
            }
            
            return;
        }
        
        final HandlerRecord record =
                new HandlerRecord(completionHandler, size);
        offerHandler(record);
        
        currentQueueSize = size();
        
        if (currentQueueSize <= size && removeHandler(record)) {
            try {
                completionHandler.completed(currentQueueSize);
            } catch (Exception e) {
                completionHandler.failed(e);
            }
        } else {
            checkHandlerOnClose(record);
        }
    }
    // ------------------------------------------------------- Protected Methods


    public void onSizeDecreased(final int maxQueueSize) {
        if (maxQueueSize < 0 ||
                handlersCounter.get() == 0) {
            return;
        }
        
        int size;
        while((size = size()) <= maxQueueSize) {
            HandlerRecord record = pollHandler();
            if (record == null) {
                return;
            }
            
            try {
                record.completionHandler.completed(size);
            } catch (Exception e) {
                record.completionHandler.failed(e);
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
            
            AsyncQueueRecord record;
            while ((record = (AsyncQueueRecord) poll()) != null) {
                record.notifyFailure(error);
            }
        }
        
        HandlerRecord record;
        while ((record = pollHandler()) != null) {
            if (error == null) {
                error = new IOException("Connection closed");
            }
            record.completionHandler.failed(error);
        }
        
    }
   

    private void offerHandler(final HandlerRecord record) {
        handlersCounter.incrementAndGet();
        handlersQueue.offer(record);
    }

    private boolean removeHandler(final HandlerRecord record) {
        if (handlersQueue.remove(record)) {
            handlersCounter.decrementAndGet();
            return true;
        }
        
        return false;
    }

    private HandlerRecord pollHandler() {
        final HandlerRecord record = handlersQueue.poll();
        if (record != null) {
            handlersCounter.decrementAndGet();
            return record;
        }
        
        return null;
    }
    
    private void checkHandlerOnClose(final HandlerRecord record) {
        if (isClosed && removeHandler(record)) {
            record.completionHandler.failed(new IOException("Connection is closed"));
        }
    }    
    
    //----------------------------------------------------------- Nested Classes
    
    private static final class HandlerRecord {
        private final int size;
        private final CompletionHandler<Integer> completionHandler;

        public HandlerRecord(final CompletionHandler<Integer> completionHandler,
                final int size) {
            this.completionHandler = completionHandler;
            this.size = size;
        }
    }
}
