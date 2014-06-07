/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2014 Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.filterchain.FilterChainBuilder;
import org.glassfish.grizzly.filterchain.TransportFilter;
import org.glassfish.grizzly.http.server.AddOn;
import org.glassfish.grizzly.http.server.HttpHandler;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.grizzly.http.server.NetworkListener;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.http.server.Response;
import org.glassfish.grizzly.portunif.PUFilter;
import org.glassfish.grizzly.portunif.PUProtocol;

import static org.glassfish.grizzly.samples.portunif.PUServer.*;

/**
 * Port-unification sample, which hosts "HTTP", "add" and "sub"
 * services on the same port.
 * 
 * Sample creates a protocol tree:
 *
 *                                   TransportFilter
 *                                          |
 *                                       PUFilter
 *                                          |
 *               -------------------------------------------------------
 *               |                          |                          |
 *     AddServerMessageFilter     SubServerMessageFilter        HttpCodecFilter
 *               |                          |                          |
 *        AddServiceFilter           SubServiceFilter          HttpServerFilter
 *
 *
 * @author Alexey Stashok
 */
public class HttpPUServer {
    public static void main(String[] args) throws IOException {
        // Create regular HttpServer
        final HttpServer httpServer = new HttpServer();
        
        final NetworkListener networkListener = new NetworkListener(
                "pu-http-server", "0.0.0.0", PORT);
        
        // Register port unification AddOn
        networkListener.registerAddOn(new PortUnificationAddOn());
        
        // Finish the server initialization
        httpServer.addListener(networkListener);
        
        httpServer.getServerConfiguration().addHttpHandler(new HttpHandler() {
            @Override
            public void service(Request request, Response response) throws Exception {
                response.getWriter().write("Hello world from HTTP!");
            }
        });
        
        httpServer.start();
        
        try {
            Grizzly.logger(HttpPUServer.class).info("Server is ready...\n"
                    + "You can test it using AddClient, SubClient applications or web browser\n"
                    + "Press enter to exit.");
            
            System.in.read();
        } finally {
            httpServer.shutdownNow();
        }
    }
    
    public static class PortUnificationAddOn implements AddOn {

        @Override
        public void setup(final NetworkListener networkListener,
                final FilterChainBuilder builder) {
            // Create PUFilter.
            // We will try to filter off "add" and "sub" protocols, if they
            // are not recognize - assume it's HTTP.
            // So we don't need to register HTTP ProtocolFinder, but have to
            // pass <tt>false</tt> to the constructor to not let "unrecognized"
            // connections to be closed (because they might be HTTP).
            final PUFilter puFilter = new PUFilter(false);

            // Configure add-service PUProtocol
            final PUProtocol addProtocol = configureAddProtocol(puFilter);
            // Configure sub-service PUProtocol
            final PUProtocol subProtocol = configureSubProtocol(puFilter);

            // Register add-service pu protocol
            puFilter.register(addProtocol);
            // Register sub-service pu protocol
            puFilter.register(subProtocol);

            // now find the place to insert PUFilter in the HTTP FilterChainBuilder.
            // we'll insert the PUFilter right next to the TransportFilter
            final int transportFilterIdx =
                    builder.indexOfType(TransportFilter.class);
            
            assert transportFilterIdx != -1;
            
            builder.add(transportFilterIdx + 1, puFilter);
        }

    }
}
