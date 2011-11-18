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

import java.util.List;
import java.util.Queue;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * 
 * @author gustav trede
 */
@SuppressWarnings("deprecation")
public class GrizzlyExecutorService extends AbstractExecutorService
        implements ExtendedThreadPool {

    private final Object statelock = new Object();
    private volatile ExtendedThreadPool pool;
    protected volatile ThreadPoolConfig config;

    /**
     *
     * @return {@link GrizzlyExecutorService}
     */
    public static GrizzlyExecutorService createInstance() {
        return createInstance(ThreadPoolConfig.DEFAULT);
    }

    /**
     *
     * @param cfg {@link ThreadPoolConfig}
     * @return {@link GrizzlyExecutorService}
     */
    public static GrizzlyExecutorService createInstance(ThreadPoolConfig cfg) {
        return new GrizzlyExecutorService(cfg);
    }

    protected GrizzlyExecutorService(ThreadPoolConfig config) {
        this(config, true);
    }

    protected GrizzlyExecutorService(ThreadPoolConfig config, boolean initialize) {
        if (initialize) {
            setImpl(config);
        } else {
            this.config = config;
        }
    }

    protected final void setImpl(ThreadPoolConfig cfg) {
        if (cfg == null) {
            throw new IllegalArgumentException("config is null");
        }
        cfg = cfg.copy();
        final Queue<Runnable> queue = cfg.getQueue();
        if ((queue == null || queue instanceof BlockingQueue) &&
                (cfg.getCorePoolSize() < 0 || cfg.getCorePoolSize() == cfg.getMaxPoolSize())) {
            
            this.pool = cfg.getQueueLimit() < 0
                ? new FixedThreadPool(cfg.getPoolName(), cfg.getMaxPoolSize(),
                (BlockingQueue<Runnable>) queue,
                cfg.getThreadFactory(), cfg.getMonitoringProbe())
                : new QueueLimitedThreadPool(
                cfg.getPoolName(), cfg.getMaxPoolSize(), cfg.getQueueLimit(),
                cfg.getThreadFactory(),
                (BlockingQueue<Runnable>) queue, cfg.getMonitoringProbe());
        } else {
            this.pool = new SyncThreadPool(cfg.getPoolName(), cfg.getCorePoolSize(),
                    cfg.getMaxPoolSize(), cfg.getKeepAliveTime(TimeUnit.MILLISECONDS), TimeUnit.MILLISECONDS,
                    cfg.getThreadFactory(), queue, cfg.getQueueLimit(),cfg.getMonitoringProbe());
        }        
        this.config = cfg.updateFrom(pool);
    }

    /**
     * Sets the {@link ThreadPoolConfig}
     * @param config
     * @return returns {@link GrizzlyExecutorService}
     */
    public GrizzlyExecutorService reconfigure(ThreadPoolConfig config) {
        synchronized (statelock) {
            //TODO: only create new pool if old one cant be runtime config
            // for the needed state change(s).
            final ExtendedThreadPool oldpool = this.pool;
            if (config.getQueue() == oldpool.getQueue()) {
                config.setQueue(null);
            }
            
            setImpl(config);
            AbstractThreadPool.drain(oldpool.getQueue(), this.pool.getQueue());
            oldpool.shutdown();
        }
        return this;
    }

    /**
     *
     * @return config - {@link ThreadPoolConfig}
     */
    public ThreadPoolConfig getConfiguration() {
        return config.copy();
    }

    public void shutdown() {
        pool.shutdown();
    }

    public List<Runnable> shutdownNow() {
        return pool.shutdownNow();
    }

    public boolean isShutdown() {
        return pool.isShutdown();
    }

    public boolean isTerminated() {
        return pool.isTerminated();
    }

    public void execute(Runnable r) {
        pool.execute(r);
    }

    public boolean awaitTermination(long timeout, TimeUnit unit)
            throws InterruptedException {
        return pool.awaitTermination(timeout, unit);
    }

    /**
     * @deprecated please use {@link GrizzlyExecutorService#getConfiguration()}
     * to check thread pool configuration settings.
     */
    @Deprecated
    public Queue<Runnable> getQueue() {
        return config.getQueue();
    }

    /**
     * @deprecated please use {@link ThreadPoolMonitoringProbe}s to intercept
     * thread pool events and build statistics.
     */
    @Deprecated
    public int getActiveCount() {
        return pool.getActiveCount();
    }

    /**
     * @deprecated please use {@link ThreadPoolMonitoringProbe}s to intercept
     * thread pool events and build statistics.
     */
    @Deprecated
    public int getTaskCount() {
        return pool.getTaskCount();
    }

    /**
     * @deprecated please use {@link ThreadPoolMonitoringProbe}s to intercept
     * thread pool events and build statistics.
     */
    @Deprecated
    public long getCompletedTaskCount() {
        return pool.getCompletedTaskCount();
    }

    /**
     * @deprecated please use {@link GrizzlyExecutorService#getConfiguration()}
     * to check thread pool configuration settings.
     */
    @Deprecated
    public int getCorePoolSize() {
        return config.getCorePoolSize();
    }

    /**
     * @deprecated please use {@link GrizzlyExecutorService#reconfigure(com.sun.grizzly.util.ThreadPoolConfig)}
     * to change thread pool configuration settings.
     */
    @Deprecated
    public void setCorePoolSize(int corePoolSize) {
        reconfigure(getConfiguration().setCorePoolSize(corePoolSize));
    }

    /**
     * @deprecated please use {@link ThreadPoolMonitoringProbe}s to intercept
     * thread pool events and build statistics.
     */
    @Deprecated
    public int getLargestPoolSize() {
        return pool.getLargestPoolSize();
    }

    /**
     * @deprecated please use {@link ThreadPoolMonitoringProbe}s to intercept
     * thread pool events and build statistics.
     */
    @Deprecated
    public int getPoolSize() {
        return pool.getPoolSize();
    }

    /**
     * @deprecated please use {@link ThreadPoolMonitoringProbe}s to intercept
     * thread pool events and build statistics.
     */
    @Deprecated
    public int getQueueSize() {
        return pool.getQueueSize();
    }

    /**
     * @deprecated please use {@link GrizzlyExecutorService#getConfiguration()}
     * to check thread pool configuration settings.
     */
    @Deprecated
    public long getKeepAliveTime(TimeUnit unit) {
        return config.getKeepAliveTime(unit);
    }

    /**
     * @deprecated please use {@link GrizzlyExecutorService#reconfigure(com.sun.grizzly.util.ThreadPoolConfig)}
     * to change thread pool configuration settings.
     */
    @Deprecated
    public void setKeepAliveTime(long time, TimeUnit unit) {
        reconfigure(getConfiguration().setKeepAliveTime(time, unit));
    }

    /**
     * @deprecated please use {@link GrizzlyExecutorService#getConfiguration()}
     * to check thread pool configuration settings.
     */
    @Deprecated
    public int getMaximumPoolSize() {
        return config.getMaxPoolSize();
    }

    /**
     * @deprecated please use {@link GrizzlyExecutorService#reconfigure(com.sun.grizzly.util.ThreadPoolConfig)}
     * to change thread pool configuration settings.
     */
    @Deprecated
    public void setMaximumPoolSize(int maximumPoolSize) {
        reconfigure(getConfiguration().setMaxPoolSize(maximumPoolSize));
    }

    /**
     * @deprecated please use {@link GrizzlyExecutorService#getConfiguration()}
     * to check thread pool configuration settings.
     */
    @Deprecated
    public int getMaxQueuedTasksCount() {
        return config.getQueueLimit();
    }

    /**
     * @deprecated please use {@link GrizzlyExecutorService#reconfigure(com.sun.grizzly.util.ThreadPoolConfig)}
     * to change thread pool configuration settings.
     */
    @Deprecated
    public void setMaxQueuedTasksCount(int maxTasksCount) {
        reconfigure(getConfiguration().setQueueLimit(maxTasksCount));
    }

    /**
     * @deprecated please use {@link GrizzlyExecutorService#getConfiguration()}
     * to check thread pool configuration settings.
     */
    @Deprecated
    public String getName() {
        return config.getPoolName();
    }

    /**
     * @deprecated please use {@link GrizzlyExecutorService#reconfigure(com.sun.grizzly.util.ThreadPoolConfig)}
     * to change thread pool configuration settings.
     */
    @Deprecated
    public void setName(String name) {
        reconfigure(getConfiguration().setPoolName(name));
    }

    /**
     * @deprecated please use {@link GrizzlyExecutorService#getConfiguration()}
     * to check thread pool configuration settings.
     */
    @Deprecated
    public int getPriority() {
        return config.getPriority();
    }

    /**
     * @deprecated please use {@link GrizzlyExecutorService#reconfigure(com.sun.grizzly.util.ThreadPoolConfig)}
     * to change thread pool configuration settings.
     */
    public void setPriority(int priority) {
        reconfigure(getConfiguration().setPriority(priority));
    }

    /**
     * @deprecated please use {@link GrizzlyExecutorService#getConfiguration()}
     * to check thread pool configuration settings.
     */
    @Deprecated
    public ThreadFactory getThreadFactory() {
        return config.getThreadFactory();
    }

    /**
     * @deprecated please use {@link GrizzlyExecutorService#reconfigure(com.sun.grizzly.util.ThreadPoolConfig)}
     * to change thread pool configuration settings.
     */
    @Deprecated
    public void setThreadFactory(ThreadFactory threadFactory) {
        reconfigure(getConfiguration().setThreadFactory(threadFactory));
    }
}
