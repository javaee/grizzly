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

package org.glassfish.grizzly.http2;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.Arrays;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLContext;
import org.glassfish.grizzly.CloseListener;
import org.glassfish.grizzly.Closeable;

import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.ICloseType;
import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.FilterChain;
import org.glassfish.grizzly.filterchain.FilterChainBuilder;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.filterchain.TransportFilter;
import org.glassfish.grizzly.http.HttpClientFilter;
import org.glassfish.grizzly.http.HttpContent;
import org.glassfish.grizzly.http.HttpHeader;
import org.glassfish.grizzly.http.HttpPacket;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.http.HttpResponsePacket;
import org.glassfish.grizzly.http.Method;
import org.glassfish.grizzly.http.Protocol;
import org.glassfish.grizzly.http.util.Header;
import org.glassfish.grizzly.impl.FutureImpl;
import org.glassfish.grizzly.memory.Buffers;

import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import org.glassfish.grizzly.nio.transport.TCPNIOTransportBuilder;
import org.glassfish.grizzly.ssl.SSLContextConfigurator;
import org.glassfish.grizzly.ssl.SSLEngineConfigurator;
import org.glassfish.grizzly.ssl.SSLFilter;
import org.glassfish.grizzly.threadpool.GrizzlyExecutorService;
import org.glassfish.grizzly.utils.Futures;
import org.junit.Test;

/**
 *
 * @author oleksiys
 */
@SuppressWarnings("unchecked")
public class TestClient {
    public static void main(String[] args) throws IOException {
        new TestClient().test();
    }
    
//    @Test
    public void test() throws IOException {

        SSLContextConfigurator sslContextConfigurator = createSSLContextConfigurator();
        SSLEngineConfigurator clientSSLEngineConfigurator;

        if (sslContextConfigurator.validateConfiguration(true)) {
            clientSSLEngineConfigurator =
                    new SSLEngineConfigurator(sslContextConfigurator.createSSLContext(),
                    true, false, false);

            clientSSLEngineConfigurator.setEnabledCipherSuites(new String[] {"TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256"});
            
//            clientSSLEngineConfigurator.setEnabledCipherSuites(new String[] {"SSL_RSA_WITH_RC4_128_SHA"});
        } else {
            throw new IllegalStateException("Failed to validate SSLContextConfiguration.");
        }

        final ExecutorService threadPool = GrizzlyExecutorService.createInstance();
        
        final BlockingQueue<Future<HttpPacket>> httpResponseQueue =
                new LinkedTransferQueue<Future<HttpPacket>>();
        
        FilterChain filterChain = FilterChainBuilder.stateless()
                .add(new TransportFilter())
                .add(new SSLFilter(null, clientSSLEngineConfigurator))
                .add(new HttpClientFilter())
                .add(new Http2ClientFilter(DraftVersion.DRAFT_14))
                .add(new MyHttpFilter(httpResponseQueue))
                .build();
        
        TCPNIOTransport transport = TCPNIOTransportBuilder.newInstance()
                .setProcessor(filterChain)
                .build();
        try {
            transport.start();
            
            final Future<Connection> connectFuture = transport.connect(
//                    new InetSocketAddress("www.google.com", 443));
//                    new InetSocketAddress("localhost", 7070));
                    new InetSocketAddress("localhost", 8443));
            final Connection connection = connectFuture.get(10, TimeUnit.SECONDS);
            
            final Http2Connection http2Connection = Http2Connection.get(connection);
            
            connection.addCloseListener(new CloseListener() {

                @Override
                public void onClosed(Closeable closeable, ICloseType type) throws IOException {
                    System.out.println("Client connection closed " + type);
                }
            });
            
            // We can initiate HTTP2 Stream either
            
// ----------------------------------------------------------------------------            
            // 1)
            final HttpRequestPacket request = HttpRequestPacket.builder()
                    .method(Method.GET)
                    .protocol(Protocol.HTTP_1_1)
//                    .uri("/")
//                    .header(Header.Host, "www.google.com:443")
//                    .uri("/push")
//                    .header(Header.Host, "localhost:7070")
                    .uri("/blog-http2-push/push?rows=0&columns=0")
                    .header(Header.Host, "localhost:8443")
                   .build();
            connection.write(HttpContent.builder(request).content(Buffers.EMPTY_BUFFER).last(true).build());
            
            // Or
            
// ----------------------------------------------------------------------------            
            // 2) uncomment if want to test
            
//            final SpdyStream spdyStream = spdySession.getStreamBuilder()
//                    .method(Method.GET)
//                    .uri("/")
//                   .protocol(Protocol.HTTP_1_1)
//                    .header(Header.Host, "www.google.com:443")
//                    .fin(true)
//                    .open();
            
// ----------------------------------------------------------------------------            
            
            
//            HttpContent httpContent =
//                    HttpContent.builder(spdyStream.getSpdyRequest())
//                    .content(Buffers.EMPTY_BUFFER)
//                    .last(true)
//                    .build();
            
//            spdyConnection.write(httpContent);
            
            final HttpPacket response = httpResponseQueue.take().get();
            
            int len = 0;
            if (HttpContent.isContent(response)) {
                len = ((HttpContent) response).getContent().remaining();
            }
            
            System.out.println("HTTP response came. Payload size=" + len);
            if (HttpContent.isContent(response)) {
                System.out.println(((HttpContent) response).getContent().toStringContent());
            }
            
//            // Request #2
//            final HttpRequestPacket request2 = HttpRequestPacket.builder()
//                    .method(Method.GET)
//                    .protocol(Protocol.HTTP_1_1)
////                    .uri("/")
////                    .header(Header.Host, "www.google.com:443")
//                    .uri("/text")
//                    .header(Header.Host, "localhost:7070")
//                   .build();
////            connection.write(request2);
//            connection.write(HttpContent.builder(request2).content(Buffers.EMPTY_BUFFER).last(true).build());
//            
//            
//            final HttpPacket response2 = httpResponseQueue.take().get();
//            
//            len = 0;
//            if (HttpContent.isContent(response2)) {
//                len = ((HttpContent) response2).getContent().remaining();
//            }
//            
//            System.out.println("HTTP response #2 came. Payload size=" + len);
//            if (HttpContent.isContent(response2)) {
//                System.out.println(((HttpContent) response2).getContent().toStringContent());
//            }
            
//            System.out.println("Press any key to stop ...");
            //noinspection ResultOfMethodCallIgnored
//            System.in.read();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            transport.shutdownNow();
            threadPool.shutdown();
        }
    }
    
    // --------------------------------------------------------- Private Methods

    
    private static SSLContextConfigurator createSSLContextConfigurator() {
        SSLContextConfigurator sslContextConfigurator =
                new SSLContextConfigurator();
        ClassLoader cl = Http2BaseFilter.class.getClassLoader();
        // override system properties
//        URL cacertsUrl = cl.getResource("ssltest-cacerts.jks");
//        if (cacertsUrl != null) {
//            sslContextConfigurator.setTrustStoreFile(cacertsUrl.getFile());
//            sslContextConfigurator.setTrustStorePass("changeit");
//        }

        // override system properties
        URL keystoreUrl = cl.getResource("ssltest-keystore.jks");
        if (keystoreUrl != null) {
            sslContextConfigurator.setKeyStoreFile(keystoreUrl.getFile());
            sslContextConfigurator.setKeyStorePass("changeit");
        }

        return sslContextConfigurator;
    }

    private static class MyHttpFilter extends BaseFilter {
        final BlockingQueue<Future<HttpPacket>> httpResponseQueue;
        
        private MyHttpFilter(final BlockingQueue<Future<HttpPacket>> httpResponseQueue) {
            this.httpResponseQueue = httpResponseQueue;
        }

        @Override
        public NextAction handleRead(final FilterChainContext ctx) throws IOException {
            final HttpContent httpContent = ctx.getMessage();
            if (!httpContent.isLast()) {
                return ctx.getStopAction(httpContent);
            }

            final HttpHeader header = httpContent.getHttpHeader();
            if (header.isRequest()) {
                final HttpRequestPacket request = (HttpRequestPacket) header;
                final Http2Stream stream = (Http2Stream) request.getAttribute(Http2Stream.HTTP2_STREAM_ATTRIBUTE);
                
                assert stream != null;
                
                System.out.println("PushPromise request(" + stream.getId() + "): " + header);
            } else {
                final HttpResponsePacket response = (HttpResponsePacket) header;
                final HttpRequestPacket request = response.getRequest();
                
                final Http2Stream stream = (Http2Stream) request.getAttribute(Http2Stream.HTTP2_STREAM_ATTRIBUTE);
                
                if (stream == null) {
                    System.out.println("Not HTTP2 response: " + response);
                } else {
                    if ((stream.getId() % 2) == 0) {
                        System.out.println("PushPromise response(" + stream.getId() + "): " + response);
                    } else {
                        System.out.println("HTTP2 Response: " + response);
                        httpResponseQueue.add(Futures.<HttpPacket>createReadyFuture(httpContent));
                    }
                }
                
            }
            
            return ctx.getStopAction();
        }

        @Override
        public NextAction handleClose(FilterChainContext ctx) throws IOException {
            httpResponseQueue.add(Futures.<HttpPacket>createReadyFuture(new IOException("closed")));
            return ctx.getInvokeAction();
        }
        
        
    }
    
}
