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

package org.glassfish.grizzly.strategies;

import java.io.IOException;
import java.util.concurrent.Executor;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.IOEvent;
import org.glassfish.grizzly.ProcessorRunnable;
import org.glassfish.grizzly.Strategy;
import org.glassfish.grizzly.nio.NIOConnection;
import org.glassfish.grizzly.nio.NIOTransport;
import org.glassfish.grizzly.strategies.SimpleDynamicStrategy.DynamicStrategyContext;
import org.glassfish.grizzly.util.CurrentThreadExecutor;
import org.glassfish.grizzly.util.WorkerThreadExecutor;

/**
 * Simple dynamic strategy, which switches I/O processing strategies, basing
 * on statistics. This implementation takes in consideration number of
 * {@link SelectionKey}s, which were selected last time by {@link Selector}.
 *
 * @author Alexey Stashok
 */
public class SimpleDynamicStrategy implements Strategy<DynamicStrategyContext> {
    private SameThreadStrategy sameThreadStrategy;
    private LeaderFollowerStrategy leaderFollowerStrategy;
    private WorkerThreadStrategy workerThreadStrategy;

    private int leaderFollowerThreshold = 1;
    private int workerThreadThreshold = 32;

    public SimpleDynamicStrategy(final NIOTransport transport) {
        this(new CurrentThreadExecutor(), new WorkerThreadExecutor(transport));
    }

    public SimpleDynamicStrategy(final Executor sameThreadProcessorExecutor,
            final Executor workerThreadProcessorExecutor) {
        sameThreadStrategy = new SameThreadStrategy();
        leaderFollowerStrategy = new LeaderFollowerStrategy(
                sameThreadProcessorExecutor, workerThreadProcessorExecutor);
        workerThreadStrategy = new WorkerThreadStrategy(
                sameThreadProcessorExecutor, workerThreadProcessorExecutor);
    }

    public DynamicStrategyContext prepare(final Connection connection,
            final IOEvent ioEvent) {
        return new DynamicStrategyContext(
                ((NIOConnection) connection).getSelectorRunner().getLastSelectedKeysCount());
    }

    public void executeProcessor(final DynamicStrategyContext strategyContext,
            final ProcessorRunnable processorRunnable) throws IOException {
        final int lastSelectedKeysCount;
        if (strategyContext != null) {
            lastSelectedKeysCount = strategyContext.lastSelectedKeysCount;
        } else {
            workerThreadStrategy.executeProcessor(strategyContext,
                    processorRunnable);
            return;
        }

        if (lastSelectedKeysCount >= workerThreadThreshold) {
            workerThreadStrategy.executeProcessor(strategyContext,
                    processorRunnable);
        } else if (lastSelectedKeysCount >= leaderFollowerThreshold) {
            strategyContext.isTerminateThread = true;
            leaderFollowerStrategy.executeProcessor(
                    strategyContext.isTerminateThread, processorRunnable);
        } else {
            sameThreadStrategy.executeProcessor(strategyContext,
                    processorRunnable);
        }

    }

    public boolean isTerminateThread(DynamicStrategyContext strategyContext) {
        return strategyContext.isTerminateThread;
    }

    public int getLeaderFollowerThreshold() {
        return leaderFollowerThreshold;
    }

    public void setLeaderFollowerThreshold(int leaderFollowerThreshold) {
        this.leaderFollowerThreshold = leaderFollowerThreshold;
        checkThresholds();
    }

    public int getWorkerThreadThreshold() {
        return workerThreadThreshold;
    }

    public void setWorkerThreadThreshold(int workerThreadThreshold) {
        this.workerThreadThreshold = workerThreadThreshold;
        checkThresholds();
    }

    private void checkThresholds() {
        if (leaderFollowerThreshold >= 0 &&
                workerThreadThreshold >= leaderFollowerThreshold) return;

        throw new IllegalStateException("Thresholds settings are incorrect");
    }

    public static final class DynamicStrategyContext {
        final int lastSelectedKeysCount;
        boolean isTerminateThread;

        public DynamicStrategyContext(int lastSelectedKeysCount) {
            this.lastSelectedKeysCount = lastSelectedKeysCount;
        }
    }
}
