/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2010 Sun Microsystems, Inc. All rights reserved.
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
 */

package com.sun.grizzly.threadpool.jmx;

import com.sun.grizzly.monitoring.jmx.GrizzlyJmxManager;
import com.sun.grizzly.monitoring.jmx.JmxObject;
import com.sun.grizzly.threadpool.AbstractThreadPool;
import com.sun.grizzly.threadpool.ThreadPoolProbe;
import org.glassfish.gmbal.Description;
import org.glassfish.gmbal.GmbalMBean;
import org.glassfish.gmbal.ManagedAttribute;
import org.glassfish.gmbal.ManagedObject;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

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

    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicInteger currentAllocatedThreadCount = new AtomicInteger();
    private final AtomicInteger totalAllocatedThreadCount = new AtomicInteger();
    private final AtomicInteger currentQueuedTasksCount = new AtomicInteger();
    private final AtomicLong totalCompletedTasksCount = new AtomicLong();
    private final AtomicInteger totalTaskQueueOverflowCount = new AtomicInteger();


    // ------------------------------------------------------------ Constructors


    public ThreadPool(AbstractThreadPool threadPool) {
        this.threadPool = threadPool;
    }


    // -------------------------------------------------- Methods from JmxObject

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onRegister(GrizzlyJmxManager mom, GmbalMBean bean) {
        threadPool.getMonitoringConfig().addProbes(probe);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onUnregister(GrizzlyJmxManager mom) {
        threadPool.getMonitoringConfig().removeProbes(probe);
    }


    // -------------------------------------------------------------- Attributes


    /**
     * @return the Java type of the managed thread pool.
     */
    @ManagedAttribute(id="thread-pool-type")
    public String getPoolType() {
        return threadPool.getClass().getName();
    }


    /**
     * @return <code>true</code> if this pool has been started, otherwise return
     *  <code>false</code>
     */
    @ManagedAttribute(id="thread-pool-started")
    public boolean isStarted() {
        return started.get();
    }


    /**
     * @return the max number of threads allowed by this thread pool.
     */
    @ManagedAttribute(id="thread-pool-max-num-threads")
    public int getMaxAllowedThreads() {
        return threadPool.getConfig().getMaxPoolSize();
    }


    /**
     * @return the core size of this thread pool.
     */
    @ManagedAttribute(id="thread-pool-core-pool-size")
    public int getCorePoolSize() {
        return threadPool.getConfig().getCorePoolSize();
    }


    /**
     * @return the current number of threads maintained by this thread pool.
     */
    @ManagedAttribute(id="thread-pool-allocated-thread-count")
    public int getCurrentAllocatedThreadCount() {
        return currentAllocatedThreadCount.get();
    }


    /**
     * @return the total number of threads that have been allocated over time
     *  by this thread pool.
     */
    @ManagedAttribute(id="thread-pool-total-allocated-thread-count")
    public int getTotalAllocatedThreadCount() {
        return totalAllocatedThreadCount.get();
    }


    /**
     * @return the current number of tasks that have been queued for processing
     *  by this thread pool.
     */
    @ManagedAttribute(id="thread-pool-queued-task-count")
    public int getCurrentTaskCount() {
        return currentQueuedTasksCount.get();
    }


    /**
     * @return the total number of tasks that have been completed by this
     *  thread pool.
     */
    @ManagedAttribute(id="thread-pool-total-completed-tasks-count")
    public long getTotalCompletedTasksCount() {
        return totalCompletedTasksCount.get();
    }


    /**
     * @return the number of times the task queue has reached it's upper limit.
     */
    @ManagedAttribute(id="thread-pool-task-queue-overflow-count")
    public int getTotalTaskQueueOverflowCount() {
        return totalTaskQueueOverflowCount.get();
    }

    // ---------------------------------------------------------- Nested Classes


    private final class JmxThreadPoolProbe implements ThreadPoolProbe {


        // ---------------------------------------- Methods from ThreadPoolProbe


        @Override
        public void onThreadPoolStartEvent(AbstractThreadPool threadPool) {
            started.compareAndSet(false, true);
        }

        @Override
        public void onThreadPoolStopEvent(AbstractThreadPool threadPool) {
            started.compareAndSet(true, false);
        }

        @Override
        public void onThreadAllocateEvent(AbstractThreadPool threadPool, Thread thread) {
            currentAllocatedThreadCount.incrementAndGet();
            totalAllocatedThreadCount.incrementAndGet();
        }

        @Override
        public void onThreadReleaseEvent(AbstractThreadPool threadPool, Thread thread) {
            currentAllocatedThreadCount.decrementAndGet();
        }

        @Override
        public void onMaxNumberOfThreadsEvent(AbstractThreadPool threadPool, int maxNumberOfThreads) {
            //To change body of implemented methods use File | Settings | File Templates.
        }

        @Override
        public void onTaskQueueEvent(AbstractThreadPool threadPool, Runnable task) {
            currentQueuedTasksCount.incrementAndGet();
        }

        @Override
        public void onTaskDequeueEvent(AbstractThreadPool threadPool, Runnable task) {
            currentQueuedTasksCount.decrementAndGet();
        }

        @Override
        public void onTaskCompleteEvent(AbstractThreadPool threadPool, Runnable task) {
            totalCompletedTasksCount.incrementAndGet();
        }

        @Override
        public void onTaskQueueOverflowEvent(AbstractThreadPool threadPool) {
            totalTaskQueueOverflowCount.incrementAndGet();
        }

    } // END JmxThreadPoolProbe

}
