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

import java.util.Set;
import java.util.HashSet;

import java.nio.ByteBuffer;

/**
 * @author Ken Cavanaugh 
 */
public final class SlabPoolImpl implements SlabMemoryManagerFactory.SlabPool {
    // We assume that all of the methods of this class may be invoked 
    // by arbitrary threads concurrently.  Since the methods are all independent,
    // we can just synchronize the public methods.  I don't think contention will
    // be too big of a problem, because Slab allocation should happen much less
    // often than buffer allocation in a Slab.
    private final int _maxAllocationSize;
    private final long _minSize;
    private final long _maxSize;
    private final boolean _bufferType;
    private boolean _closed = false;    // Use sets because Sets support constant-time remove() and add() calls.
    private Set<Slab> _emptySlabs;      // Set of all empty slabs
    private Set<Slab> _fullSlabs;       // Set of all full slabs 
    // (target of releaseSlab)
    private Set<Slab> _partialSlabs;    // Set of all slabs in use 
    // (returned by getSlab, but not
    // yet released.  State may be 
    // EMPTY or PARTIAL.

    public SlabPoolImpl(final int maxAllocationSize, final long minSize,
            final long maxSize, final boolean bufferType) {

        this._maxAllocationSize = maxAllocationSize;
        this._minSize = minSize;
        this._maxSize = maxSize;
        this._bufferType = bufferType;

        _emptySlabs = new HashSet<Slab>();
        _fullSlabs = new HashSet<Slab>();
        _partialSlabs = new HashSet<Slab>();

        while (freeSpace() < minSize) {
            final Slab slab = new Slab(maxAllocationSize, bufferType);
            _emptySlabs.add(slab);
        }
    }

    private void checkClosed() {
        if (_closed) {
            throw new IllegalStateException("SlabPoolImpl is closed");
        }
    }

    @Override
    public synchronized void close() {
        checkClosed();
        _emptySlabs = null;
        _fullSlabs = null;
        _partialSlabs = null;
        _closed = true;
    }

    @Override
    public synchronized int maxAllocationSize() {
        checkClosed();
        return _maxAllocationSize;
    }

    @Override
    public synchronized long minSize() {
        checkClosed();
        return _minSize;
    }

    @Override
    public synchronized long maxSize() {
        checkClosed();
        return _maxSize;
    }

    @Override
    public synchronized int numFreeSlabs() {
        checkClosed();
        return _emptySlabs.size();
    }

    @Override
    public synchronized int numPartialSlabs() {
        checkClosed();
        return _partialSlabs.size();
    }

    @Override
    public synchronized int numFullSlabs() {
        checkClosed();
        return _fullSlabs.size();
    }

    private long totalSpaceInUse() {
        long result = _maxAllocationSize;
        result = result * (_emptySlabs.size() + _partialSlabs.size() + _fullSlabs.size());
        return result;
    }

    private long computeAvailableSize(final Set<Slab> set) {
        long result = 0;
        for (Slab slab : set) {
            result += slab.sizeAvailable();
        }
        return result;
    }

    private long computeAllocatedSize(final Set<Slab> set) {
        long result = 0;
        for (Slab slab : set) {
            result += slab.sizeAllocated();
        }
        return result;
    }

    private long computeDisposedSize(final Set<Slab> set) {
        long result = 0;
        for (Slab slab : set) {
            result += slab.sizeDisposed();
        }
        return result;
    }

    // total free space is total free space in empty slabs (which are completely
    // available) and partial slabs (which are only partly available).
    @Override
    public synchronized long freeSpace() {
        checkClosed();
        return computeAvailableSize(_emptySlabs) + computeAvailableSize(_partialSlabs);
    }

    // unavailable space is the space that has been disposed, but since this is
    // in partial and full Slabs, the unavailable space is not available to 
    // a client of the SlabPool.
    @Override
    public synchronized long unavailableSpace() {
        checkClosed();
        return computeDisposedSize(_partialSlabs) + computeDisposedSize(_fullSlabs);
    }

    @Override
    public synchronized long allocatedSpace() {
        checkClosed();
        return computeAllocatedSize(_partialSlabs) + computeAllocatedSize(_fullSlabs);
    }

    @Override
    public synchronized boolean bufferType() {
        checkClosed();
        return _bufferType;
    }

    public synchronized Slab getSlab() {
        checkClosed();
        Slab result = null;
        for (Slab slab : _emptySlabs) {
            result = slab;
            break;
        }

        if (result == null) {
            result = new Slab(_maxAllocationSize, _bufferType);
        } else {
            // Must do this outside of the scope of the iterator
            _emptySlabs.remove(result);
        }

        _partialSlabs.add(result);
        return result;
    }

    public synchronized void releaseSlab(final Slab slab) {
        checkClosed();
        slab.markFull();
        _partialSlabs.remove(slab);
        _fullSlabs.add(slab);
    }

    public synchronized void dispose(final Slab slab,
            final ByteBuffer buffer) {
        checkClosed();
        slab.dispose(buffer);

        // If we have disposed successfully, and state is now Empty,
        // we need to requeue the slab.  Note that only Full slabs can
        // change to empty, never partial, so the slab must be in the
        // full queue. XXX should check this and log an error if not true.
        if (slab.getState() == Slab.State.EMPTY) {
            _fullSlabs.remove(slab);
            slab.markEmpty();

            // Only add the newly freed slab if the total space in use is less than
            // maxSize, otherwise just let the slab be garbage collected.
            if (totalSpaceInUse() < _maxSize) {
                _emptySlabs.add(slab);
            }
        }
    }
}