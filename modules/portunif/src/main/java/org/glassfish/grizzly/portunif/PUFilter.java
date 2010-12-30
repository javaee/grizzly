/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2007-2010 Oracle and/or its affiliates. All rights reserved.
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
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.grizzly.CompletionHandler;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Context;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.IOEvent;
import org.glassfish.grizzly.PostProcessor;
import org.glassfish.grizzly.ProcessorExecutor;
import org.glassfish.grizzly.ProcessorResult.Status;
import org.glassfish.grizzly.attributes.Attribute;
import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.Filter;
import org.glassfish.grizzly.filterchain.FilterChain;
import org.glassfish.grizzly.filterchain.FilterChainBuilder;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.FilterChainEvent;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.utils.ArraySet;

/**
 *
 * @author oleksiys
 */
public class PUFilter extends BaseFilter {
    private static final Logger LOGGER = Grizzly.logger(PUFilter.class);

    private final BackChannelFilter backChannelFilter =
            new BackChannelFilter(this);
    private final ArraySet<PUProtocol> protocols =
            new ArraySet<PUProtocol>(PUProtocol.class);
    
    final Attribute<PUContext> puContextAttribute;
    final Attribute<Boolean> isProcessingAttribute;
    final Attribute<FilterChainContext> suspendedContextAttribute;

    public PUFilter() {
        puContextAttribute =
                Grizzly.DEFAULT_ATTRIBUTE_BUILDER.createAttribute(
                        PUFilter.class.getName() + "-" + hashCode() + ".puContext");
        isProcessingAttribute =
                Grizzly.DEFAULT_ATTRIBUTE_BUILDER.createAttribute(
                PUFilter.class.getName() + "-" + hashCode() + ".isProcessing");
        suspendedContextAttribute =
                Grizzly.DEFAULT_ATTRIBUTE_BUILDER.createAttribute(
                        PUFilter.class.getName() + "-" + hashCode() + ".suspendedContext");
    }

    public PUProtocol register(final ProtocolFinder protocolFinder,
            final FilterChain filterChain) {

        final PUProtocol puProtocol = new PUProtocol(protocolFinder, filterChain);
        register(puProtocol);

        return puProtocol;
    }

    public void register(final PUProtocol puProtocol) {
        final Filter filter = puProtocol.getFilterChain().get(0);
        if (filter != backChannelFilter) {
            throw new IllegalStateException("The first Filter in the protocol should be the BackChannelFilter");
        }

        protocols.add(puProtocol);
    }

    public void deregister(final PUProtocol puProtocol) {
        protocols.remove(puProtocol);
    }

    public Set<PUProtocol> getProtocols() {
        return protocols;
    }

    public Filter getBackChannelFilter() {
        return backChannelFilter;
    }

    public FilterChainBuilder getPUFilterChainBuilder() {
        final FilterChainBuilder builder = FilterChainBuilder.stateless();
        builder.add(backChannelFilter);
        return builder;
    }

    @Override
    public NextAction handleRead(final FilterChainContext ctx) throws IOException {
        if (isProcessingAttribute.get(ctx) == Boolean.TRUE) {
            isProcessingAttribute.remove(ctx);
            return ctx.getStopAction();
        }

        final Connection connection = ctx.getConnection();
        PUContext puContext = puContextAttribute.get(connection);
        if (puContext == null) {
            puContext = new PUContext();
            puContextAttribute.set(connection, puContext);
        } else if (puContext.lastResult == ProtocolFinder.Result.NOT_FOUND) {
            return ctx.getInvokeAction();
        }

        PUProtocol protocol = puContext.protocol;

        if (protocol == null) {
            findProtocol(puContext, ctx);
            protocol = puContext.protocol;
        }
        
        if (protocol != null) {
            if (!puContext.isSticky) {
                puContext.reset();
            }
            
            isProcessingAttribute.set(ctx, Boolean.TRUE);

            final FilterChain filterChain = protocol.getFilterChain();

            final FilterChainContext filterChainContext =
                    filterChain.obtainFilterChainContext(connection);
            final Context context = filterChainContext.getInternalContext();
            context.setIoEvent(IOEvent.READ);
            context.setPostProcessor(new InternalPostProcessor());
            filterChainContext.setAddress(ctx.getAddress());
            filterChainContext.setMessage(ctx.getMessage());
            suspendedContextAttribute.set(context, ctx);
            ProcessorExecutor.execute(context);

            return ctx.getSuspendAction();
        }

        if (puContext.lastResult == ProtocolFinder.Result.NOT_FOUND) {
            return ctx.getInvokeAction();
        }
        
        return ctx.getStopAction(ctx.getMessage());
    }

    @Override
    public NextAction handleEvent(final FilterChainContext ctx,
            final FilterChainEvent event) throws IOException {

        // if upstream event - pass it to the puFilter
        if (isUpstream(ctx)) {

            final Connection connection = ctx.getConnection();
            final PUContext puContext = puContextAttribute.get(connection);
            final PUProtocol protocol;
            if (puContext != null && (protocol = puContext.protocol) != null) {
                isProcessingAttribute.set(ctx, Boolean.TRUE);

                final FilterChain filterChain = protocol.getFilterChain();
                final FilterChainContext context = filterChain.obtainFilterChainContext(connection);
                context.setStartIdx(-1);
                context.setFilterIdx(-1);
                context.setEndIdx(filterChain.size());

                suspendedContextAttribute.set(context, ctx);
                context.notifyUpstream(event, new InternalCompletionHandler(ctx));
                
                return ctx.getSuspendAction();
            }
        }

        return ctx.getInvokeAction();
    }

    @Override
    public NextAction handleClose(FilterChainContext ctx) throws IOException {
        return super.handleClose(ctx);
    }

    private void findProtocol(final PUContext puContext,
            final FilterChainContext ctx) {
        final PUProtocol[] protocolArray = protocols.getArray();
        for (int i = puContext.lastProtocolFinderIdx; i < protocolArray.length; i++) {
            puContext.lastProtocolFinderIdx = i;
            final PUProtocol protocol = protocolArray[i];
            try {
                final ProtocolFinder.Result result =
                        protocol.getProtocolFinder().find(puContext, ctx);

                puContext.lastResult = result;

                switch (result) {
                    case FOUND: {
                        puContext.protocol = protocol;
                        return;
                    }

                    case NEED_MORE_DATA: {
                        return;
                    }
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING,
                        "ProtocolFinder " + protocol.getProtocolFinder() +
                        " reported error", e);
            }
        }
    }

    private static boolean isUpstream(final FilterChainContext context) {
        return context.getStartIdx() < context.getEndIdx();
    }


    private class InternalPostProcessor implements PostProcessor {
        
        @Override
        public void process(final Context context, final Status status) throws IOException {
            final FilterChainContext suspendedContext =
                    suspendedContextAttribute.remove(context);

            assert suspendedContext != null;
            
            switch (status) {
                case COMPLETE:
                case REREGISTER:
                    suspendedContext.resume();
            }
        }
    }

    private static class InternalCompletionHandler implements
            CompletionHandler<FilterChainContext> {

        private final FilterChainContext suspendedContext;

        public InternalCompletionHandler(FilterChainContext suspendedContext) {
            this.suspendedContext = suspendedContext;
        }

        @Override
        public void cancelled() {
            suspendedContext.fail(new CancellationException());
        }

        @Override
        public void failed(final Throwable throwable) {
            suspendedContext.fail(throwable);
        }

        @Override
        public void completed(final FilterChainContext context) {
            suspendedContext.resume();
        }

        @Override
        public void updated(FilterChainContext result) {
        }

    }
}
