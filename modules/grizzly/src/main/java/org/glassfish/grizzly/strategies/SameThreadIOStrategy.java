/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2009-2012 Oracle and/or its affiliates. All rights reserved.
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
import java.util.logging.Logger;

import org.glassfish.grizzly.*;
import org.glassfish.grizzly.asyncqueue.AsyncQueue;
import org.glassfish.grizzly.threadpool.ThreadPoolConfig;

/**
 * {@link org.glassfish.grizzly.IOStrategy}, which executes {@link org.glassfish.grizzly.Processor}s in a current thread.
 *
 * @author Alexey Stashok
 */
public final class SameThreadIOStrategy extends AbstractIOStrategy {

    private static final SameThreadIOStrategy INSTANCE = new SameThreadIOStrategy();

    private static final Logger logger = Grizzly.logger(SameThreadIOStrategy.class);


    private static final InterestProcessingHandlerWhenIoEnabled PROCESSING_HANDLER_WHEN_IO_ENABLED =
            new InterestProcessingHandlerWhenIoEnabled();

    private static final InterestProcessingHandlerWhenIoDisabled PROCESSING_HANDLER_WHEN_IO_DISABLED =
            new InterestProcessingHandlerWhenIoDisabled();
    
    // ------------------------------------------------------------ Constructors


    private SameThreadIOStrategy() { }


    // ---------------------------------------------------------- Public Methods


    public static SameThreadIOStrategy getInstance() {
        return INSTANCE;
    }


    // ------------------------------------------------- Methods from IOStrategy


    @Override
    public boolean executeServiceEvent(final Connection connection,
            final ServiceEvent serviceEvent, final boolean isServiceEventInterestEnabled)
            throws IOException {
        
        ServiceEventProcessingHandler ph = null;
        if (isReadWrite(serviceEvent)) {
            ph = isServiceEventInterestEnabled
                    ? PROCESSING_HANDLER_WHEN_IO_ENABLED
                    : PROCESSING_HANDLER_WHEN_IO_DISABLED;
        }

        fireEvent(connection, serviceEvent, ph, logger);

        return true;
    }

    @Override
    protected Logger getLogger() {
        return logger;
    }


    // ----------------------------------- Methods from WorkerThreadPoolConfigProducer


    @Override
    public ThreadPoolConfig createDefaultWorkerPoolConfig(final Transport transport) {
        return null;
    }

    // ---------------------------------------------------------- Nested Classes


    private static final class InterestProcessingHandlerWhenIoEnabled
            extends ServiceEventProcessingHandler.Adapter {

        @Override
        public void onReregister(final Context context) throws IOException {
            onComplete(context, null);
        }

        @Override
        public void onComplete(final Context context, final Object data) throws IOException {
            if (context.wasSuspended() || context.isManualServiceEventControl()) {
                final ServiceEvent serviceEvent = (ServiceEvent) context.getEvent();
                final Connection connection = context.getConnection();
                
                if (AsyncQueue.EXPECTING_MORE_OPTION.equals(data)) {
                    connection.simulateServiceEvent(serviceEvent);
                } else {
                    connection.enableServiceEventInterest(serviceEvent);
                }
            }
        }

        @Override
        public void onContextSuspend(final Context context) throws IOException {
            // check manual io event control, to not disable service event twice
            if (!context.isManualServiceEventControl()) {
                disableServiceEvent(context);
            }
        }

        @Override
        public void onContextManualServiceEventControl(final Context context)
                throws IOException {
            // check suspended mode, to not disable service event interest twice
            if (!context.wasSuspended()) {
                disableServiceEvent(context);
            }
        }

        private static void disableServiceEvent(final Context context)
                throws IOException {
            final Connection connection = context.getConnection();
            final ServiceEvent serviceEvent = (ServiceEvent) context.getEvent();
            connection.disableServiceEventInterest(serviceEvent);
        }

    }
    
    private static final class InterestProcessingHandlerWhenIoDisabled
            extends ServiceEventProcessingHandler.Adapter {

        @Override
        public void onReregister(final Context context) throws IOException {
            onComplete(context, null);
        }

        @Override
        public void onComplete(final Context context, final Object data)
                throws IOException {
            
            final ServiceEvent serviceEvent = (ServiceEvent) context.getEvent();
            final Connection connection = context.getConnection();
            if (AsyncQueue.EXPECTING_MORE_OPTION.equals(data)) {
                connection.simulateServiceEvent(serviceEvent);
            } else {
                connection.enableServiceEventInterest(serviceEvent);
            }
        }
    }    
}
