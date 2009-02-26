/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 1997-2007 Sun Microsystems, Inc. All rights reserved.
 * 
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License. You can obtain
 * a copy of the License at https://glassfish.dev.java.net/public/CDDL+GPL.html
 * or glassfish/bootstrap/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 * 
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/bootstrap/legal/LICENSE.txt.
 * Sun designates this particular file as subject to the "Classpath" exception
 * as provided by Sun in the GPL Version 2 section of the License file that
 * accompanied this code.  If applicable, add the following below the License
 * Header, with the fields enclosed by brackets [] replaced by your own
 * identifying information: "Portions Copyrighted [year]
 * [name of copyright owner]"
 * 
 * Contributor(s):
 * 
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
import com.sun.grizzly.http.portunif.HttpProtocolFinder;
import com.sun.grizzly.http.portunif.HttpProtocolHandler;
import com.sun.grizzly.http.utils.EmptyHttpStreamAlgorithm;
import com.sun.grizzly.http.utils.EmptyHttpsStreamAlgorithm;
import com.sun.grizzly.http.utils.SelectorThreadUtils;
import com.sun.grizzly.portunif.PUPreProcessor;
import com.sun.grizzly.portunif.ProtocolFinder;
import com.sun.grizzly.portunif.ProtocolHandler;
import com.sun.grizzly.portunif.TLSPUPreProcessor;
import com.sun.grizzly.ssl.SSLSelectorThread;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSession;
import junit.framework.TestCase;

/**
 *
 * @author Alexey Stashok
 */
public class HttpRedirectorTest extends TestCase {
    public static final int PORT = 18889;
    public static final int CLIENTS_COUNT = 2;
    public static final int PACKETS_COUNT = 50;

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
        sslConfig.publish(System.getProperties());
        
        EmptyHttpStreamAlgorithm.resetRequestCounter();
        
        System.setProperty("javax.net.ssl.trustStore", sslConfig.getTrustStoreFile());
        System.setProperty("javax.net.ssl.keyStore", sslConfig.getKeyStoreFile());
    }
    
    public void testHttpProtocolProcess() throws IOException {
        SelectorThread httpSelectorThread = createSelectorThread(PORT, 2);
        ProtocolFinder finder = new HttpProtocolFinder();
        
        ProtocolHandler handler = 
                new HttpProtocolHandler(HttpProtocolHandler.Mode.HTTPS);
        
        PUPreProcessor preProcessor = new TLSPUPreProcessor(sslConfig);
        
        List<ProtocolFinder> finders = Arrays.asList(finder);
        List<ProtocolHandler> handlers = Arrays.asList(handler);
        List<PUPreProcessor> preProcessors = Arrays.asList(preProcessor);

        httpSelectorThread.configurePortUnification(finders, handlers, preProcessors);
        
        try {
            SelectorThreadUtils.startSelectorThread(httpSelectorThread);
            
            for(int i=0; i<PACKETS_COUNT; i++) {
                for(int j=0; j<CLIENTS_COUNT; j++) {
                    String testString = "Hello. Client#" + j + " Packet#" + i;
                    OutputStream os = null;
                    DataInputStream is = null;
                    
                    try {
                        URL url = new URL("http://localhost:" + PORT);
                        HttpURLConnection connection = 
                                (HttpURLConnection) url.openConnection();
                        connection.setRequestMethod("POST");
                        connection.setDoOutput(true);
                        os = connection.getOutputStream();
                        os.write(testString.getBytes());
                        os.flush();
                        assertEquals(200, connection.getResponseCode());
                        is = new DataInputStream(connection.getInputStream());

                        is.readInt();
                    } finally {
                        if (os != null) {
                            os.close();
                        }
                        
                        if (is != null) {
                            is.close();
                        }
                    }
                }
            }
        } finally {
            SelectorThreadUtils.stopSelectorThread(httpSelectorThread);
        }
    }

    public void testHttpsProtocolRedirect() throws IOException {
        SelectorThread httpSelectorThread = createSelectorThread(PORT, 2);
        ProtocolFinder finder = new HttpProtocolFinder();
        
        ProtocolHandler handler = 
                new HttpProtocolHandler(HttpProtocolHandler.Mode.HTTPS);
        
        PUPreProcessor preProcessor = new TLSPUPreProcessor(sslConfig);
        
        List<ProtocolFinder> finders = Arrays.asList(finder);
        List<ProtocolHandler> handlers = Arrays.asList(handler);
        List<PUPreProcessor> preProcessors = Arrays.asList(preProcessor);

        httpSelectorThread.configurePortUnification(finders, handlers, preProcessors);
        
        try {
            SelectorThreadUtils.startSelectorThread(httpSelectorThread);
            
            HostnameVerifier hv = new HostnameVerifier() {
                public boolean verify(String urlHostName, SSLSession session) {
                    return true;
                }
            };
            HttpsURLConnection.setDefaultHostnameVerifier(hv);
        
            for(int i=0; i<PACKETS_COUNT; i++) {
                for(int j=0; j<CLIENTS_COUNT; j++) {
                    String testString = "Hello. Client#" + j + " Packet#" + i;
                    OutputStream os = null;
                    DataInputStream is = null;
                    HttpsURLConnection connection = null;
                    
                    try {
                        URL url = new URL("https://localhost:" + PORT);
                        connection = 
                                (HttpsURLConnection) url.openConnection();
                        connection.setRequestMethod("POST");
                        connection.setDoOutput(true);
                        os = connection.getOutputStream();
                        os.write(testString.getBytes());
                        os.flush();
                        assertEquals(302, connection.getResponseCode());
                    } finally {
                        if (os != null) {
                            os.close();
                        }
                        
                        if (is != null) {
                            is.close();
                        }
                        
                        if (connection != null) {
                            connection.disconnect();
                        }
                    }
                }
            }
        } finally {
            SelectorThreadUtils.stopSelectorThread(httpSelectorThread);
        }
    }

    public void testHttpsProtocolProcess() throws IOException {
        SelectorThread httpSelectorThread = createSSLSelectorThread(PORT, 2);
        ProtocolFinder finder = new HttpProtocolFinder();
        
        ProtocolHandler handler = 
                new HttpProtocolHandler(HttpProtocolHandler.Mode.HTTP);
        
        PUPreProcessor preProcessor = new TLSPUPreProcessor(sslConfig);
        
        List<ProtocolFinder> finders = Arrays.asList(finder);
        List<ProtocolHandler> handlers = Arrays.asList(handler);
        List<PUPreProcessor> preProcessors = Arrays.asList(preProcessor);

        httpSelectorThread.configurePortUnification(finders, handlers, preProcessors);
        
        try {
            SelectorThreadUtils.startSelectorThread(httpSelectorThread);
            
            HostnameVerifier hv = new HostnameVerifier() {
                public boolean verify(String urlHostName, SSLSession session) {
                    return true;
                }
            };
            HttpsURLConnection.setDefaultHostnameVerifier(hv);

            for(int i=0; i<PACKETS_COUNT; i++) {
                for(int j=0; j<CLIENTS_COUNT; j++) {
                    String testString = "Hello. Client#" + j + " Packet#" + i;
                    OutputStream os = null;
                    DataInputStream is = null;
                    
                    try {
                        URL url = new URL("https://localhost:" + PORT);
                        HttpsURLConnection connection = 
                                (HttpsURLConnection) url.openConnection();
                        connection.setRequestMethod("POST");
                        connection.setDoOutput(true);
                        os = connection.getOutputStream();
                        os.write(testString.getBytes());
                        os.flush();
                        assertEquals(200, connection.getResponseCode());
                        is = new DataInputStream(connection.getInputStream());

                        is.readInt();
                    } finally {
                        if (os != null) {
                            os.close();
                        }
                        
                        if (is != null) {
                            is.close();
                        }
                    }
                }
            }
        } finally {
            SelectorThreadUtils.stopSelectorThread(httpSelectorThread);
        }        
    }

    public void testHttpProtocolRedirect() throws IOException {
        SelectorThread httpSelectorThread = createSSLSelectorThread(PORT, 2);
        ProtocolFinder finder = new HttpProtocolFinder();
        
        ProtocolHandler handler = 
                new HttpProtocolHandler(HttpProtocolHandler.Mode.HTTP);
        
        PUPreProcessor preProcessor = new TLSPUPreProcessor(sslConfig);
        
        List<ProtocolFinder> finders = Arrays.asList(finder);
        List<ProtocolHandler> handlers = Arrays.asList(handler);
        List<PUPreProcessor> preProcessors = Arrays.asList(preProcessor);

        httpSelectorThread.configurePortUnification(finders, handlers, preProcessors);
        
        try {
            SelectorThreadUtils.startSelectorThread(httpSelectorThread);
            
            for(int i=0; i<PACKETS_COUNT; i++) {
                for(int j=0; j<CLIENTS_COUNT; j++) {
                    String testString = "Hello. Client#" + j + " Packet#" + i;
                    OutputStream os = null;
                    DataInputStream is = null;
                    HttpURLConnection connection = null;
                    
                    try {
                        URL url = new URL("http://localhost:" + PORT);
                        connection = 
                                (HttpURLConnection) url.openConnection();
                        connection.setRequestMethod("POST");
                        connection.setDoOutput(true);
                        os = connection.getOutputStream();
                        os.write(testString.getBytes());
                        os.flush();
                        assertEquals(302, connection.getResponseCode());
                    } finally {
                        if (os != null) {
                            os.close();
                        }
                        
                        if (is != null) {
                            is.close();
                        }
                        
                        if (connection != null) {
                            connection.disconnect();
                        }
                    }
                }
            }
        } finally {
            SelectorThreadUtils.stopSelectorThread(httpSelectorThread);
        }
    }
    
    private SelectorThread createSelectorThread(int port, int selectorReadThreadsCount) {
        final SelectorThread selectorThread = new SelectorThread();
        selectorThread.setPort(port);
        selectorThread.setSelectorReadThreadsCount(selectorReadThreadsCount);
        selectorThread.setAlgorithmClassName(EmptyHttpStreamAlgorithm.class.getName());

        return selectorThread;
    }
    
    private SelectorThread createSSLSelectorThread(int port, int selectorReadThreadsCount) {
        final SSLSelectorThread selectorThread = new SSLSelectorThread();
        selectorThread.setPort(port);
        selectorThread.setSelectorReadThreadsCount(selectorReadThreadsCount);
        selectorThread.setAlgorithmClassName(EmptyHttpsStreamAlgorithm.class.getName());
        selectorThread.setSSLConfig(sslConfig);
        
        return selectorThread;
    }
}
