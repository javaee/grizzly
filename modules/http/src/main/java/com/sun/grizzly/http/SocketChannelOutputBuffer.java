/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2007-2013 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.grizzly.http;

import com.sun.grizzly.tcp.http11.OutputFilter;
import com.sun.grizzly.util.LogMessages;
import com.sun.grizzly.async.AsyncQueueWriteUnit;
import com.sun.grizzly.async.AsyncQueueWriter;
import com.sun.grizzly.async.AsyncWriteCallbackHandler;
import com.sun.grizzly.async.ByteBufferCloner;
import com.sun.grizzly.tcp.FileOutputBuffer;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SocketChannel;
import com.sun.grizzly.util.OutputWriter;
import com.sun.grizzly.tcp.Response;
import com.sun.grizzly.tcp.http11.InternalOutputBuffer;
import com.sun.grizzly.util.ByteBufferFactory;
import com.sun.grizzly.util.buf.ByteChunk;
import com.sun.grizzly.util.buf.CharChunk;

import java.nio.channels.Channel;
import java.nio.channels.SelectionKey;
import java.nio.channels.WritableByteChannel;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Output buffer.
 * Buffer the bytes until the {@link com.sun.grizzly.util.buf.ByteChunk}
 * is full or the request is completed.
 * 
 * @author Jean-Francois Arcand
 * @author Scott Oaks
 * @author Alexey Stashok
 */
public class SocketChannelOutputBuffer extends InternalOutputBuffer
        implements FileOutputBuffer {
    protected static final Logger logger = SelectorThread.logger();

    protected static final int DEFAULT_BUFFER_POOL_SIZE = 16384;

    protected static int maxBufferPoolSize = DEFAULT_BUFFER_POOL_SIZE;

    /**
     * ByteBuffer pool to be used with async write
     */
    protected static Queue<ByteBuffer> bufferPool =
            new ArrayBlockingQueue<ByteBuffer>(maxBufferPoolSize);

    /**
     * {@link ByteBufferCloner} implementation, which is called by Grizzly
     * framework at the time, when asynchronous write queue can not write
     * the buffer directly on socket and instead will put it in queue.
     * This implementation tries to get temporary ByteBuffer from the pool,
     * if no ByteBuffer is available - then new one will be created.
     */
    protected final ByteBufferCloner asyncHttpByteBufferCloner =
            new ByteBufferClonerImpl();

    /**
     * {@link AsyncWriteCallbackHandler} implementation, which is responsible
     * for returning cloned ByteBuffers to the pool
     */
    private static final AsyncWriteCallbackHandler asyncHttpWriteCallbackHandler =
            new AsyncWriteCallbackHandlerImpl();

    /**
     * Underlying output channel.
     */
    protected Channel channel;

    /**
     * Underlying selection key of the output channel.
     */
    protected SelectionKey selectionKey;

    /**
     * Flag, which indicates if async HTTP write is enabled
     */
    protected boolean isAsyncHttpWriteEnabled;
    
    /**
     * Asynchronous queue writer, which will be used if asyncHttp mode
     * is enabled
     */
    protected AsyncQueueWriter asyncQueueWriter;
    
    /**
     * Underlying ByteByteBuffer
     */
    protected ByteBuffer outputByteBuffer;

    
    /**
     * ACK static bytes.
     */
    protected final static ByteBuffer ACK = 
            ByteBuffer.wrap("HTTP/1.1 100 Continue\r\n\r\n".getBytes());
        
    /**
     * Default max cached bytes.  
     */
    protected static int maxBufferedBytes = Constants.MAX_BUFFERED_BYTES;

    // Do not allow any more writing.
    protected boolean discardBytes = false;
    
    // ----------------------------------------------------------- Constructors
    

    /**
     * Alternate constructor.
     */
    public SocketChannelOutputBuffer(Response response, 
            int sendBufferSize, boolean useSocketBuffer) {
        super();

        this.useSocketBuffer = useSocketBuffer;
        
        if (sendBufferSize > maxBufferedBytes){
            maxBufferedBytes = sendBufferSize;
        }

        this.response = response;
        headers = response.getMimeHeaders();
        outputStreamOutputBuffer = new OutputStreamOutputBuffer();
        filterLibrary = new OutputFilter[0];
        activeFilters = new OutputFilter[0];
        lastActiveFilter = -1;

        committed = false;
        finished = false;
        if (!useSocketBuffer){
            outputStream = new NIOOutputStream();
            outputByteBuffer = createByteBuffer(sendBufferSize);
        } else {
            buf = new byte[sendBufferSize];
            socketBuffer = new ByteChunk();
            socketBuffer.setByteOutputChannel(this);
        }
    }

    
    // ------------------------------------------------------------- Properties

    
    /**
     * Create the output {@link ByteBuffer}
     */
    protected ByteBuffer createByteBuffer(int size){
        return ByteBuffer.allocate(size);
    }


    /**
     * Set the underlying socket output stream.
     */
    @SuppressWarnings("UnusedDeclaration")
    public void setChannel(Channel channel) {
        this.channel = channel;
    }

    
    /**
     * Return the underlying SocketChannel
     */
    public Channel getChannel(){
        return channel;
    }


    /**
     * Gets the underlying selection key of the output channel.
     * @return the underlying selection key of the output channel.
     */
    public SelectionKey getSelectionKey() {
        return selectionKey;
    }

    
    /**
     * Sets the underlying selection key of the output channel.
     * @param selectionKey the underlying selection key of the output channel.
     */
    public void setSelectionKey(final SelectionKey selectionKey) {
        this.selectionKey = selectionKey;
        
        if (selectionKey != null) {
            channel = selectionKey.channel();
        } else {
            channel = null;
        }
    }

    /**
     * Is async HTTP write enabled.
     * @return <tt>true</tt>, if async HTTP write enabled, or <tt>false</tt>
     * otherwise.
     */
    @SuppressWarnings("UnusedDeclaration")
    public boolean isAsyncHttpWriteEnabled() {
        return isAsyncHttpWriteEnabled;
    }

    /**
     * Set if async HTTP write enabled.
     * @param isAsyncHttpWriteEnabled <tt>true</tt>, if async HTTP write
     * enabled, or <tt>false</tt> otherwise.
     */
    public void setAsyncHttpWriteEnabled(boolean isAsyncHttpWriteEnabled) {
        this.isAsyncHttpWriteEnabled = isAsyncHttpWriteEnabled;
    }

    /**
     * Gets the asynchronous queue writer, which will be used if asyncHttp mode
     * is enabled
     * 
     * @return The asynchronous queue writer, which will be used if asyncHttp
     * mode is enabled
     */
    @SuppressWarnings("UnusedDeclaration")
    protected AsyncQueueWriter getAsyncQueueWriter() {
        return asyncQueueWriter;
    }

    /**
     * Sets the asynchronous queue writer, which will be used if asyncHttp mode
     * is enabled
     * 
     * @param asyncQueueWriter The asynchronous queue writer, which will be
     * used if asyncHttp mode is enabled
     */
    protected void setAsyncQueueWriter(AsyncQueueWriter asyncQueueWriter) {
        this.asyncQueueWriter = asyncQueueWriter;
    }
    

    // --------------------------------------------------------- Public Methods

    /**
     * Send an acknoledgement without buffering.
     */
    @Override
    public void sendAck() throws IOException {
        if (!committed)
            flushChannel(ACK.slice());
    }

    @Override
    protected void write(CharChunk cc) {
        if (!useSocketBuffer) {
            int start = cc.getStart();
            int end = cc.getEnd();
            char[] cbuf = cc.getBuffer();
            for (int i = start; i < end; i++) {
                char c = cbuf[i];
                if ((c <= 31 && c != 9) || c == 127 || c > 255) {
                    c = ' ';
                }

                if (outputByteBuffer.position() >= outputByteBuffer.limit()) {
                    try {
                        flushBuffer();
                    } catch (IOException ignore) {
                    }
                }
                outputByteBuffer.putChar(c);

            }

        } else {
            super.write(cc);
        }
    }

    @Override
    protected void write(String s, boolean replacingCRLF) {
        if (!useSocketBuffer) {
            if (s == null) {
                return;
            }

            // From the Tomcat 3.3 HTTP/1.0 connector
            int len = s.length();
            for (int i = 0; i < len; i++) {
                char c = s.charAt(i);
                // Note:  This is clearly incorrect for many strings,
                // but is the only consistent approach within the current
                // servlet framework.  It must suffice until servlet output
                // streams properly encode their output.
                if ((c <= 31 && c != 9) || c == 127 || c > 255) {
                    c = ' ';
                }

                byte b = (byte) c;
                if (replacingCRLF && (b == 10 || b == 13)) {  // \n or \r
                    b = 32; // space
                }

                if (outputByteBuffer.position() >= outputByteBuffer.limit()) {
                    try {
                        flushBuffer();
                    } catch (IOException ignore) {
                    }
                }
                outputByteBuffer.put(b);
            }

        } else {
            super.write(s, replacingCRLF);
        }
    }

    /**
     * Callback to write data from the buffer.
     */
    @Override
    public void realWriteBytes(byte cbuf[], int off, int len)
        throws IOException {
        realWriteBytes0( cbuf, off, len);
    }
        
    /**
     * Introduce private realWriteBytes0 method to avoid undesirable effect
     * coming for recursion call.
     */
    private void realWriteBytes0(byte cbuf[], int off, int len) throws IOException {
        if (discardBytes) {
            return;
        }
        
        if (len > 0) {
            if (!useSocketBuffer) {
                int remaining = outputByteBuffer.remaining();
                if (len > remaining) {
                    if (outputByteBuffer.capacity() >= maxBufferedBytes) {
                        outputByteBuffer.put(cbuf,off,remaining);
                        flushBuffer();
                        realWriteBytes0(cbuf,off+remaining,len-remaining);
                        return;
                    } else {                
                        int size = Math.max(outputByteBuffer.capacity() * 2,
                                            len + outputByteBuffer.position());
                        ByteBuffer tmp = ByteBuffer.allocate(size);
                        outputByteBuffer.flip();
                        tmp.put(outputByteBuffer);
                        outputByteBuffer = tmp;
                    }
                }
                outputByteBuffer.put(cbuf, off, len);
            } else {
                flushChannel(ByteBuffer.wrap(cbuf,off,len));
            }
        }
    }
    
    
    /**
     * Flush the buffer by looping until the {@link ByteBuffer} is empty
     * @param bb the ByteBuffer to write.
     */   
    public void flushChannel(ByteBuffer bb) throws IOException {
        if (logger.isLoggable(Level.FINEST)) {
            logger.finest("flushChannel isAsyncHttpWriteEnabled=" +
                    isAsyncHttpWriteEnabled + " bb=" + bb);
        }
        
        if (discardBytes) return;
        
        if (SelectorThread.isEnableNioLogging()){
            ByteBuffer dd = bb.duplicate();
            
            int length = dd.limit();
            byte[] dump = new byte[length];
            dd.get(dump,0,length);
            // not applying logging rules here as this is enabled with a special
            // flag
            logger.info(new String(dump));         
        }

        if (!isAsyncHttpWriteEnabled) {
            OutputWriter.flushChannel(((SocketChannel) channel), bb);
            bb.clear();
        } else if (asyncQueueWriter != null) {
            Future future = asyncQueueWriter.write(selectionKey, bb,
                    asyncHttpWriteCallbackHandler, null,
                    asyncHttpByteBufferCloner);
            if (logger.isLoggable(Level.FINEST)) {
                logger.finest("async flushChannel isDone=" + future.isDone());
            }

            if (!bb.hasRemaining()) {
                bb.clear();
            }
        } else {
            if (logger.isLoggable(Level.WARNING)) {
                logger.warning(LogMessages.WARNING_GRIZZLY_HTTP_SCOB_FLUSH_CHANNEL_ERROR());
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    public boolean isSupportFileSend() {
        final boolean hasChannel = (response.getChannel() != null);
        if (lastActiveFilter == -1) {
            return hasChannel;
        } else if (lastActiveFilter == 0 && activeFilters[0].getEncodingName().equals("identity")) {
            return hasChannel;
        }

        return false;
    }

    /**
     * {@inheritDoc}
     */
    public long sendFile(FileChannel fileChannel, long position,
            long length) throws IOException {
        return fileChannel.transferTo(position, length,
                (WritableByteChannel) channel);
    }


    /**
     * Flush the buffered bytes,
     */
    @Override
    public void flush() throws IOException{
        super.flush();
        flushBuffer();
    }
    
    
     /**
     * End request.
     * 
     * @throws IOException an undelying I/O error occured
     */
    @Override
    public void endRequest()
        throws IOException {
        super.endRequest();
        flushBuffer();
    }  
    
    /**
     * Writes bytes to the underlying channel.
     */
    public void flushBuffer() throws IOException{
        if (!useSocketBuffer && outputByteBuffer.position() != 0){
            outputByteBuffer.flip();
            flushChannel(outputByteBuffer);
            outputByteBuffer.clear();
        }
    }

    
    /**
     * Recycle the output buffer. This should be called when closing the 
     * connection.
     */
    @Override
    public void recycle() {        
        discardBytes = false;
        response.recycle();
        if (useSocketBuffer) {
            socketBuffer.recycle();
        }
        pos = 0;

        // Recycle filters
        for (int i = 0; i <= lastActiveFilter; i++) {
            activeFilters[i].recycle();
        }

        lastActiveFilter = -1;
        committed = false;
        finished = false;
        if (outputByteBuffer != null){
            outputByteBuffer.clear();
        }

        channel = null;
    }

    
    // ---------------------------------------------- Class helper ----------//
    
    /**
     * OutputBuffer delegating all writes to the {@link OutputWriter}
     */
    private final class NIOOutputStream extends OutputStream{   
        
        @Override
        public void write(byte[] b, int off, int len) throws IOException{           
            realWriteBytes(b,off,len);
        }
        
        public void write(int b) throws IOException {
            write((byte)b);
        }
        
        public void write(byte b) throws IOException {
            if(!outputByteBuffer.hasRemaining()) {
                ByteBuffer tmp = ByteBuffer.allocate(
                        outputByteBuffer.capacity() * 2);
                outputByteBuffer.flip();
                tmp.put(outputByteBuffer);
                outputByteBuffer = tmp;
            }
            outputByteBuffer.put(b);
        }
    }


    /**
     * {@link AsyncWriteCallbackHandler} implementation, which is responsible
     * for returning cloned ByteBuffers to the pool
     */
    protected static class AsyncWriteCallbackHandlerImpl implements
            AsyncWriteCallbackHandler {
        public void onWriteCompleted(SelectionKey key,
                AsyncQueueWriteUnit writtenRecord) {
            if (logger.isLoggable(Level.FINEST)) {
                logger.finest("onWriteCompleted isCloned=" +
                        writtenRecord.isCloned());
            }
            
            if (writtenRecord.isCloned()) {
                releaseAsyncWriteUnit(writtenRecord);
            }
        }

        public void onException(Exception exception, SelectionKey key,
                ByteBuffer buffer, Queue<AsyncQueueWriteUnit> remainingQueue) {
            if (logger.isLoggable(Level.FINEST)) {
                logger.finest("onException key=" + key +
                        " exception=" + exception);
            }
            returnBuffer(buffer);
            
            for(AsyncQueueWriteUnit unit : remainingQueue) {
                releaseAsyncWriteUnit(unit);
            }
        }

        protected boolean releaseAsyncWriteUnit(AsyncQueueWriteUnit unit) {
            return returnBuffer(unit.getByteBuffer());
        }

        protected boolean returnBuffer(ByteBuffer buffer) {
            buffer.clear();
            int size = buffer.capacity();
            if (logger.isLoggable(Level.FINEST)) {
                logger.finest("return buffer buffer=" + buffer + " maxSize=" +
                        maxBufferedBytes);
            }

            if (size <= maxBufferedBytes) {
                boolean wasReturned = bufferPool.offer(buffer);
                if (logger.isLoggable(Level.FINEST)) {
                    logger.finest("return buffer to pool. result=" + wasReturned);
                }

                return wasReturned;
            }

            return false;
        }
    }

    /**
     * {@link ByteBufferCloner} implementation, which is called by Grizzly
     * framework at the time, when asynchronous write queue can not write
     * the buffer directly on socket and instead will put it in queue.
     * This implementation tries to get temporary ByteBuffer from the pool,
     * if no ByteBuffer is available - then new one will be created.
     */
    protected final class ByteBufferClonerImpl
            implements ByteBufferCloner {
        
        public ByteBuffer clone(ByteBuffer originalByteBuffer) {
            if (logger.isLoggable(Level.FINEST)) {
                logger.finest("clone buffer=" + originalByteBuffer +
                        " maxBufferedBytes=" + maxBufferedBytes);
            }
            
            int size = originalByteBuffer.remaining();

            ByteBuffer clone = null;
            
            if (size <= maxBufferedBytes) {
                clone = bufferPool.poll();
            }

            if (logger.isLoggable(Level.FINEST)) {
                logger.finest("clone buffer from pool=" + clone);
            }
            
            if (clone == null || clone.remaining() < size) {
                int allocateSize = Math.max(size, maxBufferedBytes / 2);
                clone = createByteBuffer(allocateSize,
                        originalByteBuffer.isDirect());
            }

            /**
             * If originalByteBuffer is SocketChannelOutputBuffer's
             * outputByteBuffer - then we can avoid copying bytes.
             */
            if (originalByteBuffer == outputByteBuffer) {
                outputByteBuffer = clone;
                outputByteBuffer.limit(outputByteBuffer.position());
                clone = originalByteBuffer;
            } else {
                clone.put(originalByteBuffer);
                clone.flip();
            }
            return clone;
        }
    }
    
    /**
     * Reset current response.
     * 
     * @throws IllegalStateException if the response has already been committed
     */
    @Override
    public void reset() {
        super.reset();
        if (outputByteBuffer != null) {
            outputByteBuffer.clear();
        }
    }
    
    /**
     * Stop buffering bytes, discard any upcoming writes.
     */
    public void discardUpstreamBytes(){
        try{
            flush();
        } catch (IOException ex){
            if (logger.isLoggable(Level.WARNING)) {
                logger.log(Level.WARNING,
                           LogMessages.WARNING_GRIZZLY_HTTP_SCOB_FLUSH_UPSTREAM_ERROR(),
                           ex);
            }
        }
        discardBytes = true;
    }
    
    /**
     * Create an instance of {@link ByteBuffer}
     * @param size
     * @param isDirect
     * @return
     */
    private static ByteBuffer createByteBuffer(int size, boolean isDirect) {
        return ByteBufferFactory.allocateView(size, isDirect);
    }

    /**
     * Return the maximum of buffered bytes.
     * @return
     */
    @SuppressWarnings("UnusedDeclaration")
    public static int getMaxBufferedBytes() {
        return maxBufferedBytes;
    }
    
    /**
     * Set the maximum number of bytes before flushing the {@link ByteBuffer}
     * content.
     * 
     * @param aMaxBufferedBytes
     */
    public static void setMaxBufferedBytes(int aMaxBufferedBytes) {
        maxBufferedBytes = aMaxBufferedBytes;
    }

    /**
     * Set the maximum size of cached {@link ByteBuffer} when async
     * write is enabled.
     * 
     * @param size
     */
    public static void setMaxBufferPoolSize(int size) {
        int poolSize = (size >= 0) ? size : DEFAULT_BUFFER_POOL_SIZE;

        if (maxBufferPoolSize == poolSize) return;
        
        maxBufferPoolSize = poolSize;

        bufferPool = new ArrayBlockingQueue<ByteBuffer>(maxBufferPoolSize);
    }

    /**
     * Return the maximum number of cached {@link ByteBuffer}
     * @return
     */
    @SuppressWarnings("UnusedDeclaration")
    public static int getMaxBufferPoolSize() {
        return maxBufferPoolSize;
    }

    public ByteBuffer getOutputByteBuffer(){
        return outputByteBuffer;
    }
}
