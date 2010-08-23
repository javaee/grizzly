/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2010 Oracle and/or its affiliates. All rights reserved.
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
import com.sun.grizzly.ssl.SSLSelectorThread;
import com.sun.grizzly.tcp.Adapter;
import com.sun.grizzly.tcp.Request;
import com.sun.grizzly.tcp.Response;
import com.sun.grizzly.util.Utils;
import com.sun.grizzly.util.buf.ByteChunk;
import com.sun.grizzly.util.net.jsse.JSSEImplementation;

import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
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
public class AsyncHTTPResponseTest extends TestCase {
    private static final Logger logger = Logger.getLogger("grizzly.test");

    public static final int PORT = 18890;

    private static final byte[] abcd = new byte[] {'a', 'b', 'c', 'd'};

    public void testHTTPSimpleAsyncResponse() throws Exception {
        int responseLength = 1024 * 1024;

        SelectorThread selectorThread = new SelectorThread();
        try {
            selectorThread.setPort(PORT);
            selectorThread.setAsyncHttpWriteEnabled(true);
            selectorThread.setAdapter(
                    new BigResponseAdapter(responseLength/1024, responseLength));
            selectorThread.listen();

            long start = System.currentTimeMillis();
            HttpURLConnection connection = (HttpURLConnection)
                    new URL("http://localhost:" + PORT).openConnection();

            int code = connection.getResponseCode();
            assertEquals(code, 200);

            int length = connection.getContentLength();
            long time = System.currentTimeMillis() - start;
            Utils.dumpOut("Took " + time + "ms to complete.");
            assertEquals(length, responseLength);

            byte[] content = new byte[length];

            int readBytes;
            int offset = 0;
            do {
                readBytes = connection.getInputStream().read(content, offset,
                        length - offset);
                offset += readBytes;
            } while(readBytes != -1 && offset < length);

            assertEquals(offset, length);

            checkResult(content);
        } finally {
            selectorThread.stopEndpoint();
        }
    }

    public void testHTTPSSimpleAsyncResponse() throws Exception {
        int responseLength = 5 * 1024 * 1024;

        SSLConfig sslConfig = configureSSL();

        SSLSelectorThread selectorThread = new SSLSelectorThread();
        selectorThread.setSSLConfig(sslConfig);
        try {
            selectorThread.setSSLImplementation(new JSSEImplementation());
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        
        try {
            selectorThread.setPort(PORT);
            selectorThread.setAsyncHttpWriteEnabled(true);
            selectorThread.setAdapter(
                    new BigResponseAdapter(responseLength/1024, responseLength));
            selectorThread.listen();

            HostnameVerifier hv = new HostnameVerifier() {
                public boolean verify(String urlHostName, SSLSession session) {
                    return true;
                }
            };
            HttpsURLConnection.setDefaultHostnameVerifier(hv);

            long start = System.currentTimeMillis();
            HttpsURLConnection connection = (HttpsURLConnection)
                    new URL("https://localhost:" + PORT).openConnection();

            int code = connection.getResponseCode();
            assertEquals(code, 200);

            int length = connection.getContentLength();
            long time = System.currentTimeMillis() - start;
            Utils.dumpOut("Took " + time + "ms to complete.");
            assertEquals(length, responseLength);

            byte[] content = new byte[length];

            int readBytes;
            int offset = 0;
            InputStream is = connection.getInputStream();
            do {
                readBytes = is.read(content, offset,
                        length - offset);
                offset += readBytes;
            } while(readBytes != -1 && offset < length);

            assertEquals(length, offset);

            checkResult(content);
        } finally {
            selectorThread.stopEndpoint();
        }
    }

    public static class BigResponseAdapter implements Adapter {
        private final int bufferSize;
        private final int length;

        public BigResponseAdapter(int bufferSize, int length) {
            this.bufferSize = bufferSize;
            this.length = length;
        }

        public void service(Request req, Response res) throws Exception {
            res.setStatus(200);
            res.setContentLength(length);
            res.setContentType("text/html");
            
            int remaining = length;
            while(remaining > 0) {
                int sizeToSend = Math.min(remaining, bufferSize);
                ByteChunk chunk = new ByteChunk(sizeToSend);

                byte[] content = new byte[sizeToSend];
                for (int i = 0; i < sizeToSend; i++) {
                    content[i] = abcd[i % abcd.length];
                }

                
                chunk.append(content, 0, sizeToSend);
                res.getOutputBuffer().doWrite(chunk, res);
                remaining -= sizeToSend;
            }
        }

        public void afterService(Request req, Response res) throws Exception {
        }
    }

    private static boolean checkResult(byte[] content) {
        for (int i=0; i<content.length; i++) {
            if (content[i] != abcd[i % abcd.length]) {
                return false;
            }
        }

        return true;
    }


    private SSLConfig configureSSL() throws URISyntaxException {
        SSLConfig         sslConfig = new SSLConfig();
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

        return sslConfig;
    }
}
