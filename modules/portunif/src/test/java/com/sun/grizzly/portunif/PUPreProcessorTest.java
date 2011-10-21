/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2007-2011 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.grizzly.portunif;

import com.sun.grizzly.Controller;
import com.sun.grizzly.DefaultProtocolChain;
import com.sun.grizzly.DefaultProtocolChainInstanceHandler;
import com.sun.grizzly.ProtocolChain;
import com.sun.grizzly.ProtocolFilter;
import com.sun.grizzly.SSLConfig;
import com.sun.grizzly.TCPSelectorHandler;
import com.sun.grizzly.filter.EchoFilter;
import com.sun.grizzly.portunif.utils.ControllerUtils;
import com.sun.grizzly.portunif.utils.NonBlockingIOClient;
import com.sun.grizzly.portunif.utils.NonBlockingSSLIOClient;
import com.sun.grizzly.portunif.utils.NonBlockingTCPIOClient;
import com.sun.grizzly.portunif.utils.Utils;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
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
    public void setUp() throws URISyntaxException {
        sslConfig = new SSLConfig();
        ClassLoader cl = getClass().getClassLoader();
        // override system properties
        URL cacertsUrl = cl.getResource("ppssltest-cacerts.jks");
        String trustStoreFile = new File(cacertsUrl.toURI()).getAbsolutePath();
        if (cacertsUrl != null) {
            sslConfig.setTrustStoreFile(trustStoreFile);
            sslConfig.setTrustStorePass("changeit");
        }

        logger.log(Level.INFO, "SSL certs path: " + trustStoreFile);

        // override system properties
        URL keystoreUrl = cl.getResource("ppssltest-keystore.jks");
        String keyStoreFile = new File(keystoreUrl.toURI()).getAbsolutePath();
        if (keystoreUrl != null) {
            sslConfig.setKeyStoreFile(keyStoreFile);
            sslConfig.setKeyStorePass("changeit");
        }

        logger.log(Level.INFO, "SSL keystore path: " + keyStoreFile);
        SSLConfig.DEFAULT_CONFIG = sslConfig;
    }

    public void testTLSPUPreProcessor() throws IOException {
        String protocolId = "QWER";
        PUReadFilter puReadFilter = new PUReadFilter();
        puReadFilter.addPreProcessor(new TLSPUPreProcessor(sslConfig));
        puReadFilter.addProtocolFinder(new SimpleProtocolFinder(protocolId));
        puReadFilter.addProtocolHandler(new SimpleProtocolHandler(protocolId));
        
        Controller controller = createController(PORT, puReadFilter);
        final int readThreadsCount = 5;
        controller.setReadThreadsCount(readThreadsCount);

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
    
    private Controller createController(int port, final PUReadFilter puReadFilter) {
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
                    protocolChain.addFilter(puReadFilter);
                    protocolChain.addFilter(echoFilter);
                }
                return protocolChain;
            }
        });
        
        return controller;
    }
}
