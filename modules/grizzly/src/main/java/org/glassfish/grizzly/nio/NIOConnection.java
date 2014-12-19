/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2014 Oracle and/or its affiliates. All rights reserved.
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
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.grizzly.*;
import org.glassfish.grizzly.asyncqueue.AsyncReadQueueRecord;
import org.glassfish.grizzly.asyncqueue.AsyncWriteQueueRecord;
import org.glassfish.grizzly.asyncqueue.TaskQueue;
import org.glassfish.grizzly.attributes.AttributeHolder;
import org.glassfish.grizzly.impl.FutureImpl;
import org.glassfish.grizzly.memory.Buffers;
import org.glassfish.grizzly.memory.MemoryManager;
import org.glassfish.grizzly.monitoring.MonitoringConfig;
import org.glassfish.grizzly.monitoring.DefaultMonitoringConfig;
import org.glassfish.grizzly.utils.CompletionHandlerAdapter;
import org.glassfish.grizzly.utils.DataStructures;
import org.glassfish.grizzly.utils.Futures;
import org.glassfish.grizzly.utils.NullaryFunction;

/**
 * Common {@link Connection} implementation for Java NIO <tt>Connection</tt>s.
 *
 * @author Alexey Stashok
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
    
    protected volatile Processor processor;
    protected volatile ProcessorSelector processorSelector;
    protected final AttributeHolder attributes;
    protected final TaskQueue<AsyncReadQueueRecord> asyncReadQueue;
    protected final TaskQueue<AsyncWriteQueueRecord> asyncWriteQueue;
    
    // Semaphor responsible for connect/close notification
    protected final AtomicReference<Object> connectCloseSemaphor =
            new AtomicReference<Object>();
    
    // closeTypeFlag, "null" value means the connection is open.
    private final AtomicBoolean isCloseScheduled = new AtomicBoolean();
    
    private final AtomicReference<CloseReason> closeReasonAtomic =
            new AtomicReference<CloseReason>();
    
    protected volatile boolean isBlocking;
    protected volatile boolean isStandalone;        
    protected short zeroByteReadCount;
    private final Queue<org.glassfish.grizzly.CloseListener> closeListeners =
            new ConcurrentLinkedQueue<org.glassfish.grizzly.CloseListener>();
    
    /**
     * Storage contains states of different Processors this Connection is associated with.
     */
    private final ProcessorStatesMap processorStateStorage =
            new ProcessorStatesMap();
        
    /**
     * Connection probes
     */
    protected final DefaultMonitoringConfig<ConnectionProbe> monitoringConfig =
        new DefaultMonitoringConfig<ConnectionProbe>(ConnectionProbe.class);

    public NIOConnection(final NIOTransport transport) {
        this.transport = transport;
        asyncReadQueue = TaskQueue.createTaskQueue(null);
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
    public void configureBlocking(boolean isBlocking) {
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
    public synchronized void configureStandalone(boolean isStandalone) {
        if (this.isStandalone != isStandalone) {
            this.isStandalone = isStandalone;
            if (isStandalone) {
                processor = StandaloneProcessor.INSTANCE;
                processorSelector = StandaloneProcessorSelector.INSTANCE;
            } else {
                processor = transport.getProcessor();
                processorSelector = transport.getProcessorSelector();
            }
        }
    }

    @Override
    public boolean isStandalone() {
        return isStandalone;
    }

    @Override
    public Transport getTransport() {
        return transport;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getMaxAsyncWriteQueueSize() {
        return maxAsyncWriteQueueSize;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setMaxAsyncWriteQueueSize(int maxAsyncWriteQueueSize) {
        this.maxAsyncWriteQueueSize = maxAsyncWriteQueueSize;
    }
    
    @Override
    public long getReadTimeout(TimeUnit timeUnit) {
        return timeUnit.convert(readTimeoutMillis, TimeUnit.MILLISECONDS);
    }

    @Override
    public void setReadTimeout(long timeout, TimeUnit timeUnit) {
        readTimeoutMillis = TimeUnit.MILLISECONDS.convert(timeout, timeUnit);
    }

    @Override
    public long getWriteTimeout(TimeUnit timeUnit) {
        return timeUnit.convert(writeTimeoutMillis, TimeUnit.MILLISECONDS);
    }

    @Override
    public void setWriteTimeout(long timeout, TimeUnit timeUnit) {
        writeTimeoutMillis = TimeUnit.MILLISECONDS.convert(timeout, timeUnit);
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
    public Processor obtainProcessor(IOEvent ioEvent) {
        if (processor == null && processorSelector == null) {
            return transport.obtainProcessor(ioEvent, this);
        }
        if (processor != null && processor.isInterested(ioEvent)) {
            return processor;
        } else if (processorSelector != null) {
            final Processor selectedProcessor =
                processorSelector.select(ioEvent, this);
            if (selectedProcessor != null) {
                return selectedProcessor;
            }
        }
        return null;
    }

    @Override
    public Processor getProcessor() {
        return processor;
    }

    @Override
    public void setProcessor(
        Processor preferableProcessor) {
        this.processor = preferableProcessor;
    }

    @Override
    public ProcessorSelector getProcessorSelector() {
        return processorSelector;
    }

    @Override
    public void setProcessorSelector(
            final ProcessorSelector preferableProcessorSelector) {
        this.processorSelector = preferableProcessorSelector;
    }

    @Override
    public <E> E obtainProcessorState(final Processor processor,
            final NullaryFunction<E> factory) {
        return processorStateStorage.getState(processor, factory);
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
    
    public TaskQueue<AsyncReadQueueRecord> getAsyncReadQueue() {
        return asyncReadQueue;
    }

    public TaskQueue<AsyncWriteQueueRecord> getAsyncWriteQueue() {
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
        final Processor obtainedProcessor = obtainProcessor(IOEvent.READ);
        obtainedProcessor.read(this, completionHandler);
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
    @Deprecated
    public <M> void write(final M message,
            CompletionHandler<WriteResult<M, SocketAddress>> completionHandler,
            org.glassfish.grizzly.asyncqueue.PushBackHandler pushbackHandler) {
        write(null, message, completionHandler, pushbackHandler);
    }

    @Override
    public <M> void write(final SocketAddress dstAddress, final M message,
            final CompletionHandler<WriteResult<M, SocketAddress>> completionHandler) {
        write(dstAddress, message, completionHandler, null);
    }

    
    @SuppressWarnings("unchecked")
    @Override
    @Deprecated
    public <M> void write(
            final SocketAddress dstAddress, final M message,
            final CompletionHandler<WriteResult<M, SocketAddress>> completionHandler,
            final org.glassfish.grizzly.asyncqueue.PushBackHandler pushbackHandler) {
        final Processor obtainedProcessor = obtainProcessor(IOEvent.WRITE);
        obtainedProcessor.write(this, dstAddress, message,
                completionHandler, pushbackHandler);
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
    public void terminateWithReason(final IOException reason) {
        terminate0(null, new CloseReason(
                org.glassfish.grizzly.CloseType.LOCALLY, reason));
    }

    @Override
    public GrizzlyFuture<Closeable> close() {
        
        final FutureImpl<Closeable> future = Futures.createSafeFuture();
        closeGracefully0(Futures.toCompletionHandler(future),
                CloseReason.LOCALLY_CLOSED_REASON);
        
        return future;
    }

    /**
     * {@inheritDoc}
     * @deprecated please use {@link #close()} with the following {@link
     *  GrizzlyFuture#addCompletionHandler(org.glassfish.grizzly.CompletionHandler)} call
     */
    @Override
    public void close(final CompletionHandler<Closeable> completionHandler) {
        closeGracefully0(completionHandler, CloseReason.LOCALLY_CLOSED_REASON);
    }

    @Override
    @SuppressWarnings("unchecked")
    public final void closeSilently() {
        closeGracefully0(null, CloseReason.LOCALLY_CLOSED_REASON);
    }
    
    @Override
    public void closeWithReason(final IOException reason) {
        closeGracefully0(null, new CloseReason(
                org.glassfish.grizzly.CloseType.LOCALLY, reason));
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
                    new EmptyCompletionHandler<WriteResult<Buffer, SocketAddress>>() {

                @Override
                public void completed(final WriteResult<Buffer, SocketAddress> result) {
                    terminate0(completionHandler, finalReason);
                }

                @Override
                public void failed(final Throwable throwable) {
                    terminate0(completionHandler, finalReason);
                }
                
            });
        } else {
            if (completionHandler != null) {
                addCloseListener(new org.glassfish.grizzly.CloseListener() {

                    @Override
                    public void onClosed(final Closeable closeable,
                            final ICloseType type) throws IOException {
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
        ((NIOTransport) transport).closeConnection(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addCloseListener(final org.glassfish.grizzly.CloseListener closeListener) {
        CloseReason reason = closeReasonAtomic.get();
        
        // check if connection is still open
        if (reason == null) {
            // add close listener
            closeListeners.add(closeListener);
            // check the connection state again
            reason = closeReasonAtomic.get();
            if (reason != null && closeListeners.remove(closeListener)) {
                // if connection was closed during the method call - notify the listener
                invokeCloseListener(closeListener, reason.getType());
            }
        } else { // if connection is closed - notify the listener
            invokeCloseListener(closeListener, reason.getType());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean removeCloseListener(final org.glassfish.grizzly.CloseListener closeListener) {
        return closeListeners.remove(closeListener);
    }

    @Override
    public void addCloseListener(CloseListener closeListener) {
        addCloseListener((org.glassfish.grizzly.CloseListener) closeListener);
    }

    @Override
    public boolean removeCloseListener(CloseListener closeListener) {
        return removeCloseListener((org.glassfish.grizzly.CloseListener) closeListener);
    }

    /**
     * {@inheritDoc}
     */
    @Override
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
     * @param ioEvent the {@link IOEvent}.
     */
    protected static void notifyIOEventEnabled(NIOConnection connection,
        IOEvent ioEvent) {
        final ConnectionProbe[] probes =
            connection.monitoringConfig.getProbesUnsafe();
        if (probes != null) {
            for (ConnectionProbe probe : probes) {
                probe.onIOEventEnableEvent(connection, ioEvent);
            }
        }
    }

    /**
     * Notify registered {@link ConnectionProbe}s about the IO Event disabled event.
     *
     * @param connection the <tt>Connection</tt> event occurred on.
     * @param ioEvent the {@link IOEvent}.
     */
    protected static void notifyIOEventDisabled(NIOConnection connection,
        IOEvent ioEvent) {
        final ConnectionProbe[] probes =
            connection.monitoringConfig.getProbesUnsafe();
        if (probes != null) {
            for (ConnectionProbe probe : probes) {
                probe.onIOEventDisableEvent(connection, ioEvent);
            }
        }
    }

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
    private void notifyCloseListeners(final CloseReason closeReason) {
        final org.glassfish.grizzly.CloseType closeType =
                closeReason.getType();
        
        org.glassfish.grizzly.CloseListener closeListener;
        while ((closeListener = closeListeners.poll()) != null) {
            invokeCloseListener(closeListener, closeType);
        }
    }

    protected void preClose() {
        // Check if connection init event (like CONNECT or ACCEPT) has been sent
        if (connectCloseSemaphor.getAndSet(NOTIFICATION_CLOSED_COMPLETE) ==
                NOTIFICATION_INITIALIZED) {
            transport.fireIOEvent(IOEvent.CLOSED, this, null);
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
            enableIOEvent(IOEvent.READ);
        }
    }
    
    @Override
    public void simulateIOEvent(final IOEvent ioEvent) throws IOException {
        if (!isOpen()) {
            // don't simulate IOEvent for closed connection
            return;
        }
        
        final SelectorHandler selectorHandler = transport.getSelectorHandler();
        switch (ioEvent) {
            case WRITE:
                selectorHandler.enque(selectorRunner, writeSimulatorRunnable, null);
                break;
            case READ:
                selectorHandler.enque(selectorRunner, readSimulatorRunnable, null);
                break;
            default:
                throw new IllegalArgumentException("We support only READ and WRITE events. Got " + ioEvent);
        }
    }
    
    @Override
    public final void enableIOEvent(final IOEvent ioEvent) throws IOException {
        final boolean isOpRead = (ioEvent == IOEvent.READ);
        final int interest = ioEvent.getSelectionKeyInterest();
        if (interest == 0 ||
                // don't register OP_READ for a connection scheduled to be closed
                (isOpRead && isCloseScheduled.get()) ||
                // don't register any OP for a closed connection
                closeReasonAtomic.get() != null) {
            return;
        }
        
        notifyIOEventEnabled(this, ioEvent);
        
        // if OP_READ was enabled at least once - isInitialReadRequired should be false
        isInitialReadRequired = isInitialReadRequired && !isOpRead;
        
        final SelectorHandler selectorHandler = transport.getSelectorHandler();
        selectorHandler.registerKeyInterest(selectorRunner, selectionKey,
            interest);
    }

    private final SelectorHandler.Task writeSimulatorRunnable =
            new SelectorHandler.Task() {

                @Override
                public boolean run() throws IOException {
                    return transport.getIOStrategy().executeIoEvent(
                            NIOConnection.this, IOEvent.WRITE, false);
                }
            };
    
    private final SelectorHandler.Task readSimulatorRunnable =
            new SelectorHandler.Task() {

                @Override
                public boolean run() throws IOException {
                    return transport.getIOStrategy().executeIoEvent(
                            NIOConnection.this, IOEvent.READ, false);
                }
            }; 
    
    @Override
    public final void disableIOEvent(final IOEvent ioEvent) throws IOException {
        final int interest = ioEvent.getSelectionKeyInterest();
        if (interest == 0) {
            return;
        }

        notifyIOEventDisabled(this, ioEvent);

        final SelectorHandler selectorHandler = transport.getSelectorHandler();
        selectorHandler.deregisterKeyInterest(selectorRunner, selectionKey, interest);
    }

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

    private void invokeCloseListener(org.glassfish.grizzly.CloseListener closeListener, org.glassfish.grizzly.CloseType closeType) {
        try {
            if (closeListener instanceof CloseListener) {
                CloseType closeLocal;
                if (closeType == org.glassfish.grizzly.CloseType.LOCALLY) {
                    closeLocal = CloseType.LOCALLY;
                } else {
                    closeLocal = CloseType.REMOTELY;
                }
                ((CloseListener) closeListener).onClosed(this, closeLocal);
            } else {
                closeListener.onClosed(this, closeType);
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * Set the monitoringProbes array directly.
     * @param monitoringProbes
     */
    void setMonitoringProbes(final ConnectionProbe[] monitoringProbes) {
        this.monitoringConfig.addProbes(monitoringProbes);
    }

    /**
     * Map, which contains {@link Processor}s and their states related to this {@link Connection}.
     */
    private final static class ProcessorStatesMap {
        private volatile int volatileFlag;
        private ProcessorState singleProcessorState;
        private ConcurrentMap<Processor, Object> processorStatesMap;
        
        @SuppressWarnings("unchecked")
        public <E> E getState(final Processor processor,
                final NullaryFunction<E> stateFactory) {

            final int c = volatileFlag;
            if (c == 0) {
                // Connection doesn't have any processor state associated
                return (E) getStateSync(processor, stateFactory);
            } else {
                final ProcessorState localProcessorState = singleProcessorState;
                if (localProcessorState != null) {
                    if (localProcessorState.processor.equals(processor)) {
                        // the normal code path (Connection has only one processor associated)
                        return (E) localProcessorState.state;
                    }
                } else {
                    return (E) getStateSync(processor, stateFactory);
                }

                // Code should be invoked in PU cases only, so move it under
                // static class to let Hotspot initialize it lazily only when
                // needed
                return (E) StaticMapAccessor.getFromMap(this, processor, stateFactory);
            }
        }
        
        private synchronized <E> Object getStateSync(final Processor processor,
                final NullaryFunction<E> stateFactory) {
            if (volatileFlag == 0) {
                final E state = stateFactory.evaluate();
                singleProcessorState = new ProcessorState(processor, state);
                volatileFlag++;
                
                return state;
            } else if (volatileFlag == 1) {
                if (singleProcessorState.processor.equals(processor)) {
                    return singleProcessorState.state;
                }
            }

            // Code should be invoked in PU cases only, so move it under
            // static class to let Hotspot initialize it lazily only when
            // needed
            return StaticMapAccessor.getFromMapSync(this, processor, stateFactory);
        }
        
        private static final class ProcessorState {
            private final Processor processor;
            private final Object state;

            public ProcessorState(Processor processor, Object state) {
                this.processor = processor;
                this.state = state;
            }                        
        }
        
        private static final class StaticMapAccessor {
            
            static {
                Grizzly.logger(StaticMapAccessor.class).fine("Map is going to "
                        + "be used as Connection<->ProcessorState storage");
            }

            private static <E> Object getFromMap(
                    final ProcessorStatesMap storage,
                    final Processor processor,
                    final NullaryFunction<E> stateFactory) {
                
                final Map<Processor, Object> localStateMap = storage.processorStatesMap;
                if (localStateMap != null) {
                    final Object state = storage.processorStatesMap.get(processor);
                    if (state != null) {
                        return state;
                    }
                }

                return storage.getStateSync(processor, stateFactory);
            }
            
            private static <E> Object getFromMapSync(
                    final ProcessorStatesMap storage,
                    final Processor processor,
                    final NullaryFunction<E> stateFactory) {
                
                ConcurrentMap<Processor, Object> localStatesMap =
                        storage.processorStatesMap;


                if (localStatesMap != null) {
                    if (localStatesMap.containsKey(processor)) {
                        return localStatesMap.get(processor);
                    }

                    final Object state = stateFactory.evaluate();
                    localStatesMap.put(processor, state);
                    return state;
                }

                localStatesMap = DataStructures.<Processor, Object>getConcurrentMap(4);
                final Object state = stateFactory.evaluate();
                localStatesMap.put(processor, state);
                storage.processorStatesMap = localStatesMap;
                storage.volatileFlag++;
                return state;
            }
        }
    }
}
