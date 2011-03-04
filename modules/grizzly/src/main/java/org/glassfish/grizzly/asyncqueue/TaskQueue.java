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

package org.glassfish.grizzly.asyncqueue;

import java.io.IOException;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Class represents common implementation of asynchronous processing queue.
 *
 * @author Alexey Stashok
 */
public final class TaskQueue<E> {
    private static final AtomicReferenceFieldUpdater<QueueMonitor,Boolean> MONITOR =
            AtomicReferenceFieldUpdater.newUpdater(QueueMonitor.class, Boolean.class, "invalid");

    private final AtomicReference<E> currentElement;
    final AtomicInteger spaceInBytes = new AtomicInteger();

    // Lock to provide atomic currentElement and Queue updates
    final ReentrantLock lock = new ReentrantLock();

    /**
     * The queue of tasks, which will be processed asynchronously
     */
    private final Queue<E> queue;


    protected final Queue<QueueMonitor> monitorQueue;

    private final E reserveObj;
    // ------------------------------------------------------------ Constructors


    protected TaskQueue(final E reserveObj) {
        this.reserveObj = reserveObj;
        this.queue = new LinkedList<E>();
        monitorQueue = new ConcurrentLinkedQueue<QueueMonitor>();
        currentElement = new AtomicReference<E>();
    }

    // ---------------------------------------------------------- Public Methods


    public static <E> TaskQueue<E> createTaskQueue(final E reserveObj) {
        return new TaskQueue<E>(reserveObj);
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
    public int releaseSpaceAndNotify(final int amount) throws IOException {
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

    /**
     * Get the current processing task
     * @return the current processing task
     */
    public E getCurrentElement() {
        return currentElement.get();
    }

    /**
     * Get the wrapped current processing task, to perform atomic operations.
     * @return the wrapped current processing task, to perform atomic operations.
     */
    public AtomicReference<E> getCurrentElementAtomic() {
        return currentElement;
    }
    
    /**
     * Get the queue of tasks, which will be processed asynchronously
     * @return the queue of tasks, which will be processed asynchronously
     */
//    public Queue<E> getQueue() {
//        return queue;
//    }


    public boolean addQueueMonitor(final QueueMonitor monitor) throws IOException {
        if (monitor.shouldNotify()) {
            monitor.onNotify();
            return false;
        } else {
            monitorQueue.offer(monitor);
            return true;
        }
    }


    public void removeQueueMonitor(final QueueMonitor monitor) {
        monitorQueue.remove(monitor);
    }


    // ------------------------------------------------------- Protected Methods


    protected void doNotify() throws IOException {

        if (!monitorQueue.isEmpty()) {
            for (final Iterator<QueueMonitor> i = monitorQueue.iterator(); i.hasNext(); ) {
                final QueueMonitor m = i.next();
                if (!MONITOR.get(m)) {
                    if (m.shouldNotify()) {
                        if (MONITOR.compareAndSet(m, Boolean.FALSE, Boolean.TRUE)) {
                            m.onNotify();
                        }
                    }
                } else {
                    i.remove();
                }
            }
        }

    }

    /**
     * Reserve current task element, if possible
     * @return <tt>true</tt>, if element was reserved, or <tt>false</tt> otherwise.
     */
    public boolean reserveCurrentElement() {
        return getCurrentElementAtomic().compareAndSet(null, reserveObj);
    }

    /**
     * Set current task element.
     * @param current task element.
     */
    public void setCurrentElement(final E task) {
        getCurrentElementAtomic().set(task);
    }

    /**
     * Retrieves current task element and marks the current element as reserved.
     * @return current element, or <tt>null</tt>, if none present.
     */
    public E getCurrentElementAndReserve() {
        final E current = getCurrentElementAtomic().getAndSet(reserveObj);
        if (current == reserveObj) {
            return null;
        }

        return current;
    }

    /**
     * Complete the current element processing and try to take the next element
     * from queue and make it current.
     *
     * @return new current element.
     */
    public E doneCurrentElement() {
        lock.lock();
        try {
            final E newCurrent = queue.poll();
            currentElement.set(newCurrent);
            return newCurrent;
        } finally {
            lock.unlock();
        }

    }

    /**
     * Add the new task into the task queue.
     *
     * @param task new task.
     * @return return <tt>true</tt>, if new task became current, or <tt>false</tt>
     * otherwise.
     */
    public boolean offer(final E task) {
        lock.lock();
        try {
            if (currentElement.get() == null) {
                currentElement.set(task);
                return true;
            }

            queue.offer(task);
            return false;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Remove the task from queue.
     * @param task the task to remove.
     * @return <tt>true</tt> if tasked was removed, or <tt>false</tt> otherwise.
     */
    public boolean remove(final E task) {
        lock.lock();
        try {
            return queue.remove(task);
        } finally {
            lock.unlock();
        }
    }

    public boolean isEmpty() {
        if (currentElement.get() == null) {
            lock.lock();
            try {
                return queue.isEmpty();
            } finally {
                lock.unlock();
            }
        }

        return false;
    }

    //----------------------------------------------------------- Nested Classes

    /**
     * Notification mechanism which will be invoked when
     * {@link TaskQueue#releaseSpace(int)} or {@link TaskQueue#releaseSpaceAndNotify(int)}
     * is called.
     */
    public static abstract class QueueMonitor {

        volatile Boolean invalid = Boolean.FALSE;

        // ------------------------------------------------------ Public Methods

        /**
         * Action(s) to perform when the current queue space meets the conditions
         * mandated by {@link #shouldNotify()}.
         */
        public abstract void onNotify() throws IOException;

        /**
         * This method will be invoked to determine if {@link #onNotify()} should
         * be called.  It's recommended that implementations of this method be
         * as light-weight as possible as this method may be invoked multiple
         * times.
         *
         * @return <code>true</code> if {@link #onNotify()} should be invoked on
         *  this <code>QueueMonitor</code> at the point in time <code>shouldNotify</code>
         *  was called, otherwise returns <code>false</code>
         */
        public abstract boolean shouldNotify();


    } // END QueueMonitor
}
