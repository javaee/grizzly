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

import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * @author Oleksiy Stashok
 * @author gustav trede
 */
public class ThreadPoolConfig {

    protected Queue<Runnable> queue;
    protected ThreadFactory threadFactory;
    protected String poolName;
    protected int maxPoolSize;
    protected int corepoolsize;
    protected int queueLimit;
    protected long keepAliveTime;
    protected TimeUnit timeUnit;
    protected ThreadPoolMonitoringProbe monitoringProbe;

    public ThreadPoolConfig(
            Queue<Runnable> queue,
            ThreadFactory threadFactory,
            String poolname,
            int queuelimit,
            int maxpoolsize, int corepoolsize,
            long keepAliveTime,
            TimeUnit timeUnit,
            ThreadPoolMonitoringProbe monitoringProbe) {
        this.queue = queue;
        this.threadFactory = threadFactory;
        this.poolName = poolname;
        this.maxPoolSize = maxpoolsize;
        this.queueLimit = queuelimit;
        this.corepoolsize = corepoolsize;
        this.keepAliveTime = keepAliveTime;
        this.timeUnit = timeUnit;
        this.monitoringProbe = monitoringProbe;
    }

    public ThreadPoolConfig(ThreadPoolConfig cfg) {
        this.queue = cfg.queue;
        this.threadFactory = cfg.threadFactory;
        this.poolName = cfg.poolName;
        this.maxPoolSize = cfg.maxPoolSize;
        this.queueLimit = cfg.queueLimit;
        this.corepoolsize = cfg.corepoolsize;
        this.keepAliveTime = cfg.keepAliveTime;
        this.timeUnit = cfg.timeUnit;
        this.monitoringProbe = cfg.monitoringProbe;
    }

    @Override
    public ThreadPoolConfig clone() {
        return new ThreadPoolConfig(this);
    }

    @SuppressWarnings("deprecation")
    protected ThreadPoolConfig updateFrom(ExtendedThreadPool ep) {
        Queue q = ep.getQueue();
        this.queue = ((q instanceof BlockingQueue)
                ? (BlockingQueue<Runnable>) q : null);
        this.threadFactory = ep.getThreadFactory();
        this.poolName = ep.getName();
        this.maxPoolSize = ep.getMaximumPoolSize();
        this.queueLimit = ep.getMaxQueuedTasksCount();
        this.corepoolsize = ep.getCorePoolSize();
        /*this.keepAliveTime = keepAliveTime;
        this.timeUnit = timeUnit;
        this.monitoringProbe = monitoringProbe;*/
        return this;
    }

    /**
     * @return the queue
     */
    public Queue<Runnable> getQueue() {
        return queue;
    }

    /**
     * @param queue the queue to set
     */
    public ThreadPoolConfig setQueue(BlockingQueue<Runnable> queue) {
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
     * @param threadFactory the threadFactory to set
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
     * @param poolname the poolname to set
     */
    public ThreadPoolConfig setPoolName(String poolname) {
        this.poolName = poolname;
        return this;
    }

    /**
     * @return the maxpoolsize
     */
    public int getMaxPoolSize() {
        return maxPoolSize;
    }

    /**
     * @param maxpoolsize the maxpoolsize to set
     */
    public ThreadPoolConfig setMaxPoolSize(int maxPoolSize) {
        this.maxPoolSize = maxPoolSize;
        return this;
    }

    /**
     * @return the corepoolsize
     */
    public int getCorePoolSize() {
        return corepoolsize;
    }

    /**
     * @param corepoolsize the corepoolsize to set
     */
    public ThreadPoolConfig setCorePoolSize(int corePoolSize) {
        this.corepoolsize = corePoolSize;
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
        this.keepAliveTime = time;
        this.timeUnit = unit;
        return this;
    }

    /**
     * @return the timeUnit
     */
    public TimeUnit getTimeUnit() {
        return timeUnit;
    }

    /**
     * @return the keepAliveTime
     */
    public long getKeepAliveTime() {
        return keepAliveTime;
    }

    /**
     * @return the monitoringProbe
     */
    public ThreadPoolMonitoringProbe getMonitoringProbe() {
        return monitoringProbe;
    }

    /**
     * @param monitoringProbe the monitoringProbe to set
     */
    public ThreadPoolConfig setMonitoringProbe(ThreadPoolMonitoringProbe monitoringProbe) {
        this.monitoringProbe = monitoringProbe;
        return this;
    }

    @Override
    public String toString() {
        return ThreadPoolConfig.class.getSimpleName()+" :\r\n"+
        "  corepoolsize: "+corepoolsize+"\r\n"+
        "  maxpoolsize: "+maxPoolSize+"\r\n"+
        "  poolname: "+poolName+"\r\n"+
        "  threadFactory: "+threadFactory +"\r\n"+
        "  queuelimit: "+queueLimit +"\r\n"+
        "  queu: "+queue.getClass() +"\r\n"+
        "  monitoringProbe: "+monitoringProbe +"\r\n"+
        "  keepAliveTime: "+keepAliveTime +"\r\n"+
        "  timeUnit: "+timeUnit +"\r\n";
    }
}
