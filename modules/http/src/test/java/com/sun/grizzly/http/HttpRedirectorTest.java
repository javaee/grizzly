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
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URISyntaxException;
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
    public static final int PACKETS_COUNT = 10;

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

        System.setProperty("javax.net.ssl.trustStore", trustStoreFile);
        System.setProperty("javax.net.ssl.trustStorePassword", "changeit");
        System.setProperty("javax.net.ssl.keyStore", keyStoreFile);
        System.setProperty("javax.net.ssl.keyStorePassword", "changeit");
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
                    HttpURLConnection connection = null;
                    
                    try {
                        URL url = new URL("http://localhost:" + PORT);
                        connection = 
                                (HttpURLConnection) url.openConnection();
                        connection.setReadTimeout(10000);
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

    public void testSlowHttpProtocolProcess() throws Exception {
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
                    InputStream is = null;
                    Socket s = null;
                    
                    try {
                        s = new Socket("localhost", PORT);
                        s.setSoTimeout(10000);
                        os = s.getOutputStream();
                        
                        os.write("POST / HTTP/1.1\r\n".getBytes());
                        os.flush();
                        Thread.sleep(50);

                        os.write("Connection: close\r\n".getBytes());
                        os.flush();
                        Thread.sleep(50);
                        
                        os.write(("Host: localhost:" + PORT + "\r\n").getBytes());
                        os.flush();
                        Thread.sleep(50);

                        os.write("\r\n".getBytes());
                        os.flush();
                        Thread.sleep(50);
                        
                        os.write(testString.getBytes());
                        os.flush();
                        
                        
                        is = s.getInputStream();
                        BufferedReader br = new BufferedReader(new InputStreamReader(is));
                        String line = br.readLine();
                        assertTrue(line.indexOf("200 OK") != -1);
                    } finally {
                        if (os != null) {
                            os.close();
                        }
                        
                        if (is != null) {
                            is.close();
                        }
                        
                        if (s != null) {
                            s.close();
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
                        connection.setReadTimeout(10000);
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
                    HttpsURLConnection connection = null;
                    try {
                        URL url = new URL("https://localhost:" + PORT);
                        connection = 
                                (HttpsURLConnection) url.openConnection();
                        connection.setReadTimeout(10000);
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
                        connection.setReadTimeout(10000);
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

    public void testSlowHttpProtocolRedirect() throws Exception {
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
                    InputStream is = null;
                    Socket s = null;
                    
                    try {
                        s = new Socket("localhost", PORT);
                        s.setSoTimeout(10000);
                        os = s.getOutputStream();
                        
                        os.write("POST / HTTP/1.1\r\n".getBytes());
                        os.flush();
                        Thread.sleep(20);

                        os.write("Connection: close\r\n".getBytes());
                        os.flush();
                        Thread.sleep(2);
                        
                        os.write(("Host: localhost:" + PORT + "\r\n").getBytes());
                        os.flush();
                        Thread.sleep(2);

                        os.write(("\r\n" + testString).getBytes());
                        os.flush();
                        
                        
                        is = s.getInputStream();
                        StringBuilder sb = new StringBuilder();
                        int c;
                        while ((c = is.read()) != -1) {
                            sb.append((char) c);
                            if (c == '\n') break;
                        }
                        
                        assertTrue(sb.toString(), sb.indexOf("HTTP/1.1 302 Moved Temporarily") != -1);
                    } finally {
                        if (os != null) {
                            os.close();
                        }
                        
                        if (is != null) {
                            is.close();
                        }
                        
                        if (s != null) {
                            s.close();
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
