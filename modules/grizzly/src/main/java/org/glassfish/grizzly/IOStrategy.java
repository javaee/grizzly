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

package org.glassfish.grizzly;

import org.glassfish.grizzly.strategies.WorkerThreadPoolConfigProducer;

import java.io.IOException;

/**
 * <tt>strategy</tt> is responsible for making decision how
 * {@link Runnable} task will be run: in current thread, worker thread.
 *
 * <tt>strategy</tt> can make any other processing decisions.
 * 
 * @author Alexey Stashok
 */
public interface IOStrategy extends WorkerThreadPoolConfigProducer {


    /**
     * The {@link org.glassfish.grizzly.nio.SelectorRunner} will invoke this
     * method to allow the strategy implementation to decide how the
     * {@link Event} will be handled.
     *
     * @param connection the {@link Connection} upon which the provided
     *  {@link Event} occurred.
     * @param event the {@link Event} that triggered execution of this
     *  <code>strategy</code>
     *
     * @return <tt>true</tt>, if this thread should keep processing Events on
     * the current and other Connections, or <tt>false</tt> if this thread
     * should hand-off the farther Event processing on any Connections,
     * which means IOStrategy is becoming responsible for continuing Event
     * processing (possibly starting new thread, which will handle Events).
     *
     * @throws IOException if an error occurs processing the {@link Event}.
     */
    boolean executeEvent(final Connection connection, final Event event)
    throws IOException;
    
    /**
     * The {@link org.glassfish.grizzly.nio.SelectorRunner} will invoke this
     * method to allow the strategy implementation to decide how the
     * {@link ServiceEvent} will be handled.
     *
     * @param connection the {@link Connection} upon which the provided
     *  {@link ServiceEvent} occurred.
     * @param serviceEvent the {@link ServiceEvent} that triggered execution of this
     *  <code>strategy</code>
     *
     * @return <tt>true</tt>, if this thread should keep processing ServiceEvents on
     * the current and other Connections, or <tt>false</tt> if this thread
     * should hand-off the farther ServiceEvent processing on any Connections,
     * which means IOStrategy is becoming responsible for continuing ServiceEvent
     * processing (possibly starting new thread, which will handle ServiceEvents).
     *
     * @throws IOException if an error occurs processing the {@link ServiceEvent}.
     */
    boolean executeServiceEvent(final Connection connection, final ServiceEvent serviceEvent)
    throws IOException;

    /**
     * The {@link org.glassfish.grizzly.nio.SelectorRunner} will invoke this
     * method to allow the strategy implementation to decide how the
     * {@link ServiceEvent} will be handled.
     *
     * @param connection the {@link Connection} upon which the provided
     *  {@link ServiceEvent} occurred.
     * @param serviceEvent the {@link ServiceEvent} that triggered execution of this
     *  <code>strategy</code>
     * @param isServiceEventInterestEnabled <tt>true</tt> if ServiceEvent is still enabled on the
     *  {@link Connection}, or <tt>false</tt> if ServiceEvent was preliminary disabled
     *  or ServiceEvent is being simulated.
     *
     * @return <tt>true</tt>, if this thread should keep processing ServiceEvents on
     * the current and other Connections, or <tt>false</tt> if this thread
     * should hand-off the farther ServiceEvent processing on any Connections,
     * which means IOStrategy is becoming responsible for continuing ServiceEvent
     * processing (possibly starting new thread, which will handle ServiceEvents).
     *
     * @throws IOException if an error occurs processing the {@link ServiceEvent}.
     */
    boolean executeServiceEvent(final Connection connection, final ServiceEvent serviceEvent,
            final boolean isServiceEventInterestEnabled) throws IOException;
    
}
