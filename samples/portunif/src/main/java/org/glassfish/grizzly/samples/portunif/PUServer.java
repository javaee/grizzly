/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011-2014 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.samples.portunif;

import org.glassfish.grizzly.samples.portunif.addservice.AddProtocolFinder;
import java.io.IOException;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.filterchain.FilterChain;
import org.glassfish.grizzly.filterchain.FilterChainBuilder;
import org.glassfish.grizzly.filterchain.TransportFilter;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import org.glassfish.grizzly.nio.transport.TCPNIOTransportBuilder;
import org.glassfish.grizzly.portunif.PUFilter;
import org.glassfish.grizzly.portunif.PUProtocol;
import org.glassfish.grizzly.portunif.ProtocolFinder;
import org.glassfish.grizzly.samples.portunif.addservice.AddServerMessageFilter;
import org.glassfish.grizzly.samples.portunif.addservice.AddServiceFilter;
import org.glassfish.grizzly.samples.portunif.subservice.SubProtocolFinder;
import org.glassfish.grizzly.samples.portunif.subservice.SubServerMessageFilter;
import org.glassfish.grizzly.samples.portunif.subservice.SubServiceFilter;

/**
 * Port-unification sample, which hosts "add" and "sub" services on the same port.
 * Sample creates a protocol tree:
 *
 *                      TransportFilter
 *                             |
 *                         PUFilter
 *                             |
 *               ----------------------------
 *               |                          |
 *     AddServerMessageFilter     SubServerMessageFilter
 *               |                          |
 *        AddServiceFilter           SubServiceFilter
 *
 *
 * @author Alexey Stashok
 */
public class PUServer {
    static final int PORT = 17400;

    public static void main(String[] args) throws IOException {
        // Create PUFilter
        final PUFilter puFilter = new PUFilter();

        // Configure add-service PUProtocol
        final PUProtocol addProtocol = configureAddProtocol(puFilter);
        // Configure sub-service PUProtocol
        final PUProtocol subProtocol = configureSubProtocol(puFilter);

        // Register add-service pu protocol
        puFilter.register(addProtocol);
        // Register sub-service pu protocol
        puFilter.register(subProtocol);
        
        // Construct the main filter chain
        final FilterChainBuilder puFilterChainBuilder = FilterChainBuilder.stateless()
                .add(new TransportFilter())
                .add(puFilter);

        // Build TCP transport
        final TCPNIOTransport transport = TCPNIOTransportBuilder.newInstance().build();
        transport.setProcessor(puFilterChainBuilder.build());

        try {
            // Bind to the server port
            transport.bind(PORT);
            // Start
            transport.start();

            Grizzly.logger(PUServer.class).info("Server is ready...\nPress enter to exit.");

            System.in.read();
        } finally {
            // Shutdown the TCP transport
            transport.shutdownNow();
        }
    }

    /**
     * Configure ADD-service {@link PUProtocol}.
     *
     * @param puFilter {@link PUFilter}
     * @return configured {@link PUProtocol}
     */
    static PUProtocol configureAddProtocol(final PUFilter puFilter) {
        // Create ADD-service ProtocolFinder
        final ProtocolFinder addProtocolFinder = new AddProtocolFinder();

        // Create ADD-service FilterChain
        final FilterChain addProtocolFilterChain =
                puFilter.getPUFilterChainBuilder()
                // Add ADD-service message parser/serializer
                .add(new AddServerMessageFilter())
                // Add ADD-service filter
                .add(new AddServiceFilter())
                .build();

        // Construct PUProtocol
        return new PUProtocol(addProtocolFinder, addProtocolFilterChain);
    }

    /**
     * Configure SUB-service {@link PUProtocol}.
     *
     * @param puFilter {@link PUFilter}.
     * @return configured {@link PUProtocol}
     */
    static PUProtocol configureSubProtocol(final PUFilter puFilter) {
        // Create SUB-service ProtocolFinder
        final ProtocolFinder subProtocolFinder = new SubProtocolFinder();

        // Create SUB-service FilterChain
        final FilterChain subProtocolFilterChain =
                puFilter.getPUFilterChainBuilder()
                // Add SUB-service message parser/serializer
                .add(new SubServerMessageFilter())
                // Add SUB-service filter
                .add(new SubServiceFilter())
                .build();

        // Construct PUProtocol
        return new PUProtocol(subProtocolFinder, subProtocolFilterChain);
    }

}
