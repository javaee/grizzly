/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011-2012 Oracle and/or its affiliates. All rights reserved.
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
package org.glassfish.grizzly.nio.transport;

import org.glassfish.grizzly.IOStrategy;
import org.glassfish.grizzly.nio.NIOTransportBuilder;
import org.glassfish.grizzly.nio.tmpselectors.TemporarySelectorIO;
import org.glassfish.grizzly.strategies.WorkerThreadIOStrategy;

/**
 * {@link NIOTransportBuilder} implementation for <code>TCP</code>.
 *
 * @since 2.0
 */
public class TCPNIOTransportBuilder extends NIOTransportBuilder<TCPNIOTransportBuilder> {


    protected TCPNIOTransport tcpTransport;

    // ------------------------------------------------------------ Constructors


    protected TCPNIOTransportBuilder(Class<? extends TCPNIOTransport> transportClass,
                                     IOStrategy strategy)
    throws IllegalAccessException, InstantiationException {
        super(transportClass, strategy);
        tcpTransport = (TCPNIOTransport) transport;
    }


    // ---------------------------------------------------------- Public Methods


    public static TCPNIOTransportBuilder newInstance() {
        try {
            return new TCPNIOTransportBuilder(TCPNIOTransport.class,
                                              WorkerThreadIOStrategy.getInstance());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }


    /**
     * @see TCPNIOTransport#getClientSocketSoTimeout()
     */
    public int getClientSocketSoTimeout() {
        return tcpTransport.getClientSocketSoTimeout();
    }

    /**
     * @see TCPNIOTransport#setClientSocketSoTimeout(int)
     *
     * @return this <code>TCPNIOTransportBuilder</code>
     */
    public TCPNIOTransportBuilder setClientSocketSoTimeout(int clientSocketSoTimeout) {
        tcpTransport.setClientSocketSoTimeout(clientSocketSoTimeout);
        return getThis();
    }

    /**
     * @see TCPNIOTransport#isKeepAlive() ()
     */
    public boolean isKeepAlive() {
        return tcpTransport.isKeepAlive();
    }

    /**
     * @see TCPNIOTransport#setKeepAlive(boolean)
     *
     * @return this <code>TCPNIOTransportBuilder</code>
     */
    public TCPNIOTransportBuilder setKeepAlive(boolean keepAlive) {
        tcpTransport.setKeepAlive(keepAlive);
        return getThis();
    }

    /**
     * @see TCPNIOTransport#getLinger()
     */
    public int getLinger() {
        return tcpTransport.getLinger();
    }

    /**
     * @see TCPNIOTransport#setLinger(int)
     *
     * @return this <code>TCPNIOTransportBuilder</code>
     */
    public TCPNIOTransportBuilder setLinger(int linger) {
        tcpTransport.setLinger(linger);
        return getThis();
    }

    /**
     * @see TCPNIOTransport#isReuseAddress()
     */
    public boolean isReuseAddress() {
        return tcpTransport.isReuseAddress();
    }

    /**
     * @see TCPNIOTransport#setReuseAddress(boolean)
     *
     * @return this <code>TCPNIOTransportBuilder</code>
     */
    public TCPNIOTransportBuilder setReuseAddress(boolean reuseAddress) {
        tcpTransport.setReuseAddress(reuseAddress);
        return getThis();
    }

    /**
     * @see TCPNIOTransport#getServerConnectionBackLog() ()
     */
    public int getServerConnectionBackLog() {
        return tcpTransport.getServerConnectionBackLog();
    }

    /**
     * @see TCPNIOTransport#setServerConnectionBackLog(int)
     *
     * @return this <code>TCPNIOTransportBuilder</code>
     */
    public TCPNIOTransportBuilder setServerConnectionBackLog(int serverConnectionBackLog) {
        tcpTransport.setServerConnectionBackLog(serverConnectionBackLog);
        return getThis();
    }

    /**
     * @see TCPNIOTransport#getServerSocketSoTimeout()
     */
    public int getServerSocketSoTimeout() {
        return tcpTransport.getServerSocketSoTimeout();
    }

    /**
     * @see TCPNIOTransport#setServerSocketSoTimeout(int)
     *
     * @return this <code>TCPNIOTransportBuilder</code>
     */
    public TCPNIOTransportBuilder setServerSocketSoTimeout(int serverSocketSoTimeout) {
        tcpTransport.setServerSocketSoTimeout(serverSocketSoTimeout);
        return getThis();
    }

    /**
     * @see TCPNIOTransport#isTcpNoDelay()
     */
    public boolean isTcpNoDelay() {
        return tcpTransport.isTcpNoDelay();
    }

    /**
     * @see TCPNIOTransport#setTcpNoDelay(boolean)
     *
     * @return this <code>TCPNIOTransportBuilder</code>
     */
    public TCPNIOTransportBuilder setTcpNoDelay(boolean tcpNoDelay) {
        tcpTransport.setTcpNoDelay(tcpNoDelay);
        return getThis();
    }
    
    /**
     * @see TCPNIOTransport#getTemporarySelectorIO()
     */
    public TemporarySelectorIO getTemporarySelectorIO() {
        return tcpTransport.getTemporarySelectorIO();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TCPNIOTransport build() {
        return (TCPNIOTransport) super.build();
    }

    // ------------------------------------------------------- Protected Methods


    @Override
    protected TCPNIOTransportBuilder getThis() {
        return this;
    }

}
