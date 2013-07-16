/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2009-2013 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.samples.strategy;

import java.io.IOException;

import org.glassfish.grizzly.filterchain.FilterChainBuilder;
import org.glassfish.grizzly.filterchain.TransportFilter;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import org.glassfish.grizzly.nio.transport.TCPNIOTransportBuilder;
import org.glassfish.grizzly.samples.echo.EchoFilter;
import org.glassfish.grizzly.strategies.LeaderFollowerNIOStrategy;

/**
 * Sample shows how easy custom {@link org.glassfish.grizzly.IOStrategy} could be applied for a
 * {@link org.glassfish.grizzly.Transport}. In this example we use
 * {@link org.glassfish.grizzly.strategies.LeaderFollowerNIOStrategy} for processing all I/O events occurring on
 * {@link org.glassfish.grizzly.Connection}.
 *
 * To test this echo server you can use {@link org.glassfish.grizzly.samples.echo.EchoClient}.
 *
 * @see org.glassfish.grizzly.IOStrategy
 * @see org.glassfish.grizzly.strategies.LeaderFollowerNIOStrategy
 * @see org.glassfish.grizzly.strategies.SameThreadIOStrategy
 * @see org.glassfish.grizzly.strategies.WorkerThreadIOStrategy
 * @see org.glassfish.grizzly.strategies.SimpleDynamicNIOStrategy
 * 
 * @author Alexey Stashok
 */
public class CustomStrategy {
    public static final String HOST = "localhost";
    public static final int PORT = 7777;

    public static void main(String[] args) throws IOException {
        // Create a FilterChain using FilterChainBuilder
        FilterChainBuilder filterChainBuilder = FilterChainBuilder.stateless();
        // Add TransportFilter, which is responsible
        // for reading and writing data to the connection
        filterChainBuilder.add(new TransportFilter());
        filterChainBuilder.add(new EchoFilter());


        // Create TCP transport
        final TCPNIOTransport transport =
                TCPNIOTransportBuilder.newInstance().build();
        transport.setProcessor(filterChainBuilder.build());
        
        // Set the LeaderFollowerIOStrategy (any strategy could be applied this way)
        transport.setIOStrategy(LeaderFollowerNIOStrategy.getInstance());
        
        try {
            // binding transport to start listen on certain host and port
            transport.bind(HOST, PORT);

            // start the transport
            transport.start();

            System.out.println("Press any key to stop the server...");
            System.in.read();
        } finally {
            System.out.println("Stopping transport...");
            // stop the transport
            transport.shutdownNow();

            System.out.println("Stopped transport...");
        }
    }
}
