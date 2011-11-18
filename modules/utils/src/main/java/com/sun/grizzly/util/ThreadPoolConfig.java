/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2009-2011 Oracle and/or its affiliates. All rights reserved.
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
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * @author Oleksiy Stashok
 * @author gustav trede
 */
public class ThreadPoolConfig {
    public static final ThreadPoolConfig DEFAULT = new ThreadPoolConfig(
            "Grizzly", AbstractThreadPool.DEFAULT_MIN_THREAD_COUNT,
            AbstractThreadPool.DEFAULT_MAX_THREAD_COUNT,
            null, -1,
            AbstractThreadPool.DEFAULT_IDLE_THREAD_KEEPALIVE_TIMEOUT,
            TimeUnit.MILLISECONDS,
            null, Thread.MAX_PRIORITY, null);

    protected String poolName;
    protected int corePoolSize;
    protected int maxPoolSize;
    protected Queue<Runnable> queue;
    protected int queueLimit = -1;
    protected long keepAliveTimeMillis;
    protected ThreadFactory threadFactory;
    protected int priority;
    protected ThreadPoolMonitoringProbe monitoringProbe;

    public ThreadPoolConfig(
            String poolName,
            int corePoolSize, int maxPoolSize,
            Queue<Runnable> queue, int queueLimit,
            long keepAliveTime, TimeUnit timeUnit,
            ThreadFactory threadFactory, int priority,
            ThreadPoolMonitoringProbe monitoringProbe) {
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
        this.monitoringProbe = monitoringProbe;
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
        this.monitoringProbe = cfg.monitoringProbe;
    }

    /**
     * @deprecated
     * Use {@link #copy()} instead.
     */
    @Override
    public ThreadPoolConfig clone() {
        return copy();
    }

    public ThreadPoolConfig copy() {
        return new ThreadPoolConfig(this);
    }
    
    @SuppressWarnings("deprecation")
    protected ThreadPoolConfig updateFrom(ExtendedThreadPool ep) {
        this.queue = ep.getQueue();
        this.threadFactory = ep.getThreadFactory();
        this.poolName = ep.getName();
        this.maxPoolSize = ep.getMaximumPoolSize();

        //hiding internal values, due to they might not match configure

        //this.queueLimit = ep.getMaxQueuedTasksCount();
        //this.corepoolsize = ep.getCorePoolSize();
        //this.keepAliveTime = keepAliveTime;
        //this.timeUnit = timeUnit;
        //this.monitoringProbe = monitoringProbe;
        return this;
    }

    /**
     * @return the queue
     */
    public Queue<Runnable> getQueue() {
        return queue;
    }

    /**
     * 
     * @param queue
     * @return
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
     * @param threadFactory
     * @return
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
     * @param poolname
     * @return
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
     * @param maxPoolSize
     * @return
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
     * @param corePoolSize
     * @return
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
     * @param queueLimit
     * @return
     */
    public ThreadPoolConfig setQueueLimit(int queueLimit) {
        this.queueLimit = queueLimit;
        return this;
    }

    /**
     *
     * @param time
     * @param unit
     * @return
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

    /**
     * @return the monitoringProbe
     */
    public ThreadPoolMonitoringProbe getMonitoringProbe() {
        return monitoringProbe;
    }

    /**
     *
     * @param monitoringProbe
     * @return
     */
    public ThreadPoolConfig setMonitoringProbe(ThreadPoolMonitoringProbe monitoringProbe) {
        this.monitoringProbe = monitoringProbe;
        return this;
    }

    @Override
    public String toString() {
        return new StringBuilder().append(ThreadPoolConfig.class.getSimpleName())
                .append(" :\r\n").append("  poolName: ").append(poolName)
                .append("\r\n").append("  corePoolSize: ").append(corePoolSize)
                .append("\r\n").append("  maxPoolSize: ").append(maxPoolSize)
                .append("\r\n").append("  queue: ").append((queue != null) ? queue.getClass() : "NONE")
                .append("\r\n").append("  queueLimit: ").append(queueLimit).append("\r\n")
                .append("  keepAliveTime (millis): ").append(keepAliveTimeMillis)
                .append("\r\n").append("  threadFactory: ").append(threadFactory)
                .append("\r\n").append("  priority: ").append(priority)
                .append("\r\n").append("  monitoringProbe: ").append(monitoringProbe)
                .append("\r\n").toString();
    }
}
