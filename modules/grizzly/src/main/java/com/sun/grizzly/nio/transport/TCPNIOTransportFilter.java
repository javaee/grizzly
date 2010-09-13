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

package com.sun.grizzly.nio.transport;

import com.sun.grizzly.Connection;
import com.sun.grizzly.filterchain.BaseFilter;
import com.sun.grizzly.filterchain.FilterChainContext;
import com.sun.grizzly.filterchain.NextAction;
import java.io.IOException;
import java.util.logging.Filter;
import com.sun.grizzly.Buffer;
import com.sun.grizzly.CompletionHandler;
import com.sun.grizzly.GrizzlyFuture;
import com.sun.grizzly.ReadResult;
import com.sun.grizzly.filterchain.TransportFilter;
import com.sun.grizzly.impl.FutureImpl;
import com.sun.grizzly.memory.BufferUtils;
import com.sun.grizzly.utils.CompletionHandlerAdapter;
import java.util.concurrent.ExecutionException;

/**
 * The {@link TCPNIOTransport}'s transport {@link Filter} implementation
 * 
 * @author Alexey Stashok
 */
public final class TCPNIOTransportFilter extends BaseFilter {
    private final TCPNIOTransport transport;

    TCPNIOTransportFilter(final TCPNIOTransport transport) {
        this.transport = transport;
    }

    @Override
    public NextAction handleRead(final FilterChainContext ctx)
            throws IOException {
        final TCPNIOConnection connection = (TCPNIOConnection) ctx.getConnection();
        final boolean isBlocking = ctx.getTransportContext().isBlocking();

        final Buffer buffer;
        if (!isBlocking) {
            buffer = transport.read(connection, null);
        } else {
            GrizzlyFuture<ReadResult> future =
                    transport.getTemporarySelectorIO().getReader().read(
                    connection, null);
            try {
                ReadResult<Buffer, ?> result = future.get();
                buffer = result.getMessage();
                future.recycle(true);
            } catch (ExecutionException e) {
                final Throwable cause = e.getCause();
                if (cause instanceof IOException) {
                    throw (IOException) cause;
                }
                
                throw new IOException(cause);
            } catch (InterruptedException e) {
                throw new IOException(e);
            }
        }
        
        if (buffer == null || buffer.position() == 0) {
            return ctx.getStopAction();
        } else {
            buffer.trim();
            
            ctx.setMessage(buffer);
            ctx.setAddress(connection.getPeerAddress());
        }

        return ctx.getInvokeAction();
    }

    @Override
    public NextAction handleWrite(final FilterChainContext ctx)
            throws IOException {
        final Object message = ctx.getMessage();
        if (message != null) {
            ctx.setMessage(null);
            final Connection connection = ctx.getConnection();
            final FilterChainContext.TransportContext transportContext =
                    ctx.getTransportContext();

            final FutureImpl contextFuture = transportContext.getFuture();
            final CompletionHandler completionHandler = transportContext.getCompletionHandler();

            CompletionHandler writeCompletionHandler = null;

            final boolean hasFuture = (contextFuture != null);
            if (hasFuture) {
                writeCompletionHandler = new CompletionHandlerAdapter(
                        contextFuture, completionHandler);
            } else if (completionHandler != null) {
                writeCompletionHandler = completionHandler;
            }

            transport.getWriter(transportContext.isBlocking()).write(connection,
                    (Buffer) message, writeCompletionHandler).markForRecycle(
                    !hasFuture);

            transportContext.setFuture(null);
            transportContext.setCompletionHandler(null);
        }


        return ctx.getInvokeAction();
    }

    @Override
    public NextAction handleEvent(final FilterChainContext ctx,
            final Object event) throws IOException {
        if (event == TransportFilter.FLUSH_EVENT) {
            final Connection connection = ctx.getConnection();
            final FilterChainContext.TransportContext transportContext =
                    ctx.getTransportContext();

            final FutureImpl contextFuture = transportContext.getFuture();
            final CompletionHandler completionHandler = transportContext.getCompletionHandler();

            if (contextFuture == null && completionHandler == null) return ctx.getInvokeAction();


            final CompletionHandler writeCompletionHandler;

            final boolean hasFuture = (contextFuture != null);
            if (hasFuture) {
                writeCompletionHandler = new CompletionHandlerAdapter(
                        contextFuture, completionHandler);
            } else {
                writeCompletionHandler = completionHandler;
            }

            transport.getWriter(transportContext.isBlocking()).write(connection,
                    BufferUtils.EMPTY_BUFFER, writeCompletionHandler)
                    .markForRecycle(false);

            transportContext.setFuture(null);
            transportContext.setCompletionHandler(null);
        }
        
        return ctx.getInvokeAction();
    }

    @Override
    public void exceptionOccurred(final FilterChainContext ctx,
            final Throwable error) {

        final Connection connection = ctx.getConnection();
        if (connection != null) {
            try {
                connection.close().markForRecycle(true);
            } catch (IOException ignored) {
            }
        }
    }
}
