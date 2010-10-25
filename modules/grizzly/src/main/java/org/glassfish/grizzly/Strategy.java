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

package org.glassfish.grizzly;

import java.io.IOException;

/**
 * <tt>Strategy</tt> is responsible for making decision how
 * {@link Runnable} task will be run: in current thread, worker thread.
 *
 * <tt>Strategy</tt> can make any other processing decisions.
 * 
 * @author Alexey Stashok
 */
public interface Strategy {
    boolean executeIoEvent(Connection connection, IOEvent ioEvent) throws IOException;
//    /**
//     * Prepare {@link Strategy} for processing {@link IOEvent}, occured on the
//     * {@link Connection}.
//     * At this phase {@link Strategy} may initialize and return context data,
//     * which will be passed further into executeProcessor and isTerminateThread
//     * methods.
//     *
//     * @param connection {@link Connection}, on which {@link IOEvent} occured.
//     * @param ioEvent {@link IOEvent}.
//     * @return context/state, associated with the {@link IOEvent} processing.
//     */
//    public E prepare(Connection connection, IOEvent ioEvent);
//
//    /**
//     * Execute {@link ProcessorRunnable} task.
//     *
//     * @param strategyContext context object, initialized on "prepare" phase.
//     * @param processorRunnable the {@link ProcessorRunnable} task to be executed.
//     *
//     * @throws java.io.IOException
//     */
//    public void executeProcessor(E strategyContext,
//            ProcessorRunnable processorRunnable) throws IOException;
//
//    /**
//     * This method may be called by runner {@link Thread} after task will be
//     * executed. {@link Strategy} may instruct the caller to release current
//     * thread, after task execution will be completed.
//     *
//     * @param strategyContext {@link Strategy} context, initialized on "prepare"
//     * phase.
//     *
//     * @return <tt>true</tt>, to instruct caller to release the current
//     * {@link Thread}, or <tt>false</tt> otherwise.
//     *
//     * @see com.glassfish.grizzly.strategies.LeaderFollowerStrategy
//     */
//    public boolean isTerminateThread(E strategyContext);
}
