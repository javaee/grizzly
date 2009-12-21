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

import java.util.Collection;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * TODO: pub set methods in wrap should call reconfigure.
 * 
 * @author gustav trede
 * @author Alexey Stashok
 */
public class ThreadPoolFactory {

    public static ExtendedReconfigurableThreadPool getInstance(
            ThreadPoolConfig config){
        return new PoolWrap(config);
    }

    final static class PoolWrap implements ExtendedReconfigurableThreadPool{
        private volatile ExtendedThreadPool pool;
        private volatile ThreadPoolConfig config;
        private final Object statelock = new Object();

        public PoolWrap(ThreadPoolConfig config) {
            if (config == null)
                throw new IllegalArgumentException("config is null");
            this.pool   = getImpl(config);
            this.config = config;
        }

        private final ExtendedThreadPool getImpl(ThreadPoolConfig config){
            if (config.corepoolsize < 0 || config.corepoolsize==config.maxpoolsize){
                return config.queuelimit < 1 ?
                    new FixedThreadPool(config.poolname, config.maxpoolsize,
                    config.queue, config.threadFactory,config.monitoringProbe) :
                    new QueueLimitedThreadPool(
                    config.poolname,config.maxpoolsize,config.queuelimit,
                    config.threadFactory,config.queue,config.monitoringProbe);
            }
            return new SyncThreadPool(config.poolname, config.corepoolsize,
                    config.maxpoolsize,config.keepAliveTime, config.timeUnit,
                    config.threadFactory, config.queue, config.queuelimit);
        }

        public final void reconfigure(ThreadPoolConfig config) {
            if (config == null)
                throw new IllegalArgumentException("config is null");
            synchronized(statelock){
                //TODO: only create new pool if old one cant be runtime config.
                ExtendedThreadPool oldpool = this.pool;                
                this.pool = getImpl(config);
                this.pool.getQueue().addAll(oldpool.getQueue());
                oldpool.shutdown();
                this.config = config;
            }
        }

        public ThreadPoolConfig getConfiguration() {
            return config;
        }
        
        public int getActiveCount() {
            return pool.getActiveCount();
        }

        public int getTaskCount() {
            return 0;
        }

        public long getCompletedTaskCount() {
            return pool.getCompletedTaskCount();
        }

        public int getCorePoolSize() {
            return pool.getCorePoolSize();
        }

        public void setCorePoolSize(int corePoolSize) {

        }

        public int getLargestPoolSize() {
            return pool.getLargestPoolSize();
        }

        public int getPoolSize() {
            return pool.getPoolSize();
        }

        public Queue<Runnable> getQueue() {
            return pool.getQueue();
        }

        public int getQueueSize() {
            return pool.getQueueSize();
        }

        public long getKeepAliveTime(TimeUnit unit) {
            return pool.getKeepAliveTime(unit);
        }

        public void setKeepAliveTime(long time, TimeUnit unit) {
            pool.setKeepAliveTime(time, unit);
        }

        public int getMaximumPoolSize() {
            return pool.getMaximumPoolSize();
        }

        public void setMaximumPoolSize(int maximumPoolSize) {
            //throw new UnsupportedOperationException();
        }

        public int getMaxQueuedTasksCount() {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        public void setMaxQueuedTasksCount(int maxTasksCount) {
            //throw new UnsupportedOperationException();
        }

        public String getName() {
            return pool.getName();
        }

        public void setName(String name) {
            pool.setName(name);
        }

        public void setThreadFactory(ThreadFactory threadFactory) {
            pool.setThreadFactory(threadFactory);
        }

        public ThreadFactory getThreadFactory() {
            return pool.getThreadFactory();
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

        public boolean awaitTermination(long l, TimeUnit tu) throws InterruptedException {
            return pool.awaitTermination(l, tu);
        }

        public <T> Future<T> submit(Callable<T> clbl) {
            return pool.submit(clbl);
        }

        public <T> Future<T> submit(Runnable r, T t) {
            return pool.submit(r, t);
        }

        public Future<?> submit(Runnable r) {
            return pool.submit(r);
        }

        public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> clctn) throws InterruptedException {
            return pool.invokeAll(clctn);
        }

        public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> clctn, long l, TimeUnit tu) throws InterruptedException {
            return pool.invokeAll(clctn, l, tu);
        }

        public <T> T invokeAny(Collection<? extends Callable<T>> clctn) throws InterruptedException, ExecutionException {
            return pool.invokeAny(clctn);
        }

        public <T> T invokeAny(Collection<? extends Callable<T>> clctn, long l, TimeUnit tu) throws InterruptedException, ExecutionException, TimeoutException {
           return pool.invokeAny(clctn, l, tu);
        }

        public final void execute(Runnable r) {
            pool.execute(r);
        }

    }

}
