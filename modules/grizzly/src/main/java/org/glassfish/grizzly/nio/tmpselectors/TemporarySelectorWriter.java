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
import java.util.concurrent.TimeUnit;
import org.glassfish.grizzly.*;
import org.glassfish.grizzly.asyncqueue.LifeCycleHandler;
import org.glassfish.grizzly.WritableMessage;
import org.glassfish.grizzly.nio.NIOConnection;

/**
 *
 * @author oleksiys
 */
public abstract class TemporarySelectorWriter
        extends AbstractWriter<SocketAddress> {

    /**
     * {@inheritDoc}
     */
    @Override
    public void write(
            final Connection<SocketAddress> connection,
            final SocketAddress dstAddress,
            final WritableMessage message,
            final CompletionHandler<WriteResult<WritableMessage, SocketAddress>> completionHandler,
            final LifeCycleHandler lifeCycleHandler) {
        write(connection, dstAddress, message, completionHandler,
                lifeCycleHandler,
                ((NIOConnection) connection).getBlockingWriteTimeout(TimeUnit.MILLISECONDS),
                TimeUnit.MILLISECONDS);
    }

    /**
     * Method writes the {@link WritableMessage} to the specific address.
     *
     * @param connection the {@link org.glassfish.grizzly.Connection} to write to
     * @param dstAddress the destination address the <tt>message</tt> will be
     *        sent to
     * @param message the {@link WritableMessage}, from which the data will be written
     * @param completionHandler {@link org.glassfish.grizzly.CompletionHandler},
     *        which will get notified, when write will be completed
     */
    public void write(
            final Connection<SocketAddress> connection,
            final SocketAddress dstAddress,
            final WritableMessage message,
            final CompletionHandler<WriteResult<WritableMessage, SocketAddress>> completionHandler,
            final LifeCycleHandler lifeCycleHandler,
            final long timeout, final TimeUnit timeunit) {

        if (message == null) {
            failure(new IllegalStateException("Message cannot be null"),
                    completionHandler);
            return;
        }

        if (connection == null || !(connection instanceof NIOConnection)) {
            failure(new IllegalStateException("Connection should be NIOConnection and cannot be null"),
                    completionHandler);
            return;
        }

        final NIOConnection nioConnection = (NIOConnection) connection;
        
        final WriteResult<WritableMessage, SocketAddress> writeResult =
                WriteResult.create(connection,
                        message, dstAddress, 0);

        try {
            if (lifeCycleHandler != null) {
//                lifeCycleHandler.onAccept(connection, message);
                lifeCycleHandler.onBeforeWrite(connection, message);
            }
            
            write0(nioConnection, dstAddress, message, writeResult,
                    timeout, timeunit);

            if (completionHandler != null) {
                completionHandler.completed(writeResult);
            }

            message.release();
        } catch (IOException e) {
            failure(e, completionHandler);
        }
    }
    
    /**
     * Flush the buffer by looping until the {@link Buffer} is empty
     *
     *
     * @param connection the {@link org.glassfish.grizzly.Connection}.
     * @param dstAddress the destination address.
     * @param message
     *@param currentResult the result of the write operation
     * @param timeout operation timeout value value
     * @param timeunit the timeout unit
*    @return The number of bytes written.
     * 
     * @throws java.io.IOException
     */
    protected long write0(final NIOConnection connection,
            final SocketAddress dstAddress, final WritableMessage message,
            final WriteResult<WritableMessage, SocketAddress> currentResult,
            final long timeout, final TimeUnit timeunit) throws IOException {

        final SelectableChannel channel = connection.getChannel();
        final long writeTimeout = TimeUnit.MILLISECONDS.convert(timeout, timeunit);

        SelectionKey key = null;
        Selector writeSelector = null;
        int attempts = 0;
        int bytesWritten = 0;

        try {
            synchronized (connection) {
                while (message.hasRemaining()) {
                    long len = writeNow0(connection, dstAddress, message,
                            currentResult);

                    if (len > 0) {
                        attempts = 0;
                        bytesWritten += len;
                    } else {
                        attempts++;
                        if (writeSelector == null) {
                            writeSelector = getTemporarySelectorIO().getSelectorPool().poll();

                            if (writeSelector == null) {
                                // Continue using the main one.
                                continue;
                            }
                            key = channel.register(writeSelector,
                                    SelectionKey.OP_WRITE);
                        } else {
                            writeSelector.selectedKeys().clear();
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
            getTemporarySelectorIO().getSelectorPool().offer(writeSelector, key);
        }
        
        return bytesWritten;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean canWrite(final Connection connection) {
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void notifyWritePossible(final Connection connection,
            final WriteHandler writeHandler) {
        try {
            writeHandler.onWritePossible();
        } catch (Throwable t) {
            writeHandler.onError(t);
        }
    }

    
    protected abstract long writeNow0(NIOConnection connection,
            SocketAddress dstAddress, WritableMessage message,
            WriteResult<WritableMessage, SocketAddress> currentResult)
            throws IOException;
    
    protected abstract TemporarySelectorIO getTemporarySelectorIO();
    
    private static void failure(
            final Throwable failure,
            final CompletionHandler<WriteResult<WritableMessage, SocketAddress>> completionHandler) {
        if (completionHandler != null) {
            completionHandler.failed(failure);
        }
    }
}
