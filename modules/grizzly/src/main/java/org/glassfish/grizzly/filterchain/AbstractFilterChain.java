/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2015 Oracle and/or its affiliates. All rights reserved.
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

import java.util.EnumSet;
import org.glassfish.grizzly.Closeable;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Context;
import org.glassfish.grizzly.IOEvent;

/**
 * Abstract {@link FilterChain} implementation,
 * which redirects {@link org.glassfish.grizzly.Processor#process(org.glassfish.grizzly.Context)}
 * call to the {@link AbstractFilterChain#execute(org.glassfish.grizzly.filterchain.FilterChainContext)}
 *
 * @see FilterChain
 * 
 * @author Alexey Stashok
 */
public abstract class AbstractFilterChain implements FilterChain {
    // By default interested in all client connection related events
    protected final EnumSet<IOEvent> interestedIoEventsMask = EnumSet.allOf(IOEvent.class);

    /**
     * {@inheritDoc}
     */
    @Override
    public int indexOfType(final Class<? extends Filter> filterType) {
        final int size = size();
        for (int i = 0; i < size; i++) {
            final Filter filter = get(i);
            if (filterType.isAssignableFrom(filter.getClass())) {
                return i;
            }
        }

        return -1;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isInterested(final IOEvent ioEvent) {
        return interestedIoEventsMask.contains(ioEvent);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setInterested(final IOEvent ioEvent, final boolean isInterested) {
        if (isInterested) {
            interestedIoEventsMask.add(ioEvent);
        } else {
            interestedIoEventsMask.remove(ioEvent);
        }
    }

    @Override
    public final FilterChainContext obtainFilterChainContext(
            final Connection connection) {

        final FilterChainContext context = FilterChainContext.create(connection);
        context.internalContext.setProcessor(this);
        return context;
    }

    @Override
    public FilterChainContext obtainFilterChainContext(
            final Connection connection,
            final Closeable closeable) {
        final FilterChainContext context = FilterChainContext.create(
                connection, closeable);
        context.internalContext.setProcessor(this);
        return context;
    }

    @Override
    public FilterChainContext obtainFilterChainContext(
            final Connection connection, final int startIdx, final int endIdx,
            final int currentIdx) {
        final FilterChainContext ctx = obtainFilterChainContext(connection);

        ctx.setStartIdx(startIdx);
        ctx.setEndIdx(endIdx);
        ctx.setFilterIdx(currentIdx);

        return ctx;
    }

    @Override
    public FilterChainContext obtainFilterChainContext(
            final Connection connection,
            final Closeable closeable,
            final int startIdx, final int endIdx, final int currentIdx) {
        
        final FilterChainContext ctx =
                obtainFilterChainContext(connection, closeable);

        ctx.setStartIdx(startIdx);
        ctx.setEndIdx(endIdx);
        ctx.setFilterIdx(currentIdx);

        return ctx;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder(256);
        sb.append(getClass().getSimpleName())
                .append('@')
                .append(Integer.toHexString(hashCode()))
                .append(" {");
        
        final int size = size();
        
        if (size > 0) {
            sb.append(get(0).toString());
            for (int i = 1; i < size; i++) {
                sb.append(" <-> ");
                sb.append(get(i).toString());
            }
        }
        
        sb.append('}');
        return sb.toString();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final Context obtainContext(final Connection connection) {
        return obtainFilterChainContext(connection).internalContext;
    }

    @Override
    protected void finalize() throws Throwable {
        clear();
        super.finalize();
    }
}
