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

import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.grizzly.config.dom.Protocol;
import com.sun.grizzly.config.dom.Ssl;
import com.sun.grizzly.ssl.SSLEngineConfigurator;
import com.sun.grizzly.util.ClassLoaderUtil;
import com.sun.grizzly.util.net.SSLImplementation;
import com.sun.grizzly.util.net.ServerSocketFactory;
import com.sun.grizzly.http.WebFilter;

/**
 *
 * @author oleksiys
 */
public class SSLConfigHolder extends SSLEngineConfigurator {
    private final static Logger logger = WebFilter.logger();

    /**
     * The <code>SSLImplementation</code>
     */
    private SSLImplementation sslImplementation;

    public SSLConfigHolder() {
        super(null);
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
     * Configures the SSL properties on the given PECoyoteConnector from the SSL config of the given HTTP listener.
     *
     * @param ssl
     */
    public static SSLConfigHolder configureSSL(final Ssl ssl) {
        final SSLConfigHolder config = new SSLConfigHolder();
        if (config.configure(ssl)) {
            return config;
        }

        return null;
    }
    
    /**
     * Configures the SSL properties on the given PECoyoteConnector from the SSL config of the given HTTP listener.
     *
     * @param ssl
     */
    private boolean configure(final Ssl ssl) {
        final List<String> tmpSSLArtifactsList = new LinkedList<String>();
        if (ssl != null) {
            // client-auth
            if (Boolean.parseBoolean(ssl.getClientAuthEnabled())) {
                setNeedClientAuth(true);
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
                logger.log(Level.WARNING, "pewebcontainer.all_ssl_protocols_disabled",
                    ((Protocol) ssl.getParent()).getName());
            } else {
                final String[] protocols = new String[tmpSSLArtifactsList.size()];
                tmpSSLArtifactsList.toArray(protocols);
                setEnabledProtocols(protocols);
            }
            String auth = ssl.getClientAuth();
            if (auth != null) {
                if ("want".equalsIgnoreCase(auth.trim())) {
                    setWantClientAuth(true);
                } else if ("need".equalsIgnoreCase(auth.trim())) {
                    setNeedClientAuth(true);
                }
            }
            if (ssl.getClassname() != null) {
                SSLImplementation impl = (SSLImplementation) ClassLoaderUtil.load(ssl.getClassname());
                if (impl != null) {
                    setSSLImplementation(impl);
                } else {
                    logger.log(Level.WARNING, "Unable to load SSLImplementation");

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
                logger.log(Level.WARNING, "pewebcontainer.all_ssl_ciphers_disabled",
                    ((Protocol) ssl.getParent()).getName());
            } else {
                final String[] enabledCiphers = new String[tmpSSLArtifactsList.size()];
                tmpSSLArtifactsList.toArray(enabledCiphers);
                setEnabledCipherSuites(enabledCiphers);
            }
        }

        try {
            initializeSSL(ssl);
            return true;
        } catch (Exception e) {
            logger.log(Level.WARNING, "SSL support could not be configured!", e);
        }
        return false;
    }

    /**
     * Initializes SSL
     *
     * @param ssl
     *
     * @throws Exception
     */
    private void initializeSSL(final Ssl ssl) throws Exception {
        final SSLImplementation sslHelper = SSLImplementation.getInstance();
        final ServerSocketFactory serverSF = sslHelper.getServerSocketFactory();

        if (ssl != null) {
            if (ssl.getCrlFile() != null) {
                setAttribute(serverSF, "crlFile", ssl.getCrlFile(), null, null);
            }
            if (ssl.getTrustAlgorithm() != null) {
                setAttribute(serverSF, "trustAlgorithm", ssl.getTrustAlgorithm(), null, null);
            }
            if (ssl.getTrustMaxCertLengthBytes() != null) {
                setAttribute(serverSF, "trustMaxCertLength", ssl.getTrustMaxCertLengthBytes(), null, null);
            }
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
        setSSLImplementation(sslHelper);
        setSslContext(serverSF.getSSLContext());
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
