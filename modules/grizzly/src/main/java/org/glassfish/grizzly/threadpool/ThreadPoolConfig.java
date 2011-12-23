/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2011 Oracle and/or its affiliates. All rights reserved.
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

import org.glassfish.grizzly.memory.MemoryManager;

import java.util.Queue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import org.glassfish.grizzly.monitoring.MonitoringConfigImpl;
import org.glassfish.grizzly.utils.DelayedExecutor;

/**
 * @author Oleksiy Stashok
 * @author gustav trede
 */
public class ThreadPoolConfig {
    private static final ThreadPoolConfig DEFAULT = new ThreadPoolConfig(
            "Grizzly", AbstractThreadPool.DEFAULT_MIN_THREAD_COUNT,
            AbstractThreadPool.DEFAULT_MAX_THREAD_COUNT,
            null, AbstractThreadPool.DEFAULT_MAX_TASKS_QUEUED,
            AbstractThreadPool.DEFAULT_IDLE_THREAD_KEEPALIVE_TIMEOUT,
            TimeUnit.MILLISECONDS,
            null, Thread.NORM_PRIORITY, null, null, -1);

    public static ThreadPoolConfig defaultConfig() {
        return DEFAULT.copy();
    }

    protected String poolName;
    protected int corePoolSize;
    protected int maxPoolSize;
    protected Queue<Runnable> queue;
    protected int queueLimit = -1;
    protected long keepAliveTimeMillis;
    protected ThreadFactory threadFactory;
    protected int priority = Thread.MAX_PRIORITY;
    protected MemoryManager mm;
    protected DelayedExecutor transactionMonitor;
    protected long transactionTimeoutMillis;

    /**
     * Thread pool probes
     */
    protected final MonitoringConfigImpl<ThreadPoolProbe> threadPoolMonitoringConfig;
            
    
    public ThreadPoolConfig(
            String poolName,
            int corePoolSize,
            int maxPoolSize,
            Queue<Runnable> queue,
            int queueLimit,
            long keepAliveTime,
            TimeUnit timeUnit,
            ThreadFactory threadFactory,
            int priority,
            MemoryManager mm,
            DelayedExecutor transactionMonitor,
            long transactionTimeoutMillis) {
        this.poolName        = poolName;
        this.corePoolSize    = corePoolSize;
        this.maxPoolSize     = maxPoolSize;
        this.queue           = queue;
        this.queueLimit      = queueLimit;
        if (keepAliveTime > 0) {
            this.keepAliveTimeMillis =
                    TimeUnit.MILLISECONDS.convert(keepAliveTime, timeUnit);
        } else {
            keepAliveTimeMillis = keepAliveTime;
        }

        this.threadFactory   = threadFactory;
        this.priority        = priority;
        this.mm              = mm;
        this.transactionMonitor = transactionMonitor;
        this.transactionTimeoutMillis = transactionTimeoutMillis;
        
        threadPoolMonitoringConfig = new MonitoringConfigImpl<ThreadPoolProbe>(
                ThreadPoolProbe.class);
    }

    public ThreadPoolConfig(ThreadPoolConfig cfg) {
        this.queue           = cfg.queue;
        this.threadFactory   = cfg.threadFactory;
        this.poolName        = cfg.poolName;
        this.priority        = cfg.priority;
        this.maxPoolSize     = cfg.maxPoolSize;
        this.queueLimit      = cfg.queueLimit;
        this.corePoolSize    = cfg.corePoolSize;
        this.keepAliveTimeMillis   = cfg.keepAliveTimeMillis;
        this.mm              = cfg.mm;
        
        this.threadPoolMonitoringConfig =
                new MonitoringConfigImpl<ThreadPoolProbe>(ThreadPoolProbe.class);
        
        final ThreadPoolProbe[] srcProbes = cfg.threadPoolMonitoringConfig.getProbesUnsafe();
        if (srcProbes != null) {
            threadPoolMonitoringConfig.addProbes(srcProbes);
        }
        
        this.transactionMonitor = cfg.transactionMonitor;
        this.transactionTimeoutMillis = cfg.transactionTimeoutMillis;
    }

    public ThreadPoolConfig copy() {
        return new ThreadPoolConfig(this);
    }

    /**
     * @return the queue
     */
    public Queue<Runnable> getQueue() {
        return queue;
    }

    /**
     *
     * @param queue the queue implemenation to use
     * @return the {@link ThreadPoolConfig} with the new {@link Queue} implementation.
     */
    public ThreadPoolConfig setQueue(Queue<Runnable> queue) {
        this.queue = queue;
        return this;
    }

    /**
     * @return the threadFactory
     */
    public ThreadFactory getThreadFactory() {
        return threadFactory;
    }

    /**
     *
     * @param threadFactory custom {@link ThreadFactory}
     * @return the {@link ThreadPoolConfig} with the new {@link ThreadFactory}
     */
    public ThreadPoolConfig setThreadFactory(ThreadFactory threadFactory) {
        this.threadFactory = threadFactory;
        return this;
    }

    /**
     * @return the poolname
     */
    public String getPoolName() {
        return poolName;
    }

    /**
     *
     * @param poolname the thread pool name.
     * @return the {@link ThreadPoolConfig} with the new thread pool name.
     */
    public ThreadPoolConfig setPoolName(String poolname) {
        this.poolName = poolname;
        return this;
    }

    public int getPriority() {
        return priority;
    }

    public ThreadPoolConfig setPriority(int priority) {
        this.priority = priority;
        return this;
    }

    /**
     * @return the maxpoolsize
     */
    public int getMaxPoolSize() {
        return maxPoolSize;
    }

    /**
     *
     * @param maxPoolSize the max thread pool size
     * @return the {@link ThreadPoolConfig} with the new max pool size
     */
    public ThreadPoolConfig setMaxPoolSize(int maxPoolSize) {
        this.maxPoolSize = maxPoolSize;
        return this;
    }

    /**
     * @return the corepoolsize
     */
    public int getCorePoolSize() {
        return corePoolSize;
    }

    /**
     *
     * @param corePoolSize the core thread pool size
     * @return the {@link ThreadPoolConfig} with the new core pool size
     */
    public ThreadPoolConfig setCorePoolSize(int corePoolSize) {
        this.corePoolSize = corePoolSize;
        return this;
    }

    /**
     * @return the queuelimit
     */
    public int getQueueLimit() {
        return queueLimit;
    }

    /**
     * @param queueLimit the queue limit
     * @return the {@link ThreadPoolConfig} with the new queue limite
     */
    public ThreadPoolConfig setQueueLimit(int queueLimit) {
        this.queueLimit = queueLimit;
        return this;
    }

    /**
     *
     * @param time max keep alive time
     * @param unit time unit
     * @return the {@link ThreadPoolConfig} with the new keep alive time
     */
    public ThreadPoolConfig setKeepAliveTime(long time, TimeUnit unit) {
        this.keepAliveTimeMillis = TimeUnit.MILLISECONDS.convert(time, unit);
        return this;
    }

    /**
     * @return the keepAliveTime
     */
    public long getKeepAliveTime(TimeUnit timeUnit) {
        return timeUnit.convert(keepAliveTimeMillis, TimeUnit.MILLISECONDS);
    }

    public MemoryManager getMemoryManager() {
        return mm;
    }

    public ThreadPoolConfig setMemoryManager(MemoryManager mm) {
        this.mm = mm;
        return this;
    }

    public MonitoringConfigImpl<ThreadPoolProbe> getInitialMonitoringConfig() {
        return threadPoolMonitoringConfig;
    }

    public DelayedExecutor getTransactionMonitor() {
        return transactionMonitor;
    }

    public ThreadPoolConfig setTransactionMonitor(
            final DelayedExecutor transactionMonitor) {
        this.transactionMonitor = transactionMonitor;

        return this;
    }

    public long getTransactionTimeout(final TimeUnit timeUnit) {
        if (transactionTimeoutMillis > 0) {
            return timeUnit.convert(transactionTimeoutMillis, TimeUnit.MILLISECONDS);
        }

        return 0;
    }

    public ThreadPoolConfig setTransactionTimeout(
            final long transactionTimeout, final TimeUnit timeunit) {

        if (transactionTimeout > 0) {
            this.transactionTimeoutMillis = TimeUnit.MILLISECONDS.convert(
                    transactionTimeout, timeunit);
        } else {
            transactionTimeoutMillis = 0;
        }
        return this;
    }

    public ThreadPoolConfig setTransactionTimeout(
            final DelayedExecutor transactionMonitor,
            final long transactionTimeout, final TimeUnit timeunit) {

        this.transactionMonitor = transactionMonitor;

        return setTransactionTimeout(transactionTimeout, timeunit);
    }

    @Override
    public String toString() {
        return ThreadPoolConfig.class.getSimpleName() + " :\r\n"
                + "  poolName: " + poolName + "\r\n"
                + "  corePoolSize: " + corePoolSize + "\r\n"
                + "  maxPoolSize: " + maxPoolSize + "\r\n"
                + "  queue: " + queue.getClass() + "\r\n"
                + "  queueLimit: " + queueLimit + "\r\n"
                + "  keepAliveTime (millis): " + keepAliveTimeMillis + "\r\n"
                + "  threadFactory: " + threadFactory + "\r\n"
                + "  transactionMonitor: " + transactionMonitor + "\r\n"
                + "  transactionTimeoutMillis: " + transactionTimeoutMillis + "\r\n"
                + "  priority: " + priority + "\r\n";
    }
}
