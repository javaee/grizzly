/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011-2015 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.http.ajp;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.SocketConnectorHandler;
import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.FilterChainBuilder;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.filterchain.TransportFilter;
import org.glassfish.grizzly.http.server.HttpHandler;
import org.glassfish.grizzly.http.server.NetworkListener;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.http.server.Response;
import org.glassfish.grizzly.impl.FutureImpl;
import org.glassfish.grizzly.impl.SafeFutureImpl;
import org.glassfish.grizzly.memory.Buffers;
import org.glassfish.grizzly.memory.MemoryManager;
import org.glassfish.grizzly.nio.transport.TCPNIOConnectorHandler;
import org.glassfish.grizzly.ssl.SSLSupport;
import org.junit.Assert;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Test simple Ajp communication usecases.
 * 
 * @author Alexey Stashok
 */
public class BasicAjpTest extends AjpTestBase {
    private static final Logger LOGGER = Grizzly.logger(BasicAjpTest.class);
    @Test
    public void test100ContinuePost() throws IOException, InstantiationException, Exception {
        HttpHandler httpHanlder = new HttpHandler() {

            @Override
            public void service(Request request, Response response)
                    throws Exception {
                if (request.getHeader("Expect") != null) {
                    response.sendAcknowledgement();
                    
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
                } else {
                    response.sendError(500, "100-continue header has been lost?");
                }
            }

        };

        final int size = 1024;
        
        startHttpServer(httpHanlder);

        final AjpForwardRequestPacket headersPacket =
                new AjpForwardRequestPacket("POST", "/myresource", 80, PORT);
        headersPacket.addHeader("Content-Length", String.valueOf(size));
        headersPacket.addHeader("Host", "localhost:80");
        headersPacket.addHeader("Expect", "100-continue");
        
        send(headersPacket.toByteArray());
        
        byte[] postBody = new byte[size];
        for (int i = 0; i < postBody.length; i++) {
            postBody[i] = (byte) ((i % 'Z' - 'A') + 'A');
        }
        
        final AjpDataPacket dataPacket = new AjpDataPacket(postBody);
        send(dataPacket.toByteArray());

        AjpResponse ajpResponse = Utils.parseResponse(readAjpMessage());
        Assert.assertEquals(ajpResponse.getResponseMessage(), 200, ajpResponse.getResponseCode());
        Assert.assertEquals("FINE", ajpResponse.getResponseMessage());
    }
    
    /**
     * CVE-2014-0095 Denial of Service related
     * 
     * @throws IOException
     * @throws InstantiationException
     * @throws Exception 
     */
    @Test
    public void testZeroContentLengthGet() throws IOException, InstantiationException, Exception {
        HttpHandler httpHanlder = new HttpHandler() {

            @Override
            public void service(Request request, Response response)
                    throws Exception {
                if (request.getContentLengthLong() == 0l) {
                    response.setStatus(200, "FINE");
                } else {
                    response.sendError(500, "'Content-Length: 0' header has been lost?");
                }
            }

        };

        startHttpServer(httpHanlder);

        final AjpForwardRequestPacket headersPacket =
                new AjpForwardRequestPacket("GET", "/myresource", 80, PORT);
        headersPacket.addHeader("Content-Length", "0");
        headersPacket.addHeader("Host", "localhost:80");
        
        final byte[] requestBytes = headersPacket.toByteArray();
        
        send(requestBytes);
        
        AjpResponse ajpResponse = Utils.parseResponse(readAjpMessage());
        Assert.assertEquals(ajpResponse.getResponseMessage(), 200, ajpResponse.getResponseCode());
        Assert.assertEquals("FINE", ajpResponse.getResponseMessage());

        ajpResponse = Utils.parseResponse(readAjpMessage());
        Assert.assertEquals(AjpConstants.JK_AJP13_END_RESPONSE, ajpResponse.getType());
        
        // Send one more request to make sure the connection is still alive and operable
        
        send(requestBytes);
        ajpResponse = Utils.parseResponse(readAjpMessage());
        Assert.assertEquals(ajpResponse.getResponseMessage(), 200, ajpResponse.getResponseCode());
        Assert.assertEquals("FINE", ajpResponse.getResponseMessage());
        
        ajpResponse = Utils.parseResponse(readAjpMessage());
        Assert.assertEquals(AjpConstants.JK_AJP13_END_RESPONSE, ajpResponse.getType());
    }
    
    /**
     * CVE-2014-0095 Denial of Service related
     * 
     * @throws IOException
     * @throws InstantiationException
     * @throws Exception 
     */
    @Test
    public void testZeroContentLengthPost() throws IOException, InstantiationException, Exception {
        HttpHandler httpHanlder = new HttpHandler() {

            @Override
            public void service(Request request, Response response)
                    throws Exception {
                if (request.getContentLengthLong() == 0l) {
                    response.setStatus(200, "FINE");
                } else {
                    response.sendError(500, "'Content-Length: 0' header has been lost?");
                }
            }

        };

        startHttpServer(httpHanlder);

        final AjpForwardRequestPacket headersPacket =
                new AjpForwardRequestPacket("POST", "/myresource", 80, PORT);
        headersPacket.addHeader("Content-Length", "0");
        headersPacket.addHeader("Host", "localhost:80");
        
        final byte[] requestBytes = headersPacket.toByteArray();
        
        send(requestBytes);
        
        AjpResponse ajpResponse = Utils.parseResponse(readAjpMessage());
        Assert.assertEquals(ajpResponse.getResponseMessage(), 200, ajpResponse.getResponseCode());
        Assert.assertEquals("FINE", ajpResponse.getResponseMessage());

        ajpResponse = Utils.parseResponse(readAjpMessage());
        Assert.assertEquals(AjpConstants.JK_AJP13_END_RESPONSE, ajpResponse.getType());
        
        // Send one more request to make sure the connection is still alive and operable
        
        send(requestBytes);
        ajpResponse = Utils.parseResponse(readAjpMessage());
        Assert.assertEquals(ajpResponse.getResponseMessage(), 200, ajpResponse.getResponseCode());
        Assert.assertEquals("FINE", ajpResponse.getResponseMessage());
        
        ajpResponse = Utils.parseResponse(readAjpMessage());
        Assert.assertEquals(AjpConstants.JK_AJP13_END_RESPONSE, ajpResponse.getType());
    }

    @Test
    public void testPingPong() throws Exception {
        startHttpServer(new HttpHandler() {

            @Override
            public void service(Request request, Response response) throws Exception {
                response.setStatus(200, "FINE");
            }

        }, "/");

        final MemoryManager mm =
                httpServer.getListener(LISTENER_NAME).getTransport().getMemoryManager();
        final Buffer request = mm.allocate(512);
        request.put((byte) 0x12);
        request.put((byte) 0x34);
        request.putShort((short) 1);
        request.put(AjpConstants.JK_AJP13_CPING_REQUEST);
        request.flip();

        final Future<Buffer> responseFuture = send("localhost", PORT, request);
        Buffer response = responseFuture.get(10, TimeUnit.SECONDS);

        assertEquals('A', response.get());
        assertEquals('B', response.get());
        assertEquals((short) 1, response.getShort());
        assertEquals(AjpConstants.JK_AJP13_CPONG_REPLY, response.get());
        
        final AjpForwardRequestPacket headersPacket =
                new AjpForwardRequestPacket("GET", "/TestServlet/normal", 80, PORT);
        headersPacket.addHeader("Host", "localhost:80");
        send(headersPacket.toByteArray());
        
        AjpResponse ajpResponse = Utils.parseResponse(readAjpMessage());
        Assert.assertEquals("FINE", ajpResponse.getResponseMessage());
        
    }
    
    @Test
    public void testShutdownHandlerNoSecret() throws Exception {
        final FutureImpl<Boolean> shutdownFuture = SafeFutureImpl.create();
        final ShutdownHandler shutDownHandler = new ShutdownHandler() {

            @Override
            public void onShutdown(Connection initiator) {
                shutdownFuture.result(true);
            }
        };

        AjpAddOn myAjpAddon = new AjpAddOn() {

            @Override
            protected AjpHandlerFilter createAjpHandlerFilter() {
                final AjpHandlerFilter filter = new AjpHandlerFilter();
                filter.addShutdownHandler(shutDownHandler);
                return filter;
            }
        };

        final NetworkListener listener = httpServer.getListener(LISTENER_NAME);

        listener.deregisterAddOn(ajpAddon);
        listener.registerAddOn(myAjpAddon);

        startHttpServer(new HttpHandler() {

            @Override
            public void service(Request request, Response response) throws Exception {
            }

        }, "/");

        Buffer request = Buffers.wrap(listener.getTransport().getMemoryManager(),
                new AjpShutdownPacket(null).toByteArray());

        send("localhost", PORT, request);
        final Boolean b = shutdownFuture.get(10, TimeUnit.SECONDS);
        assertTrue(b);
    }

    @Test
    public void testShutdownHandlerWithCorrectSecret() throws Exception {
        final String secretKey = "bigSecret";
        
        final FutureImpl<Boolean> shutdownFuture = SafeFutureImpl.create();
        final ShutdownHandler shutDownHandler = new ShutdownHandler() {

            @Override
            public void onShutdown(Connection initiator) {
                shutdownFuture.result(true);
            }
        };

        AjpAddOn myAjpAddon = new AjpAddOn() {

            @Override
            protected AjpHandlerFilter createAjpHandlerFilter() {
                final AjpHandlerFilter filter = new AjpHandlerFilter();
                filter.addShutdownHandler(shutDownHandler);
                return filter;
            }
        };

        myAjpAddon.configure(false, secretKey);
        final NetworkListener listener = httpServer.getListener(LISTENER_NAME);

        listener.deregisterAddOn(ajpAddon);
        listener.registerAddOn(myAjpAddon);

        startHttpServer(new HttpHandler() {

            @Override
            public void service(Request request, Response response) throws Exception {
            }

        }, "/");

        Buffer request = Buffers.wrap(listener.getTransport().getMemoryManager(),
                new AjpShutdownPacket(secretKey).toByteArray());

        send("localhost", PORT, request);
        final Boolean b = shutdownFuture.get(10, TimeUnit.SECONDS);
        assertTrue(b);
    }
    
    @Test
    public void testShutdownHandlerWithIncorrectSecret() throws Exception {
        final String secretKey = "bigSecret";
        final String incorrectSecretKey = "incorrectSecret";
        
        final FutureImpl<Boolean> shutdownFuture = SafeFutureImpl.create();
        final ShutdownHandler shutDownHandler = new ShutdownHandler() {

            @Override
            public void onShutdown(Connection initiator) {
                shutdownFuture.result(true);
            }
        };

        AjpAddOn myAjpAddon = new AjpAddOn() {

            @Override
            protected AjpHandlerFilter createAjpHandlerFilter() {
                final AjpHandlerFilter filter = new AjpHandlerFilter();
                filter.addShutdownHandler(shutDownHandler);
                return filter;
            }
        };

        myAjpAddon.configure(false, secretKey);
        final NetworkListener listener = httpServer.getListener(LISTENER_NAME);

        listener.deregisterAddOn(ajpAddon);
        listener.registerAddOn(myAjpAddon);

        startHttpServer(new HttpHandler() {

            @Override
            public void service(Request request, Response response) throws Exception {
            }

        }, "/");

        Buffer request = Buffers.wrap(listener.getTransport().getMemoryManager(),
                new AjpShutdownPacket(incorrectSecretKey).toByteArray());

        Future<Buffer> f = send("localhost", PORT, request);
        try {
           f.get(10, TimeUnit.SECONDS);
           fail("Supposed to fail");
        } catch (ExecutionException e) {
            Throwable t = e.getCause();
            assertTrue(t instanceof IOException);
        }

        assertTrue(!shutdownFuture.isDone());
    }
    
    @Test
    public void testNullAttribute() throws Exception {
        final NetworkListener listener = httpServer.getListener(LISTENER_NAME);

        startHttpServer(new HttpHandler() {

            @Override
            public void service(Request request, Response response) throws Exception {
                final Set<String> attributeNames = request.getAttributeNames();
                final boolean isOk =
                        attributeNames.contains("JK_LB_ACTIVATION") &&
                        request.getAttribute("JK_LB_ACTIVATION") == null &&
                        attributeNames.contains("AJP_REMOTE_PORT") &&
                        "60955".equals(request.getAttribute("AJP_REMOTE_PORT"));
                
                
                if (isOk) {
                    response.setStatus(200, "FINE");
                } else {
                    response.setStatus(500, "Attributes don't match");
                }
            }

        }, "/SimpleWebApp/SimpleServlet");

        final MemoryManager mm =
                listener.getTransport().getMemoryManager();
        final Buffer request = Buffers.wrap(mm,
                Utils.loadResourceFile("null-attr-payload.dat"));

        Buffer responseBuffer = send("localhost", PORT, request).get(10, TimeUnit.SECONDS);

        // Successful response length is 16 bytes.  This includes the status
        // line and a content-length
        boolean isFailure = responseBuffer.remaining() != 16;

        if (isFailure) {
            byte[] response = new byte[responseBuffer.remaining()];
            responseBuffer.get(response);
            String hex = toHexString(response);
            fail("unexpected response length=" + response.length + " content=[" + hex + "]");
        }
    }
    
    @Test
    public void testFormParameters() throws Exception {
        final Map<String, String[]> patternMap = new HashMap<String, String[]>();
        patternMap.put("title", new String[] {"Developing PaaS Components"});
        patternMap.put("authors", new String[] {"Shalini M"});
        patternMap.put("price", new String[] {"100$"});
        
        final NetworkListener listener = httpServer.getListener(LISTENER_NAME);

        startHttpServer(new HttpHandler() {

            @Override
            public void service(Request request, Response response) throws Exception {
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

        }, "/bookstore/BookStoreServlet");

        final MemoryManager mm =
                listener.getTransport().getMemoryManager();
        final Buffer requestPart1 = Buffers.wrap(mm,
                Utils.loadResourceFile("form-params-payload1.dat"));
        final Buffer requestPart2 = Buffers.wrap(mm,
                Utils.loadResourceFile("form-params-payload2.dat"));

        Buffer responseBuffer = send("localhost", PORT,
                Buffers.appendBuffers(mm, requestPart1, requestPart2))
                .get(10, TimeUnit.SECONDS);

        // Successful response length is 16 bytes.  This includes the status
        // line and a content-length
        boolean isFailure = responseBuffer.remaining() != 16;

        if (isFailure) {
            byte[] response = new byte[responseBuffer.remaining()];
            responseBuffer.get(response);
            String hex = toHexString(response);
            fail("unexpected response length=" + response.length + " content=[" + hex + "]");
        }
    }
    
    @Test
    public void testSslParams() throws Exception {
        final NetworkListener listener = httpServer.getListener(LISTENER_NAME);

        startHttpServer(new HttpHandler() {

            @Override
            public void service(Request request, Response response) throws Exception {
                boolean isOk = request.isSecure();
                String error = "unknown";
                
                if (isOk) {
                    try {
                        assertEquals((Integer) 256, (Integer) request.getAttribute(SSLSupport.KEY_SIZE_KEY));
                        assertNotNull(request.getAttribute(SSLSupport.SESSION_ID_KEY));
                        assertNotNull(request.getAttribute(SSLSupport.CIPHER_SUITE_KEY));
                        assertNotNull(request.getAttribute(SSLSupport.CERTIFICATE_KEY));
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
        
        final MemoryManager mm =
                listener.getTransport().getMemoryManager();
        final Buffer request = Buffers.wrap(mm,
                Utils.loadResourceFile("get-secured.dat"));
        
        Buffer responseBuffer = send("localhost", PORT, request).get(10, TimeUnit.SECONDS);

        // Successful response length is 16 bytes.  This includes the status
        // line and a content-length
        boolean isFailure = responseBuffer.remaining() != 16;

        if (isFailure) {
            byte[] response = new byte[responseBuffer.remaining()];
            responseBuffer.get(response);
            String hex = toHexString(response);
            fail("unexpected response length=" + response.length + " content=[" + hex + "]");
        }
    }
    
    @Test
    public void testAddresses() throws Exception {
        final String expectedRemoteAddr = "10.163.27.8";
        final String expectedLocalAddr = "10.163.25.1";
        final NetworkListener listener = httpServer.getListener(LISTENER_NAME);

        startHttpServer(new HttpHandler() {

            @Override
            public void service(Request request, Response response) throws Exception {
                boolean isOk = false;
                final StringBuilder errorBuilder = new StringBuilder();
                try {
                    String result = request.getRemoteAddr();
                    isOk = expectedRemoteAddr.equals(result);
                    if (!isOk) {
                        errorBuilder.append("Remote host don't match. Expected ")
                                .append(expectedRemoteAddr).append(" but was ")
                                .append(result).append('\n');
                    }
                    
                    String localName = request.getLocalName();
                    String localAddr = request.getLocalAddr();
                    isOk = expectedLocalAddr.equals(localName) &&
                            localName.equals(localAddr);
                    if (!isOk) {
                        errorBuilder.append("Local address and host don't match. Expected=")
                                .append(expectedLocalAddr).append(" Addr=")
                                .append(localAddr).append(" name=")
                                .append(localName).append('\n');
                    }
                } catch (Exception e) {
                    errorBuilder.append(e.toString());
                }
                
                if (isOk) {
                    response.setStatus(200, "FINE");
                } else {
                    LOGGER.warning(errorBuilder.toString());
                    response.setStatus(500, "ERROR");
                }
            }

        });
        
        final MemoryManager mm =
                listener.getTransport().getMemoryManager();
        final Buffer request = Buffers.wrap(mm,
                Utils.loadResourceFile("peer-addr-check.dat"));
        
        Buffer responseBuffer = send("localhost", PORT, request).get(60, TimeUnit.SECONDS);

        // Successful response length is 16 bytes.  This includes the status
        // line and a content-length
        boolean isFailure = responseBuffer.remaining() != 16;

        if (isFailure) {
            byte[] response = new byte[responseBuffer.remaining()];
            responseBuffer.get(response);
            String hex = toHexString(response);
            fail("unexpected response length=" + response.length + " content=[" + hex + "]");
        }
    }
    
    @SuppressWarnings({"unchecked"})
    private Future<Buffer> send(String host, int port, Buffer request) throws Exception {
        final FutureImpl<Buffer> future = SafeFutureImpl.create();

        final FilterChainBuilder builder = FilterChainBuilder.stateless();
        builder.add(new TransportFilter());

        builder.add(new AjpClientMessageFilter());
        builder.add(new BasicAjpTest.ResultFilter(future));

        SocketConnectorHandler connectorHandler = TCPNIOConnectorHandler.builder(
                httpServer.getListener(LISTENER_NAME).getTransport())
                .processor(builder.build())
                .build();

        Future<Connection> connectFuture = connectorHandler.connect(host, port);
        final Connection connection = connectFuture.get(10, TimeUnit.SECONDS);

        connection.write(request);

        return future;
    }

    private void putString(Buffer b, String s) {
        if (s == null) {
            b.putShort((short) 0xFFFF);
        } else {
            final byte[] bytes = s.getBytes();
            b.putShort((short) bytes.length);
            b.put(bytes);
            b.put((byte) 0);
        }
    }
    
    private String toHexString(byte[] response) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < response.length; i++) {
            sb.append(Integer.toHexString(response[i] & 0xFF));
            
            if (i != response.length - 1) {
                sb.append(' ');
            }
        }
        
        return sb.toString();
    }

    private static class ResultFilter extends BaseFilter {

        private final FutureImpl<Buffer> future;

        public ResultFilter(FutureImpl<Buffer> future) {
            this.future = future;
        }

        @Override
        public NextAction handleRead(FilterChainContext ctx) throws IOException {
            final Buffer content = ctx.getMessage();
            try {
                future.result(content);
            } catch (Exception e) {
                future.failure(e);
                e.printStackTrace();
            }

            return ctx.getStopAction();
        }

        @Override
        public NextAction handleClose(FilterChainContext ctx) throws IOException {
            future.failure(new IOException("connection is closed"));
            return ctx.getStopAction();
        }
    }   
}
