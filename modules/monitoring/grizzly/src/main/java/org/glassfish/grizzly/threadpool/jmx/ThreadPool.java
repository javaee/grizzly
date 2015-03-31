/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011-2015 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.threadpool.jmx;

import org.glassfish.grizzly.monitoring.jmx.JmxObject;
import org.glassfish.grizzly.threadpool.AbstractThreadPool;
import org.glassfish.grizzly.threadpool.ThreadPoolProbe;
import org.glassfish.gmbal.Description;
import org.glassfish.gmbal.GmbalMBean;
import org.glassfish.gmbal.ManagedAttribute;
import org.glassfish.gmbal.ManagedObject;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.glassfish.grizzly.jmxbase.GrizzlyJmxManager;

/**
 * JMX managed object for Grizzly thread pool implementations.
 *
 * @since 2.0
 */
@ManagedObject
@Description("Grizzly ThreadPool (typically shared between Transport instances).")
public class ThreadPool extends JmxObject {

    private final AbstractThreadPool threadPool;
    private final ThreadPoolProbe probe = new JmxThreadPoolProbe();

    private final AtomicInteger totalAllocatedThreadCount = new AtomicInteger();
    private final AtomicInteger busyThreadsCount = new AtomicInteger();
    private final AtomicLong totalCompletedTasksCount = new AtomicLong();
    private final AtomicInteger totalTaskQueueOverflowCount = new AtomicInteger();


    // ------------------------------------------------------------ Constructors


    public ThreadPool(AbstractThreadPool threadPool) {
        this.threadPool = threadPool;
    }


    // -------------------------------------------------- Methods from JmxObject


    @Override
    public String getJmxName() {
        return threadPool.getConfig().getPoolName();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onRegister(GrizzlyJmxManager mom, GmbalMBean bean) {
        threadPool.getMonitoringConfig().addProbes(probe);
        totalAllocatedThreadCount.set(threadPool.getSize()); // set the initial value
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onDeregister(GrizzlyJmxManager mom) {
        threadPool.getMonitoringConfig().removeProbes(probe);
    }


    // -------------------------------------------------------------- Attributes


    /**
     * @return the Java type of the managed thread pool.
     */
    @ManagedAttribute(id="thread-pool-type")
    @Description("The Java type of the thread pool implementation being used.")
    public String getPoolType() {
        return threadPool.getClass().getName();
    }


    /**
     * @return <code>true</code> if this pool has been started, otherwise return
     *  <code>false</code>
     */
    @ManagedAttribute(id="thread-pool-started")
    @Description("Indiciates whether or not the thread pool has been started.")
    public boolean isStarted() {
        return !threadPool.isShutdown();
    }


    /**
     * @return the max number of threads allowed by this thread pool.
     */
    @ManagedAttribute(id="thread-pool-max-num-threads")
    @Description("The maximum number of the threads allowed by this thread pool.")
    public int getMaxAllowedThreads() {
        return threadPool.getConfig().getMaxPoolSize();
    }


    /**
     * @return the core size of this thread pool.
     */
    @ManagedAttribute(id="thread-pool-core-pool-size")
    @Description("The initial/minimum number of threads managed by this thread pool.")
    public int getCorePoolSize() {
        return threadPool.getConfig().getCorePoolSize();
    }


    /**
     * @return the current number of threads maintained by this thread pool.
     */
    @ManagedAttribute(id="thread-pool-allocated-thread-count")
    @Description("The current number of threads managed by this thread pool.")
    public int getCurrentAllocatedThreadCount() {
        return threadPool.getSize();
    }


    /**
     * @return the total number of threads that have been allocated over time
     *  by this thread pool.
     */
    @ManagedAttribute(id="thread-pool-total-allocated-thread-count")
    @Description("The total number of threads allocated during the lifetime of this thread pool.")
    public int getTotalAllocatedThreadCount() {
        return totalAllocatedThreadCount.get();
    }

    /**
     * @return the number of threads, which are currently busy processing the tasks.
     * @since 2.3.20
     */
    @ManagedAttribute(id="thread-pool-busy-thread-count")
    @Description("The number of threads, which are currently busy processing the tasks.")
    public int getBusyThreadCount() {
        return busyThreadsCount.get();
    }
    /**
     * @return the current number of tasks that have been queued for processing
     *  by this thread pool.
     */
    @ManagedAttribute(id="thread-pool-queued-task-count")
    @Description("The number of tasks currently being queued by this thread pool.")
    public int getCurrentTaskCount() {
        return threadPool.getQueue().size();
    }


    /**
     * @return the total number of tasks that have been completed by this
     *  thread pool.
     */
    @ManagedAttribute(id="thread-pool-total-completed-tasks-count")
    @Description("The total number of tasks that have been processed by this thread pool.")
    public long getTotalCompletedTasksCount() {
        return totalCompletedTasksCount.get();
    }


    /**
     * @return the number of times the task queue has reached it's upper limit.
     */
    @ManagedAttribute(id="thread-pool-task-queue-overflow-count")
    @Description("The total number of times the task queue of this thread pool has been saturated.")
    public int getTotalTaskQueueOverflowCount() {
        return totalTaskQueueOverflowCount.get();
    }

    // ---------------------------------------------------------- Nested Classes


    private final class JmxThreadPoolProbe extends ThreadPoolProbe.Adapter {


        // ---------------------------------------- Methods from ThreadPoolProbe


        @Override
        public void onThreadAllocateEvent(AbstractThreadPool threadPool, Thread thread) {
            totalAllocatedThreadCount.incrementAndGet();
        }

        @Override
        public void onTaskDequeueEvent(AbstractThreadPool threadPool, Runnable task) {
            busyThreadsCount.incrementAndGet();
        }

        @Override
        public void onTaskCancelEvent(AbstractThreadPool threadPool, Runnable task) {
            decBusyThreadCount();
        }
        
        @Override
        public void onTaskCompleteEvent(AbstractThreadPool threadPool, Runnable task) {
            totalCompletedTasksCount.incrementAndGet();
            decBusyThreadCount();
        }

        @Override
        public void onTaskQueueOverflowEvent(AbstractThreadPool threadPool) {
            totalTaskQueueOverflowCount.incrementAndGet();
        }
        
        private void decBusyThreadCount() {
            final int val = busyThreadsCount.decrementAndGet();
            if (val < 0) {
                // it may happen if monitoring probe was added at the time, when
                // the task had beed dequeued.
                // So we have to re-balance the counter, sooner or later it will
                // get the proper value.
                busyThreadsCount.compareAndSet(val, 0);
            }
        }        
    } // END JmxThreadPoolProbe

}
