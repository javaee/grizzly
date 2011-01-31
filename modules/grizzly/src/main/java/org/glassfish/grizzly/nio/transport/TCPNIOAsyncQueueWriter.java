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
import org.glassfish.grizzly.Interceptor;
import org.glassfish.grizzly.asyncqueue.AsyncWriteQueueRecord;
import org.glassfish.grizzly.nio.AbstractNIOAsyncQueueWriter;
import org.glassfish.grizzly.nio.NIOTransport;
import java.io.IOException;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.IOEvent;
import org.glassfish.grizzly.ThreadCache;
import org.glassfish.grizzly.WriteResult;
import org.glassfish.grizzly.asyncqueue.AsyncQueueWriter;
import org.glassfish.grizzly.memory.BufferArray;
import org.glassfish.grizzly.nio.NIOConnection;
import org.glassfish.grizzly.utils.DebugPoint;

/**
 * The TCP transport {@link AsyncQueueWriter} implementation, based on
 * the Java NIO
 *
 * @author Alexey Stashok
 */
public final class TCPNIOAsyncQueueWriter extends AbstractNIOAsyncQueueWriter {

    public TCPNIOAsyncQueueWriter(NIOTransport transport) {
        super(transport);
    }

    @Override
    protected AsyncWriteQueueRecord createRecord(
            final Connection connection,
            final Object message,
            final Future future,
            final WriteResult currentResult,
            final CompletionHandler completionHandler,
            final Interceptor interceptor,
            final Object dstAddress,
            final Buffer outputBuffer,
            final boolean isCloned) {
        return TCPNIOQueueRecord.create(connection, message, future,
                currentResult, completionHandler, interceptor, dstAddress,
                outputBuffer, isCloned);
    }



    @Override
    protected int write0(Connection connection,
            AsyncWriteQueueRecord queueRecord) throws IOException {
                
        final WriteResult currentResult = queueRecord.getCurrentResult();
        final Buffer buffer = queueRecord.getOutputBuffer();
        final TCPNIOQueueRecord record = (TCPNIOQueueRecord) queueRecord;

        final int written;
        
        final int oldPos = buffer.position();
        if (buffer.isComposite()) {
            BufferArray array = record.bufferArray;
            if (array == null) {
                array = buffer.toBufferArray();
                record.bufferArray = array;
            }

            written = ((TCPNIOTransport) transport).write0(connection, array);


            if (!buffer.hasRemaining()) {
                array.restore();
            }

        } else {
            written = ((TCPNIOTransport) transport).write0(connection, buffer);
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

    @Override
    protected final void onReadyToWrite(Connection connection) throws IOException {
        final NIOConnection nioConnection = (NIOConnection) connection;
        nioConnection.enableIOEvent(IOEvent.WRITE);
    }

    private static class TCPNIOQueueRecord extends AsyncWriteQueueRecord {

        private static final ThreadCache.CachedTypeIndex<TCPNIOQueueRecord> CACHE_IDX =
                ThreadCache.obtainIndex(TCPNIOQueueRecord.class, 2);

        public static AsyncWriteQueueRecord create(
                final Connection connection,
                final Object message,
                final Future future,
                final WriteResult currentResult,
                final CompletionHandler completionHandler,
                final Interceptor interceptor,
                final Object dstAddress,
                final Buffer outputBuffer,
                final boolean isCloned) {

            final TCPNIOQueueRecord asyncWriteQueueRecord =
                    ThreadCache.takeFromCache(CACHE_IDX);

            if (asyncWriteQueueRecord != null) {
                asyncWriteQueueRecord.isRecycled = false;
                asyncWriteQueueRecord.set(connection, message, future,
                        currentResult, completionHandler, interceptor,
                        dstAddress, outputBuffer, isCloned);

                return asyncWriteQueueRecord;
}

            return new TCPNIOQueueRecord(connection, message, future,
                    currentResult, completionHandler, interceptor, dstAddress,
                    outputBuffer, isCloned);
        }

        private BufferArray bufferArray;
        
        public TCPNIOQueueRecord(final Connection connection,
                final Object message,
                final Future future,
                final WriteResult currentResult,
                final CompletionHandler completionHandler,
                final Interceptor interceptor,
                final Object dstAddress,
                final Buffer outputBuffer,
                final boolean isCloned) {
            super(connection, message, future, currentResult, completionHandler,
                    interceptor, dstAddress, outputBuffer, isCloned);
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

            if (bufferArray != null) {
                bufferArray.recycle();
                bufferArray = null;
            }
            
            ThreadCache.putToCache(CACHE_IDX, this);
        }
    }
}
