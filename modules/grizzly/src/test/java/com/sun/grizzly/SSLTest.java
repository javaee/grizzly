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
package com.sun.grizzly;

import com.sun.grizzly.attributes.Attribute;
import com.sun.grizzly.filterchain.Filter;
import com.sun.grizzly.filterchain.FilterAdapter;
import com.sun.grizzly.filterchain.FilterChainContext;
import com.sun.grizzly.filterchain.NextAction;
import java.io.IOException;
import java.net.URL;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import junit.framework.TestCase;
import com.sun.grizzly.filterchain.TransportFilter;
import com.sun.grizzly.impl.FutureImpl;
import com.sun.grizzly.nio.transport.TCPNIOTransport;
import com.sun.grizzly.ssl.SSLContextConfigurator;
import com.sun.grizzly.ssl.SSLEngineConfigurator;
import com.sun.grizzly.ssl.SSLFilter;
import com.sun.grizzly.ssl.SSLStreamReader;
import com.sun.grizzly.ssl.SSLStreamWriter;
import com.sun.grizzly.utils.ChunkingFilter;
import com.sun.grizzly.utils.EchoFilter;
import com.sun.grizzly.utils.StringFilter;
import java.util.Arrays;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;
import javax.net.ssl.SSLEngine;

/**
 * Set of SSL tests
 * 
 * @author Alexey Stashok
 */
public class SSLTest extends TestCase {
    private final static Logger logger = Grizzly.logger(SSLTest.class);
    
    public static final int PORT = 7779;

    public void testSimpleSyncSSL() throws Exception {
        doTestSSL(true, 1, 1, 0);
    }

    public void testSimpleAsyncSSL() throws Exception {
        doTestSSL(false, 1, 1, 0);
    }

    public void test5PacketsOn1ConnectionSyncSSL() throws Exception {
        doTestSSL(true, 1, 5, 0);
    }

    public void test5PacketsOn1ConnectionAsyncSSL() throws Exception {
        doTestSSL(false, 1, 5, 0);
    }

    public void test5PacketsOn5ConnectionsSyncSSL() throws Exception {
        doTestSSL(true, 5, 5, 0);
    }

    public void test5PacketsOn5ConnectionsAsyncSSL() throws Exception {
        doTestSSL(false, 5, 5, 0);
    }

    public void testSimpleSyncSSLChunkedBefore() throws Exception {
        doTestSSL(true, 1, 1, 1, new ChunkingFilter(1));
    }

    public void testSimpleAsyncSSLChunkedBefore() throws Exception {
        doTestSSL(false, 1, 1, 1, new ChunkingFilter(1));
    }

    public void testSimpleSyncSSLChunkedAfter() throws Exception {
        doTestSSL(true, 1, 1, 2, new ChunkingFilter(1));
    }

    public void testSimpleAsyncSSLChunkedAfter() throws Exception {
        doTestSSL(false, 1, 1, 2, new ChunkingFilter(1));
    }

    public void testPingPongFilterChainSync() throws Exception {
        doTestPingPongFilterChain(true, 5, 0);
    }

    public void testPingPongFilterChainAsync() throws Exception {
        doTestPingPongFilterChain(false, 5, 0);
    }

    public void testPingPongFilterChainSyncChunked() throws Exception {
        doTestPingPongFilterChain(true, 5, 1, new ChunkingFilter(1));
    }

    public void testPingPongFilterChainAsyncChunked() throws Exception {
        doTestPingPongFilterChain(false, 5, 1, new ChunkingFilter(1));
    }

    protected void doTestPingPongFilterChain(boolean isBlocking,
            int turnAroundsNum, int filterIndex, Filter... filters)
            throws Exception {

        final Integer pingPongTurnArounds = turnAroundsNum;
        
        Connection connection = null;
        SSLContextConfigurator sslContextConfigurator = createSSLContextConfigurator();
        SSLEngineConfigurator clientSSLEngineConfigurator = null;
        SSLEngineConfigurator serverSSLEngineConfigurator = null;

        if (sslContextConfigurator.validateConfiguration(true)) {
            clientSSLEngineConfigurator =
                    new SSLEngineConfigurator(sslContextConfigurator.createSSLContext());
            serverSSLEngineConfigurator =
                    new SSLEngineConfigurator(sslContextConfigurator.createSSLContext(),
                    false, false, false);
        } else {
            fail("Failed to validate SSLContextConfiguration.");
        }
        final SSLFilter sslFilter = new SSLFilter(serverSSLEngineConfigurator,
                clientSSLEngineConfigurator);
        final SSLPingPongFilter pingPongFilter = new SSLPingPongFilter(
                sslFilter, pingPongTurnArounds);
        
        TCPNIOTransport transport =
                TransportFactory.getInstance().createTCPTransport();
        transport.getFilterChain().add(new TransportFilter());
        transport.getFilterChain().add(sslFilter);
        transport.getFilterChain().add(new StringFilter());
        transport.getFilterChain().add(pingPongFilter);

        transport.getFilterChain().addAll(filterIndex, Arrays.asList(filters));

        try {
            transport.bind(PORT);
            transport.start();

            transport.configureBlocking(isBlocking);

            Future<Connection> future = transport.connect("localhost", PORT);
            connection = future.get(10, TimeUnit.SECONDS);
            
            assertTrue(connection != null);

            try {
                assertEquals(pingPongTurnArounds,
                        pingPongFilter.getServerCompletedFeature().get(
                        10, TimeUnit.SECONDS));
            } catch (TimeoutException e) {
                logger.severe("Server timeout");
            }

            assertEquals(pingPongTurnArounds,
                    pingPongFilter.getClientCompletedFeature().get(
                    10, TimeUnit.SECONDS));
            
            connection.close();
            connection = null;
        } finally {
            if (connection != null) {
                connection.close();
            }

            transport.stop();
            TransportFactory.getInstance().close();
        }

    }
    
    public void doTestSSL(boolean isBlocking, int connectionsNum,
            int packetsNumber, int filterIndex, Filter... filters) throws Exception {
        Connection connection = null;
        SSLContextConfigurator sslContextConfigurator = createSSLContextConfigurator();
        SSLEngineConfigurator clientSSLEngineConfigurator = null;
        SSLEngineConfigurator serverSSLEngineConfigurator = null;

        if (sslContextConfigurator.validateConfiguration(true)) {
            clientSSLEngineConfigurator =
                    new SSLEngineConfigurator(sslContextConfigurator.createSSLContext());
            serverSSLEngineConfigurator =
                    new SSLEngineConfigurator(sslContextConfigurator.createSSLContext(),
                    false, false, false);
        } else {
            fail("Failed to validate SSLContextConfiguration.");
        }

        TCPNIOTransport transport =
                TransportFactory.getInstance().createTCPTransport();
        transport.getFilterChain().add(new TransportFilter());
        transport.getFilterChain().add(new SSLFilter(serverSSLEngineConfigurator,
                clientSSLEngineConfigurator));
        transport.getFilterChain().add(new EchoFilter());

        transport.getFilterChain().addAll(filterIndex, Arrays.asList(filters));

        SSLStreamReader reader = null;
        SSLStreamWriter writer = null;

        try {
            transport.bind(PORT);
            transport.start();

            transport.configureBlocking(isBlocking);

            for (int i = 0; i < connectionsNum; i++) {
                Future<Connection> future = transport.connect("localhost", PORT);
                connection = future.get(10, TimeUnit.SECONDS);
                assertTrue(connection != null);

                connection.configureStandalone(true);
                connection.setReadTimeout(10, TimeUnit.SECONDS);

                reader = new SSLStreamReader(transport.getStreamReader(connection));
                writer = new SSLStreamWriter(transport.getStreamWriter(connection));

                final Future handshakeFuture = writer.handshake(reader,
                        clientSSLEngineConfigurator);

                handshakeFuture.get(10, TimeUnit.SECONDS);
                assertTrue(handshakeFuture.isDone());

                for (int j = 0; j < packetsNumber; j++) {
                    byte[] sentMessage = ("Hello world! Connection#" + i + " Packet#" + j).getBytes();

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
                }
                
                reader.close();
                reader = null;
                
                writer.close();
                writer = null;
                
                connection.close();
                connection = null;
            }
        } finally {
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
            sslContextConfigurator.setTrustStorePass("changeit");
        }

        // override system properties
        URL keystoreUrl = cl.getResource("ssltest-keystore.jks");
        if (keystoreUrl != null) {
            sslContextConfigurator.setKeyStoreFile(keystoreUrl.getFile());
            sslContextConfigurator.setKeyStorePass("changeit");
        }

        return sslContextConfigurator;
    }

    private class SSLPingPongFilter extends FilterAdapter {
        private final Attribute<Integer> turnAroundAttr =
                Grizzly.DEFAULT_ATTRIBUTE_BUILDER.createAttribute("TurnAroundAttr");

        private final int turnAroundNum;
        private final SSLFilter sslFilter;

        private final FutureImpl<Integer> serverCompletedFeature =
                new FutureImpl<Integer>();
        private final FutureImpl<Integer> clientCompletedFeature =
                new FutureImpl<Integer>();

        public SSLPingPongFilter(SSLFilter sslFilter, int turnaroundNum) {
            this.sslFilter = sslFilter;
            this.turnAroundNum = turnaroundNum;
        }

        @Override
        public NextAction handleConnect(final FilterChainContext ctx,
                final NextAction nextAction) throws IOException {

            final Connection connection = ctx.getConnection();
            final Transformer encoder = ctx.getEncoder();
            
            try {
                sslFilter.handshake(connection, new CompletionHandlerAdapter<SSLEngine>() {

                    @Override
                    public void completed(SSLEngine result) {
                        try {
                            connection.write("ping", null, encoder);
                            turnAroundAttr.set(connection, 1);
                        } catch (IOException e) {
                            clientCompletedFeature.failure(e);
                        }
                    }
                });
            } catch (Exception e) {
                clientCompletedFeature.failure(e);
            }
            return nextAction;
        }


        @Override
        public NextAction handleRead(final FilterChainContext ctx,
                final NextAction nextAction) throws IOException {

            final Connection connection = ctx.getConnection();
            
            Integer currentTurnAround = turnAroundAttr.get(connection);
            if (currentTurnAround == null) {
                currentTurnAround = 1;
            } else {
                currentTurnAround++;
            }
            
            final String message = (String) ctx.getMessage();
            if (message.equals("ping")) {
                try {
                    connection.write("pong", null, ctx.getEncoder());
                    turnAroundAttr.set(connection, currentTurnAround);
                    if (currentTurnAround >= turnAroundNum) {
                        serverCompletedFeature.result(turnAroundNum);
                    }
                } catch (Exception e) {
                    serverCompletedFeature.failure(e);
                }
            } else if (message.equals("pong")) {
                try {
                    if (currentTurnAround > turnAroundNum) {
                        clientCompletedFeature.result(turnAroundNum);
                        return nextAction;
                    }

                    connection.write("ping", null, ctx.getEncoder());
                    turnAroundAttr.set(connection, currentTurnAround);
                } catch (Exception e) {
                    clientCompletedFeature.failure(e);
                }
                
            }

            return nextAction;
        }

        public Future<Integer> getClientCompletedFeature() {
            return clientCompletedFeature;
        }

        public Future<Integer> getServerCompletedFeature() {
            return serverCompletedFeature;
        }
    }
}
