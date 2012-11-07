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
package org.glassfish.grizzly;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import org.glassfish.grizzly.filterchain.FilterChain;
import org.glassfish.grizzly.impl.FutureImpl;
import org.glassfish.grizzly.impl.SafeFutureImpl;
import org.glassfish.grizzly.monitoring.MonitoringConfig;
import org.glassfish.grizzly.monitoring.MonitoringConfigImpl;

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
    private FilterChain filterChain;

    /**
     * Connection probes
     */
    protected final MonitoringConfigImpl<ConnectionProbe> connectionMonitoringConfig =
            new MonitoringConfigImpl<ConnectionProbe>(ConnectionProbe.class);
    
    public AbstractSocketConnectorHandler(final Transport transport) {
        this.transport = transport;
        this.filterChain = transport.getFilterChain();
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
        return connect0(remoteAddress, localAddress, null, true);
    }

    @Override
    public void connect(SocketAddress remoteAddress,
            SocketAddress localAddress,
            CompletionHandler<Connection> completionHandler) {
        connect0(remoteAddress, localAddress, completionHandler, false);
    }

    protected abstract FutureImpl<Connection> connect0(
            final SocketAddress remoteAddress,
            final SocketAddress localAddress,
            final CompletionHandler<Connection> completionHandler,
            final boolean needFuture);

    /**
     * Get the default {@link FilterChain} to process {@link Event}, occurring
     * on connection phase.
     *
     * @return the default {@link FilterChain} to process {@link Event},
     * occurring on connection phase.
     */
    public FilterChain getFilterChain() {
        return filterChain;
    }

    /**
     * Set the default {@link FilterChain} to process {@link Event}, occurring
     * on connection phase.
     *
     * @param filterChain the default {@link FilterChain} to process
     * {@link Event}, occurring on connection phase.
     */
    public final void setFilterChain(final FilterChain filterChain) {
        this.filterChain = filterChain;
    }

    /**
     * Add the {@link ConnectionProbe}, which will be notified about
     * <tt>Connection</tt> life-cycle events.
     *
     * @param probe the {@link ConnectionProbe}.
     */
    public final void addMonitoringProbe(ConnectionProbe probe) {
        connectionMonitoringConfig.addProbes(probe);
    }

    /**
     * Remove the {@link ConnectionProbe}.
     *
     * @param probe the {@link ConnectionProbe}.
     */
    public final boolean removeMonitoringProbe(ConnectionProbe probe) {
        return connectionMonitoringConfig.removeProbes(probe);
    }

    /**
     * Get the {@link ConnectionProbe}, which are registered on the <tt>Connection</tt>.
     * Please note, it's not appropriate to modify the returned array's content.
     * Please use {@link #addMonitoringProbe(org.glassfish.grizzly.ConnectionProbe)} and
     * {@link #removeMonitoringProbe(org.glassfish.grizzly.ConnectionProbe)} instead.
     *
     * @return the {@link ConnectionProbe}, which are registered on the <tt>Connection</tt>.
     */
    public final ConnectionProbe[] getMonitoringProbes() {
        return connectionMonitoringConfig.getProbes();
    }

    /**
     * Pre-configures {@link Connection} object before actual connecting phase
     * will be started.
     * 
     * @param connection {@link Connection} to pre-configure.
     */
    protected void preConfigure(final Connection<?> connection) {
        connection.setFilterChain(getFilterChain());
        
        final MonitoringConfig<ConnectionProbe> monitoringConfig =
                connection.getMonitoringConfig();
        final ConnectionProbe[] probes = connectionMonitoringConfig.getProbes();
        monitoringConfig.addProbes(probes);
    }

    protected FutureImpl<Connection> makeCancellableFuture(final Connection connection) {
        return new SafeFutureImpl<Connection>() {

            @Override
            protected void done() {
                try {
                    if (!isCancelled()) {
                        get();
                        return;
                    }
                } catch(Throwable ignored) {
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
        protected final AbstractSocketConnectorHandler connectorHandler;

        public Builder(AbstractSocketConnectorHandler connectorHandler) {
            this.connectorHandler = connectorHandler;
        }

        public E filterChain(final FilterChain filterChain) {
            connectorHandler.setFilterChain(filterChain);
            return (E) this;
        }

        public E probe(ConnectionProbe connectionProbe) {
            connectorHandler.addMonitoringProbe(connectionProbe);
            return (E) this;
        }
    }
}
