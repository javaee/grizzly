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

import java.util.*;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.localization.LogMessages;
import org.glassfish.grizzly.memory.MemoryManager;
import org.glassfish.grizzly.memory.ThreadLocalPoolProvider;
import org.glassfish.grizzly.monitoring.MonitoringAware;
import org.glassfish.grizzly.monitoring.MonitoringConfig;
import org.glassfish.grizzly.monitoring.DefaultMonitoringConfig;
import org.glassfish.grizzly.monitoring.MonitoringUtils;
import org.glassfish.grizzly.utils.DelayedExecutor;

/**
 * Abstract {@link java.util.concurrent.ExecutorService} implementation.
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

    // "Never stop the thread by timeout" value
    private static final Long NEVER_TIMEOUT = Long.MAX_VALUE;

    static {
        int processorsBasedThreadCount =
                Runtime.getRuntime().availableProcessors();
        DEFAULT_MIN_THREAD_COUNT = processorsBasedThreadCount > 5 ? processorsBasedThreadCount : 5;
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
    
    protected final Object stateLock = new Object();
    
    protected final Map<Worker, Long> workers = new HashMap<Worker, Long>();
    protected volatile boolean running = true;
    protected final ThreadPoolConfig config;
    protected final long transactionTimeoutMillis;
    protected final DelayedExecutor.DelayQueue<Worker> delayedQueue;

    private static final DelayedExecutor.Resolver<Worker> transactionResolver =
            new DelayedExecutor.Resolver<Worker>() {

        @Override
        public boolean removeTimeout(final Worker element) {
            element.transactionExpirationTime = -1;
            return true;
        }

        @Override
        public long getTimeoutMillis(final Worker element) {
            return element.transactionExpirationTime;
        }

        @Override
        public void setTimeoutMillis(final Worker element,
                final long timeoutMillis) {
            element.transactionExpirationTime = timeoutMillis;
        }
    };

    /**
     * ThreadPool probes
     */
    protected final DefaultMonitoringConfig<ThreadPoolProbe> monitoringConfig =
            new DefaultMonitoringConfig<ThreadPoolProbe>(ThreadPoolProbe.class) {

        @Override
        public Object createManagementObject() {
            return createJmxManagementObject();
        }

    };
    
    public AbstractThreadPool(ThreadPoolConfig config) {
        if (config.getMaxPoolSize() < 1) {
            throw new IllegalArgumentException("poolsize < 1");
        }

        this.config = config;
        if (config.getInitialMonitoringConfig().hasProbes()) {
            monitoringConfig.addProbes(config.getInitialMonitoringConfig().getProbes());
        }
        
        if (config.getThreadFactory() == null) {
            config.setThreadFactory(getDefaultThreadFactory());
        }

        transactionTimeoutMillis = config.getTransactionTimeout(TimeUnit.MILLISECONDS);
        final DelayedExecutor transactionMonitor = transactionTimeoutMillis > 0 ?
            config.getTransactionMonitor() : null;

        if (transactionMonitor != null) {
            final DelayedExecutor.Worker<Worker> transactionWorker =
                    new DelayedExecutor.Worker<Worker>() {

                @Override
                public boolean doWork(final Worker worker) {
                    worker.t.interrupt();
                    delayedQueue.add(worker, NEVER_TIMEOUT, TimeUnit.MILLISECONDS);
                    return true;
                }
            };
            delayedQueue = transactionMonitor.createDelayQueue(
                    transactionWorker, transactionResolver);
        } else {
            delayedQueue = null;
        }
    }

    /**
     * must hold statelock while calling this method.
     * @param worker
     */
    protected void startWorker(final Worker worker) {
        final Thread thread = config.getThreadFactory().newThread(worker);

        worker.t = thread;
        workers.put(worker, System.currentTimeMillis());
        worker.t.start();
    }

    /**
     * @return the thread pool configuration
     */
    public ThreadPoolConfig getConfig() {
        return config;
    }
    
    /**
     * @return the task {@link Queue}
     */
    public Queue<Runnable> getQueue() {
        return config.getQueue();
    }

    /**
     * @return the number of allocated threads in the thread pool
     */
    public final int getSize() {
        synchronized (stateLock) {
            return workers.size();
        }
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
                    onTaskCancelled(task);
                }
                poisonAll();
                //try to interrupt their current work so they can get their poison fast
                for (Worker w : workers.keySet()) {
                    w.t.interrupt();
                }

                ProbeNotifier.notifyThreadPoolStopped(this);
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
                ProbeNotifier.notifyThreadPoolStopped(this);
            }
        }
    }

    @Override
    public boolean isShutdown() {
        return !running;
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

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean awaitTermination(final long timeout, final TimeUnit unit)
            throws InterruptedException {
        // see {@link java.util.concurrent.ThreadPoolExecutor#awaitTermination(long, TimeUnit)}

        long millis = unit.toMillis(timeout);
        final long timeEnd = System.currentTimeMillis() + millis;

        synchronized (stateLock) {
            if (isTerminated()) {
                return true;
            }

            for (;;) {
                if (millis < 20) {
                    return false;
                }

                stateLock.wait(millis);

                if (isTerminated()) {
                    return true;
                }

                millis = timeEnd - System.currentTimeMillis();
            }
        }
    }
    
    protected void poisonAll() {
        int size = Math.max(config.getMaxPoolSize(), workers.size()) * 4 / 3;
        final Queue<Runnable> q = getQueue();
        while (size-- > 0) {
            q.offer(poison);
        }
    }

    protected static void drain(Queue<Runnable> from,
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
     * @param worker the {@link Worker}, running the the thread t
     * @param t the thread that will run task r.
     * @param r the task that will be executed.
     */
    protected void beforeExecute(final Worker worker, final Thread t,
            final Runnable r) {
        if (delayedQueue != null) {
            worker.transactionExpirationTime =
                    System.currentTimeMillis() + transactionTimeoutMillis;
        }

        final ClassLoader initial = config.getInitialClassLoader();
        if (initial != null) {
            t.setContextClassLoader(initial);
        }
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
     * @param worker the {@link Worker}, running the the thread t
     * @param thread
     * @param r the runnable that has completed.
     * @param t the exception that caused termination, or null if
     * execution completed normally.
     */
    protected void afterExecute(final Worker worker, final Thread thread,
            final Runnable r,
            final Throwable t) {
        if (delayedQueue != null) {
            worker.transactionExpirationTime = NEVER_TIMEOUT;
        }
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
        ProbeNotifier.notifyTaskCompleted(this, task);
    }

    /**
     * Method is called by {@link Worker}, when it's starting
     * {@link Worker#run()} method execution, which means, that ThreadPool's
     * thread is getting active and ready to process tasks.
     * This method is called from {@link Worker}'s thread.
     *
     * @param worker
     */
    protected void onWorkerStarted(final Worker worker) {
        if (delayedQueue != null) {
            delayedQueue.add(worker, NEVER_TIMEOUT, TimeUnit.MILLISECONDS);
        }
        
        ProbeNotifier.notifyThreadAllocated(this, worker.t);
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

            if (workers.isEmpty()) {
                // notify awaitTermination threads
                stateLock.notifyAll();
            }
        }

        ProbeNotifier.notifyThreadReleased(this, worker.t);
    }

    /**
     * Method is called by <tt>AbstractThreadPool</tt>, when maximum number of
     * worker threads is reached and task will need to wait in task queue, until
     * one of the threads will be able to process it.
     */
    protected void onMaxNumberOfThreadsReached() {
        ProbeNotifier.notifyMaxNumberOfThreads(this, config.getMaxPoolSize());
    }

    /**
     * Method is called by a thread pool each time new task has been queued to
     * a task queue.
     *
     * @param task
     */
    protected void onTaskQueued(Runnable task) {
        ProbeNotifier.notifyTaskQueued(this, task);
    }

    /**
     * Method is called by a thread pool each time a task has been dequeued from
     * a task queue.
     *
     * @param task
     */
    protected void onTaskDequeued(Runnable task) {
        ProbeNotifier.notifyTaskDequeued(this, task);
    }

    /**
     * Method is called by a thread pool each time a dequeued task has been canceled
     * instead of being processed.
     *
     * @param task
     */
    protected void onTaskCancelled(Runnable task) {
        ProbeNotifier.notifyTaskCancelled(this, task);
    }

    /**
     * Method is called by a thread pool, when new task could not be added
     * to a task queue, because task queue is full.
     * throws  {@link RejectedExecutionException}
     */
    protected void onTaskQueueOverflow() {
        ProbeNotifier.notifyTaskQueueOverflow(this);
        
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
                LogMessages.WARNING_GRIZZLY_THREADPOOL_UNCAUGHT_EXCEPTION(thread), throwable);
    }


    Object createJmxManagementObject() {
        return MonitoringUtils.loadJmxObject(
                "org.glassfish.grizzly.threadpool.jmx.ThreadPool", this, 
                AbstractThreadPool.class);
    }

    protected final ThreadFactory getDefaultThreadFactory() {
        final AtomicInteger counter = new AtomicInteger();
        
        return new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                final MemoryManager mm = config.getMemoryManager();
                final ThreadLocalPoolProvider threadLocalPoolProvider;
                
                if (mm instanceof ThreadLocalPoolProvider) {
                    threadLocalPoolProvider = (ThreadLocalPoolProvider) mm;
                } else {
                    threadLocalPoolProvider = null;
                }
                
                final DefaultWorkerThread thread =
                        new DefaultWorkerThread(Grizzly.DEFAULT_ATTRIBUTE_BUILDER,
                        config.getPoolName() + '(' + counter.incrementAndGet() + ')',
                                               ((threadLocalPoolProvider != null) ? threadLocalPoolProvider.createThreadLocalPool() : null),
                                               r);

                thread.setUncaughtExceptionHandler(AbstractThreadPool.this);
                thread.setPriority(config.getPriority());
                thread.setDaemon(config.isDaemon());
                final ClassLoader initial = config.getInitialClassLoader();
                if (initial != null) {
                    thread.setContextClassLoader(initial);
                }
                
                return thread;
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
        protected volatile long transactionExpirationTime;
        
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
                        beforeExecute(this, thread, r); //inside try. to ensure balance
                        r.run();
                        onTaskCompletedEvent(r);
                    } catch (Exception e) {
                        error = e;
                    } finally {
                        afterExecute(this, thread, r, error);
                    }
                } catch (Exception ignore) {
                }
            }
        }

        protected abstract Runnable getTask() throws InterruptedException;
    }
}
