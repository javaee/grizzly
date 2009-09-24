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

package com.sun.grizzly.nio.transport;

import java.util.concurrent.Future;
import com.sun.grizzly.Buffer;
import com.sun.grizzly.CompletionHandler;
import com.sun.grizzly.ReadResult;
import com.sun.grizzly.WriteResult;
import com.sun.grizzly.nio.AbstractNIOConnection;
import com.sun.grizzly.IOEvent;
import com.sun.grizzly.nio.SelectorRunner;
import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.logging.Level;
import java.util.logging.Logger;
import com.sun.grizzly.Connection;
import com.sun.grizzly.Grizzly;
import com.sun.grizzly.impl.FutureImpl;
import com.sun.grizzly.streams.StreamReader;
import com.sun.grizzly.streams.StreamWriter;
import com.sun.grizzly.utils.conditions.Condition;

/**
 * {@link com.sun.grizzly.Connection} implementation
 * for the {@link TCPNIOTransport}
 *
 * @author Alexey Stashok
 */
public class TCPNIOConnection extends AbstractNIOConnection {
    private Logger logger = Grizzly.logger;

    private final TCPNIOStreamReader streamReader;

    private final TCPNIOStreamWriter streamWriter;

    private SocketAddress localSocketAddress;
    private SocketAddress peerSocketAddress;
    
    public TCPNIOConnection(TCPNIOTransport transport,
            SelectableChannel channel) {
        super(transport);
        
        this.channel = channel;
        readBufferSize = transport.getReadBufferSize();
        writeBufferSize = transport.getWriteBufferSize();

        resetAddresses();
        
        streamReader = new TCPNIOStreamReader(this);
        streamWriter = new TCPNIOStreamWriter(this);
    }

    @Override
    protected void setSelectionKey(SelectionKey selectionKey) {
        super.setSelectionKey(selectionKey);
    }

    @Override
    protected void setSelectorRunner(SelectorRunner selectorRunner) {
        super.setSelectorRunner(selectorRunner);
    }

    @Override
    public StreamReader getStreamReader() {
        return streamReader;
    }

    @Override
    public StreamWriter getStreamWriter() {
        return streamWriter;
    }

    @Override
    protected void preClose() {
        try {
            transport.fireIOEvent(IOEvent.CLOSED, this);
        } catch (IOException e) {
            Grizzly.logger.log(Level.FINE, "Unexpected IOExcption occurred, " +
                    "when firing CLOSE event");
        }
    }

    /**
     * Returns the address of the endpoint this <tt>Connection</tt> is
     * connected to, or <tt>null</tt> if it is unconnected.
     * @return the address of the endpoint this <tt>Connection</tt> is
     *         connected to, or <tt>null</tt> if it is unconnected.
     */
    @Override
    public SocketAddress getPeerAddress() {
        return peerSocketAddress;
    }
    
    /**
     * Returns the local address of this <tt>Connection</tt>,
     * or <tt>null</tt> if it is unconnected.
     * @return the local address of this <tt>Connection</tt>,
     *      or <tt>null</tt> if it is unconnected.
     */
    @Override
    public SocketAddress getLocalAddress() {
        return localSocketAddress;
    }

    protected void resetAddresses() {
        if (channel != null) {
            if (channel instanceof SocketChannel) {
                localSocketAddress =
                        ((SocketChannel) channel).socket().getLocalSocketAddress();
                peerSocketAddress =
                        ((SocketChannel) channel).socket().getRemoteSocketAddress();
            } else if (channel instanceof ServerSocketChannel) {
                localSocketAddress =
                        ((ServerSocketChannel) channel).socket().getLocalSocketAddress();
                peerSocketAddress = null;
            }
        }
    }

    @Override
    public Future<ReadResult<Buffer, SocketAddress>> read(final Buffer buffer,
            final CompletionHandler<ReadResult<Buffer, SocketAddress>> completionHandler,
            final Condition<ReadResult<Buffer, SocketAddress>> condition)
            throws IOException {
        final FutureImpl<ReadResult<Buffer, SocketAddress>> future =
                new FutureImpl<ReadResult<Buffer, SocketAddress>>();
        final ReadResult<Buffer, SocketAddress> readResult =
                new ReadResult<Buffer, SocketAddress>(TCPNIOConnection.this,
                buffer, getPeerAddress(), 0);
        
        streamReader.notifyCondition(new Condition<StreamReader>() {
            @Override
            public boolean check(final StreamReader streamReader) {
                if (condition == null) {
                    return streamReader.hasAvailableData();
                } else {
                    readResult.setReadSize(streamReader.availableDataSize());
                    return condition.check(readResult);
                }
            }
        }, new CompletionHandler<Integer>() {


            @Override
            public void cancelled(Connection connection) {
                completionHandler.cancelled(connection);
                future.cancel(false);
            }

            @Override
            public void failed(Connection connection, Throwable throwable) {
                completionHandler.failed(connection, throwable);
                future.failure(throwable);
            }

            @Override
            public void completed(Connection connection, Integer result) {
                readResult.setReadSize(result);
                if (completionHandler != null) {
                    completionHandler.completed(connection, readResult);
                }

                future.setResult(readResult);            }

            @Override
            public void updated(Connection connection, Integer result) {
                readResult.setReadSize(result);
                completionHandler.updated(connection, readResult);
            }

        });

        return future;
    }

    @Override
    public Future<WriteResult<Buffer, SocketAddress>> write(
            final SocketAddress dstAddress, final Buffer buffer,
            final CompletionHandler<WriteResult<Buffer, SocketAddress>> completionHandler)
            throws IOException {
        final FutureImpl<WriteResult<Buffer, SocketAddress>> future =
                new FutureImpl<WriteResult<Buffer, SocketAddress>>();

        streamWriter.writeBuffer(buffer);
        streamWriter.flush(new CompletionHandler<Integer>() {
            private final WriteResult<Buffer, SocketAddress> writeResult =
                    new WriteResult<Buffer, SocketAddress>(TCPNIOConnection.this,
                    buffer, getPeerAddress(), 0);
            
            @Override
            public void cancelled(Connection connection) {
                completionHandler.cancelled(connection);
                future.cancel(false);
            }

            @Override
            public void failed(Connection connection, Throwable throwable) {
                completionHandler.failed(connection, throwable);
                future.failure(throwable);
            }

            @Override
            public void completed(Connection connection, Integer result) {
                writeResult.setWrittenSize(result);
                if (completionHandler != null) {
                    completionHandler.completed(connection, writeResult);
                }
                
                future.setResult(writeResult);
            }

            @Override
            public void updated(Connection connection, Integer result) {
                writeResult.setWrittenSize(result);
                completionHandler.updated(connection, writeResult);
            }

        });

        return future;
    }
}
    
