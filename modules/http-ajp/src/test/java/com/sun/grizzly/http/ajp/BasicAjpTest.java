/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011-2012 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.grizzly.http.ajp;

import com.sun.grizzly.http.SocketChannelOutputBuffer;
import com.sun.grizzly.tcp.Adapter;
import com.sun.grizzly.tcp.Request;
import com.sun.grizzly.tcp.Response;
import com.sun.grizzly.tcp.StaticResourcesAdapter;
import com.sun.grizzly.tcp.http11.GrizzlyAdapter;
import com.sun.grizzly.tcp.http11.GrizzlyRequest;
import com.sun.grizzly.tcp.http11.GrizzlyResponse;
import com.sun.grizzly.util.FutureImpl;
import com.sun.grizzly.util.net.SSLSupport;
import java.io.*;
import java.nio.channels.Channel;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Test simple Ajp communication usecases.
 *
 * @author Alexey Stashok
 * @author Justin Lee
 */
public class BasicAjpTest extends AjpTestBase {

    @Test
    public void testStaticRequests() throws IOException, InstantiationException {
        final int oldMaxBufferedBytes = SocketChannelOutputBuffer.getMaxBufferedBytes();
        
        try {
            SocketChannelOutputBuffer.setMaxBufferedBytes(4096);
            final String docroot = "src/test/resources";
            GrizzlyAdapter a = new GrizzlyAdapter() {

                @Override
                public void service(GrizzlyRequest request, GrizzlyResponse response)
                        throws Exception {
                    response.setBufferSize(65536);

                    final File file = new File(docroot, request.getRequestURI());
                    if (!file.exists()) {
                        response.setStatus(404, request.getRequestURI() + " doesn't exist");
                        return;
                    }

                    final OutputStream os = response.getOutputStream();
                    final InputStream fis = new FileInputStream(file);

                    try {
                        byte[] buf = new byte[9000];

                        int len;
                        while ((len = fis.read(buf)) != -1) {
                            os.write(buf, 0, len);
                            os.flush();
                        }
                    } finally {
                        try {
                            fis.close();
                        } catch (IOException e) {
                        }
                    }
                }

            };

            a.setHandleStaticResources(false);
            a.setUseSendFile(false);   // TODO Re-enable once sendFile is implemented.
            configureHttpServer(a);

            final String[] files = {/*"/ajpindex.html", */"/ajplarge.html"};
            for (String file : files) {
                try {
                    requestFile(file);
                } catch (Exception e) {
                    throw new RuntimeException("Testing file " + file + ": " + e.getMessage(), e);
                }
            }
        
        } finally {
            SocketChannelOutputBuffer.setMaxBufferedBytes(oldMaxBufferedBytes);
        }
    }

    @Test
    public void testDynamicRequests() throws IOException, InstantiationException {
        final String message = "Test Message";
        final StringBuilder builder = new StringBuilder();
        while (builder.length() < 100000) {
            builder.append(message);
        }
        for (String test : new String[]{message, builder.toString()}) {
            dynamicRequest(test);
        }
    }

    private void requestFile(String file) throws IOException {
        AjpForwardRequestPacket forward = new AjpForwardRequestPacket("GET", file, PORT, 0);
        send(forward.toByteArray());

        AjpResponse ajpResponse = Utils.parseResponse(readAjpMessage());

        Assert.assertEquals("Testing file " + file, 200, ajpResponse.getResponseCode());
        Assert.assertEquals("Testing file " + file, "OK", ajpResponse.getResponseMessage());

        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        do {
            ajpResponse = Utils.parseResponse(readAjpMessage());
            if (ajpResponse.getType() == AjpConstants.JK_AJP13_SEND_BODY_CHUNK) {
                stream.write(ajpResponse.getBody());
            }
        } while (ajpResponse.getType() == AjpConstants.JK_AJP13_SEND_BODY_CHUNK);
        
        System.out.println("src/test/resources" + file);
        Assert.assertArrayEquals("Testing file " + file, readFile("src/test/resources" + file), stream.toByteArray());

        Assert.assertEquals("Testing file " + file, AjpConstants.JK_AJP13_END_RESPONSE, ajpResponse.getType());
    }

    private void dynamicRequest(final String message) throws IOException, InstantiationException {
        try {
            configureHttpServer(new GrizzlyAdapter() {
                @Override
                public void service(GrizzlyRequest request, GrizzlyResponse response) throws Exception {
                    response.setContentLength(message.length());
                    response.setContentType("text");
                    response.setLocale(Locale.US);
                    for (int i = 1; i <= 10; i++) {
                        response.addHeader("header", "value" + i);
                    }

                    response.getOutputBuffer().write(message);
                }
            });
            AjpForwardRequestPacket forward = new AjpForwardRequestPacket("GET", "/bob", PORT, 0);
            send(forward.toByteArray());
            AjpResponse ajpResponse = Utils.parseResponse(readAjpMessage());

            Assert.assertEquals(200, ajpResponse.getResponseCode());
            Assert.assertEquals("OK", ajpResponse.getResponseMessage());
            Assert.assertEquals("Should get all the grizzly headers back", 10,
                    Collections.list(ajpResponse.getHeaders().values("header")).size());

            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            do {
                ajpResponse = Utils.parseResponse(readAjpMessage());
                if (ajpResponse.getType() == AjpConstants.JK_AJP13_SEND_BODY_CHUNK) {
                    stream.write(ajpResponse.getBody());
                }
            } while (ajpResponse.getType() == AjpConstants.JK_AJP13_SEND_BODY_CHUNK);
            Assert.assertEquals(message, new String(stream.toByteArray()));

            Assert.assertEquals(AjpConstants.JK_AJP13_END_RESPONSE, ajpResponse.getType());
        } finally {
            after();
        }
    }

    @Test
    public void testPost() throws IOException, InstantiationException {
        GrizzlyAdapter a = new GrizzlyAdapter() {

            @Override
            public void service(GrizzlyRequest request, GrizzlyResponse response)
                    throws Exception {
                final int length = request.getContentLength();
                final InputStream is = request.getInputStream();

                for (int i = 0; i < length; i++) {
                    final int c = is.read();
                    final int expected = (i % 'Z' - 'A') + 'A';
                    if (c != expected) {
                        response.sendError(400, "Unexpected char[" + i + "]. Expected: " + ((char) expected) + " but was: " + ((char) c) + "(" + c + ")");
                        return;
                    }
                }
                
                response.setStatus(200, "FINE");
            }

        };

        final int postHalfSize = 512;
        
        configureHttpServer(a);

        final AjpForwardRequestPacket headersPacket =
                new AjpForwardRequestPacket("POST", "/myresource", 80, PORT);
        headersPacket.addHeader("Content-Length", String.valueOf(postHalfSize * 2));
        headersPacket.addHeader("Host", "localhost:80");
        
        send(headersPacket.toByteArray());
        
        byte[] postBody = new byte[postHalfSize * 2];
        for (int i = 0; i < postBody.length; i++) {
            postBody[i] = (byte) ((i % 'Z' - 'A') + 'A');
        }
        
        final byte[] postBody1 = new byte[postHalfSize];
        System.arraycopy(postBody, 0, postBody1, 0, postHalfSize);
        final AjpDataPacket dataPacket1 = new AjpDataPacket(postBody1);
        send(dataPacket1.toByteArray());

        AjpResponse ajpAskMoreDataPacket = Utils.parseResponse(readAjpMessage());
        Assert.assertTrue(AjpMessageUtils.getShort(ajpAskMoreDataPacket.getBody(), 0) <= AjpConstants.MAX_READ_SIZE);
        
        final byte[] postBody2 = new byte[postHalfSize];
        System.arraycopy(postBody, postHalfSize, postBody2, 0, postHalfSize);
        final AjpDataPacket dataPacket2 = new AjpDataPacket(postBody2);
        send(dataPacket2.toByteArray());
        
        AjpResponse ajpResponse = Utils.parseResponse(readAjpMessage());
        Assert.assertEquals("FINE", ajpResponse.getResponseMessage());
    }
    
    @Test
    public void testStabilityAfterFailure() throws Exception {
        GrizzlyAdapter a = new GrizzlyAdapter() {

            @Override
            public void service(GrizzlyRequest request, GrizzlyResponse response)
                    throws Exception {
                final int length = request.getContentLength();
                final InputStream is = request.getInputStream();

                for (int i = 0; i < length; i++) {
                    final int c;
                    try {
                        c = is.read();
                    } catch (IOException e) {
                        //swallow the exception
                        return;
                    }
                    
                    final int expected = (i % 'Z' - 'A') + 'A';
                    if (c != expected) {
                        response.sendError(400, "Unexpected char[" + i + "]. Expected: " + ((char) expected) + " but was: " + ((char) c) + "(" + c + ")");
                        return;
                    }
                }
                
                response.setStatus(200, "FINE");
            }

        };

        final int postHalfSize = 512;
        
        configureHttpServer(a);

        final AjpForwardRequestPacket headersPacket =
                new AjpForwardRequestPacket("POST", "/TestServlet/normal", 80, PORT);
        headersPacket.addHeader("Content-Length", String.valueOf(postHalfSize * 2));
        headersPacket.addHeader("Host", "localhost:80");
        final byte[] headersPacketArray = headersPacket.toByteArray();
        
        byte[] postBody = new byte[postHalfSize * 2];
        for (int i = 0; i < postBody.length; i++) {
            postBody[i] = (byte) ((i % 'Z' - 'A') + 'A');
        }
        
        final byte[] postBody1 = new byte[postHalfSize];
        System.arraycopy(postBody, 0, postBody1, 0, postHalfSize);
        final AjpDataPacket dataPacket1 = new AjpDataPacket(postBody1);
        final byte[] dataPacket1Array = dataPacket1.toByteArray();
        
        final byte[] postBody2 = new byte[postHalfSize];
        System.arraycopy(postBody, postHalfSize, postBody2, 0, postHalfSize);
        final AjpDataPacket dataPacket2 = new AjpDataPacket(postBody2);
        final byte[] dataPacket2Array = dataPacket2.toByteArray();
        
        byte[] incompleteData = new byte[headersPacketArray.length + dataPacket1Array.length];
        System.arraycopy(headersPacketArray, 0, incompleteData, 0, headersPacketArray.length);
        System.arraycopy(dataPacket1Array, 0, incompleteData, headersPacketArray.length, incompleteData.length - headersPacketArray.length);
        
        send(incompleteData);
        Thread.sleep(1000);
        
        // Cause failure on server
        closeClient();
        
        for (int i = 0; i < 1024; i++) {
            send(headersPacketArray);
            send(dataPacket1Array);

            AjpResponse ajpAskMoreDataPacket = Utils.parseResponse(readAjpMessage());
            Assert.assertTrue(AjpMessageUtils.getShort(ajpAskMoreDataPacket.getBody(), 0) <= AjpConstants.MAX_READ_SIZE);

            send(dataPacket2Array);

            AjpResponse ajpResponse = Utils.parseResponse(readAjpMessage());
            Assert.assertEquals("FINE", ajpResponse.getResponseMessage());
            closeClient();
        }
    }
    
    public void testPingPong() throws Exception {
        configureHttpServer(new StaticResourcesAdapter("src/test/resources"));
        
        final byte[] request = new byte[] {0x12, 0x34, 0, 1, AjpConstants.JK_AJP13_CPING_REQUEST};
        
        send(request);
        
        final DataInputStream responseInputStream = new DataInputStream(new ByteArrayInputStream(readAjpMessage()));
        
        Assert.assertEquals((byte) 'A', responseInputStream.read());
        Assert.assertEquals((byte) 'B', responseInputStream.read());
        Assert.assertEquals((short) 1, responseInputStream.readShort());
        Assert.assertEquals(AjpConstants.JK_AJP13_CPONG_REPLY, responseInputStream.read());
    }

    @Test
    public void testShutdownHandler() throws Exception {
        final FutureImpl<Boolean> shutdownFuture = new FutureImpl();
        final ShutdownHandler shutDownHandler = new ShutdownHandler() {

            public void onShutdown(Channel initiator) {
                shutdownFuture.setResult(true);
            }
        };

        configureHttpServer(new StaticResourcesAdapter("src/test/resources"));
        selectorThread.getAjpConfiguration().setShutdownEnabled(true);
        selectorThread.getAjpConfiguration().getShutdownHandlers().add(shutDownHandler);
        
        final byte[] request = new byte[] {0x12, 0x34, 0, 1, AjpConstants.JK_AJP13_SHUTDOWN};
        
        send(request);

        final Boolean b = shutdownFuture.get(10, TimeUnit.SECONDS);
        Assert.assertTrue(b);
    }

    @Test
    public void testNullAttribute() throws Exception {
        configureHttpServer(new Adapter() {

            public void service(Request request, Response response) throws Exception {
                final Set<String> attributeNames = request.getAttributes().keySet();

                final boolean isOk =
                        attributeNames.contains("JK_LB_ACTIVATION") &&
                        request.getAttribute("JK_LB_ACTIVATION") == null &&
                        attributeNames.contains("AJP_REMOTE_PORT") &&
                        "60955".equals(request.getAttribute("AJP_REMOTE_PORT"));
                
                
                if (isOk) {
                    response.setStatus(200);
                    response.setMessage("FINE");
                } else {
                    response.setStatus(500);
                    response.setMessage("Attributes don't match");
                }
            }

            public void afterService(Request req, Response res) throws Exception {
            }
        });

        send(Utils.loadResourceFile("null-attr-payload.dat"));

        AjpResponse ajpResponse = Utils.parseResponse(readAjpMessage());

        Assert.assertEquals(200, ajpResponse.getResponseCode());
        Assert.assertEquals("FINE", ajpResponse.getResponseMessage());
    }
    
    @Test
    public void testFormParameters() throws Exception {
        final Map<String, String[]> patternMap = new HashMap<String, String[]>();
        patternMap.put("title", new String[] {"Developing PaaS Components"});
        patternMap.put("authors", new String[] {"Shalini M"});
        patternMap.put("price", new String[] {"100$"});
        
        configureHttpServer(new GrizzlyAdapter() {

            @Override
            public void service(GrizzlyRequest request, GrizzlyResponse response) throws Exception {
                final Map<String, String[]> paramMap = request.getParameterMap();
                boolean isOk = paramMap.size() == patternMap.size();
                
                if (isOk) {
                    // if sizes are equal - compare content
                    for (Map.Entry<String, String[]> patternEntry : patternMap.entrySet()) {
                        final String key = patternEntry.getKey();
                        final String[] value = patternEntry.getValue();
                        isOk = paramMap.containsKey(key) &&
                                Arrays.equals(value, paramMap.get(key));
                        
                        if (!isOk) break;
                    }
                }
                
                if (isOk) {
                    response.setStatus(200, "FINE");
                } else {
                    response.setStatus(500, "Attributes don't match");
                }
            }

        });
        
        send(Utils.loadResourceFile("form-params-payload1.dat"));
        Thread.sleep(1000);
        send(Utils.loadResourceFile("form-params-payload2.dat"));

        AjpResponse ajpResponse = Utils.parseResponse(readAjpMessage());

        Assert.assertEquals(200, ajpResponse.getResponseCode());
        Assert.assertEquals("FINE", ajpResponse.getResponseMessage());
    }
    
    @Test
    public void testSslParams() throws Exception {
        configureHttpServer(new GrizzlyAdapter() {

            @Override
            public void service(GrizzlyRequest request, GrizzlyResponse response) throws Exception {
                boolean isOk = "https".equals(request.getRequest().scheme().toString());
                String error = "unknown";
                
                if (isOk) {
                    try {
                        Assert.assertEquals((Integer) 256, (Integer) request.getAttribute(SSLSupport.KEY_SIZE_KEY));
                        Assert.assertNotNull(request.getAttribute(SSLSupport.SESSION_ID_KEY));
                        Assert.assertNotNull(request.getAttribute(SSLSupport.CIPHER_SUITE_KEY));
                        Assert.assertNotNull(request.getAttribute(SSLSupport.CERTIFICATE_KEY));
                    } catch (Exception e) {
                        error = e.getClass().getName() + ": " + e.getMessage();
                        isOk = false;
                    }
                }
                
                if (isOk) {
                    response.setStatus(200, "FINE");
                } else {
                    response.setStatus(500, error);
                }
            }

        });
        
        send(Utils.loadResourceFile("get-secured.dat"));

        AjpResponse ajpResponse = Utils.parseResponse(readAjpMessage());

        Assert.assertEquals(ajpResponse.getResponseMessage(), 200, ajpResponse.getResponseCode());
        Assert.assertEquals("FINE", ajpResponse.getResponseMessage());
    }
    
    @Test
    public void testRemoteAddress() throws Exception {
        final String expectedAddr = "10.163.27.8";
        configureHttpServer(new GrizzlyAdapter() {

            public void service(GrizzlyRequest request, GrizzlyResponse response)
                    throws Exception {
                boolean isOk = false;
                String result;
                try {
                    result = request.getRemoteAddr();
                    isOk = expectedAddr.equals(result);
                } catch (Exception e) {
                    result = e.toString();
                }
                
                if (isOk) {
                    response.setStatus(200, "FINE");
                } else {
                    response.setStatus(500, "Remote host don't match. Expected " +
                            expectedAddr + " but was " + result);
                }
            }
        });

        send(Utils.loadResourceFile("peer-addr-check.dat"));

        AjpResponse ajpResponse = Utils.parseResponse(readAjpMessage());

        Assert.assertEquals("FINE", ajpResponse.getResponseMessage());
        Assert.assertEquals(200, ajpResponse.getResponseCode());
    }    
}
