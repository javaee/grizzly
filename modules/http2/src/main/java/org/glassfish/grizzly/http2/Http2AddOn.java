/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012-2016 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.http2;

import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.filterchain.FilterChainBuilder;
import org.glassfish.grizzly.http.server.AddOn;
import org.glassfish.grizzly.http.server.NetworkListener;
import org.glassfish.grizzly.ssl.SSLFilter;

import java.util.logging.Logger;
import org.glassfish.grizzly.Transport;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;


import org.glassfish.grizzly.ssl.SSLBaseFilter;

/**
 * FilterChain after being processed by {@link Http2AddOn}:
 *
 * <pre>
 *     {@link org.glassfish.grizzly.filterchain.TransportFilter} <-> {@link SSLFilter}(optional) <-> {@link org.glassfish.grizzly.http.HttpServerFilter} <-> {@link Http2ServerFilter} <-> {@link org.glassfish.grizzly.http.server.HttpServer}
 * </pre>
 *
 * {@link SSLFilter}, if present, is configured to use ALPN for HTTP2 protocol negotiation
 */
public class Http2AddOn implements AddOn {
    private static final Logger LOGGER = Grizzly.logger(Http2AddOn.class);
    
    private int maxConcurrentStreams = -1;
    private int initialWindowSize = -1;
    private int maxFramePayloadSize = -1;
    private boolean disableCipherCheck;
    

    // ------------------------------------------------------ Methods From AddOn


    @Override
    public void setup(NetworkListener networkListener, FilterChainBuilder builder) {
        final TCPNIOTransport transport = networkListener.getTransport();
        
        if (networkListener.isSecure() && !AlpnSupport.isEnabled()) {
            LOGGER.warning("TLS ALPN (Application-Layer Protocol Negotiation) support is not available. HTTP/2 support will not be enabled.");
            return;
        }
        
        final Http2ServerFilter http2Filter = updateFilterChain(builder);
        
        if (networkListener.isSecure()) {
            configureAlpn(transport, http2Filter, builder);
        }
    }

    // ------------------------------------------------------ Getters / Setters
    
    /**
     * Returns the default maximum number of concurrent streams allowed for one session.
     * Negative value means "unlimited".
     */
    public int getMaxConcurrentStreams() {
        return maxConcurrentStreams;
    }
    
    /**
     * Sets the default maximum number of concurrent streams allowed for one session.
     * Negative value means "unlimited".
     */
    public void setMaxConcurrentStreams(final int maxConcurrentStreams) {
        this.maxConcurrentStreams = maxConcurrentStreams;
    }

    /**
     * Returns the default initial stream window size (in bytes) for new HTTP2 connections.
     */
    public int getInitialWindowSize() {
        return initialWindowSize;
    }
    
    /**
     * Sets the default initial stream window size (in bytes) for new HTTP2 connections.
     */
    public void setInitialWindowSize(final int initialWindowSize) {
        this.initialWindowSize = initialWindowSize;
    }
  
    /**
     * @return the maximum allowed HTTP2 frame payload size.
     */
    public int getMaxFramePayloadSize() {
        return maxFramePayloadSize;
    }

    /**
     * Sets the maximum allowed HTTP2 frame payload size.
     * @param maxFramePayloadSize the maximum allowed HTTP2 frame size.
     */
    @SuppressWarnings("unused")
    public void setMaxFramePayloadSize(final int maxFramePayloadSize) {
        this.maxFramePayloadSize = maxFramePayloadSize;
    }

    /**
     * @return whether or not strict cipher suite checking against RFC 7540's blacklist is performed or not.
     *  If not explicitly configured, checking will be performed.
     */
    public boolean isDisableCipherCheck() {
        return disableCipherCheck;
    }

    /**
     * Allows the developer to disable strict cipher suite checking of the connection against RFC 7540's blacklist.
     *
     * @param disableCipherCheck pass <code>true</code> to disable the checking.
     */
    public void setDisableCipherCheck(boolean disableCipherCheck) {
        this.disableCipherCheck = disableCipherCheck;
    }


    // ----------------------------------------------------- Private Methods


    private Http2ServerFilter updateFilterChain(final FilterChainBuilder builder) {
        
        final int codecFilterIdx = builder.indexOfType(
                org.glassfish.grizzly.http.HttpServerFilter.class);
                
        final Http2ServerFilter http2HandlerFilter =
                new Http2ServerFilter(null, disableCipherCheck);
        
        http2HandlerFilter.setLocalMaxFramePayloadSize(getMaxFramePayloadSize());
        http2HandlerFilter.setInitialWindowSize(getInitialWindowSize());
        http2HandlerFilter.setMaxConcurrentStreams(getMaxConcurrentStreams());
        builder.add(codecFilterIdx + 1, http2HandlerFilter);
        
        return http2HandlerFilter;
    }
    
    private static void configureAlpn(final Transport transport,
                                      final Http2ServerFilter http2Filter,
                                      final FilterChainBuilder builder) {
        
        final int idx = builder.indexOfType(SSLBaseFilter.class);
        if (idx != -1) {
            final SSLBaseFilter sslFilter = (SSLBaseFilter) builder.get(idx);

            AlpnSupport.getInstance().configure(sslFilter);
            AlpnSupport.getInstance().setServerSideNegotiator(transport,
                    new AlpnServerNegotiatorImpl(http2Filter));
        }
    }
}
