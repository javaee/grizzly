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

import com.sun.grizzly.Controller;
import com.sun.grizzly.DefaultProtocolChain;
import com.sun.grizzly.DefaultProtocolChainInstanceHandler;
import com.sun.grizzly.ProtocolChain;
import com.sun.grizzly.ProtocolChainInstanceHandler;
import com.sun.grizzly.ProtocolFilter;
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
import com.sun.grizzly.http.*;
import com.sun.grizzly.portunif.CustomFilterChainProtocolHandler;
import com.sun.grizzly.portunif.PUPreProcessor;
import com.sun.grizzly.portunif.PUReadFilter;
import com.sun.grizzly.portunif.ProtocolFinder;
import com.sun.grizzly.portunif.ProtocolHandler;
import com.sun.grizzly.tcp.Adapter;
import com.sun.grizzly.tcp.StaticResourcesAdapter;
import com.sun.grizzly.tcp.http11.GrizzlyAdapter;
import com.sun.grizzly.util.DataStructures;
import com.sun.grizzly.util.ExtendedThreadPool;
import com.sun.grizzly.util.WorkerThread;
import com.sun.grizzly.util.buf.UDecoder;
import com.sun.grizzly.util.http.mapper.Mapper;
import org.jvnet.hk2.component.Habitat;
import org.jvnet.hk2.component.Inhabitant;
import org.jvnet.hk2.config.ConfigBeanProxy;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.ResourceBundle;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

import static com.sun.grizzly.util.LogMessages.SEVERE_GRIZZLY_CONFIG_MISSING_PROTOCOL_ERROR;
import static com.sun.grizzly.util.LogMessages.SEVERE_GRIZZLY_CONFIG_MISSING_THREADPOOL_ERROR;
import static com.sun.grizzly.util.LogMessages.SEVERE_GRIZZLY_CONFIG_MISSING_TRANSPORT_ERROR;

/**
 * Implementation of Grizzly embedded HTTP listener
 *
 * @author Jeanfrancois Arcand
 * @author Alexey Stashok
 * @author Justin Lee
 */
public class GrizzlyEmbeddedHttp extends SelectorThread {
    private final AtomicBoolean algorithmInitialized = new AtomicBoolean(false);
    private boolean isHttpSecured = false;
    /**
     * The resource bundle containing the message strings for logger.
     */
    protected static final ResourceBundle _rb = logger.getResourceBundle();
    private String defaultVirtualServer;
    // Port unification settings
    protected PUReadFilter puFilter;
    protected final List<ProtocolFinder> finders = new ArrayList<ProtocolFinder>();
    protected final List<ProtocolHandler> handlers = new ArrayList<ProtocolHandler>();
    protected final List<PUPreProcessor> preprocessors = new ArrayList<PUPreProcessor>();
    private UDecoder udecoder;

    private volatile ProtocolChainInstanceHandler rootProtocolChainHandler;

    /**
     * Constructor
     */
    public GrizzlyEmbeddedHttp() {
        setClassLoader(getClass().getClassLoader());
    }

    /**
     * Load using reflection the <code>Algorithm</code> class.
     */
    @Override
    protected void initAlgorithm() {
        if (!algorithmInitialized.getAndSet(true)) {
            algorithmClass = ContainerStaticStreamAlgorithm.class;
            defaultAlgorithmInstalled = true;
        }
    }

    /**
     * Initialize the Grizzly Framework classes.
     */
    @Override
    protected void initController() {
        if (controller == null) {
            controller = new Controller() {
                @Override
                public void logVersion() {
                    // Make this a no-op.  The GF GrizzlyProxy already logs the
                    // Grizzly version; no need to do it twice.
                }
            };
        }

        controller.setKernelExecutorFactory(
                new Controller.KernelExecutorFactory.DefaultFactory() {
                    @Override
                    public ExecutorService create() {
                        return create("grizzly-kernel", "Grizzly-kernel-thread-" + port);
                    }
                });

        super.initController();
        controller.setReadThreadsCount(readThreadsCount);
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
        } catch (Exception t) {
            logger.log(Level.SEVERE, "Unable to stop properly", t);
            // Force the Selector(s) to be closed in case an unexpected
            // exception occurred during shutdown.
            try {
                if (selectorHandler != null && selectorHandler.getSelector() != null) {
                    selectorHandler.getSelector().close();
                }
            } catch (IOException ignored) {
            }
        }
    }

    @Override
    protected void configureProtocolChain() {
        final ProtocolChainInstanceHandler pcih = rootProtocolChainHandler;

        final ProtocolChainInstanceHandler instanceHandler = pcih != null ? pcih
                : new DefaultProtocolChainInstanceHandler() {

                    private final Queue<ProtocolChain> chains =
                            DataStructures.getCLQinstance(ProtocolChain.class);

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
    // ---------------------------------------------- Public get/set ----- //

    public boolean isHttpSecured() {
        return isHttpSecured;
    }

    public void setHttpSecured(boolean httpSecured) {
        isHttpSecured = httpSecured;
    }

    public UDecoder getUrlDecoder() {
        return udecoder;
    }

    public void setUrlDecoder(UDecoder udecoder) {
        this.udecoder = udecoder;
    }

    public void configure(NetworkListener networkListener, Habitat habitat) {
        final Protocol httpProtocol = networkListener.findHttpProtocol();

        final Transport transport = networkListener.findTransport();
        if (transport == null) {
                throw new GrizzlyConfigException(
                                    SEVERE_GRIZZLY_CONFIG_MISSING_TRANSPORT_ERROR(
                                            networkListener.getTransport(),
                                            networkListener.getName()));
        }
        final ThreadPool pool = networkListener.findThreadPool();
        if (pool == null) {
            throw new GrizzlyConfigException(
                                 SEVERE_GRIZZLY_CONFIG_MISSING_THREADPOOL_ERROR(
                                         networkListener.getThreadPool(),
                                         networkListener.getName()));
        }

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

        if (httpProtocol != null) {
            udecoder = new UDecoder(
                    GrizzlyConfig.toBoolean(httpProtocol.getHttp().getEncodedSlashEnabled()));
        }

        final Protocol protocol = networkListener.findProtocol();
        if (protocol == null) {
            throw new GrizzlyConfigException(
                    SEVERE_GRIZZLY_CONFIG_MISSING_PROTOCOL_ERROR(
                            networkListener.getProtocol(), 
                            networkListener.getName()));
        }

        rootProtocolChainHandler = configureProtocol(networkListener, protocol, habitat, true);

        configureThreadPool(networkListener, pool, httpProtocol != null ?
                httpProtocol.getHttp().getRequestTimeoutSeconds() :
                "-1");
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
        setDisplayConfiguration(GrizzlyConfig.toBoolean(transport.getDisplayConfiguration()));
        setSelectorTimeout(Integer.parseInt(transport.getSelectorPollTimeoutMillis()));
        setTcpNoDelay(GrizzlyConfig.toBoolean(transport.getTcpNoDelay()));
        setLinger(Integer.parseInt(transport.getLinger()));
        setSocketKeepAlive(GrizzlyConfig.toBoolean(transport.getKeepAlive()));
        setEnableNioLogging(GrizzlyConfig.toBoolean(transport.getEnableSnoop()));
    }

    protected ProtocolChainInstanceHandler configureProtocol(NetworkListener networkListener, Protocol protocol,
            Habitat habitat, boolean mayEnableAsync) {
        if (protocol.getHttp() != null) {
            // Only HTTP protocol defined
            final Http http = protocol.getHttp();
            configureHttpListenerProperty(http);
            configureKeepAlive(http);
            configureHttpProtocol(http);
            configureFileCache(http.getFileCache());
            defaultVirtualServer = http.getDefaultVirtualServer();
            if (mayEnableAsync && GrizzlyConfig.toBoolean(http.getWebsocketsSupportEnabled())) {
                enableWebSockets(networkListener, habitat);
            }
            if (mayEnableAsync && (GrizzlyConfig.toBoolean(http.getCometSupportEnabled()) ||
                    Boolean.getBoolean("v3.grizzly.cometSupport"))) {
                enableComet(habitat);
            }
            enableAjpSupport(habitat, networkListener);
        } else if (protocol.getPortUnification() != null) {
            // Port unification
            PortUnification pu = protocol.getPortUnification();
            final String puFilterClassname = pu.getClassname();
            if (puFilterClassname != null) {
                try {
                    puFilter = (PUReadFilter) newInstance(puFilterClassname);
                    configureElement(habitat, puFilter, pu);
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Can not initialize port unification filter: " +
                            puFilterClassname + " default filter will be used instead", e);
                }
            }
            for (com.sun.grizzly.config.dom.ProtocolFinder finderConfig : pu.getProtocolFinder()) {
                String finderClassname = finderConfig.getClassname();
                try {
                    ProtocolFinder protocolFinder = (ProtocolFinder) newInstance(finderClassname);
                    configureElement(habitat, protocolFinder, finderConfig);
                    Protocol subProtocol = finderConfig.findProtocol();
                    ProtocolChainInstanceHandler protocolChain = configureProtocol(networkListener, subProtocol,
                            habitat, mayEnableAsync);
                    final String protocolName = subProtocol.getName();
                    finders.add(new ConfigProtocolFinderWrapper(protocolName, protocolFinder));
                    final String[] protocols = new String[]{protocolName};
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
                        handlers.add(new WebProtocolHandler(protocolName,
                                GrizzlyConfig.toBoolean(pu.getWebProtocolStickyEnabled())));
                    }
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Can not initialize sub protocol. Finder: " +
                            finderClassname, e);
                }
            }
            configurePortUnification(finders, handlers, preprocessors);
        } else if (protocol.getHttpRedirect() != null) {
            HttpRedirectFilter filter = new HttpRedirectFilter();
            filter.configure(habitat, protocol.getHttpRedirect());
            ProtocolChain protocolChain = createProtocolChain(habitat, null);
            protocolChain.addFilter(filter);
            return createProtocolChainInstanceHandler(protocolChain);
        } else {
            com.sun.grizzly.config.dom.ProtocolChainInstanceHandler pcihConfig = protocol
                    .getProtocolChainInstanceHandler();
            if (pcihConfig == null) {
                logger.log(Level.WARNING, "Empty protocol declaration");
                return null;
            }

            com.sun.grizzly.config.dom.ProtocolChain protocolChainConfig = pcihConfig.getProtocolChain();
            ProtocolChain protocolChain = createProtocolChain(habitat, protocolChainConfig);
            for (com.sun.grizzly.config.dom.ProtocolFilter protocolFilterConfig : protocolChainConfig
                    .getProtocolFilter()) {
                String filterClassname = protocolFilterConfig.getClassname();
                try {
                    ProtocolFilter filter = (ProtocolFilter) newInstance(filterClassname);
                    configureElement(habitat, filter, protocolFilterConfig);
                    protocolChain.addFilter(filter);
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Can not initialize protocol filter: " +
                            filterClassname, e);
                    throw new IllegalStateException("Can not initialize protocol filter: " +
                            filterClassname);
                }
            }
            // Ignore ProtocolChainInstanceHandler class name configuration
            return createProtocolChainInstanceHandler(protocolChain);
        }
        return null;
    }

    @Override
    public void configurePortUnification(List<ProtocolFinder> protocolFinders,
            List<ProtocolHandler> protocolHandlers,
            List<PUPreProcessor> preProcessors) {
        if (puFilter != null) {
            puFilter.configure(protocolFinders, protocolHandlers, preProcessors);
        } else {
            super.configurePortUnification(protocolFinders, protocolHandlers,
                    preProcessors);
        }
    }

    private void enableAjpSupport(Habitat habitat, NetworkListener networkListener) {
        if(GrizzlyConfig.toBoolean(networkListener.getJkEnabled())) {
            processorTaskFactory = loadAjpFactory(habitat);
            if (processorTaskFactory instanceof ConfigAwareElement) {
                ((ConfigAwareElement) processorTaskFactory).configure(habitat,
                        networkListener);
            }
        }
    }

    /**
     * Enable Comet/Poll request support.
     *
     * @param habitat
     */
    private void enableComet(Habitat habitat) {
        final AsyncFilter asyncFilter = loadCometAsyncFilter(habitat);
        if (asyncFilter != null) {
            addAsyncFilter(asyncFilter);
        }
    }

    /**
     * Enable WebSockets support.
     *
     * @param habitat
     */
    @SuppressWarnings("unchecked")
    private void enableWebSockets(NetworkListener l, Habitat habitat) {
        final AsyncFilter asyncFilter = loadWebSocketsAsyncFilter(habitat);
        if (asyncFilter != null) {
            try {
                Method m = asyncFilter.getClass().getMethod("setMapper", Mapper.class);
                Inhabitant<Mapper> inhab =
                        (Inhabitant<Mapper>) habitat.getInhabitant(Mapper.class, l.getAddress() + l.getPort());
                m.invoke(asyncFilter, inhab.get());
            } catch (Exception ignored) {
            }
            addAsyncFilter(asyncFilter);
        }
    }

    /**
     * Load and initializes Comet {@link AsyncFilter}.
     */
    public static ProcessorTaskFactory loadAjpFactory(final Habitat habitat) {
        return Utils.newInstance(habitat, ProcessorTaskFactory.class, "grizzly-ajp",
                "com.sun.grizzly.http.ajp.AjpProcessorTaskFactory");
    }

    /**
     * Load and initializes Comet {@link AsyncFilter}.
     */
    public static AsyncFilter loadCometAsyncFilter(final Habitat habitat) {
        return loadAsyncFilter(habitat, "comet",
                "com.sun.grizzly.comet.CometAsyncFilter");
    }

    /**
     * Load and initializes WebSockets {@link AsyncFilter}.
     */
    public static AsyncFilter loadWebSocketsAsyncFilter(final Habitat habitat) {
        return loadAsyncFilter(habitat, "websockets",
                "com.sun.grizzly.websockets.WebSocketAsyncFilter");
    }

    /**
     * Load {@link AsyncFilter} with the specific service name and classname.
     *
     * @param habitat
     * @param name
     * @param asyncFilterClassName
     * @return
     */
    private static AsyncFilter loadAsyncFilter(Habitat habitat, final String name,
            final String asyncFilterClassName) {

        return Utils.newInstance(habitat, AsyncFilter.class, name, asyncFilterClassName);
    }

    private void addAsyncFilter(final AsyncFilter asyncFilter) {
        setEnableAsyncExecution(true);
        if (getAsyncHandler() == null) {
            asyncHandler = new DefaultAsyncHandler();
            setAsyncHandler(asyncHandler);
        }
        getAsyncHandler().addAsyncFilter(asyncFilter);
    }


    /**
     * @param protocolChainConfig <code>ProtocolChain</code> configuration data
     * @return a new {@link ProtocolChain} based on the provided
     *         <code>protocolChainConfig</code> data
     */
    private ProtocolChain createProtocolChain(Habitat habitat,
            com.sun.grizzly.config.dom.ProtocolChain protocolChainConfig) {
        if (protocolChainConfig == null) {
            return new DefaultProtocolChain();
        }
        ProtocolChain protocolChain = null;
        final String protocolChainClassname = protocolChainConfig.getClassname();
        if (protocolChainClassname != null) {
            try {
                protocolChain = (ProtocolChain) newInstance(protocolChainClassname);
                configureElement(habitat, protocolChain, protocolChainConfig);
            } catch (Exception e) {
                logger.log(Level.WARNING, "Can not initialize protocol chain: " +
                        protocolChainClassname + ". Default one will be used", e);
            }
        }
        if (protocolChain == null) {
            protocolChain = new DefaultProtocolChain();
        }
        return protocolChain;
    }


    private ProtocolChainInstanceHandler createProtocolChainInstanceHandler(final ProtocolChain protocolChain) {

        return new DefaultProtocolChainInstanceHandler() {
            @Override
            public boolean offer(ProtocolChain protocolChain) {
                return true;
            }

            @Override
            public ProtocolChain poll() {
                return protocolChain;
            }

        };

    }


    /**
     * Configure the Grizzly FileCache mechanism
     */
    private void configureFileCache(FileCache cache) {
        if (cache != null) {
            final boolean enabled = GrizzlyConfig.toBoolean(cache.getEnabled());
            setFileCacheIsEnabled(enabled);
            setLargeFileCacheEnabled(enabled);
            setSecondsMaxAge(Integer.parseInt(cache.getMaxAgeSeconds()));
            setMaxCacheEntries(Integer.parseInt(cache.getMaxFilesCount()));
            setMaxLargeCacheSize(Integer.parseInt(cache.getMaxCacheSizeBytes()));
        }
    }

    private void configureHttpListenerProperty(Http http)
            throws NumberFormatException {
        // http settings
        try {
            setAdapter((Adapter) Class.forName(http.getAdapter()).newInstance());
            if (adapter instanceof GrizzlyAdapter) {
                ((GrizzlyAdapter) adapter).setAllowEncodedSlash(GrizzlyConfig.toBoolean(http.getEncodedSlashEnabled()));
            }
        } catch (Exception e) {
            throw new GrizzlyConfigException(e.getMessage(), e);
        }
        setMaxKeepAliveRequests(Integer.parseInt(http.getMaxConnections()));
        setProperty("authPassthroughEnabled", GrizzlyConfig.toBoolean(http.getAuthPassThroughEnabled()));
        setMaxPostSize(Integer.parseInt(http.getMaxPostSizeBytes()));
        setMaxSwallowingInputBytes(Long.parseLong(http.getMaxSwallowingInputBytes()));
        setCompression(http.getCompression());
        setCompressableMimeTypes(http.getCompressableMimeType());
        setSendBufferSize(Integer.parseInt(http.getSendBufferSizeBytes()));
        if (http.getNoCompressionUserAgents() != null) {
            setNoCompressionUserAgents(http.getNoCompressionUserAgents());
        }
        setCompressionMinSize(Integer.parseInt(http.getCompressionMinSizeBytes()));
        if (http.getRestrictedUserAgents() != null) {
            setRestrictedUserAgents(http.getRestrictedUserAgents());
        }
        enableRcmSupport(GrizzlyConfig.toBoolean(http.getRcmSupportEnabled()));
        
        final int uploadTimeoutMillis = Integer.parseInt(http.getConnectionUploadTimeoutMillis());
        setUploadTimeout(uploadTimeoutMillis);
        setDisableUploadTimeout(uploadTimeoutMillis < 0 || 
                !GrizzlyConfig.toBoolean(http.getUploadTimeoutEnabled()));
        
        setProperty("chunking-enabled", GrizzlyConfig.toBoolean(http.getChunkingEnabled()));
        setUseChunking(GrizzlyConfig.toBoolean(http.getChunkingEnabled()));
        setProperty("uriEncoding", http.getUriEncoding());
        setProperty("traceEnabled", GrizzlyConfig.toBoolean(http.getTraceEnabled()));
        setPreallocateProcessorTasks(GrizzlyConfig.toBoolean(http.getPreallocateProcessorTasks()));
        try {
            setMaxRequestHeaders(Integer.parseInt(http.getMaxRequestHeaders()));
        } catch (NumberFormatException ignored) {
            // default will be applied
        }
        try {
            setMaxResponseHeaders(Integer.parseInt(http.getMaxResponseHeaders()));
        } catch (NumberFormatException ignored) {
            // default will be applied
        }
        if (http.getScheme() != null || http.getSchemeMapping() != null
                || http.getRemoteUserMapping() != null) {
            final BackendConfiguration backendConfiguration = new BackendConfiguration();
            if (http.getSchemeMapping() != null) {
                backendConfiguration.setSchemeMapping(http.getSchemeMapping());
            } else {
                backendConfiguration.setScheme(http.getScheme());
            }
            
            backendConfiguration.setRemoteUserMapping(http.getRemoteUserMapping());
            setBackendConfiguration(backendConfiguration);
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
        String ct = http.getDefaultResponseType();
        if (getAdapter() instanceof StaticResourcesAdapter) {
            ((StaticResourcesAdapter) getAdapter()).setDefaultContentType(ct);
        }
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
    private void configureThreadPool(NetworkListener networkListener, ThreadPool threadPool,
            final String transactionTimeoutSec) {
        if (threadPool == null) {
            return;
        }
        try {
            final int maxQueueSize = Integer.parseInt(threadPool.getMaxQueueSize());
            final int minThreads = Integer.parseInt(threadPool.getMinThreadPoolSize());
            final int maxThreads = Integer.parseInt(threadPool.getMaxThreadPoolSize());
            final int keepAlive = Integer.parseInt(threadPool.getIdleThreadTimeoutSeconds());
            final int ttSec = Integer.parseInt(transactionTimeoutSec);

            final String name = Utils.composeThreadPoolName(networkListener);
            setThreadPool(newThreadPool(name, minThreads, maxThreads, maxQueueSize,
                    keepAlive < 0 ? Long.MAX_VALUE : keepAlive * 1000, TimeUnit.MILLISECONDS));
            setCoreThreads(minThreads);
            setMaxThreads(maxThreads);

            if (!com.sun.grizzly.util.Utils.isDebugVM() && ttSec > 0) {
                // Idle Threads cannot be alive more than 15 minutes by default
                setTransactionTimeout(ttSec * 1000);
            } else {
                // Disable the mechanism
                setTransactionTimeout(-1);
            }
        } catch (NumberFormatException ex) {
            logger.log(Level.WARNING, " Invalid thread-pool attribute", ex);
        }
    }

    protected ExtendedThreadPool newThreadPool(String name, int minThreads, int maxThreads,
            int maxQueueSize, long timeout, TimeUnit timeunit) {
        return new StatsThreadPool(name, minThreads, maxThreads, maxQueueSize, timeout, timeunit);
    }

    public String getDefaultVirtualServer() {
        return defaultVirtualServer;
    }

    protected static Object newInstance(String classname) throws Exception {
        return Utils.newInstance(classname);
    }

    protected static Class loadClass(String classname) throws ClassNotFoundException {
        return Utils.loadClass(classname);
    }

    @SuppressWarnings({"unchecked"})
    private static void configureElement(Habitat habitat, Object instance,
            ConfigBeanProxy configuration) {
        if (instance instanceof ConfigAwareElement) {
            ((ConfigAwareElement) instance).configure(habitat, configuration);
        }
    }
}
