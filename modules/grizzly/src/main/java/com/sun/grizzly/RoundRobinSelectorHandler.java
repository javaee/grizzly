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

package com.sun.grizzly;

import com.sun.grizzly.Controller.Protocol;
import com.sun.grizzly.util.Copyable;
import java.io.IOException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.Set;

/**
 * A SelectorHandler handles all java.nio.channels.Selector operations.
 * One or more instance of a Selector are handled by SelectorHandler.
 * The logic for processing of SelectionKey interest (OP_ACCEPT,OP_READ, etc.)
 * is usually defined using an instance of SelectorHandler.
 *
 * This class represents a TCP implementation of a SelectorHandler,
 * which handles "accept" events by registering newly accepted connections
 * to auxiliary {@link Controller}s in a round robin fashion.
 *
 * @author Alexey Stashok
 */
public class RoundRobinSelectorHandler extends TCPSelectorHandler
        implements ComplexSelectorHandler {
    private ReadController[] rrControllers;
    private AtomicInteger roundRobinCounter = new AtomicInteger();
    private Set<Protocol> customProtocols = new CopyOnWriteArraySet<Protocol>();

    public RoundRobinSelectorHandler() {}
    
    public RoundRobinSelectorHandler(ReadController[] rrControllers) {
        this.rrControllers = rrControllers;
    }
    
    @Override
    public void copyTo(Copyable copy) {
        super.copyTo(copy);
        RoundRobinSelectorHandler copyHandler = (RoundRobinSelectorHandler) copy;
        copyHandler.roundRobinCounter = roundRobinCounter;
        copyHandler.rrControllers = rrControllers;
    }
    
    @Override
    public boolean onAcceptInterest(SelectionKey key, Context context) throws IOException {
        ReadController auxController = nextController();
        SelectorHandler protocolSelectorHandler = context.getSelectorHandler();
        SelectableChannel channel = protocolSelectorHandler.acceptWithoutRegistration(key);

        if (channel != null) {
            protocolSelectorHandler.configureChannel(channel);
            SelectorHandler relativeSelectorHandler =
                    auxController.getSelectorHandlerClone(protocolSelectorHandler);

            if (relativeSelectorHandler == null) {
                // Clone was not found - take correspondent protocol SelectorHandler
                relativeSelectorHandler =
                        auxController.getSelectorHandler(protocolSelectorHandler.protocol());

                if (relativeSelectorHandler == null) {
                    throw new IOException("Can not get correct SelectorHandler");
                }
            }

            auxController.addChannel(channel, relativeSelectorHandler);
        }
        return false;
    }

    /**
     * Add custom protocol support
     * @param customProtocol custom {@link Controller.Protocol}
     */
    public void addProtocolSupport(Protocol customProtocol) {
        customProtocols.add(customProtocol);
    }

    /**
     * {@inheritDoc}
     */
    public boolean supportsProtocol(Protocol protocol) {
        return protocol == Protocol.TCP || protocol == Protocol.TLS ||
                customProtocols.contains(protocol);
    }

    /**
     * {@inheritDoc}
     */
    public boolean supportsClient( SelectorHandler selectorHandler ) {
        if( selectorHandler == null )
            return false;
        if( selectorHandler instanceof ReusableTCPSelectorHandler ||
            selectorHandler instanceof ReusableUDPSelectorHandler )
            return false;
        Protocol protocol = selectorHandler.protocol();
        return protocol == Protocol.TCP || protocol == Protocol.TLS ||
               protocol == Protocol.UDP || customProtocols.contains( protocol );
    }

    /**
     * {@inheritDoc}
     */
    public ReadController nextController() {
        return rrControllers[(roundRobinCounter.incrementAndGet() & 0x7fffffff) % rrControllers.length];
    }

    @Override
    public void shutdown() {
        super.shutdown();
        customProtocols.clear();
    }
}
