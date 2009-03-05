/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 1997-2007 Sun Microsystems, Inc. All rights reserved.
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
 */
package org.glassfish.grizzly.streams;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteOrder;
import java.nio.BufferUnderflowException;

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.CompletionHandler;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.impl.FutureImpl;
import org.glassfish.grizzly.util.conditions.Condition;

/** Each method reads data from the current ByteBuffer.  If not enough data
 * is present in the current ByteBuffer, discard is called on the current
 * ByteBuffer and we advance to the next ByteBuffer, or block if not enough 
 * data is present.  If close() is called, all subsequent method calls will
 * throw an IllegalStateException, and any threads blocked waiting for more
 * data will be unblocked, and continue with an IllegalStateException from the
 * blocking method call.
 * <p>
 * dataReceived and close may be safely invoked by multiple threads.
 * The other methods must be invoked only by one thread, which is the reader of
 * this data stream.
 * 
 *  @author Ken Cavanaugh
 */
public abstract class AbstractStreamReader extends InputStream
        implements StreamReader {

    private static final boolean DEBUG = false;
    private static Logger logger = Grizzly.logger;

    private Mode mode;

    private Connection connection;
    protected int bufferSize = 8192;

    protected long timeoutMillis = 30000;
    
    private static void msg(final String msg) {
        logger.info("READERSTREAM:DEBUG:" + msg);
    }

    private static void msg(final Exception exc) {
        msg("Exception:" + exc);
        exc.printStackTrace();
    }

    private static void displayBuffer(final String str,
            final Buffer wrapper) {
        msg(str);
        msg("\tposition()     = " + wrapper.position());
        msg("\tlimit()        = " + wrapper.limit());
        msg("\tcapacity()     = " + wrapper.capacity());
    }    // Concurrency considerations:
    // Only one thread (the consumer) may invoke the getXXX methods.
    // dataReceived and close may be invoked by a producer thread.
    // The consumer thread will invoke readXXX methods far more often
    // than a typical producer will call dataReceived or (possibly) close.
    // So buffers must be protected from concurrent access, either by locking
    // or by a wait-free queue.  However, volatile is sufficient for current,
    // since we just need to ensure the visibility of the value of current to
    // all threads.
    //
    private BlockingQueue<Buffer> buffers;
    private AtomicInteger queueSize;
    private volatile Buffer current;
    private boolean closed;
    private long timeout;
    private ByteOrder byteOrder;//Large enough to hold the largest primitive type.
    public static final int HEADER_SIZE = 8;
    protected NotifyObject notifyObject;

    public AbstractStreamReader() {
        this(null);
    }

    /**
     * Create a new ByteBufferReader.
     * @param timeout Time in milliseconds to wait for more data:  If 0, the reader will
     * never block.
     * @throws TimeoutException when a read operation times out waiting for more data.
     */
    protected AbstractStreamReader(Connection connection) {
        setConnection(connection);
        buffers = new LinkedBlockingQueue<Buffer>();
        queueSize = new AtomicInteger();
        current = null;
        closed = false;
        if (timeout < 0) {
            throw new IllegalArgumentException(
                    "Timeout must not be negative.");
        }
        this.byteOrder = ByteOrder.BIG_ENDIAN;
    }

    public Mode getMode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode;
    }

    public ByteOrder order() {
        return byteOrder;
    }

    public void order(final ByteOrder byteOrder) {
        this.byteOrder = byteOrder;
    }

    /**
     * Add more data for the reader thread to read.
     * Data is assumed to be present from position() to limit().
     */
    public boolean receiveData(final Buffer buffer) {
        if (buffer == null) {
            return false;
        }

        if (closed) {
            buffer.dispose();
        } else {
            //byteBufferWrapper.flip();
            buffer.order(byteOrder);
            boolean isAdded = buffers.offer(buffer);
            if (!isAdded) return false;
            
            queueSize.addAndGet(buffer.remaining());

            if (notifyObject != null && notifyObject.condition.check(this)) {
                NotifyObject localNotifyAvailObject = notifyObject;
                notifyObject = null;
                notifySuccess(localNotifyAvailObject.future,
                        localNotifyAvailObject.completionHandler,
                        availableDataSize());
            }
        }

        return true;
    }

    @Override
    public int read() throws IOException {
        return readByte() & 0xff;
    }

    @Override
    public boolean markSupported() {
        return false;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        int doneBytes = 0;
        while (doneBytes < len) {
            if (!checkRemaining(1)) {
                ensureRead();
            }
            int read = Math.min(current.remaining(), len - doneBytes);

            current.get(b, off + (doneBytes), read);
            doneBytes += read;

        }
        return doneBytes;
    }

    /** Cause all subsequent method calls on this object to throw IllegalStateException.
     */
    @Override
    public synchronized void close() {
        closed = true;

        if (current != null) {
            current.dispose();
            current = null;
        }

        if (buffers != null) {
            for (Buffer bw : buffers) {
                bw.dispose();
            }
            buffers = null;
        }
        queueSize.set(0);

        if (notifyObject != null) {
            NotifyObject localNotifyAvailObject = notifyObject;
            notifyObject = null;
            notifyFailure(localNotifyAvailObject.future,
                    localNotifyAvailObject.completionHandler,
                    new EOFException());
        }
    }

    public boolean isClosed() {
        return closed;
    }

    private int currentAvailable() {
        if (current == null) {
            return 0;
        } else {
            return current.remaining();
        }
    }

    private boolean checkRemaining(int size) throws IOException {
        if (current == null || !current.hasRemaining()) {
            ensureRead();
        }
        return current.remaining() >= size;
    }

    // After this call, current must contain at least size bytes.
    // This call may block until more data is available.
    protected void ensureRead() throws IOException {
        ensureRead(true);
    }

    // After this call, current must contain at least size bytes.
    // This call may block until more data is available.
    protected void ensureRead(boolean readIfEmpty) throws IOException {
        if (closed) {
            throw new IllegalStateException("ByteBufferReader is closed");
        }
        // First ensure that there is enough space

        Buffer next = pollBuffer();

        if (next == null) {
            if (readIfEmpty) {
                Buffer buffer = read0();
                if (buffer != null) {
                    receiveData(buffer);
                    next = pollBuffer();
                }

                if (next == null) {
                    throw new BufferUnderflowException();
                }
            } else {
                return;
            }
        }

        next.position(0);

        if (current != null) {
            current.dispose();
        }
        current = next;

        if (DEBUG) {
            displayBuffer("current", current);
        }
    }

    protected Buffer pollBuffer() {
        Buffer buffer;
        if (timeout > 0) {
            try {
                buffer = buffers.poll(timeout, TimeUnit.MILLISECONDS);
            } catch (InterruptedException exc) {
                throw new RuntimeException(exc);
            }

            if (buffer != null) {
                queueSize.addAndGet(-buffer.remaining());
            }
        } else {
            buffer = buffers.poll();
            if (buffer != null) {
                queueSize.addAndGet(-buffer.remaining());
            }
        }

        return buffer;
    }
    
    public int availableDataSize() {
        return queueSize.get() + currentAvailable();
    }

    public boolean readBoolean() throws IOException {
        if (!checkRemaining(1)) {
            ensureRead();
        }
        return current.get() == 1 ? true : false;
    }

    public byte readByte() throws IOException {
        if (!checkRemaining(1)) {
            ensureRead();
        }
        return current.get();
    }

    public char readChar()  throws IOException {
        if (checkRemaining(2)) {
            return current.getChar();
        }
        if (order() == ByteOrder.BIG_ENDIAN) {
            return (char) ((readByte() & 0xff) << 8 | readByte() & 0xff);
        } else {
            return (char) (readByte() & 0xff | (readByte() & 0xff) << 8);
        }
    }

    public short readShort() throws IOException {
        if (checkRemaining(2)) {
            return current.getShort();
        }
        if (order() == ByteOrder.BIG_ENDIAN) {
            return (short) ((readByte() & 0xff) << 8 | readByte() & 0xff);
        } else {
            return (short) (readByte() & 0xff | (readByte() & 0xff) << 8);
        }


    }

    public int readInt() throws IOException {
        if (checkRemaining(4)) {
            return current.getInt();
        }
        if (order() == ByteOrder.BIG_ENDIAN) {
            return (readShort() & 0xffff) << 16 | readShort() & 0xffff;
        } else {
            return readShort() & 0xFFFF | (readShort() & 0xFFFF) << 16;
        }
    }

    public long readLong() throws IOException {
        if (checkRemaining(8)) {
            return current.getLong();
        }
        if (order() == ByteOrder.BIG_ENDIAN) {
            return (readInt() & 0xffffffffL) << 32 | readInt() & 0xffffffffL;
        } else {
            return readInt() & 0xFFFFFFFFL | (readInt() & 0xFFFFFFFFL) << 32;
        }
    }

    final public float readFloat() throws IOException {
        if (checkRemaining(4)) {
            return current.getFloat();
        }


        return Float.intBitsToFloat(readInt());

    }

    final public double readDouble() throws IOException {
        if (checkRemaining(8)) {
            return current.getDouble();
        }
        return Double.longBitsToDouble(readLong());
    }

    private void arraySizeCheck(final int sizeInBytes) {
        if ((timeout == 0) && (sizeInBytes > availableDataSize())) {
            throw new BufferUnderflowException();
        }
    }

    public void readBooleanArray(boolean[] data) throws IOException {
        arraySizeCheck(data.length);
        for (int ctr = 0; ctr < data.length; ctr++) {
            data[ctr] = readBoolean();
        }
    }

    public void readByteArray(final byte[] data) throws IOException {
        arraySizeCheck(data.length);
        int offset = 0;
        while (true) {
            checkRemaining(1);
            final Buffer typedBuffer = current;
            int dataSizeToRead = data.length - offset;
            if (dataSizeToRead > typedBuffer.remaining()) {
                dataSizeToRead = typedBuffer.remaining();
            }

            typedBuffer.get(data, offset, dataSizeToRead);
            offset += dataSizeToRead;

            if (offset == data.length) {
                break;
            }
        }
    }

    public void readBytes(final Buffer buffer) throws IOException {
        buffer.reset();
        arraySizeCheck(buffer.remaining());
        while (true) {

            checkRemaining(1);
            if (buffer.remaining() > current.remaining()) {
                buffer.put(current);
            } else {
                final int save = current.limit();
                current.limit(buffer.remaining());
                final Buffer tail = current.slice();
                current.limit(save);
                buffer.put(tail);
                break;
            }

        }
    }

    public void readCharArray(final char[] data) throws IOException {
        arraySizeCheck(2 * data.length);
        for (int i = 0; i < data.length; i++) {
            data[i] = readChar();
        }
    }

    public void readShortArray(final short[] data) throws IOException {
        arraySizeCheck(2 * data.length);
        for (int i = 0; i < data.length; i++) {
            data[i] = readShort();
        }
    }

    public void readIntArray(final int[] data) throws IOException {
        arraySizeCheck(4 * data.length);
        for (int i = 0; i < data.length; i++) {
            data[i] = readInt();
        }
    }

    public void readLongArray(final long[] data) throws IOException {
        arraySizeCheck(8 * data.length);
        for (int i = 0; i < data.length; i++) {
            data[i] = readLong();
        }
    }

    public void readFloatArray(final float[] data) throws IOException {
        arraySizeCheck(4 * data.length);
        for (int i = 0; i < data.length; i++) {
            data[i] = readFloat();
        }
    }

    public void readDoubleArray(final double[] data) throws IOException {
        arraySizeCheck(8 * data.length);
        for (int i = 0; i < data.length; i++) {
            data[i] = readDouble();
        }

    }

    public Future notifyAvailable(int size) {
        return notifyAvailable(size, null);
    }

    public Future notifyAvailable(final int size,
            CompletionHandler completionHandler) {
        return notifyCondition(new Condition<StreamReader>() {
            public boolean check(StreamReader reader) {
                return reader.availableDataSize() >= size;
            }
        }, completionHandler);
    }

    public Future notifyCondition(Condition<StreamReader> condition) {
        return notifyCondition(condition, null);
    }

    private void notifySuccess(FutureImpl future,
            CompletionHandler completionHandler, int size) {
        if (completionHandler != null) {
            completionHandler.completed(null, size);
        }

        future.setResult(size);
    }

    private void notifyFailure(FutureImpl future,
            CompletionHandler completionHandler, Throwable e) {
        if (completionHandler != null) {
            completionHandler.failed(null, e);
        }

        future.failure(e);
    }

    public Buffer readBuffer() throws IOException {
        checkRemaining(1);
        Buffer retBuffer = current;
        current = null;
        return retBuffer;
    }


    public Buffer getBuffer() throws IOException {
        if (current == null) {
            ensureRead(false);
        }

        return current;
    }

    public synchronized void finishBuffer() {
        Buffer next = buffers.poll();
        if (next != null) {
            next.position(0);
            queueSize.addAndGet(-next.remaining());
        }
        
        current = next;
    }

    public Connection getConnection() {
        return connection;
    }

    public void setConnection(Connection connection) {
        if (connection != null) {
            bufferSize = connection.getReadBufferSize();
            mode = connection.isBlocking() ? Mode.BLOCKING : Mode.NON_BLOCKING;
        }
        
        this.connection = connection;
    }

    protected Buffer newBuffer(int size) {
        return getConnection().getTransport().getMemoryManager().allocate(size);
    }

    public int getBufferSize() {
        return bufferSize;
    }

    public void setBufferSize(int size) {
        this.bufferSize = size;
    }

    public long getTimeout(TimeUnit timeunit) {
        return timeunit.convert(timeoutMillis, TimeUnit.MILLISECONDS);
    }

    public void setTimeout(long timeout, TimeUnit timeunit) {
        timeoutMillis = TimeUnit.MILLISECONDS.convert(timeout, timeunit);
    }

    protected static class NotifyObject {

        private FutureImpl future;
        private CompletionHandler completionHandler;
        private Condition<StreamReader> condition;

        public NotifyObject(FutureImpl future,
                CompletionHandler completionHandler,
                Condition<StreamReader> condition) {
            this.future = future;
            this.completionHandler = completionHandler;
            this.condition = condition;
        }
    }

    protected abstract Buffer read0() throws IOException;
}

