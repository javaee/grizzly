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

package com.sun.grizzly.util;

import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * minimalistic fixed threadpool to allow for nice scalability if a
 * good Queue impl is used.
 * by default: {@link WorkerThreadImpl} is used,
 *
 * @author gustav trede
 */
public class FixedThreadPool extends AbstractThreadPool {

    private int expectedWorkerCount;
    protected final BlockingQueue<Runnable> workQueue;

    /**
     * creates a fixed pool of size 8
     */
    public FixedThreadPool() {
        this(8);
    }

    /**
     *
     * @param size
     */
    public FixedThreadPool(int size) {
        this(size, FixedThreadPool.class.getSimpleName());
    }

    /**
     *
     * @param poolsize
     * @param name
     */
    public FixedThreadPool(int poolsize, String name) {
        this(name, poolsize, DataStructures.getLTQinstance(Runnable.class), null);
    }

    /**
     * 
     * @param poolsize
     * @param threadfactory
     */
    public FixedThreadPool(int poolsize, ThreadFactory threadfactory) {
        this(poolsize, DataStructures.getLTQinstance(Runnable.class),
                threadfactory);
    }

    /**
     * 
     * @param poolsize
     * @param workQueue
     * @param threadfactory
     */
    public FixedThreadPool(int poolsize, BlockingQueue<Runnable> workQueue,
            ThreadFactory threadfactory) {
        this(FixedThreadPool.class.getSimpleName(),
                poolsize, workQueue, threadfactory);
    }

    /**
     *
     * @param name
     * @param poolsize
     * @param workQueue
     * @param threadfactory
     */
    public FixedThreadPool(String name, int poolsize,
            BlockingQueue<Runnable> workQueue,ThreadFactory threadfactory) {
        this(name, poolsize, workQueue, threadfactory, null);
    }

    /**
     *
     * @param name
     * @param poolsize
     * @param workQueue
     * @param threadfactory
     * @param probe 
     */
    public FixedThreadPool(String name, int poolsize,
            BlockingQueue<Runnable> workQueue, ThreadFactory threadfactory,
            ThreadPoolMonitoringProbe probe) {
        super(probe, name, threadfactory,poolsize);
        this.workQueue = workQueue != null ? workQueue :
            DataStructures.getLTQinstance(Runnable.class);
        synchronized (statelock) {
            while (poolsize-- > 0) {
                doStartWorker();
            }
        }
        super.onMaxNumberOfThreadsReached();
    }

    @Override
    public final void setMaximumPoolSize(int maximumPoolSize) {
        if (maximumPoolSize < 1) {
            throw new IllegalStateException("maximumPoolSize < 1");
        }
        synchronized (statelock) {
            if (running) {
                this.maxPoolSize = maximumPoolSize;
                int toAdd = maximumPoolSize - expectedWorkerCount;
                while (toAdd > 0) {
                    toAdd--;
                    doStartWorker();
                }
                while (toAdd++ < 0) {
                    workQueue.add(poison);
                    expectedWorkerCount--;
                }
                super.onMaxNumberOfThreadsReached();
            }
        }
    }

    /**
     * Must hold statelock while calling this method.
     * @param wt
     */
    private void doStartWorker() {
        startWorker(new BasicWorker());
        expectedWorkerCount++;
    }

    public void execute(Runnable command) {
        if (running) {
            if (workQueue.offer(command)) {
                onTaskQueued(command);
                return;
            }
            onTaskQueueOverflow();
            return;
        }
        throw new RejectedExecutionException("ThreadPool is not running");
    }

    /**
     * not supported
     */
    public boolean isTerminated() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    /**
     * not supported
     */
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public int getActiveCount() {
        return 0;
    }

    public int getTaskCount() {
        return 0;
    }

    public long getCompletedTaskCount() {
        return 0;
    }

    @Override
    public void setCorePoolSize(int corePoolSize) {
    }

    public int getLargestPoolSize() {
        return maxPoolSize;
    }

    public int getPoolSize() {
        return maxPoolSize;
    }

    public Queue<Runnable> getQueue() {
        return workQueue;
    }

    public int getQueueSize() {
        return workQueue.size();
    }

    public int getMaxQueuedTasksCount() {
        return -1;
    }

    public void setMaxQueuedTasksCount(int maxTasksCount) {
    }

    private final class BasicWorker extends Worker {
        protected final Runnable getTask() throws InterruptedException {
            return workQueue.take();
        }
    }
}
