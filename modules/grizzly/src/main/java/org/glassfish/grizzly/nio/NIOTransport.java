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
import java.nio.channels.SelectionKey;
import java.nio.channels.spi.SelectorProvider;
import java.util.Random;
import org.glassfish.grizzly.AbstractTransport;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Context;
import org.glassfish.grizzly.EventProcessingHandler;
import org.glassfish.grizzly.IOEvent;
import org.glassfish.grizzly.IOStrategy;
import org.glassfish.grizzly.Writer;
import org.glassfish.grizzly.asyncqueue.AsyncQueue;
import org.glassfish.grizzly.asyncqueue.AsyncQueueWriter;
import org.glassfish.grizzly.nio.tmpselectors.TemporarySelectorIO;

/**
 *
 * @author oleksiys
 */
public abstract class NIOTransport extends AbstractTransport {
    protected static final Random RANDOM = new Random();
    
    protected SelectorHandler selectorHandler;

    protected int selectorRunnersCount;
    
    protected SelectorRunner[] selectorRunners;
    
    protected NIOChannelDistributor nioChannelDistributor;

    protected SelectorProvider selectorProvider = SelectorProvider.provider();
    
    public NIOTransport(final String name) {
        super(name);

        selectorRunnersCount = Runtime.getRuntime().availableProcessors();
    }

    public NIOConnection getConnectionForKey(SelectionKey selectionKey) {
        return (NIOConnection) selectionKey.attachment();
    }
    
    public SelectorHandler getSelectorHandler() {
        return selectorHandler;
    }

    public void setSelectorHandler(final SelectorHandler selectorHandler) {
        this.selectorHandler = selectorHandler;
        notifyProbesConfigChanged(this);
    }

    public int getSelectorRunnersCount() {
        return selectorRunnersCount;
    }

    public void setSelectorRunnersCount(final int selectorRunnersCount) {
        this.selectorRunnersCount = selectorRunnersCount;
        kernelPoolConfig.setCorePoolSize(selectorRunnersCount);
        kernelPoolConfig.setMaxPoolSize(selectorRunnersCount);
        notifyProbesConfigChanged(this);
    }

    /**
     * Get the {@link SelectorProvider} to be used by this transport.
     * 
     * @return the {@link SelectorProvider} to be used by this transport.
     */    
    public SelectorProvider getSelectorProvider() {
        return selectorProvider;
    }

    /**
     * Set the {@link SelectorProvider} to be used by this transport.
     *
     * @param selectorProvider the {@link SelectorProvider}.
     */
    public void setSelectorProvider(final SelectorProvider selectorProvider) {
        this.selectorProvider = selectorProvider != null
                ? selectorProvider
                : SelectorProvider.provider();
    }


    /**
     * Returns <tt>true</tt>, if <tt>TCPNIOTransport</tt> is configured to use
     * {@link AsyncQueueWriter}, optimized to be used in connection multiplexing
     * mode, or <tt>false</tt> otherwise.
     * 
     * @return <tt>true</tt>, if <tt>TCPNIOTransport</tt> is configured to use
     * {@link AsyncQueueWriter}, optimized to be used in connection multiplexing
     * mode, or <tt>false</tt> otherwise.
     */
    public boolean isOptimizedForMultiplexing() {
        return !getAsyncQueueWriter().isAllowDirectWrite();
    }

    /**
     * Configures <tt>TCPNIOTransport</tt> to be optimized for specific for the
     * connection multiplexing usecase, when different threads will try to
     * write data simultaneously.
     */
    public void setOptimizedForMultiplexing(final boolean isOptimizedForMultiplexing) {
        getAsyncQueueWriter().setAllowDirectWrite(!isOptimizedForMultiplexing);
    }

    /**
     * @see AsyncQueueWriter#getMaxPendingBytesPerConnection()
     * 
     * Note: the value is per connection, not transport total.
     */
    public int getMaxAsyncWriteQueueSizeInBytes() {
        return getAsyncQueueWriter()
                .getMaxPendingBytesPerConnection();
    }
    
    /**
     * @see AsyncQueueWriter#setMaxPendingBytesPerConnection(int)
     * 
     * Note: the value is per connection, not transport total.
     */
    public void setMaxAsyncWriteQueueSizeInBytes(
            final int size) {
        getAsyncQueueWriter().setMaxPendingBytesPerConnection(size);
    }
    
    @Override
    public void start() throws IOException {
        if (selectorProvider == null) {
            selectorProvider = SelectorProvider.provider();
        }
    }
    
    protected synchronized void startSelectorRunners() throws IOException {
        selectorRunners = new SelectorRunner[selectorRunnersCount];
        
        for (int i = 0; i < selectorRunnersCount; i++) {
            final SelectorRunner runner = SelectorRunner.create(this);
            runner.start();
            selectorRunners[i] = runner;
        }
    }
    
    protected synchronized void stopSelectorRunners() throws IOException {
        if (selectorRunners == null) {
            return;
        }

        for (int i = 0; i < selectorRunners.length; i++) {
            SelectorRunner runner = selectorRunners[i];
            if (runner != null) {
                runner.stop();
                selectorRunners[i] = null;
            }
        }

        selectorRunners = null;
    }

    public NIOChannelDistributor getNIOChannelDistributor() {
        return nioChannelDistributor;
    }

    public void setNIOChannelDistributor(final NIOChannelDistributor nioChannelDistributor) {
        this.nioChannelDistributor = nioChannelDistributor;
        notifyProbesConfigChanged(this);
    }


    /**
     * Get the {@link Writer} to write data to the {@link Connection}.
     * The <tt>Transport</tt> may decide to return blocking or non-blocking {@link Writer}
     * depending on the {@link Connection} settings.
     *
     * @param connection {@link Connection}.
     *
     * @return {@link Writer}.
     */
    protected Writer<SocketAddress> getWriter(final Connection connection) {
        return getWriter(connection.isBlocking());
    }

    /**
     * Get the {@link Writer} implementation, depending on the requested mode.
     *
     * @param isBlocking blocking mode.
     *
     * @return {@link Writer}.
     */
    protected Writer<SocketAddress> getWriter(final boolean isBlocking) {
        if (isBlocking) {
            return getTemporarySelectorIO().getWriter();
        } else {
            return getAsyncQueueWriter();
        }
    }

    protected abstract TemporarySelectorIO getTemporarySelectorIO();
    
    /**
     * {@inheritDoc}
     */
    protected SelectorRunner[] getSelectorRunners() {
        return selectorRunners;
    }
    
    protected boolean processOpRead(final NIOConnection nioConnection)
            throws IOException {
        return strategy.executeIOEvent(nioConnection, IOEvent.READ,
                new IOStrategy.DecisionListener() {

            @Override
            public EventProcessingHandler goSync(Connection connection,
                    IOEvent ioEvent) {
                return ENABLED_READ_PROCESSING_HANDLER;
            }

            @Override
            public EventProcessingHandler goAsync(Connection connection,
                    IOEvent ioEvent) throws IOException {
                ((NIOConnection) connection).deregisterKeyInterest(
                        SelectionKey.OP_READ);
                return DISABLED_READ_PROCESSING_HANDLER;
            }
        });
    }

    protected boolean processOpWrite(final NIOConnection connection)
            throws IOException {
        return processOpWrite(connection, true);
    }
    
    boolean processOpWrite(final NIOConnection connection,
            final boolean isOpWriteEnabled)
            throws IOException {
        
        final AsyncQueue.AsyncResult result =
                getAsyncQueueWriter().onReady(connection);
        
        switch (result) {
            case COMPLETE:
                if (isOpWriteEnabled) {
                    connection.deregisterKeyInterest(SelectionKey.OP_WRITE);
                }
                break;

            case HAS_MORE:
                if (!isOpWriteEnabled) {
                    connection.registerKeyInterest(SelectionKey.OP_WRITE);
                }
                break;

            case EXPECTING_MORE:
                if (!isOpWriteEnabled) {
                    connection.enqueOpWriteReady();
                }
        }
        
        return true;
    }

    protected abstract boolean processOpAccept(NIOConnection connection)
            throws IOException;
    
    protected abstract boolean processOpConnect(NIOConnection connection)
            throws IOException;

    protected abstract void closeConnection(final NIOConnection connection)
            throws IOException;
    
    protected abstract AbstractNIOAsyncQueueWriter getAsyncQueueWriter();
    
    private static final EventProcessingHandler ENABLED_READ_PROCESSING_HANDLER =
            new EnabledReadProcessingHandler();
    
    private static final EventProcessingHandler DISABLED_READ_PROCESSING_HANDLER =
            new DisabledReadProcessingHandler();
    
    private final static class EnabledReadProcessingHandler
            extends EventProcessingHandler.Adapter {

        @Override
        public void onComplete(final Context context) throws IOException {
            if (context.wasSuspended()) {
                ((NIOConnection) context.getConnection()).registerKeyInterest(
                        SelectionKey.OP_READ);
            }
        }

        @Override
        public void onSuspend(final Context context) throws IOException {
            ((NIOConnection) context.getConnection()).deregisterKeyInterest(
                    SelectionKey.OP_READ);
        }
    }
    
    private final static class DisabledReadProcessingHandler
            extends EventProcessingHandler.Adapter {

        @Override
        public void onComplete(final Context context) throws IOException {
            ((NIOConnection) context.getConnection()).registerKeyInterest(
                    SelectionKey.OP_READ);
        }
    }
    
}
