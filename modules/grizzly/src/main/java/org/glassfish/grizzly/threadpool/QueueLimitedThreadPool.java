/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2014 Oracle and/or its affiliates. All rights reserved.
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

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;

/**
 * Need to evaluate queue size limit perf implications on this fixedpool variant.
 * The atomic counter can in theory approach synchronized (lack of) scalability
 * in heavy load situations.
 *
 * @author gustav trede
 * @author Tigran Mkrtchyan
 */
final class QueueLimitedThreadPool extends FixedThreadPool {

    private final Semaphore queuePermits;

    /**
     * @param config the {@link ThreadPoolConfig} to configure this pool.
     */
    QueueLimitedThreadPool(ThreadPoolConfig config) {
        super(config);
        if (config.getQueueLimit() < 0) {
            throw new IllegalArgumentException("maxQueuedTasks < 0");
        }

        queuePermits = new Semaphore(config.getQueueLimit());
    }

    @Override
    public final void execute(Runnable command) {
        if (command == null) { // must nullcheck to ensure queuesize is valid
            throw new IllegalArgumentException("Runnable task is null");
        }

        if (!running) {
            throw new RejectedExecutionException("ThreadPool is not running");
        }

        if (!queuePermits.tryAcquire()) {
            onTaskQueueOverflow();
        }

        if (!workQueue.offer(command)) {
            queuePermits.release();
            onTaskQueueOverflow();
        }

        onTaskQueued(command);
    }

    @Override
    protected final void beforeExecute(final Worker worker, final Thread t,
            final Runnable r) {
        super.beforeExecute(worker, t, r);
        queuePermits.release();
    }
}

