/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2015 Oracle and/or its affiliates. All rights reserved.
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

/**
 * Monitoring probe providing callbacks that may be invoked by Grizzly
 * {@link AbstractThreadPool} implementations.
 *
 * @author gustav trede
 * @author Alexey Stashok
 *
 * @since 1.9.19
 */
public interface ThreadPoolProbe {
    /**
     * <p>
     * This event may be fired when an {@link AbstractThreadPool} implementation
     * starts running.
     * </p>
     *
     * @param threadPool the {@link AbstractThreadPool} being monitored
     */
    void onThreadPoolStartEvent(AbstractThreadPool threadPool);

    /**
     * <p>
     * This event may be fired when an {@link AbstractThreadPool} implementation
     * stops.
     * </p>
     *
     * @param threadPool the {@link AbstractThreadPool} being monitored
     */
    void onThreadPoolStopEvent(AbstractThreadPool threadPool);

    /**
     * <p>
     * This event may be fired when an {@link AbstractThreadPool} implementation
     * allocates a new managed {@link Thread}.
     * </p>
     *
     * @param threadPool the {@link AbstractThreadPool} being monitored
     * @param thread the thread that has been allocated
     */
    void onThreadAllocateEvent(AbstractThreadPool threadPool, Thread thread);

    /**
     * <p>
     * This event may be fired when a thread will no longer be managed by the
     * {@link AbstractThreadPool} implementation.
     * </p>
     *
     * @param threadPool the {@link AbstractThreadPool} being monitored
     * @param thread the thread that is no longer being managed by the
     *  {@link AbstractThreadPool}
     */
    void onThreadReleaseEvent(AbstractThreadPool threadPool, Thread thread);

    /**
     * <p>
     * This event may be fired when the {@link AbstractThreadPool} implementation
     * has allocated and is managing a number of threads equal to the maximum limit
     * of the pool.
     * <p>
     *
     * @param threadPool the {@link AbstractThreadPool} being monitored
     * @param maxNumberOfThreads the maximum number of threads allowed in the
     *  {@link AbstractThreadPool}
     */
    void onMaxNumberOfThreadsEvent(AbstractThreadPool threadPool, int maxNumberOfThreads);

    /**
     * <p>
     * This event may be fired when a task has been queued for processing.
     * </p>
     *
     * @param threadPool the {@link AbstractThreadPool} being monitored
     * @param task a unit of work to be processed
     */
    void onTaskQueueEvent(AbstractThreadPool threadPool, Runnable task);

    /**
     * <p>
     * This event may be fired when a task has been pulled from the queue and
     * is about to be processed.
     * </p>
     *
     * @param threadPool the {@link AbstractThreadPool} being monitored
     * @param task a unit of work that is about to be processed.
     */
    void onTaskDequeueEvent(AbstractThreadPool threadPool, Runnable task);

    /**
     * <p>
     * This event may be fired when a dequeued task has been canceled.
     * </p>
     * This event can occur during shutdownNow() invocation, where tasks are
     * getting pulled out of thread pool queue and returned as the result of
     * shutdownNow() method call.
     *
     * @param threadPool the {@link AbstractThreadPool} being monitored
     * @param task a unit of work that has been canceled
     */
    void onTaskCancelEvent(AbstractThreadPool threadPool, Runnable task);

    /**
     * <p>
     * This event may be fired when a dequeued task has completed processing.
     * </p>
     *
     * @param threadPool the {@link AbstractThreadPool} being monitored
     * @param task the unit of work that has completed processing
     */
    void onTaskCompleteEvent(AbstractThreadPool threadPool, Runnable task);

    /**
     * <p>
     * This event may be fired when the task queue of the {@link AbstractThreadPool}
     * implementation has exceeded its configured size.
     * </p>
     *
     * @param threadPool the {@link AbstractThreadPool} being monitored
     */
    void onTaskQueueOverflowEvent(AbstractThreadPool threadPool);
    
    
    // ---------------------------------------------------------- Nested Classes


    /**
     * {@link ThreadPoolProbe} adapter that provides no-op implementations for
     * all interface methods allowing easy extension by the developer.
     *
     * @since 2.1.9
     */
    @SuppressWarnings("UnusedDeclaration")
    class Adapter implements ThreadPoolProbe {


        // ---------------------------------------- Methods from ThreadPoolProbe


        /**
         * {@inheritDoc}
         */
        @Override
        public void onThreadPoolStartEvent(AbstractThreadPool threadPool) {}

        /**
         * {@inheritDoc}
         */
        @Override
        public void onThreadPoolStopEvent(AbstractThreadPool threadPool) {}

        /**
         * {@inheritDoc}
         */
        @Override
        public void onThreadAllocateEvent(AbstractThreadPool threadPool, Thread thread) {}

        /**
         * {@inheritDoc}
         */
        @Override
        public void onThreadReleaseEvent(AbstractThreadPool threadPool, Thread thread) {}

        /**
         * {@inheritDoc}
         */
        @Override
        public void onMaxNumberOfThreadsEvent(AbstractThreadPool threadPool, int maxNumberOfThreads) {}

        /**
         * {@inheritDoc}
         */
        @Override
        public void onTaskQueueEvent(AbstractThreadPool threadPool, Runnable task) {}

        /**
         * {@inheritDoc}
         */
        @Override
        public void onTaskDequeueEvent(AbstractThreadPool threadPool, Runnable task) {}

        /**
         * {@inheritDoc}
         */
        @Override
        public void onTaskCancelEvent(AbstractThreadPool threadPool, Runnable task) {}
        
        /**
         * {@inheritDoc}
         */
        @Override
        public void onTaskCompleteEvent(AbstractThreadPool threadPool, Runnable task) {}

        /**
         * {@inheritDoc}
         */
        @Override
        public void onTaskQueueOverflowEvent(AbstractThreadPool threadPool) {}

    } // END Adapter

}
