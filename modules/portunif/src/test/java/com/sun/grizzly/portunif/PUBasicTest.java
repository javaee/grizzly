/*
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License).  You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the license at
 * https://glassfish.dev.java.net/public/CDDLv1.0.html or
 * glassfish/bootstrap/legal/CDDLv1.0.txt.
 * See the License for the specific language governing
 * permissions and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL
 * Header Notice in each file and include the License file
 * at glassfish/bootstrap/legal/CDDLv1.0.txt.
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * you own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 *
 * Copyright 2006 Sun Microsystems, Inc. All rights reserved.
 */

package com.sun.grizzly.portunif;

import com.sun.grizzly.Controller;
import com.sun.grizzly.DefaultProtocolChain;
import com.sun.grizzly.DefaultProtocolChainInstanceHandler;
import com.sun.grizzly.ProtocolChain;
import com.sun.grizzly.ProtocolFilter;
import com.sun.grizzly.TCPSelectorHandler;
import com.sun.grizzly.filter.EchoFilter;
import com.sun.grizzly.utils.ControllerUtils;
import com.sun.grizzly.utils.NonBlockingTCPIOClient;
import com.sun.grizzly.utils.Utils;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import junit.framework.TestCase;

/**
 * @author Alexey Stashok
 */
public class PUBasicTest extends TestCase {
    public static final int PORT = 18890;
    public static final int PACKETS_COUNT = 100;
    
    public void testEmptyFinders() throws IOException {
        Controller controller = createController(PORT, new PUFilter());
        NonBlockingTCPIOClient client = new NonBlockingTCPIOClient("localhost", PORT);
        
        try {
            byte[] testData = "Hello".getBytes();
            ControllerUtils.startController(controller);
            client.connect();
            client.send(testData);
            byte[] response = new byte[testData.length];
            client.receive(response);
            assertTrue(Arrays.equals(testData, response));
        } finally {
            controller.stop();
            client.close();
        }
    }

    /**
     * If Finder is added, but there is no correspondent Handler - 
     * ProtocolFilterChain should be executed
     */
    public void testPUDefaultChainExecution() throws IOException {
        String protocolId = "ABC";
        PUFilter puFilter = new PUFilter();
        puFilter.addProtocolFinder(new SimpleProtocolFinder(protocolId));
        Controller controller = createController(PORT, puFilter);
        
        NonBlockingTCPIOClient client = new NonBlockingTCPIOClient("localhost", PORT);
        
        try {
            byte[] testData = (protocolId + "payload").getBytes();
            ControllerUtils.startController(controller);
            client.connect();
            client.send(testData);
            byte[] response = new byte[testData.length];
            client.receive(response);
            assertTrue(Arrays.equals(testData, response));
        } finally {
            controller.stop();
            client.close();
        }
    }

    public void testPUFinderHandlerExecution() throws IOException {
        String protocolId = "ABC";
        PUFilter puFilter = new PUFilter();
        puFilter.addProtocolFinder(new SimpleProtocolFinder(protocolId));
        puFilter.addProtocolHandler(new SimpleProtocolHandler(protocolId));
        
        Controller controller = createController(PORT, puFilter);
        
        NonBlockingTCPIOClient client = new NonBlockingTCPIOClient("localhost", PORT);
        
        try {
            byte[] testData = (protocolId + "payload").getBytes();
            ControllerUtils.startController(controller);
            client.connect();
            client.send(testData);
            byte[] response = new byte[protocolId.length()];
            client.receive(response);
            assertEquals(new String(response), Utils.reverseString(protocolId));
        } finally {
            controller.stop();
            client.close();
        }
    }
    
    public void testTwoPUFinderHandlerExecution() throws IOException {
        String[] protocolIds = new String[] {"ABC", "QWER"};
        PUFilter puFilter = new PUFilter();
        for(String protocolId : protocolIds) {
            puFilter.addProtocolFinder(new SimpleProtocolFinder(protocolId));
            puFilter.addProtocolHandler(new SimpleProtocolHandler(protocolId));
        }
        
        Controller controller = createController(PORT, puFilter);
        controller.setReadThreadsCount(5);
        
        List<NonBlockingTCPIOClient> clients = null;
        
        try {
            ControllerUtils.startController(controller);
            clients = initializeClients("localhost", PORT, protocolIds.length);
            
            for(int i=0; i<PACKETS_COUNT; i++) {
                for(int j=0; j<clients.size(); j++) {
                    NonBlockingTCPIOClient client = clients.get(j);
                    String protocolId = protocolIds[j];
                    String testString = protocolId + ": Hello. Client#" + j + " Packet#" + i;
                    byte[] testData = testString.getBytes();
                    client.send(testData);
                    byte[] response = new byte[protocolId.length()];
                    client.receive(response);
                    assertEquals(Utils.reverseString(protocolId), 
                            new String(response));
                }
            }
        } finally {
            ControllerUtils.stopController(controller);
            if (clients != null) {
                closeClients(clients);
            }
        }
    }
    
    private List<NonBlockingTCPIOClient> initializeClients(String host, int port, int count) throws IOException {
        List<NonBlockingTCPIOClient> clients = new ArrayList<NonBlockingTCPIOClient>(count);
        for(int i=0; i<count; i++) {
            NonBlockingTCPIOClient client = new NonBlockingTCPIOClient(host, port);
            client.connect();
            clients.add(client);
        }
        
        return clients;
    }
    
    private void closeClients(List<NonBlockingTCPIOClient> clients) {
        for(NonBlockingTCPIOClient client : clients) {
            try {
                client.close();
            } catch (IOException ex) {
            }
        }
    }
    
    private Controller createController(int port, final PUFilter puFilter) {
        final ProtocolFilter echoFilter = new EchoFilter();
        
        TCPSelectorHandler selectorHandler = new TCPSelectorHandler();
        selectorHandler.setPort(port);
        
        final Controller controller = new Controller();
        
        controller.setSelectorHandler(selectorHandler);
        
        controller.setProtocolChainInstanceHandler(
                new DefaultProtocolChainInstanceHandler(){
            @Override
            public ProtocolChain poll() {
                ProtocolChain protocolChain = protocolChains.poll();
                if (protocolChain == null){
                    protocolChain = new DefaultProtocolChain();
                    protocolChain.addFilter(puFilter);
                    protocolChain.addFilter(echoFilter);
                }
                return protocolChain;
            }
        });
        
        return controller;
    }
}
