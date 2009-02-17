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
package org.glassfish.grizzly.bbs.impl ;

import org.glassfish.grizzly.bbs.BufferWrapper;
import org.glassfish.grizzly.bbs.*;
import java.nio.ByteOrder ;
import java.nio.ByteBuffer ;
import java.nio.BufferUnderflowException ;
import java.nio.CharBuffer ;
import java.nio.ShortBuffer ;
import java.nio.IntBuffer ;
import java.nio.LongBuffer ;
import java.nio.FloatBuffer ;
import java.nio.DoubleBuffer ;

import java.util.concurrent.TimeUnit ;
import java.util.concurrent.BlockingQueue ;
import java.util.concurrent.LinkedBlockingQueue ;

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
 */
public class ReaderImpl implements Reader {
    private static final boolean DEBUG = false ;

    private static void msg( final String msg ) {
        System.out.println( "READER:DEBUG:" + msg ) ;
    }

    private static void msg( final Exception exc ) {
        msg( "Exception:" + exc ) ;
        exc.printStackTrace() ;
    }

    private static void displayBuffer( final String str, 
        final BufferWrapper wrapper ) {
        msg( str ) ;
        msg( "\tposition()     = " + wrapper.buffer().position() ) ;
        msg( "\tlimit()        = " + wrapper.buffer().limit() ) ;
        msg( "\tcapacity()     = " + wrapper.buffer().capacity() ) ;
    }

    // Concurrency considerations:
    // Only one thread (the consumer) may invoke the getXXX methods.
    // dataReceived and close may be invoked by a producer thread.
    // The consumer thread will invoke readXXX methods far more often
    // than a typical producer will call dataReceived or (possibly) close.
    // So buffers must be protected from concurrent access, either by locking
    // or by a wait-free queue.  However, volatile is sufficient for current,
    // since we just need to ensure the visibility of the value of current to
    // all threads.
    //
    private BlockingQueue<BufferWrapper> buffers ;
    private int queueSize ;
    private volatile BufferWrapper current ;
    private boolean closed ;
    private final long timeout ;
    private ByteOrder byteOrder ;
        
    // Large enough to hold the largest primitive type.
    public static final int HEADER_SIZE = 8 ;

    /** Create a new ByteBufferReader.
     * @param timeout Time in milliseconds to wait for more data:  If 0, the reader will
     * never block.
     * @throws TimeoutException when a read operation times out waiting for more data.
     */
    public ReaderImpl( final long timeout ) {
	buffers = new LinkedBlockingQueue<BufferWrapper>() ;
        queueSize = 0 ;
	current = null ;
	closed = false ;
        if (timeout < 0) {
            throw new IllegalArgumentException( 
                "Timeout must not be negative." ) ;
        }
        this.timeout = timeout ;
        this.byteOrder = ByteOrder.BIG_ENDIAN ;
    }

    public boolean isBlockingStream() {
        return timeout > 0 ;
    }

    public synchronized  ByteOrder order() {
        return byteOrder ;
    }

    public synchronized void order( final ByteOrder byteOrder ) {
        this.byteOrder = byteOrder ;
    }
    
    /** Add more data for the reader thread to read.
     * Data is assumed to be present from position() to limit().
     */
    public synchronized void receiveData( final BufferWrapper bufferWrapper ) {
        if (closed) {
            bufferWrapper.dispose() ;
        } else {
            bufferWrapper.reset() ;
            bufferWrapper.buffer().order( byteOrder ) ;
            buffers.offer( bufferWrapper ) ;
            queueSize += bufferWrapper.remaining() ;
        }
    }

    /** Cause all subsequent method calls on this object to throw IllegalStateException.
     */
    public synchronized void close() {
	closed = true ; 

        if (current != null) {
            current.dispose() ;
            current = null ;
        }

        if (buffers != null) {
            for (BufferWrapper bw : buffers) {
                bw.dispose() ;
            }
            buffers = null ;
        }
        
        queueSize = 0 ;
    }

    private synchronized int currentAvailable() {
        if (current == null) {
            return 0 ;
        } else { 
            return current.buffer().remaining() ;
        }
    }

    private synchronized int totalAvailable() {
        return queueSize + currentAvailable() ;
    }

    // After this call, current must contain at least size bytes.
    // This call may block until more data is available.
    public synchronized void ensurePrimitive( final int size ) {
        if (closed)
            throw new IllegalStateException( "ByteBufferReader is closed" ) ;

        // First ensure that there is enough space
        while (currentAvailable() < size) {
            BufferWrapper next ;
            if (timeout > 0) {
                try {
                    next = buffers.poll( timeout, TimeUnit.MILLISECONDS ) ;
                    if (next != null) {
                        queueSize -= next.remaining() ;
                    }
                } catch (InterruptedException exc) {
                    throw new RuntimeException( exc ) ;
                }

                if (next == null)
                    throw new RuntimeException( "Timed out in read while waiting for more data" ) ;
            } else {
                next = buffers.poll() ;
                if (next == null) 
                    throw new BufferUnderflowException( ) ;
            } 
    
            if (current != null) {
                next.prepend( current.buffer() ) ;
                current.dispose() ;
            }

            current = next ;
        }

        if (DEBUG) {
            displayBuffer( "current", current ) ;
        }
    }

    public synchronized int availableDataSize() {
        return queueSize + current.remaining() ;
    }

    public boolean getBoolean() {
        try {
            ensurePrimitive( 1 ) ;
            return current.buffer().get() == 1 ? true : false ;
        } catch (Exception exc) {
            if (DEBUG) {
                msg( exc ) ;
                displayBuffer( "getBoolean buffer underflow: current", 
                    current ) ;
            }
        }

        return false ;
    }

    public byte getByte() {
        try {
            ensurePrimitive( 1 ) ;
            return current.buffer().get() ;
        } catch (Exception exc) {
            if (DEBUG) {
                msg( exc ) ;
                displayBuffer( "getByte buffer underflow: current", current ) ;
            }
        }

        return 0;
    }

    public char getChar() {
        ensurePrimitive( 2 ) ;
        return current.buffer().getChar() ;
    }

    public short getShort() {
        ensurePrimitive( 2 ) ;
        return current.buffer().getShort() ;
    }

    public int getInt() {
        ensurePrimitive( 4 ) ;
        return current.buffer().getInt() ;
    }

    public long getLong() {
        ensurePrimitive( 8 ) ;
        return current.buffer().getLong() ;
    }

    public float getFloat() {
        ensurePrimitive( 4 ) ;
        return current.buffer().getFloat() ;
    }

    public double getDouble() {
        ensurePrimitive( 8 ) ;
        return current.buffer().getDouble() ;
    }

    private void arraySizeCheck( final int sizeInBytes ) {
        if ((timeout == 0) && (sizeInBytes > totalAvailable())) {
            throw new BufferUnderflowException() ;
        }
    }

    public void getBooleanArray( boolean[] data ) {
        arraySizeCheck( data.length ) ;
        for (int ctr=0; ctr<data.length; ctr++) {
            data[ctr] = getBoolean() ;
        }
    }

    public void getByteArray( final byte[] data ) {
        arraySizeCheck( data.length ) ;
        int offset = 0 ;
        while (true) {
            final ByteBuffer typedBuffer = current.buffer() ;
            int dataSizeToRead = data.length - offset ;
            if (dataSizeToRead > typedBuffer.remaining()) {
                dataSizeToRead = typedBuffer.remaining() ;
            }

            typedBuffer.get( data, offset, dataSizeToRead ) ;
            offset += dataSizeToRead ;
            if (offset == data.length) {
                break ;
            }

            ensurePrimitive( 1 ) ;
        }
    }

    public void getBytes( final BufferWrapper buffer ) {
        buffer.reset() ;
        arraySizeCheck( buffer.remaining() ) ;
        while (true) {
            if (buffer.remaining() > current.remaining()) {
                buffer.buffer().put( current.buffer() ) ;
            } else {
                final int save = current.buffer().limit() ;
                current.buffer().limit( buffer.remaining() ) ;
                final ByteBuffer tail = current.buffer().slice() ;
                current.buffer().limit( save ) ;
                buffer.buffer().put( tail ) ;
                break ;
            }

            ensurePrimitive(1) ;
        }
    }

    public void getCharArray( final char[] data ) {
        arraySizeCheck( 2*data.length ) ;
        int offset = 0 ;
        while (true) {
            final CharBuffer typedBuffer = current.buffer().asCharBuffer() ;
            int dataSizeToRead = data.length - offset ;
            if (dataSizeToRead > typedBuffer.remaining()) {
                dataSizeToRead = typedBuffer.remaining() ;
            }

            typedBuffer.get( data, offset, dataSizeToRead ) ;
            offset += dataSizeToRead ;
            if (offset == data.length)
                break ;

            ensurePrimitive( 2 ) ;
        }
    }

    public void getShortArray( final short[] data ) {
        arraySizeCheck( 2*data.length ) ;
        int offset = 0 ;
        while (true) {
            final ShortBuffer typedBuffer = current.buffer().asShortBuffer() ;
            int dataSizeToRead = data.length - offset ;
            if (dataSizeToRead > typedBuffer.remaining()) {
                dataSizeToRead = typedBuffer.remaining() ;
            }

            typedBuffer.get( data, offset, dataSizeToRead ) ;
            offset += dataSizeToRead ;
            if (offset == data.length)
                break ;

            ensurePrimitive( 2 ) ;
        }
    }

    public void getIntArray( final int[] data ) {
        arraySizeCheck( 4*data.length ) ;
        int offset = 0 ;
        while (true) {
            final IntBuffer typedBuffer = current.buffer().asIntBuffer() ;
            int dataSizeToRead = data.length - offset ;
            if (dataSizeToRead > typedBuffer.remaining()) {
                dataSizeToRead = typedBuffer.remaining() ;
            }

            typedBuffer.get( data, offset, dataSizeToRead ) ;
            offset += dataSizeToRead ;
            if (offset == data.length)
                break ;

            ensurePrimitive( 4 ) ;
        }
    }

    public void getLongArray( final long[] data ) {
        arraySizeCheck( 8*data.length ) ;
        int offset = 0 ;
        while (true) {
            final LongBuffer typedBuffer = current.buffer().asLongBuffer() ;
            int dataSizeToRead = data.length - offset ;
            if (dataSizeToRead > typedBuffer.remaining()) {
                dataSizeToRead = typedBuffer.remaining() ;
            }

            typedBuffer.get( data, offset, dataSizeToRead ) ;
            offset += dataSizeToRead ;
            if (offset == data.length)
                break ;

            ensurePrimitive( 8 ) ;
        }
    }

    public void getFloatArray( final float[] data ) {
        arraySizeCheck( 4*data.length ) ;
        int offset = 0 ;
        while (true) {
            final FloatBuffer typedBuffer = current.buffer().asFloatBuffer() ;
            int dataSizeToRead = data.length - offset ;
            if (dataSizeToRead > typedBuffer.remaining()) {
                dataSizeToRead = typedBuffer.remaining() ;
            }

            typedBuffer.get( data, offset, dataSizeToRead ) ;
            offset += dataSizeToRead ;
            if (offset == data.length)
                break ;

            ensurePrimitive( 4 ) ;
        }
    }

    public void getDoubleArray( final double[] data ) {
        arraySizeCheck( 8*data.length ) ;
        int offset = 0 ;
        while (true) {
            final DoubleBuffer typedBuffer = current.buffer().asDoubleBuffer() ;
            int dataSizeToRead = data.length - offset ;
            if (dataSizeToRead > typedBuffer.remaining()) {
                dataSizeToRead = typedBuffer.remaining() ;
            }

            typedBuffer.get( data, offset, dataSizeToRead ) ;
            offset += dataSizeToRead ;
            if (offset == data.length)
                break ;

            ensurePrimitive( 8 ) ;
        }
    }
}

