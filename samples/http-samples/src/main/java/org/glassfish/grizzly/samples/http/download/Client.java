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

import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.filterchain.FilterChainBuilder;
import org.glassfish.grizzly.filterchain.TransportFilter;
import org.glassfish.grizzly.http.HttpClientFilter;
import org.glassfish.grizzly.impl.FutureImpl;
import org.glassfish.grizzly.impl.SafeFutureImpl;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import org.glassfish.grizzly.nio.transport.TCPNIOTransportBuilder;
import org.glassfish.grizzly.utils.DelayedExecutor;
import org.glassfish.grizzly.utils.IdleTimeoutFilter;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Simple asynchronous HTTP client implementation, which downloads HTTP resource
 * and saves its content in a local file.
 * 
 * @author Alexey Stashok
 */
public class Client {
    private static final Logger logger = Grizzly.logger(Client.class);
    
    public static void main(String[] args) throws IOException, URISyntaxException {
        // Check command line parameters
        if (args.length < 1) {
            System.out.println("To download the resource, please run: Client <url>");
            System.exit(0);
        }

        final String url = args[0];

        // Parse passed URL
        final URI uri = new URI(url);
        final String host = uri.getHost();
        final int port = uri.getPort() > 0 ? uri.getPort() : 80;

        // lifecycle of the executor used by the IdleTimeoutFilter must be explicitly
        // managed
        final DelayedExecutor timeoutExecutor = IdleTimeoutFilter.createDefaultIdleDelayedExecutor();
        timeoutExecutor.start();

        final FutureImpl<String> completeFuture = SafeFutureImpl.create();

        // Build HTTP client filter chain
        FilterChainBuilder clientFilterChainBuilder = FilterChainBuilder.stateless();
        // Add transport filter
        clientFilterChainBuilder.add(new TransportFilter());
        // Add IdleTimeoutFilter, which will close connections, which stay
        // idle longer than 10 seconds.
        clientFilterChainBuilder.add(new IdleTimeoutFilter(timeoutExecutor, 10, TimeUnit.SECONDS));
        // Add HttpClientFilter, which transforms Buffer <-> HttpContent
        clientFilterChainBuilder.add(new HttpClientFilter());
        // Add HTTP client download filter, which is responsible for downloading
        // HTTP resource asynchronously
        clientFilterChainBuilder.add(new ClientDownloadFilter(uri, completeFuture));

        // Initialize Transport
        final TCPNIOTransport transport =
                TCPNIOTransportBuilder.newInstance().build();
        // Set filterchain as a Transport Processor
        transport.setProcessor(clientFilterChainBuilder.build());

        try {
            // start the transport
            transport.start();

            Connection connection = null;
            
            // Connecting to a remote Web server
            Future<Connection> connectFuture = transport.connect(host, port);
            try {
                // Wait until the client connect operation will be completed
                // Once connection will be established - downloading will
                // start @ ClientDownloadFilter.onConnect(...)
                connection = connectFuture.get(10, TimeUnit.SECONDS);
                // Wait until download will be completed
                String filename = completeFuture.get();
                logger.log(Level.INFO, "File " + filename + " was successfully downloaded");
            } catch (Exception e) {
                if (connection == null) {
                    logger.log(Level.WARNING, "Can not connect to the target resource");
                } else {
                    logger.log(Level.WARNING, "Error downloading the resource");
                }
            } finally {
                // Close the client connection
                if (connection != null) {
                    connection.closeSilently();
                }
            }
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
