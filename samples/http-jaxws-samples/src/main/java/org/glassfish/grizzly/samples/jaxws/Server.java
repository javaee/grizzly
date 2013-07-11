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
package org.glassfish.grizzly.samples.jaxws;

import org.glassfish.grizzly.samples.jaxws.service.AddService;
import java.io.IOException;
import org.glassfish.grizzly.http.server.HttpHandler;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.grizzly.http.server.NetworkListener;
import org.glassfish.grizzly.jaxws.JaxwsHandler;

/**
 * Basic Grizzly JAX-WS {@link HttpHandler} sample.
 * 
 * @author Alexey Stashok
 */
public class Server {
    public static final String WEB_SERVICE_CONTEXT_ROOT = "/add";
    private static final int PORT = 19881;
    
    public static void main(String[] args) throws IOException {
        
        // Create jax-ws HttpHandler and passing web service instance.
        final HttpHandler jaxwsHandler = new JaxwsHandler(new AddService());
        
        // Standard Grizzly HttpServer initialization
        final HttpServer httpServer = new HttpServer();
        NetworkListener networkListener = new NetworkListener("jaxws-listener", "0.0.0.0", PORT);
        
        httpServer.getServerConfiguration().addHttpHandler(jaxwsHandler, WEB_SERVICE_CONTEXT_ROOT);
        httpServer.addListener(networkListener);
        
        // Start server
        httpServer.start();
        
        try {
            System.out.println("WSDL is available at http://localhost:" + PORT + WEB_SERVICE_CONTEXT_ROOT + "?wsdl");
            System.out.println("Press enter to stop the server...");
            System.in.read();
        } finally {
            httpServer.shutdownNow();
        }
        
    }
}
