/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2010 Oracle and/or its affiliates. All rights reserved.
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * Thread pool, from which it's possible to gather statistics information,
 * and finer control its execution.
 *
 * @author Alexey Stashok
 */
public interface ExtendedThreadPool extends ExecutorService {

    /**
     * Returns the approximate number of threads that are actively
     * executing tasks.
     *
     * @return the number of threads
     */
    public int getActiveCount();


    /**
     * Returns the approximate total number of tasks that have been
     * scheduled for execution. Because the states of tasks and
     * threads may change dynamically during computation, the returned
     * value is only an approximation, but one that does not ever
     * decrease across successive calls.
     *
     * @return the number of tasks
     */
    public int getTaskCount();

    /**
     * Returns the approximate total number of tasks that have
     * completed execution. Because the states of tasks and threads
     * may change dynamically during computation, the returned value
     * is only an approximation, but one that does not ever decrease
     * across successive calls.
     *
     * @return the number of tasks
     */
    public long getCompletedTaskCount();

    /**
     * Returns the core number of threads.
     *
     * @return the core number of threads
     * @see #setCorePoolSize
     */
    public int getCorePoolSize();

    /**
     * Sets the core number of threads.  This overrides any value set
     * in the constructor.  If the new value is smaller than the
     * current value, excess existing threads will be terminated when
     * they next become idle. If larger, new threads will, if needed,
     * be started to execute any queued tasks.
     *
     * @param corePoolSize the new core size
     * @throws IllegalArgumentException if <tt>corePoolSize</tt>
     * less than zero
     * @see #getCorePoolSize
     */
    public void setCorePoolSize(int corePoolSize);

    /**
     * Returns the largest number of threads that have ever
     * simultaneously been in the pool.
     *
     * @return the number of threads
     */
    public int getLargestPoolSize();

    /**
     * Returns the current number of threads in the pool.
     *
     * @return the number of threads
     */
    public int getPoolSize();

    /**
     * Returns the task queue used by this executor. Access to the
     * task queue is intended primarily for debugging and monitoring.
     * This queue may be in active use.  Retrieving the task queue
     * does not prevent queued tasks from executing.
     *
     * @return the task queue
     */
    public Queue<Runnable> getQueue();

    /**
     * Returns the number of tasks, which are currently waiting in the queue.
     *
     * @return the number of tasks, which are currently waiting in the queue.
     */
    public int getQueueSize();

    /**
     * Returns the thread keep-alive time, which is the amount of time
     * which threads in excess of the core pool size may remain
     * idle before being terminated.
     *
     * @param unit the desired time unit of the result
     * @return the time limit
     * @see #setKeepAliveTime
     */
    public long getKeepAliveTime(TimeUnit unit);

    /**
     * Sets the time limit for which threads may remain idle before
     * being terminated.  If there are more than the core number of
     * threads currently in the pool, after waiting this amount of
     * time without processing a task, excess threads will be
     * terminated.  This overrides any value set in the constructor.
     * @param time the time to wait.  A time value of zero will cause
     * excess threads to terminate immediately after executing tasks.
     * @param unit  the time unit of the time argument
     * @throws IllegalArgumentException if time less than zero
     * @see #getKeepAliveTime
     */
    public void setKeepAliveTime(long time, TimeUnit unit);

    /**
     * Returns the maximum allowed number of threads.
     *
     * @return the maximum allowed number of threads
     * @see #setMaximumPoolSize
     */
    public int getMaximumPoolSize();

    /**
     * Sets the maximum allowed number of threads. This overrides any
     * value set in the constructor. If the new value is smaller than
     * the current value, excess existing threads will be
     * terminated when they next become idle.
     *
     * @param maximumPoolSize the new maximum
     * @throws IllegalArgumentException if maximumPoolSize less than zero or
     * the {@link #getCorePoolSize core pool size}
     * @see #getMaximumPoolSize
     */
    public void setMaximumPoolSize(int maximumPoolSize);

    public int getMaxQueuedTasksCount();


    /**
     *
     * @param maxTasksCount
     */
    public void setMaxQueuedTasksCount(int maxTasksCount);


    public int getPriority();

    public void setPriority(int priority);

    public String getName();

    /**
     *
     * @param name
     */
    public void setName(String name);

    /* Getting and setting tunable parameters */

    /**
     * Sets the thread factory used to create new threads.
     *
     * @param threadFactory the new thread factory
     * @throws NullPointerException if threadFactory is null
     * @see #getThreadFactory
     */
    public void setThreadFactory(ThreadFactory threadFactory);

    /**
     * Returns the thread factory used to create new threads.
     *
     * @return the current thread factory
     * @see #setThreadFactory
     */
    public ThreadFactory getThreadFactory();

}
