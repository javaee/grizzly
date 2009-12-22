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
import java.util.concurrent.TimeUnit;

/**
 * TODO: fix so config has the actual value the impl has,
 * all null config values can be set to something by the impl.
 * 
 * @author gustav trede
 */
public class GrizzlyExecutorService extends AbstractExecutorService{
    
    private volatile ExtendedThreadPool pool;
    private volatile ThreadPoolConfig config;
    private final Object statelock = new Object();

    /**
     *
     * @param cfg {@link ThreadPoolConfig}
     * @return {@link GrizzlyExecutorService}
     */
    public static GrizzlyExecutorService createInstance(ThreadPoolConfig cfg){
        return new GrizzlyExecutorService(cfg);
    }

    private GrizzlyExecutorService(ThreadPoolConfig config){
        if (config == null)
            throw new IllegalArgumentException("config is null");
        this.pool   = getImpl(config);
        this.config = config;
    }

    private final ExtendedThreadPool getImpl(ThreadPoolConfig cfg){
        if (cfg.corepoolsize < 0 || cfg.corepoolsize==cfg.maxpoolsize){
            return cfg.queuelimit < 1 ?
                new FixedThreadPool(cfg.poolname, cfg.maxpoolsize,
                cfg.queue, cfg.threadFactory,cfg.monitoringProbe) :
                new QueueLimitedThreadPool(
                cfg.poolname,cfg.maxpoolsize,cfg.queuelimit,
                cfg.threadFactory,cfg.queue,cfg.monitoringProbe);
        }
        return new SyncThreadPool(cfg.poolname, cfg.corepoolsize,
                cfg.maxpoolsize,cfg.keepAliveTime, cfg.timeUnit,
                cfg.threadFactory, cfg.queue, cfg.queuelimit);
    }

    /**
     * Sets the {@Link ThreadPoolConfig}
     * @param cfg
     */
    public final void reconfigure(ThreadPoolConfig config) {
        if (config == null)
            throw new IllegalArgumentException("config is null");
        synchronized(statelock){
            //TODO: only create new pool if old one cant be runtime config.
            ExtendedThreadPool oldpool = this.pool;
            this.pool = getImpl(config);
            AbstractThreadPool.drain(oldpool.getQueue(), pool.getQueue());
            oldpool.shutdown();
            this.config = config;
        }
    }

    public ThreadPoolConfig getConfiguration() {
        return config;
    }

    public Queue<Runnable> getQueue() {
        return pool.getQueue();
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

    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

}
