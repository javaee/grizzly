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

    protected BlockingQueue<Runnable> queue;
    protected ThreadFactory threadFactory;
    protected String poolname;
    protected int maxpoolsize;
    protected int corepoolsize;
    protected int queuelimit;
    protected long keepAliveTime;
    protected TimeUnit timeUnit;
    protected ThreadPoolMonitoringProbe monitoringProbe;

    public ThreadPoolConfig(
            BlockingQueue<Runnable> queue,
            ThreadFactory threadFactory,
            String poolname,
            int queuelimit,
            int maxpoolsize
            ,int corepoolsize,
            long keepAliveTime,
            TimeUnit timeUnit,
            ThreadPoolMonitoringProbe monitoringProbe
            ) {
        this.queue = queue;
        this.threadFactory = threadFactory;
        this.poolname = poolname;
        this.maxpoolsize = maxpoolsize;        
        this.queuelimit = queuelimit;
        this.corepoolsize = corepoolsize;
        this.keepAliveTime = keepAliveTime;
        this.timeUnit = timeUnit;
        this.monitoringProbe = monitoringProbe;
    }

    public ThreadPoolConfig(ThreadPoolConfig cfg) {
        this.queue = cfg.queue;
        this.threadFactory = cfg.threadFactory;
        this.poolname = cfg.poolname;
        this.maxpoolsize = cfg.maxpoolsize;
        this.queuelimit = cfg.queuelimit;
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
    protected ThreadPoolConfig updatefrom(ExtendedThreadPool ep){
        Queue q = ep.getQueue();
        this.queue = ((q instanceof BlockingQueue) ? 
            (BlockingQueue<Runnable>)q : null);
        this.threadFactory = ep.getThreadFactory();
        this.poolname = ep.getName();
        this.maxpoolsize = ep.getMaximumPoolSize();
        this.queuelimit = ep.getMaxQueuedTasksCount();
        this.corepoolsize = ep.getCorePoolSize();
        /*this.keepAliveTime = keepAliveTime;
        this.timeUnit = timeUnit;
        this.monitoringProbe = monitoringProbe;*/
        return this;
    }

    /**
     * @return the queue
     */
    public BlockingQueue<Runnable> getQueue() {
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
    public String getPoolname() {
        return poolname;
    }

    /**
     * @param poolname the poolname to set
     */
    public ThreadPoolConfig setPoolname(String poolname) {
        this.poolname = poolname;
        return this;
    }

    /**
     * @return the maxpoolsize
     */
    public int getMaxpoolsize() {
        return maxpoolsize;
    }

    /**
     * @param maxpoolsize the maxpoolsize to set
     */
    public ThreadPoolConfig setMaxpoolsize(int maxpoolsize) {
        this.maxpoolsize = maxpoolsize;
        return this;
    }

    /**
     * @return the corepoolsize
     */
    public int getCorepoolsize() {
        return corepoolsize;
    }

    /**
     * @param corepoolsize the corepoolsize to set
     */
    public ThreadPoolConfig setCorepoolsize(int corepoolsize) {
        this.corepoolsize = corepoolsize;
        return this;
    }

    /**
     * @return the queuelimit
     */
    public int getQueuelimit() {
        return queuelimit;
    }

    /**
     * @param queuelimit the queuelimit to set
     */
    public ThreadPoolConfig setQueuelimit(int queuelimit) {
        this.queuelimit = queuelimit;
        return this;
    }

    /**
     * @param keepAliveTime the keepAliveTime to set
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



}
