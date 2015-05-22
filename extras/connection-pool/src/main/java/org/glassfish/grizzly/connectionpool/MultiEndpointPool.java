/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2013-2015 Oracle and/or its affiliates. All rights reserved.
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
import java.util.concurrent.ExecutorService;
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
import org.glassfish.grizzly.utils.DataStructures;
import org.glassfish.grizzly.utils.DelayedExecutor;
import org.glassfish.grizzly.utils.DelayedExecutor.DelayQueue;
import org.glassfish.grizzly.utils.Futures;
import static org.glassfish.grizzly.connectionpool.SingleEndpointPool.*;

/**
 * The multi endpoint {@link Connection} pool implementation where each endpoint
 * sub-pool is represented by {@link SingleEndpointPool} and referenced by an {@link Endpoint}.
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
 *      - <tt>asyncPollTimeoutMillis</tt>: maximum amount of time, after which
 *                                         the async connection poll operation will
 *                                         be failed with a timeout exception
 *      - <tt>connectionTTLMillis</tt>: the maximum amount of time, a
 *                                      {@link Connection} could be associated with the pool
 * 
 * @param <E> the address type, for example for TCP transport it's {@link SocketAddress}
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
     * @param <T> endpoint type
     * 
     * @return {@link Builder} 
     */
    public static <T> Builder<T> builder(Class<T> endpointType) {
        return new Builder<T>();
    }
    
    /**
     * Maps endpoint -to- SingleEndpointPool
     */
    protected final Map<Endpoint<E>, SingleEndpointPool<E>> endpointToPoolMap =
            DataStructures.<Endpoint<E>, SingleEndpointPool<E>>getConcurrentMap();
    /**
     * Maps Connection -to- ConnectionInfo
     */
    private final Map<Connection, ConnectionInfo<E>> connectionToSubPoolMap =
            DataStructures.<Connection, ConnectionInfo<E>>getConcurrentMap();

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
     * The {@link DelayedExecutor} either own or provided, used for
     * keep-alive, re-connect or other timeout related mechanisms.
     */
    private final DelayedExecutor delayedExecutor;
    /**
     * The thread-pool used by theownDelayedExecutor
     */
    private final ExecutorService ownDelayedExecutorThreadPool;
    /**
     * Own/internal {@link DelayedExecutor} to be used for keep-alive and reconnect
     * mechanisms, if one (DelayedExecutor} was not specified by user
     */
    private final DelayedExecutor ownDelayedExecutor;
    /**
     * DelayQueue for connect timeout mechanism
     */
    private DelayQueue<ConnectTimeoutTask> connectTimeoutQueue;
    /**
     * DelayQueue for reconnect mechanism
     */
    private DelayQueue<ReconnectTask> reconnectQueue;
    /**
     * DelayQueue for keep-alive mechanism
     */
    private DelayQueue<KeepAliveCleanerTask> keepAliveCleanerQueue;
    /**
     * DelayQueue for async poll timeout mechanism
     */
    private DelayQueue<Link<AsyncPoll>> asyncPollTimeoutQueue;
    /**
     * DelayQueue for connection time to live mechanism
     */
    private DelayQueue<ConnectionInfo> connectionTTLQueue;
    
    /**
     * The default {@link ConnectorHandler} used to establish new
     * {@link Connection}s is none is specified by {@link EndpointKey}
     */
    private final ConnectorHandler<E> defaultConnectorHandler;
    
    /**
     * The customizer, which will be used to modify a specific endpoint pool
     * settings and overwrite the default settings assigned by this <tt>MultiEndpointPool</tt>.
     */
    private final EndpointPoolCustomizer<E> endpointPoolCustomizer;
    
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
     * Async poll timeout, after which, the async connection poll operation will
     * be failed with a timeout exception
     */
    private final long asyncPollTimeoutMillis;
    /**
     * the maximum amount of time, a {@link Connection} could stay registered with the pool
     * Once timeout is hit - the connection will be either closed, if it's idle,
     * or detached from the pool, if it's being used.
     */
    private final long connectionTTLMillis;

    /**
     * Maximum number of reconnect attempts.
     */
    private final int maxReconnectAttempts;
    /**
     * if true, the "take" method will fail fast if there is no free connection
     * in the pool and max pool size is reached.
     */
    private final boolean failFastWhenMaxSizeReached;
    
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
     * @param asyncPollTimeoutMillis the maximum time, the async poll operation could wait for a connection to become available
     * @param connectionTTLMillis the maximum time, a connection could stay registered with the pool
     * @param failFastWhenMaxSizeReached <tt>true</tt> if the "take" method should fail fast if there is no free connection in the pool and max pool size is reached
     * @param endpointPoolCustomizer the customizer, which will be used to modify a specific endpoint pool settings and overwrite the default settings assigned by this <tt>MultiEndpointPool</tt>
     * 
     * @deprecated defaultConnectorHandler is deprecated
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
            final int maxReconnectAttempts,
            final long asyncPollTimeoutMillis,
            final long connectionTTLMillis,
            final boolean failFastWhenMaxSizeReached,
            final EndpointPoolCustomizer<E> endpointPoolCustomizer) {
        this.defaultConnectorHandler = defaultConnectorHandler;
        this.maxConnectionsPerEndpoint = maxConnectionsPerEndpoint;
        this.maxConnectionsTotal = maxConnectionsTotal;
        
        this.connectTimeoutMillis = connectTimeoutMillis;
        this.reconnectDelayMillis = reconnectDelayMillis;
        this.keepAliveTimeoutMillis = keepAliveTimeoutMillis;
        this.keepAliveCheckIntervalMillis = keepAliveCheckIntervalMillis;
        this.maxReconnectAttempts = maxReconnectAttempts;
        this.asyncPollTimeoutMillis = asyncPollTimeoutMillis;
        this.connectionTTLMillis = connectionTTLMillis;
        this.failFastWhenMaxSizeReached = failFastWhenMaxSizeReached;
        this.endpointPoolCustomizer = endpointPoolCustomizer;
        
        if (delayedExecutor == null) {
            final ThreadPoolConfig tpc = ThreadPoolConfig.defaultConfig()
                    .setPoolName("connection-pool-delays-thread-pool")
                    .setCorePoolSize(1)
                    .setMaxPoolSize(1);

            ownDelayedExecutorThreadPool =
                    GrizzlyExecutorService.createInstance(tpc);
            ownDelayedExecutor = new DelayedExecutor(
                    ownDelayedExecutorThreadPool);
            ownDelayedExecutor.start();
            this.delayedExecutor = ownDelayedExecutor;
        } else {
            ownDelayedExecutorThreadPool = null;
            ownDelayedExecutor = null;
            this.delayedExecutor = delayedExecutor;
        }
        
        checkConnectTimeoutQueue(connectTimeoutMillis);
        
        checkReconnectQueue(reconnectDelayMillis);
        
        checkKeepAliveCleanerQueue(keepAliveTimeoutMillis);
        
        checkAsyncPollTimeoutQueue(asyncPollTimeoutMillis);
        
        checkConnectionTTLQueue(connectionTTLMillis);
    }

    /**
     * Constructs MultiEndpointPool instance.
     * 
     * @param maxConnectionsPerEndpoint the maximum number of {@link Connection}s single endpoint sub-pool is allowed to have
     * @param maxConnectionsTotal the total maximum number of {@link Connection}s the pool is allowed to have
     * @param delayedExecutor custom {@link DelayedExecutor} to be used by keep-alive and reconnect mechanisms
     * @param connectTimeoutMillis timeout, after which, if a connection is not established, it is considered failed
     * @param keepAliveTimeoutMillis the maximum number of milliseconds an idle {@link Connection} will be kept in the pool
     * @param keepAliveCheckIntervalMillis the interval, which specifies how often the pool will perform idle {@link Connection}s check
     * @param reconnectDelayMillis the delay to be used before the pool will repeat the attempt to connect to the endpoint after previous connect had failed
     * @param maxReconnectAttempts the maximum number of reconnect attempts that may be made before failure notification.
     * @param asyncPollTimeoutMillis the maximum time, the async poll operation could wait for a connection to become available
     * @param connectionTTLMillis the maximum time, a connection could stay registered with the pool
     * @param failFastWhenMaxSizeReached <tt>true</tt> if the "take" method should fail fast if there is no free connection in the pool and max pool size is reached
     * @param endpointPoolCustomizer the customizer, which will be used to modify a specific endpoint pool settings and overwrite the default settings assigned by this <tt>MultiEndpointPool</tt>
     */
    protected MultiEndpointPool(
            final int maxConnectionsPerEndpoint,
            final int maxConnectionsTotal,
            final DelayedExecutor delayedExecutor,
            final long connectTimeoutMillis,
            final long keepAliveTimeoutMillis,
            final long keepAliveCheckIntervalMillis,
            final long reconnectDelayMillis,
            final int maxReconnectAttempts,
            final long asyncPollTimeoutMillis,
            final long connectionTTLMillis,
            final boolean failFastWhenMaxSizeReached,
            final EndpointPoolCustomizer<E> endpointPoolCustomizer) {    
        this(null, maxConnectionsPerEndpoint, maxConnectionsTotal,
                delayedExecutor, connectTimeoutMillis, keepAliveTimeoutMillis,
                keepAliveCheckIntervalMillis, reconnectDelayMillis,
                maxReconnectAttempts, asyncPollTimeoutMillis,
                connectionTTLMillis, failFastWhenMaxSizeReached,
                endpointPoolCustomizer);
    }
    
    /**
     * @return the total maximum number of {@link Connection}s to be kept by the pool
     */
    public int getMaxConnectionsTotal() {
        return maxConnectionsTotal;
    }

    /**
     * @return the maximum number of {@link Connection}s each
     *         {@link SingleEndpointPool} sub-pool is allowed to have
     */
    public int getMaxConnectionsPerEndpoint() {
        return maxConnectionsPerEndpoint;
    }

    /**
     * @param timeUnit {@link TimeUnit}
     * @return the connection timeout, after which, if a connection is not
     *         established, it is considered failed
     */
    public long getConnectTimeout(final TimeUnit timeUnit) {
        return connectTimeoutMillis <= 0 ?
                connectTimeoutMillis :
                timeUnit.convert(connectTimeoutMillis, TimeUnit.MILLISECONDS);
    }

    /**
     * @param timeUnit {@link TimeUnit}
     * @return the delay to be used before the pool will repeat the attempt
     *         to connect to the endpoint after previous attempt fail
     */
    public long getReconnectDelay(final TimeUnit timeUnit) {
        return reconnectDelayMillis <= 0 ?
                reconnectDelayMillis :
                timeUnit.convert(reconnectDelayMillis, TimeUnit.MILLISECONDS);
    }

    /**
     * @return the maximum number of reconnect attempts
     */
    public int getMaxReconnectAttempts() {
        return maxReconnectAttempts;
    }

    /**
     * Returns the maximum amount of time an idle {@link Connection} will be kept
     * in the pool.
     * 
     * @param timeUnit {@link TimeUnit}
     * @return the maximum amount of time an idle {@link Connection} will be kept
     * in the pool
     */
    public long getKeepAliveTimeout(final TimeUnit timeUnit) {
        return keepAliveTimeoutMillis <= 0 ?
                keepAliveTimeoutMillis :
                timeUnit.convert(keepAliveTimeoutMillis, TimeUnit.MILLISECONDS);
    }

    /**
     * @param timeUnit {@link TimeUnit}
     * @return the interval, which specifies how often the pool will perform idle {@link Connection}s check
     */
    public long getKeepAliveCheckInterval(final TimeUnit timeUnit) {
        return keepAliveCheckIntervalMillis <= 0 ?
                keepAliveCheckIntervalMillis :
                timeUnit.convert(keepAliveCheckIntervalMillis, TimeUnit.MILLISECONDS);
    }

    /**
     * @param timeUnit {@link TimeUnit}
     * @return the timeout, after which, the async connection poll operation will
     *         fail with a timeout exception
     */
    public long getAsyncPollTimeout(final TimeUnit timeUnit) {
        return asyncPollTimeoutMillis <= 0 ?
                asyncPollTimeoutMillis :
                timeUnit.convert(asyncPollTimeoutMillis, TimeUnit.MILLISECONDS);
    }

    /**
     * Return the maximum amount of time, a {@link Connection} could be associated with the pool.
     * Once timeout is hit - the connection will be either closed, if it's idle,
     * or detached from the pool, if it's being used.
     * 
     * @param timeUnit {@link TimeUnit}
     * @return the maximum amount of time, a {@link Connection} could be associated with the pool
     */
    public long getConnectionTTL(final TimeUnit timeUnit) {
        return connectionTTLMillis <= 0 ?
                connectionTTLMillis :
                timeUnit.convert(connectionTTLMillis, TimeUnit.MILLISECONDS);
    }

    /**
     * @return <tt>true</tt>, if the "take" method will fail fast if
     *         there is no free connection in the pool and max pool size is reached
     */
    public boolean isFailFastWhenMaxSizeReached() {
        return failFastWhenMaxSizeReached;
    }
    
    /**
     * @return  the current pool size.
     * This value includes connected and connecting (connect in progress)
     * {@link Connection}s
     */
    public int size() {
        synchronized (countersSync) {
            return poolSize + totalPendingConnections;
        }
    }
    
    /**
     * @return the number of connected {@link Connection}s in the pool.
     * Unlike {@link #size()} the value doesn't include connecting
     * (connect in progress) {@link Connection}s
     */
    public int getOpenConnectionsCount() {
        synchronized (countersSync) {
            return poolSize;
        }
    }
    
    /**
     * @return <tt>true</tt> is maximum number of {@link Connection}s the pool
     * can keep is reached and no new {@link Connection} can be established, or
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
     * <tt>connection = pool.take(endpoint).get();</tt>
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
     * @param endpoint {@link Endpoint}, that represents an endpoint
     * @return {@link GrizzlyFuture}
     */
    public GrizzlyFuture<Connection> take(final Endpoint<E> endpoint) {
        try {
            final SingleEndpointPool<E> sePool =
                    obtainSingleEndpointPool(endpoint);

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
     * @param endpoint {@link Endpoint}, that represents an endpoint
     * @param completionHandler
     */
    public void take(final Endpoint<E> endpoint,
            final CompletionHandler<Connection> completionHandler) {
        if (completionHandler == null) {
            throw new IllegalArgumentException("The completionHandler argument can not be null");
        }
        
        try {
            final SingleEndpointPool<E> sePool = obtainSingleEndpointPool(endpoint);

            sePool.take(completionHandler);
        } catch (IOException e) {
            completionHandler.failed(e);
        }
    }
    
    /**
     * @param endpoint {@link Endpoint}, that represents an endpoint
     * 
     * @return a {@link Connection} from the pool, if there is one available at the moment,
     *          or <tt>null</tt> otherwise
     * @throws java.io.IOException if the pool is closed
     */
    public Connection poll(final Endpoint<E> endpoint) throws IOException {
        final SingleEndpointPool<E> sePool = endpointToPoolMap.get(endpoint);

        return sePool != null ? sePool.poll() : null;
    }

    /**
     * Returns the {@link Connection} to the pool.
     * 
     * The {@link Connection} will be returned to the pool only in case it
     * was created by this pool, or it was attached to it using {@link #attach(Endpoint, org.glassfish.grizzly.Connection)}
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
     * @param endpoint {@link Endpoint}, that represents an endpoint to
     *              which the the {@link Connection} will be attached
     * @param connection {@link Connection}
     * @return <tt>true</tt> if the {@link Connection} has been successfully attached,
     *              or <tt>false</tt> otherwise. If the {@link Connection} had
     *              been already registered in the pool - the method call doesn't
     *              have any effect and <tt>true</tt> will be returned.
     * @throws IOException thrown if this pool has been already closed
     */
    public boolean attach(final Endpoint<E> endpoint,
            final Connection connection)
            throws IOException {

        final SingleEndpointPool<E> sePool = obtainSingleEndpointPool(endpoint);
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.log(Level.FINE,
                       "Associating foreign connection with pool {0} using endpoint key {1}.",
                       new Object[] {sePool, endpoint});
        }
        return sePool.attach(connection);
    }
    
    /**
     * Detaches a {@link Connection} from the pool.
     * De-registers the {@link Connection} from the pool and decreases the pool
     * size by 1. It is possible to re-attach the detached {@link Connection}
     * later by calling {@link #attach(org.glassfish.grizzly.connectionpool.Endpoint, org.glassfish.grizzly.Connection)}.
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
     * @param endpoint {@link Endpoint}, that represents an endpoint
     */
    public void close(final Endpoint<E> endpoint) {
        final SingleEndpointPool<E> sePool = endpointToPoolMap.remove(endpoint);
        if (sePool != null) {
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.log(Level.FINE,
                           "Closing pool associated with endpoint key {0}",
                           endpoint);
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

            for (Map.Entry<Endpoint<E>, SingleEndpointPool<E>> entry :
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
            
            if (ownDelayedExecutorThreadPool != null) {
                ownDelayedExecutorThreadPool.shutdownNow();
            }            
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "@" + Integer.toHexString(hashCode()) +
                "{"
                + "endpoint count=" + endpointToPoolMap.size()
                + "poolSize=" + poolSize
                + ", isClosed=" + isClosed
                + "}";
    }

    /**
     * Obtains {@link SingleEndpointPool} associated with the specific endpoint
     * represented by {@link Endpoint}. If there is no {@link SingleEndpointPool}
     * associated with the endpoint - the one will be created.
     * 
     * @param endpoint {@link Endpoint}, that represents an endpoint
     * @return {@link SingleEndpointPool}
     * @throws IOException if the pool is already closed
     */
    protected SingleEndpointPool<E> obtainSingleEndpointPool(
            final Endpoint<E> endpoint) throws IOException {
        SingleEndpointPool<E> sePool = endpointToPoolMap.get(endpoint);
        if (sePool == null) {
            synchronized (poolSync) {
                checkNotClosed();
                
                sePool = endpointToPoolMap.get(endpoint);
                if (sePool == null) {
                    if (LOGGER.isLoggable(Level.FINE)) {
                        LOGGER.log(Level.FINE,
                                   "Creating new endpoint pool for key {0}",
                                   endpoint);
                    }
                    sePool = createSingleEndpointPool(endpoint);
                    endpointToPoolMap.put(endpoint, sePool);
                } else if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.log(Level.FINE,
                               "Returning existing pool {0} for key {1}",
                               new Object[]{sePool, endpoint});
                }
            }
        }
        
        return sePool;
    }
    
    /**
     * Creates {@link SingleEndpointPool} instance.
     * @param endpoint the endpoint key
     * @return {@link SingleEndpointPool}
     */
    protected SingleEndpointPool<E> createSingleEndpointPool(
            Endpoint<E> endpoint) {
        
        if (endpointPoolCustomizer == null) {
            return new EndpointPoolImpl(endpoint);
        } else {
            final EndpointPoolBuilder<E> builder =
                    new EndpointPoolBuilder<E>(this, endpoint);
            endpointPoolCustomizer.customize(endpoint, builder);
            return builder.build();
        }
    }

    /**
     * Method throws {@link IOException} if the pool has been closed.
     * @throws java.io.IOException if the pool has been closed
     */
    protected void checkNotClosed() throws IOException {
        if (isClosed) {
            throw new IOException("The pool is closed");
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
    
    /**
     * For backwards compatibility reasons (defaultConnectorHandler is
     * deprecated in the builder), the method checks if the {@link Endpoint}
     * is an {@link EndpointKey}, which doesn't provide a {@link ConnectorHandler},
     * in this case it sets the {@link #defaultConnectorHandler}.
     * 
     * @param endpoint
     * @return
     */
    private Endpoint<E> checkWithDefaultConnectorHandler(Endpoint<E> endpoint) {
        // we need it for backwards compatibility of defaultConnectorHandler
        if (defaultConnectorHandler != null
                && endpoint instanceof EndpointKey) {
            final EndpointKey<E> epk = (EndpointKey<E>) endpoint;
            if (epk.getConnectorHandler() == null) {
                endpoint = new Endpoint<E>() {
                    
                    @Override
                    public Object getId() {
                        return epk.getId();
                    }
                    
                    @Override
                    public GrizzlyFuture<Connection> connect() {
                        return (GrizzlyFuture<Connection>) defaultConnectorHandler
                                .connect(epk.getEndpoint(), epk.getLocalEndpoint());
                    }
                };
            }
        }
        return endpoint;
    }
    
    private void checkConnectTimeoutQueue(final long connectTimeoutMillis) {
        if (connectTimeoutMillis >= 0 && connectTimeoutQueue == null) {
            connectTimeoutQueue = delayedExecutor.createDelayQueue(
                    new ConnectTimeoutWorker(), new ConnectTimeoutTaskResolver());
        }
    }
    
    private void checkReconnectQueue(final long reconnectDelayMillis) {
        if (reconnectDelayMillis >= 0 && reconnectQueue == null) {
            reconnectQueue = delayedExecutor.createDelayQueue(
                    new Reconnector(), new ReconnectTaskResolver());
        }
    }

    private void checkKeepAliveCleanerQueue(final long keepAliveTimeoutMillis) {
        // if keepAliveTimeoutMillis == 0 - we close connection right away
        // and don't rely on keep-alive cleaner
        if (keepAliveTimeoutMillis > 0 && keepAliveCleanerQueue == null) {
            keepAliveCleanerQueue = delayedExecutor.createDelayQueue(
                    new KeepAliveCleaner(), new KeepAliveCleanerTaskResolver());
        }
    }

    private void checkAsyncPollTimeoutQueue(final long asyncPollTimeoutMillis) {
        if (asyncPollTimeoutMillis >= 0 && asyncPollTimeoutQueue == null) {
            asyncPollTimeoutQueue = delayedExecutor.createDelayQueue(
                    new AsyncPollTimeoutWorker(), new AsyncPollTimeoutTaskResolver());
        }
    }
    
    private void checkConnectionTTLQueue(final long connectionTTLMillis) {
        if (connectionTTLMillis >= 0 && connectionTTLQueue == null) {
            connectionTTLQueue = delayedExecutor.createDelayQueue(
                    new ConnectionTTLWorker(), new ConnectionTTLTaskResolver());
        }
    }
    
    /**
     * The customizer, which could be used to modify an endpoint pool setting
     * before it will be created.
     * 
     * @param <E> the address type, for example for TCP transport it's {@link SocketAddress}
     */
    public interface EndpointPoolCustomizer<E> {
        public void customize(Endpoint<E> endpoint, EndpointPoolBuilder<E> builder);
    }
    
    /**
     * {@link SingleEndpointPool} implementation used by this <tt>MultiEndpointPool</tt>.
     */
    private final class EndpointPoolImpl extends SingleEndpointPool<E> {
        private final Link<EndpointPoolImpl> maxPoolSizeHitsLink =
                new Link<EndpointPoolImpl>(this);
        
        private int maxPoolSizeHits;
        
        public EndpointPoolImpl(final Endpoint<E> endpoint) {
            super(checkWithDefaultConnectorHandler(endpoint),
                    0, maxConnectionsPerEndpoint,
                    connectTimeoutQueue, reconnectQueue, keepAliveCleanerQueue,
                    asyncPollTimeoutQueue, connectionTTLQueue,
                    connectTimeoutMillis, keepAliveTimeoutMillis,
                    keepAliveCheckIntervalMillis, reconnectDelayMillis,
                    maxReconnectAttempts, asyncPollTimeoutMillis,
                    connectionTTLMillis, failFastWhenMaxSizeReached);
        }

        public EndpointPoolImpl(final Endpoint<E> endpoint,
            final int corePoolSize, final int maxPoolSize,
            final DelayQueue<ConnectTimeoutTask> connectTimeoutQueue,
            final DelayQueue<ReconnectTask> reconnectQueue,
            final DelayQueue<KeepAliveCleanerTask> keepAliveCleanerQueue,
            final DelayQueue<Link<AsyncPoll>> asyncPollTimeoutQueue,
            final DelayQueue<ConnectionInfo> connectionTTLQueue,
            final long connectTimeoutMillis,
            final long keepAliveTimeoutMillis,
            final long keepAliveCheckIntervalMillis,
            final long reconnectDelayMillis,
            final int maxReconnectAttempts,
            final long asyncPollTimeoutMillis,
            final long connectionTTLMillis,
            final boolean failFastWhenMaxSizeReached) {
            
            super(checkWithDefaultConnectorHandler(endpoint),
                    corePoolSize, maxPoolSize,
                    connectTimeoutQueue, reconnectQueue, keepAliveCleanerQueue,
                    asyncPollTimeoutQueue, connectionTTLQueue,
                    connectTimeoutMillis, keepAliveTimeoutMillis,
                    keepAliveCheckIntervalMillis, reconnectDelayMillis,
                    maxReconnectAttempts, asyncPollTimeoutMillis,
                    connectionTTLMillis, failFastWhenMaxSizeReached);
        }

        @Override
        protected boolean checkBeforeOpeningConnection() {
            if (pendingConnections >= getWaitingListSize() ||
                    super.isMaxCapacityReached()) {
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
        void onConnected(final Connection connection) {
            super.onConnected(connection);
            
            synchronized (countersSync) {
                totalPendingConnections--;
            }
        }

        /**
         * @return <tt>true</tt> is maximum number of {@link Connection}s the pool
         * can keep is reached and no new {@link Connection} can be established, or
         * <tt>false</tt> otherwise.
         */
        @Override
        public boolean isMaxCapacityReached() {
            return MultiEndpointPool.this.isMaxCapacityReached()
                    || super.isMaxCapacityReached();
        }
        
        /**
         * @return <tt>true</tt> if number of live connections is more or equal
         * to max pool size
         */
        @Override
        boolean isOverflown() {
            return maxConnectionsTotal != -1 &&
                    MultiEndpointPool.this.poolSize >= maxConnectionsTotal;
        }
        
        @Override
        ConnectionInfo<E> attach0(final Connection connection) {
            final ConnectionInfo<E> info = super.attach0(connection);
            
            connectionToSubPoolMap.put(connection, info);
            
            synchronized (countersSync) {
                MultiEndpointPool.this.poolSize++;
            }
            
            return info;
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
                if (this.size() > 0) {
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
    }
    
    public static class EndpointPoolBuilder<E> extends SingleEndpointPool.Builder<E> {

        private final MultiEndpointPool<E> multiEndpointPool;
        /**
         * Set the default builder settings.
         * 
         * @param endpoint 
         */
        EndpointPoolBuilder(final MultiEndpointPool<E> multiEndpointPool,
                final Endpoint<E> endpoint) {
            
            super(endpoint, null, null, null,
                    0, multiEndpointPool.maxConnectionsPerEndpoint,
                    multiEndpointPool.delayedExecutor,
                    multiEndpointPool.connectTimeoutMillis,
                    multiEndpointPool.reconnectDelayMillis,
                    multiEndpointPool.maxReconnectAttempts,
                    multiEndpointPool.asyncPollTimeoutMillis,
                    multiEndpointPool.connectionTTLMillis,
                    multiEndpointPool.failFastWhenMaxSizeReached,
                    multiEndpointPool.keepAliveTimeoutMillis,
                    multiEndpointPool.keepAliveCheckIntervalMillis);
            
            this.multiEndpointPool = multiEndpointPool;
        }

        @Override
        public SingleEndpointPool<E> build0(final Endpoint<E> e) {
            multiEndpointPool.checkConnectTimeoutQueue(connectTimeoutMillis);
            multiEndpointPool.checkReconnectQueue(reconnectDelayMillis);
            multiEndpointPool.checkKeepAliveCleanerQueue(keepAliveTimeoutMillis);
            multiEndpointPool.checkAsyncPollTimeoutQueue(asyncPollTimeoutMillis);
            multiEndpointPool.checkConnectionTTLQueue(connectionTTLMillis);
            
            // creating non-static class EndpointPoolImpl :)
            return multiEndpointPool.new EndpointPoolImpl(e,
                    corePoolSize, maxPoolSize,
                    multiEndpointPool.connectTimeoutQueue,
                    multiEndpointPool.reconnectQueue,
                    multiEndpointPool.keepAliveCleanerQueue,
                    multiEndpointPool.asyncPollTimeoutQueue,
                    multiEndpointPool.connectionTTLQueue,
                    connectTimeoutMillis, keepAliveTimeoutMillis,
                    keepAliveCheckIntervalMillis,
                    reconnectDelayMillis, maxReconnectAttempts,
                    asyncPollTimeoutMillis, connectionTTLMillis,
                    failFastWhenMaxSizeReached);
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
         * Async poll timeout, after which, the async connection poll operation will
         * be failed with a timeout exception
         */
        private long asyncPollTimeoutMillis = -1;
        /**
         * the maximum amount of time, a {@link Connection} could stay registered with the pool
         * Once timeout is hit - the connection will be either closed, if it's idle,
         * or detached from the pool, if it's being used.
         */
        private long connectionTTLMillis = -1;
        /**
         * if true, the "take" method will fail fast if there is no free connection
         * in the pool and max pool size is reached.
         */
        private boolean failFastWhenMaxSizeReached;
        /**
         * the maximum number of milliseconds an idle {@link Connection} will be kept
         * in the pool.
         */
        private long keepAliveTimeoutMillis = 30000;
        /**
         * the interval, which specifies how often the pool will perform idle {@link Connection}s check
         */
        private long keepAliveCheckIntervalMillis = 5000;
        /**
         * The customizer, which will be used to modify a specific endpoint pool
         * settings and overwrite the default settings assigned by this <tt>MultiEndpointPool</tt>.
         */
        private EndpointPoolCustomizer<E> endpointPoolCustomizer;
        
        /**
         * Sets the default {@link ConnectorHandler} to be used to establish new
         * {@link Connection}s if none is specified by {@link Endpoint}.
         * 
         * @param defaultConnectorHandler {@link ConnectorHandler}
         * @return this {@link Builder}
         * @deprecated {@link Endpoint} must always know how to establish the connection
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
         * Sets the max time consumer will wait for a {@link Connection} to
         * become available. When timeout expires the consumer will
         * be notified about the failure ({@link TimeoutException})
         * via {@link CompletionHandler} or {@link Future}.
         * 
         * If asyncPollTimeout &lt; 0 - timeout will not be set.
         * By default the timeout is not set and consumer may wait forever for
         * a {@link Connection}.
         * 
         * @param asyncPollTimeout the maximum time, the async poll operation
         *        could wait for a connection to become available
         * @param timeunit a <tt>TimeUnit</tt> determining how to interpret the
         *        <tt>timeout</tt> parameter
         * @return this {@link Builder}
         */ 
        public Builder<E> asyncPollTimeout(final long asyncPollTimeout,
                final TimeUnit timeunit) {

            this.asyncPollTimeoutMillis = asyncPollTimeout > 0
                    ? TimeUnit.MILLISECONDS.convert(asyncPollTimeout, timeunit)
                    : asyncPollTimeout;
            return this;
        }
        
        /**
         * Sets the max amount of time a {@link Connection} could be associated
         * with the pool.
         * Once timeout expired the {@link Connection} will be either closed,
         * if it's idle, or detached from the pool, if it's being used.
         * 
         * If connectionTTL &lt; 0 - the {@link Connection} time to live will
         * not be set and the {@link Connection} can be associated with
         * a pool forever, if no other limit is hit (like keep-alive).
         * By default the connectionTTL is not set.
         * 
         * @param connectionTTL the max amount of time a {@link Connection} could be associated
         *        with the pool
         * @param timeunit a <tt>TimeUnit</tt> determining how to interpret the
         *        <tt>connectionTTL</tt> parameter
         * @return this {@link Builder}
         */ 
        public Builder<E> connectionTTL(final long connectionTTL,
                final TimeUnit timeunit) {

            this.connectionTTLMillis = connectionTTL > 0
                    ? TimeUnit.MILLISECONDS.convert(connectionTTL, timeunit)
                    : connectionTTL;
            return this;
        }        
        
        /**
         * if <tt>true</tt>, the "take" method will fail fast if there is no
         * free connection in the pool and max pool size is reached. Otherwise
         * the pool will queue up the take request and wait for a {@link Connection}
         * to become available
         * 
         * @param failFastWhenMaxSizeReached
         *
         * @return this {@link Builder}
         */
        public Builder<E> failFastWhenMaxSizeReached(
                final boolean failFastWhenMaxSizeReached) {
            this.failFastWhenMaxSizeReached = failFastWhenMaxSizeReached;
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
         * Set the customizer, which will be used to modify a specific endpoint pool
         * settings and overwrite the default settings assigned by
         * this <tt>MultiEndpointPool</tt>.
         * 
         * @param endpointPoolCustomizer {@link EndpointPoolCustomizer}
         * 
         * @return this {@link Builder}
         */
        public Builder<E> endpointPoolCustomizer(
                final EndpointPoolCustomizer<E> endpointPoolCustomizer) {
            this.endpointPoolCustomizer = endpointPoolCustomizer;
            return this;
        }
        
        /**
         * Constructs {@link MultiEndpointPool}.
         * @return {@link MultiEndpointPool}
         */
        
        public MultiEndpointPool<E> build() {
            if (keepAliveTimeoutMillis >= 0 && keepAliveCheckIntervalMillis < 0) {
                throw new IllegalStateException("Keep-alive timeout is set, but keepAliveCheckInterval is invalid");
            }

            if (maxReconnectAttempts < 0) {
                throw new IllegalStateException("Max reconnect attempts must not be a negative value");
            }
            
            return new MultiEndpointPool<E>(defaultConnectorHandler,
                    maxConnectionsPerEndpoint, maxConnectionsTotal, delayedExecutor,
                    connectTimeoutMillis, keepAliveTimeoutMillis,
                    keepAliveCheckIntervalMillis, reconnectDelayMillis,
                    maxReconnectAttempts, asyncPollTimeoutMillis,
                    connectionTTLMillis, failFastWhenMaxSizeReached,
                    endpointPoolCustomizer);
        }
    }    
}
