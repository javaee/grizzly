/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2007-2010 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.grizzly;

import com.sun.grizzly.util.SSLUtils;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

/**
 * SSL configuration helper.
 * 
 * @author Alexey Stashok
 * @author Hubert Iwaniuk
 * @author Bruno Harbulot
 */
public class SSLConfig {
    public static final String TRUST_STORE_PROVIDER = "javax.net.ssl.trustStoreProvider";

    public static final String KEY_STORE_PROVIDER = "javax.net.ssl.keyStoreProvider";

    public static final String TRUST_STORE_FILE = "javax.net.ssl.trustStore";

    public static final String KEY_STORE_FILE = "javax.net.ssl.keyStore";

    public static final String TRUST_STORE_PASSWORD = "javax.net.ssl.trustStorePassword";

    public static final String KEY_STORE_PASSWORD = "javax.net.ssl.keyStorePassword";

    public static final String TRUST_STORE_TYPE = "javax.net.ssl.trustStoreType";

    public static final String KEY_STORE_TYPE = "javax.net.ssl.keyStoreType";

    public static final String KEY_FACTORY_MANAGER_ALGORITHM = "ssl.KeyManagerFactory.algorithm";

    public static final String TRUST_FACTORY_MANAGER_ALGORITHM = "ssl.TrustManagerFactory.algorithm";

    /**
     * Default Logger.
     */
    private static Logger logger = Logger.getLogger("grizzly");

    /**
     * Default SSL configuration. If you have changed any of
     * {@link System#getProperties()} of javax.net.ssl family you should refresh
     * this configuration by calling {@link #retrieve(java.util.Properties)}.
     */
    public static SSLConfig DEFAULT_CONFIG = new SSLConfig();

    private String trustStoreProvider;
    private String keyStoreProvider;

    private String trustStoreType;
    private String keyStoreType;

    private char[] trustStorePass;
    private char[] keyStorePass;
    private char[] keyPass;

    private String trustStoreFile;
    private String keyStoreFile;

    private String trustManagerFactoryAlgorithm;
    private String keyManagerFactoryAlgorithm;

    private String securityProtocol = "TLS";

    private boolean clientMode = false;

    private boolean needClientAuth = false;

    private boolean wantClientAuth = false;

    private int sslInactivityTimeout = SSLUtils.DEFAULT_SSL_INACTIVITY_TIMEOUT;

    /**
     * Default constructor. Reads configuration properties from
     * {@link System#getProperties()}. Calls {@link #SSLConfig(boolean)} with
     * <code>true</code>.
     */
    public SSLConfig() {
        this(true);
    }

    /**
     * Constructor that allows you creating empty configuration.
     * 
     * @param readSystemProperties
     *            If <code>true</code> populates configuration from
     *            {@link System#getProperties()}, else you have empty
     *            configuration.
     */
    public SSLConfig(boolean readSystemProperties) {
        if (readSystemProperties) {
            retrieve(System.getProperties());
        }
    }

    /**
     * Sets the <em>trust</em> store provider name.
     * 
     * @param trustStoreProvider
     *            <em>Trust</em> store provider to set.
     */
    public void setTrustStoreProvider(String trustStoreProvider) {
        this.trustStoreProvider = trustStoreProvider;
    }

    /**
     * Sets the <em>key</em> store provider name.
     * 
     * @param keyStoreProvider
     *            <em>Key</em> store provider to set.
     */
    public void setKeyStoreProvider(String keyStoreProvider) {
        this.keyStoreProvider = keyStoreProvider;
    }

    /**
     * Type of <em>trust</em> store.
     * 
     * @param trustStoreType
     *            Type of <em>trust</em> store to set.
     */
    public void setTrustStoreType(String trustStoreType) {
        this.trustStoreType = trustStoreType;
    }

    /**
     * Type of <em>key</em> store.
     * 
     * @param keyStoreType
     *            Type of <em>key</em> store to set.
     */
    public void setKeyStoreType(String keyStoreType) {
        this.keyStoreType = keyStoreType;
    }

    /**
     * Password of <em>trust</em> store.
     * 
     * @param trustStorePass
     *            Password of <em>trust</em> store to set.
     */
    public void setTrustStorePass(String trustStorePass) {
        this.trustStorePass = trustStorePass.toCharArray();
    }

    /**
     * Password of <em>key</em> store.
     * 
     * @param keyStorePass
     *            Password of <em>key</em> store to set.
     */
    public void setKeyStorePass(String keyStorePass) {
        this.keyStorePass = keyStorePass.toCharArray();
    }

    /**
     * Password of <em>key</em> store.
     * 
     * @param keyStorePass
     *            Password of <em>key</em> store to set.
     */
    public void setKeyStorePass(char[] keyStorePass) {
        this.keyStorePass = keyStorePass;
    }

    /**
     * Password of the key in the <em>key</em> store.
     * 
     * @param keyPass
     *            Password of <em>key</em> to set.
     */
    public void setKeyPass(String keyPass) {
        this.keyPass = keyPass.toCharArray();
    }

    /**
     * Password of the key in the <em>key</em> store.
     * 
     * @param keyPass
     *            Password of <em>key</em> to set.
     */
    public void setKeyPass(char[] keyPass) {
        this.keyPass = keyPass;
    }

    /**
     * Sets trust store file name, also makes sute that if other trust store
     * configutation parameters are not set to set them to default values.
     * 
     * @param trustStoreFile
     *            File name of trust store.
     */
    public void setTrustStoreFile(String trustStoreFile) {
        this.trustStoreFile = trustStoreFile;
    }

    /**
     * Sets key store file name, also makes sure that if other key store
     * configuration parameters are not set to set them to default values.
     * 
     * @param keyStoreFile
     *            File name of key store.
     */
    public void setKeyStoreFile(String keyStoreFile) {
        this.keyStoreFile = keyStoreFile;
    }

    /**
     * Sets the trust manager factory algorithm.
     * 
     * @param trustManagerFactoryAlgorithm
     *            the trust manager factory algorithm.
     */
    public void setTrustManagerFactoryAlgorithm(
            String trustManagerFactoryAlgorithm) {
        this.trustManagerFactoryAlgorithm = trustManagerFactoryAlgorithm;
    }

    /**
     * Sets the key manager factory algorithm.
     * 
     * @param keyManagerFactoryAlgorithm
     *            the key manager factory algorithm.
     */
    public void setKeyManagerFactoryAlgorithm(String keyManagerFactoryAlgorithm) {
        this.keyManagerFactoryAlgorithm = keyManagerFactoryAlgorithm;
    }

    /**
     * Sets the SSLContext protocole. The default value is <code>TLS</code> if
     * this is null.
     * 
     * @param securityProtocol
     */
    public void setSecurityProtocol(String securityProtocol) {
        this.securityProtocol = securityProtocol;
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

    public boolean isClientMode() {
        return clientMode;
    }

    public void setClientMode(boolean clientMode) {
        this.clientMode = clientMode;
    }

    public int getSslInactivityTimeout() {
        return sslInactivityTimeout;
    }

    public void setSslInactivityTimeout(int sslInactivityTimeout) {
        this.sslInactivityTimeout = sslInactivityTimeout;
    }

    /**
     * Validates {@link SSLConfig} configuration.
     * 
     * @return <code>true</code> iff configuration is valid, else
     *         <code>false</code>.
     */
    public boolean validateConfiguration() {
        return validateConfiguration(false);
    }

    /**
     * Validates {@link SSLConfig} configuration.
     * 
     * @param needsKeyStore
     *            forces failure if no keystore is specified.
     * @return <code>true</code> iff configuration is valid, else
     *         <code>false</code>.
     */
    public boolean validateConfiguration(boolean needsKeyStore) {
        boolean valid = true;

        if (keyStoreFile != null) {
            try {
                KeyStore keyStore;
                if (keyStoreProvider != null) {
                    keyStore = KeyStore.getInstance(
                            keyStoreType != null ? keyStoreType : KeyStore
                                    .getDefaultType(), keyStoreProvider);
                } else {
                    keyStore = KeyStore
                            .getInstance(keyStoreType != null ? keyStoreType
                                    : KeyStore.getDefaultType());
                }
                InputStream keyStoreInputStream = null;
                if (!keyStoreFile.equals("NONE")) {
                    keyStoreInputStream = new FileInputStream(keyStoreFile);
                }
                keyStore.load(keyStoreInputStream, keyStorePass);
                if (keyStoreInputStream != null) {
                    keyStoreInputStream.close();
                }

                String kmfAlgorithm = keyManagerFactoryAlgorithm;
                if (kmfAlgorithm == null) {
                    kmfAlgorithm = System.getProperty(
                            KEY_FACTORY_MANAGER_ALGORITHM, KeyManagerFactory
                                    .getDefaultAlgorithm());
                }
                KeyManagerFactory keyManagerFactory = KeyManagerFactory
                        .getInstance(kmfAlgorithm);
                keyManagerFactory.init(keyStore, keyPass != null ? keyPass
                        : keyStorePass);
            } catch (KeyStoreException e) {
                logger.log(Level.FINE, "Error initializing key store", e);
                valid = false;
            } catch (CertificateException e) {
                logger.log(Level.FINE, "Key store certificate exception.", e);
                valid = false;
            } catch (UnrecoverableKeyException e) {
                logger.log(Level.FINE, "Key store unrecoverable exception.", e);
                valid = false;
            } catch (FileNotFoundException e) {
                logger.log(Level.FINE, "Can't find key store file: "
                        + keyStoreFile, e);
                valid = false;
            } catch (IOException e) {
                logger.log(Level.FINE, "Error loading key store from file: "
                        + keyStoreFile, e);
                valid = false;
            } catch (NoSuchAlgorithmException e) {
                logger.log(Level.FINE,
                        "Error initializing key manager factory (no such algorithm)", e);
                valid = false;
            } catch (NoSuchProviderException e) {
                logger.log(Level.FINE,
                        "Error initializing key store (no such provider)", e);
                valid = false;
            }
        } else {
            valid &= !needsKeyStore;
        }

        if (trustStoreFile != null) {
            try {
                KeyStore trustStore;
                if (trustStoreProvider != null) {
                    trustStore = KeyStore.getInstance(
                            trustStoreType != null ? trustStoreType : KeyStore
                                    .getDefaultType(), trustStoreProvider);
                } else {
                    trustStore = KeyStore
                            .getInstance(trustStoreType != null ? trustStoreType
                                    : KeyStore.getDefaultType());
                }
                InputStream trustStoreInputStream = null;
                if (!trustStoreFile.equals("NONE")) {
                    trustStoreInputStream = new FileInputStream(trustStoreFile);
                }
                trustStore.load(trustStoreInputStream, trustStorePass);
                if (trustStoreInputStream != null) {
                    trustStoreInputStream.close();
                }

                String tmfAlgorithm = trustManagerFactoryAlgorithm;
                if (tmfAlgorithm == null) {
                    tmfAlgorithm = System.getProperty(
                            TRUST_FACTORY_MANAGER_ALGORITHM,
                            TrustManagerFactory.getDefaultAlgorithm());
                }

                TrustManagerFactory trustManagerFactory = TrustManagerFactory
                        .getInstance(tmfAlgorithm);
                trustManagerFactory.init(trustStore);
            } catch (KeyStoreException e) {
                logger.log(Level.FINE, "Error initializing trust store", e);
                valid = false;
            } catch (CertificateException e) {
                logger.log(Level.FINE, "Trust store certificate exception.", e);
                valid = false;
            } catch (FileNotFoundException e) {
                logger.log(Level.FINE, "Can't find trust store file: "
                        + trustStoreFile, e);
                valid = false;
            } catch (IOException e) {
                logger.log(Level.FINE, "Error loading trust store from file: "
                        + trustStoreFile, e);
                valid = false;
            } catch (NoSuchAlgorithmException e) {
                logger
                        .log(
                                Level.FINE,
                                "Error initializing trust manager factory (no such algorithm)",
                                e);
                valid = false;
            } catch (NoSuchProviderException e) {
                logger.log(Level.FINE,
                        "Error initializing trust store (no such provider)", e);
                valid = false;
            }
        }
        return valid;
    }

    public SSLContext createSSLContext() {
        SSLContext sslContext = null;

        try {
            TrustManagerFactory trustManagerFactory = null;
            KeyManagerFactory keyManagerFactory = null;

            if (keyStoreFile != null) {
                try {
                    KeyStore keyStore;
                    if (keyStoreProvider != null) {
                        keyStore = KeyStore.getInstance(
                                keyStoreType != null ? keyStoreType : KeyStore
                                        .getDefaultType(), keyStoreProvider);
                    } else {
                        keyStore = KeyStore
                                .getInstance(keyStoreType != null ? keyStoreType
                                        : KeyStore.getDefaultType());
                    }
                    InputStream keyStoreInputStream = null;
                    if (!keyStoreFile.equals("NONE")) {
                        keyStoreInputStream = new FileInputStream(keyStoreFile);
                    }
                    keyStore.load(keyStoreInputStream, keyStorePass);
                    if (keyStoreInputStream != null) {
                        keyStoreInputStream.close();
                    }

                    String kmfAlgorithm = keyManagerFactoryAlgorithm;
                    if (kmfAlgorithm == null) {
                        kmfAlgorithm = System.getProperty(
                                KEY_FACTORY_MANAGER_ALGORITHM,
                                KeyManagerFactory.getDefaultAlgorithm());
                    }
                    keyManagerFactory = KeyManagerFactory
                            .getInstance(kmfAlgorithm);
                    keyManagerFactory.init(keyStore, keyPass != null ? keyPass
                            : keyStorePass);
                } catch (KeyStoreException e) {
                    logger.log(Level.FINE, "Error initializing key store", e);
                } catch (CertificateException e) {
                    logger.log(Level.FINE, "Key store certificate exception.",
                            e);
                } catch (UnrecoverableKeyException e) {
                    logger.log(Level.FINE,
                            "Key store unrecoverable exception.", e);
                } catch (FileNotFoundException e) {
                    logger.log(Level.FINE, "Can't find key store file: "
                            + keyStoreFile, e);
                } catch (IOException e) {
                    logger.log(Level.FINE,
                            "Error loading key store from file: "
                                    + keyStoreFile, e);
                } catch (NoSuchAlgorithmException e) {
                    logger
                            .log(
                                    Level.FINE,
                                    "Error initializing key manager factory (no such algorithm)",
                                    e);
                } catch (NoSuchProviderException e) {
                    logger.log(Level.FINE,
                            "Error initializing key store (no such provider)",
                            e);
                }
            }

            if (trustStoreFile != null) {
                try {
                    KeyStore trustStore;
                    if (trustStoreProvider != null) {
                        trustStore = KeyStore.getInstance(
                                trustStoreType != null ? trustStoreType
                                        : KeyStore.getDefaultType(),
                                trustStoreProvider);
                    } else {
                        trustStore = KeyStore
                                .getInstance(trustStoreType != null ? trustStoreType
                                        : KeyStore.getDefaultType());
                    }
                    InputStream trustStoreInputStream = null;
                    if (!trustStoreFile.equals("NONE")) {
                        trustStoreInputStream = new FileInputStream(
                                trustStoreFile);
                    }
                    trustStore.load(trustStoreInputStream, trustStorePass);
                    if (trustStoreInputStream != null) {
                        trustStoreInputStream.close();
                    }

                    String tmfAlgorithm = trustManagerFactoryAlgorithm;
                    if (tmfAlgorithm == null) {
                        tmfAlgorithm = System.getProperty(
                                TRUST_FACTORY_MANAGER_ALGORITHM,
                                TrustManagerFactory.getDefaultAlgorithm());
                    }

                    trustManagerFactory = TrustManagerFactory
                            .getInstance(tmfAlgorithm);
                    trustManagerFactory.init(trustStore);
                } catch (KeyStoreException e) {
                    logger.log(Level.FINE, "Error initializing trust store", e);
                } catch (CertificateException e) {
                    logger.log(Level.FINE,
                            "Trust store certificate exception.", e);
                } catch (FileNotFoundException e) {
                    logger.log(Level.FINE, "Can't find trust store file: "
                            + trustStoreFile, e);
                } catch (IOException e) {
                    logger.log(Level.FINE,
                            "Error loading trust store from file: "
                                    + trustStoreFile, e);
                } catch (NoSuchAlgorithmException e) {
                    logger
                            .log(
                                    Level.FINE,
                                    "Error initializing trust manager factory (no such algorithm)",
                                    e);
                } catch (NoSuchProviderException e) {
                    logger
                            .log(
                                    Level.FINE,
                                    "Error initializing trust store (no such provider)",
                                    e);
                }
            }

            String secProtocol = "TLS";
            if (securityProtocol != null) {
                secProtocol = securityProtocol;
            }
            sslContext = SSLContext.getInstance(secProtocol);
            sslContext.init(keyManagerFactory != null ? keyManagerFactory
                    .getKeyManagers() : null,
                    trustManagerFactory != null ? trustManagerFactory
                            .getTrustManagers() : null, null);
        } catch (KeyManagementException e) {
            logger.log(Level.FINE, "Key management error.", e);
        } catch (NoSuchAlgorithmException e) {
            logger.log(Level.FINE, "Error initializing algorithm.", e);
        }

        return sslContext;
    }

    public void retrieve(Properties props) {
        trustStoreProvider = props.getProperty(TRUST_STORE_PROVIDER);
        keyStoreProvider = props.getProperty(KEY_STORE_PROVIDER);

        trustStoreType = props.getProperty(TRUST_STORE_TYPE);
        keyStoreType = props.getProperty(KEY_STORE_TYPE);

        if (props.getProperty(TRUST_STORE_PASSWORD) != null) {
            trustStorePass = props.getProperty(TRUST_STORE_PASSWORD)
                    .toCharArray();
        } else {
            trustStorePass = null;
        }

        if (props.getProperty(KEY_STORE_PASSWORD) != null) {
            keyStorePass = props.getProperty(KEY_STORE_PASSWORD).toCharArray();
        } else {
            keyStorePass = null;
        }

        trustStoreFile = props.getProperty(TRUST_STORE_FILE);
        keyStoreFile = props.getProperty(KEY_STORE_FILE);

        securityProtocol = "TLS";
    }
}
