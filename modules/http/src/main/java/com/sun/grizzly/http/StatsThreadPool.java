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
package com.sun.grizzly.http;

import com.sun.grizzly.util.DefaultThreadPool;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * Internal FIFO used by the Worker Threads to pass information
 * between {@link Task} objects.
 *
 * @author Jean-Francois Arcand
 */
public class StatsThreadPool extends DefaultThreadPool {
    
    /**
     * Port, which is served by this thread pool
     */
    protected int port;

    /**
     * The {@link ThreadPoolStatistic} objects used when gathering statistics.
     */
    protected transient ThreadPoolStatistic threadPoolStat;

    public StatsThreadPool() {
        this(DEFAULT_MAX_TASKS_QUEUED);
    }

    public StatsThreadPool(int maxTasksCount) {
        this(DEFAULT_MIN_THREAD_COUNT, DEFAULT_MAX_THREAD_COUNT, maxTasksCount,
                DEFAULT_IDLE_THREAD_KEEPALIVE_TIMEOUT, TimeUnit.MILLISECONDS);
    }

    public StatsThreadPool(int corePoolSize, int maximumPoolSize,
            int maxTasksCount, long keepAliveTime, TimeUnit unit) {
        super(corePoolSize, maximumPoolSize, maxTasksCount, keepAliveTime, unit);
        setThreadFactory(new HttpWorkerThreadFactory());
        setName("http");
    }

    /**
     * Get the port number, which is served by the thread pool
     * @return the port number, which is served by the thread pool
     */
    public int getPort() {
        return port;
    }

    /**
     * Set the port number, which is served by the thread pool
     * @param port the port number, which is served by the thread pool
     */
    public void setPort(int port) {
        this.port = port;
    }

    
    /**
     * Set the {@link ThreadPoolStatistic} object used
     * to gather statistic;
     */
    public void setStatistic(ThreadPoolStatistic threadPoolStatistic){
        this.threadPoolStat = threadPoolStatistic;
    }
    
    
    /**
     * Return the {@link ThreadPoolStatistic} object used
     * to gather statistic;
     */
    public ThreadPoolStatistic getStatistic(){
        return threadPoolStat;
    }

    /**
     * Create new {@link HttpWorkerThread}.
     */
    protected class HttpWorkerThreadFactory implements ThreadFactory {
        public Thread newThread(Runnable r) {            
            Thread thread = new HttpWorkerThread(StatsThreadPool.this,
                    name + port + "-WorkerThread(" +
                    workerThreadCounter.getAndIncrement() + ")", r,
                    initialByteBufferSize);
            thread.setUncaughtExceptionHandler(StatsThreadPool.this);
            thread.setPriority(priority);
            return thread;
        }
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder(512);
        builder.append("StatsThreadPool[");
        injectToStringAttributes(builder);
        builder.append(']');
        return builder.toString();
    }


    @Override
    protected void injectToStringAttributes(StringBuilder sb) {
        super.injectToStringAttributes(sb);
        sb.append(", port=").append(port);
    }


}
