/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved.
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
package org.glassfish.grizzly.http.server;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.URL;
import java.nio.channels.FileChannel;
import java.security.MessageDigest;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.filterchain.*;
import org.glassfish.grizzly.http.*;
import org.glassfish.grizzly.http.util.Header;
import org.glassfish.grizzly.impl.FutureImpl;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import org.glassfish.grizzly.nio.transport.TCPNIOTransportBuilder;
import org.glassfish.grizzly.ssl.SSLContextConfigurator;
import org.glassfish.grizzly.ssl.SSLEngineConfigurator;
import org.glassfish.grizzly.utils.Futures;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * {@link CLStaticHttpHandler} test.
 * 
 * @author Alexey Stashok
 */
public class CLStaticHttpHandlerTest {
    private static final int PORT = 18900;
    private static final Logger LOGGER = Grizzly.logger(CLStaticHttpHandlerTest.class);

    private HttpServer httpServer;
    
    @Before
    public void before() throws Exception {
        httpServer = createServer();
        httpServer.start();
    }

    @After
    public void after() throws Exception {
        if (httpServer != null) {
            httpServer.shutdownNow();
        }
    }
    
    @Test
    @SuppressWarnings("unchecked")
    public void testNonJarResource() throws Exception {
        final String fileName = "org/glassfish/grizzly/http/server/CLStaticHttpHandler.class";
        final int fileSize = getResourceSize(fileName);
        
        final FutureImpl<File> result = Futures.<File>createSafeFuture();

        TCPNIOTransport client = createClient(result, new CLStaticHttpHandlerTest.ResponseValidator() {
            @Override
            public void validate(HttpResponsePacket response) {
                assertEquals(Integer.toString(fileSize), response.getHeader(Header.ContentLength));
                assertEquals("application/java", response.getHeader(Header.ContentType));
            }
        });
        
        try {
            BigInteger controlSum = getMDSum(fileName);
            client.start();
            Connection c = client.connect("localhost", PORT).get(10, TimeUnit.SECONDS);
            
            HttpRequestPacket request =
                    HttpRequestPacket.builder().uri("/" + fileName)
                        .method(Method.GET)
                        .protocol(Protocol.HTTP_1_1)
                        .header("Host", "localhost:" + PORT).build();
            c.write(request);
            File fResult = result.get(60, TimeUnit.SECONDS);
            BigInteger resultSum = getMDSum(fResult);
            assertTrue("MD5Sum between control and test files differ.",
                        controlSum.equals(resultSum));
            
            c.close();
        } finally {
            client.shutdownNow();
        }        
    }
    
    @Test
    @SuppressWarnings("unchecked")
    public void testJarResource() throws Exception {
        final String fileName = "java/lang/String.class";
        
        final FutureImpl<File> result = Futures.<File>createSafeFuture();

        TCPNIOTransport client = createClient(result, new CLStaticHttpHandlerTest.ResponseValidator() {
            @Override
            public void validate(HttpResponsePacket response) {
                assertTrue(response.isChunked());
                // static resource handler won't know how to handle .tmp extension,
                // so it should punt.
                assertEquals("application/java", response.getHeader(Header.ContentType));
            }
        });
        
        try {
            BigInteger controlSum = getMDSum(fileName);
            client.start();
            Connection c = client.connect("localhost", PORT).get(10, TimeUnit.SECONDS);
            
            HttpRequestPacket request =
                    HttpRequestPacket.builder().uri("/" + fileName)
                        .method(Method.GET)
                        .protocol(Protocol.HTTP_1_1)
                        .header("Host", "localhost:" + PORT).build();
            c.write(request);
            File fResult = result.get(60, TimeUnit.SECONDS);
            BigInteger resultSum = getMDSum(fResult);
            assertTrue("MD5Sum between control and test files differ.",
                        controlSum.equals(resultSum));
            
            c.close();
        } finally {
            client.shutdownNow();
        }        
    }
    
    private static TCPNIOTransport createClient(final FutureImpl<File> result,
            final ResponseValidator validator) throws Exception {
        TCPNIOTransport transport = TCPNIOTransportBuilder.newInstance().build();
        FilterChainBuilder builder = FilterChainBuilder.stateless();
        builder.add(new TransportFilter());
        
        builder.add(new HttpClientFilter());
        builder.add(new BaseFilter() {
            int total;
            FileChannel out;
            File f;
            long start;
            long stop;

            @Override
            public NextAction handleRead(FilterChainContext ctx) throws IOException {
                HttpContent content = ctx.getMessage();
                final int remaining = content.getContent().remaining();
                total += remaining;
//                System.out.println(new Date() + "size=" + remaining + " total=" + total);
                out.write(content.getContent().toByteBuffer());
                if (content.isLast()) {
                    total = 0;
                    stop = System.currentTimeMillis();
                    try {
                        if (validator != null) {
                            validator.validate((HttpResponsePacket) content.getHttpHeader());
                        }
                        
                        out.close();
                        LOGGER.log(Level.INFO, "Client received file ({0} bytes) in {1}ms.",
                                new Object[]{f.length(), stop - start});
                        // result.result(f) should be the last operation in handleRead
                        // otherwise NPE may occur in handleWrite asynchronously
                        result.result(f);
                    } catch (Throwable e) {
                        result.failure(e);
                    }
                }
                return ctx.getStopAction();
            }

            @Override
            public NextAction handleWrite(FilterChainContext ctx) throws IOException {
                try {
                    if (f != null) {
                        if (!f.delete()) {
                            LOGGER.log(Level.WARNING, "Unable to explicitly delete file: {0}", f.getAbsolutePath());
                        }
                        f = null;
                    }
                    f = File.createTempFile("grizzly-test-", ".tmp");
                    f.deleteOnExit();
                    out = new FileOutputStream(f).getChannel();
                } catch (IOException ioe) {
                    throw new RuntimeException(ioe);
                }
                start = System.currentTimeMillis();
                return ctx.getInvokeAction();
            }
        });
        
        transport.setProcessor(builder.build());
        return transport;
    }
    
    private static HttpServer createServer() throws Exception {
        
        final HttpServer server = new HttpServer();
        final NetworkListener listener = 
                new NetworkListener("test", 
                                    NetworkListener.DEFAULT_NETWORK_HOST, 
                                    PORT);
        
        server.addListener(listener);
        server.getServerConfiguration().addHttpHandler(
                new CLStaticHttpHandler(CLStaticHttpHandlerTest.class.getClassLoader()), "/");
        
        return server;
    }

    private static BigInteger getMDSum(final InputStream in) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("MD5");
        byte[] b = new byte[8192];
        int len;
        while ((len = in.read(b)) != -1) {
            digest.update(b, 0, len);
        }
        return new BigInteger(digest.digest());
    }
    
    private static BigInteger getMDSum(final String resource) throws Exception {
        final URL url = StaticHttpHandlerTest.class.getClassLoader().getResource(resource);
        if (url == null) {
            throw new IOException("Resource " + resource + " was not found");
        }
        
        final InputStream in = url.openStream();
        
        try {
            return getMDSum(in);
        } finally {
            in.close();
        }
    }
    
    private static BigInteger getMDSum(final File f) throws Exception {
        FileInputStream in = new FileInputStream(f);
        try {
            return getMDSum(in);
        } finally {
            in.close();
        }
    }
    
    private static int getResourceSize(String resource) throws IOException {
        final URL url = StaticHttpHandlerTest.class.getClassLoader().getResource(resource);
        if (url == null) {
            throw new IOException("Resource " + resource + " was not found");
        }
        
        final InputStream in = url.openStream();
        final byte[] buf = new byte[2048];
        
        int size = 0;
        
        try {
            do {
                final int readNow = in.read(buf);
                if (readNow < 0) {
                    break;
                }

                size += readNow;
            } while (true);
        } finally {
            in.close();
        }
        
        return size;
    }
    
    private static SSLEngineConfigurator createSSLConfig(boolean isServer) throws Exception {
        final SSLContextConfigurator sslContextConfigurator =
                new SSLContextConfigurator();
        final ClassLoader cl = SuspendTest.class.getClassLoader();
        // override system properties
        final URL cacertsUrl = cl.getResource("ssltest-cacerts.jks");
        if (cacertsUrl != null) {
            sslContextConfigurator.setTrustStoreFile(cacertsUrl.getFile());
            sslContextConfigurator.setTrustStorePass("changeit");
        }

        // override system properties
        final URL keystoreUrl = cl.getResource("ssltest-keystore.jks");
        if (keystoreUrl != null) {
            sslContextConfigurator.setKeyStoreFile(keystoreUrl.getFile());
            sslContextConfigurator.setKeyStorePass("changeit");
        }

        return new SSLEngineConfigurator(sslContextConfigurator.createSSLContext(),
                !isServer, false, false);
    }
    
    // ---------------------------------------------------------- Nested Classes

    private static interface ResponseValidator {
        
        void validate(HttpResponsePacket response);
        
    }    
}
