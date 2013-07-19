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

package org.glassfish.grizzly.samples.http.download;

import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.filterchain.FilterChainBuilder;
import org.glassfish.grizzly.filterchain.TransportFilter;
import org.glassfish.grizzly.http.HttpServerFilter;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import org.glassfish.grizzly.nio.transport.TCPNIOTransportBuilder;
import org.glassfish.grizzly.utils.DelayedExecutor;
import org.glassfish.grizzly.utils.IdleTimeoutFilter;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Simple HTTP (Web) server, which listens on a specific TCP port and shares
 * static resources (files), located in a passed folder.
 * 
 * @author Alexey Stashok
 */
public class Server {
    private static final Logger logger = Grizzly.logger(Server.class);

    // TCP Host
    public static final String HOST = "localhost";
    // TCP port
    public static final int PORT = 7777;

    public static void main(String[] args) throws IOException {

        // lifecycle of the executor used by the IdleTimeoutFilter must be explicitly
        // managed
        final DelayedExecutor timeoutExecutor = IdleTimeoutFilter.createDefaultIdleDelayedExecutor();
        timeoutExecutor.start();

        // Construct filter chain
        FilterChainBuilder serverFilterChainBuilder = FilterChainBuilder.stateless();
        // Add transport filter
        serverFilterChainBuilder.add(new TransportFilter());
        // Add IdleTimeoutFilter, which will close connetions, which stay
        // idle longer than 10 seconds.
        serverFilterChainBuilder.add(new IdleTimeoutFilter(timeoutExecutor, 10, TimeUnit.SECONDS));
        // Add HttpServerFilter, which transforms Buffer <-> HttpContent
        serverFilterChainBuilder.add(new HttpServerFilter());
        // Simple server implementation, which locates a resource in a local file system
        // and transfers it via HTTP
        serverFilterChainBuilder.add(new WebServerFilter("."));

        // Initialize Transport
        final TCPNIOTransport transport =
                TCPNIOTransportBuilder.newInstance().build();
        // Set filterchain as a Transport Processor
        transport.setProcessor(serverFilterChainBuilder.build());

        try {
            // binding transport to start listen on certain host and port
            transport.bind(HOST, PORT);

            // start the transport
            transport.start();

            logger.info("Press any key to stop the server...");
            System.in.read();
        } finally {
            logger.info("Stopping transport...");
            // stop the transport
            transport.shutdownNow();
            timeoutExecutor.stop();
            timeoutExecutor.destroy();
            logger.info("Stopped transport...");
        }
    }
}
