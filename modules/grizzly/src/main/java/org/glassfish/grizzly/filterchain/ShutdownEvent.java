/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2017 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.filterchain;


import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

/**
 * An event that {@link Filter} implementations may listen for if special processing is required
 * during a graceful shutdown.
 *
 * @since 2.4.0
 */
public class ShutdownEvent implements FilterChainEvent {

    public static final Object TYPE = ShutdownEvent.class.getName();
    private Set<Callable<Filter>> shutdownFutures;
    private long gracePeriod;
    private TimeUnit timeUnit;


    // ----------------------------------------------------------- Constructors


    /**
     * Create a new {@link ShutdownEvent} with the grace period for the shutdown.
     */
    public ShutdownEvent(final long gracePeriod, final TimeUnit timeUnit) {
        this.gracePeriod = gracePeriod;
        this.timeUnit = timeUnit;
    }

    /**
     * @inheritDoc
     */
    @Override
    public Object type() {
        return TYPE;
    }


    /**
     * Adds a task to this event.  Tasks should be called on separate threads after all {@link Filter}s in the
     * chain have been notified of the impending shutdown.
     */
    public void addShutdownTask(final Callable<Filter> future) {
        if (future == null) {
            return;
        }
        if (shutdownFutures == null) {
            shutdownFutures = new LinkedHashSet<>(4);
        }
        shutdownFutures.add(future);
    }

    /**
     * @return a {@link Set} of {@link Callable<Filter>} instances that need to be
     *  checked in order to proceed with terminating processing.
     */
    public Set<Callable<Filter>> getShutdownTasks() {
        return ((shutdownFutures != null) ? shutdownFutures : Collections.emptySet());
    }

    /**
     * @return the shutdown grace period.
     */
    public long getGracePeriod() {
        return gracePeriod;
    }

    /**
     * @return the {@link TimeUnit} of the grace period.
     */
    public TimeUnit getTimeUnit() {
        return timeUnit;
    }
}
