/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012 Oracle and/or its affiliates. All rights reserved.
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

import java.io.EOFException;
import java.io.IOException;
import java.lang.ref.SoftReference;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.ThreadCache;
import org.glassfish.grizzly.memory.BufferArray;
import org.glassfish.grizzly.memory.Buffers;
import org.glassfish.grizzly.memory.ByteBufferArray;
import org.glassfish.grizzly.memory.CompositeBuffer;
import org.glassfish.grizzly.memory.MemoryManager;
import org.glassfish.grizzly.utils.Exceptions;

/**
 * TCP NIO Transport utils
 * 
 * @author Alexey Stashok
 */
public class TCPNIOUtils {
    static final Logger LOGGER = TCPNIOTransport.LOGGER;
    
    private static final int MAX_READ_ATTEMPTS = 3;
    
    private static final ThreadCache.CachedTypeIndex<CachedIORecord> CACHE_IDX =
            ThreadCache.obtainIndex("io-cache", CachedIORecord.class, 4);

    public static int writeCompositeBuffer(final TCPNIOConnection connection,
            final CompositeBuffer buffer) throws IOException {
        
        final int bufferSize = calcWriteBufferSize(connection, buffer.remaining());
        
        final int oldPos = buffer.position();
        final int oldLim = buffer.limit();
        buffer.limit(oldPos + bufferSize);
        
        final SocketChannel socketChannel = (SocketChannel) connection.getChannel();
        
        final CachedIORecord ioRecord = obtainCachedIORecord();
        final BufferArray bufferArray = buffer.toBufferArray();
        int written = 0;
        
        fill(bufferArray, bufferSize, ioRecord);
        ioRecord.finishBufferSlice();
        
        final int arraySize = ioRecord.getArraySize();
        
        try {
            written = arraySize != 1
                    ? flushByteBuffers(socketChannel, ioRecord.getArray(), 0, arraySize)
                    : flushByteBuffer(socketChannel, ioRecord.getArray()[0]);

            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.log(Level.FINE, "TCPNIOConnection ({0}) (composite) write {1} bytes", new Object[]{
                            connection, written
                        });
            }
        } finally {
            bufferArray.restore();
            bufferArray.recycle();
            ioRecord.release();
        }
        
        Buffers.setPositionLimit(buffer, oldPos + written, oldLim);
        return written;
    }

    public static int writeSimpleBuffer(final TCPNIOConnection connection,
            final Buffer buffer) throws IOException {
        final SocketChannel socketChannel = (SocketChannel)connection.getChannel();
        final int oldPos = buffer.position();
        final int oldLim = buffer.limit();
        
        final int written;
        
        if (buffer.isDirect()) {
            final ByteBuffer directByteBuffer = buffer.toByteBuffer();
            final int pos = directByteBuffer.position();
            
            try {
                written = flushByteBuffer(socketChannel, directByteBuffer);
            } finally {
                directByteBuffer.position(pos);
            }
        } else {
            final CachedIORecord ioRecord = obtainCachedIORecord();
            
            final int bufferSize = calcWriteBufferSize(connection, buffer.remaining());
            buffer.limit(oldPos + bufferSize);
            
            final ByteBuffer directByteBuffer = ioRecord.obtainDirectBuffer(bufferSize);
            fill(buffer, bufferSize, directByteBuffer);
            
            try {
                written = flushByteBuffer(socketChannel, directByteBuffer);
            } finally {
                ioRecord.release();
            }
        }

        Buffers.setPositionLimit(buffer, oldPos + written, oldLim);
        if(LOGGER.isLoggable(Level.FINE))
            LOGGER.log(Level.FINE, "TCPNIOConnection ({0}) (plain) write {1} bytes", new Object[] {
                connection, written
            });
        return written;
    }

    public static int flushByteBuffer(final SocketChannel channel,
            final ByteBuffer byteBuffer) throws IOException {
        return channel.write(byteBuffer);
    }

    public static int flushByteBuffers(final SocketChannel channel,
            final ByteBuffer byteBuffer[], final int firstBufferOffest,
            final int numberOfBuffers) throws IOException {
        return (int) channel.write(byteBuffer, firstBufferOffest, numberOfBuffers);
    }

    private static void fill(Buffer src, int size, ByteBuffer dstByteBuffer)
    {
        dstByteBuffer.limit(size);
        int oldPos = src.position();
        src.get(dstByteBuffer);
        dstByteBuffer.position(0);
        src.position(oldPos);
    }

    static void fill(final BufferArray bufferArray,
            final int totalBufferSize, final CachedIORecord ioRecord) {
        
        final Buffer buffers[] = bufferArray.getArray();
        final int size = bufferArray.size();
        
        int remaining = totalBufferSize;
        
        for (int i = 0; i < size; i++) {
            
            final Buffer buffer = buffers[i];
            assert !buffer.isComposite();
            
            final int bufferSize = buffer.remaining();
            if (bufferSize == 0) {
                continue;
            } else if(buffer.isDirect()) {
                ioRecord.finishBufferSlice();
                ioRecord.putToArray(buffer.toByteBuffer());
            } else {
                ByteBuffer currentDirectBufferSlice = ioRecord.getDirectBufferSlice();
                
                if(currentDirectBufferSlice == null) {
                    final ByteBuffer directByteBuffer = ioRecord.getDirectBuffer();
                    if(directByteBuffer == null) {
                        currentDirectBufferSlice = ioRecord.obtainDirectBuffer(remaining);
                    } else {
                        currentDirectBufferSlice = ioRecord.sliceBuffer();
                    }
                    
                    ioRecord.putToArray(currentDirectBufferSlice);
                }
                
                final int oldLim = currentDirectBufferSlice.limit();
                currentDirectBufferSlice.limit(currentDirectBufferSlice.position() + bufferSize);
                buffer.get(currentDirectBufferSlice);
                currentDirectBufferSlice.limit(oldLim);
            }
            
            remaining -= bufferSize;
        }

    }

    private static int calcWriteBufferSize(final TCPNIOConnection connection,
            final int bufferSize) {
        return Math.min(bufferSize, (connection.getWriteBufferSize() * 3) / 2);
    }

    static CachedIORecord obtainCachedIORecord() {
        final CachedIORecord record =
                ThreadCache.takeFromCache(CACHE_IDX);
        if (record != null) {
            return record;

        }

        return new CachedIORecord();
    }

    public static Buffer allocateAndReadBuffer(final TCPNIOConnection connection)
            throws IOException {
        
        final MemoryManager memoryManager =
                connection.getTransport().getMemoryManager();
        
        int read;
        Throwable error = null;
        Buffer buffer = null;
        
        try {
            final int receiveBufferSize = connection.getReadBufferSize();
        
            if (!memoryManager.willAllocateDirect(receiveBufferSize)) {
                final CachedIORecord ioRecord = obtainCachedIORecord();
                final ByteBuffer directByteBuffer =
                        ioRecord.obtainDirectBuffer(receiveBufferSize);
                
                try {
                    read = readSimpleByteBuffer(connection, directByteBuffer);
                    directByteBuffer.flip();
                    buffer = memoryManager.allocate(read);
                    buffer.put(directByteBuffer);
                } finally {
                    ioRecord.release();
                }
            } else {
                buffer = memoryManager.allocateAtLeast(receiveBufferSize);
                read = readBuffer(connection, buffer);
            }
        } catch (Throwable e) {
            error = e;
            read = -1;
        }
        
        if (read > 0) {
            buffer.position(read);
            buffer.allowBufferDispose(true);
        } else {
            if (buffer != null) {
                buffer.dispose();
            }
            
            if (read < 0) {
                throw error != null
                        ? Exceptions.makeIOException(error)
                        : new EOFException();
            }
            
            buffer = Buffers.EMPTY_BUFFER;
        }
        
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.log(Level.FINE, "TCPNIOConnection ({0}) (allocated) read {1} bytes", new Object[]{
                        connection, read
                    });
        }
        return buffer;
    }

    public static int readBuffer(final TCPNIOConnection connection,
                                 final Buffer buffer) throws IOException {
        if (buffer.isComposite()) {
            return readCompositeBuffer(connection, (CompositeBuffer) buffer);
        } else {
            return readSimpleBuffer(connection, buffer);
        }

    }

    public static int readCompositeBuffer(final TCPNIOConnection connection,
            final CompositeBuffer buffer) throws IOException {
        
//        final Thread currentThread = Thread.currentThread();
//        final boolean isSelectorThread =
//                (currentThread instanceof WorkerThread) &&
//                ((WorkerThread) currentThread).isSelectorThread();
        
        final SocketChannel socketChannel = (SocketChannel) connection.getChannel();
        final int oldPos = buffer.position();
        final ByteBufferArray array = buffer.toByteBufferArray();
        final ByteBuffer byteBuffers[] = array.getArray();
        final int size = array.size();
        
        final int read;
//        if (!isSelectorThread) {
//            read = doReadInLoop(socketChannel, byteBuffers, 0, size);
//        } else {
            read = (int) socketChannel.read(byteBuffers, 0, size);
//        }
        
        array.restore();
        array.recycle();
        
        if (read > 0) {
            buffer.position(oldPos + read);
        }
        
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.log(Level.FINE, "TCPNIOConnection ({0}) (nonallocated, composite) read {1} bytes", new Object[]{
                        connection, read
                    });
        }
        
        return read;
    }

    public static int readSimpleBuffer(final TCPNIOConnection connection,
            final Buffer buffer) throws IOException {
//        final Thread currentThread = Thread.currentThread();
//        final boolean isSelectorThread = (currentThread instanceof WorkerThread)
//                && ((WorkerThread) currentThread).isSelectorThread();
        
        final SocketChannel socketChannel = (SocketChannel) connection.getChannel();
        final int oldPos = buffer.position();
        final ByteBuffer byteBuffer = buffer.toByteBuffer();
        final int bbOldPos = byteBuffer.position();
        final int read;
        
//        if (!isSelectorThread) {
//            read = doReadInLoop(socketChannel, byteBuffer);
//        } else {
            read = socketChannel.read(byteBuffer);
//        }
        
        if (read > 0) {
            byteBuffer.position(bbOldPos);
            buffer.position(oldPos + read);
        }
        
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.log(Level.FINE, "TCPNIOConnection ({0}) (nonallocated, simple) read {1} bytes", new Object[]{
                        connection, read
                    });
        }
        
        return read;
    }

    private static int readSimpleByteBuffer(final TCPNIOConnection tcpConnection,
            final ByteBuffer byteBuffer) throws IOException {
        
        final SocketChannel socketChannel = (SocketChannel) tcpConnection.getChannel();
        return socketChannel.read(byteBuffer);

//        final Thread currentThread = Thread.currentThread();
//        final boolean isSelectorThread = (currentThread instanceof WorkerThread)
//                && ((WorkerThread) currentThread).isSelectorThread();
//        
//        final SocketChannel socketChannel = (SocketChannel) tcpConnection.getChannel();
//        
//        final int read;
//        if (!isSelectorThread) {
//            read = doReadInLoop(socketChannel, byteBuffer);
//        } else {
//            read = socketChannel.read(byteBuffer);
//        }
//        
//        return read;
    }

    private static int doReadInLoop(final SocketChannel socketChannel,
            final ByteBuffer byteBuffer) throws IOException {
        
        int read = 0;
        int readAttempt = 0;
        int readNow;
        
        while ((readNow = socketChannel.read(byteBuffer)) > 0) {
            read += readNow;
            if (!byteBuffer.hasRemaining() || ++readAttempt >= MAX_READ_ATTEMPTS) {
                return read;
            }
        }
        if (read == 0) {
            read = readNow;
        }
        
        return read;
    }

    private static int doReadInLoop(final SocketChannel socketChannel,
            final ByteBuffer byteBuffers[], final int offset, final int length)
            throws IOException {
        
        int read = 0;
        int readAttempt = 0;
        final ByteBuffer lastByteBuffer = byteBuffers[length - 1];
        int readNow;
        while ((readNow = (int) socketChannel.read(byteBuffers, offset, length)) > 0) {
            read += readNow;
            if (!lastByteBuffer.hasRemaining() || ++readAttempt >= MAX_READ_ATTEMPTS) {
                return read;
            }
        }
        
        if (read == 0) {
            read = readNow;
        }
        
        return read;
    }
    
    static final class CachedIORecord {

        private ByteBuffer directBuffer;
        private int sliceOffset;
        private ByteBuffer directBufferSlice;
        private SoftReference<ByteBuffer> softRef;
        private ByteBuffer array[];
        private int arraySize;

        CachedIORecord() {
            array = new ByteBuffer[8];
        }

        ByteBuffer getDirectBuffer() {
            return directBuffer;
        }

        ByteBuffer getDirectBufferSlice() {
            return directBufferSlice;
        }

        ByteBuffer obtainDirectBuffer(int size) {
            ByteBuffer byteBuffer;
            if ((byteBuffer = switchToStrong()) != null && byteBuffer.remaining() >= size) {
                return byteBuffer;
            } else {
                byteBuffer = ByteBuffer.allocateDirect(size);
                reset(byteBuffer);
                return byteBuffer;
            }
        }

        ByteBuffer sliceBuffer() {
            int oldLim = directBuffer.limit();
            Buffers.setPositionLimit(directBuffer, sliceOffset, directBuffer.capacity());
            directBufferSlice = directBuffer.slice();
            Buffers.setPositionLimit(directBuffer, 0, oldLim);
            return directBufferSlice;
        }

        void finishBufferSlice() {
            if (directBufferSlice != null) {
                directBufferSlice.flip();
                sliceOffset += directBufferSlice.remaining();
                directBufferSlice = null;
            }
        }

        public ByteBuffer[] getArray() {
            return array;
        }

        public int getArraySize() {
            return arraySize;
        }

        void putToArray(ByteBuffer byteBuffer) {
            ensureArraySize();
            array[arraySize++] = byteBuffer;
        }

        void release() {
            if (directBuffer != null) {
                directBuffer.clear();
                switchToSoft();
            }
            Arrays.fill(array, 0, arraySize, null);
            arraySize = 0;
            directBufferSlice = null;
            sliceOffset = 0;
            ThreadCache.putToCache(TCPNIOUtils.CACHE_IDX, this);
        }

        ByteBuffer switchToStrong() {
            if (directBuffer == null && softRef != null) {
                directBuffer = directBufferSlice = softRef.get();
            }
            return directBuffer;
        }

        void switchToSoft() {
            if (directBuffer != null && softRef == null) {
                softRef = new SoftReference<ByteBuffer>(directBuffer);
            }
            directBuffer = null;
        }

        void reset(ByteBuffer byteBuffer) {
            directBuffer = directBufferSlice = byteBuffer;
            softRef = null;
        }

        private void ensureArraySize() {
            if (arraySize == array.length) {
                array = Arrays.copyOf(array, (arraySize * 3) / 2 + 1);
            }
        }
    }
}
