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

/**
 * Write the primitive Java types and arrays of primitives to some data sink.
 * This may include internal buffering for efficiency reasons.
 *
 * Note, that <tt>StreamWriter</tt> implementation may not be thread-safe.
 *
 * @author Ken Cavanaugh
 * @author Alexey Stashok
 */
public interface StreamWriter extends Stream {

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
     * Make sure that all data that has been written is
     * flushed from the stream to its destination.
     */
    Future<Integer> flush() throws IOException;

    /**
     * Make sure that all data that has been written is
     * flushed from the stream to its destination.
     */
    Future<Integer> flush(CompletionHandler<Integer> completionHandler)
            throws IOException;

    /**
     * Close the {@link StreamWriter} and make sure all data was flushed.
     */
    Future<Integer> close(CompletionHandler<Integer> completionHandler)
            throws IOException;

    /**
     * Write the <tt>boolean</tt> value to the <tt>StreamWriter</tt>.
     * 
     * @param data <tt>boolean</tt> value.
     * @throws java.io.IOException
     */
    void writeBoolean(boolean data) throws IOException;

    /**
     * Write the <tt>byte</tt> value to the <tt>StreamWriter</tt>.
     *
     * @param data <tt>byte</tt> value.
     * @throws java.io.IOException
     */
    void writeByte(byte data) throws IOException;

    /**
     * Write the <tt>char</tt> value to the <tt>StreamWriter</tt>.
     *
     * @param data <tt>char</tt> value.
     * @throws java.io.IOException
     */
    void writeChar(char data) throws IOException;

    /**
     * Write the <tt>short</tt> value to the <tt>StreamWriter</tt>.
     *
     * @param data <tt>short</tt> value.
     * @throws java.io.IOException
     */
    void writeShort(short data) throws IOException;

    /**
     * Write the <tt>int</tt> value to the <tt>StreamWriter</tt>.
     *
     * @param data <tt>int</tt> value.
     * @throws java.io.IOException
     */
    void writeInt(int data) throws IOException;

    /**
     * Write the <tt>long</tt> value to the <tt>StreamWriter</tt>.
     *
     * @param data <tt>long</tt> value.
     * @throws java.io.IOException
     */
    void writeLong(long data) throws IOException;

    /**
     * Write the <tt>float</tt> value to the <tt>StreamWriter</tt>.
     *
     * @param data <tt>float</tt> value.
     * @throws java.io.IOException
     */
    void writeFloat(float data) throws IOException;

    /**
     * Write the <tt>double</tt> value to the <tt>StreamWriter</tt>.
     *
     * @param data <tt>double</tt> value.
     * @throws java.io.IOException
     */
    void writeDouble(double data) throws IOException;

    /**
     * Write the array of <tt>boolean</tt> values to the <tt>StreamWriter</tt>.
     *
     * @param data array of <tt>boolean</tt> values.
     * @throws java.io.IOException
     */
    void writeBooleanArray(final boolean[] data) throws IOException;

    /**
     * Write the array of <tt>byte</tt> values to the <tt>StreamWriter</tt>.
     *
     * @param data array of <tt>byte</tt> values.
     * @throws java.io.IOException
     */
    void writeByteArray(final byte[] data) throws IOException;

    /**
     * Write the part of array of <tt>byte</tt> values to the
     * <tt>StreamWriter</tt>, using specific offset and length values.
     *
     * @param data array of <tt>byte</tt> values.
     * @param offset array offset to start from.
     * @param length number of bytes to write.
     * 
     * @throws java.io.IOException
     */
    void writeByteArray(final byte[] data, int offset, int length)
            throws IOException;

    /**
     * Write the array of <tt>char</tt> values to the <tt>StreamWriter</tt>.
     *
     * @param data array of <tt>char</tt> values.
     * @throws java.io.IOException
     */
    void writeCharArray(final char[] data) throws IOException;

    /**
     * Write the array of <tt>short</tt> values to the <tt>StreamWriter</tt>.
     *
     * @param data array of <tt>short</tt> values.
     * @throws java.io.IOException
     */
    void writeShortArray(short[] data) throws IOException;

    /**
     * Write the array of <tt>int</tt> values to the <tt>StreamWriter</tt>.
     *
     * @param data array of <tt>int</tt> values.
     * @throws java.io.IOException
     */
    void writeIntArray(int[] data) throws IOException;

    /**
     * Write the array of <tt>long</tt> values to the <tt>StreamWriter</tt>.
     *
     * @param data array of <tt>long</tt> values.
     * @throws java.io.IOException
     */
    void writeLongArray(long[] data) throws IOException;

    /**
     * Write the array of <tt>float</tt> values to the <tt>StreamWriter</tt>.
     *
     * @param data array of <tt>float</tt> values.
     * @throws java.io.IOException
     */
    void writeFloatArray(float[] data) throws IOException;

    /**
     * Write the array of <tt>double</tt> values to the <tt>StreamWriter</tt>.
     *
     * @param data array of <tt>double</tt> values.
     * @throws java.io.IOException
     */
    void writeDoubleArray(double[] data) throws IOException;

    /**
     * Write the {@link Buffer} to the <tt>StreamWriter</tt>.
     *
     * @param buffer {@link Buffer}.
     * 
     * @throws java.io.IOException
     */
    public void writeBuffer(Buffer buffer) throws IOException;

    /**
     * Puts {@link StreamReader} available data to this <tt>StreamWriter</tt>
     * This method will make possible direct writing from {@link StreamReader},
     * avoiding {@link Buffer} copying.
     *
     * @param stream {@link StreamReader}
     */
    void writeStream(StreamReader stream) throws IOException;

    /**
     * Get the {@link Connection} this <tt>StreamWriter</tt> belongs to.
     *
     * @return the {@link Connection} this <tt>StreamWriter</tt> belongs to.
     */
    Connection getConnection();

    /**
     * Get the current {@link Buffer}, where the <tt>StreamWriter</tt> buffers
     * output.
     * 
     * @return the current {@link Buffer}, where the <tt>StreamWriter</tt> buffers
     * output.
     */
    Buffer getBuffer();

    /**
     * Get the preferred {@link Buffer} size to be used for <tt>StreamWriter</tt>
     * write operations.
     *
     * @return the preferred {@link Buffer} size to be used for <tt>StreamWriter</tt>
     * write operations.
     */
    int getBufferSize();

    /**
     * Set the preferred {@link Buffer} size to be used for <tt>StreamWriter</tt>
     * write operations.
     *
     * @param size the preferred {@link Buffer} size to be used for
     * <tt>StreamWriter</tt> write operations.
     */
    void setBufferSize(int size);

    /**
     * Get the timeout for <tt>StreamWriter</tt> I/O operations.
     *
     * @param timeunit timeout unit {@link TimeUnit}.
     * @return the timeout for <tt>StreamWriter</tt> I/O operations.
     */
    long getTimeout(TimeUnit timeunit);

    /**
     * Set the timeout for <tt>StreamWriter</tt> I/O operations.
     *
     * @param timeout the timeout for <tt>StreamWriter</tt> I/O operations.
     * @param timeunit timeout unit {@link TimeUnit}.
     */
    void setTimeout(long timeout, TimeUnit timeunit);
}

