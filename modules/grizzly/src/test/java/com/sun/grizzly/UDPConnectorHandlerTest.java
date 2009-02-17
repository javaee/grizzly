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

import com.sun.grizzly.filter.ReadFilter;
import com.sun.grizzly.filter.EchoFilter;
import com.sun.grizzly.utils.ControllerUtils;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import junit.framework.TestCase;

/**
 * Tests the default {@link UDPConnectorHandler}
 *
 * @author Jeanfrancois Arcand
 */
public class UDPConnectorHandlerTest extends TestCase {
    public static final int PORT = 17518;
    public static final int PACKETS_COUNT = 100;
    public static final int CLIENTS_COUNT = 10;
    
    /**
     * A {@link CallbackHandler} handler invoked by the UDPSelectorHandler
     * when a non blocking operation is ready to be processed.
     */
    private CallbackHandler callbackHandler;
    
    public void testSimplePacket() throws IOException {
        final Controller controller = createController();
        ControllerUtils.startController(controller);
        final ConnectorHandler udpConnector =
                controller.acquireConnectorHandler(Controller.Protocol.UDP);
        
        try {
            final byte[] testData = "Hello".getBytes();
            final byte[] response = new byte[testData.length];
            
            final ByteBuffer writeBB = ByteBuffer.wrap(testData);
            final ByteBuffer readBB = ByteBuffer.wrap(response);
            final CountDownLatch responseArrivedLatch = new CountDownLatch(1);
            
            callbackHandler = createCallbackHandler(controller, udpConnector, 
                    responseArrivedLatch, writeBB, readBB);
            
            try{
                udpConnector.connect(new InetSocketAddress("localhost",PORT)
                        ,callbackHandler);
            } catch (Throwable t){
                t.printStackTrace();
            }
            
            long nWrite = udpConnector.write(writeBB,false);
            
            long nRead = -1;
            
            // All bytes written
            if (nWrite == testData.length){
                nRead = udpConnector.read(readBB,false);
            }
            
            if (nRead != nWrite){
                try {
                    responseArrivedLatch.await(5,TimeUnit.SECONDS);
                } catch (InterruptedException ex) {
                    ex.printStackTrace();
                }
            }
            assertTrue(Arrays.equals(testData, readBB.array()));
        } finally {
            try{
                udpConnector.close();
                controller.releaseConnectorHandler(udpConnector);
                controller.stop();
            } catch (Throwable t){
                t.printStackTrace();
            }
        }
    }
    
    
    public void testSeveralPackets() throws IOException {
        final Controller controller = createController();
        ControllerUtils.startController(controller);
        try {
            
            for(int i=0; i<CLIENTS_COUNT; i++) {
                final ConnectorHandler udpConnector =
                        controller.acquireConnectorHandler(Controller.Protocol.UDP);
                final byte[] testData = new String("Hello. Client#" + i + " Packet#000").getBytes();
                final byte[] response = new byte[testData.length];
                
                final ByteBuffer writeBB = ByteBuffer.wrap(testData);
                final ByteBuffer readBB = ByteBuffer.wrap(response);
                
                final CountDownLatch[] responseArrivedLatchHolder = new CountDownLatch[1];
                callbackHandler = createCallbackHandler(controller, udpConnector,
                        responseArrivedLatchHolder, writeBB, readBB);
                
                try {
                    udpConnector.connect(new InetSocketAddress("localhost", PORT),
                            callbackHandler);
                    
                    for(int j=0; j<PACKETS_COUNT; j++) {
                        CountDownLatch responseArrivedLatch = new CountDownLatch(1);
                        responseArrivedLatchHolder[0] = responseArrivedLatch;
                        readBB.clear();
                        writeBB.position(writeBB.limit() - 3);
                        byte[] packetNum = Integer.toString(j).getBytes();
                        writeBB.put(packetNum);
                        writeBB.position(0);
                        long nWrite = udpConnector.write(writeBB, false);
                        long nRead = udpConnector.read(readBB, false);
                        
                        if (readBB.position() < testData.length) {
                            waitOnLatch(responseArrivedLatch, 15, TimeUnit.SECONDS);
                        }
                        
                        readBB.flip();
                        String val1 = new String(testData);
                        String val2 = new String(toArray(readBB));
//                        System.out.println("Assert. client#" + i + " packet#" + j + " Pattern: " + val1 + " Came: " + val2);
                        assertEquals(val1, val2);
                    }
                } finally {
                    udpConnector.close();
                    controller.releaseConnectorHandler(udpConnector);
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

    private CallbackHandler createCallbackHandler(final Controller controller,
            final ConnectorHandler udpConnector,
            final CountDownLatch responseArrivedLatch,
            final ByteBuffer writeBB, final ByteBuffer readBB) {
        
        return createCallbackHandler(controller, udpConnector,
                new CountDownLatch[] {responseArrivedLatch},
                writeBB, readBB);
    }

    private CallbackHandler createCallbackHandler(final Controller controller,
            final ConnectorHandler udpConnector,
            final CountDownLatch[] responseArrivedLatchHolder,
            final ByteBuffer writeBB,
            final ByteBuffer readBB) {

        return new CallbackHandler<Context>() {
            public void onConnect(IOEvent<Context> ioEvent) {
                SelectionKey key = ioEvent.attachment().getSelectionKey();
                try {
                    udpConnector.finishConnect(key);
                } catch (IOException ex) {
                    ex.printStackTrace();
                    return;
                }

                controller.registerKey(key, SelectionKey.OP_READ,
                        Controller.Protocol.UDP);
            }

            public void onRead(IOEvent<Context> ioEvent) {
                SelectionKey key = ioEvent.attachment().getSelectionKey();
                DatagramChannel datagramChannel = (DatagramChannel) key.channel();

                try {
                    long nRead = udpConnector.read(readBB, false);
                    if (nRead > 0) {
                        responseArrivedLatchHolder[0].countDown();
                    }
                } catch (IOException ex) {
                    ex.printStackTrace();
                    controller.cancelKey(key);
                }
            }

            public void onWrite(IOEvent<Context> ioEvent) {
                SelectionKey key = ioEvent.attachment().getSelectionKey();
                DatagramChannel datagramChannel = (DatagramChannel) key.channel();
                try {
                    while (writeBB.hasRemaining()) {
                        long nWrite = udpConnector.write(writeBB, false);

                        if (nWrite == 0) {
                            return;
                        }
                    }

                    udpConnector.read(readBB, false);
                } catch (IOException ex) {
                    ex.printStackTrace();
                    controller.cancelKey(key);
                }

            }
         };
    }
    
    private Controller createController() {
        final ProtocolFilter readFilter = new ReadFilter();
        final ProtocolFilter echoFilter = new EchoFilter();
        
        UDPSelectorHandler selectorHandler = new UDPSelectorHandler();
        selectorHandler.setPort(PORT);
        selectorHandler.setSelectionKeyHandler(new DefaultSelectionKeyHandler());
        
        final Controller controller = new Controller();
        
        controller.setSelectorHandler(selectorHandler);
        
        controller.setProtocolChainInstanceHandler(
                new DefaultProtocolChainInstanceHandler(){
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
