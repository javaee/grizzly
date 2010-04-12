/*
 * 
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2007-2010 Sun Microsystems, Inc. All rights reserved.
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
import com.sun.grizzly.attributes.Attribute;
import com.sun.grizzly.impl.FutureImpl;
import com.sun.grizzly.utils.CompletionHandlerAdapter;
import java.util.concurrent.ExecutionException;

/**
 * The {@link TCPNIOTransport}'s transport {@link Filter} implementation
 * 
 * @author Alexey Stashok
 */
public final class TCPNIOTransportFilter extends BaseFilter {

    public static final int DEFAULT_BUFFER_SIZE = 8192;
    private final TCPNIOTransport transport;
    private final Attribute<Buffer> compositeBufferAttr;

    TCPNIOTransportFilter(final TCPNIOTransport transport) {
        this.transport = transport;
        compositeBufferAttr = transport.getAttributeBuilder().createAttribute(
                getClass().getName() + hashCode());
    }

    @Override
    public NextAction handleRead(final FilterChainContext ctx)
            throws IOException {
        final TCPNIOConnection connection = (TCPNIOConnection) ctx.getConnection();

        final Buffer buffer;
        if (!connection.isBlocking()) {
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
        
        if (buffer == null) {
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
            final FutureImpl contextFuture = ctx.getCompletionFuture();
            final CompletionHandler completionHandler = ctx.getCompletionHandler();
            
            CompletionHandler writeCompletionHandler = null;

            final boolean hasFuture = (contextFuture != null);
            if (hasFuture) {
                writeCompletionHandler = new CompletionHandlerAdapter(
                        contextFuture, ctx.getCompletionHandler());
            } else if (completionHandler != null) {
                writeCompletionHandler = completionHandler;
            }

            transport.getWriter(connection).write(connection,
                    (Buffer) message, writeCompletionHandler).markForRecycle(
                    !hasFuture);
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
            } catch (IOException e) {
            }
        }
    }
}
