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

import com.sun.grizzly.SSLConfig;
import com.sun.grizzly.SSLConnectorHandler;
import com.sun.grizzly.http.utils.SSLEchoStreamAlgorithm;
import com.sun.grizzly.http.utils.SelectorThreadUtils;
import com.sun.grizzly.ssl.SSLSelectorThread;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import junit.framework.TestCase;

/**
 * Tests <code>Controller</code> in multithread read mode
 *
 * @author Alexey Stashok
 */
public class MultiReadSSLSelectorThreadTest extends TestCase {
    public static final int PORT = 18889;
    public static final int PACKETS_COUNT = 100;
    
    private static Logger logger = Logger.getLogger("grizzly.test");
    
    private SSLConfig sslConfig;
    
    @Override
    public void setUp() {
        sslConfig = new SSLConfig();
        ClassLoader cl = getClass().getClassLoader();
        // override system properties
        URL cacertsUrl = cl.getResource("ssltest-cacerts.jks");
        if (cacertsUrl != null) {
            sslConfig.setTrustStoreFile(cacertsUrl.getFile());
        }
        
        logger.log(Level.INFO, "SSL certs path: " + sslConfig.getTrustStoreFile());
        
        // override system properties
        URL keystoreUrl = cl.getResource("ssltest-keystore.jks");
        if (keystoreUrl != null) {
            sslConfig.setKeyStoreFile(keystoreUrl.getFile());
        }
        
        logger.log(Level.INFO, "SSL keystore path: " + sslConfig.getKeyStoreFile());
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
