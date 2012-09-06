/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2012 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.portunif;

import java.io.IOException;
import org.glassfish.grizzly.ReadResult;
import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.FilterChain;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.FilterChainEvent;
import org.glassfish.grizzly.filterchain.FilterChainContext.TransportContext;
import org.glassfish.grizzly.filterchain.NextAction;

/**
 * The {@link org.glassfish.grizzly.filterchain.Filter}, which is responsible to connect custom protocol {@link FilterChain} with
 * main {@link FilterChain}. Usually this {@link org.glassfish.grizzly.filterchain.Filter} is getting added to the
 * custom protocol {@link FilterChain} as first {@link org.glassfish.grizzly.filterchain.Filter}.
 * 
 * @author Alexey Stashok
 */
public class BackChannelFilter extends BaseFilter {
    private final PUFilter puFilter;

    BackChannelFilter(final PUFilter puFilter) {
        this.puFilter = puFilter;
    }

    @Override
    public NextAction handleRead(final FilterChainContext ctx) throws IOException {
        // If this method is called as part of natural PU filterchain processing -
        // just pass process to the next filter
        if (!isFilterChainRead(ctx)) {
            return ctx.getInvokeAction();
        }

        // if this is filterchain read - delegate read to the underlying filterchain
        final FilterChainContext suspendedParentContext =
                puFilter.suspendedContextAttribute.get(ctx);

        assert suspendedParentContext != null;

        final ReadResult readResult = suspendedParentContext.read();

        ctx.setMessage(readResult.getMessage());
        ctx.setAddressHolder(readResult.getSrcAddressHolder());

        readResult.recycle();

        return ctx.getInvokeAction();
    }

    /**
     * Methods returns <tt>true</tt>, if {@link #handleRead(org.glassfish.grizzly.filterchain.FilterChainContext)}
     * is called because user explicitly initiated FilterChain by calling
     * {@link FilterChainContext#read()} or {@link FilterChain#read(org.glassfish.grizzly.filterchain.FilterChainContext)};
     * otherwise <tt>false</tt> is returned.
     */
    private boolean isFilterChainRead(final FilterChainContext ctx) {
        return ctx.getMessage() == null;
    }


    @Override
    @SuppressWarnings("unchecked")
    public NextAction handleWrite(final FilterChainContext ctx) throws IOException {
        final FilterChainContext suspendedParentContext =
                puFilter.suspendedContextAttribute.get(ctx);

        assert suspendedParentContext != null;
        
        final TransportContext transportContext = ctx.getTransportContext();

        suspendedParentContext.write(ctx.getAddress(), ctx.getMessage(),
                transportContext.getCompletionHandler(),
                transportContext.getPushBackHandler(),
                transportContext.getMessageCloner(),
                transportContext.isBlocking());

        return ctx.getStopAction();
    }

    @Override
    public NextAction handleEvent(final FilterChainContext ctx,
            final FilterChainEvent event) throws IOException {
        
        // if downstream event - pass it to the puFilter
        if (isDownstream(ctx)) {
            final FilterChainContext suspendedParentContext =
                    puFilter.suspendedContextAttribute.get(ctx);

            assert suspendedParentContext != null;

            suspendedParentContext.notifyDownstream(event);
        }

        return ctx.getInvokeAction();
    }

    @Override
    public void exceptionOccurred(final FilterChainContext ctx,
            final Throwable error) {
        
        // if downstream event - pass it to the puFilter
        if (isDownstream(ctx)) {
            final FilterChainContext suspendedParentContext =
                    puFilter.suspendedContextAttribute.get(ctx);

            assert suspendedParentContext != null;

            suspendedParentContext.fail(error);
        }
    }

    private static boolean isDownstream(final FilterChainContext context) {
        return context.getStartIdx() > context.getEndIdx();
    }
}
