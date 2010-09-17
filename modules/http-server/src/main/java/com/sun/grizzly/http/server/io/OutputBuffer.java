/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.grizzly.http.server.io;

import com.sun.grizzly.Buffer;
import com.sun.grizzly.CompletionHandler;
import com.sun.grizzly.Connection;
import com.sun.grizzly.EmptyCompletionHandler;
import com.sun.grizzly.asyncqueue.AsyncQueueWriter;
import com.sun.grizzly.asyncqueue.TaskQueue;
import com.sun.grizzly.filterchain.FilterChainContext;
import com.sun.grizzly.http.HttpContent;
import com.sun.grizzly.http.HttpResponsePacket;
import com.sun.grizzly.http.HttpServerFilter;
import com.sun.grizzly.http.server.Constants;
import com.sun.grizzly.http.util.Utils;
import com.sun.grizzly.memory.BuffersBuffer;
import com.sun.grizzly.memory.CompositeBuffer;
import com.sun.grizzly.memory.MemoryManager;
import com.sun.grizzly.memory.MemoryUtils;
import com.sun.grizzly.nio.AbstractNIOConnection;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.util.concurrent.atomic.AtomicReference;

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

    private boolean committed;

    private boolean finished;

    private boolean closed;

    private boolean processingChars;

    private CharsetEncoder encoder;

    private final CharBuffer charBuf = CharBuffer.allocate(1);

    private MemoryManager memoryManager;

    private WriteHandler handler;

    private final AtomicReference<Throwable> asyncError = new AtomicReference<Throwable>();

    private TaskQueue.QueueMonitor monitor;

    private AsyncQueueWriter asyncWriter;

    private int bufferSize = DEFAULT_BUFFER_SIZE;

    private final CompletionHandler asyncCompletionHandler =
            new EmptyCompletionHandler() {

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
        memoryManager = ctx.getConnection().getTransport().getMemoryManager();
        compositeBuffer = createCompositeBuffer();
        final Connection c = ctx.getConnection();
        asyncWriter = ((AsyncQueueWriter) c.getTransport().getWriter(c));

    }


    public void processingChars() {
        processingChars = true;
    }

    public int getBufferSize() {
        return bufferSize;
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

        if (compositeBuffer != null) {
            compositeBuffer.removeAll();
        }
        
        if (currentBuffer != null) {
            currentBuffer.clear();
        }

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
        
        encoder = null;
        ctx = null;
        memoryManager = null;
        handler = null;
        asyncError.set(null);

        committed = false;
        finished = false;
        closed = false;
        processingChars = false;

    }


    public void endRequest()
        throws IOException {

        handleAsyncErrors();

        if (finished) {
            return;
        }

        if (monitor != null) {
            final Connection c = ctx.getConnection();
            final TaskQueue tqueue = ((AbstractNIOConnection) c).getAsyncWriteQueue();
            tqueue.removeQueueMonitor(monitor);
            monitor = null;
        }

        close();

        ctx.notifyDownstream(HttpServerFilter.RESPONSE_COMPLETE_EVENT);

        finished = true;

    }


    /**
     * Commit the response.
     */
    public void commit() {

        if (committed) {
            return;
        }
        // The response is now committed
        committed = true;

    }


    /**
     * Acknowledge a HTTP <code>Expect</code> header.  The response status
     * code and reason phrase should be set before invoking this method.
     *
     * @throws IOException if an error occurs writing the acknowledgment.
     */
    public void acknowledge() throws IOException {

        ctx.write(response);
        
    }


    // ---------------------------------------------------- Writer-Based Methods


    public void write(char cbuf[], int off, int len) throws IOException {

        if (!processingChars) {
            throw new IllegalStateException();
        }

        handleAsyncErrors();

        if (closed || len == 0) {
            return;
        }

        flushCharsToBuf(CharBuffer.wrap(cbuf, off, len));

    }


    public void writeChar(int c) throws IOException {

        if (!processingChars) {
            throw new IllegalStateException();
        }

        handleAsyncErrors();

        if (closed) {
            return;
        }

        charBuf.position(0);
        charBuf.put(0, (char) c);
        flushCharsToBuf(charBuf);
    }


    public void write(char cbuf[]) throws IOException {
        write(cbuf, 0, cbuf.length);
    }


    public void write(String str) throws IOException {
        write(str, 0, str.length());
    }


    public void write(String str, int off, int len) throws IOException {

        if (!processingChars) {
            throw new IllegalStateException();
        }

        handleAsyncErrors();

        if (closed || len == 0) {
            return;
        }

        flushCharsToBuf(CharBuffer.wrap(str, off, len + off));
    }


    // ---------------------------------------------- OutputStream-Based Methods

    public void writeByte(int b) throws IOException {

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


    public void write(byte b[]) throws IOException {
        write(b, 0, b.length);
    }


    public void write(byte b[], int off, int len) throws IOException {

        handleAsyncErrors();
        if (closed || len == 0) {
            return;
        }
        
        int total = len;
        do {
            checkCurrentBuffer();

            final int writeLen = Math.min(currentBuffer.remaining(), total);
            currentBuffer.put(b, off, writeLen);
            off += writeLen;
            total -= writeLen;

            if (currentBuffer.hasRemaining()) { // complete
                return;
            }

            flush();
        } while (total > 0);
    }


    // --------------------------------------------------- Common Output Methods


    public void close() throws IOException {

        handleAsyncErrors();
        if (closed) {
            return;
        }
        closed = true;
        doCommit();

        writeContentChunk(true);

    }




    /**
     * Flush the response.
     *
     * @throws java.io.IOException an underlying I/O error occurred
     */
    public void flush() throws IOException {

        doCommit();
        writeContentChunk(false);

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
    public void writeByteBuffer(ByteBuffer byteBuffer) throws IOException {
        Buffer w = MemoryUtils.wrap(memoryManager, byteBuffer);
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
    public void writeBuffer(Buffer buffer) throws IOException {
        finishCurrentBuffer();
        compositeBuffer.append(buffer);
    }


    // -------------------------------------------------- General Public Methods


    public boolean canWriteChar(final int length) {
        if (length <= 0) {
            return true;
        }
        CharsetEncoder e = getEncoder();
        final int len = Float.valueOf(length * e.averageBytesPerChar()).intValue();
        return canWrite(len);
    }

    /**
     * @see AsyncQueueWriter#canWrite(com.sun.grizzly.Connection, int) 
     */
    public boolean canWrite(final int length) {
        if (length <= 0) {
            return true;
        }
        final Connection c = ctx.getConnection();
        return asyncWriter.canWrite(c, length);

    }


    public boolean notifyCanWrite(final WriteHandler handler, final int length) {

        if (this.handler != null) {
            throw new IllegalStateException("Illegal attempt to set a new handler before the existing handler has been notified.");
        }
        if (canWrite(length)) {
            return false;
        }
        this.handler = handler;
        final Connection c = ctx.getConnection();
        final TaskQueue taskQueue = ((AbstractNIOConnection) c).getAsyncWriteQueue();

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
            public void onNotify() {
                handler.onWritePossible();
                OutputBuffer.this.handler = null;

            }
        };
        if (!taskQueue.addQueueMonitor(monitor)) {
            // monitor wasn't added because it was notified
            this.handler = null;
            monitor = null;
        }

        return true;
        
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


    private void writeContentChunk(boolean includeTrailer) throws IOException {
        handleAsyncErrors();

        final Buffer bufferToFlush;
        final boolean isFlushComposite = compositeBuffer.hasRemaining();

        if (isFlushComposite) {
            finishCurrentBuffer();
            bufferToFlush = compositeBuffer;
        } else if (currentBuffer != null && currentBuffer.position() > 0) {
            currentBuffer.trim();
            bufferToFlush = currentBuffer;
            currentBuffer = null;
        } else {
            bufferToFlush = null;
        }

        if (bufferToFlush != null) {
            HttpContent.Builder builder = response.httpContentBuilder();
            builder.content(bufferToFlush);
            ctx.write(builder.build(), asyncCompletionHandler);
            if (isFlushComposite) { // recreate composite if needed
                if (!includeTrailer) {
                    compositeBuffer = createCompositeBuffer();
                } else {
                    compositeBuffer = null;
                }
            }
        }
        
        if (response.isChunked() && includeTrailer) {
            ctx.write(response.httpTrailerBuilder().build());
        }
    }

    private void checkCurrentBuffer() {
        if (currentBuffer == null) {
            currentBuffer = memoryManager.allocate(DEFAULT_BUFFER_SIZE);
        }
    }

    private void finishCurrentBuffer() {
        if (currentBuffer != null && currentBuffer.position() > 0) {
            currentBuffer.trim();
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
            final Charset cs = Utils.lookupCharset(encoding);
            encoder = cs.newEncoder();
            encoder.onMalformedInput(CodingErrorAction.REPLACE);
            encoder.onUnmappableCharacter(CodingErrorAction.REPLACE);
        }

        return encoder;

    }

    
    private void doCommit() throws IOException {

        handleAsyncErrors();
        if (!committed) {
            committed = true;
            // flush the message header to the client
            ctx.write(response);
        }

    }

    private CompositeBuffer createCompositeBuffer() {
        final CompositeBuffer buffer = BuffersBuffer.create(memoryManager);
        buffer.allowBufferDispose(true);
        buffer.allowInternalBuffersDispose(true);

        return buffer;
    }

    private void flushCharsToBuf(CharBuffer charBuf) throws IOException {

        handleAsyncErrors();
        // flush the buffer - need to take care of encoding at this point
        CharsetEncoder enc = getEncoder();
        checkCurrentBuffer();
        CoderResult res = enc.encode(charBuf,
                                     (ByteBuffer) currentBuffer.underlying(),
                                     true);
        while (res == CoderResult.OVERFLOW) {
            commit();
            writeContentChunk(false);
            checkCurrentBuffer();
            res = enc.encode(charBuf, (ByteBuffer) currentBuffer.underlying(), true);
        }

        if (res != CoderResult.UNDERFLOW) {
            throw new IOException("Encoding error");
        }

    }
}
