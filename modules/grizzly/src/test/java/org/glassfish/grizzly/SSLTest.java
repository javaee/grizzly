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
package org.glassfish.grizzly;

import java.net.URL;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import junit.framework.TestCase;
import org.glassfish.grizzly.filterchain.TransportFilter;
import org.glassfish.grizzly.nio.transport.TCPNIOConnection;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import org.glassfish.grizzly.ssl.BlockingSSLHandshaker;
import org.glassfish.grizzly.ssl.SSLContextConfigurator;
import org.glassfish.grizzly.ssl.SSLEngineConfigurator;
import org.glassfish.grizzly.ssl.SSLFilter;
import org.glassfish.grizzly.ssl.SSLHandshaker;
import org.glassfish.grizzly.ssl.SSLStreamReader;
import org.glassfish.grizzly.ssl.SSLStreamWriter;
import org.glassfish.grizzly.util.EchoFilter;

/**
 *
 * @author oleksiys
 */
public class SSLTest extends TestCase {

    public static final int PORT = 7779;

    public void testSimpleSyncSSL() throws Exception {
        Connection connection = null;
        SSLContextConfigurator sslContextConfigurator = createSSLContextConfigurator();
        
        SSLEngineConfigurator clientSSLEngineConfigurator =
                new SSLEngineConfigurator(
                sslContextConfigurator.createSSLContext());

        TCPNIOTransport transport =
                TransportFactory.getInstance().createTCPTransport();
        transport.getFilterChain().add(new TransportFilter());
        transport.getFilterChain().add(new SSLFilter());
        transport.getFilterChain().add(new EchoFilter());

        SSLStreamReader reader = null;
        SSLStreamWriter writer = null;

        try {
            transport.bind(PORT);
            transport.start();

            transport.configureBlocking(true);

            Future<Connection> future = transport.connect("localhost", PORT);
            connection = (TCPNIOConnection) future.get(10, TimeUnit.SECONDS);
            assertTrue(connection != null);

            connection.setProcessor(null);

            reader = new SSLStreamReader(connection.getStreamReader());
            writer = new SSLStreamWriter(connection.getStreamWriter());

            reader.setBlocking(true);
            writer.setBlocking(true);

            SSLHandshaker handshaker = new BlockingSSLHandshaker();
            
            Future handshakeFuture = handshaker.handshake(reader, writer,
                    clientSSLEngineConfigurator);

            assertTrue(handshakeFuture.isDone());
            handshakeFuture.get();
            
            byte[] sentMessage = "Hello world!".getBytes();

            // aquire read lock to not allow incoming data to be processed by Processor
            writer.writeByteArray(sentMessage);
            Future<Integer> writeFuture = writer.flush();
            
            assertTrue("Write timeout", writeFuture.isDone());
            writeFuture.get();

            byte[] receivedMessage = new byte[sentMessage.length];

            Future readFuture = reader.notifyAvailable(receivedMessage.length);
            assertTrue(readFuture.isDone());
            readFuture.get();

            reader.readByteArray(receivedMessage);
            
            String sentString = new String(sentMessage);
            String receivedString = new String(receivedMessage);
            assertEquals(sentString, receivedString);
        }  finally {
            if (reader != null) {
                reader.close();
            }

            if (writer != null) {
                writer.close();
            }
            if (connection != null) {
                connection.close();
            }

            transport.stop();
            TransportFactory.getInstance().close();
        }
    }

    public void testSimpleAsyncSSL() throws Exception {
        Connection connection = null;
        SSLContextConfigurator sslContextConfigurator = createSSLContextConfigurator();

        SSLEngineConfigurator clientSSLEngineConfigurator =
                new SSLEngineConfigurator(sslContextConfigurator.createSSLContext());

        TCPNIOTransport transport =
                TransportFactory.getInstance().createTCPTransport();
        transport.getFilterChain().add(new TransportFilter());
        transport.getFilterChain().add(new SSLFilter());
        transport.getFilterChain().add(new EchoFilter());

        SSLStreamReader reader = null;
        SSLStreamWriter writer = null;

        try {
            transport.bind(PORT);
            transport.start();

            transport.configureBlocking(true);

            Future<Connection> future = transport.connect("localhost", PORT);
            connection = (TCPNIOConnection) future.get(10, TimeUnit.SECONDS);
            assertTrue(connection != null);

            connection.setProcessor(null);

            reader = new SSLStreamReader(connection.getStreamReader());
            writer = new SSLStreamWriter(connection.getStreamWriter());

            SSLHandshaker handshaker = new BlockingSSLHandshaker();

            Future handshakeFuture = handshaker.handshake(reader, writer,
                    clientSSLEngineConfigurator);

            handshakeFuture.get(10, TimeUnit.SECONDS);
            assertTrue(handshakeFuture.isDone());

            byte[] sentMessage = "Hello world!".getBytes();

            // aquire read lock to not allow incoming data to be processed by Processor
            writer.writeByteArray(sentMessage);
            Future writeFuture = writer.flush();

            writeFuture.get(10, TimeUnit.SECONDS);
            assertTrue("Write timeout", writeFuture.isDone());

            byte[] receivedMessage = new byte[sentMessage.length];

            Future readFuture = reader.notifyAvailable(receivedMessage.length);
            readFuture.get(10, TimeUnit.SECONDS);
            assertTrue(readFuture.isDone());

            reader.readByteArray(receivedMessage);

            String sentString = new String(sentMessage);
            String receivedString = new String(receivedMessage);
            assertEquals(sentString, receivedString);
        }  finally {
            if (reader != null) {
                reader.close();
            }

            if (writer != null) {
                writer.close();
            }
            if (connection != null) {
                connection.close();
            }

            transport.stop();
            TransportFactory.getInstance().close();
        }
    }

    private SSLContextConfigurator createSSLContextConfigurator() {
        SSLContextConfigurator sslContextConfigurator =
                new SSLContextConfigurator();
        ClassLoader cl = getClass().getClassLoader();
        // override system properties
        URL cacertsUrl = cl.getResource("ssltest-cacerts.jks");
        if (cacertsUrl != null) {
            sslContextConfigurator.setTrustStoreFile(cacertsUrl.getFile());
        }

        // override system properties
        URL keystoreUrl = cl.getResource("ssltest-keystore.jks");
        if (keystoreUrl != null) {
            sslContextConfigurator.setKeyStoreFile(keystoreUrl.getFile());
        }

        SSLContextConfigurator.DEFAULT_CONFIG = sslContextConfigurator;
        
        return sslContextConfigurator;
    }
}