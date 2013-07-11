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

package org.glassfish.grizzly.samples.ajp;

import java.io.IOException;
import java.io.Writer;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.http.ajp.AjpAddOn;
import org.glassfish.grizzly.http.server.HttpHandler;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.grizzly.http.server.NetworkListener;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.http.server.Response;
import org.glassfish.grizzly.http.server.ServerConfiguration;

/**
 * Sample demonstrates how custom {@link HttpHandler}, rigistered on
 * Grizzly {@link HttpServer} may transparently process both HTTP
 * and AJP requests.
 *
 * (please check the readme.txt for apache configuration instructions).
 * 
 * @author Alexey Stashok
 */
public class AjpHelloWorld {
    private static final Logger LOGGER = Grizzly.logger(AjpHelloWorld.class);


    public static void main(String[] args) {

        // create a HTTP server instance
        final HttpServer server = new HttpServer();

        // Create plain HTTP listener, which will handle port 8080
        final NetworkListener httpNetworkListener =
                new NetworkListener("http-listener", "0.0.0.0", 8080);

        // Create AJP listener, which will handle port 8009
        final NetworkListener ajpNetworkListener =
                new NetworkListener("ajp-listener", "0.0.0.0", 8009);
        // Register AJP addon on HttpServer's listener
        ajpNetworkListener.registerAddOn(new AjpAddOn());

        server.addListener(httpNetworkListener);
        server.addListener(ajpNetworkListener);

        final ServerConfiguration config = server.getServerConfiguration();

        // Map the path, /grizzly, to the HelloWorldHandler
        config.addHttpHandler(new HelloWorldHandler(), "/grizzly");

        try {
            // start the server
            server.start();

            // So now we're listening on 2 ports:
            // 8080 : for HTTP requests
            // 8009 : for AJP
            // both ports redirect requests to HelloWorldHandler

            System.out.println("Press enter to stop...");
            System.in.read();
        } catch (IOException ioe) {
            LOGGER.log(Level.SEVERE, ioe.toString(), ioe);
        } finally {
            server.shutdownNow();
        }
    }

    // Simple "Hello World" {@link HttpHandler}.
    public static class HelloWorldHandler extends HttpHandler {

        @Override
        public void service(final Request request, final Response response)
                throws Exception {
            // Here we don't care if it's AJP or HTTP originated request
            // everything is transparent
            final Writer writer = response.getWriter();
            writer.write("Hello world!");
        }

    }
}
