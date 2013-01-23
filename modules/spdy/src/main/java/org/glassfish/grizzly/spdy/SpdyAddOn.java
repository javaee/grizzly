/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012-2013 Oracle and/or its affiliates. All rights reserved.
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
package org.glassfish.grizzly.spdy;

import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.Transport;
import org.glassfish.grizzly.TransportProbe;
import org.glassfish.grizzly.filterchain.FilterChain;
import org.glassfish.grizzly.filterchain.FilterChainBuilder;
import org.glassfish.grizzly.http.server.AddOn;
import org.glassfish.grizzly.http.server.HttpServerFilter;
import org.glassfish.grizzly.http.server.NetworkListener;
import org.glassfish.grizzly.npn.NextProtoNegSupport;
import org.glassfish.grizzly.ssl.SSLBaseFilter;
import org.glassfish.grizzly.ssl.SSLConnectionContext;
import org.glassfish.grizzly.ssl.SSLFilter;
import org.glassfish.grizzly.ssl.SSLUtils;
import org.glassfish.grizzly.strategies.SameThreadIOStrategy;
import org.glassfish.grizzly.threadpool.GrizzlyExecutorService;
import org.glassfish.grizzly.threadpool.ThreadPoolConfig;
import org.glassfish.grizzly.utils.DelayedExecutor;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * FilterChain after being processed by SpdyAddOn:
 *
 * <pre>
 *     {@link org.glassfish.grizzly.filterchain.TransportFilter} <-> {@link SSLFilter}(1) <-> {@link SpdyFramingFilter} <-> {@link SpdyHandlerFilter}(2) <-> {@link HttpServerFilter}
 * </pre>
 * <ol>
 *     <li>SSLFilter is configured to use NPN for SPDY protocol negotiation</li>
 *     <li>SpdyFramingFilter and SpdyHandlerFilter replace {@link org.glassfish.grizzly.http.HttpServerFilter}</li>
 * </ol>
 *
 */
public class SpdyAddOn implements AddOn {

    private static final Logger LOGGER = Grizzly.logger(SpdyAddOn.class);


    // ------------------------------------------------------ Methods From AddOn


    @Override
    public void setup(NetworkListener networkListener, FilterChainBuilder builder) {
        if (!networkListener.isSecure()) {
            LOGGER.warning("SPDY support cannot be enabled as SSL isn't configured for this NetworkListener");
        }
        if (!NextProtoNegSupport.isEnabled()) {
            LOGGER.warning("TLS Next Protocol Negotiation is not available.  SPDY support cannot be enabled.");
            return;
        }
        configureTransport(networkListener.getTransport());
        processSslFilter(builder);
    }


    // ------------------------------------------------------- Protected Methods


    protected void configureTransport(final Transport transport) {
        transport.setIOStrategy(SameThreadIOStrategy.getInstance());
        transport.setWorkerThreadPoolConfig(null);
        transport.getMonitoringConfig().addProbes(new SpdyTransportProbe());
    }

    protected void processSslFilter(FilterChainBuilder builder) {
        final int idx = builder.indexOfType(SSLBaseFilter.class);
        configureNpn((SSLBaseFilter) builder.get(idx));
    }

    protected void configureNpn(final SSLBaseFilter sslFilter) {
        NextProtoNegSupport.getInstance().configure(sslFilter);
    }


    // ---------------------------------------------------------- Nested Classes

    private static final class SpdyTransportProbe extends TransportProbe.Adapter {


        // ----------------------------------------- Methods from TransportProbe


        @Override
        public void onBeforeStartEvent(Transport transport) {
            FilterChain transportFilterChain = transport.getFilterChain();
            FilterChainBuilder builder = FilterChainBuilder.stateless();
            for (int i = 0, len = transportFilterChain.size(); i < len; i++) {
                builder.add(transportFilterChain.get(i));
            }
            final DelayedExecutor executor = createDelayedExecutor(ThreadPoolConfig.newConfig().setMaxPoolSize(1024).setPoolName("SPDY"));
            removeHttpServerCodecFilter(builder);
            insertSpdyFilters(builder, executor);
            processHttpServerFilter(builder, executor);
            NextProtoNegSupport.getInstance().setServerSideNegotiator(transport,
                    new ProtocolNegotiator(builder.build()));
        }


        // ----------------------------------------------------- Private Methods


        private void removeHttpServerCodecFilter(FilterChainBuilder builder) {
            int idx = builder.indexOfType(org.glassfish.grizzly.http.HttpServerFilter.class);
            builder.remove(idx);
        }

        private void insertSpdyFilters(FilterChainBuilder builder, DelayedExecutor executor) {
            int idx = builder.indexOfType(SSLBaseFilter.class);
            if (idx == -1) {
                idx = 0; // put spdy filters right after transport filter
            }

            builder.add(idx + 1, new SpdyFramingFilter());
            builder.add(idx + 2, new SpdyHandlerFilter(executor.getThreadPool()));
        }

        private void processHttpServerFilter(FilterChainBuilder builder, DelayedExecutor executor) {
            int idx = builder.indexOfType(HttpServerFilter.class);
            final HttpServerFilter old = (HttpServerFilter) builder.get(idx);
            final HttpServerFilter newFilter = new HttpServerFilter(old.getConfiguration(), executor);
            newFilter.getMonitoringConfig().addProbes(old.getMonitoringConfig().getProbes());
            newFilter.setHttpHandler(old.getHttpHandler());
            builder.set(idx, newFilter);
        }

        private DelayedExecutor createDelayedExecutor(final ThreadPoolConfig config) {
            return new DelayedExecutor(GrizzlyExecutorService.createInstance(config));
        }


        // ------------------------------------------------------ Nested Classes


        private static final class ProtocolNegotiator implements NextProtoNegSupport.ServerSideNegotiator {

            private static final String HTTP11 = "http/1.1";
            private static final String SPDY3 = "spdy/3";

            private final List<String> supportedProtocols =
                    Collections.unmodifiableList(Arrays.asList(SPDY3, HTTP11));
            private final FilterChain spdyFilterChain;

            // ---------------------------------------------------- Constructors


            private ProtocolNegotiator(final FilterChain filterChain) {
                spdyFilterChain = filterChain;
            }


            // ------------------------------- Methods from ServerSideNegotiator


            @Override
            public List<String> supportedProtocols(final Connection connection) {
                return supportedProtocols;
            }

            @Override
            public void onSuccess(final Connection connection,
                                  final String protocol) {

                if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.log(Level.FINE, "NPN onSuccess. Connection={0} protocol={1}",
                            new Object[]{connection, protocol});
                }

                // If SPDY is supported, set the spdyFilterChain on the connection.
                // If HTTP/1.1 is negotiated, then use the transport FilterChain.
                if (SPDY3.equals(protocol)) {
                    SSLConnectionContext sslCtx =
                            SSLUtils.getSslConnectionContext(connection);
                    sslCtx.setNewConnectionFilterChain(spdyFilterChain);
                }
            }

            @Override
            public void onNoDeal(final Connection connection) {

                // Default to the transport FilterChain.
                if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.log(Level.FINE, "NPN onNoDeal. Connection={0}",
                            new Object[]{ connection });
                }
                // TODO: Should we consider making this behavior configurable?
                connection.closeSilently();
            }

        } // END ProtocolNegotiator

    } // END SpdyTransportProbe

}
