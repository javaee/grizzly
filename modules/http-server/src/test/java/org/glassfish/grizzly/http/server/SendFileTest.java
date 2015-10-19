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

package org.glassfish.grizzly.http.server;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.logging.Level;
import junit.framework.TestCase;
import org.glassfish.grizzly.CompletionHandler;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.WriteResult;
import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.FilterChainBuilder;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.filterchain.TransportFilter;
import org.glassfish.grizzly.http.HttpClientFilter;
import org.glassfish.grizzly.http.HttpContent;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.http.HttpResponsePacket;
import org.glassfish.grizzly.http.Method;
import org.glassfish.grizzly.http.Protocol;
import org.glassfish.grizzly.http.util.MimeType;
import org.glassfish.grizzly.http.util.Header;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import org.glassfish.grizzly.nio.transport.TCPNIOTransportBuilder;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.channels.FileChannel;
import java.security.MessageDigest;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import org.glassfish.grizzly.http.CompressionConfig.CompressionMode;

public class SendFileTest extends TestCase {
    
    private static final Logger LOGGER = Grizzly.logger(SendFileTest.class);
    
    private static final int PORT = 9669;


    // ------------------------------------------------------------ Test Methods


    @SuppressWarnings("unchecked")
    public void testSimpleSendFileViaStaticResourceAdapter() throws Exception {
        HttpServer server = createServer(null, false, false);
        File control = generateTempFile(1024, "tmp2");
        final ReusableFuture<File> result = new ReusableFuture<File>();

        TCPNIOTransport client = createClient(result, new ResponseValidator() {
            @Override
            public void validate(HttpResponsePacket response) {
                assertEquals("1024", response.getHeader(Header.ContentLength));
                // static resource handler won't know how to handle .tmp extension,
                // so it should punt.
                assertEquals("text/plain", response.getHeader(Header.ContentType));
            }
        });
        BigInteger controlSum = getMDSum(control);
        try {
            server.start();
            client.start();
            Connection c = client.connect("localhost", PORT).get(10, TimeUnit.SECONDS);
            for (int i = 0; i < 5; i++) {
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
                result.reset();
            }
            c.close();
        } finally {
            client.shutdownNow();
            server.shutdownNow();
        }
    }

    @SuppressWarnings("unchecked")
    public void testSimpleSendFileViaAPISingleArgWithMimeType() throws Exception {
        File control = generateTempFile(1024);
        HttpServer server = createServer(new SendFileApiHandler(control, null), false, false);
        MimeType.add("tmp", "text/temp");
        final ReusableFuture<File> result = new ReusableFuture<File>();

        TCPNIOTransport client = createClient(result, new ResponseValidator() {
            @Override
            public void validate(HttpResponsePacket response) {
                assertEquals("1024", response.getHeader(Header.ContentLength));
                assertEquals("text/temp", response.getHeader(Header.ContentType));
            }
        });
        BigInteger controlSum = getMDSum(control);
        try {
            server.start();
            client.start();
            Connection c = client.connect("localhost", PORT).get(10, TimeUnit.SECONDS);
            for (int i = 0; i < 5; i++) {
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
                result.reset();
            }
            c.close();
        } finally {
            client.shutdownNow();
            server.shutdownNow();
        }
    }


    @SuppressWarnings("unchecked")
    public void testSimpleSendFileViaAPIMultiArgExplicitMimeType() throws Exception {
        File control = generateTempFile(1024);
        HttpServer server = createServer(new SendFileApiHandler(511, 512, "application/tmp"), false, false); // send half
        MimeType.add("tmp", "text/temp");
        final ReusableFuture<File> result = new ReusableFuture<File>();

        TCPNIOTransport client = createClient(result, new ResponseValidator() {
            @Override
            public void validate(HttpResponsePacket response) {
                assertEquals("512", response.getHeader(Header.ContentLength));
                // explicit mime type set
                assertEquals("application/tmp", response.getHeader(Header.ContentType));
            }
        });
        try {
            server.start();
            client.start();
            Connection c = client.connect("localhost", PORT).get(10, TimeUnit.SECONDS);
            for (int i = 0; i < 5; i++) {
                HttpRequestPacket request =
                        HttpRequestPacket.builder().uri("/" + control.getName())
                                .method(Method.GET)
                                .protocol(Protocol.HTTP_1_1)
                                .header("Host", "localhost:" + PORT).build();
                c.write(request);
                File fResult = result.get(20, TimeUnit.SECONDS);
                assertEquals(512, fResult.length());
                result.reset();
            }
            c.close();
        } finally {
            client.shutdownNow();
            server.shutdownNow();
        }
    }

    @SuppressWarnings("unchecked")
    public void testSimpleSendFileViaAPIClearBuffer() throws Exception {
        File control = generateTempFile(1024);
        SendFileApiHandler h = new SendFileApiHandler(control, null);
        h.setSendExtraContent(true);
        HttpServer server = createServer(h, false, false);
        MimeType.add("tmp", "text/temp");
        final ReusableFuture<File> result = new ReusableFuture<File>();
        BigInteger controlSum = getMDSum(control);
        
        TCPNIOTransport client = createClient(result, new ResponseValidator() {
            @Override
            public void validate(HttpResponsePacket response) {
                assertEquals("1024", response.getHeader(Header.ContentLength));
                // explicit mime type set
                assertEquals("text/temp", response.getHeader(Header.ContentType));
            }
        });
        try {
            server.start();
            client.start();
            Connection c = client.connect("localhost", PORT).get(10, TimeUnit.SECONDS);
            for (int i = 0; i < 5; i++) {
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
                result.reset();
            }
            c.close();
        } finally {
            client.shutdownNow();
            server.shutdownNow();
        }
    }

    @SuppressWarnings("unchecked")
    public void testSimpleSendFileViaRequestAttribute() throws Exception {
        File control = generateTempFile(1024);
        HttpHandler h = new SendFileRequestAttributeHandler(control);
        HttpServer server = createServer(h, false, false);
        MimeType.add("tmp", "text/temp");
        final ReusableFuture<File> result = new ReusableFuture<File>();
        BigInteger controlSum = getMDSum(control);

        TCPNIOTransport client = createClient(result, new ResponseValidator() {
            @Override
            public void validate(HttpResponsePacket response) {
                assertEquals("1024", response.getHeader(Header.ContentLength));
                // explicit mime type set
                assertEquals("text/temp", response.getHeader(Header.ContentType));
            }
        });
        try {
            server.start();
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
            result.reset();
            c.close();
        } finally {
            client.shutdownNow();
            server.shutdownNow();
        }
    }
    
    
    @SuppressWarnings("unchecked")
    public void testSimpleSendFileViaRequestAttributeCustomThread() throws Exception {
        File control = generateTempFile(1024);
        SendFileRequestAttributeHandler h = new SendFileRequestAttributeHandler(control);
        ScheduledExecutorService e = Executors.newScheduledThreadPool(1);
        h.setExecutor(e);
        HttpServer server = createServer(h, false, false);
        MimeType.add("tmp", "text/temp");
        final ReusableFuture<File> result = new ReusableFuture<File>();
        BigInteger controlSum = getMDSum(control);

        TCPNIOTransport client = createClient(result, new ResponseValidator() {
            @Override
            public void validate(HttpResponsePacket response) {
                assertEquals("1024", response.getHeader(Header.ContentLength));
                // explicit mime type set
                assertEquals("text/temp", response.getHeader(Header.ContentType));
            }
        });
        try {
            server.start();
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
            result.reset();
            c.close();
        } finally {
            client.shutdownNow();
            server.shutdownNow();
            e.shutdownNow();
        }
    }

    @SuppressWarnings("unchecked")
    public void testSimpleSendFileViaRequestAttributeCustomPosLen() throws Exception {
        File control = generateTempFile(1024);
        HttpHandler h = new SendFileRequestAttributeHandler(511, 512);
        HttpServer server = createServer(h, false, false);
        MimeType.add("tmp", "text/temp");
        final ReusableFuture<File> result = new ReusableFuture<File>();

        TCPNIOTransport client = createClient(result, new ResponseValidator() {
            @Override
            public void validate(HttpResponsePacket response) {
                assertEquals("512", response.getHeader(Header.ContentLength));
                // explicit mime type set
                assertEquals("text/temp", response.getHeader(Header.ContentType));
            }
        });
        try {
            server.start();
            client.start();
            Connection c = client.connect("localhost", PORT).get(10, TimeUnit.SECONDS);
            HttpRequestPacket request =
                    HttpRequestPacket.builder().uri("/" + control.getName())
                            .method(Method.GET)
                            .protocol(Protocol.HTTP_1_1)
                            .header("Host", "localhost:" + PORT).build();
            c.write(request);
            File fResult = result.get(20, TimeUnit.SECONDS);
            assertEquals(512, fResult.length());
            result.reset();
            c.close();
        } finally {
            client.shutdownNow();
            server.shutdownNow();
        }
    }


    @SuppressWarnings("unchecked")
    public void testSimpleSendFileCompressionDisabled() throws Exception {
        File control = generateTempFile(1024);
        HttpHandler h = new SendFileRequestAttributeHandler(control);
        HttpServer server = createServer(h, true, false);
        MimeType.add("tmp", "text/temp");
        final ReusableFuture<File> result = new ReusableFuture<File>();
        BigInteger controlSum = getMDSum(control);
        TCPNIOTransport client = createClient(result, new ResponseValidator() {
            @Override
            public void validate(HttpResponsePacket response) {
                assertEquals("1024", response.getHeader(Header.ContentLength));
                assertNull(response.getHeader(Header.ContentEncoding));
                assertNull(response.getHeader(Header.TransferEncoding));
                // explicit mime type set
                assertEquals("text/temp", response.getHeader(Header.ContentType));
            }
        });
        try {
            server.start();
            client.start();
            Connection c = client.connect("localhost", PORT).get(10, TimeUnit.SECONDS);
            for (int i = 0; i < 5; i++) {
                HttpRequestPacket request =
                        HttpRequestPacket.builder().uri("/" + control.getName())
                                .method(Method.GET)
                                .protocol(Protocol.HTTP_1_1)
                                .header(Header.Host, "localhost:" + PORT)
                                .header(Header.AcceptEncoding, "gzip").build();

                c.write(request);
                File fResult = result.get(20, TimeUnit.SECONDS);
                BigInteger resultSum = getMDSum(fResult);
                assertTrue("MD5Sum between control and test files differ.",
                        controlSum.equals(resultSum));
                result.reset();
            }
            c.close();
        } finally {
            client.shutdownNow();
            server.shutdownNow();
        }
    }


    // --------------------------------------------------------- Private Methods
    
    
    private static final class SendFileRequestAttributeHandler extends HttpHandler {
        private final long pos;
        private final long len;
        private ScheduledExecutorService executor;


        // -------------------------------------------------------- Constructors

        private SendFileRequestAttributeHandler(final File f) {
            pos = 0;
            len = f.length();
        }

        private SendFileRequestAttributeHandler(long pos, long len) {
            this.pos = pos;
            this.len = len;
        }

        // -------------------------------------------- Methods from HttpHandler


        public void setExecutor(ScheduledExecutorService executor) {
            this.executor = executor;
        }

        @Override
        public void service(final Request request, Response response) throws Exception {
            final Runnable r = new Runnable() {
                @Override
                public void run() {
                    final String requestURI = request.getRequestURI();
                    assertTrue((Boolean) request.getAttribute(Request.SEND_FILE_ENABLED_ATTR));
                    final String fileName = requestURI.substring(requestURI.lastIndexOf('/') + 1);
                    File tempDir = new File(System.getProperty("java.io.tmpdir"));
                    File toSend = new File(tempDir, fileName);
                    if (pos != 0) {
                        request.setAttribute(Request.SEND_FILE_START_OFFSET_ATTR, pos);
                    }
                    if (len != toSend.length()) {
                        request.setAttribute(Request.SEND_FILE_WRITE_LEN_ATTR, len);
                    }
                    request.setAttribute(Request.SEND_FILE_ATTR, toSend);

                }
            };
            if (executor != null) {
                response.suspend();
                executor.scheduleWithFixedDelay(r, 5, 5, TimeUnit.SECONDS);
            } else {
                r.run();
            }
        }
    } // END SendFileRequestAttributeHandler
    
    
    private static final class SendFileApiHandler extends HttpHandler {
        
        private final long pos;
        private final long len;
        private final String contentType;
        private boolean sendExtraContent;


        // -------------------------------------------------------- Constructors

        private SendFileApiHandler(final File f, final String contentType) {
            pos = 0;
            len = f.length();
            this.contentType = contentType;
        }

        private SendFileApiHandler(long pos, long len, final String contentType) {
            this.pos = pos;
            this.len = len;
            this.contentType = contentType;
        }


        // -------------------------------------------- Methods from HttpHandler


        @Override
        public void service(final Request request, final Response response) throws Exception {
            final String requestURI = request.getRequestURI();
            final String fileName = requestURI.substring(requestURI.lastIndexOf('/') + 1);
            File tempDir = new File(System.getProperty("java.io.tmpdir"));
            File toSend = new File(tempDir, fileName);
            if (contentType != null) {
                response.setContentType(contentType);
            }
            if (sendExtraContent) {
                response.getOutputBuffer().write("Surprise!");
            }
            response.getOutputBuffer().sendfile(toSend, pos, len, new CompletionHandler<WriteResult>() {
                @Override
                public void cancelled() {
                    fail("Cancelled");
                }

                @Override
                public void failed(Throwable throwable) {
                    throwable.printStackTrace();
                    fail(throwable.getMessage());
                }

                @Override
                public void completed(WriteResult result) {
                    LOGGER.info("Server->Client: File transferred");
                }

                @Override
                public void updated(WriteResult result) {
                }
            });
        }
        
        // ------------------------------------------------------ Public Methods

        public void setSendExtraContent(boolean sendExtraContent) {
            this.sendExtraContent = sendExtraContent;
        }

    } // END SendFileApiHandler
    
    
    private static TCPNIOTransport createClient(final ReusableFuture<File> result,
                                                final ResponseValidator validator) {
        TCPNIOTransport transport = TCPNIOTransportBuilder.newInstance().build();
        FilterChainBuilder builder = FilterChainBuilder.stateless();
        builder.add(new TransportFilter());
        builder.add(new HttpClientFilter());
        builder.add(new BaseFilter() {
            FileChannel out;
            File f;
            long start;
            long stop;

            @Override
            public NextAction handleRead(FilterChainContext ctx) throws IOException {
                HttpContent content = ctx.getMessage();
                out.write(content.getContent().toByteBuffer());
                if (content.isLast()) {
                    stop = System.currentTimeMillis();
                    if (validator != null) {
                        validator.validate((HttpResponsePacket) content.getHttpHeader());
                    }
                    try {
                        out.close();
                        LOGGER.log(Level.INFO, "Client received file ({0} bytes) in {1}ms.",
                                new Object[]{f.length(), stop - start});
                        // result.result(f) should be the last operation in handleRead
                        // otherwise NPE may occur in handleWrite asynchronously
                        result.result(f);
                    } catch (IOException ioe) {
                        ioe.printStackTrace();
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
    
    
    private static HttpServer createServer(HttpHandler handler,
                                           boolean enableCompression,
                                           boolean enableFileCache) {
        final HttpServer server = new HttpServer();
        final NetworkListener listener = 
                new NetworkListener("test", 
                                    NetworkListener.DEFAULT_NETWORK_HOST, 
                                    PORT);
        if (enableCompression) {
            listener.getCompressionConfig().setCompressionMode(CompressionMode.FORCE);
        }
        listener.setSendFileEnabled(true);
        listener.getFileCache().setEnabled(enableFileCache);
        server.addListener(listener);
        if (handler == null) {
            handler = new StaticHttpHandler(System.getProperty("java.io.tmpdir"));
        }
        server.getServerConfiguration().addHttpHandler(handler, "/");
        return server;
    }

    private static BigInteger getMDSum(final File f) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("MD5");
        byte[] b = new byte[8192];
        FileInputStream in = new FileInputStream(f);
        int len;
        while ((len = in.read(b)) != -1) {
            digest.update(b, 0, len);
        }
        return new BigInteger(digest.digest());
    }


    private static File generateTempFile(final int size) throws IOException {
        return generateTempFile(size, "tmp");
    }
    
    private static File generateTempFile(final int size, final String ext) throws IOException {
        final File f = File.createTempFile("grizzly-temp-" + size, "." + ext);
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


    // ---------------------------------------------------------- Nested Classes
    
    
    private interface ResponseValidator {
        
        void validate(HttpResponsePacket response);
        
    }

}
