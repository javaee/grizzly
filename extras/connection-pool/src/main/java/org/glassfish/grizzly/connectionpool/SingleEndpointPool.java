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
import java.net.ConnectException;
import java.net.SocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.grizzly.CloseListener;
import org.glassfish.grizzly.CloseType;
import org.glassfish.grizzly.CompletionHandler;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.ConnectorHandler;
import org.glassfish.grizzly.EmptyCompletionHandler;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.GrizzlyFuture;
import org.glassfish.grizzly.impl.FutureImpl;
import org.glassfish.grizzly.impl.SafeFutureImpl;
import org.glassfish.grizzly.nio.transport.TCPNIOConnectorHandler;
import org.glassfish.grizzly.nio.transport.UDPNIOConnectorHandler;
import org.glassfish.grizzly.threadpool.GrizzlyExecutorService;
import org.glassfish.grizzly.threadpool.ThreadPoolConfig;
import org.glassfish.grizzly.utils.DelayedExecutor;
import org.glassfish.grizzly.utils.DelayedExecutor.DelayQueue;
import org.glassfish.grizzly.utils.Futures;

/**
 * The single endpoint {@link Connection} pool implementation, in other words
 * this pool manages {@link Connection}s to one specific endpoint.
 * 
 * The endpoint address has to be represented by an objected understandable by
 * a {@link ConnectorHandler} passed to the constructor. For example the
 * endpoint address has to be represented by {@link SocketAddress} for
 * {@link TCPNIOConnectorHandler} and {@link UDPNIOConnectorHandler}.
 * 
 * There are number of configuration options supported by the <tt>SingleEndpointPool</tt>:
 *      - <tt>corePoolSize</tt>: the number of {@link Connection}s to be kept in the pool and never timed out
 *                      because of keep-alive setting;
 *      - <tt>maxPoolSize</tt>: the maximum number of {@link Connection}s to be kept by the pool;
 *      - <tt>keepAliveTimeoutMillis</tt>: the maximum number of milliseconds an idle {@link Connection}
 *                                         will be kept in the pool. The idle {@link Connection}s will be
 *                                         closed till the pool size is greater than <tt>corePoolSize</tt>;
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
public class SingleEndpointPool<E> {
    private static final Logger LOGGER = Grizzly.logger(SingleEndpointPool.class);
    
    /**
     * Returns single endpoint pool {@link Builder}.
     * 
     * @param <T> endpoint type
     * @param endpointType endpoint address type, for example
     *        {@link SocketAddress} for TCP and UDP transports
     * @return {@link Builder} 
     */
    public static <T> Builder<T> builder(Class<T> endpointType) {
        return new Builder<T>();
    }
    
    /**
     * {@link CompletionHandler} to be notified once
     * {@link ConnectorHandler#connect(java.lang.Object)} is complete
     */
    private final ConnectCompletionHandler defaultConnectionCompletionHandler =
            new ConnectCompletionHandler();
    /**
     * {@link CloseListener} to be notified once pooled {@link Connection} is closed
     */
    private final PoolConnectionCloseListener closeListener =
            new PoolConnectionCloseListener();
    
    /**
     * The {@link Chain} of ready connections
     */
    private final Chain<ConnectionInfo<E>> readyConnections = new Chain<ConnectionInfo<E>>();
    
    /**
     * The {@link Map} contains *all* pooled {@link Connection}s
     */
    private final Map<Connection, ConnectionInfo<E>> connectionsMap =
            new HashMap<Connection, ConnectionInfo<E>>();
    
    /**
     * Sync object
     */
    final Object poolSync = new Object();
    
    /**
     * close flag
     */
    private boolean isClosed;
    
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
    private final DelayQueue<ConnectTimeoutTask> connectTimeoutQueue;
    /**
     * DelayQueue for reconnect mechanism
     */
    private final DelayQueue<ReconnectTask> reconnectQueue;
    /**
     * Maximum number of reconnect attempts.
     */
    private final int maxReconnectAttempts;

    /**
     * DelayQueue for keep-alive mechanism
     */
    private final DelayQueue<KeepAliveCleanerTask> keepAliveCleanerQueue;

    /**
     * DelayQueue for async poll timeout mechanism
     */
    private final DelayQueue<Link<AsyncPoll>> asyncPollTimeoutQueue;

    /**
     * DelayQueue for connection time to live mechanism
     */
    private final DelayQueue<ConnectionInfo> connectionTTLQueue;

    /**
     * The endpoint description
     */
    private final Endpoint<E> endpoint;
    /**
     * The number of {@link Connection}s, kept in the pool, that are immune to keep-alive mechanism
     */
    private final int corePoolSize;
    /**
     * The max number of {@link Connection}s kept by this pool
     */
    protected final int maxPoolSize;
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
     * fail with a timeout exception
     */
    private final long asyncPollTimeoutMillis;
    /**
     * the maximum amount of time, a {@link Connection} could be associated with the pool
     * Once timeout is hit - the connection will be either closed, if it's idle,
     * or detached from the pool, if it's being used.
     */
    private final long connectionTTLMillis;
    /**
     * if true, the "take" method will fail fast if there is no free connection
     * in the pool and max pool size is reached.
     */
    private final boolean failFastWhenMaxSizeReached;
    
    /**
     * current pool size
     */
    private int poolSize;
    /**
     * Number of connections we're currently trying to establish and waiting for the result
     */
    protected int pendingConnections;

    /**
     * Number of failed connect attempts.
     */
    private int failedConnectAttempts;
    
    /**
     * The waiting list of asynchronous polling clients
     */
    private final Chain<AsyncPoll> asyncWaitingList = new Chain<AsyncPoll>();

    /**
     * Constructs SingleEndpointPool instance.
     * 
     * @param endpoint {@link Endpoint} to be used to establish new {@link Connection}s
     * @param corePoolSize the number of {@link Connection}s, kept in the pool, that are immune to keep-alive mechanism
     * @param maxPoolSize the max number of {@link Connection}s kept by this pool
     * @param delayedExecutor custom {@link DelayedExecutor} to be used by keep-alive and reconnect mechanisms
     * @param connectTimeoutMillis timeout, after which, if a connection is not established, it is considered failed
     * @param keepAliveTimeoutMillis the maximum number of milliseconds an idle {@link Connection} will be kept in the pool
     * @param keepAliveCheckIntervalMillis the interval, which specifies how often the pool will perform idle {@link Connection}s check
     * @param reconnectDelayMillis the delay to be used before the pool will repeat the attempt to connect to the endpoint after previous connect had failed
     * @param maxReconnectAttempts the maximum number of reconnect attempts that may be made before failure notification.
     * @param asyncPollTimeoutMillis the maximum time, the async poll operation could wait for a connection to become available
     * @param connectionTTLMillis the maximum time, a connection could stay registered with the pool
     * @param failFastWhenMaxSizeReached <tt>true</tt> if the "take" method should fail fast if there is no free connection in the pool and max pool size is reached
     */
    @SuppressWarnings("unchecked")
    protected SingleEndpointPool(final Endpoint<E> endpoint,
            final int corePoolSize, final int maxPoolSize,
            DelayedExecutor delayedExecutor,
            final long connectTimeoutMillis,
            final long keepAliveTimeoutMillis,
            final long keepAliveCheckIntervalMillis,
            final long reconnectDelayMillis,
            final int maxReconnectAttempts,
            final long asyncPollTimeoutMillis,
            final long connectionTTLMillis,
            final boolean failFastWhenMaxSizeReached) {
        
        this.endpoint = endpoint;
        this.corePoolSize = corePoolSize;
        this.maxPoolSize = maxPoolSize;
        this.connectTimeoutMillis = connectTimeoutMillis;
        this.reconnectDelayMillis = reconnectDelayMillis;
        this.keepAliveTimeoutMillis = keepAliveTimeoutMillis;
        this.keepAliveCheckIntervalMillis = keepAliveCheckIntervalMillis;
        this.maxReconnectAttempts = maxReconnectAttempts;
        this.asyncPollTimeoutMillis = asyncPollTimeoutMillis;
        this.connectionTTLMillis = connectionTTLMillis;
        this.failFastWhenMaxSizeReached = failFastWhenMaxSizeReached;
        
        if (delayedExecutor == null) {
            // if custom DelayedExecutor is null - create our own
            final ThreadPoolConfig tpc = ThreadPoolConfig.defaultConfig()
                    .setPoolName("connection-pool-delays-thread-pool")
                    .setCorePoolSize(1)
                    .setMaxPoolSize(1);
            
            ownDelayedExecutorThreadPool =
                    GrizzlyExecutorService.createInstance(tpc);
            ownDelayedExecutor = new DelayedExecutor(
                    ownDelayedExecutorThreadPool);
            ownDelayedExecutor.start();
            
            delayedExecutor = ownDelayedExecutor;
        } else {
            ownDelayedExecutorThreadPool = null;
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
        
        if (keepAliveTimeoutMillis > 0) {
            keepAliveCleanerQueue = delayedExecutor.createDelayQueue(
                    new KeepAliveCleaner(), new KeepAliveCleanerTaskResolver());
            
            keepAliveCleanerQueue.add(new KeepAliveCleanerTask(this),
                    keepAliveCheckIntervalMillis, TimeUnit.MILLISECONDS);
        } else {
            keepAliveCleanerQueue = null;
        }
        
        if (asyncPollTimeoutMillis >= 0) {
            asyncPollTimeoutQueue = delayedExecutor.createDelayQueue(
                    new AsyncPollTimeoutWorker(),
                    new AsyncPollTimeoutTaskResolver());
           
        } else {
            asyncPollTimeoutQueue = null;
        }
        
        if (connectionTTLMillis >= 0) {
            connectionTTLQueue = delayedExecutor.createDelayQueue(
                    new ConnectionTTLWorker(),
                    new ConnectionTTLTaskResolver());
        } else {
            connectionTTLQueue = null;
        }
    }

    /**
     * Constructs SingleEndpointPool instance.
     * 
     * @param endpoint {@link Endpoint} to be used to establish new {@link Connection}s
     * @param corePoolSize the number of {@link Connection}s, kept in the pool, that are immune to keep-alive mechanism
     * @param maxPoolSize the max number of {@link Connection}s kept by this pool
     * @param connectTimeoutQueue the {@link DelayQueue} used by connect timeout mechanism
     * @param reconnectQueue the {@link DelayQueue} used by reconnect mechanism
     * @param keepAliveCleanerQueue the {@link DelayQueue} used by keep-alive mechanism
     * @param asyncPollTimeoutQueue the {@link DelayQueue} used by async connection poll mechanism
     * @param connectionTTLQueue the {@link DelayQueue} used by connection TTL mechanism
     * @param connectTimeoutMillis timeout, after which, if a connection is not established, it is considered failed
     * @param keepAliveTimeoutMillis the maximum number of milliseconds an idle {@link Connection} will be kept in the pool
     * @param keepAliveCheckIntervalMillis the interval, which specifies how often the pool will perform idle {@link Connection}s check
     * @param reconnectDelayMillis the delay to be used before the pool will repeat the attempt to connect to the endpoint after previous connect had failed
     * @param maxReconnectAttempts the maximum number of reconnect attempts that may be made before failure notification.
     * @param asyncPollTimeoutMillis the maximum time, the async poll operation could wait for a connection to become available
     * @param connectionTTLMillis the maximum time, a connection could stay registered with the pool
     * @param failFastWhenMaxSizeReached <tt>true</tt> if the "take" method should fail fast if there is no free connection in the pool and max pool size is reached
     */    
    @SuppressWarnings("unchecked")
    protected SingleEndpointPool(
            final Endpoint<E> endpoint,
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
        
        this.endpoint = endpoint;
        this.corePoolSize = corePoolSize;
        this.maxPoolSize = maxPoolSize;
        this.connectTimeoutMillis = connectTimeoutMillis;
        this.reconnectDelayMillis = reconnectDelayMillis;
        this.keepAliveTimeoutMillis = keepAliveTimeoutMillis;
        this.keepAliveCheckIntervalMillis = keepAliveCheckIntervalMillis;
        this.maxReconnectAttempts = maxReconnectAttempts;
        this.asyncPollTimeoutMillis = asyncPollTimeoutMillis;
        this.connectionTTLMillis = connectionTTLMillis;
        this.failFastWhenMaxSizeReached = failFastWhenMaxSizeReached;
        
        ownDelayedExecutor = null;
        ownDelayedExecutorThreadPool = null;
        
        this.connectTimeoutQueue = connectTimeoutQueue;
        this.reconnectQueue = reconnectQueue;
        this.keepAliveCleanerQueue = keepAliveCleanerQueue;
        if (keepAliveTimeoutMillis > 0) {
            keepAliveCleanerQueue.add(new KeepAliveCleanerTask(this),
                    keepAliveCheckIntervalMillis, TimeUnit.MILLISECONDS);
        }
        this.asyncPollTimeoutQueue = asyncPollTimeoutQueue;
        this.connectionTTLQueue = connectionTTLQueue;
    }
    
    /**
     * @return the endpoint description
     */
    public Endpoint<E> getEndpoint() {
        return endpoint;
    }

    /**
     * @return the number of {@link Connection}s, kept in the pool,
     *         that are immune to keep-alive mechanism
     */
    public int getCorePoolSize() {
        return corePoolSize;
    }

    /**
     * @return the max number of {@link Connection}s kept by this pool
     */
    public int getMaxPoolSize() {
        return maxPoolSize;
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
     * in the pool. The idle {@link Connection}s will be closed till the pool
     * size is greater than <tt>corePoolSize</tt>.
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
     * Returns the current pool size.
     * This value includes connected and connecting (connect in progress)
     * {@link Connection}s.
     * 
     * @return the current pool size
     */
    public int size() {
        synchronized (poolSync) {
            return poolSize + pendingConnections;
        }
    }
    
    /**
     * @return the number of connected {@link Connection}s in the pool.
     * Unlike {@link #size()} the value doesn't include connecting
     * (connect in progress) {@link Connection}s.
     */
    public int getOpenConnectionsCount() {
        synchronized (poolSync) {
            return poolSize;
        }
    }

    /**
     * @return the number of {@link Connection}s ready to be retrieved and used.
     */
    public int getReadyConnectionsCount() {
        synchronized (poolSync) {
            return readyConnections.size();
        }
    }

    /**
     * @return <tt>true</tt> is maximum number of {@link Connection}s the pool
     * can keep is reached and no new {@link Connection} can be established, or
     * <tt>false</tt> otherwise.
     */
    public boolean isMaxCapacityReached() {
        synchronized (poolSync) {
            return maxPoolSize != -1
                    && poolSize + pendingConnections >= maxPoolSize;
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
        synchronized (poolSync) {
            return connectionsMap.containsKey(connection);
        }
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
        synchronized (poolSync) {
            return isBusy0(connectionsMap.get(connection));
        }
    }

    boolean isBusy0(final ConnectionInfo<E> connectionRecord) {
        synchronized (poolSync) {
            return connectionRecord != null && !connectionRecord.isReady();
        }
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
        synchronized (poolSync) {
            return connectionsMap.get(connection);
        }
    }
    
    /**
     * Obtains a {@link Connection} from the pool in non-blocking/asynchronous fashion.
     * Returns a {@link GrizzlyFuture} representing the pending result of the
     * non-blocking/asynchronous obtain task.
     * Future's <tt>get</tt> method will return the {@link Connection} once it
     * becomes available in the pool.
     *
     * <p>
     * If you would like to immediately block waiting
     * for a {@link Connection}, you can use constructions of the form
     * <tt>connection = pool.take().get();</tt>
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
     * @return {@link GrizzlyFuture}
     */
    public GrizzlyFuture<Connection> take() {
        int errorCode = 0;
        GrizzlyFuture<Connection> future = null;
        boolean isCreateNewConnection = false;
        
        try {
            synchronized (poolSync) {
                // we need to maintain this weird if's layout to make sure we
                // create Exceptions or new connections outside of synchronized.
                if (!isClosed) {
                    if (readyConnections.isEmpty()) {
                        if (!failFastWhenMaxSizeReached
                                || !isMaxCapacityReached()
                                || pendingConnections >= getWaitingListSize() + 1) {
                            
                            final AsyncPoll asyncPoll = new AsyncPoll(this);
                            final Link<AsyncPoll> pollLink = new Link<AsyncPoll>(asyncPoll);

                            final FutureImpl<Connection> cancellableFuture
                                    = new SafeFutureImpl<Connection>() {
                                        @Override
                                        protected void onComplete() {
                                            try {
                                                if (!isCancelled()) {
                                                    get();
                                                    return;
                                                }
                                            } catch (Throwable ignored) {
                                            }

                                            synchronized (poolSync) {
                                                removeFromAsyncWaitingList(pollLink);
                                            }
                                        }
                                    };

                            asyncPoll.future = cancellableFuture;
                            addToAsyncWaitingList(pollLink);

                            isCreateNewConnection = checkBeforeOpeningConnection();
                            future = cancellableFuture;
                        } else {
                            errorCode = 2;
                        }
                    } else {
                        future = Futures.createReadyFuture(
                                readyConnections.pollLast().getValue().connection);
                    }
                } else {
                    errorCode = 1;
                }
            }

            switch (errorCode) {
                case 0: {
                    assert future != null;
                    
                    if (isCreateNewConnection) {
                        connect();
                    }
                    
                    return future;
                }
                
                case 1: 
                    return Futures.createReadyFuture(new IOException("The pool is closed"));
                
                case 2: {
                    return Futures.createReadyFuture(new IOException("Max connections exceeded"));
                }
                
                default: {
                    // should never reach this point
                    return Futures.createReadyFuture(new IllegalStateException("Unexpected state"));
                }
            }        
        
        } catch (Exception e) {
            return Futures.createReadyFuture(e);
        }
    }

    /**
     * Obtains a {@link Connection} from the pool in non-blocking/asynchronous fashion.
     * The passed {@link CompletionHandler} will be notified about the result of the
     * non-blocking/asynchronous obtain task.
     * @param completionHandler to be notified once {@link Connection} is available or
     *                          an error occurred
     */
    public void take(final CompletionHandler<Connection> completionHandler) {
        if (completionHandler == null) {
            throw new IllegalArgumentException("The completionHandler argument can not be null");
        }
        
        int errorCode = 0;
        Connection connection = null;
        boolean isCreateNewConnection = false;
        
        try {
            synchronized (poolSync) {
                // we need to maintain this weird if's layout to make sure we
                // create Exceptions or new connections outside of synchronized.
                if (!isClosed) {
                    if (readyConnections.isEmpty()) {
                        if (!failFastWhenMaxSizeReached
                                || !isMaxCapacityReached()
                                || pendingConnections >= getWaitingListSize() + 1) {
                            
                            final AsyncPoll asyncPoll = new AsyncPoll(this);
                            asyncPoll.completionHandler = completionHandler;
                            final Link<AsyncPoll> pollLink = new Link<AsyncPoll>(asyncPoll);

                            addToAsyncWaitingList(pollLink);

                            isCreateNewConnection = checkBeforeOpeningConnection();
                        } else {
                            errorCode = 2;
                        }
                    } else {
                        connection = readyConnections.pollLast().getValue().connection;
                    }
                } else {
                    errorCode = 1;
                }
            }
            
            switch (errorCode) {
                case 0: {
                    if (connection != null) {
                        completionHandler.completed(connection);
                    } else if (isCreateNewConnection) {
                        connect();
                    }
                    
                    break;
                }
                
                case 1: {
                    completionHandler.failed(new IOException("The pool is closed"));
                    break;
                }
                
                case 2: {
                    completionHandler.failed(new IOException("Max connections exceeded"));
                    break;
                }
            }
        } catch (Exception e) {
            completionHandler.failed(e);
        }
    }

    /**
     * @return a {@link Connection} from the pool, if there is one available at the moment,
     *          or <tt>null</tt> otherwise
     * @throws java.io.IOException if the pool is closed
     */
    public Connection poll() throws IOException {
        synchronized (poolSync) {
            if (isClosed) {
                throw new IOException("The pool is closed");
            }

            return !readyConnections.isEmpty()
                    ? readyConnections.pollLast().getValue().connection
                    : null;
        }
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
     * @return <code>true</code> if the connection was successfully released.
     *  If the connection cannot be released, the connection will be closed
     *  and <code>false</code> will be returned.
     */
    public boolean release(final Connection connection) {
        synchronized (poolSync) {
            final ConnectionInfo<E> info = connectionsMap.get(connection);
            if (info == null) {
                connection.closeSilently();
                return false;
            } 
            
            return release0(info);
        }
    }

    /**
     * Same as {@link #release(org.glassfish.grizzly.Connection)}, but is based
     * on connection {@link Link}.
     */
    boolean release0(final ConnectionInfo<E> info) {
        final boolean isKeepAlive;
        AsyncPoll asyncPoller = null;
        
        synchronized (poolSync) {
            if (info.isReady()) {
                return false;
            }

            // close pooled connection, if keepAliveTimeoutMillis == 0
            if (keepAliveTimeoutMillis == 0 && poolSize > corePoolSize) {
                isKeepAlive = false;
            } else {
                isKeepAlive = true;
                asyncPoller = getAsyncPoller();
                if (asyncPoller == null) {
                    readyConnections.offerLast(info.readyStateLink);
                }
            }
        }
        
        if (!isKeepAlive) {
            info.connection.closeSilently();
            return false;
        }
        
        if (asyncPoller != null) {
            Futures.notifyResult(asyncPoller.future,
                    asyncPoller.completionHandler, info.connection);
        }
        
        return true;
    }
    
    /**
     * Attaches "foreign" {@link Connection} to the pool.
     * This method might be used to add to the pool a {@link Connection}, that
     * either has not been created by this pool or has been detached.
     * After calling this method, the {@link Connection} could be still used by
     * the caller and {@link #release(org.glassfish.grizzly.Connection)} should
     * be called to return the {@link Connection} to the pool so it could be
     * reused.
     * 
     * @param connection {@link Connection}
     * @return <tt>true</tt> if the {@link Connection} has been successfully attached,
     *              or <tt>false</tt> otherwise. If the {@link Connection} had
     *              been already registered in the pool - the method call doesn't
     *              have any effect and <tt>true</tt> will be returned.
     * @throws IOException thrown if this pool has been already closed
     */
    public boolean attach(final Connection connection) throws IOException {
        synchronized (poolSync) {
            if (isClosed) {
                throw new IOException("The pool is closed");
            }
            
            if (connectionsMap.containsKey(connection)) {
                return true;
            }
            
            if (!isMaxCapacityReached()) {
                attach0(connection);
                return true;
            }
            
            return false;
        }
    }
    
    /**
     * Detaches a {@link Connection} from the pool.
     * De-registers the {@link Connection} from the pool and decreases the pool
     * size by 1. It is possible to re-attach the detached {@link Connection}
     * later by calling {@link #attach(org.glassfish.grizzly.Connection)}.
     * 
     * If the {@link Connection} was not registered in the pool - the
     * method call doesn't have any effect.
     * 
     * @param connection the {@link Connection} to detach
     * @return <code>true</code> if the connection was successfully detached
     *  from this pool, otherwise returns <code>false</code>
     */
    public boolean detach(final Connection connection) {
        synchronized (poolSync) {
            final ConnectionInfo<E> info = connectionsMap.remove(connection);
            if (info != null) {
                connection.removeCloseListener(closeListener);
                deregisterConnection(info);
                return true;
            }
            return false;
        }
    }
    
    /**
     * Closes the pool and release associated resources.
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
            
            try {
                isClosed = true;

                if (ownDelayedExecutor != null) {
                    ownDelayedExecutor.destroy();
                }
                
                if (ownDelayedExecutorThreadPool != null) {
                    ownDelayedExecutorThreadPool.shutdownNow();
                }
                
                final int size = readyConnections.size();
                for (int i = 0; i < size; i++) {
                    final Connection c = readyConnections.pollLast().getValue().connection;
                    c.closeSilently();
                }
                
                final int asyncWaitingListSize = asyncWaitingList.size();
                IOException exception = null;
                for (int i = 0; i < asyncWaitingListSize; i++) {
                    final AsyncPoll asyncPoll = obtainFromAsyncWaitingList();
                    if (exception == null) {
                        exception = new IOException("The pool is closed");
                    }
                    
                    try {
                        Futures.notifyFailure(asyncPoll.future,
                                asyncPoll.completionHandler, exception);
                    } catch (Exception ignored) {
                    }
                }
                
                for (Map.Entry<Connection, ConnectionInfo<E>> entry : connectionsMap.entrySet()) {
                    deregisterConnection(entry.getValue());
                }
                connectionsMap.clear();
                
            } finally {
                poolSync.notifyAll();
            }
        }
    }
    
    /**
     * The method is called before the pool will try to establish new client
     * connection.
     * Please note, if the method returns <tt>true</tt> it also increases
     * the {@link #pendingConnections} counter, so don't forget to decrease it, if needed.
     * 
     * @return <tt>true</tt> if new connection could be created, or <tt>false</tt> otherwise
     */
    protected boolean checkBeforeOpeningConnection() {
        if (pendingConnections < asyncWaitingList.size()
                && !isMaxCapacityReached()) {
            pendingConnections++;
            return true;
        }
        
        return false;
    }

    /**
     * @return the number of consumers waiting for a connection
     */
    protected int getWaitingListSize() {
        return asyncWaitingList.size();
    }
    
    /**
     * @return <tt>true</tt> if number of live connections is more or equal to
     *          max pool size
     */
    boolean isOverflown() {
        return maxPoolSize != -1 && poolSize >= maxPoolSize;
    }
    
    /**
     * The method will be called to notify about newly open connection (not attached yet)
     */
    void onConnected(final Connection connection) {
        pendingConnections--;
    }
    
    /**
     * The method attaches {@link Connection} to the pool.
     */
    ConnectionInfo<E> attach0(final Connection connection) {
        poolSize++;

        final ConnectionInfo<E> info =
                new ConnectionInfo<E>(connection, this);

        connectionsMap.put(connection, info);
        
        if (connectionTTLMillis >= 0) {
            connectionTTLQueue.add(info,
                    connectionTTLMillis, TimeUnit.MILLISECONDS);
        }

        connection.addCloseListener(closeListener);
        return info;
    }

    /**
     * The method will be called to notify about error occurred during new
     * connection opening.
     */
    void onFailedConnection() {
    }

    /**
     * The method will be called to notify about connection termination.
     */
    void onCloseConnection(final ConnectionInfo<E> info) {
        // If someone is waiting for a connection
        // try to create a new one
        if (getWaitingListSize() > pendingConnections) {
            createConnectionIfPossibleNoSync();
        }
    }

    /**
     * Perform keep-alive check on the ready connections and close connections,
     * that keep-alive timeout has been expired.
     */
    boolean cleanupIdleConnections(final KeepAliveCleanerTask cleanerTask) {
        synchronized (poolSync) {
            if (isClosed) {
                return true;
            }

            if (!readyConnections.isEmpty() && poolSize > corePoolSize) {
                final long now = System.currentTimeMillis();

                try {
                    do {
                        final Link<ConnectionInfo<E>> link = readyConnections.getFirstLink();
                        
                        if ((now - link.getAttachmentTimeStamp()) >= keepAliveTimeoutMillis) {
                            final Connection connection = link.getValue().connection;
                            // CloseListener will update the counters in this thread
                            connection.closeSilently();
                        } else { // the rest of links are ok
                            break;
                        }
                        
                    } while (!readyConnections.isEmpty() && poolSize > corePoolSize);
                } catch (Exception ignore) {
                }
            }
        }

        cleanerTask.timeoutMillis = System.currentTimeMillis() + keepAliveCheckIntervalMillis;
        return false;
    }

    /**
     * Checks if it's possible to create a new {@link Connection} by calling
     * {@link #checkBeforeOpeningConnection()} and if it is possible - establish
     * new connection.
     * 
     * @return <tt>true</tt> if a new {@link Connection} could be open, or <tt>false</tt> otherwise
     */
    protected boolean createConnectionIfPossible() {
        synchronized (poolSync) {
            return createConnectionIfPossibleNoSync();
        }
    }
    
    /**
     * Checks if it's possible to create a new {@link Connection} by calling
     * {@link #checkBeforeOpeningConnection()} and if it is possible - establish
     * new connection.
     * 
     * @return <tt>true</tt> if a new {@link Connection} could be open, or <tt>false</tt> otherwise
     */
    private boolean createConnectionIfPossibleNoSync() {
        if (checkBeforeOpeningConnection()) {
            connect();
            return true;
        }
        
        return false;
    }

    /**
     * Establish new pool connection.
     */
    private void connect() {
        final GrizzlyFuture<Connection> future = endpoint.connect();
        future.addCompletionHandler(defaultConnectionCompletionHandler);
        
        if (connectTimeoutMillis >= 0) {
            final ConnectTimeoutTask connectTimeoutTask
                    = new ConnectTimeoutTask(future);
            
            connectTimeoutQueue.add(connectTimeoutTask,
                    connectTimeoutMillis, TimeUnit.MILLISECONDS);
        }
    }

    private AsyncPoll getAsyncPoller() {
        if (!asyncWaitingList.isEmpty()) {
            return obtainFromAsyncWaitingList();
        }
        
        return null;
    }
    
    private void notifyAsyncPollersOfFailure(final Throwable t) {
        failedConnectAttempts = 0;
        final int waitersToFail = getWaitingListSize() - pendingConnections;
        
        for (int i = 0; i < waitersToFail; i++) {
            final AsyncPoll asyncPoll = obtainFromAsyncWaitingList();
            Futures.notifyFailure(asyncPoll.future,
                                  asyncPoll.completionHandler,
                                  t);
        }
    }

    private void deregisterConnection(final ConnectionInfo<E> info) {
        if (connectionTTLMillis >= 0) {
            connectionTTLQueue.remove(info);
        }
        
        readyConnections.remove(info.readyStateLink);
        poolSize--;

        onCloseConnection(info);
    }

    private void addToAsyncWaitingList(final Link<AsyncPoll> pollLink) {
        asyncWaitingList.offerLast(pollLink);

        if (asyncPollTimeoutMillis >= 0) {
            asyncPollTimeoutQueue.add(pollLink,
                    asyncPollTimeoutMillis, TimeUnit.MILLISECONDS);
        }
    }

    private AsyncPoll obtainFromAsyncWaitingList() {
        final Link<AsyncPoll> link = asyncWaitingList.pollFirst();
        
        if (asyncPollTimeoutMillis >= 0) {
            asyncPollTimeoutQueue.remove(link);
        }
        
        return link.getValue();
    }
    
    private boolean removeFromAsyncWaitingList(final Link<AsyncPoll> pollLink) {
        final boolean result = asyncWaitingList.remove(pollLink);
        
        if (result && asyncPollTimeoutMillis >= 0) {
            asyncPollTimeoutQueue.remove(pollLink);
        }
        
        return result;
    }    
    
    @Override
    public String toString() {
        return getClass().getSimpleName() + "@" + Integer.toHexString(hashCode()) + 
                "{" +
                "endpoint=" + endpoint +
                ", corePoolSize=" + corePoolSize +
                ", maxPoolSize=" + maxPoolSize +
                ", poolSize=" + poolSize +
                ", isClosed=" + isClosed +
                "}";
    }

    
    /**
     * {@link CompletionHandler} to be notified once new {@link Connection} is
     * connected or failed to connect.
     */
    private final class ConnectCompletionHandler
            extends EmptyCompletionHandler<Connection> {
        
        @Override
        public void completed(final Connection connection) {
            if (LOGGER.isLoggable(Level.FINEST)) {
                LOGGER.log(Level.FINEST, "Pool connection is established {0}", connection);
            }

            boolean isOk = false;
            AsyncPoll asyncPoller = null;
            
            synchronized (poolSync) {
               if (!isClosed) {
                   failedConnectAttempts = 0;
                   onConnected(connection);

                   if (!isOverflown()) {
                       isOk = true;
                       
                       final ConnectionInfo<E> info = attach0(connection);
                       asyncPoller = getAsyncPoller();
                       if (asyncPoller == null) {
                           readyConnections.offerLast(info.readyStateLink);
                       }
                   }
                }
            }
            
            if (!isOk) {
                connection.closeSilently();
            } else if (asyncPoller != null) {
                endpoint.onConnect(connection, SingleEndpointPool.this);
                Futures.notifyResult(asyncPoller.future,
                        asyncPoller.completionHandler, connection);
            }
        }

        @Override
        public void cancelled() {
            onFailedToConnect(new ConnectException("Connect timeout"));
        }

        @Override
        public void failed(final Throwable throwable) {
            onFailedToConnect(throwable);
        }

        @SuppressWarnings("unchecked")
        private void onFailedToConnect(final Throwable t) {
            synchronized (poolSync) {
                pendingConnections--;

                onFailedConnection();

                // check if there is still a thread(s) waiting for a connection
                // and reconnect mechanism is enabled
                if (reconnectQueue != null && !asyncWaitingList.isEmpty()) {
                    if (LOGGER.isLoggable(Level.FINEST)) {
                        LOGGER.log(Level.FINEST, "Pool connect operation failed, schedule reconnect");
                    }
                    if (++failedConnectAttempts > maxReconnectAttempts) {
                        notifyAsyncPollersOfFailure(t);
                    } else {
                        reconnectQueue.add(
                                new ReconnectTask(SingleEndpointPool.this),
                                reconnectDelayMillis, TimeUnit.MILLISECONDS);
                    }
                } else {
                    notifyAsyncPollersOfFailure(t);
                }
            }
        }

    }
    
    /**
     * The {@link CloseListener} to be notified, when pool {@link Connection}
     * either busy or ready has been closed, so the pool can adjust its counters.
     */
    private final class PoolConnectionCloseListener
            implements CloseListener<Connection, CloseType> {

        @Override
        public void onClosed(final Connection connection, final CloseType type)
                throws IOException {
            synchronized (poolSync) {
                final ConnectionInfo<E> info = connectionsMap.remove(connection);
                if (info != null) {
                    deregisterConnection(info);
                }
            }
        }
    }

//================================= Connect timeout mechanism ======================

    /**
     * Connect timeout mechanism classes related to DelayedExecutor.
     */
    protected static final class ConnectTimeoutWorker
            implements DelayedExecutor.Worker<ConnectTimeoutTask> {

        @Override
        public boolean doWork(final ConnectTimeoutTask connectTimeoutTask) {
            if (LOGGER.isLoggable(Level.FINEST)) {
                LOGGER.log(Level.FINEST, "Pool connect timed out");
            }
            connectTimeoutTask.connectFuture.cancel(false);
            return true;
        }
    }

    protected final static class ConnectTimeoutTaskResolver
            implements DelayedExecutor.Resolver<ConnectTimeoutTask> {

        @Override
        public boolean removeTimeout(final ConnectTimeoutTask connectTimeoutTask) {
            connectTimeoutTask.timeout = DelayedExecutor.UNSET_TIMEOUT;
            return true;
        }

        @Override
        public long getTimeoutMillis(final ConnectTimeoutTask connectTimeoutTask) {
            return connectTimeoutTask.timeout;
        }

        @Override
        public void setTimeoutMillis(final ConnectTimeoutTask connectTimeoutTask,
                final long timeoutMillis) {
            connectTimeoutTask.timeout = timeoutMillis;
        }
    }
    
    protected final static class ConnectTimeoutTask {
        public long timeout;
        public final GrizzlyFuture<Connection> connectFuture;

        public ConnectTimeoutTask(GrizzlyFuture<Connection> future) {
            this.connectFuture = future;
        }
    }
    
//================================= Keep-alive mechanism ======================
    /**
     * Keep-alive mechanism classes related to DelayedExecutor.
     */
    protected final static class KeepAliveCleaner implements
            DelayedExecutor.Worker<KeepAliveCleanerTask> {
        
        @Override
        public boolean doWork(final KeepAliveCleanerTask cleanerTask) {
            return cleanerTask.pool.cleanupIdleConnections(cleanerTask);
        }
    }

    protected final static class KeepAliveCleanerTaskResolver
            implements DelayedExecutor.Resolver<KeepAliveCleanerTask> {

        @Override
        public boolean removeTimeout(KeepAliveCleanerTask cleanerTask) {
            cleanerTask.timeoutMillis = DelayedExecutor.UNSET_TIMEOUT;
            return true;
        }

        @Override
        public long getTimeoutMillis(KeepAliveCleanerTask cleanerTask) {
            return cleanerTask.timeoutMillis;
        }

        @Override
        public void setTimeoutMillis(final KeepAliveCleanerTask cleanerTask,
                final long timeoutMillis) {
            cleanerTask.timeoutMillis = timeoutMillis;
        }
    }
    
    protected final static class KeepAliveCleanerTask<E> {
        public long timeoutMillis;
        public final SingleEndpointPool<E> pool;

        public KeepAliveCleanerTask(SingleEndpointPool<E> singleEndpointPool) {
            this.pool = singleEndpointPool;
        }
    }
    
//================================= Reconnect mechanism ======================
    
    /**
     * Reconnect mechanism classes related to DelayedExecutor.
     */
    protected static final class Reconnector
            implements DelayedExecutor.Worker<ReconnectTask> {

        @Override
        public boolean doWork(final ReconnectTask reconnectTask) {
            reconnectTask.pool.createConnectionIfPossibleNoSync();
            return true;
        }
    }

    protected final static class ReconnectTaskResolver
            implements DelayedExecutor.Resolver<ReconnectTask> {

        @Override
        public boolean removeTimeout(final ReconnectTask reconnectTask) {
            reconnectTask.timeout = DelayedExecutor.UNSET_TIMEOUT;
            return true;
        }

        @Override
        public long getTimeoutMillis(final ReconnectTask reconnectTask) {
            return reconnectTask.timeout;
        }

        @Override
        public void setTimeoutMillis(final ReconnectTask reconnectTask,
                final long timeoutMillis) {
            reconnectTask.timeout = timeoutMillis;
        }
    }
    
    protected final static class ReconnectTask<E> {
        public long timeout;
        public final SingleEndpointPool<E> pool;

        public ReconnectTask(SingleEndpointPool<E> singleEndpointPool) {
            this.pool = singleEndpointPool;
        }
    }

    /**
     * Async poll timeout mechanism classes related to DelayedExecutor.
     */
    static final class AsyncPollTimeoutWorker
            implements DelayedExecutor.Worker<Link<AsyncPoll>> {

        @Override
        public boolean doWork(final Link<AsyncPoll> asyncPollLink) {
            // even though it's not volatile - dirty check should be good
            // enough for us, because we don't plan to use asyncPollLink in this thread
            if (asyncPollLink.isAttached()) {
                final boolean removed;
                // no volatile barrier, but should be safe, because we access final fields
                final SingleEndpointPool<?> pool = asyncPollLink.getValue().pool;
                synchronized (pool.poolSync) {
                    removed = pool.asyncWaitingList.remove(asyncPollLink);
                }
                
                if (removed) {
                    if (LOGGER.isLoggable(Level.FINEST)) {
                        LOGGER.log(Level.FINEST, "Async poll timed out for {0}",
                                asyncPollLink.getValue());
                    }

                    final AsyncPoll asyncPoll = asyncPollLink.getValue();
                    Futures.notifyFailure(asyncPoll.future,
                                          asyncPoll.completionHandler,
                                          new TimeoutException("Poll timeout expired"));
                }
            }
            
            return true;
        }
    }

    final static class AsyncPollTimeoutTaskResolver
            implements DelayedExecutor.Resolver<Link<AsyncPoll>> {

        @Override
        public boolean removeTimeout(final Link<AsyncPoll> asyncPollLink) {
            asyncPollLink.getValue().timeout = DelayedExecutor.UNSET_TIMEOUT;
            return true;
        }

        @Override
        public long getTimeoutMillis(final Link<AsyncPoll> asyncPollLink) {
            return asyncPollLink.getValue().timeout;
        }

        @Override
        public void setTimeoutMillis(final Link<AsyncPoll> asyncPollLink,
                final long timeoutMillis) {
            asyncPollLink.getValue().timeout = timeoutMillis;
        }
    }
    
    protected static final class AsyncPoll {
        private final SingleEndpointPool pool;
        private FutureImpl<Connection> future;
        private CompletionHandler<Connection> completionHandler;
        
        private long timeout; // timeout stamp

        protected AsyncPoll(final SingleEndpointPool pool) {
            this.pool = pool;
        }
    }
    
//================================= Connect timeout mechanism ======================

    /**
     * Connect timeout mechanism classes related to DelayedExecutor.
     */
    protected static final class ConnectionTTLWorker
            implements DelayedExecutor.Worker<ConnectionInfo> {

        @Override
        public boolean doWork(final ConnectionInfo ci) {
            if (LOGGER.isLoggable(Level.FINEST)) {
                LOGGER.log(Level.FINEST, "Connection {0} TTL expired",
                        ci.connection);
            }
            
            synchronized(ci.endpointPool.poolSync) {
                if (ci.isReady()) {
                    ci.connection.close();
                } else {
                    ci.endpointPool.detach(ci.connection);
                }
            }
            
            return true;
        }
    }

    protected final static class ConnectionTTLTaskResolver
            implements DelayedExecutor.Resolver<ConnectionInfo> {

        @Override
        public boolean removeTimeout(final ConnectionInfo ci) {
            ci.ttlTimeout = DelayedExecutor.UNSET_TIMEOUT;
            return true;
        }

        @Override
        public long getTimeoutMillis(final ConnectionInfo ci) {
            return ci.ttlTimeout;
        }

        @Override
        public void setTimeoutMillis(final ConnectionInfo ci,
                final long timeoutMillis) {
            ci.ttlTimeout = timeoutMillis;
        }
    }
    
    /**
     * The Builder class responsible for constructing {@link SingleEndpointPool}.
     * 
     * @param <E> endpoint address type, for example {@link SocketAddress} for TCP and UDP transports
     */
    public static class Builder<E> {
        /**
         * The endpoint information
         */
        protected Endpoint<E> endpoint;
        /**
         * {@link ConnectorHandler} used to establish new {@link Connection}s
         */
        protected ConnectorHandler<E> connectorHandler;
        /**
         * Endpoint address
         */
        protected E endpointAddress;

        /**
         * Local bind address.
         */
        protected E localEndpointAddress;

        /**
         * The number of {@link Connection}s, kept in the pool, that are immune to keep-alive mechanism
         */
        protected int corePoolSize;
        /**
         * The max number of {@link Connection}s kept by this pool
         */
        protected int maxPoolSize;
        /**
         * the {@link DelayedExecutor} to be used for keep-alive and
         * reconnect mechanisms
         */
        protected DelayedExecutor delayedExecutor;
        /**
         * Connect timeout, after which, if a connection is not established, it is
         * considered failed
         */
        protected long connectTimeoutMillis;
        /**
         * the delay to be used before the pool will repeat the attempt to connect to
         * the endpoint after previous connect had failed
         */
        protected long reconnectDelayMillis;
        /**
         * Maximum number of attempts that will be made to reconnect before
         * notification of failure occurs.
         */
        protected int maxReconnectAttempts;
        /**
         * Async poll timeout, after which, the async connection poll operation will
         * be failed with a timeout exception
         */
        protected long asyncPollTimeoutMillis;
        /**
         * the maximum amount of time, a {@link Connection} could stay registered with the pool
         * Once timeout is hit - the connection will be either closed, if it's idle,
         * or detached from the pool, if it's being used.
         */
        protected long connectionTTLMillis;
        
        /**
         * if true, the "take" method will fail fast if there is no free connection
         * in the pool and max pool size is reached.
         */
        protected boolean failFastWhenMaxSizeReached;
        
        /**
         * the maximum number of milliseconds an idle {@link Connection} will be
         * kept in the pool. The idle {@link Connection}s will be closed till the pool
         * size is greater than <tt>corePoolSize</tt>
         */
        protected long keepAliveTimeoutMillis;
        /**
         * the interval, which specifies how often the pool will perform idle {@link Connection}s check
         */
        protected long keepAliveCheckIntervalMillis;

        
        protected Builder() {
            maxPoolSize = 4;
            connectTimeoutMillis = -1;
            reconnectDelayMillis = -1;
            maxReconnectAttempts = 5;
            asyncPollTimeoutMillis = -1;
            connectionTTLMillis = -1;
            keepAliveTimeoutMillis = 30000;
            keepAliveCheckIntervalMillis = 5000;
        }
        
        protected Builder(final Endpoint<E> endpoint,
                final ConnectorHandler<E> connectorHandler,
                final E endpointAddress, final E localEndpointAddress,
                final int corePoolSize, final int maxPoolSize,
                final DelayedExecutor delayedExecutor,
                final long connectTimeoutMillis,
                final long reconnectDelayMillis,
                final int maxReconnectAttempts,
                final long asyncPollTimeoutMillis,
                final long connectionTTLMillis,
                final boolean failFastWhenMaxSizeReached,
                final long keepAliveTimeoutMillis,
                final long keepAliveCheckIntervalMillis) {
            
            this.endpoint = endpoint;
            this.connectorHandler = connectorHandler;
            this.endpointAddress = endpointAddress;
            this.localEndpointAddress = localEndpointAddress;
            this.corePoolSize = corePoolSize;
            this.maxPoolSize = maxPoolSize;
            this.delayedExecutor = delayedExecutor;
            this.connectTimeoutMillis = connectTimeoutMillis;
            this.reconnectDelayMillis = reconnectDelayMillis;
            this.maxReconnectAttempts = maxReconnectAttempts;
            this.asyncPollTimeoutMillis = asyncPollTimeoutMillis;
            this.connectionTTLMillis = connectionTTLMillis;
            this.failFastWhenMaxSizeReached = failFastWhenMaxSizeReached;
            this.keepAliveTimeoutMillis = keepAliveTimeoutMillis;
            this.keepAliveCheckIntervalMillis = keepAliveCheckIntervalMillis;
        }
        
        /**
         * Sets the {@link ConnectorHandler} used to establish new {@link Connection}s.
         * 
         * @param connectorHandler {@link ConnectorHandler}
         * @return this {@link Builder}
         */
        public Builder<E> connectorHandler(final ConnectorHandler<E> connectorHandler) {
            this.connectorHandler = connectorHandler;
            return this;
        }
        
        /**
         * Sets the endpoint address.
         * 
         * @param endpointAddress
         * @return this {@link Builder}
         */
        public Builder<E> endpointAddress(final E endpointAddress) {
            this.endpointAddress = endpointAddress;
            return this;
        }

        /**
         * Sets the local endpoint address.
         *
         * @param localEndpointAddress
         * @return this {@link Builder}
         */
        public Builder<E> localEndpointAddress(final E localEndpointAddress) {
            this.localEndpointAddress = localEndpointAddress;
            return this;
        }
        
        /**
         * Sets the endpoint information.
         * If set, this setting precedes the {@link #connectorHandler(org.glassfish.grizzly.ConnectorHandler)},
         * {@link #endpointAddress(java.lang.Object)} and {@link #localEndpointAddress(java.lang.Object)}
         * values, if they were or will be set.
         * 
         * @param endpoint {@link Endpoint}
         * @return this {@link Builder}
         */
        public Builder<E> endpoint(final Endpoint<E> endpoint) {
            this.endpoint = endpoint;
            return this;
        }
        
        /**
         * Sets the number of {@link Connection}s, kept in the pool,
         * that are immune to keep-alive mechanism.
         * Default value is 0.
         * 
         * @param corePoolSize
         * @return this {@link Builder}
         */
        public Builder<E> corePoolSize(final int corePoolSize) {
            this.corePoolSize = corePoolSize;
            return this;
        }
        
        /**
         * Sets the max number of {@link Connection}s kept by this pool.
         * Default value is 4.
         * 
         * @param maxPoolSize
         * @return this {@link Builder}
         */        
        public Builder<E> maxPoolSize(final int maxPoolSize) {
            this.maxPoolSize = maxPoolSize;
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
         *  If the reconnect mechanism isn't enabled, this property is ignored.
         *
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
         * Constructs {@link SingleEndpointPool}.
         * @return {@link SingleEndpointPool}
         */
        
        public SingleEndpointPool<E> build() {
            final Endpoint<E> e;
            if (endpoint == null) {
                if (connectorHandler == null) {
                    throw new IllegalStateException("Neither Endpoint nor ConnectorHandler is set");
                }

                if (endpointAddress == null) {
                    throw new IllegalStateException("Neither Endpoint nor endpoint address is set");
                }
                
                e = Endpoint.Factory.create(endpointAddress.toString() +
                        (localEndpointAddress != null
                                ? localEndpointAddress.toString()
                                : ""), endpointAddress, localEndpointAddress,
                                connectorHandler);
            } else {
                e = endpoint;
            }

            if (keepAliveTimeoutMillis >= 0 && keepAliveCheckIntervalMillis < 0) {
                throw new IllegalStateException("Keep-alive timeout is set, but keepAliveCheckInterval is invalid");
            }

            if (maxReconnectAttempts < 0) {
                throw new IllegalStateException("Max reconnect attempts must not be a negative value");
            }

            return build0(e);
        }

        protected SingleEndpointPool<E> build0(final Endpoint<E> e) {
            return new SingleEndpointPool<E>(e,
                    corePoolSize, maxPoolSize, delayedExecutor,
                    connectTimeoutMillis, keepAliveTimeoutMillis,
                    keepAliveCheckIntervalMillis, reconnectDelayMillis,
                    maxReconnectAttempts, asyncPollTimeoutMillis,
                    connectionTTLMillis, failFastWhenMaxSizeReached);
        }
    }

}
