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
import com.sun.grizzly.filter.EchoFilter;
import com.sun.grizzly.filter.ParserProtocolFilter;
import com.sun.grizzly.filter.SSLEchoFilter;
import com.sun.grizzly.util.Utils;
import com.sun.grizzly.utils.ControllerUtils;
import com.sun.grizzly.utils.NonBlockingSSLIOClient;
import com.sun.grizzly.utils.NonBlockingTCPIOClient;
import junit.framework.TestCase;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Tests the default {@link Controller} configuration
 *
 * @author Alexey Stashok
 */
public class ProtocolParserTest extends TestCase {
    private static Logger logger = Logger.getLogger("grizzly.test");
    public static final int PORT = 17505;
    public static final int PACKETS_COUNT = 100;
    private SSLConfig sslConfig;
    
    public void testSimplePacket() throws IOException {
        Controller controller = createController(PORT);
        NonBlockingTCPIOClient client = new NonBlockingTCPIOClient("localhost", PORT);
        
        try {
            byte[] testData = "Hello".getBytes();
            ControllerUtils.startController(controller);
            client.connect();
            client.send(testData);
            
            Utils.dumpOut("Sleeping 5 seconds");
            try{
                Thread.sleep(5 * 1000); //Wait 5 second before sending the bytes
            } catch (Throwable t){
                
            }
            client.send(" Partial".getBytes());
            
            byte[] response = new byte["Hello Partial".length()];
            client.receive(response);
            assertTrue(Arrays.equals("Hello Partial".getBytes(), response));
        } finally {
            controller.stop();
            client.close();
        }
    }

    public void testSimplePacketUsingServerCache() throws IOException {
        Controller controller = createController(PORT, new CacheableSelectionKeyHandler( 10, 1 ) );
        NonBlockingTCPIOClient client = new NonBlockingTCPIOClient("localhost", PORT);

        try {
            byte[] testData = "Hello".getBytes();
            ControllerUtils.startController(controller);
            client.connect();
            client.send(testData);

            Utils.dumpOut("Sleeping 5 seconds");
            try{
                Thread.sleep(5 * 1000); //Wait 5 second before sending the bytes
            } catch (Throwable t){

            }
            client.send(" Partial".getBytes());

            byte[] response = new byte["Hello Partial".length()];
            client.receive(response);
            assertTrue(Arrays.equals("Hello Partial".getBytes(), response));
        } finally {
            controller.stop();
            client.close();
        }
    }

    public void testSimpleSSLPacket() throws IOException {
        Controller controller = createSSLController(PORT);
        NonBlockingSSLIOClient client = new NonBlockingSSLIOClient("localhost", PORT,sslConfig);
        try {
            byte[] testData = "Hello Partial".getBytes();
            ControllerUtils.startController(controller);
            client.connect();
            client.send(testData);
            byte[] response = new byte["Hello Partial".length()];
            client.receive(response);
            assertTrue(Arrays.equals("Hello Partial".getBytes(), response));
        } finally {
            controller.stop();
            client.close();
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

    private Controller createController(int port) {
        return createController( port, null );
    }

    private Controller createController(int port, SelectionKeyHandler selectionKeyHandler ) {
        final ProtocolFilter echoFilter = new EchoFilter();
        final ProtocolFilter parserProtocolFilter = createParserProtocolFilter();
        TCPSelectorHandler selectorHandler = new TCPSelectorHandler();
        selectorHandler.setPort(port);
        if( selectionKeyHandler != null )
            selectorHandler.setSelectionKeyHandler( selectionKeyHandler );
        
        final Controller controller = new Controller();
        
        controller.setSelectorHandler(selectorHandler);
        
        controller.setProtocolChainInstanceHandler(
                new DefaultProtocolChainInstanceHandler(){
            @Override
            public ProtocolChain poll() {
                ProtocolChain protocolChain = protocolChains.poll();
                if (protocolChain == null){
                    protocolChain = new DefaultProtocolChain();
                    protocolChain.addFilter(parserProtocolFilter);
                    protocolChain.addFilter(echoFilter);
                }
                return protocolChain;
            }
        });
        return controller;
    }
    private Controller createSSLController(int port) {
        final ProtocolFilter echoFilter = new SSLEchoFilter();
        final ParserProtocolFilter parserProtocolFilter = createParserProtocolFilter();
        parserProtocolFilter.setSSLConfig(sslConfig);
        SSLSelectorHandler selectorHandler = new SSLSelectorHandler();
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
                    protocolChain.addFilter(parserProtocolFilter);
                    protocolChain.addFilter(echoFilter);
                }
                return protocolChain;
            }
        });

        return controller;
    }
    private ParserProtocolFilter createParserProtocolFilter() {
            return new ParserProtocolFilter() {
            public ProtocolParser newProtocolParser() {
                return new ProtocolParser() {
                    private boolean isExpectingMoreData = false;
                    private ByteBuffer byteBuffer;
                    private Object message;

                    public boolean hasMoreBytesToParse() {
                        return false;
                    }

                    public boolean isExpectingMoreData() {
                        return isExpectingMoreData;
                    }

                    public Object getNextMessage() {
                        return message;
                    }

                    public boolean hasNextMessage() {
                        ByteBuffer dup = byteBuffer.duplicate();

                        if (byteBuffer.position() == 0){
                            isExpectingMoreData = true;
                            return false;
                        }

                        dup.flip();
                        byte[] testData = new byte[dup.remaining()];
                        dup.get(testData);
                        String testDataString = new String(testData);
                        if (testDataString.equals("Hello Partial")){
                            isExpectingMoreData = false;
                            message = testDataString;
                        } else {
                            isExpectingMoreData = true;
                        }
                        return !isExpectingMoreData;
                    }

                    public void startBuffer(ByteBuffer bb) {
                        byteBuffer = bb;
                    }

                    public boolean releaseBuffer() {
                        byteBuffer = null;
                        message = null;
                        return false;
                    }

                };
            }
        };
    }
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

}
