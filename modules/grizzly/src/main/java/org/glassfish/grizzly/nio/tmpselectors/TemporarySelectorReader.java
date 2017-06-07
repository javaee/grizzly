/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2017 Oracle and/or its affiliates. All rights reserved.
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

import java.io.EOFException;
import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.ReadResult;
import org.glassfish.grizzly.nio.NIOConnection;

/**
 *
 *
 */
public abstract class TemporarySelectorReader {

    public static final int DEFAULT_BUFFER_SIZE = 8192;
    protected final int defaultBufferSize = DEFAULT_BUFFER_SIZE;
    
    public ReadResult<Buffer, SocketAddress> read(
            final Connection<SocketAddress> connection,
            final Buffer message) throws IOException {
        return read(connection, message,
                ((NIOConnection) connection).getBlockingReadTimeout(TimeUnit.MILLISECONDS),
                TimeUnit.MILLISECONDS);
    }

    /**
     * Method reads data to the <tt>message</tt>.
     *
     * @param connection the {@link Connection} to read from
     * @param buffer the message, where data will be read
     * @param timeout operation timeout value value
     * @param timeunit the timeout unit
     * @return {@link Future}, using which it's possible to check the result
     * @throws java.io.IOException
     */
    public ReadResult<Buffer, SocketAddress> read(
            final Connection<SocketAddress> connection, final Buffer buffer,
            final long timeout, final TimeUnit timeunit) throws IOException {

        if (connection == null || !(connection instanceof NIOConnection)) {
            throw new IllegalStateException(
                    "Connection should be NIOConnection and cannot be null");
        }

        final NIOConnection nioConnection = (NIOConnection) connection;
        
        int bytesRead;

        Selector readSelector = null;
        SelectionKey key = null;
        
        final ReadResult<Buffer, SocketAddress> currentResult =
                ReadResult.create(connection, buffer, null, 0);
        
        final SelectableChannel channel = nioConnection.getChannel();
        final long readTimeout = TimeUnit.MILLISECONDS.convert(
                ((timeout < 0) ? 0 : timeout), timeunit);

        try {
            bytesRead = readNow0(nioConnection, buffer, currentResult);

            if (bytesRead == 0) {
                readSelector = getTemporarySelectorIO().getSelectorPool().poll();

                if (readSelector != null) {
                    key = channel.register(readSelector, SelectionKey.OP_READ);
                    key.interestOps(key.interestOps() | SelectionKey.OP_READ);
                    int code = readSelector.select(readTimeout);
                    key.interestOps(
                            key.interestOps() & (~SelectionKey.OP_READ));

                    if (code > 0) {
                        bytesRead = readNow0(nioConnection, buffer, currentResult);
                    }
                }
            }

            if (bytesRead == -1) {
                throw new EOFException();
            } else if (bytesRead == 0) {
                throw new IOException("Blocking read timeout expired");
            }
        } finally {
            getTemporarySelectorIO().getSelectorPool().offer(readSelector, key);
        }

        return currentResult;
    }

    protected abstract int readNow0(NIOConnection connection,
            Buffer buffer, ReadResult<Buffer, SocketAddress> currentResult)
            throws IOException;

    protected abstract TemporarySelectorIO getTemporarySelectorIO();
}
