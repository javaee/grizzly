/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2013-2015 Oracle and/or its affiliates. All rights reserved.
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
import java.net.URLClassLoader;
import java.nio.channels.FileChannel;
import java.security.MessageDigest;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.filterchain.*;
import org.glassfish.grizzly.http.*;
import org.glassfish.grizzly.http.util.Header;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import org.glassfish.grizzly.nio.transport.TCPNIOTransportBuilder;
import org.glassfish.grizzly.utils.Futures;
import org.junit.AfterClass;
import org.junit.BeforeClass;
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
    
    private static URLClassLoader folderClassLoader;
    private static URLClassLoader jarClassLoader;

    private static HttpServer httpServer;
    
    @BeforeClass
    public static void before() throws Exception {
        String folder = "file://" +
                CLStaticHttpHandlerTest.class.getClassLoader()
                .getResource("clhandler").getFile() + "/"; // make sure the folder ends with '/'
        
        String jar = folder + "welcome.jar";
        
        folderClassLoader = new URLClassLoader(new URL[] {new URL(folder)}, null);
        jarClassLoader = new URLClassLoader(new URL[] {new URL(jar)}, null);
        
        httpServer = createServer();
        httpServer.start();
    }

    @AfterClass
    public static void after() throws Exception {
        if (httpServer != null) {
            httpServer.shutdownNow();
        }
    }
    
    @Test
    public void testNonJarResource() throws Exception {
        final String fileName = "org/glassfish/grizzly/http/server/CLStaticHttpHandler.class";
        final int fileSize = getResourceSize(fileName);
        
        Client client = Client.create(new ResponseValidator() {
            @Override
            public void validate(HttpResponsePacket response) {
                assertEquals(Integer.toString(fileSize), response.getHeader(Header.ContentLength));
                assertEquals("application/java", response.getHeader(Header.ContentType));
            }
        });
        
        try {
            BigInteger controlSum = getMDSum(fileName);
            BigInteger resultSum = client.getUrlResourceMDSum("/jdk/" + fileName);
            
            assertTrue("MD5Sum between control and test files differ.",
                        controlSum.equals(resultSum));
            
        } finally {
            client.shutdown();
        }        
    }
    
    @Test
    public void testJarResource() throws Exception {
        final String fileName = "java/lang/String.class";
        
        Client client = Client.create(new ResponseValidator() {
            @Override
            public void validate(HttpResponsePacket response) {
                assertTrue(response.isChunked());
                assertEquals("application/java", response.getHeader(Header.ContentType));
            }
        });
        
        try {
            BigInteger controlSum = getMDSum(fileName);
            BigInteger resultSum = client.getUrlResourceMDSum("/jdk/" + fileName);

            assertTrue("MD5Sum between control and test files differ.",
                        controlSum.equals(resultSum));
        } finally {
            client.shutdown();
        }        
    }
    
    @Test
    public void testNonJarWelcomeResource() throws Exception {
        final String fileName1 = "index.html";
        final String fileName2 = "a/index.html";
        
        final int file1Size = getResourceSize(folderClassLoader, fileName1);
        final int file2Size = getResourceSize(folderClassLoader, fileName2);
        
        Client client = Client.create(new ResponseValidator() {
            @Override
            public void validate(HttpResponsePacket response) {
                
                final int size = response.getRequest().getRequestURI().endsWith("/a")
                        ? file2Size
                        : file1Size;
                
                assertEquals(size, response.getContentLength());
                assertEquals("text/html", response.getHeader(Header.ContentType));
            }
        });
        
        
        try {
            // check the welcome at root folder
            BigInteger controlSum = getMDSum(folderClassLoader, fileName1);
            BigInteger resultSum = client.getUrlResourceMDSum("/folder/");
            
            assertTrue("MD5Sum between control and test files differ.",
                        controlSum.equals(resultSum));
            
            // check the welcome in sub-folder
            controlSum = getMDSum(folderClassLoader, fileName2);
            resultSum = client.getUrlResourceMDSum("/folder/a/");
            
            assertTrue("MD5Sum between control and test files differ.",
                        controlSum.equals(resultSum));
        } finally {
            client.shutdown();
        }        
    }
    
    @Test
    public void testJarWelcomeResource() throws Exception {
        final String fileName1 = "index.html";
        final String fileName2 = "a/index.html";
        
        final int file1Size = getResourceSize(jarClassLoader, fileName1);
        final int file2Size = getResourceSize(jarClassLoader, fileName2);
        
        Client client = Client.create(new ResponseValidator() {
            @Override
            public void validate(HttpResponsePacket response) {
                
                final int size = response.getRequest().getRequestURI().endsWith("/a")
                        ? file2Size
                        : file1Size;
                
                assertEquals(size, response.getContentLength());
                assertEquals("text/html", response.getHeader(Header.ContentType));
            }
        });
        
        
        try {
            // check the welcome at root folder
            BigInteger controlSum = getMDSum(jarClassLoader, fileName1);
            BigInteger resultSum = client.getUrlResourceMDSum("/jar/");
            
            assertTrue("MD5Sum between control and test files differ.",
                        controlSum.equals(resultSum));
            
            // check the welcome in sub-folder
            controlSum = getMDSum(jarClassLoader, fileName2);
            resultSum = client.getUrlResourceMDSum("/jar/a/");
            
            assertTrue("MD5Sum between control and test files differ.",
                        controlSum.equals(resultSum));
        } finally {
            client.shutdown();
        }        
    }
    
    private static HttpServer createServer() throws Exception {
        
        final HttpServer server = new HttpServer();
        final NetworkListener listener = 
                new NetworkListener("test", 
                                    NetworkListener.DEFAULT_NETWORK_HOST, 
                                    PORT);
        
        server.addListener(listener);
        server.getServerConfiguration().addHttpHandler(
                new CLStaticHttpHandler(CLStaticHttpHandlerTest.class.getClassLoader()), "/jdk");
        
        server.getServerConfiguration().addHttpHandler(
                new CLStaticHttpHandler(CLStaticHttpHandlerTest.class.getClassLoader()), "/jdk");
        
        server.getServerConfiguration().addHttpHandler(new CLStaticHttpHandler(folderClassLoader), "/folder");
        server.getServerConfiguration().addHttpHandler(new CLStaticHttpHandler(jarClassLoader), "/jar");
        
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
        return getMDSum(StaticHttpHandlerTest.class.getClassLoader(), resource);
    }
    
    private static BigInteger getMDSum(final ClassLoader classloader,
            final String resource) throws Exception {
        final URL url = classloader.getResource(resource);
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
        return getResourceSize(StaticHttpHandlerTest.class.getClassLoader(), resource);
    }

    private static int getResourceSize(ClassLoader classloader, String resource)
            throws IOException {
        final URL url = classloader.getResource(resource);
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
    
    // ---------------------------------------------------------- Nested Classes

    private static class Client {

        private static Client create(final ResponseValidator validator) throws IOException {
            final BlockingQueue<Future<File>> resultQueue =
                    new LinkedBlockingQueue<Future<File>>();
            
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
                            resultQueue.add(Futures.createReadyFuture(f));
                        } catch (Throwable e) {
                            resultQueue.add(Futures.<File>createReadyFuture(e));
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
            
            transport.start();
            
            return new Client(transport, resultQueue);
        }
        
        private final TCPNIOTransport transport;
        private final BlockingQueue<Future<File>> resultQueue;

        public Client(final TCPNIOTransport transport,
                final BlockingQueue<Future<File>> resultQueue) {
            this.transport = transport;
            this.resultQueue = resultQueue;
        }
        
        @SuppressWarnings("unchecked")
        public BigInteger getUrlResourceMDSum(final String url)
                throws Exception {

            final Connection c = transport.connect("localhost", PORT)
                    .get(10, TimeUnit.SECONDS);

            try {
                final HttpRequestPacket request
                        = HttpRequestPacket.builder().uri(url)
                        .method(Method.GET)
                        .protocol(Protocol.HTTP_1_1)
                        .header("Host", "localhost:" + PORT).build();
                c.write(request);
                final Future<File> result = resultQueue.poll(60, TimeUnit.SECONDS);

                if (result == null) {
                    throw new TimeoutException("Timeout expired while waiting for the response");
                }

                final File fResult = result.get(60, TimeUnit.SECONDS);

                return getMDSum(fResult);
            } finally {
                c.close();
            }
        }
    
        public void shutdown() throws IOException {
            transport.shutdownNow();
        }
    }
    
    private interface ResponseValidator {
        
        void validate(HttpResponsePacket response);
        
    }
}
