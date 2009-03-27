
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
 * uses  WorkerThreadImpl by default.<br>
 * LTQ is used by default for its nice scalability over the lock based alternatives.<br>
 * LTQ gives FIFO per producer.<br>
 *
 * @author gustav trede
 */
public class FixedThreadPool extends AbstractExecutorService{

    protected static final Poison poison = new Poison();
    
    protected final ConcurrentHashMap<WorkerThread,Boolean> workers
            = new ConcurrentHashMap<WorkerThread,Boolean>();

    protected final BlockingQueue<Runnable> workQueue;

    protected final ThreadFactory threadFactory;

    private volatile boolean running = true;

    private final Object shutdownlock = new Object();

    /**
     *  {@link LinkedTransferQueue} is used as workQueue
     * @param size
     */
    public FixedThreadPool(int size) {
        this(size, "WorkerThread");
    }

    /**
     *  {@link LinkedTransferQueue} is used as workQueue
     * 
     * @param size
     * @param threadprefixname
     */
    public FixedThreadPool(int size, final String threadprefixname) {
        this(size, new ThreadFactory() {
            private AtomicInteger c = new AtomicInteger();
            public Thread newThread(Runnable r) {
                return new WorkerThreadImpl(null,threadprefixname+c.incrementAndGet(), r, 0);
            }
        });
    }
    /**
     *  {@link LinkedTransferQueue} is used as workQueue
     * @param size
     */
    public FixedThreadPool(int size, ThreadFactory threadfactory) {
        this(size, threadfactory, new LinkedTransferQueue());
    }
    /**
     *
     * @param fixedsize
     * @param workQueue
     */
    public FixedThreadPool(int fixedsize, ThreadFactory threadfactory,
            BlockingQueue<Runnable> workQueue) {
        if (threadfactory == null)
            throw new IllegalArgumentException("threadfactory parameter is null");
        if (workQueue == null)
            throw new IllegalArgumentException("workQueue parameter is null");
        this.threadFactory = threadfactory;
        this.workQueue = workQueue;
        while(fixedsize-->0){
            WorkerThread wt = new WorkerThread();
            workers.put(wt, Boolean.TRUE);
            wt.t.start();
        }
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
        synchronized(shutdownlock){
            List<Runnable> drained = new ArrayList<Runnable>();
            if (running){
                workQueue.drainTo(drained);
                shutdown();
                for (WorkerThread w:workers.keySet()){
                    //try to interrupt their current work so they can get their poison fast
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
        synchronized(shutdownlock){
            if (running){
                running = false;
                int size = workers.size();
                while(size-->0){
                    workQueue.offer(poison);
                }
            }
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



    protected class WorkerThread implements Runnable{
        final Thread t;

        public WorkerThread() {
            this.t = threadFactory.newThread(this);
            t.setDaemon(true);
        }

        public void run() {
            while(true){                
                try {
                    Thread.interrupted();
                    Runnable r = workQueue.take();
                    if (r == poison){
                        workers.remove(this);
                        return;
                    }
                    r.run();
                }catch(Throwable throwable){

                }
            }
        }

    }

    
   protected static final class Poison implements Runnable{
        public void run() {
            throw new UnsupportedOperationException("Not supported yet.");
        }
   }
}