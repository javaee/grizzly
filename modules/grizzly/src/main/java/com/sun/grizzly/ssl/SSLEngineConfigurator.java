/*
 *
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2007-2008 Sun Microsystems, Inc. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License. You can obtain
 * a copy of the License at https://glassfish.dev.java.net/public/CDDL+GPL.html
 * or glassfish/bootstrap/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/bootstrap/legal/LICENSE.txt.
 * Sun designates this particular file as subject to the "Classpath" exception
 * as provided by Sun in the GPL Version 2 section of the License file that
 * accompanied this code.  If applicable, add the following below the License
 * Header, with the fields enclosed by brackets [] replaced by your own
 * identifying information: "Portions Copyrighted [year]
 * [name of copyright owner]"
 *
 * Contributor(s):
 *
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
 *
 */

package com.sun.grizzly.ssl;

import java.util.ArrayList;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

/**
 * Utility class, which helps to configure {@link SSLEngine}.
 * 
 * @author Alexey Stashok
 */
public class SSLEngineConfigurator {
    private SSLContext sslContext;

    /**
     * The list of cipher suite
     */
    private String[] enabledCipherSuites = null;
    /**
     * the list of protocols
     */
    private String[] enabledProtocols = null;
    /**
     * Client mode when handshaking.
     */
    private boolean clientMode;
    /**
     * Require client Authentication.
     */
    private boolean needClientAuth;
    /**
     * True when requesting authentication.
     */
    private boolean wantClientAuth;
    /**
     * Has the enabled protocol configured.
     */
    private boolean isProtocolConfigured = false;
    /**
     * Has the enabled Cipher configured.
     */
    private boolean isCipherConfigured = false;

    public SSLEngineConfigurator(SSLContext sslContext) {
        this(sslContext, true, false, false);
    }

    public SSLEngineConfigurator(SSLContext sslContext, boolean clientMode,
            boolean needClientAuth, boolean wantClientAuth) {
        this.sslContext = sslContext;
        this.clientMode = clientMode;
        this.needClientAuth = needClientAuth;
        this.wantClientAuth = wantClientAuth;
    }

    /**
     * Create and configure {@link SSLEngine}, basing on current settings.
     * 
     * @return {@link SSLEngine}.
     */
    public SSLEngine createSSLEngine() {
        SSLEngine sslEngine = sslContext.createSSLEngine();
        configure(sslEngine);

        return sslEngine;
    }

    /**
     * Configure passed {@link SSLEngine}, using current configurator settings
     * 
     * @param sslEngine {@link SSLEngine} to configure.
     */
    public void configure(SSLEngine sslEngine) {
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
                enabledProtocols = configureEnabledProtocols(sslEngine,
                        enabledProtocols);
                isProtocolConfigured = true;
            }
            sslEngine.setEnabledProtocols(enabledProtocols);
        }

        sslEngine.setUseClientMode(clientMode);
        sslEngine.setWantClientAuth(wantClientAuth);
        sslEngine.setNeedClientAuth(needClientAuth);
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
     */
    public void setClientMode(boolean clientMode) {
        this.clientMode = clientMode;
    }


    public boolean isNeedClientAuth() {
        return needClientAuth;
    }

    public void setNeedClientAuth(boolean needClientAuth) {
        this.needClientAuth = needClientAuth;
    }

    public boolean isWantClientAuth() {
        return wantClientAuth;
    }

    public void setWantClientAuth(boolean wantClientAuth) {
        this.wantClientAuth = wantClientAuth;
    }

    public String[] getEnabledCipherSuites() {
        return enabledCipherSuites;
    }

    public void setEnabledCipherSuites(String[] enabledCipherSuites) {
        this.enabledCipherSuites = enabledCipherSuites;
    }

    public String[] getEnabledProtocols() {
        return enabledProtocols;
    }

    public void setEnabledProtocols(String[] enabledProtocols) {
        this.enabledProtocols = enabledProtocols;
    }

    public boolean isCipherConfigured() {
        return isCipherConfigured;
    }

    public void setCipherConfigured(boolean isCipherConfigured) {
        this.isCipherConfigured = isCipherConfigured;
    }

    public boolean isProtocolConfigured() {
        return isProtocolConfigured;
    }

    public void setProtocolConfigured(boolean isProtocolConfigured) {
        this.isProtocolConfigured = isProtocolConfigured;
    }

    public SSLContext getSslContext() {
        return sslContext;
    }

    public void setSslContext(final SSLContext sslContext) {
        this.sslContext = sslContext;
    }

    /**
     * Return the list of allowed protocol.
     * @return String[] an array of supported protocols.
     */
    private final static String[] configureEnabledProtocols(
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
    private final static String[] configureEnabledCiphers(SSLEngine sslEngine,
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
        StringBuilder sb = new StringBuilder("SSLEngineConfigurator[clientMode=");
        sb.append(clientMode);
        sb.append(", needClientAuth=").append(needClientAuth);
        sb.append(", wantClientAuth=").append(wantClientAuth);
        sb.append(", enabledProtocols=").append(enabledProtocols);
        sb.append(", enabledCipherSuites=").append(enabledCipherSuites);
        sb.append(']');
        return sb.toString();
    }


}
