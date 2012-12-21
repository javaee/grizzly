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

package org.glassfish.grizzly.nio;

import java.io.IOException;
import java.nio.channels.SelectableChannel;
import java.util.concurrent.atomic.AtomicInteger;
import org.glassfish.grizzly.CompletionHandler;

/**
 * RoundRobin NIOConnectionDistributor implementation,
 * which allocates one SelectorRunner for OP_ACCEPT events and other
 * event will be assign to a next SelectorRunner from the array.
 * 
 * @author Alexey Stashok
 */
public final class RoundRobinConnectionDistributor
        extends AbstractNIOConnectionDistributor {
    private final AtomicInteger counter;
    private final Iterator it;
    
    public RoundRobinConnectionDistributor(final NIOTransport transport) {
        this(transport, false);
    }

    public RoundRobinConnectionDistributor(final NIOTransport transport,
            final boolean useDedicatedAcceptor) {
        super(transport);
        counter = new AtomicInteger();
        this.it = useDedicatedAcceptor ? new DedicatedIterator() :
                new SharedIterator();
        
    }
    @Override
    public void registerChannel(final SelectableChannel channel,
            final int interestOps, final Object attachment) throws IOException {
        transport.getSelectorHandler().registerChannel(it.next(), 
                channel, interestOps, attachment);
    }

    @Override
    public void registerChannelAsync(
            final SelectableChannel channel, final int interestOps,
            final Object attachment,
            final CompletionHandler<RegisterChannelResult> completionHandler) {
        transport.getSelectorHandler().registerChannelAsync(
                it.next(), channel, interestOps, attachment, completionHandler);
    }

    @Override
    public void registerServiceChannelAsync(
            final SelectableChannel channel, final int interestOps,
            final Object attachment,
            final CompletionHandler<RegisterChannelResult> completionHandler) {
        
        transport.getSelectorHandler().registerChannelAsync(
                it.nextService(), channel, interestOps,
                attachment, completionHandler);
    }
    
    private interface Iterator {
        SelectorRunner next();
        SelectorRunner nextService();
    }
    
    private final class DedicatedIterator implements Iterator {
        @Override
        public SelectorRunner next() {
            final SelectorRunner[] runners = getTransportSelectorRunners();
            if (runners.length == 1) {
                return runners[0];
            }
            
            return runners[((counter.getAndIncrement() & 0x7fffffff) % (runners.length - 1)) + 1];
        }

        @Override
        public SelectorRunner nextService() {
            return getTransportSelectorRunners()[0];
        }
    }

    private final class SharedIterator implements Iterator {
        @Override
        public SelectorRunner next() {
            final SelectorRunner[] runners = getTransportSelectorRunners();
            if (runners.length == 1) {
                return runners[0];
            }
            
            return runners[(counter.getAndIncrement() & 0x7fffffff) % runners.length];
        }

        @Override
        public SelectorRunner nextService() {
            return next();
        }
    }    
}
