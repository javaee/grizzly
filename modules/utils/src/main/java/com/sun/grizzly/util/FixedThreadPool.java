
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
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * minimalistic fixed threadpool to allow for nice scalability if a
 * good Queue impl is used.
 * by default: {@link WorkerThreadImpl} is used,
 *
 * @author gustav trede
 */
public class FixedThreadPool extends AbstractThreadPool {

    protected final Map<Worker,Boolean> workers
            = new ConcurrentHashMap<Worker,Boolean>();

    /**
     * exits for use by subclasses, does not impact the performance of fixed pool
     */
    protected final AtomicInteger aliveworkerCount = new AtomicInteger();    

    private int expectedWorkerCount;
    
    protected final BlockingQueue<Runnable> workQueue;

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
        this(size, FixedThreadPool.class.getSimpleName());
    }

    /**
     *
     * @param poolsize
     * @param name
     */
    public FixedThreadPool(int poolsize, String name) {
        this(name,poolsize,DataStructures.getLTQinstance(Runnable.class),null);
    }

    /**
     * 
     * @param poolsize
     * @param threadfactory
     */
    public FixedThreadPool(int poolsize, ThreadFactory threadfactory) {
        this(poolsize,DataStructures.getLTQinstance(Runnable.class),
                threadfactory);
    }

    /**
     * 
     * @param poolsize
     * @param workQueue
     * @param threadfactory
     */
    public FixedThreadPool(int poolsize,BlockingQueue<Runnable> workQueue,
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
    public FixedThreadPool(String name,int poolsize,BlockingQueue<Runnable> workQueue,
                ThreadFactory threadfactory) {
         this(name, poolsize, workQueue, threadfactory,null);
    }

    /**
     *
     * @param name
     * @param poolsize
     * @param workQueue
     * @param threadfactory
     * @param probe 
     */
    public FixedThreadPool(String name,int poolsize,
            BlockingQueue<Runnable> workQueue,ThreadFactory threadfactory,
            ThreadPoolMonitoringProbe probe) {
        super(probe,name,threadfactory);
        if (poolsize < 1)
            throw new IllegalArgumentException("poolsize < 1");
        if (workQueue == null)
            workQueue = DataStructures.getLTQinstance(Runnable.class);
        this.workQueue   = workQueue;
        this.maxPoolSize = poolsize;
        while(poolsize-->0){
            dostartWorker();
        }
        super.onMaxNumberOfThreadsReached();
    }

    private void dostartWorker(){
        aliveworkerCount.incrementAndGet();
        startWorker(new BasicWorker());
        expectedWorkerCount++;
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

    /**
     * {@inheritDoc}
     */
    public int getTaskCount() {
        return 0;
    }

    /**
     * {@inheritDoc}
     */
    public long getCompletedTaskCount() {
        return 0;
    }

    @Override
    public void setCorePoolSize(int corePoolSize) {
    }

    /**
     * {@inheritDoc}
     */
    public int getLargestPoolSize() {
        return maxPoolSize;
    }

    /**
     * {@inheritDoc}
     */
    public int getPoolSize() {
        return maxPoolSize;
    }

    /**
     * {@inheritDoc}
     */
    public Queue<Runnable> getQueue() {
        return workQueue;
    }

    @Override
    public int getQueueSize() {
        return workQueue.size();
    }
   
    @Override
    public void setMaximumPoolSize(int maximumPoolSize) {
        if (maximumPoolSize < 1)
            throw new IllegalStateException("maximumPoolSize < 1");
        synchronized(statelock){
            if (running){
                this.maxPoolSize = maximumPoolSize;
                int toadd = maximumPoolSize - expectedWorkerCount;
                while(toadd > 0){
                    toadd--;
                    dostartWorker();
                }
                while(toadd++ < 0){
                    workQueue.add(poison);
                    expectedWorkerCount--;
                }
                super.onMaxNumberOfThreadsReached();
            }
        }
    }

    public int getMaxQueuedTasksCount() {
        return Integer.MAX_VALUE;
    }

    public void setMaxQueuedTasksCount(int maxTasksCount) {
    }

    @Override
    protected void onWorkerExit(Worker worker) {
        aliveworkerCount.decrementAndGet();
        workers.remove(worker);
        super.onWorkerExit(worker);
    }

    protected class BasicWorker extends Worker {
        protected Runnable getTask() throws InterruptedException {
            return workQueue.take();
        }
    }

}