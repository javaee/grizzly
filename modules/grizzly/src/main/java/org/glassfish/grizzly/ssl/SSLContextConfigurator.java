/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.glassfish.grizzly.ssl;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import org.glassfish.grizzly.Grizzly;

/**
 * Utility class, which helps to configure {@link SSLContext}.
 * 
 * @author Alexey Stashok
 */
public class SSLContextConfigurator {
    public static final String TRUST_STORE_FILE = "javax.net.ssl.trustStore";

    public static final String KEY_STORE_FILE = "javax.net.ssl.keyStore";

    public static final String TRUST_STORE_PASSWORD = "javax.net.ssl.trustStorePassword";

    public static final String KEY_STORE_PASSWORD = "javax.net.ssl.keyStorePassword";

    public static final String TRUST_STORE_TYPE = "javax.net.ssl.trustStoreType";

    public static final String KEY_STORE_TYPE = "javax.net.ssl.keyStoreType";

    /**
     * Default Logger.
     */
    private static Logger logger = Grizzly.logger;

    /**
     * Default SSL configuration
     */
    public static SSLContextConfigurator DEFAULT_CONFIG = new SSLContextConfigurator();

    private String trustStoreType;
    private String keyStoreType;

    private char[] trustStorePass;

    private char[] keyStorePass;

    private String trustStoreFile;
    private String keyStoreFile;

    private String trustStoreAlgorithm;
    private String keyStoreAlgorithm;

    private String securityProtocol;

    public SSLContextConfigurator() {
        this(true);
    }

    public SSLContextConfigurator(boolean readSystemProperties) {
        if (readSystemProperties) {
            retrieve(System.getProperties());
        }
    }

    public String getTrustStoreType() {
        return trustStoreType;
    }

    public void setTrustStoreType(String trustStoreType) {
        this.trustStoreType = trustStoreType;
    }

    public String getKeyStoreType() {
        return keyStoreType;
    }

    public void setKeyStoreType(String keyStoreType) {
        this.keyStoreType = keyStoreType;
    }

    public String getTrustStorePass() {
        return new String(trustStorePass);
    }

    public void setTrustStorePass(String trustStorePass) {
        this.trustStorePass = trustStorePass.toCharArray();
    }

    public String getKeyStorePass() {
        return new String(keyStorePass);
    }

    public void setKeyStorePass(String keyStorePass) {
        this.keyStorePass = keyStorePass.toCharArray();
    }

    public String getTrustStoreFile() {
        return trustStoreFile;
    }

    public void setTrustStoreFile(String trustStoreFile) {
        this.trustStoreFile = trustStoreFile;
    }

    public String getKeyStoreFile() {
        return keyStoreFile;
    }

    public void setKeyStoreFile(String keyStoreFile) {
        this.keyStoreFile = keyStoreFile;
    }

    public String getTrustStoreAlgorithm() {
        return trustStoreAlgorithm;
    }

    public void setTrustStoreAlgorithm(String trustStoreAlgorithm) {
        this.trustStoreAlgorithm = trustStoreAlgorithm;
    }

    public String getKeyStoreAlgorithm() {
        return keyStoreAlgorithm;
    }

    public void setKeyStoreAlgorithm(String keyStoreAlgorithm) {
        this.keyStoreAlgorithm = keyStoreAlgorithm;
    }

    public String getSecurityProtocol() {
        return securityProtocol;
    }

    public void setSecurityProtocol(String securityProtocol) {
        this.securityProtocol = securityProtocol;
    }

    public SSLContext createSSLContext() {
        SSLContext sslContext = null;

        try {
            TrustManagerFactory trustManagerFactory = null;
            KeyManagerFactory keyManagerFactory = null;

            if (trustStoreFile != null) {
                try {
                    KeyStore trustStore = KeyStore.getInstance(trustStoreType);
                    trustStore.load(new FileInputStream(trustStoreFile),
                            trustStorePass);

                    trustManagerFactory =
                            TrustManagerFactory.getInstance(trustStoreAlgorithm);
                    trustManagerFactory.init(trustStore);
                } catch (KeyStoreException e) {
                    logger.log(Level.FINE, "Error initializing trust store", e);
                } catch (CertificateException e) {
                    logger.log(Level.FINE, "Trust store certificate exception.", e);
                } catch (FileNotFoundException e) {
                    logger.log(Level.FINE, "Can't find trust store file: " + trustStoreFile, e);
                } catch (IOException e) {
                    logger.log(Level.FINE, "Error loading trust store from file: " + trustStoreFile, e);
                }
            }

            if (keyStoreFile != null) {
                try {
                    KeyStore keyStore = KeyStore.getInstance(keyStoreType);
                    keyStore.load(new FileInputStream(keyStoreFile),
                            keyStorePass);

                    keyManagerFactory =
                            KeyManagerFactory.getInstance(keyStoreAlgorithm);
                    keyManagerFactory.init(keyStore, keyStorePass);
                } catch (KeyStoreException e) {
                    logger.log(Level.FINE, "Error initializing key store", e);
                } catch (CertificateException e) {
                    logger.log(Level.FINE, "Key store certificate exception.", e);
                } catch (UnrecoverableKeyException e) {
                    logger.log(Level.FINE, "Key store unrecoverable exception.", e);
                } catch (FileNotFoundException e) {
                    logger.log(Level.FINE, "Can't find key store file: " + keyStoreFile, e);
                } catch (IOException e) {
                    logger.log(Level.FINE, "Error loading key store from file: " + keyStoreFile, e);
                }
            }

            sslContext = SSLContext.getInstance(securityProtocol);
            sslContext.init(keyManagerFactory != null ? keyManagerFactory.getKeyManagers() : null,
                    trustManagerFactory != null ? trustManagerFactory.getTrustManagers() : null,
                    null);
        } catch (KeyManagementException e) {
            logger.log(Level.FINE, "Key management error.", e);
        } catch (NoSuchAlgorithmException e) {
            logger.log(Level.FINE, "Error initializing algorithm.", e);
        }

        return sslContext;
    }

    public void retrieve(Properties props) {
        trustStoreType = System.getProperty(TRUST_STORE_TYPE, "JKS");
        keyStoreType = System.getProperty(KEY_STORE_TYPE, "JKS");

        trustStorePass =
                System.getProperty(TRUST_STORE_PASSWORD, "changeit").toCharArray();

        keyStorePass =
                System.getProperty(KEY_STORE_PASSWORD, "changeit").toCharArray();

        trustStoreFile = System.getProperty(TRUST_STORE_FILE);
        keyStoreFile = System.getProperty(KEY_STORE_FILE);

        trustStoreAlgorithm = "SunX509";
        keyStoreAlgorithm = "SunX509";

        securityProtocol = "TLS";
    }

    public void publish(Properties props) {
        props.setProperty(TRUST_STORE_FILE, trustStoreFile);
        props.setProperty(KEY_STORE_FILE, keyStoreFile);

        props.setProperty(TRUST_STORE_PASSWORD, new String(trustStorePass));
        props.setProperty(KEY_STORE_PASSWORD, new String(keyStorePass));

        props.setProperty(TRUST_STORE_TYPE, trustStoreType);
        props.setProperty(KEY_STORE_TYPE, keyStoreType);
    }
}
