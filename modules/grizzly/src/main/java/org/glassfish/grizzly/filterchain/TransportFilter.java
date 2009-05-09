/*
 * 
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2007-2008 Sun Microsystems, Inc. All rights reserved.
 * 
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License. You can obtain
 * a copy of the License at https://glassfish.dev.java.net/public/CDDL+GPL.html
 * or glassfish/bootstrap/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 * 
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/bootstrap/legal/LICENSE.txt.
 * Sun designates this particular file as subject to the "Classpath" exception
 * as provided by Sun in the GPL Version 2 section of the License file that
 * accompanied this code.  If applicable, add the following below the License
 * Header, with the fields enclosed by brackets [] replaced by your own
 * identifying information: "Portions Copyrighted [year]
 * [name of copyright owner]"
 * 
 * Contributor(s):
 * 
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
 *
 */

package org.glassfish.grizzly.filterchain;

import org.glassfish.grizzly.Transport;
import java.io.IOException;
import org.glassfish.grizzly.Connection;

/**
 * Transport {@link Filter} implementation, which should work with any
 * {@link Transport}. This {@link Filter} tries to delegate I/O event processing
 * to the {@link Transport}'s specific transport {@link Filter}. If
 * {@link Transport} doesn't have own implementation - uses common I/O event
 * processing logic.
 * 
 * @author Alexey Stashok
 */
public class TransportFilter extends FilterAdapter {
    public static final String WORKER_THREAD_BUFFER_NAME = "thread-buffer";

    /**
     * Delegates accept operation to {@link Transport}'s specific transport
     * filter.
     */
    @Override
    public NextAction handleAccept(final FilterChainContext ctx,
            final NextAction nextAction) throws IOException {

        initializeContext(ctx);

        final Filter defaultTransportFilter = getDefaultTransportFilter(
                ctx.getConnection().getTransport());

        if (defaultTransportFilter != null) {
            ctx.setDefaultTransportFilter(defaultTransportFilter);
            return defaultTransportFilter.handleAccept(ctx, nextAction);
        }

        return null;
    }

    /**
     * Delegates connect operation to {@link Transport}'s specific transport
     * filter.
     */
    @Override
    public NextAction handleConnect(final FilterChainContext ctx,
            final NextAction nextAction) throws IOException {

        initializeContext(ctx);

        final Filter defaultTransportFilter = getDefaultTransportFilter(
                ctx.getConnection().getTransport());

        if (defaultTransportFilter != null) {
            ctx.setDefaultTransportFilter(defaultTransportFilter);
            return defaultTransportFilter.handleConnect(ctx, nextAction);
        }

        return null;
    }

    /**
     * Delegates reading operation to {@link Transport}'s specific transport
     * filter.
     */
    @Override
    public NextAction handleRead(final FilterChainContext ctx,
            final NextAction nextAction) throws IOException {

        initializeContext(ctx);

        final Filter defaultTransportFilter = getDefaultTransportFilter(
                ctx.getConnection().getTransport());

        if (defaultTransportFilter != null) {
            ctx.setDefaultTransportFilter(defaultTransportFilter);
            return defaultTransportFilter.handleRead(ctx, nextAction);
        }
        
        return null;
    }

    /**
     * Delegates writing operation to {@link Transport}'s specific transport
     * filter.
     */
    @Override
    public NextAction handleWrite(final FilterChainContext ctx,
            final NextAction nextAction) throws IOException {

        initializeContext(ctx);

        final Filter defaultTransportFilter = getDefaultTransportFilter(
                ctx.getConnection().getTransport());

        if (defaultTransportFilter != null) {
            ctx.setDefaultTransportFilter(defaultTransportFilter);
            return defaultTransportFilter.handleWrite(ctx, nextAction);
        }

        return null;
    }

    /**
     * Delegates close operation to {@link Transport}'s specific transport
     * filter.
     */
    @Override
    public NextAction handleClose(final FilterChainContext ctx,
            final NextAction nextAction) throws IOException {

        initializeContext(ctx);

        final Filter defaultTransportFilter = getDefaultTransportFilter(
                ctx.getConnection().getTransport());

        if (defaultTransportFilter != null) {
            ctx.setDefaultTransportFilter(defaultTransportFilter);
            return defaultTransportFilter.handleClose(ctx, nextAction);
        }

        return null;
    }

    /**
     * Delegates post accepting processing to {@link Transport}'s specific
     * transport filter.
     */
    @Override
    public NextAction postAccept(final FilterChainContext ctx,
            final NextAction nextAction) throws IOException {

        final Filter defaultTransportFilter = ctx.getDefaultTransportFilter();

        if (defaultTransportFilter != null) {
            return defaultTransportFilter.postAccept(ctx, nextAction);
        }

        return null;
    }

    /**
     * Delegates post connecting processing to {@link Transport}'s specific
     * transport filter.
     */
    @Override
    public NextAction postConnect(final FilterChainContext ctx,
            final NextAction nextAction) throws IOException {

        final Filter defaultTransportFilter = ctx.getDefaultTransportFilter();

        if (defaultTransportFilter != null) {
            return defaultTransportFilter.postRead(ctx, nextAction);
        }

        return null;
    }

    /**
     * Delegates post reading processing to {@link Transport}'s specific
     * transport filter.
     */
    @Override
    public NextAction postRead(final FilterChainContext ctx,
            final NextAction nextAction) throws IOException {

        final Filter defaultTransportFilter = ctx.getDefaultTransportFilter();

        if (defaultTransportFilter != null) {
            return defaultTransportFilter.postConnect(ctx, nextAction);
        }
        
        return null;
    }

    /**
     * Delegates post writing processing to {@link Transport}'s specific
     * transport filter.
     */
    @Override
    public NextAction postWrite(final FilterChainContext ctx,
            final NextAction nextAction) throws IOException {

        final Filter defaultTransportFilter = ctx.getDefaultTransportFilter();

        if (defaultTransportFilter != null) {
            return defaultTransportFilter.postWrite(ctx, nextAction);
        }

        return null;
    }

    /**
     * Delegates post closing processing to {@link Transport}'s specific
     * transport filter.
     */
    @Override
    public NextAction postClose(final FilterChainContext ctx,
            final NextAction nextAction) throws IOException {

        final Filter defaultTransportFilter = ctx.getDefaultTransportFilter();

        if (defaultTransportFilter != null) {
            return defaultTransportFilter.postClose(ctx, nextAction);
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
    protected Filter getDefaultTransportFilter(final Transport transport) {
        if (transport instanceof FilterChainEnabledTransport) {
            final Filter defaultTransportFilter =
                    ((FilterChainEnabledTransport) transport).
                    getDefaultTransportFilter();
            
            return defaultTransportFilter;
        }
        
        return null;
    }

    private static final void initializeContext(final FilterChainContext ctx) {
        final Connection connection = ctx.getConnection();
        ctx.setStreamReader(connection.getStreamReader());
        ctx.setStreamWriter(connection.getStreamWriter());
    }
}
