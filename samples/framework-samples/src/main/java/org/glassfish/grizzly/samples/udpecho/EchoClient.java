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

package org.glassfish.grizzly.samples.udpecho;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;
import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.FilterChainBuilder;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.filterchain.TransportFilter;
import org.glassfish.grizzly.impl.FutureImpl;
import org.glassfish.grizzly.impl.SafeFutureImpl;
import org.glassfish.grizzly.nio.transport.UDPNIOTransport;
import org.glassfish.grizzly.nio.transport.UDPNIOTransportBuilder;
import org.glassfish.grizzly.utils.StringFilter;

/**
 * The simple client, which sends a message to the echo server
 * and waits for response
 * @author Alexey Stashok
 */
public class EchoClient {
    private static final Logger logger = Logger.getLogger(EchoClient.class.getName());

    public static void main(String[] args) throws IOException,
            ExecutionException, InterruptedException, TimeoutException {

        final FutureImpl<Boolean> future = SafeFutureImpl.create();

        // Create a FilterChain using FilterChainBuilder
        FilterChainBuilder filterChainBuilder = FilterChainBuilder.stateless();
        // Add TransportFilter, which will be responsible for reading and
        // writing data to the connection
        filterChainBuilder.add(new TransportFilter());
        // Add string filter, which will transform Buffer <-> String
        filterChainBuilder.add(new StringFilter(Charset.forName("UTF-8")));
        // Add the client filter, responsible for the client logic
        filterChainBuilder.add(new ClientFilter("Echo test", future));

        // Create the UDP transport
        final UDPNIOTransport transport =
                UDPNIOTransportBuilder.newInstance().build();
        transport.setProcessor(filterChainBuilder.build());

        try {
            // start the transport
            transport.start();

            // perform async. connect to the server
            transport.connect(EchoServer.HOST,
                    EchoServer.PORT);
            // wait for connect operation to complete

            // check the result
            final boolean isEqual = future.get(10, TimeUnit.SECONDS);
            assert isEqual;
            logger.info("Echo came successfully");
        } finally {
            // stop the transport
            transport.shutdownNow();
        }
    }

    /**
     * ClientFilter, which sends a message, when UDP connection gets bound to the target address,
     * and checks the server echo.
     */
    static class ClientFilter extends BaseFilter {
        // initial message to be sent to the server
        private final String message;
        // the resulting future
        private final FutureImpl<Boolean> future;

        private ClientFilter(String message, FutureImpl<Boolean> future) {
            this.message = message;
            this.future = future;
        }

        /**
         * Method is called, when UDP connection is getting bound to the server address.
         * 
         * @param ctx the {@link FilterChainContext}.
         * @return
         * @throws IOException
         */
        @Override
        public NextAction handleConnect(final FilterChainContext ctx) throws IOException {
            // We have StringFilter down on the filterchain - so we can write String directly
            ctx.write(message);
            return ctx.getInvokeAction();
        }

        /**
         * Method is called, when UDP message came from the server.
         *
         * @param ctx the {@link FilterChainContext}.
         * @return
         * @throws IOException
         */
        @Override
        public NextAction handleRead(final FilterChainContext ctx) throws IOException {
            // We have StringFilter down on the filterchain - so we can get String directly
            final String messageFromServer = ctx.getMessage();

            // check the echo
            future.result(message.equals(messageFromServer));
            return ctx.getInvokeAction();
        }

    }
}
