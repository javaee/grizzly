/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved.
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
package org.glassfish.grizzly.connectionpool;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.glassfish.grizzly.CompletionHandler;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.ConnectorHandler;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.GrizzlyFuture;
import org.glassfish.grizzly.threadpool.GrizzlyExecutorService;
import org.glassfish.grizzly.threadpool.ThreadPoolConfig;
import org.glassfish.grizzly.utils.DelayedExecutor;
import org.glassfish.grizzly.utils.DelayedExecutor.DelayQueue;
import org.glassfish.grizzly.utils.Futures;
import static org.glassfish.grizzly.connectionpool.SingleEndpointPool.*;

/**
 * The multi endpoint {@link Connection} pool implementation where each endpoint
 * sub-pool is represented by {@link SingleEndpointPool} and referenced by an {@link EndpointKey}.
 * 
 * There are number of configuration options supported by the <tt>MultiEndpointPool</tt>:
 *      - <tt>maxConnectionsPerEndpoint</tt>: the maximum number of {@link Connection}s each
 *                      {@link SingleEndpointPool} sub-pool is allowed to have;
 *      - <tt>maxConnectionsTotal</tt>: the total maximum number of {@link Connection}s to be kept by the pool;
 *      - <tt>keepAliveTimeoutMillis</tt>: the maximum number of milliseconds an idle {@link Connection}
 *                                         will be kept in the pool. The idle {@link Connection}s will be
 *                                         closed until the pool size is greater than the <tt>corePoolSize</tt>;
 *      - <tt>keepAliveCheckIntervalMillis</tt>: the interval, which specifies how often the pool will
 *                                               perform idle {@link Connection}s check;
 *      - <tt>reconnectDelayMillis</tt>: the delay to be used before the pool will repeat the attempt to connect to
 *                                       the endpoint after previous connect had failed.
 * 
 * @author Alexey Stashok
 */
public class MultiEndpointPool<E> {

    private static final Logger LOGGER = Grizzly.logger(MultiEndpointPool.class);
    /**
     * Returns single endpoint pool {@link Builder}.
     * 
     * @param endpointType endpoint address type, for example
     *        {@link java.net.SocketAddress} for TCP and UDP transports
     * @return {@link Builder} 
     */
    public static <T> Builder<T> builder(Class<T> endpointType) {
        return new Builder<T>();
    }
    
    /**
     * Maps endpoint -to- SingleEndpointPool
     */
    protected final Map<EndpointKey<E>, SingleEndpointPool<E>> endpointToPoolMap =
            new ConcurrentHashMap<EndpointKey<E>, SingleEndpointPool<E>>();
    /**
     * Maps Connection -to- ConnectionInfo
     */
    private final Map<Connection, ConnectionInfo<E>> connectionToSubPoolMap =
            new ConcurrentHashMap<Connection, ConnectionInfo<E>>();

    /**
     * Sync for endpointToPoolMap updates
     */
    protected final Object poolSync = new Object();
    /**
     * The pool's counters sync (poolSize, totalPendingConnections etc)
     */
    private final Object countersSync = new Object();
    
    /**
     * close flag
     */
    private boolean isClosed;
    /**
     * current pool size
     */
    private int poolSize;
    /**
     * Number of connections we're currently trying to establish and waiting for the result
     */    
    private int totalPendingConnections;
    
    /**
     * Priority queue, that helps to distribute connections fairly in situation
     * when the max number of connections is reached
     */
    private final Chain<EndpointPoolImpl> maxPoolSizeHitsChain =
            new Chain<EndpointPoolImpl>();
    
    /**
     * Own/internal {@link DelayedExecutor} to be used for keep-alive and reconnect
     * mechanisms, if one (DelayedExecutor} was not specified by user
     */
    private final DelayedExecutor ownDelayedExecutor;
    /**
     * DelayQueue for connect timeout mechanism
     */
    private final DelayQueue<ConnectTimeoutTask> connectTimeoutQueue;
    /**
     * DelayQueue for reconnect mechanism
     */
    private final DelayQueue<ReconnectTask> reconnectQueue;
    /**
     * DelayQueue for keep-alive mechanism
     */
    private final DelayQueue<KeepAliveCleanerTask> keepAliveCleanerQueue;

    /**
     * The default {@link ConnectorHandler} used to establish new
     * {@link Connection}s is none is specified by {@link EndpointKey}
     */
    private final ConnectorHandler<E> defaultConnectorHandler;
    /**
     * the maximum number of {@link Connection}s each
     * {@link SingleEndpointPool} sub-pool is allowed to have
     */
    private final int maxConnectionsPerEndpoint;
    /**
     * the total maximum number of {@link Connection}s to be kept by the pool
     */
    private final int maxConnectionsTotal;
    
    /**
     * Connect timeout, after which, if a connection is not established, it is
     * considered failed
     */
    private final long connectTimeoutMillis;
    /**
     * the delay to be used before the pool will repeat the attempt to connect to
     * the endpoint after previous connect had failed
     */
    private final long reconnectDelayMillis;
    /**
     * the maximum number of milliseconds an idle {@link Connection} will be kept
     * in the pool. The idle {@link Connection}s will be closed till the pool
     * size is greater than <tt>corePoolSize</tt>
     */    
    private final long keepAliveTimeoutMillis;
    /**
     * the interval, which specifies how often the pool will perform idle {@link Connection}s check
     */
    private final long keepAliveCheckIntervalMillis;

    /**
     * Maximum number of reconnect attempts.
     */
    private final int maxReconnectAttempts;
    
    /**
     * Constructs MultiEndpointPool instance.
     * 
     * @param defaultConnectorHandler the default {@link ConnectorHandler} to be used to establish new {@link Connection}s
     * @param maxConnectionsPerEndpoint the maximum number of {@link Connection}s single endpoint sub-pool is allowed to have
     * @param maxConnectionsTotal the total maximum number of {@link Connection}s the pool is allowed to have
     * @param delayedExecutor custom {@link DelayedExecutor} to be used by keep-alive and reconnect mechanisms
     * @param connectTimeoutMillis timeout, after which, if a connection is not established, it is considered failed
     * @param keepAliveTimeoutMillis the maximum number of milliseconds an idle {@link Connection} will be kept in the pool
     * @param keepAliveCheckIntervalMillis the interval, which specifies how often the pool will perform idle {@link Connection}s check
     * @param reconnectDelayMillis the delay to be used before the pool will repeat the attempt to connect to the endpoint after previous connect had failed
     * @param maxReconnectAttempts the maximum number of reconnect attempts that may be made before failure notification.
     */
    protected MultiEndpointPool(
            final ConnectorHandler<E> defaultConnectorHandler,
            final int maxConnectionsPerEndpoint,
            final int maxConnectionsTotal,
            DelayedExecutor delayedExecutor,
            final long connectTimeoutMillis,
            final long keepAliveTimeoutMillis,
            final long keepAliveCheckIntervalMillis,
            final long reconnectDelayMillis,
            final int maxReconnectAttempts) {
        this.defaultConnectorHandler = defaultConnectorHandler;
        this.maxConnectionsPerEndpoint = maxConnectionsPerEndpoint;
        this.maxConnectionsTotal = maxConnectionsTotal;
        
        this.connectTimeoutMillis = connectTimeoutMillis;
        this.reconnectDelayMillis = reconnectDelayMillis;
        this.keepAliveTimeoutMillis = keepAliveTimeoutMillis;
        this.keepAliveCheckIntervalMillis = keepAliveCheckIntervalMillis;
        this.maxReconnectAttempts = maxReconnectAttempts;
        
        if (delayedExecutor == null) {
            final ThreadPoolConfig tpc = ThreadPoolConfig.defaultConfig()
                    .setPoolName("connection-pool-delays-thread-pool")
                    .setCorePoolSize(1)
                    .setMaxPoolSize(1);

            ownDelayedExecutor = new DelayedExecutor(
                    GrizzlyExecutorService.createInstance(tpc));
            ownDelayedExecutor.start();
            delayedExecutor = ownDelayedExecutor;
        } else {
            ownDelayedExecutor = null;
        }
        
        if (connectTimeoutMillis >= 0) {
            connectTimeoutQueue = delayedExecutor.createDelayQueue(
                    new ConnectTimeoutWorker(),
                    new ConnectTimeoutTaskResolver());
        } else {
            connectTimeoutQueue = null;
        }
        
        if (reconnectDelayMillis >= 0) {
            reconnectQueue = delayedExecutor.createDelayQueue(new Reconnector(),
                    new ReconnectTaskResolver());
        } else {
            reconnectQueue = null;
        }
        
        if (keepAliveTimeoutMillis >= 0) {
            keepAliveCleanerQueue = delayedExecutor.createDelayQueue(
                    new KeepAliveCleaner(), new KeepAliveCleanerTaskResolver());
        } else {
            keepAliveCleanerQueue = null;
        }        
    }
    
    /**
     * Returns the current pool size.
     * This value includes connected and connecting (connect in progress)
     * {@link Connection}s.
     */
    public int size() {
        synchronized (countersSync) {
            return poolSize + totalPendingConnections;
        }
    }
    
    /**
     * Returns the number of connected {@link Connection}s in the pool.
     * Unlike {@link #size()} the value doesn't include connecting
     * (connect in progress) {@link Connection}s.
     */
    public int getOpenConnectionsCount() {
        synchronized (countersSync) {
            return poolSize;
        }
    }
    
    /**
     * Returns <tt>true</tt> is maximum number of {@link Connection}s the pool
     * can keep is reached and no new {@link Connection} can established, or
     * <tt>false</tt> otherwise.
     */
    public boolean isMaxCapacityReached() {
        synchronized (countersSync) {
            return maxConnectionsTotal != -1
                    && poolSize + totalPendingConnections >= maxConnectionsTotal;
        }
    }

    /**
     * Returns <tt>true</tt> if the {@link Connection} is registered in the pool
     * no matter if it's currently in busy or ready state, or <tt>false</tt> if
     * the {@link Connection} is not registered in the pool.
     * 
     * @param connection {@link Connection}
     * @return <tt>true</tt> if the {@link Connection} is registered in the pool
     * no matter if it's currently in busy or ready state, or <tt>false</tt> if
     * the {@link Connection} is not registered in the pool
     */
    public boolean isRegistered(final Connection connection) {
        return connectionToSubPoolMap.get(connection) != null;
    }
    
    /**
     * Returns <tt>true</tt> only if the {@link Connection} is registered in
     * the pool and is currently in busy state (used by a user), otherwise
     * returns <tt>false</tt>.
     * 
     * @param connection {@link Connection}
     * @return <tt>true</tt> only if the {@link Connection} is registered in
     * the pool and is currently in busy state (used by a user), otherwise
     * returns <tt>false</tt>
     */
    public boolean isBusy(final Connection connection) {
        final ConnectionInfo<E> info = connectionToSubPoolMap.get(connection);
        return info != null && info.endpointPool.isBusy0(info);
    }
    
    /**
     * Returns pooled {@link ConnectionInfo}, that might be used for monitoring
     * reasons, or <tt>null</tt> if the {@link Connection} does not belong to
     * this pool.
     * 
     * @param connection {@link Connection}
     * @return pooled {@link ConnectionInfo}, that might be used for monitoring
     * reasons, or <tt>null</tt> if the {@link Connection} does not belong to
     * this pool
     */
    public ConnectionInfo<E> getConnectionInfo(final Connection connection) {
        return connectionToSubPoolMap.get(connection);
    }
    
    /**
     * Obtains a {@link Connection} to the specified endpoint from the pool in
     * non-blocking/asynchronous fashion.
     * 
     * Returns a {@link GrizzlyFuture} representing the pending result of the
     * non-blocking/asynchronous obtain task.
     * Future's <tt>get</tt> method will return the {@link Connection} once it
     * becomes available in the pool.
     *
     * <p>
     * If you would like to immediately block waiting
     * for a {@link Connection}, you can use constructions of the form
     * <tt>connection = pool.take(endpointKey).get();</tt>
     * 
     * <p> Note: returned {@link GrizzlyFuture} must be checked and released
     * properly. It must not be forgotten, because a {@link Connection}, that
     * might be assigned as a result of {@link GrizzlyFuture} has to be returned
     * to the pool. If you gave up on waiting for a {@link Connection} or you
     * are not interested in the {@link Connection} anymore, the proper release
     * code has to look like:
     * <pre>
     * if (!future.cancel(false)) {
     *     // means Connection is ready
     *     pool.release(future.get());
     * }
     * </pre>
     * 
     * @param endpointKey {@link EndpointKey}, that represents an endpoint
     * @return {@link GrizzlyFuture}
     */
    public GrizzlyFuture<Connection> take(final EndpointKey<E> endpointKey) {
        try {
            final SingleEndpointPool<E> sePool =
                    obtainSingleEndpointPool(endpointKey);

            return sePool.take();
        } catch (IOException e) {
            return Futures.createReadyFuture(e);
        }
    }
    
    /**
     * Obtains a {@link Connection} to the specified endpoint from the pool
     * in non-blocking/asynchronous fashion.
     * The passed {@link CompletionHandler} will be notified about the result of the
     * non-blocking/asynchronous obtain task.
     * 
     * @param endpointKey {@link EndpointKey}, that represents an endpoint
     */
    public void take(final EndpointKey<E> endpointKey,
            final CompletionHandler<Connection> completionHandler) {
        if (completionHandler == null) {
            throw new IllegalArgumentException("The completionHandler argument can not be null");
        }
        
        try {
            final SingleEndpointPool<E> sePool = obtainSingleEndpointPool(endpointKey);

            sePool.take(completionHandler);
        } catch (IOException e) {
            completionHandler.failed(e);
        }
    }

    /**
     * Returns the {@link Connection} to the pool.
     * 
     * The {@link Connection} will be returned to the pool only in case it
     * was created by this pool, or it was attached to it using {@link #attach(EndpointKey, org.glassfish.grizzly.Connection)}
     * method.
     * If the {@link Connection} is not registered in the pool - it will be closed.
     * If the {@link Connection} is registered in the pool and already marked as ready - this method call will not have any effect.
     * 
     * If the {@link Connection} was returned - it is illegal to use it until
     * it is retrieved from the pool again.
     * 
     * @param connection the {@link Connection} to return
     * @return <code>true</code> if the connection was successfully released.
     *  If the connection cannot be released, the connection will be closed
     *  and <code>false</code> will be returned.
     */
    public boolean release(final Connection connection) {
        final ConnectionInfo<E> info = connectionToSubPoolMap.get(connection);
        if (info != null) {
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.log(Level.FINE,
                           "Returning {0} to endpoint pool {1}",
                           new Object[] {connection, info.endpointPool});
            }
            // optimize release() call to avoid redundant map lookup
            return info.endpointPool.release0(info);
        } else {
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.log(Level.FINE,
                           "No ConnectionInfo available for {0}.  Closing connection.",
                           connection);
            }
            connection.closeSilently();
            return false;
        }
    }

    /**
     * Attaches "foreign" {@link Connection} to the pool.
     * This method might be used to add to the pool a {@link Connection}, that
     * either has not been created by this pool or has been detached.
     * 
     * @param endpointKey {@link EndpointKey}, that represents an endpoint to
     *              which the the {@link Connection} will be attached
     * @param connection {@link Connection}
     * @return <tt>true</tt> if the {@link Connection} has been successfully attached,
     *              or <tt>false</tt> otherwise. If the {@link Connection} had
     *              been already registered in the pool - the method call doesn't
     *              have any effect and <tt>true</tt> will be returned.
     * @throws IOException thrown if this pool has been already closed
     */
    public boolean attach(final EndpointKey<E> endpointKey,
            final Connection connection)
            throws IOException {

        final SingleEndpointPool<E> sePool = obtainSingleEndpointPool(endpointKey);
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.log(Level.FINE,
                       "Associating foreign connection with pool {0} using endpoint key {1}.",
                       new Object[] {sePool, endpointKey});
        }
        return sePool.attach(connection);
    }
    
    /**
     * Detaches a {@link Connection} from the pool.
     * De-registers the {@link Connection} from the pool and decreases the pool
     * size by 1. It is possible to re-attach the detached {@link Connection}
     * later by calling {@link #attach(org.glassfish.grizzly.connectionpool.EndpointKey, org.glassfish.grizzly.Connection)}.
     * 
     * If the {@link Connection} was not registered in the pool - the
     * method call doesn't have any effect.
     * 
     * @param connection the {@link Connection} to detach
     * @return <code>true</code> if the {@link Connection} was detached, otherwise
     *  returns <code>false</code>
     */
    public boolean detach(final Connection connection) {
        final ConnectionInfo<E> info = connectionToSubPoolMap.get(connection);
        if (info != null && LOGGER.isLoggable(Level.FINE)) {
            LOGGER.log(Level.FINE,
                       "Detaching {0} from endpoint pool {1}.",
                       new Object[] { connection, info.endpointPool});
        }
        return info != null && info.endpointPool.detach(connection);
    }
    
    /**
     * Closes specific endpoint associated pool and releases its resources.
     * 
     * The ready {@link Connection}s associated with the endpoint pool will be
     * closed, the busy {@link Connection}, that are still in use - will be kept open and
     * will be automatically closed when returned to the pool by {@link #release(org.glassfish.grizzly.Connection)}.
     * 
     * @param endpointKey {@link EndpointKey}, that represents an endpoint
     */
    public void close(final EndpointKey<E> endpointKey) {
        final SingleEndpointPool<E> sePool = endpointToPoolMap.remove(endpointKey);
        if (sePool != null) {
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.log(Level.FINE,
                           "Closing pool associated with endpoint key {0}",
                           endpointKey);
            }
            sePool.close();
        }
    }

    /**
     * Closes the pool and releases associated resources.
     * 
     * The ready {@link Connection}s will be closed, the busy {@link Connection},
     * that are still in use - will be kept open and will be automatically
     * closed when returned to the pool by {@link #release(org.glassfish.grizzly.Connection)}.
     */
    public void close() {
        synchronized (poolSync) {
            if (isClosed) {
                return;
            }
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine("Shutting down. Closing all pools; shutting down executors as needed.");
            }
            isClosed = true;

            for (Map.Entry<EndpointKey<E>, SingleEndpointPool<E>> entry :
                    endpointToPoolMap.entrySet()) {
                try {
                    entry.getValue().close();
                } catch (Exception ignore) {
                }
            }

            endpointToPoolMap.clear();

            if (ownDelayedExecutor != null) {
                ownDelayedExecutor.destroy();
            }
        }
    }

    @Override
    public String toString() {
        return "MultiEndpointPool{"
                + "endpoint count=" + endpointToPoolMap.size()
                + "poolSize=" + poolSize
                + ", isClosed=" + isClosed
                + "} " + super.toString();
    }

    /**
     * Obtains {@link SingleEndpointPool} associated with the specific endpoint
     * represented by {@link EndpointKey}. If there is no {@link SingleEndpointPool}
     * associated with the endpoint - the one will be created.
     * 
     * @param endpointKey {@link EndpointKey}, that represents an endpoint
     * @return {@link SingleEndpointPool}
     * @throws IOException if the pool is already closed
     */
    protected SingleEndpointPool<E> obtainSingleEndpointPool(
            final EndpointKey<E> endpointKey) throws IOException {
        SingleEndpointPool<E> sePool = endpointToPoolMap.get(endpointKey);
        if (sePool == null) {
            synchronized (poolSync) {
                checkNotClosed();
                
                sePool = endpointToPoolMap.get(endpointKey);
                if (sePool == null) {
                    if (LOGGER.isLoggable(Level.FINE)) {
                        LOGGER.log(Level.FINE,
                                   "Creating new endpoint pool for key {0}",
                                   endpointKey);
                    }
                    sePool = createSingleEndpointPool(endpointKey);
                    endpointToPoolMap.put(endpointKey, sePool);
                } else if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.log(Level.FINE,
                               "Returning existing pool {0} for key {1}",
                               new Object[]{sePool, endpointKey});
                }
            }
        }
        
        return sePool;
    }
    
    /**
     * Creates {@link SingleEndpointPool} instance.
     * @param endpointKey the endpoint key
     * @return {@link SingleEndpointPool}
     */
    protected SingleEndpointPool<E> createSingleEndpointPool(
            final EndpointKey<E> endpointKey) {
        return new EndpointPoolImpl(endpointKey.getEndpoint(),
                                    endpointKey.getLocalEndpoint(),
                                    endpointKey.getConnectorHandler() == null
                                            ? defaultConnectorHandler
                                            : endpointKey.getConnectorHandler());
    }
    
    /**
     * Method throws {@link IOException} if the pool has been closed.
     */
    protected void checkNotClosed() throws IOException {
        if (isClosed) {
            throw new IOException("The pool is closed");
        }
    }
    
    /**
     * {@link SingleEndpointPool} implementation used by this <tt>MultiEndpointPool</tt>.
     */
    private final class EndpointPoolImpl extends SingleEndpointPool<E> {
        private final Link<EndpointPoolImpl> maxPoolSizeHitsLink =
                new Link<EndpointPoolImpl>(this);
        
        private int maxPoolSizeHits;
        
        public EndpointPoolImpl(final E endpoint,
                                final E localEndpoint,
                final ConnectorHandler<E> connectorHandler) {
            super(connectorHandler,
                endpoint, localEndpoint, 0, maxConnectionsPerEndpoint,
                connectTimeoutQueue, reconnectQueue, keepAliveCleanerQueue,
                connectTimeoutMillis, keepAliveTimeoutMillis,
                keepAliveCheckIntervalMillis, reconnectDelayMillis, maxReconnectAttempts);
        }

        @Override
        protected boolean checkBeforeOpeningConnection() {
            if (isMaxCapacityReached()) {
                return false;
            }
            
            synchronized (countersSync) {
                if (MultiEndpointPool.this.isMaxCapacityReached()) {
                    onMaxPoolSizeHit();
                    return false;
                }
                
                pendingConnections++;
                totalPendingConnections++;
                return true;
            }
        }

        @Override
        void onOpenConnection(final ConnectionInfo<E> info) {
            final Connection connection = info.connection;
            
            connectionToSubPoolMap.put(connection, info);
            
            synchronized (countersSync) {
                totalPendingConnections--;
                poolSize++;
            }
            
            super.onOpenConnection(info);
        }

        @Override
        void onFailedConnection() {
            synchronized (countersSync) {
                totalPendingConnections--;
            }
            
            super.onFailedConnection();
        }

        
        @Override
        void onCloseConnection(final ConnectionInfo<E> info) {
            final Connection connection = info.connection;
            connectionToSubPoolMap.remove(connection);
            
            final EndpointPoolImpl prioritizedPool;
            
            synchronized (countersSync) {
                poolSize--;
                prioritizedPool = getPrioritizedPool();
            }
            
            if (prioritizedPool != null) {
                prioritizedPool.createConnectionIfPossible();
                
                return;
            } 
            
            
            super.onCloseConnection(info);
        }
        
        private void onMaxPoolSizeHit() {
            if (maxPoolSizeHits++ == 0) {
                if (size() > 0) {
                    maxPoolSizeHitsChain.offerLast(maxPoolSizeHitsLink);
                } else { // if max pool size hit on empty pool - raise its priority
                    final Link<EndpointPoolImpl> head =
                            maxPoolSizeHitsChain.getFirstLink();
                    if (head != null) {
                        maxPoolSizeHits = head.getValue().maxPoolSizeHits;
                    }
                    
                    maxPoolSizeHitsChain.offerFirst(maxPoolSizeHitsLink);
                }
            } else {
                final Link<EndpointPoolImpl> prev = maxPoolSizeHitsLink.prev;
                if (prev != null &&
                        maxPoolSizeHits > prev.getValue().maxPoolSizeHits) {
                    maxPoolSizeHitsChain.moveTowardsHead(maxPoolSizeHitsLink);
                }
            }
        }        

        private EndpointPoolImpl getPrioritizedPool() {
            EndpointPoolImpl prioritizedPool;
            final Link<EndpointPoolImpl> firstLink =
                    maxPoolSizeHitsChain.pollFirst();
            
            if (firstLink != null) {
                prioritizedPool = firstLink.getValue();
                prioritizedPool.maxPoolSizeHits = 0;
            } else {
                prioritizedPool = null;
            }
            return prioritizedPool;
        }
    }
    
    /**
     * The Builder class responsible for constructing {@link SingleEndpointPool}.
     * 
     * @param <E> endpoint address type, for example {@link java.net.SocketAddress} for TCP and UDP transports
     */
    public static class Builder<E> {
        /**
         * {@link ConnectorHandler} used to establish new {@link Connection}s
         */
        private ConnectorHandler<E> defaultConnectorHandler;

        /**
         * the maximum number of {@link Connection}s each
         * {@link SingleEndpointPool} sub-pool is allowed to have
         */
        private int maxConnectionsPerEndpoint = 2;
        /**
         * the total maximum number of {@link Connection}s to be kept by the pool
         */
        private int maxConnectionsTotal = 16;
        /**
         * the {@link DelayedExecutor} to be used for keep-alive and
         * reconnect mechanisms
         */
        private DelayedExecutor delayedExecutor;
        /**
         * Connect timeout, after which, if a connection is not established, it is
         * considered failed
         */
        private long connectTimeoutMillis = -1;
        /**
         * the delay to be used before the pool will repeat the attempt to connect to
         * the endpoint after previous connect had failed
         */
        private long reconnectDelayMillis = -1;
        /**
         * Maximum number of attempts that will be made to reconnect before
         * notification of failure occurs.
         */
        private int maxReconnectAttempts = 5;
        /**
         * the maximum number of milliseconds an idle {@link Connection} will be kept
         * in the pool. The idle {@link Connection}s will be closed till the pool
         * size is greater than <tt>corePoolSize</tt>
         */
        private long keepAliveTimeoutMillis = 30000;
        /**
         * the interval, which specifies how often the pool will perform idle {@link Connection}s check
         */
        private long keepAliveCheckIntervalMillis = 5000;
        
        /**
         * Sets the default {@link ConnectorHandler} to be used to establish new
         * {@link Connection}s if none is specified by {@link EndpointKey}.
         * 
         * @param defaultConnectorHandler {@link ConnectorHandler}
         * @return this {@link Builder}
         */
        public Builder<E> connectorHandler(
                final ConnectorHandler<E> defaultConnectorHandler) {
            this.defaultConnectorHandler = defaultConnectorHandler;
            return this;
        }
        
        /**
         * Sets the maximum number of {@link Connection}s to a single endpoint
         * the pool is allowed to have.
         * 
         * Default value is 2.
         * 
         * @param maxConnectionsPerEndpoint
         * @return this {@link Builder}
         */
        public Builder<E> maxConnectionsPerEndpoint(final int maxConnectionsPerEndpoint) {
            this.maxConnectionsPerEndpoint = maxConnectionsPerEndpoint;
            return this;
        }
        
        /**
         * Sets the maximum number of {@link Connection}s the pool is allowed to have.
         * Default value is 16.
         * 
         * @param maxConnectionsTotal
         * @return this {@link Builder}
         */        
        public Builder<E> maxConnectionsTotal(final int maxConnectionsTotal) {
            this.maxConnectionsTotal = maxConnectionsTotal;
            return this;
        }
        
        /**
         * Sets the custom {@link DelayedExecutor} to be used for keep-alive and
         * reconnect mechanisms.
         * If none is set - the {@link SingleEndpointPool} will create its own {@link DelayedExecutor}.
         * 
         * @param delayedExecutor
         * @return this {@link Builder}
         */ 
        public Builder<E> delayExecutor(final DelayedExecutor delayedExecutor) {
            this.delayedExecutor = delayedExecutor;
            return this;
        }
        
        /**
         * Sets the max time {@link Connection} connect operation may take.
         * If timeout expires - the connect operation is considered failed.
         * If connectTimeout &lt; 0 - the connect timeout mechanism will be disabled.
         * By default the connect timeout mechanism is disabled.
         * 
         * @param connectTimeout the max time {@link Connection} connect
         *        operation may take. If timeout expires - the connect operation
         *        is considered failed. The negative value disables the
         *        connect timeout mechanism.
         * @param timeunit a <tt>TimeUnit</tt> determining how to interpret the
         *        <tt>timeout</tt> parameter
         * @return this {@link Builder}
         */ 
        public Builder<E> connectTimeout(final long connectTimeout,
                final TimeUnit timeunit) {

            this.connectTimeoutMillis = connectTimeout > 0 ?
                    TimeUnit.MILLISECONDS.convert(connectTimeout, timeunit) :
                    connectTimeout;
            return this;
        }
        
        /**
         * Sets the delay to be used before the pool will repeat the attempt to
         * connect to the endpoint after previous connect operation had failed.
         * If reconnectDelay &lt; 0 - the reconnect mechanism will be disabled.
         * By default the reconnect mechanism is disabled.
         * 
         * @param reconnectDelay the delay to be used before the pool will repeat
         *        the attempt to connect to the endpoint after previous connect
         *        operation had failed. The negative value disables the
         *        reconnect mechanism.
         * @param timeunit a <tt>TimeUnit</tt> determining how to interpret the
         *        <tt>timeout</tt> parameter
         * @return this {@link Builder}
         */ 
        public Builder<E> reconnectDelay(final long reconnectDelay,
                final TimeUnit timeunit) {

            this.reconnectDelayMillis = reconnectDelay > 0 ?
                    TimeUnit.MILLISECONDS.convert(reconnectDelay, timeunit) :
                    reconnectDelay;
            return this;
        }

        /**
         * If the reconnect mechanism is enabled, then this property will affect
         * how many times a reconnection attempt can be made consecutively before
         * a failure is flagged.
         *
         * @param maxReconnectAttempts the maximum number of reconnect attempts.
         *                             If the reconnect mechanism isn't enabled, this property is ignored.
         * @return this {@link Builder}
         */
        public Builder<E> maxReconnectAttempts(final int maxReconnectAttempts) {
            this.maxReconnectAttempts = maxReconnectAttempts;
            return this;
        }

        /**
         * Sets the maximum number of milliseconds an idle {@link Connection}
         * will be kept in the pool.
         * The idle {@link Connection}s will be closed till the pool size is
         * greater than <tt>corePoolSize</tt>.
         * 
         * If keepAliveTimeout &lt; 0 - the keep-alive mechanism will be disabled.
         * By default the keep-alive timeout is set to 30 seconds.
         * 
         * @param keepAliveTimeout the maximum number of milliseconds an idle
         *        {@link Connection} will be kept in the pool. The negative
         *        value disables the keep-alive mechanism.
         * @param timeunit a <tt>TimeUnit</tt> determining how to interpret the
         *        <tt>timeout</tt> parameter
         * @return this {@link Builder}
         */
        public Builder<E> keepAliveTimeout(final long keepAliveTimeout,
                final TimeUnit timeunit) {

            this.keepAliveTimeoutMillis = keepAliveTimeout > 0 ?
                    TimeUnit.MILLISECONDS.convert(keepAliveTimeout, timeunit) :
                    keepAliveTimeout;
            return this;
        }

        /**
         * Sets the interval, which specifies how often the pool will perform
         * idle {@link Connection}s check.
         * 
         * @param keepAliveCheckInterval the interval, which specifies how often the
         *        pool will perform idle {@link Connection}s check
         * @param timeunit a <tt>TimeUnit</tt> determining how to interpret the
         *        <tt>timeout</tt> parameter
         * @return this {@link Builder}
         */
        public Builder<E> keepAliveCheckInterval(final long keepAliveCheckInterval,
                final TimeUnit timeunit) {

            this.keepAliveCheckIntervalMillis = keepAliveCheckInterval > 0 ?
                    TimeUnit.MILLISECONDS.convert(keepAliveCheckInterval, timeunit) :
                    keepAliveCheckInterval;
            return this;
        }
        
        /**
         * Constructs {@link MultiEndpointPool}.
         * @return {@link MultiEndpointPool}
         */
        
        public MultiEndpointPool<E> build() {
            if (defaultConnectorHandler == null) {
                throw new IllegalStateException("The default ConnectorHandler is not set");
            }
            
            if (keepAliveTimeoutMillis >= 0 && keepAliveCheckIntervalMillis < 0) {
                throw new IllegalStateException("Keep-alive timeout is set, but keepAliveCheckInterval is invalid");
            }

            if (maxReconnectAttempts < 0) {
                throw new IllegalStateException("Max reconnect attempts must not be a negative value");
            }
            
            return new MultiEndpointPool<E>(defaultConnectorHandler,
                    maxConnectionsPerEndpoint, maxConnectionsTotal, delayedExecutor,
                    connectTimeoutMillis, keepAliveTimeoutMillis,
                    keepAliveCheckIntervalMillis, reconnectDelayMillis, maxReconnectAttempts);
        }
    }    
}
