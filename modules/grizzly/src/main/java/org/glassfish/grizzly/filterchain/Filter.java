/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2010 Oracle and/or its affiliates. All rights reserved.
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

import org.glassfish.grizzly.Connection;
import java.io.IOException;
import org.glassfish.grizzly.attributes.Attribute;

/**
 * A Filter encapsulates a unit of processing work to be performed, 
 * whose purpose is to examine and/or modify the state of a transaction that is 
 * represented by a {@link FilterChainContext}. Individual Filter can be assembled
 * into a {@link FilterChain}, which allows them to either complete the required 
 * processing or delegate further processing to the next Filter in the 
 * {@link FilterChain}.
 *
 * Filter implementations should be designed in a thread-safe manner, 
 * suitable for inclusion in multiple {@link FilterChain} that might be 
 * processed by different threads simultaneously. In general, this implies that 
 * Filter classes should not maintain state information in instance variables. 
 * Instead, state information should be maintained via suitable modifications to
 * the attributes of the {@link FilterChainContext} that is passed to the
 * appropriate {@link Filter} processing methods.
 *
 * Filter implementations typically retrieve and store state information
 * in the {@link FilterChainContext} instance that is passed as a parameter
 * to the appropriate {@link Filter} processing methods,
 * using particular {@link Attribute}s that can
 * be acquired via {@link Attribute#get(org.glassfish.grizzly.attributes.AttributeStorage)} method.
 *
 * @see FilterChain
 * @see Attribute
 * 
 * @author Jeanfrancois Arcand
 * @author Alexey Stashok
 */
public interface Filter {
    /**
     * Method is called, when the <tt>Filter</tt> has been added to the
     * passed {@link FilterChain}.
     *
     * @param filterChain the {@link FilterChain} this <tt>Filter</tt> was added to.
     */
    public void onAdded(FilterChain filterChain);
    
    /**
     * Method is called, when the <tt>Filter</tt> has been removed from the
     * passed {@link FilterChain}.
     *
     * @param filterChain the {@link FilterChain} this <tt>Filter</tt> was removed from.
     */
    public void onRemoved(FilterChain filterChain);

    /**
     * Method is called, when the {@link FilterChain} this <tt>Filter</tt> is part of,
     * has been changed.
     *
     * @param filterChain the {@link FilterChain}.
     */
    public void onFilterChainChanged(FilterChain filterChain);
    
    /**
     * Execute a unit of processing work to be performed, when channel will
     * become available for reading.
     * This {@link Filter} may either complete the required processing and
     * return false, or delegate remaining processing to the next
     * {@link Filter} in a {@link FilterChain} containing this {@link Filter}
     * by returning true.
     * @param ctx {@link FilterChainContext}
     * @return {@link NextAction} instruction for {@link FilterChain}, how it
     *         should continue the execution
     * @throws {@link java.io.IOException}
     */
    public NextAction handleRead(FilterChainContext ctx) throws IOException;

    /**
     * Execute a unit of processing work to be performed, when some data should
     * be written on channel.
     * This {@link Filter} may either complete the required processing and
     * return false, or delegate remaining processing to the next
     * {@link Filter} in a {@link FilterChain} containing this {@link Filter}
     * by returning true.
     * @param ctx {@link FilterChainContext}
     * @return {@link NextAction} instruction for {@link FilterChain}, how it
     *         should continue the execution
     * @throws {@link java.io.IOException}
     */
    public NextAction handleWrite(FilterChainContext ctx) throws IOException;

    /**
     * Execute a unit of processing work to be performed, when channel gets
     * connected.
     * This {@link Filter} may either complete the required processing and
     * return false, or delegate remaining processing to the next
     * {@link Filter} in a {@link FilterChain} containing this {@link Filter}
     * by returning true.
     * @param ctx {@link FilterChainContext}
     * @return {@link NextAction} instruction for {@link FilterChain}, how it
     *         should continue the execution
     * @throws {@link java.io.IOException}
     */
    public NextAction handleConnect(FilterChainContext ctx) throws IOException;

    /**
     * Execute a unit of processing work to be performed, when server channel
     * has accepted the client connection.
     * This {@link Filter} may either complete the required processing and
     * return false, or delegate remaining processing to the next
     * {@link Filter} in a {@link FilterChain} containing this {@link Filter}
     * by returning true.
     * @param ctx {@link FilterChainContext}
     * @return {@link NextAction} instruction for {@link FilterChain}, how it
     *         should continue the execution
     * @throws {@link java.io.IOException}
     */
    public NextAction handleAccept(FilterChainContext ctx) throws IOException;

    /**
     * Handle custom event associated with the {@link Connection}.
     * This {@link Filter} may either complete the required processing and
     * return {@link StopAction}, or delegate remaining processing to the next
     * {@link Filter} in a {@link FilterChain} containing this {@link Filter}
     * by returning {@link InvokeAction}.
     * @param ctx {@link FilterChainContext}
     * @param event
     * @return {@link NextAction} instruction for {@link FilterChain}, how it
     *         should continue the execution
     * @throws {@link java.io.IOException}
     */
    public NextAction handleEvent(FilterChainContext ctx,
            FilterChainEvent event) throws IOException;

    /**
     * Execute a unit of processing work to be performed, when connection
     * has been closed.
     * This {@link Filter} may either complete the required processing and
     * return false, or delegate remaining processing to the next
     * {@link Filter} in a {@link FilterChain} containing this {@link Filter}
     * by returning true.
     * @param ctx {@link FilterChainContext}
     * @return {@link NextAction} instruction for {@link FilterChain}, how it
     *         should continue the execution
     * @throws {@link java.io.IOException}
     */
    public NextAction handleClose(FilterChainContext ctx) throws IOException;

    
    /**
     * Notification about exception, occurred on the {@link FilterChain}
     * 
     * @param ctx event processing {@link FilterChainContext}
     * @param error error, which occurred during <tt>FilterChain</tt> execution
     */
    public void exceptionOccurred(FilterChainContext ctx, Throwable error);
}
