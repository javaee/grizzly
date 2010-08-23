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

package com.sun.grizzly.http;

import com.sun.grizzly.SSLConfig;
import com.sun.grizzly.SSLConnectorHandler;
import com.sun.grizzly.http.utils.SSLEchoStreamAlgorithm;
import com.sun.grizzly.http.utils.SelectorThreadUtils;
import com.sun.grizzly.ssl.SSLSelectorThread;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import junit.framework.TestCase;

/**
 * Tests {@link Controller} in multithread read mode
 *
 * @author Alexey Stashok
 */
public class MultiReadSSLSelectorThreadTest extends TestCase {
    public static final int PORT = 18889;
    public static final int PACKETS_COUNT = 100;
    
    private static Logger logger = Logger.getLogger("grizzly.test");
    
    private SSLConfig sslConfig;
    
    @Override
    public void setUp() throws URISyntaxException {
        sslConfig = new SSLConfig();
        ClassLoader cl = getClass().getClassLoader();
        // override system properties
        URL cacertsUrl = cl.getResource("ssltest-cacerts.jks");
        String trustStoreFile = new File(cacertsUrl.toURI()).getAbsolutePath();
        if (cacertsUrl != null) {
            sslConfig.setTrustStoreFile(trustStoreFile);
            sslConfig.setTrustStorePass("changeit");
        }

        logger.log(Level.INFO, "SSL certs path: " + trustStoreFile);

        // override system properties
        URL keystoreUrl = cl.getResource("ssltest-keystore.jks");
        String keyStoreFile = new File(keystoreUrl.toURI()).getAbsolutePath();
        if (keystoreUrl != null) {
            sslConfig.setKeyStoreFile(keyStoreFile);
            sslConfig.setKeyStorePass("changeit");
        }

        logger.log(Level.INFO, "SSL keystore path: " + keyStoreFile);
        SSLConfig.DEFAULT_CONFIG = sslConfig;
    }
    
    public void testSimplePacket() throws IOException {
        final SelectorThread selectorThread = createSelectorThread(PORT, 2);
        
        List<SSLConnectorHandler> clients = null;
        
        try {
            String testData = "Hello";
            ByteBuffer testDataBuffer = ByteBuffer.wrap(testData.getBytes());
            SelectorThreadUtils.startSelectorThread(selectorThread);
            clients = initializeClients("localhost", PORT, 2);
            
            for(SSLConnectorHandler client : clients) {
                testDataBuffer.clear();
                client.write(testDataBuffer, true);
                ByteBuffer response = ByteBuffer.allocate(client.getApplicationBufferSize());
                
                while(response.position() < testDataBuffer.capacity()) {
                    long read = client.read(response, true);
                    if (read == -1) throw new EOFException("Unexpected EOF");
                }
                
                response.flip();
                byte[] responseBytes = new byte[response.remaining()];
                response.get(responseBytes);
                
                
                assertEquals(testData, new String(responseBytes));
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
        
        List<SSLConnectorHandler> clients = null;
        
        try {
            SelectorThreadUtils.startSelectorThread(selectorThread);
            clients = initializeClients("localhost", PORT, 2);
            
            for(int i=0; i<PACKETS_COUNT; i++) {
                for(int j=0; j<clients.size(); j++) {
                    SSLConnectorHandler client = clients.get(j);
                    String testString = "Hello. Client#" + j + " Packet#" + i;
                    ByteBuffer testDataBuffer = ByteBuffer.wrap(testString.getBytes());
                    client.write(testDataBuffer, true);

                    ByteBuffer response = ByteBuffer.allocate(client.getApplicationBufferSize());
                    while(response.position() < testDataBuffer.capacity()) {
                        long read = client.read(response, true);
                        if (read == -1) throw new EOFException("Unexpected EOF");
                    }
                
                    response.flip();
                    byte[] responseBytes = new byte[response.remaining()];
                    response.get(responseBytes);
                    
                    assertEquals(testString, new String(responseBytes));
                }
            }
        } finally {
            SelectorThreadUtils.stopSelectorThread(selectorThread);
            if (clients != null) {
                closeClients(clients);
            }
        }
    }
    
    private List<SSLConnectorHandler> initializeClients(String host, int port, int count) throws IOException {
        List<SSLConnectorHandler> clients = new ArrayList<SSLConnectorHandler>(count);
        for(int i=0; i<count; i++) {
            SSLConnectorHandler client = new SSLConnectorHandler(sslConfig);
            client.connect(new InetSocketAddress(host, port));
            ByteBuffer bb = ByteBuffer.allocate(client.getApplicationBufferSize());
            client.handshake(bb, true);
            clients.add(client);
        }
        
        return clients;
    }
    
    private void closeClients(List<SSLConnectorHandler> clients) {
        for(SSLConnectorHandler client : clients) {
            try {
                client.close();
            } catch (IOException ex) {
            }
        }
    }
    
    private SelectorThread createSelectorThread(int port, int selectorReadThreadsCount) {
        final SSLSelectorThread selectorThread = new SSLSelectorThread();
        selectorThread.setPort(port);
        selectorThread.setSelectorReadThreadsCount(selectorReadThreadsCount);
        selectorThread.setAlgorithmClassName(SSLEchoStreamAlgorithm.class.getName());
        selectorThread.setSSLConfig(sslConfig);
        
        return selectorThread;
    }
}
