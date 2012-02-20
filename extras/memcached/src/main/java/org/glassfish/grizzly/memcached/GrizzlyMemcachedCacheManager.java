/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.memcached;

import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.IOStrategy;
import org.glassfish.grizzly.filterchain.FilterChainBuilder;
import org.glassfish.grizzly.filterchain.TransportFilter;
import org.glassfish.grizzly.memcached.zookeeper.ZKClient;
import org.glassfish.grizzly.memcached.zookeeper.ZooKeeperConfig;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import org.glassfish.grizzly.nio.transport.TCPNIOTransportBuilder;
import org.glassfish.grizzly.strategies.SameThreadIOStrategy;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The implementation of the {@link CacheManager} based on Grizzly
 * <p/>
 * This cache manager has a key(String cache name)/value({@link GrizzlyMemcachedCache} map for retrieving caches.
 * If the specific {@link TCPNIOTransport GrizzlyTransport} is not set at creation time, this will create a main GrizzlyTransport.
 * The {@link TCPNIOTransport GrizzlyTransport} must contain {@link MemcachedClientFilter}.
 *
 * @author Bongjae Chang
 */
public class GrizzlyMemcachedCacheManager implements CacheManager {

    private static final Logger logger = Grizzly.logger(GrizzlyMemcachedCacheManager.class);

    private final ConcurrentHashMap<String, GrizzlyMemcachedCache<?, ?>> caches =
            new ConcurrentHashMap<String, GrizzlyMemcachedCache<?, ?>>();
    private final TCPNIOTransport transport;
    private final boolean isExternalTransport;
    private final AtomicBoolean shutdown = new AtomicBoolean(false);

    private ZKClient zkClient;

    private GrizzlyMemcachedCacheManager(final Builder builder) {
        TCPNIOTransport transportLocal = builder.transport;
        if (transportLocal == null) {
            isExternalTransport = false;
            final FilterChainBuilder clientFilterChainBuilder = FilterChainBuilder.stateless();
            clientFilterChainBuilder.add(new TransportFilter()).add(new MemcachedClientFilter(true, true));
            final TCPNIOTransportBuilder clientTCPNIOTransportBuilder = TCPNIOTransportBuilder.newInstance();
            transportLocal = clientTCPNIOTransportBuilder.build();
            transportLocal.setProcessor(clientFilterChainBuilder.build());
            transportLocal.setSelectorRunnersCount(builder.selectorRunnersCount);
            transportLocal.setIOStrategy(builder.ioStrategy);
            transportLocal.configureBlocking(builder.blocking);
            if (builder.workerThreadPool != null) {
                transportLocal.setWorkerThreadPool(builder.workerThreadPool);
            }
            try {
                transportLocal.start();
            } catch (IOException ie) {
                if (logger.isLoggable(Level.SEVERE)) {
                    logger.log(Level.SEVERE, "failed to start the transport", ie);
                }
            }
        } else {
            isExternalTransport = true;
        }
        this.transport = transportLocal;
        if (builder.zooKeeperConfig != null) {
            final ZKClient.Builder zkBuilder = new ZKClient.Builder(builder.zooKeeperConfig.getName(),
                    builder.zooKeeperConfig.getZooKeeperServerList());
            zkBuilder.rootPath(builder.zooKeeperConfig.getRootPath());
            zkBuilder.connectTimeoutInMillis(builder.zooKeeperConfig.getConnectTimeoutInMillis());
            zkBuilder.sessionTimeoutInMillis(builder.zooKeeperConfig.getSessionTimeoutInMillis());
            zkBuilder.commitDelayTimeInSecs(builder.zooKeeperConfig.getCommitDelayTimeInSecs());
            this.zkClient = zkBuilder.build();
            try {
                this.zkClient.connect();
            } catch (IOException ie) {
                if (logger.isLoggable(Level.SEVERE)) {
                    logger.log(Level.SEVERE, "failed to connect the zookeeper server. zkClient=" + this.zkClient, ie);
                }
                this.zkClient = null;
            } catch (InterruptedException ie) {
                if (logger.isLoggable(Level.SEVERE)) {
                    logger.log(Level.SEVERE, "failed to connect the zookeeper server. zkClient=" + this.zkClient, ie);
                }
                Thread.currentThread().interrupt();
                this.zkClient = null;
            }
        } else {
            this.zkClient = null;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <K, V> GrizzlyMemcachedCache.Builder<K, V> createCacheBuilder(final String cacheName) {
        return new GrizzlyMemcachedCache.Builder<K, V>(cacheName, this, transport);
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("unchecked")
    @Override
    public <K, V> GrizzlyMemcachedCache<K, V> getCache(final String cacheName) {
        if (shutdown.get()) {
            return null;
        }
        return cacheName != null ? (GrizzlyMemcachedCache<K, V>) caches.get(cacheName) : null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean removeCache(final String cacheName) {
        if (shutdown.get()) {
            return false;
        }
        if (cacheName == null)
            return false;
        final GrizzlyMemcachedCache cache = caches.remove(cacheName);
        if (cache == null) {
            return false;
        }
        cache.stop();
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void shutdown() {
        if (!shutdown.compareAndSet(false, true)) {
            return;
        }
        for (MemcachedCache cache : caches.values()) {
            cache.stop();
        }
        caches.clear();
        if (!isExternalTransport && transport != null) {
            try {
                transport.stop();
            } catch (IOException ie) {
                if (logger.isLoggable(Level.INFO)) {
                    logger.log(Level.INFO, "failed to stop the transport", ie);
                }
            }
        }
        if (zkClient != null) {
            zkClient.shutdown();
        }
    }

    /**
     * Add the given {@code cache} to this cache manager
     * <p/>
     * If this returns false, the given {@code cache} should be stopped by caller.
     * Currently, this method is called by only {@link org.glassfish.grizzly.memcached.GrizzlyMemcachedCache.Builder#build()}.
     *
     * @param cache a cache instance
     * @return true if the cache was added
     */
    <K, V> boolean addCache(final GrizzlyMemcachedCache<K, V> cache) {
        return !shutdown.get() &&
                cache != null && caches.putIfAbsent(cache.getName(), cache) == null &&
                !(shutdown.get() && caches.remove(cache.getName()) == cache);
    }

    ZKClient getZkClient() {
        return zkClient;
    }

    public static class Builder {

        private TCPNIOTransport transport;

        // grizzly config
        private int selectorRunnersCount = Runtime.getRuntime().availableProcessors() * 2;
        private IOStrategy ioStrategy = SameThreadIOStrategy.getInstance();
        private boolean blocking = false;
        private ExecutorService workerThreadPool;

        // zookeeper config
        private ZooKeeperConfig zooKeeperConfig;

        /**
         * Set the specific {@link TCPNIOTransport GrizzlyTransport}
         * <p/>
         * If this is not set or set to be null, {@link GrizzlyMemcachedCacheManager} will create a default transport.
         * The given {@code transport} must be always started state if it is not null.
         * Default is null.
         *
         * @param transport the specific Grizzly's {@link TCPNIOTransport}
         * @return this builder
         */
        public Builder transport(final TCPNIOTransport transport) {
            this.transport = transport;
            return this;
        }

        /**
         * Set selector threads' count
         * <p/>
         * If this cache manager will create a default transport, the given selector counts will be passed to {@link TCPNIOTransport}.
         * Default is processors' count * 2.
         *
         * @param selectorRunnersCount selector threads' count
         * @return this builder
         */
        public Builder selectorRunnersCount(final int selectorRunnersCount) {
            this.selectorRunnersCount = selectorRunnersCount;
            return this;
        }

        /**
         * Set the specific IO Strategy of Grizzly
         * <p/>
         * If this cache manager will create a default transport, the given {@link IOStrategy} will be passed to {@link TCPNIOTransport}.
         * Default is {@link SameThreadIOStrategy}.
         *
         * @param ioStrategy the specific IO Strategy
         * @return this builder
         */
        public Builder ioStrategy(final IOStrategy ioStrategy) {
            this.ioStrategy = ioStrategy;
            return this;
        }

        /**
         * Enable or disable the blocking mode
         * <p/>
         * If this cache manager will create a default transport, the given mode will be passed to {@link TCPNIOTransport}.
         * Default is false.
         *
         * @param blocking true means the blocking mode
         * @return this builder
         */
        public Builder blocking(final boolean blocking) {
            this.blocking = blocking;
            return this;
        }

        /**
         * Set the specific worker thread pool
         * <p/>
         * If this cache manager will create a default transport, the given {@link ExecutorService} will be passed to {@link TCPNIOTransport}.
         * This is only effective if {@link IOStrategy} is not {@link SameThreadIOStrategy}.
         * Default is null.
         *
         * @param workerThreadPool worker thread pool
         * @return this builder
         */
        public Builder workerThreadPool(final ExecutorService workerThreadPool) {
            this.workerThreadPool = workerThreadPool;
            return this;
        }

        /**
         * Set the {@link ZooKeeperConfig} for synchronizing cache server list among cache clients
         *
         * @param zooKeeperConfig zookeeper config. if {@code zooKeeperConfig} is null, the zookeeper is never used.
         * @return this builder
         */
        public Builder zooKeeperConfig(final ZooKeeperConfig zooKeeperConfig) {
            this.zooKeeperConfig = zooKeeperConfig;
            return this;
        }

        /**
         * Create a {@link GrizzlyMemcachedCacheManager} instance with this builder's properties
         *
         * @return a cache manager
         */
        public GrizzlyMemcachedCacheManager build() {
            return new GrizzlyMemcachedCacheManager(this);
        }
    }
}
