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

package org.glassfish.grizzly.samples.ssl;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.Charset;
import org.glassfish.grizzly.filterchain.FilterChainBuilder;
import org.glassfish.grizzly.filterchain.TransportFilter;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import org.glassfish.grizzly.nio.transport.TCPNIOTransportBuilder;
import org.glassfish.grizzly.samples.echo.EchoFilter;
import org.glassfish.grizzly.ssl.SSLContextConfigurator;
import org.glassfish.grizzly.ssl.SSLEngineConfigurator;
import org.glassfish.grizzly.ssl.SSLFilter;
import org.glassfish.grizzly.utils.StringFilter;

/**
 * Class initializes and starts the SSL echo server, based on Grizzly 2.0
 * We use the {@link EchoFilter} from echo example.
 *
 * @see SSLFilter
 * @see SSLContextConfigurator
 * @see SSLEngineConfigurator
 *
 * @author Alexey Stashok
 */
public class SSLEchoServer {
    public static final String HOST = "localhost";
    public static final int PORT = 7777;

    public static void main(String[] args) throws IOException {
        // Create a FilterChain using FilterChainBuilder
        FilterChainBuilder filterChainBuilder = FilterChainBuilder.stateless();
        // Add TransportFilter, which is responsible
        // for reading and writing data to the connection
        filterChainBuilder.add(new TransportFilter());

        // Initialize and add SSLFilter
        final SSLEngineConfigurator serverConfig = initializeSSL();
        final SSLEngineConfigurator clientConfig = serverConfig.copy().setClientMode(true);

        filterChainBuilder.add(new SSLFilter(serverConfig, clientConfig));

        // Add StringFilter, which will be responsible for Buffer <-> String transformation
        filterChainBuilder.add(new StringFilter(Charset.forName("UTF-8")));

        // Use the plain EchoFilter
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

            System.out.println("Press any key to stop the server...");
            System.in.read();
        } finally {
            System.out.println("Stopping transport...");
            // stop the transport
            transport.shutdownNow();

            System.out.println("Stopped transport...");
        }
    }
    
    /**
     * Initialize server side SSL configuration.
     * 
     * @return server side {@link SSLEngineConfigurator}.
     */
    private static SSLEngineConfigurator initializeSSL() {
        // Initialize SSLContext configuration
        SSLContextConfigurator sslContextConfig = new SSLContextConfigurator();

        // Set key store
        ClassLoader cl = SSLEchoServer.class.getClassLoader();
        URL cacertsUrl = cl.getResource("ssltest-cacerts.jks");
        if (cacertsUrl != null) {
            sslContextConfig.setTrustStoreFile(cacertsUrl.getFile());
            sslContextConfig.setTrustStorePass("changeit");
        }

        // Set trust store
        URL keystoreUrl = cl.getResource("ssltest-keystore.jks");
        if (keystoreUrl != null) {
            sslContextConfig.setKeyStoreFile(keystoreUrl.getFile());
            sslContextConfig.setKeyStorePass("changeit");
        }


        // Create SSLEngine configurator
        return new SSLEngineConfigurator(sslContextConfig.createSSLContext(),
                false, false, false);
    }
}
