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

import com.sun.grizzly.aio.AIOController;
import com.sun.grizzly.aio.filter.AIOReadFilter;
import com.sun.grizzly.aio.TCPAIOHandler;
import com.sun.grizzly.aio.filter.AIOEchoFilter;
import com.sun.grizzly.utils.ControllerUtils;
import com.sun.grizzly.utils.NonBlockingTCPIOClient;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import junit.framework.TestCase;

/**
 * Tests the default {@link Controller} configuration
 * 
 * @author Alexey Stashok
 */
public class BasicAIOTest extends TestCase {

    public static final int PORT = 17503;
    public static final int PACKETS_COUNT = 100;

    public void testSimplePacket() throws IOException {
        Controller controller = createController(PORT);
        NonBlockingTCPIOClient client = new NonBlockingTCPIOClient("localhost", PORT);

        try {
            byte[] testData = "Hello".getBytes();
            System.out.println("Starting AIOController");
            ControllerUtils.startController(controller);
            System.out.println("connect");
            client.connect();
            client.send(testData);
            byte[] response = new byte[testData.length];
            System.out.println("Reading");
            client.receive(response);
            assertTrue(Arrays.equals(testData, response));
        } finally {
            controller.stop();
            client.close();
        }
    }

    public void testSeveralPackets() throws IOException, InterruptedException {
        Thread.sleep(5000);

        System.out.println("Starting AIOController");
        Controller controller = createController(PORT);
        ControllerUtils.startController(controller);

        List<NonBlockingTCPIOClient> clients = null;

        try {
            clients = initializeClients("localhost", PORT, 2);

            for (int i = 0; i < PACKETS_COUNT; i++) {
                for (int j = 0; j < clients.size(); j++) {
                    NonBlockingTCPIOClient client = clients.get(j);
                    String testString = "Hello. Client#" + j + " Packet#" + i;
                    byte[] testData = testString.getBytes();
                    client.send(testData);
                    byte[] response = new byte[testData.length];
                    client.receive(response);
                    assertEquals(testString, new String(response));
                }
            }
            System.out.println("Completed");
        } finally {
            ControllerUtils.stopController(controller);
            if (clients != null) {
                closeClients(clients);
            }
        }
    }

    private List<NonBlockingTCPIOClient> initializeClients(String host, int port, int count) throws IOException {
        List<NonBlockingTCPIOClient> clients = new ArrayList<NonBlockingTCPIOClient>(count);
        for (int i = 0; i < count; i++) {
            NonBlockingTCPIOClient client = new NonBlockingTCPIOClient(host, port);
            client.connect();
            clients.add(client);
        }

        return clients;
    }

    private void closeClients(List<NonBlockingTCPIOClient> clients) {
        for (NonBlockingTCPIOClient client : clients) {
            try {
                client.close();
            } catch (IOException ex) {
            }
        }
    }

    private Controller createController(int port) {
        final ProtocolFilter aioReadFilter = new AIOReadFilter();
        final ProtocolFilter echoFilter = new AIOEchoFilter();

        final AIOController aioConroller = new AIOController();

        TCPAIOHandler selectorHandler = new TCPAIOHandler(aioConroller);
        selectorHandler.setPort(port);

        aioConroller.addAIOHandler(selectorHandler);

        aioConroller.setProtocolChainInstanceHandler(
                new DefaultProtocolChainInstanceHandler() {

                    @Override
                    public ProtocolChain poll() {
                        ProtocolChain protocolChain = protocolChains.poll();
                        if (protocolChain == null) {
                            protocolChain = new DefaultProtocolChain();
                            protocolChain.addFilter(aioReadFilter);
                            protocolChain.addFilter(echoFilter);
                        }
                        return protocolChain;
                    }
                });

        return aioConroller;
    }
}
