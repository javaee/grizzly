
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
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
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
public class FixedThreadPool extends AbstractExecutorService{

    protected static final Runnable poison = new Runnable(){public void run(){}};    
    
    protected final ConcurrentHashMap<BasicWorker,Boolean> workers
            = new ConcurrentHashMap<BasicWorker,Boolean>();

    /**
     * exits for use by subclasses, does not impact the performance of fixed pool
     */
    protected final AtomicInteger aliveworkerCount = new AtomicInteger();
    
    protected final BlockingQueue<Runnable> workQueue;

    protected final ThreadFactory threadFactory;
    
    protected final Object statelock = new Object();

    protected volatile int maxPoolSize;

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
     * @param workerprefixname 
     */
    public FixedThreadPool(int size, final String workerprefixname) {
        this(size, new ThreadFactory(){
            private final AtomicInteger c = new AtomicInteger();
            public Thread newThread(Runnable r) {
                Thread t = new WorkerThreadImpl(null,workerprefixname+c.incrementAndGet(),r,0);
                t.setDaemon(true);
                return t;
            }
        });
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
        if (threadFactory == null)
            throw new IllegalArgumentException("threadFactory == null");
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
            workQueue.offer(command);
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
                poisonAll();
                //try to interrupt their current work so they can get their poison fast
                for (BasicWorker w:workers.keySet()){
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

    
    protected class BasicWorker implements Runnable{
        Thread t;

        public BasicWorker() {            
        }

        public void run() {            
            try{
                dowork();
            }finally{
                aliveworkerCount.decrementAndGet();
                workers.remove(this);
            }
        }

        protected void dowork(){
            while(true){
                try {
                    Thread.interrupted();
                    Runnable r = getTask();
                    if (r == poison || r == null){
                        return;
                    }
                    r.run();
                }catch(Throwable throwable){

                }
            }
        }

        protected Runnable getTask() throws InterruptedException{
            return workQueue.take();
        }
    }

}