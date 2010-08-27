/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2009-2010 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.grizzly.utils;

import java.util.Collection;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 
 * @author Alexey Stashok
 */
public class DelayedExecutor {
    private final ExecutorService threadPool;

    private final DelayedRunnable runnable = new DelayedRunnable();
    
    private final Collection<DelayQueue<?>> queues =
            new LinkedTransferQueue<DelayQueue<?>>();

    private final Object sync = new Object();

    private volatile boolean isStarted;

    private final long checkIntervalMillis;

    public DelayedExecutor(ExecutorService threadPool) {
        this(threadPool, 1000, TimeUnit.MILLISECONDS);
    }

    public DelayedExecutor(ExecutorService threadPool, long checkInterval, TimeUnit timeunit) {
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

    public ExecutorService getThreadPool() {
        return threadPool;
    }

    public <E> DelayQueue<E> createDelayQueue(Worker<E> worker,
            Resolver<E> resolver, long delay, TimeUnit timeunit) {
        
        final long delayMillis = TimeUnit.MILLISECONDS.convert(delay, timeunit);
        final DelayQueue<E> queue = new DelayQueue(worker, resolver, delayMillis);

        queues.add(queue);

        return queue;
    }

    private class DelayedRunnable implements Runnable {

        @Override
        public void run() {
            while(isStarted) {
                final long currentTimeMillis = System.currentTimeMillis();
                
                for (final DelayQueue delayQueue : queues) {
                    Object removeElem;
                    while((removeElem = delayQueue.removeQueue.poll()) != null) {
                        delayQueue.queue.remove(removeElem);
                    }

                    final Resolver resolver = delayQueue.resolver;

                    while (true) {
                        final Object element = delayQueue.queue.peek();
                        
                        if (element == null ||
                                currentTimeMillis - resolver.getTimeoutMillis(element) < 0) {
                            break;
                        }

                        delayQueue.queue.poll();

                        try {
                            delayQueue.worker.doWork(element);
                        } catch (Exception ignored) {
                        }
                    }
                }

                synchronized(sync) {
                    if (!isStarted) return;
                    
                    try {
                        sync.wait(checkIntervalMillis);
                    } catch (InterruptedException e) {
                    }
                }
            }
        }

    }

    public final class DelayQueue<E> {
        final Queue<E> queue = new LinkedTransferQueue<E>();
        final Queue<E> removeQueue = new LinkedTransferQueue<E>();

        final Worker<E> worker;
        final Resolver<E> resolver;
        final long delayMillis;

        public DelayQueue(Worker<E> worker, Resolver<E> resolver,
                long delayMillis) {
            this.worker = worker;
            this.resolver = resolver;
            this.delayMillis = delayMillis;
        }

        public void add(E elem) {
            resolver.setTimeoutMillis(elem, System.currentTimeMillis() + delayMillis);
            queue.add(elem);
        }

        public void remove(E elem) {
            if (resolver.removeTimeout(elem)) {
                removeQueue.add(elem);
            }
        }

        public void destroy() {
            queues.remove(this);
        }
    }

    public interface Worker<E> {
        public void doWork(E element);
    }

    public interface Resolver<E> {
        public boolean removeTimeout(E element);
        
        public long getTimeoutMillis(E element);
        
        public void setTimeoutMillis(E element, long timeoutMillis);
    }


    public static void main(String[] args) throws Exception {
        ExecutorService threadPool = Executors.newCachedThreadPool();

        DelayedExecutor executor = new DelayedExecutor(threadPool);
        executor.start();
        DelayQueue<MyStr> queue = executor.<MyStr>createDelayQueue(new Worker<MyStr>() {

            @Override
            public void doWork(MyStr element) {
                System.out.println("doWork " + element.str);
            }
        }, new MyStr("Resolver"), 10, TimeUnit.SECONDS);

        final MyStr myStr = new MyStr("hello");

        queue.add(myStr);

//        System.out.println("Press enter to kill");
//        System.in.read();

//        queue.remove(myStr);
        
        System.out.println("Press enter to stop");
        System.in.read();

        executor.stop();
        threadPool.shutdownNow();
    }

    public static class MyStr implements Resolver<MyStr> {
        private final String str;
        private long timeout;

        public MyStr(String str) {
            this.str = str;
        }

        @Override
        public boolean removeTimeout(MyStr element) {
            if (element.timeout >= 0) {
                element.timeout = -1;
                return true;
            }

            return false;
        }

        @Override
        public long getTimeoutMillis(MyStr element) {
            return element.timeout;
        }

        @Override
        public void setTimeoutMillis(MyStr element, long timeoutMillis) {
            element.timeout = timeoutMillis;
        }
    }
}
