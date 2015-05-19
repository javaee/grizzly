/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2015 Oracle and/or its affiliates. All rights reserved.
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

import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Map;
import java.util.logging.Logger;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.filterchain.Filter;
import org.glassfish.grizzly.filterchain.FilterChain;
import org.glassfish.grizzly.filterchain.FilterChainBuilder;
import org.glassfish.grizzly.filterchain.TransportFilter;
import org.glassfish.grizzly.http.HttpContent;
import org.glassfish.grizzly.http.HttpPacket;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.http.Protocol;
import org.glassfish.grizzly.http.server.HttpHandler;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.grizzly.http.server.NetworkListener;
import org.glassfish.grizzly.http.server.ServerConfiguration;
import org.glassfish.grizzly.memory.Buffers;
import org.glassfish.grizzly.memory.MemoryManager;
import org.glassfish.grizzly.ssl.SSLContextConfigurator;
import org.glassfish.grizzly.ssl.SSLEngineConfigurator;
import org.glassfish.grizzly.ssl.SSLFilter;

/**
 * General Spdy client/server init code.
 * 
 * @author Alexey Stashok
 */
public abstract class AbstractSpdyTest {
    private static final Logger LOGGER = Grizzly.logger(AbstractSpdyTest.class);
    
    private volatile static SSLEngineConfigurator clientSSLEngineConfigurator;
    private volatile static SSLEngineConfigurator serverSSLEngineConfigurator;    

    public static Collection<Object[]> getSpdyModes() {
        final Collection<Object[]> modes = new LinkedList<Object[]>();
        
//        modes.add(new Object[] {SpdyVersion.SPDY_3, SpdyMode.PLAIN, Boolean.FALSE});
//        modes.add(new Object[] {SpdyVersion.SPDY_3, SpdyMode.PLAIN, Boolean.TRUE});
        
        modes.add(new Object[] {SpdyVersion.SPDY_3_1, SpdyMode.PLAIN, Boolean.FALSE});
        modes.add(new Object[] {SpdyVersion.SPDY_3_1, SpdyMode.PLAIN, Boolean.TRUE});
        
        if (NextProtoNegSupport.isEnabled()) {
//            modes.add(new Object[] {SpdyVersion.SPDY_3, SpdyMode.NPN, Boolean.TRUE});
            modes.add(new Object[] {SpdyVersion.SPDY_3_1, SpdyMode.NPN, Boolean.TRUE});
        } else {
            LOGGER.info("NPN is not supported by this JDK, so NPN mode will be skipped");
        }
        
        return modes;
    }
    
    protected SpdyAddOn spdyAddon;
    
    protected HttpServer createServer(final String docRoot, final int port,
            final SpdyVersion spdyVersion,
            final SpdyMode spdyMode,
            final boolean isSecure,
            final HttpHandlerRegistration... registrations) {
        
        return createServer(docRoot, port, spdyVersion, spdyMode, isSecure,
                false, registrations);
    }
    
    protected HttpServer createServer(final String docRoot, final int port,
            final SpdyVersion spdyVersion,
            final SpdyMode spdyMode,
            final boolean isSecure,
            final boolean isFileCacheEnabled,
            final HttpHandlerRegistration... registrations) {
        HttpServer server = HttpServer.createSimpleServer(docRoot, port);
        NetworkListener listener = server.getListener("grizzly");
        listener.setSendFileEnabled(false);
        
        listener.getFileCache().setEnabled(isFileCacheEnabled);

        if (isSecure) {
            listener.setSecure(true);
            listener.setSSLEngineConfig(getServerSSLEngineConfigurator());
        }

        spdyAddon = new SpdyAddOn(spdyMode, spdyVersion);
        listener.registerAddOn(spdyAddon);
        
        ServerConfiguration sconfig = server.getServerConfiguration();
        
        for (HttpHandlerRegistration registration : registrations) {
            sconfig.addHttpHandler(registration.httpHandler, registration.mappings);
        }
        
        return server;
    }
    

    protected static FilterChain createClientFilterChain(
            final SpdyVersion spdyVersion,
            final SpdyMode spdyMode,
            final boolean isSecure,
            final Filter... clientFilters) {
        
        return createClientFilterChainAsBuilder(spdyVersion, spdyMode, isSecure,
                clientFilters).build();
    }
    

    protected static FilterChainBuilder createClientFilterChainAsBuilder(
            final SpdyVersion spdyVersion,
            final SpdyMode spdyMode,
            final boolean isSecure,
            final Filter... clientFilters) {
        
        final FilterChainBuilder builder = FilterChainBuilder.stateless()
             .add(new TransportFilter());
        if (isSecure) {
            builder.add(new SSLFilter(null, getClientSSLEngineConfigurator()));
        }
        
        builder.add(new SpdyFramingFilter())
                .add(new SpdyHandlerFilter(spdyMode, spdyVersion));
        
        if (clientFilters != null) {
            for (Filter clientFilter : clientFilters) {
                if (clientFilter != null) {
                    builder.add(clientFilter);
                }
            }
        }
        
        return builder;
    }
    
    protected static SSLEngineConfigurator getClientSSLEngineConfigurator() {
        checkSSLEngineConfigurators();
        return clientSSLEngineConfigurator;
    }
    
    protected static SSLEngineConfigurator getServerSSLEngineConfigurator() {
        checkSSLEngineConfigurators();
        return serverSSLEngineConfigurator;
    }

    private static void checkSSLEngineConfigurators() {
        if (clientSSLEngineConfigurator == null) {
            synchronized (AbstractSpdyTest.class) {
                if (clientSSLEngineConfigurator == null) {
                    SSLContextConfigurator sslContextConfigurator = createSSLContextConfigurator();

                    if (sslContextConfigurator.validateConfiguration(true)) {
                        serverSSLEngineConfigurator =
                                new SSLEngineConfigurator(sslContextConfigurator.createSSLContext(),
                                false, false, false);

                        serverSSLEngineConfigurator.setEnabledCipherSuites(new String[] {"SSL_RSA_WITH_RC4_128_SHA"});

                        clientSSLEngineConfigurator =
                                new SSLEngineConfigurator(sslContextConfigurator.createSSLContext(),
                                true, false, false);

                        clientSSLEngineConfigurator.setEnabledCipherSuites(new String[] {"SSL_RSA_WITH_RC4_128_SHA"});
                    } else {
                        throw new IllegalStateException("Failed to validate SSLContextConfiguration.");
                    }        
                }
            }
        }
    }
    
    protected static SSLContextConfigurator createSSLContextConfigurator() {
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
    
    @SuppressWarnings({"unchecked"})
    protected HttpPacket createRequest(final int port,
            final String method,
            final String content,
            String encoding) {
        return createRequest(port, method, content, encoding, null);
    }
    
    @SuppressWarnings({"unchecked"})
    protected HttpPacket createRequest(final int port,
            final String method,
            final String content,
            String encoding,
            final Map<String, String> headers) {

        HttpRequestPacket.Builder b = HttpRequestPacket.builder();
        b.method(method).protocol(Protocol.HTTP_1_1).uri("/path").header("Host", "localhost:" + port);

        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                b.header(entry.getKey(), entry.getValue());
            }
        }
        HttpRequestPacket request = b.build();

        if (content != null) {
            HttpContent.Builder cb = request.httpContentBuilder();
            MemoryManager mm = MemoryManager.DEFAULT_MEMORY_MANAGER;
            Buffer contentBuffer;
            if (encoding != null) {
                try {
                    byte[] bytes = content.getBytes(encoding);
                    contentBuffer = Buffers.wrap(mm, bytes);
                } catch (UnsupportedEncodingException e) {
                    throw new RuntimeException(e);
                }
            } else {
                contentBuffer = Buffers.wrap(mm, content);
            }

            request.setContentLength(contentBuffer.remaining());
            
            if (encoding != null) {
                request.setCharacterEncoding(encoding);
            }
            
            request.setContentType("text/plain");
            
            cb.content(contentBuffer);
            cb.last(true);
            return cb.build();

        }

        return request;
    }
    
    protected static class HttpHandlerRegistration {
        private final HttpHandler httpHandler;
        private final String[] mappings;

        private HttpHandlerRegistration(HttpHandler httpHandler, String[] mappings) {
            this.httpHandler = httpHandler;
            this.mappings = mappings;
        }
        
        public static HttpHandlerRegistration of(final HttpHandler httpHandler,
                final String... mappings) {
            return new HttpHandlerRegistration(httpHandler, mappings);
        }
    }
}
