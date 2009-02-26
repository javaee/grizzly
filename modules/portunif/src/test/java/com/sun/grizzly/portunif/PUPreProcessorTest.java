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
import com.sun.grizzly.SSLConfig;
import com.sun.grizzly.TCPSelectorHandler;
import com.sun.grizzly.filter.EchoFilter;
import com.sun.grizzly.utils.ControllerUtils;
import com.sun.grizzly.utils.NonBlockingIOClient;
import com.sun.grizzly.utils.NonBlockingSSLIOClient;
import com.sun.grizzly.utils.NonBlockingTCPIOClient;
import com.sun.grizzly.utils.Utils;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import junit.framework.TestCase;

/**
 *
 * @author Alexey Stashok
 */
public class PUPreProcessorTest extends TestCase {
    public static final int PORT = 18890;
    public static final int PACKETS_COUNT = 100;
  
    private static Logger logger = Logger.getLogger("grizzly.test");
    
    private SSLConfig sslConfig;
    
    
    @Override
    public void setUp() {
        sslConfig = new SSLConfig();
        ClassLoader cl = getClass().getClassLoader();
        // override system properties
        URL cacertsUrl = cl.getResource("ppssltest-cacerts.jks");
        if (cacertsUrl != null) {
            sslConfig.setTrustStoreFile(cacertsUrl.getFile());
        }
        
        logger.log(Level.INFO, "SSL certs path: " + sslConfig.getTrustStoreFile());
        
        // override system properties
        URL keystoreUrl = cl.getResource("ppssltest-keystore.jks");
        if (keystoreUrl != null) {
            sslConfig.setKeyStoreFile(keystoreUrl.getFile());
        }
        
        logger.log(Level.INFO, "SSL keystore path: " + sslConfig.getKeyStoreFile());
        SSLConfig.DEFAULT_CONFIG = sslConfig;
    }

    public void testTLSPUPreProcessor() throws IOException {
        String protocolId = "QWER";
        PUFilter puFilter = new PUFilter();
        puFilter.addPreProcessor(new TLSPUPreProcessor(sslConfig));
        puFilter.addProtocolFinder(new SimpleProtocolFinder(protocolId));
        puFilter.addProtocolHandler(new SimpleProtocolHandler(protocolId));
        
        Controller controller = createController(PORT, puFilter);
        controller.setReadThreadsCount(5);

        List<NonBlockingIOClient> clients = new ArrayList<NonBlockingIOClient>(2);
        
        try {
            ControllerUtils.startController(controller);
            
            NonBlockingIOClient client = new NonBlockingTCPIOClient("localhost", PORT);
            client.connect();
            clients.add(client);

            client = new NonBlockingSSLIOClient("localhost", PORT, sslConfig);
            client.connect();
            clients.add(client);

            for (int i = 0; i < PACKETS_COUNT; i++) {
                for(int j=0; j<clients.size(); j++) {
                    client = clients.get(j);
                    String testString = protocolId + ": Hello. Client#" + j + " Packet#" + i;
                    byte[] testData = testString.getBytes();
                    client.send(testData);
                    byte[] response = new byte[protocolId.length()];
                    client.receive(response);
                    String responseStr = new String(response);
                    assertEquals(Utils.reverseString(protocolId), 
                            responseStr);
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
    
    private void closeClients(List<NonBlockingIOClient> clients) {
        for(NonBlockingIOClient client : clients) {
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
