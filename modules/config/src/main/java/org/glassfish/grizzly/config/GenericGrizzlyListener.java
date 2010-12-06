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
package org.glassfish.grizzly.config;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.grizzly.SocketBinder;
import org.glassfish.grizzly.TransportFactory;
import org.glassfish.grizzly.config.dom.Http;
import org.glassfish.grizzly.config.dom.NetworkListener;
import org.glassfish.grizzly.config.dom.Protocol;
import org.glassfish.grizzly.config.dom.Ssl;
import org.glassfish.grizzly.config.dom.ThreadPool;
import org.glassfish.grizzly.config.dom.Transport;
import org.glassfish.grizzly.filterchain.Filter;
import org.glassfish.grizzly.filterchain.FilterChain;
import org.glassfish.grizzly.filterchain.FilterChainBuilder;
import org.glassfish.grizzly.filterchain.TransportFilter;
import org.glassfish.grizzly.http.ContentEncoding;
import org.glassfish.grizzly.http.EncodingFilter;
import org.glassfish.grizzly.http.GZipContentEncoding;
import org.glassfish.grizzly.http.HttpHeader;
import org.glassfish.grizzly.http.HttpResponsePacket;
import org.glassfish.grizzly.http.KeepAlive;
import org.glassfish.grizzly.http.server.FileCacheFilter;
import org.glassfish.grizzly.http.server.HttpRequestProcessor;
import org.glassfish.grizzly.http.server.HttpServerFilter;
import org.glassfish.grizzly.http.server.ServerFilterConfiguration;
import org.glassfish.grizzly.http.server.StaticResourcesService;
import org.glassfish.grizzly.http.server.filecache.FileCache;
import org.glassfish.grizzly.http.util.DataChunk;
import org.glassfish.grizzly.http.util.MimeHeaders;
import org.glassfish.grizzly.nio.NIOTransport;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import org.glassfish.grizzly.rcm.ResourceAllocationFilter;
import org.glassfish.grizzly.ssl.SSLContextConfigurator;
import org.glassfish.grizzly.ssl.SSLEngineConfigurator;
import org.glassfish.grizzly.threadpool.DefaultWorkerThread;
import org.glassfish.grizzly.threadpool.GrizzlyExecutorService;
import org.glassfish.grizzly.threadpool.ThreadPoolConfig;
import org.glassfish.grizzly.utils.DelayedExecutor;
import org.glassfish.grizzly.utils.SilentConnectionFilter;
import org.glassfish.grizzly.websockets.WebSocketFilter;
import org.jvnet.hk2.config.ConfigBeanProxy;

/**
 *
 * @author oleksiys
 */
public class GenericGrizzlyListener implements GrizzlyListener {

    /**
     * The logger to use for logging messages.
     */
    static final Logger logger = Logger.getLogger(GrizzlyListener.class.getName());
    private volatile String name;
    private volatile String address;
    private volatile int port;
    private NIOTransport transport;
    private FilterChain rootFilterChain;

    private volatile ExecutorService auxExecutorService;
    private volatile DelayedExecutor delayedExecutor;

    @Override
    public String getName() {
        return name;
    }

    protected final void setName(final String name) {
        this.name = name;
    }

    public String getAddress() {
        return address;
    }

    protected void setAddress(final String address) {
        this.address = address;
    }

    @Override
    public int getPort() {
        return port;
    }

    protected void setPort(final int port) {
        this.port = port;
    }

    @Override
    public void start() throws IOException {
        delayedExecutor.start();

        ((SocketBinder) transport).bind(address, port);
        transport.start();
    }

    @Override
    public void stop() throws IOException {
        stopDelayedExecutor();
        transport.stop();
        transport = null;
        rootFilterChain = null;
    }

    public <E> List<E> getFilters(Class<E> clazz) {
        final List<E> filters = new ArrayList<E>(2);

        for (final Filter filter : rootFilterChain) {
            if (clazz.isAssignableFrom(filter.getClass())) {
                filters.add((E) filter);
            }
        }

        return filters;
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
    @Override
    public void configure(final NetworkListener networkListener) {
        configureListener(networkListener);
    }

    protected void configureListener(final NetworkListener networkListener) {
        setName(networkListener.getName());
        setAddress(networkListener.getAddress());
        setPort(Integer.parseInt(networkListener.getPort()));

        configureDelayedExecutor();
        configureTransport(networkListener.findTransport());
        configureProtocol(networkListener.findHttpProtocol(), rootFilterChain);
        configureThreadPool(networkListener.findThreadPool());
    }

    protected void configureTransport(final Transport transportConfig) {
        final String transportName = transportConfig.getName();
        if ("tcp".equalsIgnoreCase(transportName)) {
            transport = configureTCPTransport(transportConfig);
        } else if ("udp".equalsIgnoreCase(transportName)) {
            transport = configureUDPTransport(transportConfig);
        } else {
            throw new GrizzlyConfigException("Unsupported transport type " + transportConfig.getName());
        }

        transport.setSelectorRunnersCount(Integer.parseInt(transportConfig.getAcceptorThreads()));
        transport.setReadBufferSize(Integer.parseInt(transportConfig.getBufferSizeBytes()));

        rootFilterChain = FilterChainBuilder.stateless().build();
        rootFilterChain.add(new TransportFilter());

        transport.setProcessor(rootFilterChain);
    }

    protected NIOTransport configureTCPTransport(final Transport transportConfig) {
        final TCPNIOTransport tcpTransport = TransportFactory.getInstance().createTCPTransport();
        tcpTransport.setTcpNoDelay(Boolean.parseBoolean(transportConfig.getTcpNoDelay()));
        tcpTransport.setLinger(Integer.parseInt(transportConfig.getLinger()));
        tcpTransport.setServerConnectionBackLog(Integer.parseInt(transportConfig.getMaxConnectionsCount()));

        return tcpTransport;
    }

    protected NIOTransport configureUDPTransport(final Transport transportConfig) {
        return TransportFactory.getInstance().createUDPTransport();
    }

    private void configureProtocol(final Protocol protocol,
            final FilterChain filterChain) {
        
//        final Protocol protocol = networkListener.findProtocol();

        final Http http = protocol.getHttp();
        if (http != null) {
            if (Boolean.valueOf(protocol.getSecurityEnabled())) {
                configureSsl(protocol.getSsl());
            }

            configureHttpListener(http, filterChain);

            // Only HTTP protocol defined
//            configureHttpListenerProperty(http);
//            configureKeepAlive(http);
//            configureHttpProtocol(http);
//            configureFileCache(http.getFileCache());
//            defaultVirtualServer = http.getDefaultVirtualServer();

            
            // acceptor-threads
//            if (mayEnableComet && GrizzlyConfig.toBoolean(http.getCometSupportEnabled())) {
//                throw new GrizzlyConfigException("Comet not support in 2.x yet.");
////                configureComet(habitat);
//            }

            /*
            } else if (protocol.getPortUnification() != null) {
            // Port unification
            PortUnification pu = protocol.getPortUnification();
            final String puFilterClassname = pu.getClassname();
            if (puFilterClassname != null) {
            try {
            puFilter = (PUReadFilter) newInstance(puFilterClassname);
            configureElement(puFilter, pu);
            } catch (Exception e) {
            logger.log(Level.WARNING,
            "Can not initialize port unification filter: " +
            puFilterClassname + " default filter will be used instead", e);
            }
            }
            List<ProtocolFinder> findersConfig = pu.getProtocolFinder();
            for (com.glassfish.grizzly.config.dom.ProtocolFinder finderConfig : findersConfig) {
            String finderClassname = finderConfig.getClassname();
            try {
            ProtocolFinder protocolFinder = (ProtocolFinder) newInstance(finderClassname);
            configureElement(protocolFinder, finderConfig);
            Protocol subProtocol = finderConfig.findProtocol();
            ProtocolChainInstanceHandler protocolChain =
            configureProtocol(networkListener, subProtocol,
            habitat, mayEnableComet);
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
            handlers.add(new WebProtocolHandler(protocolName));
            }
            } catch (Exception e) {
            logger.log(Level.WARNING, "Can not initialize sub protocol. Finder: " +
            finderClassname, e);
            }
            }
            configurePortUnification(); */
        } else {
            org.glassfish.grizzly.config.dom.ProtocolChainInstanceHandler pcihConfig = protocol.getProtocolChainInstanceHandler();
            if (pcihConfig == null) {
                logger.log(Level.WARNING, "Empty protocol declaration");
                return;
            }

            org.glassfish.grizzly.config.dom.ProtocolChain filterChainConfig = pcihConfig.getProtocolChain();
//            final String protocolChainClassname = protocolChainConfig.getClassname();
//            if (protocolChainClassname != null) {
//                try {
//                    filterChain = (ProtocolChain) newInstance(protocolChainClassname);
//                    configureElement(filterChain, protocolChainConfig);
//                } catch (Exception e) {
//                    logger.log(Level.WARNING, "Can not initialize protocol chain: "
//                            + protocolChainClassname + ". Default one will be used", e);
//                }
//            }
//            if (filterChain == null) {
//                filterChain = new DefaultProtocolChain();
//            }
            
            for (org.glassfish.grizzly.config.dom.ProtocolFilter filterConfig : filterChainConfig.getProtocolFilter()) {
                final String filterClassname = filterConfig.getClassname();
                try {
                    Filter filter = (Filter) Utils.newInstance(filterClassname);
                    configureElement(filter, filterConfig);
                    filterChain.add(filter);
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Can not initialize protocol filter: "
                            + filterClassname, e);
                    throw new IllegalStateException("Can not initialize protocol filter: "
                            + filterClassname);
                }
            }
        }
    }

    protected SSLEngineConfigurator configureSsl(final Ssl ssl) {
        final SSLConfigHolder holder = new SSLConfigHolder(ssl);

        final SSLEngineConfigurator sslEngineConfigurator = new SSLEngineConfigurator(SSLContextConfigurator.DEFAULT_CONFIG);
        sslEngineConfigurator.setEnabledCipherSuites(holder.getEnabledCipherSuites());
        sslEngineConfigurator.setEnabledProtocols(holder.getEnabledProtocols());
        sslEngineConfigurator.setNeedClientAuth(holder.isNeedClientAuth());
        sslEngineConfigurator.setWantClientAuth(holder.isWantClientAuth());
        sslEngineConfigurator.setClientMode(holder.isClientMode());

        return sslEngineConfigurator;
    }

    @SuppressWarnings({"unchecked"})
    private static void configureElement(Object instance,
            ConfigBeanProxy configuration) {
        if (instance instanceof ConfigAwareElement) {
            ((ConfigAwareElement) instance).configure(configuration);
        }
    }

    /*
    protected void configurePortUnification() {
    configurePortUnification(finders, handlers, preprocessors);
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

    private final void configureComet(Habitat habitat) {
    final AsyncFilter cometFilter = habitat.getComponent(AsyncFilter.class, "comet");
    if (cometFilter != null) {
    setEnableAsyncExecution(true);
    asyncHandler = new DefaultAsyncHandler();
    asyncHandler.addAsyncFilter(cometFilter);
    setAsyncHandler(asyncHandler);
    }
    }
     */

    protected void configureThreadPool(final ThreadPool threadPool) {
        try {
//            Http http = listener.findHttpProtocol().getHttp();
//            int keepAlive = http == null ? 0 : Integer.parseInt(http.getTimeoutSeconds());
            final int maxQueueSize = threadPool.getMaxQueueSize() != null ? Integer.MAX_VALUE
                    : Integer.parseInt(threadPool.getMaxQueueSize());
            final int minThreads = Integer.parseInt(threadPool.getMinThreadPoolSize());
            final int maxThreads = Integer.parseInt(threadPool.getMaxThreadPoolSize());
            final int timeout = Integer.parseInt(threadPool.getIdleThreadTimeoutSeconds());
            final ThreadPoolConfig poolConfig = ThreadPoolConfig.defaultConfig();
            poolConfig.setCorePoolSize(minThreads);
            poolConfig.setMaxPoolSize(maxThreads);
            poolConfig.setQueueLimit(maxQueueSize);
            poolConfig.setKeepAliveTime(timeout < 0 ? Long.MAX_VALUE : timeout, TimeUnit.SECONDS);
            transport.setThreadPool(GrizzlyExecutorService.createInstance(poolConfig));
//            List<String> l = ManagementFactory.getRuntimeMXBean().getInputArguments();
//            boolean debugMode = false;
//            for (String s : l) {
//                if (s.trim().startsWith("-Xrunjdwp:")) {
//                    debugMode = true;
//                    break;
//                }
//            }
//            if (!debugMode && timeout > 0) {
//                // Idle Threads cannot be alive more than 15 minutes by default
//                httpListener.setTransactionTimeout(timeout * 1000);
//            } else {
//                // Disable the mechanism
//                httpListener.setTransactionTimeout(-1);
//            }
        } catch (NumberFormatException ex) {
            logger.log(Level.WARNING, " Invalid thread-pool attribute", ex);
        }
    }

    protected void configureDelayedExecutor() {
        final AtomicInteger threadCounter = new AtomicInteger();

        auxExecutorService = Executors.newCachedThreadPool(
                new ThreadFactory() {

            @Override
            public Thread newThread(Runnable r) {
                final Thread newThread = new DefaultWorkerThread(
                        TransportFactory.getInstance().getDefaultAttributeBuilder(),
                        getName() + "-" + threadCounter.getAndIncrement(),
                        null,
                        r);
                newThread.setDaemon(true);
                return newThread;
            }
        });

        delayedExecutor = new DelayedExecutor(auxExecutorService);
    }


    protected void stopDelayedExecutor() {
        final DelayedExecutor localDelayedExecutor = delayedExecutor;
        delayedExecutor = null;

        if (localDelayedExecutor != null) {
            localDelayedExecutor.stop();
        }
        
        final ExecutorService localThreadPool = auxExecutorService;
        auxExecutorService = null;

        if (localThreadPool != null) {
            localThreadPool.shutdownNow();
        }
    }

    protected void configureHttpListener(final Http http, final FilterChain filterChain) {
        final int idleTimeoutSeconds = Integer.parseInt(http.getTimeoutSeconds());
        filterChain.add(new SilentConnectionFilter(delayedExecutor, idleTimeoutSeconds,
                TimeUnit.SECONDS));

        final int maxHeaderSize = Integer.parseInt(http.getHeaderBufferLengthBytes());
        final boolean chunkingEnabled = Boolean.parseBoolean(http.getChunkingEnabled());

        final KeepAlive keepAlive = configureKeepAlive(http);
        final org.glassfish.grizzly.http.HttpServerFilter httpServerFilter =
                new org.glassfish.grizzly.http.HttpServerFilter(
                chunkingEnabled, maxHeaderSize,
                keepAlive,
                delayedExecutor);

        final Set<ContentEncoding> contentEncodings = configureContentEncodings(http);

        for (ContentEncoding contentEncoding : contentEncodings) {
            httpServerFilter.addContentEncoding(contentEncoding);
        }
        
        httpServerFilter.addContentEncoding(new GZipContentEncoding());

        final boolean isRcmSupportEnabled = Boolean.parseBoolean(http.getRcmSupportEnabled());
        if (isRcmSupportEnabled) {
            filterChain.add(new ResourceAllocationFilter());
        }

//        httpServerFilter.getMonitoringConfig().addProbes(
//                serverConfig.getMonitoringConfig().getHttpConfig().getProbes());
        filterChain.add(httpServerFilter);

        final boolean websocketsSupportEnabled = Boolean.parseBoolean(http.getWebsocketsSupportEnabled());

        if (websocketsSupportEnabled) {
            filterChain.add(new WebSocketFilter());
        }

        final FileCache fileCache = configureHttpFileCache(http.getFileCache());
        fileCache.initialize(transport.getMemoryManager(), delayedExecutor);
        final FileCacheFilter fileCacheFilter = new FileCacheFilter(fileCache);
//        fileCache.getMonitoringConfig().addProbes(
//                serverConfig.getMonitoringConfig().getFileCacheConfig().getProbes());
        filterChain.add(fileCacheFilter);

        final HttpServerFilter webServerFilter = new HttpServerFilter(
                getHttpServerFilterConfiguration(http),
                delayedExecutor);
        webServerFilter.setHttpService(getHttpService(http));

//        webServerFilter.getMonitoringConfig().addProbes(
//                serverConfig.getMonitoringConfig().getWebServerConfig().getProbes());
        filterChain.add(webServerFilter);
    }

    protected ServerFilterConfiguration getHttpServerFilterConfiguration(final Http http) {
        return new ServerFilterConfiguration();
    }

    protected HttpRequestProcessor getHttpService(final Http http) {
        return new StaticResourcesService(".");
    }

    /**
     * Configure the Grizzly HTTP FileCache mechanism
     */
    protected FileCache configureHttpFileCache(final org.glassfish.grizzly.config.dom.FileCache cache) {
        final FileCache fileCache = new FileCache();

        if (cache != null) {
            fileCache.setEnabled(GrizzlyConfig.toBoolean(cache.getEnabled()));

            fileCache.setSecondsMaxAge(Integer.parseInt(cache.getMaxAgeSeconds()));
            fileCache.setMaxCacheEntries(Integer.parseInt(cache.getMaxFilesCount()));
            fileCache.setMaxLargeFileCacheSize(Integer.parseInt(cache.getMaxCacheSizeBytes()));
        } else {
            fileCache.setEnabled(false);
        }

        return fileCache;
    }

    protected KeepAlive configureKeepAlive(final Http http) {
        int timeoutInSeconds = 60;
        int maxConnections = 256;
        if (http != null) {
            try {
                timeoutInSeconds = Integer.parseInt(http.getTimeoutSeconds());
            } catch (NumberFormatException ex) {
//                String msg = _rb.getString("pewebcontainer.invalidKeepAliveTimeout");
                String msg = "pewebcontainer.invalidKeepAliveTimeout";
                msg = MessageFormat.format(msg, http.getTimeoutSeconds(), Integer.toString(timeoutInSeconds));
                logger.log(Level.WARNING, msg, ex);
            }
            try {
                maxConnections = Integer.parseInt(http.getMaxConnections());
            } catch (NumberFormatException ex) {
//                String msg = _rb.getString("pewebcontainer.invalidKeepAliveMaxConnections");
                String msg = "pewebcontainer.invalidKeepAliveMaxConnections";
                msg = MessageFormat.format(msg, http.getMaxConnections(), Integer.toString(maxConnections));
                logger.log(Level.WARNING, msg, ex);
            }
        }
        final KeepAlive keepAlive = new KeepAlive();

        keepAlive.setIdleTimeoutInSeconds(timeoutInSeconds);
        keepAlive.setMaxRequestsCount(maxConnections);
        
        return keepAlive;
    }

    protected Set<ContentEncoding> configureContentEncodings(final Http http) {
        final Set<ContentEncoding> compressionEncodings = configureCompressionEncodings(http);

        return compressionEncodings;
    }

    protected Set<ContentEncoding> configureCompressionEncodings(final Http http) {
        final String mode = http.getCompression();

        int compressionMinSize = Integer.parseInt(http.getCompressionMinSizeBytes());

        COMPRESSION_LEVEL compressionLevel;

        try {
            compressionLevel = getCompressionLevel(mode);
        } catch (IllegalArgumentException e) {
            try {
                // Try to parse compression as an int, which would give the
                // minimum compression size
                compressionLevel = COMPRESSION_LEVEL.ON;
                compressionMinSize = Integer.parseInt(mode);
            } catch (Exception ignore) {
            }

            compressionLevel = COMPRESSION_LEVEL.OFF;
        }

        final String compressableMimeTypesString = http.getCompressableMimeType();
        final String noCompressionUserAgentsString = http.getNoCompressionUserAgents();

        final String[] compressableMimeTypes = split(compressableMimeTypesString, ",");
        final String[] noCompressionUserAgents = split(noCompressionUserAgentsString, ",");

        final ContentEncoding gzipContentEncoding = new GZipContentEncoding(
                GZipContentEncoding.DEFAULT_IN_BUFFER_SIZE,
                GZipContentEncoding.DEFAULT_OUT_BUFFER_SIZE,
                new CompressionEncodingFilter(compressionLevel, compressionMinSize,
                compressableMimeTypes,
                noCompressionUserAgents,
                GZipContentEncoding.ALIASES));

        return Collections.singleton(gzipContentEncoding);
    }

    private String[] split(final String s, final String delim) {
        if (s == null) {
            return new String[0];
        }

        final List<String> compressableMimeTypeList = new ArrayList<String>(4);
        final StringTokenizer st = new StringTokenizer(s, delim);

        while(st.hasMoreTokens()) {
            compressableMimeTypeList.add(st.nextToken().trim());
        }

        final String[] splitStrings = new String[compressableMimeTypeList.size()];
        compressableMimeTypeList.toArray(splitStrings);

        return splitStrings;
    }

    public static enum COMPRESSION_LEVEL {OFF, ON, FORCE};
    
    /**
     * Set compression level.
     */
    protected final COMPRESSION_LEVEL getCompressionLevel(final String compression) {
        if ("on".equalsIgnoreCase(compression)) {
            return COMPRESSION_LEVEL.ON;
        } else if ("force".equalsIgnoreCase(compression)) {
            return COMPRESSION_LEVEL.FORCE;
        } else if ("off".equalsIgnoreCase(compression)) {
            return COMPRESSION_LEVEL.OFF;
        }

        throw new IllegalArgumentException();
    }

    public class CompressionEncodingFilter implements EncodingFilter {

        private final COMPRESSION_LEVEL compressionLevel;
        private final int compressionMinSize;
        private final String[] compressableMimeTypes;
        private final String[] noCompressionUserAgents;
        private final String[] aliases;

        public CompressionEncodingFilter(final COMPRESSION_LEVEL compressionLevel,
                final int compressionMinSize,
                final String[] compressableMimeTypes,
                final String[] noCompressionUserAgents,
                final String[] aliases) {
            this.compressionLevel = compressionLevel;
            this.compressionMinSize = compressionMinSize;
            this.compressableMimeTypes = compressableMimeTypes;
            this.noCompressionUserAgents = noCompressionUserAgents;
            this.aliases = aliases;
        }


        @Override
        public boolean applyEncoding(final HttpHeader httpPacket) {
            switch (compressionLevel) {
                case OFF:
                    return false;
                default: {
                    // Compress only since HTTP 1.1
                    if (!org.glassfish.grizzly.http.Protocol.HTTP_1_1.equals(
                            httpPacket.getProtocol())) {
                        return false;
                    }

                    final HttpResponsePacket responsePacket = (HttpResponsePacket) httpPacket;

                    final MimeHeaders requestHeaders = responsePacket.getRequest().getHeaders();

                    // Check if browser support gzip encoding
                    final DataChunk acceptEncodingDC =
                            requestHeaders.getValue("accept-encoding");

                    if (acceptEncodingDC == null || indexOf(aliases, acceptEncodingDC) == -1) {
                        return false;
                    }

                    final MimeHeaders responseHeaders = responsePacket.getHeaders();

                    // Check if content is not already gzipped
                    final DataChunk contentEncodingMB =
                            responseHeaders.getValue("Content-Encoding");

                    if (contentEncodingMB != null
                            && indexOf(aliases, contentEncodingMB) != -1) {
                        return false;
                    }

                    // If force mode, always compress (test purposes only)
                    if (compressionLevel == COMPRESSION_LEVEL.FORCE) {
                        return true;
                    }

                    // Check for incompatible Browser
                    if (noCompressionUserAgents.length > 0) {
                        final DataChunk userAgentValueDC =
                                requestHeaders.getValue("user-agent");
                        if (userAgentValueDC != null) {
                            if (userAgentValueDC != null &&
                                    indexOf(noCompressionUserAgents, userAgentValueDC) != -1) {
                                return false;
                            }
                        }
                    }

                    // Check if suffisant len to trig the compression
                    final long contentLength = responsePacket.getContentLength();
                    if (contentLength == -1
                            || contentLength > compressionMinSize) {
                        // Check for compatible MIME-TYPE
                        if (compressableMimeTypes.length > 0) {
                            return indexOfStartsWith(compressableMimeTypes,
                                    responsePacket.getContentType()) != -1;
                        }
                    }

                    return true;
                }
            }
        }
    }
    
    private static int indexOf(final String[] aliases, final DataChunk dc) {
        for (int i = 0; i < aliases.length; i++) {
            final String alias = aliases[i];
            if (dc.equalsIgnoreCase(alias)) {
                return i;
            }
        }

        return -1;
    }

    private static int indexOfStartsWith(final String[] aliases, final String s) {
        for (int i = 0; i < aliases.length; i++) {
            final String alias = aliases[i];
            if (s.startsWith(alias)) {
                return i;
            }
        }

        return -1;
    }
}
