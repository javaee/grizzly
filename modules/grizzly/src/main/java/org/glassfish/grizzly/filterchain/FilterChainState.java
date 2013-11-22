/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved.
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

import org.glassfish.grizzly.Event;

/**
 * The implementations of this interface are responsible for holding a
 * {@link Connection} state associated with a {@link Filter} in a {@link FilterChain}.
 * 
 * The same {@link Filter} instance registered on different {@link FilterChain}s
 * can have different state associated with the same {@link Connection}.
 */
public interface FilterChainState {
    /**
     * Returns <tt>true</tt> if there is a stored state associated with the
     * {@link Filter}-in-{@link FilterChain} for the given {@link Event},
     * or <tt>false</tt> otherwise.
     * 
     * @param filterReg {@link FilterReg} representing the {@link Filter} registered
     *        in a {@link FilterChain}
     * @param event the {@link Event}
     * @return <tt>true</tt> if there is a stored state associated with the
     *         {@link Filter}-in-{@link FilterChain} for the given {@link Event},
     *         or <tt>false</tt> otherwise
     */
    boolean hasFilterState(FilterReg filterReg, Event event);
    
    /**
     * Returns the {@link FilterState} associated with the
     * {@link Filter}-in-{@link FilterChain} for the given {@link Event},
     * or <tt>null</tt> if there is no state associated.
     * 
     * @param filterReg {@link FilterReg} representing the {@link Filter} registered
     *        in a {@link FilterChain}
     * @param event the {@link Event}
     * @return the {@link FilterState} associated with the
     *         {@link Filter}-in-{@link FilterChain} for the given {@link Event},
     *         or <tt>null</tt> if there is no state associated
     */
    FilterState getFilterState(FilterReg filterReg, Event event);
    /**
     * Returns the {@link FilterState} associated with the
     * {@link Filter}-in-{@link FilterChain} for the given {@link Event},
     * or an empty {@link FilterState}, that should be used to store the state.
     * 
     * @param filterReg {@link FilterReg} representing the {@link Filter} registered
     *        in a {@link FilterChain}
     * @param event the {@link Event}
     * @return the {@link FilterState} associated with the
     *         {@link Filter}-in-{@link FilterChain} for the given {@link Event},
     *         or an empty {@link FilterState}, that should be used to store the state.
     */
    FilterState obtainFilterState(FilterReg filterReg, Event event);
}
