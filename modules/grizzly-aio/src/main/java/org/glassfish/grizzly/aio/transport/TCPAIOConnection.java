/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.aio.transport;

import java.io.EOFException;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.ConnectionProbe;
import org.glassfish.grizzly.aio.AIOConnection;
import org.glassfish.grizzly.IOEvent;
import java.io.IOException;
import java.net.SocketAddress;
import java.net.StandardSocketOption;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousChannel;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.ReadResult;
import org.glassfish.grizzly.Result;
import org.glassfish.grizzly.WriteResult;
import org.glassfish.grizzly.impl.FutureImpl;
import org.glassfish.grizzly.impl.SafeFutureImpl;
import org.glassfish.grizzly.memory.ByteBufferArray;
import org.glassfish.grizzly.memory.MemoryManager;

/**
 * {@link org.glassfish.grizzly.Connection} implementation
 * for the {@link TCPAIOTransport}
 *
 * @author Alexey Stashok
 */
public class TCPAIOConnection extends AIOConnection {
    private static final Logger LOGGER = Grizzly.logger(TCPAIOConnection.class);

    private SocketAddress localSocketAddress;
    private SocketAddress peerSocketAddress;

    public TCPAIOConnection(TCPAIOTransport transport,
            AsynchronousChannel channel) {
        super(transport);
        
        this.channel = channel;
        
        try {
            resetProperties();
        } catch (IOException e) {
            LOGGER.log(Level.FINE, "Unexpected IOExcption occurred, " +
                    "when reseting TCPAIOConnection proeprties", e);            
        }
    }

    @Override
    protected void preClose() {
        try {
            transport.fireIOEvent(IOEvent.CLOSED, this, null);
        } catch (IOException e) {
            LOGGER.log(Level.FINE, "Unexpected IOExcption occurred, " +
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

    protected final void resetProperties() throws IOException {
        if (channel != null) {
            setReadBufferSize(transport.getReadBufferSize());
            setWriteBufferSize(transport.getWriteBufferSize());

            if (channel instanceof AsynchronousSocketChannel) {
                localSocketAddress =
                        ((AsynchronousSocketChannel) channel).getLocalAddress();
                peerSocketAddress =
                        ((AsynchronousSocketChannel) channel).getRemoteAddress();
            } else if (channel instanceof AsynchronousServerSocketChannel) {
                localSocketAddress =
                        ((AsynchronousServerSocketChannel) channel).getLocalAddress();
                peerSocketAddress = null;
            }
        }
    }

    @Override
    public void setReadBufferSize(final int readBufferSize) {
        final AsynchronousSocketChannel socketChannel =
                (AsynchronousSocketChannel) channel;

        try {
            final int socketReadBufferSize =
                    socketChannel.getOption(StandardSocketOption.SO_RCVBUF);
            if (readBufferSize != -1) {
                if (readBufferSize > socketReadBufferSize) {
                    socketChannel.setOption(StandardSocketOption.SO_RCVBUF,
                            readBufferSize);
                }
                super.setReadBufferSize(readBufferSize);
            } else {
                super.setReadBufferSize(socketReadBufferSize);
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Error setting read buffer size", e);
        }
    }

    @Override
    public void setWriteBufferSize(int writeBufferSize) {
        final AsynchronousSocketChannel socketChannel =
                (AsynchronousSocketChannel) channel;

        try {
            final int socketWriteBufferSize =
                    socketChannel.getOption(StandardSocketOption.SO_SNDBUF);
            
            if (writeBufferSize != -1) {
                if (writeBufferSize > socketWriteBufferSize) {
                    socketChannel.setOption(StandardSocketOption.SO_SNDBUF,
                            writeBufferSize);
                }
                super.setWriteBufferSize(writeBufferSize);
            } else {
                super.setWriteBufferSize(socketWriteBufferSize);
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Error setting write buffer size", e);
        }
    }

    /**
     * Method will be called, when some data was read on the connection
     */
    protected final void onRead(final Buffer data, final int size) {
        notifyProbesRead(this, data, size);
    }

    /**
     * Method will be called, when some data was written on the connection
     */
    protected final void onWrite(final Buffer data, final int size) {
        notifyProbesWrite(this, data, size);
    }

    /**
     * Set the monitoringProbes array directly.
     * @param monitoringProbes
     */
    void setMonitoringProbes(final ConnectionProbe[] monitoringProbes) {
        this.monitoringConfig.addProbes(monitoringProbes);
    }

    final void onConnect() {
        notifyProbesConnect(this);
    }

    public final void enableIOEvent(final IOEvent ioEvent) throws IOException {
        if (ioEvent == IOEvent.READ) {
            readAsync(null, null, null);
        }
//        final SelectionKeyHandler selectionKeyHandler =
//                transport.getSelectionKeyHandler();
//        final int interest =
//                selectionKeyHandler.ioEvent2SelectionKeyInterest(ioEvent);
//
//        if (interest == 0) return;
//
//        notifyIOEventEnabled(this, ioEvent);
//
//        final SelectorHandler selectorHandler = transport.getSelectorHandler();
//
//        selectorHandler.registerKeyInterest(selectorRunner, selectionKey,
//                selectionKeyHandler.ioEvent2SelectionKeyInterest(ioEvent));
    }

    public final void disableIOEvent(final IOEvent ioEvent) throws IOException {
//        final SelectionKeyHandler selectionKeyHandler =
//                transport.getSelectionKeyHandler();
//        final int interest =
//                selectionKeyHandler.ioEvent2SelectionKeyInterest(ioEvent);
//
//        if (interest == 0) return;
//
//        notifyIOEventDisabled(this, ioEvent);
//
//        final SelectorHandler selectorHandler = transport.getSelectorHandler();
//
//        selectorHandler.unregisterKeyInterest(selectorRunner, selectionKey, interest);
    }    

    private void closeNoException() {
        try {
            close();
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Error closing the connection", e);
        }
    }
    
    public int readBlocking(Buffer buffer, final ReadResult result)
            throws IOException {

        if (readResult.isDone()) {
            return readFromLastResult(buffer, result);
        }
        
        final FutureImpl<Integer> future = SafeFutureImpl.<Integer>create();
        final boolean isAllocate = (buffer == null);
        if (isAllocate) {
            final MemoryManager memoryManager = transport.getMemoryManager();
            buffer = memoryManager.allocateAtLeast(getReadBufferSize());

            readBlockingSimple(buffer.toByteBuffer(),
                    readResult.set(future, buffer, result),
                    blockingAllocatedBufferReadCompletionHandler);
        } else {        
            if (!buffer.isComposite()) {
                readBlockingSimple(buffer.toByteBuffer(),
                        readResult.set(future, buffer, result),
                        blockingReadCompletionHandler);
            } else {
                final ByteBufferArray array = buffer.toByteBufferArray();

                final ByteBuffer[] byteBuffers = array.getArray();
                final int size = array.size();

                readBlockingComposite(byteBuffers, 0, size,
                        readResult.set(future, buffer, result, array),
                        blockingReadCompositeCompletionHandler);
            }
        }
        
        try {
            final int readBytes = future.get();
            readResult.reset();
            return readBytes;
        } catch (InterruptedException e) {
            throw new IOException(e);
        } catch (ExecutionException e) {
            final Throwable cause = e.getCause();
            if (IOException.class.isAssignableFrom(cause.getClass())) {
                throw (IOException) cause;
            }
            
            throw new IOException(cause);
        }
    }

    public <A> void readBlockingSimple(
            final ByteBuffer byteBuffer, final A attachment,
            final java.nio.channels.CompletionHandler<Integer, A> completionHandler) {
        final AsynchronousSocketChannel socketChannel =
                (AsynchronousSocketChannel) channel;
        socketChannel.read(byteBuffer, readTimeoutMillis,
                TimeUnit.MILLISECONDS, attachment, completionHandler);
    }
    
    public <A> void readBlockingComposite(
            final ByteBuffer[] byteBuffers, final int offset, final int size,
            final A attachment,
            final java.nio.channels.CompletionHandler<Long, A> completionHandler) {
        final AsynchronousSocketChannel socketChannel =
                (AsynchronousSocketChannel) channel;
        socketChannel.read(byteBuffers, offset, size, readTimeoutMillis,
                TimeUnit.MILLISECONDS, attachment, completionHandler);
    }
    
    public int readFromLastResult(Buffer buffer, final ReadResult result) {
        final int bytesRead;
        
        if (buffer == null) {
            buffer = readResult.getBuffer();
            bytesRead = readResult.getBytesProcessed();
            readResult.reset();
        } else {
            final Buffer srcBuffer = readResult.getBuffer();
            final int pos = srcBuffer.position();
            bytesRead = Math.min(srcBuffer.remaining(), buffer.remaining());
            buffer.put(srcBuffer, pos, bytesRead);
            
            if (!srcBuffer.hasRemaining()) {
                srcBuffer.tryDispose();
                readResult.reset();
            }           
        }
        
        if (result != null) {
            result.setMessage(buffer);
            result.setSrcAddress(getPeerAddress());
            result.setReadSize(result.getReadSize() + bytesRead);
        }
        
        return bytesRead;
    }
    
    public int writeBlocking(final Buffer buffer, final WriteResult result)
            throws IOException {

        final FutureImpl<Integer> future = SafeFutureImpl.<Integer>create();
        
        if (!buffer.isComposite()) {
            writeBlockingSimple(buffer.toByteBuffer(),
                    writeResult.set(future, buffer, result),
                    blockingWriteCompletionHandler);
        } else {
            final ByteBufferArray array = buffer.toByteBufferArray();

            final ByteBuffer[] byteBuffers = array.getArray();
            final int size = array.size();
            
            writeBlockingComposite(byteBuffers, 0, size,
                    writeResult.set(future, buffer, result, array),
                    blockingWriteCompositeCompletionHandler);
        }
        
        try {
            return future.get();
        } catch (InterruptedException e) {
            throw new IOException(e);
        } catch (ExecutionException e) {
            final Throwable cause = e.getCause();
            if (IOException.class.isAssignableFrom(cause.getClass())) {
                throw (IOException) cause;
            }
            
            throw new IOException(cause);
        }
    }
    
    public <A> void writeBlockingSimple(
            final ByteBuffer byteBuffer, final A attachment,
            final java.nio.channels.CompletionHandler<Integer, A> completionHandler) {
        final AsynchronousSocketChannel socketChannel =
                (AsynchronousSocketChannel) channel;
        socketChannel.write(byteBuffer, writeTimeoutMillis,
                TimeUnit.MILLISECONDS, attachment, completionHandler);
    }
    
    public <A> void writeBlockingComposite(
            final ByteBuffer[] byteBuffers, final int offset, final int size,
            final A attachment,
            final java.nio.channels.CompletionHandler<Long, A> completionHandler) {
        final AsynchronousSocketChannel socketChannel =
                (AsynchronousSocketChannel) channel;
        socketChannel.write(byteBuffers, offset, size, writeTimeoutMillis,
                TimeUnit.MILLISECONDS, attachment, completionHandler);
    }
    
    public void readAsync(Buffer buffer,
            final Object attachment, final ReadResult result) throws IOException {

        if (readResult.isDone()) {
            readFromLastResult(buffer, result);
            transport.getIOStrategy().executeIoEvent(this, IOEvent.READ);
            return;
        }
                
        final boolean isAllocate = (buffer == null);
        if (isAllocate) {
            final MemoryManager memoryManager = transport.getMemoryManager();
            buffer = memoryManager.allocateAtLeast(getReadBufferSize());

            readSimple(buffer.toByteBuffer(),
                    readResult.set(attachment, buffer, result),
                    allocatedBufferReadCompletionHandler);
        } else {
            if (buffer.hasRemaining()) {
                if (buffer.isComposite()) {
                    final ByteBufferArray array = buffer.toByteBufferArray();

                    final ByteBuffer[] byteBuffers = array.getArray();
                    final int size = array.size();

                    readComposite(byteBuffers, 0, size,
                            readResult.set(attachment, buffer, result, array),
                            readCompositeCompletionHandler);
                } else {
                    readSimple(buffer.toByteBuffer(),
                            readResult.set(attachment, buffer, result),
                            readCompletionHandler);
                }
            }
        }
    }

    private <A> void readSimple(final ByteBuffer byteBuffer, final A attachment,
            final java.nio.channels.CompletionHandler<Integer, A> completionHandler) {

        ((AsynchronousSocketChannel) channel).read(byteBuffer,
                attachment, completionHandler);
    }
    
    private <A> void readComposite(final ByteBuffer[] byteBuffers,
            final int offset, final int size,
            final A attachment,
            final java.nio.channels.CompletionHandler<Long, A> completionHandler) {

        final AsynchronousSocketChannel socketChannel =
                (AsynchronousSocketChannel) channel;
        
        socketChannel.read(byteBuffers, offset, size, Long.MAX_VALUE,
                TimeUnit.MILLISECONDS, attachment, completionHandler);
    }
    
    public void writeAsync(final Buffer buffer, final Object attachment,
            final WriteResult result) {
        
        if (buffer.isComposite()) {
            final ByteBufferArray array = buffer.toByteBufferArray();

            final ByteBuffer[] byteBuffers = array.getArray();
            final int size = array.size();

            writeComposite(byteBuffers, 0, size,
                    writeResult.set(attachment, buffer, result, array),
                    writeCompositeCompletionHandler);
        } else {
            writeSimple(buffer.toByteBuffer(),
                    writeResult.set(attachment, buffer, result),
                    writeCompletionHandler);
        }
    }

    private <A> void writeSimple(final ByteBuffer byteBuffer, final A attachment,
            final java.nio.channels.CompletionHandler<Integer, A> completionHandler) {

        ((AsynchronousSocketChannel) channel).write(byteBuffer,
                attachment, completionHandler);
    }
    
    private <A> void writeComposite(final ByteBuffer[] byteBuffers,
            final int offset, final int size,
            final A attachment,
            final java.nio.channels.CompletionHandler<Long, A> completionHandler) {

        final AsynchronousSocketChannel socketChannel =
                (AsynchronousSocketChannel) channel;
        
        socketChannel.write(byteBuffers, offset, size, Long.MAX_VALUE,
                TimeUnit.MILLISECONDS, attachment, completionHandler);
    }

    
    private final AllocatedBufferReadCompletionHandler allocatedBufferReadCompletionHandler =
            new AllocatedBufferReadCompletionHandler();
    
    private final ReadCompletionHandler<Integer> readCompletionHandler =
            new ReadCompletionHandler<>()  {

                @Override
                protected int getProcessedBytes(final Integer processedBytesObj) {
                    return processedBytesObj;
                }
            };
    
    private final ReadCompletionHandler<Long> readCompositeCompletionHandler =
            new ReadCompletionHandler<>()  {

                @Override
                protected int getProcessedBytes(final Long processedBytesObj) {
                    return processedBytesObj.intValue();
                }
            };
    
    private final BlockingReadCompletionHandler<Integer> blockingReadCompletionHandler =
            new BlockingReadCompletionHandler<>() {
                @Override
                protected int getProcessedBytes(final Integer processedBytesObj) {
                    return processedBytesObj;
                }                
            };    

    private final BlockingReadCompletionHandler<Long> blockingReadCompositeCompletionHandler =
            new BlockingReadCompletionHandler<>() {
                @Override
                protected int getProcessedBytes(final Long processedBytesObj) {
                    return processedBytesObj.intValue();
                }                
            };
    
    private final BlockingAllocatedBufferReadCompletionHandler blockingAllocatedBufferReadCompletionHandler =
            new BlockingAllocatedBufferReadCompletionHandler();
    
    private final WriteCompletionHandler<Integer> writeCompletionHandler =
            new WriteCompletionHandler<>()  {

                @Override
                protected int getProcessedBytes(final Integer processedBytesObj) {
                    return processedBytesObj;
                }
            };
    
    private final WriteCompletionHandler<Long> writeCompositeCompletionHandler =
            new WriteCompletionHandler<>()  {

                @Override
                protected int getProcessedBytes(final Long processedBytesObj) {
                    return processedBytesObj.intValue();
                }
            }; 

    private final BlockingWriteCompletionHandler<Integer> blockingWriteCompletionHandler =
            new BlockingWriteCompletionHandler<>() {
                @Override
                protected int getProcessedBytes(final Integer processedBytesObj) {
                    return processedBytesObj;
                }                
            };
    
    private final BlockingWriteCompletionHandler<Long> blockingWriteCompositeCompletionHandler =
            new BlockingWriteCompletionHandler<>() {
                @Override
                protected int getProcessedBytes(final Long processedBytesObj) {
                    return processedBytesObj.intValue();
                }                
            };
    
    private abstract class OperationCompletionHandler<A>
            implements java.nio.channels.CompletionHandler<A, IOResult> {

        @Override
        public final void completed(final A processedBytesObj,
                final IOResult attachment) {

            final int processedBytes = getProcessedBytes(processedBytesObj);
            final IOResult ioResult = attachment.done(processedBytes);
            final Buffer buffer = ioResult.getBuffer();

            if (processedBytes > 0) {
                buffer.position(ioResult.getOldBufferPos() + processedBytes);
                final Result result = ioResult.getResult();
                if (result != null) {
                    updateResult(result, buffer, processedBytes);
                }
            }

            if (buffer.isComposite()) {
                if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.log(Level.FINE, "TCPAIOConnection ({0}) (composite) "
                            + "{1} processed {2} bytes",
                            new Object[]{TCPAIOConnection.this, getClass(),
                                processedBytes});
                    restoreCompositeBufferArray(
                            ioResult.getCompositeBufferArray(), ioResult);
                }                
            } else {
                if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.log(Level.FINE, "TCPAIOConnection ({0}) (plain) "
                            + "{1} processed {2} bytes",
                            new Object[]{TCPAIOConnection.this, getClass(),
                                processedBytes});
                }
            }
            

            notifyProcessed(ioResult, buffer, processedBytes);
        }
        
        protected void restoreCompositeBufferArray(final ByteBufferArray array,
                final IOResult ioResult) {
            array.restore();
            array.recycle();
        }
        
        protected abstract int getProcessedBytes(A processedBytesObj);
        
        protected abstract void updateResult(Result result, Buffer buffer,
                int processedBytes);
        protected abstract void notifyProcessed(IOResult attachment,
                Buffer buffer, int processedBytes);
    }    
       
    private abstract class ReadCompletionHandler<A>
            extends OperationCompletionHandler<A> {

        @Override
        protected void notifyProcessed(final IOResult attachment,
                final Buffer buffer, final int read) {
            
            onRead(buffer, read);
            if (read < 0) {
                failed(new EOFException(), attachment);
                return;
            }
            
            try {
                transport.getIOStrategy().executeIoEvent(TCPAIOConnection.this, IOEvent.READ);
            } catch (Exception e) {
                closeNoException();
            }
        }

        @Override
        protected final void updateResult(final Result result, final Buffer buffer,
                final int processedBytes) {
                    
            final ReadResult readResult = (ReadResult) result;
            readResult.setMessage(buffer);
            readResult.setReadSize(readResult.getReadSize() + processedBytes);
            readResult.setSrcAddress(getPeerAddress());
        }

        @Override
        public void failed(final Throwable exc, final IOResult attachment) {
            readResult.reset();
            closeNoException();
        }
    }
    
    private abstract class BlockingReadCompletionHandler<A>
            extends ReadCompletionHandler<A> {

        @Override
        protected final void notifyProcessed(final IOResult attachment,
            final Buffer buffer, final int read) {
                
            onRead(buffer, read);
            ((FutureImpl<Integer>) attachment.getAttachment()).result(read);
        }

        
        @Override
        public void failed(final Throwable e,
                final IOResult attachment) {
                    
            ((FutureImpl<Integer>) attachment.done(-1).getAttachment()).failure(e);
        }        
    }    
    
    private final class BlockingAllocatedBufferReadCompletionHandler
            extends BlockingReadCompletionHandler<Integer> {
        
        @Override
        public void failed(final Throwable e,
                final IOResult attachment) {
            attachment.getBuffer().dispose();
            super.failed(e, attachment);
        }

        @Override
        protected int getProcessedBytes(final Integer processedBytesObj) {
            return processedBytesObj;
        }
    } 
     
    private final class AllocatedBufferReadCompletionHandler
            extends ReadCompletionHandler<Integer> {

        @Override
        public void failed(final Throwable e, final IOResult attachment) {
            attachment.getBuffer().dispose();
            super.failed(e, attachment);
        }

        @Override
        protected int getProcessedBytes(Integer processedBytesObj) {
            return processedBytesObj;
        }
    }
    
    private abstract class WriteCompletionHandler<A>
            extends OperationCompletionHandler<A> {

        @Override
        protected void notifyProcessed(final IOResult attachment,
                final Buffer buffer, final int written) {

            onWrite(buffer, written);

            try {
                transport.getIOStrategy().executeIoEvent(TCPAIOConnection.this, IOEvent.WRITE);
            } catch (Exception e) {
                closeNoException();
            }

        }

        @Override
        protected final void updateResult(final Result result, final Buffer buffer,
                final int processedBytes) {
                    
            final WriteResult writeResult = (WriteResult) result;
            writeResult.setMessage(buffer);
            writeResult.setWrittenSize(writeResult.getWrittenSize()
                    + processedBytes);
            writeResult.setDstAddress(
                    getPeerAddress());
        }

        @Override
        public void failed(final Throwable exc, final IOResult attachment) {
            closeNoException();
        }        
    }

    private abstract class BlockingWriteCompletionHandler<A>
            extends WriteCompletionHandler<A> {

        @Override
        protected final void notifyProcessed(final IOResult attachment,
            final Buffer buffer, final int written) {
                
            onWrite(buffer, written);

            if (buffer.hasRemaining()) {
                attachment.oldBufferPos = buffer.position();
                
                if (buffer.isComposite()) {
                    final ByteBufferArray compositeBufferArray = 
                            attachment.getCompositeBufferArray();
                    writeBlockingComposite(compositeBufferArray.getArray(),
                            0, compositeBufferArray.size(),
                            attachment, blockingWriteCompositeCompletionHandler);
                } else {
                    writeBlockingSimple(buffer.toByteBuffer(), attachment,
                            blockingWriteCompletionHandler);
                }
                
                return;
            }
            
            ((FutureImpl<Integer>) attachment.getAttachment()).result(written);
        }

        
        @Override
        public void failed(final Throwable e,
                final IOResult attachment) {
                    
            ((FutureImpl<Integer>) attachment.done(-1).getAttachment()).failure(e);
        }

        @Override
        protected void restoreCompositeBufferArray(final ByteBufferArray array,
                final IOResult ioResult) {
            if (!ioResult.buffer.hasRemaining()) {
                super.restoreCompositeBufferArray(array, ioResult);
            }
        }

        
    }
    
    public final IOResult getLastReadResult() {
        return readResult;
    }
    
    public final IOResult getLastWriteResult() {
        return writeResult;
    }
    
    private final IOResult readResult = new IOResult();
    private final IOResult writeResult = new IOResult();
    
    public static final class IOResult {
        private volatile int dummy;
        
        private boolean isDone;
        private Object attachment;
        private Buffer buffer;
        private int oldBufferPos;
        private int bytesProcessed;
        private Result result;
        
        private ByteBufferArray compositeBufferArray;
        
        private IOResult set(final Object attachment, final Buffer buffer,
                final Result result) {
            return set(attachment, buffer, result, null);
        }

        private IOResult set(final Object attachment, final Buffer buffer,
                final Result result,
                final ByteBufferArray compositeBufferArray) {
            this.attachment = attachment;
            this.buffer = buffer;
            this.oldBufferPos = buffer.position();
            this.compositeBufferArray = compositeBufferArray;
            this.isDone = false;
            this.bytesProcessed = 0;
            this.result = result;
            
            dummy++;
            return this;
        }
        
        public IOResult done(final int bytesProcessed) {
            if (dummy != 0) {
                isDone = true;
                this.bytesProcessed = bytesProcessed;
                dummy++;
                
                return this;
            }
            
            return null;
        }

        public Object getAttachment() {
            return attachment;
        }

        public Buffer getBuffer() {
            return buffer;
        }

        public boolean isDone() {
            return isDone;
        }
        
        public int getBytesProcessed() {
            return bytesProcessed;
        }

        public Result getResult() {
            return result;
        }        
        
        public void reset() {
            attachment = null;
            buffer = null;
            oldBufferPos = 0;
            compositeBufferArray = null;
            isDone = false;
            bytesProcessed = 0;
            result = null;
            
            dummy++;
        }
        
        private int getOldBufferPos() {
            return oldBufferPos;
        }

        private ByteBufferArray getCompositeBufferArray() {
            return compositeBufferArray;
        }
    }
}
    
