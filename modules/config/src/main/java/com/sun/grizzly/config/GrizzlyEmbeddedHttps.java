/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2009-2011 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.grizzly.config;

import com.sun.grizzly.Context;
import com.sun.grizzly.ProtocolChain;
import com.sun.grizzly.ProtocolChainInstanceHandler;
import com.sun.grizzly.ProtocolChainInstruction;
import com.sun.grizzly.ProtocolFilter;
import com.sun.grizzly.TCPSelectorHandler;
import com.sun.grizzly.config.dom.NetworkListener;
import com.sun.grizzly.config.dom.Protocol;
import com.sun.grizzly.config.dom.Ssl;
import com.sun.grizzly.filter.SSLReadFilter;
import com.sun.grizzly.http.ProcessorTask;
import com.sun.grizzly.ssl.SSLAsyncProcessorTask;
import com.sun.grizzly.ssl.SSLAsyncProtocolFilter;
import com.sun.grizzly.ssl.SSLDefaultProtocolFilter;
import com.sun.grizzly.ssl.SSLProcessorTask;
import com.sun.grizzly.ssl.SSLSelectorThreadHandler;
import org.jvnet.hk2.component.Habitat;

import javax.net.ssl.SSLException;
import java.io.IOException;
import java.util.logging.Level;

/**
 * Implementation of Grizzly embedded HTTPS listener
 *
 * @author Jeanfrancois Arcand
 * @author Alexey Stashok
 */
public class GrizzlyEmbeddedHttps extends GrizzlyEmbeddedHttp {

    private ProtocolFilter lazyInitializationFilter;

    private volatile boolean isSslInitialized;
    private volatile SSLConfigHolder sslConfigHolder;

    @Override
    protected ProtocolChainInstanceHandler configureProtocol(NetworkListener networkListener, Protocol protocol,
            Habitat habitat, boolean mayEnableAsync) {
        if (protocol.getHttp() != null && GrizzlyConfig.toBoolean(protocol.getSecurityEnabled())) {
            Ssl ssl = protocol.getSsl();

            if (ssl == null) {
                ssl = (Ssl) DefaultProxy.createDummyProxy(protocol, Ssl.class);
            }
            try {
                sslConfigHolder = new SSLConfigHolder(habitat, ssl);
            } catch (SSLException e) {
                throw new IllegalStateException(e);
            }

            if (Boolean.parseBoolean(ssl.getAllowLazyInit())) {
                if (logger.isLoggable(Level.FINE)) {
                    logger.log(Level.FINE,
                               "Perform lazy SSL initialization for the listener ''{0}''",
                               networkListener.getName());
                }
                lazyInitializationFilter = new LazySSLInitializationFilter(ssl);
            } else {
                isSslInitialized = true;
                if (sslConfigHolder.configureSSL()) {
                    setHttpSecured(true);
                }
            }
        }

        return super.configureProtocol(networkListener, protocol, habitat, mayEnableAsync);
    }

    @Override
    protected TCPSelectorHandler createSelectorHandler() {
        return new SSLSelectorThreadHandler(this);
    }

    /**
     * Create HTTP parser <code>ProtocolFilter</code>
     *
     * @return HTTP parser <code>ProtocolFilter</code>
     */
    @Override
    protected ProtocolFilter createHttpParserFilter() {
        if (asyncExecution) {
            return new SSLAsyncProtocolFilter(algorithmClass,
                    inet,
                    port,
                    sslConfigHolder.getSSLImplementation());
        } else {
            return new SSLDefaultProtocolFilter(algorithmClass,
                    inet,
                    port,
                    sslConfigHolder.getSSLImplementation());
        }
    }

    @Override
    protected void configureFilters(final ProtocolChain protocolChain) {
        if (lazyInitializationFilter != null) {
            protocolChain.addFilter(lazyInitializationFilter);
        } else {
            doConfigureFilters(protocolChain);
        }
    }

    private void doConfigureFilters(final ProtocolChain protocolChain) {
        if (portUnificationFilter != null) {
            portUnificationFilter.setContinuousExecution(false);
            protocolChain.addFilter(portUnificationFilter);
        } else {
            protocolChain.addFilter(createReadFilter());
        }

        protocolChain.addFilter(createHttpParserFilter());
    }

    /**
     * Create and configure <code>SSLReadFilter</code>
     *
     * @return <code>SSLReadFilter</code>
     */
    @Override
    protected ProtocolFilter createReadFilter() {
        final SSLReadFilter readFilter = new SSLReadFilter();
        readFilter.setSSLContext(sslConfigHolder.getSSLContext());
        readFilter.setClientMode(sslConfigHolder.isClientMode());
        readFilter.setEnabledCipherSuites(sslConfigHolder.getEnabledCipherSuites());
        readFilter.setEnabledProtocols(sslConfigHolder.getEnabledProtocols());
        readFilter.setNeedClientAuth(sslConfigHolder.isNeedClientAuth());
        readFilter.setWantClientAuth(sslConfigHolder.isWantClientAuth());
        readFilter.setSslActivityTimeout(sslConfigHolder.getSslInactivityTimeout());
        return readFilter;
    }

    /**
     * Create <code>SSLProcessorTask</code> objects and configure it to be ready to proceed request.
     */
    @Override
    protected ProcessorTask newProcessorTask(final boolean initialize) {
        SSLProcessorTask t = asyncExecution
                ? new SSLAsyncProcessorTask(initialize, getBufferResponse())
                : new SSLProcessorTask(initialize, getBufferResponse());
        configureProcessorTask(t);
        return t;
    }

    /**
     * Lazy SSL initialization filter
     */
    public class LazySSLInitializationFilter implements ProtocolFilter {
        private final Ssl ssl;

        public LazySSLInitializationFilter(Ssl ssl) {
            this.ssl = ssl;
        }

        public boolean execute(Context ctx) throws IOException {
            final ProtocolChain chain = ctx.getProtocolChain();

            synchronized (ssl) {
                if (!isSslInitialized) {
                    isSslInitialized = true;
                sslConfigHolder.configureSSL();
            }
            }
            doConfigureFilters(chain);

            return true;
        }

        public boolean postExecute(Context ctx) throws IOException {
            final ProtocolChain chain = ctx.getProtocolChain();
            chain.removeFilter(this);

            ctx.setAttribute(ProtocolChain.PROTOCOL_CHAIN_POST_INSTRUCTION,
                    ProtocolChainInstruction.REINVOKE);
            return true;
        }
    }
}
