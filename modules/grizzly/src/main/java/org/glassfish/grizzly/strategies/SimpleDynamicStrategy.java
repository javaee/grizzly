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

package org.glassfish.grizzly.strategies;

import java.io.IOException;
import java.util.concurrent.Executor;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.IOEvent;
import org.glassfish.grizzly.Strategy;
import org.glassfish.grizzly.nio.NIOConnection;
import org.glassfish.grizzly.utils.CurrentThreadExecutor;
import org.glassfish.grizzly.utils.WorkerThreadExecutor;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.concurrent.ExecutorService;

/**
 * Simple dynamic strategy, which switches I/O processing strategies, basing
 * on statistics. This implementation takes in consideration number of
 * {@link SelectionKey}s, which were selected last time by {@link Selector}.
 *
 * <tt>SimpleDynamicStrategy</tt> is able to use 2 strategies underneath:
 * {@link SameThreadStrategy}, {@link WorkerThreadStrategy}.
 * And is able to switch between them basing on corresponding threshold
 * (threshold represents the number of selected {@link SelectionKey}s).
 *
 * So the strategy is getting applied following way:
 *
 * {@link SameThreadStrategy} --(worker-thread threshold)--> {@link WorkerThreadStrategy}.
 *
 * @author Alexey Stashok
 */
public final class SimpleDynamicStrategy implements Strategy {
    private final SameThreadStrategy sameThreadStrategy;
    private final WorkerThreadStrategy workerThreadStrategy;

    private static final int WORKER_THREAD_THRESHOLD = 1;

    public SimpleDynamicStrategy(final ExecutorService workerThreadPool) {
        this(new CurrentThreadExecutor(),
                new WorkerThreadExecutor(workerThreadPool));
    }

    protected SimpleDynamicStrategy(final Executor sameThreadProcessorExecutor,
            final Executor workerThreadProcessorExecutor) {
        sameThreadStrategy = new SameThreadStrategy();
        workerThreadStrategy = new WorkerThreadStrategy(
                sameThreadProcessorExecutor, workerThreadProcessorExecutor);
    }

    @Override
    public boolean executeIoEvent(Connection connection, IOEvent ioEvent)
            throws IOException {
        final NIOConnection nioConnection = (NIOConnection) connection;
        final int lastSelectedKeysCount = nioConnection.getSelectorRunner().getLastSelectedKeysCount();

        if (lastSelectedKeysCount <= WORKER_THREAD_THRESHOLD) {
            return sameThreadStrategy.executeIoEvent(connection, ioEvent);
        } else {
            return workerThreadStrategy.executeIoEvent(connection, ioEvent);
        }
    }



//   /**
//    * {@inheritDoc}
//    */
//    @Override
//    public DynamicStrategyContext prepare(final Connection connection,
//            final IOEvent ioEvent) {
//        return new DynamicStrategyContext(
//                ((NIOConnection) connection).getSelectorRunner().getLastSelectedKeysCount());
//    }
//
//   /**
//    * {@inheritDoc}
//    */
//    @Override
//    public void executeProcessor(final DynamicStrategyContext strategyContext,
//            final ProcessorRunnable processorRunnable) throws IOException {
//        final int lastSelectedKeysCount;
//        if (strategyContext != null) {
//            lastSelectedKeysCount = strategyContext.lastSelectedKeysCount;
//        } else {
//            workerThreadStrategy.executeProcessor(strategyContext,
//                    processorRunnable);
//            return;
//        }
//
//        if (lastSelectedKeysCount >= WORKER_THREAD_THRESHOLD) {
//            workerThreadStrategy.executeProcessor(strategyContext,
//                    processorRunnable);
//        } else if (lastSelectedKeysCount >= leaderFollowerThreshold) {
//            strategyContext.isTerminateThread = true;
//            leaderFollowerStrategy.executeProcessor(
//                    strategyContext.isTerminateThread, processorRunnable);
//        } else {
//            sameThreadStrategy.executeProcessor(strategyContext,
//                    processorRunnable);
//        }
//
//    }
//
//   /**
//    * {@inheritDoc}
//    */
//    @Override
//    public boolean isTerminateThread(DynamicStrategyContext strategyContext) {
//        return strategyContext.isTerminateThread;
//    }
//
//    /**
//     * Returns the number of {@link SelectionKey}s, which should be selected
//     * from a {@link Selector}, to make it apply {@link LeaderFollowerStrategy}.
//     *
//     * @return the number of {@link SelectionKey}s, which should be selected
//     * from a {@link Selector}, to make it apply {@link LeaderFollowerStrategy}.
//     */
//    public int getLeaderFollowerThreshold() {
//        return leaderFollowerThreshold;
//    }
//
//    /**
//     * Set the number of {@link SelectionKey}s, which should be selected
//     * from a {@link Selector}, to make it apply {@link LeaderFollowerStrategy}.
//     *
//     * @param leaderFollowerThreshold the number of {@link SelectionKey}s,
//     * which should be selected from a {@link Selector}, to make it apply
//     * {@link LeaderFollowerStrategy}.
//     */
//    public void setLeaderFollowerThreshold(int leaderFollowerThreshold) {
//        this.leaderFollowerThreshold = leaderFollowerThreshold;
//        checkThresholds();
//    }
//
//    /**
//     * Returns the number of {@link SelectionKey}s, which should be selected
//     * from a {@link Selector}, to make it apply {@link WorkerThreadStrategy}.
//     *
//     * @return the number of {@link SelectionKey}s, which should be selected
//     * from a {@link Selector}, to make it apply {@link WorkerThreadStrategy}.
//     */
//    public int getWorkerThreadThreshold() {
//        return WORKER_THREAD_THRESHOLD;
//    }
//
//    /**
//     * Set the number of {@link SelectionKey}s, which should be selected
//     * from a {@link Selector}, to make it apply {@link WorkerThreadStrategy}.
//     *
//     * @param WORKER_THREAD_THRESHOLD the number of {@link SelectionKey}s,
//     * which should be selected from a {@link Selector}, to make it apply
//     * {@link WorkerThreadStrategy}.
//     */
//    public void setWorkerThreadThreshold(int WORKER_THREAD_THRESHOLD) {
//        this.WORKER_THREAD_THRESHOLD = WORKER_THREAD_THRESHOLD;
//        checkThresholds();
//    }
//
//    /**
//     * Check if thresholds are set correctly.
//     */
//    private void checkThresholds() {
//        if (leaderFollowerThreshold >= 0 &&
//                WORKER_THREAD_THRESHOLD >= leaderFollowerThreshold) return;
//
//        throw new IllegalStateException("Thresholds settings are incorrect");
//    }
//
//    public static final class DynamicStrategyContext {
//        final int lastSelectedKeysCount;
//        boolean isTerminateThread;
//
//        public DynamicStrategyContext(int lastSelectedKeysCount) {
//            this.lastSelectedKeysCount = lastSelectedKeysCount;
//        }
//    }
}
