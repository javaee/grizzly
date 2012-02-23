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

import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.CompletionHandler;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.ConnectorHandler;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.GrizzlyFuture;
import org.glassfish.grizzly.Processor;
import org.glassfish.grizzly.WriteResult;
import org.glassfish.grizzly.attributes.Attribute;
import org.glassfish.grizzly.attributes.AttributeHolder;
import org.glassfish.grizzly.filterchain.FilterChain;
import org.glassfish.grizzly.memcached.pool.BaseObjectPool;
import org.glassfish.grizzly.memcached.pool.NoValidObjectException;
import org.glassfish.grizzly.memcached.pool.ObjectPool;
import org.glassfish.grizzly.memcached.pool.PoolExhaustedException;
import org.glassfish.grizzly.memcached.pool.PoolableObjectFactory;
import org.glassfish.grizzly.memcached.zookeeper.BarrierListener;
import org.glassfish.grizzly.memcached.zookeeper.CacheServerListBarrierListener;
import org.glassfish.grizzly.memcached.zookeeper.ZKClient;
import org.glassfish.grizzly.memcached.zookeeper.ZooKeeperSupportCache;
import org.glassfish.grizzly.nio.transport.TCPNIOConnectorHandler;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;

import java.io.UnsupportedEncodingException;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The implementation of the {@link MemcachedCache} based on Grizzly
 * <p/>
 * Basically, this class use {@link BaseObjectPool} for pooling connections of the memcached server
 * and {@link ConsistentHashStore} for selecting the memcached server corresponding to the given key.
 * <p/>
 * When a Cache operation is called,
 * 1. finding the correct server by consistent hashing
 * 2. borrowing the connection from the connection pool
 * 3. queueing request and sending packets to the memcached server and waiting for notification
 * 4. being waken by Grizzly filter when the response is arrived
 * 5. returning the connection to the pool
 * <p/>
 * For the failback of the memcached server, {@link HealthMonitorTask} will be scheduled by {@code healthMonitorIntervalInSecs}.
 * If connecting and writing are failed, this cache retries failure operations by {@code RETRY_COUNT}.
 * If retrials also failed, the server will be regarded as not valid and removed in {@link ConsistentHashStore}.
 * Sometimes, automatical changes of the server list can cause stale cache data at runtime.
 * So this cache provides {@code failover} flag which can turn off the failover/failback.
 * <p/>
 * This cache also supports bulk operations such as {@link #setMulti} as well as {@link #getMulti}.
 * <p/>
 * Example of use:
 * {@code
 * // creates a CacheManager
 * final GrizzlyMemcachedCacheManager manager = new GrizzlyMemcachedCacheManager.Builder().build();
 *
 * // creates a CacheBuilder
 * final GrizzlyMemcachedCache.Builder<String, String> builder = manager.createCacheBuilder("USER");
 * // sets initial servers
 * builder.servers(initServerSet);
 * // creates the specific Cache
 * final MemcachedCache<String, String> userCache = builder.build();
 *
 * // if you need to add another server
 * userCache.addServer(anotherServerAddress);
 *
 * // cache operations
 * boolean result = userCache.set("name", "foo", expirationTimeoutInSec, false);
 * String value = userCache.get("name", false);
 * // ...
 *
 * // shuts down
 * manager.shutdown();
 * }
 *
 * @author Bongjae Chang
 */
public class GrizzlyMemcachedCache<K, V> implements MemcachedCache<K, V>, ZooKeeperSupportCache {

    private static final Logger logger = Grizzly.logger(GrizzlyMemcachedCache.class);

    private static final AtomicInteger opaqueIndex = new AtomicInteger();
    private static final int RETRY_COUNT = 1;

    private final String cacheName;
    private final TCPNIOTransport transport;
    private final long connectTimeoutInMillis;
    private final long writeTimeoutInMillis;
    private final long responseTimeoutInMillis;

    public static final String CONNECTION_POOL_ATTRIBUTE_NAME = "GrizzlyMemcachedCache.ConnectionPool";
    private final Attribute<ObjectPool<SocketAddress, Connection<SocketAddress>>> connectionPoolAttribute =
            Grizzly.DEFAULT_ATTRIBUTE_BUILDER.createAttribute(CONNECTION_POOL_ATTRIBUTE_NAME);
    private final ObjectPool<SocketAddress, Connection<SocketAddress>> connectionPool;

    private final Set<SocketAddress> initialServers;

    private final long healthMonitorIntervalInSecs;
    private final ScheduledFuture<?> scheduledFuture;
    private final HealthMonitorTask healthMonitorTask;
    private final ScheduledExecutorService scheduledExecutor;

    private final boolean failover;

    private final ConsistentHashStore<SocketAddress> consistentHash = new ConsistentHashStore<SocketAddress>();

    private final ZKClient zkClient;
    private final CacheServerListBarrierListener zkListener;
    private String zooKeeperServerListPath;

    private MemcachedClientFilter clientFilter;

    private GrizzlyMemcachedCache(Builder<K, V> builder) {
        this.cacheName = builder.cacheName;
        this.transport = builder.transport;
        this.connectTimeoutInMillis = builder.connectTimeoutInMillis;
        this.writeTimeoutInMillis = builder.writeTimeoutInMillis;
        this.responseTimeoutInMillis = builder.responseTimeoutInMillis;
        this.healthMonitorIntervalInSecs = builder.healthMonitorIntervalInSecs;

        @SuppressWarnings("unchecked")
        final BaseObjectPool.Builder<SocketAddress, Connection<SocketAddress>> connectionPoolBuilder =
                new BaseObjectPool.Builder<SocketAddress, Connection<SocketAddress>>(new PoolableObjectFactory<SocketAddress, Connection<SocketAddress>>() {
                    @Override
                    public Connection<SocketAddress> createObject(final SocketAddress key) throws Exception {
                        final ConnectorHandler<SocketAddress> connectorHandler =
                                TCPNIOConnectorHandler.builder(transport).setReuseAddress(true).build();
                        final Future<Connection> future = connectorHandler.connect(key);
                        final Connection<SocketAddress> connection;
                        try {
                            if (connectTimeoutInMillis < 0) {
                                connection = future.get();
                            } else {
                                connection = future.get(connectTimeoutInMillis, TimeUnit.MILLISECONDS);
                            }
                        } catch (InterruptedException ie) {
                            if (logger.isLoggable(Level.FINER)) {
                                logger.log(Level.FINER, "failed to get the connection. address=" + key, ie);
                            }
                            throw ie;
                        } catch (ExecutionException ee) {
                            if (logger.isLoggable(Level.FINER)) {
                                logger.log(Level.FINER, "failed to get the connection. address=" + key, ee);
                            }
                            throw ee;
                        } catch (TimeoutException te) {
                            if (logger.isLoggable(Level.FINER)) {
                                logger.log(Level.FINER, "failed to get the connection. address=" + key, te);
                            }
                            throw te;
                        }
                        if (connection != null) {
                            connectionPoolAttribute.set(connection, connectionPool);
                            return connection;
                        } else {
                            throw new IllegalStateException("connection must be not null");
                        }
                    }

                    @Override
                    public void destroyObject(final SocketAddress key, final Connection<SocketAddress> value) throws Exception {
                        if (value != null) {
                            value.closeSilently();
                            final AttributeHolder attributeHolder = value.getAttributes();
                            if (attributeHolder != null) {
                                attributeHolder.removeAttribute(CONNECTION_POOL_ATTRIBUTE_NAME);
                            }
                        }
                    }

                    @Override
                    public boolean validateObject(final SocketAddress key, final Connection<SocketAddress> value) throws Exception {
                        return GrizzlyMemcachedCache.this.validateConnectionWithNoopCommand(value);
                        // or return GrizzlyMemcachedCache.this.validateConnectionWithVersionCommand(value);
                    }
                });
        connectionPoolBuilder.min(builder.minConnectionPerServer);
        connectionPoolBuilder.max(builder.maxConnectionPerServer);
        connectionPoolBuilder.keepAliveTimeoutInSecs(builder.keepAliveTimeoutInSecs);
        connectionPoolBuilder.disposable(builder.allowDisposableConnection);
        connectionPoolBuilder.borrowValidation(builder.borrowValidation);
        connectionPoolBuilder.returnValidation(builder.returnValidation);
        connectionPool = connectionPoolBuilder.build();

        this.failover = builder.failover;

        this.initialServers = builder.servers;

        if (failover && healthMonitorIntervalInSecs > 0) {
            healthMonitorTask = new HealthMonitorTask();
            scheduledExecutor = Executors.newSingleThreadScheduledExecutor();
            scheduledFuture = scheduledExecutor.scheduleWithFixedDelay(healthMonitorTask, healthMonitorIntervalInSecs, healthMonitorIntervalInSecs, TimeUnit.SECONDS);
        } else {
            healthMonitorTask = null;
            scheduledExecutor = null;
            scheduledFuture = null;
        }

        this.zkClient = builder.zkClient;
        this.zkListener = new CacheServerListBarrierListener(this, initialServers);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void start() {
        final Processor processor = transport.getProcessor();
        if (!(processor instanceof FilterChain)) {
            throw new IllegalStateException("transport's processor has to be a FilterChain");
        }
        final FilterChain filterChain = (FilterChain) processor;
        final int idx = filterChain.indexOfType(MemcachedClientFilter.class);
        if (idx == -1) {
            throw new IllegalStateException("transport has to have MemcachedClientFilter in the FilterChain");
        }
        clientFilter = (MemcachedClientFilter) filterChain.get(idx);
        if (clientFilter == null) {
            throw new IllegalStateException("MemcachedClientFilter should not be null");
        }
        if (initialServers != null) {
            for (SocketAddress address : initialServers) {
                addServer(address);
            }
        }
        if (zkClient != null) {
            // need to initialize the remote server with local initalServers if the remote server data is empty?
            // currently, do nothing
            zooKeeperServerListPath = zkClient.registerBarrier(cacheName, zkListener, null);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void stop() {
        if (scheduledFuture != null) {
            scheduledFuture.cancel(true);
        }
        if (scheduledExecutor != null) {
            scheduledExecutor.shutdown();
        }
        if (initialServers != null) {
            initialServers.clear();
        }
        consistentHash.clear();
        if (connectionPool != null) {
            connectionPool.destroy();
        }
        if (zkClient != null) {
            zkClient.unregisterBarrier(cacheName);
        }
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("unchecked")
    @Override
    public boolean addServer(final SocketAddress serverAddress) {
        return addServer(serverAddress, true);
    }

    @SuppressWarnings("unchecked")
    private boolean addServer(final SocketAddress serverAddress, boolean initial) {
        if (serverAddress == null) {
            return true;
        }
        if (connectionPool != null) {
            try {
                connectionPool.createAllMinObjects(serverAddress);
            } catch (Exception e) {
                if (logger.isLoggable(Level.SEVERE)) {
                    logger.log(Level.SEVERE, "failed to create min connections in the pool. address=" + serverAddress, e);
                }
                try {
                    connectionPool.removeAllObjects(serverAddress);
                } catch (Exception ignore) {
                }
                if (!initial) {
                    return false;
                }
            }
        }
        consistentHash.add(serverAddress);
        if (logger.isLoggable(Level.INFO)) {
            logger.log(Level.INFO, "added the server to the consistent hash successfully. address={0}", serverAddress);
        }
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void removeServer(final SocketAddress serverAddress) {
        removeServer(serverAddress, true);
    }

    private void removeServer(final SocketAddress serverAddress, final boolean forcibly) {
        if (serverAddress == null) {
            return;
        }
        if (connectionPool != null) {
            try {
                connectionPool.removeAllObjects(serverAddress);
            } catch (Exception e) {
                if (logger.isLoggable(Level.WARNING)) {
                    logger.log(Level.WARNING, "failed to remove connections in the pool", e);
                }
            }
        }
        if (!forcibly) {
            if (healthMonitorTask != null && healthMonitorTask.failure(serverAddress)) {
                consistentHash.remove(serverAddress);
                if (logger.isLoggable(Level.INFO)) {
                    logger.log(Level.INFO, "removed the server from the consistent hash successfully. address={0}", serverAddress);
                }
            }
        } else {
            consistentHash.remove(serverAddress);
            if (logger.isLoggable(Level.INFO)) {
                logger.log(Level.INFO, "removed the server from the consistent hash successfully. address={0}", serverAddress);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isInServerList(final SocketAddress serverAddress) {
        return consistentHash.hasValue(serverAddress);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isZooKeeperSupported() {
        return zkClient != null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getZooKeeperServerListPath() {
        if (!isZooKeeperSupported()) {
            return null;
        }
        return zooKeeperServerListPath;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getCurrentServerListFromZooKeeper() {
        if (!isZooKeeperSupported()) {
            return null;
        }
        final byte[] serverListBytes = zkClient.getData(zooKeeperServerListPath, null);
        if (serverListBytes == null) {
            return null;
        }
        final String serverListString;
        try {
            serverListString = new String(serverListBytes, CacheServerListBarrierListener.DEFAULT_SERVER_LIST_CHARSET);
        } catch (UnsupportedEncodingException e) {
            if (logger.isLoggable(Level.WARNING)) {
                logger.log(Level.WARNING, "failed to decode the server list bytes");
            }
            return null;
        }
        return serverListString;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean setCurrentServerListOfZooKeeper(final String cacheServerList) {
        if (!isZooKeeperSupported()) {
            return false;
        }
        if (cacheServerList == null) {
            return false;
        }
        final byte[] serverListBytes;
        try {
            serverListBytes = cacheServerList.getBytes(CacheServerListBarrierListener.DEFAULT_SERVER_LIST_CHARSET);
        } catch (UnsupportedEncodingException e) {
            if (logger.isLoggable(Level.WARNING)) {
                logger.log(Level.WARNING, "failed to eecode the server list");
            }
            return false;
        }
        return zkClient.setData(zooKeeperServerListPath, serverListBytes, -1) != null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addZooKeeperListener(final BarrierListener listener) {
        if (!isZooKeeperSupported()) {
            return;
        }
        zkListener.addCustomListener(listener);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void removeZooKeeperListener(final BarrierListener listener) {
        if (!isZooKeeperSupported()) {
            return;
        }
        zkListener.removeCustomListener(listener);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getName() {
        return cacheName;
    }

    @Override
    public boolean set(final K key, final V value, final int expirationInSecs, final boolean noReply) {
        return set(key, value, expirationInSecs, noReply, writeTimeoutInMillis, responseTimeoutInMillis);

    }

    @Override
    public boolean set(final K key, final V value, final int expirationInSecs, final boolean noReply, final long writeTimeoutInMillis, final long responseTimeoutInMillis) {
        if (key == null || value == null) {
            return false;
        }
        final MemcachedRequest.Builder builder = MemcachedRequest.Builder.create(true, true, true);
        builder.op(noReply ? CommandOpcodes.SetQ : CommandOpcodes.Set);
        builder.noReply(noReply);
        builder.opaque(noReply ? generateOpaque() : 0);
        builder.originKey(key);
        final BufferWrapper<K> keyWrapper = BufferWrapper.wrap(key, transport.getMemoryManager());
        final Buffer keyBuffer = keyWrapper.getBuffer();
        builder.key(keyBuffer);
        keyWrapper.recycle();
        final BufferWrapper valueWrapper = BufferWrapper.wrap(value, transport.getMemoryManager());
        builder.value(valueWrapper.getBuffer());
        builder.flags(valueWrapper.getType().flags);
        valueWrapper.recycle();
        builder.expirationInSecs(expirationInSecs);
        final MemcachedRequest request = builder.build();

        final SocketAddress address = consistentHash.get(keyBuffer.toByteBuffer());
        if (address == null) {
            builder.recycle();
            return false;
        }
        try {
            if (noReply) {
                sendNoReply(address, request);
                return true;
            } else {
                final Object result = send(address, request, writeTimeoutInMillis, responseTimeoutInMillis);
                if (result instanceof Boolean) {
                    return (Boolean) result;
                } else {
                    return false;
                }
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            if (logger.isLoggable(Level.SEVERE)) {
                logger.log(Level.SEVERE, "failed to set. address=" + address + ", request=" + request, ie);
            }
            return false;
        } catch (Exception e) {
            if (logger.isLoggable(Level.SEVERE)) {
                logger.log(Level.SEVERE, "failed to set. address=" + address + ", request=" + request, e);
            }
            return false;
        } finally {
            builder.recycle();
        }
    }

    @Override
    public Map<K, Boolean> setMulti(final Map<K, V> map, final int expirationInSecs) {
        return setMulti(map, expirationInSecs, writeTimeoutInMillis, responseTimeoutInMillis);
    }

    @Override
    public Map<K, Boolean> setMulti(final Map<K, V> map, final int expirationInSecs, final long writeTimeoutInMillis, final long responseTimeoutInMillis) {
        final Map<K, Boolean> result = new HashMap<K, Boolean>();
        if (map == null || map.isEmpty()) {
            return result;
        }

        // categorize keys by address
        final Map<SocketAddress, List<BufferWrapper<K>>> categorizedMap = new HashMap<SocketAddress, List<BufferWrapper<K>>>();
        for (Map.Entry<K, V> entry : map.entrySet()) {
            final BufferWrapper<K> keyWrapper = BufferWrapper.wrap(entry.getKey(), transport.getMemoryManager());
            final Buffer keyBuffer = keyWrapper.getBuffer();
            final SocketAddress address = consistentHash.get(keyBuffer.toByteBuffer());
            if (address == null) {
                if (logger.isLoggable(Level.WARNING)) {
                    logger.log(Level.WARNING, "failed to get the address from the consistent hash in set multi. key buffer={0}", keyBuffer);
                }
                keyWrapper.recycle();
                continue;
            }
            List<BufferWrapper<K>> keyList = categorizedMap.get(address);
            if (keyList == null) {
                keyList = new ArrayList<BufferWrapper<K>>();
                categorizedMap.put(address, keyList);
            }
            keyList.add(keyWrapper);
        }

        // set multi from server
        for (Map.Entry<SocketAddress, List<BufferWrapper<K>>> entry : categorizedMap.entrySet()) {
            final SocketAddress address = entry.getKey();
            final List<BufferWrapper<K>> keyList = entry.getValue();
            try {
                sendSetMulti(entry.getKey(), keyList, map, expirationInSecs, writeTimeoutInMillis, responseTimeoutInMillis, result);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                if (logger.isLoggable(Level.SEVERE)) {
                    logger.log(Level.SEVERE, "failed to set multi. address=" + address + ", keySize=" + keyList.size(), ie);
                } else if (logger.isLoggable(Level.FINER)) {
                    logger.log(Level.FINER, "failed to set multi. address=" + address + ", keyList=" + keyList, ie);
                }
            } catch (Exception e) {
                if (logger.isLoggable(Level.SEVERE)) {
                    logger.log(Level.SEVERE, "failed to set multi. address=" + address + ", keySize=" + keyList.size(), e);
                } else if (logger.isLoggable(Level.FINER)) {
                    logger.log(Level.FINER, "failed to set multi. address=" + address + ", keyList=" + keyList, e);
                }
            } finally {
                recycleBufferWrappers(keyList);
            }
        }
        return result;
    }

    @Override
    public boolean add(final K key, final V value, final int expirationInSecs, final boolean noReply) {
        return add(key, value, expirationInSecs, noReply, writeTimeoutInMillis, responseTimeoutInMillis);
    }

    @Override
    public boolean add(final K key, final V value, final int expirationInSecs, final boolean noReply, final long writeTimeoutInMillis, final long responseTimeoutInMillis) {
        if (key == null || value == null) {
            return false;
        }
        final MemcachedRequest.Builder builder = MemcachedRequest.Builder.create(true, true, true);
        builder.op(noReply ? CommandOpcodes.AddQ : CommandOpcodes.Add);
        builder.noReply(noReply);
        builder.opaque(noReply ? generateOpaque() : 0);
        builder.originKey(key);
        final BufferWrapper<K> keyWrapper = BufferWrapper.wrap(key, transport.getMemoryManager());
        final Buffer keyBuffer = keyWrapper.getBuffer();
        builder.key(keyBuffer);
        keyWrapper.recycle();
        final BufferWrapper valueWrapper = BufferWrapper.wrap(value, transport.getMemoryManager());
        builder.value(valueWrapper.getBuffer());
        builder.flags(valueWrapper.getType().flags);
        valueWrapper.recycle();
        builder.expirationInSecs(expirationInSecs);
        final MemcachedRequest request = builder.build();

        final SocketAddress address = consistentHash.get(keyBuffer.toByteBuffer());
        if (address == null) {
            builder.recycle();
            return false;
        }
        try {
            if (noReply) {
                sendNoReply(address, request);
                return true;
            } else {
                final Object result = send(address, request, writeTimeoutInMillis, responseTimeoutInMillis);
                if (result instanceof Boolean) {
                    return (Boolean) result;
                } else {
                    return false;
                }
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            if (logger.isLoggable(Level.SEVERE)) {
                logger.log(Level.SEVERE, "failed to add. address=" + address + ", request=" + request, ie);
            }
            return false;
        } catch (Exception e) {
            if (logger.isLoggable(Level.SEVERE)) {
                logger.log(Level.SEVERE, "failed to add. address=" + address + ", request=" + request, e);
            }
            return false;
        } finally {
            builder.recycle();
        }
    }

    @Override
    public boolean replace(final K key, final V value, final int expirationInSecs, final boolean noReply) {
        return replace(key, value, expirationInSecs, noReply, writeTimeoutInMillis, responseTimeoutInMillis);
    }

    @Override
    public boolean replace(final K key, final V value, final int expirationInSecs, final boolean noReply, final long writeTimeoutInMillis, final long responseTimeoutInMillis) {
        if (key == null || value == null) {
            return false;
        }
        final MemcachedRequest.Builder builder = MemcachedRequest.Builder.create(true, true, true);
        builder.op(noReply ? CommandOpcodes.ReplaceQ : CommandOpcodes.Replace);
        builder.noReply(noReply);
        builder.opaque(noReply ? generateOpaque() : 0);
        builder.originKey(key);
        final BufferWrapper<K> keyWrapper = BufferWrapper.wrap(key, transport.getMemoryManager());
        final Buffer keyBuffer = keyWrapper.getBuffer();
        builder.key(keyBuffer);
        keyWrapper.recycle();
        final BufferWrapper valueWrapper = BufferWrapper.wrap(value, transport.getMemoryManager());
        builder.value(valueWrapper.getBuffer());
        builder.flags(valueWrapper.getType().flags);
        valueWrapper.recycle();
        builder.expirationInSecs(expirationInSecs);
        final MemcachedRequest request = builder.build();

        final SocketAddress address = consistentHash.get(keyBuffer.toByteBuffer());
        if (address == null) {
            builder.recycle();
            return false;
        }
        try {
            if (noReply) {
                sendNoReply(address, request);
                return true;
            } else {
                final Object result = send(address, request, writeTimeoutInMillis, responseTimeoutInMillis);
                if (result instanceof Boolean) {
                    return (Boolean) result;
                } else {
                    return false;
                }
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            if (logger.isLoggable(Level.SEVERE)) {
                logger.log(Level.SEVERE, "failed to replace. address=" + address + ", request=" + request, ie);
            }
            return false;
        } catch (Exception e) {
            if (logger.isLoggable(Level.SEVERE)) {
                logger.log(Level.SEVERE, "failed to replace. address=" + address + ", request=" + request, e);
            }
            return false;
        } finally {
            builder.recycle();
        }
    }

    @Override
    public boolean cas(final K key, final V value, final int expirationInSecs, final long cas, final boolean noReply) {
        return cas(key, value, expirationInSecs, cas, noReply, writeTimeoutInMillis, responseTimeoutInMillis);
    }

    @Override
    public boolean cas(final K key, final V value, final int expirationInSecs, final long cas, final boolean noReply, final long writeTimeoutInMillis, final long responseTimeoutInMillis) {
        if (key == null || value == null) {
            return false;
        }
        final MemcachedRequest.Builder builder = MemcachedRequest.Builder.create(true, true, true);
        builder.op(noReply ? CommandOpcodes.SetQ : CommandOpcodes.Set);
        builder.noReply(noReply);
        builder.opaque(noReply ? generateOpaque() : 0);
        builder.cas(cas);
        builder.originKey(key);
        final BufferWrapper<K> keyWrapper = BufferWrapper.wrap(key, transport.getMemoryManager());
        final Buffer keyBuffer = keyWrapper.getBuffer();
        builder.key(keyBuffer);
        keyWrapper.recycle();
        final BufferWrapper valueWrapper = BufferWrapper.wrap(value, transport.getMemoryManager());
        builder.value(valueWrapper.getBuffer());
        builder.flags(valueWrapper.getType().flags);
        valueWrapper.recycle();
        builder.expirationInSecs(expirationInSecs);
        final MemcachedRequest request = builder.build();

        final SocketAddress address = consistentHash.get(keyBuffer.toByteBuffer());
        if (address == null) {
            builder.recycle();
            return false;
        }
        try {
            if (noReply) {
                sendNoReply(address, request);
                return true;
            } else {
                final Object result = send(address, request, writeTimeoutInMillis, responseTimeoutInMillis);
                if (result instanceof Boolean) {
                    return (Boolean) result;
                } else {
                    return false;
                }
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            if (logger.isLoggable(Level.SEVERE)) {
                logger.log(Level.SEVERE, "failed to set with cas. address=" + address + ", request=" + request, ie);
            }
            return false;
        } catch (Exception e) {
            if (logger.isLoggable(Level.SEVERE)) {
                logger.log(Level.SEVERE, "failed to set with cas. address=" + address + ", request=" + request, e);
            }
            return false;
        } finally {
            builder.recycle();
        }
    }

    @Override
    public boolean append(final K key, final V value, final boolean noReply) {
        return append(key, value, noReply, writeTimeoutInMillis, responseTimeoutInMillis);
    }

    @Override
    public boolean append(final K key, final V value, final boolean noReply, final long writeTimeoutInMillis, final long responseTimeoutInMillis) {
        if (key == null || value == null) {
            return false;
        }
        final MemcachedRequest.Builder builder = MemcachedRequest.Builder.create(false, true, true);
        builder.op(noReply ? CommandOpcodes.AppendQ : CommandOpcodes.Append);
        builder.noReply(noReply);
        builder.opaque(noReply ? generateOpaque() : 0);
        builder.originKey(key);
        final BufferWrapper<K> keyWrapper = BufferWrapper.wrap(key, transport.getMemoryManager());
        final Buffer keyBuffer = keyWrapper.getBuffer();
        builder.key(keyBuffer);
        keyWrapper.recycle();
        final BufferWrapper valueWrapper = BufferWrapper.wrap(value, transport.getMemoryManager());
        builder.value(valueWrapper.getBuffer());
        valueWrapper.recycle();
        final MemcachedRequest request = builder.build();

        final SocketAddress address = consistentHash.get(keyBuffer.toByteBuffer());
        if (address == null) {
            builder.recycle();
            return false;
        }
        try {
            if (noReply) {
                sendNoReply(address, request);
                return true;
            } else {
                final Object result = send(address, request, writeTimeoutInMillis, responseTimeoutInMillis);
                if (result instanceof Boolean) {
                    return (Boolean) result;
                } else {
                    return false;
                }
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            if (logger.isLoggable(Level.SEVERE)) {
                logger.log(Level.SEVERE, "failed to append. address=" + address + ", request=" + request, ie);
            }
            return false;
        } catch (Exception e) {
            if (logger.isLoggable(Level.SEVERE)) {
                logger.log(Level.SEVERE, "failed to append. address=" + address + ", request=" + request, e);
            }
            return false;
        } finally {
            builder.recycle();
        }
    }

    @Override
    public boolean prepend(final K key, final V value, final boolean noReply) {
        return prepend(key, value, noReply, writeTimeoutInMillis, responseTimeoutInMillis);
    }

    @Override
    public boolean prepend(final K key, final V value, final boolean noReply, final long writeTimeoutInMillis, final long responseTimeoutInMillis) {
        if (key == null || value == null) {
            return false;
        }
        final MemcachedRequest.Builder builder = MemcachedRequest.Builder.create(false, true, true);
        builder.op(noReply ? CommandOpcodes.PrependQ : CommandOpcodes.Prepend);
        builder.noReply(noReply);
        builder.opaque(noReply ? generateOpaque() : 0);
        builder.originKey(key);
        final BufferWrapper<K> keyWrapper = BufferWrapper.wrap(key, transport.getMemoryManager());
        final Buffer keyBuffer = keyWrapper.getBuffer();
        builder.key(keyBuffer);
        keyWrapper.recycle();
        final BufferWrapper valueWrapper = BufferWrapper.wrap(value, transport.getMemoryManager());
        builder.value(valueWrapper.getBuffer());
        valueWrapper.recycle();
        final MemcachedRequest request = builder.build();

        final SocketAddress address = consistentHash.get(keyBuffer.toByteBuffer());
        if (address == null) {
            builder.recycle();
            return false;
        }
        try {
            if (noReply) {
                sendNoReply(address, request);
                return true;
            } else {
                final Object result = send(address, request, writeTimeoutInMillis, responseTimeoutInMillis);
                if (result instanceof Boolean) {
                    return (Boolean) result;
                } else {
                    return false;
                }
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            if (logger.isLoggable(Level.SEVERE)) {
                logger.log(Level.SEVERE, "failed to prepend. address=" + address + ", request=" + request, ie);
            }
            return false;
        } catch (Exception e) {
            if (logger.isLoggable(Level.SEVERE)) {
                logger.log(Level.SEVERE, "failed to prepend. address=" + address + ", request=" + request, e);
            }
            return false;
        } finally {
            builder.recycle();
        }
    }

    @Override
    public Map<K, V> getMulti(final Set<K> keys) {
        return getMulti(keys, writeTimeoutInMillis, responseTimeoutInMillis);
    }

    @Override
    public Map<K, V> getMulti(final Set<K> keys, final long writeTimeoutInMillis, final long responseTimeoutInMillis) {
        final Map<K, V> result = new HashMap<K, V>();
        if (keys == null || keys.isEmpty()) {
            return result;
        }

        // categorize keys by address
        final Map<SocketAddress, List<BufferWrapper<K>>> categorizedMap = new HashMap<SocketAddress, List<BufferWrapper<K>>>();
        for (K key : keys) {
            final BufferWrapper<K> keyWrapper = BufferWrapper.wrap(key, transport.getMemoryManager());
            final Buffer keyBuffer = keyWrapper.getBuffer();
            final SocketAddress address = consistentHash.get(keyBuffer.toByteBuffer());
            if (address == null) {
                if (logger.isLoggable(Level.WARNING)) {
                    logger.log(Level.WARNING, "failed to get the address from the consistent hash in get multi. key buffer={0}", keyBuffer);
                }
                keyWrapper.recycle();
                continue;
            }
            List<BufferWrapper<K>> keyList = categorizedMap.get(address);
            if (keyList == null) {
                keyList = new ArrayList<BufferWrapper<K>>();
                categorizedMap.put(address, keyList);
            }
            keyList.add(keyWrapper);
        }

        // get multi from server
        for (Map.Entry<SocketAddress, List<BufferWrapper<K>>> entry : categorizedMap.entrySet()) {
            final SocketAddress address = entry.getKey();
            final List<BufferWrapper<K>> keyList = entry.getValue();
            try {
                sendGetMulti(entry.getKey(), keyList, writeTimeoutInMillis, responseTimeoutInMillis, result);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                if (logger.isLoggable(Level.SEVERE)) {
                    logger.log(Level.SEVERE, "failed to get multi. address=" + address + ", keySize=" + keyList.size(), ie);
                } else if (logger.isLoggable(Level.FINER)) {
                    logger.log(Level.FINER, "failed to get multi. address=" + address + ", keyList=" + keyList, ie);
                }
            } catch (Exception e) {
                if (logger.isLoggable(Level.SEVERE)) {
                    logger.log(Level.SEVERE, "failed to get multi. address=" + address + ", keySize=" + keyList.size(), e);
                } else if (logger.isLoggable(Level.FINER)) {
                    logger.log(Level.FINER, "failed to get multi. address=" + address + ", keyList=" + keyList, e);
                }
            } finally {
                recycleBufferWrappers(keyList);
            }
        }
        return result;
    }

    @Override
    public V get(final K key, final boolean noReply) {
        return get(key, noReply, writeTimeoutInMillis, responseTimeoutInMillis);
    }

    @SuppressWarnings("unchecked")
    @Override
    public V get(final K key, final boolean noReply, final long writeTimeoutInMillis, final long responseTimeoutInMillis) {
        if (key == null) {
            return null;
        }
        final MemcachedRequest.Builder builder = MemcachedRequest.Builder.create(false, true, false);
        builder.op(noReply ? CommandOpcodes.GetQ : CommandOpcodes.Get);
        builder.noReply(false);
        builder.opaque(noReply ? generateOpaque() : 0);
        builder.originKey(key);
        final BufferWrapper<K> keyWrapper = BufferWrapper.wrap(key, transport.getMemoryManager());
        final Buffer keyBuffer = keyWrapper.getBuffer();
        builder.key(keyBuffer);
        keyWrapper.recycle();
        final MemcachedRequest request = builder.build();

        final SocketAddress address = consistentHash.get(keyBuffer.toByteBuffer());
        if (address == null) {
            builder.recycle();
            return null;
        }
        try {
            final Object result = send(address, request, writeTimeoutInMillis, responseTimeoutInMillis);
            if (result != null) {
                return (V) result;
            } else {
                return null;
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            if (logger.isLoggable(Level.SEVERE)) {
                logger.log(Level.SEVERE, "failed to get. address=" + address + ", request=" + request, ie);
            }
            return null;
        } catch (Exception e) {
            if (logger.isLoggable(Level.SEVERE)) {
                logger.log(Level.SEVERE, "failed to get. address=" + address + ", request=" + request, e);
            }
            return null;
        } finally {
            builder.recycle();
        }
    }

    @Override
    public ValueWithKey<K, V> getKey(final K key, final boolean noReply) {
        return getKey(key, noReply, writeTimeoutInMillis, responseTimeoutInMillis);
    }

    @SuppressWarnings("unchecked")
    @Override
    public ValueWithKey<K, V> getKey(final K key, final boolean noReply, final long writeTimeoutInMillis, final long responseTimeoutInMillis) {
        if (key == null) {
            return null;
        }
        final MemcachedRequest.Builder builder = MemcachedRequest.Builder.create(false, true, false);
        builder.op(noReply ? CommandOpcodes.GetKQ : CommandOpcodes.GetK);
        builder.noReply(false);
        builder.opaque(noReply ? generateOpaque() : 0);
        builder.originKey(key);
        final BufferWrapper<K> keyWrapper = BufferWrapper.wrap(key, transport.getMemoryManager());
        final Buffer keyBuffer = keyWrapper.getBuffer();
        builder.key(keyBuffer);
        keyWrapper.recycle();
        final MemcachedRequest request = builder.build();

        final SocketAddress address = consistentHash.get(keyBuffer.toByteBuffer());
        if (address == null) {
            builder.recycle();
            return null;
        }
        try {
            final Object result = send(address, request, writeTimeoutInMillis, responseTimeoutInMillis);
            if (result != null) {
                return (ValueWithKey<K, V>) result;
            } else {
                return null;
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            if (logger.isLoggable(Level.SEVERE)) {
                logger.log(Level.SEVERE, "failed to get with key. address=" + address + ", request=" + request, ie);
            }
            return null;
        } catch (Exception e) {
            if (logger.isLoggable(Level.SEVERE)) {
                logger.log(Level.SEVERE, "failed to get with key. address=" + address + ", request=" + request, e);
            }
            return null;
        } finally {
            builder.recycle();
        }
    }

    @Override
    public ValueWithCas<V> gets(final K key, final boolean noReply) {
        return gets(key, noReply, writeTimeoutInMillis, responseTimeoutInMillis);
    }

    @SuppressWarnings("unchecked")
    @Override
    public ValueWithCas<V> gets(final K key, final boolean noReply, final long writeTimeoutInMillis, final long responseTimeoutInMillis) {
        if (key == null) {
            return null;
        }
        final MemcachedRequest.Builder builder = MemcachedRequest.Builder.create(false, true, false);
        builder.op(noReply ? CommandOpcodes.GetsQ : CommandOpcodes.Gets);
        builder.noReply(false);
        builder.opaque(noReply ? generateOpaque() : 0);
        builder.originKey(key);
        final BufferWrapper<K> keyWrapper = BufferWrapper.wrap(key, transport.getMemoryManager());
        final Buffer keyBuffer = keyWrapper.getBuffer();
        builder.key(keyBuffer);
        keyWrapper.recycle();
        final MemcachedRequest request = builder.build();

        final SocketAddress address = consistentHash.get(keyBuffer.toByteBuffer());
        if (address == null) {
            builder.recycle();
            return null;
        }
        try {
            final Object result = send(address, request, writeTimeoutInMillis, responseTimeoutInMillis);
            if (result != null) {
                return (ValueWithCas<V>) result;
            } else {
                return null;
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            if (logger.isLoggable(Level.SEVERE)) {
                logger.log(Level.SEVERE, "failed to get with cas. address=" + address + ", request=" + request, ie);
            }
            return null;
        } catch (Exception e) {
            if (logger.isLoggable(Level.SEVERE)) {
                logger.log(Level.SEVERE, "failed to get with cas. address=" + address + ", request=" + request, e);
            }
            return null;
        } finally {
            builder.recycle();
        }
    }

    @Override
    public V gat(final K key, final int expirationInSecs, final boolean noReply) {
        return gat(key, expirationInSecs, noReply, writeTimeoutInMillis, responseTimeoutInMillis);
    }

    @SuppressWarnings("unchecked")
    @Override
    public V gat(final K key, final int expirationInSecs, final boolean noReply, final long writeTimeoutInMillis, final long responseTimeoutInMillis) {
        if (key == null) {
            return null;
        }
        final MemcachedRequest.Builder builder = MemcachedRequest.Builder.create(true, true, false);
        builder.op(noReply ? CommandOpcodes.GATQ : CommandOpcodes.GAT);
        builder.noReply(false);
        builder.opaque(noReply ? generateOpaque() : 0);
        builder.originKey(key);
        final BufferWrapper<K> keyWrapper = BufferWrapper.wrap(key, transport.getMemoryManager());
        final Buffer keyBuffer = keyWrapper.getBuffer();
        builder.key(keyBuffer);
        keyWrapper.recycle();
        builder.expirationInSecs(expirationInSecs);
        final MemcachedRequest request = builder.build();

        final SocketAddress address = consistentHash.get(keyBuffer.toByteBuffer());
        if (address == null) {
            builder.recycle();
            return null;
        }
        try {
            final Object result = send(address, request, writeTimeoutInMillis, responseTimeoutInMillis);
            if (result != null) {
                return (V) result;
            } else {
                return null;
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            if (logger.isLoggable(Level.SEVERE)) {
                logger.log(Level.SEVERE, "failed to get and touch. address=" + address + ", request=" + request, ie);
            }
            return null;
        } catch (Exception e) {
            if (logger.isLoggable(Level.SEVERE)) {
                logger.log(Level.SEVERE, "failed to get and touch. address=" + address + ", request=" + request, e);
            }
            return null;
        } finally {
            builder.recycle();
        }
    }

    @Override
    public boolean delete(final K key, final boolean noReply) {
        return delete(key, noReply, writeTimeoutInMillis, responseTimeoutInMillis);
    }

    @Override
    public boolean delete(final K key, final boolean noReply, final long writeTimeoutInMillis, final long responseTimeoutInMillis) {
        if (key == null) {
            return false;
        }
        final MemcachedRequest.Builder builder = MemcachedRequest.Builder.create(false, true, false);
        builder.op(noReply ? CommandOpcodes.DeleteQ : CommandOpcodes.Delete);
        builder.noReply(noReply);
        builder.opaque(noReply ? generateOpaque() : 0);
        builder.originKey(key);
        final BufferWrapper<K> keyWrapper = BufferWrapper.wrap(key, transport.getMemoryManager());
        final Buffer keyBuffer = keyWrapper.getBuffer();
        builder.key(keyBuffer);
        keyWrapper.recycle();
        final MemcachedRequest request = builder.build();

        final SocketAddress address = consistentHash.get(keyBuffer.toByteBuffer());
        if (address == null) {
            builder.recycle();
            return false;
        }
        try {
            if (noReply) {
                sendNoReply(address, request);
                return true;
            } else {
                final Object result = send(address, request, writeTimeoutInMillis, responseTimeoutInMillis);
                if (result instanceof Boolean) {
                    return (Boolean) result;
                } else {
                    return false;
                }
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            if (logger.isLoggable(Level.SEVERE)) {
                logger.log(Level.SEVERE, "failed to delete. address=" + address + ", request=" + request, ie);
            }
            return false;
        } catch (Exception e) {
            if (logger.isLoggable(Level.SEVERE)) {
                logger.log(Level.SEVERE, "failed to delete. address=" + address + ", request=" + request, e);
            }
            return false;
        } finally {
            builder.recycle();
        }
    }

    @Override
    public long incr(final K key, final long delta, final long initial, final int expirationInSecs, final boolean noReply) {
        return incr(key, delta, initial, expirationInSecs, noReply, writeTimeoutInMillis, responseTimeoutInMillis);
    }

    @Override
    public long incr(final K key, final long delta, final long initial, final int expirationInSecs, final boolean noReply, final long writeTimeoutInMillis, final long responseTimeoutInMillis) {
        if (key == null) {
            return -1;
        }
        final MemcachedRequest.Builder builder = MemcachedRequest.Builder.create(true, true, false);
        builder.op(noReply ? CommandOpcodes.IncrementQ : CommandOpcodes.Increment);
        builder.noReply(noReply);
        builder.opaque(noReply ? generateOpaque() : 0);
        builder.originKey(key);
        final BufferWrapper<K> keyWrapper = BufferWrapper.wrap(key, transport.getMemoryManager());
        final Buffer keyBuffer = keyWrapper.getBuffer();
        builder.key(keyBuffer);
        keyWrapper.recycle();
        builder.delta(delta);
        builder.initial(initial);
        builder.expirationInSecs(expirationInSecs);
        final MemcachedRequest request = builder.build();

        final SocketAddress address = consistentHash.get(keyBuffer.toByteBuffer());
        if (address == null) {
            builder.recycle();
            return -1;
        }
        try {
            if (noReply) {
                sendNoReply(address, request);
                return -1;
            } else {
                final Object result = send(address, request, writeTimeoutInMillis, responseTimeoutInMillis);
                if (result != null) {
                    return (Long) result;
                } else {
                    return -1;
                }
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            if (logger.isLoggable(Level.SEVERE)) {
                logger.log(Level.SEVERE, "failed to increase. address=" + address + ", request=" + request, ie);
            }
            return -1;
        } catch (Exception e) {
            if (logger.isLoggable(Level.SEVERE)) {
                logger.log(Level.SEVERE, "failed to increase. address=" + address + ", request=" + request, e);
            }
            return -1;
        } finally {
            builder.recycle();
        }
    }

    @Override
    public long decr(final K key, final long delta, final long initial, final int expirationInSecs, final boolean noReply) {
        return decr(key, delta, initial, expirationInSecs, noReply, writeTimeoutInMillis, responseTimeoutInMillis);
    }

    @Override
    public long decr(final K key, final long delta, final long initial, final int expirationInSecs, final boolean noReply, final long writeTimeoutInMillis, final long responseTimeoutInMillis) {
        if (key == null) {
            return -1;
        }
        final MemcachedRequest.Builder builder = MemcachedRequest.Builder.create(true, true, false);
        builder.op(noReply ? CommandOpcodes.DecrementQ : CommandOpcodes.Decrement);
        builder.noReply(noReply);
        builder.opaque(noReply ? generateOpaque() : 0);
        builder.originKey(key);
        final BufferWrapper<K> keyWrapper = BufferWrapper.wrap(key, transport.getMemoryManager());
        final Buffer keyBuffer = keyWrapper.getBuffer();
        builder.key(keyBuffer);
        keyWrapper.recycle();
        builder.delta(delta);
        builder.initial(initial);
        builder.expirationInSecs(expirationInSecs);
        final MemcachedRequest request = builder.build();

        final SocketAddress address = consistentHash.get(keyBuffer.toByteBuffer());
        if (address == null) {
            builder.recycle();
            return -1;
        }
        try {
            if (noReply) {
                sendNoReply(address, request);
                return -1;
            } else {
                final Object result = send(address, request, writeTimeoutInMillis, responseTimeoutInMillis);
                if (result != null) {
                    return (Long) result;
                } else {
                    return -1;
                }
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            if (logger.isLoggable(Level.SEVERE)) {
                logger.log(Level.SEVERE, "failed to decrease. address=" + address + ", request=" + request, ie);
            }
            return -1;
        } catch (Exception e) {
            if (logger.isLoggable(Level.SEVERE)) {
                logger.log(Level.SEVERE, "failed to decrease. address=" + address + ", request=" + request, e);
            }
            return -1;
        } finally {
            builder.recycle();
        }
    }

    @Override
    public String saslAuth(final SocketAddress address, final String mechanism, final byte[] data) {
        return saslAuth(address, mechanism, data, writeTimeoutInMillis, responseTimeoutInMillis);
    }

    @Override
    public String saslAuth(final SocketAddress address, final String mechanism, final byte[] data, final long writeTimeoutInMillis, final long responseTimeoutInMillis) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String saslStep(final SocketAddress address, final String mechanism, final byte[] data) {
        return saslStep(address, mechanism, data, writeTimeoutInMillis, responseTimeoutInMillis);
    }

    @Override
    public String saslStep(final SocketAddress address, final String mechanism, final byte[] data, final long writeTimeoutInMillis, final long responseTimeoutInMillis) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String saslList(final SocketAddress address) {
        return saslList(address, writeTimeoutInMillis, responseTimeoutInMillis);
    }

    @Override
    public String saslList(final SocketAddress address, final long writeTimeoutInMillis, final long responseTimeoutInMillis) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Map<String, String> stats(final SocketAddress address) {
        return stats(address, writeTimeoutInMillis, responseTimeoutInMillis);
    }

    @Override
    public Map<String, String> stats(final SocketAddress address, final long writeTimeoutInMillis, final long responseTimeoutInMillis) {
        return statsItems(address, null, writeTimeoutInMillis, responseTimeoutInMillis);
    }

    @Override
    public Map<String, String> statsItems(final SocketAddress address, final String item) {
        return statsItems(address, item, writeTimeoutInMillis, responseTimeoutInMillis);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Map<String, String> statsItems(final SocketAddress address, final String item, final long writeTimeoutInMillis, final long responseTimeoutInMillis) {
        if (address == null) {
            return null;
        }
        if (connectionPool == null) {
            throw new IllegalStateException("connection pool must be not null");
        }
        if (clientFilter == null) {
            throw new IllegalStateException("client filter must be not null");
        }

        final MemcachedRequest.Builder builder = MemcachedRequest.Builder.create(false, item != null, false);
        builder.op(CommandOpcodes.Stat);
        builder.noReply(false);
        if (item != null) {
            builder.originKey(item);
            final BufferWrapper<String> keyWrapper = BufferWrapper.wrap(item, transport.getMemoryManager());
            final Buffer keyBuffer = keyWrapper.getBuffer();
            builder.key(keyBuffer);
            keyWrapper.recycle();
        }
        final MemcachedRequest request = builder.build();

        final Connection<SocketAddress> connection;
        try {
            connection = connectionPool.borrowObject(address, connectTimeoutInMillis);
        } catch (PoolExhaustedException pee) {
            if (logger.isLoggable(Level.SEVERE)) {
                logger.log(Level.SEVERE, "failed to get the stats. address=" + address + ", timeout=" + connectTimeoutInMillis + "ms", pee);
            }
            return null;
        } catch (NoValidObjectException nvoe) {
            if (logger.isLoggable(Level.SEVERE)) {
                logger.log(Level.SEVERE, "failed to get the stats. address=" + address + ", timeout=" + connectTimeoutInMillis + "ms", nvoe);
            }
            return null;
        } catch (InterruptedException ie) {
            if (logger.isLoggable(Level.SEVERE)) {
                logger.log(Level.SEVERE, "failed to get the stats. address=" + address + ", timeout=" + connectTimeoutInMillis + "ms", ie);
            }
            return null;
        }
        try {
            final GrizzlyFuture<WriteResult<MemcachedRequest[], SocketAddress>> future = connection.write(new MemcachedRequest[]{request});
            try {
                if (writeTimeoutInMillis > 0) {
                    future.get(writeTimeoutInMillis, TimeUnit.MILLISECONDS);
                } else {
                    future.get();
                }
            } catch (ExecutionException ee) {
                if (logger.isLoggable(Level.SEVERE)) {
                    logger.log(Level.SEVERE, "failed to get the stats. address=" + address + ", request=" + request, ee);
                }
                return null;
            } catch (TimeoutException te) {
                if (logger.isLoggable(Level.SEVERE)) {
                    logger.log(Level.SEVERE, "failed to get the stats. address=" + address + ", request=" + request, te);
                }
                return null;
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                if (logger.isLoggable(Level.SEVERE)) {
                    logger.log(Level.SEVERE, "failed to get the stats. address=" + address + ", request=" + request, ie);
                }
                return null;
            } finally {
                builder.recycle();
            }

            final Map<String, String> stats = new HashMap<String, String>();
            while (true) {
                final ValueWithKey<String, String> value;
                try {
                    value = (ValueWithKey<String, String>) clientFilter.getCorrelatedResponse(connection, request, responseTimeoutInMillis);
                } catch (TimeoutException te) {
                    if (logger.isLoggable(Level.SEVERE)) {
                        logger.log(Level.SEVERE, "failed to get the stats. timeout=" + responseTimeoutInMillis + "ms", te);
                    }
                    break;
                } catch (InterruptedException ie) {
                    if (logger.isLoggable(Level.SEVERE)) {
                        logger.log(Level.SEVERE, "failed to get the stats. timeout=" + responseTimeoutInMillis + "ms", ie);
                    }
                    break;
                }
                if (value != null) {
                    final String statKey = value.getKey();
                    final String statValue = value.getValue();
                    if (statKey != null && statValue != null) {
                        stats.put(statKey, statValue);
                    } else {
                        break;
                    }
                } else {
                    break;
                }
            }
            return stats;
        } finally {
            try {
                connectionPool.returnObject(address, connection);
            } catch (Exception e) {
                if (logger.isLoggable(Level.SEVERE)) {
                    logger.log(Level.SEVERE, "failed to return the connection. address=" + address + ", connection=" + connection, e);
                }
            }
        }
    }

    @Override
    public boolean quit(final SocketAddress address, final boolean noReply) {
        return quit(address, noReply, writeTimeoutInMillis, responseTimeoutInMillis);
    }

    @Override
    public boolean quit(final SocketAddress address, final boolean noReply, final long writeTimeoutInMillis, final long responseTimeoutInMillis) {
        if (address == null) {
            return false;
        }
        final MemcachedRequest.Builder builder = MemcachedRequest.Builder.create(false, false, false);
        builder.op(noReply ? CommandOpcodes.QuitQ : CommandOpcodes.Quit);
        builder.noReply(noReply);
        final MemcachedRequest request = builder.build();

        try {
            if (noReply) {
                sendNoReply(address, request);
                return true;
            } else {
                final Object result = send(address, request, writeTimeoutInMillis, responseTimeoutInMillis);
                if (result instanceof Boolean) {
                    return (Boolean) result;
                } else {
                    return false;
                }
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            if (logger.isLoggable(Level.SEVERE)) {
                logger.log(Level.SEVERE, "failed to quit. address=" + address + ", request=" + request, ie);
            }
            return false;
        } catch (Exception e) {
            if (logger.isLoggable(Level.SEVERE)) {
                logger.log(Level.SEVERE, "failed to quit. address=" + address + ", request=" + request, e);
            }
            return false;
        } finally {
            builder.recycle();
        }
    }

    @Override
    public boolean flushAll(final SocketAddress address, final int expirationInSecs, final boolean noReply) {
        return flushAll(address, expirationInSecs, noReply, writeTimeoutInMillis, responseTimeoutInMillis);
    }

    @Override
    public boolean flushAll(final SocketAddress address, final int expirationInSecs, final boolean noReply, final long writeTimeoutInMillis, final long responseTimeoutInMillis) {
        if (address == null) {
            return false;
        }
        final MemcachedRequest.Builder builder = MemcachedRequest.Builder.create(expirationInSecs > 0, false, false);
        builder.op(noReply ? CommandOpcodes.FlushQ : CommandOpcodes.Flush);
        builder.noReply(noReply);
        builder.expirationInSecs(expirationInSecs);
        final MemcachedRequest request = builder.build();

        try {
            if (noReply) {
                sendNoReply(address, request);
                return true;
            } else {
                final Object result = send(address, request, writeTimeoutInMillis, responseTimeoutInMillis);
                if (result instanceof Boolean) {
                    return (Boolean) result;
                } else {
                    return false;
                }
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            if (logger.isLoggable(Level.SEVERE)) {
                logger.log(Level.SEVERE, "failed to flush. address=" + address + ", request=" + request, ie);
            }
            return false;
        } catch (Exception e) {
            if (logger.isLoggable(Level.SEVERE)) {
                logger.log(Level.SEVERE, "failed to flush. address=" + address + ", request=" + request, e);
            }
            return false;
        } finally {
            builder.recycle();
        }
    }

    @Override
    public boolean touch(final K key, final int expirationInSecs) {
        return touch(key, expirationInSecs, writeTimeoutInMillis, responseTimeoutInMillis);
    }

    @Override
    public boolean touch(final K key, final int expirationInSecs, final long writeTimeoutInMillis, final long responseTimeoutInMillis) {
        if (key == null) {
            return false;
        }
        final MemcachedRequest.Builder builder = MemcachedRequest.Builder.create(true, true, false);
        builder.op(CommandOpcodes.Touch);
        builder.noReply(false);
        builder.originKey(key);
        final BufferWrapper<K> keyWrapper = BufferWrapper.wrap(key, transport.getMemoryManager());
        final Buffer keyBuffer = keyWrapper.getBuffer();
        builder.key(keyBuffer);
        keyWrapper.recycle();
        builder.expirationInSecs(expirationInSecs);
        final MemcachedRequest request = builder.build();

        final SocketAddress address = consistentHash.get(keyBuffer.toByteBuffer());
        if (address == null) {
            builder.recycle();
            return false;
        }
        try {
            final Object result = send(address, request, writeTimeoutInMillis, responseTimeoutInMillis);
            if (result instanceof Boolean) {
                return (Boolean) result;
            } else {
                return false;
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            if (logger.isLoggable(Level.SEVERE)) {
                logger.log(Level.SEVERE, "failed to touch. address=" + address + ", request=" + request, ie);
            }
            return false;
        } catch (Exception e) {
            if (logger.isLoggable(Level.SEVERE)) {
                logger.log(Level.SEVERE, "failed to touch. address=" + address + ", request=" + request, e);
            }
            return false;
        } finally {
            builder.recycle();
        }
    }

    @Override
    public boolean noop(final SocketAddress address) {
        return noop(address, writeTimeoutInMillis, responseTimeoutInMillis);
    }

    @Override
    public boolean noop(final SocketAddress address, final long writeTimeoutInMillis, final long responseTimeoutInMillis) {
        if (address == null) {
            return false;
        }
        final MemcachedRequest.Builder builder = MemcachedRequest.Builder.create(false, false, false);
        builder.op(CommandOpcodes.Noop);
        builder.opaque(generateOpaque());
        builder.noReply(false);
        final MemcachedRequest request = builder.build();

        try {
            final Object result = send(address, request, writeTimeoutInMillis, responseTimeoutInMillis);
            if (result instanceof Boolean) {
                return (Boolean) result;
            } else {
                return false;
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            if (logger.isLoggable(Level.SEVERE)) {
                logger.log(Level.SEVERE, "failed to execute the noop operation. address=" + address + ", request=" + request, ie);
            }
            return false;
        } catch (Exception e) {
            if (logger.isLoggable(Level.SEVERE)) {
                logger.log(Level.SEVERE, "failed to execute the noop operation. address=" + address + ", request=" + request, e);
            }
            return false;
        } finally {
            builder.recycle();
        }
    }

    @Override
    public boolean verbosity(final SocketAddress address, final int verbosity) {
        return verbosity(address, verbosity, writeTimeoutInMillis, responseTimeoutInMillis);
    }

    @Override
    public boolean verbosity(final SocketAddress address, final int verbosity, final long writeTimeoutInMillis, final long responseTimeoutInMillis) {
        if (address == null) {
            return false;
        }
        final MemcachedRequest.Builder builder = MemcachedRequest.Builder.create(true, false, false);
        builder.op(CommandOpcodes.Verbosity);
        builder.noReply(false);
        builder.verbosity(verbosity);
        final MemcachedRequest request = builder.build();

        try {
            final Object result = send(address, request, writeTimeoutInMillis, responseTimeoutInMillis);
            if (result instanceof Boolean) {
                return (Boolean) result;
            } else {
                return false;
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            if (logger.isLoggable(Level.SEVERE)) {
                logger.log(Level.SEVERE, "failed to execute the vebosity operation. address=" + address + ", request=" + request, ie);
            }
            return false;
        } catch (Exception e) {
            if (logger.isLoggable(Level.SEVERE)) {
                logger.log(Level.SEVERE, "failed to execute the vebosity operation. address=" + address + ", request=" + request, e);
            }
            return false;
        } finally {
            builder.recycle();
        }
    }

    @Override
    public String version(final SocketAddress address) {
        return version(address, writeTimeoutInMillis, responseTimeoutInMillis);
    }

    @Override
    public String version(final SocketAddress address, final long writeTimeoutInMillis, final long responseTimeoutInMillis) {
        if (address == null) {
            return null;
        }
        final MemcachedRequest.Builder builder = MemcachedRequest.Builder.create(false, false, false);
        builder.op(CommandOpcodes.Version);
        builder.noReply(false);
        final MemcachedRequest request = builder.build();

        try {
            final Object result = send(address, request, writeTimeoutInMillis, responseTimeoutInMillis);
            if (result instanceof String) {
                return (String) result;
            } else {
                return null;
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            if (logger.isLoggable(Level.SEVERE)) {
                logger.log(Level.SEVERE, "failed to execute the version operation. address=" + address + ", request=" + request, ie);
            }
            return null;
        } catch (Exception e) {
            if (logger.isLoggable(Level.SEVERE)) {
                logger.log(Level.SEVERE, "failed to execute the version operation. address=" + address + ", request=" + request, e);
            }
            return null;
        } finally {
            builder.recycle();
        }
    }

    private boolean validateConnectionWithNoopCommand(final Connection<SocketAddress> connection) {
        if (connection == null) {
            return false;
        }
        final MemcachedRequest.Builder builder = MemcachedRequest.Builder.create(false, false, false);
        builder.op(CommandOpcodes.Noop);
        builder.opaque(generateOpaque());
        builder.noReply(false);
        final MemcachedRequest request = builder.build();

        try {
            final GrizzlyFuture<WriteResult<MemcachedRequest[], SocketAddress>> future = connection.write(new MemcachedRequest[]{request});
            if (writeTimeoutInMillis > 0) {
                future.get(writeTimeoutInMillis, TimeUnit.MILLISECONDS);
            } else {
                future.get();
            }
            final Object result = clientFilter.getCorrelatedResponse(connection, request, responseTimeoutInMillis);
            if (result instanceof Boolean) {
                return (Boolean) result;
            } else {
                return false;
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            if (logger.isLoggable(Level.SEVERE)) {
                logger.log(Level.SEVERE, "failed to execute the noop operation. connection=" + connection + ", request=" + request, ie);
            }
            return false;
        } catch (Exception e) {
            if (logger.isLoggable(Level.SEVERE)) {
                logger.log(Level.SEVERE, "failed to execute the noop operation. connection=" + connection + ", request=" + request, e);
            }
            return false;
        } finally {
            builder.recycle();
        }
    }

    private boolean validateConnectionWithVersionCommand(final Connection<SocketAddress> connection) {
        if (connection == null) {
            return false;
        }
        final MemcachedRequest.Builder builder = MemcachedRequest.Builder.create(false, false, false);
        builder.op(CommandOpcodes.Version);
        builder.noReply(false);
        final MemcachedRequest request = builder.build();

        try {
            final GrizzlyFuture<WriteResult<MemcachedRequest[], SocketAddress>> future = connection.write(new MemcachedRequest[]{request});
            if (writeTimeoutInMillis > 0) {
                future.get(writeTimeoutInMillis, TimeUnit.MILLISECONDS);
            } else {
                future.get();
            }
            if (clientFilter == null) {
                throw new IllegalStateException("client filter must be not null");
            }
            final Object result = clientFilter.getCorrelatedResponse(connection, request, responseTimeoutInMillis);
            return result instanceof String;
        } catch (TimeoutException te) {
            if (logger.isLoggable(Level.SEVERE)) {
                logger.log(Level.SEVERE, "failed to check the connection. connection=" + connection, te);
            }
            return false;
        } catch (ExecutionException ee) {
            if (logger.isLoggable(Level.SEVERE)) {
                logger.log(Level.SEVERE, "failed to check the connection. connection=" + connection, ee);
            }
            return false;
        } catch (InterruptedException ie) {
            if (logger.isLoggable(Level.SEVERE)) {
                logger.log(Level.SEVERE, "failed to check the connection. connection=" + connection, ie);
            }
            Thread.currentThread().interrupt();
            return false;
        } finally {
            builder.recycle();
        }
    }

    private void sendNoReply(final SocketAddress address, final MemcachedRequest request) throws PoolExhaustedException, NoValidObjectException, InterruptedException {
        if (address == null) {
            throw new IllegalArgumentException("address must be not null");
        }
        if (request == null) {
            throw new IllegalArgumentException("request must be not null");
        }
        if (connectionPool == null) {
            throw new IllegalStateException("connection pool must be not null");
        }

        final Connection<SocketAddress> connection;
        try {
            connection = connectionPool.borrowObject(address, connectTimeoutInMillis);
        } catch (PoolExhaustedException pee) {
            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, "failed to get the connection. address=" + address + ", timeout=" + connectTimeoutInMillis + "ms", pee);
            }
            throw pee;
        } catch (NoValidObjectException nvoe) {
            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, "failed to get the connection. address=" + address + ", timeout=" + connectTimeoutInMillis + "ms", nvoe);
            }
            removeServer(address, false);
            throw nvoe;
        } catch (InterruptedException ie) {
            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, "failed to get the connection. address=" + address + ", timeout=" + connectTimeoutInMillis + "ms", ie);
            }
            throw ie;
        }
        try {
            if (request.isNoReply()) {
                connection.write(new MemcachedRequest[]{request}, new CompletionHandler<WriteResult<MemcachedRequest[], SocketAddress>>() {
                    @Override
                    public void cancelled() {
                        if (logger.isLoggable(Level.SEVERE)) {
                            logger.log(Level.SEVERE, "failed to send the request. request={0}, connection={1}", new Object[]{request, connection});
                        }
                    }

                    @Override
                    public void failed(Throwable t) {
                        if (logger.isLoggable(Level.SEVERE)) {
                            logger.log(Level.SEVERE, "failed to send the request. request=" + request + ", connection=" + connection, t);
                        }
                    }

                    @Override
                    public void completed(WriteResult<MemcachedRequest[], SocketAddress> result) {
                    }

                    @Override
                    public void updated(WriteResult<MemcachedRequest[], SocketAddress> result) {
                    }
                });
            }
        } finally {
            try {
                connectionPool.returnObject(address, connection);
            } catch (Exception e) {
                if (logger.isLoggable(Level.SEVERE)) {
                    logger.log(Level.SEVERE, "failed to return the connection. address=" + address + ", connection=" + connection, e);
                }
            }
        }
    }

    private boolean sendNoReplySafely(final Connection<SocketAddress> connection, final MemcachedRequest request) {
        if (connection == null) {
            throw new IllegalArgumentException("connection must be not null");
        }
        if (request == null) {
            throw new IllegalArgumentException("request must be not null");
        }
        if (request.isNoReply()) {
            GrizzlyFuture future = connection.write(new MemcachedRequest[]{request});
            try {
                future.get(writeTimeoutInMillis, TimeUnit.MILLISECONDS);
            } catch (InterruptedException ie) {
                if (logger.isLoggable(Level.SEVERE)) {
                    logger.log(Level.SEVERE, "failed to check the connection. connection=" + connection, ie);
                }
                Thread.currentThread().interrupt();
                return false;
            } catch (ExecutionException ee) {
                if (logger.isLoggable(Level.SEVERE)) {
                    logger.log(Level.SEVERE, "failed to check the connection. connection=" + connection, ee);
                }
                return false;
            } catch (TimeoutException te) {
                if (logger.isLoggable(Level.SEVERE)) {
                    logger.log(Level.SEVERE, "failed to check the connection. connection=" + connection, te);
                }
                return false;
            }
        }
        return true;
    }

    private Object send(final SocketAddress address,
                        final MemcachedRequest request,
                        final long writeTimeoutInMillis,
                        final long responseTimeoutInMillis) throws TimeoutException, InterruptedException, PoolExhaustedException, NoValidObjectException {
        if (address == null) {
            throw new IllegalArgumentException("address must be not null");
        }
        if (request == null) {
            throw new IllegalArgumentException("request must be not null");
        }
        if (request.isNoReply()) {
            throw new IllegalStateException("request type is no reply");
        }
        return sendInternal(address, new MemcachedRequest[]{request}, writeTimeoutInMillis, responseTimeoutInMillis, null);
    }

    private void sendGetMulti(final SocketAddress address,
                                   final List<BufferWrapper<K>> keyList,
                                   final long writeTimeoutInMillis,
                                   final long responseTimeoutInMillis,
                                   final Map<K, V> result) throws ExecutionException, TimeoutException, InterruptedException, PoolExhaustedException, NoValidObjectException {
        if (address == null || keyList == null || keyList.isEmpty() || result == null) {
            return;
        }

        // make multi requests based on key list
        final MemcachedRequest[] requests = new MemcachedRequest[keyList.size()];
        final BufferWrapper.BufferType keyType = !keyList.isEmpty() ? keyList.get(0).getType() : null;
        for (int i = 0; i < keyList.size(); i++) {
            final MemcachedRequest.Builder builder = MemcachedRequest.Builder.create(false, true, false);
            builder.originKeyType(keyType);
            builder.originKey(keyList.get(i).getOrigin());
            builder.key(keyList.get(i).getBuffer());
            if (i == keyList.size() - 1) {
                builder.noReply(false);
                builder.op(CommandOpcodes.Get);
            } else {
                builder.noReply(true);
                builder.op(CommandOpcodes.GetQ);
                builder.opaque(generateOpaque());
            }
            requests[i] = builder.build();
            builder.recycle();
        }
        sendInternal(address, requests, writeTimeoutInMillis, responseTimeoutInMillis, result);
    }

    private void sendSetMulti(final SocketAddress address,
                                         final List<BufferWrapper<K>> keyList,
                                         final Map<K, V> map,
                                         final int expirationInSecs,
                                         final long writeTimeoutInMillis,
                                         final long responseTimeoutInMillis,
                                         final Map<K, Boolean> result) throws ExecutionException, TimeoutException, InterruptedException, PoolExhaustedException, NoValidObjectException {
        if (address == null || keyList == null || keyList.isEmpty() || result == null) {
            return;
        }

        // make multi requests based on key list
        final MemcachedRequest[] requests = new MemcachedRequest[keyList.size()];
        final BufferWrapper.BufferType keyType = !keyList.isEmpty() ? keyList.get(0).getType() : null;
        for (int i = 0; i < keyList.size(); i++) {
            final MemcachedRequest.Builder builder = MemcachedRequest.Builder.create(true, true, true);
            if (i == keyList.size() - 1) {
                builder.op(CommandOpcodes.Set);
                builder.noReply(false);
                builder.opaque(0);
            } else {
                builder.op(CommandOpcodes.SetQ);
                builder.noReply(true);
                builder.opaque(generateOpaque());
            }
            final K originKey = keyList.get(i).getOrigin();
            builder.originKeyType(keyType);
            builder.originKey(originKey);
            builder.key(keyList.get(i).getBuffer());
            final BufferWrapper valueWrapper = BufferWrapper.wrap(map.get(originKey), transport.getMemoryManager());
            builder.value(valueWrapper.getBuffer());
            builder.flags(valueWrapper.getType().flags);
            valueWrapper.recycle();
            builder.expirationInSecs(expirationInSecs);
            requests[i] = builder.build();
            builder.recycle();
        }
        sendInternal(address, requests, writeTimeoutInMillis, responseTimeoutInMillis, result);
    }

    private Object sendInternal(final SocketAddress address,
                                       final MemcachedRequest[] requests,
                                       final long writeTimeoutInMillis,
                                       final long responseTimeoutInMillis,
                                       final Map<K, ?> result) throws PoolExhaustedException, NoValidObjectException, InterruptedException, TimeoutException {
        if (address == null || requests == null || requests.length == 0) {
            return null;
        }
        if (connectionPool == null) {
            throw new IllegalStateException("connection pool must be not null");
        }
        if (clientFilter == null) {
            throw new IllegalStateException("client filter must be not null");
        }

        final boolean isMulti = requests.length > 1;
        Connection<SocketAddress> connection = null;
        for (int i = 0; i <= RETRY_COUNT; i++) {
            try {
                connection = connectionPool.borrowObject(address, connectTimeoutInMillis);
            } catch (PoolExhaustedException pee) {
                if (logger.isLoggable(Level.FINER)) {
                    logger.log(Level.FINER, "failed to get the connection. address=" + address + ", timeout=" + connectTimeoutInMillis + "ms", pee);
                }
                throw pee;
            } catch (NoValidObjectException nvoe) {
                if (logger.isLoggable(Level.FINER)) {
                    logger.log(Level.FINER, "failed to get the connection. address=" + address + ", timeout=" + connectTimeoutInMillis + "ms", nvoe);
                }
                removeServer(address, false);
                throw nvoe;
            } catch (InterruptedException ie) {
                if (logger.isLoggable(Level.FINER)) {
                    logger.log(Level.FINER, "failed to get the connection. address=" + address + ", timeout=" + connectTimeoutInMillis + "ms", ie);
                }
                throw ie;
            }
            try {
                final GrizzlyFuture<WriteResult<MemcachedRequest[], SocketAddress>> future = connection.write(requests);
                if (writeTimeoutInMillis > 0) {
                    future.get(writeTimeoutInMillis, TimeUnit.MILLISECONDS);
                } else {
                    future.get();
                }
            } catch (ExecutionException ee) {
                // invalid connection
                try {
                    connectionPool.removeObject(address, connection);
                } catch (Exception e) {
                    if (logger.isLoggable(Level.SEVERE)) {
                        logger.log(Level.SEVERE, "failed to remove the connection. address=" + address + ", connection=" + connection, e);
                    }
                }
                connection = null;
                // retry
                continue;
            }
            break;
        }
        if (connection == null) {
            removeServer(address, false);
            return result;
        } else {
            try {
                if (!isMulti) {
                    return clientFilter.getCorrelatedResponse(connection, requests[0], responseTimeoutInMillis);
                } else {
                    return clientFilter.getMultiResponse(connection, requests, responseTimeoutInMillis, result);
                }
            } finally {
                try {
                    connectionPool.returnObject(address, connection);
                } catch (Exception e) {
                    if (logger.isLoggable(Level.SEVERE)) {
                        logger.log(Level.SEVERE, "failed to return the connection. address=" + address + ", connection=" + connection, e);
                    }
                }
            }
        }
    }

    private static <K> void recycleBufferWrappers(List<BufferWrapper<K>> bufferWrapperList) {
        if (bufferWrapperList == null) {
            return;
        }
        for (BufferWrapper<K> wrapper : bufferWrapperList) {
            wrapper.recycle();
        }
    }

    private static int generateOpaque() {
        return opaqueIndex.getAndIncrement() & 0x7fffffff;
    }

    private class HealthMonitorTask implements Runnable {

        private final Map<SocketAddress, Boolean> failures = new ConcurrentHashMap<SocketAddress, Boolean>();
        private final Map<SocketAddress, Boolean> revivals = new ConcurrentHashMap<SocketAddress, Boolean>();
        private final AtomicBoolean running = new AtomicBoolean();

        public boolean failure(final SocketAddress address) {
            if (address == null) {
                return true;
            }
            if (failures.get(address) == null && revivals.get(address) == null) {
                failures.put(address, Boolean.TRUE);
                return true;
            } else {
                return false;
            }
        }

        @SuppressWarnings("unchecked")
        @Override
        public void run() {
            if (transport == null) {
                throw new IllegalStateException("transport must be not null");
            }
            if (!running.compareAndSet(false, true)) {
                return;
            }
            try {
                revivals.clear();
                final Set<SocketAddress> failuresSet = failures.keySet();
                if (logger.isLoggable(Level.FINE)) {
                    logger.log(Level.FINE, "try to check the failures in health monitor. failed list hint={0}, interval={1}secs", new Object[]{failuresSet, healthMonitorIntervalInSecs});
                } else if (logger.isLoggable(Level.INFO) && !failuresSet.isEmpty()) {
                    logger.log(Level.INFO, "try to check the failures in health monitor. failed list hint={0}, interval={1}secs", new Object[]{failuresSet, healthMonitorIntervalInSecs});
                }
                for (SocketAddress failure : failuresSet) {
                    try {
                        // get the temporary connection
                        final ConnectorHandler<SocketAddress> connectorHandler = TCPNIOConnectorHandler.builder(transport).setReuseAddress(true).build();
                        Future<Connection> future = connectorHandler.connect(failure);
                        final Connection<SocketAddress> connection;
                        try {
                            if (connectTimeoutInMillis < 0) {
                                connection = future.get();
                            } else {
                                connection = future.get(connectTimeoutInMillis, TimeUnit.MILLISECONDS);
                            }
                        } catch (InterruptedException ie) {
                            if (logger.isLoggable(Level.SEVERE)) {
                                logger.log(Level.SEVERE, "failed to get the connection in health monitor. address=" + failure, ie);
                            }
                            continue;
                        } catch (ExecutionException ee) {
                            if (logger.isLoggable(Level.SEVERE)) {
                                logger.log(Level.SEVERE, "failed to get the connection in health monitor. address=" + failure, ee);
                            }
                            continue;
                        } catch (TimeoutException te) {
                            if (logger.isLoggable(Level.SEVERE)) {
                                logger.log(Level.SEVERE, "failed to get the connection in health monitor. address=" + failure, te);
                            }
                            continue;
                        }
                        if (validateConnectionWithVersionCommand(connection)) {
                            failures.remove(failure);
                            revivals.put(failure, Boolean.TRUE);
                        }
                        connection.closeSilently();
                    } catch (Throwable t) {
                        if (logger.isLoggable(Level.SEVERE)) {
                            logger.log(Level.SEVERE, "unexpected exception thrown", t);
                        }
                    }
                }
                final Set<SocketAddress> revivalsSet = revivals.keySet();
                if (logger.isLoggable(Level.FINE)) {
                    logger.log(Level.FINE, "try to restore revivals in health monitor. revival list hint={0}, interval={1}secs", new Object[]{revivalsSet, healthMonitorIntervalInSecs});
                } else if (logger.isLoggable(Level.INFO) && !revivalsSet.isEmpty()) {
                    logger.log(Level.INFO, "try to restore revivals in health monitor. revival list hint={0}, interval={1}secs", new Object[]{revivalsSet, healthMonitorIntervalInSecs});
                }
                for (SocketAddress revival : revivalsSet) {
                    if (!addServer(revival, false)) {
                        if (logger.isLoggable(Level.WARNING)) {
                            logger.log(Level.WARNING, "the revival was failed again in health monitor. revival={0}", revival);
                        }
                        failures.put(revival, Boolean.TRUE);
                    }
                }
            } finally {
                running.set(false);
            }
        }
    }

    public static class Builder<K, V> implements CacheBuilder<K, V> {

        private final String cacheName;
        private final GrizzlyMemcachedCacheManager manager;
        private final TCPNIOTransport transport;
        private Set<SocketAddress> servers;
        private long connectTimeoutInMillis = 5000; // 5secs
        private long writeTimeoutInMillis = 5000; // 5secs
        private long responseTimeoutInMillis = 10000; // 10secs

        private long healthMonitorIntervalInSecs = 60; // 1 min
        private boolean failover = true;

        // connection pool config
        private int minConnectionPerServer = 5;
        private int maxConnectionPerServer = Integer.MAX_VALUE;
        private long keepAliveTimeoutInSecs = 30 * 60; // 30 min
        private boolean allowDisposableConnection = false;
        private boolean borrowValidation = false;
        private boolean returnValidation = false;

        private final ZKClient zkClient;

        public Builder(final String cacheName, final GrizzlyMemcachedCacheManager manager, final TCPNIOTransport transport) {
            this.cacheName = cacheName;
            this.manager = manager;
            this.transport = transport;
            this.zkClient = manager.getZkClient();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public GrizzlyMemcachedCache<K, V> build() {
            final GrizzlyMemcachedCache<K, V> cache = new GrizzlyMemcachedCache<K, V>(this);
            cache.start();
            if (!manager.addCache(cache)) {
                cache.stop();
                throw new IllegalStateException("failed to add the cache because the CacheManager already stopped or the same cache name existed");
            }
            return cache;
        }

        /**
         * Set global connect-timeout
         * <p/>
         * If the given param is negative, the timeout is infite.
         * Default is 5000.
         *
         * @param connectTimeoutInMillis connect-timeout in milli-seconds
         * @return this builder
         */
        public Builder<K, V> connectTimeoutInMillis(final long connectTimeoutInMillis) {
            this.connectTimeoutInMillis = connectTimeoutInMillis;
            return this;
        }

        /**
         * Set global write-timeout
         * <p/>
         * If the given param is negative, the timeout is infite.
         * Default is 5000.
         *
         * @param writeTimeoutInMillis write-timeout in milli-seconds
         * @return this builder
         */
        public Builder<K, V> writeTimeoutInMillis(final long writeTimeoutInMillis) {
            this.writeTimeoutInMillis = writeTimeoutInMillis;
            return this;
        }

        /**
         * Set global response-timeout
         * <p/>
         * If the given param is negative, the timeout is infite.
         * Default is 10000.
         *
         * @param responseTimeoutInMillis response-timeout in milli-seconds
         * @return this builder
         */
        public Builder<K, V> responseTimeoutInMillis(final long responseTimeoutInMillis) {
            this.responseTimeoutInMillis = responseTimeoutInMillis;
            return this;
        }

        /**
         * Set connection pool's min
         * <p/>
         * Default is 5.
         *
         * @param minConnectionPerServer connection pool's min
         * @return this builder
         * @see BaseObjectPool.Builder#min(int)
         */
        public Builder<K, V> minConnectionPerServer(final int minConnectionPerServer) {
            this.minConnectionPerServer = minConnectionPerServer;
            return this;
        }

        /**
         * Set connection pool's max
         * <p/>
         * Default is {@link Integer#MAX_VALUE}
         *
         * @param maxConnectionPerServer connection pool's max
         * @return this builder
         * @see BaseObjectPool.Builder#max(int)
         */
        public Builder<K, V> maxConnectionPerServer(final int maxConnectionPerServer) {
            this.maxConnectionPerServer = maxConnectionPerServer;
            return this;
        }

        /**
         * Set connection pool's KeepAliveTimeout
         * <p/>
         * Default is 1800.
         *
         * @param keepAliveTimeoutInSecs connection pool's KeepAliveTimeout in seconds
         * @return this builder
         * @see BaseObjectPool.Builder#keepAliveTimeoutInSecs(long)
         */
        public Builder<K, V> keepAliveTimeoutInSecs(final long keepAliveTimeoutInSecs) {
            this.keepAliveTimeoutInSecs = keepAliveTimeoutInSecs;
            return this;
        }

        /**
         * Set health monitor's interval
         * <p/>
         * This cache will schedule {@link HealthMonitorTask} with this interval.
         * {@link HealthMonitorTask} will check the failure servers periodically and detect the revived server.
         * If the given parameter is negative, this cache never schedules {@link HealthMonitorTask}
         * so this behavior is similar to seting {@code failover} to be false.
         * Default is 60.
         *
         * @param healthMonitorIntervalInSecs interval in seconds
         * @return this builder
         */
        public Builder<K, V> healthMonitorIntervalInSecs(final long healthMonitorIntervalInSecs) {
            this.healthMonitorIntervalInSecs = healthMonitorIntervalInSecs;
            return this;
        }

        /**
         * Allow or disallow disposable connections
         * <p/>
         * Default is false.
         *
         * @param allowDisposableConnection true if this cache allows disposable connections
         * @return this builder
         */
        public Builder<K, V> allowDisposableConnection(final boolean allowDisposableConnection) {
            this.allowDisposableConnection = allowDisposableConnection;
            return this;
        }

        /**
         * Enable or disable the connection validation when the connection is borrowed from the connection pool
         * <p/>
         * Default is false.
         *
         * @param borrowValidation true if this cache should make sure the borrowed connection is valid
         * @return this builder
         */
        public Builder<K, V> borrowValidation(final boolean borrowValidation) {
            this.borrowValidation = borrowValidation;
            return this;
        }

        /**
         * Enable or disable the connection validation when the connection is returned to the connection pool
         * <p/>
         * Default is false.
         *
         * @param returnValidation true if this cache should make sure the returned connection is valid
         * @return this builder
         */
        public Builder<K, V> returnValidation(final boolean returnValidation) {
            this.returnValidation = returnValidation;
            return this;
        }

        /**
         * Set initial servers
         *
         * @param servers server set
         * @return this builder
         */
        public Builder<K, V> servers(final Set<SocketAddress> servers) {
            this.servers = new HashSet<SocketAddress>(servers);
            return this;
        }

        /**
         * Enable or disable failover/failback
         * <p/>
         * Default is true.
         *
         * @param failover true if this cache should support failover/failback when the server is failed or revived
         * @return this builder
         */
        public Builder<K, V> failover(final boolean failover) {
            this.failover = failover;
            return this;
        }
    }

    @Override
    public String toString() {
        return "GrizzlyMemcachedCache{" +
                "cacheName=" + cacheName +
                ", transport=" + transport +
                ", connectTimeoutInMillis=" + connectTimeoutInMillis +
                ", writeTimeoutInMillis=" + writeTimeoutInMillis +
                ", responseTimeoutInMillis=" + responseTimeoutInMillis +
                ", connectionPool=" + connectionPool +
                ", initialServers=" + initialServers +
                ", healthMonitorIntervalInSecs=" + healthMonitorIntervalInSecs +
                ", failover=" + failover +
                ", consistentHash=" + consistentHash +
                '}';
    }
}
