/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2009-2012 Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.net.SocketAddress;
import java.util.concurrent.ExecutionException;
import java.util.logging.Filter;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.CompletionHandler;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.GrizzlyFuture;
import org.glassfish.grizzly.ReadResult;
import org.glassfish.grizzly.asyncqueue.WritableMessage;
import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.FilterChainEvent;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.filterchain.TransportFilter;
import org.glassfish.grizzly.memory.Buffers;
import org.glassfish.grizzly.utils.Holder;

/**
 * The {@link UDPNIOTransport}'s transport {@link Filter} implementation
 * 
 * @author Alexey Stashok
 */
@SuppressWarnings("unchecked")
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

        final Buffer inBuffer = ctx.getMessage();

        final ReadResult<Buffer, SocketAddress> readResult;

        if (!isBlocking) {
            readResult = ReadResult.create(connection);
            transport.read(connection, inBuffer, readResult);

        } else {
            GrizzlyFuture<ReadResult<Buffer, SocketAddress>> future =
                    transport.getTemporarySelectorIO().getReader().read(
                    connection, inBuffer);
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
            final Holder<SocketAddress> addressHolder =
                    readResult.getSrcAddressHolder();
            readResult.recycle();

            ctx.setMessage(buffer);
            ctx.setAddressHolder(addressHolder);

//            if (!connection.isConnected()) {
//                connection.enableIOEvent(IOEvent.READ);
//            }
        } else {
            readResult.recycle();
            return ctx.getStopAction();
        }

        return ctx.getInvokeAction();
    }

    @Override
    public NextAction handleWrite(final FilterChainContext ctx)
            throws IOException {
        final WritableMessage message = ctx.getMessage();
        if (message != null) {
            ctx.setMessage(null);
            final Connection connection = ctx.getConnection();
            final FilterChainContext.TransportContext transportContext =
                    ctx.getTransportContext();

            final CompletionHandler completionHandler = transportContext.getCompletionHandler();
            final Object address = ctx.getAddress();
            
            transportContext.setCompletionHandler(null);

            transport.getWriter(transportContext.isBlocking()).write(
                    connection, address,
                    message, completionHandler);
        }

        return ctx.getInvokeAction();
    }

    @Override
    @SuppressWarnings("unchecked")
    public NextAction handleEvent(final FilterChainContext ctx,
            final FilterChainEvent event) throws IOException {
        
        if (event.type() == TransportFilter.FlushEvent.TYPE) {
            final Connection connection = ctx.getConnection();
            final FilterChainContext.TransportContext transportContext =
                    ctx.getTransportContext();

            if (transportContext.getCompletionHandler() != null) {
                throw new IllegalStateException("TransportContext CompletionHandler must be null");
            }

            final CompletionHandler completionHandler =
                    ((TransportFilter.FlushEvent) event).getCompletionHandler();

            transport.getWriter(transportContext.isBlocking()).write(connection,
                    Buffers.EMPTY_BUFFER, completionHandler);

            transportContext.setCompletionHandler(null);
        }

        return ctx.getInvokeAction();
    }
    
    @Override
    public void exceptionOccurred(final FilterChainContext ctx,
            final Throwable error) {

        final Connection connection = ctx.getConnection();
        if (connection != null) {
            connection.closeSilently();
        }
    }
}
