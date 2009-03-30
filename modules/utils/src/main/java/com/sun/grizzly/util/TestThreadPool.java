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

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * corethreads are prestarted.<br>
 * maxPoolSize is runtime configurable.<br><br>
 * 
 * Designed around the use of lockfree queue.<br>
 * less performant then {@link FixedThreadPool} due to keeping track
 * of queue size to know when to spawn a worker or not, creating a chokepoint between producers and consumers
 * in the form of looping around compareAndSet inside {@link AtomicInteger} to update the queue size for each put and get of a task .<br>
 * for short lived tasks at saturation throughput this overhead can be substantial on some platforms.<br>
 * by default: {@link WorkerThreadImpl} is used,
 * {@link LinkedTransferQueue} is used as workQueue , this means that its FIFO per producer.<br>
 *
 * @author gustav trede
 */
public class TestThreadPool extends FixedThreadPool{

    private final AtomicInteger queueSize = new AtomicInteger();
    
    protected final int corePoolsize;
    protected final long idleTimeout;
    protected final TimeUnit timeUnit;

    /**
     *
     */
    public TestThreadPool() {
        this("GrizzlyWorker-", 8, 64, 30, TimeUnit.SECONDS);
    }

    /**
     *
     * @param workerprefixname 
     * @param corePoolsize 
     * @param maxPoolSize 
     * @param keepAliveTime
     * @param timeUnit {@link TimeUnit}
     */
    public TestThreadPool(final String workerprefixname,int corePoolsize,
            int maxPoolSize, long keepAliveTime, TimeUnit timeUnit){
        this(corePoolsize, maxPoolSize, keepAliveTime, timeUnit,new ThreadFactory(){
            private final AtomicInteger c = new AtomicInteger();
            public Thread newThread(Runnable r) {
                Thread t = new WorkerThreadImpl(null, workerprefixname+c.incrementAndGet(), r,0);
                t.setDaemon(true);
                return t;
            }
        });
    }

    /**
     *
     * @param corePoolsize
     * @param maxPoolSize
     * @param keepAliveTime
     * @param timeUnit  {@link TimeUnit}
     * @param threadFactory {@link ThreadFactory}
     */
    public TestThreadPool(int corePoolsize,int maxPoolSize,
            long keepAliveTime, TimeUnit timeUnit, ThreadFactory threadFactory){
            this(corePoolsize, maxPoolSize, keepAliveTime, timeUnit,
                    threadFactory, new LinkedTransferQueue<Runnable>());
    }

    /**
     * 
     * @param corePoolsize
     * @param maxPoolSize
     * @param keepAliveTime
     * @param timeUnit {@link TimeUnit}
     * @param threadFactory {@link ThreadFactory}
     * @param workQueue {@link BlockingQueue}
     */
    public TestThreadPool(int corePoolsize,int maxPoolSize,
            long keepAliveTime, TimeUnit timeUnit, ThreadFactory threadFactory,
            BlockingQueue<Runnable> workQueue) {

        super(workQueue, threadFactory);

        validateNewPoolsize(corePoolsize, maxPoolSize);
        
        if (keepAliveTime< 0 )
            throw new IllegalArgumentException("keepAliveTime < 0");
        if (timeUnit == null)
            throw new IllegalArgumentException("timeUnit == null");
        
        this.corePoolsize  = corePoolsize;
        this.maxPoolSize   = maxPoolSize;
        this.idleTimeout   = keepAliveTime;
        this.timeUnit      = timeUnit;

        aliveworkerCount.set(corePoolsize);
        while (corePoolsize-->0){            
            startWorker(new Worker(null,true));
        }
    }

    private void validateNewPoolsize(int corePoolsize, int maxPoolSize){
        if (maxPoolSize < 1)
            throw new IllegalArgumentException("maxPoolsize < 1");
        if (corePoolsize < 1)
            throw new IllegalArgumentException("corePoolsize < 1");
        if (corePoolsize > maxPoolSize)
            throw new IllegalArgumentException("corePoolsize > maxPoolSize");
    }

    /**
     * {@inheritDoc}
     */
    public void execute(Runnable task) {
        if (task == null){
            throw new IllegalArgumentException("Runnable task is null");
        }

        int ac;
        while((ac=aliveworkerCount.get())<maxPoolSize && queueSize.get()>0 && running){
            if (aliveworkerCount.compareAndSet(ac, ac+1)){
                startWorker(new Worker(task, false));
                return;
            }
        }
        if (running){            
            workQueue.offer(task);
            queueSize.incrementAndGet();
        }
    }


    protected class Worker extends BasicWorker{
        private final boolean core;
        private Runnable firstTask;

        public Worker(Runnable firstTask, boolean core) {
            super();
            this.core = core;
            this.firstTask = firstTask;
        }
    
        protected Runnable getTask() throws InterruptedException {
            Runnable r;
            if (firstTask != null){
                r = firstTask;
                firstTask = null;
            }else{
                 // if maxpoolsize becomes lower during runtime we kill of the
                 // difference, possible abit more since we are not looping around compareAndSet
                 if (!core && aliveworkerCount.get() > maxPoolSize){
                    return null;
                 }
                 r = (core?workQueue.take():workQueue.poll(idleTimeout, timeUnit));
                 if (r != null){
                    queueSize.decrementAndGet();
                 }
            }
            
            return r;
        }

    }

    public int getQueuedTasksCount() {
        return queueSize.get();
    }
  

    public int getCorePoolSize() {
        return corePoolsize;
    }

    /**
     * 
     * @param maxPoolSize
     */
    public void setMaximumPoolSize(int maxPoolSize) {
        synchronized(statelock){
            validateNewPoolsize(corePoolsize, maxPoolSize);
            this.maxPoolSize = maxPoolSize;
        }
    }


    public int getMaximumPoolSize() {
        return maxPoolSize;
    }

}