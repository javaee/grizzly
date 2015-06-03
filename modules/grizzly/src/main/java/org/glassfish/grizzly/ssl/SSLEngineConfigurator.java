/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2015 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.ssl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.logging.Logger;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import org.glassfish.grizzly.Grizzly;

/**
 * Utility class, which helps to configure {@link SSLEngine}.
 * 
 * @author Alexey Stashok
 */
public class SSLEngineConfigurator implements SSLEngineFactory {
    private static final Logger LOGGER = Grizzly.logger(SSLEngineConfigurator.class);

    private final Object sync = new Object();
    
    protected volatile SSLContextConfigurator sslContextConfiguration;
    
    protected volatile SSLContext sslContext;

    /**
     * The list of cipher suite
     */
    protected String[] enabledCipherSuites = null;
    /**
     * the list of protocols
     */
    protected String[] enabledProtocols = null;
    /**
     * Client mode when handshaking.
     */
    protected boolean clientMode;
    /**
     * Require client Authentication.
     */
    protected boolean needClientAuth;
    /**
     * True when requesting authentication.
     */
    protected boolean wantClientAuth;
    /**
     * Has the enabled protocol configured.
     */
    private boolean isProtocolConfigured = false;
    /**
     * Has the enabled Cipher configured.
     */
    private boolean isCipherConfigured = false;
    
    /**
     * Create SSL Engine configuration basing on passed {@link SSLContext}.
     * 
     * @param sslContext {@link SSLContext}.
     */
    public SSLEngineConfigurator(SSLContext sslContext) {
        this(sslContext, true, false, false);
    }

    /**
     * Create SSL Engine configuration basing on passed {@link SSLContext},
     * using passed client mode, need/want client auth parameters.
     *
     * @param sslContext {@link SSLContext}.
     * @param clientMode
     * @param needClientAuth
     * @param wantClientAuth
     */
    public SSLEngineConfigurator(final SSLContext sslContext,
            final boolean clientMode, final boolean needClientAuth,
            final boolean wantClientAuth) {
        if (sslContext == null)
            throw new IllegalArgumentException("SSLContext can not be null");

        this.sslContextConfiguration = null;
        this.sslContext = sslContext;
        this.clientMode = clientMode;
        this.needClientAuth = needClientAuth;
        this.wantClientAuth = wantClientAuth;
    }

    /**
     * Create SSL Engine configuration basing on passed {@link SSLContextConfigurator}.
     * This constructor makes possible to initialize SSLEngine and SSLContext in lazy
     * fashion on first {@link #createSSLEngine()} call.
     *
     * @param sslContextConfiguration {@link SSLContextConfigurator}.
     */
    public SSLEngineConfigurator(SSLContextConfigurator sslContextConfiguration) {
        this(sslContextConfiguration, true, false, false);
    }

    /**
     * Create SSL Engine configuration basing on passed {@link SSLContextConfigurator}.
     * This constructor makes possible to initialize SSLEngine and SSLContext in lazy
     * fashion on first {@link #createSSLEngine()} call.
     *
     * @param sslContextConfiguration {@link SSLContextConfigurator}.
     * @param clientMode
     * @param needClientAuth
     * @param wantClientAuth
     */
    public SSLEngineConfigurator(SSLContextConfigurator sslContextConfiguration,
            boolean clientMode,
            boolean needClientAuth, boolean wantClientAuth) {
        
        if (sslContextConfiguration == null)
            throw new IllegalArgumentException("SSLContextConfigurator can not be null");

        this.sslContextConfiguration = sslContextConfiguration;
        this.clientMode = clientMode;
        this.needClientAuth = needClientAuth;
        this.wantClientAuth = wantClientAuth;
    }

    public SSLEngineConfigurator(SSLEngineConfigurator pattern) {
        this.sslContextConfiguration = pattern.sslContextConfiguration;
        this.sslContext = pattern.sslContext;
        this.clientMode = pattern.clientMode;
        this.needClientAuth = pattern.needClientAuth;
        this.wantClientAuth = pattern.wantClientAuth;

        this.enabledCipherSuites = pattern.enabledCipherSuites != null
                ? Arrays.copyOf(pattern.enabledCipherSuites, pattern.enabledCipherSuites.length)
                : null;
        
        this.enabledProtocols = pattern.enabledProtocols != null
                ? Arrays.copyOf(pattern.enabledProtocols, pattern.enabledProtocols.length)
                : null;

        this.isCipherConfigured = pattern.isCipherConfigured;
        this.isProtocolConfigured = pattern.isProtocolConfigured;
    }

    protected SSLEngineConfigurator() {
    }

    /**
     * Create and configure {@link SSLEngine} using this context configuration.
     * 
     * @return {@link SSLEngine}.
     */
    public SSLEngine createSSLEngine() {
        return createSSLEngine(null, -1);
    }

    /**
     * Create and configure {@link SSLEngine} using this context configuration
     * using advisory peer information.
     * <P>
     * Applications using this factory method are providing hints
     * for an internal session reuse strategy.
     * <P>
     * Some cipher suites (such as Kerberos) require remote hostname
     * information, in which case peerHost needs to be specified.
     * 
     * @param   peerHost the non-authoritative name of the host
     * @param   peerPort the non-authoritative port
     * 
     * @return {@link SSLEngine}.
     */
    @Override
    public SSLEngine createSSLEngine(final String peerHost, final int peerPort) {
        if (sslContext == null) {
            synchronized(sync) {
                if (sslContext == null) {
                    sslContext = sslContextConfiguration.createSSLContext();
                }
            }
        }
        
        final SSLEngine sslEngine = sslContext.createSSLEngine(peerHost, peerPort);
        configure(sslEngine);

        return sslEngine;
    }

    /**
     * Configure passed {@link SSLEngine}, using current configurator settings
     * 
     * @param sslEngine {@link SSLEngine} to configure.
     * @return configured {@link SSLEngine}.
     */
    public SSLEngine configure(final SSLEngine sslEngine) {
        if (enabledCipherSuites != null) {
            if (!isCipherConfigured) {
                enabledCipherSuites = configureEnabledCiphers(sslEngine,
                        enabledCipherSuites);
                isCipherConfigured = true;
            }
            sslEngine.setEnabledCipherSuites(enabledCipherSuites);
        }

        if (enabledProtocols != null) {
            if (!isProtocolConfigured) {
                enabledProtocols = 
                        configureEnabledProtocols(sslEngine,
                        enabledProtocols);
                isProtocolConfigured = true;
            }
            sslEngine.setEnabledProtocols(enabledProtocols);
        }

        sslEngine.setUseClientMode(clientMode);
        if (wantClientAuth) {
            sslEngine.setWantClientAuth(wantClientAuth);
        }
        if (needClientAuth) {
            sslEngine.setNeedClientAuth(needClientAuth);
        }

        return sslEngine;
    }
    
    /**
     * Will {@link SSLEngine} be configured to work in client mode.
     * 
     * @return <tt>true</tt>, if {@link SSLEngine} will be configured to work
     * in <tt>client</tt> mode, or <tt>false</tt> for <tt>server</tt> mode.
     */
    public boolean isClientMode() {
        return clientMode;
    }

    /**
     * Set {@link SSLEngine} to be configured to work in client mode.
     *
     * @param clientMode <tt>true</tt>, if {@link SSLEngine} will be configured
     * to work in <tt>client</tt> mode, or <tt>false</tt> for <tt>server</tt>
     * mode.
     * @return this SSLEngineConfigurator
     */
    public SSLEngineConfigurator setClientMode(boolean clientMode) {
        this.clientMode = clientMode;
        return this;
    }


    public boolean isNeedClientAuth() {
        return needClientAuth;
    }

    public SSLEngineConfigurator setNeedClientAuth(boolean needClientAuth) {
        this.needClientAuth = needClientAuth;
        return this;
    }

    public boolean isWantClientAuth() {
        return wantClientAuth;
    }

    public SSLEngineConfigurator setWantClientAuth(boolean wantClientAuth) {
        this.wantClientAuth = wantClientAuth;
        return this;
    }

    /**
     * @return an array of enabled cipher suites. Modifications made on the array
     *      content won't be propagated to SSLEngineConfigurator
     */
    public String[] getEnabledCipherSuites() {
        return enabledCipherSuites != null
                ? Arrays.copyOf(enabledCipherSuites, enabledCipherSuites.length)
                : null;
    }

    /**
     * Sets a list of enabled cipher suites.
     * Note: further modifications made on the passed array won't be propagated
     *       to SSLEngineConfigurator.
     * 
     * @param enabledCipherSuites list of enabled cipher suites
     * @return this SSLEngineConfigurator
     */
    public SSLEngineConfigurator setEnabledCipherSuites(
            final String[] enabledCipherSuites) {
        this.enabledCipherSuites = enabledCipherSuites != null
                ? Arrays.copyOf(enabledCipherSuites, enabledCipherSuites.length)
                : null;
        return this;
    }

    /**
     * @return an array of enabled protocols. Modifications made on the array
     *      content won't be propagated to SSLEngineConfigurator
     */
    public String[] getEnabledProtocols() {
        return enabledProtocols != null
                ? Arrays.copyOf(enabledProtocols, enabledProtocols.length)
                : null;
    }

    /**
     * Sets a list of enabled protocols.
     * Note: further modifications made on the passed array won't be propagated
     *       to SSLEngineConfigurator.
     * 
     * @param enabledProtocols list of enabled protocols
     * @return this SSLEngineConfigurator
     */
    public SSLEngineConfigurator setEnabledProtocols(
            final String[] enabledProtocols) {
        this.enabledProtocols = enabledProtocols != null
                ? Arrays.copyOf(enabledProtocols, enabledProtocols.length)
                : null;
        return this;
    }

    public boolean isCipherConfigured() {
        return isCipherConfigured;
    }

    public SSLEngineConfigurator setCipherConfigured(boolean isCipherConfigured) {
        this.isCipherConfigured = isCipherConfigured;
        return this;
    }

    public boolean isProtocolConfigured() {
        return isProtocolConfigured;
    }

    public SSLEngineConfigurator setProtocolConfigured(boolean isProtocolConfigured) {
        this.isProtocolConfigured = isProtocolConfigured;
        return this;
    }

    public SSLContext getSslContext() {
        if (sslContext == null) {
            synchronized(sync) {
                if (sslContext == null) {
                    sslContext = sslContextConfiguration.createSSLContext();
                }
            }
        }

        return sslContext;
    }

    /**
     * Return the list of allowed protocol.
     * @return String[] an array of supported protocols.
     */
    private static String[] configureEnabledProtocols(
            SSLEngine sslEngine, String[] requestedProtocols) {

        String[] supportedProtocols = sslEngine.getSupportedProtocols();
        String[] protocols = null;
        ArrayList<String> list = null;
        for (String supportedProtocol : supportedProtocols) {
            /*
             * Check to see if the requested protocol is among the
             * supported protocols, i.e., may be enabled
             */
            for (String protocol : requestedProtocols) {
                protocol = protocol.trim();
                if (supportedProtocol.equals(protocol)) {
                    if (list == null) {
                        list = new ArrayList<String>();
                    }
                    list.add(protocol);
                    break;
                }
            }
        }

        if (list != null) {
            protocols = list.toArray(new String[list.size()]);
        }

        return protocols;
    }

    /**
     * Determines the SSL cipher suites to be enabled.
     *
     * @return Array of SSL cipher suites to be enabled, or null if none of the
     * requested ciphers are supported
     */
    private static String[] configureEnabledCiphers(SSLEngine sslEngine,
            String[] requestedCiphers) {

        String[] supportedCiphers = sslEngine.getSupportedCipherSuites();
        String[] ciphers = null;
        ArrayList<String> list = null;
        for (String supportedCipher : supportedCiphers) {
            /*
             * Check to see if the requested protocol is among the
             * supported protocols, i.e., may be enabled
             */
            for (String cipher : requestedCiphers) {
                cipher = cipher.trim();
                if (supportedCipher.equals(cipher)) {
                    if (list == null) {
                        list = new ArrayList<String>();
                    }
                    list.add(cipher);
                    break;
                }
            }
        }

        if (list != null) {
            ciphers = list.toArray(new String[list.size()]);
        }

        return ciphers;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("SSLEngineConfigurator");
        sb.append("{clientMode=").append(clientMode);
        sb.append(", enabledCipherSuites=")
            .append(enabledCipherSuites == null ? "null" : Arrays.asList(enabledCipherSuites).toString());
        sb.append(", enabledProtocols=")
            .append(enabledProtocols == null ? "null" : Arrays.asList(enabledProtocols).toString());
        sb.append(", needClientAuth=").append(needClientAuth);
        sb.append(", wantClientAuth=").append(wantClientAuth);
        sb.append(", isProtocolConfigured=").append(isProtocolConfigured);
        sb.append(", isCipherConfigured=").append(isCipherConfigured);
        sb.append('}');
        return sb.toString();
    }

    public SSLEngineConfigurator copy() {
        return new SSLEngineConfigurator(this);
    }
}
