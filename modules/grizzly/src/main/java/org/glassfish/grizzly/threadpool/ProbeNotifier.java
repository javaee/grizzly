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
 * Utility class, which has notification methods for different
 * {@link ThreadPoolProbe} events.
 *
 * @author Alexey Stashok
 */
final class ProbeNotifier {
    /**
     * Notify registered {@link ThreadPoolProbe}s about the "thread pool started" event.
     *
     * @param threadPool the {@link AbstractThreadPool} being monitored
     */
    static void notifyThreadPoolStarted(final AbstractThreadPool threadPool) {

        final ThreadPoolProbe[] probes = threadPool.monitoringConfig.getProbesUnsafe();
        if (probes != null) {
            for (ThreadPoolProbe probe : probes) {
                probe.onThreadPoolStartEvent(threadPool);
            }
        }
    }

    /**
     * Notify registered {@link ThreadPoolProbe}s about the "thread pool stopped" event.
     *
     * @param threadPool the {@link AbstractThreadPool} being monitored
     */
    static void notifyThreadPoolStopped(final AbstractThreadPool threadPool) {

        final ThreadPoolProbe[] probes = threadPool.monitoringConfig.getProbesUnsafe();
        if (probes != null) {
            for (ThreadPoolProbe probe : probes) {
                probe.onThreadPoolStopEvent(threadPool);
            }
        }
    }


    /**
     * Notify registered {@link ThreadPoolProbe}s about the "thread allocated" event.
     *
     * @param threadPool the {@link AbstractThreadPool} being monitored
     * @param thread the thread that has been allocated
     */
    static void notifyThreadAllocated(final AbstractThreadPool threadPool,
            final Thread thread) {

        final ThreadPoolProbe[] probes = threadPool.monitoringConfig.getProbesUnsafe();
        if (probes != null) {
            for (ThreadPoolProbe probe : probes) {
                probe.onThreadAllocateEvent(threadPool, thread);
            }
        }
    }

    /**
     * Notify registered {@link ThreadPoolProbe}s about the "thread released" event.
     *
     * @param threadPool the {@link AbstractThreadPool} being monitored
     * @param thread the thread that has been allocated
     */
    static void notifyThreadReleased(final AbstractThreadPool threadPool,
            final Thread thread) {

        final ThreadPoolProbe[] probes = threadPool.monitoringConfig.getProbesUnsafe();
        if (probes != null) {
            for (ThreadPoolProbe probe : probes) {
                probe.onThreadReleaseEvent(threadPool, thread);
            }
        }
    }

    /**
     * Notify registered {@link ThreadPoolProbe}s about the "max number of threads reached" event.
     *
     * @param threadPool the {@link AbstractThreadPool} being monitored
     * @param maxNumberOfThreads the maximum number of threads allowed in the
     *  {@link AbstractThreadPool}
     */
    static void notifyMaxNumberOfThreads(final AbstractThreadPool threadPool,
            final int maxNumberOfThreads) {

        final ThreadPoolProbe[] probes = threadPool.monitoringConfig.getProbesUnsafe();
        if (probes != null) {
            for (ThreadPoolProbe probe : probes) {
                probe.onMaxNumberOfThreadsEvent(threadPool, maxNumberOfThreads);
            }
        }
    }

    /**
     * Notify registered {@link ThreadPoolProbe}s about the "task queued" event.
     *
     * @param threadPool the {@link AbstractThreadPool} being monitored
     * @param task a unit of work to be processed
     */
    static void notifyTaskQueued(final AbstractThreadPool threadPool,
            final Runnable task) {

        final ThreadPoolProbe[] probes = threadPool.monitoringConfig.getProbesUnsafe();
        if (probes != null) {
            for (ThreadPoolProbe probe : probes) {
                probe.onTaskQueueEvent(threadPool, task);
            }
        }
    }

    /**
     * Notify registered {@link ThreadPoolProbe}s about the "task dequeued" event.
     *
     * @param threadPool the {@link AbstractThreadPool} being monitored
     * @param task a unit of work to be processed
     */
    static void notifyTaskDequeued(final AbstractThreadPool threadPool,
            final Runnable task) {

        final ThreadPoolProbe[] probes = threadPool.monitoringConfig.getProbesUnsafe();
        if (probes != null) {
            for (ThreadPoolProbe probe : probes) {
                probe.onTaskDequeueEvent(threadPool, task);
            }
        }
    }

    /**
     * Notify registered {@link ThreadPoolProbe}s about the "task cancelled" event.
     *
     * @param threadPool the {@link AbstractThreadPool} being monitored
     * @param task a unit of work to be processed
     */
    static void notifyTaskCancelled(final AbstractThreadPool threadPool,
            final Runnable task) {

        final ThreadPoolProbe[] probes = threadPool.monitoringConfig.getProbesUnsafe();
        if (probes != null) {
            for (ThreadPoolProbe probe : probes) {
                probe.onTaskCancelEvent(threadPool, task);
            }
        }
    }
    
    /**
     * Notify registered {@link ThreadPoolProbe}s about the "task completed" event.
     *
     * @param threadPool the {@link AbstractThreadPool} being monitored
     * @param task a unit of work to be processed
     */
    static void notifyTaskCompleted(final AbstractThreadPool threadPool,
            final Runnable task) {

        final ThreadPoolProbe[] probes = threadPool.monitoringConfig.getProbesUnsafe();
        if (probes != null) {
            for (ThreadPoolProbe probe : probes) {
                probe.onTaskCompleteEvent(threadPool, task);
            }
        }
    }

    /**
     * Notify registered {@link ThreadPoolProbe}s about the "task queue overflow" event.
     *
     * @param threadPool the {@link AbstractThreadPool} being monitored
     */
    static void notifyTaskQueueOverflow(final AbstractThreadPool threadPool) {

        final ThreadPoolProbe[] probes = threadPool.monitoringConfig.getProbesUnsafe();
        if (probes != null) {
            for (ThreadPoolProbe probe : probes) {
                probe.onTaskQueueOverflowEvent(threadPool);
            }
        }
    }
}
