/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011-2013 Oracle and/or its affiliates. All rights reserved.
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
package org.glassfish.grizzly.servlet.extras;

import junit.framework.Assert;
import junit.framework.TestCase;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.CloseListener;
import org.glassfish.grizzly.CloseType;
import org.glassfish.grizzly.Closeable;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.EmptyCompletionHandler;
import org.glassfish.grizzly.GenericCloseListener;
import org.glassfish.grizzly.SocketConnectorHandler;
import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.FilterChainBuilder;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.filterchain.TransportFilter;
import org.glassfish.grizzly.http.HttpClientFilter;
import org.glassfish.grizzly.http.HttpContent;
import org.glassfish.grizzly.http.HttpPacket;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.http.HttpResponsePacket;
import org.glassfish.grizzly.http.Method;
import org.glassfish.grizzly.http.Protocol;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.grizzly.impl.FutureImpl;
import org.glassfish.grizzly.impl.SafeFutureImpl;
import org.glassfish.grizzly.nio.transport.TCPNIOConnectorHandler;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import org.glassfish.grizzly.nio.transport.TCPNIOTransportBuilder;
import org.glassfish.grizzly.servlet.extras.util.MultipartEntryPacket;
import org.glassfish.grizzly.servlet.extras.util.MultipartPacketBuilder;
import org.glassfish.grizzly.utils.ChunkingFilter;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.InetSocketAddress;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.glassfish.grizzly.servlet.FilterRegistration;
import org.glassfish.grizzly.servlet.ServletRegistration;
import org.glassfish.grizzly.servlet.WebappContext;
import org.glassfish.grizzly.utils.Futures;

public class MultipartUploadFilterTest extends TestCase {

    private static final int PORT = 9977;

    public void testBasicMultipartUploadFilter001() throws Exception {

        HttpServer httpServer = HttpServer.createSimpleServer(".", 9977);
        WebappContext ctx = new WebappContext("Upload Test");
        final String fileContent = "One Ring to rule them all, One Ring to find them,\n" +
                        "One Ring to bring them all and in the darkness bind them.";
        FilterRegistration filterRegistration =
                ctx.addFilter("UploadFilter", MultipartUploadFilter.class.getName());
        filterRegistration.addMappingForUrlPatterns(null, "/upload");
        final AtomicReference<File> uploadedFile = new AtomicReference<File>();
        final ServletRegistration servletRegistration =
                ctx.addServlet("UploadValidationServlet", new HttpServlet() {
            @Override
            protected void doPost(HttpServletRequest req,
                                  HttpServletResponse resp) throws ServletException, IOException {

                final Object value = req.getAttribute(MultipartUploadFilter.UPLOADED_FILES);
                Assert.assertNotNull(value);
                Assert.assertTrue(value instanceof File[]);
                final File[] uploadedFiles = (File[]) value;
                Assert.assertEquals(1, uploadedFiles.length);
                final File f = uploadedFiles[0];
                Reader r = new InputStreamReader(new FileInputStream(f));
                char[] buf = new char[512];
                int read = r.read(buf);
                r.close();
                Assert.assertEquals(fileContent, new String(buf, 0, read));
                Assert.assertTrue(f.exists());
                Assert.assertTrue(f.canRead());
                uploadedFile.set(f);
            }
        });
        servletRegistration.addMapping("/upload");

        try {
            ctx.deploy(httpServer);
            httpServer.start();

            final TCPNIOTransport clientTransport = TCPNIOTransportBuilder.newInstance().build();
            clientTransport.start();
            HttpClient client = new HttpClient(clientTransport);
            Future conn = client.connect("localhost", PORT);
            conn.get(10, TimeUnit.SECONDS);
            Future<HttpPacket> future = client.get(createMultipartPacket(fileContent));
            HttpPacket packet = future.get(10, TimeUnit.SECONDS);
            HttpResponsePacket response = (HttpResponsePacket) ((HttpContent) packet).getHttpHeader();
            Assert.assertEquals(200, response.getStatus());
            File f = uploadedFile.get();
            Assert.assertNotNull(f);
            Assert.assertFalse(f.exists());
        } finally {
            httpServer.shutdownNow();
        }
    }

    public void testBasicMultipartUploadFilter002() throws Exception {

        HttpServer httpServer = HttpServer.createSimpleServer(".", 9977);
        WebappContext ctx = new WebappContext("Upload Test");
        final String fileContent = "One Ring to rule them all, One Ring to find them,\n" +
                        "One Ring to bring them all and in the darkness bind them.";
        FilterRegistration filterRegistration =
                ctx.addFilter("UploadFilter", MultipartUploadFilter.class.getName());
        filterRegistration.setInitParameter(MultipartUploadFilter.DELETE_ON_REQUEST_END, "false");
        filterRegistration.addMappingForUrlPatterns(null, "/upload");
        final AtomicReference<File> uploadedFile = new AtomicReference<File>();
        final ServletRegistration servletRegistration =
                ctx.addServlet("UploadValidationServlet", new HttpServlet() {
            @Override
            protected void doPost(HttpServletRequest req,
                                  HttpServletResponse resp) throws ServletException, IOException {

                final Object value = req.getAttribute(MultipartUploadFilter.UPLOADED_FILES);
                Assert.assertNotNull(value);
                Assert.assertTrue(value instanceof File[]);
                final File[] uploadedFiles = (File[]) value;
                Assert.assertEquals(1, uploadedFiles.length);
                final File f = uploadedFiles[0];
                Reader r = new InputStreamReader(new FileInputStream(f));
                char[] buf = new char[512];
                int read = r.read(buf);
                Assert.assertEquals(fileContent, new String(buf, 0, read));
                Assert.assertTrue(f.exists());
                Assert.assertTrue(f.canRead());
                uploadedFile.set(f);
            }
        });
        servletRegistration.addMapping("/upload");

        try {
            ctx.deploy(httpServer);
            httpServer.start();

            final TCPNIOTransport clientTransport = TCPNIOTransportBuilder.newInstance().build();
            clientTransport.start();
            HttpClient client = new HttpClient(clientTransport);
            Future conn = client.connect("localhost", PORT);
            conn.get(10, TimeUnit.SECONDS);
            Future<HttpPacket> future = client.get(createMultipartPacket(fileContent));
            HttpPacket packet = future.get(10, TimeUnit.SECONDS);
            HttpResponsePacket response = (HttpResponsePacket) ((HttpContent) packet).getHttpHeader();
            Assert.assertEquals(200, response.getStatus());
            File f = uploadedFile.get();
            Assert.assertNotNull(f);
            Assert.assertTrue(f.exists());
            f.deleteOnExit();
        } finally {
            httpServer.shutdownNow();
        }
    }


    // --------------------------------------------------------- Private Methods


    private HttpPacket createMultipartPacket(final String content) {
        String boundary = "---------------------------103832778631715";
        MultipartPacketBuilder mpb = MultipartPacketBuilder.builder(boundary);
        mpb.preamble("preamble").epilogue("epilogue");

        mpb.addMultipartEntry(MultipartEntryPacket.builder()
                .contentDisposition("form-data; name=\"test.txt\"; filename=\"test.txt\"")
                .content(content)
                .build());


        final Buffer bodyBuffer = mpb.build();

        final HttpRequestPacket requestHeader = HttpRequestPacket.builder()
                .method(Method.POST)
                .uri("/upload")
                .protocol(Protocol.HTTP_1_1)
                .header("host", "localhost")
                .contentType("multipart/form-data; boundary=" + boundary)
                .contentLength(bodyBuffer.remaining())
                .build();

        return HttpContent.builder(requestHeader)
                .content(bodyBuffer)
                .build();
    }


    // ---------------------------------------------------------- Nested Classes


    private static class HttpClient {
        private final TCPNIOTransport transport;
        private final int chunkSize;

        private volatile Connection connection;
        private volatile FutureImpl<HttpPacket> asyncFuture;

        public HttpClient(TCPNIOTransport transport) {
            this(transport, -1);
        }

        public HttpClient(TCPNIOTransport transport, int chunkSize) {
            this.transport = transport;
            this.chunkSize = chunkSize;
        }

        public Future<Connection> connect(String host, int port) throws IOException {
            FilterChainBuilder filterChainBuilder = FilterChainBuilder.stateless();
            filterChainBuilder.add(new TransportFilter());

            if (chunkSize > 0) {
                filterChainBuilder.add(new ChunkingFilter(chunkSize));
            }

            filterChainBuilder.add(new HttpClientFilter());
            filterChainBuilder.add(new HttpResponseFilter());

            final SocketConnectorHandler connector =
                    TCPNIOConnectorHandler.builder(transport)
                    .processor(filterChainBuilder.build())
                    .build();

            final FutureImpl<Connection> future =
                    Futures.<Connection>createSafeFuture();
            
            connector.connect(new InetSocketAddress(host, port),
                    Futures.toCompletionHandler(future, 
                    new EmptyCompletionHandler<Connection>() {
                @Override
                public void completed(Connection result) {
                    connection = result;
                }
            }));
            
            return future;
        }

        public Future<HttpPacket> get(HttpPacket request) throws IOException {
            final FutureImpl<HttpPacket> localFuture = SafeFutureImpl.<HttpPacket>create();
            asyncFuture = localFuture;
            connection.write(request, new EmptyCompletionHandler() {

                @Override
                public void failed(Throwable throwable) {
                    localFuture.failure(throwable);
                }
            });

            connection.addCloseListener(new GenericCloseListener() {

                @Override
                public void onClosed(Closeable closeable, CloseType type)
                        throws IOException {
                    localFuture.failure(new IOException());
                }
            });
            return localFuture;
        }

        public void close() throws IOException {
            if (connection != null) {
                connection.close();
            }
        }

        private class HttpResponseFilter extends BaseFilter {
            @Override
            public NextAction handleRead(FilterChainContext ctx) throws IOException {
                HttpContent message = (HttpContent) ctx.getMessage();
                if (message.isLast()) {
                    final FutureImpl<HttpPacket> localFuture = asyncFuture;
                    asyncFuture = null;
                    localFuture.result(message);

                    return ctx.getStopAction();
                }

                return ctx.getStopAction(message);
            }
        } // END HttpResponseFilter

    } // END HttpClient

}
