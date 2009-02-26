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
package org.glassfish.grizzly.streams;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.CharBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.nio.LongBuffer;
import java.nio.FloatBuffer;
import java.nio.DoubleBuffer;
import java.util.concurrent.Future;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.CompletionHandler;

/**
 * Write the primitive Java type to the current ByteBuffer.  If it doesn't
 * fit, call the BufferHandler, and write to the result, which becomes the
 * new current ByteBuffer.  Arrays will be written across multiple ByteBuffers
 * if necessary, but all primitives will be written to a single ByteBuffer.
 * 
 * @author Ken Cavanaugh
 */
public abstract class AbstractStreamWriter implements StreamWriter {
    protected BufferHandler handler;
    private volatile Buffer buffer;
    private boolean isClosed = false;
    private ByteOrder byteOrder;

    protected AbstractStreamWriter() {
        this(null);
    }

    /** Create a new ByteBufferWriter.  An instance maintains a current buffer
     * for use in writing.  Whenever the current buffer is insufficient to hold
     * the required data, the BufferHandler is called, and the result of the
     * handler is the new current buffer. The handler is responsible for 
     * the disposition of the contents of the old buffer.
     */
    public AbstractStreamWriter(final BufferHandler handler) {
        this.handler = handler;
        byteOrder = ByteOrder.BIG_ENDIAN;
    }

    public BufferHandler bufferHandler() {
        return handler;
    }

    public ByteOrder order() {
        return byteOrder;
    }

    public void order(final ByteOrder byteOrder) {
        this.byteOrder = byteOrder;
    }

    private Future overflow() throws IOException {
        return overflow(buffer, null);
    }

    private Future overflow(Buffer buffer) throws IOException {
        return overflow(buffer, null);
    }

    private Future overflow(CompletionHandler completionHandler) throws IOException {
        return overflow(buffer, completionHandler);
    }

    private Future overflow(Buffer buffer, CompletionHandler completionHandler)
            throws IOException {
        // Why was this here: buffer.underlying().limit( buffer.underlying().position() ) ;
        Future future = null;

        if (buffer != null) {
            future = handler.flush(buffer, completionHandler);
            if (!future.isDone() && buffer == this.buffer) {
                buffer = handler.newBuffer();
            }
        } else {
            buffer = handler.newBuffer();
        }

        initBuffer(buffer);
        return future;
    }

    private void initBuffer(final Buffer buffer) {
        this.buffer = buffer;
        buffer.clear();
        buffer.order(byteOrder);
    }

    /**
     * Cause the overflow handler to be called even if buffer is not full.
     */
    public synchronized Future flush() throws IOException {
        return flush(null);
    }

    /**
     * Cause the overflow handler to be called even if buffer is not full.
     */
    public synchronized Future flush(CompletionHandler completionHandler)
            throws IOException {
        return overflow(completionHandler);
    }

    public synchronized void close() throws IOException {
        close(null);
    }

    public Future close(CompletionHandler completionHandler) throws IOException {
        try {
            return handler.close(buffer, completionHandler);
        } finally {
            buffer = null;
            isClosed = true;
        }
    }

    /** Ensure that the requested amount of space is available
     */
    public synchronized void ensure(final int size) throws IOException {
        if (isClosed) {
            throw new IllegalStateException(
                    "ByteBufferWriter is closed");
        }

        if ((buffer == null) || (buffer.remaining() < size)) {
            overflow(buffer);
        }

        if (buffer.remaining() < size) {
            throw new RuntimeException("Newly allocated buffer is too small");
        }
    }

    public void writeStream(StreamReader streamReader) throws IOException {
        boolean isFirstTime = true;
        AbstractStreamReader readerImpl = (AbstractStreamReader) streamReader;
        Buffer readerBuffer;
        while ((readerBuffer = readerImpl.getBuffer()) != null) {
            if (isFirstTime) {
                overflow();
                isFirstTime = false;
            }

            readerBuffer.position(readerBuffer.limit());
            overflow(readerBuffer);
            readerImpl.finishBuffer();
        }
    }

    public void writeBoolean(final boolean data) throws IOException {
        ensure(1);
        final byte value = data ? (byte) 1 : (byte) 0;
        buffer.put(value);
    }

    public void writeByte(final byte data) throws IOException {
        ensure(1);
        buffer.put(data);
    }

    public void writeChar(final char data) throws IOException {
        ensure(2);
        buffer.putChar(data);
    }

    public void writeShort(final short data) throws IOException {
        ensure(2);
        buffer.putShort(data);
    }

    public void writeInt(final int data) throws IOException {
        ensure(4);
        buffer.putInt(data);
    }

    public void writeLong(final long data) throws IOException {
        ensure(8);
        buffer.putLong(data);
    }

    public void writeFloat(final float data) throws IOException {
        ensure(4);
        buffer.putFloat(data);
    }

    public void writeDouble(final double data) throws IOException {
        ensure(8);
        buffer.putDouble(data);
    }

    public void writeBooleanArray(final boolean[] data) throws IOException {
        ensure(1);
        int ctr = 0;
        while (ctr < data.length) {
            final int dataSizeToWrite = Math.min(data.length - ctr,
                    buffer.remaining());

            for (int ctr2 = ctr; ctr2 < ctr + dataSizeToWrite; ctr2++) {
                buffer.put((byte) (data[ctr2] ? 1 : 0));
            }
            ctr += dataSizeToWrite;

            if (ctr == data.length) {
                break;
            }

            overflow();
        }
    }

    public void writeByteArray(final byte[] data) throws IOException {
        ensure(1);
        int ctr = 0;
        while (true) {
            final int dataSizeToWrite = Math.min(data.length - ctr,
                    buffer.remaining());
            buffer.put(data, ctr, dataSizeToWrite);
            ctr += dataSizeToWrite;

            if (ctr == data.length) {
                break;
            }
            overflow();
        }
    }

    public void writeCharArray(final char[] data) throws IOException {
        ensure(2);
        int ctr = 0;
        while (true) {
            final ByteBuffer current = (ByteBuffer) buffer.underlying();
            final CharBuffer typedBuffer = current.asCharBuffer();
            final int dataSizeToWrite = Math.min(data.length - ctr,
                    typedBuffer.remaining());
            typedBuffer.put(data, ctr, dataSizeToWrite);
            buffer.position(typedBuffer.position() * 2 + current.position());
            ctr += dataSizeToWrite;
            if (ctr == data.length) {
                break;
            }
            overflow();
        }
    }

    public void writeShortArray(final short[] data) throws IOException {
        ensure(2);
        int ctr = 0;
        while (true) {
            final ByteBuffer current = (ByteBuffer) buffer.underlying();
            final ShortBuffer typedBuffer = current.asShortBuffer();
            final int dataSizeToWrite = Math.min(data.length - ctr,
                    typedBuffer.limit() - typedBuffer.position());
            typedBuffer.put(data, ctr, dataSizeToWrite);
            current.position(typedBuffer.position() * 2 + current.position());
            ctr += dataSizeToWrite;
            if (ctr == data.length) {
                break;
            }

            overflow();
        }
    }

    public void writeIntArray(final int[] data) throws IOException {
        ensure(4);
        int ctr = 0;
        while (true) {
            final ByteBuffer current = (ByteBuffer) buffer.underlying();
            final IntBuffer typedBuffer = current.asIntBuffer();
            final int dataSizeToWrite = Math.min(data.length - ctr,
                    typedBuffer.limit() - typedBuffer.position());
            typedBuffer.put(data, ctr, dataSizeToWrite);
            current.position(typedBuffer.position() * 4 + current.position());
            ctr += dataSizeToWrite;
            if (ctr == data.length) {
                break;
            }
            overflow();
        }
    }

    public void writeLongArray(final long[] data) throws IOException {
        ensure(8);
        int ctr = 0;
        while (true) {
            final ByteBuffer current = (ByteBuffer) buffer.underlying();
            final LongBuffer typedBuffer = current.asLongBuffer();
            final int dataSizeToWrite = Math.min(data.length - ctr,
                    typedBuffer.limit() - typedBuffer.position());
            typedBuffer.put(data, ctr, dataSizeToWrite);
            ctr += dataSizeToWrite;
            current.position(typedBuffer.position() * 8 + current.position());
            if (ctr == data.length) {
                break;
            }
            overflow();
        }
    }

    public void writeFloatArray(final float[] data) throws IOException {
        ensure(4);
        int ctr = 0;
        while (true) {
            final ByteBuffer current = (ByteBuffer) buffer.underlying();
            final FloatBuffer typedBuffer = current.asFloatBuffer();
            final int dataSizeToWrite = Math.min(data.length - ctr,
                    typedBuffer.limit() - typedBuffer.position());
            typedBuffer.put(data, ctr, dataSizeToWrite);
            current.position(typedBuffer.position() * 4 + current.position());
            ctr += dataSizeToWrite;

            if (ctr == data.length) {
                break;
            }

            overflow();
        }
    }

    public void writeDoubleArray(final double[] data) throws IOException {
        ensure(8);
        int ctr = 0;
        while (true) {
            final ByteBuffer current = (ByteBuffer) buffer.underlying();
            final DoubleBuffer typedBuffer = current.asDoubleBuffer();
            final int dataSizeToWrite = Math.min(data.length - ctr,
                    typedBuffer.limit() - typedBuffer.position());
            typedBuffer.put(data, ctr, dataSizeToWrite);
            current.position(typedBuffer.position() * 8 + current.position());
            ctr += dataSizeToWrite;
            if (ctr == data.length) {
                break;
            }

            overflow();
        }
    }
}

