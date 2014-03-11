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

import org.glassfish.grizzly.ssl.SSLEngineConfigurator;

/**
 * The object represents SNI configuration for either server or client side.
 * In order to create a server-side SNI configuration - the {@link #serverConfigBuilder()}
 * has to be used, for client-side SNI configuration please use {@link #clientConfigBuilder()}.
 * 
 * @author Alexey Stashok
 */
public class SNIConfig {
    final SSLEngineConfigurator sslEngineConfigurator;
    final String host;
    final boolean isClientConfig;

    /**
     * @return server-side SNI configuration builder
     */
    public static SNIServerConfigBuilder serverConfigBuilder() {
        return new SNIServerConfigBuilder();
    }

    /**
     * @return client-side SNI configuration builder
     */
    public static SNIClientConfigBuilder clientConfigBuilder() {
        return new SNIClientConfigBuilder();
    }

    private SNIConfig(final SSLEngineConfigurator engineConfig,
            final String host, final boolean isClientConfig) {
        this.sslEngineConfigurator = engineConfig;
        this.host = host;
        this.isClientConfig = isClientConfig;
    }


    public static class SNIServerConfigBuilder {
        private SSLEngineConfigurator sslEngineConfigurator;

        public SNIServerConfigBuilder sslEngineConfigurator(
                final SSLEngineConfigurator config) {
            this.sslEngineConfigurator = config;
            return this;
        }
        
        public SNIConfig build() {
            if (sslEngineConfigurator == null) {
                throw new IllegalStateException("SNIConfig has to have non-null SSLEngineConfigurator");
            }
            
            return new SNIConfig(sslEngineConfigurator, null, false);
        }
    }

    public static class SNIClientConfigBuilder {
        private SSLEngineConfigurator sslEngineConfigurator;
        private String host;
        
        public SNIClientConfigBuilder sslEngineConfigurator(
                final SSLEngineConfigurator config) {
            this.sslEngineConfigurator = config;
            return this;
        }
        
        public SNIClientConfigBuilder host(final String host) {
            this.host = host;
            return this;
        }
        
        public SNIConfig build() {
            if (sslEngineConfigurator == null) {
                throw new IllegalStateException("SNIConfig has to have non-null SSLEngineConfigurator");
            }
            
            if (host == null) {
                throw new IllegalStateException("SNIConfig has to have non-null host");
            }

            return new SNIConfig(sslEngineConfigurator, host, true);
        }
    }    
}
