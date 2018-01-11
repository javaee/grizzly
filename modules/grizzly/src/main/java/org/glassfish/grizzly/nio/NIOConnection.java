/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2017 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.nio;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.CloseListener;
import org.glassfish.grizzly.CloseReason;
import org.glassfish.grizzly.Closeable;
import org.glassfish.grizzly.CompletionHandler;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.ConnectionProbe;
import org.glassfish.grizzly.EmptyCompletionHandler;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.GrizzlyFuture;
import org.glassfish.grizzly.IOEvent;
import org.glassfish.grizzly.Processor;
import org.glassfish.grizzly.ReadResult;
import org.glassfish.grizzly.Transport;
import org.glassfish.grizzly.WritableMessage;
import org.glassfish.grizzly.WriteHandler;
import org.glassfish.grizzly.WriteResult;
import org.glassfish.grizzly.asyncqueue.AsyncWriteQueueRecord;
import org.glassfish.grizzly.asyncqueue.LifeCycleHandler;
import org.glassfish.grizzly.asyncqueue.TaskQueue;
import org.glassfish.grizzly.attributes.AttributeHolder;
import org.glassfish.grizzly.filterchain.DefaultFilterChainState;
import org.glassfish.grizzly.filterchain.FilterChain;
import org.glassfish.grizzly.filterchain.FilterChainState;
import org.glassfish.grizzly.impl.FutureImpl;
import org.glassfish.grizzly.memory.Buffers;
import org.glassfish.grizzly.memory.MemoryManager;
import org.glassfish.grizzly.monitoring.MonitoringConfig;
import org.glassfish.grizzly.monitoring.DefaultMonitoringConfig;
import org.glassfish.grizzly.utils.CompletionHandlerAdapter;
import org.glassfish.grizzly.utils.Futures;

/**
 * Common {@link Connection} implementation for Java NIO <tt>Connection</tt>s.
 *
 *
 */
public abstract class NIOConnection implements Connection<SocketAddress> {
    protected static final Object NOTIFICATION_INITIALIZED = Boolean.TRUE;
    protected static final Object NOTIFICATION_CLOSED_COMPLETE = Boolean.FALSE;
    
    private static final boolean WIN32 = "\\".equals(System.getProperty("file.separator"));
    private static final Logger LOGGER = Grizzly.logger(NIOConnection.class);
    private static final short MAX_ZERO_READ_COUNT = 100;
    
    /**
     * Is initial OP_READ enabling required for the connection
     */
    private boolean isInitialReadRequired = true;
    
    protected final NIOTransport transport;
    protected volatile int maxAsyncWriteQueueSize;
    protected volatile long readTimeoutMillis = 30000;
    protected volatile long writeTimeoutMillis = 30000;
    protected volatile SelectableChannel channel;
    protected volatile SelectionKey selectionKey;
    protected volatile SelectorRunner selectorRunner;
    
    protected volatile FilterChain filterChain;
    protected final AttributeHolder attributes;
    
    protected final TaskQueue<AsyncWriteQueueRecord> asyncWriteQueue;
    
    // Semaphore responsible for connect/close notification
    protected final AtomicReference<Object> connectCloseSemaphore =
            new AtomicReference<>();
    
    // isCloseScheduled, "null" value means the connection hasn't been scheduled for
    // the graceful shutdown
    private final AtomicBoolean isCloseScheduled = new AtomicBoolean();
    
    // closeReasonAtomic, "null" value means the connection is open.
    protected final AtomicReference<CloseReason> closeReasonAtomic =
            new AtomicReference<>();

    private volatile GrizzlyFuture<CloseReason> closeFuture;

    protected volatile boolean isBlocking;
    protected short zeroByteReadCount;
    private final List<CloseListener> closeListeners =
            Collections.synchronizedList(new LinkedList<>());
    private final FilterChainState filterChainState =
            new DefaultFilterChainState();
    /**
     * Connection probes
     */
    protected final DefaultMonitoringConfig<ConnectionProbe> monitoringConfig =
            new DefaultMonitoringConfig<>(ConnectionProbe.class);

    public NIOConnection(final NIOTransport transport) {
        this.transport = transport;
        asyncWriteQueue = TaskQueue.createTaskQueue(
                new TaskQueue.MutableMaxQueueSize() {

                    @Override
                    public int getMaxQueueSize() {
                        return maxAsyncWriteQueueSize;
                    }
                });
        
        attributes = transport.getAttributeBuilder().createSafeAttributeHolder();
    }

    @Override
    public void configureBlocking(final boolean isBlocking) {
        this.isBlocking = isBlocking;
    }

    @Override
    public boolean isBlocking() {
        return isBlocking;
    }

    @Override
    public MemoryManager<?> getMemoryManager() {
        return transport.getMemoryManager();
    }

    @Override
    public Transport getTransport() {
        return transport;
    }

    /**
     * Get the default size of {@link Buffer}s, which will be allocated for
     * reading data from {@link NIOConnection}.
     *
     * @return the default size of {@link Buffer}s, which will be allocated for
     * reading data from {@link NIOConnection}.
     */
    public abstract int getReadBufferSize();

    /**
     * Set the default size of {@link Buffer}s, which will be allocated for
     * reading data from {@link NIOConnection}.
     *
     * @param readBufferSize the default size of {@link Buffer}s, which will
     * be allocated for reading data from {@link NIOConnection}.
     */
    public abstract void setReadBufferSize(final int readBufferSize);

    /**
     * Get the default size of {@link Buffer}s, which will be allocated for
     * writing data to {@link NIOConnection}.
     *
     * @return the default size of {@link Buffer}s, which will be allocated for
     * writing data to {@link NIOConnection}.
     */
    public abstract int getWriteBufferSize();

    /**
     * Set the default size of {@link Buffer}s, which will be allocated for
     * writing data to {@link NIOConnection}.
     *
     * @param writeBufferSize the default size of {@link Buffer}s, which will
     * be allocated for writing data to {@link NIOConnection}.
     */
    public abstract void setWriteBufferSize(final int writeBufferSize);

    /**
     * Get the max size (in bytes) of asynchronous write queue associated
     * with connection.
     * 
     * @return the max size (in bytes) of asynchronous write queue associated
     * with connection.
     * 
     * @since 2.2
     */
    public int getMaxAsyncWriteQueueSize() {
        return maxAsyncWriteQueueSize;
    }

    /**
     * Set the max size (in bytes) of asynchronous write queue associated
     * with connection.
     * 
     * @param maxAsyncWriteQueueSize the max size (in bytes) of asynchronous
     * write queue associated with connection.
     * 
     * @since 2.2
     */
    public void setMaxAsyncWriteQueueSize(final int maxAsyncWriteQueueSize) {
        this.maxAsyncWriteQueueSize = maxAsyncWriteQueueSize;
    }

    /**
     * Returns the number of in bytes pending for writing in the asynchronous
     * write queue associated with connection.
     * 
     * Pls. note that number of pending bytes may change dramatically
     * each millisecond, so this method might be used for monitoring purposes
     * only.
     * 
     * @since 2.3
     */
    public int getAsyncWriteQueueSize() {
        return getAsyncWriteQueue().size();
    }

    @Override
    public long getBlockingReadTimeout(final TimeUnit timeUnit) {
        if (readTimeoutMillis <= 0) {
            return readTimeoutMillis;
        }
        
        return timeUnit.convert(readTimeoutMillis, TimeUnit.MILLISECONDS);
    }

    @Override
    public void setBlockingReadTimeout(final long timeout, final TimeUnit timeUnit) {
        if (timeout < 0) {
            readTimeoutMillis = -1;
        } else {
            readTimeoutMillis = TimeUnit.MILLISECONDS.convert(timeout, timeUnit);
        }
    }

    @Override
    public long getBlockingWriteTimeout(final TimeUnit timeUnit) {
        if (writeTimeoutMillis <= 0) {
            return writeTimeoutMillis;
        }
        
        return timeUnit.convert(writeTimeoutMillis, TimeUnit.MILLISECONDS);
    }

    @Override
    public void setBlockingWriteTimeout(final long timeout, final TimeUnit timeUnit) {
        if (timeout < 0) {
            writeTimeoutMillis = -1;
        } else {
            writeTimeoutMillis = TimeUnit.MILLISECONDS.convert(timeout, timeUnit);
        }
    }

    public SelectorRunner getSelectorRunner() {
        return selectorRunner;
    }

    protected void setSelectorRunner(final SelectorRunner selectorRunner) {
        this.selectorRunner = selectorRunner;
    }

    public void attachToSelectorRunner(final SelectorRunner selectorRunner)
        throws IOException {
        detachSelectorRunner();
        final SelectorHandler selectorHandler = transport.getSelectorHandler();
        
        final FutureImpl<RegisterChannelResult> future =
                Futures.createSafeFuture();
        
        selectorHandler.registerChannelAsync(
            selectorRunner, channel, 0, this, Futures.toCompletionHandler(future));
        try {
            final RegisterChannelResult result =
                future.get(readTimeoutMillis, TimeUnit.MILLISECONDS);
            this.selectorRunner = selectorRunner;
            this.selectionKey = result.getSelectionKey();
        } catch (InterruptedException | TimeoutException e) {
            throw new IOException("", e);
        } catch (ExecutionException e) {
            throw new IOException("", e.getCause());
        }


    }

    public void detachSelectorRunner() throws IOException {
        final SelectorRunner selectorRunnerLocal = this.selectorRunner;
        this.selectionKey = null;
        this.selectorRunner = null;
        if (selectorRunnerLocal != null) {
            transport.getSelectorHandler().deregisterChannel(selectorRunnerLocal,
                channel);
        }
    }

    public SelectableChannel getChannel() {
        return channel;
    }

    protected void setChannel(SelectableChannel channel) {
        this.channel = channel;
    }

    public SelectionKey getSelectionKey() {
        return selectionKey;
    }

    protected void setSelectionKey(SelectionKey selectionKey) {
        this.selectionKey = selectionKey;
        setChannel(selectionKey.channel());
    }

    @Override
    public FilterChain getFilterChain() {
        final FilterChain localFilterChain = filterChain;
        return localFilterChain != null ? localFilterChain :
                transport.getFilterChain();
    }

    @Override
    public void setFilterChain(final FilterChain preferableFilterChain) {
        this.filterChain = preferableFilterChain;
    }

    @Override
    public FilterChainState getFilterChainState() {
        return filterChainState;
    }

    @Override
    public void executeInEventThread(final IOEvent event, final Runnable runnable) {
        final Executor threadPool = transport.getIOStrategy()
                .getThreadPoolFor(this, event);
        if (threadPool == null) {
            transport.getSelectorHandler().enque(selectorRunner,
                    new SelectorHandler.Task() {

                        @Override
                        public boolean run() throws Exception {
                            runnable.run();
                            return true;
                        }
                    }, null);
        } else {
            threadPool.execute(runnable);
        }
    }
    
    protected TaskQueue<AsyncWriteQueueRecord> getAsyncWriteQueue() {
        return asyncWriteQueue;
    }

    @Override
    public AttributeHolder getAttributes() {
        return attributes;
    }

    @Override
    public <M> GrizzlyFuture<ReadResult<M, SocketAddress>> read() {
        final FutureImpl<ReadResult<M, SocketAddress>> future =
                Futures.createSafeFuture();
        read(Futures.toCompletionHandler(future));
        
        return future;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <M> void read(
            final CompletionHandler<ReadResult<M, SocketAddress>> completionHandler) {
        final Processor localFilterChain = getFilterChain();
        localFilterChain.read(this, completionHandler);
    }

    @Override
    public <M> GrizzlyFuture<WriteResult<M, SocketAddress>> write(final M message) {
        final FutureImpl<WriteResult<M, SocketAddress>> future =
                Futures.createSafeFuture();
        write(null, message, Futures.toCompletionHandler(future), null);
        
        return future;
        
    }

    @Override
    public <M> void write(final M message,
            final CompletionHandler<WriteResult<M, SocketAddress>> completionHandler) {
        
        write(null, message, completionHandler, null);
    }

    @Override
    public <M> void write(final M message,
            final CompletionHandler<WriteResult<M, SocketAddress>> completionHandler,
            final LifeCycleHandler lifeCycleHandler) {
        write(null, message, completionHandler, lifeCycleHandler);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <M> void write(
            final SocketAddress dstAddress, final M message,
            final CompletionHandler<WriteResult<M, SocketAddress>> completionHandler,
            final LifeCycleHandler lifeCycleHandler) {
        final Processor localFilterChain = getFilterChain();
        localFilterChain.write(this, dstAddress, message,
                completionHandler, lifeCycleHandler);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean canWrite() {
        return transport.getWriter(this).canWrite(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void notifyWritePossible(final WriteHandler writeHandler) {
        transport.getWriter(this).notifyWritePossible(this, writeHandler);
    }
    
    @Override
    public boolean isOpen() {
        return channel != null && channel.isOpen() && closeReasonAtomic.get() == null;
    }
    
    @Override
    public void assertOpen() throws IOException {
        final CloseReason reason = getCloseReason();
        if (reason != null) {
            throw new IOException("Connection is closed", reason.getCause());
        }
    }

    public boolean isClosed() {
        return !isOpen();
    }

    @Override
    public CloseReason getCloseReason() {
        final CloseReason closeReason = closeReasonAtomic.get();
        if (closeReason != null) {
            return closeReason;
        } else if (channel == null || !channel.isOpen()) {
            return CloseReason.LOCALLY_CLOSED_REASON;
        }
        
        return null;
    }

    @Override
    public void terminateSilently() {
        terminate0(null, CloseReason.LOCALLY_CLOSED_REASON);
    }

    @Override
    public GrizzlyFuture<Closeable> terminate() {
        final FutureImpl<Closeable> future = Futures.createSafeFuture();
        terminate0(Futures.toCompletionHandler(future),
                CloseReason.LOCALLY_CLOSED_REASON);
        
        return future;
    }

    @Override
    public void terminateWithReason(final CloseReason closeReason) {
        terminate0(null, closeReason);
    }

    @Override
    public GrizzlyFuture<Closeable> close() {
        
        final FutureImpl<Closeable> future = Futures.createSafeFuture();
        terminate0(Futures.toCompletionHandler(future),
                CloseReason.LOCALLY_CLOSED_REASON);
        
        return future;
    }
       
    @Override
    @SuppressWarnings("unchecked")
    public final void closeSilently() {
        closeGracefully0(null, CloseReason.LOCALLY_CLOSED_REASON);
    }

    @Override
    public void closeWithReason(final CloseReason closeReason) {
        closeGracefully0(null, closeReason);
    }

    @Override
    public GrizzlyFuture<CloseReason> closeFuture() {
        if (closeFuture == null) {
            synchronized (this) {
                if (closeFuture == null) {
                    final CloseReason closeReason = closeReasonAtomic.get();
                    if (closeReason == null) {
                        final FutureImpl<CloseReason> f
                                = Futures.createSafeFuture();
                        addCloseListener(new CloseListener() {

                            @Override
                            public void onClosed(final Closeable closable,
                                                 final CloseReason reason)
                            throws IOException {
                                assert reason != null;
                                f.result(reason);
                            }

                        });
                        closeFuture = f;
                    } else {
                        closeFuture = Futures.createReadyFuture(closeReason);
                    }
                }
            }
        }

        return closeFuture;
    }


    @SuppressWarnings("unchecked")
    protected void closeGracefully0(
            final CompletionHandler<Closeable> completionHandler,
            CloseReason closeReason) {
        if (isCloseScheduled.compareAndSet(false, true)) {
            
            if (LOGGER.isLoggable(Level.FINEST)) {
                // replace close reason: clone the original value and add stacktrace
                closeReason = new CloseReason(closeReason.getType(),
                        new IOException("Connection is closed at",
                                closeReason.getCause()));
            }
            
            final CloseReason finalReason = closeReason;
            
            transport.getWriter(this).write(this, Buffers.EMPTY_BUFFER,
                    new EmptyCompletionHandler<WriteResult<WritableMessage, SocketAddress>>() {

                @Override
                public void completed(final WriteResult<WritableMessage, SocketAddress> result) {
                    terminate0(completionHandler, finalReason);
                }

                @Override
                public void failed(final Throwable throwable) {
                    terminate0(completionHandler, finalReason);
                }
                
            });
        } else {
            if (completionHandler != null) {
                addCloseListener(new CloseListener() {

                    @Override
                    public void onClosed(Closeable closable, CloseReason reason) throws IOException {
                        completionHandler.completed(NIOConnection.this);
                    }
                });
            }
        }
    }
    

    protected void terminate0(final CompletionHandler<Closeable> completionHandler,
            final CloseReason closeReason) {
        
        isCloseScheduled.set(true);
        if (closeReasonAtomic.compareAndSet(null, closeReason)) {
            
            if (LOGGER.isLoggable(Level.FINEST)) {
                // replace close reason: clone the original value and add stacktrace
                closeReasonAtomic.set(new CloseReason(closeReason.getType(),
                        new IOException("Connection is closed at",
                                closeReason.getCause())));
            }
            
            preClose();
            notifyCloseListeners(closeReason);
            notifyProbesClose(this);

            transport.getSelectorHandler().execute(
                    selectorRunner, new SelectorHandler.Task() {

                @Override
                public boolean run() {
                    try {
                        doClose();
                    } catch (IOException e) {
                        LOGGER.log(Level.FINE, "Error during connection close", e);
                    }

                    return true;
                }
            }, new CompletionHandlerAdapter<Closeable, SelectorHandler.Task>(
                    null, completionHandler) {

                @Override
                protected Connection adapt(final SelectorHandler.Task result) {
                    return NIOConnection.this;
                }

                @Override
                public void failed(final Throwable throwable) {
                    try {
                        doClose();
                    } catch (Exception ignored) {
                    }

                    completed(null);
                }
            });
        } else {
            Futures.notifyResult(null, completionHandler, this);
        }
    }

    /**
     * Do the actual connection close.
     */
    protected void doClose() throws IOException {
        transport.closeConnection(this);
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public void addCloseListener(final CloseListener closeListener) {
        CloseReason reason = closeReasonAtomic.get();
        
        // check if connection is still open
        if (reason == null) {
            // add close listener
            closeListeners.add(closeListener);
            // check the connection state again
            reason = closeReasonAtomic.get();
            if (reason != null && closeListeners.remove(closeListener)) {
                // if connection was closed during the method call - notify the listener
                try {
                    closeListener.onClosed(this, reason);
                } catch (IOException ignored) {
                }
            }
        } else { // if connection is closed - notify the listener
            try {
                closeListener.onClosed(this, reason);
            } catch (IOException ignored) {
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean removeCloseListener(final CloseListener closeListener) {
        return closeListeners.remove(closeListener);
    }

    /**
     * Method gets invoked, when error occur during the <tt>Connection</tt> lifecycle.
     *
     * @param error {@link Throwable}.
     */
    @SuppressWarnings("unused")
    public void notifyConnectionError(final Throwable error) {
        notifyProbesError(this, error);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final MonitoringConfig<ConnectionProbe> getMonitoringConfig() {
        return monitoringConfig;
    }

    /**
     * Notify registered {@link ConnectionProbe}s about the bind event.
     *
     * @param connection the <tt>Connection</tt> event occurred on.
     */
    protected static void notifyProbesBind(final NIOConnection connection) {
        final ConnectionProbe[] probes = connection.monitoringConfig.getProbesUnsafe();
        if (probes != null) {
            for (ConnectionProbe probe : probes) {
                probe.onBindEvent(connection);
            }
        }
    }

    /**
     * Notify registered {@link ConnectionProbe}s about the accept event.
     *
     * @param serverConnection the server <tt>Connection</tt>, which accepted the client connection.
     * @param clientConnection the client <tt>Connection</tt>.
     */
    protected static void notifyProbesAccept(final NIOConnection serverConnection,
            final NIOConnection clientConnection) {
        final ConnectionProbe[] probes =
            serverConnection.monitoringConfig.getProbesUnsafe();
        if (probes != null) {
            for (ConnectionProbe probe : probes) {
                probe.onAcceptEvent(serverConnection, clientConnection);
            }
        }
    }

    /**
     * Notify registered {@link ConnectionProbe}s about the connect event.
     *
     * @param connection the <tt>Connection</tt> event occurred on.
     */
    protected static void notifyProbesConnect(final NIOConnection connection) {
        final ConnectionProbe[] probes =
            connection.monitoringConfig.getProbesUnsafe();
        if (probes != null) {
            for (ConnectionProbe probe : probes) {
                probe.onConnectEvent(connection);
            }
        }
    }

    /**
     * Notify registered {@link ConnectionProbe}s about the read event.
     */
    protected static void notifyProbesRead(final NIOConnection connection,
        Buffer data, int size) {
        final ConnectionProbe[] probes =
            connection.monitoringConfig.getProbesUnsafe();
        if (probes != null) {
            for (ConnectionProbe probe : probes) {
                probe.onReadEvent(connection, data, size);
            }
        }
    }

    /**
     * Notify registered {@link ConnectionProbe}s about the write event.
     */
    protected static void notifyProbesWrite(final NIOConnection connection,
        Buffer data, long size) {
        final ConnectionProbe[] probes =
            connection.monitoringConfig.getProbesUnsafe();
        if (probes != null) {
            for (ConnectionProbe probe : probes) {
                probe.onWriteEvent(connection, data, size);
            }
        }
    }

    /**
     * Notify registered {@link ConnectionProbe}s about the IO Event ready event.
     *
     * @param connection the <tt>Connection</tt> event occurred on.
     * @param ioEvent the {@link IOEvent}.
     */
    @SuppressWarnings("unused")
    protected static void notifyIOEventReady(final NIOConnection connection,
                                             final IOEvent ioEvent) {
        final ConnectionProbe[] probes =
            connection.monitoringConfig.getProbesUnsafe();
        if (probes != null) {
            for (ConnectionProbe probe : probes) {
                probe.onIOEventReadyEvent(connection, ioEvent);
            }
        }
    }

    /**
     * Notify registered {@link ConnectionProbe}s about the close event.
     *
     * @param connection the <tt>Connection</tt> event occurred on.
     */
    protected static void notifyProbesClose(final NIOConnection connection) {
        final ConnectionProbe[] probes =
            connection.monitoringConfig.getProbesUnsafe();
        if (probes != null) {
            for (ConnectionProbe probe : probes) {
                probe.onCloseEvent(connection);
            }
        }
    }

    /**
     * Notify registered {@link ConnectionProbe}s about the error.
     *
     * @param connection the <tt>Connection</tt> event occurred on.
     */
    protected static void notifyProbesError(final NIOConnection connection,
        final Throwable error) {
        final ConnectionProbe[] probes =
            connection.monitoringConfig.getProbesUnsafe();
        if (probes != null) {
            for (ConnectionProbe probe : probes) {
                probe.onErrorEvent(connection, error);
            }
        }
    }

    /**
     * Notify all close listeners
     */
    private void notifyCloseListeners(final CloseReason closeReason) {
        if (!closeListeners.isEmpty()) {
            final List<CloseListener> copiedCloseListeners;
            synchronized (closeListeners) {
                copiedCloseListeners = new ArrayList<>(closeListeners); //Don't call them when the list is locked.
                closeListeners.clear();
            }
            for (CloseListener closeListener : copiedCloseListeners) {
                try {
                    closeListener.onClosed(this, closeReason);
                } catch (Exception ignored) {
                }
            }
        }
    }

    protected void preClose() {
        // Check if connection init event (like CONNECT or ACCEPT) has been sent
        if (connectCloseSemaphore.getAndSet(NOTIFICATION_CLOSED_COMPLETE) ==
                NOTIFICATION_INITIALIZED) {
            transport.fireEvent(IOEvent.CLOSED, this, null);
        }
    }

    /**
     * Enables OP_READ if it has never been enabled before.
     * 
     * @throws IOException 
     */
    protected void enableInitialOpRead() throws IOException {
        if (isInitialReadRequired) {
            // isInitialReadRequired will be disabled inside
            registerKeyInterest(SelectionKey.OP_READ);
        }
    }
    
    public final void registerKeyInterest(final int interest) throws IOException {
        final boolean isOpRead = (interest == SelectionKey.OP_READ);
        if (interest == 0 ||
                // don't register OP_READ for a connection scheduled to be closed
                (isOpRead && isCloseScheduled.get()) ||
                // don't register any OP for a closed connection
                closeReasonAtomic.get() != null) {
            return;
        }
        
        isInitialReadRequired = isInitialReadRequired && !isOpRead;
        
//        notifyServiceEventEnabled(this, serviceEvent);
        final SelectorHandler selectorHandler = transport.getSelectorHandler();
        selectorHandler.registerKeyInterest(selectorRunner, selectionKey,
            interest);
    }
    
    public final void deregisterKeyInterest(final int interest) throws IOException {
        if (interest == 0) {
            return;
        }
        
//        notifyServiceEventEnabled(this, serviceEvent);
        final SelectorHandler selectorHandler = transport.getSelectorHandler();
        selectorHandler.deregisterKeyInterest(selectorRunner, selectionKey,
            interest);
    }

    protected void enqueOpWriteReady() {
        transport.getSelectorHandler().enque(selectorRunner,
                opWriteReadyTask, null);
    }
    
    private final SelectorHandler.Task opWriteReadyTask =
            new SelectorHandler.Task() {

                @Override
                public boolean run() throws IOException {
                    return transport.processOpWrite(NIOConnection.this, false);
                }
            };

    protected final void checkEmptyRead(final int size) {
        if (WIN32) {
            if (size == 0) {
                final short count = ++zeroByteReadCount;
                if (count >= MAX_ZERO_READ_COUNT) {
                    closeSilently();
                }
            } else {
                zeroByteReadCount = 0;
            }
        }
    }

    final void onSelectionKeyUpdated(final SelectionKey newSelectionKey) {
        this.selectionKey = newSelectionKey;
    }
}
