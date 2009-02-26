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

package org.glassfish.grizzly.nio.transport;

import org.glassfish.grizzly.Context;
import org.glassfish.grizzly.nio.AbstractNIOAsyncQueueWriter;
import org.glassfish.grizzly.nio.NIOTransport;
import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.SelectionKey;
import java.util.concurrent.Future;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.CompletionHandler;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Interceptor;
import org.glassfish.grizzly.WriteResult;
import org.glassfish.grizzly.asyncqueue.AsyncQueue;
import org.glassfish.grizzly.asyncqueue.AsyncQueueWriter;
import org.glassfish.grizzly.asyncqueue.AsyncWriteQueueRecord;
import org.glassfish.grizzly.asyncqueue.MessageCloner;
import org.glassfish.grizzly.nio.AbstractNIOConnection;
import org.glassfish.grizzly.nio.NIOConnection;

/**
 * The TCP transport {@link AsyncQueueWriter} implementation, based on
 * the Java NIO
 *
 * @author Alexey Stashok
 */
public class TCPNIOAsyncQueueWriter extends AbstractNIOAsyncQueueWriter {

    public TCPNIOAsyncQueueWriter(NIOTransport transport) {
        super(transport);
    }

    @Override
    public Future<WriteResult<Buffer, SocketAddress>> write(
            Connection connection, SocketAddress dstAddress, Buffer buffer,
            CompletionHandler<WriteResult<Buffer, SocketAddress>> completionHandler,
            Interceptor<WriteResult> interceptor,
            MessageCloner<Buffer> cloner) throws IOException {

        if (dstAddress != null) {
            throw new UnsupportedOperationException(
                    "Destination address should be null for TCP!");
        }
        
        return super.write(connection, null, buffer, completionHandler,
                interceptor, cloner);
    }
    
    protected int write0(Connection connection, SocketAddress dstAddress,
            Buffer buffer,
            WriteResult<Buffer, SocketAddress> currentResult)
            throws IOException {
        return ((TCPNIOTransport) transport).write(connection, buffer, currentResult);
    }

    @Override
    protected AsyncQueue<AsyncWriteQueueRecord> getAsyncWriteQueue(
            Connection connection) {
        AbstractNIOConnection nioConnection =
                (AbstractNIOConnection) connection;
        return nioConnection.getAsyncWriteQueue();
    }

    @Override
    protected AsyncQueue<AsyncWriteQueueRecord> obtainAsyncWriteQueue(
            Connection connection) {
        AbstractNIOConnection nioConnection =
                (AbstractNIOConnection) connection;
        return nioConnection.obtainAsyncWriteQueue();
    }

    protected void onReadyToWrite(Connection connection) throws IOException {
        NIOConnection nioConnection = (NIOConnection) connection;

        transport.getSelectorHandler().registerKey(
                nioConnection.getSelectorRunner(),
                nioConnection.getSelectionKey(), SelectionKey.OP_WRITE);
    }

    public Context context() {
        return null;
    }

    public void beforeProcess(Context context) throws IOException {
    }

    public void afterProcess(Context context) throws IOException {
    }
}
