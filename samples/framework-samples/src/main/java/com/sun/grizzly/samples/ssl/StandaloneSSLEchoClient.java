/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2009-2010 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.grizzly.samples.ssl;

import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import com.sun.grizzly.Connection;
import com.sun.grizzly.StandaloneProcessor;
import com.sun.grizzly.TransportFactory;
import com.sun.grizzly.nio.transport.TCPNIOConnection;
import com.sun.grizzly.nio.transport.TCPNIOTransport;
import com.sun.grizzly.ssl.SSLContextConfigurator;
import com.sun.grizzly.ssl.SSLEngineConfigurator;
import com.sun.grizzly.ssl.SSLStreamReader;
import com.sun.grizzly.ssl.SSLStreamWriter;

/**
 * The simple standalone SSL client, which sends a message to the echo server
 * and waits for response.
 *
 * @see SSLStreamReader
 * @see SSLStreamWriter
 * @see SSLContextConfigurator
 * @see SSLEngineConfigurator
 * 
 * @author Alexey Stashok
 */
public class StandaloneSSLEchoClient {

    public static void main(String[] args) throws IOException,
            ExecutionException, InterruptedException, TimeoutException {

        Connection connection = null;
        SSLStreamReader reader = null;
        SSLStreamWriter writer = null;
        
        // Create the TCP transport
        TCPNIOTransport transport = TransportFactory.getInstance().
                createTCPTransport();

        try {
            // start the transport
            transport.start();

            // perform async. connect to the server
            Future<Connection> future = transport.connect(SSLEchoServer.HOST,
                    SSLEchoServer.PORT);
            // wait for connect operation to complete
            connection = (TCPNIOConnection) future.get(10, TimeUnit.SECONDS);

            assert connection != null;

            connection.configureStandalone(true);
            
            // Initialize SSLReader and SSLWriter
            reader = new SSLStreamReader(StandaloneProcessor.INSTANCE.getStreamReader(connection));
            writer = new SSLStreamWriter(StandaloneProcessor.INSTANCE.getStreamWriter(connection));

            // Perform SSL handshake
            Future handshakeFuture = writer.handshake(reader, initializeSSL());

            handshakeFuture.get(10, TimeUnit.SECONDS);

            String message = "SSL Echo test";
            byte[] sendBytes = message.getBytes();

            // sync. write the complete message using
            // temporary selectors if required
            writer.writeByteArray(sendBytes);
            Future<Integer> writeFuture = writer.flush();
            writeFuture.get();

            assert writeFuture.isDone();

            // allocate the buffer for receiving bytes
            byte[] receiveBytes = new byte[sendBytes.length];
            Future readFuture = reader.notifyAvailable(receiveBytes.length);

            readFuture.get();

            reader.readByteArray(receiveBytes);

            // check the result
            assert Arrays.equals(sendBytes, receiveBytes);

            System.out.println("Echo is DONE successfully");
        } finally {
            // close the client connection
            if (connection != null) {
                connection.close();
            }

            // stop the transport
            transport.stop();
            // release TransportManager resources like ThreadPool etc.
            TransportFactory.getInstance().close();
        }
    }

    /**
     * Initialize client side SSL configuration.
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
        return new SSLEngineConfigurator(sslContextConfig.createSSLContext());
    }
}
