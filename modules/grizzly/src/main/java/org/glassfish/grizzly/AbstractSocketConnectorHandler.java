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
package org.glassfish.grizzly;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.LinkedList;
import java.util.List;
import org.glassfish.grizzly.impl.FutureImpl;
import org.glassfish.grizzly.impl.SafeFutureImpl;

/**
 * Abstract class simplifies the implementation of
 * {@link SocketConnectorHandler}
 * interface by pre-implementing some of its methods.
 * 
 * @author Alexey Stashok
 */
public abstract class AbstractSocketConnectorHandler
        implements SocketConnectorHandler {

    protected final Transport transport;
    private Processor processor;
    private ProcessorSelector processorSelector;

    protected final List<ConnectionProbe> probes =
            new LinkedList<ConnectionProbe>();

    public AbstractSocketConnectorHandler(Transport transport) {
        this.transport = transport;
        this.processor = transport.getProcessor();
        this.processorSelector = transport.getProcessorSelector();
    }

    @Override
    public GrizzlyFuture<Connection> connect(String host, int port) {
        return connect(new InetSocketAddress(host, port));
    }

    @Override
    public GrizzlyFuture<Connection> connect(SocketAddress remoteAddress) {
        return connect(remoteAddress, (SocketAddress) null);
    }

    @Override
    public void connect(SocketAddress remoteAddress,
            CompletionHandler<Connection> completionHandler) {
        connect(remoteAddress, null, completionHandler);
    }

    @Override
    public GrizzlyFuture<Connection> connect(SocketAddress remoteAddress,
            SocketAddress localAddress) {
        return connectAsync(remoteAddress, localAddress, null, true);
    }

    @Override
    public void connect(SocketAddress remoteAddress,
            SocketAddress localAddress,
            CompletionHandler<Connection> completionHandler) {
        connectAsync(remoteAddress, localAddress, completionHandler, false);
    }

    protected abstract FutureImpl<Connection> connectAsync(
            final SocketAddress remoteAddress,
            final SocketAddress localAddress,
            final CompletionHandler<Connection> completionHandler,
            final boolean needFuture);

    /**
     * Get the default {@link Processor} to process {@link IOEvent}, occurring
     * on connection phase.
     *
     * @return the default {@link Processor} to process {@link IOEvent},
     * occurring on connection phase.
     */
    public Processor getProcessor() {
        return processor;
    }

    /**
     * Set the default {@link Processor} to process {@link IOEvent}, occurring
     * on connection phase.
     *
     * @param processor the default {@link Processor} to process
     * {@link IOEvent}, occurring on connection phase.
     */
    public void setProcessor(Processor processor) {
        this.processor = processor;
    }

    /**
     * Gets the default {@link ProcessorSelector}, which will be used to get
     * {@link Processor} to process I/O events, occurring on connection phase.
     *
     * @return the default {@link ProcessorSelector}, which will be used to get
     * {@link Processor} to process I/O events, occurring on connection phase.
     */
    public ProcessorSelector getProcessorSelector() {
        return processorSelector;
    }

    /**
     * Sets the default {@link ProcessorSelector}, which will be used to get
     * {@link Processor} to process I/O events, occurring on connection phase.
     *
     * @param processorSelector the default {@link ProcessorSelector},
     * which will be used to get {@link Processor} to process I/O events,
     * occurring on connection phase.
     */
    public void setProcessorSelector(ProcessorSelector processorSelector) {
        this.processorSelector = processorSelector;
    }

    /**
     * Add the {@link ConnectionProbe}, which will be notified about
     * <tt>Connection</tt> life-cycle events.
     *
     * @param probe the {@link ConnectionProbe}.
     */
    public void addMonitoringProbe(ConnectionProbe probe) {
        probes.add(probe);
    }

    /**
     * Remove the {@link ConnectionProbe}.
     *
     * @param probe the {@link ConnectionProbe}.
     */
    public boolean removeMonitoringProbe(ConnectionProbe probe) {
        return probes.remove(probe);
    }

    /**
     * Get the {@link ConnectionProbe}, which are registered on the <tt>Connection</tt>.
     * Please note, it's not appropriate to modify the returned array's content.
     * Please use {@link #addMonitoringProbe(org.glassfish.grizzly.ConnectionProbe)} and
     * {@link #removeMonitoringProbe(org.glassfish.grizzly.ConnectionProbe)} instead.
     *
     * @return the {@link ConnectionProbe}, which are registered on the <tt>Connection</tt>.
     */
    public ConnectionProbe[] getMonitoringProbes() {
        return probes.toArray(new ConnectionProbe[probes.size()]);
    }

    /**
     * Pre-configures {@link Connection} object before actual connecting phase
     * will be started.
     * 
     * @param connection {@link Connection} to pre-configure.
     */
    protected void preConfigure(Connection connection) {
    }

    protected FutureImpl<Connection> makeCancellableFuture(final Connection connection) {
        return new SafeFutureImpl<Connection>() {

            @Override
            protected void onComplete() {
                try {
                    if (!isCancelled()) {
                        get();
                        return;
                    }
                } catch (Throwable ignored) {
                }

                try {
                    connection.closeSilently();
                } catch (Exception ignored) {
                }
            }
        };
    }
    
    /**
     * Builder
     *
     * @param <E>
     */
    @SuppressWarnings("unchecked")
    public abstract static class Builder<E extends Builder> {

        protected Processor processor;
        protected ProcessorSelector processorSelector;
        protected ConnectionProbe connectionProbe;

        public E processor(final Processor processor) {
            this.processor = processor;
            return (E) this;
        }

        public E processorSelector(final ProcessorSelector processorSelector) {
            this.processorSelector = processorSelector;
            return (E) this;
        }

        public E probe(ConnectionProbe connectionProbe) {
            this.connectionProbe = connectionProbe;
            return (E) this;
        }

        public AbstractSocketConnectorHandler build() {
            AbstractSocketConnectorHandler handler = create();
            if (processor != null) {
                handler.setProcessor(processor);
            }
            if (processorSelector != null) {
                handler.setProcessorSelector(processorSelector);
            }
            if (connectionProbe != null) {
                handler.addMonitoringProbe(connectionProbe);
            }
            return handler;
        }

        protected abstract AbstractSocketConnectorHandler create();
    }
}
