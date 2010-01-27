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

package com.sun.grizzly.nio.tmpselectors;

import com.sun.grizzly.Transformer;
import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import com.sun.grizzly.AbstractWriter;
import com.sun.grizzly.Buffer;
import com.sun.grizzly.CompletionHandler;
import com.sun.grizzly.Connection;
import com.sun.grizzly.Grizzly;
import com.sun.grizzly.Interceptor;
import com.sun.grizzly.TransformationResult;
import com.sun.grizzly.WriteResult;
import com.sun.grizzly.impl.ReadyFutureImpl;
import com.sun.grizzly.nio.NIOConnection;
import java.io.EOFException;

/**
 *
 * @author oleksiys
 */
public abstract class TemporarySelectorWriter
        extends AbstractWriter<SocketAddress> {

    private static final int DEFAULT_TIMEOUT = 30000;

    private Logger logger = Grizzly.logger(TemporarySelectorWriter.class);

    protected final TemporarySelectorsEnabledTransport transport;

    private int timeoutMillis = DEFAULT_TIMEOUT;

    public TemporarySelectorWriter(
            TemporarySelectorsEnabledTransport transport) {
        this.transport = transport;
    }

    public int getTimeout() {
        return timeoutMillis;
    }

    public void setTimeout(int timeout) {
        this.timeoutMillis = timeout;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <M> Future<WriteResult<M, SocketAddress>> write(
            Connection connection, SocketAddress dstAddress, M message,
            CompletionHandler<WriteResult<M, SocketAddress>> completionHandler,
            Transformer<M, Buffer> transformer,
            Interceptor<WriteResult> interceptor) throws IOException {
        
        return write(connection, dstAddress, message, completionHandler,
                transformer, interceptor, timeoutMillis, TimeUnit.MILLISECONDS);
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
    public <M> Future<WriteResult<M, SocketAddress>> write(
            Connection connection, SocketAddress dstAddress, M message,
            CompletionHandler<WriteResult<M, SocketAddress>> completionHandler,
            Transformer<M, Buffer> transformer,
            Interceptor<WriteResult> interceptor,
            long timeout, TimeUnit timeunit) throws IOException {

        if (message == null) {
            throw new IllegalStateException("Message cannot be null.");
        }

        if (connection == null || !(connection instanceof NIOConnection)) {
            throw new IllegalStateException(
                    "Connection should be NIOConnection and cannot be null.");
        }

        final WriteResult writeResult =
                new WriteResult(connection, message, dstAddress, 0);

        if (transformer == null) {
            write0(connection, dstAddress, (Buffer) message, writeResult,
                    timeout, timeunit);
        } else {
            writeWithTransformer(connection, dstAddress, transformer,
                    writeResult, message, timeout, timeunit);
        }

        final Future<WriteResult<M, SocketAddress>> writeFuture =
                new ReadyFutureImpl(writeResult);
        
        if (completionHandler != null) {
            completionHandler.completed(connection, writeResult);
        }

        return writeFuture;
    }

    private final int writeWithTransformer(final Connection connection,
            final SocketAddress dstAddress,
            final Transformer transformer,
            final WriteResult currentResult, final Object message,
            final long timeout, final TimeUnit timeunit) throws IOException {

        final WriteResult writeResult = new WriteResult(connection);

        do {
            writeResult.setWrittenSize(0);
            
            final TransformationResult tResult = transformer.transform(
                    connection, message);

            if (tResult.getStatus() == TransformationResult.Status.COMPLETED) {
                final Buffer buffer = (Buffer) tResult.getMessage();
                try {
                    final int writtenBytes = write0(connection, dstAddress,
                            buffer, writeResult, timeout, timeunit);

                    if (writtenBytes > 0) {
                        currentResult.setWrittenSize(
                                currentResult.getWrittenSize() + writtenBytes);
                    } else if (writtenBytes == -1) {
                        throw new EOFException();
                    }
                } finally {
                    if (buffer != message) {
                        buffer.dispose();
                    }
                }

                final Object remainder = tResult.getExternalRemainder();
                if (!tResult.hasInternalRemainder() &&
                        (remainder == null || !transformer.hasInputRemaining(remainder))) {
                    transformer.release(connection);
                    return currentResult.getWrittenSize();
                }
            } else if (tResult.getStatus() == TransformationResult.Status.INCOMPLETED) {
                throw new IOException("Transformation exception: provided message is incompleted");
            } else if (tResult.getStatus() == TransformationResult.Status.ERROR) {
                throw new IOException("Transformation exception ("
                        + tResult.getErrorCode() + "): "
                        + tResult.getErrorDescription());
            }
        } while (true);
    }

    
    /**
     * Flush the buffer by looping until the {@link Buffer} is empty
     * @param channel {@link SelectableChannel}
     * @param bb the Buffer to write.
     * @return The number of bytes written
     * @throws java.io.IOException
     */
    protected int write0(final Connection connection,
            final SocketAddress dstAddress, final Buffer buffer,
            final WriteResult currentResult,
            final long timeout, final TimeUnit timeunit) throws IOException {

        final NIOConnection nioConnection = (NIOConnection) connection;
        final SelectableChannel channel = nioConnection.getChannel();
        final long writeTimeout = TimeUnit.MILLISECONDS.convert(timeout, timeunit);

        SelectionKey key = null;
        Selector writeSelector = null;
        int attempts = 0;
        int bytesWritten = 0;

        try {
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
        } finally {
            transport.getTemporarySelectorIO().recycleTemporaryArtifacts(
                    writeSelector, key);
        }
        
        return bytesWritten;
    }

    public TemporarySelectorsEnabledTransport getTransport() {
        return transport;
    }

    protected abstract int writeNow0(Connection connection,
            SocketAddress dstAddress, Buffer buffer, WriteResult currentResult)
            throws IOException;
}
