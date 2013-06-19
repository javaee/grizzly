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
 *
 * @author oleksiys
 */
public class MultiEndpointPool<E> {
    private final ConcurrentHashMap<EndpointKey<E>, SingleEndpointPool<E>> poolByEndpointMap =
            new ConcurrentHashMap<EndpointKey<E>, SingleEndpointPool<E>>();

    private final Object poolSync = new Object();
    private final Object countersSync = new Object();
    
    private boolean isClosed;
    private int poolSize;
    private int totalPendingConnections;
    
    private final Map<Connection, ConnectionInfo<E>> connectionToSubPoolMap =
            new ConcurrentHashMap<Connection, ConnectionInfo<E>>();
            
    private final Chain<EndpointPoolImpl> maxPoolSizeHitsChain =
            new Chain<EndpointPoolImpl>();
    
    private final DelayedExecutor ownDelayedExecutor;
    private final DelayQueue<ReconnectTask> reconnectQueue;
    private final DelayQueue<KeepAliveCleanerTask> keepAliveCleanerQueue;

    
    private final ConnectorHandler<E> connectorHandler;
    private final int maxConnectionsPerEndpoint;
    private final int maxConnectionsTotal;
    
    private final long reconnectDelayMillis;
    private final long keepAliveTimeoutMillis;
    private final long keepAliveCheckIntervalMillis;
    
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
        
        reconnectQueue = delayedExecutor.createDelayQueue(new Reconnector(),
                new ReconnectTaskResolver());
        
        if (keepAliveTimeoutMillis >= 0) {
            keepAliveCleanerQueue = delayedExecutor.createDelayQueue(
                    new KeepAliveCleaner(), new KeepAliveCleanerTaskResolver());
        } else {
            keepAliveCleanerQueue = null;
        }        
    }
    
    public int size() {
        synchronized (countersSync) {
            return poolSize + totalPendingConnections;
        }
    }
    
    public int getOpenConnectionsCount() {
        synchronized (poolSync) {
            return poolSize;
        }
    }
    
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
     * @param endpointKey the {@link EndpointKey} to identify the endpoint pool to check
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
     * @param endpointKey the {@link EndpointKey} to identify the endpoint pool to check
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
    
    public Connection take(final EndpointKey<E> endpointKey)
            throws IOException, InterruptedException {
        final SingleEndpointPool<E> sePool = obtainSingleEndpointPool(endpointKey);
        
        return sePool.take();
    }
    
    public Connection poll(final EndpointKey<E> endpointKey,
            final long timeout, final TimeUnit timeunit)
            throws IOException, InterruptedException {
        
        final SingleEndpointPool<E> sePool = obtainSingleEndpointPool(endpointKey);
        return sePool.poll(timeout, timeunit);
    }
    
    public Connection poll(final EndpointKey<E> endpointKey)
            throws IOException {

        final SingleEndpointPool<E> sePool = poolByEndpointMap.get(endpointKey);
        if (sePool != null) {
            return sePool.poll();
        }
        
        return null;
    }

    public void release(final Connection connection) {
        final ConnectionInfo<E> info = connectionToSubPoolMap.get(connection);
        if (info != null) {
            // optimize release() call to avoid redundant map lookup
            info.endpointPool.release0(info);
        } else {
            connection.closeSilently();
        }
    }

    public boolean attach(final EndpointKey<E> endpointKey,
            final Connection connection)
            throws IOException {
        
        final SingleEndpointPool<E> sePool = poolByEndpointMap.get(endpointKey);
        if (sePool != null) {
            return sePool.attach(connection);
        }
        
        return false;
    }
    
    public void detach(final Connection connection)
            throws IOException {
        
        final ConnectionInfo<E> info = connectionToSubPoolMap.get(connection);
        if (info != null) {
            info.endpointPool.detach(connection);
        }
    }
    
    public void close(final EndpointKey<E> endpointKey) {
        final SingleEndpointPool<E> sePool = poolByEndpointMap.remove(endpointKey);
        if (sePool != null) {
            sePool.close();
        }
    }
    
    public void close() {
        synchronized (poolSync) {
            if (isClosed) {
                return;
            }
            
            isClosed = true;

            for (Map.Entry<EndpointKey<E>, SingleEndpointPool<E>> entry :
                    poolByEndpointMap.entrySet()) {
                try {
                    entry.getValue().close();
                } catch (Exception ignore) {
                }
            }

            poolByEndpointMap.clear();

            if (ownDelayedExecutor != null) {
                ownDelayedExecutor.destroy();
            }
        }
    }
    
    private SingleEndpointPool<E> obtainSingleEndpointPool(
            final EndpointKey<E> endpointKey) throws IOException {
        SingleEndpointPool<E> sePool = poolByEndpointMap.get(endpointKey);
        if (sePool == null) {
            synchronized (poolSync) {
                checkNotClosed();
                
                sePool = poolByEndpointMap.get(endpointKey);
                if (sePool == null) {
                    sePool = createSingleEndpointPool(endpointKey.getEndpoint());
                    poolByEndpointMap.put(endpointKey, sePool);
                }
            }
        }
        
        return sePool;
    }
    
    protected SingleEndpointPool<E> createSingleEndpointPool(final E endpoint) {
        return new EndpointPoolImpl(endpoint);
    }
    
    private void checkNotClosed() throws IOException {
        if (isClosed) {
            throw new IOException("The pool is closed");
        }
    }
    
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
