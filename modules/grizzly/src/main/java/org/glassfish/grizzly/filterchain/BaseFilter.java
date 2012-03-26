/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2012 Oracle and/or its affiliates. All rights reserved.
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
import org.glassfish.grizzly.filterchain.FilterChainContext.Operation;
import java.lang.ref.WeakReference;
import org.glassfish.grizzly.Event;

/**
 * Provides empty implementation for {@link Filter} processing methods.
 *
 * @see Filter
 * 
 * @author Alexey Stashok
 */
public class BaseFilter implements Filter {

    private int index;
    private WeakReference<FilterChain> filterChain;

    @Override
    public void onFilterChainConstructed(final FilterChain filterChain) {
        index = filterChain.indexOf(this);
        this.filterChain = new WeakReference<FilterChain>(filterChain);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public NextAction handleRead(FilterChainContext ctx) throws IOException {
        return ctx.getInvokeAction();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public NextAction handleWrite(FilterChainContext ctx) throws IOException {
        return ctx.getInvokeAction();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public NextAction handleConnect(FilterChainContext ctx) throws IOException {
        return ctx.getInvokeAction();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public NextAction handleAccept(FilterChainContext ctx) throws IOException {
        return ctx.getInvokeAction();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public NextAction handleEvent(final FilterChainContext ctx,
            final Event event) throws IOException {
        return ctx.getInvokeAction();
    }

    /**
     * {@inheritDoc}
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
     * on the current thread.
     * 
     * @return the {@link FilterChain}, which is currently 
     *         executing this {@link Filter}
     */
    public FilterChain getFilterChain() {
        final WeakReference<FilterChain> localRef = filterChain;
        return localRef != null ? localRef.get() : null;
    }

    /**
     * @return the index of this filter within the {@link FilterChain}.
     */
    public int getIndex() {
        return index;
    }

    public FilterChainContext createContext(final Connection connection,
            final Operation operation) {
        final FilterChainContext ctx =
                getFilterChain().obtainFilterChainContext(connection);

        ctx.setOperation(operation);
        ctx.setFilterIdx(index);
        ctx.setStartIdx(index);

        return ctx;
    }
}
