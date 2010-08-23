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

/**
 * Monitoring probe providing callbacks that may be invoked by Grizzly
 * {@link ExtendedThreadPool} implementations.
 * 
 * @author gustav trede
 * @author Alexey Stashok
 *
 * @since 1.9.19
 */
public interface ThreadPoolMonitoringProbe {


    /**
     * <p>
     * This event may be fired when an {@link ExtendedThreadPool} implementation
     * allocates a new managed {@link Thread}.
     * </p>
     *
     * @param threadPoolName the name of the {@link ExtendedThreadPool} being monitored
     * @param thread the thread that has been allocated
     */
    public void threadAllocatedEvent(String threadPoolName, Thread thread);

    /**
     * <p>
     * This event may be fired when a thread will no longer be managed by the
     * {@link ExtendedThreadPool} implementation.
     * </p>
     *
     * @param threadPoolName the name of the {@link ExtendedThreadPool} being monitored
     * @param thread the thread that is no longer being managed by the
     *  {@link ExtendedThreadPool}
     */
    public void threadReleasedEvent(String threadPoolName, Thread thread);

    /**
     * <p>
     * This event may be fired when the {@link ExtendedThreadPool} implementation
     * has allocated and is managing a number of threads equal to the maximum limit
     * of the pool.
     * <p>
     *
     * @param threadPoolName the name of the {@link ExtendedThreadPool} being
     *  monitored
     * @param maxNumberOfThreads the maximum number of threads allowed in the
     *  {@link ExtendedThreadPool}
     */
    public void maxNumberOfThreadsReachedEvent(String threadPoolName, int maxNumberOfThreads);

    /**
     * <p>
     * This event may be fired when a task has been queued for processing.
     * </p>
     *
     * @param task a unit of work to be processed
     */
    public void onTaskQueuedEvent(Runnable task);

    /**
     * <p>
     * This event may be fired when a task has been pulled from the queue and
     * is about to be processed.
     *
     * @param task a unit of work that is about to be processed.
     */
    public void onTaskDequeuedEvent(Runnable task);

    /**
     * <p>
     * This event may be fired when a dequeued task has completed processing.
     * </p>
     *
     * @param task the unit of work that has completed processing
     */
    public void onTaskCompletedEvent(Runnable task);

    /**
     * <p>
     * This event may be fired when the task queue of the {@link ExtendedThreadPool}
     * implementation has exceeded its configured size.
     * </p>
     *
     * @param threadPoolName the name of the {@link ExtendedThreadPool} being
     *  monitored
     */
    public void onTaskQueueOverflowEvent(String threadPoolName);

}
