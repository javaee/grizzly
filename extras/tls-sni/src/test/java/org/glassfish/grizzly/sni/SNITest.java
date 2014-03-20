/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2014 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.sni;

import java.io.IOException;
import java.net.URL;
import java.util.concurrent.TimeUnit;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.attributes.Attribute;
import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.FilterChain;
import org.glassfish.grizzly.filterchain.FilterChainBuilder;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.filterchain.TransportFilter;
import org.glassfish.grizzly.impl.FutureImpl;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import org.glassfish.grizzly.nio.transport.TCPNIOTransportBuilder;
import org.glassfish.grizzly.ssl.SSLContextConfigurator;
import org.glassfish.grizzly.ssl.SSLEngineConfigurator;
import org.glassfish.grizzly.utils.Futures;
import org.glassfish.grizzly.utils.JdkVersion;
import org.glassfish.grizzly.utils.StringFilter;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Basic SNI test
 * 
 * @author Alexey Stashok
 */
@SuppressWarnings("unchecked")
public class SNITest {
    public static final int PORT = 19283;
    private static final boolean JDK7_OR_HIGHER = JdkVersion.getJdkVersion()
            .compareTo(JdkVersion.parseVersion("1.7")) >= 0;
    
    @Test
    public void testClientServerSNI() throws Exception {
        final String sniHostValue = "sni-test.com";
        final String msg = "Hello world!";
        
        final Attribute<String> sniHostAttr =
                Grizzly.DEFAULT_ATTRIBUTE_BUILDER.createAttribute("sni-host-attr");
        
        if (!JDK7_OR_HIGHER) {
            return;
        }
        
        final SSLEngineConfigurator sslServerEngineConfig = 
                    new SSLEngineConfigurator(
                            createSSLContextConfigurator().createSSLContext(),
                    false, false, false);
        final SSLEngineConfigurator sslClientEngineConfig = 
                    new SSLEngineConfigurator(
                            createSSLContextConfigurator().createSSLContext(),
                    true, false, false);
        
        final SNIFilter sniFilter = new SNIFilter();
        sniFilter.setServerSSLConfigResolver(new SNIServerConfigResolver() {

            @Override
            public SNIConfig resolve(Connection connection, String hostname) {
                sniHostAttr.set(connection, hostname);
                
                return SNIConfig.newServerConfig(sslServerEngineConfig);
            }
        });
        
        sniFilter.setClientSSLConfigResolver(new SNIClientConfigResolver() {

            @Override
            public SNIConfig resolve(Connection connection) {
                return SNIConfig.newClientConfig(sniHostValue,
                        sslClientEngineConfig);
            }
        });

        final FutureImpl<String[]> resultFuture = Futures.<String[]>createSafeFuture();
        final FilterChain chain = FilterChainBuilder.stateless()
                .add(new TransportFilter())
                .add(sniFilter)
                .add(new StringFilter())
                .add(new BaseFilter() {

                    @Override
                    public NextAction handleRead(final FilterChainContext ctx)
                            throws IOException {
                        final String msg = ctx.getMessage();
                        final String sniHost = sniHostAttr.get(ctx.getConnection());
                        
                        resultFuture.result(new String[] {msg, sniHost});
                        return ctx.getInvokeAction();
                    }

                })
                .build();
        
        TCPNIOTransport transport = TCPNIOTransportBuilder.newInstance()
                .setProcessor(chain)
                .build();
        
        try {
            transport.bind(PORT);
            transport.start();
            
            final Connection c = transport.connect("localhost", PORT).get();
            c.write(msg);
            
            final String[] result = resultFuture.get(10, TimeUnit.SECONDS);
            assertEquals(msg, result[0]);
            assertEquals(sniHostValue, result[1]);
            
        } finally {
            transport.shutdownNow();
        }
    }
    
    private static SSLContextConfigurator createSSLContextConfigurator() {
        SSLContextConfigurator sslContextConfigurator =
                new SSLContextConfigurator();
        ClassLoader cl = SNITest.class.getClassLoader();
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
}
