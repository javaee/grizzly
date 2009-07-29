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

import com.sun.grizzly.DefaultProtocolChain;
import com.sun.grizzly.DefaultProtocolChainInstanceHandler;
import com.sun.grizzly.ProtocolChain;
import com.sun.grizzly.ProtocolChainInstanceHandler;
import com.sun.grizzly.ProtocolFilter;
import com.sun.grizzly.UDPSelectorHandler;
import com.sun.grizzly.arp.AsyncFilter;
import com.sun.grizzly.arp.DefaultAsyncHandler;
import com.sun.grizzly.config.dom.FileCache;
import com.sun.grizzly.config.dom.Http;
import com.sun.grizzly.config.dom.NetworkListener;
import com.sun.grizzly.config.dom.PortUnification;
import com.sun.grizzly.config.dom.Protocol;
import com.sun.grizzly.config.dom.ThreadPool;
import com.sun.grizzly.config.dom.Transport;
import com.sun.grizzly.filter.ReadFilter;
import com.sun.grizzly.http.HttpProtocolChain;
import com.sun.grizzly.http.SelectorThread;
import com.sun.grizzly.http.StatsThreadPool;
import com.sun.grizzly.portunif.CustomFilterChainProtocolHandler;
import com.sun.grizzly.portunif.PUPreProcessor;
import com.sun.grizzly.portunif.PUReadFilter;
import com.sun.grizzly.portunif.ProtocolFinder;
import com.sun.grizzly.portunif.ProtocolHandler;
import com.sun.grizzly.tcp.Adapter;
import com.sun.grizzly.util.DefaultThreadPool;
import com.sun.grizzly.util.WorkerThread;
import java.nio.ByteBuffer;
import org.jvnet.hk2.component.Habitat;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.lang.management.ManagementFactory;

/**
 * Implementation of Grizzly embedded HTTP listener
 *
 * @author Jeanfrancois Arcand
 * @author Alexey Stashok
 * @author Justin Lee
 */
public class GrizzlyEmbeddedHttp extends SelectorThread {

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

    private int threadPoolTimeoutSeconds = 0;

    // Port unification settings
    protected String puFilterClassname;
    protected final List<ProtocolFinder> finders = new ArrayList<ProtocolFinder>();
    protected final List<ProtocolHandler> handlers = new ArrayList<ProtocolHandler>();
    protected final List<PUPreProcessor> preprocessors = new ArrayList<PUPreProcessor>();

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
                if (selectorHandler != null && selectorHandler.getSelector() != null) {
                    selectorHandler.getSelector().close();
                }
            } catch (IOException ex) {
            }
            try {
                if (udpSelectorHandler != null && udpSelectorHandler.getSelector() != null) {
                    udpSelectorHandler.getSelector().close();
                }
            } catch (IOException ex) {
            }
        }

    }

    @Override
    protected void configureProtocolChain() {
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

    public void configure(NetworkListener networkListener, Habitat habitat) {
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

        configureTransport(transport);

        final Protocol protocol = networkListener.findProtocol();

        final boolean mayEnableComet = !"admin-listener".equalsIgnoreCase(networkListener.getName());
        configureProtocol(networkListener, protocol, habitat, mayEnableComet);
        
        configureThreadPool(pool, getThreadPoolTimeoutSeconds());
    }

    protected void configureTransport(Transport transport) {
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

        // transport settings
        setBufferSize(Integer.parseInt(transport.getBufferSizeBytes()));
        setSsBackLog(Integer.parseInt(transport.getMaxConnectionsCount()));
        setDisplayConfiguration(toBoolean(transport.getDisplayConfiguration()));
        if (transport.getTcpNoDelay() != null) {
            setTcpNoDelay(toBoolean(transport.getTcpNoDelay()));
        }
    }

    protected ProtocolChainInstanceHandler configureProtocol(NetworkListener networkListener, Protocol protocol, Habitat habitat,
            boolean mayEnableComet) {
        if (protocol.getHttp() != null) {
            // Only HTTP protocol defined
            final Http http = protocol.getHttp();

            configureHttpListenerProperty(http);
            configureKeepAlive(http);
            configureHttpProtocol(http);
            configureFileCache(http.getFileCache());
            defaultVirtualServer = http.getDefaultVirtualServer();
            // acceptor-threads

            if (mayEnableComet && toBoolean(http.getEnableCometSupport())) {
                configureComet(habitat);
            }

            threadPoolTimeoutSeconds = Integer.parseInt(http.getTimeoutSeconds());
        } else if (protocol.getPortUnification() != null) {
            // Port unification

            PortUnification pu = protocol.getPortUnification();
            puFilterClassname = pu.getClassname();
            List<com.sun.grizzly.config.dom.ProtocolFinder> findersConfig = pu.getProtocolFinder();
            for (com.sun.grizzly.config.dom.ProtocolFinder finderConfig : findersConfig) {
                String finderClassname = finderConfig.getClassname();
                try {
                    ProtocolFinder protocolFinder = (ProtocolFinder) newInstance(finderClassname);
                    Protocol subProtocol = finderConfig.findProtocol();
                    
                    ProtocolChainInstanceHandler protocolChain =
                            configureProtocol(networkListener, subProtocol,
                            habitat, mayEnableComet);

                    finders.add(protocolFinder);
                    final String[] protocols = new String[] {subProtocol.getName()};
                    if (protocolChain != null) {
                        handlers.add(new CustomFilterChainProtocolHandler(protocolChain) {

                            public String[] getProtocols() {
                                return protocols;
                            }

                            public ByteBuffer getByteBuffer() {
                                final WorkerThread workerThread = (WorkerThread) Thread.currentThread();
                                if (workerThread.getSSLEngine() != null) {
                                    return workerThread.getInputBB();
                                }
                                return null;
                            }
                        });
                    } else {
                        handlers.add(new WebProtocolHandler(isHttpSecured() ?
                            WebProtocolHandler.Mode.HTTPS :
                            WebProtocolHandler.Mode.HTTP));
                    }
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Can not initialize sub protocol. Finder: " +
                            finderClassname, e);
                }
            }

            configurePortUnification();
        } else {
            com.sun.grizzly.config.dom.ProtocolChainInstanceHandler pcihConfig = protocol.getProtocolChainInstanceHandler();
            if (pcihConfig == null) {
                logger.log(Level.WARNING, "Empty protocol declaration");
                return null;
            }

            ProtocolChain protocolChain = null;
            com.sun.grizzly.config.dom.ProtocolChain protocolChainConfig = pcihConfig.getProtocolChain();
            final String protocolChainClassname = protocolChainConfig.getClassname();
            if (protocolChainClassname != null) {
                try {
                    protocolChain = (ProtocolChain) newInstance(protocolChainClassname);
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Can not initialize protocol chain: " +
                            protocolChainClassname + ". Default one will be used", e);
                }
            }

            if (protocolChain == null) {
                protocolChain = new DefaultProtocolChain();
            }

            for(com.sun.grizzly.config.dom.ProtocolFilter protocolFilterConfig : protocolChainConfig.getProtocolFilter()) {
                String filterClassname = protocolFilterConfig.getClassname();
                try {
                    ProtocolFilter filter = (ProtocolFilter) newInstance(filterClassname);
                    protocolChain.addFilter(filter);
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Can not initialize protocol filter: " +
                            filterClassname, e);
                    throw new IllegalStateException("Can not initialize protocol filter: " +
                            filterClassname);
                }
            }

            // Ignore ProtocolChainInstanceHandler class name configuration
            final ProtocolChain finalProtocolChain = protocolChain;
            ProtocolChainInstanceHandler pcih = new DefaultProtocolChainInstanceHandler() {
                @Override
                public boolean offer(ProtocolChain protocolChain) {
                    return true;
                }

                @Override
                public ProtocolChain poll() {
                    return finalProtocolChain;
                }

            };

            return pcih;
        }

        return null;
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
//        final List<ProtocolFinder> protocolFinders = new ArrayList<ProtocolFinder>();
//        protocolFinders.add(new HttpProtocolFinder());
        // [3] Add our supported ProtocolHandler. By default we support http/sip.
//        final List<ProtocolHandler> protocolHandlers = new ArrayList<ProtocolHandler>();
//        protocolHandlers.add(new WebProtocolHandler(getWebProtocolHandlerMode(), this));
        configurePortUnification(finders, handlers, preprocessors);
    }

    @Override
    public void configurePortUnification(List<ProtocolFinder> protocolFinders,
            List<ProtocolHandler> protocolHandlers,
            List<PUPreProcessor> preProcessors) {
        if (puFilterClassname != null) {
            try {
                PUReadFilter puReadFilter = (PUReadFilter) newInstance(puFilterClassname);
                puReadFilter.configure(protocolFinders, protocolHandlers, preProcessors);
            } catch (Exception e) {
                logger.log(Level.WARNING, "Can not initialize port unification filter: "
                        + puFilterClassname + " default filter will be used instead", e);
                super.configurePortUnification(protocolFinders, protocolHandlers,
                        preProcessors);
            }
        } else {
            super.configurePortUnification(protocolFinders, protocolHandlers,
                    preProcessors);
        }
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
        setSecondsMaxAge(Integer.parseInt(cache.getMaxAgeSeconds()));
        setMaxCacheEntries(Integer.parseInt(cache.getMaxFilesCount()));
        setMaxLargeCacheSize(Integer.parseInt(cache.getMaxCacheSizeBytes()));
    }

    private void configureHttpListenerProperty(Http http)
            throws NumberFormatException {

        // http settings
        try {
            setAdapter((Adapter) Class.forName(http.getAdapter()).newInstance());
        } catch (Exception e) {
            throw new GrizzlyConfigException(e.getMessage(), e);
        }

        setMaxKeepAliveRequests(Integer.parseInt(http.getMaxConnections()));
        if (http.getEnableAuthPassThrough() != null) {
            setProperty("authPassthroughEnabled", toBoolean(http.getEnableAuthPassThrough()));
        }
        setMaxPostSize(Integer.parseInt(http.getMaxPostSizeBytes()));
        setCompression(http.getCompression());
        setCompressableMimeTypes(http.getCompressableMimeType());
        setSendBufferSize(Integer.parseInt(http.getSendBufferSizeBytes()));
        if(http.getNoCompressionUserAgents() != null) {
            setNoCompressionUserAgents(http.getNoCompressionUserAgents());
        }
        setCompressionMinSize(Integer.parseInt(http.getCompressionMinSizeBytes()));
        if (http.getRestrictedUserAgents() != null) {
            setRestrictedUserAgents(http.getRestrictedUserAgents());
        }
        enableRcmSupport(toBoolean(http.getEnableRcmSupport()));
        setUploadTimeout(Integer.parseInt(http.getConnectionUploadTimeoutMillis()));
        if (http.getDisableUploadTimeout() != null) {
            setDisableUploadTimeout(toBoolean(http.getDisableUploadTimeout()));
        }
        setProperty("chunking-disabled", toBoolean(http.getChunkingDisabled()));

        if (http.getUriEncoding() != null) {
            setProperty("uriEncoding", http.getUriEncoding());
        }

        if (http.getTraceEnabled() != null) {
            setProperty("traceEnabled", toBoolean(http.getTraceEnabled()));
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
        setMaxHttpHeaderSize(Integer.parseInt(http.getHeaderBufferLengthBytes()));
    }

    /**
     * Configures the keep-alive properties on the given Connector from the given keep-alive config.
     */
    private void configureKeepAlive(Http http) {
        int timeoutInSeconds = 60;
        int maxConnections = 256;
        if (http != null) {
            try {
                timeoutInSeconds = Integer.parseInt(http.getTimeoutSeconds());
            } catch (NumberFormatException ex) {
                String msg = _rb.getString("pewebcontainer.invalidKeepAliveTimeout");
                msg = MessageFormat.format(msg, http.getTimeoutSeconds(), Integer.toString(timeoutInSeconds));
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
    private void configureThreadPool(ThreadPool threadPool, int keepAlive) {
        if (threadPool == null) {
            return;
        }
        try {
            final int maxQueueSize = threadPool.getMaxQueueSize() != null ? Integer.MAX_VALUE
                    : Integer.parseInt(threadPool.getMaxQueueSize());
            final int minThreads = Integer.parseInt(threadPool.getMinThreadPoolSize());
            final int maxThreads = Integer.parseInt(threadPool.getMaxThreadPoolSize());
            final int timeout = Integer.parseInt(threadPool.getIdleThreadTimeoutSeconds());

            setThreadPool(newThreadPool(minThreads, maxThreads, maxQueueSize,
                keepAlive < 0 ? Long.MAX_VALUE : keepAlive * 1000, TimeUnit.MILLISECONDS));
            setCoreThreads(minThreads);
            setMaxThreads(maxThreads);

            List<String> l = ManagementFactory.getRuntimeMXBean().getInputArguments();
            boolean debugMode = false;
            for (String s : l) {
                if (s.trim().startsWith("-Xrunjdwp:")) {
                    debugMode = true;
                    break;
                }
            }
            if (!debugMode) {
                // Idle Threads cannot be alive more than 15 minutes by default
                setTransactionTimeout(timeout * 1000);
            } else {
                // Disable the mechanism
                setTransactionTimeout(-1);
            }
        } catch (NumberFormatException ex) {
            logger.log(Level.WARNING, " Invalid thread-pool attribute", ex);
        }
    }

    protected DefaultThreadPool newThreadPool(int minThreads, int maxThreads,
            int maxQueueSize, long timeout, TimeUnit timeunit) {
        return new StatsThreadPool(minThreads, maxThreads, maxQueueSize, timeout, timeunit);
    }

    protected int getThreadPoolTimeoutSeconds() {
        return threadPoolTimeoutSeconds;
    }

    protected static final boolean toBoolean(String value) {
        final String v = null != value ? value.trim() : value;
        return "true".equals(v) || "yes".equals(v) || "on".equals(v) || "1".equals(v);
    }

    public String getDefaultVirtualServer() {
        return defaultVirtualServer;
    }

    protected Object newInstance(String classname) throws Exception {
        return loadClass(classname).newInstance();
    }

    protected Class loadClass(String classname) throws ClassNotFoundException {
        Class clazz = null;
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl != null) {
            try {
                clazz = cl.loadClass(classname);
            } catch (Exception cnfe) {
            }
        }

        if (clazz == null) {
            clazz = getClassLoader().loadClass(classname);
        }

        return clazz;
    }
}
