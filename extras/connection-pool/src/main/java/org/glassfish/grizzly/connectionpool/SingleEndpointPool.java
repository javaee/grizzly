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
import java.net.ConnectException;
import java.net.SocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.grizzly.CloseListener;
import org.glassfish.grizzly.CloseType;
import org.glassfish.grizzly.Closeable;
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
 * 
 * @author Alexey Stashok
 */
public class SingleEndpointPool<E> {
    private static final Logger LOGGER = Grizzly.logger(SingleEndpointPool.class);
    
    /**
     * Returns single endpoint pool {@link Builder}.
     * 
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
     * {@link ConnectorHandler} used to establish new {@link Connection}s
     */
    private final ConnectorHandler<E> connectorHandler;
    /**
     * Endpoint address
     */
    private final E endpointAddress;
    /**
     * Local bind address.
     */
    private final E localEndpointAddress;
    /**
     * The number of {@link Connection}s, kept in the pool, that are immune to keep-alive mechanism
     */
    private final int corePoolSize;
    /**
     * The max number of {@link Connection}s kept by this pool
     */
    private final int maxPoolSize;
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
     * @param connectorHandler {@link ConnectorHandler} to be used to establish new {@link Connection}s
     * @param endpointAddress endpoint address
     * @param localEndpointAddress local bind address.  May be <code>null</code>.
     * @param corePoolSize the number of {@link Connection}s, kept in the pool, that are immune to keep-alive mechanism
     * @param maxPoolSize the max number of {@link Connection}s kept by this pool
     * @param delayedExecutor custom {@link DelayedExecutor} to be used by keep-alive and reconnect mechanisms
     * @param connectTimeoutMillis timeout, after which, if a connection is not established, it is considered failed
     * @param keepAliveTimeoutMillis the maximum number of milliseconds an idle {@link Connection} will be kept in the pool
     * @param keepAliveCheckIntervalMillis the interval, which specifies how often the pool will perform idle {@link Connection}s check
     * @param reconnectDelayMillis the delay to be used before the pool will repeat the attempt to connect to the endpoint after previous connect had failed
     * @param maxReconnectAttempts the maximum number of reconnect attempts that may be made before failure notification.
     */
    @SuppressWarnings("unchecked")
    protected SingleEndpointPool(
            final ConnectorHandler<E> connectorHandler,
            final E endpointAddress,
            final E localEndpointAddress,
            final int corePoolSize, final int maxPoolSize,
            DelayedExecutor delayedExecutor,
            final long connectTimeoutMillis,
            final long keepAliveTimeoutMillis,
            final long keepAliveCheckIntervalMillis,
            final long reconnectDelayMillis,
            final int maxReconnectAttempts) {
        this.connectorHandler = connectorHandler;
        this.endpointAddress = endpointAddress;
        this.localEndpointAddress = localEndpointAddress;

        this.corePoolSize = corePoolSize;
        this.maxPoolSize = maxPoolSize;
        this.connectTimeoutMillis = connectTimeoutMillis;
        this.reconnectDelayMillis = reconnectDelayMillis;
        this.keepAliveTimeoutMillis = keepAliveTimeoutMillis;
        this.keepAliveCheckIntervalMillis = keepAliveCheckIntervalMillis;
        this.maxReconnectAttempts = maxReconnectAttempts;
        
        if (delayedExecutor == null) {
            // if custom DelayedExecutor is null - create our own
            final ThreadPoolConfig tpc = ThreadPoolConfig.newConfig()
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
            
            keepAliveCleanerQueue.add(new KeepAliveCleanerTask(this),
                    keepAliveCheckIntervalMillis, TimeUnit.MILLISECONDS);
        } else {
            keepAliveCleanerQueue = null;
        }
    }

    /**
     * Constructs SingleEndpointPool instance.
     * 
     * @param connectorHandler {@link ConnectorHandler} to be used to establish new {@link Connection}s
     * @param endpointAddress endpoint address
     * @param localEndpointAddress local bind address.  May be <code>null</code>.
     * @param corePoolSize the number of {@link Connection}s, kept in the pool, that are immune to keep-alive mechanism
     * @param maxPoolSize the max number of {@link Connection}s kept by this pool
     * @param connectTimeoutQueue the {@link DelayQueue} used by connect timeout mechanism
     * @param reconnectQueue the {@link DelayQueue} used by reconnect mechanism
     * @param keepAliveCleanerQueue the {@link DelayQueue} used by keep-alive mechanism
     * @param connectTimeoutMillis timeout, after which, if a connection is not established, it is considered failed
     * @param keepAliveTimeoutMillis the maximum number of milliseconds an idle {@link Connection} will be kept in the pool
     * @param keepAliveCheckIntervalMillis the interval, which specifies how often the pool will perform idle {@link Connection}s check
     * @param reconnectDelayMillis the delay to be used before the pool will repeat the attempt to connect to the endpoint after previous connect had failed
     * @param maxReconnectAttempts the maximum number of reconnect attempts that may be made before failure notification.
     */    
    @SuppressWarnings("unchecked")
    protected SingleEndpointPool(
            final ConnectorHandler<E> connectorHandler,
            final E endpointAddress,
            final E localEndpointAddress,
            final int corePoolSize, final int maxPoolSize,
            final DelayQueue<ConnectTimeoutTask> connectTimeoutQueue,
            final DelayQueue<ReconnectTask> reconnectQueue,
            final DelayQueue<KeepAliveCleanerTask> keepAliveCleanerQueue,
            final long connectTimeoutMillis,
            final long keepAliveTimeoutMillis,
            final long keepAliveCheckIntervalMillis,
            final long reconnectDelayMillis,
            final int maxReconnectAttempts) {
        this.connectorHandler = connectorHandler;
        this.endpointAddress = endpointAddress;
        this.localEndpointAddress = localEndpointAddress;

        this.corePoolSize = corePoolSize;
        this.maxPoolSize = maxPoolSize;
        this.connectTimeoutMillis = connectTimeoutMillis;
        this.reconnectDelayMillis = reconnectDelayMillis;
        this.keepAliveTimeoutMillis = keepAliveTimeoutMillis;
        this.keepAliveCheckIntervalMillis = keepAliveCheckIntervalMillis;
        this.maxReconnectAttempts = maxReconnectAttempts;
        ownDelayedExecutor = null;
        
        this.connectTimeoutQueue = connectTimeoutQueue;
        this.reconnectQueue = reconnectQueue;
        this.keepAliveCleanerQueue = keepAliveCleanerQueue;
        if (keepAliveTimeoutMillis >= 0) {
            keepAliveCleanerQueue.add(new KeepAliveCleanerTask(this),
                    keepAliveCheckIntervalMillis, TimeUnit.MILLISECONDS);
        }
    }
    
    /**
     * Returns the current pool size.
     * This value includes connected and connecting (connect in progress)
     * {@link Connection}s.
     */
    public int size() {
        synchronized (poolSync) {
            return poolSize + pendingConnections;
        }
    }
    
    /**
     * Returns the number of connected {@link Connection}s in the pool.
     * Unlike {@link #size()} the value doesn't include connecting
     * (connect in progress) {@link Connection}s.
     */
    public int getOpenConnectionsCount() {
        synchronized (poolSync) {
            return poolSize;
        }
    }

    /**
     * Returns the number of {@link Connection}s ready to be retrieved and used.
     */
    public int getReadyConnectionsCount() {
        synchronized (poolSync) {
            return readyConnections.size();
        }
    }

    /**
     * Returns <tt>true</tt> is maximum number of {@link Connection}s the pool
     * can keep is reached and no new {@link Connection} can established, or
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

    @Override
    public String toString() {
        return "SingleEndpointPool{" +
                "endpointAddress=" + endpointAddress +
                ", localEndpointAddress=" + localEndpointAddress +
                ", corePoolSize=" + corePoolSize +
                ", maxPoolSize=" + maxPoolSize +
                ", poolSize=" + poolSize +
                ", isClosed=" + isClosed +
                "} " + super.toString();
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
        synchronized (poolSync) {
            try {
                if (isClosed) {
                    return Futures.createReadyFuture(
                            new IOException("The pool is closed"));
                }

                if (!readyConnections.isEmpty()) {
                    return Futures.createReadyFuture(
                            readyConnections.pollLast().getValue().connection);
                }

                final AsyncPoll asyncPoll = new AsyncPoll();
                final Link<AsyncPoll> pollLink = new Link<AsyncPoll>(asyncPoll);
                
                final FutureImpl<Connection> cancellableFuture =
                        new SafeFutureImpl<Connection>() {
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
                            asyncWaitingList.remove(pollLink);
                        }
                    }                    
                };
                
                asyncPoll.future = cancellableFuture;
                asyncWaitingList.offerLast(pollLink);

                createConnectionIfPossibleNoSync();
                
                return cancellableFuture;
            } catch (Exception e) {
                return Futures.createReadyFuture(e);
            }
        }
    }

    /**
     * Obtains a {@link Connection} from the pool in non-blocking/asynchronous fashion.
     * The passed {@link CompletionHandler} will be notified about the result of the
     * non-blocking/asynchronous obtain task.
     */
    public void take(final CompletionHandler<Connection> completionHandler) {
        if (completionHandler == null) {
            throw new IllegalArgumentException("The completionHandler argument can not be null");
        }
        
        synchronized (poolSync) {
            try {
                if (isClosed) {
                    completionHandler.failed(new IOException("The pool is closed"));
                    return;
                }

                if (!readyConnections.isEmpty()) {
                    completionHandler.completed(
                            readyConnections.pollLast().getValue().connection);
                    return;
                }

                final AsyncPoll asyncPoll = new AsyncPoll();
                final Link<AsyncPoll> pollLink = new Link<AsyncPoll>(asyncPoll);
                
                asyncPoll.completionHandler = completionHandler;
                asyncWaitingList.offerLast(pollLink);
                
                createConnectionIfPossibleNoSync();
            } catch (Exception e) {
                completionHandler.failed(e);
            }
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
        synchronized (poolSync) {
            if (info.isReady()) {
                return false;
            }
            
            readyConnections.offerLast(info.readyStateLink);
            notifyAsyncPoller();
            return true;
        }
    }
    
    /**
     * Attaches "foreign" {@link Connection} to the pool.
     * This method might be used to add to the pool a {@link Connection}, that
     * either has not been created by this pool or has been detached.
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
            
            if (checkBeforeOpeningConnection()) {
                defaultConnectionCompletionHandler.completed(connection);
                return true;
            } else {
                return false;
            }
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
     * @returns <code>true</code> if the connection was successfully detached
     *  from this pool, otherwise returns <code>false</code>
     */
    public boolean detach(final Connection connection) {
        synchronized (poolSync) {
            final ConnectionInfo<E> info = connectionsMap.remove(connection);
            if (info != null) {
                if (info.isReady()) {
                    return false;
                }

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
                
                final int size = readyConnections.size();
                for (int i = 0; i < size; i++) {
                    final Connection c = readyConnections.pollLast().getValue().connection;
                    c.closeSilently();
                }
                
                final int asyncWaitingListSize = asyncWaitingList.size();
                IOException exception = null;
                for (int i = 0; i < asyncWaitingListSize; i++) {
                    final AsyncPoll asyncPoll = asyncWaitingList.pollFirst().getValue();
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
     */
    protected boolean checkBeforeOpeningConnection() {
        if (!isMaxCapacityReached()) {
            pendingConnections++;
            return true;
        }
        
        return false;
    }
    
    /**
     * The method will be called to notify about newly open connection.
     */
    void onOpenConnection(final ConnectionInfo<E> info) {
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
        if (!asyncWaitingList.isEmpty()) {
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
     */
    private boolean createConnectionIfPossibleNoSync() {
        if (checkBeforeOpeningConnection()) {
            if (connectTimeoutMillis < 0) {
                connectorHandler.connect(endpointAddress, localEndpointAddress,
                        defaultConnectionCompletionHandler);
            } else {
                final GrizzlyFuture<Connection> future =
                        connectorHandler.connect(endpointAddress, localEndpointAddress);
                final ConnectTimeoutTask connectTimeoutTask =
                        new ConnectTimeoutTask(future);
                
                connectTimeoutQueue.add(connectTimeoutTask,
                        connectTimeoutMillis, TimeUnit.MILLISECONDS);
                future.addCompletionHandler(defaultConnectionCompletionHandler);
            }
            
            return true;
        }
        
        return false;
    }
    
    private void notifyAsyncPoller() {
        if (!asyncWaitingList.isEmpty() && !readyConnections.isEmpty()) {
            ConnectionInfo<E> info = readyConnections.pollLast().getValue();
            
            final Connection connection = info.connection;

            final AsyncPoll asyncPoll =
                    asyncWaitingList.pollFirst().getValue();
            Futures.notifyResult(asyncPoll.future,
                    asyncPoll.completionHandler, connection);
        }
    }

    private void notifyAsyncPollersOfFailure(final Throwable t) {
        failedConnectAttempts = 0;
        while (!asyncWaitingList.isEmpty()) {
            final AsyncPoll asyncPoll =
                    asyncWaitingList.pollFirst().getValue();
            Futures.notifyFailure(asyncPoll.future,
                                  asyncPoll.completionHandler,
                                  t);
        }
    }

    private void deregisterConnection(final ConnectionInfo<E> info) {
        readyConnections.remove(info.readyStateLink);
        poolSize--;

        onCloseConnection(info);
    }
        
    /**
     * {@link CompletionHandler} to be notified once new {@link Connection} is
     * connected or failed to connect.
     */
    private final class ConnectCompletionHandler
            extends EmptyCompletionHandler<Connection> {
        
        private final ConnectTimeoutTask connectTimeoutTask;

        public ConnectCompletionHandler() {
            this(null);
        }

        public ConnectCompletionHandler(final ConnectTimeoutTask connectTimeoutTask) {
            this.connectTimeoutTask = connectTimeoutTask;
        }

        @Override
        public void completed(final Connection connection) {
            if (LOGGER.isLoggable(Level.FINEST)) {
                LOGGER.log(Level.FINE, "Pool connection is established {0}", connection);
            }
            
            if (connectTimeoutTask != null) {
                connectTimeoutQueue.remove(connectTimeoutTask);
            }
            
            synchronized (poolSync) {
               if (!isClosed) {
                    final ConnectionInfo<E> info =
                            new ConnectionInfo<E>(connection, SingleEndpointPool.this);
                    
                    connectionsMap.put(connection, info);
                    readyConnections.offerLast(info.readyStateLink);
                    
                    poolSize++;
                    pendingConnections--;
                    failedConnectAttempts = 0;

                    onOpenConnection(info);

                    connection.addCloseListener(closeListener);
                    notifyAsyncPoller();
                } else {
                    connection.closeSilently();
                }
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
            if (connectTimeoutTask != null) {
                connectTimeoutQueue.remove(connectTimeoutTask);
            }

            synchronized (poolSync) {
                pendingConnections--;

                onFailedConnection();

                // check if there is still a thread(s) waiting for a connection
                // and reconnect mechanism is enabled
                if (reconnectQueue != null && !asyncWaitingList.isEmpty()) {
                    if (LOGGER.isLoggable(Level.FINEST)) {
                        LOGGER.log(Level.FINE, "Pool connect operation failed, schedule reconnect");
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
            implements CloseListener {

        @Override
        public void onClosed(final Closeable closeable, final CloseType type)
                throws IOException {
            synchronized (poolSync) {
                assert closeable instanceof Connection;
                
                final ConnectionInfo<E> info = connectionsMap.remove(
                        (Connection) closeable);
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
                LOGGER.log(Level.FINE, "Pool connect timed out");
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
    
    private final class AsyncPoll {
        private CompletionHandler<Connection> completionHandler;
        private FutureImpl<Connection> future;
    }
    
    /**
     * The Builder class responsible for constructing {@link SingleEndpointPool}.
     * 
     * @param <E> endpoint address type, for example {@link SocketAddress} for TCP and UDP transports
     */
    public static class Builder<E> {
        /**
         * {@link ConnectorHandler} used to establish new {@link Connection}s
         */
        private ConnectorHandler<E> connectorHandler;
        /**
         * Endpoint address
         */
        private E endpointAddress;

        /**
         * Local bind address.
         */
        private E localEndpointAddress;

        /**
         * The number of {@link Connection}s, kept in the pool, that are immune to keep-alive mechanism
         */
        private int corePoolSize = 0;
        /**
         * The max number of {@link Connection}s kept by this pool
         */
        private int maxPoolSize = 4;
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
         * @return this {@link Builder}
         */
        public Builder<E> localEndpointAddress(final E localEndpointAddress) {
            this.localEndpointAddress = localEndpointAddress;
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
            if (connectorHandler == null) {
                throw new IllegalStateException("ConnectorHandler is not set");
            }
            
            if (endpointAddress == null) {
                throw new IllegalStateException("Endpoint address is not set");
            }

            if (keepAliveTimeoutMillis >= 0 && keepAliveCheckIntervalMillis < 0) {
                throw new IllegalStateException("Keep-alive timeout is set, but keepAliveCheckInterval is invalid");
            }

            if (maxReconnectAttempts < 0) {
                throw new IllegalStateException("Max reconnect attempts must not be a negative value");
            }
            
            return new SingleEndpointPool<E>(connectorHandler, endpointAddress,
                    localEndpointAddress, corePoolSize, maxPoolSize, delayedExecutor,
                    connectTimeoutMillis, keepAliveTimeoutMillis,
                    keepAliveCheckIntervalMillis, reconnectDelayMillis,
                    maxReconnectAttempts);
        }
    }

}
