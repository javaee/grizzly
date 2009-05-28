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

import java.io.Closeable;

import java.io.IOException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import com.sun.grizzly.Buffer;
import com.sun.grizzly.CompletionHandler;
import com.sun.grizzly.Connection;
import com.sun.grizzly.utils.conditions.Condition;

/**
 * Interface that defines methods for reading primitive types and arrays
 * of primitive types from a stream.  A stream is implemented as a sequence
 * of {@link Buffer}s which are supplied by the receiveData method.
 * The stream consumes the {@link Buffer}s: after all data has been read from
 * a {@link Buffer}, {@link Buffer#dispose()} is called.
 *
 * Note, that <tt>StreamReader</tt> implementation may not be thread-safe.
 *
 * @see StreamWriter
 * @see Connection
 *
 * @author Ken Cavanaugh
 * @author Alexey Stashok
 */
public interface StreamReader extends Closeable {

    /**
     * Returns the {@link StreamReader} mode.
     * <tt>true</tt>, if {@link StreamReader} is operating in blocking mode, or
     * <tt>false</tt> otherwise.
     *
     * @return the {@link StreamReader} mode.
     */
    public boolean isBlocking();

    /**
     * Sets the {@link StreamReader} mode.
     *
     * @param isBlocking <tt>true</tt>, if {@link StreamReader} is operating in
     * blocking mode, or <tt>false</tt> otherwise.
     */
    public void setBlocking(boolean isBlocking);

    /**
     * Method returns {@link Future}, using which it's possible check if
     * <tt>StreamReader</tt> has required amound of bytes available
     * for reading reading.
     *
     * @param size number of bytes, which should become available on
     *        <tt>StreamReader</tt>.
     * @return {@link Future}, using which it's possible to check whether
     * <tt>StreamReader</tt> has required amount of bytes available for reading.
     */
    public Future<Integer> notifyAvailable(int size);

    /**
     * Method returns {@link Future}, using which it's possible check if
     * <tt>StreamReader</tt> has required amound of bytes available
     * for reading reading.
     * {@link CompletionHandler} is also passed to get notified, once required
     * number of bytes will become available for reading.
     *
     * @param size number of bytes, which should become available on
     *        <tt>StreamReader</tt>.
     * @param completionHandler {@link CompletionHandler}, which will be notified
     *        once required number of bytes will become available.
     * 
     * @return {@link Future}, using which it's possible to check whether
     * <tt>StreamReader</tt> has required amount of bytes available for reading.
     */
    public Future<Integer> notifyAvailable(int size,
            CompletionHandler<Integer> completionHandler);

    /**
     * Method returns {@link Future}, using which it's possible check if
     * <tt>StreamReader</tt> meets specific {@link Condition}.
     *
     * @param condition {@link Condition} <tt>StreamReader</tt> should meet.
     *
     * @return {@link Future}, using which it's possible to check whether
     * <tt>StreamReader</tt> meets the required {@link Condition}.
     */
    public Future<Integer> notifyCondition(Condition<StreamReader> condition);

    /**
     * Method returns {@link Future}, using which it's possible check if
     * <tt>StreamReader</tt> meets specific {@link Condition}.
     * {@link CompletionHandler} is also passed to get notified, once
     * the {@link Condition} will be satisfied.
     *
     * @param condition {@link Condition} <tt>StreamReader</tt> should meet.
     * @param completionHandler {@link CompletionHandler}, which will be
     * notified, once the {@link Condition} will be satisfied.
     *
     * @return {@link Future}, using which it's possible to check whether
     * <tt>StreamReader</tt> meets the required {@link Condition}.
     */
    public Future<Integer> notifyCondition(Condition<StreamReader> condition,
            CompletionHandler<Integer> completionHandler);
    
    /**
     * Add more data to the beginning of the stream.
     * @return true, if buffer was prepended to the {@link StreamReader},
     * or false otherwise
     */
    boolean prependBuffer(Buffer buffer);

    /**
     * Add more data to the end of the stream.
     * @return true, if buffer was appended to the {@link StreamReader},
     * or false otherwise
     */
    boolean appendBuffer(Buffer buffer);

    /**
     * Return <tt>true</tt> if <tt>StreamReader</tt> has available data, which
     * could be read, or <tt>false</tt> otherwise.
     *
     * @return <tt>true</tt> if <tt>StreamReader</tt> has available data, which
     * could be read, or <tt>false</tt> otherwise.
     */
    boolean hasAvailableData();

    /**
     * Return the number of bytes available for get calls.  An attempt to
     * get more data than is present in the stream will either result in 
     * blocking (if isBlocking() returns true) or a BufferUnderflowException.
     */
    int availableDataSize();

    /**
     * Get the next boolean in the stream.  Requires 1 byte.
     */
    boolean readBoolean() throws IOException;

    /**
     * Get the next byte in the stream.  Requires 1 byte.
     */
    byte readByte() throws IOException;

    /**
     * Get the next character in the stream.  Requires 2 bytes.
     */
    char readChar() throws IOException;

    /** Get the next short in the stream.  Requires 2 bytes.
     */
    short readShort() throws IOException;

    /**
     * Get the next int in the stream.  Requires 4 bytes.
     */
    int readInt() throws IOException;

    /**
     * Get the next long in the stream.  Requires 8 bytes.
     */
    long readLong() throws IOException;

    /**
     * Get the next float in the stream.  Requires 4 bytes.
     */
    float readFloat() throws IOException;

    /**
     * Get the next double in the stream.  Requires 8 bytes.
     */
    double readDouble() throws IOException;

    /**
     * Fill data with booleans (byte 1=true, 0=false) from the stream.
     * If this method returns normally, data has been filled completely.
     * Requires data.length bytes.
     */
    void readBooleanArray(boolean[] data) throws IOException;

    /**
     * Fill data with bytes from the stream.
     * If this method returns normally, data has been filled completely.
     * Requires data.length bytes.
     */
    void readByteArray(byte[] data) throws IOException;

    /**
     * Fill data with bytes from the stream.
     * If this method returns normally, data has been filled completely.
     * Requires data.length bytes.
     */
    void readByteArray(byte[] data, int offset, int length) throws IOException;

    /**
     * Fill the buffer with data from the stream (that is, copy data
     * from the stream to fill buffer from position to limit).
     * This is useful when data must be read
     * from one stream and then added to another stream for
     * further processing.
     */
    void readBytes(Buffer buffer) throws IOException;

    /**
     * Fill data with characters from the stream.
     * If this method returns normally, data has been filled completely.
     * Requires 2*data.length bytes.
     */
    void readCharArray(char[] data) throws IOException;

    /**
     * Fill data with characters from the stream.
     * If this method returns normally, data has been filled completely.
     * Requires 2*data.length bytes.
     */
    void readShortArray(short[] data) throws IOException;

    /**
     * Fill data with characters from the stream.
     * If this method returns normally, data has been filled completely.
     * Requires 4*data.length bytes.
     */
    void readIntArray(int[] data) throws IOException;

    /**
     * Fill data with characters from the stream.
     * If this method returns normally, data has been filled completely.
     * Requires 8*data.length bytes.
     */
    void readLongArray(long[] data) throws IOException;

    /**
     * Fill data with characters from the stream.
     * If this method returns normally, data has been filled completely.
     * Requires 4*data.length bytes.
     */
    void readFloatArray(float[] data) throws IOException;

    /**
     * Fill data with characters from the stream.
     * If this method returns normally, data has been filled completely.
     * Requires 8*data.length bytes.
     */
    void readDoubleArray(double[] data) throws IOException;

    /**
     * Returns <tt>true</tt>, if <tt>StreamReader</tt> has been closed,
     * or <tt>false</tt> otherwise.
     *
     * @return <tt>true</tt>, if <tt>StreamReader</tt> has been closed,
     * or <tt>false</tt> otherwise.
     */
    boolean isClosed();

    /**
     * Returns the current <tt>StreamReader</tt>'s source {@link Buffer} and
     * makes next available {@link Buffer} current.
     * 
     * @return the current <tt>StreamReader</tt>'s source {@link Buffer}
     * @throws java.io.IOException
     */
    Buffer readBuffer() throws IOException;
    
    /**
     * Return the current <tt>StreamReader</tt>'s source {@link Buffer}.
     * Unlike {@link StreamReader#readBuffer()}, this method doesn't
     * make any internal updates of current {@link Buffer}.
     *
     * @return the current <tt>StreamReader</tt>'s source {@link Buffer}.
     */
    Buffer getBuffer();

    /**
     * Finishes processing of the current <tt>StreamReader</tt>'s source
     * {@link Buffer}. This method doesn't call {@link Buffer#dispose()}.
     */
    void finishBuffer();

    /**
     * Get the {@link Connection} this <tt>StreamReader</tt> belongs to.
     * 
     * @return the {@link Connection} this <tt>StreamReader</tt> belongs to.
     */
    Connection getConnection();

    /**
     * Get the preferred {@link Buffer} size to be used for <tt>StreamReader</tt>
     * read operations.
     * 
     * @return the preferred {@link Buffer} size to be used for <tt>StreamReader</tt>
     * read operations.
     */
    int getBufferSize();

    /**
     * Set the preferred {@link Buffer} size to be used for <tt>StreamReader</tt>
     * read operations.
     *
     * @param size the preferred {@link Buffer} size to be used for
     * <tt>StreamReader</tt> read operations.
     */
    void setBufferSize(int size);

    /**
     * Get the timeout for <tt>StreamReader</tt> I/O operations.
     * 
     * @param timeunit timeout unit {@link TimeUnit}.
     * @return the timeout for <tt>StreamReader</tt> I/O operations.
     */
    long getTimeout(TimeUnit timeunit);

    /**
     * Set the timeout for <tt>StreamReader</tt> I/O operations.
     * 
     * @param timeout the timeout for <tt>StreamReader</tt> I/O operations.
     * @param timeunit timeout unit {@link TimeUnit}.
     */
    void setTimeout(long timeout, TimeUnit timeunit);
}

