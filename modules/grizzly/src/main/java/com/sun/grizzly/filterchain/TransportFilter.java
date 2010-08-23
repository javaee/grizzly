/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2010 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.grizzly.filterchain;

import com.sun.grizzly.Transport;
import java.io.IOException;
import com.sun.grizzly.Connection;

/**
 * Transport {@link Filter} implementation, which should work with any
 * {@link Transport}. This {@link Filter} tries to delegate I/O event processing
 * to the {@link Transport}'s specific transport {@link Filter}. If
 * {@link Transport} doesn't have own implementation - uses common I/O event
 * processing logic.
 * <tt>TransportFilter</tt> could be set to work in 2 modes: {@link Mode#Stream}
 * or {@link Mode#Message}. In the {@link Mode#Stream} mode,
 * <tt>TransportFilter</tt> produces/consumes {@link Connection} data using
 * {@link FilterChainContext#getStreamReader()}, {@link FilterChainContext#getStreamWriter()}.
 * In the {@link Mode#Message} mode, <tt>TransportFilter</tt> represents {@link Connection}
 * data as {@link Buffer}, using {@link FilterChainContext#getMessage(}},
 * {@link FilterChainContext#setMessage()}.
 * For specific {@link Transport}, one mode could be more preferable than another.
 * For example {@link TCPNIOTransport} works just in {@link Mode#Stream} mode,
 * {@link UDPNIOTransport} prefers {@link Mode#Message} mode, but could also work
 * in {@link Mode#Stream} mode.
 * 
 * @author Alexey Stashok
 */
public final class TransportFilter extends BaseFilter {
    public static final String WORKER_THREAD_BUFFER_NAME = "thread-buffer";
    /**
     * Create <tt>TransportFilter</tt>.
     */
    public TransportFilter() {
    }

    /**
     * Delegates accept operation to {@link Transport}'s specific transport
     * filter.
     */
    @Override
    public NextAction handleAccept(final FilterChainContext ctx)
            throws IOException {

        final Filter transportFilter0 = getTransportFilter0(
                ctx.getConnection().getTransport());

        if (transportFilter0 != null) {
            return transportFilter0.handleAccept(ctx);
        }

        return null;
    }

    /**
     * Delegates connect operation to {@link Transport}'s specific transport
     * filter.
     */
    @Override
    public NextAction handleConnect(final FilterChainContext ctx)
            throws IOException {

        final Filter transportFilter0 = getTransportFilter0(
                ctx.getConnection().getTransport());

        if (transportFilter0 != null) {
            return transportFilter0.handleConnect(ctx);
        }

        return null;
    }

    /**
     * Delegates reading operation to {@link Transport}'s specific transport
     * filter.
     */
    @Override
    public NextAction handleRead(final FilterChainContext ctx)
            throws IOException {

        final Filter transportFilter0 = getTransportFilter0(
                ctx.getConnection().getTransport());

        if (transportFilter0 != null) {
            return transportFilter0.handleRead(ctx);
        }
        
        return null;
    }

    /**
     * Delegates writing operation to {@link Transport}'s specific transport
     * filter.
     */
    @Override
    public NextAction handleWrite(final FilterChainContext ctx)
            throws IOException {

        final Filter transportFilter0 = getTransportFilter0(
                ctx.getConnection().getTransport());

        if (transportFilter0 != null) {
            return transportFilter0.handleWrite(ctx);
        }

        return null;
    }

    /**
     * Delegates close operation to {@link Transport}'s specific transport
     * filter.
     */
    @Override
    public NextAction handleClose(final FilterChainContext ctx)
            throws IOException {

        final Filter transportFilter0 = getTransportFilter0(
                ctx.getConnection().getTransport());

        if (transportFilter0 != null) {
            return transportFilter0.handleClose(ctx);
        }

        return null;
    }

    /**
     * Get default {@link Transport} specific transport filter.
     *
     * @param transport {@link Transport}.
     *
     * @return default {@link Transport} specific transport filter.
     */
    protected Filter getTransportFilter0(final Transport transport) {
        if (transport instanceof FilterChainEnabledTransport) {
            return ((FilterChainEnabledTransport) transport).getTransportFilter();
        }
        
        return null;
    }
}
