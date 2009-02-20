/*
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License).  You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the license at
 * https://glassfish.dev.java.net/public/CDDLv1.0.html or
 * glassfish/bootstrap/legal/CDDLv1.0.txt.
 * See the License for the specific language governing
 * permissions and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL
 * Header Notice in each file and include the License file
 * at glassfish/bootstrap/legal/CDDLv1.0.txt.
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * you own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 *
 * Copyright 2006 Sun Microsystems, Inc. All rights reserved.
 */
package com.sun.grizzly.config;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

import com.sun.grizzly.DefaultProtocolChainInstanceHandler;
import com.sun.grizzly.ProtocolChain;
import com.sun.grizzly.ProtocolFilter;
import com.sun.grizzly.UDPSelectorHandler;
import com.sun.grizzly.filter.ReadFilter;
import com.sun.grizzly.http.HttpProtocolChain;
import com.sun.grizzly.http.SelectorThread;

/**
 * Implementation of Grizzly embedded HTTP listener
 *
 * @author Jeanfrancois Arcand
 * @author Alexey Stashok
 * @author Justin Lee
 */
public class GrizzlyEmbeddedHttp extends SelectorThread {
    private volatile ProtocolFilter httpProtocolFilterWrapper;
    private final AtomicBoolean algorithmInitialized = new AtomicBoolean(false);
    private volatile Collection<ProtocolFilter> defaultHttpFilters;
    private boolean isHttpSecured = false;
    private UDPSelectorHandler udpSelectorHandler;
    private static final Object LOCK_OBJECT = new Object();
    public static String DEFAULT_ALGORITHM_CLASS_NAME = DEFAULT_ALGORITHM;

    /**
     * Constructor
     */
    public GrizzlyEmbeddedHttp(final GrizzlyMappingAdapter httpAdapter) {
        adapter = httpAdapter;
        setClassLoader(getClass().getClassLoader());
    }

    @Override
    public GrizzlyMappingAdapter getAdapter() {
        return (GrizzlyMappingAdapter)super.getAdapter();
    }

    /**
     * Load using reflection the <code>Algorithm</code> class.
     */
    @Override
    protected void initAlgorithm() {
        if (!algorithmInitialized.getAndSet(true)) {
            try {
                algorithmClass = Class.forName(DEFAULT_ALGORITHM_CLASS_NAME);
                defaultAlgorithmInstalled = true;
            } catch (ClassNotFoundException e) {
                logger.severe(e.getMessage());
                super.initAlgorithm();
            }
        }
    }

    /**
     * Initialize the Grizzly Framework classes.
     */
    @Override
    protected void initController() {
        super.initController();
        // Re-start problem when set to true as of 04/18.
        //selectorHandler.setReuseAddress(false);
        final DefaultProtocolChainInstanceHandler instanceHandler = new DefaultProtocolChainInstanceHandler() {
            private final ConcurrentLinkedQueue<ProtocolChain> chains =
                new ConcurrentLinkedQueue<ProtocolChain>();

            /**
             * Always return instance of ProtocolChain.
             */
            @Override
            public ProtocolChain poll() {
                ProtocolChain protocolChain = chains.poll();
                if (protocolChain == null) {
                    protocolChain = new HttpProtocolChain();
                    ((HttpProtocolChain) protocolChain).enableRCM(rcmSupport);
                    configureFilters(protocolChain);
                }
                return protocolChain;
            }

            /**
             * Pool an instance of ProtocolChain.
             */
            @Override
            public boolean offer(final ProtocolChain instance) {
                return chains.offer(instance);
            }
        };
        controller.setProtocolChainInstanceHandler(instanceHandler);
        controller.setReadThreadsCount(readThreadsCount);
        // Suport UDP only when port unification is enabled.
        if (portUnificationFilter != null) {
            controller.addSelectorHandler(createUDPSelectorHandler());
        }
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                stopEndpoint();
            }
        });
    }

    @Override
    public void stopEndpoint() {
        try {
            super.stopEndpoint();
        } catch (Throwable t) {
            logger.log(Level.SEVERE, "Unable to stop properly", t);
        } finally {
            // Force the Selector(s) to be closed in case an unexpected
            // exception occured during shutdown.
            try {
                if (selectorHandler != null
                    && selectorHandler.getSelector() != null) {
                    selectorHandler.getSelector().close();
                }
            } catch (IOException ex) {
            }
            try {
                if (udpSelectorHandler != null
                    && udpSelectorHandler.getSelector() != null) {
                    udpSelectorHandler.getSelector().close();
                }
            } catch (IOException ex) {
            }
        }

    }

    protected Collection<ProtocolFilter> getDefaultHttpProtocolFilters() {
        if (defaultHttpFilters == null) {
            synchronized (LOCK_OBJECT) {
                if (defaultHttpFilters == null) {
                    final Collection<ProtocolFilter> tmpList = new ArrayList<ProtocolFilter>(4);
                    if (rcmSupport) {
                        tmpList.add(createRaFilter());
                    }
                    tmpList.add(createHttpParserFilter());
                    defaultHttpFilters = tmpList;
                }
            }
        }
        return defaultHttpFilters;
    }

    /**
     * Create ReadFilter
     *
     * @return read filter
     */
    protected ProtocolFilter createReadFilter() {
        final ReadFilter readFilter = new ReadFilter();
        readFilter.setContinuousExecution(
            Boolean.valueOf(System.getProperty("v3.grizzly.readFilter.continuousExecution", "false")));
        return readFilter;
    }

    /**
     * Create the HttpProtocolFilter, which is aware of EmbeddedHttp's context-root<->adapter map.
     */
    protected HttpProtocolFilter getHttpProtocolFilter() {
        synchronized (LOCK_OBJECT) {
            if (httpProtocolFilterWrapper == null) {
                initAlgorithm();
                final ProtocolFilter wrappedFilter = createHttpParserFilter();
                httpProtocolFilterWrapper = new HttpProtocolFilter(wrappedFilter, this);
            }
        }
        return (HttpProtocolFilter) httpProtocolFilterWrapper;
    }

    /**
     * Create <code>TCPSelectorHandler</code>
     */
    protected UDPSelectorHandler createUDPSelectorHandler() {
        if (udpSelectorHandler == null) {
            udpSelectorHandler = new UDPSelectorHandler();
            udpSelectorHandler.setPort(port);
            udpSelectorHandler.setThreadPool(threadPool);
        }
        return udpSelectorHandler;
    }

    /**
     * Configure <code>TCPSelectorHandler</code>
     */
    protected void configureSelectorHandler(final UDPSelectorHandler selectorHandler) {
        selectorHandler.setPort(port);
        selectorHandler.setReuseAddress(getReuseAddress());
        selectorHandler.setThreadPool(threadPool);
    }
    // ---------------------------------------------- Public get/set ----- //

    public boolean isHttpSecured() {
        return isHttpSecured;
    }

    public void setHttpSecured(final boolean httpSecured) {
        isHttpSecured = httpSecured;
    }
}
