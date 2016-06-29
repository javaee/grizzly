/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2009-2016 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.utils;

import java.util.Iterator;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 
 * @author Alexey Stashok
 */
public class DelayedExecutor {
    public final static long UNSET_TIMEOUT = -1;
    
    private final ExecutorService threadPool;

    private final DelayedRunnable runnable = new DelayedRunnable();
    
    private final Queue<DelayQueue> queues =
             new ConcurrentLinkedQueue<DelayQueue>();

    private final Object sync = new Object();

    private volatile boolean isStarted;

    private final long checkIntervalMillis;

    public DelayedExecutor(final ExecutorService threadPool) {
        this(threadPool, 1000, TimeUnit.MILLISECONDS);
    }

    public DelayedExecutor(final ExecutorService threadPool,
            final long checkInterval, final TimeUnit timeunit) {
        if (checkInterval < 0) {
            throw new IllegalArgumentException("check interval can't be negative");
        }
        
        this.threadPool = threadPool;
        this.checkIntervalMillis = TimeUnit.MILLISECONDS.convert(checkInterval, timeunit);
    }

    public void start() {
        synchronized(sync) {
            if (!isStarted) {
                isStarted = true;
                threadPool.execute(runnable);
            }
        }
    }

    public void stop() {
        synchronized(sync) {
            if (isStarted) {
                isStarted = false;
                sync.notify();
            }
        }
    }

    public void destroy() {
        stop();
        synchronized(sync) {
            queues.clear();
        }
    }

    @SuppressWarnings("UnusedDeclaration")
    public ExecutorService getThreadPool() {
        return threadPool;
    }

    public <E> DelayQueue<E> createDelayQueue(final Worker<E> worker,
            final Resolver<E> resolver) {
        
        final DelayQueue<E> queue = new DelayQueue<E>(worker, resolver);

        queues.add(queue);

        return queue;
    }

    private static boolean wasModified(final long l1, final long l2) {
        return l1 != l2;
    }

    private class DelayedRunnable implements Runnable {

        @SuppressWarnings("unchecked")
        @Override
        public void run() {
            while(isStarted) {
                final long currentTimeMillis = System.currentTimeMillis();
                
                for (final DelayQueue delayQueue : queues) {
                    if (delayQueue.queue.isEmpty()) continue;
                    
                    final Resolver resolver = delayQueue.resolver;

                    for (Iterator it = delayQueue.queue.keySet().iterator(); it.hasNext(); ) {
                        final Object element = it.next();
                        final long timeoutMillis = resolver.getTimeoutMillis(element);
                        
                        if (timeoutMillis == UNSET_TIMEOUT) {
                            it.remove();
                            if (wasModified(timeoutMillis,
                                    resolver.getTimeoutMillis(element))) {                                
                                delayQueue.queue.put(element, delayQueue);
                            }
                        } else if (currentTimeMillis - timeoutMillis >= 0) {
                            it.remove();
                            if (wasModified(timeoutMillis,
                                    resolver.getTimeoutMillis(element))) {
                                delayQueue.queue.put(element, delayQueue);
                            } else {
                                try {
                                    if (!delayQueue.worker.doWork(element)) {
                                        delayQueue.queue.put(element, delayQueue);
                                    }
                                } catch (Exception ignored) {
                                }
                            }
                        }
                    }
                }

                synchronized(sync) {
                    if (!isStarted) return;
                    
                    try {
                        sync.wait(checkIntervalMillis);
                    } catch (InterruptedException ignored) {
                    }
                }
            }
        }
    }

    public class DelayQueue<E> {
        final ConcurrentMap<E, DelayQueue> queue =
                DataStructures.getConcurrentMap();

        final Worker<E> worker;
        final Resolver<E> resolver;

        public DelayQueue(final Worker<E> worker, final Resolver<E> resolver) {
            this.worker = worker;
            this.resolver = resolver;
        }

        public void add(final E elem, final long delay, final TimeUnit timeUnit) {
            if (delay >= 0) {
                final long delayWithSysTime =
                        System.currentTimeMillis() + TimeUnit.MILLISECONDS.convert(delay, timeUnit);
                resolver.setTimeoutMillis(elem, ((delayWithSysTime < 0) ? Long.MAX_VALUE : delayWithSysTime));
                queue.put(elem, this);
            }
        }

        public void remove(final E elem) {
            resolver.removeTimeout(elem);
        }

        public void destroy() {
            queues.remove(this);
        }
    }

    public interface Worker<E> {
        /**
         * The method is executed by <tt>DelayExecutor</tt> once element's timeout expires.
         * 
         * @param element element to operate upon.
         * @return <tt>true</tt>, if the work is done and element has to be removed
         *          from the delay queue, or <tt>false</tt> if the element
         *          should be re-registered on the delay queue again
         */
        boolean doWork(E element);
    }

    public interface Resolver<E> {
        boolean removeTimeout(E element);
        
        long getTimeoutMillis(E element);
        
        void setTimeoutMillis(E element, long timeoutMillis);
    }
}
