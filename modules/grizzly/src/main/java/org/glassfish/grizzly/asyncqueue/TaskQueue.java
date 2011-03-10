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
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.glassfish.grizzly.utils.LinkedTransferQueue;

/**
 * Class represents common implementation of asynchronous processing queue.
 *
 * @author Alexey Stashok
 */
public final class TaskQueue<E> {
    private static final AtomicReferenceFieldUpdater<QueueMonitor,Boolean> MONITOR =
            AtomicReferenceFieldUpdater.newUpdater(QueueMonitor.class, Boolean.class, "invalid");
    /**
     * The queue of tasks, which will be processed asynchronously
     */
    private final Queue<E> queue;
    
    private final AtomicReference<E> currentElement;
    private final AtomicInteger spaceInBytes = new AtomicInteger();

    protected final Queue<QueueMonitor> monitorQueue;

    // ------------------------------------------------------------ Constructors


    protected TaskQueue() {        
        currentElement = new AtomicReference<E>();
        queue = new LinkedTransferQueue<E>();
        monitorQueue = new ConcurrentLinkedQueue<QueueMonitor>();
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
     * Set current task element.
     * @param current task element.
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
