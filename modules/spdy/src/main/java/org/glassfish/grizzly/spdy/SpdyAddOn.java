/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012 Oracle and/or its affiliates. All rights reserved.
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

import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.Transport;
import org.glassfish.grizzly.filterchain.FilterChainBuilder;
import org.glassfish.grizzly.http.server.AddOn;
import org.glassfish.grizzly.http.server.HttpServerFilter;
import org.glassfish.grizzly.http.server.NetworkListener;
import org.glassfish.grizzly.npn.NextProtoNegSupport;
import org.glassfish.grizzly.ssl.SSLFilter;
import org.glassfish.grizzly.strategies.SameThreadIOStrategy;
import org.glassfish.grizzly.threadpool.GrizzlyExecutorService;
import org.glassfish.grizzly.threadpool.ThreadPoolConfig;
import org.glassfish.grizzly.utils.DelayedExecutor;

import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * FilterChain after being processed by SpdyAddOn:
 *
 * <pre>
 *     {@link org.glassfish.grizzly.filterchain.TransportFilter} <-> {@link SSLFilter}(1) <-> {@link FramesDecoderFilter} <-> {@link SpdyHandlerFilter}(2) <-> {@link HttpServerFilter}
 * </pre>
 * <ol>
 *     <li>SSLFilter is configured to use NPN for SPDY protocol negotiation</li>
 *     <li>FramesDecoderFilter and SpdyHandlerFilter replace {@link org.glassfish.grizzly.http.HttpServerFilter}</li>
 * </ol>
 *
 */
public class SpdyAddOn implements AddOn {

    private static final Logger LOGGER = Grizzly.logger(SpdyAddOn.class);


    // ------------------------------------------------------ Methods From AddOn


    @Override
    public void setup(NetworkListener networkListener, FilterChainBuilder builder) {
        if (!networkListener.isSecure()) {
            if (LOGGER.isLoggable(Level.WARNING)) {
                LOGGER.warning("Unable to configure spdy using a insecure NetworkListener");
            }
        }
        configureTransport(networkListener.getTransport());
        final DelayedExecutor executor = createDelayedExecutor(ThreadPoolConfig.newConfig().setMaxPoolSize(1024).setPoolName("SPDY"));
        processHttpServerFilter(builder, executor);
        processSslFilter(networkListener, builder);
        insertSpdyFilters(builder, executor);
        removeHttpServerCodecFilter(builder);

    }


    // ------------------------------------------------------- Protected Methods


    protected void configureTransport(final Transport transport) {
        transport.setIOStrategy(SameThreadIOStrategy.getInstance());
        transport.setWorkerThreadPoolConfig(null);
    }
    protected void removeHttpServerCodecFilter(FilterChainBuilder builder) {
        int idx = builder.indexOfType(org.glassfish.grizzly.http.HttpServerFilter.class);
        builder.remove(idx);
    }

    protected void insertSpdyFilters(FilterChainBuilder builder, DelayedExecutor executor) {
        int idx = builder.indexOfType(SSLFilter.class);
        builder.add(idx + 1, new FramesDecoderFilter());
        builder.add(idx + 2, new SpdyHandlerFilter(executor.getThreadPool()));
    }

    protected void processSslFilter(NetworkListener networkListener, FilterChainBuilder builder) {
        int idx = builder.indexOfType(SSLFilter.class);
        configureNpn((SSLFilter) builder.get(idx), networkListener.getTransport());
    }

    protected void processHttpServerFilter(FilterChainBuilder builder, DelayedExecutor executor) {
        int idx = builder.indexOfType(HttpServerFilter.class);
        final HttpServerFilter old = (HttpServerFilter) builder.get(idx);
        final HttpServerFilter newFilter = new HttpServerFilter(old.getConfiguration(), executor);
        newFilter.getMonitoringConfig().addProbes(old.getMonitoringConfig().getProbes());
        newFilter.setHttpHandler(old.getHttpHandler());
        builder.set(idx, newFilter);
    }

    protected DelayedExecutor createDelayedExecutor(final ThreadPoolConfig config) {
        return new DelayedExecutor(GrizzlyExecutorService.createInstance(config));
    }

    protected void configureNpn(final SSLFilter sslFilter, final Transport transport) {
        NextProtoNegSupport.getInstance().configure(sslFilter);
        NextProtoNegSupport.getInstance().setServerSideNegotiator(transport,
                new NextProtoNegSupport.ServerSideNegotiator() {

                    private final List<String> supportedProtocols = Arrays.asList("spdy/3", "http/1.1");

                    @Override
                    public List<String> supportedProtocols() {
                        return supportedProtocols;
                    }

                    @Override
                    public void onSuccess(String protocol) {
                        System.out.println("Success: " + protocol);
                    }

                    @Override
                    public void onNoDeal() {
                        System.out.println("NoDeal");
                    }
                });
    }

}
