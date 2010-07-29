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

import com.sun.grizzly.nio.AbstractNIOAsyncQueueReader;
import com.sun.grizzly.Connection;
import com.sun.grizzly.ReadResult;
import com.sun.grizzly.nio.NIOTransport;
import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.SelectionKey;
import com.sun.grizzly.Buffer;
import com.sun.grizzly.CompletionHandler;
import com.sun.grizzly.Interceptor;
import com.sun.grizzly.asyncqueue.AsyncQueueReader;
import com.sun.grizzly.asyncqueue.AsyncReadQueueRecord;
import com.sun.grizzly.impl.SafeFutureImpl;
import com.sun.grizzly.nio.NIOConnection;

/**
 * The TCP transport {@link AsyncQueueReader} implementation, based on
 * the Java NIO
 *
 * @author Alexey Stashok
 */
public final class TCPNIOAsyncQueueReader extends AbstractNIOAsyncQueueReader {
    public TCPNIOAsyncQueueReader(NIOTransport transport) {
        super(transport);
    }

    @Override
    protected int read0(final Connection connection, Buffer buffer,
            final ReadResult<Buffer, SocketAddress> currentResult) throws IOException {

        final int oldPosition = buffer != null ? buffer.position() : 0;
        if ((buffer = ((TCPNIOTransport) transport).read(connection, buffer)) != null) {
            final int readBytes = buffer.position() - oldPosition;
            currentResult.setMessage(buffer);
            currentResult.setReadSize(currentResult.getReadSize() + readBytes);
            currentResult.setSrcAddress((SocketAddress) connection.getPeerAddress());

            return readBytes;
        }

        return 0;
    }

    protected void addRecord(Connection connection,
            Buffer buffer,
            CompletionHandler completionHandler,
            Interceptor<ReadResult> interceptor) {
        final AsyncReadQueueRecord record = AsyncReadQueueRecord.create(
                buffer, SafeFutureImpl.create(),
                ReadResult.create(connection),
                completionHandler, interceptor);
        ((TCPNIOConnection) connection).getAsyncReadQueue().getQueue().add(record);
    }

    @Override
    protected void onReadyToRead(Connection connection) throws IOException {
        NIOConnection nioConnection = (NIOConnection) connection;

        transport.getSelectorHandler().registerKey(
                nioConnection.getSelectorRunner(),
                nioConnection.getSelectionKey(), SelectionKey.OP_READ);
    }
}
