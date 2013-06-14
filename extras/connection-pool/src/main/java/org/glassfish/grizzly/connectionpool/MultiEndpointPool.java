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
import org.glassfish.grizzly.CloseType;
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
    private final ConcurrentHashMap<E, SingleEndpointPool<E>> poolByEndpointMap =
            new ConcurrentHashMap<E, SingleEndpointPool<E>>();

    private final Object poolSync = new Object();
    private final Object maxConnectionCounterSync = new Object();
    
    private boolean isClosed;
    private int poolSize;
    private int pendingConnections;
    
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
    
    public Connection take(final E endpoint) throws IOException, InterruptedException {
        final SingleEndpointPool<E> sePool = obtainSingleEndpointPool(endpoint);
        
        return sePool.take();
    }
    
    public Connection take(final E endpoint, final long timeout,
            final TimeUnit timeunit) throws IOException, InterruptedException {
        final SingleEndpointPool<E> sePool = obtainSingleEndpointPool(endpoint);
        return sePool.take(timeout, timeunit);
    }
    
    public void release(final E endpoint) {
        final SingleEndpointPool<E> sePool = poolByEndpointMap.remove(endpoint);
        if (sePool != null) {
            sePool.close();
        }
    }

    @SuppressWarnings("unchecked")
    public void release(final Connection connection) {
        release((E) connection.getPeerAddress(), connection);
    }

    public void release(final E endpoint, final Connection connection) {
        final SingleEndpointPool<E> sePool =
                poolByEndpointMap.get(endpoint);
        if (sePool != null) {
            sePool.release(connection);
        }
    }

    public void close() {
        synchronized (poolSync) {
            if (isClosed) {
                return;
            }
            
            isClosed = true;

            for (Map.Entry<E, SingleEndpointPool<E>> entry : poolByEndpointMap.entrySet()) {
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
    
    private SingleEndpointPool<E> obtainSingleEndpointPool(final E endpoint)
            throws IOException {
        SingleEndpointPool<E> sePool = poolByEndpointMap.get(endpoint);
        if (sePool == null) {
            synchronized (poolSync) {
                if (isClosed) {
                    throw new IOException("The pool is closed");
                }
                sePool = poolByEndpointMap.get(endpoint);
                if (sePool == null) {
                    sePool = createSingleEndpointPool(endpoint);
                    poolByEndpointMap.put(endpoint, sePool);
                }
            }
        }
        
        return sePool;
    }
    
    protected SingleEndpointPool<E> createSingleEndpointPool(final E endpoint) {
        return new EndpointPoolImpl(endpoint);
    }
    
    private final class EndpointPoolImpl extends SingleEndpointPool<E> {

        public EndpointPoolImpl(final E endpoint) {
            super(connectorHandler,
                endpoint, 0, maxConnectionsPerEndpoint,
                reconnectQueue, keepAliveCleanerQueue,
                keepAliveTimeoutMillis,
                keepAliveCheckIntervalMillis, reconnectDelayMillis);
        }
        
        @Override
        protected boolean checkBeforeOpeningConnection() {
            synchronized (maxConnectionCounterSync) {
                if (poolSize + pendingConnections < maxConnectionsTotal) {
                    pendingConnections++;
                    return true;
                }
                
                return false;
            }
        }

        @Override
        protected void onOpenConnection(Connection connection) {
            synchronized (maxConnectionCounterSync) {
                pendingConnections--;
                poolSize++;
            }
        }

        @Override
        protected void onFailedConnection() {
            synchronized (maxConnectionCounterSync) {
                pendingConnections--;
            }
        }

        
        @Override
        protected void onCloseConnection(final Connection connection,
                final CloseType type) {
            synchronized (maxConnectionCounterSync) {
                poolSize--;
            }
        }
    }
}
