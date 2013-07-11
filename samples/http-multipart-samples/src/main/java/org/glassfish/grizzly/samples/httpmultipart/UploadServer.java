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
package org.glassfish.grizzly.samples.httpmultipart;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.grizzly.http.server.NetworkListener;
import org.glassfish.grizzly.http.server.ServerConfiguration;

/**
 * HTTP upload server, which instantiates two Grizzly {@link org.glassfish.grizzly.http.server.HttpHandler}s:
 * {@link FormHttpHandler} on URL http://localhost:18080/,
 * {@link UploaderHttpHandler} on URIL http://localhost:18080/upload.
 * First one is responsible to serve simple HTML upload form and the second one
 * takes care of actual file/data uploading.
 *
 * @author Alexey Stashok
 */
public class UploadServer {
    private static final Logger LOGGER = Grizzly.logger(UploadServer.class);

    private static final int PORT = 18080;

    public static void main(String[] args) {
        // create a HttpServer
        final HttpServer server = new HttpServer();        
        final ServerConfiguration config = server.getServerConfiguration();

        // Map the path / to the FormHttpHandler
        config.addHttpHandler(new FormHttpHandler(), "/");
        // Map the path /upload to the UploaderHttpHandler
        config.addHttpHandler(new UploaderHttpHandler(), "/upload");

        // Create HTTP network listener on host "0.0.0.0" and port 18080.
        final NetworkListener listener = new NetworkListener("Grizzly",
                NetworkListener.DEFAULT_NETWORK_HOST, PORT);

        server.addListener(listener);

        try {
            // Start the server
            server.start();

            LOGGER.log(Level.INFO, "Server listens on port {0}", PORT);
            LOGGER.log(Level.INFO, "Press enter to exit");
            System.in.read();
        } catch (IOException ioe) {
            LOGGER.log(Level.SEVERE, ioe.toString(), ioe);
        } finally {
            // Stop the server
            server.shutdownNow();
        }
    }
}
