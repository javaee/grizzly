/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2007-2013 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.grizzly.http;

import com.sun.grizzly.util.AbstractThreadPool;
import com.sun.grizzly.util.GrizzlyExecutorService;
import com.sun.grizzly.util.ThreadPoolConfig;
import java.util.List;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Collections;

/**
 * Internal FIFO used by the Worker Threads to pass information
 * between {@link Task} objects.
 *
 * @author Jean-Francois Arcand
 */
public class StatsThreadPool extends GrizzlyExecutorService {
    // Min number of worker threads in a pool

    public static final int DEFAULT_MIN_THREAD_COUNT = 5;
    // Max number of worker threads in a pool
    public static final int DEFAULT_MAX_THREAD_COUNT = 5;
    // Max number of tasks thread pool can enqueue
    public static final int DEFAULT_MAX_TASKS_QUEUED = Integer.MAX_VALUE;
    // Timeout, after which idle thread will be stopped and excluded from pool
    public static final int DEFAULT_IDLE_THREAD_KEEPALIVE_TIMEOUT = 30000;
    /**
     * Port, which is served by this thread pool
     */
    protected int port;
    /**
     * The {@link ThreadPoolStatistic} objects used when gathering statistics.
     */
    private final Object startStopSync = new Object();
    private volatile boolean wasEverStarted;
    protected transient ThreadPoolStatistic threadPoolStat;

    public StatsThreadPool() {
        this(AbstractThreadPool.DEFAULT_MAX_TASKS_QUEUED);
    }

    public StatsThreadPool(int maxTasksCount) {
        this(AbstractThreadPool.DEFAULT_MIN_THREAD_COUNT,
                AbstractThreadPool.DEFAULT_MAX_THREAD_COUNT, maxTasksCount,
                AbstractThreadPool.DEFAULT_IDLE_THREAD_KEEPALIVE_TIMEOUT,
                TimeUnit.MILLISECONDS);
    }

    public StatsThreadPool(int corePoolSize, int maximumPoolSize,
            int maxTasksCount, long keepAliveTime, TimeUnit unit) {
        this("Grizzly", corePoolSize, maximumPoolSize, maxTasksCount,
                keepAliveTime, unit);
    }

    public StatsThreadPool(String name, int corePoolSize, int maximumPoolSize,
            int maxTasksCount, long keepAliveTime, TimeUnit unit) {
        this(new ThreadPoolConfig(name, corePoolSize, maximumPoolSize,
                null, maxTasksCount, keepAliveTime, unit, null,
                Thread.NORM_PRIORITY, null));
    }

    public StatsThreadPool(ThreadPoolConfig config) {
        super(config, false);
        super.config.setThreadFactory(
                new HttpWorkerThreadFactory(this));
    }

    public void start() {
        synchronized (startStopSync) {
            if (!wasEverStarted) {
                setImpl(config);
                wasEverStarted = true;
            }
        }
    }

    public void stop() {
        shutdown();
    }

    @Override
    public final void execute(Runnable r) {
        if (!wasEverStarted) {
            start();
        }

        super.execute(r);
    }


    @Override
    public void shutdown() {
        synchronized (startStopSync) {
            if (!wasEverStarted) {
                return;
            }

            super.shutdown();
        }
    }

    @Override
    public List<Runnable> shutdownNow() {
        synchronized (startStopSync) {
            if (!wasEverStarted) {
                return Collections.<Runnable>emptyList();
            }

            return super.shutdownNow();
        }
    }

    /**
     * Get the port number, which is served by the thread pool
     * @return the port number, which is served by the thread pool
     */
    public int getPort() {
        return port;
    }

    /**
     * Set the port number, which is served by the thread pool
     * @param port the port number, which is served by the thread pool
     */
    public void setPort(int port) {
        this.port = port;
    }

    /**
     * Set the {@link ThreadPoolStatistic} object used
     * to gather statistic;
     */
    public void setStatistic(ThreadPoolStatistic threadPoolStatistic) {
        this.threadPoolStat = threadPoolStatistic;
    }

    /**
     * Return the {@link ThreadPoolStatistic} object used
     * to gather statistic;
     */
    public ThreadPoolStatistic getStatistic() {
        return threadPoolStat;
    }

    @Override
    public GrizzlyExecutorService reconfigure(ThreadPoolConfig config) {
        if (wasEverStarted) {
            return super.reconfigure(config);
        }

        super.config = config;
        return this;
    }

    @Override
    public int getLargestPoolSize() {
        if (!wasEverStarted) {
            return 0;
        }

        return super.getLargestPoolSize();
    }

    @Override
    public int getPoolSize() {
        if (!wasEverStarted) {
            return 0;
        }

        return super.getPoolSize();
    }

    @Override
    public int getQueueSize() {
        if (!wasEverStarted) {
            return 0;
        }

        return super.getQueueSize();
    }

    @Override
    public int getActiveCount() {
        if (!wasEverStarted) {
            return 0;
        }

        return super.getActiveCount();
    }


    @Override
    public int getTaskCount() {
        if (!wasEverStarted) {
            return 0;
        }

        return super.getTaskCount();
    }

    @Override
    public long getCompletedTaskCount() {
        if (!wasEverStarted) {
            return 0;
        }

        return super.getCompletedTaskCount();
    }

    @Override
    public boolean isShutdown() {
        if (!wasEverStarted) {
            return false;
        }

        return super.isShutdown();
    }

    @Override
    public boolean isTerminated() {
        if (!wasEverStarted) {
            return false;
        }

        return super.isTerminated();
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit)
            throws InterruptedException {
        if (!wasEverStarted) {
            return true;
        }

        return super.awaitTermination(timeout, unit);
    }

    /**
     * Create new {@link HttpWorkerThread}.
     */
    protected static class HttpWorkerThreadFactory implements ThreadFactory {

        private final StatsThreadPool statsThreadPool;

        public HttpWorkerThreadFactory(StatsThreadPool statsThreadPool) {
            this.statsThreadPool = statsThreadPool;
        }

        public Thread newThread(final Runnable r) {
            return AccessController.doPrivileged(
                    new PrivilegedAction<Thread>() {

                        public Thread run() {
                            final Thread thread = new HttpWorkerThread(null, r);
                            thread.setContextClassLoader(
                                    HttpWorkerThreadFactory.class.getClassLoader());
                            return thread;
                        }
                    });
        }
    }

    @Override
    public String toString() {
        return super.toString() + ", port=" + port;
    }

    /**
     * Inject toString() to a specific {@link StringBuilder}
     * @param sb
     * 
     * @deprecated
     */
    protected void injectToStringAttributes(StringBuilder sb) {
        sb.append(toString());
    }
}
