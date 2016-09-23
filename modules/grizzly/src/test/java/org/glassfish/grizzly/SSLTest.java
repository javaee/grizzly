/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2009-2015 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly;

import java.util.concurrent.ExecutorService;
import java.io.InputStream;
import java.net.InetSocketAddress;

import org.glassfish.grizzly.memory.ByteBufferManager;
import org.glassfish.grizzly.memory.HeapMemoryManager;
import org.glassfish.grizzly.nio.transport.TCPNIOTransportBuilder;
import org.glassfish.grizzly.utils.ClientCheckFilter;
import org.glassfish.grizzly.utils.ParallelWriteFilter;
import org.glassfish.grizzly.utils.RandomDelayOnWriteFilter;
import org.junit.Test;
import org.glassfish.grizzly.memory.ByteBufferWrapper;
import org.junit.Before;

import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collection;
import org.junit.runners.Parameterized.Parameters;
import org.glassfish.grizzly.attributes.Attribute;
import org.glassfish.grizzly.filterchain.Filter;
import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.FilterChainBuilder;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;
import java.io.IOException;
import java.net.URL;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.glassfish.grizzly.filterchain.TransportFilter;
import org.glassfish.grizzly.impl.FutureImpl;
import org.glassfish.grizzly.impl.SafeFutureImpl;
import org.glassfish.grizzly.memory.MemoryManager;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import org.glassfish.grizzly.ssl.SSLContextConfigurator;
import org.glassfish.grizzly.ssl.SSLEngineConfigurator;
import org.glassfish.grizzly.ssl.SSLFilter;
import org.glassfish.grizzly.ssl.SSLStreamReader;
import org.glassfish.grizzly.ssl.SSLStreamWriter;
import org.glassfish.grizzly.streams.StreamReader;
import org.glassfish.grizzly.streams.StreamWriter;
import org.glassfish.grizzly.utils.ChunkingFilter;
import org.glassfish.grizzly.utils.EchoFilter;
import org.glassfish.grizzly.utils.StringFilter;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.glassfish.grizzly.memory.Buffers;
import org.glassfish.grizzly.nio.transport.TCPNIOConnectorHandler;
import org.glassfish.grizzly.utils.Futures;
import org.junit.Ignore;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import static org.junit.Assert.*;

/**
 * Set of SSL tests
 * 
 * @author Alexey Stashok
 */
@RunWith(Parameterized.class)
@SuppressWarnings("unchecked")
public class SSLTest {

    private final AtomicBoolean trustCert = new AtomicBoolean(true);

    private final static Logger logger = Grizzly.logger(SSLTest.class);
    
    public static final int PORT = 7779;

    private final boolean isLazySslInit;
    private final MemoryManager manager;

    private final TrustManager trustManager = new X509TrustManager() {

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType)
        throws CertificateException {
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain,
                                       String authType)
        throws CertificateException {
            if (!trustCert.get()) {
                throw new CertificateException("not trusted");
            }
        }
    };

    public SSLTest(boolean isLazySslInit, MemoryManager manager) {
        this.isLazySslInit = isLazySslInit;
        this.manager = manager;
    }

    @Parameters
    public static Collection<Object[]> getLazySslInit() {
        return Arrays.asList(new Object[][]{
                    {Boolean.FALSE, new HeapMemoryManager()},
                    {Boolean.FALSE, new ByteBufferManager()},
                    {Boolean.TRUE, new HeapMemoryManager()},
                    {Boolean.TRUE, new ByteBufferManager()}
                });
    }

    @Before
    public void before() throws Exception {
        Grizzly.setTrackingThreadCache(true);
        ByteBufferWrapper.DEBUG_MODE = true;
    }

    @Test
    public void testSimpleSyncSSL() throws Exception {
        doTestSSL(true, 1, 1, 0);
    }

    @Test
    public void testSimpleAsyncSSL() throws Exception {
        doTestSSL(false, 1, 1, 0);
    }

    @Test
    public void test5PacketsOn1ConnectionSyncSSL() throws Exception {
        doTestSSL(true, 1, 5, 0);
    }

    @Test
    public void test5PacketsOn1ConnectionAsyncSSL() throws Exception {
        doTestSSL(false, 1, 5, 0);
    }

    @Test
    public void test5PacketsOn5ConnectionsSyncSSL() throws Exception {
        doTestSSL(true, 5, 5, 0);
    }

    @Test
    public void test5PacketsOn5ConnectionsAsyncSSL() throws Exception {
        doTestSSL(false, 5, 5, 0);
    }

    @Test
    public void testSimpleSyncSSLChunkedBefore() throws Exception {
        doTestSSL(true, 1, 1, 1, new ChunkingFilter(1));
    }

    @Test
    public void testSimpleAsyncSSLChunkedBefore() throws Exception {
        doTestSSL(false, 1, 1, 1, new ChunkingFilter(1));
    }

    @Test
    public void testSimpleSyncSSLChunkedAfter() throws Exception {
        doTestSSL(true, 1, 1, 2, new ChunkingFilter(1));
    }

    @Test
    public void testSimpleAsyncSSLChunkedAfter() throws Exception {
        doTestSSL(false, 1, 1, 2, new ChunkingFilter(1));
    }

    @Test
    @Ignore
    public void testPingPongFilterChainSync() throws Exception {
        doTestPingPongFilterChain(true, 5, 0);
    }

    @Test
    public void testPingPongFilterChainAsync() throws Exception {
        doTestPingPongFilterChain(false, 5, 0);
    }

    @Test
    @Ignore
    public void testPingPongFilterChainSyncChunked() throws Exception {
        doTestPingPongFilterChain(true, 5, 1, new ChunkingFilter(1));
    }

    @Test
    public void testPingPongFilterChainAsyncChunked() throws Exception {
        doTestPingPongFilterChain(false, 5, 1, new ChunkingFilter(1));
    }

    @Test
    public void testSimplePendingSSLClientWrites() throws Exception {
        doTestPendingSSLClientWrites(1, 1);
    }

    @Test
    public void test20on1PendingSSLClientWrites() throws Exception {
        doTestPendingSSLClientWrites(1, 20);
    }

    @Test
    public void test200On5PendingSSLClientWrites() throws Exception {
        doTestPendingSSLClientWrites(5, 200);
    }

    @Test
    public void testParallelWrites100Packets100Size() throws Exception {
        doTestParallelWrites(100, 100);
    }

    /**
     * Added for GRIZZLY-983.
     */
    @Test
    public void testCompletionHandlerNotification() throws Exception {

        Connection connection = null;
        SSLContextConfigurator sslContextConfigurator = createSSLContextConfigurator();
        SSLEngineConfigurator clientSSLEngineConfigurator = null;
        SSLEngineConfigurator serverSSLEngineConfigurator = null;

        if (sslContextConfigurator.validateConfiguration(true)) {
            clientSSLEngineConfigurator =
                    new SSLEngineConfigurator(createSSLContext(),
                                              true,
                                              false,
                                              false);
            serverSSLEngineConfigurator =
                    new SSLEngineConfigurator(sslContextConfigurator.createSSLContext(true),
                                              false,
                                              false,
                                              false);
        } else {
            fail("Failed to validate SSLContextConfiguration.");
        }


        FilterChainBuilder filterChainBuilder = FilterChainBuilder.stateless();
        filterChainBuilder.add(new TransportFilter());
        filterChainBuilder.add(new SSLFilter(serverSSLEngineConfigurator, null));
        filterChainBuilder.add(new EchoFilter());

        TCPNIOTransport transport =
                TCPNIOTransportBuilder.newInstance().build();
        transport.setProcessor(filterChainBuilder.build());
        transport.setMemoryManager(manager);

        TCPNIOTransport cTransport =
                TCPNIOTransportBuilder.newInstance().build();
        FilterChainBuilder clientChain = FilterChainBuilder.stateless();
        clientChain.add(new TransportFilter());
        clientChain.add(new SSLFilter(null, clientSSLEngineConfigurator));
        clientChain.add(new StringFilter());
        cTransport.setProcessor(clientChain.build());
        cTransport.setMemoryManager(manager);

        try {
            transport.bind(PORT);
            transport.start();

            cTransport.start();

            Future<Connection> future = cTransport.connect("localhost", PORT);
            connection = future.get(10, TimeUnit.SECONDS);

            assertNotNull(connection);

            final FutureImpl<Boolean> result1 = Futures.createSafeFuture();
            trustCert.set(false);
            connection.write("message", new CompletionHandler() {
                @Override
                public void cancelled() {
                    result1.failure(new IllegalStateException("CompletionHandler.cancelled() should not have been called."));
                }

                @Override
                public void failed(Throwable throwable) {
                    if (throwable instanceof SSLHandshakeException) {
                        result1.result(true);
                    } else {
                        result1.failure(new IllegalStateException("SSLHandshakeException is expected, but " + throwable.getClass().getName() + " was hit", throwable));
                    }
                }

                @Override
                public void completed(Object result) {
                    result1.failure(new IllegalStateException("CompletionHandler.onComplete() should not have been called."));
                }

                @Override
                public void updated(Object result) {
                    result1.failure(new IllegalStateException("CompletionHandler.updated() should not have been called."));
                }
            });

            result1.get(10, TimeUnit.SECONDS);

            connection.closeSilently();
            connection = null;

            future = cTransport.connect("localhost", PORT);
            connection = future.get(10, TimeUnit.SECONDS);

            final FutureImpl<Boolean> result2 = Futures.createSafeFuture();

            trustCert.set(true);
            connection.write("message", new CompletionHandler() {
                @Override
                public void cancelled() {
                    result2.failure(new IllegalStateException("CompletionHandler.cancelled() should not have been called."));
                }

                @Override
                public void failed(Throwable throwable) {
                    result2.failure(new IllegalStateException("CompletionHandler.failed() should not have been called."));
                }

                @Override
                public void completed(Object result) {
                    if (result instanceof WriteResult) {
                        result2.result(true);
                    } else {
                        result2.failure(new IllegalStateException("Unexpected result: " + result));
                    }

                }

                @Override
                public void updated(Object result) {
                    result2.failure(new IllegalStateException("CompletionHandler.updated() should not have been called."));
                }
            });

            result2.get(10, TimeUnit.SECONDS);

            connection.closeSilently();
            connection = null;
        } finally {
            if (connection != null) {
                connection.closeSilently();
            }
            cTransport.shutdownNow();
            transport.shutdownNow();
        }

    }


    // ------------------------------------------------------- Protected Methods


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
                    new SSLEngineConfigurator(sslContextConfigurator.createSSLContext(true));
            serverSSLEngineConfigurator =
                    new SSLEngineConfigurator(sslContextConfigurator.createSSLContext(true),
                    false, false, false);
        } else {
            fail("Failed to validate SSLContextConfiguration.");
        }
        final SSLFilter sslFilter = new SSLFilter(serverSSLEngineConfigurator,
                clientSSLEngineConfigurator);
        final SSLPingPongFilter pingPongFilter = new SSLPingPongFilter(
                sslFilter, pingPongTurnArounds);

        FilterChainBuilder filterChainBuilder = FilterChainBuilder.stateless();
        filterChainBuilder.add(new TransportFilter());
        filterChainBuilder.add(sslFilter);
        filterChainBuilder.add(new StringFilter());
        filterChainBuilder.add(pingPongFilter);
        filterChainBuilder.addAll(filterIndex, filters);

        TCPNIOTransport transport =
                TCPNIOTransportBuilder.newInstance().build();
        transport.setProcessor(filterChainBuilder.build());
        transport.setMemoryManager(manager);

        try {
            transport.bind(PORT);
            transport.start();

            transport.configureBlocking(isBlocking);

            Future<Connection> future = transport.connect("localhost", PORT);
            connection = future.get(10, TimeUnit.SECONDS);
            
            assertTrue(connection != null);

            try {
                final Object get = pingPongFilter.getServerCompletedFeature().get(
                                    10, TimeUnit.SECONDS);
                if (get instanceof Connection) {
                    System.out.println("unexpected future=" + pingPongFilter.getServerCompletedFeature() + " object=" + get);
                }
                assertEquals(pingPongTurnArounds, get);
            } catch (TimeoutException e) {
                logger.severe("Server timeout");
            }

            assertEquals(pingPongTurnArounds,
                    pingPongFilter.getClientCompletedFeature().get(
                    10, TimeUnit.SECONDS));
            
            connection.closeSilently();
            connection = null;
        } finally {
            if (connection != null) {
                connection.closeSilently();
            }

            transport.shutdownNow();
        }

    }
    
    protected void doTestSSL(boolean isBlocking, int connectionsNum,
            int packetsNumber, int filterIndex, Filter... filters) throws Exception {
        Connection connection = null;
        SSLContextConfigurator sslContextConfigurator = createSSLContextConfigurator();
        SSLEngineConfigurator clientSSLEngineConfigurator = null;
        SSLEngineConfigurator serverSSLEngineConfigurator = null;

        if (sslContextConfigurator.validateConfiguration(true)) {
            if (isLazySslInit) {
                clientSSLEngineConfigurator =
                        new SSLEngineConfigurator(sslContextConfigurator);
                serverSSLEngineConfigurator =
                        new SSLEngineConfigurator(sslContextConfigurator,
                        false, false, false);
            } else {
                clientSSLEngineConfigurator =
                        new SSLEngineConfigurator(sslContextConfigurator.createSSLContext(true));
                serverSSLEngineConfigurator =
                        new SSLEngineConfigurator(sslContextConfigurator.createSSLContext(true),
                        false, false, false);
            }
        } else {
            fail("Failed to validate SSLContextConfiguration.");
        }

        FilterChainBuilder filterChainBuilder = FilterChainBuilder.stateless();
        filterChainBuilder.add(new TransportFilter());
        filterChainBuilder.add(new SSLFilter(serverSSLEngineConfigurator,
                clientSSLEngineConfigurator));
        filterChainBuilder.add(new EchoFilter());
        filterChainBuilder.addAll(filterIndex, filters);

        TCPNIOTransport transport =
                TCPNIOTransportBuilder.newInstance().build();
        transport.setProcessor(filterChainBuilder.build());
        transport.setMemoryManager(manager);

        SSLStreamReader reader = null;
        SSLStreamWriter writer = null;

        try {
            transport.bind(PORT);
            transport.start();

            transport.configureBlocking(isBlocking);

            for (int i = 0; i < connectionsNum; i++) {
                final FutureImpl<Connection> future =
                        Futures.createSafeFuture();
                transport.connect(
                        new InetSocketAddress("localhost", PORT),
                        Futures.toCompletionHandler(
                        future,
                        new EmptyCompletionHandler<Connection>()  {

                            @Override
                            public void completed(final Connection connection) {
                                connection.configureStandalone(true);
                                connection.setReadTimeout(10, TimeUnit.SECONDS);
                            }
                        }));

                connection = future.get(10, TimeUnit.SECONDS);
                assertTrue(connection != null);

                StreamReader connectionStreamReader =
                        StandaloneProcessor.INSTANCE.getStreamReader(connection);
                StreamWriter connectionStreamWriter =
                        StandaloneProcessor.INSTANCE.getStreamWriter(connection);
                
                reader = new SSLStreamReader(connectionStreamReader);
                writer = new SSLStreamWriter(connectionStreamWriter);

                final Future handshakeFuture = writer.handshake(reader,
                        clientSSLEngineConfigurator);

                handshakeFuture.get(10, TimeUnit.SECONDS);
                assertTrue(handshakeFuture.isDone());

                for (int j = 0; j < packetsNumber; j++) {
                    try {
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
                    } catch (Exception e) {
                        logger.log(Level.WARNING, "Error occurred when testing connection#{0} packet#{1}",
                                new Object[]{i, j});
                        throw e;
                    }
                }
                
                reader.close();
                reader = null;
                
                writer.close();
                writer = null;
                
                connection.closeSilently();
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
                connection.closeSilently();
            }

            transport.shutdownNow();
        }
    }

    protected void doTestPendingSSLClientWrites(int connectionsNum,
            int packetsNumber) throws Exception {
        Connection connection = null;
        SSLContextConfigurator sslContextConfigurator = createSSLContextConfigurator();
        SSLEngineConfigurator clientSSLEngineConfigurator = null;
        SSLEngineConfigurator serverSSLEngineConfigurator = null;

        if (sslContextConfigurator.validateConfiguration(true)) {
            clientSSLEngineConfigurator =
                    new SSLEngineConfigurator(sslContextConfigurator.createSSLContext(true));
            serverSSLEngineConfigurator =
                    new SSLEngineConfigurator(sslContextConfigurator.createSSLContext(true),
                    false, false, false);
        } else {
            fail("Failed to validate SSLContextConfiguration.");
        }

        FilterChainBuilder filterChainBuilder = FilterChainBuilder.stateless();
        filterChainBuilder.add(new TransportFilter());
        filterChainBuilder.add(new SSLFilter(serverSSLEngineConfigurator,
                clientSSLEngineConfigurator));
        filterChainBuilder.add(new EchoFilter());

        TCPNIOTransport transport =
                TCPNIOTransportBuilder.newInstance().build();
        transport.setProcessor(filterChainBuilder.build());
        transport.setMemoryManager(manager);

        final MemoryManager mm = transport.getMemoryManager();

        try {
            transport.bind(PORT);
            transport.start();

            for (int i = 0; i < connectionsNum; i++) {
                final String messagePattern = "Hello world! Connection#" + i + " Packet#";

                final FutureImpl<Integer> clientFuture = SafeFutureImpl.create();
                FilterChainBuilder clientFilterChainBuilder = FilterChainBuilder.stateless();
                clientFilterChainBuilder.add(new TransportFilter());
                clientFilterChainBuilder.add(new SSLFilter(serverSSLEngineConfigurator,
                        clientSSLEngineConfigurator));

                final ClientTestFilter clientTestFilter = new ClientTestFilter(
                        clientFuture, messagePattern, packetsNumber);

                clientFilterChainBuilder.add(clientTestFilter);

                SocketConnectorHandler connectorHandler =
                        TCPNIOConnectorHandler.builder(transport)
                        .processor(clientFilterChainBuilder.build())
                        .build();
                
                Future<Connection> future = connectorHandler.connect("localhost", PORT);
                connection = future.get(10, TimeUnit.SECONDS);
                assertTrue(connection != null);

                int packetNum = 0;
                try {
                    for (int j = 0; j < packetsNumber; j++) {
                        packetNum = j;
                        Buffer buffer = Buffers.wrap(mm, messagePattern + j);
                        connection.write(buffer);
                    }
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Error occurred when testing connection#{0} packet#{1}",
                            new Object[]{i, packetNum});
                    throw e;
                }

                try {
                    Integer bytesReceived = clientFuture.get(10, TimeUnit.SECONDS);
                    assertNotNull(bytesReceived);
                } catch (TimeoutException e) {
                    throw new TimeoutException("Received " + clientTestFilter.getBytesReceived() + " out of " + clientTestFilter.getPatternString().length());
                }

                connection.closeSilently();
                connection = null;
            }
        } finally {
            if (connection != null) {
                connection.closeSilently();
            }

            transport.shutdownNow();
        }
    }

    protected void doTestParallelWrites(int packetsNumber, int size) throws Exception {
        Connection connection = null;
        SSLContextConfigurator sslContextConfigurator = createSSLContextConfigurator();
        SSLEngineConfigurator clientSSLEngineConfigurator = null;
        SSLEngineConfigurator serverSSLEngineConfigurator = null;

        if (sslContextConfigurator.validateConfiguration(true)) {
            clientSSLEngineConfigurator =
                    new SSLEngineConfigurator(sslContextConfigurator.createSSLContext(true));
            serverSSLEngineConfigurator =
                    new SSLEngineConfigurator(sslContextConfigurator.createSSLContext(true),
                    false, false, false);
        } else {
            fail("Failed to validate SSLContextConfiguration.");
        }

        final ExecutorService executorService = Executors.newCachedThreadPool();
        
        FilterChainBuilder filterChainBuilder = FilterChainBuilder.stateless();
        filterChainBuilder.add(new TransportFilter());
        filterChainBuilder.add(new RandomDelayOnWriteFilter());
        filterChainBuilder.add(new SSLFilter(serverSSLEngineConfigurator,
                clientSSLEngineConfigurator));
        filterChainBuilder.add(new StringFilter());
        filterChainBuilder.add(new ParallelWriteFilter(executorService, packetsNumber, size));

        TCPNIOTransport transport =
                TCPNIOTransportBuilder.newInstance().build();
        transport.setProcessor(filterChainBuilder.build());
        transport.setMemoryManager(manager);

        final MemoryManager mm = transport.getMemoryManager();

        try {
            transport.bind(PORT);
            transport.start();

            final FutureImpl<Boolean> clientFuture = SafeFutureImpl.create();
            FilterChainBuilder clientFilterChainBuilder = FilterChainBuilder.stateless();
            clientFilterChainBuilder.add(new TransportFilter());
            clientFilterChainBuilder.add(new SSLFilter(serverSSLEngineConfigurator,
                    clientSSLEngineConfigurator));
            clientFilterChainBuilder.add(new StringFilter());

            final ClientCheckFilter clientTestFilter = new ClientCheckFilter(
                    clientFuture, packetsNumber, size);

            clientFilterChainBuilder.add(clientTestFilter);

            SocketConnectorHandler connectorHandler =
                    TCPNIOConnectorHandler.builder(transport)
                    .processor(clientFilterChainBuilder.build())
                    .build();
            
            Future<Connection> future = connectorHandler.connect("localhost", PORT);
            connection = future.get(10, TimeUnit.SECONDS);
            assertTrue(connection != null);

            try {
                connection.write("start");
            } catch (Exception e) {
                logger.log(Level.WARNING, "Error occurred when sending start command");
                throw e;
            }

            Boolean isDone = clientFuture.get(10, TimeUnit.SECONDS);
            assertEquals(Boolean.TRUE, isDone);
        } finally {
            try {
                executorService.shutdownNow();
            } catch (Exception e) {}
            
            if (connection != null) {
                try {
                    connection.closeSilently();
                } catch (Exception e) {}
            }

            try {
                transport.shutdownNow();
            } catch (Exception e) {}
            
        }
    }

    // --------------------------------------------------------- Private Methods

    
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

     private SSLContext createSSLContext() {
        try {
            InputStream keyStoreStream = SSLTest.class.getResourceAsStream("ssltest-cacerts.jks");
            char[] keyStorePassword = "password".toCharArray();
            KeyStore ks = KeyStore.getInstance("JKS");
            ks.load(keyStoreStream, keyStorePassword);

            char[] certificatePassword = "password".toCharArray();
            KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
            kmf.init(ks, certificatePassword);

            KeyManager[] keyManagers = kmf.getKeyManagers();
            TrustManager[] trustManagers = new TrustManager[]{ trustManager };
            SecureRandom secureRandom = new SecureRandom();

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(keyManagers, trustManagers, secureRandom);

            return sslContext;
        }
        catch (Exception e) {
            throw new Error("Failed to initialize the client SSLContext", e);
        }
    }


    // ---------------------------------------------------------- Nested Classes


    private static class SSLPingPongFilter extends BaseFilter {
        private final Attribute<Integer> turnAroundAttr =
                Grizzly.DEFAULT_ATTRIBUTE_BUILDER.createAttribute("TurnAroundAttr");

        private final int turnAroundNum;
        private final SSLFilter sslFilter;

        private final FutureImpl<Integer> serverCompletedFeature =
                SafeFutureImpl.create();
        private final FutureImpl<Integer> clientCompletedFeature =
                SafeFutureImpl.create();

        public SSLPingPongFilter(SSLFilter sslFilter, int turnaroundNum) {
            this.sslFilter = sslFilter;
            this.turnAroundNum = turnaroundNum;
        }

        @Override
        public NextAction handleConnect(final FilterChainContext ctx)
                throws IOException {

            final Connection connection = ctx.getConnection();
            
            try {
                sslFilter.handshake(connection, new EmptyCompletionHandler<SSLEngine>() {

                    @Override
                    public void completed(SSLEngine result) {
                        turnAroundAttr.set(connection, 1);
                        connection.write("ping", new EmptyCompletionHandler<WriteResult>() {
                            @Override
                            public void failed(Throwable e) {
                                clientCompletedFeature.failure(e);
                            }
                        });
                    }
                });
            } catch (Exception e) {
                clientCompletedFeature.failure(e);
            }
            return ctx.getInvokeAction();
        }


        @Override
        public NextAction handleRead(final FilterChainContext ctx)
                throws IOException {

            final Connection connection = ctx.getConnection();
            
            Integer currentTurnAround = turnAroundAttr.get(connection);
            if (currentTurnAround == null) {
                currentTurnAround = 1;
            } else {
                currentTurnAround++;
            }
            
            final String message = ctx.getMessage();
            if (message.equals("ping")) {
                try {
                    connection.write("pong");
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
                        return ctx.getStopAction();
                    }

                    connection.write("ping");
                    turnAroundAttr.set(connection, currentTurnAround);
                } catch (Exception e) {
                    clientCompletedFeature.failure(e);
                }
                
            }

            return ctx.getStopAction();
        }

        public Future<Integer> getClientCompletedFeature() {
            return clientCompletedFeature;
        }

        public Future<Integer> getServerCompletedFeature() {
            return serverCompletedFeature;
        }

    } // END SSLPingPongFilter


    private static class ClientTestFilter extends BaseFilter {

        private final FutureImpl<Integer> clientFuture;
        private final String messagePattern;
        private final int packetsNumber;

        private volatile int bytesReceived = 0;

        private final String patternString;

        private ClientTestFilter(FutureImpl<Integer> clientFuture, String messagePattern, int packetsNumber) {
            this.clientFuture = clientFuture;
            this.messagePattern = messagePattern;
            this.packetsNumber = packetsNumber;

            final StringBuilder sb = new StringBuilder(packetsNumber * (messagePattern.length() + 5));
            for (int i=0; i<packetsNumber; i++) {
                sb.append(messagePattern).append(i);
            }

            patternString = sb.toString();
        }

        @Override
        public NextAction handleRead(FilterChainContext ctx) throws IOException {
            try {
                final Buffer buffer = ctx.getMessage();

                final String rcvdStr = buffer.toStringContent();
                final String expectedChunk = patternString.substring(bytesReceived, bytesReceived + buffer.remaining());

                if (!expectedChunk.equals(rcvdStr)) {
                    clientFuture.failure(new AssertionError("Content doesn't match. Expected: " + expectedChunk + " Got: " + rcvdStr));
                }

                bytesReceived += buffer.remaining();

                if (bytesReceived == patternString.length()) {
                    clientFuture.result(bytesReceived);
                }
            } catch (Exception e) {
                clientFuture.failure(e);
            }
            return super.handleRead(ctx);
        }

        public int getBytesReceived() {
            return bytesReceived;
        }

        public String getPatternString() {
            return patternString;
        }

    } // END Client Test Filter

}
