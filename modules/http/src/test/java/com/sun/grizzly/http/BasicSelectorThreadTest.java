/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2009-2012 Oracle and/or its affiliates. All rights reserved.
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
import com.sun.grizzly.http.utils.SelectorThreadUtils;
import com.sun.grizzly.tcp.http11.GrizzlyAdapter;
import com.sun.grizzly.tcp.http11.GrizzlyRequest;
import com.sun.grizzly.tcp.http11.GrizzlyResponse;
import com.sun.grizzly.util.Utils;
import com.sun.grizzly.util.http.MimeHeaders;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.logging.Level;
import java.util.logging.Logger;

import junit.framework.TestCase;

/**
 * Basic {@link SelectorThread} test.
 * 
 * @author Jeanfrancois Arcand
 */
public class BasicSelectorThreadTest extends TestCase {
    private final static Logger logger = SelectorThread.logger();

    public static final int PORT = 18890;
    private SelectorThread st;

    public void createSelectorThread(int port) {
        st = new SelectorThread() {

            /**
             * Start the SelectorThread using its own thread and don't block the Thread.
             *  This method should be used when Grizzly is embedded.
             */
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

                super.start();

                try {
                    latch.await();
                } catch (InterruptedException ex) {
                }

                if (!controller.isStarted()) {
                    throw new IllegalStateException("Controller is not started!");
                }
            }
        };

        st.setPort(port);
        st.setDisplayConfiguration(Utils.VERBOSE_TESTS);

    }

    protected HttpURLConnection getConnection(String alias, int port) throws IOException {
        URL url = new URL("http", "localhost", port, alias);
        HttpURLConnection urlConn = (HttpURLConnection) url.openConnection();
        urlConn.connect();
        return urlConn;
    }

    public void testKeepAliveTest() throws Exception{
        Utils.dumpOut("Test: testKeepAliveTest");
        try {
            createSelectorThread(0);
            st.setAdapter(new HelloWorldAdapter());
            st.setMaxKeepAliveRequests(0);
            SelectorThread.enableNioLogging = true;

            st.listen();

            HttpURLConnection conn = getConnection("/", st.getPort());
            String s = conn.getHeaderField("Connection");
            assertEquals(s, "close");

        } finally {
            SelectorThreadUtils.stopSelectorThread(st);
        }

    }

    public void testMultipleBytesMimeHeaders() throws Exception{
        Utils.dumpOut("Test: testMultipleBytesMimeHeaders");
        try {
            createSelectorThread(0);
            st.setAdapter(new GrizzlyAdapter() {

                private final byte[] test = "test-1".getBytes();
                private final byte[] test2 = "test-2".getBytes();
                private final byte[] test3 = "test-3".getBytes();
                private final byte[] test4 = "test-4".getBytes();
                @Override
                public void service(GrizzlyRequest request, GrizzlyResponse response) {
                    MimeHeaders m1 = response.getResponse().getMimeHeaders();

                    m1.addValue("Set-Cookie").setBytes(test, 0, test.length);
                    m1.addValue("Set-Cookie").setBytes(test2, 0, test2.length);
                    m1.addValue("Set-Cookie").setBytes(test3, 0, test3.length);
                    m1.addValue("Set-Cookie").setBytes(test4, 0, test4.length);
                }
            });

            st.setMaxKeepAliveRequests(0);
            SelectorThread.enableNioLogging = true;
            logger.setLevel(Level.WARNING);

            st.listen();

            Socket s = new Socket("localhost", st.getPort());
            s.setSoTimeout(30 * 1000);
            OutputStream os = s.getOutputStream();

            Utils.dumpErr("GET / HTTP/1.1\n");
            os.write("GET / HTTP/1.1\n".getBytes());
            os.write(("Host: localhost:" + PORT + "\n").getBytes());
            os.write("\n".getBytes());
            os.flush();

            InputStream is = new DataInputStream(s.getInputStream());
            BufferedReader br = new BufferedReader(new InputStreamReader(is));
            String line = null;
            Utils.dumpErr("================== reading the response");
            boolean gotCorrectResponse = false;
            int i = 0;
            while ((line = br.readLine()) != null) {
                Utils.dumpErr("-> " + line);
                if (line.startsWith("Set-Cookie")) {
                    if (line.endsWith("test-" + ++i)){
                        gotCorrectResponse = true;
                    } else {
                        gotCorrectResponse = false;
                    }
                }
            }
            assertTrue(gotCorrectResponse);
        } finally {
            SelectorThreadUtils.stopSelectorThread(st);
        }

    }

    public void testKeepAliveNotFoundTest() throws Exception{
        Utils.dumpOut("Test: testKeepAliveNotFoundTest");
        try {
            createSelectorThread(0);
            st.setAdapter(new NotFoundAdapter());
            st.setMaxKeepAliveRequests(0);
            SelectorThread.enableNioLogging = true;

            st.listen();

            HttpURLConnection conn = getConnection("/404.html", st.getPort());
            String s = conn.getHeaderField("Connection");
            assertEquals(s, "close");

        } finally {
            SelectorThreadUtils.stopSelectorThread(st);
        }

    }

    public void testEphemeralPort() throws Exception {
        Utils.dumpOut("Test: testEphemeralPort");
        final String testString = "HelloWorld";
        final byte[] testData = testString.getBytes();
        try {
            createSelectorThread(0);
            st.setAdapter(new HelloWorldAdapter());

            st.listen();

            sendRequest(testData, testString, st.getPort());


        } finally {
            SelectorThreadUtils.stopSelectorThread(st);
        }
    }

    public void testHelloWorldGrizzlyAdapter() throws Exception {
        Utils.dumpOut("Test: testHelloWorldGrizzlyAdapter");
        final ScheduledThreadPoolExecutor pe = new ScheduledThreadPoolExecutor(1);
        final String testString = "HelloWorld";
        final byte[] testData = testString.getBytes();
        try {
            createSelectorThread(PORT);
            st.setAdapter(new HelloWorldAdapter());

            st.listen();
            st.enableMonitoring();


            sendRequest(testData, testString, PORT);


        } finally {
            SelectorThreadUtils.stopSelectorThread(st);
            pe.shutdown();
        }
    }

    public void testTcpKeepSetting() throws Exception {
        final String testString = "true";
        final byte[] testData = testString.getBytes();
        try {
            createSelectorThread(PORT);
            st.setSocketKeepAlive(true);
            st.setAdapter(new GrizzlyAdapter() {

                @Override
                public void service(final GrizzlyRequest request,
                        final GrizzlyResponse response) throws Exception {
                    boolean socketKeepAlive = true;
                    try {
                        socketKeepAlive = response.getResponse().getChannel().socket().getKeepAlive();
                    } catch (IOException e) {
                        // Exception may occur on the OS level, we don't want our test to fail because of that
                    }
                    response.getWriter().write("" + socketKeepAlive);
                }
                
            });

            st.listen();
            st.enableMonitoring();


            sendRequest(testData, testString, PORT);


        } finally {
            SelectorThreadUtils.stopSelectorThread(st);
        }
    }
    
    public void testScheme() throws Exception {
        Utils.dumpOut("Test: testScheme");
        final String testString = "https";
        final byte[] testData = testString.getBytes();
        try {
            createSelectorThread(PORT);
            BackendConfiguration backendConfiguration = new BackendConfiguration();
            backendConfiguration.setScheme("https");
            
            st.setBackendConfiguration(backendConfiguration);
            st.setAdapter(new GrizzlyAdapter() {

                @Override
                public void service(GrizzlyRequest request, GrizzlyResponse response) throws Exception {
                    response.getWriter().write(request.getScheme());
                }
            });

            st.listen();
            st.enableMonitoring();


            sendRequest(testData, testString, PORT);
        } finally {
            SelectorThreadUtils.stopSelectorThread(st);
        }
    }
    
    public void testSchemeMappingNull() throws Exception {
        Utils.dumpOut("Test: testSchemeMappingNull");
        final String testString = "http";
        final byte[] testData = testString.getBytes();
        try {
            createSelectorThread(PORT);
            BackendConfiguration backendConfiguration = new BackendConfiguration();
            backendConfiguration.setSchemeMapping("my-scheme");
            st.setBackendConfiguration(backendConfiguration);
            
            st.setAdapter(new GrizzlyAdapter() {

                @Override
                public void service(GrizzlyRequest request, GrizzlyResponse response) throws Exception {
                    response.getWriter().write(request.getScheme());
                }
            });

            st.listen();
            st.enableMonitoring();


            sendRequest(testData, testString, PORT);
        } finally {
            SelectorThreadUtils.stopSelectorThread(st);
        }
    }

    public void testSchemeMapping() throws Exception {
        Utils.dumpOut("Test: testSchemeMapping");
        final String testString = "https";
        final byte[] testData = testString.getBytes();
        try {
            createSelectorThread(PORT);
            BackendConfiguration backendConfiguration = new BackendConfiguration();
            backendConfiguration.setSchemeMapping("my-scheme");
            st.setBackendConfiguration(backendConfiguration);
            
            st.setAdapter(new GrizzlyAdapter() {

                @Override
                public void service(GrizzlyRequest request, GrizzlyResponse response) throws Exception {
                    response.getWriter().write(request.getScheme());
                }
            });

            st.listen();
            st.enableMonitoring();


            sendRequest(Collections.singletonMap("my-scheme", "https"), testData,
                    testString, PORT);
        } finally {
            SelectorThreadUtils.stopSelectorThread(st);
        }
    }

    public void testRemoteUserMappingNull() throws Exception {
        Utils.dumpOut("Test: testRemoteUserMappingNull");
        final String testString = "";
        final byte[] testData = testString.getBytes();
        try {
            createSelectorThread(PORT);
            BackendConfiguration backendConfiguration = new BackendConfiguration();
            backendConfiguration.setRemoteUserMapping("my-remote-user");
            st.setBackendConfiguration(backendConfiguration);
            
            st.setAdapter(new GrizzlyAdapter() {

                @Override
                public void service(GrizzlyRequest request, GrizzlyResponse response) throws Exception {
                    if (request.getRemoteUser() != null) {
                        response.getWriter().write(request.getRemoteUser());
                    }
                }
            });

            st.listen();
            st.enableMonitoring();

            sendRequest(null, testData,
                    testString, PORT);
        } finally {
            SelectorThreadUtils.stopSelectorThread(st);
        }
    }
    
    public void testRemoteUserMapping() throws Exception {
        Utils.dumpOut("Test: testRemoteUserMapping");
        final String testString = "grizzly";
        final byte[] testData = testString.getBytes();
        try {
            createSelectorThread(PORT);
            BackendConfiguration backendConfiguration = new BackendConfiguration();
            backendConfiguration.setRemoteUserMapping("my-remote-user");
            st.setBackendConfiguration(backendConfiguration);
            
            st.setAdapter(new GrizzlyAdapter() {

                @Override
                public void service(GrizzlyRequest request, GrizzlyResponse response) throws Exception {
                    if (request.getRemoteUser() != null) {
                        response.getWriter().write(request.getRemoteUser());
                    }
                }
            });

            st.listen();
            st.enableMonitoring();

            sendRequest(Collections.singletonMap("my-remote-user", "grizzly"), testData,
                    testString, PORT);
        } finally {
            SelectorThreadUtils.stopSelectorThread(st);
        }
    }

    public class NotFoundAdapter extends GrizzlyAdapter {

        @Override
        public void service(GrizzlyRequest request, GrizzlyResponse response) {
            response.setStatus(404, "Not Found");
        }
    }

    public class HelloWorldAdapter extends GrizzlyAdapter {

        @Override
        public void service(GrizzlyRequest request, GrizzlyResponse response) {
            try {
                response.getWriter().print("HelloWorld");
            } catch (IOException ex) {
                ex.printStackTrace();
                fail(ex.getMessage());
            }
        }
    }

    private String sendRequest(byte[] testData, String testString, int port)
            throws Exception {

        return sendRequest(null, testData, testString, true, port);
    }

    private String sendRequest(Map<String, String> headers, byte[] testData,
            String testString, int port) throws Exception {
        return sendRequest(headers, testData, testString, true, port);
    }

    private String sendRequest(Map<String, String> headers, byte[] testData,
            String testString, boolean assertTrue, int port)
            throws Exception {
        URL url = new URL("http://localhost:" + port);
        HttpURLConnection connection =
                (HttpURLConnection) url.openConnection();
        
        if (headers != null) {
            for(Map.Entry<String, String> entry : headers.entrySet()) {
                connection.addRequestProperty(entry.getKey(), entry.getValue());
            }
        }
        
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        OutputStream os = connection.getOutputStream();
        os.write("Hello".getBytes());
        os.flush();

        InputStream is = new DataInputStream(connection.getInputStream());
        byte[] tmpResponse = new byte[testData.length * 2];
        final int responseLen = is.read(tmpResponse);
        byte[] response = new byte[responseLen];
        System.arraycopy(tmpResponse, 0, response, 0, responseLen);


        String r = new String(response);
        if (assertTrue) {
            Utils.dumpOut("Response: " + r);
            assertEquals(testString, r);
        }
        connection.disconnect();
        return r;
    }
}
