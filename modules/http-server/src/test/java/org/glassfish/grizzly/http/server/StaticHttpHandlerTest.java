/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012-2016 Oracle and/or its affiliates. All rights reserved.
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
import java.math.BigInteger;
import java.net.URL;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Collection;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.filterchain.*;
import org.glassfish.grizzly.http.*;
import org.glassfish.grizzly.http.util.Header;
import org.glassfish.grizzly.impl.FutureImpl;
import org.glassfish.grizzly.memory.HeapMemoryManager;
import org.glassfish.grizzly.memory.MemoryManager;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import org.glassfish.grizzly.nio.transport.TCPNIOTransportBuilder;
import org.glassfish.grizzly.ssl.SSLContextConfigurator;
import org.glassfish.grizzly.ssl.SSLEngineConfigurator;
import org.glassfish.grizzly.ssl.SSLFilter;
import org.glassfish.grizzly.utils.DelayFilter;
import org.glassfish.grizzly.utils.Futures;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import static org.junit.Assert.*;

/**
 * {@link StaticHttpHandler} test.
 * 
 * @author Alexey Stashok
 */
@RunWith(Parameterized.class)
public class StaticHttpHandlerTest {
    private static final int PORT = 18900;
    private static final Logger LOGGER = Grizzly.logger(StaticHttpHandlerTest.class);

    @Parameterized.Parameters
    public static Collection<Object[]> getMode() {
        return Arrays.asList(new Object[][]{
                    {Boolean.FALSE, Boolean.FALSE, new HeapMemoryManager()},
                    {Boolean.FALSE, Boolean.TRUE, new HeapMemoryManager()},
                    {Boolean.TRUE, Boolean.FALSE, new HeapMemoryManager()},
                    {Boolean.TRUE, Boolean.TRUE, new HeapMemoryManager()},
                });
    }

    private HttpServer httpServer;
    
    final boolean isSslEnabled;
    final boolean isFileSendEnabled;
    final MemoryManager<?> memoryManager;
    
    public StaticHttpHandlerTest(boolean isFileSendEnabled,
            boolean isSslEnabled, MemoryManager<?> memoryManager) {
        this.isFileSendEnabled = isFileSendEnabled;
        this.isSslEnabled = isSslEnabled;
        this.memoryManager = memoryManager;
    }

    @Before
    public void before() throws Exception {
        httpServer = createServer(isFileSendEnabled, isSslEnabled, memoryManager);
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
    public void testSlowClient() throws Exception {
        final int fileSize = 16 * 1024 * 1024;
        File control = generateTempFile(fileSize);
        
        final FutureImpl<File> result = Futures.createSafeFuture();

        TCPNIOTransport client = createClient(result, new ResponseValidator() {
            @Override
            public void validate(HttpResponsePacket response) {
                assertEquals(Integer.toString(fileSize), response.getHeader(Header.ContentLength));
                // static resource handler won't know how to handle .tmp extension,
                // so it should punt.
                assertEquals("text/plain", response.getHeader(Header.ContentType));
            }
        }, isSslEnabled);
        BigInteger controlSum = getMDSum(control);
        try {
            client.start();
            Connection c = client.connect("localhost", PORT).get(10, TimeUnit.SECONDS);
            
            HttpRequestPacket request =
                    HttpRequestPacket.builder().uri("/" + control.getName())
                        .method(Method.GET)
                        .protocol(Protocol.HTTP_1_1)
                        .header("Host", "localhost:" + PORT).build();
            c.write(request);
            File fResult = result.get(20, TimeUnit.SECONDS);
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
    public void testPostMethod() throws Exception {
        final int fileSize = 16 * 1024 * 1024;
        File control = generateTempFile(fileSize);
        
        final FutureImpl<File> result = Futures.createSafeFuture();

        TCPNIOTransport client = createClient(result, new StaticHttpHandlerTest.ResponseValidator() {
            @Override
            public void validate(HttpResponsePacket response) {
                assertEquals(405, response.getStatus());
                assertEquals("GET", response.getHeader(Header.Allow));
            }
        }, isSslEnabled);
        try {
            client.start();
            Connection c = client.connect("localhost", PORT).get(10, TimeUnit.SECONDS);
            
            HttpRequestPacket request =
                    HttpRequestPacket.builder().uri("/" + control.getName())
                        .method(Method.POST)
                        .protocol(Protocol.HTTP_1_1)
                        .header("Host", "localhost:" + PORT).build();
            c.write(request);
            File fResult = result.get(20, TimeUnit.SECONDS);
            //assertEquals(0, fResult.length());
            
            c.close();
        } finally {
            client.shutdownNow();
        }        
    }
    
    /**
     * Make sure we receive 301 redirect, when trying to access directory
     * without trailing slash.
     * 
     * @throws Exception 
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testDirectoryTrailingSlash() throws Exception {
        final File tmpDir = createTempFolder();
        
        final FutureImpl<File> result = Futures.createSafeFuture();

        TCPNIOTransport client = createClient(result, new StaticHttpHandlerTest.ResponseValidator() {
            @Override
            public void validate(HttpResponsePacket response) {
                assertEquals(301, response.getStatus());
                assertEquals("/" + tmpDir.getName() + "/", response.getHeader(Header.Location));
            }
        }, isSslEnabled);
        try {
            client.start();
            Connection c = client.connect("localhost", PORT).get(10, TimeUnit.SECONDS);
            
            HttpRequestPacket request =
                    HttpRequestPacket.builder().uri("/" + tmpDir.getName())
                        .method(Method.POST)
                        .protocol(Protocol.HTTP_1_1)
                        .header("Host", "localhost:" + PORT).build();
            c.write(request);
            File fResult = result.get(20, TimeUnit.SECONDS);
            //assertEquals(0, fResult.length());
            
            c.close();
        } finally {
            client.shutdownNow();
        }        
    }
    
    private static TCPNIOTransport createClient(final FutureImpl<File> result,
            final ResponseValidator validator,
            final boolean isSslEnabled) throws Exception {
        TCPNIOTransport transport = TCPNIOTransportBuilder.newInstance().build();
        FilterChainBuilder builder = FilterChainBuilder.stateless();
        builder.add(new TransportFilter());
        
        // simulate slow read
        builder.add(new DelayFilter(5, 0));
        
        if (isSslEnabled) {
            final SSLFilter sslFilter = new SSLFilter(createSSLConfig(true),
                    createSSLConfig(false));
            builder.add(sslFilter);
        }
        
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
                return super.handleWrite(ctx);
            }
        });
        
        transport.setProcessor(builder.build());
        return transport;
    }
    
    private static HttpServer createServer(
            boolean isFileSendEnabled, boolean isSslEnabled,
            MemoryManager<?> memoryManager) throws Exception {
        
        final HttpServer server = new HttpServer();
        final NetworkListener listener = 
                new NetworkListener("test", 
                                    NetworkListener.DEFAULT_NETWORK_HOST, 
                                    PORT);
        
        listener.getTransport().setMemoryManager(memoryManager);
        
        if (isSslEnabled) {
            listener.setSecure(true);
            listener.setSSLEngineConfig(createSSLConfig(true));
        }
        
        listener.setSendFileEnabled(isFileSendEnabled);
        server.addListener(listener);
        server.getServerConfiguration().addHttpHandler(
                new StaticHttpHandler(getSystemTmpDir()), "/");
        
        return server;
    }

    private static BigInteger getMDSum(final File f) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("MD5");
        byte[] b = new byte[8192];
        FileInputStream in = new FileInputStream(f);
        try {
            int len;
            while ((len = in.read(b)) != -1) {
                digest.update(b, 0, len);
            }
        } finally {
            in.close();
        }
        return new BigInteger(digest.digest());
    }

    private static String getSystemTmpDir() {
        return System.getProperty("java.io.tmpdir");
    }

    private static File generateTempFile(final int size) throws IOException {
        final File f = File.createTempFile("grizzly-temp-" + size, ".tmp2");
        Random r = new Random();
        byte[] data = new byte[8192];
        r.nextBytes(data);
        FileOutputStream out = new FileOutputStream(f);
        int total = 0;
        int remaining = size;
        while (total < size) {
            int len = ((remaining > 8192) ? 8192 : remaining);
            out.write(data, 0, len);
            total += len;
            remaining -= len;
        }
        f.deleteOnExit();
        return f;
    }
    
    private static File createTempFolder() throws IOException {
        final File tmpDir = Files.createTempDirectory("grizzly-temp-dir").toFile();
        tmpDir.deleteOnExit();
        return tmpDir;
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
    
    
    private interface ResponseValidator {
        
        void validate(HttpResponsePacket response);
        
    }    
}
