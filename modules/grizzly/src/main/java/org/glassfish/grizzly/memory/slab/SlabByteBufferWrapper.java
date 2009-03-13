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
package org.glassfish.grizzly.memory.slab;

import org.glassfish.grizzly.memory.*;


/**
 * The SlabByteBufferWrapper knows both the MemoryManager and the slab used 
 * to allocate it. Both are needed in the BufferWrapper.trim() and 
 * BufferWrapper.dispose() methods:
 * 
 * trim() is used to allocate a buffer as large as the free space in a slab 
 * for reading, so that very large reads can be made efficiently. 
 * After reading the data, trim() is called in order to reduce 
 * the space needed to the actual data read. 
 * This works best in the case where a slab is used by a single reader thread, 
 * which is the expected mode of use. 
 * 
 * dispose() is used to release a buffer once the stream is done with it 
 * (directly from the Reader, most likely as part 
 * of the BufferHandler in the Writer). 
 * Here both the allocator and the slab are needed: the slab tracks whether 
 * or not all of its allocated space has been freed, and the allocator 
 * (in the pool case by delegating to the SlabPoolImpl) tracks empty, 
 * partial and full slabs for recycling memory. 
 * 
 * @author Ken Cavanaugh
 * @author John Vieten 
 */
public class SlabByteBufferWrapper extends ByteBufferWrapper {

    private Slab slab;
    private final int slabPosition;    // starting position in the Slab before

    /** Allocate a buffer that contains dataSize data.
     */
    public SlabByteBufferWrapper(final ByteBufferManager allocator, final Slab slab,
            final int size) {
        this.memoryManager = allocator;
        this.slab = slab;
        this.slabPosition = slab.currentPosition();
        visible = slab.allocate(size);
       
    }


    /** Trim the buffer by reducing capacity to position, if possible.
     * May return without changing capacity.  Also resets the position to 0,
     * like reset().
     */
    @Override
    public void trim() {
        checkDispose();
        final int sizeNeeded =  visible.position();
        visible = slab.trim(
                slabPosition,
                visible,
                sizeNeeded);
        visible.position(0);
    }

    /** Notify the allocator that the space for this BufferWrapper is no longer needed.
     * All calls to methods on a BufferWrapper will fail after a call to dispose().
     */
    @Override
    public void dispose() {
        checkDispose();
        ((SlabMemoryManagerBase) memoryManager).dispose(slab, visible);
        super.dispose();
        slab = null;
       

    }

    private void checkDispose() {
        if (visible == null) {
            throw new IllegalStateException("BufferWrapper has already been disposed");
        }
    }

   

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("SlabByteBufferWrapper ");
        sb.append(", visible=[").append(visible).append(']');
        sb.append(']');
        return sb.toString();
    }
}
