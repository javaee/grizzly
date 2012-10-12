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
import org.glassfish.grizzly.NIOTransportBuilder;
import org.glassfish.grizzly.nio.tmpselectors.TemporarySelectorIO;
import org.glassfish.grizzly.strategies.WorkerThreadIOStrategy;


/**
 * {@link NIOTransportBuilder} implementation for <code>UDP</code>.
 *
 * @since 2.0
 */
public class UDPNIOTransportBuilder extends NIOTransportBuilder<UDPNIOTransportBuilder> {

    protected UDPNIOTransport udpTransport;

    // ------------------------------------------------------------ Constructors


    protected UDPNIOTransportBuilder(Class<? extends UDPNIOTransport> transportClass,
                                     IOStrategy strategy)
    throws IllegalAccessException, InstantiationException {
        super(transportClass, strategy);
        udpTransport = (UDPNIOTransport) transport;
    }


    // ---------------------------------------------------------- Public Methods

    public static UDPNIOTransportBuilder newInstance() {
        try {
            return new UDPNIOTransportBuilder(UDPNIOTransport.class,
                                              WorkerThreadIOStrategy.getInstance());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }


    /**
     * @see UDPNIOTransport#getConnectionTimeout
     */
    public int getConnectionTimeout() {
        return udpTransport.getConnectionTimeout();
    }

    /**
     * @see UDPNIOTransport#setConnectionTimeout(int)
     *
     * @return this <code>UDPNIOTransport</code>
     */
    public UDPNIOTransportBuilder setConnectionTimeout(int connectionTimeout) {
        udpTransport.setConnectionTimeout(connectionTimeout);
        return getThis();
    }

    /**
     * @see UDPNIOTransport#isReuseAddress()
     */
    public boolean isReuseAddress() {
        return udpTransport.isReuseAddress();
    }

    /**
     * @see UDPNIOTransport#setReuseAddress(boolean)
     *
     * @return this <code>UDPNIOTransport</code>
     */
    public UDPNIOTransportBuilder setReuseAddress(boolean reuseAddress) {
        udpTransport.setReuseAddress(reuseAddress);
        return getThis();
    }

    /**
     * @see UDPNIOTransport#getTemporarySelectorIO()
     */
    public TemporarySelectorIO getTemporarySelectorIO() {
        return udpTransport.getTemporarySelectorIO();
    }

    /**
     * @see org.glassfish.grizzly.asyncqueue.AsyncQueueWriter#getMaxPendingBytesPerConnection()
     * 
     * Note: the value is per connection, not transport total.
     */
    public int getMaxAsyncWriteQueueSizeInBytes() {
        return udpTransport.getAsyncQueueIO()
                .getWriter().getMaxPendingBytesPerConnection();
    }
    
    /**
     * @see org.glassfish.grizzly.asyncqueue.AsyncQueueWriter#setMaxPendingBytesPerConnection(int)
     * 
     * Note: the value is per connection, not transport total.
     *
     * @return this <code>UDPNIOTransportBuilder</code>
     */
    public UDPNIOTransportBuilder setMaxAsyncWriteQueueSizeInBytes(
            final int size) {
        udpTransport.getAsyncQueueIO()
                .getWriter().setMaxPendingBytesPerConnection(size);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public UDPNIOTransport build() {
        return (UDPNIOTransport) super.build();
    }


    // ------------------------------------------------------- Protected Methods


    @Override
    protected UDPNIOTransportBuilder getThis() {
        return this;
    }

}
