/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2007-2010 Oracle and/or its affiliates. All rights reserved.
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

import com.sun.grizzly.util.DataStructures;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Class implements {@link Map}-like collection, maps keys to values, where
 * single key could have queue of correspondent values.
 *
 * @author Alexey Stashok
 */
public class AsyncQueue<K, E> {
    private final ConcurrentMap<K, AsyncQueueEntry> queueMap =
            new ConcurrentHashMap<K, AsyncQueueEntry>();
    
    /**
     * Add data to the {@link AsyncQueue}, corresponding to the given
     * <code>E</code> key
     * 
     * @param key <code>E</code>
     * @param queueRecord data unit
     */
    public void offer(K key, E queueRecord) {
        AsyncQueueEntry entry = obtainAsyncQueueEntry(key);
        entry.queue.offer(queueRecord);
    }
    
    /**
     * Get head element of <code>E</code> key related queue.
     * Element will not be removed from queue.
     * 
     * @param key <code>K</code>
     *
     * @return <code>E</code> data unit
     */
    public E peek(K key) {
        AsyncQueueEntry entry = queueMap.get(key);
        if (entry != null) {
            return entry.queue.peek();
        }
        
        return null;
    }

    /**
     * Get head element of <code>K</code> key related queue.
     * Element will be removed from queue.
     * 
     * @param key <code>K</code>
     *
     * @return <code>E</code> data unit
     */
    public E poll(K key) {
        AsyncQueueEntry entry = queueMap.get(key);
        if (entry != null) {
            return entry.queue.poll();
        }
        
        return null;
    }

    /**
     * Remove head element of <code>K</code> key related queue.
     * 
     * @param key <code>K</code>
     * @return removed entry
     */
    public AsyncQueueEntry removeEntry(K key) {
        return queueMap.remove(key);
    }

    /**
     * Get the size of <code>K</code> key related queue.
     * 
     * @param key <code>K</code>
     * @return size of <code>K</code> key related queue.
     */
    public int size(K key) {
        AsyncQueueEntry entry = queueMap.get(key);
        return entry == null ? 0 : entry.queue.size();
    }
    
    /**
     * Checks if <code>K</code> key related queue is empty.
     * 
     * @param key <code>K</code>
     * @return true, if <code>K</code> key related queue is empty, false otherwise
     */
    public boolean isEmpty(K key) {
        AsyncQueueEntry entry = queueMap.get(key);
        return entry == null || entry.queue.isEmpty();
    }

    public void clear() {
        queueMap.clear();
    }

    protected AsyncQueueEntry obtainAsyncQueueEntry(K key) {
        AsyncQueueEntry entry = queueMap.get(key);
        if (entry == null) {
            synchronized(key) {
                entry = queueMap.get(key);
                if (entry == null) {
                    entry = new AsyncQueueEntry();
                    queueMap.put(key, entry);
                }
            }
        }
        return entry;
    }

    protected AsyncQueueEntry getAsyncQueueEntry(K key) {
        return queueMap.get(key);
    }
    /**
     * {@link AsyncQueue} data unit
     */
    public class AsyncQueueEntry {
        public final Queue<E> queue;
        public final AtomicReference<E> currentElement;
        public final ReentrantLock queuedActionLock;
        // Amound of data, processed by the key
        public final AtomicInteger processedDataSize;
        // Number of queue elements processed
        public final AtomicInteger processedElementsCount;
        // Total number of elements were requested to be processed
        public final AtomicInteger totalElementsCount;
        // Number of elements passed throw the async queue (not processed directly)
        public final AtomicInteger queuedElementsCount;
        
        protected OperationResult tmpResult;

        public AsyncQueueEntry() {
            queue = (Queue<E>) DataStructures.getCLQinstance();
            currentElement = new AtomicReference<E>();
            queuedActionLock = new ReentrantLock();
            processedDataSize = new AtomicInteger();
            processedElementsCount = new AtomicInteger();
            totalElementsCount = new AtomicInteger();
            queuedElementsCount = new AtomicInteger();
            tmpResult = new OperationResult();
        }
    }    
}
