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
class QueueLimitedThreadPool extends FixedThreadPool{

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
        if (maxQueuedTasks < 1)
            throw new IllegalArgumentException("maxQueuedTasks < 1");
        this.maxQueuedTasks = maxQueuedTasks;
    }

    @Override
    public void execute(Runnable task) {
        if (task == null) {
            throw new IllegalArgumentException("Runnable task is null");
        }
        if (running){
            if (queueSize.incrementAndGet() <= maxQueuedTasks
                    && workQueue.offer(task)) {
                onTaskQueued(task);
            } else {
                queueSize.decrementAndGet();
                onTaskQueueOverflow();
                throw new RejectedExecutionException(
                        "The thread pool's task queue is full");
            }
        }
    }

    /**
     * Returns the number of tasks, which are currently waiting in the queue.
     *
     * @return the number of tasks, which are currently waiting in the queue.
     */
    @Override
    public int getQueueSize() {
        return queueSize.get();
    }

    @Override
    public void setMaxQueuedTasksCount(int maxTasksCount) {        
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void beforeExecute(Thread t, Runnable r) {
        super.beforeExecute(t, r);
        queueSize.decrementAndGet();        
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder(256);
        builder.append("GrizzlyThreadPool[");
        injectToStringAttributes(builder);
        builder.append(']');
        return builder.toString();
    }

    protected void injectToStringAttributes(StringBuilder sb) {
        sb.append("name=").append(name);
        sb.append(", queuesize=").append(getQueueSize());
        sb.append(", is-shutdown=").append(isShutdown());
    }
}
