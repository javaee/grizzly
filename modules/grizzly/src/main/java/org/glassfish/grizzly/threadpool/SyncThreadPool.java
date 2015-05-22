/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2015 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.threadpool;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * {@link ExecutorService} implementation, which function the similar way as
 * former Grizzly 1.x Pipeline based thread pools.
 *
 * The <tt>SyncThreadPool</tt> is synchronized similar way as Grizzly 1.x Pipeline,
 * which makes thread pool more accurate when deciding to create or not
 * additional worker threads.
 *
 *
 * @author Alexey Stashok
 */
public class SyncThreadPool extends AbstractThreadPool {

    private final Queue<Runnable> workQueue;
    protected int maxQueuedTasks = -1;
    private int currentPoolSize;
    private int activeThreadsCount;
    
    /**
     *
     */
    public SyncThreadPool(ThreadPoolConfig config) {
        super(config);
        if (config.getKeepAliveTime(TimeUnit.MILLISECONDS) < 0) {
            throw new IllegalArgumentException("keepAliveTime < 0");
        }

        workQueue = config.getQueue() != null ?
            config.getQueue() :
            config.setQueue(new LinkedList<Runnable>()).getQueue();

        this.maxQueuedTasks = config.getQueueLimit();
        final int corePoolSize = config.getCorePoolSize();
        while (currentPoolSize < corePoolSize) {
            startWorker(new SyncThreadWorker(true));
        }
        ProbeNotifier.notifyThreadPoolStarted(this);
    }

    @Override
    public void execute(Runnable task) {

        if (task == null) {
            throw new IllegalArgumentException("Runnable task is null");
        }

        synchronized (stateLock) {
            if (!running) {
                throw new RejectedExecutionException("ThreadPool is not running");
            }

            final int workQueueSize = workQueue.size() + 1;

            if ((maxQueuedTasks < 0 || workQueueSize <= maxQueuedTasks)
                    && workQueue.offer(task)) {
                onTaskQueued(task);
            } else {
                onTaskQueueOverflow();
                assert false; // should not reach this point
            }

            final int idleThreadsNumber = currentPoolSize - activeThreadsCount;

            if (idleThreadsNumber >= workQueueSize) {
                stateLock.notify();
                return;
            }

            if (currentPoolSize < config.getMaxPoolSize()) {
                final boolean isCore = (currentPoolSize < config.getCorePoolSize());
                startWorker(new SyncThreadWorker(isCore));

                if (currentPoolSize == config.getMaxPoolSize()) {
                    onMaxNumberOfThreadsReached();
                }
            }
        }
    }

    @Override
    protected void startWorker(Worker worker) {
        synchronized (stateLock) {
            super.startWorker(worker);
            activeThreadsCount++;
            currentPoolSize++;
        }
    }

    @Override
    protected void onWorkerExit(Worker worker) {
        super.onWorkerExit(worker);
        
        synchronized (stateLock) {
            currentPoolSize--;
            activeThreadsCount--;
        }
    }

    @Override
    protected void poisonAll() {
        int size = currentPoolSize;
        final Queue<Runnable> q = getQueue();
        while (size-- > 0) {
            q.offer(poison);
        }
    }

    @Override
    public String toString() {
        synchronized (stateLock) {
            return super.toString()
                    + ", max-queue-size=" + maxQueuedTasks;
        }
    }

    protected class SyncThreadWorker extends Worker {

        private final boolean core;

        public SyncThreadWorker(boolean core) {
            this.core = core;
        }

        @Override
        protected Runnable getTask() throws InterruptedException {
            synchronized (stateLock) {
                activeThreadsCount--;
                try {

                    if (!running
                            || (!core && currentPoolSize > config.getMaxPoolSize())) {
                        // if maxpoolsize becomes lower during runtime we kill of the
                        return null;
                    }

                    Runnable r = workQueue.poll();

                    if (r != null) {
                        return r;
                    }

                    long keepAliveMillis = config.getKeepAliveTime(TimeUnit.MILLISECONDS);
                    final boolean hasKeepAlive = !core && keepAliveMillis >= 0;

                    long endTime = -1;
                    if (hasKeepAlive) {
                        endTime = System.currentTimeMillis() + keepAliveMillis;
                    }

                    do {
                        if (!hasKeepAlive) {
                            stateLock.wait();
                        } else {
                            stateLock.wait(keepAliveMillis);
                        }

                        r = workQueue.poll();

                        if (r != null) {
                            return r;
                        }

                        // Less than 20 millis remainder will consider as keepalive timeout
                        if (!running) {
                            return null;
                        } else if (hasKeepAlive) {
                            keepAliveMillis = endTime - System.currentTimeMillis();
                            
                            if (keepAliveMillis < 20) {
                                return null;
                            }
                        }
                    } while (true);

                } finally {
                    activeThreadsCount++;
                }
            }
        }
    }
}
