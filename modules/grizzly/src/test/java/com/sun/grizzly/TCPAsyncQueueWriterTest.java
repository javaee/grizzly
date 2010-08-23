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

import com.sun.grizzly.filter.EchoAsyncWriteQueueFilter;
import com.sun.grizzly.filter.ReadFilter;
import com.sun.grizzly.util.WorkerThread;
import com.sun.grizzly.utils.ControllerUtils;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import junit.framework.TestCase;

/**
 *
 * @author Alexey Stashok
 */
public class TCPAsyncQueueWriterTest extends TestCase {
    public static final int PORT = 17514;
    public static final int PACKETS_COUNT = 10;
    public static final int CLIENTS_COUNT = 10;
    
    public static final int SIMULT_CLIENTS = 1000;
    public static final int SIMULT_THREADS_COUNT = 10;
    
    /**
     * A {@link CallbackHandler} handler invoked by the TCPSelectorHandler
     * when a non blocking operation is ready to be processed.
     */
    private CallbackHandler callbackHandler;
    private AtomicInteger echoBytesProcessed = new AtomicInteger(0);
    
    public void testSeveralPackets() throws IOException {
        final Controller controller = createController();
        ControllerUtils.startController(controller);
        try {
            
            for(int i=0; i<CLIENTS_COUNT; i++) {
                final TCPConnectorHandler tcpConnector =
                        (TCPConnectorHandler) controller.acquireConnectorHandler(Controller.Protocol.TCP);
                final byte[] testData = new String("Hello. Client#" + i + " Packet#000").getBytes();
                final byte[] response = new byte[testData.length * SIMULT_CLIENTS];
                
                final ByteBuffer writeBB = ByteBuffer.wrap(testData);
                final ByteBuffer readBB = ByteBuffer.wrap(response);
                
                final CountDownLatch[] responseArrivedLatchHolder = new CountDownLatch[1];
                callbackHandler = createCallbackHandler(controller, tcpConnector,
                        responseArrivedLatchHolder, writeBB, readBB);
                
                try {
                    final AtomicInteger sent = new AtomicInteger();
                    tcpConnector.connect(new InetSocketAddress("localhost", PORT),
                            callbackHandler);
                    
                    for(int j=0; j<PACKETS_COUNT; j++) {
                        echoBytesProcessed.set(0);
                        CountDownLatch responseArrivedLatch = new CountDownLatch(1);
                        responseArrivedLatchHolder[0] = responseArrivedLatch;
                        readBB.clear();
                        writeBB.position(writeBB.limit() - 3);
                        byte[] packetNum = Integer.toString(j).getBytes();
                        writeBB.put(packetNum);
                        writeBB.position(0);
                        
                        final Callable<Object>[] callables = new Callable[SIMULT_CLIENTS];
                        for(int x=0; x<SIMULT_CLIENTS; x++) {
                            callables[x] = new Callable() {
                                public Object call() throws Exception {
                                    ByteBuffer bb = writeBB.duplicate();
                                    sent.incrementAndGet();
                                    tcpConnector.writeToAsyncQueue(bb);
                                    return null;
                                }
                            };
                        }
                        ExecutorService executor = Executors.newFixedThreadPool(SIMULT_THREADS_COUNT);
                        List<Callable<Object>> c = Arrays.asList(callables);
                        try {
                            executor.invokeAll(c);
                        } catch(Exception e) {
                            e.printStackTrace();
                        } finally {
                            executor.shutdown();
                        }
                        
                        while(tcpConnector.read(readBB, false) > 0);
                        
                        if (readBB.hasRemaining()) {
                            waitOnLatch(responseArrivedLatch, 15, TimeUnit.SECONDS);
                        }
                        
                        readBB.flip();
                        String val1 = new String(testData);
                        StringBuffer patternBuffer = new StringBuffer();
                        for(int x=0; x<SIMULT_CLIENTS; x++) {
                            patternBuffer.append(val1);
                        }
                        val1 = patternBuffer.toString();
                        String val2 = new String(toArray(readBB));
                        if (!val1.equals(val2)) {
                            Controller.logger().log(Level.INFO, "VAL1: " + val1);
                            Controller.logger().log(Level.INFO, "VAL2: " + val2);
                            Controller.logger().log(Level.INFO, "READBB: " + readBB);
                            Controller.logger().log(Level.INFO, "LATCH: " + responseArrivedLatch.getCount());
                            Controller.logger().log(Level.INFO, "EchoFilter processed bytes: " + echoBytesProcessed.get());
                            Selector selector = tcpConnector.getSelectorHandler().getSelector();
                            HashSet<SelectionKey> mySet = new HashSet<SelectionKey>(selector.keys());
                            SelectionKey clientKey = tcpConnector.getUnderlyingChannel().keyFor(selector);
                            mySet.remove(clientKey);
                            Controller.logger().log(Level.INFO, "CLIENT QUEUE HAS ELEMENTS? : " + tcpConnector.getSelectorHandler().getAsyncQueueWriter().isReady(clientKey));
                            Controller.logger().log(Level.INFO, "CLIENT PROCESSED? : " + tcpConnector.getSelectorHandler().getAsyncQueueWriter().getAsyncQueue(clientKey).processedDataSize);
                            Controller.logger().log(Level.INFO, "TOTAL ELEMENTS SENT: " + sent);
                            Controller.logger().log(Level.INFO, "TOTAL ELEMENTS: " + tcpConnector.getSelectorHandler().getAsyncQueueWriter().getAsyncQueue(clientKey).totalElementsCount);
                            for(SelectionKey key : mySet) {
                                Controller.logger().log(Level.INFO, "KEY " + key + " QUEUE HAS ELEMENTS? : " + tcpConnector.getSelectorHandler().getAsyncQueueWriter().isReady(key));
                            }
                        }
//                        Utils.dumpOut("Assert. client#" + i + " packet#" + j + " Pattern: " + val1 + " Came: " + val2);
                        assertEquals(val1, val2);
                    }
                } finally {
                    tcpConnector.close();
                    controller.releaseConnectorHandler(tcpConnector);
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

    
    private Controller createController() {
        final ProtocolFilter readFilter = new ReadFilter();
        final EchoAsyncWriteQueueFilter echoFilter = new EchoAsyncWriteQueueFilter() {
            @Override
            public boolean execute(Context ctx) throws IOException {
                final WorkerThread workerThread = ((WorkerThread)Thread.currentThread());
                echoBytesProcessed.addAndGet(workerThread.getByteBuffer().position());
                return super.execute(ctx);
            }
        };
        
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
            final CountDownLatch responseArrivedLatch,
            final ByteBuffer writeBB, final ByteBuffer readBB) {
        
        return createCallbackHandler(controller, tcpConnector,
                new CountDownLatch[] {responseArrivedLatch},
                writeBB, readBB);
    }

    private CallbackHandler createCallbackHandler(final Controller controller,
            final ConnectorHandler tcpConnector,
            final CountDownLatch[] responseArrivedLatchHolder,
            final ByteBuffer writeBB,
            final ByteBuffer readBB) {
        
        return new CallbackHandler<Context>(){
            public void onConnect(IOEvent<Context> ioEvent) {
                SelectionKey key = ioEvent.attachment().getSelectionKey();
                try{
                    tcpConnector.finishConnect(key);
                } catch (IOException ex){
                    ex.printStackTrace();
                }
                ioEvent.attachment().getSelectorHandler().register(key,
                        SelectionKey.OP_READ);
            }
            
            public void onRead(IOEvent<Context> ioEvent) {
                SelectionKey key = ioEvent.attachment().getSelectionKey();
                SelectorHandler selectorHandler = ioEvent.attachment().
                        getSelectorHandler();
                SocketChannel socketChannel = (SocketChannel)key.channel();
                
                try {
                    int nRead = socketChannel.read(readBB);
                    if (readBB.hasRemaining()){
                        selectorHandler.register(key, SelectionKey.OP_READ);
                    } else {
                        responseArrivedLatchHolder[0].countDown();
                    }
                } catch (IOException ex){
                    selectorHandler.getSelectionKeyHandler().cancel(key);
                }
            }
            
            public void onWrite(IOEvent<Context> ioEvent) {
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
