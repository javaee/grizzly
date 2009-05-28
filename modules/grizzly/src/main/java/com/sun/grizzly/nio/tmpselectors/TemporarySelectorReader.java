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

import java.io.EOFException;
import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;
import com.sun.grizzly.AbstractReader;
import com.sun.grizzly.Buffer;
import com.sun.grizzly.CompletionHandler;
import com.sun.grizzly.Connection;
import com.sun.grizzly.Grizzly;
import com.sun.grizzly.Interceptor;
import com.sun.grizzly.ReadResult;
import com.sun.grizzly.Reader;
import com.sun.grizzly.Transport;
import com.sun.grizzly.impl.ReadyFutureImpl;
import com.sun.grizzly.nio.NIOConnection;
import com.sun.grizzly.utils.conditions.Condition;

/**
 *
 * @author oleksiys
 */
public abstract class TemporarySelectorReader
        extends AbstractReader<SocketAddress> {

    private static final int DEFAULT_TIMEOUT = 30000;
    public static final int DEFAULT_BUFFER_SIZE = 8192;
    protected int defaultBufferSize = DEFAULT_BUFFER_SIZE;
    protected final TemporarySelectorsEnabledTransport transport;
    private Logger logger = Grizzly.logger;
    private int timeoutMillis = DEFAULT_TIMEOUT;

    public TemporarySelectorReader(
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
    public Future<ReadResult<Buffer, SocketAddress>> read(Connection connection,
            Buffer buffer,
            CompletionHandler<ReadResult<Buffer, SocketAddress>> completionHandler,
            Interceptor<ReadResult> interceptor) throws IOException {
        return read(connection, buffer, completionHandler, interceptor,
                timeoutMillis, TimeUnit.MILLISECONDS);
    }

    /**
     * Method reads data to the <tt>message</tt>.
     *
     * @param connection the {@link Connection} to read from
     * @param message the message, where data will be read
     * @param completionHandler {@link CompletionHandler},
     *        which will get notified, when read will be completed
     * @param condition {@link Condition}, which will be checked
     *        each time new portion of a data was read to a <tt>buffer</tt>.
     *        The <tt>condition</tt> can decide, whether asynchronous read is
     *        completed or not.
     * @param timeout operation timeout value value
     * @param timeunit the timeout unit
     * @return {@link Future}, using which it's possible to check the result
     * @throws java.io.IOException
     */
    public Future<ReadResult<Buffer, SocketAddress>> read(Connection connection,
            Buffer buffer,
            CompletionHandler<ReadResult<Buffer, SocketAddress>> completionHandler,
            Interceptor<ReadResult> interceptor,
            long timeout, TimeUnit timeunit) throws IOException {

        if (connection == null || !(connection instanceof NIOConnection)) {
            throw new IllegalStateException(
                    "Connection should be NIOConnection and cannot be null.");
        }

        ReadResult<Buffer, SocketAddress> currentResult =
                new ReadResult<Buffer, SocketAddress>(connection, buffer, null, 0);
        ReadyFutureImpl<ReadResult<Buffer, SocketAddress>> currentFuture =
                new ReadyFutureImpl<ReadResult<Buffer, SocketAddress>>(currentResult);

        if (buffer == null) {
            buffer = acquireBuffer(connection);
        }

        boolean isCompleted = false;
        while (!isCompleted) {
            isCompleted = true;
            int readBytes = read0(connection, currentResult, buffer,
                    timeout, timeunit);
            if (readBytes <= 0) {
                currentFuture.failure(new TimeoutException());
            } else if (interceptor != null) {
                isCompleted = (interceptor.intercept(Reader.READ_EVENT,
                        null, currentResult) & Interceptor.COMPLETED) != 0;
            }
        }

        if (completionHandler != null) {
            completionHandler.completed(connection, currentResult);
        }

        if (interceptor != null) {
            interceptor.intercept(timeoutMillis, connection, currentResult);
        }

        return currentFuture;
    }

    protected int read0(Connection connection, ReadResult currentResult,
            Buffer buffer, long timeout, TimeUnit timeunit)
            throws IOException {

        int bytesRead;

        NIOConnection nioConnection = (NIOConnection) connection;
        Selector readSelector = null;
        SelectionKey key = null;
        SelectableChannel channel = nioConnection.getChannel();
        long readTimeout = TimeUnit.MILLISECONDS.convert(timeout, timeunit);

        try {
            bytesRead = readNow0(connection, buffer, currentResult);

            if (bytesRead == 0) {
                readSelector = transport.getTemporarySelectorIO().
                        getSelectorPool().poll();

                if (readSelector == null) {
                    return bytesRead;
                }

                key = channel.register(readSelector, SelectionKey.OP_READ);
                key.interestOps(key.interestOps() | SelectionKey.OP_READ);
                int code = readSelector.select(readTimeout);
                key.interestOps(
                        key.interestOps() & (~SelectionKey.OP_READ));

                if (code == 0) {
                    return bytesRead; // Return on the main Selector and try again.
                }

                bytesRead = readNow0(connection, buffer, currentResult);
            }

            if (bytesRead == -1) {
                throw new EOFException();
            }
        } finally {
            transport.getTemporarySelectorIO().recycleTemporaryArtifacts(
                    readSelector, key);
        }

        return bytesRead;
    }

    protected abstract int readNow0(Connection connection,
            Buffer buffer, ReadResult currentResult) throws IOException;

    protected Buffer acquireBuffer(Connection connection) {
        Transport connectionTransport = connection.getTransport();
        return connectionTransport.getMemoryManager().
                allocate(defaultBufferSize);
    }

    private void releaseBuffer(Connection connection,
            Buffer inputBuffer) {
        Transport connectionTransport = connection.getTransport();
        connectionTransport.getMemoryManager().release(inputBuffer);
    }

    public TemporarySelectorsEnabledTransport getTransport() {
        return transport;
    }
}
