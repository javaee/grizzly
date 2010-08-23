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

import com.sun.grizzly.connectioncache.client.CacheableConnectorHandlerPool;
import com.sun.grizzly.filter.ReadFilter;
import com.sun.grizzly.filter.EchoFilter;
import com.sun.grizzly.utils.ControllerUtils;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import junit.framework.TestCase;

/**
 * Tests the default {@link TCPConnectorHandler}
 *
 * @author Jeanfrancois Arcand
 */
public class ClientCacheTest extends TestCase {
    public static final int PORT = 17501;
    public static final int PACKETS_COUNT = 100;
    public static final int CLIENTS_COUNT = 10;
    
    public static final int HIGH_WATERMARK = 10;
    public static final int NUMBER_TO_RECLAIM = 2;
    public static final int MAX_PARALLEL = 3;
    /**
     * A {@link CallbackHandler} handler invoked by the TCPSelectorHandler
     * when a non blocking operation is ready to be processed.
     */
    private CallbackHandler callbackHandler;
    
    /**
     * A {@link CallbackHandler} handler invoked by the TCPSelectorHandler
     * when a non blocking operation is ready to be processed.
     */
    public void testOutboutCacheSeveralPackets() throws IOException {
        final Controller controller = createController();
        // setting client connection cache using CacheableConnectorHandlerPool
        controller.setConnectorHandlerPool(new CacheableConnectorHandlerPool(controller,
                HIGH_WATERMARK, NUMBER_TO_RECLAIM, MAX_PARALLEL));
        ControllerUtils.startController(controller);
        try {
            
            for(int i=0; i<CLIENTS_COUNT; i++) {
                final ConnectorHandler tcpConnector =
                        controller.acquireConnectorHandler(Controller.Protocol.TCP);
                final byte[] testData = new String("Hello. Client#" + i).getBytes();
                final ByteBuffer writeBB = ByteBuffer.wrap(testData);
                final byte[] response = new byte[testData.length];
                final ByteBuffer readBB = ByteBuffer.wrap(response);
                CountDownLatch[] responseArrivedLatchHolder = new CountDownLatch[1];
                callbackHandler = createCallbackHandler(controller, tcpConnector,
                        responseArrivedLatchHolder, writeBB, readBB);
                
                try {
                    tcpConnector.connect(new InetSocketAddress("localhost",PORT)
                            ,callbackHandler);
                    
                    for(int j=0; j<PACKETS_COUNT; j++) {
                        CountDownLatch responseArrivedLatch = new CountDownLatch(1);
                        responseArrivedLatchHolder[0] = responseArrivedLatch;
                        readBB.position(0);
                        writeBB.position(0);
                        long nWrite = tcpConnector.write(writeBB,false);
                        
                        long nRead = -1;
                        
                        // All bytes written
                        if (nWrite == testData.length){
                            nRead = tcpConnector.read(readBB,false);
                        }
                        
                        if (nRead != nWrite){
                            try {
                                responseArrivedLatch.await(5,TimeUnit.SECONDS);
                            } catch (InterruptedException ex) {
                                ex.printStackTrace();
                            }
                        }
                        assertEquals(new String(testData), new String(readBB.array()));
                    }
                } finally {
                    tcpConnector.close();
                    controller.releaseConnectorHandler(tcpConnector);
                }
            }
        } finally {
            try{
                controller.stop();
            } catch (Exception t){
                t.printStackTrace();
            }
        }
    }
    
    public void testReconnectOnFailure() throws Exception {
        final Controller controller = createController();
        // setting client connection cache using CacheableConnectorHandlerPool
        controller.setConnectorHandlerPool(new CacheableConnectorHandlerPool(controller,
                HIGH_WATERMARK, NUMBER_TO_RECLAIM, 1));
        ControllerUtils.startController(controller);

        try {
            for (int i = 0; i < CLIENTS_COUNT; i++) {
                final ConnectorHandler tcpConnector =
                        controller.acquireConnectorHandler(Controller.Protocol.TCP);
                final byte[] testData = "Hello world".getBytes();
                final ByteBuffer writeBB = ByteBuffer.wrap(testData);
                final byte[] response = new byte[testData.length];
                final ByteBuffer readBB = ByteBuffer.wrap(response);
                CountDownLatch[] responseArrivedLatchHolder = new CountDownLatch[1];
                callbackHandler = createCallbackHandler(controller, tcpConnector,
                        responseArrivedLatchHolder, writeBB, readBB);

                boolean isClosed = false;
                try {
                    tcpConnector.connect(new InetSocketAddress("localhost", PORT), callbackHandler);

                    CountDownLatch responseArrivedLatch = new CountDownLatch(1);
                    responseArrivedLatchHolder[0] = responseArrivedLatch;
                    readBB.position(0);
                    writeBB.position(0);
                    long nWrite = tcpConnector.write(writeBB, false);

                    long nRead = -1;

                    // All bytes written
                    if (nWrite == testData.length) {
                        nRead = tcpConnector.read(readBB, false);
                    }

                    if (nRead != nWrite) {
                        try {
                            responseArrivedLatch.await(5, TimeUnit.SECONDS);
                        } catch (InterruptedException ex) {
                            ex.printStackTrace();
                        }
                    }
                    assertEquals(new String(testData), new String(readBB.array()));

                    final SelectorHandler selectorHandler = tcpConnector.getSelectorHandler();
                    final SelectableChannel underlyingChannel = tcpConnector.getUnderlyingChannel();

                    tcpConnector.close();
                    controller.releaseConnectorHandler(tcpConnector);
                    isClosed = true;

                    // do low-level connection close to check reconnectOfFailure

                    selectorHandler.addPendingKeyCancel(
                            underlyingChannel.keyFor(selectorHandler.getSelector()));

                    Thread.sleep(500);

                } finally {
                    if (!isClosed) {
                        tcpConnector.close();
                        controller.releaseConnectorHandler(tcpConnector);
                    }
                }
            }
        } finally {
            try{
                controller.stop();
            } catch (Exception t){
                t.printStackTrace();
            }
        }
    }
    
    private Controller createController() {
        final ProtocolFilter readFilter = new ReadFilter();
        final ProtocolFilter echoFilter = new EchoFilter();
        
        TCPSelectorHandler selectorHandler = new TCPSelectorHandler();
        selectorHandler.setPort(PORT);
        
        final Controller controller = new Controller();
        
        controller.setSelectorHandler(selectorHandler);
        
        controller.setProtocolChainInstanceHandler(
                new DefaultProtocolChainInstanceHandler(){
            @Override
            public ProtocolChain poll() {
                ProtocolChain protocolChain = protocolChains.poll();
                if (protocolChain == null){
                    protocolChain = new DefaultProtocolChain();
                    protocolChain.addFilter(readFilter);
                    protocolChain.addFilter(echoFilter);
                }
                return protocolChain;
            }
        });
        
        return controller;
    }
    
    private CallbackHandler createCallbackHandler(final Controller controller,
            final ConnectorHandler tcpConnector,
            final CountDownLatch[] responseArrivedLatchHolder,
            final ByteBuffer writeBB,
            final ByteBuffer readBB) {
        
        return new CallbackHandler<Context>(){
            
            private int readTry;
            
            public void onConnect(IOEvent<Context> ioEvent) {
                SelectionKey key = ioEvent.attachment().getSelectionKey();
                try {
                    tcpConnector.finishConnect(key);
                } catch(IOException ex){
                    ex.printStackTrace();
                    return;
                }
                ioEvent.attachment().getSelectorHandler().register(key,
                        SelectionKey.OP_READ);
            }
            
            public void onRead(IOEvent<Context> ioEvent) {
                SelectionKey key = ioEvent.attachment().getSelectionKey();
                SocketChannel socketChannel = (SocketChannel)key.channel();
                
                try {
                    int nRead = socketChannel.read(readBB);
                    if (nRead == 0 && readTry++ < 2){
                        ioEvent.attachment().getSelectorHandler().register(key,
                                SelectionKey.OP_READ);
                    } else {
                        responseArrivedLatchHolder[0].countDown();
                    }
                } catch (IOException ex){
                    ex.printStackTrace();
                    ioEvent.attachment().getSelectorHandler().
                            getSelectionKeyHandler().cancel(key);
                }
            }
            
            public void onWrite(IOEvent<Context> ioEvent) {
                SelectionKey key = ioEvent.attachment().getSelectionKey();
                SocketChannel socketChannel = (SocketChannel)key.channel();
                try{
                    while(writeBB.hasRemaining()){
                        int nWrite = socketChannel.write(writeBB);
                        
                        if (nWrite == 0){
                            ioEvent.attachment().getSelectorHandler().register(key,
                                    SelectionKey.OP_WRITE);
                            return;
                        }
                    }
                    
                    tcpConnector.read(readBB,false);
                } catch (IOException ex){
                    ex.printStackTrace();
                    ioEvent.attachment().getSelectorHandler().
                            getSelectionKeyHandler().cancel(key);
                }
                
            }
        };
    }
}
