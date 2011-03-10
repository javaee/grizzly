/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.aio;

import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.CompletionHandler;
import org.glassfish.grizzly.ConnectionProbe;
import org.glassfish.grizzly.Processor;
import org.glassfish.grizzly.ProcessorSelector;
import org.glassfish.grizzly.Transport;
import org.glassfish.grizzly.attributes.AttributeHolder;
import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.AsynchronousChannel;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.GrizzlyFuture;
import org.glassfish.grizzly.IOEvent;
import org.glassfish.grizzly.ReadResult;
import org.glassfish.grizzly.StandaloneProcessor;
import org.glassfish.grizzly.StandaloneProcessorSelector;
import org.glassfish.grizzly.WriteResult;
import org.glassfish.grizzly.asyncqueue.TaskQueue;
import org.glassfish.grizzly.asyncqueue.AsyncReadQueueRecord;
import org.glassfish.grizzly.asyncqueue.AsyncWriteQueueRecord;
import org.glassfish.grizzly.attributes.IndexedAttributeHolder;
import org.glassfish.grizzly.impl.ReadyFutureImpl;
import org.glassfish.grizzly.monitoring.MonitoringConfig;
import org.glassfish.grizzly.monitoring.MonitoringConfigImpl;
import java.util.Queue;
import java.util.logging.Logger;
import org.glassfish.grizzly.utils.DataStructures;

/**
 * Common {@link Connection} implementation for Java NIO <tt>Connection</tt>s.
 * 
 * @author Alexey Stashok
 */
public abstract class AIOConnection implements Connection<SocketAddress> {
    private static final Logger logger = Grizzly.logger(AIOConnection.class);

    protected final AIOTransport transport;

    protected volatile int readBufferSize;
    protected volatile int writeBufferSize;

    protected volatile long readTimeoutMillis = 30000;
    protected volatile long writeTimeoutMillis = 30000;

    protected volatile AsynchronousChannel channel;
    
    protected volatile Processor processor;
    protected volatile ProcessorSelector processorSelector;
    
    protected final AttributeHolder attributes;

    protected final TaskQueue<AsyncReadQueueRecord> asyncReadQueue;
    protected final TaskQueue<AsyncWriteQueueRecord> asyncWriteQueue;
    
    protected final AtomicBoolean isClosed = new AtomicBoolean(false);

    protected volatile boolean isBlocking;

    protected volatile boolean isStandalone;

    private final Queue<CloseListener> closeListeners =
            DataStructures.getLTQinstance(CloseListener.class);

    /**
     * Connection probes
     */
    protected final MonitoringConfigImpl<ConnectionProbe> monitoringConfig =
            new MonitoringConfigImpl<>(ConnectionProbe.class);

    public AIOConnection(AIOTransport transport) {
        this.transport = transport;
        asyncReadQueue = TaskQueue.createSafeTaskQueue();
        asyncWriteQueue = TaskQueue.createSafeTaskQueue();
        
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

    @Override
    public int getReadBufferSize() {
        return readBufferSize;
    }

    @Override
    public void setReadBufferSize(int readBufferSize) {
        this.readBufferSize = readBufferSize;
    }

    @Override
    public int getWriteBufferSize() {
        return writeBufferSize;
    }

    @Override
    public void setWriteBufferSize(int writeBufferSize) {
        this.writeBufferSize = writeBufferSize;
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

    public AsynchronousChannel getChannel() {
        return channel;
    }

    protected void setChannel(AsynchronousChannel channel) {
        this.channel = channel;
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
            ProcessorSelector preferableProcessorSelector) {
        this.processorSelector =
                preferableProcessorSelector;
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
    public <M> GrizzlyFuture<ReadResult<M, SocketAddress>> read()
            throws IOException {
        return read(null);
    }

    @Override
    public <M> GrizzlyFuture<ReadResult<M, SocketAddress>> read(
            CompletionHandler<ReadResult<M, SocketAddress>> completionHandler)
            throws IOException {

        final Processor obtainedProcessor = obtainProcessor(IOEvent.READ);
        return obtainedProcessor.read(this, completionHandler);
    }

    @Override
    public <M> GrizzlyFuture<WriteResult<M, SocketAddress>> write(M message)
            throws IOException {
        return write(null, message, null);
    }

    @Override
    public <M> GrizzlyFuture<WriteResult<M, SocketAddress>> write(M message,
            CompletionHandler<WriteResult<M, SocketAddress>> completionHandler)
            throws IOException {
        return write(null, message, completionHandler);
    }

    @Override
    public <M> GrizzlyFuture<WriteResult<M, SocketAddress>> write(
            SocketAddress dstAddress, M message,
            CompletionHandler<WriteResult<M, SocketAddress>> completionHandler)
            throws IOException {

        final Processor obtainedProcessor = obtainProcessor(IOEvent.WRITE);
        return obtainedProcessor.write(this, dstAddress, message, completionHandler);
    }

    @Override
    public boolean isOpen() {
        return channel != null && channel.isOpen() && !isClosed.get();
    }

    @Override
    public GrizzlyFuture close() throws IOException {
        if (!isClosed.getAndSet(true)) {
            preClose();
            notifyCloseListeners();
            notifyProbesClose(this);

            channel.close();
//            return transport.getSelectorHandler().executeInSelectorThread(
//                    selectorRunner, new Runnable() {
//
//                @Override
//                public void run() {
//                    try {
//                        ((AIOTransport) transport).closeConnection(AIOConnection.this);
//                    } catch (IOException e) {
//                        logger.log(Level.FINE, "Error during connection close", e);
//                    }
//                }
//            }, null);
        }

        return ReadyFutureImpl.create(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addCloseListener(CloseListener closeListener) {
        // check if connection is still open
        if (!isClosed.get()) {
            // add close listener
            closeListeners.add(closeListener);
            // check the connection state again
            if (isClosed.get() && closeListeners.remove(closeListener)) {
                // if connection was closed during the method call - notify the listener
                try {
                    closeListener.onClosed(this);
                } catch (IOException ignored) {
                }
            }
        } else { // if connection is closed - notify the listener
            try {
                closeListener.onClosed(this);
            } catch (IOException ignored) {
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean removeCloseListener(CloseListener closeListener) {
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
    protected static void notifyProbesBind(AIOConnection connection) {
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
     * @param connection the <tt>Connection</tt> event occurred on.
     */
    protected static void notifyProbesAccept(AIOConnection connection) {
        final ConnectionProbe[] probes =
                connection.monitoringConfig.getProbesUnsafe();
        if (probes != null) {
            for (ConnectionProbe probe : probes) {
                probe.onAcceptEvent(connection);
            }
        }
    }

    /**
     * Notify registered {@link ConnectionProbe}s about the connect event.
     *
     * @param connection the <tt>Connection</tt> event occurred on.
     */
    protected static void notifyProbesConnect(final AIOConnection connection) {
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
    protected static void notifyProbesRead(AIOConnection connection,
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
    protected static void notifyProbesWrite(AIOConnection connection,
            Buffer data, int size) {
        
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
    protected static void notifyIOEventReady(AIOConnection connection,
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
    protected static void notifyIOEventEnabled(AIOConnection connection,
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
    protected static void notifyIOEventDisabled(AIOConnection connection,
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
    protected static void notifyProbesClose(AIOConnection connection) {
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
    protected static void notifyProbesError(AIOConnection connection,
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
        CloseListener closeListener;
        while((closeListener = closeListeners.poll()) != null) {
            try {
                closeListener.onClosed(this);
            } catch (IOException ignored) {
            }
        }
    }

    protected abstract void preClose();
}
