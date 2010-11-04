/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2010 Oracle and/or its affiliates. All rights reserved.
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

import org.glassfish.grizzly.ConnectionProbe;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.Processor;
import org.glassfish.grizzly.filterchain.FilterChain;
import org.glassfish.grizzly.filterchain.FilterChainBuilder;
import org.glassfish.grizzly.http.server.filecache.FileCache;
import org.glassfish.grizzly.http.server.jmx.Monitorable;
import org.glassfish.grizzly.http.server.jmx.JmxEventListener;
import org.glassfish.grizzly.monitoring.jmx.GrizzlyJmxManager;
import org.glassfish.grizzly.monitoring.jmx.JmxObject;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import org.glassfish.grizzly.rcm.ResourceAllocationFilter;
import org.glassfish.grizzly.ssl.SSLContextConfigurator;
import java.io.IOException;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.glassfish.grizzly.TransportFactory;
import org.glassfish.grizzly.TransportProbe;
import org.glassfish.grizzly.filterchain.TransportFilter;
import org.glassfish.grizzly.http.ContentEncoding;
import org.glassfish.grizzly.memory.MemoryProbe;
import org.glassfish.grizzly.monitoring.MonitoringConfig;
import org.glassfish.grizzly.ssl.SSLEngineConfigurator;
import org.glassfish.grizzly.ssl.SSLFilter;
import org.glassfish.grizzly.threadpool.DefaultWorkerThread;
import org.glassfish.grizzly.threadpool.ThreadPoolProbe;
import org.glassfish.grizzly.utils.DelayedExecutor;
import org.glassfish.grizzly.utils.SilentConnectionFilter;
import org.glassfish.grizzly.websockets.WebSocketFilter;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;


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
    private boolean started;

    /**
     * HttpService, which processes HTTP requests
     */
    private volatile HttpRequestProcessor httpService;

    /**
     * Mapping of {@link NetworkListener}s, by name, used by this server
     *  instance.
     */
    private final Map<String, NetworkListener> listeners =
            new HashMap<String, NetworkListener>(2);

    private volatile ScheduledExecutorService scheduledExecutorService;

    private volatile ExecutorService auxExecutorService;

    private volatile DelayedExecutor delayedExecutor;

    protected volatile GrizzlyJmxManager jmxManager;

    protected volatile JmxObject managementObject;

    private volatile JmxObject serviceManagementObject;


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
    public void addListener(final NetworkListener listener) {

        if (!started) {
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
    public NetworkListener getListener(final String name) {

        return listeners.get(name);

    }


    /**
     * @return a <code>read only</code> {@link Iterator} over the listeners
     *  associated with this <code>HttpServer</code> instance.
     */
    public Iterator<NetworkListener> getListeners() {
        return Collections.unmodifiableMap(listeners).values().iterator();
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
     */
    public NetworkListener removeListener(final String name) {

        final NetworkListener listener = listeners.remove(name);
        if (listener != null) {
            if (listener.isStarted()) {
                try {
                    listener.stop();
                } catch (IOException ioe) {
                    if (LOGGER.isLoggable(Level.SEVERE)) {
                        LOGGER.log(Level.SEVERE,
                                   "Failed to stop listener [{0}] : {1}",
                                    new Object[] { listener.toString(), ioe.toString() });
                        LOGGER.log(Level.SEVERE, ioe.toString(), ioe);
                    }
                }
            }
        }
        return listener;

    }

    DelayedExecutor getDelayedExecutor() {
        return delayedExecutor;
    }

    ScheduledExecutorService getScheduledExecutorService() {
        return scheduledExecutorService;
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

        if (started) {
            return;
        }
        started = true;

        configureScheduledThreadPool();
        configureAuxThreadPool();

        delayedExecutor = new DelayedExecutor(auxExecutorService);
        delayedExecutor.start();
        
        for (final NetworkListener listener : listeners.values()) {
            configureListener(listener);
            try {
                listener.start();
            } catch (IOException ioe) {
                if (LOGGER.isLoggable(Level.SEVERE)) {
                    LOGGER.log(Level.SEVERE,
                            "Failed to start listener [{0}] : {1}",
                            new Object[]{listener.toString(), ioe.toString()});
                    LOGGER.log(Level.SEVERE, ioe.toString(), ioe);
                }

                throw ioe;
            }
        }

        if (serverConfig.isJmxEnabled()) {
            enableJMX();
        }

        setupService();

        if (serverConfig.isJmxEnabled()) {
            for (Iterator<JmxEventListener> i = serverConfig.getJmxEventListeners(); i.hasNext(); ) {
                final JmxEventListener l = i.next();
                l.jmxEnabled();
            }
        }

        if (LOGGER.isLoggable(Level.INFO)) {
            LOGGER.log(Level.INFO, "[{0}] Started.", getServerConfiguration().getName());
        }

    }

    private void setupService() {

        httpService = serverConfig.buildService();
        if (httpService != null) {
            if (!(httpService instanceof HttpServiceChain)
                    && httpService instanceof Monitorable) {
                final Monitorable monitor = (Monitorable) httpService;
                final JmxObject jmx = monitor.createManagementObject();
                jmxManager.register(managementObject, jmx, jmx.getJmxName());
                serviceManagementObject = jmx;
            }
            httpService.start();
        }

    }


    private void tearDownService() {

        if (serviceManagementObject != null) {
            jmxManager.unregister(serviceManagementObject);
            serviceManagementObject = null;
        }
        if (httpService != null) {
            httpService.destroy();
            httpService = null;
        }

    }


    /**
     * @return the {@link HttpRequestProcessor} used by this <code>HttpServer</code>
     *  instance.
     */
    public HttpRequestProcessor getHttpService() {
        return httpService;
    }


    /**
     * @return <code>true</code> if this <code>HttpServer</code> has
     *  been started.
     */
    public boolean isStarted() {
        return started;
    }


    public JmxObject getManagementObject(boolean clear) {
        if (!clear && managementObject == null) {
            synchronized (serverConfig) {
                if (managementObject == null) {
                    managementObject = new org.glassfish.grizzly.http.server.jmx.HttpServer(this);
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


    /**
     * <p>
     * Stops the <code>HttpServer</code> instance.
     * </p>
     */
    public synchronized void stop() {

        if (!started) {
            return;
        }
        started = false;



        try {

            if (serverConfig.isJmxEnabled()) {
                for (Iterator<JmxEventListener> i = serverConfig.getJmxEventListeners(); i.hasNext();) {
                    final JmxEventListener l = i.next();
                    l.jmxDisabled();
                }
            }

            tearDownService();

            final String[] names = listeners.keySet().toArray(new String[listeners.size()]);
            for (final String name : names) {
                removeListener(name);
            }

            stopScheduledThreadPool();
            
            delayedExecutor.stop();
            delayedExecutor = null;
            
            stopAuxThreadPool();

            if (serverConfig.isJmxEnabled()) {
                disableJMX();
            }
            
            TransportFactory.getInstance().close();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, null, e);
        } finally {
            for (final NetworkListener listener : listeners.values()) {
                final Processor p = listener.getTransport().getProcessor();
                if (p instanceof FilterChain) {
                    ((FilterChain) p).clear();
                }
            }
        }

    }
    

    /**
     * @return a <code>HttpServer</code> configured to listen to requests
     * on {@link NetworkListener#DEFAULT_NETWORK_HOST}:{@link NetworkListener#DEFAULT_NETWORK_PORT},
     * using the directory in which the server was launched the server's document root.
     */
    public static HttpServer createSimpleServer() {

        return createSimpleServer(".");

    }


    /**
     * @param path the document root.
     *
     * @return a <code>HttpServer</code> configured to listen to requests
     * on {@link NetworkListener#DEFAULT_NETWORK_HOST}:{@link NetworkListener#DEFAULT_NETWORK_PORT},
     * using the specified <code>path</code> as the server's document root.
     */
    public static HttpServer createSimpleServer(final String path) {

        return createSimpleServer(path, NetworkListener.DEFAULT_NETWORK_PORT);

    }


    /**
     * @param path the document root.
     * @param port the network port to which this listener will bind.
     *
     * @return a <code>HttpServer</code> configured to listen to requests
     * on {@link NetworkListener#DEFAULT_NETWORK_HOST}:<code>port</code>,
     * using the specified <code>path</code> as the server's document root.
     */
    public static HttpServer createSimpleServer(final String path, int port) {

        final HttpServer server = new HttpServer();
        final ServerConfiguration config = server.getServerConfiguration();
        config.setDocRoot(path);
        final NetworkListener listener =
                new NetworkListener("grizzly",
                                    NetworkListener.DEFAULT_NETWORK_HOST,
                                    port);
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
            jmxManager.unregister(getManagementObject(true));
        }

    }


    // --------------------------------------------------------- Private Methods


    private void configureListener(final NetworkListener listener) {
        FilterChain chain = listener.getFilterChain();
        if (chain == null) {
            final FilterChainBuilder builder = FilterChainBuilder.stateless();
            builder.add(new TransportFilter());
            builder.add(new SilentConnectionFilter(delayedExecutor,
                    listener.getKeepAlive().getIdleTimeoutInSeconds(),
                    TimeUnit.SECONDS));
            if (listener.isSecure()) {
                SSLEngineConfigurator sslConfig = listener.getSslEngineConfig();
                if (sslConfig == null) {
                    sslConfig =
                          new SSLEngineConfigurator(
                                SSLContextConfigurator.DEFAULT_CONFIG.createSSLContext(),
                                false,
                                false,
                                false);
                    listener.setSSLEngineConfig(sslConfig);
                }
                final SSLFilter filter = new SSLFilter(sslConfig, null);
                if (sslConfig.isLazyInit()) {
                    builder.add(new LazyInitSslFilter(filter));
                } else {
                    builder.add(filter);
                }

            }
            final int maxHeaderSize = listener.getMaxHttpHeaderSize() == -1
                                        ? org.glassfish.grizzly.http.HttpServerFilter.DEFAULT_MAX_HTTP_PACKET_HEADER_SIZE
                                        : listener.getMaxHttpHeaderSize();

            final org.glassfish.grizzly.http.HttpServerFilter httpServerFilter =
                    new org.glassfish.grizzly.http.HttpServerFilter(listener.isChunkingEnabled(),
                                         maxHeaderSize,
                                         listener.getKeepAlive(),
                                         delayedExecutor);
            final Set<ContentEncoding> contentEncodings = listener.getContentEncodings();
            for (ContentEncoding contentEncoding : contentEncodings) {
                httpServerFilter.addContentEncoding(contentEncoding);
            }
            if (listener.isRcmSupportEnabled()) {
                builder.add(new ResourceAllocationFilter());
            }

            httpServerFilter.getMonitoringConfig().addProbes(
                    serverConfig.getMonitoringConfig().getHttpConfig().getProbes());
            builder.add(httpServerFilter);

            if (listener.isWebSocketsEnabled()) {
                builder.add(new WebSocketFilter());
            }

            final FileCache fileCache = listener.getFileCache();
            fileCache.initialize(listener.getTransport().getMemoryManager(), delayedExecutor);
            final FileCacheFilter fileCacheFilter = new FileCacheFilter(fileCache);
            fileCache.getMonitoringConfig().addProbes(
                    serverConfig.getMonitoringConfig().getFileCacheConfig().getProbes());
            builder.add(fileCacheFilter);

            final HttpServerFilter webServerFilter = new HttpServerFilter(this);
            webServerFilter.getMonitoringConfig().addProbes(
                    serverConfig.getMonitoringConfig().getWebServerConfig().getProbes());
            builder.add(webServerFilter);

            chain = builder.build();
            listener.setFilterChain(chain);
        }
        configureMonitoring(listener);
    }


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

        transportMonitoringCfg.clearProbes();
        connectionMonitoringCfg.clearProbes();
        memoryMonitoringCfg.clearProbes();
        threadPoolMonitoringCfg.clearProbes();

        transportMonitoringCfg.addProbes(serverConfig.getMonitoringConfig()
                .getTransportConfig().getProbes());
        connectionMonitoringCfg.addProbes(serverConfig.getMonitoringConfig()
                .getConnectionConfig().getProbes());
        memoryMonitoringCfg.addProbes(serverConfig.getMonitoringConfig()
                .getMemoryConfig().getProbes());
        threadPoolMonitoringCfg.addProbes(serverConfig.getMonitoringConfig()
                .getThreadPoolConfig().getProbes());

    }


    private void configureScheduledThreadPool() {
        final AtomicInteger threadCounter = new AtomicInteger();

        scheduledExecutorService = Executors.newScheduledThreadPool(1,
                new ThreadFactory() {

            @Override
            public Thread newThread(Runnable r) {
                final Thread newThread = new DefaultWorkerThread(
                        TransportFactory.getInstance().getDefaultAttributeBuilder(),
                        "HttpServer-" + threadCounter.getAndIncrement(),
                        r);
                newThread.setDaemon(true);
                return newThread;
            }
        });
    }


    private void stopScheduledThreadPool() {
        final ScheduledExecutorService localThreadPool = scheduledExecutorService;
        scheduledExecutorService = null;

        if (localThreadPool != null) {
            localThreadPool.shutdownNow();
        }
    }

    private void configureAuxThreadPool() {
        final AtomicInteger threadCounter = new AtomicInteger();

        auxExecutorService = Executors.newCachedThreadPool(
                new ThreadFactory() {

            @Override
            public Thread newThread(Runnable r) {
                final Thread newThread = new DefaultWorkerThread(
                        TransportFactory.getInstance().getDefaultAttributeBuilder(),
                        "HttpServer-" + threadCounter.getAndIncrement(),
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
}
