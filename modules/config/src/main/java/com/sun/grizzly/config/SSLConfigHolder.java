/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2009-2012 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.grizzly.config;

import com.sun.grizzly.util.LogMessages;
import com.sun.grizzly.SSLConfig;
import com.sun.grizzly.config.dom.NetworkListener;
import com.sun.grizzly.config.dom.Protocol;
import com.sun.grizzly.config.dom.Ssl;
import com.sun.grizzly.util.net.SSLImplementation;
import com.sun.grizzly.util.net.ServerSocketFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLServerSocketFactory;

import com.sun.hk2.component.Holder;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.jvnet.hk2.component.Habitat;

/**
 *
 * @author oleksiys
 */
public class SSLConfigHolder {
    private static final String PLAIN_PASSWORD_PROVIDER_NAME = "plain";

    private final static Logger LOGGER = GrizzlyEmbeddedHttps.logger();

    /**
     * The <code>SSLImplementation</code>
     */
    protected Holder<SSLImplementation> sslImplementation;
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
     * Handshake timeout.
     */
    protected int sslInactivityTimeout = Ssl.DEFAULT_SSL_INACTIVITY_TIMEOUT * 1000;

    /**
     * SSL settings
     */
    private final Ssl ssl;

    @SuppressWarnings({"unchecked"})
    public SSLConfigHolder(final Habitat habitat, final Ssl ssl) throws SSLException {
        this.ssl = ssl;
        sslImplementation = habitat.getInhabitantByContract(
                                            SSLImplementation.class.getName(),
                                            ssl.getClassname());
        if (sslImplementation == null) {
            final SSLImplementation impl = lookupSSLImplementation(habitat, ssl);
            if (impl == null) {
                throw new SSLException("Can not configure SSLImplementation");
            }
            sslImplementation = new Holder<SSLImplementation>() {
                public SSLImplementation get() {
                    return impl;
                }
            };
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
     * Return the <code>SSLImplementation</code>
     */
    public SSLImplementation getSSLImplementation() {
        return sslImplementation.get();
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

    /**
     * @see com.sun.grizzly.config.dom.Ssl#getSSLInactivityTimeout()
     */
    public int getSslInactivityTimeout() {
        return sslInactivityTimeout;
    }

    public SSLEngine createSSLEngine() {
        final SSLEngine sslEngine = sslContext.createSSLEngine();

        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.log(Level.FINE, "newSSLEngine: {0}", sslEngine);
        }

        if (enabledCipherSuites != null){
            sslEngine.setEnabledCipherSuites(enabledCipherSuites);
        }

        if (enabledProtocols != null){
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
     * Configures the SSL properties on the given PECoyoteConnector from the SSL config of the given HTTP listener.
     */
    public boolean configureSSL() {
        final List<String> tmpSSLArtifactsList = new LinkedList<String>();

        try {
            initializeSSL();

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
                if (Boolean.parseBoolean(ssl.getTls11Enabled())) {
                    tmpSSLArtifactsList.add("TLSv1.1");
                }
                if (Boolean.parseBoolean(ssl.getTls12Enabled())) {
                    tmpSSLArtifactsList.add("TLSv1.2");
                }
                if (Boolean.parseBoolean(ssl.getSsl3Enabled())
                        || Boolean.parseBoolean(ssl.getTlsEnabled())) {
                    tmpSSLArtifactsList.add("SSLv2Hello");
                }
                if (tmpSSLArtifactsList.isEmpty()) {
                    logEmptyWarning(ssl, "WEB0307: All SSL protocol variants disabled for network-listener {0},"
                            + " using SSL implementation specific defaults");
                } else {
                    enabledProtocols = filterSupportedProtocols(tmpSSLArtifactsList);
                }
                String auth = ssl.getClientAuth();
                if (auth != null) {
                    if ("want".equalsIgnoreCase(auth.trim())) {
                        wantClientAuth = true;
                    } else if ("need".equalsIgnoreCase(auth.trim())) {
                        needClientAuth = true;
                    }
                }

                sslInactivityTimeout = Integer.parseInt(ssl.getSSLInactivityTimeout()) * 1000;

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

                final String[] ciphers = getJSSECiphers(tmpSSLArtifactsList);
                if (ciphers == null || ciphers.length == 0) {
                    logEmptyWarning(ssl, "WEB0308: All SSL cipher suites disabled for network-listener(s) {0}."
                            + "  Using SSL implementation specific defaults");
                } else {
                    enabledCipherSuites = ciphers;
                }
            }

            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.log(Level.FINE, "Enabled secure protocols={0}"
                        + "" + " ciphers={1}", new Object[]
                        {Arrays.asList(enabledProtocols), Arrays.asList(enabledCipherSuites)});
            }
            
            return true;
        } catch (Exception e) {
            if (LOGGER.isLoggable(Level.WARNING)) {
                LOGGER.log(Level.WARNING,
                        LogMessages.WARNING_GRIZZLY_CONFIG_SSL_GENERAL_CONFIG_ERROR(),
                        e);
            }
        }

        return false;
    }

    protected void logEmptyWarning(Ssl ssl, final String msg) {
        final StringBuilder name = new StringBuilder();
        for (NetworkListener listener : ((Protocol) ssl.getParent()).findNetworkListeners()) {
            if(name.length() != 0) {
                name.append(", ");
            }
            name.append(listener.getName());
        }
        LOGGER.log(Level.FINE, msg, name.toString());
    }

    /**
     * Initializes SSL
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
        
        CipherInfo.updateCiphers(sslContext);
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
            if (LOGGER.isLoggable(Level.WARNING)) {
                LOGGER.log(Level.WARNING,
                           LogMessages.WARNING_GRIZZLY_CONFIG_SSL_SECURE_PASSWORD_INITIALIZATION_ERROR(storePasswordProvider),
                           e);
            }
        }

        return null;
    }

    private static SSLImplementation lookupSSLImplementation(
            final Habitat habitat, final Ssl ssl) {
        
        try {
            final String sslImplClassName = ssl.getClassname();
            if (sslImplClassName != null) {
                final SSLImplementation impl = Utils.newInstance(habitat,
                        SSLImplementation.class,
                        sslImplClassName, sslImplClassName);

                if (impl != null) {
                    return impl;
                } else {
                    if (LOGGER.isLoggable(Level.WARNING)) {
                        LOGGER.warning(LogMessages.WARNING_GRIZZLY_CONFIG_SSL_SSL_IMPLEMENTATION_LOAD_ERROR(sslImplClassName));
                    }
                    return SSLImplementation.getInstance();
                }
            } else {
                return SSLImplementation.getInstance();
            }
        } catch (Exception e) {
            if (LOGGER.isLoggable(Level.WARNING)) {
                LOGGER.log(Level.WARNING,
                           LogMessages.WARNING_GRIZZLY_CONFIG_SSL_GENERAL_CONFIG_ERROR(),
                           e);
            }
        }

        return null;
    }


    /*
     * Iterates through the provided protocols.  If any of these protocols are
     * not supported by the current SSL implemenation, they will be pruned from
     * the list before configuring the SSL runtime.
     *
     * @param protocols the desired protocols.
     *
     * @return a String array of the protocols that may be safely passed to
     *  the SSL implementation.
     */
    private String[] filterSupportedProtocols(final List<String> protocols) {

        if (protocols.isEmpty()) {
            //noinspection ToArrayCallWithZeroLengthArrayArgument
            return protocols.toArray(new String[0]);
        }
        final SSLEngine engine = sslContext.createSSLEngine();
        final String[] supportedProtocols = engine.getSupportedProtocols();
        Arrays.sort(supportedProtocols);
        for (Iterator<String> i = protocols.iterator(); i.hasNext(); ) {
            final String protocol = i.next();
            if (Arrays.binarySearch(supportedProtocols, protocol) < 0) {
                i.remove();
                if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.log(Level.FINE,
                               "Protocol {0}, not supported by current SSL implementation; ignoring.",
                               protocol);
                }
            }
        }
        return protocols.toArray(new String[protocols.size()]);

    }

    /*
     * Evaluates the given List of cipher suite names, converts each cipher
     * suite that is enabled (i.e., not preceded by a '-') to the corresponding
     * JSSE cipher suite name, and returns a String[] of enabled cipher suites.
     *
     * @param sslCiphers List of SSL ciphers to evaluate.
     *
     * @return String[] of cipher suite names, or null if none of the cipher
     *  suites in the given List are enabled or can be mapped to corresponding
     *  JSSE cipher suite names
     */
    private static String[] getJSSECiphers(final List<String> configuredCiphers) {
        Set<String> enabledCiphers = null;
        for (String cipher : configuredCiphers) {
            if (cipher.length() > 0 && cipher.charAt(0) != '-') {
                if (cipher.charAt(0) == '+') {
                    cipher = cipher.substring(1);
                }
                final String jsseCipher = getJSSECipher(cipher);
                if (jsseCipher == null) {
                    if (LOGGER.isLoggable(Level.WARNING)) {
                        LOGGER.warning(LogMessages.WARNING_GRIZZLY_CONFIG_SSL_UNKNOWN_CIPHER_ERROR(cipher));
                    }
                } else {
                    if (enabledCiphers == null) {
                        enabledCiphers = new HashSet<String>(configuredCiphers.size());
                    }
                    enabledCiphers.add(jsseCipher);
                }
            }
        }

        return enabledCiphers == null
                ? null
                : enabledCiphers.toArray(new String[enabledCiphers.size()]);
    }


    /*
     * Converts the given cipher suite name to the corresponding JSSE cipher.
     *
     * @param cipher The cipher suite name to convert
     *
     * @return The corresponding JSSE cipher suite name, or null if the given
     * cipher suite name can not be mapped
     */
    private static String getJSSECipher(final String cipher) {

        final CipherInfo ci = CipherInfo.getCipherInfo(cipher);
        return ci != null ? ci.getCipherName() : null;

    }


    // ---------------------------------------------------------- Nested Classes


    /**
     * This class represents the information associated with ciphers.
     * It also maintains a Map from configName to CipherInfo.
     */
    private static final class CipherInfo {
        private static final short SSL2 = 0x1;
        private static final short SSL3 = 0x2;
        private static final short TLS = 0x4;

        // The old names mapped to the standard names as existed
        private static final String[][] OLD_CIPHER_MAPPING = {
                // IWS 6.x or earlier
                {"rsa_null_md5", "SSL_RSA_WITH_NULL_MD5"},
                {"rsa_null_sha", "SSL_RSA_WITH_NULL_SHA"},
                {"rsa_rc4_40_md5", "SSL_RSA_EXPORT_WITH_RC4_40_MD5"},
                {"rsa_rc4_128_md5", "SSL_RSA_WITH_RC4_128_MD5"},
                {"rsa_rc4_128_sha", "SSL_RSA_WITH_RC4_128_SHA"},
                {"rsa_3des_sha", "SSL_RSA_WITH_3DES_EDE_CBC_SHA"},
                {"fips_des_sha", "SSL_RSA_WITH_DES_CBC_SHA"},
                {"rsa_des_sha", "SSL_RSA_WITH_DES_CBC_SHA"},

                // backward compatible with AS 9.0 or earlier
                {"SSL_RSA_WITH_NULL_MD5", "SSL_RSA_WITH_NULL_MD5"},
                {"SSL_RSA_WITH_NULL_SHA", "SSL_RSA_WITH_NULL_SHA"}
        };

        private static final Map<String,CipherInfo> ciphers =
                new HashMap<String,CipherInfo>();
        private static final ReadWriteLock ciphersLock = new ReentrantReadWriteLock();

        @SuppressWarnings({"UnusedDeclaration"})
        private final String configName;
        private final String cipherName;
        private final short protocolVersion;

        static {
            for (int i = 0, len = OLD_CIPHER_MAPPING.length; i < len; i++) {
                String nonStdName = OLD_CIPHER_MAPPING[i][0];
                String stdName = OLD_CIPHER_MAPPING[i][1];
                ciphers.put(nonStdName,
                        new CipherInfo(nonStdName, stdName, (short) (SSL3 | TLS)));
            }
        }

        /**
         * @param configName      name used in domain.xml, sun-acc.xml
         * @param cipherName      name that may depends on backend
         * @param protocolVersion
         */
        private CipherInfo(final String configName,
                           final String cipherName,
                           final short protocolVersion) {
            this.configName = configName;
            this.cipherName = cipherName;
            this.protocolVersion = protocolVersion;
        }

        public static void updateCiphers(final SSLContext sslContext) {
            SSLServerSocketFactory factory = sslContext.getServerSocketFactory();
            String[] supportedCiphers = factory.getDefaultCipherSuites();
            
            ciphersLock.writeLock().lock();
            try {
                for (int i = 0, len = supportedCiphers.length; i < len; i++) {
                    String s = supportedCiphers[i];
                    ciphers.put(s, new CipherInfo(s, s, (short) (SSL3 | TLS)));
                }
            } finally {
                ciphersLock.writeLock().unlock();
            }
        }
        public static CipherInfo getCipherInfo(final String configName) {
            ciphersLock.readLock().lock();
            try {
                return ciphers.get(configName);
            } finally {
                ciphersLock.readLock().unlock();
            }
        }

        public String getCipherName() {
            return cipherName;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public boolean isSSL2() {
            return (protocolVersion & SSL2) == SSL2;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public boolean isSSL3() {
            return (protocolVersion & SSL3) == SSL3;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public boolean isTLS() {
            return (protocolVersion & TLS) == TLS;
        }

    } // END CipherInfo
}
