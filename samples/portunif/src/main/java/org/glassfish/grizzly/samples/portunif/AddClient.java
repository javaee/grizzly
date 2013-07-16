/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011-2013 Oracle and/or its affiliates. All rights reserved.
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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.GrizzlyFuture;
import org.glassfish.grizzly.WriteResult;
import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.FilterChainBuilder;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.filterchain.TransportFilter;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import org.glassfish.grizzly.nio.transport.TCPNIOTransportBuilder;
import org.glassfish.grizzly.samples.portunif.addservice.AddClientMessageFilter;
import org.glassfish.grizzly.samples.portunif.addservice.AddRequestMessage;
import org.glassfish.grizzly.samples.portunif.addservice.AddResponseMessage;
import org.glassfish.grizzly.utils.Charsets;

/**
 * Client app, which tests deployed ADD-service.
 * 
 * @author Alexey Stashok
 */
@SuppressWarnings("unchecked")
public class AddClient {
    private static final Logger LOGGER = Grizzly.logger(PUServer.class);

    public static void main(String[] args) throws Exception {
        Connection connection = null;

        // Construct the client filter chain
        final FilterChainBuilder puFilterChainBuilder = FilterChainBuilder.stateless()
                // Add TransportFilter
                .add(new TransportFilter())
                // Add ADD-service message parser/serializer
                .add(new AddClientMessageFilter())
                // Add Result reporter Filter
                .add(new ResultFilter());

        // Construct TCPNIOTransport
        final TCPNIOTransport transport = TCPNIOTransportBuilder.newInstance().build();
        transport.setProcessor(puFilterChainBuilder.build());

        try {
            // Start
            transport.start();

            // Create the client connection
            final Future<Connection> connectFuture =
                    transport.connect("localhost", PUServer.PORT);
            connection = connectFuture.get(10, TimeUnit.SECONDS);

            LOGGER.info("Enter 2 numbers separated by space (<value1> <value2>) end press <enter>.");
            LOGGER.info("Type q and enter to exit.");

            // Read user input and communicate the ADD-service
            String line;
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    System.in, Charsets.ASCII_CHARSET));
            while ((line = reader.readLine()) != null) {
                if ("q".equals(line)) {
                    break;
                }

                // Parse user input
                final int value1;
                final int value2;
                try {
                    final String[] values = line.split(" ");

                    value1 = Integer.parseInt(values[0].trim());
                    value2 = Integer.parseInt(values[1].trim());
                } catch (Exception e) {
                    LOGGER.warning("Bad format, repeat pls");
                    continue;
                }


                // send the request to ADD-service
                final GrizzlyFuture<WriteResult> writeFuture =
                        connection.write(new AddRequestMessage(value1, value2));

                final WriteResult result = writeFuture.get(10, TimeUnit.SECONDS);
                assert result != null;
            }
            
        } finally {
            // Close the client connection
            if (connection != null) {
                connection.closeSilently();
            }
            
            // Shutdown the transport
            transport.shutdownNow();
        }
    }

    // Simple reporting Filter
    private static final class ResultFilter extends BaseFilter {

        @Override
        public NextAction handleRead(FilterChainContext ctx) throws IOException {
            // Take ADD-service response
            final AddResponseMessage addResponseMessage = ctx.getMessage();

            // do output
            LOGGER.log(Level.INFO, "Result={0}", addResponseMessage.getResult());

            return ctx.getStopAction();
        }
    }
}
