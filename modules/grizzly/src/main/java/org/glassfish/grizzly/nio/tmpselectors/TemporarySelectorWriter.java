/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2012 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.nio.tmpselectors;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.glassfish.grizzly.AbstractWriter;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.CompletionHandler;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.GrizzlyFuture;
import org.glassfish.grizzly.Interceptor;
import org.glassfish.grizzly.WriteResult;
import org.glassfish.grizzly.impl.ReadyFutureImpl;
import org.glassfish.grizzly.nio.NIOConnection;

/**
 *
 * @author oleksiys
 */
public abstract class TemporarySelectorWriter
        extends AbstractWriter<SocketAddress> {

    protected final TemporarySelectorsEnabledTransport transport;

    public TemporarySelectorWriter(
            TemporarySelectorsEnabledTransport transport) {
        this.transport = transport;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public GrizzlyFuture<WriteResult<Buffer, SocketAddress>> write(
            Connection connection, SocketAddress dstAddress, Buffer buffer,
            CompletionHandler<WriteResult<Buffer, SocketAddress>> completionHandler,
            Interceptor<WriteResult<Buffer, SocketAddress>> interceptor)
            throws IOException {
        return write(connection, dstAddress, buffer, completionHandler,
                interceptor,
                connection.getWriteTimeout(TimeUnit.MILLISECONDS),
                TimeUnit.MILLISECONDS);
    }

    /**
     * Method writes the <tt>message</tt> to the specific address.
     *
     * @param connection the {@link Connection} to write to
     * @param dstAddress the destination address the <tt>message</tt> will be
     *        sent to
     * @param message the message, from which the data will be written
     * @param completionHandler {@link CompletionHandler},
     *        which will get notified, when write will be completed
     * @return {@link Future}, using which it's possible to check the
     *         result
     * @throws java.io.IOException
     */
    @SuppressWarnings("UnusedParameters")
    public GrizzlyFuture<WriteResult<Buffer, SocketAddress>> write(
            Connection connection, SocketAddress dstAddress, Buffer message,
            CompletionHandler<WriteResult<Buffer, SocketAddress>> completionHandler,
            Interceptor<WriteResult<Buffer, SocketAddress>> interceptor,
            long timeout, TimeUnit timeunit) throws IOException {

        if (message == null) {
            return failure(new IllegalStateException("Message cannot be null"),
                    completionHandler);
        }

        if (connection == null || !(connection instanceof NIOConnection)) {
            return failure(
                    new IllegalStateException("Connection should be NIOConnection and cannot be null"),
                    completionHandler);
        }

        final NIOConnection nioConnection = (NIOConnection) connection;
        
        final WriteResult<Buffer, SocketAddress> writeResult =
                WriteResult.create(connection,
                        message, dstAddress, 0);

        try {
            write0(nioConnection, dstAddress, message, writeResult,
                    timeout, timeunit);

            final GrizzlyFuture<WriteResult<Buffer, SocketAddress>> writeFuture =
                    ReadyFutureImpl.create(writeResult);

            if (completionHandler != null) {
                completionHandler.completed(writeResult);
            }

            message.tryDispose();
            return writeFuture;
            
        } catch (IOException e) {
            return failure(e, completionHandler);
        }
    }
    
    /**
     * Flush the buffer by looping until the {@link Buffer} is empty
     *
     * @param connection the {@link Connection}.
     * @param dstAddress the destination address.
     * @param buffer the {@link Buffer} to write.
     * @param currentResult the result of the write operation
     * @param timeout operation timeout value value
     * @param timeunit the timeout unit
     *
     * @return The number of bytes written.
     * 
     * @throws java.io.IOException
     */
    protected int write0(final NIOConnection connection,
            final SocketAddress dstAddress, final Buffer buffer,
            final WriteResult<Buffer, SocketAddress> currentResult,
            final long timeout, final TimeUnit timeunit) throws IOException {

        final SelectableChannel channel = connection.getChannel();
        final long writeTimeout = TimeUnit.MILLISECONDS.convert(timeout, timeunit);

        SelectionKey key = null;
        Selector writeSelector = null;
        int attempts = 0;
        int bytesWritten = 0;

        try {
            //noinspection SynchronizationOnLocalVariableOrMethodParameter
            synchronized (connection) {
                while (buffer.hasRemaining()) {
                    int len = writeNow0(connection, dstAddress, buffer,
                            currentResult);

                    if (len > 0) {
                        attempts = 0;
                        bytesWritten += len;
                    } else {
                        attempts++;
                        if (writeSelector == null) {
                            writeSelector = transport.getTemporarySelectorIO().
                                    getSelectorPool().poll();

                            if (writeSelector == null) {
                                // Continue using the main one.
                                continue;
                            }
                            key = channel.register(writeSelector,
                                    SelectionKey.OP_WRITE);
                        }

                        if (writeSelector.select(writeTimeout) == 0) {
                            if (attempts > 2) {
                                throw new IOException("Client disconnected");
                            }
                        }
                    }
                }
            }
        } finally {
            transport.getTemporarySelectorIO().recycleTemporaryArtifacts(
                    writeSelector, key);
        }
        
        return bytesWritten;
    }

    public TemporarySelectorsEnabledTransport getTransport() {
        return transport;
    }

    protected abstract int writeNow0(NIOConnection connection,
            SocketAddress dstAddress, Buffer buffer,
            WriteResult<Buffer, SocketAddress> currentResult)
            throws IOException;
    
    private static GrizzlyFuture<WriteResult<Buffer, SocketAddress>> failure(
            final Throwable failure,
            final CompletionHandler<WriteResult<Buffer, SocketAddress>> completionHandler) {
        if (completionHandler != null) {
            completionHandler.failed(failure);
        }
        
        return ReadyFutureImpl.create(failure);
    }
}
