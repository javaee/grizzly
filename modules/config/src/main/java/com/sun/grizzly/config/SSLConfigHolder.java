/*
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License).  You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the license at
 * https://glassfish.dev.java.net/public/CDDLv1.0.html or
 * glassfish/bootstrap/legal/CDDLv1.0.txt.
 * See the License for the specific language governing
 * permissions and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL
 * Header Notice in each file and include the License file
 * at glassfish/bootstrap/legal/CDDLv1.0.txt.
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * you own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 *
 * Copyright 2006 Sun Microsystems, Inc. All rights reserved.
 */

package com.sun.grizzly.config;

import com.sun.grizzly.SSLConfig;
import com.sun.grizzly.config.dom.NetworkListener;
import com.sun.grizzly.config.dom.Protocol;
import com.sun.grizzly.config.dom.Ssl;
import com.sun.grizzly.util.ClassLoaderUtil;
import com.sun.grizzly.util.net.SSLImplementation;
import com.sun.grizzly.util.net.ServerSocketFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author oleksiys
 */
public class SSLConfigHolder {
    public static final String APP_BUFFER_ATTR_NAME = "TMP_DECODED_BUFFER";

    private final static Logger logger = GrizzlyEmbeddedHttps.logger();
    
    /**
     * The <code>SSLImplementation</code>
     */
    protected SSLImplementation sslImplementation;
    /**
     * The <code>SSLContext</code> associated with the SSL implementation we are running on.
     */
    protected SSLContext sslContext;
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
    protected boolean clientMode = false;
    /**
     * Require client Authentication.
     */
    protected boolean needClientAuth = false;
    /**
     * True when requesting authentication.
     */
    protected boolean wantClientAuth = false;

    /**
     * Set the SSLContext required to support SSL over NIO.
     */
    public void setSSLConfig(final SSLConfig sslConfig) {
        sslContext = sslConfig.createSSLContext();
    }

    /**
     * Set the SSLContext required to support SSL over NIO.
     */
    public void setSSLContext(final SSLContext sslContext) {
        this.sslContext = sslContext;
    }

    /**
     * Return the SSLContext required to support SSL over NIO.
     */
    public SSLContext getSSLContext() {
        return sslContext;
    }

    /**
     * Set the Coyote SSLImplementation.
     */
    public void setSSLImplementation(final SSLImplementation sslImplementation) {
        this.sslImplementation = sslImplementation;
    }

    /**
     * Return the current <code>SSLImplementation</code> this Thread
     */
    public SSLImplementation getSSLImplementation() {
        return sslImplementation;
    }

    /**
     * Returns the list of cipher suites to be enabled when {@link SSLEngine} is initialized.
     *
     * @return <tt>null</tt> means 'use {@link SSLEngine}'s default.'
     */
    public String[] getEnabledCipherSuites() {
        return enabledCipherSuites;
    }

    /**
     * Sets the list of cipher suites to be enabled when {@link SSLEngine} is initialized.
     *
     * @param enabledCipherSuites <tt>null</tt> means 'use {@link SSLEngine}'s default.'
     */
    public void setEnabledCipherSuites(final String[] enabledCipherSuites) {
        this.enabledCipherSuites = enabledCipherSuites;
    }

    /**
     * Returns the list of protocols to be enabled when {@link SSLEngine} is initialized.
     *
     * @return <tt>null</tt> means 'use {@link SSLEngine}'s default.'
     */
    public String[] getEnabledProtocols() {
        return enabledProtocols;
    }

    /**
     * Sets the list of protocols to be enabled when {@link SSLEngine} is initialized.
     *
     * @param enabledProtocols <tt>null</tt> means 'use {@link SSLEngine}'s default.'
     */
    public void setEnabledProtocols(final String[] enabledProtocols) {
        this.enabledProtocols = enabledProtocols;
    }

    /**
     * Returns <tt>true</tt> if the SSlEngine is set to use client mode when handshaking.
     *
     * @return is client mode enabled
     */
    public boolean isClientMode() {
        return clientMode;
    }

    /**
     * Configures the engine to use client (or server) mode when handshaking.
     */
    public void setClientMode(final boolean clientMode) {
        this.clientMode = clientMode;
    }

    /**
     * Returns <tt>true</tt> if the SSLEngine will <em>require</em> client authentication.
     */
    public boolean isNeedClientAuth() {
        return needClientAuth;
    }

    /**
     * Configures the engine to <em>require</em> client authentication.
     */
    public void setNeedClientAuth(final boolean needClientAuth) {
        this.needClientAuth = needClientAuth;
    }

    /**
     * Returns <tt>true</tt> if the engine will <em>request</em> client authentication.
     */
    public boolean isWantClientAuth() {
        return wantClientAuth;
    }

    /**
     * Configures the engine to <em>request</em> client authentication.
     */
    public void setWantClientAuth(final boolean wantClientAuth) {
        this.wantClientAuth = wantClientAuth;
    }

    public SSLEngine createSSLEngine() {
        final SSLEngine sslEngine = sslContext.createSSLEngine();
        
        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, "newSSLEngine: " + sslEngine);
        }

        if (enabledCipherSuites != null){
            sslEngine.setEnabledCipherSuites(enabledCipherSuites);
        }

        if (enabledProtocols != null){
            sslEngine.setEnabledProtocols(enabledProtocols);
        }
        
        sslEngine.setUseClientMode(clientMode);
        sslEngine.setWantClientAuth(wantClientAuth);
        sslEngine.setNeedClientAuth(needClientAuth);
        
        return sslEngine;
    }
    
    /**
     * Configures the SSL properties on the given PECoyoteConnector from the SSL config of the given HTTP listener.
     *
     * @param ssl
     */
    public static SSLConfigHolder configureSSL(final Ssl ssl) {
        final SSLConfigHolder config = new SSLConfigHolder();
        if (configureSSL(ssl, config)) {
            return config;
        }

        return null;
    }
    
    /**
     * Configures the SSL properties on the given PECoyoteConnector from the SSL config of the given HTTP listener.
     *
     * @param ssl
     */
    public static boolean configureSSL(final Ssl ssl, final SSLConfigHolder sslConfigHolder) {
        final List<String> tmpSSLArtifactsList = new LinkedList<String>();
        if (ssl != null) {
            // client-auth
            if (Boolean.parseBoolean(ssl.getClientAuthEnabled())) {
                sslConfigHolder.setNeedClientAuth(true);
            }
            // ssl protocol variants
            if (Boolean.parseBoolean(ssl.getSsl2Enabled())) {
                tmpSSLArtifactsList.add("SSLv2");
            }
            if (Boolean.parseBoolean(ssl.getSsl3Enabled())) {
                tmpSSLArtifactsList.add("SSLv3");
            }
            if (Boolean.parseBoolean(ssl.getTlsEnabled())) {
                tmpSSLArtifactsList.add("TLSv1");
            }
            if (Boolean.parseBoolean(ssl.getSsl3Enabled()) ||
                Boolean.parseBoolean(ssl.getTlsEnabled())) {
                tmpSSLArtifactsList.add("SSLv2Hello");
            }
            if (tmpSSLArtifactsList.isEmpty()) {
                logEmptyWarning(ssl, "WEB0307: All SSL protocol variants disabled for network-listener {0}," +
                        " using SSL implementation specific defaults");
            } else {
                final String[] protocols = new String[tmpSSLArtifactsList.size()];
                tmpSSLArtifactsList.toArray(protocols);
                sslConfigHolder.setEnabledProtocols(protocols);
            }
            String auth = ssl.getClientAuth();
            if (auth != null) {
                if ("want".equalsIgnoreCase(auth.trim())) {
                    sslConfigHolder.setWantClientAuth(true);
                } else if ("need".equalsIgnoreCase(auth.trim())) {
                    sslConfigHolder.setNeedClientAuth(true);
                }
            }
            
            try {
                final String sslImplClassName = ssl.getClassname();
                if (sslImplClassName != null) {
                    SSLImplementation impl =
                            (SSLImplementation) ClassLoaderUtil.load(sslImplClassName);
                    if (impl != null) {
                        sslConfigHolder.setSSLImplementation(impl);
                    } else {
                        logger.log(Level.WARNING,
                                "Unable to load SSLImplementation: " +
                                sslImplClassName);
                        sslConfigHolder.setSSLImplementation(
                                SSLImplementation.getInstance());
                    }
                } else {
                    sslConfigHolder.setSSLImplementation(
                            SSLImplementation.getInstance());
                }
            } catch (Exception e) {
                logger.log(Level.WARNING, "SSL support could not be configured!", e);
                return false;
            }
            
            tmpSSLArtifactsList.clear();
            // ssl3-tls-ciphers
            final String ssl3Ciphers = ssl.getSsl3TlsCiphers();
            if (ssl3Ciphers != null && ssl3Ciphers.length() > 0) {
                final String[] ssl3CiphersArray = ssl3Ciphers.split(",");
                for (final String cipher : ssl3CiphersArray) {
                    tmpSSLArtifactsList.add(cipher.trim());
                }
            }
            // ssl2-tls-ciphers
            final String ssl2Ciphers = ssl.getSsl2Ciphers();
            if (ssl2Ciphers != null && ssl2Ciphers.length() > 0) {
                final String[] ssl2CiphersArray = ssl2Ciphers.split(",");
                for (final String cipher : ssl2CiphersArray) {
                    tmpSSLArtifactsList.add(cipher.trim());
                }
            }
            if (tmpSSLArtifactsList.isEmpty()) {
                logEmptyWarning(ssl, "WEB0308: All SSL cipher suites disabled for network-listener(s) {0}." +
                        "  Using SSL implementation specific defaults");
            } else {
                final String[] enabledCiphers = new String[tmpSSLArtifactsList.size()];
                tmpSSLArtifactsList.toArray(enabledCiphers);
                sslConfigHolder.setEnabledCipherSuites(enabledCiphers);
            }
        }

        try {
            initializeSSL(ssl, sslConfigHolder);
            return true;
        } catch (Exception e) {
            logger.log(Level.WARNING, "SSL support could not be configured!", e);
        }
        return false;
    }

    private static void logEmptyWarning(Ssl ssl, final String msg) {
        final StringBuilder name = new StringBuilder();
        for (NetworkListener listener : ((Protocol) ssl.getParent()).findNetworkListeners()) {
            if(name.length() != 0) {
                name.append(", ");
            }
            name.append(listener.getName());
        }
        logger.log(Level.FINE, msg, name.toString());
    }

    /**
     * Initializes SSL
     *
     * @param ssl
     *
     * @throws Exception
     */
    private static void initializeSSL(final Ssl ssl,
            final SSLConfigHolder sslConfigHolder) throws Exception {
        SSLImplementation sslHelper = sslConfigHolder.getSSLImplementation();

        final ServerSocketFactory serverSF = sslHelper.getServerSocketFactory();

        if (ssl != null) {
            if (ssl.getCrlFile() != null) {
                setAttribute(serverSF, "crlFile", ssl.getCrlFile(), null, null);
            }
            if (ssl.getTrustAlgorithm() != null) {
                setAttribute(serverSF, "truststoreAlgorithm", ssl.getTrustAlgorithm(), null, null);
            }

            if (ssl.getKeyAlgorithm() != null) {
                setAttribute(serverSF, "algorithm", ssl.getKeyAlgorithm(), null, null);
            }
            setAttribute(serverSF, "trustMaxCertLength", ssl.getTrustMaxCertLength(), null, null);
        }

        // key store settings
        setAttribute(serverSF, "keystore", ssl != null ? ssl.getKeyStore() : null, "javax.net.ssl.keyStore", null);
        setAttribute(serverSF, "keystoreType", ssl != null ? ssl.getKeyStoreType() : null, "javax.net.ssl.keyStoreType", "JKS");
        setAttribute(serverSF, "keystorePass", ssl != null ? ssl.getKeyStorePassword() : null, "javax.net.ssl.keyStorePassword", "changeit");
        // trust store settings
        setAttribute(serverSF, "truststore", ssl != null ? ssl.getTrustStore() : null, "javax.net.ssl.trustStore", null);
        setAttribute(serverSF, "truststoreType", ssl != null ? ssl.getTrustStoreType() : null, "javax.net.ssl.trustStoreType", "JKS");
        setAttribute(serverSF, "truststorePass", ssl != null ? ssl.getTrustStorePassword() : null, "javax.net.ssl.trustStorePassword", "changeit");
        // cert nick name
        serverSF.setAttribute("keyAlias", ssl != null ? ssl.getCertNickname() : null);
        serverSF.init();
        sslConfigHolder.setSSLContext(serverSF.getSSLContext());
    }


    public static boolean isAllowLazyInit(final Ssl ssl) {
        return ssl == null || Boolean.parseBoolean(ssl.getAllowLazyInit());
    }
    
    private static void setAttribute(final ServerSocketFactory serverSF, final String name, final String value,
        final String property, final String defaultValue) {
        serverSF.setAttribute(name, value == null ?
            System.getProperty(property, defaultValue) :
            value);
    }
}
