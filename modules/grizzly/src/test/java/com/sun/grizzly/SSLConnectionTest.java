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

import com.sun.grizzly.filter.SSLReadFilter;
import com.sun.grizzly.filter.SSLEchoFilter;
import com.sun.grizzly.utils.ControllerUtils;
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.ssl.SSLContext;
import junit.framework.TestCase;

/**
 * @author Alexey Stashok
 */
public class SSLConnectionTest extends TestCase {
    private static Logger logger = Logger.getLogger("grizzly.test");
    
    public static final int PORT = 17509;
    public static final int PACKETS_COUNT = 10;
    public static final int CLIENTS_COUNT = 10;
    
    /**
     * A {@link SSLCallbackHandler} handler invoked by the TCPSelectorHandler
     * when a non blocking operation is ready to be processed.
     */
    private SSLCallbackHandler callbackHandler;
    
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
    
    public void testSimplePacket() throws IOException {
        
        final Controller controller = createSSLController(SSLConfig.DEFAULT_CONFIG.createSSLContext());
        ControllerUtils.startController(controller);
        final SSLConnectorHandler sslConnector = (SSLConnectorHandler) controller.
                acquireConnectorHandler(Controller.Protocol.TLS);
        
        try {
            final byte[] testData = "Hello".getBytes();
            final byte[] response = new byte[sslConnector.getApplicationBufferSize()];
            
            final ByteBuffer writeBB = ByteBuffer.wrap(testData);
            final ByteBuffer readBB = ByteBuffer.wrap(response);
            final CountDownLatch handshakeDoneLatch = new CountDownLatch(1);
            final CountDownLatch responseArrivedLatch = new CountDownLatch(1);
            
            callbackHandler = createCallbackHandler(controller, sslConnector, responseArrivedLatch, handshakeDoneLatch, writeBB, readBB);
            
            try {
                sslConnector.connect(new InetSocketAddress("localhost", PORT), callbackHandler);
            } catch (Throwable t) {
                t.printStackTrace();
            }
            
            waitOnLatch(handshakeDoneLatch, 10, TimeUnit.SECONDS);
            assertTrue("Handshake is not completed! testString: " + 
                    new String(testData) + " latchCounter: " + 
                    handshakeDoneLatch.getCount() + 
                    " isConnected: " + sslConnector.isConnected(),
                    sslConnector.isHandshakeDone());
            
            long nWrite = sslConnector.write(writeBB, false);
            
            long nRead = sslConnector.read(readBB, false);
            
            if (readBB.position() < testData.length) {
                waitOnLatch(responseArrivedLatch, 5, TimeUnit.SECONDS);
            }
            
            readBB.flip();
            assertEquals(new String(testData), new String(toArray(readBB)));
        } finally {
            try {
                sslConnector.close();
                controller.releaseConnectorHandler(sslConnector);
                controller.stop();
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
    }
    
    public void testSeveralPackets() throws IOException {
        final Controller controller = createSSLController(SSLConfig.DEFAULT_CONFIG.createSSLContext());
        controller.setReadThreadsCount(5);
        ControllerUtils.startController(controller);
        try {
            
            for(int i=0; i<CLIENTS_COUNT; i++) {
                //System.out.println("Client#" + i);
                final SSLConnectorHandler sslConnector =
                        (SSLConnectorHandler) controller.acquireConnectorHandler(Controller.Protocol.TLS);
                sslConnector.setController(controller);
                final byte[] testData = new String("Hello. Client#" + i + " Packet#000").getBytes();
                final byte[] response = new byte[sslConnector.getApplicationBufferSize()];
                
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
                    
                    waitOnLatch(handshakeDoneLatch, 10, TimeUnit.SECONDS);
                    assertTrue("Handshake is not completed! testString: " + 
                            new String(testData) + " latchCounter: " + 
                            handshakeDoneLatchHolder.get().getCount() +
                            " isConnected: " + sslConnector.isConnected(),
                            sslConnector.isHandshakeDone());

                    for(int j=0; j<PACKETS_COUNT; j++) {
                        //System.out.println("Packet#" + j);
                        synchronized(sslConnector) {
                            CountDownLatch responseArrivedLatch = new CountDownLatch(1);
                            responseArrivedLatchHolder.set(responseArrivedLatch);
                            readBB.clear();
                            writeBB.position(writeBB.limit() - 3);
                            byte[] packetNum = Integer.toString(j).getBytes();
                            writeBB.put(packetNum);
                            writeBB.position(0);
                            long nWrite = sslConnector.write(writeBB, false);
                            long nRead = sslConnector.read(readBB, false);
                        }

                        if (readBB.position() < testData.length) {
                            waitOnLatch(responseArrivedLatchHolder.get(), 15, TimeUnit.SECONDS);
                        }
                        
                        synchronized(sslConnector) {
                            readBB.flip();
                            String val1 = new String(testData);
                            String val2 = new String(toArray(readBB));
                            assertEquals("Didn't receive expected data. Latch: " +
                                    responseArrivedLatchHolder.get().getCount() +
                                    " ByteBuffer: " + readBB, val1, val2);
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
        }
    }
    
    public void testStandaloneBlockingClient() throws IOException {
        final Controller controller = createSSLController(SSLConfig.DEFAULT_CONFIG.createSSLContext());
        ControllerUtils.startController(controller);
        try {
            
            for(int i=0; i<CLIENTS_COUNT; i++) {
                //System.out.println("Client#" + i);
                final SSLConnectorHandler sslConnector = new SSLConnectorHandler();
                final byte[] testData = new String("Hello. Client#" + i + " Packet#000").getBytes();
                final byte[] response = new byte[sslConnector.getApplicationBufferSize()];
                
                final ByteBuffer writeBB = ByteBuffer.wrap(testData);
                final ByteBuffer readBB = ByteBuffer.wrap(response);
                
                try {
                    sslConnector.connect(new InetSocketAddress("localhost", PORT));
                    logger.log(Level.INFO, "SSLConnector.isConnected(): " + sslConnector.isConnected());
                    assertTrue(sslConnector.isConnected());
                    boolean isHandshakeDone = sslConnector.handshake(readBB, true);
                    logger.log(Level.INFO, "Is handshake done: " + isHandshakeDone);
                    assertTrue(isHandshakeDone);
                    for(int j=0; j<PACKETS_COUNT; j++) {
                        writeBB.position(writeBB.limit() - 3);
                        byte[] packetNum = Integer.toString(j).getBytes();
                        writeBB.put(packetNum);
                        writeBB.position(0);
                        sslConnector.write(writeBB, true);
                        long nRead = 1;
                        while(nRead > 0 && readBB.position() < testData.length) {
                            nRead = sslConnector.read(readBB, true);
                        }
                        readBB.flip();
                        
                        String val1 = new String(testData);
                        String val2 = new String(toArray(readBB));
                        //System.out.println("Assert. client#" + i + " packet#" + j + " Pattern: " + val1 + " Came: " + val2 + " nRead: " + nRead + " Buffer: " + readBB);
                        assertEquals(val1, val2);
                        readBB.clear();
                    }
                } finally {
                    sslConnector.close();
                }
            }
        } finally {
            try{
                controller.stop();
            } catch (Throwable t){
                t.printStackTrace();
            }
        }
    }
    
    private Controller createSSLController(SSLContext sslContext) {
        final SSLReadFilter readFilter = new SSLReadFilter();
        readFilter.setSSLContext(sslContext);
        
        final ProtocolFilter echoFilter = new SSLEchoFilter();
        
        SSLSelectorHandler selectorHandler = new SSLSelectorHandler();
        selectorHandler.setPort(PORT);
        
        final Controller controller = new Controller();
        
        controller.setSelectorHandler(selectorHandler);
        controller.setHandleReadWriteConcurrently(false);
        
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
            
            private int readTry;
            
            public void onConnect(IOEvent<Context> ioEvent) {
                synchronized(sslConnector) {
                    SelectionKey key = ioEvent.attachment().getSelectionKey();
                    try {
                        sslConnector.finishConnect(key);
                    } catch (IOException ex) {
                        ex.printStackTrace();
                        return;
                    }

                    try {
                        if (sslConnector.handshake(readBB, false)) {
                            onHandshake(ioEvent);
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
            
            public void onRead(IOEvent<Context> ioEvent) {
                synchronized (sslConnector) {
                    try {
                        long nRead = sslConnector.read(readBB, false);

                        if (readBB.position() == writeBB.capacity() ||
                                readTry++ > 2) {
                            responseArrivedLatchHolder.get().countDown();
                        } else if (nRead > 0) {
                            readTry = 0;
                        }
                    } catch (IOException ex) {
                        ex.printStackTrace();
                        sslConnector.getSelectorHandler().getSelectionKeyHandler().
                                cancel(ioEvent.attachment().getSelectionKey());
                    }
                }
            }
            
            public void onWrite(IOEvent<Context> ioEvent) {
                synchronized (sslConnector) {
                    SelectionKey key = ioEvent.attachment().getSelectionKey();
                    try {
                        while (writeBB.hasRemaining()) {
                            long nWrite = sslConnector.write(writeBB, false);

                            if (nWrite == 0) {
                                return;
                            }
                        }
                    } catch (IOException ex) {
                        ex.printStackTrace();
                        sslConnector.getSelectorHandler().getSelectionKeyHandler().
                                cancel(ioEvent.attachment().getSelectionKey());
                    }
                }
            }
            
            public void onHandshake(IOEvent<Context> ioEvent) {
                synchronized(sslConnector) {
                    readBB.clear();
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
