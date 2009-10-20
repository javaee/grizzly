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

package com.sun.grizzly.threadpool;

import com.sun.grizzly.Grizzly;
import com.sun.grizzly.attributes.AttributeBuilder;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

/**
 * Thread Pool implementation, based on {@link ThreadPoolExecutor}
 *
 * @author Alexey Stashok
 */
public class DefaultThreadPool extends ThreadPoolExecutor
        implements ExtendedThreadPool, Thread.UncaughtExceptionHandler {
    // Min number of worker threads in a pool
    private static final int DEFAULT_MIN_THREAD_COUNT = 5;
    
    // Max number of worker threads in a pool
    private static final int DEFAULT_MAX_THREAD_COUNT = 20;
    
    // Max number of tasks thread pool can enqueue
    private static final int DEFAULT_MAX_TASKS_QUEUED = Integer.MAX_VALUE;
    
    // Timeout, after which idle thread will be stopped and excluded from pool
    private static final int DEFAULT_IDLE_THREAD_KEEPALIVE_TIMEOUT = 30000;
    
    /**
     * AttributeBuilder to index WorkerThread attributes
     */
    protected AttributeBuilder attributeBuilder =
            Grizzly.DEFAULT_ATTRIBUTE_BUILDER;
    
    private String name = "Grizzly";
    
    private int maxTasksCount;
    
    private AtomicInteger workerThreadCounter = new AtomicInteger();

    public DefaultThreadPool() {
        this(DEFAULT_MIN_THREAD_COUNT, DEFAULT_MAX_THREAD_COUNT,
                DEFAULT_MAX_TASKS_QUEUED, DEFAULT_IDLE_THREAD_KEEPALIVE_TIMEOUT,
                TimeUnit.MILLISECONDS);
    }
    
    public DefaultThreadPool(int corePoolSize, int maximumPoolSize,
            int maxTasksCount, long keepAliveTime, TimeUnit unit) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, 
                new LinkedBlockingQueue<Runnable>(maxTasksCount));
        setThreadFactory(new DefaultWorkerThreadFactory(this));
        this.maxTasksCount = maxTasksCount;
    }

    public int getQueuedTasksCount() {
        return getQueue().size();
    }

    public int getMaxQueuedTasksCount() {
        return maxTasksCount;
    }

    public void setMaxQueuedTasksCount(int maxTasksCount) {
        throw new UnsupportedOperationException("Value could not be changed!");
    }
    
    @Override
    public String getName() {
        return name;
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }

    @Override
    public int getQueueSize() {
        return getQueue().size();
    }

    public void start() {
        prestartCoreThread();
    }

    public void stop() {
        shutdownNow();
    }

    @Override
    public AttributeBuilder getAttributeBuilder() {
        return attributeBuilder;
    }

    @Override
    public void setAttributeBuilder(AttributeBuilder attributeBuilder) {
        this.attributeBuilder = attributeBuilder;
    }

    @Override
    public void uncaughtException(Thread thread, Throwable throwable) {
        Grizzly.logger.log(Level.WARNING,
                "Uncaught thread exception. Thread: " + thread, throwable);
    }

    @Override
    protected void beforeExecute(Thread t, Runnable r) {
        ((DefaultWorkerThread) t).onBeforeRun();
    }

    @Override
    protected void afterExecute(Runnable r, Throwable t) {
        ((DefaultWorkerThread) Thread.currentThread()).onAfterRun();
    }

    private static class DefaultWorkerThreadFactory implements ThreadFactory {
        private DefaultThreadPool threadPool;

        public DefaultWorkerThreadFactory(DefaultThreadPool threadPool) {
            this.threadPool = threadPool;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new DefaultWorkerThread(
                    threadPool.getAttributeBuilder(),
                    threadPool.getName() + "-WorkerThread(" + 
                    threadPool.workerThreadCounter.getAndIncrement() + ")", r);
            thread.setUncaughtExceptionHandler(threadPool);

            return thread;
        }
    }
}
