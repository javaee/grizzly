/*
 *
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2007-2010 Sun Microsystems, Inc. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License. You can obtain
 * a copy of the License at https://glassfish.dev.java.net/public/CDDL+GPL.html
 * or glassfish/bootstrap/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/bootstrap/legal/LICENSE.txt.
 * Sun designates this particular file as subject to the "Classpath" exception
 * as provided by Sun in the GPL Version 2 section of the License file that
 * accompanied this code.  If applicable, add the following below the License
 * Header, with the fields enclosed by brackets [] replaced by your own
 * identifying information: "Portions Copyrighted [year]
 * [name of copyright owner]"
 *
 * Contributor(s):
 *
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
 *
 */

package com.sun.grizzly.threadpool;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * {@link ExecutorService} implementation, which function the similar way as
 * former Grizzly 1.x Pipeline based thread pools.
 *
 * The <tt>SyncThreadPool</tt> is sychronized similar way as Grizzly 1.x Pipeline,
 * which makes thread pool more accurate when deciding to create or not
 * additional worker threads.
 *
 *
 * @author Alexey Stashok
 */
public class SyncThreadPool extends AbstractThreadPool {

    private final Queue<Runnable> workQueue;
    protected volatile int maxQueuedTasks = -1;
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
    }

    public void start() {
        synchronized (stateLock) {
            while (currentPoolSize < config.getCorePoolSize()) {
                startWorker(new SyncThreadWorker(true));
                currentPoolSize++;
            }
        }
    }

    public void stop() {
        shutdownNow();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void execute(Runnable task) {

        if (task == null) {
            throw new IllegalArgumentException("Runnable task is null");
        }

        synchronized (stateLock) {
            if (!running) {
                throw new RejectedExecutionException("ThreadPool is not running");
            }

            final int idleThreadsNumber = currentPoolSize - activeThreadsCount;

            if ((maxQueuedTasks < 0 || workQueue.size() < maxQueuedTasks)
                    && workQueue.offer(task)) {
                onTaskQueued(task);
            } else {
                onTaskQueueOverflow();
            }

            final boolean isCore = (currentPoolSize < config.getCorePoolSize());

            if (isCore
                    || (currentPoolSize < config.getMaxPoolSize() && idleThreadsNumber == 0)) {
                startWorker(new SyncThreadWorker(isCore));
                currentPoolSize++;
            } else if (idleThreadsNumber == 0) {
                onMaxNumberOfThreadsReached();
            } else {
                stateLock.notify();
            }
        }
    }

    @Override
    protected void onWorkerStarted(Worker worker) {
        super.onWorkerStarted(worker);
        synchronized (stateLock) {
            activeThreadsCount++;
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

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isTerminated() {
        synchronized (stateLock) {
            return !running && workers.isEmpty();
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

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public String toString() {
        return super.toString()
                + ", max-queue-size=" + maxQueuedTasks;
    }

    protected class SyncThreadWorker extends Worker {

        private final boolean core;

        public SyncThreadWorker(boolean core) {
            this.core = core;
        }

        @Override
        protected Runnable getTask() throws InterruptedException {
            synchronized (stateLock) {
                try {
                    activeThreadsCount--;

                    if (running &&
                            (!core && currentPoolSize > config.getMaxPoolSize())) {
                        // if maxpoolsize becomes lower during runtime we kill of the
                        return null;
                    }

                    Runnable r = workQueue.poll();

                    long localKeepAlive = config.getKeepAliveTime(TimeUnit.MILLISECONDS);

                    while (r == null) {
                        final long startTime = System.currentTimeMillis();
                        stateLock.wait(localKeepAlive);
                        r = workQueue.poll();

                        localKeepAlive -= (System.currentTimeMillis() - startTime);

                        // Less than 100 millis remainder will consider as keepalive timeout
                        if (!running || (!core
                                && (r != null || localKeepAlive < 100))) {
                            break;
                        }
                    }

                    return r;

                } finally {
                    activeThreadsCount++;
                }
            }
        }
    }
}
