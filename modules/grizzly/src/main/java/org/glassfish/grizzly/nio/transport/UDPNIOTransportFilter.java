/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2009-2010 Oracle and/or its affiliates. All rights reserved.
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

import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.CompletionHandler;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.GrizzlyFuture;
import org.glassfish.grizzly.IOEvent;
import org.glassfish.grizzly.ReadResult;
import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.filterchain.TransportFilter;
import org.glassfish.grizzly.impl.FutureImpl;
import org.glassfish.grizzly.memory.Buffers;
import org.glassfish.grizzly.utils.CompletionHandlerAdapter;
import java.io.IOException;
import java.net.SocketAddress;
import java.util.concurrent.ExecutionException;
import java.util.logging.Filter;

/**
 * The {@link UDPNIOTransport}'s transport {@link Filter} implementation
 * 
 * @author Alexey Stashok
 */
public final class UDPNIOTransportFilter extends BaseFilter {
    private final UDPNIOTransport transport;

    UDPNIOTransportFilter(final UDPNIOTransport transport) {
        this.transport = transport;
    }

    @Override
    public NextAction handleRead(final FilterChainContext ctx)
            throws IOException {
        final UDPNIOConnection connection = (UDPNIOConnection) ctx.getConnection();
        final boolean isBlocking = ctx.getTransportContext().isBlocking();

        final ReadResult<Buffer, SocketAddress> readResult;

        if (!isBlocking) {
            readResult = ReadResult.create(connection);
            transport.read(connection, null, readResult);

        } else {
            GrizzlyFuture<ReadResult> future =
                    transport.getTemporarySelectorIO().getReader().read(
                    connection, null);
            try {
                readResult = future.get();
                future.recycle(false);
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

        if (readResult.getReadSize() > 0) {
            final Buffer buffer = readResult.getMessage();
            buffer.trim();
            final SocketAddress address = readResult.getSrcAddress();
            readResult.recycle();

            ctx.setMessage(buffer);
            ctx.setAddress(address);

            if (!connection.isConnected()) {
                connection.enableIOEvent(IOEvent.READ);
            }
        } else {
            readResult.recycle();
            return ctx.getStopAction();
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
            final Object address = ctx.getAddress();
            
            CompletionHandler writeCompletionHandler = null;

            final boolean hasFuture = (contextFuture != null);
            if (hasFuture) {
                writeCompletionHandler = new CompletionHandlerAdapter(
                        contextFuture, completionHandler);
            } else if (completionHandler != null) {
                writeCompletionHandler = completionHandler;
            }

            transport.getWriter(transportContext.isBlocking()).write(
                    connection, address,
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
                    Buffers.EMPTY_BUFFER, writeCompletionHandler)
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
