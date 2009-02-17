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

import org.glassfish.grizzly.bbs.impl.*;
import org.glassfish.grizzly.bbs.*;

import java.nio.ByteBuffer ;

/** Wrapper around a ByteBuffer that allows prepending a limited amount of data.
 * Basic sequence of operations:
 * <ol>
 * <lit>new BufferWrapper
 * <lit>buffer() to obtain buffer, which is then filled with data in any desired fashion.
 * <lit>reset() to prepare the buffer for reading.
 * <lit>(optional) prepend if necessary to place additional data at the start of the buffer.
 * <lit>buffer() again to obtain the ByteBuffer to use for reading the data (prepend WILL change
 * the ByteBuffer instance returned from buffer()!)
 * </ol>
 */
public class BufferWrapper {
    private AllocatorBase allocator ;
    private Slab slab ;
    private final int slabPosition ;    // starting position in the Slab before 
                                        // this BufferWrapper was allocated.
    private final int headerSize ;

    private ByteBuffer backingStore ;
    private ByteBuffer visible ;

    /** Allocate a buffer that contains dataSize data, and also
     * may prepend upto headerSize bytes without extra copies.
     */
    public BufferWrapper( final Allocator allocator, final Slab slab, 
        final int headerSize, final int size ) {
        this.allocator = AllocatorBase.class.cast( allocator ) ;
        this.headerSize = headerSize ;
        this.slab = slab ;
        this.slabPosition = slab.currentPosition() ;
        backingStore = slab.allocate( headerSize + size ) ;
        backingStore.position( headerSize ) ;

        visible = backingStore.slice() ;
    }

    private void checkDispose() {
        if (visible == null) 
            throw new IllegalStateException( "BufferWrapper has already been disposed" ) ;
    }

    /** Return the ByteBuffer managed by this BufferWrapper.
     * Normally only used for interfacing with NIO channels.
     */
    public ByteBuffer buffer() {
        checkDispose() ;
        return visible ;
    }

    /** Reset the buffer for reading.  Moves ByteBuffer position back to 0.
     */
    public ByteBuffer reset() {
        checkDispose() ;
        // Note that the buffer contains valid data only up to the current position,
        // so set the limit to the position and the position to 0: this is what the
        // flip() operation does.
        visible.flip() ;
        return visible ;
    }

    /** Return the amount of space remaining in the buffer.
     */
    public int remaining() {
        checkDispose() ;
        return visible.remaining() ;
    }

    /** Prepend data from header.position() to header.limit() to the 
     * current buffer.  This will change the value returned by buffer()!
     * @throws IllegalArgumentException if header.limit() - header.position() 
     * is greater than headerSize.
     */
    public ByteBuffer prepend( final ByteBuffer header ) {
        checkDispose() ;
        if (header.remaining() > headerSize)
            throw new IllegalArgumentException(
                "header size is greater than reserved space" ) ;

        if (header.remaining() > 0) {
            final int startPosition = backingStore.position() - header.remaining() ;
            backingStore.position( startPosition ) ;
            backingStore.put( header ) ;
            backingStore.position( startPosition ) ;
            visible = backingStore.slice() ;
        }

        return visible ;
    }

    /** Trim the buffer by reducing capacity to position, if possible.
     * May return without changing capacity.  Also resets the position to 0,
     * like reset().
     */
    public void trim() {
        checkDispose() ;
        final int sizeNeeded = headerSize + visible.position() ;
        backingStore = slab.trim( slabPosition, backingStore, sizeNeeded ) ;
        backingStore.position( headerSize ) ;

        visible = backingStore.slice() ;
        visible.position( 0 ) ;
    }

    /** Notify the allocator that the space for this BufferWrapper is no longer needed.
     * All calls to methods on a BufferWrapper will fail after a call to dispose().
     */
    public void dispose() {
        checkDispose() ;
        allocator.dispose( slab, backingStore ) ;
        allocator = null ;
        slab = null ;
        backingStore = null ;
        visible = null ;
    }
}
