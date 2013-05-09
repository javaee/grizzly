/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2013 Oracle and/or its affiliates. All rights reserved.
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
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.CloseListener;
import org.glassfish.grizzly.CloseType;
import org.glassfish.grizzly.Closeable;
import org.glassfish.grizzly.CompletionHandler;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.ConnectionProbe;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.GrizzlyFuture;
import org.glassfish.grizzly.IOEvent;
import org.glassfish.grizzly.Processor;
import org.glassfish.grizzly.ReadResult;
import org.glassfish.grizzly.Transport;
import org.glassfish.grizzly.WriteHandler;
import org.glassfish.grizzly.WriteResult;
import org.glassfish.grizzly.asyncqueue.AsyncWriteQueueRecord;
import org.glassfish.grizzly.asyncqueue.LifeCycleHandler;
import org.glassfish.grizzly.asyncqueue.TaskQueue;
import org.glassfish.grizzly.attributes.AttributeHolder;
import org.glassfish.grizzly.attributes.IndexedAttributeHolder;
import org.glassfish.grizzly.filterchain.FilterChain;
import org.glassfish.grizzly.impl.FutureImpl;
import org.glassfish.grizzly.monitoring.MonitoringConfig;
import org.glassfish.grizzly.monitoring.DefaultMonitoringConfig;
import org.glassfish.grizzly.utils.CompletionHandlerAdapter;
import org.glassfish.grizzly.utils.Futures;

/**
 * Common {@link Connection} implementation for Java NIO <tt>Connection</tt>s.
 *
 * @author Alexey Stashok
 */
public abstract class NIOConnection implements Connection<SocketAddress> {
    protected static final Object NOTIFICATION_INITIALIZED = Boolean.TRUE;
    protected static final Object NOTIFICATION_CLOSED_COMPLETE = Boolean.FALSE;
    
    private static final boolean WIN32 = "\\".equals(System.getProperty("file.separator"));
    private static final Logger logger = Grizzly.logger(NIOConnection.class);
    private static final short MAX_ZERO_READ_COUNT = 100;
    
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
    
    // Semaphor responsible for connect/close notification
    protected final AtomicReference<Object> connectCloseSemaphor =
            new AtomicReference<Object>();
    
    // closeTypeFlag, "null" value means the connection is open.
    protected final AtomicReference<CloseType> closeTypeFlag =
            new AtomicReference<CloseType>();
    
    protected volatile boolean isBlocking;
    protected short zeroByteReadCount;
    private final Queue<CloseListener> closeListeners =
            new ConcurrentLinkedQueue<CloseListener>();
    
    /**
     * Connection probes
     */
    protected final DefaultMonitoringConfig<ConnectionProbe> monitoringConfig =
        new DefaultMonitoringConfig<ConnectionProbe>(ConnectionProbe.class);

    public NIOConnection(final NIOTransport transport) {
        this.transport = transport;
        asyncWriteQueue = TaskQueue.createTaskQueue();
        
        attributes = new IndexedAttributeHolder(transport.getAttributeBuilder());
    }

    @Override
    public void configureBlocking(boolean isBlocking) {
        this.isBlocking = isBlocking;
    }

    @Override
    public boolean isBlocking() {
        return isBlocking;
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
    public void setMaxAsyncWriteQueueSize(int maxAsyncWriteQueueSize) {
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
    public long getBlockingReadTimeout(TimeUnit timeUnit) {
        if (readTimeoutMillis <= 0) {
            return readTimeoutMillis;
        }
        
        return timeUnit.convert(readTimeoutMillis, TimeUnit.MILLISECONDS);
    }

    @Override
    public void setBlockingReadTimeout(long timeout, TimeUnit timeUnit) {
        if (timeout < 0) {
            readTimeoutMillis = -1;
        } else {
            readTimeoutMillis = TimeUnit.MILLISECONDS.convert(timeout, timeUnit);
        }
    }

    @Override
    public long getBlockingWriteTimeout(TimeUnit timeUnit) {
        if (writeTimeoutMillis <= 0) {
            return writeTimeoutMillis;
        }
        
        return timeUnit.convert(writeTimeoutMillis, TimeUnit.MILLISECONDS);
    }

    @Override
    public void setBlockingWriteTimeout(long timeout, TimeUnit timeUnit) {
        if (timeout < 0) {
            writeTimeoutMillis = -1;
        } else {
            writeTimeoutMillis = TimeUnit.MILLISECONDS.convert(timeout, timeUnit);
        }
    }

    public SelectorRunner getSelectorRunner() {
        return selectorRunner;
    }

    protected void setSelectorRunner(SelectorRunner selectorRunner) {
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
        } catch (InterruptedException e) {
            throw new IOException("", e);
        } catch (ExecutionException e) {
            throw new IOException("", e.getCause());
        } catch (TimeoutException e) {
            throw new IOException("", e);
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
        return channel != null && channel.isOpen() && closeTypeFlag.get() == null;
    }

    @Override
    public GrizzlyFuture<Closeable> close() {
        
        final FutureImpl<Closeable> future = Futures.createSafeFuture();
        close(Futures.toCompletionHandler(future));
        
        return future;
    }

    @Override
    public void close(
            final CompletionHandler<Closeable> completionHandler) {
        close(completionHandler, true);
    }
        
    @Override
    @SuppressWarnings("unchecked")
    public final void closeSilently() {
        close(null);
    }
    
    protected void close(
            final CompletionHandler<Closeable> completionHandler,
            final boolean isClosedLocally) {
        
        if (closeTypeFlag.compareAndSet(null,
                isClosedLocally ? CloseType.LOCALLY : CloseType.REMOTELY)) {
            
            preClose();
            notifyCloseListeners();
            notifyProbesClose(this);

            transport.getSelectorHandler().execute(
                    selectorRunner, new SelectorHandler.Task() {

                @Override
                public boolean run() {
                    try {
                        close0();
                    } catch (IOException e) {
                        logger.log(Level.FINE, "Error during connection close", e);
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
                        close0();
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
    protected void close0() throws IOException {
        ((NIOTransport) transport).closeConnection(this);
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public void addCloseListener(final CloseListener closeListener) {
        CloseType closeType = closeTypeFlag.get();
        
        // check if connection is still open
        if (closeType == null) {
            // add close listener
            closeListeners.add(closeListener);
            // check the connection state again
            closeType = closeTypeFlag.get();
            if (closeType != null && closeListeners.remove(closeListener)) {
                // if connection was closed during the method call - notify the listener
                try {
                    closeListener.onClosed(this, closeType);
                } catch (IOException ignored) {
                }
            }
        } else { // if connection is closed - notify the listener
            try {
                closeListener.onClosed(this, closeType);
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
    public void notifyConnectionError(Throwable error) {
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
    protected static void notifyProbesBind(NIOConnection connection) {
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
    protected static void notifyProbesConnect(NIOConnection connection) {
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
    protected static void notifyProbesRead(NIOConnection connection,
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
    protected static void notifyProbesWrite(NIOConnection connection,
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
    protected static void notifyIOEventReady(NIOConnection connection,
        IOEvent ioEvent) {
        final ConnectionProbe[] probes =
            connection.monitoringConfig.getProbesUnsafe();
        if (probes != null) {
            for (ConnectionProbe probe : probes) {
                probe.onIOEventReadyEvent(connection, ioEvent);
            }
        }
    }

    /**
     * Notify registered {@link ConnectionProbe}s about the IO Event enabled event.
     *
     * @param connection the <tt>Connection</tt> event occurred on.
     * @param serviceEvent the {@link ServiceEvent}.
     */
//    protected static void notifyServiceEventEnabled(NIOConnection connection,
//        ServiceEvent serviceEvent) {
//        final ConnectionProbe[] probes =
//            connection.monitoringConfig.getProbesUnsafe();
//        if (probes != null) {
//            for (ConnectionProbe probe : probes) {
//                probe.onServiceEventEnableEvent(connection, serviceEvent);
//            }
//        }
//    }

    /**
     * Notify registered {@link ConnectionProbe}s about the IO Event disabled event.
     *
     * @param connection the <tt>Connection</tt> event occurred on.
     * @param serviceEvent the {@link ServiceEvent}.
     */
//    protected static void notifyServiceEventDisabled(NIOConnection connection,
//        ServiceEvent serviceEvent) {
//        final ConnectionProbe[] probes =
//            connection.monitoringConfig.getProbesUnsafe();
//        if (probes != null) {
//            for (ConnectionProbe probe : probes) {
//                probe.onServiceEventDisableEvent(connection, serviceEvent);
//            }
//        }
//    }

    /**
     * Notify registered {@link ConnectionProbe}s about the close event.
     *
     * @param connection the <tt>Connection</tt> event occurred on.
     */
    protected static void notifyProbesClose(NIOConnection connection) {
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
    protected static void notifyProbesError(NIOConnection connection,
        Throwable error) {
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
    private void notifyCloseListeners() {
        final CloseType closeType = closeTypeFlag.get();
        
        CloseListener closeListener;
        while ((closeListener = closeListeners.poll()) != null) {
            try {
                closeListener.onClosed(this, closeType);
            } catch (IOException ignored) {
            }
        }
    }

    protected void preClose() {
        // Check if connection init event (like CONNECT or ACCEPT) has been sent
        if (connectCloseSemaphor.getAndSet(NOTIFICATION_CLOSED_COMPLETE) ==
                NOTIFICATION_INITIALIZED) {
            transport.fireEvent(IOEvent.CLOSED, this, null);
        }
    }

//    @Override
//    public void simulateKeyInterest(final ServiceEvent serviceEvent) throws IOException {
//        final SelectorHandler selectorHandler = transport.getSelectorHandler();
//        switch (serviceEvent) {
//            case WRITE:
//                selectorHandler.enque(selectorRunner, writeSimulatorRunnable, null);
//                break;
//            case READ:
//                selectorHandler.enque(selectorRunner, readSimulatorRunnable, null);
//                break;
//            default:
//                throw new IllegalArgumentException("We support only READ and WRITE events. Got " + serviceEvent);
//        }
//    }
//    
    public final void registerKeyInterest(final int interest) throws IOException {
        if (interest == 0) {
            return;
        }
        
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
