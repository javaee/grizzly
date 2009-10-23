
/*
 *
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2007-2008 Sun Microsystems, Inc. All rights reserved.
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

package com.sun.grizzly.util;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * minimalistic fixed threadpool.
 * by default: {@link WorkerThreadImpl} is used,
 * {@link LinkedTransferQueue} is used as workQueue for its nice scalability over the lock based alternatives.<br>
 * {@link LinkedTransferQueue} gives FIFO per producer.<br>
 *
 * @author gustav trede
 */
public class FixedThreadPool extends AbstractThreadPool {

    protected final ConcurrentHashMap<Worker,Boolean> workers
            = new ConcurrentHashMap<Worker,Boolean>();

    /**
     * exits for use by subclasses, does not impact the performance of fixed pool
     */
    protected final AtomicInteger aliveworkerCount = new AtomicInteger();

    protected final AtomicInteger approximateRunningWorkerCount = new AtomicInteger();

    protected final BlockingQueue<Runnable> workQueue;

    protected final Object statelock = new Object();

    protected volatile boolean running = true;

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
        this(size, "GrizzlyWorker");
    }

    /**
     *
     * @param size
     * @param name
     */
    public FixedThreadPool(int size, final String name) {
        this(size, new ThreadFactory(){
            private final AtomicInteger c = new AtomicInteger();
            public Thread newThread(Runnable r) {
                Thread t = new WorkerThreadImpl(null,name+c.incrementAndGet(),r,0);
                t.setDaemon(true);
                return t;
            }
        });
        this.name = name;
    }

    /**
     *
     * @param size
     * @param threadfactory {@link ThreadFactory}
     */
    public FixedThreadPool(int size, ThreadFactory threadfactory) {
        this(size, new LinkedTransferQueue(), threadfactory);
    }
    /**
     *
     * @param fixedsize
     * @param workQueue
     */
    public FixedThreadPool(int fixedsize,BlockingQueue<Runnable> workQueue,
                ThreadFactory threadfactory) {
        if (threadfactory == null)
            throw new IllegalArgumentException("threadfactory == null");
        if (workQueue == null)
            throw new IllegalArgumentException("workQueue == null");
        if (fixedsize < 1)
            throw new IllegalArgumentException("fixedsize < 1");
        this.threadFactory = threadfactory;
        this.workQueue   = workQueue;
        this.maxPoolSize = fixedsize;
        while(fixedsize-->0){
            aliveworkerCount.incrementAndGet();
            startWorker(new BasicWorker());
        }
    }

    protected FixedThreadPool(BlockingQueue<Runnable> workQueue,ThreadFactory threadFactory){
        if (workQueue == null)
            throw new IllegalArgumentException("workQueue == null");
        this.workQueue     = workQueue;
        this.threadFactory = threadFactory;
    }


    protected void startWorker(BasicWorker wt){
        wt.t = threadFactory.newThread(wt);
        workers.put(wt, Boolean.TRUE);
        wt.t.start();
    }

    /**
     * {@inheritDoc}
     */
    public void execute(Runnable command) {
        if (running){
            if (workQueue.offer(command)) {
                onTaskQueued(command);
            } else {
                onTaskQueueOverflow();
                throw new RejectedExecutionException(
                        "The thread pool's task queue is full");
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    public List<Runnable> shutdownNow() {
        synchronized(statelock){
            List<Runnable> drained = new ArrayList<Runnable>();
            if (running){
                running = false;
                workQueue.drainTo(drained);

                for(Runnable task : drained) {
                    onTaskDequeued(task);
                }
                
                poisonAll();
                //try to interrupt their current work so they can get their poison fast
                for (Worker w:workers.keySet()){
                    w.t.interrupt();
                }
            }
            return drained;
        }
    }

    /**
     * {@inheritDoc}
     */
    public void shutdown() {
        synchronized(statelock){
            if (running){
                running = false;
                poisonAll();
            }
        }
    }


    private void poisonAll(){
        int size = Math.max(maxPoolSize, aliveworkerCount.get()) * 4/3;
        while(size-->0){
            workQueue.offer(poison);
        }
    }

    /**
     * {@inheritDoc}
     */
    public boolean isShutdown() {
        return !running;
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

    /**
     * {@inheritDoc}
     */
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

    public BlockingQueue<Runnable> getQueue() {
        return workQueue;
    }

    /**
     * Runs at O(n) time with default Impl. due to LTQ.<br>
     * FixedThreadPool uses LTQ due there is no need for queue size logic.
     *
     * @return
     */
    public int getQueueSize() {
        return workQueue.size();
    }

    @Override
    public void setMaximumPoolSize(int maximumPoolSize) {
    }

    public int getMaxQueuedTasksCount() {
        return Integer.MAX_VALUE;
    }

    public void setMaxQueuedTasksCount(int maxTasksCount) {
    }

    @Override
    protected void beforeExecute(Thread t, Runnable r) {
        super.beforeExecute(t, r);
        approximateRunningWorkerCount.incrementAndGet();
    }

    @Override
    protected void afterExecute(Runnable r, Throwable t) {
        approximateRunningWorkerCount.decrementAndGet();
        super.afterExecute(r, t);
    }

    @Override
    protected void onWorkerExit(Worker worker) {
        aliveworkerCount.decrementAndGet();
        workers.remove(worker);
        super.onWorkerExit(worker);
    }

    @Override
    protected String nextThreadId() {
        throw new UnsupportedOperationException();
    }

    protected class BasicWorker extends Worker {
        protected Runnable getTask() throws InterruptedException {
            return workQueue.take();
        }
    }
}