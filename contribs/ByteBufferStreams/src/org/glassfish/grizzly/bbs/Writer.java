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

/** Write the primitive Java types and arrays of primitives to some data sink.  
 * This may include internal buffering for efficiency reasons.
 */
public interface Writer extends Closeable {
    /** Interface that defines how full buffers are handled.  Typically this
     * implies some kind of IO, and eventual disposition of the used buffer.
     */
    interface BufferHandler {
        /** Dispose of the current ByteBuffer and return a new ByteBuffer
         * which will be written to from position to limit.
         * Note that current may be null.
         */
        BufferWrapper overflow( BufferWrapper current ) ;

        /** Dispose of the current ByteBuffer.
         * Used when closing a ByteBufferWriter.
         */
        void close( BufferWrapper current ) ;
    }

    /** Get the BufferHandler in use for this stream.
     */
    BufferHandler bufferHandler() ;

    /** Return the ByteOrder of the stream.
     * All streams default to big endian byte order.
     */
    ByteOrder order() ;

    /** Set the ByteOrder of the stream.
     */
    void order( ByteOrder byteOrder ) ;

    /** Make sure that all data that has been written is
     * flushed from the stream to its destination.
     */
    void flush() ;

    void putBoolean( boolean data ) ;

    void putByte( byte data ) ;

    void putChar( char data ) ;

    void putShort( short data ) ;

    void putInt( int data ) ;

    void putLong( long data ) ;

    void putFloat( float data ) ;

    void putDouble( double data ) ;

    void putBooleanArray( boolean[] data ) ;

    void putByteArray( byte[] data ) ;

    void putCharArray( final char[] data ) ;

    void putShortArray( short[] data ) ;

    void putIntArray( int[] data ) ;

    void putLongArray( long[] data ) ;

    void putFloatArray( float[] data ) ;

    void putDoubleArray( double[] data ) ;

    /* We need a means to remember positions within the stream that can be restored
     * later, so that we can 
     *
     * Need this in reader and writer!

    / ** Save the current position in the stream for possible later use.
     * Saving a Position on a stream has implications on buffer usage: the
     * buffers in use that include the Position and after it will NOT be
     * sent to the overflow handler until the Position is closed.
     * /
    Position save() ;

    / ** Restore the current position in the stream to the Position.
     * /
    void restore( Position pos ) ; 
    */
}

