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

package com.sun.grizzly.filterchain;

import com.sun.grizzly.Connection;
import java.io.IOException;
import com.sun.grizzly.filterchain.FilterChainContext.Operation;
import java.lang.ref.WeakReference;

/**
 * Provides empty implementation for {@link Filter} processing methods.
 *
 * @see Filter
 * 
 * @author Alexey Stashok
 */
public class BaseFilter implements Filter {

    private volatile int index;
    private volatile WeakReference<FilterChain> filterChain;

    @Override
    public void onAdded(FilterChain filterChain) {
        index = filterChain.indexOf(this);
        this.filterChain = new WeakReference<FilterChain>(filterChain);
    }

    @Override
    public void onFilterChainChanged(FilterChain filterChain) {
        index = filterChain.indexOf(this);
    }

    @Override
    public void onRemoved(FilterChain filterChain) {
        index = filterChain.indexOf(this);
    }

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
    @Override
    public NextAction handleRead(FilterChainContext ctx) throws IOException {
        return ctx.getInvokeAction();
    }

    /**
     * Execute a unit of processing work to be performed, when channel will 
     * become available for writing. 
     * This {@link Filter} may either complete the required processing and 
     * return false, or delegate remaining processing to the next 
     * {@link Filter} in a {@link FilterChain} containing this {@link Filter} 
     * by returning true.
     * @param ctx {@link FilterChainContext}
     * @return {@link NextAction} instruction for {@link FilterChain}, how it
     *         should continue the execution
     * @throws {@link java.io.IOException}
     */
    @Override
    public NextAction handleWrite(FilterChainContext ctx) throws IOException {
        return ctx.getInvokeAction();
    }

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
    @Override
    public NextAction handleConnect(FilterChainContext ctx) throws IOException {
        return ctx.getInvokeAction();
    }

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
    @Override
    public NextAction handleAccept(FilterChainContext ctx) throws IOException {
        return ctx.getInvokeAction();
    }

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
    @Override
    public NextAction handleClose(FilterChainContext ctx) throws IOException {
        return ctx.getInvokeAction();
    }

    /**
     * Notification about exception, occurred on the {@link FilterChain}
     *
     * @param ctx event processing {@link FilterChainContext}
     * @param error error, which occurred during <tt>FilterChain</tt> execution
     */
    @Override
    public void exceptionOccurred(FilterChainContext ctx, Throwable error) {
    }
    
    /**
     * Returns the {@link FilterChain}, which is executing this {@link Filter}
     * on the current thread. Because {@link Filter} could be shared among
     * several {@link FilterChain} - we need {@link IOEventContext} to get
     * the {@link FilterChain}, which is running this {@link Filter}
     * 
     * @param ctx the execution {@link IOEventContext}
     * @return the {@link FilterChain}, which is currently 
     *         executing this {@link Filter}
     */
    public FilterChain getFilterChain() {
        final WeakReference<FilterChain> localRef = filterChain;
        return localRef != null ? localRef.get() : null;
    }

    /**
     * {@inheritDoc}
     */
    public int getIndex() {
        return index;
    }

    public FilterChainContext createContext(final Connection connection,
            Operation operation) {
        final FilterChainContext ctx =
                getFilterChain().obtainFilterChainContext(connection);

        ctx.setOperation(operation);
        ctx.setFilterIdx(index);
        ctx.setStartIdx(index);

        return ctx;
    }
}
