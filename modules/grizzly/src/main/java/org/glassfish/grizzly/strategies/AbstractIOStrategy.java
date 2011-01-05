/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2011 Oracle and/or its affiliates. All rights reserved.
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

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.glassfish.grizzly.strategies;

import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Context;
import org.glassfish.grizzly.IOEvent;
import org.glassfish.grizzly.PostProcessor;
import org.glassfish.grizzly.ProcessorResult.Status;
import org.glassfish.grizzly.IOStrategy;
import org.glassfish.grizzly.nio.NIOConnection;
import org.glassfish.grizzly.nio.NIOTransport;
import org.glassfish.grizzly.threadpool.ThreadPoolConfig;

import java.io.IOException;
import java.util.concurrent.Executor;

/**
 *
 * @author oleksiys
 */
public abstract class AbstractIOStrategy implements IOStrategy {
    // COMPLETE, COMPLETE_LEAVE, RE-REGISTER, RERUN, ERROR, TERMINATE, NOT_RUN
    private final static boolean[] isRegisterMap = {true, false, true, false, false, false, false};

    protected final static PostProcessor enableInterestPostProcessor =
            new EnableInterestPostProcessor();


    // ----------------------------- Methods from WorkerThreadPoolConfigProducer


    @Override
    public ThreadPoolConfig createDefaultWorkerPoolConfig(final NIOTransport transport) {

        final ThreadPoolConfig config = ThreadPoolConfig.defaultConfig().clone();
        final int selectorRunnerCount = transport.getSelectorRunnersCount();
        config.setCorePoolSize(selectorRunnerCount * 2);
        config.setMaxPoolSize(selectorRunnerCount * 2);
        config.setMemoryManager(transport.getMemoryManager());
        return config;

    }


    // ------------------------------------------------------- Protected Methods


    protected static boolean isReadWrite(final IOEvent ioEvent) {
        return (ioEvent == IOEvent.READ || ioEvent == IOEvent.WRITE);
    }

    protected static boolean isExecuteInWorkerThread(final IOEvent ioEvent) {
        return (ioEvent == IOEvent.READ
                   || ioEvent == IOEvent.WRITE
                   || ioEvent == IOEvent.CLOSED);
    }

    protected static Executor getWorkerThreadPool(final Connection c) {
        return c.getTransport().getWorkerThreadPool();
    }


    // ---------------------------------------------------------- Nested Classes


    private static class EnableInterestPostProcessor implements PostProcessor {
        @Override
        public void process(Context context, Status status) throws IOException {
            if (isRegisterMap[status.ordinal()]) {
                final IOEvent ioEvent = context.getIoEvent();
                final NIOConnection nioConnection = (NIOConnection) context.getConnection();
                nioConnection.enableIOEvent(ioEvent);
            }
        }
    }
}
