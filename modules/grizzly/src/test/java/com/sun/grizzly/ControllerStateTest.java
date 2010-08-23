/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2010 Oracle and/or its affiliates. All rights reserved.
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

import com.sun.grizzly.filter.EchoFilter;
import com.sun.grizzly.filter.LogFilter;
import com.sun.grizzly.filter.ReadFilter;
import com.sun.grizzly.util.SyncThreadPool;
import com.sun.grizzly.util.Utils;
import com.sun.grizzly.util.WorkerThreadImpl;
import com.sun.grizzly.utils.ControllerUtils;
import com.sun.grizzly.utils.TCPIOClient;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import junit.framework.TestCase;

/**
 *
 * @author Alexey Stashok
 */
public class ControllerStateTest extends TestCase {
    public static final int PORT = 17502;
    public static final int SIMULT_CONTROLLER_START = 50;
    
    public void testControllerPauseResume() throws IOException {
        Controller controller = createController(PORT);
        
        try {
            byte[] testData = "Hello".getBytes();
            ControllerUtils.startController(controller);
            byte[] response = echo(testData);
            
            assertTrue(Arrays.equals(testData, response));
            
            controller.pause();
            sleep(1000);
            
            Exception exception = null;
            try {
                response = echo(testData);
            } catch(IOException e) {
                exception = e;
            }
            
            assertNotNull(exception);
            
            controller.resume();
            sleep(1000);

            response = echo(testData);
            assertTrue(Arrays.equals(testData, response));
        } finally {
            controller.stop();
        }
        
    }
    
    public void testControllerRestart() throws Exception {
        final Exception[] exceptionHolder = new Exception[1];
        final Controller controller = new Controller();
        controller.setProtocolChainInstanceHandler(new DefaultProtocolChainInstanceHandler() {

            @Override
            public ProtocolChain poll() {
                ProtocolChain protocolChain = protocolChains.poll();
                if (protocolChain == null) {
                    protocolChain = new DefaultProtocolChain();
                    protocolChain.addFilter(new ReadFilter());
                    protocolChain.addFilter(new LogFilter());
                }
                return protocolChain;
            }
            });

        ControllerUtils.startController(controller);
        
        Thread restartThread = new WorkerThreadImpl(new Runnable() {
            public void run() {
                try {
                    controller.stop();
                    controller.start();
                } catch (Exception ex) {
                    exceptionHolder[0] = ex;
                }
            }
        });
        
        restartThread.start();
        
        try {
            restartThread.join(5000);
        } catch(InterruptedException ex) {
            exceptionHolder[0] = ex;
        } finally {
            try {
                controller.stop();
            } catch(IOException e) {
                exceptionHolder[0] = e;
            }
        }
        
        if (exceptionHolder[0] != null) {
            throw exceptionHolder[0];
        }
    }
    
    public void testControllerStopStartStop() throws Exception {
        final Exception[] exceptionHolder = new Exception[1];
        final Controller controller = new Controller();
        controller.setProtocolChainInstanceHandler(new DefaultProtocolChainInstanceHandler() {

            @Override
            public ProtocolChain poll() {
                ProtocolChain protocolChain = protocolChains.poll();
                if (protocolChain == null) {
                    protocolChain = new DefaultProtocolChain();
                    protocolChain.addFilter(new ReadFilter());
                    protocolChain.addFilter(new LogFilter());
                }
                return protocolChain;
            }
            });

        final Runnable stopThreadRunnable = new Runnable() {
            public void run() {
                try {
                    controller.stop();
                } catch (Exception ex) {
                    exceptionHolder[0] = ex;
                }
            }
        };

        Thread stopThread = new Thread(stopThreadRunnable);

        stopThread.start();
        
        try {
            stopThread.join(5000);
            if (stopThread.isAlive()){
                exceptionHolder[0] = new IllegalStateException("The stop thread is still alive #1");
            }

        } catch(InterruptedException ex) {
            exceptionHolder[0] = ex;
        }

        if (exceptionHolder[0] == null) {

            ControllerUtils.startController(controller);

            stopThread = new Thread(stopThreadRunnable);

            stopThread.start();

            try {
                stopThread.join(5000);
                if (stopThread.isAlive()) {
                    exceptionHolder[0] = new IllegalStateException("The stop thread is still alive #2");
                }
            } catch (InterruptedException ex) {
                exceptionHolder[0] = ex;
            } finally {
                try {
                    controller.stop();
                } catch (IOException e) {
                    exceptionHolder[0] = e;
                }
            }
        }
        
        if (exceptionHolder[0] != null) {
            throw exceptionHolder[0];
        }
    }
    
    public void testConcurrentControllerStart() {
        final int[] resultControllerStarted = new int[1];
        final Controller controller = createController(PORT);
        
        controller.setThreadPool(new SyncThreadPool("testpool", 5,
                SIMULT_CONTROLLER_START * 5,
                Integer.MAX_VALUE, TimeUnit.MILLISECONDS));
        
        final Callable<Object>[] callables = new Callable[SIMULT_CONTROLLER_START];
        for (int x = 0; x < SIMULT_CONTROLLER_START - 1; x++) {
            callables[x] = new Callable() {
                public Object call() throws Exception {
                    SelectorHandler selectorHandler = new TCPSelectorHandler(true);
                    controller.addSelectorHandler(selectorHandler);
                    
                    controller.start();
                    return null;
                }
            };
        }
        
        callables[SIMULT_CONTROLLER_START - 1] = new Callable() {
            public Object call() throws Exception {
                Utils.dumpOut("Sleeping 10 seconds....");
                Thread.sleep(10000);
                resultControllerStarted[0] = controller.getSelectorHandlers().size();

                Utils.dumpOut("Shutdown controller");
                controller.stop();
                Utils.dumpOut("Shutdown completed");
                return null;
            }
        };
        
        ExecutorService executor = Executors.newFixedThreadPool(SIMULT_CONTROLLER_START);
        List<Callable<Object>> c = Arrays.asList(callables);
        try {
            executor.invokeAll(c);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            executor.shutdown();
            try {
                controller.stop();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
        
        assertEquals(resultControllerStarted[0], SIMULT_CONTROLLER_START);
    }
    
    private byte[] echo(byte[] packet) throws IOException {
        TCPIOClient client = new TCPIOClient("localhost", PORT);
        byte[] response = null;
        try {
            client.connect();
            client.send(packet);
            response = new byte[packet.length];
            client.receive(response);
            client.close();
        } finally {
            client.close();
        }
        
        return response;
    }
    
    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (Exception e) {
        }
    }
    
    private Controller createController(int port) {
        final ProtocolFilter readFilter = new ReadFilter();
        final ProtocolFilter echoFilter = new EchoFilter();

        TCPSelectorHandler selectorHandler = new TCPSelectorHandler();
        selectorHandler.setPort(port);

        final Controller controller = new Controller();

        controller.setSelectorHandler(selectorHandler);

        controller.setProtocolChainInstanceHandler(
                new DefaultProtocolChainInstanceHandler() {

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
}
