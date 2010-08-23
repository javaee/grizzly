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

import com.sun.grizzly.util.ByteBufferFactory.ByteBufferType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Abstract {@link ExtendedThreadPool} implementation.
 * 
 * @author Alexey Stashok
 */
public abstract class AbstractThreadPool extends AbstractExecutorService
        implements ExtendedThreadPool, Thread.UncaughtExceptionHandler  {

    private static final Logger LOGGER = LoggerUtils.getLogger();

    // Min number of worker threads in a pool
    public static int DEFAULT_MIN_THREAD_COUNT = 5;

    // Max number of worker threads in a pool
    public static int DEFAULT_MAX_THREAD_COUNT = 5;

    // Max number of tasks thread pool can enqueue
    public static int DEFAULT_MAX_TASKS_QUEUED = Integer.MAX_VALUE;

    // Timeout, after which idle thread will be stopped and excluded from pool
    public static int DEFAULT_IDLE_THREAD_KEEPALIVE_TIMEOUT = 30000;

    protected static final Runnable poison = new Runnable(){public void run(){}};

    /**
     * The initial ByteBuffer size for newly created WorkerThread instances
     */
    protected volatile int initialByteBufferSize = WorkerThreadImpl.DEFAULT_BYTE_BUFFER_SIZE;

    /**
     * The {@link ByteBufferType}
     */
    protected volatile ByteBufferType byteBufferType = WorkerThreadImpl.DEFAULT_BYTEBUFFER_TYPE;

    protected volatile String name = "GrizzlyWorker";

    /**
     * Threads priority
     */
    protected volatile int priority = Thread.NORM_PRIORITY;

    protected volatile int corePoolSize;
    protected volatile int maxPoolSize;

    protected volatile long keepAliveTime;

    protected volatile ThreadFactory threadFactory;

    private final AtomicInteger nextthreadID = new AtomicInteger();

    protected final ThreadPoolMonitoringProbe probe;

    protected final Object statelock = new Object();

    protected final Map<Worker, Long> workers = new HashMap<Worker, Long>();

    protected volatile boolean running = true;

    protected int currentPoolSize;
    protected int activeThreadsCount;

    public AbstractThreadPool(ThreadPoolMonitoringProbe probe, String name,
            ThreadFactory threadFactory, int maxPoolSize){        
        if (maxPoolSize < 1) {
            throw new IllegalArgumentException("poolsize < 1");
        }
        setName(name);
        corePoolSize = -1;
        this.maxPoolSize = maxPoolSize;
        this.probe = probe;
        this.threadFactory = threadFactory != null ? 
            threadFactory : getDefaultThreadFactory();
    }

    /**
     * must hold statelock while calling this method.
     * @param wt
     */
    protected void startWorker(Worker wt) {
        final Thread thread = threadFactory.newThread(wt);

        thread.setName(getName() + "(" + nextThreadId() + ")");
        thread.setUncaughtExceptionHandler(this);
        thread.setPriority(getPriority());
        thread.setDaemon(true);

        if (thread instanceof WorkerThreadImpl) {
            final WorkerThreadImpl workerThread = (WorkerThreadImpl) thread;
            workerThread.setByteBufferType(getByteBufferType());
            workerThread.setInitialByteBufferSize(getInitialByteBufferSize());
        }

        wt.t = thread;
        workers.put(wt, System.currentTimeMillis());
        wt.t.start();
    }
    
    /**
     * {@inheritDoc}
     */
    public List<Runnable> shutdownNow() {
        synchronized (statelock) {
            List<Runnable> drained = new ArrayList<Runnable>();
            if (running) {
                running = false;
                drain(getQueue(), drained);
                for (Runnable task : drained) {
                    onTaskDequeued(task);
                }
                poisonAll();
                //try to interrupt their current work so they can get their poison fast
                for (Worker w : workers.keySet()) {
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
        synchronized (statelock) {
            if (running) {
                running = false;
                poisonAll();
                statelock.notifyAll();
            }
        }
    }

    public boolean isShutdown() {
        return !running;
    }

    protected void poisonAll() {
        int size = Math.max(maxPoolSize, workers.size()) * 4 / 3;
        final Queue<Runnable> q = getQueue();
        while (size-- > 0) {
            q.offer(poison);
        }
    }

    protected static final void drain(
            Queue<Runnable> from,Collection<Runnable> too){
        boolean cont = true;
        while(cont){
            Runnable r = from.poll();
            if (cont = r!=null){
                //resizable fixedpool can have poison
                //from runtime resize (shrink) operation
                if (r != AbstractThreadPool.poison){
                    too.add(r);//bypassing pool queuelimit
                }
            }
        }
    }

    protected String nextThreadId(){
        return String.valueOf(nextthreadID.incrementAndGet());
    }
    
    /**
     * {@inheritDoc}
     */
    public String getName() {
        return name;
    }

    /**
     * {@inheritDoc}
     */
    public void setName(String name) {
        if (name == null)
            throw new IllegalArgumentException("name == null");
        if (name.length() == 0)
            throw new IllegalArgumentException("name 0 length");
        this.name = name;
    }

    /**
     * {@inheritDoc}
     */
    public int getCorePoolSize() {
        return corePoolSize;
    }

    /**
     * {@inheritDoc}
     */
    public void setCorePoolSize(int corePoolSize) {
        this.corePoolSize = corePoolSize;
    }

    /**
     * {@inheritDoc}
     */
    public int getMaximumPoolSize() {
        return maxPoolSize;
    }

    /**
     * {@inheritDoc}
     */
    public void setMaximumPoolSize(int maximumPoolSize) {
        this.maxPoolSize = maximumPoolSize;
    }

    /**
     * {@inheritDoc}
     */
    public long getKeepAliveTime(TimeUnit unit) {
        return unit.convert(keepAliveTime, TimeUnit.MILLISECONDS);
    }

    /**
     * {@inheritDoc}
     */
    public void setKeepAliveTime(long time, TimeUnit unit) {
        keepAliveTime = TimeUnit.MILLISECONDS.convert(time, unit);
    }

    /**
     * {@inheritDoc}
     */
    public void setThreadFactory(ThreadFactory threadFactory) {
        if (threadFactory == null){
            throw new IllegalArgumentException("threadFactory is null");
        }
        this.threadFactory = threadFactory;
    }

    /**
     * {@inheritDoc}
     */
    public ThreadFactory getThreadFactory() {
        return threadFactory;
    }


    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public ByteBufferType getByteBufferType() {
        return byteBufferType;
    }

    public void setByteBufferType(ByteBufferType byteBufferType) {
        this.byteBufferType = byteBufferType;
    }

    public int getInitialByteBufferSize() {
        return initialByteBufferSize;
    }

    public void setInitialByteBufferSize(int initialByteBufferSize) {
        this.initialByteBufferSize = initialByteBufferSize;
    }

    protected void validateNewPoolSize(int corePoolsize, int maxPoolSize){
        if (maxPoolSize < 1)
            throw new IllegalArgumentException("maxPoolsize < 1 :"+maxPoolSize);
        if (corePoolsize < 1)
            throw new IllegalArgumentException("corePoolsize < 1 :"+corePoolsize);
        if (corePoolsize > maxPoolSize)
            throw new IllegalArgumentException("corePoolsize > maxPoolSize: "+
                    corePoolsize+" > "+maxPoolSize);
    }
    
    /**
     * Method invoked prior to executing the given Runnable in the
     * given thread.  This method is invoked by thread <tt>t</tt> that
     * will execute task <tt>r</tt>, and may be used to re-initialize
     * ThreadLocals, or to perform logging.
     *
     * <p>This implementation does nothing, but may be customized in
     * subclasses. Note: To properly nest multiple overridings, subclasses
     * should generally invoke <tt>super.beforeExecute</tt> at the end of
     * this method.
     *
     * @param t the thread that will run task r.
     * @param r the task that will be executed.
     */
    protected void beforeExecute(Thread t, Runnable r) {
        if (t instanceof WorkerThreadImpl)
            ((WorkerThreadImpl) t).createByteBuffer(false);
    }

    /**
     * Method invoked upon completion of execution of the given Runnable.
     * This method is invoked by the thread that executed the task. If
     * non-null, the Throwable is the uncaught <tt>RuntimeException</tt>
     * or <tt>Error</tt> that caused execution to terminate abruptly.
     *
     * <p><b>Note:</b> When actions are enclosed in tasks (such as
     * {@link java.util.concurrent.FutureTask}) either explicitly or via methods such as
     * <tt>submit</tt>, these task objects catch and maintain
     * computational exceptions, and so they do not cause abrupt
     * termination, and the internal exceptions are <em>not</em>
     * passed to this method.
     *
     * <p>This implementation does nothing, but may be customized in
     * subclasses. Note: To properly nest multiple overridings, subclasses
     * should generally invoke <tt>super.afterExecute</tt> at the
     * beginning of this method.
     *
     * @param thread 
     * @param r the runnable that has completed.
     * @param t the exception that caused termination, or null if
     * execution completed normally.
     */
    protected void afterExecute(Thread thread, Runnable r, Throwable t) {
        if (thread instanceof WorkerThreadImpl)
            ((WorkerThreadImpl)thread).reset();
    }


    /**
     * <p>
     * This method will be invoked when a the specified {@link Runnable} has
     * completed execution.
     * </p>
     *
     * @param task the unit of work that has completed processing
     */
    protected void onTaskCompletedEvent(Runnable task) {
        if (probe != null) {
            probe.onTaskCompletedEvent(task);
        }
    }


    /**
     * Method is called by {@link Worker}, when it's starting
     * {@link Worker#run()} method execution, which means, that ThreadPool's
     * thread is getting active and ready to process tasks.
     * This method is called from {@link Worker}'s thread.
     *
     * @param worker
     */
    protected void onWorkerStarted(Worker worker) {
        if (probe != null){
            probe.threadAllocatedEvent(name,worker.t);
        }
    }

    /**
     * Method is called by {@link Worker}, when it's completing
     * {@link Worker#run()} method execution, which in most cases means,
     * that ThreadPool's thread will be released. This method is called from
     * {@link Worker}'s thread.
     *
     * @param worker
     */
    protected void onWorkerExit(Worker worker) {
        synchronized (statelock) {
            currentPoolSize--;
            activeThreadsCount--;
            workers.remove(worker);
        }
        if (probe != null){
            probe.threadReleasedEvent(name, worker.t);
        }
    }

    /**
     * Method is called by <tt>AbstractThreadPool</tt>, when maximum number of
     * worker threads is reached and task will need to wait in task queue, until
     * one of the threads will be able to process it.
     */
    protected void onMaxNumberOfThreadsReached() {
      if (probe != null){
            probe.maxNumberOfThreadsReachedEvent(name, maxPoolSize);
        }    
    }

    /**
     * Method is called by a thread pool each time new task has been queued to
     * a task queue.
     *
     * @param task
     */
    protected void onTaskQueued(Runnable task) {
      if (probe != null){
            probe.onTaskQueuedEvent(task);
        }
    }

    /**
     * Method is called by a thread pool each time a task has been dequeued from
     * a task queue.
     *
     * @param task
     */
    protected void onTaskDequeued(Runnable task) {
      if (probe != null){
            probe.onTaskDequeuedEvent(task);
        }
    }

    /**
     * Method is called by a thread pool, when new task could not be added
     * to a task queue, because task queue is full.
     * throws  {@link RejectedExecutionException}
     */
    protected void onTaskQueueOverflow() {
        if (probe != null){
            probe.onTaskQueueOverflowEvent(name);
        }
        throw new RejectedExecutionException(
                "The thread pool's task queue is full, limit: "+getMaxQueuedTasksCount());
    }
    
    /**
     * {@inheritDoc}
     */
    public void uncaughtException(Thread thread, Throwable throwable) {

        LoggerUtils.getLogger().log(Level.WARNING,
                 "Uncaught thread exception. Thread: " + thread, throwable);
    }

    protected ThreadFactory getDefaultThreadFactory(){
        return new ThreadFactory(){            
            public Thread newThread(Runnable r) {
                return new WorkerThreadImpl(AbstractThreadPool.this,
                    getName() + "-WorkerThread(" +
                    nextThreadId() + ")", r,
                    getInitialByteBufferSize());
            }
        };
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(256);
        sb.append(getClass().getSimpleName()+":");
        sb.append("name=").append(name);
        sb.append(", queuesize=").append(getQueueSize());
        sb.append(", is-shutdown=").append(isShutdown());
        return sb.toString();
    }
    
    public abstract class Worker implements Runnable {
        protected Thread t;

        public void run() {            
            try {
                onWorkerStarted(this);//inside try, to ensure balance
                doWork();
            } finally {
                onWorkerExit(this);
            }
        }

        protected void doWork(){
            final Thread t_=t;
            while(true) {
                try {                    
                    Thread.interrupted();
                    final Runnable r = getTask();
                    if (r == poison || r == null){
                        return;
                    }
                    onTaskDequeued(r);
                    Throwable error = null;
                    try {
                        beforeExecute(t_, r); //inside try. to ensure balance
                        r.run();
                        onTaskCompletedEvent(r);
                    } catch(Throwable throwable) {
                        error = throwable;
                    } finally {
                        afterExecute(t_, r, error);
                    }
                } catch (Throwable throwable) {
                }
            }
        }

        protected abstract Runnable getTask() throws InterruptedException;
    }
}
