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
import java.io.IOException;
import org.jvnet.hk2.component.Habitat;
import java.util.logging.Level;

/**
 * Implementation of Grizzly embedded HTTPS listener
 *
 * @author Jeanfrancois Arcand
 * @author Alexey Stashok
 */
public class GrizzlyEmbeddedHttps extends GrizzlyEmbeddedHttp {

    private ProtocolFilter lazyInitializationFilter;

    private final SSLConfigHolder sslConfigHolder = new SSLConfigHolder();
    
    public GrizzlyEmbeddedHttps(GrizzlyServiceListener grizzlyServiceListener) {
        super(grizzlyServiceListener);
    }
    // ---------------------------------------------------------------------/.

    @Override
    protected ProtocolChainInstanceHandler configureProtocol(
            NetworkListener networkListener, Protocol protocol, Habitat habitat,
            boolean mayEnableComet) {
        if (protocol.getHttp() != null && GrizzlyConfig.toBoolean(protocol.getSecurityEnabled())) {
            final Ssl ssl = protocol.getSsl();

            if (ssl == null || Boolean.parseBoolean(ssl.getAllowLazyInit())) {
                logger.log(Level.INFO, "Perform lazy SSL initialization for the listener '" + networkListener.getName() + "'");
                lazyInitializationFilter = new LazySSLInitializationFilter(protocol.getSsl());
            } else {
                if (SSLConfigHolder.configureSSL(protocol.getSsl(), sslConfigHolder)) {
                    setHttpSecured(true);
                }
            }
        }

        return super.configureProtocol(networkListener, protocol, habitat,
                mayEnableComet);
    }
    
    /**
     * {@inheritDoc}
     */
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
            return new SSLAsyncProtocolFilter(algorithmClass, port,
                    sslConfigHolder.getSSLImplementation());
        } else {
            return new SSLDefaultProtocolFilter(algorithmClass, port,
                    sslConfigHolder.getSSLImplementation());
        }
    }

    /**
     * {@inheritDoc}
     */
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
        return readFilter;
    }

    /**
     * Create <code>SSLProcessorTask</code> objects and configure it to be ready to proceed request.
     */
    @Override
    protected ProcessorTask newProcessorTask(final boolean initialize) {
        SSLProcessorTask t = (asyncExecution
            ? new SSLAsyncProcessorTask(initialize, getBufferResponse())
            : new SSLProcessorTask(initialize, getBufferResponse()));
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
            SSLConfigHolder.configureSSL(ssl, sslConfigHolder);
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
