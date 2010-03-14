/*
 *
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2007-2010 Sun Microsystems, Inc. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License. You can obtain
 * a copy of the License at https://glassfish.dev.java.net/public/CDDL+GPL.html
 * or glassfish/bootstrap/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/bootstrap/legal/LICENSE.txt.
 * Sun designates this particular file as subject to the "Classpath" exception
 * as provided by Sun in the GPL Version 2 section of the License file that
 * accompanied this code.  If applicable, add the following below the License
 * Header, with the fields enclosed by brackets [] replaced by your own
 * identifying information: "Portions Copyrighted [year]
 * [name of copyright owner]"
 *
 * Contributor(s):
 *
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
 *
 */

package com.sun.grizzly.samples.http.download;

import com.sun.grizzly.Connection;
import com.sun.grizzly.Grizzly;
import com.sun.grizzly.TransportFactory;
import com.sun.grizzly.filterchain.FilterChainBuilder;
import com.sun.grizzly.filterchain.TransportFilter;
import com.sun.grizzly.http.HttpClientFilter;
import com.sun.grizzly.impl.FutureImpl;
import com.sun.grizzly.nio.transport.TCPNIOTransport;
import com.sun.grizzly.utils.IdleTimeoutFilter;
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

//        String url = "http://www.google.com";
        
        // Parse passed URL
        final URI uri = new URI(url);
        final String scheme = uri.getScheme();
        final String host = uri.getHost();
        final int port = uri.getPort() > 0 ? uri.getPort() : 80;
        
        final FutureImpl<String> completeFuture = FutureImpl.create();

        // Build HTTP client filter chain
        FilterChainBuilder clientFilterChainBuilder = FilterChainBuilder.stateless();
        // Add transport filter
        clientFilterChainBuilder.add(new TransportFilter());
        // Add IdleTimeoutFilter, which will close connetions, which stay
        // idle longer than 10 seconds.
        clientFilterChainBuilder.add(new IdleTimeoutFilter(10, TimeUnit.SECONDS));
        // Add HttpClientFilter, which transforms Buffer <-> HttpContent
        clientFilterChainBuilder.add(new HttpClientFilter());
        // Add HTTP client download filter, which is responsible for downloading
        // HTTP resource asynchronously
        clientFilterChainBuilder.add(new ClientDownloadFilter(uri, completeFuture));

        // Initialize Transport
        TCPNIOTransport transport = TransportFactory.getInstance().createTCPTransport();
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
                    connection.close();
                }
            }
        } finally {
            logger.info("Stopping transport...");
            // stop the transport
            transport.stop();

            // release TransportManager resources like ThreadPool
            TransportFactory.getInstance().close();
            logger.info("Stopped transport...");
        }
    }
}
