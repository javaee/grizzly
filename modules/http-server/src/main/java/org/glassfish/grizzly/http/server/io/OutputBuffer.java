/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2011 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.http.server.io;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.CompletionHandler;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.EmptyCompletionHandler;
import org.glassfish.grizzly.WriteResult;
import org.glassfish.grizzly.asyncqueue.AsyncQueueWriter;
import org.glassfish.grizzly.asyncqueue.MessageCloner;
import org.glassfish.grizzly.asyncqueue.TaskQueue;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.http.HttpContent;
import org.glassfish.grizzly.http.HttpResponsePacket;
import org.glassfish.grizzly.http.HttpServerFilter;
import org.glassfish.grizzly.http.server.Constants;
import org.glassfish.grizzly.http.util.Charsets;
import org.glassfish.grizzly.memory.Buffers;
import org.glassfish.grizzly.memory.CompositeBuffer;
import org.glassfish.grizzly.memory.MemoryManager;
import org.glassfish.grizzly.nio.NIOConnection;
import org.glassfish.grizzly.utils.Exceptions;

/**
 * Abstraction exposing both byte and character methods to write content
 * to the HTTP messaging system in Grizzly.
 */
public class OutputBuffer {

    private static final int DEFAULT_BUFFER_SIZE = 1024 * 8;

    private HttpResponsePacket response;

    private FilterChainContext ctx;

    private CompositeBuffer compositeBuffer;

    private Buffer currentBuffer;

    // Buffer, which is used for write(byte[] ...) scenarious to try to avoid
    // byte arrays copying
    private final TemporaryHeapBuffer temporaryWriteBuffer =
            new TemporaryHeapBuffer();
    // The cloner, which will be responsible for cloning temporaryWriteBuffer,
    // if it's not possible to write its content in this thread
    private final ByteArrayCloner cloner = new ByteArrayCloner();
    
    private final List<LifeCycleListener> lifeCycleListeners =
            new ArrayList<LifeCycleListener>(2);
    
    private boolean committed;

    private boolean finished;

    private boolean closed;

    private CharsetEncoder encoder;

    private final Map<String, CharsetEncoder> encoders =
            new HashMap<String, CharsetEncoder>();
    
    private final CharBuffer charBuf = CharBuffer.allocate(1);

    private MemoryManager memoryManager;

    private WriteHandler handler;

    private final AtomicReference<Throwable> asyncError = new AtomicReference<Throwable>();

    private TaskQueue.QueueMonitor monitor;

    private AsyncQueueWriter asyncWriter;

    private int bufferSize = DEFAULT_BUFFER_SIZE;

    /**
     * Flag indicating whether or not async operations are being used on the
     * input streams.
     */
    private boolean asyncEnabled;
    
    private final CompletionHandler<WriteResult> asyncCompletionHandler =
            new EmptyCompletionHandler<WriteResult>() {

                @Override
                public void failed(Throwable throwable) {
                    if (handler != null) {
                        handler.onError(throwable);
                    } else {
                        asyncError.compareAndSet(null, throwable);
                    }
                }
            };


    // ---------------------------------------------------------- Public Methods


    public void initialize(HttpResponsePacket response,
                           FilterChainContext ctx) {

        this.response = response;
        this.ctx = ctx;
        memoryManager = ctx.getMemoryManager();
        final Connection c = ctx.getConnection();
        asyncWriter = ((AsyncQueueWriter) c.getTransport().getWriter(c));
        if (asyncWriter.getMaxPendingBytesPerConnection() <= 0) {
            asyncWriter = null;
        }

    }

    /**
     * <p>
     * Returns <code>true</code> if content will be written in a non-blocking
     * fashion, otherwise returns <code>false</code>.
     * </p>
     *
     * @return <code>true</code> if content will be written in a non-blocking
     * fashion, otherwise returns <code>false</code>.
     *
     * @since 2.1.2
     */
    public boolean isAsyncEnabled() {
        return asyncEnabled;
    }


    /**
     * Sets the asynchronous processing state of this <code>OutputBuffer</code>.
     *
     * @param asyncEnabled <code>true</code> if this <code>OutputBuffer<code>
     *  will write content without blocking.
     *
     *  @since 2.1.2
     */
    public void setAsyncEnabled(boolean asyncEnabled) {
        this.asyncEnabled = asyncEnabled;
    }


    public void prepareCharacterEncoder() {
        getEncoder();
    }

    public int getBufferSize() {
        return bufferSize;
    }

    public void registerLifeCycleListener(final LifeCycleListener listener) {
        lifeCycleListeners.add(listener);
    }
    
    public boolean removeLifeCycleListener(final LifeCycleListener listener) {
        return lifeCycleListeners.remove(listener);
    }
    
    public void setBufferSize(final int bufferSize) {
        if (!committed && currentBuffer == null) {
            this.bufferSize = bufferSize;  
        }
    }
    
    /**
     * Reset current response.
     *
     * @throws IllegalStateException if the response has already been committed
     */
    public void reset() {

        if (committed)
            throw new IllegalStateException(/*FIXME:Put an error message*/);

        compositeBuffer = null;
        
        if (currentBuffer != null) {
            currentBuffer.clear();
        }

    }


    /**
     * @return <code>true</code> if this <tt>OutputBuffer</tt> is closed, otherwise
     *  returns <code>false</code>.
     */
    public boolean isClosed() {
        return closed;
    }
    
    /**
     * Get the number of bytes buffered on OutputBuffer and ready to be sent.
     * 
     * @return the number of bytes buffered on OutputBuffer and ready to be sent.
     */
    public int getBufferedDataSize() {
        int size = 0;
        if (compositeBuffer != null) {
            size += compositeBuffer.remaining();
        }
        
        if (currentBuffer != null) {
            size += currentBuffer.position();
        }
        
        return size;
    }
    
    
    /**
     * Recycle the output buffer. This should be called when closing the
     * connection.
     */
    public void recycle() {

        response = null;

        if (compositeBuffer != null) {
            compositeBuffer.dispose();
            compositeBuffer = null;
        }
        
        if (currentBuffer != null) {
            currentBuffer.dispose();
            currentBuffer = null;
        }

        charBuf.position(0);
        
        encoder = null;
        ctx = null;
        memoryManager = null;
        handler = null;
        asyncError.set(null);
        monitor = null;
        asyncWriter = null;

        committed = false;
        finished = false;
        closed = false;

        lifeCycleListeners.clear();
    }


    public void endRequest()
        throws IOException {

        handleAsyncErrors();

        if (finished) {
            return;
        }

        if (monitor != null) {
            final Connection c = ctx.getConnection();
            final TaskQueue tqueue = ((NIOConnection) c).getAsyncWriteQueue();
            tqueue.removeQueueMonitor(monitor);
            monitor = null;
        }

        if (!closed) {
            close();
        }
        
        if(ctx != null) {
            ctx.notifyDownstream(HttpServerFilter.RESPONSE_COMPLETE_EVENT);
        }

        finished = true;

    }


    /**
     * Acknowledge a HTTP <code>Expect</code> header.  The response status
     * code and reason phrase should be set before invoking this method.
     *
     * @throws IOException if an error occurs writing the acknowledgment.
     */
    public void acknowledge() throws IOException {

        ctx.write(response, !asyncEnabled);
        
    }


    // ---------------------------------------------------- Writer-Based Methods


    public void write(char cbuf[], int off, int len) throws IOException {

        handleAsyncErrors();

        if (closed || len == 0) {
            return;
        }

        flushCharsToBuf(CharBuffer.wrap(cbuf, off, len));

    }


    public void writeChar(int c) throws IOException {

        handleAsyncErrors();

        if (closed) {
            return;
        }

        charBuf.position(0);
        charBuf.put(0, (char) c);
        flushCharsToBuf(charBuf);
    }


    public void write(final char cbuf[]) throws IOException {
        write(cbuf, 0, cbuf.length);
    }


    public void write(final String str) throws IOException {
        write(str, 0, str.length());
    }


    public void write(final String str, final int off, final int len) throws IOException {

        handleAsyncErrors();

        if (closed || len == 0) {
            return;
        }

        flushCharsToBuf(CharBuffer.wrap(str, off, len + off));
    }


    // ---------------------------------------------- OutputStream-Based Methods

    public void writeByte(final int b) throws IOException {

        handleAsyncErrors();
        if (closed) {
            return;
        }

        checkCurrentBuffer();
        
        if (currentBuffer.hasRemaining()) {
            currentBuffer.put((byte) b);
        } else {
            flush();
            checkCurrentBuffer();
            currentBuffer.put((byte) b);
        }

    }


    public void write(final byte b[]) throws IOException {
        write(b, 0, b.length);
    }

    
    public void write(final byte b[], final int off, final int len) throws IOException {

        handleAsyncErrors();
        if (closed || len == 0) {
            return;
        }
        
        // Copy the content of the b[] to the currentBuffer, if it's possible
        if (bufferSize >= len &&
                (currentBuffer == null || currentBuffer.remaining() >= len)) {
            checkCurrentBuffer();

            currentBuffer.put(b, off, len);
        } else {  // If b[] is too big - try to send it to wire right away.
            
            // wrap byte[] with a thread local buffer
            temporaryWriteBuffer.reset(b, off, len);
            
            // if there is data in the currentBuffer - complete it
            finishCurrentBuffer();
            
            // mark headers as commited
            doCommit();
            if (compositeBuffer != null) { // if we write a composite buffer
                compositeBuffer.append(temporaryWriteBuffer);
                writeContentBuffer0(compositeBuffer, false, cloner);
                compositeBuffer = null;
            } else { // we write just mutableHeapBuffer content
                writeContentBuffer0(temporaryWriteBuffer, false, cloner);
            }            
        }
    }


    // --------------------------------------------------- Common Output Methods


    public void close() throws IOException {

        handleAsyncErrors();
        if (closed) {
            return;
        }
        closed = true;

        // commit the response (mark it as committed)
        final boolean isJustCommitted = doCommit();
        // Try to commit the content chunk together with headers (if there were not committed before)
        if (!writeContentChunk(!isJustCommitted, true) && (isJustCommitted || response.isChunked())) {
            // If there is no ready content chunk to commit,
            // but headers were not committed yet, or this is chunked encoding
            // and we need to send trailer
            forceCommitHeaders(true);
        }
    }




    /**
     * Flush the response.
     *
     * @throws java.io.IOException an underlying I/O error occurred
     */
    public void flush() throws IOException {
        handleAsyncErrors();

        final boolean isJustCommitted = doCommit();
        if (!writeContentChunk(!isJustCommitted, false) && isJustCommitted) {
            forceCommitHeaders(false);
        }

    }


    /**
     * <p>
     * Writes the contents of the specified {@link ByteBuffer} to the client.
     * </p>
     *
     * Note, that passed {@link ByteBuffer} will be directly used by underlying
     * connection, so it could be reused only if it has been flushed.
     *
     * @param byteBuffer the {@link ByteBuffer} to write
     * @throws IOException if an error occurs during the write
     */
    @SuppressWarnings({"unchecked"})
    public void writeByteBuffer(final ByteBuffer byteBuffer) throws IOException {
        final Buffer w = Buffers.wrap(memoryManager, byteBuffer);
        w.allowBufferDispose(false);
        writeBuffer(w);
    }


    /**
     * <p>
     * Writes the contents of the specified {@link Buffer} to the client.
     * </p>
     *
     * Note, that passed {@link Buffer} will be directly used by underlying
     * connection, so it could be reused only if it has been flushed.
     * 
     * @param buffer the {@link ByteBuffer} to write
     * @throws IOException if an error occurs during the write
     */
    public void writeBuffer(final Buffer buffer) throws IOException {
        handleAsyncErrors();
        finishCurrentBuffer();
        checkCompositeBuffer();
        compositeBuffer.append(buffer);
    }


    // -------------------------------------------------- General Public Methods


    public boolean canWriteChar(final int length) {
        if (length <= 0 || asyncWriter == null) {
            return true;
        }
        final CharsetEncoder e = getEncoder();
        final int len = Float.valueOf(length * e.averageBytesPerChar()).intValue();
        return canWrite(len);
    }

    /**
     * @see AsyncQueueWriter#canWrite(org.glassfish.grizzly.Connection, int)
     */
    public boolean canWrite(final int length) {

        if (length <= 0 || asyncWriter == null) {
            return true;
        }
        final Connection c = ctx.getConnection();
        return asyncWriter.canWrite(c, length);

    }


    public void notifyCanWrite(final WriteHandler handler, final int length) {
        if (this.handler != null) {
            throw new IllegalStateException("Illegal attempt to set a new handler before the existing handler has been notified.");
        }

        if (asyncWriter == null || canWrite(length)) {
            try {
                handler.onWritePossible();
            } catch (Exception ioe) {
                handler.onError(ioe);
            }
            return;
        }
        
        this.handler = handler;
        final Connection c = ctx.getConnection();
        final TaskQueue taskQueue = ((NIOConnection) c).getAsyncWriteQueue();

        final int maxBytes = asyncWriter.getMaxPendingBytesPerConnection();
        if (length > maxBytes) {
            throw new IllegalArgumentException("Illegal request to write "
                                                  + length
                                                  + " bytes.  Max allowable write is "
                                                  + maxBytes + '.');
        }
        monitor = new TaskQueue.QueueMonitor() {
            
            @Override
            public boolean shouldNotify() {
                return ((maxBytes - taskQueue.spaceInBytes()) >= length);
            }

            @Override
            public void onNotify() throws IOException {
                OutputBuffer.this.handler = null;
                OutputBuffer.this.monitor = null;
                try {
                    handler.onWritePossible();
                } catch (Throwable t) {
                    handler.onError(t);
                    throw Exceptions.makeIOException(t);
                }
            }
        };
        try {
            // If exception occurs here - it's from WriteHandler, so it must
            // have been processed by WriteHandler.onError().
            taskQueue.addQueueMonitor(monitor);
        } catch (Exception ignored) {
        }
    }


    // --------------------------------------------------------- Private Methods


    private void handleAsyncErrors() throws IOException {
        final Throwable t = asyncError.get();
        if (t != null) {
            if (t instanceof IOException) {
                throw (IOException) t;
            } else if (t instanceof RuntimeException) {
                throw (RuntimeException) t;
            } else {
                throw new IOException(t);
            }
        }
    }

    
    private boolean writeContentChunk(final boolean areHeadersCommitted,
                                      final boolean isLast) throws IOException {
        final Buffer bufferToFlush;
        final boolean isFlushComposite = compositeBuffer != null && compositeBuffer.hasRemaining();

        if (isFlushComposite) {
            finishCurrentBuffer();
            bufferToFlush = compositeBuffer;
            compositeBuffer = null;
        } else if (currentBuffer != null && currentBuffer.position() > 0) {
            currentBuffer.trim();
            bufferToFlush = currentBuffer;
            currentBuffer = null;
        } else {
            bufferToFlush = null;
        }

        if (bufferToFlush != null) {
            if (isLast && !areHeadersCommitted &&
                    response.getContentLength() == -1 && !response.isChunked()) {
                response.setContentLength(bufferToFlush.remaining());
            }
            
            writeContentBuffer0(bufferToFlush, isLast, null);

            return true;
        }
        
        return false;
    }

    private void writeContentBuffer0(final Buffer bufferToFlush,
            final boolean isLast, final MessageCloner<Buffer> messageCloner)
            throws IOException {
        
        final HttpContent.Builder builder = response.httpContentBuilder();

        builder.content(bufferToFlush).last(isLast);
        ctx.write(null,
                  builder.build(),
                  asyncCompletionHandler,
                  messageCloner,
                  !asyncEnabled);
    }

    private void checkCurrentBuffer() {
        if (currentBuffer == null) {
            currentBuffer = memoryManager.allocate(bufferSize);
            currentBuffer.allowBufferDispose(true);
        }
    }

    private void finishCurrentBuffer() {
        if (currentBuffer != null && currentBuffer.position() > 0) {
            currentBuffer.trim();
            checkCompositeBuffer();
            compositeBuffer.append(currentBuffer);
            currentBuffer = null;
        }
    }

    private CharsetEncoder getEncoder() {

        if (encoder == null) {
            String encoding = response.getCharacterEncoding();
            if (encoding == null) {
                encoding = Constants.DEFAULT_CHARACTER_ENCODING;
            }
            
            encoder = encoders.get(encoding);
            if (encoder == null) {
                final Charset cs = Charsets.lookupCharset(encoding);
                encoder = cs.newEncoder();
                encoder.onMalformedInput(CodingErrorAction.REPLACE);
                encoder.onUnmappableCharacter(CodingErrorAction.REPLACE);
                
                encoders.put(encoding, encoder);
            } else {
                encoder.reset();
            }
        }

        return encoder;

    }

    
    private boolean doCommit() throws IOException {

        if (!committed) {
            notifyCommit();
            committed = true;
            return true;
        }

        return false;
    }

    private void forceCommitHeaders(final boolean isLast) throws IOException {
        if (isLast) {
            if (response != null) {
                final HttpContent.Builder builder = response.httpContentBuilder();
                builder.last(true);
                ctx.write(builder.build(), !asyncEnabled);
            }
        } else {
            ctx.write(response, !asyncEnabled);
        }
    }

    private void checkCompositeBuffer() {
        if (compositeBuffer == null) {
            final CompositeBuffer buffer = CompositeBuffer.newBuffer(memoryManager);
            buffer.allowBufferDispose(true);
            buffer.allowInternalBuffersDispose(true);
            compositeBuffer = buffer;
        }
    }

    private void flushCharsToBuf(final CharBuffer charBuf) throws IOException {

        handleAsyncErrors();
        // flush the buffer - need to take care of encoding at this point
        final CharsetEncoder enc = getEncoder();
        checkCurrentBuffer();
        ByteBuffer currentByteBuffer = currentBuffer.toByteBuffer();
        int bufferPos = currentBuffer.position();
        int byteBufferPos = currentByteBuffer.position();

        CoderResult res = enc.encode(charBuf,
                                     currentByteBuffer,
                                     true);

        currentBuffer.position(bufferPos + (currentByteBuffer.position() - byteBufferPos));
        
        while (res == CoderResult.OVERFLOW) {
            finishCurrentBuffer();
            checkCurrentBuffer();
            currentByteBuffer = currentBuffer.toByteBuffer();
            bufferPos = currentBuffer.position();
            byteBufferPos = currentByteBuffer.position();
            
            res = enc.encode(charBuf, currentByteBuffer, true);

            currentBuffer.position(bufferPos + (currentByteBuffer.position() - byteBufferPos));
        }

        if (res != CoderResult.UNDERFLOW) {
            throw new IOException("Encoding error");
        }
        
        if (compositeBuffer != null) {
            writeContentChunk(!doCommit(), false);
        }        
    }

    private void notifyCommit() throws IOException {
        for (int i = 0; i < lifeCycleListeners.size(); i++) {
            lifeCycleListeners.get(i).onCommit();
        }
    }
    
    /**
     * The {@link MessageCloner}, responsible for cloning Buffer content, if it
     * wasn't possible to write it in the current Thread (it was added to async
     * write queue).
     * We do this, because {@link #write(byte[], int, int)} method is not aware
     * of async write queues, and content of the passed byte[] might be changed
     * by user application once in gets control back.
     */
    private final class ByteArrayCloner implements MessageCloner<Buffer> {
        @Override
        public Buffer clone(final Connection connection,
                final Buffer originalMessage) {
            
            // Buffer was disposed somewhere on the way to async write queue -
            // just return the original message
            if (temporaryWriteBuffer.isDisposed()) {
                return originalMessage;
            }
            
            if (originalMessage.isComposite()) {
                final CompositeBuffer compositeBuffer = (CompositeBuffer) originalMessage;
                compositeBuffer.shrink();

                if (!temporaryWriteBuffer.isDisposed()) {
                    if (compositeBuffer.remaining() == temporaryWriteBuffer.remaining()) {
                        compositeBuffer.allowInternalBuffersDispose(false);
                        compositeBuffer.tryDispose();
                        return temporaryWriteBuffer.cloneContent();
                    } else {
                        compositeBuffer.replace(temporaryWriteBuffer,
                                temporaryWriteBuffer.cloneContent());
                    }
                }
                
                return originalMessage;
            }
                
            return temporaryWriteBuffer.cloneContent();
        }
    }
    
    public static interface LifeCycleListener {
        public void onCommit() throws IOException;
    }
}
