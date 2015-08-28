/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2015 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.http.server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.grizzly.CompletionHandler;
import org.glassfish.grizzly.ConnectionProbe;
import org.glassfish.grizzly.EmptyCompletionHandler;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.GrizzlyFuture;
import org.glassfish.grizzly.PortRange;
import org.glassfish.grizzly.Processor;
import org.glassfish.grizzly.Transport;
import org.glassfish.grizzly.TransportProbe;
import org.glassfish.grizzly.attributes.AttributeBuilder;
import org.glassfish.grizzly.filterchain.FilterChain;
import org.glassfish.grizzly.filterchain.FilterChainBuilder;
import org.glassfish.grizzly.filterchain.TransportFilter;
import org.glassfish.grizzly.http.CompressionConfig;
import org.glassfish.grizzly.http.CompressionConfig.CompressionMode;
import org.glassfish.grizzly.http.ContentEncoding;
import org.glassfish.grizzly.http.GZipContentEncoding;
import org.glassfish.grizzly.http.LZMAContentEncoding;
import org.glassfish.grizzly.http.server.filecache.FileCache;
import org.glassfish.grizzly.http.server.jmxbase.JmxEventListener;
import org.glassfish.grizzly.impl.FutureImpl;
import org.glassfish.grizzly.memory.MemoryProbe;
import org.glassfish.grizzly.jmxbase.GrizzlyJmxManager;
import org.glassfish.grizzly.monitoring.MonitoringConfig;
import org.glassfish.grizzly.monitoring.MonitoringUtils;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import org.glassfish.grizzly.ssl.SSLBaseFilter;
import org.glassfish.grizzly.ssl.SSLContextConfigurator;
import org.glassfish.grizzly.ssl.SSLEngineConfigurator;
import org.glassfish.grizzly.threadpool.DefaultWorkerThread;
import org.glassfish.grizzly.threadpool.ThreadPoolConfig;
import org.glassfish.grizzly.threadpool.ThreadPoolProbe;
import org.glassfish.grizzly.utils.DelayedExecutor;
import org.glassfish.grizzly.utils.Futures;
import org.glassfish.grizzly.utils.IdleTimeoutFilter;


/**
 *
 */
public class HttpServer {
    private static final Logger LOGGER = Grizzly.logger(HttpServer.class);

    /**
     * Configuration details for this server instance.
     */
    private final ServerConfiguration serverConfig = new ServerConfiguration(this);


    /**
     * Flag indicating whether or not this server instance has been started.
     */
    private State state = State.STOPPED;

    /**
     * Future to control graceful shutdown status
     */
    private FutureImpl<HttpServer> shutdownFuture;
    
    /**
     * HttpHandler, which processes HTTP requests
     */
    private final HttpHandlerChain httpHandlerChain = new HttpHandlerChain(this);

    /**
     * Mapping of {@link NetworkListener}s, by name, used by this server
     *  instance.
     */
    private final Map<String, NetworkListener> listeners =
            new HashMap<String, NetworkListener>(2);

    private volatile ExecutorService auxExecutorService;

    volatile DelayedExecutor delayedExecutor;

    protected volatile GrizzlyJmxManager jmxManager;

    protected volatile Object managementObject;

    // ---------------------------------------------------------- Public Methods


    /**
     * @return the {@link ServerConfiguration} used to configure this
     *  {@link HttpServer} instance
     */
    public final ServerConfiguration getServerConfiguration() {
        return serverConfig;
    }


    /**
     * <p>
     * Adds the specified <code>listener</code> to the server instance.
     * </p>
     *
     * <p>
     * If the server is already running when this method is called, the listener
     * will be started.
     * </p>
     *
     * @param listener the {@link NetworkListener} to associate with this
     *  server instance.
     */
    public synchronized void addListener(final NetworkListener listener) {

        if (state != State.RUNNING) {
            listeners.put(listener.getName(), listener);
        } else {
            configureListener(listener);
            if (!listener.isStarted()) {
                try {
                    listener.start();
                } catch (IOException ioe) {
                    if (LOGGER.isLoggable(Level.SEVERE)) {
                        LOGGER.log(Level.SEVERE,
                                "Failed to start listener [{0}] : {1}",
                                new Object[] { listener.toString(), ioe.toString() });
                        LOGGER.log(Level.SEVERE, ioe.toString(), ioe);
                    }
                }
            }
        }

    }


    /**
     * @param name the {@link NetworkListener} name.
     * @return the {@link NetworkListener}, if any, associated with the
     *  specified <code>name</code>.
     */
    public synchronized NetworkListener getListener(final String name) {

        return listeners.get(name);

    }


    /**
     * @return a <code>read only</code> {@link Collection} over the listeners
     *  associated with this <code>HttpServer</code> instance.
     */
    public synchronized Collection<NetworkListener> getListeners() {
        return Collections.unmodifiableCollection(listeners.values());
    }


    /**
     * <p>
     * Removes the {@link NetworkListener} associated with the specified
     * <code>name</code>.
     * </p>
     *
     * <p>
     * If the server is running when this method is invoked, the listener will
     * be stopped before being returned.
     * </p>
     *
     * @param name the name of the {@link NetworkListener} to remove.
     * @return {@link NetworkListener}, that has been removed, or <tt>null</tt>
     *      if the listener with the given name doesn't exist
     */
    public synchronized NetworkListener removeListener(final String name) {

        final NetworkListener listener = listeners.remove(name);
        if (listener != null) {
            if (listener.isStarted()) {
                try {
                    listener.shutdownNow();
                } catch (IOException ioe) {
                    if (LOGGER.isLoggable(Level.SEVERE)) {
                        LOGGER.log(Level.SEVERE,
                                   "Failed to shutdown listener [{0}] : {1}",
                                    new Object[] { listener.toString(), ioe.toString() });
                        LOGGER.log(Level.SEVERE, ioe.toString(), ioe);
                    }
                }
            }
        }
        return listener;

    }

    /**
     * <p>
     * Starts the <code>HttpServer</code>.
     * </p>
     *
     * @throws IOException if an error occurs while attempting to start the
     *  server.
     */
    public synchronized void start() throws IOException{

        if (state == State.RUNNING) {
            return;
        } else if (state == State.STOPPING) {
            throw new IllegalStateException("The server is currently in pending"
                    + " shutdown state. You have to either wait for shutdown to"
                    + " complete or force it by calling shutdownNow()");
        }
        state = State.RUNNING;
        shutdownFuture = null;
        
        configureAuxThreadPool();

        delayedExecutor = new DelayedExecutor(auxExecutorService);
        delayedExecutor.start();

        for (final NetworkListener listener : listeners.values()) {
            configureListener(listener);
        }

        if (serverConfig.isJmxEnabled()) {
            enableJMX();
        }

        for (final NetworkListener listener : listeners.values()) {
            try {
                listener.start();
            } catch (IOException ioe) {
                if (LOGGER.isLoggable(Level.FINEST)) {
                    LOGGER.log(Level.FINEST,
                            "Failed to start listener [{0}] : {1}",
                            new Object[]{listener.toString(), ioe.toString()});
                    LOGGER.log(Level.FINEST, ioe.toString(), ioe);
                }

                throw ioe;
            }
        }

        setupHttpHandler();

        if (serverConfig.isJmxEnabled()) {
            for (final JmxEventListener l : serverConfig.getJmxEventListeners()) {
                l.jmxEnabled();
            }
        }

        if (LOGGER.isLoggable(Level.INFO)) {
            LOGGER.log(Level.INFO, "[{0}] Started.", getServerConfiguration().getName());
        }

    }

    private void setupHttpHandler() {

        serverConfig.addJmxEventListener(httpHandlerChain);

        synchronized (serverConfig.handlersSync) {
            for (final HttpHandler httpHandler : serverConfig.orderedHandlers) {
                httpHandlerChain.addHandler(httpHandler,
                        serverConfig.handlers.get(httpHandler));
            }
        }
        httpHandlerChain.start();

    }


    private void tearDownHttpHandler() {

        httpHandlerChain.destroy();

    }


    /**
     * @return the {@link HttpHandler} used by this <code>HttpServer</code>
     *  instance.
     */
    public HttpHandler getHttpHandler() {
        return httpHandlerChain;
    }


    /**
     * @return <code>true</code> if this <code>HttpServer</code> has
     *  been started.
     */
    public boolean isStarted() {
        return state != State.STOPPED;
    }


    public Object getManagementObject(boolean clear) {
        if (!clear && managementObject == null) {
            synchronized (serverConfig) {
                if (managementObject == null) {
                    managementObject = MonitoringUtils.loadJmxObject(
                            "org.glassfish.grizzly.http.server.jmx.HttpServer",
                            this, HttpServer.class);
                }
            }
        }
        try {
            return managementObject;
        } finally {
            if (clear) {
                managementObject = null;
            }
        }
    }

    public synchronized GrizzlyFuture<HttpServer> shutdown(final long gracePeriod,
                                                           final TimeUnit timeUnit) {
        if (state != State.RUNNING) {
            return shutdownFuture != null ? shutdownFuture :
                    Futures.createReadyFuture(this);
        }

        shutdownFuture = Futures.createSafeFuture();
        state = State.STOPPING;

        final int listenersCount = listeners.size();
        final FutureImpl<HttpServer> shutdownFutureLocal = shutdownFuture;

        final CompletionHandler<NetworkListener> shutdownCompletionHandler =
                new EmptyCompletionHandler<NetworkListener>() {
                    final AtomicInteger counter =
                            new AtomicInteger(listenersCount);

                    @Override
                    public void completed(final NetworkListener networkListener) {
                        if (counter.decrementAndGet() == 0) {
                            try {
                                shutdownNow();
                                shutdownFutureLocal.result(HttpServer.this);
                            } catch (Throwable e) {
                                shutdownFutureLocal.failure(e);
                            }
                        }
                    }
                };

        for (NetworkListener listener : listeners.values()) {
            listener.shutdown(gracePeriod, timeUnit).addCompletionHandler(shutdownCompletionHandler);
        }


        return shutdownFuture;
    }

    /**
     * <p>
     * Gracefully shuts down the <code>HttpServer</code> instance.
     * </p>
     */
    public synchronized GrizzlyFuture<HttpServer> shutdown() {
        return shutdown(-1, TimeUnit.MILLISECONDS);
    }
    
    /**
     * <p>
     * Immediately shuts down the <code>HttpServer</code> instance.
     * </p>
     */
    public synchronized void shutdownNow() {

        if (state == State.STOPPED) {
            return;
        }
        state = State.STOPPED;

        try {

            if (serverConfig.isJmxEnabled()) {
                for (final JmxEventListener l : serverConfig.getJmxEventListeners()) {
                    l.jmxDisabled();
                }
            }

            tearDownHttpHandler();

            final String[] names = listeners.keySet().toArray(new String[listeners.size()]);
            for (final String name : names) {
                removeListener(name);
            }

            delayedExecutor.stop();
            delayedExecutor.destroy();
            delayedExecutor = null;
            
            stopAuxThreadPool();

            if (serverConfig.isJmxEnabled()) {
                disableJMX();
            }
            
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, null, e);
        } finally {
            for (final NetworkListener listener : listeners.values()) {
                final Processor p = listener.getTransport().getProcessor();
                if (p instanceof FilterChain) {
                    ((FilterChain) p).clear();
                }
            }
            
            if (shutdownFuture != null) {
                shutdownFuture.result(this);
            }
        }

    }
    
    /**
     * <p>
     * Immediately shuts down the <code>HttpServer</code> instance.
     * </p>
     * 
     * @deprecated use {@link #shutdownNow()}
     */
    public void stop() {
        shutdownNow();
    }
    
    /**
     * @return a <code>HttpServer</code> configured to listen to requests
     * on {@link NetworkListener#DEFAULT_NETWORK_HOST}:{@link NetworkListener#DEFAULT_NETWORK_PORT},
     * using the directory in which the server was launched the server's document root
     */
    public static HttpServer createSimpleServer() {

        return createSimpleServer(".");

    }


    /**
     * @param docRoot the document root,
     *   can be <code>null</code> when no static pages are needed
     *
     * @return a <code>HttpServer</code> configured to listen to requests
     * on {@link NetworkListener#DEFAULT_NETWORK_HOST}:{@link NetworkListener#DEFAULT_NETWORK_PORT},
     * using the specified <code>docRoot</code> as the server's document root
     */
    public static HttpServer createSimpleServer(final String docRoot) {

        return createSimpleServer(docRoot, NetworkListener.DEFAULT_NETWORK_PORT);

    }


    /**
     * @param docRoot the document root,
     *   can be <code>null</code> when no static pages are needed
     * @param port the network port to which this listener will bind
     *
     * @return a <code>HttpServer</code> configured to listen to requests
     * on {@link NetworkListener#DEFAULT_NETWORK_HOST}:<code>port</code>,
     * using the specified <code>docRoot</code> as the server's document root
     */
    public static HttpServer createSimpleServer(final String docRoot,
                                                final int port) {

        return createSimpleServer(docRoot, NetworkListener.DEFAULT_NETWORK_HOST, port);

    }


    /**
     * @param docRoot the document root,
     *   can be <code>null</code> when no static pages are needed
     * @param range port range to attempt to bind to
     *
     * @return a <code>HttpServer</code> configured to listen to requests
     * on {@link NetworkListener#DEFAULT_NETWORK_HOST}:<code>[port-range]</code>,
     * using the specified <code>docRoot</code> as the server's document root
     */
    public static HttpServer createSimpleServer(final String docRoot,
                                                final PortRange range) {

        return createSimpleServer(docRoot,
                NetworkListener.DEFAULT_NETWORK_HOST,
                range);

    }

    /**
     * @param docRoot the document root,
     *   can be <code>null</code> when no static pages are needed
     * @param socketAddress the endpoint address to which this listener will bind
     *
     * @return a <code>HttpServer</code> configured to listen to requests
     * on <code>socketAddress</code>,
     * using the specified <code>docRoot</code> as the server's document root
     */
    public static HttpServer createSimpleServer(final String docRoot,
                                                final SocketAddress socketAddress) {

        final InetSocketAddress inetAddr = (InetSocketAddress) socketAddress;
        return createSimpleServer(docRoot, inetAddr.getHostName(), inetAddr.getPort());
    }

    /**
     * @param docRoot the document root,
     *   can be <code>null</code> when no static pages are needed
     * @param host the network port to which this listener will bind
     * @param port the network port to which this listener will bind
     *
     * @return a <code>HttpServer</code> configured to listen to requests
     * on <code>host</code>:<code>port</code>,
     * using the specified <code>docRoot</code> as the server's document root
     */
    public static HttpServer createSimpleServer(final String docRoot,
                                                final String host,
                                                final int port) {
        
        return createSimpleServer(docRoot, host, new PortRange(port));

    }
    
    /**
     * @param docRoot the document root,
     *   can be <code>null</code> when no static pages are needed
     * @param host the network port to which this listener will bind
     * @param range port range to attempt to bind to
     *
     * @return a <code>HttpServer</code> configured to listen to requests
     * on <code>host</code>:<code>[port-range]</code>,
     * using the specified <code>docRoot</code> as the server's document root
     */
    public static HttpServer createSimpleServer(final String docRoot,
                                                final String host,
                                                final PortRange range) {

        final HttpServer server = new HttpServer();
        final ServerConfiguration config = server.getServerConfiguration();
        if (docRoot != null) {
            config.addHttpHandler(new StaticHttpHandler(docRoot), "/");
        }
        final NetworkListener listener =
                new NetworkListener("grizzly",
                                    host,
                                    range);
        server.addListener(listener);
        return server;

    }
    
    // ------------------------------------------------------- Protected Methods


    protected void enableJMX() {

        if (jmxManager == null) {
            synchronized (serverConfig) {
                if (jmxManager == null) {
                    jmxManager= GrizzlyJmxManager.instance();
                }
            }
        }
        jmxManager.registerAtRoot(getManagementObject(false),
                                  serverConfig.getName());

    }


    protected void disableJMX() {

        if (jmxManager != null) {
            jmxManager.deregister(getManagementObject(true));
        }

    }


    // --------------------------------------------------------- Private Methods


    private void configureListener(final NetworkListener listener) {
        FilterChain chain = listener.getFilterChain();
        if (chain == null) {
            final FilterChainBuilder builder = FilterChainBuilder.stateless();
            builder.add(new TransportFilter());
            if (listener.isSecure()) {
                SSLEngineConfigurator sslConfig = listener.getSslEngineConfig();
                if (sslConfig == null) {
                    sslConfig = new SSLEngineConfigurator(
                            SSLContextConfigurator.DEFAULT_CONFIG,
                            false,
                            false,
                            false);
                    listener.setSSLEngineConfig(sslConfig);
                }
                final SSLBaseFilter filter = new SSLBaseFilter(sslConfig);
                builder.add(filter);

            }
            final int maxHeaderSize = listener.getMaxHttpHeaderSize() == -1
                                        ? org.glassfish.grizzly.http.HttpServerFilter.DEFAULT_MAX_HTTP_PACKET_HEADER_SIZE
                                        : listener.getMaxHttpHeaderSize();

            // Passing null value for the delayed executor, because IdleTimeoutFilter should
            // handle idle connections for us
            final org.glassfish.grizzly.http.HttpServerFilter httpServerCodecFilter =
                    new org.glassfish.grizzly.http.HttpServerFilter(listener.isChunkingEnabled(),
                                         maxHeaderSize,
                                         null,
                                         listener.getKeepAlive(),
                                         null,
                                         listener.getMaxRequestHeaders(),
                                         listener.getMaxResponseHeaders());
            final Set<ContentEncoding> contentEncodings =
                    configureCompressionEncodings(listener);
            for (ContentEncoding contentEncoding : contentEncodings) {
                httpServerCodecFilter.addContentEncoding(contentEncoding);
            }
            httpServerCodecFilter.setAllowPayloadForUndefinedHttpMethods(
                    serverConfig.isAllowPayloadForUndefinedHttpMethods());
            httpServerCodecFilter.setMaxPayloadRemainderToSkip(
                    serverConfig.getMaxPayloadRemainderToSkip());
            
            httpServerCodecFilter.getMonitoringConfig().addProbes(
                    serverConfig.getMonitoringConfig().getHttpConfig().getProbes());
            builder.add(httpServerCodecFilter);
            
            builder.add(new IdleTimeoutFilter(delayedExecutor,
                    listener.getKeepAlive().getIdleTimeoutInSeconds(),
                    TimeUnit.SECONDS));
            
            final Transport transport = listener.getTransport();
            final FileCache fileCache = listener.getFileCache();
            fileCache.initialize(delayedExecutor);
            final FileCacheFilter fileCacheFilter = new FileCacheFilter(fileCache);
            fileCache.getMonitoringConfig().addProbes(
                    serverConfig.getMonitoringConfig().getFileCacheConfig().getProbes());
            builder.add(fileCacheFilter);

            final ServerFilterConfiguration config = new ServerFilterConfiguration(serverConfig);

            if (listener.isSendFileExplicitlyConfigured()) {
                config.setSendFileEnabled(listener.isSendFileEnabled());
                fileCache.setFileSendEnabled(listener.isSendFileEnabled());
            }
            
            if (listener.getBackendConfiguration() != null) {
                config.setBackendConfiguration(listener.getBackendConfiguration());
            }
            
            if (listener.getDefaultErrorPageGenerator() != null) {
                config.setDefaultErrorPageGenerator(listener.getDefaultErrorPageGenerator());
            }
            
            if (listener.getSessionManager() != null) {
                config.setSessionManager(listener.getSessionManager());
            }

            config.setTraceEnabled(config.isTraceEnabled() || listener.isTraceEnabled());
            
            config.setMaxFormPostSize(listener.getMaxFormPostSize());
            config.setMaxBufferedPostSize(listener.getMaxBufferedPostSize());
            
            final HttpServerFilter httpServerFilter = new HttpServerFilter(
                    config,
                    delayedExecutor);
            httpServerFilter.setHttpHandler(httpHandlerChain);
            
            httpServerFilter.getMonitoringConfig().addProbes(
                    serverConfig.getMonitoringConfig().getWebServerConfig().getProbes());

            builder.add(httpServerFilter);

            final AddOn[] addons = listener.getAddOnSet().getArray();
            if (addons != null) {
                for (AddOn addon : addons) {
                    addon.setup(listener, builder);
                }
            }

            chain = builder.build();
            listener.setFilterChain(chain);

            final int transactionTimeout = listener.getTransactionTimeout();
            if (transactionTimeout >= 0) {
                ThreadPoolConfig threadPoolConfig = transport.getWorkerThreadPoolConfig();

                if (threadPoolConfig != null) {
                    threadPoolConfig.setTransactionTimeout(
                            delayedExecutor,
                            transactionTimeout,
                            TimeUnit.SECONDS);
                }

            }

        }
        configureMonitoring(listener);
    }

    protected Set<ContentEncoding> configureCompressionEncodings(
            final NetworkListener listener) {
        
        final CompressionConfig compressionConfig = listener.getCompressionConfig();
        
        if (compressionConfig.getCompressionMode() != CompressionMode.OFF) {
            final ContentEncoding gzipContentEncoding = new GZipContentEncoding(
                GZipContentEncoding.DEFAULT_IN_BUFFER_SIZE,
                GZipContentEncoding.DEFAULT_OUT_BUFFER_SIZE,
                new CompressionEncodingFilter(compressionConfig,
                    GZipContentEncoding.getGzipAliases()));
            final ContentEncoding lzmaEncoding = new LZMAContentEncoding(
                    new CompressionEncodingFilter(compressionConfig,
                    LZMAContentEncoding.getLzmaAliases()));
            final Set<ContentEncoding> set = new HashSet<ContentEncoding>(2);
            set.add(gzipContentEncoding);
            set.add(lzmaEncoding);
            return set;
        } else {
            return Collections.emptySet();
        }
    }

    @SuppressWarnings("unchecked")
    private void configureMonitoring(final NetworkListener listener) {
        final TCPNIOTransport transport = listener.getTransport();

        final MonitoringConfig<TransportProbe> transportMonitoringCfg =
                transport.getMonitoringConfig();
        final MonitoringConfig<ConnectionProbe> connectionMonitoringCfg =
                transport.getConnectionMonitoringConfig();
        final MonitoringConfig<MemoryProbe> memoryMonitoringCfg =
                transport.getMemoryManager().getMonitoringConfig();
        final MonitoringConfig<ThreadPoolProbe> threadPoolMonitoringCfg =
                transport.getThreadPoolMonitoringConfig();

        transportMonitoringCfg.addProbes(serverConfig.getMonitoringConfig()
                .getTransportConfig().getProbes());
        connectionMonitoringCfg.addProbes(serverConfig.getMonitoringConfig()
                .getConnectionConfig().getProbes());
        memoryMonitoringCfg.addProbes(serverConfig.getMonitoringConfig()
                .getMemoryConfig().getProbes());
        threadPoolMonitoringCfg.addProbes(serverConfig.getMonitoringConfig()
                .getThreadPoolConfig().getProbes());

    }

    private void configureAuxThreadPool() {
        final AtomicInteger threadCounter = new AtomicInteger();

        auxExecutorService = Executors.newCachedThreadPool(
                new ThreadFactory() {

            @Override
            public Thread newThread(Runnable r) {
                final Thread newThread = new DefaultWorkerThread(
                        AttributeBuilder.DEFAULT_ATTRIBUTE_BUILDER,
                        serverConfig.getName() + "-" + threadCounter.getAndIncrement(),
                        null,
                        r);
                newThread.setDaemon(true);
                return newThread;
            }
        });
    }


    private void stopAuxThreadPool() {
        final ExecutorService localThreadPool = auxExecutorService;
        auxExecutorService = null;

        if (localThreadPool != null) {
            localThreadPool.shutdownNow();
        }
    }

    //************ Runtime config change listeners ******************

    /**
     * Modifies handlers mapping during runtime.
     */
    synchronized void onAddHttpHandler(HttpHandler httpHandler,
            final HttpHandlerRegistration[] registrations) {
        if (isStarted()) {
            httpHandlerChain.addHandler(httpHandler, registrations);
        }
    }

    /**
     * Modifies handlers mapping during runtime.
     */
    synchronized void onRemoveHttpHandler(HttpHandler httpHandler) {
        if (isStarted()) {
            httpHandlerChain.removeHttpHandler(httpHandler);
        }
    }

}
