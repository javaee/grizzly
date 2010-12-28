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
import java.util.HashSet;
import java.util.Set;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Context;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.IOEvent;
import org.glassfish.grizzly.PostProcessor;
import org.glassfish.grizzly.ProcessorExecutor;
import org.glassfish.grizzly.ProcessorResult.Status;
import org.glassfish.grizzly.attributes.Attribute;
import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.FilterChain;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;

/**
 *
 * @author oleksiys
 */
public class PUFilter extends BaseFilter {
    private Set<PUProtocol> protocols = new HashSet<PUProtocol>();

    private final Attribute<PUProtocol> stickyProtocolAttribute;
    public PUFilter() {
        stickyProtocolAttribute =
                Grizzly.DEFAULT_ATTRIBUTE_BUILDER.createAttribute(
                PUFilter.class.getName() + ".stickyProtocol");
    }

    public PUProtocol register(final ProtocolFinder protocolFinder,
            final FilterChain filterChain) {
        
        final PUProtocol puProtocol = new PUProtocol(protocolFinder, filterChain);
        protocols.add(puProtocol);
        return puProtocol;
    }

    public void register(final PUProtocol puProtocol) {
        protocols.add(puProtocol);
    }

    public void deregister(final PUProtocol puProtocol) {
        protocols.remove(puProtocol);
    }
    
    @Override
    public NextAction handleRead(final FilterChainContext ctx) throws IOException {
        final Connection connection = ctx.getConnection();
        PUProtocol protocol = stickyProtocolAttribute.get(connection);
        if (protocol == null) {
            
        }

        if (protocol != null) {
            final FilterChain filterChain = protocol.getFilterChain();
            ProcessorExecutor.execute(connection, IOEvent.READ, filterChain, new PostProcessor() {

                @Override
                public void process(final Context context,
                        final Status status) throws IOException {

                }
            });

            return ctx.getSuspendAction();
        }

        return ctx.getStopAction(ctx.getMessage());
    }

    @Override
    public NextAction handleWrite(final FilterChainContext ctx) throws IOException {
        return super.handleWrite(ctx);
    }

    @Override
    public NextAction handleEvent(final FilterChainContext ctx, Object event) throws IOException {
        return super.handleEvent(ctx, event);
    }

    @Override
    public NextAction handleClose(FilterChainContext ctx) throws IOException {
        return super.handleClose(ctx);
    }
}
