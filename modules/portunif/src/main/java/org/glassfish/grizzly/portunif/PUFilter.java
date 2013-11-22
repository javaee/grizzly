/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2013 Oracle and/or its affiliates. All rights reserved.
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
import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Context;
import org.glassfish.grizzly.EventProcessingHandler;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.IOEvent;
import org.glassfish.grizzly.ProcessorExecutor;
import org.glassfish.grizzly.attributes.Attribute;
import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.Filter;
import org.glassfish.grizzly.filterchain.FilterChain;
import org.glassfish.grizzly.filterchain.FilterChainBuilder;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.FilterReg;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.utils.ArraySet;

/**
 * Port unification filter.
 * 
 * @author Alexey Stashok
 */
public class PUFilter extends BaseFilter {
    private static final Logger LOGGER = Grizzly.logger(PUFilter.class);

    private final ArraySet<PUProtocol> protocols =
            new ArraySet<PUProtocol>(PUProtocol.class);
    
    final Attribute<PUContext> puContextAttribute;

    private final boolean isCloseUnrecognizedConnection;
    
    public PUFilter() {
        this(true);
    }

    /**
     * Constructs PUFilter.
     * 
     * @param isCloseUnrecognizedConnection <tt>true</tt> if a {@link Connection}
     * whose protocol is not recognized will be automatically closed,
     * or <tt>false</tt> if normal {@link FilterChain} processing will be
     * continued for such {@link Connection}, so a {@link Filter} next to
     * <tt>PUFilter</tt> may implement custom logic to process unrecognized connection.
     */    
    public PUFilter(final boolean isCloseUnrecognizedConnection) {
        this.isCloseUnrecognizedConnection = isCloseUnrecognizedConnection;
        puContextAttribute = Attribute.create(
                        PUFilter.class.getName() + '-' + hashCode() + ".puContext");
    }

    /**
     * Registers new {@link ProtocolFinder} - {@link FilterChain} pair, which
     * defines the protocol.
     * 
     * @param protocolFinder {@link ProtocolFinder}
     * @param filterChain {@link FilterChain}
     * @return {@link PUProtocol}, which wraps passed {@link ProtocolFinder} and {@link FilterChain}.
     */
    public PUProtocol register(final ProtocolFinder protocolFinder,
            final FilterChain filterChain) {

        final PUProtocol puProtocol = new PUProtocol(protocolFinder, filterChain);
        register(puProtocol);

        return puProtocol;
    }

    /**
     * Registers new {@link PUProtocol}.
     *
     * @param puProtocol {@link PUProtocol}
     */
    public void register(final PUProtocol puProtocol) {
        protocols.add(puProtocol);
    }

    /**
     * Deregisters the {@link PUProtocol}.
     *
     * @param puProtocol {@link PUProtocol}
     */
    public void deregister(final PUProtocol puProtocol) {
        protocols.remove(puProtocol);
    }

    /**
     * Get registered port unification protocols - {@link PUProtocol}s.
     *
     * @return {@link PUProtocol} {@link Set}.
     */
    public Set<PUProtocol> getProtocols() {
        return protocols;
    }

    /**
     * Returns the {@link FilterChainBuilder}, developers may use to build there
     * custom protocol filter chain.
     * The returned {@link FilterChainBuilder} may have some {@link Filter}s pre-added.
     *
     * @return {@link FilterChainBuilder}.
     */
    public FilterChainBuilder getPUFilterChainBuilder() {
        return FilterChainBuilder.newInstance();
    }

    /**
     * Returns <tt>true</tt> if a {@link Connection} whose protocol is not recognized
     * will be automatically closed, or <tt>false</tt> if normal
     * {@link FilterChain} processing will be continued for such {@link Connection},
     * so a {@link Filter} next to <tt>PUFilter</tt> may implement custom logic
     * to process unrecognized connection.
     */
    public boolean isCloseUnrecognizedConnection() {
        return isCloseUnrecognizedConnection;
    }
    
    @Override
    public NextAction handleRead(final FilterChainContext ctx) throws IOException {
        final Connection connection = ctx.getConnection();
        PUContext puContext = puContextAttribute.get(connection);
        if (puContext == null) {
            puContext = new PUContext(this);
            puContextAttribute.set(connection, puContext);
        } else if (puContext.noProtocolsFound()) {
            return ctx.getInvokeAction();
        }

        PUProtocol protocol = puContext.protocol;

        if (protocol == null) {
            // try to find appropriate protocol
            findProtocol(puContext, ctx);
            protocol = puContext.protocol;
        }
        
        if (protocol != null) {
            final NextAction suspendAction = ctx.getSuspendAction();
            ctx.suspend();

            final FilterChain completeProtocolChain = 
                    buildCompleteProtocolChain(protocol, ctx);

            connection.setFilterChain(completeProtocolChain);
            
            final FilterChainContext completeChainContext =
                    obtainProtocolChainContext(puContext, ctx,
                    completeProtocolChain);
            
            puContext.reset();
            
            ProcessorExecutor.execute(completeChainContext.getInternalContext());

            return suspendAction;
        }

        // no matching protocols within the set of known protocols were found,
        // pass the message to the next filter in the chain
        if (puContext.noProtocolsFound()) {
            if (isCloseUnrecognizedConnection) {
                connection.closeSilently();
                return ctx.getStopAction();
            }
            
            return ctx.getInvokeAction();
        }

        // one or more protocols need more data, shutdownNow processing until
        // it becomes available to check again
        return ctx.getStopAction(ctx.getMessage());
    }

    private FilterChain buildCompleteProtocolChain(
            final PUProtocol protocol,
            final FilterChainContext ctx) {
        final FilterChain nestedFilterChain = protocol.getFilterChain();
        
        final FilterChain newFilterChain = ctx.getFilterChain().copy();

        assert !newFilterChain.isEmpty();
        
        // Copy the current FilterChain filters up to the current one
        // Make sure the copied Filters share the state with the original ones
        final String lastFilterNameToKeep = ctx.getFilterReg().prev().name();
        newFilterChain.removeAllAfter(lastFilterNameToKeep);

        // Append sub-protocol Filters
        if (!nestedFilterChain.isEmpty()) {
            FilterReg curFilterReg = nestedFilterChain.firstFilterReg();
            do {
                newFilterChain.addLast(curFilterReg.filter());
                curFilterReg = curFilterReg.next();
            } while (curFilterReg != null);
        }
        
        return newFilterChain;
    }
    
    private FilterChainContext obtainProtocolChainContext(
            final PUContext puContext,
            final FilterChainContext ctx,
            final FilterChain completeProtocolFilterChain) {

        // find the last filter we copied from the master PU FilterChain into the complete FilterChain for this sub-protocol
        final String prevName = ctx.getFilterReg().prev().name();
        
        // find the correspondent FilterReg in the complete sub-protocol FilterChain to continue processing from
        final FilterReg curReg =
                completeProtocolFilterChain.getFilterReg(prevName).next();
        
        final FilterChainContext newFilterChainContext =
                completeProtocolFilterChain.obtainFilterChainContext(
                ctx.getConnection(),
                completeProtocolFilterChain.firstFilterReg(),
                null,
                curReg);
        
        newFilterChainContext.setAddressHolder(ctx.getAddressHolder());
        newFilterChainContext.setMessage(ctx.getMessage());
        newFilterChainContext.getInternalContext().setEvent(
                IOEvent.READ,
                new InternalProcessingHandler(ctx, puContext.isSticky()));

        return newFilterChainContext;
    }

    protected void findProtocol(final PUContext puContext,
                                final FilterChainContext ctx) {
        final PUProtocol[] protocolArray = protocols.getArray();

        for (int i = 0; i < protocolArray.length; i++) {
            final PUProtocol protocol = protocolArray[i];
            if ((puContext.skippedProtocolFinders & 1 << i) != 0) {
                continue;
            }
            try {
                final ProtocolFinder.Result result =
                        protocol.getProtocolFinder().find(puContext, ctx);

                switch (result) {
                    case FOUND:
                        puContext.protocol = protocol;
                        return;
                    case NOT_FOUND:
                        puContext.skippedProtocolFinders ^= 1 << i;
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING,
                        "ProtocolFinder " + protocol.getProtocolFinder() +
                        " reported error", e);
            }
        }
    }

    private class InternalProcessingHandler extends EventProcessingHandler.Adapter {
        private final FilterChainContext parentContext;
        private final boolean isSticky;
        
        private InternalProcessingHandler(final FilterChainContext parentContext,
                final boolean isSticky) {
            this.parentContext = parentContext;
            this.isSticky = isSticky;
        }
        
        @Override
        public void onComplete(final Context context) throws IOException {
            if (!isSticky) {
                parentContext.getConnection().setFilterChain(
                        parentContext.getFilterChain());
            }
            parentContext.resume(parentContext.getStopAction());
        }
    }
}
