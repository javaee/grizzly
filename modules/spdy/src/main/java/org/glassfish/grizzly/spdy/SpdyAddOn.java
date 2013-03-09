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
import org.glassfish.grizzly.npn.ServerSideNegotiator;
import org.glassfish.grizzly.ssl.SSLBaseFilter;
import org.glassfish.grizzly.ssl.SSLConnectionContext;
import org.glassfish.grizzly.ssl.SSLFilter;
import org.glassfish.grizzly.ssl.SSLUtils;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;

import javax.net.ssl.SSLEngine;

import static org.glassfish.grizzly.spdy.Constants.DEFAULT_INITIAL_WINDOW_SIZE;
import static org.glassfish.grizzly.spdy.Constants.DEFAULT_MAX_CONCURRENT_STREAMS;

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

    private final SpdyMode mode;

    private int maxConcurrentStreams = DEFAULT_MAX_CONCURRENT_STREAMS;
    private int initialWindowSize = DEFAULT_INITIAL_WINDOW_SIZE;
    
    public SpdyAddOn() {
        this(SpdyMode.NPN);
    }

    public SpdyAddOn(final SpdyMode mode) {
        this.mode = mode;
    }    
    
    // ------------------------------------------------------ Methods From AddOn


    @Override
    public void setup(NetworkListener networkListener, FilterChainBuilder builder) {
        final TCPNIOTransport transport = networkListener.getTransport();
        
        if (mode == SpdyMode.NPN) {
            if (!networkListener.isSecure()) {
                LOGGER.warning("Can not configure NPN (Next Protocol Negotiation) mode on non-secured NetworkListener. SPDY support will not be enabled.");
                return;
            }
            
            if (!NextProtoNegSupport.isEnabled()) {
                LOGGER.warning("TLS NPN (Next Protocol Negotiation) support is not available. SPDY support will not be enabled.");
                return;
            }
            
            configureNpn(builder);

            transport.getMonitoringConfig().addProbes(new SpdyNpnConfigProbe());
        } else {
            updateFilterChain(mode, builder);
        }
    }

    // ------------------------------------------------------ Getters / Setters
    
    /**
     * Sets the default maximum number of concurrent streams allowed for one session.
     * Negative value means "unlimited".
     */
    public void setMaxConcurrentStreams(final int maxConcurrentStreams) {
        this.maxConcurrentStreams = maxConcurrentStreams;
    }

    /**
     * Returns the default maximum number of concurrent streams allowed for one session.
     * Negative value means "unlimited".
     */
    public int getMaxConcurrentStreams() {
        return maxConcurrentStreams;
    }

    /**
     * Sets the default initial stream window size (in bytes) for new SPDY sessions.
     */
    public void setInitialWindowSize(final int initialWindowSize) {
        this.initialWindowSize = initialWindowSize;
    }

    /**
     * Returns the default initial stream window size (in bytes) for new SPDY sessions.
     */
    public int getInitialWindowSize() {
        return initialWindowSize;
    }    

    // ------------------------------------------------------- Protected Methods


    protected void configureNpn(FilterChainBuilder builder) {
        final int idx = builder.indexOfType(SSLBaseFilter.class);
        final SSLBaseFilter sslFilter = (SSLBaseFilter) builder.get(idx);
        
        NextProtoNegSupport.getInstance().configure(sslFilter);
    }


    // ----------------------------------------------------- Private Methods


    private static void updateFilterChain(final SpdyMode mode, final FilterChainBuilder builder) {
        final int idx = removeHttpServerCodecFilter(builder);
        insertSpdyFilters(mode, builder, idx);
    }
    
    private static int removeHttpServerCodecFilter(FilterChainBuilder builder) {
        int idx = builder.indexOfType(org.glassfish.grizzly.http.HttpServerFilter.class);
        builder.remove(idx);

        return idx;
    }

    private static void insertSpdyFilters(SpdyMode mode,
            FilterChainBuilder builder, int idx) {
        
        builder.add(idx, new SpdyFramingFilter());
        builder.add(idx + 1, new SpdyHandlerFilter(mode));
    }

    // ---------------------------------------------------------- Nested Classes

    private static final class SpdyNpnConfigProbe extends TransportProbe.Adapter {


        // ----------------------------------------- Methods from TransportProbe


        @Override
        public void onBeforeStartEvent(Transport transport) {
            FilterChain transportFilterChain = (FilterChain) transport.getProcessor();
            FilterChainBuilder builder = FilterChainBuilder.stateless();
            for (int i = 0, len = transportFilterChain.size(); i < len; i++) {
                builder.add(transportFilterChain.get(i));
            }
            
            updateFilterChain(SpdyMode.NPN, builder);
            NextProtoNegSupport.getInstance().setServerSideNegotiator(transport,
                    new ProtocolNegotiator(builder.build()));
        }


        // ------------------------------------------------------ Nested Classes


        private static final class ProtocolNegotiator implements ServerSideNegotiator {

            private static final String HTTP11 = "http/1.1";
            private static final String SPDY3 = "spdy/3";

            private final LinkedHashSet<String> supportedProtocols =
                    new LinkedHashSet<String>(Arrays.asList(SPDY3, HTTP11));
            private final FilterChain spdyFilterChain;

            // ---------------------------------------------------- Constructors


            private ProtocolNegotiator(final FilterChain filterChain) {
                spdyFilterChain = filterChain;
            }


            // ------------------------------- Methods from ServerSideNegotiator


            @Override
            public LinkedHashSet<String> supportedProtocols(final SSLEngine engine) {
                return supportedProtocols;
            }

            @Override
            public void onSuccess(final SSLEngine engine, final String protocol) {

                final Connection connection = NextProtoNegSupport.getConnection(engine);
                if (LOGGER.isLoggable(Level.INFO)) {
                    LOGGER.log(Level.INFO, "NPN onSuccess. Connection={0} protocol={1}",
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
            public void onNoDeal(final SSLEngine engine) {
                final Connection connection = NextProtoNegSupport.getConnection(engine);
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
