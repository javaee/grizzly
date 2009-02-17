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

import com.sun.grizzly.util.ByteBufferFactory.ByteBufferType;
import java.util.concurrent.ExecutorService;
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
        implements ExecutorService, ExtendedThreadPool,
        Thread.UncaughtExceptionHandler {
    // Min number of worker threads in a pool
    public static int DEFAULT_MIN_THREAD_COUNT = 5;

    // Max number of worker threads in a pool
    public static int DEFAULT_MAX_THREAD_COUNT = 5;

    // Max number of tasks thread pool can enqueue
    public static int DEFAULT_MAX_TASKS_QUEUED = Integer.MAX_VALUE;

    // Timeout, after which idle thread will be stopped and excluded from pool
    public static int DEFAULT_IDLE_THREAD_KEEPALIVE_TIMEOUT = 30000;

    protected String name = "Grizzly";

    protected int maxTasksCount;

    protected AtomicInteger workerThreadCounter = new AtomicInteger();

    /**
     * Threads priority
     */
    protected int priority = Thread.NORM_PRIORITY;

    /**
     * The initial ByteBuffer size for newly created WorkerThread instances
     */
    protected int initialByteBufferSize = 8192;

    /**
     * The {@link ByteBufferType}
     */
    protected ByteBufferType byteBufferType = ByteBufferType.HEAP_VIEW;

    public DefaultThreadPool() {
        this(DEFAULT_MIN_THREAD_COUNT, DEFAULT_MAX_THREAD_COUNT,
                DEFAULT_MAX_TASKS_QUEUED, DEFAULT_IDLE_THREAD_KEEPALIVE_TIMEOUT,
                TimeUnit.MILLISECONDS);
    }

    public DefaultThreadPool(int corePoolSize, int maximumPoolSize,
            int maxTasksCount, long keepAliveTime, TimeUnit unit) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit,
                new LinkedBlockingQueue<Runnable>(maxTasksCount));
        setThreadFactory(new DefaultWorkerThreadFactory());
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public int getQueueSize() {
        return getQueue().size();
    }

    public ByteBufferType getByteBufferType() {
        return byteBufferType;
    }

    public void setByteBufferType(ByteBufferType byteBufferType) {
        this.byteBufferType = byteBufferType;
    }

    public int getInitialByteBufferSize() {
        return initialByteBufferSize;
    }

    public void setInitialByteBufferSize(int initialByteBufferSize) {
        this.initialByteBufferSize = initialByteBufferSize;
    }

    public void start() {
        this.prestartCoreThread();
    }

    public void stop() {
        shutdownNow();
    }

    public void uncaughtException(Thread thread, Throwable throwable) {
        LoggerUtils.getLogger().log(Level.WARNING,
                "Uncaught thread exception. Thread: " + thread, throwable);
    }

    @Override
    protected void beforeExecute(Thread t, Runnable r) {
        ((WorkerThreadImpl) t).createByteBuffer(false);
    }

    @Override
    protected void afterExecute(Runnable r, Throwable t) {
        ((WorkerThreadImpl) Thread.currentThread()).reset();
    }

    private class DefaultWorkerThreadFactory implements ThreadFactory {
        public Thread newThread(Runnable r) {
            Thread thread = new WorkerThreadImpl(DefaultThreadPool.this,
                    name + "-WorkerThread(" +
                    workerThreadCounter.getAndIncrement() + ")", r,
                    initialByteBufferSize);
            thread.setUncaughtExceptionHandler(DefaultThreadPool.this);
            thread.setPriority(priority);
            return thread;
        }
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder(512);
        builder.append("DefaultThreadPool[");
        injectToStringAttributes(builder);
        builder.append(']');
        return builder.toString();
    }

    protected void injectToStringAttributes(StringBuilder sb) {
        sb.append("name=").append(name);
        sb.append(", priority=").append(priority);
        sb.append(", min-threads=").append(getCorePoolSize());
        sb.append(", max-threads=").append(getMaximumPoolSize());
        sb.append(", max-queue-size=").append(getMaxQueuedTasksCount());
        sb.append(", initial-byte-buffer-size=").append(initialByteBufferSize);
        sb.append(", byte-buffer-type=").append(byteBufferType);
        sb.append(", is-shutdown=").append(isShutdown());
    }
}
