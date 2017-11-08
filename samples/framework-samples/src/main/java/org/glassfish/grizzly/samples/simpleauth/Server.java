/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2017 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://oss.oracle.com/licenses/CDDL+GPL-1.1
 * or LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at LICENSE.txt.
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

package org.glassfish.grizzly.samples.simpleauth;

import org.glassfish.grizzly.filterchain.FilterChainBuilder;
import org.glassfish.grizzly.filterchain.TransportFilter;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import org.glassfish.grizzly.nio.transport.TCPNIOTransportBuilder;
import org.glassfish.grizzly.samples.echo.EchoFilter;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Server implementation, which echoes message, only if client was authenticated :)
 * Client and server exchange String based messages:
 *
 * (1)
 * MultiLinePacket = command
 *                   *(parameter LF)
 *                   LF
 * parameter = TEXT (ASCII)
 *
 * Server filters are built in a following way:
 *
 * {@link TransportFilter} - reads/writes data from/to network
 * {@link MultiStringFilter} - translates Buffer <-> List&lt;String&gt;
 * {@link MultiLineFilter} - translates String <-> MultiLinePacket (see 1)
 * {@link ServerAuthFilter} - checks authentication header in an incoming packets.
 * {@link org.glassfish.grizzly.samples.echo.EchoFilter} - sends echo to a client.
 *
 * @author Alexey Stashok
 */
public class Server {
    private static final Logger logger = Logger.getLogger(Server.class.getName());

    public static final String HOST = "localhost";
    public static final int PORT = 7777;

    public static void main(String[] args) throws IOException {
        // Create a FilterChain using FilterChainBuilder
        FilterChainBuilder filterChainBuilder = FilterChainBuilder.stateless();
        // Add TransportFilter, which is responsible
        // for reading and writing data to the connection
        filterChainBuilder.add(new TransportFilter());
        // MultiStringFilter is responsible for parsing list of string lines
        filterChainBuilder.add(new MultiStringFilter(Charset.forName("ASCII"), "\n") {
            
            @Override
            protected List<String> createInList() {
                // overwrite createInList to return LinkedList instead of ArrayList
                return new LinkedList<String>();
            }
        });
        // MultiLineFilter is responsible for gathering parsed lines in a single multi line packet
        filterChainBuilder.add(new MultiLineFilter(""));
        // AuthFilter is responsible for a client authentication
        filterChainBuilder.add(new ServerAuthFilter());
        // EchoServer sends the client message back
        filterChainBuilder.add(new EchoFilter());

        // Create TCP transport
        final TCPNIOTransport transport =
                TCPNIOTransportBuilder.newInstance().build();
        transport.setProcessor(filterChainBuilder.build());

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

            logger.info("Stopped transport...");
        }
    }
}
