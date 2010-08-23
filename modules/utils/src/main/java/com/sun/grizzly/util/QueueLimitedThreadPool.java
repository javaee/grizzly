/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2009-2010 Oracle and/or its affiliates. All rights reserved.
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

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Need to evaluate queuesize limit perf implications on this fixedpool variant.
 * The atomic counter can in theory approach synchronized (lack of) scalability
 * in heavy load situatuions.
 * 
 * @author gustav trede
 */
final class QueueLimitedThreadPool extends FixedThreadPool{

    private final int maxQueuedTasks;
    
    private final AtomicInteger queueSize = new AtomicInteger();

    /**
     * @param name
     * @param poolsize
     * @param threadFactory {@link ThreadFactory}
     * @param workQueue {@link BlockingQueue}
     * @param maxQueuedTasks 
     */
    QueueLimitedThreadPool(String name, int poolsize,int maxQueuedTasks,
            ThreadFactory threadFactory,BlockingQueue<Runnable> workQueue,
            ThreadPoolMonitoringProbe probe) {
        super(name,poolsize,workQueue,threadFactory,probe);
        if (maxQueuedTasks < 0)
            throw new IllegalArgumentException("maxQueuedTasks < 0");
        this.maxQueuedTasks = maxQueuedTasks;
    }

    @Override
    public final void execute(Runnable command) {
       if (command == null) { // must nullcheck to ensure queuesize is valid
            throw new IllegalArgumentException("Runnable task is null");
        }
        if (running){
            if (queueSize.incrementAndGet() <= maxQueuedTasks
                    && workQueue.offer(command)) {
                onTaskQueued(command);
                return;
            }
            onTaskQueueOverflow();
            return;
        }
        throw new RejectedExecutionException("ThreadPool is not running");
    }

    @Override
    protected void onTaskQueueOverflow() {
        queueSize.decrementAndGet();
        super.onTaskQueueOverflow();
    }

    @Override
    public int getQueueSize() {
        return queueSize.get();
    }

    @Override
    public int getMaxQueuedTasksCount() {
        return maxQueuedTasks;
    }

    @Override
    protected final void beforeExecute(Thread t, Runnable r) {
        super.beforeExecute(t, r);
        queueSize.decrementAndGet();        
    }

}
