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

import com.sun.grizzly.Grizzly;
import com.sun.grizzly.monitoring.MonitoringAware;
import com.sun.grizzly.monitoring.MonitoringConfig;
import com.sun.grizzly.monitoring.MonitoringConfigImpl;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Abstract {@link ExtendedThreadPool} implementation.
 *
 * @author Alexey Stashok
 */
public abstract class AbstractThreadPool extends AbstractExecutorService
        implements Thread.UncaughtExceptionHandler, MonitoringAware<ThreadPoolProbe> {

    private static final Logger logger = Grizzly.logger(AbstractThreadPool.class);
    // Min number of worker threads in a pool
    public static final int DEFAULT_MIN_THREAD_COUNT;
    // Max number of worker threads in a pool
    public static final int DEFAULT_MAX_THREAD_COUNT;

    static {
        int processorsBasedThreadCount =
                Runtime.getRuntime().availableProcessors();
        int defaultThreadsCount = processorsBasedThreadCount > 5 ? processorsBasedThreadCount : 5;
        DEFAULT_MIN_THREAD_COUNT = defaultThreadsCount;
        DEFAULT_MAX_THREAD_COUNT = Integer.MAX_VALUE;
    }
    
    // Max number of tasks thread pool can enqueue
    public static final int DEFAULT_MAX_TASKS_QUEUED = -1;
    // Timeout, after which idle thread will be stopped and excluded from pool
    public static final int DEFAULT_IDLE_THREAD_KEEPALIVE_TIMEOUT = 30000;
    
    protected static final Runnable poison = new Runnable() {

        @Override
        public void run() {
        }
    };
    
    private final AtomicInteger nextThreadId = new AtomicInteger();
    protected final Object stateLock = new Object();
    protected final Map<Worker, Long> workers = new HashMap<Worker, Long>();
    protected volatile boolean running = true;
    protected final ThreadPoolConfig config;

    protected final MonitoringConfigImpl<ThreadPoolProbe> monitoringConfig =
            new MonitoringConfigImpl<ThreadPoolProbe>(ThreadPoolProbe.class);
    
    public AbstractThreadPool(ThreadPoolConfig config) {
        if (config.getMaxPoolSize() < 1) {
            throw new IllegalArgumentException("poolsize < 1");
        }

        this.config = config;
        if (config.getThreadFactory() == null) {
            config.setThreadFactory(getDefaultThreadFactory());
        }
    }

    /**
     * must hold statelock while calling this method.
     * @param worker
     */
    protected void startWorker(Worker worker) {
        final Thread thread = config.getThreadFactory().newThread(worker);

        thread.setName(config.getPoolName() + "(" + nextThreadId() + ")");
        thread.setUncaughtExceptionHandler(this);
        thread.setPriority(config.getPriority());
        thread.setDaemon(true);

        worker.t = thread;
        workers.put(worker, System.currentTimeMillis());
        worker.t.start();
    }

    public ThreadPoolConfig getConfig() {
        return config;
    }
    
    public Queue<Runnable> getQueue() {
        return config.getQueue();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Runnable> shutdownNow() {
        synchronized (stateLock) {
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

                ProbeNotificator.notifyThreadPoolStopped(this);
            }
            return drained;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void shutdown() {
        synchronized (stateLock) {
            if (running) {
                running = false;
                poisonAll();
                stateLock.notifyAll();

                ProbeNotificator.notifyThreadPoolStopped(this);
            }
        }
    }

    @Override
    public boolean isShutdown() {
        return !running;
    }

    protected void poisonAll() {
        int size = Math.max(config.getMaxPoolSize(), workers.size()) * 4 / 3;
        final Queue<Runnable> q = getQueue();
        while (size-- > 0) {
            q.offer(poison);
        }
    }

    protected static final void drain(Queue<Runnable> from,
            Collection<Runnable> to) {
        boolean cont = true;
        while (cont) {
            Runnable r = from.poll();
            if (cont = r != null) {
                //resizable fixedpool can have poison
                //from runtime resize (shrink) operation
                if (r != AbstractThreadPool.poison) {
                    to.add(r);//bypassing pool queuelimit
                }
            }
        }
    }

    protected String nextThreadId() {
        return String.valueOf(nextThreadId.incrementAndGet());
    }

    protected void validateNewPoolSize(int corePoolsize, int maxPoolSize) {
        if (maxPoolSize < 1) {
            throw new IllegalArgumentException("maxPoolsize < 1 :" + maxPoolSize);
        }
        if (corePoolsize < 1) {
            throw new IllegalArgumentException("corePoolsize < 1 :" + corePoolsize);
        }
        if (corePoolsize > maxPoolSize) {
            throw new IllegalArgumentException("corePoolsize > maxPoolSize: "
                    + corePoolsize + " > " + maxPoolSize);
        }
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
        ProbeNotificator.notifyTaskCompleted(this, task);
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
        ProbeNotificator.notifyThreadAllocated(this, worker.t);
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
        synchronized (stateLock) {
            workers.remove(worker);
        }

        ProbeNotificator.notifyThreadReleased(this, worker.t);
    }

    /**
     * Method is called by <tt>AbstractThreadPool</tt>, when maximum number of
     * worker threads is reached and task will need to wait in task queue, until
     * one of the threads will be able to process it.
     */
    protected void onMaxNumberOfThreadsReached() {
        ProbeNotificator.notifyMaxNumberOfThreads(this, config.getMaxPoolSize());
    }

    /**
     * Method is called by a thread pool each time new task has been queued to
     * a task queue.
     *
     * @param task
     */
    protected void onTaskQueued(Runnable task) {
        ProbeNotificator.notifyTaskQueued(this, task);
    }

    /**
     * Method is called by a thread pool each time a task has been dequeued from
     * a task queue.
     *
     * @param task
     */
    protected void onTaskDequeued(Runnable task) {
        ProbeNotificator.notifyTaskDequeued(this, task);
    }

    /**
     * Method is called by a thread pool, when new task could not be added
     * to a task queue, because task queue is full.
     * throws  {@link RejectedExecutionException}
     */
    protected void onTaskQueueOverflow() {
        ProbeNotificator.notifyTaskQueueOverflow(this);
        
        throw new RejectedExecutionException(
                "The thread pool's task queue is full, limit: " +
                config.getQueueLimit());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MonitoringConfig<ThreadPoolProbe> getMonitoringConfig() {
        return monitoringConfig;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void uncaughtException(Thread thread, Throwable throwable) {
        logger.log(Level.WARNING,
                "Uncaught thread exception. Thread: " + thread, throwable);
    }

    protected ThreadFactory getDefaultThreadFactory() {
        final AtomicInteger counter = new AtomicInteger();
        
        return new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                return new DefaultWorkerThread(Grizzly.DEFAULT_ATTRIBUTE_BUILDER,
                        config.getPoolName() + "-WorkerThread("
                        + counter.getAndIncrement() + ")", r);
            }
        };
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(256);
        sb.append(getClass().getSimpleName());
        sb.append(" config: [").append(config.toString()).append("]\r\n");
        sb.append(", is-shutdown=").append(isShutdown());
        return sb.toString();
    }

    public abstract class Worker implements Runnable {

        protected Thread t;

        @Override
        public void run() {
            try {
                onWorkerStarted(this);//inside try, to ensure balance
                doWork();
            } finally {
                onWorkerExit(this);
            }
        }

        protected void doWork() {
            final Thread thread = t;
            
            while (true) {
                try {
                    Thread.interrupted();
                    final Runnable r = getTask();
                    if (r == poison || r == null) {
                        return;
                    }
                    onTaskDequeued(r);
                    Throwable error = null;
                    try {
                        beforeExecute(thread, r); //inside try. to ensure balance
                        r.run();
                        onTaskCompletedEvent(r);
                    } catch (Exception e) {
                        error = e;
                    } finally {
                        afterExecute(thread, r, error);
                    }
                } catch (Exception ignore) {
                }
            }
        }

        protected abstract Runnable getTask() throws InterruptedException;
    }
}
