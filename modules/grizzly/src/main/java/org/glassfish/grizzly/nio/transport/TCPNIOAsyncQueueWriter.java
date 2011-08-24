/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2011 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.nio.transport;

import java.util.concurrent.Future;
import org.glassfish.grizzly.CompletionHandler;
import org.glassfish.grizzly.asyncqueue.AsyncWriteQueueRecord;
import org.glassfish.grizzly.nio.AbstractNIOAsyncQueueWriter;
import org.glassfish.grizzly.nio.NIOTransport;
import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.IOEvent;
import org.glassfish.grizzly.ThreadCache;
import org.glassfish.grizzly.WriteResult;
import org.glassfish.grizzly.asyncqueue.AsyncQueueWriter;
import org.glassfish.grizzly.nio.NIOConnection;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport.DirectByteBufferRecord;
import org.glassfish.grizzly.utils.DebugPoint;

/**
 * The TCP transport {@link AsyncQueueWriter} implementation, based on
 * the Java NIO
 *
 * @author Alexey Stashok
 */
public final class TCPNIOAsyncQueueWriter extends AbstractNIOAsyncQueueWriter {

    public TCPNIOAsyncQueueWriter(final NIOTransport transport) {
        super(transport);
    }

    @Override
    protected AsyncWriteQueueRecord createRecord(
            final Connection connection,
            final Buffer message,
            final Future<WriteResult<Buffer, SocketAddress>> future,
            final WriteResult<Buffer, SocketAddress> currentResult,
            final CompletionHandler<WriteResult<Buffer, SocketAddress>> completionHandler,
            final SocketAddress dstAddress,
            final boolean isEmptyRecord) {
        return TCPNIOQueueRecord.create(connection, message, future,
                currentResult, completionHandler, dstAddress, isEmptyRecord);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected int write0(final NIOConnection connection,
            final AsyncWriteQueueRecord queueRecord) throws IOException {
                
        final WriteResult<Buffer, SocketAddress> currentResult =
                queueRecord.getCurrentResult();
        final Buffer buffer = queueRecord.getMessage();

        final int written;
        
        final int oldPos = buffer.position();
        final int bufferSize = buffer.remaining();
        
        if (bufferSize == 0) {
            written = 0;
        } else {
            final DirectByteBufferRecord directByteBufferRecord =
                    TCPNIOTransport.obtainDirectByteBuffer(bufferSize);
                                    
            try {
                final ByteBuffer directByteBuffer = directByteBufferRecord.strongRef;
                final SocketChannel socketChannel = (SocketChannel) connection.getChannel();

                fillByteBuffer(buffer, 0, bufferSize, directByteBuffer);
                written = TCPNIOTransport.flushByteBuffer(
                        socketChannel, directByteBuffer);

            } catch (IOException e) {
                // Mark connection as closed remotely.
                ((TCPNIOConnection) connection).close0(null, false).markForRecycle(true);
                throw e;
            } finally {
                TCPNIOTransport.releaseDirectByteBuffer(directByteBufferRecord);
            }

        }

        if (written > 0) {
            buffer.position(oldPos + written);
        }

        ((TCPNIOConnection) connection).onWrite(buffer, written);

        if (currentResult != null) {
            currentResult.setMessage(buffer);
            currentResult.setWrittenSize(currentResult.getWrittenSize()
                    + written);
            currentResult.setDstAddress(
                    connection.getPeerAddress());
        }

        return written;
    }

    private static void fillByteBuffer(final Buffer src, final int offset,
            final int size, final ByteBuffer dstByteBuffer) {
        
        dstByteBuffer.limit(size);
        final int oldPos = src.position();
        src.position(oldPos + offset);
        
        src.get(dstByteBuffer);
        
        dstByteBuffer.position(0);
        src.position(oldPos);
    }

    @Override
    protected final void onReadyToWrite(Connection connection) throws IOException {
        final NIOConnection nioConnection = (NIOConnection) connection;
        nioConnection.enableIOEvent(IOEvent.WRITE);
    }

    private static final class TCPNIOQueueRecord extends AsyncWriteQueueRecord {

        private static final ThreadCache.CachedTypeIndex<TCPNIOQueueRecord> CACHE_IDX =
                ThreadCache.obtainIndex(TCPNIOQueueRecord.class, 2);

        public static AsyncWriteQueueRecord create(
                final Connection connection,
                final Buffer message,
                final Future future,
                final WriteResult currentResult,
                final CompletionHandler completionHandler,
                final Object dstAddress,
                final boolean isEmptyRecord) {

            final TCPNIOQueueRecord asyncWriteQueueRecord =
                    ThreadCache.takeFromCache(CACHE_IDX);

            if (asyncWriteQueueRecord != null) {
                asyncWriteQueueRecord.isRecycled = false;
                asyncWriteQueueRecord.set(connection, message, future,
                        currentResult, completionHandler,
                        dstAddress, isEmptyRecord);

                return asyncWriteQueueRecord;
            }

            return new TCPNIOQueueRecord(connection, message, future,
                    currentResult, completionHandler, dstAddress, isEmptyRecord);
        }

        public TCPNIOQueueRecord(final Connection connection,
                final Buffer message,
                final Future future,
                final WriteResult currentResult,
                final CompletionHandler completionHandler,
                final Object dstAddress,
                final boolean isEmptyRecord) {
            super(connection, message, future, currentResult, completionHandler,
                    dstAddress, isEmptyRecord);
        }

        @Override
        public void recycle() {
            checkRecycled();
            reset();
            isRecycled = true;
            if (Grizzly.isTrackingThreadCache()) {
                recycleTrack = new DebugPoint(new Exception(),
                        Thread.currentThread().getName());
            }
            
            ThreadCache.putToCache(CACHE_IDX, this);
        }
    }
}
