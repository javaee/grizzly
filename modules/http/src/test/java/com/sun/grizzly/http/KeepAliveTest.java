/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010 Oracle and/or its affiliates. All rights reserved.
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

import com.sun.grizzly.ControllerStateListenerAdapter;
import com.sun.grizzly.SSLConfig;
import com.sun.grizzly.http.utils.SSLEchoStreamAlgorithm;
import com.sun.grizzly.ssl.SSLSelectorThread;
import com.sun.grizzly.tcp.StaticResourcesAdapter;
import junit.framework.Assert;
import junit.framework.TestCase;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Level;

public class KeepAliveTest extends TestCase {
    private static final int PORT = 50000;
    private SelectorThread thread;
    private final int timeOut = new Random().nextInt(20) + 10;

    private void setUpHttp() throws IOException, InstantiationException {
        thread = selectorThread();
        thread.setPort(PORT);
        thread.setKeepAliveTimeoutInSeconds(timeOut);
        thread.setTcpNoDelay(true);
        setRootFolder(thread);
        thread.listen();
    }

    private SelectorThread selectorThread() {
        return new SelectorThread() {

            @Override
            public void listen() throws IOException, InstantiationException {
                initEndpoint();
                final CountDownLatch latch = new CountDownLatch(1);
                controller.addStateListener(new ControllerStateListenerAdapter() {

                    @Override
                    public void onReady() {
                        enableMonitoring();
                        latch.countDown();
                    }

                    @Override
                    public void onException(Throwable e) {
                        if (latch.getCount() > 0) {
                            logger().log(Level.SEVERE, "Exception during " +
                                    "starting the controller", e);
                            latch.countDown();
                        } else {
                            logger().log(Level.SEVERE, "Exception during " +
                                    "controller processing", e);
                        }
                    }
                });

                start();

                try {
                    latch.await();
                } catch (InterruptedException ex) {
                }

                if (!controller.isStarted()) {
                    throw new IllegalStateException("Controller is not started!");
                }
            }
        };
    }

    private SSLSelectorThread sslSelectorThread() {
        return new SSLSelectorThread() {
            {
                setSSLConfig(sslConfig());
                setAlgorithmClassName(SSLEchoStreamAlgorithm.class.getName());
            }
            @Override
            public void listen() throws IOException, InstantiationException {
                initEndpoint();
                final CountDownLatch latch = new CountDownLatch(1);
                controller.addStateListener(new ControllerStateListenerAdapter() {

                    @Override
                    public void onReady() {
                        enableMonitoring();
                        latch.countDown();
                    }

                    @Override
                    public void onException(Throwable e) {
                        if (latch.getCount() > 0) {
                            logger().log(Level.SEVERE, "Exception during " +
                                    "starting the controller", e);
                            latch.countDown();
                        } else {
                            logger().log(Level.SEVERE, "Exception during " +
                                    "controller processing", e);
                        }
                    }
                });

                start();

                try {
                    latch.await();
                } catch (InterruptedException ex) {
                }

                if (!controller.isStarted()) {
                    throw new IllegalStateException("Controller is not started!");
                }
            }
        };
    }

    private void setUpHttps() throws IOException, InstantiationException {
        thread = sslSelectorThread();
        thread.setPort(PORT);
        thread.setKeepAliveTimeoutInSeconds(timeOut);
        thread.setTcpNoDelay(true);
        setRootFolder(thread);
        thread.listen();
    }

    private SSLConfig sslConfig() {
        try {
            SSLConfig sslConfig = new SSLConfig();
            ClassLoader cl = getClass().getClassLoader();
            // override system properties
            URL cacertsUrl = cl.getResource("ssltest-cacerts.jks");
            String trustStoreFile = new File(cacertsUrl.toURI()).getAbsolutePath();
            if (cacertsUrl != null) {
                sslConfig.setTrustStoreFile(trustStoreFile);
                sslConfig.setTrustStorePass("changeit");
            }

            // override system properties
            URL keystoreUrl = cl.getResource("ssltest-keystore.jks");
            String keyStoreFile = new File(keystoreUrl.toURI()).getAbsolutePath();
            if (keystoreUrl != null) {
                sslConfig.setKeyStoreFile(keyStoreFile);
                sslConfig.setKeyStorePass("changeit");
            }

            SSLConfig.DEFAULT_CONFIG = sslConfig;

            return sslConfig;
        } catch (URISyntaxException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    /**
     * Tears down the fixture, for example, close a network connection.
     * This method is called after a test is executed.
     */
    @Override
    protected void tearDown() throws Exception {
        thread.stopEndpoint();
    }

    public void testKeepAlive() throws IOException, InterruptedException, InstantiationException {
        setUpHttp();
        BufferedReader reader = null;
        Socket sock = new Socket("localhost", PORT);
        try {
            sock.setSoTimeout(50000);
            OutputStream os = sock.getOutputStream();
            os.write("GET /index.html HTTP/1.1\r\nHost: localhost\r\n\r\n".getBytes());
            long start = System.currentTimeMillis();
            reader = new BufferedReader(new InputStreamReader(sock.getInputStream()));
            while (reader.readLine() != null) {
            }
            long end = System.currentTimeMillis();
            Assert.assertTrue("Timeout should be " + timeOut + " seconds or longer", end - start >= timeOut * 1000);
        } catch (IOException e) {
            e.printStackTrace();
            Assert.fail(e.getMessage());
        } finally {
            if (sock != null) {
                sock.close();
            }
            if (reader != null) {
                reader.close();
            }
        }
    }
    
    public void testKeepAliveSSL() throws IOException, InterruptedException, InstantiationException {
        setUpHttps();
        BufferedReader reader = null;
        SSLSocketFactory sslsocketfactory = getSSLSocketFactory();
        SSLSocket sock = (SSLSocket) sslsocketfactory.createSocket("localhost", PORT);

        try {
            sock.setSoTimeout(50000);
            OutputStream os = sock.getOutputStream();
            os.write("GET /index.html HTTP/1.1\r\nHost: localhost\r\n\r\n".getBytes());
            long start = System.currentTimeMillis();
            reader = new BufferedReader(new InputStreamReader(sock.getInputStream()));
            while (reader.readLine() != null) {
            }
            long end = System.currentTimeMillis();
            Assert.assertTrue("Timeout should be " + timeOut + " seconds or longer", end - start >= timeOut * 1000);
        } catch (IOException e) {
            e.printStackTrace();
            Assert.fail(e.getMessage());
        } finally {
            if (sock != null) {
                sock.close();
            }
            if (reader != null) {
                reader.close();
            }
        }
    }

    protected void setRootFolder(SelectorThread thread) {
        final String path = System.getProperty("java.io.tmpdir", "/tmp");
        File dir = new File(path);
        dir.mkdirs();

        final StaticResourcesAdapter adapter = new StaticResourcesAdapter(path);
        FileWriter writer;
        try {
            writer = new FileWriter(new File(dir, "index.html"));
            try {
                writer.write("<html><body>You've found the server</body></html>");
                writer.flush();
            } finally {
                if (writer != null) {
                    writer.close();
                }
            }
        } catch (IOException e) {
            Assert.fail(e.getMessage());
        }
        thread.setAdapter(adapter);
    }
    public SSLSocketFactory getSSLSocketFactory() throws IOException {
        try {
            //---------------------------------
            // Create a trust manager that does not validate certificate chains
            TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() {
                        return null;
                    }

                    public void checkClientTrusted(
                        X509Certificate[] certs, String authType) {
                    }

                    public void checkServerTrusted(
                        X509Certificate[] certs, String authType) {
                    }
                }
            };
            // Install the all-trusting trust manager
            SSLContext sc = SSLContext.getInstance("SSL");
            sc.init(null, trustAllCerts, new SecureRandom());
            //---------------------------------
            return sc.getSocketFactory();
        } catch (Exception e) {
            e.printStackTrace();
            throw new IOException(e.getMessage());
        } finally {
        }
    }

}
