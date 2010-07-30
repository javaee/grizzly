/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 1997-2010 Sun Microsystems, Inc. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License. You can obtain
 * a copy of the License at https://glassfish.dev.java.net/public/CDDL+GPL.html
 * or glassfish/bootstrap/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/bootstrap/legal/LICENSE.txt.
 * Sun designates this particular file as subject to the "Classpath" exception
 * as provided by Sun in the GPL Version 2 section of the License file that
 * accompanied this code.  If applicable, add the following below the License
 * Header, with the fields enclosed by brackets [] replaced by your own
 * identifying information: "Portions Copyrighted [year]
 * [name of copyright owner]"
 *
 * Contributor(s):
 *
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
 *
 *
 * This file incorporates work covered by the following copyright and
 * permission notice:
 */

package com.sun.grizzly.http.server;

import com.sun.grizzly.Grizzly;
import com.sun.grizzly.MonitoringAware;
import com.sun.grizzly.Processor;
import com.sun.grizzly.Transport;
import com.sun.grizzly.filterchain.FilterChain;
import com.sun.grizzly.filterchain.FilterChainBuilder;
import com.sun.grizzly.http.HttpServerFilter;
import com.sun.grizzly.ssl.SSLContextConfigurator;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.grizzly.TransportFactory;
import com.sun.grizzly.filterchain.TransportFilter;
import com.sun.grizzly.memory.MemoryProbe;
import com.sun.grizzly.ssl.SSLEngineConfigurator;
import com.sun.grizzly.ssl.SSLFilter;
import com.sun.grizzly.threadpool.DefaultWorkerThread;
import com.sun.grizzly.utils.IdleTimeoutFilter;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;


/**
 *
 */
public class GrizzlyWebServer {

    private final Logger LOGGER = Grizzly.logger(GrizzlyWebServer.class);

    /**
     * Configuration details for this server instance.
     */
    private final ServerConfiguration serverConfig = new ServerConfiguration();


    /**
     * Flag indicating whether or not this server instance has been started.
     */
    private boolean started;

    /**
     * GrizzlyAdapter, which processes HTTP requests
     */
    private volatile GrizzlyAdapter adapter;

    /**
     * Mapping of {@link GrizzlyListener}s, by name, used by this server
     *  instance.
     */
    private final Map<String,GrizzlyListener> listeners =
            new HashMap<String,GrizzlyListener>(2);

    private volatile ScheduledExecutorService scheduledExecutorService;


    // ---------------------------------------------------------- Public Methods


    /**
     * @return the {@link ServerConfiguration} used to configure this
     *  {@link GrizzlyWebServer} instance
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
     * @param listener the {@link GrizzlyListener} to associate with this
     *  server instance.
     */
    public void addListener(final GrizzlyListener listener) {

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
     * @param name the {@link GrizzlyListener} name.
     * @return the {@link GrizzlyListener}, if any, associated with the
     *  specified <code>name</code>.
     */
    public GrizzlyListener getListener(final String name) {

        return listeners.get(name);

    }


    /**
     * @return a <code>read only</code> {@link Iterator} over the listeners
     *  associated with this <code>GrizzlyWebServer</code> instance.
     */
    public Iterator<GrizzlyListener> getListeners() {
        return Collections.unmodifiableMap(listeners).values().iterator();
    }


    /**
     * <p>
     * Removes the {@link GrizzlyListener} associated with the specified
     * <code>name</code>.
     * </p>
     *
     * <p>
     * If the server is running when this method is invoked, the listener will
     * be stopped before being returned.
     * </p>
     *
     * @param name the name of the {@link GrizzlyListener} to remove.
     */
    public GrizzlyListener removeListener(final String name) {

        final GrizzlyListener listener = listeners.remove(name);
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


    ScheduledExecutorService getScheduledExecutorService() {
        return scheduledExecutorService;
    }


    /**
     * <p>
     * Starts the <code>GrizzlyWebServer</code>.
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

        adapter = serverConfig.buildAdapter();
        if (adapter != null) {
            adapter.start();
        }

        for (final GrizzlyListener listener : listeners.values()) {
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

        if (LOGGER.isLoggable(Level.INFO)) {
            LOGGER.info("GWS Started.");
        }

    }


    /**
     * @return the {@link GrizzlyAdapter} used by this <code>GrizzlyWebServer</code>
     *  instance.
     */
    public GrizzlyAdapter getAdapter() {
        return adapter;
    }

    /**
     * Enable JMX Management by configuring the {@link Management}
     * @param jmxManagement An instance of the {@link Management} interface
     */
//    public void enableJMX(Management jmxManagement){
//        if (jmxManagement == null) return;
//
//        webFilter.getJmxManager().setManagement(jmxManagement);
//        try {
//            ObjectName sname = new ObjectName(mBeanName);
//            webFilter.getJmxManager().registerComponent(webFilter, sname, null);
//        } catch (Exception ex) {
//            WebFilter.logger().log(Level.SEVERE, "Enabling JMX failed", ex);
//        }
//    }


    /**
     * Return a {@link Statistics} instance that can be used to gather
     * statistics. By default, the {@link Statistics} object <strong>is not</strong>
     * gathering statistics. Invoking {@link Statistics#startGatheringStatistics}
     * will do it.
     */
//    public Statistics getStatistics() {
//        if (statistics == null) {
//            statistics = new Statistics(webFilter);
//        }
//
//        return statistics;
//    }

    /**
     * <p>
     * Stops the <code>GrizzlyWebServer</code> instance.
     * </p>
     */
    public synchronized void stop() {

        if (!started) {
            return;
        }
        started = false;

        try {
            if (adapter != null) {
                adapter.destroy();
                adapter = null;
            }

            final String[] names = listeners.keySet().toArray(new String[listeners.size()]);
            for (final String name : names) {
                removeListener(name);
            }

            stopScheduledThreadPool();
            
            TransportFactory.getInstance().close();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, null, e);
        } finally {
            for (final GrizzlyListener listener : listeners.values()) {
                final Processor p = listener.getTransport().getProcessor();
                if (p instanceof FilterChain) {
                    ((FilterChain) p).clear();
                }
            }
        }

    }


    /**
     * @param path the document root.
     *
     * @return a <code>GrizzlyWebServer</code> configured to listen to requests
     * on {@link GrizzlyListener#DEFAULT_NETWORK_HOST}:{@link GrizzlyListener#DEFAULT_NETWORK_PORT},
     * using the specified <code>path</code> as the server's document root.
     */
    public static GrizzlyWebServer createSimpleServer(final String path) {

        final GrizzlyWebServer server = new GrizzlyWebServer();
        final ServerConfiguration config = server.getServerConfiguration();
        config.setDocRoot(path);
        final GrizzlyListener listener = new GrizzlyListener("grizzly");
        server.addListener(listener);
        return server;

    }


    /**
     * @param path the document root.
     * @param port the network port to which this listener will bind.
     *
     * @return a <code>GrizzlyWebServer</code> configured to listen to requests
     * on {@link GrizzlyListener#DEFAULT_NETWORK_HOST}:<code>port</code>,
     * using the specified <code>path</code> as the server's document root.
     */
    public static GrizzlyWebServer createSimpleServer(final String path, int port) {

        final GrizzlyWebServer server = new GrizzlyWebServer();
        final ServerConfiguration config = server.getServerConfiguration();
        config.setDocRoot(path);
        final GrizzlyListener listener =
                new GrizzlyListener("grizzly",
                                    GrizzlyListener.DEFAULT_NETWORK_HOST,
                                    port);
        server.addListener(listener);
        return server;

    }    

    private void configureListener(final GrizzlyListener listener) {
        FilterChain chain = listener.getFilterChain();
        if (chain == null) {
            final FilterChainBuilder builder = FilterChainBuilder.stateless();
            builder.add(new TransportFilter());
            builder.add(new IdleTimeoutFilter(listener.getKeepAliveTimeoutInSeconds(),
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
                builder.add(new SSLFilter(sslConfig, null));
            }
            final int maxHeaderSize = ((listener.getMaxHttpHeaderSize() == -1)
                                        ? HttpServerFilter.DEFAULT_MAX_HTTP_PACKET_HEADER_SIZE
                                        : listener.getMaxHttpHeaderSize());

            final HttpServerFilter httpServerFilter = new HttpServerFilter(maxHeaderSize);
            httpServerFilter.addProbes(
                    serverConfig.getMonitoringConfig().getHttpConfig().getProbes());
            builder.add(httpServerFilter);

            final FileCacheFilter fileCacheFilter = new FileCacheFilter(this);
            fileCacheFilter.getFileCache().addProbes(
                    serverConfig.getMonitoringConfig().getFileCacheConfig().getProbes());
            builder.add(fileCacheFilter);

            final WebServerFilter webServerFilter = new WebServerFilter(this);
            webServerFilter.addProbes(
                    serverConfig.getMonitoringConfig().getWebServerConfig().getProbes());
            builder.add(webServerFilter);

            chain = builder.build();
            listener.setFilterChain(chain);
        }

        //------ Probes config --------
        final Transport transport = listener.getTransport();
        final MonitoringAware<MemoryProbe> mm = transport.getMemoryManager();

        transport.clearProbes();
        transport.clearConnectionProbes();
        mm.clearProbes();

        transport.addProbes(
                serverConfig.getMonitoringConfig().getTransportConfig().getProbes());
        transport.addConnectionProbes(
                serverConfig.getMonitoringConfig().getConnectionConfig().getProbes());
        mm.addProbes(serverConfig.getMonitoringConfig().getMemoryConfig().getProbes());
    }

    private void configureScheduledThreadPool() {
        final AtomicInteger threadCounter = new AtomicInteger();

        scheduledExecutorService = Executors.newScheduledThreadPool(1,
                new ThreadFactory() {

            @Override
            public Thread newThread(Runnable r) {
                final Thread newThread = new DefaultWorkerThread(
                        TransportFactory.getInstance().getDefaultAttributeBuilder(),
                        "GrizzlyWebServer-" + threadCounter.getAndIncrement(),
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


    // -------------------------------------------------------------------- MAIN

    /**
     * TODO - REMOVE later
     * @param args
     */
    public static void main(String[] args) {

        final GrizzlyWebServer server = GrizzlyWebServer.createSimpleServer("/tmp");
        try {
            server.start();
            System.out.println("Press any key to stop the server...");
            System.in.read();
        } catch (IOException ioe) {
            System.err.println(ioe.toString());
            ioe.printStackTrace();
        } finally {
            server.stop();
        }

    }
}
