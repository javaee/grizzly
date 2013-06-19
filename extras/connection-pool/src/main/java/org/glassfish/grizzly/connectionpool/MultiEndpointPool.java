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
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.ConnectorHandler;
import org.glassfish.grizzly.threadpool.GrizzlyExecutorService;
import org.glassfish.grizzly.threadpool.ThreadPoolConfig;
import org.glassfish.grizzly.utils.DelayedExecutor;
import org.glassfish.grizzly.utils.DelayedExecutor.DelayQueue;
import static org.glassfish.grizzly.connectionpool.SingleEndpointPool.*;

/**
 * The multi endpoint {@link Connection} pool implementation, where each endpoint
 * sub-pool is represented by {@link SingleEndpointPool} and referenced by an {@link EndpointKey}.
 * 
 * There are number of configuration options supported by the <tt>MultiEndpointPool</tt>:
 *      - <tt>maxConnectionsPerEndpoint</tt>: the maximum number of {@link Connection}s each
 *                      {@link SingleEndpointPool} sub-pool is allowed to have;
 *      - <tt>maxConnectionsTotal</tt>: the total maximum number of {@link Connection}s to be kept by the pool;
 *      - <tt>keepAliveTimeoutMillis</tt>: the maximum number of milliseconds an idle {@link Connection}
 *                                         will be kept in the pool. The idle {@link Connection}s will be
 *                                         closed till the pool size is greater than <tt>corePoolSize</tt>;
 *      - <tt>keepAliveCheckIntervalMillis</tt>: the interval, which specifies how often the pool will
 *                                               perform idle {@link Connection}s check;
 *      - <tt>reconnectDelayMillis</tt>: the delay to be used before the pool will repeat the attempt to connect to
 *                                       the endpoint after previous connect had failed.
 * 
 * @author Alexey Stashok
 */
public class MultiEndpointPool<E> {
    /**
     * Maps endpoint -to- SingleEndpointPool
     */
    private final Map<EndpointKey<E>, SingleEndpointPool<E>> endpointToPoolMap =
            new ConcurrentHashMap<EndpointKey<E>, SingleEndpointPool<E>>();
    /**
     * Maps Connection -to- ConnectionInfo
     */
    private final Map<Connection, ConnectionInfo<E>> connectionToSubPoolMap =
            new ConcurrentHashMap<Connection, ConnectionInfo<E>>();

    /**
     * Sync for endpointToPoolMap updates
     */
    private final Object poolSync = new Object();
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
     * DelayQueue for reconnect mechanism
     */
    private final DelayQueue<ReconnectTask> reconnectQueue;
    /**
     * DelayQueue for keep-alive mechanism
     */
    private final DelayQueue<KeepAliveCleanerTask> keepAliveCleanerQueue;

    /**
     * {@link ConnectorHandler} used to establish new {@link Connection}s
     */
    private final ConnectorHandler<E> connectorHandler;
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
     * Constructs MultiEndpointPool instance.
     * 
     * @param connectorHandler {@link ConnectorHandler} to be used to establish new {@link Connection}s
     * @param maxConnectionsPerEndpoint the maximum number of {@link Connection}s single endpoint sub-pool is allowed to have
     * @param maxConnectionsTotal the total maximum number of {@link Connection}s the pool is allowed to have
     * @param delayedExecutor custom {@link DelayedExecutor} to be used by keep-alive and reconnect mechanisms
     * @param keepAliveTimeoutMillis the maximum number of milliseconds an idle {@link Connection} will be kept in the pool
     * @param keepAliveCheckIntervalMillis the interval, which specifies how often the pool will perform idle {@link Connection}s check
     * @param reconnectDelayMillis the delay to be used before the pool will repeat the attempt to connect to the endpoint after previous connect had failed
     */
    public MultiEndpointPool(
            final ConnectorHandler<E> connectorHandler,
            final int maxConnectionsPerEndpoint,
            final int maxConnectionsTotal,
            DelayedExecutor delayedExecutor,
            final long keepAliveTimeoutMillis,
            final long keepAliveCheckIntervalMillis,
            final long reconnectDelayMillis) {
        this.connectorHandler = connectorHandler;
        this.maxConnectionsPerEndpoint = maxConnectionsPerEndpoint;
        this.maxConnectionsTotal = maxConnectionsTotal;
        
        this.reconnectDelayMillis = reconnectDelayMillis;
        this.keepAliveTimeoutMillis = keepAliveTimeoutMillis;
        this.keepAliveCheckIntervalMillis = keepAliveCheckIntervalMillis;
        
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
            return poolSize + totalPendingConnections >= maxConnectionsTotal;
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
        if (info != null) {
            // optimize isBusy call to avoid redundant map lookup
            return info.endpointPool.isBusy0(info);
        }
        
        return false;
    }
    
    /**
     * Retrieves {@link Connection} to the specified endpoint from the pool,
     * waiting if necessary until a {@link Connection} becomes available.
     * 
     * @param endpointKey {@link EndpointKey}, that represents an endpoint
     * @return {@link Connection}
     * @throws IOException thrown if this pool has been already closed
     * @throws InterruptedException if interrupted while waiting
     */
    public Connection take(final EndpointKey<E> endpointKey)
            throws IOException, InterruptedException {
        final SingleEndpointPool<E> sePool = obtainSingleEndpointPool(endpointKey);
        
        return sePool.take();
    }
    
    /**
     * Retrieves {@link Connection} to the specified endpoint from the pool,
     * waiting up to the specified wait time if necessary for a {@link Connection}
     * to become available.
     * 
     * If the timeout less than zero (timeout &lt; 0) - the method call is equivalent to {@link #take(org.glassfish.grizzly.connectionpool.EndpointKey)}.
     * If the timeout is equal to zero (timeout == 0) - the method call is equivalent to {@link #poll(org.glassfish.grizzly.connectionpool.EndpointKey)}.
     * 
     * @param endpointKey {@link EndpointKey}, that represents an endpoint
     * @param timeout how long to wait before giving up, in units of
     *        <tt>unit</tt>
     * @param timeunit a <tt>TimeUnit</tt> determining how to interpret the
     *        <tt>timeout</tt> parameter
     * @return {@link Connection}, or <tt>null</tt> if the
     *         specified waiting time elapses before a {@link Connection} is available
     * @throws IOException thrown if this pool has been already closed
     * @throws InterruptedException if interrupted while waiting
     */
    public Connection poll(final EndpointKey<E> endpointKey,
            final long timeout, final TimeUnit timeunit)
            throws IOException, InterruptedException {
        
        final SingleEndpointPool<E> sePool = obtainSingleEndpointPool(endpointKey);
        return sePool.poll(timeout, timeunit);
    }
    
    /**
     * Retrieves {@link Connection} to the specified endpoint from the pool,
     * or returns <tt>null</tt> if this pool doesn't have any ready
     * {@link Connection} as the moment.
     * 
     * @param endpointKey {@link EndpointKey}, that represents an endpoint
     * @return {@link Connection}, or <tt>null</tt> if the
     *         this pool doesn't have any ready {@link Connection} as the moment
     * @throws IOException thrown if this pool has been already closed
     */
    public Connection poll(final EndpointKey<E> endpointKey)
            throws IOException {

        final SingleEndpointPool<E> sePool = endpointToPoolMap.get(endpointKey);
        if (sePool != null) {
            return sePool.poll();
        }
        
        return null;
    }

    /**
     * Returns the {@link Connection} to the pool.
     * 
     * The {@link Connection} will be returned to the pool only in case it
     * was created by this pool, or it was attached to it using {@link #attach(org.glassfish.grizzly.Connection)}
     * method.
     * If the {@link Connection} is not registered in the pool - it will be closed.
     * If the {@link Connection} is registered in the pool and already marked as ready - this method call will not have any effect.
     * 
     * If the {@link Connection} was returned - it is illegal to use it until
     * it is retrieved from the pool again.
     * 
     * @param connection the {@link Connection} to return
     * @throws IllegalStateException if the {@link Connection} had been returned to the pool before
     */
    public void release(final Connection connection) {
        final ConnectionInfo<E> info = connectionToSubPoolMap.get(connection);
        if (info != null) {
            // optimize release() call to avoid redundant map lookup
            info.endpointPool.release0(info);
        } else {
            connection.closeSilently();
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
     * @throws IllegalStateException the {@link IllegalStateException} is thrown
     *          if the {@link Connection} is in ready state
     */
    public void detach(final Connection connection)
            throws IOException {
        
        final ConnectionInfo<E> info = connectionToSubPoolMap.get(connection);
        if (info != null) {
            info.endpointPool.detach(connection);
        }
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
    
    /**
     * Obtains {@link SingleEndpointPool} associated with the specific endpoint
     * represented by {@link EndpointKey}. If there is no {@link SingleEndpointPool}
     * associated with the endpoint - the one will be created.
     * 
     * @param endpointKey {@link EndpointKey}, that represents an endpoint
     * @return {@link SingleEndpointPool}
     * @throws IOException if the pool is already closed
     */
    private SingleEndpointPool<E> obtainSingleEndpointPool(
            final EndpointKey<E> endpointKey) throws IOException {
        SingleEndpointPool<E> sePool = endpointToPoolMap.get(endpointKey);
        if (sePool == null) {
            synchronized (poolSync) {
                checkNotClosed();
                
                sePool = endpointToPoolMap.get(endpointKey);
                if (sePool == null) {
                    sePool = createSingleEndpointPool(endpointKey.getEndpoint());
                    endpointToPoolMap.put(endpointKey, sePool);
                }
            }
        }
        
        return sePool;
    }
    
    /**
     * Creates {@link SingleEndpointPool} instance.
     * @param endpointKey {@link EndpointKey}, that represents an endpoint
     * @return {@link SingleEndpointPool}
     */
    protected SingleEndpointPool<E> createSingleEndpointPool(final E endpoint) {
        return new EndpointPoolImpl(endpoint);
    }
    
    /**
     * Method throws {@link IOException} if the pool has been closed.
     */
    private void checkNotClosed() throws IOException {
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
        
        public EndpointPoolImpl(final E endpoint) {
            super(connectorHandler,
                endpoint, 0, maxConnectionsPerEndpoint,
                reconnectQueue, keepAliveCleanerQueue,
                keepAliveTimeoutMillis,
                keepAliveCheckIntervalMillis, reconnectDelayMillis);
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
                
                final Link<EndpointPoolImpl> firstLink =
                        maxPoolSizeHitsChain.pollFirst();
                
                if (firstLink != null) {
                    prioritizedPool = firstLink.getValue();
                    prioritizedPool.maxPoolSizeHits = 0;
                } else {
                    prioritizedPool = null;
                }
            }
            
            if (prioritizedPool != null) {
                prioritizedPool.createConnectionIfPossible();
                
                return;
            } 
            
            
            super.onCloseConnection(info);
        }
        
        private void onMaxPoolSizeHit() {
            if (maxPoolSizeHits++ == 0) {
                maxPoolSizeHitsChain.offer(maxPoolSizeHitsLink);
            } else {
                final Link<EndpointPoolImpl> prev = maxPoolSizeHitsLink.prev;
                if (prev != null &&
                        maxPoolSizeHits > prev.getValue().maxPoolSizeHits) {
                    maxPoolSizeHitsChain.moveTowardsHead(maxPoolSizeHitsLink);
                }
            }
        }        
    }
}
