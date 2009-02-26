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

package com.sun.grizzly.http;

import com.sun.grizzly.http.utils.EchoStreamAlgorithm;
import com.sun.grizzly.http.utils.SelectorThreadUtils;
import com.sun.grizzly.http.utils.TCPIOClient;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import junit.framework.TestCase;

/**
 * Tests <code>Controller</code> in multithread read mode
 * 
 * @author Alexey Stashok
 */
public class MultiReadSelectorThreadTest extends TestCase {
    public static final int PORT = 18889;
    public static final int PACKETS_COUNT = 10;
    
    public void testSimplePacket() throws IOException {
        final SelectorThread selectorThread = createSelectorThread(PORT, 2);
        
        List<TCPIOClient> clients = null;
        
        try {
            byte[] testData = "Hello".getBytes();
            SelectorThreadUtils.startSelectorThread(selectorThread);
            clients = initializeClients("localhost", PORT, 2);
            
            for(TCPIOClient client : clients) {
                client.send(testData);
                byte[] response = new byte[testData.length];
                client.receive(response);
                assertTrue(Arrays.equals(testData, response));
            }
        } finally {
            SelectorThreadUtils.stopSelectorThread(selectorThread);
            if (clients != null) {
                closeClients(clients);
            }
        }
    }
    
    public void testSeveralPackets() throws IOException {
        final SelectorThread selectorThread = createSelectorThread(PORT, 2);
        
        List<TCPIOClient> clients = null;
        
        try {
            SelectorThreadUtils.startSelectorThread(selectorThread);
            clients = initializeClients("localhost", PORT, 2);
            
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
            SelectorThreadUtils.stopSelectorThread(selectorThread);
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
    
    private SelectorThread createSelectorThread(int port, int selectorReadThreadsCount) {
        final SelectorThread selectorThread = new SelectorThread();
        selectorThread.setPort(port);
        selectorThread.setSelectorReadThreadsCount(selectorReadThreadsCount);
        selectorThread.setAlgorithmClassName(EchoStreamAlgorithm.class.getName());

        return selectorThread;
    }
}
