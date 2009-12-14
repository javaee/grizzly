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

import com.sun.grizzly.util.ByteBufferFactory.ByteBufferType;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Abstract {@link ExtendedThreadPool} implementation.
 * 
 * @author Alexey Stashok
 */
public abstract class AbstractThreadPool extends AbstractExecutorService
        implements ExtendedThreadPool, Thread.UncaughtExceptionHandler  {

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

    protected abstract String nextThreadId();

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
            throw new IllegalArgumentException("maxPoolsize < 1");
        if (corePoolsize < 1)
            throw new IllegalArgumentException("corePoolsize < 1");
        if (corePoolsize > maxPoolSize)
            throw new IllegalArgumentException("corePoolsize > maxPoolSize");
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
    protected void beforeExecute(Thread t, Runnable r) { }

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
    protected void afterExecute(Thread thread,Runnable r, Throwable t) { }

    /**
     * Method is called by {@link Worker}, when it's starting
     * {@link Worker#run()} method execution, which means, that ThreadPool's
     * thread is getting active and ready to process tasks.
     * This method is called from {@link Worker}'s thread.
     *
     * @param worker
     */
    protected void onWorkerStarted(Worker worker) {
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
    }

    /**
     * Method is called by <tt>AbstractThreadPool</tt>, when maximum number of
     * worker threads is reached and task will need to wait in task queue, until
     * one of the threads will be able to process it.
     */
    protected void onMaxNumberOfThreadsReached() {
    }

    /**
     * Method is called by a thread pool each time new task has been queued to
     * a task queue.
     *
     * @param task
     */
    protected void onTaskQueued(Runnable task) {
    }

    /**
     * Method is called by a thread pool each time a task has been dequeued from
     * a task queue.
     *
     * @param task
     */
    protected void onTaskDequeued(Runnable task) {
    }

    /**
     * Method is called by a thread pool, when new task could not be added
     * to a task queue, because task queue is full.
     */
    protected void onTaskQueueOverflow() {
    }
    
    /**
     * {@inheritDoc}
     */
    public void uncaughtException(Thread thread, Throwable throwable) {
        LoggerUtils.getLogger().log(Level.WARNING,
                "Uncaught thread exception. Thread: " + thread, throwable);
    }

    public abstract class Worker implements Runnable {
        protected Thread t;

        public void run() {
            onWorkerStarted(this);
            try {
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
                    Runnable r = getTask();
                    if (r == poison || r == null){
                        return;
                    }
                    onTaskDequeued(r);
                    beforeExecute(t_, r);
                    try {
                        r.run();
                    } catch(Throwable throwable) {
                        afterExecute(t_, r, throwable);
                    } finally {
                        afterExecute(t_, r, null);
                    }
                } catch (Throwable throwable) {
                }
            }
        }

        protected abstract Runnable getTask() throws InterruptedException;
    }
}
