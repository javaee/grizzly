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
package com.sun.grizzly.streams;

import java.io.EOFException;
import java.io.IOException;
import java.nio.BufferUnderflowException;

import java.util.LinkedList;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;
import com.sun.grizzly.Buffer;
import com.sun.grizzly.CompletionHandler;
import com.sun.grizzly.Connection;
import com.sun.grizzly.Grizzly;
import com.sun.grizzly.impl.FutureImpl;
import com.sun.grizzly.utils.conditions.Condition;

/**
 * Each method reads data from the current ByteBuffer.  If not enough data
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
 * @author Ken Cavanaugh
 * @author Alexey Stashok
 */
public abstract class AbstractStreamReader implements StreamReader {

    private static final boolean DEBUG = false;
    private static Logger logger = Grizzly.logger;

    private boolean isBlocking;

    private Connection connection;
    protected int bufferSize;

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
    }
    // Concurrency considerations:
    // Only one thread (the consumer) may invoke the readXXX methods.
    // dataReceived and close may be invoked by a producer thread.
    // The consumer thread will invoke readXXX methods far more often
    // than a typical producer will call dataReceived or (possibly) close.
    // So buffers must be protected from concurrent access, either by locking
    // or by a wait-free queue.  However, volatile is sufficient for current,
    // since we just need to ensure the visibility of the value of current to
    // all threads.
    //
    protected LinkedList dataRecords;
    private volatile int queueSize;
    private volatile Object current;
    private volatile boolean closed;
    protected volatile NotifyObject notifyObject;

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
        dataRecords = new LinkedList();
        queueSize = 0;
        current = null;
        closed = false;
        if (timeoutMillis < 0) {
            throw new IllegalArgumentException(
                    "Timeout must not be negative.");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isBlocking() {
        return isBlocking;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setBlocking(boolean isBlocking) {
        this.isBlocking = isBlocking;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean prependBuffer(final Buffer buffer) {
        return prepend(wrap(buffer));
    }

    protected boolean prepend(final Object data) {
        if (data == null) {
            return false;
        }

        final Buffer buffer = unwrap(data);

        if (closed) {
            buffer.dispose();
        } else {
            if (buffer.hasRemaining()) {
                if (current == null) {
                    current = data;
                } else {
                    dataRecords.addFirst(data);
                    queueSize += buffer.remaining();
                }
            }

            notifyCondition();
        }

        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean appendBuffer(final Buffer buffer) {
        return append(wrap(buffer));
    }

    protected boolean append(final Object data) {
        if (data == null) {
            return false;
        }

        final Buffer buffer = unwrap(data);

        if (closed) {
            buffer.dispose();
        } else {
            if (buffer.hasRemaining()) {
                if (current == null) {
                    current = data;
                } else {
                    dataRecords.addLast(data);
                    queueSize += buffer.remaining();
                }
            }

            notifyCondition();
        }

        return true;
    }

    private void notifyCondition() {
        if (notifyObject != null && notifyObject.condition.check(this)) {
            final NotifyObject localNotifyAvailObject = notifyObject;
            notifyObject = null;
            notifySuccess(localNotifyAvailObject.future,
                    localNotifyAvailObject.completionHandler,
                    availableDataSize());
        }
    }

    /**
     * Closes the <tt>StreamReader</tt> and causes all subsequent method calls
     * on this object to throw IllegalStateException.
     */
    @Override
    public void close() {
        closed = true;

        if (current != null) {
            unwrap(current).dispose();
            current = null;
        }

        if (dataRecords != null) {
            for (Object record : dataRecords) {
                unwrap(record).dispose();
            }
            dataRecords = null;
        }
        queueSize = 0;

        if (notifyObject != null) {
            NotifyObject localNotifyAvailObject = notifyObject;
            notifyObject = null;
            notifyFailure(localNotifyAvailObject.future,
                    localNotifyAvailObject.completionHandler,
                    new EOFException());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isClosed() {
        return closed;
    }

    private int currentAvailable() {
        if (current == null) {
            return 0;
        } else {
            return unwrap(current).remaining();
        }
    }

    private boolean checkRemaining(int size) throws IOException {
        if (current == null || !unwrap(current).hasRemaining()) {
            ensureRead();
        }
        return unwrap(current).remaining() >= size;
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


        if (current != null) {
            unwrap(current).dispose();
            current = null;
        }
        
        // First ensure that there is enough space
        Object next = poll();

        if (next == null) {
            if (readIfEmpty) {
                Object data = read0();
                if (data != null) {
                    append(data);
                }

                if (current == null && (current = poll()) == null) {
                    throw new BufferUnderflowException();
                }
            }
        } else {
            current = next;
        }

        if (DEBUG) {
            displayBuffer("current", unwrap(current));
        }
    }

    protected Buffer pollBuffer() {
        if (!dataRecords.isEmpty()) {
            Object data = dataRecords.removeFirst();
            final Buffer buffer = unwrap(data);
            queueSize -= buffer.remaining();
            return buffer;
        }

        return null;
    }

    protected Object poll() {
        if (!dataRecords.isEmpty()) {
            Object data = dataRecords.removeFirst();
            queueSize -= unwrap(data).remaining();
            return data;
        }

        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final boolean hasAvailableData() {
       return availableDataSize() > 0;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int availableDataSize() {
        return queueSize + currentAvailable();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean readBoolean() throws IOException {
        if (!checkRemaining(1)) {
            ensureRead();
        }
        final Buffer currentBuffer = unwrap(current);
        final boolean result = currentBuffer.get() == 1;

        current = tryReleaseCurrent(current, currentBuffer);
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public byte readByte() throws IOException {
        if (!checkRemaining(1)) {
            ensureRead();
        }
        final Buffer currentBuffer = unwrap(current);
        final byte result = currentBuffer.get();
        
        current = tryReleaseCurrent(current, currentBuffer);
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public char readChar()  throws IOException {
        if (checkRemaining(2)) {
            final Buffer currentBuffer = unwrap(current);
            final char result = currentBuffer.getChar();
            current = tryReleaseCurrent(current, currentBuffer);
            return result;
        }
        return (char) ((readByte() & 0xff) << 8 | readByte() & 0xff);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public short readShort() throws IOException {
        if (checkRemaining(2)) {
            final Buffer currentBuffer = unwrap(current);
            final short result = currentBuffer.getShort();
            current = tryReleaseCurrent(current, currentBuffer);
            return result;
        }
        return (short) ((readByte() & 0xff) << 8 | readByte() & 0xff);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int readInt() throws IOException {
        if (checkRemaining(4)) {
            final Buffer currentBuffer = unwrap(current);
            final int result = currentBuffer.getInt();
            current = tryReleaseCurrent(current, currentBuffer);
            return result;
        }
        return (readShort() & 0xffff) << 16 | readShort() & 0xffff;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long readLong() throws IOException {
        if (checkRemaining(8)) {
            final Buffer currentBuffer = unwrap(current);
            final long result = currentBuffer.getLong();
            current = tryReleaseCurrent(current, currentBuffer);
            return result;
        }
        return (readInt() & 0xffffffffL) << 32 | readInt() & 0xffffffffL;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    final public float readFloat() throws IOException {
        if (checkRemaining(4)) {
            final Buffer currentBuffer = unwrap(current);
            final float result = currentBuffer.getFloat();
            current = tryReleaseCurrent(current, currentBuffer);
            return result;
        }

        return Float.intBitsToFloat(readInt());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    final public double readDouble() throws IOException {
        if (checkRemaining(8)) {
            final Buffer currentBuffer = unwrap(current);
            final double result = currentBuffer.getDouble();
            current = tryReleaseCurrent(current, currentBuffer);
            return result;
        }
        return Double.longBitsToDouble(readLong());
    }

    private void arraySizeCheck(final int sizeInBytes) {
        if (!isBlocking && timeoutMillis == 0 && (sizeInBytes > availableDataSize())) {
            throw new BufferUnderflowException();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void readBooleanArray(boolean[] data) throws IOException {
        arraySizeCheck(data.length);
        for (int ctr = 0; ctr < data.length; ctr++) {
            data[ctr] = readBoolean();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void readByteArray(final byte[] data) throws IOException {
        readByteArray(data, 0, data.length);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void readByteArray(byte[] data, int offset, int length) throws IOException {
        arraySizeCheck(length);
        while (true) {
            checkRemaining(1);
            final Buffer typedBuffer = unwrap(current);
            int dataSizeToRead = length;
            if (dataSizeToRead < typedBuffer.remaining()) {
                typedBuffer.get(data, offset, dataSizeToRead);
            } else {
                dataSizeToRead = typedBuffer.remaining();
                typedBuffer.get(data, offset, dataSizeToRead);
                current = tryReleaseCurrent(current, typedBuffer);
            }

            offset += dataSizeToRead;
            length -= dataSizeToRead;

            if (length == 0) {
                break;
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void readBytes(final Buffer buffer) throws IOException {
        buffer.reset();
        arraySizeCheck(buffer.remaining());
        while (true) {
            checkRemaining(1);
            final Buffer typedBuffer = unwrap(current);
            if (buffer.remaining() > typedBuffer.remaining()) {
                buffer.put(typedBuffer);
                current = tryReleaseCurrent(current, typedBuffer);
            } else {
                final int save = typedBuffer.limit();
                typedBuffer.limit(buffer.remaining());
                final Buffer tail = typedBuffer.slice();
                typedBuffer.limit(save);
                buffer.put(tail);
                break;
            }

        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void readCharArray(final char[] data) throws IOException {
        arraySizeCheck(2 * data.length);
        for (int i = 0; i < data.length; i++) {
            data[i] = readChar();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void readShortArray(final short[] data) throws IOException {
        arraySizeCheck(2 * data.length);
        for (int i = 0; i < data.length; i++) {
            data[i] = readShort();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void readIntArray(final int[] data) throws IOException {
        arraySizeCheck(4 * data.length);
        for (int i = 0; i < data.length; i++) {
            data[i] = readInt();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void readLongArray(final long[] data) throws IOException {
        arraySizeCheck(8 * data.length);
        for (int i = 0; i < data.length; i++) {
            data[i] = readLong();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void readFloatArray(final float[] data) throws IOException {
        arraySizeCheck(4 * data.length);
        for (int i = 0; i < data.length; i++) {
            data[i] = readFloat();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void readDoubleArray(final double[] data) throws IOException {
        arraySizeCheck(8 * data.length);
        for (int i = 0; i < data.length; i++) {
            data[i] = readDouble();
        }

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<Integer> notifyAvailable(int size) {
        return notifyAvailable(size, null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<Integer> notifyAvailable(final int size,
            CompletionHandler<Integer> completionHandler) {
        return notifyCondition(new Condition<StreamReader>() {
            @Override
            public boolean check(StreamReader reader) {
                return reader.availableDataSize() >= size;
            }
        }, completionHandler);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<Integer> notifyCondition(Condition<StreamReader> condition) {
        return notifyCondition(condition, null);
    }

    private void notifySuccess(FutureImpl<Integer> future,
            CompletionHandler<Integer> completionHandler, int size) {
        if (completionHandler != null) {
            completionHandler.completed(getConnection(), size);
        }

        future.setResult(size);
    }

    private void notifyFailure(FutureImpl<Integer> future,
            CompletionHandler<Integer> completionHandler, Throwable e) {
        if (completionHandler != null) {
            completionHandler.failed(getConnection(), e);
        }

        future.failure(e);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Buffer readBuffer() throws IOException {
        checkRemaining(1);
        final Buffer retBuffer = unwrap(current);
        current = null;
        return retBuffer;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Buffer getBuffer() {
        return unwrap(current());
    }

    protected Object current() {
        if (current == null) {
            try {
                ensureRead(false);
            } catch (IOException e) {
                throw new IllegalStateException("Unexpected exception");
            }
        }

        return current;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void finishBuffer() {
        Object next = poll();
        if (next != null) {
            queueSize -= unwrap(next).remaining();
        }
        
        current = next;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Connection getConnection() {
        return connection;
    }

    public void setConnection(Connection connection) {
        if (connection != null) {
            bufferSize = connection.getReadBufferSize();
            isBlocking = connection.isBlocking();
        }
        
        this.connection = connection;
    }

    protected Buffer newBuffer(int size) {
        return getConnection().getTransport().getMemoryManager().allocate(size);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getBufferSize() {
        return bufferSize;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setBufferSize(int size) {
        this.bufferSize = size;
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

    private static final Object tryReleaseCurrent(final Object current,
            final Buffer currentBuffer) {
        if (currentBuffer != null && !currentBuffer.hasRemaining()) {
            currentBuffer.dispose();
            return null;
        }

        return current;
    }

    protected static class NotifyObject {

        private FutureImpl<Integer> future;
        private CompletionHandler<Integer> completionHandler;
        private Condition<StreamReader> condition;

        public NotifyObject(FutureImpl<Integer> future,
                CompletionHandler<Integer> completionHandler,
                Condition<StreamReader> condition) {
            this.future = future;
            this.completionHandler = completionHandler;
            this.condition = condition;
        }
    }

    protected abstract Object read0() throws IOException;

    protected abstract Object wrap(Buffer buffer);

    protected abstract Buffer unwrap(Object data);
}

