/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2007-2012 Oracle and/or its affiliates. All rights reserved.
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

import java.beans.PropertyChangeEvent;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.grizzly.IOStrategy;

import org.glassfish.grizzly.SocketBinder;
import org.glassfish.grizzly.config.dom.Http;
import org.glassfish.grizzly.config.dom.NetworkListener;
import org.glassfish.grizzly.config.dom.PortUnification;
import org.glassfish.grizzly.config.dom.Protocol;
import org.glassfish.grizzly.config.dom.ProtocolChain;
import org.glassfish.grizzly.config.dom.ProtocolChainInstanceHandler;
import org.glassfish.grizzly.config.dom.ProtocolFilter;
import org.glassfish.grizzly.config.dom.Ssl;
import org.glassfish.grizzly.config.dom.ThreadPool;
import org.glassfish.grizzly.config.dom.Transport;
import org.glassfish.grizzly.config.portunif.HttpRedirectFilter;
import org.glassfish.grizzly.filterchain.Filter;
import org.glassfish.grizzly.filterchain.FilterChain;
import org.glassfish.grizzly.filterchain.FilterChainBuilder;
import org.glassfish.grizzly.filterchain.TransportFilter;
import org.glassfish.grizzly.http.ContentEncoding;
import org.glassfish.grizzly.http.GZipContentEncoding;
import org.glassfish.grizzly.http.KeepAlive;
import org.glassfish.grizzly.http.LZMAContentEncoding;
import org.glassfish.grizzly.http.server.AddOn;
import org.glassfish.grizzly.http.server.CompressionEncodingFilter;
import org.glassfish.grizzly.http.server.CompressionLevel;
import org.glassfish.grizzly.http.server.FileCacheFilter;
import org.glassfish.grizzly.http.server.HttpHandler;
import org.glassfish.grizzly.http.server.HttpServerFilter;
import org.glassfish.grizzly.http.server.ServerFilterConfiguration;
import org.glassfish.grizzly.http.server.StaticHttpHandler;
import org.glassfish.grizzly.http.server.filecache.FileCache;
import org.glassfish.grizzly.nio.NIOTransport;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import org.glassfish.grizzly.nio.transport.TCPNIOTransportBuilder;
import org.glassfish.grizzly.nio.transport.UDPNIOTransport;
import org.glassfish.grizzly.nio.transport.UDPNIOTransportBuilder;
import org.glassfish.grizzly.portunif.PUFilter;
import org.glassfish.grizzly.portunif.PUProtocol;
import org.glassfish.grizzly.portunif.ProtocolFinder;
import org.glassfish.grizzly.portunif.finders.SSLProtocolFinder;
import org.glassfish.grizzly.rcm.ResourceAllocationFilter;
import org.glassfish.grizzly.ssl.SSLEngineConfigurator;
import org.glassfish.grizzly.ssl.SSLFilter;
import org.glassfish.grizzly.strategies.WorkerThreadIOStrategy;
import org.glassfish.grizzly.threadpool.DefaultWorkerThread;
import org.glassfish.grizzly.threadpool.GrizzlyExecutorService;
import org.glassfish.grizzly.threadpool.ThreadPoolConfig;
import org.glassfish.grizzly.utils.DelayedExecutor;
import org.glassfish.grizzly.utils.IdleTimeoutFilter;
import org.jvnet.hk2.component.Habitat;
import org.jvnet.hk2.config.ConfigBeanProxy;

/**
 * Generic {@link GrizzlyListener} implementation, which is not HTTP dependent, and can support any Transport
 * configuration, based on {@link FilterChain}.
 *
 * @author Alexey Stashok
 */
public class GenericGrizzlyListener implements GrizzlyListener {
    /**
     * The logger to use for logging messages.
     */
    static final Logger logger = Logger.getLogger(GrizzlyListener.class.getName());
    protected volatile String name;
    protected volatile InetAddress address;
    protected volatile int port;
    protected NIOTransport transport;
    protected FilterChain rootFilterChain;
    private volatile ExecutorService auxExecutorService;
    private volatile DelayedExecutor delayedExecutor;
    private volatile long transactionTimeoutMillis = -1;

    @Override
    public String getName() {
        return name;
    }

    protected final void setName(String name) {
        this.name = name;
    }

    @Override
    public InetAddress getAddress() {
        return address;
    }

    protected final void setAddress(InetAddress inetAddress) {
        address = inetAddress;
    }

    @Override
    public int getPort() {
        return port;
    }

    protected void setPort(int port) {
        this.port = port;
    }

    @Override
    public void start() throws IOException {
        delayedExecutor.start();
        ((SocketBinder) transport).bind(new InetSocketAddress(address, port));
        transport.start();
    }

    @Override
    public void stop() throws IOException {
        stopDelayedExecutor();
        transport.stop();
        transport = null;
        rootFilterChain = null;
    }

    @Override
    public void destroy() {
    }

    @Override
    public void processDynamicConfigurationChange(Habitat habitat,
        PropertyChangeEvent[] events) {
    }

    @Override
    public <T> T getAdapter(Class<T> adapterClass) {
        return null;
    }

    public <E> List<E> getFilters(Class<E> clazz) {
        return getFilters(clazz, rootFilterChain, new ArrayList<E>(2));
    }

    public org.glassfish.grizzly.Transport getTransport() {
        return transport;
    }
    
    @SuppressWarnings({"unchecked"})
    public static <E> List<E> getFilters(Class<E> clazz,
        FilterChain filterChain, List<E> filters) {
        for (final Filter filter : filterChain) {
            if (clazz.isAssignableFrom(filter.getClass())) {
                filters.add((E) filter);
            }
            if (PUFilter.class.isAssignableFrom(filter.getClass())) {
                final Set<PUProtocol> puProtocols = ((PUFilter) filter).getProtocols();
                for (PUProtocol puProtocol : puProtocols) {
                    getFilters(clazz, puProtocol.getFilterChain(), filters);
                }
            }
        }
        return filters;
    }

    /*
     * Configures the given grizzlyListener.
     *
     * @param networkListener The NetworkListener to configure
     */
    // TODO: Must get the information from domain.xml Config objects.
    // TODO: Pending Grizzly issue 54
    @Override
    public void configure(final Habitat habitat,
        final NetworkListener networkListener) throws IOException {
        setName(networkListener.getName());
        setAddress(InetAddress.getByName(networkListener.getAddress()));
        setPort(Integer.parseInt(networkListener.getPort()));
        configureDelayedExecutor(habitat);

        final FilterChainBuilder filterChainBuilder = FilterChainBuilder.stateless();
        
        configureTransport(habitat, networkListener,
                networkListener.findTransport(), filterChainBuilder);

        configureProtocol(habitat, networkListener,
                networkListener.findProtocol(), filterChainBuilder);

        configureThreadPool(habitat, networkListener,
                networkListener.findThreadPool());

        rootFilterChain = filterChainBuilder.build();
        transport.setProcessor(rootFilterChain);
    }

    protected void configureTransport(final Habitat habitat,
            final NetworkListener networkListener,
            final Transport transportConfig,
            final FilterChainBuilder filterChainBuilder) {
        
        final String transportClassName = transportConfig.getClassname();
        if (TCPNIOTransport.class.getName().equals(transportClassName)) {
            transport = configureTCPTransport(habitat, networkListener, transportConfig);
        } else if (UDPNIOTransport.class.getName().equals(transportClassName)) {
            transport = configureUDPTransport(habitat, networkListener, transportConfig);
        } else {
            throw new GrizzlyConfigException("Unsupported transport type " + transportConfig.getName());
        }
        transport.setSelectorRunnersCount(Integer.parseInt(transportConfig.getAcceptorThreads()));
        transport.setReadBufferSize(Integer.parseInt(transportConfig.getBufferSizeBytes()));
        transport.getKernelThreadPoolConfig().setPoolName(networkListener.getName() + "-kernel");
        transport.setIOStrategy(loadIOStrategy(transportConfig.getIOStrategy()));
        
        filterChainBuilder.add(new TransportFilter());
    }

    protected NIOTransport configureTCPTransport(final Habitat habitat,
            final NetworkListener networkListener,
            final Transport transportConfig) {
        
        final TCPNIOTransport tcpTransport = TCPNIOTransportBuilder.newInstance().build();
        tcpTransport.setTcpNoDelay(Boolean.parseBoolean(transportConfig.getTcpNoDelay()));
        tcpTransport.setLinger(Integer.parseInt(transportConfig.getLinger()));
        tcpTransport.setKeepAlive(Boolean.parseBoolean(transportConfig.getKeepAlive()));
        tcpTransport.setServerConnectionBackLog(Integer.parseInt(transportConfig.getMaxConnectionsCount()));
        return tcpTransport;
    }

    protected NIOTransport configureUDPTransport(final Habitat habitat,
            final NetworkListener networkListener,
            final Transport transportConfig) {
        return UDPNIOTransportBuilder.newInstance().build();
    }

    protected void configureProtocol(final Habitat habitat,
            final NetworkListener networkListener,
            final Protocol protocol,
            final FilterChainBuilder filterChainBuilder) {
        
        if (Boolean.valueOf(protocol.getSecurityEnabled())) {
            configureSsl(habitat, 
                         networkListener, 
                         getSsl(protocol),
                         filterChainBuilder);
        }
        configureSubProtocol(habitat, networkListener, protocol,
                filterChainBuilder);
    }

    protected void configureSubProtocol(final Habitat habitat,
            final NetworkListener networkListener,
            final Protocol protocol,
            final FilterChainBuilder filterChainBuilder) {
        
        if (protocol.getHttp() != null) {
            final Http http = protocol.getHttp();
            configureHttpProtocol(habitat, networkListener, http, filterChainBuilder);

        } else if (protocol.getPortUnification() != null) {
            // Port unification
            final PortUnification pu = protocol.getPortUnification();
            final String puFilterClassname = pu.getClassname();
            PUFilter puFilter = null;
            if (puFilterClassname != null) {
                try {
                    puFilter = Utils.newInstance(habitat,
                        PUFilter.class, puFilterClassname, puFilterClassname);
                    configureElement(habitat, networkListener, pu, puFilter);
                } catch (Exception e) {
                    logger.log(Level.WARNING,
                        "Can not initialize port unification filter: "
                            + puFilterClassname + " default filter will be used instead", e);
                }
            }
            if (puFilter == null) {
                puFilter = new PUFilter();
            }
            List<org.glassfish.grizzly.config.dom.ProtocolFinder> findersConfig = pu.getProtocolFinder();
            for (org.glassfish.grizzly.config.dom.ProtocolFinder finderConfig : findersConfig) {
                final String finderClassname = finderConfig.getClassname();
                try {
                    final ProtocolFinder protocolFinder = Utils.newInstance(habitat,
                        ProtocolFinder.class, finderClassname, finderClassname);
                    configureElement(habitat, networkListener, finderConfig, protocolFinder);
                    final Protocol subProtocol = finderConfig.findProtocol();
                    final FilterChainBuilder subProtocolFilterChainBuilder =
                        puFilter.getPUFilterChainBuilder();
                    // If subprotocol is secured - we need to wrap it under SSLProtocolFinder
                    if (Boolean.valueOf(subProtocol.getSecurityEnabled())) {
                        final PUFilter extraSslPUFilter = new PUFilter();
                        
                        configureSsl(habitat, networkListener,
                                getSsl(subProtocol), subProtocolFilterChainBuilder);
                        
                        subProtocolFilterChainBuilder.add(extraSslPUFilter);
                        final FilterChainBuilder extraSslPUFilterChainBuilder =
                            extraSslPUFilter.getPUFilterChainBuilder();
                        configureSubProtocol(habitat, networkListener,
                                subProtocol, extraSslPUFilterChainBuilder);
                        extraSslPUFilter.register(protocolFinder,
                                extraSslPUFilterChainBuilder.build());
                        
                        puFilter.register(new SSLProtocolFinder(
                            new SSLConfigurator(habitat, subProtocol.getSsl())),
                            subProtocolFilterChainBuilder.build());
                    } else {
                        configureSubProtocol(habitat, networkListener,
                                subProtocol, subProtocolFilterChainBuilder);
                        puFilter.register(protocolFinder, subProtocolFilterChainBuilder.build());
                    }
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Can not initialize sub protocol. Finder: "
                        + finderClassname, e);
                }
            }
            filterChainBuilder.add(puFilter);
        } else if (protocol.getHttpRedirect() != null) {
            filterChainBuilder.add(new org.glassfish.grizzly.http.HttpServerFilter());
            final HttpRedirectFilter filter = new HttpRedirectFilter();
            filter.configure(habitat, networkListener, protocol.getHttpRedirect());
            filterChainBuilder.add(filter);
        } else {
            ProtocolChainInstanceHandler pcihConfig = protocol.getProtocolChainInstanceHandler();
            if (pcihConfig == null) {
                logger.log(Level.WARNING, "Empty protocol declaration");
                return;
            }
            ProtocolChain filterChainConfig = pcihConfig.getProtocolChain();
            for (ProtocolFilter filterConfig : filterChainConfig.getProtocolFilter()) {
                final String filterClassname = filterConfig.getClassname();
                try {
                    final Filter filter = (Filter) Utils.newInstance(filterClassname);
                    configureElement(habitat, networkListener, filterConfig, filter);
                    filterChainBuilder.add(filter);
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Can not initialize protocol filter: "
                        + filterClassname, e);
                    throw new IllegalStateException("Can not initialize protocol filter: "
                        + filterClassname);
                }
            }
        }
    }

    protected static void configureSsl(final Habitat habitat,
            final NetworkListener networkListener, final Ssl ssl,
            final FilterChainBuilder filterChainBuilder) {
        final SSLEngineConfigurator serverConfig = new SSLConfigurator(habitat, ssl);
        final SSLEngineConfigurator clientConfig = new SSLConfigurator(habitat, ssl);
        clientConfig.setClientMode(true);

        filterChainBuilder.add(new SSLFilter(serverConfig,
                                             clientConfig,
                                             isRenegotiateOnClientAuthWant(ssl)));
    }

    private static boolean isRenegotiateOnClientAuthWant(final Ssl ssl) {
        return ssl == null || Boolean.parseBoolean(ssl.getRenegotiateOnClientAuthWant());
    }

    @SuppressWarnings({"unchecked"})
    private static boolean configureElement(Habitat habitat,
            NetworkListener networkListener, ConfigBeanProxy configuration,
            Object instance) {
        
        if (instance instanceof ConfigAwareElement) {
            ((ConfigAwareElement) instance).configure(habitat, networkListener,
                    configuration);
            
            return true;
        }
        
        return false;
    }

    protected void configureThreadPool(final Habitat habitat,
            final NetworkListener networkListener,
            final ThreadPool threadPool) {
        
        final String classname = threadPool.getClassname();
        if (classname != null) {
            // Use custom thread pool
            try {
                final ExecutorService customThreadPool =
                        Utils.newInstance(habitat,
                        ExecutorService.class, classname, classname);
                
                if (customThreadPool != null) {
                    if (!configureElement(habitat, networkListener,
                            threadPool, customThreadPool)) {
                        logger.log(Level.INFO,
                                "The ThreadPool configuration bean can not be "
                                + "passed to the custom thread-pool: {0}" +
                                " instance, because it's not {1}.",
                                new Object[] {
                                classname, ConfigAwareElement.class.getName()});
                    }
                    
                    transport.setWorkerThreadPool(customThreadPool);
                    return;
                }
                
                logger.log(Level.WARNING,
                        "Can not initalize custom thread pool: {0}", classname);
                
            } catch (Throwable t) {
                logger.log(Level.WARNING,
                        "Can not initalize custom thread pool: " + classname, t);
            }
        }
            
        try {
            // Use standard Grizzly thread pool
            transport.setWorkerThreadPool(GrizzlyExecutorService.createInstance(
                configureThreadPoolConfig(habitat, networkListener, threadPool)));
        } catch (NumberFormatException ex) {
            logger.log(Level.WARNING, "Invalid thread-pool attribute", ex);
        }
    }

    protected ThreadPoolConfig configureThreadPoolConfig(final Habitat habitat,
            final NetworkListener networkListener,
            final ThreadPool threadPool) {

        final int maxQueueSize = threadPool.getMaxQueueSize() == null ? Integer.MAX_VALUE
            : Integer.parseInt(threadPool.getMaxQueueSize());
        final int minThreads = Integer.parseInt(threadPool.getMinThreadPoolSize());
        final int maxThreads = Integer.parseInt(threadPool.getMaxThreadPoolSize());
        final int timeout = Integer.parseInt(threadPool.getIdleThreadTimeoutSeconds());
        final ThreadPoolConfig poolConfig = ThreadPoolConfig.newConfig();
        poolConfig.setPoolName(networkListener.getName());
        poolConfig.setCorePoolSize(minThreads);
        poolConfig.setMaxPoolSize(maxThreads);
        poolConfig.setQueueLimit(maxQueueSize);
        poolConfig.setKeepAliveTime(timeout < 0 ? Long.MAX_VALUE : timeout, TimeUnit.SECONDS);
        if (transactionTimeoutMillis > 0 && !Utils.isDebugVM()) {
            poolConfig.setTransactionTimeout(delayedExecutor,
                    transactionTimeoutMillis, TimeUnit.MILLISECONDS);
        }
        
        return poolConfig;
    }

    protected void configureDelayedExecutor(Habitat habitat) {
        final AtomicInteger threadCounter = new AtomicInteger();
        auxExecutorService = Executors.newCachedThreadPool(
            new ThreadFactory() {
                @Override
                public Thread newThread(Runnable r) {
                    final Thread newThread = new DefaultWorkerThread(
                        transport.getAttributeBuilder(),
                        getName() + '-' + threadCounter.getAndIncrement(),
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

    @SuppressWarnings({"deprecation"})
    protected void configureHttpProtocol(final Habitat habitat,
            final NetworkListener networkListener,
            final Http http, final FilterChainBuilder filterChainBuilder) {
        transactionTimeoutMillis = Integer.parseInt(http.getRequestTimeoutSeconds()) * 1000;
        filterChainBuilder.add(new IdleTimeoutFilter(delayedExecutor, Integer.parseInt(http.getTimeoutSeconds()),
            TimeUnit.SECONDS));
        final org.glassfish.grizzly.http.HttpServerFilter httpServerFilter =
            new org.glassfish.grizzly.http.HttpServerFilter(
                Boolean.parseBoolean(http.getChunkingEnabled()),
                Integer.parseInt(http.getHeaderBufferLengthBytes()),
                http.getForcedResponseType(),
                configureKeepAlive(habitat, http),
                delayedExecutor);
        final Set<ContentEncoding> contentEncodings =
            configureContentEncodings(habitat, http);
        for (ContentEncoding contentEncoding : contentEncodings) {
            httpServerFilter.addContentEncoding(contentEncoding);
        }
        //httpServerFilter.addContentEncoding(new GZipContentEncoding());
        final boolean isRcmSupportEnabled = Boolean.parseBoolean(http.getRcmSupportEnabled());
        if (isRcmSupportEnabled) {
            filterChainBuilder.add(new ResourceAllocationFilter());
        }
//        httpServerFilter.getMonitoringConfig().addProbes(
//                serverConfig.getMonitoringConfig().getHttpConfig().getProbes());
        filterChainBuilder.add(httpServerFilter);
        final FileCache fileCache = configureHttpFileCache(habitat, http.getFileCache());
        fileCache.initialize(transport.getMemoryManager(), delayedExecutor);
        final FileCacheFilter fileCacheFilter = new FileCacheFilter(fileCache);
//        fileCache.getMonitoringConfig().addProbes(
//                serverConfig.getMonitoringConfig().getFileCacheConfig().getProbes());
        filterChainBuilder.add(fileCacheFilter);
        final HttpServerFilter webServerFilter = new HttpServerFilter(getHttpServerFilterConfiguration(http),
            delayedExecutor);

        final HttpHandler httpHandler = getHttpHandler(http);
        httpHandler.setAllowEncodedSlash(GrizzlyConfig.toBoolean(http.getEncodedSlashEnabled()));
        webServerFilter.setHttpHandler(httpHandler);
//        webServerFilter.getMonitoringConfig().addProbes(
//                serverConfig.getMonitoringConfig().getWebServerConfig().getProbes());
        filterChainBuilder.add(webServerFilter);

        configureCometSupport(habitat, networkListener, http, filterChainBuilder);

        configureWebSocketSupport(habitat, networkListener, http, filterChainBuilder);

        configureAjpSupport(habitat, networkListener, http, filterChainBuilder);
    }

    protected void configureCometSupport(final Habitat habitat,
            final NetworkListener networkListener,
            final Http http, final FilterChainBuilder filterChainBuilder) {

        if(GrizzlyConfig.toBoolean(http.getCometSupportEnabled())) {
            final AddOn cometAddOn = loadAddOn(habitat, "comet",
                    "org.glassfish.grizzly.comet.CometAddOn");
            if (cometAddOn != null) {
                configureElement(habitat, networkListener, http, cometAddOn);
                cometAddOn.setup(null, filterChainBuilder);
            }
        }
    }

    protected void configureWebSocketSupport(final Habitat habitat,
            final NetworkListener networkListener,
            final Http http, final FilterChainBuilder filterChainBuilder) {
        final boolean websocketsSupportEnabled = Boolean.parseBoolean(http.getWebsocketsSupportEnabled());
        if (websocketsSupportEnabled) {
            final long timeoutSeconds = Integer.parseInt(http.getWebsocketsTimeoutSeconds());
            Filter f;
            if (timeoutSeconds != Http.WEBSOCKETS_TIMEOUT) {
                f = loadFilter(habitat,
                               "websockets",
                               "org.glassfish.grizzly.websockets.WebSocketFilter",
                               new Class<?>[] { Integer.TYPE },
                               new Object[] { timeoutSeconds });
                
            } else {
                f = loadFilter(habitat, "websockets", "org.glassfish.grizzly.websockets.WebSocketFilter");
            }
            final int httpServerFilterIdx = filterChainBuilder.indexOfType(HttpServerFilter.class);

            if (httpServerFilterIdx >= 0) {
                // Insert the WebSocketFilter right after HttpCodecFilter
                filterChainBuilder.add(httpServerFilterIdx, f);
            }
        }
    }

    protected void configureAjpSupport(final Habitat habitat,
            final NetworkListener networkListener,
            final Http http, final FilterChainBuilder filterChainBuilder) {

        final boolean jkSupportEnabled = http.getJkEnabled() != null ?
            Boolean.parseBoolean(http.getJkEnabled()) :
            Boolean.parseBoolean(networkListener.getJkEnabled());

        if (jkSupportEnabled) {
            final AddOn ajpAddOn = loadAddOn(habitat, "ajp",
                    "org.glassfish.grizzly.http.ajp.AjpAddOn");
            if (ajpAddOn != null) {
                configureElement(habitat, networkListener, http, ajpAddOn);
                ajpAddOn.setup(null, filterChainBuilder);
            }
        }
    }

    
    /**
     * Load {@link AddOn} with the specific service name and classname.
     */
    private AddOn loadAddOn(Habitat habitat, String name, String addOnClassName) {
        return Utils.newInstance(habitat, AddOn.class, name, addOnClassName);
    }

    /**
     * Load {@link Filter} with the specific service name and classname.
     */
    private Filter loadFilter(Habitat habitat, String name, String filterClassName) {
        return Utils.newInstance(habitat, Filter.class, name, filterClassName);
    }
    
    private Filter loadFilter(Habitat habitat,
                              String name, 
                              String filterClassName, 
                              Class<?>[] ctorArgTypes, 
                              Object[] ctorArgs) {
        return Utils.newInstance(habitat, Filter.class, name, filterClassName, ctorArgTypes, ctorArgs);
    }

    protected ServerFilterConfiguration getHttpServerFilterConfiguration(Http http) {
        final ServerFilterConfiguration serverFilterConfiguration =
                new ServerFilterConfiguration();
        serverFilterConfiguration.setScheme(http.getScheme());
        serverFilterConfiguration.setTraceEnabled(Boolean.valueOf(http.getTraceEnabled()));
        return serverFilterConfiguration;
    }

    protected HttpHandler getHttpHandler(Http http) {
        return new StaticHttpHandler(".");
    }

    /**
     * Configure the Grizzly HTTP FileCache mechanism
     */
    protected FileCache configureHttpFileCache(Habitat habitat,
        org.glassfish.grizzly.config.dom.FileCache cache) {
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

    protected KeepAlive configureKeepAlive(Habitat habitat, Http http) {
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

    protected Set<ContentEncoding> configureContentEncodings(
        Habitat habitat, Http http) {
        return configureCompressionEncodings(habitat, http);
    }

    protected Set<ContentEncoding> configureCompressionEncodings(Habitat habitat, Http http) {
        final String mode = http.getCompression();
        int compressionMinSize = Integer.parseInt(http.getCompressionMinSizeBytes());
        CompressionLevel compressionLevel;
        try {
            compressionLevel = CompressionLevel.getCompressionLevel(mode);
        } catch (IllegalArgumentException e) {
            try {
                // Try to parse compression as an int, which would give the
                // minimum compression size
                compressionLevel = CompressionLevel.ON;
                compressionMinSize = Integer.parseInt(mode);
            } catch (Exception ignore) {
                compressionLevel = CompressionLevel.OFF;
            }
        }
        final String compressableMimeTypesString = http.getCompressableMimeType();
        final String noCompressionUserAgentsString = http.getNoCompressionUserAgents();
        final String[] compressableMimeTypes = 
                ((compressableMimeTypesString != null) 
                        ? compressableMimeTypesString.split(",") 
                        : new String[0]);
        final String[] noCompressionUserAgents = 
                ((noCompressionUserAgentsString != null) 
                        ? noCompressionUserAgentsString.split(",") 
                        : new String[0]);
        final ContentEncoding gzipContentEncoding = new GZipContentEncoding(
            GZipContentEncoding.DEFAULT_IN_BUFFER_SIZE,
            GZipContentEncoding.DEFAULT_OUT_BUFFER_SIZE,
            new CompressionEncodingFilter(compressionLevel, compressionMinSize,
                compressableMimeTypes,
                noCompressionUserAgents,
                GZipContentEncoding.getGzipAliases()));
        final ContentEncoding lzmaEncoding = new LZMAContentEncoding(new CompressionEncodingFilter(compressionLevel, compressionMinSize,
                compressableMimeTypes,
                noCompressionUserAgents,
                LZMAContentEncoding.getLzmaAliases()));
        final Set<ContentEncoding> set = new HashSet<ContentEncoding>(2);
        set.add(gzipContentEncoding);
        set.add(lzmaEncoding);
        return set;
    }

    @SuppressWarnings("unchecked")
    private static IOStrategy loadIOStrategy(final String classname) {
        Class<? extends IOStrategy> strategy;
        if (classname == null) {
            strategy = WorkerThreadIOStrategy.class;
        } else {
            try {
                strategy = Utils.loadClass(classname);
            } catch (Exception e) {
                strategy = WorkerThreadIOStrategy.class;
            }
        }
        
        try {
            final Method m = strategy.getMethod("getInstance");
            return (IOStrategy) m.invoke(null);
        } catch (Exception e) {
            throw new IllegalStateException("Can not initialize IOStrategy: " + strategy + ". Error: " + e);
        }
    }

    private static Ssl getSsl(Protocol protocol) {
        Ssl ssl = protocol.getSsl();
        if (ssl == null) {
            ssl = (Ssl) DefaultProxy.createDummyProxy(protocol, Ssl.class);
        }
        return ssl;
    }
}
