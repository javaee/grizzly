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

import java.io.Closeable;

import java.io.IOException;
import java.nio.ByteOrder;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.CompletionHandler;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.util.conditions.Condition;

/** Interface that defines methods for reading primitive types and arrays
 * of primitive types from a stream.  A stream is implemented as a sequence
 * of BufferWrappers which are supplied by the receiveData method.
 * The stream consumes the BufferWrappers: after all data has been read from
 * a BufferWrapper, BufferWrapper.dispose() is called on the BufferWrapper.
 * @author Ken Cavanaugh
 */
public interface StreamReader extends Closeable {
    public enum Mode {
        NON_BLOCKING, BLOCKING, FEEDER;
    }
    
    public Future notifyAvailable(int length);

    public Future notifyAvailable(int length, CompletionHandler completionHandler);

    public Future notifyCondition(Condition<StreamReader> condition);

    public Future notifyCondition(Condition<StreamReader> condition,
            CompletionHandler completionHandler);

    public Mode getMode();

    public void setMode(Mode mode);

    /**
     * Add more data to the stream.
     * @return true, if buffer was appended to the {@link StreamReader},
     * or false otherwise
     */
    boolean receiveData(Buffer buffer);

    /** Return the ByteOrder of the stream.
     * All streams default to big endian byte order.
     */
    ByteOrder order();

    /** Set the ByteOrder of the stream.
     */
    void order(ByteOrder byteOrder);

    /** Return the number of bytes available for get calls.  An attempt to 
     * get more data than is present in the stream will either result in 
     * blocking (if isBlocking() returns true) or a BufferUnderflowException.
     */
    int availableDataSize();

    /** Get the next boolean in the stream.  Requires 1 byte.
     */
    boolean readBoolean() throws IOException;

    /** Get the next byte in the stream.  Requires 1 byte.
     */
    byte readByte() throws IOException;

    /** Get the next character in the stream.  Requires 2 bytes.
     */
    char readChar() throws IOException;

    /** Get the next short in the stream.  Requires 2 bytes.
     */
    short readShort() throws IOException;

    /** Get the next int in the stream.  Requires 4 bytes.
     */
    int readInt() throws IOException;

    /** Get the next long in the stream.  Requires 8 bytes.
     */
    long readLong() throws IOException;

    /** Get the next float in the stream.  Requires 4 bytes.
     */
    float readFloat() throws IOException;

    /** Get the next double in the stream.  Requires 8 bytes.
     */
    double readDouble() throws IOException;

    /** Fill data with booleans (byte 1=true, 0=false) from the stream.
     * If this method returns normally, data has been filled completely.
     * Requires data.length bytes.
     */
    void readBooleanArray(boolean[] data) throws IOException;

    /** Fill data with bytes from the stream.
     * If this method returns normally, data has been filled completely.
     * Requires data.length bytes.
     */
    void readByteArray(byte[] data) throws IOException;

    /** Fill the buffer with data from the stream (that is, copy data
     * from the stream to fill buffer from position to limit).
     * This is useful when data must be read
     * from one stream and then added to another stream for
     * further processing.
     */
    void readBytes(Buffer buffer) throws IOException;

    /** Fill data with characters from the stream.
     * If this method returns normally, data has been filled completely.
     * Requires 2*data.length bytes.
     */
    void readCharArray(char[] data) throws IOException;

    /** Fill data with characters from the stream.
     * If this method returns normally, data has been filled completely.
     * Requires 2*data.length bytes.
     */
    void readShortArray(short[] data) throws IOException;

    /** Fill data with characters from the stream.
     * If this method returns normally, data has been filled completely.
     * Requires 4*data.length bytes.
     */
    void readIntArray(int[] data) throws IOException;

    /** Fill data with characters from the stream.
     * If this method returns normally, data has been filled completely.
     * Requires 8*data.length bytes.
     */
    void readLongArray(long[] data) throws IOException;

    /** Fill data with characters from the stream.
     * If this method returns normally, data has been filled completely.
     * Requires 4*data.length bytes.
     */
    void readFloatArray(float[] data) throws IOException;

    /** Fill data with characters from the stream.
     * If this method returns normally, data has been filled completely.
     * Requires 8*data.length bytes.
     */
    void readDoubleArray(double[] data) throws IOException;

    boolean isClosed();

    Buffer readBuffer() throws IOException;
    
    Buffer getBuffer();

    void finishBuffer();

    Connection getConnection();

    int getBufferSize();

    void setBufferSize(int size);

    long getTimeout(TimeUnit timeunit);
    void setTimeout(long timeout, TimeUnit timeunit);
}

