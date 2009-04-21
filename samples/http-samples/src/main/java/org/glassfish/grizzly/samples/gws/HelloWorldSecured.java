/*
 *
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2007-2008 Sun Microsystems, Inc. All rights reserved.
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


package org.glassfish.grizzly.samples.gws;

import java.io.IOException;
import java.net.URL;
import org.glassfish.grizzly.ssl.SSLContextConfigurator;
import org.glassfish.grizzly.ssl.SSLEngineConfigurator;
import org.glassfish.grizzly.web.container.http11.GrizzlyAdapter;
import org.glassfish.grizzly.web.container.http11.GrizzlyRequest;
import org.glassfish.grizzly.web.container.http11.GrizzlyResponse;
import org.glassfish.grizzly.web.embed.GrizzlyWebServer;

/**
 * Simple example of using {@link GrizzlyWebServer} operating over HTTPS.
 *
 * @see GrizzlyWebServer
 * 
 * @author Alexey Stashok
 */
public class HelloWorldSecured {
    public static final int PORT = 7777;

    public static void main(String[] args) throws IOException {
        // Initialize GrizzlyWebServer
        GrizzlyWebServer gws = new GrizzlyWebServer(PORT, ".", true);
        // Provide GrizzlyWebServer with SSL configuration
        gws.setSSLConfiguration(initializeSSL());

        // Add HelloWorld GrizzlyAdapter
        gws.addGrizzlyAdapter(new HelloWorldAdapter(), new String[] {"/hello"});
        // Start Web server
        gws.start();

        // <-------------- RUNNING -------------->

        System.out.println("You can access adapter by: https://localhost:" +
                PORT + "/hello/");
        System.out.println("Press <ENTER> key to exit...");
        System.in.read();

        // Stop Web server
        gws.stop();
    }

    private static SSLEngineConfigurator initializeSSL() {
        // Initialize SSLContext configuration
        SSLContextConfigurator sslContextConfig = new SSLContextConfigurator();

        // Set key store
        ClassLoader cl = HelloWorldSecured.class.getClassLoader();
        URL cacertsUrl = cl.getResource("ssltest-cacerts.jks");
        if (cacertsUrl != null) {
            sslContextConfig.setTrustStoreFile(cacertsUrl.getFile());
        }

        // Set trust store
        URL keystoreUrl = cl.getResource("ssltest-keystore.jks");
        if (keystoreUrl != null) {
            sslContextConfig.setKeyStoreFile(keystoreUrl.getFile());
        }


        // Create SSLEngine configurator
        return new SSLEngineConfigurator(sslContextConfig.createSSLContext(),
                false, false, false);
    }


    /**
     * Custom HTTP Adapter implementation
     */
    public static class HelloWorldAdapter extends GrizzlyAdapter {

        /**
         * {@inheritDoc}
         */
        @Override
        public void service(GrizzlyRequest request, GrizzlyResponse response) {
            // Return "Hello World" in response to any HTTP request
            try {
                response.getWriter().println("Hello World");
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }
}
