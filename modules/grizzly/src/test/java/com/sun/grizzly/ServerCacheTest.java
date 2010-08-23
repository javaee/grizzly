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

import com.sun.grizzly.connectioncache.server.CacheableSelectionKeyHandler;
import com.sun.grizzly.filter.ReadFilter;
import com.sun.grizzly.filter.EchoFilter;
import com.sun.grizzly.utils.ControllerUtils;
import com.sun.grizzly.utils.TCPIOClient;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import junit.framework.TestCase;

/**
 * Tests Grizzly Connection Management
 * 
 * @author Alexey Stashok
 */
public class ServerCacheTest extends TestCase {
    public static final int PORT = 17511;
    public static final int PACKETS_COUNT = 100;
    public static final int CLIENTS_COUNT = 10;
    
    public void testServerCacheSimplePacket() throws IOException {
        final Controller controller = createController(10, 1);
        controller.setReadThreadsCount(2);
        
        List<TCPIOClient> clients = null;
        
        try {
            byte[] testData = "Hello".getBytes();
            ControllerUtils.startController(controller);
            clients = initializeClients("localhost", PORT, 2);
            
            for(TCPIOClient client : clients) {
                client.send(testData);
                byte[] response = new byte[testData.length];
                client.receive(response);
                assertTrue(Arrays.equals(testData, response));
            }
        } finally {
            ControllerUtils.stopController(controller);
            if (clients != null) {
                closeClients(clients);
            }
        }
    }
    
    public void testServerCacheSeveralPackets() throws IOException {
        final Controller controller = createController(10, 2);
        controller.setReadThreadsCount(2);
        
        List<TCPIOClient> clients = null;
        
        try {
            ControllerUtils.startController(controller);
            clients = initializeClients("localhost", PORT, CLIENTS_COUNT);
            
            for(int i=0; i<PACKETS_COUNT; i++) {
                for(int j=0; j<clients.size(); j++) {
                    TCPIOClient client = clients.get(j);
                    String testString = "Hello. Client#" + j + " Packet#" + i;
                    byte[] testData = testString.getBytes();
                    client.send(testData);
                    byte[] response = new byte[testData.length];
                    client.receive(response);
                    assertEquals(testString, new String(response));
                }
            }
        } finally {
            ControllerUtils.stopController(controller);
            if (clients != null) {
                closeClients(clients);
            }
        }
    }
    
    private List<TCPIOClient> initializeClients(String host, int port, int count) throws IOException {
        List<TCPIOClient> clients = new ArrayList<TCPIOClient>(count);
        for(int i=0; i<count; i++) {
            TCPIOClient client = new TCPIOClient(host, port);
            client.connect();
            clients.add(client);
        }
        
        return clients;
    }
    
    private void closeClients(List<TCPIOClient> clients) {
        for(TCPIOClient client : clients) {
            try {
                client.close();
            } catch (IOException ex) {
            }
        }
    }
    
    private Controller createController(int highWatermark, int numberToReclaim) {
        final ProtocolFilter readFilter = new ReadFilter();
        final ProtocolFilter echoFilter = new EchoFilter();
        
        TCPSelectorHandler selectorHandler = new TCPSelectorHandler();
        selectorHandler.setPort(PORT);
        // Setup server connection cache using CacheableSelectionKeyHandler
        CacheableSelectionKeyHandler selectionKeyHandler = new CacheableSelectionKeyHandler(highWatermark, 
                numberToReclaim);
        selectionKeyHandler.setSelectorHandler(selectorHandler);
        selectorHandler.setSelectionKeyHandler(selectionKeyHandler);
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
}
