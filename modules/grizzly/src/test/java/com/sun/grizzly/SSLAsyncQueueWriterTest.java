/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2007-2010 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.grizzly;

import com.sun.grizzly.filter.SSLEchoAsyncWriteQueueFilter;
import com.sun.grizzly.filter.SSLReadFilter;
import com.sun.grizzly.util.WorkerThread;
import com.sun.grizzly.utils.ControllerUtils;
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.ssl.SSLContext;
import junit.framework.TestCase;

/**
 *
 * @author Alexey Stashok
 */
public class SSLAsyncQueueWriterTest extends TestCase {
    private static Logger logger = Logger.getLogger("grizzly.test");

    public static final int PORT = 17508;
    public static final int PACKETS_COUNT = 5;
    public static final int CLIENTS_COUNT = 100;
    
    public static final int SIMULT_CLIENTS = 1000;
    public static final int SIMULT_THREADS_COUNT = 20;
    
    /**
     * A {@link SSLCallbackHandler} handler invoked by the TCPSelectorHandler
     * when a non blocking operation is ready to be processed.
     */
    private SSLCallbackHandler callbackHandler;
    private AtomicInteger echoBytesProcessed = new AtomicInteger(0);
    
    private SSLConfig sslConfig;
    
    @Override
    public void setUp() throws URISyntaxException {
        sslConfig = new SSLConfig();
        ClassLoader cl = getClass().getClassLoader();
        // override system properties
        URL cacertsUrl = cl.getResource("ssltest-cacerts.jks");
        String trustStoreFile = new File(cacertsUrl.toURI()).getAbsolutePath();
        if (cacertsUrl != null) {
            sslConfig.setTrustStoreFile(trustStoreFile);
            sslConfig.setTrustStorePass("changeit");
        }
        
        logger.log(Level.INFO, "SSL certs path: " + trustStoreFile);
        
        // override system properties
        URL keystoreUrl = cl.getResource("ssltest-keystore.jks");
        String keyStoreFile = new File(keystoreUrl.toURI()).getAbsolutePath();
        if (keystoreUrl != null) {
            sslConfig.setKeyStoreFile(keyStoreFile);
            sslConfig.setKeyStorePass("changeit");
        }
        
        logger.log(Level.INFO, "SSL keystore path: " + keyStoreFile);
        SSLConfig.DEFAULT_CONFIG = sslConfig;
    }

    public void testSeveralPackets() throws IOException {
        Controller.logger().log(Level.INFO, "Starting SSLAsyncQueueWriterTest.testSeveralPackets() test...");
        final Controller controller = createSSLController(SSLConfig.DEFAULT_CONFIG.createSSLContext());
        ControllerUtils.startController(controller);
        try {
            
            for(int i=0; i<CLIENTS_COUNT; i++) {
                final SSLConnectorHandler sslConnector = 
                        (SSLConnectorHandler) controller.acquireConnectorHandler(Controller.Protocol.TLS);
                sslConnector.setController(controller);
                
                final byte[] testData = new String("Hello. Client#" + i + " Packet#000").getBytes();
                int appBufferSize = Math.max(sslConnector.getApplicationBufferSize(), testData.length * SIMULT_CLIENTS);
                final byte[] response = new byte[appBufferSize + sslConnector.getApplicationBufferSize()];
                
                final ByteBuffer writeBB = ByteBuffer.wrap(testData);
                final ByteBuffer readBB = ByteBuffer.wrap(response);
                
                CountDownLatch handshakeDoneLatch = new CountDownLatch(1);
                final AtomicReference<CountDownLatch> responseArrivedLatchHolder = new AtomicReference<CountDownLatch>();
                final AtomicReference<CountDownLatch> handshakeDoneLatchHolder = new AtomicReference<CountDownLatch>(handshakeDoneLatch);
                callbackHandler = createCallbackHandler(controller, sslConnector,
                        responseArrivedLatchHolder, handshakeDoneLatchHolder,
                        writeBB, readBB);
                
                try {
                    sslConnector.connect(new InetSocketAddress("localhost", PORT),
                            callbackHandler);
                    
                    sslConnector.handshake(readBB, true);
                    
                    assertTrue("Handshake is not completed! testString: " + 
                            new String(testData) + " latchCounter: " + 
                            handshakeDoneLatchHolder.get().getCount() +
                            " isConnected: " + sslConnector.isConnected(),
                            sslConnector.isHandshakeDone());

                    for(int j=0; j<PACKETS_COUNT; j++) {
                        synchronized (sslConnector) {
                            echoBytesProcessed.set(0);
                            CountDownLatch responseArrivedLatch = new CountDownLatch(1);
                            responseArrivedLatchHolder.set(responseArrivedLatch);
                            readBB.clear();
                            writeBB.position(testData.length - 3);
                            byte[] packetNum = Integer.toString(j).getBytes();
                            writeBB.put(packetNum);
                            writeBB.position(0);
                            writeBB.limit(testData.length);

                            final Callable<Object>[] callables = new Callable[SIMULT_CLIENTS];
                            for (int x = 0; x < SIMULT_CLIENTS; x++) {
                                callables[x] = new Callable() {

                                    public Object call() throws Exception {
                                        ByteBuffer bb = writeBB.duplicate();
                                        sslConnector.writeToAsyncQueue(bb);
                                        return null;
                                    }
                                };
                            }
                            ExecutorService executor = Executors.newFixedThreadPool(SIMULT_THREADS_COUNT);
                            List<Callable<Object>> c = Arrays.asList(callables);
                            try {
                                executor.invokeAll(c);
                            } catch (Exception e) {
                                e.printStackTrace();
                            } finally {
                                executor.shutdown();
                            }

                            while (sslConnector.read(readBB, false) > 0 && readBB.position() < testData.length * SIMULT_CLIENTS);
                        }
                        
                        if (readBB.position() < testData.length * SIMULT_CLIENTS) {
                            waitOnLatch(responseArrivedLatchHolder.get(), 30, TimeUnit.SECONDS);
                        }
                        
                        synchronized(sslConnector) {
                            readBB.flip();
                            String val1 = new String(testData);
                            StringBuffer patternBuffer = new StringBuffer();
                            for (int x = 0; x < SIMULT_CLIENTS; x++) {
                                patternBuffer.append(val1);
                            }
                            val1 = patternBuffer.toString();
                            String val2 = new String(toArray(readBB));
                            if (!val1.equals(val2)) {
                                Controller.logger().log(Level.INFO, "Client#" + i + " Packet#" + j);
                                Controller.logger().log(Level.INFO, "EXPECTED SIZE: " + testData.length * SIMULT_CLIENTS);
                                Controller.logger().log(Level.INFO, "VAL1: " + val1);
                                Controller.logger().log(Level.INFO, "VAL2: " + val2);
                                Controller.logger().log(Level.INFO, "READBB: " + readBB);
                                Controller.logger().log(Level.INFO, "LATCH: " + responseArrivedLatchHolder.get().getCount());
                                Controller.logger().log(Level.INFO, "QUEUE HAS ELEMENTS? : " + sslConnector.getSelectorHandler().getAsyncQueueWriter().isReady(sslConnector.getUnderlyingChannel().keyFor(sslConnector.getSelectorHandler().getSelector())));
                                Controller.logger().log(Level.INFO, "EchoFilter processed bytes: " + echoBytesProcessed.get());
                            }

                            assertEquals(val1, val2);
                        }
                    }
                } finally {
                    sslConnector.close();
                    controller.releaseConnectorHandler(sslConnector);
                }
            }
        } finally {
            try{
                controller.stop();
            } catch (Throwable t){
                t.printStackTrace();
            }
            Controller.logger().log(Level.INFO, "Finished SSLAsyncQueueWriterTest.testSeveralPackets() test");
        }
    }

    
    private Controller createSSLController(SSLContext sslContext) {
        final SSLReadFilter readFilter = new SSLReadFilter();
        readFilter.setSSLContext(sslContext);
        
        final SSLEchoAsyncWriteQueueFilter echoFilter = new SSLEchoAsyncWriteQueueFilter() {
            @Override
            public boolean execute(Context ctx) throws IOException {
                final WorkerThread workerThread = ((WorkerThread)Thread.currentThread());
                echoBytesProcessed.addAndGet(workerThread.getByteBuffer().position());
                return super.execute(ctx);
            }
        };
        
        SSLSelectorHandler selectorHandler = new SSLSelectorHandler();
        selectorHandler.setPort(PORT);
        
        final Controller controller = new Controller();
        
        controller.setSelectorHandler(selectorHandler);
        controller.setHandleReadWriteConcurrently(true);
        
        controller.setProtocolChainInstanceHandler(new DefaultProtocolChainInstanceHandler() {
            
            @Override
            public ProtocolChain poll() {
                ProtocolChain protocolChain = protocolChains.poll();
                if (protocolChain == null) {
                    protocolChain = new DefaultProtocolChain();
                    protocolChain.addFilter(readFilter);
                    protocolChain.addFilter(echoFilter);
                }
                return protocolChain;
            }
        });
        
        return controller;
    }
    
    private SSLCallbackHandler createCallbackHandler(final Controller controller,
            final SSLConnectorHandler sslConnector,
            final CountDownLatch responseArrivedLatch,
            final CountDownLatch handshakeDoneLatch,
            final ByteBuffer writeBB, final ByteBuffer readBB) {
        
        return createCallbackHandler(controller, sslConnector,
                new AtomicReference<CountDownLatch>(responseArrivedLatch),
                new AtomicReference<CountDownLatch>(handshakeDoneLatch),
                writeBB, readBB);
    }
    
    private SSLCallbackHandler createCallbackHandler(final Controller controller,
            final SSLConnectorHandler sslConnector,
            final AtomicReference<CountDownLatch> responseArrivedLatchHolder,
            final AtomicReference<CountDownLatch> handshakeDoneLatchHolder,
            final ByteBuffer writeBB, final ByteBuffer readBB) {
        
        return new SSLCallbackHandler<Context>() {
            public void onConnect(IOEvent<Context> ioEvent) {
                synchronized (sslConnector) {
                    SelectionKey key = ioEvent.attachment().getSelectionKey();
                    try {
                        sslConnector.finishConnect(key);
                    } catch (IOException ex) {
                        ex.printStackTrace();
                        return;
                    }
                }
            }
            
            public void onRead(IOEvent<Context> ioEvent) {
                synchronized(sslConnector) {
                    try {
                        while (sslConnector.read(readBB, false) > 0 &&
                                readBB.position() < writeBB.capacity() * SIMULT_CLIENTS);

                        if (readBB.position() >= writeBB.limit() * SIMULT_CLIENTS) {
                            responseArrivedLatchHolder.get().countDown();
                        }
                    } catch (IOException ex) {
                        ex.printStackTrace();
                        sslConnector.getSelectorHandler().getSelectionKeyHandler().
                                cancel(ioEvent.attachment().getSelectionKey());
                    }
                }
            }
            
            public void onWrite(IOEvent<Context> ioEvent) {
            }
            
            public void onHandshake(IOEvent<Context> ioEvent) {
                synchronized(sslConnector) {
                    readBB.clear();
                    ioEvent.attachment().getSelectionKey().interestOps(0);
                    handshakeDoneLatchHolder.get().countDown();
                }
            }
        };
    }
    
    public void waitOnLatch(CountDownLatch latch, int timeout, TimeUnit timeUnit) {
        try {
            latch.await(timeout, timeUnit);
        } catch (InterruptedException ex) {
            ex.printStackTrace();
        }
    }
    
    private byte[] toArray(ByteBuffer bb) {
        byte[] buf = new byte[bb.remaining()];
        bb.get(buf);
        return buf;
    }

}
