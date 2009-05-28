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
package com.sun.grizzly.memory.slab;

import com.sun.grizzly.memory.*;
import java.io.Closeable;

/** Factory for Allocators (pooled and non-pooled) and SlabPools.
 *  @author Ken Cavanaugh
 */
public class SlabMemoryManagerFactory {


    /**
     * The default maximum size that can be satisfied by
     * a call to Allocator.allocate().
     **/
    public static final int DEFAULT_SLAB_SIZE =  512 * 1024;
     /**
     * Default max size of which the pool my grow to
     */
    public static final long DEFAULT_POOL_MAX_SIZE = DEFAULT_SLAB_SIZE * 15;
    /**
     * The default minimum size of the pool in bytes.
     */
    public static final long DEFAULT_POOL_MIN_SIZE = DEFAULT_SLAB_SIZE * 5;


    
    public static final boolean DEFAULT_BYTE_BUFFER_TYPE = false;





    /** Return an Allocator that allocates from one slab at a time, creating a new slab
     * as needed.  Space is recovered by the garbage collector.
     * This version will create a new slab allocator when available space is exhausted.
     * It will only return null if an attempt is made to allocate a BufferWrapper larger
     * than allocatorSize - headerSize bytes.
     * trim is supported, but only for the last BufferWrapper returned from an allocate call.
     * @param allocatorSize The total size available for all allocate calls in a single Allocator.
     * @param bufferType The buffer type of the slabs created for this allocator.
     * @return   MemoryManager
     */
    public static MemoryManager makeAllocator(
            final int allocatorSize, final boolean bufferType) {
       
        return new SlabMemoryManagerImpl( allocatorSize, bufferType);
    }

    private SlabMemoryManagerFactory() {
    }

    public static MemoryManager makeAllocator() {
        return new SlabMemoryManagerImpl(
                DEFAULT_SLAB_SIZE,
                DEFAULT_BYTE_BUFFER_TYPE);
    }

    

    /** Create an Allocator that allocates from a pool of slabs.  All space is managed by
     * the Slabs and SlabPool, and Slabs are recycled once they have been completely freed.
     * @param pool The SlabPool to use for Slabs for this Allocator.
     * @return   MemoryManager
     */
    public static MemoryManager makePoolAllocator(final SlabPool pool) {
        return new SlabPoolMemoryManagerImpl( pool);
    }

    /** Obtain useful statistics about the SlabPool.
     */
    public interface SlabPool extends Closeable {

        /** The maxAllocationSize of every Slab in this pool.
         */
        int maxAllocationSize();

        /** The BufferType of every Slab in this pool.
         */
        boolean bufferType();

        /** Number of free slabs available for use in Allocators using this SlabPool.
         */
        int numFreeSlabs();

        /** Number of slabs currently in use by Allocators.
         */
        int numPartialSlabs();

        /** Number of full slabs which still have allocations in use.
         * Full slabs become empty as soon as all of their allocations have
         * been disposed.
         */
        int numFullSlabs();

        /** Minimum size of pool, as specified when the SlabPool was created.  
         */
        long minSize();

        /** Maximum size to which pool may expand as specified when the SlabPool was created.  
         */
        long maxSize();

        /** Current free space in pool.
         * freeSpace() + allocatedSpace() is the total size of the pool.  This may at times
         * temporarily exceed maxSize, but excess space will be released from the pool
         * when it is freed and the total size is bigger than the maximum size.
         */
        long freeSpace();

        /** Total bytes disposed but not yet available for use.  This is always less
         * than allocatedSpace.
         */
        long unavailableSpace();

        /** Total bytes allocated.  This includes unavailableSpace().  
         * allocatedSpace() - unavailableSpace() is the space still actively in use
         * by clients of the SlabPool.
         */
        long allocatedSpace();
    }

    /**
     * Create a SlabPool from which Allocators may be created.
     * @param maxAllocationSize The maximum size that can be satisfied
     * by a call to Allocator.allocate().
     * @param minSize The minimum size of the pool in bytes.
     * @param maxSize The maximum size to which a pool may grow.
     * @param bufferType The buffer type used in this pool.
     */
    public static SlabPool makeSlabPool(final int maxAllocationSize,
            final long minSize, final long maxSize,
            final boolean bufferType) {

        return new SlabPoolImpl(maxAllocationSize, minSize, maxSize, bufferType);
    }

    /**
     * Get a SlabPool with defaults
     *
     * @return
     */
    public static SlabPool makeDefaultSlabPool() {
        return new SlabPoolImpl(
                DEFAULT_SLAB_SIZE,
                DEFAULT_POOL_MIN_SIZE,
                DEFAULT_POOL_MAX_SIZE,
                false);
        
    }
}

