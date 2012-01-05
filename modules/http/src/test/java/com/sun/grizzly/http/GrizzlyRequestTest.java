/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2011 Oracle and/or its affiliates. All rights reserved.
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

import org.junit.Test;
import com.sun.grizzly.SSLConfig;
import com.sun.grizzly.http.utils.SelectorThreadUtils;
import com.sun.grizzly.ssl.SSLSelectorThread;
import com.sun.grizzly.tcp.http11.GrizzlyAdapter;
import com.sun.grizzly.tcp.http11.GrizzlyRequest;
import com.sun.grizzly.tcp.http11.GrizzlyResponse;
import com.sun.grizzly.util.ExtendedThreadPool;
import com.sun.grizzly.util.LoggerUtils;
import com.sun.grizzly.util.Utils;
import com.sun.grizzly.util.net.jsse.JSSEImplementation;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSession;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import static org.junit.Assert.*;


@RunWith(Parameterized.class)
public class GrizzlyRequestTest {

    boolean isBufferResponse;
            
    private SSLConfig sslConfig;
    private SelectorThread st;
    private static final int PORT = 7333;
    private Logger logger = LoggerUtils.getLogger();
    private String trustStoreFile;
    private String keyStoreFile;
    private AtomicReference<Throwable> error = new AtomicReference<Throwable>();

    public GrizzlyRequestTest(boolean isBufferResponse) {
        this.isBufferResponse = isBufferResponse;
    }

    @Parameters
    public static Collection<Object[]> getSslParameter() {
        return Arrays.asList(new Object[][]{
                    {Boolean.FALSE},
                    {Boolean.TRUE}
        });
    }
    
    @Before
    public void setUp() throws URISyntaxException {
        sslConfig = new SSLConfig();
        ClassLoader cl = getClass().getClassLoader();
        // override system properties
        URL cacertsUrl = cl.getResource("ssltest-cacerts.jks");
        if (cacertsUrl != null) {
            trustStoreFile = new File(cacertsUrl.toURI()).getAbsolutePath();
            sslConfig.setTrustStoreFile(trustStoreFile);
            sslConfig.setTrustStorePass("changeit");
            logger.log(Level.INFO, "SSL certs path: {0}", trustStoreFile);
        }

        // override system properties
        URL keystoreUrl = cl.getResource("ssltest-keystore.jks");
        if (keystoreUrl != null) {
            keyStoreFile = new File(keystoreUrl.toURI()).getAbsolutePath();
            sslConfig.setKeyStoreFile(keyStoreFile);
            sslConfig.setKeyStorePass("changeit");

        }
        logger.log(Level.INFO, "SSL keystore path: {0}", keyStoreFile);

        SSLConfig.DEFAULT_CONFIG = sslConfig;

        System.setProperty("javax.net.ssl.trustStore", trustStoreFile);
        System.setProperty("javax.net.ssl.trustStorePassword", "changeit");
        System.setProperty("javax.net.ssl.keyStore", keyStoreFile);
        System.setProperty("javax.net.ssl.keyStorePassword", "changeit");

    }


    // ------------------------------------------------------------ Test Methods


    @Test
    public void testGetUserPrincipalSSL() throws Exception {
        createSSLSelectorThread();
        try {
            HostnameVerifier hv = new HostnameVerifier() {
                public boolean verify(String urlHostName, SSLSession session) {
                    return true;
                }
            };
            HttpsURLConnection.setDefaultHostnameVerifier(hv);

            HttpsURLConnection connection = null;
            InputStream in = null;
            try {
                URL url = new URL("https://localhost:" + PORT);
                connection =
                        (HttpsURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setDoOutput(true);
                in = connection.getInputStream();
                BufferedReader r = new BufferedReader(new InputStreamReader(in));
                boolean found = false;
                for (String l = r.readLine(); l != null; l = r.readLine()) {
                    if (l.length() > 0 && l.contains("Principal not null: true")) {
                        found = true;
                    }
                }
                assertEquals(200, connection.getResponseCode());
                assertTrue("GrizzlyRequest.getUserPrincipal() returned null.", found);
            } finally {
                if (in != null) {
                    in.close();
                }

                if (connection != null) {
                    connection.disconnect();
                }
            }
        } finally {
            SelectorThreadUtils.stopSelectorThread(st);
        }
    }

    @Test
    public void testParametersAvailable() throws Throwable {
        try {
            createSelectorThread(new InputAdapter());
            URL url = new URL("http://localhost:" + PORT + "/path");
            HttpURLConnection c = (HttpURLConnection) url.openConnection();
            c.setDoOutput(true);
            c.setRequestMethod("POST");
            c.setRequestProperty("content-type", "application/x-www-form-urlencoded");
            OutputStream out = c.getOutputStream();
            out.write("param1=value1".getBytes());
            out.flush();
            out.close();

            Thread.sleep(100);
            Throwable t = error.get();
            if (t != null) {
                throw t;
            }
        } finally {
            SelectorThreadUtils.stopSelectorThread(st);
        }
    }

    @Test
    public void testParametersAvailable2() throws Throwable {
        try {
            createSelectorThread(new InputAdapter2());
            URL url = new URL("http://localhost:" + PORT + "/path");
            HttpURLConnection c = (HttpURLConnection) url.openConnection();
            c.setDoOutput(true);
            c.setRequestMethod("POST");
            c.setRequestProperty("content-type", "application/x-www-form-urlencoded");
            OutputStream out = c.getOutputStream();
            out.write("param1=value1".getBytes());
            out.flush();
            out.close();

            Thread.sleep(100);
            Throwable t = error.get();
            if (t != null) {
                throw t;
            }
        } finally {
            SelectorThreadUtils.stopSelectorThread(st);
        }
    }

    @Test
    public void testReaderCharacterEncoding() throws Throwable {
        final String msg = "\u043F\u0440\u0438\u0432\u0435\u0442";
        
        try {
            createSelectorThread(new GrizzlyAdapter() {

                @Override
                public void service(GrizzlyRequest request,
                        GrizzlyResponse response) throws Exception {
                    request.setCharacterEncoding("UTF-16");

                    BufferedReader r = request.getReader();
                    String gotMsg = r.readLine();
                    if (msg.equals(gotMsg)) {
                        response.getWriter().write("OK");
                    } else {
                        response.setStatus(500, "Unexpected msg: " + gotMsg);
                    }
                }
            });
            URL url = new URL("http://localhost:" + PORT + "/path");
            HttpURLConnection c = (HttpURLConnection) url.openConnection();
            c.setDoOutput(true);
            c.setRequestMethod("POST");
            c.setRequestProperty("content-type", "text/plain;charset=UTF-16");
            OutputStream out = c.getOutputStream();
            out.write((msg + "\r\n").getBytes("UTF-16"));
            out.flush();
            out.close();

            assertEquals("Status: " + c.getResponseMessage(), 200, c.getResponseCode());
            
        } finally {
            SelectorThreadUtils.stopSelectorThread(st);
        }
    }
    // --------------------------------------------------------- Private Methods


    public void createSSLSelectorThread() throws Exception {
        final SSLSelectorThread sslt = new SSLSelectorThread();
        st = sslt;
        st.setBufferResponse(isBufferResponse);
        st.setPort(PORT);
        st.setAdapter(new MyAdapter());
        st.setDisplayConfiguration(Utils.VERBOSE_TESTS);
        st.setFileCacheIsEnabled(false);
        st.setLargeFileCacheEnabled(false);
        st.setBufferResponse(false);
        st.setKeepAliveTimeoutInSeconds(2000);

        st.initThreadPool();
        ((ExtendedThreadPool) st.getThreadPool()).setMaximumPoolSize(50);

        st.setBufferSize(32768);
        st.setMaxKeepAliveRequests(8196);
        st.setWebAppRootPath("/dev/null");
        sslt.setSSLConfig(sslConfig);
        
        try {
            sslt.setSSLImplementation(new JSSEImplementation());
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
          st.enableMonitoring();
        sslt.setNeedClientAuth(true);
        st.listen();


    }


     public void createSelectorThread(final GrizzlyAdapter adapter) throws Exception {
        st = new SelectorThread();
        st.setBufferResponse(isBufferResponse);
        st.setPort(PORT);
        st.setAdapter(adapter);
        st.setDisplayConfiguration(Utils.VERBOSE_TESTS);
        st.setFileCacheIsEnabled(false);
        st.setLargeFileCacheEnabled(false);
        st.setBufferResponse(false);
        st.setKeepAliveTimeoutInSeconds(2000);

        st.initThreadPool();
        ((ExtendedThreadPool) st.getThreadPool()).setMaximumPoolSize(50);

        st.setBufferSize(32768);
        st.setMaxKeepAliveRequests(8196);
        st.setWebAppRootPath("/dev/null");
        st.enableMonitoring();
        st.listen();
    }


    // ---------------------------------------------------------- Nested Classes

    private final class InputAdapter extends GrizzlyAdapter {

        @Override
        public void service(GrizzlyRequest request, GrizzlyResponse response) {
            try {
                request.getInputStream();
                Map params = request.getParameterMap();
                assertTrue("param1 not available", params.containsKey("param1"));
                assertEquals("value1", request.getParameter("param1"));
            } catch (Throwable t) {
                error.set(t);
            }
        }
    }

    private final class InputAdapter2 extends GrizzlyAdapter {

        @Override
        public void service(GrizzlyRequest request, GrizzlyResponse response) {
            try {
                InputStream in = request.getInputStream();
                in.read();
                Map params = request.getParameterMap();
                assertTrue("param1 shouldn't be available", params.containsKey("param1"));
                assertNull(request.getParameter("param1"));
            } catch (Throwable t) {
                error.set(t);
            }
        }
    }


    private static final class MyAdapter extends GrizzlyAdapter {

        @Override
        public void service(GrizzlyRequest request, GrizzlyResponse response) {

            response.setContentType("text/html");
            response.setCharacterEncoding("ISO-8859-1");
            response.setLocale(Locale.US);
            Writer w = null;
            try {
                w = response.getWriter();
                w.write("<html><head></head><body>");
                w.write("Principal not null: ");
                w.write(Boolean.toString(request.getUserPrincipal() != null));
                w.write("</body></html>");
            } catch (Exception e) {
                 LoggerUtils.getLogger().log(Level.SEVERE,
                                                    e.toString(),
                                                    e);
            } finally {
                if (w != null) {
                    try {
                        w.close();
                    } catch (IOException ioe) {
                        LoggerUtils.getLogger().log(Level.SEVERE,
                                                    ioe.toString(),
                                                    ioe);
                    }
                }
            }
        }
    }

}
