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
package org.glassfish.grizzly.bbs ;

import org.glassfish.grizzly.bbs.BufferWrapper;
import java.io.Closeable ;

import java.nio.ByteOrder ;

/** Interface that defines methods for reading primitive types and arrays
 * of primitive types from a stream.  A stream is implemented as a sequence
 * of BufferWrappers which are supplied by the receiveData method.
 * The stream consumes the BufferWrappers: after all data has been read from
 * a BufferWrapper, BufferWrapper.dispose() is called on the BufferWrapper.
 */
public interface Reader extends Closeable {
    /** If this returns true, methods will block (possibly with a timeout)
     * until enough data is available.  If false, methods throw java.nio.BufferUnderflowException.
     */
    boolean isBlockingStream() ;

    /** Add more data to the stream.
     */
    void receiveData( BufferWrapper buffer ) ;

    /** Return the ByteOrder of the stream.
     * All streams default to big endian byte order.
     */
    ByteOrder order() ;

    /** Set the ByteOrder of the stream.
     */
    void order( ByteOrder byteOrder ) ;

    /** Return the number of bytes available for get calls.  An attempt to 
     * get more data than is present in the stream will either result in 
     * blocking (if isBlocking() returns true) or a BufferUnderflowException.
     */
    int availableDataSize() ;

    /** Get the next boolean in the stream.  Requires 1 byte.
     */
    boolean getBoolean() ;

    /** Get the next byte in the stream.  Requires 1 byte.
     */
    byte getByte() ;

    /** Get the next character in the stream.  Requires 2 bytes.
     */
    char getChar() ;

    /** Get the next short in the stream.  Requires 2 bytes.
     */
    short getShort() ;

    /** Get the next int in the stream.  Requires 4 bytes.
     */
    int getInt() ;

    /** Get the next long in the stream.  Requires 8 bytes.
     */
    long getLong() ;

    /** Get the next float in the stream.  Requires 4 bytes.
     */
    float getFloat() ;

    /** Get the next double in the stream.  Requires 8 bytes.
     */
    double getDouble() ;

    /** Fill data with booleans (byte 1=true, 0=false) from the stream.
     * If this method returns normally, data has been filled completely.
     * Requires data.length bytes.
     */
    void getBooleanArray( boolean[] data ) ;

    /** Fill data with bytes from the stream.
     * If this method returns normally, data has been filled completely.
     * Requires data.length bytes.
     */
    void getByteArray( byte[] data ) ;

    /** Fill the buffer with data from the stream (that is, copy data
     * from the stream to fill buffer from position to limit).
     * This is useful when data must be read
     * from one stream and then added to another stream for
     * further processing.
     */
    void getBytes( BufferWrapper buffer ) ;

    /** Fill data with characters from the stream.
     * If this method returns normally, data has been filled completely.
     * Requires 2*data.length bytes.
     */
    void getCharArray( char[] data ) ;

    /** Fill data with characters from the stream.
     * If this method returns normally, data has been filled completely.
     * Requires 2*data.length bytes.
     */
    void getShortArray( short[] data ) ;

    /** Fill data with characters from the stream.
     * If this method returns normally, data has been filled completely.
     * Requires 4*data.length bytes.
     */
    void getIntArray( int[] data ) ;

    /** Fill data with characters from the stream.
     * If this method returns normally, data has been filled completely.
     * Requires 8*data.length bytes.
     */
    void getLongArray( long[] data ) ;

    /** Fill data with characters from the stream.
     * If this method returns normally, data has been filled completely.
     * Requires 4*data.length bytes.
     */
    void getFloatArray( float[] data ) ;

    /** Fill data with characters from the stream.
     * If this method returns normally, data has been filled completely.
     * Requires 8*data.length bytes.
     */
    void getDoubleArray( double[] data ) ;
}

