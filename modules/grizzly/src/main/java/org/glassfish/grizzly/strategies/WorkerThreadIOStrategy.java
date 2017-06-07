/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2009-2017 Oracle and/or its affiliates. All rights reserved.
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
import java.util.EnumSet;
import java.util.logging.Logger;
import java.util.concurrent.Executor;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.IOEvent;
import org.glassfish.grizzly.EventLifeCycleListener;
import org.glassfish.grizzly.Processor;

/**
 * {@link org.glassfish.grizzly.IOStrategy}, which executes {@link Processor}s in worker thread.
 *
 *
 */
public final class WorkerThreadIOStrategy extends AbstractIOStrategy {

    private final static EnumSet<IOEvent> WORKER_THREAD_EVENT_SET =
            EnumSet.<IOEvent>of(IOEvent.READ, IOEvent.WRITE, IOEvent.CLOSED);
    
    private static final WorkerThreadIOStrategy INSTANCE = new WorkerThreadIOStrategy();

    private static final Logger logger = Grizzly.logger(WorkerThreadIOStrategy.class);


    // ------------------------------------------------------------ Constructors


    private WorkerThreadIOStrategy() { }


    // ---------------------------------------------------------- Public Methods


    public static WorkerThreadIOStrategy getInstance() {
        return INSTANCE;
    }

    // ------------------------------------------------- Methods from IOStrategy


    @Override
    public Executor getThreadPoolFor(final Connection connection,
            final IOEvent ioEvent) {
        return WORKER_THREAD_EVENT_SET.contains(ioEvent) ?
                getWorkerThreadPool(connection) :
                null;
    }
    
    @Override
    public boolean executeIOEvent(final Connection connection,
            final IOEvent ioEvent,
            final EventLifeCycleListener lifeCycleListener) {
        return executeIOEvent(connection, ioEvent, lifeCycleListener,
                getThreadPoolFor(connection, ioEvent));
    }

    @Override
    public boolean executeIOEvent(final Connection connection,
            final IOEvent ioEvent,
            final DecisionListener listener) throws IOException {
        
        final Executor executor = getThreadPoolFor(connection, ioEvent);
        
        EventLifeCycleListener lifeCycleListener = null;
        
        if (listener != null) {
            lifeCycleListener = executor != null ?
                    listener.goAsync(connection, ioEvent) :
                    listener.goSync(connection, ioEvent);
        }
    
        return executeIOEvent(connection, ioEvent, lifeCycleListener, executor);
    }

    // --------------------------------------------------------- Private Methods
    
    private boolean executeIOEvent(final Connection connection,
            final IOEvent ioEvent,
            final EventLifeCycleListener lifeCycleListener,
            final Executor executor) {
        
        if (executor != null) {
            executor.execute(new WorkerThreadRunnable(connection, ioEvent,
                    lifeCycleListener));
        } else {
            fireEvent(connection, ioEvent, lifeCycleListener, logger);
        }
        
        return true;
    }
    
    private static final class WorkerThreadRunnable implements Runnable {
        final Connection connection;
        final IOEvent event;
        final EventLifeCycleListener lifeCycleListener;
        
        private WorkerThreadRunnable(final Connection connection,
                final IOEvent event,
                final EventLifeCycleListener lifeCycleListener) {
            this.connection = connection;
            this.event = event;
            this.lifeCycleListener = lifeCycleListener;
            
        }

        @Override
        public void run() {
            fireEvent(connection, event, lifeCycleListener, logger);
        }        
    }

}
