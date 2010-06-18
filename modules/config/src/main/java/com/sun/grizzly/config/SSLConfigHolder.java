/*
 *
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2007-2010 Sun Microsystems, Inc. All rights reserved.
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

package com.sun.grizzly.config;

import com.sun.grizzly.SSLConfig;
import com.sun.grizzly.config.dom.NetworkListener;
import com.sun.grizzly.config.dom.Protocol;
import com.sun.grizzly.config.dom.Ssl;
import com.sun.grizzly.util.net.SSLImplementation;
import com.sun.grizzly.util.net.ServerSocketFactory;
import java.security.AccessController;
import java.security.PrivilegedAction;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.ssl.SSLException;

/**
 *
 * @author oleksiys
 */
public class SSLConfigHolder {
    public static final String APP_BUFFER_ATTR_NAME = "TMP_DECODED_BUFFER";

    private static final String PLAIN_PASSWORD_PROVIDER_NAME = "plain";

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
     * SSL settings
     */
    private final Ssl ssl;

    public SSLConfigHolder(final Ssl ssl) throws SSLException {
        this.ssl = ssl;
        sslImplementation = lookupSSLImplementation(ssl);

        if (sslImplementation == null) {
            throw new SSLException("Can not configure SSLImplementation");
        }
    }

    /**
     * Set the SSLContext required to support SSL over NIO.
     */
    public void setSSLConfig(final SSLConfig sslConfig) {
        sslContext = sslConfig.createSSLContext();
    }

    /**
     * Return the SSLContext required to support SSL over NIO.
     */
    public SSLContext getSSLContext() {
        return sslContext;
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
     * Returns the list of protocols to be enabled when {@link SSLEngine} is initialized.
     *
     * @return <tt>null</tt> means 'use {@link SSLEngine}'s default.'
     */
    public String[] getEnabledProtocols() {
        return enabledProtocols;
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
     * Returns <tt>true</tt> if the SSLEngine will <em>require</em> client authentication.
     */
    public boolean isNeedClientAuth() {
        return needClientAuth;
    }

    /**
     * Returns <tt>true</tt> if the engine will <em>request</em> client authentication.
     */
    public boolean isWantClientAuth() {
        return wantClientAuth;
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
     */
    public boolean configureSSL() {
        final List<String> tmpSSLArtifactsList = new LinkedList<String>();
        if (ssl != null) {
            // client-auth
            if (Boolean.parseBoolean(ssl.getClientAuthEnabled())) {
                needClientAuth = true;
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
                enabledProtocols = protocols;
            }
            String auth = ssl.getClientAuth();
            if (auth != null) {
                if ("want".equalsIgnoreCase(auth.trim())) {
                    wantClientAuth = true;
                } else if ("need".equalsIgnoreCase(auth.trim())) {
                    needClientAuth = true;
                }
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
                enabledCipherSuites = enabledCiphers;
            }
        }

        try {
            initializeSSL();
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
    private void initializeSSL() throws Exception {
        SSLImplementation sslHelper = getSSLImplementation();

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
        setAttribute(serverSF, "keystorePass", ssl != null ? getKeyStorePassword(ssl) : null, "javax.net.ssl.keyStorePassword", "changeit");
        // trust store settings
        setAttribute(serverSF, "truststore", ssl != null ? ssl.getTrustStore() : null, "javax.net.ssl.trustStore", null);
        setAttribute(serverSF, "truststoreType", ssl != null ? ssl.getTrustStoreType() : null, "javax.net.ssl.trustStoreType", "JKS");
        setAttribute(serverSF, "truststorePass", ssl != null ? getTrustStorePassword(ssl) : null, "javax.net.ssl.trustStorePassword", "changeit");
        // cert nick name
        serverSF.setAttribute("keyAlias", ssl != null ? ssl.getCertNickname() : null);
        serverSF.init();
        sslContext = serverSF.getSSLContext();
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

    private static String getKeyStorePassword(Ssl ssl) {
        if (PLAIN_PASSWORD_PROVIDER_NAME.equalsIgnoreCase(ssl.getKeyStorePasswordProvider())) {
            return ssl.getKeyStorePassword();
        } else {
            return getStorePasswordCustom(ssl.getKeyStorePassword());
        }
    }

    private static String getTrustStorePassword(Ssl ssl) {
        if (PLAIN_PASSWORD_PROVIDER_NAME.equalsIgnoreCase(ssl.getTrustStorePasswordProvider())) {
            return ssl.getTrustStorePassword();
        } else {
            return getStorePasswordCustom(ssl.getTrustStorePassword());
        }
    }

    private static String getStorePasswordCustom(String storePasswordProvider) {
        try {
            final SecurePasswordProvider provider =
                    (SecurePasswordProvider) Utils.newInstance(storePasswordProvider);
            if (provider != null) {
                return provider.getPassword();
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Secure password provider could not " +
                    "be initialized: " + storePasswordProvider, e);
        }

        return null;
    }

    private static SSLImplementation lookupSSLImplementation(Ssl ssl) {
        try {
            final String sslImplClassName = ssl.getClassname();
            if (sslImplClassName != null) {
                try {
                    Class clazz;
                    ClassLoader cl = getContextClassLoader();
                    if (cl != null) {
                        try {
                            clazz = Class.forName(sslImplClassName, false, cl);
                        } catch (ClassNotFoundException ex) {
                            clazz = Class.forName(sslImplClassName);
                        }
                    } else {
                        clazz = Class.forName(sslImplClassName);
                    }

                    SSLImplementation impl = (SSLImplementation) clazz.newInstance();

                    if (impl != null) {
                        return impl;
                    } else {
                        logger.log(Level.WARNING, "Unable to load SSLImplementation: {0}",
                                sslImplClassName);
                        return SSLImplementation.getInstance();
                    }
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Unable to load class " + sslImplClassName, e);
                    return SSLImplementation.getInstance();
                }
            } else {
                return SSLImplementation.getInstance();
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "SSL support could not be configured!", e);
        }

        return null;
    }

    private static ClassLoader getContextClassLoader() {
        return AccessController.doPrivileged(
                new PrivilegedAction<ClassLoader>() {

                    public ClassLoader run() {
                        ClassLoader cl = null;
                        try {
                            cl = Thread.currentThread().getContextClassLoader();
                        } catch (SecurityException ex) {
                        }
                        return cl;
                    }
                });
    }
}
