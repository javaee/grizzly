/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2014 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.streams;

import org.glassfish.grizzly.Transformer;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.CompletionHandler;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.GrizzlyFuture;
import org.glassfish.grizzly.TransformationException;
import org.glassfish.grizzly.TransformationResult;
import org.glassfish.grizzly.TransformationResult.Status;
import org.glassfish.grizzly.impl.ReadyFutureImpl;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import org.glassfish.grizzly.memory.Buffers;

/**
 * Write the primitive Java type to the current ByteBuffer.  If it doesn't
 * fit, call the BufferHandler, and write to the result, which becomes the
 * new current ByteBuffer.  Arrays will be written across multiple ByteBuffers
 * if necessary, but all primitives will be written to a single ByteBuffer.
 * 
 * @author Ken Cavanaugh
 */
public abstract class AbstractStreamWriter implements StreamWriter {
    protected static final Logger logger = Grizzly.logger(AbstractStreamWriter.class);
    
    protected static final Integer ZERO = 0;
    protected static final GrizzlyFuture<Integer> ZERO_READY_FUTURE =
            ReadyFutureImpl.create(ZERO);
    
    private final Connection connection;

    private long timeoutMillis = 30000;
    
    private final AtomicBoolean isClosed = new AtomicBoolean();

    protected final boolean isOutputBuffered;
    protected final Output output;

    /** Create a new ByteBufferWriter.  An instance maintains a current buffer
     * for use in writing.  Whenever the current buffer is insufficient to hold
     * the required data, the BufferHandler is called, and the result of the
     * handler is the new current buffer. The handler is responsible for
     * the disposition of the contents of the old buffer.
     */
    protected AbstractStreamWriter(final Connection connection,
            Output streamOutput) {
        this.connection = connection;
        this.output = streamOutput;
        this.isOutputBuffered = streamOutput.isBuffered();
    }

    /**
     * Cause the overflow handler to be called even if buffer is not full.
     */
    @Override
    public GrizzlyFuture<Integer> flush() throws IOException {
        return flush(null);
    }

    /**
     * Cause the overflow handler to be called even if buffer is not full.
     */
    @Override
    public GrizzlyFuture<Integer> flush(CompletionHandler<Integer> completionHandler)
            throws IOException {
        return output.flush(completionHandler);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isClosed() {
        return isClosed.get();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() throws IOException {
        close(null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public GrizzlyFuture<Integer> close(
            CompletionHandler<Integer> completionHandler) throws IOException {
        if (!isClosed.getAndSet(true)) {
            return output.close(completionHandler);
        }

        return ReadyFutureImpl.create(0);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeBuffer(Buffer b) throws IOException {
        output.write(b);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeBoolean(final boolean data) throws IOException {
        final byte value = data ? (byte) 1 : (byte) 0;
        writeByte(value);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeByte(final byte data) throws IOException {
        output.write(data);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeChar(final char data) throws IOException {
        if (isOutputBuffered) {
            output.ensureBufferCapacity(2);
            output.getBuffer().putChar(data);
        } else {
            output.write((byte) ((data >>> 8) & 0xFF));
            output.write((byte) ((data) & 0xFF));
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeShort(final short data) throws IOException {
        if (isOutputBuffered) {
            output.ensureBufferCapacity(2);
            output.getBuffer().putShort(data);
        } else {
            output.write((byte) ((data >>> 8) & 0xFF));
            output.write((byte) ((data) & 0xFF));
        }
    }

    @Override
    public void writeInt(final int data) throws IOException {
        if (isOutputBuffered) {
            output.ensureBufferCapacity(4);
            output.getBuffer().putInt(data);
        } else {
            output.write((byte) ((data >>> 24) & 0xFF));
            output.write((byte) ((data >>> 16) & 0xFF));
            output.write((byte) ((data >>> 8) & 0xFF));
            output.write((byte) ((data) & 0xFF));
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeLong(final long data) throws IOException {
        if (isOutputBuffered) {
            output.ensureBufferCapacity(8);
            output.getBuffer().putLong(data);
        } else {
            output.write((byte) ((data >>> 56) & 0xFF));
            output.write((byte) ((data >>> 48) & 0xFF));
            output.write((byte) ((data >>> 40) & 0xFF));
            output.write((byte) ((data >>> 32) & 0xFF));
            output.write((byte) ((data >>> 24) & 0xFF));
            output.write((byte) ((data >>> 16) & 0xFF));
            output.write((byte) ((data >>> 8) & 0xFF));
            output.write((byte) ((data) & 0xFF));
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeFloat(final float data) throws IOException {
        writeInt(Float.floatToIntBits(data));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeDouble(final double data) throws IOException {
        writeLong(Double.doubleToLongBits(data));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeBooleanArray(final boolean[] data) throws IOException {
        for(int i=0; i<data.length; i++) {
            output.write((byte) (data[i] ? 1 : 0));
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeByteArray(final byte[] data) throws IOException {
        writeByteArray(data, 0, data.length);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeByteArray(byte[] data, int offset, int length) throws IOException {
        final Buffer buffer = Buffers.wrap(connection.getMemoryManager(),
                data, offset, length);
        output.write(buffer);
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public void writeCharArray(final char[] data) throws IOException {
        for(int i=0; i<data.length; i++) {
            writeChar(data[i]);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeShortArray(final short[] data) throws IOException {
        for(int i=0; i<data.length; i++) {
            writeShort(data[i]);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeIntArray(final int[] data) throws IOException {
        for(int i=0; i<data.length; i++) {
            writeInt(data[i]);
        }

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeLongArray(final long[] data) throws IOException {
        for(int i=0; i<data.length; i++) {
            writeLong(data[i]);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeFloatArray(final float[] data) throws IOException {
        for(int i=0; i<data.length; i++) {
            writeFloat(data[i]);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeDoubleArray(final double[] data) throws IOException {
        for(int i=0; i<data.length; i++) {
            writeDouble(data[i]);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <E> GrizzlyFuture<Stream> encode(Transformer<E, Buffer> encoder,
            E object) throws IOException {
        return encode(encoder, object, null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <E> GrizzlyFuture<Stream> encode(Transformer<E, Buffer> encoder,
            E object, CompletionHandler<Stream> completionHandler)
            throws IOException {
        Exception exception = null;

        final TransformationResult<E, Buffer> result =
                encoder.transform(connection, object);

        final Status status = result.getStatus();
        if (status == Status.COMPLETE) {
            output.write(result.getMessage());
            if (completionHandler != null) {
                completionHandler.completed(this);
            }
            return ReadyFutureImpl.<Stream>create(this);
        } else if (status == Status.INCOMPLETE) {
            exception = new IllegalStateException("Encoder returned INCOMPLETE state");
        }

        if (exception == null) {
            exception = new TransformationException(result.getErrorCode() + ": " +
                result.getErrorDescription());
        }

        return ReadyFutureImpl.create(exception);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Connection getConnection() {
        return connection;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getTimeout(TimeUnit timeunit) {
        return timeunit.convert(timeoutMillis, TimeUnit.MILLISECONDS);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setTimeout(long timeout, TimeUnit timeunit) {
        timeoutMillis = TimeUnit.MILLISECONDS.convert(timeout, timeunit);
    }

    public static class DisposeBufferCompletionHandler
            implements CompletionHandler {

        private final Buffer buffer;

        public DisposeBufferCompletionHandler(Buffer buffer) {
            this.buffer = buffer;
        }
        
        @Override
        public void cancelled() {
            disposeBuffer();
        }

        @Override
        public void failed(Throwable throwable) {
            disposeBuffer();
        }

        @Override
        public void completed(Object result) {
            disposeBuffer();
        }

        @Override
        public void updated(Object result) {
        }

        protected void disposeBuffer() {
            buffer.dispose();
        }
    }
}

