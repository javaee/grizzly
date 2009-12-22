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

import java.util.List;
import java.util.Queue;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * 
 * @author gustav trede
 */
@SuppressWarnings("deprecation")
public class GrizzlyExecutorService extends AbstractExecutorService
        implements ExtendedThreadPool {

    private volatile ExtendedThreadPool pool;
    private volatile ThreadPoolConfig config;
    private final Object statelock = new Object();

    /**
     *
     * @param cfg {@link ThreadPoolConfig}
     * @return {@link GrizzlyExecutorService}
     */
    public static GrizzlyExecutorService createInstance(ThreadPoolConfig cfg) {
        return new GrizzlyExecutorService(cfg);
    }

    private GrizzlyExecutorService(ThreadPoolConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("config is null");
        }
        setImpl(config);
    }

    private final void setImpl(ThreadPoolConfig cfg) {
        cfg = cfg.clone();
        ExtendedThreadPool impl = null;
        if (cfg.getCorePoolSize() < 0
                || cfg.getCorePoolSize() == cfg.getMaxPoolSize()) {
            impl = cfg.getQueueLimit() < 1
                    ? new FixedThreadPool(cfg.getPoolName(), cfg.getMaxPoolSize(),
                    cfg.getQueue(), cfg.getThreadFactory(), cfg.getMonitoringProbe())
                    : new QueueLimitedThreadPool(
                    cfg.getPoolName(), cfg.getMaxPoolSize(), cfg.getQueueLimit(),
                    cfg.getThreadFactory(), cfg.getQueue(), cfg.getMonitoringProbe());
        } else {
            impl = new SyncThreadPool(cfg.getPoolName(), cfg.getCorePoolSize(),
                    cfg.getMaxPoolSize(), cfg.getKeepAliveTime(), cfg.getTimeUnit(),
                    cfg.getThreadFactory(), cfg.getQueue(), cfg.getQueueLimit());
        }
        this.pool = impl;
        this.config = cfg.updateFrom(pool);
    }

    /**
     * Sets the {@link ThreadPoolConfig}
     * @param config
     * @return returns {@link GrizzlyExecutorService}
     */
    public final GrizzlyExecutorService reconfigure(ThreadPoolConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("config is null");
        }
        System.err.println(config);
        synchronized (statelock) {
            //TODO: only create new pool if old one cant be runtime config
            // for the needed state change(s).
            final ExtendedThreadPool oldpool = this.pool;
            setImpl(config);
            AbstractThreadPool.drain(oldpool.getQueue(), pool.getQueue());
            oldpool.shutdown();
        }
        return this;
    }

    /**
     *
     * @return config - {@link ThreadPoolConfig}
     */
    public ThreadPoolConfig getConfiguration() {
        return config.clone();
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

    public final void execute(Runnable r) {
        pool.execute(r);
    }

    public boolean awaitTermination(long timeout, TimeUnit unit)
            throws InterruptedException {
        return pool.awaitTermination(timeout, unit);
    }

    @Deprecated
    public Queue<Runnable> getQueue() {
        return pool.getQueue();
    }

    @Deprecated
    public int getActiveCount() {
        return pool.getActiveCount();
    }

    @Deprecated
    public int getTaskCount() {
        return pool.getTaskCount();
    }

    @Deprecated
    public long getCompletedTaskCount() {
        return pool.getCompletedTaskCount();
    }

    @Deprecated
    public int getCorePoolSize() {
        return pool.getCorePoolSize();
    }

    @Deprecated
    public void setCorePoolSize(int corePoolSize) {
        reconfigure(config.clone().setCorePoolSize(corePoolSize));
    }

    @Deprecated
    public int getLargestPoolSize() {
        return pool.getLargestPoolSize();
    }

    @Deprecated
    public int getPoolSize() {
        return pool.getPoolSize();
    }

    @Deprecated
    public int getQueueSize() {
        return pool.getQueueSize();
    }

    @Deprecated
    public long getKeepAliveTime(TimeUnit unit) {
        return pool.getKeepAliveTime(unit);
    }

    @Deprecated
    public void setKeepAliveTime(long time, TimeUnit unit) {
        reconfigure(config.clone().setKeepAliveTime(time, unit));
    }

    @Deprecated
    public int getMaximumPoolSize() {
        return pool.getMaximumPoolSize();
    }

    @Deprecated
    public void setMaximumPoolSize(int maximumPoolSize) {
        reconfigure(config.clone().setMaxPoolSize(maximumPoolSize));
    }

    @Deprecated
    public int getMaxQueuedTasksCount() {
        return pool.getMaxQueuedTasksCount();
    }

    @Deprecated
    public void setMaxQueuedTasksCount(int maxTasksCount) {
        reconfigure(config.clone().setQueueLimit(maxTasksCount));
    }

    @Deprecated
    public String getName() {
        return pool.getName();
    }

    @Deprecated
    public void setName(String name) {
        reconfigure(config.clone().setPoolName(name));
    }

    @Deprecated
    public void setThreadFactory(ThreadFactory threadFactory) {
        reconfigure(config.clone().setThreadFactory(threadFactory));
    }

    @Deprecated
    public ThreadFactory getThreadFactory() {
        return pool.getThreadFactory();
    }
}
