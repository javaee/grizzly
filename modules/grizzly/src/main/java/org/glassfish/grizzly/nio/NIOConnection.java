/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2012 Oracle and/or its affiliates. All rights reserved.
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
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.grizzly.*;
import org.glassfish.grizzly.asyncqueue.AsyncReadQueueRecord;
import org.glassfish.grizzly.asyncqueue.AsyncWriteQueueRecord;
import org.glassfish.grizzly.asyncqueue.TaskQueue;
import org.glassfish.grizzly.attributes.AttributeHolder;
import org.glassfish.grizzly.attributes.IndexedAttributeHolder;
import org.glassfish.grizzly.attributes.NullaryFunction;
import org.glassfish.grizzly.impl.FutureImpl;
import org.glassfish.grizzly.monitoring.MonitoringConfig;
import org.glassfish.grizzly.monitoring.MonitoringConfigImpl;
import org.glassfish.grizzly.utils.CompletionHandlerAdapter;
import org.glassfish.grizzly.utils.Futures;
import org.glassfish.grizzly.utils.Holder;

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
    
    protected volatile Processor processor;
    protected volatile ProcessorSelector processorSelector;
    protected final AttributeHolder attributes;
    protected final TaskQueue<AsyncReadQueueRecord> asyncReadQueue;
    protected final TaskQueue<AsyncWriteQueueRecord> asyncWriteQueue;
    
    // Semaphor responsible for connect/close notification
    protected final AtomicReference<Object> connectCloseSemaphor =
            new AtomicReference<Object>();
    
    // closeTypeFlag, "null" value means the connection is open.
    protected final AtomicReference<CloseType> closeTypeFlag =
            new AtomicReference<CloseType>();
    
    protected volatile boolean isBlocking;
    protected volatile boolean isStandalone;
    protected short zeroByteReadCount;
    private final Queue<CloseListener> closeListeners =
            new ConcurrentLinkedQueue<CloseListener>();
    
    /**
     * Storage contains states of different Processors this Connection is associated with.
     */
    private final ProcessorStatesMap processorStateStorage =
            new ProcessorStatesMap();
        
    /**
     * Connection probes
     */
    protected final MonitoringConfigImpl<ConnectionProbe> monitoringConfig =
        new MonitoringConfigImpl<ConnectionProbe>(ConnectionProbe.class);

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
                Futures.<RegisterChannelResult>createSafeFuture();
        
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
                Futures.<ReadResult<M, SocketAddress>>createSafeFuture();
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
                Futures.<WriteResult<M, SocketAddress>>createSafeFuture();
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
        return channel != null && channel.isOpen() && closeTypeFlag.get() == null;
    }

    @Override
    public GrizzlyFuture<Connection> close() {
        
        final FutureImpl<Connection> future = Futures.<Connection>createSafeFuture();
        close(Futures.toCompletionHandler(future));
        
        return future;
    }

    @Override
    public void close(
            final CompletionHandler<Connection> completionHandler) {
        close0(completionHandler, true);
    }
        
    @Override
    @SuppressWarnings("unchecked")
    public final void closeSilently() {
        close(null);
    }
    
    protected void close0(
            final CompletionHandler<Connection> completionHandler,
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
                        transport.closeConnection(NIOConnection.this);
                    } catch (IOException e) {
                        logger.log(Level.FINE, "Error during connection close", e);
                    }

                    return true;
                }
            }, new CompletionHandlerAdapter<Connection, SelectorHandler.Task>(
                    null, completionHandler) {

                @Override
                protected Connection adapt(final SelectorHandler.Task result) {
                    return NIOConnection.this;
                }

                @Override
                public void failed(final Throwable throwable) {
                    try {
                        transport.closeConnection(NIOConnection.this);
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
            transport.fireIOEvent(IOEvent.CLOSED, this, null);
        }
    }

    @Override
    public void simulateIOEvent(final IOEvent ioEvent) throws IOException {
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
        final int interest = ioEvent.getSelectionKeyInterest();
        if (interest == 0) {
            return;
        }
        
        notifyIOEventEnabled(this, ioEvent);
        
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
    
    /**
     * Map, which contains {@link Processor}s and their states related to this {@link Connection}.
     */
    private final static class ProcessorStatesMap {
        private volatile int volatileFlag;
        private ProcessorState singleProcessorState;
        private ConcurrentHashMap<Processor, Object> processorStatesMap;
        
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
                
                ConcurrentHashMap<Processor, Object> localStatesMap =
                        storage.processorStatesMap;


                if (localStatesMap != null) {
                    if (localStatesMap.containsKey(processor)) {
                        return localStatesMap.get(processor);
                    }

                    final Object state = stateFactory.evaluate();
                    localStatesMap.put(processor, state);
                    return state;
                }

                localStatesMap = new ConcurrentHashMap<Processor, Object>();
                final Object state = stateFactory.evaluate();
                localStatesMap.put(processor, state);
                storage.processorStatesMap = localStatesMap;
                storage.volatileFlag++;
                return state;
            }
        }
    }
    
    protected static abstract class SettableIntHolder extends Holder.LazyIntHolder {
        @Override
        public synchronized void setInt(int value) {
            super.setInt(value);
        }
    }
}
