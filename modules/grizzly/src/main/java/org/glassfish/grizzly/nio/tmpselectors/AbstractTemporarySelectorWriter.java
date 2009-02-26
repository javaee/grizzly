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

package org.glassfish.grizzly.nio.tmpselectors;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import org.glassfish.grizzly.AbstractWriter;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.CompletionHandler;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.Interceptor;
import org.glassfish.grizzly.WriteResult;
import org.glassfish.grizzly.impl.ReadyFutureImpl;
import org.glassfish.grizzly.nio.NIOConnection;

/**
 *
 * @author oleksiys
 */
public abstract class AbstractTemporarySelectorWriter
        extends AbstractWriter<SocketAddress> {

    private static final int DEFAULT_TIMEOUT = 30000;

    private Logger logger = Grizzly.logger;

    private TemporarySelectorIO temporarySelectorIO;

    private int timeoutMillis = DEFAULT_TIMEOUT;

    public AbstractTemporarySelectorWriter(
            TemporarySelectorIO temporarySelectorIO) {
        this.temporarySelectorIO = temporarySelectorIO;
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
    public Future<WriteResult<Buffer, SocketAddress>> write(
            Connection connection, SocketAddress dstAddress,
            Buffer buffer,
            CompletionHandler<WriteResult<Buffer, SocketAddress>> completionHandler,
            Interceptor<WriteResult> interceptor)
            throws IOException {
        return write(connection, dstAddress, buffer, completionHandler,
                interceptor, timeoutMillis, TimeUnit.MILLISECONDS);
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
    public Future<WriteResult<Buffer, SocketAddress>> write(
            Connection connection, SocketAddress dstAddress,
            Buffer buffer,
            CompletionHandler<WriteResult<Buffer, SocketAddress>> completionHandler,
            Interceptor<WriteResult> interceptor,
            long timeout, TimeUnit timeunit) throws IOException {

        if (buffer == null) {
            throw new IllegalStateException("Message cannot be null.");
        }

        if (connection == null || !(connection instanceof NIOConnection)) {
            throw new IllegalStateException(
                    "Connection should be NIOConnection and cannot be null.");
        }

        WriteResult writeResult = new WriteResult(connection, buffer, dstAddress, 0);

        write0(connection, dstAddress, buffer, writeResult,
                timeout, timeunit);

        writeResult.setMessage(buffer);
        Future<WriteResult<Buffer, SocketAddress>> writeFuture =
                new ReadyFutureImpl(writeResult);
        
        if (completionHandler != null) {
            completionHandler.completed(connection, writeResult);
        }

        return writeFuture;
    }

    /**
     * Flush the buffer by looping until the {@link Buffer} is empty
     * @param channel {@link SelectableChannel}
     * @param bb the Buffer to write.
     * @return The number of bytes written
     * @throws java.io.IOException
     */
    protected int write0(Connection connection,
            SocketAddress dstAddress, Buffer buffer, WriteResult currentResult,
            long timeout, TimeUnit timeunit) throws IOException {

        NIOConnection nioConnection = (NIOConnection) connection;
        SelectableChannel channel = nioConnection.getChannel();
        long writeTimeout = TimeUnit.MILLISECONDS.convert(timeout, timeunit);

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
                        writeSelector =
                                temporarySelectorIO.getSelectorPool().poll();
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
            temporarySelectorIO.recycleTemporaryArtifacts(writeSelector, key);
        }
        
        return bytesWritten;
    }

    protected abstract int writeNow0(Connection connection,
            SocketAddress dstAddress, Buffer buffer, WriteResult currentResult)
            throws IOException;
}
