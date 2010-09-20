/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2010 Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.util.concurrent.Future;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.CompletionHandler;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.GrizzlyFuture;
import org.glassfish.grizzly.Transformer;
import org.glassfish.grizzly.utils.conditions.Condition;

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
public interface StreamReader extends Stream {

    /**
     * Method returns {@link Future}, using which it's possible check if
     * <tt>StreamReader</tt> has required amount of bytes available
     * for reading reading.
     *
     * @param size number of bytes, which should become available on
     *        <tt>StreamReader</tt>.
     * @return {@link Future}, using which it's possible to check whether
     * <tt>StreamReader</tt> has required amount of bytes available for reading.
     */
    public GrizzlyFuture<Integer> notifyAvailable(int size);

    /**
     * Method returns {@link Future}, using which it's possible check if
     * <tt>StreamReader</tt> has required amount of bytes available
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
    public GrizzlyFuture<Integer> notifyAvailable(int size,
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
    public GrizzlyFuture<Integer> notifyCondition(Condition condition);

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
    public GrizzlyFuture<Integer> notifyCondition(Condition condition,
            CompletionHandler<Integer> completionHandler);

    /**
     * Return <tt>true</tt> if <tt>StreamReader</tt> has available data, which
     * could be read, or <tt>false</tt> otherwise.
     *
     * @return <tt>true</tt> if <tt>StreamReader</tt> has available data, which
     * could be read, or <tt>false</tt> otherwise.
     */
    public boolean hasAvailable();

    /**
     * Return the number of bytes available for get calls.  An attempt to
     * get more data than is present in the stream will either result in 
     * blocking (if isBlocking() returns true) or a BufferUnderflowException.
     */
    public int available();

    /**
     * Get the next boolean in the stream.  Requires 1 byte.
     */
    boolean readBoolean() throws IOException;

    /**
     * Get the next byte in the stream.  Requires 1 byte.
     */
    public byte readByte() throws IOException;

    /**
     * Get the next character in the stream.  Requires 2 bytes.
     */
    public char readChar() throws IOException;

    /** Get the next short in the stream.  Requires 2 bytes.
     */
    public short readShort() throws IOException;

    /**
     * Get the next int in the stream.  Requires 4 bytes.
     */
    public int readInt() throws IOException;

    /**
     * Get the next long in the stream.  Requires 8 bytes.
     */
    public long readLong() throws IOException;

    /**
     * Get the next float in the stream.  Requires 4 bytes.
     */
    public float readFloat() throws IOException;

    /**
     * Get the next double in the stream.  Requires 8 bytes.
     */
    public double readDouble() throws IOException;

    /**
     * Fill data with booleans (byte 1=true, 0=false) from the stream.
     * If this method returns normally, data has been filled completely.
     * Requires data.length bytes.
     */
    public void readBooleanArray(boolean[] data) throws IOException;

    /**
     * Fill data with bytes from the stream.
     * If this method returns normally, data has been filled completely.
     * Requires data.length bytes.
     */
    public void readByteArray(byte[] data) throws IOException;

    /**
     * Fill data with bytes from the stream.
     * If this method returns normally, data has been filled completely.
     * Requires data.length bytes.
     */
    public void readByteArray(byte[] data, int offset, int length) throws IOException;

    /**
     * Fill the buffer with data from the stream (that is, copy data
     * from the stream to fill buffer from position to limit).
     * This is useful when data must be read
     * from one stream and then added to another stream for
     * further processing.
     */
    public void readBytes(Buffer buffer) throws IOException;

    /**
     * Fill data with characters from the stream.
     * If this method returns normally, data has been filled completely.
     * Requires 2*data.length bytes.
     */
    public void readCharArray(char[] data) throws IOException;

    /**
     * Fill data with characters from the stream.
     * If this method returns normally, data has been filled completely.
     * Requires 2*data.length bytes.
     */
    public void readShortArray(short[] data) throws IOException;

    /**
     * Fill data with characters from the stream.
     * If this method returns normally, data has been filled completely.
     * Requires 4*data.length bytes.
     */
    public void readIntArray(int[] data) throws IOException;

    /**
     * Fill data with characters from the stream.
     * If this method returns normally, data has been filled completely.
     * Requires 8*data.length bytes.
     */
    public void readLongArray(long[] data) throws IOException;

    /**
     * Fill data with characters from the stream.
     * If this method returns normally, data has been filled completely.
     * Requires 4*data.length bytes.
     */
    public void readFloatArray(float[] data) throws IOException;

    /**
     * Fill data with characters from the stream.
     * If this method returns normally, data has been filled completely.
     * Requires 8*data.length bytes.
     */
    public void readDoubleArray(double[] data) throws IOException;

    public void skip(int length);
    
    /**
     * Read and decode data from the <tt>StreamReader</tt>
     * 
     * @param <E> decoded data type
     * @param decoder {@link Transformer}
     * @return {@link Future}, which will hold the decoding state.
     */
    public <E> GrizzlyFuture<E> decode(Transformer<Stream, E> decoder);
    
    /**
     * Read and decode data from the <tt>StreamReader</tt>
     *
     * @param <E> decoded data type
     * @param decoder {@link Transformer}
     * @param completionHandler {@link CompletionHandler}, which will be
     *                          notified, when decoder will become ready.
     * @return {@link Future}, which will hold the decoding state.
     */
    public <E> GrizzlyFuture<E> decode(Transformer<Stream, E> decoder,
            CompletionHandler<E> completionHandler);

    /**
     * Returns <tt>true</tt>, if <tt>StreamReader</tt> has been closed,
     * or <tt>false</tt> otherwise.
     *
     * @return <tt>true</tt>, if <tt>StreamReader</tt> has been closed,
     * or <tt>false</tt> otherwise.
     */
    public boolean isClosed();

    public boolean isSupportBufferWindow();

    public Buffer getBufferWindow();

    public Buffer takeBufferWindow();
}

