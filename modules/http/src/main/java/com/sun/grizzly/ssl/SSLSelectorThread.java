/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2007-2010 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.grizzly.ssl;

import com.sun.grizzly.ProtocolChain;
import com.sun.grizzly.ProtocolFilter;
import com.sun.grizzly.SSLConfig;
import com.sun.grizzly.TCPSelectorHandler;
import com.sun.grizzly.filter.SSLReadFilter;
import com.sun.grizzly.http.FileCacheFactory;
import com.sun.grizzly.http.ProcessorTask;
import com.sun.grizzly.http.SelectorThread;
import com.sun.grizzly.util.net.SSLImplementation;
import javax.net.ssl.SSLContext;

/**
 * SSL over NIO {@link java.nio.channels.Selector} implementation. Mainly, this class
 * replace the clear text implementation by defining the SSL tasks counterpart:
 * SSLReadTask, SSLProcessorTask and SSLByteBufferInputStream.
 *
 * @author Jean-Francois Arcand
 */
public class SSLSelectorThread extends SelectorThread {
    
    
    /**
     * The {@link SSLImplementation} 
     */
    private SSLImplementation sslImplementation;
    
    
    /**
     * The {@link SSLContext} associated with the SSL implementation
     * we are running on.
     */
    protected SSLContext sslContext;
    
    
    /**
     * The list of cipher suite
     */
    private String[] enabledCipherSuites = null;
    
    
    /**
     * the list of protocols
     */
    private String[] enabledProtocols = null;
    
    
    /**
     * Client mode when handshaking.
     */
    private boolean clientMode = false;
    
    
    /**
     * Require client Authentication.
     */
    private boolean needClientAuth = false;
    
    
    /** 
     * True when requesting authentication.
     */
    private boolean wantClientAuth = false;    
    
    // ---------------------------------------------------------------------/.
    
    /**
     * {@inheritDoc}
     */
    @Override
    protected TCPSelectorHandler createSelectorHandler() {
        return new SSLSelectorThreadHandler(this);
    }

    /**
     * Create HTTP parser {@link ProtocolFilter}
     * @return HTTP parser {@link ProtocolFilter}
     */
    @Override
    protected ProtocolFilter createHttpParserFilter() {
        if (asyncExecution){
            return new SSLAsyncProtocolFilter(algorithmClass, inet, port, sslImplementation);
        } else {
            return new SSLDefaultProtocolFilter(algorithmClass, inet, port, sslImplementation);
        }
    }

    /**
     * Create and configure {@link SSLReadFilter}
     * @return {@link SSLReadFilter}
     */
    private ProtocolFilter createSSLReadFilter() {
        SSLReadFilter readFilter = new SSLReadFilter();
        readFilter.setSSLContext(sslContext);
        readFilter.setClientMode(clientMode);
        readFilter.setEnabledCipherSuites(enabledCipherSuites);
        readFilter.setEnabledProtocols(enabledProtocols);
        readFilter.setNeedClientAuth(needClientAuth);
        readFilter.setWantClientAuth(wantClientAuth);
        return readFilter;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void configureFilters(ProtocolChain protocolChain) {
        if (portUnificationFilter != null) {
            portUnificationFilter.setContinuousExecution(true);
            protocolChain.addFilter(portUnificationFilter);
        }
        
        protocolChain.addFilter(createSSLReadFilter());
        
        if (rcmSupport){
            protocolChain.addFilter(createRaFilter());
        }
        
        protocolChain.addFilter(createHttpParserFilter());
    }

    /**
     * Create {@link SSLProcessorTask} objects and configure it to be ready
     * to proceed request.
     */
    @Override
    protected ProcessorTask newProcessorTask(boolean initialize){                                                      
        SSLProcessorTask task = null;
        if (!asyncExecution) {
            task = new SSLProcessorTask(initialize, getBufferResponse());
        } else {
            task = new SSLAsyncProcessorTask(initialize, getBufferResponse());
        }      
        return configureProcessorTask(task);        
    }
    
    
    /**
     * Set the SSLContext required to support SSL over NIO.
     */
    public void setSSLConfig(SSLConfig sslConfig) {
        this.sslContext = sslConfig.createSSLContext();
    }
    
    /**
     * Set the SSLContext required to support SSL over NIO.
     */
    public void setSSLContext(SSLContext sslContext){
        this.sslContext = sslContext;
    }
 
    
    /**
     * Return the SSLContext required to support SSL over NIO.
     */    
    public SSLContext getSSLContext(){
        return sslContext;
    }
    
    
    /**
     * Set the Coyote SSLImplementation.
     */
    public void setSSLImplementation(SSLImplementation sslImplementation){
        this.sslImplementation = sslImplementation;
    }   

    
    /**
     * Return the current {@link SSLImplementation} this Thread
     */
    public SSLImplementation getSSLImplementation() {
        return sslImplementation;
    } 
    
    /**
     * Returns the list of cipher suites to be enabled when {@link javax.net.ssl.SSLEngine}
     * is initialized.
     * 
     * @return <tt>null</tt> means 'use {@link javax.net.ssl.SSLEngine}'s default.'
     */
    public String[] getEnabledCipherSuites() {
        return enabledCipherSuites;
    }

    
    /**
     * Sets the list of cipher suites to be enabled when {@link javax.net.ssl.SSLEngine}
     * is initialized.
     * 
     * @param enabledCipherSuites <tt>null</tt> means 'use
     *  {@link javax.net.ssl.SSLEngine}'s default.'
     */
    public void setEnabledCipherSuites(String[] enabledCipherSuites) {
        this.enabledCipherSuites = enabledCipherSuites;
    }

   
    /**
     * Returns the list of protocols to be enabled when {@link javax.net.ssl.SSLEngine}
     * is initialized.
     * 
     * @return <tt>null</tt> means 'use {@link javax.net.ssl.SSLEngine}'s default.'
     */  
    public String[] getEnabledProtocols() {
        return enabledProtocols;
    }

    
    /**
     * Sets the list of protocols to be enabled when {@link javax.net.ssl.SSLEngine}
     * is initialized.
     * 
     * @param enabledProtocols <tt>null</tt> means 'use {@link javax.net.ssl.SSLEngine}'s default.'
     */    
    public void setEnabledProtocols(String[] enabledProtocols) {
        this.enabledProtocols = enabledProtocols;
    }

    
    /**
     * Returns <tt>true</tt> if the SSlEngine is set to use client mode
     * when handshaking.
     * @return is client mode enabled
     */
    public boolean isClientMode() {
        return clientMode;
    }


    /**
     * Configures the engine to use client (or server) mode when handshaking.
     */    
    public void setClientMode(boolean clientMode) {
        this.clientMode = clientMode;
    }

    
    /**
     * Returns <tt>true</tt> if the SSLEngine will <em>require</em> 
     * client authentication.
     */   
    public boolean isNeedClientAuth() {
        return needClientAuth;
    }

    
    /**
     * Configures the engine to <em>require</em> client authentication.
     */    
    public void setNeedClientAuth(boolean needClientAuth) {
        this.needClientAuth = needClientAuth;
    }

    
    /**
     * Returns <tt>true</tt> if the engine will <em>request</em> client 
     * authentication.
     */   
    public boolean isWantClientAuth() {
        return wantClientAuth;
    }

    
    /**
     * Configures the engine to <em>request</em> client authentication.
     */    
    public void setWantClientAuth(boolean wantClientAuth) {
        this.wantClientAuth = wantClientAuth;
    }
    
    
    /**
     * Initialize the fileCacheFactory associated with this instance
     */
    @Override
    protected void initFileCacheFactory(){
        fileCacheFactory = createFileCacheFactory();
        configureFileCacheFactory();
    }

    /**
     * Create SSL aware {@link FileCacheFactory}
     */
    @Override
    protected FileCacheFactory createFileCacheFactory() {
        return SSLFileCacheFactory.getFactory(port);
    }
}
