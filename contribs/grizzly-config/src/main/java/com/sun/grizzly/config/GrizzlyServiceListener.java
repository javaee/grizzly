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

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.ssl.SSLContext;

import com.sun.grizzly.Controller;
import com.sun.grizzly.arp.AsyncFilter;
import com.sun.grizzly.arp.AsyncHandler;
import com.sun.grizzly.arp.DefaultAsyncHandler;
import com.sun.grizzly.config.dom.FileCache;
import com.sun.grizzly.config.dom.Http;
import com.sun.grizzly.config.dom.NetworkConfig;
import com.sun.grizzly.config.dom.NetworkListener;
import com.sun.grizzly.config.dom.NetworkListeners;
import com.sun.grizzly.config.dom.Property;
import com.sun.grizzly.config.dom.Protocol;
import com.sun.grizzly.config.dom.Ssl;
import com.sun.grizzly.config.dom.ThreadPool;
import com.sun.grizzly.config.dom.Transport;
import com.sun.grizzly.http.portunif.HttpProtocolFinder;
import com.sun.grizzly.portunif.PUPreProcessor;
import com.sun.grizzly.portunif.ProtocolFinder;
import com.sun.grizzly.portunif.ProtocolHandler;
import com.sun.grizzly.portunif.TLSPUPreProcessor;
import org.jvnet.hk2.component.Habitat;

/**
 * <p>The GrizzlyServiceListener is responsible of mapping incoming requests to the proper Container or Grizzly
 * extensions. Registered Containers can be notified by Grizzly using three mode:</p> <ul><li>At the transport level:
 * Containers can be notified when TCP, TLS or UDP requests are mapped to them.</li> <li>At the protocol level:
 * Containers can be notified when protocols (ex: SIP, HTTP) requests are mapped to them.</li> </li>At the requests
 * level: Containers can be notified when specific patterns requests are mapped to them.</li><ul>
 *
 * @author Jeanfrancois Arcand
 */
public class GrizzlyServiceListener {
    /**
     * The logger to use for logging messages.
     */
    protected static final Logger logger = Logger.getLogger(GrizzlyServiceListener.class.getName());
    /**
     * The resource bundle containing the message strings for logger.
     */
    protected static final ResourceBundle _rb = logger.getResourceBundle();
    private Controller controller;
    private boolean isEmbeddedHttpSecured;
    private GrizzlyEmbeddedHttp embeddedHttp;
    private String name;
    private Http http;

    public GrizzlyServiceListener(final Controller cont) {
        controller = cont;
    }

    /*
    * Configures the given grizzlyListener.
    *
    * @param grizzlyListener The grizzlyListener to configure
    * @param httpProtocol The Protocol that corresponds to the given grizzlyListener
    * @param isSecure true if the grizzlyListener is security-enabled, false otherwise
    * @param httpServiceProps The httpProtocol-service properties
    * @param isWebProfile if true - just HTTP protocol is supported on port,
    *        false - port unification will be activated
    */
    // TODO: Must get the information from domain.xml Config objects.
    // TODO: Pending Grizzly issue 54
    public void configure(final NetworkConfig networkConfig, final NetworkListener networkListener,
        final boolean isWebProfile, final Habitat habitat,
        final GrizzlyMappingAdapter adapter) {
        if (System.getProperty("product.name") == null) {
            System.setProperty("product.name", "Grizzly");
        }
        //TODO: Configure via domain.xml
        //grizzlyListener.setController(controller);
        // TODO: This is not the right way to do.
        GrizzlyEmbeddedHttp.setWebAppRootPath(System.getProperty("com.sun.aas.instanceRoot") + "/docroot");
        final Protocol httpProtocol = findProtocol(networkConfig, networkListener.getProtocol());
        http = httpProtocol.getHttp();
        final Transport transport = findTransport(networkConfig, networkListener.getTransport());
        final ThreadPool threadPool = findThreadpool(networkConfig.getNetworkListeners(),
            networkListener.getThreadPool());
        final boolean isSecure = Boolean.parseBoolean(httpProtocol.getSecurityEnabled());
        //TODO: Use the grizzly-config name.
        initializeEmbeddedHttp(isSecure, adapter, networkListener);
        setName("grizzlyServiceListener-" + getPort());
        if (isSecure) {
            configureSSL(httpProtocol);
        }
        GrizzlyEmbeddedHttp.setLogger(logger);
        configureNetworkingProperties(http, transport, httpProtocol.getSsl());
        configureKeepAlive(http);
        configureHttpProtocol(http);
        configureThreadPool(threadPool, http);
        configureFileCache(http.getFileCache());
        // acceptor-threads
        final String acceptorThreads = transport.getAcceptorThreads();
        try {
            final int readController = Integer.parseInt(acceptorThreads) - 1;
            if (readController > 0) {
                embeddedHttp.setSelectorReadThreadsCount(readController);
            }
        } catch (NumberFormatException nfe) {
            logger.log(Level.WARNING, "pewebcontainer.invalid_acceptor_threads",
                new Object[]{
                    acceptorThreads,
                    httpProtocol.getName(),
                    Integer.toString(embeddedHttp.getMaxThreads())
                });
        }
        final Boolean enableComet = Boolean.valueOf(System.getProperty("v3.grizzly.cometSupport", "false"));
        if (enableComet && !"admin-httpProtocol".equalsIgnoreCase(httpProtocol.getName())) {
            configureComet(habitat);
        }
        if (!isWebProfile) {
            configurePortUnification();
        }
    }

    private void initializeEmbeddedHttp(final boolean isSecured, final GrizzlyMappingAdapter adapter,
        final NetworkListener networkListener) {
        isEmbeddedHttpSecured = isSecured;
        if (isSecured) {
            embeddedHttp = new GrizzlyEmbeddedHttps(adapter);
        } else {
            embeddedHttp = new GrizzlyEmbeddedHttp(adapter);
        }
        readAddressAndPort(networkListener);
    }

    private void readAddressAndPort(final NetworkListener networkListener) {
        final String portValue = networkListener.getPort();
        if (portValue == null) {
            logger.severe("Cannot find port information from configuration");
            throw new RuntimeException("Cannot find port information from configuration");
        }
        try {
            setPort(Integer.parseInt(portValue));
        } catch (NumberFormatException e) {
            logger.severe("Cannot parse port value : " + portValue + ", using port 8080");
            setPort(8080);
        }
        try {
            setAddress(InetAddress.getByName(networkListener.getAddress()));
        } catch (UnknownHostException ex) {
            logger.log(Level.SEVERE, "Unknown address " + networkListener.getAddress(), ex);
        }
    }

    public void configurePortUnification() {
        // [1] Detect TLS requests.
        // If sslContext is null, that means TLS is not enabled on that port.
        // We need to revisit the way GlassFish is configured and make
        // sure TLS is always enabled. We can always do what we did for
        // GlassFish v2, which is to located the keystore/trustore by ourself.
        // TODO: Enable TLS support on all ports using com.sun.Grizzly.SSLConfig
        final ArrayList<PUPreProcessor> puPreProcessors = new ArrayList<PUPreProcessor>();
        final WebProtocolHandler.Mode webProtocolHandlerMode;
        if (isEmbeddedHttpSecured) {
            final SSLContext sslContext = ((GrizzlyEmbeddedHttps) embeddedHttp).getSSLContext();
            final PUPreProcessor preProcessor = new TLSPUPreProcessor(sslContext);
            puPreProcessors.add(preProcessor);
            webProtocolHandlerMode = WebProtocolHandler.Mode.HTTPS;
        } else {
            webProtocolHandlerMode = WebProtocolHandler.Mode.HTTP;
        }
        // [2] Add our supported ProtocolFinder. By default, we support http/sip
        // TODO: The list of ProtocolFinder is retrieved using System.getProperties().
        final ArrayList<ProtocolFinder> protocolFinders = new ArrayList<ProtocolFinder>();
        protocolFinders.add(new HttpProtocolFinder());
        // [3] Add our supported ProtocolHandler. By default we support http/sip.
        final ArrayList<ProtocolHandler> protocolHandlers = new ArrayList<ProtocolHandler>();
        protocolHandlers.add(new WebProtocolHandler(webProtocolHandlerMode, embeddedHttp));
        embeddedHttp.configurePortUnification(protocolFinders, protocolHandlers, puPreProcessors);
    }

    public void start() throws IOException, InstantiationException {
        embeddedHttp.initEndpoint();
        embeddedHttp.startEndpoint();
    }

    public void stop() {
        embeddedHttp.stopEndpoint();
    }

    public void setAddress(final InetAddress address) {
        if (embeddedHttp != null) {
            embeddedHttp.setAddress(address);
        }
    }

    public Controller getController() {
        return controller;
    }

    public String getDefaultVirtualServer() {
        return http.getDefaultVirtualServer();
    }

    public GrizzlyEmbeddedHttp getEmbeddedHttp() {
        return embeddedHttp;
    }

    public boolean isEmbeddedHttpSecured() {
        return isEmbeddedHttpSecured;
    }

    public String getName() {
        return name;
    }

    public void setName(final String name) {
        this.name = name;
    }

    public int getPort() {
        return embeddedHttp.getPort();
    }

    public void setPort(final int port) {
        if (embeddedHttp != null) {
            embeddedHttp.setPort(port);
        }
    }

    private Protocol findProtocol(final NetworkConfig networkConfig, final String name) {
        for (final Protocol protocol : networkConfig.getProtocols().getProtocol()) {
            if (protocol.getName().equals(name)) {
                return protocol;
            }
        }
        return null;
    }

    private ThreadPool findThreadpool(final NetworkListeners networkConfig, final String name) {
        for (final ThreadPool threadPool : networkConfig.getThreadPool()) {
            if (threadPool.getThreadPoolId().equals(name)) {
                return threadPool;
            }
        }
        return null;
    }

    private Transport findTransport(final NetworkConfig networkConfig, final String name) {
        for (final Transport transport : networkConfig.getTransports().getTransport()) {
            if (transport.getName().equals(name)) {
                return transport;
            }
        }
        return null;
    }

    /**
     * Enable Comet/Poll request support.
     * @param habitat
     */
    private final void configureComet(final Habitat habitat) {
        final AsyncFilter cometFilter = habitat.getComponent(AsyncFilter.class, "comet");
        if (cometFilter != null) {
            embeddedHttp.setEnableAsyncExecution(true);
            final AsyncHandler asyncHandler = new DefaultAsyncHandler();
            asyncHandler.addAsyncFilter(cometFilter);
            embeddedHttp.setAsyncHandler(asyncHandler);
        }
    }

    /**
     * Configure the Grizzly FileCache mechanism
     */
    private void configureFileCache(final FileCache cache) {
        if (cache == null) {
            return;
        }
        final boolean enabled = toBoolean(cache.getEnabled());
        embeddedHttp.setFileCacheIsEnabled(enabled);
        embeddedHttp.setLargeFileCacheEnabled(enabled);
        embeddedHttp.setSecondsMaxAge(Integer.parseInt(cache.getMaxAge()));
        embeddedHttp.setMaxCacheEntries(Integer.parseInt(cache.getMaxFilesCount()));
        embeddedHttp.setMaxLargeCacheSize(Integer.parseInt(cache.getMaxCacheSize()));
    }

    private void configureHttpListenerProperty(final Http http, final Transport transport, final Ssl ssl)
        throws NumberFormatException {
        if (transport.getBufferSize() != null) {
            embeddedHttp.setBufferSize(Integer.parseInt(transport.getBufferSize()));
        }
        if (transport.getUseNioDirectByteBuffer() != null) {
            embeddedHttp.setUseByteBufferView(toBoolean(transport.getUseNioDirectByteBuffer()));
        }
        if (http.getMaxConnections() != null) {
            embeddedHttp.setMaxKeepAliveRequests(Integer.parseInt(http.getMaxConnections()));
        }
        if (http.getEnableAuthPassThrough() != null) {
            embeddedHttp.setProperty("authPassthroughEnabled", toBoolean(http.getEnableAuthPassThrough()));
        }
        if (http.getMaxPostSize() != null) {
            embeddedHttp.setMaxPostSize(Integer.parseInt(http.getMaxPostSize()));
        }
        if (http.getCompression() != null) {
            embeddedHttp.setCompression(http.getCompression());
        }
        if (http.getCompressableMimeType() != null) {
            embeddedHttp.setCompressableMimeTypes(http.getCompressableMimeType());
        }
        if (http.getNoCompressionUserAgents() != null) {
            embeddedHttp.setNoCompressionUserAgents(http.getNoCompressionUserAgents());
        }
        if (http.getCompressionMinSize() != null) {
            embeddedHttp.setCompressionMinSize(Integer.parseInt(http.getCompressionMinSize()));
        }
        if (http.getRestrictedUserAgents() != null) {
            embeddedHttp.setRestrictedUserAgents(http.getRestrictedUserAgents());
        }
        if (http.getEnableRcmSupport() != null) {
            embeddedHttp.enableRcmSupport(toBoolean(http.getEnableRcmSupport()));
        }
        if (http.getConnectionUploadTimeout() != null) {
            embeddedHttp.setUploadTimeout(Integer.parseInt(http.getConnectionUploadTimeout()));
        }
        if (http.getDisableUploadTimeout() != null) {
            embeddedHttp.setDisableUploadTimeout(toBoolean(http.getDisableUploadTimeout()));
        }
        if (http.getChunkingDisabled() != null) {
            embeddedHttp.setProperty("chunking-disabled", toBoolean(http.getChunkingDisabled()));
        }
        if (ssl.getCrlFile() != null) {
            embeddedHttp.setProperty("crlFile", ssl.getCrlFile());
        }
        if (ssl.getTrustAlgorithm() != null) {
            embeddedHttp.setProperty("trustAlgorithm", ssl.getTrustAlgorithm());
        }
        if (ssl.getTrustMaxCertLength() != null) {
            embeddedHttp.setProperty("trustMaxCertLength", ssl.getTrustMaxCertLength());
        }
        if (http.getUriEncoding() != null) {
            embeddedHttp.setProperty("uriEncoding", http.getUriEncoding());
        }
//        if ("jkEnabled".equals(propName)) {
//            embeddedHttp.setProperty(propName, propValue);
//        }
        if (transport.getTcpNoDelay() != null) {
            embeddedHttp.setTcpNoDelay(toBoolean(transport.getTcpNoDelay()));
        }
        if (http.getTraceEnabled() != null) {
            embeddedHttp.setProperty("traceEnabled", toBoolean(http.getTraceEnabled()));
        }
    }

    /**
     * Configures the given HTTP grizzlyListener with the given http-protocol config.
     *
     * @param http http-protocol config to use
     */
    private void configureHttpProtocol(final Http http) {
        if (http == null) {
            return;
        }
        embeddedHttp.setForcedRequestType(http.getForcedResponseType());
        embeddedHttp.setDefaultResponseType(http.getDefaultResponseType());
    }

    /**
     * Configures the keep-alive properties on the given Connector from the given keep-alive config.
     *
     * @param http config to use
     */
    private void configureKeepAlive(final Http http) {
        int timeoutInSeconds = 60;
        int maxConnections = 256;
        if (http != null) {
            try {
                timeoutInSeconds = Integer.parseInt(http.getTimeout());
            } catch (NumberFormatException ex) {
                String msg = _rb.getString("pewebcontainer.invalidKeepAliveTimeout");
                msg = MessageFormat.format(msg, http.getTimeout(), Integer.toString(timeoutInSeconds));
                logger.log(Level.WARNING, msg, ex);
            }
            try {
                maxConnections = Integer.parseInt(http.getMaxConnections());
            } catch (NumberFormatException ex) {
                String msg = _rb.getString("pewebcontainer.invalidKeepAliveMaxConnections");
                msg = MessageFormat.format(msg, http.getMaxConnections(), Integer.toString(maxConnections));
                logger.log(Level.WARNING, msg, ex);
            }
        }
        embeddedHttp.setKeepAliveTimeoutInSeconds(timeoutInSeconds);
        embeddedHttp.setMaxKeepAliveRequests(maxConnections);
    }

    /**
     * Configure http-service properties.
     */
    private void configureNetworkingProperties(final Http http, final Transport transport,
        final Ssl ssl) {
        final List<Property> props = http.getProperty();
        configureHttpListenerProperty(http, transport, ssl);
        if (props != null) {
            for (final Property property : props) {
                if (logger.isLoggable(Level.WARNING)) {
                    logger.log(Level.WARNING, "pewebcontainer.invalid_property", property.getName());
                }
            }
        }
    }

    /**
     * Configures the SSL properties on the given PECoyoteConnector from the SSL config of the given HTTP listener.
     *
     * @param http HTTP listener whose SSL config to use
     */
    private boolean configureSSL(final Protocol http) {
        final Ssl sslConfig = http.getSsl();
        final GrizzlyEmbeddedHttps grizzlyEmbeddedHttps = (GrizzlyEmbeddedHttps) embeddedHttp;
        final List<String> tmpSSLArtifactsList = new LinkedList<String>();
        if (sslConfig != null) {
            // client-auth
            if (Boolean.parseBoolean(sslConfig.getClientAuthEnabled())) {
                grizzlyEmbeddedHttps.setNeedClientAuth(true);
            }
            // ssl protocol variants
            if (Boolean.parseBoolean(sslConfig.getSsl2Enabled())) {
                tmpSSLArtifactsList.add("SSLv2");
            }
            if (Boolean.parseBoolean(sslConfig.getSsl3Enabled())) {
                tmpSSLArtifactsList.add("SSLv3");
            }
            if (Boolean.parseBoolean(sslConfig.getTlsEnabled())) {
                tmpSSLArtifactsList.add("TLSv1");
            }
            if (Boolean.parseBoolean(sslConfig.getSsl3Enabled()) ||
                Boolean.parseBoolean(sslConfig.getTlsEnabled())) {
                tmpSSLArtifactsList.add("SSLv2Hello");
            }
        }
        if (tmpSSLArtifactsList.isEmpty()) {
            logger.log(Level.WARNING, "pewebcontainer.all_ssl_protocols_disabled", http.getName());
        } else {
            final String[] enabledProtocols = new String[tmpSSLArtifactsList.size()];
            tmpSSLArtifactsList.toArray(enabledProtocols);
            grizzlyEmbeddedHttps.setEnabledProtocols(enabledProtocols);
        }
        tmpSSLArtifactsList.clear();
        if (sslConfig != null) {
            // cert-nickname
            final String certNickname = sslConfig.getCertNickname();
            if (certNickname != null && certNickname.length() > 0) {
                grizzlyEmbeddedHttps.setCertNickname(certNickname);
            }
            // ssl3-tls-ciphers
            final String ssl3Ciphers = sslConfig.getSsl3TlsCiphers();
            if (ssl3Ciphers != null && ssl3Ciphers.length() > 0) {
                final String[] ssl3CiphersArray = ssl3Ciphers.split(",");
                for (final String cipher : ssl3CiphersArray) {
                    tmpSSLArtifactsList.add(cipher.trim());
                }
            }
            // ssl2-tls-ciphers
            final String ssl2Ciphers = sslConfig.getSsl2Ciphers();
            if (ssl2Ciphers != null && ssl2Ciphers.length() > 0) {
                final String[] ssl2CiphersArray = ssl2Ciphers.split(",");
                for (final String cipher : ssl2CiphersArray) {
                    tmpSSLArtifactsList.add(cipher.trim());
                }
            }
        }
        if (tmpSSLArtifactsList.isEmpty()) {
            logger.log(Level.WARNING, "pewebcontainer.all_ssl_ciphers_disabled", http.getName());
        } else {
            final String[] enabledCiphers = new String[tmpSSLArtifactsList.size()];
            tmpSSLArtifactsList.toArray(enabledCiphers);
            grizzlyEmbeddedHttps.setEnabledCipherSuites(enabledCiphers);
        }
        try {
            grizzlyEmbeddedHttps.initializeSSL();
            return true;
        } catch (Exception e) {
            logger.log(Level.WARNING, "SSL support could not be configured!", e);
        }
        return false;
    }

    /**
     * Configures an HTTP grizzlyListener with the given request-processing config.
     */
    private void configureThreadPool(final ThreadPool threadPool, final Http http) {
        if (threadPool == null) {
            return;
        }
        try {
            final int maxQueueSize = threadPool.getMaxQueueSize() != null ? Integer.MAX_VALUE
                : Integer.parseInt(threadPool.getMaxQueueSize());
            final int minThreads = Integer.parseInt(threadPool.getMinThreadPoolSize());
            final int maxThreads = Integer.parseInt(threadPool.getMaxThreadPoolSize());
            embeddedHttp.setThreadPool(new ThreadPoolExecutor(minThreads, maxThreads, maxQueueSize,
                TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>(maxQueueSize)));
            embeddedHttp.setMaxHttpHeaderSize(Integer.parseInt(http.getHeaderBufferLength()));

        } catch (NumberFormatException ex) {
            logger.log(Level.WARNING, " Invalid request-processing attribute", ex);
        }
    }

    private boolean toBoolean(final String value) {
        final String v = null != value ? value.trim() : value;
        return "true".equals(v)
            || "yes".equals(v)
            || "on".equals(v)
            || "1".equals(v);
    }
}