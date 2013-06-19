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
import java.net.SocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.glassfish.grizzly.CloseListener;
import org.glassfish.grizzly.CloseType;
import org.glassfish.grizzly.CompletionHandler;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.ConnectorHandler;
import org.glassfish.grizzly.EmptyCompletionHandler;
import org.glassfish.grizzly.nio.transport.TCPNIOConnectorHandler;
import org.glassfish.grizzly.nio.transport.UDPNIOConnectorHandler;
import org.glassfish.grizzly.threadpool.GrizzlyExecutorService;
import org.glassfish.grizzly.threadpool.ThreadPoolConfig;
import org.glassfish.grizzly.utils.DelayedExecutor;
import org.glassfish.grizzly.utils.DelayedExecutor.DelayQueue;

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

    /**
     * {@link CompletionHandler} to be notified once
     * {@link ConnectorHandler#connect(java.lang.Object)} is complete
     */
    private final ConnectCompletionHandler connectionCompletionHandler =
            new ConnectCompletionHandler();
    /**
     * {@link CloseListener} to be notified once pooled {@link Connection} is closed
     */
    private final PoolConnectionCloseListener closeListener =
            new PoolConnectionCloseListener();
    
    /**
     * The {@link Chain} of ready connections
     */
    private final Chain<Connection> readyConnections = new Chain<Connection>();
    
    /**
     * The {@link Map} contains *all* pooled {@link Connection}s
     */
    private final Map<Connection, Link<Connection>> connectionsMap =
            new HashMap<Connection, Link<Connection>>();
    
    /**
     * Sync object
     */
    private final Object poolSync = new Object();
    
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
     * Endpoint address
     */
    private final E endpointAddress;

    /**
     * The number of {@link Connection}s, kept in the pool, that are immune to keep-alive mechanism
     */
    private final int corePoolSize;
    /**
     * The max number of {@link Connection}s kept by this pool
     */
    private final int maxPoolSize;
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
     * Number of threads currently polling for available {@link Connection}
     */
    private int waitListSize;

    /**
     * Constructs SingleEndpointPool instance.
     * 
     * @param connectorHandler {@link ConnectorHandler} to be used to establish new {@link Connection}s.
     * @param endpointAddress endpoint address.
     * @param corePoolSize the number of {@link Connection}s, kept in the pool, that are immune to keep-alive mechanism.
     * @param maxPoolSize the max number of {@link Connection}s kept by this pool.
     * @param delayedExecutor custom {@link DelayedExecutor} to be used by keep-alive and reconnect mechanisms.
     * @param keepAliveTimeoutMillis the maximum number of milliseconds an idle {@link Connection} will be kept in the pool.
     * @param keepAliveCheckIntervalMillis the interval, which specifies how often the pool will perform idle {@link Connection}s check.
     * @param reconnectDelayMillis the delay to be used before the pool will repeat the attempt to connect to the endpoint after previous connect had failed.
     */
    @SuppressWarnings("unchecked")
    public SingleEndpointPool(
            final ConnectorHandler<E> connectorHandler,
            final E endpointAddress,
            final int corePoolSize, final int maxPoolSize,
            DelayedExecutor delayedExecutor,
            final long keepAliveTimeoutMillis,
            final long keepAliveCheckIntervalMillis,
            final long reconnectDelayMillis) {
        this.connectorHandler = connectorHandler;
        this.endpointAddress = endpointAddress;

        this.corePoolSize = corePoolSize;
        this.maxPoolSize = maxPoolSize;
        this.reconnectDelayMillis = reconnectDelayMillis;
        this.keepAliveTimeoutMillis = keepAliveTimeoutMillis;
        this.keepAliveCheckIntervalMillis = keepAliveCheckIntervalMillis;
        
        if (delayedExecutor == null) {
            // if custom DelayedExecutor is null - create our own
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
            
            keepAliveCleanerQueue.add(new KeepAliveCleanerTask(this),
                    keepAliveCheckIntervalMillis, TimeUnit.MILLISECONDS);
        } else {
            keepAliveCleanerQueue = null;
        }
    }

    /**
     * Constructs SingleEndpointPool instance.
     * 
     * @param connectorHandler {@link ConnectorHandler} to be used to establish new {@link Connection}s.
     * @param endpointAddress endpoint address.
     * @param corePoolSize the number of {@link Connection}s, kept in the pool, that are immune to keep-alive mechanism.
     * @param maxPoolSize the max number of {@link Connection}s kept by this pool.
     * @param reconnectQueue the {@link DelayQueue} used by reconnect mechanism.
     * @param keepAliveCleanerQueue the {@link DelayQueue} used by keep-alive mechanism.
     * @param keepAliveTimeoutMillis the maximum number of milliseconds an idle {@link Connection} will be kept in the pool.
     * @param keepAliveCheckIntervalMillis the interval, which specifies how often the pool will perform idle {@link Connection}s check.
     * @param reconnectDelayMillis the delay to be used before the pool will repeat the attempt to connect to the endpoint after previous connect had failed.
     */    
    @SuppressWarnings("unchecked")
    protected SingleEndpointPool(
            final ConnectorHandler<E> connectorHandler,
            final E endpointAddress,
            final int corePoolSize, final int maxPoolSize,
            final DelayQueue<ReconnectTask> reconnectQueue,
            final DelayQueue<KeepAliveCleanerTask> keepAliveCleanerQueue,
            final long keepAliveTimeoutMillis,
            final long keepAliveCheckIntervalMillis,
            final long reconnectDelayMillis) {
        this.connectorHandler = connectorHandler;
        this.endpointAddress = endpointAddress;

        this.corePoolSize = corePoolSize;
        this.maxPoolSize = maxPoolSize;
        this.reconnectDelayMillis = reconnectDelayMillis;
        this.keepAliveTimeoutMillis = keepAliveTimeoutMillis;
        this.keepAliveCheckIntervalMillis = keepAliveCheckIntervalMillis;

        ownDelayedExecutor = null;
        
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
            return poolSize + pendingConnections >= maxPoolSize;
        }
    }
    
    /**
     * Retrieves {@link Connection} from the pool, waiting if necessary
     * until a {@link Connection} becomes available.
     * 
     * @return {@link Connection}
     * @throws IOException thrown if this pool has been already closed
     * @throws InterruptedException if interrupted while waiting
     */
    public Connection take() throws IOException, InterruptedException {
        synchronized (poolSync) {
            checkNotClosed();
            
            if (!readyConnections.isEmpty()) {
                final Connection connection =
                        readyConnections.pollLast().getValue();

                return connection;
            }

            try {
                waitListSize++;
                do {
                    createConnectionIfPossibleNoSync();

                    poolSync.wait();

                    if (isClosed) {
                        break;
                    }

                    if (!readyConnections.isEmpty()) {
                        return readyConnections.pollLast().getValue();
                    }
                } while (true);

                throw new IOException("The pool is closed");
            } finally {
                waitListSize--;
            }
        }
    }

    /**
     * Retrieves {@link Connection} from the pool, waiting up to the
     * specified wait time if necessary for a {@link Connection} to become available.
     * 
     * If the timeout less than zero (timeout &lt; 0) - the method call is equivalent to {@link #take()}.
     * If the timeout is equal to zero (timeout == 0) - the method call is equivalent to {@link #poll()}.
     * 
     * @param timeout how long to wait before giving up, in units of
     *        <tt>unit</tt>
     * @param timeunit a <tt>TimeUnit</tt> determining how to interpret the
     *        <tt>timeout</tt> parameter
     * @return {@link Connection}, or <tt>null</tt> if the
     *         specified waiting time elapses before a {@link Connection} is available
     * @throws IOException thrown if this pool has been already closed
     * @throws InterruptedException if interrupted while waiting
     */
    public Connection poll(final long timeout, final TimeUnit timeunit)
            throws IOException, InterruptedException {
        final long timeoutMillis = timeout <= 0 ?
                timeout :
                TimeUnit.MILLISECONDS.convert(timeout, timeunit);
        
        if (timeoutMillis < 0) {
            return take();
        }
        
        synchronized (poolSync) {
            checkNotClosed();
            
            if (!readyConnections.isEmpty()) {
                final Connection connection =
                        readyConnections.pollLast().getValue();

                return connection;
            }

            if (timeoutMillis == 0) {
                return null;
            }
            
            long remainingMillis = timeoutMillis;
            long startTime = System.currentTimeMillis();
            
            try {
                waitListSize++;
                do {
                    createConnectionIfPossibleNoSync();

                    poolSync.wait(remainingMillis);

                    if (isClosed) {
                        break;
                    }

                    if (!readyConnections.isEmpty()) {
                        return readyConnections.pollLast().getValue();
                    }
                    
                    final long endTime = System.currentTimeMillis();
                    remainingMillis -= (endTime - startTime);
                    
                    if (remainingMillis <= 100) { // assume <= 100 means timeout expired
                        return null;
                    }
                    
                    startTime = endTime;
                } while (true);

                throw new IOException("The pool is closed");
            } finally {
                waitListSize--;
            }
        }
    }
    
    /**
     * Retrieves {@link Connection} from the pool, or returns <tt>null</tt> if
     * this pool doesn't have any ready {@link Connection} as the moment.
     * 
     * @return {@link Connection}, or <tt>null</tt> if the
     *         this pool doesn't have any ready {@link Connection} as the moment
     * @throws IOException thrown if this pool has been already closed
     */
    public Connection poll() throws IOException {
        synchronized (poolSync) {
            checkNotClosed();
            
            if (!readyConnections.isEmpty()) {
                final Connection connection =
                        readyConnections.pollLast().getValue();

                return connection;
            }

            return null;
        }
    }
    
    /**
     * Returns the {@link Connection} to the pool.
     * 
     * The {@link Connection} will be returned to the pool only in case it
     * was created by this pool, or it was attached to it using {@link #attach(org.glassfish.grizzly.Connection)}
     * method, otherwise this method call will not have any effect.
     * 
     * If the {@link Connection} was returned - it is illegal to use it until
     * it is retrieved from the pool again.
     * 
     * @param connection the {@link Connection} to return
     */
    public void release(final Connection connection) {
        synchronized (poolSync) {
            final Link<Connection> connectionLink = connectionsMap.get(connection);
            
            if (connectionLink == null || connectionLink.isAttached()) {
                return;
            }

            if (isClosed) {
                connection.closeSilently();
                return;
            }
            
            if (readyConnections.size() < maxPoolSize) {
                readyConnections.offer(connectionLink);
            }
        }
    }

    public boolean attach(final Connection connection) throws IOException {
        synchronized (poolSync) {
            checkNotClosed();
            if (connectionsMap.containsKey(connection)) {
                return true;
            }
            
            if (checkBeforeOpeningConnection()) {
                connectionCompletionHandler.completed(connection);
                return true;
            } else {
                return false;
            }
        }
    }
    
    public void detach(final Connection connection) throws IOException {
        synchronized (poolSync) {
            checkNotClosed();
            
            final Link<Connection> link = connectionsMap.remove(connection);
            if (link != null) {
                connection.removeCloseListener(closeListener);
                
                readyConnections.remove(link);
                poolSize--;

                onCloseConnection(connection);
            }            
        }
    }
    
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
                    final Connection c = readyConnections.pollLast().getValue();
                    c.closeSilently();
                }
            } finally {
                poolSync.notifyAll();
            }
        }
    }
    
    protected boolean checkBeforeOpeningConnection() {
        if (!isMaxCapacityReached()) {
            pendingConnections++;
            return true;
        }
        
        return false;
    }
    
    protected void onOpenConnection(final Connection connection) {
    }
    
    protected void onFailedConnection() {
    }

    protected void onCloseConnection(final Connection connection) {
        // If someone is waiting for a connection
        // try to create a new one
        if (waitListSize > 0) {
            createConnectionIfPossibleNoSync();
        }
    }

    boolean cleanupIdleConnections(final KeepAliveCleanerTask cleanerTask) {
        synchronized (poolSync) {
            if (isClosed) {
                return true;
            }

            if (!readyConnections.isEmpty() && poolSize > corePoolSize) {
                final long now = System.currentTimeMillis();

                try {
                    do {
                        final Link<Connection> link = readyConnections.getFirstLink();

                        if ((now - link.getAttachmentTimeStamp()) >= keepAliveTimeoutMillis) {
                            final Connection connection = link.getValue();
                            // CloseListener will update the counters
                            connection.closeSilently();
                        } else { // the rest of links are ok
                            break;
                        }
                    } while (!readyConnections.isEmpty());
                } catch (Exception ignore) {
                }
            }
        }

        cleanerTask.timeoutMillis = System.currentTimeMillis() + keepAliveCheckIntervalMillis;
        return false;
    }

    protected boolean createConnectionIfPossible() {
        synchronized (poolSync) {
            return createConnectionIfPossibleNoSync();
        }
    }
    
    private boolean createConnectionIfPossibleNoSync() {
        if (checkBeforeOpeningConnection()) {
            connectorHandler.connect(endpointAddress,
                    connectionCompletionHandler);
            
            return true;
        }
        
        return false;
    }
    
    private void checkNotClosed() throws IOException {
        if (isClosed) {
            throw new IOException("The pool is closed");
        }
    }

    private final class ConnectCompletionHandler
            extends EmptyCompletionHandler<Connection> {
        
        @Override
        @SuppressWarnings("unchecked")
        public void failed(Throwable throwable) {
            synchronized (poolSync) {
                pendingConnections--;
                
                onFailedConnection();
                reconnectQueue.add(new ReconnectTask(SingleEndpointPool.this),
                        reconnectDelayMillis, TimeUnit.MILLISECONDS);
            }
        }

        @Override
        public void completed(final Connection connection) {
            synchronized (poolSync) {
                if (!isClosed) {
                    final Link<Connection> link = new Link<Connection>(connection);
                    connectionsMap.put(connection, link);
                    readyConnections.offer(link);
                    
                    poolSize++;
                    pendingConnections--;

                    onOpenConnection(connection);

                    connection.addCloseListener(closeListener);
                    
                    if (waitListSize > 0) {
                        poolSync.notify();
                    }
                } else {
                    connection.closeSilently();
                }
            }
        }
    }
    
    private final class PoolConnectionCloseListener
            implements CloseListener<Connection, CloseType> {

        @Override
        public void onClosed(final Connection connection, final CloseType type)
                throws IOException {
            synchronized (poolSync) {
                final Link<Connection> link = connectionsMap.remove(connection);
                if (link != null) {
                    readyConnections.remove(link);
                    poolSize--;
                    
                    onCloseConnection(connection);
                }
            }
        }
    }
    
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
}
