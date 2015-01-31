/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2014-2015 Oracle and/or its affiliates. All rights reserved.
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

import javax.net.ssl.SSLEngine;
import org.glassfish.grizzly.ssl.SSLEngineConfigurator;

/**
 * The object represents SNI configuration for either server or client side.
 * In order to create a server-side SNI configuration - the {@link #serverConfigBuilder()}
 * has to be used, for client-side SNI configuration please use {@link #clientConfigBuilder()}.
 * 
 * @author Alexey Stashok
 */
public class SNIConfig {
    private static final SSLEngineConfigurator NULL_SERVER_CONFIG =
            new NullSSLEngineConfigurator();
    
    final SSLEngineConfigurator sslEngineConfigurator;
    final String host;
    final boolean isClientConfig;

    /**
     * @param sslEngineConfigurator {@link SSLEngineConfigurator},
     *          or <tt>null</tt> for the default configuration
     * @return server-side SNI configuration
     */
    public static SNIConfig newServerConfig(
        final SSLEngineConfigurator sslEngineConfigurator) {
        return new SNIConfig(sslEngineConfigurator, null, false);
    }
    
    /**
     * @param host the SNI host name to be sent to a server, or <tt>null</tt>
     *          to not use SNI extension
     * @return client-side SNI configuration
     */
    public static SNIConfig newClientConfig(final String host) {
        return new SNIConfig(null, host, true);
    }
    
    /**
     * @param host the SNI host name to be sent to a server, or <tt>null</tt>
     *          to not use SNI extension
     * @param sslEngineConfigurator {@link SSLEngineConfigurator},
     *          or <tt>null</tt> for the default configuration
     * @return client-side SNI configuration
     */
    public static SNIConfig newClientConfig(final String host,
        final SSLEngineConfigurator sslEngineConfigurator) {
        return new SNIConfig(sslEngineConfigurator, host, true);
    }

    /**
     * @param host
     * @return SNIConfig for {@link Connection}, whose SNI host wasn't recognized
     *          as supported, so the {@link Connection} has to be closed
     */
    public static SNIConfig failServerConfig(final String host) {
        return new SNIConfig(NULL_SERVER_CONFIG, host, false);
    }
    
    private SNIConfig(final SSLEngineConfigurator engineConfig,
            final String host, final boolean isClientConfig) {
        this.sslEngineConfigurator = engineConfig;
        this.host = host;
        this.isClientConfig = isClientConfig;
    }
    
    private static class NullSSLEngineConfigurator extends SSLEngineConfigurator {

        public NullSSLEngineConfigurator() {
        }
        
        @Override
        public SSLEngine createSSLEngine(String peerHost, int peerPort) {
            throw new IllegalStateException("No SNI config found");
        }

        @Override
        public SSLEngine createSSLEngine() {
            throw new IllegalStateException("No SNI config found");
        }

        @Override
        public SSLEngine configure(SSLEngine sslEngine) {
            throw new IllegalStateException("No SNI config found");
        }

        @Override
        public SSLEngineConfigurator copy() {
            return new NullSSLEngineConfigurator();
        }

        @Override
        public SSLEngineConfigurator setProtocolConfigured(
                boolean isProtocolConfigured) {
            throw new IllegalStateException("Immutable config");
        }

        @Override
        public SSLEngineConfigurator setCipherConfigured(boolean isCipherConfigured) {
            throw new IllegalStateException("Immutable config");
        }

        @Override
        public SSLEngineConfigurator setEnabledProtocols(String[] enabledProtocols) {
            throw new IllegalStateException("Immutable config");
        }

        @Override
        public SSLEngineConfigurator setEnabledCipherSuites(String[] enabledCipherSuites) {
            throw new IllegalStateException("Immutable config");
        }

        @Override
        public SSLEngineConfigurator setWantClientAuth(boolean wantClientAuth) {
            throw new IllegalStateException("Immutable config");
        }

        @Override
        public SSLEngineConfigurator setNeedClientAuth(boolean needClientAuth) {
            throw new IllegalStateException("Immutable config");
        }

        @Override
        public SSLEngineConfigurator setClientMode(boolean clientMode) {
            throw new IllegalStateException("Immutable config");
        }
    }
}
