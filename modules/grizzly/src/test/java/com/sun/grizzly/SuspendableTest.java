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

import com.sun.grizzly.filter.ReadFilter;
import com.sun.grizzly.filter.EchoFilter;
import com.sun.grizzly.suspendable.Suspendable;
import com.sun.grizzly.suspendable.SuspendableFilter;
import com.sun.grizzly.suspendable.SuspendableHandler;
import com.sun.grizzly.util.Utils;
import com.sun.grizzly.utils.ControllerUtils;
import com.sun.grizzly.utils.NonBlockingTCPIOClient;
import java.io.EOFException;
import java.io.IOException;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;
import junit.framework.TestCase;

/**
 * Tests Suspendable
 * 
 * @author Jeanfrancois Arcand
 */
public class SuspendableTest extends TestCase {

    public static final int PORT = 17512;
    public static final int PACKETS_COUNT = 100;
    private static final String echoString = "This is a suspendable request";
    private Suspendable suspendable;

    public void testBasicSuspendAfter() throws IOException {
        Controller controller = createController(PORT, 5000, SuspendableFilter.Suspend.AFTER);
        NonBlockingTCPIOClient client = new NonBlockingTCPIOClient("localhost", PORT);

        try {
            byte[] testData = echoString.getBytes();
            ControllerUtils.startController(controller);
            client.connect();
            client.send(testData);
            byte[] response = new byte[echoString.getBytes().length];
            try {
                client.receive(response);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
            Utils.dumpOut("Response: " + new String(response));


            byte[] testData2 = echoString.getBytes();
            byte[] response2 = new byte[echoString.getBytes().length];
            client.send(testData2);


            long t1 = System.currentTimeMillis();
            try {
                client.receive(response2);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
            long t2 = System.currentTimeMillis() - t1;
            Utils.dumpOut("Resumed after:" + t2);
            assertTrue(Arrays.equals(echoString.getBytes(), response2));
        } finally {
            controller.stop();
            client.close();
        }
    }

    public void testBasicSuspendBefore() throws IOException, InterruptedException {
        Controller controller = createController(PORT, 5000, SuspendableFilter.Suspend.BEFORE);
        NonBlockingTCPIOClient client = new NonBlockingTCPIOClient("localhost", PORT);

        try {
            byte[] testData = echoString.getBytes();
            ControllerUtils.startController(controller);
            client.connect();
            client.send(testData);
            byte[] response = new byte[echoString.getBytes().length];
            try {
                client.receive(response);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
            byte[] testData2 = echoString.getBytes();
            byte[] response2 = new byte[echoString.getBytes().length];

            long t1 = System.currentTimeMillis();
            Utils.dumpOut("Now trying to push bytes on a suspended request. Must wait for 5 seconds.");
            client.send(testData2);

            try {
                client.receive(response2);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
            long t2 = System.currentTimeMillis() - t1;
            Utils.dumpOut("Resumed after:" + t2);
            assertTrue(Arrays.equals(echoString.getBytes(), response2));
        } finally {
            controller.stop();
            client.close();
        }
    }

    public void testCancelSuspendBefore() throws IOException {
        Controller controller = createController(PORT, 50000, SuspendableFilter.Suspend.BEFORE);
        final NonBlockingTCPIOClient client = new NonBlockingTCPIOClient("localhost", PORT);

        try {
            ControllerUtils.startController(controller);
            client.connect();
            final byte[] testData2 = echoString.getBytes();
            byte[] response2 = new byte[echoString.getBytes().length];
            long t1 = System.currentTimeMillis();
            new Thread() {

                @Override
                public void run() {
                    try {
                        client.send(testData2);
                    } catch (IOException ex) {
                        Logger.getLogger(SuspendableTest.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            }.start();
            // Block to let the request go.
            Thread.sleep(5000);
            suspendable.cancel();

            try {
                client.receive(response2);
            } catch (EOFException ex) {
                assertTrue(true);
                return;
            }

            long t2 = System.currentTimeMillis() - t1;
            Utils.dumpOut("Resumed after:" + t2);
            assertFalse(true);
        } catch (InterruptedException ex) {
            Logger.getLogger(SuspendableTest.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            controller.stop();
            client.close();
        }
    }

    public void testCancelSuspendAfter() throws IOException {
        Controller controller = createController(PORT, 5000, SuspendableFilter.Suspend.AFTER);
        NonBlockingTCPIOClient client = new NonBlockingTCPIOClient("localhost", PORT);

        try {
            byte[] testData = echoString.getBytes();
            ControllerUtils.startController(controller);
            client.connect();
            client.send(testData);
            byte[] response = new byte[echoString.getBytes().length];
            client.receive(response);
            Utils.dumpOut("Response: " + new String(response));


            byte[] testData2 = echoString.getBytes();
            byte[] response2 = new byte[echoString.getBytes().length];
            client.send(testData2);


            long t1 = System.currentTimeMillis();
            Utils.dumpOut("Now trying cancelling");
            Thread.sleep(2000);
            suspendable.cancel();

            try {
                client.receive(response2);
            } catch (IOException ex) {
                assertTrue(true);
                return;
            }

            long t2 = System.currentTimeMillis() - t1;
            Utils.dumpOut("Resumed after:" + t2);
            assertFalse(true);
        } catch (InterruptedException ex) {
            Logger.getLogger(SuspendableTest.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            controller.stop();
            client.close();
        }
    }

    public void testResumeSuspendAfter() throws IOException, InterruptedException {
        Controller controller = createController(PORT, 5000, SuspendableFilter.Suspend.AFTER);
        NonBlockingTCPIOClient client = new NonBlockingTCPIOClient("localhost", PORT);

        try {
            byte[] testData = echoString.getBytes();
            ControllerUtils.startController(controller);
            client.connect();
            client.send(testData);
            byte[] response = new byte[echoString.getBytes().length];
            client.receive(response);
            Utils.dumpOut("Response: " + new String(response));


            byte[] testData2 = echoString.getBytes();
            byte[] response2 = new byte[echoString.getBytes().length];
            client.send(testData2);

            long t1 = System.currentTimeMillis();
            Utils.dumpOut("Now trying cancelling");
            Thread.sleep(2000);
            suspendable.resume();

            Utils.dumpOut("Now trying to push bytes on a resumed request");
            client.receive(response2);
            long t2 = System.currentTimeMillis() - t1;
            Utils.dumpOut("Took:" + t2);

            if (t2 > 5000) {
                assertFalse(false);
            }

            Utils.dumpOut("Response2: " + new String(response2));

            assertTrue(Arrays.equals(echoString.getBytes(), response2));
        } finally {
            controller.stop();
            client.close();
        }
    }

    public void testResumeSuspendBefore() throws IOException, InterruptedException {
        Controller controller = createController(PORT, 50000, SuspendableFilter.Suspend.BEFORE);
        final NonBlockingTCPIOClient client = new NonBlockingTCPIOClient("localhost", PORT);

        try {
            ControllerUtils.startController(controller);
            client.connect();

            final byte[] testData2 = echoString.getBytes();
            byte[] response2 = new byte[echoString.getBytes().length];

            Utils.dumpOut("Send and get resumed using Suspendable");
            long t1 = System.currentTimeMillis();
            new Thread() {

                @Override
                public void run() {
                    try {
                        client.send(testData2);
                    } catch (IOException ex) {
                        Logger.getLogger(SuspendableTest.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            }.start();
            Utils.dumpOut("Now trying resuming");

            // Block to let the request go.
            Thread.sleep(5000);
            suspendable.resume();

            Utils.dumpOut("Now reading bytes");
            client.receive(response2);
            long t2 = System.currentTimeMillis() - t1;
            Utils.dumpOut("Took:" + t2);

            if (t2 > 5000) {
                assertFalse(false);
            }

            Utils.dumpOut("Response2: " + new String(response2));

            assertTrue(Arrays.equals(echoString.getBytes(), response2));
        } finally {
            controller.stop();
            client.close();
        }
    }

    private Controller createController(int port, long timeout, SuspendableFilter.Suspend type) {
        final ProtocolFilter readFilter = new ReadFilter();
        final SuspendableFilter suspendFilter = new SuspendableFilter();
        final ProtocolFilter echoFilter = new EchoFilter();
        suspendable = suspendFilter.suspend("suspendable", timeout, null, new SuspendableHandler() {

            public void interupted(Object attachment) {
                Utils.dumpOut("interrupted");
            }

            public void resumed(Object attachment) {
                Utils.dumpOut("resumed");
            }

            public void expired(Object attachment) {
                Utils.dumpOut("expired");
            }
        }, type);


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
                            protocolChain.addFilter(suspendFilter);
                            protocolChain.addFilter(echoFilter);
                        }
                        return protocolChain;
                    }
                });

        return controller;
    }
}
