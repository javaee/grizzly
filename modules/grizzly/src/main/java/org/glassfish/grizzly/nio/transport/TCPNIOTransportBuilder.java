/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011-2014 Oracle and/or its affiliates. All rights reserved.
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

import org.glassfish.grizzly.NIOTransportBuilder;
import org.glassfish.grizzly.nio.NIOTransport;

/**
 * {@link NIOTransportBuilder} implementation for <code>TCP</code>.
 *
 * @since 2.0
 */
@SuppressWarnings("ALL")
public class TCPNIOTransportBuilder extends NIOTransportBuilder<TCPNIOTransportBuilder> {

    protected boolean keepAlive = TCPNIOTransport.DEFAULT_KEEP_ALIVE;
    protected int linger = TCPNIOTransport.DEFAULT_LINGER;
    protected int serverConnectionBackLog = TCPNIOTransport.DEFAULT_SERVER_CONNECTION_BACKLOG;
    protected int serverSocketSoTimeout = TCPNIOTransport.DEFAULT_SERVER_SOCKET_SO_TIMEOUT;
    protected boolean tcpNoDelay = TCPNIOTransport.DEFAULT_TCP_NO_DELAY;

    // ------------------------------------------------------------ Constructors


    protected TCPNIOTransportBuilder(Class<? extends TCPNIOTransport> transportClass) {
        super(transportClass);
    }


    // ---------------------------------------------------------- Public Methods


    public static TCPNIOTransportBuilder newInstance() {
        return new TCPNIOTransportBuilder(TCPNIOTransport.class);
    }

    /**
     * @see TCPNIOTransport#isKeepAlive() ()
     */
    public boolean isKeepAlive() {
        return keepAlive;
    }

    /**
     * @see TCPNIOTransport#setKeepAlive(boolean)
     *
     * @return this <code>TCPNIOTransportBuilder</code>
     */
    public TCPNIOTransportBuilder setKeepAlive(boolean keepAlive) {
        this.keepAlive = keepAlive;
        return getThis();
    }

    /**
     * @see TCPNIOTransport#getLinger()
     */
    public int getLinger() {
        return linger;
    }

    /**
     * @see TCPNIOTransport#setLinger(int)
     *
     * @return this <code>TCPNIOTransportBuilder</code>
     */
    public TCPNIOTransportBuilder setLinger(int linger) {
        this.linger = linger;
        return getThis();
    }

    /**
     * @see TCPNIOTransport#getServerConnectionBackLog() ()
     */
    public int getServerConnectionBackLog() {
        return serverConnectionBackLog;
    }

    /**
     * @see TCPNIOTransport#setServerConnectionBackLog(int)
     *
     * @return this <code>TCPNIOTransportBuilder</code>
     */
    public TCPNIOTransportBuilder setServerConnectionBackLog(int serverConnectionBackLog) {
        this.serverConnectionBackLog = serverConnectionBackLog;
        return getThis();
    }

    /**
     * @see TCPNIOTransport#getServerSocketSoTimeout()
     */
    public int getServerSocketSoTimeout() {
        return serverSocketSoTimeout;
    }

    /**
     * @see TCPNIOTransport#setServerSocketSoTimeout(int)
     *
     * @return this <code>TCPNIOTransportBuilder</code>
     */
    public TCPNIOTransportBuilder setServerSocketSoTimeout(int serverSocketSoTimeout) {
        this.serverSocketSoTimeout = serverSocketSoTimeout;
        return getThis();
    }

    /**
     * @see TCPNIOTransport#isTcpNoDelay()
     */
    public boolean isTcpNoDelay() {
        return tcpNoDelay;
    }

    /**
     * @see TCPNIOTransport#setTcpNoDelay(boolean)
     *
     * @return this <code>TCPNIOTransportBuilder</code>
     */
    public TCPNIOTransportBuilder setTcpNoDelay(boolean tcpNoDelay) {
        this.tcpNoDelay = tcpNoDelay;
        return getThis();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TCPNIOTransport build() {
        TCPNIOTransport transport = (TCPNIOTransport) super.build();
        transport.setKeepAlive(keepAlive);
        transport.setLinger(linger);
        transport.setServerConnectionBackLog(serverConnectionBackLog);
        transport.setTcpNoDelay(tcpNoDelay);
        transport.setServerSocketSoTimeout(serverSocketSoTimeout);
        return transport;
    }

    // ------------------------------------------------------- Protected Methods


    @Override
    protected TCPNIOTransportBuilder getThis() {
        return this;
    }

    @Override
    protected NIOTransport create(final String name) {
        return new TCPNIOTransport(name);
    }
}
