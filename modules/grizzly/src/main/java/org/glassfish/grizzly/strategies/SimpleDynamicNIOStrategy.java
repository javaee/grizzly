/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2009-2013 Oracle and/or its affiliates. All rights reserved.
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
import org.glassfish.grizzly.IOStrategy;
import org.glassfish.grizzly.Transport;
import org.glassfish.grizzly.nio.NIOConnection;
import org.glassfish.grizzly.nio.NIOTransport;
import org.glassfish.grizzly.threadpool.ThreadPoolConfig;

/**
 * Simple dynamic strategy, which switches I/O processing strategies, basing
 * on statistics. This implementation takes in consideration number of
 * {@link java.nio.channels.SelectionKey}s, which were selected last time by
 * {@link java.nio.channels.Selector}.
 *
 * <tt>SimpleDynamicIOStrategy</tt> is able to use 2 strategies underneath:
 * {@link SameThreadIOStrategy}, {@link WorkerThreadIOStrategy}.
 * And is able to switch between them basing on corresponding threshold
 * (threshold represents the number of selected
 * {@link java.nio.channels.SelectionKey}s).
 *
 * So the strategy is getting applied following way:
 *
 * {@link SameThreadIOStrategy} --(worker-thread threshold)--> {@link WorkerThreadIOStrategy}.
 *
 * @author Alexey Stashok
 */
public final class SimpleDynamicNIOStrategy implements IOStrategy {

    private static final SimpleDynamicNIOStrategy INSTANCE = new SimpleDynamicNIOStrategy();

    private final SameThreadIOStrategy sameThreadStrategy;
    private final WorkerThreadIOStrategy workerThreadStrategy;

    private static final int WORKER_THREAD_THRESHOLD = 1;


    // ------------------------------------------------------------ Constructors

    private SimpleDynamicNIOStrategy() {
        sameThreadStrategy = SameThreadIOStrategy.getInstance();
        workerThreadStrategy = WorkerThreadIOStrategy.getInstance();
    }


    // ---------------------------------------------------------- Public Methods


    public static SimpleDynamicNIOStrategy getInstance() {

        return INSTANCE;

    }

    @Override
    public boolean executeIoEvent(Connection connection, IOEvent ioEvent) throws IOException {
        return executeIoEvent(connection, ioEvent, true);
    }


    // ------------------------------------------------- Methods from IOStrategy


    @Override
    public Executor getThreadPoolFor(final Connection connection,
            final IOEvent ioEvent) {
        final int lastSelectedKeysCount = getLastSelectedKeysCount(connection);

        return lastSelectedKeysCount <= WORKER_THREAD_THRESHOLD ?
                     sameThreadStrategy.getThreadPoolFor(connection, ioEvent) :
                     workerThreadStrategy.getThreadPoolFor(connection, ioEvent);
    }

    @Override
    public boolean executeIoEvent(final Connection connection,
            final IOEvent ioEvent, final boolean isIoEventEnabled)
            throws IOException {
        
        final int lastSelectedKeysCount = getLastSelectedKeysCount(connection);

        return ((lastSelectedKeysCount <= WORKER_THREAD_THRESHOLD)
                     ? sameThreadStrategy.executeIoEvent(connection, ioEvent, isIoEventEnabled)
                     : workerThreadStrategy.executeIoEvent(connection, ioEvent, isIoEventEnabled));
    }

    @Override
    public ThreadPoolConfig createDefaultWorkerPoolConfig(final Transport transport) {

        final ThreadPoolConfig config = ThreadPoolConfig.defaultConfig().copy();
        final int selectorRunnerCount = ((NIOTransport) transport).getSelectorRunnersCount();
        config.setCorePoolSize(selectorRunnerCount * 2);
        config.setMaxPoolSize(selectorRunnerCount * 2);
        config.setMemoryManager(transport.getMemoryManager());
        return config;

    }


    // --------------------------------------------------------- Private Methods


    private static int getLastSelectedKeysCount(final Connection c) {
        return ((NIOConnection) c).getSelectorRunner().getLastSelectedKeysCount();
    }

}
