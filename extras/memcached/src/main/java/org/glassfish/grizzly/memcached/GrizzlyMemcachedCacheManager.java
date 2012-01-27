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
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import org.glassfish.grizzly.nio.transport.TCPNIOTransportBuilder;
import org.glassfish.grizzly.strategies.SameThreadIOStrategy;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Bongjae Chang
 */
public class GrizzlyMemcachedCacheManager implements CacheManager {

    private static final Logger logger = Grizzly.logger(GrizzlyMemcachedCacheManager.class);

    private final ConcurrentHashMap<String, GrizzlyMemcachedCache<?, ?>> caches = new ConcurrentHashMap<String, GrizzlyMemcachedCache<?, ?>>();
    private final TCPNIOTransport transport;

    private GrizzlyMemcachedCacheManager(final Builder builder) {
        TCPNIOTransport transport = builder.transport;
        if (transport == null) {
            final FilterChainBuilder clientFilterChainBuilder = FilterChainBuilder.stateless();
            clientFilterChainBuilder.add(new TransportFilter()).add(new MemcachedClientFilter(true, true));
            final TCPNIOTransportBuilder clientTCPNIOTransportBuilder = TCPNIOTransportBuilder.newInstance();
            transport = clientTCPNIOTransportBuilder.build();
            transport.setProcessor(clientFilterChainBuilder.build());
            transport.setSelectorRunnersCount(builder.selectorRunnersCount);
            transport.setIOStrategy(builder.ioStrategy);
            transport.configureBlocking(builder.blocking);
            if (builder.workerThreadPool != null) {
                transport.setWorkerThreadPool(builder.workerThreadPool);
            }
            try {
                transport.start();
            } catch (IOException ie) {
                if (logger.isLoggable(Level.SEVERE)) {
                    logger.log(Level.SEVERE, "failed to start the transport", ie);
                }
            }
        }
        this.transport = transport;
    }

    @Override
    public <K, V> GrizzlyMemcachedCache.Builder<K, V> createCacheBuilder(final String cacheName) {
        return new GrizzlyMemcachedCache.Builder<K, V>(cacheName, this, transport);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <K, V> GrizzlyMemcachedCache<K, V> getCache(final String cacheName) {
        return cacheName != null ? (GrizzlyMemcachedCache<K, V>) caches.get(cacheName) : null;
    }

    @Override
    public boolean removeCache(final String cacheName) {
        if (cacheName == null)
            return false;
        GrizzlyMemcachedCache cache = caches.remove(cacheName);
        if (cache == null) {
            return false;
        }
        cache.stop();
        return true;
    }

    @Override
    public void shutdown() {
        for (MemcachedCache cache : caches.values()) {
            cache.stop();
        }
        caches.clear();
        if (transport != null) {
            try {
                transport.stop();
            } catch (IOException ie) {
                if (logger.isLoggable(Level.INFO)) {
                    logger.log(Level.INFO, "failed to stop the transport", ie);
                }
            }
        }
    }

    /**
     * Adds a cache.
     *
     * @param cache a cache instance
     * @return true if the cache was added
     */
    public <K, V> boolean addCache(final GrizzlyMemcachedCache<K, V> cache) {
        return cache != null && caches.putIfAbsent(cache.getName(), cache) == null;
    }

    public static class Builder {

        private TCPNIOTransport transport;

        // grizzly config
        private int selectorRunnersCount = Runtime.getRuntime().availableProcessors() * 2;
        private IOStrategy ioStrategy = SameThreadIOStrategy.getInstance();
        private boolean blocking = false;
        private ExecutorService workerThreadPool;

        public Builder transport(final TCPNIOTransport transport) {
            this.transport = transport;
            return this;
        }

        public Builder selectorRunnersCount(final int selectorRunnersCount) {
            this.selectorRunnersCount = selectorRunnersCount;
            return this;
        }

        public Builder ioStrategy(final IOStrategy ioStrategy) {
            this.ioStrategy = ioStrategy;
            return this;
        }

        public Builder blocking(final boolean blocking) {
            this.blocking = blocking;
            return this;
        }

        public Builder workerThreadPool(final ExecutorService workerThreadPool) {
            this.workerThreadPool = workerThreadPool;
            return this;
        }

        public GrizzlyMemcachedCacheManager build() {
            return new GrizzlyMemcachedCacheManager(this);
        }
    }
}
