/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2013 Oracle and/or its affiliates. All rights reserved.
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

import java.util.List;
import java.util.Queue;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import org.glassfish.grizzly.monitoring.MonitoringAware;
import org.glassfish.grizzly.monitoring.MonitoringConfig;

/**
 *
 * @author gustav trede
 */
public class GrizzlyExecutorService extends AbstractExecutorService
        implements MonitoringAware<ThreadPoolProbe> {

    private final Object statelock = new Object();
    private volatile AbstractThreadPool pool;
    protected volatile ThreadPoolConfig config;

    /**
     *
     * @return {@link GrizzlyExecutorService}
     */
    public static GrizzlyExecutorService createInstance() {
        return createInstance(ThreadPoolConfig.defaultConfig());
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
        setImpl(config);
    }

    protected final void setImpl(ThreadPoolConfig cfg) {
        if (cfg == null) {
            throw new IllegalArgumentException("config is null");
        }

        cfg = cfg.copy();

        if (cfg.getMemoryManager() == null) {
            cfg.setMemoryManager(MemoryManager.DEFAULT_MEMORY_MANAGER);
        }
        
        final Queue<Runnable> queue = cfg.getQueue();
        if ((queue == null || queue instanceof BlockingQueue) &&
                (cfg.getCorePoolSize() < 0 || cfg.getCorePoolSize() == cfg.getMaxPoolSize())) {

            this.pool = cfg.getQueueLimit() < 0
                ? new FixedThreadPool(cfg)
                : new QueueLimitedThreadPool(cfg);
        } else {
            this.pool = new SyncThreadPool(cfg);
        }
        
        this.config = cfg;
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
            final AbstractThreadPool oldpool = this.pool;
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

    @Override
    public void shutdown() {
        pool.shutdown();
    }

    @Override
    public List<Runnable> shutdownNow() {
        return pool.shutdownNow();
    }

    @Override
    public boolean isShutdown() {
        return pool.isShutdown();
    }

    @Override
    public boolean isTerminated() {
        return pool.isTerminated();
    }

    @Override
    public void execute(Runnable r) {
        pool.execute(r);
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit)
            throws InterruptedException {
        return pool.awaitTermination(timeout, unit);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MonitoringConfig<ThreadPoolProbe> getMonitoringConfig() {
        return pool.getMonitoringConfig();
    }
}
