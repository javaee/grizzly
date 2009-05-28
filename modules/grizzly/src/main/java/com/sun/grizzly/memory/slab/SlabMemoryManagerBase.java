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

import java.nio.ByteBuffer;

import com.sun.grizzly.Buffer;
import com.sun.grizzly.Grizzly;
import com.sun.grizzly.attributes.Attribute;
import com.sun.grizzly.attributes.AttributeHolder;
import com.sun.grizzly.memory.ByteBufferManager;
import com.sun.grizzly.memory.ByteBufferWrapper;
import com.sun.grizzly.threadpool.WorkerThread;

/**
 * Base class for implementing an SlabMemory Allocator. All subclasses must implement dispose, releaseSlab and obtainSlab,
 * as well as the undefined methods in the Allocator interface: maxAllocationSize, bufferType, and close.
 * For the aim of avoiding contention on allocate() and supporting Buffer.trim()
 * SlabMemoryManagerBase associates each calling Thread with ist own Slab.
 * If calling Thread happens to be WorkerThread Slab is stored in WorkerThread's Attributes otherwise Threadlocal storage is used.
 * The Thread associated Slab serves as a memory depot for allocating BufferWrappers. If a Slab's space is exhausted
 * obtainSlab will provide a empty Slab which will replace the calling Thread's exhausted Slab.
 * 
 * @author Ken Cavanaugh
 * @author John Vieten
 */
public abstract class SlabMemoryManagerBase extends ByteBufferManager {

    private SlabAssociation slabThreadAssociation = new SlabThreadAssociation(this);
    private SlabAssociation slabWorkerThreadAssociation = new SlabWorkerThreadAssociation(this);

    SlabMemoryManagerBase() {

    }

    public abstract void dispose(Slab slab, ByteBuffer store);

    abstract void releaseSlab(Slab slab);

    abstract Slab obtainSlab();

    /**
     * Returns the maximum size that can be allocated by an allocate( int ) call.
     */
    abstract public int maxAllocationSize();

    @Override
    public SlabByteBufferWrapper allocate(final int size) {
        if (size > maxAllocationSize()) {
            throw new IllegalArgumentException("Request size "
                    + size + " is larger than maximum allocation size "
                    + maxAllocationSize());
        }

        Slab currentSlab = obtainCurrentThreadSlab();

        if (currentSlab.sizeAvailable() < (size)) {
            currentSlab.markFull();
            releaseSlab(currentSlab);
            currentSlab = obtainSlab();
            associateThread(currentSlab);
        }
     return new SlabByteBufferWrapper(this, currentSlab, size);
    }

    public final Buffer allocate(final int minSize, final int maxSize) {

        if (minSize > maxAllocationSize()) {
            throw new IllegalArgumentException("Minimum request size "
                    + minSize + " is larger than maximum allocation size "
                    + maxAllocationSize());
        }

        final int remaining = obtainCurrentThreadSlab().sizeAvailable();
        int allocSize = maxSize;
        if ((remaining >= minSize) && (remaining <= maxSize)) {
            allocSize = remaining;
        }

        return allocate(allocSize);
    }


    @Override
    public void release(ByteBufferWrapper buffer) {
    }

    @Override
    public ByteBufferWrapper reallocate(ByteBufferWrapper oldBuffer, int newSize) {
        return null;
    }

    private Slab getCurrentThreadSlab() {
       return getSlabAssociation().getSlab();

    }

    private Slab obtainCurrentThreadSlab() {
        return getSlabAssociation().obtainSlab();
    }

    private void associateThread(Slab slab) {
         getSlabAssociation().associate(slab);
    }

    private SlabAssociation getSlabAssociation() {
        SlabAssociation result;
        Thread thread = Thread.currentThread();
          if (thread instanceof WorkerThread) {
            result = slabWorkerThreadAssociation;
        } else {
            result = slabThreadAssociation;
        }
        return result;
    }


    interface SlabAssociation {
        Slab getSlab();
        Slab obtainSlab();
        void associate(Slab slab);
    }

    private  class SlabThreadAssociation implements SlabAssociation {
        private ThreadLocalSlab threadLocalSlab = new ThreadLocalSlab();
        private SlabMemoryManagerBase manager;

        private SlabThreadAssociation(SlabMemoryManagerBase manager) {
            this.manager = manager;
        }

        private  class ThreadLocalSlab extends ThreadLocal {
            @Override
            public Object initialValue() {
                return null;
            }

            public Slab getSlab() {
                return (Slab) super.get();
            }

        }

        public Slab getSlab() {
            return threadLocalSlab.getSlab();
        }

        public Slab obtainSlab() {
            Slab slab =threadLocalSlab.getSlab();
            if(slab==null) {
               slab=manager.obtainSlab();
               associate(slab);
            }
            return slab;
        }

        public void associate(Slab slab) {
             threadLocalSlab.set(slab);
        }
    }

    private class SlabWorkerThreadAssociation implements SlabAssociation {
        Attribute<Slab> threadAssociatedSlab
                = Grizzly.DEFAULT_ATTRIBUTE_BUILDER.<Slab>createAttribute("SlabMemoryManagerBase.slab");

        private SlabMemoryManagerBase manager;

        private SlabWorkerThreadAssociation(SlabMemoryManagerBase manager) {
            this.manager = manager;
        }

        public Slab getSlab() {
            WorkerThread thread = (WorkerThread) Thread.currentThread();
            return threadAssociatedSlab.get(thread);
        }

        public Slab obtainSlab() {
            WorkerThread thread = (WorkerThread) Thread.currentThread();

            Slab currentSlab = threadAssociatedSlab.get(thread);
            if (currentSlab == null) {
                currentSlab = manager.obtainSlab();
                threadAssociatedSlab.set(thread, currentSlab);
            }
            return currentSlab;
        }

        public void associate(Slab slab) {
            WorkerThread thread = (WorkerThread) Thread.currentThread();
            AttributeHolder workerThreadAttributes = thread.obtainAttributes();
            threadAssociatedSlab.set(workerThreadAttributes, slab);
        }
    }
}

