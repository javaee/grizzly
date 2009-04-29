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

import com.sun.grizzly.DefaultProtocolChainInstanceHandler;
import com.sun.grizzly.ProtocolChain;
import com.sun.grizzly.ProtocolFilter;
import com.sun.grizzly.UDPSelectorHandler;
import com.sun.grizzly.arp.AsyncFilter;
import com.sun.grizzly.arp.DefaultAsyncHandler;
import com.sun.grizzly.config.dom.FileCache;
import com.sun.grizzly.config.dom.Http;
import com.sun.grizzly.config.dom.NetworkListener;
import com.sun.grizzly.config.dom.Protocol;
import com.sun.grizzly.config.dom.Ssl;
import com.sun.grizzly.config.dom.ThreadPool;
import com.sun.grizzly.config.dom.Transport;
import com.sun.grizzly.filter.ReadFilter;
import com.sun.grizzly.http.HttpProtocolChain;
import com.sun.grizzly.http.SelectorThread;
import com.sun.grizzly.http.StatsThreadPool;
import com.sun.grizzly.http.portunif.HttpProtocolFinder;
import com.sun.grizzly.portunif.PUPreProcessor;
import com.sun.grizzly.portunif.ProtocolFinder;
import com.sun.grizzly.portunif.ProtocolHandler;
import com.sun.grizzly.tcp.Adapter;
import com.sun.grizzly.util.DefaultThreadPool;
import org.jvnet.hk2.component.Habitat;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Implementation of Grizzly embedded HTTP listener
 *
 * @author Jeanfrancois Arcand
 * @author Alexey Stashok
 * @author Justin Lee
 */
public class GrizzlyEmbeddedHttp extends SelectorThread {
    private volatile ProtocolFilter httpProtocolFilterWrapper;
    private final AtomicBoolean algorithmInitialized = new AtomicBoolean(false);
    private volatile Collection<ProtocolFilter> defaultHttpFilters;
    private boolean isHttpSecured = false;
    private UDPSelectorHandler udpSelectorHandler;
    private static final Object LOCK_OBJECT = new Object();
    public static String DEFAULT_ALGORITHM_CLASS_NAME = DEFAULT_ALGORITHM;
    /**
     * The resource bundle containing the message strings for logger.
     */
    protected static final ResourceBundle _rb = logger.getResourceBundle();
    private String defaultVirtualServer;
    private GrizzlyServiceListener service;

    /**
     * Constructor
     *
     * @param grizzlyServiceListener
     */
    public GrizzlyEmbeddedHttp(GrizzlyServiceListener grizzlyServiceListener) {
        service = grizzlyServiceListener;
        setClassLoader(getClass().getClassLoader());
    }

    /**
     * Load using reflection the <code>Algorithm</code> class.
     */
    @Override
    protected void initAlgorithm() {
        if (!algorithmInitialized.getAndSet(true)) {
            try {
                algorithmClass = Class.forName(DEFAULT_ALGORITHM_CLASS_NAME);
                defaultAlgorithmInstalled = true;
            } catch (ClassNotFoundException e) {
                logger.severe(e.getMessage());
                super.initAlgorithm();
            }
        }
    }

    /**
     * Initialize the Grizzly Framework classes.
     */
    @Override
    protected void initController() {
        super.initController();
        // Re-start problem when set to true as of 04/18.
        //selectorHandler.setReuseAddress(false);
        final DefaultProtocolChainInstanceHandler instanceHandler = new DefaultProtocolChainInstanceHandler() {
            private final ConcurrentLinkedQueue<ProtocolChain> chains =
                    new ConcurrentLinkedQueue<ProtocolChain>();

            /**
             * Always return instance of ProtocolChain.
             */
            @Override
            public ProtocolChain poll() {
                ProtocolChain protocolChain = chains.poll();
                if (protocolChain == null) {
                    protocolChain = new HttpProtocolChain();
                    ((HttpProtocolChain) protocolChain).enableRCM(rcmSupport);
                    configureFilters(protocolChain);
                }
                return protocolChain;
            }

            /**
             * Pool an instance of ProtocolChain.
             */
            @Override
            public boolean offer(ProtocolChain instance) {
                return chains.offer(instance);
            }
        };
        controller.setProtocolChainInstanceHandler(instanceHandler);
        controller.setReadThreadsCount(readThreadsCount);
        // Suport UDP only when port unification is enabled.
        if (portUnificationFilter != null) {
            controller.addSelectorHandler(createUDPSelectorHandler());
        }
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                stopEndpoint();
            }
        });
    }

    @Override
    public void stopEndpoint() {
        try {
            super.stopEndpoint();
        } catch (Throwable t) {
            logger.log(Level.SEVERE, "Unable to stop properly", t);
        } finally {
            // Force the Selector(s) to be closed in case an unexpected
            // exception occured during shutdown.
            try {
                if (selectorHandler != null
                        && selectorHandler.getSelector() != null) {
                    selectorHandler.getSelector().close();
                }
            } catch (IOException ex) {
            }
            try {
                if (udpSelectorHandler != null
                        && udpSelectorHandler.getSelector() != null) {
                    udpSelectorHandler.getSelector().close();
                }
            } catch (IOException ex) {
            }
        }

    }

    protected Collection<ProtocolFilter> getDefaultHttpProtocolFilters() {
        if (defaultHttpFilters == null) {
            synchronized (LOCK_OBJECT) {
                if (defaultHttpFilters == null) {
                    final Collection<ProtocolFilter> tmpList = new ArrayList<ProtocolFilter>(4);
                    if (rcmSupport) {
                        tmpList.add(createRaFilter());
                    }
                    tmpList.add(createHttpParserFilter());
                    defaultHttpFilters = tmpList;
                }
            }
        }
        return defaultHttpFilters;
    }

    /**
     * Create ReadFilter
     *
     * @return read filter
     */
    protected ProtocolFilter createReadFilter() {
        final ReadFilter readFilter = new ReadFilter();
        readFilter.setContinuousExecution(
                Boolean.valueOf(System.getProperty("v3.grizzly.readFilter.continuousExecution", "false")));
        return readFilter;
    }

    /**
     * Create the HttpProtocolFilter, which is aware of EmbeddedHttp's context-root<->adapter map.
     */
    protected HttpProtocolFilter getHttpProtocolFilter() {
        synchronized (LOCK_OBJECT) {
            if (httpProtocolFilterWrapper == null) {
                initAlgorithm();
                final ProtocolFilter wrappedFilter = createHttpParserFilter();
                httpProtocolFilterWrapper = new HttpProtocolFilter(wrappedFilter, this);
            }
        }
        return (HttpProtocolFilter) httpProtocolFilterWrapper;
    }

    /**
     * Create <code>TCPSelectorHandler</code>
     */
    protected UDPSelectorHandler createUDPSelectorHandler() {
        if (udpSelectorHandler == null) {
            udpSelectorHandler = new UDPSelectorHandler();
            udpSelectorHandler.setPort(port);
            udpSelectorHandler.setThreadPool(threadPool);
        }
        return udpSelectorHandler;
    }

    /**
     * Configure <code>TCPSelectorHandler</code>
     */
    protected void configureSelectorHandler(UDPSelectorHandler selectorHandler) {
        selectorHandler.setPort(port);
        selectorHandler.setReuseAddress(getReuseAddress());
        selectorHandler.setThreadPool(threadPool);
    }
    // ---------------------------------------------- Public get/set ----- //

    public boolean isHttpSecured() {
        return isHttpSecured;
    }

    public void setHttpSecured(boolean httpSecured) {
        isHttpSecured = httpSecured;
    }

    public void configure(boolean isWebProfile, NetworkListener networkListener, Habitat habitat) {
        final Protocol httpProtocol = networkListener.findProtocol();
        final Http http = httpProtocol.getHttp();
        final Transport transport = networkListener.findTransport();
        final ThreadPool pool = networkListener.findThreadPool();

        setPort(Integer.parseInt(networkListener.getPort()));
        try {
            setAddress(InetAddress.getByName(networkListener.getAddress()));
        } catch (UnknownHostException e) {
            logger.log(Level.WARNING, "Invalid address for {0}: {1}",
                    new Object[]{
                            networkListener.getName(),
                            networkListener.getAddress()
                    });
        }
        configureHttpListenerProperty(http, transport, httpProtocol.getSsl());
        configureKeepAlive(http);
        configureHttpProtocol(http);
        configureThreadPool(http, pool);
        configureFileCache(http.getFileCache());
        defaultVirtualServer = http.getDefaultVirtualServer();
        // acceptor-threads
        final String acceptorThreads = transport.getAcceptorThreads();
        try {
            final int readController = Integer.parseInt(acceptorThreads) - 1;
            if (readController > 0) {
                setSelectorReadThreadsCount(readController);
            }
        } catch (NumberFormatException nfe) {
            logger.log(Level.WARNING, "pewebcontainer.invalid_acceptor_threads",
                    new Object[]{
                            acceptorThreads,
                            transport.getName()
                    });
        }
        final Boolean enableComet = toBoolean(http.getEnableCometSupport());
        if (enableComet && !"admin-listener".equalsIgnoreCase(networkListener.getName())) {
            configureComet(habitat);
        }
        if (!isWebProfile) {
            configurePortUnification();
        }
    }

    protected void configurePortUnification() {
        // [1] Detect TLS requests.
        // If sslContext is null, that means TLS is not enabled on that port.
        // We need to revisit the way GlassFish is configured and make
        // sure TLS is always enabled. We can always do what we did for
        // GlassFish v2, which is to located the keystore/trustore by ourself.
        // TODO: Enable TLS support on all ports using com.sun.Grizzly.SSLConfig
        // [2] Add our supported ProtocolFinder. By default, we support http/sip
        // TODO: The list of ProtocolFinder is retrieved using System.getProperties().
        final List<ProtocolFinder> protocolFinders = new ArrayList<ProtocolFinder>();
        protocolFinders.add(new HttpProtocolFinder());
        // [3] Add our supported ProtocolHandler. By default we support http/sip.
        final List<ProtocolHandler> protocolHandlers = new ArrayList<ProtocolHandler>();
        protocolHandlers.add(new WebProtocolHandler(getWebProtocolHandlerMode(), this));
        configurePortUnification(protocolFinders, protocolHandlers, getPuPreProcessors());
    }

    List<PUPreProcessor> getPuPreProcessors() {
        return new ArrayList<PUPreProcessor>();
    }

    WebProtocolHandler.Mode getWebProtocolHandlerMode() {
        return WebProtocolHandler.Mode.HTTP;
    }


    /**
     * Enable Comet/Poll request support.
     *
     * @param habitat
     */
    private final void configureComet(Habitat habitat) {
        final AsyncFilter cometFilter = habitat.getComponent(AsyncFilter.class, "comet");
        if (cometFilter != null) {
            setEnableAsyncExecution(true);
            asyncHandler = new DefaultAsyncHandler();
            asyncHandler.addAsyncFilter(cometFilter);
            setAsyncHandler(asyncHandler);
        }
    }

    /**
     * Configure the Grizzly FileCache mechanism
     */
    private void configureFileCache(FileCache cache) {
        if (cache == null) {
            return;
        }
        final boolean enabled = toBoolean(cache.getEnabled());
        setFileCacheIsEnabled(enabled);
        setLargeFileCacheEnabled(enabled);
        setSecondsMaxAge(Integer.parseInt(cache.getMaxAgeInSeconds()));
        setMaxCacheEntries(Integer.parseInt(cache.getMaxFilesCount()));
        setMaxLargeCacheSize(Integer.parseInt(cache.getMaxCacheSizeInBytes()));
    }

    private void configureHttpListenerProperty(Http http, Transport transport, Ssl ssl)
            throws NumberFormatException {
        if (transport.getBufferSizeInBytes() != null) {
            setBufferSize(Integer.parseInt(transport.getBufferSizeInBytes()));
        }
        try {
            setAdapter((Adapter) Class.forName(http.getAdapter()).newInstance());
        } catch (Exception e) {
            throw new GrizzlyConfigException(e.getMessage(), e);
        }
        if (http.getMaxConnections() != null) {
            setMaxKeepAliveRequests(Integer.parseInt(http.getMaxConnections()));
        }
        if (http.getEnableAuthPassThrough() != null) {
            setProperty("authPassthroughEnabled", toBoolean(http.getEnableAuthPassThrough()));
        }
        if (http.getMaxPostSizeInBytes() != null) {
            setMaxPostSize(Integer.parseInt(http.getMaxPostSizeInBytes()));
        }
        if (http.getCompression() != null) {
            setCompression(http.getCompression());
        }
        if (http.getCompressableMimeType() != null) {
            setCompressableMimeTypes(http.getCompressableMimeType());
        }
        if (http.getNoCompressionUserAgents() != null) {
            setNoCompressionUserAgents(http.getNoCompressionUserAgents());
        }
        if (http.getCompressionMinSizeInBytes() != null) {
            setCompressionMinSize(Integer.parseInt(http.getCompressionMinSizeInBytes()));
        }
        if (http.getRestrictedUserAgents() != null) {
            setRestrictedUserAgents(http.getRestrictedUserAgents());
        }
        if (http.getEnableRcmSupport() != null) {
            enableRcmSupport(toBoolean(http.getEnableRcmSupport()));
        }
        if (http.getConnectionUploadTimeoutInMillis() != null) {
            setUploadTimeout(Integer.parseInt(http.getConnectionUploadTimeoutInMillis()));
        }
        if (http.getDisableUploadTimeout() != null) {
            setDisableUploadTimeout(toBoolean(http.getDisableUploadTimeout()));
        }
        if (http.getChunkingDisabled() != null) {
            setProperty("chunking-disabled", toBoolean(http.getChunkingDisabled()));
        }
        configSslOptions(ssl);
        if (http.getUriEncoding() != null) {
            setProperty("uriEncoding", http.getUriEncoding());
        }
//        if ("jkEnabled".equals(propName)) {
//            setProperty(propName, propValue);
//        }
        if (transport.getTcpNoDelay() != null) {
            setTcpNoDelay(toBoolean(transport.getTcpNoDelay()));
        }
        if (http.getTraceEnabled() != null) {
            setProperty("traceEnabled", toBoolean(http.getTraceEnabled()));
        }
    }

    private void configSslOptions(Ssl ssl) {
        if (ssl != null) {
            if (ssl.getCrlFile() != null) {
                setProperty("crlFile", ssl.getCrlFile());
            }
            if (ssl.getTrustAlgorithm() != null) {
                setProperty("trustAlgorithm", ssl.getTrustAlgorithm());
            }
            if (ssl.getTrustMaxCertLengthInBytes() != null) {
                setProperty("trustMaxCertLength", ssl.getTrustMaxCertLengthInBytes());
            }
        }
    }

    /**
     * Configures the given HTTP grizzlyListener with the given http-protocol config.
     */
    private void configureHttpProtocol(Http http) {
        if (http == null) {
            return;
        }
        setForcedRequestType(http.getForcedResponseType());
        setDefaultResponseType(http.getDefaultResponseType());
    }

    /**
     * Configures the keep-alive properties on the given Connector from the given keep-alive config.
     */
    private void configureKeepAlive(Http http) {
        int timeoutInSeconds = 60;
        int maxConnections = 256;
        if (http != null) {
            try {
                timeoutInSeconds = Integer.parseInt(http.getTimeoutInSeconds());
            } catch (NumberFormatException ex) {
                String msg = _rb.getString("pewebcontainer.invalidKeepAliveTimeout");
                msg = MessageFormat.format(msg, http.getTimeoutInSeconds(), Integer.toString(timeoutInSeconds));
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
        setKeepAliveTimeoutInSeconds(timeoutInSeconds);
        setMaxKeepAliveRequests(maxConnections);
    }

    /**
     * Configures an HTTP grizzlyListener with the given request-processing config.
     */
    private void configureThreadPool(Http http, ThreadPool threadPool) {
        if (threadPool == null) {
            return;
        }
        try {
            final int maxQueueSize = threadPool.getMaxQueueSize() != null ? Integer.MAX_VALUE
                    : Integer.parseInt(threadPool.getMaxQueueSize());
            final int minThreads = Integer.parseInt(threadPool.getMinThreadPoolSize());
            final int maxThreads = Integer.parseInt(threadPool.getMaxThreadPoolSize());
            final int keepAlive = Integer.parseInt(http.getTimeoutInSeconds());

            final DefaultThreadPool pool = new StatsThreadPool(minThreads, maxThreads, maxQueueSize,
                    keepAlive, TimeUnit.SECONDS) {
            };

            setThreadPool(pool);
            setMaxHttpHeaderSize(Integer.parseInt(http.getHeaderBufferLengthInBytes()));

        } catch (NumberFormatException ex) {
            logger.log(Level.WARNING, " Invalid request-processing attribute", ex);
        }
    }

    private boolean toBoolean(String value) {
        final String v = null != value ? value.trim() : value;
        return "true".equals(v)
                || "yes".equals(v)
                || "on".equals(v)
                || "1".equals(v);
    }

    public String getDefaultVirtualServer() {
        return defaultVirtualServer;
    }
}
