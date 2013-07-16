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
package org.glassfish.grizzly.spdy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.FilterChain;
import org.glassfish.grizzly.filterchain.FilterChainBuilder;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.filterchain.TransportFilter;
import org.glassfish.grizzly.http.HttpContent;
import org.glassfish.grizzly.http.HttpPacket;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.http.Method;
import org.glassfish.grizzly.http.Protocol;
import org.glassfish.grizzly.http.util.Header;
import org.glassfish.grizzly.impl.FutureImpl;

import org.glassfish.grizzly.memory.BuffersBuffer;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import org.glassfish.grizzly.nio.transport.TCPNIOTransportBuilder;
import org.glassfish.grizzly.ssl.SSLContextConfigurator;
import org.glassfish.grizzly.ssl.SSLEngineConfigurator;
import org.glassfish.grizzly.ssl.SSLFilter;
import org.glassfish.grizzly.threadpool.GrizzlyExecutorService;
import org.glassfish.grizzly.utils.Futures;

/**
 *
 * @author oleksiys
 */
@SuppressWarnings("unchecked")
public class TestClient {
    public static void main(String[] args) throws IOException {

        SSLContextConfigurator sslContextConfigurator = createSSLContextConfigurator();
        SSLEngineConfigurator clientSSLEngineConfigurator;

        if (sslContextConfigurator.validateConfiguration(true)) {
            clientSSLEngineConfigurator =
                    new SSLEngineConfigurator(sslContextConfigurator.createSSLContext(),
                    true, false, false);
            
//            clientSSLEngineConfigurator.setEnabledCipherSuites(new String[] {"SSL_RSA_WITH_RC4_128_SHA"});
        } else {
            throw new IllegalStateException("Failed to validate SSLContextConfiguration.");
        }

        final ExecutorService threadPool = GrizzlyExecutorService.createInstance();
        
        final FutureImpl<HttpPacket> httpResponseFuture =
                Futures.createSafeFuture();
        
        FilterChain filterChain = FilterChainBuilder.stateless()
                .add(new TransportFilter())
                .add(new SSLFilter(null, clientSSLEngineConfigurator))
                .add(new SpdyFramingFilter())
                .add(new SpdyHandlerFilter(SpdyMode.NPN, threadPool))
                .add(new MyHttpFilter(httpResponseFuture))
                .build();
        
        TCPNIOTransport transport = TCPNIOTransportBuilder.newInstance()
                .setProcessor(filterChain)
                .build();
        try {
            transport.start();
            
            final Future<Connection> connectFuture = transport.connect(
                    new InetSocketAddress("www.google.com", 443));
//                    new InetSocketAddress("localhost", 7070));
            final Connection spdyConnection = connectFuture.get(10, TimeUnit.SECONDS);
            
            final SpdySession spdySession = SpdySession.get(spdyConnection);
            

            // We can initiate SPDY SYN_STREAM either
            
// ----------------------------------------------------------------------------            
            // 1)
            final HttpRequestPacket request = HttpRequestPacket.builder()
                    .method(Method.GET)
                    .uri("/")
                    .protocol(Protocol.HTTP_1_1)
                    .header(Header.Host, "www.google.com:443")
                   .build();
            request.setExpectContent(false);
            spdyConnection.write(request);
            
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
            
            final HttpPacket response = httpResponseFuture.get();
            
            int len = 0;
            if (HttpContent.isContent(response)) {
                len = ((HttpContent) response).getContent().remaining();
            }
            
            System.out.println("HTTP response came. Payload size=" + len);
            if (HttpContent.isContent(response)) {
                System.out.println(((HttpContent) response).getContent().toStringContent());
            }
            
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
        ClassLoader cl = SpdyHandlerFilter.class.getClassLoader();
        // override system properties
        URL cacertsUrl = cl.getResource("ssltest-cacerts.jks");
        if (cacertsUrl != null) {
            sslContextConfigurator.setTrustStoreFile(cacertsUrl.getFile());
            sslContextConfigurator.setTrustStorePass("changeit");
        }

        // override system properties
        URL keystoreUrl = cl.getResource("ssltest-keystore.jks");
        if (keystoreUrl != null) {
            sslContextConfigurator.setKeyStoreFile(keystoreUrl.getFile());
            sslContextConfigurator.setKeyStorePass("changeit");
        }

        return sslContextConfigurator;
    }

    private static class MyHttpFilter extends BaseFilter {
        final FutureImpl<HttpPacket> httpResponseFuture;
        
        private MyHttpFilter(final FutureImpl<HttpPacket> httpResponseFuture) {
            this.httpResponseFuture = httpResponseFuture;
        }

        @Override
        public NextAction handleRead(final FilterChainContext ctx) throws IOException {
            final HttpContent httpContent = ctx.getMessage();
            if (!httpContent.isLast()) {
                return ctx.getStopAction(httpContent);
            }
            
            httpResponseFuture.result(httpContent);
            return ctx.getStopAction();
        }
    }
    
}
