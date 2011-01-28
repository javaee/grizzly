/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011 Oracle and/or its affiliates. All rights reserved.
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
package org.glassfish.grizzly.aio.transport;

import org.glassfish.grizzly.IOStrategy;
import org.glassfish.grizzly.AIOTransportBuilder;
import org.glassfish.grizzly.strategies.WorkerThreadIOStrategy;

/**
 * {@link AIOTransportBuilder} implementation for <code>TCP</code>.
 *
 * @since 2.0
 */
public class TCPAIOTransportBuilder extends AIOTransportBuilder<TCPAIOTransportBuilder> {


    protected TCPAIOTransport tcpTransport;

    // ------------------------------------------------------------ Constructors


    protected TCPAIOTransportBuilder(Class<? extends TCPAIOTransport> transportClass,
                                     IOStrategy strategy)
    throws IllegalAccessException, InstantiationException {
        super(transportClass, strategy);
        tcpTransport = (TCPAIOTransport) transport;
    }


    // ---------------------------------------------------------- Public Methods


    public static TCPAIOTransportBuilder newInstance() {
        try {
            return new TCPAIOTransportBuilder(TCPAIOTransport.class,
                                              WorkerThreadIOStrategy.getInstance());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }


    /**
     * @see TCPAIOTransport#getClientSocketSoTimeout()
     */
    public int getClientSocketSoTimeout() {
        return tcpTransport.getClientSocketSoTimeout();
    }

    /**
     * @see TCPAIOTransport#setClientSocketSoTimeout(int)
     *
     * @return this <code>TCPAIOTransportBuilder</code>
     */
    public TCPAIOTransportBuilder setClientSocketSoTimeout(int clientSocketSoTimeout) {
        tcpTransport.setClientSocketSoTimeout(clientSocketSoTimeout);
        return getThis();
    }

    /**
     * @see TCPAIOTransport#getConnectionTimeout()
     */
    public int getConnectionTimeout() {
        return tcpTransport.getConnectionTimeout();
    }

    /**
     * @see TCPAIOTransport#setConnectionTimeout(int)
     *
     * @return this <code>TCPAIOTransportBuilder</code>
     */
    public TCPAIOTransportBuilder setConnectionTimeout(int connectionTimeout) {
        tcpTransport.setConnectionTimeout(connectionTimeout);
        return getThis();
    }

    /**
     * @see TCPAIOTransport#isKeepAlive() ()
     */
    public boolean isKeepAlive() {
        return tcpTransport.isKeepAlive();
    }

    /**
     * @see TCPAIOTransport#setKeepAlive(boolean)
     *
     * @return this <code>TCPAIOTransportBuilder</code>
     */
    public TCPAIOTransportBuilder setKeepAlive(boolean keepAlive) {
        tcpTransport.setKeepAlive(keepAlive);
        return getThis();
    }

    /**
     * @see TCPAIOTransport#getLinger()
     */
    public int getLinger() {
        return tcpTransport.getLinger();
    }

    /**
     * @see TCPAIOTransport#setLinger(int)
     *
     * @return this <code>TCPAIOTransportBuilder</code>
     */
    public TCPAIOTransportBuilder setLinger(int linger) {
        tcpTransport.setLinger(linger);
        return getThis();
    }

    /**
     * @see TCPAIOTransport#isReuseAddress()
     */
    public boolean isReuseAddress() {
        return tcpTransport.isReuseAddress();
    }

    /**
     * @see TCPAIOTransport#setReuseAddress(boolean)
     *
     * @return this <code>TCPAIOTransportBuilder</code>
     */
    public TCPAIOTransportBuilder setReuseAddress(boolean reuseAddress) {
        tcpTransport.setReuseAddress(reuseAddress);
        return getThis();
    }

    /**
     * @see TCPAIOTransport#getServerConnectionBackLog() ()
     */
    public int getServerConnectionBackLog() {
        return tcpTransport.getServerConnectionBackLog();
    }

    /**
     * @see TCPAIOTransport#setServerConnectionBackLog(int)
     *
     * @return this <code>TCPAIOTransportBuilder</code>
     */
    public TCPAIOTransportBuilder setServerConnectionBackLog(int serverConnectionBackLog) {
        tcpTransport.setServerConnectionBackLog(serverConnectionBackLog);
        return getThis();
    }

    /**
     * @see TCPAIOTransport#getServerSocketSoTimeout()
     */
    public int getServerSocketSoTimeout() {
        return tcpTransport.getServerSocketSoTimeout();
    }

    /**
     * @see TCPAIOTransport#setServerSocketSoTimeout(int)
     *
     * @return this <code>TCPAIOTransportBuilder</code>
     */
    public TCPAIOTransportBuilder setServerSocketSoTimeout(int serverSocketSoTimeout) {
        tcpTransport.setServerSocketSoTimeout(serverSocketSoTimeout);
        return getThis();
    }

    /**
     * @see TCPAIOTransport#isTcpNoDelay()
     */
    public boolean isTcpNoDelay() {
        return tcpTransport.isTcpNoDelay();
    }

    /**
     * @see TCPAIOTransport#setTcpNoDelay(boolean)
     *
     * @return this <code>TCPAIOTransportBuilder</code>
     */
    public TCPAIOTransportBuilder setTcpNoDelay(boolean tcpNoDelay) {
        tcpTransport.setTcpNoDelay(tcpNoDelay);
        return getThis();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TCPAIOTransport build() {
        return (TCPAIOTransport) super.build();
    }

    // ------------------------------------------------------- Protected Methods


    @Override
    protected TCPAIOTransportBuilder getThis() {
        return this;
    }

}
